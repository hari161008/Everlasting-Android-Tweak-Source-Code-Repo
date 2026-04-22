package com.coolappstore.everlastingandroidtweak.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Hardware-accelerated blur on Android 12+ (API 31) via RenderEffect.
 * Falls back to Modifier.blur() on older APIs.
 */
fun Modifier.realBlur(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.graphicsLayer {
            val px = radius.toPx().coerceAtLeast(1f)
            renderEffect = RenderEffect
                .createBlurEffect(px, px, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        this.blur(radius)
    }
