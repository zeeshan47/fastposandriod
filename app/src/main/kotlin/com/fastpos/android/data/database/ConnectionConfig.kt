package com.fastpos.android.data.database

data class ConnectionConfig(
    val serverIp: String = "",
    val port: Int = 1433,
    val instanceName: String = "",       // e.g. "SQLEXPRESS" – leave blank for default
    val databaseName: String = "FASTPOSDB",
    val username: String = "",
    val password: String = ""
) {
    fun buildJdbcUrl(): String {
        // JTDS requires instance name as a parameter, NOT embedded in the host.
        // Wrong: 192.168.1.10\SQLEXPRESS  → JTDS tries to resolve this as a hostname → "unknown host"
        // Correct: 192.168.1.10:1433;instance=SQLEXPRESS
        val base = "jdbc:jtds:sqlserver://$serverIp:$port/$databaseName;ssl=off"
        return if (instanceName.isNotBlank()) "$base;instance=$instanceName" else base
    }

    val isConfigured: Boolean get() = serverIp.isNotBlank() && username.isNotBlank()
}
