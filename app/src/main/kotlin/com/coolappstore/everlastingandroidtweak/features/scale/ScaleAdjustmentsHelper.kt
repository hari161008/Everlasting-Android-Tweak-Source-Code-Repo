package com.coolappstore.everlastingandroidtweak.features.scale

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.coolappstore.everlastingandroidtweak.shizuku.ShizukuManager
import com.coolappstore.everlastingandroidtweak.shizuku.ShizukuProcessHelper
import org.json.JSONObject

object ScaleAdjustmentsHelper {

    private const val PREFS_NAME = "everlasting_scale_prefs"
    private const val KEY_MODE = "scale_mode"
    private const val KEY_DEFAULT_PROFILE = "scale_profile_default"
    private const val KEY_GLOVE_PROFILE = "scale_profile_glove"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Mode ──────────────────────────────────────────────────────────────────

    fun getMode(context: Context): String =
        prefs(context).getString(KEY_MODE, "default") ?: "default"

    fun setMode(context: Context, mode: String) =
        prefs(context).edit().putString(KEY_MODE, mode).apply()

    // ── Read live system values ───────────────────────────────────────────────

    fun readCurrentSystemValues(context: Context): ScaleAnimationsProfile {
        val cr = context.contentResolver
        val fontScale = try {
            Settings.System.getFloat(cr, Settings.System.FONT_SCALE)
        } catch (_: Exception) { 1.0f }
        val animatorDuration = try {
            Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE)
        } catch (_: Exception) { 1.0f }
        val transitionAnim = try {
            Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE)
        } catch (_: Exception) { 1.0f }
        val windowAnim = try {
            Settings.Global.getFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE)
        } catch (_: Exception) { 1.0f }
        val autoRotate = try {
            Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
        } catch (_: Exception) { false }
        val screenTimeout = try {
            Settings.System.getLong(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000L)
        } catch (_: Exception) { 30000L }
        val smallestWidth = getSmallestWidth(context)
        return ScaleAnimationsProfile(
            fontScale = fontScale,
            animatorDurationScale = animatorDuration,
            transitionAnimationScale = transitionAnim,
            windowAnimationScale = windowAnim,
            smallestWidth = smallestWidth,
            autoRotateEnabled = autoRotate,
            screenTimeoutMs = screenTimeout
        )
    }

    fun getSmallestWidth(context: Context): Int {
        val cr = context.contentResolver
        val forcedDensity = try {
            Settings.Secure.getInt(cr, "display_density_forced")
        } catch (_: Exception) { 0 }
        if (forcedDensity > 0) {
            val metrics = context.resources.displayMetrics
            val widthPx = minOf(metrics.widthPixels, metrics.heightPixels)
            return (widthPx * 160) / forcedDensity
        }
        return context.resources.configuration.smallestScreenWidthDp
    }

    fun getFontWeight(context: Context): Int {
        return try {
            Settings.Secure.getInt(context.contentResolver, "font_weight_adjustment")
        } catch (_: Exception) { 0 }
    }

    fun getTouchSensitivity(context: Context): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, "touch_sensitivity") == 1
        } catch (_: Exception) { false }
    }

    // ── Apply individual settings ─────────────────────────────────────────────

    fun setFontScale(context: Context, scale: Float) {
        try {
            Settings.System.putFloat(context.contentResolver, Settings.System.FONT_SCALE, scale)
        } catch (_: Exception) {}
    }

    fun setFontWeight(context: Context, weight: Int) {
        try {
            Settings.Secure.putInt(context.contentResolver, "font_weight_adjustment", weight)
        } catch (_: Exception) {}
    }

    fun setAnimationScale(context: Context, key: String, scale: Float) {
        try {
            Settings.Global.putFloat(context.contentResolver, key, scale)
        } catch (_: Exception) {}
    }

    fun setAutoRotate(context: Context, enabled: Boolean) {
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enabled) 1 else 0
            )
        } catch (_: Exception) {}
    }

    fun setScreenTimeout(context: Context, timeoutMs: Long) {
        try {
            Settings.System.putLong(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                timeoutMs
            )
        } catch (_: Exception) {}
    }

    fun setTouchSensitivity(context: Context, enabled: Boolean) {
        try {
            Settings.System.putInt(context.contentResolver, "touch_sensitivity", if (enabled) 1 else 0)
        } catch (_: Exception) {}
    }

    fun setSmallestWidth(context: Context, widthDp: Int) {
        prefs(context).edit().putInt("scale_smallest_width", widthDp).apply()
        val metrics = context.resources.displayMetrics
        val widthPx = minOf(metrics.widthPixels, metrics.heightPixels)
        val density = (widthPx * 160) / widthDp
        val command = "wm density $density"
        if (ShizukuManager.isReady()) {
            if (!ShizukuProcessHelper.runCommand(command)) {
                try {
                    Settings.Secure.putInt(context.contentResolver, "display_density_forced", density)
                } catch (_: Exception) {}
            }
        } else {
            try {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            } catch (_: Exception) {}
            try {
                Settings.Secure.putInt(context.contentResolver, "display_density_forced", density)
            } catch (_: Exception) {}
        }
    }

    fun resetSmallestWidth(context: Context) {
        prefs(context).edit().remove("scale_smallest_width").apply()
        val command = "wm density reset"
        if (ShizukuManager.isReady()) {
            ShizukuProcessHelper.runCommand(command)
        } else {
            try {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            } catch (_: Exception) {}
            try {
                Settings.Secure.putString(context.contentResolver, "display_density_forced", null)
            } catch (_: Exception) {}
        }
    }

    // ── Apply a full profile to the system ────────────────────────────────────

    fun applyProfile(context: Context, profile: ScaleAnimationsProfile) {
        setFontScale(context, profile.fontScale)
        setAnimationScale(context, Settings.Global.ANIMATOR_DURATION_SCALE, profile.animatorDurationScale)
        setAnimationScale(context, Settings.Global.TRANSITION_ANIMATION_SCALE, profile.transitionAnimationScale)
        setAnimationScale(context, Settings.Global.WINDOW_ANIMATION_SCALE, profile.windowAnimationScale)
        setAutoRotate(context, profile.autoRotateEnabled)
        setScreenTimeout(context, profile.screenTimeoutMs)
        if (profile.smallestWidth > 0) {
            setSmallestWidth(context, profile.smallestWidth)
        }
    }

    // ── Profile persistence ───────────────────────────────────────────────────

    fun saveProfile(context: Context, mode: String, profile: ScaleAnimationsProfile) {
        val key = if (mode == "glove") KEY_GLOVE_PROFILE else KEY_DEFAULT_PROFILE
        prefs(context).edit().putString(key, profileToJson(profile)).apply()
    }

    fun loadProfile(context: Context, mode: String): ScaleAnimationsProfile {
        val key = if (mode == "glove") KEY_GLOVE_PROFILE else KEY_DEFAULT_PROFILE
        val json = prefs(context).getString(key, null) ?: return defaultProfile(mode)
        return try { profileFromJson(json) } catch (_: Exception) { defaultProfile(mode) }
    }

    private fun defaultProfile(mode: String): ScaleAnimationsProfile =
        if (mode == "glove") {
            ScaleAnimationsProfile(
                fontScale = 1.25f,
                smallestWidth = 385,
                autoRotateEnabled = true,
                screenTimeoutMs = 60000L
            )
        } else {
            ScaleAnimationsProfile()
        }

    // ── JSON serialization ────────────────────────────────────────────────────

    private fun profileToJson(p: ScaleAnimationsProfile): String =
        JSONObject().apply {
            put("fontScale", p.fontScale.toDouble())
            put("animatorDurationScale", p.animatorDurationScale.toDouble())
            put("transitionAnimationScale", p.transitionAnimationScale.toDouble())
            put("windowAnimationScale", p.windowAnimationScale.toDouble())
            put("smallestWidth", p.smallestWidth)
            put("autoRotateEnabled", p.autoRotateEnabled)
            put("screenTimeoutMs", p.screenTimeoutMs)
        }.toString()

    private fun profileFromJson(json: String): ScaleAnimationsProfile {
        val obj = JSONObject(json)
        return ScaleAnimationsProfile(
            fontScale = obj.optDouble("fontScale", 1.0).toFloat(),
            animatorDurationScale = obj.optDouble("animatorDurationScale", 1.0).toFloat(),
            transitionAnimationScale = obj.optDouble("transitionAnimationScale", 1.0).toFloat(),
            windowAnimationScale = obj.optDouble("windowAnimationScale", 1.0).toFloat(),
            smallestWidth = obj.optInt("smallestWidth", 360),
            autoRotateEnabled = obj.optBoolean("autoRotateEnabled", false),
            screenTimeoutMs = obj.optLong("screenTimeoutMs", 30000L)
        )
    }
}
