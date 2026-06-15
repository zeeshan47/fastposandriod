package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Permission
import com.fastpos.android.data.models.Role
import com.fastpos.android.data.models.User
import com.fastpos.android.data.repositories.UserManagementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class UserManagementViewModel @Inject constructor(private val repo: UserManagementRepository) : ViewModel() {

    private val _users       = MutableStateFlow<List<User>>(emptyList())
    private val _roles       = MutableStateFlow<List<Role>>(emptyList())
    private val _permissions = MutableStateFlow<List<Permission>>(emptyList())
    private val _message     = MutableStateFlow<String?>(null)
    private val _loading     = MutableStateFlow(false)

    // User form
    private val _formUserId   = MutableStateFlow(0)
    private val _formFullName = MutableStateFlow("")
    private val _formUsername = MutableStateFlow("")
    private val _formPassword = MutableStateFlow("")
    private val _formRoleId   = MutableStateFlow(0)
    private val _formIsActive = MutableStateFlow(true)

    // Role permissions tab
    private val _selectedRoleForPerms = MutableStateFlow<Role?>(null)
    private val _grantedPermIds       = MutableStateFlow<Set<Int>>(emptySet())

    val users:       StateFlow<List<User>>       = _users.asStateFlow()
    val roles:       StateFlow<List<Role>>       = _roles.asStateFlow()
    val permissions: StateFlow<List<Permission>> = _permissions.asStateFlow()
    val message:     StateFlow<String?>          = _message.asStateFlow()
    val loading:     StateFlow<Boolean>          = _loading.asStateFlow()

    val formUserId:   StateFlow<Int>     = _formUserId.asStateFlow()
    val formFullName: StateFlow<String>  = _formFullName.asStateFlow()
    val formUsername: StateFlow<String>  = _formUsername.asStateFlow()
    val formPassword: StateFlow<String>  = _formPassword.asStateFlow()
    val formRoleId:   StateFlow<Int>     = _formRoleId.asStateFlow()
    val formIsActive: StateFlow<Boolean> = _formIsActive.asStateFlow()

    val selectedRoleForPerms: StateFlow<Role?>    = _selectedRoleForPerms.asStateFlow()
    val grantedPermIds:       StateFlow<Set<Int>> = _grantedPermIds.asStateFlow()

    val isEditing: Boolean get() = _formUserId.value > 0

    init {
        viewModelScope.launch {
            loadAll()
        }
    }

    fun loadAll() = viewModelScope.launch {
        _loading.value = true
        runCatching {
            _users.value       = repo.getAllUsers()
            _roles.value       = repo.getAllRoles()
            _permissions.value = repo.getAllPermissions()
            if (_formRoleId.value == 0) _roles.value.firstOrNull()?.let { _formRoleId.value = it.roleId }
        }.onFailure { _message.value = "Load failed: ${it.message}" }
        _loading.value = false
    }

    fun selectForEdit(u: User) {
        _formUserId.value   = u.userId
        _formFullName.value = u.fullName
        _formUsername.value = u.username
        _formPassword.value = ""
        _formRoleId.value   = u.roleId
        _formIsActive.value = u.isActive
    }

    fun clearForm() {
        _formUserId.value = 0; _formFullName.value = ""; _formUsername.value = ""
        _formPassword.value = ""
        _formIsActive.value = true
        _roles.value.firstOrNull()?.let { _formRoleId.value = it.roleId }
    }

    fun setFormFullName(v: String) { _formFullName.value = v }
    fun setFormUsername(v: String) { _formUsername.value = v }
    fun setFormPassword(v: String) { _formPassword.value = v.filter { it.isDigit() }.take(6) }
    fun setFormRoleId(v: Int)      { _formRoleId.value = v }
    fun setFormIsActive(v: Boolean){ _formIsActive.value = v }

    fun saveUser(onError: (String) -> Unit, onSuccess: () -> Unit = {}) = viewModelScope.launch {
        val name     = _formFullName.value.trim()
        val uname    = _formUsername.value.trim()
        val password = _formPassword.value
        val roleId   = _formRoleId.value

        if (name.isBlank())  { onError("Full name required"); return@launch }
        if (uname.isBlank()) { onError("Username required"); return@launch }
        if (roleId == 0)     { onError("Select a role"); return@launch }
        if (_formUserId.value == 0 && password.isBlank()) { onError("PIN required for new users"); return@launch }
        if (password.isNotBlank()) {
            if (password.length < 4) { onError("PIN must be 4–6 digits"); return@launch }
            if (!password.all { it.isDigit() }) { onError("PIN must be digits only"); return@launch }
        }

        _loading.value = true
        runCatching {
            val exists = repo.usernameExists(uname, _formUserId.value)
            if (exists) { _message.value = "Username already taken"; return@runCatching }

            val user = User(
                userId   = _formUserId.value,
                fullName = name,
                username = uname,
                roleId   = roleId,
                isActive = _formIsActive.value
            )
            if (_formUserId.value == 0) {
                repo.createUser(user, sha256(password))
                _message.value = "User '$uname' created"
            } else {
                repo.updateUser(user)
                if (password.isNotBlank()) repo.changePassword(_formUserId.value, sha256(password))
                _message.value = "User '$uname' updated"
            }
            clearForm()
            _users.value = repo.getAllUsers()
            onSuccess()
        }.onFailure { _message.value = "Error: ${it.message}" }
        _loading.value = false
    }

    fun deactivateUser(userId: Int) = viewModelScope.launch {
        val u = _users.value.find { it.userId == userId } ?: return@launch
        runCatching {
            repo.updateUser(u.copy(isActive = false))
            _users.value = repo.getAllUsers()
            _message.value = "User deactivated"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun resetPassword(userId: Int) = viewModelScope.launch {
        runCatching {
            repo.changePassword(userId, sha256("123123"))
            _message.value = "PIN reset to '123123'"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    // Role permissions
    fun selectRoleForPerms(role: Role) {
        _selectedRoleForPerms.value = role
        viewModelScope.launch {
            runCatching { _grantedPermIds.value = repo.getGrantedPermissionIds(role.roleId) }
        }
    }

    fun togglePermission(permissionId: Int) {
        val cur = _grantedPermIds.value.toMutableSet()
        if (permissionId in cur) cur.remove(permissionId) else cur.add(permissionId)
        _grantedPermIds.value = cur
    }

    fun saveRolePermissions() = viewModelScope.launch {
        val role = _selectedRoleForPerms.value ?: return@launch
        runCatching {
            repo.setRolePermissions(role.roleId, _grantedPermIds.value.toList())
            _message.value = "Permissions saved for '${role.roleName}'"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun clearMessage() { _message.value = null }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
