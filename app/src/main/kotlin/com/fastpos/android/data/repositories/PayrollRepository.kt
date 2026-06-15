package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.EmployeeAdvance
import com.fastpos.android.data.models.EmployeeSalaryInfo
import com.fastpos.android.data.models.PayrollRecord
import com.fastpos.android.data.models.PayrollRow
import com.fastpos.android.utils.SessionManager
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PayrollRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val audit:   AuditLogRepository,
    private val session: SessionManager
) {

    suspend fun initSchema() {
        // SalaryPayments
        runCatching {
            db.execute("""
                IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'SalaryPayments')
                CREATE TABLE SalaryPayments (
                    PaymentId     INT IDENTITY(1,1) PRIMARY KEY,
                    EmployeeId    INT NOT NULL,
                    BranchId      INT NOT NULL DEFAULT 1,
                    PaymentDate   DATE NOT NULL DEFAULT GETDATE(),
                    Amount        DECIMAL(18,2) NOT NULL DEFAULT 0,
                    PeriodMonth   INT NOT NULL,
                    PeriodYear    INT NOT NULL,
                    PaymentMethod NVARCHAR(50) DEFAULT 'Cash',
                    Notes         NVARCHAR(200) DEFAULT '',
                    ShiftId       INT NULL,
                    IsActive      BIT DEFAULT 1,
                    CreatedBy     INT NULL,
                    CreatedAt     DATETIME DEFAULT GETDATE()
                )
            """.trimIndent())
        }
        // EmployeeAdvances
        runCatching {
            db.execute("""
                IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'EmployeeAdvances')
                CREATE TABLE EmployeeAdvances (
                    AdvanceId     INT IDENTITY(1,1) PRIMARY KEY,
                    EmployeeId    INT NOT NULL,
                    BranchId      INT NOT NULL DEFAULT 1,
                    AdvanceDate   DATE NOT NULL DEFAULT GETDATE(),
                    Amount        DECIMAL(18,2) NOT NULL DEFAULT 0,
                    Notes         NVARCHAR(200) DEFAULT '',
                    PaymentMethod NVARCHAR(50) DEFAULT 'Cash',
                    ShiftId       INT NULL,
                    IsActive      BIT DEFAULT 1,
                    CreatedBy     INT NULL,
                    CreatedAt     DATETIME DEFAULT GETDATE()
                )
            """.trimIndent())
        }
        // EmployeeSalaries — avoids ALTER TABLE Users
        runCatching {
            db.execute("""
                IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'EmployeeSalaries')
                CREATE TABLE EmployeeSalaries (
                    UserId        INT PRIMARY KEY,
                    MonthlySalary DECIMAL(18,2) NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }
    }

    suspend fun getActiveEmployees(): List<EmployeeSalaryInfo> = try {
        db.query(
            """SELECT EmployeeId AS UserId, EmployeeName AS FullName,
                      ISNULL(EmployeeRole,'') AS RoleName,
                      ISNULL(MonthlySalary, 0) AS BasicSalary, IsActive
               FROM Employees
               WHERE IsActive = 1
               UNION ALL
               SELECT u.UserId, u.Username AS FullName,
                      ISNULL(r.RoleName,'') AS RoleName,
                      ISNULL(es.MonthlySalary, 0) AS BasicSalary, u.IsActive
               FROM Users u
               LEFT JOIN Roles r ON r.RoleId = u.RoleId
               LEFT JOIN EmployeeSalaries es ON es.UserId = u.UserId
               WHERE u.IsActive = 1
                 AND u.UserId NOT IN (SELECT EmployeeId FROM Employees WHERE IsActive = 1)
               ORDER BY FullName"""
        ) { rs ->
            EmployeeSalaryInfo(
                userId      = rs.getInt("UserId"),
                fullName    = rs.getString("FullName") ?: "",
                roleName    = rs.getString("RoleName") ?: "",
                basicSalary = try { rs.getDouble("BasicSalary") } catch (_: Exception) { 0.0 },
                isActive    = rs.getBoolean("IsActive")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getSalaryPaidForMonth(employeeId: Int, month: Int, year: Int): Double =
        db.query(
            "SELECT ISNULL(SUM(Amount),0) AS Total FROM SalaryPayments WHERE EmployeeId=? AND PeriodMonth=? AND PeriodYear=? AND IsActive=1",
            listOf(employeeId, month, year)
        ) { rs -> rs.getDouble("Total") }.firstOrNull() ?: 0.0

    suspend fun getAdvancesForMonth(employeeId: Int, monthStart: java.sql.Date, monthEnd: java.sql.Date): Double =
        db.query(
            "SELECT ISNULL(SUM(Amount),0) AS Total FROM EmployeeAdvances WHERE EmployeeId=? AND AdvanceDate>=? AND AdvanceDate<=? AND IsActive=1",
            listOf(employeeId, monthStart, monthEnd)
        ) { rs -> rs.getDouble("Total") }.firstOrNull() ?: 0.0

    suspend fun getAdvanceHistory(employeeId: Int? = null): List<EmployeeAdvance> {
        val sql = if (employeeId != null)
            """SELECT ea.*, e.EmployeeName FROM EmployeeAdvances ea
               JOIN Employees e ON e.EmployeeId = ea.EmployeeId
               WHERE ea.IsActive=1 AND ea.EmployeeId=? ORDER BY ea.AdvanceDate DESC, ea.AdvanceId DESC"""
        else
            """SELECT ea.*, e.EmployeeName FROM EmployeeAdvances ea
               JOIN Employees e ON e.EmployeeId = ea.EmployeeId
               WHERE ea.IsActive=1 ORDER BY ea.AdvanceDate DESC, ea.AdvanceId DESC"""
        val params = if (employeeId != null) listOf(employeeId) else emptyList()
        return db.query(sql, params) { rs ->
            EmployeeAdvance(
                advanceId     = rs.getInt("AdvanceId"),
                employeeId    = rs.getInt("EmployeeId"),
                employeeName  = rs.getString("EmployeeName") ?: "",
                advanceDate   = rs.getDate("AdvanceDate") ?: Date(),
                amount        = rs.getDouble("Amount"),
                notes         = rs.getString("Notes") ?: "",
                paymentMethod = try { rs.getString("PaymentMethod") ?: "Cash" } catch (_: Exception) { "Cash" },
                createdAt     = rs.getTimestamp("CreatedAt") ?: Date()
            )
        }
    }

    suspend fun recordSalaryPayment(
        employeeId: Int, amount: Double, month: Int, year: Int,
        notes: String, createdBy: Int, shiftId: Int?
    ) {
        if (amount <= 0) throw IllegalArgumentException("Payment amount must be greater than zero.")
        var newPaymentId = 0
        db.transaction { conn ->
            val alreadyPaid = db.querySync(conn,
                "SELECT ISNULL(SUM(Amount),0) AS Total FROM SalaryPayments WHERE EmployeeId=? AND PeriodMonth=? AND PeriodYear=? AND IsActive=1",
                listOf(employeeId, month, year)
            ) { rs -> rs.getDouble("Total") }.firstOrNull() ?: 0.0

            if (alreadyPaid > 0)
                throw IllegalStateException("Salary already recorded for this period (paid: ${alreadyPaid.toLong()}).")

            val id = db.insertAndGetIdSync(conn,
                """INSERT INTO SalaryPayments
                       (EmployeeId, BranchId, PaymentDate, Amount, PeriodMonth, PeriodYear, PaymentMethod, Notes, ShiftId, CreatedBy)
                   VALUES (?, ?, GETDATE(), ?, ?, ?, 'Cash', ?, ?, ?)""",
                listOf(employeeId, session.currentBranchId.value, amount, month, year, notes, shiftId, createdBy)
            )
            newPaymentId = id
            if (id > 0 && shiftId != null) {
                runCatching {
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'Out',?,'Salary Payment',?,?,GETDATE())",
                        listOf(shiftId, session.currentBranchId.value, amount, if (notes.isBlank()) "Salary payment #$employeeId" else notes, createdBy)
                    )
                }.onFailure {
                    // Fallback without BranchId for older SQL Server installs
                    runCatching {
                        db.executeSync(conn,
                            "INSERT INTO CashTransactions (ShiftId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,'Out',?,'Salary Payment',?,?,GETDATE())",
                            listOf(shiftId, amount, if (notes.isBlank()) "Salary payment #$employeeId" else notes, createdBy)
                        )
                    }
                }
            }
        }
        runCatching { audit.writeAudit(createdBy, "INSERT", "SalaryPayments", newPaymentId) }
    }

    suspend fun recordAdvance(
        employeeId: Int, amount: Double, notes: String,
        createdBy: Int, shiftId: Int?, paymentMethod: String = "Cash"
    ) {
        // Try insert with PaymentMethod column; fall back for SQL Server without it
        val advanceId = runCatching {
            db.insertAndGetId(
                """INSERT INTO EmployeeAdvances (EmployeeId, BranchId, AdvanceDate, Amount, Notes, PaymentMethod, ShiftId, CreatedBy)
                   VALUES (?, ?, GETDATE(), ?, ?, ?, ?, ?)""",
                listOf(employeeId, session.currentBranchId.value, amount, notes, paymentMethod, shiftId, createdBy)
            )
        }.getOrElse {
            db.insertAndGetId(
                """INSERT INTO EmployeeAdvances (EmployeeId, BranchId, AdvanceDate, Amount, Notes, ShiftId, CreatedBy)
                   VALUES (?, ?, GETDATE(), ?, ?, ?, ?)""",
                listOf(employeeId, session.currentBranchId.value, amount, notes, shiftId, createdBy)
            )
        }
        if (paymentMethod.equals("Cash", ignoreCase = true) && shiftId != null) {
            runCatching {
                db.execute(
                    "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'Out',?,'Employee Advance',?,?,GETDATE())",
                    listOf(shiftId, session.currentBranchId.value, amount, if (notes.isBlank()) "Advance for employee #$employeeId" else notes.take(200), createdBy)
                )
            }.onFailure {
                runCatching {
                    db.execute(
                        "INSERT INTO CashTransactions (ShiftId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,'Out',?,'Employee Advance',?,?,GETDATE())",
                        listOf(shiftId, amount, if (notes.isBlank()) "Advance for employee #$employeeId" else notes.take(200), createdBy)
                    )
                }
            }
        }
        runCatching { audit.writeAudit(createdBy, "INSERT", "EmployeeAdvances", advanceId) }
    }

    suspend fun deleteAdvance(advanceId: Int, deletedBy: Int = 0) {
        val old = runCatching {
            db.queryOne(
                "SELECT Amount, ShiftId, ISNULL(PaymentMethod,'Cash') AS PaymentMethod FROM EmployeeAdvances WHERE AdvanceId = ? AND IsActive = 1",
                listOf(advanceId)
            ) { rs -> Triple(rs.getDouble("Amount"), rs.getInt("ShiftId").takeIf { !rs.wasNull() }, try { rs.getString("PaymentMethod") ?: "Cash" } catch (_: Exception) { "Cash" }) }
        }.getOrNull()

        db.execute("UPDATE EmployeeAdvances SET IsActive=0 WHERE AdvanceId=?", listOf(advanceId))

        if (old != null && old.second != null && old.third.equals("Cash", ignoreCase = true)) {
            runCatching {
                db.execute(
                    "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'In',?,'Advance Deleted','Reversal of deleted advance',?,GETDATE())",
                    listOf(old.second, session.currentBranchId.value, old.first, deletedBy)
                )
            }
        }
        runCatching { audit.writeAudit(deletedBy, "DELETE", "EmployeeAdvances", advanceId) }
    }

    suspend fun updateEmployeeSalary(employeeId: Int, salary: Double, updatedBy: Int = 0) {
        db.execute("UPDATE Employees SET MonthlySalary=? WHERE EmployeeId=?", listOf(salary, employeeId))
        runCatching { audit.writeAudit(updatedBy, "UPDATE", "Employees", employeeId) }
    }

    // Used by reports — queries SalaryPayments grouped per employee for the month
    suspend fun getPayrollReportSummary(month: Int, year: Int): List<PayrollRecord> = try {
        db.query(
            """SELECT e.EmployeeId AS UserId, e.EmployeeName AS FullName,
                      ISNULL(e.MonthlySalary, 0) AS BasicSalary,
                      ISNULL((SELECT SUM(Amount) FROM EmployeeAdvances WHERE EmployeeId=e.EmployeeId AND IsActive=1
                              AND MONTH(AdvanceDate)=? AND YEAR(AdvanceDate)=?), 0) AS Deductions,
                      ISNULL((SELECT SUM(Amount) FROM SalaryPayments WHERE EmployeeId=e.EmployeeId AND PeriodMonth=? AND PeriodYear=? AND IsActive=1), 0) AS NetSalary
               FROM Employees e
               WHERE e.IsActive=1
               UNION ALL
               SELECT u.UserId, u.Username AS FullName,
                      ISNULL(es.MonthlySalary, 0) AS BasicSalary,
                      ISNULL((SELECT SUM(Amount) FROM EmployeeAdvances WHERE EmployeeId=u.UserId AND IsActive=1
                              AND MONTH(AdvanceDate)=? AND YEAR(AdvanceDate)=?), 0) AS Deductions,
                      ISNULL((SELECT SUM(Amount) FROM SalaryPayments WHERE EmployeeId=u.UserId AND PeriodMonth=? AND PeriodYear=? AND IsActive=1), 0) AS NetSalary
               FROM Users u
               LEFT JOIN EmployeeSalaries es ON es.UserId = u.UserId
               WHERE u.IsActive=1
                 AND u.UserId NOT IN (SELECT EmployeeId FROM Employees WHERE IsActive=1)
               ORDER BY FullName""",
            listOf(month, year, month, year, month, year, month, year)
        ) { rs ->
            val basic   = rs.getDouble("BasicSalary")
            val deduct  = rs.getDouble("Deductions")
            val paid    = rs.getDouble("NetSalary")
            PayrollRecord(
                userId        = rs.getInt("UserId"),
                fullName      = rs.getString("FullName") ?: "",
                payMonth      = month,
                payYear       = year,
                basicSalary   = basic,
                deductions    = deduct,
                netSalary     = paid,
                paymentStatus = if (paid >= (basic - deduct - 0.01) && basic > 0) "Paid" else "Pending"
            )
        }
    } catch (_: Exception) { emptyList() }
}
