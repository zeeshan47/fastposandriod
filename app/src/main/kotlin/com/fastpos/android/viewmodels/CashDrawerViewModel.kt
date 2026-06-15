package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.CashTransaction
import com.fastpos.android.data.repositories.CashDrawerRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CashDrawerViewModel @Inject constructor(
    private val repo:    CashDrawerRepository,
    val session:         SessionManager
) : ViewModel() {

    private val _transactions = MutableStateFlow<List<CashTransaction>>(emptyList())
    private val _totalIn      = MutableStateFlow(0.0)
    private val _totalOut     = MutableStateFlow(0.0)
    private val _isLoading    = MutableStateFlow(false)
    private val _message      = MutableStateFlow<String?>(null)

    val transactions: StateFlow<List<CashTransaction>> = _transactions
    val totalIn:      StateFlow<Double>                = _totalIn
    val totalOut:     StateFlow<Double>                = _totalOut
    val isLoading:    StateFlow<Boolean>               = _isLoading
    val message:      StateFlow<String?>               = _message

    val netCash: Double get() = _totalIn.value - _totalOut.value

    init {
        viewModelScope.launch { repo.initSchema() }
        load()
    }

    fun load() {
        viewModelScope.launch {
            val shiftId = session.currentShift.value?.shiftId ?: return@launch
            _isLoading.value = true
            try {
                val manual = repo.getByShift(shiftId)
                val sales  = repo.getShiftOrderPayments(shiftId)
                // Sale entries shown for visibility but NOT counted in totalIn/totalOut
                _transactions.value = (manual + sales).sortedBy { it.createdAt }
                _totalIn.value      = repo.getTotalIn(shiftId)
                _totalOut.value     = repo.getTotalOut(shiftId)
            } catch (e: Exception) {
                _message.value = "Load failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addTransaction(type: String, amount: Double, reason: String, notes: String) {
        if (reason.isBlank()) { _message.value = "Please enter a reason."; return }
        if (amount <= 0)      { _message.value = "Amount must be greater than zero."; return }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val shiftId = session.currentShift.value?.shiftId ?: run {
                    _message.value = "No shift is open."
                    return@launch
                }
                val userId = session.currentUser.value?.userId ?: 0
                repo.addTransaction(shiftId, type, amount, reason.trim(), notes.trim(), userId)
                _message.value = "${if (type == "In") "Cash In" else "Cash Out"} of ${"%.2f".format(amount)} recorded."
                load()
            } catch (e: Exception) {
                _message.value = "Save failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTransaction(transactionId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repo.deleteTransaction(transactionId)
                _message.value = "Entry deleted."
                load()
            } catch (e: Exception) {
                _message.value = "Delete failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
