@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.delivery

import androidx.compose.foundation.BorderStroke
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
import com.fastpos.android.data.models.CompanyBalance
import com.fastpos.android.data.models.DeliveryCompany
import com.fastpos.android.data.models.DeliverySettlement
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.DeliveryViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun DeliveryScreen(
    onNavigateBack: () -> Unit,
    vm: DeliveryViewModel = hiltViewModel()
) {
    val message  by vm.message.collectAsState()
    val settings by vm.session.settings.collectAsState()
    val snack    = remember { SnackbarHostState() }
    var tab      by remember { mutableStateOf(0) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Delivery Management") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = { vm.loadCompanies(); vm.loadSettlements() }) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Companies") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Settlements") })
            }
            when (tab) {
                0 -> CompaniesTab(vm, settings.currencySymbol)
                1 -> SettlementsTab(vm, settings.currencySymbol)
            }
        }
    }
}

// ── Companies Tab ──────────────────────────────────────────────────────────────

@Composable
private fun CompaniesTab(vm: DeliveryViewModel, sym: String) {
    val companies        by vm.companies.collectAsState()
    val balances         by vm.companyBalances.collectAsState()
    val unassignedOrders by vm.unassignedOrders.collectAsState()
    val loading          by vm.loading.collectAsState()
    var showDialog       by remember { mutableStateOf(false) }
    var deleteTarget     by remember { mutableStateOf<DeliveryCompany?>(null) }
    var assignTarget     by remember { mutableStateOf<com.fastpos.android.data.models.Order?>(null) }
    var assignExpanded   by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                // Total receivables summary banner
                val totalReceivable = balances.sumOf { it.balance.coerceAtLeast(0.0) }
                if (totalReceivable > 0) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, RedError.copy(alpha = 0.4f))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(20.dp), tint = RedError)
                                    Text("Total Receivables", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = RedError)
                                }
                                Text("$sym %.0f".format(totalReceivable), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RedError)
                            }
                        }
                    }
                }

                if (unassignedOrders.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.1f)),
                            border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = AmberWarning)
                                    Text("${unassignedOrders.size} unassigned delivery order(s)", style = MaterialTheme.typography.titleSmall, color = AmberWarning)
                                }
                                unassignedOrders.take(5).forEach { order ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("${order.orderNo} — $sym%.0f".format(order.grandTotal), style = MaterialTheme.typography.bodySmall)
                                            if (!order.deliveryName.isNullOrBlank()) Text(order.deliveryName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        TextButton(onClick = { assignTarget = order }) { Text("Assign", style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (companies.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.DeliveryDining, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("No delivery companies", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    items(companies, key = { it.companyId }) { c ->
                        val bal = balances.find { it.companyId == c.companyId }
                        CompanyCard(
                            company = c,
                            balance = bal,
                            onEdit  = { vm.selectCompanyForEdit(c); showDialog = true },
                            onDelete = if (c.companyId == 1) null else ({ deleteTarget = c })
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { vm.clearCompanyForm(); showDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Add, null, tint = Color.White) }
    }

    if (showDialog) {
        CompanyDialog(vm = vm, onDismiss = { showDialog = false; vm.clearCompanyForm() })
    }

    deleteTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Company") },
            text  = { Text("Delete \"${c.companyName}\"? If it has orders it will be deactivated instead.") },
            confirmButton = { TextButton(onClick = { vm.deleteCompany(c.companyId); deleteTarget = null }) { Text("Delete", color = RedError) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    assignTarget?.let { order ->
        AlertDialog(
            onDismissRequest = { assignTarget = null },
            title = { Text("Assign Delivery Company") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Order: ${order.orderNo}", style = MaterialTheme.typography.bodyMedium)
                    ExposedDropdownMenuBox(expanded = assignExpanded, onExpandedChange = { assignExpanded = it }) {
                        OutlinedTextField(
                            value = "Select company", onValueChange = {}, readOnly = true,
                            label = { Text("Company") }, modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(assignExpanded) }
                        )
                        ExposedDropdownMenu(expanded = assignExpanded, onDismissRequest = { assignExpanded = false }) {
                            companies.filter { it.isActive }.forEach { c ->
                                DropdownMenuItem(text = { Text(c.companyName) }, onClick = {
                                    vm.assignDeliveryCompany(order.orderId, c)
                                    assignExpanded = false; assignTarget = null
                                })
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { assignTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun CompanyCard(
    company: DeliveryCompany,
    balance: CompanyBalance?,
    onEdit:  () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (company.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.DeliveryDining, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(company.companyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (company.companyId == 1) {
                        Surface(shape = MaterialTheme.shapes.small, color = GreenSuccess.copy(alpha = 0.15f)) {
                            Text("Own Rider", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = GreenSuccess)
                        }
                    }
                    if (!company.isActive) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) {
                            Text("Inactive", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (company.commissionPercent > 0) {
                        Text("${company.commissionPercent}% commission", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (balance != null && balance.balance > 0) {
                        Text("Due: %.0f".format(balance.balance), style = MaterialTheme.typography.bodySmall,
                            color = RedError, fontWeight = FontWeight.Medium)
                    } else if (balance != null) {
                        Text("Settled", style = MaterialTheme.typography.bodySmall, color = GreenSuccess)
                    }
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, tint = AmberWarning, modifier = Modifier.size(18.dp))
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun CompanyDialog(vm: DeliveryViewModel, onDismiss: () -> Unit) {
    val formId         by vm.formCompanyId.collectAsState()
    val formName       by vm.formName.collectAsState()
    val formCommission by vm.formCommission.collectAsState()
    val formIsActive   by vm.formIsActive.collectAsState()
    var nameErr        by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (formId > 0) "Edit Company" else "Add Company") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = formName, onValueChange = { vm.setFormName(it); nameErr = false },
                    label = { Text("Company Name *") }, isError = nameErr,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (nameErr) ({ Text("Required") }) else null
                )
                OutlinedTextField(
                    value = formCommission, onValueChange = vm::setFormCommission,
                    label = { Text("Commission %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (formId > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = formIsActive, onCheckedChange = vm::setFormIsActive)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (formName.isBlank()) { nameErr = true; return@Button }
                vm.saveCompany(); onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Settlements Tab ────────────────────────────────────────────────────────────

@Composable
private fun SettlementsTab(vm: DeliveryViewModel, sym: String) {
    val companies    by vm.companies.collectAsState()
    val settlements  by vm.settlements.collectAsState()
    val pendingBal   by vm.pendingBalance.collectAsState()
    val filterFrom   by vm.filterFrom.collectAsState()
    val filterTo     by vm.filterTo.collectAsState()
    val filterCo     by vm.filterCompanyId.collectAsState()
    val settCompId      by vm.settlementCompanyId.collectAsState()
    val settDate        by vm.settlementDate.collectAsState()
    val settAmount      by vm.settlementAmount.collectAsState()
    val settNotes       by vm.settlementNotes.collectAsState()
    val editingSettId   by vm.editingSettlementId.collectAsState()

    var showNewForm      by remember { mutableStateOf(false) }
    var showFromPick     by remember { mutableStateOf(false) }
    var showToPick       by remember { mutableStateOf(false) }
    var showSettDatePick by remember { mutableStateOf(false) }
    var coExpanded       by remember { mutableStateOf(false) }
    var filterCoExpanded by remember { mutableStateOf(false) }
    var deleteTarget     by remember { mutableStateOf<DeliverySettlement?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Filter row
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Filter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateFmt.format(filterFrom), onValueChange = {}, readOnly = true,
                        label = { Text("From") },
                        trailingIcon = { IconButton(onClick = { showFromPick = true }) { Icon(Icons.Default.DateRange, null) } },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dateFmt.format(filterTo), onValueChange = {}, readOnly = true,
                        label = { Text("To") },
                        trailingIcon = { IconButton(onClick = { showToPick = true }) { Icon(Icons.Default.DateRange, null) } },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExposedDropdownMenuBox(expanded = filterCoExpanded, onExpandedChange = { filterCoExpanded = it }, modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = companies.find { it.companyId == filterCo }?.companyName ?: "All companies",
                            onValueChange = {}, readOnly = true, label = { Text("Company") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(filterCoExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = filterCoExpanded, onDismissRequest = { filterCoExpanded = false }) {
                            DropdownMenuItem(text = { Text("All companies") }, onClick = { vm.setFilterCompany(0); filterCoExpanded = false })
                            companies.forEach { c ->
                                DropdownMenuItem(text = { Text(c.companyName) }, onClick = { vm.setFilterCompany(c.companyId); filterCoExpanded = false })
                            }
                        }
                    }
                    Button(onClick = vm::applyFilter) { Text("Apply") }
                }
            }
        }

        // Outstanding balances summary
        val nonZero = vm.companyBalances.collectAsState().value.filter { it.balance > 0 }
        if (nonZero.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, RedError.copy(alpha = 0.3f))) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Outstanding Balances", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = RedError)
                    nonZero.forEach { b ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(b.companyName, style = MaterialTheme.typography.bodySmall)
                            Text("$sym %.0f".format(b.balance), style = MaterialTheme.typography.bodySmall, color = RedError, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (nonZero.size > 1) {
                        HorizontalDivider(color = RedError.copy(alpha = 0.3f))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("$sym %.0f".format(nonZero.sumOf { it.balance }), style = MaterialTheme.typography.bodySmall, color = RedError, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // Settlement list
        if (settlements.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("No settlements in range", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(settlements, key = { it.settlementId }) { s ->
                    SettlementCard(s = s, sym = sym,
                        onEdit   = { vm.loadSettlementForEdit(s); showNewForm = true },
                        onDelete = { deleteTarget = s }
                    )
                }
            }
        }

        // Add settlement button
        Button(
            onClick = { vm.clearSettlementForm(); showNewForm = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
        ) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Record Settlement")
        }
    }

    // New/Edit settlement dialog
    if (showNewForm) {
        AlertDialog(
            onDismissRequest = { showNewForm = false; vm.clearSettlementForm() },
            title = { Text(if (editingSettId > 0) "Edit Settlement" else "Record Settlement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExposedDropdownMenuBox(expanded = coExpanded, onExpandedChange = { coExpanded = it }) {
                        OutlinedTextField(
                            value = companies.find { it.companyId == settCompId }?.companyName ?: "Select company",
                            onValueChange = {}, readOnly = true, label = { Text("Delivery Company *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(coExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = coExpanded, onDismissRequest = { coExpanded = false }) {
                            companies.filter { it.isActive && it.companyId != 1 }.forEach { c ->
                                DropdownMenuItem(text = { Text(c.companyName) }, onClick = {
                                    vm.setSettlementCompany(c.companyId); coExpanded = false
                                })
                            }
                        }
                    }
                    if (settCompId > 0 && pendingBal != 0.0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AccountBalance, null, Modifier.size(16.dp), tint = if (pendingBal > 0) RedError else GreenSuccess)
                            Text(
                                if (pendingBal > 0) "Pending: Rs. %.0f".format(pendingBal)
                                else "Account settled",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pendingBal > 0) RedError else GreenSuccess
                            )
                        }
                    }
                    OutlinedTextField(
                        value = dateFmt.format(settDate), onValueChange = {}, readOnly = true,
                        label = { Text("Date") },
                        trailingIcon = { IconButton(onClick = { showSettDatePick = true }) { Icon(Icons.Default.DateRange, null) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settAmount, onValueChange = vm::setSettlementAmount,
                        label = { Text("Amount Received *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = settNotes, onValueChange = vm::setSettlementNotes,
                        label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.saveSettlement(); showNewForm = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showNewForm = false; vm.clearSettlementForm() }) { Text("Cancel") } }
        )
    }

    if (showSettDatePick) {
        DeliveryDatePicker(initialMillis = settDate.time, onDismiss = { showSettDatePick = false }) {
            vm.setSettlementDate(java.util.Date(it)); showSettDatePick = false
        }
    }
    if (showFromPick) {
        DeliveryDatePicker(initialMillis = filterFrom.time, onDismiss = { showFromPick = false }) {
            vm.setFilterFrom(java.util.Date(it)); showFromPick = false
        }
    }
    if (showToPick) {
        DeliveryDatePicker(initialMillis = filterTo.time, onDismiss = { showToPick = false }) {
            vm.setFilterTo(java.util.Date(it)); showToPick = false
        }
    }

    deleteTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Settlement") },
            text  = { Text("Delete settlement of Rs. %.0f from ${s.companyName}?".format(s.amountReceived)) },
            confirmButton = { TextButton(onClick = { vm.deleteSettlement(s.settlementId); deleteTarget = null }) { Text("Delete", color = RedError) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettlementCard(s: DeliverySettlement, sym: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.3f))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(s.companyName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(dateFmt.format(s.settlementDate), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.AttachMoney, null, Modifier.size(12.dp), tint = GreenSuccess)
                        Text("$sym %.0f".format(s.amountReceived), style = MaterialTheme.typography.bodySmall, color = GreenSuccess, fontWeight = FontWeight.Bold)
                    }
                }
                if (s.notes.isNotBlank()) Text(s.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, tint = AmberWarning, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun DeliveryDatePicker(initialMillis: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}
