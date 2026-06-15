package com.fastpos.android.data.database

import android.util.Base64
import java.sql.Timestamp

/** Wraps a [Map] row (received from [PeerDbClient]) as a [java.sql.ResultSet]. */
class MapResultSet(private val row: Map<String, String?>) : AbstractResultSet() {

    private var lastWasNull = false

    override fun wasNull(): Boolean = lastWasNull

    override fun getString(col: String): String? {
        val v = row[col]; lastWasNull = (v == null); return v
    }

    override fun getInt(col: String): Int {
        val v = row[col]; lastWasNull = (v == null); return v?.toIntOrNull() ?: 0
    }

    override fun getLong(col: String): Long {
        val v = row[col]; lastWasNull = (v == null); return v?.toLongOrNull() ?: 0L
    }

    override fun getDouble(col: String): Double {
        val v = row[col]; lastWasNull = (v == null); return v?.toDoubleOrNull() ?: 0.0
    }

    override fun getFloat(col: String): Float {
        val v = row[col]; lastWasNull = (v == null); return v?.toFloatOrNull() ?: 0f
    }

    override fun getBoolean(col: String): Boolean {
        val v = row[col]; lastWasNull = (v == null)
        return v?.let { it == "1" || it.lowercase() == "true" } ?: false
    }

    override fun getShort(col: String): Short {
        val v = row[col]; lastWasNull = (v == null); return v?.toShortOrNull() ?: 0
    }

    override fun getBytes(col: String): ByteArray {
        val v = row[col]; lastWasNull = (v == null)
        if (v == null) return ByteArray(0)
        return if (v.startsWith("data:blob;base64,"))
            Base64.decode(v.removePrefix("data:blob;base64,"), Base64.DEFAULT)
        else ByteArray(0)
    }

    override fun getDate(col: String): java.sql.Date? {
        val v = row[col]; lastWasNull = (v == null); if (v == null) return null
        return CursorResultSet.parseSqlDate(v)
    }

    override fun getTimestamp(col: String): Timestamp? {
        val v = row[col]; lastWasNull = (v == null); if (v == null) return null
        return CursorResultSet.parseSqlTimestamp(v)
    }

    override fun getObject(col: String): Any? {
        val v = row[col]; lastWasNull = (v == null); return v
    }
}
