package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.repositories.BackupRepository
import com.fastpos.android.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repo:  BackupRepository,
    private val prefs: PreferencesManager
) : ViewModel() {

    private val _isLoading       = MutableStateFlow(false)
    private val _message         = MutableStateFlow<String?>(null)
    private val _isError         = MutableStateFlow(false)
    private val _lastBackupPath  = MutableStateFlow<String?>(null)
    private val _restoreFilePath = MutableStateFlow("")
    private val _isRestoring     = MutableStateFlow(false)
    private val _restoreSuccess  = MutableStateFlow(false)

    val isLoading:       StateFlow<Boolean>  = _isLoading
    val message:         StateFlow<String?>  = _message
    val isError:         StateFlow<Boolean>  = _isError
    val lastBackupPath:  StateFlow<String?>  = _lastBackupPath
    val restoreFilePath: StateFlow<String>   = _restoreFilePath
    val isRestoring:     StateFlow<Boolean>  = _isRestoring
    val restoreSuccess:  StateFlow<Boolean>  = _restoreSuccess

    val backupFolder: StateFlow<String> = prefs.backupFolder
        .stateIn(viewModelScope, SharingStarted.Eagerly, "C:\\FastPOS_Backups")

    fun setBackupFolder(path: String) {
        viewModelScope.launch { prefs.saveBackupFolder(path) }
    }

    fun backup() {
        viewModelScope.launch {
            _isLoading.value = true
            _isError.value   = false
            _message.value   = null
            try {
                val path = repo.backupDatabase(backupFolder.value)
                _lastBackupPath.value = path
                _message.value = "Backup created successfully at:\n$path"
            } catch (e: Exception) {
                _isError.value = true
                _message.value = e.message ?: "Backup failed."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setRestoreFilePath(path: String) { _restoreFilePath.value = path }

    fun restore() {
        val path = _restoreFilePath.value.trim()
        if (path.isBlank()) { _message.value = "Enter the path to the .bak file."; _isError.value = true; return }
        viewModelScope.launch {
            _isRestoring.value    = true
            _isError.value        = false
            _restoreSuccess.value = false
            _message.value        = null
            try {
                repo.restoreDatabase(path)
                _restoreSuccess.value = true
                _message.value = "Database restored successfully from:\n$path"
            } catch (e: Exception) {
                _isError.value = true
                _message.value = e.message ?: "Restore failed."
            } finally {
                _isRestoring.value = false
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
