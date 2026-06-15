package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Permission
import com.fastpos.android.data.models.Role
import com.fastpos.android.data.models.User
import javax.inject.Inject

class UserManagementRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun getAllUsers(): List<User> = db.query(
        """SELECT u.UserId, u.FullName, u.Username, u.PasswordHash, u.RoleId,
                  r.RoleName, u.IsActive, u.LastLogin
           FROM Users u
           JOIN Roles r ON r.RoleId = u.RoleId
           ORDER BY u.FullName"""
    ) { rs ->
        User(
            userId       = rs.getInt("UserId"),
            fullName     = rs.getString("FullName") ?: "",
            username     = rs.getString("Username") ?: "",
            passwordHash = rs.getString("PasswordHash") ?: "",
            roleId       = rs.getInt("RoleId"),
            roleName     = rs.getString("RoleName") ?: "",
            isActive     = rs.getBoolean("IsActive"),
            lastLogin    = try { rs.getTimestamp("LastLogin") } catch (_: Exception) { null }
        )
    }

    suspend fun getAllRoles(): List<Role> = db.query(
        "SELECT * FROM Roles WHERE IsActive = 1 ORDER BY RoleId"
    ) { rs ->
        Role(roleId = rs.getInt("RoleId"), roleName = rs.getString("RoleName") ?: "")
    }

    suspend fun getAllPermissions(): List<Permission> = db.query(
        "SELECT * FROM Permissions WHERE IsActive = 1 ORDER BY Module, PermissionName"
    ) { rs ->
        Permission(
            permissionId   = rs.getInt("PermissionId"),
            permissionKey  = rs.getString("PermissionKey") ?: "",
            permissionName = rs.getString("PermissionName") ?: "",
            module         = rs.getString("Module") ?: "",
            isGranted      = false
        )
    }

    suspend fun getGrantedPermissionIds(roleId: Int): Set<Int> = db.query(
        "SELECT PermissionId FROM RolePermissions WHERE RoleId = ? AND IsGranted = 1",
        listOf(roleId)
    ) { rs -> rs.getInt("PermissionId") }.toSet()

    suspend fun createUser(user: User, passwordHash: String): Int =
        db.insertAndGetId(
            "INSERT INTO Users (FullName, Username, PasswordHash, RoleId, IsActive, CreatedAt) VALUES (?, ?, ?, ?, ?, GETDATE())",
            listOf(user.fullName, user.username, passwordHash, user.roleId, if (user.isActive) 1 else 0)
        )

    suspend fun updateUser(user: User) {
        db.execute(
            "UPDATE Users SET FullName=?, RoleId=?, IsActive=?, UpdatedAt=GETDATE() WHERE UserId=?",
            listOf(user.fullName, user.roleId, if (user.isActive) 1 else 0, user.userId)
        )
    }

    suspend fun changePassword(userId: Int, passwordHash: String) {
        db.execute(
            "UPDATE Users SET PasswordHash=?, UpdatedAt=GETDATE() WHERE UserId=?",
            listOf(passwordHash, userId)
        )
    }

    suspend fun usernameExists(username: String, excludeUserId: Int = 0): Boolean {
        val count = db.queryOne(
            "SELECT COUNT(1) AS cnt FROM Users WHERE Username = ? AND UserId != ?",
            listOf(username, excludeUserId)
        ) { rs -> rs.getInt("cnt") } ?: 0
        return count > 0
    }

    suspend fun setRolePermissions(roleId: Int, grantedIds: List<Int>) {
        db.transaction { conn ->
            db.executeSync(conn, "DELETE FROM RolePermissions WHERE RoleId = ?", listOf(roleId))
            grantedIds.forEach { pid ->
                db.executeSync(
                    conn,
                    "INSERT INTO RolePermissions (RoleId, PermissionId, IsGranted, CreatedAt) VALUES (?, ?, 1, GETDATE())",
                    listOf(roleId, pid)
                )
            }
        }
    }
}
