package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.repositories.AuditLogRepository
import com.fastpos.android.data.repositories.AuthRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class OverrideState {
    object Idle      : OverrideState()
    object Verifying : OverrideState()
    data class Success(val authUserId: Int, val authUserName: String) : OverrideState()
    data class Error(val message: String) : OverrideState()
}

@HiltViewModel
class ManagerOverrideViewModel @Inject constructor(
    private val authRepo:  AuthRepository,
    private val auditRepo: AuditLogRepository,
    val session:           SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow<OverrideState>(OverrideState.Idle)
    val state: StateFlow<OverrideState> = _state

    fun verify(username: String, password: String, action: String) {
        viewModelScope.launch {
            _state.value = OverrideState.Verifying
            try {
                val user = authRepo.verifyManagerCredentials(username, password)
                if (user != null) {
                    runCatching {
                        auditRepo.writeAudit(
                            user.userId, "MANAGER_OVERRIDE", "Users", session.userId
                        )
                    }
                    _state.value = OverrideState.Success(user.userId, user.fullName)
                } else {
                    _state.value = OverrideState.Error(
                        "Invalid credentials or insufficient role.\nAdmin or Manager required."
                    )
                }
            } catch (e: Exception) {
                _state.value = OverrideState.Error(e.message ?: "Verification failed")
            }
        }
    }

    fun reset() {
        _state.value = OverrideState.Idle
    }
}
