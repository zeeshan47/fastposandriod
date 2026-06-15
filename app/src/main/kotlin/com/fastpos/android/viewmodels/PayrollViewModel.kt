package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.EmployeeAdvance
import com.fastpos.android.data.models.EmployeeSalaryInfo
import com.fastpos.android.data.models.PayrollRow
import com.fastpos.android.data.repositories.PayrollRepository
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class PayrollViewModel @Inject constructor(
    private val repo:  PayrollRepository,
    private val prefs: PreferencesManager,
    val session:       SessionManager
) : ViewModel() {

    private val now = Calendar.getInstance()

    private val _filterMonth    = MutableStateFlow(now.get(Calendar.MONTH) + 1)
    private val _filterYear     = MutableStateFlow(now.get(Calendar.YEAR))
    private val _payrollRows    = MutableStateFlow<List<PayrollRow>>(emptyList())
    private val _salaryList     = MutableStateFlow<List<EmployeeSalaryInfo>>(emptyList())
    private val _advanceHistory = MutableStateFlow<List<EmployeeAdvance>>(emptyList())
    private val _isLoading      = MutableStateFlow(false)
    private val _message        = MutableStateFlow<String?>(null)
    private val _totalSalary    = MutableStateFlow(0.0)
    private val _totalAdvances  = MutableStateFlow(0.0)
    private val _totalNetPay    = MutableStateFlow(0.0)

    val filterMonth:    StateFlow<Int>                    = _filterMonth
    val filterYear:     StateFlow<Int>                    = _filterYear
    val payrollRows:    StateFlow<List<PayrollRow>>       = _payrollRows
    val salaryList:     StateFlow<List<EmployeeSalaryInfo>> = _salaryList
    val advanceHistory: StateFlow<List<EmployeeAdvance>>  = _advanceHistory
    val isLoading:      StateFlow<Boolean>                = _isLoading
    val message:        StateFlow<String?>                = _message
    val totalSalary:    StateFlow<Double>                 = _totalSalary
    val totalAdvances:  StateFlow<Double>                 = _totalAdvances
    val totalNetPay:    StateFlow<Double>                 = _totalNetPay

    fun filterMonthLabel(): String =
        DateFormatSymbols().months.getOrElse(_filterMonth.value - 1) { _filterMonth.value.toString() } + " ${_filterYear.value}"

    init {
        viewModelScope.launch {
            try { repo.initSchema() } catch (_: Exception) {}
            load()
        }
    }

    fun prevMonth() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, _filterMonth.value - 1)
            set(Calendar.YEAR, _filterYear.value)
            add(Calendar.MONTH, -1)
        }
        _filterMonth.value = cal.get(Calendar.MONTH) + 1
        _filterYear.value  = cal.get(Calendar.YEAR)
        load()
    }

    fun nextMonth() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MONTH, _filterMonth.value - 1)
            set(Calendar.YEAR, _filterYear.value)
            add(Calendar.MONTH, 1)
        }
        _filterMonth.value = cal.get(Calendar.MONTH) + 1
        _filterYear.value  = cal.get(Calendar.YEAR)
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val employees = repo.getActiveEmployees()
                val month = _filterMonth.value
                val year  = _filterYear.value

                val cal = Calendar.getInstance().apply { set(year, month - 1, 1) }
                val monthStart = java.sql.Date(cal.timeInMillis)
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                val monthEnd = java.sql.Date(cal.timeInMillis)

                val rows = employees.map { emp ->
                    val advances = repo.getAdvancesForMonth(emp.userId, monthStart, monthEnd)
                    val paid     = repo.getSalaryPaidForMonth(emp.userId, month, year)
                    PayrollRow(
                        employeeId        = emp.userId,
                        employeeName      = emp.fullName,
                        employeeRole      = emp.roleName,
                        monthlySalary     = emp.basicSalary,
                        advancesThisMonth = advances,
                        alreadyPaid       = paid
                    )
                }

                _payrollRows.value   = rows
                _salaryList.value    = employees
                _totalSalary.value   = rows.sumOf { it.monthlySalary }
                _totalAdvances.value = rows.sumOf { it.advancesThisMonth }
                _totalNetPay.value   = rows.sumOf { it.netPay }
                _advanceHistory.value = repo.getAdvanceHistory()
            } catch (e: Exception) {
                _message.value = "Load failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun paySalary(row: PayrollRow) {
        if (row.remainingPay <= 0 || row.isPaid) return
        viewModelScope.launch {
            try {
                repo.recordSalaryPayment(
                    employeeId = row.employeeId,
                    amount     = row.remainingPay,
                    month      = _filterMonth.value,
                    year       = _filterYear.value,
                    notes      = "Payroll – ${filterMonthLabel()}",
                    createdBy  = session.userId,
                    shiftId    = session.currentShift.value?.shiftId
                )
                _message.value = "Salary paid to ${row.employeeName}."
                load()
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun payAll() {
        val unpaid = _payrollRows.value.filter { !it.isPaid && it.remainingPay > 0 }
        if (unpaid.isEmpty()) return
        viewModelScope.launch {
            try {
                val monthLabel = filterMonthLabel()
                unpaid.forEach { row ->
                    repo.recordSalaryPayment(
                        employeeId = row.employeeId,
                        amount     = row.remainingPay,
                        month      = _filterMonth.value,
                        year       = _filterYear.value,
                        notes      = "Payroll – $monthLabel",
                        createdBy  = session.userId,
                        shiftId    = session.currentShift.value?.shiftId
                    )
                }
                _message.value = "${unpaid.size} salaries paid for $monthLabel."
                load()
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun addAdvance(employeeId: Int, amount: Double, notes: String, paymentMethod: String = "Cash") {
        if (amount <= 0) { _message.value = "Enter a valid amount."; return }
        viewModelScope.launch {
            try {
                repo.recordAdvance(
                    employeeId    = employeeId,
                    amount        = amount,
                    notes         = notes,
                    createdBy     = session.userId,
                    shiftId       = session.currentShift.value?.shiftId,
                    paymentMethod = paymentMethod
                )
                _message.value = "Advance recorded."
                load()
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun deleteAdvance(advanceId: Int) {
        viewModelScope.launch {
            try {
                val currentUserId = session.currentUser.value?.userId ?: 0
                repo.deleteAdvance(advanceId, currentUserId)
                _advanceHistory.value = _advanceHistory.value.filter { it.advanceId != advanceId }
                _message.value = "Advance removed."
                load()
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun updateSalary(userId: Int, salary: Double) {
        viewModelScope.launch {
            try {
                val currentUserId = session.currentUser.value?.userId ?: 0
                repo.updateEmployeeSalary(userId, salary, currentUserId)
                _message.value = "Salary updated."
                load()
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun printPayslip(row: PayrollRow, companyName: String, sym: String) {
        viewModelScope.launch {
            try {
                val address = prefs.savedPrinterAddress.first()
                if (address.isBlank()) { _message.value = "No printer configured."; return@launch }
                val result = BluetoothPrinterHelper.printPayslip(address, row, _filterMonth.value, _filterYear.value, companyName, sym)
                _message.value = if (result.isSuccess) "Payslip printed." else "Print failed: ${result.exceptionOrNull()?.message}"
            } catch (e: Exception) {
                _message.value = "Print failed: ${e.message}"
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
