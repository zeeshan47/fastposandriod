@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.backup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.BackupViewModel

@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    vm: BackupViewModel = hiltViewModel()
) {
    val isLoading       by vm.isLoading.collectAsState()
    val message         by vm.message.collectAsState()
    val isError         by vm.isError.collectAsState()
    val lastBackupPath  by vm.lastBackupPath.collectAsState()
    val backupFolder    by vm.backupFolder.collectAsState()
    val restoreFilePath by vm.restoreFilePath.collectAsState()
    val isRestoring     by vm.isRestoring.collectAsState()
    val restoreSuccess  by vm.restoreSuccess.collectAsState()

    var showRestoreConfirm by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            if (!isError && !restoreSuccess) snackbarHost.showSnackbar(it)
            vm.clearMessage()
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            icon    = { Icon(Icons.Default.Warning, null, tint = RedError) },
            title   = { Text("Confirm Restore", color = RedError) },
            text    = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will REPLACE the current database with the selected backup.", fontWeight = FontWeight.SemiBold)
                    Text("All data entered after the backup was created will be permanently lost. This action cannot be undone.")
                    Text("File: ${restoreFilePath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showRestoreConfirm = false; vm.restore() },
                    colors  = ButtonDefaults.buttonColors(containerColor = RedError)
                ) { Text("Restore Now") }
            },
            dismissButton = { TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                title = { Text("Backup & Restore") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Backup card ──────────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Backup, null, Modifier.size(22.dp), tint = GreenSuccess)
                        Text("Create Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Save a complete copy of the database to the SQL Server machine.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value         = backupFolder,
                        onValueChange = vm::setBackupFolder,
                        label         = { Text("Backup Folder (on SQL Server machine)") },
                        leadingIcon   = { Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )

                    // Info box
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.Info, null, Modifier.size(18.dp), tint = BlueInfo)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("What gets backed up?", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "All orders, customers, products, inventory, employees, shifts, expenses, and settings. " +
                                    "The backup file is saved as a SQL Server .bak file on the server machine. " +
                                    "The folder path must be accessible to the SQL Server service account.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Button(
                        onClick  = vm::backup,
                        enabled  = !isLoading,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Creating backup…")
                        } else {
                            Icon(Icons.Default.Backup, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Backup Now", style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    // Result
                    if (message != null && isError) {
                        Card(colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.1f))) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Error, null, Modifier.size(18.dp), tint = RedError)
                                Text(message ?: "", style = MaterialTheme.typography.bodySmall, color = RedError)
                            }
                        }
                    } else if (lastBackupPath != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = GreenSuccess.copy(alpha = 0.1f))) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = GreenSuccess)
                                    Text("Backup created successfully", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = GreenSuccess)
                                }
                                Text(lastBackupPath!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // ── Restore card ─────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, RedError.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.RestorePage, null, Modifier.size(22.dp), tint = RedError)
                        Text("Restore Database", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RedError)
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.1f))) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Warning, null, Modifier.size(16.dp), tint = RedError)
                            Text(
                                "Restoring will REPLACE the current database. All data after the backup date will be lost.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = RedError
                            )
                        }
                    }
                    OutlinedTextField(
                        value         = restoreFilePath,
                        onValueChange = vm::setRestoreFilePath,
                        label         = { Text(".bak File Path (on SQL Server machine)") },
                        placeholder   = { Text("e.g. C:\\FastPOS_Backups\\FASTPOSDB_20260519.bak") },
                        leadingIcon   = { Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp)) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true
                    )
                    Button(
                        onClick  = { showRestoreConfirm = true },
                        enabled  = restoreFilePath.isNotBlank() && !isRestoring,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = RedError)
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Restoring database…")
                        } else {
                            Icon(Icons.Default.RestorePage, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Restore Database", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    if (restoreSuccess) {
                        Card(colors = CardDefaults.cardColors(containerColor = GreenSuccess.copy(alpha = 0.1f))) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp), tint = GreenSuccess)
                                Text("Restore completed successfully.", style = MaterialTheme.typography.bodySmall, color = GreenSuccess, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (message != null && isError) {
                        Card(colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.1f))) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.Error, null, Modifier.size(18.dp), tint = RedError)
                                Text(message ?: "", style = MaterialTheme.typography.bodySmall, color = RedError)
                            }
                        }
                    }
                }
            }

            // ── Tips card ───────────────────────────────────────────────────
            Card(colors = CardDefaults.cardColors(containerColor = BlueInfo.copy(alpha = 0.08f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Backup Tips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BlueInfo)
                    listOf(
                        "Schedule backups daily — early morning before business opens is ideal.",
                        "Store backup files on a different drive or network share to protect against disk failure.",
                        "The SQL Server service account must have write permission to the backup folder.",
                        "Test restoring a backup periodically to verify it is valid."
                    ).forEach { tip ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Circle, null, Modifier.size(6.dp).padding(top = 5.dp), tint = BlueInfo)
                            Text(tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
