package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.*
import java.sql.ResultSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RawMaterialRepository @Inject constructor(
    private val db: DatabaseHelper,
    private val audit: AuditLogRepository
) {

    /**
     * Creates Android-specific auxiliary tables only. WPF tables (Products, InventoryLedger,
     * WasteEntries, WasteEntryItems) are never recreated if they already exist.
     */
    suspend fun initSchema() {
        // WPF-compatible InventoryLedger (created only if not already present by WPF)
        try {
            db.execute("""
                IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'InventoryLedger')
                CREATE TABLE InventoryLedger (
                    LedgerId        INT IDENTITY(1,1) PRIMARY KEY,
                    ProductId       INT NOT NULL,
                    TransactionDate DATETIME DEFAULT GETDATE(),
                    ReferenceType   NVARCHAR(50)  DEFAULT '',
                    ReferenceId     INT NULL,
                    InQty           DECIMAL(18,3) DEFAULT 0,
                    OutQty          DECIMAL(18,3) DEFAULT 0,
                    BalanceQty      DECIMAL(18,3) DEFAULT 0,
                    Rate            DECIMAL(18,2) DEFAULT 0,
                    Amount          DECIMAL(18,2) DEFAULT 0,
                    Remarks         NVARCHAR(500) DEFAULT '',
                    CreatedBy       INT NULL,
                    CreatedAt       DATETIME DEFAULT GETDATE()
                )
            """.trimIndent())
        } catch (_: Exception) {}

        // WPF-compatible WasteEntries
        try {
            db.execute("""
                IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'WasteEntries')
                CREATE TABLE WasteEntries (
                    WasteId     INT IDENTITY(1,1) PRIMARY KEY,
                    WasteDate   DATETIME DEFAULT GETDATE(),
                    Notes       NVARCHAR(500) DEFAULT '',
                    TotalAmount DECIMAL(18,2) DEFAULT 0,
                    IsActive    BIT DEFAULT 1,
                    CreatedBy   INT NULL,
                    CreatedAt   DATETIME DEFAULT GETDATE()
                )
            """.trimIndent())
        } catch (_: Exception) {}

        // WPF-compatible WasteEntryItems (uses ProductId, matching WPF WasteRepository)
        try {
            db.execute("""
                IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'WasteEntryItems')
                CREATE TABLE WasteEntryItems (
                    ItemId    INT IDENTITY(1,1) PRIMARY KEY,
                    WasteId   INT NOT NULL,
                    ProductId INT NOT NULL,
                    Quantity  DECIMAL(18,3) DEFAULT 1,
                    Unit      NVARCHAR(50)  DEFAULT 'kg',
                    Rate      DECIMAL(18,2) DEFAULT 0,
                    Amount    DECIMAL(18,2) DEFAULT 0,
                    Reason    NVARCHAR(200) DEFAULT ''
                )
            """.trimIndent())
        } catch (_: Exception) {}

        // WPF-compatible Recipes header table
        try {
            db.execute("""
                IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'Recipes')
                CREATE TABLE Recipes (
                    RecipeId   INT IDENTITY(1,1) PRIMARY KEY,
                    ProductId  INT NOT NULL,
                    SizeId     INT NULL,
                    RecipeName NVARCHAR(200) DEFAULT '',
                    IsActive   BIT DEFAULT 1,
                    CreatedAt  DATETIME DEFAULT GETDATE(),
                    CreatedBy  INT NULL,
                    UpdatedAt  DATETIME NULL,
                    UpdatedBy  INT NULL
                )
            """.trimIndent())
        } catch (_: Exception) {}

        // WPF-compatible RecipeItems detail table
        try {
            db.execute("""
                IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'RecipeItems')
                CREATE TABLE RecipeItems (
                    RecipeItemId  INT IDENTITY(1,1) PRIMARY KEY,
                    RecipeId      INT NOT NULL,
                    ProductId     INT NOT NULL,
                    QuantityUsed  DECIMAL(18,3) NOT NULL DEFAULT 1,
                    Unit          NVARCHAR(50) DEFAULT 'kg'
                )
            """.trimIndent())
        } catch (_: Exception) {}
    }

    // ── Raw Materials — backed by Products table (ProductType = 'RawMaterial') ──

    suspend fun getAllMaterials(search: String = ""): List<RawMaterial> {
        val where  = if (search.isNotBlank()) "AND p.ProductName LIKE ?" else ""
        val params = if (search.isNotBlank()) listOf<Any?>("%$search%") else emptyList()
        return try {
            db.query("""
                SELECT p.ProductId, p.ProductName, ISNULL(p.Unit,'kg') AS Unit,
                       ISNULL(p.CurrentStock,0) AS CurrentStock,
                       ISNULL(p.ReorderLevel,0) AS ReorderLevel,
                       ISNULL(p.PurchasePrice,0) AS PurchasePrice,
                       ISNULL(p.IsActive,1) AS IsActive
                FROM Products p
                WHERE (p.ProductType = 'RawMaterial' OR ISNULL(p.IsStockManaged,0)=1)
                  AND ISNULL(p.IsActive,1)=1 $where
                ORDER BY p.ProductName
            """.trimIndent(), params) { rs ->
                RawMaterial(
                    materialId    = rs.getInt("ProductId"),
                    materialName  = rs.getString("ProductName") ?: "",
                    unit          = rs.getString("Unit") ?: "kg",
                    currentStock  = rs.getDouble("CurrentStock"),
                    minStockLevel = rs.getDouble("ReorderLevel"),
                    costPerUnit   = rs.getDouble("PurchasePrice"),
                    isActive      = rs.getBoolean("IsActive")
                )
            }
        } catch (_: Exception) {
            try {
                // Fallback for SQL Server installs missing optional columns (Unit, CurrentStock, etc.)
                db.query("""
                    SELECT p.ProductId, p.ProductName, 'kg' AS Unit,
                           0 AS CurrentStock, 0 AS ReorderLevel, 0 AS PurchasePrice, 1 AS IsActive
                    FROM Products p
                    WHERE (p.ProductType = 'RawMaterial' OR p.IsStockManaged=1)
                      AND p.IsActive=1 $where
                    ORDER BY p.ProductName
                """.trimIndent(), params) { rs ->
                    RawMaterial(
                        materialId    = rs.getInt("ProductId"),
                        materialName  = rs.getString("ProductName") ?: "",
                        unit          = "kg",
                        currentStock  = 0.0,
                        minStockLevel = 0.0,
                        costPerUnit   = 0.0,
                        isActive      = true
                    )
                }
            } catch (_: Exception) { emptyList() }
        }
    }

    suspend fun addMaterial(name: String, unit: String, stock: Double, minStock: Double, costPerUnit: Double, createdBy: Int = 0) {
        val code = generateMaterialCode()
        val id = try {
            db.insertAndGetId("""
                INSERT INTO Products
                    (ProductCode, ProductName, ProductType, Unit, OpeningStock, CurrentStock,
                     ReorderLevel, PurchasePrice, SalePrice, IsStockManaged, IsActive, DisplayOrder)
                VALUES (?,?,?,?,?,?,?,?,0,1,1,
                    (SELECT ISNULL(MAX(DisplayOrder),0)+1 FROM Products WHERE ProductType='RawMaterial'))
            """.trimIndent(), listOf(code, name.trim(), "RawMaterial", unit, stock, stock, minStock, costPerUnit))
        } catch (_: Exception) {
            // Fallback without optional columns
            db.insertAndGetId(
                "INSERT INTO Products (ProductCode, ProductName, ProductType, SalePrice, IsStockManaged, IsActive) VALUES (?,?,?,0,1,1)",
                listOf(code, name.trim(), "RawMaterial")
            )
        }
        // Record opening stock in ledger if > 0
        if (stock > 0) {
            try {
                db.execute(
                    """INSERT INTO InventoryLedger
                       (ProductId, TransactionDate, ReferenceType, ReferenceId,
                        InQty, OutQty, BalanceQty, Rate, Amount, Remarks, CreatedBy, CreatedAt)
                       VALUES (?,GETDATE(),'Opening',?,?,0,?,?,?,?,?,GETDATE())""",
                    listOf(id, id, stock, stock, costPerUnit, stock * costPerUnit, "Opening stock", createdBy)
                )
            } catch (_: Exception) {}
        }
        runCatching { audit.writeAudit(createdBy, "INSERT", "Products", id) }
    }

    suspend fun updateMaterial(materialId: Int, name: String, unit: String, minStock: Double, costPerUnit: Double) {
        try {
            db.execute(
                "UPDATE Products SET ProductName=?, Unit=?, ReorderLevel=?, PurchasePrice=? WHERE ProductId=?",
                listOf(name.trim(), unit, minStock, costPerUnit, materialId)
            )
        } catch (_: Exception) {
            db.execute(
                "UPDATE Products SET ProductName=? WHERE ProductId=?",
                listOf(name.trim(), materialId)
            )
        }
        runCatching { audit.writeAudit(0, "UPDATE", "Products", materialId) }
    }

    suspend fun adjustStock(materialId: Int, delta: Double, refType: String = "Adjustment", refId: Int? = null, rate: Double = 0.0, remarks: String = "") {
        try {
            db.execute(
                "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) + ? WHERE ProductId = ?",
                listOf(delta, materialId)
            )
        } catch (_: Exception) { return }
        try {
            val inQty  = if (delta > 0) delta else 0.0
            val outQty = if (delta < 0) -delta else 0.0
            db.execute(
                "INSERT INTO InventoryLedger (ProductId, TransactionDate, ReferenceType, ReferenceId, InQty, OutQty, Rate, Amount, Remarks) VALUES (?,GETDATE(),?,?,?,?,?,?,?)",
                listOf(materialId, refType, refId, inQty, outQty, rate, (inQty + outQty) * rate, remarks.ifBlank { refType })
            )
        } catch (_: Exception) {}
        runCatching { audit.writeAudit(0, "ADJUSTMENT", "Products", materialId) }
    }

    suspend fun setStock(materialId: Int, stock: Double) {
        val current = try {
            db.queryOne("SELECT ISNULL(CurrentStock,0) AS CurrentStock FROM Products WHERE ProductId=?",
                listOf(materialId)) { it.getDouble("CurrentStock") } ?: 0.0
        } catch (_: Exception) { 0.0 }
        try {
            db.execute("UPDATE Products SET CurrentStock = ? WHERE ProductId = ?", listOf(stock, materialId))
        } catch (_: Exception) { return }
        val delta = stock - current
        try {
            val inQty  = if (delta > 0) delta else 0.0
            val outQty = if (delta < 0) -delta else 0.0
            db.execute(
                "INSERT INTO InventoryLedger (ProductId, TransactionDate, ReferenceType, InQty, OutQty, Rate, Amount, Remarks) VALUES (?,GETDATE(),'Adjustment',?,?,0,0,'Stock Set')",
                listOf(materialId, inQty, outQty)
            )
        } catch (_: Exception) {}
    }

    suspend fun deleteMaterial(materialId: Int) {
        db.execute("UPDATE Products SET IsActive = 0 WHERE ProductId = ?", listOf(materialId))
        runCatching { audit.writeAudit(0, "DELETE", "Products", materialId) }
    }

    private suspend fun generateMaterialCode(): String {
        val seq = try {
            db.queryOne("SELECT ISNULL(COUNT(1),0)+1 AS NextSeq FROM Products WHERE ProductType='RawMaterial'") { it.getInt("NextSeq") } ?: 1
        } catch (_: Exception) { 1 }
        return "RM-" + seq.toString().padStart(4, '0')
    }

    // ── Recipes ───────────────────────────────────────────────────────────────

    suspend fun getSizesForProduct(productId: Int): List<ProductSize> = try {
        db.query(
            "SELECT SizeId, ProductId, SizeName, ISNULL(Price,0) AS Price FROM ProductSizes WHERE ProductId=? AND ISNULL(IsActive,1)=1 ORDER BY SizeName",
            listOf(productId)
        ) { rs ->
            ProductSize(
                sizeId      = rs.getInt("SizeId"),
                productId   = productId,
                sizeName    = rs.getString("SizeName") ?: "",
                price       = rs.getDouble("Price")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getRecipeForProduct(productId: Int, sizeId: Int? = null): List<RecipeItem> {
        // WPF uses Recipes (header) + RecipeItems (detail). SizeId is nullable for non-pizza products.
        val sizeWhere = if (sizeId == null) "AND r.SizeId IS NULL" else "AND r.SizeId = ?"
        val params: List<Any?> = if (sizeId == null) listOf(productId) else listOf(productId, sizeId)
        return try {
            db.query("""
                SELECT ri.RecipeItemId, r.ProductId, fin.ProductName,
                       ri.ProductId AS MaterialId, mat.ProductName AS MaterialName,
                       ISNULL(ri.Unit, ISNULL(mat.Unit,'kg')) AS Unit,
                       ri.QuantityUsed AS QuantityRequired,
                       ISNULL(mat.PurchasePrice,0) AS CostPerUnit
                FROM RecipeItems ri
                JOIN Recipes r ON r.RecipeId = ri.RecipeId
                JOIN Products fin ON fin.ProductId = r.ProductId
                JOIN Products mat ON mat.ProductId = ri.ProductId
                WHERE r.ProductId = ? AND r.IsActive = 1 $sizeWhere
                ORDER BY mat.ProductName
            """.trimIndent(), params) { rs ->
                RecipeItem(
                    recipeId         = rs.getInt("RecipeItemId"),
                    productId        = rs.getInt("ProductId"),
                    productName      = rs.getString("ProductName") ?: "",
                    materialId       = rs.getInt("MaterialId"),
                    materialName     = rs.getString("MaterialName") ?: "",
                    unit             = rs.getString("Unit") ?: "",
                    quantityRequired = rs.getDouble("QuantityRequired"),
                    costPerUnit      = rs.getDouble("CostPerUnit")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun upsertRecipeItem(productId: Int, materialId: Int, quantity: Double, sizeId: Int? = null) {
        // 1. Find or create the Recipes header for this product+size
        val sizeWhere = if (sizeId == null) "AND SizeId IS NULL" else "AND SizeId = ?"
        val findParams: List<Any?> = if (sizeId == null) listOf(productId) else listOf(productId, sizeId)

        val existingId = try {
            db.queryOne(
                "SELECT TOP 1 RecipeId FROM Recipes WHERE ProductId = ? AND IsActive = 1 $sizeWhere",
                findParams
            ) { it.getInt("RecipeId") }
        } catch (_: Exception) { null }

        val recipeId: Int = if (existingId == null || existingId == 0) {
            val pName = try {
                db.queryOne("SELECT ProductName FROM Products WHERE ProductId = ?", listOf(productId)) {
                    it.getString("ProductName") ?: ""
                } ?: ""
            } catch (_: Exception) { "" }
            val sName = if (sizeId != null) try {
                db.queryOne("SELECT SizeName FROM ProductSizes WHERE SizeId = ?", listOf(sizeId)) {
                    it.getString("SizeName") ?: ""
                } ?: ""
            } catch (_: Exception) { "" } else ""
            val rName = if (sName.isBlank()) "$pName Recipe" else "$pName ($sName) Recipe"
            try {
                val newId = db.insertAndGetId(
                    "INSERT INTO Recipes (ProductId, SizeId, RecipeName, IsActive, CreatedAt) VALUES (?,?,?,1,GETDATE())",
                    listOf(productId, sizeId, rName)
                )
                runCatching { audit.writeAudit(0, "INSERT", "Recipes", newId) }
                newId
            } catch (_: Exception) { return }
        } else {
            runCatching { audit.writeAudit(0, "UPDATE", "Recipes", existingId) }
            existingId
        }

        if (recipeId <= 0) return

        // 2. Upsert the ingredient row in RecipeItems
        val matUnit = try {
            db.queryOne("SELECT ISNULL(Unit,'kg') AS Unit FROM Products WHERE ProductId = ?", listOf(materialId)) {
                it.getString("Unit") ?: "kg"
            } ?: "kg"
        } catch (_: Exception) { "kg" }

        try {
            db.execute("""
                MERGE INTO RecipeItems AS target
                USING (VALUES (?,?)) AS src (RecipeId, ProductId)
                ON target.RecipeId = src.RecipeId AND target.ProductId = src.ProductId
                WHEN MATCHED THEN UPDATE SET QuantityUsed = ?
                WHEN NOT MATCHED THEN INSERT (RecipeId, ProductId, QuantityUsed, Unit) VALUES (?,?,?,?)
            """.trimIndent(), listOf(recipeId, materialId, quantity, recipeId, materialId, quantity, matUnit))
        } catch (_: Exception) {
            try {
                val exists = (db.queryOne(
                    "SELECT COUNT(1) AS Cnt FROM RecipeItems WHERE RecipeId = ? AND ProductId = ?",
                    listOf(recipeId, materialId)
                ) { it.getInt("Cnt") } ?: 0) > 0
                if (exists) {
                    db.execute("UPDATE RecipeItems SET QuantityUsed = ? WHERE RecipeId = ? AND ProductId = ?",
                        listOf(quantity, recipeId, materialId))
                } else {
                    db.execute("INSERT INTO RecipeItems (RecipeId, ProductId, QuantityUsed, Unit) VALUES (?,?,?,?)",
                        listOf(recipeId, materialId, quantity, matUnit))
                }
            } catch (_: Exception) {}
        }

        try { db.execute("UPDATE Products SET IsRecipeBased = 1 WHERE ProductId = ?", listOf(productId)) }
        catch (_: Exception) {}
    }

    suspend fun deleteRecipeItem(recipeItemId: Int) {
        val rootProductId = try {
            db.queryOne("""
                SELECT r.ProductId AS RootPid
                FROM RecipeItems ri
                JOIN Recipes r ON r.RecipeId = ri.RecipeId
                WHERE ri.RecipeItemId = ?
            """.trimIndent(), listOf(recipeItemId)) { it.getInt("RootPid") }
        } catch (_: Exception) { null }

        db.execute("DELETE FROM RecipeItems WHERE RecipeItemId = ?", listOf(recipeItemId))

        if (rootProductId != null) {
            try {
                val remaining = db.queryOne("""
                    SELECT COUNT(1) AS Cnt FROM RecipeItems ri
                    JOIN Recipes r ON r.RecipeId = ri.RecipeId
                    WHERE r.ProductId = ? AND r.IsActive = 1
                """.trimIndent(), listOf(rootProductId)) { it.getInt("Cnt") } ?: 0
                if (remaining == 0) {
                    db.execute("UPDATE Products SET IsRecipeBased = 0 WHERE ProductId = ?", listOf(rootProductId))
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun getRecipeCost(productId: Int, sizeId: Int? = null): Double {
        val sizeWhere = if (sizeId == null) "AND r.SizeId IS NULL" else "AND r.SizeId = ?"
        val params: List<Any?> = if (sizeId == null) listOf(productId) else listOf(productId, sizeId)
        return try {
            db.queryOne("""
                SELECT ISNULL(SUM(ri.QuantityUsed * ISNULL(mat.PurchasePrice,0)), 0) AS TotalCost
                FROM RecipeItems ri
                JOIN Recipes r ON r.RecipeId = ri.RecipeId
                JOIN Products mat ON mat.ProductId = ri.ProductId
                WHERE r.ProductId = ? AND r.IsActive = 1 $sizeWhere
            """.trimIndent(), params) { it.getDouble("TotalCost") } ?: 0.0
        } catch (_: Exception) { 0.0 }
    }

    // ── New recipe-list API (used by redesigned RecipeScreen) ─────────────────

    suspend fun getAllRecipes(): List<RecipeHeader> = try {
        db.query("""
            SELECT r.RecipeId, r.ProductId, p.ProductName,
                   r.SizeId, ISNULL(ps.SizeName,'') AS SizeName,
                   ISNULL(r.RecipeName,'') AS RecipeName,
                   (SELECT COUNT(1) FROM RecipeItems WHERE RecipeId = r.RecipeId) AS ItemCount
            FROM Recipes r
            JOIN Products p ON p.ProductId = r.ProductId
            LEFT JOIN ProductSizes ps ON ps.SizeId = r.SizeId
            WHERE r.IsActive = 1
            ORDER BY p.ProductName, ps.SizeName
        """.trimIndent()) { rs ->
            RecipeHeader(
                recipeId    = rs.getInt("RecipeId"),
                productId   = rs.getInt("ProductId"),
                productName = rs.getString("ProductName") ?: "",
                sizeId      = rs.getInt("SizeId").takeIf { !rs.wasNull() },
                sizeName    = rs.getString("SizeName") ?: "",
                recipeName  = rs.getString("RecipeName") ?: "",
                itemCount   = rs.getInt("ItemCount")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun createRecipe(productId: Int, sizeId: Int?, recipeName: String): Int {
        val newId = db.insertAndGetId(
            "INSERT INTO Recipes (ProductId, SizeId, RecipeName, IsActive, CreatedAt) VALUES (?,?,?,1,GETDATE())",
            listOf(productId, sizeId, recipeName.trim().ifBlank { "Recipe" })
        )
        runCatching { audit.writeAudit(0, "INSERT", "Recipes", newId) }
        return newId
    }

    suspend fun deleteRecipe(recipeId: Int) {
        val productId = try {
            db.queryOne("SELECT ProductId FROM Recipes WHERE RecipeId = ?", listOf(recipeId)) { it.getInt("ProductId") }
        } catch (_: Exception) { null }

        db.execute("DELETE FROM RecipeItems WHERE RecipeId = ?", listOf(recipeId))
        db.execute("UPDATE Recipes SET IsActive = 0 WHERE RecipeId = ?", listOf(recipeId))
        runCatching { audit.writeAudit(0, "DELETE", "Recipes", recipeId) }

        if (productId != null) {
            try {
                val remaining = db.queryOne("""
                    SELECT COUNT(1) AS Cnt FROM RecipeItems ri
                    JOIN Recipes r ON r.RecipeId = ri.RecipeId
                    WHERE r.ProductId = ? AND r.IsActive = 1
                """.trimIndent(), listOf(productId)) { it.getInt("Cnt") } ?: 0
                if (remaining == 0)
                    db.execute("UPDATE Products SET IsRecipeBased = 0 WHERE ProductId = ?", listOf(productId))
            } catch (_: Exception) {}
        }
    }

    suspend fun getRecipeItems(recipeId: Int): List<RecipeItem> = try {
        db.query("""
            SELECT ri.RecipeItemId, r.ProductId, fin.ProductName,
                   ri.ProductId AS MaterialId, mat.ProductName AS MaterialName,
                   ISNULL(ri.Unit, ISNULL(mat.Unit,'kg')) AS Unit,
                   ri.QuantityUsed AS QuantityRequired,
                   ISNULL(mat.PurchasePrice,0) AS CostPerUnit
            FROM RecipeItems ri
            JOIN Recipes r ON r.RecipeId = ri.RecipeId
            JOIN Products fin ON fin.ProductId = r.ProductId
            JOIN Products mat ON mat.ProductId = ri.ProductId
            WHERE ri.RecipeId = ?
            ORDER BY mat.ProductName
        """.trimIndent(), listOf(recipeId)) { rs ->
            RecipeItem(
                recipeId         = rs.getInt("RecipeItemId"),
                productId        = rs.getInt("ProductId"),
                productName      = rs.getString("ProductName") ?: "",
                materialId       = rs.getInt("MaterialId"),
                materialName     = rs.getString("MaterialName") ?: "",
                unit             = rs.getString("Unit") ?: "",
                quantityRequired = rs.getDouble("QuantityRequired"),
                costPerUnit      = rs.getDouble("CostPerUnit")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun addIngredientToRecipe(recipeId: Int, productId: Int, materialId: Int, quantity: Double) {
        val matUnit = try {
            db.queryOne("SELECT ISNULL(Unit,'kg') AS Unit FROM Products WHERE ProductId = ?", listOf(materialId)) {
                it.getString("Unit") ?: "kg"
            } ?: "kg"
        } catch (_: Exception) { "kg" }

        try {
            db.execute("""
                MERGE INTO RecipeItems AS target
                USING (VALUES (?,?)) AS src (RecipeId, ProductId)
                ON target.RecipeId = src.RecipeId AND target.ProductId = src.ProductId
                WHEN MATCHED THEN UPDATE SET QuantityUsed = ?
                WHEN NOT MATCHED THEN INSERT (RecipeId, ProductId, QuantityUsed, Unit) VALUES (?,?,?,?)
            """.trimIndent(), listOf(recipeId, materialId, quantity, recipeId, materialId, quantity, matUnit))
        } catch (_: Exception) {
            try {
                val exists = (db.queryOne(
                    "SELECT COUNT(1) AS Cnt FROM RecipeItems WHERE RecipeId = ? AND ProductId = ?",
                    listOf(recipeId, materialId)
                ) { it.getInt("Cnt") } ?: 0) > 0
                if (exists)
                    db.execute("UPDATE RecipeItems SET QuantityUsed = ? WHERE RecipeId = ? AND ProductId = ?",
                        listOf(quantity, recipeId, materialId))
                else
                    db.execute("INSERT INTO RecipeItems (RecipeId, ProductId, QuantityUsed, Unit) VALUES (?,?,?,?)",
                        listOf(recipeId, materialId, quantity, matUnit))
            } catch (_: Exception) {}
        }
        try { db.execute("UPDATE Products SET IsRecipeBased = 1 WHERE ProductId = ?", listOf(productId)) }
        catch (_: Exception) {}
    }

    suspend fun getRecipeCostById(recipeId: Int): Double = try {
        db.queryOne("""
            SELECT ISNULL(SUM(ri.QuantityUsed * ISNULL(mat.PurchasePrice,0)), 0) AS TotalCost
            FROM RecipeItems ri
            JOIN Products mat ON mat.ProductId = ri.ProductId
            WHERE ri.RecipeId = ?
        """.trimIndent(), listOf(recipeId)) { it.getDouble("TotalCost") } ?: 0.0
    } catch (_: Exception) { 0.0 }

    // SalePrice for a recipe: uses ProductSizes.Price for pizza (sizeId != null), else Products.SalePrice
    suspend fun getSalePrice(productId: Int, sizeId: Int?): Double = try {
        if (sizeId != null) {
            db.queryOne(
                "SELECT ISNULL(Price,0) AS Price FROM ProductSizes WHERE SizeId = ?",
                listOf(sizeId)
            ) { it.getDouble("Price") } ?: 0.0
        } else {
            db.queryOne(
                "SELECT ISNULL(SalePrice,0) AS SalePrice FROM Products WHERE ProductId = ?",
                listOf(productId)
            ) { it.getDouble("SalePrice") } ?: 0.0
        }
    } catch (_: Exception) { 0.0 }

    // ── Stock Ledger — backed by InventoryLedger table ─────────────────────────

    suspend fun getOpeningBalance(materialId: Int, fromDate: java.util.Date): Double = try {
        db.queryOne(
            "SELECT ISNULL(SUM(InQty - OutQty), 0) AS bal FROM InventoryLedger WHERE ProductId = ? AND TransactionDate < ?",
            listOf(materialId, java.sql.Date(fromDate.time))
        ) { it.getDouble("bal") } ?: 0.0
    } catch (_: Exception) { 0.0 }

    suspend fun getLedgerRows(materialId: Int, fromDate: java.util.Date, toDate: java.util.Date): List<InventoryLedger> {
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
                listOf(materialId, java.sql.Date(fromDate.time), java.sql.Date(toDate.time))
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

        val opening = getOpeningBalance(materialId, fromDate)
        var running = opening
        // compute balance ascending, then reverse so latest is on top
        return rows.map { r ->
            running += r.inQty - r.outQty
            r.copy(balanceQty = running)
        }.reversed()
    }

    // ── Wastage — backed by WasteEntries / WasteEntryItems ────────────────────

    suspend fun getWasteEntries(fromDate: java.util.Date, toDate: java.util.Date): List<WasteEntry> = try {
        db.query("""
            SELECT w.WasteId, w.WasteDate, ISNULL(w.Notes,'') AS Notes,
                   COUNT(i.ItemId) AS ItemCount,
                   ISNULL(SUM(i.Amount), 0) AS TotalAmount
            FROM WasteEntries w
            LEFT JOIN WasteEntryItems i ON i.WasteId = w.WasteId
            WHERE ISNULL(w.IsActive,1)=1
              AND w.WasteDate >= ? AND w.WasteDate < DATEADD(day,1,CAST(? AS DATE))
            GROUP BY w.WasteId, w.WasteDate, w.Notes
            ORDER BY w.WasteDate DESC, w.WasteId DESC
        """.trimIndent(), listOf(java.sql.Date(fromDate.time), java.sql.Date(toDate.time))
        ) { rs ->
            WasteEntry(
                wasteId     = rs.getInt("WasteId"),
                wasteDate   = rs.getTimestamp("WasteDate") ?: java.util.Date(),
                notes       = rs.getString("Notes") ?: "",
                itemCount   = rs.getInt("ItemCount"),
                totalAmount = rs.getDouble("TotalAmount")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun getWasteItems(wasteId: Int): List<WasteEntryItem> = try {
        db.query("""
            SELECT i.ItemId, i.WasteId, i.ProductId, p.ProductName AS ItemName,
                   i.Quantity, ISNULL(i.Unit,'kg') AS Unit,
                   ISNULL(i.Rate,0) AS Rate, ISNULL(i.Amount,0) AS Amount,
                   ISNULL(i.Reason,'') AS Reason
            FROM WasteEntryItems i
            JOIN Products p ON p.ProductId = i.ProductId
            WHERE i.WasteId = ?
        """.trimIndent(), listOf(wasteId)) { rs ->
            WasteEntryItem(
                itemId     = try { rs.getInt("ItemId") } catch (_: Exception) { 0 },
                wasteId    = rs.getInt("WasteId"),
                materialId = rs.getInt("ProductId"),
                itemName   = rs.getString("ItemName") ?: "",
                quantity   = rs.getDouble("Quantity"),
                unit       = rs.getString("Unit") ?: "kg",
                rate       = rs.getDouble("Rate"),
                amount     = rs.getDouble("Amount"),
                reason     = rs.getString("Reason") ?: ""
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun saveWasteEntry(wasteDate: java.util.Date, notes: String, items: List<WasteEntryItem>) {
        db.transaction { conn ->
            val total = items.sumOf { it.quantity * it.rate }
            val wasteId = db.insertAndGetIdSync(
                conn,
                "INSERT INTO WasteEntries (WasteDate, Notes, TotalAmount, IsActive, CreatedAt) VALUES (?,?,?,1,GETDATE())",
                listOf(java.sql.Timestamp(wasteDate.time), notes, total)
            )
            for (item in items) {
                try {
                    db.executeSync(
                        conn,
                        "INSERT INTO WasteEntryItems (WasteId, ProductId, Quantity, Unit, Rate, Amount, Reason) VALUES (?,?,?,?,?,?,?)",
                        listOf(wasteId, item.materialId, item.quantity, item.unit,
                            item.rate, item.quantity * item.rate, item.reason)
                    )
                } catch (_: Exception) {}
                try {
                    db.executeSync(
                        conn,
                        "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) - ? WHERE ProductId = ?",
                        listOf(item.quantity, item.materialId)
                    )
                    db.executeSync(
                        conn,
                        "INSERT INTO InventoryLedger (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,Rate,Amount,Remarks) VALUES (?,GETDATE(),'Waste',?,0,?,?,?,?)",
                        listOf(item.materialId, wasteId, item.quantity,
                            item.rate, item.quantity * item.rate,
                            if (item.reason.isNotBlank()) item.reason else "Waste #$wasteId")
                    )
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun deleteWasteEntry(wasteId: Int) {
        val items = try {
            db.query(
                "SELECT ProductId, Quantity, Rate FROM WasteEntryItems WHERE WasteId = ?",
                listOf(wasteId)
            ) { rs -> Triple(rs.getInt("ProductId"), rs.getDouble("Quantity"), rs.getDouble("Rate")) }
        } catch (_: Exception) { emptyList() }

        db.transaction { conn ->
            for ((productId, qty, rate) in items) {
                try {
                    db.executeSync(
                        conn,
                        "UPDATE Products SET CurrentStock = ISNULL(CurrentStock,0) + ? WHERE ProductId = ?",
                        listOf(qty, productId)
                    )
                    db.executeSync(
                        conn,
                        "INSERT INTO InventoryLedger (ProductId,TransactionDate,ReferenceType,ReferenceId,InQty,OutQty,Rate,Amount,Remarks) VALUES (?,GETDATE(),'WasteReversal',?,?,0,?,?,?)",
                        listOf(productId, wasteId, qty, rate, qty * rate,
                            "Waste #$wasteId reversed")
                    )
                } catch (_: Exception) {}
            }
            try {
                db.executeSync(conn, "DELETE FROM WasteEntryItems WHERE WasteId = ?", listOf(wasteId))
                db.executeSync(conn, "UPDATE WasteEntries SET IsActive = 0 WHERE WasteId = ?", listOf(wasteId))
            } catch (_: Exception) {}
        }
    }

    // ── Stock Valuation / Reorder ─────────────────────────────────────────────

    private fun mapStockRow(rs: ResultSet) = RawMaterial(
        materialId    = rs.getInt("ProductId"),
        materialName  = rs.getString("ProductName") ?: "",
        unit          = try { rs.getString("Unit") ?: "Pcs" } catch (_: Exception) { "Pcs" },
        currentStock  = try { rs.getDouble("CurrentStock") } catch (_: Exception) { 0.0 },
        minStockLevel = try { rs.getDouble("ReorderLevel") } catch (_: Exception) { 0.0 },
        costPerUnit   = try { rs.getDouble("CostPerUnit") } catch (_: Exception) { 0.0 },
        isActive      = try { rs.getBoolean("IsActive") } catch (_: Exception) { true }
    )

    /** All stock-managed products (raw materials + finished) with current stock > 0, for valuation. */
    suspend fun getStockValuation(): List<RawMaterial> = try {
        db.query("""
            SELECT ProductId, ProductName, ISNULL(Unit,'Pcs') AS Unit,
                   ISNULL(CurrentStock,0) AS CurrentStock,
                   ISNULL(ReorderLevel,0) AS ReorderLevel,
                   ISNULL(PurchasePrice,0) AS CostPerUnit,
                   ISNULL(IsActive,1) AS IsActive
            FROM Products
            WHERE ISNULL(IsStockManaged,0)=1 AND ISNULL(IsActive,1)=1
              AND ISNULL(CurrentStock,0) > 0
            ORDER BY ProductName
        """.trimIndent()) { rs -> mapStockRow(rs) }
    } catch (_: Exception) {
        try {
            db.query("""
                SELECT ProductId, ProductName, 'Pcs' AS Unit,
                       ISNULL(CurrentStock,0) AS CurrentStock,
                       0 AS ReorderLevel, 0 AS CostPerUnit,
                       ISNULL(IsActive,1) AS IsActive
                FROM Products WHERE ISNULL(IsStockManaged,0)=1 AND ISNULL(IsActive,1)=1
                  AND ISNULL(CurrentStock,0)>0 ORDER BY ProductName
            """.trimIndent()) { rs -> mapStockRow(rs) }
        } catch (_: Exception) { emptyList() }
    }

    /** All stock-managed products at or below reorder level, for reorder alerts. */
    suspend fun getReorderList(): List<RawMaterial> = try {
        db.query("""
            SELECT ProductId, ProductName, ISNULL(Unit,'Pcs') AS Unit,
                   ISNULL(CurrentStock,0) AS CurrentStock,
                   ISNULL(ReorderLevel,0) AS ReorderLevel,
                   ISNULL(PurchasePrice,0) AS CostPerUnit,
                   ISNULL(IsActive,1) AS IsActive
            FROM Products
            WHERE ISNULL(IsStockManaged,0)=1 AND ISNULL(IsActive,1)=1
              AND ISNULL(ReorderLevel,0) > 0
              AND ISNULL(CurrentStock,0) <= ISNULL(ReorderLevel,0)
            ORDER BY CurrentStock ASC, ProductName
        """.trimIndent()) { rs -> mapStockRow(rs) }
    } catch (_: Exception) {
        try {
            db.query("""
                SELECT ProductId, ProductName, 'Pcs' AS Unit,
                       ISNULL(CurrentStock,0) AS CurrentStock,
                       0 AS ReorderLevel, 0 AS CostPerUnit,
                       ISNULL(IsActive,1) AS IsActive
                FROM Products WHERE ISNULL(IsStockManaged,0)=1 AND ISNULL(IsActive,1)=1
                  AND ISNULL(ReorderLevel,0)>0 AND ISNULL(CurrentStock,0)<=ISNULL(ReorderLevel,0)
                ORDER BY CurrentStock ASC, ProductName
            """.trimIndent()) { rs -> mapStockRow(rs) }
        } catch (_: Exception) { emptyList() }
    }

    // Returns only Pizza and Normal products — matching WPF RecipeViewModel product filter
    suspend fun getProductsWithRecipes(): List<Triple<Int, String, String>> = try {
        db.query("""
            SELECT p.ProductId, p.ProductName, ISNULL(p.ProductType,'Normal') AS ProductType
            FROM Products p
            WHERE ISNULL(p.IsActive,1)=1
              AND ISNULL(p.ProductType,'Normal') IN ('Pizza','Normal')
            ORDER BY p.ProductName
        """.trimIndent()) { rs ->
            Triple(rs.getInt("ProductId"), rs.getString("ProductName") ?: "", rs.getString("ProductType") ?: "Normal")
        }
    } catch (_: Exception) {
        try {
            db.query(
                "SELECT ProductId, ProductName FROM Products WHERE ISNULL(IsActive,1)=1 ORDER BY ProductName",
                emptyList()
            ) { rs -> Triple(rs.getInt("ProductId"), rs.getString("ProductName") ?: "", "Normal") }
        } catch (_: Exception) { emptyList() }
    }
}
