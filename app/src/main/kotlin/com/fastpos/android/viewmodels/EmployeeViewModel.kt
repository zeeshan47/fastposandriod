package com.fastpos.android.viewmodels

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Employee
import com.fastpos.android.data.models.EmployeeAdvance
import com.fastpos.android.data.models.SalaryPayment
import com.fastpos.android.data.repositories.EmployeeRepository
import com.fastpos.android.data.repositories.WaiterRepository
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

@HiltViewModel
class EmployeeViewModel @Inject constructor(
    private val employeeRepo: EmployeeRepository,
    private val waiterRepo:   WaiterRepository,
    val session: SessionManager
) : ViewModel() {

    // ── Employees tab ─────────────────────────────────────────────────────────
    private val _allEmployees = MutableStateFlow<List<Employee>>(emptyList())
    private val _isLoading    = MutableStateFlow(false)
    private val _message      = MutableStateFlow<String?>(null)
    private val _search       = MutableStateFlow("")

    val employees: StateFlow<List<Employee>> = _allEmployees
    val isLoading: StateFlow<Boolean>        = _isLoading
    val message:   StateFlow<String?>        = _message
    val search:    StateFlow<String>         = _search

    // ── Advances tab ──────────────────────────────────────────────────────────
    private val _advances              = MutableStateFlow<List<EmployeeAdvance>>(emptyList())
    val advances: StateFlow<List<EmployeeAdvance>> = _advances

    private val _avFilterEmployeeId    = MutableStateFlow<Int?>(null)
    val avFilterEmployeeId: StateFlow<Int?> = _avFilterEmployeeId

    private val _avFilterFrom = MutableStateFlow(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -30) }.time)
    private val _avFilterTo   = MutableStateFlow(Date())
    val avFilterFrom: StateFlow<Date> = _avFilterFrom
    val avFilterTo:   StateFlow<Date> = _avFilterTo

    // ── Salary Payments tab ───────────────────────────────────────────────────
    private val _salaryPayments        = MutableStateFlow<List<SalaryPayment>>(emptyList())
    val salaryPayments: StateFlow<List<SalaryPayment>> = _salaryPayments

    private val _spFilterEmployeeId    = MutableStateFlow<Int?>(null)
    val spFilterEmployeeId: StateFlow<Int?> = _spFilterEmployeeId

    private val _spFilterFrom = MutableStateFlow(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -30) }.time)
    private val _spFilterTo   = MutableStateFlow(Date())
    val spFilterFrom: StateFlow<Date> = _spFilterFrom
    val spFilterTo:   StateFlow<Date> = _spFilterTo

    private val _spFormEmployeeId         = MutableStateFlow<Int?>(null)
    val spFormEmployeeId: StateFlow<Int?> = _spFormEmployeeId

    private val _spEmployeeSalary         = MutableStateFlow(0.0)
    val spEmployeeSalary: StateFlow<Double> = _spEmployeeSalary

    private val _spTotalAdvances          = MutableStateFlow(0.0)
    val spTotalAdvances: StateFlow<Double> = _spTotalAdvances

    private val _spPeriodMonth            = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH) + 1)
    private val _spPeriodYear             = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val spPeriodMonth: StateFlow<Int>     = _spPeriodMonth
    val spPeriodYear: StateFlow<Int>      = _spPeriodYear

    private val _spAlreadyPaidThisPeriod  = MutableStateFlow(0.0)
    val spAlreadyPaidThisPeriod: StateFlow<Double> = _spAlreadyPaidThisPeriod

    val spNetPayable: Double
        get() = (_spEmployeeSalary.value - _spTotalAdvances.value).coerceAtLeast(0.0)
    val spRemainingPayable: Double
        get() = (spNetPayable - _spAlreadyPaidThisPeriod.value).coerceAtLeast(0.0)
    val spPeriodFullyPaid: Boolean
        get() = _spAlreadyPaidThisPeriod.value > 0 && spRemainingPayable <= 0.01

    init {
        viewModelScope.launch {
            try { employeeRepo.initHrSchema() } catch (_: Exception) {}
            load()
            loadAdvances()
            loadSalaryPayments()
        }
    }

    fun setSearch(q: String) {
        _search.value = q
        load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val q   = _search.value
                val all = employeeRepo.getEmployees()
                _allEmployees.value = if (q.isBlank()) all
                    else all.filter {
                        it.employeeName.contains(q, true) || it.phone.contains(q, true) || it.employeeRole.contains(q, true)
                    }
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addEmployee(
        name: String,
        phone: String = "", designation: String = "",
        joiningDate: Date? = null, monthlySalary: Double = 0.0
    ) {
        if (name.isBlank()) { _message.value = "Employee name is required."; return }
        viewModelScope.launch {
            try {
                val id = employeeRepo.addEmployee(
                    name, phone, designation, joiningDate, monthlySalary,
                    branchId  = session.currentBranchId.value,
                    createdBy = session.userId
                )
                _message.value = "Employee added."
                if (designation == "Waiter") autoLinkWaiter(id, name, phone)
                load()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun updateEmployee(
        employeeId: Int, name: String,
        phone: String = "", designation: String = "",
        joiningDate: Date? = null, monthlySalary: Double = 0.0, isActive: Boolean = true
    ) {
        if (name.isBlank()) { _message.value = "Employee name is required."; return }
        viewModelScope.launch {
            try {
                employeeRepo.updateEmployee(employeeId, name, phone, designation, joiningDate, monthlySalary, isActive)
                _message.value = "Employee updated."
                if (designation == "Waiter") autoLinkWaiter(employeeId, name, phone)
                load()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    private suspend fun autoLinkWaiter(employeeId: Int, name: String, phone: String) {
        try {
            if (waiterRepo.isLinkedToEmployee(employeeId)) return
            waiterRepo.createLinkedToEmployee(employeeId, name, phone, createdBy = session.userId)
            _message.value = (_message.value ?: "") + " Registered as Waiter automatically."
        } catch (_: Exception) {}
    }

    fun toggleActive(employee: Employee) {
        viewModelScope.launch {
            try {
                employeeRepo.toggleActive(employee.employeeId, !employee.isActive)
                _message.value = if (!employee.isActive) "${employee.employeeName} activated."
                                 else "${employee.employeeName} deactivated."
                load()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    // ── Advances ──────────────────────────────────────────────────────────────

    fun setAvFilterEmployeeId(id: Int?) {
        _avFilterEmployeeId.value = id
        loadAdvances()
    }

    fun setAvFilterDates(from: Date, to: Date) {
        _avFilterFrom.value = from
        _avFilterTo.value   = to
        loadAdvances()
    }

    fun loadAdvances() = viewModelScope.launch {
        _avFilterTo.value = Date() // always include advances up to right now
        runCatching {
            _advances.value = employeeRepo.getAdvances(
                _avFilterEmployeeId.value, _avFilterFrom.value, _avFilterTo.value)
        }
    }

    fun saveAdvance(
        employeeId: Int, amount: Double, notes: String,
        date: Date = Date(), paymentMethod: String = "Cash"
    ) = viewModelScope.launch {
        if (employeeId == 0 || amount <= 0) { _message.value = "Select employee and enter valid amount"; return@launch }
        runCatching {
            val shiftId = session.currentShift.value?.shiftId
            employeeRepo.saveAdvance(employeeId, amount, notes, date, paymentMethod, shiftId,
                branchId  = session.currentBranchId.value,
                createdBy = session.userId)
            loadAdvances()
            _message.value = "Advance recorded"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun deleteAdvance(advanceId: Int) = viewModelScope.launch {
        runCatching {
            employeeRepo.deleteAdvance(advanceId)
            loadAdvances()
            _message.value = "Advance deleted"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    // ── Salary Payments ────────────────────────────────────────────────────────

    fun setSpFilterEmployeeId(id: Int?) {
        _spFilterEmployeeId.value = id
        loadSalaryPayments()
    }

    fun setSpFilterDates(from: Date, to: Date) {
        _spFilterFrom.value = from
        _spFilterTo.value   = to
        loadSalaryPayments()
    }

    fun loadSalaryPayments() = viewModelScope.launch {
        _spFilterTo.value = Date()
        runCatching {
            _salaryPayments.value = employeeRepo.getSalaryPayments(
                _spFilterEmployeeId.value, _spFilterFrom.value, _spFilterTo.value)
        }
    }

    fun setSpFormEmployee(employeeId: Int?) {
        _spFormEmployeeId.value = employeeId
        if (employeeId == null) {
            _spEmployeeSalary.value = 0.0
            _spTotalAdvances.value = 0.0
            _spAlreadyPaidThisPeriod.value = 0.0
            return
        }
        viewModelScope.launch {
            _spEmployeeSalary.value = employeeRepo.getEmployeeSalary(employeeId)
            _spTotalAdvances.value  = employeeRepo.getTotalActiveAdvances(employeeId)
            loadSpAlreadyPaid()
        }
    }

    fun setSpPeriod(month: Int, year: Int) {
        _spPeriodMonth.value = month
        _spPeriodYear.value  = year
        loadSpAlreadyPaid()
    }

    private fun loadSpAlreadyPaid() {
        val empId = _spFormEmployeeId.value ?: return
        viewModelScope.launch {
            try {
                _spAlreadyPaidThisPeriod.value = employeeRepo.getSalaryPaidForMonth(
                    empId, _spPeriodMonth.value, _spPeriodYear.value)
            } catch (_: Exception) {}
        }
    }

    fun saveSalaryPayment(
        employeeId: Int, amount: Double,
        periodMonth: Int, periodYear: Int,
        method: String, notes: String
    ) = viewModelScope.launch {
        if (employeeId == 0 || amount <= 0) { _message.value = "Select employee and enter valid amount."; return@launch }
        val netPay = spNetPayable
        val alreadyPaid = _spAlreadyPaidThisPeriod.value
        val remaining = spRemainingPayable
        val periodLabel = "${listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec").getOrElse(periodMonth - 1) { periodMonth.toString() }} $periodYear"

        if (netPay > 0 && amount > netPay) {
            _message.value = "Amount exceeds net payable (Rs. ${netPay.toLong()})."; return@launch
        }
        if (alreadyPaid > 0 && remaining <= 0.01) {
            _message.value = "Salary for $periodLabel is already fully paid (Rs. ${alreadyPaid.toLong()})."; return@launch
        }
        if (alreadyPaid > 0 && amount > remaining) {
            _message.value = "Only Rs. ${remaining.toLong()} remaining for $periodLabel."; return@launch
        }
        runCatching {
            val shiftId = session.currentShift.value?.shiftId
            employeeRepo.saveSalaryPayment(employeeId, amount, periodMonth, periodYear, method, notes, shiftId,
                branchId = session.currentBranchId.value)
            _spAlreadyPaidThisPeriod.value = employeeRepo.getSalaryPaidForMonth(employeeId, periodMonth, periodYear)
            _spFilterTo.value = Date()
            _salaryPayments.value = employeeRepo.getSalaryPayments(
                _spFilterEmployeeId.value, _spFilterFrom.value, _spFilterTo.value)
            _message.value = "Salary payment of Rs. ${amount.toLong()} recorded."
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun deleteSalaryPayment(paymentId: Int) = viewModelScope.launch {
        runCatching {
            employeeRepo.deleteSalaryPayment(paymentId)
            loadSalaryPayments()
            _message.value = "Payment deleted"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun clearMessage() { _message.value = null }

    // ── Export CSV ─────────────────────────────────────────────────────────────

    fun exportEmployees(context: Context) {
        val list = _allEmployees.value
        if (list.isEmpty()) { _message.value = "No employees to export"; return }
        val dFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val sb = StringBuilder("ID,Name,Phone,Role,Joining Date,Monthly Salary,Status\n")
        list.forEach { e ->
            sb.append("${e.employeeId},\"${e.employeeName}\",\"${e.phone}\",\"${e.employeeRole}\",")
              .append("\"${if (e.joiningDate != null) dFmt.format(e.joiningDate) else ""}\",")
              .append("${e.monthlySalary.toLong()},${if (e.isActive) "Active" else "Inactive"}\n")
        }
        shareCsv(context, sb.toString(), "Employees_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv")
    }

    fun exportAdvances(context: Context) {
        val list = _advances.value
        if (list.isEmpty()) { _message.value = "No advances to export"; return }
        val dFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val sb = StringBuilder("Date,Employee,Amount,Method,Notes\n")
        list.forEach { a ->
            sb.append("\"${dFmt.format(a.advanceDate)}\",\"${a.employeeName}\",${a.amount},\"${a.paymentMethod}\",\"${a.notes}\"\n")
        }
        shareCsv(context, sb.toString(), "EmployeeAdvances_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv")
    }

    fun exportSalaryPayments(context: Context) {
        val list = _salaryPayments.value
        if (list.isEmpty()) { _message.value = "No payments to export"; return }
        val dFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val sb = StringBuilder("Date,Employee,Period,Amount,Method,Notes\n")
        list.forEach { p ->
            sb.append("\"${dFmt.format(p.paymentDate)}\",\"${p.employeeName}\",\"${p.periodLabel}\",${p.amount},\"${p.paymentMethod}\",\"${p.notes}\"\n")
        }
        shareCsv(context, sb.toString(), "SalaryPayments_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv")
    }

    private fun shareCsv(context: Context, content: String, filename: String) {
        try {
            val file = File(context.cacheDir, filename)
            file.writeText(content, Charsets.UTF_8)
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
}
