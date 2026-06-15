@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.employees

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.Employee
import com.fastpos.android.data.models.EmployeeAdvance
import com.fastpos.android.data.models.SalaryPayment
import com.fastpos.android.ui.theme.*
import com.fastpos.android.data.models.AttendanceRecord
import com.fastpos.android.viewmodels.AttendanceViewModel
import com.fastpos.android.viewmodels.EmployeeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ATTEND_DATE_FMT = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

@Composable
fun EmployeeScreen(
    onNavigateBack: () -> Unit,
    vm:   EmployeeViewModel   = hiltViewModel(),
    attVm: AttendanceViewModel = hiltViewModel()
) {
    val snack   = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    val attMsg  by attVm.message.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Employees", "Advances", "Salary", "Attendance")

    LaunchedEffect(message)  { message?.let { snack.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(attMsg)   { attMsg?.let  { snack.showSnackbar(it); attVm.clearMessage() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Employee Management") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    if (selectedTab == 3) {
                        IconButton(onClick = attVm::loadAttendance) { Icon(Icons.Default.Refresh, null) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                tabs.forEachIndexed { idx, title ->
                    Tab(selected = selectedTab == idx, onClick = { selectedTab = idx },
                        text = { Text(title) })
                }
            }
            when (selectedTab) {
                0 -> EmployeesTab(vm)
                1 -> AdvancesTab(vm)
                2 -> SalaryPaymentsTab(vm)
                3 -> AttendanceTab(attVm)
            }
        }
    }
}

// ── Tab 1: Employees ─────────────────────────────────────────────────────────

@Composable
private fun EmployeesTab(vm: EmployeeViewModel) {
    val context    = LocalContext.current
    val employees  by vm.employees.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val search     by vm.search.collectAsState()

    var showAdd      by remember { mutableStateOf(false) }
    var editTarget   by remember { mutableStateOf<Employee?>(null) }
    var toggleTarget by remember { mutableStateOf<Employee?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = search, onValueChange = { vm.setSearch(it); vm.load() },
                label = { Text("Search employees…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (search.isNotEmpty()) IconButton(onClick = { vm.setSearch(""); vm.load() }) {
                        Icon(Icons.Default.Clear, null)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }

            if (employees.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PeopleOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No employees found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                return@Column
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${employees.size} employees",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { vm.exportEmployees(context) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Download, "Export CSV",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(employees, key = { it.employeeId }) { emp ->
                    EmployeeCard(
                        employee = emp,
                        onEdit   = { editTarget = emp },
                        onToggle = { toggleTarget = emp }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.PersonAdd, "Add Employee")
        }
    }

    if (showAdd) {
        EmployeeDialog(
            title     = "New Employee",
            onConfirm = { name, phone, des, doj, salary, _ ->
                vm.addEmployee(name, phone, des, doj, salary)
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    editTarget?.let { emp ->
        EmployeeDialog(
            title           = "Edit Employee",
            initialEmployee = emp,
            onConfirm = { name, phone, des, doj, salary, active ->
                vm.updateEmployee(emp.employeeId, name, phone, des, doj, salary, active)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    toggleTarget?.let { emp ->
        AlertDialog(
            onDismissRequest = { toggleTarget = null },
            icon  = { Icon(if (emp.isActive) Icons.Default.PersonOff else Icons.Default.PersonAdd, null,
                tint = if (emp.isActive) RedError else GreenSuccess) },
            title = { Text(if (emp.isActive) "Deactivate Employee?" else "Activate Employee?") },
            text  = { Text("${if (emp.isActive) "Deactivate" else "Activate"} ${emp.employeeName}?") },
            confirmButton = {
                Button(
                    onClick = { vm.toggleActive(emp); toggleTarget = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = if (emp.isActive) RedError else GreenSuccess)
                ) { Text(if (emp.isActive) "Deactivate" else "Activate") }
            },
            dismissButton = { TextButton(onClick = { toggleTarget = null }) { Text("Cancel") } }
        )
    }
}

// ── Tab 2: Advances ──────────────────────────────────────────────────────────

@Composable
private fun AdvancesTab(vm: EmployeeViewModel) {
    val context          = LocalContext.current
    val advances         by vm.advances.collectAsState()
    val employees        by vm.employees.collectAsState()
    val filterEmployeeId by vm.avFilterEmployeeId.collectAsState()
    val filterFrom       by vm.avFilterFrom.collectAsState()
    val filterTo         by vm.avFilterTo.collectAsState()

    var showDialog      by remember { mutableStateOf(false) }
    var deleteTarget    by remember { mutableStateOf<EmployeeAdvance?>(null) }
    var filterExpanded  by remember { mutableStateOf(false) }
    var showFromPicker  by remember { mutableStateOf(false) }
    var showToPicker    by remember { mutableStateOf(false) }

    val dFmt = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }

    if (showFromPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = filterFrom.time)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setAvFilterDates(Date(it), filterTo) }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    if (showToPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = filterTo.time)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setAvFilterDates(filterFrom, Date(it)) }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    Column(Modifier.fillMaxSize()) {
        // Date range filter row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = dFmt.format(filterFrom),
                onValueChange = {},
                readOnly = true,
                label = { Text("From") },
                trailingIcon = {
                    IconButton(onClick = { showFromPicker = true }, Modifier.size(24.dp)) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = dFmt.format(filterTo),
                onValueChange = {},
                readOnly = true,
                label = { Text("To") },
                trailingIcon = {
                    IconButton(onClick = { showToPicker = true }, Modifier.size(24.dp)) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        // Employee filter + actions row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = employees.find { it.employeeId == filterEmployeeId }?.employeeName ?: "All Employees",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Employee") },
                    trailingIcon = {
                        IconButton(onClick = { filterExpanded = !filterExpanded }) {
                            Icon(if (filterExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    DropdownMenuItem(text = { Text("All Employees") },
                        onClick = { vm.setAvFilterEmployeeId(null); filterExpanded = false })
                    employees.forEach { emp ->
                        DropdownMenuItem(text = { Text(emp.employeeName) },
                            onClick = { vm.setAvFilterEmployeeId(emp.employeeId); filterExpanded = false })
                    }
                }
            }
            IconButton(onClick = { vm.exportAdvances(context) }) {
                Icon(Icons.Default.Download, "Export CSV", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Record")
            }
        }

        if (advances.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.MoneyOff, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No advances recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(advances, key = { it.advanceId }) { adv ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(adv.employeeName, fontWeight = FontWeight.Medium)
                                Text(dFmt.format(adv.advanceDate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(adv.paymentMethod,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (adv.notes.isNotBlank()) {
                                    Text(adv.notes, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                "Rs. ${"%.0f".format(adv.amount)}",
                                fontWeight = FontWeight.Bold,
                                color = RedError,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { deleteTarget = adv }) {
                                Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        RecordAdvanceDialog(
            employees = employees,
            onConfirm = { employeeId, amount, notes, date, paymentMethod ->
                vm.saveAdvance(employeeId, amount, notes, date, paymentMethod)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }

    deleteTarget?.let { adv ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Delete, null, tint = RedError) },
            title = { Text("Delete Advance?") },
            text  = { Text("Delete advance of Rs. ${"%.0f".format(adv.amount)} for ${adv.employeeName}?") },
            confirmButton = {
                Button(onClick = { vm.deleteAdvance(adv.advanceId); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

// ── Tab 3: Salary Payments ───────────────────────────────────────────────────

@Composable
private fun SalaryPaymentsTab(vm: EmployeeViewModel) {
    val context          = LocalContext.current
    val payments         by vm.salaryPayments.collectAsState()
    val employees        by vm.employees.collectAsState()
    val filterEmployeeId by vm.spFilterEmployeeId.collectAsState()
    val filterFrom       by vm.spFilterFrom.collectAsState()
    val filterTo         by vm.spFilterTo.collectAsState()

    var showDialog      by remember { mutableStateOf(false) }
    var deleteTarget    by remember { mutableStateOf<SalaryPayment?>(null) }
    var filterExpanded  by remember { mutableStateOf(false) }
    var showFromPicker  by remember { mutableStateOf(false) }
    var showToPicker    by remember { mutableStateOf(false) }

    val dFmt = remember { SimpleDateFormat("dd MMM yy", Locale.getDefault()) }

    if (showFromPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = filterFrom.time)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setSpFilterDates(Date(it), filterTo) }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    if (showToPicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = filterTo.time)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setSpFilterDates(filterFrom, Date(it)) }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    Column(Modifier.fillMaxSize()) {
        // Date range filter row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = dFmt.format(filterFrom),
                onValueChange = {},
                readOnly = true,
                label = { Text("From") },
                trailingIcon = {
                    IconButton(onClick = { showFromPicker = true }, Modifier.size(24.dp)) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = dFmt.format(filterTo),
                onValueChange = {},
                readOnly = true,
                label = { Text("To") },
                trailingIcon = {
                    IconButton(onClick = { showToPicker = true }, Modifier.size(24.dp)) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        // Employee filter + actions row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = employees.find { it.employeeId == filterEmployeeId }?.employeeName ?: "All Employees",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Employee") },
                    trailingIcon = {
                        IconButton(onClick = { filterExpanded = !filterExpanded }) {
                            Icon(if (filterExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                    DropdownMenuItem(text = { Text("All Employees") },
                        onClick = { vm.setSpFilterEmployeeId(null); filterExpanded = false })
                    employees.forEach { emp ->
                        DropdownMenuItem(text = { Text(emp.employeeName) },
                            onClick = { vm.setSpFilterEmployeeId(emp.employeeId); filterExpanded = false })
                    }
                }
            }
            IconButton(onClick = { vm.exportSalaryPayments(context) }) {
                Icon(Icons.Default.Download, "Export CSV", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Record")
            }
        }

        if (payments.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Payments, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No salary payments recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(payments, key = { it.paymentId }) { pmt ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pmt.employeeName, fontWeight = FontWeight.Medium)
                                Text(
                                    "${pmt.periodLabel}  •  ${pmt.paymentMethod}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(dFmt.format(pmt.paymentDate),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (pmt.notes.isNotBlank()) {
                                    Text(pmt.notes, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                "Rs. ${"%.0f".format(pmt.amount)}",
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = { deleteTarget = pmt }) {
                                Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        RecordSalaryPaymentDialog(
            employees = employees,
            vm        = vm,
            onConfirm = { employeeId, amount, month, year, method, notes ->
                vm.saveSalaryPayment(employeeId, amount, month, year, method, notes)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }

    deleteTarget?.let { pmt ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Delete, null, tint = RedError) },
            title = { Text("Delete Salary Payment?") },
            text  = { Text("Delete payment of Rs. ${"%.0f".format(pmt.amount)} for ${pmt.employeeName}?") },
            confirmButton = {
                Button(onClick = { vm.deleteSalaryPayment(pmt.paymentId); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

// ── Reusable Cards & Dialogs ─────────────────────────────────────────────────

@Composable
private fun EmployeeCard(
    employee: Employee,
    onEdit:   () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (employee.isActive) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.AccountCircle, null, Modifier.size(40.dp),
                    tint = if (employee.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            employee.employeeName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (employee.isActive) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                        if (!employee.isActive) {
                            Badge(containerColor = RedError.copy(0.15f)) {
                                Text("Inactive", style = MaterialTheme.typography.labelSmall, color = RedError)
                            }
                        }
                    }
                    if (employee.phone.isNotBlank()) {
                        Text(employee.phone, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (employee.employeeRole.isNotBlank()) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) {
                            Text(employee.employeeRole, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (employee.monthlySalary > 0) {
                        Text("Rs. ${employee.monthlySalary.toLong()}/mo",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenSuccess)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = AmberWarning)
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (employee.isActive) Icons.Default.PersonOff else Icons.Default.PersonAdd,
                        null, modifier = Modifier.size(18.dp),
                        tint = if (employee.isActive) RedError else GreenSuccess
                    )
                }
            }
        }
    }
}

@Composable
private fun EmployeeDialog(
    title: String,
    initialEmployee: Employee? = null,
    onConfirm: (name: String, phone: String, designation: String,
                joiningDate: Date?, monthlySalary: Double, isActive: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val isEdit = initialEmployee != null
    val dFmt   = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val roleOptions = listOf("Waiter", "Chef", "Worker", "Guard")

    var name           by remember { mutableStateOf(initialEmployee?.employeeName ?: "") }
    var phone          by remember { mutableStateOf(initialEmployee?.phone ?: "") }
    var designation    by remember { mutableStateOf(initialEmployee?.employeeRole ?: "") }
    var joiningDateMs  by remember { mutableLongStateOf(initialEmployee?.joiningDate?.time ?: System.currentTimeMillis()) }
    var hasJoiningDate by remember { mutableStateOf(initialEmployee?.joiningDate != null) }
    var salaryText     by remember { mutableStateOf(if ((initialEmployee?.monthlySalary ?: 0.0) > 0) initialEmployee!!.monthlySalary.toLong().toString() else "") }
    var isActive       by remember { mutableStateOf(initialEmployee?.isActive ?: true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var roleExpanded   by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = joiningDateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { joiningDateMs = it; hasJoiningDate = true }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = designation.ifBlank { "" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role / Designation") },
                        trailingIcon = {
                            IconButton(onClick = { roleExpanded = !roleExpanded }) {
                                Icon(if (roleExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                        roleOptions.forEach { role ->
                            DropdownMenuItem(
                                text    = { Text(role) },
                                onClick = { designation = role; roleExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = if (hasJoiningDate) dFmt.format(Date(joiningDateMs)) else "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Joining Date") },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (hasJoiningDate) {
                                IconButton(onClick = { hasJoiningDate = false }) {
                                    Icon(Icons.Default.Clear, null, Modifier.size(18.dp))
                                }
                            }
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = salaryText, onValueChange = { salaryText = it },
                    label = { Text("Monthly Salary") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (isEdit) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    val doj    = if (hasJoiningDate) Date(joiningDateMs) else null
                    val salary = salaryText.toDoubleOrNull() ?: 0.0
                    onConfirm(name, phone, designation, doj, salary, isActive)
                }
            }) { Text("Save Employee") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RecordAdvanceDialog(
    employees: List<Employee>,
    onConfirm: (Int, Double, String, Date, String) -> Unit,
    onDismiss: () -> Unit
) {
    val dFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    var selectedEmployeeId by remember { mutableIntStateOf(0) }
    var amount             by remember { mutableStateOf("") }
    var notes              by remember { mutableStateOf("") }
    var expanded           by remember { mutableStateOf(false) }
    var methExpanded       by remember { mutableStateOf(false) }
    var method             by remember { mutableStateOf("Cash") }
    var advanceDateMs      by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker     by remember { mutableStateOf(false) }
    val methods      = listOf("Cash", "Bank Transfer", "Cheque")
    val selectedName = employees.find { it.employeeId == selectedEmployeeId }?.employeeName ?: "Select Employee *"

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = advanceDateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { advanceDateMs = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Advance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedTextField(
                        value = selectedName, onValueChange = {}, readOnly = true,
                        label = { Text("Employee") },
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        employees.filter { it.isActive }.forEach { emp ->
                            DropdownMenuItem(
                                text    = { Text(emp.employeeName) },
                                onClick = { selectedEmployeeId = emp.employeeId; expanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Amount *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = dFmt.format(Date(advanceDateMs)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    OutlinedTextField(
                        value = method, onValueChange = {}, readOnly = true,
                        label = { Text("Payment Method") },
                        trailingIcon = {
                            IconButton(onClick = { methExpanded = !methExpanded }) {
                                Icon(if (methExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = methExpanded, onDismissRequest = { methExpanded = false }) {
                        methods.forEach { m ->
                            DropdownMenuItem(
                                text    = { Text(m) },
                                onClick = { method = m; methExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (selectedEmployeeId != 0 && amt > 0) onConfirm(selectedEmployeeId, amt, notes, Date(advanceDateMs), method)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RecordSalaryPaymentDialog(
    employees: List<Employee>,
    vm: EmployeeViewModel,
    onConfirm: (Int, Double, Int, Int, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val now = remember { Calendar.getInstance() }
    var selectedEmployeeId by remember { mutableIntStateOf(0) }
    var amount             by remember { mutableStateOf("") }
    var periodMonth        by remember { mutableIntStateOf(now.get(Calendar.MONTH) + 1) }
    var periodYear         by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var method             by remember { mutableStateOf("Cash") }
    var notes              by remember { mutableStateOf("") }
    var empExpanded        by remember { mutableStateOf(false) }
    var methExpanded       by remember { mutableStateOf(false) }

    val spEmployeeSalary  by vm.spEmployeeSalary.collectAsState()
    val spTotalAdvances   by vm.spTotalAdvances.collectAsState()
    val spAlreadyPaid     by vm.spAlreadyPaidThisPeriod.collectAsState()
    val netPayable   = (spEmployeeSalary - spTotalAdvances).coerceAtLeast(0.0)
    val remaining    = (netPayable - spAlreadyPaid).coerceAtLeast(0.0)
    val periodFullyPaid = spAlreadyPaid > 0 && remaining <= 0.01

    // Notify VM when period changes so it reloads alreadyPaid
    LaunchedEffect(selectedEmployeeId, periodMonth, periodYear) {
        if (selectedEmployeeId != 0) vm.setSpPeriod(periodMonth, periodYear)
    }

    // Auto-fill amount with remaining payable when employee/period/advances load
    LaunchedEffect(spEmployeeSalary, spTotalAdvances, spAlreadyPaid) {
        if (selectedEmployeeId != 0) {
            amount = if (remaining > 0) remaining.toLong().toString()
                     else if (netPayable > 0) netPayable.toLong().toString()
                     else ""
        }
    }

    val selectedName = employees.find { it.employeeId == selectedEmployeeId }?.employeeName ?: "Select Employee *"
    val monthNames   = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    val methods      = listOf("Cash", "Bank Transfer", "Cheque")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Salary Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedTextField(
                        value = selectedName, onValueChange = {}, readOnly = true,
                        label = { Text("Employee") },
                        trailingIcon = {
                            IconButton(onClick = { empExpanded = !empExpanded }) {
                                Icon(if (empExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = empExpanded, onDismissRequest = { empExpanded = false }) {
                        employees.filter { it.isActive }.forEach { emp ->
                            DropdownMenuItem(
                                text    = { Text(emp.employeeName) },
                                onClick = {
                                    selectedEmployeeId = emp.employeeId
                                    vm.setSpFormEmployee(emp.employeeId)
                                    amount = ""
                                    empExpanded = false
                                }
                            )
                        }
                    }
                }

                if (selectedEmployeeId != 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (periodFullyPaid) GreenSuccess.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Monthly Salary",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Rs. ${"%.0f".format(spEmployeeSalary)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (spTotalAdvances > 0) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Advances",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Rs. ${"%.0f".format(spTotalAdvances)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold, color = RedError)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Net Payable",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold)
                                Text("Rs. ${"%.0f".format(netPayable)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold, color = GreenSuccess)
                            }
                            if (spAlreadyPaid > 0 || periodFullyPaid) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Paid This Period",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Rs. ${"%.0f".format(spAlreadyPaid)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold, color = AmberWarning)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Remaining",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold)
                                    if (periodFullyPaid) {
                                        Surface(shape = MaterialTheme.shapes.small, color = GreenSuccess.copy(alpha = 0.15f)) {
                                            Text("FULLY PAID",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold, color = GreenSuccess,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    } else {
                                        Text("Rs. ${"%.0f".format(remaining)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold, color = GreenSuccess)
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Amount *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var monthExpanded by remember { mutableStateOf(false) }
                    Box(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = monthNames.getOrElse(periodMonth - 1) { "" },
                            onValueChange = {}, readOnly = true,
                            label = { Text("Month") },
                            trailingIcon = {
                                IconButton(onClick = { monthExpanded = !monthExpanded }) {
                                    Icon(if (monthExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                            monthNames.forEachIndexed { idx, m ->
                                DropdownMenuItem(text = { Text(m) },
                                    onClick = { periodMonth = idx + 1; monthExpanded = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = periodYear.toString(),
                        onValueChange = { periodYear = it.toIntOrNull() ?: periodYear },
                        label = { Text("Year") },
                        modifier = Modifier.weight(0.7f), singleLine = true
                    )
                }

                Box {
                    OutlinedTextField(
                        value = method, onValueChange = {}, readOnly = true,
                        label = { Text("Method") },
                        trailingIcon = {
                            IconButton(onClick = { methExpanded = !methExpanded }) {
                                Icon(if (methExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = methExpanded, onDismissRequest = { methExpanded = false }) {
                        methods.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = { method = m; methExpanded = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(), singleLine = false, maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull() ?: 0.0
                if (selectedEmployeeId != 0 && amt > 0) onConfirm(selectedEmployeeId, amt, periodMonth, periodYear, method, notes)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Tab 4: Attendance ─────────────────────────────────────────────────────────

@Composable
private fun AttendanceTab(vm: AttendanceViewModel) {
    val attendance   by vm.attendance.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    val checkedIn = attendance.count { it.checkInTime != null && it.checkOutTime == null }
    val completed = attendance.count { it.checkInTime != null && it.checkOutTime != null }
    val absent    = attendance.count { it.checkInTime == null }

    Column(Modifier.fillMaxSize()) {
        // Date navigation bar
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply { time = selectedDate; add(Calendar.DAY_OF_MONTH, -1) }
                    vm.setDate(cal.time)
                }) { Icon(Icons.Default.ChevronLeft, null) }
                TextButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(ATTEND_DATE_FMT.format(selectedDate), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = {
                    val cal = Calendar.getInstance().apply { time = selectedDate; add(Calendar.DAY_OF_MONTH, 1) }
                    vm.setDate(cal.time)
                }) { Icon(Icons.Default.ChevronRight, null) }
            }
        }
        // Summary chips
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AttendChip("In $checkedIn",   AmberWarning)
            AttendChip("Done $completed", GreenSuccess)
            AttendChip("Absent $absent",  RedError)
        }
        Spacer(Modifier.height(4.dp))
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (attendance.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active employees found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(attendance, key = { it.employeeId }) { record ->
                    AttendanceEmployeeCard(
                        record     = record,
                        onCheckIn  = { vm.checkIn(record.employeeId) },
                        onCheckOut = { vm.checkOut(record.attendanceId) },
                        onClear    = { vm.removeAttendance(record.employeeId) }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { vm.setDate(Date(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = dpState) }
    }
}

@Composable
private fun AttendChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Surface(color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small) {
        Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun AttendanceEmployeeCard(
    record:     AttendanceRecord,
    onCheckIn:  () -> Unit,
    onCheckOut: () -> Unit,
    onClear:    () -> Unit
) {
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val statusColor = when {
        record.checkInTime != null && record.checkOutTime != null -> GreenSuccess
        record.checkInTime != null                               -> AmberWarning
        else                                                     -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when {
        record.checkInTime != null && record.checkOutTime != null -> "Completed"
        record.checkInTime != null                               -> "In Progress"
        else                                                     -> "Absent"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small,
                        modifier = Modifier.size(38.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(record.fullName.firstOrNull()?.toString()?.uppercase(Locale.getDefault()) ?: "?",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = statusColor)
                        }
                    }
                    Column {
                        Text(record.fullName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (record.roleName.isNotBlank())
                            Text(record.roleName, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(color = statusColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
                    Text(statusLabel, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = statusColor)
                }
            }
            if (record.checkInTime != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("In",  style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(timeFmt.format(record.checkInTime), style = MaterialTheme.typography.bodySmall, color = GreenSuccess)
                    }
                    if (record.checkOutTime != null) {
                        Column {
                            Text("Out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(timeFmt.format(record.checkOutTime), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        record.durationLabel.takeIf { it.isNotBlank() }?.let {
                            Column {
                                Text("Duration", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.checkInTime == null)
                    Button(onClick = onCheckIn, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)) {
                        Icon(Icons.Default.Login, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Check In")
                    }
                if (record.checkInTime != null && record.checkOutTime == null)
                    Button(onClick = onCheckOut, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Icon(Icons.Default.Logout, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Check Out")
                    }
                if (record.checkInTime != null)
                    OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Clear, null, Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Clear")
                    }
            }
        }
    }
}
