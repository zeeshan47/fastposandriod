@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.reservations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import com.fastpos.android.data.models.Reservation
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.ReservationsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun ReservationScreen(
    onNavigateBack: () -> Unit,
    vm: ReservationsViewModel = hiltViewModel()
) {
    val reservations by vm.reservations.collectAsState()
    val tables       by vm.tables.collectAsState()
    val filterMode   by vm.filterMode.collectAsState()
    val filterDate   by vm.filterDate.collectAsState()
    val loading      by vm.loading.collectAsState()
    val message      by vm.message.collectAsState()
    val formId       by vm.formId.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showForm          by remember { mutableStateOf(false) }
    var deleteTarget      by remember { mutableStateOf<Reservation?>(null) }
    var showFilterDatePick by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reservations") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.clearForm(); showForm = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Filter chips
            ScrollableTabRow(selectedTabIndex = vm.filterModes.indexOf(filterMode)) {
                vm.filterModes.forEachIndexed { i, mode ->
                    Tab(selected = filterMode == mode, onClick = { vm.setFilterMode(mode) }, text = { Text(mode) })
                }
            }

            // Date picker row — visible only for "By Date" filter
            if (filterMode == "By Date") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick  = { showFilterDatePick = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.DateRange, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(dateFmt.format(filterDate))
                    }
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            if (reservations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.EventNote, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No reservations", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reservations, key = { it.reservationId }) { r ->
                        ReservationCard(
                            r        = r,
                            onEdit   = { vm.selectForEdit(r); showForm = true },
                            onDelete = { deleteTarget = r },
                            onStatusChange = { vm.updateStatus(r.reservationId, it) }
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        ReservationFormSheet(vm = vm, tables = tables, onDismiss = { showForm = false })
    }

    if (showFilterDatePick) {
        val state = rememberDatePickerState(initialSelectedDateMillis = filterDate.time)
        DatePickerDialog(
            onDismissRequest = { showFilterDatePick = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setFilterDate(java.util.Date(it)) }
                    showFilterDatePick = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showFilterDatePick = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    deleteTarget?.let { r ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Reservation") },
            text  = { Text("Delete reservation for ${r.customerName}?") },
            confirmButton = { TextButton(onClick = { vm.delete(r.reservationId); deleteTarget = null }) { Text("Delete", color = RedError) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ReservationCard(
    r: Reservation,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    val statusColor = when (r.status) {
        "Confirmed" -> GreenSuccess
        "Seated"    -> MaterialTheme.colorScheme.primary
        "Cancelled", "NoShow" -> RedError
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border  = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.customerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (r.phone.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Phone, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(r.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box {
                        Surface(shape = MaterialTheme.shapes.small, color = statusColor.copy(alpha = 0.15f)) {
                            Text(r.status, Modifier.padding(horizontal = 8.dp, vertical = 3.dp).noRippleClick { menuExpanded = true },
                                style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            listOf("Confirmed", "Seated", "Cancelled", "NoShow").forEach { s ->
                                DropdownMenuItem(text = { Text(s) }, onClick = { onStatusChange(s); menuExpanded = false })
                            }
                        }
                    }
                    IconButton(onClick = onEdit,   modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Edit,   null, tint = AmberWarning, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, tint = RedError,     modifier = Modifier.size(18.dp)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${dateFmt.format(r.reservationDate)} ${r.reservationTime}", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.People, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${r.partySize} guests", style = MaterialTheme.typography.bodySmall)
                }
                if (r.tableName != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.TableRestaurant, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(r.tableName, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (r.notes.isNotBlank()) Text(r.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Modifier.noRippleClick(onClick: () -> Unit): Modifier =
    clickable(indication = null, interactionSource = null, onClick = onClick)

@Composable
private fun ReservationFormSheet(
    vm: ReservationsViewModel,
    tables: List<com.fastpos.android.data.models.RestaurantTable>,
    onDismiss: () -> Unit
) {
    val formId     by vm.formId.collectAsState()
    val formName   by vm.formName.collectAsState()
    val formPhone  by vm.formPhone.collectAsState()
    val formParty  by vm.formParty.collectAsState()
    val formDate   by vm.formDate.collectAsState()
    val formTime   by vm.formTime.collectAsState()
    val formTableId by vm.formTableId.collectAsState()
    val formStatus by vm.formStatus.collectAsState()
    val formNotes  by vm.formNotes.collectAsState()

    var showDatePick  by remember { mutableStateOf(false) }
    var tableExpanded by remember { mutableStateOf(false) }
    var statusExpanded by remember { mutableStateOf(false) }
    var nameErr       by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (formId > 0) "Edit Reservation" else "New Reservation",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()

            OutlinedTextField(value = formName, onValueChange = { vm.setFormName(it); nameErr = false },
                label = { Text("Customer Name *") }, isError = nameErr, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = formPhone, onValueChange = vm::setFormPhone,
                label = { Text("Phone") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = dateFmt.format(formDate), onValueChange = {}, readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = { IconButton(onClick = { showDatePick = true }) { Icon(Icons.Default.DateRange, null) } },
                    modifier = Modifier.weight(1f))
                OutlinedTextField(value = formTime, onValueChange = vm::setFormTime,
                    label = { Text("Time") }, placeholder = { Text("19:00") },
                    modifier = Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = formParty, onValueChange = vm::setFormParty,
                    label = { Text("Guests") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f))

                // Table selector
                ExposedDropdownMenuBox(expanded = tableExpanded, onExpandedChange = { tableExpanded = it }, modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = tables.find { it.tableId == formTableId }?.tableName ?: "No table",
                        onValueChange = {}, readOnly = true, label = { Text("Table") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tableExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = tableExpanded, onDismissRequest = { tableExpanded = false }) {
                        DropdownMenuItem(text = { Text("No table") }, onClick = { vm.setFormTableId(null); tableExpanded = false })
                        tables.forEach { t ->
                            DropdownMenuItem(text = { Text(t.tableName) }, onClick = { vm.setFormTableId(t.tableId); tableExpanded = false })
                        }
                    }
                }
            }

            // Status (edit only)
            if (formId > 0) {
                ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it }) {
                    OutlinedTextField(value = formStatus, onValueChange = {}, readOnly = true, label = { Text("Status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                        vm.statusOptions.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { vm.setFormStatus(s); statusExpanded = false })
                        }
                    }
                }
            }

            OutlinedTextField(value = formNotes, onValueChange = vm::setFormNotes,
                label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    if (formName.isBlank()) { nameErr = true; return@Button }
                    vm.save(); onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp))
                Text(if (formId > 0) "Update Reservation" else "Save Reservation")
            }
        }
    }

    if (showDatePick) {
        val state = rememberDatePickerState(initialSelectedDateMillis = formDate.time)
        DatePickerDialog(
            onDismissRequest = { showDatePick = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setFormDate(java.util.Date(it)) }
                    showDatePick = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePick = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }
}
