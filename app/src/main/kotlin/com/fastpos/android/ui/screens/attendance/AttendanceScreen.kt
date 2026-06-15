@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.attendance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.AttendanceRecord
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.*

private val DATE_FMT = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())

@Composable
fun AttendanceScreen(
    onNavigateBack: () -> Unit,
    vm: AttendanceViewModel = hiltViewModel()
) {
    val attendance   by vm.attendance.collectAsState()
    val selectedDate by vm.selectedDate.collectAsState()
    val isLoading    by vm.isLoading.collectAsState()
    val message      by vm.message.collectAsState()
    val snack        = remember { SnackbarHostState() }

    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = { vm.loadAttendance() }) { Icon(Icons.Default.Refresh, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        snackbarHost = { AppSnackbarHost(snack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Date selector bar
            Card(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val cal = Calendar.getInstance().apply { time = selectedDate; add(Calendar.DAY_OF_MONTH, -1) }
                        vm.setDate(cal.time)
                    }) { Icon(Icons.Default.ChevronLeft, null) }

                    TextButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(DATE_FMT.format(selectedDate), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }

                    IconButton(onClick = {
                        val cal = Calendar.getInstance().apply { time = selectedDate; add(Calendar.DAY_OF_MONTH, 1) }
                        vm.setDate(cal.time)
                    }) { Icon(Icons.Default.ChevronRight, null) }
                }
            }

            // Summary chips
            val checkedIn    = attendance.count { it.checkInTime != null && it.checkOutTime == null }
            val completed    = attendance.count { it.checkInTime != null && it.checkOutTime != null }
            val absent       = attendance.count { it.checkInTime == null }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryChip("In $checkedIn",   AmberWarning)
                SummaryChip("Done $completed", GreenSuccess)
                SummaryChip("Absent $absent",  RedError)
            }

            Spacer(Modifier.height(4.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            if (attendance.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No active employees found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attendance, key = { it.employeeId }) { record ->
                    AttendanceCard(
                        record   = record,
                        onCheckIn  = { vm.checkIn(record.employeeId) },
                        onCheckOut = { vm.checkOut(record.attendanceId) },
                        onClear    = { vm.removeAttendance(record.employeeId) }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.time)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { vm.setDate(Date(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
private fun SummaryChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun AttendanceCard(
    record: AttendanceRecord,
    onCheckIn:  () -> Unit,
    onCheckOut: () -> Unit,
    onClear:    () -> Unit
) {
    val statusColor = when (record.statusLabel) {
        "Completed"   -> GreenSuccess
        "In Progress" -> AmberWarning
        else          -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (record.checkInTime == null) MaterialTheme.colorScheme.surfaceVariant
                             else statusColor.copy(alpha = 0.07f)
        ),
        border = if (record.checkInTime != null)
            androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
        else null
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            record.fullName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(record.fullName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(record.roleName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        record.statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // Time info row when checked in
            if (record.checkInTime != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Check In", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(record.checkInLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = GreenSuccess)
                    }
                    Column {
                        Text("Check Out", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(record.checkOutLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            color = if (record.checkOutTime != null) GreenSuccess else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (record.checkOutTime != null) {
                        Column {
                            Text("Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(record.durationLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Action buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.canCheckIn) {
                    Button(
                        onClick = onCheckIn,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GreenSuccess)
                    ) {
                        Icon(Icons.Default.Login, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Check In", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (record.canCheckOut) {
                    Button(
                        onClick = onCheckOut,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Logout, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Check Out", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (record.checkInTime != null) {
                    IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, null, Modifier.size(16.dp), tint = RedError.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
