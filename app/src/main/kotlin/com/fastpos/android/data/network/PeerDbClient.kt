package com.fastpos.android.data.network

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.Socket

/** Sends JSON-over-TCP query requests to a [PeerDbServer] running on another device.
 *  Maintains a persistent connection to avoid TCP handshake overhead per query. */
class PeerDbClient(val host: String, val port: Int) {

    private var socket: Socket? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val lock = Any()

    private fun getOrConnect(): Triple<Socket, BufferedWriter, BufferedReader> {
        synchronized(lock) {
            val s = socket
            if (s != null && !s.isClosed && s.isConnected) {
                return Triple(s, writer!!, reader!!)
            }
            val newSocket = Socket(host, port).also {
                it.soTimeout = 15_000
                it.tcpNoDelay = true  // disable Nagle — send each query immediately
                it.keepAlive = true
            }
            val w = newSocket.getOutputStream().bufferedWriter()
            val r = newSocket.getInputStream().bufferedReader()
            socket = newSocket; writer = w; reader = r
            return Triple(newSocket, w, r)
        }
    }

    private fun request(req: JSONObject): JSONObject {
        synchronized(lock) {
            repeat(2) { attempt ->
                try {
                    val (_, w, r) = getOrConnect()
                    w.write(req.toString())
                    w.newLine()
                    w.flush()
                    val line = r.readLine() ?: throw Exception("Connection closed by server")
                    return JSONObject(line)
                } catch (e: Exception) {
                    // On first failure reconnect and retry once
                    runCatching { socket?.close() }
                    socket = null; writer = null; reader = null
                    if (attempt == 1) throw Exception("Server unreachable at $host:$port — ${e.message}")
                }
            }
            throw Exception("Unreachable")
        }
    }

    private fun paramsArray(params: List<Any?>): JSONArray {
        val arr = JSONArray()
        params.forEach { v -> if (v == null) arr.put(JSONObject.NULL) else arr.put(v.toString()) }
        return arr
    }

    fun ping(): Boolean = try {
        request(JSONObject().put("type", "ping")).optBoolean("ok", false)
    } catch (_: Exception) { false }

    fun query(sql: String, params: List<Any?>): List<Map<String, String?>> {
        val resp = request(
            JSONObject().put("type", "query").put("sql", sql).put("params", paramsArray(params))
        )
        check(resp.optBoolean("ok")) { resp.optString("error", "Query failed") }
        val rowsArr = resp.getJSONArray("rows")
        return List(rowsArr.length()) { i ->
            val obj = rowsArr.getJSONObject(i)
            val map = mutableMapOf<String, String?>()
            @Suppress("UNCHECKED_CAST")
            (obj.keys() as Iterator<String>).forEach { k ->
                map[k] = if (obj.isNull(k)) null else obj.getString(k)
            }
            map
        }
    }

    fun execute(sql: String, params: List<Any?>): Int {
        val resp = request(
            JSONObject().put("type", "execute").put("sql", sql).put("params", paramsArray(params))
        )
        check(resp.optBoolean("ok")) { resp.optString("error", "Execute failed") }
        return resp.optInt("affected", 1)
    }

    fun insertAndGetId(sql: String, params: List<Any?>): Int {
        val resp = request(
            JSONObject().put("type", "insertId").put("sql", sql).put("params", paramsArray(params))
        )
        check(resp.optBoolean("ok")) { resp.optString("error", "Insert failed") }
        return resp.optInt("id", 0)
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null; writer = null; reader = null
    }
}
