@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.fastpos.android.ui.screens.pos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.*
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.SmsHelper
import com.fastpos.android.utils.formatCurrency
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.fastpos.android.utils.PermissionKeys
import androidx.lifecycle.repeatOnLifecycle
import com.fastpos.android.viewmodels.PosUiEvent
import com.fastpos.android.viewmodels.PosViewModel
import com.fastpos.android.viewmodels.SettingsViewModel

@Composable
fun PosScreen(
    onNavigateBack:  () -> Unit,
    onOpenPayment:   (Int) -> Unit,
    vm:              PosViewModel    = hiltViewModel(),
    settingsVm:      SettingsViewModel = hiltViewModel()
) {
    val categories          by vm.categories.collectAsState()
    val products            by vm.products.collectAsState()
    val selectedCategory    by vm.selectedCategory.collectAsState()
    val searchQuery         by vm.searchQuery.collectAsState()
    val deals               by vm.deals.collectAsState()
    val showingDeals        by vm.showingDeals.collectAsState()
    val cart                by vm.cart.collectAsState()
    val isLoadingCatalog    by vm.isLoadingCatalog.collectAsState()
    val isPlacingOrder      by vm.isPlacingOrder.collectAsState()
    val orderType           by vm.orderType.collectAsState()
    val subTotal            by vm.subTotal.collectAsState()
    val taxAmount           by vm.taxAmount.collectAsState()
    val grandTotal          by vm.grandTotal.collectAsState()
    val discount            by vm.discount.collectAsState()
    val notes               by vm.notes.collectAsState()
    val settings            by vm.session.settings.collectAsState()
    val showSizeModDialog   by vm.showSizeModDialog.collectAsState()
    val selectedCustomer    by vm.selectedCustomer.collectAsState()
    val customerResults     by vm.customerResults.collectAsState()
    val isOffline           by vm.isOffline.collectAsState()
    val deliveryName        by vm.deliveryName.collectAsState()
    val deliveryPhone       by vm.deliveryPhone.collectAsState()
    val deliveryAddress     by vm.deliveryAddress.collectAsState()
    val pendingOffline      by vm.pendingOfflineOrders.collectAsState()
    val addToOrderNo        by vm.addToOrderNo.collectAsState()
    val editOrderNo         by vm.editOrderNo.collectAsState()
    val pinnedProducts      by vm.pinnedProducts.collectAsState()
    val pinnedIds           by vm.pinnedIds.collectAsState()
    val appliedVoucher      by vm.appliedVoucher.collectAsState()
    val voucherLoading      by vm.voucherLoading.collectAsState()
    val tables                  by vm.tables.collectAsState()
    val waiters                 by vm.waiters.collectAsState()
    val tableId                 by vm.tableId.collectAsState()
    val waiterId                by vm.waiterId.collectAsState()
    val deliveryCompanies       by vm.deliveryCompanies.collectAsState()
    val selectedDeliveryCompany by vm.selectedDeliveryCompany.collectAsState()
    val deliveryCharge          by vm.deliveryCharge.collectAsState()
    val serviceChargeAmount     by vm.serviceChargeAmount.collectAsState()
    val heldOrders              by vm.heldOrders.collectAsState()
    val currentShift            by vm.session.currentShift.collectAsState()
    val foundCustomerHint       by vm.foundCustomerHint.collectAsState()
    val takeawayName            by vm.takeawayName.collectAsState()
    val posUser                 by vm.session.currentUser.collectAsState()
    val posPermissions          by vm.session.permissions.collectAsState()
    val canAssignWaiter = posPermissions.contains(PermissionKeys.WAITERS_MANAGE) ||
                          posUser?.roleName?.equals("Admin", ignoreCase = true) == true

    val context = LocalContext.current
    val isMobileScreen = LocalConfiguration.current.screenWidthDp <= 700

    var showOrderTypeDialog  by remember { mutableStateOf(false) }
    var pendingIsBill        by remember { mutableStateOf(true) }

    val requireManagerPin    by settingsVm.requireManagerPin.collectAsState()
    val managerPinHash       by settingsVm.managerPinHash.collectAsState()

    var pendingSmsSend by remember { mutableStateOf<PosUiEvent.SmsSend?>(null) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingSmsSend?.let { SmsHelper.sendSms(context, it.phone, it.message) }
        }
        pendingSmsSend = null
    }

    var showDiscount         by remember { mutableStateOf(false) }
    var showPinForDiscount   by remember { mutableStateOf(false) }
    var showCustomerSearch   by remember { mutableStateOf(false) }
    var customerQuery        by remember { mutableStateOf("") }
    var notesDialogFor       by remember { mutableStateOf<String?>(null) } // cartId
    var savedOrderEvent      by remember { mutableStateOf<PosUiEvent.OrderSaved?>(null) }
    val kotPreviewGroups     by vm.kotPreviewGroups.collectAsState()
    val tokenPreview         by vm.tokenPreview.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    var cartToastMsg     by remember { mutableStateOf("") }
    var cartToastVisible by remember { mutableStateOf(false) }
    var cartToastKey     by remember { mutableIntStateOf(0) }

    LaunchedEffect(cartToastKey) {
        if (cartToastKey > 0) {
            cartToastVisible = true
            kotlinx.coroutines.delay(1500)
            cartToastVisible = false
        }
    }

    // Re-read shift from DB each time POS screen is opened (picks up WPF-opened shifts)
    LaunchedEffect(Unit) { vm.refreshShift() }

    // Refresh catalog every time POS screen is resumed so new products/waiters show immediately
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                vm.refreshCatalog(minIntervalMs = 3_000)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        vm.event.collect { event ->
            when (event) {
                is PosUiEvent.OrderPlaced  -> onOpenPayment(event.orderId)
                is PosUiEvent.OrderUpdated -> onNavigateBack()
                is PosUiEvent.OrderSaved   -> savedOrderEvent = event
                is PosUiEvent.OrderQueued  -> snackbarHost.showSnackbar("Order saved offline — will sync when connected.")
                is PosUiEvent.OrderHeld    -> snackbarHost.showSnackbar("Order ${event.tokenNo} put on hold.")
                is PosUiEvent.ItemsAdded   -> onNavigateBack()
                is PosUiEvent.Info        -> {
                    cartToastMsg = event.message
                    cartToastKey++
                }
                is PosUiEvent.Error       -> snackbarHost.showSnackbar(event.message)
                is PosUiEvent.SmsSend     -> {
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        SmsHelper.sendSms(context, event.phone, event.message)
                    } else {
                        pendingSmsSend = event
                        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                    }
                }
                is PosUiEvent.WhatsAppSend -> {
                    SmsHelper.sendWhatsApp(context, event.phone, event.message)
                }
            }
        }
    }

    // KOT Preview dialog
    if (kotPreviewGroups.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { vm.dismissKotPreview() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Receipt, null, tint = AmberWarning)
                    Text("KOT Preview", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(kotPreviewGroups) { group ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp), tint = AmberWarning)
                                        Text(group.printerName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { vm.printKotGroupNow(group) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) { Text("Print", style = MaterialTheme.typography.labelSmall) }
                                }
                                HorizontalDivider()
                                group.items.forEach { item ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(
                                            buildString {
                                                append(item.productName)
                                                if (!item.sizeName.isNullOrBlank()) append(" (${item.sizeName})")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text("x${item.quantity}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    if (item.notes.isNotBlank()) {
                                        Text("  Note: ${item.notes}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        kotPreviewGroups.forEach { vm.printKotGroupNow(it) }
                        vm.dismissKotPreview()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning)
                ) {
                    Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Print All")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissKotPreview() }) { Text("Proceed") }
            }
        )
    }

    // Takeaway token preview dialog
    tokenPreview?.let { token ->
        AlertDialog(
            onDismissRequest = { vm.clearTokenPreview() },
            icon  = { Icon(Icons.Default.ConfirmationNumber, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Takeaway Token", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Token:", style = MaterialTheme.typography.labelMedium)
                        Text(token.tokenNo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Order:", style = MaterialTheme.typography.labelMedium)
                        Text(token.orderNo, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!token.customerName.isNullOrBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Customer:", style = MaterialTheme.typography.labelMedium)
                            Text(token.customerName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    token.items.forEach { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(buildString {
                                append(item.productName)
                                if (!item.sizeName.isNullOrBlank()) append(" (${item.sizeName})")
                            }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text("x${item.quantity}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (!token.notes.isNullOrBlank()) {
                        Text("Note: ${token.notes}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.clearTokenPreview() }) { Text("OK") }
            }
        )
    }

    // Pay-later order confirmation dialog
    savedOrderEvent?.let { ev ->
        AlertDialog(
            onDismissRequest = { savedOrderEvent = null },
            icon  = { Icon(Icons.Default.CheckCircle, null, tint = GreenSuccess, modifier = Modifier.size(36.dp)) },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Order Saved", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            },
            text  = {
                Text("Order sent to kitchen. Collect payment when ready.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            },
            confirmButton = {
                Button(onClick = { savedOrderEvent = null }, colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)) {
                    Text("New Order")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.size(if (isMobileScreen) 29.dp else 48.dp)) {
                            Icon(Icons.Default.ArrowBack, null, Modifier.size(if (isMobileScreen) 14.dp else 24.dp))
                        }
                    },
                    title = { Text("New Order") },
                    actions = {
                        if (pendingOffline > 0) {
                            Badge(containerColor = RedError) {
                                Text("$pendingOffline", color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
                if (editOrderNo != null) {
                    Surface(color = AmberWarning.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Edit, null, Modifier.size(16.dp), tint = Color.White)
                                Text("Editing Order $editOrderNo",
                                    style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                            TextButton(onClick = vm::cancelEditOrder) {
                                Text("Cancel", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }
                if (addToOrderNo != null) {
                    Surface(color = BlueInfo.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AddShoppingCart, null, Modifier.size(16.dp), tint = Color.White)
                                Text("Adding items to $addToOrderNo",
                                    style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                            TextButton(onClick = vm::cancelAddToOrder) {
                                Text("Cancel", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }
                if (isOffline) {
                    Surface(color = RedError.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.WifiOff, null, Modifier.size(16.dp), tint = Color.White)
                                Text("Offline — using cached menu. Orders will sync on reconnect.",
                                    style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                            TextButton(onClick = vm::retryConnection) {
                                Text("Retry", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                    }
                }
                if (currentShift == null) {
                    Surface(color = AmberWarning.copy(alpha = 0.92f), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Lock, null, Modifier.size(16.dp), tint = Color.Black)
                            Text(
                                "No open shift — orders are blocked. Open a shift from the More menu.",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                // Order type is chosen in the overlay dialog when placing order
            }
        },
        snackbarHost = { AppSnackbarHost(snackbarHost) }
    ) { padding ->
        // Adaptive layout: side-by-side on wide screens (tablets), stacked on phones
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val isWide = maxWidth > 700.dp
            if (isWide) {
                Row(Modifier.fillMaxSize()) {
                    // Product catalog panel
                    Column(Modifier.weight(1.6f).fillMaxHeight()) {
                        CategoryBar(categories, selectedCategory, showingDeals, vm::selectCategory, vm::toggleDealsView)
                        if (!showingDeals) {
                            ProductSearchBar(searchQuery, vm::setSearchQuery, vm::submitSearchQuery, compactIconButtons = false)
                            if (pinnedProducts.isNotEmpty()) {
                                FavoritesStrip(pinnedProducts, settings.currencySymbol, vm::onProductTapped, vm::togglePin, compactIconButtons = false)
                            }
                        }
                        if (showingDeals) DealGrid(deals, settings.currencySymbol, vm::addDealToCart)
                        else ProductGrid(products, isLoadingCatalog, pinnedIds, settings.currencySymbol, vm::onProductTapped, vm::togglePin, vm::toggleProductAvailability, compactIconButtons = false)
                    }
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                    // Cart panel
                    CartPanel(
                        modifier          = Modifier.weight(1f).fillMaxHeight(),
                        cart              = cart,
                        subTotal          = subTotal,
                        taxAmount         = taxAmount,
                        grandTotal        = grandTotal,
                        discount          = discount,
                        deliveryCharge    = deliveryCharge,
                        serviceCharge     = serviceChargeAmount,
                        notes             = notes,
                        symbol            = settings.currencySymbol,
                        isPlacing         = isPlacingOrder,
                        addToOrderNo      = addToOrderNo,
                        editOrderNo       = editOrderNo,
                        orderType         = orderType,
                        selectedCustomer  = selectedCustomer,
                        appliedVoucher    = appliedVoucher,
                        voucherLoading    = voucherLoading,
                        onCustomerTap     = { showCustomerSearch = true },
                        onClearCustomer   = { vm.setCustomer(null) },
                        onIncrement          = vm::incrementItem,
                        onDecrement          = vm::decrementItem,
                        onRemove             = vm::removeItem,
                        onSetItemNotes       = { cartId -> notesDialogFor = cartId },
                        onSetItemQuantity    = vm::setItemQuantity,
                        onSetItemDiscount    = vm::setItemDiscount,
                        onSetItemPrice       = vm::setItemPrice,
                        heldOrders           = heldOrders,
                        onLoadHeldOrders     = vm::loadHeldOrders,
                        onResumeHeldOrder    = vm::resumeHeldOrder,
                        onNotesChange        = vm::setNotes,
                        onDiscount           = { if (requireManagerPin && managerPinHash.isNotBlank()) showPinForDiscount = true else showDiscount = true },
                        onApplyVoucher       = vm::applyVoucherCode,
                        onRemoveVoucher      = vm::removeVoucher,
                        onPlaceOrder         = { if (editOrderNo != null) vm.placeOrder() else { pendingIsBill = true; showOrderTypeDialog = true } },
                        onPlaceOrderPayLater = { pendingIsBill = false; showOrderTypeDialog = true },
                        onHoldOrder          = vm::holdOrder,
                        onClear              = vm::clearCart,
                        showOrderAndBill     = true,
                        compactIconButtons   = false
                    )
                }
            } else {
                // Phone: tabs for catalog vs cart
                var tab by remember { mutableIntStateOf(0) }
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }) {
                            Text("Menu", Modifier.padding(12.dp))
                        }
                        Tab(selected = tab == 1, onClick = { tab = 1 }) {
                            BadgedBox(badge = { if (cart.isNotEmpty()) Badge { Text(cart.size.toString()) } }) {
                                Text("Cart", Modifier.padding(12.dp))
                            }
                        }
                    }
                    when (tab) {
                        0 -> Column(Modifier.fillMaxSize()) {
                            CategoryBar(categories, selectedCategory, showingDeals, vm::selectCategory, vm::toggleDealsView)
                            if (!showingDeals) {
                                ProductSearchBar(searchQuery, vm::setSearchQuery, vm::submitSearchQuery, compactIconButtons = true)
                                if (pinnedProducts.isNotEmpty()) {
                                    FavoritesStrip(pinnedProducts, settings.currencySymbol, vm::onProductTapped, vm::togglePin, compactIconButtons = true)
                                }
                            }
                            if (showingDeals) DealGrid(deals, settings.currencySymbol, vm::addDealToCart)
                            else ProductGrid(products, isLoadingCatalog, pinnedIds, settings.currencySymbol, vm::onProductTapped, vm::togglePin, vm::toggleProductAvailability, compactIconButtons = true)
                        }
                        1 -> CartPanel(
                            modifier          = Modifier.fillMaxSize(),
                            cart              = cart,
                            subTotal          = subTotal,
                            taxAmount         = taxAmount,
                            grandTotal        = grandTotal,
                            discount          = discount,
                            deliveryCharge    = deliveryCharge,
                            serviceCharge     = serviceChargeAmount,
                            notes             = notes,
                            symbol            = settings.currencySymbol,
                            isPlacing         = isPlacingOrder,
                            addToOrderNo      = addToOrderNo,
                            orderType         = orderType,
                            selectedCustomer  = selectedCustomer,
                            appliedVoucher    = appliedVoucher,
                            voucherLoading    = voucherLoading,
                            onCustomerTap     = { showCustomerSearch = true },
                            onClearCustomer   = { vm.setCustomer(null) },
                            onIncrement          = vm::incrementItem,
                            onDecrement          = vm::decrementItem,
                            onRemove             = vm::removeItem,
                            onSetItemNotes       = { cartId -> notesDialogFor = cartId },
                            onSetItemQuantity    = vm::setItemQuantity,
                            onSetItemDiscount    = vm::setItemDiscount,
                            onSetItemPrice       = vm::setItemPrice,
                            heldOrders           = heldOrders,
                            onLoadHeldOrders     = vm::loadHeldOrders,
                            onResumeHeldOrder    = vm::resumeHeldOrder,
                            onNotesChange        = vm::setNotes,
                            onDiscount           = { if (requireManagerPin && managerPinHash.isNotBlank()) showPinForDiscount = true else showDiscount = true },
                            onApplyVoucher       = vm::applyVoucherCode,
                            onRemoveVoucher      = vm::removeVoucher,
                            editOrderNo          = editOrderNo,
                            onPlaceOrder         = { if (editOrderNo != null) vm.placeOrder() else { pendingIsBill = true; showOrderTypeDialog = true } },
                            onPlaceOrderPayLater = { pendingIsBill = false; showOrderTypeDialog = true },
                            onHoldOrder          = vm::holdOrder,
                            onClear              = vm::clearCart,
                            compactIconButtons   = true
                        )
                    }
                }
            }
        }
    }

    // Customer search dialog
    if (showCustomerSearch) {
        AlertDialog(
            onDismissRequest = { showCustomerSearch = false; customerQuery = ""; vm.setCustomer(selectedCustomer) },
            title = { Text("Select Customer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerQuery,
                        onValueChange = { customerQuery = it; vm.searchCustomers(it) },
                        label = { Text("Search name or phone…") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (customerResults.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(customerResults) { c ->
                                Card(
                                    onClick = {
                                        vm.setCustomer(c)
                                        showCustomerSearch = false
                                        customerQuery = ""
                                    },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(c.customerName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCustomerSearch = false; customerQuery = "" }) { Text("Cancel") } }
        )
    }

    // Centered cart-add toast overlay
    if (cartToastVisible || cartToastMsg.isNotEmpty()) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = cartToastVisible,
                enter   = fadeIn() + scaleIn(initialScale = 0.85f),
                exit    = fadeOut() + scaleOut(targetScale = 0.85f)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GreenSuccess),
                    shape  = MaterialTheme.shapes.medium,
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(cartToastMsg, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // Size / Modifier dialog
    if (showSizeModDialog) {
        SizeModifierDialog(
            product    = vm.pendingProduct.collectAsState().value,
            sizes      = vm.productSizes.collectAsState().value,
            groups     = vm.modifierGroups.collectAsState().value,
            symbol     = settings.currencySymbol,
            onConfirm  = vm::confirmSizeAndModifiers,
            onDismiss  = vm::dismissSizeModDialog
        )
    }

    // Item notes dialog
    notesDialogFor?.let { cartId ->
        val currentNotes = cart.firstOrNull { it.cartId == cartId }?.notes ?: ""
        var noteInput by remember(cartId) { mutableStateOf(currentNotes) }
        AlertDialog(
            onDismissRequest = { notesDialogFor = null },
            title = { Text("Item Note") },
            text = {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("e.g. No onions, extra sauce") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setItemNotes(cartId, noteInput)
                    notesDialogFor = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { notesDialogFor = null }) { Text("Cancel") } }
        )
    }

    // Manager PIN gate for discount
    if (showPinForDiscount) {
        ManagerPinDialog(
            onVerified = { showPinForDiscount = false; showDiscount = true },
            onDismiss  = { showPinForDiscount = false },
            checkPin   = settingsVm::checkManagerPin
        )
    }

    // Order type overlay — shown when user taps Order & Bill or Pay Later
    if (showOrderTypeDialog) {
        OrderTypeDialog(
            initialOrderType       = orderType,
            tables                 = tables,
            waiters                = waiters,
            initialTable           = tables.firstOrNull { it.tableId == tableId },
            initialWaiterId        = waiterId,
            deliveryCompanies      = deliveryCompanies,
            initialDeliveryCompany = selectedDeliveryCompany,
            initialDeliveryName    = deliveryName,
            initialDeliveryPhone   = deliveryPhone,
            initialDeliveryAddress = deliveryAddress,
            initialDeliveryCharge  = deliveryCharge,
            isBillMode             = pendingIsBill,
            canAssignWaiter        = canAssignWaiter,
            foundCustomerHint      = foundCustomerHint,
            foundCustomer          = selectedCustomer,
            initialTakeawayName    = takeawayName,
            onPhoneChanged         = vm::setDeliveryPhone,
            onTakeawayNameChanged  = vm::setTakeawayName,
            onRefreshTables        = vm::refreshTables,
            onConfirm = { type, table, wId, dCo, dName, dPhone, dAddr, dCharge ->
                vm.setOrderType(type)
                vm.setTable(table)
                vm.setWaiter(wId)
                vm.setDeliveryCompany(dCo)
                vm.setDeliveryName(dName)
                vm.setDeliveryPhone(dPhone)
                vm.setDeliveryAddress(dAddr)
                vm.setDeliveryCharge(dCharge)
                showOrderTypeDialog = false
                // DineIn and Delivery are always Pay Later
                if (pendingIsBill && type != "Delivery" && type != "DineIn") vm.placeOrder() else vm.placeOrderPayLater()
            },
            onDismiss = { showOrderTypeDialog = false }
        )
    }

    // Discount dialog
    if (showDiscount) {
        var discountInput by remember { mutableStateOf(if (discount > 0) discount.toString() else "") }
        var usePercent    by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showDiscount = false },
            title = { Text("Apply Discount") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Mode toggle
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !usePercent,
                            onClick  = { usePercent = false; discountInput = "" },
                            label    = { Text("Amount (${settings.currencySymbol})") }
                        )
                        FilterChip(
                            selected = usePercent,
                            onClick  = { usePercent = true; discountInput = "" },
                            label    = { Text("Percent (%)") }
                        )
                    }
                    // Quick presets (only shown for percent mode)
                    if (usePercent) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 10, 15, 20, 25).forEach { pct ->
                                FilterChip(
                                    selected = discountInput == pct.toString(),
                                    onClick  = { discountInput = pct.toString() },
                                    label    = { Text("$pct%", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f),
                                    colors   = FilterChipDefaults.filterChipColors(selectedContainerColor = GreenSuccess.copy(alpha = 0.18f), selectedLabelColor = GreenSuccess)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value         = discountInput,
                        onValueChange = { discountInput = it },
                        label         = { Text(if (usePercent) "Custom %" else "Amount") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    )
                    val previewAmt = if (usePercent)
                        (discountInput.toDoubleOrNull() ?: 0.0) / 100.0 * subTotal
                    else discountInput.toDoubleOrNull() ?: 0.0
                    if (previewAmt > 0) {
                        Text("= ${previewAmt.formatCurrency(settings.currencySymbol)} off",
                            style = MaterialTheme.typography.labelMedium, color = GreenSuccess)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val input  = discountInput.toDoubleOrNull() ?: 0.0
                    val amount = if (usePercent) input / 100.0 * subTotal else input
                    vm.setDiscount(amount)
                    showDiscount = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { vm.setDiscount(0.0); discountInput = ""; showDiscount = false }) { Text("Clear") }
            }
        )
    }

}


// ── Product search bar ────────────────────────────────────────────────────────

@Composable
private fun ProductSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit = {},
    compactIconButtons: Boolean = false
) {
    OutlinedTextField(
        value         = query,
        onValueChange = onQueryChange,
        placeholder   = { Text("Search or scan barcode…", style = MaterialTheme.typography.bodySmall) },
        leadingIcon   = { Icon(Icons.Default.Search, null, Modifier.size(18.dp)) },
        trailingIcon  = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(if (compactIconButtons) 29.dp else 48.dp)) {
                    Icon(Icons.Default.Clear, null, Modifier.size(if (compactIconButtons) 11.dp else 18.dp))
                }
            }
        } else null,
        modifier        = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        singleLine      = true,
        textStyle       = MaterialTheme.typography.bodySmall,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Done
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = { onSubmit() }
        )
    )
}

// ── Category bar ──────────────────────────────────────────────────────────────

@Composable
private fun CategoryBar(
    categories:   List<Category>,
    selected:     Int?,
    showingDeals: Boolean,
    onSelect:     (Int?) -> Unit,
    onDealsClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selected == null && !showingDeals,
                onClick  = { onSelect(null) },
                label    = { Text("All") }
            )
        }
        item {
            FilterChip(
                selected      = showingDeals,
                onClick       = onDealsClick,
                label         = { Text("Deals") },
                leadingIcon   = { Icon(Icons.Default.LocalOffer, null, Modifier.size(16.dp)) },
                colors        = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary)
            )
        }
        items(categories) { cat ->
            FilterChip(
                selected = selected == cat.categoryId && !showingDeals,
                onClick  = { onSelect(cat.categoryId) },
                label    = { Text(cat.categoryName) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = runCatching { Color(android.graphics.Color.parseColor(cat.colorCode)) }.getOrElse { MaterialTheme.colorScheme.primary }
                )
            )
        }
    }
}

@Composable
private fun DealGrid(
    deals:    List<Deal>,
    symbol:   String,
    onAddDeal: (Deal) -> Unit
) {
    if (deals.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocalOffer, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No active deals", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(deals) { deal ->
            DealCard(deal, symbol, onAddDeal)
        }
    }
}

@Composable
private fun DealCard(deal: Deal, symbol: String, onAdd: (Deal) -> Unit) {
    Card(
        onClick = { onAdd(deal) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.LocalOffer, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(deal.dealName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            if (deal.description.isNotBlank()) {
                Text(deal.description, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(2.dp))
            if (deal.dealPrice > 0) {
                Text(deal.dealPrice.formatCurrency(symbol),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            } else if (deal.discountPercent > 0) {
                Surface(shape = MaterialTheme.shapes.small, color = GreenSuccess.copy(0.15f)) {
                    Text("${deal.discountPercent.toInt()}% OFF",
                        style = MaterialTheme.typography.labelMedium, color = GreenSuccess,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = { onAdd(deal) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add to Cart", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── Product grid ──────────────────────────────────────────────────────────────

@Composable
private fun ProductGrid(
    products:             List<Product>,
    isLoading:            Boolean,
    pinnedIds:            Set<Int>,
    currency:             String,
    onTap:                (Product) -> Unit,
    onTogglePin:          (Int) -> Unit,
    onToggleAvailability: (Int) -> Unit,
    compactIconButtons:   Boolean = false
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (compactIconButtons) 92.dp else 120.dp),
        contentPadding = PaddingValues(if (compactIconButtons) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compactIconButtons) 8.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compactIconButtons) 8.dp else 10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(products) { product ->
            ProductCard(product, product.productId in pinnedIds, currency, onTap, onTogglePin, onToggleAvailability, compactIconButtons)
        }
    }
}

@Composable
private fun ProductCard(
    product:              Product,
    pinned:               Boolean,
    currency:             String,
    onTap:                (Product) -> Unit,
    onTogglePin:          (Int) -> Unit,
    onToggleAvailability: (Int) -> Unit,
    compactIconButtons:   Boolean = false
) {
    val bgColor      = runCatching { Color(android.graphics.Color.parseColor(product.categoryColor)) }.getOrElse { MaterialTheme.colorScheme.primary }
    val available    = product.isAvailable
    val contentAlpha = if (available) 1f else 0.38f
    Box(Modifier.fillMaxWidth().aspectRatio(if (compactIconButtons) 1.12f else 0.85f)) {
        Card(
            modifier = Modifier.fillMaxSize().combinedClickable(
                onClick     = { onTap(product) },
                onLongClick = { onToggleAvailability(product.productId) }
            ),
            colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = if (available) 0.18f else 0.06f)),
            border = BorderStroke(1.dp, bgColor.copy(alpha = if (available) 0.5f else 0.2f))
        ) {
            Column(
                modifier = Modifier.padding(if (compactIconButtons) 7.dp else 10.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier
                        .size(if (compactIconButtons) 34.dp else 48.dp)
                        .clip(CircleShape)
                        .background(bgColor.copy(alpha = if (available) 0.3f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        product.productName.first().uppercase(),
                        style = if (compactIconButtons) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        color = bgColor.copy(alpha = contentAlpha)
                    )
                }
                Text(
                    product.productName, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = if (compactIconButtons) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                if (available) {
                    Text(
                        text       = "$currency ${"%.0f".format(product.salePrice)}",
                        style      = if (compactIconButtons) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color      = bgColor
                    )
                } else {
                    Surface(
                        shape  = RoundedCornerShape(4.dp),
                        color  = RedError.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, RedError.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "SOLD OUT",
                            style      = MaterialTheme.typography.labelSmall,
                            color      = RedError,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        // Star pin overlay at top-end corner
        IconButton(
            onClick  = { onTogglePin(product.productId) },
            modifier = Modifier.size(if (compactIconButtons) 17.dp else 28.dp).align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector        = Icons.Default.Star,
                contentDescription = if (pinned) "Unpin" else "Pin to favourites",
                tint               = if (pinned) AmberWarning else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                modifier           = Modifier.size(if (compactIconButtons) 8.dp else 14.dp)
            )
        }
    }
}

// ── Favourites strip ──────────────────────────────────────────────────────────

@Composable
private fun FavoritesStrip(
    products:    List<Product>,
    currency:    String,
    onTap:       (Product) -> Unit,
    onUnpin:     (Int) -> Unit,
    compactIconButtons: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(Icons.Default.Star, null, Modifier.size(13.dp), tint = AmberWarning)
        Text("Favourites", style = MaterialTheme.typography.labelSmall,
            color = AmberWarning, fontWeight = FontWeight.SemiBold)
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(products, key = { it.productId }) { p ->
            val bgColor = runCatching {
                Color(android.graphics.Color.parseColor(p.categoryColor))
            }.getOrElse { MaterialTheme.colorScheme.primary }
            Surface(
                onClick = { onTap(p) },
                shape   = RoundedCornerShape(8.dp),
                color   = bgColor.copy(alpha = 0.14f),
                border  = BorderStroke(1.dp, bgColor.copy(alpha = 0.45f))
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column {
                        Text(p.productName, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 80.dp))
                        Text("$currency ${"%.0f".format(p.salePrice)}", style = MaterialTheme.typography.labelSmall,
                            color = bgColor, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { onUnpin(p.productId) }, modifier = Modifier.size(if (compactIconButtons) 11.dp else 18.dp)) {
                        Icon(Icons.Default.Star, null, Modifier.size(if (compactIconButtons) 6.dp else 10.dp), tint = AmberWarning)
                    }
                }
            }
        }
    }
}

// ── Cart panel ────────────────────────────────────────────────────────────────

@Composable
fun CartPanel(
    modifier:          Modifier,
    cart:              List<CartItem>,
    subTotal:          Double,
    taxAmount:         Double,
    grandTotal:        Double,
    discount:          Double,
    deliveryCharge:    Double,
    serviceCharge:     Double,
    notes:             String,
    symbol:            String,
    isPlacing:         Boolean,
    addToOrderNo:      String?,
    editOrderNo:       String? = null,
    orderType:         String,
    selectedCustomer:  com.fastpos.android.data.models.Customer?,
    appliedVoucher:    com.fastpos.android.data.models.VoucherValidation?,
    voucherLoading:    Boolean,
    onCustomerTap:     () -> Unit,
    onClearCustomer:   () -> Unit,
    onIncrement:       (String) -> Unit,
    onDecrement:       (String) -> Unit,
    onRemove:            (String) -> Unit,
    onSetItemNotes:      (String) -> Unit,
    onSetItemQuantity:   (String, Int) -> Unit,
    onSetItemDiscount:   (String, Double) -> Unit,
    onSetItemPrice:      (String, Double) -> Unit,
    heldOrders:          List<com.fastpos.android.data.models.Order> = emptyList(),
    onLoadHeldOrders:    () -> Unit = {},
    onResumeHeldOrder:   (com.fastpos.android.data.models.Order) -> Unit = {},
    onNotesChange:       (String) -> Unit,
    onDiscount:           () -> Unit,
    onApplyVoucher:       (String) -> Unit,
    onRemoveVoucher:      () -> Unit,
    onPlaceOrder:         () -> Unit,
    onPlaceOrderPayLater: () -> Unit,
    onHoldOrder:          () -> Unit,
    onClear:              () -> Unit,
    showOrderAndBill:     Boolean = true,
    compactIconButtons:   Boolean = false
) {
    var voucherCode       by remember { mutableStateOf("") }
    var showNoteDialog    by remember { mutableStateOf(false) }
    var noteDialogInput   by remember { mutableStateOf("") }
    var showVoucherDialog by remember { mutableStateOf(false) }
    var voucherDialogInput by remember { mutableStateOf("") }
    var qtyEditCartId     by remember { mutableStateOf<String?>(null) }
    var qtyEditText      by remember { mutableStateOf("") }
    var discountCartId   by remember { mutableStateOf<String?>(null) }
    var priceEditCartId  by remember { mutableStateOf<String?>(null) }
    var priceEditText    by remember { mutableStateOf("") }
    var showHeldSheet    by remember { mutableStateOf(false) }
    LaunchedEffect(appliedVoucher) { if (appliedVoucher != null) voucherCode = "" }

    qtyEditCartId?.let { cartId ->
        val currentQty = cart.firstOrNull { it.cartId == cartId }?.quantity ?: 1
        AlertDialog(
            onDismissRequest = { qtyEditCartId = null },
            title  = { Text("Set Quantity") },
            text   = {
                OutlinedTextField(
                    value         = qtyEditText,
                    onValueChange = { if (it.length <= 3) qtyEditText = it.filter { c -> c.isDigit() } },
                    label         = { Text("Quantity (current: $currentQty)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = qtyEditText.toIntOrNull()
                    if (q != null) onSetItemQuantity(cartId, q)
                    qtyEditCartId = null
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { qtyEditCartId = null }) { Text("Cancel") } }
        )
    }

    discountCartId?.let { cartId ->
        val item = cart.firstOrNull { it.cartId == cartId }
        val itemPrice = item?.let { (it.unitPrice + it.modifiersTotal) * it.quantity } ?: 0.0
        var discountInput by remember(cartId) { mutableStateOf(
            if ((item?.discountAmount ?: 0.0) > 0) "%.2f".format(item?.discountAmount ?: 0.0) else ""
        ) }
        var usePercent by remember(cartId) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { discountCartId = null },
            title = { Text("Item Discount") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item?.let {
                        Text(it.productName, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !usePercent, onClick = { usePercent = false; discountInput = "" },
                            label = { Text("Amount ($symbol)") })
                        FilterChip(selected = usePercent,  onClick = { usePercent = true; discountInput = "" },
                            label = { Text("Percent (%)") })
                    }
                    if (usePercent) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 10, 15, 20, 25).forEach { pct ->
                                FilterChip(
                                    selected = discountInput == pct.toString(),
                                    onClick  = { discountInput = pct.toString() },
                                    label    = { Text("$pct%", style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f),
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GreenSuccess.copy(alpha = 0.18f),
                                        selectedLabelColor     = GreenSuccess
                                    )
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value         = discountInput,
                        onValueChange = { discountInput = it },
                        label         = { Text(if (usePercent) "Custom %" else "Amount") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                    val previewAmt = if (usePercent)
                        (discountInput.toDoubleOrNull() ?: 0.0) / 100.0 * itemPrice
                    else discountInput.toDoubleOrNull() ?: 0.0
                    if (previewAmt > 0) {
                        Text("= ${previewAmt.formatCurrency(symbol)} off",
                            style = MaterialTheme.typography.labelMedium, color = GreenSuccess)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val input  = discountInput.toDoubleOrNull() ?: 0.0
                    val amount = if (usePercent) input / 100.0 * itemPrice else input
                    onSetItemDiscount(cartId, amount)
                    discountCartId = null
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { onSetItemDiscount(cartId, 0.0); discountCartId = null }) { Text("Clear") }
            }
        )
    }

    priceEditCartId?.let { cartId ->
        val item = cart.firstOrNull { it.cartId == cartId }
        AlertDialog(
            onDismissRequest = { priceEditCartId = null },
            title = { Text("Override Price") },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item?.let {
                        Text(it.productName, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Original: ${it.unitPrice.formatCurrency(symbol)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value         = priceEditText,
                        onValueChange = { priceEditText = it },
                        label         = { Text("New unit price") },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = priceEditText.toDoubleOrNull()
                    if (p != null && p > 0) onSetItemPrice(cartId, p)
                    priceEditCartId = null
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { priceEditCartId = null }) { Text("Cancel") } }
        )
    }

    if (showHeldSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showHeldSheet = false }
        ) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("Held Orders", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                if (heldOrders.isEmpty()) {
                    Text("No held orders.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    heldOrders.forEach { order ->
                        androidx.compose.material3.ListItem(
                            headlineContent = {
                                Text("${order.orderNo}  •  ${order.tokenNo}", fontWeight = FontWeight.Medium)
                            },
                            supportingContent = {
                                Text(buildString {
                                    append(order.orderType)
                                    order.tableName?.let { append(" — $it") }
                                    append("  •  ${order.grandTotal.formatCurrency(symbol)}")
                                    append("  •  ${order.itemCount} item(s)")
                                }, style = MaterialTheme.typography.labelSmall)
                            },
                            trailingContent = {
                                Button(
                                    onClick = { onResumeHeldOrder(order); showHeldSheet = false },
                                    colors  = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                                ) { Text("Resume") }
                            }
                        )
                        androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
        }
    }

    Column(modifier.padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text  = when {
                    editOrderNo != null  -> "Edit $editOrderNo"
                    addToOrderNo != null -> "Adding to $addToOrderNo"
                    else                 -> "Order"
                },
                style = MaterialTheme.typography.titleLarge,
                color = when {
                    editOrderNo  != null -> AmberWarning
                    addToOrderNo != null -> BlueInfo
                    else                 -> MaterialTheme.colorScheme.onSurface
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (heldOrders.isNotEmpty()) {
                    OutlinedButton(
                        onClick  = { onLoadHeldOrders(); showHeldSheet = true },
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning)
                    ) {
                        Icon(Icons.Default.Pause, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Held (${heldOrders.size})", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (cart.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(if (compactIconButtons) 29.dp else 48.dp),
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteSweep, "Clear", Modifier.size(if (compactIconButtons) 14.dp else 24.dp))
                    }
                }
            }
        }

        // Customer row
        if (selectedCustomer != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = GreenSuccess.copy(0.12f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, GreenSuccess.copy(0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.AccountCircle, null, Modifier.size(20.dp), tint = GreenSuccess)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(selectedCustomer.customerName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    IconButton(onClick = onClearCustomer, modifier = Modifier.size(if (compactIconButtons) 17.dp else 28.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(if (compactIconButtons) 8.dp else 14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // Scrollable content area — takes all remaining height so action buttons stay pinned below
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        if (cart.isEmpty()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 150.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ShoppingCartCheckout, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap a product to add it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                cart.forEach { item ->
                    CartItemRow(
                        item            = item,
                        symbol          = symbol,
                        onIncrement     = onIncrement,
                        onDecrement     = onDecrement,
                        onRemove        = onRemove,
                        onAddNote       = onSetItemNotes,
                        onEditQuantity  = { cartId -> qtyEditCartId = cartId; qtyEditText = "" },
                        onItemDiscount  = { cartId -> discountCartId = cartId },
                        onEditPrice     = { cartId -> priceEditCartId = cartId; priceEditText = "" },
                        compactIconButtons = compactIconButtons
                    )
                }
            }
        }

        // Note dialog
        if (showNoteDialog) {
            AlertDialog(
                onDismissRequest = { showNoteDialog = false },
                title = { Text("Order Note") },
                text = {
                    OutlinedTextField(
                        value         = noteDialogInput,
                        onValueChange = { noteDialogInput = it },
                        label         = { Text("Note (optional)") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        leadingIcon   = { Icon(Icons.Default.StickyNote2, null, Modifier.size(16.dp)) }
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onNotesChange(noteDialogInput); showNoteDialog = false }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showNoteDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Voucher dialog
        if (showVoucherDialog) {
            AlertDialog(
                onDismissRequest = { showVoucherDialog = false },
                title = { Text("Apply Voucher") },
                text = {
                    OutlinedTextField(
                        value         = voucherDialogInput,
                        onValueChange = { voucherDialogInput = it.uppercase() },
                        label         = { Text("Voucher Code") },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        leadingIcon   = { Icon(Icons.Default.LocalOffer, null, Modifier.size(16.dp)) }
                    )
                },
                confirmButton = {
                    if (voucherLoading) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            onClick  = { voucherCode = voucherDialogInput; onApplyVoucher(voucherDialogInput); showVoucherDialog = false },
                            enabled  = voucherDialogInput.isNotBlank()
                        ) { Text("Apply") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVoucherDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Voucher row (applied state only)
        if (appliedVoucher != null) {
            Surface(
                color    = GreenSuccess.copy(alpha = 0.12f),
                shape    = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.LocalOffer, null, Modifier.size(16.dp), tint = GreenSuccess)
                        Column {
                            Text(appliedVoucher.voucherCode, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = GreenSuccess)
                            Text(appliedVoucher.message, style = MaterialTheme.typography.labelSmall, color = GreenSuccess)
                        }
                    }
                    IconButton(onClick = onRemoveVoucher, modifier = Modifier.size(if (compactIconButtons) 17.dp else 28.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(if (compactIconButtons) 8.dp else 14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        // Totals
        TotalRow("Subtotal", subTotal, symbol)
        if (discount > 0) TotalRow("Discount", -discount, symbol, color = GreenSuccess)
        if (taxAmount > 0) TotalRow("Tax", taxAmount, symbol)
        if (serviceCharge > 0) TotalRow("Service Charge", serviceCharge, symbol, color = BlueInfo)
        if (deliveryCharge > 0) TotalRow("Delivery", deliveryCharge, symbol, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        TotalRow("Grand Total", grandTotal, symbol, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(8.dp))
        } // end scrollable content

        if (editOrderNo != null) {
            // ── Edit mode: single Update Order button ─────────────────────────
            Button(
                onClick  = onPlaceOrder,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled  = cart.isNotEmpty() && !isPlacing,
                colors   = ButtonDefaults.buttonColors(containerColor = AmberWarning)
            ) {
                if (isPlacing) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Update Order", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            // ── Normal mode: Discount + Note + Voucher | Hold | Order & Bill + Pay Later ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onDiscount,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Discount, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Discount", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { noteDialogInput = notes; showNoteDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = if (notes.isNotBlank())
                        ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning)
                    else
                        ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Default.StickyNote2, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Note", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { voucherDialogInput = ""; showVoucherDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = if (appliedVoucher != null)
                        ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess)
                    else
                        ButtonDefaults.outlinedButtonColors()
                ) {
                    Icon(Icons.Default.LocalOffer, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Voucher", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(4.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick  = onHoldOrder,
                    modifier = Modifier.weight(1f).height(52.dp),
                    enabled  = cart.isNotEmpty() && !isPlacing,
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning)
                ) {
                    Icon(Icons.Default.Pause, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Hold")
                }
                // Order & Bill hidden for third-party delivery company orders (WPF behaviour)
                if (showOrderAndBill) {
                    Button(
                        onClick  = onPlaceOrder,
                        modifier = Modifier.weight(1.6f).height(52.dp),
                        enabled  = cart.isNotEmpty() && !isPlacing
                    ) {
                        if (isPlacing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Default.Receipt, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Order & Bill", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            OutlinedButton(
                onClick  = onPlaceOrderPayLater,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled  = cart.isNotEmpty() && !isPlacing,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = GreenSuccess),
                border   = BorderStroke(1.dp, GreenSuccess)
            ) {
                Icon(Icons.Default.SendToMobile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Order (Pay Later)", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun CartItemRow(
    item:            CartItem,
    symbol:          String,
    onIncrement:     (String) -> Unit,
    onDecrement:     (String) -> Unit,
    onRemove:        (String) -> Unit,
    onAddNote:       (String) -> Unit,
    onEditQuantity:  (String) -> Unit = {},
    onItemDiscount:  (String) -> Unit = {},
    onEditPrice:     (String) -> Unit = {},
    compactIconButtons: Boolean = false
) {
    val actionButtonSize = if (compactIconButtons) 30.dp else 36.dp
    val actionIconSize = if (compactIconButtons) 16.dp else 18.dp
    val qtyButtonSize = if (compactIconButtons) 28.dp else 34.dp
    val qtyIconSize = if (compactIconButtons) 15.dp else 17.dp

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: name / extras / action buttons
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.productName,
                    style = if (compactIconButtons) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val extras = buildList {
                    item.sizeName?.let { add(it) }
                    if (item.selectedModifiers.isNotEmpty()) add(item.selectedModifiers.joinToString(", ") { it.modifierName })
                }
                if (extras.isNotEmpty()) {
                    Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.notes.isNotBlank()) {
                    Text("Note: ${item.notes}", style = MaterialTheme.typography.bodySmall,
                        color = AmberWarning, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.discountAmount > 0) {
                    Text("-${item.discountAmount.formatCurrency(symbol)}", style = MaterialTheme.typography.bodySmall, color = GreenSuccess, maxLines = 1)
                }
                // Action buttons below name
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        modifier = Modifier.size(actionButtonSize)
                    ) {
                        IconButton(onClick = { onRemove(item.cartId) }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Delete, null, Modifier.size(actionIconSize), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = AmberWarning.copy(alpha = if (item.notes.isNotBlank()) 0.20f else 0.12f),
                        modifier = Modifier.size(actionButtonSize)
                    ) {
                        IconButton(onClick = { onAddNote(item.cartId) }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Note, null, Modifier.size(actionIconSize), tint = AmberWarning)
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = GreenSuccess.copy(alpha = if (item.discountAmount > 0) 0.20f else 0.12f),
                        modifier = Modifier.size(actionButtonSize)
                    ) {
                        IconButton(onClick = { onItemDiscount(item.cartId) }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Discount, null, Modifier.size(actionIconSize), tint = GreenSuccess)
                        }
                    }
                }
            }
            // Right: qty controls + price
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onDecrement(item.cartId) }, modifier = Modifier.size(qtyButtonSize)) {
                    Icon(Icons.Default.Remove, null, Modifier.size(qtyIconSize))
                }
                Surface(
                    onClick  = { onEditQuantity(item.cartId) },
                    shape    = MaterialTheme.shapes.extraSmall,
                    color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.widthIn(min = if (compactIconButtons) 30.dp else 34.dp)
                ) {
                    Text(
                        text       = item.quantity.toString(),
                        modifier   = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        textAlign  = TextAlign.Center,
                        style      = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { onIncrement(item.cartId) }, modifier = Modifier.size(qtyButtonSize)) {
                    Icon(Icons.Default.Add, null, Modifier.size(qtyIconSize))
                }
                Spacer(Modifier.width(2.dp))
                Surface(
                    onClick  = { onEditPrice(item.cartId) },
                    shape    = MaterialTheme.shapes.extraSmall,
                    color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                ) {
                    Text(
                        text       = item.lineTotal.formatCurrency(symbol),
                        modifier   = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalRow(
    label:      String,
    amount:     Double,
    symbol:     String,
    color:      Color = MaterialTheme.colorScheme.onSurface,
    style:      androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight = FontWeight.Normal
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style, fontWeight = fontWeight, color = color)
        Text(amount.formatCurrency(symbol), style = style, fontWeight = fontWeight, color = color)
    }
}

// ── Size & Modifier dialog ────────────────────────────────────────────────────

@Composable
private fun SizeModifierDialog(
    product:   Product?,
    sizes:     List<ProductSize>,
    groups:    List<ModifierGroup>,
    symbol:    String,
    onConfirm: (ProductSize?, List<SelectedModifier>) -> Unit,
    onDismiss: () -> Unit
) {
    if (product == null) return
    var selectedSize by remember { mutableStateOf<ProductSize?>(sizes.firstOrNull()) }
    val selectedMods = remember { mutableStateMapOf<Int, SelectedModifier>() }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(product.productName, style = MaterialTheme.typography.titleLarge)

                // Sizes
                if (sizes.isNotEmpty()) {
                    Text("Choose Size", style = MaterialTheme.typography.titleMedium)
                    sizes.forEach { size ->
                        OutlinedButton(
                            onClick = { selectedSize = size },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (selectedSize?.sizeId == size.sizeId)
                                ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(0.15f))
                            else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text(size.sizeName)
                            Spacer(Modifier.weight(1f))
                            Text(size.price.formatCurrency(symbol))
                        }
                    }
                }

                // Modifiers
                groups.forEach { group ->
                    val groupHasError = validationError != null && group.isRequired &&
                        group.modifiers.none { selectedMods.containsKey(it.modifierId) }
                    Text(
                        group.groupName + if (group.isRequired) " *" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (groupHasError) MaterialTheme.colorScheme.error else LocalContentColor.current
                    )
                    group.modifiers.forEach { mod ->
                        val isSelected = selectedMods.containsKey(mod.modifierId)
                        OutlinedButton(
                            onClick = {
                                if (isSelected) selectedMods.remove(mod.modifierId)
                                else selectedMods[mod.modifierId] = SelectedModifier(mod.modifierId, mod.modifierName, mod.extraPrice)
                                validationError = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (isSelected)
                                ButtonDefaults.outlinedButtonColors(containerColor = TealAccent.copy(0.15f))
                            else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text(mod.modifierName)
                            if (mod.extraPrice > 0) {
                                Spacer(Modifier.weight(1f))
                                Text("+${mod.extraPrice.formatCurrency(symbol)}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                validationError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val missingGroup = groups.firstOrNull { g ->
                                g.isRequired && g.modifiers.none { selectedMods.containsKey(it.modifierId) }
                            }
                            if (missingGroup != null) {
                                validationError = "Please select an option for: ${missingGroup.groupName}"
                            } else {
                                onConfirm(selectedSize, selectedMods.values.toList())
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Add to Cart") }
                }
            }
        }
    }
}

// ── Order Type Dialog (overlay when placing order) ────────────────────────────

@Composable
private fun OrderTypeDialog(
    initialOrderType:       String,
    tables:                 List<RestaurantTable>,
    waiters:                List<Waiter>,
    initialTable:           RestaurantTable?,
    initialWaiterId:        Int?,
    deliveryCompanies:      List<DeliveryCompany>,
    initialDeliveryCompany: DeliveryCompany?,
    initialDeliveryName:    String,
    initialDeliveryPhone:   String,
    initialDeliveryAddress: String,
    initialDeliveryCharge:  Double,
    isBillMode:             Boolean,
    canAssignWaiter:        Boolean    = false,
    foundCustomerHint:      String?    = null,
    foundCustomer:          Customer?  = null,
    initialTakeawayName:    String     = "",
    onPhoneChanged:         (String) -> Unit = {},
    onTakeawayNameChanged:  (String) -> Unit = {},
    onRefreshTables:        () -> Unit = {},
    onConfirm: (orderType: String, table: RestaurantTable?, waiterId: Int?,
                deliveryCompany: DeliveryCompany?, deliveryName: String,
                deliveryPhone: String, deliveryAddress: String,
                deliveryCharge: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var orderType         by remember { mutableStateOf(initialOrderType) }
    // DineIn and Delivery are always Pay Later — Order & Bill is for Takeaway only
    val effectiveBillMode = isBillMode && orderType != "Delivery" && orderType != "DineIn"
    var localTable        by remember { mutableStateOf(initialTable) }
    var localWaiter       by remember { mutableStateOf(initialWaiterId) }
    var localDelivCo      by remember { mutableStateOf(initialDeliveryCompany) }
    var delivName         by remember { mutableStateOf(initialDeliveryName) }
    var delivPhone        by remember { mutableStateOf(initialDeliveryPhone) }
    var delivAddress      by remember { mutableStateOf(initialDeliveryAddress) }
    var delivCharge       by remember { mutableStateOf(if (initialDeliveryCharge > 0) initialDeliveryCharge.toString() else "") }
    var localTakeawayName by remember { mutableStateOf(initialTakeawayName) }

    LaunchedEffect(foundCustomer?.customerId) {
        if (foundCustomer != null) {
            if (delivName.isBlank()) delivName = foundCustomer.customerName
            if (delivAddress.isBlank()) delivAddress = foundCustomer.address
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape    = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        if (effectiveBillMode) "Order & Bill" else "Order — Pay Later",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider()

                // Order type row
                Text("Order Type", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        Triple("DineIn",   Icons.Default.TableRestaurant, "Dine In"),
                        Triple("Takeaway", Icons.Default.ShoppingBag,     "Takeaway"),
                        Triple("Delivery", Icons.Default.DeliveryDining,  "Delivery")
                    ).forEach { (type, icon, label) ->
                        FilterChip(
                            selected    = orderType == type,
                            onClick     = { orderType = type },
                            label       = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(icon, null, Modifier.size(13.dp)) },
                            modifier    = Modifier.weight(1f),
                            colors      = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor       = MaterialTheme.colorScheme.primary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // Dine In: table + waiter dropdowns
                if (orderType == "DineIn") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Table dropdown
                        var tableExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded         = tableExpanded,
                            onExpandedChange = { expanded ->
                                tableExpanded = expanded
                                if (expanded) onRefreshTables()
                            },
                            modifier         = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value         = localTable?.tableName ?: "Select Table",
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text("Table", style = MaterialTheme.typography.labelSmall) },
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(tableExpanded) },
                                textStyle     = MaterialTheme.typography.bodySmall,
                                modifier      = Modifier.menuAnchor().fillMaxWidth(),
                                singleLine    = true
                            )
                            ExposedDropdownMenu(expanded = tableExpanded, onDismissRequest = { tableExpanded = false }) {
                                DropdownMenuItem(
                                    text    = { Text("— None —", style = MaterialTheme.typography.bodySmall) },
                                    onClick = { localTable = null; tableExpanded = false }
                                )
                                tables.filter { it.isActive }.forEach { table ->
                                    val occupied = table.tableStatus.equals("Occupied", ignoreCase = true)
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(table.tableName, style = MaterialTheme.typography.bodySmall)
                                                if (occupied) {
                                                    Surface(shape = MaterialTheme.shapes.extraSmall, color = RedError.copy(0.15f)) {
                                                        Text("Occupied", style = MaterialTheme.typography.labelSmall, color = RedError,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                    }
                                                }
                                            }
                                        },
                                        onClick = { localTable = table; tableExpanded = false },
                                        enabled = !occupied || table.tableId == localTable?.tableId
                                    )
                                }
                            }
                        }

                        // Waiter dropdown — only visible to admin/manager roles
                        if (canAssignWaiter) {
                            var waiterExpanded by remember { mutableStateOf(false) }
                            val waiterName = waiters.firstOrNull { it.waiterId == localWaiter }?.waiterName
                            ExposedDropdownMenuBox(
                                expanded         = waiterExpanded,
                                onExpandedChange = { waiterExpanded = !waiterExpanded },
                                modifier         = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value         = waiterName ?: "Select Waiter",
                                    onValueChange = {},
                                    readOnly      = true,
                                    label         = { Text("Waiter", style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(waiterExpanded) },
                                    textStyle     = MaterialTheme.typography.bodySmall,
                                    modifier      = Modifier.menuAnchor().fillMaxWidth(),
                                    singleLine    = true
                                )
                                ExposedDropdownMenu(expanded = waiterExpanded, onDismissRequest = { waiterExpanded = false }) {
                                    DropdownMenuItem(
                                        text    = { Text("— None —", style = MaterialTheme.typography.bodySmall) },
                                        onClick = { localWaiter = null; waiterExpanded = false }
                                    )
                                    waiters.filter { it.isActive }.forEach { waiter ->
                                        DropdownMenuItem(
                                            text    = { Text(waiter.waiterName, style = MaterialTheme.typography.bodySmall) },
                                            onClick = { localWaiter = waiter.waiterId; waiterExpanded = false }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Takeaway: optional customer name
                if (orderType == "Takeaway") {
                    OutlinedTextField(
                        value         = localTakeawayName,
                        onValueChange = { localTakeawayName = it; onTakeawayNameChanged(it) },
                        label         = { Text("Customer Name (optional)", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon   = { Icon(Icons.Default.Person, null, Modifier.size(14.dp)) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodySmall
                    )
                }

                // Delivery: info fields
                if (orderType == "Delivery") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value         = delivName,
                            onValueChange = { delivName = it },
                            label         = { Text("Customer Name", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon   = { Icon(Icons.Default.Person, null, Modifier.size(14.dp)) },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true,
                            textStyle     = MaterialTheme.typography.bodySmall
                        )
                        Column(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value         = delivPhone,
                                onValueChange = { delivPhone = it; onPhoneChanged(it) },
                                label         = { Text("Phone", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon   = { Icon(Icons.Default.Phone, null, Modifier.size(14.dp)) },
                                modifier      = Modifier.fillMaxWidth(),
                                singleLine    = true,
                                textStyle     = MaterialTheme.typography.bodySmall,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                            )
                            if (foundCustomerHint != null) {
                                Text(
                                    text  = foundCustomerHint,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                )
                            }
                        }
                    }
                    // Address — full row so it never competes with company/charge
                    OutlinedTextField(
                        value         = delivAddress,
                        onValueChange = { delivAddress = it },
                        label         = { Text("Address", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon   = { Icon(Icons.Default.LocationOn, null, Modifier.size(14.dp)) },
                        modifier      = Modifier.fillMaxWidth(),
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodySmall
                    )
                    // Company + Charge on their own row with enough width each
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var companyExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded         = companyExpanded,
                            onExpandedChange = { companyExpanded = !companyExpanded },
                            modifier         = Modifier.weight(1.6f)
                        ) {
                            OutlinedTextField(
                                value         = localDelivCo?.companyName ?: "None",
                                onValueChange = {},
                                readOnly      = true,
                                label         = { Text("Delivery Co.", style = MaterialTheme.typography.labelSmall) },
                                trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(companyExpanded) },
                                textStyle     = MaterialTheme.typography.bodySmall,
                                modifier      = Modifier.menuAnchor().fillMaxWidth(),
                                singleLine    = true
                            )
                            ExposedDropdownMenu(expanded = companyExpanded, onDismissRequest = { companyExpanded = false }) {
                                DropdownMenuItem(
                                    text    = { Text("— None —", style = MaterialTheme.typography.bodySmall) },
                                    onClick = { localDelivCo = null; companyExpanded = false }
                                )
                                deliveryCompanies.forEach { company ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(company.companyName, style = MaterialTheme.typography.bodySmall)
                                                if (company.commissionPercent > 0) {
                                                    Text("${company.commissionPercent}% commission",
                                                        style = MaterialTheme.typography.labelSmall, color = AmberWarning)
                                                }
                                            }
                                        },
                                        onClick = { localDelivCo = company; companyExpanded = false }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value         = delivCharge,
                            onValueChange = { delivCharge = it },
                            label         = { Text("Charge", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon   = { Icon(Icons.Default.LocalShipping, null, Modifier.size(14.dp)) },
                            modifier      = Modifier.weight(1f),
                            singleLine    = true,
                            textStyle     = MaterialTheme.typography.bodySmall,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }

                HorizontalDivider()

                // Action buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            onConfirm(
                                orderType, localTable, localWaiter,
                                localDelivCo, delivName, delivPhone, delivAddress,
                                delivCharge.toDoubleOrNull() ?: 0.0
                            )
                        },
                        modifier = Modifier.weight(2f),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = if (effectiveBillMode) MaterialTheme.colorScheme.primary else GreenSuccess
                        )
                    ) {
                        Icon(if (effectiveBillMode) Icons.Default.Receipt else Icons.Default.AccessTime,
                            null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (effectiveBillMode) "Order & Bill" else "Pay Later")
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagerPinDialog(
    onVerified: () -> Unit,
    onDismiss:  () -> Unit,
    checkPin:   (String) -> Boolean
) {
    var pin   by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Lock, null, tint = AmberWarning) },
        title = { Text("Manager Authorization") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter manager PIN to continue.", style = MaterialTheme.typography.bodyMedium)
                if (error) Text("Incorrect PIN. Try again.", style = MaterialTheme.typography.labelMedium, color = RedError)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { pin = it; error = false } },
                    label = { Text("PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (checkPin(pin)) onVerified() else error = true
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
