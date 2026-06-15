@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.waste

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.Product
import com.fastpos.android.data.models.WasteEntry
import com.fastpos.android.data.models.WasteEntryItem
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.WasteViewModel
import java.text.SimpleDateFormat
import java.util.*

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val UNITS   = listOf("kg", "g", "liters", "ml", "pieces", "packs", "dozen")

@Composable
fun WasteScreen(
    onNavigateBack: () -> Unit,
    vm: WasteViewModel = hiltViewModel()
) {
    val entries       by vm.entries.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()
    val message       by vm.message.collectAsState()
    val stockProducts by vm.stockProducts.collectAsState()
    val pendingItems  by vm.pendingItems.collectAsState()
    val selectedItems by vm.selectedItems.collectAsState()
    val fromDate      by vm.fromDate.collectAsState()
    val toDate        by vm.toDate.collectAsState()
    val settings      by vm.session.settings.collectAsState()
    val sym           = settings.currencySymbol

    val snackHost     = remember { SnackbarHostState() }
    var showNewSheet  by remember { mutableStateOf(false) }
    var viewEntry     by remember { mutableStateOf<WasteEntry?>(null) }
    var deleteTarget  by remember { mutableStateOf<WasteEntry?>(null) }

    LaunchedEffect(message) {
        message?.let { snackHost.showSnackbar(it); vm.clearMessage() }
    }

    // View entry items sheet
    viewEntry?.let { entry ->
        EntryDetailSheet(
            entry = entry,
            items = selectedItems,
            sym   = sym,
            onDelete = { deleteTarget = entry; viewEntry = null },
            onDismiss = { viewEntry = null }
        )
    }

    // New entry sheet
    if (showNewSheet) {
        NewEntrySheet(
            pendingItems  = pendingItems,
            stockProducts = stockProducts,
            sym           = sym,
            onAddItem     = { vm.addPendingItem(it) },
            onRemoveItem  = { vm.removePendingItem(it) },
            onSave        = { notes -> vm.saveEntry(notes) },
            onDismiss     = { vm.clearPendingItems(); showNewSheet = false }
        )
    }

    // Delete confirmation
    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Waste Entry") },
            text  = { Text("Delete waste entry #${entry.wasteId} (${entry.itemCount} items)? Stock will be reversed.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteEntry(entry.wasteId); deleteTarget = null },
                           colors = ButtonDefaults.textButtonColors(contentColor = RedError)) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Waste / Spoilage") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snackHost) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewSheet = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, null, tint = Color.White) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DateFilterBar(fromDate, toDate, vm::setFromDate, vm::setToDate)

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DeleteOutline, null, Modifier.size(56.dp),
                             tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No waste entries", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val totalWaste = entries.sumOf { it.totalAmount }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${entries.size} entries", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Total: ${totalWaste.formatCurrency(sym)}",
                            style     = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color     = RedError
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries) { entry ->
                        WasteEntryCard(
                            entry   = entry,
                            sym     = sym,
                            onClick = {
                                viewEntry = entry
                                vm.loadEntryItems(entry.wasteId)
                            },
                            onDelete = { deleteTarget = entry }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date filter bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DateFilterBar(
    fromDate:     Date,
    toDate:       Date,
    onFromChange: (Date) -> Unit,
    onToChange:   (Date) -> Unit
) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("From", style = MaterialTheme.typography.labelSmall)
        OutlinedButton(
            onClick = {
                val cal = Calendar.getInstance().also { it.time = fromDate }
                DatePickerDialog(ctx, { _, y, m, d ->
                    onFromChange(Calendar.getInstance().also { c ->
                        c.set(y, m, d, 0, 0, 0); c.set(Calendar.MILLISECOND, 0)
                    }.time)
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) { Text(dateFmt.format(fromDate), style = MaterialTheme.typography.bodySmall) }

        Text("To", style = MaterialTheme.typography.labelSmall)
        OutlinedButton(
            onClick = {
                val cal = Calendar.getInstance().also { it.time = toDate }
                DatePickerDialog(ctx, { _, y, m, d ->
                    onToChange(Calendar.getInstance().also { c ->
                        c.set(y, m, d, 23, 59, 59); c.set(Calendar.MILLISECOND, 999)
                    }.time)
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) { Text(dateFmt.format(toDate), style = MaterialTheme.typography.bodySmall) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Waste entry card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WasteEntryCard(
    entry:    WasteEntry,
    sym:      String,
    onClick:  () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.DeleteOutline, null, Modifier.size(32.dp), tint = RedError)
            Column(Modifier.weight(1f)) {
                Text(
                    text  = dateFmt.format(entry.wasteDate),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (entry.notes.isNotBlank()) {
                    Text(entry.notes, style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    text  = "${entry.itemCount} item${if (entry.itemCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text  = entry.totalAmount.formatCurrency(sym),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = RedError
                )
                Text("#${entry.wasteId}", style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, null, tint = RedError.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry detail bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EntryDetailSheet(
    entry:    WasteEntry,
    items:    List<WasteEntryItem>,
    sym:      String,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("Waste Entry #${entry.wasteId}", style = MaterialTheme.typography.titleMedium,
                         fontWeight = FontWeight.Bold)
                    Text(dateFmt.format(entry.wasteDate), style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = RedError)
                }
            }

            if (entry.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(entry.notes, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            if (items.isEmpty()) {
                Text("No items", color = MaterialTheme.colorScheme.onSurfaceVariant,
                     modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                items.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.itemName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            if (item.reason.isNotBlank()) {
                                Text(item.reason, style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            text  = "${item.quantity} ${item.unit}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        Text(
                            text  = item.amount.formatCurrency(sym),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = RedError
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                }

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("Total: ${entry.totalAmount.formatCurrency(sym)}",
                         style = MaterialTheme.typography.bodyMedium,
                         fontWeight = FontWeight.Bold,
                         color = RedError)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// New entry bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NewEntrySheet(
    pendingItems:  List<WasteEntryItem>,
    stockProducts: List<Product>,
    sym:           String,
    onAddItem:     (WasteEntryItem) -> Unit,
    onRemoveItem:  (Int) -> Unit,
    onSave:        (String) -> Unit,
    onDismiss:     () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var notes      by remember { mutableStateOf("") }
    var showItemDialog by remember { mutableStateOf(false) }

    if (showItemDialog) {
        AddItemDialog(
            stockProducts = stockProducts,
            onConfirm     = { item -> onAddItem(item); showItemDialog = false },
            onDismiss     = { showItemDialog = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("New Waste Entry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value         = notes,
                onValueChange = { notes = it },
                label         = { Text("Notes (optional)") },
                modifier      = Modifier.fillMaxWidth(),
                maxLines      = 2
            )

            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Items", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { showItemDialog = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Item")
                }
            }

            if (pendingItems.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No items added", color = MaterialTheme.colorScheme.onSurfaceVariant,
                         style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(pendingItems) { idx, item ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.itemName, style = MaterialTheme.typography.bodySmall,
                                     fontWeight = FontWeight.SemiBold)
                                Text("${item.quantity} ${item.unit}  •  ${item.amount.formatCurrency(sym)}",
                                     style = MaterialTheme.typography.labelSmall,
                                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.reason.isNotBlank()) {
                                    Text(item.reason, style = MaterialTheme.typography.labelSmall,
                                         color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onRemoveItem(idx) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, null, tint = RedError, modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }

                val totalAmt = pendingItems.sumOf { it.quantity * it.rate }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    Text("Total: ${totalAmt.formatCurrency(sym)}",
                         style = MaterialTheme.typography.bodySmall,
                         fontWeight = FontWeight.Bold,
                         color = RedError)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onSave(notes); onDismiss() },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled  = pendingItems.isNotEmpty()
                ) {
                    Text("Save Entry")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add item dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddItemDialog(
    stockProducts: List<Product>,
    onConfirm:     (WasteEntryItem) -> Unit,
    onDismiss:     () -> Unit
) {
    var selectedProductId   by remember { mutableIntStateOf(0) }
    var selectedProductName by remember { mutableStateOf("") }
    var productExpanded     by remember { mutableStateOf(false) }
    var qty                 by remember { mutableStateOf("") }
    var unit                by remember { mutableStateOf("kg") }
    var unitExpanded        by remember { mutableStateOf(false) }
    var rate                by remember { mutableStateOf("") }
    var reason              by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Waste Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Material picker
                Box {
                    OutlinedTextField(
                        value         = selectedProductName.ifBlank { "Select material…" },
                        onValueChange = {},
                        label         = { Text("Material") },
                        readOnly      = true,
                        modifier      = Modifier.fillMaxWidth(),
                        trailingIcon  = {
                            IconButton(onClick = { productExpanded = !productExpanded }) {
                                Icon(if (productExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded         = productExpanded,
                        onDismissRequest = { productExpanded = false },
                        modifier         = Modifier.heightIn(max = 200.dp)
                    ) {
                        stockProducts.forEach { p ->
                            DropdownMenuItem(
                                text    = { Text(p.productName) },
                                onClick = {
                                    selectedProductId   = p.productId
                                    selectedProductName = p.productName
                                    if (p.costPrice > 0) rate = "%.2f".format(p.costPrice)
                                    productExpanded     = false
                                }
                            )
                        }
                        if (stockProducts.isEmpty()) {
                            DropdownMenuItem(
                                text    = { Text("No stock-managed products", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { productExpanded = false }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = qty,
                        onValueChange = { qty = it },
                        label         = { Text("Qty") },
                        modifier      = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine    = true
                    )

                    Box(Modifier.weight(0.8f)) {
                        OutlinedTextField(
                            value         = unit,
                            onValueChange = {},
                            label         = { Text("Unit") },
                            readOnly      = true,
                            modifier      = Modifier.fillMaxWidth(),
                            trailingIcon  = {
                                IconButton(onClick = { unitExpanded = !unitExpanded }) {
                                    Icon(if (unitExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null)
                                }
                            }
                        )
                        DropdownMenu(
                            expanded         = unitExpanded,
                            onDismissRequest = { unitExpanded = false }
                        ) {
                            UNITS.forEach { u ->
                                DropdownMenuItem(
                                    text    = { Text(u) },
                                    onClick = { unit = u; unitExpanded = false }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value         = rate,
                    onValueChange = { rate = it },
                    label         = { Text("Rate (per unit)") },
                    modifier      = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine    = true
                )

                OutlinedTextField(
                    value         = reason,
                    onValueChange = { reason = it },
                    label         = { Text("Reason (optional)") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val q = qty.toDoubleOrNull() ?: 0.0
                    val r = rate.toDoubleOrNull() ?: 0.0
                    val name = selectedProductName.ifBlank { "Unknown" }
                    if (q > 0) {
                        onConfirm(
                            WasteEntryItem(
                                materialId = selectedProductId,
                                itemName   = name,
                                quantity   = q,
                                unit       = unit,
                                rate       = r,
                                amount     = q * r,
                                reason     = reason.trim()
                            )
                        )
                    }
                },
                enabled = qty.toDoubleOrNull()?.let { it > 0 } == true
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
