@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.cashdrawer

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
import com.fastpos.android.data.models.CashTransaction
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.CashDrawerViewModel
import java.text.SimpleDateFormat
import java.util.*

private val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())

@Composable
fun CashDrawerScreen(
    onNavigateBack: () -> Unit,
    vm: CashDrawerViewModel = hiltViewModel()
) {
    val transactions by vm.transactions.collectAsState()
    val totalIn      by vm.totalIn.collectAsState()
    val totalOut     by vm.totalOut.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    val message      by vm.message.collectAsState()
    val settings     by vm.session.settings.collectAsState()
    val shift        by vm.session.currentShift.collectAsState()
    val sym          = settings.currencySymbol

    val snackbar    = remember { SnackbarHostState() }
    var showForm    by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CashTransaction?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    // Delete confirmation
    deleteTarget?.let { tx ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Entry") },
            text  = { Text("Remove ${tx.transactionType} entry of ${tx.amount.formatCurrency(sym)}?") },
            confirmButton = {
                TextButton(onClick = { vm.deleteTransaction(tx.transactionId); deleteTarget = null }) {
                    Text("Delete", color = RedError)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    // Add transaction sheet
    if (showForm) {
        AddTransactionSheet(
            shift      = shift,
            isLoading  = isLoading,
            sym        = sym,
            onDismiss  = { showForm = false },
            onConfirm  = { type, amt, reason, notes ->
                vm.addTransaction(type, amt, reason, notes)
                showForm = false
            }
        )
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cash Drawer")
                        shift?.let {
                            Text("Shift: ${it.shiftCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = vm::load, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showForm = true },
                containerColor = if (shift != null) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.outline
            ) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Totals summary bar ──────────────────────────────────────────
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TotalChip("Cash In",  totalIn,    sym, GreenSuccess)
                    VerticalDivider(Modifier.height(32.dp))
                    TotalChip("Cash Out", totalOut,   sym, RedError)
                    VerticalDivider(Modifier.height(32.dp))
                    TotalChip("Net",      vm.netCash, sym, if (vm.netCash >= 0) GreenSuccess else RedError, bold = true)
                }
            }

            if (shift == null) {
                Surface(color = RedError.copy(alpha = 0.1f)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, null, tint = RedError, modifier = Modifier.size(16.dp))
                        Text("No shift open — transactions cannot be recorded.",
                            style = MaterialTheme.typography.bodySmall, color = RedError)
                    }
                }
            }

            // ── Transaction list ────────────────────────────────────────────
            if (isLoading && transactions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (transactions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Inbox, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No transactions this shift",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge)
                        if (shift != null)
                            Text("Tap + to record a cash movement",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(transactions, key = { "${it.transactionType}_${it.transactionId}" }) { tx ->
                        TransactionCard(tx = tx, sym = sym, onDelete = { deleteTarget = tx })
                    }
                    // Bottom padding for FAB
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TotalChip(label: String, amount: Double, sym: String, color: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(amount.formatCurrency(sym),
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
            color = color)
    }
}

@Composable
private fun TransactionCard(tx: CashTransaction, sym: String, onDelete: () -> Unit) {
    val (bg, fg) = when (tx.transactionType) {
        "In"   -> GreenSuccess.copy(alpha = 0.08f) to GreenSuccess
        "Sale" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) to MaterialTheme.colorScheme.primary
        else   -> RedError.copy(alpha = 0.08f) to RedError
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, fg.copy(alpha = 0.4f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Type badge
            Surface(color = fg.copy(alpha = 0.18f), shape = MaterialTheme.shapes.small) {
                Text(
                    tx.transactionType.uppercase(),
                    Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
            }

            // Reason + time (takes all remaining space)
            Column(Modifier.weight(1f)) {
                Text(
                    tx.reason.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (tx.notes.isNotBlank()) {
                    Text(tx.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(timeFmt.format(tx.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Amount
            Text(
                tx.amount.formatCurrency(sym),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = fg
            )

            if (tx.transactionType != "Sale" && tx.reason != "Opening Cash") {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.DeleteOutline, null, tint = RedError, modifier = Modifier.size(18.dp))
                }
            } else {
                Icon(Icons.Default.Lock, null, Modifier.size(18.dp).padding(7.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun AddTransactionSheet(
    shift:     com.fastpos.android.data.models.Shift?,
    isLoading: Boolean,
    sym:       String,
    onDismiss: () -> Unit,
    onConfirm: (type: String, amount: Double, reason: String, notes: String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var reason     by remember { mutableStateOf("") }
    var notes      by remember { mutableStateOf("") }
    var error      by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Record Cash Movement",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
            HorizontalDivider()

            if (shift == null) {
                Card(colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.1f))) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Warning, null, tint = RedError, modifier = Modifier.size(16.dp))
                        Text("No shift open. Open a shift first.",
                            style = MaterialTheme.typography.bodySmall, color = RedError)
                    }
                }
            }

            error?.let {
                Text(it, color = RedError, style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = amountText, onValueChange = { amountText = it; error = null },
                label = { Text("Amount *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                enabled = shift != null && !isLoading,
                leadingIcon = { Text(sym, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp)) }
            )
            OutlinedTextField(
                value = reason, onValueChange = { reason = it; error = null },
                label = { Text("Reason *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = shift != null && !isLoading
            )
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = shift != null && !isLoading
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        when {
                            amt <= 0       -> error = "Enter a valid amount"
                            reason.isBlank()-> error = "Reason is required"
                            else -> onConfirm("In", amt, reason, notes)
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled  = shift != null && !isLoading,
                    colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("CASH IN", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        when {
                            amt <= 0        -> error = "Enter a valid amount"
                            reason.isBlank() -> error = "Reason is required"
                            else -> onConfirm("Out", amt, reason, notes)
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled  = shift != null && !isLoading,
                    colors   = ButtonDefaults.buttonColors(containerColor = RedError)
                ) {
                    Icon(Icons.Default.Remove, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("CASH OUT", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
