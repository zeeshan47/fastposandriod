package com.fastpos.android.utils

import com.fastpos.android.data.models.CartItem
import javax.inject.Inject
import javax.inject.Singleton

/** Passes cart payloads between OrdersViewModel and PosViewModel. */
@Singleton
class PosCartBridge @Inject constructor() {
    var pendingCart: List<CartItem>? = null          // resume held order
    var pendingAddToOrderId: Int? = null             // add items to existing order
    var pendingAddToOrderNo: String? = null
    var pendingEditOrderId: Int? = null              // full edit of existing order
    var pendingEditOrderNo: String? = null
}
