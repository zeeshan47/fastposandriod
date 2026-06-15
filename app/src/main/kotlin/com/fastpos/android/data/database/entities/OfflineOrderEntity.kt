package com.fastpos.android.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_orders")
data class OfflineOrderEntity(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val orderType:       String,
    val tableId:         Int,
    val waiterId:        Int,
    val customerId:      Int,
    val shiftId:         Int,
    val discountAmount:  Double,
    val discountPercent: Double,
    val taxAmount:       Double,
    val taxPercent:      Double,
    val serviceCharges:  Double,
    val grandTotal:      Double,
    val subTotal:        Double,
    val notes:           String,
    val createdBy:       Int,
    val createdAt:       Long,
    val isSynced:        Boolean = false,
    val itemsJson:       String,
    val paymentMethod:   String  = "Cash",
    val paymentAmount:   Double  = 0.0,
    val deliveryCharge:  Double  = 0.0
)
