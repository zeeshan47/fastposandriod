@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.reports

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.ReportsViewModel

@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit,
    vm: ReportsViewModel = hiltViewModel()
) {
    val summary          by vm.summary.collectAsState()
    val paymentBreakdown by vm.paymentBreakdown.collectAsState()
    val topProducts      by vm.topProducts.collectAsState()
    val orderTypes       by vm.orderTypes.collectAsState()
    val waiterSales      by vm.waiterSales.collectAsState()
    val userSales        by vm.userSales.collectAsState()
    val cancelledOrders  by vm.cancelledOrders.collectAsState()
    val recentFeedback   by vm.recentFeedback.collectAsState()
    val deliveryReport   by vm.deliveryReport.collectAsState()
    val topCustomers     by vm.topCustomers.collectAsState()
    val shiftHistory     by vm.shiftHistory.collectAsState()
    val cashLedger       by vm.cashLedger.collectAsState()
    val expensesByType   by vm.expensesByType.collectAsState()
    val purchaseSummary  by vm.purchaseSummary.collectAsState()
    val dailySalesDetail by vm.dailySalesDetail.collectAsState()
    val recentOrders     by vm.recentOrders.collectAsState()
    val attendanceSummary by vm.attendanceSummary.collectAsState()
    val payrollSummary    by vm.payrollSummary.collectAsState()
    val inventoryItems    by vm.inventoryItems.collectAsState()
    val dailyAttendance   by vm.dailyAttendance.collectAsState()
    val profitLoss        by vm.profitLoss.collectAsState()
    val isLoading        by vm.isLoading.collectAsState()
    val error            by vm.error.collectAsState()
    val selectedRange    by vm.selectedRange.collectAsState()
    val settings         by vm.session.settings.collectAsState()
    val fromDate         by vm.fromDate.collectAsState()
    val toDate           by vm.toDate.collectAsState()
    val branches          by vm.branches.collectAsState()
    val selectedBranchId  by vm.selectedBranchId.collectAsState()
    val reportPreviewText by vm.reportPreviewText.collectAsState()
    val snack            = remember { SnackbarHostState() }
    val context          = LocalContext.current
    var showCustomDialog by remember { mutableStateOf(false) }
    var customFromText   by remember { mutableStateOf("") }
    var customToText     by remember { mutableStateOf("") }
    val dateFmt          = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()) }
    var selectedTab      by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        "Sales Summary", "Payment & Order Type", "Expense Summary", "Shift History",
        "Cancelled Orders", "By User", "By Waiter", "Feedback",
        "Delivery", "Purchases", "Loyalty", "Cash Ledger",
        "Attendance", "Payroll", "Inventory", "Daily Attend", "P&L"
    )

    LaunchedEffect(error) {
        error?.let { snack.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { vm.prepareReportPreview() }) { Icon(Icons.Default.Print, "Print") }
                    IconButton(onClick = {
                        val uri = vm.exportToCsv(context) ?: return@IconButton
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            putExtra(Intent.EXTRA_SUBJECT, "Sales Report – $selectedRange")
                        }
                        context.startActivity(Intent.createChooser(intent, "Export CSV"))
                    }) { Icon(Icons.Default.FileDownload, "Export CSV") }
                    IconButton(onClick = {
                        val text = vm.generateReportText(settings.currencySymbol)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            putExtra(Intent.EXTRA_SUBJECT, "Sales Report – $selectedRange")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }) { Icon(Icons.Default.Share, "Share") }
                    IconButton(onClick = vm::loadReports) { Icon(Icons.Default.Refresh, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->

        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Branch filter ─────────────────────────────────────────────────
            if (branches.isNotEmpty()) {
                LazyRow(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedBranchId == null,
                            onClick  = { vm.selectBranch(null) },
                            label    = { Text("All Branches", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Store, null, Modifier.size(12.dp)) }
                        )
                    }
                    items(branches) { branch ->
                        FilterChip(
                            selected = selectedBranchId == branch.branchId,
                            onClick  = { vm.selectBranch(branch.branchId) },
                            label    = { Text(branch.branchName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // ── Date range filter ──────────────────────────────────────────────
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Today", "Yesterday", "This Week", "This Month").forEach { range ->
                        FilterChip(
                            selected = selectedRange == range,
                            onClick  = { vm.setRange(range) },
                            label    = { Text(range, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected    = selectedRange == "Current Shift",
                        onClick     = { vm.setRange("Current Shift") },
                        label       = { Text("Current Shift", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.AccessTime, null, Modifier.size(14.dp)) },
                        modifier    = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected    = selectedRange == "Custom",
                        onClick     = { customFromText = dateFmt.format(fromDate); customToText = dateFmt.format(toDate); showCustomDialog = true },
                        label       = { Text("Custom", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.DateRange, null, Modifier.size(14.dp)) },
                        modifier    = Modifier.weight(1f)
                    )
                    if (selectedRange == "Custom") {
                        Text(
                            "${dateFmt.format(fromDate)} – ${dateFmt.format(toDate)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Top stat cards ─────────────────────────────────────────────────
            val totalExpenses  = expensesByType.sumOf { it.second }
            val totalPayroll   = payrollSummary.filter { it.paymentStatus == "Paid" }.sumOf { it.netSalary }
            val purchaseCount = purchaseSummary.sumOf { it.invoiceCount }
            val sym = settings.currencySymbol
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatCard("Sales",    compactAmount(summary.totalSales, sym),  GreenSuccess,  Modifier.weight(1f))
                StatCard("Orders",   summary.totalOrders.toString(),          MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatCard("Payroll",  compactAmount(totalPayroll, sym),        AmberWarning,  Modifier.weight(1f))
                StatCard("Expense",  compactAmount(totalExpenses, sym),       RedError,      Modifier.weight(1f))
                StatCard("Purchases","$purchaseCount inv.",                   BlueInfo,      Modifier.weight(1f))
            }

            Spacer(Modifier.height(4.dp))

            // ── Tab row ────────────────────────────────────────────────────────
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding      = 8.dp,
                containerColor   = MaterialTheme.colorScheme.surfaceVariant
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick  = { selectedTab = idx },
                        text     = { Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Scaffold
            }

            // ── Tab content ────────────────────────────────────────────────────
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {

                    // ── 0: Sales Summary ──────────────────────────────────────
                    0 -> {
                        item {
                            ReportCard(title = "Daily Sales Breakdown", icon = Icons.Default.BarChart, iconColor = MaterialTheme.colorScheme.primary) {
                                if (dailySalesDetail.isEmpty()) {
                                    EmptyState("No daily sales data")
                                } else {
                                    ReportTableHeader(listOf("Date" to 1f, "Orders" to 0.5f, "Sales" to 1f, "Tax" to 0.8f, "Discount" to 0.8f))
                                    dailySalesDetail.forEach { row ->
                                        ReportTableRow(listOf(
                                            row.date           to 1f,
                                            row.orderCount.toString() to 0.5f,
                                            row.sales.formatCurrency(settings.currencySymbol) to 1f,
                                            row.tax.formatCurrency(settings.currencySymbol) to 0.8f,
                                            row.discount.formatCurrency(settings.currencySymbol) to 0.8f
                                        ))
                                    }
                                    HorizontalDivider(Modifier.padding(top = 4.dp))
                                    ReportTableRow(
                                        listOf(
                                            "Total" to 1f,
                                            dailySalesDetail.sumOf { it.orderCount }.toString() to 0.5f,
                                            dailySalesDetail.sumOf { it.sales }.formatCurrency(settings.currencySymbol) to 1f,
                                            dailySalesDetail.sumOf { it.tax }.formatCurrency(settings.currencySymbol) to 0.8f,
                                            dailySalesDetail.sumOf { it.discount }.formatCurrency(settings.currencySymbol) to 0.8f
                                        ),
                                        bold = true
                                    )
                                }
                            }
                        }
                        item {
                            ReportCard(title = "Top Products", icon = Icons.Default.Star, iconColor = AmberWarning) {
                                if (topProducts.isEmpty()) {
                                    EmptyState("No product sales data")
                                } else {
                                    ReportTableHeader(listOf("Product" to 2f, "Qty" to 0.7f, "Revenue" to 1f))
                                    topProducts.take(20).forEach { p ->
                                        ReportTableRow(listOf(
                                            p.productName to 2f,
                                            "%.1f".format(p.quantity) to 0.7f,
                                            p.revenue.formatCurrency(settings.currencySymbol) to 1f
                                        ))
                                    }
                                }
                            }
                        }
                        item {
                            val orderFmt = remember { java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()) }
                            ReportCard(title = "Recent Orders", icon = Icons.Default.Receipt, iconColor = BlueInfo) {
                                if (recentOrders.isEmpty()) {
                                    EmptyState("No orders in range")
                                } else {
                                    ReportTableHeader(listOf("Order #" to 1f, "Type" to 0.8f, "Items" to 0.5f, "Total" to 1f, "Status" to 0.8f))
                                    recentOrders.take(50).forEach { o ->
                                        ReportTableRow(listOf(
                                            o.orderNo to 1f,
                                            o.orderType to 0.8f,
                                            o.itemCount.toString() to 0.5f,
                                            o.grandTotal.formatCurrency(settings.currencySymbol) to 1f,
                                            o.orderStatus to 0.8f
                                        ))
                                    }
                                }
                            }
                        }
                    }

                    // ── 1: Payment & Order Type ───────────────────────────────
                    1 -> {
                        item {
                            ReportCard(title = "Payment Methods", icon = Icons.Default.Payment, iconColor = GreenSuccess) {
                                if (paymentBreakdown.isEmpty()) {
                                    EmptyState("No payment data")
                                } else {
                                    ReportTableHeader(listOf("Method" to 2f, "Count" to 0.7f, "Amount" to 1f))
                                    paymentBreakdown.forEach { pm ->
                                        ReportTableRow(listOf(
                                            pm.method to 2f,
                                            pm.count.toString() to 0.7f,
                                            pm.amount.formatCurrency(settings.currencySymbol) to 1f
                                        ))
                                    }
                                    HorizontalDivider(Modifier.padding(top = 4.dp))
                                    ReportTableRow(
                                        listOf(
                                            "Total" to 2f,
                                            paymentBreakdown.sumOf { it.count }.toString() to 0.7f,
                                            paymentBreakdown.sumOf { it.amount }.formatCurrency(settings.currencySymbol) to 1f
                                        ),
                                        bold = true
                                    )
                                }
                            }
                        }
                        item {
                            ReportCard(title = "Orders by Type", icon = Icons.Default.Category, iconColor = MaterialTheme.colorScheme.primary) {
                                if (orderTypes.isEmpty()) {
                                    EmptyState("No order type data")
                                } else {
                                    ReportTableHeader(listOf("Order Type" to 2f, "Total" to 1f))
                                    orderTypes.forEach { (type, total) ->
                                        ReportTableRow(listOf(
                                            type to 2f,
                                            total.formatCurrency(settings.currencySymbol) to 1f
                                        ))
                                    }
                                }
                            }
                        }
                    }

                    // ── 2: Expense Summary ────────────────────────────────────
                    2 -> {
                        item {
                            ReportCard(title = "Expenses by Category", icon = Icons.Default.MoneyOff, iconColor = RedError) {
                                if (expensesByType.isEmpty()) {
                                    EmptyState("No expense records in range")
                                } else {
                                    ReportTableHeader(listOf("Category" to 2f, "Amount" to 1f))
                                    expensesByType.forEach { (type, amount) ->
                                        ReportTableRow(listOf(
                                            type to 2f,
                                            amount.formatCurrency(settings.currencySymbol) to 1f
                                        ))
                                    }
                                    HorizontalDivider(Modifier.padding(top = 4.dp))
                                    ReportTableRow(
                                        listOf(
                                            "Total" to 2f,
                                            expensesByType.sumOf { it.second }.formatCurrency(settings.currencySymbol) to 1f
                                        ),
                                        bold = true
                                    )
                                }
                            }
                        }
                    }

                    // ── 3: Shift History ──────────────────────────────────────
                    3 -> {
                        item {
                            val sym      = settings.currencySymbol
                            val dtFmtSh  = remember { java.text.SimpleDateFormat("dd MMM  hh:mm a", java.util.Locale.getDefault()) }
                            // Summary stat chips
                            val totalShiftSales = shiftHistory.sumOf { it.totalSales }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatCard("Shifts",  shiftHistory.size.toString(),       BlueInfo,     Modifier.weight(1f))
                                StatCard("Sales",   compactAmount(totalShiftSales, sym), GreenSuccess, Modifier.weight(1f))
                                StatCard("Orders",  shiftHistory.sumOf { it.orderCount }.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                            }
                        }
                        item {
                            val sym     = settings.currencySymbol
                            val dtFmtSh = remember { java.text.SimpleDateFormat("dd MMM  hh:mm a", java.util.Locale.getDefault()) }
                            ReportCard(title = "Shift Details", icon = Icons.Default.History, iconColor = BlueInfo) {
                                if (shiftHistory.isEmpty()) {
                                    EmptyState("No shift records found")
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        shiftHistory.forEach { s ->
                                            val isOpen = s.shiftStatus == "Open"
                                            val statusColor = if (isOpen) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isOpen) GreenSuccess.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    // Header: code + status
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(s.shiftCode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                        Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                                            Text(s.shiftStatus, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    // Opened by
                                                    Text("By: ${s.openedBy}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    // Dates
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Opened", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(dtFmtSh.format(s.openDate), style = MaterialTheme.typography.bodySmall)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Closed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.closeDate?.let { dtFmtSh.format(it) } ?: "–", style = MaterialTheme.typography.bodySmall, color = if (s.closeDate == null) AmberWarning else MaterialTheme.colorScheme.onSurface)
                                                        }
                                                    }
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                                    // Figures
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Orders", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.orderCount.toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.totalSales.formatCurrency(sym), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                                        }
                                                    }
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Cash In", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.cashIn.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, color = GreenSuccess)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Cash Out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.cashOut.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, color = if (s.cashOut > 0) RedError else MaterialTheme.colorScheme.onSurface)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Net Cash", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            val net = s.cashIn - s.cashOut
                                                            Text(net.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = if (net >= 0) GreenSuccess else RedError)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 4: Cancelled Orders ───────────────────────────────────
                    4 -> {
                        item {
                            val cancelFmt = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()) }
                            ReportCard(title = "Cancelled Orders", icon = Icons.Default.Cancel, iconColor = RedError) {
                                if (cancelledOrders.isEmpty()) {
                                    EmptyState("No cancelled orders in range")
                                } else {
                                    ReportTableHeader(listOf("Order #" to 1f, "Date" to 1f, "Type" to 0.8f, "Items" to 0.5f, "Total" to 1f))
                                    cancelledOrders.forEach { o ->
                                        ReportTableRow(listOf(
                                            o.orderNo to 1f,
                                            cancelFmt.format(o.createdAt) to 1f,
                                            o.orderType to 0.8f,
                                            o.itemCount.toString() to 0.5f,
                                            o.grandTotal.formatCurrency(settings.currencySymbol) to 1f
                                        ))
                                    }
                                    HorizontalDivider(Modifier.padding(top = 4.dp))
                                    ReportTableRow(
                                        listOf(
                                            "${cancelledOrders.size} orders" to 1f,
                                            "" to 1f,
                                            "" to 0.8f,
                                            "" to 0.5f,
                                            cancelledOrders.sumOf { it.grandTotal }.formatCurrency(settings.currencySymbol) to 1f
                                        ),
                                        bold = true
                                    )
                                }
                            }
                        }
                    }

                    // ── 5: By User ────────────────────────────────────────────
                    5 -> {
                        item {
                            ReportCard(title = "Sales by User", icon = Icons.Default.Person, iconColor = BlueInfo) {
                                if (userSales.isEmpty()) {
                                    EmptyState("No user sales data")
                                } else {
                                    ReportTableHeader(listOf("User" to 2f, "Orders" to 0.7f, "Total Sales" to 1f, "Avg Order" to 1f))
                                    userSales.forEach { u ->
                                        ReportTableRow(listOf(
                                            u.userName to 2f,
                                            u.orderCount.toString() to 0.7f,
                                            u.totalSales.formatCurrency(settings.currencySymbol) to 1f,
                                            u.avgOrder.formatCurrency(settings.currencySymbol) to 1f
                                        ))
                                    }
                                    HorizontalDivider(Modifier.padding(top = 4.dp))
                                    ReportTableRow(
                                        listOf(
                                            "Total" to 2f,
                                            userSales.sumOf { it.orderCount }.toString() to 0.7f,
                                            userSales.sumOf { it.totalSales }.formatCurrency(settings.currencySymbol) to 1f,
                                            "" to 1f
                                        ),
                                        bold = true
                                    )
                                }
                            }
                        }
                    }

                    // ── 6: By Waiter ──────────────────────────────────────────
                    6 -> {
                        item {
                            ReportCard(title = "Sales by Waiter", icon = Icons.Default.SupportAgent, iconColor = MaterialTheme.colorScheme.primary) {
                                if (waiterSales.isEmpty()) {
                                    EmptyState("No waiter sales data")
                                } else {
                                    ReportTableHeader(listOf("Waiter" to 1.5f, "Orders" to 0.6f, "Total Sales" to 1f, "Avg Order" to 1f))
                                    waiterSales.forEach { w ->
                                        ReportTableRow(listOf(
                                            w.waiterName to 1.5f,
                                            w.orderCount.toString() to 0.6f,
                                            w.total.formatCurrency(settings.currencySymbol) to 1f,
                                            w.avgOrder.formatCurrency(settings.currencySymbol) to 1f
                                        ))
                                    }
                                    HorizontalDivider(Modifier.padding(top = 4.dp))
                                    ReportTableRow(
                                        listOf(
                                            "Total" to 1.5f,
                                            waiterSales.sumOf { it.orderCount }.toString() to 0.6f,
                                            waiterSales.sumOf { it.total }.formatCurrency(settings.currencySymbol) to 1f,
                                            "" to 1f
                                        ),
                                        bold = true
                                    )
                                }
                            }
                        }
                    }

                    // ── 7: Feedback ───────────────────────────────────────────
                    7 -> {
                        if (recentFeedback.isNotEmpty()) {
                            item {
                                val avgRating = recentFeedback.map { it.rating }.average()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatCard("Responses", recentFeedback.size.toString(), BlueInfo, Modifier.weight(1f))
                                    StatCard("Avg Rating", "%.1f ★".format(avgRating), AmberWarning, Modifier.weight(1f))
                                    StatCard("5★", recentFeedback.count { it.rating == 5 }.toString(), GreenSuccess, Modifier.weight(1f))
                                    StatCard("1-2★", recentFeedback.count { it.rating <= 2 }.toString(), RedError, Modifier.weight(1f))
                                }
                            }
                        }
                        item {
                            val fbFmt = remember { java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()) }
                            ReportCard(title = "Customer Feedback", icon = Icons.Default.ThumbUp, iconColor = AmberWarning) {
                                if (recentFeedback.isEmpty()) {
                                    EmptyState("No feedback records in range")
                                } else {
                                    ReportTableHeader(listOf("Customer" to 1.5f, "Rating" to 0.7f, "Comment" to 2f, "Date" to 1f))
                                    recentFeedback.forEach { fb ->
                                        ReportTableRow(listOf(
                                            fb.customerName.ifBlank { "Guest" } to 1.5f,
                                            fb.ratingStars to 0.7f,
                                            fb.comment.take(30) to 2f,
                                            fbFmt.format(fb.feedbackDate) to 1f
                                        ))
                                    }
                                }
                            }
                        }
                    }

                    // ── 8: Delivery ───────────────────────────────────────────
                    8 -> {
                        if (deliveryReport.isNotEmpty()) {
                            item {
                                val totalOrders     = deliveryReport.sumOf { it.orderCount }
                                val totalSales      = deliveryReport.sumOf { it.totalSales }
                                val totalCommission = deliveryReport.sumOf { it.totalCommission }
                                val netRevenue      = deliveryReport.sumOf { it.netRevenue }
                                val dSym = settings.currencySymbol
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatCard("Orders",     totalOrders.toString(),                    BlueInfo,      Modifier.weight(1f))
                                    StatCard("Sales",      compactAmount(totalSales, dSym),           GreenSuccess,  Modifier.weight(1f))
                                    StatCard("Commission", compactAmount(totalCommission, dSym),      AmberWarning,  Modifier.weight(1f))
                                    StatCard("Net",        compactAmount(netRevenue, dSym),           MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                }
                            }
                        }
                        item {
                            val sym = settings.currencySymbol
                            ReportCard(title = "Delivery Companies", icon = Icons.Default.DeliveryDining, iconColor = BlueInfo) {
                                if (deliveryReport.isEmpty()) {
                                    EmptyState("No delivery orders in range")
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        deliveryReport.forEach { r ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(r.companyName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                                        Surface(color = BlueInfo.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                                            Text("${r.commissionPercent.toInt()}% comm.", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = BlueInfo)
                                                        }
                                                    }
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Orders", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(r.orderCount.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Total Sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(r.totalSales.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Commission", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(r.totalCommission.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, color = AmberWarning, fontWeight = FontWeight.Medium)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Net Revenue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(r.netRevenue.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 9: Purchases ──────────────────────────────────────────
                    9 -> {
                        item {
                            val sym = settings.currencySymbol
                            val totalInv  = purchaseSummary.sumOf { it.totalAmount }
                            val totalPaid = purchaseSummary.sumOf { it.totalPaid }
                            val totalBal  = purchaseSummary.sumOf { it.totalBalance }
                            val invCount  = purchaseSummary.sumOf { it.invoiceCount }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatCard("Invoices",     invCount.toString(),           BlueInfo,      Modifier.weight(1f))
                                StatCard("Invoiced",     compactAmount(totalInv, sym),  MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                                StatCard("Paid",         compactAmount(totalPaid, sym), GreenSuccess,  Modifier.weight(1f))
                                StatCard("Outstanding",  compactAmount(totalBal, sym),  if (totalBal > 0) RedError else GreenSuccess, Modifier.weight(1f))
                            }
                        }
                        item {
                            val sym = settings.currencySymbol
                            ReportCard(title = "Purchases by Supplier", icon = Icons.Default.ShoppingCart, iconColor = MaterialTheme.colorScheme.primary) {
                                if (purchaseSummary.isEmpty()) {
                                    EmptyState("No purchases in range")
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        purchaseSummary.forEach { s ->
                                            val hasBalance = s.totalBalance > 0.01
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp,
                                                    if (hasBalance) RedError.copy(alpha = 0.4f)
                                                    else MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(s.supplierName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Surface(
                                                            color = if (hasBalance) RedError.copy(alpha = 0.12f) else GreenSuccess.copy(alpha = 0.12f),
                                                            shape = MaterialTheme.shapes.small
                                                        ) {
                                                            Text(
                                                                if (hasBalance) "PARTIAL" else "PAID",
                                                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (hasBalance) RedError else GreenSuccess
                                                            )
                                                        }
                                                    }
                                                    Text("${s.invoiceCount} invoice(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.totalAmount.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Paid", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.totalPaid.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = GreenSuccess)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Outstanding", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.totalBalance.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (hasBalance) RedError else MaterialTheme.colorScheme.onSurface)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        // Grand total row
                                        HorizontalDivider(Modifier.padding(vertical = 2.dp))
                                        val grandTotal   = purchaseSummary.sumOf { it.totalAmount }
                                        val grandPaid    = purchaseSummary.sumOf { it.totalPaid }
                                        val grandBalance = purchaseSummary.sumOf { it.totalBalance }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Column(Modifier.weight(1f)) {
                                                Text("Total", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                Text(grandTotal.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text("Paid", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                Text(grandPaid.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Text("Outstanding", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                Text(grandBalance.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (grandBalance > 0) RedError else MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 10: Customers ─────────────────────────────────────────
                    10 -> {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Top Customers", topCustomers.size.toString(), BlueInfo, Modifier.weight(1f))
                            }
                        }
                        item {
                            ReportCard(title = "Top Customers by Spend", icon = Icons.Default.EmojiEvents, iconColor = AmberWarning) {
                                if (topCustomers.isEmpty()) {
                                    EmptyState("No customer data in range")
                                } else {
                                    ReportTableHeader(listOf("Customer" to 2f, "Orders" to 0.7f, "Total Spent" to 1f))
                                    topCustomers.take(20).forEach { c ->
                                        ReportTableRow(listOf(
                                            c.customerName to 2f,
                                            c.orderCount.toString() to 0.7f,
                                            c.totalSpent.formatCurrency(settings.currencySymbol) to 1f
                                        ))
                                    }
                                }
                            }
                        }
                    }

                    // ── 11: Cash Ledger ───────────────────────────────────────
                    11 -> {
                        item {
                            val (cashIn, cashOut, netDiff) = cashLedger
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Cash In",  cashIn.formatCurrency(settings.currencySymbol),  GreenSuccess, Modifier.weight(1f))
                                StatCard("Cash Out", cashOut.formatCurrency(settings.currencySymbol), RedError,     Modifier.weight(1f))
                                StatCard("Net Diff", netDiff.formatCurrency(settings.currencySymbol), if (netDiff >= 0) GreenSuccess else RedError, Modifier.weight(1f))
                            }
                        }
                        item {
                            val shiftFmt    = remember { java.text.SimpleDateFormat("dd MMM  hh:mm a", java.util.Locale.getDefault()) }
                            val sym         = settings.currencySymbol
                            ReportCard(title = "Shift Cash Ledger", icon = Icons.Default.AccountBalance, iconColor = BlueInfo) {
                                if (shiftHistory.isEmpty()) {
                                    EmptyState("No shift cash data")
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        shiftHistory.forEach { s ->
                                            val isOpen     = s.shiftStatus == "Open"
                                            val statusColor = if (isOpen) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(s.shiftCode, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                                        Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                                            Text(s.shiftStatus, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    Text("By: ${s.openedBy}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Opened", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(shiftFmt.format(s.openDate), style = MaterialTheme.typography.bodySmall)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Closed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.closeDate?.let { shiftFmt.format(it) } ?: "–", style = MaterialTheme.typography.bodySmall)
                                                        }
                                                    }
                                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Sales", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.totalSales.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = GreenSuccess)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Orders", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.orderCount.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Cash In", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.cashIn.formatCurrency(sym), style = MaterialTheme.typography.bodySmall)
                                                        }
                                                        Column(Modifier.weight(1f)) {
                                                            Text("Cash Out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            Text(s.cashOut.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, color = if (s.cashOut > 0) RedError else MaterialTheme.colorScheme.onSurface)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 12: Monthly Attendance Summary ─────────────────────────
                    12 -> {
                        item {
                            if (attendanceSummary.isEmpty()) {
                                ReportCard(title = "Attendance Summary", icon = Icons.Default.Groups, iconColor = AmberWarning) {
                                    EmptyState("No attendance data for selected period")
                                }
                            } else {
                                ReportCard(title = "Attendance Summary", icon = Icons.Default.Groups, iconColor = AmberWarning) {
                                    ReportTableHeader(listOf("Employee" to 1.5f, "Present" to 0.6f, "In Prog." to 0.7f, "Absent" to 0.6f))
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    attendanceSummary.forEach { att ->
                                        ReportTableRow(listOf(
                                            att.fullName    to 1.5f,
                                            att.presentDays.toString()    to 0.6f,
                                            att.inProgressDays.toString() to 0.7f,
                                            att.absentDays.toString()     to 0.6f
                                        ))
                                    }
                                }
                            }
                        }
                    }

                    // ── 13: Payroll Summary ─────────────────────────────────────
                    13 -> {
                        item {
                            if (payrollSummary.isEmpty()) {
                                ReportCard(title = "Payroll Summary", icon = Icons.Default.Payments, iconColor = GreenSuccess) {
                                    EmptyState("No payroll data for selected period")
                                }
                            } else {
                                val sym = settings.currencySymbol
                                ReportCard(title = "Payroll Summary", icon = Icons.Default.Payments, iconColor = GreenSuccess) {
                                    payrollSummary.forEach { pr ->
                                        val isPaid = pr.paymentStatus == "Paid"
                                        val statusColor = if (isPaid) GreenSuccess else AmberWarning
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(pr.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                                                        Text(pr.paymentStatus, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text("Basic", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text(pr.basicSalary.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                    }
                                                    Column(Modifier.weight(1f)) {
                                                        Text("Advances", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text(pr.deductions.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = if (pr.deductions > 0) RedError else MaterialTheme.colorScheme.onSurface)
                                                    }
                                                    Column(Modifier.weight(1f)) {
                                                        Text("Net Pay", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Text(pr.netSalary.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = statusColor)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    val paidRows = payrollSummary.filter { it.paymentStatus == "Paid" }
                                    if (paidRows.isNotEmpty()) {
                                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Paid Total", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                            Text(paidRows.sumOf { it.netSalary }.formatCurrency(sym), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GreenSuccess)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 14: Inventory Snapshot ──────────────────────────────────
                    14 -> {
                        item {
                            if (inventoryItems.isEmpty()) {
                                ReportCard(title = "Inventory Snapshot", icon = Icons.Default.Inventory, iconColor = TealAccent) {
                                    EmptyState("No stock-managed items")
                                }
                            } else {
                                val outOfStock = inventoryItems.count { it.currentStock <= 0 }
                                val lowStock   = inventoryItems.count { it.reorderLevel > 0 && it.currentStock in 0.0..it.reorderLevel }
                                ReportCard(title = "Inventory Snapshot", icon = Icons.Default.Inventory, iconColor = TealAccent) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        StatCard("Total Items", inventoryItems.size.toString(), TealAccent, Modifier.weight(1f))
                                        StatCard("Out of Stock", outOfStock.toString(), RedError, Modifier.weight(1f))
                                        StatCard("Low Stock", lowStock.toString(), AmberWarning, Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    ReportTableHeader(listOf("Item" to 1.5f, "Stock" to 0.7f, "Reorder" to 0.7f, "Unit" to 0.5f))
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    inventoryItems.forEach { item ->
                                        val stockColor = when {
                                            item.currentStock <= 0 -> RedError
                                            item.reorderLevel > 0 && item.currentStock <= item.reorderLevel -> AmberWarning
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                            androidx.compose.material3.Text(item.productName, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            androidx.compose.material3.Text("%.1f".format(item.currentStock), Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, color = stockColor, fontWeight = if (item.currentStock <= 0) FontWeight.Bold else FontWeight.Normal)
                                            androidx.compose.material3.Text("%.1f".format(item.reorderLevel), Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            androidx.compose.material3.Text(item.unit, Modifier.weight(0.5f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── 16: Profit & Loss ────────────────────────────────────────
                    16 -> {
                        item {
                            val sym = settings.currencySymbol
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Revenue",      profitLoss.revenue.formatCurrency(sym),      GreenSuccess,  Modifier.weight(1f))
                                StatCard("Gross Profit", profitLoss.grossProfit.formatCurrency(sym),  BlueInfo,      Modifier.weight(1f))
                                StatCard("Net Profit",   profitLoss.netProfit.formatCurrency(sym),    if (profitLoss.netProfit >= 0) GreenSuccess else RedError, Modifier.weight(1f))
                            }
                        }
                        item {
                            val sym = settings.currencySymbol
                            ReportCard(title = "Profit & Loss", icon = Icons.Default.TrendingUp, iconColor = GreenSuccess) {
                                SummaryRow("Revenue (Sales)",     profitLoss.revenue.formatCurrency(sym))
                                SummaryRow("Cost of Goods (COGS)", profitLoss.cogs.formatCurrency(sym))
                                SummaryRow("Gross Profit",        profitLoss.grossProfit.formatCurrency(sym), bold = true)
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                SummaryRow("Expenses",            profitLoss.expenses.formatCurrency(sym))
                                SummaryRow("Payroll Costs",       profitLoss.payrollCosts.formatCurrency(sym))
                                SummaryRow("Advances Paid",       profitLoss.advanceSalaries.formatCurrency(sym))
                                if (profitLoss.cogs == 0.0 && profitLoss.purchaseCosts > 0) {
                                    SummaryRow("Purchase Costs (cash paid)", profitLoss.purchaseCosts.formatCurrency(sym))
                                }
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                SummaryRow("Net Profit",          profitLoss.netProfit.formatCurrency(sym), bold = true)
                            }
                        }
                    }

                    // ── 15: Daily Attendance ─────────────────────────────────────
                    15 -> {
                        item {
                            if (dailyAttendance.isEmpty()) {
                                ReportCard(title = "Daily Attendance Log", icon = Icons.Default.AccessTime, iconColor = MaterialTheme.colorScheme.primary) {
                                    EmptyState("No attendance records for selected period")
                                }
                            } else {
                                val dateFmtReport = remember { java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()) }
                                ReportCard(title = "Daily Attendance Log", icon = Icons.Default.AccessTime, iconColor = MaterialTheme.colorScheme.primary) {
                                    ReportTableHeader(listOf("Date" to 0.6f, "Employee" to 1.2f, "In" to 0.7f, "Out" to 0.7f, "Dur." to 0.6f))
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                    dailyAttendance.forEach { row ->
                                        val statusColor = when (row.statusLabel) {
                                            "Completed"   -> GreenSuccess
                                            "In Progress" -> AmberWarning
                                            else          -> RedError
                                        }
                                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                            androidx.compose.material3.Text(dateFmtReport.format(row.attendanceDate), Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall)
                                            androidx.compose.material3.Text(row.employeeName, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            androidx.compose.material3.Text(row.checkInLabel, Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, color = GreenSuccess)
                                            androidx.compose.material3.Text(row.checkOutLabel, Modifier.weight(0.7f), style = MaterialTheme.typography.bodySmall, color = if (row.checkOutTime != null) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant)
                                            androidx.compose.material3.Text(row.durationLabel, Modifier.weight(0.6f), style = MaterialTheme.typography.bodySmall, color = statusColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Print preview dialog ───────────────────────────────────────────────────
    reportPreviewText?.let { previewText ->
        AlertDialog(
            onDismissRequest = { vm.clearReportPreview() },
            title = { Text("Print Preview") },
            text  = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text       = previewText,
                        style      = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.printReport(); vm.clearReportPreview() }) {
                    Text("Print")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearReportPreview() }) { Text("Cancel") }
            }
        )
    }

    // ── Custom date range dialog ───────────────────────────────────────────────
    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Custom Date Range") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value         = customFromText,
                        onValueChange = { customFromText = it },
                        label         = { Text("From (dd/MM/yyyy)") },
                        modifier      = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value         = customToText,
                        onValueChange = { customToText = it },
                        label         = { Text("To (dd/MM/yyyy)") },
                        modifier      = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val from = runCatching { dateFmt.parse(customFromText)!! }.getOrNull()
                    val to   = runCatching { dateFmt.parse(customToText)!! }.getOrNull()
                    if (from != null && to != null) {
                        vm.setCustomRange(from, to)
                        showCustomDialog = false
                    }
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") } }
        )
    }
}

// ── Shared composables ─────────────────────────────────────────────────────────

/** Formats a currency amount compactly so it always fits in a narrow stat card. */
private fun compactAmount(amount: Double, sym: String): String = when {
    amount >= 10_000_000 -> "$sym ${"%.1f".format(amount / 1_000_000)}M"
    amount >= 1_000_000  -> "$sym ${"%.2f".format(amount / 1_000_000)}M"
    amount >= 10_000     -> "$sym ${"%.1f".format(amount / 1_000)}K"
    amount >= 1_000      -> "$sym ${"%.0f".format(amount / 1_000)}K"
    else                 -> "$sym ${"%.0f".format(amount)}"
}

@Composable
private fun StatCard(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border   = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(2.dp)
        ) {
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ReportCard(
    title:      String,
    icon:       androidx.compose.ui.graphics.vector.ImageVector,
    iconColor:  androidx.compose.ui.graphics.Color,
    content:    @Composable ColumnScope.() -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, Modifier.size(18.dp), tint = iconColor)
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ColumnScope.ReportTableHeader(columns: List<Pair<String, Float>>) {
    Row(Modifier.fillMaxWidth()) {
        columns.forEach { (label, weight) ->
            Text(
                label,
                style    = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(weight),
                color    = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun ReportTableRow(columns: List<Pair<String, Float>>, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        columns.forEach { (value, weight) ->
            Text(
                value,
                style    = MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(weight),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
