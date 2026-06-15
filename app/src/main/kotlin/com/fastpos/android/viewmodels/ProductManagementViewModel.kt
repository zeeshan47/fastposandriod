package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Category
import com.fastpos.android.data.models.ModifierGroup
import com.fastpos.android.data.models.Product
import com.fastpos.android.data.models.ProductSchedule
import com.fastpos.android.data.models.ProductSize
import com.fastpos.android.data.models.TaxRate
import com.fastpos.android.data.repositories.ProductRepository
import com.fastpos.android.data.repositories.ProductScheduleRepository
import com.fastpos.android.data.repositories.TaxRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductManagementViewModel @Inject constructor(
    private val productRepo:   ProductRepository,
    private val scheduleRepo:  ProductScheduleRepository,
    private val taxRepo:       TaxRepository,
    private val db:            DatabaseHelper,
    val session:               SessionManager
) : ViewModel() {

    private val _allProducts         = MutableStateFlow<List<Product>>(emptyList())
    private val _products            = MutableStateFlow<List<Product>>(emptyList())
    private val _categories          = MutableStateFlow<List<Category>>(emptyList())
    private val _activeCategories    = MutableStateFlow<List<Category>>(emptyList())
    private val _isLoading       = MutableStateFlow(false)
    private val _message         = MutableStateFlow<String?>(null)
    private val _search          = MutableStateFlow("")
    private val _categoryFilter  = MutableStateFlow<Int?>(null)
    private val _typeFilter      = MutableStateFlow<String?>(null)
    private val _statusFilter    = MutableStateFlow<Boolean?>(null) // null=all, true=active, false=inactive
    private val _generatedCode   = MutableStateFlow("")
    private val _taxes           = MutableStateFlow<List<TaxRate>>(emptyList())

    // Sizes & Modifier Groups sheet
    private val _selectedProduct       = MutableStateFlow<Product?>(null)
    private val _sizes                 = MutableStateFlow<List<ProductSize>>(emptyList())
    private val _productModGroups      = MutableStateFlow<List<ModifierGroup>>(emptyList())
    private val _allModGroups          = MutableStateFlow<List<ModifierGroup>>(emptyList())
    private val _stockManagedProducts  = MutableStateFlow<List<Product>>(emptyList())
    private val _sizesLoading          = MutableStateFlow(false)

    // Schedule sheet
    private val _scheduleProduct     = MutableStateFlow<Product?>(null)
    private val _schedules           = MutableStateFlow<List<ProductSchedule>>(emptyList())

    val products:          StateFlow<List<Product>>          = _products
    val categories:        StateFlow<List<Category>>         = _categories
    val activeCategories:  StateFlow<List<Category>>         = _activeCategories
    val isLoading:         StateFlow<Boolean>                = _isLoading
    val message:           StateFlow<String?>                = _message
    val search:            StateFlow<String>                 = _search
    val categoryFilter:    StateFlow<Int?>                   = _categoryFilter
    val typeFilter:        StateFlow<String?>                = _typeFilter
    val statusFilter:      StateFlow<Boolean?>               = _statusFilter
    val selectedProduct:        StateFlow<Product?>               = _selectedProduct
    val sizes:                  StateFlow<List<ProductSize>>      = _sizes
    val productModGroups:       StateFlow<List<ModifierGroup>>    = _productModGroups
    val allModGroups:           StateFlow<List<ModifierGroup>>    = _allModGroups
    val stockManagedProducts:   StateFlow<List<Product>>          = _stockManagedProducts
    val sizesLoading:           StateFlow<Boolean>                = _sizesLoading
    val scheduleProduct:   StateFlow<Product?>               = _scheduleProduct
    val schedules:         StateFlow<List<ProductSchedule>>  = _schedules
    val generatedCode:     StateFlow<String>                 = _generatedCode
    val taxes:             StateFlow<List<TaxRate>>          = _taxes

    init { load() }

    fun setSearch(q: String) { _search.value = q; applyFilter() }
    fun setCategoryFilter(id: Int?) { _categoryFilter.value = id; applyFilter() }
    fun setTypeFilter(type: String?) { _typeFilter.value = type; applyFilter() }
    fun setStatusFilter(active: Boolean?) { _statusFilter.value = active; applyFilter() }

    private fun applyFilter() {
        var list = _allProducts.value
        val q      = _search.value
        val cat    = _categoryFilter.value
        val type   = _typeFilter.value
        val status = _statusFilter.value
        if (q.isNotBlank()) list = list.filter { it.productName.contains(q, true) || it.productCode.contains(q, true) }
        if (cat    != null) list = list.filter { it.categoryId == cat }
        if (type   != null) list = list.filter { it.productType == type }
        if (status != null) list = list.filter { it.isActive == status }
        _products.value = list
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _allProducts.value      = productRepo.getAllProductsForManagement()
                _categories.value       = productRepo.getCategories(includeInactive = true)
                _activeCategories.value = productRepo.getCategories(includeInactive = false)
                try { _taxes.value = taxRepo.getAllTaxes().filter { it.isActive } } catch (_: Exception) {}
                try { _allModGroups.value = productRepo.getAllModifierGroups() } catch (_: Exception) {}
                applyFilter()
            } catch (e: Exception) { _message.value = e.message }
            finally { _isLoading.value = false }
        }
    }

    fun addProduct(
        name: String, categoryId: Int, price: Double,
        productType: String = "Normal", code: String = "", printer: String = "",
        costPrice: Double = 0.0, isStockManaged: Boolean = false,
        isRecipeBased: Boolean = false, displayOrder: Int = 0,
        otherName: String = "", taxId: Int? = null,
        unit: String = "Pcs", reorderLevel: Double = 0.0,
        pendingSizes: List<Triple<String, Double, Double>> = emptyList(),
        modifierGroupIds: List<Int> = emptyList()
    ) {
        if (name.isBlank() || categoryId == 0) { _message.value = "Name and category required."; return }
        viewModelScope.launch {
            try {
                val kitchenPrinterId: Int? = if (printer.isNotBlank()) {
                    try { db.queryOne("SELECT MIN(PrinterId) AS P FROM KitchenPrinters WHERE PrinterName=? AND IsActive=1", listOf(printer)) { it.getInt("P").takeIf { it > 0 } } } catch (_: Exception) { null }
                } else null
                val id = db.insertAndGetId(
                    """INSERT INTO Products
                       (ProductCode, ProductName, ProductNameOtherLanguage, CategoryId,
                        ProductType, SalePrice, PurchasePrice, IsStockManaged, IsRecipeBased,
                        TaxId, KitchenPrinterId, DisplayOrder, IsActive,
                        Unit, OpeningStock, CurrentStock, ReorderLevel, CreatedBy, CreatedAt)
                       VALUES (?, ?, ISNULL(?,''), ?,
                               ?, ?, ?, ?, ?,
                               ?, ?, ?, 1,
                               ?, 0, 0, ?, ?, GETDATE())""",
                    listOf(code.ifBlank { null }, name, otherName.ifBlank { null }, categoryId,
                           productType.ifBlank { "Normal" }, price, costPrice,
                           if (isStockManaged) 1 else 0, if (isRecipeBased) 1 else 0,
                           taxId, kitchenPrinterId, displayOrder,
                           unit.ifBlank { "Pcs" }, reorderLevel, session.userId)
                )
                if (id > 0) {
                    pendingSizes.forEach { (sizeName, sizePrice, sizeCost) ->
                        try { productRepo.addProductSize(id, sizeName, sizePrice, sizeCost) } catch (_: Exception) {}
                    }
                    try { productRepo.setModifierGroupAssignments(id, modifierGroupIds) } catch (_: Exception) {}
                }
                _message.value = "Product added."
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateProduct(
        productId: Int, name: String, categoryId: Int, price: Double,
        code: String = "", printer: String = "", costPrice: Double = 0.0,
        productType: String = "Normal", isStockManaged: Boolean = false,
        isRecipeBased: Boolean = false, displayOrder: Int = 0,
        otherName: String = "", taxId: Int? = null,
        unit: String = "Pcs", reorderLevel: Double = 0.0,
        isActive: Boolean = true,
        modifierGroupIds: List<Int> = emptyList()
    ) {
        viewModelScope.launch {
            try {
                val kitchenPrinterId: Int? = if (printer.isNotBlank()) {
                    try { db.queryOne("SELECT MIN(PrinterId) AS P FROM KitchenPrinters WHERE PrinterName=? AND IsActive=1", listOf(printer)) { it.getInt("P").takeIf { it > 0 } } } catch (_: Exception) { null }
                } else null
                db.execute(
                    """UPDATE Products SET
                       ProductName=?, ProductCode=?, ProductNameOtherLanguage=ISNULL(?,''),
                       CategoryId=?, ProductType=?, SalePrice=?, PurchasePrice=?,
                       IsStockManaged=?, IsRecipeBased=?, IsActive=?,
                       TaxId=?, KitchenPrinterId=?,
                       DisplayOrder=?, Unit=?, ReorderLevel=?,
                       UpdatedAt=GETDATE(), UpdatedBy=?
                       WHERE ProductId=?""",
                    listOf(name, code.ifBlank { null }, otherName.ifBlank { null },
                           categoryId, productType.ifBlank { "Normal" }, price, costPrice,
                           if (isStockManaged) 1 else 0, if (isRecipeBased) 1 else 0, if (isActive) 1 else 0,
                           taxId, kitchenPrinterId,
                           displayOrder, unit.ifBlank { "Pcs" }, reorderLevel,
                           session.userId, productId)
                )
                try { productRepo.setModifierGroupAssignments(productId, modifierGroupIds) } catch (_: Exception) {}
                _message.value = "Product updated."
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun clearGeneratedCode() { _generatedCode.value = "" }

    fun requestCodeSuggestion(name: String) {
        viewModelScope.launch {
            try {
                val nextId = db.queryOne(
                    "SELECT ISNULL(MAX(ProductId), 0) + 1 AS NextId FROM Products",
                    emptyList()
                ) { it.getInt("NextId") } ?: 1
                _generatedCode.value = "P%04d".format(nextId)
            } catch (_: Exception) {
                _generatedCode.value = "P0001"
            }
        }
    }

    fun toggleProductActive(product: Product) {
        viewModelScope.launch {
            try {
                val newState = if (product.isActive) 0 else 1
                db.execute(
                    "UPDATE Products SET IsActive=?, UpdatedAt=GETDATE(), UpdatedBy=? WHERE ProductId=?",
                    listOf(newState, session.userId, product.productId)
                )
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun duplicateProduct(product: Product) {
        viewModelScope.launch {
            try {
                val id = db.insertAndGetId(
                    """INSERT INTO Products (ProductName, ProductCode, CategoryId, SalePrice, ProductType,
                       IsStockManaged, IsActive, DisplayOrder, CreatedAt, CreatedBy)
                       VALUES (?,?,?,?,?,?,1,?,GETDATE(),?)""",
                    listOf("Copy of ${product.productName}", null, product.categoryId,
                           product.salePrice, product.productType,
                           if (product.isStockManaged) 1 else 0,
                           product.displayOrder, session.userId)
                )
                if (id > 0) {
                    try { db.execute("UPDATE Products SET IsRecipeBased=? WHERE ProductId=?", listOf(if (product.isRecipeBased) 1 else 0, id)) } catch (_: Exception) {}
                    if (product.printerName.isNotBlank()) {
                        try { db.execute("UPDATE Products SET PrinterName=? WHERE ProductId=?", listOf(product.printerName, id)) } catch (_: Exception) {}
                        try { db.execute("UPDATE Products SET KitchenPrinterId=(SELECT MIN(PrinterId) FROM KitchenPrinters WHERE PrinterName=? AND IsActive=1) WHERE ProductId=?", listOf(product.printerName, id)) } catch (_: Exception) {}
                    }
                    if (product.costPrice > 0) try { db.execute("UPDATE Products SET PurchasePrice=? WHERE ProductId=?", listOf(product.costPrice, id)) } catch (_: Exception) {}
                    if (product.productNameOtherLanguage.isNotBlank()) try { db.execute("UPDATE Products SET ProductNameOtherLanguage=? WHERE ProductId=?", listOf(product.productNameOtherLanguage, id)) } catch (_: Exception) {}
                    if (product.taxId != null) try { db.execute("UPDATE Products SET TaxId=? WHERE ProductId=?", listOf(product.taxId, id)) } catch (_: Exception) {}
                    try { db.execute("UPDATE Products SET Unit=?, ReorderLevel=? WHERE ProductId=?", listOf(product.unit, product.reorderLevel, id)) } catch (_: Exception) {}
                }
                _message.value = "'${product.productName}' duplicated."
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteProduct(productId: Int) {
        viewModelScope.launch {
            try {
                db.execute(
                    "UPDATE Products SET IsActive=0, UpdatedAt=GETDATE(), UpdatedBy=? WHERE ProductId=?",
                    listOf(session.userId, productId)
                )
                _message.value = "Product deleted."
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun addCategory(name: String, colorCode: String, displayOrder: Int = 0, otherLanguageName: String = "", isActive: Boolean = true) {
        if (name.isBlank()) { _message.value = "Category name required."; return }
        viewModelScope.launch {
            try {
                val id = db.insertAndGetId(
                    "INSERT INTO Categories (CategoryName, ColorCode, DisplayOrder, CreatedAt, CreatedBy) VALUES (?,?,?,GETDATE(),?)",
                    listOf(name, colorCode.ifBlank { "#607D8B" }, displayOrder, session.userId)
                )
                if (id > 0) {
                    try { db.execute("UPDATE Categories SET IsActive=? WHERE CategoryId=?", listOf(if (isActive) 1 else 0, id)) } catch (_: Exception) {}
                    if (otherLanguageName.isNotBlank()) try { db.execute("UPDATE Categories SET CategoryNameOtherLanguage=? WHERE CategoryId=?", listOf(otherLanguageName, id)) } catch (_: Exception) {}
                }
                _message.value = "Category added."
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateCategory(categoryId: Int, name: String, colorCode: String, displayOrder: Int = 0, otherLanguageName: String = "", isActive: Boolean = true) {
        if (name.isBlank()) { _message.value = "Category name required."; return }
        viewModelScope.launch {
            try {
                db.execute(
                    "UPDATE Categories SET CategoryName=?, ColorCode=?, DisplayOrder=?, UpdatedAt=GETDATE() WHERE CategoryId=?",
                    listOf(name, colorCode.ifBlank { "#607D8B" }, displayOrder, categoryId)
                )
                try { db.execute("UPDATE Categories SET IsActive=? WHERE CategoryId=?", listOf(if (isActive) 1 else 0, categoryId)) } catch (_: Exception) {}
                try { db.execute("UPDATE Categories SET CategoryNameOtherLanguage=? WHERE CategoryId=?", listOf(otherLanguageName.ifBlank { null }, categoryId)) } catch (_: Exception) {}
                _message.value = "Category updated."
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteCategory(categoryId: Int) {
        viewModelScope.launch {
            try {
                val count = db.queryOne(
                    "SELECT COUNT(*) AS Cnt FROM Products WHERE CategoryId=? AND IsActive=1",
                    listOf(categoryId)
                ) { rs -> rs.getInt("Cnt") } ?: 0
                if (count > 0) {
                    _message.value = "Cannot delete: $count product(s) are in this category."
                    return@launch
                }
                db.execute(
                    "UPDATE Categories SET IsActive=0 WHERE CategoryId=?",
                    listOf(categoryId)
                )
                _message.value = "Category deleted."
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    // ── Sizes & Modifier Groups ───────────────────────────────────────────────

    fun openSizesSheet(product: Product) {
        _selectedProduct.value = product
        viewModelScope.launch {
            _sizesLoading.value = true
            try {
                _sizes.value               = productRepo.getProductSizes(product.productId)
                _productModGroups.value    = productRepo.getModifierGroups(product.productId)
                _allModGroups.value        = productRepo.getAllModifierGroups()
                _stockManagedProducts.value = productRepo.getStockManagedProducts()
            } catch (e: Exception) { _message.value = e.message }
            finally { _sizesLoading.value = false }
        }
    }

    fun closeSizesSheet() { _selectedProduct.value = null }

    // ── Pizza sizes for the product edit dialog ────────────────────────────────
    private val _editProductSizes = MutableStateFlow<List<ProductSize>>(emptyList())
    val editProductSizes: StateFlow<List<ProductSize>> = _editProductSizes

    // Modifier groups for the product add/edit dialog
    private val _editProductModGroupIds = MutableStateFlow<List<Int>>(emptyList())
    val editProductModGroupIds: StateFlow<List<Int>> = _editProductModGroupIds

    fun loadSizesForEditDialog(productId: Int) {
        viewModelScope.launch {
            try { _editProductSizes.value = productRepo.getProductSizes(productId) }
            catch (_: Exception) { _editProductSizes.value = emptyList() }
        }
    }

    fun clearEditProductSizes() { _editProductSizes.value = emptyList() }

    fun loadModGroupsForEditDialog(productId: Int) {
        viewModelScope.launch {
            try {
                _editProductModGroupIds.value = productRepo.getAssignedModifierGroupIds(productId)
                if (_allModGroups.value.isEmpty()) {
                    _allModGroups.value = productRepo.getAllModifierGroups()
                }
            } catch (_: Exception) { _editProductModGroupIds.value = emptyList() }
        }
    }

    fun clearEditProductModGroupIds() { _editProductModGroupIds.value = emptyList() }

    fun addSizeFromEditDialog(productId: Int, sizeName: String, price: Double, costPrice: Double = 0.0) {
        viewModelScope.launch {
            try {
                productRepo.addProductSize(productId, sizeName, price, costPrice)
                _editProductSizes.value = productRepo.getProductSizes(productId)
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteSizeFromEditDialog(productId: Int, sizeId: Int) {
        viewModelScope.launch {
            try {
                productRepo.deleteProductSize(sizeId)
                _editProductSizes.value = productRepo.getProductSizes(productId)
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    private fun reloadSizes() {
        val pid = _selectedProduct.value?.productId ?: return
        viewModelScope.launch {
            try { _sizes.value = productRepo.getProductSizes(pid) }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    private fun reloadModGroups() {
        val pid = _selectedProduct.value?.productId ?: return
        viewModelScope.launch {
            try {
                _productModGroups.value = productRepo.getModifierGroups(pid)
                _allModGroups.value     = productRepo.getAllModifierGroups()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun addSize(sizeName: String, price: Double, costPrice: Double = 0.0) {
        val pid = _selectedProduct.value?.productId ?: return
        viewModelScope.launch {
            try { productRepo.addProductSize(pid, sizeName, price, costPrice); reloadSizes() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateSize(sizeId: Int, sizeName: String, price: Double, costPrice: Double = 0.0) {
        viewModelScope.launch {
            try { productRepo.updateProductSize(sizeId, sizeName, price, costPrice); reloadSizes() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteSize(sizeId: Int) {
        viewModelScope.launch {
            try { productRepo.deleteProductSize(sizeId); reloadSizes() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun addModifierGroup(groupName: String, minSel: Int, maxSel: Int, isRequired: Boolean) {
        val pid = _selectedProduct.value?.productId ?: return
        viewModelScope.launch {
            try {
                val gid = productRepo.addModifierGroup(groupName, minSel, maxSel, isRequired)
                if (gid > 0) productRepo.linkGroupToProduct(pid, gid)
                reloadModGroups()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateModifierGroup(groupId: Int, groupName: String, minSel: Int, maxSel: Int, isRequired: Boolean) {
        viewModelScope.launch {
            try { productRepo.updateModifierGroup(groupId, groupName, minSel, maxSel, isRequired); reloadModGroups() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun unlinkModifierGroup(groupId: Int) {
        val pid = _selectedProduct.value?.productId ?: return
        viewModelScope.launch {
            try { productRepo.unlinkGroupFromProduct(pid, groupId); reloadModGroups() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun linkExistingGroup(groupId: Int) {
        val pid = _selectedProduct.value?.productId ?: return
        viewModelScope.launch {
            try { productRepo.linkGroupToProduct(pid, groupId); reloadModGroups() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun addModifier(groupId: Int, name: String, price: Double, stockItemId: Int = 0) {
        viewModelScope.launch {
            try { productRepo.addModifier(groupId, name, price, stockItemId); reloadModGroups() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateModifier(modifierId: Int, name: String, price: Double, stockItemId: Int = 0) {
        viewModelScope.launch {
            try { productRepo.updateModifier(modifierId, name, price, stockItemId); reloadModGroups() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteModifier(modifierId: Int) {
        viewModelScope.launch {
            try { productRepo.deleteModifier(modifierId); reloadModGroups() }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun clearMessage() { _message.value = null }

    fun bulkUpdatePrices(categoryId: Int?, changePercent: Double) {
        if (changePercent == 0.0) return
        viewModelScope.launch {
            try {
                productRepo.bulkUpdatePrices(categoryId, changePercent)
                load()
                val catLabel = if (categoryId == null) "all products" else
                    _categories.value.firstOrNull { it.categoryId == categoryId }?.categoryName ?: "category"
                _message.value = "Prices updated for $catLabel by %.1f%%".format(changePercent)
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    // ── Schedule management ───────────────────────────────────────────────────
    fun openScheduleSheet(product: Product) {
        _scheduleProduct.value = product
        viewModelScope.launch {
            try { _schedules.value = scheduleRepo.getSchedulesForProduct(product.productId) }
            catch (e: Exception) { _message.value = e.message }
        }
    }

    fun closeScheduleSheet() { _scheduleProduct.value = null }

    fun addSchedule(label: String, dayOfWeek: Int, startTime: String, endTime: String) {
        val pid = _scheduleProduct.value?.productId ?: return
        viewModelScope.launch {
            try {
                scheduleRepo.addSchedule(pid, label, dayOfWeek, startTime, endTime)
                _schedules.value = scheduleRepo.getSchedulesForProduct(pid)
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateSchedule(scheduleId: Int, label: String, dayOfWeek: Int, startTime: String, endTime: String) {
        val pid = _scheduleProduct.value?.productId ?: return
        viewModelScope.launch {
            try {
                scheduleRepo.updateSchedule(scheduleId, label, dayOfWeek, startTime, endTime)
                _schedules.value = scheduleRepo.getSchedulesForProduct(pid)
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteSchedule(scheduleId: Int) {
        val pid = _scheduleProduct.value?.productId ?: return
        viewModelScope.launch {
            try {
                scheduleRepo.deleteSchedule(scheduleId)
                _schedules.value = scheduleRepo.getSchedulesForProduct(pid)
            } catch (e: Exception) { _message.value = e.message }
        }
    }
}
