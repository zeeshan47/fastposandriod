package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.MessageLog
import com.fastpos.android.data.repositories.MessageLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MessageLogViewModel @Inject constructor(
    private val repo: MessageLogRepository
) : ViewModel() {

    private val _logs          = MutableStateFlow<List<MessageLog>>(emptyList())
    private val _isLoading     = MutableStateFlow(false)
    private val _message       = MutableStateFlow<String?>(null)
    private val _channels      = MutableStateFlow<List<String>>(listOf("(All Channels)"))
    private val _failedToday   = MutableStateFlow(0)

    val logs:        StateFlow<List<MessageLog>> = _logs
    val isLoading:   StateFlow<Boolean>          = _isLoading
    val message:     StateFlow<String?>          = _message
    val channels:    StateFlow<List<String>>     = _channels
    val failedToday: StateFlow<Int>              = _failedToday

    private val _fromDate        = MutableStateFlow(startOfDayMinus(6))
    private val _toDate          = MutableStateFlow(startOfToday())
    private val _selectedChannel = MutableStateFlow("(All Channels)")
    private val _selectedStatus  = MutableStateFlow("(All Statuses)")

    val fromDate:        StateFlow<Date>   = _fromDate
    val toDate:          StateFlow<Date>   = _toDate
    val selectedChannel: StateFlow<String> = _selectedChannel
    val selectedStatus:  StateFlow<String> = _selectedStatus

    val statusOptions = listOf("(All Statuses)", "Sent", "Failed")

    init { loadFiltersAndSearch() }

    fun setFromDate(d: Date)          { _fromDate.value = d }
    fun setToDate(d: Date)            { _toDate.value = d }
    fun setSelectedChannel(c: String) { _selectedChannel.value = c }
    fun setSelectedStatus(s: String)  { _selectedStatus.value = s }

    fun search() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val ch = _selectedChannel.value.takeIf { it != "(All Channels)" }
                val st = _selectedStatus.value.takeIf  { it != "(All Statuses)" }
                _logs.value = repo.getRecent(_fromDate.value, _toDate.value, ch, st)
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
        _selectedChannel.value = "(All Channels)"; _selectedStatus.value = "(All Statuses)"; search()
    }

    fun clearMessage() { _message.value = null }

    private fun loadFiltersAndSearch() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val chs = repo.getDistinctChannels()
                _channels.value  = listOf("(All Channels)") + chs
                _failedToday.value = repo.getFailedCountToday()
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
