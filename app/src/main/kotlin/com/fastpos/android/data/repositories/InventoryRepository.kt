package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.StockItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun initSchema() {
        try {
            db.execute("""
                IF NOT EXISTS (
                    SELECT * FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_NAME = 'Products' AND COLUMN_NAME = 'MinimumStock'
                )
                ALTER TABLE Products ADD MinimumStock DECIMAL(10,2) NOT NULL DEFAULT 5
            """.trimIndent())
        } catch (_: Exception) {}
    }

    suspend fun getStockItems(search: String = ""): List<StockItem> {
        val where = if (search.isNotBlank()) "AND (p.ProductName LIKE ? OR c.CategoryName LIKE ?)" else ""
        val params = if (search.isNotBlank()) listOf("%$search%", "%$search%") else emptyList<Any>()
        return db.query(
            """
            SELECT p.ProductId, p.ProductName, p.CategoryId, c.CategoryName, c.ColorCode,
                   ISNULL(p.CurrentStock, 0) AS CurrentStock, p.IsActive,
                   ISNULL(p.MinimumStock, 5) AS MinimumStock,
                   ISNULL(p.SalePrice, 0) AS SalePrice
            FROM Products p
            JOIN Categories c ON p.CategoryId = c.CategoryId
            WHERE p.IsActive = 1 AND p.IsStockManaged = 1 $where
            ORDER BY c.CategoryName, p.ProductName
            """.trimIndent(),
            params
        ) { rs ->
            StockItem(
                productId    = rs.getInt("ProductId"),
                productName  = rs.getString("ProductName") ?: "",
                categoryId   = rs.getInt("CategoryId"),
                categoryName = rs.getString("CategoryName") ?: "",
                categoryColor = rs.getString("ColorCode") ?: "#607D8B",
                currentStock = rs.getDouble("CurrentStock"),
                minimumStock = try { rs.getDouble("MinimumStock") } catch (_: Exception) { 5.0 },
                salePrice    = try { rs.getDouble("SalePrice") } catch (_: Exception) { 0.0 },
                isActive     = rs.getBoolean("IsActive")
            )
        }
    }

    suspend fun adjustStock(productId: Int, delta: Double, userId: Int, remarks: String = "") {
        db.execute(
            "UPDATE Products SET CurrentStock = ISNULL(CurrentStock, 0) + ?, UpdatedAt = GETDATE(), UpdatedBy = ? WHERE ProductId = ?",
            listOf(delta, userId, productId)
        )
        val inQty  = if (delta > 0) delta else 0.0
        val outQty = if (delta < 0) -delta else 0.0
        val remark = remarks.ifBlank { if (delta > 0) "Stock added" else "Stock removed" }
        try {
            // BalanceQty is read from Products AFTER the UPDATE above
            db.execute(
                """INSERT INTO InventoryLedger
                   (ProductId, TransactionDate, ReferenceType, ReferenceId,
                    InQty, OutQty, BalanceQty, Rate, Amount, Remarks, CreatedBy, CreatedAt)
                   SELECT ?, GETDATE(), 'Adjustment', ?,
                          ?, ?, ISNULL(CurrentStock, 0), 0, 0, ?, ?, GETDATE()
                   FROM Products WHERE ProductId = ?""",
                listOf(productId, productId, inQty, outQty, remark, userId, productId)
            )
        } catch (_: Exception) {}
    }

    suspend fun setStockLevel(productId: Int, level: Double, userId: Int, remarks: String = "") {
        val currentStock = try {
            db.queryOne(
                "SELECT ISNULL(CurrentStock, 0) AS CurrentStock FROM Products WHERE ProductId = ?",
                listOf(productId)
            ) { it.getDouble("CurrentStock") } ?: 0.0
        } catch (_: Exception) { 0.0 }

        db.execute(
            "UPDATE Products SET CurrentStock = ?, UpdatedAt = GETDATE(), UpdatedBy = ? WHERE ProductId = ?",
            listOf(level, userId, productId)
        )
        val delta  = level - currentStock
        val inQty  = if (delta > 0) delta else 0.0
        val outQty = if (delta < 0) -delta else 0.0
        val remark = remarks.ifBlank { "Stock set to ${"%.2f".format(level)}" }
        try {
            db.execute(
                """INSERT INTO InventoryLedger
                   (ProductId, TransactionDate, ReferenceType, ReferenceId,
                    InQty, OutQty, BalanceQty, Rate, Amount, Remarks, CreatedBy, CreatedAt)
                   VALUES (?, GETDATE(), 'Adjustment', ?, ?, ?, ?, 0, 0, ?, ?, GETDATE())""",
                listOf(productId, productId, inQty, outQty, level, remark, userId)
            )
        } catch (_: Exception) {}
    }

    suspend fun setMinimumStock(productId: Int, minimum: Double) {
        db.execute(
            "UPDATE Products SET MinimumStock = ? WHERE ProductId = ?",
            listOf(minimum, productId)
        )
    }
}
