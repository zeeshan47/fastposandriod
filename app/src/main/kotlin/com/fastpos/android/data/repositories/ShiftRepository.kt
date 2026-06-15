package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Shift
import com.fastpos.android.data.models.ShiftCashTotals
import com.fastpos.android.data.models.ShiftPaymentSummary
import com.fastpos.android.utils.SessionManager
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val session: SessionManager,
    private val audit:   AuditLogRepository
) {

    suspend fun getOpenShift(userId: Int): Shift? {
        val liveSalesSub = "ISNULL((SELECT SUM(GrandTotal) FROM Orders WHERE ShiftId = s.ShiftId AND OrderStatus = 'Completed'), 0) AS LiveSales"
        return try {
            db.queryOne(
                "SELECT s.*, $liveSalesSub FROM Shifts s WHERE s.UserId = ? AND s.ShiftStatus = 'Open' AND ISNULL(s.BranchId,1) = ? ORDER BY s.OpeningTime DESC",
                listOf(userId, session.currentBranchId.value)
            ) { rs -> mapShift(rs, liveMode = true) }
        } catch (_: Exception) {
            // BranchId column may not exist on this SQL Server install
            db.queryOne(
                "SELECT s.*, $liveSalesSub FROM Shifts s WHERE s.UserId = ? AND s.ShiftStatus = 'Open' ORDER BY s.OpeningTime DESC",
                listOf(userId)
            ) { rs -> mapShift(rs, liveMode = true) }
        }
    }

    /** Find ANY open shift for this branch (e.g. opened from the WPF PC). */
    suspend fun getAnyOpenShift(): Shift? {
        val liveSalesSub = "ISNULL((SELECT SUM(GrandTotal) FROM Orders WHERE ShiftId = s.ShiftId AND OrderStatus = 'Completed'), 0) AS LiveSales"
        return try {
            db.queryOne(
                "SELECT TOP 1 s.*, $liveSalesSub FROM Shifts s WHERE s.ShiftStatus = 'Open' AND ISNULL(s.BranchId,1) = ? ORDER BY s.OpeningTime DESC",
                listOf(session.currentBranchId.value)
            ) { rs -> mapShift(rs, liveMode = true) }
        } catch (_: Exception) {
            db.queryOne(
                "SELECT TOP 1 s.*, $liveSalesSub FROM Shifts s WHERE s.ShiftStatus = 'Open' ORDER BY s.OpeningTime DESC",
                emptyList()
            ) { rs -> mapShift(rs, liveMode = true) }
        }
    }

    suspend fun getShiftById(shiftId: Int): Shift? = try {
        db.queryOne(
            "SELECT s.*, 0.0 AS LiveSales FROM Shifts s WHERE s.ShiftId = ?",
            listOf(shiftId)
        ) { rs -> mapShift(rs, liveMode = true) }
    } catch (_: Exception) { null }

    suspend fun openShift(userId: Int, openingCash: Double): Int {
        val datePart     = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val businessDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val seq = try {
            db.query(
                "SELECT ISNULL(COUNT(1),0)+1 AS Seq FROM Shifts WHERE CAST(OpeningTime AS DATE) = CAST(GETDATE() AS DATE)",
                emptyList()
            ) { rs -> rs.getInt("Seq") }.firstOrNull() ?: 1
        } catch (_: Exception) { 1 }
        val shiftCode = "SH-$datePart-%02d".format(seq)
        val shiftId = try {
            db.insertAndGetId(
                """INSERT INTO Shifts (ShiftCode, BusinessDate, UserId, OpeningCash, ShiftStatus, OpeningTime, BranchId, CreatedAt)
                   VALUES (?, ?, ?, ?, 'Open', GETDATE(), ?, GETDATE())""",
                listOf(shiftCode, businessDate, userId, openingCash, session.currentBranchId.value)
            )
        } catch (_: Exception) {
            // BranchId column may not exist on some SQL Server installs
            db.insertAndGetId(
                """INSERT INTO Shifts (ShiftCode, BusinessDate, UserId, OpeningCash, ShiftStatus, OpeningTime, CreatedAt)
                   VALUES (?, ?, ?, ?, 'Open', GETDATE(), GETDATE())""",
                listOf(shiftCode, businessDate, userId, openingCash)
            )
        }
        // Insert opening cash into CashTransactions for ledger visibility (same as WPF)
        if (openingCash > 0) {
            runCatching {
                db.insertAndGetId(
                    """INSERT INTO CashTransactions (ShiftId, BranchId, TransactionType, Amount, Reason, Notes, CreatedBy, CreatedAt)
                       VALUES (?, ?, 'In', ?, 'Opening Cash', 'Opening cash', ?, GETDATE())""",
                    listOf(shiftId, session.currentBranchId.value, openingCash, userId)
                )
            }
        }
        runCatching { audit.writeAudit(userId, "OPEN_SHIFT", "Shifts", shiftId) }
        return shiftId
    }

    suspend fun closeShift(shiftId: Int, closingCash: Double, notes: String) {
        val pendingOrders = getPendingOrderCount(shiftId)
        if (pendingOrders > 0) {
            val orderText = if (pendingOrders == 1) "order is" else "orders are"
            throw IllegalStateException("Cannot close shift: $pendingOrders pending $orderText still open.")
        }

        val params = listOf(
            closingCash,
            shiftId,
            shiftId, shiftId, shiftId,
            closingCash,
            shiftId, shiftId, shiftId,
            notes, shiftId
        )
        try {
            // CashTransactions 'Out' now captures all cash outflows (expenses, salary, advances, purchases, supplier payments)
            val expectedExpr = """
                OpeningCash
                + ISNULL((SELECT SUM(op.Amount) FROM OrderPayments op
                          JOIN Orders o ON op.OrderId = o.OrderId
                          WHERE o.ShiftId = ? AND op.PaymentMethod = 'Cash'
                            AND o.OrderStatus != 'Cancelled'), 0)
                + ISNULL((SELECT SUM(Amount) FROM CashTransactions
                          WHERE ShiftId = ? AND TransactionType = 'In'
                            AND ISNULL(Reason,'') != 'Opening Cash'), 0)
                - ISNULL((SELECT SUM(Amount) FROM CashTransactions
                          WHERE ShiftId = ? AND TransactionType = 'Out'), 0)
            """.trimIndent()
            db.execute(
                """UPDATE Shifts
                   SET ShiftStatus  = 'Closed',
                       ClosingTime  = GETDATE(),
                       ClosingCash  = ?,
                       TotalSales   = ISNULL((SELECT SUM(GrandTotal) FROM Orders
                                              WHERE ShiftId = ? AND OrderStatus = 'Completed'), 0),
                       ExpectedCash = $expectedExpr,
                       Difference   = ? - ($expectedExpr),
                       Notes        = ?
                   WHERE ShiftId = ?""",
                params
            )
        } catch (_: Exception) {
            val expectedExpr = """
                OpeningCash
                + ISNULL((SELECT SUM(op.Amount) FROM OrderPayments op
                          JOIN Orders o ON op.OrderId = o.OrderId
                          WHERE o.ShiftId = ? AND op.PaymentMethod = 'Cash'
                            AND o.OrderStatus != 'Cancelled'), 0)
                + ISNULL((SELECT SUM(Amount) FROM CashTransactions
                          WHERE ShiftId = ? AND TransactionType = 'In'
                            AND ISNULL(Reason,'') != 'Opening Cash'), 0)
                - ISNULL((SELECT SUM(Amount) FROM CashTransactions
                          WHERE ShiftId = ? AND TransactionType = 'Out'), 0)
            """.trimIndent()
            db.execute(
                """UPDATE Shifts
                   SET ShiftStatus  = 'Closed',
                       ClosingTime  = GETDATE(),
                       ClosingCash  = ?,
                       TotalSales   = ISNULL((SELECT SUM(GrandTotal) FROM Orders
                                              WHERE ShiftId = ? AND OrderStatus = 'Completed'), 0),
                       ExpectedCash = $expectedExpr,
                       Difference   = ? - ($expectedExpr),
                       Notes        = ?
                   WHERE ShiftId = ?""",
                params
            )
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "CLOSE_SHIFT", "Shifts", shiftId) }
    }

    suspend fun getPendingOrderCount(shiftId: Int): Int = runCatching {
        db.queryOne(
            "SELECT COUNT(1) AS cnt FROM Orders WHERE ShiftId = ? AND OrderStatus NOT IN ('Completed','Cancelled')",
            listOf(shiftId)
        ) { it.getInt("cnt") } ?: 0
    }.getOrDefault(0)

    /** Returns (salariesPaid, advancesPaid) for the given shift. */
    suspend fun getShiftPayrollTotals(shiftId: Int): Pair<Double, Double> {
        val salaries = try {
            db.queryOne("SELECT ISNULL(SUM(Amount),0) AS T FROM SalaryPayments WHERE ShiftId=? AND IsActive=1",
                listOf(shiftId)) { it.getDouble("T") } ?: 0.0
        } catch (_: Exception) { 0.0 }
        val advances = try {
            db.queryOne("SELECT ISNULL(SUM(Amount),0) AS T FROM EmployeeAdvances WHERE ShiftId=? AND IsActive=1",
                listOf(shiftId)) { it.getDouble("T") } ?: 0.0
        } catch (_: Exception) { 0.0 }
        return Pair(salaries, advances)
    }

    suspend fun getShiftCashTotals(shiftId: Int): ShiftCashTotals = try {
        db.query(
            """SELECT
                   ISNULL((SELECT SUM(Amount) FROM CashTransactions
                           WHERE ShiftId = ? AND TransactionType = 'In'
                             AND ISNULL(Reason,'') != 'Opening Cash'),  0) AS CashTxIn,
                   ISNULL((SELECT SUM(Amount) FROM CashTransactions
                           WHERE ShiftId = ? AND TransactionType = 'Out'), 0) AS CashTxOut""",
            listOf(shiftId, shiftId)
        ) { rs -> ShiftCashTotals(rs.getDouble("CashTxIn"), rs.getDouble("CashTxOut")) }
            .firstOrNull() ?: ShiftCashTotals()
    } catch (_: Exception) { ShiftCashTotals() }

    suspend fun getShiftPaymentSummary(shiftId: Int): List<ShiftPaymentSummary> = db.query(
        """SELECT op.PaymentMethod,
                  SUM(op.Amount) AS Amount,
                  COUNT(*)       AS TxCount
           FROM OrderPayments op
           JOIN Orders o ON op.OrderId = o.OrderId
           WHERE o.ShiftId = ?
             AND o.OrderStatus = 'Completed'
           GROUP BY op.PaymentMethod
           ORDER BY Amount DESC""",
        listOf(shiftId)
    ) { rs ->
        ShiftPaymentSummary(
            method  = rs.getString("PaymentMethod") ?: "",
            amount  = rs.getDouble("Amount"),
            txCount = rs.getInt("TxCount")
        )
    }

    private fun mapShift(rs: java.sql.ResultSet, liveMode: Boolean = false) = Shift(
        shiftId       = rs.getInt("ShiftId"),
        shiftCode     = rs.getString("ShiftCode") ?: "",
        businessDate  = rs.getDate("BusinessDate") ?: Date(),
        userId        = rs.getInt("UserId"),
        openingTime   = rs.getTimestamp("OpeningTime") ?: Date(),
        closingTime   = rs.getTimestamp("ClosingTime"),
        openingCash   = rs.getDouble("OpeningCash"),
        closingCash   = rs.getDouble("ClosingCash"),
        totalSales    = if (liveMode) try { rs.getDouble("LiveSales") } catch (_: Exception) { rs.getDouble("TotalSales") }
                        else rs.getDouble("TotalSales"),
        totalExpenses = rs.getDouble("TotalExpenses"),
        expectedCash  = rs.getDouble("ExpectedCash"),
        difference    = rs.getDouble("Difference"),
        shiftStatus   = rs.getString("ShiftStatus") ?: "Open",
        notes         = rs.getString("Notes") ?: ""
    )
}
