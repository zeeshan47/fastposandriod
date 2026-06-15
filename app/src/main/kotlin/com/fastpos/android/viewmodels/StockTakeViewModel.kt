package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.StockTake
import com.fastpos.android.data.models.StockTakeItem
import com.fastpos.android.data.repositories.StockTakeRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockTakeViewModel @Inject constructor(
    private val repo: StockTakeRepository,
    val session: SessionManager
) : ViewModel() {

    private val _isLoading   = MutableStateFlow(false)
    private val _message     = MutableStateFlow<String?>(null)
    private val _openTake    = MutableStateFlow<StockTake?>(null)
    private val _takeItems   = MutableStateFlow<List<StockTakeItem>>(emptyList())
    private val _history     = MutableStateFlow<List<StockTake>>(emptyList())
    private val _isSaving    = MutableStateFlow(false)

    val isLoading: StateFlow<Boolean>            = _isLoading
    val message:   StateFlow<String?>            = _message
    val openTake:  StateFlow<StockTake?>         = _openTake
    val takeItems: StateFlow<List<StockTakeItem>> = _takeItems
    val history:   StateFlow<List<StockTake>>    = _history
    val isSaving:  StateFlow<Boolean>            = _isSaving

    // local mutable map of itemId → typed actual qty (String for text field)
    private val _actualInputs = MutableStateFlow<Map<Int, String>>(emptyMap())
    val actualInputs: StateFlow<Map<Int, String>> = _actualInputs

    init {
        load()
    }

    fun load() = viewModelScope.launch {
        _isLoading.value = true
        try {
            repo.initSchema()
            val open = repo.getOpenStockTake()
            _openTake.value = open
            if (open != null) {
                val items = repo.getStockTakeItems(open.stockTakeId)
                _takeItems.value = items
                _actualInputs.value = items.associate { it.itemId to formatQty(it.actualQty) }
            } else {
                _takeItems.value = emptyList()
                _actualInputs.value = emptyMap()
            }
            _history.value = repo.getStockTakeHistory()
        } catch (e: Exception) {
            _message.value = "Load failed: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun startNewStockTake(notes: String = "") = viewModelScope.launch {
        if (_openTake.value != null) { _message.value = "A stock take is already open"; return@launch }
        _isLoading.value = true
        try {
            val userId = session.currentUser.value?.userId
            val id = repo.startNewStockTake(notes, userId)
            if (id > 0) { load() } else { _message.value = "Could not create stock take" }
        } catch (e: Exception) {
            _message.value = "Error: ${e.message}"
        } finally {
            _isLoading.value = false
        }
    }

    fun setActualQty(itemId: Int, input: String) {
        _actualInputs.value = _actualInputs.value.toMutableMap().apply { put(itemId, input) }
    }

    fun saveActualQty(itemId: Int) = viewModelScope.launch {
        val input = _actualInputs.value[itemId] ?: return@launch
        val qty = input.toDoubleOrNull() ?: return@launch
        try {
            repo.updateActualQty(itemId, qty)
            _takeItems.value = _takeItems.value.map { if (it.itemId == itemId) it.copy(actualQty = qty) else it }
        } catch (_: Exception) {}
    }

    fun finalizeStockTake() = viewModelScope.launch {
        val take = _openTake.value ?: return@launch
        _isSaving.value = true
        try {
            // persist any pending inputs first
            for ((itemId, input) in _actualInputs.value) {
                val qty = input.toDoubleOrNull() ?: continue
                repo.updateActualQty(itemId, qty)
            }
            repo.finalizeStockTake(take.stockTakeId)
            _message.value = "Stock Take finalized — stock levels updated"
            load()
        } catch (e: Exception) {
            _message.value = "Finalize failed: ${e.message}"
        } finally {
            _isSaving.value = false
        }
    }

    fun cancelStockTake() = viewModelScope.launch {
        val take = _openTake.value ?: return@launch
        try {
            repo.cancelStockTake(take.stockTakeId)
            _message.value = "Stock Take cancelled"
            load()
        } catch (e: Exception) {
            _message.value = "Error: ${e.message}"
        }
    }

    fun clearMessage() { _message.value = null }

    private fun formatQty(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else "%.3f".format(d).trimEnd('0')
}
