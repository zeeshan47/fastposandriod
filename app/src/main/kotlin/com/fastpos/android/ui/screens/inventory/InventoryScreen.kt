@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.inventory

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.StockItem
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatCurrency
import com.fastpos.android.viewmodels.InventoryViewModel

@Composable
fun InventoryScreen(
    onNavigateBack: () -> Unit,
    vm: InventoryViewModel = hiltViewModel()
) {
    val items      by vm.items.collectAsState()
    val isLoading  by vm.isLoading.collectAsState()
    val message    by vm.message.collectAsState()
    val search     by vm.search.collectAsState()
    val filter     by vm.filter.collectAsState()
    val settings   by vm.session.settings.collectAsState()
    val snack      = remember { SnackbarHostState() }

    var adjustTarget by remember { mutableStateOf<StockItem?>(null) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    val outOfStock = items.count { it.currentStock <= 0 }
    val lowStock   = items.count { it.currentStock > 0 && it.minimumStock > 0 && it.currentStock <= it.minimumStock }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Inventory")
                            if (!isLoading) Text(
                                "${items.size} items · $outOfStock out · $lowStock low",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                    actions = {
                        IconButton(onClick = vm::printStockReport) { Icon(Icons.Default.Print, null) }
                        IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, null) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->

        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search
            OutlinedTextField(
                value = search, onValueChange = vm::setSearch,
                label = { Text("Search products…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { vm.setSearch("") }) { Icon(Icons.Default.Clear, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            // Filter chips + value summary
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("All", "Low Stock", "Out of Stock").forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick  = { vm.setFilter(f) },
                        label    = { Text(f, style = MaterialTheme.typography.labelMedium) }
                    )
                }
                val totalValue = items.sumOf { it.currentStock * it.salePrice }
                if (totalValue > 0 && !isLoading) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Value: ${totalValue.formatCurrency(settings.currencySymbol)}",
                        style     = MaterialTheme.typography.labelSmall,
                        color     = GreenSuccess,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Inventory, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (filter == "All" && search.isBlank()) "No stock-managed products.\nEnable stock tracking on products first."
                            else "No items match the current filter.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.productId }) { item ->
                    StockCard(item = item, onAdjust = { adjustTarget = item })
                }
            }
        }
    }

    adjustTarget?.let { item ->
        StockAdjustDialog(
            item      = item,
            onAdd     = { qty, rem -> vm.addStock(item.productId, qty, rem); adjustTarget = null },
            onRemove  = { qty, rem -> vm.removeStock(item.productId, qty, rem); adjustTarget = null },
            onDismiss = { adjustTarget = null }
        )
    }
}

@Composable
private fun StockCard(item: StockItem, onAdjust: () -> Unit) {
    val stockColor = when {
        item.currentStock <= 0  -> RedError
        item.currentStock <= 5  -> AmberWarning
        else                    -> GreenSuccess
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                Box(
                    Modifier.size(12.dp).background(
                        try { Color(android.graphics.Color.parseColor(item.categoryColor)) } catch (_: Exception) { MaterialTheme.colorScheme.primary },
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                )
                Column {
                    Text(item.productName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(item.categoryName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "%.1f".format(item.currentStock),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = stockColor
                    )
                    Text(
                        when {
                            item.currentStock <= 0                       -> "Out of stock"
                            item.currentStock <= item.minimumStock       -> "Low stock"
                            else                                         -> "In stock"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = stockColor
                    )
                    Text(
                        "Min: ${"%.1f".format(item.minimumStock)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onAdjust) {
                    Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun StockAdjustDialog(
    item: StockItem,
    onAdd: (Double, String) -> Unit,
    onRemove: (Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode    by remember { mutableStateOf("Add") }
    var amount  by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.productName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "Current: ${"%.1f".format(item.currentStock)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Min: ${"%.1f".format(item.minimumStock)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Add", "Remove").forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick  = { mode = m; amount = ""; remarks = "" },
                            label    = { Text(m) }
                        )
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Quantity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = remarks, onValueChange = { remarks = it },
                    label = { Text("Remarks (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val qty = amount.toDoubleOrNull() ?: return@Button
                when (mode) {
                    "Add"    -> onAdd(qty, remarks)
                    "Remove" -> onRemove(qty, remarks)
                }
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
