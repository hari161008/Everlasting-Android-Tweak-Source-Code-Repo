package com.coolappstore.everlastingandroidtweak.features.screensaver

import android.animation.*
import android.graphics.*
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.*
import kotlin.random.Random

class EverlastingDreamService : DreamService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    private var moveRunnable: Runnable? = null
    private var animators = mutableListOf<Animator>()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        scope.launch { setup() }
    }

    private suspend fun setup() {
        val theme        = AppPreferences.get(AppPreferences.SCREENSAVER_THEME, "Clock").first()
        val colorHex     = AppPreferences.get(AppPreferences.SCREENSAVER_COLOR, "#FFFFFF").first()
        val clockColorHex= AppPreferences.get(AppPreferences.SCREENSAVER_CLOCK_COLOR, "#FFFFFF").first()
        val size         = AppPreferences.get(AppPreferences.SCREENSAVER_SIZE, 1.0f).first()
        val fadeDuration = AppPreferences.get(AppPreferences.SCREENSAVER_FADE_DURATION, 0).first()
        val moveEnabled  = AppPreferences.get(AppPreferences.SCREENSAVER_MOVE_ENABLED, true).first()
        val moveSpeed    = AppPreferences.get(AppPreferences.SCREENSAVER_MOVE_SPEED, 3).first()
        val burnInEnabled= AppPreferences.get(AppPreferences.SCREENSAVER_BURN_IN_ENABLED, true).first()
        val burnInSec    = AppPreferences.get(AppPreferences.SCREENSAVER_BURN_IN_INTERVAL, 30).first()
        val showDate     = AppPreferences.get(AppPreferences.SCREENSAVER_SHOW_DATE, true).first()
        val showBattery  = AppPreferences.get(AppPreferences.SCREENSAVER_SHOW_BATTERY, false).first()

        val primaryColor  = try { Color.parseColor(colorHex) } catch (_: Exception) { Color.WHITE }
        val clockColor    = try { Color.parseColor(clockColorHex) } catch (_: Exception) { primaryColor }

        val rootBgHex = when (theme) {
            "Windows Phone"     -> AppPreferences.get(AppPreferences.SCREENSAVER_WP_BG_COLOR,   "#000000").first()
            "Moto Screen Saver" -> AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BG_COLOR, "#000000").first()
            else -> "#000000"
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(try { Color.parseColor(rootBgHex) } catch (_: Exception) { Color.BLACK })
        }

        val contentView: View = when (theme) {
            "Matrix Rain"       -> buildDigitalRainView(primaryColor, size)
            "Floating Orbs"     -> buildBubblesView(primaryColor, size)
            "Deep Space"        -> buildStarfieldView(primaryColor)
            "Ocean Wave"        -> buildColorWaveView(primaryColor)
            "Moto Screen Saver" -> buildMotoScreensaver()
            "Windows Phone"     -> buildWindowsPhoneScreensaver()
            else                -> buildMotoScreensaver()   // fallback to Moto
        }

        // Fade in
        if (fadeDuration > 0) {
            contentView.alpha = 0f
            contentView.animate().alpha(1f).setDuration(fadeDuration.toLong()).start()
        }

        // Moto and WP screensavers manage their own full-screen layout
        val isFullScreen = theme == "Moto Screen Saver" || theme == "Windows Phone"
        val lp = if (isFullScreen) {
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        } else {
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }

        root.addView(contentView, lp)
        setContentView(root)

        // Burn-in protection + movement (only for non-fullscreen themes)
        if (!isFullScreen && (burnInEnabled || moveEnabled)) {
            startBurnInProtection(contentView, moveSpeed, burnInSec)
        }
    }

    // ─── Screensaver themes ───────────────────────────────────────────────────

    // ─── Classic Clock ─── Bold large digits + AM/PM + seconds + full date ──────
    private fun buildClock(color: Int, size: Float, showDate: Boolean = true,
                           showBattery: Boolean = false, clockColor: Int = color): View {
        val container = FrameLayout(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val colLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        val boldTf = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        // Large bold time
        col.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            textSize = (96 * size).coerceIn(32f, 240f)
            setTextColor(clockColor)
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            typeface = boldTf
            letterSpacing = -0.04f
        })
        // AM/PM
        col.addView(TextClock(this).apply {
            format12Hour = "a"
            format24Hour = ""
            textSize = (20 * size).coerceIn(8f, 50f)
            setTextColor(Color.argb(150, Color.red(clockColor), Color.green(clockColor), Color.blue(clockColor)))
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = 0.14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        // Seconds ticker
        col.addView(TextClock(this).apply {
            format12Hour = ":ss"
            format24Hour = ":ss"
            textSize = (16 * size).coerceIn(7f, 40f)
            setTextColor(Color.argb(100, Color.red(clockColor), Color.green(clockColor), Color.blue(clockColor)))
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        if (showDate) {
            // Thin accent divider
            col.addView(android.view.View(this).apply {
                setBackgroundColor(Color.argb(70, Color.red(clockColor), Color.green(clockColor), Color.blue(clockColor)))
            }, LinearLayout.LayoutParams((52 * size).toInt(), (2 * size).coerceAtLeast(1f).toInt()).apply {
                setMargins(0, (10 * size).toInt(), 0, (8 * size).toInt())
                gravity = Gravity.CENTER_HORIZONTAL
            })
            // Full day + date
            col.addView(TextClock(this).apply {
                format12Hour = "EEEE, MMMM d"
                format24Hour = "EEEE, MMMM d"
                textSize = (16 * size).coerceIn(9f, 40f)
                setTextColor(Color.argb(170, Color.red(clockColor), Color.green(clockColor), Color.blue(clockColor)))
                gravity = Gravity.CENTER_HORIZONTAL
                includeFontPadding = false
                letterSpacing = 0.04f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
        }
        if (showBattery) {
            val mgr = getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val pct = mgr?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (pct >= 0) {
                col.addView(android.widget.TextView(this).apply {
                    text = "\uD83D\uDD0B $pct%"
                    textSize = (13 * size).coerceIn(8f, 30f)
                    setTextColor(Color.argb(160, Color.red(clockColor), Color.green(clockColor), Color.blue(clockColor)))
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, (8 * size).toInt(), 0, 0)
                    }
                })
            }
        }
        container.addView(col, colLp)
        return container
    }

    // ─── Pure Time ─── Ultra-thin giant time, almost invisible date ──────────────
    private fun buildMinimalClock(color: Int, size: Float): View {
        val container = FrameLayout(this)
        val thinTf = try { Typeface.create("sans-serif-thin", Typeface.NORMAL) }
                     catch (_: Exception) { Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val colLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        // Giant ultra-thin time
        col.addView(TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "H:mm"
            textSize = (112 * size).coerceIn(40f, 280f)
            setTextColor(color)
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            typeface = thinTf
            letterSpacing = 0.08f
        })
        // Tiny spaced-out date — barely visible
        col.addView(TextClock(this).apply {
            format12Hour = "MMM d"
            format24Hour = "MMM d"
            textSize = (10 * size).coerceIn(6f, 26f)
            setTextColor(Color.argb(85, Color.red(color), Color.green(color), Color.blue(color)))
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            typeface = thinTf
            letterSpacing = 0.32f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, (5 * size).toInt(), 0, 0)
            }
        })
        container.addView(col, colLp)
        return container
    }

    // ─── Ambient Float ─── Light-weight glowing clock, day + month-date ─────────
    private fun buildFloatingClock(color: Int, size: Float): View {
        val container = FrameLayout(this)
        val lightTf = try { Typeface.create("sans-serif-light", Typeface.NORMAL) }
                      catch (_: Exception) { Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val colLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        // Medium-weight glowing time
        col.addView(TextClock(this).apply {
            format12Hour = "hh:mm"
            format24Hour = "HH:mm"
            textSize = (80 * size).coerceIn(26f, 200f)
            setTextColor(color)
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            typeface = lightTf
            letterSpacing = 0.02f
            paint.setShadowLayer(
                (9 * size).coerceIn(2f, 22f), 0f, 0f,
                Color.argb(150, Color.red(color), Color.green(color), Color.blue(color)))
        })
        // Day of week — wide letter spacing
        col.addView(TextClock(this).apply {
            format12Hour = "EEEE"
            format24Hour = "EEEE"
            textSize = (14 * size).coerceIn(8f, 34f)
            setTextColor(Color.argb(130, Color.red(color), Color.green(color), Color.blue(color)))
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            typeface = lightTf
            letterSpacing = 0.20f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, (7 * size).toInt(), 0, 0)
            }
        })
        // Month + date — very dim
        col.addView(TextClock(this).apply {
            format12Hour = "MMMM d"
            format24Hour = "MMMM d"
            textSize = (11 * size).coerceIn(7f, 27f)
            setTextColor(Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)))
            gravity = Gravity.CENTER_HORIZONTAL
            includeFontPadding = false
            typeface = lightTf
            letterSpacing = 0.10f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, (2 * size).toInt(), 0, 0)
            }
        })
        container.addView(col, colLp)
        return container
    }


    private fun buildDigitalRainView(color: Int, size: Float): View {
        val baseColor = color
        return object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
            private val cols = mutableListOf<DigitalRainColumn>()
            private val charset = "アイウエオカキクケコサシスセソタチツテトナニヌネノ0123456789ABCDEF"
            private var initialized = false
            private val rnd = Random.Default
            private val frameRunnable = object : Runnable {
                override fun run() { invalidate(); handler.postDelayed(this, (80 / size.coerceIn(0.5f, 3f)).toLong().coerceIn(30L, 150L)) }
            }

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                val charSize = (16 * size).coerceIn(8f, 40f)
                paint.textSize = charSize
                val colCount = (w / charSize).toInt().coerceAtLeast(1)
                cols.clear()
                repeat(colCount) { i ->
                    cols.add(DigitalRainColumn(i * charSize, h.toFloat(), charSize, charset, rnd))
                }
                initialized = true
                handler.post(frameRunnable)
            }

            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.argb(40, 0, 0, 0))
                if (!initialized) return
                cols.forEach { col ->
                    col.update()
                    col.draw(canvas, paint, baseColor)
                }
            }

            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                handler.removeCallbacks(frameRunnable)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun buildBubblesView(color: Int, size: Float): View {
        return object : View(this) {
            private val bubbles = mutableListOf<Bubble>()
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val rnd = Random.Default
            private val frameRunnable = object : Runnable {
                override fun run() { invalidate(); handler.postDelayed(this, 32) }
            }

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                bubbles.clear()
                repeat(18) { bubbles.add(Bubble.random(w.toFloat(), h.toFloat(), color, size, rnd)) }
                handler.post(frameRunnable)
            }

            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.BLACK)
                bubbles.forEach { b ->
                    b.update(width.toFloat(), height.toFloat())
                    paint.color = b.currentColor
                    paint.alpha = b.alpha
                    canvas.drawCircle(b.x, b.y, b.r, paint)
                    // Outline
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.alpha = (b.alpha * 0.4f).toInt()
                    canvas.drawCircle(b.x, b.y, b.r, paint)
                    paint.style = Paint.Style.FILL
                }
            }

            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                handler.removeCallbacks(frameRunnable)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun buildStarfieldView(color: Int): View {
        return object : View(this) {
            private val stars = mutableListOf<Star>()
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private var cx = 0f; private var cy = 0f
            private val rnd = Random.Default
            private val frameRunnable = object : Runnable {
                override fun run() { invalidate(); handler.postDelayed(this, 16) }
            }

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                cx = w / 2f; cy = h / 2f
                stars.clear()
                repeat(150) { stars.add(Star.random(cx, cy, color, rnd)) }
                handler.post(frameRunnable)
            }

            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.BLACK)
                stars.forEach { s ->
                    s.update(cx, cy)
                    paint.color = s.color
                    paint.alpha = s.alpha
                    canvas.drawCircle(s.sx, s.sy, s.size, paint)
                }
            }

            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                handler.removeCallbacks(frameRunnable)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun buildColorWaveView(baseColor: Int): View {
        return object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private var phase = 0f
            private val frameRunnable = object : Runnable {
                override fun run() { phase += 0.03f; invalidate(); handler.postDelayed(this, 32) }
            }

            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                handler.post(frameRunnable)
            }

            override fun onDraw(canvas: Canvas) {
                canvas.drawColor(Color.BLACK)
                val w = width.toFloat(); val h = height.toFloat()
                val waveCount = 3
                for (waveIdx in 0 until waveCount) {
                    val alpha = (160 - waveIdx * 40).coerceAtLeast(40)
                    val amp = h * (0.15f - waveIdx * 0.03f)
                    val freq = 2f + waveIdx * 0.5f
                    val offset = phase + waveIdx * 1.2f

                    val baseHsv = FloatArray(3)
                    Color.RGBToHSV(Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor), baseHsv)
                    val hue = (baseHsv[0] + waveIdx * 30f) % 360f
                    paint.color = Color.HSVToColor(alpha, floatArrayOf(hue, 0.7f, 0.9f))

                    val path = Path()
                    val steps = 80
                    for (i in 0..steps) {
                        val x = w * i / steps
                        val y = h / 2 + amp * sin(freq * Math.PI.toFloat() * x / w + offset)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.lineTo(w, h); path.lineTo(0f, h); path.close()
                    canvas.drawPath(path, paint)
                }
            }

            override fun onDetachedFromWindow() {
                super.onDetachedFromWindow()
                handler.removeCallbacks(frameRunnable)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    // ─── Moto Screen Saver ────────────────────────────────────────────────────
    //
    // Reference layout (from original Motorola TurboPower screen):
    //   • Pure black background
    //   • Soft radial pink/magenta glow, centre ~42% down screen
    //   • Large thin "14%" number, centred inside glow
    //     - number in sans-serif-thin, "%" superscript ~40% size, raised ~35%
    //   • Thin pink arc (smile shape) directly below number
    //     - arc sweep 195°→345° (150°), radius ~14% of screen width
    //     - gradient: transparent→color→color→transparent
    //   • White lightning bolt at the nadir (270°) of that arc
    //   • Large bottom-arc: huge circle centred below the screen,
    //     only the top sliver visible as a gentle dome above TurboPower
    //   • "⚡ TurboPower" bold text at ~95% down screen
    //
    private suspend fun buildMotoScreensaver(): View {

        // ── Read all preferences ──────────────────────────────────────────
        val glowColorHex    = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_COLOR,         "#AA CC0077").first()
        val textColorHex    = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_TEXT_COLOR,         "#FFFFFF").first()
        val arcColorHex     = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_COLOR,          "#FFCC0077").first()
        val bgColorHex      = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BG_COLOR,           "#000000").first()
        val brandingText    = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BRANDING_TEXT,      "TurboPower").first()
        val showBranding    = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SHOW_BRANDING,      true).first()
        val showArc         = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SHOW_ARC,           true).first()
        val glowSize        = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_SIZE,          1.0f).first()
        val pulseSpeed      = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_PULSE_SPEED,        1.0f).first()
        val fontSz          = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_FONT_SIZE,          1.0f).first()
        val useRealPct      = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_USE_REAL_PCT,       true).first()
        val customPct       = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_CUSTOM_PCT,         75).first()
        val arcProgress     = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_PROGRESS,       true).first()
        val glowLayers      = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_LAYERS,        3).first()
        val glowIntensity   = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_INTENSITY,     1.0f).first()
        val bgVignette      = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BG_VIGNETTE,        true).first()
        val boltStyle       = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BOLT_STYLE,         "Filled").first()
        val pctSuffixStyle  = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_PCT_SUFFIX_STYLE,   "%").first()
        val arcStrokeWidth  = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_STROKE_WIDTH,   3.5f).first()
        val animStyle       = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ANIMATION_STYLE,    "Pulse").first()
        val numFontWeight   = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_FONT_WEIGHT,    "Thin").first()
        val numLetterSpc    = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_LETTER_SPC,     -0.02f).first()
        val numOpacity      = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_COLOR_OPACITY,  1.0f).first()
        val suffixSizeMult  = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SUFFIX_SIZE_MULT,   0.40f).first()
        val arcGapMult      = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_GAP_MULT,       0.12f).first()
        val arcRadiusMult   = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_RADIUS_MULT,    0.18f).first()
        val boltOffsetY     = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BOLT_OFFSET_Y,      0.0f).first()
        val arcAngleStart   = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_ANGLE_START,    195f).first()
        val arcAngleSweep   = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_ANGLE_SWEEP,    150f).first()
        // Per-element X/Y positions (0..1 fraction of screen)
        val glowOffsetX     = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_OFFSET_X,      0.50f).first()
        val glowOffsetY     = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_OFFSET_Y,      0.42f).first()
        val numOffsetX      = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_OFFSET_X,       0.50f).first()
        val numOffsetY      = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_OFFSET_Y,       0.42f).first()
        val arcOffsetX      = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_OFFSET_X,       0.50f).first()
        val arcOffsetYAdj   = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_OFFSET_Y_ADJ,   0.0f).first()
        val brandingOffsetX = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BRANDING_OFFSET_X,  0.50f).first()
        val brandingOffsetY = AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BRANDING_OFFSET_Y,  0.950f).first()

        // ── Resolved colours ──────────────────────────────────────────────
        val glowColor  = try { Color.parseColor(glowColorHex.replace(" ",""))  } catch (_: Exception) { Color.parseColor("#AACC0077") }
        val textColor  = try { Color.parseColor(textColorHex)                  } catch (_: Exception) { Color.WHITE }
        val arcColor   = try { Color.parseColor(arcColorHex.replace(" ",""))   } catch (_: Exception) { glowColor   }
        val bgColor    = try { Color.parseColor(bgColorHex)                    } catch (_: Exception) { Color.BLACK }
        val displaySuffix = when (pctSuffixStyle) { "Percent" -> " percent"; "None" -> ""; else -> "%" }

        val battMgr    = getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val realPct    = battMgr?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 50
        val displayPct = if (useRealPct) realPct else customPct.coerceIn(0, 100)

        val numTf: Typeface = when (numFontWeight) {
            "Thin"  -> try { Typeface.create("sans-serif-thin",  Typeface.NORMAL) } catch (_: Exception) { Typeface.DEFAULT }
            "Light" -> try { Typeface.create("sans-serif-light", Typeface.NORMAL) } catch (_: Exception) { Typeface.DEFAULT }
            "Bold"  -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            else    -> Typeface.DEFAULT
        }

        val outerH = handler

        return object : View(this) {
            private var pulsePhase = 0f

            // Pre-allocated paints
            private val pBg    = Paint()
            private val pGlow  = Paint(Paint.ANTI_ALIAS_FLAG)
            private val pNum   = Paint(Paint.ANTI_ALIAS_FLAG)
            private val pArc   = Paint(Paint.ANTI_ALIAS_FLAG)
            private val pBolt  = Paint(Paint.ANTI_ALIAS_FLAG)
            private val pBrand = Paint(Paint.ANTI_ALIAS_FLAG)

            private val ticker = object : Runnable {
                override fun run() {
                    pulsePhase = (pulsePhase + 0.025f * pulseSpeed.coerceIn(0.1f, 5f)) % (2f * PI.toFloat())
                    invalidate()
                    outerH.postDelayed(this, 33L)   // ~30 fps
                }
            }

            override fun onAttachedToWindow()   { super.onAttachedToWindow();  outerH.post(ticker) }
            override fun onDetachedFromWindow() { super.onDetachedFromWindow(); outerH.removeCallbacks(ticker) }

            override fun onDraw(canvas: Canvas) {
                val w   = width.toFloat()
                val h   = height.toFloat()
                val dp  = resources.displayMetrics.density

                // ── Element anchor points ─────────────────────────────────
                val glowCX   = w * glowOffsetX.coerceIn(0f, 1f)
                val glowCY   = h * glowOffsetY.coerceIn(0.10f, 0.90f)
                val numCX    = w * numOffsetX.coerceIn(0f, 1f)
                val numCY    = h * numOffsetY.coerceIn(0.10f, 0.90f)
                val arcCX    = w * arcOffsetX.coerceIn(0f, 1f)
                val brandCX  = w * brandingOffsetX.coerceIn(0f, 1f)
                val brandY   = h * brandingOffsetY.coerceIn(0.40f, 0.99f)

                // ── Black background ──────────────────────────────────────
                canvas.drawColor(bgColor)

                val gr = Color.red(glowColor); val gg = Color.green(glowColor); val gb = Color.blue(glowColor)

                // ── Layered pulsing radial glow ───────────────────────────
                val pulse   = when (animStyle) {
                    "Breathe" -> sin(pulsePhase) * 0.14f
                    "Static"  -> 0f
                    else      -> sin(pulsePhase) * 0.06f   // Pulse (default)
                }
                val baseR   = minOf(w, h) * 0.40f * glowSize.coerceIn(0.3f, 2.5f)
                val glowR   = baseR * (1f + pulse)
                val baseAlpha = (glowIntensity.coerceIn(0.2f, 2.0f) * 160f).toInt().coerceIn(15, 240)

                for (layer in 0 until glowLayers.coerceIn(1, 5)) {
                    val la = (baseAlpha - layer * 28).coerceAtLeast(8)
                    val lr = glowR * (1f + layer * 0.28f)
                    pGlow.shader = RadialGradient(glowCX, glowCY, lr,
                        intArrayOf(
                            Color.argb(la,           gr, gg, gb),
                            Color.argb(la * 6 / 10,  gr, gg, gb),
                            Color.argb(la * 2 / 10,  gr, gg, gb),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0f, 0.30f, 0.65f, 1f),
                        Shader.TileMode.CLAMP)
                    canvas.drawCircle(glowCX, glowCY, lr, pGlow)
                }

                // ── Edge vignette ─────────────────────────────────────────
                if (bgVignette) {
                    pGlow.shader = RadialGradient(w / 2f, h / 2f, maxOf(w, h) * 0.85f,
                        intArrayOf(Color.TRANSPARENT, Color.argb(170, 0, 0, 0)),
                        null, Shader.TileMode.CLAMP)
                    canvas.drawRect(0f, 0f, w, h, pGlow)
                }
                pGlow.shader = null

                // ── Percentage number ─────────────────────────────────────
                // Large thin number + raised superscript "%" — exactly like Moto reference
                val pctFontSz  = w * 0.26f * fontSz.coerceIn(0.3f, 2.5f)
                val sufFontSz  = pctFontSz * suffixSizeMult.coerceIn(0.15f, 1.0f)
                val numAlpha   = (numOpacity.coerceIn(0f, 1f) * 255f).toInt()
                val tr = Color.red(textColor); val tg = Color.green(textColor); val tb = Color.blue(textColor)
                val numCol = Color.argb(numAlpha, tr, tg, tb)

                pNum.apply {
                    textSize      = pctFontSz
                    color         = numCol
                    textAlign     = Paint.Align.LEFT
                    typeface      = numTf
                    letterSpacing = numLetterSpc.coerceIn(-0.10f, 0.30f)
                }

                val numStr     = "$displayPct"
                val numW       = pNum.measureText(numStr)
                val sufPaint   = Paint(pNum).apply { textSize = sufFontSz; textAlign = Paint.Align.LEFT; letterSpacing = 0f }
                val sufW       = if (displaySuffix.isNotEmpty()) sufPaint.measureText(displaySuffix) else 0f
                val totalTextW = numW + sufW
                val baseline   = numCY + pctFontSz * 0.35f
                val numLeft    = numCX - totalTextW / 2f

                canvas.drawText(numStr, numLeft, baseline, pNum)
                if (displaySuffix.isNotEmpty()) {
                    canvas.drawText(displaySuffix, numLeft + numW, baseline - pctFontSz * 0.32f, sufPaint)
                }

                // ── Thin arc + lightning bolt ─────────────────────────────
                if (showArc) {
                    val arcR      = w * arcRadiusMult.coerceIn(0.06f, 0.50f) * fontSz.coerceIn(0.3f, 2.5f)
                    val strokeW   = arcStrokeWidth.coerceIn(1f, 16f) * dp
                    // Arc circle centre: directly below the number baseline
                    val arcCenterY = baseline + pctFontSz * arcGapMult.coerceIn(0.02f, 1.5f) + arcR +
                                     h * arcOffsetYAdj.coerceIn(-0.30f, 0.30f)
                    val arcRect = RectF(arcCX - arcR, arcCenterY - arcR, arcCX + arcR, arcCenterY + arcR)

                    val ar = Color.red(arcColor); val ag2 = Color.green(arcColor); val ab2 = Color.blue(arcColor)

                    // Ghost track — very faint
                    pArc.apply {
                        this.style  = Paint.Style.STROKE
                        strokeWidth = strokeW
                        strokeCap   = Paint.Cap.ROUND
                        color       = Color.argb(35, ar, ag2, ab2)
                        shader      = null
                    }
                    canvas.drawArc(arcRect, arcAngleStart, arcAngleSweep, false, pArc)

                    // Glowing sweep arc with gradient
                    val sweep = if (arcProgress) (arcAngleSweep * displayPct / 100f).coerceAtLeast(3f) else arcAngleSweep
                    pArc.shader = SweepGradient(
                        arcCX, arcCenterY,
                        intArrayOf(
                            Color.TRANSPARENT,
                            Color.argb(200, ar, ag2, ab2),
                            Color.argb(220, ar, ag2, ab2),
                            Color.argb(200, ar, ag2, ab2),
                            Color.TRANSPARENT
                        ),
                        floatArrayOf(0.25f, 0.40f, 0.50f, 0.60f, 0.75f)
                    )
                    canvas.drawArc(arcRect, arcAngleStart, sweep, false, pArc)
                    pArc.shader = null

                    // ── Lightning bolt at nadir of arc (270° = bottom dead centre) ──
                    if (boltStyle != "None") {
                        // Nadir: arcCenterY + arcR (bottom of arc circle)
                        val bCX = arcCX
                        val bCY = arcCenterY + arcR + boltOffsetY.coerceIn(-1f, 1f) * arcR * 0.5f
                        val bs  = pctFontSz * 0.22f * fontSz.coerceIn(0.3f, 2.5f)

                        val bPath = Path()
                        bPath.moveTo(bCX + bs * 0.35f, bCY - bs)
                        bPath.lineTo(bCX - bs * 0.10f, bCY - bs * 0.05f)
                        bPath.lineTo(bCX + bs * 0.12f, bCY - bs * 0.05f)
                        bPath.lineTo(bCX - bs * 0.35f, bCY + bs)
                        bPath.lineTo(bCX + bs * 0.10f, bCY + bs * 0.05f)
                        bPath.lineTo(bCX - bs * 0.12f, bCY + bs * 0.05f)
                        bPath.close()

                        pBolt.apply {
                            this.style  = if (boltStyle == "Outline") Paint.Style.STROKE else Paint.Style.FILL
                            strokeWidth = strokeW * 0.5f
                            color       = Color.argb(numAlpha, tr, tg, tb)
                        }
                        canvas.drawPath(bPath, pBolt)
                    }
                }

                // ── Bottom large arc — only top sliver visible ────────────
                // Giant circle whose centre is pushed below the screen, so only
                // the top edge shows as the gentle arch seen in the Moto reference
                if (showBranding) {
                    val bArcR  = w * 0.58f
                    val bArcCY = h + bArcR * 0.28f   // centre below screen bottom
                    val ar3 = Color.red(arcColor); val ag3 = Color.green(arcColor); val ab3 = Color.blue(arcColor)

                    pArc.apply {
                        this.style  = Paint.Style.STROKE
                        strokeWidth = arcStrokeWidth.coerceIn(1f, 16f) * dp
                        strokeCap   = Paint.Cap.ROUND
                        // Gradient centred at 270° (top of the circle as rendered)
                        shader = SweepGradient(
                            brandCX, bArcCY,
                            intArrayOf(
                                Color.TRANSPARENT,
                                Color.argb(120, ar3, ag3, ab3),
                                Color.argb(190, ar3, ag3, ab3),
                                Color.argb(120, ar3, ag3, ab3),
                                Color.TRANSPARENT
                            ),
                            floatArrayOf(0.30f, 0.43f, 0.50f, 0.57f, 0.70f)
                        )
                    }
                    // 205°→335° sweep (130°), centred at 270° = 12-o'clock of this sub-screen circle
                    canvas.drawArc(
                        RectF(brandCX - bArcR, bArcCY - bArcR, brandCX + bArcR, bArcCY + bArcR),
                        205f, 130f, false, pArc
                    )
                    pArc.shader = null
                }

                // ── ⚡ TurboPower branding ─────────────────────────────────
                if (showBranding) {
                    val bFontSz = w * 0.040f * fontSz.coerceIn(0.3f, 2.5f)
                    val bCapY   = brandY - bFontSz * 0.62f   // vertical centre of cap-height

                    // Bold branding text
                    pBrand.apply {
                        textSize      = bFontSz
                        textAlign     = Paint.Align.LEFT
                        typeface      = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        color         = Color.argb(210, tr, tg, tb)
                        letterSpacing = 0.04f
                    }
                    val bW = pBrand.measureText(brandingText)

                    // Tiny bolt icon, vertically centred on text cap-height
                    val bIconSz = bFontSz * 0.38f
                    val bIconX  = brandCX - bW / 2f - bIconSz * 1.6f
                    val bPath2  = Path()
                    bPath2.moveTo(bIconX + bIconSz * 0.32f, bCapY - bIconSz)
                    bPath2.lineTo(bIconX - bIconSz * 0.10f, bCapY - bIconSz * 0.05f)
                    bPath2.lineTo(bIconX + bIconSz * 0.12f, bCapY - bIconSz * 0.05f)
                    bPath2.lineTo(bIconX - bIconSz * 0.32f, bCapY + bIconSz)
                    bPath2.lineTo(bIconX + bIconSz * 0.10f, bCapY + bIconSz * 0.05f)
                    bPath2.lineTo(bIconX - bIconSz * 0.12f, bCapY + bIconSz * 0.05f)
                    bPath2.close()

                    pBolt.apply { this.style = Paint.Style.FILL; color = Color.argb(210, tr, tg, tb) }
                    canvas.drawPath(bPath2, pBolt)

                    // Draw text starting just right of bolt icon
                    canvas.drawText(brandingText, brandCX - bW / 2f + bIconSz * 0.40f, brandY, pBrand)
                }
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }


    // ─── Windows Phone Screen Saver ───────────────────────────────────────────

    private suspend fun buildWindowsPhoneScreensaver(): View {
        val textColorHex  = AppPreferences.get(AppPreferences.SCREENSAVER_WP_TEXT_COLOR,       "#FFFFFF").first()
        val is24h         = AppPreferences.get(AppPreferences.SCREENSAVER_WP_IS_24H,           false).first()
        val showDate      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_DATE,        true).first()
        val layout        = AppPreferences.get(AppPreferences.SCREENSAVER_WP_LAYOUT,           "Minimal").first()
        val showWeather   = AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_WEATHER,     false).first()
        val city          = AppPreferences.get(AppPreferences.SCREENSAVER_WP_CITY,             "Bellevue").first()
        val condition     = AppPreferences.get(AppPreferences.SCREENSAVER_WP_CONDITION,        "Light rain").first()
        val temperature   = AppPreferences.get(AppPreferences.SCREENSAVER_WP_TEMPERATURE,      "56°F").first()
        val tempHigh      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_TEMP_HIGH,        "64°").first()
        val tempLow       = AppPreferences.get(AppPreferences.SCREENSAVER_WP_TEMP_LOW,         "53°").first()
        val day2Name      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY2_NAME,        "FRI").first()
        val day2High      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY2_HIGH,        "63°").first()
        val day2Low       = AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY2_LOW,         "52°").first()
        val day3Name      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY3_NAME,        "SAT").first()
        val day3High      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY3_HIGH,        "65°").first()
        val day3Low       = AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY3_LOW,         "54°").first()
        val showEvents    = AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_EVENTS,      false).first()
        val eventTitle    = AppPreferences.get(AppPreferences.SCREENSAVER_WP_EVENT_TITLE,      "Design workshop").first()
        val eventLoc      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_EVENT_LOCATION,   "Studio A").first()
        val eventTime     = AppPreferences.get(AppPreferences.SCREENSAVER_WP_EVENT_TIME,       "All day").first()
        val showNotif     = AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_NOTIF,       false).first()
        val showBattery   = AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_BATTERY,     false).first()
        val phoneCount    = AppPreferences.get(AppPreferences.SCREENSAVER_WP_PHONE_COUNT,      0).first()
        val emailCount    = AppPreferences.get(AppPreferences.SCREENSAVER_WP_EMAIL_COUNT,      0).first()
        val fontMult      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_FONT_SIZE,        1.0f).first()
        val showAlarm     = AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_ALARM_ICON,  true).first()
        val bgColorHex    = AppPreferences.get(AppPreferences.SCREENSAVER_WP_BG_COLOR,         "#000000").first()
        val ls            = AppPreferences.get(AppPreferences.SCREENSAVER_WP_LETTER_SPACING,   0.0f).first()
        val showSecs      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_SECONDS,     false).first()
        val timeColorHex  = AppPreferences.get(AppPreferences.SCREENSAVER_WP_TIME_COLOR,       "").first()
        val dateAlpha     = AppPreferences.get(AppPreferences.SCREENSAVER_WP_DATE_OPACITY,     0.73f).first()
        val clockPos      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_CLOCK_POSITION,   "Left").first()
        val showSep       = AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_SEPARATOR,   true).first()
        val ev2Title      = AppPreferences.get(AppPreferences.SCREENSAVER_WP_EVENT2_TITLE,     "").first()
        val ev2Time       = AppPreferences.get(AppPreferences.SCREENSAVER_WP_EVENT2_TIME,      "").first()
        val compact       = AppPreferences.get(AppPreferences.SCREENSAVER_WP_COMPACT_MODE,     false).first()
        val accentHex     = AppPreferences.get(AppPreferences.SCREENSAVER_WP_ACCENT_COLOR,     "#4090FF").first()
        val showWkNum     = AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_WEEK_NUMBER, false).first()
        val notifStyle    = AppPreferences.get(AppPreferences.SCREENSAVER_WP_NOTIF_STYLE,      "Numbers").first()
        val clockMult     = AppPreferences.get(AppPreferences.SCREENSAVER_WP_CLOCK_SIZE,       1.0f).first()
        // New granular size controls
        val clockVertPos  = AppPreferences.get(AppPreferences.SCREENSAVER_WP_CLOCK_VERTICAL_POS, 0.65f).first()
        val clockSizeSp   = AppPreferences.get(AppPreferences.SCREENSAVER_WP_CLOCK_SIZE_SP,    0f).first()   // 0 = auto
        val dateSizeSp    = AppPreferences.get(AppPreferences.SCREENSAVER_WP_DATE_SIZE_SP,     0f).first()   // 0 = auto
        val weatherSizeSp = AppPreferences.get(AppPreferences.SCREENSAVER_WP_WEATHER_SIZE_SP,  0f).first()   // 0 = auto
        val notifSizeSp   = AppPreferences.get(AppPreferences.SCREENSAVER_WP_NOTIF_SIZE_SP,    0f).first()   // 0 = auto
        val paddingLeft   = AppPreferences.get(AppPreferences.SCREENSAVER_WP_PADDING_LEFT,     0.072f).first()

        val textColor   = try { Color.parseColor(textColorHex) } catch (_: Exception) { Color.WHITE }
        val bgColor     = try { Color.parseColor(bgColorHex) }   catch (_: Exception) { Color.BLACK }
        val timeColor   = if (timeColorHex.isNotBlank()) try { Color.parseColor(timeColorHex) } catch (_: Exception) { textColor } else textColor
        val accentColor = try { Color.parseColor(accentHex) }    catch (_: Exception) { Color.parseColor("#4090FF") }

        val isFullLayout = layout == "Full" && showWeather
        val isCentered   = clockPos == "Center"
        val den          = resources.displayMetrics.density
        val outerH       = handler

        // Load Segoe WP font from assets — fall back gracefully if missing
        val segoeTf: Typeface = try {
            Typeface.createFromAsset(assets, "fonts/SegoeWP.ttf")
        } catch (_: Exception) {
            try { Typeface.create("sans-serif-thin", Typeface.NORMAL) }
            catch (_: Exception) { Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
        }

        val iconFor: (String) -> String = { c ->
            when {
                c.contains("rain",    true) || c.contains("drizzle", true) -> "\u2602"
                c.contains("snow",    true) || c.contains("sleet",   true) -> "\u2744"
                c.contains("storm",   true) || c.contains("thunder", true) -> "\u26A1"
                c.contains("cloud",   true) || c.contains("overcast",true)  -> "\u2601"
                c.contains("fog",     true) || c.contains("mist",    true)  -> "\u2601"
                else -> "\u2600"
            }
        }

        return object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isSubpixelText = true }

            private val tick = object : Runnable {
                override fun run() {
                    invalidate()
                    val now = System.currentTimeMillis()
                    outerH.postDelayed(this, 1000L - (now % 1000L))
                }
            }

            override fun onAttachedToWindow()  { super.onAttachedToWindow();  outerH.post(tick) }
            override fun onDetachedFromWindow() { super.onDetachedFromWindow(); outerH.removeCallbacks(tick) }

            override fun onDraw(canvas: Canvas) {
                val vw = width.toFloat()
                val vh = height.toFloat()

                canvas.drawColor(bgColor)

                val lx = vw * paddingLeft.coerceIn(0f, 0.20f)
                val ax = if (isCentered) vw * 0.5f else lx
                val al = if (isCentered) Paint.Align.CENTER else Paint.Align.LEFT

                fun tc(a: Float)  = Color.argb((a.coerceIn(0f,1f)*255).toInt(), Color.red(textColor),  Color.green(textColor),  Color.blue(textColor))
                fun tiC(a: Float) = Color.argb((a.coerceIn(0f,1f)*255).toInt(), Color.red(timeColor),  Color.green(timeColor),  Color.blue(timeColor))
                fun ac(a: Float)  = Color.argb((a.coerceIn(0f,1f)*255).toInt(), Color.red(accentColor),Color.green(accentColor),Color.blue(accentColor))

                // Sizes — user can override each independently, or fall back to fontMult scaling
                val clkPx     = if (clockSizeSp   > 0f) clockSizeSp   * den * clockMult * fontMult
                                else                     90f * den * clockMult * fontMult
                val datePx    = if (dateSizeSp    > 0f) dateSizeSp    * den * fontMult
                                else                     17f * den * fontMult
                val weatherPx = if (weatherSizeSp > 0f) weatherSizeSp * den * fontMult
                                else                     13f * den * fontMult
                val notifPx   = if (notifSizeSp   > 0f) notifSizeSp   * den * fontMult
                                else                     13f * den * fontMult

                val cal     = java.util.Calendar.getInstance()
                val hh      = if (is24h) cal.get(java.util.Calendar.HOUR_OF_DAY)
                              else cal.get(java.util.Calendar.HOUR).let { if (it == 0) 12 else it }
                val mm      = cal.get(java.util.Calendar.MINUTE)
                val ss      = cal.get(java.util.Calendar.SECOND)
                val clkStr  = "${"%02d".format(hh)}:${"%02d".format(mm)}${if (showSecs) ":${"%02d".format(ss)}" else ""}"
                val dayStr  = java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(cal.time)
                val dateStr = java.text.SimpleDateFormat(
                    if (showWkNum) "MMMM d  'W'w" else "MMMM d",
                    java.util.Locale.getDefault()).format(cal.time)

                val gap = (if (compact) 2f else 4f) * den

                // ── Full layout — weather block drawn at top ───────────────
                if (isFullLayout) {
                    var wy      = vh * 0.042f
                    val fcDivX = vw * 0.58f
                    val fcX1   = fcDivX + 10f * den
                    val fcX2   = fcX1 + (vw - fcX1 - lx) / 2f

                    paint.apply { typeface = segoeTf; textAlign = Paint.Align.LEFT; letterSpacing = ls }

                    paint.apply { textSize = weatherPx * 1.05f; color = tc(0.88f) }
                    canvas.drawText(city, lx, wy - paint.ascent(), paint)
                    wy += (-paint.ascent() + paint.descent()) + 2f*den

                    paint.apply { textSize = weatherPx * 0.85f; color = tc(0.55f) }
                    canvas.drawText(condition, lx, wy - paint.ascent(), paint)
                    wy += (-paint.ascent() + paint.descent()) + 7f*den

                    val wIcon = iconFor(condition)
                    paint.apply { textSize = weatherPx * 1.6f; color = tc(0.75f) }
                    canvas.drawText(wIcon, lx, wy - paint.ascent(), paint)
                    val iconW = paint.measureText(wIcon) + 5f*den
                    paint.apply { textSize = weatherPx * 1.95f; color = tc(1.0f) }
                    canvas.drawText(temperature, lx + iconW, wy - paint.ascent(), paint)
                    wy += (-paint.ascent() + paint.descent()) + 4f*den

                    paint.apply { textSize = weatherPx * 0.88f; color = tc(0.60f) }
                    canvas.drawText("$tempHigh  $tempLow", lx, wy - paint.ascent(), paint)
                    val wBotY = wy + (-paint.ascent() + paint.descent())

                    paint.apply { style = Paint.Style.STROKE; strokeWidth = den * 0.8f; color = tc(0.20f) }
                    canvas.drawLine(fcDivX, vh*0.036f, fcDivX, wBotY + 4f*den, paint)
                    paint.style = Paint.Style.FILL

                    fun drawFcCol(name: String, high: String, low: String, fx: Float) {
                        var fcy = vh * 0.042f
                        paint.apply { typeface = segoeTf; textSize = weatherPx * 0.82f; color = tc(0.60f); textAlign = Paint.Align.LEFT; letterSpacing = ls }
                        canvas.drawText(name, fx, fcy - paint.ascent(), paint)
                        fcy += (-paint.ascent() + paint.descent()) + 3f*den
                        paint.apply { textSize = weatherPx * 1.10f; color = tc(0.62f) }
                        canvas.drawText("\u26C5", fx, fcy - paint.ascent(), paint)
                        fcy += (-paint.ascent() + paint.descent()) + 3f*den
                        paint.apply { textSize = weatherPx * 1.0f; color = tc(0.84f) }
                        canvas.drawText(high, fx, fcy - paint.ascent(), paint)
                        fcy += (-paint.ascent() + paint.descent()) + 2f*den
                        paint.apply { textSize = weatherPx * 0.90f; color = tc(0.58f) }
                        canvas.drawText(low, fx, fcy - paint.ascent(), paint)
                    }
                    drawFcCol(day2Name, day2High, day2Low, fcX1)
                    drawFcCol(day3Name, day3High, day3Low, fcX2)

                    if (showSep) {
                        val sepY = wBotY + 14f*den
                        paint.apply { color = tc(0.16f); strokeWidth = den * 0.8f; style = Paint.Style.STROKE }
                        canvas.drawLine(lx, sepY, vw - lx, sepY, paint)
                        paint.style = Paint.Style.FILL
                    }
                }

                // ── Clock — positioned at clockVertPos fraction down screen ─
                // clockVertPos 0.5 = middle, 0.75 = quite low
                val clockAnchorY = vh * clockVertPos.coerceIn(0.35f, 0.90f)

                paint.apply { typeface = segoeTf; textSize = clkPx; color = tiC(1.0f); textAlign = al; letterSpacing = ls }
                val clkBase = clockAnchorY - paint.ascent()
                canvas.drawText(clkStr, ax, clkBase, paint)

                // Alarm ° superscript
                if (showAlarm) {
                    val alSz = clkPx * 0.32f
                    val clkW = paint.measureText(clkStr)
                    paint.textSize = alSz
                    val alX = if (isCentered) ax + clkW / 2f else lx + clkW
                    canvas.drawText("\u00B0", alX, clkBase - clkPx * 0.42f, paint)
                    paint.textSize = clkPx
                }

                var y = clkBase + paint.descent() + gap * 1.2f

                // ── Date ──────────────────────────────────────────────────
                if (showDate) {
                    val da = dateAlpha.coerceIn(0.2f, 1.0f)
                    paint.apply { typeface = segoeTf; textSize = datePx * 1.05f; color = tc(da * 0.88f); textAlign = al; letterSpacing = ls + 0.01f }
                    canvas.drawText(dayStr, ax, y - paint.ascent(), paint)
                    y += (-paint.ascent() + paint.descent()) + gap * 0.5f
                    paint.apply { textSize = datePx * 0.95f; color = tc(da * 0.75f); letterSpacing = ls }
                    canvas.drawText(dateStr, ax, y - paint.ascent(), paint)
                    y += (-paint.ascent() + paint.descent()) + gap
                }

                // ── Events ────────────────────────────────────────────────
                if (showEvents && eventTitle.isNotBlank()) {
                    y += gap * 2.5f
                    paint.apply { typeface = segoeTf; textSize = datePx * 0.85f; color = tc(0.86f); textAlign = al; letterSpacing = ls }
                    canvas.drawText(eventTitle, ax, y - paint.ascent(), paint)
                    y += (-paint.ascent() + paint.descent()) + gap * 0.4f
                    if (eventLoc.isNotBlank()) {
                        paint.apply { textSize = datePx * 0.76f; color = tc(0.60f) }
                        canvas.drawText(eventLoc, ax, y - paint.ascent(), paint)
                        y += (-paint.ascent() + paint.descent()) + gap * 0.4f
                    }
                    paint.apply { textSize = datePx * 0.76f; color = ac(0.80f) }
                    canvas.drawText(eventTime, ax, y - paint.ascent(), paint)
                    y += (-paint.ascent() + paint.descent()) + gap * 0.6f
                    if (ev2Title.isNotBlank()) {
                        y += gap
                        paint.apply { textSize = datePx * 0.85f; color = tc(0.80f) }
                        canvas.drawText(ev2Title, ax, y - paint.ascent(), paint)
                        if (ev2Time.isNotBlank()) {
                            y += (-paint.ascent() + paint.descent()) + gap * 0.4f
                            paint.apply { textSize = datePx * 0.76f; color = ac(0.76f) }
                            canvas.drawText(ev2Time, ax, y - paint.ascent(), paint)
                        }
                    }
                }


                // ── Battery status ────────────────────────────────────────
                if (showBattery) {
                    val battMgr = this@EverlastingDreamService.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
                    val battPct = battMgr?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                    if (battPct >= 0) {
                        val isCharging: Boolean = run {
                            val ifilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                            val bs = this@EverlastingDreamService.registerReceiver(null, ifilter)
                            val st = bs?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                            st == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                            st == android.os.BatteryManager.BATTERY_STATUS_FULL
                        }
                        val battY     = vh * 0.875f
                        val battPx    = notifPx * 1.15f
                        val iconH     = battPx * 0.80f
                        val iconW     = iconH * 1.55f
                        val startX    = if (isCentered) vw * 0.5f - iconW * 0.5f - battPx * 1.5f else lx
                        val iconLeft  = startX
                        val iconRight = startX + iconW
                        val iconTop   = battY - iconH * 0.90f
                        val iconBot   = battY + iconH * 0.10f
                        val stroke    = iconH * 0.11f
                        // Battery outline
                        paint.apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = tc(0.68f) }
                        canvas.drawRoundRect(RectF(iconLeft, iconTop, iconRight, iconBot),
                            stroke * 1.2f, stroke * 1.2f, paint)
                        // Nub on right
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(RectF(iconRight, battY - iconH * 0.48f,
                            iconRight + iconH * 0.12f, battY - iconH * 0.22f), paint)
                        // Fill level — colour-coded
                        val fillPad = stroke * 0.7f
                        val fillW   = (iconW - fillPad * 2f) * (battPct / 100f)
                        paint.color = when {
                            battPct <= 20 -> Color.argb(220, 220, 60, 55)
                            battPct <= 50 -> Color.argb(220, 220, 175, 55)
                            else          -> tc(0.80f)
                        }
                        if (fillW > 0f)
                            canvas.drawRoundRect(RectF(iconLeft + fillPad, iconTop + fillPad,
                                iconLeft + fillPad + fillW, iconBot - fillPad),
                                stroke, stroke, paint)
                        // Charging bolt overlay
                        if (isCharging) {
                            paint.apply { textSize = iconH * 0.70f; color = tc(0.95f)
                                textAlign = Paint.Align.CENTER; typeface = segoeTf; style = Paint.Style.FILL }
                            canvas.drawText("\u26A1", iconLeft + iconW / 2f, battY - iconH * 0.15f, paint)
                        }
                        // Percentage text
                        paint.apply { typeface = segoeTf; textSize = battPx; color = tc(0.78f)
                            textAlign = Paint.Align.LEFT; letterSpacing = ls; style = Paint.Style.FILL }
                        canvas.drawText("$battPct%", iconRight + battPx * 0.35f, battY, paint)
                    }
                }

                // ── Notifications at very bottom ──────────────────────────
                if (showNotif && (phoneCount > 0 || emailCount > 0)) {
                    val notifY = vh * 0.930f
                    val showI  = notifStyle == "Icons"   || notifStyle == "Both"
                    val showN  = notifStyle == "Numbers" || notifStyle == "Both"
                    paint.apply { typeface = segoeTf; textSize = notifPx; letterSpacing = ls; textAlign = Paint.Align.LEFT }
                    var nx = lx
                    if (phoneCount > 0) {
                        paint.color = tc(0.68f)
                        val t = buildString { if (showI) append("\u2706"); if (showN) append(" $phoneCount") }.trim()
                        canvas.drawText(t, nx, notifY, paint)
                        nx += paint.measureText(t) + 30f*den
                    }
                    if (emailCount > 0) {
                        paint.color = tc(0.68f)
                        val t = buildString { if (showI) append("\u2709"); if (showN) append(" $emailCount") }.trim()
                        canvas.drawText(t, nx, notifY, paint)
                    }
                }
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }



    // ─── Burn-in protection: move content slowly ──────────────────────────────

    private fun startBurnInProtection(view: View, speed: Int, intervalSec: Int = 30) {
        val maxOffset = 80
        val stepMs = (intervalSec * 1000L).coerceAtLeast(3000L / speed.coerceIn(1, 10))
        var targetX = 0f; var targetY = 0f

        moveRunnable = object : Runnable {
            override fun run() {
                val rnd = Random.Default
                targetX = (rnd.nextFloat() * 2 - 1) * maxOffset
                targetY = (rnd.nextFloat() * 2 - 1) * maxOffset
                view.animate()
                    .translationX(targetX)
                    .translationY(targetY)
                    .setDuration(stepMs)
                    .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                    .withEndAction { handler.postDelayed(this, stepMs) }
                    .start()
            }
        }
        handler.postDelayed(moveRunnable!!, stepMs)
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
        moveRunnable?.let { handler.removeCallbacks(it) }
        animators.forEach { it.cancel() }
    }

    // ─── Data classes for animation ───────────────────────────────────────────

    private class DigitalRainColumn(
        val x: Float, val height: Float, val charSize: Float,
        val charset: String, val rnd: Random
    ) {
        private var y = rnd.nextFloat() * -height
        private val speed = charSize * (0.5f + rnd.nextFloat() * 1.5f)
        private val length = (5 + rnd.nextInt(20))
        private val chars = Array(length) { charset[rnd.nextInt(charset.length)] }

        fun update() {
            y += speed * 0.5f
            if (y > height + charSize * length) y = rnd.nextFloat() * -height
            if (rnd.nextInt(8) == 0) chars[rnd.nextInt(chars.size)] = charset[rnd.nextInt(charset.length)]
        }

        fun draw(canvas: Canvas, paint: Paint, baseColor: Int) {
            chars.forEachIndexed { i, char ->
                val cy = y + i * charSize
                if (cy < 0 || cy > height) return@forEachIndexed
                val brightness = 1f - i.toFloat() / length
                paint.color = Color.argb(
                    (brightness * 255).toInt(),
                    Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)
                )
                canvas.drawText(char.toString(), x, cy, paint)
            }
        }
    }

    private class Bubble(
        var x: Float, var y: Float, var r: Float,
        var speedX: Float, var speedY: Float,
        val baseColor: Int, var alpha: Int, val rnd: Random
    ) {
        var currentColor: Int = baseColor
        private var colorPhase = rnd.nextFloat() * Math.PI.toFloat() * 2

        fun update(w: Float, h: Float) {
            x += speedX; y += speedY
            colorPhase += 0.03f
            val a = ((sin(colorPhase) * 0.5f + 0.5f) * 200 + 40).toInt()
            val hsv = FloatArray(3)
            Color.RGBToHSV(Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor), hsv)
            hsv[0] = (hsv[0] + 0.5f) % 360f
            currentColor = Color.HSVToColor(hsv)
            alpha = a
            if (x - r < 0 || x + r > w) speedX = -speedX
            if (y - r < 0 || y + r > h) speedY = -speedY
        }

        companion object {
            fun random(w: Float, h: Float, color: Int, size: Float, rnd: Random): Bubble {
                val r = (20 + rnd.nextFloat() * 60) * size.coerceIn(0.5f, 3f)
                return Bubble(
                    x = r + rnd.nextFloat() * (w - 2 * r),
                    y = r + rnd.nextFloat() * (h - 2 * r),
                    r = r,
                    speedX = (rnd.nextFloat() * 2 - 1) * 2f,
                    speedY = (rnd.nextFloat() * 2 - 1) * 2f,
                    baseColor = color, alpha = 180, rnd = rnd
                )
            }
        }
    }

    private class Star(
        var x: Float, var y: Float, val cx: Float, val cy: Float,
        val speed: Float, val color: Int, val rnd: Random
    ) {
        var sx = 0f; var sy = 0f; var size = 1f; var alpha = 0
        private val angle = atan2(y - cy, x - cx)
        private var dist = sqrt((x - cx).pow(2) + (y - cy).pow(2))

        fun update(cx: Float, cy: Float) {
            dist += speed
            val nx = cx + cos(angle) * dist
            val ny = cy + sin(angle) * dist
            sx = nx; sy = ny
            size = (dist / 500f * 4f).coerceIn(0.5f, 5f)
            alpha = ((dist / 400f) * 255).toInt().coerceIn(0, 255)
        }

        fun isOffScreen(w: Float, h: Float) = sx < 0 || sx > w || sy < 0 || sy > h

        companion object {
            fun random(cx: Float, cy: Float, color: Int, rnd: Random): Star {
                val angle = rnd.nextFloat() * 2 * Math.PI.toFloat()
                val dist = rnd.nextFloat() * 50f
                return Star(
                    x = cx + cos(angle) * dist,
                    y = cy + sin(angle) * dist,
                    cx = cx, cy = cy,
                    speed = 0.5f + rnd.nextFloat() * 2f,
                    color = color, rnd = rnd
                )
            }
        }
    }
}
