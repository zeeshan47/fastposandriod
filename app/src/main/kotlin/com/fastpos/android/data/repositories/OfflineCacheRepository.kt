package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.AppDatabase
import com.fastpos.android.data.database.entities.CachedCategoryEntity
import com.fastpos.android.data.database.entities.CachedProductEntity
import com.fastpos.android.data.database.entities.OfflineOrderEntity
import com.fastpos.android.data.models.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineCacheRepository @Inject constructor(private val db: AppDatabase) {

    private val gson = Gson()

    // ── Catalog cache ─────────────────────────────────────────────────────────

    suspend fun cacheCategories(categories: List<Category>) {
        db.catalogCacheDao().insertCategories(categories.map {
            CachedCategoryEntity(it.categoryId, it.categoryName, it.colorCode, it.displayOrder)
        })
    }

    suspend fun getCachedCategories(): List<Category> =
        db.catalogCacheDao().getAllCategories().map {
            Category(categoryId = it.categoryId, categoryName = it.categoryName, colorCode = it.colorCode, displayOrder = it.displayOrder)
        }

    suspend fun cacheProducts(products: List<Product>) {
        db.catalogCacheDao().insertProducts(products.map {
            CachedProductEntity(
                productId      = it.productId,
                productCode    = it.productCode,
                productName    = it.productName,
                categoryId     = it.categoryId,
                categoryName   = it.categoryName,
                categoryColor  = it.categoryColor,
                productType    = it.productType,
                salePrice      = it.salePrice,
                costPrice      = it.costPrice,
                isStockManaged = it.isStockManaged,
                taxId          = it.taxId ?: 0,
                taxPercent     = it.taxPercent,
                displayOrder   = it.displayOrder,
                printerName    = it.printerName
            )
        })
    }

    suspend fun getCachedProducts(categoryId: Int? = null): List<Product> =
        db.catalogCacheDao().getProducts(categoryId ?: 0).map {
            Product(
                productId      = it.productId,
                productCode    = it.productCode,
                productName    = it.productName,
                categoryId     = it.categoryId,
                categoryName   = it.categoryName,
                categoryColor  = it.categoryColor,
                productType    = it.productType,
                salePrice      = it.salePrice,
                costPrice      = it.costPrice,
                isStockManaged = it.isStockManaged,
                taxId          = it.taxId.takeIf { id -> id > 0 },
                taxPercent     = it.taxPercent,
                displayOrder   = it.displayOrder,
                printerName    = it.printerName
            )
        }

    suspend fun hasCachedData(): Boolean =
        db.catalogCacheDao().getAllCategories().isNotEmpty()

    // ── Offline order queue ───────────────────────────────────────────────────

    suspend fun saveOfflineOrder(
        cart:            List<CartItem>,
        orderType:       String,
        tableId:         Int?,
        waiterId:        Int?,
        customerId:      Int?,
        shiftId:         Int?,
        discountAmount:  Double,
        discountPercent: Double,
        taxAmount:       Double,
        taxPercent:      Double,
        serviceCharges:  Double,
        deliveryCharge:  Double,
        grandTotal:      Double,
        subTotal:        Double,
        notes:           String,
        createdBy:       Int,
        paymentMethod:   String = "Cash",
        paymentAmount:   Double = 0.0
    ) {
        db.offlineOrderDao().insert(
            OfflineOrderEntity(
                orderType       = orderType,
                tableId         = tableId ?: 0,
                waiterId        = waiterId ?: 0,
                customerId      = customerId ?: 0,
                shiftId         = shiftId ?: 0,
                discountAmount  = discountAmount,
                discountPercent = discountPercent,
                taxAmount       = taxAmount,
                taxPercent      = taxPercent,
                serviceCharges  = serviceCharges,
                deliveryCharge  = deliveryCharge,
                grandTotal      = grandTotal,
                subTotal        = subTotal,
                notes           = notes,
                createdBy       = createdBy,
                createdAt       = System.currentTimeMillis(),
                itemsJson       = gson.toJson(cart),
                paymentMethod   = paymentMethod,
                paymentAmount   = paymentAmount
            )
        )
    }

    suspend fun getPendingOrders(): List<OfflineOrderEntity> =
        db.offlineOrderDao().getPending()

    suspend fun markSynced(localId: Int) =
        db.offlineOrderDao().markSynced(localId)

    suspend fun pendingCount(): Int =
        db.offlineOrderDao().pendingCount()

    fun cartFromJson(json: String): List<CartItem> = try {
        val type = object : TypeToken<List<CartItem>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    } catch (_: Exception) { emptyList() }
}
