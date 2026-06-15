package com.fastpos.android.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.CartItem
import com.fastpos.android.data.models.KitchenPrinterConfig
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.OrderItem
import com.fastpos.android.data.models.PaymentEntry
import com.fastpos.android.data.models.PrinterOption
import com.fastpos.android.data.models.RestaurantTable
import com.fastpos.android.data.models.SelectedModifier
import com.fastpos.android.data.repositories.OrderRepository
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.utils.NetworkPrinterHelper
import com.fastpos.android.utils.PosCartBridge
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepo: OrderRepository,
    private val prefs:     PreferencesManager,
    private val bridge:    PosCartBridge,
    val session:           SessionManager
) : ViewModel() {

    private val _isPrinting          = MutableStateFlow(false)
    private val _navigateToPos       = MutableStateFlow(false)
    private val _showReceiptPreview  = MutableStateFlow(false)
    private val _previewOrder        = MutableStateFlow<Order?>(null)
    private val _previewPayments     = MutableStateFlow<List<PaymentEntry>>(emptyList())
    private val _availablePrinters   = MutableStateFlow<List<PrinterOption>>(emptyList())
    private val _kotPreviewGroups    = MutableStateFlow<List<KotPreviewGroup>>(emptyList())

    val isPrinting:         StateFlow<Boolean>              = _isPrinting
    val navigateToPos:      StateFlow<Boolean>              = _navigateToPos
    val showReceiptPreview: StateFlow<Boolean>              = _showReceiptPreview
    val previewOrder:       StateFlow<Order?>               = _previewOrder
    val previewPayments:    StateFlow<List<PaymentEntry>>   = _previewPayments
    val availablePrinters:  StateFlow<List<PrinterOption>>  = _availablePrinters
    val kotPreviewGroups:   StateFlow<List<KotPreviewGroup>> = _kotPreviewGroups

    val kotPrintMode: Flow<String> = prefs.kotPrintMode

    fun clearKotPreview() { _kotPreviewGroups.value = emptyList() }

    fun showKotPreviewAll() {
        val order = _selectedOrder.value ?: return
        val items = _orderItems.value
        if (items.isEmpty()) { _message.value = "No items to print."; return }
        viewModelScope.launch {
            if (prefs.kotPrintMode.first() == "Off") { _message.value = "KOT print is disabled."; return@launch }
            val cartItems = items.map { oi ->
                CartItem(
                    productId  = oi.productId,
                    productName = oi.productNameSnapshot,
                    sizeId     = oi.sizeId,
                    sizeName   = oi.sizeNameSnapshot,
                    unitPrice  = oi.unitPrice,
                    quantity   = oi.quantity.toInt().coerceAtLeast(1),
                    notes      = oi.notes ?: ""
                )
            }
            val settings = session.settings.value
            _kotPreviewGroups.value = listOf(
                KotPreviewGroup(
                    printerName  = "KOT Preview",
                    ip           = "",
                    port         = 9100,
                    paperType    = null,
                    orderNo      = order.orderNo,
                    tokenNo      = order.tokenNo,
                    orderType    = order.orderType,
                    tableName    = order.tableName ?: "",
                    waiterName   = order.waiterName ?: "",
                    customerName = order.customerName ?: "",
                    notes        = order.notes ?: "",
                    items        = cartItems,
                    companyName  = settings.companyName,
                    logoData     = settings.logoData
                )
            )
        }
    }

    fun printKotGroupNow(cfg: KotPreviewGroup) {
        viewModelScope.launch {
            NetworkPrinterHelper.printKitchenTicket(
                ip           = cfg.ip,
                port         = cfg.port,
                orderNo      = cfg.orderNo,
                tokenNo      = cfg.tokenNo,
                orderType    = cfg.orderType,
                tableName    = cfg.tableName,
                items        = cfg.items,
                stationName  = cfg.printerName,
                companyName  = cfg.companyName,
                waiterName   = cfg.waiterName,
                notes        = cfg.notes,
                customerName = cfg.customerName,
                paperType    = cfg.paperType,
                logoData     = cfg.logoData
            )
        }
    }

    val kitchenPrinters = prefs.kitchenPrinters

    private val _orders        = MutableStateFlow<List<Order>>(emptyList())
    private val _allRaw        = MutableStateFlow<List<Order>>(emptyList()) // unfiltered for active tab counts
    private val _isLoading     = MutableStateFlow(false)
    private val _error         = MutableStateFlow<String?>(null)
    private val _tab           = MutableStateFlow(0)
    private val _message       = MutableStateFlow<String?>(null)
    private val _selectedOrder = MutableStateFlow<Order?>(null)
    private val _orderItems    = MutableStateFlow<List<OrderItem>>(emptyList())
    private val _tables        = MutableStateFlow<List<RestaurantTable>>(emptyList())

    // ── Active tab filters ──────────────────────────────────────────────────
    private val _typeFilter    = MutableStateFlow("All") // All / DineIn / Takeaway / Delivery

    // ── All Orders tab filters ──────────────────────────────────────────────
    private val _fromDate      = MutableStateFlow(todayStart())
    private val _toDate        = MutableStateFlow(Date())
    private val _statusFilter  = MutableStateFlow("All")
    private val _search        = MutableStateFlow("")

    val orders:        StateFlow<List<Order>>     = _orders
    val isLoading:     StateFlow<Boolean>         = _isLoading
    val error:         StateFlow<String?>         = _error
    val tab:           StateFlow<Int>             = _tab
    val message:       StateFlow<String?>         = _message
    val selectedOrder: StateFlow<Order?>          = _selectedOrder
    val orderItems:    StateFlow<List<OrderItem>> = _orderItems
    val tables:        StateFlow<List<RestaurantTable>> = _tables
    val typeFilter:    StateFlow<String>          = _typeFilter
    val fromDate:      StateFlow<Date>            = _fromDate
    val toDate:        StateFlow<Date>            = _toDate
    val statusFilter:  StateFlow<String>          = _statusFilter
    val search:        StateFlow<String>          = _search

    // Counts derived from _allRaw for Active tab chips — StateFlow so Compose reacts to changes
    val countAll:      StateFlow<Int> = _allRaw.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val countDineIn:   StateFlow<Int> = _allRaw.map { it.count { o -> o.orderType == "DineIn" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val countTakeaway: StateFlow<Int> = _allRaw.map { it.count { o -> o.orderType == "Takeaway" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val countDelivery: StateFlow<Int> = _allRaw.map { it.count { o -> o.orderType == "Delivery" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val statusOptions = listOf("All", "New", "SentToKitchen", "Ready", "Held", "Completed", "Cancelled", "Void", "Refunded")

    init {
        loadOrders()
        loadAvailablePrinters()
        viewModelScope.launch {
            while (isActive) {
                delay(session.pollIntervalMs)
                if (_tab.value == 0 && !_isLoading.value) {
                    try {
                        val pending = orderRepo.getPendingOrders()
                        _allRaw.value = pending
                        applyTypeFilter()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun setTab(index: Int) {
        _tab.value = index
        loadOrders()
    }

    fun setTypeFilter(type: String) {
        _typeFilter.value = type
        applyTypeFilter()
    }

    fun setFromDate(date: Date)     { _fromDate.value = date }
    fun setToDate(date: Date)       { _toDate.value = date }
    fun setStatusFilter(s: String)  { _statusFilter.value = s }
    fun setSearch(s: String)        { _search.value = s }

    fun setQuickRange(label: String) {
        val cal = Calendar.getInstance()
        when (label) {
            "Today"     -> { _fromDate.value = todayStart(); _toDate.value = Date() }
            "Yesterday" -> {
                cal.add(Calendar.DAY_OF_YEAR, -1)
                _fromDate.value = dayStart(cal.time); _toDate.value = dayEnd(cal.time)
            }
            "This Week" -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                _fromDate.value = dayStart(cal.time); _toDate.value = Date()
            }
            "This Month" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                _fromDate.value = dayStart(cal.time); _toDate.value = Date()
            }
        }
        loadOrders()
    }

    private fun dayStart(d: Date) = Calendar.getInstance().also {
        it.time = d; it.set(Calendar.HOUR_OF_DAY, 0); it.set(Calendar.MINUTE, 0); it.set(Calendar.SECOND, 0)
    }.time

    private fun dayEnd(d: Date) = Calendar.getInstance().also {
        it.time = d; it.set(Calendar.HOUR_OF_DAY, 23); it.set(Calendar.MINUTE, 59); it.set(Calendar.SECOND, 59)
    }.time

    fun loadOrders() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                if (_tab.value == 0) {
                    val pending = orderRepo.getPendingOrders()
                    _allRaw.value = pending
                    applyTypeFilter()
                } else {
                    _orders.value = orderRepo.getAllOrders(
                        fromDate = _fromDate.value,
                        toDate   = _toDate.value,
                        status   = _statusFilter.value,
                        search   = _search.value
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load orders."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun applyTypeFilter() {
        _orders.value = if (_typeFilter.value == "All") _allRaw.value
                        else _allRaw.value.filter { it.orderType == _typeFilter.value }
    }

    fun selectOrder(order: Order?) {
        _selectedOrder.value = order
        if (order != null) loadItems(order.orderId)
        else _orderItems.value = emptyList()
    }

    private fun loadItems(orderId: Int) {
        viewModelScope.launch {
            try { _orderItems.value = orderRepo.getOrderItems(orderId) }
            catch (_: Exception) { _orderItems.value = emptyList() }
        }
    }

    fun sendToKitchen(orderId: Int) {
        viewModelScope.launch {
            try {
                orderRepo.updateOrderStatus(orderId, "SentToKitchen")
                _allRaw.value = _allRaw.value.map { o ->
                    if (o.orderId == orderId) o.copy(orderStatus = "SentToKitchen") else o
                }
                _orders.value = _orders.value.map { o ->
                    if (o.orderId == orderId) o.copy(orderStatus = "SentToKitchen") else o
                }
                _selectedOrder.value = _selectedOrder.value?.let {
                    if (it.orderId == orderId) it.copy(orderStatus = "SentToKitchen") else it
                }
                _message.value = "KOT sent to kitchen."
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun cancelOrder(orderId: Int) {
        viewModelScope.launch {
            try {
                orderRepo.cancelActiveOrder(orderId)
                _allRaw.value = _allRaw.value.filter { it.orderId != orderId }
                _orders.value = _orders.value.filter { it.orderId != orderId }
                if (_selectedOrder.value?.orderId == orderId) {
                    _selectedOrder.value = null
                    _orderItems.value = emptyList()
                }
                _message.value = "Order cancelled."
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun loadAvailablePrinters() {
        viewModelScope.launch {
            val printers  = mutableListOf<PrinterOption>()
            val btAddress = prefs.savedPrinterAddress.first()
            val btName    = prefs.savedPrinterName.first()
            val netIp     = prefs.receiptNetIp.first()
            val netPort   = prefs.receiptNetPort.first()
            val netName   = prefs.receiptNetName.first()
            if (btAddress.isNotBlank())
                printers.add(PrinterOption(btName.ifBlank { "Bluetooth Printer" }, "Bluetooth", address = btAddress))
            if (netIp.isNotBlank())
                printers.add(PrinterOption(netName.ifBlank { "Network Printer" }, "Network", ip = netIp, port = netPort))
            _availablePrinters.value = printers
        }
    }

    fun dismissReceiptPreview() {
        _showReceiptPreview.value = false
        _previewOrder.value = null
        _previewPayments.value = emptyList()
    }

    fun printFromPreview(printer: PrinterOption) {
        val order = _previewOrder.value ?: return
        viewModelScope.launch {
            _isPrinting.value = true
            _showReceiptPreview.value = false
            val settings     = session.settings.value
            val lineWidth    = prefs.paperWidth.first()
            val payments     = _previewPayments.value
            val netPaperType = prefs.receiptNetPaperType.first()
            val logoData     = settings.logoData
            val result = if (printer.type == "Network") {
                NetworkPrinterHelper.printReceipt(printer.ip, printer.port, order, settings,
                    payments = payments, lineWidth = lineWidth, paperType = netPaperType,
                    logoData = logoData)
            } else {
                BluetoothPrinterHelper.printReceipt(printer.address, order, settings,
                    payments = payments, lineWidth = lineWidth, logoData = logoData)
            }
            result.onSuccess { _message.value = "Receipt printed." }
                  .onFailure { _message.value = "Print failed: ${it.message}" }
            _isPrinting.value = false
            _previewOrder.value = null
            _previewPayments.value = emptyList()
        }
    }

    fun printReceipt(context: Context) {
        val order = _selectedOrder.value ?: return
        viewModelScope.launch {
            val billMode       = prefs.billPrintMode.first()
            val prType         = prefs.receiptPrinterType.first()
            val btAddress      = prefs.savedPrinterAddress.first()
            val netIp          = prefs.receiptNetIp.first()
            val netPort        = prefs.receiptNetPort.first()
            val settings       = session.settings.value
            val orderWithItems = order.copy(items = _orderItems.value)
            val payments       = orderRepo.getOrderPayments(order.orderId)

            if (billMode == "Preview") {
                _previewOrder.value    = orderWithItems
                _previewPayments.value = payments
                _showReceiptPreview.value = true
                return@launch
            }

            _isPrinting.value = true
            val netPaperType = prefs.receiptNetPaperType.first()
            val logoData     = settings.logoData
            val result = if (prType == "Network" && netIp.isNotBlank()) {
                NetworkPrinterHelper.printReceipt(netIp, netPort, orderWithItems, settings,
                    payments = payments, paperType = netPaperType, logoData = logoData)
            } else {
                if (btAddress.isBlank()) { _message.value = "No printer configured."; _isPrinting.value = false; return@launch }
                BluetoothPrinterHelper.printReceipt(btAddress, orderWithItems, settings,
                    payments = payments, logoData = logoData)
            }
            result.onSuccess { _message.value = "Receipt printed." }
                  .onFailure { _message.value = "Print failed: ${it.message}" }
            _isPrinting.value = false
        }
    }

    fun printPreBill(context: Context) {
        val order = _selectedOrder.value ?: return
        viewModelScope.launch {
            val billMode       = prefs.billPrintMode.first()
            val orderWithItems = order.copy(items = _orderItems.value)

            if (billMode == "Preview") {
                _previewOrder.value    = orderWithItems
                _previewPayments.value = emptyList()
                _showReceiptPreview.value = true
                return@launch
            }

            _isPrinting.value  = true
            val prType         = prefs.receiptPrinterType.first()
            val btAddress      = prefs.savedPrinterAddress.first()
            val netIp          = prefs.receiptNetIp.first()
            val netPort        = prefs.receiptNetPort.first()
            val settings       = session.settings.value
            val lineWidth      = prefs.paperWidth.first()
            val netPaperType   = prefs.receiptNetPaperType.first()
            val logoData       = settings.logoData
            val result = if (prType == "Network" && netIp.isNotBlank()) {
                NetworkPrinterHelper.printPreBill(netIp, netPort, orderWithItems, settings, lineWidth, netPaperType,
                    logoData = logoData)
            } else {
                if (btAddress.isBlank()) { _message.value = "No printer configured."; _isPrinting.value = false; return@launch }
                BluetoothPrinterHelper.printPreBill(btAddress, orderWithItems, settings, logoData = logoData)
            }
            result.onSuccess { _message.value = "Pre-bill printed." }
                  .onFailure { _message.value = "Print failed: ${it.message}" }
            _isPrinting.value = false
        }
    }

    fun markDelivered(orderId: Int) {
        viewModelScope.launch {
            try {
                orderRepo.markDelivered(orderId)
                _message.value = "Order marked as delivered."
                _selectedOrder.value = null
                _orderItems.value    = emptyList()
                loadOrders()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun voidOrder(orderId: Int, reason: String) {
        viewModelScope.launch {
            try {
                orderRepo.voidOrder(orderId, reason)
                _message.value = "Order voided."
                _selectedOrder.value = null
                _orderItems.value    = emptyList()
                loadOrders()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun refundOrder(orderId: Int, reason: String) {
        viewModelScope.launch {
            try {
                orderRepo.refundOrder(orderId, reason)
                _message.value = "Order refunded."
                _selectedOrder.value = null
                _orderItems.value    = emptyList()
                loadOrders()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun reorderFromHistory(orderId: Int) {
        viewModelScope.launch {
            try {
                val items = orderRepo.getOrderItems(orderId)
                if (items.isEmpty()) { _message.value = "No items in this order."; return@launch }
                bridge.pendingCart = items.map { oi ->
                    CartItem(
                        productId   = oi.productId,
                        productName = oi.productNameSnapshot,
                        sizeId      = oi.sizeId,
                        sizeName    = oi.sizeNameSnapshot,
                        unitPrice   = oi.unitPrice,
                        quantity    = oi.quantity.toInt().coerceAtLeast(1),
                        notes       = oi.notes ?: ""
                    )
                }
                _selectedOrder.value = null
                _navigateToPos.value = true
            } catch (e: Exception) { _message.value = "Reorder failed: ${e.message}" }
        }
    }

    fun startAddToOrder(orderId: Int, orderNo: String) {
        bridge.pendingAddToOrderId = orderId
        bridge.pendingAddToOrderNo = orderNo
        _navigateToPos.value = true
    }

    fun startEditOrder(orderId: Int, orderNo: String) {
        viewModelScope.launch {
            try {
                val cartItems = orderRepo.getCartItemsForOrderWithModifiers(orderId)
                bridge.pendingCart = cartItems
                bridge.pendingEditOrderId = orderId
                bridge.pendingEditOrderNo = orderNo
                _selectedOrder.value = null
                _navigateToPos.value = true
            } catch (e: Exception) {
                _message.value = "Failed to load order: ${e.message}"
            }
        }
    }

    fun resumeHeldOrder(orderId: Int) {
        viewModelScope.launch {
            try {
                val cartItems = orderRepo.getCartItemsForOrder(orderId)
                orderRepo.updateOrderStatus(orderId, "Cancelled")
                orderRepo.deleteHeldOrderRecord(orderId)
                bridge.pendingCart = cartItems
                _allRaw.value  = _allRaw.value.filter { it.orderId != orderId }
                _orders.value  = _orders.value.filter { it.orderId != orderId }
                _selectedOrder.value = null
                _orderItems.value    = emptyList()
                _navigateToPos.value = true
            } catch (e: Exception) {
                _message.value = "Failed to resume: ${e.message}"
            }
        }
    }

    fun reprintKot(cfg: KitchenPrinterConfig) {
        val order = _selectedOrder.value ?: return
        val items = _orderItems.value
        if (items.isEmpty()) { _message.value = "No items to print."; return }
        viewModelScope.launch {
            val kotMode = prefs.kotPrintMode.first()
            if (kotMode == "Off") { _message.value = "KOT print is disabled."; return@launch }
            val cartItems = items.map { oi ->
                CartItem(
                    productId         = oi.productId,
                    productName       = oi.productNameSnapshot,
                    sizeId            = oi.sizeId,
                    sizeName          = oi.sizeNameSnapshot,
                    unitPrice         = oi.unitPrice,
                    quantity          = oi.quantity.toInt().coerceAtLeast(1),
                    notes             = oi.notes ?: "",
                    selectedModifiers = oi.modifiers.map { m ->
                        SelectedModifier(m.modifierId, m.modifierNameSnapshot, m.extraPrice)
                    }
                )
            }
            if (kotMode == "Preview") {
                _kotPreviewGroups.value = listOf(
                    KotPreviewGroup(
                        printerName  = cfg.printerName,
                        ip           = cfg.ipAddress,
                        port         = cfg.port,
                        paperType    = cfg.paperType,
                        items        = cartItems,
                        orderNo      = order.orderNo,
                        tokenNo      = order.tokenNo,
                        orderType    = order.orderType,
                        tableName    = order.tableName ?: "",
                        waiterName   = order.waiterName ?: "",
                        notes        = order.notes ?: "",
                        customerName = order.customerName ?: "",
                        companyName  = session.settings.value.companyName,
                        logoData     = session.settings.value.logoData
                    )
                )
                return@launch
            }
            _isPrinting.value = true
            val result = NetworkPrinterHelper.printKitchenTicket(
                ip           = cfg.ipAddress,
                port         = cfg.port,
                orderNo      = order.orderNo,
                tokenNo      = order.tokenNo,
                orderType    = order.orderType,
                tableName    = order.tableName,
                items        = cartItems,
                stationName  = cfg.printerName,
                companyName  = session.settings.value.companyName,
                waiterName   = order.waiterName,
                notes        = order.notes,
                customerName = order.customerName,
                paperType    = cfg.paperType,
                logoData     = session.settings.value.logoData
            )
            result.onSuccess { _message.value = "KOT sent to ${cfg.printerName}." }
                  .onFailure { _message.value = "Print failed: ${it.message}" }
            _isPrinting.value = false
        }
    }

    fun reprintKotByItemPrinter(printers: List<KitchenPrinterConfig>) {
        val order = _selectedOrder.value ?: return
        val items = _orderItems.value
        if (items.isEmpty()) { _message.value = "No items to print."; return }
        val configMap = printers.associateBy { it.printerName.lowercase() }
        viewModelScope.launch {
            val kotMode = prefs.kotPrintMode.first()
            if (kotMode == "Off") { _message.value = "KOT print is disabled."; return@launch }
            val groups = items.groupBy { it.printerName.trim() }.mapNotNull { (printerName, group) ->
                if (printerName.isBlank()) return@mapNotNull null
                val cfg = configMap[printerName.lowercase()] ?: return@mapNotNull null
                val cartItems = group.map { oi ->
                    CartItem(
                        productId         = oi.productId,
                        productName       = oi.productNameSnapshot,
                        sizeId            = oi.sizeId,
                        sizeName          = oi.sizeNameSnapshot,
                        unitPrice         = oi.unitPrice,
                        quantity          = oi.quantity.toInt().coerceAtLeast(1),
                        notes             = oi.notes ?: "",
                        selectedModifiers = oi.modifiers.map { m ->
                            SelectedModifier(m.modifierId, m.modifierNameSnapshot, m.extraPrice)
                        }
                    )
                }
                KotPreviewGroup(
                    printerName  = cfg.printerName,
                    ip           = cfg.ipAddress,
                    port         = cfg.port,
                    paperType    = cfg.paperType,
                    items        = cartItems,
                    orderNo      = order.orderNo,
                    tokenNo      = order.tokenNo,
                    orderType    = order.orderType,
                    tableName    = order.tableName ?: "",
                    waiterName   = order.waiterName ?: "",
                    notes        = order.notes ?: "",
                    customerName = order.customerName ?: "",
                    companyName  = session.settings.value.companyName,
                    logoData     = session.settings.value.logoData
                )
            }
            if (groups.isEmpty()) { _message.value = "No matching kitchen printers found."; return@launch }
            if (kotMode == "Preview") {
                _kotPreviewGroups.value = groups
                return@launch
            }
            _isPrinting.value = true
            var successCount = 0
            var failCount    = 0
            groups.forEach { g ->
                val result = NetworkPrinterHelper.printKitchenTicket(
                    ip           = g.ip,
                    port         = g.port,
                    orderNo      = g.orderNo,
                    tokenNo      = g.tokenNo,
                    orderType    = g.orderType,
                    tableName    = g.tableName,
                    items        = g.items,
                    stationName  = g.printerName,
                    companyName  = g.companyName,
                    waiterName   = g.waiterName,
                    notes        = g.notes,
                    customerName = g.customerName,
                    paperType    = g.paperType,
                    logoData     = g.logoData
                )
                if (result.isSuccess) successCount++ else failCount++
            }
            _message.value = when {
                failCount == 0 && successCount > 0 -> "KOT routed to $successCount printer(s)."
                successCount == 0                  -> "KOT routing failed — check printer configs."
                else                               -> "KOT sent to $successCount printer(s); $failCount failed."
            }
            _isPrinting.value = false
        }
    }

    fun removeOrderItem(orderItemId: Int) {
        val orderId = _selectedOrder.value?.orderId ?: return
        viewModelScope.launch {
            try {
                orderRepo.removeOrderItem(orderId, orderItemId)
                loadItems(orderId)
                _selectedOrder.value = orderRepo.getOrderById(orderId)
                _message.value = "Item removed."
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun updateOrderItemQty(orderItemId: Int, newQty: Double) {
        val orderId = _selectedOrder.value?.orderId ?: return
        if (newQty <= 0.0) { removeOrderItem(orderItemId); return }
        viewModelScope.launch {
            try {
                orderRepo.updateOrderItemQty(orderId, orderItemId, newQty)
                loadItems(orderId)
                _selectedOrder.value = orderRepo.getOrderById(orderId)
                _message.value = "Quantity updated."
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun applyOrderDiscount(discountAmount: Double) {
        val orderId = _selectedOrder.value?.orderId ?: return
        viewModelScope.launch {
            try {
                orderRepo.applyOrderDiscount(orderId, discountAmount)
                _selectedOrder.value = orderRepo.getOrderById(orderId)
                _message.value = "Discount applied."
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun hardDeleteOrder(orderId: Int) {
        viewModelScope.launch {
            try {
                orderRepo.hardDeleteOrder(orderId)
                _allRaw.value = _allRaw.value.filter { it.orderId != orderId }
                _orders.value = _orders.value.filter { it.orderId != orderId }
                _selectedOrder.value = null
                _orderItems.value    = emptyList()
                _message.value = "Order permanently deleted."
            } catch (e: Exception) { _message.value = "Delete failed: ${e.message}" }
        }
    }

    fun revertOrderToPending(orderId: Int) {
        viewModelScope.launch {
            try {
                orderRepo.revertOrderToPending(orderId)
                _message.value = "Order reverted to active."
                _selectedOrder.value = null
                _orderItems.value    = emptyList()
                loadOrders()
            } catch (e: Exception) { _message.value = "Revert failed: ${e.message}" }
        }
    }

    fun loadTables() {
        viewModelScope.launch {
            try { _tables.value = orderRepo.getTables() }
            catch (_: Exception) {}
        }
    }

    fun changeTable(orderId: Int, fromTableId: Int, toTableId: Int) {
        viewModelScope.launch {
            try {
                orderRepo.transferOrder(orderId, fromTableId, toTableId)
                val newTable = _tables.value.find { it.tableId == toTableId }
                _selectedOrder.value = _selectedOrder.value?.copy(tableId = toTableId, tableName = newTable?.tableName)
                _allRaw.value = _allRaw.value.map { o ->
                    if (o.orderId == orderId) o.copy(tableId = toTableId, tableName = newTable?.tableName) else o
                }
                _orders.value = _orders.value.map { o ->
                    if (o.orderId == orderId) o.copy(tableId = toTableId, tableName = newTable?.tableName) else o
                }
                _message.value = "Table changed to ${newTable?.tableName ?: "table $toTableId"}."
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun clearNavigateToPos() { _navigateToPos.value = false }

    fun clearMessage() { _message.value = null }

    private fun todayStart(): Date = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time
}
