package com.fastpos.android.data.database.daos

import androidx.room.*
import com.fastpos.android.data.database.entities.OfflineOrderEntity

@Dao
interface OfflineOrderDao {

    @Insert
    suspend fun insert(order: OfflineOrderEntity): Long

    @Query("SELECT * FROM offline_orders WHERE isSynced = 0 ORDER BY createdAt")
    suspend fun getPending(): List<OfflineOrderEntity>

    @Query("UPDATE offline_orders SET isSynced = 1 WHERE localId = :id")
    suspend fun markSynced(id: Int)

    @Query("SELECT COUNT(*) FROM offline_orders WHERE isSynced = 0")
    suspend fun pendingCount(): Int
}
