package com.coolappstore.everlastingandroidtweak.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.coolappstore.everlastingandroidtweak.R
import com.coolappstore.everlastingandroidtweak.features.watermark.ColorMode

@Composable
fun ColorModeOption(
    mode: ColorMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Int? = null
) {
    val view = LocalView.current
    val color = when (mode) {
        ColorMode.LIGHT -> Color.White
        ColorMode.DARK  -> Color.Black
        ColorMode.ACCENT_LIGHT, ColorMode.ACCENT_DARK -> {
            val base = accentColor ?: android.graphics.Color.GRAY
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(base, hsl)
            hsl[2] = if (mode == ColorMode.ACCENT_LIGHT) 0.8f else 0.2f
            Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
        }
    }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 3.dp else 1.dp

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .border(borderWidth, borderColor, CircleShape)
            .clickable {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        if (mode == ColorMode.ACCENT_LIGHT || mode == ColorMode.ACCENT_DARK) {
            Icon(
                painter = painterResource(R.drawable.rounded_image_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (mode == ColorMode.ACCENT_LIGHT) Color.Black else Color.White
            )
        }
    }
}

// FIX: HorizontalMultiBrowseCarousel / rememberCarouselState are Material3 Expressive APIs
// not available in the Material3 version Everlasting uses.
// Replaced with a LazyRow — identical functionality, no special APIs needed.
@Composable
fun LogoCarouselPicker(
    selectedResId: Int?,
    onLogoSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val logos = listOf(
        R.drawable.apple, R.drawable.cmf, R.drawable.google, R.drawable.moto,
        R.drawable.nothing, R.drawable.oppo, R.drawable.samsung,
        R.drawable.sony, R.drawable.vivo, R.drawable.xiaomi
    )
    val view = LocalView.current

    LazyRow(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .height(84.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(logos) { resId ->
            val isSelected = selectedResId == resId
            val containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.surfaceContainerHigh
            val contentColor   = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                 else MaterialTheme.colorScheme.onSurface

            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(containerColor)
                    .clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        onLogoSelected(resId)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(resId),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = contentColor
                )
            }
        }
    }
}
