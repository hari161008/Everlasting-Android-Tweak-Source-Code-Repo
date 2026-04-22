package com.coolappstore.everlastingandroidtweak.features.notiflight

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingColorMode
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingSide
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingStyle

class NotificationLightingHandler(private val service: AccessibilityService) {

    private var windowManager: WindowManager? = null
    private val overlayViews = mutableListOf<View>()
    private val handler = Handler(Looper.getMainLooper())

    // Config state
    private var cornerRadiusDp: Float = OverlayHelper.CORNER_RADIUS_DP.toFloat()
    private var strokeThicknessDp: Float = OverlayHelper.STROKE_DP.toFloat()
    var isPreview: Boolean = false
        private set
    private var ignoreScreenState: Boolean = false
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
    private var sweepPosition: String = "CENTER"
    private var sweepThickness: Float = 8f
    private var randomShapes: Boolean = false
    private var isAmbientDisplayRequested: Boolean = false
    private var isAmbientShowLockScreen: Boolean = false
    private var isInterrupted: Boolean = false

    // Queue for staggered playback
    private val intentQueue = java.util.ArrayDeque<Intent>()
    private var currentPackageShowing: String? = null

    fun handleIntent(intent: Intent) {
        if (intent.action != "SHOW_NOTIFICATION_LIGHTING") return

        val isPreviewIntent = intent.getBooleanExtra("is_preview", false)
        val removePreview   = intent.getBooleanExtra("remove_preview", false)

        if (removePreview) {
            removeOverlay(immediate = true)
            intentQueue.clear()
            currentPackageShowing = null
            return
        }

        if (isPreviewIntent) {
            removeOverlay(immediate = true)
            intentQueue.clear()
            currentPackageShowing = null
            extractIntentExtras(intent)
            showNotificationLighting()
            return
        }

        val pkg = intent.getStringExtra("package_name")
        if (pkg != null) {
            if (pkg == currentPackageShowing) return
            if (intentQueue.any { it.getStringExtra("package_name") == pkg }) return
        }

        intentQueue.add(intent)
        processQueue()
    }

    private fun extractIntentExtras(intent: Intent) {
        cornerRadiusDp    = intent.getFloatExtra("corner_radius_dp", OverlayHelper.CORNER_RADIUS_DP.toFloat())
        strokeThicknessDp = intent.getFloatExtra("stroke_thickness_dp", OverlayHelper.STROKE_DP.toFloat())
        isPreview         = intent.getBooleanExtra("is_preview", false)
        ignoreScreenState = intent.getBooleanExtra("ignore_screen_state", false)
        colorMode         = NotificationLightingColorMode.valueOf(intent.getStringExtra("color_mode") ?: "SYSTEM")
        customColor       = intent.getIntExtra("custom_color", 0)
        resolvedColor     = if (intent.hasExtra("resolved_color")) intent.getIntExtra("resolved_color", 0) else null
        pulseCount        = intent.getIntExtra("pulse_count", 1)
        pulseDuration     = intent.getLongExtra("pulse_duration", 3000)
        val styleName     = intent.getStringExtra("style")
        edgeLightingStyle = if (styleName != null) NotificationLightingStyle.valueOf(styleName) else NotificationLightingStyle.STROKE
        val glowSidesArray = intent.getStringArrayExtra("glow_sides")
        glowSides = glowSidesArray?.mapNotNull {
            try { NotificationLightingSide.valueOf(it) } catch (_: Exception) { null }
        }?.toSet() ?: setOf(NotificationLightingSide.LEFT, NotificationLightingSide.RIGHT)
        indicatorX     = intent.getFloatExtra("indicator_x", 50f)
        indicatorY     = intent.getFloatExtra("indicator_y", 2f)
        indicatorScale = intent.getFloatExtra("indicator_scale", 1.0f)
        isAmbientDisplayRequested = intent.getBooleanExtra("is_ambient_display", false)
        isAmbientShowLockScreen   = intent.getBooleanExtra("is_ambient_show_lock_screen", false)
        sweepPosition  = intent.getStringExtra("sweep_position") ?: "CENTER"
        sweepThickness = intent.getFloatExtra("sweep_thickness", 8f)
        randomShapes   = intent.getBooleanExtra("random_shapes", false)
        isInterrupted  = false
    }

    private fun processQueue() {
        if (currentPackageShowing != null || intentQueue.isEmpty()) return
        val nextIntent = intentQueue.poll() ?: return
        extractIntentExtras(nextIntent)
        currentPackageShowing = nextIntent.getStringExtra("package_name")
        showNotificationLighting()
    }

    fun onScreenOn() {
        if (!isPreview) {
            val prefs = service.getSharedPreferences("everlasting_notif_prefs", Context.MODE_PRIVATE)
            val onlyScreenOff = prefs.getBoolean("edge_lighting_only_screen_off", true)
            if (onlyScreenOff) removeOverlay(immediate = true)
        }
    }

    fun removeOverlay(immediate: Boolean = false) {
        val iterator = overlayViews.iterator()
        while (iterator.hasNext()) {
            val overlay = iterator.next()
            if (immediate) {
                try { windowManager?.removeView(overlay) } catch (_: Exception) {}
                iterator.remove()
            } else {
                try { OverlayHelper.fadeOutAndRemoveOverlay(windowManager, overlay, overlayViews) } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun showNotificationLighting() {
        windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val powerManager = service.getSystemService(Context.POWER_SERVICE) as PowerManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { WindowManager.LayoutParams::class.java.getField("TYPE_ACCESSIBILITY_OVERLAY").getInt(null) }
            catch (_: Exception) { WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        }

        try {
            val color = resolveColor()
            val strokeDpForStyle = if (edgeLightingStyle == NotificationLightingStyle.SWEEP) sweepThickness else strokeThicknessDp

            val overlay = OverlayHelper.createOverlayView(
                service, color,
                strokeDp = strokeDpForStyle,
                cornerRadiusDp = cornerRadiusDp,
                style = edgeLightingStyle,
                glowSides = glowSides,
                indicatorScale = indicatorScale,
                randomShapes = randomShapes
            )
            val params = OverlayHelper.createOverlayLayoutParams(overlayType)
            val isScreenOn = powerManager.isInteractive
            val showBackground = isAmbientDisplayRequested && !isScreenOn && !isPreview && !isAmbientShowLockScreen

            if (isAmbientDisplayRequested && !isScreenOn && !isPreview) {
                if (showBackground) {
                    val ambientOverlay = OverlayHelper.createOverlayView(
                        service, color,
                        strokeDp = strokeThicknessDp,
                        cornerRadiusDp = cornerRadiusDp,
                        style = edgeLightingStyle,
                        glowSides = glowSides,
                        indicatorScale = indicatorScale,
                        randomShapes = randomShapes,
                        showBackground = true
                    )
                    val ambientParams = OverlayHelper.createOverlayLayoutParams(overlayType, isTouchable = true)
                    ambientOverlay.setOnTouchListener { _, _ ->
                        isInterrupted = true; removeOverlay(); true
                    }
                    if (OverlayHelper.addOverlayView(windowManager, ambientOverlay, ambientParams)) {
                        overlayViews.add(ambientOverlay)
                        acquireWakeLock(powerManager)
                        handler.postDelayed({ if (!isInterrupted) startPulsing(ambientOverlay) }, 500)
                    }
                } else {
                    if (OverlayHelper.addOverlayView(windowManager, overlay, params)) {
                        overlayViews.add(overlay)
                        acquireWakeLock(powerManager)
                        handler.postDelayed({ startPulsing(overlay) }, 500)
                    }
                }
            } else {
                val prefs = service.getSharedPreferences("everlasting_notif_prefs", Context.MODE_PRIVATE)
                val onlyScreenOff = prefs.getBoolean("edge_lighting_only_screen_off", true)
                if (onlyScreenOff && !ignoreScreenState && !isPreview && isScreenOn) {
                    removeOverlay(); currentPackageShowing = null; processQueue(); return
                }
                if (OverlayHelper.addOverlayView(windowManager, overlay, params)) {
                    overlayViews.add(overlay)
                    if (isPreview) {
                        val sweepX = if (edgeLightingStyle == NotificationLightingStyle.SWEEP) {
                            when (sweepPosition) { "LEFT" -> 0f; "RIGHT" -> 100f; else -> 50f }
                        } else indicatorX
                        OverlayHelper.showPreview(
                            overlay, edgeLightingStyle, strokeDpForStyle,
                            indicatorX = sweepX, indicatorY = indicatorY, indicatorScale = indicatorScale,
                            pulseDurationMillis = pulseDuration
                        ) { currentPackageShowing = null; processQueue() }
                    } else {
                        startPulsing(overlay)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireWakeLock(powerManager: PowerManager) {
        try {
            @Suppress("DEPRECATION")
            val wl = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "everlasting:NotificationLighting"
            )
            wl.acquire(10000L)
        } catch (e: Exception) { Log.e("NotifLightingHandler", "Failed to wake screen", e) }
    }

    private fun resolveColor(): Int {
        if (resolvedColor != null) return resolvedColor!!
        if (colorMode == NotificationLightingColorMode.CUSTOM) return customColor
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.getColor(android.R.color.system_accent1_100)
        } else {
            0xFF8BCAFF.toInt()
        }
    }

    private fun startPulsing(overlay: View) {
        val strokeDpForStyle = if (edgeLightingStyle == NotificationLightingStyle.SWEEP) sweepThickness else strokeThicknessDp
        val sweepX = if (edgeLightingStyle == NotificationLightingStyle.SWEEP) {
            when (sweepPosition) { "LEFT" -> 0f; "RIGHT" -> 100f; else -> 50f }
        } else indicatorX

        OverlayHelper.pulseOverlay(
            overlay,
            maxPulses = if (isPreview) 1 else pulseCount,
            pulseDurationMillis = pulseDuration,
            style = edgeLightingStyle,
            strokeWidthDp = strokeDpForStyle,
            indicatorX = sweepX,
            indicatorY = indicatorY,
            indicatorScale = indicatorScale
        ) {
            if (isAmbientDisplayRequested && !isInterrupted && !isPreview && !isAmbientShowLockScreen) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                handler.postDelayed({
                    OverlayHelper.fadeOutAndRemoveOverlay(windowManager, overlay, overlayViews) {
                        currentPackageShowing = null; processQueue()
                    }
                }, 500)
            } else {
                OverlayHelper.fadeOutAndRemoveOverlay(windowManager, overlay, overlayViews) {
                    currentPackageShowing = null; processQueue()
                }
            }
        }
    }
}
