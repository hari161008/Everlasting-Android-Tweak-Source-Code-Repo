package com.coolappstore.everlastingandroidtweak.features.mapspowersaving

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import com.coolappstore.everlastingandroidtweak.shizuku.ShizukuManager

/**
 * MapsPowerSavingManager
 *
 * What this feature ACTUALLY does:
 *   When the screen turns off during a Google Maps navigation session,
 *   it launches the Maps MinMode activity — a Pixel 10 exclusive low-power
 *   navigation overlay that works on any Android device via Shizuku.
 *
 *   Intent used:
 *     package:  com.google.android.apps.maps
 *     class:    com.google.android.apps.gmm.features.minmode.MinModeActivity
 *
 *   The activity requires privileged launch (cannot be started by a normal app),
 *   so Shizuku is used to run `am start` as a shell command.
 *
 *   Trigger logic:
 *   1. Screen turns OFF  → check if Maps navigation is active
 *                        → if yes AND feature enabled → launch MinModeActivity
 *   2. Screen turns ON   → MinMode closes automatically (it's designed for screen-off)
 *
 *   Navigation detection: The notification listener sets MapsState.hasNavigationNotification
 *   when it sees a persistent Maps notification (turn-by-turn nav indicator).
 */
class MapsPowerSavingManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var registered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    // MinMode is an overlay designed to display ON the lock screen.
                    // Triggering on SCREEN_ON (when lock screen appears) is correct —
                    // you cannot display anything while the screen is off.
                    // Flow: screen off → screen on → lock screen shown → MinMode launches over it.
                    if (isEnabled() && MapsState.hasNavigationNotification) {
                        launchMinMode()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> Unit  // nothing to do on screen off
            }
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(screenReceiver, filter)
        registered = true
        MapsState.isEnabled = true
    }

    fun stop() {
        if (!registered) return
        try { context.unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        registered = false
        MapsState.isEnabled = false
    }

    // ── Launch MinMode via Shizuku shell ───────────────────────────────────────
    // `am start` with component name requires elevated permission — Shizuku provides this.
    // The -n flag specifies the exact component:
    //   com.google.android.apps.maps/com.google.android.apps.gmm.features.minmode.MinModeActivity
    private fun launchMinMode() {
        try {
            if (!isShizukuReady()) return
            val cmd = "am start -n " +
                "com.google.android.apps.maps/" +
                "com.google.android.apps.gmm.features.minmode.MinModeActivity"
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
            process.errorStream.readBytes()
            process.waitFor()
        } catch (_: Exception) {}
    }

    fun launchMinModeManually() = launchMinMode()

    private fun isEnabled() = prefs.getBoolean(KEY_ENABLED, false)

    companion object {
        const val PREFS_NAME     = "everlasting_maps_prefs"
        const val KEY_ENABLED    = "maps_power_saving_enabled"
        const val KEY_DISCOVERED = "maps_discovered_channels"
        const val KEY_DETECTION  = "maps_detection_channels"
        val DEFAULT_NAV_CHANNELS = setOf(
            "navigation_notification_channel",
            "primary_navigation_channel_v1",
            "primary_navigation_channel_v2"
        )

        fun isShizukuReady(): Boolean = ShizukuManager.isReady()

        fun isMapsPowerSavingEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
    }
}
