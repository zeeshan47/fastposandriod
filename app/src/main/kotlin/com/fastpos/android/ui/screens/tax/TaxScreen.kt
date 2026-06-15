@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.tax

import androidx.compose.foundation.BorderStroke
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
import com.fastpos.android.data.models.TaxRate
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.TaxViewModel

@Composable
fun TaxScreen(
    onNavigateBack: () -> Unit,
    vm: TaxViewModel = hiltViewModel()
) {
    val taxes   by vm.taxes.collectAsState()
    val loading by vm.loading.collectAsState()
    val message by vm.message.collectAsState()
    val snack   = remember { SnackbarHostState() }
    var showDialog  by remember { mutableStateOf(false) }
    var editTarget  by remember { mutableStateOf<TaxRate?>(null) }
    var deleteTarget by remember { mutableStateOf<TaxRate?>(null) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tax Management") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editTarget = null; showDialog = true }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, null, tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (taxes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Percent, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No tax rates yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(taxes, key = { it.taxId }) { tax ->
                    TaxCard(tax = tax, onEdit = { editTarget = tax; showDialog = true }, onDelete = { deleteTarget = tax })
                }
            }
        }
    }

    if (showDialog) {
        TaxDialog(
            initial   = editTarget,
            onDismiss = { showDialog = false },
            onSave    = { name, pct, active ->
                if (editTarget != null) vm.updateTax(editTarget!!.taxId, name, pct, active)
                else vm.addTax(name, pct)
                showDialog = false
            }
        )
    }

    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Tax") },
            text  = { Text("Delete ${t.taxName} (${t.taxPercent}%)? It cannot be deleted if assigned to products.") },
            confirmButton = { TextButton(onClick = { vm.deleteTax(t.taxId); deleteTarget = null }) { Text("Delete", color = RedError) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TaxCard(tax: TaxRate, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border  = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("${tax.taxPercent.toInt()}%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tax.taxName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("${tax.taxPercent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!tax.isActive) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)) {
                    Text("Inactive", Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onEdit)   { Icon(Icons.Default.Edit,   null, tint = AmberWarning) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = RedError) }
        }
    }
}

@Composable
private fun TaxDialog(
    initial:  TaxRate?,
    onDismiss: () -> Unit,
    onSave:   (String, Double, Boolean) -> Unit
) {
    var name     by remember { mutableStateOf(initial?.taxName ?: "") }
    var percent  by remember { mutableStateOf(initial?.taxPercent?.toString() ?: "") }
    var isActive by remember { mutableStateOf(initial?.isActive ?: true) }
    var nameErr  by remember { mutableStateOf(false) }
    var pctErr   by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Tax Rate" else "Edit Tax Rate") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it; nameErr = false },
                    label = { Text("Tax Name *") }, isError = nameErr, modifier = Modifier.fillMaxWidth(),
                    supportingText = if (nameErr) ({ Text("Required") }) else null)
                OutlinedTextField(value = percent, onValueChange = { percent = it; pctErr = false },
                    label = { Text("Rate (%) *") }, isError = pctErr,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = if (pctErr) ({ Text("0–100") }) else null)
                if (initial != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Active"); Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val pct = percent.toDoubleOrNull()
                if (name.isBlank()) { nameErr = true; return@Button }
                if (pct == null || pct < 0 || pct > 100) { pctErr = true; return@Button }
                onSave(name.trim(), pct, isActive)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
