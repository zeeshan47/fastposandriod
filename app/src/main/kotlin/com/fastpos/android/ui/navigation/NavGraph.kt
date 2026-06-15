package com.fastpos.android.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fastpos.android.ui.screens.customers.CustomerScreen
import com.fastpos.android.ui.screens.dashboard.DashboardScreen
import com.fastpos.android.ui.screens.attendance.AttendanceScreen
import com.fastpos.android.ui.screens.employees.EmployeeScreen
import com.fastpos.android.ui.screens.inventory.InventoryScreen
import com.fastpos.android.ui.screens.payroll.PayrollScreen
import com.fastpos.android.ui.screens.expenses.ExpensesScreen
import com.fastpos.android.ui.screens.waiters.WaitersScreen
import com.fastpos.android.ui.screens.purchases.PurchaseScreen
import com.fastpos.android.ui.screens.tax.TaxScreen
import com.fastpos.android.ui.screens.deals.DealsScreen
import com.fastpos.android.ui.screens.reservations.ReservationScreen
import com.fastpos.android.ui.screens.delivery.DeliveryScreen
import com.fastpos.android.ui.screens.usermanagement.UserManagementScreen
import com.fastpos.android.ui.screens.cashdrawer.CashDrawerScreen
import com.fastpos.android.ui.screens.auditlog.AuditLogScreen
import com.fastpos.android.ui.screens.accounts.AccountLedgerScreen
import com.fastpos.android.ui.screens.waste.WasteScreen
import com.fastpos.android.ui.screens.vouchers.VouchersScreen
import com.fastpos.android.ui.screens.suppliers.SupplierScreen
import com.fastpos.android.ui.screens.recipes.RecipeScreen
import com.fastpos.android.ui.screens.activation.ActivationScreen
import com.fastpos.android.ui.screens.more.RaastQrScreen
import com.fastpos.android.ui.screens.branch.BranchScreen
import com.fastpos.android.ui.screens.splash.SplashScreen
import com.fastpos.android.ui.screens.messagelog.MessageLogScreen
import com.fastpos.android.ui.screens.productledger.ProductLedgerScreen
import com.fastpos.android.ui.screens.modifiers.ModifierManagementScreen
import com.fastpos.android.ui.screens.kitchen.KitchenScreen
import com.fastpos.android.ui.screens.login.LoginScreen
import com.fastpos.android.ui.screens.main.MainBottomBar
import com.fastpos.android.ui.screens.more.MoreScreen
import com.fastpos.android.ui.screens.orders.OrdersScreen
import com.fastpos.android.ui.screens.payment.PaymentScreen
import com.fastpos.android.ui.screens.pos.PosScreen
import com.fastpos.android.ui.screens.products.ProductManagementScreen
import com.fastpos.android.ui.screens.reports.ReportsScreen
import com.fastpos.android.ui.screens.settings.SettingsScreen
import com.fastpos.android.ui.screens.setup.DatabaseSetupScreen
import com.fastpos.android.ui.screens.shift.ShiftScreen
import com.fastpos.android.ui.screens.tables.TableScreen
import com.fastpos.android.utils.SessionManager
import com.fastpos.android.viewmodels.AppViewModel

private val bottomBarRoutes = setOf(
    Screen.Dashboard.route,
    Screen.Pos.route,
    Screen.Kitchen.route,
    Screen.Orders.route,
    Screen.More.route
)

@Composable
private fun PermissionGuard(
    session: SessionManager,
    permKey: String,
    onDenied: () -> Unit,
    content: @Composable () -> Unit
) {
    val permissions by session.permissions.collectAsState()
    val user        by session.currentUser.collectAsState()
    val granted = permissions.contains(permKey) || user?.roleName == "Admin"
    LaunchedEffect(granted) { if (!granted) onDenied() }
    if (granted) content()
}

@Composable
fun FastPosNavGraph() {
    val navController = rememberNavController()
    val appViewModel: AppViewModel = hiltViewModel()
    val startDestination  by appViewModel.startDestination.collectAsState()
    val realDestination   by appViewModel.realDestination.collectAsState()
    val session = appViewModel.session

    if (startDestination == null) return

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isOnline     by appViewModel.isOnline.collectAsState()
    val isPeerMode   = appViewModel.isPeerMode

    Scaffold(
        bottomBar = {
            if (currentRoute in bottomBarRoutes) {
                MainBottomBar(navController)
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            if (!isOnline && isPeerMode) {
                Surface(color = Color(0xFFB71C1C), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WifiOff, null,
                            modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Server offline — orders are being saved locally",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                NavHost(
                    navController       = navController,
                    startDestination    = startDestination!!,
                    modifier            = Modifier,
                    enterTransition     = { slideInHorizontally { it } + fadeIn() },
                    exitTransition      = { slideOutHorizontally { -it } + fadeOut() },
                    popEnterTransition  = { slideInHorizontally { -it } + fadeIn() },
                    popExitTransition   = { slideOutHorizontally { it } + fadeOut() }
                ) {
            // ── Splash ─────────────────────────────────────────────────────
            composable(Screen.Splash.route) {
                SplashScreen(
                    destination = realDestination,
                    onReady = { dest ->
                        navController.navigate(dest) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            // ── License activation ──────────────────────────────────────────
            composable(Screen.Activation.route) {
                ActivationScreen(
                    onContinue = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Screen.Login.route) {
                                popUpTo(Screen.Activation.route) { inclusive = true }
                            }
                        }
                    }
                )
            }

            // ── Raast QR ────────────────────────────────────────────────────
            composable(Screen.RaastQr.route) {
                RaastQrScreen(onNavigateBack = { navController.popBackStack() })
            }

            // ── Auth ────────────────────────────────────────────────────────
            composable(Screen.Setup.route) {
                DatabaseSetupScreen(
                    onSetupComplete = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Setup.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onChangeServer = {
                        navController.navigate(Screen.Setup.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    },
                    onActivate = {
                        navController.navigate(Screen.Activation.route)
                    }
                )
            }

            // ── Main tabs ───────────────────────────────────────────────────
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToPos          = { navController.navigate(Screen.Pos.route) },
                    onNavigateToTables       = { navController.navigate(Screen.Tables.route) },
                    onNavigateToKitchen      = { navController.navigate(Screen.Kitchen.route) },
                    onNavigateToOrders       = { navController.navigate(Screen.Orders.route) },
                    onNavigateToInventory    = { navController.navigate(Screen.Inventory.route) },
                    onNavigateToReservations = { navController.navigate(Screen.Reservations.route) },
                    onNavigateToVouchers     = { navController.navigate(Screen.Vouchers.route) },
                    onNavigateToDelivery     = { navController.navigate(Screen.Delivery.route) },
                    onNavigateToPurchases    = { navController.navigate(Screen.Purchases.route) },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Pos.route) {
                PosScreen(
                    onNavigateBack = {
                        val popped = navController.popBackStack()
                        if (!popped) {
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    },
                    onOpenPayment  = { orderId ->
                        navController.navigate(Screen.Payment.withOrder(orderId))
                    }
                )
            }

            composable(Screen.Kitchen.route) {
                KitchenScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.Orders.route) {
                OrdersScreen(
                    onNavigateBack      = { navController.popBackStack() },
                    onNavigateToPayment = { orderId -> navController.navigate(Screen.Payment.withOrder(orderId)) },
                    onNavigateToPos     = { navController.navigate(Screen.Pos.route) }
                )
            }

            composable(Screen.More.route) {
                MoreScreen(
                    onNavigateToShift     = { navController.navigate(Screen.Shift.route) },
                    onNavigateToSettings  = { navController.navigate(Screen.Settings.route) },
                    onNavigateToReports   = { navController.navigate(Screen.Reports.route) },
                    onNavigateToProducts  = { navController.navigate(Screen.Products.route) },
                    onNavigateToCustomers = { navController.navigate(Screen.Customers.route) },
                    onNavigateToTables    = { navController.navigate(Screen.Tables.route) },
                    onNavigateToInventory  = { navController.navigate(Screen.Inventory.route) },
                    onNavigateToEmployees  = { navController.navigate(Screen.Employees.route) },
                    onNavigateToAttendance   = { navController.navigate(Screen.Attendance.route) },
                    onNavigateToPayroll      = { navController.navigate(Screen.Payroll.route) },
                    onNavigateToExpenses     = { navController.navigate(Screen.Expenses.route) },
                    onNavigateToWaiters      = { navController.navigate(Screen.Waiters.route) },
                    onNavigateToPurchases    = { navController.navigate(Screen.Purchases.route) },
                    onNavigateToTax          = { navController.navigate(Screen.Tax.route) },
                    onNavigateToDeals        = { navController.navigate(Screen.Deals.route) },
                    onNavigateToReservations = { navController.navigate(Screen.Reservations.route) },
                    onNavigateToDelivery        = { navController.navigate(Screen.Delivery.route) },
                    onNavigateToUserManagement  = { navController.navigate(Screen.UserManagement.route) },
                    onNavigateToVouchers        = { navController.navigate(Screen.Vouchers.route) },
                    onNavigateToCashDrawer      = { navController.navigate(Screen.CashDrawer.route) },
                    onNavigateToAuditLog        = { navController.navigate(Screen.AuditLog.route) },
                    onNavigateToWaste           = { navController.navigate(Screen.Waste.route) },
                    onNavigateToSuppliers       = { navController.navigate(Screen.Suppliers.route) },
                    onNavigateToRecipes         = { navController.navigate(Screen.Recipes.route) },
                    onNavigateToModifiers       = { navController.navigate(Screen.Modifiers.route) },
                    onNavigateToProductLedger   = { navController.navigate(Screen.ProductLedger.route) },
                    onNavigateToBranch          = { navController.navigate(Screen.Branch.route) },
                    onNavigateToMessageLog      = { navController.navigate(Screen.MessageLog.route) },
                    onNavigateToActivation      = { navController.navigate(Screen.Activation.route) },
                    onNavigateToRaastQr         = { navController.navigate(Screen.RaastQr.route) },
                    onNavigateToAccountLedger   = { navController.navigate(Screen.AccountLedger.route) },
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // ── Payment (sub-screen, no bottom bar) ─────────────────────────
            composable(
                route     = Screen.Payment.route,
                arguments = listOf(navArgument("orderId") { type = NavType.IntType })
            ) { backStack ->
                val orderId = backStack.arguments!!.getInt("orderId")
                PaymentScreen(
                    orderId = orderId,
                    onPaymentComplete = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Pos.route) { inclusive = true }
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // ── More sub-screens (no bottom bar) ────────────────────────────
            composable(Screen.Shift.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.SHIFT_OPEN, { navController.popBackStack() }) {
                    ShiftScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Settings.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.SETTINGS_MANAGE, { navController.popBackStack() }) {
                    SettingsScreen(
                        onNavigateBack    = { navController.popBackStack() },
                        onResetConnection = {
                            navController.navigate(Screen.Setup.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
            }

            composable(Screen.Reports.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.REPORTS_VIEW, { navController.popBackStack() }) {
                    ReportsScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Products.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.PRODUCTS_VIEW, { navController.popBackStack() }) {
                    ProductManagementScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Customers.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.CUSTOMERS_MANAGE, { navController.popBackStack() }) {
                    CustomerScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Tables.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.TABLES_MANAGE, { navController.popBackStack() }) {
                    TableScreen(
                        onNavigateBack      = { navController.popBackStack() },
                        onNavigateToOrders  = { navController.navigate(Screen.Orders.route) { launchSingleTop = true } },
                        onNavigateToPos     = { navController.navigate(Screen.Pos.route) { launchSingleTop = true } },
                        onNavigateToPayment = { orderId -> navController.navigate(Screen.Payment.withOrder(orderId)) }
                    )
                }
            }

            composable(Screen.Inventory.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.INVENTORY_STOCK, { navController.popBackStack() }) {
                    InventoryScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Employees.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.SETTINGS_MANAGE, { navController.popBackStack() }) {
                    EmployeeScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Attendance.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.ATTENDANCE_MANAGE, { navController.popBackStack() }) {
                    AttendanceScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Payroll.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.PAYROLL_MANAGE, { navController.popBackStack() }) {
                    PayrollScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Expenses.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.EXPENSES_MANAGE, { navController.popBackStack() }) {
                    ExpensesScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Waiters.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.WAITERS_MANAGE, { navController.popBackStack() }) {
                    WaitersScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Purchases.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.INVENTORY_PURCHASE, { navController.popBackStack() }) {
                    PurchaseScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Tax.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.SETTINGS_MANAGE, { navController.popBackStack() }) {
                    TaxScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Deals.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.DEALS_MANAGE, { navController.popBackStack() }) {
                    DealsScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Reservations.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.TABLES_MANAGE, { navController.popBackStack() }) {
                    ReservationScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Delivery.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.POS_DELIVERY, { navController.popBackStack() }) {
                    DeliveryScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.UserManagement.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.USERS_MANAGE, { navController.popBackStack() }) {
                    UserManagementScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Vouchers.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.SETTINGS_MANAGE, { navController.popBackStack() }) {
                    VouchersScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.CashDrawer.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.EXPENSES_MANAGE, { navController.popBackStack() }) {
                    CashDrawerScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.AuditLog.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.SETTINGS_MANAGE, { navController.popBackStack() }) {
                    AuditLogScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.AccountLedger.route) {
                AccountLedgerScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.Waste.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.INVENTORY_WASTE, { navController.popBackStack() }) {
                    WasteScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Suppliers.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.INVENTORY_PURCHASE, { navController.popBackStack() }) {
                    SupplierScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Recipes.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.INVENTORY_RECIPE, { navController.popBackStack() }) {
                    RecipeScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Modifiers.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.MODIFIERS_MANAGE, { navController.popBackStack() }) {
                    ModifierManagementScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.Branch.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.SETTINGS_MANAGE, { navController.popBackStack() }) {
                    BranchScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.MessageLog.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.SETTINGS_MANAGE, { navController.popBackStack() }) {
                    MessageLogScreen(onNavigateBack = { navController.popBackStack() })
                }
            }

            composable(Screen.ProductLedger.route) {
                PermissionGuard(session, com.fastpos.android.utils.PermissionKeys.REPORTS_VIEW, { navController.popBackStack() }) {
                    ProductLedgerScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
        }  // NavHost
            }  // Box
        }  // Column
    }  // Scaffold
}
