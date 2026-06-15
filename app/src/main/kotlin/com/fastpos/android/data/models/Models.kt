package com.fastpos.android.data.models

import java.util.Date

// ─── Security ────────────────────────────────────────────────────────────────

data class User(
    val userId: Int = 0,
    val fullName: String = "",
    val username: String = "",
    val passwordHash: String = "",
    val roleId: Int = 0,
    val roleName: String = "",
    val isActive: Boolean = true,
    val lastLogin: Date? = null,
    val phone: String = "",
    val designation: String = "",
    val joiningDate: Date? = null,
    val monthlySalary: Double = 0.0
)

// ─── Product Catalog ─────────────────────────────────────────────────────────

data class Category(
    val categoryId: Int = 0,
    val categoryName: String = "",
    val otherLanguageName: String = "",
    val colorCode: String = "#FF6B35",
    val displayOrder: Int = 0,
    val isActive: Boolean = true
)

data class Product(
    val productId: Int = 0,
    val productCode: String = "",
    val productName: String = "",
    val productNameOtherLanguage: String = "",
    val categoryId: Int = 0,
    val categoryName: String = "",
    val categoryColor: String = "#FF6B35",
    val productType: String = "Normal",
    val salePrice: Double = 0.0,
    val costPrice: Double = 0.0,
    val isStockManaged: Boolean = false,
    val isRecipeBased: Boolean = false,
    val currentStock: Double = 0.0,
    val unit: String = "Pcs",
    val reorderLevel: Double = 0.0,
    val taxId: Int? = null,
    val taxPercent: Double = 0.0,
    val displayOrder: Int = 0,
    val isActive: Boolean = true,
    val isAvailable: Boolean = true,
    val printerName: String = "",
    val kitchenPrinterId: Int? = null
) {
    val marginPercent: Double get() = if (salePrice > 0 && costPrice > 0) ((salePrice - costPrice) / salePrice) * 100.0 else 0.0
}

data class ProductSize(
    val sizeId: Int = 0,
    val productId: Int = 0,
    val sizeName: String = "",
    val price: Double = 0.0,
    val costPrice: Double = 0.0,
    val displayOrder: Int = 0
)

data class ModifierGroup(
    val modifierGroupId: Int = 0,
    val groupName: String = "",
    val minSelection: Int = 0,
    val maxSelection: Int = 1,
    val isRequired: Boolean = false,
    val modifiers: List<ProductModifier> = emptyList()
)

data class ProductModifier(
    val modifierId: Int = 0,
    val modifierGroupId: Int = 0,
    val modifierName: String = "",
    val extraPrice: Double = 0.0,
    val stockItemId: Int = 0,
    val stockItemName: String = ""
)

// ─── Cart ─────────────────────────────────────────────────────────────────────

data class CartItem(
    val cartId: String = java.util.UUID.randomUUID().toString(),
    val productId: Int = 0,
    val productName: String = "",
    val sizeId: Int? = null,
    val sizeName: String? = null,
    var unitPrice: Double = 0.0,
    var quantity: Int = 1,
    var discountAmount: Double = 0.0,
    val notes: String = "",
    val selectedModifiers: List<SelectedModifier> = emptyList(),
    val printerName: String = "",
    val kitchenPrinterId: Int? = null,
    val productNameOtherLanguage: String = ""
) {
    val modifiersTotal: Double get() = selectedModifiers.sumOf { it.extraPrice * it.quantity }
    val lineTotal: Double get() = (unitPrice + modifiersTotal) * quantity - discountAmount
}

data class SelectedModifier(
    val modifierId: Int = 0,
    val modifierName: String = "",
    val extraPrice: Double = 0.0,
    val quantity: Int = 1
)

// ─── Orders ───────────────────────────────────────────────────────────────────

data class Order(
    val orderId: Int = 0,
    val orderNo: String = "",
    val tokenNo: String = "",
    val orderDate: Date = Date(),
    val orderType: String = "Takeaway",
    val tableId: Int? = null,
    val tableName: String? = null,
    val waiterId: Int? = null,
    val waiterName: String? = null,
    val customerId: Int? = null,
    val customerName: String? = null,
    val shiftId: Int? = null,
    val subTotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val discountPercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val taxPercent: Double = 0.0,
    val serviceCharges: Double = 0.0,
    val deliveryCharge: Double = 0.0,
    val tips: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balanceAmount: Double = 0.0,
    val orderStatus: String = "New",
    val paymentStatus: String = "Unpaid",
    val deliveryName: String? = null,
    val deliveryPhone: String? = null,
    val deliveryAddress: String? = null,
    val deliveryCompanyId: Int? = null,
    val deliveryCompanyName: String? = null,
    val commissionAmount: Double = 0.0,
    val notes: String? = null,
    val createdBy: Int? = null,
    val createdAt: Date = Date(),
    val itemCount: Int = 0,
    val items: List<OrderItem> = emptyList(),
    val fbrInvoiceNo: String? = null,
    val fbrStatus: String? = null
)

data class OrderItem(
    val orderItemId: Int = 0,
    val orderId: Int = 0,
    val productId: Int = 0,
    val sizeId: Int? = null,
    val productNameSnapshot: String = "",
    val sizeNameSnapshot: String? = null,
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val discountAmount: Double = 0.0,
    val lineTotal: Double = 0.0,
    val notes: String? = null,
    val kitchenStatus: String = "Pending",
    val modifiers: List<OrderItemModifier> = emptyList(),
    val printerName: String = ""
)

data class OrderItemModifier(
    val orderItemModifierId: Int = 0,
    val orderItemId: Int = 0,
    val modifierId: Int = 0,
    val modifierNameSnapshot: String = "",
    val extraPrice: Double = 0.0,
    val quantity: Int = 1,
    val total: Double = 0.0
)

data class PaymentEntry(
    val paymentMethod: String = "Cash",
    val amount: Double = 0.0,
    val reference: String = ""
)

// ─── Kitchen ──────────────────────────────────────────────────────────────────

data class KitchenTicket(
    val ticketId: Int = 0,
    val orderId:  Int = 0,
    val orderNo: String = "",
    val tokenNo: String = "",
    val orderType: String = "Takeaway",
    val tableName: String? = null,
    val status: String = "Pending",
    val createdAt: Date = Date(),
    val items: List<KitchenTicketItem> = emptyList()
)

data class KitchenTicketItem(
    val itemId: Int = 0,
    val ticketId: Int = 0,
    val orderItemId: Int = 0,
    val productName: String = "",
    val quantity: Double = 1.0,
    val notes: String? = null,
    val modifiers: String? = null,
    val status: String = "Pending",
    val printerName: String = ""
)

// ─── Shift ────────────────────────────────────────────────────────────────────

data class Shift(
    val shiftId: Int = 0,
    val shiftCode: String = "",
    val businessDate: Date = Date(),
    val userId: Int = 0,
    val openingTime: Date = Date(),
    val closingTime: Date? = null,
    val openingCash: Double = 0.0,
    val closingCash: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val expectedCash: Double = 0.0,
    val difference: Double = 0.0,
    val shiftStatus: String = "Open",
    val notes: String = ""
)

data class ShiftPaymentSummary(
    val method:  String,
    val amount:  Double,
    val txCount: Int
)

data class ShiftCashTotals(
    val cashTxIn:  Double = 0.0,
    val cashTxOut: Double = 0.0
)

// ─── Restaurant Tables ────────────────────────────────────────────────────────

data class RestaurantTable(
    val tableId: Int = 0,
    val tableName: String = "",
    val areaId: Int? = null,
    val areaName: String? = null,
    val capacity: Int = 4,
    val tableStatus: String = "Available",
    val isActive: Boolean = true
)

data class DiningArea(
    val areaId:       Int     = 0,
    val areaName:     String  = "",
    val branchId:     Int     = 1,
    val displayOrder: Int     = 0,
    val isActive:     Boolean = true,
    val createdBy:    Int?    = null,
    val updatedBy:    Int?    = null
)

// ─── Waiter ───────────────────────────────────────────────────────────────────

data class Waiter(
    val waiterId:            Int     = 0,
    val waiterName:          String  = "",
    val phone:               String  = "",
    val isActive:            Boolean = true,
    val areaId:              Int?    = null,
    val linkedEmployeeId:    Int?    = null,
    val linkedEmployeeName:  String  = ""
)

// ─── Dashboard ────────────────────────────────────────────────────────────────

data class DashboardAlert(
    val alertId: String = java.util.UUID.randomUUID().toString(),
    val type: String,         // LOW_STOCK | RESERVATION | VOUCHER | DELIVERY
    val severity: String,     // Error | Warning | Info
    val title: String,
    val message: String,
    val actionLabel: String = "",
    val actionRoute: String = ""
)

data class TopProduct(
    val productName: String,
    val quantity: Double,
    val revenue: Double
)

data class DashboardStats(
    val todaySales: Double = 0.0,
    val shiftSales: Double = 0.0,
    val todayOrders: Int = 0,
    val todayCustomers: Int = 0,
    val avgOrderValue: Double = 0.0,
    val pendingOrders: Int = 0,
    val lowStockCount: Int = 0,
    val yesterdaySales: Double = 0.0,
    val todayReservations: Int = 0,
    val todayExpenses: Double = 0.0,
    val recentOrders: List<Order> = emptyList(),
    val alerts: List<DashboardAlert> = emptyList(),
    val occupiedTables: Int = 0,
    val totalTables: Int = 0,
    val tables: List<RestaurantTable> = emptyList(),
    val weeklyRevenue: List<Pair<String, Double>> = emptyList(),
    val cashInDrawer: Double = 0.0,
    val todayPurchases: Double = 0.0,
    val dineInOrders: Int = 0,
    val takeawayOrders: Int = 0,
    val deliveryOrders: Int = 0,
    val completedOrders: Int = 0,
    val supplierPayables: Double = 0.0,
    val topProducts: List<TopProduct> = emptyList(),
    val weeklyPurchases: List<Pair<String, Double>> = emptyList(),
    val weeklyExpenses: List<Pair<String, Double>> = emptyList()
)

// ─── Customer ─────────────────────────────────────────────────────────────────

data class Customer(
    val customerId: Int = 0,
    val customerName: String = "",
    val phone: String = "",
    val address: String = "",
    val totalOrders: Int = 0,
    val loyaltyPoints: Int = 0,
    val isActive: Boolean = true,
    val createdAt: Date = Date()
)

// ─── Expense ──────────────────────────────────────────────────────────────────

data class Expense(
    val expenseId: Int = 0,
    val shiftId: Int? = null,
    val expenseDate: Date = Date(),
    val expenseType: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val paidTo: String = "",
    val paymentMethod: String = "Cash",
    val createdAt: Date = Date()
)

// ─── Reports ──────────────────────────────────────────────────────────────────

data class SalesSummary(
    val totalSales: Double = 0.0,
    val totalOrders: Int = 0,
    val avgOrderValue: Double = 0.0,
    val totalDiscount: Double = 0.0,
    val totalTax: Double = 0.0,
    val completedOrders: Int = 0,
    val cancelledOrders: Int = 0,
    val voidedOrders: Int = 0,
    val refundedOrders: Int = 0
)

data class PaymentMethodSummary(
    val method: String = "",
    val amount: Double = 0.0,
    val count: Int = 0
)

data class ProductSaleSummary(
    val productId:    Int    = 0,
    val productName:  String = "",
    val quantity:     Double = 0.0,
    val revenue:      Double = 0.0,
    val costOfGoods:  Double = 0.0
) {
    val grossProfit: Double get() = revenue - costOfGoods
    val marginPercent: Double get() = if (revenue > 0 && costOfGoods > 0) (grossProfit / revenue) * 100.0 else 0.0
}

data class WaiterSalesSummary(
    val waiterName:  String = "",
    val orderCount:  Int    = 0,
    val total:       Double = 0.0,
    val avgOrder:    Double = 0.0
)

data class UserSalesSummary(
    val userName:   String = "",
    val orderCount: Int    = 0,
    val totalSales: Double = 0.0,
    val avgOrder:   Double = 0.0
)

data class ProfitLoss(
    val revenue:          Double = 0.0,
    val cogs:             Double = 0.0,
    val expenses:         Double = 0.0,
    val payrollCosts:     Double = 0.0,
    val purchaseCosts:    Double = 0.0,
    val advanceSalaries:  Double = 0.0
) {
    val grossProfit: Double get() = revenue - cogs
    // purchaseCosts uses PaidAmount (cash actually paid), not TotalAmount (invoiced).
    // Only deducted when COGS = 0 to avoid double-counting ingredient costs.
    val netProfit: Double get() = grossProfit - expenses - payrollCosts - advanceSalaries -
            (if (cogs == 0.0) purchaseCosts else 0.0)
}

data class CustomerSalesSummary(
    val customerId:   Int    = 0,
    val customerName: String = "",
    val orderCount:   Int    = 0,
    val totalSpent:   Double = 0.0
)

data class SupplierPurchaseSummary(
    val supplierName:  String = "Unknown",
    val invoiceCount:  Int    = 0,
    val totalAmount:   Double = 0.0,
    val totalPaid:     Double = 0.0,
    val totalBalance:  Double = 0.0
)

data class CustomerFeedback(
    val feedbackId:   Int    = 0,
    val customerId:   Int?   = null,
    val customerName: String = "",
    val orderId:      Int?   = null,
    val rating:       Int    = 5,
    val comment:      String = "",
    val feedbackDate: Date   = Date()
) {
    val ratingStars: String get() = "★".repeat(rating) + "☆".repeat(5 - rating.coerceIn(0, 5))
}

data class DailySalesDetail(
    val date: String = "",
    val orderCount: Int = 0,
    val sales: Double = 0.0,
    val tax: Double = 0.0,
    val discount: Double = 0.0
)

data class InventoryItem(
    val productId: Int = 0,
    val productCode: String = "",
    val productName: String = "",
    val unit: String = "",
    val currentStock: Double = 0.0,
    val reorderLevel: Double = 0.0,
    val purchaseRate: Double = 0.0
) {
    val status: String get() = when {
        currentStock <= 0 -> "OUT"
        reorderLevel > 0 && currentStock <= reorderLevel -> "LOW"
        else -> "OK"
    }
}

// ─── Inventory ────────────────────────────────────────────────────────────────

data class StockItem(
    val productId: Int = 0,
    val productName: String = "",
    val categoryId: Int = 0,
    val categoryName: String = "",
    val categoryColor: String = "#FF6B35",
    val currentStock: Double = 0.0,
    val minimumStock: Double = 5.0,
    val salePrice: Double = 0.0,
    val isActive: Boolean = true
)

// ─── Roles / Employees ───────────────────────────────────────────────────────

data class Role(
    val roleId: Int = 0,
    val roleName: String = ""
)

data class Employee(
    val employeeId:    Int     = 0,
    val employeeName:  String  = "",
    val phone:         String  = "",
    val employeeRole:  String  = "",
    val joiningDate:   Date?   = null,
    val monthlySalary: Double  = 0.0,
    val isActive:      Boolean = true,
    val branchId:      Int     = 1
)

data class Permission(
    val permissionId:   Int     = 0,
    val permissionKey:  String  = "",
    val permissionName: String  = "",
    val module:         String  = "",
    val isGranted:      Boolean = false
)

// ─── Attendance ───────────────────────────────────────────────────────────────

data class AttendanceRecord(
    val attendanceId: Int = 0,
    val employeeId: Int = 0,
    val fullName: String = "",
    val roleName: String = "",
    val attendanceDate: Date = Date(),
    val checkInTime: Date? = null,
    val checkOutTime: Date? = null,
    val notes: String = ""
) {
    val canCheckIn: Boolean get() = checkInTime == null
    val canCheckOut: Boolean get() = checkInTime != null && checkOutTime == null
    val statusLabel: String get() = when {
        checkInTime == null -> "Absent"
        checkOutTime == null -> "In Progress"
        else -> "Completed"
    }
    private val timeFmt get() = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
    val checkInLabel: String get() = checkInTime?.let { timeFmt.format(it) } ?: "—"
    val checkOutLabel: String get() = checkOutTime?.let { timeFmt.format(it) } ?: "—"
    val durationLabel: String get() {
        val ci = checkInTime ?: return "Absent"
        val co = checkOutTime ?: return "In Progress"
        val mins = (co.time - ci.time) / 60000L
        return "${mins / 60}h ${mins % 60}m"
    }
}

data class AttendanceMonthSummary(
    val userId: Int = 0,
    val fullName: String = "",
    val presentDays: Int = 0,
    val inProgressDays: Int = 0,
    val absentDays: Int = 0
)

data class DailyAttendanceRow(
    val attendanceDate: Date = Date(),
    val employeeName: String = "",
    val roleName: String = "",
    val checkInTime: Date? = null,
    val checkOutTime: Date? = null
) {
    val statusLabel: String get() = when {
        checkInTime == null -> "Absent"
        checkOutTime == null -> "In Progress"
        else -> "Completed"
    }
    private val timeFmt get() = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
    val checkInLabel: String get() = checkInTime?.let { timeFmt.format(it) } ?: "—"
    val checkOutLabel: String get() = checkOutTime?.let { timeFmt.format(it) } ?: "—"
    val durationLabel: String get() {
        val ci = checkInTime ?: return "—"
        val co = checkOutTime ?: return "—"
        val mins = (co.time - ci.time) / 60000L
        return "${mins / 60}h ${mins % 60}m"
    }
}

// ─── Payroll ──────────────────────────────────────────────────────────────────

data class EmployeeSalaryInfo(
    val userId: Int = 0,
    val fullName: String = "",
    val roleName: String = "",
    val basicSalary: Double = 0.0,
    val isActive: Boolean = true
)

data class SalaryPayment(
    val paymentId: Int = 0,
    val employeeId: Int = 0,
    val employeeName: String = "",
    val paymentDate: Date = Date(),
    val amount: Double = 0.0,
    val periodMonth: Int = 0,
    val periodYear: Int = 0,
    val paymentMethod: String = "Cash",
    val notes: String = "",
    val createdAt: Date = Date()
) {
    val periodLabel: String get() {
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        return "${months.getOrElse(periodMonth - 1) { "?" }} $periodYear"
    }
}

data class EmployeeAdvance(
    val advanceId: Int = 0,
    val employeeId: Int = 0,
    val employeeName: String = "",
    val advanceDate: Date = Date(),
    val amount: Double = 0.0,
    val notes: String = "",
    val paymentMethod: String = "Cash",
    val createdAt: Date = Date()
)

data class PayrollRow(
    val employeeId: Int = 0,
    val employeeName: String = "",
    val employeeRole: String = "",
    val monthlySalary: Double = 0.0,
    val advancesThisMonth: Double = 0.0,
    val alreadyPaid: Double = 0.0
) {
    val netPay: Double get() = (monthlySalary - advancesThisMonth).coerceAtLeast(0.0)
    val remainingPay: Double get() = (netPay - alreadyPaid).coerceAtLeast(0.0)
    val isPaid: Boolean get() = remainingPay <= 0.01
}

// kept for reports only
data class PayrollRecord(
    val payrollId: Int = 0,
    val userId: Int = 0,
    val fullName: String = "",
    val payMonth: Int = 0,
    val payYear: Int = 0,
    val basicSalary: Double = 0.0,
    val deductions: Double = 0.0,
    val netSalary: Double = 0.0,
    val paymentStatus: String = "Pending"
)

// ─── Raw Material & Recipe ────────────────────────────────────────────────────

data class RawMaterial(
    val materialId: Int = 0,
    val materialName: String = "",
    val unit: String = "kg",
    val currentStock: Double = 0.0,
    val minStockLevel: Double = 0.0,
    val costPerUnit: Double = 0.0,
    val isActive: Boolean = true
)

data class RecipeHeader(
    val recipeId:    Int    = 0,
    val productId:   Int    = 0,
    val productName: String = "",
    val sizeId:      Int?   = null,
    val sizeName:    String = "",
    val recipeName:  String = "",
    val itemCount:   Int    = 0
) {
    val displayName: String get() = if (sizeName.isBlank()) productName else "$productName ($sizeName)"
}

data class RecipeItem(
    val recipeId: Int = 0,
    val productId: Int = 0,
    val productName: String = "",
    val materialId: Int = 0,
    val materialName: String = "",
    val unit: String = "",
    val quantityRequired: Double = 0.0,
    val costPerUnit: Double = 0.0
) {
    val lineCost: Double get() = quantityRequired * costPerUnit
}

// ─── Stock Ledger ────────────────────────────────────────────────────────────

data class InventoryLedger(
    val ledgerId:   Int    = 0,
    val materialId: Int    = 0,
    val transDate:  Date   = Date(),
    val refType:    String = "",      // Purchase / Waste / Order / Adjustment
    val refId:      Int?   = null,
    val inQty:      Double = 0.0,
    val outQty:     Double = 0.0,
    val balanceQty: Double = 0.0,
    val rate:       Double = 0.0,
    val amount:     Double = 0.0,
    val remarks:    String = ""
)

// ─── Wastage ──────────────────────────────────────────────────────────────────

data class WasteEntry(
    val wasteId:     Int    = 0,
    val wasteDate:   Date   = Date(),
    val notes:       String = "",
    val itemCount:   Int    = 0,
    val totalAmount: Double = 0.0
)

data class WasteEntryItem(
    val itemId:     Int    = 0,
    val wasteId:    Int    = 0,
    val materialId: Int    = 0,
    val itemName:   String = "",
    val quantity:   Double = 1.0,
    val unit:       String = "kg",
    val rate:       Double = 0.0,
    val reason:     String = "",
    val amount:     Double = 0.0
)

// ─── Kitchen Printer Routing ──────────────────────────────────────────────────

data class KitchenPrinterConfig(
    val printerName: String = "",
    val ipAddress: String = "",
    val port: Int = 9100,
    val paperType: String? = null
)

data class PrinterOption(
    val name:    String = "",
    val type:    String = "Bluetooth",  // "Bluetooth" or "Network"
    val address: String = "",
    val ip:      String = "",
    val port:    Int    = 9100
)

// ─── Tax ─────────────────────────────────────────────────────────────────────

data class TaxRate(
    val taxId:      Int     = 0,
    val taxName:    String  = "",
    val taxPercent: Double  = 0.0,
    val isActive:   Boolean = true
)

// ─── Deals / Combos ───────────────────────────────────────────────────────────

data class Deal(
    val dealId:          Int     = 0,
    val dealName:        String  = "",
    val description:     String  = "",
    val dealPrice:       Double  = 0.0,
    val discountPercent: Double  = 0.0,
    val validFrom:       Date?   = null,
    val validTo:         Date?   = null,
    val isActive:        Boolean = true,
    val items:           List<DealItem> = emptyList()
)

data class DealItem(
    val itemId:      Int     = 0,
    val dealId:      Int     = 0,
    val productId:   Int     = 0,
    val productName: String  = "",
    val salePrice:   Double  = 0.0,
    val sizeId:      Int?    = null,
    val sizeName:    String? = null,
    val quantity:    Int     = 1,
    val isOptional:  Boolean = false
)

// ─── Reservations ─────────────────────────────────────────────────────────────

data class Reservation(
    val reservationId:   Int     = 0,
    val customerName:    String  = "",
    val phone:           String  = "",
    val partySize:       Int     = 1,
    val reservationDate: Date    = Date(),
    val reservationTime: String  = "19:00",
    val tableId:         Int?    = null,
    val tableName:       String? = null,
    val status:          String  = "Confirmed",
    val notes:           String  = "",
    val createdAt:       Date    = Date()
)

// ─── Suppliers & Purchase Orders ─────────────────────────────────────────────

data class Supplier(
    val supplierId:    Int     = 0,
    val supplierName:  String  = "",
    val contactPerson: String  = "",
    val phone:         String  = "",
    val address:       String  = "",
    val email:         String  = "",
    val isActive:      Boolean = true
)

data class PurchaseProduct(
    val productId:     Int    = 0,
    val productName:   String = "",
    val unit:          String = "",
    val purchasePrice: Double = 0.0
)

data class PurchaseInvoice(
    val invoiceId:      Int     = 0,
    val invoiceNo:      String  = "",
    val supplierId:     Int?    = null,
    val supplierName:   String  = "",
    val invoiceDate:    Date    = Date(),
    val totalAmount:    Double  = 0.0,
    val paidAmount:     Double  = 0.0,
    val balanceAmount:  Double  = 0.0,
    val paymentStatus:  String  = "Unpaid",
    val notes:          String  = "",
    val createdAt:      Date    = Date(),
    val items:          List<PurchaseInvoiceItem> = emptyList()
)

data class PurchaseInvoiceItem(
    val itemId:         Int    = 0,
    val invoiceId:      Int    = 0,
    val productId:      Int?   = null,
    val itemName:       String = "",
    val unit:           String = "kg",
    val quantity:       Double = 1.0,
    val purchaseRate:   Double = 0.0,
    val discountAmount: Double = 0.0
) {
    val total: Double get() = quantity * purchaseRate - discountAmount
}

data class PurchasePayment(
    val paymentId:     Int    = 0,
    val supplierId:    Int    = 0,
    val supplierName:  String = "",
    val paymentDate:   Date   = Date(),
    val amount:        Double = 0.0,
    val paymentMethod: String = "Cash",
    val reference:     String = "",
    val notes:         String = ""
)

data class SupplierBalance(
    val supplierId:         Int    = 0,
    val supplierName:       String = "",
    val phone:              String = "",
    val totalInvoiced:      Double = 0.0,
    val directPayments:     Double = 0.0,
    val outstandingBalance: Double = 0.0
)

data class SupplierLedgerEntry(
    val entryDate:  java.util.Date = java.util.Date(),
    val entryType:  String         = "",
    val reference:  String         = "",
    val debit:      Double         = 0.0,
    val credit:     Double         = 0.0,
    val balance:    Double         = 0.0,
    val notes:      String         = ""
)

data class PurchaseReturn(
    val returnId:     Int    = 0,
    val invoiceId:    Int?   = null,
    val supplierId:   Int?   = null,
    val supplierName: String = "",
    val returnDate:   Date   = Date(),
    val totalAmount:  Double = 0.0,
    val notes:        String = "",
    val refundMethod: String = "Credit",
    val shiftId:      Int?   = null,
    val itemCount:    Int    = 0,
    val createdAt:    Date   = Date()
)

data class PurchaseReturnItem(
    val returnItemId: Int    = 0,
    val returnId:     Int    = 0,
    val productId:    Int    = 0,
    val itemName:     String = "",
    val quantity:     Double = 0.0,
    val unit:         String = "",
    val returnRate:   Double = 0.0
) {
    val lineTotal: Double get() = quantity * returnRate
}

// ─── Delivery ─────────────────────────────────────────────────────────────────

data class DeliveryCompany(
    val companyId:         Int     = 0,
    val companyName:       String  = "",
    val commissionPercent: Double  = 0.0,
    val isActive:          Boolean = true
)

data class DeliverySettlement(
    val settlementId:      Int     = 0,
    val deliveryCompanyId: Int     = 0,
    val companyName:       String  = "",
    val settlementDate:    java.util.Date = java.util.Date(),
    val amountReceived:    Double  = 0.0,
    val notes:             String  = ""
)

data class CompanyBalance(
    val companyId:   Int    = 0,
    val companyName: String = "",
    val balance:     Double = 0.0
)

// ─── Company Settings ─────────────────────────────────────────────────────────

data class CompanySettings(
    val companyName: String = "My Restaurant",
    val currencySymbol: String = "Rs.",
    val defaultTaxPercent: Double = 0.0,
    val serviceChargePercent: Double = 0.0,
    val defaultOrderType: String = "DineIn",
    val tokenPrefix: String = "T",
    val allowPartialPayment: Boolean = true,
    val address: String = "",
    val phone: String = "",
    val taxNumber: String = "",
    val taxLabel: String = "Tax",
    val receiptFooter: String = "Thank you for your visit!",
    val language: String = "English",
    val email: String = "",
    val website: String = "",
    val allowNegativeStock: Boolean = false,
    val maxDiscountPercent: Double = 0.0,
    val requireWaiter: Boolean = false,
    val smsEnabled: Boolean = false,
    val smsGatewayUrl: String = "",
    val whatsappEnabled: Boolean = false,
    val whatsappGatewayUrl: String = "",
    val notifyOnOrderPlaced: Boolean = true,
    val notifyOnOrderReady: Boolean = true,
    val notifyOnOrderCancelled: Boolean = false,
    val refreshMode: String = "Normal",
    val logoData: ByteArray? = null,
    val fbrEnabled: Boolean = false,
    val fbrToken: String = "",
    val fbrNtn: String = "",
    val fbrBusinessName: String = "",
    val fbrProvince: String = "",
    val fbrSellerAddress: String = "",
    val fbrSandboxMode: Boolean = false,
    val fbrHsCode: String = "21069099",
    val kitchenUrgentMinutes: Int = 15
)

// ─── Product Schedule ────────────────────────────────────────────────────────

data class ProductSchedule(
    val scheduleId: Int = 0,
    val productId: Int = 0,
    val label: String = "",          // e.g. "Breakfast", "Lunch", "Happy Hour"
    val dayOfWeek: Int = -1,         // 0=Sun…6=Sat, -1 = every day
    val startTime: String = "00:00", // HH:mm 24-hour
    val endTime: String = "23:59",
    val isActive: Boolean = true
)

// ─── Vouchers ─────────────────────────────────────────────────────────────────

data class Voucher(
    val voucherId: Int = 0,
    val voucherCode: String = "",
    val description: String = "",
    val discountType: String = "Percent",   // Percent | Fixed
    val discountValue: Double = 0.0,
    val minOrderAmount: Double = 0.0,
    val maxUses: Int = 0,                   // 0 = unlimited
    val usedCount: Int = 0,
    val expiryDate: Date? = null,
    val isActive: Boolean = true,
    val createdAt: Date = Date()
)

data class VoucherValidation(
    val isValid: Boolean = false,
    val voucherId: Int = 0,
    val voucherCode: String = "",
    val discountType: String = "Percent",
    val discountValue: Double = 0.0,
    val discountAmount: Double = 0.0,
    val message: String = ""
)

// ─── Customer Wallet ──────────────────────────────────────────────────────────

data class WalletTransaction(
    val transId:    Int    = 0,
    val customerId: Int    = 0,
    val transDate:  Date   = Date(),
    val transType:  String = "TopUp",   // TopUp | Deduction | Refund
    val amount:     Double = 0.0,
    val orderId:    Int?   = null,
    val notes:      String = "",
    val createdBy:  Int?   = null
)

// ─── Stock Take ───────────────────────────────────────────────────────────────

data class StockTake(
    val stockTakeId: Int    = 0,
    val takeDate:    Date   = Date(),
    val status:      String = "Open",   // Open | Finalized | Cancelled
    val notes:       String = "",
    val createdBy:   Int?   = null,
    val finalizedAt: Date?  = null,
    val itemCount:   Int    = 0
)

data class StockTakeItem(
    val itemId:       Int    = 0,
    val stockTakeId:  Int    = 0,
    val materialId:   Int    = 0,
    val materialName: String = "",
    val unit:         String = "kg",
    val expectedQty:  Double = 0.0,
    val actualQty:    Double = 0.0
) {
    val variance: Double get() = actualQty - expectedQty
}

// ─── Cash Drawer ──────────────────────────────────────────────────────────────

data class CashTransaction(
    val transactionId:   Int    = 0,
    val shiftId:         Int    = 0,
    val transactionType: String = "In",  // "In" | "Out"
    val amount:          Double = 0.0,
    val reason:          String = "",
    val notes:           String = "",
    val createdBy:       Int    = 0,
    val createdAt:       Date   = Date()
)

data class DeliveryReportRow(
    val companyId:         Int    = 0,
    val companyName:       String = "",
    val commissionPercent: Double = 0.0,
    val orderCount:        Int    = 0,
    val totalSales:        Double = 0.0,
    val totalCommission:   Double = 0.0,
    val netRevenue:        Double = 0.0
)

data class ShiftSummaryReport(
    val shiftId:     Int    = 0,
    val shiftCode:   String = "",
    val openedBy:    String = "",
    val openDate:    Date   = Date(),
    val closeDate:   Date?  = null,
    val totalSales:  Double = 0.0,
    val orderCount:  Int    = 0,
    val shiftStatus: String = "Closed",
    val cashIn:      Double = 0.0,
    val cashOut:     Double = 0.0
)

// ─── Audit Log ────────────────────────────────────────────────────────────────

data class AuditLogRow(
    val logId:       Int    = 0,
    val loggedAt:    Date   = Date(),
    val userName:    String = "System",
    val action:      String = "",
    val tableName:   String = "",
    val recordId:    Int?   = null,
    val machineName: String = ""
)

// ─── MessageLog ───────────────────────────────────────────────────────────────

data class MessageLog(
    val messageId:      Int     = 0,
    val orderId:        Int?    = null,
    val orderNo:        String  = "",
    val recipientPhone: String  = "",
    val channel:        String  = "",
    val messageText:    String  = "",
    val status:         String  = "",
    val errorMessage:   String  = "",
    val sentAt:         Date?   = null,
    val createdAt:      Date    = Date()
)

// ─── Branch ───────────────────────────────────────────────────────────────────

data class Branch(
    val branchId:        Int     = 0,
    val branchName:      String  = "",
    val address:         String  = "",
    val phone:           String  = "",
    val isActive:        Boolean = true,
    val isCurrentBranch: Boolean = false
)


// ─── Accounting Module ────────────────────────────────────────────────────────

data class ChartOfAccount(
    val accountId:   Int     = 0,
    val accountCode: String  = "",
    val accountName: String  = "",
    val accountType: String  = "",   // Asset | Liability | Equity | Income | Expense
    val isActive:    Boolean = true
) {
    val displayName get() = "$accountCode — $accountName"
}

data class AccountLedgerEntry(
    val ledgerId:      Int     = 0,
    val accountId:     Int     = 0,
    val accountCode:   String  = "",
    val accountName:   String  = "",
    val entryDate:     Date    = Date(),
    val debit:         Double  = 0.0,
    val credit:        Double  = 0.0,
    val referenceType: String  = "",
    val referenceId:   Int?    = null,
    val narration:     String  = "",
    val createdAt:     Date    = Date(),
    var balance:       Double  = 0.0    // running balance, set client-side
)

data class TrialBalanceRow(
    val accountId:   Int    = 0,
    val accountCode: String = "",
    val accountName: String = "",
    val accountType: String = "",
    val totalDebit:  Double = 0.0,
    val totalCredit: Double = 0.0
) {
    val balance get() = totalDebit - totalCredit
    val balanceLabel get() = when {
        balance == 0.0  -> "—"
        balance > 0.0   -> "Dr %.2f".format(balance)
        else            -> "Cr %.2f".format(-balance)
    }
}
