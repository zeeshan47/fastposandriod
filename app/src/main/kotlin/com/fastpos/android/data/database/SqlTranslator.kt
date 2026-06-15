package com.fastpos.android.data.database

/**
 * Translates T-SQL patterns used by repositories into SQLite-compatible equivalents.
 * Only handles patterns actually present in this codebase.
 */
object SqlTranslator {

    fun toSqlite(sql: String): String {
        val trimmed = sql.trim()

        return when {
            // Any IF NOT EXISTS (...) CREATE/ALTER/INSERT block
            trimmed.startsWith("IF", ignoreCase = true) &&
            trimmed.contains("NOT EXISTS", ignoreCase = true) -> translateConditionalDdl(trimmed)

            // MERGE INTO ... (recipe upsert)
            trimmed.startsWith("MERGE", ignoreCase = true) -> translateMerge(trimmed)

            else -> translateDml(trimmed)
        }
    }

    // ── Conditional DDL (IF NOT EXISTS ...) ───────────────────────────────────

    private fun translateConditionalDdl(sql: String): String {
        // BEGIN...END wrapper: extract only the CREATE TABLE statement (LocalSchemaHelper seeds data)
        if (sql.contains("BEGIN", ignoreCase = true) &&
            sql.contains("END",   ignoreCase = true)) {
            val beginIdx = sql.indexOf("BEGIN", ignoreCase = true) + 5
            val endIdx   = sql.lastIndexOf("END",   ignoreCase = true)
            val blockContent = sql.substring(beginIdx, endIdx)
            val createStart = Regex("\\bCREATE\\s+TABLE\\b", RegexOption.IGNORE_CASE)
                .find(blockContent)?.range?.first ?: return "SELECT 1"
            // Find the opening '(' and its matching ')' to capture the full column list
            val openIdx = blockContent.indexOf('(', createStart)
            val closeIdx = if (openIdx >= 0) findMatchingCloseParen(blockContent, openIdx) else -1
            val createBody = if (closeIdx >= 0) blockContent.substring(createStart, closeIdx + 1)
                             else blockContent.substring(createStart)
            return applyDdlTypeTranslations(createBody.trim())
                .replace(Regex("\\bCREATE\\s+TABLE\\s+(?!IF)", RegexOption.IGNORE_CASE),
                    "CREATE TABLE IF NOT EXISTS ")
        }

        // ALTER TABLE ADD COLUMN / ADD CONSTRAINT — skip in local mode
        if (sql.contains("ALTER TABLE", ignoreCase = true)) {
            return "SELECT 1"
        }

        // CREATE TABLE: extract from CREATE TABLE onward and translate types
        val createIdx = Regex("\\bCREATE\\s+TABLE\\b", RegexOption.IGNORE_CASE)
            .find(sql)?.range?.first
        if (createIdx != null) {
            val createBody = sql.substring(createIdx).trimEnd(';', ' ')
            return applyDdlTypeTranslations(createBody)
                .replace(Regex("\\bCREATE\\s+TABLE\\s+(?!IF)", RegexOption.IGNORE_CASE),
                    "CREATE TABLE IF NOT EXISTS ")
        }

        // INSERT INTO: convert to INSERT OR IGNORE and translate DML
        val insertIdx = Regex("\\bINSERT\\s+INTO\\b", RegexOption.IGNORE_CASE)
            .find(sql)?.range?.first
        if (insertIdx != null) {
            val insertBody = sql.substring(insertIdx).trimEnd(';', ' ')
            return translateDml(
                insertBody.replace(Regex("\\bINSERT\\s+INTO\\b", RegexOption.IGNORE_CASE),
                    "INSERT OR IGNORE INTO")
            )
        }

        // Fallback: treat as a no-op SELECT to avoid SQLite syntax errors
        return "SELECT 1"
    }

    // ── Shared DDL column-type translations ───────────────────────────────────

    private fun applyDdlTypeTranslations(sql: String): String = sql
        // IDENTITY → AUTOINCREMENT
        .replace(Regex("INT\\s+IDENTITY\\(1,1\\)\\s+PRIMARY\\s+KEY", RegexOption.IGNORE_CASE),
            "INTEGER PRIMARY KEY AUTOINCREMENT")
        // NVARCHAR / VARCHAR NOT NULL
        .replace(Regex("[NV]VARCHAR\\(\\d+\\)\\s+NOT\\s+NULL", RegexOption.IGNORE_CASE), "TEXT NOT NULL")
        // NVARCHAR / VARCHAR
        .replace(Regex("[NV]VARCHAR\\(\\d+\\)", RegexOption.IGNORE_CASE), "TEXT")
        // NTEXT / TEXT stays as TEXT
        .replace(Regex("\\bNTEXT\\b", RegexOption.IGNORE_CASE), "TEXT")
        // BIT DEFAULT x
        .replace(Regex("\\bBIT\\s+DEFAULT\\s+(\\d+)", RegexOption.IGNORE_CASE))
            { "INTEGER DEFAULT ${it.groupValues[1]}" }
        // BIT NOT NULL
        .replace(Regex("\\bBIT\\s+NOT\\s+NULL", RegexOption.IGNORE_CASE), "INTEGER NOT NULL")
        // BIT alone
        .replace(Regex("\\bBIT\\b", RegexOption.IGNORE_CASE), "INTEGER")
        // FLOAT / DECIMAL / MONEY → REAL
        .replace(Regex("\\bFLOAT\\s+DEFAULT\\s+(\\S+)", RegexOption.IGNORE_CASE))
            { "REAL DEFAULT ${it.groupValues[1]}" }
        .replace(Regex("\\bFLOAT\\b", RegexOption.IGNORE_CASE), "REAL")
        .replace(Regex("\\bDECIMAL\\(\\d+,\\d+\\)\\s+NOT\\s+NULL", RegexOption.IGNORE_CASE),
            "REAL NOT NULL")
        .replace(Regex("\\bDECIMAL\\(\\d+,\\d+\\)", RegexOption.IGNORE_CASE), "REAL")
        .replace(Regex("\\bMONEY\\b", RegexOption.IGNORE_CASE), "REAL")
        // DATETIME DEFAULT GETDATE()
        .replace(Regex("\\bDATETIME\\s+DEFAULT\\s+GETDATE\\(\\)", RegexOption.IGNORE_CASE),
            "TEXT DEFAULT (datetime('now'))")
        // DATETIME / DATE column types → TEXT (distinguish from CAST(x AS DATE))
        .replace(Regex("\\bDATETIME\\b", RegexOption.IGNORE_CASE), "TEXT")
        .replace(Regex("\\bDATE\\s+(?=NULL|NOT NULL)", RegexOption.IGNORE_CASE), "TEXT ")
        // INT DEFAULT x
        .replace(Regex("\\bINT\\s+DEFAULT\\s+(\\S+)", RegexOption.IGNORE_CASE))
            { "INTEGER DEFAULT ${it.groupValues[1]}" }
        // INT NOT NULL
        .replace(Regex("\\bINT\\s+NOT\\s+NULL", RegexOption.IGNORE_CASE), "INTEGER NOT NULL")
        // INT (remaining, not inside a word)
        .replace(Regex("(?<![A-Z])\\bINT\\b(?![A-Z])", RegexOption.IGNORE_CASE), "INTEGER")

    // ── MERGE translation ──────────────────────────────────────────────────────

    private fun translateMerge(sql: String): String {
        // MERGE INTO TableName AS target USING (VALUES (?,?,...)) AS source (c1,c2,...)
        val tableMatch = Regex(
            "MERGE\\s+INTO\\s+(\\w+)", RegexOption.IGNORE_CASE
        ).find(sql)?.groupValues?.get(1) ?: return sql

        val colMatch = Regex(
            "WHEN\\s+NOT\\s+MATCHED\\s+THEN\\s+INSERT\\s*\\(([^)]+)\\)",
            RegexOption.IGNORE_CASE
        ).find(sql)?.groupValues?.get(1)?.trim() ?: return sql

        val paramCount = Regex("USING\\s*\\(\\s*VALUES\\s*\\(([^)]+)\\)", RegexOption.IGNORE_CASE)
            .find(sql)?.groupValues?.get(1)?.count { it == '?' } ?: return sql

        val placeholders = (1..paramCount).joinToString(", ") { "?" }
        return "INSERT OR REPLACE INTO $tableMatch ($colMatch) VALUES ($placeholders)"
    }

    // ── Bracket depth matcher ─────────────────────────────────────────────────

    private fun findMatchingCloseParen(sql: String, openIdx: Int): Int {
        var depth = 0
        for (i in openIdx until sql.length) {
            when (sql[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }

    // ── DML translation ────────────────────────────────────────────────────────

    fun translateDml(sql: String): String {
        var s = sql

        // Capture TOP N or TOP (N) before removing it
        var limit: String? = null
        s = s.replace(Regex("\\bSELECT\\s+TOP\\s+\\(?(\\d+)\\)?\\s+", RegexOption.IGNORE_CASE)) {
            limit = it.groupValues[1]; "SELECT "
        }

        // WITH (...) lock hints — strip NOLOCK, UPDLOCK, HOLDLOCK and any combination
        s = s.replace(Regex("\\s*WITH\\s*\\([^)]*\\)", RegexOption.IGNORE_CASE), "")

        // ISNULL → IFNULL
        s = s.replace(Regex("\\bISNULL\\(", RegexOption.IGNORE_CASE), "IFNULL(")

        // CAST(GETDATE() AS DATE) — must precede general GETDATE() replacement to avoid nested-paren issue
        s = s.replace(Regex("CAST\\(GETDATE\\(\\)\\s+AS\\s+DATE\\)", RegexOption.IGNORE_CASE), "date('now')")

        // CAST(DATEADD(DAY,N,GETDATE()) AS DATE) → date('now', '...')
        s = s.replace(
            Regex("CAST\\(DATEADD\\(DAY,\\s*(-?\\d+),\\s*GETDATE\\(\\)\\)\\s+AS\\s+DATE\\)",
                RegexOption.IGNORE_CASE)
        ) {
            val n = it.groupValues[1].toInt()
            if (n >= 0) "date('now', '+$n days')" else "date('now', '$n days')"
        }

        // GETDATE() → datetime('now')
        s = s.replace(Regex("\\bGETDATE\\(\\)", RegexOption.IGNORE_CASE), "datetime('now')")

        // DATEADD(DAY, N, datetime('now')) — literal N
        s = s.replace(
            Regex("DATEADD\\(DAY,\\s*(-?\\d+),\\s*datetime\\('now'\\)\\)", RegexOption.IGNORE_CASE)
        ) {
            val n = it.groupValues[1].toInt()
            if (n >= 0) "datetime('now', '+$n days')" else "datetime('now', '$n days')"
        }

        // DATEADD(DAY, ?, datetime('now') or date('now')) — parameterized N
        s = s.replace(
            Regex("DATEADD\\(DAY,\\s*\\?,\\s*datetime\\('now'\\)\\)", RegexOption.IGNORE_CASE),
            "datetime('now', (? || ' days'))")
        s = s.replace(
            Regex("DATEADD\\(DAY,\\s*\\?,\\s*date\\('now'\\)\\)", RegexOption.IGNORE_CASE),
            "datetime(date('now'), (? || ' days'))")

        // DATEADD(HOUR, N, GETDATE()) → datetime('now', '+N hours')
        s = s.replace(
            Regex("DATEADD\\(HOUR,\\s*(-?\\d+),\\s*GETDATE\\(\\)\\)", RegexOption.IGNORE_CASE)
        ) {
            val n = it.groupValues[1].toInt()
            if (n >= 0) "datetime('now', '+$n hours')" else "datetime('now', '$n hours')"
        }

        // DATEADD(HOUR, N, col)
        s = s.replace(
            Regex("DATEADD\\(HOUR,\\s*(-?\\d+),\\s*([\\w.]+)\\)", RegexOption.IGNORE_CASE)
        ) {
            val n   = it.groupValues[1].toInt()
            val col = it.groupValues[2].trim()
            if (n >= 0) "datetime($col, '+$n hours')" else "datetime($col, '$n hours')"
        }

        // DATEADD(DAY, N, CAST(? AS DATE)) — literal N, parameterized date value
        s = s.replace(
            Regex("DATEADD\\(DAY,\\s*(-?\\d+),\\s*CAST\\(\\?\\s+AS\\s+DATE\\)\\)", RegexOption.IGNORE_CASE)
        ) {
            val n = it.groupValues[1].toInt()
            if (n >= 0) "date(?, '+$n days')" else "date(?, '$n days')"
        }

        // DATEADD(DAY, N, CAST(col AS DATE)) — literal N, column expression
        s = s.replace(
            Regex("DATEADD\\(DAY,\\s*(-?\\d+),\\s*CAST\\(([^)]+)\\s+AS\\s+DATE\\)\\)", RegexOption.IGNORE_CASE)
        ) {
            val n   = it.groupValues[1].toInt()
            val col = it.groupValues[2].trim()
            if (n >= 0) "date($col, '+$n days')" else "date($col, '$n days')"
        }

        // DATEADD(DAY, N, col) — literal N
        s = s.replace(
            Regex("DATEADD\\(DAY,\\s*(-?\\d+),\\s*([\\w.]+)\\)", RegexOption.IGNORE_CASE)
        ) {
            val n   = it.groupValues[1].toInt()
            val col = it.groupValues[2].trim()
            if (n >= 0) "datetime($col, '+$n days')" else "datetime($col, '$n days')"
        }

        // DATEADD(DAY, ?, col) — parameterized N with column reference
        s = s.replace(
            Regex("DATEADD\\(DAY,\\s*\\?,\\s*([\\w.]+)\\)", RegexOption.IGNORE_CASE)
        ) { "datetime(${it.groupValues[1].trim()}, (? || ' days'))" }

        // DATEPART(HOUR, x) → CAST(strftime('%H', x) AS INTEGER)
        s = s.replace(
            Regex("DATEPART\\(HOUR,\\s*([^)]+)\\)", RegexOption.IGNORE_CASE)
        ) { "CAST(strftime('%H', ${it.groupValues[1].trim()}) AS INTEGER)" }

        // MONTH(x) / YEAR(x) / DAY(x) → strftime equivalents
        s = s.replace(Regex("\\bMONTH\\(([^)]+)\\)", RegexOption.IGNORE_CASE))
            { "CAST(strftime('%m', ${it.groupValues[1].trim()}) AS INTEGER)" }
        s = s.replace(Regex("\\bYEAR\\(([^)]+)\\)", RegexOption.IGNORE_CASE))
            { "CAST(strftime('%Y', ${it.groupValues[1].trim()}) AS INTEGER)" }
        s = s.replace(Regex("\\bDAY\\(([^)]+)\\)", RegexOption.IGNORE_CASE))
            { "CAST(strftime('%d', ${it.groupValues[1].trim()}) AS INTEGER)" }

        // CONVERT(varchar(n), x, 120) → strftime('%Y-%m-%d', x) (ISO date format)
        s = s.replace(
            Regex("CONVERT\\(varchar\\(\\d+\\),\\s*([^,)]+),\\s*120\\)", RegexOption.IGNORE_CASE)
        ) { "strftime('%Y-%m-%d', ${it.groupValues[1].trim()})" }

        // CONVERT(DATE, x) → date(x)
        s = s.replace(
            Regex("CONVERT\\(DATE,\\s*([^)]+)\\)", RegexOption.IGNORE_CASE)
        ) { "date(${it.groupValues[1].trim()})" }

        // CAST(x AS DATE) → date(x)
        s = s.replace(
            Regex("CAST\\(([^)]+?)\\s+AS\\s+DATE\\)", RegexOption.IGNORE_CASE)
        ) { "date(${it.groupValues[1].trim()})" }

        // CAST(x AS NVARCHAR(n)) → CAST(x AS TEXT)
        s = s.replace(
            Regex("CAST\\(([^)]+?)\\s+AS\\s+NVARCHAR\\(\\d+\\)\\)", RegexOption.IGNORE_CASE)
        ) { "CAST(${it.groupValues[1].trim()} AS TEXT)" }

        // TRY_CAST → CAST (SQLite CAST never throws; returns 0/NULL on bad input)
        s = s.replace(Regex("\\bTRY_CAST\\(", RegexOption.IGNORE_CASE), "CAST(")

        // SCOPE_IDENTITY() → last_insert_rowid()
        s = s.replace(Regex("\\bSCOPE_IDENTITY\\(\\)", RegexOption.IGNORE_CASE), "last_insert_rowid()")
        s = s.replace(Regex("\\bIDENT_CURRENT\\([^)]+\\)", RegexOption.IGNORE_CASE), "last_insert_rowid()")

        // Append LIMIT if TOP was found
        if (limit != null) {
            s = s.trimEnd().trimEnd(';')
            s = "$s LIMIT $limit"
        }

        return s
    }
}
