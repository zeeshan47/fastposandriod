package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.CompanyBalance
import com.fastpos.android.data.models.DeliveryCompany
import com.fastpos.android.data.models.DeliverySettlement
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.repositories.DeliveryRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DeliveryViewModel @Inject constructor(
    private val repo: DeliveryRepository,
    val session: SessionManager
) : ViewModel() {

    private val _companies            = MutableStateFlow<List<DeliveryCompany>>(emptyList())
    private val _settlements          = MutableStateFlow<List<DeliverySettlement>>(emptyList())
    private val _companyBalances      = MutableStateFlow<List<CompanyBalance>>(emptyList())
    private val _pendingBalance       = MutableStateFlow(0.0)
    private val _message              = MutableStateFlow<String?>(null)
    private val _loading              = MutableStateFlow(false)
    private val _unassignedOrders     = MutableStateFlow<List<Order>>(emptyList())

    // Company form
    private val _formCompanyId   = MutableStateFlow(0)
    private val _formName        = MutableStateFlow("")
    private val _formCommission  = MutableStateFlow("")
    private val _formIsActive    = MutableStateFlow(true)

    // Settlement form
    private val _settlementCompanyId  = MutableStateFlow(0)
    private val _settlementDate       = MutableStateFlow<Date>(Date())
    private val _settlementAmount     = MutableStateFlow("")
    private val _settlementNotes      = MutableStateFlow("")
    private val _editingSettlementId  = MutableStateFlow(0)

    // Settlement filter
    private val _filterFrom      = MutableStateFlow<Date>(thirtyDaysAgo())
    private val _filterTo        = MutableStateFlow<Date>(Date())
    private val _filterCompanyId = MutableStateFlow(0)

    val companies:        StateFlow<List<DeliveryCompany>>    = _companies.asStateFlow()
    val settlements:      StateFlow<List<DeliverySettlement>> = _settlements.asStateFlow()
    val companyBalances:  StateFlow<List<CompanyBalance>>     = _companyBalances.asStateFlow()
    val pendingBalance:   StateFlow<Double>                   = _pendingBalance.asStateFlow()
    val message:          StateFlow<String?>                  = _message.asStateFlow()
    val loading:          StateFlow<Boolean>                  = _loading.asStateFlow()
    val unassignedOrders: StateFlow<List<Order>>              = _unassignedOrders.asStateFlow()

    val formCompanyId:  StateFlow<Int>     = _formCompanyId.asStateFlow()
    val formName:       StateFlow<String>  = _formName.asStateFlow()
    val formCommission: StateFlow<String>  = _formCommission.asStateFlow()
    val formIsActive:   StateFlow<Boolean> = _formIsActive.asStateFlow()

    val settlementCompanyId:  StateFlow<Int>     = _settlementCompanyId.asStateFlow()
    val settlementDate:       StateFlow<Date>    = _settlementDate.asStateFlow()
    val settlementAmount:     StateFlow<String>  = _settlementAmount.asStateFlow()
    val settlementNotes:      StateFlow<String>  = _settlementNotes.asStateFlow()
    val editingSettlementId:  StateFlow<Int>     = _editingSettlementId.asStateFlow()

    val filterFrom:      StateFlow<Date> = _filterFrom.asStateFlow()
    val filterTo:        StateFlow<Date> = _filterTo.asStateFlow()
    val filterCompanyId: StateFlow<Int>  = _filterCompanyId.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { repo.initSchema() }
            loadCompanies()
            loadSettlements()
            loadUnassignedOrders()
        }
    }

    fun loadCompanies() = viewModelScope.launch {
        _loading.value = true
        runCatching {
            _companies.value = repo.getAllCompanies()
            _companyBalances.value = repo.getCompanyBalances(_companies.value)
        }.onFailure { _message.value = "Load failed: ${it.message}" }
        _loading.value = false
    }

    fun loadSettlements() = viewModelScope.launch {
        runCatching {
            _settlements.value = repo.getSettlements(
                fromDate  = _filterFrom.value,
                toDate    = _filterTo.value,
                companyId = _filterCompanyId.value.takeIf { it > 0 }
            )
        }.onFailure { _message.value = "Load failed: ${it.message}" }
    }

    fun loadUnassignedOrders() = viewModelScope.launch {
        runCatching { _unassignedOrders.value = repo.getUnassignedDeliveryOrders() }
    }

    fun assignDeliveryCompany(orderId: Int, company: DeliveryCompany) = viewModelScope.launch {
        runCatching {
            repo.assignDeliveryCompany(orderId, company)
            _unassignedOrders.value = _unassignedOrders.value.filter { it.orderId != orderId }
            loadCompanies()
            _message.value = "Company assigned"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    // Company form
    fun selectCompanyForEdit(c: DeliveryCompany) {
        _formCompanyId.value  = c.companyId
        _formName.value       = c.companyName
        _formCommission.value = c.commissionPercent.toString()
        _formIsActive.value   = c.isActive
    }

    fun clearCompanyForm() {
        _formCompanyId.value = 0; _formName.value = ""; _formCommission.value = ""; _formIsActive.value = true
    }

    fun setFormName(v: String)       { _formName.value = v }
    fun setFormCommission(v: String) { _formCommission.value = v }
    fun setFormIsActive(v: Boolean)  { _formIsActive.value = v }

    fun saveCompany() = viewModelScope.launch {
        val name = _formName.value.trim()
        if (name.isBlank()) { _message.value = "Company name required"; return@launch }
        val commission = _formCommission.value.toDoubleOrNull() ?: 0.0
        if (commission < 0 || commission > 100) { _message.value = "Commission must be 0–100"; return@launch }
        runCatching {
            repo.saveCompany(DeliveryCompany(
                companyId         = _formCompanyId.value,
                companyName       = name,
                commissionPercent = commission,
                isActive          = _formIsActive.value
            ))
            clearCompanyForm()
            loadCompanies()
            _message.value = "Saved"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun deleteCompany(companyId: Int) = viewModelScope.launch {
        if (companyId == 1) { _message.value = "Own Rider cannot be deleted"; return@launch }
        runCatching {
            val hardDeleted = repo.deleteCompany(companyId)
            _message.value = if (hardDeleted) "Deleted" else "In use — deactivated instead"
            loadCompanies()
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    // Settlement form
    fun setSettlementCompany(id: Int) {
        _settlementCompanyId.value = id
        if (id > 0) viewModelScope.launch {
            val company = _companies.value.firstOrNull { it.companyId == id }
            if (company != null) {
                runCatching { _pendingBalance.value = repo.getPendingBalance(company) }
            }
        }
    }

    fun setSettlementDate(d: Date)   { _settlementDate.value = d }
    fun setSettlementAmount(v: String) { _settlementAmount.value = v }
    fun setSettlementNotes(v: String)  { _settlementNotes.value = v }

    fun clearSettlementForm() {
        _settlementCompanyId.value = 0; _settlementDate.value = Date()
        _settlementAmount.value = ""; _settlementNotes.value = ""
        _pendingBalance.value = 0.0; _editingSettlementId.value = 0
    }

    fun loadSettlementForEdit(s: DeliverySettlement) {
        _editingSettlementId.value  = s.settlementId
        _settlementCompanyId.value  = s.deliveryCompanyId
        _settlementDate.value       = s.settlementDate
        _settlementAmount.value     = "%.2f".format(s.amountReceived)
        _settlementNotes.value      = s.notes
    }

    fun saveSettlement() = viewModelScope.launch {
        if (_settlementCompanyId.value <= 0) { _message.value = "Select a company"; return@launch }
        val amt = _settlementAmount.value.toDoubleOrNull()
        if (amt == null || amt <= 0) { _message.value = "Enter a valid amount"; return@launch }
        val companyName = _companies.value.firstOrNull { it.companyId == _settlementCompanyId.value }?.companyName ?: ""
        runCatching {
            repo.saveSettlement(
                DeliverySettlement(
                    settlementId      = _editingSettlementId.value,
                    deliveryCompanyId = _settlementCompanyId.value,
                    settlementDate    = _settlementDate.value,
                    amountReceived    = amt,
                    notes             = _settlementNotes.value.trim()
                ),
                companyName = companyName
            )
            _message.value = if (_editingSettlementId.value == 0) "Settlement saved" else "Settlement updated"
            clearSettlementForm()
            loadSettlements()
            loadCompanies()
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun deleteSettlement(settlementId: Int) = viewModelScope.launch {
        runCatching {
            repo.deleteSettlement(settlementId)
            _settlements.value = _settlements.value.filter { it.settlementId != settlementId }
            loadCompanies()
            _message.value = "Deleted"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    // Filter
    fun setFilterFrom(d: Date)        { _filterFrom.value = d }
    fun setFilterTo(d: Date)          { _filterTo.value = d }
    fun setFilterCompany(id: Int)     { _filterCompanyId.value = id }
    fun applyFilter()                 { loadSettlements() }

    fun clearMessage() { _message.value = null }

    private fun thirtyDaysAgo(): Date = Calendar.getInstance()
        .apply { add(Calendar.DAY_OF_YEAR, -30) }.time
}
