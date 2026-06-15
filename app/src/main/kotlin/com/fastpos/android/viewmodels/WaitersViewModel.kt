package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Waiter
import com.fastpos.android.data.repositories.WaiterRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WaitersViewModel @Inject constructor(
    private val repo:    WaiterRepository,
    private val session: SessionManager
) : ViewModel() {

    private val _waiters   = MutableStateFlow<List<Waiter>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _message   = MutableStateFlow<String?>(null)
    private val _employees = MutableStateFlow<List<Pair<Int, String>>>(emptyList())

    val waiters:   StateFlow<List<Waiter>>          = _waiters
    val isLoading: StateFlow<Boolean>               = _isLoading
    val message:   StateFlow<String?>               = _message
    val employees: StateFlow<List<Pair<Int,String>>> = _employees

    init { load() }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _waiters.value   = repo.getAllWaiters()
                _employees.value = repo.getActiveEmployees()
            }
            catch (e: Exception) { _message.value = "Failed: ${e.message}" }
            finally { _isLoading.value = false }
        }
    }

    fun addWaiter(name: String, phone: String, linkedEmployeeId: Int? = null, areaId: Int? = null) {
        viewModelScope.launch {
            try {
                repo.addWaiter(name, phone, linkedEmployeeId, areaId, createdBy = session.userId)
                load()
                _message.value = "'$name' added."
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun updateWaiter(waiterId: Int, name: String, phone: String, isActive: Boolean, linkedEmployeeId: Int? = null, areaId: Int? = null) {
        viewModelScope.launch {
            try {
                repo.updateWaiter(waiterId, name, phone, isActive, linkedEmployeeId, areaId)
                load()
                _message.value = "Updated."
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun deleteWaiter(waiterId: Int) {
        viewModelScope.launch {
            try {
                repo.deleteWaiter(waiterId)
                _waiters.value = _waiters.value.filter { it.waiterId != waiterId }
                _message.value = "Waiter removed."
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun clearMessage() { _message.value = null }
}
