package com.example.netspeedoverlay

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.netspeedoverlay.data.SettingsRepository
import com.example.netspeedoverlay.overlay.NetSpeedOverlayService
import com.example.netspeedoverlay.ui.SettingsScreen
import com.example.netspeedoverlay.ui.theme.NetSpeedOverlayTheme

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private val hasOverlayPermission = mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: service still runs, the persistent notification just won't be visible if denied */ }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshOverlayPermissionState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository(applicationContext)
        requestNotificationPermissionIfNeeded()
        refreshOverlayPermissionState()

        setContent {
            NetSpeedOverlayTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        settingsRepository = settingsRepository,
                        hasOverlayPermission = hasOverlayPermission.value,
                        onRequestOverlayPermission = ::requestOverlayPermission,
                        onStartOverlay = { NetSpeedOverlayService.start(this) },
                        onStopOverlay = { NetSpeedOverlayService.stop(this) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Catches the case where the user grants the overlay permission and
        // returns via the back button rather than the launcher's callback.
        refreshOverlayPermissionState()
    }

    private fun refreshOverlayPermissionState() {
        hasOverlayPermission.value = NetSpeedOverlayService.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
