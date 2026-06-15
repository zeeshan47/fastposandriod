package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.*
import com.fastpos.android.data.repositories.RawMaterialRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RawMaterialViewModel @Inject constructor(
    private val repo: RawMaterialRepository,
    val session: SessionManager
) : ViewModel() {

    // ── Materials ─────────────────────────────────────────────────────────────
    private val _materials       = MutableStateFlow<List<RawMaterial>>(emptyList())
    private val _search          = MutableStateFlow("")
    private val _isLoading       = MutableStateFlow(false)
    private val _message         = MutableStateFlow<String?>(null)

    val materials: StateFlow<List<RawMaterial>> = _materials
    val search:    StateFlow<String>            = _search
    val isLoading: StateFlow<Boolean>           = _isLoading
    val message:   StateFlow<String?>           = _message

    // ── Recipes ─────────────���──────────────────────────��──────────────────────
    private val _products         = MutableStateFlow<List<Triple<Int, String, String>>>(emptyList())
    private val _selectedProduct  = MutableStateFlow<Triple<Int, String, String>?>(null)
    private val _recipe           = MutableStateFlow<List<RecipeItem>>(emptyList())
    private val _recipeCost       = MutableStateFlow(0.0)

    val products:        StateFlow<List<Triple<Int, String, String>>>  = _products
    val selectedProduct: StateFlow<Triple<Int, String, String>?>       = _selectedProduct
    val recipe:          StateFlow<List<RecipeItem>>         = _recipe
    val recipeCost:      StateFlow<Double>                   = _recipeCost

    init {
        viewModelScope.launch {
            try { repo.initSchema() } catch (_: Exception) {}
            loadMaterials()
            loadProducts()
        }
    }

    fun loadMaterials(query: String = _search.value) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _materials.value = repo.getAllMaterials(query)
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearch(q: String) {
        _search.value = q
        loadMaterials(q)
    }

    private fun loadProducts() {
        viewModelScope.launch {
            try { _products.value = repo.getProductsWithRecipes() } catch (_: Exception) {}
        }
    }

    fun selectProduct(product: Triple<Int, String, String>?) {
        _selectedProduct.value = product
        if (product != null) loadRecipe(product.first)
        else { _recipe.value = emptyList(); _recipeCost.value = 0.0 }
    }

    private fun loadRecipe(productId: Int) {
        viewModelScope.launch {
            try {
                _recipe.value     = repo.getRecipeForProduct(productId)
                _recipeCost.value = repo.getRecipeCost(productId)
            } catch (e: Exception) {
                _message.value = e.message
            }
        }
    }

    fun addMaterial(name: String, unit: String, stock: Double, minStock: Double, costPerUnit: Double) {
        viewModelScope.launch {
            try {
                repo.addMaterial(name, unit, stock, minStock, costPerUnit, session.userId)
                loadMaterials()
                _message.value = "'$name' added."
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun updateMaterial(materialId: Int, name: String, unit: String, minStock: Double, costPerUnit: Double) {
        viewModelScope.launch {
            try {
                repo.updateMaterial(materialId, name, unit, minStock, costPerUnit)
                loadMaterials()
                _message.value = "Updated."
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun adjustStock(materialId: Int, delta: Double, rate: Double = 0.0, remarks: String = "") {
        viewModelScope.launch {
            try {
                repo.adjustStock(materialId, delta, rate = rate, remarks = remarks)
                _materials.value = _materials.value.map { m ->
                    if (m.materialId == materialId) m.copy(currentStock = (m.currentStock + delta).coerceAtLeast(0.0)) else m
                }
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun setStock(materialId: Int, stock: Double) {
        viewModelScope.launch {
            try {
                repo.setStock(materialId, stock)
                _materials.value = _materials.value.map { m ->
                    if (m.materialId == materialId) m.copy(currentStock = stock) else m
                }
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun deleteMaterial(materialId: Int) {
        viewModelScope.launch {
            try {
                repo.deleteMaterial(materialId)
                _materials.value = _materials.value.filter { it.materialId != materialId }
                _message.value = "Deleted."
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun upsertRecipeItem(materialId: Int, quantity: Double) {
        val productId = _selectedProduct.value?.first ?: return
        viewModelScope.launch {
            try {
                repo.upsertRecipeItem(productId, materialId, quantity)
                loadRecipe(productId)
                _message.value = "Recipe updated."
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun deleteRecipeItem(recipeId: Int) {
        viewModelScope.launch {
            try {
                repo.deleteRecipeItem(recipeId)
                _recipe.value = _recipe.value.filter { it.recipeId != recipeId }
                _selectedProduct.value?.first?.let { _recipeCost.value = repo.getRecipeCost(it) }
                _message.value = "Removed."
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            }
        }
    }

    fun clearMessage() { _message.value = null }

    // ── Stock Ledger ──────────────────────────────────────────────────────────

    private val _ledgerMaterial    = MutableStateFlow<RawMaterial?>(null)
    private val _ledgerFromDate    = MutableStateFlow<java.util.Date>(thirtyDaysAgo())
    private val _ledgerToDate      = MutableStateFlow<java.util.Date>(java.util.Date())
    private val _ledgerRows        = MutableStateFlow<List<InventoryLedger>>(emptyList())
    private val _ledgerOpening     = MutableStateFlow(0.0)
    private val _ledgerTotalIn     = MutableStateFlow(0.0)
    private val _ledgerTotalOut    = MutableStateFlow(0.0)
    private val _ledgerClosing     = MutableStateFlow(0.0)

    val ledgerMaterial: StateFlow<RawMaterial?> = _ledgerMaterial
    val ledgerFromDate: StateFlow<java.util.Date> = _ledgerFromDate
    val ledgerToDate:   StateFlow<java.util.Date> = _ledgerToDate
    val ledgerRows:     StateFlow<List<InventoryLedger>> = _ledgerRows
    val ledgerOpening:  StateFlow<Double> = _ledgerOpening
    val ledgerTotalIn:  StateFlow<Double> = _ledgerTotalIn
    val ledgerTotalOut: StateFlow<Double> = _ledgerTotalOut
    val ledgerClosing:  StateFlow<Double> = _ledgerClosing

    fun setLedgerMaterial(m: RawMaterial?) { _ledgerMaterial.value = m }
    fun setLedgerFromDate(d: java.util.Date) { _ledgerFromDate.value = d }
    fun setLedgerToDate(d: java.util.Date)   { _ledgerToDate.value = d }

    fun runLedger() = viewModelScope.launch {
        val mat = _ledgerMaterial.value ?: run { _message.value = "Select a material first"; return@launch }
        _isLoading.value = true
        try {
            val rows    = repo.getLedgerRows(mat.materialId, _ledgerFromDate.value, _ledgerToDate.value)
            val opening = repo.getOpeningBalance(mat.materialId, _ledgerFromDate.value)
            _ledgerRows.value    = rows
            _ledgerOpening.value = opening
            _ledgerTotalIn.value  = rows.sumOf { it.inQty }
            _ledgerTotalOut.value = rows.sumOf { it.outQty }
            _ledgerClosing.value  = opening + _ledgerTotalIn.value - _ledgerTotalOut.value
        } catch (e: Exception) {
            _message.value = "Ledger error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    // ── Wastage ───────────────────────────────────────────────────────────────

    private val _wasteEntries     = MutableStateFlow<List<WasteEntry>>(emptyList())
    private val _wasteItems       = MutableStateFlow<List<WasteEntryItem>>(emptyList())
    private val _wasteSelected    = MutableStateFlow<WasteEntry?>(null)
    private val _wasteFilterFrom  = MutableStateFlow<java.util.Date>(thirtyDaysAgo())
    private val _wasteFilterTo    = MutableStateFlow<java.util.Date>(java.util.Date())
    private val _wasteFormItems   = MutableStateFlow<List<WasteEntryItem>>(emptyList())
    private val _wasteFormDate    = MutableStateFlow<java.util.Date>(java.util.Date())
    private val _wasteFormNotes   = MutableStateFlow("")

    val wasteEntries:    StateFlow<List<WasteEntry>>     = _wasteEntries
    val wasteItems:      StateFlow<List<WasteEntryItem>> = _wasteItems
    val wasteSelected:   StateFlow<WasteEntry?>          = _wasteSelected
    val wasteFilterFrom: StateFlow<java.util.Date>       = _wasteFilterFrom
    val wasteFilterTo:   StateFlow<java.util.Date>       = _wasteFilterTo
    val wasteFormItems:  StateFlow<List<WasteEntryItem>> = _wasteFormItems
    val wasteFormDate:   StateFlow<java.util.Date>       = _wasteFormDate
    val wasteFormNotes:  StateFlow<String>               = _wasteFormNotes

    fun setWasteFilterFrom(d: java.util.Date) { _wasteFilterFrom.value = d }
    fun setWasteFilterTo(d: java.util.Date)   { _wasteFilterTo.value = d }
    fun setWasteFormDate(d: java.util.Date)   { _wasteFormDate.value = d }
    fun setWasteFormNotes(v: String)          { _wasteFormNotes.value = v }

    fun loadWasteEntries() = viewModelScope.launch {
        runCatching { _wasteEntries.value = repo.getWasteEntries(_wasteFilterFrom.value, _wasteFilterTo.value) }
    }

    fun selectWasteEntry(e: WasteEntry?) {
        _wasteSelected.value = e
        if (e != null) {
            viewModelScope.launch { runCatching { _wasteItems.value = repo.getWasteItems(e.wasteId) } }
        } else {
            _wasteItems.value = emptyList()
        }
    }

    fun addWasteFormItem(item: WasteEntryItem) { _wasteFormItems.value = _wasteFormItems.value + item }
    fun removeWasteFormItem(i: Int) {
        _wasteFormItems.value = _wasteFormItems.value.toMutableList().also { it.removeAt(i) }
    }
    fun clearWasteForm() {
        _wasteFormItems.value = emptyList()
        _wasteFormDate.value  = java.util.Date()
        _wasteFormNotes.value = ""
    }

    fun saveWasteEntry() = viewModelScope.launch {
        val items = _wasteFormItems.value
        if (items.isEmpty()) { _message.value = "Add at least one item"; return@launch }
        _isLoading.value = true
        runCatching {
            repo.saveWasteEntry(_wasteFormDate.value, _wasteFormNotes.value, items)
            clearWasteForm()
            loadWasteEntries()
            loadMaterials()
            _message.value = "Waste entry saved"
        }.onFailure { _message.value = "Failed: ${it.message}" }
        _isLoading.value = false
    }

    fun deleteWasteEntry(wasteId: Int) = viewModelScope.launch {
        runCatching {
            repo.deleteWasteEntry(wasteId)
            _wasteEntries.value = _wasteEntries.value.filter { it.wasteId != wasteId }
            selectWasteEntry(null)
            loadMaterials()
            _message.value = "Waste entry deleted"
        }.onFailure { _message.value = "Failed: ${it.message}" }
    }

    // ── Stock Valuation ───────────────────────────────────────────────────────
    private val _valuationItems = MutableStateFlow<List<RawMaterial>>(emptyList())
    private val _reorderItems   = MutableStateFlow<List<RawMaterial>>(emptyList())
    private val _valuationLoading = MutableStateFlow(false)

    val valuationItems:   StateFlow<List<RawMaterial>> = _valuationItems
    val reorderItems:     StateFlow<List<RawMaterial>> = _reorderItems
    val valuationLoading: StateFlow<Boolean>           = _valuationLoading

    fun loadStockValuation() {
        viewModelScope.launch {
            _valuationLoading.value = true
            try {
                _valuationItems.value = repo.getStockValuation()
                _reorderItems.value   = repo.getReorderList()
            } catch (e: Exception) {
                _message.value = "Failed to load valuation: ${e.message}"
            } finally {
                _valuationLoading.value = false
            }
        }
    }

    private fun thirtyDaysAgo(): java.util.Date {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -29)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.time
    }
}
