package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Waiter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaiterRepository @Inject constructor(
    private val db:    DatabaseHelper,
    private val audit: AuditLogRepository
) {

    suspend fun getAllWaiters(): List<Waiter> = try {
        db.query(
            """SELECT w.WaiterId, w.WaiterName, ISNULL(w.Phone,'') AS Phone, w.IsActive,
                      w.AreaId, w.EmployeeId, ISNULL(u.FullName,'') AS LinkedEmployeeName
               FROM Waiters w
               LEFT JOIN Users u ON u.UserId = w.EmployeeId
               ORDER BY w.WaiterName"""
        ) { rs ->
            Waiter(
                waiterId           = rs.getInt("WaiterId"),
                waiterName         = rs.getString("WaiterName") ?: "",
                phone              = rs.getString("Phone") ?: "",
                isActive           = rs.getBoolean("IsActive"),
                areaId             = rs.getInt("AreaId").takeIf { !rs.wasNull() },
                linkedEmployeeId   = rs.getInt("EmployeeId").takeIf { !rs.wasNull() },
                linkedEmployeeName = rs.getString("LinkedEmployeeName") ?: ""
            )
        }
    } catch (_: Exception) {
        db.query(
            "SELECT WaiterId, WaiterName, ISNULL(Phone,'') AS Phone, IsActive FROM Waiters ORDER BY WaiterName"
        ) { rs ->
            Waiter(
                waiterId   = rs.getInt("WaiterId"),
                waiterName = rs.getString("WaiterName") ?: "",
                phone      = rs.getString("Phone") ?: "",
                isActive   = rs.getBoolean("IsActive")
            )
        }
    }

    suspend fun getActiveEmployees(): List<Pair<Int, String>> = try {
        db.query(
            "SELECT EmployeeId, EmployeeName FROM Employees WHERE IsActive = 1 ORDER BY EmployeeName"
        ) { rs -> Pair(rs.getInt("EmployeeId"), rs.getString("EmployeeName") ?: "") }
    } catch (_: Exception) { emptyList() }

    suspend fun addWaiter(name: String, phone: String, linkedEmployeeId: Int? = null, areaId: Int? = null, createdBy: Int = 0): Int {
        val id = db.insertAndGetId(
            "INSERT INTO Waiters (WaiterName, Phone, AreaId, EmployeeId, IsActive, CreatedAt, CreatedBy) VALUES (?, ?, ?, ?, 1, GETDATE(), ?)",
            listOf(name.trim(), phone.trim().ifBlank { null }, areaId, linkedEmployeeId, createdBy)
        )
        runCatching { audit.writeAudit(createdBy, "INSERT", "Waiters", id) }
        return id
    }

    suspend fun updateWaiter(waiterId: Int, name: String, phone: String, isActive: Boolean, linkedEmployeeId: Int? = null, areaId: Int? = null) {
        db.execute(
            "UPDATE Waiters SET WaiterName=?, Phone=?, IsActive=?, EmployeeId=?, AreaId=? WHERE WaiterId=?",
            listOf(name.trim(), phone.trim().ifBlank { null }, if (isActive) 1 else 0, linkedEmployeeId, areaId, waiterId)
        )
        runCatching { audit.writeAudit(0, "UPDATE", "Waiters", waiterId) }
    }

    suspend fun deleteWaiter(waiterId: Int) {
        db.execute("UPDATE Waiters SET IsActive=0 WHERE WaiterId=?", listOf(waiterId))
        runCatching { audit.writeAudit(0, "DELETE", "Waiters", waiterId) }
    }

    suspend fun isLinkedToEmployee(employeeId: Int): Boolean = try {
        (db.queryOne(
            "SELECT COUNT(1) AS cnt FROM Waiters WHERE EmployeeId = ?",
            listOf(employeeId)
        ) { it.getInt("cnt") } ?: 0) > 0
    } catch (_: Exception) { false }

    suspend fun createLinkedToEmployee(employeeId: Int, name: String, phone: String, createdBy: Int = 0): Int =
        db.insertAndGetId(
            "INSERT INTO Waiters (WaiterName, Phone, AreaId, EmployeeId, IsActive, CreatedAt, CreatedBy) VALUES (?, ?, ?, ?, 1, GETDATE(), ?)",
            listOf(name.trim(), phone.trim().ifBlank { null }, null, employeeId, createdBy)
        )
}
