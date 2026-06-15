package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.*
import com.fastpos.android.utils.SessionManager
import java.sql.ResultSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val db:           DatabaseHelper,
    private val offlineCache: OfflineCacheRepository,
    private val audit:        AuditLogRepository,
    private val session:      SessionManager
) {

    suspend fun getCategories(includeInactive: Boolean = false): List<Category> {
        val where = if (includeInactive) "" else "WHERE IsActive = 1"
        return try {
            db.query(
                "SELECT CategoryId, CategoryName, ISNULL(CategoryNameOtherLanguage,'') AS OtherLanguageName, ISNULL(ColorCode,'#607D8B') AS ColorCode, DisplayOrder, ISNULL(IsActive,1) AS IsActive FROM Categories $where ORDER BY DisplayOrder, CategoryName"
            ) { rs ->
                Category(
                    categoryId        = rs.getInt("CategoryId"),
                    categoryName      = rs.getString("CategoryName") ?: "",
                    otherLanguageName = rs.getString("OtherLanguageName") ?: "",
                    colorCode         = rs.getString("ColorCode") ?: "#607D8B",
                    displayOrder      = rs.getInt("DisplayOrder"),
                    isActive          = rs.getBoolean("IsActive")
                )
            }
        } catch (_: Exception) {
            try {
                db.query(
                    "SELECT CategoryId, CategoryName, ISNULL(ColorCode,'#607D8B') AS ColorCode, DisplayOrder, ISNULL(IsActive,1) AS IsActive FROM Categories $where ORDER BY DisplayOrder, CategoryName"
                ) { rs ->
                    Category(
                        categoryId   = rs.getInt("CategoryId"),
                        categoryName = rs.getString("CategoryName") ?: "",
                        colorCode    = rs.getString("ColorCode") ?: "#607D8B",
                        displayOrder = rs.getInt("DisplayOrder"),
                        isActive     = rs.getBoolean("IsActive")
                    )
                }
            } catch (_: Exception) {
                try {
                    db.query(
                        "SELECT CategoryId, CategoryName, DisplayOrder FROM Categories ORDER BY DisplayOrder, CategoryName"
                    ) { rs ->
                        Category(
                            categoryId   = rs.getInt("CategoryId"),
                            categoryName = rs.getString("CategoryName") ?: "",
                            displayOrder = rs.getInt("DisplayOrder")
                        )
                    }
                } catch (_: Exception) {
                    offlineCache.getCachedCategories()
                }
            }
        }
    }

    suspend fun getProducts(categoryId: Int? = null): List<Product> {
        val where  = if (categoryId != null) "AND p.CategoryId = ?" else ""
        val params = if (categoryId != null) listOf(categoryId) else emptyList<Any?>()
        return try {
            db.query("""
                SELECT p.ProductId, p.ProductCode, p.ProductName, p.CategoryId, p.ProductType,
                       p.SalePrice, ISNULL(p.PurchasePrice,0) AS CostPrice, p.IsStockManaged, p.DisplayOrder,
                       ISNULL(p.IsAvailable, 1) AS IsAvailable,
                       p.TaxId, ISNULL(t.TaxPercent,0) AS TaxPercent,
                       c.CategoryName, c.ColorCode,
                       ISNULL(kp.PrinterName,'') AS PrinterName,
                       p.KitchenPrinterId,
                       ISNULL(p.ProductNameOtherLanguage,'') AS ProductNameOtherLanguage
                FROM Products p
                JOIN Categories c ON p.CategoryId = c.CategoryId
                LEFT JOIN Taxes t ON p.TaxId = t.TaxId
                LEFT JOIN KitchenPrinters kp ON p.KitchenPrinterId = kp.PrinterId
                WHERE p.IsActive = 1 AND p.ProductType NOT IN ('RawMaterial','Modifier') $where
                ORDER BY p.DisplayOrder, p.ProductName
            """.trimIndent(), params) { rs ->
                mapProduct(rs).copy(isAvailable = rs.getBoolean("IsAvailable"))
            }
        } catch (_: Exception) {
            try {
                db.query("""
                    SELECT p.ProductId, p.ProductCode, p.ProductName, p.CategoryId, p.ProductType,
                           p.SalePrice, ISNULL(p.PurchasePrice,0) AS CostPrice, p.IsStockManaged, p.DisplayOrder,
                           p.TaxId, ISNULL(t.TaxPercent,0) AS TaxPercent,
                           c.CategoryName, c.ColorCode,
                           ISNULL(kp.PrinterName,'') AS PrinterName,
                           p.KitchenPrinterId,
                           ISNULL(p.ProductNameOtherLanguage,'') AS ProductNameOtherLanguage
                    FROM Products p
                    JOIN Categories c ON p.CategoryId = c.CategoryId
                    LEFT JOIN Taxes t ON p.TaxId = t.TaxId
                    LEFT JOIN KitchenPrinters kp ON p.KitchenPrinterId = kp.PrinterId
                    WHERE p.IsActive = 1 AND p.ProductType NOT IN ('RawMaterial','Modifier') $where
                    ORDER BY p.DisplayOrder, p.ProductName
                """.trimIndent(), params) { rs -> mapProduct(rs) }
            } catch (_: Exception) {
                offlineCache.getCachedProducts(categoryId)
            }
        }
    }

    suspend fun setProductAvailability(productId: Int, available: Boolean) {
        try {
            db.execute(
                "UPDATE Products SET IsAvailable = ? WHERE ProductId = ?",
                listOf(if (available) 1 else 0, productId)
            )
        } catch (_: Exception) {}
    }

    /** Insert a new product row and return the generated ProductId. */
    suspend fun saveProductInsert(
        productCode: String?, productName: String, otherName: String?,
        categoryId: Int, productType: String, salePrice: Double, costPrice: Double,
        isStockManaged: Boolean, isRecipeBased: Boolean, taxId: Int?,
        kitchenPrinterId: Int?, displayOrder: Int, unit: String,
        reorderLevel: Double, createdBy: Int
    ): Int {
        val id = db.insertAndGetId(
            """INSERT INTO Products
               (ProductCode, ProductName, ProductNameOtherLanguage, CategoryId,
                ProductType, SalePrice, PurchasePrice, IsStockManaged, IsRecipeBased,
                TaxId, KitchenPrinterId, DisplayOrder, IsActive,
                Unit, OpeningStock, CurrentStock, ReorderLevel, CreatedBy, CreatedAt)
               VALUES (?, ?, ISNULL(?,''), ?,
                       ?, ?, ?, ?, ?,
                       ?, ?, ?, 1,
                       ?, 0, 0, ?, ?, GETDATE())""",
            listOf(productCode, productName, otherName, categoryId,
                   productType.ifBlank { "Normal" }, salePrice, costPrice,
                   if (isStockManaged) 1 else 0, if (isRecipeBased) 1 else 0,
                   taxId, kitchenPrinterId, displayOrder,
                   unit.ifBlank { "Pcs" }, reorderLevel, createdBy)
        )
        if (id > 0) runCatching { audit.writeAudit(createdBy, "INSERT", "Products", id) }
        return id
    }

    /** Update an existing product row. */
    suspend fun saveProductUpdate(
        productId: Int, productCode: String?, productName: String, otherName: String?,
        categoryId: Int, productType: String, salePrice: Double, costPrice: Double,
        isStockManaged: Boolean, isRecipeBased: Boolean, isActive: Boolean,
        taxId: Int?, kitchenPrinterId: Int?, displayOrder: Int,
        unit: String, reorderLevel: Double, updatedBy: Int
    ) {
        db.execute(
            """UPDATE Products SET
               ProductName=?, ProductCode=?, ProductNameOtherLanguage=ISNULL(?,''),
               CategoryId=?, ProductType=?, SalePrice=?, PurchasePrice=?,
               IsStockManaged=?, IsRecipeBased=?, IsActive=?,
               TaxId=?, KitchenPrinterId=?,
               DisplayOrder=?, Unit=?, ReorderLevel=?,
               UpdatedAt=GETDATE(), UpdatedBy=?
               WHERE ProductId=?""",
            listOf(productName, productCode, otherName,
                   categoryId, productType.ifBlank { "Normal" }, salePrice, costPrice,
                   if (isStockManaged) 1 else 0, if (isRecipeBased) 1 else 0, if (isActive) 1 else 0,
                   taxId, kitchenPrinterId,
                   displayOrder, unit.ifBlank { "Pcs" }, reorderLevel,
                   updatedBy, productId)
        )
        runCatching { audit.writeAudit(updatedBy, "UPDATE", "Products", productId) }
    }

    /** Soft-delete a product (set IsActive = 0). */
    suspend fun deleteProduct(productId: Int, deletedBy: Int = session.currentUser.value?.userId ?: 0) {
        db.execute(
            "UPDATE Products SET IsActive=0, UpdatedAt=GETDATE(), UpdatedBy=? WHERE ProductId=?",
            listOf(deletedBy, productId)
        )
        runCatching { audit.writeAudit(deletedBy, "DELETE", "Products", productId) }
    }

    suspend fun getDistinctPrinterNames(): List<String> = try {
        db.query(
            "SELECT PrinterName FROM KitchenPrinters WHERE IsActive = 1 ORDER BY PrinterName"
        ) { rs -> rs.getString("PrinterName") ?: "" }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    suspend fun getProductSizes(productId: Int): List<ProductSize> = db.query(
        "SELECT SizeId, ProductId, SizeName, Price, ISNULL(CostPrice,0) AS CostPrice, DisplayOrder FROM ProductSizes WHERE ProductId = ? AND IsActive = 1 ORDER BY DisplayOrder",
        listOf(productId)
    ) { rs ->
        ProductSize(
            sizeId       = rs.getInt("SizeId"),
            productId    = rs.getInt("ProductId"),
            sizeName     = rs.getString("SizeName") ?: "",
            price        = rs.getDouble("Price"),
            costPrice    = try { rs.getDouble("CostPrice") } catch (_: Exception) { 0.0 },
            displayOrder = rs.getInt("DisplayOrder")
        )
    }

    suspend fun getModifierGroups(productId: Int): List<ModifierGroup> {
        val groups = try {
            db.query(
                """SELECT mg.ModifierGroupId, mg.GroupName, mg.MinSelection, mg.MaxSelection, mg.IsRequired
                   FROM ModifierGroups mg
                   JOIN ProductModifierGroups pmg ON mg.ModifierGroupId = pmg.ModifierGroupId
                   WHERE pmg.ProductId = ? AND mg.IsActive = 1""",
                listOf(productId)
            ) { rs ->
                ModifierGroup(
                    modifierGroupId = rs.getInt("ModifierGroupId"),
                    groupName       = rs.getString("GroupName") ?: "",
                    minSelection    = rs.getInt("MinSelection"),
                    maxSelection    = rs.getInt("MaxSelection"),
                    isRequired      = rs.getBoolean("IsRequired")
                )
            }
        } catch (_: Exception) { return emptyList() }

        return groups.map { group ->
            val modifiers = try {
                // Primary: include StockItemId for stock-linked modifiers
                db.query(
                    """SELECT m.ModifierId, m.ModifierGroupId, m.ModifierName, m.ExtraPrice,
                              ISNULL(m.StockItemId,0) AS StockItemId,
                              ISNULL(p.ProductName,'') AS StockItemName
                       FROM ProductModifiers m
                       LEFT JOIN Products p ON p.ProductId = m.StockItemId
                       WHERE m.ModifierGroupId = ? AND ISNULL(m.IsActive,1) = 1""",
                    listOf(group.modifierGroupId)
                ) { rs ->
                    ProductModifier(
                        modifierId      = rs.getInt("ModifierId"),
                        modifierGroupId = rs.getInt("ModifierGroupId"),
                        modifierName    = rs.getString("ModifierName") ?: "",
                        extraPrice      = rs.getDouble("ExtraPrice"),
                        stockItemId     = try { rs.getInt("StockItemId") } catch (_: Exception) { 0 },
                        stockItemName   = try { rs.getString("StockItemName") ?: "" } catch (_: Exception) { "" }
                    )
                }
            } catch (_: Exception) {
                // Fallback: StockItemId column may not exist on this SQL Server install
                try {
                    db.query(
                        "SELECT ModifierId, ModifierGroupId, ModifierName, ExtraPrice FROM ProductModifiers WHERE ModifierGroupId = ? AND ISNULL(IsActive,1) = 1",
                        listOf(group.modifierGroupId)
                    ) { rs ->
                        ProductModifier(
                            modifierId      = rs.getInt("ModifierId"),
                            modifierGroupId = rs.getInt("ModifierGroupId"),
                            modifierName    = rs.getString("ModifierName") ?: "",
                            extraPrice      = rs.getDouble("ExtraPrice")
                        )
                    }
                } catch (_: Exception) { emptyList() }
            }
            group.copy(modifiers = modifiers)
        }
    }

    // ── Sizes write ──────────────────────────────────────────────────────────

    suspend fun addProductSize(productId: Int, sizeName: String, price: Double, costPrice: Double = 0.0) {
        val id = db.insertAndGetId(
            "INSERT INTO ProductSizes (ProductId, SizeName, Price, CostPrice, DisplayOrder, IsActive) VALUES (?,?,?,?,(SELECT ISNULL(MAX(DisplayOrder),0)+1 FROM ProductSizes WHERE ProductId=?),1)",
            listOf(productId, sizeName, price, costPrice, productId)
        )
        if (id > 0) runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "INSERT", "ProductSizes", id) }
    }

    suspend fun updateProductSize(sizeId: Int, sizeName: String, price: Double, costPrice: Double = 0.0) {
        db.execute("UPDATE ProductSizes SET SizeName=?, Price=?, CostPrice=? WHERE SizeId=?", listOf(sizeName, price, costPrice, sizeId))
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "UPDATE", "ProductSizes", sizeId) }
    }

    suspend fun deleteProductSize(sizeId: Int) {
        db.execute("UPDATE ProductSizes SET IsActive=0 WHERE SizeId=?", listOf(sizeId))
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "DELETE", "ProductSizes", sizeId) }
    }

    // ── Modifier groups write ─────────────────────────────────────────────────

    suspend fun getAllModifierGroups(): List<ModifierGroup> {
        val groups = db.query(
            "SELECT ModifierGroupId, GroupName, MinSelection, MaxSelection, IsRequired FROM ModifierGroups WHERE IsActive=1 ORDER BY GroupName"
        ) { rs ->
            ModifierGroup(
                modifierGroupId = rs.getInt("ModifierGroupId"),
                groupName       = rs.getString("GroupName") ?: "",
                minSelection    = rs.getInt("MinSelection"),
                maxSelection    = rs.getInt("MaxSelection"),
                isRequired      = rs.getBoolean("IsRequired")
            )
        }
        return groups.map { group ->
            val mods = db.query(
                """SELECT m.ModifierId, m.ModifierGroupId, m.ModifierName, m.ExtraPrice,
                          ISNULL(m.StockItemId,0) AS StockItemId,
                          ISNULL(p.ProductName,'') AS StockItemName
                   FROM ProductModifiers m
                   LEFT JOIN Products p ON p.ProductId = m.StockItemId
                   WHERE m.ModifierGroupId=? AND ISNULL(m.IsActive,1)=1
                   ORDER BY m.ModifierName""",
                listOf(group.modifierGroupId)
            ) { rs ->
                ProductModifier(
                    modifierId      = rs.getInt("ModifierId"),
                    modifierGroupId = rs.getInt("ModifierGroupId"),
                    modifierName    = rs.getString("ModifierName") ?: "",
                    extraPrice      = rs.getDouble("ExtraPrice"),
                    stockItemId     = try { rs.getInt("StockItemId") } catch (_: Exception) { 0 },
                    stockItemName   = try { rs.getString("StockItemName") ?: "" } catch (_: Exception) { "" }
                )
            }
            group.copy(modifiers = mods)
        }
    }

    suspend fun getStockManagedProducts(): List<Product> = try {
        db.query(
            "SELECT ProductId, ProductName FROM Products WHERE ISNULL(IsStockManaged,0)=1 AND ISNULL(IsActive,1)=1 ORDER BY ProductName"
        ) { rs ->
            Product(
                productId   = rs.getInt("ProductId"),
                productName = rs.getString("ProductName") ?: ""
            )
        }
    } catch (_: Exception) { emptyList() }

    // ── Product Stock Ledger ───────────────────────────────────────────────────

    suspend fun getProductLedgerOpeningBalance(productId: Int, fromDate: java.util.Date): Double = try {
        db.queryOne(
            "SELECT ISNULL(SUM(InQty - OutQty), 0) AS bal FROM InventoryLedger WHERE ProductId = ? AND TransactionDate < ?",
            listOf(productId, java.sql.Date(fromDate.time))
        ) { it.getDouble("bal") } ?: 0.0
    } catch (_: Exception) { 0.0 }

    suspend fun getProductLedgerRows(productId: Int, fromDate: java.util.Date, toDate: java.util.Date): List<InventoryLedger> {
        val rows = try {
            db.query("""
                SELECT LedgerId, ProductId, TransactionDate, ReferenceType, ReferenceId,
                       InQty, OutQty, 0 AS BalanceQty,
                       ISNULL(Rate,0) AS Rate, ISNULL(Amount,0) AS Amount,
                       ISNULL(Remarks,'') AS Remarks
                FROM InventoryLedger
                WHERE ProductId = ? AND TransactionDate >= ? AND TransactionDate < DATEADD(day,1,CAST(? AS DATE))
                ORDER BY TransactionDate, LedgerId
            """.trimIndent(),
                listOf(productId, java.sql.Date(fromDate.time), java.sql.Date(toDate.time))
            ) { rs ->
                InventoryLedger(
                    ledgerId   = rs.getInt("LedgerId"),
                    materialId = rs.getInt("ProductId"),
                    transDate  = rs.getTimestamp("TransactionDate") ?: java.util.Date(),
                    refType    = rs.getString("ReferenceType") ?: "",
                    refId      = rs.getInt("ReferenceId").takeIf { !rs.wasNull() },
                    inQty      = rs.getDouble("InQty"),
                    outQty     = rs.getDouble("OutQty"),
                    balanceQty = rs.getDouble("BalanceQty"),
                    rate       = rs.getDouble("Rate"),
                    amount     = rs.getDouble("Amount"),
                    remarks    = rs.getString("Remarks") ?: ""
                )
            }
        } catch (_: Exception) { emptyList() }

        val opening = getProductLedgerOpeningBalance(productId, fromDate)
        var running = opening
        return rows.map { r ->
            running += r.inQty - r.outQty
            r.copy(balanceQty = running)
        }.reversed()
    }

    suspend fun addModifierGroup(groupName: String, minSel: Int, maxSel: Int, isRequired: Boolean): Int {
        val id = db.insertAndGetId(
            "INSERT INTO ModifierGroups (GroupName, MinSelection, MaxSelection, IsRequired, IsActive) VALUES (?,?,?,?,1)",
            listOf(groupName, minSel, maxSel, if (isRequired) 1 else 0)
        )
        if (id > 0) runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "INSERT", "ModifierGroups", id) }
        return id
    }

    suspend fun updateModifierGroup(groupId: Int, groupName: String, minSel: Int, maxSel: Int, isRequired: Boolean) {
        db.execute(
            "UPDATE ModifierGroups SET GroupName=?, MinSelection=?, MaxSelection=?, IsRequired=? WHERE ModifierGroupId=?",
            listOf(groupName, minSel, maxSel, if (isRequired) 1 else 0, groupId)
        )
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "UPDATE", "ModifierGroups", groupId) }
    }

    suspend fun deleteModifierGroup(groupId: Int) {
        db.execute("UPDATE ModifierGroups SET IsActive=0 WHERE ModifierGroupId=?", listOf(groupId))
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "DELETE", "ModifierGroups", groupId) }
    }

    suspend fun addModifier(groupId: Int, name: String, price: Double, stockItemId: Int = 0) {
        val stockId = if (stockItemId > 0) stockItemId else null
        val id = try {
            db.insertAndGetId(
                "INSERT INTO ProductModifiers (ModifierGroupId, ModifierName, ExtraPrice, StockItemId, IsActive) VALUES (?,?,?,?,1)",
                listOf(groupId, name, price, stockId)
            )
        } catch (_: Exception) {
            db.insertAndGetId(
                "INSERT INTO ProductModifiers (ModifierGroupId, ModifierName, ExtraPrice, IsActive) VALUES (?,?,?,1)",
                listOf(groupId, name, price)
            )
        }
        if (id > 0) runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "INSERT", "ProductModifiers", id) }
    }

    suspend fun updateModifier(modifierId: Int, name: String, price: Double, stockItemId: Int = 0) {
        val stockId = if (stockItemId > 0) stockItemId else null
        try {
            db.execute(
                "UPDATE ProductModifiers SET ModifierName=?, ExtraPrice=?, StockItemId=? WHERE ModifierId=?",
                listOf(name, price, stockId, modifierId)
            )
        } catch (_: Exception) {
            db.execute("UPDATE ProductModifiers SET ModifierName=?, ExtraPrice=? WHERE ModifierId=?", listOf(name, price, modifierId))
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "UPDATE", "ProductModifiers", modifierId) }
    }

    suspend fun deleteModifier(modifierId: Int) {
        val usedCount = try {
            db.queryOne(
                "SELECT COUNT(1) AS cnt FROM OrderItemModifiers WHERE ModifierId = ?",
                listOf(modifierId)
            ) { it.getInt("cnt") } ?: 0
        } catch (_: Exception) { 0 }

        if (usedCount > 0) {
            db.execute("UPDATE ProductModifiers SET IsActive=0 WHERE ModifierId=?", listOf(modifierId))
        } else {
            db.execute("DELETE FROM ProductModifiers WHERE ModifierId=?", listOf(modifierId))
        }
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "DELETE", "ProductModifiers", modifierId) }
    }

    suspend fun linkGroupToProduct(productId: Int, groupId: Int) {
        try {
            db.execute(
                "INSERT OR IGNORE INTO ProductModifierGroups (ProductId, ModifierGroupId) VALUES (?,?)",
                listOf(productId, groupId)
            )
        } catch (_: Exception) {}
    }

    suspend fun unlinkGroupFromProduct(productId: Int, groupId: Int) {
        db.execute("DELETE FROM ProductModifierGroups WHERE ProductId=? AND ModifierGroupId=?", listOf(productId, groupId))
    }

    suspend fun getAssignedModifierGroupIds(productId: Int): List<Int> = try {
        db.query(
            "SELECT ModifierGroupId FROM ProductModifierGroups WHERE ProductId=?",
            listOf(productId)
        ) { it.getInt("ModifierGroupId") }
    } catch (_: Exception) { emptyList() }

    suspend fun setModifierGroupAssignments(productId: Int, groupIds: List<Int>) {
        try {
            db.execute("DELETE FROM ProductModifierGroups WHERE ProductId=?", listOf(productId))
            groupIds.forEach { gid ->
                try {
                    db.execute(
                        "INSERT INTO ProductModifierGroups (ProductId, ModifierGroupId) VALUES (?,?)",
                        listOf(productId, gid)
                    )
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    suspend fun bulkUpdatePrices(categoryId: Int?, changePercent: Double) {
        val sql = if (categoryId != null) {
            "UPDATE Products SET SalePrice = SalePrice * ? WHERE CategoryId = ? AND IsActive = 1"
        } else {
            "UPDATE Products SET SalePrice = SalePrice * ? WHERE IsActive = 1"
        }
        val factor = 1.0 + changePercent / 100.0
        val params = if (categoryId != null) listOf(factor, categoryId) else listOf(factor)
        db.execute(sql, params)
    }

    // ─────────────────────────────────────────────────────────────────────────

    suspend fun getCompanySettings(): com.fastpos.android.data.models.CompanySettings {
        // Try extended query first (with address, phone, NTN, tax label, footer, language)
        return try {
            db.queryOne("""
                SELECT TOP 1 CompanyName, CurrencySymbol, DefaultTaxPercent, ServiceChargePercent,
                       DefaultOrderType, TokenPrefix, AllowPartialPayment,
                       ISNULL(Address,'') AS Address,
                       ISNULL(Phone,'') AS Phone,
                       ISNULL(TaxNumber,'') AS TaxNumber,
                       ISNULL(TaxLabel,'Tax') AS TaxLabel,
                       ISNULL(ReceiptFooter,'Thank you for your visit!') AS ReceiptFooter,
                       ISNULL(Language,'English') AS Language
                FROM CompanySettings
            """.trimIndent()
            ) { rs ->
                CompanySettings(
                    companyName          = rs.getString("CompanyName") ?: "My Restaurant",
                    currencySymbol       = rs.getString("CurrencySymbol") ?: "Rs.",
                    defaultTaxPercent    = rs.getDouble("DefaultTaxPercent"),
                    serviceChargePercent = rs.getDouble("ServiceChargePercent"),
                    defaultOrderType     = rs.getString("DefaultOrderType") ?: "DineIn",
                    tokenPrefix          = rs.getString("TokenPrefix") ?: "T",
                    allowPartialPayment  = rs.getBoolean("AllowPartialPayment"),
                    address              = rs.getString("Address") ?: "",
                    phone                = rs.getString("Phone") ?: "",
                    taxNumber            = rs.getString("TaxNumber") ?: "",
                    taxLabel             = rs.getString("TaxLabel") ?: "Tax",
                    receiptFooter        = rs.getString("ReceiptFooter") ?: "Thank you for your visit!",
                    language             = rs.getString("Language") ?: "English"
                )
            } ?: CompanySettings()
        } catch (_: Exception) {
            try {
                db.queryOne(
                    "SELECT TOP 1 CompanyName, CurrencySymbol, DefaultTaxPercent, ServiceChargePercent, DefaultOrderType, TokenPrefix, AllowPartialPayment FROM CompanySettings"
                ) { rs ->
                    CompanySettings(
                        companyName          = rs.getString("CompanyName") ?: "My Restaurant",
                        currencySymbol       = rs.getString("CurrencySymbol") ?: "Rs.",
                        defaultTaxPercent    = rs.getDouble("DefaultTaxPercent"),
                        serviceChargePercent = rs.getDouble("ServiceChargePercent"),
                        defaultOrderType     = rs.getString("DefaultOrderType") ?: "DineIn",
                        tokenPrefix          = rs.getString("TokenPrefix") ?: "T",
                        allowPartialPayment  = rs.getBoolean("AllowPartialPayment")
                    )
                } ?: CompanySettings()
            } catch (_: Exception) { CompanySettings() }
        }
    }

    suspend fun getAllProductsForManagement(): List<Product> = try {
        db.query("""
            SELECT p.ProductId, p.ProductCode, p.ProductName, p.CategoryId, p.ProductType,
                   p.SalePrice, p.IsStockManaged, p.DisplayOrder, p.IsActive,
                   ISNULL(p.IsAvailable, 1) AS IsAvailable,
                   ISNULL(p.PurchasePrice, 0) AS CostPrice,
                   ISNULL(p.IsRecipeBased, 0) AS IsRecipeBased,
                   ISNULL(kp.PrinterName, '') AS PrinterName,
                   ISNULL(p.ProductNameOtherLanguage, '') AS ProductNameOtherLanguage,
                   ISNULL(p.Unit, 'Pcs') AS Unit,
                   ISNULL(p.ReorderLevel, 0) AS ReorderLevel,
                   p.TaxId, ISNULL(t.TaxPercent, 0) AS TaxPercent,
                   c.CategoryName, c.ColorCode
            FROM Products p
            JOIN Categories c ON p.CategoryId = c.CategoryId
            LEFT JOIN Taxes t ON p.TaxId = t.TaxId
            LEFT JOIN KitchenPrinters kp ON p.KitchenPrinterId = kp.PrinterId
            WHERE p.ProductType <> 'Modifier'
            ORDER BY p.IsActive DESC, p.DisplayOrder, p.ProductName
        """.trimIndent(), emptyList()) { rs ->
            mapProduct(rs).copy(
                isActive    = rs.getBoolean("IsActive"),
                isAvailable = rs.getBoolean("IsAvailable")
            )
        }
    } catch (_: Exception) {
        db.query("""
            SELECT p.ProductId, p.ProductCode, p.ProductName, p.CategoryId, p.ProductType,
                   p.SalePrice, p.IsStockManaged, p.DisplayOrder, p.IsActive,
                   ISNULL(p.IsAvailable, 1) AS IsAvailable,
                   ISNULL(p.PurchasePrice, 0) AS CostPrice,
                   ISNULL(p.IsRecipeBased, 0) AS IsRecipeBased,
                   ISNULL(kp.PrinterName, '') AS PrinterName,
                   ISNULL(p.ProductNameOtherLanguage, '') AS ProductNameOtherLanguage,
                   ISNULL(p.Unit, 'Pcs') AS Unit,
                   ISNULL(p.ReorderLevel, 0) AS ReorderLevel,
                   p.TaxId, ISNULL(t.TaxPercent, 0) AS TaxPercent,
                   c.CategoryName, c.ColorCode
            FROM Products p
            JOIN Categories c ON p.CategoryId = c.CategoryId
            LEFT JOIN Taxes t ON p.TaxId = t.TaxId
            LEFT JOIN KitchenPrinters kp ON p.KitchenPrinterId = kp.PrinterId
            WHERE p.ProductType <> 'Modifier'
            ORDER BY p.IsActive DESC, p.DisplayOrder, p.ProductName
        """.trimIndent(), emptyList()) { rs ->
            mapProduct(rs).copy(
                isActive    = rs.getBoolean("IsActive"),
                isAvailable = rs.getBoolean("IsAvailable")
            )
        }
    }

    private fun mapProduct(rs: ResultSet) = Product(
        productId                  = rs.getInt("ProductId"),
        productCode                = rs.getString("ProductCode") ?: "",
        productName                = rs.getString("ProductName") ?: "",
        productNameOtherLanguage   = try { rs.getString("ProductNameOtherLanguage") ?: "" } catch (_: Exception) { "" },
        categoryId                 = rs.getInt("CategoryId"),
        categoryName   = rs.getString("CategoryName") ?: "",
        categoryColor  = rs.getString("ColorCode") ?: "#607D8B",
        productType    = rs.getString("ProductType") ?: "Normal",
        salePrice      = rs.getDouble("SalePrice"),
        costPrice      = try { rs.getDouble("CostPrice") } catch (_: Exception) { 0.0 },
        isStockManaged = rs.getBoolean("IsStockManaged"),
        isRecipeBased  = try { rs.getBoolean("IsRecipeBased") } catch (_: Exception) { false },
        unit           = try { rs.getString("Unit") ?: "Pcs" } catch (_: Exception) { "Pcs" },
        reorderLevel   = try { rs.getDouble("ReorderLevel") } catch (_: Exception) { 0.0 },
        taxId          = rs.getInt("TaxId").takeIf { it > 0 },
        taxPercent     = rs.getDouble("TaxPercent"),
        displayOrder         = rs.getInt("DisplayOrder"),
        printerName          = try { rs.getString("PrinterName") ?: "" } catch (_: Exception) { "" },
        kitchenPrinterId     = try { rs.getInt("KitchenPrinterId").takeIf { it > 0 } } catch (_: Exception) { null }
    )
}
