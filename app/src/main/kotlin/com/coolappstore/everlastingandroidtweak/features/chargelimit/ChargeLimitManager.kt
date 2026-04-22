package com.coolappstore.everlastingandroidtweak.features.chargelimit

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import com.coolappstore.everlastingandroidtweak.EverlastingApp
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class ChargeLimitManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var registered = false
    private var alarmFired = false
    private var mediaPlayer: MediaPlayer? = null
    private var repeatJob: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val lev = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scl = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val pct = lev * 100 / scl
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

                // Reset when unplugged
                if (plugged == 0 && alarmFired) {
                    alarmFired = false
                    stopAlarm()
                    return
                }

                scope.launch {
                    val limit = AppPreferences.get(AppPreferences.CHARGE_LIMIT_PERCENT, 80).first()
                    if (pct >= limit && !alarmFired && plugged != 0) {
                        alarmFired = true
                        val ringtoneUri = AppPreferences.get(AppPreferences.CHARGE_RINGTONE_URI, "").first()
                        val repeat = AppPreferences.get(AppPreferences.CHARGE_REPEAT_ENABLED, true).first()
                        fireAlarm(pct, limit, ringtoneUri, repeat)
                    }
                }
            }
        }
    }

    private fun fireAlarm(current: Int, limit: Int, ringtoneUri: String, repeat: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(3001,
            NotificationCompat.Builder(context, EverlastingApp.CHANNEL_ALERTS)
                .setContentTitle("⚡ Charge Limit Reached — $current%!")
                .setContentText("Battery at $current%. Unplug now to protect battery health (limit: $limit%)")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
        // Vibrate
        try {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vm.defaultVibrator.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0,400,200,400,200,400), -1))
        } catch (_: Exception) {}

        // Play ringtone
        playAlarmSound(ringtoneUri, repeat)
    }

    private fun playAlarmSound(customUri: String, repeat: Boolean) {
        stopAlarm()
        val uri: Uri = if (customUri.isNotBlank()) {
            Uri.parse(customUri)
        } else {
            // Use default alarm ringtone
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return
        }

        fun startPlayer() {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val vol = am.getStreamVolume(AudioManager.STREAM_ALARM).toFloat() /
                          am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                    setDataSource(context, uri)
                    setVolume(vol, vol)
                    prepare()
                    setOnCompletionListener {
                        if (repeat && alarmFired) {
                            repeatJob = scope.launch {
                                delay(3000L) // 3s gap between repeats
                                if (alarmFired) startPlayer()
                            }
                        } else {
                            release()
                            mediaPlayer = null
                        }
                    }
                    start()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        startPlayer()
    }

    private fun stopAlarm() {
        repeatJob?.cancel(); repeatJob = null
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun start() {
        if (registered) return
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        registered = true
    }

    fun stop() {
        if (!registered) return
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        registered = false
        stopAlarm()
        scope.cancel()
    }
}
