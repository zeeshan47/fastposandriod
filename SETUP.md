# FastPOS Android — Setup Guide

## Prerequisites

1. **Android Studio** Ladybug (2024.2.1) or newer
2. **Android device/emulator** running Android 8.0+ (API 26+)
3. **SQL Server** with FASTPOSDB already set up (from the WPF project)

---

## SQL Server Configuration (REQUIRED before first run)

The Android app connects **directly** to SQL Server over the local network using JDBC.
You must enable SQL Server Authentication:

### Step 1 – Enable SQL Server Authentication
1. Open **SQL Server Management Studio**
2. Right-click the server → **Properties → Security**
3. Set **Server Authentication** to **SQL Server and Windows Authentication mode**
4. Restart SQL Server service

### Step 2 – Enable the SA account (or create a dedicated user)
```sql
-- Enable SA account
ALTER LOGIN sa ENABLE;
ALTER LOGIN sa WITH PASSWORD = 'YourStrongPassword123!';

-- OR create a dedicated app login
CREATE LOGIN fastpos_app WITH PASSWORD = 'YourStrongPassword123!';
USE FASTPOSDB;
CREATE USER fastpos_app FOR LOGIN fastpos_app;
ALTER ROLE db_datareader ADD MEMBER fastpos_app;
ALTER ROLE db_datawriter ADD MEMBER fastpos_app;
GRANT EXECUTE TO fastpos_app;
```

### Step 3 – Enable TCP/IP
1. Open **SQL Server Configuration Manager**
2. SQL Server Network Configuration → Protocols for MSSQLSERVER (or SQLEXPRESS)
3. Enable **TCP/IP**
4. Under TCP/IP → IP Addresses tab → IPAll → set **TCP Port** = `1433`
5. Restart SQL Server service

### Step 4 – Firewall
Allow inbound TCP port 1433 on the Windows Firewall of the PC running SQL Server.

---

## Opening in Android Studio

1. Open Android Studio
2. **File → Open** → select the `FASTPOS-Android` folder
3. Wait for Gradle sync to complete (first time downloads ~500MB)
4. Connect your Android device via USB or start an emulator
5. Click **Run ▶**

---

## First Launch

On first launch the app shows the **Database Connection Setup** screen:

| Field         | Example value            |
|---------------|--------------------------|
| Server IP     | `192.168.1.10`           |
| Port          | `1433`                   |
| Instance Name | `SQLEXPRESS` (or blank)  |
| Database Name | `FASTPOSDB`              |
| Username      | `sa` or `fastpos_app`    |
| Password      | (your SQL password)      |

Tap **Test & Connect** — if successful you'll be taken to Login.

Default admin credentials: `admin` / `admin123`

---

## Architecture

```
Android App
  ├── Jetpack Compose UI  (Material 3, dark theme)
  ├── Hilt DI
  ├── MVVM (ViewModel + StateFlow)
  └── JTDS JDBC Driver → SQL Server (local network TCP)
```

## Phase 1 Features (implemented)
- [x] Database connection setup
- [x] Login / authentication (SHA-256 password matching WPF)
- [x] Dashboard with KPIs (today's sales, orders, avg value, pending)
- [x] POS terminal — product grid, category filter, sizes, modifiers, cart
- [x] Order placement (DineIn/Takeaway/Delivery)
- [x] Payment screen (Cash, Card, JazzCash, EasyPaisa, Bank, Voucher)
- [x] Kitchen Display System (KDS) with auto-refresh every 30 sec
- [x] Orders list (Active / All)
- [x] Responsive layout (tablet: side-by-side, phone: tabbed)

## Phase 2 Features (implemented)
- [x] Product/Category management
- [x] Inventory & Stock management (add/remove/set stock levels)
- [x] Reports & Analytics (Today/Yesterday/Week/Month, payment breakdown, top products)
- [x] Customer management (CRUD, search)
- [x] Employee management (add/edit/reset password/activate)
- [x] Shift management (open/close shift, add expenses)
- [x] Settings screen (company settings, DB stats, connection reset)
- [x] Tables (status management: Available/Occupied/Reserved/Cleaning)

## Phase 3 Features (implemented)
- [x] Customer selection at POS (search by name/phone, attach to order)
- [x] Loyalty points — earn 1 pt per 10 Rs. spent; redeem up to 25% of order total
- [x] Bluetooth receipt printing — select paired ESC/POS printer, auto-saves preference

## Phase 4 (complete as of 2026-05-17)
- [x] Multi-kitchen printer routing — products carry `PrinterName`; on order placement, items grouped by printer name and sent to each kitchen's LAN printer (TCP:9100 / ESC/POS) in parallel
- [x] Kitchen Printers config in Settings — Add/Edit/Delete name→IP mappings, Test Print button, shows detected printer names from DB products

## Phase 5 (complete as of 2026-05-17)
- [x] Receipt Printer management in Settings — select/change/test/clear saved BT printer without opening a payment
- [x] Attendance tracking — mark daily employee status (Present/Late/Leave/Absent) with date picker, live summary chips
- [x] Payroll generation — monthly payroll from attendance data, employee salary management, Mark as Paid

### Phase 5 SQL Setup (run once in SSMS)

The Attendance and Payroll screens auto-create their tables on first launch using `IF NOT EXISTS`. However, you can run these manually to verify:

```sql
-- Attendance records table
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'AttendanceRecords')
CREATE TABLE AttendanceRecords (
    AttendanceId INT IDENTITY(1,1) PRIMARY KEY,
    UserId INT NOT NULL,
    AttendanceDate DATE NOT NULL,
    Status VARCHAR(20) NOT NULL DEFAULT 'Present',  -- Present/Late/Leave/Absent
    Notes VARCHAR(200) NULL,
    CreatedAt DATETIME DEFAULT GETDATE(),
    CONSTRAINT UQ_Attendance_UserDate UNIQUE(UserId, AttendanceDate)
);

-- Add BasicSalary column to Users if missing
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Users' AND COLUMN_NAME = 'BasicSalary')
    ALTER TABLE Users ADD BasicSalary DECIMAL(18,2) NOT NULL DEFAULT 0;

-- Payroll records table
IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'PayrollRecords')
CREATE TABLE PayrollRecords (
    PayrollId     INT IDENTITY(1,1) PRIMARY KEY,
    UserId        INT NOT NULL,
    PayMonth      INT NOT NULL,
    PayYear       INT NOT NULL,
    WorkingDays   INT DEFAULT 0,
    PresentDays   INT DEFAULT 0,
    LeaveDays     INT DEFAULT 0,
    BasicSalary   DECIMAL(18,2) DEFAULT 0,
    Allowances    DECIMAL(18,2) DEFAULT 0,
    Deductions    DECIMAL(18,2) DEFAULT 0,
    NetSalary     DECIMAL(18,2) DEFAULT 0,
    PaymentStatus VARCHAR(20) DEFAULT 'Pending',
    PaidDate      DATETIME NULL,
    Notes         VARCHAR(200) NULL,
    CreatedBy     INT NULL,
    CreatedAt     DATETIME DEFAULT GETDATE(),
    CONSTRAINT UQ_Payroll_UserMonthYear UNIQUE(UserId, PayMonth, PayYear)
);
```

## Phase 6 (next steps)
- [ ] Inventory: raw material tracking & recipe cost
- [ ] Offline mode / local SQLite fallback
