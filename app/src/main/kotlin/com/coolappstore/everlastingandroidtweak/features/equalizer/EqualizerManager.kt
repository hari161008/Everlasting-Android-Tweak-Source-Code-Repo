package com.coolappstore.everlastingandroidtweak.features.equalizer

import android.media.audiofx.Equalizer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EqualizerManager {

    private const val TAG = "EqualizerManager"
    private var equalizer: Equalizer? = null
    private var isInitialized = false
    private val SESSION_IDS_TO_TRY = listOf(0, 1, 2)

    // Actual band count reported by the device EQ (often 5, not 10)
    var deviceBandCount: Int = 5
        private set

    // Actual millibel range supported by this device
    var deviceMinMb: Short = -1500
        private set
    var deviceMaxMb: Short = 1500
        private set

    /** Must be called from a coroutine (uses IO dispatcher internally). */
    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        release()
        for (sessionId in SESSION_IDS_TO_TRY) {
            try {
                val eq = Equalizer(1000, sessionId)
                val bands = eq.numberOfBands.toInt()
                if (bands <= 0) { eq.release(); continue }
                val range = eq.bandLevelRange
                deviceBandCount = bands
                deviceMinMb = range[0]
                deviceMaxMb = range[1]
                equalizer = eq
                isInitialized = true
                Log.d(TAG, "EQ ready: session=$sessionId bands=$bands range=[${range[0]},${range[1]}]mb")
                return@withContext true
            } catch (e: Exception) {
                Log.w(TAG, "Session $sessionId failed: ${e.message}")
            }
        }
        isInitialized = false
        false
    }

    /** Call from IO dispatcher only. */
    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        try { equalizer?.enabled = enabled }
        catch (e: Exception) { Log.e(TAG, "setEnabled failed: ${e.message}") }
    }

    fun getEnabled(): Boolean = try { equalizer?.enabled ?: false } catch (_: Exception) { false }

    /**
     * Apply all band levels. Safe to call from any coroutine — dispatches to IO internally.
     * levels is in dB; we convert to millibels and clamp to device range.
     */
    suspend fun applyAllBands(levels: List<Float>) = withContext(Dispatchers.IO) {
        val eq = equalizer ?: return@withContext
        try {
            val range = eq.bandLevelRange
            val maxBands = eq.numberOfBands.toInt()
            levels.forEachIndexed { i, dB ->
                if (i < maxBands) {
                    val mb = (dB * 100f).toInt().toShort().coerceIn(range[0], range[1])
                    eq.setBandLevel(i.toShort(), mb)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyAllBands failed: ${e.message}")
        }
    }

    suspend fun applyFromString(raw: String) {
        val parsed = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
        if (parsed.isNotEmpty()) applyAllBands(parsed)
    }

    fun release() {
        try { equalizer?.enabled = false; equalizer?.release() } catch (_: Exception) {}
        equalizer = null
        isInitialized = false
    }
}
