@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.accounts

import android.app.DatePickerDialog
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.data.models.AccountLedgerEntry
import com.fastpos.android.data.models.ChartOfAccount
import com.fastpos.android.data.models.TrialBalanceRow
import com.fastpos.android.ui.theme.*
import com.fastpos.android.viewmodels.AccountLedgerViewModel
import java.text.SimpleDateFormat
import java.util.*

private val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
private val numFmt  = java.text.DecimalFormat("#,##0.00")

@Composable
fun AccountLedgerScreen(
    onNavigateBack: () -> Unit,
    vm: AccountLedgerViewModel = hiltViewModel()
) {
    val accounts        by vm.accounts.collectAsState()
    val ledgerEntries   by vm.ledgerEntries.collectAsState()
    val trialBalance    by vm.trialBalance.collectAsState()
    val selectedAccount by vm.selectedAccount.collectAsState()
    val fromDate        by vm.fromDate.collectAsState()
    val toDate          by vm.toDate.collectAsState()
    val isLoading       by vm.isLoading.collectAsState()
    val message         by vm.message.collectAsState()
    val totalDebit      by vm.totalDebit.collectAsState()
    val totalCredit     by vm.totalCredit.collectAsState()

    val snackbar  = remember { SnackbarHostState() }
    val context   = LocalContext.current
    var tabIndex  by remember { mutableIntStateOf(0) }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    // Auto-load when tab changes
    LaunchedEffect(tabIndex) {
        if (tabIndex == 0) vm.loadLedger() else vm.loadTrialBalance()
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Accounts") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── Date filter bar ───────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { time = fromDate }
                        DatePickerDialog(context, { _, y, m, d ->
                            vm.setFromDate(Calendar.getInstance().apply {
                                set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                            }.time)
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                            .show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(dateFmt.format(fromDate), fontSize = 12.sp) }

                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { time = toDate }
                        DatePickerDialog(context, { _, y, m, d ->
                            vm.setToDate(Calendar.getInstance().apply {
                                set(y, m, d, 23, 59, 59); set(Calendar.MILLISECOND, 999)
                            }.time)
                        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                            .show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(dateFmt.format(toDate), fontSize = 12.sp) }

                OutlinedButton(onClick = { vm.filterToday(); if (tabIndex == 0) vm.loadLedger() else vm.loadTrialBalance() }) {
                    Text("Today", fontSize = 12.sp)
                }
                OutlinedButton(onClick = { vm.filterThisMonth(); if (tabIndex == 0) vm.loadLedger() else vm.loadTrialBalance() }) {
                    Text("Month", fontSize = 12.sp)
                }
            }

            // ── Tabs ──────────────────────────────────────────────────────────
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) {
                    Text("Account Ledger", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) {
                    Text("Trial Balance", modifier = Modifier.padding(12.dp))
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.padding(16.dp))
                }
            }

            when (tabIndex) {
                0 -> LedgerTab(accounts, selectedAccount, ledgerEntries, totalDebit, totalCredit, vm)
                1 -> TrialBalanceTab(trialBalance)
            }
        }
    }
}

@Composable
private fun LedgerTab(
    accounts: List<ChartOfAccount>,
    selected: ChartOfAccount?,
    entries:  List<AccountLedgerEntry>,
    totalDebit:  Double,
    totalCredit: Double,
    vm: AccountLedgerViewModel
) {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        // ── Account picker + Search ───────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selected?.displayName ?: "Select account…",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    accounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text(acc.displayName) },
                            onClick = { vm.setSelectedAccount(acc); expanded = false }
                        )
                    }
                }
            }

            Button(onClick = { vm.loadLedger() }) {
                Icon(Icons.Default.Search, contentDescription = null)
            }
        }

        // ── Totals row ────────────────────────────────────────────────────
        if (entries.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Debit: ${numFmt.format(totalDebit)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("Credit: ${numFmt.format(totalCredit)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                val net = totalDebit - totalCredit
                Text(
                    if (net >= 0) "Dr ${numFmt.format(net)}" else "Cr ${numFmt.format(-net)}",
                    fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
            }
            Divider()
        }

        // ── Header row ────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            LedgerHeaderCell("Date",    80.dp)
            LedgerHeaderCell("Type",    80.dp)
            LedgerHeaderCell("Ref#",    50.dp)
            LedgerHeaderCell("Narration", 180.dp)
            LedgerHeaderCell("Debit",   90.dp)
            LedgerHeaderCell("Credit",  90.dp)
            LedgerHeaderCell("Balance", 90.dp)
        }
        Divider()

        LazyColumn(Modifier.fillMaxSize()) {
            items(entries) { e ->
                LedgerRow(e)
                Divider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun LedgerRow(e: AccountLedgerEntry) {
    val timeFmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LedgerCell(timeFmt.format(e.entryDate),    80.dp)
        LedgerCell(e.referenceType,                80.dp)
        LedgerCell(e.referenceId?.toString() ?: "", 50.dp)
        LedgerCell(e.narration,                    180.dp)
        LedgerCell(if (e.debit  > 0) numFmt.format(e.debit)  else "", 90.dp)
        LedgerCell(if (e.credit > 0) numFmt.format(e.credit) else "", 90.dp)
        LedgerCell(
            if (e.balance >= 0) "Dr ${numFmt.format(e.balance)}" else "Cr ${numFmt.format(-e.balance)}",
            90.dp, bold = true
        )
    }
}

@Composable
private fun TrialBalanceTab(rows: List<TrialBalanceRow>) {
    Column(Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            LedgerHeaderCell("Code",    60.dp)
            LedgerHeaderCell("Account", 160.dp)
            LedgerHeaderCell("Type",    80.dp)
            LedgerHeaderCell("Debit",   90.dp)
            LedgerHeaderCell("Credit",  90.dp)
            LedgerHeaderCell("Balance", 100.dp)
        }
        Divider()

        // ── Totals ────────────────────────────────────────────────────────
        val grandDebit  = rows.sumOf { it.totalDebit }
        val grandCredit = rows.sumOf { it.totalCredit }
        if (rows.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Debit: ${numFmt.format(grandDebit)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Total Credit: ${numFmt.format(grandCredit)}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Divider()
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(rows) { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LedgerCell(r.accountCode, 60.dp)
                    LedgerCell(r.accountName, 160.dp)
                    LedgerCell(r.accountType, 80.dp)
                    LedgerCell(if (r.totalDebit  > 0) numFmt.format(r.totalDebit)  else "", 90.dp)
                    LedgerCell(if (r.totalCredit > 0) numFmt.format(r.totalCredit) else "", 90.dp)
                    LedgerCell(r.balanceLabel, 100.dp, bold = true)
                }
                Divider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun LedgerHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        modifier   = Modifier.width(width).padding(horizontal = 2.dp),
        fontWeight = FontWeight.Bold,
        fontSize   = 12.sp
    )
}

@Composable
private fun LedgerCell(text: String, width: androidx.compose.ui.unit.Dp, bold: Boolean = false) {
    Text(
        text,
        modifier   = Modifier.width(width).padding(horizontal = 2.dp),
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        fontSize   = 12.sp,
        maxLines   = 1
    )
}
