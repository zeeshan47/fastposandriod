@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.activation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.LicenseStatus
import com.fastpos.android.viewmodels.ActivationViewModel

@Composable
fun ActivationScreen(
    onContinue: () -> Unit,
    vm: ActivationViewModel = hiltViewModel()
) {
    val licenseInfo       by vm.licenseInfo.collectAsState()
    val keyInput          by vm.keyInput.collectAsState()
    val isWorking         by vm.isWorking.collectAsState()
    val errorMessage      by vm.errorMessage.collectAsState()
    val activationSuccess by vm.activationSuccess.collectAsState()

    LaunchedEffect(activationSuccess) {
        if (activationSuccess) onContinue()
    }

    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FastPOS Activation") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement   = Arrangement.spacedBy(16.dp),
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            // ── Status badge ─────────────────────────────────────────────────
            licenseInfo?.let { info ->
                val (badgeColor, badgeText, badgeIcon) = when (info.status) {
                    LicenseStatus.Valid        -> Triple(GreenSuccess, "ACTIVATED", Icons.Default.CheckCircle)
                    LicenseStatus.Trial        -> Triple(AmberWarning, "TRIAL — ${info.daysLeft} DAYS LEFT", Icons.Default.WatchLater)
                    LicenseStatus.TrialExpired -> Triple(RedError, "TRIAL EXPIRED", Icons.Default.Cancel)
                    LicenseStatus.Invalid      -> Triple(RedError, "INVALID KEY", Icons.Default.Cancel)
                    LicenseStatus.Tampered     -> Triple(RedError, "TAMPERED", Icons.Default.Security)
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.12f)),
                    border = BorderStroke(1.5.dp, badgeColor)
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(badgeIcon, null, Modifier.size(28.dp), tint = badgeColor)
                        Column {
                            Text(badgeText, style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = badgeColor)
                            Text(info.message, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // ── Machine ID ───────────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your Device ID", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text("Provide this to your vendor to generate a license key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            vm.machineId,
                            style      = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color      = BlueInfo,
                            modifier   = Modifier.weight(1f)
                        )
                        IconButton(onClick = { clipboard.setText(AnnotatedString(vm.machineId)) }) {
                            Icon(Icons.Default.ContentCopy, null, tint = BlueInfo)
                        }
                    }
                }
            }

            // ── Key input ────────────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter License Key", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value         = keyInput,
                        onValueChange = vm::setKeyInput,
                        label         = { Text("License Key") },
                        placeholder   = { Text("FP-XXXXXXXXXXXXXXXXX-XXXXXXXXXXXXXXXX") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        textStyle     = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        isError       = errorMessage != null,
                        supportingText = errorMessage?.let { msg -> { Text(msg, color = RedError) } }
                    )
                    Button(
                        onClick  = vm::activate,
                        enabled  = keyInput.isNotBlank() && !isWorking,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Activating…")
                        } else {
                            Icon(Icons.Default.Key, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Activate License", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            // ── Continue trial ───────────────────────────────────────────────
            if (licenseInfo?.status == LicenseStatus.Trial) {
                TextButton(
                    onClick  = vm::continueTrial,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Continue with Trial (${licenseInfo!!.daysLeft} day(s) remaining)")
                }
            }
        }
    }
}
