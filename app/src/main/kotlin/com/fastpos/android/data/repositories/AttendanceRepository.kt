package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.AttendanceMonthSummary
import com.fastpos.android.data.models.AttendanceRecord
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor(
    private val db: DatabaseHelper,
    private val audit: AuditLogRepository
) {

    suspend fun initSchema() {
        try {
            db.execute("""
                IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'EmployeeAttendance')
                CREATE TABLE EmployeeAttendance (
                    AttendanceId   INT IDENTITY(1,1) PRIMARY KEY,
                    EmployeeId     INT NOT NULL,
                    BranchId       INT NOT NULL DEFAULT 1,
                    AttendanceDate DATE NOT NULL,
                    CheckInTime    DATETIME NULL,
                    CheckOutTime   DATETIME NULL,
                    Notes          VARCHAR(200) NULL,
                    CreatedAt      DATETIME DEFAULT GETDATE(),
                    CreatedBy      INT NULL
                )
            """.trimIndent())
            try {
                db.execute("""
                    IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'UQ_EmpAtt_EmpDate')
                    ALTER TABLE EmployeeAttendance ADD CONSTRAINT UQ_EmpAtt_EmpDate UNIQUE(EmployeeId, AttendanceDate, BranchId)
                """.trimIndent())
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    suspend fun getAttendanceForDate(date: Date): List<AttendanceRecord> {
        val sqlDate = java.sql.Date(date.time)
        return db.query(
            """
            SELECT e.EmployeeId,
                   e.EmployeeName AS FullName,
                   ISNULL(e.EmployeeRole,'') AS RoleName,
                   ISNULL(a.AttendanceId, 0) AS AttendanceId,
                   a.CheckInTime,
                   a.CheckOutTime,
                   ISNULL(a.Notes,'') AS Notes
            FROM Employees e
            LEFT JOIN EmployeeAttendance a
                   ON a.EmployeeId = e.EmployeeId
                  AND CAST(a.AttendanceDate AS DATE) = CAST(? AS DATE)
            WHERE e.IsActive = 1
            ORDER BY e.EmployeeName
            """.trimIndent(),
            listOf(sqlDate)
        ) { rs ->
            AttendanceRecord(
                attendanceId  = rs.getInt("AttendanceId"),
                employeeId    = rs.getInt("EmployeeId"),
                fullName      = rs.getString("FullName") ?: "",
                roleName      = rs.getString("RoleName") ?: "",
                attendanceDate = date,
                checkInTime   = rs.getTimestamp("CheckInTime"),
                checkOutTime  = rs.getTimestamp("CheckOutTime"),
                notes         = rs.getString("Notes") ?: ""
            )
        }
    }

    suspend fun checkIn(employeeId: Int, date: Date) {
        val sqlDate = java.sql.Date(date.time)
        val existing = db.query(
            "SELECT COUNT(1) AS C FROM EmployeeAttendance WHERE EmployeeId=? AND CAST(AttendanceDate AS DATE)=CAST(? AS DATE)",
            listOf(employeeId, sqlDate)
        ) { rs -> rs.getInt("C") }.firstOrNull() ?: 0

        if (existing > 0) {
            db.execute(
                "UPDATE EmployeeAttendance SET CheckInTime=GETDATE() WHERE EmployeeId=? AND CAST(AttendanceDate AS DATE)=CAST(? AS DATE)",
                listOf(employeeId, sqlDate)
            )
        } else {
            db.execute(
                "INSERT INTO EmployeeAttendance (EmployeeId, AttendanceDate, CheckInTime) VALUES (?, ?, GETDATE())",
                listOf(employeeId, sqlDate)
            )
        }
        runCatching { audit.writeAudit(0, "INSERT", "EmployeeAttendance", employeeId) }
    }

    suspend fun checkOut(attendanceId: Int) {
        db.execute(
            "UPDATE EmployeeAttendance SET CheckOutTime=GETDATE() WHERE AttendanceId=?",
            listOf(attendanceId)
        )
    }

    suspend fun removeAttendance(employeeId: Int, date: Date) {
        val sqlDate = java.sql.Date(date.time)
        db.execute(
            "DELETE FROM EmployeeAttendance WHERE EmployeeId=? AND CAST(AttendanceDate AS DATE)=CAST(? AS DATE)",
            listOf(employeeId, sqlDate)
        )
    }

    suspend fun getMonthlyAttendanceSummary(month: Int, year: Int): List<AttendanceMonthSummary> =
        db.query(
            """
            SELECT e.EmployeeId AS UserId, e.EmployeeName AS FullName,
                   ISNULL(SUM(CASE WHEN a.CheckInTime IS NOT NULL AND a.CheckOutTime IS NOT NULL THEN 1 ELSE 0 END), 0) AS PresentDays,
                   ISNULL(SUM(CASE WHEN a.CheckInTime IS NOT NULL AND a.CheckOutTime IS NULL THEN 1 ELSE 0 END), 0) AS InProgressDays,
                   ISNULL(SUM(CASE WHEN a.AttendanceId IS NOT NULL AND a.CheckInTime IS NULL THEN 1 ELSE 0 END), 0) AS AbsentDays
            FROM Employees e
            LEFT JOIN EmployeeAttendance a
                   ON a.EmployeeId = e.EmployeeId
                  AND MONTH(a.AttendanceDate) = ? AND YEAR(a.AttendanceDate) = ?
            WHERE e.IsActive = 1
            GROUP BY e.EmployeeId, e.EmployeeName
            ORDER BY e.EmployeeName
            """.trimIndent(),
            listOf(month, year)
        ) { rs ->
            AttendanceMonthSummary(
                userId         = rs.getInt("UserId"),
                fullName       = rs.getString("FullName") ?: "",
                presentDays    = rs.getInt("PresentDays"),
                inProgressDays = rs.getInt("InProgressDays"),
                absentDays     = rs.getInt("AbsentDays")
            )
        }
}
