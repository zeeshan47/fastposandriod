package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.models.*
import com.fastpos.android.data.repositories.CustomerRepository
import com.fastpos.android.data.repositories.DealRepository
import com.fastpos.android.data.repositories.DeliveryRepository
import com.fastpos.android.data.repositories.OfflineCacheRepository
import com.fastpos.android.data.repositories.OrderRepository
import com.fastpos.android.data.repositories.ProductRepository
import com.fastpos.android.data.repositories.ProductScheduleRepository
import com.fastpos.android.data.repositories.ShiftRepository
import com.fastpos.android.data.repositories.VoucherRepository
import com.fastpos.android.utils.ConnectivityMonitor
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.utils.NetworkPrinterHelper
import com.fastpos.android.utils.PosCartBridge
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KotPreviewGroup(
    val printerName: String,
    val ip: String,
    val port: Int,
    val paperType: String?,
    val items: List<CartItem>,
    val orderNo: String,
    val tokenNo: String,
    val orderType: String,
    val tableName: String,
    val waiterName: String,
    val notes: String,
    val customerName: String,
    val companyName: String,
    val logoData: ByteArray?
)

data class TakeawayTokenPreviewData(
    val orderNo:      String,
    val tokenNo:      String,
    val customerName: String?,
    val notes:        String?,
    val items:        List<CartItem>,
    val companyName:  String
)

sealed class PosUiEvent {
    data class OrderPlaced(val orderId: Int)    : PosUiEvent()
    data class OrderSaved(
        val tokenNo:   String,
        val orderNo:   String   = "",
        val orderType: String   = "",
        val tableName: String?  = null
    ) : PosUiEvent()  // Pay Later — no payment screen
    data object OrderQueued                     : PosUiEvent()
    data object OrderUpdated                    : PosUiEvent()
    data class OrderHeld(val tokenNo: String)   : PosUiEvent()
    data class ItemsAdded(val orderId: Int)     : PosUiEvent()
    data class Error(val message: String)       : PosUiEvent()
    data class Info(val message: String)        : PosUiEvent()
    data class SmsSend(val phone: String, val message: String)      : PosUiEvent()
    data class WhatsAppSend(val phone: String, val message: String) : PosUiEvent()
}

@HiltViewModel
class PosViewModel @Inject constructor(
    private val productRepo:    ProductRepository,
    private val orderRepo:      OrderRepository,
    private val customerRepo:   CustomerRepository,
    private val dealRepo:       DealRepository,
    private val voucherRepo:    VoucherRepository,
    private val scheduleRepo:   ProductScheduleRepository,
    private val deliveryRepo:   DeliveryRepository,
    private val shiftRepo:      ShiftRepository,
    private val prefs:         PreferencesManager,
    private val offlineCache: OfflineCacheRepository,
    private val connectivity: ConnectivityMonitor,
    private val bridge:       PosCartBridge,
    val session:              SessionManager
) : ViewModel() {

    // ── Catalog ──────────────────────────────────────────────────────────────
    private val _categories       = MutableStateFlow<List<Category>>(emptyList())
    private val _allProducts      = MutableStateFlow<List<Product>>(emptyList())
    private val _products         = MutableStateFlow<List<Product>>(emptyList())
    private val _searchQuery      = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<Int?>(null)
    private val _isLoadingCatalog = MutableStateFlow(false)

    val categories:       StateFlow<List<Category>> = _categories
    val products: StateFlow<List<Product>> = combine(_products, _searchQuery) { list, q ->
        if (q.isBlank()) list else list.filter {
            it.productName.contains(q, ignoreCase = true) ||
            (it.productCode.isNotBlank() && it.productCode.contains(q, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())
    val searchQuery:      StateFlow<String>          = _searchQuery
    val selectedCategory: StateFlow<Int?>            = _selectedCategory
    val isLoadingCatalog: StateFlow<Boolean>         = _isLoadingCatalog

    // ── Pinned / Favourites ───────────────────────────────────────────────────
    private val _pinnedIds = MutableStateFlow<Set<Int>>(emptySet())
    val pinnedIds: StateFlow<Set<Int>> = _pinnedIds

    val pinnedProducts: StateFlow<List<Product>> = combine(_allProducts, _pinnedIds) { all, ids ->
        all.filter { it.productId in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    // ── Cart ─────────────────────────────────────────────────────────────────
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart

    // ── Order config ─────────────────────────────────────────────────────────
    private val _orderType       = MutableStateFlow(session.settings.value.defaultOrderType.ifBlank { "Takeaway" })
    private val _tableId         = MutableStateFlow<Int?>(null)
    private val _tableName       = MutableStateFlow<String?>(null)
    private val _waiterId        = MutableStateFlow<Int?>(null)
    private val _discount        = MutableStateFlow(0.0)
    private val _notes           = MutableStateFlow("")
    private val _tables          = MutableStateFlow<List<RestaurantTable>>(emptyList())
    private val _waiters         = MutableStateFlow<List<Waiter>>(emptyList())
    private val _deliveryName            = MutableStateFlow("")
    private val _deliveryPhone           = MutableStateFlow("")
    private val _deliveryAddress         = MutableStateFlow("")
    private val _takeawayName            = MutableStateFlow("")
    private val _deliveryCompanies       = MutableStateFlow<List<DeliveryCompany>>(emptyList())
    private val _selectedDeliveryCompany = MutableStateFlow<DeliveryCompany?>(null)
    private val _deliveryCharge          = MutableStateFlow(0.0)
    private val _foundCustomerHint       = MutableStateFlow<String?>(null)

    val orderType:               StateFlow<String>                = _orderType
    val tableId:                 StateFlow<Int?>                  = _tableId
    val tableName:               StateFlow<String?>               = _tableName
    val waiterId:                StateFlow<Int?>                  = _waiterId
    val discount:                StateFlow<Double>                = _discount
    val notes:                   StateFlow<String>                = _notes
    val tables:                  StateFlow<List<RestaurantTable>> = _tables
    val waiters:                 StateFlow<List<Waiter>>          = _waiters
    val deliveryName:            StateFlow<String>                = _deliveryName
    val deliveryPhone:           StateFlow<String>                = _deliveryPhone
    val deliveryAddress:         StateFlow<String>                = _deliveryAddress
    val takeawayName:            StateFlow<String>                = _takeawayName
    val deliveryCompanies:       StateFlow<List<DeliveryCompany>> = _deliveryCompanies
    val selectedDeliveryCompany: StateFlow<DeliveryCompany?>      = _selectedDeliveryCompany
    val deliveryCharge:          StateFlow<Double>                = _deliveryCharge
    val foundCustomerHint:       StateFlow<String?>               = _foundCustomerHint

    // ── Sizes / Modifiers popup ───────────────────────────────────────────────
    private val _pendingProduct    = MutableStateFlow<Product?>(null)
    private val _productSizes      = MutableStateFlow<List<ProductSize>>(emptyList())
    private val _modifierGroups    = MutableStateFlow<List<ModifierGroup>>(emptyList())
    private val _showSizeModDialog = MutableStateFlow(false)

    val pendingProduct:    StateFlow<Product?>            = _pendingProduct
    val productSizes:      StateFlow<List<ProductSize>>   = _productSizes
    val modifierGroups:    StateFlow<List<ModifierGroup>> = _modifierGroups
    val showSizeModDialog: StateFlow<Boolean>             = _showSizeModDialog

    // ── Customer ──────────────────────────────────────────────────────────────
    private val _selectedCustomer    = MutableStateFlow<Customer?>(null)
    private val _customerResults     = MutableStateFlow<List<Customer>>(emptyList())
    private val _isSearchingCustomer = MutableStateFlow(false)

    val selectedCustomer:    StateFlow<Customer?>      = _selectedCustomer
    val customerResults:     StateFlow<List<Customer>> = _customerResults
    val isSearchingCustomer: StateFlow<Boolean>        = _isSearchingCustomer

    // ── Deals ────────────────────────────────────────────────────────────────────
    private val _deals        = MutableStateFlow<List<Deal>>(emptyList())
    private val _showingDeals = MutableStateFlow(false)
    val deals:        StateFlow<List<Deal>> = _deals
    val showingDeals: StateFlow<Boolean>    = _showingDeals

    // ── Voucher ───────────────────────────────────────────────────────────────
    private val _appliedVoucher      = MutableStateFlow<VoucherValidation?>(null)
    private val _voucherLoading      = MutableStateFlow(false)
    val appliedVoucher:  StateFlow<VoucherValidation?> = _appliedVoucher
    val voucherLoading:  StateFlow<Boolean>            = _voucherLoading

    // ── Add-to-order mode ─────────────────────────────────────────────────────
    private val _addToOrderId  = MutableStateFlow<Int?>(null)
    private val _addToOrderNo  = MutableStateFlow<String?>(null)
    val addToOrderNo: StateFlow<String?> = _addToOrderNo

    // ── Edit-order mode ───────────────────────────────────────────────────────
    private val _editOrderId  = MutableStateFlow<Int?>(null)
    private val _editOrderNo  = MutableStateFlow<String?>(null)
    val editOrderNo: StateFlow<String?> = _editOrderNo

    // ── Held orders ───────────────────────────────────────────────────────────
    private val _heldOrders = MutableStateFlow<List<com.fastpos.android.data.models.Order>>(emptyList())
    val heldOrders: StateFlow<List<com.fastpos.android.data.models.Order>> = _heldOrders

    // ── Placing order ─────────────────────────────────────────────────────────
    private val _isPlacingOrder = MutableStateFlow(false)
    private val _lastSavedCart  = MutableStateFlow<List<com.fastpos.android.data.models.CartItem>>(emptyList())
    val isPlacingOrder: StateFlow<Boolean>                                              = _isPlacingOrder
    val lastSavedCart:  StateFlow<List<com.fastpos.android.data.models.CartItem>>       = _lastSavedCart

    // ── Offline ───────────────────────────────────────────────────────────────
    val isOffline: StateFlow<Boolean> = connectivity.isOnline
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _pendingOfflineOrders = MutableStateFlow(0)
    val pendingOfflineOrders: StateFlow<Int> = _pendingOfflineOrders

    private val _event = MutableSharedFlow<PosUiEvent>()
    val event: SharedFlow<PosUiEvent> = _event

    // ── Print preview state (KOT + Takeaway token) ────────────────────────────
    private val _kotPreviewGroups = MutableStateFlow<List<KotPreviewGroup>>(emptyList())
    val kotPreviewGroups: StateFlow<List<KotPreviewGroup>> = _kotPreviewGroups
    private val _pendingOrderId   = MutableStateFlow<Int?>(null)
    private val _tokenPreview     = MutableStateFlow<TakeawayTokenPreviewData?>(null)
    val tokenPreview: StateFlow<TakeawayTokenPreviewData?> = _tokenPreview

    // ── Computed totals ───────────────────────────────────────────────────────
    val subTotal: StateFlow<Double> = _cart.map { it.sumOf { item -> item.lineTotal } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0.0)

    val taxAmount: StateFlow<Double> = combine(_cart, session.settings) { cart, settings ->
        if (settings.defaultTaxPercent == 0.0) 0.0
        else cart.sumOf { it.lineTotal } * settings.defaultTaxPercent / 100.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0.0)

    val serviceChargeAmount: StateFlow<Double> = combine(subTotal, session.settings) { sub, settings ->
        if (settings.serviceChargePercent == 0.0) 0.0
        else sub * settings.serviceChargePercent / 100.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0.0)

    val grandTotal: StateFlow<Double> = combine(
        combine(subTotal, taxAmount, _discount, session.settings) { sub, tax, disc, settings ->
            sub + tax + settings.serviceChargePercent * sub / 100.0 - disc
        },
        _deliveryCharge
    ) { base, delCharge -> base + delCharge }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), 0.0)

    init {
        checkResume()
        checkAddToOrder()
        checkEditOrder()
        loadCatalog()
        loadHeldOrders()
        refreshShift()
        viewModelScope.launch {
            _pendingOfflineOrders.value = offlineCache.pendingCount()
        }
        viewModelScope.launch {
            prefs.pinnedProductIds.collect { _pinnedIds.value = it }
        }
        // Sync pending orders + re-read shift whenever connection is restored
        viewModelScope.launch {
            connectivity.isOnline
                .drop(1)
                .distinctUntilChanged()
                .filter { it }
                .collect { syncPendingOrders(); refreshShift() }
        }
        // Poll held orders every 5 seconds so the list stays current
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                if (!isOffline.value) {
                    try { _heldOrders.value = orderRepo.getHeldOrders() } catch (_: Exception) {}
                }
            }
        }
    }

    fun refreshShift() {
        viewModelScope.launch {
            try {
                val userId = session.userId
                val shift = shiftRepo.getOpenShift(userId) ?: shiftRepo.getAnyOpenShift()
                session.setShift(shift)
            } catch (_: Exception) {}
        }
    }

    // ── Catalog ───────────────────────────────────────────────────────────────
    private var lastCatalogLoadMs = 0L

    fun refreshCatalog(minIntervalMs: Long = 30_000) {
        if (System.currentTimeMillis() - lastCatalogLoadMs > minIntervalMs) loadCatalog()
    }

    fun loadCatalog() {
        viewModelScope.launch {
            lastCatalogLoadMs = System.currentTimeMillis()
            _isLoadingCatalog.value = true
            try {
                _categories.value  = productRepo.getCategories()
                val allProds       = productRepo.getProducts()
                val unavailNow     = try { scheduleRepo.getUnavailableProductIds() } catch (_: Exception) { emptySet() }
                val filtered       = if (unavailNow.isEmpty()) allProds
                                     else allProds.map { p ->
                                         if (p.productId in unavailNow) p.copy(isAvailable = false) else p
                                     }
                _allProducts.value = filtered
                _products.value    = filtered
                _tables.value      = orderRepo.getTables()
                _waiters.value     = try { orderRepo.getWaiters() } catch (_: Exception) { emptyList() }
                _deals.value       = try { dealRepo.getAllDeals() } catch (_: Exception) { emptyList() }
                try {
                    _deliveryCompanies.value = deliveryRepo.getActiveCompanies()
                    if (_selectedDeliveryCompany.value == null)
                        _selectedDeliveryCompany.value = _deliveryCompanies.value.firstOrNull { it.companyId == 1 }
                            ?: _deliveryCompanies.value.firstOrNull()
                } catch (_: Exception) {}
                connectivity.setOnline()
                offlineCache.cacheCategories(_categories.value)
                offlineCache.cacheProducts(allProds)
            } catch (e: Exception) {
                if (isConnectionError(e)) {
                    connectivity.setOffline()
                    val cachedCats = offlineCache.getCachedCategories()
                    if (cachedCats.isNotEmpty()) {
                        _categories.value  = cachedCats
                        _products.value    = offlineCache.getCachedProducts()
                        _allProducts.value = _products.value
                    } else {
                        _event.emit(PosUiEvent.Error("No connection. No cached menu available."))
                    }
                } else {
                    _event.emit(PosUiEvent.Error("Failed to load products: ${e.message}"))
                }
            } finally {
                _isLoadingCatalog.value = false
            }
        }
    }

    fun toggleDealsView() {
        _showingDeals.value = !_showingDeals.value
        if (_showingDeals.value) _selectedCategory.value = null
    }

    fun addDealToCart(deal: Deal) {
        viewModelScope.launch {
            try {
                val items = dealRepo.getDealItems(deal.dealId).filter { !it.isOptional }
                if (items.isEmpty()) {
                    _event.emit(PosUiEvent.Error("Deal '${deal.dealName}' has no items configured."))
                    return@launch
                }
                val regularTotal = items.sumOf { it.salePrice * it.quantity }
                items.forEach { item ->
                    val discAmt = when {
                        deal.dealPrice > 0 && regularTotal > 0 && deal.dealPrice < regularTotal ->
                            item.salePrice * item.quantity * (1.0 - deal.dealPrice / regularTotal)
                        deal.discountPercent > 0 ->
                            item.salePrice * item.quantity * deal.discountPercent / 100.0
                        else -> 0.0
                    }
                    _cart.value = _cart.value + CartItem(
                        productId      = item.productId,
                        productName    = item.productName,
                        sizeId         = item.sizeId,
                        sizeName       = item.sizeName,
                        unitPrice      = item.salePrice,
                        quantity       = item.quantity,
                        discountAmount = discAmt,
                        notes          = "Deal: ${deal.dealName}"
                    )
                }
            } catch (e: Exception) {
                _event.emit(PosUiEvent.Error("Failed to add deal: ${e.message}"))
            }
        }
    }

    fun selectCategory(categoryId: Int?) {
        _selectedCategory.value = categoryId
        _showingDeals.value = false
        viewModelScope.launch {
            try {
                val prods      = productRepo.getProducts(categoryId)
                val unavailNow = try { scheduleRepo.getUnavailableProductIds() } catch (_: Exception) { emptySet() }
                _products.value = if (unavailNow.isEmpty()) prods
                                  else prods.map { p -> if (p.productId in unavailNow) p.copy(isAvailable = false) else p }
                connectivity.setOnline()
            } catch (_: Exception) {
                _products.value = offlineCache.getCachedProducts(categoryId)
            }
        }
    }

    fun retryConnection() { loadCatalog(); refreshShift() }

    // ── Product selection ─────────────────────────────────────────────────────
    fun onProductTapped(product: Product) {
        viewModelScope.launch {
            if (!product.isAvailable) {
                _event.emit(PosUiEvent.Error("'${product.productName}' is currently unavailable."))
                return@launch
            }
            // Sizes/modifiers unavailable offline — add directly
            if (isOffline.value) {
                addToCart(product, null, null, emptyList())
                return@launch
            }
            try {
                val sizes     = productRepo.getProductSizes(product.productId)
                val modifiers = productRepo.getModifierGroups(product.productId)
                if (sizes.isEmpty() && modifiers.isEmpty()) {
                    addToCart(product, null, null, emptyList())
                } else {
                    _pendingProduct.value    = product
                    _productSizes.value      = sizes
                    _modifierGroups.value    = modifiers
                    _showSizeModDialog.value = true
                }
            } catch (_: Exception) {
                // Fallback: if sizes/modifiers queries fail, add item directly
                addToCart(product, null, null, emptyList())
            }
        }
    }

    fun confirmSizeAndModifiers(size: ProductSize?, modifiers: List<SelectedModifier>) {
        val product = _pendingProduct.value ?: return
        addToCart(product, size?.sizeId, size?.sizeName, modifiers, size?.price ?: product.salePrice)
        _showSizeModDialog.value = false
        _pendingProduct.value    = null
    }

    fun dismissSizeModDialog() {
        _showSizeModDialog.value = false
        _pendingProduct.value    = null
    }

    // ── Cart operations ───────────────────────────────────────────────────────
    private fun addToCart(
        product:   Product,
        sizeId:    Int?,
        sizeName:  String?,
        modifiers: List<SelectedModifier>,
        price:     Double = product.salePrice
    ) {
        val existing = _cart.value.firstOrNull {
            it.productId == product.productId &&
            it.sizeId == sizeId &&
            it.selectedModifiers.map { m -> m.modifierId }.sorted() ==
            modifiers.map { m -> m.modifierId }.sorted()
        }
        _cart.value = if (existing != null) {
            _cart.value.map { if (it.cartId == existing.cartId) it.copy(quantity = it.quantity + 1) else it }
        } else {
            _cart.value + CartItem(
                productId                = product.productId,
                productName              = product.productName,
                sizeId                   = sizeId,
                sizeName                 = sizeName,
                unitPrice                = price,
                quantity                 = 1,
                selectedModifiers        = modifiers,
                printerName              = product.printerName,
                kitchenPrinterId         = product.kitchenPrinterId,
                productNameOtherLanguage = product.productNameOtherLanguage
            )
        }
        val label = if (sizeName != null) "${product.productName} ($sizeName)" else product.productName
        viewModelScope.launch { _event.emit(PosUiEvent.Info("$label added")) }
    }

    fun incrementItem(cartId: String) {
        _cart.value = _cart.value.map { if (it.cartId == cartId) it.copy(quantity = it.quantity + 1) else it }
    }

    fun decrementItem(cartId: String) {
        _cart.value = _cart.value.mapNotNull {
            if (it.cartId == cartId) { if (it.quantity > 1) it.copy(quantity = it.quantity - 1) else null }
            else it
        }
    }

    fun removeItem(cartId: String) {
        _cart.value = _cart.value.filter { it.cartId != cartId }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _selectedCustomer.value = null
        _tableId.value = null
        _tableName.value = null
        _waiterId.value = null
        _orderType.value = session.settings.value.defaultOrderType.ifBlank { "Takeaway" }
        _discount.value = 0.0
        _notes.value = ""
        _deliveryName.value = ""; _deliveryPhone.value = ""; _deliveryAddress.value = ""; _foundCustomerHint.value = null; _takeawayName.value = ""
        _selectedDeliveryCompany.value = _deliveryCompanies.value.firstOrNull { it.companyId == 1 }
            ?: _deliveryCompanies.value.firstOrNull()
        _deliveryCharge.value = 0.0
        _searchQuery.value = ""
        _appliedVoucher.value = null
    }

    fun setItemNotes(cartId: String, notes: String) {
        _cart.value = _cart.value.map { if (it.cartId == cartId) it.copy(notes = notes) else it }
    }

    fun setItemQuantity(cartId: String, qty: Int) {
        if (qty <= 0) {
            _cart.value = _cart.value.filter { it.cartId != cartId }
        } else {
            _cart.value = _cart.value.map { if (it.cartId == cartId) it.copy(quantity = qty) else it }
        }
    }

    fun setItemDiscount(cartId: String, discountAmount: Double) {
        _cart.value = _cart.value.map { item ->
            if (item.cartId == cartId) item.copy(discountAmount = discountAmount.coerceAtLeast(0.0)) else item
        }
    }

    fun setItemPrice(cartId: String, price: Double) {
        if (price <= 0) return
        _cart.value = _cart.value.map { item ->
            if (item.cartId == cartId) item.copy(unitPrice = price) else item
        }
    }

    // ── Order config ──────────────────────────────────────────────────────────
    fun setSearchQuery(q: String) { _searchQuery.value = q }

    /** Called when user presses Done/Enter on the search bar. If the current
     *  filtered list has exactly one product, adds it to cart and clears the query. */
    fun submitSearchQuery() {
        val matches = products.value
        if (matches.size == 1) {
            onProductTapped(matches[0])
            _searchQuery.value = ""
        }
    }

    fun setOrderType(type: String) {
        _orderType.value = type
        if (type != "DineIn") { _tableId.value = null; _tableName.value = null; _waiterId.value = null }
        if (type != "Delivery") {
            _deliveryName.value = ""; _deliveryPhone.value = ""; _deliveryAddress.value = ""; _foundCustomerHint.value = null
            _selectedDeliveryCompany.value = _deliveryCompanies.value.firstOrNull { it.companyId == 1 }
                ?: _deliveryCompanies.value.firstOrNull()
            _deliveryCharge.value = 0.0
        }
        if (type != "Takeaway") _takeawayName.value = ""
        if (type == "DineIn") refreshTables()
    }

    fun refreshTables() {
        viewModelScope.launch {
            try {
                _tables.value = orderRepo.getTables()
            } catch (_: Exception) {}
        }
    }

    fun setTable(table: RestaurantTable?) {
        _tableId.value   = table?.tableId
        _tableName.value = table?.tableName
    }

    fun setWaiter(waiterId: Int?) { _waiterId.value = waiterId }
    fun setDiscount(amount: Double) {
        val max = session.settings.value.maxDiscountPercent
        val clamped = if (max > 0) {
            val sub = subTotal.value
            val maxAmt = if (sub > 0) sub * max / 100.0 else amount
            amount.coerceIn(0.0, maxAmt)
        } else amount.coerceAtLeast(0.0)
        _discount.value = clamped
        if (_appliedVoucher.value != null) _appliedVoucher.value = null
    }
    fun setNotes(text: String)      { _notes.value = text }
    fun setDeliveryName(v: String)    { _deliveryName.value = v }

    fun applyVoucherCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _voucherLoading.value = true
            try {
                val result = voucherRepo.validateVoucher(code, subTotal.value, session.settings.value.currencySymbol)
                if (result.isValid) {
                    _appliedVoucher.value = result
                    _discount.value = result.discountAmount
                    _event.emit(PosUiEvent.Info(result.message))
                } else {
                    _event.emit(PosUiEvent.Error(result.message))
                }
            } catch (e: Exception) {
                _event.emit(PosUiEvent.Error("Could not apply voucher: ${e.message}"))
            } finally {
                _voucherLoading.value = false
            }
        }
    }

    fun removeVoucher() {
        _appliedVoucher.value = null
        _discount.value = 0.0
    }
    fun setDeliveryPhone(v: String) {
        _deliveryPhone.value = v
        val trimmed = v.trim()
        if (trimmed.length >= 10) lookupCustomerByPhone(trimmed)
        else _foundCustomerHint.value = null
    }
    fun setDeliveryAddress(v: String)        { _deliveryAddress.value = v }
    fun setTakeawayName(v: String)           { _takeawayName.value = v }
    fun setDeliveryCompany(c: DeliveryCompany?) { _selectedDeliveryCompany.value = c }
    fun setDeliveryCharge(v: Double)         { _deliveryCharge.value = v.coerceAtLeast(0.0) }

    fun togglePin(productId: Int) {
        val updated = _pinnedIds.value.toMutableSet().also {
            if (productId in it) it.remove(productId) else it.add(productId)
        }
        _pinnedIds.value = updated
        viewModelScope.launch { prefs.savePinnedProductIds(updated) }
    }

    fun toggleProductAvailability(productId: Int) {
        val current = _allProducts.value.find { it.productId == productId } ?: return
        val newValue = !current.isAvailable
        // Optimistic update
        _allProducts.value = _allProducts.value.map {
            if (it.productId == productId) it.copy(isAvailable = newValue) else it
        }
        _products.value = _products.value.map {
            if (it.productId == productId) it.copy(isAvailable = newValue) else it
        }
        viewModelScope.launch {
            try {
                productRepo.setProductAvailability(productId, newValue)
            } catch (e: Exception) {
                // Rollback
                _allProducts.value = _allProducts.value.map {
                    if (it.productId == productId) it.copy(isAvailable = !newValue) else it
                }
                _products.value = _products.value.map {
                    if (it.productId == productId) it.copy(isAvailable = !newValue) else it
                }
                _event.emit(PosUiEvent.Error("Failed to update availability: ${e.message}"))
            }
        }
    }

    // ── Customer search ───────────────────────────────────────────────────────
    fun searchCustomers(query: String) {
        if (query.isBlank()) { _customerResults.value = emptyList(); return }
        viewModelScope.launch {
            _isSearchingCustomer.value = true
            try {
                _customerResults.value = customerRepo.getCustomers(query)
            } catch (_: Exception) {
            } finally {
                _isSearchingCustomer.value = false
            }
        }
    }

    fun setCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
        _customerResults.value  = emptyList()
    }

    private fun lookupCustomerByPhone(phone: String) {
        viewModelScope.launch {
            try {
                val customer = customerRepo.getByPhone(phone)
                if (customer != null) {
                    _foundCustomerHint.value = "Found: ${customer.customerName}"
                    if (_deliveryName.value.isBlank()) _deliveryName.value = customer.customerName
                    if (_deliveryAddress.value.isBlank()) _deliveryAddress.value = customer.address
                    _selectedCustomer.value = customer
                } else {
                    _foundCustomerHint.value = null
                    _selectedCustomer.value = null
                }
            } catch (_: Exception) {
                _foundCustomerHint.value = null
            }
        }
    }

    // ── Place order ───────────────────────────────────────────────────────────
    fun placeOrder() {
        if (session.currentShift.value == null) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("No open shift. Open a shift before taking orders.")) }
            return
        }
        if (_cart.value.isEmpty()) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("Cart is empty.")) }
            return
        }
        if (session.settings.value.requireWaiter && _waiterId.value == null) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("Waiter is required. Please select a waiter before placing the order.")) }
            return
        }
        if (_orderType.value == "DineIn" && _tableId.value == null) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("Please select a table for Dine In orders.")) }
            return
        }
        if (_orderType.value == "Delivery" && _deliveryPhone.value.isBlank()) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("Phone number is required for Delivery orders.")) }
            return
        }
        val editId = _editOrderId.value
        if (editId != null) { updateEditedOrder(editId); return }
        val targetOrderId = _addToOrderId.value
        if (targetOrderId != null) { addItemsToExistingOrder(targetOrderId); return }
        viewModelScope.launch {
            _isPlacingOrder.value = true
            val settings     = session.settings.value
            val cartSnapshot = _cart.value.toList()
            val sub          = subTotal.value
            val tax          = taxAmount.value
            val svc          = settings.serviceChargePercent * sub / 100.0
            val grand        = grandTotal.value

            // Peer server offline — queue order locally for sync on reconnect
            if (!connectivity.isOnline.value && connectivity.isPeerMode) {
                try {
                    offlineCache.saveOfflineOrder(
                        cart            = cartSnapshot,
                        orderType       = _orderType.value,
                        tableId         = _tableId.value,
                        waiterId        = _waiterId.value,
                        customerId      = _selectedCustomer.value?.customerId,
                        shiftId         = session.shiftId,
                        discountAmount  = _discount.value,
                        discountPercent = if (sub > 0) _discount.value / sub * 100.0 else 0.0,
                        taxAmount       = tax,
                        taxPercent      = settings.defaultTaxPercent,
                        serviceCharges  = svc,
                        deliveryCharge  = _deliveryCharge.value,
                        grandTotal      = grand,
                        subTotal        = sub,
                        notes           = _notes.value,
                        createdBy       = session.userId,
                        paymentMethod   = "Cash",
                        paymentAmount   = grand
                    )
                    clearCart()
                    _discount.value = 0.0
                    _notes.value    = ""
                    _event.emit(PosUiEvent.OrderQueued)
                } catch (e: Exception) {
                    _event.emit(PosUiEvent.Error("Failed to save order offline: ${e.message}"))
                } finally {
                    _isPlacingOrder.value = false
                }
                return@launch
            }

            try {
                val effectiveCustomerName = _selectedCustomer.value?.customerName?.takeIf { it.isNotBlank() }
                    ?: if (_orderType.value == "Takeaway" && _takeawayName.value.isNotBlank()) _takeawayName.value.trim() else null
                val orderId = orderRepo.placeOrder(
                    cart                = cartSnapshot,
                    orderType           = _orderType.value,
                    tableId             = _tableId.value,
                    waiterId            = _waiterId.value,
                    customerId          = _selectedCustomer.value?.customerId,
                    shiftId             = session.shiftId,
                    discountAmount      = _discount.value,
                    discountPercent     = if (sub > 0) _discount.value / sub * 100.0 else 0.0,
                    taxAmount           = tax,
                    taxPercent          = settings.defaultTaxPercent,
                    serviceCharges      = svc,
                    deliveryCharge      = _deliveryCharge.value,
                    grandTotal          = grand,
                    subTotal            = sub,
                    createdBy           = session.userId,
                    notes               = _notes.value,
                    settings            = settings,
                    deliveryName        = _deliveryName.value.ifBlank { null },
                    deliveryPhone       = _deliveryPhone.value.ifBlank { null },
                    deliveryAddress     = _deliveryAddress.value.ifBlank { null },
                    deliveryCompanyId   = _selectedDeliveryCompany.value?.companyId,
                    customerName        = effectiveCustomerName,
                    deliveryCompanyName = _selectedDeliveryCompany.value?.companyName,
                    commissionAmount    = if (_orderType.value == "Delivery") (_selectedDeliveryCompany.value?.commissionPercent ?: 0.0) * sub / 100.0 else 0.0,
                    voucherCode         = _appliedVoucher.value?.voucherCode?.ifBlank { null },
                    voucherDiscount     = _appliedVoucher.value?.discountAmount ?: 0.0
                )

                // Auto-save delivery customer and track visit
                val phone = _deliveryPhone.value.trim()
                if (_orderType.value == "Delivery" && phone.isNotBlank()) {
                    try {
                        var cid = _selectedCustomer.value?.customerId
                        if (cid == null) {
                            val existing = customerRepo.getByPhone(phone)
                            cid = existing?.customerId ?: customerRepo.addCustomer(
                                name      = _deliveryName.value.ifBlank { phone },
                                phone     = phone,
                                address   = _deliveryAddress.value,
                                createdBy = session.userId
                            )
                        }
                        if (cid != null && cid > 0) customerRepo.incrementTotalOrders(cid)
                    } catch (_: Exception) {}
                }

                connectivity.setOnline()
                _appliedVoucher.value?.let { v -> launch { try { voucherRepo.redeemVoucher(v.voucherId) } catch (_: Exception) {} } }

                // Capture messaging data before clearCart() wipes customer/phone
                val smsMode      = prefs.smsMode.first()
                val waMode       = prefs.whatsappMode.first()
                val msgPhone     = _deliveryPhone.value.ifBlank { _selectedCustomer.value?.phone ?: "" }
                val msgCustomer  = _selectedCustomer.value?.customerName ?: _deliveryName.value.ifBlank { "Customer" }
                val msgOrderType = _orderType.value

                // Fire KOT + token BEFORE navigation so preview dialogs show on POS screen
                val order    = orderRepo.getOrderById(orderId)
                val kotMode  = prefs.kotPrintMode.first()
                if (order != null) {
                    fireKotPrints(order, cartSnapshot, settings)
                    fireTakeawayToken(order, cartSnapshot, settings)
                }
                val kotPreviewPending = kotMode == "Preview" && _kotPreviewGroups.value.isNotEmpty()
                if (kotPreviewPending) _pendingOrderId.value = orderId

                clearCart()
                _discount.value = 0.0
                _notes.value    = ""

                // Auto-send messaging notification if mode is not Off
                if (msgPhone.isNotBlank() && (smsMode != "Off" || waMode != "Off")) {
                    val template = when (msgOrderType) {
                        "Delivery" -> prefs.smsTemplateDelivery.first()
                        "Takeaway" -> prefs.smsTemplateTakeaway.first()
                        "DineIn"   -> prefs.smsTemplateDineIn.first()
                        else       -> prefs.smsTemplateOther.first()
                    }
                    val orderNoStr = order?.orderNo ?: orderId.toString()
                    val totalStr   = settings.currencySymbol + "%.2f".format(grand)
                    val msg = template
                        .replace("{customerName}", msgCustomer)
                        .replace("{orderNo}",      orderNoStr)
                        .replace("{total}",        totalStr)
                        .replace("{type}",         msgOrderType)

                    when (smsMode) {
                        "Device" -> _event.emit(PosUiEvent.SmsSend(msgPhone, msg))
                        "Api" -> {
                            val url = session.settings.value.smsGatewayUrl
                            if (url.isNotBlank()) launch {
                                try { java.net.URL(url.replace("{phone}", msgPhone).replace("{message}", java.net.URLEncoder.encode(msg, "UTF-8"))).openConnection().connect() } catch (_: Exception) {}
                            }
                        }
                    }
                    when (waMode) {
                        "Device" -> _event.emit(PosUiEvent.WhatsAppSend(msgPhone, msg))
                        "Api" -> {
                            val url = session.settings.value.whatsappGatewayUrl
                            if (url.isNotBlank()) launch {
                                try { java.net.URL(url.replace("{phone}", msgPhone).replace("{message}", java.net.URLEncoder.encode(msg, "UTF-8"))).openConnection().connect() } catch (_: Exception) {}
                            }
                        }
                    }
                }

                // Navigate to payment — deferred if KOT preview is pending (user must dismiss first)
                if (!kotPreviewPending) {
                    _event.emit(PosUiEvent.OrderPlaced(orderId))
                }
            } catch (e: Exception) {
                if (isConnectionError(e)) {
                    connectivity.setOffline()
                    offlineCache.saveOfflineOrder(
                        cart            = cartSnapshot,
                        orderType       = _orderType.value,
                        tableId         = _tableId.value,
                        waiterId        = _waiterId.value,
                        customerId      = _selectedCustomer.value?.customerId,
                        shiftId         = session.shiftId,
                        discountAmount  = _discount.value,
                        discountPercent = if (sub > 0) _discount.value / sub * 100.0 else 0.0,
                        taxAmount       = tax,
                        taxPercent      = settings.defaultTaxPercent,
                        serviceCharges  = svc,
                        deliveryCharge  = _deliveryCharge.value,
                        grandTotal      = grand,
                        subTotal        = sub,
                        notes           = _notes.value,
                        createdBy       = session.userId
                    )
                    _pendingOfflineOrders.value = offlineCache.pendingCount()
                    clearCart()
                    _discount.value = 0.0
                    _notes.value    = ""
                    _event.emit(PosUiEvent.OrderQueued)
                } else {
                    _event.emit(PosUiEvent.Error("Failed to place order: ${e.message}"))
                }
            } finally {
                _isPlacingOrder.value = false
            }
        }
    }

    fun placeOrderPayLater() {
        if (session.currentShift.value == null) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("No open shift. Open a shift before taking orders.")) }
            return
        }
        if (_cart.value.isEmpty()) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("Cart is empty.")) }
            return
        }
        if (session.settings.value.requireWaiter && _waiterId.value == null) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("Waiter is required. Please select a waiter before placing the order.")) }
            return
        }
        if (_orderType.value == "DineIn" && _tableId.value == null) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("Please select a table for Dine In orders.")) }
            return
        }
        if (_orderType.value == "Delivery" && _deliveryPhone.value.isBlank()) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("Phone number is required for Delivery orders.")) }
            return
        }
        viewModelScope.launch {
            _isPlacingOrder.value = true
            val settings     = session.settings.value
            val cartSnapshot = _cart.value.toList()
            val sub          = subTotal.value
            val tax          = taxAmount.value
            val svc          = settings.serviceChargePercent * sub / 100.0
            val grand        = grandTotal.value
            try {
                val effectiveCustomerName = _selectedCustomer.value?.customerName?.takeIf { it.isNotBlank() }
                    ?: if (_orderType.value == "Takeaway" && _takeawayName.value.isNotBlank()) _takeawayName.value.trim() else null
                val orderId = orderRepo.placeOrder(
                    cart                = cartSnapshot,
                    orderType           = _orderType.value,
                    tableId             = _tableId.value,
                    waiterId            = _waiterId.value,
                    customerId          = _selectedCustomer.value?.customerId,
                    shiftId             = session.shiftId,
                    discountAmount      = _discount.value,
                    discountPercent     = if (sub > 0) _discount.value / sub * 100.0 else 0.0,
                    taxAmount           = tax,
                    taxPercent          = settings.defaultTaxPercent,
                    serviceCharges      = svc,
                    deliveryCharge      = _deliveryCharge.value,
                    grandTotal          = grand,
                    subTotal            = sub,
                    createdBy           = session.userId,
                    notes               = _notes.value,
                    settings            = settings,
                    deliveryName        = _deliveryName.value.ifBlank { null },
                    deliveryPhone       = _deliveryPhone.value.ifBlank { null },
                    deliveryAddress     = _deliveryAddress.value.ifBlank { null },
                    deliveryCompanyId   = _selectedDeliveryCompany.value?.companyId,
                    customerName        = effectiveCustomerName,
                    deliveryCompanyName = _selectedDeliveryCompany.value?.companyName,
                    commissionAmount    = if (_orderType.value == "Delivery") (_selectedDeliveryCompany.value?.commissionPercent ?: 0.0) * sub / 100.0 else 0.0,
                    voucherCode         = _appliedVoucher.value?.voucherCode?.ifBlank { null },
                    voucherDiscount     = _appliedVoucher.value?.discountAmount ?: 0.0
                )

                // Auto-save delivery customer and track visit
                val phone = _deliveryPhone.value.trim()
                if (_orderType.value == "Delivery" && phone.isNotBlank()) {
                    try {
                        var cid = _selectedCustomer.value?.customerId
                        if (cid == null) {
                            val existing = customerRepo.getByPhone(phone)
                            cid = existing?.customerId ?: customerRepo.addCustomer(
                                name      = _deliveryName.value.ifBlank { phone },
                                phone     = phone,
                                address   = _deliveryAddress.value,
                                createdBy = session.userId
                            )
                        }
                        if (cid != null && cid > 0) customerRepo.incrementTotalOrders(cid)
                    } catch (_: Exception) {}
                }

                connectivity.setOnline()
                _appliedVoucher.value?.let { v -> launch { try { voucherRepo.redeemVoucher(v.voucherId) } catch (_: Exception) {} } }
                // Fetch order details for token + slip printing
                val order   = try { orderRepo.getOrderById(orderId) } catch (_: Exception) { null }
                val tokenNo = order?.tokenNo ?: "#$orderId"
                if (order != null) {
                    fireKotPrints(order, cartSnapshot, settings)
                    fireTakeawayToken(order, cartSnapshot, settings)
                }
                _lastSavedCart.value = cartSnapshot
                clearCart()
                _discount.value = 0.0
                _notes.value    = ""
                _event.emit(PosUiEvent.OrderSaved(
                    tokenNo   = tokenNo,
                    orderNo   = order?.orderNo   ?: "",
                    orderType = order?.orderType ?: _orderType.value,
                    tableName = order?.tableName
                ))
            } catch (e: Exception) {
                if (isConnectionError(e)) {
                    connectivity.setOffline()
                    offlineCache.saveOfflineOrder(
                        cart            = cartSnapshot,
                        orderType       = _orderType.value,
                        tableId         = _tableId.value,
                        waiterId        = _waiterId.value,
                        customerId      = _selectedCustomer.value?.customerId,
                        shiftId         = session.shiftId,
                        discountAmount  = _discount.value,
                        discountPercent = if (sub > 0) _discount.value / sub * 100.0 else 0.0,
                        taxAmount       = tax,
                        taxPercent      = settings.defaultTaxPercent,
                        serviceCharges  = svc,
                        deliveryCharge  = _deliveryCharge.value,
                        grandTotal      = grand,
                        subTotal        = sub,
                        notes           = _notes.value,
                        createdBy       = session.userId
                    )
                    _pendingOfflineOrders.value = offlineCache.pendingCount()
                    clearCart()
                    _discount.value = 0.0
                    _notes.value    = ""
                    _event.emit(PosUiEvent.OrderQueued)
                } else {
                    _event.emit(PosUiEvent.Error("Failed to place order: ${e.message}"))
                }
            } finally {
                _isPlacingOrder.value = false
            }
        }
    }

    private fun addItemsToExistingOrder(orderId: Int) {
        viewModelScope.launch {
            _isPlacingOrder.value = true
            val settings     = session.settings.value
            val cartSnapshot = _cart.value.toList()
            val newSub       = subTotal.value
            val newTax       = taxAmount.value
            try {
                orderRepo.addItemsToOrder(orderId, cartSnapshot, newSub, newTax)
                connectivity.setOnline()
                val order = orderRepo.getOrderById(orderId)
                if (order != null) fireKotPrints(order, cartSnapshot, settings)
                clearCart()
                _addToOrderId.value = null
                _addToOrderNo.value = null
                _discount.value = 0.0
                _notes.value    = ""
                _event.emit(PosUiEvent.ItemsAdded(orderId))
            } catch (e: Exception) {
                _event.emit(PosUiEvent.Error("Failed to add items: ${e.message}"))
            } finally {
                _isPlacingOrder.value = false
            }
        }
    }

    private fun fireTakeawayToken(order: Order, cartSnapshot: List<CartItem>, settings: com.fastpos.android.data.models.CompanySettings) {
        if (order.orderType != "Takeaway") return
        viewModelScope.launch {
            // Check master toggle — if off, skip everything (no preview, no print)
            if (!prefs.autoPrintTakeawayToken.first()) return@launch

            // Show on-screen token preview
            _tokenPreview.value = TakeawayTokenPreviewData(
                orderNo      = order.orderNo,
                tokenNo      = order.tokenNo,
                customerName = order.customerName,
                notes        = order.notes,
                items        = cartSnapshot,
                companyName  = settings.companyName
            )

            // Also print if a printer is configured
            val billMode = prefs.billPrintMode.first()
            if (billMode == "Off" || billMode == "Silent" || billMode == "Preview") return@launch
            if (!prefs.autoPrintTakeawayToken.first()) return@launch

            val prType    = prefs.receiptPrinterType.first()
            val btAddress = prefs.savedPrinterAddress.first()
            val netIp     = prefs.receiptNetIp.first()
            val netPort   = prefs.receiptNetPort.first()
            val lineWidth = prefs.paperWidth.first()
            val logoData  = settings.logoData
            if (prType == "Network" && netIp.isNotBlank()) {
                NetworkPrinterHelper.printTakeawayToken(
                    ip           = netIp,
                    port         = netPort,
                    orderNo      = order.orderNo,
                    tokenNo      = order.tokenNo,
                    customerName = order.customerName,
                    notes        = order.notes,
                    items        = cartSnapshot,
                    companyName  = settings.companyName,
                    lineWidth    = lineWidth,
                    logoData     = logoData
                )
            } else if (btAddress.isNotBlank()) {
                BluetoothPrinterHelper.printTakeawayToken(
                    address      = btAddress,
                    orderNo      = order.orderNo,
                    tokenNo      = order.tokenNo,
                    customerName = order.customerName,
                    notes        = order.notes,
                    items        = cartSnapshot,
                    companyName  = settings.companyName,
                    lineWidth    = lineWidth,
                    logoData     = logoData
                )
            }
        }
    }

    private suspend fun fireKotPrints(order: Order, cartSnapshot: List<CartItem>, settings: com.fastpos.android.data.models.CompanySettings) {
        val mode = prefs.kotPrintMode.first()
        if (mode == "Off") return
        val kitchenConfigs = prefs.kitchenPrinters.first()
        val configMap = kitchenConfigs.associateBy { it.printerName.lowercase() }
        val logoData = settings.logoData
        val groups = cartSnapshot.groupBy { it.printerName.trim() }
            .mapNotNull { (printerName, groupItems) ->
                if (printerName.isBlank()) return@mapNotNull null
                val cfg = configMap[printerName.lowercase()] ?: return@mapNotNull null
                KotPreviewGroup(
                    printerName  = printerName,
                    ip           = cfg.ipAddress,
                    port         = cfg.port,
                    paperType    = cfg.paperType,
                    items        = groupItems,
                    orderNo      = order.orderNo,
                    tokenNo      = order.tokenNo,
                    orderType    = order.orderType,
                    tableName    = order.tableName ?: "",
                    waiterName   = order.waiterName ?: "",
                    notes        = order.notes ?: "",
                    customerName = order.customerName ?: "",
                    companyName  = settings.companyName,
                    logoData     = logoData
                )
            }
        if (groups.isEmpty()) return
        if (mode == "Preview") {
            _kotPreviewGroups.value = groups
        } else {
            groups.forEach { g ->
                viewModelScope.launch {
                    NetworkPrinterHelper.printKitchenTicket(
                        ip           = g.ip,
                        port         = g.port,
                        orderNo      = g.orderNo,
                        tokenNo      = g.tokenNo,
                        orderType    = g.orderType,
                        tableName    = g.tableName,
                        items        = g.items,
                        stationName  = g.printerName,
                        companyName  = g.companyName,
                        waiterName   = g.waiterName,
                        notes        = g.notes,
                        customerName = g.customerName,
                        paperType    = g.paperType,
                        logoData     = g.logoData
                    )
                }
            }
        }
    }

    fun dismissKotPreview() {
        val pendingId = _pendingOrderId.value
        _pendingOrderId.value   = null
        _kotPreviewGroups.value = emptyList()
        if (pendingId != null) {
            viewModelScope.launch { _event.emit(PosUiEvent.OrderPlaced(pendingId)) }
        }
    }

    fun clearTokenPreview() { _tokenPreview.value = null }

    fun printKotGroupNow(group: KotPreviewGroup) {
        viewModelScope.launch {
            NetworkPrinterHelper.printKitchenTicket(
                ip           = group.ip,
                port         = group.port,
                orderNo      = group.orderNo,
                tokenNo      = group.tokenNo,
                orderType    = group.orderType,
                tableName    = group.tableName,
                items        = group.items,
                stationName  = group.printerName,
                companyName  = group.companyName,
                waiterName   = group.waiterName,
                notes        = group.notes,
                customerName = group.customerName,
                paperType    = group.paperType,
                logoData     = group.logoData
            )
        }
    }

    private fun updateEditedOrder(orderId: Int) {
        viewModelScope.launch {
            _isPlacingOrder.value = true
            try {
                val settings     = session.settings.value
                val cartSnapshot = _cart.value.toList()
                val sub          = subTotal.value
                val tax          = taxAmount.value
                val discount     = _discount.value
                val notes        = _notes.value

                orderRepo.replaceOrderItems(
                    orderId   = orderId,
                    cart      = cartSnapshot,
                    subTotal  = sub,
                    taxAmount = tax,
                    discount  = discount,
                    notes     = notes,
                    orderType = _orderType.value,
                    tableId   = _tableId.value,
                    waiterId  = _waiterId.value
                )

                clearCart()
                _editOrderId.value = null
                _editOrderNo.value = null
                _discount.value    = 0.0
                _notes.value       = ""
                _event.emit(PosUiEvent.OrderUpdated)
            } catch (e: Exception) {
                _event.emit(PosUiEvent.Error("Failed to update order: ${e.message}"))
            } finally {
                _isPlacingOrder.value = false
            }
        }
    }

    private fun checkAddToOrder() {
        val orderId = bridge.pendingAddToOrderId ?: return
        val orderNo = bridge.pendingAddToOrderNo ?: ""
        bridge.pendingAddToOrderId = null
        bridge.pendingAddToOrderNo = null
        _addToOrderId.value = orderId
        _addToOrderNo.value = orderNo
    }

    fun cancelAddToOrder() {
        _addToOrderId.value = null
        _addToOrderNo.value = null
        clearCart()
    }

    private fun checkEditOrder() {
        val orderId = bridge.pendingEditOrderId ?: return
        val orderNo = bridge.pendingEditOrderNo ?: ""
        bridge.pendingEditOrderId = null
        bridge.pendingEditOrderNo = null
        _editOrderId.value = orderId
        _editOrderNo.value = orderNo
        viewModelScope.launch {
            try {
                val order = orderRepo.getOrderById(orderId) ?: return@launch
                val items = orderRepo.getCartItemsForOrder(orderId)
                _cart.value             = items
                _orderType.value        = order.orderType
                _tableId.value          = order.tableId
                _tableName.value        = order.tableName
                _waiterId.value         = order.waiterId
                _discount.value         = order.discountAmount
                _notes.value            = order.notes ?: ""
                _deliveryName.value     = order.deliveryName ?: ""
                _deliveryPhone.value    = order.deliveryPhone ?: ""
                _deliveryAddress.value  = order.deliveryAddress ?: ""
            } catch (_: Exception) {}
        }
    }

    fun cancelEditOrder() {
        _editOrderId.value = null
        _editOrderNo.value = null
        clearCart()
    }

    fun holdOrder() {
        if (session.currentShift.value == null) {
            viewModelScope.launch { _event.emit(PosUiEvent.Error("No open shift. Open a shift before taking orders.")) }
            return
        }
        if (_cart.value.isEmpty()) return
        viewModelScope.launch {
            _isPlacingOrder.value = true
            val settings     = session.settings.value
            val cartSnapshot = _cart.value.toList()
            val sub          = subTotal.value
            val tax          = taxAmount.value
            val svc          = settings.serviceChargePercent * sub / 100.0
            val grand        = grandTotal.value
            try {
                val tokenNo = orderRepo.holdOrder(
                    cart            = cartSnapshot,
                    orderType       = _orderType.value,
                    tableId         = _tableId.value,
                    waiterId        = _waiterId.value,
                    customerId      = _selectedCustomer.value?.customerId,
                    shiftId         = session.shiftId,
                    discountAmount  = _discount.value,
                    discountPercent = if (sub > 0) _discount.value / sub * 100.0 else 0.0,
                    taxAmount       = tax,
                    taxPercent      = settings.defaultTaxPercent,
                    serviceCharges  = svc,
                    grandTotal      = grand,
                    subTotal        = sub,
                    createdBy       = session.userId,
                    notes           = _notes.value,
                    settings        = settings
                )
                connectivity.setOnline()
                clearCart()
                _discount.value = 0.0
                _notes.value    = ""
                _appliedVoucher.value = null
                loadHeldOrders()
                _event.emit(PosUiEvent.OrderHeld(tokenNo))
            } catch (e: Exception) {
                _event.emit(PosUiEvent.Error("Failed to hold order: ${e.message}"))
            } finally {
                _isPlacingOrder.value = false
            }
        }
    }

    fun loadHeldOrders() {
        viewModelScope.launch {
            try { _heldOrders.value = orderRepo.getHeldOrders() } catch (_: Exception) {}
        }
    }

    fun resumeHeldOrder(order: com.fastpos.android.data.models.Order) {
        viewModelScope.launch {
            try {
                val items = orderRepo.getCartItemsForOrder(order.orderId)
                if (items.isEmpty()) { _event.emit(PosUiEvent.Error("No items found for this order.")); return@launch }
                orderRepo.cancelHeldOrder(order.orderId)
                _cart.value      = items
                _discount.value  = order.discountAmount
                _orderType.value = order.orderType
                _tableId.value   = order.tableId
                _tableName.value = order.tableName
                _waiterId.value  = order.waiterId
                _notes.value     = order.notes ?: ""
                _heldOrders.value = _heldOrders.value.filter { it.orderId != order.orderId }
                _event.emit(PosUiEvent.Info("Order ${order.orderNo} resumed."))
            } catch (e: Exception) {
                _event.emit(PosUiEvent.Error("Failed to resume order: ${e.message}"))
            }
        }
    }

    private fun checkResume() {
        val items = bridge.pendingCart ?: return
        bridge.pendingCart = null
        _cart.value = items
    }

    // ── Sync pending offline orders ───────────────────────────────────────────
    private suspend fun syncPendingOrders() {
        val pending = offlineCache.getPendingOrders()
        if (pending.isEmpty()) return
        val settings = session.settings.value
        var synced = 0
        pending.forEach { entity ->
            try {
                val cart = offlineCache.cartFromJson(entity.itemsJson)
                orderRepo.placeOrder(
                    cart            = cart,
                    orderType       = entity.orderType,
                    tableId         = entity.tableId.takeIf { it > 0 },
                    waiterId        = entity.waiterId.takeIf { it > 0 },
                    customerId      = entity.customerId.takeIf { it > 0 },
                    shiftId         = entity.shiftId.takeIf { it > 0 },
                    discountAmount  = entity.discountAmount,
                    discountPercent = entity.discountPercent,
                    taxAmount       = entity.taxAmount,
                    taxPercent      = entity.taxPercent,
                    serviceCharges  = entity.serviceCharges,
                    deliveryCharge  = 0.0,
                    grandTotal      = entity.grandTotal,
                    subTotal        = entity.subTotal,
                    createdBy       = entity.createdBy,
                    notes           = entity.notes,
                    settings        = settings
                )
                offlineCache.markSynced(entity.localId)
                synced++
            } catch (_: Exception) {
                // leave in queue if sync fails
            }
        }
        _pendingOfflineOrders.value = offlineCache.pendingCount()
        if (synced > 0) {
            _event.emit(PosUiEvent.Info("$synced offline order(s) synced to server."))
        }
    }

    private fun isConnectionError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        // Only true network/transport failures should mark us offline.
        // Generic SQLExceptions (wrong column, constraint violation, etc.) must NOT
        // trigger offline mode — they are server errors, not connectivity failures.
        return e is java.net.SocketException ||
               e is java.net.ConnectException ||
               e is java.net.SocketTimeoutException ||
               e is java.net.UnknownHostException ||
               msg.contains("server unreachable") ||
               msg.contains("unreachable at") ||
               msg.contains("connection closed by server") ||
               (e is java.sql.SQLException && (
                   msg.contains("socket") ||
                   msg.contains("network") ||
                   msg.contains("timeout") ||
                   msg.contains("refused") ||
                   msg.contains("unreachable") ||
                   msg.contains("server not found") ||
                   msg.contains("unable to connect") ||
                   msg.contains("login failed")
               ))
    }
}
