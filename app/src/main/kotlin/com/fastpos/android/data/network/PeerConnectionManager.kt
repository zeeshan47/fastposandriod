package com.fastpos.android.data.network

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.PaymentEntry
import com.fastpos.android.data.repositories.OfflineCacheRepository
import com.fastpos.android.data.repositories.OrderRepository
import com.fastpos.android.data.repositories.ProductRepository
import com.fastpos.android.utils.ConnectivityMonitor
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeerConnectionManager @Inject constructor(
    private val db:           DatabaseHelper,
    private val connectivity: ConnectivityMonitor,
    private val offlineCache: OfflineCacheRepository,
    private val productRepo:  ProductRepository,
    private val orderRepo:    OrderRepository,
    private val session:      SessionManager,
    private val prefs:        PreferencesManager
) {
    private val scope       = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    fun start() {
        scope.launch {
            prefs.dbMode.collect { mode ->
                if (mode == "peer") {
                    connectivity.setPeerMode(true)
                    if (monitorJob?.isActive != true) {
                        monitorJob = scope.launch { runMonitorLoop() }
                    }
                } else {
                    monitorJob?.cancel()
                    monitorJob = null
                    connectivity.setPeerMode(false)
                    connectivity.setOnline()
                }
            }
        }
    }

    private suspend fun runMonitorLoop() {
        while (true) {
            val alive     = try { db.pingPeer() } catch (_: Exception) { false }
            val wasOnline = connectivity.isOnline.value

            when {
                alive && !wasOnline -> {
                    // Came back online after outage — sync then mark online
                    scope.launch { syncPendingOrders() }
                    scope.launch { cacheCatalog() }
                    connectivity.setOnline()
                }
                alive && !offlineCache.hasCachedData() -> {
                    // Online but catalog not yet cached (first connect)
                    scope.launch { cacheCatalog() }
                }
                !alive && wasOnline -> {
                    connectivity.setOffline()
                }
            }

            delay(5_000)
        }
    }

    private suspend fun cacheCatalog() {
        try {
            val cats  = productRepo.getCategories()
            val prods = productRepo.getProducts()
            if (cats.isNotEmpty()) offlineCache.cacheCategories(cats)
            if (prods.isNotEmpty()) offlineCache.cacheProducts(prods)
        } catch (_: Exception) {}
    }

    private suspend fun syncPendingOrders() {
        try {
            val pending  = offlineCache.getPendingOrders()
            if (pending.isEmpty()) return
            val settings = session.settings.value
            for (entity in pending) {
                try {
                    val cart    = offlineCache.cartFromJson(entity.itemsJson)
                    val orderId = orderRepo.placeOrder(
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
                        deliveryCharge  = entity.deliveryCharge,
                        grandTotal      = entity.grandTotal,
                        subTotal        = entity.subTotal,
                        createdBy       = entity.createdBy,
                        notes           = entity.notes,
                        settings        = settings
                    )
                    val payAmt = if (entity.paymentAmount > 0) entity.paymentAmount else entity.grandTotal
                    orderRepo.addPayment(
                        orderId   = orderId,
                        payments  = listOf(PaymentEntry(entity.paymentMethod, payAmt)),
                        createdBy = entity.createdBy
                    )
                    offlineCache.markSynced(entity.localId)
                } catch (_: Exception) {
                    // Leave unsynced — will retry on next reconnect
                }
            }
        } catch (_: Exception) {}
    }
}
