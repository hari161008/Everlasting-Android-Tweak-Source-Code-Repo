package com.coolappstore.everlastingandroidtweak.features.navbar

import android.app.Service
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class NavBarOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pendingShowJob: Job? = null

    companion object {
        const val ACTION_STOP   = "action_stop_navbar"
        const val ACTION_UPDATE = "action_update_navbar"

        fun start(context: android.content.Context) {
            if (!Settings.canDrawOverlays(context)) return
            context.startService(Intent(context, NavBarOverlayService::class.java))
        }

        fun stop(context: android.content.Context) {
            context.startService(Intent(context, NavBarOverlayService::class.java).apply {
                action = ACTION_STOP
            })
        }

        fun update(context: android.content.Context) {
            if (!Settings.canDrawOverlays(context)) return
            context.startService(Intent(context, NavBarOverlayService::class.java).apply {
                action = ACTION_UPDATE
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                pendingShowJob?.cancel()
                removeOverlaySync()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                pendingShowJob?.cancel()
                pendingShowJob = scope.launch {
                    delay(300)
                    showOverlay()
                }
            }
            else -> {
                pendingShowJob?.cancel()
                pendingShowJob = scope.launch { showOverlay() }
            }
        }
        return START_STICKY
    }

    private fun removeOverlaySync() {
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    private suspend fun showOverlay() {
        removeOverlaySync()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }

        val style        = AppPreferences.get(AppPreferences.NAVBAR_STYLE, "Minimal Pill").first()
        val pillColorHex = AppPreferences.get(AppPreferences.NAVBAR_PILL_COLOR, "#FFFFFF").first()
        val pillOpacity  = AppPreferences.get(AppPreferences.NAVBAR_PILL_OPACITY, 0.8f).first()
        val thicknessDp  = AppPreferences.get(AppPreferences.NAVBAR_HEIGHT, 48f).first()
        val xPos         = AppPreferences.get(AppPreferences.NAVBAR_X_POSITION, 0f).first()
        val yPos         = AppPreferences.get(AppPreferences.NAVBAR_Y_POSITION, 0f).first()

        val pillColor   = parsePillColor(pillColorHex, pillOpacity)
        val thicknessPx = dpToPx(thicknessDp.coerceIn(8f, 200f))
        val view        = buildNavView(style, pillColor, thicknessPx)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            thicknessPx,
            type,
            baseFlags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = if (style == "Colored" || style == "Gradient" || style == "Neon Glow") 0 else xPos.toInt()
            y = yPos.toInt()
        }

        if (style == "Colored" || style == "Gradient" || style == "Neon Glow") {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
        }

        overlayView = view
        try { windowManager?.addView(view, params) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun buildNavView(style: String, pillColor: Int, thicknessPx: Int): View = when (style) {
        "Solid Pill"      -> buildSolidPill(pillColor, thicknessPx)
        "Minimal Pill"    -> buildMinimalPill(pillColor, thicknessPx)
        "Blurred Pill"    -> buildBlurredPill(pillColor, thicknessPx)
        "Colored"         -> buildColoredBar(pillColor)
        "Classic Buttons" -> buildClassicButtons(pillColor, thicknessPx)
        "Gradient"        -> buildGradientBar(pillColor)
        "Neon Glow"       -> buildNeonGlow(pillColor, thicknessPx)
        "Dot Indicators"  -> buildDotIndicators(pillColor, thicknessPx)
        else              -> buildMinimalPill(pillColor, thicknessPx)
    }

    private fun buildSolidPill(pillColor: Int, thicknessPx: Int): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val solidColor = Color.argb(255, Color.red(pillColor), Color.green(pillColor), Color.blue(pillColor))
        val pillH = (thicknessPx * 0.4f).toInt().coerceAtLeast(dpToPx(5f))
        container.addView(View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 100f; setColor(solidColor) }
            layoutParams = LinearLayout.LayoutParams(dpToPx(134f), pillH)
            elevation = 4f
        })
        return container
    }

    private fun buildColoredBar(color: Int): View = View(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(color); cornerRadius = dpToPx(8f).toFloat() }
    }

    private fun buildMinimalPill(pillColor: Int, thicknessPx: Int): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val pillH = (thicknessPx * 0.35f).toInt().coerceAtLeast(dpToPx(4f))
        container.addView(View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 100f; setColor(pillColor) }
            layoutParams = LinearLayout.LayoutParams(dpToPx(134f), pillH)
        })
        return container
    }

    /**
     * Blurred Pill — frosted-glass effect applied to the background ONLY.
     *
     * ROOT CAUSE FIX: The previous implementation called `setRenderEffect()` on
     * the pill View itself, which would also blur any content drawn on top of it.
     *
     * Fix: a FrameLayout wraps two layers:
     *  1. A background View (`blurBg`) that receives `setRenderEffect()` — only
     *     this layer is blurred, keeping the pill shape as a blurred surface.
     *  2. The pill shape View drawn on top — rendered crisply above the blur.
     *
     * Since the Blurred Pill currently has no text/icon children the visual
     * result is the same, but the architecture is now correct so future content
     * added on top will not inherit the blur.
     */
    private fun buildBlurredPill(pillColor: Int, thicknessPx: Int): View {
        val pillH = (thicknessPx * 0.35f).toInt().coerceAtLeast(dpToPx(4f))
        val pillW = dpToPx(134f)

        // Outer container centres the pill.
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER
        }

        // FrameLayout lets us layer the blur background below the pill shape.
        val frame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(pillW, pillH)
        }

        // ── Layer 1: blur background (receives RenderEffect) ──────────────
        val blurBg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(pillColor)
            }
            // BUG FIX: setRenderEffect is applied ONLY to this background View.
            // Any View drawn on top of blurBg (layer 2+) will not be blurred.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(
                    android.graphics.RenderEffect.createBlurEffect(
                        6f, 6f, android.graphics.Shader.TileMode.CLAMP,
                    )
                )
            }
            elevation = 8f
        }

        // ── Layer 2: crisp pill foreground (no blur) ───────────────────────
        // Currently matches blurBg visually, but is intentionally a separate
        // layer so future content (e.g. text, icons) added here stays sharp.
        val pillFg = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            // Semi-transparent so the blurred layer below is visible through it.
            background = GradientDrawable().apply {
                shape        = GradientDrawable.RECTANGLE
                cornerRadius = 100f
                setColor(Color.argb(0, 0, 0, 0)) // fully transparent — blurBg shows through
            }
        }

        frame.addView(blurBg)
        frame.addView(pillFg)
        container.addView(frame)
        return container
    }

    private fun buildClassicButtons(pillColor: Int, thicknessPx: Int): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(Color.argb(60, 0, 0, 0)); cornerRadius = dpToPx(12f).toFloat() }
        }
        val btnW = dpToPx(56f)
        listOf("◀" to "Back", "●" to "Home", "■" to "Recent").forEach { (sym, _) ->
            container.addView(TextView(this).apply {
                text = sym; textSize = 20f; setTextColor(pillColor); gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(btnW, thicknessPx)
            })
        }
        return container
    }

    private fun buildGradientBar(pillColor: Int): View = View(this).apply {
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.TRANSPARENT, pillColor, Color.TRANSPARENT),
        )
    }

    private fun buildNeonGlow(pillColor: Int, thicknessPx: Int): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        container.addView(View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = 100f; setColor(pillColor) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (thicknessPx * 0.2f).toInt().coerceAtLeast(dpToPx(3f)),
            )
            elevation = 12f
        })
        return container
    }

    private fun buildDotIndicators(pillColor: Int, thicknessPx: Int): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val dotSize = (thicknessPx * 0.45f).toInt().coerceIn(dpToPx(6f), dpToPx(18f))
        repeat(3) { i ->
            container.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(
                        if (i == 1) 255 else 130,
                        Color.red(pillColor), Color.green(pillColor), Color.blue(pillColor),
                    ))
                }
                layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                    if (i > 0) leftMargin = dpToPx(16f)
                }
            })
        }
        return container
    }

    private fun parsePillColor(hex: String, opacity: Float): Int = try {
        val base  = Color.parseColor(hex)
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    } catch (_: Exception) { Color.argb(204, 255, 255, 255) }

    private fun dpToPx(dp: Float): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        removeOverlaySync()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
