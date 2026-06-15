package com.fastpos.android.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ThemeController {
    var themeMode   by mutableStateOf("Black")
    var accentColor by mutableStateOf("Orange")
}
