package com.streamx.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.streamx.app.StreamXApp
import com.streamx.app.navigation.StreamXNavHost
import com.streamx.app.presentation.theme.STREAMXTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            val userPrefsRepo = (application as StreamXApp).container.userPreferencesRepository
            val themeMode by userPrefsRepo.themeModeFlow.collectAsState()
            val isDark = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }

            STREAMXTheme(darkTheme = isDark) {
                StreamXNavHost()
            }
        }
    }
}
