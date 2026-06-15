package com.fastpos.android.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_products")
data class CachedProductEntity(
    @PrimaryKey val productId:     Int,
    val productCode:   String,
    val productName:   String,
    val categoryId:    Int,
    val categoryName:  String,
    val categoryColor: String,
    val productType:   String,
    val salePrice:     Double,
    val costPrice:     Double,
    val isStockManaged: Boolean,
    val taxId:         Int,
    val taxPercent:    Double,
    val displayOrder:  Int,
    val printerName:   String
)
