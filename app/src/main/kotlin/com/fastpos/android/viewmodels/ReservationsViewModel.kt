package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.Reservation
import com.fastpos.android.data.models.RestaurantTable
import com.fastpos.android.data.repositories.ReservationRepository
import com.fastpos.android.data.repositories.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReservationsViewModel @Inject constructor(
    private val repo:      ReservationRepository,
    private val orderRepo: OrderRepository
) : ViewModel() {

    val filterModes = listOf("Upcoming", "By Date", "All")
    val statusOptions = listOf("Confirmed", "Seated", "Cancelled", "NoShow")

    private val _reservations = MutableStateFlow<List<Reservation>>(emptyList())
    private val _tables       = MutableStateFlow<List<RestaurantTable>>(emptyList())
    private val _filterMode   = MutableStateFlow("Upcoming")
    private val _filterDate   = MutableStateFlow<java.util.Date>(java.util.Date())
    private val _message      = MutableStateFlow<String?>(null)
    private val _loading      = MutableStateFlow(false)

    // form state
    private val _formId       = MutableStateFlow(0)
    private val _formName     = MutableStateFlow("")
    private val _formPhone    = MutableStateFlow("")
    private val _formParty    = MutableStateFlow("2")
    private val _formDate     = MutableStateFlow<java.util.Date>(java.util.Date())
    private val _formTime     = MutableStateFlow("19:00")
    private val _formTableId  = MutableStateFlow<Int?>(null)
    private val _formStatus   = MutableStateFlow("Confirmed")
    private val _formNotes    = MutableStateFlow("")

    val reservations: StateFlow<List<Reservation>>     = _reservations.asStateFlow()
    val tables:       StateFlow<List<RestaurantTable>> = _tables.asStateFlow()
    val filterMode:   StateFlow<String>                = _filterMode.asStateFlow()
    val filterDate:   StateFlow<java.util.Date>        = _filterDate.asStateFlow()
    val message:      StateFlow<String?>               = _message.asStateFlow()
    val loading:      StateFlow<Boolean>               = _loading.asStateFlow()
    val formId:       StateFlow<Int>                   = _formId.asStateFlow()
    val formName:     StateFlow<String>                = _formName.asStateFlow()
    val formPhone:    StateFlow<String>                = _formPhone.asStateFlow()
    val formParty:    StateFlow<String>                = _formParty.asStateFlow()
    val formDate:     StateFlow<java.util.Date>        = _formDate.asStateFlow()
    val formTime:     StateFlow<String>                = _formTime.asStateFlow()
    val formTableId:  StateFlow<Int?>                  = _formTableId.asStateFlow()
    val formStatus:   StateFlow<String>                = _formStatus.asStateFlow()
    val formNotes:    StateFlow<String>                = _formNotes.asStateFlow()

    val isEditing: Boolean get() = _formId.value > 0

    init {
        viewModelScope.launch {
            runCatching { repo.initSchema() }
            loadTables()
            load()
        }
    }

    private fun loadTables() = viewModelScope.launch {
        runCatching { _tables.value = orderRepo.getTables() }
    }

    fun setFilterMode(mode: String) { _filterMode.value = mode; load() }
    fun setFilterDate(d: java.util.Date) { _filterDate.value = d; if (_filterMode.value == "By Date") load() }

    fun load() = viewModelScope.launch {
        _loading.value = true
        runCatching {
            _reservations.value = when (_filterMode.value) {
                "By Date"  -> repo.getByDate(_filterDate.value)
                "All"      -> repo.getAll()
                else       -> repo.getUpcoming()
            }
        }.onFailure { _message.value = "Load failed: ${it.message}" }
        _loading.value = false
    }

    fun selectForEdit(r: Reservation) {
        _formId.value     = r.reservationId
        _formName.value   = r.customerName
        _formPhone.value  = r.phone
        _formParty.value  = r.partySize.toString()
        _formDate.value   = r.reservationDate
        _formTime.value   = r.reservationTime
        _formTableId.value = r.tableId
        _formStatus.value = r.status
        _formNotes.value  = r.notes
    }

    fun clearForm() {
        _formId.value = 0; _formName.value = ""; _formPhone.value = ""
        _formParty.value = "2"; _formDate.value = java.util.Date(); _formTime.value = "19:00"
        _formTableId.value = null; _formStatus.value = "Confirmed"; _formNotes.value = ""
    }

    fun setFormName(v: String)    { _formName.value = v }
    fun setFormPhone(v: String)   { _formPhone.value = v }
    fun setFormParty(v: String)   { _formParty.value = v }
    fun setFormDate(d: java.util.Date) { _formDate.value = d }
    fun setFormTime(v: String)    { _formTime.value = v }
    fun setFormTableId(id: Int?)  { _formTableId.value = id }
    fun setFormStatus(v: String)  { _formStatus.value = v }
    fun setFormNotes(v: String)   { _formNotes.value = v }

    fun save() = viewModelScope.launch {
        if (_formName.value.isBlank()) { _message.value = "Customer name required"; return@launch }
        if (_formTime.value.isBlank()) { _message.value = "Time required"; return@launch }
        _loading.value = true
        runCatching {
            val r = Reservation(
                reservationId   = _formId.value,
                customerName    = _formName.value.trim(),
                phone           = _formPhone.value.trim(),
                partySize       = _formParty.value.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                reservationDate = _formDate.value,
                reservationTime = _formTime.value.trim(),
                tableId         = _formTableId.value,
                status          = _formStatus.value,
                notes           = _formNotes.value.trim()
            )
            repo.save(r)
            clearForm()
            load()
            _message.value = if (isEditing) "Reservation updated" else "Reservation saved"
        }.onFailure { _message.value = "Error: ${it.message}" }
        _loading.value = false
    }

    fun delete(reservationId: Int) = viewModelScope.launch {
        runCatching {
            repo.delete(reservationId)
            _reservations.value = _reservations.value.filter { it.reservationId != reservationId }
            clearForm()
            _message.value = "Deleted"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun updateStatus(reservationId: Int, status: String) = viewModelScope.launch {
        runCatching {
            repo.updateStatus(reservationId, status)
            _reservations.value = _reservations.value.map {
                if (it.reservationId == reservationId) it.copy(status = status) else it
            }
        }
    }

    fun clearMessage() { _message.value = null }
}
