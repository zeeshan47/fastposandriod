package com.fastpos.android.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager

object SmsHelper {

    fun sendSms(context: Context, phone: String, message: String): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val manager = SmsManager.getDefault()
            val parts = manager.divideMessage(message)
            if (parts.size == 1) {
                manager.sendTextMessage(phone.trim(), null, message, null, null)
            } else {
                manager.sendMultipartTextMessage(phone.trim(), null, parts, null, null)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun sendWhatsApp(context: Context, phone: String, message: String): Boolean {
        val clean = phone.trim().replace(Regex("[\\s\\-()]"), "")
        return try {
            val uri = Uri.parse("whatsapp://send?phone=$clean&text=${Uri.encode(message)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (_: Exception) {
            // WhatsApp not installed — fall back to generic share link
            try {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=$clean&text=${Uri.encode(message)}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            } catch (_: Exception) { false }
        }
    }

    fun formatTemplate(
        template:     String,
        orderNo:      String,
        customerName: String,
        total:        String,
        orderType:    String
    ): String = template
        .replace("{orderNo}",      orderNo)
        .replace("{customerName}", customerName)
        .replace("{total}",        total)
        .replace("{type}",         orderType)
}
