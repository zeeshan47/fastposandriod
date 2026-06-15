package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.AttendanceMonthSummary
import com.fastpos.android.data.models.DailyAttendanceRow
import com.fastpos.android.data.models.CustomerFeedback
import com.fastpos.android.data.models.CustomerSalesSummary
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
import com.fastpos.android.data.models.SupplierPurchaseSummary
import com.fastpos.android.data.models.UserSalesSummary
import com.fastpos.android.data.models.WaiterSalesSummary
import com.fastpos.android.data.models.Branch
import com.fastpos.android.data.repositories.BranchRepository
import com.fastpos.android.data.repositories.CustomerFeedbackRepository
import com.fastpos.android.data.repositories.ReportRepository
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.utils.NetworkPrinterHelper
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportRepo:   ReportRepository,
    private val feedbackRepo: CustomerFeedbackRepository,
    private val branchRepo:   BranchRepository,
    private val prefs:        PreferencesManager,
    val session:              SessionManager
) : ViewModel() {

    private val _summary          = MutableStateFlow(SalesSummary())
    private val _paymentBreakdown = MutableStateFlow<List<PaymentMethodSummary>>(emptyList())
    private val _topProducts      = MutableStateFlow<List<ProductSaleSummary>>(emptyList())
    private val _orderTypes       = MutableStateFlow<List<Pair<String, Double>>>(emptyList())
    private val _hourlySales      = MutableStateFlow<List<Pair<Int, Double>>>(emptyList())
    private val _waiterSales      = MutableStateFlow<List<WaiterSalesSummary>>(emptyList())
    private val _categorySales    = MutableStateFlow<List<Pair<String, Double>>>(emptyList())
    private val _profitLoss       = MutableStateFlow(ProfitLoss())
    private val _dailySales       = MutableStateFlow<List<Pair<String, Double>>>(emptyList())
    private val _topCustomers     = MutableStateFlow<List<CustomerSalesSummary>>(emptyList())
    private val _expensesByType   = MutableStateFlow<List<Pair<String, Double>>>(emptyList())
    private val _lowStockItems    = MutableStateFlow<List<StockItem>>(emptyList())
    private val _purchaseSummary  = MutableStateFlow<List<SupplierPurchaseSummary>>(emptyList())
    private val _userSales        = MutableStateFlow<List<UserSalesSummary>>(emptyList())
    private val _cancelledOrders  = MutableStateFlow<List<Order>>(emptyList())
    private val _recentFeedback   = MutableStateFlow<List<CustomerFeedback>>(emptyList())
    private val _deliveryReport   = MutableStateFlow<List<DeliveryReportRow>>(emptyList())
    private val _shiftHistory     = MutableStateFlow<List<ShiftSummaryReport>>(emptyList())
    private val _cashLedger       = MutableStateFlow(Triple(0.0, 0.0, 0.0))
    private val _attendanceSummary= MutableStateFlow<List<AttendanceMonthSummary>>(emptyList())
    private val _payrollSummary   = MutableStateFlow<List<PayrollRecord>>(emptyList())
    private val _dailySalesDetail  = MutableStateFlow<List<DailySalesDetail>>(emptyList())
    private val _recentOrders      = MutableStateFlow<List<Order>>(emptyList())
    private val _inventoryItems    = MutableStateFlow<List<InventoryItem>>(emptyList())
    private val _dailyAttendance   = MutableStateFlow<List<DailyAttendanceRow>>(emptyList())
    private val _isLoading        = MutableStateFlow(false)
    private val _error            = MutableStateFlow<String?>(null)
    private val _fromDate         = MutableStateFlow(startOfDay(Date()))
    private val _toDate           = MutableStateFlow(Date())
    private val _selectedRange    = MutableStateFlow("Today")
    private val _branches           = MutableStateFlow<List<Branch>>(emptyList())
    private val _selectedBranchId   = MutableStateFlow<Int?>(null)
    private val _reportPreviewText  = MutableStateFlow<String?>(null)

    val summary:          StateFlow<SalesSummary>              = _summary
    val paymentBreakdown: StateFlow<List<PaymentMethodSummary>> = _paymentBreakdown
    val topProducts:      StateFlow<List<ProductSaleSummary>>  = _topProducts
    val orderTypes:       StateFlow<List<Pair<String,Double>>>  = _orderTypes
    val hourlySales:      StateFlow<List<Pair<Int, Double>>>     = _hourlySales
    val waiterSales:      StateFlow<List<WaiterSalesSummary>>    = _waiterSales
    val categorySales:    StateFlow<List<Pair<String, Double>>>  = _categorySales
    val profitLoss:       StateFlow<ProfitLoss>                 = _profitLoss
    val dailySales:       StateFlow<List<Pair<String, Double>>> = _dailySales
    val topCustomers:     StateFlow<List<CustomerSalesSummary>> = _topCustomers
    val expensesByType:   StateFlow<List<Pair<String, Double>>> = _expensesByType
    val lowStockItems:    StateFlow<List<StockItem>>            = _lowStockItems
    val purchaseSummary:  StateFlow<List<SupplierPurchaseSummary>> = _purchaseSummary
    val userSales:        StateFlow<List<UserSalesSummary>>      = _userSales
    val cancelledOrders:  StateFlow<List<Order>>                 = _cancelledOrders
    val recentFeedback:   StateFlow<List<CustomerFeedback>>      = _recentFeedback
    val deliveryReport:    StateFlow<List<DeliveryReportRow>>     = _deliveryReport
    val shiftHistory:      StateFlow<List<ShiftSummaryReport>>   = _shiftHistory
    val cashLedger:        StateFlow<Triple<Double, Double, Double>> = _cashLedger
    val attendanceSummary: StateFlow<List<AttendanceMonthSummary>>  = _attendanceSummary
    val payrollSummary:    StateFlow<List<PayrollRecord>>           = _payrollSummary
    val dailySalesDetail:  StateFlow<List<DailySalesDetail>>    = _dailySalesDetail
    val recentOrders:      StateFlow<List<Order>>               = _recentOrders
    val inventoryItems:    StateFlow<List<InventoryItem>>       = _inventoryItems
    val dailyAttendance:  StateFlow<List<DailyAttendanceRow>> = _dailyAttendance
    val isLoading:        StateFlow<Boolean>                    = _isLoading
    val error:            StateFlow<String?>                   = _error
    val fromDate:         StateFlow<Date>                      = _fromDate
    val toDate:           StateFlow<Date>                      = _toDate
    val selectedRange:    StateFlow<String>                    = _selectedRange
    val branches:           StateFlow<List<Branch>>              = _branches
    val selectedBranchId:   StateFlow<Int?>                      = _selectedBranchId
    val reportPreviewText:  StateFlow<String?>                   = _reportPreviewText

    init {
        loadBranches()
        loadReports()
    }

    fun loadBranches() {
        viewModelScope.launch {
            try { _branches.value = branchRepo.getBranches().filter { it.isActive } } catch (_: Exception) {}
        }
    }

    fun selectBranch(branchId: Int?) {
        _selectedBranchId.value = branchId
        loadReports()
    }

    fun setRange(label: String) {
        _selectedRange.value = label
        val cal = Calendar.getInstance()
        when (label) {
            "Today"     -> { _fromDate.value = startOfDay(Date()); _toDate.value = Date() }
            "Yesterday" -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                _fromDate.value = startOfDay(cal.time); _toDate.value = endOfDay(cal.time)
            }
            "This Week" -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                _fromDate.value = startOfDay(cal.time); _toDate.value = Date()
            }
            "This Month" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                _fromDate.value = startOfDay(cal.time); _toDate.value = Date()
            }
            "Current Shift" -> {
                val shiftStart = session.currentShift.value?.openingTime ?: startOfDay(Date())
                _fromDate.value = shiftStart
                _toDate.value   = Date()
            }
        }
        loadReports()
    }

    fun setCustomRange(from: Date, to: Date) {
        _fromDate.value      = startOfDay(from)
        _toDate.value        = endOfDay(to)
        _selectedRange.value = "Custom"
        loadReports()
    }

    fun loadReports() {
        // Refresh live-range boundaries on every load
        when (_selectedRange.value) {
            "Today", "This Week", "This Month" -> _toDate.value = Date()
            "Current Shift" -> {
                _fromDate.value = session.currentShift.value?.openingTime ?: startOfDay(Date())
                _toDate.value   = Date()
            }
        }
        val bId = _selectedBranchId.value
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                _summary.value          = reportRepo.getSalesSummary(_fromDate.value, _toDate.value, bId)
                _paymentBreakdown.value = reportRepo.getSalesByPaymentMethod(_fromDate.value, _toDate.value, bId)
                _topProducts.value      = reportRepo.getTopProducts(_fromDate.value, _toDate.value, branchId = bId)
                _orderTypes.value       = reportRepo.getSalesByOrderType(_fromDate.value, _toDate.value, bId)
                _hourlySales.value      = try { reportRepo.getHourlySales(_fromDate.value, _toDate.value, bId) } catch (_: Exception) { emptyList() }
                _waiterSales.value      = reportRepo.getSalesByWaiter(_fromDate.value, _toDate.value, bId)
                _categorySales.value    = try { reportRepo.getSalesByCategory(_fromDate.value, _toDate.value, bId) } catch (_: Exception) { emptyList() }
                _profitLoss.value       = try { reportRepo.getProfitLoss(_fromDate.value, _toDate.value, bId) } catch (_: Exception) { ProfitLoss() }
                _dailySales.value       = try { reportRepo.getDailySales(_fromDate.value, _toDate.value, bId) } catch (_: Exception) { emptyList() }
                _topCustomers.value     = try { reportRepo.getTopCustomers(_fromDate.value, _toDate.value) } catch (_: Exception) { emptyList() }
                _expensesByType.value   = try { reportRepo.getExpensesByType(_fromDate.value, _toDate.value) } catch (_: Exception) { emptyList() }
                _lowStockItems.value    = try { reportRepo.getLowStockItems() } catch (_: Exception) { emptyList() }
                _purchaseSummary.value  = try { reportRepo.getPurchaseSummary(_fromDate.value, _toDate.value) } catch (_: Exception) { emptyList() }
                _userSales.value        = try { reportRepo.getSalesByUser(_fromDate.value, _toDate.value, bId) } catch (_: Exception) { emptyList() }
                _cancelledOrders.value  = try { reportRepo.getCancelledOrders(_fromDate.value, _toDate.value, bId) } catch (_: Exception) { emptyList() }
                _recentFeedback.value   = try { feedbackRepo.getRecentFeedback(_fromDate.value, _toDate.value) } catch (_: Exception) { emptyList() }
                _dailySalesDetail.value = try { reportRepo.getDailySalesDetail(_fromDate.value, _toDate.value, bId) } catch (_: Exception) { emptyList() }
                _recentOrders.value     = try { reportRepo.getRecentOrders(_fromDate.value, _toDate.value, branchId = bId) } catch (_: Exception) { emptyList() }
                _inventoryItems.value   = try { reportRepo.getInventorySnapshot() } catch (_: Exception) { emptyList() }
                _dailyAttendance.value  = try { reportRepo.getDailyAttendance(java.sql.Date(_fromDate.value.time), java.sql.Date(_toDate.value.time)) } catch (_: Exception) { emptyList() }
                _deliveryReport.value    = try { reportRepo.getDeliveryReport(_fromDate.value, _toDate.value, bId) } catch (_: Exception) { emptyList() }
                _shiftHistory.value      = try { reportRepo.getShiftHistory() } catch (_: Exception) { emptyList() }
                _cashLedger.value        = try { reportRepo.getCashLedgerSummary(_fromDate.value, _toDate.value) } catch (_: Exception) { Triple(0.0, 0.0, 0.0) }
                val cal = java.util.Calendar.getInstance().also { it.time = _fromDate.value }
                _attendanceSummary.value = try { reportRepo.getAttendanceReportSummary(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR)) } catch (_: Exception) { emptyList() }
                _payrollSummary.value    = try { reportRepo.getPayrollReportSummary(cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.YEAR)) } catch (_: Exception) { emptyList() }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun generateReportText(sym: String): String {
        val dtFmt = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val s   = _summary.value
        val pl  = _profitLoss.value
        fun amt(d: Double) = "$sym ${"%.0f".format(d)}"
        return buildString {
            appendLine("================================")
            appendLine("        SALES REPORT")
            appendLine("        ${_selectedRange.value}")
            appendLine("  ${dtFmt.format(_fromDate.value)} – ${dtFmt.format(_toDate.value)}")
            appendLine("================================")
            appendLine("Total Sales     : ${amt(s.totalSales)}")
            appendLine("Orders          : ${s.totalOrders}")
            appendLine("Avg Order       : ${amt(s.avgOrderValue)}")
            appendLine("Completed       : ${s.completedOrders}")
            appendLine("Cancelled       : ${s.cancelledOrders}")
            if (s.voidedOrders > 0)   appendLine("Voided          : ${s.voidedOrders}")
            if (s.refundedOrders > 0) appendLine("Refunded        : ${s.refundedOrders}")
            if (s.totalDiscount > 0)  appendLine("Discount        : ${amt(s.totalDiscount)}")
            if (s.totalTax > 0)       appendLine("Tax Collected   : ${amt(s.totalTax)}")
            if (_paymentBreakdown.value.isNotEmpty()) {
                appendLine("--------------------------------")
                appendLine("PAYMENT METHODS")
                _paymentBreakdown.value.forEach { pm ->
                    appendLine("  ${pm.method.padEnd(14)}: ${amt(pm.amount)} (${pm.count}x)")
                }
            }
            if (_orderTypes.value.isNotEmpty()) {
                appendLine("--------------------------------")
                appendLine("ORDER TYPES")
                _orderTypes.value.forEach { (type, total) ->
                    appendLine("  ${type.padEnd(14)}: ${amt(total)}")
                }
            }
            appendLine("--------------------------------")
            appendLine("PROFIT & LOSS")
            appendLine("  Revenue         : ${amt(pl.revenue)}")
            if (pl.cogs > 0) {
                appendLine("  COGS            : ${amt(pl.cogs)}")
                appendLine("  Gross Profit    : ${amt(pl.grossProfit)}")
            } else if (pl.purchaseCosts > 0) {
                appendLine("  Purchase Costs  : ${amt(pl.purchaseCosts)}")
            }
            if (pl.expenses > 0) appendLine("  Expenses        : ${amt(pl.expenses)}")
            if (pl.payrollCosts > 0) appendLine("  Payroll         : ${amt(pl.payrollCosts)}")
            if (pl.advanceSalaries > 0) appendLine("  Advances Paid   : ${amt(pl.advanceSalaries)}")
            appendLine("  Net Profit      : ${amt(pl.netProfit)}")
            if (_expensesByType.value.isNotEmpty()) {
                appendLine("--------------------------------")
                appendLine("EXPENSES BY TYPE")
                _expensesByType.value.forEach { (type, total) ->
                    appendLine("  ${type.take(14).padEnd(14)}: ${amt(total)}")
                }
            }
            if (_topProducts.value.isNotEmpty()) {
                appendLine("--------------------------------")
                appendLine("TOP PRODUCTS")
                _topProducts.value.take(5).forEachIndexed { i, p ->
                    appendLine("  ${i + 1}. ${p.productName.take(16).padEnd(16)}: ${"%.0f".format(p.quantity)}x ${amt(p.revenue)}")
                }
            }
            val purchases = _purchaseSummary.value
            if (purchases.isNotEmpty()) {
                val totalInv  = purchases.sumOf { it.totalAmount }
                val totalPaid = purchases.sumOf { it.totalPaid }
                val totalBal  = purchases.sumOf { it.totalBalance }
                val invCount  = purchases.sumOf { it.invoiceCount }
                appendLine("--------------------------------")
                appendLine("PURCHASES ($invCount invoices)")
                appendLine("  Invoiced        : ${amt(totalInv)}")
                appendLine("  Paid            : ${amt(totalPaid)}")
                if (totalBal > 0) appendLine("  Outstanding     : ${amt(totalBal)}")
                purchases.forEach { s ->
                    appendLine("  ${s.supplierName.take(16).padEnd(16)}: ${s.invoiceCount}x  ${amt(s.totalAmount)}  Paid:${amt(s.totalPaid)}")
                }
            }
            val (cashIn, cashOut, cashNet) = _cashLedger.value
            if (cashIn > 0 || cashOut > 0) {
                appendLine("--------------------------------")
                appendLine("CASH DRAWER")
                appendLine("  Cash In        : ${amt(cashIn)}")
                appendLine("  Cash Out       : ${amt(cashOut)}")
                appendLine("  Net            : ${amt(cashNet)}")
            }
            // Show only the current open shift or the most recently closed shift
            val sh = session.currentShift.value?.let { cur ->
                _shiftHistory.value.firstOrNull { it.shiftId == cur.shiftId }
            } ?: _shiftHistory.value.firstOrNull()
            if (sh != null) {
                val shiftFmt = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US)
                appendLine("--------------------------------")
                appendLine("SHIFT")
                appendLine("  Code  : ${sh.shiftCode}")
                appendLine("  Status: ${sh.shiftStatus}")
                appendLine("  Opened: ${shiftFmt.format(sh.openDate)}")
                sh.closeDate?.let { appendLine("  Closed: ${shiftFmt.format(it)}") }
                appendLine("  Sales : ${amt(sh.totalSales)}  Orders: ${sh.orderCount}")
                if (sh.cashIn > 0 || sh.cashOut > 0)
                    appendLine("  Cash In: ${amt(sh.cashIn)}  Cash Out: ${amt(sh.cashOut)}")
            }
            appendLine("================================")
            appendLine("Generated by FastPOS Android")
        }
    }

    fun prepareReportPreview() {
        _reportPreviewText.value = generateReportText(session.settings.value.currencySymbol)
    }

    fun clearReportPreview() {
        _reportPreviewText.value = null
    }

    fun printReport() {
        viewModelScope.launch {
            val text        = generateReportText(session.settings.value.currencySymbol)
            val printerType = prefs.receiptPrinterType.first()
            if (printerType == "Network") {
                val ip   = prefs.receiptNetIp.first()
                val port = prefs.receiptNetPort.first()
                if (ip.isBlank()) { _error.value = "No network printer configured. Configure receipt printer in Settings."; return@launch }
                val result = NetworkPrinterHelper.printTextReport(ip, port, text)
                if (!result.isSuccess) _error.value = "Print failed: ${result.exceptionOrNull()?.message}"
            } else {
                val address = prefs.savedPrinterAddress.first()
                if (address.isBlank()) { _error.value = "No Bluetooth printer configured. Configure receipt printer in Settings."; return@launch }
                val result = BluetoothPrinterHelper.printReport(address, text)
                if (!result.isSuccess) _error.value = "Print failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun exportToCsv(context: Context): Uri? {
        val dtFmt = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val s     = _summary.value
        val pl    = _profitLoss.value
        fun esc(v: String) = if (v.contains(',') || v.contains('"')) "\"${v.replace("\"", "\"\"")}\"" else v
        val csv = buildString {
            appendLine("FastPOS Sales Report")
            appendLine("Period,${esc(_selectedRange.value)}")
            appendLine("From,${dtFmt.format(_fromDate.value)}")
            appendLine("To,${dtFmt.format(_toDate.value)}")
            appendLine()
            appendLine("SUMMARY")
            appendLine("Metric,Value")
            appendLine("Total Sales,${"%.2f".format(s.totalSales)}")
            appendLine("Total Orders,${s.totalOrders}")
            appendLine("Average Order Value,${"%.2f".format(s.avgOrderValue)}")
            appendLine("Completed Orders,${s.completedOrders}")
            appendLine("Cancelled Orders,${s.cancelledOrders}")
            if (s.voidedOrders > 0)   appendLine("Voided Orders,${s.voidedOrders}")
            if (s.refundedOrders > 0) appendLine("Refunded Orders,${s.refundedOrders}")
            appendLine("Total Discount,${"%.2f".format(s.totalDiscount)}")
            appendLine("Total Tax,${"%.2f".format(s.totalTax)}")
            if (_paymentBreakdown.value.isNotEmpty()) {
                appendLine()
                appendLine("PAYMENT METHODS")
                appendLine("Method,Amount,Count")
                _paymentBreakdown.value.forEach { pm ->
                    appendLine("${esc(pm.method)},${"%.2f".format(pm.amount)},${pm.count}")
                }
            }
            if (_orderTypes.value.isNotEmpty()) {
                appendLine()
                appendLine("ORDER TYPES")
                appendLine("Type,Amount")
                _orderTypes.value.forEach { (type, amt) ->
                    appendLine("${esc(type)},${"%.2f".format(amt)}")
                }
            }
            if (_topProducts.value.isNotEmpty()) {
                appendLine()
                appendLine("TOP PRODUCTS")
                appendLine("Product,Quantity,Revenue")
                _topProducts.value.forEach { p ->
                    appendLine("${esc(p.productName)},${"%.0f".format(p.quantity)},${"%.2f".format(p.revenue)}")
                }
            }
            if (_categorySales.value.isNotEmpty()) {
                appendLine()
                appendLine("SALES BY CATEGORY")
                appendLine("Category,Amount")
                _categorySales.value.forEach { (cat, amt) ->
                    appendLine("${esc(cat)},${"%.2f".format(amt)}")
                }
            }
            if (_waiterSales.value.isNotEmpty()) {
                appendLine()
                appendLine("WAITER PERFORMANCE")
                appendLine("Waiter,Orders,Revenue")
                _waiterSales.value.forEach { ws ->
                    appendLine("${esc(ws.waiterName)},${ws.orderCount},${"%.2f".format(ws.total)}")
                }
            }
            if (_topCustomers.value.isNotEmpty()) {
                appendLine()
                appendLine("TOP CUSTOMERS")
                appendLine("Customer,Orders,Total Spent")
                _topCustomers.value.forEach { cs ->
                    appendLine("${esc(cs.customerName)},${cs.orderCount},${"%.2f".format(cs.totalSpent)}")
                }
            }
            if (_dailySales.value.isNotEmpty()) {
                appendLine()
                appendLine("DAILY SALES")
                appendLine("Date,Amount")
                _dailySales.value.forEach { (date, amt) ->
                    appendLine("${esc(date)},${"%.2f".format(amt)}")
                }
            }
            if (_hourlySales.value.isNotEmpty()) {
                appendLine()
                appendLine("HOURLY SALES")
                appendLine("Hour,Amount")
                _hourlySales.value.forEach { (hour, amt) ->
                    appendLine("${"%.0f".format(hour.toDouble())}:00,${"%.2f".format(amt)}")
                }
            }
            appendLine()
            appendLine("PROFIT & LOSS")
            appendLine("Metric,Amount")
            appendLine("Revenue,${"%.2f".format(pl.revenue)}")
            appendLine("Cost of Goods Sold,${"%.2f".format(pl.cogs)}")
            appendLine("Gross Profit,${"%.2f".format(pl.grossProfit)}")
            appendLine("Expenses,${"%.2f".format(pl.expenses)}")
            appendLine("Payroll,${"%.2f".format(pl.payrollCosts)}")
            appendLine("Net Profit,${"%.2f".format(pl.netProfit)}")
            if (_expensesByType.value.isNotEmpty()) {
                appendLine()
                appendLine("EXPENSES BY TYPE")
                appendLine("Type,Amount")
                _expensesByType.value.forEach { (type, amt) ->
                    appendLine("${esc(type)},${"%.2f".format(amt)}")
                }
            }
            val csvPurchases = _purchaseSummary.value
            if (csvPurchases.isNotEmpty()) {
                appendLine()
                appendLine("PURCHASES BY SUPPLIER")
                appendLine("Supplier,Invoices,Total Amount,Total Paid,Outstanding")
                csvPurchases.forEach { s ->
                    appendLine("${esc(s.supplierName)},${s.invoiceCount},${"%.2f".format(s.totalAmount)},${"%.2f".format(s.totalPaid)},${"%.2f".format(s.totalBalance)}")
                }
                appendLine()
                appendLine("PURCHASES TOTALS")
                appendLine("Metric,Value")
                appendLine("Total Invoices,${csvPurchases.sumOf { it.invoiceCount }}")
                appendLine("Total Invoiced,${"%.2f".format(csvPurchases.sumOf { it.totalAmount })}")
                appendLine("Total Paid,${"%.2f".format(csvPurchases.sumOf { it.totalPaid })}")
                appendLine("Outstanding,${"%.2f".format(csvPurchases.sumOf { it.totalBalance })}")
            }
            if (_lowStockItems.value.isNotEmpty()) {
                appendLine()
                appendLine("LOW STOCK ITEMS")
                appendLine("Product,Current Stock,Min Stock")
                _lowStockItems.value.forEach { item ->
                    appendLine("${esc(item.productName)},${"%.2f".format(item.currentStock)},${"%.2f".format(item.minimumStock)}")
                }
            }
            if (_deliveryReport.value.isNotEmpty()) {
                appendLine()
                appendLine("DELIVERY SUMMARY")
                appendLine("Company,Orders,Sales,Commission,Net Revenue")
                _deliveryReport.value.forEach { row ->
                    appendLine("${esc(row.companyName)},${row.orderCount},${"%.2f".format(row.totalSales)},${"%.2f".format(row.totalCommission)},${"%.2f".format(row.netRevenue)}")
                }
            }
            if (_shiftHistory.value.isNotEmpty()) {
                appendLine()
                appendLine("SHIFT HISTORY")
                appendLine("Code,Opened By,Open Date,Close Date,Orders,Sales,Status")
                val sf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
                _shiftHistory.value.forEach { sh ->
                    appendLine("${esc(sh.shiftCode)},${esc(sh.openedBy)},${sf.format(sh.openDate)},${sh.closeDate?.let { sf.format(it) } ?: ""},${sh.orderCount},${"%.2f".format(sh.totalSales)},${sh.shiftStatus}")
                }
            }
            val (cashIn, cashOut, cashNet) = _cashLedger.value
            if (cashIn > 0 || cashOut > 0) {
                appendLine()
                appendLine("CASH LEDGER")
                appendLine("Metric,Amount")
                appendLine("Cash In,${"%.2f".format(cashIn)}")
                appendLine("Cash Out,${"%.2f".format(cashOut)}")
                appendLine("Net,${"%.2f".format(cashNet)}")
            }
            if (_attendanceSummary.value.isNotEmpty()) {
                val monthLabel = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US).format(_fromDate.value)
                appendLine()
                appendLine("ATTENDANCE SUMMARY — $monthLabel")
                appendLine("Employee,Present,In Progress,Absent")
                _attendanceSummary.value.forEach { att ->
                    appendLine("${esc(att.fullName)},${att.presentDays},${att.inProgressDays},${att.absentDays}")
                }
            }
            if (_payrollSummary.value.isNotEmpty()) {
                val monthLabel = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US).format(_fromDate.value)
                appendLine()
                appendLine("PAYROLL SUMMARY — $monthLabel")
                appendLine("Employee,Basic Salary,Deductions,Net Salary,Status")
                _payrollSummary.value.forEach { pr ->
                    appendLine("${esc(pr.fullName)},${"%.2f".format(pr.basicSalary)},${"%.2f".format(pr.deductions)},${"%.2f".format(pr.netSalary)},${esc(pr.paymentStatus)}")
                }
                appendLine("TOTAL,,,${"%.2f".format(_payrollSummary.value.sumOf { it.netSalary })},")
            }
            if (_recentFeedback.value.isNotEmpty()) {
                val avgRating = _recentFeedback.value.map { it.rating }.average()
                appendLine()
                appendLine("CUSTOMER FEEDBACK")
                appendLine("Metric,Value")
                appendLine("Responses,${_recentFeedback.value.size}")
                appendLine("Average Rating,${"%.1f".format(avgRating)}")
                appendLine()
                appendLine("Date,Customer,Rating,Comment")
                val ffmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                _recentFeedback.value.forEach { fb ->
                    appendLine("${ffmt.format(fb.feedbackDate)},${esc(fb.customerName)},${fb.rating},${esc(fb.comment)}")
                }
            }
        }
        return try {
            val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = java.io.File(context.cacheDir, "FastPOS_Report_$ts.csv")
            file.writeText(csv, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) { null }
    }

    private fun startOfDay(d: Date) = Calendar.getInstance().also {
        it.time = d; it.set(Calendar.HOUR_OF_DAY, 0); it.set(Calendar.MINUTE, 0); it.set(Calendar.SECOND, 0)
    }.time

    private fun endOfDay(d: Date) = Calendar.getInstance().also {
        it.time = d; it.set(Calendar.HOUR_OF_DAY, 23); it.set(Calendar.MINUTE, 59); it.set(Calendar.SECOND, 59); it.set(Calendar.MILLISECOND, 999)
    }.time
}
