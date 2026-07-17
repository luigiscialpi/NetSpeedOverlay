package com.example.netspeedoverlay.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.netspeedoverlay.MainActivity
import com.example.netspeedoverlay.R
import com.example.netspeedoverlay.data.DisplayMode
import com.example.netspeedoverlay.data.IconStyle
import com.example.netspeedoverlay.data.IndicatorMode
import com.example.netspeedoverlay.data.NotificationMetric
import com.example.netspeedoverlay.data.OverlaySettings
import com.example.netspeedoverlay.data.VerticalAnchor
import com.example.netspeedoverlay.data.SettingsRepository
import com.example.netspeedoverlay.speed.SpeedSampler
import com.example.netspeedoverlay.accessibility.SystemUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service driving the network-speed indicator in one of two
 * mutually exclusive modes, switchable at runtime from the settings screen:
 *
 * - [IndicatorMode.OVERLAY]: the floating [WindowManager] window (unchanged
 *   behaviour below — drag, colors, spacing, etc).
 * - [IndicatorMode.NOTIFICATION_ICON]: redraws the small icon of this
 *   service's own ongoing notification on every sample. That one genuinely
 *   lives in the real status bar (it's a real notification icon, not an
 *   overlay), at the cost of fitting only ~3 compact characters and having
 *   its position in the icon tray decided by the system, not us — see
 *   README for why that trade-off exists.
 *
 * Deliberately uses plain [android.view.View]s for the overlay rather than
 * a ComposeView: a ComposeView hosted outside an Activity needs a manually
 * attached Lifecycle/ViewModelStore/SavedStateRegistry owner to render at
 * all, which is a lot of ceremony for two TextViews. Plain views are what
 * every non-root "floating bubble" app uses for exactly this reason.
 * Compose is used for [MainActivity]'s settings screen instead, where it
 * runs in a normal Activity context with zero extra wiring.
 */
class NetSpeedOverlayService : LifecycleService() {

    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository
    private val sampler = SpeedSampler()

    private var overlayRoot: LinearLayout? = null
    private var downloadText: TextView? = null
    private var uploadText: TextView? = null
    private var overlayBackground: GradientDrawable? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var currentSettings: OverlaySettings = OverlaySettings()
    private var lastKnownMode: IndicatorMode? = null
    private var samplingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepository = SettingsRepository(applicationContext)
        startForegroundWithNotification()
        // No unconditional addOverlayView() here anymore: the first emission
        // from observeSettings() creates the overlay (or doesn't) depending
        // on the persisted indicatorMode — see observeSettings().
        observeSettings()
        startSamplingLoop()
        observeAccessibilityState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        samplingJob?.cancel()
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    // ---------------------------------------------------------------
    // Notification / foreground service type
    // ---------------------------------------------------------------

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification = buildNotification(
            IconCompat.createWithResource(this, R.drawable.ic_speed_notification),
            getString(R.string.notification_content_running)
        )

        // Android 14+ (API 34) requires every foreground service to declare
        // a type. There's no built-in type for "small overlay widget", so
        // this uses specialUse (declared + justified in AndroidManifest.xml)
        // rather than misusing dataSync. ServiceCompat.startForeground is a
        // no-op-safe wrapper across all API levels, passing 0 pre-34.
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    /** Shared by the initial startForeground() call and every icon refresh in NOTIFICATION_ICON mode. */
    private fun buildNotification(icon: IconCompat, contentText: String? = null): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setSmallIcon(icon)
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
        if (contentText != null) {
            builder.setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Redraws the ongoing notification's small icon with the current speed,
     * which is what makes NOTIFICATION_ICON mode genuinely appear inside the
     * real status bar instead of floating over it.
     */
    private fun updateNotificationIcon(sample: SpeedSampler.Sample, settings: OverlaySettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val (lines, contentText) = if (settings.notificationTwoLines) {
            // Two stacked rows: upload on top, download below. The "Cosa
            // mostrare" metric is ignored because both values are drawn.
            val down = iconFor(settings.iconStyle, isDownload = true) +
                SpeedSampler.format(sample.rxBytesPerSec, false, true)
            val up = iconFor(settings.iconStyle, isDownload = false) +
                SpeedSampler.format(sample.txBytesPerSec, false, true)
            listOf(up, down) to "$down   $up"
        } else {
            val value = when (settings.notificationMetric) {
                NotificationMetric.DOWNLOAD -> sample.rxBytesPerSec
                NotificationMetric.UPLOAD -> sample.txBytesPerSec
                NotificationMetric.COMBINED -> sample.rxBytesPerSec + sample.txBytesPerSec
            }
            val prefix = when (settings.notificationMetric) {
                NotificationMetric.DOWNLOAD -> iconFor(settings.iconStyle, isDownload = true)
                NotificationMetric.UPLOAD -> iconFor(settings.iconStyle, isDownload = false)
                NotificationMetric.COMBINED -> ""
            }
            val text = "$prefix${SpeedSampler.format(value, false, true)}"
            listOf(SpeedSampler.formatCompact(value)) to text
        }
        val icon = IconCompat.createWithBitmap(renderIconBitmap(lines, settings))
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(icon, contentText))
    }

    /**
     * Notification small icons are forced monochrome by the system (it
     * recolors based on the alpha channel), so this only needs opaque white
     * text on a transparent canvas — the actual on-screen tint is decided
     * by the OS/theme, not by this bitmap.
     *
     * Accepts one or more lines; with several lines the font shrinks so each
     * row still fits inside the icon. The system then downscales the icon to
     * a few dp in the status bar, so multi-line stays deliberately small.
     */
    private fun renderIconBitmap(lines: List<String>, settings: OverlaySettings): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val maxChars = lines.maxOfOrNull { it.length } ?: 0
        val band = (size - (lines.size - 1) * settings.notificationLineSpacing) / lines.size.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        lines.forEachIndexed { index, text ->
            val digitsCount = text.count { it.isDigit() }
            val hasDotOrComma = text.contains('.') || text.contains(',')
            val baseFontSizePct = if (settings.notificationAutoFit) 150 else settings.notificationFontSizePct
            val applyDynamicBoost = !settings.notificationAutoFit && digitsCount in 1..2 && !hasDotOrComma
            val lineFontSizePct = if (applyDynamicBoost) (baseFontSizePct * 1.2f).toInt() else baseFontSizePct

            paint.textSize = iconTextSize(size, band, maxChars, lines.size, lineFontSizePct)

            if (settings.notificationAutoFit) {
                val maxHeight = band * 1.0f
                if (paint.textSize > maxHeight) {
                    paint.textSize = maxHeight
                }
            }

            android.util.Log.d(
                "NetSpeedOverlay",
                "renderIconBitmap: line='$text', digits=$digitsCount, hasDotOrComma=$hasDotOrComma, boost=$applyDynamicBoost, sizeBeforeAutoFit=${paint.textSize}"
            )

            if (settings.notificationAutoFit) {
                val maxWidth = size * 0.95f
                val measuredWidth = paint.measureText(text)
                if (measuredWidth > maxWidth) {
                    val originalSize = paint.textSize
                    paint.textSize = originalSize * (maxWidth / measuredWidth)
                    android.util.Log.d(
                        "NetSpeedOverlay",
                        "renderIconBitmap autofit: line='$text', originalSize=$originalSize, newSize=${paint.textSize}, width=$measuredWidth, maxWidth=$maxWidth"
                    )
                }
            }

            val centerY = band / 2f + index * (band + settings.notificationLineSpacing)
            val baseline = centerY - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(text, size / 2f, baseline, paint)
        }
        return bitmap
    }

    /** Font size that fits both the widest line (width, measured against the
     * full icon width [size]) and the available per-line height ([band], which
     * already accounts for spacing), so stacked rows don't overlap. */
    private fun iconTextSize(size: Int, band: Float, maxChars: Int, lineCount: Int, fontSizePct: Int): Float {
        val widthBased = when (maxChars) {
            0, 1 -> size * 0.85f
            2 -> size * 0.75f
            3 -> size * 0.65f
            4 -> size * 0.52f
            else -> size * 0.40f
        }
        val heightBased = band * 0.92f
        val base = if (widthBased < heightBased) widthBased else heightBased
        return base * (fontSizePct / 100f)
    }

    // ---------------------------------------------------------------
    // Overlay view
    // ---------------------------------------------------------------

    private fun addOverlayView() {
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(0xAA000000.toInt()) // nero ~67% opaco
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(3), dp(8), dp(3))
            this.background = background
        }
        overlayBackground = background
        val download = TextView(this).apply {
            setTextColor(Color.WHITE)
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            setLineSpacing(0f, 1f)
        }
        val upload = TextView(this).apply {
            setTextColor(Color.WHITE)
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            setLineSpacing(0f, 1f)
        }
        root.addView(upload)
        root.addView(download)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        windowManager.addView(root, params)
        overlayRoot = root
        downloadText = download
        uploadText = upload
        layoutParams = params

        setupDragHandling(root)
    }

    /**
     * When free-position mode is on, lets the user drag the overlay anywhere
     * on screen and persists the final spot. No-op while anchored so the
     * indicator stays click-through as before.
     */
    private fun setupDragHandling(root: View) {
        var downRawX = 0f
        var downRawY = 0f
        var startXDp = 0
        var startYDp = 0
        var moved = false

        root.setOnTouchListener { _, event ->
            if (!currentSettings.freePosition) return@setOnTouchListener false
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startXDp = pxToDp(params.x)
                    startYDp = pxToDp(params.y)
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dxDp = pxToDp((event.rawX - downRawX).toInt())
                    val dyDp = pxToDp((event.rawY - downRawY).toInt())
                    if (kotlin.math.abs(event.rawX - downRawX) > 4 ||
                        kotlin.math.abs(event.rawY - downRawY) > 4
                    ) {
                        moved = true
                    }
                    params.x = dp((startXDp + dxDp).coerceAtLeast(0))
                    params.y = dp((startYDp + dyDp).coerceAtLeast(0))
                    runCatching { windowManager.updateViewLayout(root, params) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) {
                        val finalX = pxToDp(params.x)
                        val finalY = pxToDp(params.y)
                        lifecycleScope.launch { settingsRepository.setPosition(finalX, finalY) }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Tears the floating window down when switching to NOTIFICATION_ICON mode. */
    private fun removeOverlayView() {
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        overlayRoot = null
        downloadText = null
        uploadText = null
        overlayBackground = null
        layoutParams = null
    }

    private fun pxToDp(px: Int): Int = (px / resources.displayMetrics.density).toInt()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Height of the system navigation bar in px. 0 when there is no on-screen
     * bar (gesture navigation). Used only to position the overlay *inside* the
     * bar when anchored to the bottom. */
    private fun getNavigationBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    // ---------------------------------------------------------------
    // Settings -> layout/style
    // ---------------------------------------------------------------

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings

                // lastKnownMode starts null, so this also runs on the very
                // first emission — that's what creates (or skips) the
                // overlay window at startup based on the persisted mode.
                if (settings.indicatorMode != lastKnownMode) {
                    lastKnownMode = settings.indicatorMode
                    when (settings.indicatorMode) {
                        IndicatorMode.OVERLAY -> if (overlayRoot == null) addOverlayView()
                        IndicatorMode.NOTIFICATION_ICON -> removeOverlayView()
                    }
                }

                if (settings.indicatorMode == IndicatorMode.OVERLAY) {
                    applySettingsToView(settings)
                }
            }
        }
    }

    private fun observeAccessibilityState() {
        lifecycleScope.launch {
            SystemUiState.isFullscreen.collect { isFullscreen ->
                if (currentSettings.indicatorMode == IndicatorMode.OVERLAY) {
                    overlayRoot?.visibility = if (isFullscreen) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                }
            }
        }
    }

    private fun applySettingsToView(settings: OverlaySettings) {
        val params = layoutParams ?: return
        val root = overlayRoot ?: return

        if (settings.freePosition) {
            // Draggable anywhere: anchor to top-left and use saved x/y, and
            // make the window touchable so it can receive drag gestures.
            params.gravity = Gravity.TOP or Gravity.START
            params.x = dp(settings.posXDp)
            params.y = dp(settings.posYDp)
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            val vGravity = when (settings.verticalAnchor) {
                VerticalAnchor.TOP -> Gravity.TOP
                VerticalAnchor.BOTTOM -> Gravity.BOTTOM
            }
            // Positioned by an absolute horizontal percentage for both
            // anchors, so always anchor to the start edge and offset x manually.
            params.gravity = vGravity or Gravity.START
            val screenW = resources.displayMetrics.widthPixels
            val viewW = overlayRoot?.width?.takeIf { it > 0 } ?: dp(120)
            params.x = ((screenW * settings.horizontalOffsetPct / 100) - viewW / 2)
                .coerceIn(0, (screenW - viewW).coerceAtLeast(0))
            // Anchored to the bottom: with these window flags the BOTTOM
            // gravity sits at the TOP of the navigation bar, so subtract the
            // bar height to push the indicator down INTO the bar (its bottom
            // edge ends up `verticalOffsetDp` px above the screen bottom).
            params.y = if (settings.verticalAnchor == VerticalAnchor.BOTTOM) {
                dp(settings.verticalOffsetDp) - getNavigationBarHeight()
            } else {
                dp(settings.verticalOffsetDp)
            }
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { windowManager.updateViewLayout(root, params) }

        root.visibility = if (SystemUiState.isFullscreen.value) {
            View.GONE
        } else {
            View.VISIBLE
        }

        root.orientation = if (settings.displayMode == DisplayMode.STACKED) {
            LinearLayout.VERTICAL
        } else {
            LinearLayout.HORIZONTAL
        }

        listOf(downloadText, uploadText).forEach { tv ->
            tv?.textSize = settings.fontSizeSp.toFloat()
            tv?.typeface = if (settings.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        // Colore testo/sfondo: se "Abbina barra di sistema" è attivo, segui
        // l'aspetto live della navigation bar (chiaro/scuro), altrimenti i
        // valori di default (testo chiaro su sfondo scuro).
        applyOverlayColors(settings)

        // Spacing before the second row. Uses a LayoutParams margin (which can
        // be negative) instead of padding so that 0 dp really means "lines
        // touching": we subtract the font's own leading gap at 0 so the
        // residual fixed spacing disappears, and positive values add from there.
        (root.getChildAt(1) as? TextView)?.let { tv ->
            val lp = (tv.layoutParams as? LinearLayout.LayoutParams)
                ?: LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )

            if (settings.displayMode == DisplayMode.INLINE) {
                tv.setPadding(dp(6), 0, 0, 0)
                lp.leftMargin = 0
                lp.topMargin = 0
            } else {
                tv.setPadding(0, 0, 0, 0)
                lp.leftMargin = 0
                lp.topMargin = dp(settings.lineSpacingDp) - leadingGapPx(tv)
            }

            tv.layoutParams = lp
        }
    }

    /**
     * The font's intrinsic leading whitespace (the extra space above the
     * ascent and below the descent). Subtracting it from the top margin
     * cancels the fixed minimum gap between the two stacked lines, so a
     * line-spacing setting of 0 dp makes the rows visually touch.
     */
    private fun leadingGapPx(tv: TextView): Int {
        val fm = tv.paint.fontMetricsInt
        val topGap = fm.ascent - fm.top          // whitespace above the glyphs
        val bottomGap = fm.bottom - fm.descent   // whitespace below the glyphs
        return (topGap + bottomGap).coerceAtLeast(0)
    }

    /**
     * Applica il colore del testo e (se attivo) dello sfondo dell'overlay,
     * entrambi personalizzabili dall'utente.
     */
    private fun applyOverlayColors(settings: OverlaySettings) {
        downloadText?.setTextColor(settings.textColorArgb)
        uploadText?.setTextColor(settings.textColorArgb)
        if (settings.showBackground) {
            overlayBackground?.setColor(settings.backgroundColorArgb)
            overlayRoot?.background = overlayBackground
        } else {
            overlayRoot?.background = null
        }
    }

    // ---------------------------------------------------------------
    // Sampling loop
    // ---------------------------------------------------------------

    private fun startSamplingLoop() {
        samplingJob = lifecycleScope.launch {
            while (true) {
                val settings = currentSettings
                sampler.sample()?.let { sample ->
                    when (settings.indicatorMode) {
                        IndicatorMode.OVERLAY -> updateTexts(sample, settings)
                        IndicatorMode.NOTIFICATION_ICON -> updateNotificationIcon(sample, settings)
                    }
                }
                delay(settings.updateIntervalMs)
            }
        }
    }

    private fun updateTexts(sample: SpeedSampler.Sample, settings: OverlaySettings) {
        val downArrow = iconFor(settings.iconStyle, isDownload = true)
        val upArrow = iconFor(settings.iconStyle, isDownload = false)

        downloadText?.text = "$downArrow ${SpeedSampler.format(sample.rxBytesPerSec, settings.showPerSecondSuffix)}"
        uploadText?.text = "$upArrow ${SpeedSampler.format(sample.txBytesPerSec, settings.showPerSecondSuffix)}"

        val idle = sample.rxBytesPerSec < settings.idleThresholdBytesPerSec &&
            sample.txBytesPerSec < settings.idleThresholdBytesPerSec
        overlayRoot?.alpha = if (settings.dimWhenIdle && idle) settings.idleAlpha else 1f
    }

    private fun iconFor(style: IconStyle, isDownload: Boolean): String = when (style) {
        IconStyle.NONE -> ""
        IconStyle.ARROWS -> if (isDownload) "↓" else "↑"
        IconStyle.LETTERS -> if (isDownload) "D" else "U"
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "net_speed_overlay"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NetSpeedOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NetSpeedOverlayService::class.java))
        }

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
