package com.fastpos.android.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fastpos.android.R

@Composable
fun SplashScreen(
    destination: String?,
    onReady:     (String) -> Unit
) {
    val timerDone = remember { mutableStateOf(false) }
    val progress  = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(5000, easing = LinearEasing))
        timerDone.value = true
    }
    LaunchedEffect(timerDone.value, destination) {
        if (timerDone.value && destination != null) onReady(destination)
    }

    Box(
        modifier         = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        // ── Centered logo ────────────────────────────────────────────────────
        Image(
            painter            = painterResource(R.drawable.mealflow_logo),
            contentDescription = null,
            modifier           = Modifier
                .fillMaxWidth(0.68f)
                .aspectRatio(1f),
            contentScale       = ContentScale.Fit
        )

        // ── Bottom progress section ──────────────────────────────────────────
        Column(
            modifier            = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LinearProgressIndicator(
                progress   = { progress.value },
                modifier   = Modifier.fillMaxWidth(0.50f).height(3.dp),
                color      = Color(0xFFFF8A00),
                trackColor = Color(0xFFFF8A00).copy(alpha = 0.18f)
            )
            Text(
                "Powered by Rubix Solutions",
                style     = MaterialTheme.typography.labelSmall,
                color     = Color(0xFFBDBDBD),
                textAlign = TextAlign.Center
            )
        }
    }
}
