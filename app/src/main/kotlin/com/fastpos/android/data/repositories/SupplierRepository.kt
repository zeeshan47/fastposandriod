package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.PurchasePayment
import com.fastpos.android.data.models.Supplier
import com.fastpos.android.data.models.SupplierBalance
import com.fastpos.android.data.models.SupplierLedgerEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.fastpos.android.utils.SessionManager
import javax.inject.Inject
import javax.inject.Singleton
import com.fastpos.android.data.repositories.AuditLogRepository

@Singleton
class SupplierRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val session: SessionManager,
    private val audit:   AuditLogRepository
) {

    suspend fun initSchema() {
        db.execute("""
            IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'Suppliers')
            CREATE TABLE Suppliers (
                SupplierId    INT IDENTITY(1,1) PRIMARY KEY,
                SupplierName  NVARCHAR(200) NOT NULL,
                ContactPerson NVARCHAR(200) DEFAULT '',
                Phone         NVARCHAR(50)  DEFAULT '',
                Address       NVARCHAR(500) DEFAULT '',
                Email         NVARCHAR(200) DEFAULT '',
                IsActive      BIT DEFAULT 1,
                CreatedAt     DATETIME DEFAULT GETDATE()
            )
        """)
        db.execute("""
            IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SupplierPayments')
            CREATE TABLE SupplierPayments (
                PaymentId     INT IDENTITY(1,1) PRIMARY KEY,
                SupplierId    INT NOT NULL,
                PaymentDate   DATETIME DEFAULT GETDATE(),
                Amount        DECIMAL(18,2) DEFAULT 0,
                PaymentMethod NVARCHAR(50)  DEFAULT 'Cash',
                Reference     NVARCHAR(200) DEFAULT '',
                Notes         NVARCHAR(500) DEFAULT '',
                ShiftId       INT NULL,
                IsActive      BIT DEFAULT 1,
                CreatedAt     DATETIME DEFAULT GETDATE()
            )
        """)
    }

    suspend fun getAllSuppliers(): List<Supplier> = db.query(
        "SELECT SupplierId, SupplierName, ISNULL(ContactPerson,'') AS ContactPerson, ISNULL(Phone,'') AS Phone, ISNULL(Address,'') AS Address, ISNULL(Email,'') AS Email, IsActive FROM Suppliers WHERE IsActive = 1 ORDER BY SupplierName"
    ) { rs ->
        Supplier(
            supplierId    = rs.getInt("SupplierId"),
            supplierName  = rs.getString("SupplierName") ?: "",
            contactPerson = rs.getString("ContactPerson") ?: "",
            phone         = rs.getString("Phone") ?: "",
            address       = rs.getString("Address") ?: "",
            email         = rs.getString("Email") ?: "",
            isActive      = rs.getBoolean("IsActive")
        )
    }

    suspend fun addSupplier(name: String, contact: String, phone: String, address: String, email: String): Int {
        val newId = db.insertAndGetId(
            "INSERT INTO Suppliers (SupplierName, ContactPerson, Phone, Address, Email) VALUES (?,?,?,?,?)",
            listOf(name, contact, phone, address, email)
        )
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "INSERT", "Suppliers", newId) }
        return newId
    }

    suspend fun updateSupplier(supplierId: Int, name: String, contact: String, phone: String, address: String, email: String, isActive: Boolean) {
        db.execute(
            "UPDATE Suppliers SET SupplierName=?, ContactPerson=?, Phone=?, Address=?, Email=?, IsActive=? WHERE SupplierId=?",
            listOf(name, contact, phone, address, email, if (isActive) 1 else 0, supplierId)
        )
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "UPDATE", "Suppliers", supplierId) }
    }

    suspend fun deleteSupplier(supplierId: Int) {
        db.execute("UPDATE Suppliers SET IsActive=0 WHERE SupplierId=?", listOf(supplierId))
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "DELETE", "Suppliers", supplierId) }
    }

    suspend fun getSupplierBalances(): List<SupplierBalance> = try {
        db.query("""
            SELECT
                s.SupplierId,
                s.SupplierName,
                ISNULL(s.Phone,'') AS Phone,
                ISNULL(inv.TotalInvoiced,  0) AS TotalInvoiced,
                ISNULL(inv.TotalInvPaid,   0) AS TotalInvPaid,
                ISNULL(dp.DirectPayments,  0) AS DirectPayments,
                ISNULL(ret.TotalReturned,  0) AS TotalReturned
            FROM Suppliers s
            LEFT JOIN (
                SELECT SupplierId,
                       SUM(GrandTotal)  AS TotalInvoiced,
                       SUM(PaidAmount)  AS TotalInvPaid
                FROM PurchaseInvoices
                WHERE ISNULL(IsActive, 1) = 1
                GROUP BY SupplierId
            ) inv ON inv.SupplierId = s.SupplierId
            LEFT JOIN (
                SELECT SupplierId, SUM(Amount) AS DirectPayments
                FROM SupplierPayments
                GROUP BY SupplierId
            ) dp ON dp.SupplierId = s.SupplierId
            LEFT JOIN (
                SELECT SupplierId, SUM(TotalAmount) AS TotalReturned
                FROM PurchaseReturns
                WHERE ISNULL(IsActive, 1) = 1
                GROUP BY SupplierId
            ) ret ON ret.SupplierId = s.SupplierId
            WHERE s.IsActive = 1
            ORDER BY s.SupplierName
        """) { rs ->
            val invoiced    = rs.getDouble("TotalInvoiced")
            val invPaid     = rs.getDouble("TotalInvPaid")
            val directPaid  = rs.getDouble("DirectPayments")
            val returned    = rs.getDouble("TotalReturned")
            val outstanding = invoiced - invPaid - directPaid - returned
            SupplierBalance(
                supplierId         = rs.getInt("SupplierId"),
                supplierName       = rs.getString("SupplierName") ?: "",
                phone              = rs.getString("Phone") ?: "",
                totalInvoiced      = invoiced,
                directPayments     = directPaid + invPaid,
                outstandingBalance = outstanding.coerceAtLeast(0.0)
            )
        }
    } catch (_: Exception) {
        // Fallback: PurchaseReturns table may not exist on some installs
        db.query("""
            SELECT
                s.SupplierId, s.SupplierName, ISNULL(s.Phone,'') AS Phone,
                ISNULL(inv.TotalInvoiced, 0) AS TotalInvoiced,
                ISNULL(inv.TotalInvPaid,  0) AS TotalInvPaid,
                ISNULL(dp.DirectPayments, 0) AS DirectPayments
            FROM Suppliers s
            LEFT JOIN (
                SELECT SupplierId, SUM(GrandTotal) AS TotalInvoiced, SUM(PaidAmount) AS TotalInvPaid
                FROM PurchaseInvoices WHERE ISNULL(IsActive,1)=1 GROUP BY SupplierId
            ) inv ON inv.SupplierId = s.SupplierId
            LEFT JOIN (
                SELECT SupplierId, SUM(Amount) AS DirectPayments
                FROM SupplierPayments GROUP BY SupplierId
            ) dp ON dp.SupplierId = s.SupplierId
            WHERE s.IsActive = 1 ORDER BY s.SupplierName
        """) { rs ->
            val invoiced   = rs.getDouble("TotalInvoiced")
            val invPaid    = rs.getDouble("TotalInvPaid")
            val directPaid = rs.getDouble("DirectPayments")
            SupplierBalance(
                supplierId         = rs.getInt("SupplierId"),
                supplierName       = rs.getString("SupplierName") ?: "",
                phone              = rs.getString("Phone") ?: "",
                totalInvoiced      = invoiced,
                directPayments     = directPaid + invPaid,
                outstandingBalance = (invoiced - invPaid - directPaid).coerceAtLeast(0.0)
            )
        }
    }

    suspend fun addDirectPayment(supplierId: Int, amount: Double, method: String, reference: String, notes: String, supplierName: String = "", shiftId: Int? = null) {
        var newPaymentId = 0
        db.transaction { conn ->
            try {
                newPaymentId = db.insertAndGetIdSync(conn,
                    "INSERT INTO SupplierPayments (SupplierId, Amount, PaymentMethod, Reference, Notes, ShiftId) VALUES (?,?,?,?,?,?)",
                    listOf(supplierId, amount, method, reference.ifBlank { null }, notes.ifBlank { null }, shiftId)
                )
            } catch (_: Exception) {
                // Fallback for existing installs without ShiftId column
                newPaymentId = db.insertAndGetIdSync(conn,
                    "INSERT INTO SupplierPayments (SupplierId, Amount, PaymentMethod, Reference, Notes) VALUES (?,?,?,?,?)",
                    listOf(supplierId, amount, method, reference.ifBlank { null }, notes.ifBlank { null })
                )
            }
            if (method.equals("Cash", ignoreCase = true) && shiftId != null) {
                try {
                    val noteStr = if (notes.isNotBlank()) notes else (if (supplierName.isNotBlank()) "Supplier payment: $supplierName" else "Supplier payment")
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'Out',?,'Supplier Payment',?,?,GETDATE())",
                        listOf(shiftId, session.currentBranchId.value, amount, noteStr, session.userId)
                    )
                } catch (_: Exception) {}
            }
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "INSERT", "SupplierPayments", newPaymentId) }
        // Accounting: Debit AP-Suppliers / Credit Cash for cash payments
        if (method.equals("Cash", ignoreCase = true)) runCatching {
            val userId = session.currentUser.value?.userId ?: 0
            val dId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='2000' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
            val cId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='1000' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
            if (dId > 0 && cId > 0) {
                val sql = "INSERT INTO AccountLedger (AccountId,EntryDate,Debit,Credit,ReferenceType,ReferenceId,Narration,CreatedBy,CreatedAt) VALUES (?,GETDATE(),?,?,?,?,?,?,GETDATE())"
                db.execute(sql, listOf(dId, amount, 0.0,    "SupplierPayment", newPaymentId, "Supplier payment: $supplierName", userId))
                db.execute(sql, listOf(cId, 0.0,    amount, "SupplierPayment", newPaymentId, "Supplier payment: $supplierName", userId))
            }
        }
    }

    suspend fun deletePayment(paymentId: Int) {
        db.transaction { conn ->
            val pay = try {
                db.querySync(conn,
                    "SELECT PaymentMethod, ShiftId, Amount FROM SupplierPayments WHERE PaymentId = ?",
                    listOf(paymentId)
                ) { rs ->
                    Triple(
                        rs.getString("PaymentMethod") ?: "Cash",
                        try { rs.getInt("ShiftId").takeIf { !rs.wasNull() } } catch (_: Exception) { null },
                        rs.getDouble("Amount")
                    )
                }.firstOrNull()
            } catch (_: Exception) { null }

            db.executeSync(conn, "DELETE FROM SupplierPayments WHERE PaymentId = ?", listOf(paymentId))

            val method  = pay?.first ?: ""
            val shiftId = pay?.second
            val amount  = pay?.third ?: 0.0
            if (method.equals("Cash", ignoreCase = true) && shiftId != null) {
                try {
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'In',?,'Supplier Payment Deleted','Reversal of deleted supplier payment',?,GETDATE())",
                        listOf(shiftId, session.currentBranchId.value, amount, session.userId)
                    )
                } catch (_: Exception) {}
            }
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "DELETE", "SupplierPayments", paymentId) }
    }

    suspend fun getOpeningBalance(supplierId: Int, from: Date): Double {
        val fmt    = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val fromStr = fmt.format(from)
        return try {
            db.query("""
                SELECT
                    ISNULL((SELECT SUM(pi.BalanceAmount)
                            FROM PurchaseInvoices pi
                            WHERE pi.SupplierId = ? AND ISNULL(pi.IsActive,1) = 1
                              AND CAST(pi.InvoiceDate AS DATE) < ?), 0)
                  - ISNULL((SELECT SUM(pp.Amount)
                            FROM SupplierPayments pp
                            WHERE pp.SupplierId = ?
                              AND CAST(pp.PaymentDate AS DATE) < ?), 0)
                  - ISNULL((SELECT SUM(pr.TotalAmount)
                            FROM PurchaseReturns pr
                            WHERE pr.SupplierId = ? AND ISNULL(pr.IsActive,1) = 1
                              AND CAST(pr.ReturnDate AS DATE) < ?), 0) AS OpeningBalance
            """, listOf(supplierId, fromStr, supplierId, fromStr, supplierId, fromStr)) { rs ->
                rs.getDouble("OpeningBalance")
            }.firstOrNull() ?: 0.0
        } catch (_: Exception) {
            db.query("""
                SELECT
                    ISNULL((SELECT SUM(pi.BalanceAmount)
                            FROM PurchaseInvoices pi
                            WHERE pi.SupplierId = ? AND ISNULL(pi.IsActive,1) = 1
                              AND CAST(pi.InvoiceDate AS DATE) < ?), 0)
                  - ISNULL((SELECT SUM(pp.Amount)
                            FROM SupplierPayments pp
                            WHERE pp.SupplierId = ?
                              AND CAST(pp.PaymentDate AS DATE) < ?), 0) AS OpeningBalance
            """, listOf(supplierId, fromStr, supplierId, fromStr)) { rs ->
                rs.getDouble("OpeningBalance")
            }.firstOrNull() ?: 0.0
        }
    }

    suspend fun getLedger(supplierId: Int, from: Date, to: Date): List<SupplierLedgerEntry> {
        val fmt    = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val fromStr = fmt.format(from)
        val toStr   = fmt.format(to)
        val sql = """
            SELECT EntryDate, EntryType, Reference, Debit, Credit, Notes
            FROM (
                SELECT CAST(pi.InvoiceDate AS DATE)                                   AS EntryDate,
                       'Invoice'                                                       AS EntryType,
                       pi.InvoiceNo                                                    AS Reference,
                       ISNULL(pi.BalanceAmount, pi.GrandTotal - ISNULL(pi.PaidAmount,0)) AS Debit,
                       0                                                               AS Credit,
                       ISNULL(pi.Notes,'')                                             AS Notes
                FROM PurchaseInvoices pi
                WHERE pi.SupplierId = ? AND ISNULL(pi.IsActive,1) = 1
                  AND CAST(pi.InvoiceDate AS DATE) BETWEEN ? AND ?

                UNION ALL

                SELECT CAST(pp.PaymentDate AS DATE)                              AS EntryDate,
                       'Payment'                                                  AS EntryType,
                       ISNULL(NULLIF(pp.Reference,''), pp.PaymentMethod)         AS Reference,
                       0                                                          AS Debit,
                       pp.Amount                                                  AS Credit,
                       ISNULL(pp.Notes,'')                                        AS Notes
                FROM SupplierPayments pp
                WHERE pp.SupplierId = ?
                  AND CAST(pp.PaymentDate AS DATE) BETWEEN ? AND ?
        """
        val sqlWithReturns = sql + """
                UNION ALL

                SELECT CAST(pr.ReturnDate AS DATE)                               AS EntryDate,
                       'Return'                                                   AS EntryType,
                       'RTN-' + CAST(pr.ReturnId AS VARCHAR)                     AS Reference,
                       0                                                          AS Debit,
                       pr.TotalAmount                                             AS Credit,
                       ISNULL(pr.Notes,'')                                        AS Notes
                FROM PurchaseReturns pr
                WHERE pr.SupplierId = ? AND ISNULL(pr.IsActive,1) = 1
                  AND CAST(pr.ReturnDate AS DATE) BETWEEN ? AND ?
            ) t
            ORDER BY EntryDate,
                CASE EntryType WHEN 'Invoice' THEN 1 WHEN 'Return' THEN 2 WHEN 'Payment' THEN 3 ELSE 4 END
        """
        val sqlNoReturns = sql + """
            ) t
            ORDER BY EntryDate,
                CASE EntryType WHEN 'Invoice' THEN 1 WHEN 'Payment' THEN 2 ELSE 3 END
        """

        fun mapRow(rs: java.sql.ResultSet): SupplierLedgerEntry {
            val dateVal = rs.getDate("EntryDate")
            return SupplierLedgerEntry(
                entryDate = dateVal ?: Date(),
                entryType = rs.getString("EntryType") ?: "",
                reference = rs.getString("Reference") ?: "",
                debit     = rs.getDouble("Debit"),
                credit    = rs.getDouble("Credit"),
                notes     = rs.getString("Notes") ?: ""
            )
        }

        val rawRows = try {
            db.query(sqlWithReturns,
                listOf(supplierId, fromStr, toStr, supplierId, fromStr, toStr, supplierId, fromStr, toStr),
                ::mapRow)
        } catch (_: Exception) {
            db.query(sqlNoReturns,
                listOf(supplierId, fromStr, toStr, supplierId, fromStr, toStr),
                ::mapRow)
        }

        var running = 0.0
        return rawRows.map { r -> running += r.debit - r.credit; r.copy(balance = running) }
    }

    suspend fun getPaymentsBySupplier(supplierId: Int): List<PurchasePayment> = db.query("""
        SELECT pp.PaymentId, pp.SupplierId, s.SupplierName, pp.PaymentDate,
               pp.Amount, pp.PaymentMethod, ISNULL(pp.Reference,'') AS Reference,
               ISNULL(pp.Notes,'') AS Notes
        FROM SupplierPayments pp
        JOIN Suppliers s ON s.SupplierId = pp.SupplierId
        WHERE pp.SupplierId = ?
        ORDER BY pp.PaymentDate DESC
    """, listOf(supplierId)) { rs ->
        PurchasePayment(
            paymentId     = rs.getInt("PaymentId"),
            supplierId    = rs.getInt("SupplierId"),
            supplierName  = rs.getString("SupplierName") ?: "",
            paymentDate   = rs.getTimestamp("PaymentDate") ?: java.util.Date(),
            amount        = rs.getDouble("Amount"),
            paymentMethod = rs.getString("PaymentMethod") ?: "Cash",
            reference     = rs.getString("Reference") ?: "",
            notes         = rs.getString("Notes") ?: ""
        )
    }
}
