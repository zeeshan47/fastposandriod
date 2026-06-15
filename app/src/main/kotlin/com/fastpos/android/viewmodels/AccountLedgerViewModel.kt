package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.AccountLedgerEntry
import com.fastpos.android.data.models.ChartOfAccount
import com.fastpos.android.data.models.TrialBalanceRow
import com.fastpos.android.data.repositories.AccountLedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AccountLedgerViewModel @Inject constructor(
    private val repo: AccountLedgerRepository
) : ViewModel() {

    private val _accounts        = MutableStateFlow<List<ChartOfAccount>>(emptyList())
    private val _ledgerEntries   = MutableStateFlow<List<AccountLedgerEntry>>(emptyList())
    private val _trialBalance    = MutableStateFlow<List<TrialBalanceRow>>(emptyList())
    private val _selectedAccount = MutableStateFlow<ChartOfAccount?>(null)
    private val _fromDate        = MutableStateFlow(startOfMonth())
    private val _toDate          = MutableStateFlow(startOfToday())
    private val _isLoading       = MutableStateFlow(false)
    private val _message         = MutableStateFlow<String?>(null)
    private val _totalDebit      = MutableStateFlow(0.0)
    private val _totalCredit     = MutableStateFlow(0.0)
    private val _netBalance      = MutableStateFlow(0.0)

    val accounts:        StateFlow<List<ChartOfAccount>>     = _accounts
    val ledgerEntries:   StateFlow<List<AccountLedgerEntry>> = _ledgerEntries
    val trialBalance:    StateFlow<List<TrialBalanceRow>>    = _trialBalance
    val selectedAccount: StateFlow<ChartOfAccount?>         = _selectedAccount
    val fromDate:        StateFlow<Date>                     = _fromDate
    val toDate:          StateFlow<Date>                     = _toDate
    val isLoading:       StateFlow<Boolean>                  = _isLoading
    val message:         StateFlow<String?>                  = _message
    val totalDebit:      StateFlow<Double>                   = _totalDebit
    val totalCredit:     StateFlow<Double>                   = _totalCredit
    val netBalance:      StateFlow<Double>                   = _netBalance

    init { loadAccounts() }

    fun setFromDate(d: Date)              { _fromDate.value = d }
    fun setToDate(d: Date)                { _toDate.value = d }
    fun setSelectedAccount(a: ChartOfAccount?) { _selectedAccount.value = a }

    fun filterToday() {
        _fromDate.value = startOfToday(); _toDate.value = startOfToday()
    }

    fun filterThisMonth() {
        _fromDate.value = startOfMonth(); _toDate.value = startOfToday()
    }

    fun clearMessage() { _message.value = null }

    private fun loadAccounts() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _accounts.value = repo.getAllAccounts()
                if (_accounts.value.isNotEmpty() && _selectedAccount.value == null) {
                    _selectedAccount.value = _accounts.value.first()
                }
            } catch (e: Exception) {
                _message.value = "Failed to load accounts: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadLedger() {
        val account = _selectedAccount.value ?: return
        if (_fromDate.value.after(_toDate.value)) {
            _message.value = "From date cannot be after To date."; return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val entries = repo.getLedger(account.accountId, _fromDate.value, _toDate.value)
                _ledgerEntries.value = entries
                _totalDebit.value    = entries.sumOf { it.debit }
                _totalCredit.value   = entries.sumOf { it.credit }
                _netBalance.value    = _totalDebit.value - _totalCredit.value
                _message.value       = "${entries.size} record(s) found."
            } catch (e: Exception) {
                _message.value = "Load failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadTrialBalance() {
        if (_fromDate.value.after(_toDate.value)) {
            _message.value = "From date cannot be after To date."; return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _trialBalance.value = repo.getTrialBalance(_fromDate.value, _toDate.value)
            } catch (e: Exception) {
                _message.value = "Load failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private companion object {
        fun startOfToday(): Date = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time

        fun startOfMonth(): Date = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
    }
}
