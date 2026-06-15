@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.recipes

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.RawMaterial
import com.fastpos.android.data.models.RecipeHeader
import com.fastpos.android.data.models.RecipeItem
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.RecipeViewModel

@Composable
fun RecipeScreen(
    onNavigateBack: () -> Unit,
    vm: RecipeViewModel = hiltViewModel()
) {
    val recipes        by vm.recipes.collectAsState()
    val selectedRecipe by vm.selectedRecipe.collectAsState()
    val recipeItems    by vm.recipeItems.collectAsState()
    val recipeCost     by vm.recipeCost.collectAsState()
    val salePrice      by vm.salePrice.collectAsState()
    val products       by vm.products.collectAsState()
    val productSizes   by vm.productSizes.collectAsState()
    val rawMaterials   by vm.rawMaterials.collectAsState()
    val isLoading      by vm.isLoading.collectAsState()
    val message        by vm.message.collectAsState()
    val snack          = remember { SnackbarHostState() }

    var showAddRecipe  by remember { mutableStateOf(false) }
    var deleteRecipe   by remember { mutableStateOf<RecipeHeader?>(null) }
    var editItem       by remember { mutableStateOf<RecipeItem?>(null) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(selectedRecipe?.recipeName?.ifBlank { selectedRecipe?.displayName } ?: "Recipes")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedRecipe != null) vm.clearSelection() else onNavigateBack()
                    }) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            if (selectedRecipe == null) {
                FloatingActionButton(onClick = { showAddRecipe = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "Add Recipe")
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (selectedRecipe == null) {
                RecipesPane(
                    recipes  = recipes,
                    onSelect = { vm.selectRecipe(it) },
                    onDelete = { deleteRecipe = it }
                )
            } else {
                RecipeItemsPane(
                    recipe       = selectedRecipe!!,
                    items        = recipeItems,
                    cost         = recipeCost,
                    salePrice    = salePrice,
                    rawMaterials = rawMaterials,
                    onAddIngredient    = { matId, qty -> vm.addIngredient(matId, qty) },
                    onEditItem         = { editItem = it },
                    onDeleteItem       = { vm.deleteIngredient(it.recipeId) }
                )
            }
        }
    }

    // Add Recipe dialog
    if (showAddRecipe) {
        AddRecipeDialog(
            products     = products,
            productSizes = productSizes,
            onLoadSizes  = { pid -> vm.loadSizesForProduct(pid) },
            onClearSizes = vm::clearProductSizes,
            onConfirm    = { productId, sizeId, name ->
                vm.addRecipe(productId, sizeId, name)
                showAddRecipe = false
            },
            onDismiss    = { showAddRecipe = false; vm.clearProductSizes() }
        )
    }

    // Edit ingredient quantity dialog
    editItem?.let { item ->
        EditIngredientDialog(
            item      = item,
            onConfirm = { qty -> vm.updateIngredient(item.recipeId, item.materialId, qty); editItem = null },
            onDismiss = { editItem = null }
        )
    }

    // Delete recipe confirmation
    deleteRecipe?.let { r ->
        AlertDialog(
            onDismissRequest = { deleteRecipe = null },
            icon  = { Icon(Icons.Default.Warning, null, tint = RedError) },
            title = { Text("Delete Recipe?") },
            text  = { Text("Delete '${r.recipeName.ifBlank { r.displayName }}' and all its ingredients?") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteRecipe(r.recipeId); deleteRecipe = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = RedError)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteRecipe = null }) { Text("Cancel") } }
        )
    }
}

// ── Level 1: Recipe list ──────────────────────────────────────────────────────

@Composable
private fun RecipesPane(
    recipes:  List<RecipeHeader>,
    onSelect: (RecipeHeader) -> Unit,
    onDelete: (RecipeHeader) -> Unit
) {
    if (recipes.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.MenuBook, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No recipes yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap + to add a recipe", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(recipes, key = { it.recipeId }) { r ->
            RecipeHeaderCard(recipe = r, onSelect = { onSelect(r) }, onDelete = { onDelete(r) })
        }
    }
}

@Composable
private fun RecipeHeaderCard(
    recipe:   RecipeHeader,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onSelect,
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.MenuBook, null, Modifier.size(36.dp), tint = AmberWarning)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        recipe.recipeName.ifBlank { recipe.displayName },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (recipe.recipeName.isNotBlank()) {
                        Text(
                            recipe.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${recipe.itemCount} ingredient(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (recipe.itemCount > 0) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, tint = RedError)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Level 2: Ingredients for a recipe ────────────────────────────────────────

@Composable
private fun RecipeItemsPane(
    recipe:          RecipeHeader,
    items:           List<RecipeItem>,
    cost:            Double,
    salePrice:       Double,
    rawMaterials:    List<RawMaterial>,
    onAddIngredient: (Int, Double) -> Unit,
    onEditItem:      (RecipeItem) -> Unit,
    onDeleteItem:    (RecipeItem) -> Unit
) {
    var addMaterial by remember { mutableStateOf<RawMaterial?>(null) }
    var addQtyText  by remember { mutableStateOf("") }
    var addExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))

        // Recipe header summary card
        Card(
            colors   = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.10f)),
            border   = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    recipe.recipeName.ifBlank { recipe.displayName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (recipe.recipeName.isNotBlank()) {
                    Text(recipe.displayName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (cost > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recipe Cost / serving", style = MaterialTheme.typography.labelSmall,
                            color = GreenSuccess)
                        Text(cost.formatCurrency(""), style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold, color = GreenSuccess)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (items.isEmpty()) "No ingredients yet — add one below" else "${items.size} ingredient(s)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items, key = { it.recipeId }) { item ->
                RecipeItemCard(item = item, onEdit = { onEditItem(item) }, onDelete = { onDeleteItem(item) })
            }
        }

        // ── Costing summary (mirrors WPF HasCosting card) ────────────────
        if (salePrice > 0) {
            val margin = ((salePrice - cost) / salePrice * 100)
            val marginColor = when {
                margin >= 50 -> GreenSuccess
                margin >= 25 -> AmberWarning
                else         -> RedError
            }
            Spacer(Modifier.height(8.dp))
            Card(
                colors   = CardDefaults.cardColors(containerColor = GreenSuccess.copy(alpha = 0.08f)),
                border   = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.30f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Ingredient Cost",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(cost.formatCurrency(""),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sale Price",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(salePrice.formatCurrency(""),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider(color = GreenSuccess.copy(alpha = 0.30f))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Gross Margin",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold)
                        Text("${"%.1f".format(margin)}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = marginColor)
                    }
                }
            }
        }

        // ── Inline Add Ingredient form ────────────────────────────────────
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Add Ingredient", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.Bottom
        ) {
            ExposedDropdownMenuBox(
                expanded         = addExpanded,
                onExpandedChange = { addExpanded = it },
                modifier         = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value         = addMaterial?.materialName ?: "Select material…",
                    onValueChange = {},
                    readOnly      = true,
                    label         = { Text("Raw Material") },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(addExpanded) },
                    modifier      = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = addExpanded, onDismissRequest = { addExpanded = false }) {
                    if (rawMaterials.isEmpty()) {
                        DropdownMenuItem(
                            text    = { Text("No raw materials defined", style = MaterialTheme.typography.bodySmall) },
                            onClick = { addExpanded = false },
                            enabled = false
                        )
                    } else {
                        rawMaterials.forEach { m ->
                            DropdownMenuItem(
                                text    = { Text("${m.materialName} (${m.unit})") },
                                onClick = { addMaterial = m; addExpanded = false }
                            )
                        }
                    }
                }
            }
            OutlinedTextField(
                value           = addQtyText,
                onValueChange   = { addQtyText = it },
                label           = { Text("Qty") },
                suffix          = { Text(addMaterial?.unit ?: "") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier        = Modifier.width(100.dp),
                singleLine      = true
            )
            Button(
                onClick = {
                    val mat = addMaterial ?: return@Button
                    val qty = addQtyText.toDoubleOrNull()?.takeIf { it > 0 } ?: return@Button
                    onAddIngredient(mat.materialId, qty)
                    addMaterial = null
                    addQtyText  = ""
                },
                enabled  = addMaterial != null && addQtyText.toDoubleOrNull()?.let { it > 0 } == true,
                modifier = Modifier.height(56.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Icon(Icons.Default.Add, "Add") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RecipeItemCard(item: RecipeItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Inventory2, null, Modifier.size(28.dp), tint = AmberWarning)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.materialName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${item.quantityRequired} ${item.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(item.lineCost.formatCurrency(""), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold, color = GreenSuccess)
                Text("@ ${item.costPerUnit.formatCurrency("")}/${item.unit}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row {
                IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit,   null, Modifier.size(18.dp), tint = AmberWarning) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = RedError) }
            }
        }
    }
}

// ── Add Recipe dialog ─────────────────────────────────────────────────────────

@Composable
private fun AddRecipeDialog(
    products:     List<Triple<Int, String, String>>,
    productSizes: List<com.fastpos.android.data.models.ProductSize>,
    onLoadSizes:  (Int) -> Unit,
    onClearSizes: () -> Unit,
    onConfirm:    (productId: Int, sizeId: Int?, recipeName: String) -> Unit,
    onDismiss:    () -> Unit
) {
    var productExpanded by remember { mutableStateOf(false) }
    var sizeExpanded    by remember { mutableStateOf(false) }
    var selectedProduct by remember { mutableStateOf<Triple<Int, String, String>?>(null) }
    var selectedSizeId  by remember { mutableStateOf<Int?>(null) }
    var recipeName      by remember { mutableStateOf("") }

    val isPizza = selectedProduct?.third == "Pizza"

    // Auto-fill recipe name per WPF convention when product/size changes
    LaunchedEffect(selectedProduct, selectedSizeId) {
        val pName = selectedProduct?.second ?: return@LaunchedEffect
        val sName = productSizes.find { it.sizeId == selectedSizeId }?.sizeName
        recipeName = if (sName != null) "$pName ($sName) Recipe" else "$pName Recipe"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.MenuBook, null, tint = AmberWarning) },
        title = { Text("Add Recipe") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Product picker
                ExposedDropdownMenuBox(expanded = productExpanded, onExpandedChange = { productExpanded = it }) {
                    OutlinedTextField(
                        value         = selectedProduct?.second ?: "Select product…",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Product *") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(productExpanded) },
                        modifier      = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = productExpanded, onDismissRequest = { productExpanded = false }) {
                        products.forEach { p ->
                            DropdownMenuItem(
                                text    = { Text(if (p.third == "Pizza") "${p.second} (Pizza)" else p.second) },
                                onClick = {
                                    selectedProduct = p
                                    selectedSizeId  = null
                                    productExpanded = false
                                    if (p.third == "Pizza") onLoadSizes(p.first) else onClearSizes()
                                }
                            )
                        }
                    }
                }

                // Size picker — only for pizza
                if (isPizza) {
                    ExposedDropdownMenuBox(expanded = sizeExpanded, onExpandedChange = { sizeExpanded = it }) {
                        OutlinedTextField(
                            value         = productSizes.find { it.sizeId == selectedSizeId }?.sizeName ?: "Select size…",
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Pizza Size *") },
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(sizeExpanded) },
                            modifier      = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                            productSizes.forEach { s ->
                                DropdownMenuItem(
                                    text    = { Text(s.sizeName) },
                                    onClick = { selectedSizeId = s.sizeId; sizeExpanded = false }
                                )
                            }
                        }
                    }
                }

                // Recipe name (pre-filled, editable)
                OutlinedTextField(
                    value         = recipeName,
                    onValueChange = { recipeName = it },
                    label         = { Text("Recipe Name") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = selectedProduct ?: return@Button
                    if (isPizza && selectedSizeId == null) return@Button
                    onConfirm(p.first, if (isPizza) selectedSizeId else null, recipeName)
                },
                enabled = selectedProduct != null && (!isPizza || selectedSizeId != null)
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Edit ingredient quantity dialog ───────────────────────────────────────────

@Composable
private fun EditIngredientDialog(
    item:      RecipeItem,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var qtyText by remember { mutableStateOf(item.quantityRequired.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Inventory2, null, tint = AmberWarning) },
        title = { Text("Edit Ingredient") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${item.materialName} (${item.unit})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value           = qtyText,
                    onValueChange   = { qtyText = it },
                    label           = { Text("Quantity (${item.unit})") },
                    singleLine      = true,
                    modifier        = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { qtyText.toDoubleOrNull()?.let { onConfirm(it) } },
                enabled  = qtyText.toDoubleOrNull()?.let { it > 0 } == true
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
