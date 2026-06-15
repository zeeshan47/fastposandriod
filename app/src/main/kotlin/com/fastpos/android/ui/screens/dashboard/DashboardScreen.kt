@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.DashboardAlert
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.TopProduct
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.utils.formatDateTime
import com.fastpos.android.utils.orderStatusColor
import com.fastpos.android.viewmodels.DashboardViewModel

@Composable
fun DashboardScreen(
    onNavigateToPos:          () -> Unit,
    onNavigateToTables:       () -> Unit = {},
    onNavigateToKitchen:      () -> Unit,
    onNavigateToOrders:       () -> Unit,
    onNavigateToInventory:    () -> Unit = {},
    onNavigateToReservations: () -> Unit = {},
    onNavigateToVouchers:     () -> Unit = {},
    onNavigateToDelivery:     () -> Unit = {},
    onNavigateToPurchases:    () -> Unit = {},
    onLogout:                 () -> Unit,
    vm: DashboardViewModel = hiltViewModel()
) {
    val stats        by vm.stats.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    val error        by vm.error.collectAsState()
    val session      = vm.session
    val user         by session.currentUser.collectAsState()
    val settings     by session.settings.collectAsState()
    val currentShift by session.currentShift.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLogout      by remember { mutableStateOf(false) }
    var alertsExpanded  by remember { mutableStateOf(false) }
    var recentOrdersExpanded by remember { mutableStateOf(false) }
    var showLowStockSheet by remember { mutableStateOf(false) }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.clearError()
    }

    fun onAlertAction(route: String) {
        when (route) {
            "rawmaterials" -> onNavigateToInventory()
            "reservations" -> onNavigateToReservations()
            "vouchers"     -> onNavigateToVouchers()
            "delivery"     -> onNavigateToDelivery()
            "purchases"    -> onNavigateToPurchases()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                DashboardHeader(
                    userName = user?.fullName.orEmpty().ifBlank { "Administrator" },
                    isRefreshing = isLoading,
                    onRefresh = { vm.loadStats() },
                    onLogout = { showLogout = true }
                )
            }

            currentShift?.let { shift ->
                item {
                    ShiftStatusCard(shiftCode = shift.shiftCode)
                }
            }

            item {
                val tableTotal = stats.totalTables.coerceAtLeast(0)
                val salesDetail = if (stats.yesterdaySales > 0) {
                    val pct = (stats.todaySales - stats.yesterdaySales) / stats.yesterdaySales * 100
                    "${if (pct >= 0) "+" else ""}${pct.toInt()}% vs yesterday"
                } else "Shift ready"
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardKpiCard(Modifier.fillMaxWidth(), "Today's Sales", stats.todaySales.formatCurrency(settings.currencySymbol), salesDetail, Icons.Default.AccountBalanceWallet, GreenSuccess)
                    DashboardKpiCard(Modifier.fillMaxWidth(), "Orders", stats.todayOrders.toString(), "${stats.pendingOrders} pending", Icons.Default.ReceiptLong, BlueInfo)
                    DashboardKpiCard(Modifier.fillMaxWidth(), "Tables", "${stats.occupiedTables} / $tableTotal", "Busy / Total", Icons.Default.TableRestaurant, AmberWarning)
                }
            }

            item {
                TodaySummaryPanel(
                    modifier = Modifier.fillMaxWidth(),
                    cashSales = stats.cashInDrawer.takeIf { currentShift != null } ?: stats.todaySales,
                    cardSales = stats.todaySales - (stats.cashInDrawer.takeIf { currentShift != null } ?: 0.0),
                    onlineSales = stats.deliveryOrders * stats.avgOrderValue,
                    orders = stats.todayOrders,
                    customers = stats.todayCustomers,
                    currencySymbol = settings.currencySymbol,
                    onLowStock = { showLowStockSheet = true },
                    lowStockCount = stats.lowStockCount
                )
            }

            item { Text("Quick Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 840.dp) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            QuickActionTile(Modifier.weight(1f), "New Order", "Start a new order", Icons.Default.ShoppingCart, GreenSuccess, onNavigateToPos)
                            QuickActionTile(Modifier.weight(1f), "Tables", "View all tables", Icons.Default.TableRestaurant, BlueInfo, onNavigateToTables)
                            QuickActionTile(Modifier.weight(1f), "Kitchen", "Kitchen Display", Icons.Default.Restaurant, PurpleKitchen, onNavigateToKitchen)
                            QuickActionTile(Modifier.weight(1f), "Orders", "View all orders", Icons.Default.Work, AmberWarning, onNavigateToOrders)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickActionTile(Modifier.weight(1f), "New Order", "Start a new order", Icons.Default.ShoppingCart, GreenSuccess, onNavigateToPos)
                                QuickActionTile(Modifier.weight(1f), "Tables", "View all tables", Icons.Default.TableRestaurant, BlueInfo, onNavigateToTables)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                QuickActionTile(Modifier.weight(1f), "Kitchen", "Kitchen Display", Icons.Default.Restaurant, PurpleKitchen, onNavigateToKitchen)
                                QuickActionTile(Modifier.weight(1f), "Orders", "View all orders", Icons.Default.Work, AmberWarning, onNavigateToOrders)
                            }
                        }
                    }
                }
            }

            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val wide = maxWidth >= 900.dp
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TableStatusPanel(
                                modifier = Modifier.weight(1f),
                                tables = stats.tables,
                                onClick = onNavigateToTables
                            )
                            RecentOrdersPanel(
                                modifier = Modifier.weight(1f),
                                orders = stats.recentOrders,
                                currencySymbol = settings.currencySymbol,
                                isLoading = isLoading,
                                expanded = recentOrdersExpanded,
                                onToggleExpanded = { recentOrdersExpanded = !recentOrdersExpanded },
                                onViewAll = onNavigateToOrders
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            TableStatusPanel(
                                modifier = Modifier.fillMaxWidth(),
                                tables = stats.tables,
                                onClick = onNavigateToTables
                            )
                            RecentOrdersPanel(
                                modifier = Modifier.fillMaxWidth(),
                                orders = stats.recentOrders,
                                currencySymbol = settings.currencySymbol,
                                isLoading = isLoading,
                                expanded = recentOrdersExpanded,
                                onToggleExpanded = { recentOrdersExpanded = !recentOrdersExpanded },
                                onViewAll = onNavigateToOrders
                            )
                        }
                    }
                }
            }

            if (stats.alerts.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    BadgedBox(badge = { Badge(containerColor = RedError) { Text(stats.alerts.size.toString()) } }) {
                                        Icon(Icons.Default.Notifications, null, Modifier.size(22.dp), tint = AmberWarning)
                                    }
                                    Text("Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                IconButton(onClick = { alertsExpanded = !alertsExpanded }, modifier = Modifier.size(34.dp)) {
                                    Icon(if (alertsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                }
                            }
                            if (alertsExpanded) {
                                stats.alerts.forEach { alert ->
                                    AlertRow(alert = alert, onAction = { onAlertAction(alert.actionRoute) })
                                }
                            }
                        }
                    }
                }
            }

            if (stats.weeklyRevenue.isNotEmpty() || stats.topProducts.isNotEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (stats.weeklyRevenue.isNotEmpty()) {
                            WeeklyRevenueChart(
                                modifier = Modifier.fillMaxWidth(),
                                sales = stats.weeklyRevenue,
                                purchases = stats.weeklyPurchases,
                                expenses = stats.weeklyExpenses,
                                currencySymbol = settings.currencySymbol
                            )
                        }
                        if (stats.topProducts.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Top Products Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    stats.topProducts.take(5).forEachIndexed { idx, product ->
                                        TopProductRow(rank = idx + 1, product = product, currencySymbol = settings.currencySymbol)
                                        if (idx < stats.topProducts.take(5).lastIndex) HorizontalDivider(thickness = 0.5.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (stats.todayReservations > 0 || stats.lowStockCount > 0) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (stats.todayReservations > 0) {
                            NoticePill(
                                modifier = Modifier.weight(1f),
                                text = "${stats.todayReservations} reservation${if (stats.todayReservations == 1) "" else "s"} today",
                                icon = Icons.Default.EventNote,
                                color = BlueInfo,
                                onClick = onNavigateToReservations
                            )
                        }
                        if (stats.lowStockCount > 0) {
                            NoticePill(
                                modifier = Modifier.weight(1f),
                                text = "${stats.lowStockCount} low stock item${if (stats.lowStockCount == 1) "" else "s"}",
                                icon = Icons.Default.Warning,
                                color = RedError,
                                onClick = { showLowStockSheet = true }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLowStockSheet) {
        val stockAlerts = stats.alerts.filter { it.type == "LOW_STOCK" }
        ModalBottomSheet(onDismissRequest = { showLowStockSheet = false }) {
            Column(
                Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, null, tint = RedError, modifier = Modifier.size(22.dp))
                    Text("Low Stock Items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
                if (stockAlerts.isEmpty()) {
                    Text("${stats.lowStockCount} item(s) below minimum stock level.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    stockAlerts.forEach { alert ->
                        Card(colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.06f)),
                             border = androidx.compose.foundation.BorderStroke(1.dp, RedError.copy(alpha = 0.3f))) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(alert.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = RedError)
                                Text(alert.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (alert.actionLabel.isNotBlank()) {
                                    TextButton(
                                        onClick = { showLowStockSheet = false; onNavigateToInventory() },
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text(alert.actionLabel, style = MaterialTheme.typography.labelMedium) }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { showLowStockSheet = false; onNavigateToInventory() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = RedError)
                ) {
                    Icon(Icons.Default.Inventory, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Go to Raw Materials")
                }
            }
        }
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Sign Out") },
            text  = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(onClick = { session.logout(); onLogout() }) { Text("Sign Out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DashboardHeader(
    userName: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onLogout: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(
                shape = CircleShape,
                color = GreenSuccess.copy(alpha = 0.12f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(26.dp))
                }
            }
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Meal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = GreenSuccess)
                    Text("Flow", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
                }
                Text("Welcome, $userName", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.size(52.dp)
            ) {
                IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                modifier = Modifier.size(52.dp)
            ) {
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun DashboardKpiCard(
    modifier: Modifier,
    title: String,
    value: String,
    detail: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier.height(132.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, color.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ShiftStatusCard(shiftCode: String) {
    Surface(
        color = GreenSuccess.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.AccessTime, null, tint = GreenSuccess, modifier = Modifier.size(20.dp))
            Text("Shift $shiftCode open", style = MaterialTheme.typography.labelLarge, color = GreenSuccess, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QuickActionTile(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(104.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.96f), color.copy(alpha = 0.82f)))),
            contentAlignment = Alignment.CenterStart
        ) {
            val compact = maxWidth < 190.dp
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 10.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(if (compact) 42.dp else 56.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(if (compact) 21.dp else 26.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = if (compact) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable
private fun TodaySummaryPanel(
    modifier: Modifier,
    cashSales: Double,
    cardSales: Double,
    onlineSales: Double,
    orders: Int,
    customers: Int,
    currencySymbol: String,
    lowStockCount: Int,
    onLowStock: () -> Unit
) {
    PanelCard(modifier.heightIn(min = 320.dp)) {
        Text("Today's Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        SummaryRow(Icons.Default.AccountBalanceWallet, "Cash Sales", cashSales.coerceAtLeast(0.0).formatCurrency(currencySymbol), GreenSuccess)
        SummaryRow(Icons.Default.CreditCard, "Card Sales", cardSales.coerceAtLeast(0.0).formatCurrency(currencySymbol), BlueInfo)
        SummaryRow(Icons.Default.Language, "Online Sales", onlineSales.coerceAtLeast(0.0).formatCurrency(currencySymbol), PurpleKitchen)
        SummaryRow(Icons.Default.ReceiptLong, "Orders", orders.toString(), AmberWarning)
        SummaryRow(Icons.Default.People, "Customers", customers.toString(), BlueInfo)
        if (lowStockCount > 0) {
            TextButton(onClick = onLowStock, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Warning, null, Modifier.size(18.dp), tint = RedError)
                Spacer(Modifier.width(6.dp))
                Text("$lowStockCount low stock", color = RedError, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SummaryRow(icon: ImageVector, label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(7.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}

@Composable
private fun TableStatusPanel(
    modifier: Modifier,
    tables: List<com.fastpos.android.data.models.RestaurantTable>,
    onClick: () -> Unit
) {
    val displayTotal = tables.size
    PanelCard(modifier.heightIn(min = if (displayTotal == 0) 180.dp else 220.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Table Status", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusDot(GreenSuccess, "Available")
                StatusDot(RedError, "Occupied")
            }
        }
        if (displayTotal == 0) {
            Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                Text("No active tables.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val columns = displayTotal.coerceAtMost(4)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tables.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { table ->
                            TableStatusTile(
                                modifier = Modifier.weight(1f),
                                tableName = table.tableName.ifBlank { "Table ${table.tableId}" },
                                occupied = table.tableStatus.equals("Occupied", ignoreCase = true),
                                onClick = onClick
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TableStatusTile(modifier: Modifier, tableName: String, occupied: Boolean, onClick: () -> Unit) {
    val color = if (occupied) RedError else GreenSuccess
    Card(
        onClick = onClick,
        modifier = modifier.height(76.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.TableRestaurant, null, tint = color, modifier = Modifier.size(25.dp))
            Text(tableName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
private fun RecentOrdersPanel(
    modifier: Modifier,
    orders: List<Order>,
    currencySymbol: String,
    isLoading: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onViewAll: () -> Unit
) {
    PanelCard(if (expanded) modifier.heightIn(min = 320.dp) else modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recent Orders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (orders.isNotEmpty()) {
                    Badge(containerColor = BlueInfo) {
                        Text(orders.size.toString())
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (expanded) {
                    TextButton(onClick = onViewAll, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("View All", fontWeight = FontWeight.Bold)
                    }
                }
                IconButton(onClick = onToggleExpanded, modifier = Modifier.size(34.dp)) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
        }
        if (expanded) {
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (orders.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                    Text("No orders today.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                orders.take(4).forEachIndexed { index, order ->
                    RecentOrderCompactRow(order = order, currencySymbol = currencySymbol)
                    if (index < orders.take(4).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), thickness = 0.7.dp)
                }
            }
        }
    }
}

@Composable
private fun RecentOrderCompactRow(order: Order, currencySymbol: String) {
    val color = when {
        order.orderType.contains("Dine", ignoreCase = true) -> BlueInfo
        order.orderType.contains("Delivery", ignoreCase = true) -> PurpleKitchen
        order.orderType.contains("Take", ignoreCase = true) -> GreenSuccess
        else -> AmberWarning
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f), modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (order.tableName != null) Icons.Default.TableRestaurant else Icons.Default.Work, null, tint = color, modifier = Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(order.orderNo.ifBlank { "#${order.orderId}" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            Text(order.tableName ?: order.orderType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(order.createdAt.formatDateTime(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(order.grandTotal.formatCurrency(currencySymbol), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = GreenSuccess)
        }
    }
}

@Composable
private fun NoticePill(
    modifier: Modifier,
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = color)
            Text(text, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun KpiCard(
    modifier:      Modifier,
    label:         String,
    value:         String,
    icon:          ImageVector,
    color:         Color,
    subLabel:      String? = null,
    subLabelColor: Color   = Color.Unspecified
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            if (subLabel != null) {
                Text(
                    subLabel,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = subLabelColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun AlertRow(alert: DashboardAlert, onAction: () -> Unit) {
    val (bg, icon, tint) = when (alert.severity) {
        "Error"   -> Triple(RedError.copy(0.1f),    Icons.Default.Error,       RedError)
        "Warning" -> Triple(AmberWarning.copy(0.1f), Icons.Default.Warning,    AmberWarning)
        else      -> Triple(BlueInfo.copy(0.1f),    Icons.Default.Info,        BlueInfo)
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = tint)
            Column(Modifier.weight(1f)) {
                Text(alert.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = tint)
                if (alert.message.isNotBlank())
                    Text(alert.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (alert.actionLabel.isNotBlank()) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = tint)
                ) {
                    Text(alert.actionLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ActionButton(modifier: Modifier, label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun OrderTypeChip(modifier: Modifier, label: String, count: Int, color: Color) {
    Surface(
        modifier = modifier,
        color    = color.copy(alpha = 0.1f),
        shape    = MaterialTheme.shapes.small,
        border   = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
private fun TopProductRow(rank: Int, product: TopProduct, currencySymbol: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    rank.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            product.productName,
            style    = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            "×${"%.1f".format(product.quantity)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            product.revenue.formatCurrency(currencySymbol),
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color      = GreenSuccess
        )
    }
}

@Composable
private fun WeeklyRevenueChart(
    modifier: Modifier = Modifier,
    sales:          List<Pair<String, Double>>,
    purchases:      List<Pair<String, Double>>,
    expenses:       List<Pair<String, Double>>,
    currencySymbol: String
) {
    val allValues = (sales + purchases + expenses).map { it.second }
    val maxVal    = allValues.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val hasPurchases = purchases.any { it.second > 0 }
    val hasExpenses  = expenses.any { it.second > 0 }

    Card(
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Last 7 Days", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(MaterialTheme.colorScheme.primary, "Sales")
                if (hasPurchases) LegendDot(BlueInfo,    "Purchases")
                if (hasExpenses)  LegendDot(RedError,    "Expenses")
            }
            val salesBarColor = MaterialTheme.colorScheme.primary
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                val n         = sales.size
                if (n == 0) return@Canvas
                val spacing   = size.width / n
                val seriesCount = 1 + (if (hasPurchases) 1 else 0) + (if (hasExpenses) 1 else 0)
                val totalBarWidth = spacing * 0.7f
                val barWidth  = totalBarWidth / seriesCount
                val maxHeight = size.height - 16f

                sales.forEachIndexed { i, (_, sv) ->
                    val groupLeft = i * spacing + (spacing - totalBarWidth) / 2
                    var seriesIdx = 0

                    // Sales bar (orange)
                    val sh = if (maxVal > 0) (sv / maxVal * maxHeight).toFloat() else 0f
                    drawRoundRect(
                        color        = salesBarColor,
                        topLeft      = Offset(groupLeft + seriesIdx * barWidth, size.height - sh),
                        size         = Size(barWidth - 2f, sh.coerceAtLeast(2f)),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                    seriesIdx++

                    if (hasPurchases) {
                        val pv = purchases.getOrNull(i)?.second ?: 0.0
                        val ph = if (maxVal > 0) (pv / maxVal * maxHeight).toFloat() else 0f
                        drawRoundRect(
                            color        = BlueInfo,
                            topLeft      = Offset(groupLeft + seriesIdx * barWidth, size.height - ph),
                            size         = Size(barWidth - 2f, ph.coerceAtLeast(2f)),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                        seriesIdx++
                    }

                    if (hasExpenses) {
                        val ev = expenses.getOrNull(i)?.second ?: 0.0
                        val eh = if (maxVal > 0) (ev / maxVal * maxHeight).toFloat() else 0f
                        drawRoundRect(
                            color        = RedError,
                            topLeft      = Offset(groupLeft + seriesIdx * barWidth, size.height - eh),
                            size         = Size(barWidth - 2f, eh.coerceAtLeast(2f)),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                sales.forEach { (label, _) ->
                    Text(
                        label,
                        style     = MaterialTheme.typography.labelSmall,
                        fontSize  = 10.sp,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier  = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            val total = sales.sumOf { it.second }
            Text(
                "Sales Total: ${total.formatCurrency(currencySymbol)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, shape = MaterialTheme.shapes.extraSmall))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecentOrderRow(order: Order, currencySymbol: String) {
    val statusColor = Color(order.orderStatus.orderStatusColor())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(order.orderNo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${order.orderType} • ${order.createdAt.formatDateTime()}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(order.grandTotal.formatCurrency(currencySymbol), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Badge(containerColor = statusColor) {
                    Text(order.orderStatus, style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}
