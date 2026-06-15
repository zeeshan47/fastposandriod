package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.CashTransaction
import com.fastpos.android.utils.SessionManager
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashDrawerRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val audit:   AuditLogRepository,
    private val session: SessionManager
) {

    suspend fun initSchema() {
        try {
            db.execute(
                """IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='CashTransactions')
                   CREATE TABLE CashTransactions (
                       TransactionId    INT IDENTITY(1,1) PRIMARY KEY,
                       ShiftId          INT NOT NULL,
                       BranchId         INT NOT NULL DEFAULT 1,
                       TransactionType  NVARCHAR(10) NOT NULL DEFAULT 'In',
                       Amount           DECIMAL(18,2) NOT NULL DEFAULT 0,
                       Reason           NVARCHAR(200) NOT NULL DEFAULT '',
                       Notes            NVARCHAR(500) NULL,
                       CreatedBy        INT NOT NULL DEFAULT 0,
                       CreatedAt        DATETIME NOT NULL DEFAULT GETDATE()
                   )"""
            )
        } catch (_: Exception) {}
    }

    suspend fun getByShift(shiftId: Int): List<CashTransaction> = try {
        db.query(
            """SELECT TransactionId, ShiftId, TransactionType, Amount,
                      ISNULL(Reason,'') AS Reason,
                      ISNULL(Notes,'') AS Notes,
                      ISNULL(CreatedBy,0) AS CreatedBy,
                      CreatedAt
               FROM CashTransactions
               WHERE ShiftId = ?
               ORDER BY CreatedAt""",
            listOf(shiftId)
        ) { rs ->
            CashTransaction(
                transactionId   = rs.getInt("TransactionId"),
                shiftId         = rs.getInt("ShiftId"),
                transactionType = rs.getString("TransactionType") ?: "In",
                amount          = rs.getDouble("Amount"),
                reason          = try { rs.getString("Reason") ?: "" } catch (_: Exception) { "" },
                notes           = rs.getString("Notes") ?: "",
                createdBy       = rs.getInt("CreatedBy"),
                createdAt       = rs.getTimestamp("CreatedAt") ?: Date()
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun addTransaction(shiftId: Int, type: String, amount: Double, reason: String, notes: String, createdBy: Int): Int {
        val branchId = session.currentBranchId.value
        val id = db.insertAndGetId(
            """INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt)
               VALUES (?,?,?,?,?,?,?,GETDATE())""",
            listOf(shiftId, branchId, type, amount, reason, notes, createdBy)
        )
        runCatching { audit.writeAudit(createdBy, "INSERT", "CashTransactions", id) }
        return id
    }

    suspend fun deleteTransaction(transactionId: Int, deletedBy: Int = 0) {
        db.execute("DELETE FROM CashTransactions WHERE TransactionId = ?", listOf(transactionId))
        runCatching { audit.writeAudit(deletedBy, "DELETE", "CashTransactions", transactionId) }
    }

    suspend fun getTotalIn(shiftId: Int): Double = try {
        db.queryOne(
            "SELECT ISNULL(SUM(Amount),0) AS TotalIn FROM CashTransactions WHERE ShiftId=? AND TransactionType='In' AND ISNULL(Reason,'') != 'Opening Cash'",
            listOf(shiftId)
        ) { it.getDouble("TotalIn") } ?: 0.0
    } catch (_: Exception) { 0.0 }

    suspend fun getTotalOut(shiftId: Int): Double = try {
        db.queryOne(
            "SELECT ISNULL(SUM(Amount),0) AS TotalOut FROM CashTransactions WHERE ShiftId=? AND TransactionType='Out'",
            listOf(shiftId)
        ) { it.getDouble("TotalOut") } ?: 0.0
    } catch (_: Exception) { 0.0 }

    /** Returns cash collected from orders in this shift — one row per order (grouped to avoid duplicates from partial payments). */
    suspend fun getShiftOrderPayments(shiftId: Int): List<CashTransaction> = try {
        db.query(
            """SELECT o.OrderId AS TransactionId,
                      ISNULL(SUM(op.Amount), 0) AS Amount,
                      ISNULL(o.OrderNo,'') AS OrderNo,
                      ISNULL(o.OrderType,'') AS OrderType,
                      o.CreatedAt
               FROM OrderPayments op
               INNER JOIN Orders o ON op.OrderId = o.OrderId
               WHERE o.ShiftId = ?
                 AND op.PaymentMethod = 'Cash'
                 AND o.OrderStatus != 'Cancelled'
               GROUP BY o.OrderId, o.OrderNo, o.OrderType, o.CreatedAt
               ORDER BY o.CreatedAt""",
            listOf(shiftId)
        ) { rs ->
            CashTransaction(
                transactionId   = rs.getInt("TransactionId"),
                shiftId         = shiftId,
                transactionType = "Sale",
                amount          = rs.getDouble("Amount"),
                reason          = "${rs.getString("OrderNo") ?: ""} (${rs.getString("OrderType") ?: ""})",
                notes           = "",
                createdBy       = 0,
                createdAt       = rs.getTimestamp("CreatedAt") ?: Date()
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getTotalSales(shiftId: Int): Double = try {
        db.queryOne(
            """SELECT ISNULL(SUM(op.Amount),0) AS TotalSales
               FROM OrderPayments op
               INNER JOIN Orders o ON op.OrderId = o.OrderId
               WHERE o.ShiftId = ? AND op.PaymentMethod = 'Cash' AND o.OrderStatus != 'Cancelled'""",
            listOf(shiftId)
        ) { it.getDouble("TotalSales") } ?: 0.0
    } catch (_: Exception) { 0.0 }
}
