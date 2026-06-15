package com.fastpos.android.utils

import com.fastpos.android.data.models.CompanySettings
import com.fastpos.android.data.models.Shift
import com.fastpos.android.data.models.User
import com.fastpos.android.data.models.Branch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor() {

    private val _currentUser       = MutableStateFlow<User?>(null)
    private val _currentShift      = MutableStateFlow<Shift?>(null)
    private val _settings          = MutableStateFlow(CompanySettings())
    private val _permissions       = MutableStateFlow<Set<String>>(emptySet())
    private val _currentBranchId   = MutableStateFlow(1)
    private val _currentBranchName = MutableStateFlow("Main Branch")

    val currentUser:       StateFlow<User?>           = _currentUser
    val currentShift:      StateFlow<Shift?>          = _currentShift
    val settings:          StateFlow<CompanySettings> = _settings
    val permissions:       StateFlow<Set<String>>     = _permissions
    val currentBranchId:   StateFlow<Int>             = _currentBranchId
    val currentBranchName: StateFlow<String>          = _currentBranchName

    fun login(user: User, perms: List<String>) {
        _currentUser.value  = user
        _permissions.value  = perms.toSet()
    }

    fun logout() {
        _currentUser.value       = null
        _currentShift.value      = null
        _permissions.value       = emptySet()
        _currentBranchId.value   = 1
        _currentBranchName.value = "Main Branch"
    }

    fun setShift(shift: Shift?)  { _currentShift.value = shift }
    fun setSettings(s: CompanySettings) { _settings.value = s }
    fun setBranch(branchId: Int, branchName: String) {
        _currentBranchId.value   = branchId
        _currentBranchName.value = branchName
    }

    fun hasPermission(key: String): Boolean =
        _permissions.value.contains(key) || _currentUser.value?.roleName == "Admin"

    val isLoggedIn:     Boolean get() = _currentUser.value != null
    val userId:         Int     get() = _currentUser.value?.userId ?: 0
    val shiftId:        Int?    get() = _currentShift.value?.shiftId
    val pollIntervalMs: Long    get() = if (_settings.value.refreshMode == "Instant") 2_000L else 5_000L
}
