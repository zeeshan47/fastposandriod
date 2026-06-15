package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.WasteEntry
import com.fastpos.android.data.models.WasteEntryItem
import com.fastpos.android.utils.SessionManager
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WasteRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val audit:   AuditLogRepository,
    private val session: SessionManager
) {

    suspend fun getEntries(fromDate: Date, toDate: Date): List<WasteEntry> = try {
        db.query(
            """SELECT w.WasteId, w.WasteDate, ISNULL(w.Notes,'') AS Notes,
                      (SELECT COUNT(*) FROM WasteEntryItems i WHERE i.WasteId = w.WasteId) AS ItemCount,
                      (SELECT ISNULL(SUM(i.Amount),0) FROM WasteEntryItems i WHERE i.WasteId = w.WasteId) AS TotalAmount
               FROM WasteEntries w
               WHERE ISNULL(w.IsActive,1) = 1
               AND CAST(w.WasteDate AS DATE) BETWEEN CAST(? AS DATE) AND CAST(? AS DATE)
               ORDER BY w.WasteDate DESC""",
            listOf(fromDate, toDate)
        ) { rs ->
            WasteEntry(
                wasteId     = rs.getInt("WasteId"),
                wasteDate   = rs.getTimestamp("WasteDate") ?: Date(),
                notes       = rs.getString("Notes") ?: "",
                itemCount   = rs.getInt("ItemCount"),
                totalAmount = rs.getDouble("TotalAmount")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getEntryItems(wasteId: Int): List<WasteEntryItem> = try {
        db.query(
            """SELECT i.ItemId, i.WasteId, i.ProductId AS MaterialId,
                      ISNULL(p.ProductName,'') AS ItemName, ISNULL(i.Quantity,0) AS Quantity,
                      ISNULL(i.Unit,'kg') AS Unit, ISNULL(i.Rate,0) AS Rate,
                      ISNULL(i.Amount,0) AS Amount, ISNULL(i.Reason,'') AS Reason
               FROM WasteEntryItems i
               LEFT JOIN Products p ON p.ProductId = i.ProductId
               WHERE i.WasteId = ?""",
            listOf(wasteId)
        ) { rs ->
            WasteEntryItem(
                itemId     = rs.getInt("ItemId"),
                wasteId    = rs.getInt("WasteId"),
                materialId = rs.getInt("MaterialId"),
                itemName   = rs.getString("ItemName") ?: "",
                quantity   = rs.getDouble("Quantity"),
                unit       = rs.getString("Unit") ?: "kg",
                rate       = rs.getDouble("Rate"),
                amount     = rs.getDouble("Amount"),
                reason     = rs.getString("Reason") ?: ""
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun saveEntry(notes: String, items: List<WasteEntryItem>): Int {
        val result = db.transaction { conn ->
            val wasteId = db.insertAndGetIdSync(
                conn,
                "INSERT INTO WasteEntries (WasteDate, Notes, CreatedAt) VALUES (GETDATE(), ?, GETDATE())",
                listOf(notes.ifBlank { null })
            )
            if (wasteId == 0) throw Exception("Failed to create waste entry")

            for (item in items) {
                db.executeSync(
                    conn,
                    """INSERT INTO WasteEntryItems (WasteId, ProductId, Quantity, Unit, Rate, Amount, Reason)
                       VALUES (?, ?, ?, ?, ?, ?, ?)""",
                    listOf(
                        wasteId,
                        if (item.materialId > 0) item.materialId else null,
                        item.quantity,
                        item.unit,
                        item.rate,
                        item.quantity * item.rate,
                        item.reason.ifBlank { null }
                    )
                )

                if (item.materialId > 0) {
                    try {
                        db.executeSync(
                            conn,
                            "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) - ? WHERE ProductId = ? AND ISNULL(IsStockManaged,0)=1",
                            listOf(item.quantity, item.materialId)
                        )
                        db.executeSync(
                            conn,
                            """INSERT INTO InventoryLedger
                               (ProductId, TransactionDate, ReferenceType, ReferenceId,
                                InQty, OutQty, Rate, Amount, Remarks, CreatedAt)
                               VALUES (?, GETDATE(), 'Waste', ?, 0, ?, ?, ?, ?, GETDATE())""",
                            listOf(
                                item.materialId, wasteId,
                                item.quantity, item.rate,
                                item.quantity * item.rate,
                                "Waste #$wasteId: ${item.itemName}"
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
            wasteId
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "INSERT", "WasteEntries", result) }
        return result
    }

    suspend fun deleteEntry(wasteId: Int) {
        try {
            db.transaction { conn ->
                // Reverse stock for each item
                val items = getEntryItems(wasteId)
                for (item in items) {
                    if (item.materialId > 0) {
                        try {
                            db.executeSync(
                                conn,
                                "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) + ? WHERE ProductId = ? AND ISNULL(IsStockManaged,0)=1",
                                listOf(item.quantity, item.materialId)
                            )
                            db.executeSync(
                                conn,
                                """INSERT INTO InventoryLedger (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,Rate,Amount,Remarks)
                                   VALUES (?,GETDATE(),'WasteReversal',?,?,0,?,?,?)""",
                                listOf(item.materialId, wasteId, item.quantity, item.rate,
                                    item.quantity * item.rate, "Waste #$wasteId reversed")
                            )
                        } catch (_: Exception) {}
                    }
                }
                // Soft-delete matching WPF behaviour (audit trail preserved)
                db.executeSync(conn, "UPDATE WasteEntries SET IsActive=0 WHERE WasteId=?", listOf(wasteId))
            }
            runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "DELETE", "WasteEntries", wasteId) }
        } catch (_: Exception) {}
    }
}
