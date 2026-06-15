package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.AttendanceMonthSummary
import com.fastpos.android.data.models.DailyAttendanceRow
import com.fastpos.android.data.models.CustomerSalesSummary
import com.fastpos.android.data.models.SupplierPurchaseSummary
import com.fastpos.android.data.models.DailySalesDetail
import com.fastpos.android.data.models.DeliveryReportRow
import com.fastpos.android.data.models.InventoryItem
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.PaymentMethodSummary
import com.fastpos.android.data.models.PayrollRecord
import com.fastpos.android.data.models.ProductSaleSummary
import com.fastpos.android.data.models.ProfitLoss
import com.fastpos.android.data.models.SalesSummary
import com.fastpos.android.data.models.ShiftSummaryReport
import com.fastpos.android.data.models.StockItem
import com.fastpos.android.data.models.UserSalesSummary
import com.fastpos.android.data.models.WaiterSalesSummary
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun getSalesSummary(fromDate: Date, toDate: Date, branchId: Int? = null): SalesSummary =
        db.queryOne(
            buildString {
                append("""SELECT
                ISNULL(SUM(CASE WHEN OrderStatus='Completed' THEN GrandTotal ELSE 0 END),0) AS TotalSales,
                COUNT(*) AS TotalOrders,
                ISNULL(AVG(CASE WHEN OrderStatus='Completed' THEN GrandTotal END),0) AS AvgOrder,
                ISNULL(SUM(CASE WHEN OrderStatus='Completed' THEN DiscountAmount ELSE 0 END),0) AS TotalDiscount,
                ISNULL(SUM(CASE WHEN OrderStatus='Completed' THEN TaxAmount ELSE 0 END),0) AS TotalTax,
                SUM(CASE WHEN OrderStatus='Completed' THEN 1 ELSE 0 END) AS CompletedOrders,
                SUM(CASE WHEN OrderStatus='Cancelled' THEN 1 ELSE 0 END) AS CancelledOrders,
                SUM(CASE WHEN OrderStatus='Void'      THEN 1 ELSE 0 END) AS VoidedOrders,
                SUM(CASE WHEN OrderStatus='Refunded'  THEN 1 ELSE 0 END) AS RefundedOrders
               FROM Orders
               WHERE OrderDate >= ? AND OrderDate <= ?""")
                if (branchId != null) append(" AND BranchId = ?")
            },
            buildList { add(fromDate); add(toDate); if (branchId != null) add(branchId) }
        ) { rs ->
            SalesSummary(
                totalSales      = rs.getDouble("TotalSales"),
                totalOrders     = rs.getInt("TotalOrders"),
                avgOrderValue   = rs.getDouble("AvgOrder"),
                totalDiscount   = rs.getDouble("TotalDiscount"),
                totalTax        = rs.getDouble("TotalTax"),
                completedOrders = rs.getInt("CompletedOrders"),
                cancelledOrders = rs.getInt("CancelledOrders"),
                voidedOrders    = rs.getInt("VoidedOrders"),
                refundedOrders  = rs.getInt("RefundedOrders")
            )
        } ?: SalesSummary()

    suspend fun getSalesByPaymentMethod(fromDate: Date, toDate: Date, branchId: Int? = null): List<PaymentMethodSummary> {
        val sql = buildString {
            append("""SELECT op.PaymentMethod, SUM(op.Amount) AS Amount, COUNT(*) AS Cnt
               FROM OrderPayments op
               JOIN Orders o ON op.OrderId = o.OrderId
               WHERE o.OrderStatus = 'Completed'
                 AND o.OrderDate >= ? AND o.OrderDate <= ?""")
            if (branchId != null) append(" AND o.BranchId = ?")
            append(" GROUP BY op.PaymentMethod ORDER BY Amount DESC")
        }
        val params = mutableListOf<Any?>(fromDate, toDate)
        if (branchId != null) params.add(branchId)
        return db.query(sql, params) { rs ->
            PaymentMethodSummary(
                method = rs.getString("PaymentMethod") ?: "",
                amount = rs.getDouble("Amount"),
                count  = rs.getInt("Cnt")
            )
        }
    }

    suspend fun getTopProducts(fromDate: Date, toDate: Date, limit: Int = 10, branchId: Int? = null): List<ProductSaleSummary> {
        val sql = buildString {
            append("""SELECT TOP ($limit) oi.ProductId,
                      oi.ProductNameSnapshot AS ProductName,
                      SUM(oi.Quantity) AS TotalQty,
                      SUM(oi.LineTotal) AS Revenue,
                      SUM(oi.Quantity * ISNULL(p.PurchasePrice, 0)) AS TotalCOGS
               FROM OrderItems oi
               JOIN Orders o ON oi.OrderId = o.OrderId
               LEFT JOIN Products p ON oi.ProductId = p.ProductId
               WHERE o.OrderDate >= ? AND o.OrderDate <= ?
               AND o.OrderStatus = 'Completed'""")
            if (branchId != null) append(" AND o.BranchId = ?")
            append(" GROUP BY oi.ProductId, oi.ProductNameSnapshot ORDER BY Revenue DESC")
        }
        val params = mutableListOf<Any?>(fromDate, toDate)
        if (branchId != null) params.add(branchId)
        return db.query(sql, params) { rs ->
            ProductSaleSummary(
                productId   = rs.getInt("ProductId"),
                productName = rs.getString("ProductName") ?: "",
                quantity    = rs.getDouble("TotalQty"),
                revenue     = rs.getDouble("Revenue"),
                costOfGoods = try { rs.getDouble("TotalCOGS") } catch (_: Exception) { 0.0 }
            )
        }
    }

    suspend fun getSalesByOrderType(fromDate: Date, toDate: Date, branchId: Int? = null): List<Pair<String, Double>> {
        val sql = buildString {
            append("""SELECT OrderType, ISNULL(SUM(GrandTotal),0) AS Total
               FROM Orders
               WHERE OrderDate >= ? AND OrderDate <= ?
               AND OrderStatus = 'Completed'""")
            if (branchId != null) append(" AND BranchId = ?")
            append(" GROUP BY OrderType")
        }
        val params = buildList { add(fromDate); add(toDate); if (branchId != null) add(branchId) }
        return db.query(sql, params) { rs -> Pair(rs.getString("OrderType") ?: "", rs.getDouble("Total")) }
    }

    suspend fun getHourlySales(fromDate: Date, toDate: Date, branchId: Int? = null): List<Pair<Int, Double>> {
        val sql = buildString {
            append("""SELECT DATEPART(HOUR, OrderDate) AS Hr, ISNULL(SUM(GrandTotal),0) AS Total
               FROM Orders
               WHERE OrderStatus = 'Completed'
                 AND OrderDate >= ? AND OrderDate <= ?""")
            if (branchId != null) append(" AND BranchId = ?")
            append(" GROUP BY DATEPART(HOUR, OrderDate) ORDER BY Hr")
        }
        val params = buildList { add(fromDate); add(toDate); if (branchId != null) add(branchId) }
        return db.query(sql, params) { rs -> Pair(rs.getInt("Hr"), rs.getDouble("Total")) }
    }

    suspend fun getSalesByCategory(fromDate: Date, toDate: Date, branchId: Int? = null): List<Pair<String, Double>> {
        val sql = buildString {
            append("""SELECT c.CategoryName, ISNULL(SUM(oi.LineTotal),0) AS Total
               FROM OrderItems oi
               JOIN Orders o ON oi.OrderId = o.OrderId
               JOIN Products p ON oi.ProductId = p.ProductId
               JOIN Categories c ON p.CategoryId = c.CategoryId
               WHERE o.OrderDate >= ? AND o.OrderDate <= ?
                 AND o.OrderStatus = 'Completed'""")
            if (branchId != null) append(" AND o.BranchId = ?")
            append(" GROUP BY c.CategoryName ORDER BY Total DESC")
        }
        val params = buildList { add(fromDate); add(toDate); if (branchId != null) add(branchId) }
        return db.query(sql, params) { rs -> Pair(rs.getString("CategoryName") ?: "", rs.getDouble("Total")) }
    }

    suspend fun getSalesByWaiter(fromDate: Date, toDate: Date, branchId: Int? = null): List<WaiterSalesSummary> = try {
        val sql = buildString {
            append("""SELECT w.WaiterName, COUNT(o.OrderId) AS OrderCount, ISNULL(SUM(o.GrandTotal),0) AS Total,
                      CASE WHEN COUNT(o.OrderId) > 0 THEN ISNULL(SUM(o.GrandTotal),0)/COUNT(o.OrderId) ELSE 0 END AS AvgOrder
               FROM Orders o
               JOIN Waiters w ON o.WaiterId = w.WaiterId
               WHERE o.OrderDate >= ? AND o.OrderDate <= ?
                 AND o.OrderStatus = 'Completed'""")
            if (branchId != null) append(" AND o.BranchId = ?")
            append(" GROUP BY w.WaiterName ORDER BY Total DESC")
        }
        val params = buildList { add(fromDate); add(toDate); if (branchId != null) add(branchId) }
        db.query(sql, params) { rs ->
            WaiterSalesSummary(
                waiterName = rs.getString("WaiterName") ?: "",
                orderCount = rs.getInt("OrderCount"),
                total      = rs.getDouble("Total"),
                avgOrder   = try { rs.getDouble("AvgOrder") } catch (_: Exception) { 0.0 }
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getDailySales(fromDate: Date, toDate: Date, branchId: Int? = null): List<Pair<String, Double>> {
        val sql = buildString {
            append("""SELECT CONVERT(DATE, OrderDate) AS Day,
                      ISNULL(SUM(GrandTotal), 0) AS Total
               FROM Orders
               WHERE OrderStatus = 'Completed'
                 AND OrderDate >= ? AND OrderDate <= ?""")
            if (branchId != null) append(" AND BranchId = ?")
            append(" GROUP BY CONVERT(DATE, OrderDate) ORDER BY Day")
        }
        val params = mutableListOf<Any?>(fromDate, toDate)
        if (branchId != null) params.add(branchId)
        return db.query(sql, params) { rs -> Pair(rs.getString("Day") ?: "", rs.getDouble("Total")) }
    }

    suspend fun getTopCustomers(fromDate: Date, toDate: Date, limit: Int = 10): List<CustomerSalesSummary> = try {
        db.query(
            """SELECT TOP ($limit) c.CustomerId,
                      c.CustomerName,
                      COUNT(o.OrderId) AS OrderCount,
                      ISNULL(SUM(o.GrandTotal), 0) AS TotalSpent
               FROM Orders o
               JOIN Customers c ON o.CustomerId = c.CustomerId
               WHERE o.OrderStatus = 'Completed'
                 AND o.OrderDate >= ? AND o.OrderDate <= ?
               GROUP BY c.CustomerId, c.CustomerName
               ORDER BY TotalSpent DESC""",
            listOf(fromDate, toDate)
        ) { rs ->
            CustomerSalesSummary(
                customerId   = rs.getInt("CustomerId"),
                customerName = rs.getString("CustomerName") ?: "",
                orderCount   = rs.getInt("OrderCount"),
                totalSpent   = rs.getDouble("TotalSpent")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getExpensesByType(fromDate: Date, toDate: Date): List<Pair<String, Double>> = try {
        db.query(
            """SELECT ISNULL(ExpenseType, 'Other') AS ExpType,
                      ISNULL(SUM(Amount), 0) AS Total
               FROM Expenses
               WHERE IsActive = 1
                 AND ExpenseDate >= ? AND ExpenseDate <= ?
               GROUP BY ExpenseType
               ORDER BY Total DESC""",
            listOf(fromDate, toDate)
        ) { rs -> Pair(rs.getString("ExpType") ?: "Other", rs.getDouble("Total")) }
    } catch (_: Exception) { emptyList() }

    suspend fun getProfitLoss(fromDate: Date, toDate: Date, branchId: Int? = null): ProfitLoss {
        val revenue = getSalesSummary(fromDate, toDate, branchId).totalSales

        val cogs = try {
            db.queryOne(
                """SELECT ISNULL(SUM(oi.Quantity * pr.QuantityRequired * ISNULL(p.PurchasePrice,0)), 0) AS COGS
                   FROM OrderItems oi
                   JOIN Orders o ON oi.OrderId = o.OrderId
                   JOIN ProductRecipes pr ON oi.ProductId = pr.ProductId
                   JOIN Products p ON pr.MaterialId = p.ProductId
                   WHERE o.OrderStatus = 'Completed'
                     AND o.OrderDate >= ? AND o.OrderDate <= ?""",
                listOf(fromDate, toDate)
            ) { it.getDouble("COGS") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        val expenses = try {
            db.queryOne(
                """SELECT ISNULL(SUM(Amount), 0) AS TotalExpenses
                   FROM Expenses
                   WHERE IsActive = 1
                     AND ExpenseDate >= ? AND ExpenseDate <= ?""",
                listOf(fromDate, toDate)
            ) { it.getDouble("TotalExpenses") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        val payrollCosts = try {
            // Filter salary payments by PaymentDate falling within the date range
            // (matches WPF which uses CAST(PaymentDate AS DATE) BETWEEN @From AND @To)
            db.queryOne(
                """SELECT ISNULL(SUM(Amount), 0) AS TotalPayroll
                   FROM SalaryPayments
                   WHERE IsActive = 1
                     AND PaymentDate >= ? AND PaymentDate <= ?""",
                listOf(fromDate, toDate)
            ) { it.getDouble("TotalPayroll") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        // Only count cash actually paid out (PaidAmount), not the full invoice amount.
        // Using TotalAmount would overstate costs for partially-paid or credit purchases.
        val purchaseCosts = try {
            db.queryOne(
                """SELECT ISNULL(SUM(PaidAmount), 0) AS PurchaseCosts
                   FROM PurchaseInvoices
                   WHERE ISNULL(IsActive,1) = 1
                     AND InvoiceDate >= ? AND InvoiceDate <= ?""",
                listOf(fromDate, toDate)
            ) { it.getDouble("PurchaseCosts") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        // Advance salaries paid out are a cash expense and must be counted in P&L.
        val advanceSalaries = try {
            db.queryOne(
                """SELECT ISNULL(SUM(Amount), 0) AS TotalAdvances
                   FROM EmployeeAdvances
                   WHERE IsActive = 1
                     AND AdvanceDate >= ? AND AdvanceDate <= ?""",
                listOf(fromDate, toDate)
            ) { it.getDouble("TotalAdvances") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        return ProfitLoss(
            revenue         = revenue,
            cogs            = cogs,
            expenses        = expenses,
            payrollCosts    = payrollCosts,
            purchaseCosts   = purchaseCosts,
            advanceSalaries = advanceSalaries
        )
    }

    suspend fun getPurchaseSummary(fromDate: Date, toDate: Date): List<SupplierPurchaseSummary> = try {
        db.query(
            """SELECT ISNULL(s.SupplierName,'Unknown') AS SupplierName,
                      COUNT(pi.InvoiceId) AS InvoiceCount,
                      ISNULL(SUM(ISNULL(pi.GrandTotal, ISNULL(pi.TotalAmount,0))), 0) AS TotalAmount,
                      ISNULL(SUM(pi.PaidAmount),   0) AS TotalPaid,
                      ISNULL(SUM(pi.BalanceAmount), 0) AS TotalBalance
               FROM PurchaseInvoices pi
               LEFT JOIN Suppliers s ON s.SupplierId = pi.SupplierId
               WHERE ISNULL(pi.IsActive,1) = 1
                 AND pi.InvoiceDate >= ? AND pi.InvoiceDate <= ?
               GROUP BY pi.SupplierId, s.SupplierName
               ORDER BY TotalAmount DESC""",
            listOf(fromDate, toDate)
        ) { rs ->
            SupplierPurchaseSummary(
                supplierName  = rs.getString("SupplierName") ?: "Unknown",
                invoiceCount  = rs.getInt("InvoiceCount"),
                totalAmount   = rs.getDouble("TotalAmount"),
                totalPaid     = rs.getDouble("TotalPaid"),
                totalBalance  = rs.getDouble("TotalBalance")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getSalesByUser(fromDate: Date, toDate: Date, branchId: Int? = null): List<UserSalesSummary> = try {
        val sql = buildString {
            append("""SELECT u.FullName AS UserName,
                      COUNT(*) AS OrderCount,
                      ISNULL(SUM(o.GrandTotal), 0) AS TotalSales,
                      ISNULL(AVG(o.GrandTotal), 0) AS AvgOrder
               FROM Orders o
               JOIN Users u ON u.UserId = o.CreatedBy
               WHERE o.OrderStatus = 'Completed'
                 AND o.OrderDate >= ? AND o.OrderDate <= ?""")
            if (branchId != null) append(" AND o.BranchId = ?")
            append(" GROUP BY u.UserId, u.FullName ORDER BY TotalSales DESC")
        }
        val params = buildList { add(fromDate); add(toDate); if (branchId != null) add(branchId) }
        db.query(sql, params) { rs ->
            UserSalesSummary(
                userName   = rs.getString("UserName") ?: "",
                orderCount = rs.getInt("OrderCount"),
                totalSales = rs.getDouble("TotalSales"),
                avgOrder   = rs.getDouble("AvgOrder")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getCancelledOrders(fromDate: Date, toDate: Date, branchId: Int? = null): List<Order> = try {
        val sql = buildString {
            append("""SELECT TOP 200 o.OrderId, o.OrderNo, o.TokenNo, o.OrderType,
                      o.GrandTotal, o.OrderStatus, o.PaymentStatus,
                      o.OrderDate AS CreatedAt, ISNULL(t.TableName,'') AS TableName,
                      (SELECT COUNT(*) FROM OrderItems WHERE OrderId = o.OrderId) AS ItemCount
               FROM Orders o
               LEFT JOIN Tables t ON o.TableId = t.TableId
               WHERE o.OrderStatus IN ('Cancelled','Void','Voided')
                 AND o.OrderDate >= ? AND o.OrderDate <= ?""")
            if (branchId != null) append(" AND o.BranchId = ?")
            append(" ORDER BY o.OrderDate DESC")
        }
        val params = buildList { add(fromDate); add(toDate); if (branchId != null) add(branchId) }
        db.query(sql, params) { rs ->
            Order(
                orderId      = rs.getInt("OrderId"),
                orderNo      = rs.getString("OrderNo") ?: "",
                tokenNo      = rs.getString("TokenNo") ?: "",
                orderType    = rs.getString("OrderType") ?: "",
                tableName    = rs.getString("TableName"),
                grandTotal   = rs.getDouble("GrandTotal"),
                orderStatus  = rs.getString("OrderStatus") ?: "",
                paymentStatus= rs.getString("PaymentStatus") ?: "",
                createdAt    = rs.getTimestamp("CreatedAt") ?: java.util.Date(),
                itemCount    = try { rs.getInt("ItemCount") } catch (_: Exception) { 0 }
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getDeliveryReport(fromDate: Date, toDate: Date, branchId: Int? = null): List<DeliveryReportRow> = try {
        val sql = buildString {
            append("""SELECT dc.CompanyId, dc.CompanyName, dc.CommissionPercent,
                      COUNT(o.OrderId) AS OrderCount,
                      ISNULL(SUM(o.GrandTotal), 0) AS TotalSales,
                      ISNULL(SUM(o.GrandTotal * dc.CommissionPercent / 100), 0) AS TotalCommission,
                      ISNULL(SUM(o.GrandTotal * (1 - dc.CommissionPercent / 100)), 0) AS NetRevenue
               FROM DeliveryCompanies dc
               LEFT JOIN Orders o ON o.DeliveryCompanyId = dc.CompanyId
                 AND o.OrderType = 'Delivery'
                 AND o.OrderStatus = 'Completed'
                 AND o.OrderDate >= ? AND o.OrderDate <= ?""")
            if (branchId != null) append(" AND o.BranchId = ?")
            append("""
               WHERE dc.IsActive = 1
               GROUP BY dc.CompanyId, dc.CompanyName, dc.CommissionPercent
               ORDER BY TotalSales DESC""")
        }
        val params = buildList { add(fromDate); add(toDate); if (branchId != null) add(branchId) }
        db.query(sql, params) { rs ->
            DeliveryReportRow(
                companyId         = rs.getInt("CompanyId"),
                companyName       = rs.getString("CompanyName") ?: "",
                commissionPercent = rs.getDouble("CommissionPercent"),
                orderCount        = rs.getInt("OrderCount"),
                totalSales        = rs.getDouble("TotalSales"),
                totalCommission   = rs.getDouble("TotalCommission"),
                netRevenue        = rs.getDouble("NetRevenue")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getShiftHistory(limit: Int = 30): List<ShiftSummaryReport> = try {
        db.query(
            """SELECT TOP ($limit) s.ShiftId, s.ShiftCode, u.FullName AS OpenedBy,
                      s.OpeningTime AS OpenDate, s.ClosingTime AS CloseDate,
                      ISNULL(s.TotalSales, 0) AS TotalSales, s.ShiftStatus,
                      (SELECT COUNT(*) FROM Orders o WHERE o.ShiftId = s.ShiftId AND o.OrderStatus = 'Completed') AS OrderCount,
                      ISNULL((SELECT SUM(Amount) FROM CashTransactions WHERE ShiftId = s.ShiftId AND TransactionType = 'In'), 0) AS CashIn,
                      ISNULL((SELECT SUM(Amount) FROM CashTransactions WHERE ShiftId = s.ShiftId AND TransactionType = 'Out'), 0) AS CashOut
               FROM Shifts s
               JOIN Users u ON s.UserId = u.UserId
               ORDER BY s.OpeningTime DESC""",
            emptyList()
        ) { rs ->
            ShiftSummaryReport(
                shiftId     = rs.getInt("ShiftId"),
                shiftCode   = rs.getString("ShiftCode") ?: "",
                openedBy    = rs.getString("OpenedBy") ?: "",
                openDate    = rs.getTimestamp("OpenDate") ?: java.util.Date(),
                closeDate   = try { rs.getTimestamp("CloseDate") } catch (_: Exception) { null },
                totalSales  = rs.getDouble("TotalSales"),
                orderCount  = rs.getInt("OrderCount"),
                shiftStatus = rs.getString("ShiftStatus") ?: "Closed",
                cashIn      = try { rs.getDouble("CashIn") } catch (_: Exception) { 0.0 },
                cashOut     = try { rs.getDouble("CashOut") } catch (_: Exception) { 0.0 }
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getLowStockItems(): List<StockItem> {
        // Try ReorderLevel first (standard column name in WPF schema)
        val primary = try {
            db.query(
                """SELECT p.ProductId, p.ProductName, p.CategoryId,
                          c.CategoryName, ISNULL(c.ColorCode,'#FF6B35') AS ColorCode,
                          ISNULL(p.CurrentStock, 0)  AS CurrentStock,
                          ISNULL(p.ReorderLevel, 0)  AS MinimumStock,
                          ISNULL(p.SalePrice, 0)     AS SalePrice,
                          p.IsActive
                   FROM Products p
                   LEFT JOIN Categories c ON p.CategoryId = c.CategoryId
                   WHERE p.IsActive = 1 AND p.IsStockManaged = 1
                     AND ISNULL(p.ReorderLevel, 0) > 0
                     AND ISNULL(p.CurrentStock, 0) <= ISNULL(p.ReorderLevel, 0)
                   ORDER BY p.CurrentStock ASC, p.ProductName""",
                emptyList()
            ) { rs ->
                StockItem(
                    productId    = rs.getInt("ProductId"),
                    productName  = rs.getString("ProductName") ?: "",
                    categoryId   = rs.getInt("CategoryId"),
                    categoryName = try { rs.getString("CategoryName") ?: "" } catch (_: Exception) { "" },
                    categoryColor = try { rs.getString("ColorCode") ?: "#FF6B35" } catch (_: Exception) { "#FF6B35" },
                    currentStock = rs.getDouble("CurrentStock"),
                    minimumStock = rs.getDouble("MinimumStock"),
                    salePrice    = try { rs.getDouble("SalePrice") } catch (_: Exception) { 0.0 },
                    isActive     = rs.getBoolean("IsActive")
                )
            }
        } catch (_: Exception) { null }
        if (primary != null) return primary
        // Fallback: MinimumStock column (alternate schema)
        return try {
            db.query(
                """SELECT p.ProductId, p.ProductName, p.CategoryId,
                          ISNULL(p.CurrentStock, 0)  AS CurrentStock,
                          ISNULL(p.MinimumStock, 5)  AS MinimumStock,
                          ISNULL(p.SalePrice, 0)     AS SalePrice,
                          p.IsActive
                   FROM Products p
                   WHERE p.IsActive = 1 AND p.IsStockManaged = 1
                     AND ISNULL(p.MinimumStock, 5) > 0
                     AND ISNULL(p.CurrentStock, 0) <= ISNULL(p.MinimumStock, 5)
                   ORDER BY p.CurrentStock ASC, p.ProductName""",
                emptyList()
            ) { rs ->
                StockItem(
                    productId    = rs.getInt("ProductId"),
                    productName  = rs.getString("ProductName") ?: "",
                    categoryId   = 0,
                    categoryName = "",
                    categoryColor = "#FF6B35",
                    currentStock = rs.getDouble("CurrentStock"),
                    minimumStock = rs.getDouble("MinimumStock"),
                    salePrice    = try { rs.getDouble("SalePrice") } catch (_: Exception) { 0.0 },
                    isActive     = rs.getBoolean("IsActive")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getCashLedgerSummary(fromDate: Date, toDate: Date): Triple<Double, Double, Double> = try {
        val cashIn = db.queryOne(
            """SELECT ISNULL(SUM(Amount),0) AS Total FROM CashTransactions
               WHERE TransactionType = 'In'
                 AND CreatedAt >= ? AND CreatedAt <= ?""",
            listOf(fromDate, toDate)
        ) { it.getDouble("Total") } ?: 0.0
        val cashOut = db.queryOne(
            """SELECT ISNULL(SUM(Amount),0) AS Total FROM CashTransactions
               WHERE TransactionType = 'Out'
                 AND CreatedAt >= ? AND CreatedAt <= ?""",
            listOf(fromDate, toDate)
        ) { it.getDouble("Total") } ?: 0.0
        Triple(cashIn, cashOut, cashIn - cashOut)
    } catch (_: Exception) { Triple(0.0, 0.0, 0.0) }

    suspend fun getAttendanceReportSummary(month: Int, year: Int): List<AttendanceMonthSummary> = try {
        db.query(
            """
            SELECT e.EmployeeId AS UserId, e.EmployeeName AS FullName,
                   ISNULL(SUM(CASE WHEN a.CheckInTime IS NOT NULL AND a.CheckOutTime IS NOT NULL THEN 1 ELSE 0 END), 0) AS PresentDays,
                   ISNULL(SUM(CASE WHEN a.CheckInTime IS NOT NULL AND a.CheckOutTime IS NULL THEN 1 ELSE 0 END), 0) AS InProgressDays,
                   ISNULL(SUM(CASE WHEN a.AttendanceId IS NOT NULL AND a.CheckInTime IS NULL THEN 1 ELSE 0 END), 0) AS AbsentDays
            FROM Employees e
            LEFT JOIN EmployeeAttendance a
                   ON a.EmployeeId = e.EmployeeId
                  AND MONTH(a.AttendanceDate) = ? AND YEAR(a.AttendanceDate) = ?
            WHERE e.IsActive = 1
            GROUP BY e.EmployeeId, e.EmployeeName
            HAVING SUM(CASE WHEN a.AttendanceId IS NOT NULL THEN 1 ELSE 0 END) > 0
            ORDER BY e.EmployeeName
            """.trimIndent(),
            listOf(month, year)
        ) { rs ->
            AttendanceMonthSummary(
                userId         = rs.getInt("UserId"),
                fullName       = rs.getString("FullName") ?: "",
                presentDays    = rs.getInt("PresentDays"),
                inProgressDays = rs.getInt("InProgressDays"),
                absentDays     = rs.getInt("AbsentDays")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getPayrollReportSummary(month: Int, year: Int): List<PayrollRecord> = try {
        db.query(
            """SELECT e.EmployeeId AS UserId, e.EmployeeName AS FullName,
                      ISNULL(e.MonthlySalary, 0) AS BasicSalary,
                      ISNULL((SELECT SUM(Amount) FROM EmployeeAdvances WHERE EmployeeId=e.EmployeeId AND IsActive=1
                              AND MONTH(AdvanceDate)=? AND YEAR(AdvanceDate)=?), 0) AS Deductions,
                      ISNULL((SELECT SUM(Amount) FROM SalaryPayments WHERE EmployeeId=e.EmployeeId AND PeriodMonth=? AND PeriodYear=? AND IsActive=1), 0) AS PaidAmount
               FROM Employees e
               WHERE e.IsActive=1
               ORDER BY e.EmployeeName""",
            listOf(month, year, month, year)
        ) { rs ->
            val basic  = rs.getDouble("BasicSalary")
            val deduct = rs.getDouble("Deductions")
            val paid   = rs.getDouble("PaidAmount")
            // netSalary is the computed amount owed (basic minus advances), NOT the paid amount
            val netSalary = (basic - deduct).coerceAtLeast(0.0)
            PayrollRecord(
                userId        = rs.getInt("UserId"),
                fullName      = rs.getString("FullName") ?: "",
                payMonth      = month,
                payYear       = year,
                basicSalary   = basic,
                deductions    = deduct,
                netSalary     = netSalary,
                // Matches WPF: IsPaid = RemainingPay <= 0, where RemainingPay = NetPay - AlreadyPaid
                paymentStatus = if (basic > 0 && paid >= netSalary - 0.01) "Paid" else "Pending"
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getDailySalesDetail(fromDate: Date, toDate: Date, branchId: Int? = null): List<DailySalesDetail> = try {
        val sql = buildString {
            append("""SELECT CONVERT(DATE, OrderDate) AS Day,
                      COUNT(*) AS OrderCount,
                      ISNULL(SUM(CASE WHEN OrderStatus='Completed' THEN GrandTotal         ELSE 0 END), 0) AS TotalSales,
                      ISNULL(SUM(CASE WHEN OrderStatus='Completed' THEN ISNULL(TaxAmount,0)      ELSE 0 END), 0) AS TotalTax,
                      ISNULL(SUM(CASE WHEN OrderStatus='Completed' THEN ISNULL(DiscountAmount,0) ELSE 0 END), 0) AS TotalDiscount
               FROM Orders
               WHERE OrderDate >= ? AND OrderDate <= ?""")
            if (branchId != null) append(" AND BranchId = ?")
            append(" GROUP BY CONVERT(DATE, OrderDate) ORDER BY Day")
        }
        val params = mutableListOf<Any?>(fromDate, toDate)
        if (branchId != null) params.add(branchId)
        db.query(sql, params) { rs ->
            DailySalesDetail(
                date       = rs.getString("Day") ?: "",
                orderCount = rs.getInt("OrderCount"),
                sales      = rs.getDouble("TotalSales"),
                tax        = rs.getDouble("TotalTax"),
                discount   = rs.getDouble("TotalDiscount")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getRecentOrders(fromDate: Date, toDate: Date, limit: Int = 50, branchId: Int? = null): List<Order> = try {
        val sql = buildString {
            append("""SELECT TOP ($limit) o.OrderId, o.OrderNo, ISNULL(o.TokenNo,'') AS TokenNo,
                      o.OrderType, o.GrandTotal, o.OrderStatus, o.PaymentStatus,
                      o.OrderDate AS CreatedAt, ISNULL(t.TableName,'') AS TableName,
                      (SELECT COUNT(*) FROM OrderItems WHERE OrderId = o.OrderId) AS ItemCount
               FROM Orders o
               LEFT JOIN Tables t ON o.TableId = t.TableId
               WHERE o.OrderDate >= ? AND o.OrderDate <= ?""")
            if (branchId != null) append(" AND o.BranchId = ?")
            append(" ORDER BY o.OrderDate DESC")
        }
        val params = mutableListOf<Any?>(fromDate, toDate)
        if (branchId != null) params.add(branchId)
        db.query(sql, params) { rs ->
            Order(
                orderId       = rs.getInt("OrderId"),
                orderNo       = rs.getString("OrderNo") ?: "",
                tokenNo       = rs.getString("TokenNo") ?: "",
                orderType     = rs.getString("OrderType") ?: "",
                tableName     = rs.getString("TableName"),
                grandTotal    = rs.getDouble("GrandTotal"),
                orderStatus   = rs.getString("OrderStatus") ?: "",
                paymentStatus = rs.getString("PaymentStatus") ?: "",
                createdAt     = rs.getTimestamp("CreatedAt") ?: java.util.Date(),
                itemCount     = try { rs.getInt("ItemCount") } catch (_: Exception) { 0 }
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getDailyAttendance(from: java.sql.Date, to: java.sql.Date): List<DailyAttendanceRow> = try {
        db.query(
            """
            SELECT a.AttendanceDate, e.EmployeeName, ISNULL(e.EmployeeRole,'') AS RoleName,
                   a.CheckInTime, a.CheckOutTime
            FROM EmployeeAttendance a
            INNER JOIN Employees e ON e.EmployeeId = a.EmployeeId
            WHERE a.AttendanceDate >= ? AND a.AttendanceDate <= ?
            ORDER BY a.AttendanceDate DESC, e.EmployeeName
            """.trimIndent(),
            listOf(from, to)
        ) { rs ->
            DailyAttendanceRow(
                attendanceDate = rs.getDate("AttendanceDate") ?: java.util.Date(),
                employeeName   = rs.getString("EmployeeName") ?: "",
                roleName       = rs.getString("RoleName") ?: "",
                checkInTime    = rs.getTimestamp("CheckInTime"),
                checkOutTime   = rs.getTimestamp("CheckOutTime")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getInventorySnapshot(): List<InventoryItem> = try {
        db.query(
            """SELECT p.ProductId, ISNULL(p.ProductCode,'') AS ProductCode, p.ProductName,
                      ISNULL(p.Unit,'Pcs') AS Unit,
                      ISNULL(p.CurrentStock, 0)  AS CurrentStock,
                      ISNULL(p.ReorderLevel, 0)  AS ReorderLevel,
                      ISNULL(p.PurchasePrice, 0) AS PurchaseRate
               FROM Products p
               WHERE p.IsActive = 1 AND p.IsStockManaged = 1
               ORDER BY
                   CASE WHEN ISNULL(p.CurrentStock,0) <= 0 THEN 0
                        WHEN ISNULL(p.ReorderLevel,0) > 0 AND ISNULL(p.CurrentStock,0) <= ISNULL(p.ReorderLevel,0) THEN 1
                        ELSE 2 END,
               p.ProductName""",
            emptyList()
        ) { rs ->
            InventoryItem(
                productId    = rs.getInt("ProductId"),
                productCode  = rs.getString("ProductCode") ?: "",
                productName  = rs.getString("ProductName") ?: "",
                unit         = rs.getString("Unit") ?: "Pcs",
                currentStock = rs.getDouble("CurrentStock"),
                reorderLevel = rs.getDouble("ReorderLevel"),
                purchaseRate = rs.getDouble("PurchaseRate")
            )
        }
    } catch (_: Exception) { emptyList() }
}
