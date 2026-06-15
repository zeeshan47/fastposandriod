@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.ui.theme.AmberWarning
import com.fastpos.android.ui.theme.RedError
import com.fastpos.android.viewmodels.ManagerOverrideViewModel
import com.fastpos.android.viewmodels.OverrideState

/**
 * Reusable manager-override dialog matching WPF's ManagerOverrideDialog.
 * Blocks the action until an Admin or Manager (RoleId ≤ 2) enters valid credentials.
 *
 * @param action      Human-readable description shown in the dialog, e.g. "Void Order #ORD-00023"
 * @param onVerified  Called when credentials are accepted; proceed with the action inside this callback
 * @param onDismiss   Called when the user cancels without authorising
 */
@Composable
fun ManagerOverrideDialog(
    action:     String,
    onVerified: () -> Unit,
    onDismiss:  () -> Unit,
    vm:         ManagerOverrideViewModel = hiltViewModel()
) {
    val state        by vm.state.collectAsState()
    var username     by remember { mutableStateOf("") }
    var password     by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is OverrideState.Success) {
            onVerified()
            vm.reset()
        }
    }

    DisposableEffect(Unit) {
        onDispose { vm.reset() }
    }

    val isVerifying = state is OverrideState.Verifying

    AlertDialog(
        onDismissRequest = { if (!isVerifying) { vm.reset(); onDismiss() } },
        icon  = { Icon(Icons.Default.AdminPanelSettings, null, tint = AmberWarning) },
        title = { Text("Manager Authorization", fontWeight = FontWeight.Bold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Action banner
                Surface(
                    color  = AmberWarning.copy(alpha = 0.12f),
                    shape  = MaterialTheme.shapes.small
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                        Text(
                            text       = action,
                            style      = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Error message
                if (state is OverrideState.Error) {
                    Text(
                        text  = (state as OverrideState.Error).message,
                        style = MaterialTheme.typography.labelMedium,
                        color = RedError
                    )
                }

                OutlinedTextField(
                    value         = username,
                    onValueChange = { username = it; },
                    label         = { Text("Username") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    enabled       = !isVerifying,
                    leadingIcon   = { Icon(Icons.Default.Person, null, Modifier.size(18.dp)) }
                )

                OutlinedTextField(
                    value                  = password,
                    onValueChange          = { password = it },
                    label                  = { Text("Password") },
                    modifier               = Modifier.fillMaxWidth(),
                    singleLine             = true,
                    enabled                = !isVerifying,
                    visualTransformation   = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions        = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon            = { Icon(Icons.Default.Lock, null, Modifier.size(18.dp)) },
                    trailingIcon           = {
                        IconButton(onClick = { showPassword = !showPassword }, enabled = !isVerifying) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = { vm.verify(username.trim(), password, action) },
                enabled  = username.isNotBlank() && password.isNotBlank() && !isVerifying
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Authorize")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick  = { vm.reset(); onDismiss() },
                enabled  = !isVerifying
            ) { Text("Cancel") }
        }
    )
}
