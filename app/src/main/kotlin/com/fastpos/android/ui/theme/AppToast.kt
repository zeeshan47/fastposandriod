package com.fastpos.android.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Drop-in replacement for [SnackbarHost] that renders messages as elevated
 * full-width cards — green for success/info, red for errors — matching the
 * cart-add notification style in PosScreen.
 *
 * No extra imports are needed in screens because this file lives in the
 * `ui.theme` package, which every screen already imports as a wildcard.
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(
        hostState = hostState,
        modifier  = modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
            .padding(horizontal = 32.dp)
    ) { data ->
        val msg = data.visuals.message
        val isError = msg.contains("fail",          ignoreCase = true) ||
                      msg.contains("error",         ignoreCase = true) ||
                      msg.contains("could not",     ignoreCase = true) ||
                      msg.contains("unable",        ignoreCase = true) ||
                      msg.contains("invalid",       ignoreCase = true) ||
                      msg.contains("denied",        ignoreCase = true) ||
                      msg.contains("wrong",         ignoreCase = true) ||
                      msg.contains("please select", ignoreCase = true) ||
                      msg.contains("required",      ignoreCase = true) ||
                      msg.contains("not allowed",   ignoreCase = true) ||
                      msg.contains("no shift",      ignoreCase = true) ||
                      msg.contains("no open",       ignoreCase = true) ||
                      msg.contains("not found",     ignoreCase = true) ||
                      msg.contains("must ",         ignoreCase = true) ||
                      msg.contains("cannot",        ignoreCase = true) ||
                      msg.contains("no connection", ignoreCase = true)
        val bg   = if (isError) RedError    else GreenSuccess
        val icon = if (isError) Icons.Default.Error else Icons.Default.CheckCircle

        Card(
            colors    = CardDefaults.cardColors(containerColor = bg),
            shape     = MaterialTheme.shapes.large,
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier  = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier              = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(22.dp)
                )
                Text(
                    text       = msg,
                    color      = Color.White,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier   = Modifier.weight(1f)
                )
            }
        }
    }
}
