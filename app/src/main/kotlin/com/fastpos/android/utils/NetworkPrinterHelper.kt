package com.fastpos.android.utils

import android.graphics.BitmapFactory
import com.fastpos.android.data.models.CartItem
import com.fastpos.android.data.models.CompanySettings
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.PaymentEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// paperType: null or "Thermal" → ESC/POS bytes; "A4" → plain UTF-8 text + form-feed

object NetworkPrinterHelper {

    private const val A4_LINE_WIDTH = 64   // chars per line on A4 at default 10cpi — 170mm printable ÷ 2.54mm = ~67 chars; 64 gives a safe 3-char right margin

    // ESC/POS command bytes
    private val INIT          = byteArrayOf(0x1B, 0x40)
    private val ALIGN_CENTER  = byteArrayOf(0x1B, 0x61, 0x01)
    private val ALIGN_LEFT    = byteArrayOf(0x1B, 0x61, 0x00)
    private val BOLD_ON       = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF      = byteArrayOf(0x1B, 0x45, 0x00)
    private val DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10)
    private val NORMAL_SIZE   = byteArrayOf(0x1B, 0x21, 0x00)
    private val LF            = byteArrayOf(0x0A)
    private val CUT           = byteArrayOf(0x1D, 0x56, 0x42, 0x00)
    private val FEED_3        = byteArrayOf(0x1B, 0x64, 0x03)

    private const val LOGO_MAX_DOTS = 384

    private fun loadLogoBytes(logoData: ByteArray?): ByteArray {
        if (logoData == null || logoData.isEmpty()) return ByteArray(0)
        return try {
            val bm = BitmapFactory.decodeByteArray(logoData, 0, logoData.size) ?: return ByteArray(0)
            val scale = minOf(1f, LOGO_MAX_DOTS.toFloat() / bm.width)
            var w = (bm.width * scale).toInt()
            if (w % 8 != 0) w -= (w % 8)
            if (w <= 0) return ByteArray(0)
            val h = (bm.height * scale).toInt().coerceAtLeast(1)
            val scaled = android.graphics.Bitmap.createScaledBitmap(bm, w, h, true)
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            val bytesPerRow = w / 8
            val raster = ByteArray(h * bytesPerRow)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val px = pixels[y * w + x]
                    val lum = (0.299 * ((px shr 16) and 0xFF) +
                               0.587 * ((px shr 8)  and 0xFF) +
                               0.114 * ( px         and 0xFF)).toInt()
                    if (lum < 128) raster[y * bytesPerRow + x / 8] =
                        (raster[y * bytesPerRow + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
            val xL = (bytesPerRow and 0xFF).toByte()
            val xH = ((bytesPerRow shr 8) and 0xFF).toByte()
            val yL = (h and 0xFF).toByte()
            val yH = ((h shr 8) and 0xFF).toByte()
            ALIGN_CENTER +
            byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH) + raster +
            LF + ALIGN_LEFT
        } catch (_: Exception) { ByteArray(0) }
    }

    private fun escPosQrCode(data: String): ByteArray {
        if (data.isBlank()) return ByteArray(0)
        val bytes = data.toByteArray(Charsets.UTF_8)
        val storeLen = bytes.size + 3
        val pL = (storeLen and 0xFF).toByte()
        val pH = ((storeLen shr 8) and 0xFF).toByte()
        return ALIGN_CENTER + byteArrayOf(
            0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00,
            0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x05,
            0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x32,
            0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30
        ) + bytes + byteArrayOf(
            0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30
        ) + LF + ALIGN_LEFT
    }

    suspend fun printKitchenTicket(
        ip: String,
        port: Int = 9100,
        orderNo: String,
        tokenNo: String,
        orderType: String,
        tableName: String?,
        items: List<CartItem>,
        stationName: String,
        companyName: String = "",
        waiterName: String? = null,
        notes: String? = null,
        customerName: String? = null,
        lineWidth: Int = 32,
        paperType: String? = null,
        logoData: ByteArray? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = if (paperType?.equals("A4", ignoreCase = true) == true) {
                buildKitchenTicketText(
                    orderNo, tokenNo, orderType, tableName, items, stationName,
                    companyName, waiterName, notes, customerName
                ).toA4Bytes()
            } else {
                buildKitchenTicket(
                    orderNo, tokenNo, orderType, tableName, items, stationName,
                    companyName, waiterName, notes, customerName, lineWidth, logoData
                )
            }
            printRaw(ip, port, data)
        }
    }

    suspend fun testPrint(ip: String, port: Int = 9100): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = buildString {
                append("\n")
                append("  PRINTER TEST  \n")
                append("  $ip:$port  \n")
                append("  Connection OK  \n\n\n")
            }.toByteArray(Charsets.ISO_8859_1)
            printRaw(ip, port, INIT + data + FEED_3 + CUT)
        }
    }

    suspend fun printReceipt(
        ip:                    String,
        port:                  Int                = 9100,
        order:                 Order,
        settings:              CompanySettings,
        pointsEarned:          Int                = 0,
        header:                String             = "",
        footer:                String             = "",
        payments:              List<PaymentEntry> = emptyList(),
        lineWidth:             Int                = 32,
        pointsRedeemed:        Int                = 0,
        customerTotalOrders:   Int                = 0,
        customerLoyaltyPoints: Int                = 0,
        paperType:             String?            = null,
        logoData:              ByteArray?         = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = if (paperType?.equals("A4", ignoreCase = true) == true) {
                BluetoothPrinterHelper.buildReceiptText(
                    order, settings, pointsEarned, header, footer, payments, A4_LINE_WIDTH,
                    pointsRedeemed, customerTotalOrders, customerLoyaltyPoints
                ).toA4Bytes()
            } else {
                BluetoothPrinterHelper.buildReceiptBytes(
                    order, settings, pointsEarned, header, footer, payments, lineWidth,
                    pointsRedeemed, customerTotalOrders, customerLoyaltyPoints, logoData
                )
            }
            printRaw(ip, port, data)
        }
    }

    suspend fun printPreBill(
        ip:        String,
        port:      Int             = 9100,
        order:     Order,
        settings:  CompanySettings,
        lineWidth: Int             = 32,
        paperType: String?         = null,
        logoData:  ByteArray?      = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = if (paperType?.equals("A4", ignoreCase = true) == true) {
                buildPreBillText(order, settings, A4_LINE_WIDTH).toA4Bytes()
            } else {
                BluetoothPrinterHelper.buildPreBillBytes(order, settings, lineWidth, logoData)
            }
            printRaw(ip, port, data)
        }
    }

    suspend fun printTakeawayToken(
        ip: String,
        port: Int = 9100,
        orderNo: String,
        tokenNo: String,
        customerName: String?,
        notes: String?,
        items: List<CartItem>,
        companyName: String,
        lineWidth: Int = 32,
        paperType: String? = null,
        logoData: ByteArray? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = BluetoothPrinterHelper.buildTakeawayTokenBytes(
                orderNo = orderNo, tokenNo = tokenNo, orderType = "Takeaway",
                customerName = customerName, notes = notes, items = items,
                companyName = companyName, lineWidth = lineWidth, logoData = logoData
            )
            printRaw(ip, port, bytes)
        }
    }

    suspend fun printZReport(
        ip:               String,
        port:             Int    = 9100,
        shift:            com.fastpos.android.data.models.Shift,
        summary:          List<com.fastpos.android.data.models.ShiftPaymentSummary>,
        expenses:         List<com.fastpos.android.data.models.Expense>,
        cashTransactions: List<com.fastpos.android.data.models.CashTransaction> = emptyList(),
        salariesPaid:     Double = 0.0,
        advancesPaid:     Double = 0.0,
        sym:              String,
        companyName:      String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val data = BluetoothPrinterHelper.buildZReportBytes(
                shift            = shift,
                summary          = summary,
                expenses         = expenses,
                cashTransactions = cashTransactions,
                salariesPaid     = salariesPaid,
                advancesPaid     = advancesPaid,
                sym              = sym,
                companyName      = companyName
            )
            printRaw(ip, port, data)
        }
    }

    suspend fun printTextReport(ip: String, port: Int = 9100, text: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = buildString {
                    append(text)
                    append("\n\n\n")
                }.toByteArray(Charsets.UTF_8)
                printRaw(ip, port, INIT + bytes + FEED_3 + CUT)
            }
        }

    private fun buildKitchenTicketText(
        orderNo: String,
        tokenNo: String,
        orderType: String,
        tableName: String?,
        items: List<CartItem>,
        stationName: String,
        companyName: String,
        waiterName: String?,
        notes: String?,
        customerName: String?
    ): String {
        val sb = StringBuilder()
        val now = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date())
        val sep  = "=".repeat(A4_LINE_WIDTH)
        val dash = "-".repeat(A4_LINE_WIDTH)

        sb.append("** KITCHEN ORDER **\r\n")
        if (companyName.isNotBlank()) sb.append("$companyName\r\n")
        if (stationName.isNotBlank()) sb.append("[ ${stationName.uppercase()} ]\r\n")
        sb.append("$sep\r\n")
        sb.append("Token: ${tokenNo.ifBlank { "-" }}\r\n")
        sb.append("Order #: ${orderNo.ifBlank { "-" }}\r\n")
        sb.append("Type: ${orderType.ifBlank { "-" }}\r\n")
        if (orderType == "DineIn" && !tableName.isNullOrBlank()) sb.append("Table: $tableName\r\n")
        if (!waiterName.isNullOrBlank()) sb.append("Waiter: $waiterName\r\n")
        if (orderType == "Takeaway" && !customerName.isNullOrBlank()) sb.append("Name: $customerName\r\n")
        sb.append("Time: $now\r\n")
        if (!notes.isNullOrBlank()) {
            sb.append("$dash\r\n")
            sb.append("** ORDER NOTE **\r\n")
            sb.append("$notes\r\n")
        }
        sb.append("$sep\r\n")
        sb.append("ITEMS\r\n")
        sb.append("$dash\r\n")
        for (item in items) {
            val name = item.productName + if (!item.sizeName.isNullOrBlank()) " (${item.sizeName})" else ""
            sb.append("x${item.quantity} $name\r\n")
            for (mod in item.selectedModifiers) sb.append("  + ${mod.modifierName}\r\n")
            if (item.notes.isNotBlank()) sb.append("  ** ${item.notes} **\r\n")
        }
        sb.append("$sep\r\n")
        sb.append("-- END OF ORDER --\r\n\r\n\r\n")

        return sb.toString()
    }

    private fun buildPreBillText(order: Order, settings: CompanySettings, lineWidth: Int = 32): String {
        val sym   = settings.currencySymbol
        val dtFmt = SimpleDateFormat("dd MMM yyyy  hh:mm a", Locale.ENGLISH)
        val sb    = StringBuilder()

        fun twoCol(label: String, value: String) {
            val spaces = lineWidth - label.length - value.length
            if (spaces > 0) sb.appendLine(label + " ".repeat(spaces) + value)
            else sb.appendLine(label.take(lineWidth - value.length - 1) + " " + value)
        }
        fun amtRow(label: String, amount: Double) = twoCol(label, "$sym${"%.2f".format(amount)}")

        val co = settings.companyName
        sb.appendLine(co.padStart((lineWidth + co.length) / 2).take(lineWidth))
        if (settings.address.isNotBlank()) sb.appendLine(settings.address)
        if (settings.phone.isNotBlank())   sb.appendLine("Tel: ${settings.phone}")
        sb.appendLine("=".repeat(lineWidth))
        val pb = "*** PRE-BILL ***"
        sb.appendLine(pb.padStart((lineWidth + pb.length) / 2).take(lineWidth))
        sb.appendLine("=".repeat(lineWidth))
        twoCol("Token:", order.tokenNo)
        twoCol("Order #:", order.orderNo)
        twoCol("Date:", dtFmt.format(order.orderDate))
        twoCol("Type:", order.orderType)
        order.tableName?.takeIf { it.isNotBlank() }?.let { twoCol("Table:", it) }
        order.waiterName?.takeIf { it.isNotBlank() }?.let { twoCol("Waiter:", it) }
        order.customerName?.takeIf { it.isNotBlank() }?.let { twoCol("Customer:", it) }
        sb.appendLine("=".repeat(lineWidth))
        order.notes?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Note: $it") }

        val qtyW = 4; val prW = (lineWidth - qtyW) / 4
        val totW = (lineWidth - qtyW) / 4; val nmW = lineWidth - qtyW - prW - totW
        sb.appendLine("Item".padEnd(nmW) + "Qty".padStart(qtyW) + "Price".padStart(prW) + "Total".padStart(totW))
        sb.appendLine("-".repeat(lineWidth))
        order.items.forEach { item ->
            val rawName = buildString {
                append(item.productNameSnapshot)
                if (!item.sizeNameSnapshot.isNullOrBlank()) append(" (${item.sizeNameSnapshot})")
            }
            sb.appendLine(rawName.take(nmW).padEnd(nmW) +
                "x${"%.0f".format(item.quantity)}".padStart(qtyW) +
                "$sym${"%.2f".format(item.unitPrice)}".padStart(prW) +
                "$sym${"%.2f".format(item.lineTotal)}".padStart(totW))
            item.modifiers.forEach { mod ->
                val mName = "  + ${mod.modifierNameSnapshot}"
                if (mod.extraPrice > 0) {
                    val priceStr = "$sym${"%.2f".format(mod.extraPrice)}"
                    val pad = lineWidth - mName.length - priceStr.length
                    if (pad > 0) sb.appendLine(mName + " ".repeat(pad) + priceStr)
                    else sb.appendLine(mName.take(lineWidth - priceStr.length - 1) + " " + priceStr)
                } else { sb.appendLine(mName.take(lineWidth - 2)) }
            }
            item.notes?.takeIf { it.isNotBlank() }?.let { sb.appendLine("  * $it".take(lineWidth)) }
        }
        sb.appendLine("-".repeat(lineWidth))
        amtRow("Sub-Total:", order.subTotal)
        if (order.discountAmount > 0) amtRow("Discount:", order.discountAmount)
        if (order.taxAmount > 0) amtRow("${settings.taxLabel.ifBlank { "Tax" }} (${"%.1f".format(order.taxPercent)}%):", order.taxAmount)
        if (order.serviceCharges > 0) amtRow("Service Charge:", order.serviceCharges)
        if (order.deliveryCharge > 0) amtRow("Delivery Charge:", order.deliveryCharge)
        if (order.tips > 0) amtRow("Tip:", order.tips)
        sb.appendLine("-".repeat(lineWidth))
        amtRow("TOTAL DUE:", order.grandTotal + order.tips)
        sb.appendLine("=".repeat(lineWidth))
        val pc = "Please pay at the counter"
        sb.appendLine(pc.padStart((lineWidth + pc.length) / 2).take(lineWidth))
        return sb.toString()
    }

    private fun printRaw(ip: String, port: Int, data: ByteArray) {
        Socket(ip, port).use { socket ->
            socket.soTimeout = 5000
            val out: OutputStream = socket.getOutputStream()
            out.write(data)
            out.flush()
        }
    }

    private fun buildKitchenTicket(
        orderNo: String,
        tokenNo: String,
        orderType: String,
        tableName: String?,
        items: List<CartItem>,
        stationName: String,
        companyName: String,
        waiterName: String?,
        notes: String?,
        customerName: String?,
        lineWidth: Int,
        logoData: ByteArray? = null
    ): ByteArray {
        val buf = mutableListOf<ByteArray>()

        fun add(vararg arrays: ByteArray) = arrays.forEach { buf.add(it) }
        fun line(text: String) { buf.add((text + "\n").toByteArray(Charsets.ISO_8859_1)) }
        fun sep()  = line("=".repeat(lineWidth))
        fun dash() = line("-".repeat(lineWidth))

        fun twoCol(left: String, right: String) {
            val gap = (lineWidth - left.length - right.length).coerceAtLeast(1)
            line(left + " ".repeat(gap) + right)
        }

        val timeFmt = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        val now     = timeFmt.format(Date())

        // ── Header ──────────────────────────────────────────────────────────
        add(INIT)
        val logoBytes = loadLogoBytes(logoData)
        if (logoBytes.isNotEmpty()) buf.add(logoBytes)
        add(ALIGN_CENTER)
        add(DOUBLE_HEIGHT, BOLD_ON)
        line("** KITCHEN ORDER **")
        add(NORMAL_SIZE, BOLD_OFF)

        if (companyName.isNotBlank()) line(companyName)
        if (stationName.isNotBlank()) line("[ ${stationName.uppercase()} ]")

        sep()

        // ── Order info ───────────────────────────────────────────────────────
        add(ALIGN_LEFT, BOLD_ON)
        twoCol("Token:", tokenNo.ifBlank { "-" })
        add(BOLD_OFF)
        twoCol("Order #:", orderNo.ifBlank { "-" })
        twoCol("Type:", orderType.ifBlank { "-" })
        if (orderType == "DineIn" && !tableName.isNullOrBlank())
            twoCol("Table:", tableName)
        if (!waiterName.isNullOrBlank())
            twoCol("Waiter:", waiterName)
        if (orderType == "Takeaway" && !customerName.isNullOrBlank()) {
            add(BOLD_ON)
            twoCol("Name:", customerName)
            add(BOLD_OFF)
        }
        twoCol("Time:", now)

        // ── Order note (if any) ──────────────────────────────────────────────
        if (!notes.isNullOrBlank()) {
            dash()
            add(ALIGN_CENTER, BOLD_ON)
            line("** ORDER NOTE **")
            line(notes)
            add(BOLD_OFF, ALIGN_LEFT)
        }

        // ── Items section ────────────────────────────────────────────────────
        sep()
        add(ALIGN_CENTER, BOLD_ON)
        line("ITEMS")
        add(BOLD_OFF, ALIGN_LEFT)
        dash()

        for (item in items) {
            val name = item.productName +
                if (!item.sizeName.isNullOrBlank()) " (${item.sizeName})" else ""
            add(BOLD_ON)
            line("x${item.quantity} $name")
            add(BOLD_OFF)
            for (mod in item.selectedModifiers) {
                line("  + ${mod.modifierName}")
            }
            if (item.notes.isNotBlank()) {
                line("  ** ${item.notes} **")
            }
        }

        sep()
        add(ALIGN_CENTER)
        line("-- END OF ORDER --")
        add(ALIGN_LEFT)

        add(FEED_3, CUT)

        val total = buf.fold(0) { acc, b -> acc + b.size }
        val result = ByteArray(total)
        var pos = 0
        for (b in buf) { b.copyInto(result, pos); pos += b.size }
        return result
    }

    // Normalize any mix of \n / \r\n to strict CRLF, then append a form-feed to
    // eject the page on PCL/laser/inkjet A4 printers.  Plain \n (from Kotlin's
    // appendLine) leaves the horizontal print position where it ended — subsequent
    // lines staircase off the right edge and disappear.  \r\n resets to column 0.
    private fun String.toA4Bytes(): ByteArray =
        replace("\r\n", "\n")           // collapse any existing CRLF → LF
            .replace("\n", "\r\n")      // re-expand every LF → CRLF
            .plus("\r\n")         // trailing CRLF + form-feed to eject page
            .toByteArray(Charsets.UTF_8)

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val result = ByteArray(size + other.size)
        copyInto(result)
        other.copyInto(result, size)
        return result
    }
}
