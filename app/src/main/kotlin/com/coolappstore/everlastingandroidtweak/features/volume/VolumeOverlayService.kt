package com.coolappstore.everlastingandroidtweak.features.volume

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.*
import android.view.*
import android.widget.*
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class VolumeOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var hideJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch { showVolumeOverlay() }
        return START_NOT_STICKY
    }

    private suspend fun showVolumeOverlay() {
        removeOverlay()

        val style        = AppPreferences.get(AppPreferences.VOLUME_STYLE, "Default").first()
        if (style == "Default") { stopSelf(); return }  // System volume UI handles it

        val colorHex     = AppPreferences.get(AppPreferences.VOLUME_COLOR, "#1C1C1E").first()
        val cornerRadius = AppPreferences.get(AppPreferences.VOLUME_CORNER_RADIUS, 24f).first()
        val opacity      = AppPreferences.get(AppPreferences.VOLUME_OPACITY, 0.92f).first()

        val bgColor = parseBgColor(colorHex, opacity)
        val fraction = currentVolumeFraction()

        val view = buildVolumeView(style, bgColor, cornerRadius, fraction)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val (w, h, gravity) = dimensionsForStyle(style)

        val params = WindowManager.LayoutParams(
            if (w < 0) w else dpToPx(w),
            if (h < 0) h else dpToPx(h),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            x = if (style == "Expanded") 0 else dpToPx(12)
            y = 0
        }

        overlayView = view
        try {
            windowManager?.addView(view, params)
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(180).start()
        } catch (e: Exception) { e.printStackTrace(); stopSelf(); return }

        scheduleHide()
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(2500)
            overlayView?.animate()
                ?.alpha(0f)
                ?.setDuration(300)
                ?.withEndAction { removeOverlay(); stopSelf() }
                ?.start()
        }
    }

    // ─── Style builders ───────────────────────────────────────────────────────

    private fun buildVolumeView(style: String, bgColor: Int, cornerRadius: Float, fraction: Float): View =
        when (style) {
            "Circular"  -> buildCircularView(bgColor, fraction)
            "Expanded"  -> buildExpandedView(bgColor, cornerRadius, fraction)
            "Compact"   -> buildVerticalBar(bgColor, cornerRadius, fraction, compact = true)
            "Minimal"   -> buildMinimalPill(bgColor, fraction)
            else        -> buildVerticalBar(bgColor, cornerRadius, fraction, compact = false) // "Pill"
        }

    /**
     * Proper vertical fill bar using a custom drawn view — no ProgressBar issues.
     */
    private fun buildVerticalBar(bgColor: Int, cornerRadius: Float, fraction: Float, compact: Boolean): View {
        val cr = if (compact) 16f else cornerRadius
        return object : View(this) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = if (compact) 28f else 36f; textAlign = Paint.Align.CENTER
            }
            private val bgRect = RectF()
            private val fillRect = RectF()

            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()

                // Background
                bgRect.set(0f, 0f, w, h)
                canvas.drawRoundRect(bgRect, cr, cr, bgPaint)

                // Fill from bottom
                val fillTop = h * (1f - fraction)
                fillRect.set(0f, fillTop, w, h)
                canvas.save()
                canvas.clipRect(fillRect)
                canvas.drawRoundRect(bgRect, cr, cr, fillPaint)
                canvas.restore()

                // Icon
                val icon = if (fraction <= 0f) "🔇" else if (fraction < 0.5f) "🔉" else "🔊"
                canvas.drawText(icon, w / 2, h - if (compact) 12f else 16f, iconPaint)
            }
        }
    }

    private fun buildMinimalPill(bgColor: Int, fraction: Float): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 100f; setColor(bgColor)
            }
        }
        val label = TextView(this).apply {
            text = "${(fraction * 100).toInt()}%"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        container.addView(label)
        return container
    }

    private fun buildCircularView(bgColor: Int, fraction: Float): View {
        return object : View(this) {
            private val bgPaint   = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
            private val arcPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 12f; strokeCap = Paint.Cap.ROUND
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 28f; textAlign = Paint.Align.CENTER
            }
            private val oval = RectF()

            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f; val cy = height / 2f; val r = minOf(cx, cy) - 16f
                canvas.drawCircle(cx, cy, r, bgPaint)
                oval.set(cx - r + 10, cy - r + 10, cx + r - 10, cy + r - 10)
                canvas.drawArc(oval, -90f, fraction * 360f, false, arcPaint)
                val icon = if (fraction <= 0f) "🔇" else "🔊"
                canvas.drawText(icon, cx, cy - 6f, textPaint)
                textPaint.textSize = 20f
                canvas.drawText("${(fraction * 100).toInt()}%", cx, cy + 22f, textPaint)
            }
        }
    }

    private fun buildExpandedView(bgColor: Int, cornerRadius: Float, fraction: Float): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(20), 0, dpToPx(20), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                this.cornerRadius = cornerRadius
                setColor(bgColor)
            }
        }
        val icon = TextView(this).apply {
            text = if (fraction <= 0f) "🔇" else if (fraction < 0.5f) "🔉" else "🔊"
            textSize = 20f
            setPadding(0, 0, dpToPx(12), 0)
        }
        // Custom progress bar replacing broken seekbar
        val progressBar = object : View(this) {
            private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(80, 255, 255, 255) }
            private val fillPaint  = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            private val trackRect  = RectF()
            private val fillRect   = RectF()
            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                val cr = h / 2
                trackRect.set(0f, 0f, w, h)
                canvas.drawRoundRect(trackRect, cr, cr, trackPaint)
                fillRect.set(0f, 0f, w * fraction, h)
                canvas.drawRoundRect(fillRect, cr, cr, fillPaint)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(6), 1f)
        }
        row.addView(icon); row.addView(progressBar)
        return row
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun dimensionsForStyle(style: String): Triple<Int, Int, Int> = when (style) {
        "Pill"      -> Triple(52, 180, Gravity.END or Gravity.CENTER_VERTICAL)
        "Minimal"   -> Triple(44, 80,  Gravity.END or Gravity.CENTER_VERTICAL)
        "Circular"  -> Triple(80, 80,  Gravity.END or Gravity.CENTER_VERTICAL)
        "Compact"   -> Triple(40, 160, Gravity.END or Gravity.CENTER_VERTICAL)
        "Expanded"  -> Triple(WindowManager.LayoutParams.MATCH_PARENT, 56, Gravity.BOTTOM)
        else        -> Triple(52, 180, Gravity.END or Gravity.CENTER_VERTICAL)
    }

    private fun currentVolumeFraction(): Float {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        return if (max > 0) (cur / max).coerceIn(0f, 1f) else 0f
    }

    private fun parseBgColor(hex: String, opacity: Float): Int = try {
        val base = Color.parseColor(hex)
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    } catch (_: Exception) { Color.argb(235, 28, 28, 30) }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    private fun removeOverlay() {
        overlayView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        removeOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun show(context: Context) {
            // Regular service — NOT foreground, because it's short-lived (2.5s)
            context.startService(Intent(context, VolumeOverlayService::class.java))
        }
    }
}
