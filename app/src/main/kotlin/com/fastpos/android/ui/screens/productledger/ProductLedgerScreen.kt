@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.productledger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.InventoryLedger
import com.fastpos.android.data.models.Product
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.ProductLedgerViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val shortDate  = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val rowDate    = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
private val numFmt     = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 3 }
private val curFmt     = NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 2 }

@Composable
fun ProductLedgerScreen(
    onNavigateBack: () -> Unit,
    vm: ProductLedgerViewModel = hiltViewModel()
) {
    val products        by vm.products.collectAsState()
    val selectedProduct by vm.selectedProduct.collectAsState()
    val fromDate        by vm.fromDate.collectAsState()
    val toDate          by vm.toDate.collectAsState()
    val ledgerRows      by vm.ledgerRows.collectAsState()
    val opening         by vm.opening.collectAsState()
    val totalIn         by vm.totalIn.collectAsState()
    val totalOut        by vm.totalOut.collectAsState()
    val closing         by vm.closing.collectAsState()
    val isLoading       by vm.isLoading.collectAsState()
    val message         by vm.message.collectAsState()

    val snack = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Ledger") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    if (isLoading) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp))
                    IconButton(onClick = { vm.runLedger() }) { Icon(Icons.Default.Refresh, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProductSelector(
                products        = products,
                selectedProduct = selectedProduct,
                onSelect        = { vm.selectProduct(it) }
            )

            // Quick range buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { vm.setToday(); vm.runLedger() }, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Today", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = { vm.setWeek(); vm.runLedger() }, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Week", style = MaterialTheme.typography.labelSmall) }
                OutlinedButton(onClick = { vm.setMonth(); vm.runLedger() }, modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Month", style = MaterialTheme.typography.labelSmall) }
            }

            DateRangeRow(
                fromDate   = fromDate,
                toDate     = toDate,
                onFromDate = { vm.setFromDate(it) },
                onToDate   = { vm.setToDate(it) }
            )

            Button(
                onClick  = { vm.runLedger() },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled  = selectedProduct != null && !isLoading
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text("Run Ledger")
            }

            if (ledgerRows.isNotEmpty() || opening != 0.0) {
                SummaryCard(
                    productName = selectedProduct?.productName ?: "",
                    fromDate    = fromDate,
                    toDate      = toDate,
                    opening     = opening,
                    totalIn     = totalIn,
                    totalOut    = totalOut,
                    closing     = closing
                )
            }

            when {
                ledgerRows.isNotEmpty() -> LedgerTable(rows = ledgerRows)
                selectedProduct != null && !isLoading ->
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
                        Text("No transactions in selected range",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
            }
        }
    }
}

@Composable
private fun ProductSelector(
    products: List<Product>,
    selectedProduct: Product?,
    onSelect: (Product?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value         = selectedProduct?.productName ?: "",
            onValueChange = {},
            readOnly      = true,
            label         = { Text("Select Product") },
            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier      = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            products.forEach { p ->
                DropdownMenuItem(
                    text    = { Text(p.productName) },
                    onClick = { onSelect(p); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun DateRangeRow(
    fromDate:   java.util.Date,
    toDate:     java.util.Date,
    onFromDate: (java.util.Date) -> Unit,
    onToDate:   (java.util.Date) -> Unit
) {
    var showFrom by remember { mutableStateOf(false) }
    var showTo   by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value         = shortDate.format(fromDate),
            onValueChange = {},
            readOnly      = true,
            label         = { Text("From") },
            trailingIcon  = {
                IconButton(onClick = { showFrom = true }) { Icon(Icons.Default.DateRange, null) }
            },
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value         = shortDate.format(toDate),
            onValueChange = {},
            readOnly      = true,
            label         = { Text("To") },
            trailingIcon  = {
                IconButton(onClick = { showTo = true }) { Icon(Icons.Default.DateRange, null) }
            },
            modifier = Modifier.weight(1f)
        )
    }

    if (showFrom) PlDatePickerDialog(fromDate, onDismiss = { showFrom = false }, onConfirm = { onFromDate(it); showFrom = false })
    if (showTo)   PlDatePickerDialog(toDate,   onDismiss = { showTo = false },   onConfirm = { onToDate(it);   showTo   = false })
}

@Composable
private fun SummaryCard(
    productName: String,
    fromDate:    java.util.Date,
    toDate:      java.util.Date,
    opening:     Double,
    totalIn:     Double,
    totalOut:    Double,
    closing:     Double
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "$productName  ·  ${shortDate.format(fromDate)} – ${shortDate.format(toDate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            SummaryRow("Opening",   numFmt.format(opening),  MaterialTheme.colorScheme.onSurface)
            SummaryRow("Total In",  numFmt.format(totalIn),  GreenSuccess)
            SummaryRow("Total Out", numFmt.format(totalOut), RedError)
            HorizontalDivider()
            SummaryRow("Closing",   numFmt.format(closing),  MaterialTheme.colorScheme.primary, bold = true)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color)
    }
}

@Composable
private fun LedgerTable(rows: List<InventoryLedger>) {
    // Header
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Date",    Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("Type",    Modifier.weight(1.1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("In",      Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = GreenSuccess)
        Text("Out",     Modifier.weight(0.9f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = RedError)
        Text("Balance", Modifier.weight(1.0f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Amount",  Modifier.weight(1.0f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Text("Remarks", Modifier.weight(1.5f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        items(rows) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(rowDate.format(row.transDate),
                    Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
                Text(row.refType,
                    Modifier.weight(1.1f), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (row.inQty  > 0) numFmt.format(row.inQty)  else "–",
                    Modifier.weight(0.9f), style = MaterialTheme.typography.bodySmall, color = GreenSuccess)
                Text(if (row.outQty > 0) numFmt.format(row.outQty) else "–",
                    Modifier.weight(0.9f), style = MaterialTheme.typography.bodySmall, color = RedError)
                Text(numFmt.format(row.balanceQty),
                    Modifier.weight(1.0f), style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(if (row.amount != 0.0) curFmt.format(row.amount) else "–",
                    Modifier.weight(1.0f), style = MaterialTheme.typography.bodySmall)
                Text(row.remarks.ifBlank { "–" },
                    Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        }
    }
}

@Composable
private fun PlDatePickerDialog(
    initial:   java.util.Date,
    onDismiss: () -> Unit,
    onConfirm: (java.util.Date) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.time)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onConfirm(java.util.Date(it)) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(state = state) }
}
