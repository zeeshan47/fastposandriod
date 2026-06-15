@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.deals

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
import com.fastpos.android.data.models.Deal
import com.fastpos.android.data.models.DealItem
import com.fastpos.android.data.models.Product
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.DealsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun DealsScreen(
    onNavigateBack: () -> Unit,
    vm: DealsViewModel = hiltViewModel()
) {
    val deals    by vm.deals.collectAsState()
    val loading  by vm.loading.collectAsState()
    val message  by vm.message.collectAsState()
    val selected by vm.selected.collectAsState()
    val settings by vm.session.settings.collectAsState()
    val snack    = remember { SnackbarHostState() }
    var showForm    by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Deal?>(null) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deals & Combos") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = vm::loadDeals) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.selectDeal(null); showForm = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (deals.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LocalOffer, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No deals yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(deals, key = { it.dealId }) { deal ->
                    DealCard(
                        deal     = deal,
                        currency = settings.currencySymbol,
                        onEdit   = { vm.selectDeal(deal); showForm = true },
                        onDelete = { deleteTarget = deal }
                    )
                }
            }
        }
    }

    if (showForm) {
        DealFormSheet(vm = vm, onDismiss = { showForm = false })
    }

    deleteTarget?.let { d ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Deal") },
            text  = { Text("Delete \"${d.dealName}\"?") },
            confirmButton = { TextButton(onClick = { vm.deleteDeal(d.dealId); deleteTarget = null }) { Text("Delete", color = RedError) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DealCard(deal: Deal, currency: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (deal.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(deal.dealName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (!deal.isActive) {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) {
                                Text("Inactive", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (deal.description.isNotBlank())
                        Text(deal.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit,   modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Edit,   null, tint = AmberWarning, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, tint = RedError,     modifier = Modifier.size(18.dp)) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (deal.dealPrice > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.AttachMoney, null, Modifier.size(14.dp), tint = GreenSuccess)
                        Text("$currency ${"%.2f".format(deal.dealPrice)}", style = MaterialTheme.typography.bodySmall, color = GreenSuccess, fontWeight = FontWeight.Bold)
                    }
                }
                if (deal.discountPercent > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Percent, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("${deal.discountPercent}% off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (deal.items.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Fastfood, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${deal.items.size} items", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (deal.validFrom != null || deal.validTo != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.DateRange, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    val fromStr = deal.validFrom?.let { dateFmt.format(it) } ?: "—"
                    val toStr   = deal.validTo?.let { dateFmt.format(it) }   ?: "—"
                    Text("$fromStr → $toStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DealFormSheet(vm: DealsViewModel, onDismiss: () -> Unit) {
    val products  by vm.products.collectAsState()
    val formItems by vm.formItems.collectAsState()
    val selected  by vm.selected.collectAsState()

    val formName     by vm.formName.collectAsState()
    val formDesc     by vm.formDesc.collectAsState()
    val formPrice    by vm.formPrice.collectAsState()
    val formDiscount by vm.formDiscount.collectAsState()
    val formValidFrom by vm.formValidFrom.collectAsState()
    val formValidTo   by vm.formValidTo.collectAsState()
    val formIsActive  by vm.formIsActive.collectAsState()

    var nameErr      by remember { mutableStateOf(false) }
    var itemsErr     by remember { mutableStateOf(false) }
    var showFromPick by remember { mutableStateOf(false) }
    var showToPick   by remember { mutableStateOf(false) }
    var showAddItem  by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (selected != null) "Edit Deal" else "New Deal",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()

            OutlinedTextField(value = formName, onValueChange = { vm.setFormName(it); nameErr = false },
                label = { Text("Deal Name *") }, isError = nameErr, modifier = Modifier.fillMaxWidth(),
                supportingText = if (nameErr) ({ Text("Required") }) else null)

            OutlinedTextField(value = formDesc, onValueChange = vm::setFormDesc,
                label = { Text("Description") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = formPrice, onValueChange = vm::setFormPrice,
                    label = { Text("Deal Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f))
                OutlinedTextField(value = formDiscount, onValueChange = vm::setFormDiscount,
                    label = { Text("Discount %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = formValidFrom?.let { dateFmt.format(it) } ?: "",
                    onValueChange = {}, readOnly = true, label = { Text("Valid From") },
                    trailingIcon = { IconButton(onClick = { showFromPick = true }) { Icon(Icons.Default.DateRange, null) } },
                    modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = formValidTo?.let { dateFmt.format(it) } ?: "",
                    onValueChange = {}, readOnly = true, label = { Text("Valid To") },
                    trailingIcon = { IconButton(onClick = { showToPick = true }) { Icon(Icons.Default.DateRange, null) } },
                    modifier = Modifier.weight(1f))
            }

            if (selected != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = formIsActive, onCheckedChange = vm::setFormIsActive)
                }
            }

            // Items section
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FilledTonalButton(onClick = { showAddItem = true; itemsErr = false }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Item", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (itemsErr) {
                Text("Add at least one item", style = MaterialTheme.typography.bodySmall, color = RedError)
            }

            if (formItems.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text("No items added", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    formItems.forEachIndexed { idx, item ->
                        DealItemRow(item = item, onRemove = { vm.removeFormItem(idx) })
                    }
                }
            }

            Button(
                onClick = {
                    var valid = true
                    if (formName.isBlank()) { nameErr = true; valid = false }
                    if (formItems.isEmpty()) { itemsErr = true; valid = false }
                    if (valid) { vm.saveDeal(); onDismiss() }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp))
                Text(if (selected != null) "Update Deal" else "Save Deal")
            }
        }
    }

    if (showAddItem) {
        AddDealItemDialog(products = products, vm = vm, onDismiss = { showAddItem = false }) { item ->
            vm.addFormItem(item)
            showAddItem = false
        }
    }

    if (showFromPick) {
        DealDatePickerDialog(
            initialMillis = formValidFrom?.time,
            onDismiss = { showFromPick = false }
        ) { vm.setFormValidFrom(java.util.Date(it)); showFromPick = false }
    }

    if (showToPick) {
        DealDatePickerDialog(
            initialMillis = formValidTo?.time,
            onDismiss = { showToPick = false }
        ) { vm.setFormValidTo(java.util.Date(it)); showToPick = false }
    }
}

@Composable
private fun DealItemRow(item: DealItem, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.Fastfood, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            val nameLabel = if (item.sizeName != null) "${item.productName} (${item.sizeName})" else item.productName
            Text(nameLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            val subLabel = buildString {
                append("×${item.quantity}")
                if (item.isOptional) append(" · Optional")
            }
            Text(subLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null, tint = RedError, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AddDealItemDialog(
    products: List<Product>,
    vm: DealsViewModel,
    onDismiss: () -> Unit,
    onAdd: (DealItem) -> Unit
) {
    val sizes            by vm.sizesForItem.collectAsState()
    var selectedProduct  by remember { mutableStateOf<Product?>(null) }
    var productExpanded  by remember { mutableStateOf(false) }
    var selectedSizeId   by remember { mutableStateOf<Int?>(null) }
    var selectedSizeName by remember { mutableStateOf<String?>(null) }
    var sizeExpanded     by remember { mutableStateOf(false) }
    var quantity         by remember { mutableStateOf("1") }
    var isOptional       by remember { mutableStateOf(false) }
    var productErr       by remember { mutableStateOf(false) }

    LaunchedEffect(selectedProduct) {
        selectedSizeId = null; selectedSizeName = null
        val p = selectedProduct
        if (p != null) vm.loadSizesForItem(p.productId) else vm.clearSizesForItem()
    }

    AlertDialog(
        onDismissRequest = { vm.clearSizesForItem(); onDismiss() },
        title = { Text("Add Item to Deal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedTextField(
                        value = selectedProduct?.productName ?: "",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Product *") }, isError = productErr,
                        trailingIcon = {
                            IconButton(onClick = { productExpanded = !productExpanded }) {
                                Icon(if (productExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = productExpanded,
                        onDismissRequest = { productExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        products.forEach { p ->
                            DropdownMenuItem(text = { Text(p.productName) }, onClick = {
                                selectedProduct = p; productExpanded = false; productErr = false
                            })
                        }
                    }
                }

                if (sizes.isNotEmpty()) {
                    Box {
                        OutlinedTextField(
                            value = selectedSizeName ?: "Any size",
                            onValueChange = {}, readOnly = true, label = { Text("Size") },
                            trailingIcon = {
                                IconButton(onClick = { sizeExpanded = !sizeExpanded }) {
                                    Icon(if (sizeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = sizeExpanded,
                            onDismissRequest = { sizeExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DropdownMenuItem(text = { Text("Any size") }, onClick = {
                                selectedSizeId = null; selectedSizeName = null; sizeExpanded = false
                            })
                            sizes.forEach { s ->
                                DropdownMenuItem(text = { Text(s.sizeName) }, onClick = {
                                    selectedSizeId = s.sizeId; selectedSizeName = s.sizeName; sizeExpanded = false
                                })
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = quantity, onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Optional item", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isOptional, onCheckedChange = { isOptional = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = selectedProduct
                if (p == null) { productErr = true; return@Button }
                val qty = quantity.toIntOrNull()?.coerceAtLeast(1) ?: 1
                vm.clearSizesForItem()
                onAdd(DealItem(
                    productId   = p.productId,
                    productName = p.productName,
                    sizeId      = selectedSizeId,
                    sizeName    = selectedSizeName,
                    quantity    = qty,
                    isOptional  = isOptional
                ))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = { vm.clearSizesForItem(); onDismiss() }) { Text("Cancel") } }
    )
}

@Composable
private fun DealDatePickerDialog(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}
