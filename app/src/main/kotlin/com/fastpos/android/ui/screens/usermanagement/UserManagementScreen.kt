@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.usermanagement

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.Permission
import com.fastpos.android.data.models.Role
import com.fastpos.android.data.models.User
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.UserManagementViewModel

@Composable
fun UserManagementScreen(
    onNavigateBack: () -> Unit,
    vm: UserManagementViewModel = hiltViewModel()
) {
    val message by vm.message.collectAsState()
    val snack   = remember { SnackbarHostState() }
    var tab     by remember { mutableStateOf(0) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Management") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = vm::loadAll) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Users") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Role Permissions") })
            }
            when (tab) {
                0 -> UsersTab(vm)
                1 -> RolePermissionsTab(vm)
            }
        }
    }
}

// ── Users Tab ──────────────────────────────────────────────────────────────────

@Composable
private fun UsersTab(vm: UserManagementViewModel) {
    val users   by vm.users.collectAsState()
    val loading by vm.loading.collectAsState()
    var showForm     by remember { mutableStateOf(false) }
    var deactivateTarget by remember { mutableStateOf<User?>(null) }
    var resetTarget      by remember { mutableStateOf<User?>(null) }

    Box(Modifier.fillMaxSize()) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            users.isEmpty() -> Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ManageAccounts, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No users", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(users, key = { it.userId }) { u ->
                    UserCard(
                        user       = u,
                        onEdit     = { vm.selectForEdit(u); showForm = true },
                        onDeactivate = if (u.isActive) ({ deactivateTarget = u }) else null,
                        onReset    = { resetTarget = u }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { vm.clearForm(); showForm = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Default.PersonAdd, null, tint = androidx.compose.ui.graphics.Color.White) }
    }

    if (showForm) {
        UserFormSheet(vm = vm, onDismiss = { showForm = false; vm.clearForm() })
    }

    deactivateTarget?.let { u ->
        AlertDialog(
            onDismissRequest = { deactivateTarget = null },
            title = { Text("Deactivate User") },
            text  = { Text("Deactivate \"${u.fullName}\"? They won't be able to log in.") },
            confirmButton = { TextButton(onClick = { vm.deactivateUser(u.userId); deactivateTarget = null }) { Text("Deactivate", color = RedError) } },
            dismissButton = { TextButton(onClick = { deactivateTarget = null }) { Text("Cancel") } }
        )
    }

    resetTarget?.let { u ->
        AlertDialog(
            onDismissRequest = { resetTarget = null },
            title = { Text("Reset PIN") },
            text  = { Text("Reset PIN for \"${u.fullName}\" to '123123'?") },
            confirmButton = { TextButton(onClick = { vm.resetPassword(u.userId); resetTarget = null }) { Text("Reset", color = AmberWarning) } },
            dismissButton = { TextButton(onClick = { resetTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun UserCard(user: User, onEdit: () -> Unit, onDeactivate: (() -> Unit)?, onReset: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, if (user.isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(user.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (!user.isActive) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) {
                            Text("Inactive", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = MaterialTheme.shapes.small, color = AmberWarning.copy(alpha = 0.15f)) {
                        Text(user.roleName, Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                    }
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, null, tint = AmberWarning, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onReset, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.LockReset, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            if (onDeactivate != null) {
                IconButton(onClick = onDeactivate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.PersonOff, null, tint = RedError, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun UserFormSheet(vm: UserManagementViewModel, onDismiss: () -> Unit) {
    val roles        by vm.roles.collectAsState()
    val formUserId   by vm.formUserId.collectAsState()
    val formFullName by vm.formFullName.collectAsState()
    val formUsername by vm.formUsername.collectAsState()
    val formPassword by vm.formPassword.collectAsState()
    val formRoleId   by vm.formRoleId.collectAsState()
    val formIsActive by vm.formIsActive.collectAsState()

    var roleExpanded by remember { mutableStateOf(false) }
    var formError    by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(if (formUserId > 0) "Edit User" else "New User",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            HorizontalDivider()

            if (formError != null) {
                Card(colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, RedError.copy(alpha = 0.4f))) {
                    Text(formError!!, Modifier.padding(10.dp), color = RedError, style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(value = formFullName, onValueChange = { vm.setFormFullName(it); formError = null },
                label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = formUsername, onValueChange = { vm.setFormUsername(it); formError = null },
                label = { Text("Username *") }, modifier = Modifier.fillMaxWidth())

            // Role selector
            ExposedDropdownMenuBox(expanded = roleExpanded, onExpandedChange = { roleExpanded = it }) {
                OutlinedTextField(
                    value = roles.find { it.roleId == formRoleId }?.roleName ?: "Select role",
                    onValueChange = {}, readOnly = true, label = { Text("Role *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                    roles.forEach { r ->
                        DropdownMenuItem(text = { Text(r.roleName) }, onClick = { vm.setFormRoleId(r.roleId); roleExpanded = false })
                    }
                }
            }

            val pinLabel = if (formUserId > 0) "New PIN (4–6 digits, leave blank to keep)" else "PIN * (4–6 digits)"
            OutlinedTextField(
                value = formPassword,
                onValueChange = { vm.setFormPassword(it); formError = null },
                label = { Text(pinLabel) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                leadingIcon = { Icon(Icons.Default.Lock, null, Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (formUserId > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = formIsActive, onCheckedChange = vm::setFormIsActive)
                }
            }

            Button(
                onClick = { vm.saveUser(onError = { formError = it }, onSuccess = onDismiss) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp))
                Text(if (formUserId > 0) "Update User" else "Create User")
            }
        }
    }
}

// ── Role Permissions Tab ───────────────────────────────────────────────────────

@Composable
private fun RolePermissionsTab(vm: UserManagementViewModel) {
    val roles           by vm.roles.collectAsState()
    val permissions     by vm.permissions.collectAsState()
    val selectedRole    by vm.selectedRoleForPerms.collectAsState()
    val grantedIds      by vm.grantedPermIds.collectAsState()
    var roleExpanded    by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Role selector
        ExposedDropdownMenuBox(expanded = roleExpanded, onExpandedChange = { roleExpanded = it }) {
            OutlinedTextField(
                value = selectedRole?.roleName ?: "Select a role",
                onValueChange = {}, readOnly = true, label = { Text("Role") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                roles.forEach { r ->
                    DropdownMenuItem(text = { Text(r.roleName) }, onClick = { vm.selectRoleForPerms(r); roleExpanded = false })
                }
            }
        }

        if (selectedRole == null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Select a role to manage permissions", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // Group permissions by module
            val grouped = permissions.groupBy { it.module }

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                grouped.forEach { (module, perms) ->
                    item(key = "header_$module") {
                        Text(
                            module.ifBlank { "General" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(perms, key = { it.permissionId }) { perm ->
                        PermissionRow(
                            permission = perm,
                            isGranted  = perm.permissionId in grantedIds,
                            onToggle   = { vm.togglePermission(perm.permissionId) }
                        )
                    }
                }
            }

            Button(
                onClick = vm::saveRolePermissions,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(8.dp))
                Text("Save Permissions for '${selectedRole!!.roleName}'")
            }
        }
    }
}

@Composable
private fun PermissionRow(permission: Permission, isGranted: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(permission.permissionName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            if (permission.permissionKey.isNotBlank()) {
                Text(permission.permissionKey, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = isGranted, onCheckedChange = { onToggle() }, modifier = Modifier.scale(0.85f))
    }
}
