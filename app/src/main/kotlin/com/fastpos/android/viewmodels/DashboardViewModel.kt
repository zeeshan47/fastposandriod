package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.DashboardStats
import com.fastpos.android.data.repositories.DashboardRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepo: DashboardRepository,
    val session: SessionManager
) : ViewModel() {

    private val _stats      = MutableStateFlow(DashboardStats())
    private val _isLoading  = MutableStateFlow(false)
    private val _error      = MutableStateFlow<String?>(null)

    val stats:     StateFlow<DashboardStats> = _stats
    val isLoading: StateFlow<Boolean>        = _isLoading
    val error:     StateFlow<String?>        = _error

    init {
        loadStats()
        viewModelScope.launch {
            while (isActive) {
                delay(session.pollIntervalMs)
                if (!_isLoading.value) loadStats()
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                _stats.value = dashboardRepo.getDashboardStats(session.settings.value.currencySymbol)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load dashboard."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
