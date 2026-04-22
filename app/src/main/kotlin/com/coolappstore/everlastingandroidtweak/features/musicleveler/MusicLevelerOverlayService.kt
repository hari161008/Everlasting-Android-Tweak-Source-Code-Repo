package com.coolappstore.everlastingandroidtweak.features.musicleveler

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.os.*
import android.view.*
import androidx.core.content.ContextCompat
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.sqrt

class MusicLevelerOverlayService : Service() {
    private var wm: WindowManager? = null
    private var levelerView: LevelerView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var viewAdded = false
    private var autoHide = true

    // Single job guards against concurrent startLeveler() calls
    private var levelerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_STOP = "action_stop_leveler"
        fun start(ctx: android.content.Context) =
            ctx.startService(Intent(ctx, MusicLevelerOverlayService::class.java))
        fun stop(ctx: android.content.Context) {
            ctx.startService(Intent(ctx, MusicLevelerOverlayService::class.java).apply { action = ACTION_STOP })
        }
    }

    private val silenceRunnable = Runnable { handler.post { hideOverlay() } }

    override fun onCreate() { super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { cleanup(); stopSelf(); return START_NOT_STICKY }
        levelerJob?.cancel()
        levelerJob = scope.launch { startLeveler() }
        return START_STICKY
    }

    private suspend fun startLeveler() {
        // Read prefs on IO thread
        val colorHex = AppPreferences.get(AppPreferences.MUSIC_LEVELER_COLOR, "#8BCAFF").first()
        val position = AppPreferences.get(AppPreferences.MUSIC_LEVELER_POSITION, "Bottom").first()
        val height   = AppPreferences.get(AppPreferences.MUSIC_LEVELER_HEIGHT, 56f).first()
        val opacity  = AppPreferences.get(AppPreferences.MUSIC_LEVELER_OPACITY, 1f).first()
        autoHide     = AppPreferences.get(AppPreferences.MUSIC_LEVELER_AUTO_HIDE, true).first()
        val color    = try { Color.parseColor(colorHex) } catch (_: Exception) { Color.parseColor("#8BCAFF") }

        withContext(Dispatchers.Main) {
            cleanup()

            val view = LevelerView(this@MusicLevelerOverlayService, color, opacity)
            levelerView = view

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val gravity = if (position == "Top") Gravity.TOP or Gravity.CENTER_HORIZONTAL
                          else Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

            overlayParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                (height * resources.displayMetrics.density).toInt().coerceAtLeast(32),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply { this.gravity = gravity }

            if (!autoHide) showOverlay()
        }

        val hasAudio = ContextCompat.checkSelfPermission(
            this@MusicLevelerOverlayService, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudio) {
            // No audio permission — show a static demo on main thread
            withContext(Dispatchers.Main) {
                levelerView?.showStaticDemo()
                if (!autoHide) showOverlay()
            }
            return
        }

        // Try Visualizer (system audio output) first
        var visualizerStarted = false
        withContext(Dispatchers.Main) {
            try {
                val vis = Visualizer(0).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer, wf: ByteArray, sr: Int) {
                            val view = levelerView ?: return
                            val chunk = (wf.size / 32).coerceAtLeast(1)
                            val bars = FloatArray(32) { i ->
                                var sum = 0.0
                                for (j in 0 until chunk) {
                                    val b = (wf[(i * chunk + j).coerceIn(0, wf.size - 1)].toInt() and 0xFF) - 128
                                    sum += b * b
                                }
                                (sqrt(sum / chunk).toFloat() / 72f).coerceIn(0f, 1f)
                            }
                            view.updateBars(bars)
                            if (autoHide) {
                                handler.removeCallbacks(silenceRunnable)
                                if (bars.any { it > 0.03f }) {
                                    if (!viewAdded) showOverlay()
                                } else {
                                    handler.postDelayed(silenceRunnable, 1500L)
                                }
                            }
                        }
                        override fun onFftDataCapture(v: Visualizer, fft: ByteArray, sr: Int) {}
                    }, Visualizer.getMaxCaptureRate() / 2, true, false)
                    enabled = true
                }
                // Store a reference so cleanup() can release it
                activeVisualizer = vis
                visualizerStarted = true
            } catch (_: Exception) {}
        }

        if (!visualizerStarted) {
            // Fallback: mic-based AudioRecord loop
            runMicFallback()
        }
    }

    @Volatile private var activeVisualizer: Visualizer? = null

    private suspend fun runMicFallback() {
        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return

        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
            )
        } catch (_: SecurityException) { return }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) { audioRecord.release(); return }
        audioRecord.startRecording()

        if (!autoHide) withContext(Dispatchers.Main) { showOverlay() }

        val buffer = ShortArray(minBuf / 2)
        try {
            while (currentCoroutineContext().isActive) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) {
                    // Build 32 bars from the mic buffer
                    val chunkSize = (read / 32).coerceAtLeast(1)
                    val bars = FloatArray(32) { i ->
                        var sum = 0.0
                        for (j in 0 until chunkSize) {
                            val v = buffer[(i * chunkSize + j).coerceIn(0, read - 1)].toDouble()
                            sum += v * v
                        }
                        (sqrt(sum / chunkSize).toFloat() / 5000f).coerceIn(0f, 1f)
                    }
                    withContext(Dispatchers.Main) {
                        levelerView?.updateBars(bars)
                        if (autoHide) {
                            handler.removeCallbacks(silenceRunnable)
                            if (bars.any { it > 0.02f }) {
                                if (!viewAdded) showOverlay()
                            } else {
                                handler.postDelayed(silenceRunnable, 1500L)
                            }
                        }
                    }
                }
                delay(30L)
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }
    }

    private fun showOverlay() {
        if (viewAdded || levelerView == null || overlayParams == null) return
        try { wm?.addView(levelerView, overlayParams); viewAdded = true } catch (_: Exception) {}
    }

    private fun hideOverlay() {
        if (!viewAdded || levelerView == null) return
        try { wm?.removeView(levelerView); viewAdded = false } catch (_: Exception) {}
    }

    private fun cleanup() {
        handler.removeCallbacks(silenceRunnable)
        handler.post { hideOverlay() }
        try { activeVisualizer?.enabled = false; activeVisualizer?.release() } catch (_: Exception) {}
        activeVisualizer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        levelerJob?.cancel()
        scope.cancel()
        cleanup()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class LevelerView(
    ctx: android.content.Context,
    barColor: Int,
    opacity: Float = 1f
) : android.view.View(ctx) {
    private var bars = FloatArray(32) { 0f }
    private val alpha255 = (opacity * 255f).toInt().coerceIn(0, 255)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = barColor; alpha = alpha255; style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = barColor; alpha = (alpha255 * 0.4f).toInt(); style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    }

    fun updateBars(newBars: FloatArray) {
        for (i in bars.indices) {
            bars[i] = if (newBars[i] > bars[i]) bars[i] * 0.15f + newBars[i] * 0.85f
                      else bars[i] * 0.86f + newBars[i] * 0.14f
        }
        postInvalidateOnAnimation()
    }

    fun showStaticDemo() {
        val wave = FloatArray(32) { i -> (0.1f + 0.4f * kotlin.math.sin(i * 0.4f).toFloat()).coerceIn(0.05f, 0.9f) }
        updateBars(wave)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val barW = w / bars.size
        val cornerR = (barW * 0.42f).coerceAtLeast(2f)
        bars.forEachIndexed { i, level ->
            val barH = (level * h * 0.9f).coerceAtLeast(3f)
            val left  = i * barW + barW * 0.1f
            val right = left + barW * 0.8f
            val top   = h - barH
            val rect  = RectF(left, top, right, h)
            canvas.drawRoundRect(rect, cornerR, cornerR, glowPaint)
            canvas.drawRoundRect(rect, cornerR, cornerR, paint)
        }
    }
}
