package com.coolappstore.everlastingandroidtweak.services

import android.app.*
import android.content.*
import android.os.*
import androidx.core.app.NotificationCompat
import com.coolappstore.everlastingandroidtweak.EverlastingApp
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.features.camera.TwistCameraManager
import com.coolappstore.everlastingandroidtweak.features.charging.ChargingAnimationManager
import com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsPowerSavingManager
import com.coolappstore.everlastingandroidtweak.features.charging.ChargingSoundManager
import com.coolappstore.everlastingandroidtweak.features.chargelimit.ChargeLimitManager
import com.coolappstore.everlastingandroidtweak.features.notiflight.NotifLightManager
import com.coolappstore.everlastingandroidtweak.features.flipdnd.FlipToDndManager
import com.coolappstore.everlastingandroidtweak.features.keepon.KeepScreenOnTileService
import com.coolappstore.everlastingandroidtweak.features.musiclight.MusicLightManager
import com.coolappstore.everlastingandroidtweak.features.musicleveler.MusicLevelerOverlayService
import com.coolappstore.everlastingandroidtweak.features.securitymotion.SecurityMotionManager
import com.coolappstore.everlastingandroidtweak.features.torch.ShakeTorchManager
import com.coolappstore.everlastingandroidtweak.features.volumebooster.VolumeBoosterManager
import com.coolappstore.everlastingandroidtweak.features.navbar.NavBarOverlayService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class EverlastingForegroundService : Service() {

    private lateinit var shakeTorchManager:     ShakeTorchManager
    private lateinit var twistCameraManager:    TwistCameraManager
    private lateinit var musicLightManager:     MusicLightManager
    private lateinit var chargingSoundManager:  ChargingSoundManager
    private lateinit var chargingAnimManager:   ChargingAnimationManager
    private lateinit var flipToDndManager:      FlipToDndManager
    private lateinit var volumeBoosterManager:  VolumeBoosterManager
    private lateinit var securityMotionManager: SecurityMotionManager
    private lateinit var chargeLimitManager:    ChargeLimitManager
    private lateinit var mapsPowerSavingManager: MapsPowerSavingManager

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // BUG FIX: auto-update used delay() inside collect{} stacking on every emit.
    // Use a dedicated cancellable Job instead.
    private var autoUpdateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        shakeTorchManager     = ShakeTorchManager(this)
        twistCameraManager    = TwistCameraManager(this)
        musicLightManager     = MusicLightManager(this)
        chargingSoundManager  = ChargingSoundManager(this)
        chargingAnimManager   = ChargingAnimationManager(this)
        flipToDndManager      = FlipToDndManager(this)
        volumeBoosterManager  = VolumeBoosterManager(this)
        securityMotionManager = SecurityMotionManager(this)
        chargeLimitManager        = ChargeLimitManager(this)
        mapsPowerSavingManager    = MapsPowerSavingManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        observePreferences()
        return START_STICKY
    }

    private fun observePreferences() {
        scope.launch { AppPreferences.get(AppPreferences.SHAKE_TORCH_ENABLED, false).collect { if (it) shakeTorchManager.start() else shakeTorchManager.stop() } }
        scope.launch { AppPreferences.get(AppPreferences.SHAKE_SENSITIVITY, 12f).collect { shakeTorchManager.setSensitivity(it) } }
        scope.launch { AppPreferences.get(AppPreferences.SHAKE_PROXIMITY_ENABLED, false).collect { shakeTorchManager.setProximityEnabled(it) } }
        scope.launch { AppPreferences.get(AppPreferences.TWIST_CAMERA_ENABLED, false).collect { if (it) twistCameraManager.start() else twistCameraManager.stop() } }
        scope.launch { AppPreferences.get(AppPreferences.TWIST_SENSITIVITY, 3.5f).collect { twistCameraManager.setSensitivity(it) } }
        scope.launch { AppPreferences.get(AppPreferences.TWIST_PROXIMITY_ENABLED, false).collect { twistCameraManager.setProximityEnabled(it) } }
        scope.launch {
            AppPreferences.get(AppPreferences.MUSIC_LIGHT_ENABLED, false).collect { lightEnabled ->
                val vibEnabled = AppPreferences.get(AppPreferences.MUSIC_VIBRATE_ENABLED, false).first()
                if (lightEnabled || vibEnabled) {
                    musicLightManager.lightEnabled = lightEnabled
                    musicLightManager.vibrationEnabled = vibEnabled
                    musicLightManager.start()
                } else musicLightManager.stop()
            }
        }
        scope.launch {
            AppPreferences.get(AppPreferences.MUSIC_VIBRATE_ENABLED, false).collect { vibEnabled ->
                musicLightManager.vibrationEnabled = vibEnabled
                val lightEnabled = AppPreferences.get(AppPreferences.MUSIC_LIGHT_ENABLED, false).first()
                if (vibEnabled || lightEnabled) {
                    musicLightManager.lightEnabled = lightEnabled
                    musicLightManager.start()
                } else {
                    musicLightManager.stop()
                }
            }
        }
        scope.launch { AppPreferences.get(AppPreferences.MUSIC_LIGHT_SENSITIVITY, 0.35f).collect { musicLightManager.sensitivity = it } }
        // FIX: UI saves blinkSpeed to MUSIC_SPEED_SENSITIVITY; observe the correct key
        scope.launch { AppPreferences.get(AppPreferences.MUSIC_SPEED_SENSITIVITY, 1.0f).collect { musicLightManager.blinkSpeed = it } }
        scope.launch { AppPreferences.get(AppPreferences.MUSIC_BLINK_DURATION_MS, 80).collect { musicLightManager.blinkDurationMs = it } }
        scope.launch { AppPreferences.get(AppPreferences.MUSIC_VIBRATION_INTENSITY, 160).collect { musicLightManager.vibrationIntensity = it } }
        scope.launch { AppPreferences.get(AppPreferences.KEEP_SCREEN_ON_ENABLED, false).collect { if (it) KeepScreenOnTileService.acquireWakeLock(applicationContext) else KeepScreenOnTileService.releaseWakeLock() } }
        scope.launch { AppPreferences.get(AppPreferences.CHARGING_SOUND_ENABLED, false).collect { if (it) chargingSoundManager.start() else chargingSoundManager.stop() } }
        // New: Charging Animation — defaults false so no drain on first install
        scope.launch { AppPreferences.get(AppPreferences.CHARGING_ANIMATION_ENABLED, false).collect { if (it) chargingAnimManager.start() else chargingAnimManager.stop() } }
        // Maps Power Saving — launches MinModeActivity via Shizuku when screen turns off during navigation
        scope.launch { AppPreferences.get(AppPreferences.POWER_SAVING_MAPS_ENABLED, false).collect { if (it) mapsPowerSavingManager.start() else mapsPowerSavingManager.stop() } }
        scope.launch { AppPreferences.get(AppPreferences.FLIP_DND_ENABLED, false).collect { if (it) flipToDndManager.start() else flipToDndManager.stop() } }
        scope.launch {
            AppPreferences.get(AppPreferences.VOLUME_BOOST_ENABLED, false).collect { enabled ->
                val level = AppPreferences.get(AppPreferences.VOLUME_BOOST_LEVEL, 300).first()
                volumeBoosterManager.setBoost(enabled, level)
            }
        }
        scope.launch {
            AppPreferences.get(AppPreferences.VOLUME_BOOST_LEVEL, 300).collect { level ->
                val enabled = AppPreferences.get(AppPreferences.VOLUME_BOOST_ENABLED, false).first()
                if (enabled) volumeBoosterManager.setBoost(true, level)
            }
        }
        scope.launch {
            AppPreferences.get(AppPreferences.SECURITY_MOTION_ENABLED, false).collect { enabled ->
                if (enabled) {
                    val sens = AppPreferences.get(AppPreferences.SECURITY_MOTION_SENSITIVITY, 0.5f).first()
                    securityMotionManager.sensitivity = sens
                    securityMotionManager.onMotionDetected = {
                        val nm = getSystemService(NotificationManager::class.java)
                        nm?.notify(SECURITY_NOTIF_ID,
                            NotificationCompat.Builder(applicationContext, EverlastingApp.CHANNEL_ALERTS)
                                .setContentTitle("⚠️ Motion Detected!")
                                .setContentText("Device was moved or tampered with.")
                                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setAutoCancel(true).build())
                    }
                    securityMotionManager.start()
                } else securityMotionManager.stop()
            }
        }
        scope.launch { AppPreferences.get(AppPreferences.CHARGE_LIMIT_ENABLED, false).collect { if (it) chargeLimitManager.start() else chargeLimitManager.stop() } }
        scope.launch { AppPreferences.get(AppPreferences.MUSIC_LEVELER_ENABLED, false).collect { if (it) MusicLevelerOverlayService.start(applicationContext) else MusicLevelerOverlayService.stop(applicationContext) } }
        // NavBar overlay — start/stop based on pref so it survives app restarts
        scope.launch {
            AppPreferences.get(AppPreferences.NAVBAR_OVERLAY_ENABLED, false).collect { enabled ->
                if (enabled) NavBarOverlayService.start(applicationContext)
                else NavBarOverlayService.stop(applicationContext)
            }
        }
        // BUG FIX: use dedicated cancellable Job for auto-update delay
        scope.launch {
            AppPreferences.get(AppPreferences.AUTO_UPDATE_ENABLED, false).collect { enabled ->
                autoUpdateJob?.cancel()
                if (!enabled) return@collect
                val intervalHours = AppPreferences.get(AppPreferences.AUTO_UPDATE_INTERVAL_HOURS, 24).first()
                autoUpdateJob = scope.launch {
                    delay(intervalHours * 60 * 60 * 1000L)
                    try {
                        val result = com.coolappstore.everlastingandroidtweak.features.appupdater.AppUpdaterHelper.checkSelfUpdate(applicationContext)
                        if (result?.hasUpdate == true) {
                            val nm = getSystemService(NotificationManager::class.java)
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(result.downloadUrl)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            val pi = android.app.PendingIntent.getActivity(applicationContext, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
                            nm?.notify(UPDATE_NOTIF_ID, NotificationCompat.Builder(applicationContext, EverlastingApp.CHANNEL_ALERTS)
                                .setContentTitle("🔄 Update Available!").setContentText("Everlasting Tweak v${result.latestVersion} is ready")
                                .setSmallIcon(android.R.drawable.stat_sys_download).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).setContentIntent(pi).build())
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy(); scope.cancel()
        shakeTorchManager.stop(); twistCameraManager.stop(); musicLightManager.stop()
        chargingSoundManager.stop(); chargingAnimManager.stop(); flipToDndManager.stop()
        volumeBoosterManager.release(); securityMotionManager.stop(); chargeLimitManager.stop()
        KeepScreenOnTileService.releaseWakeLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, EverlastingApp.CHANNEL_FOREGROUND)
            .setContentTitle("Everlasting Tweak Active")
            .setContentText("Gesture & feature monitoring running")
            .setSmallIcon(android.R.drawable.ic_menu_manage).setOngoing(true).build()

    companion object {
        private const val NOTIF_ID          = 1001
        private const val SECURITY_NOTIF_ID = 1002
        private const val UPDATE_NOTIF_ID   = 1003

        fun start(context: Context) {
            val intent = Intent(context, EverlastingForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) = context.stopService(Intent(context, EverlastingForegroundService::class.java))
    }
}

// ── Boot Receiver ─────────────────────────────────────────────────────────────
// BUG FIX: was starting service unconditionally on every boot — wasted battery
// even when all features were disabled. Now only starts if at least one
// sensor/background feature is enabled.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            val anyEnabled = listOf(
                AppPreferences.get(AppPreferences.SHAKE_TORCH_ENABLED,        false).first(),
                AppPreferences.get(AppPreferences.TWIST_CAMERA_ENABLED,       false).first(),
                AppPreferences.get(AppPreferences.MUSIC_LIGHT_ENABLED,        false).first(),
                AppPreferences.get(AppPreferences.MUSIC_VIBRATE_ENABLED,      false).first(),
                AppPreferences.get(AppPreferences.CHARGING_SOUND_ENABLED,     false).first(),
                AppPreferences.get(AppPreferences.CHARGING_ANIMATION_ENABLED, false).first(),
                AppPreferences.get(AppPreferences.FLIP_DND_ENABLED,           false).first(),
                AppPreferences.get(AppPreferences.VOLUME_BOOST_ENABLED,       false).first(),
                AppPreferences.get(AppPreferences.SECURITY_MOTION_ENABLED,    false).first(),
                AppPreferences.get(AppPreferences.CHARGE_LIMIT_ENABLED,       false).first(),
                AppPreferences.get(AppPreferences.MUSIC_LEVELER_ENABLED,      false).first(),
                AppPreferences.get(AppPreferences.KEEP_SCREEN_ON_ENABLED,     false).first(),
                AppPreferences.get(AppPreferences.AUTO_UPDATE_ENABLED,        false).first(),
            ).any { it }

            if (anyEnabled) EverlastingForegroundService.start(context)
        }
    }
}
