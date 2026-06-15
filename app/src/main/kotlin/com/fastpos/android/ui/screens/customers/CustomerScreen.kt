@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.customers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.Customer
import com.fastpos.android.data.models.CustomerFeedback
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.WalletTransaction
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.utils.formatDateTime
import com.fastpos.android.utils.orderStatusColor
import com.fastpos.android.utils.paymentStatusColor
import com.fastpos.android.viewmodels.CustomerViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CustomerScreen(
    onNavigateBack: () -> Unit,
    vm: CustomerViewModel = hiltViewModel()
) {
    val customers        by vm.customers.collectAsState()
    val isLoading        by vm.isLoading.collectAsState()
    val message          by vm.message.collectAsState()
    val search           by vm.search.collectAsState()
    val settings         by vm.session.settings.collectAsState()
    val selectedCustomer by vm.selectedCustomer.collectAsState()
    val sym              = settings.currencySymbol
    val snack            = remember { SnackbarHostState() }

    var tab          by remember { mutableIntStateOf(0) }
    var showAdd      by remember { mutableStateOf(false) }
    var editCustomer by remember { mutableStateOf<Customer?>(null) }
    var deleteTarget by remember { mutableStateOf<Customer?>(null) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    // Dialogs
    if (showAdd) {
        CustomerFormDialog(title = "Add Customer",
            onConfirm = { n, p, a -> vm.addCustomer(n, p, a); showAdd = false },
            onDismiss = { showAdd = false })
    }
    editCustomer?.let { c ->
        CustomerFormDialog(title = "Edit Customer",
            initialName = c.customerName, initialPhone = c.phone, initialAddr = c.address,
            onConfirm = { n, p, a -> vm.updateCustomer(c.customerId, n, p, a); editCustomer = null },
            onDismiss = { editCustomer = null })
    }
    deleteTarget?.let { c ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon  = { Icon(Icons.Default.Warning, null, tint = RedError) },
            title = { Text("Delete '${c.customerName}'?") },
            text  = { Text("Cannot delete if the customer has order history.") },
            confirmButton = {
                Button(onClick = { vm.deleteCustomer(c.customerId); deleteTarget = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Customers")
                            if (selectedCustomer != null)
                                Text(selectedCustomer!!.customerName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                    actions = {
                        if (selectedCustomer != null && tab != 0) {
                            IconButton(onClick = { vm.closeDetail(); tab = 0 }) {
                                Icon(Icons.Default.PersonOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, null) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    Tab(selected = tab == 0, onClick = { tab = 0 },
                        text = { Text("Customers") },
                        icon = { Icon(Icons.Default.People, null, Modifier.size(16.dp)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 },
                        text = { Text("Orders") },
                        icon = { Icon(Icons.Default.Receipt, null, Modifier.size(16.dp)) })
                    Tab(selected = tab == 2, onClick = { tab = 2 },
                        text = { Text("Feedback") },
                        icon = { Icon(Icons.Default.Star, null, Modifier.size(16.dp)) })
                    Tab(selected = tab == 3, onClick = { tab = 3 },
                        text = { Text("Wallet") },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(16.dp)) })
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snack) },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.PersonAdd, null)
                }
            }
        }
    ) { padding ->
        when (tab) {
            0 -> CustomersTab(
                customers = customers,
                isLoading = isLoading,
                search    = search,
                selected  = selectedCustomer,
                padding   = padding,
                onSearch  = vm::setSearch,
                onSelect  = { c -> vm.openDetail(c); tab = 1 },
                onEdit    = { editCustomer = it },
                onDelete  = { deleteTarget = it }
            )
            1 -> CustomerSubTab(
                customer  = selectedCustomer,
                padding   = padding,
                emptyIcon = Icons.Default.Receipt,
                emptyText = "Select a customer to view orders"
            ) {
                OrdersContent(vm = vm, sym = sym)
            }
            2 -> CustomerSubTab(
                customer  = selectedCustomer,
                padding   = padding,
                emptyIcon = Icons.Default.StarBorder,
                emptyText = "Select a customer to view feedback"
            ) {
                FeedbackContent(vm = vm)
            }
            3 -> CustomerSubTab(
                customer  = selectedCustomer,
                padding   = padding,
                emptyIcon = Icons.Default.AccountBalanceWallet,
                emptyText = "Select a customer to view wallet"
            ) {
                WalletContent(vm = vm, sym = sym)
            }
        }
    }
}

// ── Customers Tab ─────────────────────────────────────────────────────────────

@Composable
private fun CustomersTab(
    customers: List<Customer>,
    isLoading: Boolean,
    search:    String,
    selected:  Customer?,
    padding:   PaddingValues,
    onSearch:  (String) -> Unit,
    onSelect:  (Customer) -> Unit,
    onEdit:    (Customer) -> Unit,
    onDelete:  (Customer) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(
            value = search, onValueChange = onSearch,
            label = { Text("Search name or phone…") },
            leadingIcon  = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Clear, null) } },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true
        )
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            customers.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PeopleOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No customers found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                Text("${customers.size} customers", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(customers, key = { it.customerId }) { c ->
                        val isSelected = c.customerId == selected?.customerId
                        CustomerCard(
                            customer   = c,
                            isSelected = isSelected,
                            onTap      = { onSelect(c) },
                            onEdit     = { onEdit(c) },
                            onDelete   = { onDelete(c) }
                        )
                    }
                }
            }
        }
    }
}

// ── Wrapper for tabs that need a customer selected ────────────────────────────

@Composable
private fun CustomerSubTab(
    customer:  Customer?,
    padding:   PaddingValues,
    emptyIcon: androidx.compose.ui.graphics.vector.ImageVector,
    emptyText: String,
    content:   @Composable () -> Unit
) {
    if (customer == null) {
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(emptyIcon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
                Text("← Go to Customers tab and tap a name",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    } else {
        Box(Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

// ── Orders Content ────────────────────────────────────────────────────────────

@Composable
private fun OrdersContent(vm: CustomerViewModel, sym: String) {
    val orders     by vm.customerOrders.collectAsState()
    val isLoading  by vm.ordersLoading.collectAsState()
    val customer   by vm.selectedCustomer.collectAsState()

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        orders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ReceiptLong, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("No orders for ${customer?.customerName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        else -> LazyColumn(
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text("${orders.size} order(s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
            items(orders) { order ->
                OrderCard(order = order, sym = sym)
            }
        }
    }
}

@Composable
private fun OrderCard(order: Order, sym: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(order.orderNo, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraSmall) {
                        Text(order.orderType, Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (order.itemCount > 0)
                        Text("${order.itemCount} items", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(order.grandTotal.formatCurrency(sym), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(order.createdAt.formatDateTime(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Badge(containerColor = Color(order.orderStatus.orderStatusColor())) {
                        Text(order.orderStatus, style = MaterialTheme.typography.labelSmall,
                            color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    Badge(containerColor = Color(order.paymentStatus.paymentStatusColor())) {
                        Text(order.paymentStatus, style = MaterialTheme.typography.labelSmall,
                            color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
            if (order.discountAmount > 0 || order.taxAmount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (order.discountAmount > 0)
                        Text("Disc: ${order.discountAmount.formatCurrency(sym)}", style = MaterialTheme.typography.labelSmall, color = GreenSuccess)
                    if (order.taxAmount > 0)
                        Text("Tax: ${order.taxAmount.formatCurrency(sym)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── Feedback Content ──────────────────────────────────────────────────────────

@Composable
private fun FeedbackContent(vm: CustomerViewModel) {
    val feedbacks  by vm.feedbacks.collectAsState()
    val isLoading  by vm.feedbackLoading.collectAsState()
    var rating     by remember { mutableIntStateOf(5) }
    var comment    by remember { mutableStateOf("") }
    val dateFmt    = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Add feedback card
        Card(Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add Feedback", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Rating:", style = MaterialTheme.typography.bodySmall)
                    (1..5).forEach { r ->
                        IconButton(onClick = { rating = r }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Star, null,
                                tint = if (r <= rating) AmberWarning else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(22.dp))
                        }
                    }
                    Text("$rating / 5", style = MaterialTheme.typography.labelSmall, color = AmberWarning,
                        fontWeight = FontWeight.SemiBold)
                }
                OutlinedTextField(value = comment, onValueChange = { comment = it },
                    label = { Text("Comment (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                Button(onClick = { vm.addFeedback(rating, comment); comment = ""; rating = 5 },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarning)) {
                    Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save Feedback")
                }
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
        Text("History (${feedbacks.size})",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (feedbacks.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No feedback recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                feedbacks.forEach { fb ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(fb.ratingStars, color = AmberWarning, style = MaterialTheme.typography.bodySmall)
                                    Text("${fb.rating}/5", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (fb.comment.isNotBlank())
                                    Text(fb.comment, style = MaterialTheme.typography.bodySmall)
                                Text(dateFmt.format(fb.feedbackDate), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { vm.deleteFeedback(fb.feedbackId) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, null, tint = RedError, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ── Wallet Content ────────────────────────────────────────────────────────────

@Composable
private fun WalletContent(vm: CustomerViewModel, sym: String) {
    val balance    by vm.walletBalance.collectAsState()
    val txs        by vm.walletTransactions.collectAsState()
    val isLoading  by vm.walletLoading.collectAsState()
    val topUpAmt   by vm.topUpAmount.collectAsState()
    val customer   by vm.selectedCustomer.collectAsState()
    val txFmt      = remember { SimpleDateFormat("dd MMM  hh:mm a", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        // Balance + top-up
        Card(Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Wallet Balance", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(balance.formatCurrency(sym), style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (balance > 0) GreenSuccess else MaterialTheme.colorScheme.onPrimaryContainer)
                        customer?.let {
                            Text(it.customerName, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Icon(Icons.Default.AccountBalanceWallet, null, Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = topUpAmt, onValueChange = vm::setTopUpAmount,
                        label = { Text("Top-Up Amount") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = { Text(sym, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodyMedium) }
                    )
                    Button(
                        onClick  = vm::topUpWallet,
                        enabled  = topUpAmt.toDoubleOrNull()?.let { it > 0 } == true,
                        colors   = ButtonDefaults.buttonColors(containerColor = GreenSuccess),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Top Up", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text("Transaction History",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        HorizontalDivider(Modifier.padding(horizontal = 12.dp))

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            txs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(txs) { tx ->
                    val isCredit = tx.transType == "TopUp" || tx.transType == "Refund"
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp,
                            if (isCredit) GreenSuccess.copy(alpha = 0.4f) else RedError.copy(alpha = 0.4f))) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = if (isCredit) GreenSuccess.copy(0.15f) else RedError.copy(0.15f),
                                    shape = MaterialTheme.shapes.small) {
                                    Text(tx.transType, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                        color = if (isCredit) GreenSuccess else RedError)
                                }
                                Text(txFmt.format(tx.transDate), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(tx.amount.formatCurrency(sym), style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCredit) GreenSuccess else RedError)
                        }
                    }
                }
            }
        }
    }
}

// ── Customer Card ─────────────────────────────────────────────────────────────

@Composable
private fun CustomerCard(
    customer:   Customer,
    isSelected: Boolean,
    onTap:      () -> Unit,
    onEdit:     () -> Unit,
    onDelete:   () -> Unit
) {
    Card(
        onClick = onTap,
        colors  = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                             else MaterialTheme.colorScheme.surfaceVariant),
        border  = if (isSelected)
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AccountCircle, null, Modifier.size(40.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(customer.customerName, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if (customer.phone.isNotBlank())
                        Text(customer.phone, style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${customer.totalOrders} orders", style = MaterialTheme.typography.labelSmall,
                            color = if (customer.totalOrders > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (customer.loyaltyPoints > 0)
                            Text("${customer.loyaltyPoints} pts", style = MaterialTheme.typography.labelSmall,
                                color = AmberWarning)
                    }
                }
            }
            Row {
                IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit,   null, tint = AmberWarning) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = RedError) }
            }
        }
    }
}

// ── Customer Form Dialog ──────────────────────────────────────────────────────

@Composable
private fun CustomerFormDialog(
    title:        String,
    initialName:  String = "",
    initialPhone: String = "",
    initialAddr:  String = "",
    onConfirm:    (String, String, String) -> Unit,
    onDismiss:    () -> Unit
) {
    var name    by remember { mutableStateOf(initialName) }
    var phone   by remember { mutableStateOf(initialPhone) }
    var address by remember { mutableStateOf(initialAddr) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Full Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it },
                    label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = address, onValueChange = { address = it },
                    label = { Text("Address") }, modifier = Modifier.fillMaxWidth(), maxLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name, phone, address) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
