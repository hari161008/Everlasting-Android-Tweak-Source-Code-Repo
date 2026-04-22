package com.coolappstore.everlastingandroidtweak.features.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.*
import android.net.Uri
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ChargingSoundManager(private val context: Context) {

    // BUG FIX: scope was created once and permanently cancelled after stop().
    // Calling start() again after stop() would silently fail because a cancelled
    // CoroutineScope refuses to launch new coroutines.
    // Fix: scope is a `var` recreated on each start() call.
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_CONNECTED) scope.launch { playChargingSound() }
        }
    }

    private suspend fun playChargingSound() {
        val enabled = AppPreferences.get(AppPreferences.CHARGING_SOUND_ENABLED, false).first()
        val uri     = AppPreferences.get(AppPreferences.CHARGING_SOUND_URI, "").first()
        if (!enabled || uri.isBlank()) return
        try {
            val parsedUri = Uri.parse(uri)
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val vol = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION).toFloat() /
                      am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                if (parsedUri.scheme == "content") setDataSource(context, parsedUri)
                else setDataSource(uri)
                setVolume(vol, vol)
                prepare()
                setOnCompletionListener { release() }
                start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun start() {
        if (registered) return
        // Always create a fresh scope so re-enabling after disable works correctly
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_POWER_CONNECTED))
        registered = true
    }

    fun stop() {
        if (!registered) return
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        registered = false
        scope.cancel()
    }
}
