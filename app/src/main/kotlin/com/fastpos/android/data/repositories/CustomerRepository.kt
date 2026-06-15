package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Customer
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepository @Inject constructor(
    private val db:    DatabaseHelper,
    private val audit: AuditLogRepository
) {

    suspend fun getCustomers(search: String = ""): List<Customer> {
        val sql = if (search.isBlank())
            "SELECT * FROM Customers WHERE IsActive = 1 ORDER BY CustomerName"
        else
            "SELECT * FROM Customers WHERE IsActive = 1 AND (CustomerName LIKE ? OR Phone LIKE ?) ORDER BY CustomerName"
        val params = if (search.isBlank()) emptyList() else listOf("%$search%", "%$search%")
        return db.query(sql, params) { rs ->
            Customer(
                customerId    = rs.getInt("CustomerId"),
                customerName  = rs.getString("CustomerName") ?: "",
                phone         = rs.getString("Phone") ?: "",
                address       = rs.getString("Address") ?: "",
                totalOrders   = rs.getInt("TotalOrders"),
                loyaltyPoints = try { rs.getInt("LoyaltyPoints") } catch (_: Exception) { 0 },
                isActive      = rs.getBoolean("IsActive"),
                createdAt     = rs.getTimestamp("CreatedAt") ?: Date()
            )
        }
    }

    suspend fun addCustomer(name: String, phone: String, address: String, createdBy: Int): Int {
        val id = db.insertAndGetId(
            "INSERT INTO Customers (CustomerName, Phone, Address, IsActive, CreatedAt, CreatedBy) VALUES (?,?,?,1,GETDATE(),?)",
            listOf(name, phone.ifBlank { null }, address.ifBlank { null }, createdBy)
        )
        runCatching { audit.writeAudit(createdBy, "INSERT", "Customers", id) }
        return id
    }

    suspend fun updateCustomer(customerId: Int, name: String, phone: String, address: String, updatedBy: Int) {
        db.execute(
            "UPDATE Customers SET CustomerName=?, Phone=?, Address=?, UpdatedAt=GETDATE(), UpdatedBy=? WHERE CustomerId=?",
            listOf(name, phone.ifBlank { null }, address.ifBlank { null }, updatedBy, customerId)
        )
        runCatching { audit.writeAudit(updatedBy, "UPDATE", "Customers", customerId) }
    }

    /** Returns false if the customer has order history (matching WPF guard). */
    suspend fun deleteCustomer(customerId: Int, updatedBy: Int): Boolean {
        val orderCount = try {
            db.queryOne("SELECT COUNT(1) AS cnt FROM Orders WHERE CustomerId=?",
                listOf(customerId)) { it.getInt("cnt") } ?: 0
        } catch (_: Exception) { 0 }
        if (orderCount > 0) return false
        db.execute(
            "UPDATE Customers SET IsActive=0, UpdatedAt=GETDATE(), UpdatedBy=? WHERE CustomerId=?",
            listOf(updatedBy, customerId)
        )
        runCatching { audit.writeAudit(updatedBy, "DELETE", "Customers", customerId) }
        return true
    }

    suspend fun getCustomerById(customerId: Int): Customer? = db.queryOne(
        "SELECT * FROM Customers WHERE CustomerId = ?",
        listOf(customerId)
    ) { rs ->
        Customer(
            customerId    = rs.getInt("CustomerId"),
            customerName  = rs.getString("CustomerName") ?: "",
            phone         = rs.getString("Phone") ?: "",
            address       = rs.getString("Address") ?: "",
            totalOrders   = rs.getInt("TotalOrders"),
            loyaltyPoints = try { rs.getInt("LoyaltyPoints") } catch (_: Exception) { 0 },
            isActive      = rs.getBoolean("IsActive"),
            createdAt     = rs.getTimestamp("CreatedAt") ?: Date()
        )
    }

    suspend fun getByPhone(phone: String): Customer? = db.queryOne(
        "SELECT TOP 1 * FROM Customers WHERE IsActive = 1 AND Phone = ? ORDER BY CustomerId DESC",
        listOf(phone)
    ) { rs ->
        Customer(
            customerId    = rs.getInt("CustomerId"),
            customerName  = rs.getString("CustomerName") ?: "",
            phone         = rs.getString("Phone") ?: "",
            address       = rs.getString("Address") ?: "",
            totalOrders   = rs.getInt("TotalOrders"),
            loyaltyPoints = try { rs.getInt("LoyaltyPoints") } catch (_: Exception) { 0 },
            isActive      = rs.getBoolean("IsActive"),
            createdAt     = rs.getTimestamp("CreatedAt") ?: Date()
        )
    }

    suspend fun incrementTotalOrders(customerId: Int) =
        db.execute(
            "UPDATE Customers SET TotalOrders = ISNULL(TotalOrders, 0) + 1, UpdatedAt = GETDATE() WHERE CustomerId = ?",
            listOf(customerId)
        )

    suspend fun addLoyaltyPoints(customerId: Int, points: Int) {
        db.execute(
            "UPDATE Customers SET LoyaltyPoints = ISNULL(LoyaltyPoints, 0) + ?, UpdatedAt = GETDATE() WHERE CustomerId = ?",
            listOf(points, customerId)
        )
        runCatching { audit.writeAudit(0, "AWARD_POINTS", "Customers", customerId) }
    }

    suspend fun deductLoyaltyPoints(customerId: Int, points: Int) {
        db.execute(
            "UPDATE Customers SET LoyaltyPoints = CASE WHEN ISNULL(LoyaltyPoints,0) >= ? THEN ISNULL(LoyaltyPoints,0) - ? ELSE 0 END, UpdatedAt = GETDATE() WHERE CustomerId = ?",
            listOf(points, points, customerId)
        )
        runCatching { audit.writeAudit(0, "REDEEM_POINTS", "Customers", customerId) }
    }

}
