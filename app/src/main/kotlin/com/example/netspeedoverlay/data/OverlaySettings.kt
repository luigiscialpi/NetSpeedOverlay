package com.example.netspeedoverlay.data

/**
 * Every knob the overlay exposes. The option set mirrors what Mikanoshi's
 * CustoMIUIzer offers for its "Detailed network speed indicator" Xposed mod
 * (position, size, thresholds, one-line vs stacked layout, icon style) —
 * reimplemented from scratch here for a root-free overlay window instead of
 * a SystemUI hook, so none of the actual code is shared, only the feature
 * set it inspired.
 */
data class OverlaySettings(
    val horizontalPosition: HorizontalPosition = HorizontalPosition.RIGHT,
    val verticalOffsetDp: Int = 4,
    val displayMode: DisplayMode = DisplayMode.STACKED,
    val lineSpacingDp: Int = 0, // spazio tra le due righe in modalità "Due righe"
    val iconStyle: IconStyle = IconStyle.ARROWS,
    val fontSizeSp: Int = 12,
    val bold: Boolean = false,
    val showPerSecondSuffix: Boolean = true,
    val updateIntervalMs: Long = 1500L,
    val dimWhenIdle: Boolean = true,
    val idleThresholdBytesPerSec: Long = 1024L, // 1 KB/s
    val idleAlpha: Float = 0.35f,
    // Posizionamento libero: se attivo, l'overlay può essere trascinato
    // ovunque sullo schermo e la posizione (in dp dall'angolo alto-sinistra)
    // viene salvata qui, ignorando horizontalPosition/verticalOffsetDp.
    val freePosition: Boolean = false,
    val posXDp: Int = 0,
    val posYDp: Int = 0
)

enum class HorizontalPosition { LEFT, CENTER, RIGHT }
enum class DisplayMode { STACKED, INLINE }
enum class IconStyle { NONE, ARROWS, LETTERS }
