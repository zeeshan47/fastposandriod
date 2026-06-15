package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.ProductSchedule
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductScheduleRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun initSchema() {
        db.execute("""
            IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='ProductSchedules' AND xtype='U')
            CREATE TABLE ProductSchedules (
                ScheduleId  INT IDENTITY(1,1) PRIMARY KEY,
                ProductId   INT          NOT NULL,
                Label       NVARCHAR(50) DEFAULT '',
                DayOfWeek   INT          DEFAULT -1,
                StartTime   NVARCHAR(5)  DEFAULT '00:00',
                EndTime     NVARCHAR(5)  DEFAULT '23:59',
                IsActive    BIT          DEFAULT 1,
                CreatedAt   DATETIME     DEFAULT GETDATE()
            )
        """)
    }

    suspend fun getSchedulesForProduct(productId: Int): List<ProductSchedule> {
        initSchema()
        return db.query(
            "SELECT * FROM ProductSchedules WHERE ProductId=? AND IsActive=1 ORDER BY DayOfWeek, StartTime",
            listOf(productId)
        ) { rs ->
            ProductSchedule(
                scheduleId = rs.getInt("ScheduleId"),
                productId  = rs.getInt("ProductId"),
                label      = rs.getString("Label") ?: "",
                dayOfWeek  = rs.getInt("DayOfWeek"),
                startTime  = rs.getString("StartTime") ?: "00:00",
                endTime    = rs.getString("EndTime") ?: "23:59",
                isActive   = rs.getBoolean("IsActive")
            )
        }
    }

    suspend fun addSchedule(
        productId: Int,
        label: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String
    ) {
        initSchema()
        db.execute(
            "INSERT INTO ProductSchedules (ProductId, Label, DayOfWeek, StartTime, EndTime) VALUES (?,?,?,?,?)",
            listOf(productId, label, dayOfWeek, startTime, endTime)
        )
    }

    suspend fun updateSchedule(
        scheduleId: Int,
        label: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String
    ) {
        db.execute(
            "UPDATE ProductSchedules SET Label=?, DayOfWeek=?, StartTime=?, EndTime=? WHERE ScheduleId=?",
            listOf(label, dayOfWeek, startTime, endTime, scheduleId)
        )
    }

    suspend fun deleteSchedule(scheduleId: Int) {
        db.execute("UPDATE ProductSchedules SET IsActive=0 WHERE ScheduleId=?", listOf(scheduleId))
    }

    /** Returns set of productIds that are currently restricted by a schedule but NOT matching now.
     *  Products with NO schedules are always available.
     *  Products WITH schedules are only available if at least one schedule matches now. */
    suspend fun getUnavailableProductIds(): Set<Int> {
        initSchema()
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK) - 1   // 0=Sun…6=Sat
        val hh  = cal.get(Calendar.HOUR_OF_DAY)
        val mm  = cal.get(Calendar.MINUTE)
        val now = "%02d:%02d".format(hh, mm)

        return try {
            val scheduledProducts = db.query(
                "SELECT DISTINCT ProductId FROM ProductSchedules WHERE IsActive=1",
                emptyList()
            ) { it.getInt("ProductId") }.toSet()

            if (scheduledProducts.isEmpty()) return emptySet()

            // Products with a schedule matching now (day + time range)
            val available = db.query(
                """SELECT DISTINCT ProductId FROM ProductSchedules
                   WHERE IsActive=1
                     AND (DayOfWeek=-1 OR DayOfWeek=?)
                     AND StartTime <= ? AND EndTime >= ?""",
                listOf(dow, now, now)
            ) { it.getInt("ProductId") }.toSet()

            // Scheduled products NOT available right now
            scheduledProducts - available
        } catch (_: Exception) { emptySet() }
    }
}
