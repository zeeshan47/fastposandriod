package com.fastpos.android.di

import android.content.Context
import androidx.room.Room
import com.fastpos.android.data.database.AppDatabase
import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.repositories.*
import com.fastpos.android.utils.SessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton fun provideDatabaseHelper(@ApplicationContext ctx: Context): DatabaseHelper = DatabaseHelper(ctx)

    @Provides @Singleton fun provideAppDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "fastpos_local.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton fun provideOfflineCacheRepository(db: AppDatabase) = OfflineCacheRepository(db)

    @Provides @Singleton fun provideAuthRepository(db: DatabaseHelper)                                       = AuthRepository(db)
    @Provides @Singleton fun provideProductRepository(db: DatabaseHelper, offlineCache: OfflineCacheRepository, audit: AuditLogRepository, session: SessionManager) = ProductRepository(db, offlineCache, audit, session)
    @Provides @Singleton fun provideOrderRepository(db: DatabaseHelper, session: SessionManager, audit: AuditLogRepository) = OrderRepository(db, session, audit)
    @Provides @Singleton fun provideKitchenRepository(db: DatabaseHelper, session: SessionManager)         = KitchenRepository(db, session)
    @Provides @Singleton fun provideDashboardRepository(db: DatabaseHelper, session: SessionManager)       = DashboardRepository(db, session)
    @Provides @Singleton fun provideShiftRepository(db: DatabaseHelper, session: SessionManager, audit: AuditLogRepository) = ShiftRepository(db, session, audit)
    @Provides @Singleton fun provideBranchRepository(db: DatabaseHelper, audit: AuditLogRepository) = BranchRepository(db, audit)
    @Provides @Singleton fun provideCustomerRepository(db: DatabaseHelper, audit: AuditLogRepository) = CustomerRepository(db, audit)
    @Provides @Singleton fun provideExpenseRepository(db: DatabaseHelper, audit: AuditLogRepository, session: SessionManager) = ExpenseRepository(db, audit, session)
    @Provides @Singleton fun provideReportRepository(db: DatabaseHelper)     = ReportRepository(db)
    @Provides @Singleton fun provideSettingsRepository(db: DatabaseHelper, audit: AuditLogRepository, session: SessionManager) = SettingsRepository(db, audit, session)
    @Provides @Singleton fun provideInventoryRepository(db: DatabaseHelper)   = InventoryRepository(db)
    @Provides @Singleton fun provideEmployeeRepository(db: DatabaseHelper, audit: AuditLogRepository) = EmployeeRepository(db, audit)
    @Provides @Singleton fun provideAttendanceRepository(db: DatabaseHelper, audit: AuditLogRepository) = AttendanceRepository(db, audit)
    @Provides @Singleton fun providePayrollRepository(db: DatabaseHelper, audit: AuditLogRepository, session: SessionManager) = PayrollRepository(db, audit, session)
    @Provides @Singleton fun provideRawMaterialRepository(db: DatabaseHelper, audit: AuditLogRepository) = RawMaterialRepository(db, audit)
    @Provides @Singleton fun provideWaiterRepository(db: DatabaseHelper, audit: AuditLogRepository) = WaiterRepository(db, audit)
    @Provides @Singleton fun provideSupplierRepository(db: DatabaseHelper, session: SessionManager, audit: AuditLogRepository) = SupplierRepository(db, session, audit)
    @Provides @Singleton fun providePurchaseRepository(db: DatabaseHelper, session: SessionManager, audit: AuditLogRepository) = PurchaseRepository(db, session, audit)
    @Provides @Singleton fun provideTaxRepository(db: DatabaseHelper, audit: AuditLogRepository, session: SessionManager) = TaxRepository(db, audit, session)
    @Provides @Singleton fun provideDealRepository(db: DatabaseHelper, session: SessionManager, audit: AuditLogRepository) = DealRepository(db, session, audit)
    @Provides @Singleton fun provideReservationRepository(db: DatabaseHelper, audit: AuditLogRepository) = ReservationRepository(db, audit)
    @Provides @Singleton fun provideDeliveryRepository(db: DatabaseHelper, session: SessionManager, audit: AuditLogRepository) = DeliveryRepository(db, session, audit)
    @Provides @Singleton fun provideUserManagementRepository(db: DatabaseHelper)    = UserManagementRepository(db)
    @Provides @Singleton fun provideVoucherRepository(db: DatabaseHelper, audit: AuditLogRepository) = VoucherRepository(db, audit)
    @Provides @Singleton fun provideProductScheduleRepository(db: DatabaseHelper)   = ProductScheduleRepository(db)
    @Provides @Singleton fun provideCustomerWalletRepository(db: DatabaseHelper, audit: AuditLogRepository) = CustomerWalletRepository(db, audit)
    @Provides @Singleton fun provideStockTakeRepository(db: DatabaseHelper)        = StockTakeRepository(db)
    @Provides @Singleton fun provideCashDrawerRepository(db: DatabaseHelper, audit: AuditLogRepository, session: SessionManager) = CashDrawerRepository(db, audit, session)
    @Provides @Singleton fun provideAuditLogRepository(db: DatabaseHelper)        = AuditLogRepository(db)
    @Provides @Singleton fun provideBackupRepository(db: DatabaseHelper)          = BackupRepository(db)
}
