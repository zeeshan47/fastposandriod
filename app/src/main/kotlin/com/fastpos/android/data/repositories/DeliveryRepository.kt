package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.CompanyBalance
import com.fastpos.android.data.models.DeliveryCompany
import com.fastpos.android.data.models.DeliverySettlement
import com.fastpos.android.data.models.Order
import com.fastpos.android.utils.SessionManager
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeliveryRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val session: SessionManager,
    private val audit:   AuditLogRepository
) {

    suspend fun initSchema() {
        db.execute("""
            IF NOT EXISTS (SELECT 1 FROM sysobjects WHERE name='DeliveryCompanies' AND xtype='U')
            CREATE TABLE DeliveryCompanies (
                CompanyId         INT IDENTITY(1,1) PRIMARY KEY,
                CompanyName       NVARCHAR(100) NOT NULL,
                CommissionPercent DECIMAL(5,2)  NOT NULL DEFAULT 0,
                IsActive          BIT           NOT NULL DEFAULT 1,
                CreatedAt         DATETIME      NOT NULL DEFAULT GETDATE()
            )
        """)
        // Seed "Own Rider" as CompanyId 1 if table was just created
        db.execute("""
            IF NOT EXISTS (SELECT 1 FROM DeliveryCompanies WHERE CompanyId = 1)
            INSERT INTO DeliveryCompanies (CompanyName, CommissionPercent, IsActive)
            VALUES ('Own Rider', 0, 1)
        """)
        db.execute("""
            IF NOT EXISTS (SELECT 1 FROM sysobjects WHERE name='DeliverySettlements' AND xtype='U')
            CREATE TABLE DeliverySettlements (
                SettlementId      INT IDENTITY(1,1) PRIMARY KEY,
                DeliveryCompanyId INT           NOT NULL,
                SettlementDate    DATE          NOT NULL,
                AmountReceived    DECIMAL(12,2) NOT NULL,
                Notes             NVARCHAR(500),
                CreatedAt         DATETIME      NOT NULL DEFAULT GETDATE()
            )
        """)
    }

    // ── Companies ──────────────────────────────────────────────────────────────

    suspend fun getAllCompanies(): List<DeliveryCompany> = db.query(
        "SELECT * FROM DeliveryCompanies ORDER BY CompanyId"
    ) { rs ->
        DeliveryCompany(
            companyId         = rs.getInt("CompanyId"),
            companyName       = rs.getString("CompanyName") ?: "",
            commissionPercent = rs.getDouble("CommissionPercent"),
            isActive          = rs.getBoolean("IsActive")
        )
    }

    suspend fun getActiveCompanies(): List<DeliveryCompany> = db.query(
        "SELECT * FROM DeliveryCompanies WHERE IsActive = 1 ORDER BY CompanyId"
    ) { rs ->
        DeliveryCompany(
            companyId         = rs.getInt("CompanyId"),
            companyName       = rs.getString("CompanyName") ?: "",
            commissionPercent = rs.getDouble("CommissionPercent"),
            isActive          = rs.getBoolean("IsActive")
        )
    }

    suspend fun saveCompany(c: DeliveryCompany) {
        val userId = session.currentUser.value?.userId ?: 0
        if (c.companyId == 0) {
            db.execute(
                "INSERT INTO DeliveryCompanies (CompanyName, CommissionPercent, IsActive) VALUES (?, ?, ?)",
                listOf(c.companyName, c.commissionPercent, if (c.isActive) 1 else 0)
            )
            val newId = db.queryOne(
                "SELECT ISNULL(MAX(CompanyId),0) AS id FROM DeliveryCompanies",
                emptyList()
            ) { rs -> rs.getInt("id") } ?: 0
            runCatching { audit.writeAudit(userId, "INSERT", "DeliveryCompanies", newId) }
        } else {
            db.execute(
                "UPDATE DeliveryCompanies SET CompanyName=?, CommissionPercent=?, IsActive=? WHERE CompanyId=?",
                listOf(c.companyName, c.commissionPercent, if (c.isActive) 1 else 0, c.companyId)
            )
            runCatching { audit.writeAudit(userId, "UPDATE", "DeliveryCompanies", c.companyId) }
        }
    }

    /** Returns true if hard-deleted, false if only soft-deactivated (in use). */
    suspend fun deleteCompany(companyId: Int): Boolean {
        if (companyId == 1) return false
        val userId = session.currentUser.value?.userId ?: 0
        val inUse = db.queryOne(
            "SELECT COUNT(1) AS cnt FROM Orders WHERE DeliveryCompanyId = ? AND DeliveryCompanyId != 1",
            listOf(companyId)
        ) { rs -> rs.getInt("cnt") } ?: 0
        return if (inUse > 0) {
            db.execute(
                "UPDATE DeliveryCompanies SET IsActive=0 WHERE CompanyId=?",
                listOf(companyId)
            )
            runCatching { audit.writeAudit(userId, "DEACTIVATE", "DeliveryCompanies", companyId) }
            false
        } else {
            db.execute("DELETE FROM DeliveryCompanies WHERE CompanyId=?", listOf(companyId))
            runCatching { audit.writeAudit(userId, "DELETE", "DeliveryCompanies", companyId) }
            true
        }
    }

    // ── Settlements ────────────────────────────────────────────────────────────

    suspend fun getSettlements(
        fromDate: Date? = null,
        toDate: Date? = null,
        companyId: Int? = null
    ): List<DeliverySettlement> {
        val sql = buildString {
            append("""
                SELECT ds.SettlementId, ds.DeliveryCompanyId, ds.SettlementDate,
                       ds.AmountReceived, ds.Notes, dc.CompanyName
                FROM DeliverySettlements ds
                INNER JOIN DeliveryCompanies dc ON dc.CompanyId = ds.DeliveryCompanyId
                WHERE 1=1
            """)
            if (fromDate != null)  append(" AND ds.SettlementDate >= ?")
            if (toDate != null)    append(" AND ds.SettlementDate <= ?")
            if (companyId != null) append(" AND ds.DeliveryCompanyId = ?")
            append(" ORDER BY ds.SettlementDate DESC, ds.SettlementId DESC")
        }
        val params = mutableListOf<Any?>()
        if (fromDate != null)  params.add(java.sql.Date(fromDate.time))
        if (toDate != null)    params.add(java.sql.Date(toDate.time))
        if (companyId != null) params.add(companyId)
        return db.query(sql, params) { rs ->
            DeliverySettlement(
                settlementId      = rs.getInt("SettlementId"),
                deliveryCompanyId = rs.getInt("DeliveryCompanyId"),
                companyName       = rs.getString("CompanyName") ?: "",
                settlementDate    = rs.getDate("SettlementDate"),
                amountReceived    = rs.getDouble("AmountReceived"),
                notes             = rs.getString("Notes") ?: ""
            )
        }
    }

    suspend fun getPendingBalance(company: DeliveryCompany): Double {
        val totalOrders = db.queryOne(
            "SELECT ISNULL(SUM(GrandTotal),0) AS t FROM Orders WHERE DeliveryCompanyId=? AND OrderType='Delivery' AND OrderStatus='Completed'",
            listOf(company.companyId)
        ) { rs -> rs.getDouble("t") } ?: 0.0
        val totalSettled = db.queryOne(
            "SELECT ISNULL(SUM(AmountReceived),0) AS t FROM DeliverySettlements WHERE DeliveryCompanyId=?",
            listOf(company.companyId)
        ) { rs -> rs.getDouble("t") } ?: 0.0
        val netExpected = totalOrders * (1.0 - company.commissionPercent / 100.0)
        return netExpected - totalSettled
    }

    suspend fun getCompanyBalances(companies: List<DeliveryCompany>): List<CompanyBalance> =
        companies.map { c ->
            CompanyBalance(
                companyId   = c.companyId,
                companyName = c.companyName,
                balance     = runCatching { getPendingBalance(c) }.getOrDefault(0.0)
            )
        }

    suspend fun saveSettlement(s: DeliverySettlement, companyName: String = "") {
        val userId = session.currentUser.value?.userId ?: 0
        val isNew = s.settlementId == 0
        if (isNew) {
            db.execute(
                "INSERT INTO DeliverySettlements (DeliveryCompanyId, SettlementDate, AmountReceived, Notes) VALUES (?, ?, ?, ?)",
                listOf(s.deliveryCompanyId, java.sql.Date(s.settlementDate.time), s.amountReceived, s.notes.ifBlank { null })
            )
            val newId = db.queryOne(
                "SELECT ISNULL(MAX(SettlementId),0) AS id FROM DeliverySettlements",
                emptyList()
            ) { rs -> rs.getInt("id") } ?: 0
            runCatching { audit.writeAudit(userId, "INSERT", "DeliverySettlements", newId) }
        } else {
            db.execute(
                "UPDATE DeliverySettlements SET SettlementDate=?, AmountReceived=?, Notes=? WHERE SettlementId=?",
                listOf(java.sql.Date(s.settlementDate.time), s.amountReceived, s.notes.ifBlank { null }, s.settlementId)
            )
        }
        // Record cash received as a Cash Drawer 'In' entry so it appears in the shift ledger
        if (isNew && s.amountReceived > 0) {
            val shiftId  = session.currentShift.value?.shiftId
            val branchId = session.currentBranchId.value
            val userId   = session.userId
            val reason   = "Delivery Settlement${if (companyName.isNotBlank()) " - $companyName" else ""}"
            runCatching {
                db.execute(
                    """INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt)
                       VALUES (?, ?, 'In', ?, ?, ?, ?, GETDATE())""",
                    listOf(shiftId, branchId, s.amountReceived, reason, s.notes.ifBlank { null }, userId)
                )
            }
            // Accounting: Debit Cash / Credit AR-Delivery for settlement received
            runCatching {
                val dId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='1000' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
                val cId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='1100' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
                if (dId > 0 && cId > 0) {
                    val sql = "INSERT INTO AccountLedger (AccountId,EntryDate,Debit,Credit,ReferenceType,ReferenceId,Narration,CreatedBy,CreatedAt) VALUES (?,GETDATE(),?,?,?,?,?,?,GETDATE())"
                    val newId = db.queryOne("SELECT ISNULL(MAX(SettlementId),0) AS id FROM DeliverySettlements", emptyList()) { it.getInt("id") } ?: 0
                    db.execute(sql, listOf(dId, s.amountReceived, 0.0,                "DeliverySettlement", newId, reason, userId))
                    db.execute(sql, listOf(cId, 0.0,              s.amountReceived,   "DeliverySettlement", newId, reason, userId))
                }
            }
        }
    }

    suspend fun deleteSettlement(settlementId: Int) {
        val userId = session.currentUser.value?.userId ?: 0
        db.execute("DELETE FROM DeliverySettlements WHERE SettlementId=?", listOf(settlementId))
        runCatching { audit.writeAudit(userId, "DELETE", "DeliverySettlements", settlementId) }
    }

    suspend fun getUnassignedDeliveryOrders(): List<Order> = try {
        db.query(
            """SELECT OrderId, OrderNo, TokenNo, OrderType, GrandTotal, CreatedAt,
                      ISNULL(DeliveryName,'') AS DeliveryName, ISNULL(DeliveryPhone,'') AS DeliveryPhone
               FROM Orders
               WHERE OrderType = 'Delivery'
                 AND OrderStatus NOT IN ('Cancelled','Void','Refunded')
                 AND (DeliveryCompanyId IS NULL OR DeliveryCompanyId = 1)
               ORDER BY CreatedAt DESC""",
            emptyList()
        ) { rs ->
            Order(
                orderId   = rs.getInt("OrderId"),
                orderNo   = rs.getString("OrderNo") ?: "",
                tokenNo   = rs.getString("TokenNo") ?: "",
                orderType = "Delivery",
                grandTotal = rs.getDouble("GrandTotal"),
                createdAt  = rs.getTimestamp("CreatedAt") ?: Date(),
                deliveryName  = rs.getString("DeliveryName"),
                deliveryPhone = rs.getString("DeliveryPhone")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun assignDeliveryCompany(orderId: Int, company: DeliveryCompany) {
        val grandTotal = try {
            db.queryOne("SELECT ISNULL(GrandTotal,0) AS g FROM Orders WHERE OrderId=?",
                listOf(orderId)) { it.getDouble("g") } ?: 0.0
        } catch (_: Exception) { 0.0 }
        val commission = grandTotal * company.commissionPercent / 100.0
        db.execute(
            "UPDATE Orders SET DeliveryCompanyId=?, DeliveryCompanyName=?, CommissionAmount=? WHERE OrderId=?",
            listOf(company.companyId, company.companyName, commission, orderId)
        )
    }
}
