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
    // Come mostrare il valore. OVERLAY è la finestra flottante esistente;
    // NOTIFICATION_ICON ridisegna l'icona della notifica persistente a ogni
    // campionamento — quella vive per davvero nella status bar (non è un
    // overlay), a costo di poter mostrare solo 3-4 caratteri e non poter
    // scegliere la sua posizione nel vassoio icone (decide il sistema).
    val indicatorMode: IndicatorMode = IndicatorMode.OVERLAY,

    val horizontalPosition: HorizontalPosition = HorizontalPosition.RIGHT,
    val verticalAnchor: VerticalAnchor = VerticalAnchor.TOP,
    // Solo per verticalAnchor = BOTTOM: posizione orizzontale in % della
    // larghezza dello schermo (0 = bordo sinistro, 100 = bordo destro);
    // l'overlay viene centrato su quel punto, così può stare ovunque nella
    // barra di navigazione (es. tra due tasti).
    val bottomHorizontalOffsetPct: Int = 50,
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
    // Colori personalizzabili dell'overlay. Android non espone l'aspetto
    // (chiaro/scuro) della navigation bar dell'app in primo piano, quindi
    // l'utente imposta qui i colori per abbinarli a quelli della sua barra.
    val textColorArgb: Int = 0xFFFFFFFF.toInt(),       // bianco
    val backgroundColorArgb: Int = 0xAA000000.toInt(),   // nero ~67% opaco
    val showBackground: Boolean = true,
    // Posizionamento libero: se attivo, l'overlay può essere trascinato
    // ovunque sullo schermo e la posizione (in dp dall'angolo alto-sinistra)
    // viene salvata qui, ignorando horizontalPosition/verticalOffsetDp.
    val freePosition: Boolean = false,
    val posXDp: Int = 0,
    val posYDp: Int = 0,

    // Solo per indicatorMode = NOTIFICATION_ICON: quale valore disegnare,
    // dato che non c'è spazio per mostrarli entrambi come nell'overlay.
    val notificationMetric: NotificationMetric = NotificationMetric.COMBINED,

    // Solo per NOTIFICATION_ICON: se true, l'icona disegna due righe
    // (download in alto, upload in basso) invece di un solo valore. La
    // scelta "Cosa mostrare" viene ignorata. I caratteri sono più piccoli
    // perché il sistema ridimensiona l'icona a pochi dp nella status bar.
    val notificationTwoLines: Boolean = false,

    // Solo per NOTIFICATION_ICON a due righe: spaziatura verticale (in px
    // sulla bitmap 96x96 dell'icona) tra la riga download e quella upload.
    val notificationLineSpacing: Int = 0
)

enum class IndicatorMode { OVERLAY, NOTIFICATION_ICON }
enum class HorizontalPosition { LEFT, CENTER, RIGHT }
enum class VerticalAnchor { TOP, BOTTOM }
enum class DisplayMode { STACKED, INLINE }
enum class IconStyle { NONE, ARROWS, LETTERS }
enum class NotificationMetric { DOWNLOAD, UPLOAD, COMBINED }
