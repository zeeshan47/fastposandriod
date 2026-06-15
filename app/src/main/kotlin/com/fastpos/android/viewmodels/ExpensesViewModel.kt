package com.fastpos.android.viewmodels

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Expense
import com.fastpos.android.data.repositories.ExpenseRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private val BASE_EXPENSE_TYPES = listOf("Utilities", "Salary", "Supplies", "Maintenance", "Fuel", "Cleaning", "Other")

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val repo:    ExpenseRepository,
    val session: SessionManager
) : ViewModel() {

    val expenseTypes: List<String> = listOf("All") + BASE_EXPENSE_TYPES

    private val _expenses    = MutableStateFlow<List<Expense>>(emptyList())
    private val _isLoading   = MutableStateFlow(false)
    private val _isSaving    = MutableStateFlow(false)
    private val _message     = MutableStateFlow<String?>(null)
    private val _total       = MutableStateFlow(0.0)

    // Filters
    private val _fromDate    = MutableStateFlow(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -30) }.time)
    private val _toDate      = MutableStateFlow(Date())
    private val _typeFilter  = MutableStateFlow("All")

    init {
        loadExpenses()
    }

    val expenses:   StateFlow<List<Expense>> = _expenses
    val isLoading:  StateFlow<Boolean>       = _isLoading
    val isSaving:   StateFlow<Boolean>       = _isSaving
    val message:    StateFlow<String?>       = _message
    val total:      StateFlow<Double>        = _total
    val fromDate:   StateFlow<Date>          = _fromDate
    val toDate:     StateFlow<Date>          = _toDate
    val typeFilter: StateFlow<String>        = _typeFilter

    fun setFromDate(d: Date)     { _fromDate.value = d; loadExpenses() }
    fun setToDate(d: Date)       { _toDate.value = d; loadExpenses() }
    fun setTypeFilter(t: String) { _typeFilter.value = t; loadExpenses() }

    fun loadExpenses() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val type = if (_typeFilter.value == "All") "" else _typeFilter.value
                val list = repo.getExpenses(_fromDate.value, _toDate.value, type)
                _expenses.value = list
                _total.value    = list.sumOf { it.amount }
            } catch (e: Exception) {
                _message.value = "Failed to load: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addExpense(type: String, description: String, amount: Double, paidTo: String, paymentMethod: String = "Cash") {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val userId  = session.currentUser.value?.userId ?: 0
                val shiftId = session.currentShift.value?.shiftId
                repo.addExpense(shiftId, type, description, amount, paidTo, paymentMethod, userId)
                _message.value = "Expense of ${formatAmt(amount)} added."
                loadExpenses()
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun updateExpense(expenseId: Int, type: String, description: String, amount: Double, paidTo: String, paymentMethod: String = "Cash") {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val userId = session.currentUser.value?.userId ?: 0
                repo.updateExpense(expenseId, type, description, amount, paidTo, paymentMethod, userId)
                _message.value = "Expense updated."
                loadExpenses()
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteExpense(expenseId: Int) {
        viewModelScope.launch {
            try {
                val userId = session.currentUser.value?.userId ?: 0
                repo.deleteExpense(expenseId, userId)
                _message.value = "Expense deleted."
                loadExpenses()
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun exportExpenses(context: Context) {
        val list = _expenses.value
        if (list.isEmpty()) { _message.value = "No expenses to export"; return }
        val dFmt  = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
        val label = SimpleDateFormat("yyyyMMdd", Locale.US).let { "${it.format(_fromDate.value)}_${it.format(_toDate.value)}" }
        val sb = StringBuilder("Date,Type,Description,Amount,Paid To\n")
        list.forEach { e ->
            sb.append("\"${dFmt.format(e.expenseDate)}\",\"${e.expenseType}\",\"${e.description}\",")
              .append("${e.amount},\"${e.paidTo}\"\n")
        }
        try {
            val file = File(context.cacheDir, "Expenses_$label.csv")
            file.writeText(sb.toString(), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export CSV"))
        } catch (e: Exception) {
            _message.value = "Export failed: ${e.message}"
        }
    }

    fun clearMessage() { _message.value = null }

    private fun formatAmt(a: Double) = "${session.settings.value.currencySymbol} %.2f".format(a)
}
