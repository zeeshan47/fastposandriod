package com.fastpos.android.utils

object PermissionKeys {
    // POS
    const val POS_ACCESS           = "POS.Access"
    const val POS_DISCOUNT         = "POS.Discount"
    const val POS_EDIT_PRICE       = "POS.EditPrice"
    const val POS_DELIVERY         = "POS.Delivery"
    const val POS_SAVE_ORDER       = "POS.SaveOrder"
    const val POS_CANCEL_ORDER     = "POS.CancelOrder"
    const val POS_HOLD_ORDER       = "POS.HoldOrder"
    const val POS_RECALL_ORDER     = "POS.RecallOrder"
    const val POS_PRINT_KOT        = "POS.PrintKOT"

    // Products / Catalog
    const val PRODUCTS_VIEW        = "Products.View"
    const val PRODUCTS_ADD         = "Products.Add"
    const val PRODUCTS_EDIT        = "Products.Edit"
    const val PRODUCTS_DELETE      = "Products.Delete"
    const val CATEGORIES_MANAGE    = "Categories.Manage"
    const val SIZES_MANAGE         = "Sizes.Manage"
    const val MODIFIERS_MANAGE     = "Modifiers.Manage"
    const val DEALS_MANAGE         = "Deals.Manage"

    // Setup
    const val TABLES_MANAGE        = "Tables.Manage"
    const val CUSTOMERS_MANAGE     = "Customers.Manage"
    const val WAITERS_MANAGE       = "Waiters.Manage"

    // Shift / Expenses
    const val SHIFT_OPEN           = "Shift.Open"
    const val SHIFT_CLOSE          = "Shift.Close"
    const val EXPENSES_MANAGE      = "Expenses.Manage"

    // Inventory
    const val INVENTORY_PURCHASE   = "Inventory.Purchase"
    const val INVENTORY_STOCK      = "Inventory.Stock"
    const val INVENTORY_RECIPE     = "Inventory.Recipe"
    const val INVENTORY_WASTE      = "Inventory.Waste"

    // Reports
    const val REPORTS_VIEW         = "Reports.View"
    const val REPORTS_PROFIT       = "Reports.Profit"

    // HR
    const val PAYROLL_MANAGE       = "Payroll.Manage"
    const val ATTENDANCE_MANAGE    = "Attendance.Manage"

    // Admin
    const val USERS_MANAGE         = "Users.Manage"
    const val SETTINGS_MANAGE      = "Settings.Manage"

    val ALL: List<String> = listOf(
        POS_ACCESS, POS_DISCOUNT, POS_EDIT_PRICE, POS_DELIVERY,
        POS_SAVE_ORDER, POS_CANCEL_ORDER, POS_HOLD_ORDER, POS_RECALL_ORDER, POS_PRINT_KOT,
        PRODUCTS_VIEW, PRODUCTS_ADD, PRODUCTS_EDIT, PRODUCTS_DELETE,
        CATEGORIES_MANAGE, SIZES_MANAGE, MODIFIERS_MANAGE, DEALS_MANAGE,
        TABLES_MANAGE, CUSTOMERS_MANAGE, WAITERS_MANAGE,
        SHIFT_OPEN, SHIFT_CLOSE, EXPENSES_MANAGE,
        INVENTORY_PURCHASE, INVENTORY_STOCK, INVENTORY_RECIPE, INVENTORY_WASTE,
        REPORTS_VIEW, REPORTS_PROFIT,
        PAYROLL_MANAGE, ATTENDANCE_MANAGE,
        USERS_MANAGE, SETTINGS_MANAGE
    )

    val MANAGER: List<String> = ALL.filter { it != USERS_MANAGE && it != SETTINGS_MANAGE }

    val CASHIER: List<String> = listOf(
        POS_ACCESS, POS_DISCOUNT, POS_DELIVERY, POS_SAVE_ORDER,
        POS_HOLD_ORDER, POS_RECALL_ORDER, POS_PRINT_KOT,
        PRODUCTS_VIEW, CUSTOMERS_MANAGE,
        SHIFT_OPEN, SHIFT_CLOSE, EXPENSES_MANAGE
    )

    val KITCHEN: List<String> = listOf(POS_PRINT_KOT)

    val WAITER: List<String> = listOf(
        POS_ACCESS, POS_HOLD_ORDER, POS_RECALL_ORDER, POS_PRINT_KOT, PRODUCTS_VIEW
    )
}
