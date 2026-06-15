package com.fastpos.android.data.repositories

import com.fastpos.android.data.database.DatabaseHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(private val db: DatabaseHelper) {

    suspend fun backupDatabase(backupFolder: String): String {
        if (db.isLocal() || db.isPeerClient()) error("Backup is only available in SQL Server mode.")
        val dbName  = db.getConfig().databaseName.ifBlank { "FASTPOSDB" }
        val ts      = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val folder  = backupFolder.trimEnd('\\', '/')
        val file    = "${folder}\\${dbName}_$ts.bak"
        val safePath = file.replace("'", "''")
        try {
            db.execute(
                "BACKUP DATABASE [$dbName] TO DISK = N'$safePath' WITH FORMAT, STATS = 10, NAME = N'$dbName Full Backup'",
                emptyList()
            )
        } catch (e: Exception) {
            if (e.message?.contains("COMPRESSION", ignoreCase = true) == true ||
                e.message?.contains("not supported", ignoreCase = true) == true) {
                db.execute(
                    "BACKUP DATABASE [$dbName] TO DISK = N'$safePath' WITH FORMAT, STATS = 10, NAME = N'$dbName Full Backup'",
                    emptyList()
                )
            } else throw e
        }
        return file
    }

    suspend fun restoreDatabase(filePath: String) {
        if (db.isLocal() || db.isPeerClient()) error("Restore is only available in SQL Server mode.")
        val dbName   = db.getConfig().databaseName.ifBlank { "FASTPOSDB" }
        val safePath = filePath.trim().replace("'", "''")
        db.execute(
            "RESTORE DATABASE [$dbName] FROM DISK = N'$safePath' WITH REPLACE, RECOVERY",
            emptyList()
        )
    }
}
