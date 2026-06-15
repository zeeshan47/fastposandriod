package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.StockTake
import com.fastpos.android.data.models.StockTakeItem
import java.util.Date
import javax.inject.Inject

class StockTakeRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun initSchema() {
        try {
            db.execute("""
                IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'StockTakes')
                CREATE TABLE StockTakes (
                    StockTakeId INT IDENTITY(1,1) PRIMARY KEY,
                    TakeDate    DATETIME NOT NULL DEFAULT GETDATE(),
                    Status      VARCHAR(20) NOT NULL DEFAULT 'Open',
                    Notes       NVARCHAR(500) NOT NULL DEFAULT '',
                    CreatedBy   INT NULL,
                    FinalizedAt DATETIME NULL
                )
            """.trimIndent())
        } catch (_: Exception) {}

        try {
            db.execute("""
                IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'StockTakeItems')
                CREATE TABLE StockTakeItems (
                    ItemId       INT IDENTITY(1,1) PRIMARY KEY,
                    StockTakeId  INT NOT NULL,
                    MaterialId   INT NOT NULL,
                    MaterialName NVARCHAR(200) NOT NULL DEFAULT '',
                    Unit         NVARCHAR(50)  NOT NULL DEFAULT 'kg',
                    ExpectedQty  DECIMAL(18,3) NOT NULL DEFAULT 0,
                    ActualQty    DECIMAL(18,3) NOT NULL DEFAULT 0
                )
            """.trimIndent())
        } catch (_: Exception) {}
    }

    /** Returns the currently open (in-progress) stock take, or null. */
    suspend fun getOpenStockTake(): StockTake? = try {
        db.queryOne(
            "SELECT StockTakeId, TakeDate, Status, ISNULL(Notes,'') AS Notes, CreatedBy, FinalizedAt FROM StockTakes WHERE Status = 'Open' ORDER BY TakeDate DESC"
        ) { rs ->
            StockTake(
                stockTakeId = rs.getInt("StockTakeId"),
                takeDate    = rs.getTimestamp("TakeDate") ?: Date(),
                status      = rs.getString("Status") ?: "Open",
                notes       = rs.getString("Notes") ?: "",
                createdBy   = rs.getInt("CreatedBy").takeIf { !rs.wasNull() }
            )
        }
    } catch (_: Exception) { null }

    /** Returns finalized/cancelled stock take history. */
    suspend fun getStockTakeHistory(limit: Int = 30): List<StockTake> = try {
        db.query(
            """SELECT TOP ($limit) s.StockTakeId, s.TakeDate, s.Status,
                      ISNULL(s.Notes,'') AS Notes, s.CreatedBy, s.FinalizedAt,
                      (SELECT COUNT(*) FROM StockTakeItems i WHERE i.StockTakeId = s.StockTakeId) AS ItemCount
               FROM StockTakes s
               WHERE s.Status <> 'Open'
               ORDER BY s.TakeDate DESC"""
        ) { rs ->
            StockTake(
                stockTakeId = rs.getInt("StockTakeId"),
                takeDate    = rs.getTimestamp("TakeDate") ?: Date(),
                status      = rs.getString("Status") ?: "Finalized",
                notes       = rs.getString("Notes") ?: "",
                createdBy   = rs.getInt("CreatedBy").takeIf { !rs.wasNull() },
                finalizedAt = rs.getTimestamp("FinalizedAt"),
                itemCount   = try { rs.getInt("ItemCount") } catch (_: Exception) { 0 }
            )
        }
    } catch (_: Exception) { emptyList() }

    /** Returns items for a specific stock take. */
    suspend fun getStockTakeItems(stockTakeId: Int): List<StockTakeItem> = try {
        db.query(
            """SELECT ItemId, StockTakeId, MaterialId, MaterialName, Unit, ExpectedQty, ActualQty
               FROM StockTakeItems
               WHERE StockTakeId = ?
               ORDER BY MaterialName""",
            listOf(stockTakeId)
        ) { rs ->
            StockTakeItem(
                itemId       = rs.getInt("ItemId"),
                stockTakeId  = rs.getInt("StockTakeId"),
                materialId   = rs.getInt("MaterialId"),
                materialName = rs.getString("MaterialName") ?: "",
                unit         = rs.getString("Unit") ?: "kg",
                expectedQty  = rs.getDouble("ExpectedQty"),
                actualQty    = rs.getDouble("ActualQty")
            )
        }
    } catch (_: Exception) { emptyList() }

    /** Creates a new Open stock take, pre-filling expected qty from current stock. */
    suspend fun startNewStockTake(notes: String, createdBy: Int?): Int {
        val id = db.insertAndGetId(
            "INSERT INTO StockTakes (Status, Notes, CreatedBy) VALUES ('Open', ?, ?)",
            listOf(notes.ifBlank { "" }, createdBy)
        )
        if (id > 0) {
            try {
                db.execute(
                    """INSERT INTO StockTakeItems (StockTakeId, MaterialId, MaterialName, Unit, ExpectedQty, ActualQty)
                       SELECT ?, ProductId, ProductName, ISNULL(Unit,''), ISNULL(CurrentStock, 0), ISNULL(CurrentStock, 0)
                       FROM Products WHERE ISNULL(IsStockManaged,0)=1 AND ISNULL(IsActive,1)=1""",
                    listOf(id)
                )
            } catch (_: Exception) {}
        }
        return id
    }

    /** Updates the actual quantity counted for one item. */
    suspend fun updateActualQty(itemId: Int, actualQty: Double) {
        try {
            db.execute(
                "UPDATE StockTakeItems SET ActualQty = ? WHERE ItemId = ?",
                listOf(actualQty, itemId)
            )
        } catch (_: Exception) {}
    }

    /** Finalizes the stock take — adjusts stock levels to match actual counts, writes ledger. */
    suspend fun finalizeStockTake(stockTakeId: Int) {
        val items = getStockTakeItems(stockTakeId)
        db.transaction { conn ->
            for (item in items) {
                val delta = item.actualQty - item.expectedQty
                db.executeSync(
                    conn,
                    "UPDATE Products SET CurrentStock = ? WHERE ProductId = ?",
                    listOf(item.actualQty, item.materialId)
                )
                try {
                    val inQty  = if (delta > 0) delta else 0.0
                    val outQty = if (delta < 0) -delta else 0.0
                    db.executeSync(
                        conn,
                        """INSERT INTO InventoryLedger
                           (ProductId, TransactionDate, ReferenceType, ReferenceId,
                            InQty, OutQty, Remarks, CreatedAt)
                           VALUES (?, GETDATE(), 'StockTake', ?, ?, ?, ?, GETDATE())""",
                        listOf(item.materialId, stockTakeId, inQty, outQty, "Stock Take #$stockTakeId")
                    )
                } catch (_: Exception) {}
            }
            db.executeSync(
                conn,
                "UPDATE StockTakes SET Status = 'Finalized', FinalizedAt = GETDATE() WHERE StockTakeId = ?",
                listOf(stockTakeId)
            )
        }
    }

    /** Cancels the stock take without changing any stock levels. */
    suspend fun cancelStockTake(stockTakeId: Int) {
        try {
            db.execute(
                "UPDATE StockTakes SET Status = 'Cancelled' WHERE StockTakeId = ? AND Status = 'Open'",
                listOf(stockTakeId)
            )
        } catch (_: Exception) {}
    }
}
