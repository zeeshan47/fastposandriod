import sqlite3, hashlib, os

db_path = os.path.join(os.path.expanduser("~"), "OneDrive", "Desktop", "fastpos_standalone.db")
if os.path.exists(db_path):
    os.remove(db_path)

conn = sqlite3.connect(db_path)
conn.execute("PRAGMA journal_mode=WAL")
conn.execute("PRAGMA foreign_keys=OFF")
c = conn.cursor()

def h(pw):
    return hashlib.sha256(pw.encode('utf-8')).hexdigest()

# ── SCHEMA ─────────────────────────────────────────────────────────────────────
ddl = [
    "CREATE TABLE IF NOT EXISTS Roles (RoleId INTEGER PRIMARY KEY AUTOINCREMENT, RoleName TEXT NOT NULL, IsActive INTEGER NOT NULL DEFAULT 1)",
    "CREATE TABLE IF NOT EXISTS Users (UserId INTEGER PRIMARY KEY AUTOINCREMENT, FullName TEXT NOT NULL, Username TEXT NOT NULL UNIQUE, PasswordHash TEXT NOT NULL, RoleId INTEGER NOT NULL DEFAULT 1, IsActive INTEGER NOT NULL DEFAULT 1, BasicSalary REAL NOT NULL DEFAULT 0, Phone TEXT DEFAULT '', Designation TEXT DEFAULT '', JoiningDate TEXT, LastLogin TEXT, CreatedAt TEXT DEFAULT (datetime('now')), UpdatedAt TEXT)",
    "CREATE TABLE IF NOT EXISTS Permissions (PermissionId INTEGER PRIMARY KEY AUTOINCREMENT, PermissionKey TEXT NOT NULL UNIQUE, PermissionName TEXT NOT NULL, Module TEXT NOT NULL DEFAULT '', IsActive INTEGER NOT NULL DEFAULT 1)",
    "CREATE TABLE IF NOT EXISTS RolePermissions (RolePermissionId INTEGER PRIMARY KEY AUTOINCREMENT, RoleId INTEGER NOT NULL, PermissionId INTEGER NOT NULL, IsGranted INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')), UNIQUE(RoleId, PermissionId))",
    "CREATE TABLE IF NOT EXISTS AppSettings (SettingKey TEXT PRIMARY KEY, SettingValue TEXT NOT NULL DEFAULT '')",
    "CREATE TABLE IF NOT EXISTS CompanySettings (SettingId INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT NOT NULL DEFAULT 'My Restaurant', Address TEXT DEFAULT '', Phone TEXT DEFAULT '', Email TEXT DEFAULT '', Website TEXT DEFAULT '', TaxNumber TEXT DEFAULT '', TaxLabel TEXT DEFAULT 'Tax', CurrencySymbol TEXT NOT NULL DEFAULT 'Rs.', DefaultTaxPercent REAL NOT NULL DEFAULT 0, ServiceChargePercent REAL NOT NULL DEFAULT 0, DefaultOrderType TEXT NOT NULL DEFAULT 'DineIn', TokenPrefix TEXT NOT NULL DEFAULT 'T', AllowPartialPayment INTEGER NOT NULL DEFAULT 1, AllowNegativeStock INTEGER NOT NULL DEFAULT 0, MaxDiscountPercent REAL NOT NULL DEFAULT 0, RequireWaiter INTEGER NOT NULL DEFAULT 0, ReceiptFooter TEXT DEFAULT 'Thank you for your visit!', Language TEXT DEFAULT 'English', SmsEnabled INTEGER NOT NULL DEFAULT 0, SmsGatewayUrl TEXT DEFAULT '', WhatsappEnabled INTEGER NOT NULL DEFAULT 0, WhatsappGatewayUrl TEXT DEFAULT '', NotifyOnOrderPlaced INTEGER NOT NULL DEFAULT 1, NotifyOnOrderReady INTEGER NOT NULL DEFAULT 1, NotifyOnOrderCancelled INTEGER NOT NULL DEFAULT 0, RefreshMode TEXT DEFAULT 'Normal', LogoPath TEXT DEFAULT '', LogoData BLOB DEFAULT NULL, AutoPrintTakeawayToken INTEGER NOT NULL DEFAULT 0, UpdatedAt TEXT)",
    "CREATE TABLE IF NOT EXISTS Branches (BranchId INTEGER PRIMARY KEY AUTOINCREMENT, BranchName TEXT NOT NULL, Address TEXT DEFAULT '', Phone TEXT DEFAULT '', IsActive INTEGER NOT NULL DEFAULT 1, CreatedBy INTEGER, CreatedAt TEXT DEFAULT (datetime('now')), UpdatedAt TEXT, UpdatedBy INTEGER)",
    "CREATE TABLE IF NOT EXISTS Areas (AreaId INTEGER PRIMARY KEY AUTOINCREMENT, AreaName TEXT NOT NULL, IsActive INTEGER NOT NULL DEFAULT 1)",
    "CREATE TABLE IF NOT EXISTS Tables (TableId INTEGER PRIMARY KEY AUTOINCREMENT, TableName TEXT NOT NULL, AreaId INTEGER, Capacity INTEGER DEFAULT 4, TableStatus TEXT NOT NULL DEFAULT 'Available', IsActive INTEGER NOT NULL DEFAULT 1)",
    "CREATE TABLE IF NOT EXISTS Categories (CategoryId INTEGER PRIMARY KEY AUTOINCREMENT, CategoryName TEXT NOT NULL, CategoryNameOtherLanguage TEXT DEFAULT '', ColorCode TEXT DEFAULT '#FF6B35', DisplayOrder INTEGER DEFAULT 0, IsActive INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')), CreatedBy INTEGER, UpdatedAt TEXT)",
    "CREATE TABLE IF NOT EXISTS Taxes (TaxId INTEGER PRIMARY KEY AUTOINCREMENT, TaxName TEXT NOT NULL, TaxPercent REAL NOT NULL DEFAULT 0, IsActive INTEGER NOT NULL DEFAULT 1)",
    "CREATE TABLE IF NOT EXISTS Products (ProductId INTEGER PRIMARY KEY AUTOINCREMENT, ProductCode TEXT DEFAULT '', ProductName TEXT NOT NULL, ProductNameOtherLanguage TEXT DEFAULT '', CategoryId INTEGER, ProductType TEXT DEFAULT 'Normal', SalePrice REAL NOT NULL DEFAULT 0, PurchasePrice REAL NOT NULL DEFAULT 0, IsStockManaged INTEGER NOT NULL DEFAULT 0, IsRecipeBased INTEGER NOT NULL DEFAULT 0, OpeningStock REAL NOT NULL DEFAULT 0, CurrentStock REAL NOT NULL DEFAULT 0, MinimumStock REAL NOT NULL DEFAULT 5, ReorderLevel REAL NOT NULL DEFAULT 0, Unit TEXT DEFAULT 'Pcs', DisplayOrder INTEGER DEFAULT 0, IsAvailable INTEGER NOT NULL DEFAULT 1, TaxId INTEGER, PrinterName TEXT DEFAULT '', IsActive INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')), CreatedBy INTEGER, UpdatedAt TEXT, UpdatedBy INTEGER)",
    "CREATE TABLE IF NOT EXISTS ProductSizes (SizeId INTEGER PRIMARY KEY AUTOINCREMENT, ProductId INTEGER NOT NULL, SizeName TEXT NOT NULL, Price REAL NOT NULL DEFAULT 0, DisplayOrder INTEGER DEFAULT 0, IsActive INTEGER NOT NULL DEFAULT 1)",
    "CREATE TABLE IF NOT EXISTS ModifierGroups (ModifierGroupId INTEGER PRIMARY KEY AUTOINCREMENT, GroupName TEXT NOT NULL, MinSelection INTEGER NOT NULL DEFAULT 0, MaxSelection INTEGER NOT NULL DEFAULT 1, IsRequired INTEGER NOT NULL DEFAULT 0, IsActive INTEGER NOT NULL DEFAULT 1)",
    "CREATE TABLE IF NOT EXISTS ProductModifiers (ModifierId INTEGER PRIMARY KEY AUTOINCREMENT, ModifierGroupId INTEGER NOT NULL, ModifierName TEXT NOT NULL, ExtraPrice REAL NOT NULL DEFAULT 0, StockItemId INTEGER NULL, IsActive INTEGER NOT NULL DEFAULT 1)",
    "CREATE TABLE IF NOT EXISTS ProductModifierGroups (ProductId INTEGER NOT NULL, ModifierGroupId INTEGER NOT NULL, PRIMARY KEY (ProductId, ModifierGroupId))",
    "CREATE TABLE IF NOT EXISTS Orders (OrderId INTEGER PRIMARY KEY AUTOINCREMENT, OrderNo TEXT NOT NULL DEFAULT '', TokenNo TEXT NOT NULL DEFAULT '', OrderDate TEXT DEFAULT (datetime('now')), OrderType TEXT NOT NULL DEFAULT 'Takeaway', BranchId INTEGER DEFAULT 1, TableId INTEGER, WaiterId INTEGER, CustomerId INTEGER, CustomerName TEXT DEFAULT '', ShiftId INTEGER, SubTotal REAL NOT NULL DEFAULT 0, DiscountAmount REAL NOT NULL DEFAULT 0, DiscountPercent REAL NOT NULL DEFAULT 0, TaxAmount REAL NOT NULL DEFAULT 0, TaxPercent REAL NOT NULL DEFAULT 0, ServiceCharges REAL NOT NULL DEFAULT 0, DeliveryCharge REAL NOT NULL DEFAULT 0, Tips REAL NOT NULL DEFAULT 0, IsRush INTEGER NOT NULL DEFAULT 0, GrandTotal REAL NOT NULL DEFAULT 0, PaidAmount REAL NOT NULL DEFAULT 0, BalanceAmount REAL NOT NULL DEFAULT 0, OrderStatus TEXT NOT NULL DEFAULT 'New', PaymentStatus TEXT NOT NULL DEFAULT 'Unpaid', DeliveryName TEXT DEFAULT '', DeliveryPhone TEXT DEFAULT '', DeliveryAddress TEXT DEFAULT '', DeliveryCompanyId INTEGER, Notes TEXT DEFAULT '', CreatedBy INTEGER, CreatedAt TEXT DEFAULT (datetime('now')), UpdatedAt TEXT)",
    "CREATE TABLE IF NOT EXISTS OrderItems (OrderItemId INTEGER PRIMARY KEY AUTOINCREMENT, OrderId INTEGER NOT NULL, ProductId INTEGER, SizeId INTEGER, ProductNameSnapshot TEXT DEFAULT '', SizeNameSnapshot TEXT, Quantity REAL NOT NULL DEFAULT 1, UnitPrice REAL NOT NULL DEFAULT 0, DiscountAmount REAL NOT NULL DEFAULT 0, LineTotal REAL NOT NULL DEFAULT 0, Notes TEXT, KitchenStatus TEXT NOT NULL DEFAULT 'Pending', CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS OrderItemModifiers (OrderItemModifierId INTEGER PRIMARY KEY AUTOINCREMENT, OrderItemId INTEGER NOT NULL, ModifierId INTEGER, ModifierNameSnapshot TEXT DEFAULT '', ExtraPrice REAL NOT NULL DEFAULT 0, Quantity INTEGER NOT NULL DEFAULT 1, Total REAL NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS KitchenTickets (TicketId INTEGER PRIMARY KEY AUTOINCREMENT, OrderId INTEGER NOT NULL, TicketNo TEXT DEFAULT '', PrintedAt TEXT DEFAULT (datetime('now')), TicketStatus TEXT NOT NULL DEFAULT 'Pending', CompletedAt TEXT)",
    "CREATE TABLE IF NOT EXISTS KitchenTicketItems (TicketItemId INTEGER PRIMARY KEY AUTOINCREMENT, TicketId INTEGER NOT NULL, OrderItemId INTEGER, ItemName TEXT DEFAULT '', Quantity REAL NOT NULL DEFAULT 1, Notes TEXT, ItemStatus TEXT NOT NULL DEFAULT 'Pending', CompletedAt TEXT)",
    "CREATE TABLE IF NOT EXISTS OrderPayments (PaymentId INTEGER PRIMARY KEY AUTOINCREMENT, OrderId INTEGER NOT NULL, PaymentMethod TEXT NOT NULL DEFAULT 'Cash', Amount REAL NOT NULL DEFAULT 0, Reference TEXT DEFAULT '', PaymentDate TEXT DEFAULT (datetime('now')), CreatedBy INTEGER)",
    "CREATE TABLE IF NOT EXISTS Customers (CustomerId INTEGER PRIMARY KEY AUTOINCREMENT, CustomerName TEXT NOT NULL, Phone TEXT DEFAULT '', Address TEXT DEFAULT '', LoyaltyPoints INTEGER NOT NULL DEFAULT 0, TotalOrders INTEGER NOT NULL DEFAULT 0, TotalSpent REAL NOT NULL DEFAULT 0, IsActive INTEGER NOT NULL DEFAULT 1, CreatedBy INTEGER, CreatedAt TEXT DEFAULT (datetime('now')), UpdatedAt TEXT, UpdatedBy INTEGER)",
    "CREATE TABLE IF NOT EXISTS Shifts (ShiftId INTEGER PRIMARY KEY AUTOINCREMENT, ShiftCode TEXT NOT NULL, BusinessDate TEXT NOT NULL DEFAULT (date('now')), UserId INTEGER NOT NULL, BranchId INTEGER NOT NULL DEFAULT 1, OpeningTime TEXT DEFAULT (datetime('now')), ClosingTime TEXT, OpeningCash REAL DEFAULT 0, ClosingCash REAL DEFAULT 0, TotalSales REAL DEFAULT 0, TotalExpenses REAL DEFAULT 0, ExpectedCash REAL DEFAULT 0, Difference REAL DEFAULT 0, ShiftStatus TEXT NOT NULL DEFAULT 'Open', Notes TEXT DEFAULT '', CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS Expenses (ExpenseId INTEGER PRIMARY KEY AUTOINCREMENT, ShiftId INTEGER, ExpenseDate TEXT NOT NULL DEFAULT (datetime('now')), ExpenseType TEXT NOT NULL DEFAULT 'Other', Description TEXT DEFAULT '', Amount REAL NOT NULL DEFAULT 0, PaidTo TEXT DEFAULT '', PaymentMethod TEXT DEFAULT 'Cash', IsActive INTEGER NOT NULL DEFAULT 1, CreatedBy INTEGER, CreatedAt TEXT DEFAULT (datetime('now')), UpdatedAt TEXT, UpdatedBy INTEGER)",
    "CREATE TABLE IF NOT EXISTS EmployeeAttendance (AttendanceId INTEGER PRIMARY KEY AUTOINCREMENT, EmployeeId INTEGER NOT NULL, BranchId INTEGER NOT NULL DEFAULT 1, AttendanceDate TEXT NOT NULL, CheckInTime TEXT NULL, CheckOutTime TEXT NULL, Notes TEXT DEFAULT '', CreatedAt TEXT DEFAULT (datetime('now')), CreatedBy INTEGER NULL)",
    "CREATE TABLE IF NOT EXISTS PayrollRecords (PayrollId INTEGER PRIMARY KEY AUTOINCREMENT, UserId INTEGER NOT NULL, PayMonth INTEGER NOT NULL, PayYear INTEGER NOT NULL, WorkingDays INTEGER DEFAULT 0, PresentDays INTEGER DEFAULT 0, LeaveDays INTEGER DEFAULT 0, BasicSalary REAL DEFAULT 0, Allowances REAL DEFAULT 0, Deductions REAL DEFAULT 0, NetSalary REAL DEFAULT 0, PaymentStatus TEXT NOT NULL DEFAULT 'Pending', PaidDate TEXT, Notes TEXT DEFAULT '', CreatedBy INTEGER, CreatedAt TEXT DEFAULT (datetime('now')), UNIQUE(UserId, PayMonth, PayYear))",
    "CREATE TABLE IF NOT EXISTS CashTransactions (TransactionId INTEGER PRIMARY KEY AUTOINCREMENT, ShiftId INTEGER NOT NULL, BranchId INTEGER NOT NULL DEFAULT 1, TransactionType TEXT NOT NULL DEFAULT 'In', Amount REAL NOT NULL DEFAULT 0, Reason TEXT NOT NULL DEFAULT '', Notes TEXT, CreatedBy INTEGER NOT NULL DEFAULT 0, CreatedAt TEXT NOT NULL DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS RawMaterials (MaterialId INTEGER PRIMARY KEY AUTOINCREMENT, MaterialName TEXT NOT NULL, Unit TEXT DEFAULT 'kg', CurrentStock REAL NOT NULL DEFAULT 0, MinStockLevel REAL NOT NULL DEFAULT 0, CostPerUnit REAL NOT NULL DEFAULT 0, IsActive INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS ProductRecipes (RecipeId INTEGER PRIMARY KEY AUTOINCREMENT, ProductId INTEGER NOT NULL, SizeId INTEGER NOT NULL DEFAULT 0, MaterialId INTEGER NOT NULL, QuantityRequired REAL NOT NULL DEFAULT 0, UNIQUE(ProductId, SizeId, MaterialId))",
    "CREATE TABLE IF NOT EXISTS Recipes (RecipeId INTEGER PRIMARY KEY AUTOINCREMENT, ProductId INTEGER NOT NULL, SizeId INTEGER NULL, RecipeName TEXT DEFAULT '', IsActive INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')), CreatedBy INTEGER NULL, UpdatedAt TEXT NULL, UpdatedBy INTEGER NULL)",
    "CREATE TABLE IF NOT EXISTS RecipeItems (RecipeItemId INTEGER PRIMARY KEY AUTOINCREMENT, RecipeId INTEGER NOT NULL, ProductId INTEGER NOT NULL, QuantityUsed REAL NOT NULL DEFAULT 1, Unit TEXT DEFAULT 'kg')",
    "CREATE TABLE IF NOT EXISTS InventoryLedger (LedgerId INTEGER PRIMARY KEY AUTOINCREMENT, ProductId INTEGER NOT NULL, TransactionDate TEXT DEFAULT (datetime('now')), ReferenceType TEXT DEFAULT '', ReferenceId INTEGER, InQty REAL NOT NULL DEFAULT 0, OutQty REAL NOT NULL DEFAULT 0, BalanceQty REAL NOT NULL DEFAULT 0, Rate REAL DEFAULT 0, Amount REAL DEFAULT 0, Remarks TEXT DEFAULT '', CreatedBy INTEGER, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS StockLedger (LedgerId INTEGER PRIMARY KEY AUTOINCREMENT, MaterialId INTEGER NOT NULL, TransDate TEXT DEFAULT (datetime('now')), RefType TEXT DEFAULT '', RefId INTEGER, InQty REAL DEFAULT 0, OutQty REAL DEFAULT 0, Rate REAL DEFAULT 0, Amount REAL DEFAULT 0, Remarks TEXT DEFAULT '')",
    "CREATE TABLE IF NOT EXISTS WasteEntries (WasteId INTEGER PRIMARY KEY AUTOINCREMENT, WasteDate TEXT DEFAULT (datetime('now')), Notes TEXT DEFAULT '', TotalAmount REAL NOT NULL DEFAULT 0, IsActive INTEGER NOT NULL DEFAULT 1, CreatedBy INTEGER, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS WasteEntryItems (ItemId INTEGER PRIMARY KEY AUTOINCREMENT, WasteId INTEGER NOT NULL, ProductId INTEGER NOT NULL, Quantity REAL NOT NULL DEFAULT 1, Unit TEXT DEFAULT 'kg', Rate REAL DEFAULT 0, Amount REAL DEFAULT 0, Reason TEXT DEFAULT '')",
    "CREATE TABLE IF NOT EXISTS Suppliers (SupplierId INTEGER PRIMARY KEY AUTOINCREMENT, SupplierName TEXT NOT NULL, ContactPerson TEXT DEFAULT '', Phone TEXT DEFAULT '', Address TEXT DEFAULT '', Email TEXT DEFAULT '', IsActive INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS PurchaseInvoices (InvoiceId INTEGER PRIMARY KEY AUTOINCREMENT, InvoiceNo TEXT NOT NULL, SupplierId INTEGER, BranchId INTEGER DEFAULT 1, InvoiceDate TEXT NOT NULL DEFAULT (datetime('now')), GrandTotal REAL DEFAULT 0, TotalAmount REAL DEFAULT 0, PaidAmount REAL DEFAULT 0, BalanceAmount REAL DEFAULT 0, PaymentStatus TEXT DEFAULT 'Unpaid', Notes TEXT DEFAULT '', IsActive INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS PurchaseInvoiceItems (ItemId INTEGER PRIMARY KEY AUTOINCREMENT, InvoiceId INTEGER NOT NULL, ProductId INTEGER, ItemName TEXT DEFAULT '', Unit TEXT DEFAULT 'kg', Quantity REAL DEFAULT 1, PurchaseRate REAL DEFAULT 0, LineTotal REAL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS PurchasePayments (PaymentId INTEGER PRIMARY KEY AUTOINCREMENT, SupplierId INTEGER NOT NULL, PaymentDate TEXT DEFAULT (datetime('now')), Amount REAL NOT NULL DEFAULT 0, PaymentMethod TEXT DEFAULT 'Cash', Reference TEXT DEFAULT '', Notes TEXT DEFAULT '', ShiftId INTEGER NULL, IsActive INTEGER DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS PurchaseReturns (ReturnId INTEGER PRIMARY KEY AUTOINCREMENT, InvoiceId INTEGER, SupplierId INTEGER, ReturnDate TEXT DEFAULT (datetime('now')), TotalAmount REAL DEFAULT 0, Notes TEXT DEFAULT '', RefundMethod TEXT DEFAULT 'Credit', ShiftId INTEGER NULL, IsActive INTEGER DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')), CreatedBy INTEGER)",
    "CREATE TABLE IF NOT EXISTS PurchaseReturnItems (ReturnItemId INTEGER PRIMARY KEY AUTOINCREMENT, ReturnId INTEGER NOT NULL, ProductId INTEGER NOT NULL, ItemName TEXT NOT NULL DEFAULT '', Quantity REAL DEFAULT 1, Unit TEXT DEFAULT '', ReturnRate REAL DEFAULT 0, LineTotal REAL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS Deals (DealId INTEGER PRIMARY KEY AUTOINCREMENT, DealName TEXT NOT NULL, Description TEXT DEFAULT '', DealPrice REAL NOT NULL DEFAULT 0, DiscountPercent REAL DEFAULT 0, ValidFrom TEXT, ValidTo TEXT, IsActive INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS DealItems (ItemId INTEGER PRIMARY KEY AUTOINCREMENT, DealId INTEGER NOT NULL, ProductId INTEGER NOT NULL, SizeId INTEGER, Quantity INTEGER NOT NULL DEFAULT 1, IsOptional INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS Reservations (ReservationId INTEGER PRIMARY KEY AUTOINCREMENT, CustomerName TEXT NOT NULL, Phone TEXT DEFAULT '', PartySize INTEGER DEFAULT 1, ReservationDate TEXT NOT NULL, ReservationTime TEXT NOT NULL DEFAULT '19:00', TableId INTEGER, Status TEXT NOT NULL DEFAULT 'Confirmed', Notes TEXT DEFAULT '', CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS DeliveryCompanies (CompanyId INTEGER PRIMARY KEY AUTOINCREMENT, CompanyName TEXT NOT NULL, CommissionPercent REAL DEFAULT 0, IsActive INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS DeliverySettlements (SettlementId INTEGER PRIMARY KEY AUTOINCREMENT, DeliveryCompanyId INTEGER NOT NULL, SettlementDate TEXT NOT NULL, AmountReceived REAL NOT NULL DEFAULT 0, Notes TEXT DEFAULT '', CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS Waiters (WaiterId INTEGER PRIMARY KEY AUTOINCREMENT, WaiterName TEXT NOT NULL, Phone TEXT DEFAULT '', IsActive INTEGER NOT NULL DEFAULT 1, LinkedEmployeeId INTEGER)",
    "CREATE TABLE IF NOT EXISTS Vouchers (VoucherId INTEGER PRIMARY KEY AUTOINCREMENT, VoucherCode TEXT NOT NULL UNIQUE, Description TEXT DEFAULT '', DiscountType TEXT NOT NULL DEFAULT 'Percent', DiscountValue REAL NOT NULL DEFAULT 0, MinOrderAmount REAL DEFAULT 0, MaxUses INTEGER DEFAULT 0, UsedCount INTEGER DEFAULT 0, ExpiryDate TEXT, IsActive INTEGER NOT NULL DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS ProductSchedules (ScheduleId INTEGER PRIMARY KEY AUTOINCREMENT, ProductId INTEGER NOT NULL, Label TEXT DEFAULT '', DayOfWeek INTEGER NOT NULL DEFAULT -1, StartTime TEXT NOT NULL DEFAULT '00:00', EndTime TEXT NOT NULL DEFAULT '23:59', IsActive INTEGER NOT NULL DEFAULT 1)",
    "CREATE TABLE IF NOT EXISTS CustomerWallet (WalletId INTEGER PRIMARY KEY AUTOINCREMENT, CustomerId INTEGER NOT NULL UNIQUE, Balance REAL NOT NULL DEFAULT 0, LastUpdated TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS WalletTransactions (TransId INTEGER PRIMARY KEY AUTOINCREMENT, CustomerId INTEGER NOT NULL, TransDate TEXT DEFAULT (datetime('now')), TransType TEXT NOT NULL, Amount REAL NOT NULL DEFAULT 0, OrderId INTEGER, Notes TEXT, CreatedBy INTEGER)",
    "CREATE TABLE IF NOT EXISTS CustomerFeedback (FeedbackId INTEGER PRIMARY KEY AUTOINCREMENT, CustomerId INTEGER, OrderId INTEGER, Rating INTEGER NOT NULL DEFAULT 5, Comment TEXT, FeedbackDate TEXT NOT NULL DEFAULT (datetime('now')), CreatedAt TEXT NOT NULL DEFAULT (datetime('now')), CreatedBy INTEGER)",
    "CREATE TABLE IF NOT EXISTS StockTakes (StockTakeId INTEGER PRIMARY KEY AUTOINCREMENT, TakeDate TEXT NOT NULL DEFAULT (datetime('now')), Status TEXT NOT NULL DEFAULT 'Open', Notes TEXT NOT NULL DEFAULT '', CreatedBy INTEGER, FinalizedAt TEXT)",
    "CREATE TABLE IF NOT EXISTS StockTakeItems (ItemId INTEGER PRIMARY KEY AUTOINCREMENT, StockTakeId INTEGER NOT NULL, MaterialId INTEGER NOT NULL, MaterialName TEXT NOT NULL DEFAULT '', Unit TEXT NOT NULL DEFAULT 'kg', ExpectedQty REAL NOT NULL DEFAULT 0, ActualQty REAL NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS Employees (EmployeeId INTEGER PRIMARY KEY AUTOINCREMENT, EmployeeName TEXT NOT NULL, Phone TEXT DEFAULT '', EmployeeRole TEXT DEFAULT '', JoiningDate TEXT, MonthlySalary REAL NOT NULL DEFAULT 0, IsActive INTEGER NOT NULL DEFAULT 1, BranchId INTEGER DEFAULT 1, CreatedAt TEXT DEFAULT (datetime('now')), CreatedBy INTEGER)",
    "CREATE TABLE IF NOT EXISTS EmployeeSalaries (UserId INTEGER PRIMARY KEY, MonthlySalary REAL NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS EmployeeAdvances (AdvanceId INTEGER PRIMARY KEY AUTOINCREMENT, EmployeeId INTEGER NOT NULL, AdvanceDate TEXT DEFAULT (datetime('now')), Amount REAL NOT NULL DEFAULT 0, Notes TEXT DEFAULT '', ShiftId INTEGER, IsActive INTEGER DEFAULT 1, CreatedBy INTEGER, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS SalaryPayments (PaymentId INTEGER PRIMARY KEY AUTOINCREMENT, EmployeeId INTEGER NOT NULL, PaymentDate TEXT DEFAULT (datetime('now')), Amount REAL NOT NULL DEFAULT 0, PeriodMonth INTEGER NOT NULL DEFAULT 1, PeriodYear INTEGER NOT NULL DEFAULT 2025, PaymentMethod TEXT DEFAULT 'Cash', Notes TEXT DEFAULT '', ShiftId INTEGER, IsActive INTEGER DEFAULT 1, CreatedBy INTEGER, CreatedAt TEXT DEFAULT (datetime('now')))",
    "CREATE TABLE IF NOT EXISTS AuditLogs (LogId INTEGER PRIMARY KEY AUTOINCREMENT, LoggedAt TEXT DEFAULT (datetime('now')), UserId INTEGER, Action TEXT NOT NULL DEFAULT '', TableName TEXT NOT NULL DEFAULT '', RecordId INTEGER, MachineName TEXT DEFAULT '', Details TEXT DEFAULT '')",
    "CREATE TABLE IF NOT EXISTS MessageLog (MessageId INTEGER PRIMARY KEY AUTOINCREMENT, OrderId INTEGER, RecipientPhone TEXT NOT NULL DEFAULT '', Channel TEXT NOT NULL DEFAULT '', MessageText TEXT NOT NULL DEFAULT '', Status TEXT NOT NULL DEFAULT 'Sent', ErrorMessage TEXT DEFAULT '', SentAt TEXT, CreatedAt TEXT NOT NULL DEFAULT (datetime('now')))",
    "CREATE VIEW IF NOT EXISTS RestaurantTables AS SELECT * FROM Tables",
    "CREATE VIEW IF NOT EXISTS DiningAreas AS SELECT AreaId, AreaName FROM Areas",
]
for sql in ddl:
    c.execute(sql)

# ── ROLES ──────────────────────────────────────────────────────────────────────
c.executemany("INSERT OR IGNORE INTO Roles(RoleId,RoleName) VALUES(?,?)", [
    (1,'Admin'),(2,'Manager'),(3,'Cashier'),(4,'Kitchen'),(5,'Waiter')
])

# ── PERMISSIONS ────────────────────────────────────────────────────────────────
perms = [
    (1,'POS.Access','Access POS','POS'),
    (2,'POS.Discount','Apply Discount','POS'),
    (3,'POS.EditPrice','Edit Item Price','POS'),
    (4,'POS.Delivery','Manage Delivery','POS'),
    (5,'POS.SaveOrder','Save/Finalize Order','POS'),
    (6,'POS.CancelOrder','Cancel Order','POS'),
    (7,'POS.HoldOrder','Hold Order','POS'),
    (8,'POS.RecallOrder','Recall Order','POS'),
    (9,'POS.PrintKOT','Print KOT','POS'),
    (10,'Products.View','View Products','Products'),
    (11,'Products.Add','Add Products','Products'),
    (12,'Products.Edit','Edit Products','Products'),
    (13,'Products.Delete','Delete Products','Products'),
    (14,'Categories.Manage','Manage Categories','Products'),
    (15,'Sizes.Manage','Manage Sizes','Products'),
    (16,'Modifiers.Manage','Manage Modifiers','Products'),
    (17,'Deals.Manage','Manage Deals','Products'),
    (18,'Tables.Manage','Manage Tables','Setup'),
    (19,'Customers.Manage','Manage Customers','Setup'),
    (20,'Waiters.Manage','Manage Waiters','Setup'),
    (21,'Shift.Open','Open Shift','Operations'),
    (22,'Shift.Close','Close Shift','Operations'),
    (23,'Expenses.Manage','Manage Expenses','Operations'),
    (24,'Inventory.Purchase','Purchase & Suppliers','Inventory'),
    (25,'Inventory.Stock','Manage Stock','Inventory'),
    (26,'Inventory.Recipe','Manage Recipes','Inventory'),
    (27,'Inventory.Waste','Record Waste','Inventory'),
    (28,'Reports.View','View Reports','Reports'),
    (29,'Reports.Profit','View Profit Reports','Reports'),
    (30,'Users.Manage','Manage Users & Roles','Admin'),
    (31,'Settings.Manage','Manage Settings','Admin'),
]
c.executemany("INSERT OR IGNORE INTO Permissions(PermissionId,PermissionKey,PermissionName,Module) VALUES(?,?,?,?)", perms)

all_pids     = [p[0] for p in perms]
manager_pids = [p[0] for p in perms if p[0] not in (30,31)]
cashier_pids = [1,2,4,5,7,8,9,10,19,21,22,23]
kitchen_pids = [9]
waiter_pids  = [1,7,8,9,10]
for role_id, pids in [(1,all_pids),(2,manager_pids),(3,cashier_pids),(4,kitchen_pids),(5,waiter_pids)]:
    c.executemany("INSERT OR IGNORE INTO RolePermissions(RoleId,PermissionId,IsGranted) VALUES(?,?,1)",
                  [(role_id,pid) for pid in pids])

# ── USERS ──────────────────────────────────────────────────────────────────────
c.execute("INSERT OR IGNORE INTO Users(UserId,FullName,Username,PasswordHash,RoleId,IsActive) VALUES(1,'Administrator','admin',?,1,1)", (h('admin123'),))
c.execute("INSERT OR IGNORE INTO Users(UserId,FullName,Username,PasswordHash,RoleId,IsActive) VALUES(2,'Sara Khan','manager',?,2,1)", (h('manager123'),))
c.execute("INSERT OR IGNORE INTO Users(UserId,FullName,Username,PasswordHash,RoleId,IsActive) VALUES(3,'Ali Raza','cashier',?,3,1)", (h('cashier123'),))

# ── BRANCH / COMPANY ───────────────────────────────────────────────────────────
c.execute("INSERT OR IGNORE INTO Branches(BranchId,BranchName,Address,Phone,IsActive) VALUES(1,'Main Branch','123 Food Street, City','0300-1234567',1)")
c.execute("""INSERT OR IGNORE INTO CompanySettings
    (SettingId,CompanyName,Address,Phone,CurrencySymbol,DefaultTaxPercent,
     ServiceChargePercent,DefaultOrderType,TokenPrefix,AllowPartialPayment,ReceiptFooter)
    VALUES(1,'FastBite Restaurant','123 Food Street, City','0300-1234567',
           'Rs.',0,0,'DineIn','T',1,'Thank you! Visit again.')""")
c.executemany("INSERT OR IGNORE INTO AppSettings(SettingKey,SettingValue) VALUES(?,?)", [
    ('CompanyName','FastBite Restaurant'), ('Address','123 Food Street, City'),
    ('Phone','0300-1234567'),              ('CurrencySymbol','Rs.'),
    ('TaxPercent','0'),                   ('EnableTax','false'),
    ('DefaultOrderType','DineIn'),        ('TokenPrefix','T'),
    ('ServiceChargePercent','0'),         ('AllowPartialPayment','true'),
    ('ReceiptFooter','Thank you! Visit again.'),
])

# ── DELIVERY COMPANIES ─────────────────────────────────────────────────────────
c.executemany("INSERT OR IGNORE INTO DeliveryCompanies(CompanyId,CompanyName,CommissionPercent,IsActive) VALUES(?,?,?,1)", [
    (1,'Own Rider',0), (2,'Foodpanda',10), (3,'Cheetay',8),
])

# ── AREAS & TABLES ─────────────────────────────────────────────────────────────
c.executemany("INSERT OR IGNORE INTO Areas(AreaId,AreaName,IsActive) VALUES(?,?,1)", [
    (1,'Main Hall'),(2,'Outdoor'),
])
c.executemany("INSERT OR IGNORE INTO Tables(TableId,TableName,AreaId,Capacity,TableStatus,IsActive) VALUES(?,?,?,?,'Available',1)", [
    (1,'Table 1',1,4),(2,'Table 2',1,4),(3,'Table 3',1,4),(4,'Table 4',1,4),
    (5,'Table 5',1,6),(6,'Table 6',1,6),(7,'Table 7',1,2),(8,'Table 8',1,2),
    (9,'Outdoor 1',2,4),(10,'Outdoor 2',2,4),
])

# ── WAITERS ────────────────────────────────────────────────────────────────────
c.executemany("INSERT OR IGNORE INTO Waiters(WaiterId,WaiterName,Phone,IsActive) VALUES(?,?,?,1)", [
    (1,'Ahmed Khan','0300-1111111'),
    (2,'Bilal Ahmed','0300-2222222'),
    (3,'Fahad Ali','0300-3333333'),
    (4,'Hassan Raza','0300-4444444'),
    (5,'Imran Shah','0300-5555555'),
])

# ── CATEGORIES ─────────────────────────────────────────────────────────────────
c.executemany("INSERT OR IGNORE INTO Categories(CategoryId,CategoryName,ColorCode,DisplayOrder,IsActive) VALUES(?,?,?,?,1)", [
    (1,'Burgers','#FF6B35',1),(2,'Pizzas','#E63946',2),(3,'Drinks','#2196F3',3),
    (4,'Sides','#4CAF50',4),(5,'Desserts','#9C27B0',5),(6,'Sandwiches','#FF9800',6),
])

# ── PRODUCTS (30 items) ────────────────────────────────────────────────────────
c.executemany("""INSERT OR IGNORE INTO Products
    (ProductId,ProductName,CategoryId,SalePrice,PurchasePrice,
     IsRecipeBased,DisplayOrder,IsAvailable,IsStockManaged,IsActive,Unit)
    VALUES(?,?,?,?,?,?,?,1,0,1,'Pcs')""", [
    # Burgers
    (1,'Classic Beef Burger',1,350,150,1,1),
    (2,'Double Smash Burger',1,550,220,1,2),
    (3,'Crispy Chicken Burger',1,380,160,1,3),
    (4,'BBQ Mushroom Burger',1,420,180,1,4),
    (5,'Zinger Burger',1,400,170,1,5),
    (6,'Veggie Burger',1,300,120,1,6),
    (7,'Cheese Burst Burger',1,480,200,1,7),
    (8,'Spicy Jalapeno Burger',1,430,185,1,8),
    # Pizzas
    (9,'Margherita Pizza',2,650,280,1,1),
    (10,'BBQ Chicken Pizza',2,850,360,1,2),
    (11,'Beef Pepperoni Pizza',2,900,380,1,3),
    (12,'Veggie Supreme Pizza',2,750,310,1,4),
    (13,'Tikka Pizza',2,880,370,1,5),
    (14,'Four Cheese Pizza',2,950,400,1,6),
    # Drinks
    (15,'Coca-Cola 500ml',3,80,35,0,1),
    (16,'Mineral Water',3,50,20,0,2),
    (17,'Fresh Lemonade',3,150,40,1,3),
    (18,'Mango Shake',3,200,60,1,4),
    (19,'Chocolate Milkshake',3,220,70,1,5),
    # Sides
    (20,'French Fries',4,180,60,1,1),
    (21,'Onion Rings',4,200,70,1,2),
    (22,'Coleslaw',4,120,40,1,3),
    (23,'Garlic Bread',4,150,50,1,4),
    (24,'Loaded Fries',4,280,90,1,5),
    # Desserts
    (25,'Chocolate Brownie',5,250,90,1,1),
    (26,'Vanilla Ice Cream',5,180,60,0,2),
    (27,'Oreo Shake',5,280,100,1,3),
    # Sandwiches
    (28,'Club Sandwich',6,320,130,1,1),
    (29,'Chicken Mayo Sandwich',6,280,110,1,2),
    (30,'Grilled Cheese Sandwich',6,220,80,1,3),
])

# ── RAW MATERIALS (20 items) ───────────────────────────────────────────────────
c.executemany("""INSERT OR IGNORE INTO RawMaterials
    (MaterialId,MaterialName,Unit,CurrentStock,MinStockLevel,CostPerUnit,IsActive)
    VALUES(?,?,?,?,?,?,1)""", [
    (1,'Beef Patty','Pcs',100,10,120),
    (2,'Burger Bun','Pcs',200,20,15),
    (3,'Lettuce','kg',5,1,80),
    (4,'Tomato','kg',10,2,60),
    (5,'Cheddar Cheese','kg',3,0.5,800),
    (6,'Chicken Fillet','Pcs',80,10,90),
    (7,'Pizza Dough','Pcs',50,5,40),
    (8,'Mozzarella Cheese','kg',4,0.5,900),
    (9,'Tomato Sauce','kg',10,1,50),
    (10,'Cooking Oil','Liter',10,1,200),
    (11,'Potatoes','kg',20,3,40),
    (12,'Milk','Liter',20,2,90),
    (13,'Flour','kg',15,2,50),
    (14,'Eggs','Pcs',120,12,15),
    (15,'Cocoa Powder','kg',2,0.2,500),
    (16,'Lemon','Pcs',50,5,10),
    (17,'Mango Pulp','kg',5,1,150),
    (18,'Sugar','kg',10,1,80),
    (19,'Bread Loaf','Pcs',30,3,60),
    (20,'Mayonnaise','kg',3,0.3,250),
])

# ── RECIPES ────────────────────────────────────────────────────────────────────
c.executemany("INSERT OR IGNORE INTO Recipes(RecipeId,ProductId,RecipeName,IsActive) VALUES(?,?,?,1)", [
    (1,1,'Classic Beef Burger Recipe'),
    (2,2,'Double Smash Burger Recipe'),
    (3,3,'Crispy Chicken Burger Recipe'),
    (4,9,'Margherita Pizza Recipe'),
    (5,10,'BBQ Chicken Pizza Recipe'),
    (6,17,'Fresh Lemonade Recipe'),
    (7,18,'Mango Shake Recipe'),
    (8,20,'French Fries Recipe'),
    (9,25,'Chocolate Brownie Recipe'),
    (10,28,'Club Sandwich Recipe'),
])

c.executemany("INSERT OR IGNORE INTO RecipeItems(RecipeItemId,RecipeId,ProductId,QuantityUsed,Unit) VALUES(?,?,?,?,?)", [
    # Classic Beef Burger
    (1,1,1,1,'Pcs'),(2,1,2,1,'Pcs'),(3,1,3,0.05,'kg'),(4,1,4,0.05,'kg'),(5,1,5,0.03,'kg'),
    # Double Smash Burger
    (6,2,1,2,'Pcs'),(7,2,2,1,'Pcs'),(8,2,5,0.05,'kg'),(9,2,3,0.05,'kg'),(10,2,4,0.05,'kg'),
    # Crispy Chicken Burger
    (11,3,6,1,'Pcs'),(12,3,2,1,'Pcs'),(13,3,3,0.05,'kg'),(14,3,20,0.03,'kg'),
    # Margherita Pizza
    (15,4,7,1,'Pcs'),(16,4,9,0.1,'kg'),(17,4,8,0.2,'kg'),(18,4,3,0.05,'kg'),
    # BBQ Chicken Pizza
    (19,5,7,1,'Pcs'),(20,5,6,1,'Pcs'),(21,5,8,0.25,'kg'),(22,5,9,0.1,'kg'),
    # Fresh Lemonade
    (23,6,16,2,'Pcs'),(24,6,18,0.03,'kg'),(25,6,12,0.2,'Liter'),
    # Mango Shake
    (26,7,17,0.15,'kg'),(27,7,12,0.3,'Liter'),(28,7,18,0.03,'kg'),
    # French Fries
    (29,8,11,0.15,'kg'),(30,8,10,0.05,'Liter'),
    # Chocolate Brownie
    (31,9,15,0.05,'kg'),(32,9,13,0.1,'kg'),(33,9,14,2,'Pcs'),(34,9,18,0.08,'kg'),
    # Club Sandwich
    (35,10,19,2,'Pcs'),(36,10,6,1,'Pcs'),(37,10,3,0.05,'kg'),(38,10,4,0.05,'kg'),(39,10,20,0.03,'kg'),
])

# ── DEALS ──────────────────────────────────────────────────────────────────────
c.executemany("INSERT OR IGNORE INTO Deals(DealId,DealName,Description,DealPrice,DiscountPercent,IsActive) VALUES(?,?,?,?,?,1)", [
    (1,'Burger Combo','Classic Burger + Fries + Drink',750,0),
    (2,'Family Pizza Deal','2 Pizzas at special price',1500,0),
    (3,'Student Meal','Sandwich + Drink',350,0),
])
c.executemany("INSERT OR IGNORE INTO DealItems(ItemId,DealId,ProductId,SizeId,Quantity,IsOptional) VALUES(?,?,?,NULL,?,0)", [
    (1,1,1,1),(2,1,20,1),(3,1,15,1),   # Burger Combo
    (4,2,10,1),(5,2,9,1),               # Family Pizza Deal
    (6,3,28,1),(7,3,15,1),              # Student Meal
])

# ── EMPLOYEES ──────────────────────────────────────────────────────────────────
c.executemany("""INSERT OR IGNORE INTO Employees
    (EmployeeId,EmployeeName,Phone,EmployeeRole,MonthlySalary,IsActive,BranchId)
    VALUES(?,?,?,?,?,1,1)""", [
    (1,'Ahmed Khan','0300-1111111','Waiter',25000),
    (2,'Bilal Ahmed','0300-2222222','Waiter',25000),
    (3,'Fahad Ali','0300-3333333','Waiter',25000),
    (4,'Hassan Raza','0300-4444444','Waiter',25000),
    (5,'Imran Shah','0300-5555555','Waiter',25000),
    (6,'Zaid Cook','0300-6666666','Chef',35000),
    (7,'Usman Helper','0300-7777777','Kitchen',22000),
])

# ── SUPPLIERS ──────────────────────────────────────────────────────────────────
c.executemany("INSERT OR IGNORE INTO Suppliers(SupplierId,SupplierName,ContactPerson,Phone,IsActive) VALUES(?,?,?,?,1)", [
    (1,'Metro Cash & Carry','Mr. Tariq','0300-9999999'),
    (2,'Fresh Farms','Mr. Nadeem','0321-8888888'),
])

conn.commit()
conn.close()
print(f"SUCCESS: {db_path}")
print(f"File size: {os.path.getsize(db_path):,} bytes")
