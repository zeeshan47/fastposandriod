@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.tables

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.DiningArea
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.RestaurantTable
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.utils.formatDateTime
import com.fastpos.android.viewmodels.TableViewModel

@Composable
fun TableScreen(
    onNavigateBack:      () -> Unit,
    onNavigateToOrders:  () -> Unit = {},
    onNavigateToPos:     () -> Unit = {},
    onNavigateToPayment: (Int) -> Unit = {},
    vm: TableViewModel = hiltViewModel()
) {
    val tables      by vm.tables.collectAsState()
    val tableOrders by vm.tableOrders.collectAsState()
    val areas       by vm.areas.collectAsState()
    val isLoading   by vm.isLoading.collectAsState()
    val message     by vm.message.collectAsState()
    val settings    by vm.session.settings.collectAsState()
    val snack       = remember { SnackbarHostState() }

    var tab            by remember { mutableStateOf(0) }
    var changeTarget   by remember { mutableStateOf<RestaurantTable?>(null) }
    var orderSheet     by remember { mutableStateOf<Pair<RestaurantTable, Order>?>(null) }
    var transferTarget by remember { mutableStateOf<Pair<RestaurantTable, Order>?>(null) }
    var showAddDialog  by remember { mutableStateOf(false) }
    var editTarget     by remember { mutableStateOf<RestaurantTable?>(null) }
    var deleteTarget   by remember { mutableStateOf<RestaurantTable?>(null) }
    var showAreaDialog     by remember { mutableStateOf(false) }
    var editAreaTarget     by remember { mutableStateOf<DiningArea?>(null) }
    var deleteAreaTarget   by remember { mutableStateOf<DiningArea?>(null) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    val grouped = tables.groupBy { it.areaName ?: "General" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tables")
                        Text("${tables.count { it.tableStatus == "Available" }} available · ${tables.count { it.tableStatus == "Occupied" }} occupied",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (tab == 0) showAddDialog = true else showAreaDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, null) }
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->

        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Tables") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Areas") })
            }

            if (tab == 1) {
                // ── Areas tab ──────────────────────────────────────────────────
                if (areas.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Place, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No areas yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = vm::load) { Text("Refresh") }
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(areas, key = { it.areaId }) { area ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(area.areaName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text("Order: ${area.displayOrder}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { editAreaTarget = area }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = AmberWarning)
                                    }
                                    IconButton(onClick = { deleteAreaTarget = area }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = RedError)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ── Tables tab ─────────────────────────────────────────────────
                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (tables.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.TableRestaurant, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No tables found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = vm::load) { Text("Refresh") }
                        }
                    }
                } else {

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            grouped.forEach { (area, areaT) ->
                item {
                    Text(area, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                item {
                    TableGrid(
                        tables      = areaT,
                        tableOrders = tableOrders,
                        onTableClick = { table ->
                            val activeOrder = tableOrders[table.tableId]
                            if (activeOrder != null) {
                                orderSheet = Pair(table, activeOrder)
                            } else {
                                changeTarget = table
                            }
                        },
                        onEditTable   = { editTarget   = it },
                        onDeleteTable = { deleteTarget = it }
                    )
                }
            }
        }
                } // end else (tables not empty)
            } // end Tables tab else
        } // end outer Column
    } // end Scaffold

    // Area add dialog
    if (showAreaDialog) {
        AreaFormDialog(
            title     = "Add Area",
            onDismiss = { showAreaDialog = false },
            onConfirm = { name, order -> vm.addArea(name, order); showAreaDialog = false }
        )
    }

    // Area edit dialog
    editAreaTarget?.let { area ->
        AreaFormDialog(
            title        = "Edit Area",
            initialName  = area.areaName,
            initialOrder = area.displayOrder,
            onDismiss    = { editAreaTarget = null },
            onConfirm    = { name, order -> vm.updateArea(area.areaId, name, order); editAreaTarget = null }
        )
    }

    // Area delete confirmation
    deleteAreaTarget?.let { area ->
        AlertDialog(
            onDismissRequest = { deleteAreaTarget = null },
            icon    = { Icon(Icons.Default.Delete, null, tint = RedError) },
            title   = { Text("Delete '${area.areaName}'?") },
            text    = { Text("Area will be deactivated. Tables assigned to it will lose their area grouping.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteArea(area.areaId, area.areaName); deleteAreaTarget = null }) {
                    Text("Delete", color = RedError)
                }
            },
            dismissButton = { TextButton(onClick = { deleteAreaTarget = null }) { Text("Cancel") } }
        )
    }

    // Occupied table: active order mini-sheet
    orderSheet?.let { (table, order) ->
        ModalBottomSheet(
            onDismissRequest = { orderSheet = null },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TableRestaurant, null, tint = RedError, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(table.tableName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Occupied · Cap: ${table.capacity}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()

                // Order summary
                TableInfoRow("Token", order.tokenNo)
                TableInfoRow("Order No", order.orderNo)
                TableInfoRow("Type", order.orderType)
                TableInfoRow("Status", order.orderStatus)
                TableInfoRow("Items", order.itemCount.toString())
                TableInfoRow("Total", order.grandTotal.formatCurrency(settings.currencySymbol), bold = true)
                TableInfoRow("Placed", order.createdAt.formatDateTime())

                HorizontalDivider()

                if (order.paymentStatus != "Paid") {
                    Button(
                        onClick  = { orderSheet = null; onNavigateToPayment(order.orderId) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                    ) {
                        Icon(Icons.Default.Payment, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pay Now", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = { orderSheet = null; onNavigateToOrders() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueInfo)
                ) {
                    Icon(Icons.Default.ListAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("View in Orders", style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = { orderSheet = null; onNavigateToPos() },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    Icon(Icons.Default.AddShoppingCart, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open POS", style = MaterialTheme.typography.titleMedium)
                }

                OutlinedButton(
                    onClick = { transferTarget = Pair(table, order); orderSheet = null },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BlueInfo)
                ) {
                    Icon(Icons.Default.CompareArrows, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Transfer Table")
                }

            }
        }
    }

    // Transfer table dialog
    transferTarget?.let { (fromTable, order) ->
        val availableTables = tables.filter { it.tableStatus == "Available" && it.tableId != fromTable.tableId }
        AlertDialog(
            onDismissRequest = { transferTarget = null },
            icon  = { Icon(Icons.Default.CompareArrows, null, tint = BlueInfo) },
            title = { Text("Transfer: ${fromTable.tableName}") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select a table to transfer order ${order.tokenNo} to:",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (availableTables.isEmpty()) {
                        Text("No available tables to transfer to.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(availableTables) { targetTable ->
                                OutlinedButton(
                                    onClick = {
                                        vm.transferOrder(order.orderId, fromTable.tableId, targetTable.tableId)
                                        transferTarget = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess)
                                ) {
                                    Icon(Icons.Default.TableRestaurant, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(targetTable.tableName)
                                    targetTable.areaName?.let {
                                        Spacer(Modifier.width(4.dp))
                                        Text("· $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { transferTarget = null }) { Text("Cancel") } }
        )
    }

    // Status change dialog (Available table — only action is Start New Order)
    changeTarget?.let { table ->
        AlertDialog(
            onDismissRequest = { changeTarget = null },
            title = { Text(table.tableName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current: ${table.tableStatus}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (table.tableStatus == "Available") {
                        OutlinedButton(
                            onClick = { changeTarget = null; onNavigateToPos() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess)
                        ) {
                            Icon(Icons.Default.AddShoppingCart, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Start New Order")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { changeTarget = null }) { Text("Cancel") } }
        )
    }

    // Add table dialog
    if (showAddDialog) {
        TableFormDialog(
            title  = "Add Table",
            areas  = areas,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, areaId, capacity ->
                vm.addTable(name, areaId, capacity)
                showAddDialog = false
            }
        )
    }

    // Edit table dialog
    editTarget?.let { table ->
        TableFormDialog(
            title        = "Edit Table",
            areas        = areas,
            initialName  = table.tableName,
            initialArea  = table.areaId,
            initialCap   = table.capacity,
            onDismiss    = { editTarget = null },
            onConfirm    = { name, areaId, capacity ->
                vm.updateTable(table.tableId, name, areaId, capacity)
                editTarget = null
            }
        )
    }

    // Delete confirmation dialog
    deleteTarget?.let { table ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon    = { Icon(Icons.Default.Delete, null, tint = RedError) },
            title   = { Text("Delete '${table.tableName}'?") },
            text    = { Text("This table will be deactivated. Active orders prevent deletion.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTable(table); deleteTarget = null }) {
                    Text("Delete", color = RedError)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TableGrid(
    tables:       List<RestaurantTable>,
    tableOrders:  Map<Int, Order>,
    onTableClick: (RestaurantTable) -> Unit,
    onEditTable:  (RestaurantTable) -> Unit,
    onDeleteTable:(RestaurantTable) -> Unit
) {
    val columns = 3
    val rows    = (tables.size + columns - 1) / columns

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (col in 0 until columns) {
                    val idx = row * columns + col
                    if (idx < tables.size) {
                        TableCard(
                            table       = tables[idx],
                            activeOrder = tableOrders[tables[idx].tableId],
                            onClick     = { onTableClick(tables[idx]) },
                            onEdit      = { onEditTable(tables[idx]) },
                            onDelete    = { onDeleteTable(tables[idx]) },
                            modifier    = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCard(
    table:       RestaurantTable,
    activeOrder: Order?,
    onClick:     () -> Unit,
    onEdit:      () -> Unit,
    onDelete:    () -> Unit,
    modifier:    Modifier = Modifier
) {
    val color = statusColor(table.tableStatus)
    Card(
        onClick  = onClick,
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        border   = androidx.compose.foundation.BorderStroke(1.5.dp, color)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(6.dp),
            verticalArrangement   = Arrangement.spacedBy(2.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Text(table.tableName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
            Text(table.tableStatus, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
            if (activeOrder != null) {
                Text(activeOrder.tokenNo, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Cap: ${table.capacity}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(14.dp), tint = AmberWarning)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = RedError)
                }
            }
        }
    }
}

@Composable
private fun TableFormDialog(
    title:       String,
    areas:       List<DiningArea>,
    initialName: String  = "",
    initialArea: Int?    = null,
    initialCap:  Int     = 4,
    onDismiss:   () -> Unit,
    onConfirm:   (name: String, areaId: Int?, capacity: Int) -> Unit
) {
    var name     by remember { mutableStateOf(initialName) }
    var capacity by remember { mutableStateOf(initialCap.toString()) }
    var areaId   by remember { mutableStateOf(initialArea) }
    var areaExpanded by remember { mutableStateOf(false) }

    val selectedAreaName = areas.find { it.areaId == areaId }?.areaName ?: "None"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Table Name *") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded         = areaExpanded,
                    onExpandedChange = { areaExpanded = it }
                ) {
                    OutlinedTextField(
                        value         = selectedAreaName,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Dining Area (optional)") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(areaExpanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded         = areaExpanded,
                        onDismissRequest = { areaExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text    = { Text("None") },
                            onClick = { areaId = null; areaExpanded = false }
                        )
                        areas.forEach { area ->
                            DropdownMenuItem(
                                text    = { Text(area.areaName) },
                                onClick = { areaId = area.areaId; areaExpanded = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value         = capacity,
                    onValueChange = { if (it.all(Char::isDigit)) capacity = it },
                    label         = { Text("Capacity") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val cap = capacity.toIntOrNull()?.coerceAtLeast(1) ?: 4
                onConfirm(name.trim(), areaId, cap)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TableInfoRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun statusColor(status: String) = when (status) {
    "Available" -> GreenSuccess
    "Occupied"  -> RedError
    else        -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun AreaFormDialog(
    title:        String,
    initialName:  String = "",
    initialOrder: Int    = 0,
    onDismiss:    () -> Unit,
    onConfirm:    (name: String, displayOrder: Int) -> Unit
) {
    var name         by remember { mutableStateOf(initialName) }
    var displayOrder by remember { mutableStateOf(initialOrder.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Area Name *") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value         = displayOrder,
                    onValueChange = { if (it.all(Char::isDigit)) displayOrder = it },
                    label         = { Text("Display Order") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank())
                        onConfirm(name.trim(), displayOrder.toIntOrNull() ?: 0)
                },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
