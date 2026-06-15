package com.fastpos.android.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.ui.navigation.Screen
import com.fastpos.android.utils.ConnectivityMonitor
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

@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs:       PreferencesManager,
    private val db:          DatabaseHelper,
    private val connectivity: ConnectivityMonitor,
    val session:             SessionManager
) : ViewModel() {

    val isOnline: kotlinx.coroutines.flow.StateFlow<Boolean> = connectivity.isOnline
    val isPeerMode: Boolean get() = connectivity.isPeerMode

    // Always starts at Splash; real destination is computed async and passed to SplashScreen.
    private val _startDestination = MutableStateFlow<String?>(Screen.Splash.route)
    val startDestination: StateFlow<String?> = _startDestination

    private val _realDestination = MutableStateFlow<String?>(null)
    val realDestination: StateFlow<String?> = _realDestination

    init {
        viewModelScope.launch {
            try {
            // License check first
            val licenseInfo = LicenseManager.getCurrentLicense(context, prefs)
            if (licenseInfo.status == LicenseStatus.TrialExpired ||
                licenseInfo.status == LicenseStatus.Tampered) {
                _realDestination.value = Screen.Activation.route
                return@launch
            }

            val mode = prefs.dbMode.first()
            if (mode == "local") {
                db.activateLocalMode()
                if (prefs.peerServerEnabled.first()) db.startPeerServer()
                _realDestination.value = Screen.Login.route
                return@launch
            }
            if (mode == "peer") {
                val host = prefs.peerHost.first()
                val port = prefs.peerPort.first()
                if (host.isNotBlank()) {
                    db.activatePeerClientMode(host, port)
                    connectivity.setPeerMode(true)
                    _realDestination.value = Screen.Login.route
                    return@launch
                }
                // Peer config missing — fall through to Setup
            }
            val cfg = prefs.connectionConfig.first()
            _realDestination.value = if (cfg.isConfigured) {
                db.configure(cfg)
                if (prefs.peerServerEnabled.first()) db.startPeerServer()
                Screen.Login.route
            } else {
                Screen.Setup.route
            }
            } catch (_: Exception) {
                _realDestination.value = Screen.Setup.route
            }
        }
    }
}
