package com.example.netspeedoverlay.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "overlay_settings")

/**
 * Reads/writes [OverlaySettings] through Jetpack DataStore. The service and
 * the settings screen both collect [settingsFlow], so a change made in the
 * UI is picked up by the overlay on the next emission with no manual
 * plumbing between the two.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val HORIZONTAL_POSITION = stringPreferencesKey("horizontal_position")
        val VERTICAL_OFFSET_DP = intPreferencesKey("vertical_offset_dp")
        val DISPLAY_MODE = stringPreferencesKey("display_mode")
        val LINE_SPACING_DP = intPreferencesKey("line_spacing_dp")
        val ICON_STYLE = stringPreferencesKey("icon_style")
        val FONT_SIZE_SP = intPreferencesKey("font_size_sp")
        val BOLD = booleanPreferencesKey("bold")
        val SHOW_PER_SECOND_SUFFIX = booleanPreferencesKey("show_per_second_suffix")
        val UPDATE_INTERVAL_MS = longPreferencesKey("update_interval_ms")
        val DIM_WHEN_IDLE = booleanPreferencesKey("dim_when_idle")
        val IDLE_THRESHOLD_BYTES = longPreferencesKey("idle_threshold_bytes")
        val FREE_POSITION = booleanPreferencesKey("free_position")
        val POS_X_DP = intPreferencesKey("pos_x_dp")
        val POS_Y_DP = intPreferencesKey("pos_y_dp")
    }

    val settingsFlow: Flow<OverlaySettings> = context.dataStore.data.map { prefs ->
        val defaults = OverlaySettings()
        OverlaySettings(
            horizontalPosition = prefs[Keys.HORIZONTAL_POSITION]
                ?.let { runCatching { HorizontalPosition.valueOf(it) }.getOrNull() }
                ?: defaults.horizontalPosition,
            verticalOffsetDp = prefs[Keys.VERTICAL_OFFSET_DP] ?: defaults.verticalOffsetDp,
            displayMode = prefs[Keys.DISPLAY_MODE]
                ?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() }
                ?: defaults.displayMode,
            lineSpacingDp = prefs[Keys.LINE_SPACING_DP] ?: defaults.lineSpacingDp,
            iconStyle = prefs[Keys.ICON_STYLE]
                ?.let { runCatching { IconStyle.valueOf(it) }.getOrNull() }
                ?: defaults.iconStyle,
            fontSizeSp = prefs[Keys.FONT_SIZE_SP] ?: defaults.fontSizeSp,
            bold = prefs[Keys.BOLD] ?: defaults.bold,
            showPerSecondSuffix = prefs[Keys.SHOW_PER_SECOND_SUFFIX] ?: defaults.showPerSecondSuffix,
            updateIntervalMs = prefs[Keys.UPDATE_INTERVAL_MS] ?: defaults.updateIntervalMs,
            dimWhenIdle = prefs[Keys.DIM_WHEN_IDLE] ?: defaults.dimWhenIdle,
            idleThresholdBytesPerSec = prefs[Keys.IDLE_THRESHOLD_BYTES] ?: defaults.idleThresholdBytesPerSec,
            freePosition = prefs[Keys.FREE_POSITION] ?: defaults.freePosition,
            posXDp = prefs[Keys.POS_X_DP] ?: defaults.posXDp,
            posYDp = prefs[Keys.POS_Y_DP] ?: defaults.posYDp
        )
    }

    suspend fun setHorizontalPosition(value: HorizontalPosition) = edit { it[Keys.HORIZONTAL_POSITION] = value.name }
    suspend fun setVerticalOffsetDp(value: Int) = edit { it[Keys.VERTICAL_OFFSET_DP] = value }
    suspend fun setDisplayMode(value: DisplayMode) = edit { it[Keys.DISPLAY_MODE] = value.name }
    suspend fun setLineSpacingDp(value: Int) = edit { it[Keys.LINE_SPACING_DP] = value }
    suspend fun setIconStyle(value: IconStyle) = edit { it[Keys.ICON_STYLE] = value.name }
    suspend fun setFontSizeSp(value: Int) = edit { it[Keys.FONT_SIZE_SP] = value }
    suspend fun setBold(value: Boolean) = edit { it[Keys.BOLD] = value }
    suspend fun setShowPerSecondSuffix(value: Boolean) = edit { it[Keys.SHOW_PER_SECOND_SUFFIX] = value }
    suspend fun setUpdateIntervalMs(value: Long) = edit { it[Keys.UPDATE_INTERVAL_MS] = value }
    suspend fun setDimWhenIdle(value: Boolean) = edit { it[Keys.DIM_WHEN_IDLE] = value }
    suspend fun setIdleThresholdBytesPerSec(value: Long) = edit { it[Keys.IDLE_THRESHOLD_BYTES] = value }
    suspend fun setFreePosition(value: Boolean) = edit { it[Keys.FREE_POSITION] = value }
    suspend fun setPosition(xDp: Int, yDp: Int) = edit {
        it[Keys.POS_X_DP] = xDp
        it[Keys.POS_Y_DP] = yDp
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
