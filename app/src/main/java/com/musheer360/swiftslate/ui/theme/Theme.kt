package com.musheer360.swiftslate.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Matches the warm dark palette visible in the Everlasting Tweak main app
private val DarkColorScheme = darkColorScheme(
    background           = Color(0xFF131108),  // very dark olive — matches host app bg
    surface              = Color(0xFF1A170F),  // slightly lighter for top bars
    surfaceVariant       = Color(0xFF252218),  // card color — matches host app surfaceVariant
    onBackground         = Color(0xFFEDE5D8),  // warm white text
    onSurface            = Color(0xFFEDE5D8),
    onSurfaceVariant     = Color(0xFF9A9080),  // warm muted subtitle text
    outline              = Color(0xFF3A3528),  // divider colour
    outlineVariant       = Color(0xFF2E2A1E),  // subtler divider
    primary              = Color(0xFFCB9A14),  // amber — matches category labels in host
    onPrimary            = Color(0xFF1A1200),
    primaryContainer     = Color(0xFF3A2E08),  // tinted button background
    onPrimaryContainer   = Color(0xFFFFDF8C),
    secondary            = Color(0xFFB0A080),
    onSecondary          = Color(0xFF1A1200),
    secondaryContainer   = Color(0xFF2A2518),
    onSecondaryContainer = Color(0xFFE8DDB8),
    error                = Color(0xFFFF453A),
    onError              = Color(0xFFFFFFFF),
    errorContainer       = Color(0xFF3B1012),
    onErrorContainer     = Color(0xFFFFB4AB),
    tertiary             = Color(0xFF32D74B),  // success green
    onTertiary           = Color(0xFF000000),
    tertiaryContainer    = Color(0xFFFFD60A),  // warning amber
    onTertiaryContainer  = Color(0xFF000000),
)

@Composable
fun SwiftSlateTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(colorScheme = DarkColorScheme, content = content)
}
