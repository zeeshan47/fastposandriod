@file:OptIn(ExperimentalMaterial3Api::class)

package com.fastpos.android.ui.screens.more

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fastpos.android.ui.theme.GreenSuccess
import com.fastpos.android.viewmodels.SettingsViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun RaastQrScreen(
    onNavigateBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val raastId  by vm.raastId.collectAsState()
    val settings by vm.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pay via Raast") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            if (raastId.isBlank()) {
                // No Raast ID configured
                Icon(Icons.Default.QrCode, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Raast ID not configured",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Go to Settings → Raast Payment QR to enter your Raast ID.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    settings.companyName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Scan to Pay via Raast",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // QR code
                val qrBitmap = remember(raastId) { generateQrBitmap(raastId) }
                if (qrBitmap != null) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Image(
                            bitmap   = qrBitmap.asImageBitmap(),
                            contentDescription = "Raast QR Code",
                            modifier = Modifier
                                .padding(16.dp)
                                .size(260.dp)
                        )
                    }
                }

                // Raast ID text
                Surface(
                    color = GreenSuccess.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Raast ID", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            raastId,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Text(
                    "Ask your customer to open their banking app and scan this QR code to send the payment directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int = 512): Bitmap? = try {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    bmp
} catch (_: Exception) { null }
