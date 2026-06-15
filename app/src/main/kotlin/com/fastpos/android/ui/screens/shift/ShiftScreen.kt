@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.shift

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.utils.formatDateTime
import com.fastpos.android.viewmodels.ShiftViewModel

@Composable
fun ShiftScreen(
    onNavigateBack: (() -> Unit)? = null,
    vm: ShiftViewModel = hiltViewModel()
) {

    val shift             by vm.shift.collectAsState()
    val expenses          by vm.expenses.collectAsState()
    val isLoading         by vm.isLoading.collectAsState()
    val message           by vm.message.collectAsState()
    val settings          by vm.session.settings.collectAsState()
    val shiftSummary      by vm.shiftSummary.collectAsState()
    val summaryLoading    by vm.summaryLoading.collectAsState()
    val isPrintingZReport  by vm.isPrintingZReport.collectAsState()
    val cashTotals         by vm.cashTotals.collectAsState()
    val zReportPreviewText by vm.zReportPreviewText.collectAsState()
    val pendingOrderCount  by vm.pendingOrderCount.collectAsState()
    val closeSheetRequest  by vm.closeSheetRequest.collectAsState()
    val snack           = remember { SnackbarHostState() }

    var openingCash       by remember { mutableStateOf("") }
    var closingCash       by remember { mutableStateOf("") }
    var closeNotes        by remember { mutableStateOf("") }
    var showZReport       by remember { mutableStateOf(false) }
    var showExpense       by remember { mutableStateOf(false) }
    var expType           by remember { mutableStateOf("") }
    var expDesc           by remember { mutableStateOf("") }
    var expAmount         by remember { mutableStateOf("") }
    var expPaidTo         by remember { mutableStateOf("") }
    var expPaymentMethod  by remember { mutableStateOf("Cash") }
    var editingExpenseId  by remember { mutableStateOf<Int?>(null) }
    var deleteConfirmId   by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    LaunchedEffect(shift) {
        if (shift == null) showZReport = false
    }

    LaunchedEffect(closeSheetRequest) {
        if (closeSheetRequest > 0) showZReport = true
    }

    // Z-Report preview dialog (shown when no printer is configured or print fails)
    zReportPreviewText?.let { reportText ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.clearZReportPreview() },
            title = {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.Receipt, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)
                    )
                    androidx.compose.material3.Text("Z-Report Preview")
                }
            },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    androidx.compose.material3.Text(
                        text       = reportText,
                        style      = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { vm.clearZReportPreview() }) {
                    androidx.compose.material3.Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shift Management") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) }
                    }
                },
                actions = { IconButton(onClick = vm::loadShift) { Icon(Icons.Default.Refresh, null) } },
                colors  = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) },
        floatingActionButton = {
            if (shift != null) {
                FloatingActionButton(onClick = { showExpense = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "Add Expense")
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (shift == null) {
                // No open shift
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = AmberWarning)
                            Text("No Shift Open", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Open a shift to start taking orders and tracking sales.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(
                                value = openingCash,
                                onValueChange = { openingCash = it },
                                label = { Text("Opening Cash (${settings.currencySymbol})") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Button(
                                onClick = { vm.openShift(openingCash.toDoubleOrNull() ?: 0.0) },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                            ) {
                                Icon(Icons.Default.LockOpen, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Open Shift", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            } else {
                // Shift is open
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GreenSuccess.copy(0.1f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GreenSuccess)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LockOpen, null, tint = GreenSuccess)
                                Spacer(Modifier.width(8.dp))
                                Text("Shift Open", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = GreenSuccess)
                            }
                            HorizontalDivider()
                            InfoRow("Shift Code", shift!!.shiftCode)
                            InfoRow("Opened At", shift!!.openingTime.formatDateTime())
                            InfoRow("Opening Cash", shift!!.openingCash.formatCurrency(settings.currencySymbol))
                            InfoRow("Total Sales", shift!!.totalSales.formatCurrency(settings.currencySymbol))
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { vm.requestCloseShiftSheet() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Lock, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Close Shift")
                            }
                        }
                    }
                }

                // Expenses
                if (expenses.isNotEmpty()) {
                    item { Text("Expenses (${expenses.size})", style = MaterialTheme.typography.titleMedium) }
                    items(expenses) { exp ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(exp.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (exp.expenseType.isNotBlank()) Text(exp.expenseType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (!exp.paymentMethod.equals("Cash", ignoreCase = true)) {
                                            Text("• ${exp.paymentMethod}", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                                        }
                                    }
                                    if (exp.paidTo.isNotBlank()) Text("To: ${exp.paidTo}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(exp.amount.formatCurrency(settings.currencySymbol), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RedError)
                                    IconButton(onClick = {
                                        editingExpenseId = exp.expenseId
                                        expType = exp.expenseType; expDesc = exp.description
                                        expAmount = exp.amount.toString(); expPaidTo = exp.paidTo
                                        expPaymentMethod = exp.paymentMethod
                                    }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Edit, null, Modifier.size(15.dp), tint = AmberWarning)
                                    }
                                    IconButton(onClick = { deleteConfirmId = exp.expenseId }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Delete, null, Modifier.size(15.dp), tint = RedError)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Z-Report + close shift bottom sheet
    if (showZReport && shift != null) {
        ModalBottomSheet(
            onDismissRequest = { showZReport = false },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            val sym           = settings.currencySymbol
            val totalExpenses = expenses.sumOf { it.amount }
            val cashExpenses  = expenses.filter { it.paymentMethod.equals("Cash", ignoreCase = true) }.sumOf { it.amount }
            val cashSales     = shiftSummary.firstOrNull { it.method.equals("Cash", ignoreCase = true) }?.amount ?: 0.0
            val expectedClose = shift!!.openingCash + cashSales + cashTotals.cashTxIn - cashExpenses - cashTotals.cashTxOut
            val closingAmt    = closingCash.toDoubleOrNull() ?: 0.0
            val variance      = closingAmt - expectedClose

            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Assessment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Z-Report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Shift ${shift!!.shiftCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()

                // Shift info
                ZRow("Opened At",    shift!!.openingTime.formatDateTime())
                ZRow("Opening Cash", shift!!.openingCash.formatCurrency(sym))
                ZRow("Total Sales",  shift!!.totalSales.formatCurrency(sym), bold = true)

                // Payment breakdown
                if (summaryLoading) {
                    Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (shiftSummary.isNotEmpty()) {
                    Text("Payment Breakdown", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    shiftSummary.forEach { s ->
                        ZRow("${s.method} (${s.txCount}x)", s.amount.formatCurrency(sym))
                    }
                }

                HorizontalDivider()

                // Expenses summary (all, for information)
                ZRow("Total Expenses", totalExpenses.formatCurrency(sym), color = RedError)

                // Cash reconciliation
                Text("Cash Reconciliation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ZRow("Opening Cash",    shift!!.openingCash.formatCurrency(sym))
                ZRow("+ Cash Sales",    cashSales.formatCurrency(sym))
                if (cashTotals.cashTxIn > 0)
                    ZRow("+ Cash In",   cashTotals.cashTxIn.formatCurrency(sym),  color = GreenSuccess)
                ZRow("- Cash Expenses", cashExpenses.formatCurrency(sym),          color = RedError)
                if (cashTotals.cashTxOut > 0)
                    ZRow("- Cash Out",  cashTotals.cashTxOut.formatCurrency(sym),  color = RedError)
                ZRow("Expected Cash",   expectedClose.formatCurrency(sym), bold = true)

                HorizontalDivider()

                OutlinedTextField(
                    value = closingCash, onValueChange = { closingCash = it },
                    label = { Text("Closing Cash (${sym})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    trailingIcon = {
                        val color = if (kotlin.math.abs(variance) < 1.0) GreenSuccess
                                    else if (variance < 0) RedError else AmberWarning
                        Text(
                            text  = "${if (variance >= 0) "+" else ""}${"%.2f".format(variance)}",
                            color = color,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
                OutlinedTextField(
                    value = closeNotes, onValueChange = { closeNotes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2
                )

                OutlinedButton(
                    onClick  = { vm.printZReport() },
                    enabled  = !isPrintingZReport,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isPrintingZReport) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("Printing…")
                    } else {
                        Icon(Icons.Default.Print, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print Z-Report")
                    }
                }

                Button(
                    onClick = {
                        vm.closeShift(closingCash.toDoubleOrNull() ?: 0.0, closeNotes)
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = RedError)
                ) {
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm Close Shift", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }

    // Edit expense dialog
    if (editingExpenseId != null) {
        var editMethodExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editingExpenseId = null },
            title = { Text("Edit Expense") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = expType,   onValueChange = { expType   = it }, label = { Text("Type") },         modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = expDesc,   onValueChange = { expDesc   = it }, label = { Text("Description *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = expAmount, onValueChange = { expAmount = it }, label = { Text("Amount *") },      modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = expPaidTo, onValueChange = { expPaidTo = it }, label = { Text("Paid To") },       modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Box {
                        OutlinedButton(onClick = { editMethodExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(expPaymentMethod, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = editMethodExpanded, onDismissRequest = { editMethodExpanded = false }) {
                            listOf("Cash", "Card", "Bank Transfer", "Other").forEach { m ->
                                DropdownMenuItem(
                                    text    = { Text(m) },
                                    onClick = { expPaymentMethod = m; editMethodExpanded = false },
                                    leadingIcon = if (m == expPaymentMethod) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val id = editingExpenseId ?: return@Button
                    if (expDesc.isNotBlank()) {
                        vm.editExpense(id, expType, expDesc, expAmount.toDoubleOrNull() ?: 0.0, expPaidTo, expPaymentMethod)
                        expType = ""; expDesc = ""; expAmount = ""; expPaidTo = ""; expPaymentMethod = "Cash"
                        editingExpenseId = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingExpenseId = null }) { Text("Cancel") } }
        )
    }

    // Delete expense confirmation
    deleteConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Delete Expense") },
            text  = { Text("Remove this expense from the shift?") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteExpense(id); deleteConfirmId = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = RedError)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteConfirmId = null }) { Text("Cancel") } }
        )
    }

    // Add expense dialog
    if (showExpense) {
        var addMethodExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showExpense = false },
            title = { Text("Add Expense") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = expType, onValueChange = { expType = it }, label = { Text("Type (e.g. Utility, Rent)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = expDesc, onValueChange = { expDesc = it }, label = { Text("Description *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = expAmount, onValueChange = { expAmount = it }, label = { Text("Amount *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = expPaidTo, onValueChange = { expPaidTo = it }, label = { Text("Paid To") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Box {
                        OutlinedButton(onClick = { addMethodExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(expPaymentMethod, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = addMethodExpanded, onDismissRequest = { addMethodExpanded = false }) {
                            listOf("Cash", "Card", "Bank Transfer", "Other").forEach { m ->
                                DropdownMenuItem(
                                    text    = { Text(m) },
                                    onClick = { expPaymentMethod = m; addMethodExpanded = false },
                                    leadingIcon = if (m == expPaymentMethod) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (expDesc.isNotBlank() && expAmount.isNotBlank()) {
                        vm.addExpense(expType, expDesc, expAmount.toDoubleOrNull() ?: 0.0, expPaidTo, expPaymentMethod)
                        expType = ""; expDesc = ""; expAmount = ""; expPaidTo = ""; expPaymentMethod = "Cash"
                        showExpense = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showExpense = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ZRow(
    label: String,
    value: String,
    bold:  Boolean = false,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}
