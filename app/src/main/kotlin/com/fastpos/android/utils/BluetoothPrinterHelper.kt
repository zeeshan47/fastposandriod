package com.fastpos.android.utils

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.graphics.BitmapFactory
import com.fastpos.android.data.models.CompanySettings
import com.fastpos.android.data.models.Expense
import com.fastpos.android.data.models.Order
import com.fastpos.android.data.models.PayrollRow
import com.fastpos.android.data.models.Shift
import com.fastpos.android.data.models.ShiftPaymentSummary
import com.fastpos.android.data.models.StockItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

object BluetoothPrinterHelper {

    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ESC/POS command bytes
    private val INIT          = byteArrayOf(0x1B, 0x40)
    private val ALIGN_LEFT    = byteArrayOf(0x1B, 0x61, 0x00)
    private val ALIGN_CENTER  = byteArrayOf(0x1B, 0x61, 0x01)
    private val ALIGN_RIGHT   = byteArrayOf(0x1B, 0x61, 0x02)
    private val BOLD_ON       = byteArrayOf(0x1B, 0x45, 0x01)
    private val BOLD_OFF      = byteArrayOf(0x1B, 0x45, 0x00)
    private val DOUBLE_HEIGHT = byteArrayOf(0x1B, 0x21, 0x10)
    private val NORMAL_SIZE   = byteArrayOf(0x1B, 0x21, 0x00)
    private val LF            = byteArrayOf(0x0A)
    private val CUT           = byteArrayOf(0x1D, 0x56, 0x41, 0x10)
    private val FEED_3        = byteArrayOf(0x1B, 0x64, 0x03)

    // Max printable dots for 80mm/203dpi thermal = 576; 384 is conservative for both 58mm and 80mm rolls
    private const val LOGO_MAX_DOTS = 384

    // Converts logo bytes to ESC/POS GS v 0 raster bit-image bytes, centred.
    // Returns empty array (silent skip) if data is null or empty.
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

    // ESC/POS native QR code (GS ( k) — no external library needed.
    private fun escPosQrCode(data: String): ByteArray {
        if (data.isBlank()) return ByteArray(0)
        val bytes = data.toByteArray(Charsets.UTF_8)
        val storeLen = bytes.size + 3
        val pL = (storeLen and 0xFF).toByte()
        val pH = ((storeLen shr 8) and 0xFF).toByte()
        return ALIGN_CENTER + byteArrayOf(
            0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00, // model 2
            0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x05,        // size 5
            0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x32,        // error correction M
            0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30             // store data
        ) + bytes + byteArrayOf(
            0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30         // print QR
        ) + LF + ALIGN_LEFT
    }

    suspend fun testPrint(address: String): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not available"))
            @Suppress("MissingPermission") val device = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission") socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            @Suppress("MissingPermission") adapter.cancelDiscovery()
            @Suppress("MissingPermission") socket.connect()
            val buf = mutableListOf<ByteArray>()
            fun add(vararg b: ByteArray) = b.forEach { buf.add(it) }
            fun text(t: String) = buf.add((t + "\n").toByteArray(Charsets.UTF_8))
            add(INIT, ALIGN_CENTER, BOLD_ON, DOUBLE_HEIGHT)
            text("  PRINTER TEST  ")
            add(NORMAL_SIZE, BOLD_OFF)
            text("  Connection OK  ")
            add(FEED_3, CUT)
            val data = buf.reduce { acc, b -> acc + b }
            socket.outputStream.apply { write(data); flush() }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    fun getPairedPrinters(context: Context): List<BluetoothDevice> {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()
            @Suppress("MissingPermission")
            adapter.bondedDevices.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun printReceipt(
        address:               String,
        order:                 Order,
        settings:              CompanySettings,
        pointsEarned:          Int               = 0,
        header:                String            = "",
        footer:                String            = "",
        payments:              List<com.fastpos.android.data.models.PaymentEntry> = emptyList(),
        lineWidth:             Int               = 32,
        pointsRedeemed:        Int               = 0,
        customerTotalOrders:   Int               = 0,
        customerLoyaltyPoints: Int               = 0,
        logoData:              ByteArray?        = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not available"))
            @Suppress("MissingPermission") val device = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission") socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            @Suppress("MissingPermission") socket.connect()
            val stream = socket.outputStream
            val data   = buildReceiptBytes(order, settings, pointsEarned, header, footer, payments, lineWidth, pointsRedeemed, customerTotalOrders, customerLoyaltyPoints, logoData)
            stream.write(data)
            stream.flush()
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    fun buildReceiptBytes(
        order:                 Order,
        settings:              CompanySettings,
        pointsEarned:          Int               = 0,
        header:                String            = "",
        footer:                String            = "",
        payments:              List<com.fastpos.android.data.models.PaymentEntry> = emptyList(),
        lineWidth:             Int               = 32,
        pointsRedeemed:        Int               = 0,
        customerTotalOrders:   Int               = 0,
        customerLoyaltyPoints: Int               = 0,
        logoData:              ByteArray?        = null
    ): ByteArray {
        val sym   = settings.currencySymbol
        // 4-column layout: name | qty | unit-price | line-total
        val qtyW  = 4
        val prW   = (lineWidth - qtyW) / 4
        val totW  = (lineWidth - qtyW) / 4
        val nmW   = lineWidth - qtyW - prW - totW
        val dtFmt = java.text.SimpleDateFormat("dd MMM yyyy  hh:mm a", java.util.Locale.ENGLISH)

        val buf = mutableListOf<ByteArray>()
        fun add(vararg b: ByteArray) = b.forEach { buf.add(it) }
        fun text(t: String) = buf.add((t + "\n").toByteArray(Charsets.UTF_8))
        fun sepLine(c: Char = '=') = text(c.toString().repeat(lineWidth))
        fun twoCol(label: String, value: String) {
            val spaces = lineWidth - label.length - value.length
            if (spaces > 0) text(label + " ".repeat(spaces) + value)
            else text(label.take(lineWidth - value.length - 1) + " " + value)
        }
        fun amtRow(label: String, amount: Double) = twoCol(label, formatAmt(amount, sym))

        // ── Header ──────────────────────────────────────────────────────────
        add(INIT)
        val logoBytes = loadLogoBytes(logoData ?: settings.logoData)
        if (logoBytes.isNotEmpty()) buf.add(logoBytes)
        add(ALIGN_CENTER, BOLD_ON, DOUBLE_HEIGHT)
        text(settings.companyName)
        add(NORMAL_SIZE, BOLD_OFF)
        if (settings.address.isNotBlank())   text(settings.address)
        if (settings.phone.isNotBlank())     text("Tel: ${settings.phone}")
        if (settings.taxNumber.isNotBlank()) text("NTN: ${settings.taxNumber}")
        if (header.isNotBlank())             header.lines().forEach { text(it) }
        add(ALIGN_LEFT)
        sepLine('=')

        // ── Order info ──────────────────────────────────────────────────────
        twoCol("Order #:", order.orderNo)
        twoCol("Token:",   order.tokenNo)
        twoCol("Date:",    dtFmt.format(order.orderDate))
        twoCol("Type:",    order.orderType)
        order.tableName?.takeIf    { it.isNotBlank() }?.let { twoCol("Table:",    it) }
        order.waiterName?.takeIf   { it.isNotBlank() }?.let { twoCol("Waiter:",   it) }
        order.customerName?.takeIf { it.isNotBlank() }?.let { twoCol("Customer:", it) }

        // ── Delivery block ──────────────────────────────────────────────────
        if (order.orderType == "Delivery" &&
            listOf(order.deliveryName, order.deliveryPhone, order.deliveryAddress).any { !it.isNullOrBlank() }) {
            sepLine('-')
            add(ALIGN_CENTER, BOLD_ON); text("DELIVERY"); add(BOLD_OFF, ALIGN_LEFT)
            order.deliveryName?.takeIf    { it.isNotBlank() }?.let { twoCol("Customer:", it) }
            order.deliveryPhone?.takeIf   { it.isNotBlank() }?.let { twoCol("Phone:",    it) }
            order.deliveryAddress?.takeIf { it.isNotBlank() }?.let { twoCol("Address:",  it) }
        }

        sepLine('-')
        order.notes?.takeIf { it.isNotBlank() }?.let { text("Note: $it") }

        // ── Column headers ──────────────────────────────────────────────────
        add(BOLD_ON)
        text("Item".padEnd(nmW) + "Qty".padStart(qtyW) + "Price".padStart(prW) + "Total".padStart(totW))
        add(BOLD_OFF)
        sepLine('-')

        // ── Items ───────────────────────────────────────────────────────────
        order.items.forEach { item ->
            val rawName = buildString {
                append(item.productNameSnapshot)
                if (!item.sizeNameSnapshot.isNullOrBlank()) append(" (${item.sizeNameSnapshot})")
            }
            val name  = rawName.take(nmW).padEnd(nmW)
            val qty   = "x${"%.0f".format(item.quantity)}".padStart(qtyW)
            val price = formatAmt(item.unitPrice, sym).padStart(prW)
            val total = formatAmt(item.lineTotal, sym).padStart(totW)
            text(name + qty + price + total)

            item.modifiers.forEach { mod ->
                val modName = "  + ${mod.modifierNameSnapshot}"
                if (mod.extraPrice > 0) {
                    val priceStr = formatAmt(mod.extraPrice, sym)
                    val padLen   = lineWidth - modName.length - priceStr.length
                    if (padLen > 0) text(modName + " ".repeat(padLen) + priceStr)
                    else text(modName.take(lineWidth - priceStr.length - 1) + " " + priceStr)
                } else {
                    text(modName.take(lineWidth - 2))
                }
            }
            item.notes?.takeIf { it.isNotBlank() }?.let { text("  * $it".take(lineWidth)) }
        }
        sepLine('-')

        // ── Totals ──────────────────────────────────────────────────────────
        amtRow("Sub-Total:",  order.subTotal)
        if (order.discountAmount > 0) amtRow("Discount:",         order.discountAmount)
        if (order.taxAmount > 0) {
            val lbl = "${settings.taxLabel.ifBlank { "Tax" }} (${"%.1f".format(order.taxPercent)}%):"
            amtRow(lbl, order.taxAmount)
        }
        if (order.serviceCharges > 0) amtRow("Service Charge:",  order.serviceCharges)
        if (order.deliveryCharge > 0) amtRow("Delivery Charge:", order.deliveryCharge)
        if (order.tips > 0)           amtRow("Tip:",             order.tips)
        sepLine('-')
        add(BOLD_ON)
        amtRow("TOTAL:", order.grandTotal + order.tips)
        add(BOLD_OFF)

        // ── Points redeemed / NET PAYABLE ───────────────────────────────────
        val pointsDiscount = pointsRedeemed / 10.0
        val effectiveTotal  = order.grandTotal + order.tips - pointsDiscount
        if (pointsRedeemed > 0 && pointsDiscount > 0) {
            val ptsStr = "-${formatAmt(pointsDiscount, sym)}"
            twoCol("Points Redeemed ($pointsRedeemed pts):", ptsStr)
            add(BOLD_ON); amtRow("NET PAYABLE:", effectiveTotal); add(BOLD_OFF)
        }
        sepLine('=')

        // ── Payments ────────────────────────────────────────────────────────
        if (payments.isNotEmpty()) {
            payments.filter { it.amount > 0 }.forEach { p ->
                amtRow("Paid (${p.paymentMethod}):", p.amount)
            }
        } else if (order.paidAmount > 0) {
            amtRow("Paid:", order.paidAmount)
        }
        if (order.balanceAmount < 0) amtRow("Change:", -order.balanceAmount)
        sepLine('=')

        // ── QR code: FBR invoice when enabled, order number otherwise ────────
        if (settings.fbrEnabled && !order.fbrInvoiceNo.isNullOrBlank()) {
            sepLine('-')
            add(ALIGN_CENTER, BOLD_ON); text("FBR TAX INVOICE"); add(BOLD_OFF, ALIGN_LEFT)
            twoCol("FBR No:", order.fbrInvoiceNo)
            val fbrQr = escPosQrCode("FBR:${order.fbrInvoiceNo}")
            if (fbrQr.isNotEmpty()) { add(ALIGN_CENTER); buf.add(fbrQr); add(ALIGN_LEFT) }
        } else {
            val qrBytes = escPosQrCode(order.orderNo.ifBlank { order.tokenNo })
            if (qrBytes.isNotEmpty()) buf.add(qrBytes)
        }

        // ── Customer profile ─────────────────────────────────────────────────
        if (order.customerId != null) {
            sepLine('-')
            add(ALIGN_CENTER, BOLD_ON); text("CUSTOMER PROFILE"); add(BOLD_OFF, ALIGN_LEFT)
            if (customerTotalOrders > 0) twoCol("Total Visits:", customerTotalOrders.toString())
            if (pointsRedeemed > 0)     twoCol("Points Redeemed:", "$pointsRedeemed pts")
            if (pointsEarned > 0)       twoCol("Points Earned:", "+$pointsEarned pts")
            add(BOLD_ON); twoCol("Points Balance:", "$customerLoyaltyPoints pts"); add(BOLD_OFF)
        }

        // ── Footer ───────────────────────────────────────────────────────────
        add(ALIGN_CENTER)
        val footerText = footer.ifBlank { settings.receiptFooter.ifBlank { "Thank you for your visit!" } }
        footerText.lines().forEach { text(it) }
        text(dtFmt.format(java.util.Date()))
        add(FEED_3, CUT)

        return buf.reduce { acc, b -> acc + b }
    }

    suspend fun printZReport(
        address:          String,
        shift:            Shift,
        summary:          List<ShiftPaymentSummary>,
        expenses:         List<Expense>,
        cashTransactions: List<com.fastpos.android.data.models.CashTransaction> = emptyList(),
        salariesPaid:     Double = 0.0,
        advancesPaid:     Double = 0.0,
        sym:              String,
        companyName:      String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not available"))
            @Suppress("MissingPermission") val device = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission") socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            @Suppress("MissingPermission") adapter.cancelDiscovery()
            @Suppress("MissingPermission") socket.connect()
            val data = buildZReportBytes(shift, summary, expenses, cashTransactions, salariesPaid, advancesPaid, sym, companyName)
            socket.outputStream.apply { write(data); flush() }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    fun buildZReportBytes(
        shift:            Shift,
        summary:          List<ShiftPaymentSummary>,
        expenses:         List<Expense>,
        cashTransactions: List<com.fastpos.android.data.models.CashTransaction> = emptyList(),
        salariesPaid:     Double = 0.0,
        advancesPaid:     Double = 0.0,
        sym:              String,
        companyName:      String
    ): ByteArray {
        val buf = mutableListOf<ByteArray>()
        val dtFmt  = java.text.SimpleDateFormat("dd MMM yyyy  hh:mm a", java.util.Locale.ENGLISH)
        val timeFmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)

        fun add(vararg b: ByteArray) = b.forEach { buf.add(it) }
        fun text(t: String) = buf.add((t + "\n").toByteArray(Charsets.UTF_8))
        fun line(char: Char = '-', len: Int = 32) = text(char.toString().repeat(len))
        fun amtRow(label: String, amount: Double) =
            text(label.padEnd(20) + formatAmt(amount, sym).padStart(12))

        val totalOrders = summary.sumOf { it.txCount }
        val dateFmt     = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH)

        // Exclude Opening Cash from manual movements — it's shown via shift.openingCash separately
        val manualCashIn  = cashTransactions.filter { it.transactionType == "In" && it.reason != "Opening Cash" }
        val manualCashOut = cashTransactions.filter { it.transactionType == "Out" }
        val totalManualIn  = manualCashIn.sumOf  { it.amount }
        val totalManualOut = manualCashOut.sumOf { it.amount }

        add(INIT, ALIGN_CENTER, BOLD_ON, DOUBLE_HEIGHT)
        text(companyName)
        add(NORMAL_SIZE, BOLD_OFF)
        text("SHIFT Z-REPORT")
        text(dtFmt.format(java.util.Date()))
        add(ALIGN_LEFT)
        line('=')
        text("Shift       : ${shift.shiftCode}")
        text("Date        : ${dateFmt.format(shift.businessDate)}")
        text("Opened      : ${dtFmt.format(shift.openingTime)}")
        shift.closingTime?.let { text("Closed      : ${dtFmt.format(it)}") }
        text("Total Orders: $totalOrders")

        line()
        add(BOLD_ON); text("SALES SUMMARY"); add(BOLD_OFF)
        amtRow("Total Sales", shift.totalSales)

        if (summary.isNotEmpty()) {
            line()
            add(BOLD_ON); text("PAYMENT BREAKDOWN"); add(BOLD_OFF)
            summary.forEach { s -> amtRow("${s.method} (${s.txCount}x)", s.amount) }
        }

        val totalExpenses = expenses.sumOf { it.amount }
        if (totalExpenses > 0) {
            line()
            add(BOLD_ON); text("EXPENSES"); add(BOLD_OFF)
            expenses.forEach { e ->
                text("${e.description.take(18).padEnd(18)} ${formatAmt(e.amount, sym).padStart(14)}")
            }
            amtRow("Total Expenses", totalExpenses)
        }

        if (salariesPaid > 0 || advancesPaid > 0) {
            line()
            add(BOLD_ON); text("PAYROLL THIS SHIFT"); add(BOLD_OFF)
            if (salariesPaid > 0) amtRow("Salaries Paid", salariesPaid)
            if (advancesPaid > 0) amtRow("Advances Paid", advancesPaid)
        }

        // Cash drawer movements
        line('=')
        add(BOLD_ON); text("CASH DRAWER"); add(BOLD_OFF)
        amtRow("Opening Cash", shift.openingCash)
        val cashSales = summary.firstOrNull { it.method.equals("Cash", ignoreCase = true) }?.amount ?: 0.0
        amtRow("+ Cash Sales", cashSales)
        if (totalManualIn > 0) {
            amtRow("+ Cash In", totalManualIn)
            manualCashIn.forEach { tx ->
                text("  ${timeFmt.format(tx.createdAt)} ${tx.reason.take(14).padEnd(14)} ${formatAmt(tx.amount, sym).padStart(8)}")
            }
        }
        if (totalManualOut > 0) {
            amtRow("- Cash Out", totalManualOut)
            manualCashOut.forEach { tx ->
                text("  ${timeFmt.format(tx.createdAt)} ${tx.reason.take(14).padEnd(14)} ${formatAmt(tx.amount, sym).padStart(8)}")
            }
        }
        if (totalExpenses > 0) amtRow("- Expenses", totalExpenses)
        if (salariesPaid > 0)  amtRow("- Salaries", salariesPaid)
        if (advancesPaid > 0)  amtRow("- Advances", advancesPaid)

        val expectedCash = shift.openingCash + cashSales + totalManualIn - totalManualOut - totalExpenses - salariesPaid - advancesPaid
        line()
        add(BOLD_ON)
        amtRow("Expected in Drawer", expectedCash)
        add(BOLD_OFF)
        if (shift.closingCash > 0 || shift.closingTime != null) {
            val closingAmt = if (shift.closingCash > 0) shift.closingCash else expectedCash
            amtRow("Actual (Closing)",   closingAmt)
            val difference = closingAmt - expectedCash
            line()
            add(BOLD_ON)
            val diffLabel = if (difference >= 0) "Over  (+)" else "Short (-)"
            amtRow(diffLabel, kotlin.math.abs(difference))
            add(BOLD_OFF)
        }

        add(ALIGN_CENTER)
        line('=')
        text("*** END OF SHIFT REPORT ***")
        add(FEED_3, CUT)

        return buf.reduce { acc, b -> acc + b }
    }

    suspend fun printTokenSlip(
        address:     String,
        tokenNo:     String,
        orderNo:     String,
        orderType:   String,
        tableName:   String?,
        items:       List<com.fastpos.android.data.models.CartItem>,
        companyName: String,
        footer:      String = "Please wait for your token to be called."
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not available"))
            @Suppress("MissingPermission") val device = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission") socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            @Suppress("MissingPermission") socket.connect()

            val buf = mutableListOf<ByteArray>()
            fun add(vararg b: ByteArray) = b.forEach { buf.add(it) }
            fun text(t: String) = buf.add((t + "\n").toByteArray(Charsets.UTF_8))
            fun line(char: Char = '-', len: Int = 32) = text(char.toString().repeat(len))

            add(INIT, ALIGN_CENTER, BOLD_ON, DOUBLE_HEIGHT)
            text(companyName)
            add(NORMAL_SIZE, BOLD_OFF)
            text(java.text.SimpleDateFormat("dd MMM yyyy  hh:mm a", java.util.Locale.ENGLISH).format(java.util.Date()))
            line()
            add(BOLD_ON)
            text("ORDER TOKEN")
            add(NORMAL_SIZE)
            add(byteArrayOf(0x1D, 0x21, 0x11))
            text(tokenNo)
            add(NORMAL_SIZE, ALIGN_LEFT)
            text("Order : $orderNo")
            text("Type  : $orderType")
            tableName?.takeIf { it.isNotBlank() }?.let { text("Table : $it") }
            line()
            items.forEach { item ->
                val name = if (item.productName.length > 20) item.productName.take(19) + "…" else item.productName
                text("  x${item.quantity}  $name")
                item.sizeName?.let { text("       [$it]") }
            }
            line()
            add(ALIGN_CENTER, BOLD_ON)
            footer.lines().forEach { text(it) }
            add(BOLD_OFF, FEED_3, CUT)

            val data = buf.reduce { acc, b -> acc + b }
            socket.outputStream.apply { write(data); flush() }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    suspend fun printPayslip(
        address:     String,
        row:         PayrollRow,
        month:       Int,
        year:        Int,
        companyName: String,
        sym:         String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not available"))
            @Suppress("MissingPermission") val device = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission") socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            @Suppress("MissingPermission") adapter.cancelDiscovery()
            @Suppress("MissingPermission") socket.connect()
            val data = buildPayslipBytes(row, month, year, companyName, sym)
            socket.outputStream.apply { write(data); flush() }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun buildPayslipBytes(row: PayrollRow, month: Int, year: Int, companyName: String, sym: String): ByteArray {
        val buf = mutableListOf<ByteArray>()
        val monthName = java.text.DateFormatSymbols().months.getOrElse(month - 1) { month.toString() }

        fun add(vararg b: ByteArray) = b.forEach { buf.add(it) }
        fun text(t: String) = buf.add((t + "\n").toByteArray(Charsets.UTF_8))
        fun line(char: Char = '-', len: Int = 32) = text(char.toString().repeat(len))
        fun amtRow(label: String, amount: Double) = text(label.padEnd(20) + formatAmt(amount, sym).padStart(12))

        add(INIT, ALIGN_CENTER, BOLD_ON, DOUBLE_HEIGHT)
        text(companyName)
        add(NORMAL_SIZE, BOLD_OFF)
        text("PAYSLIP")
        text("$monthName $year")
        add(ALIGN_LEFT)
        line('=')
        add(BOLD_ON)
        text("Employee : ${row.employeeName}")
        add(BOLD_OFF)
        if (row.employeeRole.isNotBlank()) text("Role     : ${row.employeeRole}")
        line()
        amtRow("Basic Salary", row.monthlySalary)
        if (row.advancesThisMonth > 0) amtRow("- Advances", row.advancesThisMonth)
        line('=')
        add(BOLD_ON)
        amtRow("NET PAY", row.netPay)
        add(BOLD_OFF)
        line('=')
        add(ALIGN_CENTER)
        text("Status: ${if (row.isPaid) "PAID" else "PENDING"}")
        add(FEED_3, CUT)

        return buf.reduce { acc, b -> acc + b }
    }

    suspend fun printReport(address: String, lines: String): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not available"))
            @Suppress("MissingPermission") val device = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission") socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            @Suppress("MissingPermission") adapter.cancelDiscovery()
            @Suppress("MissingPermission") socket.connect()
            val buf = mutableListOf<ByteArray>()
            fun add(vararg b: ByteArray) = b.forEach { buf.add(it) }
            fun text(t: String) = buf.add((t + "\n").toByteArray(Charsets.UTF_8))
            add(INIT, ALIGN_LEFT)
            lines.lines().forEach { text(it) }
            add(FEED_3, CUT)
            val data = buf.reduce { acc, b -> acc + b }
            socket.outputStream.apply { write(data); flush() }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    suspend fun printStockReport(
        address:     String,
        items:       List<StockItem>,
        companyName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not available"))
            @Suppress("MissingPermission") val device = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission") socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            @Suppress("MissingPermission") adapter.cancelDiscovery()
            @Suppress("MissingPermission") socket.connect()
            val buf = mutableListOf<ByteArray>()
            fun add(vararg b: ByteArray) = b.forEach { buf.add(it) }
            fun text(t: String) = buf.add((t + "\n").toByteArray(Charsets.UTF_8))
            fun line(char: Char = '-', len: Int = 32) = text(char.toString().repeat(len))

            val dtFmt = java.text.SimpleDateFormat("dd MMM yyyy  hh:mm a", java.util.Locale.ENGLISH)
            add(INIT, ALIGN_CENTER, BOLD_ON, DOUBLE_HEIGHT)
            text(companyName)
            add(NORMAL_SIZE, BOLD_OFF)
            text("STOCK REPORT")
            text(dtFmt.format(java.util.Date()))
            add(ALIGN_LEFT)
            line('=')

            val outOfStock = items.count { it.currentStock <= 0 }
            val lowStock   = items.count { it.currentStock > 0 && it.currentStock <= it.minimumStock }
            text("Total Items : ${items.size}")
            text("Out of Stock: $outOfStock")
            text("Low Stock   : $lowStock")
            line('=')

            add(BOLD_ON)
            text("${"Item".padEnd(18)}${"Stock".padStart(7)}${"Min".padStart(7)}")
            add(BOLD_OFF)
            line()

            items.forEach { item ->
                val name  = if (item.productName.length > 17) item.productName.take(16) + "…" else item.productName
                val stock = "%.1f".format(item.currentStock).padStart(7)
                val min   = "%.1f".format(item.minimumStock).padStart(7)
                text(name.padEnd(18) + stock + min)
                if (item.currentStock <= 0) {
                    text("  [OUT OF STOCK]")
                } else if (item.currentStock <= item.minimumStock) {
                    text("  [LOW STOCK]")
                }
            }

            add(ALIGN_CENTER)
            line('=')
            text("${items.size} products listed")
            add(FEED_3, CUT)
            val data = buf.reduce { acc, b -> acc + b }
            socket.outputStream.apply { write(data); flush() }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    suspend fun printPreBill(
        address:   String,
        order:     Order,
        settings:  CompanySettings,
        lineWidth: Int    = 32,
        logoData:  ByteArray? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not available"))
            @Suppress("MissingPermission") val device = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission") socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            @Suppress("MissingPermission") adapter.cancelDiscovery()
            @Suppress("MissingPermission") socket.connect()
            val data = buildPreBillBytes(order, settings, lineWidth, logoData)
            socket.outputStream.apply { write(data); flush() }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    fun buildPreBillBytes(
        order:     Order,
        settings:  CompanySettings,
        lineWidth: Int    = 32,
        logoData:  ByteArray? = null
    ): ByteArray {
        val sym  = settings.currencySymbol
        val qtyW = 4
        val prW  = (lineWidth - qtyW) / 4
        val totW = (lineWidth - qtyW) / 4
        val nmW  = lineWidth - qtyW - prW - totW
        val dtFmt = java.text.SimpleDateFormat("dd MMM yyyy  hh:mm a", java.util.Locale.ENGLISH)

        val buf = mutableListOf<ByteArray>()
        fun add(vararg b: ByteArray) = b.forEach { buf.add(it) }
        fun text(t: String) = buf.add((t + "\n").toByteArray(Charsets.UTF_8))
        fun sepLine(c: Char = '=') = text(c.toString().repeat(lineWidth))
        fun twoCol(label: String, value: String) {
            val spaces = lineWidth - label.length - value.length
            if (spaces > 0) text(label + " ".repeat(spaces) + value)
            else text(label.take(lineWidth - value.length - 1) + " " + value)
        }
        fun amtRow(label: String, amount: Double) = twoCol(label, formatAmt(amount, sym))

        // ── Header ──
        add(INIT)
        val logoBytes = loadLogoBytes(logoData ?: settings.logoData)
        if (logoBytes.isNotEmpty()) buf.add(logoBytes)
        add(ALIGN_CENTER, BOLD_ON, DOUBLE_HEIGHT)
        text(settings.companyName)
        add(NORMAL_SIZE, BOLD_OFF)
        if (settings.address.isNotBlank()) text(settings.address)
        if (settings.phone.isNotBlank())   text("Tel: ${settings.phone}")
        add(ALIGN_LEFT)
        sepLine('=')
        add(ALIGN_CENTER, BOLD_ON)
        text("*** PRE-BILL ***")
        add(BOLD_OFF, ALIGN_LEFT)
        sepLine('=')

        // ── Order info ──
        add(BOLD_ON); twoCol("Token:", order.tokenNo); add(BOLD_OFF)
        twoCol("Order #:", order.orderNo)
        twoCol("Date:",    dtFmt.format(order.orderDate))
        twoCol("Type:",    order.orderType)
        order.tableName?.takeIf  { it.isNotBlank() }?.let { twoCol("Table:",    it) }
        order.waiterName?.takeIf { it.isNotBlank() }?.let { twoCol("Waiter:",   it) }
        order.customerName?.takeIf { it.isNotBlank() }?.let { twoCol("Customer:", it) }
        sepLine('=')
        order.notes?.takeIf { it.isNotBlank() }?.let { text("Note: $it") }

        // ── Column headers ──
        add(BOLD_ON)
        text("Item".padEnd(nmW) + "Qty".padStart(qtyW) + "Price".padStart(prW) + "Total".padStart(totW))
        add(BOLD_OFF)
        sepLine('-')

        // ── Items ──
        order.items.forEach { item ->
            val rawName = buildString {
                append(item.productNameSnapshot)
                if (!item.sizeNameSnapshot.isNullOrBlank()) append(" (${item.sizeNameSnapshot})")
            }
            val name  = rawName.take(nmW).padEnd(nmW)
            val qty   = "x${"%.0f".format(item.quantity)}".padStart(qtyW)
            val price = formatAmt(item.unitPrice, sym).padStart(prW)
            val total = formatAmt(item.lineTotal, sym).padStart(totW)
            text(name + qty + price + total)

            item.modifiers.forEach { mod ->
                val modName = "  + ${mod.modifierNameSnapshot}"
                if (mod.extraPrice > 0) {
                    val priceStr = formatAmt(mod.extraPrice, sym)
                    val padLen   = lineWidth - modName.length - priceStr.length
                    if (padLen > 0) text(modName + " ".repeat(padLen) + priceStr)
                    else text(modName.take(lineWidth - priceStr.length - 1) + " " + priceStr)
                } else {
                    text(modName.take(lineWidth - 2))
                }
            }
            item.notes?.takeIf { it.isNotBlank() }?.let { text("  * $it".take(lineWidth)) }
        }
        sepLine('-')

        // ── Totals ──
        amtRow("Sub-Total:", order.subTotal)
        if (order.discountAmount > 0) amtRow("Discount:",         order.discountAmount)
        if (order.taxAmount > 0) {
            val lbl = "${settings.taxLabel.ifBlank { "Tax" }} (${"%.1f".format(order.taxPercent)}%):"
            amtRow(lbl, order.taxAmount)
        }
        if (order.serviceCharges > 0) amtRow("Service Charge:",  order.serviceCharges)
        if (order.deliveryCharge > 0) amtRow("Delivery Charge:", order.deliveryCharge)
        if (order.tips > 0)           amtRow("Tip:",             order.tips)
        sepLine('-')
        add(BOLD_ON)
        amtRow("TOTAL DUE:", order.grandTotal + order.tips)
        add(BOLD_OFF)
        sepLine('=')

        // ── Footer ──
        val qrBytesPreBill = escPosQrCode(order.orderNo.ifBlank { order.tokenNo })
        if (qrBytesPreBill.isNotEmpty()) buf.add(qrBytesPreBill)
        add(ALIGN_CENTER)
        text("Please pay at the counter")
        text("This is not a payment receipt")
        add(FEED_3, CUT)

        return buf.reduce { acc, bytes -> acc + bytes }
    }

    fun buildTakeawayTokenBytes(
        orderNo: String,
        tokenNo: String,
        orderType: String,
        customerName: String?,
        notes: String?,
        items: List<com.fastpos.android.data.models.CartItem>,
        companyName: String,
        lineWidth: Int = 32,
        logoData: ByteArray? = null
    ): ByteArray {
        val DOUBLE_WIDE_HIGH = byteArrayOf(0x1B, 0x21, 0x30)
        val timeFmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.ENGLISH)
        val buf = mutableListOf<ByteArray>()
        fun add(vararg b: ByteArray) = b.forEach { buf.add(it) }
        fun text(t: String) = buf.add((t + "\n").toByteArray(Charsets.UTF_8))
        fun sep()  = text("=".repeat(lineWidth))
        fun dash() = text("-".repeat(lineWidth))
        fun twoCol(label: String, value: String) {
            val spaces = (lineWidth - label.length - value.length).coerceAtLeast(1)
            text(label + " ".repeat(spaces) + value)
        }

        add(INIT)
        val logoBytes = loadLogoBytes(logoData)
        if (logoBytes.isNotEmpty()) buf.add(logoBytes)

        // Header
        add(ALIGN_CENTER, BOLD_ON, DOUBLE_HEIGHT)
        if (companyName.isNotBlank()) text(companyName)
        add(NORMAL_SIZE, BOLD_OFF)
        add(BOLD_ON)
        text("** TAKEAWAY TOKEN **")
        add(BOLD_OFF)
        sep()

        // Prominent token number
        add(ALIGN_CENTER, DOUBLE_WIDE_HIGH, BOLD_ON)
        text(tokenNo.ifBlank { "-" })
        add(NORMAL_SIZE, BOLD_OFF)
        dash()

        // Order info
        add(ALIGN_LEFT)
        twoCol("Order #:", orderNo.ifBlank { "-" })
        twoCol("Time:", timeFmt.format(java.util.Date()))
        if (!customerName.isNullOrBlank()) {
            add(BOLD_ON)
            twoCol("Customer:", customerName)
            add(BOLD_OFF)
        }

        // Note
        if (!notes.isNullOrBlank()) {
            dash()
            add(ALIGN_CENTER, BOLD_ON)
            text("** NOTE: $notes **")
            add(BOLD_OFF, ALIGN_LEFT)
        }

        // Items
        sep()
        add(ALIGN_CENTER, BOLD_ON)
        text("ITEMS")
        add(BOLD_OFF, ALIGN_LEFT)
        dash()
        for (item in items) {
            val name = item.productName + if (!item.sizeName.isNullOrBlank()) " (${item.sizeName})" else ""
            add(BOLD_ON)
            text("x${item.quantity} $name")
            add(BOLD_OFF)
            for (mod in item.selectedModifiers) text("  + ${mod.modifierName}")
            if (item.notes.isNotBlank()) text("  ** ${item.notes} **")
        }
        sep()
        add(ALIGN_CENTER)
        text("-- PLEASE COLLECT WHEN CALLED --")
        add(ALIGN_LEFT, FEED_3, CUT)
        return buf.reduce { acc, b -> acc + b }
    }

    suspend fun printTakeawayToken(
        address: String,
        orderNo: String,
        tokenNo: String,
        customerName: String?,
        notes: String?,
        items: List<com.fastpos.android.data.models.CartItem>,
        companyName: String,
        lineWidth: Int = 32,
        logoData: ByteArray? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = buildTakeawayTokenBytes(
                orderNo = orderNo, tokenNo = tokenNo,
                orderType = "Takeaway", customerName = customerName, notes = notes,
                items = items, companyName = companyName, lineWidth = lineWidth, logoData = logoData
            )
            var socket: BluetoothSocket? = null
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw Exception("Bluetooth not available")
                @Suppress("MissingPermission") val device = adapter.getRemoteDevice(address)
                @Suppress("MissingPermission") socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                adapter.cancelDiscovery()
                @Suppress("MissingPermission") socket.connect()
                socket.outputStream.write(bytes)
                socket.outputStream.flush()
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }
    }

    suspend fun openCashDrawer(address: String): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: BluetoothSocket? = null
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: return@withContext Result.failure(Exception("Bluetooth not available"))
            @Suppress("MissingPermission") val device = adapter.getRemoteDevice(address)
            @Suppress("MissingPermission") socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            adapter.cancelDiscovery()
            @Suppress("MissingPermission") socket.connect()
            val drawerCmd = byteArrayOf(0x1B, 0x70, 0x00, 0x19.toByte(), 0xFA.toByte())
            socket.outputStream.apply { write(drawerCmd); flush() }
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    fun buildReceiptText(
        order:                 Order,
        settings:              CompanySettings,
        pointsEarned:          Int               = 0,
        header:                String            = "",
        footer:                String            = "",
        payments:              List<com.fastpos.android.data.models.PaymentEntry> = emptyList(),
        lineWidth:             Int               = 32,
        pointsRedeemed:        Int               = 0,
        customerTotalOrders:   Int               = 0,
        customerLoyaltyPoints: Int               = 0,
        @Suppress("UNUSED_PARAMETER") logoData:  ByteArray? = null  // A4 plain-text: no image support
    ): String = buildString {
        val sym   = settings.currencySymbol
        val qtyW  = 4
        val prW   = (lineWidth - qtyW) / 4
        val totW  = (lineWidth - qtyW) / 4
        val nmW   = lineWidth - qtyW - prW - totW
        val dtFmt = java.text.SimpleDateFormat("dd MMM yyyy  hh:mm a", java.util.Locale.ENGLISH)

        fun sep(c: Char = '=') = appendLine(c.toString().repeat(lineWidth))
        fun center(t: String) = appendLine(t.padStart((lineWidth + t.length) / 2).take(lineWidth))
        fun twoCol(label: String, value: String) {
            val spaces = lineWidth - label.length - value.length
            if (spaces > 0) appendLine(label + " ".repeat(spaces) + value)
            else appendLine(label.take(lineWidth - value.length - 1) + " " + value)
        }
        fun amtRow(label: String, amount: Double) = twoCol(label, formatAmt(amount, sym))

        // Header
        center(settings.companyName)
        if (settings.address.isNotBlank())   center(settings.address)
        if (settings.phone.isNotBlank())     center("Tel: ${settings.phone}")
        if (settings.taxNumber.isNotBlank()) center("NTN: ${settings.taxNumber}")
        if (header.isNotBlank())             header.lines().forEach { center(it) }
        sep('=')

        // Order info
        twoCol("Order #:", order.orderNo)
        twoCol("Token:",   order.tokenNo)
        twoCol("Date:",    dtFmt.format(order.orderDate))
        twoCol("Type:",    order.orderType)
        order.tableName?.takeIf    { it.isNotBlank() }?.let { twoCol("Table:",    it) }
        order.waiterName?.takeIf   { it.isNotBlank() }?.let { twoCol("Waiter:",   it) }
        order.customerName?.takeIf { it.isNotBlank() }?.let { twoCol("Customer:", it) }

        // Delivery
        if (order.orderType == "Delivery" &&
            listOf(order.deliveryName, order.deliveryPhone, order.deliveryAddress).any { !it.isNullOrBlank() }) {
            sep('-')
            center("DELIVERY")
            order.deliveryName?.takeIf    { it.isNotBlank() }?.let { twoCol("Customer:", it) }
            order.deliveryPhone?.takeIf   { it.isNotBlank() }?.let { twoCol("Phone:",    it) }
            order.deliveryAddress?.takeIf { it.isNotBlank() }?.let { twoCol("Address:",  it) }
        }

        sep('-')
        order.notes?.takeIf { it.isNotBlank() }?.let { appendLine("Note: $it") }

        // Column headers
        appendLine("Item".padEnd(nmW) + "Qty".padStart(qtyW) + "Price".padStart(prW) + "Total".padStart(totW))
        sep('-')

        // Items
        order.items.forEach { item ->
            val rawName = buildString {
                append(item.productNameSnapshot)
                if (!item.sizeNameSnapshot.isNullOrBlank()) append(" (${item.sizeNameSnapshot})")
            }
            val name  = rawName.take(nmW).padEnd(nmW)
            val qty   = "x${"%.0f".format(item.quantity)}".padStart(qtyW)
            val price = formatAmt(item.unitPrice, sym).padStart(prW)
            val total = formatAmt(item.lineTotal, sym).padStart(totW)
            appendLine(name + qty + price + total)

            item.modifiers.forEach { mod ->
                val modName = "  + ${mod.modifierNameSnapshot}"
                if (mod.extraPrice > 0) {
                    val priceStr = formatAmt(mod.extraPrice, sym)
                    val padLen   = lineWidth - modName.length - priceStr.length
                    if (padLen > 0) appendLine(modName + " ".repeat(padLen) + priceStr)
                    else appendLine(modName.take(lineWidth - priceStr.length - 1) + " " + priceStr)
                } else {
                    appendLine(modName.take(lineWidth - 2))
                }
            }
            item.notes?.takeIf { it.isNotBlank() }?.let { appendLine("  * $it") }
        }
        sep('-')

        // Totals
        amtRow("Sub-Total:", order.subTotal)
        if (order.discountAmount > 0) amtRow("Discount:",         order.discountAmount)
        if (order.taxAmount > 0) {
            val lbl = "${settings.taxLabel.ifBlank { "Tax" }} (${"%.1f".format(order.taxPercent)}%):"
            amtRow(lbl, order.taxAmount)
        }
        if (order.serviceCharges > 0) amtRow("Service Charge:",  order.serviceCharges)
        if (order.deliveryCharge > 0) amtRow("Delivery Charge:", order.deliveryCharge)
        if (order.tips > 0)           amtRow("Tip:",             order.tips)
        sep('-')
        twoCol("TOTAL:", formatAmt(order.grandTotal + order.tips, sym))

        val pointsDiscount = pointsRedeemed / 10.0
        val effectiveTotal  = order.grandTotal + order.tips - pointsDiscount
        if (pointsRedeemed > 0 && pointsDiscount > 0) {
            twoCol("Points Redeemed ($pointsRedeemed pts):", "-${formatAmt(pointsDiscount, sym)}")
            twoCol("NET PAYABLE:", formatAmt(effectiveTotal, sym))
        }
        sep('=')

        if (payments.isNotEmpty()) {
            payments.filter { it.amount > 0 }.forEach { p ->
                amtRow("Paid (${p.paymentMethod}):", p.amount)
            }
        } else if (order.paidAmount > 0) {
            amtRow("Paid:", order.paidAmount)
        }
        if (order.balanceAmount < 0) amtRow("Change:", -order.balanceAmount)
        sep('=')

        // FBR section (A4 text receipt)
        if (!order.fbrInvoiceNo.isNullOrBlank()) {
            sep('-')
            center("FBR TAX INVOICE")
            twoCol("FBR No:", order.fbrInvoiceNo)
            sep('-')
        }

        if (order.customerId != null) {
            sep('-')
            center("CUSTOMER PROFILE")
            if (customerTotalOrders > 0) twoCol("Total Visits:", customerTotalOrders.toString())
            if (pointsRedeemed > 0)     twoCol("Points Redeemed:", "$pointsRedeemed pts")
            if (pointsEarned > 0)       twoCol("Points Earned:", "+$pointsEarned pts")
            twoCol("Points Balance:", "$customerLoyaltyPoints pts")
        }

        val footerText = footer.ifBlank { settings.receiptFooter.ifBlank { "Thank you for your visit!" } }
        center(footerText)
        center(dtFmt.format(java.util.Date()))
    }

    private fun formatAmt(amount: Double, symbol: String): String =
        "$symbol ${"%.2f".format(amount)}"
}
