package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.WalletTransaction
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerWalletRepository @Inject constructor(
    private val db: DatabaseHelper,
    private val audit: AuditLogRepository
) {

    suspend fun initSchema() {
        db.execute(
            "IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='CustomerWallet') " +
            "CREATE TABLE CustomerWallet (" +
            "WalletId INT IDENTITY(1,1) PRIMARY KEY, " +
            "CustomerId INT NOT NULL UNIQUE, " +
            "Balance DECIMAL(12,2) NOT NULL DEFAULT 0, " +
            "LastUpdated DATETIME DEFAULT GETDATE())"   // matches WPF column name
        )
        db.execute(
            "IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='WalletTransactions') " +
            "CREATE TABLE WalletTransactions (" +
            "TransId INT IDENTITY(1,1) PRIMARY KEY, " +
            "CustomerId INT NOT NULL, " +
            "OrderId INT NULL, " +
            "TransType NVARCHAR(20) NOT NULL, " +       // matches WPF column name
            "Amount DECIMAL(12,2) NOT NULL DEFAULT 0, " +
            "TransDate DATETIME DEFAULT GETDATE(), " +  // matches WPF column name
            "Notes NVARCHAR(500) NULL, " +
            "CreatedBy INT NULL)"
        )
    }

    suspend fun getBalance(customerId: Int): Double = try {
        db.queryOne(
            "SELECT ISNULL(Balance, 0) AS Balance FROM CustomerWallet WHERE CustomerId = ?",
            listOf(customerId)
        ) { it.getDouble("Balance") } ?: 0.0
    } catch (_: Exception) { 0.0 }

    private suspend fun ensureWalletRow(customerId: Int) {
        val count = db.queryOne(
            "SELECT COUNT(*) AS C FROM CustomerWallet WHERE CustomerId = ?",
            listOf(customerId)
        ) { it.getInt("C") } ?: 0
        if (count == 0) {
            db.execute("INSERT INTO CustomerWallet (CustomerId, Balance) VALUES (?, 0)", listOf(customerId))
        }
    }

    suspend fun topUp(customerId: Int, amount: Double, createdBy: Int) {
        ensureWalletRow(customerId)
        db.execute(
            "UPDATE CustomerWallet SET Balance = Balance + ?, LastUpdated = GETDATE() WHERE CustomerId = ?",
            listOf(amount, customerId)
        )
        db.execute(
            "INSERT INTO WalletTransactions (CustomerId, TransType, Amount, TransDate, CreatedBy) VALUES (?,?,?,GETDATE(),?)",
            listOf(customerId, "TopUp", amount, createdBy)
        )
        runCatching { audit.writeAudit(createdBy, "TOPUP", "CustomerWallet", customerId) }
    }

    suspend fun deduct(customerId: Int, amount: Double, orderId: Int?, createdBy: Int): Boolean {
        val balance = getBalance(customerId)
        if (balance < amount - 0.01) return false
        db.execute(
            "UPDATE CustomerWallet SET Balance = Balance - ?, LastUpdated = GETDATE() WHERE CustomerId = ?",
            listOf(amount, customerId)
        )
        db.execute(
            "INSERT INTO WalletTransactions (CustomerId, OrderId, TransType, Amount, TransDate, CreatedBy) VALUES (?,?,?,?,GETDATE(),?)",
            listOf(customerId, orderId, "Deduction", amount, createdBy)
        )
        runCatching { audit.writeAudit(createdBy, "DEDUCT", "CustomerWallet", customerId) }
        return true
    }

    suspend fun refund(customerId: Int, amount: Double, orderId: Int?, createdBy: Int) {
        ensureWalletRow(customerId)
        db.execute(
            "UPDATE CustomerWallet SET Balance = Balance + ?, LastUpdated = GETDATE() WHERE CustomerId = ?",
            listOf(amount, customerId)
        )
        db.execute(
            "INSERT INTO WalletTransactions (CustomerId, OrderId, TransType, Amount, TransDate, CreatedBy) VALUES (?,?,?,?,GETDATE(),?)",
            listOf(customerId, orderId, "Refund", amount, createdBy)
        )
        runCatching { audit.writeAudit(createdBy, "REFUND", "CustomerWallet", customerId) }
    }

    suspend fun getTransactions(customerId: Int): List<WalletTransaction> =
        db.query(
            "SELECT TOP 50 * FROM WalletTransactions WHERE CustomerId = ? ORDER BY TransDate DESC",
            listOf(customerId)
        ) { rs ->
            WalletTransaction(
                transId    = rs.getInt("TransId"),
                customerId = rs.getInt("CustomerId"),
                transDate  = try { rs.getTimestamp("TransDate") ?: Date() } catch (_: Exception) { Date() },
                transType  = try { rs.getString("TransType") ?: "TopUp" } catch (_: Exception) { "TopUp" },
                amount     = rs.getDouble("Amount"),
                orderId    = try { rs.getInt("OrderId").takeIf { !rs.wasNull() } } catch (_: Exception) { null },
                notes      = "",
                createdBy  = try { rs.getInt("CreatedBy").takeIf { !rs.wasNull() } } catch (_: Exception) { null }
            )
        }
}
