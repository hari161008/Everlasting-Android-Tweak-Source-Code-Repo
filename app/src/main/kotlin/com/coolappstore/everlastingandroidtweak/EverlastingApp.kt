package com.coolappstore.everlastingandroidtweak

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.preference.PreferenceManager
import com.aistra.hail.HailApp
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.shizuku.ShizukuManager
import tk.zwander.common.util.PrefManager

class EverlastingApp : HailApp() {

    override fun onCreate() {
        // Disable Bugsnag BEFORE LSW's App.onCreate() runs (it defaults to true)
        // This prevents crash from missing native lockscreenwidgets.so
        val sp = PreferenceManager.getDefaultSharedPreferences(this)
        sp.edit().putBoolean(PrefManager.KEY_ENABLE_BUGSNAG, false).commit()

        super.onCreate()   // → HailApp.onCreate() → LSW App.onCreate()

        // Everlasting-specific init
        ShizukuManager.initialize()
        createNotificationChannels()
        AppPreferences.init(this)

        // ── Eagerly seed Hail SharedPrefs with sane defaults on first launch ──
        // DataStore is async so we can't read it synchronously at startup.
        // We write defaults here so Hail ALWAYS opens with a valid theme entry
        // (not an unset -1 / missing key). Once the user visits SettingsScreen,
        // LaunchedEffect(themeMode, dynamicColor) overwrites these with the real values.
        if (!sp.contains(com.aistra.hail.app.HailData.EVERLASTING_THEME_MODE)) {
            sp.edit()
                .putInt(com.aistra.hail.app.HailData.EVERLASTING_THEME_MODE, 0)   // follow system
                .putBoolean(com.aistra.hail.app.HailData.EVERLASTING_DYNAMIC_COLOR, true)
                .putString(com.aistra.hail.app.HailData.EVERLASTING_CUSTOM_PRIMARY, "")
                .apply()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        ShizukuManager.destroy()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOREGROUND,
                "Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps gesture/haptic features running" }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "App alerts and notifications" }
        )
    }

    companion object {
        const val CHANNEL_FOREGROUND = "foreground_service"
        const val CHANNEL_ALERTS = "alerts"
    }
}
