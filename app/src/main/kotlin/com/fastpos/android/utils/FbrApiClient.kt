package com.fastpos.android.utils

import com.fastpos.android.data.models.CompanySettings
import com.fastpos.android.data.models.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FbrApiClient {

    private const val POST_URL    = "https://gw.fbr.gov.pk/di_data/v1/di/postinvoicedata"
    private const val POST_URL_SB = "https://gw.fbr.gov.pk/di_data/v1/di/postinvoicedata_sb"
    private const val VAL_URL     = "https://gw.fbr.gov.pk/di_data/v1/di/validateinvoicedata"
    private const val VAL_URL_SB  = "https://gw.fbr.gov.pk/di_data/v1/di/validateinvoicedata_sb"
    private const val TIMEOUT_MS  = 30_000

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun submitInvoice(order: Order, settings: CompanySettings): String? = withContext(Dispatchers.IO) {
        try {
            val url     = if (settings.fbrSandboxMode) POST_URL_SB else POST_URL
            val payload = buildPayload(order, settings)
            val json    = post(url, payload.toString(), settings.fbrToken) ?: return@withContext null
            val valResp = json.optJSONObject("validationResponse")
            if (valResp?.optString("statusCode") == "00") {
                json.optString("invoiceNumber").ifBlank { null }
            } else {
                null
            }
        } catch (_: Exception) { null }
    }

    // Returns true if the bearer token is accepted (HTTP 200/400 = valid auth; 401 = bad token)
    suspend fun testConnection(settings: CompanySettings): Boolean = withContext(Dispatchers.IO) {
        try {
            val url     = if (settings.fbrSandboxMode) VAL_URL_SB else VAL_URL
            val payload = buildTestPayload(settings)
            val conn    = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod  = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout    = TIMEOUT_MS
            conn.doOutput       = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${settings.fbrToken}")
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            code != 401
        } catch (_: Exception) { false }
    }

    private fun buildPayload(order: Order, settings: CompanySettings): JSONObject {
        val taxRateStr   = formatTaxRate(order.taxPercent.takeIf { it > 0 } ?: settings.defaultTaxPercent)
        val sellerName   = settings.fbrBusinessName.ifBlank { settings.companyName }
        val sellerAddr   = settings.fbrSellerAddress.ifBlank { settings.address }
        val province     = settings.fbrProvince
        val buyerName    = order.customerName?.takeIf { it.isNotBlank() }
            ?: order.deliveryName?.takeIf { it.isNotBlank() }
            ?: "Walk-in Customer"

        return JSONObject().apply {
            put("invoiceType",           "Sale Invoice")
            put("invoiceDate",           dateFmt.format(order.orderDate))
            put("sellerNTNCNIC",         settings.fbrNtn)
            put("sellerBusinessName",    sellerName)
            put("sellerProvince",        province)
            put("sellerAddress",         sellerAddr)
            put("buyerNTNCNIC",          "")
            put("buyerBusinessName",     buyerName)
            put("buyerProvince",         "")
            put("buyerAddress",          order.deliveryAddress?.ifBlank { "" } ?: "")
            put("buyerRegistrationType", "Unregistered")
            put("invoiceRefNo",          "")
            if (settings.fbrSandboxMode) put("scenarioId", "SN001")
            put("items", buildItemsArray(order, settings, taxRateStr))
        }
    }

    private fun buildTestPayload(settings: CompanySettings): JSONObject {
        val sellerName = settings.fbrBusinessName.ifBlank { settings.companyName }
        val sellerAddr = settings.fbrSellerAddress.ifBlank { settings.address }
        val hsCode     = settings.fbrHsCode.ifBlank { "21069099" }
        return JSONObject().apply {
            put("invoiceType",           "Sale Invoice")
            put("invoiceDate",           dateFmt.format(Date()))
            put("sellerNTNCNIC",         settings.fbrNtn)
            put("sellerBusinessName",    sellerName)
            put("sellerProvince",        settings.fbrProvince)
            put("sellerAddress",         sellerAddr)
            put("buyerNTNCNIC",          "")
            put("buyerBusinessName",     "Walk-in Customer")
            put("buyerProvince",         "")
            put("buyerAddress",          "")
            put("buyerRegistrationType", "Unregistered")
            put("invoiceRefNo",          "")
            if (settings.fbrSandboxMode) put("scenarioId", "SN001")
            put("items", JSONArray().apply {
                put(JSONObject().apply {
                    put("hsCode",                          hsCode)
                    put("productDescription",              "Food Item")
                    put("rate",                            formatTaxRate(settings.defaultTaxPercent))
                    put("uoM",                             "Numbers, pieces, units")
                    put("quantity",                        1.0)
                    put("totalValues",                     0.0)
                    put("valueSalesExcludingST",           100.0)
                    put("fixedNotifiedValueOrRetailPrice", 0.0)
                    put("salesTaxApplicable",              0.0)
                    put("salesTaxWithheldAtSource",        0.0)
                    put("extraTax",                        0.0)
                    put("furtherTax",                      0.0)
                    put("sroScheduleNo",                   "")
                    put("fedPayable",                      0.0)
                    put("discount",                        0.0)
                    put("saleType",                        "Goods at standard rate (default)")
                    put("sroItemSerialNo",                 "")
                })
            })
        }
    }

    private fun buildItemsArray(order: Order, settings: CompanySettings, taxRateStr: String): JSONArray {
        val arr    = JSONArray()
        val hsCode = settings.fbrHsCode.ifBlank { "21069099" }
        order.items.forEach { item ->
            val qty             = item.quantity.toDouble().coerceAtLeast(1.0)
            val saleExclTax     = maxOf(0.0, item.lineTotal - item.discountAmount)
            val taxCharged      = round2(saleExclTax * order.taxPercent / 100.0)
            val totalWithTax    = saleExclTax + taxCharged
            arr.put(JSONObject().apply {
                put("hsCode",                          hsCode)
                put("productDescription",              item.productNameSnapshot)
                put("rate",                            taxRateStr)
                put("uoM",                             "Numbers, pieces, units")
                put("quantity",                        qty)
                put("totalValues",                     round2(totalWithTax))
                put("valueSalesExcludingST",           round2(saleExclTax))
                put("fixedNotifiedValueOrRetailPrice", 0.0)
                put("salesTaxApplicable",              round2(taxCharged))
                put("salesTaxWithheldAtSource",        0.0)
                put("extraTax",                        0.0)
                put("furtherTax",                      0.0)
                put("sroScheduleNo",                   "")
                put("fedPayable",                      0.0)
                put("discount",                        round2(item.discountAmount))
                put("saleType",                        "Goods at standard rate (default)")
                put("sroItemSerialNo",                 "")
            })
        }
        return arr
    }

    private fun formatTaxRate(percent: Double): String =
        if (percent <= 0) "0%" else "${percent.toInt()}%"

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0

    private fun post(urlStr: String, body: String, token: String): JSONObject? {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod  = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout    = TIMEOUT_MS
            conn.doOutput       = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code   = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val resp   = stream?.bufferedReader()?.readText() ?: return null
            JSONObject(resp)
        } catch (_: Exception) { null } finally {
            conn.disconnect()
        }
    }
}
