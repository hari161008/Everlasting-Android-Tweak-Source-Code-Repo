package com.coolappstore.everlastingandroidtweak.features.charging

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * ChargingAnimationManager
 *
 * Shows a beautiful full-screen charging animation overlay via WindowManager
 * whenever the charger is connected.  The overlay auto-dismisses after the
 * configured duration, or when the charger is removed.
 *
 * Supported styles:  "lightning"  |  "ripple"  |  "pulse"  |  "fire"
 *
 * All features default to FALSE so no battery is wasted on first install.
 */
class ChargingAnimationManager(private val context: Context) {

    // BUG-SAFE: var scope recreated on every start() so re-enables work after stop()
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var registered    = false
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var dismissJob: Job? = null
    private var animator: ValueAnimator? = null

    // ── Broadcast receivers ──────────────────────────────────────────────────

    private val connectReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_CONNECTED) {
                scope.launch(Dispatchers.Main) { showAnimation() }
            }
        }
    }

    private val disconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_DISCONNECTED) {
                dismissAnimation()
            }
        }
    }

    // ── Public API ───────────────────────────────────────────────────────────

    fun start() {
        if (registered) return
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        context.registerReceiver(connectReceiver,    IntentFilter(Intent.ACTION_POWER_CONNECTED))
        context.registerReceiver(disconnectReceiver, IntentFilter(Intent.ACTION_POWER_DISCONNECTED))
        registered = true
    }

    fun stop() {
        if (!registered) return
        try { context.unregisterReceiver(connectReceiver) }    catch (_: Exception) {}
        try { context.unregisterReceiver(disconnectReceiver) } catch (_: Exception) {}
        registered = false
        dismissAnimation()
        scope.cancel()
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private suspend fun showAnimation() {
        val enabled  = AppPreferences.get(AppPreferences.CHARGING_ANIMATION_ENABLED, false).first()
        if (!enabled) return
        if (!Settings.canDrawOverlays(context)) return

        val style    = AppPreferences.get(AppPreferences.CHARGING_ANIMATION_STYLE,   "lightning").first()
        val colorHex = AppPreferences.get(AppPreferences.CHARGING_ANIMATION_COLOR,   "#FFD600").first()
        val duration = AppPreferences.get(AppPreferences.CHARGING_ANIMATION_DURATION, 4).first()
        val showPct  = AppPreferences.get(AppPreferences.CHARGING_ANIMATION_SHOW_PCT, true).first()

        val color = try { Color.parseColor(colorHex) } catch (_: Exception) { Color.parseColor("#FFD600") }

        // Dismiss any already-running animation first
        dismissAnimation()

        val view = buildAnimationView(style, color, showPct)
        overlayView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        try {
            windowManager?.addView(view, params)
        } catch (_: Exception) { return }

        // Auto-dismiss after `duration` seconds (0 = until unplugged)
        if (duration > 0) {
            dismissJob = scope.launch {
                delay(duration * 1000L)
                dismissAnimation()
            }
        }
    }

    private fun dismissAnimation() {
        dismissJob?.cancel()
        dismissJob = null
        animator?.cancel()
        animator = null
        overlayView?.let { v ->
            try { windowManager?.removeView(v) } catch (_: Exception) {}
        }
        overlayView = null
    }

    // ── View builders ─────────────────────────────────────────────────────────

    private fun buildAnimationView(style: String, color: Int, showPct: Boolean): View {
        val dm   = context.resources.displayMetrics
        val w    = dm.widthPixels
        val h    = dm.heightPixels

        return when (style) {
            "ripple"    -> buildRippleView(w, h, color, showPct)
            "pulse"     -> buildPulseView(w, h, color, showPct)
            "fire"      -> buildFireView(w, h, color, showPct)
            else        -> buildLightningView(w, h, color, showPct)   // default: lightning
        }
    }

    // ── Lightning / TurboPower style animation ───────────────────────────────
    private fun buildLightningView(w: Int, h: Int, color: Int, showPct: Boolean): View {
        val frame = FrameLayout(context)

        val canvas = object : View(context) {
            var animProgress = 0f

            val bgPaint = Paint().apply { this.color = Color.BLACK; style = Paint.Style.FILL }

            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textAlign  = Paint.Align.LEFT
                typeface   = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            }
            val sufPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textAlign  = Paint.Align.LEFT
                typeface   = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            }
            val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style     = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
            val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                style      = Paint.Style.FILL
            }
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE
                textAlign  = Paint.Align.LEFT
                typeface   = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                val vw = width.toFloat()
                val vh = height.toFloat()
                val dp = context.resources.displayMetrics.density
                canvas.drawRect(0f, 0f, vw, vh, bgPaint)

                val cx = vw / 2f
                val cy = vh * 0.42f   // glow centre ~42% down

                // ── Pulsing radial glow ───────────────────────────────────
                val glowR    = minOf(vw, vh) * (0.38f + animProgress * 0.05f)
                val baseAlpha = (140 + (animProgress * 50).toInt()).coerceIn(0, 220)
                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx, cy, glowR,
                        intArrayOf(
                            Color.argb(baseAlpha,           Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(baseAlpha * 6 / 10,  Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(baseAlpha * 2 / 10,  Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.30f, 0.65f, 1f),
                        Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(cx, cy, glowR * 1.25f, haloPaint)

                if (showPct) {
                    val bi  = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val lv  = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val sc  = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    val pct = if (lv >= 0) lv * 100 / sc else 14

                    // ── Percentage number — thin font, centred ────────────
                    val numFontSz = minOf(vw, vh) * 0.26f
                    val sufFontSz = numFontSz * 0.40f
                    numPaint.textSize = numFontSz
                    sufPaint.textSize = sufFontSz

                    val numStr     = "$pct"
                    val numW       = numPaint.measureText(numStr)
                    val sufW       = sufPaint.measureText("%")
                    val totalTextW = numW + sufW
                    val baseline   = cy + numFontSz * 0.35f
                    val numLeft    = cx - totalTextW / 2f

                    canvas.drawText(numStr, numLeft, baseline, numPaint)
                    canvas.drawText("%", numLeft + numW, baseline - numFontSz * 0.32f, sufPaint)

                    // ── Thin smile arc below number ───────────────────────
                    val arcR      = minOf(vw, vh) * 0.14f
                    val strokeW   = 3.5f * dp
                    // Arc centre: below number baseline
                    val arcCenterY = baseline + numFontSz * 0.12f + arcR
                    val arcRect   = RectF(cx - arcR, arcCenterY - arcR, cx + arcR, arcCenterY + arcR)
                    val sweep     = 150f * pct / 100f

                    // Ghost track
                    arcPaint.strokeWidth = strokeW
                    arcPaint.color       = Color.argb(35, Color.red(color), Color.green(color), Color.blue(color))
                    arcPaint.shader      = null
                    canvas.drawArc(arcRect, 195f, 150f, false, arcPaint)

                    // Glowing sweep
                    arcPaint.shader = SweepGradient(cx, arcCenterY,
                        intArrayOf(Color.TRANSPARENT,
                            Color.argb(200, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(220, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.argb(200, Color.red(color), Color.green(color), Color.blue(color)),
                            Color.TRANSPARENT),
                        floatArrayOf(0.25f, 0.40f, 0.50f, 0.60f, 0.75f))
                    canvas.drawArc(arcRect, 195f, sweep.coerceAtLeast(3f), false, arcPaint)
                    arcPaint.shader = null

                    // ── Bolt at nadir (bottom of arc circle) ──────────────
                    val bCX  = cx
                    val bCY  = arcCenterY + arcR
                    val bs   = numFontSz * 0.22f
                    val bPath = Path()
                    bPath.moveTo(bCX + bs * 0.35f, bCY - bs)
                    bPath.lineTo(bCX - bs * 0.10f, bCY - bs * 0.05f)
                    bPath.lineTo(bCX + bs * 0.12f, bCY - bs * 0.05f)
                    bPath.lineTo(bCX - bs * 0.35f, bCY + bs)
                    bPath.lineTo(bCX + bs * 0.10f, bCY + bs * 0.05f)
                    bPath.lineTo(bCX - bs * 0.12f, bCY + bs * 0.05f)
                    bPath.close()
                    canvas.drawPath(bPath, boltPaint)
                }

                // ── Bottom arc — giant circle, only top sliver visible ─────
                val bArcR  = vw * 0.58f
                val bArcCY = vh + bArcR * 0.28f
                arcPaint.strokeWidth = 3f * dp
                arcPaint.color       = Color.argb(180, Color.red(color), Color.green(color), Color.blue(color))
                arcPaint.shader      = SweepGradient(cx, bArcCY,
                    intArrayOf(Color.TRANSPARENT,
                        Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.argb(190, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)),
                        Color.TRANSPARENT),
                    floatArrayOf(0.30f, 0.43f, 0.50f, 0.57f, 0.70f))
                canvas.drawArc(RectF(cx - bArcR, bArcCY - bArcR, cx + bArcR, bArcCY + bArcR),
                    205f, 130f, false, arcPaint)
                arcPaint.shader = null

                // ── TurboPower branding ────────────────────────────────────
                val bFontSz = vw * 0.038f
                val bY      = vh * 0.955f
                val bCapY   = bY - bFontSz * 0.62f
                brandPaint.textSize = bFontSz
                val tpStr   = "TurboPower"
                val tpW     = brandPaint.measureText(tpStr)
                val bIconSz = bFontSz * 0.38f
                val bIconX  = cx - tpW / 2f - bIconSz * 1.6f

                val bIconPath = Path()
                bIconPath.moveTo(bIconX + bIconSz * 0.32f, bCapY - bIconSz)
                bIconPath.lineTo(bIconX - bIconSz * 0.10f, bCapY - bIconSz * 0.05f)
                bIconPath.lineTo(bIconX + bIconSz * 0.12f, bCapY - bIconSz * 0.05f)
                bIconPath.lineTo(bIconX - bIconSz * 0.32f, bCapY + bIconSz)
                bIconPath.lineTo(bIconX + bIconSz * 0.10f, bCapY + bIconSz * 0.05f)
                bIconPath.lineTo(bIconX - bIconSz * 0.12f, bCapY + bIconSz * 0.05f)
                bIconPath.close()
                boltPaint.color = Color.WHITE
                canvas.drawPath(bIconPath, boltPaint)
                canvas.drawText(tpStr, cx - tpW / 2f + bIconSz * 0.40f, bY, brandPaint)
            }
        }

        frame.addView(canvas, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT))

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration     = 1500
            repeatCount  = ValueAnimator.INFINITE
            repeatMode   = ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { a -> canvas.animProgress = a.animatedValue as Float; canvas.invalidate() }
            start()
        }

        frame.setOnClickListener { dismissAnimation() }
        return frame
    }
    // ── Ripple animation ─────────────────────────────────────────────────────
    private fun buildRippleView(w: Int, h: Int, color: Int, showPct: Boolean): View {
        val frame = FrameLayout(context)

        val canvas = object : View(context) {
            var progress = 0f
            val rings = arrayOf(0f, 0.33f, 0.66f)
            val bgPaint = Paint().apply { this.color = Color.argb(180, 0, 0, 0); style = Paint.Style.FILL }
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textAlign = Paint.Align.CENTER
                textSize = 72f; typeface = Typeface.DEFAULT_BOLD
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE; textAlign = Paint.Align.CENTER
                textSize = 38f
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                val cx = width / 2f; val cy = height / 2f
                val maxR = minOf(width, height) * 0.45f

                for (offset in rings) {
                    val p = ((progress + offset) % 1f)
                    val r = maxR * p
                    val a = ((1f - p) * 200).toInt()
                    ringPaint.color = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
                    canvas.drawCircle(cx, cy, r, ringPaint)
                }

                textPaint.textSize = maxR * 0.7f
                canvas.drawText("⚡", cx, cy + maxR * 0.25f, textPaint)

                if (showPct) {
                    val bi  = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val lv  = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val sc  = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    val pct = if (lv >= 0) lv * 100 / sc else 0
                    subPaint.textSize = 40f
                    canvas.drawText("$pct% • Charging", cx, cy + maxR * 0.8f, subPaint)
                }
            }
        }

        frame.addView(canvas, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode  = ValueAnimator.RESTART
            addUpdateListener { a -> canvas.progress = a.animatedValue as Float; canvas.invalidate() }
            start()
        }

        frame.setOnClickListener { dismissAnimation() }
        return frame
    }

    // ── Pulse glow animation ─────────────────────────────────────────────────
    private fun buildPulseView(w: Int, h: Int, color: Int, showPct: Boolean): View {
        val frame = FrameLayout(context)

        val canvas = object : View(context) {
            var scale = 0.7f
            val bgPaint = Paint().apply { this.color = Color.argb(160, 0, 0, 0); style = Paint.Style.FILL }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE; textAlign = Paint.Align.CENTER
                textSize = 42f
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                val cx = width / 2f; val cy = height / 2f
                val r  = minOf(width, height) * 0.35f * scale

                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = RadialGradient(cx, cy, r,
                        intArrayOf(color, Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
                        floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                }
                canvas.drawCircle(cx, cy, r, glowPaint)

                val emPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = Paint.Align.CENTER; textSize = r * 0.85f
                    this.color = Color.WHITE
                }
                canvas.drawText("⚡", cx, cy + r * 0.28f, emPaint)

                if (showPct) {
                    val bi  = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val lv  = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val sc  = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    val pct = if (lv >= 0) lv * 100 / sc else 0
                    textPaint.textSize = 44f
                    canvas.drawText("$pct%", cx, cy + r * 1.35f, textPaint)
                    textPaint.textSize = 30f
                    canvas.drawText("Charging", cx, cy + r * 1.35f + 46f, textPaint)
                }
            }
        }

        frame.addView(canvas, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        animator = ValueAnimator.ofFloat(0.7f, 1.0f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode  = ValueAnimator.REVERSE
            addUpdateListener { a -> canvas.scale = a.animatedValue as Float; canvas.invalidate() }
            start()
        }

        frame.setOnClickListener { dismissAnimation() }
        return frame
    }

    // ── Fire animation (warm gradient + rising particles) ────────────────────
    private fun buildFireView(w: Int, h: Int, color: Int, showPct: Boolean): View {
        val frame = FrameLayout(context)

        val canvas = object : View(context) {
            var offset = 0f
            val bgPaint = Paint().apply {
                shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
                    intArrayOf(Color.argb(220, 200, 60, 0), Color.argb(220, 255, 140, 0), Color.argb(200, 255, 200, 0)),
                    null, Shader.TileMode.CLAMP)
                style = Paint.Style.FILL
            }
            val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.argb(180, 255, 240, 60); style = Paint.Style.FILL
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE; textAlign = Paint.Align.CENTER
                textSize = 72f; typeface = Typeface.DEFAULT_BOLD
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 38f
            }

            val particles = Array(18) { i ->
                val rng = java.util.Random(i.toLong() * 31337)
                floatArrayOf(rng.nextFloat(), rng.nextFloat(), (rng.nextFloat() * 8f + 4f))
            }

            override fun onDraw(canvas: android.graphics.Canvas) {
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                val cx = width / 2f; val cy = height / 2f

                for (p in particles) {
                    val x  = p[0] * width
                    val y  = ((p[1] + offset) % 1f) * height
                    val r  = p[2]
                    val fadeY = y / height
                    particlePaint.alpha = ((1f - fadeY) * 200).toInt().coerceIn(0, 200)
                    canvas.drawCircle(x, y, r, particlePaint)
                }

                textPaint.textSize = minOf(width, height) * 0.3f
                canvas.drawText("🔥", cx, cy + textPaint.textSize * 0.35f, textPaint)

                if (showPct) {
                    val bi  = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val lv  = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val sc  = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                    val pct = if (lv >= 0) lv * 100 / sc else 0
                    subPaint.textSize = 52f
                    canvas.drawText("$pct%", cx, cy + minOf(width, height) * 0.38f, subPaint)
                    subPaint.textSize = 32f
                    canvas.drawText("Charging", cx, cy + minOf(width, height) * 0.38f + 54f, subPaint)
                }
            }
        }

        frame.addView(canvas, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            repeatMode  = ValueAnimator.RESTART
            addUpdateListener { a -> canvas.offset = a.animatedValue as Float; canvas.invalidate() }
            start()
        }

        frame.setOnClickListener { dismissAnimation() }
        return frame
    }
}
