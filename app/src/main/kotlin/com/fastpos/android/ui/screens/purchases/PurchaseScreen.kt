@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.purchases

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import com.fastpos.android.data.models.*
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.PurchasesViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val numFmt  = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 }

@Composable
fun PurchaseScreen(
    onNavigateBack: () -> Unit,
    vm: PurchasesViewModel = hiltViewModel()
) {
    val tabs = listOf("Invoices", "Returns")
    var selectedTab by remember { mutableIntStateOf(0) }

    val message  by vm.message.collectAsState()
    val settings by vm.session.settings.collectAsState()
    val sym      = settings.currencySymbol
    val snackHost = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackHost.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Purchase Orders") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snackHost) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) })
                }
            }
            when (selectedTab) {
                0 -> InvoicesTab(vm, sym)
                1 -> ReturnsTab(vm, sym)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 1 — Invoices
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InvoicesTab(vm: PurchasesViewModel, sym: String) {
    val invoices         by vm.invoices.collectAsState()
    val suppliers        by vm.suppliers.collectAsState()
    val products         by vm.products.collectAsState()
    val selected         by vm.selectedInvoice.collectAsState()
    val invItems         by vm.invoiceItems.collectAsState()
    val loading          by vm.loading.collectAsState()
    val invoiceSearch    by vm.invoiceSearch.collectAsState()
    val supplierFilter   by vm.invoiceSupplierFilter.collectAsState()
    val editingInvoiceId by vm.editingInvoiceId.collectAsState()

    var showForm       by remember { mutableStateOf(false) }
    var payTarget      by remember { mutableStateOf<PurchaseInvoice?>(null) }
    var deleteTarget   by remember { mutableStateOf<PurchaseInvoice?>(null) }

    if (showForm) {
        InvoiceFormSheet(
            vm        = vm,
            suppliers = suppliers,
            products  = products,
            loading   = loading,
            sym       = sym,
            isEdit    = editingInvoiceId != null,
            onDismiss = { showForm = false; vm.clearForm() }
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = invoiceSearch,
            onValueChange = vm::setInvoiceSearch,
            label = { Text("Search invoices…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (invoiceSearch.isNotEmpty()) IconButton(onClick = { vm.setInvoiceSearch("") }) {
                    Icon(Icons.Default.Clear, null)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            singleLine = true
        )

        if (suppliers.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = supplierFilter == null,
                    onClick  = { vm.setInvoiceSupplierFilter(null) },
                    label    = { Text("All", style = MaterialTheme.typography.labelSmall) }
                )
                suppliers.forEach { sup ->
                    FilterChip(
                        selected = supplierFilter == sup.supplierId,
                        onClick  = {
                            vm.setInvoiceSupplierFilter(
                                if (supplierFilter == sup.supplierId) null else sup.supplierId)
                        },
                        label = { Text(sup.supplierName, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(Modifier.weight(1f)) {
            if (invoices.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (invoiceSearch.isNotBlank() || supplierFilter != null)
                            "No invoices match the filter."
                        else "No invoices yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(invoices) { _, inv ->
                        InvoiceCard(
                            invoice  = inv,
                            sym      = sym,
                            onClick  = { vm.selectInvoice(inv) },
                            onPay    = { payTarget = inv }
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { showForm = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = GreenSuccess
            ) { Icon(Icons.Default.Add, null, tint = androidx.compose.ui.graphics.Color.White) }
        }
    }

    // Invoice detail bottom sheet
    if (selected != null) {
        ModalBottomSheet(
            onDismissRequest = { vm.clearSelectedInvoice() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top) {
                    Column {
                        Text("Invoice ${selected!!.invoiceNo}",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (selected!!.supplierName.isNotBlank())
                            Text("Supplier: ${selected!!.supplierName}",
                                style = MaterialTheme.typography.bodyMedium)
                        Text("Date: ${dateFmt.format(selected!!.invoiceDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    InvoiceStatusBadge(selected!!)
                }
                HorizontalDivider()

                // Items table
                Row(Modifier.fillMaxWidth()) {
                    Text("Item", Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Qty",  Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Rate", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("Total",Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
                invItems.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(item.itemName, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                        Text("${numFmt.format(item.quantity)} ${item.unit}", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        Text(numFmt.format(item.purchaseRate), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                        Text(numFmt.format(item.total), Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", fontWeight = FontWeight.Bold)
                    Text("$sym ${numFmt.format(selected!!.totalAmount)}",
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                if (selected!!.paidAmount > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Paid")
                        Text("$sym ${numFmt.format(selected!!.paidAmount)}", color = GreenSuccess)
                    }
                }
                if (selected!!.balanceAmount > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Balance Due")
                        Text("$sym ${numFmt.format(selected!!.balanceAmount)}",
                            color = RedError, fontWeight = FontWeight.Bold)
                    }
                }

                if (selected!!.paymentStatus != "Paid") {
                    Button(
                        onClick  = { payTarget = selected; vm.clearSelectedInvoice() },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                    ) {
                        Icon(Icons.Default.Payment, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Record Payment")
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick  = vm::printSelectedInvoice,
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Print, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Print")
                    }
                    OutlinedButton(
                        onClick  = {
                            val inv = selected!!
                            vm.startEditInvoice(inv)
                            vm.clearSelectedInvoice()
                            showForm = true
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning)
                    ) {
                        Icon(Icons.Default.Edit, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick  = { deleteTarget = selected; vm.clearSelectedInvoice() },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedError)
                    ) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    // Pay invoice dialog
    payTarget?.let { inv ->
        InvoicePayDialog(
            invoice  = inv,
            sym      = sym,
            onDismiss = { payTarget = null },
            onPay    = { amount, method ->
                vm.payInvoice(inv.invoiceId, amount, method)
                payTarget = null
            }
        )
    }

    // Delete invoice confirmation
    deleteTarget?.let { inv ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Warning, null, tint = RedError) },
            title = { Text("Delete Invoice?") },
            text  = { Text("Delete ${inv.invoiceNo}? Stock will be reversed. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteInvoice(inv.invoiceId); deleteTarget = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = RedError)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun InvoiceStatusBadge(invoice: PurchaseInvoice) {
    val (label, color) = when (invoice.paymentStatus) {
        "Paid"    -> "Paid"    to GreenSuccess
        "Partial" -> "Partial" to AmberWarning
        else      -> "Unpaid"  to RedError
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.15f)) {
        Text(label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InvoiceCard(
    invoice: PurchaseInvoice,
    sym: String,
    onClick: () -> Unit,
    onPay: () -> Unit
) {
    Card(
        onClick = onClick,
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(invoice.invoiceNo,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("$sym ${numFmt.format(invoice.totalAmount)}",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(invoice.supplierName.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(dateFmt.format(invoice.invoiceDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                InvoiceStatusBadge(invoice)
                if (invoice.paymentStatus != "Paid") {
                    TextButton(
                        onClick = onPay,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Payment, null, Modifier.size(14.dp), tint = GreenSuccess)
                        Spacer(Modifier.width(4.dp))
                        Text("Pay", style = MaterialTheme.typography.labelSmall, color = GreenSuccess)
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoicePayDialog(
    invoice: PurchaseInvoice,
    sym: String,
    onDismiss: () -> Unit,
    onPay: (Double, String) -> Unit
) {
    val methods = listOf("Cash", "Bank Transfer", "Cheque")
    var amount    by remember { mutableStateOf("") }
    var method    by remember { mutableStateOf("Cash") }
    var amtErr    by remember { mutableStateOf(false) }
    var methodExp by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Invoice: ${invoice.invoiceNo}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Balance Due: $sym ${numFmt.format(invoice.balanceAmount)}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = RedError)
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it; amtErr = false },
                    label = { Text("Payment Amount ($sym) *") }, isError = amtErr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = methodExp,
                    onExpandedChange = { methodExp = it }
                ) {
                    OutlinedTextField(
                        value = method,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Method") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(methodExp) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = methodExp, onDismissRequest = { methodExp = false }) {
                        methods.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = { method = m; methodExp = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = amount.toDoubleOrNull()
                if (amt == null || amt <= 0) { amtErr = true; return@Button }
                onPay(amt, method)
            }, colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun InvoiceFormSheet(
    vm: PurchasesViewModel,
    suppliers: List<Supplier>,
    products: List<PurchaseProduct>,
    loading: Boolean,
    sym: String,
    isEdit: Boolean = false,
    onDismiss: () -> Unit
) {
    val formSupplier    by vm.formSupplier.collectAsState()
    val formItems       by vm.formItems.collectAsState()
    val formPaid        by vm.formPaid.collectAsState()
    val formNotes       by vm.formNotes.collectAsState()
    val formTotal       by vm.formTotal.collectAsState()
    val formInvoiceNo   by vm.formInvoiceNo.collectAsState()
    val formInvoiceDate by vm.formInvoiceDate.collectAsState()

    var showAddItem      by remember { mutableStateOf(false) }
    var supplierExpanded by remember { mutableStateOf(false) }
    var showDatePicker   by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, null) }
            Text(if (isEdit) "Edit Purchase Invoice" else "New Purchase Invoice",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        // Supplier dropdown (optional)
        ExposedDropdownMenuBox(expanded = supplierExpanded, onExpandedChange = { supplierExpanded = it }) {
            OutlinedTextField(
                value = formSupplier?.supplierName ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Supplier (optional)") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(supplierExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = supplierExpanded, onDismissRequest = { supplierExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("— None —") },
                    onClick = { vm.setFormSupplier(null); supplierExpanded = false }
                )
                suppliers.forEach { s ->
                    DropdownMenuItem(
                        text    = { Text(s.supplierName) },
                        onClick = { vm.setFormSupplier(s); supplierExpanded = false }
                    )
                }
            }
        }

        // Invoice No + Date row
        val dateFmtInv = remember { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value         = formInvoiceNo,
                onValueChange = vm::setFormInvoiceNo,
                label         = { Text("Invoice No (optional)") },
                modifier      = Modifier.weight(1f),
                singleLine    = true,
                placeholder   = { Text("Auto-generated if blank") }
            )
            OutlinedTextField(
                value         = dateFmtInv.format(formInvoiceDate),
                onValueChange = {},
                readOnly      = true,
                label         = { Text("Invoice Date") },
                modifier      = Modifier.weight(1f),
                trailingIcon  = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp))
                    }
                }
            )
        }

        if (showDatePicker) {
            val dpState = rememberDatePickerState(initialSelectedDateMillis = formInvoiceDate.time)
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        dpState.selectedDateMillis?.let { vm.setFormInvoiceDate(java.util.Date(it)) }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
            ) { DatePicker(state = dpState) }
        }

        // Items section
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("Items", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showAddItem = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Item")
            }
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(formItems) { idx, item ->
                FormItemRow(item = item, sym = sym, onRemove = { vm.removeFormItem(idx) })
            }
            if (formItems.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No items added yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Total", fontWeight = FontWeight.Bold)
            Text("$sym ${numFmt.format(formTotal)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            value = formPaid, onValueChange = vm::setFormPaid,
            label = { Text("Amount Paid") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = formNotes, onValueChange = vm::setFormNotes,
            label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = { vm.saveInvoice(); onDismiss() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled  = !loading,
            colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
        ) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp),
                color = androidx.compose.ui.graphics.Color.White)
            else {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isEdit) "Update Invoice" else "Save Invoice")
            }
        }
    }

    if (showAddItem) {
        AddInvoiceItemDialog(
            products  = products,
            sym       = sym,
            onDismiss = { showAddItem = false },
            onAdd     = { vm.addFormItem(it); showAddItem = false }
        )
    }
}

@Composable
private fun FormItemRow(item: PurchaseInvoiceItem, sym: String, onRemove: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.itemName,
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${numFmt.format(item.quantity)} ${item.unit} × $sym${numFmt.format(item.purchaseRate)} = $sym${numFmt.format(item.total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, null, tint = RedError, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AddInvoiceItemDialog(
    products: List<PurchaseProduct>,
    sym: String,
    onDismiss: () -> Unit,
    onAdd: (PurchaseInvoiceItem) -> Unit
) {
    var itemName     by remember { mutableStateOf("") }
    var unit         by remember { mutableStateOf("") }
    var qty          by remember { mutableStateOf("") }
    var rate         by remember { mutableStateOf("") }
    var selectedProd by remember { mutableStateOf<PurchaseProduct?>(null) }
    var nameErr      by remember { mutableStateOf(false) }
    var qtyErr       by remember { mutableStateOf(false) }
    var rateErr      by remember { mutableStateOf(false) }
    var prodExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Product picker
                if (products.isNotEmpty()) {
                    ExposedDropdownMenuBox(expanded = prodExpanded,
                        onExpandedChange = { prodExpanded = it }) {
                        OutlinedTextField(
                            value = selectedProd?.productName ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Product (optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(prodExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = prodExpanded,
                            onDismissRequest = { prodExpanded = false }) {
                            DropdownMenuItem(text = { Text("— None —") },
                                onClick = { selectedProd = null; prodExpanded = false })
                            products.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.productName}${if (p.unit.isNotBlank()) " (${p.unit})" else ""}") },
                                    onClick = {
                                        selectedProd = p
                                        itemName = p.productName
                                        unit     = p.unit
                                        if (p.purchasePrice > 0) rate = p.purchasePrice.toString()
                                        prodExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(value = itemName, onValueChange = { itemName = it; nameErr = false },
                    label = { Text("Item Name *") }, isError = nameErr,
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it; qtyErr = false },
                        label = { Text("Quantity *") }, isError = qtyErr,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unit, onValueChange = { unit = it },
                        label = { Text("Unit") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = rate, onValueChange = { rate = it; rateErr = false },
                    label = { Text("Rate ($sym) *") }, isError = rateErr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                val qtyD  = qty.toDoubleOrNull()
                val rateD = rate.toDoubleOrNull()
                if (itemName.isBlank()) { nameErr = true; return@Button }
                if (qtyD == null || qtyD <= 0) { qtyErr = true; return@Button }
                if (rateD == null || rateD < 0) { rateErr = true; return@Button }
                onAdd(PurchaseInvoiceItem(
                    productId    = selectedProd?.productId,
                    itemName     = itemName.trim(),
                    unit         = unit.trim(),
                    quantity     = qtyD,
                    purchaseRate = rateD
                ))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab 2 — Purchase Returns
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReturnsTab(vm: PurchasesViewModel, sym: String) {
    val returns          by vm.returns.collectAsState()
    val suppliers        by vm.suppliers.collectAsState()
    val products         by vm.products.collectAsState()
    val selectedReturn   by vm.selectedReturn.collectAsState()
    val returnItems      by vm.returnItems.collectAsState()
    val retSupplierId    by vm.retSupplierId.collectAsState()
    val retFormItems     by vm.retFormItems.collectAsState()
    val retNotes         by vm.retNotes.collectAsState()
    val retRefundMethod  by vm.retRefundMethod.collectAsState()
    val retTotal         by vm.retFormTotal.collectAsState()
    val loading          by vm.loading.collectAsState()
    val editingReturnId  by vm.editingReturnId.collectAsState()

    var showReturnDialog by remember { mutableStateOf(false) }
    var deleteTarget     by remember { mutableStateOf<PurchaseReturn?>(null) }

    var retItemName      by remember { mutableStateOf("") }
    var retItemQty       by remember { mutableStateOf("1") }
    var retItemRate      by remember { mutableStateOf("") }
    var retItemUnit      by remember { mutableStateOf("") }
    var retItemProductId by remember { mutableStateOf<Int?>(null) }
    var prodExpanded     by remember { mutableStateOf(false) }
    var suppExpanded     by remember { mutableStateOf(false) }
    var refundExpanded   by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${returns.size} returns", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { vm.clearRetForm(); showReturnDialog = true },
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New Return")
            }
        }

        if (returns.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No purchase returns", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(returns) { _, ret ->
                    Card(
                        onClick = { vm.selectReturn(ret) },
                        colors  = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (ret.supplierName.isNotBlank()) ret.supplierName
                                    else "Unspecified Supplier",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${dateFmt.format(ret.returnDate)} · ${ret.itemCount} item${if (ret.itemCount != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (ret.notes.isNotBlank())
                                    Text(ret.notes, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(numFmt.format(ret.totalAmount),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold, color = RedError)
                                Text(sym, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail bottom sheet
    if (selectedReturn != null) {
        val ret = selectedReturn!!
        ModalBottomSheet(
            onDismissRequest = { vm.selectReturn(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Purchase Return",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(dateFmt.format(ret.returnDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (ret.supplierName.isNotBlank())
                            Text(ret.supplierName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$sym ${numFmt.format(ret.totalAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = RedError)
                        val refundColor = if (ret.refundMethod.equals("Cash", ignoreCase = true)) GreenSuccess else MaterialTheme.colorScheme.primary
                        Surface(shape = MaterialTheme.shapes.small, color = refundColor.copy(alpha = 0.15f)) {
                            Text(ret.refundMethod,
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = refundColor, fontWeight = FontWeight.SemiBold)
                        }
                        Row {
                            IconButton(onClick = {
                                vm.startEditReturn(ret)
                                vm.selectReturn(null)
                                showReturnDialog = true
                            }) {
                                Icon(Icons.Default.Edit, null, tint = AmberWarning)
                            }
                            IconButton(onClick = { deleteTarget = ret }) {
                                Icon(Icons.Default.Delete, null, tint = RedError)
                            }
                        }
                    }
                }
                if (ret.notes.isNotBlank())
                    Text("\"${ret.notes}\"", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                if (returnItems.isEmpty()) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Item", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text("Qty",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(56.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("Rate",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(72.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Text("Total",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(72.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    returnItems.forEach { item ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.itemName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium)
                                if (item.unit.isNotBlank())
                                    Text(item.unit, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(numFmt.format(item.quantity),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(56.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text(numFmt.format(item.returnRate),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(72.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            Text(numFmt.format(item.lineTotal),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold, color = RedError,
                                modifier = Modifier.width(72.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                OutlinedButton(
                    onClick  = vm::printSelectedReturn,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Print, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Print Return")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    deleteTarget?.let { ret ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Warning, null, tint = RedError) },
            title = { Text("Delete Return?") },
            text  = { Text("This will restore stock levels for all returned items. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteReturn(ret.returnId)
                        deleteTarget = null
                        vm.selectReturn(null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    // New / Edit return form dialog
    if (showReturnDialog) {
        AlertDialog(
            onDismissRequest = { showReturnDialog = false },
            title = { Text(if (editingReturnId != null) "Edit Purchase Return" else "New Purchase Return") },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Supplier picker
                    val selSupplier = suppliers.find { it.supplierId == retSupplierId }
                    ExposedDropdownMenuBox(expanded = suppExpanded,
                        onExpandedChange = { suppExpanded = it }) {
                        OutlinedTextField(
                            value = selSupplier?.supplierName ?: "All / Unspecified",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Supplier (optional)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(suppExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = suppExpanded,
                            onDismissRequest = { suppExpanded = false }) {
                            DropdownMenuItem(text = { Text("— None —") },
                                onClick = { vm.setRetSupplierId(null); suppExpanded = false })
                            suppliers.forEach { s ->
                                DropdownMenuItem(text = { Text(s.supplierName) },
                                    onClick = { vm.setRetSupplierId(s.supplierId); suppExpanded = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = retNotes, onValueChange = vm::setRetNotes,
                        label = { Text("Notes") }, modifier = Modifier.fillMaxWidth()
                    )
                    // Refund method selector
                    ExposedDropdownMenuBox(expanded = refundExpanded, onExpandedChange = { refundExpanded = it }) {
                        OutlinedTextField(
                            value = retRefundMethod, onValueChange = {}, readOnly = true,
                            label = { Text("Refund Method") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(refundExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = refundExpanded, onDismissRequest = { refundExpanded = false }) {
                            listOf("Credit", "Cash").forEach { m ->
                                DropdownMenuItem(
                                    text    = { Text(m) },
                                    onClick = { vm.setRetRefundMethod(m); refundExpanded = false }
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("Add items to return:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    // Product picker
                    ExposedDropdownMenuBox(expanded = prodExpanded,
                        onExpandedChange = { prodExpanded = it }) {
                        OutlinedTextField(
                            value = retItemName.ifBlank { "Select product" },
                            onValueChange = {}, readOnly = true,
                            label = { Text("Product / Item") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(prodExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = prodExpanded,
                            onDismissRequest = { prodExpanded = false }) {
                            products.forEach { p ->
                                DropdownMenuItem(
                                    text = { Text("${p.productName}${if (p.unit.isNotBlank()) " (${p.unit})" else ""}") },
                                    onClick = {
                                        retItemName      = p.productName
                                        retItemUnit      = p.unit
                                        retItemProductId = p.productId
                                        if (p.purchasePrice > 0) retItemRate = p.purchasePrice.toString()
                                        prodExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = retItemQty, onValueChange = { retItemQty = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = retItemRate, onValueChange = { retItemRate = it },
                            label = { Text("Rate") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            val pid  = retItemProductId ?: return@Button
                            val qty  = retItemQty.toDoubleOrNull() ?: return@Button
                            val rate = retItemRate.toDoubleOrNull() ?: return@Button
                            if (retItemName.isBlank()) return@Button
                            vm.addRetFormItem(PurchaseReturnItem(
                                productId  = pid,
                                itemName   = retItemName,
                                quantity   = qty,
                                unit       = retItemUnit,
                                returnRate = rate
                            ))
                            retItemName = ""; retItemQty = "1"; retItemRate = ""
                            retItemUnit = ""; retItemProductId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Item")
                    }

                    if (retFormItems.isNotEmpty()) {
                        HorizontalDivider()
                        retFormItems.forEachIndexed { i, item ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.itemName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium)
                                    Text("${numFmt.format(item.quantity)} ${item.unit} × ${numFmt.format(item.returnRate)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("$sym${numFmt.format(item.lineTotal)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold, color = RedError)
                                IconButton(onClick = { vm.removeRetFormItem(i) },
                                    modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, null, Modifier.size(14.dp), tint = RedError)
                                }
                            }
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Return",
                                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text("$sym ${numFmt.format(retTotal)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold, color = RedError)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick  = { if (retFormItems.isNotEmpty()) { vm.saveReturn(); showReturnDialog = false } },
                    enabled  = retFormItems.isNotEmpty() && !loading,
                    colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text(if (editingReturnId != null) "Update Return" else "Save Return") }
            },
            dismissButton = {
                TextButton(onClick = { showReturnDialog = false; vm.clearRetForm() }) { Text("Cancel") }
            }
        )
    }
}

