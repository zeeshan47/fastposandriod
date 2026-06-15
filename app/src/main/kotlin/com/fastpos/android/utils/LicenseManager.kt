package com.fastpos.android.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Base64
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class LicenseStatus { Valid, Trial, TrialExpired, Invalid, Tampered }

data class LicenseInfo(
    val status:      LicenseStatus,
    val licenseType: String = "",
    val daysLeft:    Int    = 0,
    val machineId:   String = "",
    val message:     String = ""
)

object LicenseManager {
    // HMAC key stored as XOR-encoded bytes (mask = 0x5A) — never a plain string in DEX
    private val K1 = byteArrayOf(0x1C, 0x1B, 0x09, 0x0E, 0x0A, 0x15, 0x09, 0x79)
    private val K2 = byteArrayOf(0x68, 0x6A, 0x68, 0x6E, 0x7E, 0x09, 0x3F, 0x39)
    private val K3 = byteArrayOf(0x28, 0x3F, 0x2E, 0x11, 0x3F, 0x23, 0x7B, 0x02)
    private val K4 = byteArrayOf(0x00, 0x63, 0x2B, 0x16, 0x6D, 0x37, 0x0A)

    private const val TRIAL_DAYS  = 4
    private val DATE_FMT          = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun todayStr(): String = DATE_FMT.format(Date())

    private fun toEpochDay(dateStr: String): Long =
        DATE_FMT.parse(dateStr)!!.time / 86_400_000L

    private fun getHmacKey(): ByteArray {
        val raw = K1 + K2 + K3 + K4
        return ByteArray(raw.size) { i -> (raw[i].toInt() xor 0x5A).toByte() }
    }

    fun getMachineId(context: Context): String {
        val androidId  = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val model      = Build.MODEL      ?: "device"
        val board      = Build.BOARD      ?: ""
        val hardware   = Build.HARDWARE   ?: ""
        val fingerprint = Build.FINGERPRINT.take(32)
        val combined   = "$androidId|$model|$board|$hardware|$fingerprint"
        val hash       = MessageDigest.getInstance("SHA-256").digest(combined.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
            .replace("/", "A").replace("+", "B").replace("=", "")
            .substring(0, 20).uppercase()
    }

    private fun computeHmac(data: String): String {
        val key  = SecretKeySpec(getHmacKey(), "HmacSHA256")
        val mac  = Mac.getInstance("HmacSHA256").also { it.init(key) }
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
            .replace("/", "A").replace("+", "B").replace("=", "")
            .substring(0, 16)
    }

    // Integrity HMAC for trial start date — prevents manual date rollback
    private fun trialIntegrityHmac(machineId: String, trialStart: String): String =
        computeHmac("TRIAL:$machineId:$trialStart:FASTPOS_TRIAL_LOCK")

    fun validateKey(key: String, machineId: String): Pair<Boolean, LicenseInfo> {
        return try {
            val parts = key.trim().split("-")
            if (parts.size != 3 || parts[0] != "FP")
                return Pair(false, LicenseInfo(LicenseStatus.Invalid, message = "Invalid key format."))

            val payloadBytes = try { Base64.decode(parts[1], Base64.NO_WRAP) }
                catch (_: Exception) { return Pair(false, LicenseInfo(LicenseStatus.Invalid, message = "Corrupt key payload.")) }
            val payload      = String(payloadBytes, Charsets.UTF_8)

            val expectedHmac = computeHmac(payload)
            if (expectedHmac != parts[2])
                return Pair(false, LicenseInfo(LicenseStatus.Tampered, message = "Key signature mismatch — key may be tampered."))

            val fields = payload.split("|")
            if (fields.size < 3)
                return Pair(false, LicenseInfo(LicenseStatus.Invalid, message = "Invalid key payload structure."))

            val keyMachineId = fields[0]
            val licenseType  = fields[1]
            val expiryStr    = fields[2]

            if (keyMachineId != machineId)
                return Pair(false, LicenseInfo(LicenseStatus.Invalid, machineId = machineId,
                    message = "This license key is for a different device.\nYour Device ID: $machineId"))

            if (expiryStr != "Permanent") {
                val expiryDay = toEpochDay(expiryStr)
                val todayDay  = toEpochDay(todayStr())
                if (todayDay > expiryDay) {
                    val ago = todayDay - expiryDay
                    return Pair(false, LicenseInfo(LicenseStatus.TrialExpired, licenseType, 0, machineId,
                        "License expired $ago day(s) ago."))
                }
                val daysLeft = (expiryDay - todayDay).toInt()
                return Pair(true, LicenseInfo(LicenseStatus.Valid, licenseType, daysLeft, machineId,
                    "License valid — $daysLeft day(s) remaining."))
            }

            Pair(true, LicenseInfo(LicenseStatus.Valid, licenseType, Int.MAX_VALUE, machineId,
                "Permanent license — no expiry."))
        } catch (e: Exception) {
            Pair(false, LicenseInfo(LicenseStatus.Invalid, message = "Validation error: ${e.message}"))
        }
    }

    suspend fun getCurrentLicense(context: Context, prefs: PreferencesManager): LicenseInfo {
        val machineId = getMachineId(context)

        val savedKey = prefs.licenseKey.first().trim()
        if (savedKey.isNotBlank()) {
            val (valid, info) = validateKey(savedKey, machineId)
            if (valid) return info.copy(machineId = machineId)
            if (info.status == LicenseStatus.TrialExpired)
                return info.copy(machineId = machineId)
        }

        return checkBuiltInTrial(prefs, machineId)
    }

    private suspend fun checkBuiltInTrial(prefs: PreferencesManager, machineId: String): LicenseInfo {
        val trialStartStr = prefs.trialStart.first()
        val storedHmac    = prefs.trialHmac.first()

        // Fresh install — start trial and write integrity HMAC
        if (trialStartStr.isBlank() && storedHmac.isBlank()) {
            val today = todayStr()
            prefs.saveTrialStart(today)
            prefs.saveTrialHmac(trialIntegrityHmac(machineId, today))
            return LicenseInfo(LicenseStatus.Trial, "Trial", TRIAL_DAYS, machineId,
                "$TRIAL_DAYS-day trial started.")
        }

        // HMAC exists but trial start cleared — tampering detected
        if (trialStartStr.isBlank() && storedHmac.isNotBlank()) {
            return LicenseInfo(LicenseStatus.Tampered, "Trial", 0, machineId,
                "Trial data tampered. Please contact support.")
        }

        // Trial start exists but no HMAC — legacy data (first run after upgrade); seal it
        if (storedHmac.isBlank()) {
            prefs.saveTrialHmac(trialIntegrityHmac(machineId, trialStartStr))
        } else {
            // Both present — verify integrity
            val expectedHmac = trialIntegrityHmac(machineId, trialStartStr)
            if (expectedHmac != storedHmac) {
                return LicenseInfo(LicenseStatus.Tampered, "Trial", 0, machineId,
                    "Trial integrity check failed. Please contact support.")
            }
        }

        return try {
            val trialStartDay = toEpochDay(trialStartStr)
            val todayDay      = toEpochDay(todayStr())
            val daysUsed      = (todayDay - trialStartDay).toInt().coerceAtLeast(0)
            val daysLeft      = TRIAL_DAYS - daysUsed

            if (daysLeft > 0) {
                LicenseInfo(LicenseStatus.Trial, "Trial", daysLeft, machineId,
                    "$daysLeft trial day(s) remaining.")
            } else {
                LicenseInfo(LicenseStatus.TrialExpired, "Trial", 0, machineId,
                    "$TRIAL_DAYS-day trial has expired. Please activate a license.")
            }
        } catch (_: Exception) {
            // Corrupt date — seal a fresh trial start
            val today = todayStr()
            prefs.saveTrialStart(today)
            prefs.saveTrialHmac(trialIntegrityHmac(machineId, today))
            LicenseInfo(LicenseStatus.Trial, "Trial", TRIAL_DAYS, machineId,
                "$TRIAL_DAYS-day trial started.")
        }
    }
}
