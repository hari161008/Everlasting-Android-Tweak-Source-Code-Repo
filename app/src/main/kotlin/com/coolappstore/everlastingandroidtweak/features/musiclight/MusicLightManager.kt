package com.coolappstore.everlastingandroidtweak.features.musiclight

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.*
import android.media.audiofx.Visualizer
import android.os.*
import kotlinx.coroutines.*
import kotlin.math.*

class MusicLightManager(private val context: Context) {

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator

    private var job: Job? = null
    private var visualizer: Visualizer? = null
    private var torchCameraId: String? = null

    // ── Configurable properties (set from ForegroundService via preference observe) ──
    var lightEnabled       = true
    var vibrationEnabled   = true
    var sensitivity        = 0.35f   // 0.1 (very reactive) → 1.0 (only loud beats)

    /**
     * Blink speed multiplier.
     * 0.2 = slow (MIN_BEAT_GAP ~500ms)   1.0 = default (~120ms)   2.0 = fast (~60ms)
     */
    var blinkSpeed: Float = 1.0f
        set(value) { field = value.coerceIn(0.2f, 2.0f); updateBeatGap() }

    /**
     * How long (ms) the torch stays ON per beat. Range 20–300ms.
     */
    var blinkDurationMs: Int = 80
        set(value) { field = value.coerceIn(20, 300) }

    /**
     * Vibration amplitude when a beat is detected. 20–255.
     */
    var vibrationIntensity: Int = 160
        set(value) { field = value.coerceIn(20, 255) }

    private var minBeatGapMs = 120L    // derived from blinkSpeed
    private fun updateBeatGap() { minBeatGapMs = (120 / blinkSpeed).toLong().coerceIn(40L, 600L) }

    private var isRunning      = false
    private var maxTorchLevel  = 1
    private var smoothedLevel  = 0f
    private var lastBeatTime   = 0L

    init {
        try {
            torchCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                torchCameraId?.let { id ->
                    maxTorchLevel = cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                }
            }
        } catch (_: Exception) {}
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, samplingRate: Int) {
                        var sum = 0.0
                        for (b in waveform) {
                            val c = (b.toInt() and 0xFF) - 128
                            sum += c * c
                        }
                        val rms = sqrt(sum / waveform.size).toFloat()
                        processLevel((rms / 80f).coerceIn(0f, 1f))
                    }
                    override fun onFftDataCapture(v: Visualizer, fft: ByteArray, samplingRate: Int) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false)
                enabled = true
            }
        } catch (e: Exception) {
            // Fallback to microphone if Visualizer isn't available (e.g. no active audio session)
            job = CoroutineScope(Dispatchers.IO).launch { runMicFallback() }
        }
    }

    fun stop() {
        isRunning = false
        try { visualizer?.enabled = false; visualizer?.release(); visualizer = null } catch (_: Exception) {}
        job?.cancel(); job = null
        try { torchCameraId?.let { cameraManager.setTorchMode(it, false) } } catch (_: Exception) {}
    }

    private fun processLevel(normalized: Float) {
        if (!isRunning) return

        // Faster attack, slower decay for punchy response
        smoothedLevel = if (normalized > smoothedLevel)
            smoothedLevel * 0.2f + normalized * 0.8f
        else
            smoothedLevel * 0.9f + normalized * 0.1f

        val now = System.currentTimeMillis()
        val isBeat = smoothedLevel > sensitivity && (now - lastBeatTime) > minBeatGapMs
        if (isBeat) lastBeatTime = now
        applyReaction(smoothedLevel, isBeat)
    }

    private fun applyReaction(level: Float, isBeat: Boolean) {
        if (lightEnabled) {
            torchCameraId?.let { id ->
                try {
                    if (isBeat || level > 0.5f) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxTorchLevel > 1) {
                            val strength = if (isBeat) maxTorchLevel
                                           else (level * maxTorchLevel * 0.5f).toInt().coerceAtLeast(1)
                            cameraManager.turnOnTorchWithStrengthLevel(id, strength)
                        } else {
                            cameraManager.setTorchMode(id, true)
                        }
                        // Turn torch off after blinkDurationMs
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            try { cameraManager.setTorchMode(id, false) } catch (_: Exception) {}
                        }, blinkDurationMs.toLong())
                    }
                } catch (_: Exception) {}
            }
        }

        if (vibrationEnabled && isBeat && level > sensitivity) {
            val amplitude = vibrationIntensity.coerceIn(20, 255)
            val duration = blinkDurationMs.toLong().coerceIn(20L, 200L)
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        }
    }

    private suspend fun runMicFallback() {
        val sampleRate = 44100
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2
            )
        } catch (e: SecurityException) { return }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) return
        audioRecord.startRecording()
        val buffer = ShortArray(bufferSize / 2)
        try {
            while (isRunning && currentCoroutineContext().isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) sum += buffer[i].toDouble().pow(2)
                    val rms = sqrt(sum / read).toFloat()
                    withContext(Dispatchers.Main) { processLevel((rms / 5000f).coerceIn(0f, 1f)) }
                }
                delay((40 / blinkSpeed).toLong().coerceIn(20L, 80L))
            }
        } finally {
            audioRecord.stop(); audioRecord.release()
            withContext(Dispatchers.Main) {
                try { torchCameraId?.let { cameraManager.setTorchMode(it, false) } } catch (_: Exception) {}
            }
        }
    }
}
