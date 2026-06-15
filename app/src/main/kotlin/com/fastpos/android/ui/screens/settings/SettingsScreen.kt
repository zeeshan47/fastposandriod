@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.fastpos.android.data.models.Category
import com.fastpos.android.data.models.CompanySettings
import com.fastpos.android.data.models.KitchenPrinterConfig
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.viewmodels.SettingsViewModel

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onResetConnection: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val settings               by vm.settings.collectAsState()
    val isLoading              by vm.isLoading.collectAsState()
    val isSaving               by vm.isSaving.collectAsState()
    val message                by vm.message.collectAsState()
    val productCount           by vm.productCount.collectAsState()
    val userCount              by vm.userCount.collectAsState()
    val tableCount             by vm.tableCount.collectAsState()
    val kitchenPrinters        by vm.kitchenPrinters.collectAsState()
    val detectedPrinterNames   by vm.detectedPrinterNames.collectAsState()
    val categories             by vm.categories.collectAsState()
    val productsForBulk        by vm.productsForBulk.collectAsState()
    val savedPrinterAddress    by vm.savedPrinterAddress.collectAsState()
    val savedPrinterName       by vm.savedPrinterName.collectAsState()
    val autoPrintReceipt       by vm.autoPrintReceipt.collectAsState()
    val autoOpenDrawer         by vm.autoOpenDrawer.collectAsState()
    val receiptHeader          by vm.receiptHeader.collectAsState()
    val receiptFooter          by vm.receiptFooter.collectAsState()
    val customPaymentMethods   by vm.customPaymentMethods.collectAsState()
    val customExpenseTypes     by vm.customExpenseTypes.collectAsState()
    val managerPinHash         by vm.managerPinHash.collectAsState()
    val requireManagerPin      by vm.requireManagerPin.collectAsState()
    val isLocalMode            by vm.isLocalMode.collectAsState()
    val isDbOpWorking          by vm.isDbOpWorking.collectAsState()
    val backupUri              by vm.backupUri.collectAsState()
    val isPeerServerRunning    by vm.isPeerServerRunning.collectAsState()
    val localIpAddress         by vm.localIpAddress.collectAsState()
    val paperWidth             by vm.paperWidth.collectAsState()
    val billPrintMode          by vm.billPrintMode.collectAsState()
    val kotPrintMode               by vm.kotPrintMode.collectAsState()
    val autoPrintTakeawayToken     by vm.autoPrintTakeawayToken.collectAsState()
    val receiptPrinterType     by vm.receiptPrinterType.collectAsState()
    val receiptNetIp           by vm.receiptNetIp.collectAsState()
    val receiptNetPort         by vm.receiptNetPort.collectAsState()
    val receiptNetName         by vm.receiptNetName.collectAsState()
    val receiptNetPaperType    by vm.receiptNetPaperType.collectAsState()
    val smsMode                by vm.smsMode.collectAsState()
    val whatsappMode           by vm.whatsappMode.collectAsState()
    val smsTemplateDelivery    by vm.smsTemplateDelivery.collectAsState()
    val smsTemplateTakeaway    by vm.smsTemplateTakeaway.collectAsState()
    val smsTemplateDineIn      by vm.smsTemplateDineIn.collectAsState()
    val smsTemplateOther       by vm.smsTemplateOther.collectAsState()
    val themeMode              by vm.themeMode.collectAsState()
    val accentColor            by vm.accentColor.collectAsState()
    val raastId                by vm.raastId.collectAsState()
    val logoData               by vm.logoData.collectAsState()
    val snack                  = remember { SnackbarHostState() }
    val context                = LocalContext.current
    val scope                  = rememberCoroutineScope()

    // Image picker: read bytes from URI and save to DB
    val logoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                try { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                catch (_: Exception) { null }
            }
            if (bytes != null) vm.saveLogoData(bytes)
        }
    }

    var companyName          by remember(settings) { mutableStateOf(settings.companyName) }
    var companyAddress       by remember(settings) { mutableStateOf(settings.address) }
    var companyPhone         by remember(settings) { mutableStateOf(settings.phone) }
    var companyEmail         by remember(settings) { mutableStateOf(settings.email) }
    var companyWebsite       by remember(settings) { mutableStateOf(settings.website) }
    var taxNumber            by remember(settings) { mutableStateOf(settings.taxNumber) }
    var taxLabel             by remember(settings) { mutableStateOf(settings.taxLabel) }
    var currencySymbol       by remember(settings) { mutableStateOf(settings.currencySymbol) }
    var defaultTax           by remember(settings) { mutableStateOf(settings.defaultTaxPercent.toString()) }
    var serviceCharge        by remember(settings) { mutableStateOf(settings.serviceChargePercent.toString()) }
    var tokenPrefix          by remember(settings) { mutableStateOf(settings.tokenPrefix) }
    var allowPartialPayment  by remember(settings) { mutableStateOf(settings.allowPartialPayment) }
    var defaultOrderType     by remember(settings) { mutableStateOf(settings.defaultOrderType) }
    var orderTypeExpanded    by remember { mutableStateOf(false) }
    var allowNegativeStock  by remember(settings) { mutableStateOf(settings.allowNegativeStock) }
    var maxDiscountPct      by remember(settings) { mutableStateOf(settings.maxDiscountPercent.toString()) }
    var requireWaiter       by remember(settings) { mutableStateOf(settings.requireWaiter) }
    var smsEnabled          by remember(settings) { mutableStateOf(settings.smsEnabled) }
    var smsGatewayUrl       by remember(settings) { mutableStateOf(settings.smsGatewayUrl) }
    var whatsappEnabled     by remember(settings) { mutableStateOf(settings.whatsappEnabled) }
    var whatsappGatewayUrl  by remember(settings) { mutableStateOf(settings.whatsappGatewayUrl) }
    var notifyOnPlaced      by remember(settings) { mutableStateOf(settings.notifyOnOrderPlaced) }
    var notifyOnReady       by remember(settings) { mutableStateOf(settings.notifyOnOrderReady) }
    var notifyOnCancelled   by remember(settings) { mutableStateOf(settings.notifyOnOrderCancelled) }
    var refreshMode         by remember(settings) { mutableStateOf(settings.refreshMode) }
    var headerText          by remember(receiptHeader)        { mutableStateOf(receiptHeader) }
    var footerText          by remember(receiptFooter)        { mutableStateOf(receiptFooter) }
    var smsTmplDelivery     by remember(smsTemplateDelivery)  { mutableStateOf(smsTemplateDelivery) }
    var smsTmplTakeaway     by remember(smsTemplateTakeaway)  { mutableStateOf(smsTemplateTakeaway) }
    var smsTmplDineIn       by remember(smsTemplateDineIn)    { mutableStateOf(smsTemplateDineIn) }
    var smsTmplOther        by remember(smsTemplateOther)     { mutableStateOf(smsTemplateOther) }
    var newMethodText        by remember { mutableStateOf("") }
    var newExpenseTypeText   by remember { mutableStateOf("") }
    var newPinText           by remember { mutableStateOf("") }
    var showResetDialog      by remember { mutableStateOf(false) }
    var showKitchenDialog    by remember { mutableStateOf(false) }
    var editingPrinter       by remember { mutableStateOf<KitchenPrinterConfig?>(null) }
    var editingPrinterIndex  by remember { mutableStateOf(-1) }
    var showBtPicker         by remember { mutableStateOf(false) }
    var pairedBtDevices      by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var netIpText            by remember(receiptNetIp)   { mutableStateOf(receiptNetIp) }
    var netPortText          by remember(receiptNetPort) { mutableStateOf(receiptNetPort.toString()) }
    var showCleanDialog      by remember { mutableStateOf(false) }
    var showRestoreDialog    by remember { mutableStateOf(false) }
    var pendingRestoreUri    by remember { mutableStateOf<android.net.Uri?>(null) }

    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { pendingRestoreUri = uri; showRestoreDialog = true }
    }

    val writeStoragePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.backupLocalDb(context)
        else scope.launch { snack.showSnackbar("Storage permission denied — cannot save backup") }
    }

    fun triggerBackup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            vm.backupLocalDb(context)
        } else {
            writeStoragePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    LaunchedEffect(backupUri) {
        val uri = backupUri ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Backup"))
        vm.clearBackupUri()
    }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = vm::loadSettings) { Icon(Icons.Default.Refresh, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
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
            // Database Stats
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Database Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            StatChip("Products", productCount.toString(), MaterialTheme.colorScheme.primary)
                            StatChip("Users", userCount.toString(), GreenSuccess)
                            StatChip("Tables", tableCount.toString(), AmberWarning)
                        }
                    }
                }
            }

            // Company Settings
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Company Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()

                        OutlinedTextField(
                            value = companyName, onValueChange = { companyName = it },
                            label = { Text("Company Name") },
                            leadingIcon = { Icon(Icons.Default.Business, null) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = companyAddress, onValueChange = { companyAddress = it },
                            label = { Text("Address") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                            modifier = Modifier.fillMaxWidth(), maxLines = 2
                        )
                        OutlinedTextField(
                            value = companyPhone, onValueChange = { companyPhone = it },
                            label = { Text("Phone") },
                            leadingIcon = { Icon(Icons.Default.Phone, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = companyEmail, onValueChange = { companyEmail = it },
                            label = { Text("Email") },
                            leadingIcon = { Icon(Icons.Default.Email, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = companyWebsite, onValueChange = { companyWebsite = it },
                            label = { Text("Website") },
                            leadingIcon = { Icon(Icons.Default.Language, null) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = taxNumber, onValueChange = { taxNumber = it },
                                label = { Text("Tax Number") },
                                modifier = Modifier.weight(1f), singleLine = true
                            )
                            OutlinedTextField(
                                value = taxLabel, onValueChange = { taxLabel = it },
                                label = { Text("Tax Label") },
                                modifier = Modifier.weight(1f), singleLine = true
                            )
                        }
                        OutlinedTextField(
                            value = currencySymbol, onValueChange = { currencySymbol = it },
                            label = { Text("Currency Symbol") },
                            leadingIcon = { Icon(Icons.Default.AttachMoney, null) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = tokenPrefix, onValueChange = { tokenPrefix = it },
                            label = { Text("Token Prefix (e.g. T, #)") },
                            leadingIcon = { Icon(Icons.Default.Tag, null) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = defaultTax, onValueChange = { defaultTax = it },
                            label = { Text("Default Tax %") },
                            leadingIcon = { Icon(Icons.Default.Percent, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )
                        OutlinedTextField(
                            value = serviceCharge, onValueChange = { serviceCharge = it },
                            label = { Text("Service Charge %") },
                            leadingIcon = { Icon(Icons.Default.RoomService, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )

                        // Default order type
                        Box {
                            OutlinedButton(
                                onClick  = { orderTypeExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Receipt, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Default Order Type: $defaultOrderType", modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = orderTypeExpanded, onDismissRequest = { orderTypeExpanded = false }) {
                                listOf("DineIn", "Takeaway", "Delivery").forEach { ot ->
                                    DropdownMenuItem(
                                        text = { Text(ot) },
                                        onClick = { defaultOrderType = ot; orderTypeExpanded = false },
                                        leadingIcon = if (ot == defaultOrderType) {{ Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }} else null
                                    )
                                }
                            }
                        }

                        // Allow partial payment toggle
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Allow Partial Payment", style = MaterialTheme.typography.bodyMedium)
                                Text("Let orders be saved with 'Partial' pay status",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked  = allowPartialPayment,
                                onCheckedChange = { allowPartialPayment = it }
                            )
                        }

                        Button(
                            onClick = {
                                vm.saveSettings(
                                    CompanySettings(
                                        companyName         = companyName.ifBlank { "My Restaurant" },
                                        address             = companyAddress,
                                        phone               = companyPhone,
                                        email               = companyEmail,
                                        website             = companyWebsite,
                                        taxNumber           = taxNumber,
                                        taxLabel            = taxLabel.ifBlank { "Tax" },
                                        currencySymbol      = currencySymbol.ifBlank { "Rs." },
                                        defaultTaxPercent   = defaultTax.toDoubleOrNull() ?: 0.0,
                                        serviceChargePercent = serviceCharge.toDoubleOrNull() ?: 0.0,
                                        tokenPrefix         = tokenPrefix.ifBlank { "T" },
                                        defaultOrderType    = defaultOrderType,
                                        allowPartialPayment = allowPartialPayment,
                                        allowNegativeStock    = allowNegativeStock,
                                        maxDiscountPercent    = maxDiscountPct.toDoubleOrNull() ?: 0.0,
                                        requireWaiter         = requireWaiter,
                                        smsEnabled            = smsEnabled,
                                        smsGatewayUrl         = smsGatewayUrl,
                                        whatsappEnabled       = whatsappEnabled,
                                        whatsappGatewayUrl    = whatsappGatewayUrl,
                                        notifyOnOrderPlaced   = notifyOnPlaced,
                                        notifyOnOrderReady    = notifyOnReady,
                                        notifyOnOrderCancelled = notifyOnCancelled,
                                        refreshMode           = refreshMode
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled  = !isSaving,
                            colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                        ) {
                            if (isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            else {
                                Icon(Icons.Default.Save, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Save Settings", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }

            // POS Behavior
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("POS Behavior", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Allow Negative Stock", style = MaterialTheme.typography.bodyMedium)
                                Text("Allow sales when stock is zero", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = allowNegativeStock, onCheckedChange = { allowNegativeStock = it })
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Require Waiter", style = MaterialTheme.typography.bodyMedium)
                                Text("Force waiter selection before order", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = requireWaiter, onCheckedChange = { requireWaiter = it })
                        }

                        OutlinedTextField(
                            value = maxDiscountPct, onValueChange = { maxDiscountPct = it },
                            label = { Text("Max Discount % (0 = no limit)") },
                            leadingIcon = { Icon(Icons.Default.Discount, null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(), singleLine = true
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Data Refresh Mode", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "How frequently shift status, orders, kitchen & tables auto-refresh over the network",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected    = refreshMode == "Normal",
                                    onClick     = { refreshMode = "Normal" },
                                    label       = { Text("Normal  (5 s)") },
                                    leadingIcon = if (refreshMode == "Normal") ({ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }) else null,
                                    modifier    = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected    = refreshMode == "Instant",
                                    onClick     = { refreshMode = "Instant" },
                                    label       = { Text("Instant  (2 s)") },
                                    leadingIcon = if (refreshMode == "Instant") ({ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }) else null,
                                    modifier    = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Notifications & Messaging
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Notifications & Messaging", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Notify on Order Placed", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = notifyOnPlaced, onCheckedChange = { notifyOnPlaced = it })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Notify on Order Ready", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = notifyOnReady, onCheckedChange = { notifyOnReady = it })
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Notify on Order Cancelled", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = notifyOnCancelled, onCheckedChange = { notifyOnCancelled = it })
                        }

                        HorizontalDivider()

                        // ── SMS ──────────────────────────────────────────────
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Sms, null, Modifier.size(18.dp), tint = BlueInfo)
                            Text("SMS", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        // Mode selector: Off / Device / Api
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Off", "Device", "Api").forEach { mode ->
                                val selected = smsMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick  = { vm.setSmsMode(mode) },
                                    label    = { Text(mode) },
                                    leadingIcon = if (selected) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null
                                )
                            }
                        }
                        when (smsMode) {
                            "Api" -> {
                                OutlinedTextField(
                                    value = smsGatewayUrl, onValueChange = { smsGatewayUrl = it },
                                    label = { Text("SMS Gateway URL") },
                                    placeholder = { Text("https://gateway/send?phone={phone}&msg={message}") },
                                    leadingIcon = { Icon(Icons.Default.Sms, null) },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                Text(
                                    "Placeholders in URL: {phone}  {message}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            "Device" -> {
                                Text(
                                    "Sends via this device's SIM. Customer must have a phone number.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Placeholders: {customerName}  {orderNo}  {total}  {type}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                listOf("Delivery", "Takeaway", "DineIn", "Other").forEach { label ->
                                    val current = when (label) {
                                        "Delivery" -> smsTmplDelivery
                                        "Takeaway" -> smsTmplTakeaway
                                        "DineIn"   -> smsTmplDineIn
                                        else       -> smsTmplOther
                                    }
                                    OutlinedTextField(
                                        value         = current,
                                        onValueChange = { v ->
                                            when (label) {
                                                "Delivery" -> smsTmplDelivery = v
                                                "Takeaway" -> smsTmplTakeaway = v
                                                "DineIn"   -> smsTmplDineIn   = v
                                                else       -> smsTmplOther    = v
                                            }
                                        },
                                        label    = { Text("$label SMS Template") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2, maxLines = 4
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    FilledTonalButton(onClick = {
                                        vm.saveSmsTemplate("Delivery", smsTmplDelivery)
                                        vm.saveSmsTemplate("Takeaway", smsTmplTakeaway)
                                        vm.saveSmsTemplate("DineIn",   smsTmplDineIn)
                                        vm.saveSmsTemplate("Other",    smsTmplOther)
                                    }) {
                                        Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save Templates")
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        // ── WhatsApp ─────────────────────────────────────────
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Message, null, Modifier.size(18.dp), tint = GreenSuccess)
                            Text("WhatsApp", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Off", "Device", "Api").forEach { mode ->
                                val selected = whatsappMode == mode
                                FilterChip(
                                    selected = selected,
                                    onClick  = { vm.setWhatsappMode(mode) },
                                    label    = { Text(mode) },
                                    leadingIcon = if (selected) {{ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }} else null
                                )
                            }
                        }
                        when (whatsappMode) {
                            "Api" -> {
                                OutlinedTextField(
                                    value = whatsappGatewayUrl, onValueChange = { whatsappGatewayUrl = it },
                                    label = { Text("WhatsApp Gateway URL") },
                                    placeholder = { Text("https://gateway/send?phone={phone}&msg={message}") },
                                    leadingIcon = { Icon(Icons.Default.Message, null) },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                                Text(
                                    "Placeholders in URL: {phone}  {message}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            "Device" -> {
                                Text(
                                    "Opens the WhatsApp app installed on this device with a pre-filled message. Customer must have a phone number.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Message uses the SMS templates above.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Button(
                            onClick = {
                                vm.saveSettings(
                                    vm.settings.value.copy(
                                        allowNegativeStock     = allowNegativeStock,
                                        maxDiscountPercent     = maxDiscountPct.toDoubleOrNull() ?: 0.0,
                                        requireWaiter          = requireWaiter,
                                        smsEnabled             = smsMode != "Off",
                                        smsGatewayUrl          = smsGatewayUrl.trim(),
                                        whatsappEnabled        = whatsappMode != "Off",
                                        whatsappGatewayUrl     = whatsappGatewayUrl.trim(),
                                        notifyOnOrderPlaced    = notifyOnPlaced,
                                        notifyOnOrderReady     = notifyOnReady,
                                        notifyOnOrderCancelled = notifyOnCancelled
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled  = !isSaving,
                            colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                        ) { Text("Save Notification Settings") }
                    }
                }
            }

            // Kitchen Printers
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Kitchen Printers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            FilledTonalButton(
                                onClick = { editingPrinter = KitchenPrinterConfig(); editingPrinterIndex = -1; showKitchenDialog = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }
                        HorizontalDivider()

                        Text("KOT Print Mode", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Silent prints immediately. Preview shows a review dialog before printing.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = kotPrintMode == "Off",
                                onClick  = { vm.setKotPrintMode("Off") },
                                label    = { Text("Off") },
                                leadingIcon = if (kotPrintMode == "Off") ({ Icon(Icons.Default.PrintDisabled, null, Modifier.size(14.dp)) }) else null,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = kotPrintMode == "Preview",
                                onClick  = { vm.setKotPrintMode("Preview") },
                                label    = { Text("Preview") },
                                leadingIcon = if (kotPrintMode == "Preview") ({ Icon(Icons.Default.Visibility, null, Modifier.size(14.dp)) }) else null,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = kotPrintMode == "Silent",
                                onClick  = { vm.setKotPrintMode("Silent") },
                                label    = { Text("Silent") },
                                leadingIcon = if (kotPrintMode == "Silent") ({ Icon(Icons.Default.Print, null, Modifier.size(14.dp)) }) else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider()

                        if (detectedPrinterNames.isNotEmpty()) {
                            Text(
                                "Printer names in DB: ${detectedPrinterNames.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (kitchenPrinters.isEmpty()) {
                            Text(
                                "No kitchen printers configured. Add name→IP mappings to auto-print tickets on order placement.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            kitchenPrinters.forEachIndexed { index, cfg ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(cfg.printerName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${cfg.ipAddress}:${cfg.port}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row {
                                        IconButton(onClick = { vm.testKitchenPrint(cfg.ipAddress, cfg.port) }) {
                                            Icon(Icons.Default.Print, null, tint = GreenSuccess)
                                        }
                                        IconButton(onClick = { editingPrinter = cfg; editingPrinterIndex = index; showKitchenDialog = true }) {
                                            Icon(Icons.Default.Edit, null, tint = AmberWarning)
                                        }
                                        IconButton(onClick = {
                                            val updated = kitchenPrinters.toMutableList().also { it.removeAt(index) }
                                            vm.saveKitchenPrinters(updated)
                                        }) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                if (index < kitchenPrinters.lastIndex) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }

            // Bulk Assign Printer to Category / Product
            item {
                BulkAssignPrinterCard(
                    kitchenPrinters  = kitchenPrinters,
                    categories       = categories,
                    products         = productsForBulk,
                    onAssignCategory = { printer, catId -> vm.bulkAssignPrinterToCategory(printer, catId) },
                    onAssignProduct  = { printer, prodId -> vm.bulkAssignPrinterToProduct(printer, prodId) }
                )
            }

            // Receipt Printer
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Receipt Printer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()

                        // ── Printer type toggle ─────────────────────────────────────────
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Connection Type", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = receiptPrinterType == "Bluetooth",
                                    onClick  = { vm.saveReceiptPrinterType("Bluetooth") },
                                    label    = { Text("Bluetooth") },
                                    leadingIcon = if (receiptPrinterType == "Bluetooth") ({
                                        Icon(Icons.Default.Bluetooth, null, Modifier.size(14.dp))
                                    }) else null,
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = receiptPrinterType == "Network",
                                    onClick  = { vm.saveReceiptPrinterType("Network") },
                                    label    = { Text("Network (TCP/IP)") },
                                    leadingIcon = if (receiptPrinterType == "Network") ({
                                        Icon(Icons.Default.Wifi, null, Modifier.size(14.dp))
                                    }) else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 2.dp))

                        if (receiptPrinterType == "Bluetooth") {
                            // ── Bluetooth printer section ───────────────────────────────
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Paired Bluetooth Device", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                FilledTonalButton(
                                    onClick = {
                                        @Suppress("MissingPermission")
                                        pairedBtDevices = BluetoothPrinterHelper.getPairedPrinters(context)
                                        showBtPicker = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.BluetoothSearching, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (savedPrinterAddress.isBlank()) "Select" else "Change")
                                }
                            }
                            if (savedPrinterAddress.isBlank()) {
                                Text("No printer saved. Select a paired Bluetooth ESC/POS printer.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.primary)
                                        Column {
                                            Text(savedPrinterName.ifBlank { "Unknown" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                            Text(savedPrinterAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Row {
                                        IconButton(onClick = { vm.testReceiptPrint() }) {
                                            Icon(Icons.Default.Print, null, tint = GreenSuccess)
                                        }
                                        IconButton(onClick = { vm.clearReceiptPrinter() }) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        } else {
                            // ── Network (TCP/IP) printer section ────────────────────────
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Network Printer (ESC/POS over TCP)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Supports Blackcopper, Epson TM, and any ESC/POS printer connected via LAN/WiFi",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = netIpText,
                                        onValueChange = { netIpText = it },
                                        label = { Text("IP Address") },
                                        placeholder = { Text("192.168.1.100") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = netPortText,
                                        onValueChange = { netPortText = it.filter { c -> c.isDigit() } },
                                        label = { Text("Port") },
                                        placeholder = { Text("9100") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(88.dp)
                                    )
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = {
                                            val port = netPortText.toIntOrNull() ?: 9100
                                            vm.saveReceiptNetworkPrinter(netIpText.trim(), port, netIpText.trim())
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Save")
                                    }
                                    if (receiptNetIp.isNotBlank()) {
                                        IconButton(onClick = { vm.testReceiptPrint() }) {
                                            Icon(Icons.Default.Print, null, tint = GreenSuccess)
                                        }
                                        IconButton(onClick = { vm.clearReceiptPrinter() }) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                if (receiptNetIp.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text("$receiptNetIp:$receiptNetPort",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                // Paper type toggle — only relevant when Network is selected
                                if (receiptPrinterType == "Network") {
                                    Text("Paper Type", style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = receiptNetPaperType != "A4",
                                            onClick  = { vm.saveReceiptNetPaperType("Thermal") },
                                            label    = { Text("Thermal") },
                                            leadingIcon = if (receiptNetPaperType != "A4") ({ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }) else null
                                        )
                                        FilterChip(
                                            selected = receiptNetPaperType == "A4",
                                            onClick  = { vm.saveReceiptNetPaperType("A4") },
                                            label    = { Text("A4") },
                                            leadingIcon = if (receiptNetPaperType == "A4") ({ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }) else null
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        // ── Bill Print Mode ─────────────────────────────────────────────
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Bill Print Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("How the receipt is handled after successful payment",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = billPrintMode == "Off",
                                    onClick  = { vm.setBillPrintMode("Off") },
                                    label    = { Text("Off") },
                                    leadingIcon = if (billPrintMode == "Off") ({ Icon(Icons.Default.PrintDisabled, null, Modifier.size(14.dp)) }) else null,
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = billPrintMode == "Preview",
                                    onClick  = { vm.setBillPrintMode("Preview") },
                                    label    = { Text("Preview") },
                                    leadingIcon = if (billPrintMode == "Preview") ({ Icon(Icons.Default.Visibility, null, Modifier.size(14.dp)) }) else null,
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected = billPrintMode == "Silent",
                                    onClick  = { vm.setBillPrintMode("Silent") },
                                    label    = { Text("Silent") },
                                    leadingIcon = if (billPrintMode == "Silent") ({ Icon(Icons.Default.Print, null, Modifier.size(14.dp)) }) else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Takeaway Token Preview / Print", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Show on-screen token after placing a Takeaway order, and print it if a printer is connected",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = autoPrintTakeawayToken, onCheckedChange = vm::setAutoPrintTakeawayToken)
                        }
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Auto-open cash drawer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Open cash drawer when Cash payment is confirmed",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = autoOpenDrawer, onCheckedChange = vm::setAutoOpenDrawer)
                        }
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Paper Width", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Set receipt column width to match your printer roll size",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected  = paperWidth == 32,
                                    onClick   = { vm.setPaperWidth(32) },
                                    label     = { Text("58 mm  (32 cols)") },
                                    modifier  = Modifier.weight(1f)
                                )
                                FilterChip(
                                    selected  = paperWidth == 48,
                                    onClick   = { vm.setPaperWidth(48) },
                                    label     = { Text("80 mm  (48 cols)") },
                                    modifier  = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // Receipt Customization
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Receipt Customization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()

                        // Logo picker
                        Text("Receipt & KOT Logo", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val logoBitmap = remember(logoData) {
                                logoData?.let {
                                    try { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } catch (_: Exception) { null }
                                }
                            }
                            if (logoBitmap != null) {
                                Image(
                                    bitmap           = logoBitmap,
                                    contentDescription = "Logo",
                                    contentScale     = ContentScale.Fit,
                                    modifier         = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(72.dp),
                                    shape    = RoundedCornerShape(8.dp),
                                    color    = MaterialTheme.colorScheme.surfaceVariant,
                                    border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick  = { logoPickerLauncher.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FileUpload, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (logoData != null) "Change Logo" else "Pick Logo")
                                }
                                if (logoData != null) {
                                    OutlinedButton(
                                        onClick  = { vm.saveLogoData(null) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Remove Logo")
                                    }
                                }
                            }
                        }
                        Text(
                            "Printed at top of receipts, pre-bills, and KOTs (thermal printers only).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider()

                        OutlinedTextField(
                            value = headerText,
                            onValueChange = { headerText = it },
                            label = { Text("Receipt Header (printed below company name)") },
                            placeholder = { Text("Address, phone, tagline…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2, maxLines = 5
                        )
                        OutlinedTextField(
                            value = footerText,
                            onValueChange = { footerText = it },
                            label = { Text("Receipt Footer") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2, maxLines = 5
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(
                                onClick = { vm.setReceiptHeader(headerText); vm.setReceiptFooter(footerText) },
                                colors  = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                            ) {
                                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }

            // Custom Payment Methods
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Custom Payment Methods", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Text(
                            "Appear alongside Cash, Card, JazzCash, EasyPaisa, Bank, Voucher in the payment screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newMethodText,
                                onValueChange = { newMethodText = it },
                                label = { Text("New Method") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    vm.addCustomPaymentMethod(newMethodText)
                                    newMethodText = ""
                                },
                                enabled = newMethodText.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }

                        if (customPaymentMethods.isEmpty()) {
                            Text(
                                "No custom methods added yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            customPaymentMethods.forEachIndexed { index, method ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Payment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(method, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                    IconButton(onClick = { vm.removeCustomPaymentMethod(method) }) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (index < customPaymentMethods.lastIndex)
                                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // Custom Expense Types
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Custom Expense Types", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Text(
                            "Appear alongside Utilities, Salary, Supplies, Maintenance, Fuel, Cleaning, Other in the expenses screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newExpenseTypeText,
                                onValueChange = { newExpenseTypeText = it },
                                label = { Text("New Type") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(
                                onClick = {
                                    vm.addCustomExpenseType(newExpenseTypeText)
                                    newExpenseTypeText = ""
                                },
                                enabled = newExpenseTypeText.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add")
                            }
                        }

                        if (customExpenseTypes.isEmpty()) {
                            Text(
                                "No custom types added yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            customExpenseTypes.forEachIndexed { index, type ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Category, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(type, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                    IconButton(onClick = { vm.removeCustomExpenseType(type) }) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                if (index < customExpenseTypes.lastIndex)
                                    HorizontalDivider(Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // Security / Manager PIN
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Lock, null, Modifier.size(20.dp), tint = AmberWarning)
                                Text("Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Switch(checked = requireManagerPin, onCheckedChange = vm::setRequireManagerPin)
                        }
                        HorizontalDivider()
                        Text(
                            "When enabled, a manager PIN must be entered before applying discounts, voiding, or refunding orders.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (requireManagerPin) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newPinText,
                                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPinText = it },
                                    label = { Text("New PIN (4–6 digits)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = { vm.setManagerPin(newPinText); newPinText = "" },
                                    enabled = newPinText.length in 4..6,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Key, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Set PIN")
                                }
                            }
                            if (managerPinHash.isNotBlank()) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = GreenSuccess)
                                    Text("PIN is set", style = MaterialTheme.typography.labelMedium, color = GreenSuccess)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(
                                        onClick = { vm.setManagerPin("") },
                                        colors  = ButtonDefaults.textButtonColors(contentColor = RedError)
                                    ) { Text("Clear PIN") }
                                }
                            } else {
                                Text(
                                    "No PIN set yet — any 4-6 digit PIN will be accepted until one is saved.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberWarning
                                )
                            }
                        }
                    }
                }
            }

            // Offline DB Management (only in local/standalone mode)
            if (isLocalMode) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Storage, null, Modifier.size(20.dp), tint = AmberWarning)
                                Text("Offline Database Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider()
                            Text(
                                "Manage your local SQLite database. Backup regularly to protect your data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider()

                            // Share Database (peer server toggle)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Wifi, null, Modifier.size(16.dp), tint = if (isPeerServerRunning) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Share Database (LAN Server)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    }
                                    if (isPeerServerRunning) {
                                        val ip = localIpAddress ?: "Unknown IP"
                                        Text("Running on $ip:7001 — other devices can connect using this IP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GreenSuccess)
                                    } else {
                                        Text("Allow other Android devices to use this database over WiFi",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = isPeerServerRunning,
                                    onCheckedChange = { on ->
                                        if (on) vm.startPeerServer() else vm.stopPeerServer()
                                    }
                                )
                            }

                            HorizontalDivider()

                            // Backup
                            OutlinedButton(
                                onClick = { triggerBackup() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isDbOpWorking,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess)
                            ) {
                                if (isDbOpWorking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Backup, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Backup Database")
                            }

                            // Restore
                            OutlinedButton(
                                onClick = { restorePicker.launch("application/octet-stream") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isDbOpWorking,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Restore, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Restore Database")
                            }

                            // Clean
                            OutlinedButton(
                                onClick = { showCleanDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isDbOpWorking,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Clean Database")
                            }
                        }
                    }
                }
            }

            // Appearance
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()

                        // Theme mode
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Theme", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("System", "Light", "Black").forEach { mode ->
                                    FilterChip(
                                        selected = themeMode == mode,
                                        onClick  = { vm.setThemeMode(mode) },
                                        label    = { Text(mode, style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = if (themeMode == mode) ({
                                            Icon(Icons.Default.Check, null, Modifier.size(12.dp))
                                        }) else null,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Accent color
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Accent Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val accentOptions = listOf(
                                "Orange" to MaterialTheme.colorScheme.primary,
                                "Teal"   to TealAccent,
                                "Blue"   to BlueInfo,
                                "Green"  to GreenSuccess,
                                "Purple" to PurpleKitchen
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                accentOptions.forEach { (name, color) ->
                                    val selected = accentColor == name
                                    OutlinedButton(
                                        onClick  = { vm.setAccentColor(name) },
                                        modifier = Modifier.weight(1f),
                                        border   = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else color.copy(alpha = 0.4f)),
                                        colors   = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selected) color.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color(0x00000000),
                                            contentColor   = color
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Surface(
                                                shape  = CircleShape,
                                                color  = color,
                                                modifier = Modifier.size(20.dp)
                                            ) {}
                                            Spacer(Modifier.height(4.dp))
                                            Text(name, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Raast Payment QR ─────────────────────────────────────────────
            item {
                var localRaastId by remember(raastId) { mutableStateOf(raastId) }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.QrCode, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Raast Payment QR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("Enter your Raast ID (phone number / IBAN) so customers can scan a QR code to pay directly.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value         = localRaastId,
                            onValueChange = { localRaastId = it },
                            label         = { Text("Raast ID (e.g. 03001234567 or PK36SCBL...)") },
                            modifier      = Modifier.fillMaxWidth(),
                            singleLine    = true,
                            leadingIcon   = { Icon(Icons.Default.AccountBalance, null, Modifier.size(18.dp)) }
                        )
                        Button(
                            onClick  = { vm.setRaastId(localRaastId) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled  = localRaastId.trim().isNotBlank()
                        ) {
                            Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save Raast ID")
                        }
                        if (raastId.isNotBlank()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = GreenSuccess)
                                Text("Saved: $raastId", style = MaterialTheme.typography.bodySmall, color = GreenSuccess)
                            }
                        }
                    }
                }
            }

            // Connection
            // ── FBR Digital Invoicing (DI API v1.12) ─────────────────────────────
            item {
                var localFbrEnabled      by remember { mutableStateOf(settings.fbrEnabled) }
                var localFbrToken        by remember { mutableStateOf(settings.fbrToken) }
                var localFbrNtn          by remember { mutableStateOf(settings.fbrNtn) }
                var localFbrBusinessName by remember { mutableStateOf(settings.fbrBusinessName) }
                var localFbrProvince     by remember { mutableStateOf(settings.fbrProvince) }
                var localFbrSellerAddr   by remember { mutableStateOf(settings.fbrSellerAddress) }
                var localFbrSandbox      by remember { mutableStateOf(settings.fbrSandboxMode) }
                var localFbrHsCode       by remember { mutableStateOf(settings.fbrHsCode) }
                var showToken            by remember { mutableStateOf(false) }
                LaunchedEffect(settings) {
                    localFbrEnabled      = settings.fbrEnabled
                    localFbrToken        = settings.fbrToken
                    localFbrNtn          = settings.fbrNtn
                    localFbrBusinessName = settings.fbrBusinessName
                    localFbrProvince     = settings.fbrProvince
                    localFbrSellerAddr   = settings.fbrSellerAddress
                    localFbrSandbox      = settings.fbrSandboxMode
                    localFbrHsCode       = settings.fbrHsCode
                }
                fun buildFbrCopy() = settings.copy(
                    fbrEnabled       = localFbrEnabled,
                    fbrToken         = localFbrToken,
                    fbrNtn           = localFbrNtn,
                    fbrBusinessName  = localFbrBusinessName,
                    fbrProvince      = localFbrProvince,
                    fbrSellerAddress = localFbrSellerAddr,
                    fbrSandboxMode   = localFbrSandbox,
                    fbrHsCode        = localFbrHsCode
                )
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("FBR Digital Invoicing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Enable FBR Integration", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked         = localFbrEnabled,
                                onCheckedChange = { localFbrEnabled = it; vm.saveSettings(buildFbrCopy()) }
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Sandbox Mode", style = MaterialTheme.typography.bodyMedium)
                                Text("Use FBR sandbox URL for testing", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked         = localFbrSandbox,
                                onCheckedChange = { localFbrSandbox = it },
                                enabled         = localFbrEnabled
                            )
                        }
                        OutlinedTextField(
                            value         = localFbrToken,
                            onValueChange = { localFbrToken = it },
                            label         = { Text("Bearer Token (issued by PRAL)") },
                            singleLine    = true,
                            enabled       = localFbrEnabled,
                            visualTransformation = if (showToken) androidx.compose.ui.text.input.VisualTransformation.None
                                                   else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                                }
                            },
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value         = localFbrNtn,
                            onValueChange = { localFbrNtn = it },
                            label         = { Text("Seller NTN / CNIC") },
                            singleLine    = true,
                            enabled       = localFbrEnabled,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value         = localFbrBusinessName,
                            onValueChange = { localFbrBusinessName = it },
                            label         = { Text("FBR Business Name (leave blank to use Company Name)") },
                            singleLine    = true,
                            enabled       = localFbrEnabled,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value         = localFbrProvince,
                            onValueChange = { localFbrProvince = it },
                            label         = { Text("Seller Province (e.g. Punjab, Sindh)") },
                            singleLine    = true,
                            enabled       = localFbrEnabled,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value         = localFbrSellerAddr,
                            onValueChange = { localFbrSellerAddr = it },
                            label         = { Text("Seller Address (leave blank to use Company Address)") },
                            singleLine    = true,
                            enabled       = localFbrEnabled,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value         = localFbrHsCode,
                            onValueChange = { localFbrHsCode = it },
                            label         = { Text("HS Code (default: 21069099 for food preparations)") },
                            singleLine    = true,
                            enabled       = localFbrEnabled,
                            modifier      = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick  = { vm.saveSettings(buildFbrCopy()) },
                                modifier = Modifier.weight(1f),
                                enabled  = localFbrEnabled
                            ) {
                                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }
                            OutlinedButton(
                                onClick  = { vm.testFbr() },
                                modifier = Modifier.weight(1f),
                                enabled  = localFbrEnabled && localFbrToken.isNotBlank() && localFbrNtn.isNotBlank()
                            ) {
                                Icon(Icons.Default.NetworkCheck, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Test Token")
                            }
                        }
                        Text(
                            "Bearer Token is issued by PRAL via the FBR portal (valid 5 years). " +
                            "When enabled, paid orders are queued and submitted to FBR DI API v1.12 " +
                            "every 30 seconds. The invoice number is printed on receipts once confirmed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Database Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        HorizontalDivider()
                        Text(
                            "Reset the saved database connection to reconfigure the server IP, port, and credentials.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.LinkOff, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reset Connection")
                        }
                    }
                }
            }
        }
    }

    if (showCleanDialog) {
        AlertDialog(
            onDismissRequest = { showCleanDialog = false },
            icon  = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clean Database?") },
            text  = { Text("This will permanently delete ALL data in the local database and recreate empty tables. This cannot be undone. Make a backup first.") },
            confirmButton = {
                Button(
                    onClick = {
                        showCleanDialog = false
                        vm.cleanLocalDb(context) {}
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clean") }
            },
            dismissButton = { TextButton(onClick = { showCleanDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRestoreDialog && pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false; pendingRestoreUri = null },
            icon  = { Icon(Icons.Default.Restore, null, tint = AmberWarning) },
            title = { Text("Restore Database?") },
            text  = { Text("This will replace the current database with the selected backup file. The app will restart automatically. Any unsaved data will be lost.") },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = pendingRestoreUri!!
                        showRestoreDialog = false
                        pendingRestoreUri = null
                        vm.restoreLocalDb(context, uri) {
                            val activity = context as? Activity
                            val intent = activity?.intent?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            if (intent != null) {
                                activity.finish()
                                context.startActivity(intent)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false; pendingRestoreUri = null }) { Text("Cancel") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            icon  = { Icon(Icons.Default.Warning, null, tint = AmberWarning) },
            title = { Text("Reset Connection?") },
            text  = { Text("This will clear the saved database configuration. You will need to set up the connection again.") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        vm.resetConnection(onResetConnection)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    if (showBtPicker) {
        BtPrinterPickerDialog(
            devices   = pairedBtDevices,
            onSelect  = { device ->
                @Suppress("MissingPermission")
                val name = device.name ?: device.address
                vm.saveReceiptPrinter(device.address, name)
                showBtPicker = false
            },
            onDismiss = { showBtPicker = false }
        )
    }

    if (showKitchenDialog && editingPrinter != null) {
        KitchenPrinterDialog(
            initial = editingPrinter!!,
            isEdit  = editingPrinterIndex >= 0,
            onDismiss = { showKitchenDialog = false },
            onConfirm = { cfg ->
                val updated = kitchenPrinters.toMutableList()
                if (editingPrinterIndex >= 0) updated[editingPrinterIndex] = cfg else updated.add(cfg)
                vm.saveKitchenPrinters(updated)
                showKitchenDialog = false
            }
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BtPrinterPickerDialog(
    devices: List<BluetoothDevice>,
    onSelect: (BluetoothDevice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Bluetooth, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Select Receipt Printer") },
        text  = {
            if (devices.isEmpty()) {
                Text("No paired Bluetooth devices found.\nPair your ESC/POS thermal printer in Android Settings → Bluetooth first.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(devices) { device ->
                        @Suppress("MissingPermission")
                        OutlinedButton(onClick = { onSelect(device) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Print, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(device.name ?: "Unknown", style = MaterialTheme.typography.bodyMedium)
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
private fun BulkAssignPrinterCard(
    kitchenPrinters:  List<KitchenPrinterConfig>,
    categories:       List<Category>,
    products:         List<Pair<Int,String>>,
    onAssignCategory: (printerName: String, categoryId: Int?) -> Unit,
    onAssignProduct:  (printerName: String, productId: Int)  -> Unit
) {
    var expanded        by remember { mutableStateOf(false) }
    var byProduct       by remember { mutableStateOf(false) }
    var printerExpanded by remember { mutableStateOf(false) }
    var targetExpanded  by remember { mutableStateOf(false) }
    var selectedPrinter by remember { mutableStateOf("") }
    var selectedCatId   by remember { mutableStateOf<Int?>(null) }
    var selectedProdId  by remember { mutableStateOf<Int?>(null) }

    val selectedCatName  = categories.firstOrNull { it.categoryId == selectedCatId }?.categoryName ?: "All Categories"
    val selectedProdName = products.firstOrNull { it.first == selectedProdId }?.second ?: "Select Product"

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Bulk Assign Kitchen Printer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            if (expanded) {
                HorizontalDivider()

                // Mode toggle
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !byProduct,
                        onClick  = { byProduct = false; selectedProdId = null },
                        label    = { Text("By Category") }
                    )
                    FilterChip(
                        selected = byProduct,
                        onClick  = { byProduct = true; selectedCatId = null },
                        label    = { Text("By Product") }
                    )
                }

                // Printer dropdown
                ExposedDropdownMenuBox(expanded = printerExpanded, onExpandedChange = { printerExpanded = it }) {
                    OutlinedTextField(
                        value         = selectedPrinter.ifBlank { "Select Printer" },
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Kitchen Printer") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(printerExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = printerExpanded, onDismissRequest = { printerExpanded = false }) {
                        kitchenPrinters.forEach { cfg ->
                            DropdownMenuItem(
                                text    = { Text(cfg.printerName) },
                                onClick = { selectedPrinter = cfg.printerName; printerExpanded = false }
                            )
                        }
                    }
                }

                // Category or product dropdown
                ExposedDropdownMenuBox(expanded = targetExpanded, onExpandedChange = { targetExpanded = it }) {
                    OutlinedTextField(
                        value         = if (byProduct) selectedProdName else selectedCatName,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text(if (byProduct) "Product" else "Category") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(targetExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
                        if (byProduct) {
                            products.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text    = { Text(name) },
                                    onClick = { selectedProdId = id; targetExpanded = false }
                                )
                            }
                        } else {
                            DropdownMenuItem(
                                text    = { Text("All Categories") },
                                onClick = { selectedCatId = null; targetExpanded = false }
                            )
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text    = { Text(cat.categoryName) },
                                    onClick = { selectedCatId = cat.categoryId; targetExpanded = false }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick  = {
                        if (selectedPrinter.isBlank()) return@Button
                        if (byProduct) {
                            selectedProdId?.let { onAssignProduct(selectedPrinter, it) }
                        } else {
                            onAssignCategory(selectedPrinter, selectedCatId)
                        }
                    },
                    enabled  = selectedPrinter.isNotBlank() && (!byProduct || selectedProdId != null),
                    modifier = Modifier.align(Alignment.End),
                    colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                ) {
                    Icon(Icons.Default.Print, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Apply")
                }
            }
        }
    }
}

@Composable
private fun KitchenPrinterDialog(
    initial: KitchenPrinterConfig,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (KitchenPrinterConfig) -> Unit
) {
    var name      by remember { mutableStateOf(initial.printerName) }
    var ip        by remember { mutableStateOf(initial.ipAddress) }
    var port      by remember { mutableStateOf(initial.port.toString()) }
    var paperType by remember { mutableStateOf(initial.paperType ?: "Thermal") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Print, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(if (isEdit) "Edit Kitchen Printer" else "Add Kitchen Printer") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Printer Name (matches product PrinterName)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ip, onValueChange = { ip = it },
                    label = { Text("IP Address (e.g. 192.168.1.20)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port, onValueChange = { port = it },
                    label = { Text("Port (default 9100)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("Paper Type", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = paperType != "A4",
                        onClick  = { paperType = "Thermal" },
                        label    = { Text("Thermal") },
                        leadingIcon = if (paperType != "A4") ({ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }) else null
                    )
                    FilterChip(
                        selected = paperType == "A4",
                        onClick  = { paperType = "A4" },
                        label    = { Text("A4") },
                        leadingIcon = if (paperType == "A4") ({ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }) else null
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && ip.isNotBlank())
                        onConfirm(KitchenPrinterConfig(
                            printerName = name.trim(),
                            ipAddress   = ip.trim(),
                            port        = port.toIntOrNull() ?: 9100,
                            paperType   = if (paperType == "A4") "A4" else null
                        ))
                },
                colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
            ) { Text(if (isEdit) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
