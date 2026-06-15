package com.fastpos.android.data.database

import android.database.Cursor
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

/** Wraps an Android [Cursor] as a [java.sql.ResultSet] so existing repository mappers work unchanged. */
class CursorResultSet(private val cursor: Cursor) : AbstractResultSet() {

    private var lastColIdx: Int = -1

    private fun idx(col: String): Int = cursor.getColumnIndex(col)

    override fun wasNull(): Boolean = lastColIdx >= 0 && cursor.isNull(lastColIdx)

    // ── String ───────────────────────────────────────────────────────────────
    override fun getString(col: String): String? {
        val i = idx(col); lastColIdx = i; return if (i < 0 || cursor.isNull(i)) null else cursor.getString(i)
    }
    override fun getString(i: Int): String? { lastColIdx = i - 1; return cursor.getString(i - 1) }

    // ── Int ──────────────────────────────────────────────────────────────────
    override fun getInt(col: String): Int {
        val i = idx(col); lastColIdx = i; return if (i < 0) 0 else cursor.getInt(i)
    }
    override fun getInt(i: Int): Int { lastColIdx = i - 1; return cursor.getInt(i - 1) }

    // ── Long ─────────────────────────────────────────────────────────────────
    override fun getLong(col: String): Long {
        val i = idx(col); lastColIdx = i; return if (i < 0) 0L else cursor.getLong(i)
    }
    override fun getLong(i: Int): Long { lastColIdx = i - 1; return cursor.getLong(i - 1) }

    // ── Double ───────────────────────────────────────────────────────────────
    override fun getDouble(col: String): Double {
        val i = idx(col); lastColIdx = i; return if (i < 0) 0.0 else cursor.getDouble(i)
    }
    override fun getDouble(i: Int): Double { lastColIdx = i - 1; return cursor.getDouble(i - 1) }

    // ── Float ────────────────────────────────────────────────────────────────
    override fun getFloat(col: String): Float {
        val i = idx(col); lastColIdx = i; return if (i < 0) 0f else cursor.getFloat(i)
    }
    override fun getFloat(i: Int): Float { lastColIdx = i - 1; return cursor.getFloat(i - 1) }

    // ── Boolean (stored as INTEGER 0/1) ──────────────────────────────────────
    override fun getBoolean(col: String): Boolean {
        val i = idx(col); lastColIdx = i; return i >= 0 && cursor.getInt(i) != 0
    }
    override fun getBoolean(i: Int): Boolean { lastColIdx = i - 1; return cursor.getInt(i - 1) != 0 }

    // ── Short ────────────────────────────────────────────────────────────────
    override fun getShort(col: String): Short {
        val i = idx(col); lastColIdx = i; return if (i < 0) 0 else cursor.getShort(i)
    }

    // ── Date (stored as TEXT "yyyy-MM-dd" or "yyyy-MM-dd HH:mm:ss") ──────────
    override fun getDate(col: String): java.sql.Date? {
        val i = idx(col); lastColIdx = i
        if (i < 0 || cursor.isNull(i)) return null
        val str = cursor.getString(i) ?: return null
        return parseSqlDate(str)
    }

    // ── Bytes / BLOB ──────────────────────────────────────────────────────────
    override fun getBytes(col: String): ByteArray {
        val i = idx(col); lastColIdx = i
        if (i < 0 || cursor.isNull(i)) return ByteArray(0)
        return cursor.getBlob(i) ?: ByteArray(0)
    }

    // ── Timestamp ────────────────────────────────────────────────────────────
    override fun getTimestamp(col: String): Timestamp? {
        val i = idx(col); lastColIdx = i
        if (i < 0 || cursor.isNull(i)) return null
        val str = cursor.getString(i) ?: return null
        return parseSqlTimestamp(str)
    }

    // ── Object (fallback – returns String for unknown types) ──────────────────
    override fun getObject(col: String): Any? {
        val i = idx(col); lastColIdx = i; return if (i < 0 || cursor.isNull(i)) null else cursor.getString(i)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    companion object {
        private val DATE_FORMATS = listOf(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"
        )

        fun parseSqlDate(str: String): java.sql.Date? {
            for (fmt in DATE_FORMATS) {
                try {
                    return java.sql.Date(SimpleDateFormat(fmt, Locale.US).parse(str)!!.time)
                } catch (_: Exception) {}
            }
            return null
        }

        fun parseSqlTimestamp(str: String): Timestamp? {
            for (fmt in DATE_FORMATS) {
                try {
                    return Timestamp(SimpleDateFormat(fmt, Locale.US).parse(str)!!.time)
                } catch (_: Exception) {}
            }
            return null
        }
    }
}
