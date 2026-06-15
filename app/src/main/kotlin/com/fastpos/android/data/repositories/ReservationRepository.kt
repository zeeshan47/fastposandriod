package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Reservation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReservationRepository @Inject constructor(
    private val db:    DatabaseHelper,
    private val audit: AuditLogRepository
) {

    suspend fun initSchema() = withContext(Dispatchers.IO) {
        db.execute("""
            IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'Reservations')
            CREATE TABLE Reservations (
                ReservationId   INT IDENTITY(1,1) PRIMARY KEY,
                CustomerName    NVARCHAR(200) NOT NULL,
                Phone           NVARCHAR(50)  DEFAULT '',
                PartySize       INT           DEFAULT 1,
                ReservationDate DATE          NOT NULL,
                ReservationTime NVARCHAR(10)  DEFAULT '19:00',
                TableId         INT           NULL,
                Status          NVARCHAR(50)  DEFAULT 'Confirmed',
                Notes           NVARCHAR(500) DEFAULT '',
                CreatedAt       DATETIME      DEFAULT GETDATE()
            )
        """.trimIndent())
    }

    private val baseSelect = """
        SELECT r.ReservationId, r.CustomerName, ISNULL(r.Phone,'') AS Phone,
               r.PartySize, r.ReservationDate, r.ReservationTime,
               r.TableId, t.TableName,
               r.Status, ISNULL(r.Notes,'') AS Notes, r.CreatedAt
        FROM Reservations r
        LEFT JOIN Tables t ON t.TableId = r.TableId
    """.trimIndent()

    suspend fun getUpcoming(days: Int = 14): List<Reservation> = db.query(
        "$baseSelect WHERE r.ReservationDate >= CAST(GETDATE() AS DATE) AND r.ReservationDate <= DATEADD(day,?,CAST(GETDATE() AS DATE)) AND r.Status NOT IN ('Cancelled','NoShow') ORDER BY r.ReservationDate, r.ReservationTime",
        listOf(days), ::mapRow
    )

    suspend fun getByDate(date: java.util.Date): List<Reservation> = db.query(
        "$baseSelect WHERE r.ReservationDate = ? ORDER BY r.ReservationTime",
        listOf(java.sql.Date(date.time)), ::mapRow
    )

    suspend fun getAll(): List<Reservation> = db.query(
        "$baseSelect ORDER BY r.ReservationDate DESC, r.ReservationTime", mapper = ::mapRow
    )

    suspend fun save(r: Reservation): Int =
        if (r.reservationId == 0) {
            val newId = db.insertAndGetId(
                "INSERT INTO Reservations (CustomerName, Phone, PartySize, ReservationDate, ReservationTime, TableId, Status, Notes) VALUES (?,?,?,?,?,?,?,?)",
                listOf(r.customerName, r.phone, r.partySize, java.sql.Date(r.reservationDate.time),
                    r.reservationTime, r.tableId, r.status, r.notes)
            )
            runCatching { audit.writeAudit(0, "INSERT", "Reservations", newId) }
            newId
        } else {
            db.execute(
                "UPDATE Reservations SET CustomerName=?, Phone=?, PartySize=?, ReservationDate=?, ReservationTime=?, TableId=?, Status=?, Notes=? WHERE ReservationId=?",
                listOf(r.customerName, r.phone, r.partySize, java.sql.Date(r.reservationDate.time),
                    r.reservationTime, r.tableId, r.status, r.notes, r.reservationId)
            )
            runCatching { audit.writeAudit(0, "UPDATE", "Reservations", r.reservationId) }
            r.reservationId
        }

    suspend fun delete(reservationId: Int) {
        db.execute("DELETE FROM Reservations WHERE ReservationId = ?", listOf(reservationId))
        runCatching { audit.writeAudit(0, "DELETE", "Reservations", reservationId) }
    }

    suspend fun updateStatus(reservationId: Int, status: String) =
        db.execute("UPDATE Reservations SET Status=? WHERE ReservationId=?", listOf(status, reservationId))

    private fun mapRow(rs: java.sql.ResultSet) = Reservation(
        reservationId   = rs.getInt("ReservationId"),
        customerName    = rs.getString("CustomerName") ?: "",
        phone           = rs.getString("Phone") ?: "",
        partySize       = rs.getInt("PartySize"),
        reservationDate = rs.getDate("ReservationDate") ?: java.util.Date(),
        reservationTime = rs.getString("ReservationTime") ?: "",
        tableId         = rs.getInt("TableId").takeIf { !rs.wasNull() },
        tableName       = rs.getString("TableName"),
        status          = rs.getString("Status") ?: "Confirmed",
        notes           = rs.getString("Notes") ?: "",
        createdAt       = rs.getTimestamp("CreatedAt") ?: java.util.Date()
    )
}
