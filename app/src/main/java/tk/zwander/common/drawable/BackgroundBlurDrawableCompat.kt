package tk.zwander.common.drawable

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.appcompat.graphics.drawable.DrawableWrapperCompat
import tk.zwander.common.util.createBackgroundBlurDrawableReflect
import tk.zwander.common.util.peekLogUtils

/**
 * Compat wrapper for the hidden BackgroundBlurDrawable.
 * All method calls on the real BackgroundBlurDrawable are made via reflection
 * through [wrapped], which is the actual BackgroundBlurDrawable (a subclass of Drawable).
 */
sealed class BackgroundBlurDrawableCompat(protected open val wrapped: Drawable) : DrawableWrapperCompat(wrapped) {
    abstract fun setColor(@ColorInt color: Int)
    abstract fun setBlurRadius(blurRadius: Int)
    abstract fun setCornerRadius(cornerRadius: Float)
    abstract fun setCornerRadius(
        cornerRadiusTL: Float,
        cornerRadiusTR: Float,
        cornerRadiusBL: Float,
        cornerRadiusBR: Float,
    )

    override fun draw(canvas: Canvas) {
        if (canvas.isHardwareAccelerated) {
            peekLogUtils?.debugLog("Drawing BackgroundBlurDrawable.", null)
            wrapped.draw(canvas)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    class BackgroundBlurDrawableCompatApi31(override val wrapped: Drawable) : BackgroundBlurDrawableCompat(wrapped) {
        override fun setColor(@ColorInt color: Int) {
            try { wrapped.javaClass.getMethod("setColor", Int::class.java).invoke(wrapped, color) } catch (_: Exception) {}
        }

        override fun setBlurRadius(blurRadius: Int) {
            try { wrapped.javaClass.getMethod("setBlurRadius", Int::class.java).invoke(wrapped, blurRadius) } catch (_: Exception) {}
        }

        override fun setCornerRadius(cornerRadius: Float) {
            try { wrapped.javaClass.getMethod("setCornerRadius", Float::class.java).invoke(wrapped, cornerRadius) } catch (_: Exception) {}
        }

        override fun setCornerRadius(
            cornerRadiusTL: Float,
            cornerRadiusTR: Float,
            cornerRadiusBL: Float,
            cornerRadiusBR: Float,
        ) {
            try {
                wrapped.javaClass.getMethod(
                    "setCornerRadius",
                    Float::class.java, Float::class.java, Float::class.java, Float::class.java,
                ).invoke(wrapped, cornerRadiusTL, cornerRadiusTR, cornerRadiusBL, cornerRadiusBR)
            } catch (_: Exception) {}
        }
    }

    companion object {
        /**
         * Creates a [BackgroundBlurDrawableCompat] from a raw ViewRootImpl object.
         * Returns null on API < 31 or if reflection fails.
         */
        operator fun invoke(viewRootImplRaw: Any?): BackgroundBlurDrawableCompat? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || viewRootImplRaw == null) return null
            val drawable = viewRootImplRaw.createBackgroundBlurDrawableReflect() ?: return null
            return BackgroundBlurDrawableCompatApi31(drawable)
        }
    }
}
