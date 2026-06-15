package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.OrderItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FbrRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun queueOrderForFbr(orderId: Int) = db.execute(
        "UPDATE Orders SET FbrStatus = 'Pending' WHERE OrderId = ? AND (FbrStatus IS NULL OR FbrStatus = '')",
        listOf(orderId)
    )

    suspend fun getPendingFbrOrders(): List<Order> = db.query(
        """SELECT OrderId, OrderNo, OrderDate, CustomerName,
                  DeliveryName, DeliveryPhone, SubTotal, DiscountAmount,
                  TaxAmount, TaxPercent, GrandTotal, OrderType
           FROM Orders
           WHERE FbrStatus IN ('Pending','Failed') AND PaymentStatus = 'Paid'
           ORDER BY OrderId"""
    ) { rs ->
        Order(
            orderId        = rs.getInt("OrderId"),
            orderNo        = rs.getString("OrderNo") ?: "",
            orderDate      = rs.getDate("OrderDate") ?: java.util.Date(),
            customerName   = rs.getString("CustomerName"),
            deliveryName   = rs.getString("DeliveryName"),
            deliveryPhone  = rs.getString("DeliveryPhone"),
            subTotal       = rs.getDouble("SubTotal"),
            discountAmount = rs.getDouble("DiscountAmount"),
            taxAmount      = rs.getDouble("TaxAmount"),
            taxPercent     = rs.getDouble("TaxPercent"),
            grandTotal     = rs.getDouble("GrandTotal"),
            orderType      = rs.getString("OrderType") ?: "Takeaway"
        )
    }

    suspend fun getOrderItemsForFbr(orderId: Int): List<OrderItem> = db.query(
        """SELECT OrderItemId, OrderId, ProductId,
                  ProductNameSnapshot, Quantity, UnitPrice,
                  ISNULL(DiscountAmount,0) AS DiscountAmount, LineTotal
           FROM OrderItems WHERE OrderId = ?""",
        listOf(orderId)
    ) { rs ->
        OrderItem(
            orderItemId         = rs.getInt("OrderItemId"),
            orderId             = rs.getInt("OrderId"),
            productId           = rs.getInt("ProductId"),
            productNameSnapshot = rs.getString("ProductNameSnapshot") ?: "",
            quantity            = rs.getDouble("Quantity"),
            unitPrice           = rs.getDouble("UnitPrice"),
            discountAmount      = rs.getDouble("DiscountAmount"),
            lineTotal           = rs.getDouble("LineTotal")
        )
    }

    suspend fun updateFbrStatus(orderId: Int, status: String, invoiceNo: String? = null) = db.execute(
        "UPDATE Orders SET FbrStatus = ?, FbrInvoiceNo = ? WHERE OrderId = ?",
        listOf(status, invoiceNo, orderId)
    )

    suspend fun getFbrInvoiceNo(orderId: Int): String? = try {
        db.queryOne(
            "SELECT FbrInvoiceNo FROM Orders WHERE OrderId = ?",
            listOf(orderId)
        ) { rs -> rs.getString("FbrInvoiceNo").takeIf { !it.isNullOrBlank() } }
    } catch (_: Exception) { null }
}
