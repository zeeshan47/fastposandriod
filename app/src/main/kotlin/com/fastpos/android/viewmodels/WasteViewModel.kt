package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Product
import com.fastpos.android.data.models.WasteEntry
import com.fastpos.android.data.models.WasteEntryItem
import com.fastpos.android.data.repositories.ProductRepository
import com.fastpos.android.data.repositories.WasteRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class WasteViewModel @Inject constructor(
    private val repo:        WasteRepository,
    private val productRepo: ProductRepository,
    val session:             SessionManager
) : ViewModel() {

    private val _entries       = MutableStateFlow<List<WasteEntry>>(emptyList())
    private val _selectedItems = MutableStateFlow<List<WasteEntryItem>>(emptyList())
    private val _stockProducts = MutableStateFlow<List<Product>>(emptyList())
    private val _isLoading     = MutableStateFlow(false)
    private val _message       = MutableStateFlow<String?>(null)
    private val _pendingItems  = MutableStateFlow<List<WasteEntryItem>>(emptyList())
    private val _fromDate      = MutableStateFlow(monthStart())
    private val _toDate        = MutableStateFlow(Date())

    val entries:       StateFlow<List<WasteEntry>>     = _entries
    val selectedItems: StateFlow<List<WasteEntryItem>> = _selectedItems
    val stockProducts: StateFlow<List<Product>>        = _stockProducts
    val isLoading:     StateFlow<Boolean>              = _isLoading
    val message:       StateFlow<String?>              = _message
    val pendingItems:  StateFlow<List<WasteEntryItem>> = _pendingItems
    val fromDate:      StateFlow<Date>                 = _fromDate
    val toDate:        StateFlow<Date>                 = _toDate

    init {
        loadEntries()
        loadStockProducts()
    }

    fun setFromDate(d: Date) { _fromDate.value = d; loadEntries() }
    fun setToDate(d: Date)   { _toDate.value = d;   loadEntries() }

    fun loadEntries() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _entries.value = repo.getEntries(_fromDate.value, _toDate.value)
            } catch (e: Exception) {
                _message.value = "Failed to load: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadStockProducts() {
        viewModelScope.launch {
            _stockProducts.value = try { productRepo.getStockManagedProducts() } catch (_: Exception) { emptyList() }
        }
    }

    fun loadEntryItems(wasteId: Int) {
        viewModelScope.launch {
            _selectedItems.value = repo.getEntryItems(wasteId)
        }
    }

    fun addPendingItem(item: WasteEntryItem) {
        _pendingItems.value = _pendingItems.value + item
    }

    fun removePendingItem(index: Int) {
        _pendingItems.value = _pendingItems.value.toMutableList().also { it.removeAt(index) }
    }

    fun clearPendingItems() {
        _pendingItems.value = emptyList()
    }

    fun saveEntry(notes: String) {
        val items = _pendingItems.value
        if (items.isEmpty()) { _message.value = "Add at least one item."; return }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repo.saveEntry(notes, items)
                _pendingItems.value = emptyList()
                _message.value = "Waste entry saved."
                loadEntries()
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteEntry(wasteId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repo.deleteEntry(wasteId)
                _entries.value = _entries.value.filter { it.wasteId != wasteId }
                _message.value = "Entry deleted."
            } catch (e: Exception) {
                _message.value = "Failed: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessage() { _message.value = null }

    private fun monthStart(): Date = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time
}
