@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.payment

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.PaymentViewModel
import kotlinx.coroutines.launch

private val PAYMENT_METHODS = listOf("Cash", "Card", "JazzCash", "EasyPaisa", "Bank", "Voucher", "Wallet")

@Composable
fun PaymentScreen(
    orderId:           Int,
    onPaymentComplete: () -> Unit,
    onNavigateBack:    () -> Unit,
    vm: PaymentViewModel = hiltViewModel()
) {
    val context        = LocalContext.current
    val order          by vm.order.collectAsState()
    val customer       by vm.customer.collectAsState()
    val isLoading      by vm.isLoading.collectAsState()
    val isPaying       by vm.isPaying.collectAsState()
    val isPrinting     by vm.isPrinting.collectAsState()
    val error          by vm.error.collectAsState()
    val paid           by vm.paid.collectAsState()
    val payments       by vm.payments.collectAsState()
    val totalEntered   by vm.totalEntered.collectAsState()
    val settings       by vm.session.settings.collectAsState()
    val effectiveTotal by vm.effectiveTotal.collectAsState()
    val walletBalance          by vm.walletBalance.collectAsState()
    val tipAmount              by vm.tipAmount.collectAsState()
    val savedPrinterAddress    by vm.savedPrinterAddress.collectAsState(initial = "")
    val showReceiptPreview     by vm.showReceiptPreview.collectAsState()
    val confirmedPayments      by vm.confirmedPayments.collectAsState()
    val savedPrinterName       by vm.savedPrinterName.collectAsState(initial = "")
    val customPaymentMethods   by vm.customPaymentMethods.collectAsState(initial = emptyList())
    val contactPhone           by vm.contactPhone.collectAsState()
    val billPrintMode          by vm.billPrintMode.collectAsState(initial = "Silent")
    val customerTotalOrders    by vm.customerTotalOrders.collectAsState()
    val customerLoyaltyPoints  by vm.customerLoyaltyPoints.collectAsState()
    val availablePrinters      by vm.availablePrinters.collectAsState()

    var showPrinterPicker    by remember { mutableStateOf(false) }
    var pairedDevices        by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var showPhoneDialog      by remember { mutableStateOf<String?>(null) } // "sms" or "whatsapp"
    var phoneInput           by remember { mutableStateOf("") }

    LaunchedEffect(orderId) { vm.loadOrder(orderId) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (!paid) IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                title = { Text(if (paid) "Payment Complete" else "Payment — ${order?.orderNo ?: ""}") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val o = order
        if (o == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Order not found.", color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        // ── Success state ─────────────────────────────────────────────────────
        if (paid) {
            val scope = rememberCoroutineScope()
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.weight(0.3f))
                Icon(Icons.Default.CheckCircle, null, Modifier.size(96.dp), tint = GreenSuccess)
                Text("Payment Successful!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = GreenSuccess)
                Text("Order ${o.orderNo}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(Modifier.weight(0.3f))

                // Print receipt section — behaviour depends on Bill Print Mode setting
                if (billPrintMode == "Preview") {
                    Button(
                        onClick  = vm::showPreview,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled  = !isPrinting,
                        colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Visibility, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Preview Receipt", style = MaterialTheme.typography.titleSmall)
                    }
                } else if (savedPrinterAddress.isNotBlank()) {
                    Button(
                        onClick  = { vm.printReceipt(context, savedPrinterAddress) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled  = !isPrinting,
                        colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Printing…")
                        } else {
                            Icon(Icons.Default.Print, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Print Receipt ($savedPrinterName)", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        @Suppress("MissingPermission")
                        pairedDevices = BluetoothPrinterHelper.getPairedPrinters(context)
                        showPrinterPicker = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BluetoothSearching, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (savedPrinterAddress.isBlank()) "Select Printer & Print" else "Change Printer")
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(
                    onClick  = {
                        scope.launch {
                            val text = vm.buildShareText()
                            if (text.isBlank()) return@launch
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type    = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                                putExtra(Intent.EXTRA_SUBJECT, "Receipt — ${o.orderNo}")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Receipt"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share Receipt")
                }

                // SMS and WhatsApp row
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick  = {
                            if (contactPhone.isNotBlank()) {
                                vm.sendSms(context, contactPhone)
                            } else {
                                phoneInput = ""; showPhoneDialog = "sms"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = BlueInfo)
                    ) {
                        Icon(Icons.Default.Sms, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SMS")
                    }
                    OutlinedButton(
                        onClick  = {
                            if (contactPhone.isNotBlank()) {
                                vm.openWhatsApp(context, contactPhone)
                            } else {
                                phoneInput = ""; showPhoneDialog = "whatsapp"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess)
                    ) {
                        Icon(Icons.Default.Chat, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("WhatsApp")
                    }
                }

                Button(
                    onClick   = onPaymentComplete,
                    modifier  = Modifier.fillMaxWidth().height(52.dp),
                    colors    = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Done, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Done", style = MaterialTheme.typography.titleMedium)
                }
            }

            // Phone entry dialog when no contact phone on file
            if (showPhoneDialog != null) {
                val channel = showPhoneDialog!!
                AlertDialog(
                    onDismissRequest = { showPhoneDialog = null },
                    icon  = { Icon(if (channel == "sms") Icons.Default.Sms else Icons.Default.Chat, null,
                        tint = if (channel == "sms") BlueInfo else GreenSuccess) },
                    title = { Text("Enter Phone Number") },
                    text  = {
                        OutlinedTextField(
                            value         = phoneInput,
                            onValueChange = { phoneInput = it },
                            label         = { Text("Phone") },
                            placeholder   = { Text("e.g. 923001234567") },
                            singleLine    = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier      = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick  = {
                                if (phoneInput.isNotBlank()) {
                                    if (channel == "sms") vm.sendSms(context, phoneInput)
                                    else vm.openWhatsApp(context, phoneInput)
                                }
                                showPhoneDialog = null
                            }
                        ) { Text("Send") }
                    },
                    dismissButton = { TextButton(onClick = { showPhoneDialog = null }) { Text("Cancel") } }
                )
            }

            if (showPrinterPicker) {
                PrinterPickerDialog(
                    devices  = pairedDevices,
                    onSelect = { device ->
                        @Suppress("MissingPermission")
                        val name = device.name ?: device.address
                        vm.savePrinter(device.address, name)
                        vm.printReceipt(context, device.address)
                        showPrinterPicker = false
                    },
                    onDismiss = { showPrinterPicker = false }
                )
            }

            if (showReceiptPreview && order != null) {
                ReceiptPreviewSheet(
                    order                  = order!!,
                    settings               = settings,
                    payments               = confirmedPayments,
                    isPrinting             = isPrinting,
                    onPrintTo              = { vm.printFromPreview(it) },
                    onDismiss              = { vm.dismissReceiptPreview() },
                    printers               = availablePrinters,
                    customerTotalOrders    = customerTotalOrders,
                    customerLoyaltyPoints  = customerLoyaltyPoints
                )
            }
            return@Scaffold
        }

        // ── Payment form ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Order summary
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Order Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    SummaryRow("Order No", o.orderNo)
                    SummaryRow("Type", o.orderType)
                    o.tableName?.takeIf { it.isNotBlank() }?.let { SummaryRow("Table", it) }
                    o.waiterName?.takeIf { it.isNotBlank() }?.let { SummaryRow("Waiter", it) }
                    customer?.let { SummaryRow("Customer", it.customerName) }
                    if (o.orderType == "Delivery") {
                        o.deliveryName?.takeIf    { it.isNotBlank() }?.let { SummaryRow("Delivery Name",    it) }
                        o.deliveryPhone?.takeIf   { it.isNotBlank() }?.let { SummaryRow("Delivery Phone",   it) }
                        o.deliveryAddress?.takeIf { it.isNotBlank() }?.let { SummaryRow("Delivery Address", it) }
                    }
                    SummaryRow("Subtotal", o.subTotal.formatCurrency(settings.currencySymbol))
                    if (o.discountAmount > 0) SummaryRow("Discount", "- ${o.discountAmount.formatCurrency(settings.currencySymbol)}", color = GreenSuccess)
                    if (o.taxAmount > 0)      SummaryRow("Tax (${o.taxPercent}%)", o.taxAmount.formatCurrency(settings.currencySymbol))
                    if (o.serviceCharges > 0) SummaryRow("Service Charges", o.serviceCharges.formatCurrency(settings.currencySymbol))
                    if (o.deliveryCharge > 0) SummaryRow("Delivery Charge", o.deliveryCharge.formatCurrency(settings.currencySymbol), color = MaterialTheme.colorScheme.primary)
                    if (tipAmount > 0)        SummaryRow("Tip", tipAmount.formatCurrency(settings.currencySymbol), color = BlueInfo)
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grand Total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(effectiveTotal.formatCurrency(settings.currencySymbol),
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Wallet payment (only if customer has wallet balance)
            if (customer != null && walletBalance > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GreenSuccess.copy(0.08f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GreenSuccess.copy(0.4f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = GreenSuccess, modifier = Modifier.size(18.dp))
                            Text("Wallet Balance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = GreenSuccess)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("${customer!!.customerName}'s wallet",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(walletBalance.formatCurrency(settings.currencySymbol),
                                    style      = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color      = GreenSuccess)
                            }
                            val walletApplyAmt = minOf(walletBalance, effectiveTotal)
                            Button(
                                onClick = { vm.applyWalletPayment(walletApplyAmt) },
                                colors  = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pay ${walletApplyAmt.formatCurrency(settings.currencySymbol)}")
                            }
                        }
                    }
                }
            }

            // Tip section
            var tipText by remember { mutableStateOf(if (tipAmount > 0) "%.0f".format(tipAmount) else "") }
            Card(
                colors = CardDefaults.cardColors(containerColor = BlueInfo.copy(alpha = 0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, BlueInfo.copy(alpha = 0.3f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Favorite, null, tint = BlueInfo, modifier = Modifier.size(18.dp))
                        Text("Tip (Optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = BlueInfo)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("None" to 0.0, "5%" to o.grandTotal * 0.05, "10%" to o.grandTotal * 0.10, "15%" to o.grandTotal * 0.15).forEach { (label, amt) ->
                            FilterChip(
                                selected = tipAmount == amt,
                                onClick  = {
                                    vm.setTipAmount(amt)
                                    tipText = if (amt > 0) "%.0f".format(amt) else ""
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value         = tipText,
                        onValueChange = {
                            tipText = it
                            vm.setTipAmount(it.toDoubleOrNull() ?: 0.0)
                        },
                        label         = { Text("Custom amount") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        prefix        = { Text(settings.currencySymbol) }
                    )
                }
            }

            // Split bill
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(0.08f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(0.3f))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.People, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Split Bill", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Divide total equally between multiple people",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 3, 4, 5).forEach { n ->
                            FilterChip(
                                selected = payments.size == n && payments.all { it.paymentMethod == "Cash" && it.amount == payments[0].amount },
                                onClick  = { vm.splitBill(n) },
                                label    = { Text("$n people", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Payment rows
            Text("Payment Method(s)", style = MaterialTheme.typography.titleMedium)

            payments.forEachIndexed { index, payment ->
                PaymentRowCard(
                    index        = index,
                    payment      = payment,
                    canDelete    = payments.size > 1,
                    grandTotal   = effectiveTotal,
                    extraMethods = customPaymentMethods,
                    onChange     = { method, amount, ref -> vm.updatePayment(index, method, amount, ref) },
                    onDelete     = { vm.removePaymentRow(index) }
                )
            }

            TextButton(onClick = vm::addPaymentRow, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Add Payment Method")
            }

            // Balance
            val balance = effectiveTotal - totalEntered
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (balance <= 0) GreenSuccess.copy(0.15f) else AmberWarning.copy(0.15f)
                )
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (balance <= 0) "Change" else "Remaining", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text  = kotlin.math.abs(balance).formatCurrency(settings.currencySymbol),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (balance <= 0) GreenSuccess else AmberWarning
                    )
                }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick  = vm::confirmPayment,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled  = !isPaying && totalEntered > 0,
                colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) {
                if (isPaying) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Processing…")
                } else {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Confirm Payment", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun PrinterPickerDialog(
    devices:   List<BluetoothDevice>,
    onSelect:  (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Printer") },
        text = {
            if (devices.isEmpty()) {
                Text("No paired Bluetooth devices found.\nPair your thermal printer in Android Settings → Bluetooth first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(devices) { device ->
                        @Suppress("MissingPermission")
                        OutlinedButton(
                            onClick = { onSelect(device) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Print, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                @Suppress("MissingPermission")
                                Text(device.name ?: "Unknown", style = MaterialTheme.typography.bodyMedium)
                                @Suppress("MissingPermission")
                                Text(device.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PaymentRowCard(
    index:        Int,
    payment:      com.fastpos.android.data.models.PaymentEntry,
    canDelete:    Boolean,
    grandTotal:   Double,
    extraMethods: List<String> = emptyList(),
    onChange:     (String?, Double?, String?) -> Unit,
    onDelete:     () -> Unit
) {
    var expanded   by remember { mutableStateOf(false) }
    var amountText by remember(payment.amount) { mutableStateOf(if (payment.amount == 0.0) "" else payment.amount.toString()) }
    val allMethods = remember(extraMethods) { PAYMENT_METHODS + extraMethods }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value         = payment.paymentMethod,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Method") },
                        trailingIcon  = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded         = expanded,
                        onDismissRequest = { expanded = false },
                        modifier         = Modifier.fillMaxWidth()
                    ) {
                        allMethods.forEach { method ->
                            DropdownMenuItem(
                                text    = { Text(method) },
                                onClick = { onChange(method, null, null); expanded = false }
                            )
                        }
                    }
                }
                if (canDelete) {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value         = amountText,
                    onValueChange = {
                        amountText = it
                        onChange(null, it.toDoubleOrNull() ?: 0.0, null)
                    },
                    label           = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier        = Modifier.weight(1f),
                    singleLine      = true
                )
                if (payment.paymentMethod != "Cash") {
                    OutlinedTextField(
                        value         = payment.reference,
                        onValueChange = { onChange(null, null, it) },
                        label         = { Text("Reference") },
                        modifier      = Modifier.weight(1f),
                        singleLine    = true
                    )
                }
            }
            // Quick cash presets + exact fill
            if (payment.paymentMethod == "Cash") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    listOf(500, 1000, 2000, 5000).forEach { preset ->
                        val presetD = preset.toDouble()
                        FilterChip(
                            selected  = payment.amount == presetD,
                            onClick   = { amountText = preset.toString(); onChange(null, presetD, null) },
                            label     = { Text("$preset", style = MaterialTheme.typography.labelSmall) },
                            modifier  = Modifier.weight(1f),
                            colors    = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(0.18f),
                                selectedLabelColor     = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
            TextButton(
                onClick  = { amountText = grandTotal.toString(); onChange(null, grandTotal, null) },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Fill Exact: ${grandTotal.toLong()}") }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
internal fun ReceiptPreviewSheet(
    order:                 com.fastpos.android.data.models.Order,
    settings:              com.fastpos.android.data.models.CompanySettings,
    payments:              List<com.fastpos.android.data.models.PaymentEntry>,
    isPrinting:            Boolean,
    onPrintTo:             (com.fastpos.android.data.models.PrinterOption) -> Unit,
    onDismiss:             () -> Unit,
    printers:              List<com.fastpos.android.data.models.PrinterOption> = emptyList(),
    customerTotalOrders:   Int = 0,
    customerLoyaltyPoints: Int = 0,
    pointsRedeemed:        Int = 0
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside   = true
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color    = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                // Top bar
                androidx.compose.material3.TopAppBar(
                    title = { Text("Receipt Preview") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        if (isPrinting) {
                            Row(
                                Modifier.padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("Printing…", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (printers.isEmpty()) {
                            Text("No printer configured", Modifier.padding(end = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error)
                        } else {
                            printers.forEach { printer ->
                                Button(
                                    onClick  = { onPrintTo(printer) },
                                    enabled  = !isPrinting,
                                    colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Icon(Icons.Default.Print, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(printer.name, maxLines = 1)
                                }
                            }
                        }
                    }
                )
                // Scrollable receipt content
                LazyColumn(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding      = PaddingValues(16.dp)
                ) {
                    item {
                        ReceiptView(
                            order                  = order,
                            settings               = settings,
                            payments               = payments,
                            modifier               = Modifier.fillMaxWidth(0.90f),
                            customerTotalOrders    = customerTotalOrders,
                            customerLoyaltyPoints  = customerLoyaltyPoints,
                            pointsRedeemed         = pointsRedeemed
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReceiptView(
    order:                 com.fastpos.android.data.models.Order,
    settings:              com.fastpos.android.data.models.CompanySettings,
    payments:              List<com.fastpos.android.data.models.PaymentEntry>,
    modifier:              Modifier = Modifier,
    customerTotalOrders:   Int      = 0,
    customerLoyaltyPoints: Int      = 0,
    pointsRedeemed:        Int      = 0
) {
    val pointsEarned = order.grandTotal.toInt() / 10
    val sym    = settings.currencySymbol
    val dtFmt  = remember { java.text.SimpleDateFormat("dd MMM yyyy  hh:mm a", java.util.Locale.ENGLISH) }
    val gray   = MaterialTheme.colorScheme.onSurfaceVariant

    fun fmtAmt(v: Double) = "$sym ${"%.2f".format(v)}"

    Card(
        colors   = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            // ── Header ────────────────────────────────────────────────────────
            val logoBitmap = remember(settings.logoData) {
                settings.logoData?.let {
                    try { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } catch (_: Exception) { null }
                }
            }
            if (logoBitmap != null) {
                Image(
                    bitmap             = logoBitmap,
                    contentDescription = null,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.size(80.dp).align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(settings.companyName,
                style     = MaterialTheme.typography.titleLarge,
                fontWeight= FontWeight.Bold,
                modifier  = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color     = Color.Black)
            if (settings.address.isNotBlank())
                Text(settings.address, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = gray)
            if (settings.phone.isNotBlank())
                Text("Tel: ${settings.phone}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = gray)
            if (settings.taxNumber.isNotBlank())
                Text("NTN: ${settings.taxNumber}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = gray)

            ReceiptDivider()

            // ── Order info ────────────────────────────────────────────────────
            ReceiptRow("Order #:",  order.orderNo,  color = Color.Black)
            ReceiptRow("Date:",     dtFmt.format(order.orderDate), color = gray)
            ReceiptRow("Type:",     order.orderType, color = Color.Black)
            order.tableName?.takeIf    { it.isNotBlank() }?.let { ReceiptRow("Table:",    it, color = Color.Black) }
            order.waiterName?.takeIf   { it.isNotBlank() }?.let { ReceiptRow("Waiter:",   it, color = gray) }
            order.customerName?.takeIf { it.isNotBlank() }?.let { ReceiptRow("Customer:", it, color = Color.Black) }

            // ── Delivery block ─────────────────────────────────────────────────
            if (order.orderType == "Delivery" &&
                listOf(order.deliveryName, order.deliveryPhone, order.deliveryAddress).any { !it.isNullOrBlank() }) {
                ReceiptDivider()
                Text("DELIVERY", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Black)
                order.deliveryName?.takeIf    { it.isNotBlank() }?.let { ReceiptRow("Customer:", it, color = gray) }
                order.deliveryPhone?.takeIf   { it.isNotBlank() }?.let { ReceiptRow("Phone:",    it, color = gray) }
                order.deliveryAddress?.takeIf { it.isNotBlank() }?.let { ReceiptRow("Address:",  it, color = gray) }
            }

            ReceiptDivider()
            order.notes?.takeIf { it.isNotBlank() }?.let {
                Text("Note: $it", style = MaterialTheme.typography.bodySmall,
                    color = gray, modifier = Modifier.padding(bottom = 4.dp))
            }

            // ── Column header ──────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth()) {
                Text("Item",  Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Color.Black)
                Text("Qty",   Modifier.width(36.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Black)
                Text("Price", Modifier.width(64.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Color.Black)
                Text("Total", Modifier.width(64.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Color.Black)
            }
            ReceiptDivider(dashed = true)

            // ── Items ──────────────────────────────────────────────────────────
            order.items.forEach { item ->
                val name = buildString {
                    append(item.productNameSnapshot)
                    if (!item.sizeNameSnapshot.isNullOrBlank()) append(" (${item.sizeNameSnapshot})")
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color.Black)
                    Text("x${"%.0f".format(item.quantity)}", Modifier.width(36.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Black)
                    Text(fmtAmt(item.unitPrice), Modifier.width(64.dp),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, color = gray)
                    Text(fmtAmt(item.lineTotal), Modifier.width(64.dp),
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, color = Color.Black)
                }
                item.modifiers.forEach { mod ->
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp)) {
                        Text("+ ${mod.modifierNameSnapshot}", Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall, color = gray)
                        if (mod.extraPrice > 0)
                            Text(fmtAmt(mod.extraPrice), Modifier.width(64.dp),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End, color = gray)
                    }
                }
                item.notes?.takeIf { it.isNotBlank() }?.let {
                    Text("  * $it", style = MaterialTheme.typography.bodySmall, color = gray)
                }
            }

            ReceiptDivider(dashed = true)

            // ── Totals ─────────────────────────────────────────────────────────
            ReceiptRow("Sub-Total:", fmtAmt(order.subTotal), color = Color.Black)
            if (order.discountAmount > 0)
                ReceiptRow("Discount:", fmtAmt(order.discountAmount), color = gray)
            if (order.taxAmount > 0)
                ReceiptRow("${settings.taxLabel.ifBlank{"Tax"}} (${"%.1f".format(order.taxPercent)}%):",
                    fmtAmt(order.taxAmount), color = Color.Black)
            if (order.serviceCharges > 0)
                ReceiptRow("Service Charge:", fmtAmt(order.serviceCharges), color = Color.Black)
            if (order.deliveryCharge > 0)
                ReceiptRow("Delivery Charge:", fmtAmt(order.deliveryCharge), color = Color.Black)
            if (order.tips > 0)
                ReceiptRow("Tip:", fmtAmt(order.tips), color = Color.Black)

            ReceiptDivider(dashed = true)

            ReceiptRow("TOTAL:", fmtAmt(order.grandTotal + order.tips),
                color = Color.Black, bold = true)

            ReceiptDivider()

            // ── Payments ───────────────────────────────────────────────────────
            val effectiveTotal = order.grandTotal + order.tips
            val validPayments = payments.filter { it.amount > 0 }
            if (validPayments.isNotEmpty()) {
                validPayments.forEach { p ->
                    ReceiptRow("Paid (${p.paymentMethod}):", fmtAmt(p.amount), color = Color.Black)
                }
                val totalPaid = validPayments.sumOf { it.amount }
                val change    = totalPaid - effectiveTotal
                if (change > 0.005) ReceiptRow("Change:", fmtAmt(change), color = Color.Black)
            } else if (order.paidAmount > 0) {
                ReceiptRow("Paid:", fmtAmt(order.paidAmount), color = Color.Black)
            }

            ReceiptDivider()

            // ── Customer profile ────────────────────────────────────────────────
            if (order.customerId != null) {
                ReceiptDivider(dashed = true)
                Text("CUSTOMER PROFILE", Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                if (customerTotalOrders > 0)
                    ReceiptRow("Total Visits:", customerTotalOrders.toString(), color = MaterialTheme.colorScheme.primary)
                if (pointsRedeemed > 0)
                    ReceiptRow("Points Redeemed:", "$pointsRedeemed pts", color = MaterialTheme.colorScheme.primary)
                if (pointsEarned > 0)
                    ReceiptRow("Points Earned:", "+$pointsEarned pts", color = MaterialTheme.colorScheme.primary)
                ReceiptRow("Points Balance:", "$customerLoyaltyPoints pts", color = MaterialTheme.colorScheme.primary, bold = true)
                ReceiptDivider()
            }

            // ── FBR Invoice ──────────────────────────────────────────────────────
            if (!order.fbrInvoiceNo.isNullOrBlank()) {
                ReceiptDivider(dashed = true)
                Text(
                    "FBR INVOICE",
                    modifier   = Modifier.fillMaxWidth(),
                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center,
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color      = Color.Black
                )
                ReceiptRow("FBR No:", order.fbrInvoiceNo!!, color = Color.Black)
                ReceiptDivider()
            }

            // ── Footer ──────────────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Text(settings.receiptFooter.ifBlank { "Thank you for your visit!" }, Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = gray)
            Text(dtFmt.format(java.util.Date()), Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = gray)
        }
    }
}

@Composable
internal fun ReceiptRow(label: String, value: String, color: androidx.compose.ui.graphics.Color, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color)
        Text(value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
internal fun ReceiptDivider(dashed: Boolean = false) {
    if (dashed) {
        Text("- ".repeat(24), Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outlineVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    } else {
        HorizontalDivider(
            modifier  = Modifier.padding(vertical = 4.dp),
            color     = MaterialTheme.colorScheme.outlineVariant
        )
    }
}
