package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.PurchasePayment
import com.fastpos.android.data.models.Supplier
import com.fastpos.android.data.models.SupplierBalance
import com.fastpos.android.data.models.SupplierLedgerEntry
import com.fastpos.android.data.repositories.SupplierRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val repo:    SupplierRepository,
    val session: SessionManager
) : ViewModel() {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ── Suppliers tab ─────────────────────────────────────────────────────────
    private val _suppliers        = MutableStateFlow<List<Supplier>>(emptyList())
    private val _balances         = MutableStateFlow<List<SupplierBalance>>(emptyList())
    private val _isLoading        = MutableStateFlow(false)
    private val _message          = MutableStateFlow<String?>(null)
    private val _search           = MutableStateFlow("")

    val suppliers:  StateFlow<List<Supplier>>        = _suppliers
    val balances:   StateFlow<List<SupplierBalance>> = _balances
    val isLoading:  StateFlow<Boolean>               = _isLoading
    val message:    StateFlow<String?>               = _message
    val search:     StateFlow<String>                = _search

    val filtered get() = _suppliers.value.filter { s ->
        val q = _search.value.trim()
        q.isEmpty() || s.supplierName.contains(q, true) || s.phone.contains(q, true)
    }

    // ── Payments & Ledger tab ─────────────────────────────────────────────────
    private val _selectedBalance  = MutableStateFlow<SupplierBalance?>(null)
    private val _payments         = MutableStateFlow<List<PurchasePayment>>(emptyList())
    private val _ledgerEntries    = MutableStateFlow<List<SupplierLedgerEntry>>(emptyList())
    private val _openingBalance   = MutableStateFlow(0.0)
    private val _totalDebit       = MutableStateFlow(0.0)
    private val _totalCredit      = MutableStateFlow(0.0)
    private val _closingBalance   = MutableStateFlow(0.0)
    private val _ledgerFrom       = MutableStateFlow(startOfCurrentMonth())
    private val _ledgerTo         = MutableStateFlow(Date())
    private val _ledgerLoading    = MutableStateFlow(false)

    val selectedBalance: StateFlow<SupplierBalance?>        = _selectedBalance
    val payments:        StateFlow<List<PurchasePayment>>   = _payments
    val ledgerEntries:   StateFlow<List<SupplierLedgerEntry>> = _ledgerEntries
    val openingBalance:  StateFlow<Double>                  = _openingBalance
    val totalDebit:      StateFlow<Double>                  = _totalDebit
    val totalCredit:     StateFlow<Double>                  = _totalCredit
    val closingBalance:  StateFlow<Double>                  = _closingBalance
    val ledgerFrom:      StateFlow<Date>                    = _ledgerFrom
    val ledgerTo:        StateFlow<Date>                    = _ledgerTo
    val ledgerLoading:   StateFlow<Boolean>                 = _ledgerLoading

    init {
        viewModelScope.launch {
            repo.initSchema()
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _suppliers.value = repo.getAllSuppliers()
                _balances.value  = repo.getSupplierBalances()
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearch(q: String) { _search.value = q }

    fun addSupplier(name: String, contact: String, phone: String, address: String, email: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                repo.addSupplier(name.trim(), contact.trim(), phone.trim(), address.trim(), email.trim())
                _message.value = "$name added"
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateSupplier(id: Int, name: String, contact: String, phone: String, address: String, email: String, isActive: Boolean) {
        viewModelScope.launch {
            try {
                repo.updateSupplier(id, name.trim(), contact.trim(), phone.trim(), address.trim(), email.trim(), isActive)
                _message.value = "Updated"
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteSupplier(id: Int) {
        viewModelScope.launch {
            try {
                repo.deleteSupplier(id)
                _message.value = "Supplier removed"
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    // ── Payments & Ledger ─────────────────────────────────────────────────────

    fun selectBalance(balance: SupplierBalance) {
        _selectedBalance.value = balance
        loadPayments(balance.supplierId)
        runLedger()
    }

    fun clearSelectedBalance() {
        _selectedBalance.value = null
        _payments.value        = emptyList()
        _ledgerEntries.value   = emptyList()
        _openingBalance.value  = 0.0
        _totalDebit.value      = 0.0
        _totalCredit.value     = 0.0
        _closingBalance.value  = 0.0
    }

    private fun loadPayments(supplierId: Int) {
        viewModelScope.launch {
            try { _payments.value = repo.getPaymentsBySupplier(supplierId) }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun addPayment(supplierId: Int, amount: Double, method: String, reference: String, notes: String) {
        if (amount <= 0) return
        viewModelScope.launch {
            try {
                val name    = _suppliers.value.find { it.supplierId == supplierId }?.supplierName ?: ""
                val shiftId = session.currentShift.value?.shiftId
                repo.addDirectPayment(supplierId, amount, method, reference, notes, name, shiftId)
                _message.value = "Payment recorded"
                load()
                loadPayments(supplierId)
                runLedger()
                // refresh selected balance with updated outstanding
                _selectedBalance.value = _balances.value.find { it.supplierId == supplierId }
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deletePayment(paymentId: Int) {
        val supplierId = _selectedBalance.value?.supplierId ?: return
        viewModelScope.launch {
            try {
                repo.deletePayment(paymentId)
                _message.value = "Payment removed"
                load()
                loadPayments(supplierId)
                runLedger()
                _selectedBalance.value = _balances.value.find { it.supplierId == supplierId }
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun runLedger() {
        val supplierId = _selectedBalance.value?.supplierId ?: return
        val from = _ledgerFrom.value
        val to   = _ledgerTo.value
        if (from.after(to)) { _message.value = "From date cannot be after To date"; return }
        viewModelScope.launch {
            _ledgerLoading.value = true
            try {
                val opening = repo.getOpeningBalance(supplierId, from)
                val rows    = repo.getLedger(supplierId, from, to)

                var runningBalance = opening
                val adjusted = rows.map { r ->
                    runningBalance += r.debit - r.credit
                    r.copy(balance = runningBalance)
                }

                _openingBalance.value = opening
                _totalDebit.value     = rows.sumOf { it.debit }
                _totalCredit.value    = rows.sumOf { it.credit }
                _closingBalance.value = opening + _totalDebit.value - _totalCredit.value
                _ledgerEntries.value  = adjusted
            } catch (e: Exception) { _message.value = e.message }
            finally { _ledgerLoading.value = false }
        }
    }

    fun setLedgerFrom(date: Date) { _ledgerFrom.value = date }
    fun setLedgerTo(date: Date)   { _ledgerTo.value = date }

    fun thisMonth() {
        _ledgerFrom.value = startOfCurrentMonth()
        _ledgerTo.value   = Date()
        runLedger()
    }

    fun last3Months() {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -3) }
        _ledgerFrom.value = cal.time
        _ledgerTo.value   = Date()
        runLedger()
    }

    fun allTime() {
        _ledgerFrom.value = dateFmt.parse("2000-01-01") ?: Date()
        _ledgerTo.value   = Date()
        runLedger()
    }

    fun ledgerFromStr(): String = dateFmt.format(_ledgerFrom.value)
    fun ledgerToStr():   String = dateFmt.format(_ledgerTo.value)

    fun setLedgerFromStr(s: String) {
        runCatching { dateFmt.parse(s) }.getOrNull()?.let { _ledgerFrom.value = it }
    }

    fun setLedgerToStr(s: String) {
        runCatching { dateFmt.parse(s) }.getOrNull()?.let { _ledgerTo.value = it }
    }

    fun clearMessage() { _message.value = null }

    private fun startOfCurrentMonth(): Date {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0);      cal.set(Calendar.MILLISECOND, 0)
        return cal.time
    }
}
