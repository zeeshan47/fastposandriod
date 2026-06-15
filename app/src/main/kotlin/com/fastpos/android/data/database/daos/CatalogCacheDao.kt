package com.fastpos.android.data.database.daos

import androidx.room.*
import com.fastpos.android.data.database.entities.CachedCategoryEntity
import com.fastpos.android.data.database.entities.CachedProductEntity

@Dao
interface CatalogCacheDao {

    @Query("SELECT * FROM cached_categories ORDER BY displayOrder, categoryName")
    suspend fun getAllCategories(): List<CachedCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(list: List<CachedCategoryEntity>)

    @Query("DELETE FROM cached_categories")
    suspend fun clearCategories()

    @Query("SELECT * FROM cached_products WHERE (:catId = 0 OR categoryId = :catId) ORDER BY displayOrder, productName")
    suspend fun getProducts(catId: Int): List<CachedProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(list: List<CachedProductEntity>)

    @Query("DELETE FROM cached_products")
    suspend fun clearProducts()
}
