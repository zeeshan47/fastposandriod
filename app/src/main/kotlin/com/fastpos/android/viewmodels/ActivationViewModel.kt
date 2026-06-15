package com.fastpos.android.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.utils.LicenseInfo
import com.fastpos.android.utils.LicenseManager
import com.fastpos.android.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ActivationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager
) : ViewModel() {

    val machineId: String = LicenseManager.getMachineId(context)

    private val _licenseInfo       = MutableStateFlow<LicenseInfo?>(null)
    private val _keyInput          = MutableStateFlow("")
    private val _isWorking         = MutableStateFlow(false)
    private val _errorMessage      = MutableStateFlow<String?>(null)
    private val _activationSuccess = MutableStateFlow(false)

    val licenseInfo:       StateFlow<LicenseInfo?> = _licenseInfo
    val keyInput:          StateFlow<String>        = _keyInput
    val isWorking:         StateFlow<Boolean>       = _isWorking
    val errorMessage:      StateFlow<String?>       = _errorMessage
    val activationSuccess: StateFlow<Boolean>       = _activationSuccess

    init {
        viewModelScope.launch {
            _licenseInfo.value = LicenseManager.getCurrentLicense(context, prefs)
        }
    }

    fun setKeyInput(key: String) {
        _keyInput.value = key.trim()
        _errorMessage.value = null
    }

    fun activate() {
        val key = _keyInput.value.trim()
        if (key.isBlank()) { _errorMessage.value = "Enter a license key."; return }
        viewModelScope.launch {
            _isWorking.value    = true
            _errorMessage.value = null
            val (valid, info) = LicenseManager.validateKey(key, machineId)
            if (valid) {
                prefs.saveLicenseKey(key)
                _licenseInfo.value     = info.copy(machineId = machineId)
                _activationSuccess.value = true
            } else {
                _errorMessage.value = info.message
                _licenseInfo.value  = info.copy(machineId = machineId)
            }
            _isWorking.value = false
        }
    }

    fun continueTrial() {
        _activationSuccess.value = true
    }
}
