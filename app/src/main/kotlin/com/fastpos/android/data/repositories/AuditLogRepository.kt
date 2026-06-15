package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.AuditLogRow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun getLogs(
        from:      Date,
        to:        Date,
        action:    String? = null,
        tableName: String? = null
    ): List<AuditLogRow> = try {
        var sql = """SELECT a.LogId, a.LoggedAt,
                            ISNULL(u.FullName, 'System') AS UserName,
                            a.Action, a.TableName, a.RecordId,
                            ISNULL(a.MachineName,'') AS MachineName
                     FROM AuditLogs a
                     LEFT JOIN Users u ON u.UserId = a.UserId
                     WHERE CAST(a.LoggedAt AS DATE) BETWEEN CAST(? AS DATE) AND CAST(? AS DATE)"""
        val params = mutableListOf<Any?>(from, to)
        if (!action.isNullOrBlank()) { sql += " AND a.Action = ?";    params += action }
        if (!tableName.isNullOrBlank()) { sql += " AND a.TableName = ?"; params += tableName }
        sql += " ORDER BY a.LoggedAt DESC"
        db.query(sql, params) { rs ->
            AuditLogRow(
                logId       = rs.getInt("LogId"),
                loggedAt    = rs.getTimestamp("LoggedAt") ?: Date(),
                userName    = rs.getString("UserName") ?: "System",
                action      = rs.getString("Action") ?: "",
                tableName   = rs.getString("TableName") ?: "",
                recordId    = rs.getInt("RecordId").takeIf { it > 0 },
                machineName = rs.getString("MachineName") ?: ""
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getDistinctActions(): List<String> = try {
        db.query(
            "SELECT DISTINCT Action FROM AuditLogs WHERE Action IS NOT NULL ORDER BY Action"
        ) { it.getString("Action") ?: "" }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    suspend fun getDistinctTables(): List<String> = try {
        db.query(
            "SELECT DISTINCT TableName FROM AuditLogs WHERE TableName IS NOT NULL ORDER BY TableName"
        ) { it.getString("TableName") ?: "" }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    suspend fun writeAudit(userId: Int, action: String, tableName: String, recordId: Int) {
        try {
            db.execute(
                """INSERT INTO AuditLogs (UserId, Action, TableName, RecordId, LoggedAt)
                   VALUES (?,?,?,?,GETDATE())""",
                listOf(userId, action, tableName, recordId)
            )
        } catch (_: Exception) {}
    }
}
