@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.branch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.Branch
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.BranchViewModel

@Composable
fun BranchScreen(
    onNavigateBack: () -> Unit,
    vm: BranchViewModel = hiltViewModel()
) {
    val branches   by vm.branches.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val message    by vm.message.collectAsState()
    val branchId   by vm.session.currentBranchId.collectAsState()
    val branchName by vm.session.currentBranchName.collectAsState()

    var showDialog  by remember { mutableStateOf(false) }
    var editTarget  by remember { mutableStateOf<Branch?>(null) }
    var deleteTarget by remember { mutableStateOf<Branch?>(null) }

    message?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            vm.clearMessage()
        }
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title   = { Text("Delete Branch") },
            text    = { Text("Delete \"${deleteTarget!!.branchName}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteBranch(deleteTarget!!.branchId)
                    deleteTarget = null
                }) { Text("Delete", color = RedError) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showDialog) {
        BranchDialog(
            initial  = editTarget ?: Branch(),
            onDismiss = { showDialog = false; editTarget = null },
            onSave    = { branch ->
                vm.saveBranch(branch)
                showDialog = false
                editTarget = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Branch Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadBranches() }) {
                        Icon(Icons.Default.Refresh, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editTarget = null; showDialog = true },
                containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, null, tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Active branch banner
            Card(colors = CardDefaults.cardColors(
                containerColor = GreenSuccess.copy(alpha = 0.12f))) {
                Row(Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Store, null, tint = GreenSuccess, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Active Branch", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(branchName, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = GreenSuccess)
                    }
                }
            }

            message?.let {
                Card(colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.15f))) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }

            if (branches.isEmpty() && !isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Store, null, Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Text("No branches found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Tap + to add a branch", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(branches) { branch ->
                        BranchCard(
                            branch     = branch,
                            isActive   = branch.branchId == branchId,
                            onSwitch   = { vm.switchBranch(branch) },
                            onEdit     = { editTarget = branch; showDialog = true },
                            onDelete   = { deleteTarget = branch }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchCard(
    branch:   Branch,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isActive) GreenSuccess else MaterialTheme.colorScheme.outlineVariant
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) GreenSuccess.copy(alpha = 0.08f)
                             else MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            if (isActive) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(branch.branchName, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    if (isActive) {
                        Surface(color = GreenSuccess, shape = MaterialTheme.shapes.small) {
                            Text("Active", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                    if (!branch.isActive) {
                        Surface(color = RedError.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
                            Text("Inactive", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = RedError)
                        }
                    }
                }
                if (branch.address.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(branch.address, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (branch.phone.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Phone, null, Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(branch.phone, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isActive) {
                    IconButton(onClick = onSwitch) {
                        Icon(Icons.Default.SwapHoriz, null, tint = GreenSuccess, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, tint = AmberWarning, modifier = Modifier.size(20.dp))
                }
                if (branch.branchId != 1) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchDialog(
    initial:  Branch,
    onDismiss: () -> Unit,
    onSave:    (Branch) -> Unit
) {
    var name    by remember { mutableStateOf(initial.branchName) }
    var address by remember { mutableStateOf(initial.address) }
    var phone   by remember { mutableStateOf(initial.phone) }
    var isActive by remember { mutableStateOf(initial.isActive) }
    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.branchId == 0) "New Branch" else "Edit Branch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Branch Name *") },
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("Required") }) else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (initial.branchId != 0) {
                    Row(Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Active", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { nameError = true; return@TextButton }
                onSave(initial.copy(
                    branchName = name.trim(),
                    address    = address.trim(),
                    phone      = phone.trim(),
                    isActive   = isActive
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
