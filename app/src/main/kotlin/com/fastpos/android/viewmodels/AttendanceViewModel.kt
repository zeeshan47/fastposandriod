package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.AttendanceRecord
import com.fastpos.android.data.repositories.AttendanceRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val repo: AttendanceRepository,
    val session: SessionManager
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(Date())
    private val _attendance   = MutableStateFlow<List<AttendanceRecord>>(emptyList())
    private val _isLoading    = MutableStateFlow(false)
    private val _message      = MutableStateFlow<String?>(null)

    val selectedDate: StateFlow<Date>                   = _selectedDate
    val attendance:   StateFlow<List<AttendanceRecord>> = _attendance
    val isLoading:    StateFlow<Boolean>                = _isLoading
    val message:      StateFlow<String?>                = _message

    init {
        viewModelScope.launch {
            try { repo.initSchema() } catch (_: Exception) {}
            loadAttendance()
        }
    }

    fun loadAttendance(date: Date = _selectedDate.value) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _attendance.value = repo.getAttendanceForDate(date)
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setDate(date: Date) {
        _selectedDate.value = date
        loadAttendance(date)
    }

    fun checkIn(employeeId: Int) {
        viewModelScope.launch {
            try {
                repo.checkIn(employeeId, _selectedDate.value)
                loadAttendance(_selectedDate.value)
            } catch (e: Exception) {
                _message.value = "Check-in failed: ${e.message}"
            }
        }
    }

    fun checkOut(attendanceId: Int) {
        if (attendanceId <= 0) return
        viewModelScope.launch {
            try {
                repo.checkOut(attendanceId)
                loadAttendance(_selectedDate.value)
            } catch (e: Exception) {
                _message.value = "Check-out failed: ${e.message}"
            }
        }
    }

    fun removeAttendance(employeeId: Int) {
        viewModelScope.launch {
            try {
                repo.removeAttendance(employeeId, _selectedDate.value)
                _attendance.value = _attendance.value.map { rec ->
                    if (rec.employeeId == employeeId) rec.copy(checkInTime = null, checkOutTime = null, attendanceId = 0) else rec
                }
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
