@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.payroll

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.EmployeeAdvance
import com.fastpos.android.data.models.EmployeeSalaryInfo
import com.fastpos.android.data.models.PayrollRow
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.PayrollViewModel

@Composable
fun PayrollScreen(
    onNavigateBack: () -> Unit,
    vm: PayrollViewModel = hiltViewModel()
) {
    val payrollRows    by vm.payrollRows.collectAsState()
    val salaryList     by vm.salaryList.collectAsState()
    val advanceHistory by vm.advanceHistory.collectAsState()
    val isLoading      by vm.isLoading.collectAsState()
    val message        by vm.message.collectAsState()
    val totalSalary    by vm.totalSalary.collectAsState()
    val totalAdvances  by vm.totalAdvances.collectAsState()
    val totalNetPay    by vm.totalNetPay.collectAsState()
    val settings       by vm.session.settings.collectAsState()
    val snack          = remember { SnackbarHostState() }
    var tabIndex       by remember { mutableIntStateOf(0) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payroll") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Month / Year navigation
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = vm::prevMonth) { Icon(Icons.Default.ChevronLeft, "Previous month") }
                Text(
                    vm.filterMonthLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = vm::nextMonth) { Icon(Icons.Default.ChevronRight, "Next month") }
            }

            // Summary cards
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard("Total Salary",  totalSalary,   settings.currencySymbol, GreenSuccess,                           Modifier.weight(1f))
                SummaryCard("Advances",      totalAdvances, settings.currencySymbol, MaterialTheme.colorScheme.error,        Modifier.weight(1f))
                SummaryCard("Net Pay",       totalNetPay,   settings.currencySymbol, MaterialTheme.colorScheme.primary,                          Modifier.weight(1f))
            }

            Spacer(Modifier.height(4.dp))

            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 },
                    text = { Text("Payroll") },  icon = { Icon(Icons.Default.Payments,     null, Modifier.size(18.dp)) })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 },
                    text = { Text("Advances") }, icon = { Icon(Icons.Default.MoneyOff,     null, Modifier.size(18.dp)) })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 },
                    text = { Text("Salaries") }, icon = { Icon(Icons.Default.AttachMoney,  null, Modifier.size(18.dp)) })
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            when (tabIndex) {
                0 -> PayrollTab(
                    rows           = payrollRows,
                    currencySymbol = settings.currencySymbol,
                    onPay          = vm::paySalary,
                    onPayAll       = vm::payAll,
                    onPrint        = { row -> vm.printPayslip(row, settings.companyName, settings.currencySymbol) }
                )
                1 -> AdvancesTab(
                    employees      = salaryList,
                    history        = advanceHistory,
                    currencySymbol = settings.currencySymbol,
                    onAdd          = { empId, amount, notes, method -> vm.addAdvance(empId, amount, notes, method) },
                    onDelete       = vm::deleteAdvance
                )
                2 -> SalariesTab(
                    salaryList     = salaryList,
                    currencySymbol = settings.currencySymbol,
                    onSave         = { userId, salary -> vm.updateSalary(userId, salary) }
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: Double, sym: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            Modifier.padding(10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value.formatCurrency(sym), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PayrollTab(
    rows:           List<PayrollRow>,
    currencySymbol: String,
    onPay:          (PayrollRow) -> Unit,
    onPayAll:       () -> Unit,
    onPrint:        (PayrollRow) -> Unit
) {
    val unpaid = rows.filter { !it.isPaid && it.remainingPay > 0 }
    var showPayAll by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (unpaid.isNotEmpty()) {
            item {
                Button(
                    onClick  = { showPayAll = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                ) {
                    Icon(Icons.Default.Payments, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pay All (${unpaid.size} pending)")
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                Box(
                    Modifier.fillParentMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No employees found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(rows, key = { it.employeeId }) { row ->
                PayrollRowCard(row, currencySymbol, onPay, onPrint)
            }
        }
    }

    if (showPayAll) {
        AlertDialog(
            onDismissRequest = { showPayAll = false },
            icon    = { Icon(Icons.Default.Payments, null, tint = GreenSuccess) },
            title   = { Text("Pay All Pending") },
            text    = { Text("Record salary payment for ${unpaid.size} pending employee(s)?") },
            confirmButton = {
                Button(
                    onClick = { onPayAll(); showPayAll = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                ) { Text("Confirm") }
            },
            dismissButton = { TextButton(onClick = { showPayAll = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PayrollRowCard(
    row:            PayrollRow,
    currencySymbol: String,
    onPay:          (PayrollRow) -> Unit,
    onPrint:        (PayrollRow) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (row.isPaid) GreenSuccess.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (row.isPaid) androidx.compose.foundation.BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.3f)) else null
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.employeeName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (row.employeeRole.isNotBlank())
                        Text(row.employeeRole, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = (if (row.isPaid) GreenSuccess else AmberWarning).copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        if (row.isPaid) "Paid" else "Pending",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (row.isPaid) GreenSuccess else AmberWarning
                    )
                }
                IconButton(onClick = { onPrint(row) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SalaryLine("Salary",   row.monthlySalary,     currencySymbol, MaterialTheme.colorScheme.onSurface)
                    if (row.advancesThisMonth > 0)
                        SalaryLine("Advances", row.advancesThisMonth, currencySymbol, MaterialTheme.colorScheme.error)
                    if (row.alreadyPaid > 0) {
                        SalaryLine("Net Pay",   row.netPay,       currencySymbol, MaterialTheme.colorScheme.primary)
                        SalaryLine("Paid",      row.alreadyPaid,  currencySymbol, GreenSuccess)
                        SalaryLine("Remaining", row.remainingPay, currencySymbol, if (row.isPaid) GreenSuccess else AmberWarning)
                    } else {
                        SalaryLine("Net Pay", row.netPay, currencySymbol, if (row.isPaid) GreenSuccess else MaterialTheme.colorScheme.primary)
                    }
                }
                if (!row.isPaid && row.remainingPay > 0) {
                    Button(
                        onClick = { onPay(row) },
                        colors  = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pay", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SalaryLine(label: String, amount: Double, sym: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount.formatCurrency(sym), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun AdvancesTab(
    employees:      List<EmployeeSalaryInfo>,
    history:        List<EmployeeAdvance>,
    currencySymbol: String,
    onAdd:          (Int, Double, String, String) -> Unit,
    onDelete:       (Int) -> Unit
) {
    val payMethods  = listOf("Cash", "Bank Transfer", "Cheque")
    var selectedEmp by remember { mutableStateOf<EmployeeSalaryInfo?>(null) }
    var amountText  by remember { mutableStateOf("") }
    var notesText   by remember { mutableStateOf("") }
    var showMenu    by remember { mutableStateOf(false) }
    var method      by remember { mutableStateOf("Cash") }
    var methodExp   by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Record Advance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Box {
                        OutlinedTextField(
                            value = selectedEmp?.fullName ?: "",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Employee") },
                            trailingIcon = { IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.ExpandMore, null) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            employees.forEach { emp ->
                                DropdownMenuItem(text = { Text(emp.fullName) }, onClick = { selectedEmp = emp; showMenu = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount ($currencySymbol)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Notes (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ExposedDropdownMenuBox(
                        expanded = methodExp,
                        onExpandedChange = { methodExp = it }
                    ) {
                        OutlinedTextField(
                            value = method,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Payment Method") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExp) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = methodExp, onDismissRequest = { methodExp = false }) {
                            payMethods.forEach { m ->
                                DropdownMenuItem(text = { Text(m) }, onClick = { method = m; methodExp = false })
                            }
                        }
                    }
                    Button(
                        onClick = {
                            val emp = selectedEmp ?: return@Button
                            val amt = amountText.toDoubleOrNull() ?: return@Button
                            onAdd(emp.userId, amt, notesText, method)
                            amountText = ""; notesText = ""; method = "Cash"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled  = selectedEmp != null && amountText.toDoubleOrNull() != null
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Record Advance")
                    }
                }
            }
        }

        if (history.isEmpty()) {
            item {
                Text(
                    "No advance records.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )
            }
        } else {
            item {
                Text("History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            items(history, key = { it.advanceId }) { adv ->
                AdvanceHistoryCard(adv, currencySymbol, onDelete)
            }
        }
    }
}

@Composable
private fun AdvanceHistoryCard(
    adv:            EmployeeAdvance,
    currencySymbol: String,
    onDelete:       (Int) -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    val dtFmt = remember { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(adv.employeeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(dtFmt.format(adv.advanceDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (adv.notes.isNotBlank())
                    Text(adv.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(adv.paymentMethod, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(adv.amount.formatCurrency(currencySymbol), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            IconButton(onClick = { showConfirm = true }) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon    = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Delete Advance") },
            text    = { Text("Remove this advance record for ${adv.employeeName}?") },
            confirmButton = {
                Button(
                    onClick = { onDelete(adv.advanceId); showConfirm = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SalariesTab(
    salaryList:     List<EmployeeSalaryInfo>,
    currencySymbol: String,
    onSave:         (Int, Double) -> Unit
) {
    var editTarget by remember { mutableStateOf<EmployeeSalaryInfo?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(salaryList, key = { it.userId }) { emp ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(emp.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(emp.roleName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        emp.basicSalary.formatCurrency(currencySymbol),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = GreenSuccess
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { editTarget = emp }) { Icon(Icons.Default.Edit, null, tint = AmberWarning) }
                }
            }
        }
    }

    editTarget?.let { emp ->
        SalaryEditDialog(
            employee       = emp,
            currencySymbol = currencySymbol,
            onDismiss      = { editTarget = null },
            onSave         = { newSalary -> onSave(emp.userId, newSalary); editTarget = null }
        )
    }
}

@Composable
private fun SalaryEditDialog(
    employee:       EmployeeSalaryInfo,
    currencySymbol: String,
    onDismiss:      () -> Unit,
    onSave:         (Double) -> Unit
) {
    var salaryText by remember { mutableStateOf(employee.basicSalary.toLong().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.AttachMoney, null, tint = GreenSuccess) },
        title = { Text("Edit Salary — ${employee.fullName}") },
        text  = {
            OutlinedTextField(
                value           = salaryText,
                onValueChange   = { salaryText = it },
                label           = { Text("Basic Salary ($currencySymbol)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { salaryText.toDoubleOrNull()?.let { onSave(it) } },
                colors  = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
