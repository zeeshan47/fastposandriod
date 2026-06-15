@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.messagelog

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.MessageLog
import com.fastpos.android.ui.theme.*
import com.fastpos.android.utils.formatDateTime
import com.fastpos.android.viewmodels.MessageLogViewModel
import java.text.SimpleDateFormat
import java.util.*

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

@Composable
fun MessageLogScreen(
    onNavigateBack: () -> Unit,
    vm: MessageLogViewModel = hiltViewModel()
) {
    val logs            by vm.logs.collectAsState()
    val isLoading       by vm.isLoading.collectAsState()
    val message         by vm.message.collectAsState()
    val channels        by vm.channels.collectAsState()
    val failedToday     by vm.failedToday.collectAsState()
    val fromDate        by vm.fromDate.collectAsState()
    val toDate          by vm.toDate.collectAsState()
    val selectedChannel by vm.selectedChannel.collectAsState()
    val selectedStatus  by vm.selectedStatus.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val context  = LocalContext.current

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Message Log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    if (failedToday > 0) {
                        Surface(
                            color = RedError.copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "$failedToday failed today",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = RedError,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (logs.isNotEmpty()) {
                        IconButton(onClick = {
                            val csv = buildString {
                                appendLine("Date/Time,Channel,Phone,Order,Status,Message,Error")
                                logs.forEach { m ->
                                    appendLine("\"${m.createdAt.formatDateTime()}\",\"${m.channel}\",\"${m.recipientPhone}\",\"${m.orderNo}\",\"${m.status}\",\"${m.messageText.replace("\"","'")}\",\"${m.errorMessage.replace("\"","'")}\"")
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, csv)
                                putExtra(Intent.EXTRA_SUBJECT, "Message Log Export")
                            }
                            context.startActivity(Intent.createChooser(intent, "Export Message Log"))
                        }) {
                            Icon(Icons.Default.Share, "Export", tint = MaterialTheme.colorScheme.primary)
                        }
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
                color          = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp,
                modifier       = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
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

                    // Channel filter
                    var channelExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = channelExpanded, onExpandedChange = { channelExpanded = it }) {
                        OutlinedTextField(
                            value         = selectedChannel,
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Channel", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(channelExpanded) },
                            modifier      = Modifier.menuAnchor().width(160.dp),
                            textStyle     = MaterialTheme.typography.bodySmall,
                            singleLine    = true
                        )
                        ExposedDropdownMenu(expanded = channelExpanded, onDismissRequest = { channelExpanded = false }) {
                            channels.forEach { ch ->
                                DropdownMenuItem(
                                    text    = { Text(ch, style = MaterialTheme.typography.bodySmall) },
                                    onClick = { vm.setSelectedChannel(ch); channelExpanded = false }
                                )
                            }
                        }
                    }

                    // Status filter
                    var statusExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(expanded = statusExpanded, onExpandedChange = { statusExpanded = it }) {
                        OutlinedTextField(
                            value         = selectedStatus,
                            onValueChange = {},
                            readOnly      = true,
                            label         = { Text("Status", style = MaterialTheme.typography.labelSmall) },
                            trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) },
                            modifier      = Modifier.menuAnchor().width(150.dp),
                            textStyle     = MaterialTheme.typography.bodySmall,
                            singleLine    = true
                        )
                        ExposedDropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                            vm.statusOptions.forEach { st ->
                                DropdownMenuItem(
                                    text    = { Text(st, style = MaterialTheme.typography.bodySmall) },
                                    onClick = { vm.setSelectedStatus(st); statusExpanded = false }
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
                    ) { Text("Week", style = MaterialTheme.typography.bodySmall) }

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
                Text("Date / Time",  Modifier.width(140.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Channel",      Modifier.width(80.dp),  style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Phone",        Modifier.width(110.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Order",        Modifier.width(80.dp),  style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Status",       Modifier.width(70.dp),  style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Message",      Modifier.weight(1f),    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()

            // ── Log list ──────────────────────────────────────────────────────
            if (logs.isEmpty() && !isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Forum, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No messages found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Message Log records SMS/WhatsApp notifications sent to customers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(logs, key = { it.messageId }) { msg ->
                        MessageLogRow(msg)
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageLogRow(msg: MessageLog) {
    val isFailed = msg.status.equals("Failed", ignoreCase = true)
    val statusColor = if (isFailed) RedError else GreenSuccess

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            msg.createdAt.formatDateTime(),
            Modifier.width(140.dp),
            style = MaterialTheme.typography.bodySmall
        )

        // Channel badge
        Surface(
            color    = when (msg.channel.uppercase()) {
                "WHATSAPP" -> GreenSuccess.copy(alpha = 0.15f)
                "SMS"      -> BlueInfo.copy(alpha = 0.15f)
                else       -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            },
            shape    = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.width(80.dp)
        ) {
            Text(
                msg.channel.ifBlank { "—" },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style    = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = when (msg.channel.uppercase()) {
                    "WHATSAPP" -> GreenSuccess
                    "SMS"      -> BlueInfo
                    else       -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        Text(
            msg.recipientPhone.ifBlank { "—" },
            Modifier.width(110.dp),
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            msg.orderNo.ifBlank { "—" },
            Modifier.width(80.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Status badge
        Surface(
            color    = statusColor.copy(alpha = 0.15f),
            shape    = MaterialTheme.shapes.extraSmall,
            modifier = Modifier.width(70.dp)
        ) {
            Text(
                msg.status.ifBlank { "—" },
                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style      = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color      = statusColor
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                msg.messageText,
                style    = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
            if (isFailed && msg.errorMessage.isNotBlank()) {
                Text(
                    "Error: ${msg.errorMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = RedError
                )
            }
        }
    }
}
