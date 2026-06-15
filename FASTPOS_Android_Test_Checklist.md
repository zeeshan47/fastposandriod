# FASTPOS Android — Complete Testing Checklist
### Version: Phase 76 | Date: 2026-05-28
### Tester: _____________ | Device: _____________ | Android Version: _______

---

## HOW TO USE THIS CHECKLIST
- Run every section **twice**: once in **Standalone (SQLite)** mode, once in **SQL Server** mode.
- Each checkbox has a mode tag: **[S]** = Standalone only · **[Q]** = SQL Server only · **[B]** = Both modes.
- Fill in suggested values exactly as written. Circle **PASS** or **FAIL** after each step.
- Leave the **Notes** line blank if pass. Write the error message if fail.

---

## PRE-TEST SETUP

### Standalone Mode Setup
- [ ] [S] Open app → tap **"Use Offline / Standalone"** on DB setup screen
- [ ] [S] Confirm app reaches Login screen without error · **PASS / FAIL**
- [ ] [S] Default user: **admin** / **admin123** — confirm login succeeds · **PASS / FAIL**

### SQL Server Mode Setup
- [ ] [Q] Open app → tap **"Connect to SQL Server"**
- [ ] [Q] Enter: Server = `YOUR_SERVER_IP`, DB = `FASTPOSDB`, User = `sa`, Pass = `your_password`
- [ ] [Q] Tap **Test Connection** — confirm green tick · **PASS / FAIL**
- [ ] [Q] Login with existing user (e.g. admin) · **PASS / FAIL**
- [ ] [Q] Confirm migrations run silently (no error toast on first login) · **PASS / FAIL**

---

## MODULE 1 — SETTINGS

### 1.1 Company Settings
- [ ] [B] Navigate to **Settings → Company**
- [ ] [B] Change **Company Name** to `Test Burger House` → Save · **PASS / FAIL**
- [ ] [B] Change **Currency Symbol** to `Rs.` → Save · **PASS / FAIL**
- [ ] [B] Change **Tax %** to `17` → Save · **PASS / FAIL**
- [ ] [B] Change **Service Charge %** to `5` → Save · **PASS / FAIL**
- [ ] [B] Change **Receipt Footer** to `Thank you! Visit again.` → Save · **PASS / FAIL**
- [ ] [B] Change **Token Prefix** to `T` → Save · **PASS / FAIL**
- [ ] [B] Close app and reopen → verify all values persisted · **PASS / FAIL**

Notes: _______________________________________________________________

### 1.2 Logo Upload
- [ ] [B] In Settings tap **Pick Logo** → select any image from gallery · **PASS / FAIL**
- [ ] [B] Verify logo preview appears in Settings screen · **PASS / FAIL**
- [ ] [B] Re-login → navigate to POS → place a test order → print receipt → verify logo appears · **PASS / FAIL**

Notes: _______________________________________________________________

### 1.3 Printer Setup
- [ ] [B] Set **Printer Type** to `Bluetooth`
- [ ] [B] Set **Paper Width** to `32`
- [ ] [B] Tap **Scan Printers** → pair and select a printer · **PASS / FAIL**
- [ ] [B] Tap **Test Print** → receipt prints without error · **PASS / FAIL**

Notes: _______________________________________________________________

### 1.4 FBR Settings
- [ ] [Q] Toggle **FBR Enabled** ON
- [ ] [Q] Enter **NTN** = `1234567-8`, **Business Name** = `Test Burger House`, **Province** = `Punjab`
- [ ] [Q] Save → re-enter Settings → confirm values saved · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 2 — USER MANAGEMENT

### 2.1 Add Users
- [ ] [B] Go to **User Management** → tap **Add User**
- [ ] [B] Fill: Username = `cashier1`, Full Name = `Ali Ahmed`, Role = `Cashier`, Password = `1234`
- [ ] [B] Tap Save → user appears in list · **PASS / FAIL**
- [ ] [B] Add another: Username = `manager1`, Full Name = `Sara Khan`, Role = `Manager`, Password = `5678`
- [ ] [B] Tap Save → user appears in list · **PASS / FAIL**

### 2.2 Edit User
- [ ] [B] Tap `cashier1` → Edit → change Full Name to `Ali Ahmed Khan` → Save · **PASS / FAIL**
- [ ] [B] Verify updated name shows in list · **PASS / FAIL**

### 2.3 Login with New User
- [ ] [B] Logout → Login as `cashier1` / `1234` → confirm access · **PASS / FAIL**
- [ ] [B] Login back as `admin` · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 3 — BRANCH

### 3.1 Add Branch
- [ ] [B] Go to **Settings → Branches** → tap **Add Branch**
- [ ] [B] Fill: Branch Name = `Main Branch`, Address = `Shop 1, Main Street`, Phone = `03001234567`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 3.2 Edit Branch
- [ ] [B] Tap **Main Branch** → Edit → change Phone to `03009876543` → Save · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 4 — TAX

### 4.1 Add Tax Rate
- [ ] [B] Go to **Tax** → Add Tax
- [ ] [B] Fill: Tax Name = `GST`, Rate = `17`, Type = `Percentage`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 4.2 Edit Tax
- [ ] [B] Tap `GST` → Edit → change Rate to `16` → Save → verify · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 5 — TABLES & AREAS

### 5.1 Add Area
- [ ] [B] Go to **Tables → Areas** → Add Area
- [ ] [B] Fill: Area Name = `Main Hall`, Display Order = `1`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 5.2 Add Tables
- [ ] [B] Add Table: Name = `T1`, Capacity = `4`, Area = `Main Hall` → Save · **PASS / FAIL**
- [ ] [B] Add Table: Name = `T2`, Capacity = `2`, Area = `Main Hall` → Save · **PASS / FAIL**
- [ ] [B] Add Table: Name = `T3`, Capacity = `6`, Area = `Main Hall` → Save · **PASS / FAIL**

### 5.3 Edit Table
- [ ] [B] Tap `T1` → Edit → change Capacity to `6` → Save → verify · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 6 — WAITERS

### 6.1 Add Waiter
- [ ] [B] Go to **Waiters** → Add Waiter
- [ ] [B] Fill: Name = `Kamran`, Phone = `03111234567`, Area = `Main Hall`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 6.2 Edit Waiter
- [ ] [B] Tap `Kamran` → Edit → change Phone to `03119999999` → Save → verify · **PASS / FAIL**

### 6.3 Deactivate Waiter
- [ ] [B] Tap `Kamran` → toggle Active = OFF → Save → verify status in list · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 7 — PRODUCTS

### 7.1 Add Category
- [ ] [B] Go to **Products → Categories** → Add
- [ ] [B] Fill: Name = `Burgers`, Display Order = `1`
- [ ] [B] Save → appears · **PASS / FAIL**
- [ ] [B] Add second: Name = `Drinks`, Display Order = `2` → Save · **PASS / FAIL**
- [ ] [B] Add third: Name = `Sides`, Display Order = `3` → Save · **PASS / FAIL**

### 7.2 Add Products
- [ ] [B] Add Product 1: Name = `Zinger Burger`, Category = `Burgers`, Price = `350`, Tax = `17%` → Save · **PASS / FAIL**
- [ ] [B] Add Product 2: Name = `Chicken Burger`, Category = `Burgers`, Price = `280` → Save · **PASS / FAIL**
- [ ] [B] Add Product 3: Name = `Pepsi`, Category = `Drinks`, Price = `80` → Save · **PASS / FAIL**
- [ ] [B] Add Product 4: Name = `Fries`, Category = `Sides`, Price = `120` → Save · **PASS / FAIL**
- [ ] [B] Add Product 5 (with sizes): Name = `Pizza`, Type = `Pizza`
  - Size: `Small` = `450`
  - Size: `Medium` = `650`
  - Size: `Large` = `900`
  - Save · **PASS / FAIL**

### 7.3 Edit Product
- [ ] [B] Tap `Zinger Burger` → Edit → change Price to `380` → Save → verify · **PASS / FAIL**

### 7.4 Modifiers
- [ ] [B] Go to **Modifiers** → Add Group: Name = `Extra Toppings`, Required = NO
- [ ] [B] Add items: `Extra Cheese` = `30`, `Jalapenos` = `20`, `Extra Sauce` = `15` · **PASS / FAIL**
- [ ] [B] Go to Products → Edit `Zinger Burger` → assign modifier group `Extra Toppings`
- [ ] [B] Save → reopen product to edit → confirm `Extra Toppings` is pre-selected · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 8 — DEALS

### 8.1 Add Deal
- [ ] [B] Go to **Deals** → Add Deal
- [ ] [B] Fill: Name = `Burger Meal Deal`, Price = `450`, Discount = `10%`
- [ ] [B] Add items: `Zinger Burger` qty=1, `Pepsi` qty=1, `Fries` qty=1
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 8.2 Edit Deal
- [ ] [B] Tap `Burger Meal Deal` → Edit → change Price to `480` → Save → verify · **PASS / FAIL**

### 8.3 Add Deal to Cart (POS)
- [ ] [B] Go to POS → tap Deals tab → tap `Burger Meal Deal`
- [ ] [B] Verify deal items appear in cart without "invalid itemid" error · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 9 — VOUCHERS

### 9.1 Add Voucher
- [ ] [B] Go to **Vouchers** → Add
- [ ] [B] Fill: Code = `SAVE50`, Discount = `50`, Type = `Fixed`, Valid From = today
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 9.2 Use Voucher in POS
- [ ] [B] Go to POS → add `Zinger Burger` to cart → apply voucher code `SAVE50`
- [ ] [B] Verify discount of Rs.50 deducted from total · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 10 — CUSTOMERS

### 10.1 Add Customer
- [ ] [B] Go to **Customers** → Add
- [ ] [B] Fill: Name = `Ahmed Raza`, Phone = `03021234567`, Email = `ahmed@test.com`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 10.2 Edit Customer
- [ ] [B] Tap `Ahmed Raza` → Edit → change Email to `ahmed.raza@test.com` → Save → verify · **PASS / FAIL**

### 10.3 Loyalty Points
- [ ] [B] Place a paid order for `Ahmed Raza` (see POS section)
- [ ] [B] Return to Customers → verify Loyalty Points increased · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 11 — SHIFT

### 11.1 Open Shift
- [ ] [B] Go to **Shift** → Open New Shift
- [ ] [B] Fill: Opening Cash = `5000`
- [ ] [B] Tap Open → confirm shift shows as Active · **PASS / FAIL**

### 11.2 Z-Report / Close Shift (do AFTER completing POS transactions)
- [ ] [B] Go to **Shift** → tap Close Shift
- [ ] [B] Fill: Closing Cash = `6500`, Notes = `Test shift close`
- [ ] [B] Confirm summary shows sales, cash in/out · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 12 — POS (POINT OF SALE)

> Open a Shift before this section. Ensure Tables and Products are created.

### 12.1 Dine-In Order
- [ ] [B] Go to **POS** → Order Type = `Dine-In` → select Table `T2`
- [ ] [B] Select Waiter = `Kamran`
- [ ] [B] Add: `Zinger Burger` x1, `Pepsi` x2, `Fries` x1
- [ ] [B] Verify subtotal = `730` (350+80+80+120+100 tax) — check calculation · **PASS / FAIL**
- [ ] [B] Add modifier `Extra Cheese` to Zinger Burger → verify line total updates · **PASS / FAIL**
- [ ] [B] Tap **Send to Kitchen** → KOT prints / kitchen screen updates · **PASS / FAIL**
- [ ] [B] Tap **Pre-Bill** → pre-bill prints with logo (if configured) and QR code · **PASS / FAIL**

### 12.2 Takeaway Order
- [ ] [B] New order → Type = `Takeaway`
- [ ] [B] Add: `Chicken Burger` x2 → Token auto-generated → **PASS / FAIL**
- [ ] [B] Tap **Place Order** · **PASS / FAIL**

### 12.3 Delivery Order
- [ ] [B] New order → Type = `Delivery`
- [ ] [B] Fill: Delivery Name = `Bilal Khan`, Phone = `03031234567`, Address = `House 5, Block A`
- [ ] [B] Select Delivery Company (from Delivery module — create first if needed)
- [ ] [B] Add: `Zinger Burger` x1, `Pepsi` x1
- [ ] [B] Verify Commission Amount = `SubTotal × CompanyCommission%` (NOT grand total) · **PASS / FAIL**
- [ ] [B] Place Order · **PASS / FAIL**

### 12.4 Hold Order
- [ ] [B] New order → add `Pizza (Medium)` x1 → tap **Hold**
- [ ] [B] Verify order appears in Held orders list · **PASS / FAIL**
- [ ] [B] Resume held order → cart restores correctly · **PASS / FAIL**

### 12.5 Token Numbers
- [ ] [B] Place 3 orders in quick succession (test duplicate token issue)
- [ ] [B] Verify each gets unique token: T001, T002, T003 · **PASS / FAIL**

### 12.6 Apply Discount
- [ ] [B] New order → add `Fries` x2 → apply 10% discount
- [ ] [B] Verify discount row shows in cart → totals update · **PASS / FAIL**

### 12.7 Voucher in POS
- [ ] [B] New order → add any item → apply code `SAVE50` → verify Rs.50 deducted · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 13 — PAYMENT

### 13.1 Full Cash Payment
- [ ] [B] Open the Dine-In order from 12.1 → tap **Pay**
- [ ] [B] Payment Method = `Cash`, Amount = full grand total
- [ ] [B] Tap **Complete** → receipt prints (with logo + QR) · **PASS / FAIL**
- [ ] [B] Verify order disappears from Active Orders · **PASS / FAIL**
- [ ] [B] Verify table T2 status returns to Available · **PASS / FAIL**

### 13.2 Split Payment
- [ ] [B] New order → add `Zinger Burger` + `Pepsi` → go to Pay
- [ ] [B] Add Cash = `200`, Card = remaining → Complete · **PASS / FAIL**

### 13.3 Partial Payment
- [ ] [B] New order → add `Pizza (Large)` → go to Pay
- [ ] [B] Pay partial Cash = `400` (less than total) → confirm balance shows · **PASS / FAIL**

### 13.4 Wallet Payment
- [ ] [B] Top up wallet for `Ahmed Raza` with Rs.1000 (see Customer Wallet section)
- [ ] [B] Place order → in Payment screen select customer → pay via Wallet
- [ ] [B] Verify wallet balance reduces · **PASS / FAIL**

### 13.5 Receipt Verification
- [ ] [B] After payment — on printed receipt verify:
  - Company Name = `Test Burger House` · **PASS / FAIL**
  - Logo appears (if uploaded) · **PASS / FAIL**
  - QR code appears · **PASS / FAIL**
  - Order No., Date, Items, Totals correct · **PASS / FAIL**
  - Footer = `Thank you! Visit again.` · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 14 — ORDERS MANAGEMENT

### 14.1 View Active Orders
- [ ] [B] Go to **Orders** → Active tab
- [ ] [B] Verify only UNPAID orders appear (no Paid orders in list) · **PASS / FAIL**
- [ ] [B] Paid orders from Module 13 must NOT appear here · **PASS / FAIL**

### 14.2 Reprint Receipt
- [ ] [B] Find a completed order → tap → **Reprint** → receipt prints · **PASS / FAIL**

### 14.3 Void Order
- [ ] [B] Create a new order (don't pay) → go to Orders → find it → Void
- [ ] [B] Fill reason = `Test void` → confirm it moves to Voided status · **PASS / FAIL**

### 14.4 Filter / Search
- [ ] [B] Filter orders by Date = today → verify correct results · **PASS / FAIL**
- [ ] [B] Search by Order No. (e.g. `ORD-00001`) → correct order appears · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 15 — KITCHEN SCREEN

- [ ] [B] Go to **Kitchen** screen
- [ ] [B] Verify KOT tickets from POS orders appear · **PASS / FAIL**
- [ ] [B] Tap an item → mark as **Ready** → status updates · **PASS / FAIL**
- [ ] [B] Mark full ticket as **Completed** → ticket removed from Kitchen · **PASS / FAIL**
- [ ] [B] Check Order status updates to `Ready` after kitchen marks it · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 16 — DELIVERY

### 16.1 Add Delivery Company
- [ ] [B] Go to **Delivery → Companies** → Add
- [ ] [B] Fill: Name = `Foodpanda`, Commission % = `15`, Contact = `03001234567`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 16.2 Add another Company
- [ ] [B] Add: Name = `Careem Food`, Commission % = `12` → Save · **PASS / FAIL**

### 16.3 Assign Delivery Company to Unassigned Order
- [ ] [B] Go to **Delivery → Unassigned** → find the delivery order from 12.3
- [ ] [B] Tap → select company `Foodpanda` → Assign
- [ ] [B] Verify no error; commission = GrandTotal × 15% · **PASS / FAIL**
- [ ] [B] Reload order → DeliveryCompanyName = `Foodpanda`, CommissionAmount populated · **PASS / FAIL**

### 16.4 Verify Commission on POS Order
- [ ] [B] Place a new Delivery order with `Foodpanda` selected at order time
- [ ] [B] Verify Commission = SubTotal × 15% (not grand total) · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 17 — CASH DRAWER

### 17.1 Cash In
- [ ] [B] Go to **Cash Drawer** → select type = `In`
- [ ] [B] Fill: Amount = `2000`, Reason = `Opening Float`, Notes = `Test`
- [ ] [B] Tap Add → entry appears in list with Reason column visible · **PASS / FAIL**

### 17.2 Cash Out
- [ ] [B] Select type = `Out`
- [ ] [B] Fill: Amount = `500`, Reason = `Petty Cash Purchase`, Notes = `Supplies`
- [ ] [B] Tap Add → entry appears · **PASS / FAIL**

### 17.3 Verify Totals
- [ ] [B] Verify Total In and Total Out update correctly · **PASS / FAIL**
- [ ] [B] Verify Sales row appears (from POS orders) · **PASS / FAIL**

### 17.4 Empty Reason Guard
- [ ] [B] Try to add Cash In with blank Reason → app shows validation error (does NOT save) · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 18 — EXPENSES

### 18.1 Add Expense
- [ ] [B] Go to **Expenses** → Add
- [ ] [B] Fill: Category = `Utilities`, Amount = `3500`, Description = `Electricity Bill`, Date = today
- [ ] [B] Payment Method = `Cash`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 18.2 Edit Expense
- [ ] [B] Tap expense → Edit → change Amount to `3800` → Save → verify · **PASS / FAIL**

### 18.3 Delete Expense
- [ ] [B] Delete the expense → confirm removed from list · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 19 — SUPPLIERS

### 19.1 Add Supplier
- [ ] [B] Go to **Suppliers** → Add
- [ ] [B] Fill: Name = `Al-Hamd Foods`, Phone = `03101234567`, Address = `Lahore`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 19.2 Edit Supplier
- [ ] [B] Tap `Al-Hamd Foods` → Edit → change Phone to `03109876543` → Save → verify · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 20 — PURCHASES

### 20.1 Add Purchase
- [ ] [B] Go to **Purchases** → Add New Purchase
- [ ] [B] Fill: Supplier = `Al-Hamd Foods`, Date = today
- [ ] [B] Add item: Product = `Zinger Burger` (as ingredient proxy), Qty = `100`, Unit Price = `50`
- [ ] [B] Total = `5000` → Save · **PASS / FAIL**

### 20.2 Return Purchase
- [ ] [B] Find the purchase → Add Return → Qty = `10`, Reason = `Damaged goods`
- [ ] [B] Verify return recorded and amount credited · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 21 — RAW MATERIALS & RECIPES

### 21.1 Add Raw Material
- [ ] [B] Go to **Raw Materials** → Add
- [ ] [B] Fill: Name = `Chicken Patty`, Unit = `Pcs`, Cost = `120`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 21.2 Add Recipe
- [ ] [B] Go to **Recipes** → Add Recipe for `Zinger Burger`
- [ ] [B] Add ingredient: `Chicken Patty` × 1
- [ ] [B] Save → appears in recipe list · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 22 — INVENTORY

- [ ] [B] Go to **Inventory** → verify raw material levels visible · **PASS / FAIL**
- [ ] [B] Adjust stock for `Chicken Patty`: add `50` units, Reason = `Stock received`
- [ ] [B] Verify quantity updated · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 23 — WASTE

- [ ] [B] Go to **Waste** → Add Waste Entry
- [ ] [B] Fill: Item = `Chicken Patty`, Qty = `5`, Reason = `Expired`, Date = today
- [ ] [B] Save → appears in list · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 24 — EMPLOYEES

### 24.1 Add Employee
- [ ] [B] Go to **Employees** → Add
- [ ] [B] Fill: Name = `Usman Ali`, Phone = `03051234567`, Role = `Cook`,
  Joining Date = today, Monthly Salary = `25000`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 24.2 Edit Employee
- [ ] [B] Tap `Usman Ali` → Edit → change Salary to `27000` → Save → verify · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 25 — ATTENDANCE

- [ ] [B] Go to **Attendance** → select Employee = `Usman Ali`
- [ ] [B] Mark today as **Present** → Save · **PASS / FAIL**
- [ ] [B] Reload — verify attendance entry persists · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 26 — PAYROLL

### 26.1 Salary Payment
- [ ] [B] Go to **Payroll → Salary** → select `Usman Ali`
- [ ] [B] Period: Month = current month, Year = current year
- [ ] [B] Check for duplicate guard: try saving twice → second attempt should show "already paid" · **PASS / FAIL**
- [ ] [B] Pay Amount = `27000`, Method = `Cash` → Save · **PASS / FAIL**

### 26.2 Advance Payment
- [ ] [B] Go to **Payroll → Advances** → Add Advance
- [ ] [B] Employee = `Usman Ali`, Amount = `5000`, Date = today, Method = `Cash`, Notes = `Medical`
- [ ] [B] Save → appears in list · **PASS / FAIL**

### 26.3 Delete Advance
- [ ] [B] Delete the advance → verify Cash reversal entry made in CashTransactions · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 27 — RESERVATIONS

### 27.1 Add Reservation
- [ ] [B] Go to **Reservations** → Add
- [ ] [B] Fill: Customer = `Ahmed Raza`, Table = `T3`, Party Size = `4`,
  Date = tomorrow, Time = `19:00`, Notes = `Birthday`
- [ ] [B] Save → appears in calendar/list · **PASS / FAIL**

### 27.2 Edit Reservation
- [ ] [B] Tap reservation → Edit → change Time to `20:00` → Save → verify · **PASS / FAIL**

### 27.3 Cancel Reservation
- [ ] [B] Cancel the reservation → status changes to Cancelled · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 28 — REPORTS

### 28.1 Sales Summary
- [ ] [B] Go to **Reports → Sales Summary**
- [ ] [B] Date From = today, Date To = today → Generate
- [ ] [B] Verify total matches POS orders placed in Module 12 · **PASS / FAIL**

### 28.2 Sales by Payment Method
- [ ] [B] Generate report → verify Cash and Card entries appear · **PASS / FAIL**

### 28.3 Top Products
- [ ] [B] Generate → verify `Zinger Burger` appears as top seller · **PASS / FAIL**

### 28.4 Daily Sales
- [ ] [B] Generate for today → individual order rows visible · **PASS / FAIL**

### 28.5 Expense Report
- [ ] [B] Generate Expense report for today → `Utilities Rs.3800` visible · **PASS / FAIL**

### 28.6 Shift Report / Z-Report
- [ ] [B] Generate Z-Report for current shift → totals match cash drawer entries · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 29 — PRODUCT LEDGER

- [ ] [B] Go to **Product Ledger** → select `Zinger Burger`
- [ ] [B] Verify sales history entries match POS orders · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 30 — STOCK TAKE

- [ ] [B] Go to **Stock Take** → New Stock Take
- [ ] [B] Enter counted qty for `Chicken Patty` = `45`
- [ ] [B] Finalize → verify variance = -5 (100 added - 50 waste - 45 counted = variance) · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 31 — BACKUP

- [ ] [B] Go to **Backup** → tap **Backup Now**
- [ ] [B] Backup completes without error → file saved to device · **PASS / FAIL**
- [ ] [S] Verify backup file visible in device storage · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 32 — AUDIT LOG

- [ ] [B] Go to **Audit Log**
- [ ] [B] Verify recent actions logged: INSERT Waiters, UPDATE Orders, etc. · **PASS / FAIL**
- [ ] [B] Filter by table = `Orders` → only order events shown · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 33 — CUSTOMER WALLET

### 33.1 Top Up
- [ ] [B] Go to **Customers** → tap `Ahmed Raza` → **Wallet**
- [ ] [B] Top Up = `1000` → Save
- [ ] [B] Verify Balance = `1000` · **PASS / FAIL**

### 33.2 Deduct via Payment
- [ ] [B] Place order → pay via Wallet for `Ahmed Raza`
- [ ] [B] Verify balance reduced by order amount · **PASS / FAIL**

### 33.3 Transaction History
- [ ] [B] View wallet transactions → Credit and Debit rows visible with correct amounts · **PASS / FAIL**

Notes: _______________________________________________________________

---

## MODULE 34 — APPEARANCE / THEME

- [ ] [B] Go to **Settings → Appearance** → switch to **Dark Mode** → verify UI updates · **PASS / FAIL**
- [ ] [B] Switch back to **Light Mode** · **PASS / FAIL**
- [ ] [B] Change **Accent Color** → verify color changes across app · **PASS / FAIL**

Notes: _______________________________________________________________

---

## END-TO-END FLOW TEST

> This test runs the full restaurant cycle in one pass.

- [ ] [B] **Open shift** (Opening Cash = Rs. 5000)
- [ ] [B] **Dine-In**: Table T1, Waiter Kamran, items: Zinger + Pepsi + Fries → KOT → Pre-Bill
- [ ] [B] **Kitchen**: Mark all items Ready
- [ ] [B] **Payment**: Full Cash → receipt printed with logo + QR
- [ ] [B] Verify Table T1 = Available after payment
- [ ] [B] **Takeaway**: Token T001 → Chicken Burger x2 → Place → Token print
- [ ] [B] **Delivery**: Foodpanda order → Zinger + Pepsi → commission = SubTotal × 15%
- [ ] [B] **Cash In**: Rs.1000, Reason = "Cash sale"
- [ ] [B] **Cash Out**: Rs.200, Reason = "Petty cash"
- [ ] [B] **Reports**: Sales Summary today → matches all orders above
- [ ] [B] **Close shift** → Z-Report printed → totals correct
- [ ] [B] **No errors** throughout entire flow · **PASS / FAIL**

Notes: _______________________________________________________________

---

## SQL SERVER SPECIFIC TESTS

### DB Migration Verification
- [ ] [Q] After first login, verify these columns exist in SQL Server:
  - `Waiters.AreaId` — `SELECT TOP 1 AreaId FROM Waiters` (no error) · **PASS / FAIL**
  - `CashTransactions.Reason` — `SELECT TOP 1 Reason FROM CashTransactions` · **PASS / FAIL**
  - `Orders.DeliveryCompanyName` — `SELECT TOP 1 DeliveryCompanyName FROM Orders` · **PASS / FAIL**
  - `Orders.CommissionAmount` — `SELECT TOP 1 CommissionAmount FROM Orders` · **PASS / FAIL**
  - `WalletTransactions.TransactionType` — `SELECT TOP 1 TransactionType FROM WalletTransactions` · **PASS / FAIL**

### Concurrent Order Test
- [ ] [Q] Open two devices / sessions → place orders simultaneously (within 1 second)
- [ ] [Q] Verify each gets unique OrderNo and unique TokenNo — no duplicate key error · **PASS / FAIL**

### Branch Filter
- [ ] [Q] Verify Orders, CashTransactions, Reports all filter by correct BranchId · **PASS / FAIL**

Notes: _______________________________________________________________

---

## STANDALONE SPECIFIC TESTS

### Offline Capability
- [ ] [S] Disable Wi-Fi/mobile data → confirm app still works fully · **PASS / FAIL**
- [ ] [S] Place 5 orders offline → verify all saved to SQLite · **PASS / FAIL**
- [ ] [S] Reopen app → orders persist · **PASS / FAIL**

### SQLite Schema Version
- [ ] [S] After fresh install or update, confirm no crash on DB open (version 45) · **PASS / FAIL**
- [ ] [S] If upgrading from old install, confirm all new columns present (DealItems.DealItemId, Orders.DeliveryCompanyName, etc.) · **PASS / FAIL**

Notes: _______________________________________________________________

---

## REGRESSION CHECKS

| Check | Expected Result | Mode | Result |
|-------|----------------|------|--------|
| Paid orders NOT in Active Orders list | ✓ Only Unpaid/Partial visible | B | PASS / FAIL |
| Token numbers unique across orders | ✓ T001, T002, T003 never duplicate | B | PASS / FAIL |
| Product modifier pre-selected on edit | ✓ Checkboxes ticked when re-opening product | B | PASS / FAIL |
| Commission = SubTotal × % (not GrandTotal) | ✓ Lower value than grand-total based calc | B | PASS / FAIL |
| Deal add to cart — no "invalid itemid" | ✓ Deal items appear in cart | B | PASS / FAIL |
| Logo on printed receipt | ✓ Logo visible if uploaded | B | PASS / FAIL |
| QR on printed receipt (FBR off) | ✓ Order number QR visible | B | PASS / FAIL |
| Cash Drawer Reason field required | ✓ Cannot save with blank reason | B | PASS / FAIL |
| Assign delivery company sets CommissionAmount | ✓ Commission calculated and saved | B | PASS / FAIL |
| Hold order restores cart correctly | ✓ All items/qty restored | B | PASS / FAIL |

---

## SIGN-OFF

| Section | Tester | Date | Standalone | SQL Server |
|---------|--------|------|------------|------------|
| Setup | | | PASS/FAIL | PASS/FAIL |
| Settings | | | PASS/FAIL | PASS/FAIL |
| User Management | | | PASS/FAIL | PASS/FAIL |
| Products & Modifiers | | | PASS/FAIL | PASS/FAIL |
| POS & Payment | | | PASS/FAIL | PASS/FAIL |
| Orders & Kitchen | | | PASS/FAIL | PASS/FAIL |
| Delivery | | | PASS/FAIL | PASS/FAIL |
| Cash Drawer | | | PASS/FAIL | PASS/FAIL |
| Employees & Payroll | | | PASS/FAIL | PASS/FAIL |
| Reports | | | PASS/FAIL | PASS/FAIL |
| End-to-End Flow | | | PASS/FAIL | PASS/FAIL |

**Overall Result: PASS / FAIL**

Tester Signature: _______________________ Date: _______________
