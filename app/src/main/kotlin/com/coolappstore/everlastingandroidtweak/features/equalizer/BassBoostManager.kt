package com.coolappstore.everlastingandroidtweak.features.equalizer

import android.media.audiofx.BassBoost
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BassBoostManager {

    private const val TAG = "BassBoostManager"
    private var bassBoost: BassBoost? = null
    private val SESSION_IDS_TO_TRY = listOf(0, 1, 2)

    var strengthSupported: Boolean = false
        private set

    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        release()
        for (sessionId in SESSION_IDS_TO_TRY) {
            try {
                val bb = BassBoost(900, sessionId)
                strengthSupported = bb.strengthSupported
                bassBoost = bb
                Log.d(TAG, "BassBoost ready: session=$sessionId strengthSupported=$strengthSupported")
                return@withContext true
            } catch (e: Exception) {
                Log.w(TAG, "BassBoost session $sessionId failed: ${e.message}")
            }
        }
        false
    }

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        try { bassBoost?.enabled = enabled }
        catch (e: Exception) { Log.e(TAG, "setEnabled failed: ${e.message}") }
    }

    fun getEnabled(): Boolean = try { bassBoost?.enabled ?: false } catch (_: Exception) { false }

    /** strength: 0..1000 */
    suspend fun setStrength(strength: Short) = withContext(Dispatchers.IO) {
        try {
            val clamped = strength.toInt().coerceIn(0, 1000).toShort()
            bassBoost?.setStrength(clamped)
        } catch (e: Exception) {
            Log.e(TAG, "setStrength failed: ${e.message}")
        }
    }

    fun release() {
        try { bassBoost?.enabled = false; bassBoost?.release() } catch (_: Exception) {}
        bassBoost = null
    }
}
