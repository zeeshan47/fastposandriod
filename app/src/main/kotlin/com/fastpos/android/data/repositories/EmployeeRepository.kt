package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Employee
import com.fastpos.android.data.models.EmployeeAdvance
import com.fastpos.android.data.models.SalaryPayment
import java.sql.ResultSet
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmployeeRepository @Inject constructor(
    private val db: DatabaseHelper,
    private val audit: AuditLogRepository
) {

    suspend fun initHrSchema() {
        // Employees table already exists in WPF SQL Server DB; standalone handled by LocalSchemaHelper
    }

    suspend fun getEmployees(): List<Employee> = db.query(
        """SELECT EmployeeId, EmployeeName, ISNULL(Phone,'') AS Phone,
                  ISNULL(EmployeeRole,'') AS EmployeeRole, JoiningDate,
                  ISNULL(MonthlySalary,0) AS MonthlySalary, IsActive,
                  ISNULL(BranchId,1) AS BranchId
           FROM Employees
           ORDER BY EmployeeName"""
    ) { rs -> mapEmployee(rs) }

    suspend fun addEmployee(
        name: String, phone: String = "", role: String = "",
        joiningDate: java.util.Date? = null, monthlySalary: Double = 0.0,
        branchId: Int = 1, createdBy: Int = 0
    ): Int {
        val id = db.insertAndGetId(
            """INSERT INTO Employees
                   (EmployeeName, Phone, EmployeeRole, JoiningDate, MonthlySalary, IsActive, BranchId, CreatedBy)
               VALUES (?, ?, ?, ?, ?, 1, ?, ?)""",
            listOf(
                name,
                phone.ifBlank { null },
                role.ifBlank { null },
                if (joiningDate != null) java.sql.Timestamp(joiningDate.time) else null,
                monthlySalary,
                branchId,
                createdBy
            )
        )
        if (id > 0) runCatching { audit.writeAudit(createdBy, "INSERT", "Employees", id) }
        return id
    }

    suspend fun updateEmployee(
        employeeId: Int, name: String, phone: String = "", role: String = "",
        joiningDate: java.util.Date? = null, monthlySalary: Double = 0.0, isActive: Boolean = true
    ) {
        db.execute(
            """UPDATE Employees
               SET EmployeeName   = ?,
                   Phone          = ?,
                   EmployeeRole   = ?,
                   JoiningDate    = ?,
                   MonthlySalary  = ?,
                   IsActive       = ?
               WHERE EmployeeId = ?""",
            listOf(
                name,
                phone.ifBlank { null },
                role.ifBlank { null },
                if (joiningDate != null) java.sql.Timestamp(joiningDate.time) else null,
                monthlySalary,
                if (isActive) 1 else 0,
                employeeId
            )
        )
        runCatching { audit.writeAudit(0, "UPDATE", "Employees", employeeId) }
    }

    suspend fun toggleActive(employeeId: Int, isActive: Boolean) {
        db.execute(
            "UPDATE Employees SET IsActive=? WHERE EmployeeId=?",
            listOf(if (isActive) 1 else 0, employeeId)
        )
    }

    suspend fun deleteEmployee(employeeId: Int, deletedBy: Int = 0) {
        db.execute(
            "UPDATE Employees SET IsActive=0 WHERE EmployeeId=?",
            listOf(employeeId)
        )
        runCatching { audit.writeAudit(deletedBy, "DELETE", "Employees", employeeId) }
    }

    private fun mapEmployee(rs: ResultSet) = Employee(
        employeeId    = rs.getInt("EmployeeId"),
        employeeName  = rs.getString("EmployeeName") ?: "",
        phone         = try { rs.getString("Phone") ?: "" } catch (_: Exception) { "" },
        employeeRole  = try { rs.getString("EmployeeRole") ?: "" } catch (_: Exception) { "" },
        joiningDate   = try { rs.getTimestamp("JoiningDate") } catch (_: Exception) { null },
        monthlySalary = try { rs.getDouble("MonthlySalary") } catch (_: Exception) { 0.0 },
        isActive      = rs.getBoolean("IsActive"),
        branchId      = try { rs.getInt("BranchId") } catch (_: Exception) { 1 }
    )

    // ── Employee Advances ──────────────────────────────────────────────────────

    suspend fun getAdvances(employeeId: Int? = null, from: Date? = null, to: Date? = null): List<EmployeeAdvance> = try {
        val sql = buildString {
            append("""
                SELECT a.AdvanceId, a.EmployeeId, e.EmployeeName, a.Amount, a.AdvanceDate,
                       ISNULL(a.Notes,'') AS Notes,
                       ISNULL(a.PaymentMethod,'Cash') AS PaymentMethod
                FROM EmployeeAdvances a
                JOIN Employees e ON e.EmployeeId = a.EmployeeId
                WHERE a.IsActive = 1
            """)
            if (employeeId != null) append(" AND a.EmployeeId = ?")
            if (from != null)       append(" AND a.AdvanceDate >= ?")
            if (to != null)         append(" AND a.AdvanceDate <= ?")
            append(" ORDER BY a.AdvanceDate DESC, a.AdvanceId DESC")
        }
        val params = mutableListOf<Any?>()
        if (employeeId != null) params.add(employeeId)
        if (from != null)       params.add(java.sql.Timestamp(from.time))
        if (to != null)         params.add(java.sql.Timestamp(to.time))
        db.query(sql, params) { rs ->
            EmployeeAdvance(
                advanceId     = rs.getInt("AdvanceId"),
                employeeId    = rs.getInt("EmployeeId"),
                employeeName  = rs.getString("EmployeeName") ?: "",
                amount        = rs.getDouble("Amount"),
                advanceDate   = rs.getTimestamp("AdvanceDate") ?: Date(),
                notes         = rs.getString("Notes") ?: "",
                paymentMethod = try { rs.getString("PaymentMethod") ?: "Cash" } catch (_: Exception) { "Cash" }
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun saveAdvance(
        employeeId: Int, amount: Double, notes: String,
        date: Date = Date(), paymentMethod: String = "Cash", shiftId: Int? = null,
        branchId: Int = 1, createdBy: Int = 0
    ): Int = db.transaction { conn ->
        val id = db.insertAndGetIdSync(conn,
            "INSERT INTO EmployeeAdvances (EmployeeId, BranchId, AdvanceDate, Amount, Notes, PaymentMethod, ShiftId, CreatedBy) VALUES (?,?,?,?,?,?,?,?)",
            listOf(employeeId, branchId, java.sql.Timestamp(date.time), amount, notes.ifBlank { null }, paymentMethod, shiftId, createdBy)
        )
        if (id > 0 && paymentMethod.equals("Cash", ignoreCase = true) && shiftId != null) {
            runCatching {
                db.executeSync(conn,
                    "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt) VALUES (?,?,'Out',?,'Employee Advance',?,?,GETDATE())",
                    listOf(shiftId, branchId, amount, if (notes.isBlank()) "Advance to employee #$employeeId" else notes, createdBy)
                )
            }
        }
        if (id > 0) runCatching { audit.writeAudit(createdBy, "INSERT", "EmployeeAdvances", id) }
        // Accounting: Debit Salary/Wages / Credit Cash for cash advances
        if (id > 0 && paymentMethod.equals("Cash", ignoreCase = true)) runCatching {
            val dId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='5200' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
            val cId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='1000' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
            if (dId > 0 && cId > 0) {
                val sql = "INSERT INTO AccountLedger (AccountId,EntryDate,Debit,Credit,ReferenceType,ReferenceId,Narration,CreatedBy,CreatedAt) VALUES (?,GETDATE(),?,?,?,?,?,?,GETDATE())"
                db.execute(sql, listOf(dId, amount, 0.0,    "Advance", id, "Employee advance #$employeeId", createdBy))
                db.execute(sql, listOf(cId, 0.0,    amount, "Advance", id, "Employee advance #$employeeId", createdBy))
            }
        }
        id
    }

    suspend fun deleteAdvance(advanceId: Int) {
        db.transaction { conn ->
            data class AdvRec(val amount: Double, val shiftId: Int?, val method: String, val branchId: Int)
            val adv = try {
                db.querySync(conn,
                    "SELECT Amount, ShiftId, ISNULL(PaymentMethod,'Cash') AS PaymentMethod, ISNULL(BranchId,1) AS BranchId FROM EmployeeAdvances WHERE AdvanceId = ?",
                    listOf(advanceId)
                ) { rs ->
                    AdvRec(
                        rs.getDouble("Amount"),
                        try { rs.getInt("ShiftId").takeIf { !rs.wasNull() } } catch (_: Exception) { null },
                        rs.getString("PaymentMethod") ?: "Cash",
                        try { rs.getInt("BranchId") } catch (_: Exception) { 1 }
                    )
                }.firstOrNull()
            } catch (_: Exception) { null }

            db.executeSync(conn, "UPDATE EmployeeAdvances SET IsActive = 0 WHERE AdvanceId = ?", listOf(advanceId))
            runCatching { audit.writeAudit(0, "DELETE", "EmployeeAdvances", advanceId) }

            if (adv?.method?.equals("Cash", ignoreCase = true) == true && adv.shiftId != null) {
                try {
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedAt) VALUES (?,?,'In',?,'Advance Deleted','Reversal of deleted advance',GETDATE())",
                        listOf(adv.shiftId, adv.branchId, adv.amount)
                    )
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun getTotalActiveAdvances(employeeId: Int): Double = try {
        db.queryOne(
            "SELECT ISNULL(SUM(Amount),0) AS Total FROM EmployeeAdvances WHERE EmployeeId = ? AND IsActive = 1",
            listOf(employeeId)
        ) { it.getDouble("Total") } ?: 0.0
    } catch (_: Exception) { 0.0 }

    // ── Salary Payments ────────────────────────────────────────────────────────

    suspend fun getSalaryPayments(employeeId: Int? = null, from: Date? = null, to: Date? = null): List<SalaryPayment> = try {
        val sql = buildString {
            append("""
                SELECT p.PaymentId, p.EmployeeId, e.EmployeeName, p.Amount, p.PaymentDate,
                       p.PeriodMonth, p.PeriodYear,
                       ISNULL(p.PaymentMethod,'Cash') AS PaymentMethod,
                       ISNULL(p.Notes,'') AS Notes
                FROM SalaryPayments p
                JOIN Employees e ON e.EmployeeId = p.EmployeeId
                WHERE p.IsActive = 1
            """)
            if (employeeId != null) append(" AND p.EmployeeId = ?")
            if (from != null)       append(" AND p.PaymentDate >= ?")
            if (to != null)         append(" AND p.PaymentDate <= ?")
            append(" ORDER BY p.PaymentDate DESC, p.PaymentId DESC")
        }
        val params = mutableListOf<Any?>()
        if (employeeId != null) params.add(employeeId)
        if (from != null)       params.add(java.sql.Timestamp(from.time))
        if (to != null)         params.add(java.sql.Timestamp(to.time))
        db.query(sql, params) { rs ->
            SalaryPayment(
                paymentId     = rs.getInt("PaymentId"),
                employeeId    = rs.getInt("EmployeeId"),
                employeeName  = rs.getString("EmployeeName") ?: "",
                amount        = rs.getDouble("Amount"),
                paymentDate   = rs.getTimestamp("PaymentDate") ?: Date(),
                periodMonth   = rs.getInt("PeriodMonth"),
                periodYear    = rs.getInt("PeriodYear"),
                paymentMethod = rs.getString("PaymentMethod") ?: "Cash",
                notes         = rs.getString("Notes") ?: ""
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun saveSalaryPayment(
        employeeId: Int, amount: Double,
        periodMonth: Int, periodYear: Int,
        method: String, notes: String,
        shiftId: Int? = null, branchId: Int = 1
    ): Int = db.transaction { conn ->
        val already = db.querySync(conn,
            "SELECT COUNT(*) AS C FROM SalaryPayments WHERE EmployeeId=? AND BranchId=? AND PeriodMonth=? AND PeriodYear=? AND IsActive=1",
            listOf(employeeId, branchId, periodMonth, periodYear)
        ) { it.getInt("C") }.firstOrNull() ?: 0
        if (already > 0) throw IllegalStateException("Salary already paid for this period")

        val id = db.insertAndGetIdSync(conn,
            "INSERT INTO SalaryPayments (EmployeeId, Amount, PeriodMonth, PeriodYear, PaymentMethod, Notes, ShiftId, BranchId) VALUES (?,?,?,?,?,?,?,?)",
            listOf(employeeId, amount, periodMonth, periodYear, method, notes.ifBlank { null }, shiftId, branchId)
        )
        if (id > 0 && method.equals("Cash", ignoreCase = true) && shiftId != null) {
            runCatching {
                db.executeSync(conn,
                    "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedAt) VALUES (?,?,'Out',?,'Salary Payment',?,GETDATE())",
                    listOf(shiftId, branchId, amount, if (notes.isBlank()) "Salary payment #$employeeId" else notes)
                )
            }
        }
        if (id > 0) runCatching { audit.writeAudit(0, "INSERT", "SalaryPayments", id) }
        // Accounting: Debit Salary/Wages / Credit Cash for cash salary payments
        if (id > 0 && method.equals("Cash", ignoreCase = true)) runCatching {
            val dId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='5200' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
            val cId = db.queryOne("SELECT AccountId FROM ChartOfAccounts WHERE AccountCode='1000' AND IsActive=1", emptyList()) { it.getInt("AccountId") } ?: 0
            if (dId > 0 && cId > 0) {
                val sql = "INSERT INTO AccountLedger (AccountId,EntryDate,Debit,Credit,ReferenceType,ReferenceId,Narration,CreatedBy,CreatedAt) VALUES (?,GETDATE(),?,?,?,?,?,?,GETDATE())"
                db.execute(sql, listOf(dId, amount, 0.0,    "Salary", id, "Salary payment #$employeeId", 0))
                db.execute(sql, listOf(cId, 0.0,    amount, "Salary", id, "Salary payment #$employeeId", 0))
            }
        }
        id
    }

    suspend fun deleteSalaryPayment(paymentId: Int) {
        db.transaction { conn ->
            data class PayRec(val method: String, val shiftId: Int?, val amount: Double, val branchId: Int)
            val pay = try {
                db.querySync(conn,
                    "SELECT PaymentMethod, ShiftId, Amount, ISNULL(BranchId,1) AS BranchId FROM SalaryPayments WHERE PaymentId = ?",
                    listOf(paymentId)
                ) { rs ->
                    PayRec(
                        rs.getString("PaymentMethod") ?: "Cash",
                        try { rs.getInt("ShiftId").takeIf { !rs.wasNull() } } catch (_: Exception) { null },
                        rs.getDouble("Amount"),
                        try { rs.getInt("BranchId") } catch (_: Exception) { 1 }
                    )
                }.firstOrNull()
            } catch (_: Exception) { null }

            db.executeSync(conn, "UPDATE SalaryPayments SET IsActive = 0 WHERE PaymentId = ?", listOf(paymentId))
            runCatching { audit.writeAudit(0, "DELETE", "SalaryPayments", paymentId) }

            if (pay?.method?.equals("Cash", ignoreCase = true) == true && pay.shiftId != null) {
                try {
                    db.executeSync(conn,
                        "INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedAt) VALUES (?,?,'In',?,'Salary Payment Deleted','Reversal of deleted salary payment',GETDATE())",
                        listOf(pay.shiftId, pay.branchId, pay.amount)
                    )
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun getEmployeeSalary(employeeId: Int): Double = try {
        db.queryOne(
            "SELECT ISNULL(MonthlySalary, 0) AS MonthlySalary FROM Employees WHERE EmployeeId = ?",
            listOf(employeeId)
        ) { it.getDouble("MonthlySalary") } ?: 0.0
    } catch (_: Exception) { 0.0 }

    suspend fun getSalaryPaidForMonth(employeeId: Int, month: Int, year: Int): Double = try {
        db.queryOne(
            "SELECT ISNULL(SUM(Amount),0) AS Total FROM SalaryPayments WHERE EmployeeId=? AND PeriodMonth=? AND PeriodYear=? AND IsActive=1",
            listOf(employeeId, month, year)
        ) { it.getDouble("Total") } ?: 0.0
    } catch (_: Exception) { 0.0 }
}
