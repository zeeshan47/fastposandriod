package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Voucher
import com.fastpos.android.data.models.VoucherValidation
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoucherRepository @Inject constructor(
    private val db:    DatabaseHelper,
    private val audit: AuditLogRepository
) {

    suspend fun initSchema() {
        if (db.isLocal() || db.isPeerClient()) return
        db.execute("""
            IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='Vouchers' AND xtype='U')
            CREATE TABLE Vouchers (
                VoucherId      INT IDENTITY(1,1) PRIMARY KEY,
                VoucherCode    NVARCHAR(50)  NOT NULL UNIQUE,
                Description    NVARCHAR(200) DEFAULT '',
                DiscountType   NVARCHAR(10)  DEFAULT 'Percent',
                DiscountValue  FLOAT         DEFAULT 0,
                MinOrderAmount FLOAT         DEFAULT 0,
                MaxUses        INT           DEFAULT 0,
                UsedCount      INT           DEFAULT 0,
                ExpiryDate     DATE          NULL,
                IsActive       BIT           DEFAULT 1,
                CreatedAt      DATETIME      DEFAULT GETDATE()
            )
        """)
    }

    suspend fun getAllVouchers(): List<Voucher> {
        initSchema()
        return db.query(
            "SELECT * FROM Vouchers ORDER BY IsActive DESC, CreatedAt DESC",
            emptyList()
        ) { rs ->
            Voucher(
                voucherId      = rs.getInt("VoucherId"),
                voucherCode    = rs.getString("VoucherCode") ?: "",
                description    = rs.getString("Description") ?: "",
                discountType   = rs.getString("DiscountType") ?: "Percent",
                discountValue  = rs.getDouble("DiscountValue"),
                minOrderAmount = rs.getDouble("MinOrderAmount"),
                maxUses        = rs.getInt("MaxUses"),
                usedCount      = rs.getInt("UsedCount"),
                expiryDate     = rs.getDate("ExpiryDate"),
                isActive       = rs.getBoolean("IsActive"),
                createdAt      = rs.getTimestamp("CreatedAt") ?: Date()
            )
        }
    }

    suspend fun createVoucher(
        code:            String,
        description:     String,
        discountType:    String,
        discountValue:   Double,
        minOrderAmount:  Double,
        maxUses:         Int,
        expiryDate:      Date?
    ) {
        initSchema()
        val id = db.insertAndGetId(
            """INSERT INTO Vouchers
                   (VoucherCode, Description, DiscountType, DiscountValue, MinOrderAmount, MaxUses, ExpiryDate)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            listOf(code.trim().uppercase(), description, discountType, discountValue, minOrderAmount, maxUses, expiryDate)
        )
        runCatching { audit.writeAudit(0, "INSERT", "Vouchers", id) }
    }

    suspend fun updateVoucher(
        voucherId:      Int,
        code:           String,
        description:    String,
        discountType:   String,
        discountValue:  Double,
        minOrderAmount: Double,
        maxUses:        Int,
        expiryDate:     Date?,
        isActive:       Boolean
    ) {
        db.execute(
            """UPDATE Vouchers
               SET VoucherCode=?, Description=?, DiscountType=?, DiscountValue=?,
                   MinOrderAmount=?, MaxUses=?, ExpiryDate=?, IsActive=?
               WHERE VoucherId=?""",
            listOf(code.trim().uppercase(), description, discountType, discountValue,
                minOrderAmount, maxUses, expiryDate, if (isActive) 1 else 0, voucherId)
        )
        runCatching { audit.writeAudit(0, "UPDATE", "Vouchers", voucherId) }
    }

    suspend fun deleteVoucher(voucherId: Int) {
        db.execute("UPDATE Vouchers SET IsActive=0 WHERE VoucherId=?", listOf(voucherId))
        runCatching { audit.writeAudit(0, "DELETE", "Vouchers", voucherId) }
    }

    suspend fun validateVoucher(code: String, orderTotal: Double, currencySymbol: String = "Rs."): VoucherValidation {
        initSchema()
        val v = db.queryOne(
            "SELECT * FROM Vouchers WHERE UPPER(VoucherCode)=UPPER(?) AND IsActive=1",
            listOf(code.trim())
        ) { rs ->
            Voucher(
                voucherId      = rs.getInt("VoucherId"),
                voucherCode    = rs.getString("VoucherCode") ?: "",
                description    = rs.getString("Description") ?: "",
                discountType   = rs.getString("DiscountType") ?: "Percent",
                discountValue  = rs.getDouble("DiscountValue"),
                minOrderAmount = rs.getDouble("MinOrderAmount"),
                maxUses        = rs.getInt("MaxUses"),
                usedCount      = rs.getInt("UsedCount"),
                expiryDate     = rs.getDate("ExpiryDate"),
                isActive       = rs.getBoolean("IsActive"),
                createdAt      = rs.getTimestamp("CreatedAt") ?: Date()
            )
        } ?: return VoucherValidation(message = "Invalid voucher code")

        val now = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.time

        if (v.expiryDate != null && v.expiryDate.before(now))
            return VoucherValidation(message = "Voucher has expired")
        if (v.maxUses > 0 && v.usedCount >= v.maxUses)
            return VoucherValidation(message = "Voucher usage limit reached")
        if (v.minOrderAmount > 0 && orderTotal < v.minOrderAmount)
            return VoucherValidation(message = "Minimum order amount of $currencySymbol${v.minOrderAmount.toLong()} required")

        val discountAmount = if (v.discountType == "Percent")
            (orderTotal * v.discountValue / 100.0).coerceAtMost(orderTotal)
        else
            v.discountValue.coerceAtMost(orderTotal)

        val label = if (v.discountType == "Percent")
            "${v.discountValue.toInt()}% off" else "$currencySymbol ${v.discountValue.toInt()} off"

        return VoucherValidation(
            isValid       = true,
            voucherId     = v.voucherId,
            voucherCode   = v.voucherCode,
            discountType  = v.discountType,
            discountValue = v.discountValue,
            discountAmount = discountAmount,
            message       = "Voucher applied: $label"
        )
    }

    suspend fun redeemVoucher(voucherId: Int) {
        db.execute(
            "UPDATE Vouchers SET UsedCount = UsedCount + 1 WHERE VoucherId=?",
            listOf(voucherId)
        )
    }
}
