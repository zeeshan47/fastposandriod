@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.fastpos.android.data.models.Category
import com.fastpos.android.data.models.ModifierGroup
import com.fastpos.android.data.models.Product
import com.fastpos.android.data.models.ProductSchedule
import com.fastpos.android.data.models.ProductSize
import com.fastpos.android.data.models.TaxRate
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.ProductManagementViewModel

@Composable
fun ProductManagementScreen(
    onNavigateBack: () -> Unit,
    vm: ProductManagementViewModel = hiltViewModel()
) {
    val products          by vm.products.collectAsState()
    val categories        by vm.categories.collectAsState()      // all (incl. inactive) — for tab display
    val activeCategories  by vm.activeCategories.collectAsState() // active only — for form dropdown
    val isLoading         by vm.isLoading.collectAsState()
    val message           by vm.message.collectAsState()
    val search            by vm.search.collectAsState()
    val categoryFilter    by vm.categoryFilter.collectAsState()
    val typeFilter        by vm.typeFilter.collectAsState()
    val statusFilter      by vm.statusFilter.collectAsState()
    val settings         by vm.session.settings.collectAsState()
    val selectedProduct       by vm.selectedProduct.collectAsState()
    val sizes                 by vm.sizes.collectAsState()
    val productModGroups      by vm.productModGroups.collectAsState()
    val allModGroups          by vm.allModGroups.collectAsState()
    val stockManagedProducts  by vm.stockManagedProducts.collectAsState()
    val sizesLoading          by vm.sizesLoading.collectAsState()
    val scheduleProduct  by vm.scheduleProduct.collectAsState()
    val schedules        by vm.schedules.collectAsState()
    val generatedCode      by vm.generatedCode.collectAsState()
    val taxes              by vm.taxes.collectAsState()
    val editProductSizes        by vm.editProductSizes.collectAsState()
    val editProductModGroupIds  by vm.editProductModGroupIds.collectAsState()
    val snack                   = remember { SnackbarHostState() }

    var selectedTab         by remember { mutableStateOf(0) }
    var showAddProduct      by remember { mutableStateOf(false) }
    var showAddCategory     by remember { mutableStateOf(false) }
    var editProduct         by remember { mutableStateOf<Product?>(null) }
    var editCategory        by remember { mutableStateOf<Category?>(null) }
    var deleteProductId     by remember { mutableStateOf<Int?>(null) }
    var deleteCategoryId    by remember { mutableStateOf<Int?>(null) }
    var showBulkPriceDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Product Management") },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                    actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, null) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Products") }, icon = { Icon(Icons.Default.Fastfood, null) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Categories") }, icon = { Icon(Icons.Default.Category, null) })
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snack) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (selectedTab == 0) showAddProduct = true else showAddCategory = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, "Add") }
        }
    ) { padding ->

        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        when (selectedTab) {
            0 -> ProductsTab(
                products         = products,
                categories       = activeCategories,
                search           = search,
                categoryFilter   = categoryFilter,
                typeFilter       = typeFilter,
                statusFilter     = statusFilter,
                currency         = settings.currencySymbol,
                onSearch         = vm::setSearch,
                onCategoryFilter = vm::setCategoryFilter,
                onTypeFilter     = vm::setTypeFilter,
                onStatusFilter   = vm::setStatusFilter,
                onEdit           = { editProduct = it },
                onToggle         = vm::toggleProductActive,
                onDelete         = { deleteProductId = it },
                onOpenSizes      = vm::openSizesSheet,
                onOpenSchedule   = vm::openScheduleSheet,
                onDuplicate      = vm::duplicateProduct,
                onBulkPrices     = { showBulkPriceDialog = true },
                padding          = padding
            )
            1 -> CategoriesTab(
                categories = categories,   // shows all including inactive
                onEdit     = { editCategory = it },
                onDelete   = { deleteCategoryId = it },
                padding    = padding
            )
        }
    }

    // Auto-generate code on open, clear on close
    LaunchedEffect(showAddProduct) {
        if (showAddProduct) vm.requestCodeSuggestion("") else vm.clearGeneratedCode()
    }

    // Add product dialog
    if (showAddProduct) {
        ProductDialog(
            title         = "Add Product",
            categories    = activeCategories,
            taxes         = taxes,
            currency      = settings.currencySymbol,
            allModGroups  = allModGroups,
            suggestedCode = generatedCode,
            onRequestCode = vm::requestCodeSuggestion,
            onConfirm     = { name, code, catId, price, costPrice, printer, type, stockManaged, recipeBased, dispOrder, otherName, taxId, unit, reorderLevel, _, pendingSizes, modGroupIds ->
                vm.addProduct(name, catId, price, type, code, printer, costPrice, stockManaged, recipeBased, dispOrder, otherName, taxId, unit, reorderLevel, pendingSizes = pendingSizes, modifierGroupIds = modGroupIds)
                showAddProduct = false
            },
            onDismiss     = { showAddProduct = false }
        )
    }

    // Edit product dialog
    editProduct?.let { p ->
        LaunchedEffect(p.productId) {
            if (p.productType == "Pizza") vm.loadSizesForEditDialog(p.productId)
            else vm.clearEditProductSizes()
            vm.loadModGroupsForEditDialog(p.productId)
        }
        ProductDialog(
            title                = "Edit Product",
            categories           = activeCategories,
            taxes                = taxes,
            currency             = settings.currencySymbol,
            allModGroups         = allModGroups,
            initialModGroupIds   = editProductModGroupIds,
            sizes                = if (p.productType == "Pizza") editProductSizes else emptyList(),
            onAddSize            = if (p.productType == "Pizza") { n, pr -> vm.addSizeFromEditDialog(p.productId, n, pr) } else null, // cost price not wired through onAddSize (inline form only)
            onDeleteSize         = if (p.productType == "Pizza") { sid -> vm.deleteSizeFromEditDialog(p.productId, sid) } else null,
            initialName          = p.productName,
            initialOtherName     = p.productNameOtherLanguage,
            initialCode          = p.productCode,
            initialCatId         = p.categoryId,
            initialPrice         = p.salePrice.toString(),
            initialCostPrice     = if (p.costPrice > 0) p.costPrice.toString() else "",
            initialPrinter       = p.printerName,
            initialType          = p.productType,
            initialStockManaged  = p.isStockManaged,
            initialRecipeBased   = p.isRecipeBased,
            initialDisplayOrder  = p.displayOrder.toString(),
            initialIsActive      = p.isActive,
            initialTaxId         = p.taxId,
            initialUnit          = p.unit,
            initialReorderLevel  = if (p.reorderLevel > 0) p.reorderLevel.toString() else "",
            onConfirm            = { name, code, catId, price, costPrice, printer, type, stockManaged, recipeBased, dispOrder, otherName, taxId, unit, reorderLevel, isActive, _, modGroupIds ->
                vm.updateProduct(p.productId, name, catId, price, code, printer, costPrice, type, stockManaged, recipeBased, dispOrder, otherName, taxId, unit, reorderLevel, isActive, modifierGroupIds = modGroupIds)
                editProduct = null
            },
            onDismiss            = { editProduct = null; vm.clearEditProductSizes(); vm.clearEditProductModGroupIds() }
        )
    }

    // Add category dialog
    if (showAddCategory) {
        CategoryDialog(
            onConfirm = { name, color, order, otherName, active -> vm.addCategory(name, color, order, otherName, active); showAddCategory = false },
            onDismiss = { showAddCategory = false }
        )
    }

    // Edit category dialog
    editCategory?.let { cat ->
        CategoryDialog(
            title                    = "Edit Category",
            initialName              = cat.categoryName,
            initialOtherLanguageName = cat.otherLanguageName,
            initialColor             = cat.colorCode,
            initialDisplayOrder      = cat.displayOrder.toString(),
            initialIsActive          = cat.isActive,
            onConfirm                = { name, color, order, otherName, active -> vm.updateCategory(cat.categoryId, name, color, order, otherName, active); editCategory = null },
            onDismiss                = { editCategory = null }
        )
    }

    // Delete product confirmation
    deleteProductId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteProductId = null },
            icon  = { Icon(Icons.Default.DeleteForever, null, tint = RedError) },
            title = { Text("Delete Product?") },
            text  = { Text("This will mark the product as inactive. It can be restored from the database.") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteProduct(id); deleteProductId = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = RedError)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteProductId = null }) { Text("Cancel") } }
        )
    }

    // Delete category confirmation
    deleteCategoryId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteCategoryId = null },
            icon  = { Icon(Icons.Default.DeleteForever, null, tint = RedError) },
            title = { Text("Delete Category?") },
            text  = { Text("The category will be deactivated. Products in this category must be moved first.") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteCategory(id); deleteCategoryId = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = RedError)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteCategoryId = null }) { Text("Cancel") } }
        )
    }

    // Bulk Price Update dialog
    if (showBulkPriceDialog) {
        BulkPriceUpdateDialog(
            categories   = activeCategories,
            categoryFilter = categoryFilter,
            onConfirm    = { catId, pct -> vm.bulkUpdatePrices(catId, pct); showBulkPriceDialog = false },
            onDismiss    = { showBulkPriceDialog = false }
        )
    }

    // Sizes & Modifier Groups sheet
    if (selectedProduct != null) {
        ModalBottomSheet(
            onDismissRequest = vm::closeSizesSheet,
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            SizesModifiersSheet(
                product               = selectedProduct!!,
                sizes                 = sizes,
                productModGroups      = productModGroups,
                allModGroups          = allModGroups,
                stockManagedProducts  = stockManagedProducts,
                isLoading             = sizesLoading,
                currency              = settings.currencySymbol,
                onAddSize             = vm::addSize,
                onUpdateSize          = vm::updateSize,
                onDeleteSize          = vm::deleteSize,
                onAddModGroup         = vm::addModifierGroup,
                onUpdateModGroup      = vm::updateModifierGroup,
                onUnlinkGroup         = vm::unlinkModifierGroup,
                onLinkGroup           = vm::linkExistingGroup,
                onAddModifier         = vm::addModifier,
                onUpdateModifier      = vm::updateModifier,
                onDeleteModifier      = vm::deleteModifier
            )
        }
    }

    // Schedule sheet
    if (scheduleProduct != null) {
        ModalBottomSheet(
            onDismissRequest = vm::closeScheduleSheet,
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            ScheduleSheet(
                product    = scheduleProduct!!,
                schedules  = schedules,
                onAdd      = vm::addSchedule,
                onUpdate   = vm::updateSchedule,
                onDelete   = vm::deleteSchedule
            )
        }
    }
}

private val PRODUCT_TYPES = listOf("Normal", "Pizza", "Deal", "RawMaterial")

@Composable
private fun ProductsTab(
    products:         List<Product>,
    categories:       List<Category>,
    search:           String,
    categoryFilter:   Int?,
    typeFilter:       String?,
    statusFilter:     Boolean?,
    currency:         String,
    onSearch:         (String) -> Unit,
    onCategoryFilter: (Int?) -> Unit,
    onTypeFilter:     (String?) -> Unit,
    onStatusFilter:   (Boolean?) -> Unit,
    onEdit:           (Product) -> Unit,
    onToggle:         (Product) -> Unit,
    onDelete:         (Int) -> Unit,
    onOpenSizes:      (Product) -> Unit,
    onOpenSchedule:   (Product) -> Unit,
    onDuplicate:      (Product) -> Unit,
    onBulkPrices:     () -> Unit = {},
    padding:          PaddingValues
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(
            value         = search,
            onValueChange = onSearch,
            label         = { Text("Search name or code…") },
            leadingIcon   = { Icon(Icons.Default.Search, null) },
            trailingIcon  = { if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Clear, null) } },
            modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine    = true
        )
        // Category filter chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(selected = categoryFilter == null, onClick = { onCategoryFilter(null) }, label = { Text("All") })
            categories.forEach { cat ->
                val catColor = runCatching { Color(android.graphics.Color.parseColor(cat.colorCode)) }.getOrElse { MaterialTheme.colorScheme.primary }
                FilterChip(
                    selected = categoryFilter == cat.categoryId,
                    onClick  = { onCategoryFilter(cat.categoryId) },
                    label    = { Text(cat.categoryName) },
                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = catColor.copy(alpha = 0.25f))
                )
            }
        }
        // Type filter chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(selected = typeFilter == null, onClick = { onTypeFilter(null) }, label = { Text("All Types") })
            PRODUCT_TYPES.forEach { t ->
                val tColor = when (t) {
                    "Pizza"       -> MaterialTheme.colorScheme.primary
                    "Deal"        -> GreenSuccess
                    "RawMaterial" -> RedError
                    else          -> TealAccent
                }
                FilterChip(
                    selected = typeFilter == t,
                    onClick  = { onTypeFilter(t) },
                    label    = { Text(t) },
                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = tColor.copy(alpha = 0.2f))
                )
            }
        }
        // Status filter + count row
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick  = { onStatusFilter(null) },
                    label    = { Text("All (${products.size})") }
                )
                FilterChip(
                    selected = statusFilter == true,
                    onClick  = { onStatusFilter(true) },
                    label    = { Text("Active") },
                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenSuccess.copy(alpha = 0.2f))
                )
                FilterChip(
                    selected = statusFilter == false,
                    onClick  = { onStatusFilter(false) },
                    label    = { Text("Inactive") },
                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = RedError.copy(alpha = 0.2f))
                )
            }
            TextButton(
                onClick = onBulkPrices,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.PriceChange, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Bulk Price Update", style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(products, key = { it.productId }) { product ->
                ProductRow(
                    product        = product,
                    currency       = currency,
                    onEdit         = { onEdit(product) },
                    onToggle       = { onToggle(product) },
                    onDelete       = { onDelete(product.productId) },
                    onOpenSizes    = { onOpenSizes(product) },
                    onOpenSchedule = { onOpenSchedule(product) },
                    onDuplicate    = { onDuplicate(product) }
                )
            }
        }
    }
}

@Composable
private fun ProductRow(
    product:        Product,
    currency:       String,
    onEdit:         () -> Unit,
    onToggle:       () -> Unit,
    onDelete:       () -> Unit,
    onOpenSizes:    () -> Unit,
    onOpenSchedule: () -> Unit,
    onDuplicate:    () -> Unit = {}
) {
    val catColor = runCatching { Color(android.graphics.Color.parseColor(product.categoryColor)) }.getOrElse { MaterialTheme.colorScheme.primary }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (product.isActive) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(Modifier.size(12.dp).background(catColor, CircleShape))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            product.productName,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color      = if (product.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (product.productType != "Normal") {
                            val typeColor = when (product.productType) {
                                "Pizza"       -> MaterialTheme.colorScheme.primary
                                "Deal"        -> GreenSuccess
                                "RawMaterial" -> RedError
                                else          -> TealAccent
                            }
                            Surface(color = typeColor.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
                                val typeLabel = when (product.productType) {
                                    "RawMaterial" -> "Raw Mat."
                                    else          -> product.productType
                                }
                                Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = typeColor,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                        if (!product.isActive) {
                            Surface(color = RedError.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
                                Text("Inactive", style = MaterialTheme.typography.labelSmall, color = RedError,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(product.categoryName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (product.printerName.isNotBlank()) {
                            Text("· ${product.printerName}", style = MaterialTheme.typography.labelSmall, color = TealAccent)
                        }
                        if (product.isStockManaged) {
                            Text("· Stock", style = MaterialTheme.typography.labelSmall, color = BlueInfo)
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(product.salePrice.formatCurrency(currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (product.marginPercent > 0) {
                    val mc = when {
                        product.marginPercent >= 50 -> GreenSuccess
                        product.marginPercent >= 25 -> MaterialTheme.colorScheme.primary
                        else                        -> MaterialTheme.colorScheme.error
                    }
                    Text("${product.marginPercent.toInt()}% margin",
                        style = MaterialTheme.typography.labelSmall,
                        color = mc)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSizes, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Tune, null, Modifier.size(16.dp), tint = TealAccent)
                    }
                    IconButton(onClick = onOpenSchedule, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), tint = BlueInfo)
                    }
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = AmberWarning)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = RedError)
                    }
                    Switch(
                        checked         = product.isActive,
                        onCheckedChange = { onToggle() },
                        modifier        = Modifier.height(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoriesTab(
    categories: List<Category>,
    onEdit:     (Category) -> Unit,
    onDelete:   (Int) -> Unit,
    padding:    PaddingValues
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories, key = { it.categoryId }) { cat ->
            val catColor = runCatching { Color(android.graphics.Color.parseColor(cat.colorCode)) }.getOrElse { MaterialTheme.colorScheme.primary }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.size(24.dp).background(catColor, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(cat.categoryName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                            color = if (cat.isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (cat.displayOrder > 0) Text("Order: ${cat.displayOrder}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!cat.isActive) {
                                Surface(color = RedError.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall) {
                                    Text("Inactive", style = MaterialTheme.typography.labelSmall, color = RedError, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                                }
                            }
                        }
                    }
                    IconButton(onClick = { onEdit(cat) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = AmberWarning)
                    }
                    IconButton(onClick = { onDelete(cat.categoryId) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = RedError)
                    }
                }
            }
        }
    }
}

private val PRODUCT_UNITS = listOf("Pcs", "Kg", "g", "L", "ml", "Dozen", "Box", "Pack", "Bag", "Bottle")

@Composable
private fun ProductDialog(
    title:               String,
    categories:          List<Category>,
    taxes:               List<TaxRate>       = emptyList(),
    currency:            String              = "",
    sizes:               List<ProductSize>   = emptyList(),
    allModGroups:        List<ModifierGroup> = emptyList(),
    initialModGroupIds:  List<Int>           = emptyList(),
    onAddSize:           ((String, Double) -> Unit)? = null,
    onDeleteSize:        ((Int) -> Unit)?    = null,
    initialName:         String  = "",
    initialOtherName:    String  = "",
    initialCode:         String  = "",
    initialCatId:        Int     = 0,
    initialPrice:        String  = "",
    initialCostPrice:    String  = "",
    initialPrinter:      String  = "",
    initialType:         String  = "Normal",
    initialStockManaged: Boolean = false,
    initialRecipeBased:  Boolean = false,
    initialDisplayOrder: String  = "0",
    initialIsActive:     Boolean = true,
    initialTaxId:        Int?    = null,
    initialUnit:         String  = "Pcs",
    initialReorderLevel: String  = "",
    suggestedCode:       String  = "",
    onRequestCode:       (String) -> Unit = {},
    onConfirm:           (name: String, code: String, catId: Int, price: Double, costPrice: Double,
                          printer: String, type: String, stockManaged: Boolean, recipeBased: Boolean,
                          displayOrder: Int, otherName: String, taxId: Int?, unit: String,
                          reorderLevel: Double, isActive: Boolean,
                          pendingSizes: List<Triple<String, Double, Double>>,
                          modifierGroupIds: List<Int>) -> Unit,
    onDismiss:           () -> Unit
) {
    var name         by remember { mutableStateOf(initialName) }
    var otherName    by remember { mutableStateOf(initialOtherName) }
    var code         by remember { mutableStateOf(initialCode) }
    var price        by remember { mutableStateOf(initialPrice) }
    var costPrice    by remember { mutableStateOf(initialCostPrice) }
    var printer      by remember { mutableStateOf(initialPrinter) }
    var selectedId   by remember { mutableStateOf(initialCatId) }
    var productType  by remember { mutableStateOf(initialType.ifBlank { "Normal" }) }
    var stockManaged by remember { mutableStateOf(initialStockManaged) }
    var recipeBased  by remember { mutableStateOf(initialRecipeBased) }
    var dispOrder    by remember { mutableStateOf(initialDisplayOrder) }
    var isActive     by remember { mutableStateOf(initialIsActive) }
    var selectedTaxId by remember { mutableStateOf(initialTaxId) }
    var selectedUnit  by remember { mutableStateOf(initialUnit.ifBlank { "Pcs" }) }
    var reorderLevel  by remember { mutableStateOf(initialReorderLevel) }
    var catExpanded  by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }
    var taxExpanded  by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    var showSizeForm     by remember { mutableStateOf(false) }
    var newSizeName      by remember { mutableStateOf("") }
    var newSizePrice     by remember { mutableStateOf("") }
    var newSizeCostPrice by remember { mutableStateOf("") }
    var localSizes          by remember { mutableStateOf<List<Triple<String, Double, Double>>>(emptyList()) }
    var selectedModGroupIds by remember(initialModGroupIds) { mutableStateOf(initialModGroupIds.toSet()) }
    val selectedCat  = categories.find { it.categoryId == selectedId }
    val saleVal      = price.toDoubleOrNull() ?: 0.0
    val costVal      = costPrice.toDoubleOrNull() ?: 0.0
    val margin       = if (saleVal > 0 && costVal > 0) ((saleVal - costVal) / saleVal * 100).toInt() else -1

    LaunchedEffect(suggestedCode) { if (suggestedCode.isNotBlank()) code = suggestedCode }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.94f),
            shape    = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                // ── Header ───────────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                HorizontalDivider()

                // ── Scrollable body ──────────────────────────────────────────
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = otherName, onValueChange = { otherName = it }, label = { Text("Name (Other Language)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Product Code") }, modifier = Modifier.weight(1f), singleLine = true)
                        FilledTonalButton(onClick = { onRequestCode(name) }, enabled = name.isNotBlank(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) { Text("Auto") }
                    }

                    // Category
                    Box {
                        OutlinedTextField(
                            value = selectedCat?.categoryName ?: "Select Category *", onValueChange = {},
                            readOnly = true, label = { Text("Category") },
                            trailingIcon = { IconButton(onClick = { catExpanded = !catExpanded }) { Icon(if (catExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }, modifier = Modifier.fillMaxWidth()) {
                            categories.forEach { cat -> DropdownMenuItem(text = { Text(cat.categoryName) }, onClick = { selectedId = cat.categoryId; catExpanded = false }) }
                        }
                    }

                    // Product Type
                    Box {
                        OutlinedTextField(
                            value = productType, onValueChange = {}, readOnly = true, label = { Text("Product Type") },
                            trailingIcon = { IconButton(onClick = { typeExpanded = !typeExpanded }) { Icon(if (typeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) } },
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            PRODUCT_TYPES.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { productType = t; typeExpanded = false }) }
                        }
                    }

                    // Tax
                    if (taxes.isNotEmpty()) {
                        val selectedTax = taxes.find { it.taxId == selectedTaxId }
                        Box {
                            OutlinedTextField(
                                value = selectedTax?.let { "${it.taxName} (${it.taxPercent}%)" } ?: "No Tax",
                                onValueChange = {}, readOnly = true, label = { Text("Tax") },
                                trailingIcon = { IconButton(onClick = { taxExpanded = !taxExpanded }) { Icon(if (taxExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) } },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(expanded = taxExpanded, onDismissRequest = { taxExpanded = false }, modifier = Modifier.fillMaxWidth()) {
                                DropdownMenuItem(text = { Text("No Tax") }, onClick = { selectedTaxId = null; taxExpanded = false })
                                taxes.forEach { tax -> DropdownMenuItem(text = { Text("${tax.taxName} (${tax.taxPercent}%)") }, onClick = { selectedTaxId = tax.taxId; taxExpanded = false }) }
                            }
                        }
                    }

                    // Prices
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Sale Price *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = costPrice, onValueChange = { costPrice = it }, label = { Text("Cost Price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), singleLine = true)
                    }
                    if (margin >= 0) {
                        val marginColor = when { margin >= 50 -> GreenSuccess; margin >= 25 -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.error }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.TrendingUp, null, Modifier.size(14.dp), tint = marginColor)
                            Text("Margin: $margin%", style = MaterialTheme.typography.labelMedium, color = marginColor)
                        }
                    }

                    // Printer & Display Order
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value         = printer,
                            onValueChange = { printer = it },
                            label         = { Text("Kitchen Printer") },
                            leadingIcon   = { Icon(Icons.Default.Print, null, Modifier.size(16.dp)) },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true
                        )
                        OutlinedTextField(
                            value           = dispOrder,
                            onValueChange   = { dispOrder = it },
                            label           = { Text("Order") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier        = Modifier.width(80.dp),
                            singleLine      = true
                        )
                    }

                    // Toggles
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            Switch(checked = stockManaged, onCheckedChange = { stockManaged = it }, modifier = Modifier.height(28.dp))
                            Text("Stock Managed", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            Switch(checked = recipeBased, onCheckedChange = { recipeBased = it }, modifier = Modifier.height(28.dp))
                            Text("Recipe Based", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Unit + Reorder Level (stock-managed only)
                    if (stockManaged) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value         = selectedUnit,
                                    onValueChange = {},
                                    readOnly      = true,
                                    label         = { Text("Unit") },
                                    trailingIcon  = {
                                        IconButton(onClick = { unitExpanded = !unitExpanded }) {
                                            Icon(if (unitExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                                    PRODUCT_UNITS.forEach { u ->
                                        DropdownMenuItem(text = { Text(u) }, onClick = { selectedUnit = u; unitExpanded = false })
                                    }
                                }
                            }
                            OutlinedTextField(
                                value           = reorderLevel,
                                onValueChange   = { reorderLevel = it },
                                label           = { Text("Reorder Level") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier        = Modifier.weight(1f),
                                singleLine      = true
                            )
                        }
                    }

                    // Active toggle
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Active", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(
                                if (isActive) "Visible in POS" else "Hidden from POS",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }

                    // Modifier groups section (Normal / Pizza / Deal — matches WPF ShowModifiersPanel)
                    if (productType != "RawMaterial") {
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Modifier Groups", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (selectedModGroupIds.isNotEmpty()) {
                                Surface(
                                    color = TealAccent.copy(alpha = 0.15f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        "${selectedModGroupIds.size} selected",
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = TealAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        if (allModGroups.isEmpty()) {
                            Card(
                                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "No modifier groups defined yet. Create them via the Tune button on a product in the list.",
                                    style    = MaterialTheme.typography.bodySmall,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            allModGroups.forEach { group ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Checkbox(
                                        checked         = group.modifierGroupId in selectedModGroupIds,
                                        onCheckedChange = { checked ->
                                            selectedModGroupIds = if (checked)
                                                selectedModGroupIds + group.modifierGroupId
                                            else
                                                selectedModGroupIds - group.modifierGroupId
                                        }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(group.groupName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                        if (group.modifiers.isNotEmpty()) {
                                            Text(
                                                "${group.modifiers.size} modifier(s) · ${group.minSelection}-${group.maxSelection} sel",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pizza sizes inline section
                    if (productType == "Pizza") {
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Pizza Sizes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            FilledTonalButton(
                                onClick        = { showSizeForm = !showSizeForm },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(if (showSizeForm) Icons.Default.ExpandLess else Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showSizeForm) "Cancel" else "Add Size")
                            }
                        }
                        if (showSizeForm) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                                    OutlinedTextField(
                                        value         = newSizeName,
                                        onValueChange = { newSizeName = it },
                                        label         = { Text("Size Name") },
                                        modifier      = Modifier.weight(1.5f),
                                        singleLine    = true
                                    )
                                    OutlinedTextField(
                                        value           = newSizePrice,
                                        onValueChange   = { newSizePrice = it },
                                        label           = { Text("Sale Price") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier        = Modifier.weight(1f),
                                        singleLine      = true
                                    )
                                    OutlinedTextField(
                                        value           = newSizeCostPrice,
                                        onValueChange   = { newSizeCostPrice = it },
                                        label           = { Text("Cost Price") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier        = Modifier.weight(1f),
                                        singleLine      = true
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        if (newSizeName.isNotBlank() && newSizePrice.isNotBlank()) {
                                            val sPrice = newSizePrice.toDoubleOrNull() ?: 0.0
                                            val sCost  = newSizeCostPrice.toDoubleOrNull() ?: 0.0
                                            if (onAddSize != null) {
                                                onAddSize(newSizeName, sPrice)
                                            } else {
                                                localSizes = localSizes + Triple(newSizeName, sPrice, sCost)
                                            }
                                            newSizeName = ""; newSizePrice = ""; newSizeCostPrice = ""
                                            showSizeForm = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) { Text("Add Size") }
                            }
                        }
                        // Edit mode: show DB-backed sizes list
                        if (onAddSize != null) {
                            if (sizes.isEmpty()) {
                                Text(
                                    "No sizes defined yet. Add sizes like Small / Medium / Large.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                sizes.forEach { size ->
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment     = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(size.sizeName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                Text("Sale: ${size.price.formatCurrency(currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                if (size.costPrice > 0) Text("Cost: ${size.costPrice.formatCurrency(currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            IconButton(onClick = { onDeleteSize?.invoke(size.sizeId) }, modifier = Modifier.size(30.dp)) {
                                                Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = RedError)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Add mode: show in-memory localSizes
                            if (localSizes.isEmpty()) {
                                Text(
                                    "No sizes added yet. Sizes will be saved when you tap Save.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                localSizes.forEachIndexed { idx, (sName, sPrice, sCost) ->
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                        Row(
                                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment     = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(sName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                Text("Sale: ${sPrice.formatCurrency(currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                if (sCost > 0) Text("Cost: ${sCost.formatCurrency(currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            IconButton(
                                                onClick  = { localSizes = localSizes.filterIndexed { i, _ -> i != idx } },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, null, Modifier.size(14.dp), tint = RedError)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            if (name.isNotBlank() && selectedId != 0 && price.isNotBlank())
                                onConfirm(
                                    name, code, selectedId,
                                    price.toDoubleOrNull() ?: 0.0,
                                    costPrice.toDoubleOrNull() ?: 0.0,
                                    printer, productType, stockManaged, recipeBased,
                                    dispOrder.toIntOrNull() ?: 0,
                                    otherName, selectedTaxId, selectedUnit,
                                    reorderLevel.toDoubleOrNull() ?: 0.0,
                                    isActive, localSizes, selectedModGroupIds.toList()
                                )
                        }
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    title:                    String  = "Add Category",
    initialName:              String  = "",
    initialOtherLanguageName: String  = "",
    initialColor:             String  = "#FF6B35",
    initialDisplayOrder:      String  = "0",
    initialIsActive:          Boolean = true,
    onConfirm:                (name: String, color: String, displayOrder: Int, otherLanguageName: String, isActive: Boolean) -> Unit,
    onDismiss:                () -> Unit
) {
    val colorPalette = remember {
        listOf(
            "#FF6B35", "#E74C3C", "#E91E63", "#9C27B0",
            "#3F51B5", "#2196F3", "#009688", "#4CAF50",
            "#8BC34A", "#FFC107", "#FF9800", "#795548",
            "#607D8B", "#9E9E9E", "#212121", "#FFFFFF"
        )
    }
    var name              by remember { mutableStateOf(initialName) }
    var otherLanguageName by remember { mutableStateOf(initialOtherLanguageName) }
    var color             by remember { mutableStateOf(initialColor) }
    var displayOrder      by remember { mutableStateOf(initialDisplayOrder) }
    var isActive          by remember { mutableStateOf(initialIsActive) }
    val parsedColor       = runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Category Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = otherLanguageName, onValueChange = { otherLanguageName = it }, label = { Text("Other Language Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                // Color palette swatches
                Text("Color", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                colorPalette.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { hex ->
                            val swatchColor = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { Color.Gray }
                            val isSelected  = color.equals(hex, ignoreCase = true)
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(swatchColor, CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable { color = hex }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = color,
                        onValueChange = { color = it },
                        label         = { Text("Hex code") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                    if (parsedColor != null) {
                        Box(Modifier.size(36.dp).background(parsedColor, androidx.compose.foundation.shape.RoundedCornerShape(6.dp)))
                    }
                }
                OutlinedTextField(
                    value           = displayOrder,
                    onValueChange   = { displayOrder = it },
                    label           = { Text("Display Order") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier        = Modifier.fillMaxWidth(0.5f),
                    singleLine      = true
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            if (isActive) "Visible in POS" else "Hidden from POS",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isActive) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, color, displayOrder.toIntOrNull() ?: 0, otherLanguageName, isActive) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─── Sizes & Modifier Groups Sheet ───────────────────────────────────────────

@Composable
private fun SizesModifiersSheet(
    product:               Product,
    sizes:                 List<ProductSize>,
    productModGroups:      List<ModifierGroup>,
    allModGroups:          List<ModifierGroup>,
    stockManagedProducts:  List<Product> = emptyList(),
    isLoading:             Boolean,
    currency:              String,
    onAddSize:             (String, Double, Double) -> Unit,
    onUpdateSize:          (Int, String, Double, Double) -> Unit,
    onDeleteSize:          (Int) -> Unit,
    onAddModGroup:         (String, Int, Int, Boolean) -> Unit,
    onUpdateModGroup:      (Int, String, Int, Int, Boolean) -> Unit,
    onUnlinkGroup:         (Int) -> Unit,
    onLinkGroup:           (Int) -> Unit,
    onAddModifier:         (Int, String, Double, Int) -> Unit,
    onUpdateModifier:      (Int, String, Double, Int) -> Unit,
    onDeleteModifier:      (Int) -> Unit
) {
    var sheetTab by remember { mutableStateOf(0) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(product.productName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(product.salePrice.formatCurrency(currency), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        TabRow(selectedTabIndex = sheetTab) {
            Tab(selected = sheetTab == 0, onClick = { sheetTab = 0 }, text = { Text("Sizes") }, icon = { Icon(Icons.Default.Straighten, null, Modifier.size(16.dp)) })
            Tab(selected = sheetTab == 1, onClick = { sheetTab = 1 }, text = { Text("Modifiers") }, icon = { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) })
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        when (sheetTab) {
            0 -> SizesTab(sizes = sizes, currency = currency, onAdd = onAddSize, onUpdate = onUpdateSize, onDelete = onDeleteSize)
            1 -> ModifiersTab(
                productModGroups     = productModGroups,
                allModGroups         = allModGroups,
                stockManagedProducts = stockManagedProducts,
                currency             = currency,
                onAddGroup           = onAddModGroup,
                onUpdateGroup        = onUpdateModGroup,
                onUnlinkGroup        = onUnlinkGroup,
                onLinkGroup          = onLinkGroup,
                onAddModifier        = onAddModifier,
                onUpdateModifier     = onUpdateModifier,
                onDeleteModifier     = onDeleteModifier
            )
        }
    }
}

@Composable
private fun SizesTab(
    sizes:    List<ProductSize>,
    currency: String,
    onAdd:    (String, Double, Double) -> Unit,
    onUpdate: (Int, String, Double, Double) -> Unit,
    onDelete: (Int) -> Unit
) {
    var showAddDialog  by remember { mutableStateOf(false) }
    var editingSize    by remember { mutableStateOf<ProductSize?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${sizes.size} size(s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(
                onClick = { showAddDialog = true },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Size")
            }
        }

        if (sizes.isEmpty()) {
            Text(
                "No sizes defined. The base price is used when ordering.\nAdd sizes to offer Small / Medium / Large options with per-size pricing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            sizes.forEach { size ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(size.sizeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Sale: ${size.price.formatCurrency(currency)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (size.costPrice > 0) Text("Cost: ${size.costPrice.formatCurrency(currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row {
                            IconButton(onClick = { editingSize = size }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = AmberWarning)
                            }
                            IconButton(onClick = { onDelete(size.sizeId) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = RedError)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        SizeDialog(onConfirm = { name, price, cost -> onAdd(name, price, cost); showAddDialog = false }, onDismiss = { showAddDialog = false })
    }
    editingSize?.let { s ->
        SizeDialog(
            initialName      = s.sizeName,
            initialPrice     = s.price.toString(),
            initialCostPrice = s.costPrice.toString(),
            onConfirm        = { name, price, cost -> onUpdate(s.sizeId, name, price, cost); editingSize = null },
            onDismiss        = { editingSize = null }
        )
    }
}

@Composable
private fun SizeDialog(
    initialName:      String = "",
    initialPrice:     String = "",
    initialCostPrice: String = "",
    onConfirm:        (name: String, price: Double, costPrice: Double) -> Unit,
    onDismiss:        () -> Unit
) {
    var name      by remember { mutableStateOf(initialName) }
    var price     by remember { mutableStateOf(initialPrice) }
    var costPrice by remember { mutableStateOf(initialCostPrice) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isBlank()) "Add Size" else "Edit Size") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Size Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = price, onValueChange = { price = it },
                    label = { Text("Sale Price *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = costPrice, onValueChange = { costPrice = it },
                    label = { Text("Cost Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && price.isNotBlank())
                    onConfirm(name, price.toDoubleOrNull() ?: 0.0, costPrice.toDoubleOrNull() ?: 0.0)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ModifiersTab(
    productModGroups:     List<ModifierGroup>,
    allModGroups:         List<ModifierGroup>,
    stockManagedProducts: List<Product> = emptyList(),
    currency:             String,
    onAddGroup:           (String, Int, Int, Boolean) -> Unit,
    onUpdateGroup:        (Int, String, Int, Int, Boolean) -> Unit,
    onUnlinkGroup:        (Int) -> Unit,
    onLinkGroup:          (Int) -> Unit,
    onAddModifier:        (Int, String, Double, Int) -> Unit,
    onUpdateModifier:     (Int, String, Double, Int) -> Unit,
    onDeleteModifier:     (Int) -> Unit
) {
    var showAddGroupDialog  by remember { mutableStateOf(false) }
    var showLinkDialog      by remember { mutableStateOf(false) }
    var editingGroup        by remember { mutableStateOf<ModifierGroup?>(null) }
    val linkedIds           = remember(productModGroups) { productModGroups.map { it.modifierGroupId }.toSet() }
    val unlinkable          = remember(allModGroups, linkedIds) { allModGroups.filter { it.modifierGroupId !in linkedIds } }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (unlinkable.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showLinkDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Link, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Link Existing", style = MaterialTheme.typography.labelMedium)
                }
            }
            FilledTonalButton(
                onClick = { showAddGroupDialog = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Group", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (productModGroups.isEmpty()) {
            Text(
                "No modifier groups linked. Add groups like 'Sauces' or 'Toppings' to let customers customize their order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            productModGroups.forEach { group ->
                ModifierGroupCard(
                    group                = group,
                    currency             = currency,
                    stockManagedProducts = stockManagedProducts,
                    onEdit               = { editingGroup = group },
                    onUnlink             = { onUnlinkGroup(group.modifierGroupId) },
                    onAddModifier        = { name, price, sid -> onAddModifier(group.modifierGroupId, name, price, sid) },
                    onUpdateModifier     = onUpdateModifier,
                    onDeleteModifier     = onDeleteModifier
                )
            }
        }
    }

    if (showAddGroupDialog) {
        ModifierGroupDialog(
            onConfirm = { name, min, max, req -> onAddGroup(name, min, max, req); showAddGroupDialog = false },
            onDismiss = { showAddGroupDialog = false }
        )
    }
    editingGroup?.let { g ->
        ModifierGroupDialog(
            initialName     = g.groupName,
            initialMin      = g.minSelection.toString(),
            initialMax      = g.maxSelection.toString(),
            initialRequired = g.isRequired,
            onConfirm       = { name, min, max, req -> onUpdateGroup(g.modifierGroupId, name, min, max, req); editingGroup = null },
            onDismiss       = { editingGroup = null }
        )
    }
    if (showLinkDialog) {
        LinkGroupDialog(
            groups    = unlinkable,
            onSelect  = { onLinkGroup(it); showLinkDialog = false },
            onDismiss = { showLinkDialog = false }
        )
    }
}

@Composable
private fun ModifierGroupCard(
    group:                ModifierGroup,
    currency:             String,
    stockManagedProducts: List<Product> = emptyList(),
    onEdit:               () -> Unit,
    onUnlink:             () -> Unit,
    onAddModifier:        (String, Double, Int) -> Unit,
    onUpdateModifier:     (Int, String, Double, Int) -> Unit,
    onDeleteModifier:     (Int) -> Unit
) {
    var expanded         by remember { mutableStateOf(true) }
    var showAddModDialog by remember { mutableStateOf(false) }
    var editingMod       by remember { mutableStateOf<com.fastpos.android.data.models.ProductModifier?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, TealAccent.copy(0.3f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Text(group.groupName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (group.isRequired) {
                        Surface(color = RedError.copy(0.15f), shape = MaterialTheme.shapes.small) {
                            Text("Required", style = MaterialTheme.typography.labelSmall, color = RedError, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    Text("${group.minSelection}-${group.maxSelection}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = { showAddModDialog = true }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(14.dp), tint = GreenSuccess)
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(14.dp), tint = AmberWarning)
                    }
                    IconButton(onClick = onUnlink, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.LinkOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(30.dp)) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(14.dp))
                    }
                }
            }
            if (expanded) {
                if (group.modifiers.isEmpty()) {
                    Text("No modifiers yet. Tap + to add.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    group.modifiers.forEach { mod ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(mod.modifierName, style = MaterialTheme.typography.bodySmall)
                                if (mod.stockItemName.isNotBlank()) {
                                    Text(
                                        "Stock: ${mod.stockItemName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TealAccent
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (mod.extraPrice > 0)
                                    Text("+${mod.extraPrice.formatCurrency(currency)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { editingMod = mod }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Edit, null, Modifier.size(12.dp), tint = AmberWarning)
                                }
                                IconButton(onClick = { onDeleteModifier(mod.modifierId) }, modifier = Modifier.size(26.dp)) {
                                    Icon(Icons.Default.Delete, null, Modifier.size(12.dp), tint = RedError)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddModDialog) {
        ModifierDialog(
            stockManagedProducts = stockManagedProducts,
            onConfirm            = { name, price, sid -> onAddModifier(name, price, sid); showAddModDialog = false },
            onDismiss            = { showAddModDialog = false }
        )
    }
    editingMod?.let { mod ->
        ModifierDialog(
            initialName          = mod.modifierName,
            initialPrice         = if (mod.extraPrice > 0) mod.extraPrice.toString() else "",
            initialStockItemId   = mod.stockItemId,
            stockManagedProducts = stockManagedProducts,
            onConfirm            = { name, price, sid -> onUpdateModifier(mod.modifierId, name, price, sid); editingMod = null },
            onDismiss            = { editingMod = null }
        )
    }
}

@Composable
private fun ModifierGroupDialog(
    initialName:     String  = "",
    initialMin:      String  = "0",
    initialMax:      String  = "1",
    initialRequired: Boolean = false,
    onConfirm:       (String, Int, Int, Boolean) -> Unit,
    onDismiss:       () -> Unit
) {
    var name     by remember { mutableStateOf(initialName) }
    var minSel   by remember { mutableStateOf(initialMin) }
    var maxSel   by remember { mutableStateOf(initialMax) }
    var required by remember { mutableStateOf(initialRequired) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isBlank()) "New Modifier Group" else "Edit Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group Name (e.g. Sauces) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minSel, onValueChange = { minSel = it },
                        label = { Text("Min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxSel, onValueChange = { maxSel = it },
                        label = { Text("Max") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Required selection", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = required, onCheckedChange = { required = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank())
                    onConfirm(name, minSel.toIntOrNull() ?: 0, maxSel.toIntOrNull() ?: 1, required)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ModifierDialog(
    initialName:          String        = "",
    initialPrice:         String        = "",
    initialStockItemId:   Int           = 0,
    stockManagedProducts: List<Product> = emptyList(),
    onConfirm:            (String, Double, Int) -> Unit,
    onDismiss:            () -> Unit
) {
    var name            by remember { mutableStateOf(initialName) }
    var price           by remember { mutableStateOf(initialPrice) }
    var linkToStock     by remember { mutableStateOf(initialStockItemId > 0) }
    var selectedStockId by remember { mutableStateOf(initialStockItemId) }
    var stockExpanded   by remember { mutableStateOf(false) }

    val selectedStockProduct = stockManagedProducts.find { it.productId == selectedStockId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isBlank()) "Add Modifier" else "Edit Modifier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name *") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price, onValueChange = { price = it },
                    label = { Text("Extra Price (0 = free)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (stockManagedProducts.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Link to stock item", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = linkToStock,
                            onCheckedChange = { linkToStock = it; if (!it) selectedStockId = 0 }
                        )
                    }
                    if (linkToStock) {
                        Box {
                            OutlinedTextField(
                                value = selectedStockProduct?.productName ?: "Select product…",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Stock Item") },
                                trailingIcon = {
                                    IconButton(onClick = { stockExpanded = !stockExpanded }) {
                                        Icon(if (stockExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = stockExpanded,
                                onDismissRequest = { stockExpanded = false },
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = { selectedStockId = 0; stockExpanded = false }
                                )
                                stockManagedProducts.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.productName) },
                                        onClick = { selectedStockId = p.productId; stockExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) onConfirm(name, price.toDoubleOrNull() ?: 0.0, selectedStockId)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LinkGroupDialog(
    groups:    List<ModifierGroup>,
    onSelect:  (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Link, null, tint = TealAccent) },
        title = { Text("Link Existing Group") },
        text  = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups) { group ->
                    OutlinedButton(onClick = { onSelect(group.modifierGroupId) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(group.groupName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("${group.modifiers.size} modifier(s) · ${group.minSelection}-${group.maxSelection} sel",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Schedule Sheet ────────────────────────────────────────────────────────────

private val DAY_LABELS = listOf("Every Day", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val DAY_VALUES = listOf(-1, 0, 1, 2, 3, 4, 5, 6)

@Composable
private fun ScheduleSheet(
    product:   Product,
    schedules: List<ProductSchedule>,
    onAdd:     (String, Int, String, String) -> Unit,
    onUpdate:  (Int, String, Int, String, String) -> Unit,
    onDelete:  (Int) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget    by remember { mutableStateOf<ProductSchedule?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text("Availability Schedule", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(product.productName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = BlueInfo)) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Window")
            }
        }

        if (schedules.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = BlueInfo.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, BlueInfo.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = BlueInfo)
                    Text(
                        "No schedule set — product is always available.\nAdd time windows to restrict when it appears on the POS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            schedules.forEach { s ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(20.dp), tint = BlueInfo)
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.label.ifBlank { "Window" },
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val dayLabel = DAY_LABELS[DAY_VALUES.indexOf(s.dayOfWeek).coerceAtLeast(0)]
                            Text(
                                "$dayLabel · ${s.startTime} – ${s.endTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editTarget = s }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = AmberWarning)
                        }
                        IconButton(onClick = { onDelete(s.scheduleId) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = RedError)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showAddDialog) {
        ScheduleDialog(
            initial   = null,
            onDismiss = { showAddDialog = false },
            onSave    = { label, dow, start, end -> onAdd(label, dow, start, end); showAddDialog = false }
        )
    }

    editTarget?.let { s ->
        ScheduleDialog(
            initial   = s,
            onDismiss = { editTarget = null },
            onSave    = { label, dow, start, end -> onUpdate(s.scheduleId, label, dow, start, end); editTarget = null }
        )
    }
}

@Composable
private fun ScheduleDialog(
    initial:  ProductSchedule?,
    onDismiss: () -> Unit,
    onSave:    (String, Int, String, String) -> Unit
) {
    var label     by remember { mutableStateOf(initial?.label ?: "") }
    var dayIndex  by remember { mutableIntStateOf(if (initial == null) 0 else DAY_VALUES.indexOf(initial.dayOfWeek).coerceAtLeast(0)) }
    var startTime by remember { mutableStateOf(initial?.startTime ?: "08:00") }
    var endTime   by remember { mutableStateOf(initial?.endTime ?: "23:59") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Time Window" else "Edit Time Window") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value         = label,
                    onValueChange = { label = it },
                    label         = { Text("Label (e.g. Breakfast, Happy Hour)") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )

                // Day selector
                Text("Day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(DAY_LABELS.size) { i ->
                        FilterChip(
                            selected = dayIndex == i,
                            onClick  = { dayIndex = i },
                            label    = { Text(DAY_LABELS[i], style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = startTime,
                        onValueChange = { startTime = it },
                        label         = { Text("Start (HH:mm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                    OutlinedTextField(
                        value         = endTime,
                        onValueChange = { endTime = it },
                        label         = { Text("End (HH:mm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (startTime.isNotBlank() && endTime.isNotBlank())
                    onSave(label, DAY_VALUES[dayIndex], startTime, endTime)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun BulkPriceUpdateDialog(
    categories:    List<Category>,
    categoryFilter: Int?,
    onConfirm:     (Int?, Double) -> Unit,
    onDismiss:     () -> Unit
) {
    var selectedCatId by remember { mutableStateOf(categoryFilter) }
    var changeInput   by remember { mutableStateOf("") }
    var isIncrease    by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.PriceChange, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Bulk Price Update") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Direction toggle
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isIncrease,
                        onClick  = { isIncrease = true },
                        label    = { Text("Increase") },
                        modifier = Modifier.weight(1f),
                        colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenSuccess.copy(alpha = 0.2f))
                    )
                    FilterChip(
                        selected = !isIncrease,
                        onClick  = { isIncrease = false },
                        label    = { Text("Decrease") },
                        modifier = Modifier.weight(1f),
                        colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = RedError.copy(alpha = 0.2f))
                    )
                }
                // Percent input
                OutlinedTextField(
                    value         = changeInput,
                    onValueChange = { changeInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label         = { Text("Percentage (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )
                // Category selector
                Text("Apply to:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = selectedCatId == null,
                        onClick  = { selectedCatId = null },
                        label    = { Text("All Categories") }
                    )
                    categories.forEach { cat ->
                        val catColor = runCatching { Color(android.graphics.Color.parseColor(cat.colorCode)) }.getOrElse { MaterialTheme.colorScheme.primary }
                        FilterChip(
                            selected = selectedCatId == cat.categoryId,
                            onClick  = { selectedCatId = cat.categoryId },
                            label    = { Text(cat.categoryName) },
                            colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = catColor.copy(alpha = 0.25f))
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pct = changeInput.toDoubleOrNull() ?: return@Button
                    onConfirm(selectedCatId, if (isIncrease) pct else -pct)
                },
                enabled = changeInput.isNotBlank()
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
