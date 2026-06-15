package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.ModifierGroup
import com.fastpos.android.data.models.Product
import com.fastpos.android.data.repositories.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModifierViewModel @Inject constructor(
    private val repo: ProductRepository
) : ViewModel() {

    private val _groups          = MutableStateFlow<List<ModifierGroup>>(emptyList())
    private val _selectedGroup   = MutableStateFlow<ModifierGroup?>(null)
    private val _stockItems      = MutableStateFlow<List<Product>>(emptyList())
    private val _isLoading       = MutableStateFlow(false)
    private val _message         = MutableStateFlow<String?>(null)

    val groups:        StateFlow<List<ModifierGroup>> = _groups
    val selectedGroup: StateFlow<ModifierGroup?>      = _selectedGroup
    val stockItems:    StateFlow<List<Product>>       = _stockItems
    val isLoading:     StateFlow<Boolean>             = _isLoading
    val message:       StateFlow<String?>             = _message

    init { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _groups.value     = repo.getAllModifierGroups()
                _stockItems.value = try { repo.getStockManagedProducts() } catch (_: Exception) { emptyList() }
                val sel = _selectedGroup.value
                if (sel != null) {
                    _selectedGroup.value = _groups.value.find { it.modifierGroupId == sel.modifierGroupId }
                }
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectGroup(group: ModifierGroup) { _selectedGroup.value = group }
    fun clearSelection() { _selectedGroup.value = null }

    fun addGroup(name: String, minSel: Int, maxSel: Int, isRequired: Boolean) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                repo.addModifierGroup(name.trim(), minSel, maxSel, isRequired)
                _message.value = "$name added"
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateGroup(groupId: Int, name: String, minSel: Int, maxSel: Int, isRequired: Boolean) {
        viewModelScope.launch {
            try {
                repo.updateModifierGroup(groupId, name.trim(), minSel, maxSel, isRequired)
                _message.value = "Updated"
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteGroup(groupId: Int) {
        viewModelScope.launch {
            try {
                repo.deleteModifierGroup(groupId)
                if (_selectedGroup.value?.modifierGroupId == groupId) _selectedGroup.value = null
                _message.value = "Group deleted"
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun addModifier(groupId: Int, name: String, price: Double, stockItemId: Int = 0) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                repo.addModifier(groupId, name.trim(), price, stockItemId)
                _message.value = "$name added"
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun updateModifier(modifierId: Int, name: String, price: Double, stockItemId: Int = 0) {
        viewModelScope.launch {
            try {
                repo.updateModifier(modifierId, name.trim(), price, stockItemId)
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun deleteModifier(modifierId: Int) {
        viewModelScope.launch {
            try {
                repo.deleteModifier(modifierId)
                _message.value = "Option deleted"
                load()
            } catch (e: Exception) { _message.value = e.message }
        }
    }

    fun clearMessage() { _message.value = null }
}
