package com.fastpos.android.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Double.formatCurrency(symbol: String = "Rs."): String =
    "$symbol ${"%,.2f".format(this)}"

fun Date.formatDateTime(): String =
    SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(this)

fun Date.formatTime(): String =
    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(this)

fun Date.formatDate(): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(this)

fun String.orderStatusColor(): Long = when (this) {
    "New"           -> 0xFF2196F3
    "Held"          -> 0xFFFF9800
    "SentToKitchen" -> 0xFF9C27B0
    "Ready"         -> 0xFF4CAF50
    "Completed"     -> 0xFF607D8B
    "Cancelled"     -> 0xFFF44336
    else            -> 0xFF607D8B
}

fun String.paymentStatusColor(): Long = when (this) {
    "Paid"    -> 0xFF4CAF50
    "Partial" -> 0xFFFF9800
    "Unpaid"  -> 0xFFF44336
    else      -> 0xFF607D8B
}
