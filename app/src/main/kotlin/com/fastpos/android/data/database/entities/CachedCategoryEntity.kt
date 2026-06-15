package com.fastpos.android.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_categories")
data class CachedCategoryEntity(
    @PrimaryKey val categoryId: Int,
    val categoryName: String,
    val colorCode:    String,
    val displayOrder: Int
)
