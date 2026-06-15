package com.fastpos.android.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Customer
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.PaymentEntry
import com.fastpos.android.data.models.PrinterOption
import com.fastpos.android.data.repositories.CustomerRepository
import com.fastpos.android.data.repositories.CustomerWalletRepository
import com.fastpos.android.data.repositories.FbrRepository
import com.fastpos.android.data.repositories.OrderRepository
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.utils.NetworkPrinterHelper
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val orderRepo:    OrderRepository,
    private val customerRepo: CustomerRepository,
    private val walletRepo:   CustomerWalletRepository,
    private val fbrRepo:      FbrRepository,
    private val prefs:        PreferencesManager,
    val session:              SessionManager
) : ViewModel() {

    private val _order      = MutableStateFlow<Order?>(null)
    private val _customer   = MutableStateFlow<Customer?>(null)
    private val _isLoading  = MutableStateFlow(false)
    private val _isPaying   = MutableStateFlow(false)
    private val _error      = MutableStateFlow<String?>(null)
    private val _paid       = MutableStateFlow(false)
    private val _isPrinting            = MutableStateFlow(false)
    private val _showReceiptPreview    = MutableStateFlow(false)
    private val _receiptPreviewText    = MutableStateFlow("")

    private val _tipAmount             = MutableStateFlow(0.0)
    private val _confirmedPayments     = MutableStateFlow<List<PaymentEntry>>(emptyList())
    private val _walletBalance         = MutableStateFlow(0.0)
    private val _customerTotalOrders   = MutableStateFlow(0)
    private val _customerLoyaltyPoints = MutableStateFlow(0)
    private val _availablePrinters     = MutableStateFlow<List<PrinterOption>>(emptyList())

    val order:        StateFlow<Order?>    = _order
    val customer:     StateFlow<Customer?> = _customer
    val isLoading:    StateFlow<Boolean>   = _isLoading
    val isPaying:     StateFlow<Boolean>   = _isPaying
    val error:        StateFlow<String?>   = _error
    val paid:         StateFlow<Boolean>   = _paid
    val isPrinting:          StateFlow<Boolean> = _isPrinting
    val showReceiptPreview:  StateFlow<Boolean> = _showReceiptPreview
    val receiptPreviewText:  StateFlow<String>  = _receiptPreviewText
    val tipAmount:       StateFlow<Double> = _tipAmount
    val walletBalance:   StateFlow<Double> = _walletBalance
    val customerTotalOrders:   StateFlow<Int>              = _customerTotalOrders
    val customerLoyaltyPoints: StateFlow<Int>              = _customerLoyaltyPoints
    val availablePrinters:     StateFlow<List<PrinterOption>> = _availablePrinters

    // Best available phone: delivery phone > attached customer phone
    val contactPhone: StateFlow<String> = combine(_order, _customer) { order, customer ->
        order?.deliveryPhone?.takeIf { it.isNotBlank() }
            ?: customer?.phone?.takeIf { it.isNotBlank() }
            ?: ""
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Effective total after tip
    val effectiveTotal: StateFlow<Double> = combine(_order, _tipAmount) { order, tip ->
        (order?.grandTotal ?: 0.0) + tip
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // Active payment rows
    private val _payments = MutableStateFlow(listOf(PaymentEntry("Cash", 0.0, "")))
    val payments: StateFlow<List<PaymentEntry>> = _payments

    val totalEntered: StateFlow<Double> = _payments.map { it.sumOf { p -> p.amount } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0.0)

    val savedPrinterAddress:    Flow<String>       = prefs.savedPrinterAddress
    val savedPrinterName:       Flow<String>       = prefs.savedPrinterName
    val customPaymentMethods:   Flow<List<String>> = prefs.customPaymentMethods
    val confirmedPayments:      StateFlow<List<PaymentEntry>> = _confirmedPayments
    val paperWidth:             Flow<Int>          = prefs.paperWidth
    val billPrintMode:          Flow<String>       = prefs.billPrintMode

    fun loadAvailablePrinters() {
        viewModelScope.launch {
            val printers    = mutableListOf<PrinterOption>()
            val btAddress   = prefs.savedPrinterAddress.first()
            val btName      = prefs.savedPrinterName.first()
            val netIp       = prefs.receiptNetIp.first()
            val netPort     = prefs.receiptNetPort.first()
            val netName     = prefs.receiptNetName.first()
            if (btAddress.isNotBlank())
                printers.add(PrinterOption(btName.ifBlank { "Bluetooth Printer" }, "Bluetooth", address = btAddress))
            if (netIp.isNotBlank())
                printers.add(PrinterOption(netName.ifBlank { "Network Printer" }, "Network", ip = netIp, port = netPort))
            _availablePrinters.value = printers
        }
    }

    fun loadOrder(orderId: Int) {
        _paid.value              = false
        _tipAmount.value         = 0.0
        _confirmedPayments.value = emptyList()
        _error.value             = null
        loadAvailablePrinters()
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val orders = orderRepo.getPendingOrders()
                _order.value = orders.firstOrNull { it.orderId == orderId }
                    ?: orderRepo.getAllOrders(limit = 200).firstOrNull { it.orderId == orderId }

                _order.value?.let { o ->
                    // Load items for receipt printing
                    val items = orderRepo.getOrderItems(o.orderId)
                    _order.value = o.copy(items = items)

                    // Pre-fill cash with effective total
                    _payments.value = listOf(PaymentEntry("Cash", o.grandTotal, ""))
                    // Load customer + wallet balance if attached
                    o.customerId?.let { cid ->
                        _customer.value = customerRepo.getCustomerById(cid)
                        try {
                            walletRepo.initSchema()
                            _walletBalance.value = walletRepo.getBalance(cid)
                        } catch (_: Exception) { _walletBalance.value = 0.0 }
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setTipAmount(amount: Double) {
        _tipAmount.value = amount.coerceAtLeast(0.0)
        adjustSinglePaymentRow()
    }

    private fun adjustSinglePaymentRow() {
        _order.value?.let { o ->
            val newTotal = (o.grandTotal + _tipAmount.value).coerceAtLeast(0.0)
            if (_payments.value.size == 1) {
                _payments.value = listOf(_payments.value[0].copy(amount = newTotal))
            }
        }
    }

    fun updatePayment(index: Int, method: String? = null, amount: Double? = null, ref: String? = null) {
        _payments.value = _payments.value.toMutableList().apply {
            val p = get(index)
            set(index, p.copy(
                paymentMethod = method ?: p.paymentMethod,
                amount        = amount ?: p.amount,
                reference     = ref    ?: p.reference
            ))
        }
    }

    fun addPaymentRow() {
        _payments.value = _payments.value + PaymentEntry("Cash", 0.0, "")
    }

    fun removePaymentRow(index: Int) {
        if (_payments.value.size > 1)
            _payments.value = _payments.value.toMutableList().also { it.removeAt(index) }
    }

    fun applyWalletPayment(amount: Double) {
        val idx = _payments.value.indexOfFirst { it.paymentMethod == "Wallet" }
        if (idx >= 0) {
            _payments.value = _payments.value.toMutableList().apply {
                set(idx, get(idx).copy(amount = amount))
            }
        } else if (_payments.value.size == 1 && _payments.value[0].paymentMethod == "Cash") {
            _payments.value = listOf(PaymentEntry("Wallet", amount, ""))
        } else {
            _payments.value = _payments.value + PaymentEntry("Wallet", amount, "")
        }
    }

    fun confirmPayment() {
        val order = _order.value ?: return
        val validPayments = _payments.value.filter { it.amount > 0 }
        if (validPayments.isEmpty()) { _error.value = "Enter payment amount."; return }

        val tip = _tipAmount.value
        val effectiveTotal = order.grandTotal + tip
        val totalPaid = validPayments.sumOf { it.amount }
        if (!session.settings.value.allowPartialPayment && totalPaid < effectiveTotal - 0.01) {
            _error.value = "Partial payment is not allowed. Full amount required."
            return
        }

        viewModelScope.launch {
            _isPaying.value = true
            _error.value    = null
            try {
                // Deduct wallet payment before saving order
                val walletAmt = validPayments.filter { it.paymentMethod == "Wallet" }.sumOf { it.amount }
                if (walletAmt > 0 && order.customerId != null) {
                    val ok = walletRepo.deduct(order.customerId, walletAmt, order.orderId, session.userId)
                    if (!ok) {
                        _error.value = "Insufficient wallet balance. Available: ${_walletBalance.value.toLong()}"
                        _isPaying.value = false
                        return@launch
                    }
                    _walletBalance.value = walletRepo.getBalance(order.customerId)
                }

                val paymentsToSave = validPayments

                orderRepo.addPayment(order.orderId, paymentsToSave, session.userId)
                if (tip > 0) orderRepo.saveTip(order.orderId, tip)
                _confirmedPayments.value = paymentsToSave

                // Update in-memory order so auto-print and manual reprint use correct values.
                val totalPaidAmt = paymentsToSave.sumOf { it.amount }
                val isFull = totalPaidAmt >= effectiveTotal - 0.01
                _order.value = order.copy(
                    tips          = tip,
                    paidAmount    = totalPaidAmt,
                    balanceAmount = if (isFull) 0.0 else (effectiveTotal - totalPaidAmt),
                    paymentStatus = if (isFull) "Paid" else "Partial",
                    orderStatus   = if (isFull) "Completed" else order.orderStatus
                )

                // Fetch customer profile data for receipt
                val cid = order.customerId
                if (cid != null) {
                    try {
                        val cust = customerRepo.getCustomerById(cid)
                        _customerTotalOrders.value   = cust?.totalOrders   ?: 0
                        _customerLoyaltyPoints.value = cust?.loyaltyPoints ?: 0
                    } catch (_: Exception) {}
                }

                _paid.value = true
                if (session.settings.value.fbrEnabled) {
                    runCatching { fbrRepo.queueOrderForFbr(order.orderId) }
                }
                autoPrintIfEnabled()
                autoOpenDrawerIfEnabled(validPayments)
            } catch (e: Exception) {
                _error.value = "Payment failed: ${e.message}"
            } finally {
                _isPaying.value = false
            }
        }
    }

    private fun autoPrintIfEnabled() {
        val order = _order.value ?: return
        viewModelScope.launch {
            val mode      = prefs.billPrintMode.first()
            if (mode == "Off") return@launch

            if (mode == "Preview") {
                _showReceiptPreview.value = true
                return@launch
            }

            val prType    = prefs.receiptPrinterType.first()
            val btAddress = prefs.savedPrinterAddress.first()
            val netIp     = prefs.receiptNetIp.first()
            val netPort   = prefs.receiptNetPort.first()
            val hasTarget = if (prType == "Network") netIp.isNotBlank() else btAddress.isNotBlank()
            if (!hasTarget) return@launch

            val settings     = session.settings.value
            val header       = prefs.receiptHeader.first()
            val footer       = prefs.receiptFooter.first()
            val lineWidth    = prefs.paperWidth.first()
            val netPaperType = prefs.receiptNetPaperType.first()
            val logoData     = settings.logoData
            _isPrinting.value = true
            val cto       = _customerTotalOrders.value
            val clp       = _customerLoyaltyPoints.value
            val ptsEarned = order.grandTotal.toInt() / 10
            if (prType == "Network") {
                NetworkPrinterHelper.printReceipt(netIp, netPort, order, settings,
                    ptsEarned, header, footer, _confirmedPayments.value, lineWidth,
                    customerTotalOrders = cto, customerLoyaltyPoints = clp,
                    paperType = netPaperType, logoData = logoData)
            } else {
                BluetoothPrinterHelper.printReceipt(btAddress, order, settings,
                    ptsEarned, header, footer, _confirmedPayments.value, lineWidth,
                    customerTotalOrders = cto, customerLoyaltyPoints = clp,
                    logoData = logoData)
            }
            _isPrinting.value = false
        }
    }

    fun showPreview() {
        _showReceiptPreview.value = true
    }

    fun dismissReceiptPreview() {
        _showReceiptPreview.value = false
    }

    fun printFromPreview(printer: PrinterOption) {
        val order = _order.value ?: return
        viewModelScope.launch {
            val settings     = session.settings.value
            val header       = prefs.receiptHeader.first()
            val footer       = prefs.receiptFooter.first()
            val lineWidth    = prefs.paperWidth.first()
            val netPaperType = prefs.receiptNetPaperType.first()
            val logoData     = settings.logoData
            _isPrinting.value = true
            _showReceiptPreview.value = false
            val cto       = _customerTotalOrders.value
            val clp       = _customerLoyaltyPoints.value
            val ptsEarned = order.grandTotal.toInt() / 10
            if (printer.type == "Network") {
                NetworkPrinterHelper.printReceipt(printer.ip, printer.port, order, settings,
                    ptsEarned, header, footer, _confirmedPayments.value, lineWidth,
                    customerTotalOrders = cto, customerLoyaltyPoints = clp,
                    paperType = netPaperType, logoData = logoData)
            } else {
                BluetoothPrinterHelper.printReceipt(printer.address, order, settings,
                    ptsEarned, header, footer, _confirmedPayments.value, lineWidth,
                    customerTotalOrders = cto, customerLoyaltyPoints = clp,
                    logoData = logoData)
            }
            _isPrinting.value = false
        }
    }

    private fun autoOpenDrawerIfEnabled(payments: List<com.fastpos.android.data.models.PaymentEntry>) {
        val hasCash = payments.any { it.paymentMethod.equals("Cash", ignoreCase = true) && it.amount > 0 }
        if (!hasCash) return
        viewModelScope.launch {
            val enabled = prefs.autoOpenDrawer.first()
            val address = prefs.savedPrinterAddress.first()
            if (!enabled || address.isBlank()) return@launch
            try { BluetoothPrinterHelper.openCashDrawer(address) } catch (_: Exception) {}
        }
    }

    fun printReceipt(context: Context, address: String) {
        val order    = _order.value ?: return
        val settings = session.settings.value
        viewModelScope.launch {
            val mode = prefs.billPrintMode.first()
            if (mode == "Preview") {
                _showReceiptPreview.value = true
                return@launch
            }
            _isPrinting.value = true
            _error.value      = null
            val header     = prefs.receiptHeader.first()
            val footer     = prefs.receiptFooter.first()
            val lineWidth  = prefs.paperWidth.first()
            val prType     = prefs.receiptPrinterType.first()
            val netIp      = prefs.receiptNetIp.first()
            val netPort    = prefs.receiptNetPort.first()
            val cto          = _customerTotalOrders.value
            val clp          = _customerLoyaltyPoints.value
            val ptsEarned    = order.grandTotal.toInt() / 10
            val netPaperType = prefs.receiptNetPaperType.first()
            val logoData     = settings.logoData
            val result = if (prType == "Network" && netIp.isNotBlank()) {
                NetworkPrinterHelper.printReceipt(netIp, netPort, order, settings,
                    ptsEarned, header, footer, _confirmedPayments.value, lineWidth,
                    customerTotalOrders = cto, customerLoyaltyPoints = clp,
                    paperType = netPaperType, logoData = logoData)
            } else {
                BluetoothPrinterHelper.printReceipt(address, order, settings,
                    ptsEarned, header, footer, _confirmedPayments.value, lineWidth,
                    customerTotalOrders = cto, customerLoyaltyPoints = clp,
                    logoData = logoData)
            }
            result.onFailure { _error.value = "Print failed: ${it.message}" }
            _isPrinting.value = false
        }
    }

    fun savePrinter(address: String, name: String) {
        viewModelScope.launch { prefs.savePrinter(address, name) }
    }

    suspend fun buildShareText(): String {
        val order     = _order.value ?: return ""
        val settings  = session.settings.value
        val header    = prefs.receiptHeader.first()
        val footer    = prefs.receiptFooter.first()
        val lineWidth = prefs.paperWidth.first()
        val ptsEarned = order.grandTotal.toInt() / 10
        return BluetoothPrinterHelper.buildReceiptText(
            order, settings, ptsEarned, header, footer, _confirmedPayments.value, lineWidth,
            customerTotalOrders = _customerTotalOrders.value,
            customerLoyaltyPoints = _customerLoyaltyPoints.value
        )
    }

    fun sendSms(context: Context, phone: String) {
        viewModelScope.launch {
            val body   = buildShareText().ifBlank { return@launch }
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.trim()}")).apply {
                putExtra("sms_body", body)
            }
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    fun openWhatsApp(context: Context, phone: String) {
        viewModelScope.launch {
            val text        = buildShareText().ifBlank { return@launch }
            val cleanPhone  = phone.trim().replace(Regex("[\\s\\-()]"), "")
            val uri         = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(text)}")
            val waIntent    = Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
            try {
                context.startActivity(waIntent)
            } catch (_: Exception) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
            }
        }
    }

    fun splitBill(n: Int) {
        if (n < 2 || n > 20) return
        val total     = effectiveTotal.value
        val perPerson = kotlin.math.floor(total / n * 100) / 100.0
        val remainder = (total * 100).toLong() - (perPerson * 100).toLong() * (n - 1)
        _payments.value = (1 until n).map { com.fastpos.android.data.models.PaymentEntry("Cash", perPerson, "") } +
                          listOf(com.fastpos.android.data.models.PaymentEntry("Cash", remainder / 100.0, ""))
    }
}
