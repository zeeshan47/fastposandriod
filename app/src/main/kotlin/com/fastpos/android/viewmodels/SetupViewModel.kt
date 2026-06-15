package com.fastpos.android.viewmodels

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.database.ConnectionConfig
import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.network.PeerDbClient
import com.fastpos.android.utils.ConnectivityMonitor
import com.fastpos.android.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject

sealed class SetupUiState {
    data object Idle    : SetupUiState()
    data object Testing : SetupUiState()
    data object Success : SetupUiState()
    data class  Error(val message: String) : SetupUiState()
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs:        PreferencesManager,
    private val db:           DatabaseHelper,
    private val connectivity: ConnectivityMonitor
) : ViewModel() {

    private val _uiState            = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    private val _discoveredServers  = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    private val _savedConfig        = MutableStateFlow(ConnectionConfig())
    private val _savedPeerHost      = MutableStateFlow("")
    private val _savedPeerPort      = MutableStateFlow(7001)
    private val _savedDbMode        = MutableStateFlow("")
    private val _isDiscovering      = MutableStateFlow(false)
    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    val uiState:           StateFlow<SetupUiState>           = _uiState
    val discoveredServers: StateFlow<List<Pair<String, Int>>> = _discoveredServers
    val savedConfig:       StateFlow<ConnectionConfig>        = _savedConfig
    val savedPeerHost:     StateFlow<String>                  = _savedPeerHost
    val savedPeerPort:     StateFlow<Int>                     = _savedPeerPort
    val savedDbMode:       StateFlow<String>                  = _savedDbMode
    val isDiscovering:     StateFlow<Boolean>                 = _isDiscovering

    init {
        viewModelScope.launch {
            _savedConfig.value   = prefs.connectionConfig.first()
            _savedPeerHost.value = prefs.peerHost.first()
            _savedPeerPort.value = prefs.peerPort.first()
            _savedDbMode.value   = prefs.dbMode.first()
        }
    }

    fun testAndSave(
        serverIp:     String,
        port:         String,
        instanceName: String,
        databaseName: String,
        username:     String,
        password:     String
    ) {
        val portInt = port.toIntOrNull() ?: 1433
        val cfg = ConnectionConfig(
            serverIp     = serverIp.trim(),
            port         = portInt,
            instanceName = instanceName.trim(),
            databaseName = databaseName.trim().ifEmpty { "FASTPOSDB" },
            username     = username.trim(),
            password     = password
        )

        if (!cfg.isConfigured) {
            _uiState.value = SetupUiState.Error("Server IP and Username are required.")
            return
        }

        viewModelScope.launch {
            _uiState.value = SetupUiState.Testing
            try {
                db.deactivateAllModes()
                db.configure(cfg)
                db.testConnection()
                prefs.clearDbMode()
                prefs.saveConnectionConfig(cfg)
                _uiState.value = SetupUiState.Success
            } catch (e: Exception) {
                _uiState.value = SetupUiState.Error(
                    "Connection failed: ${e.message ?: "Unknown error"}\n\nCheck IP, credentials, and SQL Server port 1433."
                )
            }
        }
    }

    fun activateLocalMode() {
        viewModelScope.launch {
            try {
                prefs.setDbMode("local")
                db.activateLocalMode()
                _uiState.value = SetupUiState.Success
            } catch (e: Exception) {
                _uiState.value = SetupUiState.Error("Failed to initialize local database: ${e.message}")
            }
        }
    }

    fun connectToPeer(host: String, port: String) {
        val portInt = port.toIntOrNull() ?: 7001
        if (host.isBlank()) {
            _uiState.value = SetupUiState.Error("Server IP address is required.")
            return
        }
        viewModelScope.launch {
            _uiState.value = SetupUiState.Testing
            try {
                val client = PeerDbClient(host.trim(), portInt)
                val reachable = withContext(Dispatchers.IO) { client.ping() }
                if (!reachable) throw Exception("Cannot reach FastPOS server at ${host.trim()}:$portInt\n\nMake sure the server device has 'Share Database' enabled and is on the same network.")
                db.deactivateAllModes()
                prefs.savePeerConfig(host.trim(), portInt)
                prefs.setDbMode("peer")
                db.activatePeerClientMode(host.trim(), portInt)
                connectivity.setPeerMode(true)
                _uiState.value = SetupUiState.Success
            } catch (e: Exception) {
                _uiState.value = SetupUiState.Error(e.message ?: "Connection failed")
            }
        }
    }

    fun resetState() { _uiState.value = SetupUiState.Idle }

    fun testPeerConnection(host: String, port: String) {
        val portInt = port.toIntOrNull() ?: 7001
        if (host.isBlank()) { _uiState.value = SetupUiState.Error("Enter a server IP first."); return }
        viewModelScope.launch {
            _uiState.value = SetupUiState.Testing
            try {
                val client    = PeerDbClient(host.trim(), portInt)
                val reachable = withContext(Dispatchers.IO) { client.ping() }
                _uiState.value = if (reachable)
                    SetupUiState.Error("✓ Server at ${host.trim()}:$portInt is reachable. Tap Connect to save.")
                else
                    SetupUiState.Error("✗ Cannot reach ${host.trim()}:$portInt. Check IP and that the server has 'Share Database' enabled.")
            } catch (e: Exception) {
                _uiState.value = SetupUiState.Error("✗ ${e.message ?: "Connection failed"}")
            }
        }
    }

    fun startDiscovery() {
        _discoveredServers.value = emptyList()
        _isDiscovering.value = true
        try {
            val mgr = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            nsdManager = mgr
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(type: String) {}
                override fun onDiscoveryStopped(type: String) { _isDiscovering.value = false }
                override fun onStartDiscoveryFailed(type: String, code: Int) { _isDiscovering.value = false }
                override fun onStopDiscoveryFailed(type: String, code: Int) {}
                override fun onServiceFound(info: NsdServiceInfo) {
                    mgr.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, code: Int) {}
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolved.host?.hostAddress ?: return
                            val port = resolved.port
                            val entry = Pair(host, port)
                            if (entry !in _discoveredServers.value) {
                                _discoveredServers.value = _discoveredServers.value + entry
                            }
                        }
                    })
                }
                override fun onServiceLost(info: NsdServiceInfo) {}
            }
            discoveryListener = listener
            mgr.discoverServices("_fastpos._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (_: Exception) { _isDiscovering.value = false }
    }

    fun stopDiscovery() {
        _isDiscovering.value = false
        try {
            val listener = discoveryListener ?: return
            nsdManager?.stopServiceDiscovery(listener)
        } catch (_: Exception) {}
        discoveryListener = null
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }
}
