package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Deal
import com.fastpos.android.data.models.DealItem
import com.fastpos.android.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DealRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val session: SessionManager,
    private val audit:   AuditLogRepository
) {

    suspend fun initSchema() = withContext(Dispatchers.IO) {
        db.execute("""
            IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'Deals')
            CREATE TABLE Deals (
                DealId          INT IDENTITY(1,1) PRIMARY KEY,
                DealName        NVARCHAR(200) NOT NULL,
                Description     NVARCHAR(500) DEFAULT '',
                DealPrice       DECIMAL(18,2) DEFAULT 0,
                DiscountPercent DECIMAL(5,2)  DEFAULT 0,
                ValidFrom       DATE          NULL,
                ValidTo         DATE          NULL,
                IsActive        BIT           DEFAULT 1,
                CreatedAt       DATETIME      DEFAULT GETDATE()
            )
        """.trimIndent())
        db.execute("""
            IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'DealItems')
            CREATE TABLE DealItems (
                DealItemId  INT IDENTITY(1,1) PRIMARY KEY,
                DealId      INT NOT NULL,
                ProductId   INT NOT NULL,
                SizeId      INT NULL,
                Quantity    INT DEFAULT 1,
                IsOptional  BIT DEFAULT 0
            )
        """.trimIndent())
    }

    suspend fun getAllDeals(): List<Deal> = db.query(
        "SELECT DealId, DealName, ISNULL(Description,'') AS Description, DealPrice, DiscountPercent, ValidFrom, ValidTo, IsActive FROM Deals WHERE IsActive = 1 ORDER BY DealName"
    ) { rs ->
        Deal(
            dealId          = rs.getInt("DealId"),
            dealName        = rs.getString("DealName") ?: "",
            description     = rs.getString("Description") ?: "",
            dealPrice       = rs.getDouble("DealPrice"),
            discountPercent = rs.getDouble("DiscountPercent"),
            validFrom       = rs.getDate("ValidFrom"),
            validTo         = rs.getDate("ValidTo"),
            isActive        = rs.getBoolean("IsActive")
        )
    }

    suspend fun getDealItems(dealId: Int): List<DealItem> = db.query(
        """
        SELECT di.DealItemId AS ItemId, di.DealId, di.ProductId, p.ProductName, p.SalePrice,
               di.SizeId, ps.SizeName, ISNULL(ps.Price, p.SalePrice) AS EffectivePrice,
               di.Quantity, di.IsOptional
        FROM DealItems di
        JOIN Products p ON p.ProductId = di.ProductId
        LEFT JOIN ProductSizes ps ON ps.SizeId = di.SizeId
        WHERE di.DealId = ?
        ORDER BY di.DealItemId
        """.trimIndent(), listOf(dealId)
    ) { rs ->
        DealItem(
            itemId      = rs.getInt("ItemId"),
            dealId      = rs.getInt("DealId"),
            productId   = rs.getInt("ProductId"),
            productName = rs.getString("ProductName") ?: "",
            salePrice   = rs.getDouble("EffectivePrice"),
            sizeId      = rs.getInt("SizeId").takeIf { !rs.wasNull() },
            sizeName    = rs.getString("SizeName"),
            quantity    = rs.getInt("Quantity"),
            isOptional  = rs.getBoolean("IsOptional")
        )
    }

    suspend fun saveDeal(deal: Deal, items: List<DealItem>): Int {
        val isInsert = deal.dealId == 0
        val dealId = db.transaction { conn ->
            val id = if (isInsert) {
                db.insertAndGetIdSync(conn,
                    "INSERT INTO Deals (DealName, Description, DealPrice, DiscountPercent, ValidFrom, ValidTo, IsActive) VALUES (?,?,?,?,?,?,?)",
                    listOf(deal.dealName, deal.description, deal.dealPrice, deal.discountPercent,
                        deal.validFrom?.let { java.sql.Date(it.time) },
                        deal.validTo?.let { java.sql.Date(it.time) },
                        if (deal.isActive) 1 else 0)
                )
            } else {
                db.executeSync(
                    conn,
                    "UPDATE Deals SET DealName=?, Description=?, DealPrice=?, DiscountPercent=?, ValidFrom=?, ValidTo=?, IsActive=? WHERE DealId=?",
                    listOf(deal.dealName, deal.description, deal.dealPrice, deal.discountPercent,
                        deal.validFrom?.let { java.sql.Date(it.time) },
                        deal.validTo?.let { java.sql.Date(it.time) },
                        if (deal.isActive) 1 else 0, deal.dealId)
                )
                deal.dealId
            }
            db.executeSync(conn, "DELETE FROM DealItems WHERE DealId = ?", listOf(id))
            for (item in items) {
                db.executeSync(
                    conn,
                    "INSERT INTO DealItems (DealId, ProductId, SizeId, Quantity, IsOptional) VALUES (?,?,?,?,?)",
                    listOf(id, item.productId, item.sizeId, item.quantity,
                        if (item.isOptional) 1 else 0)
                )
            }
            id
        }
        val userId = session.currentUser.value?.userId ?: 0
        if (isInsert) {
            runCatching { audit.writeAudit(userId, "INSERT", "Deals", dealId) }
        } else {
            runCatching { audit.writeAudit(userId, "UPDATE", "Deals", dealId) }
        }
        return dealId
    }

    suspend fun deleteDeal(dealId: Int) {
        db.execute("UPDATE Deals SET IsActive = 0 WHERE DealId = ?", listOf(dealId))
        val userId = session.currentUser.value?.userId ?: 0
        runCatching { audit.writeAudit(userId, "DELETE", "Deals", dealId) }
    }
}
