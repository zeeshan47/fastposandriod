@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.waiters

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
import com.fastpos.android.data.models.Waiter
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.WaitersViewModel

@Composable
fun WaitersScreen(
    onNavigateBack: () -> Unit,
    vm: WaitersViewModel = hiltViewModel()
) {
    val waiters   by vm.waiters.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val message   by vm.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog  by remember { mutableStateOf(false) }
    var editingWaiter  by remember { mutableStateOf<Waiter?>(null) }
    var deleteTarget   by remember { mutableStateOf<Waiter?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }

    val employees by vm.employees.collectAsState()

    if (showAddDialog || editingWaiter != null) {
        WaiterDialog(
            waiter    = editingWaiter,
            employees = employees,
            onSave    = { name, phone, isActive, linkedEmpId ->
                if (editingWaiter != null)
                    vm.updateWaiter(editingWaiter!!.waiterId, name, phone, isActive, linkedEmpId)
                else
                    vm.addWaiter(name, phone, linkedEmpId)
                showAddDialog = false
                editingWaiter = null
            },
            onDismiss = { showAddDialog = false; editingWaiter = null }
        )
    }

    deleteTarget?.let { waiter ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title  = { Text("Remove Waiter") },
            text   = { Text("Remove ${waiter.waiterName} from the waiters list?") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteWaiter(waiter.waiterId); deleteTarget = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = RedError)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                title = { Text("Waiters") },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, "Refresh") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, null, tint = Color.White) }
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            waiters.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SupportAgent, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No waiters added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(waiters, key = { it.waiterId }) { waiter ->
                    WaiterCard(
                        waiter   = waiter,
                        onEdit   = { editingWaiter = waiter },
                        onDelete = { deleteTarget  = waiter }
                    )
                }
            }
        }
    }
}

@Composable
private fun WaiterCard(
    waiter:   Waiter,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Avatar with initial
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (waiter.isActive) AmberWarning.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            waiter.waiterName.take(1).uppercase(),
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = if (waiter.isActive) AmberWarning else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(waiter.waiterName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (waiter.phone.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(waiter.phone, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (waiter.linkedEmployeeName.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(waiter.linkedEmployeeName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!waiter.isActive) {
                        Badge(containerColor = MaterialTheme.colorScheme.outline) {
                            Text("Inactive", style = MaterialTheme.typography.labelSmall,
                                color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, tint = AmberWarning)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = RedError)
                }
            }
        }
    }
}

@Composable
private fun WaiterDialog(
    waiter:    Waiter?,
    employees: List<Pair<Int, String>>,
    onSave:    (name: String, phone: String, isActive: Boolean, linkedEmployeeId: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var name              by remember { mutableStateOf(waiter?.waiterName ?: "") }
    var phone             by remember { mutableStateOf(waiter?.phone ?: "") }
    var isActive          by remember { mutableStateOf(waiter?.isActive ?: true) }
    var selectedEmpId     by remember { mutableStateOf(waiter?.linkedEmployeeId) }
    var empDropdownExpanded by remember { mutableStateOf(false) }
    var nameError         by remember { mutableStateOf(false) }

    val selectedEmpName = employees.firstOrNull { it.first == selectedEmpId }?.second ?: "None"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (waiter != null) "Edit Waiter" else "Add Waiter") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it; nameError = false },
                    label         = { Text("Waiter Name *") },
                    isError       = nameError,
                    supportingText = if (nameError) {{ Text("Name is required") }} else null,
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = phone,
                    onValueChange = { phone = it },
                    label         = { Text("Phone") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    leadingIcon   = { Icon(Icons.Default.Phone, null, Modifier.size(16.dp)) },
                    modifier      = Modifier.fillMaxWidth()
                )
                // Linked employee dropdown
                ExposedDropdownMenuBox(
                    expanded        = empDropdownExpanded,
                    onExpandedChange = { empDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = selectedEmpName,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Linked Employee") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(empDropdownExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded        = empDropdownExpanded,
                        onDismissRequest = { empDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text    = { Text("None") },
                            onClick = { selectedEmpId = null; empDropdownExpanded = false }
                        )
                        employees.forEach { (id, empName) ->
                            DropdownMenuItem(
                                text    = { Text(empName) },
                                onClick = { selectedEmpId = id; empDropdownExpanded = false }
                            )
                        }
                    }
                }
                if (waiter != null) {
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
                if (name.isBlank()) { nameError = true; return@Button }
                onSave(name.trim(), phone.trim(), isActive, selectedEmpId)
            }) { Text(if (waiter != null) "Update" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
