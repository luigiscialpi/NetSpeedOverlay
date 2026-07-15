package com.example.netspeedoverlay.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.util.Log

class SystemBarAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
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

    override fun onInterrupt() {
        Log.d("NetSpeedAccessibility", "Service interrupted")
    }

    override fun onDestroy() {
        SystemUiState.reset()
        super.onDestroy()
    }
}
