package com.coolappstore.everlastingandroidtweak.features.notiflight

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.StatusBarNotification
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingColorMode
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingSide
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingStyle
import com.coolappstore.everlastingandroidtweak.services.EverlastingAccessibilityService
import com.coolappstore.everlastingandroidtweak.utils.FlashlightUtil
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mirrors Essentials NotificationListener exactly.
 * ALL settings read from SharedPreferences "everlasting_notif_prefs"
 * (the same prefs the UI writes to).
 */
class NotifLightManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs get() = context.getSharedPreferences("everlasting_notif_prefs", Context.MODE_PRIVATE)

    fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == context.packageName) return

        val edgeEnabled  = prefs.getBoolean("edge_lighting_enabled", false)
        val flashEnabled = prefs.getBoolean("flashlight_pulse_enabled", false)

        if (!edgeEnabled && !flashEnabled) return
        if (!canDrawOverlays()) return

        // Screen-off check (shared by both features)
        val onlyScreenOff = prefs.getBoolean("edge_lighting_only_screen_off", true)
        if (onlyScreenOff) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            if (pm.isInteractive) return
        }

        if (edgeEnabled) triggerEdgeLighting()
        if (flashEnabled) triggerFlashPulse()
    }

    // ── Edge Lighting ─────────────────────────────────────────────────────────

    private fun triggerEdgeLighting() {
        val cornerRadius    = try { prefs.getFloat("edge_lighting_corner_radius", 20f) }
                              catch (_: ClassCastException) { prefs.getInt("edge_lighting_corner_radius", 20).toFloat() }
        val strokeThickness = try { prefs.getFloat("edge_lighting_stroke_thickness", 8f) }
                              catch (_: ClassCastException) { prefs.getInt("edge_lighting_stroke_thickness", 8).toFloat() }
        val colorModeName   = prefs.getString("edge_lighting_color_mode", NotificationLightingColorMode.SYSTEM.name)
        val colorMode       = NotificationLightingColorMode.valueOf(colorModeName ?: NotificationLightingColorMode.SYSTEM.name)
        val pulseCount      = try { prefs.getInt("edge_lighting_pulse_count", 1) }
                              catch (_: ClassCastException) { prefs.getFloat("edge_lighting_pulse_count", 1f).toInt() }
        val pulseDuration   = try { prefs.getFloat("edge_lighting_pulse_duration", 3000f).toLong() }
                              catch (_: ClassCastException) { prefs.getInt("edge_lighting_pulse_duration", 3000).toLong() }
        val styleName       = prefs.getString("edge_lighting_style", NotificationLightingStyle.STROKE.name)
        val glowSidesJson   = prefs.getString("edge_lighting_glow_sides", null)
        val glowSides: Set<NotificationLightingSide> = if (glowSidesJson != null) {
            try { Gson().fromJson(glowSidesJson, Array<NotificationLightingSide>::class.java).toSet() }
            catch (_: Exception) { defaultGlowSides() }
        } else { defaultGlowSides() }
        val indicatorX     = try { prefs.getFloat("edge_lighting_indicator_x", 50f) }
                             catch (_: ClassCastException) { prefs.getInt("edge_lighting_indicator_x", 50).toFloat() }
        val indicatorY     = try { prefs.getFloat("edge_lighting_indicator_y", 2f) }
                             catch (_: ClassCastException) { prefs.getInt("edge_lighting_indicator_y", 2).toFloat() }
        val indicatorScale = try { prefs.getFloat("edge_lighting_indicator_scale", 1.0f) }
                             catch (_: ClassCastException) { prefs.getInt("edge_lighting_indicator_scale", 1).toFloat() }
        val isAmbient      = prefs.getBoolean("edge_lighting_ambient_display", false)
        val ambientLock    = prefs.getBoolean("edge_lighting_ambient_show_lock_screen", false)
        val sweepThickness = try { prefs.getFloat("edge_lighting_sweep_thickness", 8f) }
                             catch (_: ClassCastException) { prefs.getInt("edge_lighting_sweep_thickness", 8).toFloat() }
        val sweepPosition  = prefs.getString("edge_lighting_sweep_position", "CENTER") ?: "CENTER"
        val randomShapes   = prefs.getBoolean("edge_lighting_sweep_random_shapes", false)

        // Map Everlasting UI style names → Essentials enum + resolve color
        val (style, sides, resolvedColor, customColor) = resolveStyleAndColor(styleName, colorMode, glowSides)

        val intent = buildIntent(
            cornerRadius, strokeThickness, colorMode, style, sides,
            indicatorX, indicatorY, indicatorScale, pulseCount, pulseDuration,
            isPreview = false, isAmbient = isAmbient, ambientLock = ambientLock,
            resolvedColor = resolvedColor, customColor = customColor,
            sweepThickness = sweepThickness, sweepPosition = sweepPosition, randomShapes = randomShapes
        )
        startService(intent)
    }

    private data class StyleData(
        val style: NotificationLightingStyle,
        val sides: Set<NotificationLightingSide>,
        val resolvedColor: Int?,
        val customColor: Int
    )

    private fun resolveStyleAndColor(
        styleName: String?,
        colorMode: NotificationLightingColorMode,
        glowSides: Set<NotificationLightingSide>
    ): StyleData {
        val style = when (styleName) {
            "Pulse Wave", NotificationLightingStyle.GLOW.name      -> NotificationLightingStyle.GLOW
            "Indicator",  NotificationLightingStyle.INDICATOR.name -> NotificationLightingStyle.INDICATOR
            "Sweep",      NotificationLightingStyle.SWEEP.name     -> NotificationLightingStyle.SWEEP
            else                                                    -> NotificationLightingStyle.STROKE
        }
        val sides = if (style == NotificationLightingStyle.GLOW && glowSides.isNotEmpty()) glowSides
                    else when (styleName) {
                        "Left + Right" -> setOf(NotificationLightingSide.LEFT, NotificationLightingSide.RIGHT)
                        "Top Only"     -> setOf(NotificationLightingSide.TOP)
                        "Bottom Only"  -> setOf(NotificationLightingSide.BOTTOM)
                        else           -> defaultGlowSides()
                    }

        val customColor = if (colorMode == NotificationLightingColorMode.CUSTOM)
            prefs.getInt("edge_lighting_custom_color", 0xFF6200EE.toInt()) else 0

        // Build resolved color from hex + alpha
        val colorHex = prefs.getString("edge_lighting_color", "#8BCAFF") ?: "#8BCAFF"
        val alphaF   = try { prefs.getFloat("edge_lighting_alpha", 0.9f) }
                       catch (_: ClassCastException) { prefs.getInt("edge_lighting_alpha", 1).toFloat() }
        val base     = try { android.graphics.Color.parseColor(colorHex) } catch (_: Exception) { 0xFF8BCAFF.toInt() }
        val alphaInt = (alphaF * 255).toInt().coerceIn(0, 255)
        val colorWithAlpha = android.graphics.Color.argb(alphaInt,
            android.graphics.Color.red(base), android.graphics.Color.green(base), android.graphics.Color.blue(base))

        val resolvedColor = when (colorMode) {
            NotificationLightingColorMode.CUSTOM -> customColor
            else -> colorWithAlpha
        }

        return StyleData(style, sides, resolvedColor, customColor)
    }

    // ── Flash Pulse ───────────────────────────────────────────────────────────

    private fun triggerFlashPulse() {
        scope.launch {
            val camId = FlashlightUtil.getTorchCameraId(context) ?: return@launch
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    FlashlightUtil.isIntensitySupported(context, camId)) {
                    // Essentials-style: fade in/out
                    val maxLevel = FlashlightUtil.getMaxLevel(context, camId)
                    val pulseLevel = (maxLevel * 0.2f).toInt().coerceAtLeast(1)
                    FlashlightUtil.fadeFlashlight(context, camId, fromLevel = 0, toLevel = pulseLevel, durationMs = 600L, steps = 40)
                    kotlinx.coroutines.delay(800L)
                    FlashlightUtil.fadeFlashlight(context, camId, fromLevel = pulseLevel, toLevel = 0, durationMs = 600L, steps = 40)
                } else {
                    // Fallback: simple on/off
                    val count   = try { prefs.getInt("notif_flash_count", 3) } catch (_: Exception) { 3 }
                    val speedMs = try { prefs.getInt("notif_flash_speed_ms", 150) } catch (_: Exception) { 150 }
                    FlashlightUtil.pulseFlashlight(context, camId, count, speedMs.toLong())
                }
            } catch (_: Exception) {}
        }
    }

    // ── Preview (called from UI) ──────────────────────────────────────────────

    fun previewEdgeLighting(
        colorHex: String,
        durationMs: Long,
        thicknessDp: Float,
        alphaF: Float,
        style: String
    ) {
        if (!canDrawOverlays()) return
        val mappedStyle = when (style) {
            "Pulse Wave" -> NotificationLightingStyle.GLOW
            "Indicator"  -> NotificationLightingStyle.INDICATOR
            "Sweep"      -> NotificationLightingStyle.SWEEP
            else         -> NotificationLightingStyle.STROKE
        }
        val sides = when (style) {
            "Left + Right" -> setOf(NotificationLightingSide.LEFT, NotificationLightingSide.RIGHT)
            "Top Only"     -> setOf(NotificationLightingSide.TOP)
            "Bottom Only"  -> setOf(NotificationLightingSide.BOTTOM)
            else           -> defaultGlowSides()
        }
        val base = try { android.graphics.Color.parseColor(colorHex) } catch (_: Exception) { 0xFF8BCAFF.toInt() }
        val a    = (alphaF * 255).toInt().coerceIn(0, 255)
        val color = android.graphics.Color.argb(a,
            android.graphics.Color.red(base), android.graphics.Color.green(base), android.graphics.Color.blue(base))

        startService(buildIntent(
            cornerRadiusDp = 20f, strokeThicknessDp = thicknessDp,
            colorMode = NotificationLightingColorMode.CUSTOM,
            style = mappedStyle, glowSides = sides,
            indicatorX = 50f, indicatorY = 2f, indicatorScale = 1.0f,
            pulseCount = 1, pulseDuration = durationMs,
            isPreview = true, isAmbient = false, ambientLock = false,
            resolvedColor = color, customColor = color,
            ignoreScreenState = true
        ))
    }

    fun removePreviewOverlay() {
        try {
            context.startService(Intent(context, NotificationLightingService::class.java).apply {
                putExtra("remove_preview", true)
            })
        } catch (_: Exception) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun defaultGlowSides() = setOf(
        NotificationLightingSide.LEFT, NotificationLightingSide.RIGHT,
        NotificationLightingSide.TOP,  NotificationLightingSide.BOTTOM
    )

    private fun buildIntent(
        cornerRadiusDp: Float, strokeThicknessDp: Float,
        colorMode: NotificationLightingColorMode,
        style: NotificationLightingStyle, glowSides: Set<NotificationLightingSide>,
        indicatorX: Float, indicatorY: Float, indicatorScale: Float,
        pulseCount: Int, pulseDuration: Long,
        isPreview: Boolean, isAmbient: Boolean, ambientLock: Boolean,
        resolvedColor: Int?, customColor: Int,
        ignoreScreenState: Boolean = false,
        sweepThickness: Float = 8f,
        sweepPosition: String = "CENTER",
        randomShapes: Boolean = false
    ): Intent = Intent(context, NotificationLightingService::class.java).apply {
        putExtra("corner_radius_dp",           cornerRadiusDp)
        putExtra("stroke_thickness_dp",        strokeThicknessDp)
        putExtra("color_mode",                 colorMode.name)
        putExtra("style",                      style.name)
        putExtra("glow_sides",                 glowSides.map { it.name }.toTypedArray())
        putExtra("indicator_x",                indicatorX)
        putExtra("indicator_y",                indicatorY)
        putExtra("indicator_scale",            indicatorScale)
        putExtra("pulse_count",                pulseCount)
        putExtra("pulse_duration",             pulseDuration)
        putExtra("is_preview",                 isPreview)
        putExtra("is_ambient_display",         isAmbient)
        putExtra("is_ambient_show_lock_screen", ambientLock)
        putExtra("ignore_screen_state",        ignoreScreenState)
        putExtra("custom_color",               customColor)
        putExtra("sweep_thickness",            sweepThickness)
        putExtra("sweep_position",             sweepPosition)
        putExtra("random_shapes",              randomShapes)
        if (resolvedColor != null) putExtra("resolved_color", resolvedColor)
    }

    private fun startService(intent: Intent) {
        try {
            if (isAccessibilityEnabled()) {
                context.startService(intent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.putExtra("is_foreground_start", true)
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}
    }

    private fun canDrawOverlays() = Settings.canDrawOverlays(context)

    private fun isAccessibilityEnabled(): Boolean = try {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val name = "${context.packageName}/${EverlastingAccessibilityService::class.java.name}"
        enabled?.contains(name) == true
    } catch (_: Exception) { false }

    fun release() { scope.cancel() }
}
