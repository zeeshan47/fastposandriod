package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.KitchenTicket
import com.fastpos.android.data.models.KitchenTicketItem
import com.fastpos.android.utils.SessionManager
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

// Actual DB columns:
//   KitchenTickets   → TicketId, OrderId, TicketNo, PrintedAt, PrintedBy, TicketStatus
//   KitchenTicketItems → TicketItemId, TicketId, OrderItemId, Quantity, ItemName, Notes, ItemStatus, CompletedAt

@Singleton
class KitchenRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val session: SessionManager
) {

    suspend fun getActiveTickets(): List<KitchenTicket> {
        val branchId = session.currentBranchId.value
        // ── Android-placed orders (have KitchenTickets rows) ──────────────────
        val tickets = try {
            db.query(
                """SELECT kt.TicketId, kt.OrderId, kt.TicketStatus, kt.PrintedAt,
                          o.OrderNo, o.TokenNo, o.OrderType,
                          ISNULL(t.TableName,'') AS TableName
                   FROM KitchenTickets kt
                   JOIN Orders o ON kt.OrderId = o.OrderId
                   LEFT JOIN Tables t ON o.TableId = t.TableId
                   WHERE kt.TicketStatus = 'Pending'
                     AND o.OrderStatus != 'Cancelled'
                     AND ISNULL(o.BranchId,1) = ?
                   ORDER BY kt.PrintedAt""",
                listOf(branchId)
            ) { rs ->
                KitchenTicket(
                    ticketId  = rs.getInt("TicketId"),
                    orderId   = rs.getInt("OrderId"),
                    orderNo   = rs.getString("OrderNo") ?: "",
                    tokenNo   = rs.getString("TokenNo") ?: "",
                    orderType = rs.getString("OrderType") ?: "Takeaway",
                    tableName = rs.getString("TableName").ifEmpty { null },
                    status    = rs.getString("TicketStatus") ?: "Pending",
                    createdAt = rs.getTimestamp("PrintedAt") ?: Date()
                )
            }
        } catch (_: Exception) { emptyList() }

        val androidTickets = tickets.map { ticket ->
            ticket.copy(items = getTicketItems(ticket.ticketId))
        }

        // ── WPF-placed orders (no KitchenTickets row; ticketId = -orderId) ────
        // WPF sets OrderStatus='SentToKitchen' and OrderItems.KitchenStatus='Cooking'
        // but does NOT insert into KitchenTickets. Surface those orders here.
        val wpfTickets = try {
            val wpfOrders = db.query(
                """SELECT o.OrderId, o.OrderNo, o.TokenNo, o.OrderType,
                          ISNULL(t.TableName,'') AS TableName,
                          ISNULL(o.CreatedAt, GETDATE()) AS CreatedAt
                   FROM Orders o
                   LEFT JOIN Tables t ON o.TableId = t.TableId
                   WHERE o.OrderStatus != 'Cancelled'
                     AND ISNULL(o.BranchId,1) = ?
                     AND EXISTS (
                         SELECT 1 FROM OrderItems oi
                         WHERE oi.OrderId = o.OrderId
                           AND oi.KitchenStatus IN ('Pending','Cooking','Done')
                     )
                     AND NOT EXISTS (
                         SELECT 1 FROM KitchenTickets kt
                         WHERE kt.OrderId = o.OrderId
                     )
                   ORDER BY o.CreatedAt""",
                listOf(branchId)
            ) { rs ->
                KitchenTicket(
                    ticketId  = -(rs.getInt("OrderId")),
                    orderId   = rs.getInt("OrderId"),
                    orderNo   = rs.getString("OrderNo") ?: "",
                    tokenNo   = rs.getString("TokenNo") ?: "",
                    orderType = rs.getString("OrderType") ?: "Takeaway",
                    tableName = rs.getString("TableName").ifEmpty { null },
                    status    = "Pending",
                    createdAt = rs.getTimestamp("CreatedAt") ?: Date()
                )
            }
            wpfOrders.map { ticket ->
                ticket.copy(items = getItemsForOrderDirect(-ticket.ticketId))
            }
        } catch (_: Exception) { emptyList() }

        return androidTickets + wpfTickets
    }

    private suspend fun getItemsForOrderDirect(orderId: Int): List<KitchenTicketItem> = try {
        db.query(
            """SELECT oi.OrderItemId, oi.ProductNameSnapshot, oi.Quantity, oi.Notes, oi.KitchenStatus
               FROM OrderItems oi
               WHERE oi.OrderId = ?
                 AND oi.KitchenStatus IN ('Pending','Cooking','Done')
               ORDER BY oi.OrderItemId""",
            listOf(orderId)
        ) { rs ->
            val orderItemId   = rs.getInt("OrderItemId")
            val kitchenStatus = rs.getString("KitchenStatus") ?: "Pending"
            KitchenTicketItem(
                itemId      = -orderItemId,
                ticketId    = -orderId,
                orderItemId = orderItemId,
                productName = rs.getString("ProductNameSnapshot") ?: "",
                quantity    = rs.getDouble("Quantity"),
                notes       = rs.getString("Notes"),
                modifiers   = null,
                status      = if (kitchenStatus == "Done") "Completed" else "Pending",
                printerName = ""
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getTicketItems(ticketId: Int): List<KitchenTicketItem> = try {
        db.query(
            """SELECT kti.TicketItemId, kti.TicketId, kti.OrderItemId, kti.ItemName,
                      kti.Quantity, kti.Notes, kti.ItemStatus,
                      ISNULL(kp.PrinterName, '') AS PrinterName
               FROM KitchenTicketItems kti
               LEFT JOIN OrderItems oi ON kti.OrderItemId = oi.OrderItemId
               LEFT JOIN Products p ON oi.ProductId = p.ProductId
               LEFT JOIN KitchenPrinters kp ON p.KitchenPrinterId = kp.PrinterId
               WHERE kti.TicketId = ?
               ORDER BY kti.TicketItemId""",
            listOf(ticketId)
        ) { rs ->
            KitchenTicketItem(
                itemId      = rs.getInt("TicketItemId"),
                ticketId    = rs.getInt("TicketId"),
                orderItemId = rs.getInt("OrderItemId"),
                productName = rs.getString("ItemName") ?: "",
                quantity    = rs.getDouble("Quantity"),
                notes       = rs.getString("Notes"),
                modifiers   = null,
                status      = rs.getString("ItemStatus") ?: "Pending",
                printerName = rs.getString("PrinterName") ?: ""
            )
        }
    } catch (_: Exception) {
        // PrinterName column may not exist on older DB installs — retry without it
        db.query(
            """SELECT kti.TicketItemId, kti.TicketId, kti.OrderItemId, kti.ItemName,
                      kti.Quantity, kti.Notes, kti.ItemStatus
               FROM KitchenTicketItems kti
               WHERE kti.TicketId = ?
               ORDER BY kti.TicketItemId""",
            listOf(ticketId)
        ) { rs ->
            KitchenTicketItem(
                itemId      = rs.getInt("TicketItemId"),
                ticketId    = rs.getInt("TicketId"),
                orderItemId = rs.getInt("OrderItemId"),
                productName = rs.getString("ItemName") ?: "",
                quantity    = rs.getDouble("Quantity"),
                notes       = rs.getString("Notes"),
                modifiers   = null,
                status      = rs.getString("ItemStatus") ?: "Pending",
                printerName = ""
            )
        }
    }

    suspend fun markItemReady(ticketItemId: Int) {
        if (ticketItemId < 0) {
            // WPF-sourced item: toggle Done↔Cooking so WPF and other Android devices see it.
            val orderItemId = -ticketItemId
            db.execute(
                """UPDATE OrderItems SET KitchenStatus = CASE WHEN KitchenStatus = 'Done' THEN 'Cooking' ELSE 'Done' END
                   WHERE OrderItemId = ?""",
                listOf(orderItemId)
            )
            return
        }
        // Android item: toggle Completed↔Pending, matching WPF undo behaviour.
        db.execute(
            """UPDATE KitchenTicketItems
               SET ItemStatus   = CASE WHEN ItemStatus = 'Completed' THEN 'Pending' ELSE 'Completed' END,
                   CompletedAt  = CASE WHEN ItemStatus = 'Completed' THEN NULL ELSE GETDATE() END
               WHERE TicketItemId = ?""",
            listOf(ticketItemId)
        )
    }

    suspend fun markTicketComplete(ticketId: Int) {
        if (ticketId < 0) {
            // WPF-sourced order: update OrderItems and Orders directly
            val orderId = -ticketId
            db.execute(
                "UPDATE OrderItems SET KitchenStatus = 'Completed' WHERE OrderId = ? AND KitchenStatus IN ('Pending','Cooking','Done')",
                listOf(orderId)
            )
            db.execute(
                "UPDATE Orders SET OrderStatus = 'Ready', UpdatedAt = GETDATE() WHERE OrderId = ? AND OrderStatus NOT IN ('Completed','Cancelled')",
                listOf(orderId)
            )
            return
        }
        db.execute(
            "UPDATE KitchenTickets SET TicketStatus = 'Completed' WHERE TicketId = ?",
            listOf(ticketId)
        )
        db.execute(
            "UPDATE KitchenTicketItems SET ItemStatus = 'Completed', CompletedAt = GETDATE() WHERE TicketId = ? AND ItemStatus = 'Pending'",
            listOf(ticketId)
        )
        db.execute(
            """UPDATE Orders SET OrderStatus = 'Ready', UpdatedAt = GETDATE()
               WHERE OrderId = (SELECT OrderId FROM KitchenTickets WHERE TicketId = ?)
               AND OrderStatus = 'SentToKitchen'""",
            listOf(ticketId)
        )
    }

    // ── Token Display ─────────────────────────────────────────────────────────

    suspend fun getInKitchenTickets(): List<KitchenTicket> = getActiveTickets()

    suspend fun getReadyOrders(): List<KitchenTicket> = db.query(
        """SELECT o.OrderId, o.OrderNo, o.TokenNo, o.OrderType,
                  ISNULL(t.TableName,'') AS TableName,
                  ISNULL(o.UpdatedAt, o.CreatedAt) AS PrintedAt,
                  (SELECT COUNT(*) FROM OrderItems WHERE OrderId = o.OrderId) AS ItemCount
           FROM Orders o
           LEFT JOIN Tables t ON o.TableId = t.TableId
           WHERE o.OrderStatus = 'Ready'
             AND o.PaymentStatus IN ('Unpaid','Partial')
             AND ISNULL(o.BranchId,1) = ?
           ORDER BY o.UpdatedAt""",
        listOf(session.currentBranchId.value)
    ) { rs ->
        KitchenTicket(
            ticketId  = rs.getInt("OrderId"),
            orderId   = rs.getInt("OrderId"),
            orderNo   = rs.getString("OrderNo") ?: "",
            tokenNo   = rs.getString("TokenNo") ?: "",
            orderType = rs.getString("OrderType") ?: "Takeaway",
            tableName = rs.getString("TableName").ifEmpty { null },
            status    = "Ready",
            createdAt = rs.getTimestamp("PrintedAt") ?: Date()
        )
    }

    suspend fun markOrderServed(orderId: Int) {
        db.execute(
            "UPDATE Orders SET OrderStatus = 'Completed', UpdatedAt = GETDATE() WHERE OrderId = ?",
            listOf(orderId)
        )
    }
}
