@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.suppliers

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
import com.fastpos.android.data.models.PurchasePayment
import com.fastpos.android.data.models.Supplier
import com.fastpos.android.data.models.SupplierBalance
import com.fastpos.android.data.models.SupplierLedgerEntry
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.SupplierViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val displayFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun SupplierScreen(
    onNavigateBack: () -> Unit,
    vm: SupplierViewModel = hiltViewModel()
) {
    val isLoading       by vm.isLoading.collectAsState()
    val message         by vm.message.collectAsState()
    val snack           = remember { SnackbarHostState() }
    var selectedTab     by remember { mutableIntStateOf(0) }
    val selectedBalance by vm.selectedBalance.collectAsState()

    var showAdd     by remember { mutableStateOf(false) }
    var editTarget  by remember { mutableStateOf<Supplier?>(null) }
    var deleteTarget by remember { mutableStateOf<Supplier?>(null) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectedTab == 1 && selectedBalance != null)
                        Text(selectedBalance!!.supplierName)
                    else
                        Text("Suppliers")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedTab == 1 && selectedBalance != null) vm.clearSelectedBalance()
                        else onNavigateBack()
                    }) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { showAdd = true }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "Add Supplier")
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("Suppliers") }, icon = { Icon(Icons.Default.Business, null) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("Payments & Ledger") }, icon = { Icon(Icons.Default.AccountBalance, null) })
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            when (selectedTab) {
                0 -> SuppliersTab(
                    suppliers = vm.filtered,
                    search    = vm.search.collectAsState().value,
                    onSearch  = vm::setSearch,
                    onEdit    = { editTarget = it },
                    onDelete  = { deleteTarget = it }
                )
                1 -> PaymentsLedgerTab(vm = vm)
            }
        }
    }

    // Dialogs
    if (showAdd) {
        SupplierDialog(title = "Add Supplier",
            onConfirm = { name, contact, phone, address, email ->
                vm.addSupplier(name, contact, phone, address, email); showAdd = false
            },
            onDismiss = { showAdd = false })
    }

    editTarget?.let { s ->
        SupplierDialog(
            title = "Edit Supplier",
            initialName    = s.supplierName, initialContact = s.contactPerson,
            initialPhone   = s.phone,        initialAddress = s.address,
            initialEmail   = s.email,        initialActive  = s.isActive,
            showActiveToggle = true,
            onConfirm = { name, contact, phone, address, email ->
                vm.updateSupplier(s.supplierId, name, contact, phone, address, email, true)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    deleteTarget?.let { s ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Warning, null, tint = RedError) },
            title = { Text("Remove Supplier?") },
            text  = { Text("Remove ${s.supplierName} from the supplier list?") },
            confirmButton = {
                Button(onClick = { vm.deleteSupplier(s.supplierId); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 1 — Suppliers list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuppliersTab(
    suppliers: List<Supplier>,
    search: String,
    onSearch: (String) -> Unit,
    onEdit: (Supplier) -> Unit,
    onDelete: (Supplier) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search, onValueChange = onSearch,
            label = { Text("Search suppliers…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Clear, null) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )

        if (suppliers.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Business, null, Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No suppliers found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return
        }

        Text("${suppliers.size} suppliers", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp))

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(suppliers, key = { it.supplierId }) { s ->
                SupplierCard(supplier = s, onEdit = { onEdit(s) }, onDelete = { onDelete(s) })
            }
        }
    }
}

@Composable
private fun SupplierCard(supplier: Supplier, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Business, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(supplier.supplierName, style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium)
                        if (!supplier.isActive) {
                            Surface(shape = MaterialTheme.shapes.extraSmall,
                                color = RedError.copy(alpha = 0.12f)) {
                                Text("Inactive", style = MaterialTheme.typography.labelSmall,
                                    color = RedError,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                    }
                    if (supplier.contactPerson.isNotBlank())
                        Text(supplier.contactPerson, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (supplier.phone.isNotBlank())
                        Text(supplier.phone, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row {
                IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit, null, tint = AmberWarning) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = RedError) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 2 — Payments & Ledger
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PaymentsLedgerTab(vm: SupplierViewModel) {
    val selectedBalance by vm.selectedBalance.collectAsState()

    if (selectedBalance == null) {
        BalancesList(vm = vm)
    } else {
        SupplierPaymentLedgerDetail(vm = vm, balance = selectedBalance!!)
    }
}

@Composable
private fun BalancesList(vm: SupplierViewModel) {
    val balances by vm.balances.collectAsState()

    if (balances.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AccountBalance, null, Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No supplier balances", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val totalDue = balances.sumOf { it.outstandingBalance.coerceAtLeast(0.0) }

    Column(Modifier.fillMaxSize()) {
        if (totalDue > 0) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, RedError.copy(alpha = 0.4f))
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.AccountBalance, null, tint = RedError)
                        Text("Total Outstanding", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = RedError)
                    }
                    Text(totalDue.formatCurrency(""),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = RedError)
                }
            }
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(balances, key = { it.supplierId }) { b ->
                BalanceSummaryCard(balance = b, onClick = { vm.selectBalance(b) })
            }
        }
    }
}

@Composable
private fun BalanceSummaryCard(balance: SupplierBalance, onClick: () -> Unit) {
    val outstanding = balance.outstandingBalance
    val color = when {
        outstanding > 0 -> RedError
        outstanding < 0 -> GreenSuccess
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        onClick = onClick,
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border  = if (outstanding > 0)
            androidx.compose.foundation.BorderStroke(1.dp, RedError.copy(0.35f)) else null
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(balance.supplierName, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium)
                    if (balance.phone.isNotBlank())
                        Text(balance.phone, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.15f)) {
                        Text(
                            if (outstanding > 0) "Owing" else "Clear",
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = color, fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LabelValue("Invoiced",    balance.totalInvoiced.formatCurrency(""))
                LabelValue("Payments",   balance.directPayments.formatCurrency(""))
                LabelValue("Balance",    outstanding.formatCurrency(""), color = color)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail view: selected supplier — payment form + history + ledger
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SupplierPaymentLedgerDetail(vm: SupplierViewModel, balance: SupplierBalance) {
    val payments      by vm.payments.collectAsState()
    val ledgerEntries by vm.ledgerEntries.collectAsState()
    val openingBal    by vm.openingBalance.collectAsState()
    val totalDebit    by vm.totalDebit.collectAsState()
    val totalCredit   by vm.totalCredit.collectAsState()
    val closingBal    by vm.closingBalance.collectAsState()
    val ledgerLoading by vm.ledgerLoading.collectAsState()
    val ledgerFrom    by vm.ledgerFrom.collectAsState()
    val ledgerTo      by vm.ledgerTo.collectAsState()

    var payAmount    by remember { mutableStateOf("") }
    var payMethod    by remember { mutableStateOf("Cash") }
    var payReference by remember { mutableStateOf("") }
    var payNotes     by remember { mutableStateOf("") }
    var methodExpanded by remember { mutableStateOf(false) }
    val methods = listOf("Cash", "Bank Transfer", "Cheque", "Online")

    var fromStr by remember { mutableStateOf(vm.ledgerFromStr()) }
    var toStr   by remember { mutableStateOf(vm.ledgerToStr()) }

    val outstanding = balance.outstandingBalance

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Outstanding summary header
        item {
            Card(colors = CardDefaults.cardColors(
                containerColor = if (outstanding > 0) RedError.copy(0.08f)
                                 else GreenSuccess.copy(0.08f)),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, if (outstanding > 0) RedError.copy(0.4f) else GreenSuccess.copy(0.4f))
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LabelValue("Total Invoiced",  balance.totalInvoiced.formatCurrency(""))
                        LabelValue("Payments Made",   balance.directPayments.formatCurrency(""))
                    }
                    Column(horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("OUTSTANDING", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(outstanding.formatCurrency(""),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (outstanding > 0) RedError else GreenSuccess)
                    }
                }
            }
        }

        // Record Payment card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Record Payment", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = payAmount, onValueChange = { payAmount = it },
                            label = { Text("Amount *") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        ExposedDropdownMenuBox(expanded = methodExpanded,
                            onExpandedChange = { methodExpanded = it },
                            modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = payMethod, onValueChange = {},
                                readOnly = true, label = { Text("Method") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(methodExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(expanded = methodExpanded,
                                onDismissRequest = { methodExpanded = false }) {
                                methods.forEach { m ->
                                    DropdownMenuItem(text = { Text(m) },
                                        onClick = { payMethod = m; methodExpanded = false })
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = payReference, onValueChange = { payReference = it },
                        label = { Text("Reference / Cheque No.") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = payNotes, onValueChange = { payNotes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(
                        onClick = {
                            val amt = payAmount.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                vm.addPayment(balance.supplierId, amt, payMethod, payReference, payNotes)
                                payAmount = ""; payReference = ""; payNotes = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                    ) {
                        Icon(Icons.Default.Payment, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Payment")
                    }
                }
            }
        }

        // Payment History
        item {
            Text("Payment History", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
        }
        if (payments.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No payments recorded", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(payments, key = { it.paymentId }) { p ->
                PaymentHistoryRow(payment = p, onDelete = { vm.deletePayment(p.paymentId) })
            }
        }

        // Account Statement
        item {
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            Text("Account Statement", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
        }

        // Quick filter chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { fromStr = vm.ledgerFromStr(); toStr = vm.ledgerToStr(); vm.thisMonth() }, label = { Text("This Month") })
                FilterChip(selected = false, onClick = { vm.last3Months(); fromStr = vm.ledgerFromStr(); toStr = vm.ledgerToStr() }, label = { Text("3 Months") })
                FilterChip(selected = false, onClick = { vm.allTime(); fromStr = vm.ledgerFromStr(); toStr = vm.ledgerToStr() }, label = { Text("All Time") })
            }
        }

        // Date range + Run
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = fromStr, onValueChange = { fromStr = it; vm.setLedgerFromStr(it) },
                    label = { Text("From") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("yyyy-MM-dd") })
                OutlinedTextField(value = toStr, onValueChange = { toStr = it; vm.setLedgerToStr(it) },
                    label = { Text("To") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("yyyy-MM-dd") })
                Button(onClick = { vm.runLedger() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Text("Run")
                }
            }
        }

        // Ledger rows or loading
        if (ledgerLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else if (ledgerEntries.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No ledger entries for this period",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            // Header row
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    Text("Date",  Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Type",  Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Debit", Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text("Credit",Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    Text("Balance",Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }
                HorizontalDivider()
            }
            items(ledgerEntries) { entry ->
                LedgerRow(entry = entry)
            }

            // Summary footer
            item {
                HorizontalDivider(thickness = 1.5.dp)
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        LedgerSummaryColumn("OPENING", openingBal.formatCurrency(""),
                            MaterialTheme.colorScheme.onSurface)
                        LedgerSummaryColumn("TOTAL DEBIT",  totalDebit.formatCurrency(""),  RedError)
                        LedgerSummaryColumn("TOTAL CREDIT", totalCredit.formatCurrency(""), GreenSuccess)
                        LedgerSummaryColumn("CLOSING",      closingBal.formatCurrency(""),
                            if (closingBal > 0) RedError else GreenSuccess)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PaymentHistoryRow(payment: PurchasePayment, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(10.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(displayFmt.format(payment.paymentDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(payment.paymentMethod, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                if (payment.reference.isNotBlank())
                    Text("Ref: ${payment.reference}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(payment.amount.formatCurrency(""),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = GreenSuccess)
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = RedError)
            }
        }
    }
}

@Composable
private fun LedgerRow(entry: SupplierLedgerEntry) {
    val balColor = if (entry.balance > 0) RedError else GreenSuccess
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(displayFmt.format(entry.entryDate),
                Modifier.weight(2f), style = MaterialTheme.typography.labelSmall)
            Text(entry.entryType, Modifier.weight(1.5f),
                style = MaterialTheme.typography.labelSmall,
                color = when (entry.entryType) {
                    "Invoice" -> MaterialTheme.colorScheme.primary
                    "Payment" -> GreenSuccess
                    "Return"  -> AmberWarning
                    else      -> MaterialTheme.colorScheme.onSurface
                })
            Text(if (entry.debit > 0) entry.debit.formatCurrency("") else "—",
                Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall,
                color = if (entry.debit > 0) RedError else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text(if (entry.credit > 0) entry.credit.formatCurrency("") else "—",
                Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall,
                color = if (entry.credit > 0) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Text(entry.balance.formatCurrency(""),
                Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, color = balColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        if (entry.reference.isNotBlank() || entry.notes.isNotBlank()) {
            Text(listOfNotNull(
                    entry.reference.takeIf { it.isNotBlank() },
                    entry.notes.takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun LedgerSummaryColumn(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold, color = color)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LabelValue(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun SupplierDialog(
    title: String,
    initialName:    String  = "",
    initialContact: String  = "",
    initialPhone:   String  = "",
    initialAddress: String  = "",
    initialEmail:   String  = "",
    initialActive:  Boolean = true,
    showActiveToggle: Boolean = false,
    onConfirm: (String, String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name     by remember { mutableStateOf(initialName) }
    var contact  by remember { mutableStateOf(initialContact) }
    var phone    by remember { mutableStateOf(initialPhone) }
    var address  by remember { mutableStateOf(initialAddress) }
    var email    by remember { mutableStateOf(initialEmail) }
    var isActive by remember { mutableStateOf(initialActive) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Supplier Name *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = contact, onValueChange = { contact = it },
                    label = { Text("Contact Person") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(value = address, onValueChange = { address = it },
                    label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
                OutlinedTextField(value = email, onValueChange = { email = it },
                    label = { Text("Email") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                if (showActiveToggle) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, contact, phone, address, email) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
