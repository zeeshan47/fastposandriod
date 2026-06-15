@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.kitchen

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fastpos.android.data.models.KitchenTicket
import com.fastpos.android.data.models.KitchenTicketItem
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.KitchenViewModel
import com.fastpos.android.viewmodels.STATION_ALL
import kotlinx.coroutines.delay

@Composable
fun KitchenScreen(
    onNavigateBack: () -> Unit,
    vm: KitchenViewModel = hiltViewModel()
) {
    val context         = LocalContext.current
    val tickets         by vm.tickets.collectAsState()
    val stations        by vm.stations.collectAsState()
    val selectedStation by vm.selectedStation.collectAsState()
    val isLoading       by vm.isLoading.collectAsState()
    val error           by vm.error.collectAsState()
    val urgentMinutes   by vm.urgentMinutes.collectAsState()

    // Ticks every 30 s so age badges stay current between DB polls
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(30_000L); now = System.currentTimeMillis() }
    }

    // New-ticket alert: beep + vibrate when a fresh ticket arrives during polling
    LaunchedEffect(Unit) {
        vm.newTicketAlert.collect {
            alertKitchen(context)
        }
    }

    // Reload tickets every time the screen is resumed (e.g. after placing an order on POS)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.loadTickets()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val urgent  = tickets.count { (now - it.createdAt.time) / 60000 >= urgentMinutes }
    val late    = tickets.count { val m = (now - it.createdAt.time) / 60000; m in (urgentMinutes - 5) until urgentMinutes }
    val onTrack = tickets.count { (now - it.createdAt.time) / 60000 < (urgentMinutes - 5) }
    val sorted  = remember(tickets) { tickets.sortedBy { it.createdAt } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Kitchen, null, tint = PurpleKitchen)
                        Spacer(Modifier.width(8.dp))
                        Text("Kitchen Display")
                        if (tickets.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            Badge(containerColor = PurpleKitchen) { Text(tickets.size.toString()) }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = vm::loadTickets) { Icon(Icons.Default.Refresh, "Refresh") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                tickets.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.DoneAll, null, Modifier.size(80.dp), tint = GreenSuccess)
                        Spacer(Modifier.height(16.dp))
                        Text("All orders ready!", style = MaterialTheme.typography.titleLarge, color = GreenSuccess)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = vm::loadTickets) { Text("Refresh") }
                    }
                }

                else -> Column {
                    // Urgency stats strip
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UrgencyChip("Urgent",   urgent,  RedError,     Modifier.weight(1f))
                        UrgencyChip("Late",     late,    AmberWarning, Modifier.weight(1f))
                        UrgencyChip("On Track", onTrack, GreenSuccess, Modifier.weight(1f))
                    }

                    // Station filter chips — only shown when multiple stations exist
                    if (stations.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedStation == STATION_ALL,
                                    onClick  = { vm.setStation(STATION_ALL) },
                                    label    = { Text("All Stations") },
                                    leadingIcon = if (selectedStation == STATION_ALL) {
                                        { Icon(Icons.Default.Done, null, Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                            items(stations) { station ->
                                FilterChip(
                                    selected = selectedStation == station,
                                    onClick  = { vm.setStation(station) },
                                    label    = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Kitchen, null, Modifier.size(14.dp))
                                            Text(station)
                                        }
                                    },
                                    leadingIcon = if (selectedStation == station) {
                                        { Icon(Icons.Default.Done, null, Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(300.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sorted) { ticket ->
                            KitchenTicketCard(ticket, now, urgentMinutes, vm::markItemReady, vm::markTicketComplete)
                        }
                    }
                }
            }

            error?.let {
                Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(it) }
            }
        }
    }
}

@Composable
private fun UrgencyChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    val active = count > 0
    Surface(
        modifier = modifier,
        shape    = MaterialTheme.shapes.small,
        color    = if (active) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        border   = BorderStroke(1.dp, if (active) color else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp).fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text       = count.toString(),
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color      = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text  = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun KitchenTicketCard(
    ticket:        KitchenTicket,
    now:           Long,
    urgentMinutes: Int,
    onItemReady:   (Int) -> Unit,
    onTicketDone:  (Int) -> Unit
) {
    val ageMinutes = ((now - ticket.createdAt.time) / 60000).toInt()
    val urgency = when {
        ageMinutes >= urgentMinutes          -> RedError
        ageMinutes >= urgentMinutes - 5      -> AmberWarning
        else                                 -> GreenSuccess
    }

    Card(
        border = BorderStroke(2.dp, urgency),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(ticket.orderNo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = urgency)
                    Text("Token: ${ticket.tokenNo}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Badge(containerColor = urgency) {
                        Text("${ageMinutes}m", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        ticket.orderType + (ticket.tableName?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider()

            ticket.items.forEach { item -> KitchenItemRow(item, onItemReady) }

            HorizontalDivider()

            val allDone = ticket.items.all { it.status == "Completed" }
            Button(
                onClick  = { onTicketDone(ticket.ticketId) },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = if (allDone) GreenSuccess else urgency)
            ) {
                Icon(Icons.Default.CheckCircle, null)
                Spacer(Modifier.width(6.dp))
                Text(if (allDone) "Mark Ready" else "Complete All & Ready")
            }
        }
    }
}

@Composable
private fun KitchenItemRow(item: KitchenTicketItem, onReady: (Int) -> Unit) {
    val isDone = item.status == "Completed"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text       = "×${"%.0f".format(item.quantity)}  ${item.productName}",
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color      = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            item.modifiers?.let { mods ->
                if (mods.isNotBlank()) Text("+ $mods", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            item.notes?.let { n ->
                if (n.isNotBlank()) Text("Note: $n", style = MaterialTheme.typography.labelSmall, color = AmberWarning)
            }
        }
        IconButton(onClick = { onReady(item.itemId) }) {
            if (isDone) {
                Icon(Icons.Default.CheckCircle, "Undo", tint = GreenSuccess, modifier = Modifier.size(28.dp))
            } else {
                Icon(Icons.Default.RadioButtonUnchecked, "Mark ready", modifier = Modifier.size(28.dp))
            }
        }
    }
}

private fun alertKitchen(context: Context) {
    try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            .startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
    } catch (_: Exception) {}
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(VibratorManager::class.java)
            vibratorManager?.defaultVibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 150), -1)
            )
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            v?.vibrate(longArrayOf(0, 150, 80, 150), -1)
        }
    } catch (_: Exception) {}
}
