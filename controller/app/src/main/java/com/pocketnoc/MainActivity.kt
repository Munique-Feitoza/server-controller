package com.pocketnoc

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.pocketnoc.notifications.NtfySubscriberService
import com.pocketnoc.ui.navigation.AppNavHost
import com.pocketnoc.ui.navigation.AppRoute
import com.pocketnoc.ui.theme.LocalThemeState
import com.pocketnoc.ui.theme.PocketNOCTheme
import com.pocketnoc.ui.theme.ThemeState
import dagger.hilt.android.AndroidEntryPoint

// FragmentActivity (não ComponentActivity): BiometricPrompt exige FragmentActivity.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // POST_NOTIFICATIONS é runtime no Android 13+. Sem isso, nem o foreground service
    // nem os alertas push aparecem. O resultado é ignorado: o app funciona sem a permissão,
    // só não exibe notificações.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            val themeState = remember { ThemeState() }
            CompositionLocalProvider(LocalThemeState provides themeState) {
                PocketNOCTheme(themeState = themeState) {
                    MainApp()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Iniciado AQUI (Activity visível = foreground garantido), nunca de Application.onCreate,
        // pra não disparar ForegroundServiceStartNotAllowedException em background no Android 12+.
        NtfySubscriberService.start(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
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
