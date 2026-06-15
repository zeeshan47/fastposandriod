package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Expense
import com.fastpos.android.utils.SessionManager
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val audit:   AuditLogRepository,
    private val session: SessionManager
) {

    suspend fun getExpenses(
        fromDate: Date,
        toDate:   Date,
        type:     String = ""
    ): List<Expense> {
        val typeClause = if (type.isNotBlank()) "AND ExpenseType = ?" else ""
        val params = mutableListOf<Any?>(
            java.sql.Date(fromDate.time),
            java.sql.Date(toDate.time),
            session.currentBranchId.value
        )
        if (type.isNotBlank()) params += type
        return db.query(
            """SELECT * FROM Expenses
               WHERE IsActive = 1
               AND CAST(ExpenseDate AS DATE) >= ?
               AND CAST(ExpenseDate AS DATE) <= ?
               AND ISNULL(BranchId,1) = ?
               $typeClause
               ORDER BY ExpenseDate DESC""",
            params
        ) { rs -> mapExpense(rs) }
    }

    suspend fun getTodayExpenses(): List<Expense> = db.query(
        "SELECT * FROM Expenses WHERE CAST(ExpenseDate AS DATE) = CAST(GETDATE() AS DATE) AND IsActive=1 ORDER BY ExpenseDate DESC"
    ) { rs -> mapExpense(rs) }

    suspend fun getExpensesByShift(shiftId: Int): List<Expense> = db.query(
        "SELECT * FROM Expenses WHERE ShiftId = ? AND IsActive=1 AND ISNULL(BranchId,1) = ? ORDER BY ExpenseDate DESC",
        listOf(shiftId, session.currentBranchId.value)
    ) { rs -> mapExpense(rs) }

    suspend fun addExpense(
        shiftId:       Int?,
        type:          String,
        description:   String,
        amount:        Double,
        paidTo:        String,
        paymentMethod: String = "Cash",
        createdBy:     Int
    ): Int {
        val id = db.insertAndGetId(
            "INSERT INTO Expenses (ShiftId, BranchId, ExpenseDate, ExpenseType, Description, Amount, PaidTo, PaymentMethod, IsActive, CreatedAt, CreatedBy) VALUES (?,?,GETDATE(),?,?,?,?,?,1,GETDATE(),?)",
            listOf(shiftId, session.currentBranchId.value, type.ifBlank { null }, description, amount, paidTo.ifBlank { null }, paymentMethod, createdBy)
        )
        if (paymentMethod.equals("Cash", ignoreCase = true) && shiftId != null) {
            runCatching {
                val reason = type.ifBlank { description }.take(50)
                db.execute(
                    "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'Out',?,?,?,?,GETDATE())",
                    listOf(shiftId, session.currentBranchId.value, amount, "Expense: $reason", description.take(100).ifBlank { null }, createdBy)
                )
            }
        }
        runCatching { audit.writeAudit(createdBy, "INSERT", "Expenses", id) }
        // Accounting: Debit Expenses / Credit Cash
        if (paymentMethod.equals("Cash", ignoreCase = true)) runCatching {
            val dId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='5100' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
            val cId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='1000' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
            if (dId > 0 && cId > 0) {
                val sql = "INSERT INTO AccountLedger (AccountId,EntryDate,Debit,Credit,ReferenceType,ReferenceId,Narration,CreatedBy,CreatedAt) VALUES (?,GETDATE(),?,?,?,?,?,?,GETDATE())"
                db.execute(sql, listOf(dId, amount, 0.0,    "Expense", id, "Expense: ${type.ifBlank { description }}", createdBy))
                db.execute(sql, listOf(cId, 0.0,    amount, "Expense", id, "Expense: ${type.ifBlank { description }}", createdBy))
            }
        }
        return id
    }

    suspend fun updateExpense(
        expenseId:     Int,
        type:          String,
        description:   String,
        amount:        Double,
        paidTo:        String,
        paymentMethod: String = "Cash",
        updatedBy:     Int
    ) {
        // Read old state before updating so we can adjust CashTransactions
        val old = runCatching {
            db.queryOne(
                "SELECT Amount, ISNULL(PaymentMethod,'Cash') AS PaymentMethod, ShiftId FROM Expenses WHERE ExpenseId = ? AND IsActive = 1",
                listOf(expenseId)
            ) { rs -> Triple(rs.getDouble("Amount"), rs.getString("PaymentMethod") ?: "Cash", rs.getInt("ShiftId").takeIf { !rs.wasNull() }) }
        }.getOrNull()

        db.execute(
            "UPDATE Expenses SET ExpenseType=?, Description=?, Amount=?, PaidTo=?, PaymentMethod=?, UpdatedAt=GETDATE(), UpdatedBy=? WHERE ExpenseId=?",
            listOf(type, description, amount, paidTo.ifBlank { null }, paymentMethod, updatedBy, expenseId)
        )

        runCatching {
            val shiftId = old?.third
            if (old != null && old.second.equals("Cash", ignoreCase = true) && shiftId != null) {
                db.execute(
                    "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'In',?,'Expense Updated','Reversal of updated expense',?,GETDATE())",
                    listOf(shiftId, session.currentBranchId.value, old.first, updatedBy)
                )
            }
            if (paymentMethod.equals("Cash", ignoreCase = true) && shiftId != null) {
                db.execute(
                    "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'Out',?,?,?,?,GETDATE())",
                    listOf(shiftId, session.currentBranchId.value, amount, "Expense: ${type.ifBlank { description }.take(50)}", description.take(100).ifBlank { null }, updatedBy)
                )
            }
        }
        runCatching { audit.writeAudit(updatedBy, "UPDATE", "Expenses", expenseId) }
    }

    suspend fun deleteExpense(expenseId: Int, updatedBy: Int) {
        val old = runCatching {
            db.queryOne(
                "SELECT Amount, ISNULL(PaymentMethod,'Cash') AS PaymentMethod, ShiftId FROM Expenses WHERE ExpenseId = ? AND IsActive = 1",
                listOf(expenseId)
            ) { rs -> Triple(rs.getDouble("Amount"), rs.getString("PaymentMethod") ?: "Cash", rs.getInt("ShiftId").takeIf { !rs.wasNull() }) }
        }.getOrNull()

        db.execute(
            "UPDATE Expenses SET IsActive=0, UpdatedAt=GETDATE(), UpdatedBy=? WHERE ExpenseId=?",
            listOf(updatedBy, expenseId)
        )

        if (old != null && old.second.equals("Cash", ignoreCase = true) && old.third != null) {
            runCatching {
                db.execute(
                    "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'In',?,'Expense Deleted','Reversal of deleted expense',?,GETDATE())",
                    listOf(old.third, session.currentBranchId.value, old.first, updatedBy)
                )
            }
        }
        runCatching { audit.writeAudit(updatedBy, "DELETE", "Expenses", expenseId) }
    }

    private fun mapExpense(rs: java.sql.ResultSet) = Expense(
        expenseId     = rs.getInt("ExpenseId"),
        shiftId       = rs.getInt("ShiftId").takeIf { it > 0 },
        expenseDate   = rs.getTimestamp("ExpenseDate") ?: Date(),
        expenseType   = rs.getString("ExpenseType") ?: "",
        description   = rs.getString("Description") ?: "",
        amount        = rs.getDouble("Amount"),
        paidTo        = rs.getString("PaidTo") ?: "",
        paymentMethod = try { rs.getString("PaymentMethod") ?: "Cash" } catch (_: Exception) { "Cash" },
        createdAt     = rs.getTimestamp("CreatedAt") ?: Date()
    )
}
