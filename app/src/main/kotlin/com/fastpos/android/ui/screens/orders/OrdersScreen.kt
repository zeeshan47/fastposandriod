@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.orders

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.KitchenPrinterConfig
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.OrderItem
import com.fastpos.android.data.models.RestaurantTable
import com.fastpos.android.ui.components.ManagerOverrideDialog
import com.fastpos.android.ui.screens.payment.ReceiptPreviewSheet
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.utils.formatDateTime
import com.fastpos.android.utils.orderStatusColor
import com.fastpos.android.utils.paymentStatusColor
import com.fastpos.android.viewmodels.KotPreviewGroup
import com.fastpos.android.viewmodels.OrdersViewModel
import java.text.SimpleDateFormat
import java.util.*

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun OrdersScreen(
    onNavigateBack:      () -> Unit,
    onNavigateToPayment: (Int) -> Unit,
    onNavigateToPos:     () -> Unit = {},
    vm: OrdersViewModel = hiltViewModel()
) {
    val orders        by vm.orders.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()
    val error         by vm.error.collectAsState()
    val tab           by vm.tab.collectAsState()
    val message       by vm.message.collectAsState()
    val selectedOrder by vm.selectedOrder.collectAsState()
    val orderItems    by vm.orderItems.collectAsState()
    val isPrinting          by vm.isPrinting.collectAsState()
    val navigateToPos       by vm.navigateToPos.collectAsState()
    val kitchenPrinters     by vm.kitchenPrinters.collectAsState(initial = emptyList())
    val tables              by vm.tables.collectAsState()
    val showReceiptPreview  by vm.showReceiptPreview.collectAsState()
    val previewOrder        by vm.previewOrder.collectAsState()
    val previewPayments     by vm.previewPayments.collectAsState()
    val availablePrinters   by vm.availablePrinters.collectAsState()
    val kotPreviewGroups    by vm.kotPreviewGroups.collectAsState()
    val kotPrintMode        by vm.kotPrintMode.collectAsState(initial = "Silent")
    val settings             by vm.session.settings.collectAsState()

    val snackbarHostState    = remember { SnackbarHostState() }
    var showKotPrinterPickerDialog by remember { mutableStateOf(false) }
    var showKotSendModeDialog      by remember { mutableStateOf(false) }
    var showTablePickerDialog      by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(navigateToPos) {
        if (navigateToPos) { vm.clearNavigateToPos(); onNavigateToPos() }
    }

    if (selectedOrder != null) {
        val context = androidx.compose.ui.platform.LocalContext.current
        ModalBottomSheet(
            onDismissRequest = { vm.selectOrder(null) },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            val canAddItems = selectedOrder!!.paymentStatus != "Paid" &&
                selectedOrder!!.orderStatus !in listOf("Cancelled", "Completed", "Held")
            val canVoidRefund = selectedOrder!!.paymentStatus == "Paid" &&
                selectedOrder!!.orderStatus !in listOf("Void", "Refunded", "Cancelled")
            val isTerminal = selectedOrder!!.orderStatus in listOf("Completed", "Cancelled", "Void", "Refunded")
            val canRevert  = selectedOrder!!.paymentStatus == "Paid" && selectedOrder!!.orderStatus == "Completed"
            val canChangeTable = selectedOrder!!.orderType == "DineIn" && selectedOrder!!.tableId != null &&
                selectedOrder!!.paymentStatus != "Paid" && !isTerminal
            val canPreBill = selectedOrder!!.paymentStatus != "Paid" && !isTerminal
            OrderDetailSheet(
                order             = selectedOrder!!,
                items             = orderItems,
                currency          = settings.currencySymbol,
                isPrinting        = isPrinting,
                onKot             = {
                    if (kotPrintMode == "Silent") showKotSendModeDialog = true
                    else vm.showKotPreviewAll()  // "Preview" shows dialog; "Off" shows disabled message
                },
                onCancel          = { vm.cancelOrder(selectedOrder!!.orderId); vm.selectOrder(null) },
                onBill            = { onNavigateToPayment(selectedOrder!!.orderId); vm.selectOrder(null) },
                onPrint           = { vm.printReceipt(context) },
                onPreBill         = if (canPreBill) {{ vm.printPreBill(context) }} else null,
                onResume          = if (selectedOrder!!.orderStatus == "Held") {{ vm.resumeHeldOrder(selectedOrder!!.orderId) }} else null,
                onAddItems        = if (canAddItems) {{ vm.startEditOrder(selectedOrder!!.orderId, selectedOrder!!.orderNo); vm.selectOrder(null) }} else null,
                onVoid            = if (canVoidRefund) {{ reason -> vm.voidOrder(selectedOrder!!.orderId, reason) }} else null,
                onRefund          = if (canVoidRefund) {{ reason -> vm.refundOrder(selectedOrder!!.orderId, reason) }} else null,
                onReorder         = if (selectedOrder!!.paymentStatus == "Paid") {{ vm.reorderFromHistory(selectedOrder!!.orderId) }} else null,
                onDelete          = if (isTerminal) {{ vm.hardDeleteOrder(selectedOrder!!.orderId); vm.selectOrder(null) }} else null,
                onRevert          = if (canRevert) {{ vm.revertOrderToPending(selectedOrder!!.orderId) }} else null,
                onRemoveItem      = if (canAddItems) {{ itemId -> vm.removeOrderItem(itemId) }} else null,
                onUpdateItemQty   = if (canAddItems) {{ itemId, qty -> vm.updateOrderItemQty(itemId, qty) }} else null,
                onApplyDiscount   = if (canAddItems) {{ amount -> vm.applyOrderDiscount(amount) }} else null,
                onChangeTable     = if (canChangeTable) {{ vm.loadTables(); showTablePickerDialog = true }} else null,
                onMarkDelivered   = if (
                    selectedOrder!!.orderType == "Delivery" &&
                    selectedOrder!!.deliveryCompanyId != null &&
                    selectedOrder!!.deliveryCompanyId != 1 &&
                    !isTerminal
                ) {{ vm.markDelivered(selectedOrder!!.orderId); vm.selectOrder(null) }} else null
            )
        }
    }

    // Table picker dialog for Change Table
    if (showTablePickerDialog && selectedOrder != null) {
        val currentTableId = selectedOrder!!.tableId
        AlertDialog(
            onDismissRequest = { showTablePickerDialog = false },
            icon  = { Icon(Icons.Default.TableRestaurant, null, tint = TealAccent) },
            title = { Text("Change Table") },
            text  = {
                if (tables.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(tables) { table ->
                            val isCurrent  = table.tableId == currentTableId
                            val isOccupied = table.tableStatus == "Occupied" && !isCurrent
                            OutlinedButton(
                                onClick   = {
                                    if (!isOccupied) {
                                        if (!isCurrent) vm.changeTable(selectedOrder!!.orderId, currentTableId!!, table.tableId)
                                        showTablePickerDialog = false
                                    }
                                },
                                enabled  = !isOccupied,
                                modifier = Modifier.fillMaxWidth(),
                                colors   = when {
                                    isCurrent  -> ButtonDefaults.outlinedButtonColors(containerColor = TealAccent.copy(alpha = 0.15f))
                                    isOccupied -> ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                    else       -> ButtonDefaults.outlinedButtonColors()
                                }
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(table.tableName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    when {
                                        isCurrent  -> Text("current", style = MaterialTheme.typography.labelSmall, color = TealAccent)
                                        isOccupied -> Text("Occupied", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                        else       -> Text("Available", style = MaterialTheme.typography.labelSmall, color = GreenSuccess)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton  = {},
            dismissButton  = { TextButton(onClick = { showTablePickerDialog = false }) { Text("Cancel") } }
        )
    }

    // KOT dialog: Kitchen Screen | Send to Printer | Route by Item Printer
    if (showKotSendModeDialog && selectedOrder != null) {
        AlertDialog(
            onDismissRequest = { showKotSendModeDialog = false },
            icon  = { Icon(Icons.Default.RestaurantMenu, null, tint = AmberWarning) },
            title = { Text("KOT") },
            text  = { Text("Choose how to send the kitchen order ticket.") },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.sendToKitchen(selectedOrder!!.orderId)
                            showKotSendModeDialog = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = AmberWarning)
                    ) {
                        Icon(Icons.Default.Tv, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Kitchen Screen")
                    }
                    if (kitchenPrinters.isNotEmpty()) {
                        OutlinedButton(
                            onClick  = { showKotSendModeDialog = false; showKotPrinterPickerDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Print, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Send to Printer")
                        }
                        OutlinedButton(
                            onClick  = {
                                vm.reprintKotByItemPrinter(kitchenPrinters)
                                showKotSendModeDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CallSplit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Route by Item Printer")
                        }
                    }
                    TextButton(onClick = { showKotSendModeDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
            },
            dismissButton = {}
        )
    }

    // KOT printer picker (shared by both flows above)
    if (showKotPrinterPickerDialog) {
        AlertDialog(
            onDismissRequest = { showKotPrinterPickerDialog = false },
            icon  = { Icon(Icons.Default.Print, null, tint = TealAccent) },
            title = { Text("Select Kitchen Printer") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    kitchenPrinters.forEach { cfg ->
                        OutlinedButton(
                            onClick   = { vm.reprintKot(cfg); showKotPrinterPickerDialog = false },
                            modifier  = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.RestaurantMenu, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${cfg.printerName}  (${cfg.ipAddress}:${cfg.port})")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showKotPrinterPickerDialog = false }) { Text("Cancel") } }
        )
    }

    // KOT preview dialog (thermal receipt style)
    if (kotPreviewGroups.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.clearKotPreview() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Receipt, null, tint = AmberWarning)
                    Text("KOT Preview", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(kotPreviewGroups) { group ->
                        KotReceiptSlip(group = group, onPrint = if (group.ip.isNotBlank()) {{ vm.printKotGroupNow(group) }} else null)
                    }
                }
            },
            confirmButton = {
                val printable = kotPreviewGroups.filter { it.ip.isNotBlank() }
                if (printable.isNotEmpty()) {
                    Button(
                        onClick = {
                            printable.forEach { vm.printKotGroupNow(it) }
                            vm.clearKotPreview()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning)
                    ) {
                        Icon(Icons.Default.Print, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Print All")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearKotPreview() }) { Text("Close") }
            }
        )
    }

    if (showReceiptPreview && previewOrder != null) {
        ReceiptPreviewSheet(
            order       = previewOrder!!,
            settings    = settings,
            payments    = previewPayments,
            isPrinting  = isPrinting,
            onPrintTo   = { vm.printFromPreview(it) },
            onDismiss   = { vm.dismissReceiptPreview() },
            printers    = availablePrinters
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                title = { Text("Orders") },
                actions = { IconButton(onClick = vm::loadOrders) { Icon(Icons.Default.Refresh, "Refresh") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { vm.setTab(0) }, text = { Text("Active") })
                Tab(selected = tab == 1, onClick = { vm.setTab(1) }, text = { Text("All Orders") })
            }

            // Tab-specific filter bar
            if (tab == 0) ActiveFilterBar(vm)
            else AllOrdersFilterBar(vm)

            // Summary strip for All Orders tab
            if (tab == 1 && !isLoading && orders.isNotEmpty()) {
                val completedSales = orders.filter { it.orderStatus == "Completed" }.sumOf { it.grandTotal }
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(
                        "${orders.size} order${if (orders.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (completedSales > 0) {
                        Text(
                            "Completed: ${completedSales.formatCurrency(settings.currencySymbol)}",
                            style      = MaterialTheme.typography.labelMedium,
                            color      = GreenSuccess,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = vm::loadOrders) { Text("Retry") }
                    }
                }
                orders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ReceiptLong, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No orders found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(orders) { order ->
                        OrderCard(order = order, currency = settings.currencySymbol, onClick = { vm.selectOrder(order) })
                    }
                }
            }
        }
    }
}

// ── Active tab: type filter chips ─────────────────────────────────────────────

@Composable
private fun ActiveFilterBar(vm: OrdersViewModel) {
    val typeFilter by vm.typeFilter.collectAsState()

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val countAll      by vm.countAll.collectAsState()
        val countDineIn   by vm.countDineIn.collectAsState()
        val countTakeaway by vm.countTakeaway.collectAsState()
        val countDelivery by vm.countDelivery.collectAsState()

        listOf(
            Triple("All",      countAll,      MaterialTheme.colorScheme.primary),
            Triple("DineIn",   countDineIn,   GreenSuccess),
            Triple("Takeaway", countTakeaway, AmberWarning),
            Triple("Delivery", countDelivery, MaterialTheme.colorScheme.primary)
        ).forEach { (label, count, color) ->
            val selected = typeFilter == label
            FilterChip(
                selected = selected,
                onClick  = { vm.setTypeFilter(label) },
                label    = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label)
                        Badge(
                            containerColor = if (selected) color else color.copy(alpha = 0.3f)
                        ) {
                            Text(count.toString(), style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else color)
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor     = color.copy(alpha = 0.15f),
                    selectedLabelColor         = color,
                    selectedLeadingIconColor   = color
                )
            )
        }
    }
}

// ── All Orders tab: date range + status + search ───────���──────────────────────

@Composable
private fun AllOrdersFilterBar(vm: OrdersViewModel) {
    val context      = LocalContext.current
    val fromDate     by vm.fromDate.collectAsState()
    val toDate       by vm.toDate.collectAsState()
    val statusFilter by vm.statusFilter.collectAsState()
    val search       by vm.search.collectAsState()
    var statusExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Row 0: quick range chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Today", "Yesterday", "This Week", "This Month").forEach { label ->
                FilterChip(
                    selected = false,
                    onClick  = { vm.setQuickRange(label) },
                    label    = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        // Row 1: date range pickers
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick  = {
                    val cal = Calendar.getInstance().apply { time = fromDate }
                    DatePickerDialog(context, { _, y, m, d ->
                        vm.setFromDate(Calendar.getInstance().apply {
                            set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                        }.time)
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(dateFmt.format(fromDate), style = MaterialTheme.typography.bodySmall)
            }
            Text("—", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick  = {
                    val cal = Calendar.getInstance().apply { time = toDate }
                    DatePickerDialog(context, { _, y, m, d ->
                        vm.setToDate(Calendar.getInstance().apply {
                            set(y, m, d, 23, 59, 59)
                        }.time)
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(dateFmt.format(toDate), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Row 2: status dropdown + search + apply
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Status dropdown
            Box(Modifier.weight(1f)) {
                OutlinedButton(
                    onClick  = { statusExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(statusFilter, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null, Modifier.size(16.dp))
                }
                DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                    vm.statusOptions.forEach { opt ->
                        DropdownMenuItem(
                            text    = { Text(opt) },
                            onClick = { vm.setStatusFilter(opt); statusExpanded = false },
                            leadingIcon = if (opt == statusFilter) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null
                        )
                    }
                }
            }

            // Search field
            OutlinedTextField(
                value         = search,
                onValueChange = vm::setSearch,
                modifier      = Modifier.weight(1.5f),
                placeholder   = { Text("Token / Order# / Customer", style = MaterialTheme.typography.bodySmall) },
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Default.Search, null, Modifier.size(16.dp)) },
                trailingIcon  = if (search.isNotBlank()) {{ IconButton(onClick = { vm.setSearch("") }) { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) } }} else null
            )

            // Apply button
            Button(onClick = vm::loadOrders) {
                Icon(Icons.Default.FilterList, null, Modifier.size(16.dp))
            }
        }
    }
}

// ── Order card ────────────���─────────────────────────��─────────────────────────

@Composable
private fun OrderCard(order: Order, currency: String, onClick: () -> Unit) {
    val statusColor = Color(order.orderStatus.orderStatusColor())
    val payColor    = Color(order.paymentStatus.paymentStatusColor())

    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        order.tokenNo,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            order.orderType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            maxLines = 1
                        )
                    }
                    if (order.orderStatus == "Ready") {
                        Surface(shape = RoundedCornerShape(4.dp), color = GreenSuccess) {
                            Text(
                                "READY",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                order.tableName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        order.createdAt.formatDateTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (order.itemCount > 0)
                        Text(
                            "· ${order.itemCount} item(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(order.grandTotal.formatCurrency(currency), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Badge(containerColor = statusColor) {
                        Text(order.orderStatus, style = MaterialTheme.typography.labelSmall,
                            color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    Badge(containerColor = payColor) {
                        Text(order.paymentStatus, style = MaterialTheme.typography.labelSmall,
                            color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
        }
    }
}

// ── Order detail bottom sheet ───────────���─────────────────────────────────────

@Composable
private fun OrderDetailSheet(
    order:             Order,
    items:             List<OrderItem>,
    currency:          String,
    isPrinting:        Boolean,
    onKot:             () -> Unit,
    onCancel:          () -> Unit,
    onBill:            () -> Unit,
    onPrint:           () -> Unit,
    onPreBill:         (() -> Unit)?                  = null,
    onResume:          (() -> Unit)?                  = null,
    onAddItems:        (() -> Unit)?                  = null,
    onVoid:            ((String) -> Unit)?            = null,
    onRefund:          ((String) -> Unit)?            = null,
    onReorder:         (() -> Unit)?                  = null,
    onDelete:          (() -> Unit)?                  = null,
    onRevert:          (() -> Unit)?                  = null,
    onChangeTable:     (() -> Unit)?                  = null,
    onRemoveItem:      ((Int) -> Unit)?               = null,
    onUpdateItemQty:   ((Int, Double) -> Unit)?       = null,
    onApplyDiscount:   ((Double) -> Unit)?            = null,
    onMarkDelivered:   (() -> Unit)?                  = null
) {
    var showCancelConfirm  by remember { mutableStateOf(false) }
    var showVoidDialog     by remember { mutableStateOf(false) }
    var showRefundDialog   by remember { mutableStateOf(false) }
    var showDiscountDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm  by remember { mutableStateOf(false) }
    var editingQtyItem     by remember { mutableStateOf<OrderItem?>(null) }
    var voidReason         by remember { mutableStateOf("") }
    var refundReason       by remember { mutableStateOf("") }
    var discountInput      by remember { mutableStateOf("") }
    var qtyInput           by remember { mutableStateOf("") }
    // "void" | "refund" | "delete" | "revert" — triggers manager credential dialog
    var overrideTarget     by remember { mutableStateOf<String?>(null) }
    val isPaid   = order.paymentStatus == "Paid"
    val isHeld   = order.orderStatus == "Held"

    overrideTarget?.let { target ->
        val actionDesc = when (target) {
            "void"   -> "Void Order ${order.orderNo}"
            "refund" -> "Refund Order ${order.orderNo}"
            "delete" -> "Delete Order ${order.orderNo}"
            "revert" -> "Revert Order ${order.orderNo} to Pending"
            else     -> target
        }
        ManagerOverrideDialog(
            action     = actionDesc,
            onVerified = {
                when (target) {
                    "void"   -> showVoidDialog    = true
                    "refund" -> showRefundDialog   = true
                    "delete" -> showDeleteConfirm  = true
                    "revert" -> onRevert?.invoke()
                }
                overrideTarget = null
            },
            onDismiss  = { overrideTarget = null }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title  = { Text("Cancel Order") },
            text   = { Text("Cancel order ${order.tokenNo}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { showCancelConfirm = false; onCancel() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = RedError)
                ) { Text("Yes, Cancel") }
            },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("Keep") } }
        )
    }

    if (showVoidDialog) {
        AlertDialog(
            onDismissRequest = { showVoidDialog = false },
            title = { Text("Void Order") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Order ${order.tokenNo} will be marked Void.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = voidReason, onValueChange = { voidReason = it },
                        label = { Text("Reason") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onVoid?.invoke(voidReason); showVoidDialog = false; voidReason = "" },
                    colors  = ButtonDefaults.textButtonColors(contentColor = RedError)
                ) { Text("Void Order") }
            },
            dismissButton = { TextButton(onClick = { showVoidDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRefundDialog) {
        AlertDialog(
            onDismissRequest = { showRefundDialog = false },
            title = { Text("Refund Order") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Order ${order.tokenNo} (${order.grandTotal.formatCurrency(currency)}) will be marked as Refunded.",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = refundReason, onValueChange = { refundReason = it },
                        label = { Text("Reason") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onRefund?.invoke(refundReason); showRefundDialog = false; refundReason = "" },
                    colors  = ButtonDefaults.textButtonColors(contentColor = AmberWarning)
                ) { Text("Confirm Refund") }
            },
            dismissButton = { TextButton(onClick = { showRefundDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon  = { Icon(Icons.Default.DeleteForever, null, tint = RedError) },
            title = { Text("Permanently Delete Order") },
            text  = {
                Text(
                    "Order ${order.tokenNo} and all its data will be permanently removed from the database. This CANNOT be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete?.invoke() },
                    colors  = ButtonDefaults.textButtonColors(contentColor = RedError)
                ) { Text("Delete Permanently") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep") } }
        )
    }

    if (showDiscountDialog) {
        AlertDialog(
            onDismissRequest = { showDiscountDialog = false; discountInput = "" },
            title = { Text("Apply Discount") },
            text  = {
                OutlinedTextField(
                    value = discountInput,
                    onValueChange = { discountInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Discount Amount ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = discountInput.toDoubleOrNull() ?: 0.0
                    onApplyDiscount?.invoke(amt)
                    showDiscountDialog = false
                    discountInput = ""
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showDiscountDialog = false; discountInput = "" }) { Text("Cancel") } }
        )
    }

    if (editingQtyItem != null) {
        AlertDialog(
            onDismissRequest = { editingQtyItem = null; qtyInput = "" },
            title = { Text("Update Quantity") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(editingQtyItem!!.productNameSnapshot, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { qtyInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("New Quantity (0 to remove)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val qty = qtyInput.toDoubleOrNull() ?: return@TextButton
                    onUpdateItemQty?.invoke(editingQtyItem!!.orderItemId, qty)
                    editingQtyItem = null
                    qtyInput = ""
                }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { editingQtyItem = null; qtyInput = "" }) { Text("Cancel") } }
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Drag handle
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(36.dp, 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )

        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(order.tokenNo, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    AssistChip(onClick = {}, label = { Text(order.orderType) })
                }
                order.tableName?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                order.waiterName?.takeIf { it.isNotBlank() }?.let {
                    Text("Waiter: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                order.customerName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (order.orderType == "Delivery") {
                    order.deliveryName?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    order.deliveryPhone?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    order.deliveryAddress?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
                order.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AmberWarning, fontStyle = FontStyle.Italic)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(order.orderNo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(order.createdAt.formatDateTime(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDivider()

        // Items table header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ITEM",  Modifier.weight(1f),  style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            Text("QTY",   Modifier.width(40.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            Text("PRICE", Modifier.width(72.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
            Text("TOTAL", Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
            if (onRemoveItem != null) Spacer(Modifier.width(32.dp))
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.productNameSnapshot, style = MaterialTheme.typography.bodyMedium)
                        item.sizeNameSnapshot?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item.notes?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                        }
                    }
                    val qtyText = item.quantity.let {
                        if (it == it.toLong().toDouble()) it.toLong().toString() else "%.2f".format(it)
                    }
                    if (onUpdateItemQty != null) {
                        Surface(
                            onClick = { editingQtyItem = item; qtyInput = qtyText },
                            shape   = MaterialTheme.shapes.extraSmall,
                            color   = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.width(40.dp)
                        ) {
                            Text(qtyText, Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                    } else {
                        Text(qtyText, Modifier.width(40.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                    Text(item.unitPrice.formatCurrency(currency), Modifier.width(72.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                    Text(item.lineTotal.formatCurrency(currency), Modifier.width(80.dp),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
                    if (onRemoveItem != null) {
                        IconButton(onClick = { onRemoveItem.invoke(item.orderItemId) },
                            modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.RemoveCircleOutline, null,
                                modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        // Totals
        @Composable
        fun TotalRow(label: String, value: Double, bold: Boolean = false, color: Color = MaterialTheme.colorScheme.onSurface) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label,
                    style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
                Text(value.formatCurrency(currency),
                    style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
            }
        }

        if (order.subTotal > 0 && order.subTotal != order.grandTotal) TotalRow("Sub Total", order.subTotal)
        if (order.discountAmount > 0) TotalRow("Discount", -order.discountAmount, color = RedError)
        if (order.taxAmount > 0)      TotalRow("Tax (${order.taxPercent.toInt()}%)", order.taxAmount)
        if (order.serviceCharges > 0) TotalRow("Service Charge", order.serviceCharges)
        if (order.deliveryCharge > 0) TotalRow("Delivery Charge", order.deliveryCharge)
        if (order.tips > 0)           TotalRow("Tip", order.tips)
        HorizontalDivider()
        TotalRow("GRAND TOTAL", order.grandTotal + order.tips, bold = true, color = MaterialTheme.colorScheme.primary)
        if (order.paidAmount > 0 && !isPaid) {
            TotalRow("Paid", order.paidAmount, color = GreenSuccess)
            TotalRow("Balance Due", order.balanceAmount, bold = true, color = RedError)
        }

        Spacer(Modifier.height(4.dp))

        // Action buttons
        if (isHeld && onResume != null) {
            Button(
                onClick  = onResume,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AmberWarning)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Resume Order", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick  = { showCancelConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedError)
            ) {
                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel Held Order")
            }
        } else if (isPaid) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = GreenSuccess, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Order Paid", style = MaterialTheme.typography.titleMedium,
                        color = GreenSuccess, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick  = onPrint,
                    enabled  = !isPrinting,
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isPrinting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Print, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Reprint") }
                }
            }
            // Reorder / Void / Refund row
            if (onReorder != null || onVoid != null || onRefund != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onReorder != null) {
                        OutlinedButton(
                            onClick  = onReorder,
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess)
                        ) {
                            Icon(Icons.Default.Replay, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reorder")
                        }
                    }
                    if (onVoid != null) {
                        OutlinedButton(
                            onClick  = { overrideTarget = "void" },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedError)
                        ) {
                            Icon(Icons.Default.Block, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Void")
                        }
                    }
                    if (onRefund != null) {
                        OutlinedButton(
                            onClick  = { overrideTarget = "refund" },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning)
                        ) {
                            Icon(Icons.Default.Undo, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Refund")
                        }
                    }
                }
            }
            if (onRevert != null || onDelete != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onRevert != null) {
                        OutlinedButton(
                            onClick  = { overrideTarget = "revert" },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = BlueInfo)
                        ) {
                            Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Revert")
                        }
                    }
                    if (onDelete != null) {
                        OutlinedButton(
                            onClick  = { overrideTarget = "delete" },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedError)
                        ) {
                            Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        } else {
            if (onApplyDiscount != null || onAddItems != null || onChangeTable != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onApplyDiscount != null) {
                        OutlinedButton(
                            onClick  = { discountInput = order.discountAmount.let { if (it > 0) "%.2f".format(it) else "" }; showDiscountDialog = true },
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning)
                        ) {
                            Icon(Icons.Default.Discount, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Discount")
                        }
                    }
                    if (onChangeTable != null) {
                        OutlinedButton(
                            onClick  = onChangeTable,
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.outlinedButtonColors(contentColor = TealAccent)
                        ) {
                            Icon(Icons.Default.TableRestaurant, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Table")
                        }
                    }
                    if (onAddItems != null) {
                        Button(
                            onClick  = onAddItems,
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.buttonColors(containerColor = AmberWarning)
                        ) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Order", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick  = { showCancelConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedError)
                ) {
                    Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cancel")
                }
                OutlinedButton(
                    onClick  = onKot,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning)
                ) {
                    Icon(Icons.Default.Restaurant, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("KOT")
                }
                if (onMarkDelivered != null) {
                    // Third-party delivery: Mark Delivered instead of Bill (WPF behaviour)
                    Button(
                        onClick  = onMarkDelivered,
                        modifier = Modifier.weight(1.4f),
                        colors   = ButtonDefaults.buttonColors(containerColor = BlueInfo)
                    ) {
                        Icon(Icons.Default.DeliveryDining, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Mark Delivered", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick  = onBill,
                        modifier = Modifier.weight(1.4f),
                        colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                    ) {
                        Icon(Icons.Default.Receipt, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Bill", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (onPreBill != null) {
                OutlinedButton(
                    onClick  = onPreBill,
                    enabled  = !isPrinting,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isPrinting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Default.ReceiptLong, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Print Pre-Bill")
                    }
                }
            }
            if (onDelete != null) {
                OutlinedButton(
                    onClick  = { overrideTarget = "delete" },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedError)
                ) {
                    Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Permanently Delete")
                }
            }
        }
    }
}

// ── KOT thermal receipt slip ──────────────────────────────────────────────────

@Composable
private fun KotReceiptSlip(group: KotPreviewGroup, onPrint: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (group.companyName.isNotBlank()) {
            Text(group.companyName, color = Color.Black, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Text("** KITCHEN ORDER **", color = Color.Black, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        KotSlipDivider()

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Token#:", color = Color.Black, fontWeight = FontWeight.Medium)
            Text(group.tokenNo.ifBlank { "-" }, color = Color.Black, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Order#:", color = Color.Black)
            Text(group.orderNo, color = Color.Black)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Type:", color = Color.Black)
            Text(group.orderType, color = Color.Black)
        }
        if (group.tableName.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Table:", color = Color.Black)
                Text(group.tableName, color = Color.Black)
            }
        }
        if (group.waiterName.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Waiter:", color = Color.Black)
                Text(group.waiterName, color = Color.Black)
            }
        }
        if (group.customerName.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Customer:", color = Color.Black)
                Text(group.customerName, color = Color.Black)
            }
        }

        KotSlipDivider()

        Row(Modifier.fillMaxWidth()) {
            Text("Qty", Modifier.width(40.dp), color = Color.Black, fontWeight = FontWeight.Bold)
            Text("Item", Modifier.weight(1f), color = Color.Black, fontWeight = FontWeight.Bold)
        }
        KotSlipDivider(dashed = true)

        group.items.forEach { item ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text("x${"%.0f".format(item.quantity.toDouble())}", Modifier.width(40.dp),
                    color = Color.Black, fontWeight = FontWeight.Bold)
                Column(Modifier.weight(1f)) {
                    Text(buildString {
                        append(item.productName)
                        item.sizeName?.takeIf { it.isNotBlank() }?.let { append(" [$it]") }
                    }, color = Color.Black)
                    item.selectedModifiers.forEach { mod ->
                        Text("  + ${mod.modifierName}", color = Color.DarkGray)
                    }
                    item.notes.takeIf { it.isNotBlank() }?.let {
                        Text("  * $it", color = Color.Gray)
                    }
                }
            }
        }

        if (group.notes.isNotBlank()) {
            KotSlipDivider(dashed = true)
            Text("Note: ${group.notes}", color = Color.Black, modifier = Modifier.fillMaxWidth())
        }

        KotSlipDivider()

        if (group.printerName.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Print, null, Modifier.size(14.dp), tint = AmberWarning)
                Text(group.printerName, color = Color.Gray)
            }
        }

        if (onPrint != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onPrint,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Print, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Print this slip")
            }
        }
    }
}

@Composable
private fun KotSlipDivider(dashed: Boolean = false) {
    val text = if (dashed) "- - - - - - - - - - - - - - - - - - - - - -"
               else        "─────────────────────────────────────────────"
    Text(text, color = Color.Black, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
}
