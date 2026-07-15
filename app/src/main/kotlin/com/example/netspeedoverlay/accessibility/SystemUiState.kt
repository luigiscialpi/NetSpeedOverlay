package com.example.netspeedoverlay.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SystemUiState {
    private val _isFullscreen = MutableStateFlow(false)
    val isFullscreen: StateFlow<Boolean> = _isFullscreen.asStateFlow()

    fun updateFullscreen(isFullscreen: Boolean) {
        _isFullscreen.value = isFullscreen
    }

    fun reset() {
        _isFullscreen.value = false
    }
}
