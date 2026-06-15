package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.PurchaseInvoice
import com.fastpos.android.data.models.PurchaseInvoiceItem
import com.fastpos.android.data.models.PurchaseProduct
import com.fastpos.android.data.models.PurchaseReturn
import com.fastpos.android.data.models.PurchaseReturnItem
import com.fastpos.android.data.repositories.AuditLogRepository
import com.fastpos.android.utils.SessionManager
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val session: SessionManager,
    private val audit:   AuditLogRepository
) {

    suspend fun initSchema() {
        // Tables already exist in WPF SQL Server DB; standalone handled by LocalSchemaHelper
    }

    suspend fun initReturnSchema() {
        // Tables already exist in WPF SQL Server DB; standalone handled by LocalSchemaHelper
    }

    // ── Products for purchase item picker ────────────────────────────────────

    suspend fun getProductsForPurchase(): List<PurchaseProduct> = try {
        db.query(
            """SELECT ProductId, ProductName, ISNULL(Unit,'') AS Unit,
                      ISNULL(PurchasePrice,0) AS PurchasePrice
               FROM Products
               WHERE IsActive = 1
                 AND (ISNULL(ProductType,'') = 'RawMaterial' OR ISNULL(IsStockManaged,0) = 1)
               ORDER BY ProductName"""
        ) { rs ->
            PurchaseProduct(
                productId     = rs.getInt("ProductId"),
                productName   = rs.getString("ProductName") ?: "",
                unit          = try { rs.getString("Unit") ?: "" } catch (_: Exception) { "" },
                purchasePrice = try { rs.getDouble("PurchasePrice") } catch (_: Exception) { 0.0 }
            )
        }
    } catch (_: Exception) { emptyList() }

    // ── Invoices ──────────────────────────────────────────────────────────────

    suspend fun getInvoices(supplierId: Int? = null): List<PurchaseInvoice> {
        // SQL Server (WPF) uses GrandTotal; local SQLite uses TotalAmount — try both
        fun buildSql(totalCol: String) = buildString {
            append("""
                SELECT pi.InvoiceId, pi.InvoiceNo,
                       pi.SupplierId, ISNULL(s.SupplierName,'') AS SupplierName,
                       pi.InvoiceDate,
                       ISNULL(pi.$totalCol, 0)   AS TotalAmount,
                       ISNULL(pi.PaidAmount, 0)  AS PaidAmount,
                       ISNULL(pi.BalanceAmount, ISNULL(pi.$totalCol,0) - ISNULL(pi.PaidAmount,0)) AS BalanceAmount,
                       ISNULL(pi.PaymentStatus, CASE
                         WHEN ISNULL(pi.PaidAmount,0) >= ISNULL(pi.$totalCol,0) THEN 'Paid'
                         WHEN ISNULL(pi.PaidAmount,0) > 0 THEN 'Partial'
                         ELSE 'Unpaid' END) AS PaymentStatus,
                       ISNULL(pi.Notes,'') AS Notes, pi.CreatedAt
                FROM PurchaseInvoices pi
                LEFT JOIN Suppliers s ON s.SupplierId = pi.SupplierId
                WHERE ISNULL(pi.IsActive, 1) = 1
            """)
            if (supplierId != null) append(" AND pi.SupplierId = ?")
            append(" ORDER BY pi.InvoiceDate DESC, pi.InvoiceId DESC")
        }
        val params = if (supplierId != null) listOf(supplierId) else emptyList<Any>()
        fun mapRow(rs: java.sql.ResultSet) = PurchaseInvoice(
            invoiceId     = rs.getInt("InvoiceId"),
            invoiceNo     = rs.getString("InvoiceNo") ?: "",
            supplierId    = rs.getInt("SupplierId").takeIf { !rs.wasNull() },
            supplierName  = rs.getString("SupplierName") ?: "",
            invoiceDate   = rs.getTimestamp("InvoiceDate") ?: Date(),
            totalAmount   = rs.getDouble("TotalAmount"),
            paidAmount    = rs.getDouble("PaidAmount"),
            balanceAmount = rs.getDouble("BalanceAmount"),
            paymentStatus = rs.getString("PaymentStatus") ?: "Unpaid",
            notes         = rs.getString("Notes") ?: "",
            createdAt     = rs.getTimestamp("CreatedAt") ?: Date()
        )
        return try {
            db.query(buildSql("GrandTotal"), params, ::mapRow)
        } catch (_: Exception) {
            db.query(buildSql("TotalAmount"), params, ::mapRow)
        }
    }

    suspend fun getInvoiceItems(invoiceId: Int): List<PurchaseInvoiceItem> {
        fun mapRow(rs: java.sql.ResultSet) = PurchaseInvoiceItem(
            itemId         = rs.getInt("ItemId"),
            invoiceId      = rs.getInt("InvoiceId"),
            productId      = rs.getInt("ProductId").takeIf { !rs.wasNull() },
            itemName       = rs.getString("ItemName") ?: "",
            unit           = rs.getString("Unit") ?: "kg",
            quantity       = rs.getDouble("Quantity"),
            purchaseRate   = rs.getDouble("PurchaseRate"),
            discountAmount = try { rs.getDouble("DiscountAmount") } catch (_: Exception) { 0.0 }
        )
        return try {
            // SQL Server (WPF): InvoiceItemId, no ItemName column — JOIN Products for name
            db.query("""
                SELECT ii.InvoiceItemId AS ItemId, ii.InvoiceId, ii.ProductId,
                       ISNULL(p.ProductName, '') AS ItemName,
                       ISNULL(ii.Unit, 'kg') AS Unit, ii.Quantity, ii.PurchaseRate,
                       ISNULL(ii.DiscountAmount, 0) AS DiscountAmount
                FROM PurchaseInvoiceItems ii
                LEFT JOIN Products p ON p.ProductId = ii.ProductId
                WHERE ii.InvoiceId = ?
            """, listOf(invoiceId), ::mapRow)
        } catch (_: Exception) {
            // Local SQLite fallback: ItemId and ItemName stored directly
            db.query("""
                SELECT ItemId, InvoiceId, ProductId, ItemName, Unit, Quantity, PurchaseRate,
                       ISNULL(DiscountAmount, 0) AS DiscountAmount
                FROM PurchaseInvoiceItems WHERE InvoiceId = ?
            """, listOf(invoiceId), ::mapRow)
        }
    }

    suspend fun saveInvoice(
        supplierId:  Int?,
        items:       List<PurchaseInvoiceItem>,
        paidAmount:  Double,
        notes:       String,
        manualInvoiceNo:   String?    = null,
        manualInvoiceDate: Date?      = null
    ): Int {
        val total = items.sumOf { it.total }
        val balance = total - paidAmount
        val status = when {
            paidAmount >= total -> "Paid"
            paidAmount > 0     -> "Partial"
            else               -> "Unpaid"
        }
        // Use manual invoice no if provided, otherwise auto-generate matching WPF pattern
        val effectiveDate = manualInvoiceDate ?: Date()
        val invoiceNo = if (!manualInvoiceNo.isNullOrBlank()) {
            manualInvoiceNo
        } else {
            val dateStr = SimpleDateFormat("yyyyMMdd").format(effectiveDate)
            val seq = try {
                // Match WPF: count by InvoiceDate = today (not by InvoiceNo pattern)
                db.queryOne(
                    "SELECT ISNULL(COUNT(1),0)+1 AS NextSeq FROM PurchaseInvoices WHERE CAST(InvoiceDate AS DATE) = CAST(GETDATE() AS DATE)",
                    emptyList()
                ) { it.getInt("NextSeq") } ?: 1
            } catch (_: Exception) {
                // Fallback for standalone (SQLite uses date() function)
                try {
                    db.queryOne(
                        "SELECT COUNT(*)+1 AS NextSeq FROM PurchaseInvoices WHERE InvoiceNo LIKE ?",
                        listOf("PO-$dateStr%")
                    ) { it.getInt("NextSeq") } ?: 1
                } catch (_: Exception) { 1 }
            }
            "PO-$dateStr-${seq.toString().padStart(3, '0')}"
        }
        val invoiceDate = manualInvoiceDate?.let { java.sql.Date(it.time) }

        return db.transaction { conn ->
            val invoiceId = db.insertAndGetIdSync(
                conn,
                """INSERT INTO PurchaseInvoices
                   (InvoiceNo, SupplierId, BranchId, InvoiceDate, GrandTotal, PaidAmount, BalanceAmount,
                    PaymentStatus, Notes, IsActive, CreatedAt, CreatedBy)
                   VALUES (?,?,?,${if (invoiceDate != null) "?" else "GETDATE()"},?,?,?,?,?,1,GETDATE(),?)""",
                buildList {
                    add(invoiceNo); add(supplierId); add(session.currentBranchId.value)
                    if (invoiceDate != null) add(invoiceDate)
                    add(total); add(paidAmount); add(balance); add(status)
                    add(notes.ifBlank { null }); add(session.currentUser.value?.userId ?: 0)
                }
            )
            if (invoiceId == 0) throw Exception("Insert invoice failed")

            for (item in items) {
                db.executeSync(
                    conn,
                    """INSERT INTO PurchaseInvoiceItems
                       (InvoiceId, ProductId, Quantity, Unit, PurchaseRate, DiscountAmount, LineTotal)
                       VALUES (?,?,?,?,?,?,?)""",
                    listOf(invoiceId, item.productId, item.quantity,
                        item.unit, item.purchaseRate, item.discountAmount, item.total)
                )
                if (item.productId != null) {
                    try {
                        db.executeSync(
                            conn,
                            "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) + ? WHERE ProductId = ?",
                            listOf(item.quantity, item.productId)
                        )
                    } catch (_: Exception) {}
                    try {
                        db.executeSync(
                            conn,
                            "UPDATE Products SET PurchasePrice = ? WHERE ProductId = ?",
                            listOf(item.purchaseRate, item.productId)
                        )
                    } catch (_: Exception) {}
                    try {
                        db.executeSync(
                            conn,
                            """INSERT INTO InventoryLedger
                               (ProductId, TransactionDate, ReferenceType, ReferenceId,
                                InQty, OutQty, BalanceQty, Rate, Amount, Remarks, CreatedAt)
                               SELECT ?, GETDATE(), 'Purchase', ?,
                                      ?, 0, ISNULL(CurrentStock,0), ?, ?, ?, GETDATE()
                               FROM Products WHERE ProductId = ?""",
                            listOf(item.productId, invoiceId, item.quantity, item.purchaseRate,
                                item.total, "Invoice $invoiceNo", item.productId)
                        )
                    } catch (_: Exception) {}
                }
            }
            val shiftId  = session.currentShift.value?.shiftId
            val userId   = session.currentUser.value?.userId ?: 0
            if (paidAmount > 0 && shiftId != null) {
                try {
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'Out',?,'Purchase Payment',?,?,GETDATE())",
                        listOf(shiftId, session.currentBranchId.value, paidAmount, "Invoice $invoiceNo", userId)
                    )
                } catch (_: Exception) {}
            }
            invoiceId
        }.also { invoiceId ->
            runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "INSERT", "PurchaseInvoices", invoiceId) }
            // Accounting: Debit Purchase / Credit Cash for initial cash payment
            if (paidAmount > 0) runCatching {
                val userId = session.currentUser.value?.userId ?: 0
                val dId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='5300' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
                val cId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='1000' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
                if (dId > 0 && cId > 0) {
                    val sql = "INSERT INTO AccountLedger (AccountId,EntryDate,Debit,Credit,ReferenceType,ReferenceId,Narration,CreatedBy,CreatedAt) VALUES (?,GETDATE(),?,?,?,?,?,?,GETDATE())"
                    db.execute(sql, listOf(dId, paidAmount, 0.0,        "Purchase", invoiceId, "Purchase invoice $invoiceNo", userId))
                    db.execute(sql, listOf(cId, 0.0,        paidAmount, "Purchase", invoiceId, "Purchase invoice $invoiceNo", userId))
                }
            }
        }
    }

    suspend fun recordInvoicePayment(invoiceId: Int, amount: Double, paymentMethod: String = "Cash") {
        db.execute(
            "UPDATE PurchaseInvoices SET PaidAmount = PaidAmount + ? WHERE InvoiceId = ?",
            listOf(amount, invoiceId)
        )
        // recalculate balance and status
        try {
            db.execute(
                """UPDATE PurchaseInvoices
                   SET BalanceAmount  = GrandTotal - PaidAmount,
                       PaymentStatus  = CASE
                         WHEN PaidAmount >= GrandTotal THEN 'Paid'
                         WHEN PaidAmount > 0 THEN 'Partial'
                         ELSE 'Unpaid'
                       END
                   WHERE InvoiceId = ?""",
                listOf(invoiceId)
            )
        } catch (_: Exception) {}
        if (paymentMethod.equals("Cash", ignoreCase = true)) {
            val shiftId = session.currentShift.value?.shiftId
            val userId  = session.currentUser.value?.userId ?: 0
            if (shiftId != null) {
                runCatching {
                    db.execute(
                        "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'Out',?,'Purchase Payment','Invoice payment #$invoiceId',?,GETDATE())",
                        listOf(shiftId, session.currentBranchId.value, amount, userId)
                    )
                }
            }
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "PAYMENT", "PurchaseInvoices", invoiceId) }
        // Accounting: Debit Purchase / Credit Cash for additional cash payments
        if (paymentMethod.equals("Cash", ignoreCase = true)) runCatching {
            val userId = session.currentUser.value?.userId ?: 0
            val dId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='5300' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
            val cId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='1000' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
            if (dId > 0 && cId > 0) {
                val sql = "INSERT INTO AccountLedger (AccountId,EntryDate,Debit,Credit,ReferenceType,ReferenceId,Narration,CreatedBy,CreatedAt) VALUES (?,GETDATE(),?,?,?,?,?,?,GETDATE())"
                db.execute(sql, listOf(dId, amount, 0.0,    "Purchase", invoiceId, "Purchase payment", userId))
                db.execute(sql, listOf(cId, 0.0,    amount, "Purchase", invoiceId, "Purchase payment", userId))
            }
        }
    }

    suspend fun deleteInvoice(invoiceId: Int, userId: Int) {
        val items = getInvoiceItems(invoiceId)
        // Read cash already paid so we can reverse it in the drawer
        val paidCash = try {
            db.queryOne("SELECT ISNULL(PaidAmount,0) AS P FROM PurchaseInvoices WHERE InvoiceId=?",
                listOf(invoiceId)) { it.getDouble("P") } ?: 0.0
        } catch (_: Exception) { 0.0 }
        db.transaction { conn ->
            for (item in items) {
                if (item.productId != null) {
                    try {
                        db.executeSync(
                            conn,
                            "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) - ? WHERE ProductId = ?",
                            listOf(item.quantity, item.productId)
                        )
                    } catch (_: Exception) {}
                    try {
                        db.executeSync(
                            conn,
                            """INSERT INTO InventoryLedger
                               (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                               SELECT ?,GETDATE(),'PurchaseInvoiceDelete',?,
                                      0,?,ISNULL(CurrentStock,0),?,?,?
                               FROM Products WHERE ProductId = ?""",
                            listOf(item.productId, invoiceId, item.quantity, item.purchaseRate,
                                item.quantity * item.purchaseRate, "Invoice #$invoiceId deleted", item.productId)
                        )
                    } catch (_: Exception) {}
                }
            }
            db.executeSync(conn, "UPDATE PurchaseInvoices SET IsActive = 0 WHERE InvoiceId = ?", listOf(invoiceId))
            // Reverse cash that was paid for this invoice
            if (paidCash > 0) {
                val shiftId = session.currentShift.value?.shiftId
                if (shiftId != null) {
                    runCatching {
                        db.executeSync(conn,
                            "INSERT INTO CashTransactions (ShiftId,BranchId,TransactionType,Amount,Reason,Notes,CreatedBy,CreatedAt) VALUES (?,?,'In',?,'Purchase Invoice Deleted','Cash reversal for invoice #$invoiceId',?,GETDATE())",
                            listOf(shiftId, session.currentBranchId.value, paidCash, userId))
                    }
                }
            }
        }
        runCatching { audit.writeAudit(userId, "DELETE", "PurchaseInvoices", invoiceId) }
    }

    // ── Purchase Returns ──────────────────────────────────────────────────────

    suspend fun getReturns(supplierId: Int? = null): List<PurchaseReturn> = try {
        val sql = buildString {
            append("""
                SELECT pr.ReturnId, pr.InvoiceId, pr.SupplierId,
                       ISNULL(s.SupplierName,'') AS SupplierName,
                       pr.ReturnDate, pr.TotalAmount, ISNULL(pr.Notes,'') AS Notes, pr.CreatedAt,
                       (SELECT COUNT(1) FROM PurchaseReturnItems pri WHERE pri.ReturnId = pr.ReturnId) AS ItemCount
                FROM PurchaseReturns pr
                LEFT JOIN Suppliers s ON s.SupplierId = pr.SupplierId
                WHERE pr.IsActive = 1
            """)
            if (supplierId != null) append(" AND pr.SupplierId = ?")
            append(" ORDER BY pr.ReturnDate DESC, pr.ReturnId DESC")
        }
        val params = if (supplierId != null) listOf(supplierId) else emptyList()
        db.query(sql, params) { rs ->
            PurchaseReturn(
                returnId     = rs.getInt("ReturnId"),
                invoiceId    = rs.getInt("InvoiceId").takeIf { !rs.wasNull() },
                supplierId   = rs.getInt("SupplierId").takeIf { !rs.wasNull() },
                supplierName = rs.getString("SupplierName") ?: "",
                returnDate   = rs.getTimestamp("ReturnDate") ?: Date(),
                totalAmount  = rs.getDouble("TotalAmount"),
                notes        = rs.getString("Notes") ?: "",
                refundMethod = try { rs.getString("RefundMethod") ?: "Credit" } catch (_: Exception) { "Credit" },
                shiftId      = try { rs.getInt("ShiftId").takeIf { !rs.wasNull() } } catch (_: Exception) { null },
                itemCount    = rs.getInt("ItemCount"),
                createdAt    = rs.getTimestamp("CreatedAt") ?: Date()
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getReturnItems(returnId: Int): List<PurchaseReturnItem> = try {
        db.query(
            """SELECT ReturnItemId, ReturnId, ProductId, ItemName,
                      Quantity, Unit, ReturnRate, LineTotal
               FROM PurchaseReturnItems WHERE ReturnId = ?""",
            listOf(returnId)
        ) { rs ->
            PurchaseReturnItem(
                returnItemId = rs.getInt("ReturnItemId"),
                returnId     = rs.getInt("ReturnId"),
                productId    = rs.getInt("ProductId"),
                itemName     = rs.getString("ItemName") ?: "",
                quantity     = rs.getDouble("Quantity"),
                unit         = rs.getString("Unit") ?: "",
                returnRate   = rs.getDouble("ReturnRate")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun saveReturn(
        supplierId: Int?,
        invoiceId: Int?,
        items: List<PurchaseReturnItem>,
        notes: String,
        userId: Int,
        refundMethod: String = "Credit",
        shiftId: Int? = null
    ): Int {
        val total = items.sumOf { it.lineTotal }
        return db.transaction { conn ->
            val returnId = try {
                db.insertAndGetIdSync(conn,
                    "INSERT INTO PurchaseReturns (InvoiceId, SupplierId, ReturnDate, TotalAmount, Notes, CreatedBy, RefundMethod, ShiftId) VALUES (?,?,GETDATE(),?,?,?,?,?)",
                    listOf(invoiceId, supplierId, total, notes.ifBlank { null }, userId, refundMethod, shiftId)
                )
            } catch (_: Exception) {
                // Fallback for existing installs without RefundMethod/ShiftId columns
                db.insertAndGetIdSync(conn,
                    "INSERT INTO PurchaseReturns (InvoiceId, SupplierId, ReturnDate, TotalAmount, Notes, CreatedBy) VALUES (?,?,GETDATE(),?,?,?)",
                    listOf(invoiceId, supplierId, total, notes.ifBlank { null }, userId)
                )
            }
            if (returnId == 0) throw Exception("Insert return failed")
            for (item in items) {
                db.executeSync(conn,
                    "INSERT INTO PurchaseReturnItems (ReturnId, ProductId, ItemName, Quantity, Unit, ReturnRate, LineTotal) VALUES (?,?,?,?,?,?,?)",
                    listOf(returnId, item.productId, item.itemName, item.quantity, item.unit, item.returnRate, item.lineTotal)
                )
                try {
                    db.executeSync(conn,
                        "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) - ? WHERE ProductId = ?",
                        listOf(item.quantity, item.productId)
                    )
                } catch (_: Exception) {}
                try {
                    db.executeSync(conn,
                        """INSERT INTO InventoryLedger
                           (ProductId, TransactionDate, ReferenceType, ReferenceId,
                            InQty, OutQty, BalanceQty, Rate, Amount, Remarks, CreatedAt)
                           SELECT ?, GETDATE(), 'PurchaseReturn', ?,
                                  0, ?, ISNULL(CurrentStock,0), ?, ?, ?, GETDATE()
                           FROM Products WHERE ProductId = ?""",
                        listOf(item.productId, returnId, item.quantity, item.returnRate,
                            item.lineTotal, "Return #$returnId", item.productId)
                    )
                } catch (_: Exception) {}
            }
            if (refundMethod.equals("Cash", ignoreCase = true) && shiftId != null) {
                try {
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'In',?,'Purchase Return Refund',?,?,GETDATE())",
                        listOf(shiftId, session.currentBranchId.value, total, if (notes.isBlank()) "Purchase return cash refund" else notes, session.userId)
                    )
                } catch (_: Exception) {}
            }
            returnId
        }.also { returnId ->
            runCatching { audit.writeAudit(userId, "INSERT", "PurchaseReturns", returnId) }
        }
    }

    suspend fun deleteReturn(returnId: Int, userId: Int) {
        val items = getReturnItems(returnId)
        db.transaction { conn ->
            val ret = try {
                db.querySync(conn,
                    "SELECT RefundMethod, ShiftId, TotalAmount FROM PurchaseReturns WHERE ReturnId = ?",
                    listOf(returnId)
                ) { rs ->
                    Triple(
                        try { rs.getString("RefundMethod") ?: "Credit" } catch (_: Exception) { "Credit" },
                        try { rs.getInt("ShiftId").takeIf { !rs.wasNull() } } catch (_: Exception) { null },
                        rs.getDouble("TotalAmount")
                    )
                }.firstOrNull()
            } catch (_: Exception) { null }

            for (item in items) {
                try {
                    db.executeSync(conn,
                        "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) + ? WHERE ProductId = ?",
                        listOf(item.quantity, item.productId)
                    )
                } catch (_: Exception) {}
                try {
                    db.executeSync(conn,
                        """INSERT INTO InventoryLedger
                           (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                           SELECT ?,GETDATE(),'PurchaseReturnDelete',?,
                                  ?,0,ISNULL(CurrentStock,0),?,?,?
                           FROM Products WHERE ProductId = ?""",
                        listOf(item.productId, returnId, item.quantity, item.returnRate,
                            item.quantity * item.returnRate, "Return #$returnId deleted", item.productId)
                    )
                } catch (_: Exception) {}
            }
            db.executeSync(conn, "UPDATE PurchaseReturns SET IsActive = 0 WHERE ReturnId = ?", listOf(returnId))

            val refundMethod = ret?.first ?: ""
            val shiftId      = ret?.second
            val totalAmount  = ret?.third ?: 0.0
            if (refundMethod.equals("Cash", ignoreCase = true) && shiftId != null) {
                try {
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'Out',?,'Purchase Return Deleted','Reversal of deleted cash purchase return',?,GETDATE())",
                        listOf(shiftId, session.currentBranchId.value, totalAmount, session.userId)
                    )
                } catch (_: Exception) {}
            }
        }
        runCatching { audit.writeAudit(userId, "DELETE", "PurchaseReturns", returnId) }
    }

    // ── Update Invoice (edit existing, fully reverse then re-apply effects) ──

    suspend fun updateInvoice(
        invoiceId:         Int,
        supplierId:        Int?,
        items:             List<PurchaseInvoiceItem>,
        paidAmount:        Double,
        notes:             String,
        manualInvoiceNo:   String?  = null,
        manualInvoiceDate: Date?    = null
    ) {
        val oldPaid = try {
            db.queryOne("SELECT ISNULL(PaidAmount,0) AS P FROM PurchaseInvoices WHERE InvoiceId=?",
                listOf(invoiceId)) { it.getDouble("P") } ?: 0.0
        } catch (_: Exception) { 0.0 }
        val oldItems = getInvoiceItems(invoiceId)
        val total    = items.sumOf { it.total }
        val balance  = total - paidAmount
        val status   = when {
            paidAmount >= total -> "Paid"
            paidAmount > 0     -> "Partial"
            else               -> "Unpaid"
        }

        db.transaction { conn ->
            // Reverse old stock and ledger
            for (old in oldItems) {
                if (old.productId != null) {
                    try {
                        db.executeSync(conn,
                            "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) - ? WHERE ProductId = ?",
                            listOf(old.quantity, old.productId))
                    } catch (_: Exception) {}
                    try {
                        db.executeSync(conn,
                            """INSERT INTO InventoryLedger
                               (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                               SELECT ?,GETDATE(),'PurchaseInvoiceDelete',?,
                                      0,?,ISNULL(CurrentStock,0),?,?,?
                               FROM Products WHERE ProductId = ?""",
                            listOf(old.productId, invoiceId, old.quantity, old.purchaseRate,
                                old.quantity * old.purchaseRate, "Invoice #$invoiceId edit reversal", old.productId))
                    } catch (_: Exception) {}
                }
            }
            // Delete old items
            db.executeSync(conn, "DELETE FROM PurchaseInvoiceItems WHERE InvoiceId = ?", listOf(invoiceId))

            // Insert new items + apply new stock and ledger
            for (item in items) {
                db.executeSync(conn,
                    "INSERT INTO PurchaseInvoiceItems (InvoiceId, ProductId, Quantity, Unit, PurchaseRate, DiscountAmount, LineTotal) VALUES (?,?,?,?,?,?,?)",
                    listOf(invoiceId, item.productId, item.quantity,
                        item.unit, item.purchaseRate, item.discountAmount, item.total))
                if (item.productId != null) {
                    try {
                        db.executeSync(conn,
                            "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) + ? WHERE ProductId = ?",
                            listOf(item.quantity, item.productId))
                    } catch (_: Exception) {}
                    try {
                        db.executeSync(conn,
                            "UPDATE Products SET PurchasePrice = ? WHERE ProductId = ?",
                            listOf(item.purchaseRate, item.productId))
                    } catch (_: Exception) {}
                    try {
                        db.executeSync(conn,
                            """INSERT INTO InventoryLedger
                               (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                               SELECT ?,GETDATE(),'Purchase',?,
                                      ?,0,ISNULL(CurrentStock,0),?,?,?
                               FROM Products WHERE ProductId = ?""",
                            listOf(item.productId, invoiceId, item.quantity, item.purchaseRate,
                                item.total, "Invoice #$invoiceId (updated)", item.productId))
                    } catch (_: Exception) {}
                }
            }
            // Update header (include InvoiceNo/Date if provided)
            if (!manualInvoiceNo.isNullOrBlank() || manualInvoiceDate != null) {
                val invNo   = manualInvoiceNo?.ifBlank { null }
                val invDate = manualInvoiceDate?.let { java.sql.Date(it.time) }
                db.executeSync(conn,
                    "UPDATE PurchaseInvoices SET SupplierId=?, GrandTotal=?, PaidAmount=?, BalanceAmount=?, PaymentStatus=?, Notes=?${if (invNo != null) ", InvoiceNo=?" else ""}${if (invDate != null) ", InvoiceDate=?" else ""} WHERE InvoiceId=?",
                    buildList {
                        add(supplierId); add(total); add(paidAmount); add(balance); add(status); add(notes.ifBlank { null })
                        if (invNo != null) add(invNo)
                        if (invDate != null) add(invDate)
                        add(invoiceId)
                    })
            } else {
                db.executeSync(conn,
                    "UPDATE PurchaseInvoices SET SupplierId=?, GrandTotal=?, PaidAmount=?, BalanceAmount=?, PaymentStatus=?, Notes=? WHERE InvoiceId=?",
                    listOf(supplierId, total, paidAmount, balance, status, notes.ifBlank { null }, invoiceId))
            }
            // Write CashTransaction for any additional cash paid on edit
            val extraPaid = paidAmount - oldPaid
            if (extraPaid > 0.01) {
                val shiftId = session.currentShift.value?.shiftId
                if (shiftId != null) {
                    runCatching {
                        db.executeSync(conn,
                            "INSERT INTO CashTransactions (ShiftId,BranchId,TransactionType,Amount,Reason,Notes,CreatedBy,CreatedAt) VALUES (?,?,'Out',?,'Purchase Payment','Invoice #$invoiceId additional payment',?,GETDATE())",
                            listOf(shiftId, session.currentBranchId.value, extraPaid, session.currentUser.value?.userId ?: 0))
                    }
                }
            } else if (extraPaid < -0.01) {
                // Payment reduced — reverse the over-payment in the drawer
                val shiftId = session.currentShift.value?.shiftId
                if (shiftId != null) {
                    runCatching {
                        db.executeSync(conn,
                            "INSERT INTO CashTransactions (ShiftId,BranchId,TransactionType,Amount,Reason,Notes,CreatedBy,CreatedAt) VALUES (?,?,'In',?,'Purchase Payment Reduced','Invoice #$invoiceId payment adjustment',?,GETDATE())",
                            listOf(shiftId, session.currentBranchId.value, -extraPaid, session.currentUser.value?.userId ?: 0))
                    }
                }
            }
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "UPDATE", "PurchaseInvoices", invoiceId) }
    }

    // ── Update Return (edit existing, fully reverse then re-apply effects) ───

    suspend fun updateReturn(
        returnId: Int,
        supplierId: Int?,
        invoiceId: Int?,
        items: List<PurchaseReturnItem>,
        notes: String
    ) {
        // Read old return info for CashTransaction reversal
        val oldReturn = try {
            db.queryOne(
                "SELECT ISNULL(RefundMethod,'Credit') AS RM, ShiftId, TotalAmount FROM PurchaseReturns WHERE ReturnId=?",
                listOf(returnId)
            ) { rs -> Triple(rs.getString("RM") ?: "Credit", rs.getInt("ShiftId").takeIf { !rs.wasNull() }, rs.getDouble("TotalAmount")) }
        } catch (_: Exception) { null }
        val oldItems = getReturnItems(returnId)
        val total    = items.sumOf { it.lineTotal }

        db.transaction { conn ->
            // Reverse old stock deduction and ledger
            for (old in oldItems) {
                try {
                    db.executeSync(conn,
                        "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) + ? WHERE ProductId = ?",
                        listOf(old.quantity, old.productId))
                } catch (_: Exception) {}
                try {
                    db.executeSync(conn,
                        """INSERT INTO InventoryLedger
                           (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                           SELECT ?,GETDATE(),'PurchaseReturnDelete',?,
                                  ?,0,ISNULL(CurrentStock,0),?,?,?
                           FROM Products WHERE ProductId = ?""",
                        listOf(old.productId, returnId, old.quantity, old.returnRate,
                            old.quantity * old.returnRate, "Return #$returnId edit reversal", old.productId))
                } catch (_: Exception) {}
            }
            // Delete old items
            db.executeSync(conn, "DELETE FROM PurchaseReturnItems WHERE ReturnId = ?", listOf(returnId))

            // Insert new items + apply new stock and ledger
            for (item in items) {
                db.executeSync(conn,
                    "INSERT INTO PurchaseReturnItems (ReturnId, ProductId, ItemName, Quantity, Unit, ReturnRate, LineTotal) VALUES (?,?,?,?,?,?,?)",
                    listOf(returnId, item.productId, item.itemName, item.quantity,
                        item.unit, item.returnRate, item.lineTotal))
                try {
                    db.executeSync(conn,
                        "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) - ? WHERE ProductId = ?",
                        listOf(item.quantity, item.productId))
                } catch (_: Exception) {}
                try {
                    db.executeSync(conn,
                        """INSERT INTO InventoryLedger
                           (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                           SELECT ?,GETDATE(),'PurchaseReturn',?,
                                  0,?,ISNULL(CurrentStock,0),?,?,?
                           FROM Products WHERE ProductId = ?""",
                        listOf(item.productId, returnId, item.quantity, item.returnRate,
                            item.lineTotal, "Return #$returnId (updated)", item.productId))
                } catch (_: Exception) {}
            }
            // Update header
            db.executeSync(conn,
                "UPDATE PurchaseReturns SET SupplierId=?, InvoiceId=?, TotalAmount=?, Notes=? WHERE ReturnId=?",
                listOf(supplierId, invoiceId, total, notes.ifBlank { null }, returnId))

            // Reverse old cash entry and write new one if refund method is Cash
            val oldRefundMethod = oldReturn?.first ?: "Credit"
            val oldShiftId      = oldReturn?.second
            val oldTotal        = oldReturn?.third ?: 0.0
            if (oldRefundMethod.equals("Cash", ignoreCase = true) && oldShiftId != null) {
                runCatching {
                    // Reverse the old cash-in
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId,BranchId,TransactionType,Amount,Reason,Notes,CreatedBy,CreatedAt) VALUES (?,?,'Out',?,'Purchase Return Edit','Reversal of old return #$returnId cash',?,GETDATE())",
                        listOf(oldShiftId, session.currentBranchId.value, oldTotal, session.currentUser.value?.userId ?: 0))
                    // Write new cash-in for new amount
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId,BranchId,TransactionType,Amount,Reason,Notes,CreatedBy,CreatedAt) VALUES (?,?,'In',?,'Purchase Return Refund','Updated return #$returnId',?,GETDATE())",
                        listOf(oldShiftId, session.currentBranchId.value, total, session.currentUser.value?.userId ?: 0))
                }
            }
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "UPDATE", "PurchaseReturns", returnId) }
    }
}
