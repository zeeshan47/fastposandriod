package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.TaxRate
import com.fastpos.android.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaxRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val audit:   AuditLogRepository,
    private val session: SessionManager
) {

    suspend fun initSchema() = withContext(Dispatchers.IO) {
        db.execute("""
            IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'Taxes')
            CREATE TABLE Taxes (
                TaxId      INT IDENTITY(1,1) PRIMARY KEY,
                TaxName    NVARCHAR(100) NOT NULL,
                TaxPercent DECIMAL(5,2)  NOT NULL DEFAULT 0,
                IsActive   BIT           NOT NULL DEFAULT 1,
                CreatedAt  DATETIME               DEFAULT GETDATE()
            )
        """.trimIndent())
    }

    suspend fun getAllTaxes(): List<TaxRate> = db.query(
        "SELECT TaxId, TaxName, TaxPercent, IsActive FROM Taxes ORDER BY TaxName"
    ) { rs ->
        TaxRate(
            taxId      = rs.getInt("TaxId"),
            taxName    = rs.getString("TaxName") ?: "",
            taxPercent = rs.getDouble("TaxPercent"),
            isActive   = rs.getBoolean("IsActive")
        )
    }

    suspend fun addTax(name: String, percent: Double): Int {
        val id = db.insertAndGetId("INSERT INTO Taxes (TaxName, TaxPercent) VALUES (?,?)", listOf(name.trim(), percent))
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "INSERT", "Taxes", id) }
        return id
    }

    suspend fun updateTax(taxId: Int, name: String, percent: Double, isActive: Boolean) {
        db.execute("UPDATE Taxes SET TaxName=?, TaxPercent=?, IsActive=? WHERE TaxId=?",
            listOf(name.trim(), percent, isActive, taxId))
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "UPDATE", "Taxes", taxId) }
    }

    suspend fun deleteTax(taxId: Int): Boolean {
        val inUse = db.queryOne(
            "SELECT COUNT(1) AS c FROM Products WHERE TaxId = ? AND IsActive = 1",
            listOf(taxId)
        ) { it.getInt("c") } ?: 0
        if (inUse > 0) return false
        db.execute("UPDATE Taxes SET IsActive = 0 WHERE TaxId = ?", listOf(taxId))
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "DELETE", "Taxes", taxId) }
        return true
    }
}
