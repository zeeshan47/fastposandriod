package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs idempotent DDL migrations against the SQL Server database on first login.
 * Each migration uses IF NOT EXISTS guards so it is safe to run multiple times.
 * Migrations are skipped in local-SQLite and peer-client modes — LocalSchemaHelper
 * owns the offline schema via its DB_VERSION / onCreate / onUpgrade lifecycle.
 */
@Singleton
class DbMigrationRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun runPendingMigrations() {
        if (db.isLocal() || db.isPeerClient()) return
        MIGRATIONS.forEach { sql ->
            try { db.execute(sql) } catch (_: Exception) {}
        }
        // Accounting module — ChartOfAccounts + AccountLedger (run separately due to BEGIN...END block)
        runCatching {
            db.execute(
                """IF NOT EXISTS (
                       SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'ChartOfAccounts'
                   ) BEGIN
                       CREATE TABLE ChartOfAccounts (
                           AccountId    INT           IDENTITY(1,1) PRIMARY KEY,
                           AccountCode  NVARCHAR(10)  NOT NULL CONSTRAINT UQ_ChartOfAccounts_Code UNIQUE,
                           AccountName  NVARCHAR(100) NOT NULL,
                           AccountType  NVARCHAR(20)  NOT NULL,
                           IsActive     BIT           NOT NULL DEFAULT 1
                       );
                       INSERT INTO ChartOfAccounts (AccountCode, AccountName, AccountType) VALUES
                           ('1000', 'Cash',                          'Asset'),
                           ('1100', 'Accounts Receivable – Delivery','Asset'),
                           ('2000', 'Accounts Payable – Suppliers',  'Liability'),
                           ('4000', 'Sales Revenue',                 'Income'),
                           ('5000', 'Cost of Goods Sold',            'Expense'),
                           ('5100', 'Operational Expenses',          'Expense'),
                           ('5200', 'Salary & Wages',                'Expense'),
                           ('5300', 'Purchase Payments',             'Expense');
                   END"""
            )
        }
        runCatching {
            db.execute(
                """IF NOT EXISTS (
                       SELECT 1 FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'AccountLedger'
                   ) CREATE TABLE AccountLedger (
                       LedgerId      INT           IDENTITY(1,1) PRIMARY KEY,
                       AccountId     INT           NOT NULL,
                       EntryDate     DATETIME      NOT NULL DEFAULT GETDATE(),
                       Debit         DECIMAL(18,2) NOT NULL DEFAULT 0,
                       Credit        DECIMAL(18,2) NOT NULL DEFAULT 0,
                       ReferenceType NVARCHAR(30)  NOT NULL,
                       ReferenceId   INT           NULL,
                       Narration     NVARCHAR(200) NOT NULL DEFAULT '',
                       CreatedBy     INT           NULL,
                       CreatedAt     DATETIME      NOT NULL DEFAULT GETDATE(),
                       CONSTRAINT FK_AccountLedger_Accounts FOREIGN KEY (AccountId) REFERENCES ChartOfAccounts(AccountId)
                   )"""
            )
        }
    }

    companion object {
        private val MIGRATIONS = listOf(

            // WPF Waiters.AreaId — missing on databases created by the Android app
            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_NAME = 'Waiters' AND COLUMN_NAME = 'AreaId'
               ) ALTER TABLE Waiters ADD AreaId INT NULL""",

            // WPF DiningAreas columns — missing on databases created before Areas was expanded
            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_NAME = 'Areas' AND COLUMN_NAME = 'BranchId'
               ) ALTER TABLE Areas ADD BranchId INT NOT NULL DEFAULT 1""",

            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_NAME = 'Areas' AND COLUMN_NAME = 'DisplayOrder'
               ) ALTER TABLE Areas ADD DisplayOrder INT NOT NULL DEFAULT 0""",

            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_NAME = 'Areas' AND COLUMN_NAME = 'CreatedAt'
               ) ALTER TABLE Areas ADD CreatedAt DATETIME NULL""",

            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_NAME = 'Areas' AND COLUMN_NAME = 'CreatedBy'
               ) ALTER TABLE Areas ADD CreatedBy INT NULL""",

            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_NAME = 'Areas' AND COLUMN_NAME = 'UpdatedAt'
               ) ALTER TABLE Areas ADD UpdatedAt DATETIME NULL""",

            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_NAME = 'Areas' AND COLUMN_NAME = 'UpdatedBy'
               ) ALTER TABLE Areas ADD UpdatedBy INT NULL""",

            // WPF HeldOrders table — records each hold action for history and reports
            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.TABLES
                   WHERE TABLE_NAME = 'HeldOrders'
               )
               CREATE TABLE HeldOrders (
                   OrderId  INT          NOT NULL PRIMARY KEY,
                   HeldAt   DATETIME     NOT NULL DEFAULT GETDATE(),
                   HeldBy   INT          NOT NULL,
                   Notes    NVARCHAR(500) NULL,
                   CONSTRAINT FK_HeldOrders_Orders FOREIGN KEY (OrderId) REFERENCES Orders(OrderId)
               )""",

            // Customer Wallet — one row per customer, running balance
            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.TABLES
                   WHERE TABLE_NAME = 'CustomerWallet'
               )
               CREATE TABLE CustomerWallet (
                   WalletId    INT IDENTITY(1,1) PRIMARY KEY,
                   CustomerId  INT           NOT NULL UNIQUE,
                   Balance     DECIMAL(12,2) NOT NULL DEFAULT 0,
                   LastUpdated DATETIME      NOT NULL DEFAULT GETDATE()
               )""",

            // Wallet transaction ledger — matches WPF schema column names
            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.TABLES
                   WHERE TABLE_NAME = 'WalletTransactions'
               )
               CREATE TABLE WalletTransactions (
                   TransId    INT IDENTITY(1,1) PRIMARY KEY,
                   CustomerId INT           NOT NULL,
                   OrderId    INT           NULL,
                   TransType  NVARCHAR(20)  NOT NULL,
                   Amount     DECIMAL(12,2) NOT NULL DEFAULT 0,
                   TransDate  DATETIME      NOT NULL DEFAULT GETDATE(),
                   Notes      NVARCHAR(500) NULL,
                   CreatedBy  INT           NULL
               )""",

            // CashTransactions.Reason — add if missing
            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_NAME = 'CashTransactions' AND COLUMN_NAME = 'Reason'
               ) ALTER TABLE CashTransactions ADD Reason NVARCHAR(200) NOT NULL DEFAULT ''""",

            // ProductSizes.CostPrice — cost price per size for accurate margin calculation
            """IF NOT EXISTS (
                   SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
                   WHERE TABLE_NAME = 'ProductSizes' AND COLUMN_NAME = 'CostPrice'
               ) ALTER TABLE ProductSizes ADD CostPrice DECIMAL(18,2) NOT NULL DEFAULT 0"""
        )
    }
}
