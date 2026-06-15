package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.InventoryLedger
import com.fastpos.android.data.models.Product
import com.fastpos.android.data.repositories.ProductRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ProductLedgerViewModel @Inject constructor(
    private val productRepo: ProductRepository,
    val session: SessionManager
) : ViewModel() {

    private val _products        = MutableStateFlow<List<Product>>(emptyList())
    private val _selectedProduct = MutableStateFlow<Product?>(null)
    private val _fromDate        = MutableStateFlow(thirtyDaysAgo())
    private val _toDate          = MutableStateFlow(Date())
    private val _ledgerRows      = MutableStateFlow<List<InventoryLedger>>(emptyList())
    private val _opening         = MutableStateFlow(0.0)
    private val _totalIn         = MutableStateFlow(0.0)
    private val _totalOut        = MutableStateFlow(0.0)
    private val _closing         = MutableStateFlow(0.0)
    private val _isLoading       = MutableStateFlow(false)
    private val _message         = MutableStateFlow<String?>(null)

    val products:        StateFlow<List<Product>>         = _products
    val selectedProduct: StateFlow<Product?>              = _selectedProduct
    val fromDate:        StateFlow<Date>                  = _fromDate
    val toDate:          StateFlow<Date>                  = _toDate
    val ledgerRows:      StateFlow<List<InventoryLedger>> = _ledgerRows
    val opening:         StateFlow<Double>                = _opening
    val totalIn:         StateFlow<Double>                = _totalIn
    val totalOut:        StateFlow<Double>                = _totalOut
    val closing:         StateFlow<Double>                = _closing
    val isLoading:       StateFlow<Boolean>               = _isLoading
    val message:         StateFlow<String?>               = _message

    init {
        viewModelScope.launch {
            _products.value = productRepo.getStockManagedProducts()
        }
    }

    fun selectProduct(product: Product?) { _selectedProduct.value = product }
    fun setFromDate(d: Date) { _fromDate.value = d }
    fun setToDate(d: Date)   { _toDate.value   = d }

    fun setToday() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        _fromDate.value = cal.time
        _toDate.value   = Date()
    }

    fun setWeek() {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        _fromDate.value = cal.time
        _toDate.value   = Date()
    }

    fun setMonth() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        _fromDate.value = cal.time
        _toDate.value   = Date()
    }

    fun runLedger() {
        val product = _selectedProduct.value ?: run { _message.value = "Select a product first"; return }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val rows    = productRepo.getProductLedgerRows(product.productId, _fromDate.value, _toDate.value)
                val opening = productRepo.getProductLedgerOpeningBalance(product.productId, _fromDate.value)
                _ledgerRows.value = rows
                _opening.value    = opening
                _totalIn.value    = rows.sumOf { it.inQty }
                _totalOut.value   = rows.sumOf { it.outQty }
                _closing.value    = opening + _totalIn.value - _totalOut.value
            } catch (e: Exception) {
                _message.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearMessage() { _message.value = null }

    private fun thirtyDaysAgo(): Date = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -29)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time
}
