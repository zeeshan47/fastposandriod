package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.CustomerFeedback
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerFeedbackRepository @Inject constructor(
    private val db: DatabaseHelper,
    private val audit: AuditLogRepository
) {

    suspend fun initSchema() {
        try {
            db.execute("""
                IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'CustomerFeedback')
                CREATE TABLE CustomerFeedback (
                    FeedbackId   INT            IDENTITY(1,1) PRIMARY KEY,
                    CustomerId   INT            NULL,
                    OrderId      INT            NULL,
                    Rating       INT            NOT NULL DEFAULT 5,
                    Comment      NVARCHAR(1000) NULL,
                    FeedbackDate DATETIME       NOT NULL DEFAULT GETDATE(),
                    CreatedAt    DATETIME       NOT NULL DEFAULT GETDATE(),
                    CreatedBy    INT            NULL
                )
            """.trimIndent())
        } catch (_: Exception) {}
    }

    suspend fun getFeedbackForCustomer(customerId: Int): List<CustomerFeedback> = try {
        db.query(
            """SELECT cf.FeedbackId, cf.CustomerId, ISNULL(c.CustomerName,'') AS CustomerName,
                      cf.OrderId, cf.Rating, ISNULL(cf.Comment,'') AS Comment, cf.FeedbackDate
               FROM CustomerFeedback cf
               LEFT JOIN Customers c ON c.CustomerId = cf.CustomerId
               WHERE cf.CustomerId = ?
               ORDER BY cf.FeedbackDate DESC""",
            listOf(customerId)
        ) { rs -> mapFeedback(rs) }
    } catch (_: Exception) { emptyList() }

    suspend fun getRecentFeedback(fromDate: Date, toDate: Date, limit: Int = 200): List<CustomerFeedback> = try {
        db.query(
            """SELECT TOP ($limit) cf.FeedbackId, cf.CustomerId,
                      ISNULL(c.CustomerName,'Anonymous') AS CustomerName,
                      cf.OrderId, cf.Rating, ISNULL(cf.Comment,'') AS Comment, cf.FeedbackDate
               FROM CustomerFeedback cf
               LEFT JOIN Customers c ON c.CustomerId = cf.CustomerId
               WHERE CAST(cf.FeedbackDate AS DATE) BETWEEN CAST(? AS DATE) AND CAST(? AS DATE)
               ORDER BY cf.FeedbackDate DESC""",
            listOf(fromDate, toDate)
        ) { rs -> mapFeedback(rs) }
    } catch (_: Exception) { emptyList() }

    suspend fun addFeedback(customerId: Int?, orderId: Int?, rating: Int, comment: String, createdBy: Int): Int {
        val newId = db.insertAndGetId(
            """INSERT INTO CustomerFeedback (CustomerId, OrderId, Rating, Comment, FeedbackDate, CreatedAt, CreatedBy)
               VALUES (?, ?, ?, ?, GETDATE(), GETDATE(), ?)""",
            listOf(customerId, orderId, rating.coerceIn(1, 5), comment.ifBlank { null }, createdBy)
        )
        runCatching { audit.writeAudit(createdBy, "INSERT", "CustomerFeedback", newId) }
        return newId
    }

    suspend fun deleteFeedback(feedbackId: Int, deletedBy: Int = 0) {
        try {
            db.execute("DELETE FROM CustomerFeedback WHERE FeedbackId = ?", listOf(feedbackId))
            runCatching { audit.writeAudit(deletedBy, "DELETE", "CustomerFeedback", feedbackId) }
        } catch (_: Exception) {}
    }

    private fun mapFeedback(rs: java.sql.ResultSet) = CustomerFeedback(
        feedbackId   = rs.getInt("FeedbackId"),
        customerId   = rs.getInt("CustomerId").takeIf { !rs.wasNull() },
        customerName = rs.getString("CustomerName") ?: "",
        orderId      = try { rs.getInt("OrderId").takeIf { !rs.wasNull() } } catch (_: Exception) { null },
        rating       = rs.getInt("Rating"),
        comment      = rs.getString("Comment") ?: "",
        feedbackDate = rs.getTimestamp("FeedbackDate") ?: Date()
    )
}
