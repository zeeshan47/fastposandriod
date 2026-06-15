@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.vouchers

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.Voucher
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.VouchersViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

@Composable
fun VouchersScreen(
    onNavigateBack: () -> Unit,
    vm: VouchersViewModel = hiltViewModel()
) {
    val vouchers   by vm.vouchers.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val saveResult by vm.saveResult.collectAsState()
    val settings   by vm.session.settings.collectAsState()

    var showAddDialog  by remember { mutableStateOf(false) }
    var editTarget     by remember { mutableStateOf<Voucher?>(null) }
    var deleteTarget   by remember { mutableStateOf<Voucher?>(null) }

    val snackHost = remember { SnackbarHostState() }
    LaunchedEffect(saveResult) {
        if (saveResult != null) {
            snackHost.showSnackbar(saveResult!!)
            vm.clearResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vouchers & Coupons") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Voucher")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snackHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon    = { Icon(Icons.Default.Add, null) },
                text    = { Text("New Voucher") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        if (isLoading && vouchers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (vouchers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LocalOffer, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                    Text("No vouchers yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Create discount codes for customers", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(vouchers, key = { it.voucherId }) { v ->
                    VoucherCard(
                        voucher        = v,
                        currency       = settings.currencySymbol,
                        onEdit         = { editTarget = v },
                        onDelete       = { deleteTarget = v },
                        onToggleActive = { vm.toggleActive(v) }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        VoucherDialog(
            initial   = null,
            onDismiss = { showAddDialog = false },
            onSave    = { code, desc, type, value, minAmt, maxU, expiry ->
                vm.createVoucher(code, desc, type, value, minAmt, maxU, expiry)
                showAddDialog = false
            }
        )
    }

    editTarget?.let { v ->
        VoucherDialog(
            initial   = v,
            onDismiss = { editTarget = null },
            onSave    = { code, desc, type, value, minAmt, maxU, expiry ->
                vm.updateVoucher(v.voucherId, code, desc, type, value, minAmt, maxU, expiry, v.isActive)
                editTarget = null
            }
        )
    }

    deleteTarget?.let { v ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title   = { Text("Deactivate Voucher") },
            text    = { Text("Deactivate \"${v.voucherCode}\"? It will no longer be usable at POS.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteVoucher(v.voucherId); deleteTarget = null }) {
                    Text("Deactivate", color = RedError)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun VoucherCard(
    voucher:        Voucher,
    currency:       String,
    onEdit:         () -> Unit,
    onDelete:       () -> Unit,
    onToggleActive: () -> Unit
) {
    val dtFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val isExpired = voucher.expiryDate != null && voucher.expiryDate.before(
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.time
    )
    val limitReached = voucher.maxUses > 0 && voucher.usedCount >= voucher.maxUses

    val statusColor = when {
        !voucher.isActive || isExpired || limitReached -> MaterialTheme.colorScheme.outline
        else -> GreenSuccess
    }
    val statusLabel = when {
        !voucher.isActive  -> "Inactive"
        isExpired          -> "Expired"
        limitReached       -> "Limit Reached"
        else               -> "Active"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.Top
        ) {
            // Discount badge
            Surface(
                color        = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape        = MaterialTheme.shapes.small,
                modifier     = Modifier.width(72.dp)
            ) {
                Column(
                    Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (voucher.discountType == "Percent")
                            "${voucher.discountValue.toInt()}%"
                        else
                            "$currency${voucher.discountValue.toInt()}",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (voucher.discountType == "Percent") "OFF" else "FLAT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Details
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(voucher.voucherCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        color    = statusColor.copy(alpha = 0.15f),
                        shape    = MaterialTheme.shapes.extraSmall,
                        onClick  = { if (!isExpired && !limitReached) onToggleActive() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
                            if (!isExpired && !limitReached) {
                                Icon(
                                    if (voucher.isActive) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                                    null,
                                    modifier = Modifier.size(14.dp),
                                    tint = statusColor
                                )
                            }
                        }
                    }
                }
                if (voucher.description.isNotBlank())
                    Text(voucher.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (voucher.minOrderAmount > 0)
                        MetaChip("Min: $currency${voucher.minOrderAmount.toInt()}")
                    if (voucher.maxUses > 0)
                        MetaChip("Uses: ${voucher.usedCount}/${voucher.maxUses}")
                    else if (voucher.usedCount > 0)
                        MetaChip("Used: ${voucher.usedCount}x")
                    if (voucher.expiryDate != null)
                        MetaChip("Exp: ${dtFmt.format(voucher.expiryDate)}")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = AmberWarning)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, null, Modifier.size(18.dp), tint = RedError)
                }
            }
        }
    }
}

@Composable
private fun MetaChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Text(
            label,
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun VoucherDialog(
    initial:   Voucher?,
    onDismiss: () -> Unit,
    onSave:    (String, String, String, Double, Double, Int, Date?) -> Unit
) {
    val dtFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()) }

    var code          by remember { mutableStateOf(initial?.voucherCode ?: "") }
    var description   by remember { mutableStateOf(initial?.description ?: "") }
    var discountType  by remember { mutableStateOf(initial?.discountType ?: "Percent") }
    var discountValue by remember { mutableStateOf(if (initial != null) initial.discountValue.toString() else "") }
    var minOrder      by remember { mutableStateOf(if ((initial?.minOrderAmount ?: 0.0) > 0) initial!!.minOrderAmount.toString() else "") }
    var maxUses       by remember { mutableStateOf(if ((initial?.maxUses ?: 0) > 0) initial!!.maxUses.toString() else "") }
    var expiryText    by remember { mutableStateOf(if (initial?.expiryDate != null) dtFmt.format(initial.expiryDate) else "") }
    var codeError     by remember { mutableStateOf<String?>(null) }
    var valueError    by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Voucher" else "Edit Voucher") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value         = code,
                        onValueChange = { code = it.uppercase(); codeError = null },
                        label         = { Text("Voucher Code *") },
                        placeholder   = { Text("e.g. SAVE20") },
                        isError       = codeError != null,
                        supportingText = codeError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                    OutlinedButton(
                        onClick = {
                            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                            code = (1..7).map { chars[Random.nextInt(chars.length)] }.joinToString("")
                            codeError = null
                        },
                        modifier = Modifier.padding(top = if (codeError != null) 0.dp else 8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Gen", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Description") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )

                // Discount type toggle
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Percent", "Fixed").forEach { t ->
                        FilterChip(
                            selected = discountType == t,
                            onClick  = { discountType = t },
                            label    = { Text(if (t == "Percent") "% Off" else "Fixed Amount") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value         = discountValue,
                    onValueChange = { discountValue = it; valueError = null },
                    label         = { Text(if (discountType == "Percent") "Discount %" else "Discount Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError       = valueError != null,
                    supportingText = valueError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value         = minOrder,
                        onValueChange = { minOrder = it },
                        label         = { Text("Min Order") },
                        placeholder   = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                    OutlinedTextField(
                        value         = maxUses,
                        onValueChange = { maxUses = it },
                        label         = { Text("Max Uses") },
                        placeholder   = { Text("∞") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                }

                OutlinedTextField(
                    value         = expiryText,
                    onValueChange = { expiryText = it },
                    label         = { Text("Expiry (dd/MM/yyyy)") },
                    placeholder   = { Text("Leave blank = no expiry") },
                    leadingIcon   = { Icon(Icons.Default.CalendarToday, null) },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                codeError  = if (code.isBlank()) "Required" else null
                valueError = if (discountValue.toDoubleOrNull() == null || discountValue.toDouble() <= 0) "Enter valid amount" else null
                if (codeError != null || valueError != null) return@TextButton

                val expDate: Date? = expiryText.takeIf { it.isNotBlank() }?.let {
                    try { dtFmt.parse(it) } catch (_: Exception) { null }
                }

                onSave(
                    code,
                    description,
                    discountType,
                    discountValue.toDouble(),
                    minOrder.toDoubleOrNull() ?: 0.0,
                    maxUses.toIntOrNull() ?: 0,
                    expDate
                )
            }) {
                Text(if (initial == null) "Create" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
