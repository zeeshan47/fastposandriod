package com.fastpos.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun accentPrimary(color: String): Color = when (color) {
    "Teal"   -> TealAccent
    "Blue"   -> BlueInfo
    "Green"  -> GreenSuccess
    "Purple" -> PurpleKitchen
    else     -> PrimaryOrange
}

private fun accentDark(color: String): Color = when (color) {
    "Teal"   -> Color(0xFF00838F)
    "Blue"   -> Color(0xFF1565C0)
    "Green"  -> Color(0xFF2E7D32)
    "Purple" -> Color(0xFF6A1B9A)
    else     -> PrimaryDark
}

private fun darkScheme(color: String) = darkColorScheme(
    primary          = accentPrimary(color),
    onPrimary        = Color.White,
    primaryContainer = accentDark(color),
    secondary        = TealAccent,
    onSecondary      = Color.Black,
    background       = BackgroundDark,
    onBackground     = OnDark,
    surface          = SurfaceDark,
    onSurface        = OnDark,
    surfaceVariant   = CardDark,
    onSurfaceVariant = OnDarkSecondary,
    error            = RedError,
    onError          = Color.White,
    outline          = Color(0xFF444466)
)

private fun blackScheme(color: String) = darkColorScheme(
    primary          = accentPrimary(color),
    onPrimary        = Color.White,
    primaryContainer = accentDark(color),
    secondary        = Color(0xFF888888),
    onSecondary      = Color.White,
    background       = BackgroundBlack,
    onBackground     = OnDark,
    surface          = SurfaceBlack,
    onSurface        = OnDark,
    surfaceVariant   = CardBlack,
    onSurfaceVariant = OnDarkSecondary,
    error            = RedError,
    onError          = Color.White,
    outline          = OutlineBlack
)

private fun lightScheme(color: String) = lightColorScheme(
    primary          = accentPrimary(color),
    onPrimary        = Color.White,
    primaryContainer = accentDark(color).copy(alpha = 0.15f),
    secondary        = TealAccent,
    onSecondary      = Color.Black,
    background       = BackgroundLight,
    onBackground     = OnLight,
    surface          = SurfaceLight,
    onSurface        = OnLight,
    surfaceVariant   = CardLight,
    onSurfaceVariant = OnLightSecondary,
    error            = RedError,
    onError          = Color.White,
    outline          = Color(0xFFCCCCCC)
)

@Composable
fun FastPosTheme(content: @Composable () -> Unit) {
    val scheme = when (ThemeController.themeMode) {
        "Light"  -> lightScheme(ThemeController.accentColor)
        "System" -> if (isSystemInDarkTheme()) blackScheme(ThemeController.accentColor)
                    else lightScheme(ThemeController.accentColor)
        else     -> blackScheme(ThemeController.accentColor) // Black is default; "Dark" also falls here
    }
    MaterialTheme(
        colorScheme = scheme,
        typography  = FastPosTypography,
        content     = content
    )
}
