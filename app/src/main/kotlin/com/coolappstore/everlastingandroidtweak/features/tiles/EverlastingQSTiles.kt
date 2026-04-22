package com.coolappstore.everlastingandroidtweak.features.tiles

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

// ─── UI BLUR TILE ─────────────────────────────────────────────────────────────
class UIBlurTileService : TileService() {
    override fun onStartListening() { qsTile?.apply { updateTile(); refresh() } }
    override fun onClick() {
        val enabled = qsTile?.state == Tile.STATE_ACTIVE
        try {
            Settings.Global.putInt(contentResolver, "blur_enabled", if (enabled) 0 else 1)
        } catch (_: Exception) {}
        qsTile?.apply { state = if (enabled) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }
    private fun Tile.refresh() {
        val on = try { Settings.Global.getInt(contentResolver, "blur_enabled", 1) == 1 } catch (_: Exception) { true }
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        label = "UI Blur"
        updateTile()
    }
}

// ─── MONO AUDIO TILE ─────────────────────────────────────────────────────────
class MonoAudioTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        val enabled = qsTile?.state == Tile.STATE_ACTIVE
        try { Settings.System.putInt(contentResolver, "master_mono", if (enabled) 0 else 1) } catch (_: Exception) {}
        qsTile?.apply { state = if (enabled) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }
    private fun Tile.refresh() {
        val on = try { Settings.System.getInt(contentResolver, "master_mono", 0) == 1 } catch (_: Exception) { false }
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Mono Audio"; updateTile()
    }
}

// ─── SOUND MODE TILE ─────────────────────────────────────────────────────────
class SoundModeTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL  -> am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> am.ringerMode = AudioManager.RINGER_MODE_SILENT
            else -> am.ringerMode = AudioManager.RINGER_MODE_NORMAL
        }
        qsTile?.refresh()
    }
    private fun Tile.refresh() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL  -> { label = "Sound On";   state = Tile.STATE_ACTIVE }
            AudioManager.RINGER_MODE_VIBRATE -> { label = "Vibrate";    state = Tile.STATE_ACTIVE }
            else                             -> { label = "Silent";     state = Tile.STATE_INACTIVE }
        }
        updateTile()
    }
}

// ─── STAY AWAKE TILE ─────────────────────────────────────────────────────────
class StayAwakeTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        val on = qsTile?.state == Tile.STATE_ACTIVE
        try { Settings.Global.putInt(contentResolver, "stay_on_while_plugged_in", if (on) 0 else 3) } catch (_: Exception) {}
        qsTile?.apply { state = if (on) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }
    private fun Tile.refresh() {
        val on = try { Settings.Global.getInt(contentResolver, "stay_on_while_plugged_in", 0) != 0 } catch (_: Exception) { false }
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Stay Awake"; updateTile()
    }
}

// ─── ADAPTIVE BRIGHTNESS TILE ─────────────────────────────────────────────────
class AdaptiveBrightnessTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        val on = qsTile?.state == Tile.STATE_ACTIVE
        try { Settings.System.putInt(contentResolver, "screen_brightness_mode", if (on) 0 else 1) } catch (_: Exception) {}
        qsTile?.apply { state = if (on) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }
    private fun Tile.refresh() {
        val on = try { Settings.System.getInt(contentResolver, "screen_brightness_mode", 0) == 1 } catch (_: Exception) { false }
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Auto Brightness"; updateTile()
    }
}

// ─── NFC TILE ────────────────────────────────────────────────────────────────
class NfcTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        startActivity(Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun Tile.refresh() {
        val nfcAdapter = try { android.nfc.NfcAdapter.getDefaultAdapter(applicationContext) } catch (_: Exception) { null }
        val on = nfcAdapter?.isEnabled == true
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "NFC"; updateTile()
    }
}

// ─── AOD TILE ────────────────────────────────────────────────────────────────
class AodTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        val on = qsTile?.state == Tile.STATE_ACTIVE
        try { Settings.Secure.putInt(contentResolver, "doze_always_on", if (on) 0 else 1) } catch (_: Exception) {}
        qsTile?.apply { state = if (on) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }
    private fun Tile.refresh() {
        val on = try { Settings.Secure.getInt(contentResolver, "doze_always_on", 0) == 1 } catch (_: Exception) { false }
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Always On Display"; updateTile()
    }
}

// ─── TAP TO WAKE TILE ────────────────────────────────────────────────────────
class TapToWakeTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        val on = qsTile?.state == Tile.STATE_ACTIVE
        try { Settings.Secure.putInt(contentResolver, "tap_to_wake", if (on) 0 else 1) } catch (_: Exception) {}
        qsTile?.apply { state = if (on) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }
    private fun Tile.refresh() {
        val on = try { Settings.Secure.getInt(contentResolver, "tap_to_wake", 0) == 1 } catch (_: Exception) { false }
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Tap to Wake"; updateTile()
    }
}

// ─── SENSITIVE LOCK SCREEN TILE ───────────────────────────────────────────────
class SensitiveLockTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        val on = qsTile?.state == Tile.STATE_ACTIVE
        try { Settings.Secure.putInt(contentResolver, "lock_screen_show_notifications", if (on) 0 else 1) } catch (_: Exception) {}
        qsTile?.apply { state = if (on) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }
    private fun Tile.refresh() {
        val on = try { Settings.Secure.getInt(contentResolver, "lock_screen_show_notifications", 1) == 1 } catch (_: Exception) { true }
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Lock Screen Notifs"; updateTile()
    }
}

// ─── PRIVATE DNS TILE ────────────────────────────────────────────────────────
class PrivateDnsTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun Tile.refresh() {
        val mode = try { Settings.Global.getString(contentResolver, "private_dns_mode") ?: "off" } catch (_: Exception) { "off" }
        state = if (mode != "off") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Private DNS"; updateTile()
    }
}

// ─── USB DEBUGGING TILE ──────────────────────────────────────────────────────
class UsbDebuggingTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun Tile.refresh() {
        val on = try { Settings.Global.getInt(contentResolver, Settings.Global.ADB_ENABLED, 0) == 1 } catch (_: Exception) { false }
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "USB Debugging"; updateTile()
    }
}

// ─── DEVELOPER OPTIONS TILE ───────────────────────────────────────────────────
class DevOptionsTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun Tile.refresh() {
        val on = try { Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 } catch (_: Exception) { false }
        state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "Developer Options"; updateTile()
    }
}

// ─── CAFFEINATE TILE ─────────────────────────────────────────────────────────
class CaffeinateTileService : TileService() {
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release(); wakeLock = null
            qsTile?.apply { state = Tile.STATE_INACTIVE; label = "Caffeinate"; updateTile() }
        } else {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP, "Everlasting:caffeinate")
            wakeLock?.acquire(3600000L)
            qsTile?.apply { state = Tile.STATE_ACTIVE; label = "Caffeinate ON"; updateTile() }
        }
    }
    override fun onStopListening() { wakeLock?.release(); wakeLock = null }
    private fun Tile.refresh() {
        state = if (wakeLock?.isHeld == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        label = "Caffeinate"; updateTile()
    }
}

// ─── FLIP TO DND TILE ────────────────────────────────────────────────────────
class FlipDndTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        val on = qsTile?.state == Tile.STATE_ACTIVE
        scope.launch { AppPreferences.set(AppPreferences.FLIP_DND_ENABLED, !on) }
        qsTile?.apply { state = if (on) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; label = "Flip to DND"; updateTile() }
    }
    private fun Tile.refresh() { label = "Flip to DND"; state = Tile.STATE_INACTIVE; updateTile() }
    override fun onDestroy() { scope.cancel() }
}

// ─── VOLUME BOOST TILE ───────────────────────────────────────────────────────
class VolumeBoostTileService : TileService() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        val on = qsTile?.state == Tile.STATE_ACTIVE
        scope.launch { AppPreferences.set(AppPreferences.VOLUME_BOOST_ENABLED, !on) }
        qsTile?.apply { state = if (on) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; label = "Volume Boost"; updateTile() }
    }
    private fun Tile.refresh() { label = "Volume Boost"; state = Tile.STATE_INACTIVE; updateTile() }
    override fun onDestroy() { scope.cancel() }
}

// ─── SCALE ANIMATIONS TILE ───────────────────────────────────────────────────
class ScaleAnimationsTileService : TileService() {
    override fun onStartListening() { qsTile?.refresh() }
    override fun onClick() {
        val helper = com.coolappstore.everlastingandroidtweak.features.scale.ScaleAdjustmentsHelper
        val ctx = applicationContext
        val currentMode = helper.getMode(ctx)
        val newMode = if (currentMode == "glove") "default" else "glove"
        val currentValues = helper.readCurrentSystemValues(ctx)
        helper.saveProfile(ctx, currentMode, currentValues)
        val newProfile = helper.loadProfile(ctx, newMode)
        helper.setMode(ctx, newMode)
        helper.applyProfile(ctx, newProfile)
        qsTile?.refresh()
    }
    private fun Tile.refresh() {
        val mode = com.coolappstore.everlastingandroidtweak.features.scale.ScaleAdjustmentsHelper.getMode(this@ScaleAnimationsTileService)
        state = if (mode == "glove") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        label = if (mode == "glove") "Scale: Glove" else "Scale: Default"
        updateTile()
    }
}
