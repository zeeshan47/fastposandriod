package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.DashboardAlert
import com.fastpos.android.data.models.DashboardStats
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.RestaurantTable
import com.fastpos.android.data.models.TopProduct
import com.fastpos.android.utils.SessionManager
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val session: SessionManager
) {

    suspend fun getDashboardStats(currencySymbol: String = "Rs."): DashboardStats {
        val branchId = session.currentBranchId.value
        val shiftId  = session.shiftId

        // 1. Shift-based stats when shift is open; otherwise fall back to today
        val shiftFilter  = if (shiftId != null) "ShiftId = ? AND ISNULL(BranchId,1) = ?" to listOf<Any?>(shiftId, branchId)
                          else "CAST(OrderDate AS DATE) = CAST(GETDATE() AS DATE) AND ISNULL(BranchId,1) = ?" to listOf<Any?>(branchId)
        val todayStats = try {
            db.queryOne(
                """SELECT
                    ISNULL(SUM(GrandTotal), 0) AS TodaySales,
                    COUNT(*) AS TotalOrders,
                    COUNT(DISTINCT NULLIF(CustomerId, 0)) AS TodayCustomers,
                    COUNT(CASE WHEN OrderType = 'DineIn' THEN 1 END) AS DineInOrders,
                    COUNT(CASE WHEN OrderType = 'Takeaway' THEN 1 END) AS TakeawayOrders,
                    COUNT(CASE WHEN OrderType = 'Delivery' THEN 1 END) AS DeliveryOrders,
                    COUNT(CASE WHEN OrderStatus = 'Completed' THEN 1 END) AS CompletedOrders
                   FROM Orders
                   WHERE ${shiftFilter.first}
                     AND OrderStatus != 'Cancelled'""",
                shiftFilter.second
            ) { rs ->
                DashboardStats(
                    todaySales      = rs.getDouble("TodaySales"),
                    todayOrders     = rs.getInt("TotalOrders"),
                    todayCustomers  = rs.getInt("TodayCustomers"),
                    dineInOrders    = rs.getInt("DineInOrders"),
                    takeawayOrders  = rs.getInt("TakeawayOrders"),
                    deliveryOrders  = rs.getInt("DeliveryOrders"),
                    completedOrders = rs.getInt("CompletedOrders")
                )
            } ?: DashboardStats()
        } catch (_: Exception) { DashboardStats() }

        // 2. Pending orders — shift-based or today
        val pendingOrders = try {
            val (pendWhere, pendParams) = if (shiftId != null)
                "ShiftId = ? AND ISNULL(BranchId,1) = ?" to listOf<Any?>(shiftId, branchId)
            else
                "CAST(OrderDate AS DATE) = CAST(GETDATE() AS DATE) AND ISNULL(BranchId,1) = ?" to listOf<Any?>(branchId)
            db.queryOne(
                "SELECT COUNT(*) AS Cnt FROM Orders WHERE OrderStatus IN ('New','Held','SentToKitchen') AND $pendWhere",
                pendParams
            ) { it.getInt("Cnt") } ?: 0
        } catch (_: Exception) { 0 }

        // 3. Recent orders — shift-based or today
        val recentOrders = try {
            val (recWhere, recParams) = if (shiftId != null)
                "o.ShiftId = ? AND ISNULL(o.BranchId,1) = ?" to listOf<Any?>(shiftId, branchId)
            else
                "CAST(o.OrderDate AS DATE) = CAST(GETDATE() AS DATE) AND ISNULL(o.BranchId,1) = ?" to listOf<Any?>(branchId)
            db.query(
                """SELECT TOP 10 o.OrderId, o.OrderNo, o.TokenNo, o.OrderType, o.OrderDate,
                          o.GrandTotal, o.OrderStatus, o.PaymentStatus,
                          o.CreatedAt, ISNULL(t.TableName,'') AS TableName, 0 AS ItemCount
                   FROM Orders o
                   LEFT JOIN Tables t ON o.TableId = t.TableId
                   WHERE $recWhere
                   ORDER BY o.CreatedAt DESC""",
                recParams
            ) { rs ->
                Order(
                    orderId       = rs.getInt("OrderId"),
                    orderNo       = rs.getString("OrderNo") ?: "",
                    tokenNo       = rs.getString("TokenNo") ?: "",
                    orderType     = rs.getString("OrderType") ?: "",
                    tableName     = rs.getString("TableName"),
                    grandTotal    = rs.getDouble("GrandTotal"),
                    orderStatus   = rs.getString("OrderStatus") ?: "",
                    paymentStatus = rs.getString("PaymentStatus") ?: "",
                    createdAt     = rs.getTimestamp("CreatedAt") ?: Date()
                )
            }
        } catch (_: Exception) { emptyList() }

        // 5. Cash in drawer — op.Amount (correct column); try with PaymentMethod filter, fallback without
        val cashInDrawer = if (shiftId != null) {
            try {
                db.queryOne(
                    """SELECT ISNULL(s.OpeningCash, 0)
                        + ISNULL((SELECT SUM(op.Amount) FROM OrderPayments op
                                  INNER JOIN Orders o ON op.OrderId = o.OrderId
                                  WHERE o.ShiftId = ? AND op.PaymentMethod = 'Cash'
                                    AND o.OrderStatus != 'Cancelled'), 0)
                        + ISNULL((SELECT SUM(Amount) FROM CashTransactions
                                  WHERE ShiftId = ? AND TransactionType = 'In'), 0)
                        - ISNULL((SELECT SUM(Amount) FROM Expenses
                                  WHERE ShiftId = ? AND IsActive = 1
                                    AND ISNULL(PaymentMethod,'Cash') = 'Cash'), 0)
                        - ISNULL((SELECT SUM(Amount) FROM CashTransactions
                                  WHERE ShiftId = ? AND TransactionType = 'Out'), 0)
                        AS CashInDrawer
                       FROM Shifts s WHERE s.ShiftId = ?""",
                    listOf(shiftId, shiftId, shiftId, shiftId, shiftId)
                ) { it.getDouble("CashInDrawer") } ?: 0.0
            } catch (_: Exception) {
                // Fallback: pre-migration (Expenses without PaymentMethod column)
                try {
                    db.queryOne(
                        """SELECT ISNULL(s.OpeningCash, 0)
                            + ISNULL((SELECT SUM(op.Amount) FROM OrderPayments op
                                      INNER JOIN Orders o ON op.OrderId = o.OrderId
                                      WHERE o.ShiftId = ? AND op.PaymentMethod = 'Cash'
                                        AND o.OrderStatus != 'Cancelled'), 0)
                            + ISNULL((SELECT SUM(Amount) FROM CashTransactions
                                      WHERE ShiftId = ? AND TransactionType = 'In'), 0)
                            - ISNULL((SELECT SUM(Amount) FROM Expenses
                                      WHERE ShiftId = ? AND IsActive = 1), 0)
                            - ISNULL((SELECT SUM(Amount) FROM CashTransactions
                                      WHERE ShiftId = ? AND TransactionType = 'Out'), 0)
                            AS CashInDrawer
                           FROM Shifts s WHERE s.ShiftId = ?""",
                        listOf(shiftId, shiftId, shiftId, shiftId, shiftId)
                    ) { it.getDouble("CashInDrawer") } ?: 0.0
                } catch (_: Exception) { 0.0 }
            }
        } else 0.0

        // 6. Purchases today — InvoiceDate (matches WPF)
        val todayPurchases = try {
            db.queryOne(
                """SELECT ISNULL(SUM(GrandTotal), 0) AS TodayPurchases
                   FROM PurchaseInvoices
                   WHERE IsActive = 1
                     AND CAST(InvoiceDate AS DATE) = CAST(GETDATE() AS DATE)
                     AND ISNULL(BranchId, 1) = ?""",
                listOf(branchId)
            ) { it.getDouble("TodayPurchases") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        // 7. Expenses today
        val todayExpenses = try {
            db.queryOne(
                """SELECT ISNULL(SUM(Amount), 0) AS TotalExp
                   FROM Expenses WHERE IsActive = 1
                     AND CAST(ExpenseDate AS DATE) = CAST(GETDATE() AS DATE)""",
                emptyList()
            ) { it.getDouble("TotalExp") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        // 8. Low stock — ReorderLevel > 0 guard + RawMaterial type fallback (matches WPF)
        val lowStock = try {
            db.queryOne(
                """SELECT COUNT(*) AS Cnt FROM Products
                   WHERE ISNULL(IsActive,1) = 1
                     AND ReorderLevel > 0
                     AND ISNULL(CurrentStock,0) <= ReorderLevel
                     AND (ISNULL(IsStockManaged,0) = 1 OR ProductType = 'RawMaterial')""",
                emptyList()
            ) { it.getInt("Cnt") } ?: 0
        } catch (_: Exception) { 0 }

        // 9. Yesterday sales — OrderStatus != 'Cancelled' (matches WPF)
        val yesterday = try {
            db.queryOne(
                """SELECT ISNULL(SUM(GrandTotal),0) AS YSales FROM Orders
                   WHERE CAST(OrderDate AS DATE) = CAST(DATEADD(DAY,-1,GETDATE()) AS DATE)
                     AND OrderStatus != 'Cancelled'
                     AND ISNULL(BranchId,1) = ?""",
                listOf(branchId)
            ) { it.getDouble("YSales") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        // 10. Supplier payables — BalanceAmount column directly (matches WPF)
        val supplierPayables = try {
            db.queryOne(
                """SELECT ISNULL(SUM(pi.BalanceAmount), 0) AS TotalPayables
                   FROM PurchaseInvoices pi
                   INNER JOIN Suppliers s ON s.SupplierId = pi.SupplierId AND s.IsActive = 1
                   WHERE pi.IsActive = 1 AND pi.BalanceAmount > 0""",
                emptyList()
            ) { it.getDouble("TotalPayables") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        // 11. Top products — shift-based when shift open, else today
        val topProducts = try {
            val (topWhere, topParams) = if (shiftId != null)
                "o.ShiftId = ? AND ISNULL(o.BranchId, 1) = ?" to listOf<Any?>(shiftId, branchId)
            else
                "CAST(o.OrderDate AS DATE) = CAST(GETDATE() AS DATE) AND ISNULL(o.BranchId, 1) = ?" to listOf<Any?>(branchId)
            db.query(
                """SELECT TOP 5
                       oi.ProductNameSnapshot AS ProductName,
                       SUM(oi.Quantity) AS TotalQty,
                       ISNULL(SUM(oi.LineTotal), 0) AS TotalRevenue
                   FROM OrderItems oi
                   INNER JOIN Orders o ON oi.OrderId = o.OrderId
                   WHERE o.OrderStatus != 'Cancelled'
                     AND $topWhere
                   GROUP BY oi.ProductNameSnapshot
                   ORDER BY SUM(oi.Quantity) DESC""",
                topParams
            ) { rs ->
                TopProduct(
                    productName = rs.getString("ProductName") ?: "",
                    quantity    = rs.getDouble("TotalQty"),
                    revenue     = rs.getDouble("TotalRevenue")
                )
            }
        } catch (_: Exception) { emptyList() }

        // 12. Table status
        val tables = try {
            db.query(
                """SELECT TableId, TableName, AreaId, TableStatus, IsActive
                   FROM Tables
                   WHERE IsActive = 1
                   ORDER BY AreaId, TableName"""
            ) { rs ->
                RestaurantTable(
                    tableId     = rs.getInt("TableId"),
                    tableName   = rs.getString("TableName") ?: "",
                    areaId      = rs.getInt("AreaId").takeIf { !rs.wasNull() },
                    tableStatus = rs.getString("TableStatus") ?: "Available",
                    isActive    = rs.getBoolean("IsActive")
                )
            }
        } catch (_: Exception) { emptyList() }
        val tableCounts = Pair(
            tables.size,
            tables.count { it.tableStatus.equals("Occupied", ignoreCase = true) }
        )

        // 13. Today's reservations
        val todayRes = try {
            db.queryOne(
                """SELECT COUNT(*) AS Cnt FROM Reservations
                   WHERE Status IN ('Confirmed','Arrived')
                     AND CAST(ReservationDate AS DATE) = CAST(GETDATE() AS DATE)""",
                emptyList()
            ) { it.getInt("Cnt") } ?: 0
        } catch (_: Exception) { 0 }

        val alerts          = getAlerts(lowStock, currencySymbol)
        val weeklyRevenue   = getWeeklyRevenue(branchId)
        val weeklyPurchases = getWeeklyPurchases(branchId)
        val weeklyExpenses  = getWeeklyExpenses(branchId)

        return todayStats.copy(
            shiftSales         = 0.0,
            pendingOrders      = pendingOrders,
            lowStockCount      = lowStock,
            yesterdaySales     = yesterday,
            todayReservations  = todayRes,
            todayExpenses      = todayExpenses,
            recentOrders       = recentOrders,
            alerts             = alerts,
            totalTables        = tableCounts.first,
            occupiedTables     = tableCounts.second,
            tables             = tables,
            weeklyRevenue      = weeklyRevenue,
            cashInDrawer       = cashInDrawer,
            todayPurchases     = todayPurchases,
            supplierPayables   = supplierPayables,
            topProducts        = topProducts,
            weeklyPurchases    = weeklyPurchases,
            weeklyExpenses     = weeklyExpenses
        )
    }

    private suspend fun getWeeklyRevenue(branchId: Int): List<Pair<String, Double>> {
        return try {
            val dayLabels = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
            val rawRows = db.query(
                """SELECT CAST(OrderDate AS DATE) AS SaleDate,
                          ISNULL(SUM(CASE WHEN OrderStatus='Completed' THEN GrandTotal ELSE 0 END),0) AS Revenue
                   FROM Orders
                   WHERE CAST(OrderDate AS DATE) >= CAST(DATEADD(DAY,-6,GETDATE()) AS DATE)
                     AND ISNULL(BranchId,1) = ?
                   GROUP BY CAST(OrderDate AS DATE)
                   ORDER BY SaleDate""",
                listOf(branchId)
            ) { rs ->
                val d = rs.getDate("SaleDate") ?: java.sql.Date(System.currentTimeMillis())
                val cal = java.util.Calendar.getInstance().also { it.time = d }
                Pair(dayLabels[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1], rs.getDouble("Revenue"))
            }
            // Pad to 7 entries using last 7 days in order
            val cal = java.util.Calendar.getInstance()
            val result = mutableListOf<Pair<String, Double>>()
            val rawMap = rawRows.toMap()
            for (i in 6 downTo 0) {
                val c = java.util.Calendar.getInstance().also { it.time = cal.time; it.add(java.util.Calendar.DAY_OF_YEAR, -i) }
                val label = dayLabels[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                result.add(Pair(label, rawMap[label] ?: 0.0))
            }
            result
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun getWeeklyPurchases(branchId: Int): List<Pair<String, Double>> {
        return try {
            val dayLabels = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
            val rawRows = db.query(
                """SELECT CAST(InvoiceDate AS DATE) AS PurchaseDate,
                          ISNULL(SUM(GrandTotal), 0) AS Amount
                   FROM PurchaseInvoices
                   WHERE IsActive = 1
                     AND CAST(InvoiceDate AS DATE) >= CAST(DATEADD(DAY,-6,GETDATE()) AS DATE)
                     AND ISNULL(BranchId,1) = ?
                   GROUP BY CAST(InvoiceDate AS DATE)
                   ORDER BY PurchaseDate""",
                listOf(branchId)
            ) { rs ->
                val d = rs.getDate("PurchaseDate") ?: java.sql.Date(System.currentTimeMillis())
                val cal = java.util.Calendar.getInstance().also { it.time = d }
                Pair(dayLabels[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1], rs.getDouble("Amount"))
            }
            val cal = java.util.Calendar.getInstance()
            val result = mutableListOf<Pair<String, Double>>()
            val rawMap = rawRows.toMap()
            for (i in 6 downTo 0) {
                val c = java.util.Calendar.getInstance().also { it.time = cal.time; it.add(java.util.Calendar.DAY_OF_YEAR, -i) }
                val label = dayLabels[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                result.add(Pair(label, rawMap[label] ?: 0.0))
            }
            result
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun getWeeklyExpenses(branchId: Int): List<Pair<String, Double>> {
        return try {
            val dayLabels = arrayOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
            val rawRows = db.query(
                """SELECT CAST(ExpenseDate AS DATE) AS ExpDate,
                          ISNULL(SUM(Amount), 0) AS Amount
                   FROM Expenses
                   WHERE IsActive = 1
                     AND CAST(ExpenseDate AS DATE) >= CAST(DATEADD(DAY,-6,GETDATE()) AS DATE)
                     AND ISNULL(BranchId,1) = ?
                   GROUP BY CAST(ExpenseDate AS DATE)
                   ORDER BY ExpDate""",
                listOf(branchId)
            ) { rs ->
                val d = rs.getDate("ExpDate") ?: java.sql.Date(System.currentTimeMillis())
                val cal = java.util.Calendar.getInstance().also { it.time = d }
                Pair(dayLabels[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1], rs.getDouble("Amount"))
            }
            val cal = java.util.Calendar.getInstance()
            val result = mutableListOf<Pair<String, Double>>()
            val rawMap = rawRows.toMap()
            for (i in 6 downTo 0) {
                val c = java.util.Calendar.getInstance().also { it.time = cal.time; it.add(java.util.Calendar.DAY_OF_YEAR, -i) }
                val label = dayLabels[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                result.add(Pair(label, rawMap[label] ?: 0.0))
            }
            result
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun getAlerts(lowStockCount: Int, sym: String = "Rs."): List<DashboardAlert> {
        val alerts = mutableListOf<DashboardAlert>()

        // Low stock items list
        if (lowStockCount > 0) {
            val names = try {
                db.query(
                    """SELECT TOP 3 ProductName FROM Products
                       WHERE ISNULL(IsStockManaged,0)=1 AND ISNULL(IsActive,1)=1
                         AND ISNULL(CurrentStock,0) <= ISNULL(ReorderLevel,0)
                       ORDER BY (ISNULL(CurrentStock,0) - ISNULL(ReorderLevel,0)) ASC""",
                    emptyList()
                ) { it.getString("ProductName") ?: "" }
            } catch (_: Exception) { emptyList() }

            alerts.add(DashboardAlert(
                type        = "LOW_STOCK",
                severity    = "Error",
                title       = "$lowStockCount item${if (lowStockCount == 1) "" else "s"} running low on stock",
                message     = names.joinToString(" • ").ifBlank { "Check raw materials" },
                actionLabel = "View Stock",
                actionRoute = "rawmaterials"
            ))
        }

        // Upcoming reservations (today only)
        try {
            val upcoming = db.queryOne(
                """SELECT COUNT(*) AS Cnt FROM Reservations
                   WHERE Status='Confirmed'
                     AND CAST(ReservationDate AS DATE) = CAST(GETDATE() AS DATE)""",
                emptyList()
            ) { it.getInt("Cnt") } ?: 0

            if (upcoming > 0) {
                alerts.add(DashboardAlert(
                    type        = "RESERVATION",
                    severity    = "Warning",
                    title       = "$upcoming reservation${if (upcoming == 1) "" else "s"} today",
                    message     = "Check table availability and preparation",
                    actionLabel = "View",
                    actionRoute = "reservations"
                ))
            }
        } catch (_: Exception) {}

        // Vouchers expiring within 7 days
        try {
            val expiring = db.queryOne(
                """SELECT COUNT(*) AS Cnt FROM Vouchers
                   WHERE IsActive=1 AND ExpiryDate IS NOT NULL
                     AND ExpiryDate BETWEEN CAST(GETDATE() AS DATE)
                     AND CAST(DATEADD(DAY,7,GETDATE()) AS DATE)""",
                emptyList()
            ) { it.getInt("Cnt") } ?: 0

            if (expiring > 0) {
                alerts.add(DashboardAlert(
                    type        = "VOUCHER",
                    severity    = "Info",
                    title       = "$expiring voucher${if (expiring == 1) "" else "s"} expiring within 7 days",
                    message     = "Consider extending or creating new promotions",
                    actionLabel = "Manage",
                    actionRoute = "vouchers"
                ))
            }
        } catch (_: Exception) {}

        // Supplier outstanding balances
        try {
            val supplierData = db.queryOne(
                """SELECT COUNT(*) AS Cnt,
                          ISNULL(SUM(ISNULL(pi.InvoiceTotal,0) - ISNULL(ip.InvPaid,0) - ISNULL(dp.PmtPaid,0)), 0) AS TotalOwed
                   FROM Suppliers s
                   LEFT JOIN (SELECT SupplierId, SUM(GrandTotal)  AS InvoiceTotal FROM PurchaseInvoices  GROUP BY SupplierId) pi ON pi.SupplierId = s.SupplierId
                   LEFT JOIN (SELECT SupplierId, SUM(PaidAmount)  AS InvPaid      FROM PurchaseInvoices  GROUP BY SupplierId) ip ON ip.SupplierId = s.SupplierId
                   LEFT JOIN (SELECT SupplierId, SUM(Amount)      AS PmtPaid      FROM SupplierPayments  GROUP BY SupplierId) dp ON dp.SupplierId = s.SupplierId
                   WHERE s.IsActive = 1
                     AND (ISNULL(pi.InvoiceTotal,0) - ISNULL(ip.InvPaid,0) - ISNULL(dp.PmtPaid,0)) > 0""",
                emptyList()
            ) { rs -> Pair(rs.getInt("Cnt"), rs.getDouble("TotalOwed")) }

            if (supplierData != null && supplierData.first > 0) {
                alerts.add(DashboardAlert(
                    type        = "SUPPLIER",
                    severity    = "Warning",
                    title       = "${supplierData.first} supplier${if (supplierData.first == 1) "" else "s"} with outstanding balance",
                    message     = "Total owed: $sym${"%,.0f".format(supplierData.second)}",
                    actionLabel = "View",
                    actionRoute = "purchases"
                ))
            }
        } catch (_: Exception) {}

        // Pending delivery settlements (companies with balance > 0)
        try {
            val pendingDeliveries = db.queryOne(
                """SELECT COUNT(DISTINCT dc.CompanyId) AS Cnt
                   FROM DeliveryCompanies dc
                   WHERE dc.IsActive = 1
                     AND (
                       SELECT ISNULL(SUM(o.GrandTotal),0) FROM Orders o
                       WHERE o.DeliveryCompanyId = dc.CompanyId AND o.OrderStatus='Completed'
                     ) > (
                       SELECT ISNULL(SUM(s.AmountReceived),0) FROM DeliverySettlements s
                       WHERE s.DeliveryCompanyId = dc.CompanyId
                     )""",
                emptyList()
            ) { it.getInt("Cnt") } ?: 0

            if (pendingDeliveries > 0) {
                alerts.add(DashboardAlert(
                    type        = "DELIVERY",
                    severity    = "Warning",
                    title       = "$pendingDeliveries delivery company${if (pendingDeliveries == 1) "" else " companies"} with outstanding balance",
                    message     = "Record settlements to keep accounts up to date",
                    actionLabel = "Settle",
                    actionRoute = "delivery"
                ))
            }
        } catch (_: Exception) {}

        return alerts
    }
}
