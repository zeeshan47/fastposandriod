package com.fastpos.android.data.network

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.nsd.NsdManager
import android.util.Base64
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.fastpos.android.data.database.SqlTranslator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.ServerSocket
import java.net.Socket

/**
 * Listens on [port] for JSON-over-TCP query requests from [PeerDbClient] instances.
 * Each request is one line of JSON; the response is one line of JSON.
 * The server device must be running in standalone (local SQLite) mode.
 * When [context] is provided, registers with NsdManager so client devices can auto-discover.
 */
class PeerDbServer(private val db: SQLiteDatabase, val port: Int = 7001, private val context: Context? = null) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dbMutex = Mutex()
    private var serverSocket: ServerSocket? = null
    private var nsdManager: NsdManager? = null
    private var nsdRegistered = false
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null

    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                val ss = ServerSocket(port)
                serverSocket = ss
                context?.let { registerNsd(it) }
                while (isRunning) {
                    try {
                        val client = ss.accept()
                        launch { handleClient(client) }
                    } catch (_: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (_: Exception) {
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        unregisterNsd()
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun registerNsd(ctx: Context) {
        try {
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "FastPOS-${Build.MODEL}".take(63)
                serviceType = "_fastpos._tcp."
                setPort(port)
            }
            val mgr = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager = mgr
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) { nsdRegistered = true }
                override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {}
                override fun onServiceUnregistered(info: NsdServiceInfo) { nsdRegistered = false }
                override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {}
            }
            nsdRegistrationListener = listener
            mgr.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) {}
    }

    private fun unregisterNsd() {
        val listener = nsdRegistrationListener ?: return
        try { nsdManager?.unregisterService(listener) } catch (_: Exception) {}
        nsdRegistrationListener = null
        nsdRegistered = false
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 60_000
            socket.tcpNoDelay = true
            val reader = socket.getInputStream().bufferedReader()
            val writer = socket.getOutputStream().bufferedWriter()
            // Keep reading requests on the same connection until it closes
            while (isRunning) {
                val line = reader.readLine() ?: break
                val req = JSONObject(line)
                val resp = when (req.optString("type")) {
                    "ping"     -> JSONObject().put("ok", true)
                    "query"    -> dbMutex.withLock { handleQuery(req) }
                    "execute"  -> dbMutex.withLock { handleExecute(req) }
                    "insertId" -> dbMutex.withLock { handleInsertId(req) }
                    else       -> JSONObject().put("ok", false).put("error", "unknown type")
                }
                writer.write(resp.toString())
                writer.newLine()
                writer.flush()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun handleQuery(req: JSONObject): JSONObject {
        return try {
            val sql = SqlTranslator.translateDml(req.getString("sql"))
            val paramsArr = req.optJSONArray("params")
            val args = paramsArr?.let { arr ->
                Array(arr.length()) { i -> if (arr.isNull(i)) null else arr.getString(i) }
            }
            val cursor = db.rawQuery(inlineParams(sql, args), null)
            val cols = Array(cursor.columnCount) { cursor.getColumnName(it) }
            val rows = JSONArray()
            while (cursor.moveToNext()) {
                val row = JSONObject()
                cols.forEachIndexed { i, col ->
                    when {
                        cursor.isNull(i) -> row.put(col, JSONObject.NULL)
                        cursor.getType(i) == Cursor.FIELD_TYPE_BLOB -> {
                            val b64 = Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP)
                            row.put(col, "data:blob;base64,$b64")
                        }
                        else -> row.put(col, cursor.getString(i))
                    }
                }
                rows.put(row)
            }
            cursor.close()
            JSONObject().put("ok", true).put("rows", rows)
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message)
        }
    }

    private fun handleExecute(req: JSONObject): JSONObject {
        return try {
            val sql = SqlTranslator.toSqlite(req.getString("sql"))
            val paramsArr = req.optJSONArray("params")
            val args = paramsArr?.let { arr ->
                Array(arr.length()) { i -> if (arr.isNull(i)) null else arr.getString(i) }
            }
            db.execSQL(inlineParams(sql, args))
            JSONObject().put("ok", true).put("affected", 1)
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message)
        }
    }

    private fun handleInsertId(req: JSONObject): JSONObject {
        return try {
            val sql = SqlTranslator.toSqlite(req.getString("sql"))
            val paramsArr = req.optJSONArray("params")
            val args = paramsArr?.let { arr ->
                Array(arr.length()) { i -> if (arr.isNull(i)) null else arr.getString(i) }
            }
            db.execSQL(inlineParams(sql, args))
            val id = db.compileStatement("SELECT last_insert_rowid()").simpleQueryForLong().toInt()
            JSONObject().put("ok", true).put("id", id)
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", e.message)
        }
    }

    private fun inlineParams(sql: String, args: Array<String?>?): String {
        if (args.isNullOrEmpty()) return sql
        val sb = StringBuilder(sql.length + args.size * 8)
        var idx = 0
        for (ch in sql) {
            if (ch == '?' && idx < args.size) {
                val v = args[idx++]
                if (v == null) {
                    sb.append("NULL")
                } else {
                    val lng = v.toLongOrNull()
                    val dbl = if (lng == null) v.toDoubleOrNull() else null
                    when {
                        lng != null -> sb.append(lng)
                        dbl != null -> sb.append(dbl)
                        else        -> sb.append('\'').append(v.replace("'", "''")).append('\'')
                    }
                }
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }
}
