package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Branch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BranchRepository @Inject constructor(
    private val db: DatabaseHelper,
    private val audit: AuditLogRepository
) {

    suspend fun getBranches(): List<Branch> = try {
        db.query(
            """SELECT BranchId, BranchName, ISNULL(Address,'') AS Address, ISNULL(Phone,'') AS Phone,
                      ISNULL(IsActive,1) AS IsActive
               FROM Branches
               ORDER BY BranchId"""
        ) { rs ->
            Branch(
                branchId   = rs.getInt("BranchId"),
                branchName = rs.getString("BranchName") ?: "",
                address    = rs.getString("Address") ?: "",
                phone      = rs.getString("Phone") ?: "",
                isActive   = rs.getBoolean("IsActive")
            )
        }
    } catch (_: Exception) { emptyList() }

    suspend fun saveBranch(branch: Branch, userId: Int): Boolean = try {
        if (branch.branchId == 0) {
            val newId = db.insertAndGetId(
                """INSERT INTO Branches (BranchName, Address, Phone, IsActive, CreatedAt, CreatedBy)
                   VALUES (?,?,?,1,GETDATE(),?)""",
                listOf(branch.branchName, branch.address.ifEmpty { null },
                       branch.phone.ifEmpty { null }, userId)
            )
            runCatching { audit.writeAudit(userId, "INSERT", "Branches", newId) }
        } else {
            db.execute(
                """UPDATE Branches SET BranchName=?, Address=?, Phone=?, IsActive=?,
                   UpdatedAt=GETDATE(), UpdatedBy=? WHERE BranchId=?""",
                listOf(branch.branchName, branch.address.ifEmpty { null },
                       branch.phone.ifEmpty { null }, branch.isActive, userId, branch.branchId)
            )
            runCatching { audit.writeAudit(userId, "UPDATE", "Branches", branch.branchId) }
        }
        true
    } catch (_: Exception) { false }

    suspend fun deleteBranch(branchId: Int, userId: Int = 0): Boolean {
        return try {
            val hasOrders = db.queryOne(
                "SELECT COUNT(1) AS Cnt FROM Orders WHERE BranchId=?",
                listOf(branchId)
            ) { it.getInt("Cnt") } ?: 0
            if (hasOrders > 0) return false
            db.execute("DELETE FROM Branches WHERE BranchId=?", listOf(branchId))
            runCatching { audit.writeAudit(userId, "DELETE", "Branches", branchId) }
            true
        } catch (_: Exception) { false }
    }
}
