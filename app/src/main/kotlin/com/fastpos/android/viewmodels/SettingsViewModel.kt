package com.fastpos.android.viewmodels

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fastpos.android.data.database.DatabaseHelper
import com.fastpos.android.data.models.Category
import com.fastpos.android.data.models.CompanySettings
import com.fastpos.android.data.models.KitchenPrinterConfig
import com.fastpos.android.data.repositories.FbrRepository
import com.fastpos.android.data.repositories.ProductRepository
import com.fastpos.android.data.repositories.SettingsRepository
import com.fastpos.android.utils.FbrApiClient
import com.fastpos.android.utils.BluetoothPrinterHelper
import com.fastpos.android.utils.NetworkPrinterHelper
import com.fastpos.android.utils.PreferencesManager
import com.fastpos.android.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val productRepo:  ProductRepository,
    private val fbrRepo:      FbrRepository,
    private val prefs:        PreferencesManager,
    private val db:           DatabaseHelper,
    val session:              SessionManager
) : ViewModel() {

    private val _settings     = MutableStateFlow(CompanySettings())
    private val _isLoading    = MutableStateFlow(false)
    private val _isSaving     = MutableStateFlow(false)
    private val _message      = MutableStateFlow<String?>(null)
    private val _productCount = MutableStateFlow(0)
    private val _userCount    = MutableStateFlow(0)
    private val _tableCount   = MutableStateFlow(0)
    private val _kitchenPrinters         = MutableStateFlow<List<KitchenPrinterConfig>>(emptyList())
    private val _detectedPrinterNames    = MutableStateFlow<List<String>>(emptyList())
    private val _savedPrinterAddress     = MutableStateFlow("")
    private val _savedPrinterName        = MutableStateFlow("")
    private val _autoPrintReceipt        = MutableStateFlow(false)
    private val _autoOpenDrawer          = MutableStateFlow(false)
    private val _receiptHeader           = MutableStateFlow("")
    private val _receiptFooter           = MutableStateFlow("Thank you for your visit! Please come again.")
    private val _customPaymentMethods    = MutableStateFlow<List<String>>(emptyList())
    private val _customExpenseTypes      = MutableStateFlow<List<String>>(emptyList())
    private val _managerPinHash          = MutableStateFlow("")
    private val _requireManagerPin       = MutableStateFlow(false)
    private val _isLocalMode             = MutableStateFlow(false)
    private val _isDbOpWorking           = MutableStateFlow(false)
    private val _backupUri               = MutableStateFlow<Uri?>(null)
    private val _paperWidth              = MutableStateFlow(32)
    private val _billPrintMode           = MutableStateFlow("Silent")
    private val _kotPrintMode            = MutableStateFlow("Silent")
    private val _autoPrintTakeawayToken  = MutableStateFlow(false)
    private val _isPeerServerRunning     = MutableStateFlow(false)
    private val _localIpAddress          = MutableStateFlow<String?>(null)
    private val _categories              = MutableStateFlow<List<Category>>(emptyList())
    private val _productsForBulk         = MutableStateFlow<List<Pair<Int,String>>>(emptyList())
    private val _receiptPrinterType      = MutableStateFlow("Bluetooth")
    private val _receiptNetIp            = MutableStateFlow("")
    private val _receiptNetPort          = MutableStateFlow(9100)
    private val _receiptNetName          = MutableStateFlow("")
    private val _receiptNetPaperType     = MutableStateFlow("Thermal")
    private val _smsMode                 = MutableStateFlow("Off")
    private val _whatsappMode            = MutableStateFlow("Off")
    private val _smsTemplateDelivery     = MutableStateFlow(PreferencesManager.DEFAULT_SMS_DELIVERY)
    private val _smsTemplateTakeaway     = MutableStateFlow(PreferencesManager.DEFAULT_SMS_TAKEAWAY)
    private val _smsTemplateDineIn       = MutableStateFlow(PreferencesManager.DEFAULT_SMS_DINEIN)
    private val _smsTemplateOther        = MutableStateFlow(PreferencesManager.DEFAULT_SMS_OTHER)
    private val _themeMode               = MutableStateFlow("Black")
    private val _accentColor             = MutableStateFlow("Orange")
    private val _raastId                 = MutableStateFlow("")
    private val _logoData                = MutableStateFlow<ByteArray?>(null)

    val settings:               StateFlow<CompanySettings>            = _settings
    val isLoading:              StateFlow<Boolean>                    = _isLoading
    val isSaving:               StateFlow<Boolean>                    = _isSaving
    val message:                StateFlow<String?>                    = _message
    val productCount:           StateFlow<Int>                        = _productCount
    val userCount:              StateFlow<Int>                        = _userCount
    val tableCount:             StateFlow<Int>                        = _tableCount
    val kitchenPrinters:        StateFlow<List<KitchenPrinterConfig>> = _kitchenPrinters
    val detectedPrinterNames:   StateFlow<List<String>>               = _detectedPrinterNames
    val savedPrinterAddress:    StateFlow<String>                     = _savedPrinterAddress
    val savedPrinterName:       StateFlow<String>                     = _savedPrinterName
    val autoPrintReceipt:       StateFlow<Boolean>                    = _autoPrintReceipt
    val autoOpenDrawer:         StateFlow<Boolean>                    = _autoOpenDrawer
    val receiptHeader:          StateFlow<String>                     = _receiptHeader
    val receiptFooter:          StateFlow<String>                     = _receiptFooter
    val customPaymentMethods:   StateFlow<List<String>>               = _customPaymentMethods
    val customExpenseTypes:     StateFlow<List<String>>               = _customExpenseTypes
    val managerPinHash:         StateFlow<String>                     = _managerPinHash
    val requireManagerPin:      StateFlow<Boolean>                    = _requireManagerPin
    val isLocalMode:            StateFlow<Boolean>                    = _isLocalMode
    val isDbOpWorking:          StateFlow<Boolean>                    = _isDbOpWorking
    val backupUri:              StateFlow<Uri?>                       = _backupUri
    val paperWidth:             StateFlow<Int>                        = _paperWidth
    val billPrintMode:          StateFlow<String>                     = _billPrintMode
    val kotPrintMode:           StateFlow<String>                     = _kotPrintMode
    val autoPrintTakeawayToken: StateFlow<Boolean>                    = _autoPrintTakeawayToken
    val isPeerServerRunning:    StateFlow<Boolean>                    = _isPeerServerRunning
    val localIpAddress:         StateFlow<String?>                    = _localIpAddress
    val categories:             StateFlow<List<Category>>             = _categories
    val productsForBulk:        StateFlow<List<Pair<Int,String>>>     = _productsForBulk
    val receiptPrinterType:     StateFlow<String>                     = _receiptPrinterType
    val receiptNetIp:           StateFlow<String>                     = _receiptNetIp
    val receiptNetPort:         StateFlow<Int>                        = _receiptNetPort
    val receiptNetName:         StateFlow<String>                     = _receiptNetName
    val receiptNetPaperType:    StateFlow<String>                     = _receiptNetPaperType
    val smsMode:                StateFlow<String>                     = _smsMode
    val whatsappMode:           StateFlow<String>                     = _whatsappMode
    val smsTemplateDelivery:    StateFlow<String>                     = _smsTemplateDelivery
    val smsTemplateTakeaway:    StateFlow<String>                     = _smsTemplateTakeaway
    val smsTemplateDineIn:      StateFlow<String>                     = _smsTemplateDineIn
    val smsTemplateOther:       StateFlow<String>                     = _smsTemplateOther
    val themeMode:              StateFlow<String>                     = _themeMode
    val accentColor:            StateFlow<String>                     = _accentColor
    val raastId:                StateFlow<String>                     = _raastId
    val logoData:               StateFlow<ByteArray?>                 = _logoData

    init {
        loadSettings()
        viewModelScope.launch {
            prefs.kitchenPrinters.collect { _kitchenPrinters.value = it }
        }
        viewModelScope.launch {
            prefs.savedPrinterAddress.collect { _savedPrinterAddress.value = it }
        }
        viewModelScope.launch {
            prefs.savedPrinterName.collect { _savedPrinterName.value = it }
        }
        viewModelScope.launch {
            prefs.autoPrintReceipt.collect { _autoPrintReceipt.value = it }
        }
        viewModelScope.launch {
            prefs.autoOpenDrawer.collect { _autoOpenDrawer.value = it }
        }
        viewModelScope.launch {
            prefs.receiptHeader.collect { _receiptHeader.value = it }
        }
        viewModelScope.launch {
            prefs.receiptFooter.collect { _receiptFooter.value = it }
        }
        viewModelScope.launch {
            prefs.customPaymentMethods.collect { _customPaymentMethods.value = it }
        }
        viewModelScope.launch {
            prefs.customExpenseTypes.collect { _customExpenseTypes.value = it }
        }
        viewModelScope.launch {
            prefs.managerPinHash.collect { _managerPinHash.value = it }
        }
        viewModelScope.launch {
            prefs.requireManagerPin.collect { _requireManagerPin.value = it }
        }
        viewModelScope.launch {
            prefs.dbMode.collect { _isLocalMode.value = it == "local" }
        }
        viewModelScope.launch {
            prefs.paperWidth.collect { _paperWidth.value = it }
        }
        viewModelScope.launch {
            prefs.billPrintMode.collect { _billPrintMode.value = it }
        }
        viewModelScope.launch { prefs.kotPrintMode.collect { _kotPrintMode.value = it } }
        viewModelScope.launch { prefs.autoPrintTakeawayToken.collect { _autoPrintTakeawayToken.value = it } }
        viewModelScope.launch { prefs.receiptPrinterType.collect    { _receiptPrinterType.value    = it } }
        viewModelScope.launch { prefs.receiptNetIp.collect           { _receiptNetIp.value          = it } }
        viewModelScope.launch { prefs.receiptNetPort.collect         { _receiptNetPort.value        = it } }
        viewModelScope.launch { prefs.receiptNetName.collect         { _receiptNetName.value        = it } }
        viewModelScope.launch { prefs.receiptNetPaperType.collect    { _receiptNetPaperType.value   = it } }
        viewModelScope.launch { prefs.smsMode.collect             { _smsMode.value             = it } }
        viewModelScope.launch { prefs.whatsappMode.collect        { _whatsappMode.value        = it } }
        viewModelScope.launch { prefs.smsTemplateDelivery.collect { _smsTemplateDelivery.value = it } }
        viewModelScope.launch { prefs.smsTemplateTakeaway.collect { _smsTemplateTakeaway.value = it } }
        viewModelScope.launch { prefs.smsTemplateDineIn.collect   { _smsTemplateDineIn.value   = it } }
        viewModelScope.launch { prefs.smsTemplateOther.collect    { _smsTemplateOther.value    = it } }
        viewModelScope.launch { prefs.themeMode.collect   { _themeMode.value   = it } }
        viewModelScope.launch { prefs.accentColor.collect { _accentColor.value = it } }
        viewModelScope.launch { prefs.raastId.collect     { _raastId.value     = it } }
        _isPeerServerRunning.value = db.isPeerServerRunning()
        _localIpAddress.value = db.getLocalIpAddress()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _settings.value     = settingsRepo.getSettings()
                _logoData.value     = settingsRepo.getLogoData()
                _settings.value     = _settings.value.copy(logoData = _logoData.value)
                _productCount.value = settingsRepo.getProductCount()
                _userCount.value    = settingsRepo.getUserCount()
                _tableCount.value   = settingsRepo.getTableCount()
                _detectedPrinterNames.value = try { productRepo.getDistinctPrinterNames() } catch (_: Exception) { emptyList() }
                _categories.value           = try { productRepo.getCategories(includeInactive = false) } catch (_: Exception) { emptyList() }
                _productsForBulk.value      = try {
                    db.query(
                        "SELECT ProductId, ProductName FROM Products WHERE IsActive=1 AND ISNULL(ProductType,'') != 'Modifier' ORDER BY ProductName"
                    ) { rs -> Pair(rs.getInt("ProductId"), rs.getString("ProductName") ?: "") }
                } catch (_: Exception) { emptyList() }
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun saveSettings(s: CompanySettings) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                settingsRepo.updateSettings(s)
                session.setSettings(s)
                _settings.value = s
                _message.value  = "Settings saved."
            } catch (e: Exception) {
                _message.value = "Failed to save: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveKitchenPrinters(configs: List<KitchenPrinterConfig>) {
        viewModelScope.launch { prefs.saveKitchenPrinters(configs) }
    }

    fun saveReceiptPrinter(address: String, name: String) {
        viewModelScope.launch { prefs.savePrinter(address, name) }
    }

    fun clearReceiptPrinter() {
        viewModelScope.launch {
            prefs.clearPrinter()
            prefs.clearReceiptNetworkPrinter()
        }
    }

    fun testReceiptPrint() {
        viewModelScope.launch {
            val result = if (_receiptPrinterType.value == "Network") {
                NetworkPrinterHelper.testPrint(_receiptNetIp.value, _receiptNetPort.value)
            } else {
                BluetoothPrinterHelper.testPrint(_savedPrinterAddress.value)
            }
            _message.value = if (result.isSuccess) "Test print sent." else "Print failed: ${result.exceptionOrNull()?.message}"
        }
    }

    fun saveReceiptPrinterType(type: String) {
        viewModelScope.launch { prefs.saveReceiptPrinterType(type) }
    }

    fun saveReceiptNetworkPrinter(ip: String, port: Int, name: String) {
        viewModelScope.launch { prefs.saveReceiptNetworkPrinter(ip, port, name) }
    }

    fun saveReceiptNetPaperType(type: String) {
        viewModelScope.launch { prefs.saveReceiptNetPaperType(type) }
    }

    fun testKitchenPrint(ip: String, port: Int) {
        viewModelScope.launch {
            val result = NetworkPrinterHelper.testPrint(ip, port)
            _message.value = if (result.isSuccess) "Test print sent to $ip:$port" else "Print failed: ${result.exceptionOrNull()?.message}"
        }
    }

    fun resetConnection(onDone: () -> Unit) {
        viewModelScope.launch {
            db.deactivateAllModes()
            prefs.clearDbMode()
            prefs.clearConnectionConfig()
            onDone()
        }
    }

    fun setAutoPrintReceipt(enabled: Boolean) {
        viewModelScope.launch { prefs.saveAutoPrintReceipt(enabled) }
    }

    fun setAutoOpenDrawer(enabled: Boolean) {
        viewModelScope.launch { prefs.saveAutoOpenDrawer(enabled) }
    }

    fun setPaperWidth(width: Int) {
        viewModelScope.launch { prefs.savePaperWidth(width) }
    }

    fun setBillPrintMode(mode: String) {
        viewModelScope.launch { prefs.saveBillPrintMode(mode) }
    }

    fun setKotPrintMode(mode: String) {
        viewModelScope.launch { prefs.saveKotPrintMode(mode) }
    }

    fun setAutoPrintTakeawayToken(enabled: Boolean) {
        viewModelScope.launch { prefs.saveAutoPrintTakeawayToken(enabled) }
    }

    fun setReceiptHeader(text: String) {
        viewModelScope.launch { prefs.saveReceiptHeader(text) }
    }

    fun setReceiptFooter(text: String) {
        viewModelScope.launch { prefs.saveReceiptFooter(text) }
    }

    fun addCustomPaymentMethod(method: String) {
        val trimmed = method.trim()
        if (trimmed.isBlank() || _customPaymentMethods.value.any { it.equals(trimmed, ignoreCase = true) }) return
        viewModelScope.launch { prefs.saveCustomPaymentMethods(_customPaymentMethods.value + trimmed) }
    }

    fun removeCustomPaymentMethod(method: String) {
        viewModelScope.launch { prefs.saveCustomPaymentMethods(_customPaymentMethods.value.filter { it != method }) }
    }

    fun addCustomExpenseType(type: String) {
        val trimmed = type.trim()
        if (trimmed.isBlank()) return
        val baseTypes = listOf("Utilities", "Salary", "Supplies", "Maintenance", "Fuel", "Cleaning", "Other")
        if (baseTypes.any { it.equals(trimmed, ignoreCase = true) }) return
        if (_customExpenseTypes.value.any { it.equals(trimmed, ignoreCase = true) }) return
        viewModelScope.launch { prefs.saveCustomExpenseTypes(_customExpenseTypes.value + trimmed) }
    }

    fun removeCustomExpenseType(type: String) {
        viewModelScope.launch { prefs.saveCustomExpenseTypes(_customExpenseTypes.value.filter { it != type }) }
    }

    fun backupLocalDb(context: Context) {
        viewModelScope.launch {
            _isDbOpWorking.value = true
            _message.value = null
            try {
                val src      = db.getLocalDbPath()
                val ts       = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val fileName = "fastpos_backup_$ts.db"
                val shareUri: Uri = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ — MediaStore Downloads, no permission required
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                            put(MediaStore.Downloads.RELATIVE_PATH, "Download/FastPOS")
                        }
                        val uri = context.contentResolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
                        ) ?: error("MediaStore insert failed")
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            src.inputStream().use { it.copyTo(out) }
                        }
                        uri
                    } else {
                        // Android 7–9 — public Downloads folder (requires WRITE_EXTERNAL_STORAGE)
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "FastPOS"
                        )
                        dir.mkdirs()
                        val dest = File(dir, fileName)
                        src.copyTo(dest, overwrite = true)
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
                    }
                }
                _backupUri.value = shareUri
                _message.value = "Backup saved to Downloads/FastPOS/$fileName"
            } catch (e: Exception) {
                _message.value = "Backup failed: ${e.message}"
            } finally {
                _isDbOpWorking.value = false
            }
        }
    }

    fun cleanLocalDb(context: Context, onDone: () -> Unit) {
        viewModelScope.launch {
            _isDbOpWorking.value = true
            _message.value = null
            try {
                db.closeLocalDb()
                withContext(Dispatchers.IO) {
                    val dbFile = db.getLocalDbPath()
                    listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach { it.delete() }
                }
                db.reopenLocalDb()
                _message.value = "Database cleaned successfully."
                onDone()
            } catch (e: Exception) {
                _message.value = "Clean failed: ${e.message}"
                runCatching { db.reopenLocalDb() }
            } finally {
                _isDbOpWorking.value = false
            }
        }
    }

    fun restoreLocalDb(context: Context, uri: Uri, onDone: () -> Unit) {
        viewModelScope.launch {
            _isDbOpWorking.value = true
            _message.value = null
            try {
                db.closeLocalDb()
                withContext(Dispatchers.IO) {
                    val dbFile = db.getLocalDbPath()
                    listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).forEach { it.delete() }
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dbFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw Exception("Cannot open selected file.")
                }
                _message.value = "Restore complete. Restarting…"
                onDone()
            } catch (e: Exception) {
                _message.value = "Restore failed: ${e.message}"
                runCatching { db.reopenLocalDb() }
            } finally {
                _isDbOpWorking.value = false
            }
        }
    }

    fun clearBackupUri() { _backupUri.value = null }

    fun startPeerServer(port: Int = 7001) {
        db.startPeerServer(port)
        _isPeerServerRunning.value = db.isPeerServerRunning()
        if (_isPeerServerRunning.value) {
            viewModelScope.launch { prefs.savePeerServerEnabled(true) }
            _message.value = "Database server started on port $port."
        } else {
            _message.value = "Failed to start server."
        }
    }

    fun stopPeerServer() {
        db.stopPeerServer()
        _isPeerServerRunning.value = false
        viewModelScope.launch { prefs.savePeerServerEnabled(false) }
        _message.value = "Database server stopped."
    }

    fun refreshServerState() {
        _isPeerServerRunning.value = db.isPeerServerRunning()
        _localIpAddress.value = db.getLocalIpAddress()
    }

    fun bulkAssignPrinterToCategory(printerName: String, categoryId: Int?) {
        viewModelScope.launch {
            try {
                if (categoryId == null) {
                    db.execute("UPDATE Products SET KitchenPrinterId=(SELECT MIN(PrinterId) FROM KitchenPrinters WHERE PrinterName=? AND IsActive=1) WHERE IsActive=1", listOf(printerName.ifBlank { null }))
                } else {
                    db.execute("UPDATE Products SET KitchenPrinterId=(SELECT MIN(PrinterId) FROM KitchenPrinters WHERE PrinterName=? AND IsActive=1) WHERE CategoryId=? AND IsActive=1", listOf(printerName.ifBlank { null }, categoryId))
                }
                val catLabel = if (categoryId == null) "all categories" else
                    _categories.value.firstOrNull { it.categoryId == categoryId }?.categoryName ?: "selected category"
                _message.value = "Printer assigned to $catLabel."
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun bulkAssignPrinterToProduct(printerName: String, productId: Int) {
        viewModelScope.launch {
            try {
                db.execute("UPDATE Products SET KitchenPrinterId=(SELECT MIN(PrinterId) FROM KitchenPrinters WHERE PrinterName=? AND IsActive=1) WHERE ProductId=?", listOf(printerName.ifBlank { null }, productId))
                val name = _productsForBulk.value.firstOrNull { it.first == productId }?.second ?: "selected product"
                _message.value = "Printer '$printerName' assigned to $name."
            } catch (e: Exception) { _message.value = "Failed: ${e.message}" }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { prefs.saveThemeMode(mode) }
    }

    fun setAccentColor(color: String) {
        viewModelScope.launch { prefs.saveAccentColor(color) }
    }

    fun setRaastId(id: String) {
        viewModelScope.launch { prefs.saveRaastId(id) }
    }

    fun saveLogoData(data: ByteArray?) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                settingsRepo.updateLogoData(data)
                _logoData.value = data
                _settings.value = _settings.value.copy(logoData = data)
                session.setSettings(_settings.value)
                _message.value = if (data != null) "Logo saved." else "Logo removed."
            } catch (e: Exception) {
                _message.value = "Failed to save logo: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun setSmsMode(mode: String) {
        viewModelScope.launch { prefs.saveSmsMode(mode) }
    }

    fun setWhatsappMode(mode: String) {
        viewModelScope.launch { prefs.saveWhatsappMode(mode) }
    }

    fun saveSmsTemplate(type: String, text: String) {
        viewModelScope.launch { prefs.saveSmsTemplate(type, text) }
    }

    fun testFbr() {
        val s = _settings.value
        if (!s.fbrEnabled || s.fbrToken.isBlank() || s.fbrNtn.isBlank()) {
            _message.value = "FBR is not configured. Enable FBR and enter Bearer Token and NTN first."
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val ok = FbrApiClient.testConnection(s)
                _message.value = if (ok) "FBR token accepted by server." else "FBR token rejected (HTTP 401). Check your Bearer Token."
            } catch (e: Exception) {
                _message.value = "FBR test failed: ${e.message}"
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun clearMessage() { _message.value = null }

    fun setManagerPin(pin: String) {
        viewModelScope.launch {
            prefs.saveManagerPinHash(if (pin.isBlank()) "" else hashPin(pin))
        }
    }

    fun setRequireManagerPin(enabled: Boolean) {
        viewModelScope.launch { prefs.saveRequireManagerPin(enabled) }
    }

    fun checkManagerPin(pin: String): Boolean {
        val stored = _managerPinHash.value
        if (stored.isBlank()) return true
        return hashPin(pin) == stored
    }

    private fun hashPin(pin: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
