package com.fastpos.android.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fastpos.android.data.database.ConnectionConfig
import com.fastpos.android.data.models.KitchenPrinterConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("fastpos_prefs")

@Singleton
class PreferencesManager @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private val KEY_SERVER_IP       = stringPreferencesKey("server_ip")
        private val KEY_PORT            = intPreferencesKey("port")
        private val KEY_INSTANCE        = stringPreferencesKey("instance")
        private val KEY_DB_NAME         = stringPreferencesKey("db_name")
        private val KEY_USERNAME        = stringPreferencesKey("db_username")
        private val KEY_PASSWORD        = stringPreferencesKey("db_password")
        private val KEY_PRINTER_ADDRESS  = stringPreferencesKey("printer_address")
        private val KEY_PRINTER_NAME     = stringPreferencesKey("printer_name")
        private val KEY_KITCHEN_PRINTERS = stringPreferencesKey("kitchen_printers")
        private val KEY_RECEIPT_PRINTER_TYPE = stringPreferencesKey("receipt_printer_type")
        private val KEY_RECEIPT_NET_IP        = stringPreferencesKey("receipt_net_ip")
        private val KEY_RECEIPT_NET_PORT      = intPreferencesKey("receipt_net_port")
        private val KEY_RECEIPT_NET_NAME      = stringPreferencesKey("receipt_net_name")
        private val KEY_RECEIPT_NET_PAPER_TYPE = stringPreferencesKey("receipt_net_paper_type")
        private val KEY_AUTO_PRINT           = booleanPreferencesKey("auto_print_receipt")
        private val KEY_AUTO_DRAWER          = booleanPreferencesKey("auto_open_drawer")
        private val KEY_PINNED_IDS           = stringPreferencesKey("pinned_product_ids")
        private val KEY_RECEIPT_HEADER       = stringPreferencesKey("receipt_header")
        private val KEY_RECEIPT_FOOTER       = stringPreferencesKey("receipt_footer")
        private val KEY_CUSTOM_PAYMENT_METHODS = stringPreferencesKey("custom_payment_methods")
        val KEY_DB_MODE                        = stringPreferencesKey("db_mode")
        private val KEY_KITCHEN_STATION        = stringPreferencesKey("kitchen_station")
        private val KEY_MANAGER_PIN_HASH       = stringPreferencesKey("manager_pin_hash")
        private val KEY_REQUIRE_MANAGER_PIN    = booleanPreferencesKey("require_manager_pin")
        private val KEY_PEER_HOST              = stringPreferencesKey("peer_host")
        private val KEY_PEER_PORT              = intPreferencesKey("peer_port")
        private val KEY_PEER_SERVER_ENABLED    = booleanPreferencesKey("peer_server_enabled")

        // License
        private val KEY_LICENSE_KEY  = stringPreferencesKey("license_key")
        private val KEY_TRIAL_START  = stringPreferencesKey("trial_start_utc")
        private val KEY_TRIAL_HMAC   = stringPreferencesKey("trial_integrity_hmac")

        // Appearance
        val KEY_THEME_MODE   = stringPreferencesKey("theme_mode")    // "Dark" | "Light" | "System"
        val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")  // "Orange" | "Teal" | "Blue" | "Green" | "Purple"

        // Messaging modes: "Off" | "Device" | "Api"
        private val KEY_SMS_MODE             = stringPreferencesKey("sms_mode")
        private val KEY_WHATSAPP_MODE        = stringPreferencesKey("whatsapp_mode")
        private val KEY_SMS_TMPL_DELIVERY    = stringPreferencesKey("sms_tmpl_delivery")
        private val KEY_SMS_TMPL_TAKEAWAY    = stringPreferencesKey("sms_tmpl_takeaway")
        private val KEY_SMS_TMPL_DINEIN      = stringPreferencesKey("sms_tmpl_dinein")
        private val KEY_SMS_TMPL_OTHER       = stringPreferencesKey("sms_tmpl_other")

        const val DEFAULT_SMS_DELIVERY = "Hi {customerName}, your order {orderNo} is confirmed. Total: {total}. Delivery on the way!"
        const val DEFAULT_SMS_TAKEAWAY = "Hi {customerName}, your order {orderNo} is ready for pickup. Total: {total}."
        const val DEFAULT_SMS_DINEIN   = "Thank you {customerName}! Order {orderNo} received. Total: {total}. Enjoy your meal!"
        const val DEFAULT_SMS_OTHER    = "Your order {orderNo} has been received. Total: {total}. Thank you!"
    }

    private val gson = Gson()

    val connectionConfig: Flow<ConnectionConfig> = context.dataStore.data.map { prefs ->
        ConnectionConfig(
            serverIp     = prefs[KEY_SERVER_IP] ?: "",
            port         = prefs[KEY_PORT]      ?: 1433,
            instanceName = prefs[KEY_INSTANCE]  ?: "",
            databaseName = prefs[KEY_DB_NAME]   ?: "FASTPOSDB",
            username     = prefs[KEY_USERNAME]  ?: "",
            password     = prefs[KEY_PASSWORD]  ?: ""
        )
    }

    suspend fun saveConnectionConfig(cfg: ConnectionConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVER_IP] = cfg.serverIp
            prefs[KEY_PORT]      = cfg.port
            prefs[KEY_INSTANCE]  = cfg.instanceName
            prefs[KEY_DB_NAME]   = cfg.databaseName
            prefs[KEY_USERNAME]  = cfg.username
            prefs[KEY_PASSWORD]  = cfg.password
        }
    }

    suspend fun clearConnectionConfig() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_SERVER_IP)
            prefs.remove(KEY_PORT)
            prefs.remove(KEY_INSTANCE)
            prefs.remove(KEY_DB_NAME)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_PASSWORD)
        }
    }

    val savedPrinterAddress: kotlinx.coroutines.flow.Flow<String> =
        context.dataStore.data.map { it[KEY_PRINTER_ADDRESS] ?: "" }

    val savedPrinterName: kotlinx.coroutines.flow.Flow<String> =
        context.dataStore.data.map { it[KEY_PRINTER_NAME] ?: "" }

    suspend fun savePrinter(address: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PRINTER_ADDRESS] = address
            prefs[KEY_PRINTER_NAME]    = name
        }
    }

    suspend fun clearPrinter() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_PRINTER_ADDRESS)
            prefs.remove(KEY_PRINTER_NAME)
        }
    }

    val receiptPrinterType:    Flow<String> = context.dataStore.data.map { it[KEY_RECEIPT_PRINTER_TYPE]     ?: "Bluetooth" }
    val receiptNetIp:          Flow<String> = context.dataStore.data.map { it[KEY_RECEIPT_NET_IP]            ?: "" }
    val receiptNetPort:        Flow<Int>    = context.dataStore.data.map { it[KEY_RECEIPT_NET_PORT]           ?: 9100 }
    val receiptNetName:        Flow<String> = context.dataStore.data.map { it[KEY_RECEIPT_NET_NAME]           ?: "" }
    val receiptNetPaperType:   Flow<String> = context.dataStore.data.map { it[KEY_RECEIPT_NET_PAPER_TYPE]    ?: "Thermal" }

    suspend fun saveReceiptPrinterType(type: String) {
        context.dataStore.edit { it[KEY_RECEIPT_PRINTER_TYPE] = type }
    }

    suspend fun saveReceiptNetworkPrinter(ip: String, port: Int, name: String) {
        context.dataStore.edit {
            it[KEY_RECEIPT_NET_IP]   = ip
            it[KEY_RECEIPT_NET_PORT] = port
            it[KEY_RECEIPT_NET_NAME] = name
        }
    }

    suspend fun clearReceiptNetworkPrinter() {
        context.dataStore.edit {
            it.remove(KEY_RECEIPT_NET_IP)
            it.remove(KEY_RECEIPT_NET_PORT)
            it.remove(KEY_RECEIPT_NET_NAME)
        }
    }

    suspend fun saveReceiptNetPaperType(type: String) {
        context.dataStore.edit { it[KEY_RECEIPT_NET_PAPER_TYPE] = type }
    }

    val kitchenPrinters: Flow<List<KitchenPrinterConfig>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_KITCHEN_PRINTERS] ?: return@map emptyList()
        try {
            val type = object : TypeToken<List<KitchenPrinterConfig>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    suspend fun saveKitchenPrinters(configs: List<KitchenPrinterConfig>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_KITCHEN_PRINTERS] = gson.toJson(configs)
        }
    }

    val autoPrintReceipt: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_PRINT] ?: false }

    suspend fun saveAutoPrintReceipt(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_PRINT] = enabled }
    }

    val autoOpenDrawer: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_DRAWER] ?: false }

    suspend fun saveAutoOpenDrawer(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_DRAWER] = enabled }
    }

    val pinnedProductIds: Flow<Set<Int>> = context.dataStore.data.map { prefs ->
        prefs[KEY_PINNED_IDS]
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet() ?: emptySet()
    }

    suspend fun savePinnedProductIds(ids: Set<Int>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PINNED_IDS] = ids.joinToString(",")
        }
    }

    val receiptHeader: Flow<String> = context.dataStore.data.map { it[KEY_RECEIPT_HEADER] ?: "" }
    val receiptFooter: Flow<String> = context.dataStore.data.map { it[KEY_RECEIPT_FOOTER] ?: "Thank you for your visit! Please come again." }

    suspend fun saveReceiptHeader(text: String) {
        context.dataStore.edit { prefs -> prefs[KEY_RECEIPT_HEADER] = text }
    }

    suspend fun saveReceiptFooter(text: String) {
        context.dataStore.edit { prefs -> prefs[KEY_RECEIPT_FOOTER] = text }
    }

    val customPaymentMethods: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_PAYMENT_METHODS]
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun saveCustomPaymentMethods(methods: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_PAYMENT_METHODS] = methods.joinToString("|")
        }
    }

    val kitchenStation: Flow<String> = context.dataStore.data.map { it[KEY_KITCHEN_STATION] ?: "" }

    suspend fun saveKitchenStation(station: String) {
        context.dataStore.edit { prefs -> prefs[KEY_KITCHEN_STATION] = station }
    }

    val dbMode: kotlinx.coroutines.flow.Flow<String> =
        context.dataStore.data.map { it[KEY_DB_MODE] ?: "" }

    suspend fun setDbMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[KEY_DB_MODE] = mode }
    }

    suspend fun clearDbMode() {
        context.dataStore.edit { prefs -> prefs.remove(KEY_DB_MODE) }
    }

    val managerPinHash: Flow<String> = context.dataStore.data.map { it[KEY_MANAGER_PIN_HASH] ?: "" }
    val requireManagerPin: Flow<Boolean> = context.dataStore.data.map { it[KEY_REQUIRE_MANAGER_PIN] ?: false }

    suspend fun saveManagerPinHash(hash: String) {
        context.dataStore.edit { it[KEY_MANAGER_PIN_HASH] = hash }
    }

    suspend fun saveRequireManagerPin(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REQUIRE_MANAGER_PIN] = enabled }
    }

    val peerHost:          Flow<String>  = context.dataStore.data.map { it[KEY_PEER_HOST]           ?: "" }
    val peerPort:          Flow<Int>     = context.dataStore.data.map { it[KEY_PEER_PORT]           ?: 7001 }
    val peerServerEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_PEER_SERVER_ENABLED] ?: false }

    suspend fun savePeerConfig(host: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PEER_HOST] = host
            prefs[KEY_PEER_PORT] = port
        }
    }

    suspend fun savePeerServerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PEER_SERVER_ENABLED] = enabled }
    }

    private val KEY_PAPER_WIDTH = intPreferencesKey("paper_width_chars")
    val paperWidth: Flow<Int> = context.dataStore.data.map { it[KEY_PAPER_WIDTH] ?: 32 }
    suspend fun savePaperWidth(width: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_PAPER_WIDTH] = width }
    }

    private val KEY_CUSTOM_EXPENSE_TYPES = stringPreferencesKey("custom_expense_types")
    val customExpenseTypes: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_EXPENSE_TYPES]
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() } ?: emptyList()
    }
    suspend fun saveCustomExpenseTypes(types: List<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CUSTOM_EXPENSE_TYPES] = types.joinToString("|")
        }
    }

    private val KEY_BILL_PRINT_MODE = stringPreferencesKey("bill_print_mode")
    val billPrintMode: Flow<String> = context.dataStore.data.map { it[KEY_BILL_PRINT_MODE] ?: "Silent" }
    suspend fun saveBillPrintMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[KEY_BILL_PRINT_MODE] = mode }
    }

    private val KEY_KOT_PRINT_MODE = stringPreferencesKey("kot_print_mode")
    val kotPrintMode: Flow<String> = context.dataStore.data.map { it[KEY_KOT_PRINT_MODE] ?: "Silent" }
    suspend fun saveKotPrintMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[KEY_KOT_PRINT_MODE] = mode }
    }

    private val KEY_AUTO_PRINT_TAKEAWAY_TOKEN = booleanPreferencesKey("auto_print_takeaway_token")
    val autoPrintTakeawayToken: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_PRINT_TAKEAWAY_TOKEN] ?: true }
    suspend fun saveAutoPrintTakeawayToken(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_PRINT_TAKEAWAY_TOKEN] = enabled }
    }

    private val KEY_BACKUP_FOLDER = stringPreferencesKey("backup_folder")
    val backupFolder: Flow<String> = context.dataStore.data.map { it[KEY_BACKUP_FOLDER] ?: "C:\\FastPOS_Backups" }
    suspend fun saveBackupFolder(path: String) {
        context.dataStore.edit { prefs -> prefs[KEY_BACKUP_FOLDER] = path }
    }

    // ── License ──────────────────────────────────────────────────────────────
    val licenseKey:  Flow<String> = context.dataStore.data.map { it[KEY_LICENSE_KEY]  ?: "" }
    val trialStart:  Flow<String> = context.dataStore.data.map { it[KEY_TRIAL_START]  ?: "" }
    val trialHmac:   Flow<String> = context.dataStore.data.map { it[KEY_TRIAL_HMAC]   ?: "" }

    suspend fun saveLicenseKey(key: String) {
        context.dataStore.edit { it[KEY_LICENSE_KEY] = key }
    }

    suspend fun saveTrialStart(date: String) {
        context.dataStore.edit { it[KEY_TRIAL_START] = date }
    }

    suspend fun saveTrialHmac(hmac: String) {
        context.dataStore.edit { it[KEY_TRIAL_HMAC] = hmac }
    }

    suspend fun clearTrialData() {
        context.dataStore.edit {
            it.remove(KEY_TRIAL_START)
            it.remove(KEY_TRIAL_HMAC)
        }
    }

    // ── Messaging modes ──────────────────────────────────────────────────────
    val smsMode:             Flow<String>  = context.dataStore.data.map { it[KEY_SMS_MODE]          ?: "Off" }
    val whatsappMode:        Flow<String>  = context.dataStore.data.map { it[KEY_WHATSAPP_MODE]     ?: "Off" }
    val smsTemplateDelivery: Flow<String>  = context.dataStore.data.map { it[KEY_SMS_TMPL_DELIVERY] ?: DEFAULT_SMS_DELIVERY }
    val smsTemplateTakeaway: Flow<String>  = context.dataStore.data.map { it[KEY_SMS_TMPL_TAKEAWAY] ?: DEFAULT_SMS_TAKEAWAY }
    val smsTemplateDineIn:   Flow<String>  = context.dataStore.data.map { it[KEY_SMS_TMPL_DINEIN]   ?: DEFAULT_SMS_DINEIN }
    val smsTemplateOther:    Flow<String>  = context.dataStore.data.map { it[KEY_SMS_TMPL_OTHER]    ?: DEFAULT_SMS_OTHER }

    suspend fun saveSmsMode(mode: String) {
        context.dataStore.edit { it[KEY_SMS_MODE] = mode }
    }

    suspend fun saveWhatsappMode(mode: String) {
        context.dataStore.edit { it[KEY_WHATSAPP_MODE] = mode }
    }

    suspend fun saveSmsTemplate(type: String, text: String) {
        context.dataStore.edit { prefs ->
            when (type) {
                "Delivery" -> prefs[KEY_SMS_TMPL_DELIVERY] = text
                "Takeaway" -> prefs[KEY_SMS_TMPL_TAKEAWAY] = text
                "DineIn"   -> prefs[KEY_SMS_TMPL_DINEIN]   = text
                else       -> prefs[KEY_SMS_TMPL_OTHER]    = text
            }
        }
    }

    private val KEY_ACTIVE_BRANCH_ID   = intPreferencesKey("active_branch_id")
    private val KEY_ACTIVE_BRANCH_NAME = stringPreferencesKey("active_branch_name")

    val activeBranchId:   Flow<Int>    = context.dataStore.data.map { it[KEY_ACTIVE_BRANCH_ID]   ?: 1 }
    val activeBranchName: Flow<String> = context.dataStore.data.map { it[KEY_ACTIVE_BRANCH_NAME] ?: "Main Branch" }

    suspend fun saveActiveBranch(id: Int, name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_BRANCH_ID]   = id
            prefs[KEY_ACTIVE_BRANCH_NAME] = name
        }
    }

    // ── Raast / Payment QR ───────────────────────────────────────────────────
    private val KEY_RAAST_ID = stringPreferencesKey("raast_id")
    val raastId: Flow<String> = context.dataStore.data.map { it[KEY_RAAST_ID] ?: "" }
    suspend fun saveRaastId(id: String) {
        context.dataStore.edit { it[KEY_RAAST_ID] = id.trim() }
    }

    // ── Appearance ───────────────────────────────────────────────────────────
    val themeMode:   Flow<String> = context.dataStore.data.map { it[KEY_THEME_MODE]   ?: "Black" }
    val accentColor: Flow<String> = context.dataStore.data.map { it[KEY_ACCENT_COLOR] ?: "Orange" }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun saveAccentColor(color: String) {
        context.dataStore.edit { it[KEY_ACCENT_COLOR] = color }
    }

}
