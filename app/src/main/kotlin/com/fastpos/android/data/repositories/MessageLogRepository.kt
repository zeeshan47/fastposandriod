package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.MessageLog
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageLogRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun getRecent(
        fromDate: Date,
        toDate:   Date,
        channel:  String? = null,
        status:   String? = null,
        top:      Int = 500
    ): List<MessageLog> {
        val channelClause = if (channel != null) "AND ml.Channel = ?" else ""
        val statusClause  = if (status  != null) "AND ml.Status  = ?" else ""
        val params = mutableListOf<Any?>(
            java.sql.Date(fromDate.time),
            java.sql.Date(toDate.time)
        )
        if (channel != null) params.add(channel)
        if (status  != null) params.add(status)

        return try {
            db.query("""
                SELECT TOP $top
                    ml.MessageId, ml.OrderId,
                    ISNULL(o.OrderNo,'') AS OrderNo,
                    ISNULL(ml.RecipientPhone,'') AS RecipientPhone,
                    ISNULL(ml.Channel,'')       AS Channel,
                    ISNULL(ml.MessageText,'')   AS MessageText,
                    ISNULL(ml.Status,'')        AS Status,
                    ISNULL(ml.ErrorMessage,'')  AS ErrorMessage,
                    ml.SentAt, ml.CreatedAt
                FROM MessageLog ml
                LEFT JOIN Orders o ON o.OrderId = ml.OrderId
                WHERE ml.CreatedAt >= ?
                  AND ml.CreatedAt < DATEADD(day,1,CAST(? AS DATE))
                  $channelClause $statusClause
                ORDER BY ml.CreatedAt DESC
            """.trimIndent(), params) { rs ->
                MessageLog(
                    messageId      = rs.getInt("MessageId"),
                    orderId        = rs.getInt("OrderId").takeIf { !rs.wasNull() },
                    orderNo        = rs.getString("OrderNo") ?: "",
                    recipientPhone = rs.getString("RecipientPhone") ?: "",
                    channel        = rs.getString("Channel") ?: "",
                    messageText    = rs.getString("MessageText") ?: "",
                    status         = rs.getString("Status") ?: "",
                    errorMessage   = rs.getString("ErrorMessage") ?: "",
                    sentAt         = rs.getTimestamp("SentAt"),
                    createdAt      = rs.getTimestamp("CreatedAt") ?: Date()
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getFailedCountToday(): Int = try {
        db.queryOne(
            "SELECT COUNT(1) AS cnt FROM MessageLog WHERE Status = 'Failed' AND CreatedAt >= CAST(GETDATE() AS DATE)",
            emptyList()
        ) { it.getInt("cnt") } ?: 0
    } catch (_: Exception) { 0 }

    suspend fun getDistinctChannels(): List<String> = try {
        db.query(
            "SELECT DISTINCT Channel FROM MessageLog WHERE Channel IS NOT NULL AND Channel <> '' ORDER BY Channel",
            emptyList()
        ) { it.getString("Channel") ?: "" }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }
}
