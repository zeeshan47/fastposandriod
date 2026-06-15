package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.*
import com.fastpos.android.data.repositories.PurchaseRepository
import com.fastpos.android.data.repositories.SupplierRepository
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PurchasesViewModel @Inject constructor(
    private val supplierRepo: SupplierRepository,
    private val purchaseRepo: PurchaseRepository,
    private val prefs: PreferencesManager,
    val session: SessionManager
) : ViewModel() {

    // ── Suppliers tab ─────────────────────────────────────────────────────────
    private val _suppliers = MutableStateFlow<List<Supplier>>(emptyList())
    val suppliers: StateFlow<List<Supplier>> = _suppliers.asStateFlow()

    // ── Invoices tab ──────────────────────────────────────────────────────────
    private val _allInvoices           = MutableStateFlow<List<PurchaseInvoice>>(emptyList())
    private val _invoiceSearch         = MutableStateFlow("")
    private val _invoiceSupplierFilter = MutableStateFlow<Int?>(null)

    val invoiceSearch:         StateFlow<String> = _invoiceSearch.asStateFlow()
    val invoiceSupplierFilter: StateFlow<Int?>   = _invoiceSupplierFilter.asStateFlow()

    val invoices: StateFlow<List<PurchaseInvoice>> = combine(
        _allInvoices, _invoiceSearch, _invoiceSupplierFilter
    ) { all, search, supplierId ->
        all.filter { inv ->
            (supplierId == null || inv.supplierId == supplierId) &&
            (search.isBlank() || inv.invoiceNo.contains(search, ignoreCase = true) ||
             inv.supplierName.contains(search, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedInvoice = MutableStateFlow<PurchaseInvoice?>(null)
    val selectedInvoice: StateFlow<PurchaseInvoice?> = _selectedInvoice.asStateFlow()

    private val _invoiceItems = MutableStateFlow<List<PurchaseInvoiceItem>>(emptyList())
    val invoiceItems: StateFlow<List<PurchaseInvoiceItem>> = _invoiceItems.asStateFlow()

    // invoice form state
    private val _formSupplier = MutableStateFlow<Supplier?>(null)
    val formSupplier: StateFlow<Supplier?> = _formSupplier.asStateFlow()

    private val _formItems = MutableStateFlow<List<PurchaseInvoiceItem>>(emptyList())
    val formItems: StateFlow<List<PurchaseInvoiceItem>> = _formItems.asStateFlow()

    private val _formPaid       = MutableStateFlow("")
    val formPaid: StateFlow<String> = _formPaid.asStateFlow()

    private val _formNotes      = MutableStateFlow("")
    val formNotes: StateFlow<String> = _formNotes.asStateFlow()

    private val _formInvoiceNo  = MutableStateFlow("")
    val formInvoiceNo: StateFlow<String> = _formInvoiceNo.asStateFlow()

    private val _formInvoiceDate = MutableStateFlow<java.util.Date>(java.util.Date())
    val formInvoiceDate: StateFlow<java.util.Date> = _formInvoiceDate.asStateFlow()

    val formTotal: StateFlow<Double> = _formItems.map { it.sumOf { i -> i.total } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // ── Returns tab ───────────────────────────────────────────────────────────
    private val _returns        = MutableStateFlow<List<PurchaseReturn>>(emptyList())
    val returns: StateFlow<List<PurchaseReturn>> = _returns.asStateFlow()

    private val _selectedReturn = MutableStateFlow<PurchaseReturn?>(null)
    val selectedReturn: StateFlow<PurchaseReturn?> = _selectedReturn.asStateFlow()

    private val _returnItems    = MutableStateFlow<List<PurchaseReturnItem>>(emptyList())
    val returnItems: StateFlow<List<PurchaseReturnItem>> = _returnItems.asStateFlow()

    // return form state
    private val _retSupplierId    = MutableStateFlow<Int?>(null)
    val retSupplierId: StateFlow<Int?> = _retSupplierId.asStateFlow()

    private val _retFormItems     = MutableStateFlow<List<PurchaseReturnItem>>(emptyList())
    val retFormItems: StateFlow<List<PurchaseReturnItem>> = _retFormItems.asStateFlow()

    private val _retNotes         = MutableStateFlow("")
    val retNotes: StateFlow<String> = _retNotes.asStateFlow()

    private val _retRefundMethod  = MutableStateFlow("Credit")
    val retRefundMethod: StateFlow<String> = _retRefundMethod.asStateFlow()

    val retFormTotal: StateFlow<Double> = _retFormItems.map { it.sumOf { i -> i.lineTotal } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    // ── Edit mode ─────────────────────────────────────────────────────────────
    private val _editingInvoiceId = MutableStateFlow<Int?>(null)
    val editingInvoiceId: StateFlow<Int?> = _editingInvoiceId.asStateFlow()

    private val _editingReturnId = MutableStateFlow<Int?>(null)
    val editingReturnId: StateFlow<Int?> = _editingReturnId.asStateFlow()

    // ── Products (for item picker in invoices & returns) ──────────────────────
    private val _products = MutableStateFlow<List<PurchaseProduct>>(emptyList())
    val products: StateFlow<List<PurchaseProduct>> = _products.asStateFlow()

    // ── Shared ────────────────────────────────────────────────────────────────
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            supplierRepo.initSchema()
            purchaseRepo.initSchema()
            purchaseRepo.initReturnSchema()
            loadAll()
        }
    }

    private fun loadAll() {
        loadSuppliers()
        loadInvoices()
        loadProducts()
        loadReturns()
    }

    fun loadSuppliers() = viewModelScope.launch {
        runCatching { _suppliers.value = supplierRepo.getAllSuppliers() }
    }

    fun setInvoiceSearch(q: String) { _invoiceSearch.value = q }
    fun setInvoiceSupplierFilter(id: Int?) {
        _invoiceSupplierFilter.value = id
        loadInvoices(id)
    }

    fun loadInvoices(supplierId: Int? = null) = viewModelScope.launch {
        runCatching { _allInvoices.value = purchaseRepo.getInvoices(supplierId) }
    }

    fun loadProducts() = viewModelScope.launch {
        runCatching { _products.value = purchaseRepo.getProductsForPurchase() }
    }

    // ── Invoice form ──────────────────────────────────────────────────────────
    fun setFormSupplier(supplier: Supplier?)      { _formSupplier.value = supplier }
    fun setFormPaid(v: String)                   { _formPaid.value = v }
    fun setFormNotes(v: String)                  { _formNotes.value = v }
    fun setFormInvoiceNo(v: String)              { _formInvoiceNo.value = v }
    fun setFormInvoiceDate(d: java.util.Date)    { _formInvoiceDate.value = d }

    fun addFormItem(item: PurchaseInvoiceItem) {
        _formItems.value = _formItems.value + item
    }

    fun removeFormItem(index: Int) {
        _formItems.value = _formItems.value.toMutableList().also { it.removeAt(index) }
    }

    fun updateFormItem(index: Int, item: PurchaseInvoiceItem) {
        _formItems.value = _formItems.value.toMutableList().also { it[index] = item }
    }

    fun clearForm() {
        _formSupplier.value     = null
        _formItems.value        = emptyList()
        _formPaid.value         = ""
        _formNotes.value        = ""
        _formInvoiceNo.value    = ""
        _formInvoiceDate.value  = java.util.Date()
        _editingInvoiceId.value = null
    }

    fun startEditInvoice(invoice: PurchaseInvoice) = viewModelScope.launch {
        _editingInvoiceId.value  = invoice.invoiceId
        _formSupplier.value      = _suppliers.value.find { it.supplierId == invoice.supplierId }
        _formPaid.value          = if (invoice.paidAmount > 0) invoice.paidAmount.toString() else ""
        _formNotes.value         = invoice.notes
        _formInvoiceNo.value     = invoice.invoiceNo
        _formInvoiceDate.value   = invoice.invoiceDate
        val items = if (_selectedInvoice.value?.invoiceId == invoice.invoiceId && _invoiceItems.value.isNotEmpty())
            _invoiceItems.value
        else
            runCatching { purchaseRepo.getInvoiceItems(invoice.invoiceId) }.getOrElse { emptyList() }
        _formItems.value = items
    }

    fun saveInvoice() = viewModelScope.launch {
        val items = _formItems.value
        if (items.isEmpty()) { _message.value = "Add at least one item"; return@launch }
        val paid   = _formPaid.value.toDoubleOrNull() ?: 0.0
        val editId = _editingInvoiceId.value
        _loading.value = true
        runCatching {
            val invNo   = _formInvoiceNo.value.trim().ifBlank { null }
            val invDate = _formInvoiceDate.value
            if (editId != null) {
                purchaseRepo.updateInvoice(editId, _formSupplier.value?.supplierId, items, paid, _formNotes.value, invNo, invDate)
                _message.value = "Invoice updated"
            } else {
                purchaseRepo.saveInvoice(_formSupplier.value?.supplierId, items, paid, _formNotes.value, invNo, invDate)
                _message.value = "Invoice saved"
            }
            clearForm()
            loadInvoices()
            loadProducts()
        }.onFailure { _message.value = "Error: ${it.message}" }
        _loading.value = false
    }

    fun selectInvoice(invoice: PurchaseInvoice) = viewModelScope.launch {
        _selectedInvoice.value = invoice
        runCatching { _invoiceItems.value = purchaseRepo.getInvoiceItems(invoice.invoiceId) }
    }

    fun clearSelectedInvoice() {
        _selectedInvoice.value = null
        _invoiceItems.value    = emptyList()
    }

    fun payInvoice(invoiceId: Int, amount: Double, paymentMethod: String = "Cash") = viewModelScope.launch {
        if (amount <= 0) { _message.value = "Enter a valid amount"; return@launch }
        runCatching {
            purchaseRepo.recordInvoicePayment(invoiceId, amount, paymentMethod)
            loadInvoices()
            // refresh selected invoice if it's the one being paid
            _selectedInvoice.value?.let { current ->
                if (current.invoiceId == invoiceId) {
                    _selectedInvoice.value = _allInvoices.value.find { it.invoiceId == invoiceId }
                        ?: current.copy(
                            paidAmount    = current.paidAmount + amount,
                            balanceAmount = (current.balanceAmount - amount).coerceAtLeast(0.0)
                        )
                }
            }
            _message.value = "Payment recorded"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun deleteInvoice(invoiceId: Int) = viewModelScope.launch {
        runCatching {
            purchaseRepo.deleteInvoice(invoiceId, session.userId)
            clearSelectedInvoice()
            loadInvoices()
            loadProducts()
            _message.value = "Invoice deleted"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun printSelectedInvoice() {
        val invoice = _selectedInvoice.value ?: return
        val items   = _invoiceItems.value
        viewModelScope.launch {
            val address = prefs.savedPrinterAddress.first()
            if (address.isBlank()) { _message.value = "No printer configured. Set one in Settings."; return@launch }
            val sym  = session.settings.value.currencySymbol
            val nf   = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 }
            val dFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val text = buildString {
                appendLine("================================")
                appendLine("     PURCHASE INVOICE")
                appendLine("================================")
                appendLine("Invoice : ${invoice.invoiceNo}")
                appendLine("Date    : ${dFmt.format(invoice.invoiceDate)}")
                if (invoice.supplierName.isNotBlank()) appendLine("Supplier: ${invoice.supplierName}")
                if (invoice.notes.isNotBlank()) appendLine("Notes   : ${invoice.notes}")
                appendLine("--------------------------------")
                appendLine("${"Item".padEnd(20)} ${"Qty".padStart(6)} ${"Total".padStart(10)}")
                appendLine("--------------------------------")
                items.forEach { i ->
                    val name  = i.itemName.take(20).padEnd(20)
                    val qty   = "${nf.format(i.quantity)} ${i.unit}".padStart(6)
                    val total = "$sym${nf.format(i.total)}".padStart(10)
                    appendLine("$name $qty $total")
                }
                appendLine("--------------------------------")
                appendLine("Total   : $sym${nf.format(invoice.totalAmount)}")
                if (invoice.paidAmount > 0)
                    appendLine("Paid    : $sym${nf.format(invoice.paidAmount)}")
                if (invoice.balanceAmount > 0)
                    appendLine("Balance : $sym${nf.format(invoice.balanceAmount)}")
                appendLine("================================")
            }
            val result = BluetoothPrinterHelper.printReport(address, text)
            _message.value = if (result.isSuccess) "Invoice printed." else "Print failed: ${result.exceptionOrNull()?.message}"
        }
    }

    fun printSelectedReturn() {
        val ret   = _selectedReturn.value ?: return
        val items = _returnItems.value
        viewModelScope.launch {
            val address = prefs.savedPrinterAddress.first()
            if (address.isBlank()) { _message.value = "No printer configured. Set one in Settings."; return@launch }
            val sym  = session.settings.value.currencySymbol
            val nf   = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 }
            val dFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val text = buildString {
                appendLine("================================")
                appendLine("     PURCHASE RETURN")
                appendLine("================================")
                appendLine("Date    : ${dFmt.format(ret.returnDate)}")
                if (ret.supplierName.isNotBlank()) appendLine("Supplier: ${ret.supplierName}")
                appendLine("Refund  : ${ret.refundMethod}")
                if (ret.notes.isNotBlank()) appendLine("Notes   : ${ret.notes}")
                appendLine("--------------------------------")
                appendLine("${"Item".padEnd(20)} ${"Qty".padStart(6)} ${"Total".padStart(10)}")
                appendLine("--------------------------------")
                items.forEach { i ->
                    val name  = i.itemName.take(20).padEnd(20)
                    val qty   = "${nf.format(i.quantity)} ${i.unit}".padStart(6)
                    val total = "$sym${nf.format(i.lineTotal)}".padStart(10)
                    appendLine("$name $qty $total")
                }
                appendLine("--------------------------------")
                appendLine("Total   : $sym${nf.format(ret.totalAmount)}")
                appendLine("================================")
            }
            val result = BluetoothPrinterHelper.printReport(address, text)
            _message.value = if (result.isSuccess) "Return printed." else "Print failed: ${result.exceptionOrNull()?.message}"
        }
    }

    // ── Returns ───────────────────────────────────────────────────────────────
    fun loadReturns() = viewModelScope.launch {
        runCatching { _returns.value = purchaseRepo.getReturns() }
    }

    fun selectReturn(ret: PurchaseReturn?) = viewModelScope.launch {
        _selectedReturn.value = ret
        if (ret != null) runCatching { _returnItems.value = purchaseRepo.getReturnItems(ret.returnId) }
        else _returnItems.value = emptyList()
    }

    fun setRetSupplierId(id: Int?)        { _retSupplierId.value = id }
    fun setRetNotes(v: String)            { _retNotes.value = v }
    fun setRetRefundMethod(v: String)     { _retRefundMethod.value = v }

    fun addRetFormItem(item: PurchaseReturnItem) {
        _retFormItems.value = _retFormItems.value + item
    }

    fun removeRetFormItem(index: Int) {
        _retFormItems.value = _retFormItems.value.toMutableList().also { it.removeAt(index) }
    }

    fun clearRetForm() {
        _retSupplierId.value    = null
        _retFormItems.value     = emptyList()
        _retNotes.value         = ""
        _retRefundMethod.value  = "Credit"
        _editingReturnId.value  = null
    }

    fun startEditReturn(ret: PurchaseReturn) = viewModelScope.launch {
        _editingReturnId.value  = ret.returnId
        _retSupplierId.value    = ret.supplierId
        _retNotes.value         = ret.notes
        _retRefundMethod.value  = ret.refundMethod
        val items = if (_selectedReturn.value?.returnId == ret.returnId && _returnItems.value.isNotEmpty())
            _returnItems.value
        else
            runCatching { purchaseRepo.getReturnItems(ret.returnId) }.getOrElse { emptyList() }
        _retFormItems.value = items
    }

    fun saveReturn() = viewModelScope.launch {
        val items  = _retFormItems.value
        if (items.isEmpty()) { _message.value = "Add at least one item"; return@launch }
        val editId       = _editingReturnId.value
        val refundMethod = _retRefundMethod.value
        val shiftId      = session.currentShift.value?.shiftId
        _loading.value = true
        runCatching {
            if (editId != null) {
                purchaseRepo.updateReturn(editId, _retSupplierId.value, null, items, _retNotes.value)
                _message.value = "Return updated"
            } else {
                purchaseRepo.saveReturn(_retSupplierId.value, null, items, _retNotes.value, session.userId, refundMethod, shiftId)
                _message.value = "Return recorded"
            }
            clearRetForm()
            loadReturns()
            loadProducts()
        }.onFailure { _message.value = "Error: ${it.message}" }
        _loading.value = false
    }

    fun deleteReturn(returnId: Int) = viewModelScope.launch {
        runCatching {
            purchaseRepo.deleteReturn(returnId, session.userId)
            _selectedReturn.value = null
            _returnItems.value    = emptyList()
            loadReturns()
            loadProducts()
            _message.value = "Return deleted"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun clearMessage() { _message.value = null }
}
