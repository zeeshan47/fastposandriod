@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.LicenseStatus
import com.fastpos.android.viewmodels.LoginUiState
import com.fastpos.android.viewmodels.LoginViewModel

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onChangeServer: () -> Unit = {},
    onActivate:     () -> Unit = {},
    vm: LoginViewModel = hiltViewModel()
) {
    val uiState          by vm.uiState.collectAsState()
    val licenseInfo      by vm.licenseInfo.collectAsState()
    val dbMode           by vm.dbMode.collectAsState()
    val activeUsers      by vm.activeUsers.collectAsState()
    val selectedUsername by vm.selectedUsername.collectAsState()

    var pin by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) onLoginSuccess()
    }
    LaunchedEffect(selectedUsername) { pin = "" }

    // Branch selection dialog
    if (uiState is LoginUiState.SelectBranch) {
        val branches = (uiState as LoginUiState.SelectBranch).branches
        AlertDialog(
            onDismissRequest = {},
            icon  = { Icon(Icons.Default.Store, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Select Branch") },
            text  = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(branches) { branch ->
                        Card(
                            onClick = { vm.selectBranch(branch) },
                            colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                            border  = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Store, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(branch.branchName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    if (branch.address.isNotBlank())
                                        Text(branch.address, style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    val trialExpired = licenseInfo?.status == LicenseStatus.TrialExpired
    val loading      = uiState is LoginUiState.Loading

    Box(
        modifier         = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier            = Modifier
                .widthIn(max = 420.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // ── Brand ─────────────────────────────────────────────────────────
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black)) { append("Meal") }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary,      fontWeight = FontWeight.Black)) { append("Flow") }
                },
                fontSize = 30.sp
            )
            Text(
                "Where Orders Flow",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // Badges row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                licenseInfo?.let { info ->
                    val (badgeColor, badgeText) = when (info.status) {
                        LicenseStatus.Valid        -> Pair(GreenSuccess, if (info.daysLeft == Int.MAX_VALUE) "Licensed" else "Licensed · ${info.daysLeft}d")
                        LicenseStatus.Trial        -> Pair(AmberWarning, "Trial · ${info.daysLeft}d left")
                        LicenseStatus.TrialExpired -> Pair(RedError, "Trial Expired")
                        else                       -> Pair(RedError, "Unlicensed")
                    }
                    Surface(
                        color  = badgeColor.copy(alpha = 0.12f),
                        shape  = MaterialTheme.shapes.extraSmall,
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                    ) {
                        Text(badgeText, style = MaterialTheme.typography.labelSmall,
                            color = badgeColor, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                }
                val (modeColor, modeIcon, modeLabel) = when (dbMode) {
                    "local" -> Triple(GreenSuccess, Icons.Default.PhoneAndroid, "Standalone")
                    "peer"  -> Triple(AmberWarning, Icons.Default.Wifi, "Client")
                    else    -> Triple(BlueInfo, Icons.Default.Storage, "SQL Server")
                }
                Surface(
                    color  = modeColor.copy(alpha = 0.10f),
                    shape  = MaterialTheme.shapes.extraSmall,
                    border = BorderStroke(1.dp, modeColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(modeIcon, null, Modifier.size(11.dp), tint = modeColor)
                        Text(modeLabel, style = MaterialTheme.typography.labelSmall,
                            color = modeColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ── Account list ──────────────────────────────────────────────────
            Text(
                "Select Account",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            if (activeUsers.isEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Loading accounts…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeUsers.forEach { user ->
                        val isSelected = selectedUsername == user.username
                        Card(
                            onClick = { vm.selectUser(user.username) },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(10.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 1.dp),
                            border    = if (isSelected) null
                                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, Modifier.size(20.dp),
                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                   else MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text      = user.fullName.ifBlank { user.username },
                                        style     = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color     = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurface,
                                        maxLines  = 1,
                                        overflow  = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text  = user.roleName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, null, Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── PIN section ───────────────────────────────────────────────────
            if (trialExpired) {
                Text("Trial period has ended.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick  = onActivate,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                ) {
                    Icon(Icons.Default.Key, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Activate License")
                }
            } else {
                // Selected user label / prompt
                if (selectedUsername.isNotBlank()) {
                    val displayUser = activeUsers.firstOrNull { it.username == selectedUsername }
                    Text(
                        text  = "PIN for ${displayUser?.fullName?.ifBlank { selectedUsername } ?: selectedUsername}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text("Select an account above to login",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(10.dp))

                // PIN dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    repeat(maxOf(pin.length, 4)) { i ->
                        Surface(
                            shape    = androidx.compose.foundation.shape.CircleShape,
                            color    = if (i < pin.length) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(if (i < pin.length) 14.dp else 11.dp)
                        ) {}
                    }
                }

                // Error message
                AnimatedVisibility(visible = uiState is LoginUiState.Error) {
                    Text(
                        text     = (uiState as? LoginUiState.Error)?.message ?: "",
                        color    = MaterialTheme.colorScheme.error,
                        style    = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // PIN pad
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier            = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("⌫", "0", "✓")
                    ).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier              = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { key ->
                                val isAction = key == "✓" || key == "⌫"
                                OutlinedButton(
                                    onClick = {
                                        when (key) {
                                            "⌫"  -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            "✓"  -> if (selectedUsername.isNotBlank() && pin.isNotBlank() && !loading) {
                                                        vm.login(selectedUsername, pin)
                                                    }
                                            else -> if (pin.length < 12) pin += key
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(64.dp),
                                    shape    = RoundedCornerShape(12.dp),
                                    enabled  = !loading,
                                    colors   = when (key) {
                                        "✓"  -> ButtonDefaults.outlinedButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    contentColor   = MaterialTheme.colorScheme.primary)
                                        "⌫"  -> ButtonDefaults.outlinedButtonColors(
                                                    contentColor   = MaterialTheme.colorScheme.error)
                                        else -> ButtonDefaults.outlinedButtonColors()
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    if (loading && key == "✓") {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(
                                            text       = key,
                                            fontSize   = if (isAction) 20.sp else 22.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign  = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()

            // ── Connection settings ───────────────────────────────────────────
            TextButton(
                onClick  = onChangeServer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, null, Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Connection Settings", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "MealFlow  ·  Powered by Rubix Solutions",
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}
