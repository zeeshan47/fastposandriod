package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.AuditLogRow
import com.fastpos.android.data.repositories.AuditLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val repo: AuditLogRepository
) : ViewModel() {

    private val _logs          = MutableStateFlow<List<AuditLogRow>>(emptyList())
    private val _actions       = MutableStateFlow<List<String>>(listOf("(All Actions)"))
    private val _tables        = MutableStateFlow<List<String>>(listOf("(All Tables)"))
    private val _isLoading     = MutableStateFlow(false)
    private val _message       = MutableStateFlow<String?>(null)

    val logs:      StateFlow<List<AuditLogRow>> = _logs
    val actions:   StateFlow<List<String>>      = _actions
    val tables:    StateFlow<List<String>>      = _tables
    val isLoading: StateFlow<Boolean>           = _isLoading
    val message:   StateFlow<String?>           = _message

    private val _fromDate      = MutableStateFlow(startOfDayMinus(6))
    private val _toDate        = MutableStateFlow(startOfToday())
    private val _selectedAction = MutableStateFlow("(All Actions)")
    private val _selectedTable  = MutableStateFlow("(All Tables)")

    val fromDate:       StateFlow<Date>   = _fromDate
    val toDate:         StateFlow<Date>   = _toDate
    val selectedAction: StateFlow<String> = _selectedAction
    val selectedTable:  StateFlow<String> = _selectedTable

    init { loadFiltersAndSearch() }

    fun setFromDate(d: Date)        { _fromDate.value = d }
    fun setToDate(d: Date)          { _toDate.value = d }
    fun setSelectedAction(a: String) { _selectedAction.value = a }
    fun setSelectedTable(t: String)  { _selectedTable.value = t }

    fun search() {
        if (_fromDate.value.after(_toDate.value)) {
            _message.value = "From date cannot be after To date."; return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val actionFilter = _selectedAction.value.takeIf { it != "(All Actions)" }
                val tableFilter  = _selectedTable.value.takeIf  { it != "(All Tables)" }
                val rows = repo.getLogs(_fromDate.value, _toDate.value, actionFilter, tableFilter)
                _logs.value    = rows
                _message.value = "${rows.size} record(s) found."
            } catch (e: Exception) {
                _message.value = "Search failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filterToday() {
        _fromDate.value = startOfToday(); _toDate.value = startOfToday(); search()
    }

    fun filterThisWeek() {
        _fromDate.value = startOfDayMinus(6); _toDate.value = startOfToday(); search()
    }

    fun clearFilters() {
        _selectedAction.value = "(All Actions)"; _selectedTable.value = "(All Tables)"; search()
    }

    fun clearMessage() { _message.value = null }

    private fun loadFiltersAndSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val actionList = repo.getDistinctActions()
                _actions.value = listOf("(All Actions)") + actionList
                val tableList  = repo.getDistinctTables()
                _tables.value  = listOf("(All Tables)") + tableList
                search()
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

        fun startOfDayMinus(days: Int): Date = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.time
    }
}
