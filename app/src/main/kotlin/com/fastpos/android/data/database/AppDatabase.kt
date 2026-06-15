package com.fastpos.android.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.fastpos.android.data.database.daos.CatalogCacheDao
import com.fastpos.android.data.database.daos.OfflineOrderDao
import com.fastpos.android.data.database.entities.CachedCategoryEntity
import com.fastpos.android.data.database.entities.CachedProductEntity
import com.fastpos.android.data.database.entities.OfflineOrderEntity

@Database(
    entities = [
        CachedCategoryEntity::class,
        CachedProductEntity::class,
        OfflineOrderEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogCacheDao(): CatalogCacheDao
    abstract fun offlineOrderDao(): OfflineOrderDao
}
