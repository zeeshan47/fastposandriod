package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.AccountLedgerEntry
import com.fastpos.android.data.models.ChartOfAccount
import com.fastpos.android.data.models.TrialBalanceRow
import com.fastpos.android.utils.SessionManager
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountLedgerRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val session: SessionManager
) {
    companion object {
        const val CODE_CASH          = "1000"
        const val CODE_AR_DELIVERY   = "1100"
        const val CODE_AP_SUPPLIERS  = "2000"
        const val CODE_SALES_REVENUE = "4000"
        const val CODE_COGS          = "5000"
        const val CODE_EXPENSES      = "5100"
        const val CODE_SALARY        = "5200"
        const val CODE_PURCHASE      = "5300"
    }

    // ── Chart of Accounts ──────────────────────────────────────────────────────

    suspend fun getAllAccounts(): List<ChartOfAccount> = runCatching {
        db.query(
            "SELECT AccountId, AccountCode, AccountName, AccountType, IsActive FROM ChartOfAccounts WHERE IsActive = 1 ORDER BY AccountCode",
            emptyList()
        ) { rs ->
            ChartOfAccount(
                accountId   = rs.getInt("AccountId"),
                accountCode = rs.getString("AccountCode") ?: "",
                accountName = rs.getString("AccountName") ?: "",
                accountType = rs.getString("AccountType") ?: "",
                isActive    = rs.getBoolean("IsActive")
            )
        }
    }.getOrDefault(emptyList())

    // ── Auto-post both sides of a journal entry ─────────────────────────────────

    suspend fun post(
        referenceType: String,
        referenceId:   Int?,
        debitCode:     String,
        creditCode:    String,
        amount:        Double,
        narration:     String,
        createdBy:     Int = 0
    ) {
        if (amount <= 0.0) return
        runCatching {
            val debitId  = db.queryOne(
                "SELECT AccountId FROM ChartOfAccounts WHERE AccountCode = ? AND IsActive = 1",
                listOf(debitCode)
            ) { it.getInt("AccountId") } ?: return@runCatching

            val creditId = db.queryOne(
                "SELECT AccountId FROM ChartOfAccounts WHERE AccountCode = ? AND IsActive = 1",
                listOf(creditCode)
            ) { it.getInt("AccountId") } ?: return@runCatching

            val sql = """INSERT INTO AccountLedger
                (AccountId, EntryDate, Debit, Credit, ReferenceType, ReferenceId, Narration, CreatedBy, CreatedAt)
                VALUES (?, GETDATE(), ?, ?, ?, ?, ?, ?, GETDATE())"""

            // Debit side
            db.execute(sql, listOf(debitId,  amount, 0.0,    referenceType, referenceId, narration, createdBy))
            // Credit side
            db.execute(sql, listOf(creditId, 0.0,    amount, referenceType, referenceId, narration, createdBy))
        }
    }

    // ── Account Ledger ─────────────────────────────────────────────────────────

    suspend fun getLedger(
        accountId: Int,
        fromDate:  Date,
        toDate:    Date
    ): List<AccountLedgerEntry> = runCatching {
        val rows = db.query(
            """SELECT al.LedgerId, al.AccountId, ca.AccountCode, ca.AccountName,
                      al.EntryDate, al.Debit, al.Credit,
                      al.ReferenceType, al.ReferenceId, al.Narration, al.CreatedAt
               FROM AccountLedger al
               INNER JOIN ChartOfAccounts ca ON ca.AccountId = al.AccountId
               WHERE al.AccountId = ?
                 AND CAST(al.EntryDate AS DATE) >= ?
                 AND CAST(al.EntryDate AS DATE) <= ?
               ORDER BY al.EntryDate ASC, al.LedgerId ASC""",
            listOf(accountId, java.sql.Date(fromDate.time), java.sql.Date(toDate.time))
        ) { rs ->
            AccountLedgerEntry(
                ledgerId      = rs.getInt("LedgerId"),
                accountId     = rs.getInt("AccountId"),
                accountCode   = rs.getString("AccountCode") ?: "",
                accountName   = rs.getString("AccountName") ?: "",
                entryDate     = rs.getTimestamp("EntryDate") ?: Date(),
                debit         = rs.getDouble("Debit"),
                credit        = rs.getDouble("Credit"),
                referenceType = rs.getString("ReferenceType") ?: "",
                referenceId   = rs.getInt("ReferenceId").takeIf { it > 0 },
                narration     = rs.getString("Narration") ?: "",
                createdAt     = rs.getTimestamp("CreatedAt") ?: Date()
            )
        }
        // Calculate running balance
        var balance = 0.0
        rows.forEach { e -> balance += e.debit - e.credit; e.balance = balance }
        rows
    }.getOrDefault(emptyList())

    // ── Trial Balance ──────────────────────────────────────────────────────────

    suspend fun getTrialBalance(
        fromDate: Date,
        toDate:   Date
    ): List<TrialBalanceRow> = runCatching {
        db.query(
            """SELECT ca.AccountId, ca.AccountCode, ca.AccountName, ca.AccountType,
                      ISNULL(SUM(al.Debit),  0) AS TotalDebit,
                      ISNULL(SUM(al.Credit), 0) AS TotalCredit
               FROM ChartOfAccounts ca
               LEFT JOIN AccountLedger al
                   ON al.AccountId = ca.AccountId
                   AND CAST(al.EntryDate AS DATE) >= ?
                   AND CAST(al.EntryDate AS DATE) <= ?
               WHERE ca.IsActive = 1
               GROUP BY ca.AccountId, ca.AccountCode, ca.AccountName, ca.AccountType
               ORDER BY ca.AccountCode""",
            listOf(java.sql.Date(fromDate.time), java.sql.Date(toDate.time))
        ) { rs ->
            TrialBalanceRow(
                accountId   = rs.getInt("AccountId"),
                accountCode = rs.getString("AccountCode") ?: "",
                accountName = rs.getString("AccountName") ?: "",
                accountType = rs.getString("AccountType") ?: "",
                totalDebit  = rs.getDouble("TotalDebit"),
                totalCredit = rs.getDouble("TotalCredit")
            )
        }
    }.getOrDefault(emptyList())
}
