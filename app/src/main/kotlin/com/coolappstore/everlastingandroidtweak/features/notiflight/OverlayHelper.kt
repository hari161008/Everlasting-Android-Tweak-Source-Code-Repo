package com.coolappstore.everlastingandroidtweak.features.notiflight

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingSide
import com.coolappstore.everlastingandroidtweak.features.notiflight.model.NotificationLightingStyle

object OverlayHelper {

    const val STROKE_DP = 8
    const val CORNER_RADIUS_DP = 20
    const val INDICATOR_SIZE_DP = 48

    fun createOverlayView(
        context: Context,
        color: Int,
        strokeDp: Float = STROKE_DP.toFloat(),
        cornerRadiusDp: Float = CORNER_RADIUS_DP.toFloat(),
        style: NotificationLightingStyle = NotificationLightingStyle.STROKE,
        glowSides: Set<NotificationLightingSide> = setOf(
            NotificationLightingSide.LEFT,
            NotificationLightingSide.RIGHT
        ),
        indicatorScale: Float = 1.0f,
        randomShapes: Boolean = false,
        showBackground: Boolean = false
    ): FrameLayout {
        if (style == NotificationLightingStyle.GLOW)      return createGlowOverlayView(context, color, glowSides, showBackground)
        if (style == NotificationLightingStyle.INDICATOR) return createIndicatorOverlayView(context, color, indicatorScale, showBackground)
        if (style == NotificationLightingStyle.SWEEP)     return createSweepOverlayView(context, color, strokeDp, showBackground)

        val overlay = FrameLayout(context)
        if (showBackground) overlay.setBackgroundColor(Color.BLACK)
        val strokePx      = (context.resources.displayMetrics.density * strokeDp).toInt()
        val cornerRadiusPx = (context.resources.displayMetrics.density * cornerRadiusDp).toInt()

        val drawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(strokePx, color)
            cornerRadius = cornerRadiusPx.toFloat()
        }

        if (showBackground) {
            overlay.addView(View(context).apply {
                background = drawable
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
        } else {
            overlay.background = drawable
        }
        return overlay
    }

    private fun createGlowOverlayView(
        context: Context, color: Int,
        sides: Set<NotificationLightingSide>, showBackground: Boolean
    ): FrameLayout {
        val overlay = FrameLayout(context)
        if (showBackground) overlay.setBackgroundColor(Color.BLACK)
        if (sides.contains(NotificationLightingSide.LEFT)) {
            overlay.addView(View(context).apply {
                tag = "left_glow"; alpha = 0.5f
                layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.START }
                background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(color, Color.TRANSPARENT))
            })
        }
        if (sides.contains(NotificationLightingSide.RIGHT)) {
            overlay.addView(View(context).apply {
                tag = "right_glow"; alpha = 0.5f
                layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.END }
                background = GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, intArrayOf(color, Color.TRANSPARENT))
            })
        }
        if (sides.contains(NotificationLightingSide.TOP)) {
            overlay.addView(View(context).apply {
                tag = "top_glow"; alpha = 0.5f
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { gravity = Gravity.TOP }
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(color, Color.TRANSPARENT))
            })
        }
        if (sides.contains(NotificationLightingSide.BOTTOM)) {
            overlay.addView(View(context).apply {
                tag = "bottom_glow"; alpha = 0.5f
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { gravity = Gravity.BOTTOM }
                background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(color, Color.TRANSPARENT))
            })
        }
        return overlay
    }

    private fun createIndicatorOverlayView(
        context: Context, color: Int, indicatorScale: Float, showBackground: Boolean
    ): FrameLayout {
        val overlay = FrameLayout(context)
        if (showBackground) overlay.setBackgroundColor(Color.BLACK)
        val density = context.resources.displayMetrics.density
        val size = (INDICATOR_SIZE_DP * density * indicatorScale).toInt()
        overlay.addView(View(context).apply {
            tag = "loading_indicator"
            layoutParams = FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER }
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            scaleX = 0f; scaleY = 0f
        })
        return overlay
    }

    private fun createSweepOverlayView(
        context: Context, color: Int, strokeDp: Float, showBackground: Boolean
    ): FrameLayout {
        val overlay = FrameLayout(context)
        if (showBackground) overlay.setBackgroundColor(Color.BLACK)
        overlay.addView(SweepCircleView(context, color, strokeDp).apply {
            tag = "sweep_view"; alpha = 0f
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })
        return overlay
    }

    private class SweepCircleView(context: Context, val color: Int, val strokeDp: Float) : View(context) {
        var centerX: Float = 0f
        var centerY: Float = 0f
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            this.color = this@SweepCircleView.color
            strokeWidth = context.resources.displayMetrics.density * strokeDp
            maskFilter = android.graphics.BlurMaskFilter(
                context.resources.displayMetrics.density * 15f,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
        var currentRadius: Float = 0f
            set(value) { field = value; invalidate() }
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            if (currentRadius <= 0) return
            canvas.drawCircle(centerX, centerY, currentRadius, paint)
        }
    }

    fun createOverlayLayoutParams(overlayType: Int, flags: Int = 0, isTouchable: Boolean = false): WindowManager.LayoutParams {
        var baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        if (!isTouchable) baseFlags = baseFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            overlayType, baseFlags or flags, android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try { params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES } catch (_: Exception) {}
        }
        return params
    }

    fun addOverlayView(windowManager: WindowManager?, view: View, params: WindowManager.LayoutParams): Boolean {
        return try { windowManager?.addView(view, params); true } catch (e: Exception) { e.printStackTrace(); false }
    }

    fun removeOverlayView(windowManager: WindowManager?, view: View) {
        try { windowManager?.removeView(view) } catch (_: Exception) {}
    }

    fun removeAllOverlays(windowManager: WindowManager?, overlayViews: MutableList<View>) {
        try { overlayViews.forEach { removeOverlayView(windowManager, it) } } catch (e: Exception) { e.printStackTrace() }
        overlayViews.clear()
    }

    fun showPreview(
        view: View, style: NotificationLightingStyle, strokeWidthDp: Float,
        indicatorX: Float = 50f, indicatorY: Float = 2f, indicatorScale: Float = 1.0f,
        pulseDurationMillis: Long = 3000L, onAnimationEnd: (() -> Unit)? = null
    ) {
        if (style == NotificationLightingStyle.GLOW) {
            val vg = view as? ViewGroup
            if (vg != null) {
                val density = view.resources.displayMetrics.density
                val maxPixels = (strokeWidthDp * density * 12).toInt()
                vg.findViewWithTag<View>("left_glow")?.updateLayoutParams { width = maxPixels }
                vg.findViewWithTag<View>("right_glow")?.updateLayoutParams { width = maxPixels }
                vg.findViewWithTag<View>("top_glow")?.updateLayoutParams { height = maxPixels }
                vg.findViewWithTag<View>("bottom_glow")?.updateLayoutParams { height = maxPixels }
            }
        } else if (style == NotificationLightingStyle.INDICATOR) {
            view.alpha = 1f
            view.findViewWithTag<View>("loading_indicator")?.apply {
                val pw = view.resources.displayMetrics.widthPixels
                val ph = view.resources.displayMetrics.heightPixels
                translationX = (pw * (indicatorX / 100f)) - (pw / 2f)
                translationY = (ph * (indicatorY / 100f)) - (ph / 2f)
                scaleX = indicatorScale; scaleY = indicatorScale
            }
        } else if (style == NotificationLightingStyle.SWEEP) {
            pulseSweepOverlay(view as ViewGroup, 1, pulseDurationMillis, strokeWidthDp, indicatorX, onAnimationEnd)
            return
        }
        fadeInOverlay(view, onAnimationEnd)
    }

    fun fadeInOverlay(view: View, onAnimationEnd: (() -> Unit)? = null) {
        view.alpha = 0f
        ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
            duration = 1000
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onAnimationEnd?.invoke() }
            })
            start()
        }
    }

    fun fadeOutAndRemoveOverlay(
        windowManager: WindowManager?, view: View,
        overlayViews: MutableList<View>, onAnimationEnd: (() -> Unit)? = null
    ) {
        ObjectAnimator.ofFloat(view, "alpha", view.alpha, 0f).apply {
            duration = 1000
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeOverlayView(windowManager, view)
                    overlayViews.remove(view)
                    onAnimationEnd?.invoke()
                }
            })
            start()
        }
    }

    fun pulseOverlay(
        view: View, maxPulses: Int = 3, pulseDurationMillis: Long = 3000,
        style: NotificationLightingStyle = NotificationLightingStyle.STROKE,
        strokeWidthDp: Float = STROKE_DP.toFloat(),
        indicatorX: Float = 50f, indicatorY: Float = 2f, indicatorScale: Float = 1.0f,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        if (style == NotificationLightingStyle.GLOW) {
            pulseGlowOverlay(view as ViewGroup, maxPulses, pulseDurationMillis, strokeWidthDp, onAnimationEnd); return
        }
        if (style == NotificationLightingStyle.INDICATOR) {
            pulseIndicatorOverlay(view as ViewGroup, pulseDurationMillis, indicatorX, indicatorY, indicatorScale, onAnimationEnd); return
        }
        if (style == NotificationLightingStyle.SWEEP) {
            pulseSweepOverlay(view as ViewGroup, maxPulses, pulseDurationMillis, strokeWidthDp, indicatorX, onAnimationEnd); return
        }

        var pulseCount = 0
        val durationIn   = (pulseDurationMillis * 0.1).toLong()
        val durationHold = (pulseDurationMillis * 0.4).toLong()
        val durationOut  = (pulseDurationMillis * 0.5).toLong()

        fun startPulse() {
            if (pulseCount >= maxPulses) { onAnimationEnd?.invoke(); return }
            pulseCount++
            ObjectAnimator.ofFloat(view, "alpha", 0f, 1f).apply {
                duration = durationIn
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.postDelayed({
                            ObjectAnimator.ofFloat(view, "alpha", 1f, 0f).apply {
                                duration = durationOut
                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) { startPulse() }
                                })
                                start()
                            }
                        }, durationHold)
                    }
                })
                start()
            }
        }
        startPulse()
    }

    private fun pulseGlowOverlay(
        view: ViewGroup, maxPulses: Int, pulseDurationMillis: Long,
        strokeWidthDp: Float, onAnimationEnd: (() -> Unit)?
    ) {
        val leftGlow   = view.findViewWithTag<View>("left_glow")
        val rightGlow  = view.findViewWithTag<View>("right_glow")
        val topGlow    = view.findViewWithTag<View>("top_glow")
        val bottomGlow = view.findViewWithTag<View>("bottom_glow")
        val density   = view.resources.displayMetrics.density
        val maxPixels = (strokeWidthDp * density * 12).toInt()
        var pulseCount = 0
        val expandDuration = (pulseDurationMillis * 0.1).toLong()
        val holdDuration   = (pulseDurationMillis * 0.4).toLong()
        val shrinkDuration = (pulseDurationMillis * 0.5).toLong()

        fun startPulse() {
            if (pulseCount >= maxPulses) { onAnimationEnd?.invoke(); return }
            pulseCount++
            ValueAnimator.ofInt(0, maxPixels).apply {
                duration = expandDuration; interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val dim = anim.animatedValue as Int
                    leftGlow?.updateLayoutParams { width = dim }; rightGlow?.updateLayoutParams { width = dim }
                    topGlow?.updateLayoutParams { height = dim }; bottomGlow?.updateLayoutParams { height = dim }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.postDelayed({
                            ValueAnimator.ofInt(maxPixels, 0).apply {
                                duration = shrinkDuration; interpolator = AccelerateDecelerateInterpolator()
                                addUpdateListener { anim ->
                                    val dim = anim.animatedValue as Int
                                    leftGlow?.updateLayoutParams { width = dim }; rightGlow?.updateLayoutParams { width = dim }
                                    topGlow?.updateLayoutParams { height = dim }; bottomGlow?.updateLayoutParams { height = dim }
                                }
                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) { startPulse() }
                                })
                            }.start()
                        }, holdDuration)
                    }
                })
            }.start()
        }
        startPulse()
    }

    private fun pulseIndicatorOverlay(
        view: ViewGroup, durationMillis: Long,
        indicatorX: Float, indicatorY: Float, indicatorScale: Float,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        val indicator = view.findViewWithTag<View>("loading_indicator") ?: return
        val pw = view.resources.displayMetrics.widthPixels
        val ph = view.resources.displayMetrics.heightPixels
        indicator.translationX = (pw * (indicatorX / 100f)) - (pw / 2f)
        indicator.translationY = (ph * (indicatorY / 100f)) - (ph / 2f)
        view.alpha = 1f
        indicator.animate()
            .scaleX(indicatorScale).scaleY(indicatorScale).setDuration(400)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.postDelayed({
                        indicator.animate().scaleX(0f).scaleY(0f).setDuration(400)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) { onAnimationEnd?.invoke() }
                            }).start()
                    }, (durationMillis - 800).coerceAtLeast(0))
                }
            }).start()
    }

    private fun pulseSweepOverlay(
        view: ViewGroup, maxPulses: Int, pulseDurationMillis: Long,
        strokeWidthDp: Float, sweepPositionX: Float, onAnimationEnd: (() -> Unit)? = null
    ) {
        val sweepView = view.findViewWithTag<View>("sweep_view") as? SweepCircleView ?: return
        val dm = view.resources.displayMetrics
        val screenWidth  = dm.widthPixels
        val screenHeight = dm.heightPixels

        val startX = when {
            sweepPositionX < 34f -> 0f
            sweepPositionX > 66f -> screenWidth.toFloat()
            else -> screenWidth / 2f
        }
        val startY = 16f * dm.density
        sweepView.centerX = startX; sweepView.centerY = startY

        val maxDistX = maxOf(startX, screenWidth - startX)
        val maxDistY = maxOf(startY, screenHeight - startY)
        val maxRadius = Math.sqrt((maxDistX * maxDistX + maxDistY * maxDistY).toDouble()).toFloat() + (15f * dm.density)

        var pulseCount = 0
        fun startPulse() {
            if (pulseCount >= maxPulses) { onAnimationEnd?.invoke(); return }
            pulseCount++
            sweepView.alpha = 1f; sweepView.currentRadius = 0f
            ValueAnimator.ofFloat(0f, maxRadius).apply {
                duration = pulseDurationMillis
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val radius = anim.animatedValue as Float
                    sweepView.currentRadius = radius
                    sweepView.alpha = 1f - (radius / maxRadius)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { startPulse() }
                })
            }.start()
        }
        startPulse()
    }
}
