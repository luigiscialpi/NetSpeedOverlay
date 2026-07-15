package com.example.netspeedoverlay.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityWindowInfo
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class SystemBarAccessibilityService : AccessibilityService() {

    private val themeCache = mutableMapOf<String, Boolean>()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            val cls = event.className?.toString() ?: return
            SystemUiState.updateForegroundPackage(pkg)

            // Skip checking ourselves
            if (pkg == packageName) return

            // Prova a determinare il tema dell'app in foreground
            val isDark = themeCache.getOrPut("$pkg/$cls") {
                inferIsDarkTheme(pkg, cls)
            }
            SystemUiState.updateForegroundAppDark(isDark)
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkFullscreenState()
        }
    }

    private fun checkFullscreenState() {
        val windowsList = windows
        if (windowsList.isNullOrEmpty()) return

        // Cerca finestre di sistema come status bar o nav bar (TYPE_SYSTEM)
        val hasSystemBars = windowsList.any { it.type == AccessibilityWindowInfo.TYPE_SYSTEM }
        // Se non ci sono finestre di tipo SYSTEM, l'app è a schermo intero
        SystemUiState.updateFullscreen(!hasSystemBars)
        Log.d("NetSpeedAccessibility", "checkFullscreenState: hasSystemBars=$hasSystemBars")
    }

    private fun inferIsDarkTheme(packageName: String, className: String): Boolean {
        try {
            val component = ComponentName(packageName, className)
            // 1. Prova a risolvere l'ActivityInfo
            val activityInfo = packageManager.getActivityInfo(component, PackageManager.GET_META_DATA)
            val themeResId = if (activityInfo.themeResource != 0) {
                activityInfo.themeResource
            } else {
                // Se l'activity non specifica un tema, prendi quello dell'application
                val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                appInfo.theme
            }

            if (themeResId != 0) {
                val foreignContext = createPackageContext(packageName, 0)
                val theme = foreignContext.resources.newTheme()
                theme.applyStyle(themeResId, true)

                // 2. Livello 1: Controlla isLightTheme
                val isLightThemeAttr = android.R.attr.isLightTheme
                val typedArray = theme.obtainStyledAttributes(intArrayOf(isLightThemeAttr))
                if (typedArray.hasValue(0)) {
                    val isLight = typedArray.getBoolean(0, true)
                    typedArray.recycle()
                    Log.d("NetSpeedAccessibility", "inferIsDarkTheme: Resolved isLightTheme=$isLight for $packageName/$className via isLightTheme")
                    return !isLight
                }
                typedArray.recycle()

                // 3. Livello 2: Controlla colorBackground
                val colorBackgroundAttr = android.R.attr.colorBackground
                val typedArrayBg = theme.obtainStyledAttributes(intArrayOf(colorBackgroundAttr))
                if (typedArrayBg.hasValue(0)) {
                    val color = typedArrayBg.getColor(0, 0)
                    typedArrayBg.recycle()
                    // Calcola la luminosità percepita (formula standard YUV)
                    val r = (color shr 16 and 0xFF) / 255.0f
                    val g = (color shr 8 and 0xFF) / 255.0f
                    val b = (color and 0xFF) / 255.0f
                    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
                    val isLight = luminance > 0.5f
                    Log.d("NetSpeedAccessibility", "inferIsDarkTheme: Resolved isLightTheme=$isLight for $packageName/$className via colorBackground luminance=$luminance")
                    return !isLight
                }
                typedArrayBg.recycle()
            }
        } catch (e: Exception) {
            Log.d("NetSpeedAccessibility", "inferIsDarkTheme: Error reading theme for $packageName/$className: ${e.message}")
        }

        // 4. Livello 3: Fallback al tema di sistema
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isSystemDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        Log.d("NetSpeedAccessibility", "inferIsDarkTheme: Fallback to system isDarkMode=$isSystemDark for $packageName/$className")
        return isSystemDark
    }

    override fun onInterrupt() {
        Log.d("NetSpeedAccessibility", "Service interrupted")
    }

    override fun onDestroy() {
        SystemUiState.reset()
        super.onDestroy()
    }
}
