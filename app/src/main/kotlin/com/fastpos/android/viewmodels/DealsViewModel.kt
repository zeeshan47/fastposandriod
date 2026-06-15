package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.*
import com.fastpos.android.data.repositories.DealRepository
import com.fastpos.android.data.repositories.ProductRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DealsViewModel @Inject constructor(
    private val dealRepo:    DealRepository,
    private val productRepo: ProductRepository,
    val session: SessionManager
) : ViewModel() {

    private val _deals      = MutableStateFlow<List<Deal>>(emptyList())
    private val _products   = MutableStateFlow<List<Product>>(emptyList())
    private val _dealItems  = MutableStateFlow<List<DealItem>>(emptyList())   // items for selected/editing deal
    private val _selected   = MutableStateFlow<Deal?>(null)
    private val _message    = MutableStateFlow<String?>(null)
    private val _loading    = MutableStateFlow(false)

    // form state
    private val _formName        = MutableStateFlow("")
    private val _formDesc        = MutableStateFlow("")
    private val _formPrice       = MutableStateFlow("")
    private val _formDiscount    = MutableStateFlow("")
    private val _formValidFrom   = MutableStateFlow<java.util.Date?>(null)
    private val _formValidTo     = MutableStateFlow<java.util.Date?>(null)
    private val _formIsActive    = MutableStateFlow(true)
    private val _formItems       = MutableStateFlow<List<DealItem>>(emptyList())
    private val _sizesForItem    = MutableStateFlow<List<ProductSize>>(emptyList())

    val deals:       StateFlow<List<Deal>>     = _deals.asStateFlow()
    val products:    StateFlow<List<Product>>  = _products.asStateFlow()
    val dealItems:   StateFlow<List<DealItem>> = _dealItems.asStateFlow()
    val selected:    StateFlow<Deal?>          = _selected.asStateFlow()
    val message:     StateFlow<String?>        = _message.asStateFlow()
    val loading:     StateFlow<Boolean>        = _loading.asStateFlow()
    val formName:    StateFlow<String>         = _formName.asStateFlow()
    val formDesc:    StateFlow<String>         = _formDesc.asStateFlow()
    val formPrice:   StateFlow<String>         = _formPrice.asStateFlow()
    val formDiscount:StateFlow<String>         = _formDiscount.asStateFlow()
    val formValidFrom: StateFlow<java.util.Date?> = _formValidFrom.asStateFlow()
    val formValidTo:   StateFlow<java.util.Date?> = _formValidTo.asStateFlow()
    val formIsActive:  StateFlow<Boolean>          = _formIsActive.asStateFlow()
    val formItems:     StateFlow<List<DealItem>>   = _formItems.asStateFlow()
    val sizesForItem:  StateFlow<List<ProductSize>> = _sizesForItem.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { dealRepo.initSchema() }
            loadDeals()
            loadProducts()
        }
    }

    fun loadDeals() = viewModelScope.launch {
        _loading.value = true
        runCatching { _deals.value = dealRepo.getAllDeals() }
            .onFailure { _message.value = "Load failed: ${it.message}" }
        _loading.value = false
    }

    private fun loadProducts() = viewModelScope.launch {
        runCatching { _products.value = productRepo.getProducts() }
    }

    fun selectDeal(deal: Deal?) {
        _selected.value = deal
        if (deal != null) {
            _formName.value     = deal.dealName
            _formDesc.value     = deal.description
            _formPrice.value    = deal.dealPrice.toString()
            _formDiscount.value = deal.discountPercent.toString()
            _formValidFrom.value = deal.validFrom
            _formValidTo.value   = deal.validTo
            _formIsActive.value  = deal.isActive
            viewModelScope.launch {
                runCatching { _formItems.value = dealRepo.getDealItems(deal.dealId) }
            }
        } else clearForm()
    }

    fun setFormName(v: String)    { _formName.value = v }
    fun setFormDesc(v: String)    { _formDesc.value = v }
    fun setFormPrice(v: String)   { _formPrice.value = v }
    fun setFormDiscount(v: String){ _formDiscount.value = v }
    fun setFormValidFrom(d: java.util.Date?) { _formValidFrom.value = d }
    fun setFormValidTo(d: java.util.Date?)   { _formValidTo.value = d }
    fun setFormIsActive(v: Boolean) { _formIsActive.value = v }

    fun loadSizesForItem(productId: Int) = viewModelScope.launch {
        _sizesForItem.value = emptyList()
        if (productId > 0) runCatching { _sizesForItem.value = productRepo.getProductSizes(productId) }
    }

    fun clearSizesForItem() { _sizesForItem.value = emptyList() }

    fun addFormItem(item: DealItem) { _formItems.value = _formItems.value + item }
    fun removeFormItem(idx: Int)    { _formItems.value = _formItems.value.toMutableList().also { it.removeAt(idx) } }

    fun clearForm() {
        _selected.value = null
        _formName.value = ""; _formDesc.value = ""; _formPrice.value = ""
        _formDiscount.value = ""; _formValidFrom.value = null; _formValidTo.value = null
        _formIsActive.value = true; _formItems.value = emptyList()
    }

    fun saveDeal() = viewModelScope.launch {
        val name = _formName.value.trim()
        if (name.isBlank()) { _message.value = "Deal name required"; return@launch }
        if (_formItems.value.isEmpty()) { _message.value = "Add at least one item"; return@launch }
        _loading.value = true
        runCatching {
            val deal = Deal(
                dealId          = _selected.value?.dealId ?: 0,
                dealName        = name,
                description     = _formDesc.value.trim(),
                dealPrice       = _formPrice.value.toDoubleOrNull() ?: 0.0,
                discountPercent = _formDiscount.value.toDoubleOrNull() ?: 0.0,
                validFrom       = _formValidFrom.value,
                validTo         = _formValidTo.value,
                isActive        = _formIsActive.value
            )
            dealRepo.saveDeal(deal, _formItems.value)
            clearForm()
            loadDeals()
            _message.value = "Deal saved"
        }.onFailure { _message.value = "Error: ${it.message}" }
        _loading.value = false
    }

    fun deleteDeal(dealId: Int) = viewModelScope.launch {
        runCatching {
            dealRepo.deleteDeal(dealId)
            _deals.value = _deals.value.filter { it.dealId != dealId }
            clearForm()
            _message.value = "Deal deleted"
        }.onFailure { _message.value = "Error: ${it.message}" }
    }

    fun clearMessage() { _message.value = null }
}
