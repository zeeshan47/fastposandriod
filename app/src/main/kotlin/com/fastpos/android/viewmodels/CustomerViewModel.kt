package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Customer
import com.fastpos.android.data.models.CustomerFeedback
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.WalletTransaction
import com.fastpos.android.data.repositories.CustomerFeedbackRepository
import com.fastpos.android.data.repositories.CustomerRepository
import com.fastpos.android.data.repositories.OrderRepository
import com.fastpos.android.data.repositories.WalletRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val repo:         CustomerRepository,
    private val orderRepo:    OrderRepository,
    private val feedbackRepo: CustomerFeedbackRepository,
    private val walletRepo:   WalletRepository,
    val session:              SessionManager
) : ViewModel() {

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _message   = MutableStateFlow<String?>(null)
    private val _search    = MutableStateFlow("")

    val customers: StateFlow<List<Customer>> = _customers
    val isLoading: StateFlow<Boolean>        = _isLoading
    val message:   StateFlow<String?>        = _message
    val search:    StateFlow<String>         = _search

    // ── Unified detail sheet (Orders | Feedback | Wallet tabs) ────────────────
    private val _selectedCustomer    = MutableStateFlow<Customer?>(null)
    private val _customerOrders      = MutableStateFlow<List<Order>>(emptyList())
    private val _ordersLoading       = MutableStateFlow(false)
    private val _feedbacks           = MutableStateFlow<List<CustomerFeedback>>(emptyList())
    private val _feedbackLoading     = MutableStateFlow(false)
    private val _walletBalance       = MutableStateFlow(0.0)
    private val _walletTransactions  = MutableStateFlow<List<WalletTransaction>>(emptyList())
    private val _walletLoading       = MutableStateFlow(false)
    private val _topUpAmount         = MutableStateFlow("")

    val selectedCustomer:   StateFlow<Customer?>              = _selectedCustomer
    val customerOrders:     StateFlow<List<Order>>            = _customerOrders
    val ordersLoading:      StateFlow<Boolean>                = _ordersLoading
    val feedbacks:          StateFlow<List<CustomerFeedback>> = _feedbacks
    val feedbackLoading:    StateFlow<Boolean>                = _feedbackLoading
    val walletBalance:      StateFlow<Double>                 = _walletBalance
    val walletTransactions: StateFlow<List<WalletTransaction>> = _walletTransactions
    val walletLoading:      StateFlow<Boolean>                = _walletLoading
    val topUpAmount:        StateFlow<String>                 = _topUpAmount

    // ── Top-level Feedback tab (all customers, date-filtered) ─────────────────
    private val _allFeedbacks        = MutableStateFlow<List<CustomerFeedback>>(emptyList())
    private val _allFeedbacksLoading = MutableStateFlow(false)
    private val _fbFromDate          = MutableStateFlow(startOfMonth())
    private val _fbToDate            = MutableStateFlow(Date())

    val allFeedbacks:        StateFlow<List<CustomerFeedback>> = _allFeedbacks
    val allFeedbacksLoading: StateFlow<Boolean>                = _allFeedbacksLoading
    val fbFromDate:          StateFlow<Date>                   = _fbFromDate
    val fbToDate:            StateFlow<Date>                   = _fbToDate

    // Keep legacy names for backward compat with any existing callers
    val historyCustomer:       StateFlow<Customer?>   get() = _selectedCustomer
    val customerOrdersLoading: StateFlow<Boolean>     get() = _ordersLoading
    val feedbackCustomer:      StateFlow<Customer?>   get() = _selectedCustomer

    init {
        load()
        viewModelScope.launch { try { feedbackRepo.initSchema() } catch (_: Exception) {} }
        loadAllFeedbacks()
    }

    fun setSearch(q: String) { _search.value = q; load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try { _customers.value = repo.getCustomers(_search.value) }
            catch (e: Exception) { _message.value = e.message }
            finally { _isLoading.value = false }
        }
    }

    // ── Detail sheet ──────────────────────────────────────────────────────────

    fun openDetail(customer: Customer) {
        _selectedCustomer.value = customer
        loadOrders(customer.customerId)
        loadFeedback(customer.customerId)
        loadWallet(customer.customerId)
    }

    fun closeDetail() {
        _selectedCustomer.value   = null
        _customerOrders.value     = emptyList()
        _feedbacks.value          = emptyList()
        _walletBalance.value      = 0.0
        _walletTransactions.value = emptyList()
        _topUpAmount.value        = ""
    }

    private fun loadOrders(customerId: Int) {
        viewModelScope.launch {
            _ordersLoading.value = true
            try { _customerOrders.value = orderRepo.getOrdersByCustomer(customerId) }
            catch (_: Exception) { _customerOrders.value = emptyList() }
            finally { _ordersLoading.value = false }
        }
    }

    private fun loadFeedback(customerId: Int) {
        viewModelScope.launch {
            _feedbackLoading.value = true
            try { _feedbacks.value = feedbackRepo.getFeedbackForCustomer(customerId) }
            catch (_: Exception) { _feedbacks.value = emptyList() }
            finally { _feedbackLoading.value = false }
        }
    }

    private fun loadWallet(customerId: Int) {
        viewModelScope.launch {
            _walletLoading.value = true
            try {
                _walletBalance.value      = walletRepo.getBalance(customerId)
                _walletTransactions.value = walletRepo.getTransactions(customerId)
            } catch (_: Exception) {
                _walletBalance.value      = 0.0
                _walletTransactions.value = emptyList()
            } finally { _walletLoading.value = false }
        }
    }

    fun setTopUpAmount(v: String) { _topUpAmount.value = v }

    fun topUpWallet() {
        val customer = _selectedCustomer.value ?: return
        val amount   = _topUpAmount.value.toDoubleOrNull() ?: 0.0
        if (amount <= 0) { _message.value = "Enter a valid amount."; return }
        viewModelScope.launch {
            try {
                walletRepo.topUp(customer.customerId, amount, session.userId)
                _topUpAmount.value = ""
                _message.value = "Wallet topped up ${amount.toLong()}"
                loadWallet(customer.customerId)
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun addFeedback(rating: Int, comment: String) {
        val customer = _selectedCustomer.value ?: return
        viewModelScope.launch {
            try {
                feedbackRepo.addFeedback(customer.customerId, null, rating, comment, session.userId)
                loadFeedback(customer.customerId)
                loadAllFeedbacks()
                _message.value = "Feedback saved."
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun deleteFeedback(feedbackId: Int) {
        val customer = _selectedCustomer.value ?: return
        viewModelScope.launch {
            try {
                feedbackRepo.deleteFeedback(feedbackId)
                loadFeedback(customer.customerId)
                loadAllFeedbacks()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    // ── Top-level Feedback tab ────────────────────────────────────────────────

    fun loadAllFeedbacks() {
        viewModelScope.launch {
            _allFeedbacksLoading.value = true
            try { _allFeedbacks.value = feedbackRepo.getRecentFeedback(_fbFromDate.value, _fbToDate.value) }
            catch (_: Exception) { _allFeedbacks.value = emptyList() }
            finally { _allFeedbacksLoading.value = false }
        }
    }

    fun setFeedbackDateRange(from: Date, to: Date) {
        _fbFromDate.value = from; _fbToDate.value = to; loadAllFeedbacks()
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    fun addCustomer(name: String, phone: String, address: String) {
        if (name.isBlank()) { _message.value = "Name is required."; return }
        viewModelScope.launch {
            try { repo.addCustomer(name, phone, address, session.userId); _message.value = "Customer added."; load() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateCustomer(id: Int, name: String, phone: String, address: String) {
        viewModelScope.launch {
            try { repo.updateCustomer(id, name, phone, address, session.userId); _message.value = "Customer updated."; load() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteCustomer(id: Int) {
        viewModelScope.launch {
            try {
                val deleted = repo.deleteCustomer(id, session.userId)
                _message.value = if (deleted) "Customer removed." else "Cannot delete — customer has order history."
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    // Legacy compat
    fun showHistory(customer: Customer) = openDetail(customer)
    fun clearHistory()                  = closeDetail()
    fun openFeedback(customer: Customer) = openDetail(customer)
    fun closeFeedback()                  = closeDetail()

    fun clearMessage() { _message.value = null }

    private fun startOfMonth(): Date = Calendar.getInstance().also {
        it.set(Calendar.DAY_OF_MONTH, 1)
        it.set(Calendar.HOUR_OF_DAY, 0); it.set(Calendar.MINUTE, 0); it.set(Calendar.SECOND, 0)
    }.time
}
