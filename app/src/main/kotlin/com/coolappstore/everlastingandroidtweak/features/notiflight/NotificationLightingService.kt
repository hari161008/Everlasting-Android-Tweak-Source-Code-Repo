package com.coolappstore.everlastingandroidtweak.features.notiflight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.coolappstore.everlastingandroidtweak.R
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingColorMode
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingSide
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingStyle
import com.coolappstore.everlastingandroidtweak.services.EverlastingAccessibilityService

class NotificationLightingService : Service() {

    private var windowManager: WindowManager? = null
    private val overlayViews = mutableListOf<View>()
    private var cornerRadiusDp: Float = OverlayHelper.CORNER_RADIUS_DP.toFloat()
    private var strokeThicknessDp: Float = OverlayHelper.STROKE_DP.toFloat()
    private var isPreview: Boolean = false
    private var colorMode: NotificationLightingColorMode = NotificationLightingColorMode.SYSTEM
    private var customColor: Int = 0
    private var resolvedColor: Int? = null
    private var pulseCount: Int = 1
    private var pulseDuration: Long = 3000
    private var edgeLightingStyle: NotificationLightingStyle = NotificationLightingStyle.STROKE
    private var glowSides: Set<NotificationLightingSide> =
        setOf(NotificationLightingSide.LEFT, NotificationLightingSide.RIGHT)
    private var indicatorX: Float = 50f
    private var indicatorY: Float = 2f
    private var indicatorScale: Float = 1.0f
    private var isAmbientDisplay: Boolean = false
    private var sweepThickness: Float = 8f
    private var sweepPosition: String = "CENTER"
    private var randomShapes: Boolean = false
    private var screenReceiver: BroadcastReceiver? = null

    companion object {
        private const val CHANNEL_ID = "everlasting_edge_lighting_channel"
        private const val NOTIF_ID = 24321
        const val ACTION_SHOW = "com.coolappstore.everlastingandroidtweak.SHOW_NOTIFICATION_LIGHTING"
        const val ACTION_REMOVE_PREVIEW = "com.coolappstore.everlastingandroidtweak.REMOVE_LIGHTING_PREVIEW"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    when (intent?.action) {
                        Intent.ACTION_SCREEN_OFF -> if (canDrawOverlays()) showOverlay()
                        Intent.ACTION_SCREEN_ON  -> if (canDrawOverlays()) showOverlay()
                    }
                } catch (_: Exception) {}
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, f)
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canDrawOverlays()) { stopSelf(); return START_NOT_STICKY }

        cornerRadiusDp    = intent?.getFloatExtra("corner_radius_dp", OverlayHelper.CORNER_RADIUS_DP.toFloat()) ?: OverlayHelper.CORNER_RADIUS_DP.toFloat()
        strokeThicknessDp = intent?.getFloatExtra("stroke_thickness_dp", OverlayHelper.STROKE_DP.toFloat()) ?: OverlayHelper.STROKE_DP.toFloat()
        isPreview         = intent?.getBooleanExtra("is_preview", false) ?: false
        colorMode         = NotificationLightingColorMode.valueOf(intent?.getStringExtra("color_mode") ?: NotificationLightingColorMode.SYSTEM.name)
        customColor       = intent?.getIntExtra("custom_color", 0) ?: 0
        resolvedColor     = if (intent?.hasExtra("resolved_color") == true) intent.getIntExtra("resolved_color", 0) else null
        pulseCount        = intent?.getIntExtra("pulse_count", 1) ?: 1
        pulseDuration     = intent?.getLongExtra("pulse_duration", 3000L) ?: 3000L
        edgeLightingStyle = NotificationLightingStyle.valueOf(intent?.getStringExtra("style") ?: NotificationLightingStyle.STROKE.name)
        val glowSidesArray = intent?.getStringArrayExtra("glow_sides")
        glowSides = glowSidesArray?.mapNotNull {
            try { NotificationLightingSide.valueOf(it) } catch (_: Exception) { null }
        }?.toSet() ?: setOf(NotificationLightingSide.LEFT, NotificationLightingSide.RIGHT)
        indicatorX     = intent?.getFloatExtra("indicator_x", 50f) ?: 50f
        indicatorY     = intent?.getFloatExtra("indicator_y", 2f) ?: 2f
        indicatorScale = intent?.getFloatExtra("indicator_scale", 1.0f) ?: 1.0f
        isAmbientDisplay  = intent?.getBooleanExtra("is_ambient_display", false) ?: false
        sweepThickness    = intent?.getFloatExtra("sweep_thickness", 8f) ?: 8f
        sweepPosition     = intent?.getStringExtra("sweep_position") ?: "CENTER"
        randomShapes      = intent?.getBooleanExtra("random_shapes", false) ?: false
        val removePreview = intent?.getBooleanExtra("remove_preview", false) ?: false

        if (removePreview) {
            // Delegate remove to accessibility service as well
            delegateToAccessibilityService(intent!!)
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }

        val isForegroundStart = intent?.getBooleanExtra("is_foreground_start", false) ?: false
        if (isForegroundStart && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { startForeground(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
        }

        // Prefer accessibility overlay (higher elevation, shown on lock screen)
        if (isAccessibilityServiceEnabled()) {
            delegateToAccessibilityService(intent ?: Intent())
            if (isForegroundStart && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Handler(Looper.getMainLooper()).postDelayed({
                    try { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } catch (_: Exception) {}
                }, 500)
            } else {
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (!isForegroundStart && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { startForeground(NOTIF_ID, buildNotification()) } catch (_: Exception) {}
        }
        showOverlay()
        return START_NOT_STICKY
    }

    private fun delegateToAccessibilityService(src: Intent) {
        try {
            val ai = Intent(applicationContext, EverlastingAccessibilityService::class.java).apply {
                action = "SHOW_NOTIFICATION_LIGHTING"
                // copy all extras
                src.extras?.keySet()?.forEach { key ->
                    @Suppress("DEPRECATION")
                    src.extras?.get(key)?.let { v ->
                        when (v) {
                            is Boolean      -> putExtra(key, v)
                            is Int          -> putExtra(key, v)
                            is Float        -> putExtra(key, v)
                            is Long         -> putExtra(key, v)
                            is String       -> putExtra(key, v)
                            is Array<*>     -> putExtra(key, v.filterIsInstance<String>().toTypedArray())
                            else            -> {}
                        }
                    }
                }
            }
            applicationContext.startService(ai)
        } catch (_: Exception) {}
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.rounded_magnify_fullscreen_24)
            .setContentTitle("")
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val chan = NotificationChannel(CHANNEL_ID, "Notification Lighting", NotificationManager.IMPORTANCE_LOW)
                chan.setSound(null, null)
                nm.createNotificationChannel(chan)
            }
        }
    }

    private fun showOverlay() {
        if (isPreview && overlayViews.isNotEmpty()) removeOverlay()
        if (overlayViews.isNotEmpty()) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        try {
            val color = resolveColor()
            val strokeDpForStyle = if (edgeLightingStyle == NotificationLightingStyle.SWEEP) sweepThickness else strokeThicknessDp
            val sweepX = when (sweepPosition) { "LEFT" -> 0f; "RIGHT" -> 100f; else -> 50f }

            val overlay = OverlayHelper.createOverlayView(
                this, color,
                strokeDp = strokeDpForStyle,
                cornerRadiusDp = cornerRadiusDp,
                style = edgeLightingStyle,
                glowSides = glowSides,
                indicatorScale = indicatorScale,
                randomShapes = randomShapes,
                showBackground = isAmbientDisplay
            )
            val params = OverlayHelper.createOverlayLayoutParams(getOverlayType())

            if (OverlayHelper.addOverlayView(windowManager, overlay, params)) {
                overlayViews.add(overlay)
                if (isPreview) {
                    OverlayHelper.showPreview(
                        overlay, edgeLightingStyle, strokeDpForStyle,
                        indicatorX = if (edgeLightingStyle == NotificationLightingStyle.SWEEP) sweepX else indicatorX,
                        indicatorY = indicatorY, indicatorScale = indicatorScale,
                        pulseDurationMillis = pulseDuration
                    )
                } else {
                    OverlayHelper.pulseOverlay(
                        overlay, maxPulses = pulseCount, pulseDurationMillis = pulseDuration,
                        style = edgeLightingStyle, strokeWidthDp = strokeDpForStyle,
                        indicatorX = if (edgeLightingStyle == NotificationLightingStyle.SWEEP) sweepX else indicatorX,
                        indicatorY = indicatorY, indicatorScale = indicatorScale
                    ) {
                        OverlayHelper.fadeOutAndRemoveOverlay(windowManager, overlay, overlayViews) {
                            if (overlayViews.isEmpty()) {
                                try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) stopForeground(true) } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NotifLightingService", "showOverlay failed", e)
        }
    }

    private fun resolveColor(): Int {
        if (resolvedColor != null) return resolvedColor!!
        if (colorMode == NotificationLightingColorMode.CUSTOM) return customColor
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getColor(android.R.color.system_accent1_100)
        } else {
            0xFF8BCAFF.toInt()
        }
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun removeOverlay() {
        overlayViews.forEach { view ->
            OverlayHelper.fadeOutAndRemoveOverlay(windowManager, view, overlayViews) {
                if (overlayViews.isEmpty()) {
                    try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) stopForeground(true) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val name = "${packageName}/${EverlastingAccessibilityService::class.java.name}"
            enabled?.contains(name) == true
        } catch (_: Exception) { false }
    }
}
