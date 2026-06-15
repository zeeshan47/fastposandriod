package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.WalletTransaction
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun getBalance(customerId: Int): Double =
        db.queryOne(
            "SELECT ISNULL(Balance, 0) AS Balance FROM CustomerWallet WHERE CustomerId = ?",
            listOf(customerId)
        ) { it.getDouble("Balance") } ?: 0.0

    suspend fun topUp(customerId: Int, amount: Double, createdBy: Int) {
        db.transaction { conn ->
            ensureWalletRow(conn, customerId)
            db.executeSync(conn,
                "UPDATE CustomerWallet SET Balance = Balance + ?, LastUpdated = GETDATE() WHERE CustomerId = ?",
                listOf(amount, customerId))
            db.executeSync(conn,
                "INSERT INTO WalletTransactions (CustomerId, OrderId, TransType, Amount, TransDate, CreatedBy) VALUES (?, NULL, 'TopUp', ?, GETDATE(), ?)",
                listOf(customerId, amount, createdBy))
        }
    }

    suspend fun deduct(customerId: Int, amount: Double, orderId: Int?, createdBy: Int) {
        db.transaction { conn ->
            db.executeSync(conn,
                "UPDATE CustomerWallet SET Balance = Balance - ?, LastUpdated = GETDATE() WHERE CustomerId = ?",
                listOf(amount, customerId))
            db.executeSync(conn,
                "INSERT INTO WalletTransactions (CustomerId, OrderId, TransType, Amount, TransDate, CreatedBy) VALUES (?, ?, 'Deduction', ?, GETDATE(), ?)",
                listOf(customerId, orderId, amount, createdBy))
        }
    }

    suspend fun refund(customerId: Int, amount: Double, orderId: Int?, createdBy: Int) {
        db.transaction { conn ->
            ensureWalletRow(conn, customerId)
            db.executeSync(conn,
                "UPDATE CustomerWallet SET Balance = Balance + ?, LastUpdated = GETDATE() WHERE CustomerId = ?",
                listOf(amount, customerId))
            db.executeSync(conn,
                "INSERT INTO WalletTransactions (CustomerId, OrderId, TransType, Amount, TransDate, CreatedBy) VALUES (?, ?, 'Refund', ?, GETDATE(), ?)",
                listOf(customerId, orderId, amount, createdBy))
        }
    }

    suspend fun getTransactions(customerId: Int): List<WalletTransaction> = db.query(
        """SELECT TransId, CustomerId, TransDate, TransType, Amount, OrderId, CreatedBy
           FROM WalletTransactions
           WHERE CustomerId = ?
           ORDER BY TransDate DESC""",
        listOf(customerId)
    ) { rs ->
        WalletTransaction(
            transId    = rs.getInt("TransId"),
            customerId = rs.getInt("CustomerId"),
            transDate  = try { rs.getTimestamp("TransDate") ?: Date() } catch (_: Exception) { Date() },
            transType  = try { rs.getString("TransType") ?: "TopUp" } catch (_: Exception) { "TopUp" },
            amount     = rs.getDouble("Amount"),
            orderId    = rs.getInt("OrderId").takeIf { !rs.wasNull() },
            notes      = "",
            createdBy  = rs.getInt("CreatedBy").takeIf { !rs.wasNull() }
        )
    }

    private fun ensureWalletRow(conn: java.sql.Connection, customerId: Int) {
        db.executeSync(conn,
            "INSERT INTO CustomerWallet (CustomerId, Balance) SELECT ?, 0 WHERE NOT EXISTS (SELECT 1 FROM CustomerWallet WHERE CustomerId = ?)",
            listOf(customerId, customerId))
    }
}
