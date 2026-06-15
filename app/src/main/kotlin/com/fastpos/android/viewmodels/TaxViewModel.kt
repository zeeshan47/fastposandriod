package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.TaxRate
import com.fastpos.android.data.repositories.TaxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaxViewModel @Inject constructor(private val repo: TaxRepository) : ViewModel() {

    private val _taxes   = MutableStateFlow<List<TaxRate>>(emptyList())
    private val _message = MutableStateFlow<String?>(null)
    private val _loading = MutableStateFlow(false)

    val taxes:   StateFlow<List<TaxRate>> = _taxes.asStateFlow()
    val message: StateFlow<String?>       = _message.asStateFlow()
    val loading: StateFlow<Boolean>       = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repo.initSchema() }
            load()
        }
    }

    fun load() = viewModelScope.launch {
        _loading.value = true
        runCatching { _taxes.value = repo.getAllTaxes() }
            .onFailure { _message.value = "Load failed: ${it.message}" }
        _loading.value = false
    }

    fun addTax(name: String, percent: Double) = viewModelScope.launch {
        runCatching {
            repo.addTax(name, percent)
            load()
            _message.value = "Tax rate added"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun updateTax(taxId: Int, name: String, percent: Double, isActive: Boolean) = viewModelScope.launch {
        runCatching {
            repo.updateTax(taxId, name, percent, isActive)
            _taxes.value = _taxes.value.map {
                if (it.taxId == taxId) it.copy(taxName = name, taxPercent = percent, isActive = isActive) else it
            }
            _message.value = "Updated"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun deleteTax(taxId: Int) = viewModelScope.launch {
        runCatching {
            val ok = repo.deleteTax(taxId)
            if (ok) {
                _taxes.value = _taxes.value.filter { it.taxId != taxId }
                _message.value = "Deleted"
            } else {
                _message.value = "Cannot delete: tax is assigned to products"
            }
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun clearMessage() { _message.value = null }
}
