package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.User
import com.fastpos.android.utils.PasswordHelper
import java.sql.ResultSet
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun login(username: String, password: String): User? {
        val sql = """
            SELECT u.UserId, u.FullName, u.Username, u.PasswordHash, u.RoleId,
                   r.RoleName, u.IsActive, u.LastLogin
            FROM Users u
            JOIN Roles r ON u.RoleId = r.RoleId
            WHERE u.Username = ?
        """.trimIndent()

        val user = db.queryOne(sql, listOf(username)) { rs -> mapUser(rs) } ?: return null
        if (!user.isActive) return null

        // Try lowercase SHA-256 first, then uppercase (for SQL seed data)
        val hash = PasswordHelper.hash(password)
        val match = user.passwordHash.equals(hash, ignoreCase = true) ||
                    user.passwordHash.equals(hash.uppercase(), ignoreCase = true)
        return if (match) user else null
    }

    suspend fun getUserPermissions(roleId: Int): List<String> {
        val sql = """
            SELECT p.PermissionKey
            FROM RolePermissions rp
            JOIN Permissions p ON rp.PermissionId = p.PermissionId
            WHERE rp.RoleId = ? AND rp.IsGranted = 1 AND p.IsActive = 1
        """.trimIndent()
        return db.query(sql, listOf(roleId)) { rs -> rs.getString("PermissionKey") }
    }

    /** Verify that username + password belong to an active Admin or Manager (RoleId ≤ 2). */
    suspend fun verifyManagerCredentials(username: String, password: String): User? {
        val user = db.queryOne(
            """SELECT u.UserId, u.FullName, u.Username, u.PasswordHash, u.RoleId,
                      r.RoleName, u.IsActive, u.LastLogin
               FROM Users u JOIN Roles r ON u.RoleId = r.RoleId
               WHERE u.Username = ?""",
            listOf(username)
        ) { rs -> mapUser(rs) } ?: return null
        if (!user.isActive || user.roleId > 2) return null
        val hash = PasswordHelper.hash(password)
        val match = user.passwordHash.equals(hash, ignoreCase = true) ||
                    user.passwordHash.equals(hash.uppercase(), ignoreCase = true)
        return if (match) user else null
    }

    suspend fun updateLastLogin(userId: Int) {
        db.execute("UPDATE Users SET LastLogin = GETDATE() WHERE UserId = ?", listOf(userId))
    }

    suspend fun getActiveUsers(): List<User> = try {
        db.query(
            """SELECT u.UserId, u.FullName, u.Username, u.PasswordHash, u.RoleId, r.RoleName, u.IsActive
               FROM Users u
               JOIN Roles r ON u.RoleId = r.RoleId
               WHERE u.IsActive = 1
               ORDER BY u.FullName""",
            emptyList()
        ) { rs -> mapUser(rs) }
    } catch (_: Exception) { emptyList() }

    private fun mapUser(rs: ResultSet) = User(
        userId       = rs.getInt("UserId"),
        fullName     = rs.getString("FullName") ?: "",
        username     = rs.getString("Username") ?: "",
        passwordHash = rs.getString("PasswordHash") ?: "",
        roleId       = rs.getInt("RoleId"),
        roleName     = rs.getString("RoleName") ?: "",
        isActive     = rs.getBoolean("IsActive")
    )
}
