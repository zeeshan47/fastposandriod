package com.fastpos.android.tools

import org.junit.Test
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Run this test from Android Studio to generate a valid license key.
 * Right-click the function name → "Run 'generateLicenseKey'"
 *
 * HOW TO USE:
 *   1. Open the app on the device → go to Activation screen.
 *   2. Copy the Device ID shown there.
 *   3. Paste it into MACHINE_ID below.
 *   4. Set LICENSE_TYPE and EXPIRY as needed.
 *   5. Run the test — key prints in the Run console.
 *   6. Paste the key into the Activation screen.
 */
class LicenseKeyGenerator {

    // ── EDIT THESE ────────────────────────────────────────────────────────────
    private val MACHINE_ID   = "PASTE_DEVICE_ID_HERE"   // from Activation screen
    private val LICENSE_TYPE = "Standard"                // e.g. Standard, Premium, Lifetime
    private val EXPIRY       = "Permanent"               // "Permanent"  OR  "2027-12-31"
    // ─────────────────────────────────────────────────────────────────────────

    private val HMAC_SECRET  = "FASTPOS#2024\$SecretKey!XZ9qL7mP"

    private fun computeHmac(data: String): String {
        val keySpec = SecretKeySpec(HMAC_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac     = Mac.getInstance("HmacSHA256").also { it.init(keySpec) }
        val raw     = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(raw)
            .replace("/", "A").replace("+", "B").replace("=", "")
            .substring(0, 16)
    }

    @Test
    fun generateLicenseKey() {
        require(MACHINE_ID != "PASTE_DEVICE_ID_HERE") {
            "Set MACHINE_ID to the Device ID shown on the Activation screen first."
        }

        val payload    = "$MACHINE_ID|$LICENSE_TYPE|$EXPIRY"
        val payloadB64 = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        val hmac       = computeHmac(payload)
        val key        = "FP-$payloadB64-$hmac"

        println()
        println("===========================================")
        println("  FastPOS License Key Generator")
        println("===========================================")
        println("  Device ID    : $MACHINE_ID")
        println("  License Type : $LICENSE_TYPE")
        println("  Expiry       : $EXPIRY")
        println("-------------------------------------------")
        println("  KEY: $key")
        println("===========================================")
        println()
    }
}
