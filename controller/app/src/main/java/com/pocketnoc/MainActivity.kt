package com.pocketnoc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.pocketnoc.ui.navigation.AppNavHost
import com.pocketnoc.ui.navigation.AppRoute
import com.pocketnoc.ui.theme.LocalThemeState
import com.pocketnoc.ui.theme.PocketNOCTheme
import com.pocketnoc.ui.theme.ThemeState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeState = remember { ThemeState() }
            CompositionLocalProvider(LocalThemeState provides themeState) {
                PocketNOCTheme(themeState = themeState) {
                    MainApp()
                }
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    AppNavHost(
        navController = navController,
        startDestination = AppRoute.Splash.route
    )
}
