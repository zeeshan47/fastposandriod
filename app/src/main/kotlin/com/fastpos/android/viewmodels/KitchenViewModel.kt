package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.KitchenTicket
import com.fastpos.android.data.repositories.KitchenRepository
import com.fastpos.android.data.repositories.SettingsRepository
import com.fastpos.android.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

const val STATION_ALL = ""

@HiltViewModel
class KitchenViewModel @Inject constructor(
    private val kitchenRepo:  KitchenRepository,
    private val settingsRepo: SettingsRepository,
    private val prefs:        PreferencesManager
) : ViewModel() {

    private val _allTickets      = MutableStateFlow<List<KitchenTicket>>(emptyList())
    private val _selectedStation = MutableStateFlow(STATION_ALL)
    private val _isLoading       = MutableStateFlow(false)
    private val _error           = MutableStateFlow<String?>(null)

    private val _urgentMinutes   = MutableStateFlow(15)
    val urgentMinutes: StateFlow<Int> = _urgentMinutes

    private val _newTicketAlert  = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val newTicketAlert: Flow<Unit> = _newTicketAlert

    private var knownTicketIds  = emptySet<Int>()
    private var isFirstLoad     = true

    /** All unique non-empty printer stations seen across loaded tickets. */
    val stations: StateFlow<List<String>> get() = _stations
    private val _stations = MutableStateFlow<List<String>>(emptyList())

    val selectedStation: StateFlow<String> = _selectedStation

    /** Tickets filtered to the selected station; items of other stations are stripped out. */
    val tickets: StateFlow<List<KitchenTicket>> get() = _tickets
    private val _tickets = MutableStateFlow<List<KitchenTicket>>(emptyList())

    val isLoading: StateFlow<Boolean> = _isLoading
    val error:     StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            try { _urgentMinutes.value = settingsRepo.getSettings().kitchenUrgentMinutes } catch (_: Exception) {}
        }
        viewModelScope.launch {
            _selectedStation.value = prefs.kitchenStation.first()
        }
        viewModelScope.launch {
            combine(_allTickets, _selectedStation) { all, station ->
                applyFilter(all, station)
            }.collect { _tickets.value = it }
        }
        viewModelScope.launch {
            _allTickets.collect { all ->
                _stations.value = all
                    .flatMap { it.items }
                    .map { it.printerName }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
            }
        }
        loadTickets()
        startPolling()
    }

    fun loadTickets() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                val fetched = kitchenRepo.getActiveTickets()
                checkForNewTickets(fetched)
                _allTickets.value = mergeInMemoryCompletions(fetched)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load kitchen tickets."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun checkForNewTickets(fetched: List<KitchenTicket>) {
        val fetchedIds = fetched.map { it.ticketId }.toSet()
        if (!isFirstLoad && fetchedIds.any { it !in knownTicketIds }) {
            _newTicketAlert.tryEmit(Unit)
        }
        isFirstLoad    = false
        knownTicketIds = fetchedIds
    }

    fun setStation(station: String) {
        _selectedStation.value = station
        viewModelScope.launch { prefs.saveKitchenStation(station) }
    }

    fun markItemReady(kitchenTicketItemId: Int) {
        // Toggle immediately for instant UI feedback, matching WPF Done↔Cooking behaviour
        _allTickets.value = _allTickets.value.map { ticket ->
            ticket.copy(items = ticket.items.map { item ->
                if (item.itemId == kitchenTicketItemId)
                    item.copy(status = if (item.status == "Completed") "Pending" else "Completed")
                else item
            })
        }
        viewModelScope.launch {
            try {
                kitchenRepo.markItemReady(kitchenTicketItemId)
                // For Android items reload from DB to confirm; for WPF-sourced items the
                // polling loop will pick up the DB change on the next tick.
                if (kitchenTicketItemId > 0) loadTickets()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun markTicketComplete(ticketId: Int) {
        viewModelScope.launch {
            try {
                kitchenRepo.markTicketComplete(ticketId)
                loadTickets()
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private fun applyFilter(all: List<KitchenTicket>, station: String): List<KitchenTicket> {
        if (station == STATION_ALL) return all
        return all.mapNotNull { ticket ->
            val filtered = ticket.items.filter { it.printerName == station || it.printerName.isBlank() }
            if (filtered.isEmpty()) null else ticket.copy(items = filtered)
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(5_000L)
                try {
                    val fetched = kitchenRepo.getActiveTickets()
                    checkForNewTickets(fetched)
                    _allTickets.value = mergeInMemoryCompletions(fetched)
                } catch (e: Exception) { _error.value = e.message }
            }
        }
    }

    // Preserve in-memory "Completed" status for WPF-sourced items (negative IDs) that have
    // no DB row — polling would otherwise reset their checkmarks on every refresh.
    private fun mergeInMemoryCompletions(fetched: List<KitchenTicket>): List<KitchenTicket> {
        val completedIds = _allTickets.value
            .flatMap { it.items }
            .filter { it.itemId < 0 && it.status == "Completed" }
            .map { it.itemId }
            .toSet()
        if (completedIds.isEmpty()) return fetched
        return fetched.map { ticket ->
            ticket.copy(items = ticket.items.map { item ->
                if (item.itemId in completedIds) item.copy(status = "Completed") else item
            })
        }
    }
}
