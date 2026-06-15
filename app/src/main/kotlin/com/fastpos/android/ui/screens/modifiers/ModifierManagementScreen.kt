@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.modifiers

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.ModifierGroup
import com.fastpos.android.data.models.Product
import com.fastpos.android.data.models.ProductModifier
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.ModifierViewModel

@Composable
fun ModifierManagementScreen(
    onNavigateBack: () -> Unit,
    vm: ModifierViewModel = hiltViewModel()
) {
    val groups        by vm.groups.collectAsState()
    val selectedGroup by vm.selectedGroup.collectAsState()
    val stockItems    by vm.stockItems.collectAsState()
    val isLoading     by vm.isLoading.collectAsState()
    val message       by vm.message.collectAsState()
    val snack         = remember { SnackbarHostState() }

    var showAddGroup   by remember { mutableStateOf(false) }
    var editGroup      by remember { mutableStateOf<ModifierGroup?>(null) }
    var deleteGroup    by remember { mutableStateOf<ModifierGroup?>(null) }
    var showAddOption  by remember { mutableStateOf(false) }
    var editOption     by remember { mutableStateOf<ProductModifier?>(null) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modifier Groups") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedGroup != null) vm.clearSelection() else onNavigateBack()
                    }) {
                        Icon(if (selectedGroup != null) Icons.Default.ArrowBack else Icons.Default.ArrowBack, null)
                    }
                },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (selectedGroup != null) showAddOption = true else showAddGroup = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, "Add") }
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            if (selectedGroup == null) {
                // Groups list
                GroupsPane(
                    groups    = groups,
                    onSelect  = { vm.selectGroup(it) },
                    onEdit    = { editGroup = it },
                    onDelete  = { deleteGroup = it }
                )
            } else {
                // Modifiers for selected group
                ModifiersPane(
                    group     = selectedGroup!!,
                    onEdit    = { editOption = it },
                    onDelete  = { vm.deleteModifier(it.modifierId) }
                )
            }
        }
    }

    if (showAddGroup) {
        ModifierGroupDialog(
            onConfirm = { name, min, max, req ->
                vm.addGroup(name, min, max, req)
                showAddGroup = false
            },
            onDismiss = { showAddGroup = false }
        )
    }

    editGroup?.let { g ->
        ModifierGroupDialog(
            title        = "Edit Group",
            initialName  = g.groupName,
            initialMin   = g.minSelection,
            initialMax   = g.maxSelection,
            initialReq   = g.isRequired,
            onConfirm    = { name, min, max, req ->
                vm.updateGroup(g.modifierGroupId, name, min, max, req)
                editGroup = null
            },
            onDismiss    = { editGroup = null }
        )
    }

    deleteGroup?.let { g ->
        AlertDialog(
            onDismissRequest = { deleteGroup = null },
            icon  = { Icon(Icons.Default.Warning, null, tint = RedError) },
            title = { Text("Delete Group?") },
            text  = { Text("Delete '${g.groupName}' and all its options?") },
            confirmButton = {
                Button(onClick = { vm.deleteGroup(g.modifierGroupId); deleteGroup = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteGroup = null }) { Text("Cancel") } }
        )
    }

    if (showAddOption) {
        selectedGroup?.let { g ->
            ModifierOptionDialog(
                stockItems = stockItems,
                onConfirm  = { name, price, stockItemId ->
                    vm.addModifier(g.modifierGroupId, name, price, stockItemId)
                    showAddOption = false
                },
                onDismiss  = { showAddOption = false }
            )
        }
    }

    editOption?.let { opt ->
        ModifierOptionDialog(
            title           = "Edit Option",
            initialName     = opt.modifierName,
            initialPrice    = opt.extraPrice,
            initialStockId  = opt.stockItemId,
            stockItems      = stockItems,
            onConfirm       = { name, price, stockItemId ->
                vm.updateModifier(opt.modifierId, name, price, stockItemId)
                editOption = null
            },
            onDismiss       = { editOption = null }
        )
    }
}

@Composable
private fun GroupsPane(
    groups: List<ModifierGroup>,
    onSelect: (ModifierGroup) -> Unit,
    onEdit:   (ModifierGroup) -> Unit,
    onDelete: (ModifierGroup) -> Unit
) {
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Tune, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No modifier groups", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap + to create one", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(groups, key = { it.modifierGroupId }) { g ->
            GroupCard(group = g, onSelect = { onSelect(g) }, onEdit = { onEdit(g) }, onDelete = { onDelete(g) })
        }
    }
}

@Composable
private fun GroupCard(
    group: ModifierGroup,
    onSelect: () -> Unit,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Tune, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(group.groupName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (group.isRequired) {
                            Surface(shape = MaterialTheme.shapes.extraSmall, color = RedError.copy(0.15f)) {
                                Text("Required", style = MaterialTheme.typography.labelSmall, color = RedError,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                            }
                        }
                    }
                    Text(
                        "Select ${group.minSelection}–${group.maxSelection}  •  ${group.modifiers.size} option(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit,   null, tint = AmberWarning) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = RedError) }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ModifiersPane(
    group:    ModifierGroup,
    onEdit:   (ProductModifier) -> Unit,
    onDelete: (ProductModifier) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // Group summary header
        Card(
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(0.1f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(group.groupName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select ${group.minSelection}–${group.maxSelection}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (group.isRequired) Text("Required", style = MaterialTheme.typography.labelSmall, color = RedError)
                }
            }
        }

        if (group.modifiers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AddCircleOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No options yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap + to add options", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(group.modifiers, key = { it.modifierId }) { mod ->
                ModifierOptionCard(modifier = mod, onEdit = { onEdit(mod) }, onDelete = { onDelete(mod) })
            }
        }
    }
}

@Composable
private fun ModifierOptionCard(
    modifier: ProductModifier,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.RadioButtonChecked, null, Modifier.size(24.dp), tint = AmberWarning)
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(modifier.modifierName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (modifier.stockItemName.isNotBlank()) {
                        Text(
                            "Stock: ${modifier.stockItemName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (modifier.extraPrice > 0)
                    Text("+${modifier.extraPrice.formatCurrency("")}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = GreenSuccess)
                else
                    Text("Free", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit,   null, Modifier.size(18.dp), tint = AmberWarning) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = RedError) }
            }
        }
    }
}

@Composable
private fun ModifierGroupDialog(
    title:       String = "Add Modifier Group",
    initialName: String = "",
    initialMin:  Int    = 0,
    initialMax:  Int    = 1,
    initialReq:  Boolean = false,
    onConfirm: (String, Int, Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var name      by remember { mutableStateOf(initialName) }
    var minText   by remember { mutableStateOf(initialMin.toString()) }
    var maxText   by remember { mutableStateOf(initialMax.toString()) }
    var required  by remember { mutableStateOf(initialReq) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Tune, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Group Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText, onValueChange = { minText = it },
                        label = { Text("Min") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = maxText, onValueChange = { maxText = it },
                        label = { Text("Max") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = required, onCheckedChange = { required = it })
                    Text("Required selection", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val min = minText.toIntOrNull() ?: 0
                        val max = (maxText.toIntOrNull() ?: 1).coerceAtLeast(min)
                        onConfirm(name, min, max, required)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ModifierOptionDialog(
    title:          String  = "Add Option",
    initialName:    String  = "",
    initialPrice:   Double  = 0.0,
    initialStockId: Int     = 0,
    stockItems:     List<Product> = emptyList(),
    onConfirm: (name: String, price: Double, stockItemId: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val hasStock        = stockItems.isNotEmpty()
    var linkToStock     by remember { mutableStateOf(initialStockId > 0 && hasStock) }
    var name            by remember { mutableStateOf(initialName) }
    var priceText       by remember { mutableStateOf(if (initialPrice > 0) initialPrice.toString() else "") }
    var stockExpanded   by remember { mutableStateOf(false) }
    var selectedStock   by remember {
        mutableStateOf(stockItems.find { it.productId == initialStockId })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.RadioButtonChecked, null, tint = AmberWarning) },
        title = { Text(title) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                if (hasStock) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = linkToStock,
                            onCheckedChange = {
                                linkToStock = it
                                if (!it) selectedStock = null
                            }
                        )
                        Text("Link to stock item", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                if (linkToStock) {
                    ExposedDropdownMenuBox(
                        expanded = stockExpanded,
                        onExpandedChange = { stockExpanded = !stockExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedStock?.productName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Stock Item *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(stockExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = stockExpanded,
                            onDismissRequest = { stockExpanded = false }
                        ) {
                            stockItems.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.productName) },
                                    onClick = {
                                        selectedStock = item
                                        name = item.productName
                                        stockExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Option Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = priceText, onValueChange = { priceText = it },
                    label = { Text("Extra Price (0 = free)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (linkToStock) selectedStock?.productName ?: "" else name
                    if (finalName.isNotBlank()) {
                        val price       = priceText.toDoubleOrNull() ?: 0.0
                        val stockItemId = if (linkToStock) selectedStock?.productId ?: 0 else 0
                        onConfirm(finalName, price, stockItemId)
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
