@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.expenses

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.Expense
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.utils.formatDateTime
import com.fastpos.android.viewmodels.ExpensesViewModel
import java.text.SimpleDateFormat
import java.util.*

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun ExpensesScreen(
    onNavigateBack: () -> Unit,
    vm: ExpensesViewModel = hiltViewModel()
) {
    val expenses   by vm.expenses.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val message    by vm.message.collectAsState()
    val total      by vm.total.collectAsState()
    val fromDate   by vm.fromDate.collectAsState()
    val toDate     by vm.toDate.collectAsState()
    val typeFilter by vm.typeFilter.collectAsState()
    val settings   by vm.session.settings.collectAsState()
    val sym        = settings.currencySymbol

    val context           = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog     by remember { mutableStateOf(false) }
    var editingExpense    by remember { mutableStateOf<Expense?>(null) }
    var deleteTarget      by remember { mutableStateOf<Expense?>(null) }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); vm.clearMessage() }
    }

    if (showAddDialog || editingExpense != null) {
        ExpenseDialog(
            expense  = editingExpense,
            types    = vm.expenseTypes.drop(1),  // drop "All"
            onSave   = { type, desc, amount, paidTo, paymentMethod ->
                if (editingExpense != null)
                    vm.updateExpense(editingExpense!!.expenseId, type, desc, amount, paidTo, paymentMethod)
                else
                    vm.addExpense(type, desc, amount, paidTo, paymentMethod)
                showAddDialog  = false
                editingExpense = null
            },
            onDismiss = { showAddDialog = false; editingExpense = null }
        )
    }

    deleteTarget?.let { expense ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title  = { Text("Delete Expense") },
            text   = { Text("Delete this ${expense.expenseType} expense of ${expense.amount.formatCurrency(sym)}?") },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteExpense(expense.expenseId); deleteTarget = null },
                    colors  = ButtonDefaults.textButtonColors(contentColor = RedError)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                title = { Text("Expenses") },
                actions = {
                    if (expenses.isNotEmpty()) {
                        IconButton(onClick = { vm.exportExpenses(context) }) {
                            Icon(Icons.Default.Download, "Export CSV")
                        }
                    }
                    IconButton(onClick = vm::loadExpenses) { Icon(Icons.Default.Refresh, "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick          = { showAddDialog = true },
                containerColor   = MaterialTheme.colorScheme.primary
            ) { Icon(Icons.Default.Add, null, tint = Color.White) }
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // ── Filter bar ───────────────────────────────────────────────────
            FilterBar(vm, fromDate, toDate, typeFilter)

            // ── Summary card ─────────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.08f)),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MoneyOff, null, tint = RedError, modifier = Modifier.size(20.dp))
                            Text("Total Expenses", style = MaterialTheme.typography.bodyMedium)
                            if (!isLoading && expenses.isNotEmpty()) {
                                Surface(
                                    color = RedError.copy(alpha = 0.12f),
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        "${expenses.size} entries",
                                        style    = MaterialTheme.typography.labelSmall,
                                        color    = RedError,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(total.formatCurrency(sym),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RedError)
                    }
                    if (!isLoading && expenses.isNotEmpty() && typeFilter == "All") {
                        val byType = expenses.groupBy { it.expenseType }
                            .mapValues { (_, v) -> v.sumOf { it.amount } }
                            .entries.sortedByDescending { it.value }
                        if (byType.size > 1) {
                            HorizontalDivider(color = RedError.copy(alpha = 0.15f))
                            byType.forEach { (type, amount) ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically
                                ) {
                                    Text(type, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(amount.formatCurrency(sym),
                                        style      = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color      = RedError.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }
                }
            }

            // ── List ─────────────────────────────────────────────────────────
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                expenses.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.MoneyOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No expenses found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(expenses, key = { it.expenseId }) { expense ->
                        ExpenseCard(
                            expense  = expense,
                            currency = sym,
                            onEdit   = { editingExpense = expense },
                            onDelete = { deleteTarget  = expense }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    vm:         ExpensesViewModel,
    fromDate:   Date,
    toDate:     Date,
    typeFilter: String
) {
    val context = LocalContext.current
    var typeExpanded by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Date range
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { time = fromDate }
                    DatePickerDialog(context, { _, y, m, d ->
                        vm.setFromDate(Calendar.getInstance().apply {
                            set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                        }.time)
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(dateFmt.format(fromDate), style = MaterialTheme.typography.bodySmall)
            }
            Text("—", Modifier.align(Alignment.CenterVertically))
            OutlinedButton(
                onClick = {
                    val cal = Calendar.getInstance().apply { time = toDate }
                    DatePickerDialog(context, { _, y, m, d ->
                        vm.setToDate(Calendar.getInstance().apply {
                            set(y, m, d, 23, 59, 59)
                        }.time)
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(dateFmt.format(toDate), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Type filter chips + apply
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            vm.expenseTypes.forEach { type ->
                FilterChip(
                    selected = typeFilter == type,
                    onClick  = { vm.setTypeFilter(type) },
                    label    = { Text(type, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
private fun ExpenseCard(
    expense:  Expense,
    currency: String,
    onEdit:   () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), color = RedError.copy(alpha = 0.12f)) {
                        Text(
                            expense.expenseType,
                            style    = MaterialTheme.typography.labelSmall,
                            color    = RedError,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                    if (expense.paidTo.isNotBlank()) {
                        Text("→ ${expense.paidTo}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                if (expense.description.isNotBlank()) {
                    Text(expense.description, style = MaterialTheme.typography.bodyMedium)
                }
                Text(expense.expenseDate.formatDateTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(expense.amount.formatCurrency(currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RedError)
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = AmberWarning)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = RedError)
                    }
                }
            }
        }
    }
}

private val PAYMENT_METHODS = listOf("Cash", "Card", "Bank Transfer", "Other")

@Composable
private fun ExpenseDialog(
    expense:   Expense?,
    types:     List<String>,
    onSave:    (type: String, description: String, amount: Double, paidTo: String, paymentMethod: String) -> Unit,
    onDismiss: () -> Unit
) {
    var type          by remember { mutableStateOf(expense?.expenseType ?: "Other") }
    var description   by remember { mutableStateOf(expense?.description ?: "") }
    var amountText    by remember { mutableStateOf(if (expense != null && expense.amount > 0) expense.amount.toString() else "") }
    var paidTo        by remember { mutableStateOf(expense?.paidTo ?: "") }
    var paymentMethod by remember { mutableStateOf(expense?.paymentMethod ?: "Cash") }
    var typeExpanded    by remember { mutableStateOf(false) }
    var methodExpanded  by remember { mutableStateOf(false) }
    var amountError     by remember { mutableStateOf(false) }

    val isEdit = expense != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit Expense" else "Add Expense") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Type dropdown
                Box {
                    OutlinedButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(type, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        types.forEach { t ->
                            DropdownMenuItem(
                                text    = { Text(t) },
                                onClick = { type = t; typeExpanded = false },
                                leadingIcon = if (t == type) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value         = description,
                    onValueChange = { description = it },
                    label         = { Text("Description") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value         = amountText,
                    onValueChange = { amountText = it; amountError = false },
                    label         = { Text("Amount *") },
                    singleLine    = true,
                    isError       = amountError,
                    supportingText = if (amountError) {{ Text("Amount must be greater than zero.", color = MaterialTheme.colorScheme.error) }} else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier      = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value         = paidTo,
                    onValueChange = { paidTo = it },
                    label         = { Text("Paid To") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )

                // Payment method dropdown
                Box {
                    OutlinedButton(onClick = { methodExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(paymentMethod, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = methodExpanded, onDismissRequest = { methodExpanded = false }) {
                        PAYMENT_METHODS.forEach { m ->
                            DropdownMenuItem(
                                text    = { Text(m) },
                                onClick = { paymentMethod = m; methodExpanded = false },
                                leadingIcon = if (m == paymentMethod) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toDoubleOrNull() ?: 0.0
                if (amount > 0) onSave(type, description.trim(), amount, paidTo.trim(), paymentMethod)
                else amountError = true
            }) { Text(if (isEdit) "Update" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
