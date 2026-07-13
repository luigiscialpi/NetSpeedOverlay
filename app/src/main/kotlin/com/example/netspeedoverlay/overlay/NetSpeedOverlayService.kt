package com.example.netspeedoverlay.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.netspeedoverlay.MainActivity
import com.example.netspeedoverlay.R
import com.example.netspeedoverlay.data.DisplayMode
import com.example.netspeedoverlay.data.HorizontalPosition
import com.example.netspeedoverlay.data.IconStyle
import com.example.netspeedoverlay.data.OverlaySettings
import com.example.netspeedoverlay.data.SettingsRepository
import com.example.netspeedoverlay.speed.SpeedSampler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the floating network-speed indicator.
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
    private var layoutParams: WindowManager.LayoutParams? = null

    private var currentSettings: OverlaySettings = OverlaySettings()
    private var samplingJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepository = SettingsRepository(applicationContext)
        startForegroundWithNotification()
        addOverlayView()
        observeSettings()
        startSamplingLoop()
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
        val channelId = "net_speed_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setSmallIcon(R.drawable.ic_speed_notification)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

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
        ServiceCompat.startForeground(this, 1, notification, fgsType)
    }

    // ---------------------------------------------------------------
    // Overlay view
    // ---------------------------------------------------------------

    private fun addOverlayView() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(0xAA000000.toInt()) // nero ~67% opaco
            }
        }
        val download = TextView(this).apply { setTextColor(Color.WHITE) }
        val upload = TextView(this).apply { setTextColor(Color.WHITE) }
        root.addView(download)
        root.addView(upload)

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
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------
    // Settings -> layout/style
    // ---------------------------------------------------------------

    private fun observeSettings() {
        lifecycleScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings
                applySettingsToView(settings)
            }
        }
    }

    private fun applySettingsToView(settings: OverlaySettings) {
        val params = layoutParams ?: return
        val root = overlayRoot ?: return

        params.gravity = when (settings.horizontalPosition) {
            HorizontalPosition.LEFT -> Gravity.TOP or Gravity.START
            HorizontalPosition.CENTER -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            HorizontalPosition.RIGHT -> Gravity.TOP or Gravity.END
        }
        params.y = dp(settings.verticalOffsetDp)
        runCatching { windowManager.updateViewLayout(root, params) }

        root.orientation = if (settings.displayMode == DisplayMode.STACKED) {
            LinearLayout.VERTICAL
        } else {
            LinearLayout.HORIZONTAL
        }

        listOf(downloadText, uploadText).forEach { tv ->
            tv?.textSize = settings.fontSizeSp.toFloat()
            tv?.typeface = if (settings.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        uploadText?.setPadding(if (settings.displayMode == DisplayMode.INLINE) dp(6) else 0, 0, 0, 0)
    }

    // ---------------------------------------------------------------
    // Sampling loop
    // ---------------------------------------------------------------

    private fun startSamplingLoop() {
        samplingJob = lifecycleScope.launch {
            while (true) {
                val settings = currentSettings
                sampler.sample()?.let { updateTexts(it, settings) }
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
        fun start(context: Context) {
            context.startForegroundService(Intent(context, NetSpeedOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NetSpeedOverlayService::class.java))
        }

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
