package com.fastpos.android.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Branch
import com.fastpos.android.data.models.User
import com.fastpos.android.data.repositories.AuditLogRepository
import com.fastpos.android.data.repositories.AuthRepository
import com.fastpos.android.data.repositories.BranchRepository
import com.fastpos.android.data.repositories.DbMigrationRepository
import com.fastpos.android.data.repositories.OrderRepository
import com.fastpos.android.data.repositories.ShiftRepository
import com.fastpos.android.data.repositories.ProductRepository
import com.fastpos.android.data.repositories.SettingsRepository
import com.fastpos.android.utils.LicenseInfo
import com.fastpos.android.utils.LicenseManager
import com.fastpos.android.utils.LicenseStatus
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginUiState {
    data object Idle    : LoginUiState()
    data object Loading : LoginUiState()
    data object Success : LoginUiState()
    data class  SelectBranch(val branches: List<Branch>) : LoginUiState()
    data class  Error(val message: String) : LoginUiState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepo:      AuthRepository,
    private val auditRepo:     AuditLogRepository,
    private val shiftRepo:     ShiftRepository,
    private val productRepo:   ProductRepository,
    private val settingsRepo:  SettingsRepository,
    private val branchRepo:    BranchRepository,
    private val migrationRepo: DbMigrationRepository,
    private val orderRepo:     OrderRepository,
    private val prefs:         PreferencesManager,
    private val session:       SessionManager
) : ViewModel() {

    private val _uiState          = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    private val _licenseInfo      = MutableStateFlow<LicenseInfo?>(null)
    private val _dbMode           = MutableStateFlow("")
    private val _activeUsers      = MutableStateFlow<List<User>>(emptyList())
    private val _selectedUsername = MutableStateFlow("")

    val uiState:          StateFlow<LoginUiState> = _uiState
    val licenseInfo:      StateFlow<LicenseInfo?> = _licenseInfo
    val dbMode:           StateFlow<String>       = _dbMode
    val activeUsers:      StateFlow<List<User>>   = _activeUsers
    val selectedUsername: StateFlow<String>       = _selectedUsername

    init {
        viewModelScope.launch {
            _licenseInfo.value = LicenseManager.getCurrentLicense(context, prefs)
        }
        viewModelScope.launch {
            prefs.dbMode.collect { _dbMode.value = it }
        }
        loadActiveUsers()
    }

    fun loadActiveUsers() {
        viewModelScope.launch {
            try { _activeUsers.value = authRepo.getActiveUsers() } catch (_: Exception) {}
        }
    }

    fun selectUser(username: String) { _selectedUsername.value = username }

    fun login(username: String, password: String) {
        if (_licenseInfo.value?.status == LicenseStatus.TrialExpired) {
            _uiState.value = LoginUiState.Error("Trial expired. Please activate a license key.")
            return
        }
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Please enter username and password.")
            return
        }
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val user = authRepo.login(username, password)
                if (user == null) {
                    _uiState.value = LoginUiState.Error("Invalid username or password.")
                    return@launch
                }
                val perms = authRepo.getUserPermissions(user.roleId)
                session.login(user, perms)
                authRepo.updateLastLogin(user.userId)
                runCatching { auditRepo.writeAudit(user.userId, "LOGIN", "Users", user.userId) }

                runCatching { migrationRepo.runPendingMigrations() }
                runCatching { orderRepo.fixPaidOrderStatuses() }

                val settings = try {
                    val s = settingsRepo.getSettings()
                    val logoData = runCatching { settingsRepo.getLogoData() }.getOrNull()
                    s.copy(logoData = logoData)
                } catch (_: Exception) {
                    productRepo.getCompanySettings()
                }
                session.setSettings(settings)

                val shift = shiftRepo.getOpenShift(user.userId) ?: shiftRepo.getAnyOpenShift()
                session.setShift(shift)

                val branches = branchRepo.getBranches().filter { it.isActive }
                if (branches.size > 1) {
                    _uiState.value = LoginUiState.SelectBranch(branches)
                } else {
                    val b = branches.firstOrNull()
                    val branchId   = b?.branchId   ?: prefs.activeBranchId.first()
                    val branchName = b?.branchName ?: prefs.activeBranchName.first()
                    session.setBranch(branchId, branchName)
                    prefs.saveActiveBranch(branchId, branchName)
                    _uiState.value = LoginUiState.Success
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    "Login failed: ${e.message ?: "Check your network connection."}"
                )
            }
        }
    }

    fun selectBranch(branch: Branch) {
        viewModelScope.launch {
            session.setBranch(branch.branchId, branch.branchName)
            prefs.saveActiveBranch(branch.branchId, branch.branchName)
            _uiState.value = LoginUiState.Success
        }
    }

    fun resetState() { _uiState.value = LoginUiState.Idle }
}
