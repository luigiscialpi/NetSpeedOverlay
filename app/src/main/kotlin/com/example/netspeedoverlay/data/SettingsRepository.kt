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
        val INDICATOR_MODE = stringPreferencesKey("indicator_mode")
        val HORIZONTAL_POSITION = stringPreferencesKey("horizontal_position")
        val VERTICAL_ANCHOR = stringPreferencesKey("vertical_anchor")
        val BOTTOM_HORIZONTAL_OFFSET = intPreferencesKey("bottom_horizontal_offset")
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
        val TEXT_COLOR_ARGB = intPreferencesKey("text_color_argb")
        val BACKGROUND_COLOR_ARGB = intPreferencesKey("background_color_argb")
        val SHOW_BACKGROUND = booleanPreferencesKey("show_background")
        val FREE_POSITION = booleanPreferencesKey("free_position")
        val POS_X_DP = intPreferencesKey("pos_x_dp")
        val POS_Y_DP = intPreferencesKey("pos_y_dp")
        val NOTIFICATION_METRIC = stringPreferencesKey("notification_metric")
        val NOTIFICATION_TWO_LINES = booleanPreferencesKey("notification_two_lines")
        val NOTIFICATION_LINE_SPACING = intPreferencesKey("notification_line_spacing")
        val NOTIFICATION_FONT_SIZE_PCT = intPreferencesKey("notification_font_size_pct")
    }

    val settingsFlow: Flow<OverlaySettings> = context.dataStore.data.map { prefs ->
        val defaults = OverlaySettings()
        OverlaySettings(
            indicatorMode = prefs[Keys.INDICATOR_MODE]
                ?.let { runCatching { IndicatorMode.valueOf(it) }.getOrNull() }
                ?: defaults.indicatorMode,
            horizontalPosition = prefs[Keys.HORIZONTAL_POSITION]
                ?.let { runCatching { HorizontalPosition.valueOf(it) }.getOrNull() }
                ?: defaults.horizontalPosition,
            verticalAnchor = prefs[Keys.VERTICAL_ANCHOR]
                ?.let { runCatching { VerticalAnchor.valueOf(it) }.getOrNull() }
                ?: defaults.verticalAnchor,
            bottomHorizontalOffsetPct = prefs[Keys.BOTTOM_HORIZONTAL_OFFSET]
                ?: defaults.bottomHorizontalOffsetPct,
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
            textColorArgb = prefs[Keys.TEXT_COLOR_ARGB] ?: defaults.textColorArgb,
            backgroundColorArgb = prefs[Keys.BACKGROUND_COLOR_ARGB] ?: defaults.backgroundColorArgb,
            showBackground = prefs[Keys.SHOW_BACKGROUND] ?: defaults.showBackground,
            freePosition = prefs[Keys.FREE_POSITION] ?: defaults.freePosition,
            posXDp = prefs[Keys.POS_X_DP] ?: defaults.posXDp,
            posYDp = prefs[Keys.POS_Y_DP] ?: defaults.posYDp,
            notificationMetric = prefs[Keys.NOTIFICATION_METRIC]
                ?.let { runCatching { NotificationMetric.valueOf(it) }.getOrNull() }
                ?: defaults.notificationMetric,
            notificationTwoLines = prefs[Keys.NOTIFICATION_TWO_LINES]
                ?: defaults.notificationTwoLines,
            notificationLineSpacing = prefs[Keys.NOTIFICATION_LINE_SPACING]
                ?: defaults.notificationLineSpacing,
            notificationFontSizePct = prefs[Keys.NOTIFICATION_FONT_SIZE_PCT]
                ?: defaults.notificationFontSizePct
        )
    }

    suspend fun setIndicatorMode(value: IndicatorMode) = edit { it[Keys.INDICATOR_MODE] = value.name }
    suspend fun setHorizontalPosition(value: HorizontalPosition) = edit { it[Keys.HORIZONTAL_POSITION] = value.name }
    suspend fun setVerticalAnchor(value: VerticalAnchor) = edit { it[Keys.VERTICAL_ANCHOR] = value.name }
    suspend fun setBottomHorizontalOffsetPct(value: Int) = edit { it[Keys.BOTTOM_HORIZONTAL_OFFSET] = value }
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
    suspend fun setTextColorArgb(value: Int) = edit { it[Keys.TEXT_COLOR_ARGB] = value }
    suspend fun setBackgroundColorArgb(value: Int) = edit { it[Keys.BACKGROUND_COLOR_ARGB] = value }
    suspend fun setShowBackground(value: Boolean) = edit { it[Keys.SHOW_BACKGROUND] = value }
    suspend fun setNotificationMetric(value: NotificationMetric) = edit { it[Keys.NOTIFICATION_METRIC] = value.name }
    suspend fun setNotificationTwoLines(value: Boolean) = edit { it[Keys.NOTIFICATION_TWO_LINES] = value }
    suspend fun setNotificationLineSpacing(value: Int) = edit { it[Keys.NOTIFICATION_LINE_SPACING] = value }
    suspend fun setNotificationFontSizePct(value: Int) = edit { it[Keys.NOTIFICATION_FONT_SIZE_PCT] = value }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
