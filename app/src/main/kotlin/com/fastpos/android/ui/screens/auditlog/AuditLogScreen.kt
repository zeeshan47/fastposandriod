@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.auditlog

import android.app.DatePickerDialog
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.AuditLogRow
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatDateTime
import com.fastpos.android.viewmodels.AuditLogViewModel
import java.text.SimpleDateFormat
import java.util.*

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun AuditLogScreen(
    onNavigateBack: () -> Unit,
    vm: AuditLogViewModel = hiltViewModel()
) {
    val logs           by vm.logs.collectAsState()
    val actions        by vm.actions.collectAsState()
    val tables         by vm.tables.collectAsState()
    val isLoading      by vm.isLoading.collectAsState()
    val message        by vm.message.collectAsState()
    val fromDate       by vm.fromDate.collectAsState()
    val toDate         by vm.toDate.collectAsState()
    val selectedAction by vm.selectedAction.collectAsState()
    val selectedTable  by vm.selectedTable.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val context  = LocalContext.current

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Audit Log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = {
                            val csv = buildString {
                                appendLine("Date/Time,User,Action,Table,RecordId,Machine")
                                logs.forEach { row ->
                                    appendLine("\"${row.loggedAt.formatDateTime()}\",\"${row.userName}\",\"${row.action}\",\"${row.tableName}\",\"${row.recordId ?: ""}\",\"${row.machineName}\"")
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, csv)
                                putExtra(Intent.EXTRA_SUBJECT, "Audit Log Export")
                            }
                            context.startActivity(Intent.createChooser(intent, "Export Audit Log"))
                        }) {
                            Icon(Icons.Default.Share, "Export CSV", tint = MaterialTheme.colorScheme.primary)
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "${logs.size} records",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (isLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // ── Filter bar ────────────────────────────────────────────────────
            Surface(
                color       = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    // From date
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply { time = fromDate }
                            DatePickerDialog(context, { _, y, m, d ->
                                vm.setFromDate(Calendar.getInstance().apply {
                                    set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                                }.time)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("From: ${dateFmt.format(fromDate)}", style = MaterialTheme.typography.bodySmall)
                    }

                    Text("—", style = MaterialTheme.typography.bodySmall)

                    // To date
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply { time = toDate }
                            DatePickerDialog(context, { _, y, m, d ->
                                vm.setToDate(Calendar.getInstance().apply {
                                    set(y, m, d, 23, 59, 59)
                                }.time)
                            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("To: ${dateFmt.format(toDate)}", style = MaterialTheme.typography.bodySmall)
                    }

                    // Action dropdown
                    var actionExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = actionExpanded, onExpandedChange = { actionExpanded = it }) {
                        OutlinedTextField(
                            value           = selectedAction,
                            onValueChange   = {},
                            readOnly        = true,
                            label           = { Text("Action", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon    = { ExposedDropdownMenuDefaults.TrailingIcon(actionExpanded) },
                            modifier        = Modifier.menuAnchor().width(160.dp),
                            textStyle       = MaterialTheme.typography.bodySmall,
                            singleLine      = true
                        )
                        ExposedDropdownMenu(expanded = actionExpanded, onDismissRequest = { actionExpanded = false }) {
                            actions.forEach { a ->
                                DropdownMenuItem(
                                    text    = { Text(a, style = MaterialTheme.typography.bodySmall) },
                                    onClick = { vm.setSelectedAction(a); actionExpanded = false }
                                )
                            }
                        }
                    }

                    // Table dropdown
                    var tableExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = tableExpanded, onExpandedChange = { tableExpanded = it }) {
                        OutlinedTextField(
                            value           = selectedTable,
                            onValueChange   = {},
                            readOnly        = true,
                            label           = { Text("Table", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon    = { ExposedDropdownMenuDefaults.TrailingIcon(tableExpanded) },
                            modifier        = Modifier.menuAnchor().width(180.dp),
                            textStyle       = MaterialTheme.typography.bodySmall,
                            singleLine      = true
                        )
                        ExposedDropdownMenu(expanded = tableExpanded, onDismissRequest = { tableExpanded = false }) {
                            tables.forEach { t ->
                                DropdownMenuItem(
                                    text    = { Text(t, style = MaterialTheme.typography.bodySmall) },
                                    onClick = { vm.setSelectedTable(t); tableExpanded = false }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { vm.search() },
                        colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Search", style = MaterialTheme.typography.bodySmall)
                    }

                    OutlinedButton(
                        onClick = { vm.filterToday() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("Today", style = MaterialTheme.typography.bodySmall) }

                    OutlinedButton(
                        onClick = { vm.filterThisWeek() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("This Week", style = MaterialTheme.typography.bodySmall) }

                    OutlinedButton(
                        onClick = { vm.clearFilters() },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) { Text("Clear", style = MaterialTheme.typography.bodySmall) }
                }
            }

            HorizontalDivider()

            // ── Column headers ────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Date / Time", Modifier.width(140.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("User",        Modifier.width(120.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Action",      Modifier.width(90.dp),  style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Table",       Modifier.width(130.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Rec ID",      Modifier.width(60.dp),  style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Machine",     Modifier.weight(1f),    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()

            // ── Log list ──────────────────────────────────────────────────────
            if (logs.isEmpty() && !isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No audit records found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(logs) { row ->
                        AuditLogRowItem(row = row)
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLogRowItem(row: AuditLogRow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            row.loggedAt.formatDateTime(),
            Modifier.width(140.dp),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            row.userName,
            Modifier.width(120.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )

        // Action badge
        val (badgeBg, badgeFg) = actionColors(row.action)
        Surface(
            color    = badgeBg,
            shape    = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.width(90.dp)
        ) {
            Text(
                row.action,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = badgeFg
            )
        }

        Text(
            row.tableName,
            Modifier.width(130.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            row.recordId?.toString() ?: "—",
            Modifier.width(60.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            row.machineName.ifBlank { "—" },
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun actionColors(action: String): Pair<Color, Color> = when (action.uppercase()) {
    "CREATE" -> GreenSuccess.copy(alpha = 0.15f) to GreenSuccess
    "UPDATE" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) to MaterialTheme.colorScheme.primary
    "DELETE" -> RedError.copy(alpha = 0.15f) to RedError
    "LOGIN"  -> AmberWarning.copy(alpha = 0.15f) to AmberWarning
    else     -> TealAccent.copy(alpha = 0.15f) to TealAccent
}
