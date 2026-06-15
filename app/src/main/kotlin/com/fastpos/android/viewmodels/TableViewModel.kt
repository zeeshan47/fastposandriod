package com.fastpos.android.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.DiningArea
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.RestaurantTable
import com.fastpos.android.data.repositories.OrderRepository
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TableViewModel @Inject constructor(
    private val db:        DatabaseHelper,
    private val orderRepo: OrderRepository,
    val session:           SessionManager
) : ViewModel() {

    private val _tables      = MutableStateFlow<List<RestaurantTable>>(emptyList())
    private val _tableOrders = MutableStateFlow<Map<Int, Order>>(emptyMap())
    private val _areas       = MutableStateFlow<List<DiningArea>>(emptyList())
    private val _isLoading   = MutableStateFlow(false)
    private val _message     = MutableStateFlow<String?>(null)

    val tables:      StateFlow<List<RestaurantTable>> = _tables
    val tableOrders: StateFlow<Map<Int, Order>>       = _tableOrders
    val areas:       StateFlow<List<DiningArea>>      = _areas
    val isLoading:   StateFlow<Boolean>               = _isLoading
    val message:     StateFlow<String?>               = _message

    init {
        load()
        startPolling()
    }

    private val orphanRepairSql = """
        UPDATE Tables SET TableStatus = 'Available'
        WHERE TableStatus = 'Occupied'
          AND TableId NOT IN (
              SELECT DISTINCT TableId FROM Orders
              WHERE TableId IS NOT NULL
                AND OrderStatus IN ('New','Held','SentToKitchen','Ready')
          )""".trimIndent()

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                delay(session.pollIntervalMs)
                try {
                    try { db.execute(orphanRepairSql, emptyList()) } catch (_: Exception) {}
                    _tables.value      = queryTables()
                    _tableOrders.value = try { orderRepo.getActiveOrdersByTable() } catch (_: Exception) { emptyMap() }
                } catch (_: Exception) { }
            }
        }
    }

    private suspend fun queryTables(): List<RestaurantTable> {
        return try {
            db.query(
                """SELECT t.TableId, t.TableName, t.AreaId, a.AreaName, t.Capacity,
                          t.TableStatus, t.IsActive
                   FROM   Tables t
                   LEFT JOIN DiningAreas a ON t.AreaId = a.AreaId
                   WHERE  t.IsActive = 1
                   ORDER  BY a.AreaName, t.TableName"""
            ) { rs ->
                RestaurantTable(
                    tableId     = rs.getInt("TableId"),
                    tableName   = rs.getString("TableName") ?: "",
                    areaId      = rs.getInt("AreaId").takeIf { it > 0 },
                    areaName    = try { rs.getString("AreaName") } catch (_: Exception) { null },
                    capacity    = rs.getInt("Capacity"),
                    tableStatus = rs.getString("TableStatus") ?: "Available",
                    isActive    = rs.getInt("IsActive") == 1
                )
            }
        } catch (_: Exception) {
            db.query(
                """SELECT TableId, TableName, ISNULL(AreaId,0) AS AreaId, Capacity,
                          ISNULL(TableStatus,'Available') AS TableStatus, ISNULL(IsActive,1) AS IsActive
                   FROM Tables
                   WHERE ISNULL(IsActive,1) = 1
                   ORDER BY TableName"""
            ) { rs ->
                RestaurantTable(
                    tableId     = rs.getInt("TableId"),
                    tableName   = rs.getString("TableName") ?: "",
                    areaId      = rs.getInt("AreaId").takeIf { it > 0 },
                    areaName    = null,
                    capacity    = rs.getInt("Capacity"),
                    tableStatus = rs.getString("TableStatus") ?: "Available",
                    isActive    = rs.getInt("IsActive") == 1
                )
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                try { db.execute(orphanRepairSql, emptyList()) } catch (_: Exception) {}
                _tables.value      = queryTables()
                _tableOrders.value = try { orderRepo.getActiveOrdersByTable() } catch (_: Exception) { emptyMap() }
                _areas.value       = queryAreas()
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun queryAreas(): List<DiningArea> = try {
        db.query(
            """SELECT AreaId, AreaName, ISNULL(BranchId,1) AS BranchId,
                      ISNULL(DisplayOrder,0) AS DisplayOrder
               FROM DiningAreas WHERE ISNULL(IsActive,1)=1 ORDER BY DisplayOrder, AreaName"""
        ) { rs ->
            DiningArea(
                areaId       = rs.getInt("AreaId"),
                areaName     = rs.getString("AreaName") ?: "",
                branchId     = rs.getInt("BranchId"),
                displayOrder = rs.getInt("DisplayOrder")
            )
        }
    } catch (_: Exception) {
        db.query("SELECT AreaId, AreaName FROM DiningAreas ORDER BY AreaName") { rs ->
            DiningArea(areaId = rs.getInt("AreaId"), areaName = rs.getString("AreaName") ?: "")
        }
    }

    fun addTable(name: String, areaId: Int?, capacity: Int) {
        if (name.isBlank()) { _message.value = "Table name is required"; return }
        viewModelScope.launch {
            try {
                val params = listOf(name.trim(), areaId, capacity)
                try {
                    db.execute(
                        "INSERT INTO Tables (TableName, AreaId, Capacity, TableStatus, IsActive, CreatedAt) VALUES (?, ?, ?, 'Available', 1, GETDATE())",
                        params
                    )
                } catch (_: Exception) {
                    db.execute(
                        "INSERT INTO Tables (TableName, AreaId, Capacity, TableStatus, IsActive) VALUES (?, ?, ?, 'Available', 1)",
                        params
                    )
                }
                load()
                _message.value = "Table '$name' added"
            } catch (e: Exception) {
                _message.value = "Failed to add table: ${e.message}"
            }
        }
    }

    fun updateTable(tableId: Int, name: String, areaId: Int?, capacity: Int) {
        if (name.isBlank()) { _message.value = "Table name is required"; return }
        viewModelScope.launch {
            try {
                try {
                    db.execute(
                        "UPDATE Tables SET TableName=?, AreaId=?, Capacity=?, UpdatedAt=GETDATE() WHERE TableId=?",
                        listOf(name.trim(), areaId, capacity, tableId)
                    )
                } catch (_: Exception) {
                    db.execute(
                        "UPDATE Tables SET TableName=?, AreaId=?, Capacity=? WHERE TableId=?",
                        listOf(name.trim(), areaId, capacity, tableId)
                    )
                }
                load()
                _message.value = "Table '$name' updated"
            } catch (e: Exception) {
                _message.value = "Failed to update table: ${e.message}"
            }
        }
    }

    fun deleteTable(table: RestaurantTable) {
        viewModelScope.launch {
            try {
                val activeOrders = db.queryOne(
                    "SELECT COUNT(*) AS C FROM Orders WHERE TableId=? AND OrderStatus IN ('New','Held','SentToKitchen','Ready')",
                    listOf(table.tableId)
                ) { rs -> rs.getInt("C") } ?: 0
                if (activeOrders > 0) {
                    _message.value = "Cannot delete '${table.tableName}' — it has active orders"
                    return@launch
                }
                try {
                    db.execute("UPDATE Tables SET IsActive=0, UpdatedAt=GETDATE() WHERE TableId=?", listOf(table.tableId))
                } catch (_: Exception) {
                    db.execute("UPDATE Tables SET IsActive=0 WHERE TableId=?", listOf(table.tableId))
                }
                load()
                _message.value = "'${table.tableName}' deleted"
            } catch (e: Exception) {
                _message.value = "Failed to delete: ${e.message}"
            }
        }
    }

    fun updateStatus(tableId: Int, status: String) {
        viewModelScope.launch {
            try {
                db.execute(
                    "UPDATE Tables SET TableStatus=? WHERE TableId=?",
                    listOf(status, tableId)
                )
                load()
            } catch (e: Exception) {
                _message.value = "Failed to update table: ${e.message}"
            }
        }
    }

    fun transferOrder(orderId: Int, fromTableId: Int, toTableId: Int) {
        viewModelScope.launch {
            try {
                orderRepo.transferOrder(orderId, fromTableId, toTableId)
                load()
                _message.value = "Order transferred successfully."
            } catch (e: Exception) {
                _message.value = "Transfer failed: ${e.message}"
            }
        }
    }

    // In standalone (SQLite) the real table is Areas; in SQL Server (WPF) it is DiningAreas
    private val areaTable: String get() = if (db.isLocal()) "Areas" else "DiningAreas"

    fun addArea(name: String, displayOrder: Int) {
        if (name.isBlank()) { _message.value = "Area name is required"; return }
        viewModelScope.launch {
            try {
                db.execute(
                    "INSERT INTO $areaTable (AreaName, DisplayOrder, BranchId, IsActive, CreatedAt) VALUES (?, ?, ?, 1, GETDATE())",
                    listOf(name.trim(), displayOrder, session.currentBranchId.value)
                )
                load()
                _message.value = "Area '$name' added"
            } catch (e: Exception) { _message.value = "Failed to add area: ${e.message}" }
        }
    }

    fun updateArea(areaId: Int, name: String, displayOrder: Int) {
        if (name.isBlank()) { _message.value = "Area name is required"; return }
        viewModelScope.launch {
            try {
                db.execute(
                    "UPDATE $areaTable SET AreaName=?, DisplayOrder=?, UpdatedAt=GETDATE() WHERE AreaId=?",
                    listOf(name.trim(), displayOrder, areaId)
                )
                load()
                _message.value = "Area '$name' updated"
            } catch (e: Exception) { _message.value = "Failed to update area: ${e.message}" }
        }
    }

    fun deleteArea(areaId: Int, areaName: String) {
        viewModelScope.launch {
            try {
                val tableCount = db.queryOne(
                    "SELECT COUNT(*) AS C FROM Tables WHERE AreaId=? AND ISNULL(IsActive,1)=1",
                    listOf(areaId)
                ) { rs -> rs.getInt("C") } ?: 0
                if (tableCount > 0) {
                    _message.value = "Cannot delete '$areaName' — $tableCount table(s) assigned to it"
                    return@launch
                }
                db.execute("UPDATE $areaTable SET IsActive=0 WHERE AreaId=?", listOf(areaId))
                load()
                _message.value = "Area '$areaName' deleted"
            } catch (e: Exception) { _message.value = "Failed to delete area: ${e.message}" }
        }
    }

    fun clearMessage() { _message.value = null }
}
