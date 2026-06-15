package com.fastpos.android.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.fastpos.android.utils.PasswordHelper

private const val DB_NAME    = "fastpos_standalone.db"
private const val DB_VERSION = 51

/**
 * Opens (or creates) the standalone SQLite database with a schema that mirrors
 * the SQL Server FASTPOSDB schema used by all repositories.
 * Column names match the SQL Server schema exactly so repositories work in both modes.
 */
class LocalSchemaHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            createAllTables(db)
            createCompatViews(db)
            seedDefaultData(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Incremental migrations — preserves all existing data
        db.beginTransaction()
        try {
            var v = oldVersion
            fun tryExec(sql: String) { try { db.execSQL(sql) } catch (_: Exception) {} }
            fun addCol(table: String, col: String, type: String) =
                tryExec("ALTER TABLE $table ADD COLUMN $col $type")

            if (v < 39) addCol("SalaryPayments", "BranchId", "INTEGER NOT NULL DEFAULT 1")
            if (v < 40) addCol("EmployeeAdvances", "BranchId", "INTEGER NOT NULL DEFAULT 1")
            if (v < 41) {
                addCol("Waiters", "CreatedAt", "TEXT DEFAULT (datetime('now'))")
                addCol("Waiters", "CreatedBy", "INTEGER")
            }
            if (v < 42) {
                // WalletTransactions column renames: recreate table preserving data
                tryExec("ALTER TABLE WalletTransactions RENAME TO _wt_old")
                tryExec("""CREATE TABLE IF NOT EXISTS WalletTransactions (
                    TransId INTEGER PRIMARY KEY AUTOINCREMENT,
                    CustomerId INTEGER NOT NULL, OrderId INTEGER,
                    TransType TEXT NOT NULL, Amount REAL NOT NULL DEFAULT 0,
                    TransDate TEXT DEFAULT (datetime('now')), Notes TEXT DEFAULT '',
                    CreatedBy INTEGER)""")
                tryExec("""INSERT INTO WalletTransactions (TransId,CustomerId,OrderId,TransType,Amount,TransDate,CreatedBy)
                    SELECT TransId,CustomerId,OrderId,
                    COALESCE(TransType,TransactionType,'TopUp') AS TransType,
                    Amount,
                    COALESCE(TransDate,CreatedAt,datetime('now')) AS TransDate,
                    CreatedBy FROM _wt_old""")
                tryExec("DROP TABLE IF EXISTS _wt_old")
                // CustomerWallet: rename UpdatedAt to LastUpdated
                tryExec("ALTER TABLE CustomerWallet RENAME TO _cw_old")
                tryExec("""CREATE TABLE IF NOT EXISTS CustomerWallet (
                    WalletId INTEGER PRIMARY KEY AUTOINCREMENT,
                    CustomerId INTEGER NOT NULL UNIQUE,
                    Balance REAL NOT NULL DEFAULT 0,
                    LastUpdated TEXT DEFAULT (datetime('now')))""")
                tryExec("INSERT INTO CustomerWallet SELECT WalletId,CustomerId,Balance,COALESCE(LastUpdated,UpdatedAt,datetime('now')) FROM _cw_old")
                tryExec("DROP TABLE IF EXISTS _cw_old")
            }
            if (v < 43) {
                // Waiters: rename LinkedEmployeeId → EmployeeId
                tryExec("ALTER TABLE Waiters RENAME TO _w_old")
                tryExec("""CREATE TABLE IF NOT EXISTS Waiters (
                    WaiterId INTEGER PRIMARY KEY AUTOINCREMENT,
                    WaiterName TEXT NOT NULL, Phone TEXT DEFAULT '',
                    AreaId INTEGER, EmployeeId INTEGER,
                    IsActive INTEGER NOT NULL DEFAULT 1,
                    CreatedAt TEXT DEFAULT (datetime('now')), CreatedBy INTEGER)""")
                tryExec("""INSERT INTO Waiters (WaiterId,WaiterName,Phone,AreaId,EmployeeId,IsActive,CreatedAt,CreatedBy)
                    SELECT WaiterId,WaiterName,Phone,AreaId,
                    COALESCE(EmployeeId,LinkedEmployeeId) AS EmployeeId,
                    IsActive,CreatedAt,CreatedBy FROM _w_old""")
                tryExec("DROP TABLE IF EXISTS _w_old")
            }
            if (v < 44) {
                addCol("Orders", "DeliveryCompanyName", "TEXT DEFAULT ''")
                addCol("Orders", "CommissionAmount", "REAL NOT NULL DEFAULT 0")
                addCol("Orders", "VoucherCode", "TEXT DEFAULT ''")
                addCol("Orders", "VoucherDiscount", "REAL NOT NULL DEFAULT 0")
                addCol("KitchenTickets", "PrinterName", "TEXT DEFAULT ''")
            }
            if (v < 45) {
                // DealItems: rename ItemId → DealItemId
                tryExec("ALTER TABLE DealItems RENAME TO _di_old")
                tryExec("""CREATE TABLE IF NOT EXISTS DealItems (
                    DealItemId INTEGER PRIMARY KEY AUTOINCREMENT,
                    DealId INTEGER NOT NULL, ProductId INTEGER NOT NULL,
                    SizeId INTEGER, Quantity INTEGER NOT NULL DEFAULT 1,
                    IsOptional INTEGER NOT NULL DEFAULT 0)""")
                tryExec("INSERT INTO DealItems (DealItemId,DealId,ProductId,SizeId,Quantity,IsOptional) SELECT ItemId,DealId,ProductId,SizeId,Quantity,IsOptional FROM _di_old")
                tryExec("DROP TABLE IF EXISTS _di_old")
            }
            if (v < 46) {
                addCol("ProductSizes", "CostPrice", "REAL NOT NULL DEFAULT 0")
                tryExec("""CREATE TABLE IF NOT EXISTS Vouchers (
                    VoucherId INTEGER PRIMARY KEY AUTOINCREMENT,
                    VoucherCode TEXT NOT NULL UNIQUE, Description TEXT DEFAULT '',
                    DiscountType TEXT DEFAULT 'Percent', DiscountValue REAL NOT NULL DEFAULT 0,
                    MinOrderAmount REAL NOT NULL DEFAULT 0, MaxUses INTEGER NOT NULL DEFAULT 0,
                    UsedCount INTEGER NOT NULL DEFAULT 0, ExpiryDate TEXT NULL,
                    IsActive INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')))""")
            }
            if (v < 47) {
                // Drop old SupplierPayments view and create as real table
                tryExec("DROP VIEW IF EXISTS SupplierPayments")
                tryExec("""CREATE TABLE IF NOT EXISTS SupplierPayments (
                    PaymentId INTEGER PRIMARY KEY AUTOINCREMENT,
                    SupplierId INTEGER NOT NULL, PaymentDate TEXT DEFAULT (datetime('now')),
                    Amount REAL NOT NULL DEFAULT 0, PaymentMethod TEXT DEFAULT 'Cash',
                    Reference TEXT DEFAULT '', Notes TEXT DEFAULT '',
                    ShiftId INTEGER NULL, IsActive INTEGER DEFAULT 1,
                    CreatedAt TEXT DEFAULT (datetime('now')))""")
                // Copy data from PurchasePayments if it existed
                tryExec("INSERT OR IGNORE INTO SupplierPayments SELECT * FROM PurchasePayments")
            }
            if (v < 48) tryExec("DROP TABLE IF EXISTS PurchasePayments")
            if (v < 49) {
                // CustomerWallet: rename UpdatedAt → LastUpdated if not done in v42
                tryExec("ALTER TABLE CustomerWallet RENAME COLUMN UpdatedAt TO LastUpdated")
            }
            if (v < 50) {
                addCol("Expenses", "BranchId", "INTEGER NOT NULL DEFAULT 1")
            }
            if (v < 51) {
                // Accounting module tables
                tryExec("""CREATE TABLE IF NOT EXISTS ChartOfAccounts (
                    AccountId INTEGER PRIMARY KEY AUTOINCREMENT,
                    AccountCode TEXT NOT NULL UNIQUE, AccountName TEXT NOT NULL,
                    AccountType TEXT NOT NULL, IsActive INTEGER NOT NULL DEFAULT 1)""")
                tryExec("""CREATE TABLE IF NOT EXISTS AccountLedger (
                    LedgerId INTEGER PRIMARY KEY AUTOINCREMENT,
                    AccountId INTEGER NOT NULL, EntryDate TEXT NOT NULL DEFAULT (datetime('now')),
                    Debit REAL NOT NULL DEFAULT 0, Credit REAL NOT NULL DEFAULT 0,
                    ReferenceType TEXT NOT NULL DEFAULT '', ReferenceId INTEGER,
                    Narration TEXT NOT NULL DEFAULT '', CreatedBy INTEGER,
                    CreatedAt TEXT NOT NULL DEFAULT (datetime('now')))""")
                // Seed default accounts if not already present
                listOf(
                    "('1000','Cash','Asset')", "('1100','Accounts Receivable – Delivery','Asset')",
                    "('2000','Accounts Payable – Suppliers','Liability')",
                    "('4000','Sales Revenue','Income')", "('5000','Cost of Goods Sold','Expense')",
                    "('5100','Operational Expenses','Expense')", "('5200','Salary & Wages','Expense')",
                    "('5300','Purchase Payments','Expense')"
                ).forEach { tryExec("INSERT OR IGNORE INTO ChartOfAccounts (AccountCode,AccountName,AccountType) VALUES $it") }
            }

            // Ensure all views exist (idempotent)
            createCompatViews(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ── Drop ──────────────────────────────────────────────────────────────────

    private fun dropAllTables(db: SQLiteDatabase) {
        // Drop views first — SupplierPayments was a view in older schema versions
        val views = listOf("RestaurantTables", "DiningAreas", "SupplierPayments")
        views.forEach { db.execSQL("DROP VIEW IF EXISTS $it") }
        val tables = listOf(
            "AccountLedger", "ChartOfAccounts",
            "MessageLog", "AuditLogs", "SalaryPayments", "EmployeeAdvances", "Employees",
            "KitchenPrinters",
            "CashTransactions", "Branches",
            "PurchaseReturnItems", "PurchaseReturns",
            "StockTakeItems", "StockTakes",
            "WalletTransactions", "CustomerWallet", "CustomerFeedback",
            "OrderPayments", "KitchenTicketItems", "OrderItemModifiers", "OrderItems",
            "KitchenTickets", "HeldOrders", "Orders", "DealItems", "Deals",
            "Reservations", "DeliverySettlements", "DeliveryCompanies", "Waiters",
            "PurchaseInvoiceItems", "PurchaseInvoices",
            "SupplierPayments", "PurchasePayments", "Suppliers",  // PurchasePayments kept for upgrading from old schema
            "WasteEntryItems", "WasteEntries", "InventoryLedger", "StockLedger",
            "RecipeItems", "Recipes", "ProductRecipes",
            "RawMaterials", "EmployeeAttendance", "EmployeeSalaries",
            "ProductSchedules", "Vouchers", "Customers",
            "Expenses", "Shifts", "ProductModifierGroups", "ProductModifiers",
            "ModifierGroups", "ProductSizes", "Products", "Taxes", "Categories",
            "Tables", "Areas", "CompanySettings", "AppSettings",
            "RolePermissions", "Permissions", "Users", "Roles"
        )
        tables.forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
    }

    // ── Table creation ────────────────────────────────────────────────────────

    private fun createAllTables(db: SQLiteDatabase) {
        listOf(
            // ── Auth / users ─────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Roles (
                RoleId   INTEGER PRIMARY KEY AUTOINCREMENT,
                RoleName TEXT NOT NULL,
                IsActive INTEGER NOT NULL DEFAULT 1
            )""",

            """CREATE TABLE IF NOT EXISTS Users (
                UserId       INTEGER PRIMARY KEY AUTOINCREMENT,
                FullName     TEXT NOT NULL,
                Username     TEXT NOT NULL UNIQUE,
                PasswordHash TEXT NOT NULL,
                RoleId       INTEGER NOT NULL DEFAULT 1,
                IsActive     INTEGER NOT NULL DEFAULT 1,
                BasicSalary  REAL    NOT NULL DEFAULT 0,
                Phone        TEXT    DEFAULT '',
                Designation  TEXT    DEFAULT '',
                JoiningDate  TEXT,
                LastLogin    TEXT,
                CreatedAt    TEXT DEFAULT (datetime('now')),
                UpdatedAt    TEXT
            )""",

            """CREATE TABLE IF NOT EXISTS Permissions (
                PermissionId   INTEGER PRIMARY KEY AUTOINCREMENT,
                PermissionKey  TEXT NOT NULL UNIQUE,
                PermissionName TEXT NOT NULL,
                Module         TEXT NOT NULL DEFAULT '',
                IsActive       INTEGER NOT NULL DEFAULT 1
            )""",

            """CREATE TABLE IF NOT EXISTS RolePermissions (
                RolePermissionId INTEGER PRIMARY KEY AUTOINCREMENT,
                RoleId           INTEGER NOT NULL,
                PermissionId     INTEGER NOT NULL,
                IsGranted        INTEGER NOT NULL DEFAULT 1,
                CreatedAt        TEXT DEFAULT (datetime('now')),
                UNIQUE(RoleId, PermissionId)
            )""",

            // ── App settings ─────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS AppSettings (
                SettingKey   TEXT PRIMARY KEY,
                SettingValue TEXT NOT NULL DEFAULT ''
            )""",

            """CREATE TABLE IF NOT EXISTS CompanySettings (
                SettingId              INTEGER PRIMARY KEY AUTOINCREMENT,
                CompanyName            TEXT    NOT NULL DEFAULT 'My Restaurant',
                Address                TEXT    DEFAULT '',
                Phone                  TEXT    DEFAULT '',
                Email                  TEXT    DEFAULT '',
                Website                TEXT    DEFAULT '',
                TaxNumber              TEXT    DEFAULT '',
                TaxLabel               TEXT    DEFAULT 'Tax',
                CurrencySymbol         TEXT    NOT NULL DEFAULT 'Rs.',
                DefaultTaxPercent      REAL    NOT NULL DEFAULT 0,
                ServiceChargePercent   REAL    NOT NULL DEFAULT 0,
                DefaultOrderType       TEXT    NOT NULL DEFAULT 'DineIn',
                TokenPrefix            TEXT    NOT NULL DEFAULT 'T',
                AllowPartialPayment    INTEGER NOT NULL DEFAULT 1,
                AllowNegativeStock     INTEGER NOT NULL DEFAULT 0,
                MaxDiscountPercent     REAL    NOT NULL DEFAULT 0,
                RequireWaiter          INTEGER NOT NULL DEFAULT 0,
                ReceiptFooter          TEXT    DEFAULT 'Thank you for your visit!',
                Language               TEXT    DEFAULT 'English',
                SmsEnabled             INTEGER NOT NULL DEFAULT 0,
                SmsGatewayUrl          TEXT    DEFAULT '',
                WhatsappEnabled        INTEGER NOT NULL DEFAULT 0,
                WhatsappGatewayUrl     TEXT    DEFAULT '',
                NotifyOnOrderPlaced    INTEGER NOT NULL DEFAULT 1,
                NotifyOnOrderReady     INTEGER NOT NULL DEFAULT 1,
                NotifyOnOrderCancelled INTEGER NOT NULL DEFAULT 0,
                RefreshMode            TEXT    DEFAULT 'Normal',
                LogoPath               TEXT    DEFAULT '',
                LogoData               BLOB    DEFAULT NULL,
                AutoPrintTakeawayToken INTEGER NOT NULL DEFAULT 0,
                FbrEnabled             INTEGER NOT NULL DEFAULT 0,
                FbrPassword            TEXT    DEFAULT '',
                FbrNtn                 TEXT    DEFAULT '',
                FbrBusinessName        TEXT    DEFAULT '',
                FbrSellerProvince      TEXT    DEFAULT '',
                FbrSellerAddress       TEXT    DEFAULT '',
                FbrSandboxMode         INTEGER NOT NULL DEFAULT 0,
                FbrHsCode              TEXT    DEFAULT '21069099',
                KitchenUrgentMinutes   INTEGER NOT NULL DEFAULT 15,
                UpdatedAt              TEXT
            )""",

            // ── Branches ─────────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Branches (
                BranchId   INTEGER PRIMARY KEY AUTOINCREMENT,
                BranchName TEXT NOT NULL,
                Address    TEXT DEFAULT '',
                Phone      TEXT DEFAULT '',
                IsActive   INTEGER NOT NULL DEFAULT 1,
                CreatedBy  INTEGER,
                CreatedAt  TEXT DEFAULT (datetime('now')),
                UpdatedAt  TEXT,
                UpdatedBy  INTEGER
            )""",

            // ── Restaurant tables & areas ────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Areas (
                AreaId       INTEGER PRIMARY KEY AUTOINCREMENT,
                AreaName     TEXT    NOT NULL,
                IsActive     INTEGER NOT NULL DEFAULT 1,
                BranchId     INTEGER NOT NULL DEFAULT 1,
                DisplayOrder INTEGER NOT NULL DEFAULT 0,
                CreatedAt    TEXT,
                CreatedBy    INTEGER,
                UpdatedAt    TEXT,
                UpdatedBy    INTEGER
            )""",

            """CREATE TABLE IF NOT EXISTS Tables (
                TableId     INTEGER PRIMARY KEY AUTOINCREMENT,
                TableName   TEXT NOT NULL,
                AreaId      INTEGER,
                Capacity    INTEGER DEFAULT 4,
                TableStatus TEXT    NOT NULL DEFAULT 'Available',
                IsActive    INTEGER NOT NULL DEFAULT 1
            )""",

            // ── Product catalog ──────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Categories (
                CategoryId                INTEGER PRIMARY KEY AUTOINCREMENT,
                CategoryName              TEXT NOT NULL,
                CategoryNameOtherLanguage TEXT    DEFAULT '',
                ColorCode                 TEXT    DEFAULT '#FF6B35',
                DisplayOrder              INTEGER DEFAULT 0,
                IsActive                  INTEGER NOT NULL DEFAULT 1,
                CreatedAt                 TEXT    DEFAULT (datetime('now')),
                CreatedBy                 INTEGER,
                UpdatedAt                 TEXT
            )""",

            """CREATE TABLE IF NOT EXISTS Taxes (
                TaxId      INTEGER PRIMARY KEY AUTOINCREMENT,
                TaxName    TEXT NOT NULL,
                TaxPercent REAL NOT NULL DEFAULT 0,
                IsActive   INTEGER NOT NULL DEFAULT 1
            )""",

            """CREATE TABLE IF NOT EXISTS KitchenPrinters (
                PrinterId   INTEGER PRIMARY KEY AUTOINCREMENT,
                PrinterName TEXT NOT NULL,
                IpAddress   TEXT DEFAULT '',
                Port        INTEGER DEFAULT 9100,
                IsActive    INTEGER NOT NULL DEFAULT 1,
                CreatedAt   TEXT DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS Products (
                ProductId                 INTEGER PRIMARY KEY AUTOINCREMENT,
                ProductCode               TEXT    DEFAULT '',
                ProductName               TEXT NOT NULL,
                ProductNameOtherLanguage  TEXT    DEFAULT '',
                CategoryId                INTEGER,
                ProductType               TEXT    DEFAULT 'Normal',
                SalePrice                 REAL    NOT NULL DEFAULT 0,
                PurchasePrice             REAL    NOT NULL DEFAULT 0,
                IsStockManaged            INTEGER NOT NULL DEFAULT 0,
                IsRecipeBased             INTEGER NOT NULL DEFAULT 0,
                OpeningStock              REAL    NOT NULL DEFAULT 0,
                CurrentStock              REAL    NOT NULL DEFAULT 0,
                MinimumStock              REAL    NOT NULL DEFAULT 5,
                ReorderLevel              REAL    NOT NULL DEFAULT 0,
                Unit                      TEXT    DEFAULT 'Pcs',
                DisplayOrder              INTEGER DEFAULT 0,
                IsAvailable               INTEGER NOT NULL DEFAULT 1,
                TaxId                     INTEGER,
                KitchenPrinterId          INTEGER NULL,
                PrinterName               TEXT    DEFAULT '',
                IsActive                  INTEGER NOT NULL DEFAULT 1,
                CreatedAt                 TEXT    DEFAULT (datetime('now')),
                CreatedBy                 INTEGER,
                UpdatedAt                 TEXT,
                UpdatedBy                 INTEGER
            )""",

            """CREATE TABLE IF NOT EXISTS ProductSizes (
                SizeId       INTEGER PRIMARY KEY AUTOINCREMENT,
                ProductId    INTEGER NOT NULL,
                SizeName     TEXT NOT NULL,
                Price        REAL NOT NULL DEFAULT 0,
                CostPrice    REAL NOT NULL DEFAULT 0,
                DisplayOrder INTEGER DEFAULT 0,
                IsActive     INTEGER NOT NULL DEFAULT 1
            )""",

            """CREATE TABLE IF NOT EXISTS ModifierGroups (
                ModifierGroupId INTEGER PRIMARY KEY AUTOINCREMENT,
                GroupName       TEXT NOT NULL,
                MinSelection    INTEGER NOT NULL DEFAULT 0,
                MaxSelection    INTEGER NOT NULL DEFAULT 1,
                IsRequired      INTEGER NOT NULL DEFAULT 0,
                IsActive        INTEGER NOT NULL DEFAULT 1
            )""",

            """CREATE TABLE IF NOT EXISTS ProductModifiers (
                ModifierId      INTEGER PRIMARY KEY AUTOINCREMENT,
                ModifierGroupId INTEGER NOT NULL,
                ModifierName    TEXT NOT NULL,
                ExtraPrice      REAL NOT NULL DEFAULT 0,
                StockItemId     INTEGER NULL REFERENCES Products(ProductId),
                IsActive        INTEGER NOT NULL DEFAULT 1
            )""",

            """CREATE TABLE IF NOT EXISTS ProductModifierGroups (
                ProductId       INTEGER NOT NULL,
                ModifierGroupId INTEGER NOT NULL,
                PRIMARY KEY (ProductId, ModifierGroupId)
            )""",

            // ── Orders ───────────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Orders (
                OrderId           INTEGER PRIMARY KEY AUTOINCREMENT,
                OrderNo           TEXT NOT NULL DEFAULT '',
                TokenNo           TEXT NOT NULL DEFAULT '',
                OrderDate         TEXT DEFAULT (datetime('now')),
                OrderType         TEXT NOT NULL DEFAULT 'Takeaway',
                BranchId          INTEGER DEFAULT 1,
                TableId           INTEGER,
                WaiterId          INTEGER,
                CustomerId        INTEGER,
                CustomerName      TEXT    DEFAULT '',
                ShiftId           INTEGER,
                SubTotal          REAL NOT NULL DEFAULT 0,
                DiscountAmount    REAL NOT NULL DEFAULT 0,
                DiscountPercent   REAL NOT NULL DEFAULT 0,
                TaxAmount         REAL NOT NULL DEFAULT 0,
                TaxPercent        REAL NOT NULL DEFAULT 0,
                ServiceCharges    REAL NOT NULL DEFAULT 0,
                DeliveryCharge    REAL NOT NULL DEFAULT 0,
                Tips              REAL NOT NULL DEFAULT 0,
                GrandTotal        REAL NOT NULL DEFAULT 0,
                PaidAmount        REAL NOT NULL DEFAULT 0,
                BalanceAmount     REAL NOT NULL DEFAULT 0,
                OrderStatus         TEXT NOT NULL DEFAULT 'New',
                PaymentStatus       TEXT NOT NULL DEFAULT 'Unpaid',
                CancellationReason  TEXT NULL,
                DeliveryName      TEXT DEFAULT '',
                DeliveryPhone     TEXT DEFAULT '',
                DeliveryAddress   TEXT DEFAULT '',
                DeliveryCompanyId   INTEGER,
                DeliveryCompanyName TEXT    DEFAULT '',
                CommissionAmount    REAL    NOT NULL DEFAULT 0,
                VoucherCode         TEXT    DEFAULT '',
                VoucherDiscount     REAL    NOT NULL DEFAULT 0,
                Notes             TEXT DEFAULT '',
                CreatedBy         INTEGER,
                CreatedAt         TEXT DEFAULT (datetime('now')),
                UpdatedAt         TEXT,
                FbrInvoiceNo      TEXT NULL,
                FbrStatus         TEXT NULL
            )""",

            """CREATE TABLE IF NOT EXISTS HeldOrders (
                OrderId  INTEGER NOT NULL PRIMARY KEY,
                HeldAt   TEXT    NOT NULL DEFAULT (datetime('now')),
                HeldBy   INTEGER NOT NULL,
                Notes    TEXT    NULL
            )""",

            """CREATE TABLE IF NOT EXISTS OrderItems (
                OrderItemId         INTEGER PRIMARY KEY AUTOINCREMENT,
                OrderId             INTEGER NOT NULL,
                ProductId           INTEGER,
                SizeId              INTEGER,
                ProductNameSnapshot TEXT DEFAULT '',
                SizeNameSnapshot    TEXT,
                Quantity            REAL NOT NULL DEFAULT 1,
                UnitPrice           REAL NOT NULL DEFAULT 0,
                DiscountAmount      REAL NOT NULL DEFAULT 0,
                LineTotal           REAL NOT NULL DEFAULT 0,
                Notes               TEXT,
                KitchenStatus       TEXT NOT NULL DEFAULT 'Pending',
                KitchenPrinterId    INTEGER,
                ProductNameOtherLanguageSnapshot TEXT DEFAULT '',
                CreatedAt           TEXT DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS OrderItemModifiers (
                OrderItemModifierId INTEGER PRIMARY KEY AUTOINCREMENT,
                OrderItemId         INTEGER NOT NULL,
                ModifierId          INTEGER,
                ModifierNameSnapshot TEXT DEFAULT '',
                ExtraPrice          REAL NOT NULL DEFAULT 0,
                Quantity            INTEGER NOT NULL DEFAULT 1,
                Total               REAL NOT NULL DEFAULT 0
            )""",

            """CREATE TABLE IF NOT EXISTS KitchenTickets (
                TicketId     INTEGER PRIMARY KEY AUTOINCREMENT,
                OrderId      INTEGER NOT NULL,
                TicketNo     TEXT DEFAULT '',
                PrintedAt    TEXT DEFAULT (datetime('now')),
                PrinterName  TEXT DEFAULT '',
                TicketStatus TEXT NOT NULL DEFAULT 'Pending',
                CompletedAt  TEXT
            )""",

            """CREATE TABLE IF NOT EXISTS KitchenTicketItems (
                TicketItemId INTEGER PRIMARY KEY AUTOINCREMENT,
                TicketId     INTEGER NOT NULL,
                OrderItemId  INTEGER,
                ItemName     TEXT DEFAULT '',
                Quantity     REAL NOT NULL DEFAULT 1,
                Notes        TEXT,
                ItemStatus   TEXT NOT NULL DEFAULT 'Pending',
                CompletedAt  TEXT
            )""",

            """CREATE TABLE IF NOT EXISTS OrderPayments (
                PaymentId     INTEGER PRIMARY KEY AUTOINCREMENT,
                OrderId       INTEGER NOT NULL,
                PaymentMethod TEXT NOT NULL DEFAULT 'Cash',
                Amount        REAL NOT NULL DEFAULT 0,
                Reference     TEXT DEFAULT '',
                PaymentDate   TEXT DEFAULT (datetime('now')),
                CreatedBy     INTEGER
            )""",

            // ── Customers & loyalty ──────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Customers (
                CustomerId    INTEGER PRIMARY KEY AUTOINCREMENT,
                CustomerName  TEXT NOT NULL,
                Phone         TEXT DEFAULT '',
                Address       TEXT DEFAULT '',
                LoyaltyPoints INTEGER NOT NULL DEFAULT 0,
                TotalOrders   INTEGER NOT NULL DEFAULT 0,
                TotalSpent    REAL    NOT NULL DEFAULT 0,
                IsActive      INTEGER NOT NULL DEFAULT 1,
                CreatedBy     INTEGER,
                CreatedAt     TEXT DEFAULT (datetime('now')),
                UpdatedAt     TEXT,
                UpdatedBy     INTEGER
            )""",

            // ── Shift & finance ──────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Shifts (
                ShiftId        INTEGER PRIMARY KEY AUTOINCREMENT,
                ShiftCode      TEXT NOT NULL,
                BusinessDate   TEXT NOT NULL DEFAULT (date('now')),
                UserId         INTEGER NOT NULL,
                BranchId       INTEGER NOT NULL DEFAULT 1,
                OpeningTime    TEXT DEFAULT (datetime('now')),
                ClosingTime    TEXT,
                OpeningCash    REAL DEFAULT 0,
                ClosingCash    REAL DEFAULT 0,
                TotalSales     REAL DEFAULT 0,
                TotalExpenses  REAL DEFAULT 0,
                ExpectedCash   REAL DEFAULT 0,
                Difference     REAL DEFAULT 0,
                ShiftStatus    TEXT NOT NULL DEFAULT 'Open',
                Notes          TEXT DEFAULT '',
                CreatedAt      TEXT DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS Expenses (
                ExpenseId     INTEGER PRIMARY KEY AUTOINCREMENT,
                ShiftId       INTEGER,
                BranchId      INTEGER NOT NULL DEFAULT 1,
                ExpenseDate   TEXT NOT NULL DEFAULT (datetime('now')),
                ExpenseType   TEXT NOT NULL DEFAULT 'Other',
                Description   TEXT DEFAULT '',
                Amount        REAL NOT NULL DEFAULT 0,
                PaidTo        TEXT DEFAULT '',
                PaymentMethod TEXT DEFAULT 'Cash',
                IsActive      INTEGER NOT NULL DEFAULT 1,
                CreatedBy     INTEGER,
                CreatedAt     TEXT DEFAULT (datetime('now')),
                UpdatedAt     TEXT,
                UpdatedBy     INTEGER
            )""",

            // ── HR ────────────────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS EmployeeAttendance (
                AttendanceId   INTEGER PRIMARY KEY AUTOINCREMENT,
                EmployeeId     INTEGER NOT NULL,
                BranchId       INTEGER NOT NULL DEFAULT 1,
                AttendanceDate TEXT NOT NULL,
                CheckInTime    TEXT NULL,
                CheckOutTime   TEXT NULL,
                Notes          TEXT DEFAULT '',
                CreatedAt      TEXT DEFAULT (datetime('now')),
                CreatedBy      INTEGER NULL
            )""",

            // ── Cash drawer ───────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS CashTransactions (
                TransactionId   INTEGER PRIMARY KEY AUTOINCREMENT,
                ShiftId         INTEGER NOT NULL,
                BranchId        INTEGER NOT NULL DEFAULT 1,
                TransactionType TEXT NOT NULL DEFAULT 'In',
                Amount          REAL NOT NULL DEFAULT 0,
                Reason          TEXT NOT NULL DEFAULT '',
                Notes           TEXT,
                CreatedBy       INTEGER NOT NULL DEFAULT 0,
                CreatedAt       TEXT NOT NULL DEFAULT (datetime('now'))
            )""",

            // ── Raw material & recipe ────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS RawMaterials (
                MaterialId    INTEGER PRIMARY KEY AUTOINCREMENT,
                MaterialName  TEXT NOT NULL,
                Unit          TEXT DEFAULT 'kg',
                CurrentStock  REAL NOT NULL DEFAULT 0,
                MinStockLevel REAL NOT NULL DEFAULT 0,
                CostPerUnit   REAL NOT NULL DEFAULT 0,
                IsActive      INTEGER NOT NULL DEFAULT 1,
                CreatedAt     TEXT DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS ProductRecipes (
                RecipeId         INTEGER PRIMARY KEY AUTOINCREMENT,
                ProductId        INTEGER NOT NULL,
                SizeId           INTEGER NOT NULL DEFAULT 0,
                MaterialId       INTEGER NOT NULL,
                QuantityRequired REAL NOT NULL DEFAULT 0,
                UNIQUE(ProductId, SizeId, MaterialId)
            )""",

            // WPF-compatible recipe tables (used by RecipeScreen in all modes)
            """CREATE TABLE IF NOT EXISTS Recipes (
                RecipeId   INTEGER PRIMARY KEY AUTOINCREMENT,
                ProductId  INTEGER NOT NULL,
                SizeId     INTEGER NULL,
                RecipeName TEXT    DEFAULT '',
                IsActive   INTEGER NOT NULL DEFAULT 1,
                CreatedAt  TEXT    DEFAULT (datetime('now')),
                CreatedBy  INTEGER NULL,
                UpdatedAt  TEXT    NULL,
                UpdatedBy  INTEGER NULL
            )""",

            """CREATE TABLE IF NOT EXISTS RecipeItems (
                RecipeItemId  INTEGER PRIMARY KEY AUTOINCREMENT,
                RecipeId      INTEGER NOT NULL,
                ProductId     INTEGER NOT NULL,
                QuantityUsed  REAL    NOT NULL DEFAULT 1,
                Unit          TEXT    DEFAULT 'kg'
            )""",

            """CREATE TABLE IF NOT EXISTS InventoryLedger (
                LedgerId        INTEGER PRIMARY KEY AUTOINCREMENT,
                ProductId       INTEGER NOT NULL,
                TransactionDate TEXT DEFAULT (datetime('now')),
                ReferenceType   TEXT DEFAULT '',
                ReferenceId     INTEGER,
                InQty           REAL NOT NULL DEFAULT 0,
                OutQty          REAL NOT NULL DEFAULT 0,
                BalanceQty      REAL NOT NULL DEFAULT 0,
                Rate            REAL DEFAULT 0,
                Amount          REAL DEFAULT 0,
                Remarks         TEXT DEFAULT '',
                CreatedBy       INTEGER,
                CreatedAt       TEXT DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS StockLedger (
                LedgerId   INTEGER PRIMARY KEY AUTOINCREMENT,
                MaterialId INTEGER NOT NULL,
                TransDate  TEXT DEFAULT (datetime('now')),
                RefType    TEXT DEFAULT '',
                RefId      INTEGER,
                InQty      REAL DEFAULT 0,
                OutQty     REAL DEFAULT 0,
                Rate       REAL DEFAULT 0,
                Amount     REAL DEFAULT 0,
                Remarks    TEXT DEFAULT ''
            )""",

            """CREATE TABLE IF NOT EXISTS WasteEntries (
                WasteId     INTEGER PRIMARY KEY AUTOINCREMENT,
                WasteDate   TEXT    DEFAULT (datetime('now')),
                Notes       TEXT    DEFAULT '',
                TotalAmount REAL    NOT NULL DEFAULT 0,
                IsActive    INTEGER NOT NULL DEFAULT 1,
                CreatedBy   INTEGER,
                CreatedAt   TEXT    DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS WasteEntryItems (
                ItemId    INTEGER PRIMARY KEY AUTOINCREMENT,
                WasteId   INTEGER NOT NULL,
                ProductId INTEGER NOT NULL,
                Quantity  REAL NOT NULL DEFAULT 1,
                Unit      TEXT DEFAULT 'kg',
                Rate      REAL DEFAULT 0,
                Amount    REAL DEFAULT 0,
                Reason    TEXT DEFAULT ''
            )""",

            // ── Suppliers & purchases ────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Suppliers (
                SupplierId    INTEGER PRIMARY KEY AUTOINCREMENT,
                SupplierName  TEXT NOT NULL,
                ContactPerson TEXT DEFAULT '',
                Phone         TEXT DEFAULT '',
                Address       TEXT DEFAULT '',
                Email         TEXT DEFAULT '',
                IsActive      INTEGER NOT NULL DEFAULT 1,
                CreatedAt     TEXT DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS PurchaseInvoices (
                InvoiceId      INTEGER PRIMARY KEY AUTOINCREMENT,
                InvoiceNo      TEXT    NOT NULL,
                SupplierId     INTEGER,
                BranchId       INTEGER DEFAULT 1,
                InvoiceDate    TEXT    NOT NULL DEFAULT (datetime('now')),
                GrandTotal     REAL    DEFAULT 0,
                TotalAmount    REAL    DEFAULT 0,
                PaidAmount     REAL    DEFAULT 0,
                BalanceAmount  REAL    DEFAULT 0,
                PaymentStatus  TEXT    DEFAULT 'Unpaid',
                Notes          TEXT    DEFAULT '',
                IsActive       INTEGER NOT NULL DEFAULT 1,
                CreatedAt      TEXT    DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS PurchaseInvoiceItems (
                ItemId        INTEGER PRIMARY KEY AUTOINCREMENT,
                InvoiceId     INTEGER NOT NULL,
                ProductId     INTEGER,
                ItemName      TEXT DEFAULT '',
                Unit          TEXT DEFAULT 'kg',
                Quantity       REAL DEFAULT 1,
                PurchaseRate   REAL DEFAULT 0,
                DiscountAmount REAL DEFAULT 0,
                LineTotal      REAL DEFAULT 0
            )""",

            """CREATE TABLE IF NOT EXISTS SupplierPayments (
                PaymentId     INTEGER PRIMARY KEY AUTOINCREMENT,
                SupplierId    INTEGER NOT NULL,
                PaymentDate   TEXT DEFAULT (datetime('now')),
                Amount        REAL NOT NULL DEFAULT 0,
                PaymentMethod TEXT DEFAULT 'Cash',
                Reference     TEXT DEFAULT '',
                Notes         TEXT DEFAULT '',
                ShiftId       INTEGER NULL,
                IsActive      INTEGER DEFAULT 1,
                CreatedAt     TEXT DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS PurchaseReturns (
                ReturnId     INTEGER PRIMARY KEY AUTOINCREMENT,
                InvoiceId    INTEGER,
                SupplierId   INTEGER,
                ReturnDate   TEXT DEFAULT (datetime('now')),
                TotalAmount  REAL DEFAULT 0,
                Notes        TEXT DEFAULT '',
                RefundMethod TEXT DEFAULT 'Credit',
                ShiftId      INTEGER NULL,
                IsActive     INTEGER DEFAULT 1,
                CreatedAt    TEXT DEFAULT (datetime('now')),
                CreatedBy    INTEGER
            )""",

            """CREATE TABLE IF NOT EXISTS PurchaseReturnItems (
                ReturnItemId INTEGER PRIMARY KEY AUTOINCREMENT,
                ReturnId     INTEGER NOT NULL,
                ProductId    INTEGER NOT NULL,
                ItemName     TEXT NOT NULL DEFAULT '',
                Quantity     REAL DEFAULT 1,
                Unit         TEXT DEFAULT '',
                ReturnRate   REAL DEFAULT 0,
                LineTotal    REAL DEFAULT 0
            )""",

            // ── Deals & combos ───────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Deals (
                DealId          INTEGER PRIMARY KEY AUTOINCREMENT,
                DealName        TEXT NOT NULL,
                Description     TEXT DEFAULT '',
                DealPrice       REAL NOT NULL DEFAULT 0,
                DiscountPercent REAL DEFAULT 0,
                ValidFrom       TEXT,
                ValidTo         TEXT,
                IsActive        INTEGER NOT NULL DEFAULT 1,
                CreatedAt       TEXT DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS DealItems (
                DealItemId INTEGER PRIMARY KEY AUTOINCREMENT,
                DealId     INTEGER NOT NULL,
                ProductId  INTEGER NOT NULL,
                SizeId     INTEGER,
                Quantity   INTEGER NOT NULL DEFAULT 1,
                IsOptional INTEGER NOT NULL DEFAULT 0
            )""",

            // ── Reservations ──────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Reservations (
                ReservationId   INTEGER PRIMARY KEY AUTOINCREMENT,
                CustomerName    TEXT NOT NULL,
                Phone           TEXT DEFAULT '',
                PartySize       INTEGER DEFAULT 1,
                ReservationDate TEXT NOT NULL,
                ReservationTime TEXT NOT NULL DEFAULT '19:00',
                TableId         INTEGER,
                Status          TEXT NOT NULL DEFAULT 'Confirmed',
                Notes           TEXT DEFAULT '',
                CreatedAt       TEXT DEFAULT (datetime('now'))
            )""",

            // ── Delivery ──────────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS DeliveryCompanies (
                CompanyId         INTEGER PRIMARY KEY AUTOINCREMENT,
                CompanyName       TEXT NOT NULL,
                CommissionPercent REAL DEFAULT 0,
                IsActive          INTEGER NOT NULL DEFAULT 1,
                CreatedAt         TEXT DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS DeliverySettlements (
                SettlementId      INTEGER PRIMARY KEY AUTOINCREMENT,
                DeliveryCompanyId INTEGER NOT NULL,
                SettlementDate    TEXT NOT NULL,
                AmountReceived    REAL NOT NULL DEFAULT 0,
                Notes             TEXT DEFAULT '',
                CreatedAt         TEXT DEFAULT (datetime('now'))
            )""",

            // ── Staff ─────────────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Waiters (
                WaiterId   INTEGER PRIMARY KEY AUTOINCREMENT,
                WaiterName TEXT    NOT NULL,
                Phone      TEXT    DEFAULT '',
                IsActive   INTEGER NOT NULL DEFAULT 1,
                AreaId     INTEGER,
                EmployeeId INTEGER,
                CreatedAt  TEXT    DEFAULT (datetime('now')),
                CreatedBy  INTEGER
            )""",

            // ── Vouchers ──────────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS Vouchers (
                VoucherId      INTEGER PRIMARY KEY AUTOINCREMENT,
                VoucherCode    TEXT NOT NULL UNIQUE,
                Description    TEXT DEFAULT '',
                DiscountType   TEXT NOT NULL DEFAULT 'Percent',
                DiscountValue  REAL NOT NULL DEFAULT 0,
                MinOrderAmount REAL DEFAULT 0,
                MaxUses        INTEGER DEFAULT 0,
                UsedCount      INTEGER DEFAULT 0,
                ExpiryDate     TEXT,
                IsActive       INTEGER NOT NULL DEFAULT 1,
                CreatedAt      TEXT DEFAULT (datetime('now'))
            )""",

            // ── Product schedules ─────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS ProductSchedules (
                ScheduleId INTEGER PRIMARY KEY AUTOINCREMENT,
                ProductId  INTEGER NOT NULL,
                Label      TEXT DEFAULT '',
                DayOfWeek  INTEGER NOT NULL DEFAULT -1,
                StartTime  TEXT NOT NULL DEFAULT '00:00',
                EndTime    TEXT NOT NULL DEFAULT '23:59',
                IsActive   INTEGER NOT NULL DEFAULT 1
            )""",

            // ── Customer wallet ───────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS CustomerWallet (
                WalletId    INTEGER PRIMARY KEY AUTOINCREMENT,
                CustomerId  INTEGER NOT NULL UNIQUE,
                Balance     REAL    NOT NULL DEFAULT 0,
                LastUpdated TEXT    DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS WalletTransactions (
                TransId    INTEGER PRIMARY KEY AUTOINCREMENT,
                CustomerId INTEGER NOT NULL,
                OrderId    INTEGER,
                TransType  TEXT    NOT NULL,
                Amount     REAL    NOT NULL DEFAULT 0,
                TransDate  TEXT    DEFAULT (datetime('now')),
                Notes      TEXT    DEFAULT '',
                CreatedBy  INTEGER
            )""",

            """CREATE TABLE IF NOT EXISTS CustomerFeedback (
                FeedbackId   INTEGER PRIMARY KEY AUTOINCREMENT,
                CustomerId   INTEGER,
                OrderId      INTEGER,
                Rating       INTEGER NOT NULL DEFAULT 5,
                Comment      TEXT,
                FeedbackDate TEXT NOT NULL DEFAULT (datetime('now')),
                CreatedAt    TEXT NOT NULL DEFAULT (datetime('now')),
                CreatedBy    INTEGER
            )""",

            // ── Stock Take ────────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS StockTakes (
                StockTakeId INTEGER PRIMARY KEY AUTOINCREMENT,
                TakeDate    TEXT NOT NULL DEFAULT (datetime('now')),
                Status      TEXT NOT NULL DEFAULT 'Open',
                Notes       TEXT NOT NULL DEFAULT '',
                CreatedBy   INTEGER,
                FinalizedAt TEXT
            )""",

            """CREATE TABLE IF NOT EXISTS StockTakeItems (
                ItemId       INTEGER PRIMARY KEY AUTOINCREMENT,
                StockTakeId  INTEGER NOT NULL,
                MaterialId   INTEGER NOT NULL,
                MaterialName TEXT NOT NULL DEFAULT '',
                Unit         TEXT NOT NULL DEFAULT 'kg',
                ExpectedQty  REAL NOT NULL DEFAULT 0,
                ActualQty    REAL NOT NULL DEFAULT 0
            )""",

            // ── HR – Employees, Advances & Salary ────────────────────────────
            """CREATE TABLE IF NOT EXISTS Employees (
                EmployeeId    INTEGER PRIMARY KEY AUTOINCREMENT,
                EmployeeName  TEXT NOT NULL,
                Phone         TEXT DEFAULT '',
                EmployeeRole  TEXT DEFAULT '',
                JoiningDate   TEXT,
                MonthlySalary REAL NOT NULL DEFAULT 0,
                IsActive      INTEGER NOT NULL DEFAULT 1,
                BranchId      INTEGER DEFAULT 1,
                CreatedAt     TEXT DEFAULT (datetime('now')),
                CreatedBy     INTEGER
            )""",

            """CREATE TABLE IF NOT EXISTS EmployeeSalaries (
                UserId        INTEGER PRIMARY KEY,
                MonthlySalary REAL NOT NULL DEFAULT 0
            )""",

            """CREATE TABLE IF NOT EXISTS EmployeeAdvances (
                AdvanceId     INTEGER PRIMARY KEY AUTOINCREMENT,
                EmployeeId    INTEGER NOT NULL,
                BranchId      INTEGER NOT NULL DEFAULT 1,
                AdvanceDate   TEXT DEFAULT (datetime('now')),
                Amount        REAL NOT NULL DEFAULT 0,
                Notes         TEXT DEFAULT '',
                PaymentMethod TEXT DEFAULT 'Cash',
                ShiftId       INTEGER,
                IsActive      INTEGER DEFAULT 1,
                CreatedBy     INTEGER,
                CreatedAt     TEXT DEFAULT (datetime('now'))
            )""",

            """CREATE TABLE IF NOT EXISTS SalaryPayments (
                PaymentId     INTEGER PRIMARY KEY AUTOINCREMENT,
                EmployeeId    INTEGER NOT NULL,
                PaymentDate   TEXT DEFAULT (datetime('now')),
                Amount        REAL NOT NULL DEFAULT 0,
                PeriodMonth   INTEGER NOT NULL DEFAULT 1,
                PeriodYear    INTEGER NOT NULL DEFAULT 2025,
                PaymentMethod TEXT DEFAULT 'Cash',
                Notes         TEXT DEFAULT '',
                ShiftId       INTEGER,
                BranchId      INTEGER NOT NULL DEFAULT 1,
                IsActive      INTEGER DEFAULT 1,
                CreatedBy     INTEGER,
                CreatedAt     TEXT DEFAULT (datetime('now'))
            )""",

            // ── Audit Log ─────────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS AuditLogs (
                LogId       INTEGER PRIMARY KEY AUTOINCREMENT,
                LoggedAt    TEXT DEFAULT (datetime('now')),
                UserId      INTEGER,
                Action      TEXT NOT NULL DEFAULT '',
                TableName   TEXT NOT NULL DEFAULT '',
                RecordId    INTEGER,
                MachineName TEXT DEFAULT '',
                Details     TEXT DEFAULT ''
            )""",

            // ── Accounting ────────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS ChartOfAccounts (
                AccountId   INTEGER PRIMARY KEY AUTOINCREMENT,
                AccountCode TEXT    NOT NULL UNIQUE,
                AccountName TEXT    NOT NULL,
                AccountType TEXT    NOT NULL,
                IsActive    INTEGER NOT NULL DEFAULT 1
            )""",

            """CREATE TABLE IF NOT EXISTS AccountLedger (
                LedgerId      INTEGER PRIMARY KEY AUTOINCREMENT,
                AccountId     INTEGER NOT NULL,
                EntryDate     TEXT    NOT NULL DEFAULT (datetime('now')),
                Debit         REAL    NOT NULL DEFAULT 0,
                Credit        REAL    NOT NULL DEFAULT 0,
                ReferenceType TEXT    NOT NULL DEFAULT '',
                ReferenceId   INTEGER,
                Narration     TEXT    NOT NULL DEFAULT '',
                CreatedBy     INTEGER,
                CreatedAt     TEXT    NOT NULL DEFAULT (datetime('now'))
            )""",

            // ── Message Log ───────────────────────────────────────────────────
            """CREATE TABLE IF NOT EXISTS MessageLog (
                MessageId      INTEGER PRIMARY KEY AUTOINCREMENT,
                OrderId        INTEGER,
                RecipientPhone TEXT NOT NULL DEFAULT '',
                Channel        TEXT NOT NULL DEFAULT '',
                MessageText    TEXT NOT NULL DEFAULT '',
                Status         TEXT NOT NULL DEFAULT 'Sent',
                ErrorMessage   TEXT DEFAULT '',
                SentAt         TEXT,
                CreatedAt      TEXT NOT NULL DEFAULT (datetime('now'))
            )"""

        ).forEach { db.execSQL(it) }
    }

    // ── Compatibility views ────────────────────────────────────────────────────
    // Some repositories use different table names; views ensure both work.

    private fun createCompatViews(db: SQLiteDatabase) {
        // ReservationRepository uses RestaurantTables; main table is Tables
        db.execSQL("CREATE VIEW IF NOT EXISTS RestaurantTables AS SELECT * FROM Tables")
        // OrderRepository.getTables() uses DiningAreas; main table is Areas
        db.execSQL("CREATE VIEW IF NOT EXISTS DiningAreas AS SELECT AreaId, AreaName, BranchId, DisplayOrder, IsActive FROM Areas")
        // SupplierPayments is now the real table (renamed from PurchasePayments to match WPF schema)
    }

    // ── Seed data ─────────────────────────────────────────────────────────────

    private fun seedDefaultData(db: SQLiteDatabase) {
        // Roles
        db.execSQL("INSERT OR IGNORE INTO Roles (RoleId, RoleName) VALUES (1, 'Admin')")
        db.execSQL("INSERT OR IGNORE INTO Roles (RoleId, RoleName) VALUES (2, 'Manager')")
        db.execSQL("INSERT OR IGNORE INTO Roles (RoleId, RoleName) VALUES (3, 'Cashier')")
        db.execSQL("INSERT OR IGNORE INTO Roles (RoleId, RoleName) VALUES (4, 'Kitchen')")
        db.execSQL("INSERT OR IGNORE INTO Roles (RoleId, RoleName) VALUES (5, 'Waiter')")

        // Permissions (31 keys matching WPF source) — triple: key, display name, module
        data class PermDef(val key: String, val name: String, val module: String)
        val permDefs = listOf(
            PermDef("POS.Access",          "Access POS",             "POS"),
            PermDef("POS.Discount",        "Apply Discount",         "POS"),
            PermDef("POS.EditPrice",       "Edit Item Price",        "POS"),
            PermDef("POS.Delivery",        "Manage Delivery",        "POS"),
            PermDef("POS.SaveOrder",       "Save/Finalize Order",    "POS"),
            PermDef("POS.CancelOrder",     "Cancel Order",           "POS"),
            PermDef("POS.HoldOrder",       "Hold Order",             "POS"),
            PermDef("POS.RecallOrder",     "Recall Order",           "POS"),
            PermDef("POS.PrintKOT",        "Print KOT",              "POS"),
            PermDef("Products.View",       "View Products",          "Products"),
            PermDef("Products.Add",        "Add Products",           "Products"),
            PermDef("Products.Edit",       "Edit Products",          "Products"),
            PermDef("Products.Delete",     "Delete Products",        "Products"),
            PermDef("Categories.Manage",   "Manage Categories",      "Products"),
            PermDef("Sizes.Manage",        "Manage Sizes",           "Products"),
            PermDef("Modifiers.Manage",    "Manage Modifiers",       "Products"),
            PermDef("Deals.Manage",        "Manage Deals",           "Products"),
            PermDef("Tables.Manage",       "Manage Tables",          "Setup"),
            PermDef("Customers.Manage",    "Manage Customers",       "Setup"),
            PermDef("Waiters.Manage",      "Manage Waiters",         "Setup"),
            PermDef("Shift.Open",          "Open Shift",             "Operations"),
            PermDef("Shift.Close",         "Close Shift",            "Operations"),
            PermDef("Expenses.Manage",     "Manage Expenses",        "Operations"),
            PermDef("Inventory.Purchase",  "Purchase & Suppliers",   "Inventory"),
            PermDef("Inventory.Stock",     "Manage Stock",           "Inventory"),
            PermDef("Inventory.Recipe",    "Manage Recipes",         "Inventory"),
            PermDef("Inventory.Waste",     "Record Waste",           "Inventory"),
            PermDef("Reports.View",        "View Reports",           "Reports"),
            PermDef("Reports.Profit",      "View Profit Reports",    "Reports"),
            PermDef("Users.Manage",        "Manage Users & Roles",   "Admin"),
            PermDef("Settings.Manage",     "Manage Settings",        "Admin"),
            PermDef("Payroll.Manage",      "Manage Payroll",         "HR"),
            PermDef("Attendance.Manage",   "Manage Attendance",      "HR")
        )
        val permKeys = permDefs.map { it.key }
        permDefs.forEach { p ->
            db.execSQL(
                "INSERT OR IGNORE INTO Permissions (PermissionKey, PermissionName, Module) " +
                "VALUES ('${p.key}', '${p.name}', '${p.module}')"
            )
        }

        // Role permissions — seed by subquery so we don't hard-code auto-increment IDs
        val managerKeys = permKeys.filter { it != "Users.Manage" && it != "Settings.Manage" }
        val cashierKeys = listOf(
            "POS.Access", "POS.Discount", "POS.Delivery", "POS.SaveOrder",
            "POS.HoldOrder", "POS.RecallOrder", "POS.PrintKOT",
            "Products.View", "Customers.Manage",
            "Shift.Open", "Shift.Close", "Expenses.Manage"
        )
        val kitchenKeys = listOf("POS.PrintKOT")
        val waiterKeys  = listOf("POS.Access", "POS.HoldOrder", "POS.RecallOrder", "POS.PrintKOT", "Products.View")

        fun seedRolePerms(roleId: Int, keys: List<String>) {
            keys.forEach { key ->
                db.execSQL(
                    "INSERT OR IGNORE INTO RolePermissions (RoleId, PermissionId, IsGranted) " +
                    "SELECT $roleId, PermissionId, 1 FROM Permissions WHERE PermissionKey = '$key'"
                )
            }
        }
        seedRolePerms(1, permKeys)     // Admin
        seedRolePerms(2, managerKeys)  // Manager
        seedRolePerms(3, cashierKeys)  // Cashier
        seedRolePerms(4, kitchenKeys)  // Kitchen
        seedRolePerms(5, waiterKeys)   // Waiter

        // Default admin — username: admin, PIN: 123123
        val adminHash = PasswordHelper.hash("123123")
        db.execSQL(
            "INSERT OR IGNORE INTO Users (UserId, FullName, Username, PasswordHash, RoleId, IsActive) " +
            "VALUES (1, 'Administrator', 'admin', '$adminHash', 1, 1)"
        )

        // App settings
        listOf(
            "CompanyName"           to "My Restaurant",
            "Address"               to "",
            "Phone"                 to "",
            "CurrencySymbol"        to "Rs.",
            "TaxPercent"            to "0",
            "EnableTax"             to "false",
            "DefaultOrderType"      to "DineIn",
            "TokenPrefix"           to "T",
            "ServiceChargePercent"  to "0",
            "AllowPartialPayment"   to "true",
            "ReceiptFooter"         to "Thank you for your visit!",
            "LastOrderNumber"       to "0",
            "OrderNumberPrefix"     to "ORD"
        ).forEach { (k, v) ->
            db.execSQL("INSERT OR IGNORE INTO AppSettings (SettingKey, SettingValue) VALUES ('$k', '$v')")
        }

        // Default branch (required — all Orders reference BranchId=1)
        db.execSQL(
            "INSERT OR IGNORE INTO Branches (BranchId, BranchName, Address, Phone, IsActive) " +
            "VALUES (1, 'Main Branch', '', '', 1)"
        )

        // Own Rider delivery company
        db.execSQL(
            "INSERT OR IGNORE INTO DeliveryCompanies (CompanyId, CompanyName, CommissionPercent, IsActive) " +
            "VALUES (1, 'Own Rider', 0, 1)"
        )

        // Default company settings row
        db.execSQL(
            "INSERT OR IGNORE INTO CompanySettings (SettingId, CompanyName, CurrencySymbol, DefaultTaxPercent, " +
            "ServiceChargePercent, DefaultOrderType, TokenPrefix, AllowPartialPayment) " +
            "VALUES (1, 'My Restaurant', 'Rs.', 0, 0, 'DineIn', 'T', 1)"
        )

        // Default categories matching WPF template
        db.execSQL("INSERT OR IGNORE INTO Categories (CategoryId, CategoryName, ColorCode, DisplayOrder) VALUES (1, 'Burgers',    '#FF6B35', 1)")
        db.execSQL("INSERT OR IGNORE INTO Categories (CategoryId, CategoryName, ColorCode, DisplayOrder) VALUES (2, 'Pizza',      '#E74C3C', 2)")
        db.execSQL("INSERT OR IGNORE INTO Categories (CategoryId, CategoryName, ColorCode, DisplayOrder) VALUES (3, 'Beverages',  '#3498DB', 3)")
        db.execSQL("INSERT OR IGNORE INTO Categories (CategoryId, CategoryName, ColorCode, DisplayOrder) VALUES (4, 'Appetizers', '#2ECC71', 4)")
        db.execSQL("INSERT OR IGNORE INTO Categories (CategoryId, CategoryName, ColorCode, DisplayOrder) VALUES (5, 'Desserts',   '#9B59B6', 5)")
        db.execSQL("INSERT OR IGNORE INTO Categories (CategoryId, CategoryName, ColorCode, DisplayOrder) VALUES (6, 'Rice Dishes','#F39C12', 6)")
        db.execSQL("INSERT OR IGNORE INTO Categories (CategoryId, CategoryName, ColorCode, DisplayOrder) VALUES (7, 'Sandwiches', '#1ABC9C', 7)")
        db.execSQL("INSERT OR IGNORE INTO Categories (CategoryId, CategoryName, ColorCode, DisplayOrder) VALUES (8, 'Soups',      '#E67E22', 8)")

        // Default area and a sample table
        db.execSQL("INSERT OR IGNORE INTO Areas (AreaId, AreaName, BranchId, DisplayOrder) VALUES (1, 'Main Hall', 1, 0)")
        db.execSQL("INSERT OR IGNORE INTO Tables (TableId, TableName, AreaId, Capacity, TableStatus) VALUES (1, 'Table 1', 1, 4, 'Available')")
        db.execSQL("INSERT OR IGNORE INTO Tables (TableId, TableName, AreaId, Capacity, TableStatus) VALUES (2, 'Table 2', 1, 4, 'Available')")
        db.execSQL("INSERT OR IGNORE INTO Tables (TableId, TableName, AreaId, Capacity, TableStatus) VALUES (3, 'Table 3', 1, 4, 'Available')")
        db.execSQL("INSERT OR IGNORE INTO Tables (TableId, TableName, AreaId, Capacity, TableStatus) VALUES (4, 'Table 4', 1, 4, 'Available')")

        // Default Chart of Accounts — matches WPF seed data
        db.execSQL("INSERT OR IGNORE INTO ChartOfAccounts (AccountCode, AccountName, AccountType) VALUES ('1000', 'Cash',                          'Asset')")
        db.execSQL("INSERT OR IGNORE INTO ChartOfAccounts (AccountCode, AccountName, AccountType) VALUES ('1100', 'Accounts Receivable – Delivery','Asset')")
        db.execSQL("INSERT OR IGNORE INTO ChartOfAccounts (AccountCode, AccountName, AccountType) VALUES ('2000', 'Accounts Payable – Suppliers',  'Liability')")
        db.execSQL("INSERT OR IGNORE INTO ChartOfAccounts (AccountCode, AccountName, AccountType) VALUES ('4000', 'Sales Revenue',                 'Income')")
        db.execSQL("INSERT OR IGNORE INTO ChartOfAccounts (AccountCode, AccountName, AccountType) VALUES ('5000', 'Cost of Goods Sold',            'Expense')")
        db.execSQL("INSERT OR IGNORE INTO ChartOfAccounts (AccountCode, AccountName, AccountType) VALUES ('5100', 'Operational Expenses',          'Expense')")
        db.execSQL("INSERT OR IGNORE INTO ChartOfAccounts (AccountCode, AccountName, AccountType) VALUES ('5200', 'Salary & Wages',                'Expense')")
        db.execSQL("INSERT OR IGNORE INTO ChartOfAccounts (AccountCode, AccountName, AccountType) VALUES ('5300', 'Purchase Payments',             'Expense')")
    }
}
