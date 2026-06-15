package com.fastpos.android

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.fastpos.android.ui.navigation.FastPosNavGraph
import com.fastpos.android.ui.theme.FastPosTheme
import com.fastpos.android.ui.theme.ThemeController
import com.fastpos.android.utils.PreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lifecycleScope.launch {
            combine(prefs.themeMode, prefs.accentColor) { mode, color -> mode to color }
                .collect { (mode, color) ->
                    ThemeController.themeMode   = mode
                    ThemeController.accentColor = color
                }
        }

        setContent {
            FastPosTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FastPosNavGraph()
                }
            }
        }
    }
}
