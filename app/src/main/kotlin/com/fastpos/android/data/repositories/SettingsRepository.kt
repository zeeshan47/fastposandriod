package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.CompanySettings
import com.fastpos.android.utils.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val db:      DatabaseHelper,
    private val audit:   AuditLogRepository,
    private val session: SessionManager
) {

    suspend fun getSettings(): CompanySettings = db.queryOne(
        """SELECT TOP 1 CompanyName, CurrencySymbol, DefaultTaxPercent, ServiceChargePercent,
                  DefaultOrderType, TokenPrefix, AllowPartialPayment,
                  ISNULL(Address,'') AS Address, ISNULL(Phone,'') AS Phone,
                  ISNULL(TaxNumber,'') AS TaxNumber, ISNULL(TaxLabel,'Tax') AS TaxLabel,
                  ISNULL(ReceiptFooter,'Thank you!') AS ReceiptFooter,
                  ISNULL(Language,'English') AS Language,
                  ISNULL(Email,'') AS Email, ISNULL(Website,'') AS Website,
                  ISNULL(AllowNegativeStock,0) AS AllowNegativeStock,
                  ISNULL(MaxDiscountPercent,0) AS MaxDiscountPercent,
                  ISNULL(RequireWaiter,0) AS RequireWaiter,
                  ISNULL(SmsEnabled,0) AS SmsEnabled,
                  ISNULL(SmsGatewayUrl,'') AS SmsGatewayUrl,
                  ISNULL(WhatsappEnabled,0) AS WhatsappEnabled,
                  ISNULL(WhatsappGatewayUrl,'') AS WhatsappGatewayUrl,
                  ISNULL(NotifyOnOrderPlaced,1) AS NotifyOnOrderPlaced,
                  ISNULL(NotifyOnOrderReady,1) AS NotifyOnOrderReady,
                  ISNULL(NotifyOnOrderCancelled,0) AS NotifyOnOrderCancelled,
                  ISNULL(RefreshMode,'Normal') AS RefreshMode,
                  ISNULL(FbrEnabled,0) AS FbrEnabled,
                  ISNULL(FbrPassword,'') AS FbrPassword,
                  ISNULL(FbrNtn,'') AS FbrNtn,
                  ISNULL(FbrBusinessName,'') AS FbrBusinessName,
                  ISNULL(FbrSellerProvince,'') AS FbrSellerProvince,
                  ISNULL(FbrSellerAddress,'') AS FbrSellerAddress,
                  ISNULL(FbrSandboxMode,0) AS FbrSandboxMode,
                  ISNULL(KitchenUrgentMinutes,15) AS KitchenUrgentMinutes
           FROM CompanySettings"""
    ) { rs ->
        CompanySettings(
            companyName          = rs.getString("CompanyName") ?: "My Restaurant",
            currencySymbol       = rs.getString("CurrencySymbol") ?: "Rs.",
            defaultTaxPercent    = rs.getDouble("DefaultTaxPercent"),
            serviceChargePercent = rs.getDouble("ServiceChargePercent"),
            defaultOrderType     = rs.getString("DefaultOrderType") ?: "DineIn",
            tokenPrefix          = rs.getString("TokenPrefix") ?: "T",
            allowPartialPayment  = rs.getBoolean("AllowPartialPayment"),
            address              = rs.getString("Address") ?: "",
            phone                = rs.getString("Phone") ?: "",
            taxNumber            = rs.getString("TaxNumber") ?: "",
            taxLabel             = rs.getString("TaxLabel") ?: "Tax",
            receiptFooter        = rs.getString("ReceiptFooter") ?: "Thank you for your visit!",
            language             = rs.getString("Language") ?: "English",
            email                = rs.getString("Email") ?: "",
            website              = rs.getString("Website") ?: "",
            allowNegativeStock   = rs.getBoolean("AllowNegativeStock"),
            maxDiscountPercent   = try { rs.getDouble("MaxDiscountPercent") } catch (_: Exception) { 0.0 },
            requireWaiter        = rs.getBoolean("RequireWaiter"),
            smsEnabled           = rs.getBoolean("SmsEnabled"),
            smsGatewayUrl        = rs.getString("SmsGatewayUrl") ?: "",
            whatsappEnabled      = rs.getBoolean("WhatsappEnabled"),
            whatsappGatewayUrl   = rs.getString("WhatsappGatewayUrl") ?: "",
            notifyOnOrderPlaced  = rs.getBoolean("NotifyOnOrderPlaced"),
            notifyOnOrderReady   = rs.getBoolean("NotifyOnOrderReady"),
            notifyOnOrderCancelled = rs.getBoolean("NotifyOnOrderCancelled"),
            refreshMode          = rs.getString("RefreshMode") ?: "Normal",
            fbrEnabled           = try { rs.getBoolean("FbrEnabled") } catch (_: Exception) { false },
            fbrToken             = try { rs.getString("FbrPassword") ?: "" } catch (_: Exception) { "" },
            fbrNtn               = try { rs.getString("FbrNtn") ?: "" } catch (_: Exception) { "" },
            fbrBusinessName      = try { rs.getString("FbrBusinessName") ?: "" } catch (_: Exception) { "" },
            fbrProvince          = try { rs.getString("FbrSellerProvince") ?: "" } catch (_: Exception) { "" },
            fbrSellerAddress     = try { rs.getString("FbrSellerAddress") ?: "" } catch (_: Exception) { "" },
            fbrSandboxMode       = try { rs.getBoolean("FbrSandboxMode") } catch (_: Exception) { false },
            fbrHsCode            = try { rs.getString("FbrHsCode") ?: "21069099" } catch (_: Exception) { "21069099" },
            kitchenUrgentMinutes = try { rs.getInt("KitchenUrgentMinutes") } catch (_: Exception) { 15 }
        )
    } ?: CompanySettings()

    suspend fun updateSettings(s: CompanySettings) {
        db.execute(
            """UPDATE CompanySettings SET
               CompanyName=?, CurrencySymbol=?, DefaultTaxPercent=?,
               ServiceChargePercent=?, DefaultOrderType=?, TokenPrefix=?,
               AllowPartialPayment=?,
               Address=?, Phone=?, TaxNumber=?, TaxLabel=?,
               ReceiptFooter=?, Language=?, Email=?, Website=?,
               AllowNegativeStock=?, MaxDiscountPercent=?, RequireWaiter=?,
               SmsEnabled=?, SmsGatewayUrl=?, WhatsappEnabled=?, WhatsappGatewayUrl=?,
               NotifyOnOrderPlaced=?, NotifyOnOrderReady=?, NotifyOnOrderCancelled=?,
               RefreshMode=?,
               FbrEnabled=?, FbrPassword=?, FbrNtn=?, FbrBusinessName=?, FbrSellerProvince=?, FbrSellerAddress=?, FbrSandboxMode=?,
               KitchenUrgentMinutes=?,
               UpdatedAt=GETDATE()
           WHERE SettingId = 1""",
            listOf(
                s.companyName, s.currencySymbol, s.defaultTaxPercent,
                s.serviceChargePercent, s.defaultOrderType, s.tokenPrefix,
                if (s.allowPartialPayment) 1 else 0,
                s.address, s.phone, s.taxNumber, s.taxLabel,
                s.receiptFooter, s.language, s.email, s.website,
                if (s.allowNegativeStock) 1 else 0, s.maxDiscountPercent,
                if (s.requireWaiter) 1 else 0,
                if (s.smsEnabled) 1 else 0, s.smsGatewayUrl,
                if (s.whatsappEnabled) 1 else 0, s.whatsappGatewayUrl,
                if (s.notifyOnOrderPlaced) 1 else 0,
                if (s.notifyOnOrderReady) 1 else 0,
                if (s.notifyOnOrderCancelled) 1 else 0,
                s.refreshMode,
                if (s.fbrEnabled) 1 else 0, s.fbrToken, s.fbrNtn, s.fbrBusinessName, s.fbrProvince, s.fbrSellerAddress,
                if (s.fbrSandboxMode) 1 else 0,
                s.kitchenUrgentMinutes
            )
        )
        runCatching { audit.writeAudit(session.currentUser.value?.userId ?: 0, "UPDATE", "CompanySettings", 1) }
    }

    suspend fun getLogoData(): ByteArray? = try {
        db.queryOne("SELECT LogoData FROM CompanySettings WHERE SettingId=1") { rs ->
            rs.getBytes("LogoData").takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) { null }

    suspend fun updateLogoData(data: ByteArray?) = db.execute(
        "UPDATE CompanySettings SET LogoData = ?, UpdatedAt = GETDATE() WHERE SettingId = 1",
        listOf(data)
    )

    suspend fun getProductCount(): Int =
        db.queryOne("SELECT COUNT(*) AS C FROM Products WHERE IsActive=1") { rs -> rs.getInt("C") } ?: 0

    suspend fun getUserCount(): Int =
        db.queryOne("SELECT COUNT(*) AS C FROM Users WHERE IsActive=1") { rs -> rs.getInt("C") } ?: 0

    suspend fun getTableCount(): Int =
        db.queryOne("SELECT COUNT(*) AS C FROM Tables WHERE IsActive=1") { rs -> rs.getInt("C") } ?: 0
}
