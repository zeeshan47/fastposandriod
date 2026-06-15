package com.fastpos.android.data.database

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DatabaseHelper"
private const val CONNECT_TIMEOUT = 10  // seconds

@Singleton
class DatabaseHelper @Inject constructor(@ApplicationContext private val context: Context) {

    private var config: ConnectionConfig = ConnectionConfig()

    @Volatile private var isLocalMode = false
    private var localDb: android.database.sqlite.SQLiteDatabase? = null

    @Volatile private var isPeerClientMode = false
    private var peerClient: com.fastpos.android.data.network.PeerDbClient? = null
    private var peerServer: com.fastpos.android.data.network.PeerDbServer? = null

    // Persistent reusable connection — avoids TCP handshake overhead per query.
    // connLock serializes access so a single connection is never used by two queries simultaneously.
    private val connLock = Mutex()
    private var cachedConn: Connection? = null

    private suspend fun <T> withConn(block: (Connection) -> T): T =
        connLock.withLock {
            val conn = cachedConn?.takeIf { !it.isClosed }
                ?: openConnection().also { cachedConn = it }
            try {
                block(conn)
            } catch (e: Exception) {
                if (conn.isClosed) cachedConn = null
                throw e
            }
        }

    fun closeConnection() {
        runCatching { cachedConn?.close() }
        cachedConn = null
    }

    fun configure(cfg: ConnectionConfig) { config = cfg }
    fun getConfig(): ConnectionConfig = config
    fun isLocal(): Boolean = isLocalMode
    fun isPeerClient(): Boolean = isPeerClientMode

    fun activateLocalMode() {
        isLocalMode = true
        localDb = LocalSchemaHelper(context).writableDatabase
    }

    fun deactivateAllModes() {
        isLocalMode = false
        isPeerClientMode = false
        runCatching { localDb?.close() }
        localDb = null
        peerClient = null
        stopPeerServer()
        closeConnection()
    }

    fun activatePeerClientMode(host: String, port: Int) {
        isPeerClientMode = true
        peerClient = com.fastpos.android.data.network.PeerDbClient(host, port)
    }

    fun startPeerServer(port: Int = 7001) {
        if (localDb == null) activateLocalMode()
        val db = localDb ?: return
        if (peerServer?.isRunning == true) return
        peerServer = com.fastpos.android.data.network.PeerDbServer(db, port, context)
        peerServer!!.start()
    }

    fun stopPeerServer() {
        peerServer?.stop()
        peerServer = null
    }

    fun isPeerServerRunning(): Boolean = peerServer?.isRunning == true

    fun getLocalIpAddress(): String? {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    fun getLocalDbPath(): java.io.File = context.getDatabasePath("fastpos_standalone.db")

    fun withLocalDb(block: (android.database.sqlite.SQLiteDatabase) -> Unit) {
        check(isLocalMode) { "withLocalDb is only available in local mode" }
        localDb?.let { block(it) }
    }

    fun closeLocalDb() {
        stopPeerServer()
        runCatching { localDb?.close() }
        localDb = null
    }

    fun reopenLocalDb() {
        localDb = LocalSchemaHelper(context).writableDatabase
    }

    private fun openConnection(): Connection {
        Class.forName("net.sourceforge.jtds.jdbc.Driver")
        val props = java.util.Properties().apply {
            setProperty("user", config.username)
            setProperty("password", config.password)
            setProperty("loginTimeout", CONNECT_TIMEOUT.toString())
            setProperty("socketTimeout", "30")
            setProperty("ssl", "off")
        }
        return DriverManager.getConnection(config.buildJdbcUrl(), props)
    }

    /** Test connectivity – throws on failure. */
    suspend fun testConnection(): Unit = withContext(Dispatchers.IO) {
        openConnection().use { }
    }

    /** Ping the peer server; returns false if not in peer mode or unreachable. */
    suspend fun pingPeer(): Boolean = withContext(Dispatchers.IO) {
        peerClient?.ping() ?: false
    }

    /** Execute a query and map every row with [mapper]. */
    suspend fun <T> query(sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): List<T> =
        withContext(Dispatchers.IO) {
            if (isPeerClientMode) {
                return@withContext peerClient!!.query(sql, params).map { mapper(MapResultSet(it)) }
            }
            if (isLocalMode) {
                val translated = SqlTranslator.translateDml(sql)
                localDb!!.rawQuery(embedParams(translated, params), null).use { cursor ->
                    val result = mutableListOf<T>()
                    while (cursor.moveToNext()) result += mapper(CursorResultSet(cursor))
                    result
                }
            } else {
                withConn { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        params.forEachIndexed { i, v -> stmt.setObject(i + 1, toJdbcParam(v)) }
                        stmt.executeQuery().use { rs ->
                            val result = mutableListOf<T>()
                            while (rs.next()) result += mapper(rs)
                            result
                        }
                    }
                }
            }
        }

    /** Execute a query and return the first row mapped, or null. */
    suspend fun <T> queryOne(sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): T? =
        query(sql, params, mapper).firstOrNull()

    /** Execute INSERT/UPDATE/DELETE – returns rows affected. */
    suspend fun execute(sql: String, params: List<Any?> = emptyList()): Int =
        withContext(Dispatchers.IO) {
            if (isPeerClientMode) {
                return@withContext peerClient!!.execute(sql, params)
            }
            if (isLocalMode) {
                val translated = SqlTranslator.toSqlite(sql)
                val db = localDb!!
                val bound = params.take(translated.count { it == '?' })
                if (bound.isEmpty()) {
                    db.execSQL(translated)
                } else {
                    db.execSQL(translated, bound.map { paramToObject(it) }.toTypedArray())
                }
                1
            } else {
                withConn { conn ->
                    conn.prepareStatement(sql).use { stmt ->
                        params.forEachIndexed { i, v -> stmt.setObject(i + 1, toJdbcParam(v)) }
                        stmt.executeUpdate()
                    }
                }
            }
        }

    /** Execute INSERT and return the generated identity key. */
    suspend fun insertAndGetId(sql: String, params: List<Any?> = emptyList()): Int =
        withContext(Dispatchers.IO) {
            if (isPeerClientMode) {
                return@withContext peerClient!!.insertAndGetId(sql, params)
            }
            if (isLocalMode) {
                val db = localDb!!
                val translated = SqlTranslator.toSqlite(sql)
                val bound = params.take(translated.count { it == '?' })
                if (bound.isEmpty()) {
                    db.execSQL(translated)
                } else {
                    db.execSQL(translated, bound.map { paramToObject(it) }.toTypedArray())
                }
                db.compileStatement("SELECT last_insert_rowid()").simpleQueryForLong().toInt()
            } else {
                withConn { conn ->
                    conn.prepareStatement("$sql; SELECT SCOPE_IDENTITY()").use { stmt ->
                        params.forEachIndexed { i, v -> stmt.setObject(i + 1, toJdbcParam(v)) }
                        stmt.execute()
                        if (stmt.moreResults.not() && stmt.updateCount >= 0) stmt.moreResults
                        stmt.resultSet?.use { rs ->
                            if (rs.next()) rs.getInt(1) else 0
                        } ?: 0
                    }
                }
            }
        }

    /** Convenience wrapper that runs multiple statements in one connection. */
    suspend fun <T> transaction(block: suspend (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            if (isPeerClientMode) {
                // No atomic transaction in peer mode — each statement is individually forwarded
                return@withContext block(LocalMarkerConnection)
            }
            if (isLocalMode) {
                val db = localDb!!
                db.beginTransaction()
                try {
                    val result = block(LocalMarkerConnection)
                    db.setTransactionSuccessful()
                    result
                } catch (ex: Exception) {
                    Log.e(TAG, "Local transaction rolled back", ex)
                    throw ex
                } finally {
                    db.endTransaction()
                }
            } else {
                connLock.withLock {
                    val conn = cachedConn?.takeIf { !it.isClosed }
                        ?: openConnection().also { cachedConn = it }
                    conn.autoCommit = false
                    try {
                        val result = block(conn)
                        conn.commit()
                        result
                    } catch (ex: Exception) {
                        try { conn.rollback() } catch (_: Exception) {}
                        if (conn.isClosed) cachedConn = null
                        Log.e(TAG, "Transaction rolled back", ex)
                        throw ex
                    } finally {
                        try { conn.autoCommit = true } catch (_: Exception) { cachedConn = null }
                    }
                }
            }
        }

    /** Run INSERT inside an already-open connection and return generated id. */
    fun insertAndGetIdSync(conn: Connection, sql: String, params: List<Any?>): Int {
        if (isPeerClientMode) {
            return peerClient!!.insertAndGetId(sql, params)
        }
        if (isLocalMode) {
            val db = localDb!!
            val translated = SqlTranslator.toSqlite(sql)
            val bound = params.take(translated.count { it == '?' })
            if (bound.isEmpty()) {
                db.execSQL(translated)
            } else {
                db.execSQL(translated, bound.map { paramToObject(it) }.toTypedArray())
            }
            return db.compileStatement("SELECT last_insert_rowid()").simpleQueryForLong().toInt()
        }
        conn.prepareStatement("$sql; SELECT SCOPE_IDENTITY()").use { stmt ->
            params.forEachIndexed { i, v -> stmt.setObject(i + 1, toJdbcParam(v)) }
            stmt.execute()
            stmt.resultSet?.use { rs ->
                if (rs.next()) return rs.getInt(1)
            }
            if (stmt.moreResults) {
                stmt.resultSet?.use { rs ->
                    if (rs.next()) return rs.getInt(1)
                }
            }
            return 0
        }
    }

    /** Execute INSERT/UPDATE/DELETE inside an already-open connection — no lock re-acquisition. */
    fun executeSync(conn: Connection, sql: String, params: List<Any?> = emptyList()): Int {
        if (isPeerClientMode) return peerClient!!.execute(sql, params)
        if (isLocalMode) {
            val translated = SqlTranslator.toSqlite(sql)
            val db = localDb!!
            val bound = params.take(translated.count { it == '?' })
            if (bound.isEmpty()) db.execSQL(translated)
            else db.execSQL(translated, bound.map { paramToObject(it) }.toTypedArray())
            return 1
        }
        conn.prepareStatement(sql).use { stmt ->
            params.forEachIndexed { i, v -> stmt.setObject(i + 1, toJdbcParam(v)) }
            return stmt.executeUpdate()
        }
    }

    /** Query inside an already-open connection — no lock re-acquisition. */
    fun <T> querySync(conn: Connection, sql: String, params: List<Any?> = emptyList(), mapper: (ResultSet) -> T): List<T> {
        if (isPeerClientMode) return peerClient!!.query(sql, params).map { mapper(MapResultSet(it)) }
        if (isLocalMode) {
            val translated = SqlTranslator.translateDml(sql)
            localDb!!.rawQuery(embedParams(translated, params), null).use { cursor ->
                val result = mutableListOf<T>()
                while (cursor.moveToNext()) result += mapper(CursorResultSet(cursor))
                return result
            }
        }
        conn.prepareStatement(sql).use { stmt ->
            params.forEachIndexed { i, v -> stmt.setObject(i + 1, toJdbcParam(v)) }
            stmt.executeQuery().use { rs ->
                val result = mutableListOf<T>()
                while (rs.next()) result += mapper(rs)
                return result
            }
        }
    }

    private fun embedParams(sql: String, params: List<Any?>): String {
        if (params.isEmpty()) return sql
        val sb = StringBuilder(sql.length + params.size * 4)
        var paramIdx = 0
        for (c in sql) {
            if (c == '?' && paramIdx < params.size) {
                when (val v = params[paramIdx++]) {
                    null          -> sb.append("NULL")
                    is Int        -> sb.append(v)
                    is Long       -> sb.append(v)
                    is Double     -> sb.append(v)
                    is Float      -> sb.append(v)
                    is Boolean    -> sb.append(if (v) 1 else 0)
                    is java.util.Date -> sb.append("'")
                        .append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(v))
                        .append("'")
                    else          -> sb.append("'").append(v.toString().replace("'", "''")).append("'")
                }
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun toJdbcParam(v: Any?): Any? = when (v) {
        is java.util.Date -> java.sql.Timestamp(v.time)
        else -> v
    }

    private fun paramToObject(v: Any?): Any? = when (v) {
        is java.util.Date -> java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(v)
        else -> v
    }
}
