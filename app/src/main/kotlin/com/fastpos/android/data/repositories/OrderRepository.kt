package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.*
import com.fastpos.android.utils.SessionManager
import java.sql.Connection
import java.sql.ResultSet
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val session: SessionManager,
    private val audit:   AuditLogRepository
) {

    /** Place a new order – inserts Order, OrderItems, modifiers, and a KitchenTicket atomically. */
    suspend fun placeOrder(
        cart: List<CartItem>,
        orderType: String,
        tableId: Int?,
        waiterId: Int?,
        customerId: Int?,
        shiftId: Int?,
        discountAmount: Double,
        discountPercent: Double,
        taxAmount: Double,
        taxPercent: Double,
        serviceCharges: Double,
        deliveryCharge: Double,
        grandTotal: Double,
        subTotal: Double,
        createdBy: Int,
        notes: String,
        settings: CompanySettings,
        deliveryName: String? = null,
        deliveryPhone: String? = null,
        deliveryAddress: String? = null,
        deliveryCompanyId: Int? = null,
        customerName: String? = null,
        deliveryCompanyName: String? = null,
        commissionAmount: Double = 0.0,
        voucherCode: String? = null,
        voucherDiscount: Double = 0.0
    ): Int {
        val orderNo = nextOrderNo()

        val placeOrderResult = db.transaction { conn ->
            // Generate token inside the transaction with UPDLOCK so concurrent orders
            // can't read the same MAX and produce duplicate token numbers.
            val tokenPrefix = settings.tokenPrefix
            val tokenNo = run {
                val n = try {
                    db.querySync(conn,
                        """SELECT ISNULL(MAX(TRY_CAST(REPLACE(TokenNo,?,?) AS INT)),0)+1 AS NextNum
                           FROM Orders WITH (UPDLOCK, HOLDLOCK)
                           WHERE TokenNo LIKE ? AND CAST(OrderDate AS DATE) = CAST(GETDATE() AS DATE)""",
                        listOf(tokenPrefix, "", "$tokenPrefix%")
                    ) { it.getInt("NextNum") }.firstOrNull() ?: 1
                } catch (_: Exception) { 1 }
                "$tokenPrefix${n.toString().padStart(3, '0')}"
            }

            val orderId = db.insertAndGetIdSync(
                conn,
                """INSERT INTO Orders (OrderNo, TokenNo, OrderDate, OrderType, TableId, WaiterId, CustomerId,
                   ShiftId, SubTotal, DiscountAmount, DiscountPercent, TaxAmount, TaxPercent,
                   ServiceCharges, DeliveryCharge, GrandTotal, PaidAmount, BalanceAmount,
                   OrderStatus, PaymentStatus, Notes, CreatedBy, CreatedAt,
                   CustomerName, DeliveryName, DeliveryPhone, DeliveryAddress, DeliveryCompanyId,
                   DeliveryCompanyName, CommissionAmount, VoucherCode, VoucherDiscount, BranchId)
                   VALUES (?,?,GETDATE(),?,?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,?,GETDATE(),
                           ISNULL(?,''),ISNULL(?,''),ISNULL(?,''),ISNULL(?,''),?,ISNULL(?,''),ISNULL(?,0),ISNULL(?,''),ISNULL(?,0),?)""",
                listOf(orderNo, tokenNo, orderType, tableId, waiterId, customerId,
                       shiftId, subTotal, discountAmount, discountPercent,
                       taxAmount, taxPercent, serviceCharges, deliveryCharge,
                       grandTotal, grandTotal, "New", "Unpaid", notes, createdBy,
                       customerName, deliveryName, deliveryPhone, deliveryAddress, deliveryCompanyId,
                       deliveryCompanyName, commissionAmount, voucherCode, voucherDiscount,
                       session.currentBranchId.value)
            )

            // Order items — insert all first, collect (itemId, cartItem) for per-printer KOT grouping
            val insertedItems = mutableListOf<Pair<Int, CartItem>>()
            for (item in cart) {
                val itemId = db.insertAndGetIdSync(
                    conn,
                    """INSERT INTO OrderItems (OrderId, ProductId, SizeId, ProductNameSnapshot,
                       SizeNameSnapshot, Quantity, UnitPrice, DiscountAmount, LineTotal, Notes,
                       KitchenStatus, KitchenPrinterId, ProductNameOtherLanguageSnapshot, CreatedAt)
                       VALUES (?,?,?,?,?,?,?,?,?,?,'Pending',?,ISNULL(?,''),GETDATE())""",
                    listOf(orderId, item.productId, item.sizeId, item.productName,
                           item.sizeName, item.quantity.toDouble(), item.unitPrice,
                           item.discountAmount, item.lineTotal, item.notes.ifEmpty { null },
                           item.kitchenPrinterId, item.productNameOtherLanguage)
                )
                for (mod in item.selectedModifiers) {
                    db.executeSync(
                        conn,
                        """INSERT INTO OrderItemModifiers (OrderItemId, ModifierId, ModifierNameSnapshot,
                           ExtraPrice, Quantity, Total) VALUES (?,?,?,?,?,?)""",
                        listOf(itemId, mod.modifierId, mod.modifierName, mod.extraPrice,
                               mod.quantity, mod.extraPrice * mod.quantity)
                    )
                }
                insertedItems.add(Pair(itemId, item))
            }

            // Kitchen tickets — one per printer; items with no assigned printer share a default ticket
            val printerGroups = insertedItems.groupBy { (_, item) -> item.printerName.ifBlank { "" } }
            for ((printerName, lines) in printerGroups) {
                val nextTicketNum = try {
                    db.querySync(
                        conn,
                        "SELECT ISNULL(MAX(TRY_CAST(REPLACE(TicketNo,'KT-','') AS INT)),0)+1 AS NextNum FROM KitchenTickets WHERE TicketNo LIKE 'KT-%'",
                        emptyList()
                    ) { rs -> rs.getInt("NextNum") }.firstOrNull() ?: 1
                } catch (_: Exception) { 1 }
                val ticketNo = "KT-%05d".format(nextTicketNum)
                val ticketId = db.insertAndGetIdSync(
                    conn,
                    "INSERT INTO KitchenTickets (OrderId, TicketNo, PrintedAt, PrinterName, TicketStatus) VALUES (?,?,GETDATE(),?,?)",
                    listOf(orderId, ticketNo, printerName.ifBlank { null }, "Pending")
                )
                for ((itemId, item) in lines) {
                    val displayName = buildString {
                        append(item.productName)
                        item.sizeName?.let { append(" [$it]") }
                        if (item.selectedModifiers.isNotEmpty())
                            append(" + " + item.selectedModifiers.joinToString(", ") { it.modifierName })
                    }
                    db.executeSync(
                        conn,
                        "INSERT INTO KitchenTicketItems (TicketId, OrderItemId, ItemName, Quantity, Notes, ItemStatus) VALUES (?,?,?,?,?,?)",
                        listOf(ticketId, itemId, displayName, item.quantity.toDouble(),
                               item.notes.ifEmpty { null }, "Pending")
                    )
                }
            }

            // Mark table occupied for DineIn
            if (orderType == "DineIn" && tableId != null) {
                db.executeSync(conn, "UPDATE Tables SET TableStatus = 'Occupied' WHERE TableId = ?",
                    listOf(tableId))
            }

            // Transition 'New' → 'SentToKitchen' after KOT rows are created, matching WPF flow
            db.executeSync(conn,
                "UPDATE Orders SET OrderStatus='SentToKitchen', UpdatedAt=GETDATE() WHERE OrderId=?",
                listOf(orderId))

            orderId
        }
        runCatching { audit.writeAudit(createdBy, "INSERT", "Orders", placeOrderResult) }
        return placeOrderResult
    }

    suspend fun updateOrderStatus(orderId: Int, status: String) {
        db.execute(
            "UPDATE Orders SET OrderStatus = ?, UpdatedAt = GETDATE() WHERE OrderId = ?",
            listOf(status, orderId)
        )
        if (status == "Completed") {
            try {
                db.execute(
                    "UPDATE Tables SET TableStatus = 'Available' WHERE TableId = (SELECT TableId FROM Orders WHERE OrderId = ? AND TableId IS NOT NULL)",
                    listOf(orderId)
                )
            } catch (_: Exception) {}
        }
        when (status) {
            "SentToKitchen" -> runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "SENT_TO_KITCHEN", "Orders", orderId) }
            "Ready"         -> runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "ORDER_READY", "Orders", orderId) }
        }
    }

    suspend fun voidOrder(orderId: Int, reason: String) {
        val existing = db.queryOne("SELECT ISNULL(Notes,'') AS N FROM Orders WHERE OrderId=?",
            listOf(orderId)) { it.getString("N") ?: "" } ?: ""
        val note = reason.ifBlank { "No reason" }
        val newNotes = if (existing.isBlank()) "[Void: $note]" else "$existing [Void: $note]"
        val cashPaid = try {
            db.queryOne("SELECT ISNULL(SUM(Amount),0) AS T FROM OrderPayments WHERE OrderId=? AND PaymentMethod='Cash'",
                listOf(orderId)) { it.getDouble("T") } ?: 0.0
        } catch (_: Exception) { 0.0 }
        val orderNo = try { db.queryOne("SELECT ISNULL(OrderNo,'') AS N FROM Orders WHERE OrderId=?",
            listOf(orderId)) { it.getString("N") ?: "" } ?: "" } catch (_: Exception) { "" }
        db.transaction { conn ->
            val currentStatus = try {
                db.querySync(conn, "SELECT OrderStatus FROM Orders WHERE OrderId=?", listOf(orderId)) { it.getString("OrderStatus") ?: "" }.firstOrNull() ?: ""
            } catch (_: Exception) { "" }
            if (currentStatus == "Completed") {
                restoreOrderStockSync(conn, orderId)
            }
            db.executeSync(conn,
                "UPDATE Orders SET OrderStatus='Cancelled', Notes=?, CancellationReason='Voided', UpdatedAt=GETDATE() WHERE OrderId=?",
                listOf(newNotes, orderId))
            freeOrderTableSync(conn, orderId)
            cancelPendingKitchenTicketsSync(conn, orderId)
            if (cashPaid > 0) {
                runCatching {
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId,BranchId,TransactionType,Amount,Reason,Notes,CreatedBy,CreatedAt) VALUES (?,?,'Out',?,?,?,?,GETDATE())",
                        listOf(session.currentShift.value?.shiftId, session.currentBranchId.value,
                            cashPaid, "Order Cancelled${if (orderNo.isNotBlank()) " - $orderNo" else ""}",
                            note, session.currentUser.value?.userId ?: 0))
                }
            }
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "VOID_ORDER", "Orders", orderId) }
    }

    suspend fun refundOrder(orderId: Int, reason: String) {
        val existing = db.queryOne("SELECT ISNULL(Notes,'') AS N FROM Orders WHERE OrderId=?",
            listOf(orderId)) { it.getString("N") ?: "" } ?: ""
        val note = reason.ifBlank { "No reason" }
        val newNotes = if (existing.isBlank()) "[Refund: $note]" else "$existing [Refund: $note]"

        // Sum cash payments already made for this order so we can reverse them
        val cashPaid = try {
            db.queryOne(
                "SELECT ISNULL(SUM(Amount),0) AS T FROM OrderPayments WHERE OrderId=? AND PaymentMethod='Cash'",
                listOf(orderId)
            ) { it.getDouble("T") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        db.transaction { conn ->
            val currentStatus = try {
                db.querySync(conn, "SELECT OrderStatus FROM Orders WHERE OrderId=?", listOf(orderId)) { it.getString("OrderStatus") ?: "" }.firstOrNull() ?: ""
            } catch (_: Exception) { "" }
            if (currentStatus == "Completed") {
                restoreOrderStockSync(conn, orderId)
            }
            db.executeSync(conn,
                "UPDATE Orders SET OrderStatus='Cancelled', PaymentStatus='Paid', Notes=?, CancellationReason='Refunded', UpdatedAt=GETDATE() WHERE OrderId=?",
                listOf(newNotes, orderId))
            freeOrderTableSync(conn, orderId)
            cancelPendingKitchenTicketsSync(conn, orderId)

            // Record cash refund as a Cash Out entry in the drawer
            if (cashPaid > 0) {
                val orderNo = try {
                    db.querySync(conn, "SELECT ISNULL(OrderNo,'') AS N FROM Orders WHERE OrderId=?", listOf(orderId)) { it.getString("N") ?: "" }.firstOrNull() ?: ""
                } catch (_: Exception) { "" }
                runCatching {
                    db.executeSync(conn,
                        """INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt)
                           VALUES (?, ?, 'Out', ?, ?, ?, ?, GETDATE())""",
                        listOf(
                            session.currentShift.value?.shiftId,
                            session.currentBranchId.value,
                            cashPaid,
                            "Refund${if (orderNo.isNotBlank()) " - $orderNo" else ""}",
                            note,
                            session.currentUser.value?.userId ?: 0
                        )
                    )
                }
            }
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "REFUND_ORDER", "Orders", orderId) }
    }

    /** Mark a third-party delivery order as delivered — status update only, no payment (WPF MarkOrderDeliveredAsync). */
    suspend fun markDelivered(orderId: Int) {
        db.execute(
            "UPDATE Orders SET OrderStatus='Completed', UpdatedAt=GETDATE() WHERE OrderId=? AND OrderStatus NOT IN ('Cancelled','Void','Voided','Refunded','Completed')",
            listOf(orderId)
        )
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "ORDER_DELIVERED", "Orders", orderId) }
    }

    /** Cancel an active order; restore stock only if it was Completed (WPF behaviour). */
    suspend fun cancelActiveOrder(orderId: Int) {
        val currentStatus = try {
            db.queryOne("SELECT OrderStatus FROM Orders WHERE OrderId=?", listOf(orderId)) { it.getString("OrderStatus") ?: "" } ?: ""
        } catch (_: Exception) { "" }

        db.transaction { conn ->
            if (currentStatus == "Completed") {
                restoreOrderStockSync(conn, orderId)
            }
            db.executeSync(conn,
                "UPDATE Orders SET OrderStatus='Cancelled', UpdatedAt=GETDATE() WHERE OrderId=?",
                listOf(orderId))
            freeOrderTableSync(conn, orderId)
            cancelPendingKitchenTicketsSync(conn, orderId)
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "CANCEL_ORDER", "Orders", orderId) }
    }

    private fun freeOrderTableSync(conn: java.sql.Connection, orderId: Int) {
        try {
            db.executeSync(conn,
                "UPDATE Tables SET TableStatus = 'Available' WHERE TableId = (SELECT TableId FROM Orders WHERE OrderId = ? AND TableId IS NOT NULL)",
                listOf(orderId))
        } catch (_: Exception) {}
    }

    private fun cancelPendingKitchenTicketsSync(conn: java.sql.Connection, orderId: Int) {
        try {
            db.executeSync(conn,
                "UPDATE KitchenTickets SET TicketStatus = 'Cancelled' WHERE OrderId = ? AND TicketStatus = 'Pending'",
                listOf(orderId))
        } catch (_: Exception) {}
    }

    /**
     * Restore stock for a completed order on cancel / delete / revert.
     * Mirrors WPF CancelOrderAsync / HardDeleteOrderAsync / RevertOrderToPendingAsync.
     * Tries WPF schema (Recipes+RecipeItems) first, falls back to local SQLite (ProductRecipes).
     * Uses INSERT…SELECT FROM Products so BalanceQty is the actual post-update value.
     */
    // recipeDirectRemarks: used for both recipe-ingredient and direct-product reversal rows
    // modifierRemarks: used for modifier-linked stock reversal rows
    // Defaults match WPF CancelOrderAsync; callers for delete/revert pass different strings.
    private fun restoreOrderStockSync(
        conn: java.sql.Connection,
        orderId: Int,
        recipeDirectRemarks: String = "Order cancellation reversal",
        modifierRemarks: String = "Modifier stock reversal"
    ) {
        // ── 1. Recipe ingredients ─────────────────────────────────────────────
        // WPF: gates on p.IsRecipeBased=1 first; exact size match (both NULL or both equal)
        val recipeRestores: List<Triple<Int, Double, Double>> = try {
            db.querySync(conn,
                """SELECT ri.ProductId,
                          SUM(oi.Quantity * ri.QuantityUsed) AS RestoreQty,
                          ISNULL(p_ing.PurchasePrice, 0) AS Rate
                   FROM OrderItems oi
                   JOIN Products p_main ON p_main.ProductId = oi.ProductId
                                       AND ISNULL(p_main.IsRecipeBased, 0) = 1
                   JOIN Recipes r ON r.RecipeId = (
                       SELECT TOP 1 RecipeId FROM Recipes r2
                       WHERE r2.ProductId = p_main.ProductId
                         AND (r2.SizeId = oi.SizeId OR (r2.SizeId IS NULL AND oi.SizeId IS NULL))
                         AND r2.IsActive = 1
                       ORDER BY r2.RecipeId DESC
                   )
                   JOIN RecipeItems ri ON ri.RecipeId = r.RecipeId
                   JOIN Products p_ing ON p_ing.ProductId = ri.ProductId
                   WHERE oi.OrderId = ?
                   GROUP BY ri.ProductId, p_ing.PurchasePrice""",
                listOf(orderId)
            ) { rs -> Triple(rs.getInt("ProductId"), rs.getDouble("RestoreQty"), rs.getDouble("Rate")) }
        } catch (_: Exception) { emptyList() }
        for ((pid, qty, rate) in recipeRestores) {
            try {
                db.executeSync(conn, "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) + ? WHERE ProductId = ?", listOf(qty, pid))
                db.executeSync(conn,
                    """INSERT INTO InventoryLedger (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                       SELECT ?,GETDATE(),'OrderCancel',?,?,0,ISNULL(CurrentStock,0),?,?,?
                       FROM Products WHERE ProductId = ?""",
                    listOf(pid, orderId, qty, rate, qty * rate, recipeDirectRemarks, pid))
            } catch (_: Exception) {}
        }

        // ── 2. Direct stock-managed products (no recipe) ─────────────────────
        val recipeProductIds = recipeRestores.map { it.first }.toSet()
        val directRestores: List<Triple<Int, Double, Double>> = try {
            db.querySync(conn,
                """SELECT oi.ProductId,
                          SUM(oi.Quantity) AS RestoreQty,
                          ISNULL(p.PurchasePrice, 0) AS Rate
                   FROM OrderItems oi
                   JOIN Products p ON p.ProductId = oi.ProductId
                   WHERE oi.OrderId = ? AND ISNULL(p.IsStockManaged,0) = 1
                     AND ISNULL(p.IsRecipeBased,0) = 0
                     AND NOT EXISTS (
                         SELECT 1 FROM Recipes r WHERE r.ProductId = p.ProductId AND r.IsActive = 1
                     )
                   GROUP BY oi.ProductId, p.PurchasePrice""",
                listOf(orderId)
            ) { rs -> Triple(rs.getInt("ProductId"), rs.getDouble("RestoreQty"), rs.getDouble("Rate")) }
        } catch (_: Exception) {
            try {
                db.querySync(conn,
                    """SELECT oi.ProductId, SUM(oi.Quantity) AS RestoreQty, ISNULL(p.PurchasePrice,0) AS Rate
                       FROM OrderItems oi JOIN Products p ON p.ProductId = oi.ProductId
                       WHERE oi.OrderId = ? AND ISNULL(p.IsStockManaged,0) = 1
                       GROUP BY oi.ProductId, p.PurchasePrice""",
                    listOf(orderId)
                ) { rs -> Triple(rs.getInt("ProductId"), rs.getDouble("RestoreQty"), rs.getDouble("Rate")) }
                    .filter { it.first !in recipeProductIds }
            } catch (_: Exception) { emptyList() }
        }
        for ((pid, qty, rate) in directRestores) {
            try {
                db.executeSync(conn, "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) + ? WHERE ProductId = ?", listOf(qty, pid))
                db.executeSync(conn,
                    """INSERT INTO InventoryLedger (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                       SELECT ?,GETDATE(),'OrderCancel',?,?,0,ISNULL(CurrentStock,0),?,?,?
                       FROM Products WHERE ProductId = ?""",
                    listOf(pid, orderId, qty, rate, qty * rate, recipeDirectRemarks, pid))
            } catch (_: Exception) {}
        }

        // ── 3. Modifier-linked stock ──────────────────────────────────────────
        val modRestores: List<Triple<Int, Double, Double>> = try {
            db.querySync(conn,
                """SELECT pm.StockItemId AS ProductId,
                          SUM(oi.Quantity * oim.Quantity) AS RestoreQty,
                          ISNULL(p.PurchasePrice,0) AS Rate
                   FROM OrderItems oi
                   JOIN OrderItemModifiers oim ON oim.OrderItemId = oi.OrderItemId
                   JOIN ProductModifiers pm ON pm.ModifierId = oim.ModifierId
                   JOIN Products p ON p.ProductId = pm.StockItemId
                   WHERE oi.OrderId = ? AND pm.StockItemId IS NOT NULL
                     AND ISNULL(p.IsStockManaged,0) = 1
                   GROUP BY pm.StockItemId, p.PurchasePrice""",
                listOf(orderId)
            ) { rs -> Triple(rs.getInt("ProductId"), rs.getDouble("RestoreQty"), rs.getDouble("Rate")) }
        } catch (_: Exception) { emptyList() }
        for ((pid, qty, rate) in modRestores) {
            try {
                db.executeSync(conn, "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) + ? WHERE ProductId = ?", listOf(qty, pid))
                db.executeSync(conn,
                    """INSERT INTO InventoryLedger (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                       SELECT ?,GETDATE(),'OrderCancel',?,?,0,ISNULL(CurrentStock,0),?,?,?
                       FROM Products WHERE ProductId = ?""",
                    listOf(pid, orderId, qty, rate, qty * rate, modifierRemarks, pid))
            } catch (_: Exception) {}
        }
    }

    // Mirrors WPF AddPaymentAsync paths 1-3 exactly.
    private fun deductOrderStockSync(conn: java.sql.Connection, orderId: Int) {
        // ── 1. Recipe ingredients ─────────────────────────────────────────────
        // WPF: p.IsRecipeBased=1 gate; exact size match (both NULL or both equal); Remarks='Order sale'
        val recipeDeductions: List<Triple<Int, Double, Double>> = try {
            db.querySync(conn,
                """SELECT ri.ProductId,
                          SUM(oi.Quantity * ri.QuantityUsed) AS OutQty,
                          ISNULL(p_ing.PurchasePrice, 0) AS Rate
                   FROM OrderItems oi
                   JOIN Products p_main ON p_main.ProductId = oi.ProductId
                                       AND ISNULL(p_main.IsRecipeBased, 0) = 1
                   JOIN Recipes r ON r.RecipeId = (
                       SELECT TOP 1 RecipeId FROM Recipes r2
                       WHERE r2.ProductId = p_main.ProductId
                         AND (r2.SizeId = oi.SizeId OR (r2.SizeId IS NULL AND oi.SizeId IS NULL))
                         AND r2.IsActive = 1
                       ORDER BY r2.RecipeId DESC
                   )
                   JOIN RecipeItems ri ON ri.RecipeId = r.RecipeId
                   JOIN Products p_ing ON p_ing.ProductId = ri.ProductId
                   WHERE oi.OrderId = ?
                   GROUP BY ri.ProductId, p_ing.PurchasePrice""",
                listOf(orderId)
            ) { rs -> Triple(rs.getInt("ProductId"), rs.getDouble("OutQty"), rs.getDouble("Rate")) }
        } catch (_: Exception) { emptyList() }
        for ((pid, qty, rate) in recipeDeductions) {
            try {
                db.executeSync(conn, "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) - ? WHERE ProductId = ?", listOf(qty, pid))
                db.executeSync(conn,
                    """INSERT INTO InventoryLedger (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                       SELECT ?,GETDATE(),'Sale',?,0,?,ISNULL(CurrentStock,0),?,?,'Order sale'
                       FROM Products WHERE ProductId = ?""",
                    listOf(pid, orderId, qty, rate, qty * rate, pid))
            } catch (_: Exception) {}
        }

        // ── 2. Direct stock-managed products (no recipe) ─────────────────────
        // WPF: IsStockManaged=1 AND IsRecipeBased=0 AND no active Recipes row; Remarks='Direct stock sale'
        val recipeProductIds = recipeDeductions.map { it.first }.toSet()
        val directDeductions: List<Triple<Int, Double, Double>> = try {
            db.querySync(conn,
                """SELECT oi.ProductId,
                          SUM(oi.Quantity) AS OutQty,
                          ISNULL(p.PurchasePrice, 0) AS Rate
                   FROM OrderItems oi
                   JOIN Products p ON p.ProductId = oi.ProductId
                   WHERE oi.OrderId = ? AND ISNULL(p.IsStockManaged,0) = 1
                     AND ISNULL(p.IsRecipeBased,0) = 0
                     AND NOT EXISTS (
                         SELECT 1 FROM Recipes r WHERE r.ProductId = p.ProductId AND r.IsActive = 1
                     )
                   GROUP BY oi.ProductId, p.PurchasePrice""",
                listOf(orderId)
            ) { rs -> Triple(rs.getInt("ProductId"), rs.getDouble("OutQty"), rs.getDouble("Rate")) }
        } catch (_: Exception) {
            try {
                db.querySync(conn,
                    """SELECT oi.ProductId, SUM(oi.Quantity) AS OutQty, ISNULL(p.PurchasePrice,0) AS Rate
                       FROM OrderItems oi JOIN Products p ON p.ProductId = oi.ProductId
                       WHERE oi.OrderId = ? AND ISNULL(p.IsStockManaged,0) = 1
                       GROUP BY oi.ProductId, p.PurchasePrice""",
                    listOf(orderId)
                ) { rs -> Triple(rs.getInt("ProductId"), rs.getDouble("OutQty"), rs.getDouble("Rate")) }
                    .filter { it.first !in recipeProductIds }
            } catch (_: Exception) { emptyList() }
        }
        for ((pid, qty, rate) in directDeductions) {
            try {
                db.executeSync(conn, "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) - ? WHERE ProductId = ?", listOf(qty, pid))
                db.executeSync(conn,
                    """INSERT INTO InventoryLedger (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                       SELECT ?,GETDATE(),'Sale',?,0,?,ISNULL(CurrentStock,0),?,?,'Direct stock sale'
                       FROM Products WHERE ProductId = ?""",
                    listOf(pid, orderId, qty, rate, qty * rate, pid))
            } catch (_: Exception) {}
        }

        // ── 3. Modifier-linked stock ──────────────────────────────────────────
        // WPF: pm.StockItemId IS NOT NULL AND p.IsStockManaged=1; Remarks='Modifier stock deduction'
        val modDeductions: List<Triple<Int, Double, Double>> = try {
            db.querySync(conn,
                """SELECT pm.StockItemId AS ProductId,
                          SUM(oi.Quantity * oim.Quantity) AS OutQty,
                          ISNULL(p.PurchasePrice,0) AS Rate
                   FROM OrderItems oi
                   JOIN OrderItemModifiers oim ON oim.OrderItemId = oi.OrderItemId
                   JOIN ProductModifiers pm ON pm.ModifierId = oim.ModifierId
                   JOIN Products p ON p.ProductId = pm.StockItemId
                   WHERE oi.OrderId = ? AND pm.StockItemId IS NOT NULL
                     AND ISNULL(p.IsStockManaged,0) = 1
                   GROUP BY pm.StockItemId, p.PurchasePrice""",
                listOf(orderId)
            ) { rs -> Triple(rs.getInt("ProductId"), rs.getDouble("OutQty"), rs.getDouble("Rate")) }
        } catch (_: Exception) { emptyList() }
        for ((pid, qty, rate) in modDeductions) {
            try {
                db.executeSync(conn, "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) - ? WHERE ProductId = ?", listOf(qty, pid))
                db.executeSync(conn,
                    """INSERT INTO InventoryLedger (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,BalanceQty,Rate,Amount,Remarks)
                       SELECT ?,GETDATE(),'Sale',?,0,?,ISNULL(CurrentStock,0),?,?,'Modifier stock deduction'
                       FROM Products WHERE ProductId = ?""",
                    listOf(pid, orderId, qty, rate, qty * rate, pid))
            } catch (_: Exception) {}
        }
    }

    suspend fun addPayment(orderId: Int, payments: List<PaymentEntry>, createdBy: Int) {
        db.transaction { conn ->
            // Snapshot pre-payment state to detect order completion transition
            data class OrderSnapshot(val paidAmount: Double, val grandTotal: Double, val status: String)
            val snapshot = try {
                db.querySync(conn,
                    "SELECT ISNULL(PaidAmount,0) AS PaidAmount, ISNULL(GrandTotal,0) AS GrandTotal, ISNULL(OrderStatus,'') AS OrderStatus FROM Orders WHERE OrderId = ?",
                    listOf(orderId)
                ) { rs -> OrderSnapshot(rs.getDouble("PaidAmount"), rs.getDouble("GrandTotal"), rs.getString("OrderStatus") ?: "") }.firstOrNull()
            } catch (_: Exception) { null }
            val wasAlreadyCompleted = snapshot?.status == "Completed" || (snapshot != null && snapshot.paidAmount >= snapshot.grandTotal && snapshot.grandTotal > 0)
            val newPaymentsTotal = payments.sumOf { it.amount }
            val completing = !wasAlreadyCompleted && snapshot != null &&
                (snapshot.paidAmount + newPaymentsTotal) >= snapshot.grandTotal && snapshot.grandTotal > 0

            for (p in payments) {
                db.executeSync(
                    conn,
                    "INSERT INTO OrderPayments (OrderId, PaymentMethod, Amount, Reference, PaymentDate, CreatedBy) VALUES (?,?,?,?,GETDATE(),?)",
                    listOf(orderId, p.paymentMethod, p.amount, p.reference.ifEmpty { null }, createdBy)
                )
            }
            // Recalculate paid & balance
            val statusSql = if (completing) "OrderStatus = 'Completed'," else ""
            db.executeSync(
                conn,
                """UPDATE Orders
                   SET PaidAmount    = (SELECT ISNULL(SUM(Amount),0) FROM OrderPayments WHERE OrderId = ?),
                       BalanceAmount = GrandTotal - (SELECT ISNULL(SUM(Amount),0) FROM OrderPayments WHERE OrderId = ?),
                       PaymentStatus = CASE
                           WHEN (SELECT ISNULL(SUM(Amount),0) FROM OrderPayments WHERE OrderId = ?) >= GrandTotal THEN 'Paid'
                           WHEN (SELECT ISNULL(SUM(Amount),0) FROM OrderPayments WHERE OrderId = ?) > 0 THEN 'Partial'
                           ELSE 'Unpaid' END,
                       $statusSql
                       UpdatedAt = GETDATE()
                   WHERE OrderId = ?""",
                listOf(orderId, orderId, orderId, orderId, orderId)
            )

            // Deduct stock only when order transitions to Completed (WPF behaviour)
            if (completing) {
                deductOrderStockSync(conn, orderId)

                // Queue for FBR submission if enabled
                val fbrEnabled = try {
                    db.querySync(conn, "SELECT ISNULL(FbrEnabled,0) AS V FROM CompanySettings", emptyList()) { rs -> rs.getBoolean("V") }.firstOrNull() ?: false
                } catch (_: Exception) { false }
                if (fbrEnabled) {
                    try {
                        db.executeSync(conn,
                            "UPDATE Orders SET FbrStatus='Pending' WHERE OrderId=? AND ISNULL(FbrStatus,'') NOT IN ('Pending','Submitted')",
                            listOf(orderId))
                    } catch (_: Exception) {}
                }
            }

            // Free the table when order is fully paid
            try {
                db.executeSync(
                    conn,
                    """UPDATE Tables SET TableStatus = 'Available'
                       WHERE TableId = (SELECT TableId FROM Orders WHERE OrderId = ? AND TableId IS NOT NULL)
                         AND (SELECT ISNULL(SUM(Amount),0) FROM OrderPayments WHERE OrderId = ?)
                             >= (SELECT GrandTotal FROM Orders WHERE OrderId = ?)""",
                    listOf(orderId, orderId, orderId)
                )
            } catch (_: Exception) {}

            // Update customer lifetime stats when order is fully paid
            try {
                db.executeSync(
                    conn,
                    """UPDATE Customers
                       SET TotalOrders = ISNULL(TotalOrders, 0) + 1,
                           TotalSpent  = ISNULL(TotalSpent, 0) + (SELECT GrandTotal FROM Orders WHERE OrderId = ?)
                       WHERE CustomerId = (SELECT CustomerId FROM Orders WHERE OrderId = ? AND CustomerId IS NOT NULL)
                         AND (SELECT ISNULL(SUM(Amount),0) FROM OrderPayments WHERE OrderId = ?)
                             >= (SELECT GrandTotal FROM Orders WHERE OrderId = ?)""",
                    listOf(orderId, orderId, orderId, orderId)
                )
            } catch (_: Exception) {}

            // Award loyalty points: 1 pt per 10 currency units of grand total
            try {
                val points = (snapshot?.grandTotal?.toInt() ?: 0) / 10
                if (points > 0) {
                    db.executeSync(
                        conn,
                        """UPDATE Customers
                           SET LoyaltyPoints = ISNULL(LoyaltyPoints, 0) + ?,
                               UpdatedAt     = GETDATE()
                           WHERE CustomerId = (SELECT CustomerId FROM Orders WHERE OrderId = ? AND CustomerId IS NOT NULL)""",
                        listOf(points, orderId)
                    )
                }
            } catch (_: Exception) {}
        }
        runCatching { audit.writeAudit(createdBy, "ADD_PAYMENT", "Orders", orderId) }

        // Accounting: post Cash Sale entry for cash payments on completion
        runCatching {
            val cashPayments = payments.filter { it.paymentMethod.equals("Cash", ignoreCase = true) }
            val cashTotal = cashPayments.sumOf { it.amount }
            if (cashTotal > 0) {
                val debitId  = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode = '1000' AND IsActive = 1", emptyList()) { it.getInt("AccountId") } ?: 0
                val creditId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode = '4000' AND IsActive = 1", emptyList()) { it.getInt("AccountId") } ?: 0
                if (debitId > 0 && creditId > 0) {
                    val sql = "INSERT INTO AccountLedger (AccountId,EntryDate,Debit,Credit,ReferenceType,ReferenceId,Narration,CreatedBy,CreatedAt) VALUES (?,GETDATE(),?,?,?,?,?,?,GETDATE())"
                    db.execute(sql, listOf(debitId,  cashTotal, 0.0,       "Sale", orderId, "Cash sale", createdBy))
                    db.execute(sql, listOf(creditId, 0.0,       cashTotal, "Sale", orderId, "Cash sale", createdBy))
                }
            }
        }
    }

    suspend fun getOrderPayments(orderId: Int): List<PaymentEntry> = try {
        db.query(
            "SELECT PaymentMethod, Amount, ISNULL(Reference,'') AS Reference FROM OrderPayments WHERE OrderId = ? ORDER BY PaymentDate",
            listOf(orderId)
        ) { rs ->
            PaymentEntry(
                paymentMethod = rs.getString("PaymentMethod") ?: "Cash",
                amount        = rs.getDouble("Amount"),
                reference     = rs.getString("Reference") ?: ""
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getOrderById(orderId: Int): Order? = db.query(
        """SELECT o.*,
                  ISNULL(t.TableName,'')    AS TableName,
                  ISNULL(w.WaiterName,'')   AS WaiterName,
                  ISNULL(c.CustomerName,'') AS CustomerName,
                  (SELECT COUNT(*) FROM OrderItems oi WHERE oi.OrderId = o.OrderId) AS ItemCount
           FROM Orders o
           LEFT JOIN Tables    t ON t.TableId    = o.TableId
           LEFT JOIN Waiters   w ON w.WaiterId   = o.WaiterId
           LEFT JOIN Customers c ON c.CustomerId = o.CustomerId
           WHERE o.OrderId = ?""",
        listOf(orderId)
    ) { rs -> mapOrder(rs) }.firstOrNull()

    suspend fun getPendingOrders(): List<Order> = db.query(
        """SELECT o.*,
                  ISNULL(t.TableName,'')    AS TableName,
                  ISNULL(w.WaiterName,'')   AS WaiterName,
                  ISNULL(c.CustomerName,'') AS CustomerName,
                  (SELECT COUNT(*) FROM OrderItems oi WHERE oi.OrderId = o.OrderId) AS ItemCount
           FROM Orders o
           LEFT JOIN Tables    t ON t.TableId    = o.TableId
           LEFT JOIN Waiters   w ON w.WaiterId   = o.WaiterId
           LEFT JOIN Customers c ON c.CustomerId = o.CustomerId
           WHERE o.OrderStatus IN ('New','Held','SentToKitchen','Ready')
             AND o.PaymentStatus IN ('Unpaid','Partial')
             AND ISNULL(o.BranchId,1) = ?
           ORDER BY o.CreatedAt DESC""",
        listOf(session.currentBranchId.value)
    ) { rs -> mapOrder(rs) }

    suspend fun getAllOrders(
        fromDate: java.util.Date? = null,
        toDate:   java.util.Date? = null,
        status:   String = "All",
        search:   String = "",
        limit:    Int = 200
    ): List<Order> {
        val conditions = mutableListOf("ISNULL(o.BranchId,1) = ?")
        val params     = mutableListOf<Any?>(session.currentBranchId.value)
        fromDate?.let { conditions += "CAST(o.CreatedAt AS DATE) >= CAST(? AS DATE)"; params += java.sql.Date(it.time) }
        toDate?.let   { conditions += "CAST(o.CreatedAt AS DATE) <= CAST(? AS DATE)"; params += java.sql.Date(it.time) }
        if (status != "All") { conditions += "o.OrderStatus = ?"; params += status }
        if (search.isNotBlank()) {
            conditions += "(o.TokenNo LIKE ? OR o.OrderNo LIKE ? OR ISNULL(o.Notes,'') LIKE ? OR ISNULL(c.CustomerName,'') LIKE ?)"
            val q = "%$search%"; params += q; params += q; params += q; params += q
        }
        val where = conditions.joinToString(" AND ")
        return db.query(
            """SELECT TOP ($limit) o.*,
               ISNULL(t.TableName,'')    AS TableName,
               ISNULL(w.WaiterName,'')   AS WaiterName,
               ISNULL(c.CustomerName,'') AS CustomerName,
               (SELECT COUNT(*) FROM OrderItems oi WHERE oi.OrderId = o.OrderId) AS ItemCount
               FROM Orders o
               LEFT JOIN Tables    t ON t.TableId    = o.TableId
               LEFT JOIN Waiters   w ON w.WaiterId   = o.WaiterId
               LEFT JOIN Customers c ON c.CustomerId = o.CustomerId
               WHERE $where ORDER BY o.CreatedAt DESC""",
            params
        ) { rs -> mapOrder(rs) }
    }

    suspend fun getOrderItems(orderId: Int): List<OrderItem> {
        val items = try {
            db.query(
                """SELECT oi.*, oi.SizeNameSnapshot, ISNULL(kp.PrinterName, '') AS PrinterName
                   FROM OrderItems oi
                   LEFT JOIN Products p ON p.ProductId = oi.ProductId
                   LEFT JOIN KitchenPrinters kp ON p.KitchenPrinterId = kp.PrinterId
                   WHERE oi.OrderId = ?
                   ORDER BY oi.OrderItemId""",
                listOf(orderId)
            ) { rs ->
                OrderItem(
                    orderItemId         = rs.getInt("OrderItemId"),
                    orderId             = rs.getInt("OrderId"),
                    productId           = rs.getInt("ProductId"),
                    sizeId              = rs.getInt("SizeId").takeIf { it > 0 },
                    productNameSnapshot = rs.getString("ProductNameSnapshot") ?: "",
                    sizeNameSnapshot    = rs.getString("SizeNameSnapshot"),
                    quantity            = rs.getDouble("Quantity"),
                    unitPrice           = rs.getDouble("UnitPrice"),
                    discountAmount      = rs.getDouble("DiscountAmount"),
                    lineTotal           = rs.getDouble("LineTotal"),
                    notes               = rs.getString("Notes"),
                    kitchenStatus       = rs.getString("KitchenStatus") ?: "Pending",
                    printerName         = rs.getString("PrinterName") ?: ""
                )
            }
        } catch (_: Exception) {
            db.query(
                """SELECT oi.*, oi.SizeNameSnapshot
                   FROM OrderItems oi
                   WHERE oi.OrderId = ?
                   ORDER BY oi.OrderItemId""",
                listOf(orderId)
            ) { rs ->
                OrderItem(
                    orderItemId         = rs.getInt("OrderItemId"),
                    orderId             = rs.getInt("OrderId"),
                    productId           = rs.getInt("ProductId"),
                    sizeId              = rs.getInt("SizeId").takeIf { it > 0 },
                    productNameSnapshot = rs.getString("ProductNameSnapshot") ?: "",
                    sizeNameSnapshot    = rs.getString("SizeNameSnapshot"),
                    quantity            = rs.getDouble("Quantity"),
                    unitPrice           = rs.getDouble("UnitPrice"),
                    discountAmount      = rs.getDouble("DiscountAmount"),
                    lineTotal           = rs.getDouble("LineTotal"),
                    notes               = rs.getString("Notes"),
                    kitchenStatus       = rs.getString("KitchenStatus") ?: "Pending"
                )
            }
        }
        if (items.isEmpty()) return items

        val modifiers = try {
            db.query(
                """SELECT oim.* FROM OrderItemModifiers oim
                   JOIN OrderItems oi ON oim.OrderItemId = oi.OrderItemId
                   WHERE oi.OrderId = ?""",
                listOf(orderId)
            ) { rs ->
                OrderItemModifier(
                    orderItemModifierId  = rs.getInt("OrderItemModifierId"),
                    orderItemId          = rs.getInt("OrderItemId"),
                    modifierId           = rs.getInt("ModifierId"),
                    modifierNameSnapshot = rs.getString("ModifierNameSnapshot") ?: "",
                    extraPrice           = rs.getDouble("ExtraPrice"),
                    quantity             = rs.getInt("Quantity"),
                    total                = rs.getDouble("Total")
                )
            }
        } catch (_: Exception) { emptyList() }

        if (modifiers.isEmpty()) return items
        val modsByItem = modifiers.groupBy { it.orderItemId }
        return items.map { it.copy(modifiers = modsByItem[it.orderItemId] ?: emptyList()) }
    }

    /** Place a held order (status=Held, no kitchen ticket). Returns the token number. */
    suspend fun holdOrder(
        cart: List<CartItem>,
        orderType: String,
        tableId: Int?,
        waiterId: Int?,
        customerId: Int?,
        shiftId: Int?,
        discountAmount: Double,
        discountPercent: Double,
        taxAmount: Double,
        taxPercent: Double,
        serviceCharges: Double,
        grandTotal: Double,
        subTotal: Double,
        createdBy: Int,
        notes: String,
        settings: CompanySettings
    ): String {
        val orderNo = nextOrderNo()
        var tokenNo = ""

        db.transaction { conn ->
            val tokenPrefix = settings.tokenPrefix
            tokenNo = run {
                val n = try {
                    db.querySync(conn,
                        """SELECT ISNULL(MAX(TRY_CAST(REPLACE(TokenNo,?,?) AS INT)),0)+1 AS NextNum
                           FROM Orders WITH (UPDLOCK, HOLDLOCK)
                           WHERE TokenNo LIKE ? AND CAST(OrderDate AS DATE) = CAST(GETDATE() AS DATE)""",
                        listOf(tokenPrefix, "", "$tokenPrefix%")
                    ) { it.getInt("NextNum") }.firstOrNull() ?: 1
                } catch (_: Exception) { 1 }
                "$tokenPrefix${n.toString().padStart(3, '0')}"
            }

            val orderId = db.insertAndGetIdSync(
                conn,
                """INSERT INTO Orders (OrderNo, TokenNo, OrderDate, OrderType, TableId, WaiterId, CustomerId,
                   ShiftId, SubTotal, DiscountAmount, DiscountPercent, TaxAmount, TaxPercent,
                   ServiceCharges, DeliveryCharge, GrandTotal, PaidAmount, BalanceAmount,
                   OrderStatus, PaymentStatus, Notes, CreatedBy, CreatedAt)
                   VALUES (?,?,GETDATE(),?,?,?,?,?,?,?,?,?,?,?,0,?,0,?,?,?,?,?,GETDATE())""",
                listOf(orderNo, tokenNo, orderType, tableId, waiterId, customerId,
                       shiftId, subTotal, discountAmount, discountPercent,
                       taxAmount, taxPercent, serviceCharges,
                       grandTotal, grandTotal, "Held", "Unpaid", notes, createdBy)
            )
            try {
                db.executeSync(conn, "UPDATE Orders SET BranchId=? WHERE OrderId=?",
                    listOf(session.currentBranchId.value, orderId))
            } catch (_: Exception) {}

            for (item in cart) {
                db.insertAndGetIdSync(
                    conn,
                    """INSERT INTO OrderItems (OrderId, ProductId, SizeId, ProductNameSnapshot,
                       SizeNameSnapshot, Quantity, UnitPrice, DiscountAmount, LineTotal, Notes,
                       KitchenStatus, KitchenPrinterId, ProductNameOtherLanguageSnapshot, CreatedAt)
                       VALUES (?,?,?,?,?,?,?,?,?,?,'Pending',?,ISNULL(?,''),GETDATE())""",
                    listOf(orderId, item.productId, item.sizeId, item.productName,
                           item.sizeName, item.quantity.toDouble(), item.unitPrice,
                           item.discountAmount, item.lineTotal, item.notes.ifEmpty { null },
                           item.kitchenPrinterId, item.productNameOtherLanguage)
                )
            }
            if (orderType == "DineIn" && tableId != null) {
                db.executeSync(conn, "UPDATE Tables SET TableStatus = 'Occupied' WHERE TableId = ?",
                    listOf(tableId))
            }
            try {
                db.executeSync(conn,
                    "INSERT INTO HeldOrders (OrderId, HeldAt, HeldBy, Notes) VALUES (?, GETDATE(), ?, ?)",
                    listOf(orderId, createdBy, notes.ifEmpty { null }))
            } catch (_: Exception) {}
        }
        return tokenNo
    }

    /** Load order items as CartItems for resuming a held order. */
    suspend fun getCartItemsForOrder(orderId: Int): List<CartItem> = db.query(
        "SELECT * FROM OrderItems WHERE OrderId = ? ORDER BY OrderItemId",
        listOf(orderId)
    ) { rs ->
        CartItem(
            productId                = rs.getInt("ProductId"),
            productName              = rs.getString("ProductNameSnapshot") ?: "",
            sizeId                   = rs.getInt("SizeId").takeIf { it > 0 },
            sizeName                 = rs.getString("SizeNameSnapshot"),
            unitPrice                = rs.getDouble("UnitPrice"),
            quantity                 = rs.getDouble("Quantity").toInt().coerceAtLeast(1),
            discountAmount           = rs.getDouble("DiscountAmount"),
            notes                    = rs.getString("Notes") ?: "",
            kitchenPrinterId         = try { rs.getInt("KitchenPrinterId").takeIf { it > 0 } } catch (_: Exception) { null },
            productNameOtherLanguage = try { rs.getString("ProductNameOtherLanguageSnapshot") ?: "" } catch (_: Exception) { "" }
        )
    }

    /** Load order items WITH modifiers for full order editing. */
    suspend fun getCartItemsForOrderWithModifiers(orderId: Int): List<CartItem> {
        val items = db.query(
            "SELECT * FROM OrderItems WHERE OrderId = ? ORDER BY OrderItemId",
            listOf(orderId)
        ) { rs ->
            Pair(
                rs.getInt("OrderItemId"),
                CartItem(
                    productId                = rs.getInt("ProductId"),
                    productName              = rs.getString("ProductNameSnapshot") ?: "",
                    sizeId                   = rs.getInt("SizeId").takeIf { it > 0 },
                    sizeName                 = rs.getString("SizeNameSnapshot"),
                    unitPrice                = rs.getDouble("UnitPrice"),
                    quantity                 = rs.getDouble("Quantity").toInt().coerceAtLeast(1),
                    discountAmount           = rs.getDouble("DiscountAmount"),
                    notes                    = rs.getString("Notes") ?: "",
                    kitchenPrinterId         = try { rs.getInt("KitchenPrinterId").takeIf { it > 0 } } catch (_: Exception) { null },
                    productNameOtherLanguage = try { rs.getString("ProductNameOtherLanguageSnapshot") ?: "" } catch (_: Exception) { "" }
                )
            )
        }
        return items.map { (orderItemId, cartItem) ->
            val mods = try {
                db.query(
                    "SELECT * FROM OrderItemModifiers WHERE OrderItemId = ?",
                    listOf(orderItemId)
                ) { rs ->
                    com.fastpos.android.data.models.SelectedModifier(
                        modifierId   = rs.getInt("ModifierId"),
                        modifierName = rs.getString("ModifierNameSnapshot") ?: "",
                        extraPrice   = rs.getDouble("ExtraPrice"),
                        quantity     = rs.getInt("Quantity").coerceAtLeast(1)
                    )
                }
            } catch (_: Exception) { emptyList() }
            cartItem.copy(selectedModifiers = mods)
        }
    }

    /** Replace all items in an existing order (edit order flow). */
    suspend fun replaceOrderItems(
        orderId:   Int,
        cart:      List<CartItem>,
        subTotal:  Double,
        taxAmount: Double,
        discount:  Double,
        notes:     String,
        orderType: String? = null,
        tableId:   Int?    = null,
        waiterId:  Int?    = null
    ): Unit = db.transaction { conn ->
        // If order was already Completed, restore stock BEFORE deleting old items
        // (restoreOrderStockSync reads OrderItems to know what to reverse)
        val currentStatus = try {
            db.querySync(conn, "SELECT OrderStatus FROM Orders WHERE OrderId=?", listOf(orderId)) { it.getString("OrderStatus") ?: "" }.firstOrNull() ?: ""
        } catch (_: Exception) { "" }
        if (currentStatus == "Completed") {
            // MUST be called BEFORE deleting old OrderItems
            restoreOrderStockSync(conn, orderId,
                recipeDirectRemarks = "Order reverted for editing",
                modifierRemarks     = "Modifier stock reversal on revert")
        }

        // Remove old items + modifiers + kitchen ticket items
        try { db.executeSync(conn, "DELETE FROM OrderItemModifiers WHERE OrderItemId IN (SELECT OrderItemId FROM OrderItems WHERE OrderId = ?)", listOf(orderId)) } catch (_: Exception) {}
        try { db.executeSync(conn, "DELETE FROM KitchenTicketItems WHERE OrderItemId IN (SELECT OrderItemId FROM OrderItems WHERE OrderId = ?)", listOf(orderId)) } catch (_: Exception) {}
        db.executeSync(conn, "DELETE FROM OrderItems WHERE OrderId = ?", listOf(orderId))

        // Create a new kitchen ticket for the updated items
        val nextTicketNum = try {
            db.querySync(conn,
                "SELECT ISNULL(MAX(TRY_CAST(REPLACE(TicketNo,'KT-','') AS INT)),0)+1 AS NextNum FROM KitchenTickets WHERE TicketNo LIKE 'KT-%'",
                emptyList()
            ) { rs -> rs.getInt("NextNum") }.firstOrNull() ?: 1
        } catch (_: Exception) { 1 }
        val ticketNo = "KT-%05d".format(nextTicketNum)
        val ticketId = try {
            db.insertAndGetIdSync(conn,
                "INSERT INTO KitchenTickets (OrderId, TicketNo, PrintedAt, TicketStatus) VALUES (?,?,GETDATE(),?)",
                listOf(orderId, ticketNo, "Pending"))
        } catch (_: Exception) { -1 }

        for (item in cart) {
            val itemId = db.insertAndGetIdSync(conn,
                """INSERT INTO OrderItems (OrderId, ProductId, SizeId, ProductNameSnapshot,
                   SizeNameSnapshot, Quantity, UnitPrice, DiscountAmount, LineTotal, Notes,
                   KitchenStatus, CreatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,GETDATE())""",
                listOf(orderId, item.productId, item.sizeId, item.productName,
                       item.sizeName, item.quantity.toDouble(), item.unitPrice,
                       item.discountAmount, item.lineTotal, item.notes.ifEmpty { null }, "Pending"))

            for (mod in item.selectedModifiers) {
                try {
                    db.executeSync(conn,
                        "INSERT INTO OrderItemModifiers (OrderItemId, ModifierId, ModifierNameSnapshot, ExtraPrice, Quantity, Total) VALUES (?,?,?,?,?,?)",
                        listOf(itemId, mod.modifierId, mod.modifierName, mod.extraPrice, mod.quantity, mod.extraPrice * mod.quantity))
                } catch (_: Exception) {}
            }

            if (ticketId > 0) {
                val displayName = buildString {
                    append(item.productName)
                    item.sizeName?.let { append(" [$it]") }
                    if (item.selectedModifiers.isNotEmpty())
                        append(" + " + item.selectedModifiers.joinToString(", ") { it.modifierName })
                }
                try {
                    db.executeSync(conn,
                        "INSERT INTO KitchenTicketItems (TicketId, OrderItemId, ItemName, Quantity, Notes, ItemStatus) VALUES (?,?,?,?,?,?)",
                        listOf(ticketId, itemId, displayName, item.quantity.toDouble(), item.notes.ifEmpty { null }, "Pending"))
                } catch (_: Exception) {}
            }
        }

        val serviceCharge = try {
            db.querySync(conn, "SELECT ISNULL(ServiceCharges,0) AS SC FROM Orders WHERE OrderId=?", listOf(orderId)) { rs -> rs.getDouble("SC") }.firstOrNull() ?: 0.0
        } catch (_: Exception) { 0.0 }
        val grandTotal = subTotal + taxAmount + serviceCharge - discount

        val extraSets   = buildString {
            if (orderType != null) append(", OrderType = ?")
            if (tableId   != null) append(", TableId   = ?")
            if (waiterId  != null) append(", WaiterId  = ?")
        }
        val extraParams = mutableListOf<Any?>()
        if (orderType != null) extraParams.add(orderType)
        if (tableId   != null) extraParams.add(tableId)
        if (waiterId  != null) extraParams.add(waiterId)

        db.executeSync(conn,
            """UPDATE Orders SET
                   SubTotal      = ?,
                   TaxAmount     = ?,
                   DiscountAmount= ?,
                   GrandTotal    = ?,
                   BalanceAmount = ? - ISNULL(PaidAmount,0),
                   Notes         = ?,
                   OrderStatus   = 'SentToKitchen',
                   UpdatedAt     = GETDATE()$extraSets
               WHERE OrderId = ?""",
            listOf(subTotal, taxAmount, discount, grandTotal, grandTotal, notes.ifEmpty { null }) + extraParams + listOf(orderId))
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "UPDATE_ORDER", "Orders", orderId) }
    }

    /** Append new items to an existing active order and update its totals. */
    suspend fun addItemsToOrder(
        orderId:     Int,
        cart:        List<CartItem>,
        newSubTotal: Double,
        newTaxAmount: Double
    ): Unit = db.transaction { conn ->
        val nextTicketNum = try {
            db.querySync(
                conn,
                "SELECT ISNULL(MAX(TRY_CAST(REPLACE(TicketNo,'KT-','') AS INT)),0)+1 AS NextNum FROM KitchenTickets WHERE TicketNo LIKE 'KT-%'",
                emptyList()
            ) { rs -> rs.getInt("NextNum") }.firstOrNull() ?: 1
        } catch (_: Exception) { 1 }
        val ticketNo = "KT-%05d".format(nextTicketNum)
        val ticketId = db.insertAndGetIdSync(
            conn,
            "INSERT INTO KitchenTickets (OrderId, TicketNo, PrintedAt, TicketStatus) VALUES (?,?,GETDATE(),?)",
            listOf(orderId, ticketNo, "Pending")
        )

        for (item in cart) {
            val itemId = db.insertAndGetIdSync(
                conn,
                """INSERT INTO OrderItems (OrderId, ProductId, SizeId, ProductNameSnapshot,
                   SizeNameSnapshot, Quantity, UnitPrice, DiscountAmount, LineTotal, Notes,
                   KitchenStatus, CreatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,GETDATE())""",
                listOf(orderId, item.productId, item.sizeId, item.productName,
                       item.sizeName, item.quantity.toDouble(), item.unitPrice,
                       item.discountAmount, item.lineTotal, item.notes.ifEmpty { null },
                       "Pending")
            )

            for (mod in item.selectedModifiers) {
                db.executeSync(
                    conn,
                    "INSERT INTO OrderItemModifiers (OrderItemId, ModifierId, ModifierNameSnapshot, ExtraPrice, Quantity, Total) VALUES (?,?,?,?,?,?)",
                    listOf(itemId, mod.modifierId, mod.modifierName, mod.extraPrice,
                           mod.quantity, mod.extraPrice * mod.quantity)
                )
            }

            val displayName = buildString {
                append(item.productName)
                item.sizeName?.let { append(" [$it]") }
                if (item.selectedModifiers.isNotEmpty())
                    append(" + " + item.selectedModifiers.joinToString(", ") { it.modifierName })
            }
            db.executeSync(
                conn,
                "INSERT INTO KitchenTicketItems (TicketId, OrderItemId, ItemName, Quantity, Notes, ItemStatus) VALUES (?,?,?,?,?,?)",
                listOf(ticketId, itemId, displayName, item.quantity.toDouble(),
                       item.notes.ifEmpty { null }, "Pending")
            )
        }

        // Add to order totals and re-send to kitchen
        db.executeSync(
            conn,
            """UPDATE Orders SET
                   SubTotal      = SubTotal + ?,
                   TaxAmount     = TaxAmount + ?,
                   GrandTotal    = GrandTotal + ? + ?,
                   BalanceAmount = BalanceAmount + ? + ?,
                   OrderStatus   = 'SentToKitchen',
                   UpdatedAt     = GETDATE()
               WHERE OrderId = ?""",
            listOf(newSubTotal, newTaxAmount, newSubTotal, newTaxAmount,
                   newSubTotal, newTaxAmount, orderId)
        )
    }

    suspend fun transferOrder(orderId: Int, fromTableId: Int, toTableId: Int) {
        db.execute("UPDATE Orders SET TableId = ? WHERE OrderId = ?", listOf(toTableId, orderId))
        db.execute("UPDATE Tables SET TableStatus = 'Available' WHERE TableId = ?", listOf(fromTableId))
        db.execute("UPDATE Tables SET TableStatus = 'Occupied' WHERE TableId = ?", listOf(toTableId))
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "CHANGE_TABLE", "Orders", orderId) }
    }

    suspend fun getActiveOrdersByTable(): Map<Int, Order> {
        val orders = db.query(
            """SELECT o.*, ISNULL(t.TableName,'') AS TableName,
                      (SELECT COUNT(*) FROM OrderItems oi WHERE oi.OrderId = o.OrderId) AS ItemCount
               FROM Orders o
               LEFT JOIN Tables t ON o.TableId = t.TableId
               WHERE o.TableId IS NOT NULL
                 AND o.OrderStatus IN ('New','Held','SentToKitchen','Ready')
                 AND ISNULL(o.BranchId,1) = ?
               ORDER BY o.CreatedAt DESC""",
            listOf(session.currentBranchId.value)
        ) { rs -> mapOrder(rs) }
        return orders.groupBy { it.tableId ?: 0 }
            .filterKeys { it > 0 }
            .mapValues { it.value.first() }
    }

    suspend fun getOrdersByCustomer(customerId: Int): List<Order> = db.query(
        """SELECT TOP 50 o.*, ISNULL(t.TableName,'') AS TableName,
           (SELECT COUNT(*) FROM OrderItems oi WHERE oi.OrderId = o.OrderId) AS ItemCount
           FROM Orders o
           LEFT JOIN Tables t ON o.TableId = t.TableId
           WHERE o.CustomerId = ?
           ORDER BY o.CreatedAt DESC""",
        listOf(customerId)
    ) { rs -> mapOrder(rs) }

    suspend fun getTables(): List<com.fastpos.android.data.models.RestaurantTable> {
        // Repair orphaned Occupied statuses — tables stuck Occupied but with no active order
        try {
            db.execute(
                """UPDATE Tables
                   SET TableStatus = 'Available'
                   WHERE TableStatus = 'Occupied'
                     AND TableId NOT IN (
                         SELECT DISTINCT TableId FROM Orders
                         WHERE TableId IS NOT NULL
                           AND OrderStatus IN ('New','Held','SentToKitchen','Ready')
                     )""",
                emptyList()
            )
        } catch (_: Exception) {}

        return db.query(
            """SELECT t.TableId, t.TableName, t.AreaId, t.Capacity, t.TableStatus, t.IsActive,
                      da.AreaName
               FROM Tables t
               LEFT JOIN DiningAreas da ON t.AreaId = da.AreaId
               WHERE t.IsActive = 1
               ORDER BY t.TableName"""
        ) { rs ->
            RestaurantTable(
                tableId     = rs.getInt("TableId"),
                tableName   = rs.getString("TableName") ?: "",
                areaId      = rs.getInt("AreaId").takeIf { it > 0 },
                areaName    = rs.getString("AreaName"),
                capacity    = rs.getInt("Capacity"),
                tableStatus = rs.getString("TableStatus") ?: "Available"
            )
        }
    }

    suspend fun getWaiters(): List<com.fastpos.android.data.models.Waiter> = try {
        db.query(
            "SELECT WaiterId, WaiterName FROM Waiters WHERE IsActive = 1 ORDER BY WaiterName"
        ) { rs -> Waiter(waiterId = rs.getInt("WaiterId"), waiterName = rs.getString("WaiterName") ?: "") }
    } catch (_: Exception) {
        db.query(
            "SELECT WaiterId, WaiterName FROM Waiters ORDER BY WaiterName"
        ) { rs -> Waiter(waiterId = rs.getInt("WaiterId"), waiterName = rs.getString("WaiterName") ?: "") }
    }

    suspend fun getHeldOrders(): List<Order> = db.query(
        """SELECT o.OrderId, o.OrderNo, o.TokenNo, o.OrderDate, o.OrderType,
                  o.TableId, o.WaiterId, o.CustomerId, o.ShiftId,
                  o.SubTotal, o.DiscountAmount, o.DiscountPercent,
                  o.TaxAmount, o.TaxPercent, o.ServiceCharges, o.DeliveryCharge,
                  o.GrandTotal, o.PaidAmount, o.BalanceAmount,
                  o.OrderStatus, o.PaymentStatus, o.Notes, o.CreatedBy, o.CreatedAt,
                  ISNULL(t.TableName,'') AS TableName,
                  (SELECT COUNT(*) FROM OrderItems oi WHERE oi.OrderId = o.OrderId) AS ItemCount
           FROM Orders o
           LEFT JOIN Tables t ON t.TableId = o.TableId
           WHERE o.OrderStatus = 'Held' AND o.PaymentStatus = 'Unpaid'
             AND ISNULL(o.BranchId,1) = ?
           ORDER BY o.CreatedAt DESC""",
        listOf(session.currentBranchId.value)
    ) { rs -> mapOrder(rs) }

    suspend fun cancelHeldOrder(orderId: Int) {
        db.transaction { conn ->
            db.executeSync(conn, "UPDATE Orders SET OrderStatus = 'Cancelled' WHERE OrderId = ? AND OrderStatus = 'Held'",
                listOf(orderId))
            freeOrderTableSync(conn, orderId)
            try {
                db.executeSync(conn, "DELETE FROM HeldOrders WHERE OrderId = ?", listOf(orderId))
            } catch (_: Exception) {}
        }
    }

    suspend fun deleteHeldOrderRecord(orderId: Int) {
        try { db.execute("DELETE FROM HeldOrders WHERE OrderId = ?", listOf(orderId)) } catch (_: Exception) {}
    }

    suspend fun saveTip(orderId: Int, tipAmount: Double) {
        if (tipAmount <= 0) return
        try {
            db.execute("UPDATE Orders SET Tips=? WHERE OrderId=?", listOf(tipAmount, orderId))
        } catch (_: Exception) {}
    }

    suspend fun hardDeleteOrder(orderId: Int) {
        db.transaction { conn ->
            val currentStatus = try {
                db.querySync(conn, "SELECT OrderStatus FROM Orders WHERE OrderId=?", listOf(orderId)) { it.getString("OrderStatus") ?: "" }.firstOrNull() ?: ""
            } catch (_: Exception) { "" }
            if (currentStatus == "Completed") {
                restoreOrderStockSync(conn, orderId,
                    recipeDirectRemarks = "Order deleted reversal",
                    modifierRemarks     = "Modifier stock reversal on delete")
            }
            freeOrderTableSync(conn, orderId)

            try {
                db.executeSync(
                    conn,
                    "DELETE FROM KitchenTicketItems WHERE TicketId IN (SELECT TicketId FROM KitchenTickets WHERE OrderId = ?)",
                    listOf(orderId)
                )
            } catch (_: Exception) {}
            try {
                db.executeSync(conn, "DELETE FROM KitchenTickets WHERE OrderId = ?", listOf(orderId))
            } catch (_: Exception) {}
            try {
                db.executeSync(
                    conn,
                    "DELETE FROM OrderItemModifiers WHERE OrderItemId IN (SELECT OrderItemId FROM OrderItems WHERE OrderId = ?)",
                    listOf(orderId)
                )
            } catch (_: Exception) {}
            try {
                db.executeSync(conn, "DELETE FROM OrderItems WHERE OrderId = ?", listOf(orderId))
            } catch (_: Exception) {}
            try {
                db.executeSync(conn, "DELETE FROM OrderPayments WHERE OrderId = ?", listOf(orderId))
            } catch (_: Exception) {}
            db.executeSync(conn, "DELETE FROM Orders WHERE OrderId = ?", listOf(orderId))
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "DELETE", "Orders", orderId) }
    }

    suspend fun revertOrderToPending(orderId: Int) {
        db.transaction { conn ->
            val currentStatus = try {
                db.querySync(conn, "SELECT OrderStatus FROM Orders WHERE OrderId=?", listOf(orderId)) { it.getString("OrderStatus") ?: "" }.firstOrNull() ?: ""
            } catch (_: Exception) { "" }
            if (currentStatus == "Completed") {
                restoreOrderStockSync(conn, orderId,
                    recipeDirectRemarks = "Order reverted for editing",
                    modifierRemarks     = "Modifier stock reversal on revert")
            }
            try {
                db.executeSync(conn, "DELETE FROM OrderPayments WHERE OrderId = ?", listOf(orderId))
            } catch (_: Exception) {}
            db.executeSync(
                conn,
                """UPDATE Orders
                   SET OrderStatus   = 'SentToKitchen',
                       PaymentStatus = 'Unpaid',
                       PaidAmount    = 0,
                       BalanceAmount = GrandTotal,
                       UpdatedAt     = GETDATE()
                   WHERE OrderId = ?""",
                listOf(orderId)
            )
            try {
                db.executeSync(
                    conn,
                    "UPDATE KitchenTicketItems SET ItemStatus = 'Pending' WHERE TicketId IN (SELECT TicketId FROM KitchenTickets WHERE OrderId = ?)",
                    listOf(orderId)
                )
            } catch (_: Exception) {}
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "REVERT_ORDER", "Orders", orderId) }
    }

    // ── Order item management ─────────────────────────────────────────────────

    suspend fun removeOrderItem(orderId: Int, orderItemId: Int) {
        db.transaction { conn ->
            try { db.executeSync(conn, "DELETE FROM OrderItemModifiers WHERE OrderItemId = ?", listOf(orderItemId)) } catch (_: Exception) {}
            try { db.executeSync(conn, "DELETE FROM KitchenTicketItems WHERE OrderItemId = ?", listOf(orderItemId)) } catch (_: Exception) {}
            db.executeSync(conn, "DELETE FROM OrderItems WHERE OrderItemId = ? AND OrderId = ?", listOf(orderItemId, orderId))
            recalcOrderTotals(conn, orderId)
        }
    }

    suspend fun updateOrderItemQty(orderId: Int, orderItemId: Int, newQty: Double) {
        db.transaction { conn ->
            db.executeSync(conn,
                """UPDATE OrderItems
                   SET Quantity  = ?,
                       LineTotal = (UnitPrice + ISNULL((
                           SELECT SUM(oim.ExtraPrice * oim.Quantity)
                           FROM OrderItemModifiers oim
                           WHERE oim.OrderItemId = OrderItems.OrderItemId
                       ), 0)) * ?
                   WHERE OrderItemId = ? AND OrderId = ?""",
                listOf(newQty, newQty, orderItemId, orderId))
            try {
                db.executeSync(conn, "UPDATE KitchenTicketItems SET Quantity = ? WHERE OrderItemId = ?",
                    listOf(newQty, orderItemId))
            } catch (_: Exception) {}
            recalcOrderTotals(conn, orderId)
        }
    }

    suspend fun applyOrderDiscount(orderId: Int, discountAmount: Double) {
        db.execute(
            """UPDATE Orders
               SET DiscountAmount = ?,
                   GrandTotal = ISNULL(SubTotal,0) + ISNULL(TaxAmount,0) + ISNULL(ServiceCharges,0) + ISNULL(DeliveryCharge,0) - ?,
                   BalanceAmount = ISNULL(SubTotal,0) + ISNULL(TaxAmount,0) + ISNULL(ServiceCharges,0) + ISNULL(DeliveryCharge,0) - ? - ISNULL(PaidAmount,0)
               WHERE OrderId = ?""",
            listOf(discountAmount, discountAmount, discountAmount, orderId)
        )
    }

    private fun recalcOrderTotals(conn: java.sql.Connection, orderId: Int) {
        val subTotal = db.querySync(conn,
            "SELECT ISNULL(SUM(LineTotal), 0) AS Sub FROM OrderItems WHERE OrderId = ?",
            listOf(orderId)
        ) { it.getDouble("Sub") }.firstOrNull() ?: 0.0
        db.executeSync(conn,
            """UPDATE Orders
               SET SubTotal = ?,
                   GrandTotal = ? + ISNULL(TaxAmount,0) + ISNULL(ServiceCharges,0) + ISNULL(DeliveryCharge,0) - ISNULL(DiscountAmount,0),
                   BalanceAmount = ? + ISNULL(TaxAmount,0) + ISNULL(ServiceCharges,0) + ISNULL(DeliveryCharge,0) - ISNULL(DiscountAmount,0) - ISNULL(PaidAmount,0)
               WHERE OrderId = ?""",
            listOf(subTotal, subTotal, subTotal, orderId))
    }

    /** One-time repair: mark all fully-paid orders as Completed so reports show correct data. */
    suspend fun fixPaidOrderStatuses() = runCatching {
        db.execute(
            """UPDATE Orders SET OrderStatus = 'Completed'
               WHERE PaymentStatus = 'Paid'
                 AND OrderStatus NOT IN ('Completed','Cancelled','Void','Voided','Refunded')""",
            emptyList()
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun nextOrderNo(): String {
        return try {
            db.transaction { conn ->
                // Read current counter — same AppSettings key WPF uses
                val current = db.querySync(conn,
                    "SELECT SettingValue FROM AppSettings WHERE SettingKey = 'LastOrderNumber'"
                ) { it.getString("SettingValue") }.firstOrNull()?.toIntOrNull() ?: 0
                val next = current + 1
                // Atomically write back (inside the same transaction)
                db.executeSync(conn,
                    "UPDATE AppSettings SET SettingValue = ? WHERE SettingKey = 'LastOrderNumber'",
                    listOf(next.toString())
                )
                val prefix = db.querySync(conn,
                    "SELECT SettingValue FROM AppSettings WHERE SettingKey = 'OrderNumberPrefix'"
                ) { it.getString("SettingValue") }.firstOrNull()?.takeIf { it.isNotBlank() } ?: "ORD"
                "$prefix-${next.toString().padStart(5, '0')}"
            }
        } catch (_: Exception) {
            "ORD-${java.text.SimpleDateFormat("yyMMddHHmm", java.util.Locale.US).format(java.util.Date())}"
        }
    }

    private fun mapOrder(rs: ResultSet) = Order(
        orderId        = rs.getInt("OrderId"),
        orderNo        = rs.getString("OrderNo") ?: "",
        tokenNo        = rs.getString("TokenNo") ?: "",
        orderType      = rs.getString("OrderType") ?: "Takeaway",
        tableId        = rs.getInt("TableId").takeIf { it > 0 },
        tableName      = try { rs.getString("TableName") } catch (_: Exception) { null },
        waiterId       = rs.getInt("WaiterId").takeIf { it > 0 },
        waiterName     = try { rs.getString("WaiterName") } catch (_: Exception) { null },
        customerId     = rs.getInt("CustomerId").takeIf { it > 0 },
        customerName   = try { rs.getString("CustomerName") } catch (_: Exception) { null },
        shiftId        = rs.getInt("ShiftId").takeIf { it > 0 },
        subTotal       = rs.getDouble("SubTotal"),
        discountAmount = rs.getDouble("DiscountAmount"),
        discountPercent= rs.getDouble("DiscountPercent"),
        taxAmount      = rs.getDouble("TaxAmount"),
        taxPercent     = rs.getDouble("TaxPercent"),
        serviceCharges = rs.getDouble("ServiceCharges"),
        deliveryCharge = rs.getDouble("DeliveryCharge"),
        tips           = try { rs.getDouble("Tips") } catch (_: Exception) { 0.0 },
        grandTotal     = rs.getDouble("GrandTotal"),
        paidAmount     = rs.getDouble("PaidAmount"),
        balanceAmount  = rs.getDouble("BalanceAmount"),
        orderStatus    = rs.getString("OrderStatus") ?: "New",
        paymentStatus  = rs.getString("PaymentStatus") ?: "Unpaid",
        notes          = rs.getString("Notes"),
        createdBy      = rs.getInt("CreatedBy").takeIf { it > 0 },
        createdAt      = rs.getTimestamp("CreatedAt") ?: Date(),
        itemCount      = try { rs.getInt("ItemCount") } catch (_: Exception) { 0 },
        deliveryName        = try { rs.getString("DeliveryName") } catch (_: Exception) { null },
        deliveryPhone       = try { rs.getString("DeliveryPhone") } catch (_: Exception) { null },
        deliveryAddress     = try { rs.getString("DeliveryAddress") } catch (_: Exception) { null },
        deliveryCompanyId   = try { rs.getInt("DeliveryCompanyId").takeIf { !rs.wasNull() && it > 0 } } catch (_: Exception) { null },
        deliveryCompanyName = try { rs.getString("DeliveryCompanyName")?.takeIf { it.isNotBlank() } } catch (_: Exception) { null },
        commissionAmount    = try { rs.getDouble("CommissionAmount") } catch (_: Exception) { 0.0 }
    )
}
