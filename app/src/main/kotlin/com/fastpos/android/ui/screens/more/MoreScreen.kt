@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.GitHubUpdateManager
import com.fastpos.android.utils.LicenseStatus
import com.fastpos.android.utils.PermissionKeys
import com.fastpos.android.viewmodels.ActivationViewModel
import com.fastpos.android.viewmodels.SettingsViewModel
import com.fastpos.android.viewmodels.ShiftViewModel
import kotlinx.coroutines.launch

data class MoreMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val permKey: String? = null
)

private data class MoreSection(
    val title: String,
    val accentColor: Color,
    val items: List<MoreMenuItem>
)

@Composable
fun MoreScreen(
    onNavigateToShift:          () -> Unit,
    onNavigateToSettings:       () -> Unit,
    onNavigateToReports:        () -> Unit,
    onNavigateToProducts:       () -> Unit,
    onNavigateToCustomers:      () -> Unit,
    onNavigateToTables:         () -> Unit,
    onNavigateToInventory:      () -> Unit,
    onNavigateToEmployees:      () -> Unit,
    onNavigateToAttendance:     () -> Unit,
    onNavigateToPayroll:        () -> Unit,
    onNavigateToExpenses:       () -> Unit,
    onNavigateToWaiters:        () -> Unit,
    onNavigateToPurchases:      () -> Unit,
    onNavigateToTax:            () -> Unit,
    onNavigateToDeals:          () -> Unit,
    onNavigateToReservations:   () -> Unit,
    onNavigateToDelivery:       () -> Unit,
    onNavigateToUserManagement: () -> Unit,
    onNavigateToVouchers:       () -> Unit,
    onNavigateToCashDrawer:     () -> Unit,
    onNavigateToAuditLog:       () -> Unit,
    onNavigateToWaste:          () -> Unit,
    onNavigateToSuppliers:      () -> Unit,
    onNavigateToRecipes:        () -> Unit,
    onNavigateToProductLedger:  () -> Unit,
    onNavigateToModifiers:      () -> Unit,
    onNavigateToBranch:         () -> Unit,
    onNavigateToMessageLog:     () -> Unit,
    onNavigateToActivation:     () -> Unit,
    onNavigateToRaastQr:        () -> Unit,
    onNavigateToAccountLedger:  () -> Unit,
    onLogout:                   () -> Unit,
    vm: ShiftViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
    activationVm: ActivationViewModel = hiltViewModel()
) {
    val session     = vm.session
    val user        by session.currentUser.collectAsState()
    val shift       by session.currentShift.collectAsState()
    val permissions by session.permissions.collectAsState()
    val isLocalMode by settingsVm.isLocalMode.collectAsState()
    val licenseInfo by activationVm.licenseInfo.collectAsState()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var updateInfo by remember { mutableStateOf<GitHubUpdateManager.UpdateInfo?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateBusyText by remember { mutableStateOf("") }

    fun canAccess(key: String?): Boolean =
        key == null || permissions.contains(key) || user?.roleName == "Admin"

    fun checkForAppUpdate() {
        if (updateBusy) return
        scope.launch {
            updateBusy = true
            updateBusyText = "Checking for update..."
            when (val result = GitHubUpdateManager.checkForUpdate(context)) {
                is GitHubUpdateManager.CheckResult.Available -> updateInfo = result.info
                is GitHubUpdateManager.CheckResult.UpToDate -> {
                    snack.showSnackbar("FastPOS is up to date (${result.currentVersion}).")
                }
                is GitHubUpdateManager.CheckResult.NotConfigured -> snack.showSnackbar(result.message)
                is GitHubUpdateManager.CheckResult.Error -> snack.showSnackbar(result.message)
            }
            updateBusy = false
            updateBusyText = ""
        }
    }

    fun downloadAndInstallUpdate(info: GitHubUpdateManager.UpdateInfo) {
        if (updateBusy) return
        scope.launch {
            updateBusy = true
            updateBusyText = "Downloading update..."
            try {
                val apkFile = GitHubUpdateManager.downloadApk(context, info)
                when (val installResult = GitHubUpdateManager.installApk(context, apkFile)) {
                    GitHubUpdateManager.InstallResult.Started -> {
                        updateInfo = null
                        snack.showSnackbar("Android installer opened.")
                    }
                    is GitHubUpdateManager.InstallResult.PermissionRequired -> {
                        snack.showSnackbar(installResult.message)
                    }
                    is GitHubUpdateManager.InstallResult.Error -> {
                        snack.showSnackbar(installResult.message)
                    }
                }
            } catch (e: Exception) {
                snack.showSnackbar(e.message ?: "Unable to download update.")
            }
            updateBusy = false
            updateBusyText = ""
        }
    }

    // ── Sections mirror WPF sidebar: SETUP / OPERATIONS / INVENTORY / ADMIN ──
    val allSections = listOf(
        MoreSection(
            title       = "SETUP",
            accentColor = MaterialTheme.colorScheme.primary,
            items       = listOf(
                MoreMenuItem("Products",     Icons.Default.Fastfood,        onNavigateToProducts,    PermissionKeys.PRODUCTS_VIEW),
                MoreMenuItem("Tax",          Icons.Default.Percent,         onNavigateToTax,         PermissionKeys.SETTINGS_MANAGE),
                MoreMenuItem("Modifiers",    Icons.Default.Tune,            onNavigateToModifiers,   PermissionKeys.MODIFIERS_MANAGE),
                MoreMenuItem("Deals",        Icons.Default.LocalOffer,      onNavigateToDeals,       PermissionKeys.DEALS_MANAGE),
                MoreMenuItem("Vouchers",     Icons.Default.Redeem,          onNavigateToVouchers,    PermissionKeys.SETTINGS_MANAGE),
                MoreMenuItem("Tables",       Icons.Default.TableRestaurant, onNavigateToTables,      PermissionKeys.TABLES_MANAGE),
                MoreMenuItem("Reservations", Icons.Default.EventNote,       onNavigateToReservations,PermissionKeys.TABLES_MANAGE),
                MoreMenuItem("Customers",    Icons.Default.People,          onNavigateToCustomers,   PermissionKeys.CUSTOMERS_MANAGE),
                MoreMenuItem("Waiters",      Icons.Default.SupportAgent,    onNavigateToWaiters,     PermissionKeys.WAITERS_MANAGE),
                MoreMenuItem("Employees",    Icons.Default.Badge,           onNavigateToEmployees,   PermissionKeys.SETTINGS_MANAGE),
                MoreMenuItem("Payroll",      Icons.Default.Payments,        onNavigateToPayroll,     PermissionKeys.SETTINGS_MANAGE),
            )
        ),
        MoreSection(
            title       = "OPERATIONS",
            accentColor = MaterialTheme.colorScheme.primary,
            items       = listOf(
                MoreMenuItem("Shift",       Icons.Default.WatchLater,      onNavigateToShift,       PermissionKeys.SHIFT_OPEN),
                MoreMenuItem("Expenses",    Icons.Default.MoneyOff,        onNavigateToExpenses,    PermissionKeys.EXPENSES_MANAGE),
                MoreMenuItem("Cash Drawer", Icons.Default.Payments,        onNavigateToCashDrawer,  PermissionKeys.EXPENSES_MANAGE),
                MoreMenuItem("Delivery",    Icons.Default.DeliveryDining,  onNavigateToDelivery,    PermissionKeys.POS_DELIVERY),
                MoreMenuItem("Raast QR",    Icons.Default.QrCode,          onNavigateToRaastQr,     null),
                // Attendance is now inside Employee Management (4th tab)
            )
        ),
        MoreSection(
            title       = "INVENTORY",
            accentColor = MaterialTheme.colorScheme.primary,
            items       = listOf(
                MoreMenuItem("Purchases",      Icons.Default.ShoppingCart, onNavigateToPurchases,    PermissionKeys.INVENTORY_PURCHASE),
                MoreMenuItem("Suppliers",      Icons.Default.Business,     onNavigateToSuppliers,    PermissionKeys.INVENTORY_PURCHASE),
                MoreMenuItem("Inventory",      Icons.Default.Inventory,    onNavigateToInventory,    PermissionKeys.INVENTORY_STOCK),
                MoreMenuItem("Recipes",        Icons.Default.MenuBook,     onNavigateToRecipes,      PermissionKeys.INVENTORY_RECIPE),
                MoreMenuItem("Waste",          Icons.Default.Delete,       onNavigateToWaste,        PermissionKeys.INVENTORY_WASTE),
                MoreMenuItem("Product Ledger", Icons.Default.ListAlt,      onNavigateToProductLedger,PermissionKeys.REPORTS_VIEW),
            )
        ),
        MoreSection(
            title       = "ADMIN",
            accentColor = MaterialTheme.colorScheme.primary,
            items       = listOf(
                MoreMenuItem("Reports",     Icons.Default.BarChart,        onNavigateToReports,         PermissionKeys.REPORTS_VIEW),
                MoreMenuItem("Users",       Icons.Default.ManageAccounts,  onNavigateToUserManagement,  PermissionKeys.USERS_MANAGE),
                MoreMenuItem("Settings",    Icons.Default.Settings,        onNavigateToSettings,        PermissionKeys.SETTINGS_MANAGE),
                MoreMenuItem("Message Log", Icons.Default.Sms,             onNavigateToMessageLog,      PermissionKeys.SETTINGS_MANAGE),
                MoreMenuItem("Audit Log",   Icons.Default.History,         onNavigateToAuditLog,        PermissionKeys.SETTINGS_MANAGE),
                MoreMenuItem("Accounts",    Icons.Default.AccountBalance,  onNavigateToAccountLedger,   PermissionKeys.REPORTS_VIEW),
                MoreMenuItem("Branch",      Icons.Default.Store,           onNavigateToBranch,          PermissionKeys.SETTINGS_MANAGE),
                MoreMenuItem("Update App",  Icons.Default.SystemUpdate,    ::checkForAppUpdate,         PermissionKeys.SETTINGS_MANAGE),
                MoreMenuItem("Activation",  Icons.Default.VpnKey,          onNavigateToActivation,      null),
            )
        ),
    )

    val sections = allSections.mapNotNull { section ->
        val filtered = section.items.filter { canAccess(it.permKey) }
        if (filtered.isEmpty()) null else section.copy(items = filtered)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("More") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── User & Shift card ───────────────────────────────────────────
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.AccountCircle, null,
                                Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(user?.fullName ?: "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold)
                                Text(user?.roleName ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        licenseInfo?.let { info ->
                            val (badgeColor, badgeText) = when (info.status) {
                                LicenseStatus.Valid        -> Pair(GreenSuccess, if (info.daysLeft == Int.MAX_VALUE) "Licensed · Permanent" else "Licensed · ${info.daysLeft}d left")
                                LicenseStatus.Trial        -> Pair(MaterialTheme.colorScheme.primary, "Trial · ${info.daysLeft} day${if (info.daysLeft == 1) "" else "s"} left")
                                LicenseStatus.TrialExpired -> Pair(RedError,     "Trial Expired — Activate License")
                                else                       -> Pair(RedError,     "Unlicensed")
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.VpnKey, null, Modifier.size(14.dp), tint = badgeColor)
                                    Text(badgeText, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.SemiBold)
                                }
                                if (info.status != LicenseStatus.Valid) {
                                    TextButton(
                                        onClick = onNavigateToActivation,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) { Text("Activate", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                            HorizontalDivider()
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (shift != null) Icons.Default.LockOpen else Icons.Default.Lock,
                                    null,
                                    tint = if (shift != null) GreenSuccess else RedError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    if (shift != null) "Shift Open · ${shift!!.shiftCode}"
                                    else "No Shift Open",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (shift != null) GreenSuccess else RedError
                                )
                            }
                            TextButton(onClick = onNavigateToShift) { Text("Manage") }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Sections ────────────────────────────────────────────────────
            sections.forEach { section ->
                item {
                    SectionHeader(title = section.title, color = section.accentColor)
                    Spacer(Modifier.height(6.dp))
                }

                val rows = section.items.chunked(3)
                items(rows) { rowItems ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            MenuCard(item = item, modifier = Modifier.weight(1f))
                        }
                        repeat(3 - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                item { Spacer(Modifier.height(4.dp)) }
            }

            // ── Logout ──────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick  = onLogout,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedError)
                ) {
                    Icon(Icons.Default.Logout, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Logout", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    updateInfo?.let { info ->
        UpdateAvailableDialog(
            info = info,
            busy = updateBusy,
            busyText = updateBusyText,
            onInstall = { downloadAndInstallUpdate(info) },
            onDismiss = { if (!updateBusy) updateInfo = null }
        )
    }
}

@Composable
private fun UpdateAvailableDialog(
    info: GitHubUpdateManager.UpdateInfo,
    busy: Boolean,
    busyText: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Available") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Version ${info.versionName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (info.releaseName.isNotBlank()) {
                    Text(info.releaseName, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "File: ${info.apkName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (info.releaseNotes.isNotBlank()) {
                    HorizontalDivider()
                    Text(
                        info.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (busy) {
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(busyText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onInstall, enabled = !busy) {
                Text("Download & Install")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            Modifier.width(12.dp),
            color = color.copy(alpha = 0.5f),
            thickness = 1.5.dp
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider(
            Modifier.weight(1f),
            color = color.copy(alpha = 0.5f),
            thickness = 1.5.dp
        )
    }
}

@Composable
private fun MenuCard(item: MoreMenuItem, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Card(
        onClick  = item.onClick,
        colors   = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f)),
        border   = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier.height(78.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            verticalArrangement   = Arrangement.Center,
            horizontalAlignment   = Alignment.CenterHorizontally
        ) {
            Icon(item.icon, null, Modifier.size(24.dp), tint = accent)
            Spacer(Modifier.height(4.dp))
            Text(
                item.label,
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color      = accent,
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}
