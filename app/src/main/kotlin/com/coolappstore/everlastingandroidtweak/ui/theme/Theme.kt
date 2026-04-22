package com.coolappstore.everlastingandroidtweak.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalBlurEnabled     = compositionLocalOf { false }
val LocalBlurAmount      = compositionLocalOf { 0f }
val LocalWallpaperActive = compositionLocalOf { false }

// ── Fallback palette — mirrors Hail's full M3 token set exactly ───────────────
private val DarkCS = darkColorScheme(
    primary                  = Color(0xFF9DCBFC),
    onPrimary                = Color(0xFF003355),
    primaryContainer         = Color(0xFF134A74),
    onPrimaryContainer       = Color(0xFFCFE5FF),
    secondary                = Color(0xFFBAC8DA),
    onSecondary              = Color(0xFF243240),
    secondaryContainer       = Color(0xFF3A4857),
    onSecondaryContainer     = Color(0xFFD6E4F7),
    tertiary                 = Color(0xFFD4BEE6),
    onTertiary               = Color(0xFF392A49),
    tertiaryContainer        = Color(0xFF514060),
    onTertiaryContainer      = Color(0xFFF0DBFF),
    error                    = Color(0xFFFFB4AB),
    onError                  = Color(0xFF690005),
    errorContainer           = Color(0xFF93000A),
    onErrorContainer         = Color(0xFFFFDAD6),
    background               = Color(0xFF101418),
    onBackground             = Color(0xFFE0E2E8),
    surface                  = Color(0xFF101418),
    onSurface                = Color(0xFFE0E2E8),
    surfaceVariant           = Color(0xFF42474E),
    onSurfaceVariant         = Color(0xFFC2C7CF),
    outline                  = Color(0xFF8C9199),
    outlineVariant           = Color(0xFF42474E),
    scrim                    = Color(0xFF000000),
    inverseSurface           = Color(0xFFE0E2E8),
    inverseOnSurface         = Color(0xFF2D3135),
    inversePrimary           = Color(0xFF32628D),
    surfaceDim               = Color(0xFF101418),
    surfaceBright            = Color(0xFF36393E),
    surfaceContainerLowest   = Color(0xFF0B0E12),
    surfaceContainerLow      = Color(0xFF191C20),
    surfaceContainer         = Color(0xFF1D2024),
    surfaceContainerHigh     = Color(0xFF272A2F),
    surfaceContainerHighest  = Color(0xFF32353A),
)
private val LightCS = lightColorScheme(
    primary                  = Color(0xFF32628D),
    onPrimary                = Color(0xFFFFFFFF),
    primaryContainer         = Color(0xFFCFE5FF),
    onPrimaryContainer       = Color(0xFF001D34),
    secondary                = Color(0xFF526070),
    onSecondary              = Color(0xFFFFFFFF),
    secondaryContainer       = Color(0xFFD6E4F7),
    onSecondaryContainer     = Color(0xFF0F1D2A),
    tertiary                 = Color(0xFF695779),
    onTertiary               = Color(0xFFFFFFFF),
    tertiaryContainer        = Color(0xFFF0DBFF),
    onTertiaryContainer      = Color(0xFF241532),
    error                    = Color(0xFFBA1A1A),
    onError                  = Color(0xFFFFFFFF),
    errorContainer           = Color(0xFFFFDAD6),
    onErrorContainer         = Color(0xFF410002),
    background               = Color(0xFFF7F9FF),
    onBackground             = Color(0xFF191C20),
    surface                  = Color(0xFFF7F9FF),
    onSurface                = Color(0xFF191C20),
    surfaceVariant           = Color(0xFFDEE3EB),
    onSurfaceVariant         = Color(0xFF42474E),
    outline                  = Color(0xFF73777F),
    outlineVariant           = Color(0xFFC2C7CF),
    scrim                    = Color(0xFF000000),
    inverseSurface           = Color(0xFF2D3135),
    inverseOnSurface         = Color(0xFFEFF1F6),
    inversePrimary           = Color(0xFF9DCBFC),
    surfaceDim               = Color(0xFFD8DAE0),
    surfaceBright            = Color(0xFFF7F9FF),
    surfaceContainerLowest   = Color(0xFFFFFFFF),
    surfaceContainerLow      = Color(0xFFF2F3F9),
    surfaceContainer         = Color(0xFFECEEF4),
    surfaceContainerHigh     = Color(0xFFE6E8EE),
    surfaceContainerHighest  = Color(0xFFE0E2E8),
)

// ── Pure-black surface overrides (FIX: now covers ALL container tokens so
//    cards and surfaces actually go black instead of staying grey) ─────────────
private fun ColorScheme.applyPureBlack() = copy(
    // ── All backgrounds and surfaces → true pitch black ───────────────────────
    background               = Color(0xFF000000),
    surface                  = Color(0xFF000000),
    surfaceDim               = Color(0xFF000000),
    surfaceContainerLowest   = Color(0xFF000000),
    surfaceContainerLow      = Color(0xFF070707),
    surfaceContainer         = Color(0xFF0A0A0A),
    surfaceContainerHigh     = Color(0xFF0F0F0F),
    surfaceContainerHighest  = Color(0xFF141414),
    surfaceVariant           = Color(0xFF111111),
    surfaceBright            = Color(0xFF161616),
    // Foregrounds — bright white readable on pitch-black backgrounds ──────────
    onBackground             = Color(0xFFEEEEEE),
    onSurface                = Color(0xFFEEEEEE),
    onSurfaceVariant         = Color(0xFFAAAAAA),
    // Outlines — subtle dividers on black ────────────────────────────────────
    outline                  = Color(0xFF555555),
    outlineVariant           = Color(0xFF252525),
    // Inverse ─────────────────────────────────────────────────────────────────
    inverseSurface           = Color(0xFFEEEEEE),
    inverseOnSurface         = Color(0xFF181818),
)

// ── Pure-white surface overrides (FIX: same — all container tokens covered) ───
private fun ColorScheme.applyPureWhite() = copy(
    background               = Color(0xFFFFFFFF),
    surface                  = Color(0xFFFFFFFF),
    surfaceVariant           = Color(0xFFF0F0F0),
    surfaceContainer         = Color(0xFFF5F5F5),
    surfaceContainerHigh     = Color(0xFFEEEEEE),
    surfaceContainerHighest  = Color(0xFFE8E8E8),
    surfaceContainerLow      = Color(0xFFF9F9F9),
    surfaceContainerLowest   = Color(0xFFFFFFFF),
    surfaceDim               = Color(0xFFE8E8E8),
    surfaceBright            = Color(0xFFFFFFFF),
    // Foregrounds — dark so content is readable on white
    onBackground             = Color(0xFF111111),
    onSurface                = Color(0xFF111111),
    onSurfaceVariant         = Color(0xFF555555),
    // Outlines stay visible against white
    outline                  = Color(0xFF888888),
    outlineVariant           = Color(0xFFCCCCCC),
    // Inverse stays reversed
    inverseSurface           = Color(0xFF1A1A1A),
    inverseOnSurface         = Color(0xFFF5F5F5),
)

@Composable
fun EverlastingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeMode: Int = 0,
    blurEnabled: Boolean = false,
    blurAmount: Float = 16f,
    wallpaperActive: Boolean = false,
    customPrimaryColor: String = "",
    content: @Composable () -> Unit
) {
    val isAutoBW    = themeMode == 5
    val isPureBlack = themeMode == 3 || (isAutoBW && darkTheme)
    val isPureWhite = themeMode == 4 || (isAutoBW && !darkTheme)
    val isDark = isPureBlack || (themeMode == 0 && darkTheme) || themeMode == 2

    var base: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (isDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        isDark -> DarkCS
        else   -> LightCS
    }

    // FIX: Pure black / pure white now override ALL surface container tokens so
    // cards and containers actually respond to theme changes (not just background
    // and surface which are only used for the window/scaffold backgrounds).
    base = when {
        isPureBlack -> base.applyPureBlack()
        isPureWhite -> base.applyPureWhite()
        else -> base
    }

    // Custom primary color (only when dynamic color is OFF)
    if (!dynamicColor && customPrimaryColor.isNotBlank()) {
        val col = try { Color(android.graphics.Color.parseColor(customPrimaryColor)) } catch (_: Exception) { null }
        if (col != null) {
            base = base.copy(
                primary = col,
                primaryContainer = col.copy(alpha = 0.25f),
                onPrimary = if (isDark) Color.Black else Color.White
            )
        }
    }

    // Blur UI — make ALL surface tokens semi-transparent so every card and
    // container lets the blurred background gradient show through them.
    // Previously only 'surface' and 'surfaceVariant' were overridden — cards
    // use 'surfaceContainer' which stayed fully opaque, so blur had no effect.
    val finalScheme = when {
        blurEnabled && wallpaperActive -> base.copy(
            background              = Color.Transparent,
            surface                 = if (isDark) Color(0xBB0F1417) else Color(0xBBF7F9FC),
            surfaceVariant          = if (isDark) Color(0xAA40484F) else Color(0xAADDE3EA),
            surfaceContainer        = if (isDark) Color(0xBB1D2024) else Color(0xBBECEEF4),
            surfaceContainerHigh    = if (isDark) Color(0xBB272A2F) else Color(0xBBE6E8EE),
            surfaceContainerHighest = if (isDark) Color(0xBB32353A) else Color(0xBBE0E2E8),
            surfaceContainerLow     = if (isDark) Color(0xBB191C20) else Color(0xBBF2F3F9),
            surfaceContainerLowest  = if (isDark) Color(0xBB0B0E12) else Color(0xBBFFFFFF),
        )
        blurEnabled -> base.copy(
            surface                 = if (isDark) Color(0xCC0F1417) else Color(0xCCF7F9FC),
            surfaceVariant          = if (isDark) Color(0xCC40484F) else Color(0xCCDDE3EA),
            surfaceContainer        = if (isDark) Color(0xCC1D2024) else Color(0xCCECEEF4),
            surfaceContainerHigh    = if (isDark) Color(0xCC272A2F) else Color(0xCCE6E8EE),
            surfaceContainerHighest = if (isDark) Color(0xCC32353A) else Color(0xCCE0E2E8),
            surfaceContainerLow     = if (isDark) Color(0xCC191C20) else Color(0xCCF2F3F9),
            surfaceContainerLowest  = if (isDark) Color(0xCC0B0E12) else Color(0xCCFFFFFF),
        )
        wallpaperActive -> base.copy(
            background              = Color.Transparent,
            surface                 = if (isDark) Color(0xCC0F1417) else Color(0xCCF7F9FC),
            surfaceVariant          = if (isDark) Color(0xBB40484F) else Color(0xBBDDE3EA),
            surfaceContainer        = if (isDark) Color(0xCC1D2024) else Color(0xCCECEEF4),
            surfaceContainerHigh    = if (isDark) Color(0xCC272A2F) else Color(0xCCE6E8EE),
            surfaceContainerHighest = if (isDark) Color(0xCC32353A) else Color(0xCCE0E2E8),
            surfaceContainerLow     = if (isDark) Color(0xCC191C20) else Color(0xCCF2F3F9),
            surfaceContainerLowest  = if (isDark) Color(0xCC0B0E12) else Color(0xCCFFFFFF),
        )
        else -> base
    }

    val isLightBar = isPureWhite || (!isPureBlack && !darkTheme && themeMode != 2)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val win = (view.context as Activity).window
            win.statusBarColor = Color.Transparent.toArgb()
            win.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(win, view).isAppearanceLightStatusBars = isLightBar
        }
    }

    CompositionLocalProvider(
        LocalBlurEnabled provides blurEnabled,
        LocalBlurAmount  provides blurAmount,
        LocalWallpaperActive provides wallpaperActive
    ) {
        MaterialTheme(colorScheme = finalScheme, typography = Typography, content = content)
    }
}
