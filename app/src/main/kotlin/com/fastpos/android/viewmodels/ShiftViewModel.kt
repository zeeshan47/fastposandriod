package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Expense
import com.fastpos.android.data.models.Shift
import com.fastpos.android.data.models.ShiftCashTotals
import com.fastpos.android.data.models.ShiftPaymentSummary
import com.fastpos.android.data.repositories.CashDrawerRepository
import com.fastpos.android.data.repositories.ExpenseRepository
import com.fastpos.android.data.repositories.ShiftRepository
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShiftViewModel @Inject constructor(
    private val shiftRepo:      ShiftRepository,
    private val expenseRepo:    ExpenseRepository,
    private val cashDrawerRepo: CashDrawerRepository,
    private val prefs:          PreferencesManager,
    val session:                SessionManager
) : ViewModel() {

    private val _shift             = MutableStateFlow<Shift?>(null)
    private val _expenses          = MutableStateFlow<List<Expense>>(emptyList())
    private val _isLoading         = MutableStateFlow(false)
    private val _message           = MutableStateFlow<String?>(null)
    private val _shiftSummary      = MutableStateFlow<List<ShiftPaymentSummary>>(emptyList())
    private val _summaryLoading    = MutableStateFlow(false)
    private val _isPrintingZReport  = MutableStateFlow(false)
    private val _cashTotals         = MutableStateFlow(ShiftCashTotals())
    private val _zReportPreviewText = MutableStateFlow<String?>(null)
    private val _pendingOrderCount  = MutableStateFlow(0)
    private val _closeSheetRequest  = MutableStateFlow(0)

    val shift:             StateFlow<Shift?>                    = _shift
    val expenses:          StateFlow<List<Expense>>             = _expenses
    val isLoading:         StateFlow<Boolean>                   = _isLoading
    val message:           StateFlow<String?>                   = _message
    val shiftSummary:      StateFlow<List<ShiftPaymentSummary>> = _shiftSummary
    val summaryLoading:    StateFlow<Boolean>                   = _summaryLoading
    val isPrintingZReport:  StateFlow<Boolean>    = _isPrintingZReport
    val cashTotals:         StateFlow<ShiftCashTotals> = _cashTotals
    val zReportPreviewText: StateFlow<String?>    = _zReportPreviewText
    val pendingOrderCount:  StateFlow<Int>        = _pendingOrderCount
    val closeSheetRequest:  StateFlow<Int>        = _closeSheetRequest

    init {
        loadShift()
        viewModelScope.launch {
            while (isActive) {
                delay(session.pollIntervalMs)
                try {
                    val s = shiftRepo.getOpenShift(session.userId) ?: shiftRepo.getAnyOpenShift()
                    session.setShift(s)
                    _shift.value = s
                    if (s != null) _expenses.value = expenseRepo.getExpensesByShift(s.shiftId)
                    else _expenses.value = emptyList()
                } catch (_: Exception) { }
            }
        }
    }

    fun loadShift() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Always re-query DB so WPF-opened shifts are recognised
                val userId = session.userId
                val s = shiftRepo.getOpenShift(userId) ?: shiftRepo.getAnyOpenShift()
                session.setShift(s)
                _shift.value = s
                if (s != null) {
                    _expenses.value = expenseRepo.getExpensesByShift(s.shiftId)
                } else {
                    _expenses.value = emptyList()
                }
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun openShift(openingCash: Double) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val shiftId = shiftRepo.openShift(session.userId, openingCash)
                val shift   = if (shiftId > 0) shiftRepo.getShiftById(shiftId) ?: shiftRepo.getAnyOpenShift()
                              else shiftRepo.getAnyOpenShift()
                session.setShift(shift)
                _shift.value   = shift
                _message.value = if (shift != null) "Shift opened successfully." else "Failed to open shift."
            } catch (e: Exception) {
                _message.value = "Failed to open shift: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkPendingOrdersBeforeClose(shiftId: Int) {
        viewModelScope.launch {
            _pendingOrderCount.value = shiftRepo.getPendingOrderCount(shiftId)
        }
    }

    fun requestCloseShiftSheet() {
        val shift = _shift.value ?: return
        viewModelScope.launch {
            val pendingOrders = shiftRepo.getPendingOrderCount(shift.shiftId)
            _pendingOrderCount.value = pendingOrders
            if (pendingOrders > 0) {
                _message.value = if (pendingOrders == 1) {
                    "Cannot close shift: 1 order is still pending."
                } else {
                    "Cannot close shift: $pendingOrders orders are still pending."
                }
                return@launch
            }

            _summaryLoading.value = true
            try {
                _shiftSummary.value = shiftRepo.getShiftPaymentSummary(shift.shiftId)
                _cashTotals.value   = shiftRepo.getShiftCashTotals(shift.shiftId)
                _closeSheetRequest.value = _closeSheetRequest.value + 1
            } catch (_: Exception) {
                _shiftSummary.value = emptyList()
                _cashTotals.value   = ShiftCashTotals()
                _closeSheetRequest.value = _closeSheetRequest.value + 1
            } finally {
                _summaryLoading.value = false
            }
        }
    }

    fun closeShift(closingCash: Double, notes: String) {
        val shift = _shift.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val pendingOrders = shiftRepo.getPendingOrderCount(shift.shiftId)
                _pendingOrderCount.value = pendingOrders
                if (pendingOrders > 0) {
                    _message.value = if (pendingOrders == 1) {
                        "Cannot close shift: 1 order is still pending."
                    } else {
                        "Cannot close shift: $pendingOrders orders are still pending."
                    }
                    return@launch
                }

                shiftRepo.closeShift(shift.shiftId, closingCash, notes)
                // Reload shift so closingCash/closingTime are populated for auto-print
                val closedShift = shiftRepo.getShiftById(shift.shiftId)
                session.setShift(null)
                _shift.value          = null
                _pendingOrderCount.value = 0
                _message.value        = "Shift closed successfully."
                // Auto-print Z-report
                closedShift?.let { firePrintZReport(it) }
            } catch (e: Exception) {
                _message.value = "Failed to close shift: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addExpense(type: String, description: String, amount: Double, paidTo: String, paymentMethod: String = "Cash") {
        viewModelScope.launch {
            try {
                expenseRepo.addExpense(
                    shiftId       = _shift.value?.shiftId,
                    type          = type,
                    description   = description,
                    amount        = amount,
                    paidTo        = paidTo,
                    paymentMethod = paymentMethod,
                    createdBy     = session.userId
                )
                _shift.value?.let { _expenses.value = expenseRepo.getExpensesByShift(it.shiftId) }
                _message.value = "Expense added."
            } catch (e: Exception) {
                _message.value = "Failed to add expense: ${e.message}"
            }
        }
    }

    fun deleteExpense(expenseId: Int) {
        viewModelScope.launch {
            try {
                expenseRepo.deleteExpense(expenseId, session.userId)
                _shift.value?.let { _expenses.value = expenseRepo.getExpensesByShift(it.shiftId) }
                _message.value = "Expense deleted."
            } catch (e: Exception) { _message.value = "Failed to delete: ${e.message}" }
        }
    }

    fun editExpense(expenseId: Int, type: String, description: String, amount: Double, paidTo: String, paymentMethod: String = "Cash") {
        viewModelScope.launch {
            try {
                expenseRepo.updateExpense(expenseId, type, description, amount, paidTo, paymentMethod, session.userId)
                _shift.value?.let { _expenses.value = expenseRepo.getExpensesByShift(it.shiftId) }
                _message.value = "Expense updated."
            } catch (e: Exception) { _message.value = "Failed to update: ${e.message}" }
        }
    }

    fun loadShiftSummary() {
        val shiftId = _shift.value?.shiftId ?: return
        viewModelScope.launch {
            _summaryLoading.value = true
            try {
                _shiftSummary.value = shiftRepo.getShiftPaymentSummary(shiftId)
                _cashTotals.value   = shiftRepo.getShiftCashTotals(shiftId)
            } catch (_: Exception) {
                _shiftSummary.value = emptyList()
                _cashTotals.value   = ShiftCashTotals()
            } finally {
                _summaryLoading.value = false
            }
        }
    }

    fun printZReport() {
        val shift = _shift.value ?: return
        viewModelScope.launch {
            _isPrintingZReport.value = true
            firePrintZReport(shift)
            _isPrintingZReport.value = false
        }
    }

    private suspend fun firePrintZReport(shift: Shift) {
        val settings = session.settings.value
        val summary  = try { shiftRepo.getShiftPaymentSummary(shift.shiftId) } catch (_: Exception) { _shiftSummary.value }
        val expenses = try { expenseRepo.getExpensesByShift(shift.shiftId) } catch (_: Exception) { _expenses.value }
        val cashTx   = try { cashDrawerRepo.getByShift(shift.shiftId) } catch (_: Exception) { emptyList() }
        val (salaries, advances) = try { shiftRepo.getShiftPayrollTotals(shift.shiftId) } catch (_: Exception) { Pair(0.0, 0.0) }

        val btAddress  = prefs.savedPrinterAddress.first()
        val netIp      = prefs.receiptNetIp.first()
        val netPort    = prefs.receiptNetPort.first()
        val prType     = prefs.receiptPrinterType.first()

        val hasPrinter = (prType == "Network" && netIp.isNotBlank()) || btAddress.isNotBlank()

        if (!hasPrinter) {
            // No printer configured — show on-screen preview
            _zReportPreviewText.value = buildZReportText(shift, summary, expenses, cashTx, salaries, advances, settings.currencySymbol, settings.companyName)
            return
        }

        val result = if (prType == "Network" && netIp.isNotBlank()) {
            com.fastpos.android.utils.NetworkPrinterHelper.printZReport(
                ip               = netIp,
                port             = netPort,
                shift            = shift,
                summary          = summary,
                expenses         = expenses,
                cashTransactions = cashTx,
                salariesPaid     = salaries,
                advancesPaid     = advances,
                sym              = settings.currencySymbol,
                companyName      = settings.companyName
            )
        } else {
            BluetoothPrinterHelper.printZReport(
                address          = btAddress,
                shift            = shift,
                summary          = summary,
                expenses         = expenses,
                cashTransactions = cashTx,
                salariesPaid     = salaries,
                advancesPaid     = advances,
                sym              = settings.currencySymbol,
                companyName      = settings.companyName
            )
        }
        result.onSuccess { _message.value = "Z-Report printed." }
              .onFailure {
                  _message.value = "Print failed: ${it.message}"
                  // Print failed — fall back to preview
                  _zReportPreviewText.value = buildZReportText(shift, summary, expenses, cashTx, salaries, advances, settings.currencySymbol, settings.companyName)
              }
    }

    private fun buildZReportText(
        shift: Shift,
        summary: List<ShiftPaymentSummary>,
        expenses: List<com.fastpos.android.data.models.Expense>,
        cashTx: List<com.fastpos.android.data.models.CashTransaction>,
        salariesPaid: Double,
        advancesPaid: Double,
        sym: String,
        companyName: String
    ): String {
        val dtFmt   = java.text.SimpleDateFormat("dd MMM yyyy  hh:mm a", java.util.Locale.ENGLISH)
        val timeFmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
        fun amt(d: Double) = "$sym ${"%.0f".format(d)}"
        fun row(label: String, value: String) = "${label.padEnd(22)}$value"
        fun line(c: Char = '-') = c.toString().repeat(34)
        return buildString {
            appendLine(line('='))
            appendLine(companyName.take(34))
            appendLine("SHIFT Z-REPORT")
            appendLine(dtFmt.format(java.util.Date()))
            appendLine(line('='))
            appendLine(row("Shift:", shift.shiftCode))
            appendLine(row("Opened:", dtFmt.format(shift.openingTime)))
            shift.closingTime?.let { appendLine(row("Closed:", dtFmt.format(it))) }
            appendLine(row("Total Orders:", summary.sumOf { it.txCount }.toString()))
            appendLine(line())
            appendLine("SALES SUMMARY")
            appendLine(row("Total Sales:", amt(shift.totalSales)))
            if (summary.isNotEmpty()) {
                appendLine(line())
                appendLine("PAYMENT BREAKDOWN")
                summary.forEach { s -> appendLine(row("  ${s.method} (${s.txCount}x):", amt(s.amount))) }
            }
            val totalExpenses = expenses.sumOf { it.amount }
            if (totalExpenses > 0) {
                appendLine(line())
                appendLine("EXPENSES")
                expenses.forEach { e -> appendLine(row("  ${e.description.take(18)}:", amt(e.amount))) }
                appendLine(row("Total Expenses:", amt(totalExpenses)))
            }
            if (salariesPaid > 0 || advancesPaid > 0) {
                appendLine(line())
                appendLine("PAYROLL THIS SHIFT")
                if (salariesPaid > 0) appendLine(row("Salaries Paid:", amt(salariesPaid)))
                if (advancesPaid > 0) appendLine(row("Advances Paid:", amt(advancesPaid)))
            }
            val manualIn  = cashTx.filter { it.transactionType == "In"  && it.reason != "Opening Cash" }
            val manualOut = cashTx.filter { it.transactionType == "Out" }
            val totalIn   = manualIn.sumOf  { it.amount }
            val totalOut  = manualOut.sumOf { it.amount }
            val cashSales = summary.firstOrNull { it.method.equals("Cash", ignoreCase = true) }?.amount ?: 0.0
            val expected  = shift.openingCash + cashSales + totalIn - totalOut - totalExpenses - salariesPaid - advancesPaid
            appendLine(line('='))
            appendLine("CASH DRAWER")
            appendLine(row("Opening Cash:", amt(shift.openingCash)))
            appendLine(row("+ Cash Sales:", amt(cashSales)))
            if (totalIn > 0) {
                appendLine(row("+ Cash In:", amt(totalIn)))
                manualIn.forEach { tx -> appendLine("  ${timeFmt.format(tx.createdAt)} ${tx.reason.take(16)}  ${amt(tx.amount)}") }
            }
            if (totalOut > 0) {
                appendLine(row("- Cash Out:", amt(totalOut)))
                manualOut.forEach { tx -> appendLine("  ${timeFmt.format(tx.createdAt)} ${tx.reason.take(16)}  ${amt(tx.amount)}") }
            }
            if (totalExpenses > 0) appendLine(row("- Expenses:", amt(totalExpenses)))
            if (salariesPaid > 0)  appendLine(row("- Salaries:", amt(salariesPaid)))
            if (advancesPaid > 0)  appendLine(row("- Advances:", amt(advancesPaid)))
            appendLine(line())
            appendLine(row("Expected in Drawer:", amt(expected)))
            val closing = if (shift.closingCash > 0) shift.closingCash else expected
            appendLine(row("Actual (Closing):", amt(closing)))
            val diff = closing - expected
            appendLine(row(if (diff >= 0) "Over  (+):" else "Short (-):", amt(kotlin.math.abs(diff))))
            appendLine(line('='))
            appendLine("*** END OF SHIFT REPORT ***")
        }
    }

    fun clearZReportPreview() { _zReportPreviewText.value = null }

    fun clearMessage() { _message.value = null }
}
