package com.fastpos.android.utils

import com.fastpos.android.data.repositories.FbrRepository
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FbrQueueService @Inject constructor(
    private val fbrRepo: FbrRepository,
    private val session: SessionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                delay(30_000L)
                val settings = session.settings.value
                if (settings.fbrEnabled &&
                    settings.fbrToken.isNotBlank() &&
                    settings.fbrNtn.isNotBlank()
                ) {
                    processPending()
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun processPending() {
        try {
            val settings = session.settings.value
            val pending  = fbrRepo.getPendingFbrOrders()
            for (order in pending) {
                try {
                    val items        = fbrRepo.getOrderItemsForFbr(order.orderId)
                    val orderWithItems = order.copy(items = items)
                    val invoiceNo    = FbrApiClient.submitInvoice(orderWithItems, settings)
                    if (!invoiceNo.isNullOrBlank()) {
                        fbrRepo.updateFbrStatus(order.orderId, "Submitted", invoiceNo)
                    } else {
                        fbrRepo.updateFbrStatus(order.orderId, "Failed")
                    }
                } catch (_: Exception) {
                    runCatching { fbrRepo.updateFbrStatus(order.orderId, "Failed") }
                }
            }
        } catch (_: Exception) { /* will retry on next 30 s tick */ }
    }
}
