package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.StockItem
import com.fastpos.android.data.repositories.InventoryRepository
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventoryRepo: InventoryRepository,
    private val prefs: PreferencesManager,
    val session: SessionManager
) : ViewModel() {

    private val _allItems  = MutableStateFlow<List<StockItem>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _message   = MutableStateFlow<String?>(null)
    private val _search    = MutableStateFlow("")
    private val _filter    = MutableStateFlow("All")

    val isLoading: StateFlow<Boolean>  = _isLoading
    val message:   StateFlow<String?>  = _message
    val search:    StateFlow<String>   = _search
    val filter:    StateFlow<String>   = _filter

    val items: StateFlow<List<StockItem>> = combine(_allItems, _filter) { all, f ->
        when (f) {
            "Low Stock"    -> all.filter { it.currentStock > 0 && it.currentStock <= it.minimumStock }
            "Out of Stock" -> all.filter { it.currentStock <= 0 }
            else           -> all
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            try { inventoryRepo.initSchema() } catch (_: Exception) {}
            load()
        }
    }

    fun setSearch(q: String) { _search.value = q; load() }
    fun setFilter(f: String) { _filter.value = f }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _allItems.value = inventoryRepo.getStockItems(_search.value)
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addStock(productId: Int, amount: Double, remarks: String = "") {
        if (amount <= 0) return
        viewModelScope.launch {
            try {
                inventoryRepo.adjustStock(productId, amount, session.userId, remarks)
                _message.value = "Stock added."
                load()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun removeStock(productId: Int, amount: Double, remarks: String = "") {
        if (amount <= 0) return
        viewModelScope.launch {
            try {
                inventoryRepo.adjustStock(productId, -amount, session.userId, remarks)
                _message.value = "Stock removed."
                load()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun setStockLevel(productId: Int, level: Double, remarks: String = "") {
        if (level < 0) return
        viewModelScope.launch {
            try {
                inventoryRepo.setStockLevel(productId, level, session.userId, remarks)
                _message.value = "Stock level set."
                load()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun setMinimumStock(productId: Int, minimum: Double) {
        if (minimum < 0) return
        viewModelScope.launch {
            try {
                inventoryRepo.setMinimumStock(productId, minimum)
                _message.value = "Minimum stock level updated."
                load()
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun printStockReport() {
        val currentItems = items.value
        if (currentItems.isEmpty()) { _message.value = "No items to print."; return }
        viewModelScope.launch {
            val address = prefs.savedPrinterAddress.first()
            if (address.isBlank()) { _message.value = "No printer saved. Configure in Settings."; return@launch }
            val companyName = session.settings.value.companyName
            val result = BluetoothPrinterHelper.printStockReport(address, currentItems, companyName)
            _message.value = if (result.isSuccess) "Stock report printed." else "Print failed: ${result.exceptionOrNull()?.message}"
        }
    }

    fun clearMessage() { _message.value = null }
}
