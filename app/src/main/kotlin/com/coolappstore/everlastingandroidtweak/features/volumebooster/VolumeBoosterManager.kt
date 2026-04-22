package com.coolappstore.everlastingandroidtweak.features.volumebooster

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer

class VolumeBoosterManager(private val context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var enhancer: LoudnessEnhancer? = null
    private var originalVolume: Int = -1

    fun setBoost(enabled: Boolean, gainMb: Int) {
        if (!enabled) {
            enhancer?.enabled = false
            enhancer?.release()
            enhancer = null
            // Restore original volume if saved
            if (originalVolume >= 0) {
                try { am.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0) } catch (_: Exception) {}
                originalVolume = -1
            }
            return
        }
        try {
            // Step 1: Push system volume to max for maximum headroom
            if (originalVolume < 0) {
                originalVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)

            // Step 2: Apply LoudnessEnhancer on top (session 0 = global output mix)
            // gainMb is 0..15000 millibels (0..+1500 dB equivalent — clamped by driver)
            // LoudnessEnhancer accepts values in millibels; real driver caps vary ~300–1500 mB
            if (enhancer == null) enhancer = LoudnessEnhancer(0)
            enhancer?.setTargetGain(gainMb.coerceIn(0, 15000))
            enhancer?.enabled = true
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun release() {
        enhancer?.release()
        enhancer = null
        if (originalVolume >= 0) {
            try { am.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0) } catch (_: Exception) {}
            originalVolume = -1
        }
    }
}
