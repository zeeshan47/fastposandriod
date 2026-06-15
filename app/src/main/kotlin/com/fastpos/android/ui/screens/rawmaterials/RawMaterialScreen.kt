@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.rawmaterials

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.*
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.RawMaterialViewModel
import com.fastpos.android.viewmodels.StockTakeViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val UNITS = listOf("kg", "g", "liters", "ml", "pieces", "packs", "dozen")

@Composable
fun RawMaterialScreen(
    onNavigateBack: () -> Unit,
    vm: RawMaterialViewModel = hiltViewModel(),
    stockTakeVm: StockTakeViewModel = hiltViewModel()
) {
    val materials       by vm.materials.collectAsState()
    val search          by vm.search.collectAsState()
    val isLoading       by vm.isLoading.collectAsState()
    val message         by vm.message.collectAsState()
    val products        by vm.products.collectAsState()
    val selectedProduct by vm.selectedProduct.collectAsState()
    val recipe          by vm.recipe.collectAsState()
    val recipeCost      by vm.recipeCost.collectAsState()
    val settings        by vm.session.settings.collectAsState()
    // ledger
    val ledgerMaterial  by vm.ledgerMaterial.collectAsState()
    val ledgerFromDate  by vm.ledgerFromDate.collectAsState()
    val ledgerToDate    by vm.ledgerToDate.collectAsState()
    val ledgerRows      by vm.ledgerRows.collectAsState()
    val ledgerOpening   by vm.ledgerOpening.collectAsState()
    val ledgerTotalIn   by vm.ledgerTotalIn.collectAsState()
    val ledgerTotalOut  by vm.ledgerTotalOut.collectAsState()
    val ledgerClosing   by vm.ledgerClosing.collectAsState()
    // waste
    val wasteEntries    by vm.wasteEntries.collectAsState()
    val wasteItems      by vm.wasteItems.collectAsState()
    val wasteSelected   by vm.wasteSelected.collectAsState()
    val wasteFilterFrom by vm.wasteFilterFrom.collectAsState()
    val wasteFilterTo   by vm.wasteFilterTo.collectAsState()
    val wasteFormItems  by vm.wasteFormItems.collectAsState()
    val wasteFormDate   by vm.wasteFormDate.collectAsState()
    val wasteFormNotes  by vm.wasteFormNotes.collectAsState()

    val snack           = remember { SnackbarHostState() }
    var tabIndex        by remember { mutableIntStateOf(0) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Raw Materials") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = { vm.loadMaterials() }) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 },
                    text = { Text("Materials") }, icon = { Icon(Icons.Default.Inventory2, null, Modifier.size(18.dp)) })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 },
                    text = { Text("Recipes") }, icon = { Icon(Icons.Default.MenuBook, null, Modifier.size(18.dp)) })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 },
                    text = { Text("Ledger") }, icon = { Icon(Icons.Default.History, null, Modifier.size(18.dp)) })
                Tab(selected = tabIndex == 3, onClick = { tabIndex = 3; vm.loadWasteEntries() },
                    text = { Text("Wastage") }, icon = { Icon(Icons.Default.DeleteSweep, null, Modifier.size(18.dp)) })
                Tab(selected = tabIndex == 4, onClick = { tabIndex = 4; stockTakeVm.load() },
                    text = { Text("Stock Take") }, icon = { Icon(Icons.Default.Checklist, null, Modifier.size(18.dp)) })
                Tab(selected = tabIndex == 5, onClick = { tabIndex = 5; vm.loadStockValuation() },
                    text = { Text("Valuation") }, icon = { Icon(Icons.Default.Assessment, null, Modifier.size(18.dp)) })
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            when (tabIndex) {
                0 -> MaterialsTab(
                    materials = materials, search = search, currencySymbol = settings.currencySymbol,
                    onSearch = vm::setSearch,
                    onAdd    = { n, u, s, min, c -> vm.addMaterial(n, u, s, min, c) },
                    onEdit   = { id, n, u, min, c -> vm.updateMaterial(id, n, u, min, c) },
                    onAdjust = { id, delta, rate, remarks -> vm.adjustStock(id, delta, rate, remarks) },
                    onSet    = { id, s -> vm.setStock(id, s) },
                    onDelete = { vm.deleteMaterial(it) }
                )
                1 -> RecipesTab(
                    products        = products,
                    selectedProduct = selectedProduct,
                    recipe          = recipe,
                    recipeCost      = recipeCost,
                    materials       = materials,
                    currencySymbol  = settings.currencySymbol,
                    onSelectProduct = vm::selectProduct,
                    onUpsert        = { matId, qty -> vm.upsertRecipeItem(matId, qty) },
                    onDelete        = { vm.deleteRecipeItem(it) }
                )
                2 -> LedgerTab(
                    materials      = materials,
                    selectedMat    = ledgerMaterial,
                    fromDate       = ledgerFromDate,
                    toDate         = ledgerToDate,
                    rows           = ledgerRows,
                    opening        = ledgerOpening,
                    totalIn        = ledgerTotalIn,
                    totalOut       = ledgerTotalOut,
                    closing        = ledgerClosing,
                    onSelectMat    = vm::setLedgerMaterial,
                    onFromDate     = vm::setLedgerFromDate,
                    onToDate       = vm::setLedgerToDate,
                    onRun          = { vm.runLedger() }
                )
                3 -> WastageTab(
                    materials      = materials,
                    entries        = wasteEntries,
                    selectedEntry  = wasteSelected,
                    entryItems     = wasteItems,
                    filterFrom     = wasteFilterFrom,
                    filterTo       = wasteFilterTo,
                    formItems      = wasteFormItems,
                    formDate       = wasteFormDate,
                    formNotes      = wasteFormNotes,
                    currencySymbol = settings.currencySymbol,
                    onFilterFrom   = vm::setWasteFilterFrom,
                    onFilterTo     = vm::setWasteFilterTo,
                    onApplyFilter  = { vm.loadWasteEntries() },
                    onSelectEntry  = vm::selectWasteEntry,
                    onFormDate     = vm::setWasteFormDate,
                    onFormNotes    = vm::setWasteFormNotes,
                    onAddFormItem  = vm::addWasteFormItem,
                    onRemoveFormItem = vm::removeWasteFormItem,
                    onSave         = { vm.saveWasteEntry() },
                    onClearForm    = vm::clearWasteForm,
                    onDelete       = { vm.deleteWasteEntry(it) }
                )
                4 -> StockTakeTab(stockTakeVm = stockTakeVm)
                5 -> StockValuationTab(vm = vm)
            }
        }
    }
}

// ── Materials Tab ─────────────────────────────────────────────────────────────

@Composable
private fun MaterialsTab(
    materials: List<RawMaterial>,
    search: String,
    currencySymbol: String,
    onSearch: (String) -> Unit,
    onAdd:    (String, String, Double, Double, Double) -> Unit,
    onEdit:   (Int, String, String, Double, Double) -> Unit,
    onAdjust: (Int, Double, Double, String) -> Unit,
    onSet:    (Int, Double) -> Unit,
    onDelete: (Int) -> Unit
) {
    var showAdd      by remember { mutableStateOf(false) }
    var editTarget   by remember { mutableStateOf<RawMaterial?>(null) }
    var adjustTarget by remember { mutableStateOf<RawMaterial?>(null) }
    var deleteTarget by remember { mutableStateOf<RawMaterial?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = search, onValueChange = onSearch,
                placeholder = { Text("Search materials…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Clear, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true
            )

            if (materials.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory2, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No raw materials yet. Tap + to add.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(materials, key = { it.materialId }) { mat ->
                        MaterialCard(
                            material       = mat,
                            currencySymbol = currencySymbol,
                            onEdit         = { editTarget = mat },
                            onAdjust       = { adjustTarget = mat },
                            onDelete       = { deleteTarget = mat }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.Add, "Add Material") }
    }

    if (showAdd) {
        MaterialDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onConfirm = { n, u, s, min, c -> onAdd(n, u, s, min, c); showAdd = false }
        )
    }
    editTarget?.let { mat ->
        MaterialDialog(
            initial = mat,
            onDismiss = { editTarget = null },
            onConfirm = { n, u, _, min, c -> onEdit(mat.materialId, n, u, min, c); editTarget = null }
        )
    }
    adjustTarget?.let { mat ->
        StockAdjustDialog(
            material = mat,
            onDismiss = { adjustTarget = null },
            onAdjust = { delta, rate, remarks -> onAdjust(mat.materialId, delta, rate, remarks); adjustTarget = null },
            onSet    = { s -> onSet(mat.materialId, s); adjustTarget = null }
        )
    }
    deleteTarget?.let { mat ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Delete, null, tint = RedError) },
            title = { Text("Delete Material?") },
            text  = { Text("'${mat.materialName}' will be deactivated. Existing recipes using it will be unaffected.") },
            confirmButton = {
                Button(onClick = { onDelete(mat.materialId); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MaterialCard(
    material: RawMaterial,
    currencySymbol: String,
    onEdit:   () -> Unit,
    onAdjust: () -> Unit,
    onDelete: () -> Unit
) {
    val stockColor = when {
        material.currentStock <= 0                           -> RedError
        material.currentStock <= material.minStockLevel      -> AmberWarning
        else                                                 -> GreenSuccess
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = stockColor.copy(alpha = if (material.currentStock <= material.minStockLevel) 0.06f else 0.0f)
                .let { if (material.currentStock <= material.minStockLevel) it else MaterialTheme.colorScheme.surfaceVariant }
        ),
        border = if (material.currentStock <= material.minStockLevel)
            androidx.compose.foundation.BorderStroke(1.dp, stockColor.copy(alpha = 0.35f)) else null
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(material.materialName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StockBadge("${"%g".format(material.currentStock)} ${material.unit}", stockColor)
                    if (material.minStockLevel > 0)
                        Text("Min: ${"%g".format(material.minStockLevel)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (material.costPerUnit > 0)
                    Text("${material.costPerUnit.formatCurrency(currencySymbol)} / ${material.unit}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                IconButton(onClick = onAdjust) { Icon(Icons.Default.MoveToInbox, null, tint = GreenSuccess) }
                IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit, null, tint = AmberWarning) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun StockBadge(label: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
        Text(label, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun MaterialDialog(
    initial: RawMaterial?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, Double, Double) -> Unit
) {
    var name        by remember { mutableStateOf(initial?.materialName ?: "") }
    var unitExpanded by remember { mutableStateOf(false) }
    var unit        by remember { mutableStateOf(initial?.unit ?: "kg") }
    var stockText   by remember { mutableStateOf(initial?.currentStock?.toString() ?: "0") }
    var minText     by remember { mutableStateOf(initial?.minStockLevel?.toString() ?: "0") }
    var costText    by remember { mutableStateOf(initial?.costPerUnit?.toString() ?: "0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (initial == null) "Add Raw Material" else "Edit Material") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Material Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // Unit dropdown
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = unit, onValueChange = {}, readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { IconButton(onClick = { unitExpanded = true }) { Icon(Icons.Default.ExpandMore, null) } },
                        modifier = Modifier.fillMaxWidth())
                    DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        UNITS.forEach { u ->
                            DropdownMenuItem(text = { Text(u) }, onClick = { unit = u; unitExpanded = false })
                        }
                    }
                }
                if (initial == null) {
                    OutlinedTextField(value = stockText, onValueChange = { stockText = it },
                        label = { Text("Initial Stock") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(value = minText, onValueChange = { minText = it },
                    label = { Text("Min Stock Level (alert threshold)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = costText, onValueChange = { costText = it },
                    label = { Text("Cost per $unit") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank())
                        onConfirm(name, unit,
                            stockText.toDoubleOrNull() ?: 0.0,
                            minText.toDoubleOrNull() ?: 0.0,
                            costText.toDoubleOrNull() ?: 0.0)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StockAdjustDialog(
    material: RawMaterial,
    onDismiss: () -> Unit,
    onAdjust: (delta: Double, rate: Double, remarks: String) -> Unit,
    onSet: (Double) -> Unit
) {
    var mode      by remember { mutableIntStateOf(0) }  // 0=Add, 1=Remove, 2=Set
    var valueText by remember { mutableStateOf("") }
    var rateText  by remember { mutableStateOf("") }
    var remarks   by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.MoveToInbox, null, tint = GreenSuccess) },
        title = { Text("Adjust Stock — ${material.materialName}") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current: ${"%g".format(material.currentStock)} ${material.unit}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Add", "Remove", "Set").forEachIndexed { i, label ->
                        FilterChip(selected = mode == i, onClick = { mode = i },
                            label = { Text(label) },
                            leadingIcon = if (mode == i) { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null)
                    }
                }
                OutlinedTextField(
                    value = valueText, onValueChange = { valueText = it },
                    label = { Text("Quantity (${material.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (mode != 2) {
                    OutlinedTextField(
                        value = rateText, onValueChange = { rateText = it },
                        label = { Text("Rate (for costing, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = remarks, onValueChange = { remarks = it },
                        label = { Text("Remarks (optional)") },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val v = valueText.toDoubleOrNull() ?: return@Button
                    val r = rateText.toDoubleOrNull() ?: 0.0
                    when (mode) {
                        0 -> onAdjust(v, r, remarks)
                        1 -> onAdjust(-v, r, remarks)
                        2 -> onSet(v)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Recipes Tab ───────────────────────────────────────────────────────────────

@Composable
private fun RecipesTab(
    products:        List<Triple<Int, String, String>>,
    selectedProduct: Triple<Int, String, String>?,
    recipe:          List<RecipeItem>,
    recipeCost:      Double,
    materials:       List<RawMaterial>,
    currencySymbol:  String,
    onSelectProduct: (Triple<Int, String, String>?) -> Unit,
    onUpsert:        (Int, Double) -> Unit,
    onDelete:        (Int) -> Unit
) {
    var productExpanded by remember { mutableStateOf(false) }
    var showAddItem     by remember { mutableStateOf(false) }
    var editItem        by remember { mutableStateOf<RecipeItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Product selector
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = selectedProduct?.second ?: "Select a product…",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Product") },
                    leadingIcon = { Icon(Icons.Default.Fastfood, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = { IconButton(onClick = { productExpanded = true }) { Icon(Icons.Default.ExpandMore, null) } },
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = productExpanded,
                    onDismissRequest = { productExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    products.forEach { prod ->
                        DropdownMenuItem(text = { Text(prod.second) },
                            onClick = { onSelectProduct(prod); productExpanded = false })
                    }
                }
            }
        }

        if (selectedProduct == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a product to view or edit its recipe.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        // Cost summary
        if (recipe.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = CardDefaults.cardColors(containerColor = GreenSuccess.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.3f))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Calculate, null, tint = GreenSuccess, modifier = Modifier.size(18.dp))
                        Text("Cost per serving", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    Text(recipeCost.formatCurrency(currencySymbol),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GreenSuccess)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.weight(1f)) {
            if (recipe.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MenuBook, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No recipe defined yet. Tap + to add ingredients.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(recipe, key = { it.recipeId }) { item ->
                        RecipeItemCard(item = item, currencySymbol = currencySymbol,
                            onEdit = { editItem = item }, onDelete = { onDelete(item.recipeId) })
                    }
                }
            }

            FloatingActionButton(
                onClick = { showAddItem = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, "Add Ingredient") }
        }
    }

    if (showAddItem || editItem != null) {
        RecipeItemDialog(
            initial   = editItem,
            materials = materials,
            onDismiss = { showAddItem = false; editItem = null },
            onConfirm = { matId, qty -> onUpsert(matId, qty); showAddItem = false; editItem = null }
        )
    }
}

@Composable
private fun RecipeItemCard(
    item: RecipeItem,
    currencySymbol: String,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.materialName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${"%g".format(item.quantityRequired)} ${item.unit}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.lineCost.formatCurrency(currencySymbol), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            Row {
                IconButton(onClick = onEdit,   modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Edit, null, tint = AmberWarning) }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun RecipeItemDialog(
    initial:   RecipeItem?,
    materials: List<RawMaterial>,
    onDismiss: () -> Unit,
    onConfirm: (Int, Double) -> Unit
) {
    var matExpanded by remember { mutableStateOf(false) }
    var selectedMat by remember {
        mutableStateOf(initial?.let { item -> materials.find { it.materialId == item.materialId } })
    }
    var qtyText by remember { mutableStateOf(initial?.quantityRequired?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (initial == null) "Add Ingredient" else "Edit Ingredient") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedMat?.materialName ?: "Select material…",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Raw Material") },
                        trailingIcon = { IconButton(onClick = { matExpanded = true }) { Icon(Icons.Default.ExpandMore, null) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = matExpanded, onDismissRequest = { matExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.85f)) {
                        materials.forEach { mat ->
                            DropdownMenuItem(
                                text = { Text("${mat.materialName} (${"%g".format(mat.currentStock)} ${mat.unit})") },
                                onClick = { selectedMat = mat; matExpanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = qtyText, onValueChange = { qtyText = it },
                    label = { Text("Quantity per serving${selectedMat?.let { " (${it.unit})" } ?: ""}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mat = selectedMat ?: return@Button
                    val qty = qtyText.toDoubleOrNull() ?: return@Button
                    if (qty > 0) onConfirm(mat.materialId, qty)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) { Text(if (initial == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Ledger Tab ────────────────────────────────────────────────────────────────

private val shortDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val numFmt2   = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 3 }

@Composable
private fun LedgerTab(
    materials:   List<RawMaterial>,
    selectedMat: RawMaterial?,
    fromDate:    java.util.Date,
    toDate:      java.util.Date,
    rows:        List<InventoryLedger>,
    opening:     Double,
    totalIn:     Double,
    totalOut:    Double,
    closing:     Double,
    onSelectMat: (RawMaterial?) -> Unit,
    onFromDate:  (java.util.Date) -> Unit,
    onToDate:    (java.util.Date) -> Unit,
    onRun:       () -> Unit
) {
    var matExpanded  by remember { mutableStateOf(false) }
    var showFromPick by remember { mutableStateOf(false) }
    var showToPick   by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = matExpanded, onExpandedChange = { matExpanded = it }) {
            OutlinedTextField(
                value = selectedMat?.materialName ?: "",
                onValueChange = {}, readOnly = true,
                label = { Text("Select Material") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(matExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = matExpanded, onDismissRequest = { matExpanded = false }) {
                materials.forEach { m ->
                    DropdownMenuItem(text = { Text(m.materialName) }, onClick = { onSelectMat(m); matExpanded = false })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = shortDate.format(fromDate), onValueChange = {}, readOnly = true,
                label = { Text("From") },
                trailingIcon = { IconButton(onClick = { showFromPick = true }) { Icon(Icons.Default.DateRange, null) } },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = shortDate.format(toDate), onValueChange = {}, readOnly = true,
                label = { Text("To") },
                trailingIcon = { IconButton(onClick = { showToPick = true }) { Icon(Icons.Default.DateRange, null) } },
                modifier = Modifier.weight(1f)
            )
        }

        Button(onClick = onRun, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            enabled = selectedMat != null) {
            Icon(Icons.Default.Search, null); Spacer(Modifier.width(8.dp)); Text("Run Ledger")
        }

        if (rows.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${selectedMat?.materialName} — ${shortDate.format(fromDate)} to ${shortDate.format(toDate)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Opening"); Text("${numFmt2.format(opening)} ${selectedMat?.unit ?: ""}")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total In", color = GreenSuccess); Text("${numFmt2.format(totalIn)} ${selectedMat?.unit ?: ""}", color = GreenSuccess)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Out", color = RedError); Text("${numFmt2.format(totalOut)} ${selectedMat?.unit ?: ""}", color = RedError)
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Closing", fontWeight = FontWeight.Bold)
                        Text("${numFmt2.format(closing)} ${selectedMat?.unit ?: ""}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                Text("Date",    Modifier.weight(0.85f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("Type",    Modifier.weight(0.9f),  style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("In",      Modifier.weight(0.65f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GreenSuccess)
                Text("Out",     Modifier.weight(0.65f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = RedError)
                Text("Bal",     Modifier.weight(0.75f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Remarks", Modifier.weight(1.2f),  style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(rows) { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(SimpleDateFormat("dd/MM", Locale.getDefault()).format(row.transDate),
                            Modifier.weight(0.85f), style = MaterialTheme.typography.bodySmall)
                        Text(row.refType,
                            Modifier.weight(0.9f),  style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (row.inQty  > 0) numFmt2.format(row.inQty)  else "-",
                            Modifier.weight(0.65f), style = MaterialTheme.typography.bodySmall, color = GreenSuccess)
                        Text(if (row.outQty > 0) numFmt2.format(row.outQty) else "-",
                            Modifier.weight(0.65f), style = MaterialTheme.typography.bodySmall, color = RedError)
                        Text(numFmt2.format(row.balanceQty),
                            Modifier.weight(0.75f), style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text(row.remarks,
                            Modifier.weight(1.2f),  style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                }
            }
        } else if (selectedMat != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions in selected range", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showFromPick) RmDatePickerDialog(fromDate, onDismiss = { showFromPick = false }, onConfirm = { onFromDate(it); showFromPick = false })
    if (showToPick)   RmDatePickerDialog(toDate,   onDismiss = { showToPick = false },   onConfirm = { onToDate(it);   showToPick = false })
}

// ── Wastage Tab ───────────────────────────────────────────────────────────────

@Composable
private fun WastageTab(
    materials:       List<RawMaterial>,
    entries:         List<WasteEntry>,
    selectedEntry:   WasteEntry?,
    entryItems:      List<WasteEntryItem>,
    filterFrom:      java.util.Date,
    filterTo:        java.util.Date,
    formItems:       List<WasteEntryItem>,
    formDate:        java.util.Date,
    formNotes:       String,
    currencySymbol:  String,
    onFilterFrom:    (java.util.Date) -> Unit,
    onFilterTo:      (java.util.Date) -> Unit,
    onApplyFilter:   () -> Unit,
    onSelectEntry:   (WasteEntry?) -> Unit,
    onFormDate:      (java.util.Date) -> Unit,
    onFormNotes:     (String) -> Unit,
    onAddFormItem:   (WasteEntryItem) -> Unit,
    onRemoveFormItem:(Int) -> Unit,
    onSave:          () -> Unit,
    onClearForm:     () -> Unit,
    onDelete:        (Int) -> Unit
) {
    var showAddItem  by remember { mutableStateOf(false) }
    var showFromPick by remember { mutableStateOf(false) }
    var showToPick   by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<WasteEntry?>(null) }
    var showFormDate by remember { mutableStateOf(false) }
    val formTotal = formItems.sumOf { it.quantity * it.rate }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(value = shortDate.format(filterFrom), onValueChange = {}, readOnly = true, label = { Text("From") },
                    trailingIcon = { IconButton(onClick = { showFromPick = true }) { Icon(Icons.Default.DateRange, null, Modifier.size(16.dp)) } },
                    modifier = Modifier.weight(1f))
                OutlinedTextField(value = shortDate.format(filterTo), onValueChange = {}, readOnly = true, label = { Text("To") },
                    trailingIcon = { IconButton(onClick = { showToPick = true }) { Icon(Icons.Default.DateRange, null, Modifier.size(16.dp)) } },
                    modifier = Modifier.weight(1f))
            }
            Button(onClick = onApplyFilter, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AmberWarning)) {
                Text("Filter", color = androidx.compose.ui.graphics.Color.White)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(entries) { e ->
                    val isSel = selectedEntry?.wasteId == e.wasteId
                    Card(onClick = { onSelectEntry(if (isSel) null else e) },
                        colors = CardDefaults.cardColors(containerColor = if (isSel) RedError.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, if (isSel) RedError.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))) {
                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(shortDate.format(e.wasteDate), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                IconButton(onClick = { deleteTarget = e }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(14.dp))
                                }
                            }
                            Text("${e.itemCount} items  $currencySymbol${numFmt2.format(e.totalAmount)}", style = MaterialTheme.typography.bodySmall, color = RedError)
                            if (e.notes.isNotBlank()) Text(e.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (selectedEntry != null && entryItems.isNotEmpty()) {
                HorizontalDivider()
                Text("Items", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                entryItems.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.itemName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${numFmt2.format(item.quantity)} ${item.unit}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        VerticalDivider()

        Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("New Entry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = shortDate.format(formDate), onValueChange = {}, readOnly = true, label = { Text("Date") },
                trailingIcon = { IconButton(onClick = { showFormDate = true }) { Icon(Icons.Default.DateRange, null) } },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = formNotes, onValueChange = onFormNotes, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Items", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAddItem = true }) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text("Add") }
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(formItems) { idx, item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.itemName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text("${numFmt2.format(item.quantity)} ${item.unit} x $currencySymbol${numFmt2.format(item.rate)}", style = MaterialTheme.typography.bodySmall)
                                if (item.reason.isNotBlank()) Text(item.reason, style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                            }
                            IconButton(onClick = { onRemoveFormItem(idx) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, null, tint = RedError, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            if (formItems.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Loss", fontWeight = FontWeight.Bold)
                    Text("$currencySymbol${numFmt2.format(formTotal)}", color = RedError, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = onClearForm, modifier = Modifier.weight(1f)) { Text("Clear") }
                    Button(onClick = onSave, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = RedError)) {
                        Icon(Icons.Default.Save, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Save")
                    }
                }
            }
        }
    }

    if (showAddItem) AddWasteItemDialog(materials = materials, onDismiss = { showAddItem = false }, onAdd = { onAddFormItem(it); showAddItem = false })
    if (showFromPick) RmDatePickerDialog(filterFrom, onDismiss = { showFromPick = false }, onConfirm = { onFilterFrom(it); showFromPick = false })
    if (showToPick)   RmDatePickerDialog(filterTo,   onDismiss = { showToPick = false },   onConfirm = { onFilterTo(it);   showToPick = false })
    if (showFormDate) RmDatePickerDialog(formDate,   onDismiss = { showFormDate = false },  onConfirm = { onFormDate(it);   showFormDate = false })

    deleteTarget?.let { e ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Waste Entry") },
            text  = { Text("Delete entry from ${shortDate.format(e.wasteDate)}? Stock will be restored.") },
            confirmButton = { TextButton(onClick = { onDelete(e.wasteId); deleteTarget = null }) { Text("Delete", color = RedError) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddWasteItemDialog(
    materials: List<RawMaterial>,
    onDismiss: () -> Unit,
    onAdd:     (WasteEntryItem) -> Unit
) {
    var matExpanded by remember { mutableStateOf(false) }
    var selectedMat by remember { mutableStateOf<RawMaterial?>(null) }
    var qty         by remember { mutableStateOf("") }
    var rate        by remember { mutableStateOf("") }
    var reason      by remember { mutableStateOf("") }

    LaunchedEffect(selectedMat) {
        if (selectedMat != null) rate = selectedMat!!.costPerUnit.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Waste Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = matExpanded, onExpandedChange = { matExpanded = it }) {
                    OutlinedTextField(value = selectedMat?.materialName ?: "", onValueChange = {}, readOnly = true,
                        label = { Text("Material *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(matExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor())
                    ExposedDropdownMenu(expanded = matExpanded, onDismissRequest = { matExpanded = false }) {
                        materials.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("${m.materialName}  (${numFmt2.format(m.currentStock)} ${m.unit})") },
                                onClick = { selectedMat = m; matExpanded = false })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it },
                        label = { Text("Qty${selectedMat?.let { " (${it.unit})" } ?: ""}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text("Rate/unit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = reason, onValueChange = { reason = it },
                    label = { Text("Reason (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val m = selectedMat ?: return@Button
                val q = qty.toDoubleOrNull() ?: return@Button
                val r = rate.toDoubleOrNull() ?: 0.0
                if (q <= 0) return@Button
                onAdd(WasteEntryItem(materialId = m.materialId, itemName = m.materialName, quantity = q, unit = m.unit, rate = r, reason = reason.trim()))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RmDatePickerDialog(
    initial:   java.util.Date,
    onDismiss: () -> Unit,
    onConfirm: (java.util.Date) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.time)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(java.util.Date(it)) }; onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}

// ── Stock Take Tab ────────────────────────────────────────────────────────────

@Composable
private fun StockTakeTab(stockTakeVm: StockTakeViewModel) {
    val isLoading   by stockTakeVm.isLoading.collectAsState()
    val message     by stockTakeVm.message.collectAsState()
    val openTake    by stockTakeVm.openTake.collectAsState()
    val takeItems   by stockTakeVm.takeItems.collectAsState()
    val history     by stockTakeVm.history.collectAsState()
    val isSaving    by stockTakeVm.isSaving.collectAsState()
    val actualInputs by stockTakeVm.actualInputs.collectAsState()

    val snack = remember { SnackbarHostState() }
    var showStart   by remember { mutableStateOf(false) }
    var showFinalize by remember { mutableStateOf(false) }
    var showCancel  by remember { mutableStateOf(false) }
    var startNotes  by remember { mutableStateOf("") }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); stockTakeVm.clearMessage() }
    }

    Box(Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (openTake != null) {
                    // ── Open stock take header ────────────────────────────────
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Checklist, null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Stock Take In Progress",
                                        style     = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color     = MaterialTheme.colorScheme.primary)
                                }
                                Text("${takeItems.size} materials to count. Enter the physical quantity you counted for each item.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { showCancel = true },
                                        modifier = Modifier.weight(1f),
                                        border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Cancel, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Cancel Take", color = MaterialTheme.colorScheme.error)
                                    }
                                    Button(
                                        onClick  = { showFinalize = true },
                                        modifier = Modifier.weight(1f),
                                        enabled  = !isSaving
                                    ) {
                                        if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        else {
                                            Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Finalize & Apply")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // ── Count items ───────────────────────────────────────────
                    items(takeItems, key = { it.itemId }) { item ->
                        val inputVal = actualInputs[item.itemId] ?: "0"
                        val qty      = inputVal.toDoubleOrNull() ?: 0.0
                        val variance = qty - item.expectedQty
                        val varColor = when {
                            variance > 0.001  -> GreenSuccess
                            variance < -0.001 -> MaterialTheme.colorScheme.error
                            else              -> MaterialTheme.colorScheme.onSurface
                        }
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.materialName,
                                        style      = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines   = 1)
                                    Text("Expected: ${"%.3f".format(item.expectedQty).trimEnd('0').trimEnd('.')} ${item.unit}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (kotlin.math.abs(variance) > 0.001) {
                                        val sign = if (variance > 0) "+" else ""
                                        Text("Variance: $sign${"%.3f".format(variance).trimEnd('0').trimEnd('.')} ${item.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = varColor,
                                            fontWeight = FontWeight.Medium)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                OutlinedTextField(
                                    value         = inputVal,
                                    onValueChange = { v ->
                                        stockTakeVm.setActualQty(item.itemId, v)
                                    },
                                    label         = { Text("Actual") },
                                    suffix        = { Text(item.unit, style = MaterialTheme.typography.bodySmall) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier      = Modifier.width(130.dp),
                                    singleLine    = true
                                )
                            }
                        }
                    }
                } else {
                    // ── No open take — show Start button + history ───────────
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Inventory2, null,
                                    tint     = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp))
                                Text("No Active Stock Take",
                                    style      = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text("A stock take captures current stock levels and lets you enter physical counts to reconcile variances.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = { showStart = true }, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Start New Stock Take")
                                }
                            }
                        }
                    }
                    if (history.isNotEmpty()) {
                        item {
                            Text("History", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier   = Modifier.padding(top = 4.dp))
                        }
                        items(history, key = { it.stockTakeId }) { h ->
                            val fmt = remember { java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault()) }
                            OutlinedCard(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(fmt.format(h.takeDate),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium)
                                        Text("${h.itemCount} materials",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (h.notes.isNotBlank()) Text(h.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1)
                                    }
                                    val chipColor = if (h.status == "Finalized") GreenSuccess else MaterialTheme.colorScheme.error
                                    SuggestionChip(
                                        onClick = {},
                                        label   = { Text(h.status, color = chipColor) },
                                        border  = BorderStroke(1.dp, chipColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        AppSnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    if (showStart) {
        AlertDialog(
            onDismissRequest = { showStart = false; startNotes = "" },
            title   = { Text("Start Stock Take") },
            text    = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will capture current stock levels for all active raw materials. You can then enter physical counts and finalize to apply adjustments.")
                    OutlinedTextField(
                        value         = startNotes,
                        onValueChange = { startNotes = it },
                        label         = { Text("Notes (optional)") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    stockTakeVm.startNewStockTake(startNotes)
                    showStart = false
                    startNotes = ""
                }) { Text("Start") }
            },
            dismissButton = { TextButton(onClick = { showStart = false; startNotes = "" }) { Text("Cancel") } }
        )
    }

    if (showFinalize) {
        AlertDialog(
            onDismissRequest = { showFinalize = false },
            icon    = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.primary) },
            title   = { Text("Finalize Stock Take?") },
            text    = { Text("This will update all raw material stock levels to the counted quantities. Stock variances will be recorded in the ledger. This cannot be undone.") },
            confirmButton = {
                Button(onClick = { stockTakeVm.finalizeStockTake(); showFinalize = false }) { Text("Finalize") }
            },
            dismissButton = { TextButton(onClick = { showFinalize = false }) { Text("Cancel") } }
        )
    }

    if (showCancel) {
        AlertDialog(
            onDismissRequest = { showCancel = false },
            icon    = { Icon(Icons.Default.Cancel, null, tint = MaterialTheme.colorScheme.error) },
            title   = { Text("Cancel Stock Take?") },
            text    = { Text("No changes will be made to stock levels. All entered counts will be discarded.") },
            confirmButton = {
                Button(
                    onClick = { stockTakeVm.cancelStockTake(); showCancel = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Cancel Take") }
            },
            dismissButton = { TextButton(onClick = { showCancel = false }) { Text("Keep") } }
        )
    }
}

// ─── Stock Valuation Tab ──────────────────────────────────────────────────────

@Composable
private fun StockValuationTab(vm: RawMaterialViewModel) {
    val valuationItems   by vm.valuationItems.collectAsState()
    val reorderItems     by vm.reorderItems.collectAsState()
    val valuationLoading by vm.valuationLoading.collectAsState()
    val settings         by vm.session.settings.collectAsState()
    val sym              = settings.currencySymbol

    val totalValue = valuationItems.sumOf { it.currentStock * it.costPerUnit }

    LaunchedEffect(Unit) { vm.loadStockValuation() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (valuationLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            // Summary card
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Inventory Value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${valuationItems.size} materials in stock",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "$sym ${"%,.2f".format(totalValue)}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Reorder alerts
            if (reorderItems.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.08f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, RedError.copy(alpha = 0.4f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Warning, null, tint = RedError, modifier = Modifier.size(18.dp))
                                Text("Reorder Required (${reorderItems.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold, color = RedError)
                            }
                            reorderItems.forEach { mat ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mat.materialName, style = MaterialTheme.typography.bodySmall)
                                    val need = mat.minStockLevel - mat.currentStock
                                    Text(
                                        "Stock: %.2f / Min: %.2f  →  Need: +%.2f ${mat.unit}".format(mat.currentStock, mat.minStockLevel, need),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RedError
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Valuation list header
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("MATERIAL", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                    Text("STOCK", Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text("COST/UNIT", Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text("VALUE", Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                HorizontalDivider()
            }

            if (valuationItems.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No materials with stock on hand.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(valuationItems) { mat ->
                    val value = mat.currentStock * mat.costPerUnit
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(mat.materialName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(mat.unit, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("%.2f".format(mat.currentStock), Modifier.width(70.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Text("%.2f".format(mat.costPerUnit), Modifier.width(80.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$sym ${"%.2f".format(value)}", Modifier.width(80.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            color = if (value > 0) GreenSuccess else MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
