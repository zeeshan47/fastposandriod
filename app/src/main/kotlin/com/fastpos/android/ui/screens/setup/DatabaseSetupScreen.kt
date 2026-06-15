@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.viewmodels.SetupUiState
import com.fastpos.android.viewmodels.SetupViewModel

private enum class DbMode { NONE, SQL_SERVER, STANDALONE, MOBILE_CLIENT }

@Composable
fun DatabaseSetupScreen(
    onSetupComplete: () -> Unit,
    vm: SetupViewModel = hiltViewModel()
) {
    val uiState          by vm.uiState.collectAsState()
    val savedConfig      by vm.savedConfig.collectAsState()
    val savedPeerHost    by vm.savedPeerHost.collectAsState()
    val savedPeerPort    by vm.savedPeerPort.collectAsState()
    val savedDbMode      by vm.savedDbMode.collectAsState()
    val discoveredServers by vm.discoveredServers.collectAsState()
    val isDiscovering    by vm.isDiscovering.collectAsState()

    var selectedMode by remember { mutableStateOf(DbMode.NONE) }
    var populated    by remember { mutableStateOf(false) }

    var serverIp     by remember { mutableStateOf("") }
    var port         by remember { mutableStateOf("1433") }
    var instanceName by remember { mutableStateOf("SQLEXPRESS") }
    var databaseName by remember { mutableStateOf("FASTPOSDB") }
    var username     by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var peerHost     by remember { mutableStateOf("") }
    var peerPort     by remember { mutableStateOf("7001") }

    // Pre-populate from saved config once it loads
    LaunchedEffect(savedDbMode, savedConfig, savedPeerHost) {
        if (populated) return@LaunchedEffect
        when (savedDbMode) {
            "local" -> selectedMode = DbMode.STANDALONE
            "peer"  -> {
                selectedMode = DbMode.MOBILE_CLIENT
                peerHost = savedPeerHost
                peerPort = savedPeerPort.toString()
                vm.startDiscovery()
            }
            else -> if (savedConfig.isConfigured) {
                selectedMode = DbMode.SQL_SERVER
                serverIp     = savedConfig.serverIp
                port         = savedConfig.port.toString()
                instanceName = savedConfig.instanceName
                databaseName = savedConfig.databaseName
                username     = savedConfig.username
                password     = savedConfig.password
            }
        }
        populated = true
    }

    LaunchedEffect(uiState) {
        if (uiState is SetupUiState.Success) onSetupComplete()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Database Setup") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Text(
                text = "Choose how FastPOS stores its data",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Text(
                text = "You can use a shared SQL Server, run standalone, or connect this device to another Android running FastPOS.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // ── Mode cards ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeCard(
                    modifier = Modifier.weight(1f),
                    selected = selectedMode == DbMode.SQL_SERVER,
                    icon = { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(36.dp)) },
                    title = "SQL Server",
                    subtitle = "Multi-terminal / networked",
                    onClick = { selectedMode = DbMode.SQL_SERVER; vm.resetState(); vm.stopDiscovery() }
                )
                ModeCard(
                    modifier = Modifier.weight(1f),
                    selected = selectedMode == DbMode.STANDALONE,
                    icon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(36.dp)) },
                    title = "Standalone",
                    subtitle = "Local device only",
                    onClick = { selectedMode = DbMode.STANDALONE; vm.resetState(); vm.stopDiscovery() }
                )
                ModeCard(
                    modifier = Modifier.weight(1f),
                    selected = selectedMode == DbMode.MOBILE_CLIENT,
                    icon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(36.dp)) },
                    title = "Mobile Client",
                    subtitle = "Connect to another phone",
                    onClick = { selectedMode = DbMode.MOBILE_CLIENT; vm.resetState(); vm.startDiscovery() }
                )
            }

            // ── SQL Server form ──────────────────────────────────────────────
            if (selectedMode == DbMode.SQL_SERVER) {
                HorizontalDivider()

                Text(
                    text = "Connect to SQL Server",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Enter the SQL Server details where FASTPOSDB is hosted.\nEnsure SQL Server authentication is enabled and port 1433 is open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = serverIp, onValueChange = { serverIp = it },
                    label = { Text("Server IP Address *") },
                    placeholder = { Text("e.g. 192.168.1.10") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = port, onValueChange = { port = it },
                        label = { Text("Port") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = instanceName, onValueChange = { instanceName = it },
                        label = { Text("Instance Name") },
                        placeholder = { Text("SQLEXPRESS") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = databaseName, onValueChange = { databaseName = it },
                    label = { Text("Database Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = username, onValueChange = { username = it },
                    label = { Text("SQL Username *") },
                    placeholder = { Text("sa") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("SQL Password *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )

                if (uiState is SetupUiState.Error) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = (uiState as SetupUiState.Error).message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Button(
                    onClick = {
                        vm.testAndSave(serverIp, port, instanceName, databaseName, username, password)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = uiState !is SetupUiState.Testing
                ) {
                    if (uiState is SetupUiState.Testing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Testing Connection…")
                    } else {
                        Text("Test & Connect", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // ── Standalone mode ──────────────────────────────────────────────
            if (selectedMode == DbMode.STANDALONE) {
                HorizontalDivider()

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Standalone / Local Database",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "All data is stored locally on this device in a SQLite database. No network or SQL Server is required. Data is not shared with other terminals.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Default login: admin  |  PIN: 123123",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (uiState is SetupUiState.Error) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            text = (uiState as SetupUiState.Error).message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Button(
                    onClick = { vm.activateLocalMode() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = uiState !is SetupUiState.Testing
                ) {
                    if (uiState is SetupUiState.Testing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Setting up…")
                    } else {
                        Text("Use Standalone Mode", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // ── Mobile Client form ───────────────────────────────────────────
            if (selectedMode == DbMode.MOBILE_CLIENT) {
                HorizontalDivider()

                Text(
                    text = "Connect to Mobile Server",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = "Enter the IP address of the Android device running FastPOS in Standalone mode with 'Share Database' enabled. Both devices must be on the same WiFi network.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = peerHost, onValueChange = { peerHost = it },
                    label = { Text("Server IP Address *") },
                    placeholder = { Text("e.g. 192.168.1.5") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Wifi, null) }
                )

                // Auto-discovered servers via NSD
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isDiscovering) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(
                            if (isDiscovering) "Scanning for servers…" else "No servers found via auto-scan",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { vm.stopDiscovery(); vm.startDiscovery() }) { Text("Scan Again") }
                }
                if (discoveredServers.isNotEmpty()) {
                    discoveredServers.forEach { (host, port) ->
                        OutlinedButton(
                            onClick = { peerHost = host; peerPort = port.toString() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Wifi, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("$host:$port", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                OutlinedTextField(
                    value = peerPort, onValueChange = { peerPort = it },
                    label = { Text("Port (default 7001)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                if (uiState is SetupUiState.Error) {
                    val msg    = (uiState as SetupUiState.Error).message
                    val isInfo = msg.startsWith("✓")
                    Card(colors = CardDefaults.cardColors(
                        containerColor = if (isInfo) MaterialTheme.colorScheme.secondaryContainer
                                         else MaterialTheme.colorScheme.errorContainer
                    )) {
                        Text(
                            text     = msg,
                            modifier = Modifier.padding(12.dp),
                            color    = if (isInfo) MaterialTheme.colorScheme.onSecondaryContainer
                                       else MaterialTheme.colorScheme.onErrorContainer,
                            style    = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = { vm.resetState(); vm.testPeerConnection(peerHost, peerPort) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        enabled  = uiState !is SetupUiState.Testing
                    ) {
                        if (uiState is SetupUiState.Testing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.NetworkCheck, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Verify")
                        }
                    }
                    Button(
                        onClick  = { vm.resetState(); vm.connectToPeer(peerHost, peerPort) },
                        modifier = Modifier.weight(2f).height(52.dp),
                        enabled  = uiState !is SetupUiState.Testing
                    ) {
                        Icon(Icons.Default.Wifi, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Connect to Server", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    modifier: Modifier = Modifier,
    selected: Boolean,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Card(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
