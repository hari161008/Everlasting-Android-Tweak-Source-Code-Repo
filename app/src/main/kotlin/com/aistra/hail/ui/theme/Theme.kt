package com.aistra.hail.ui.theme

import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.aistra.hail.app.HailData
import com.aistra.hail.utils.HTarget

// ── Pure-black / pure-white surface overrides ─────────────────────────────────
//
// FIX: The old overrides only changed *surface* colours but left the
// *on-surface* foreground colours untouched.  In pure-black mode the dark-
// theme foreground colours are already fine (they are light).  In pure-white
// mode the light-theme foreground colours are also fine (they are dark).
// The only extra work we need to do is guarantee the surface-variant text
// colour has enough contrast in both extremes, and ensure outline colours
// remain visible.  All other colours (primary, secondary, etc.) are
// intentionally kept from the underlying scheme so branded colours survive.
//
private fun ColorScheme.applyPureBlack() = copy(
    // Surfaces → true black with near-black variants for subtle elevation
    background              = Color(0xFF000000),
    surface                 = Color(0xFF000000),
    surfaceVariant          = Color(0xFF111111),
    surfaceContainer        = Color(0xFF0D0D0D),
    surfaceContainerHigh    = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1C1C1C),
    surfaceContainerLow     = Color(0xFF0A0A0A),
    surfaceContainerLowest  = Color(0xFF000000),
    // Foregrounds — must be light so content is readable on black
    onBackground            = Color(0xFFE8E8E8),
    onSurface               = Color(0xFFE8E8E8),
    onSurfaceVariant        = Color(0xFFB0B0B0),
    // Outlines stay visible against black
    outline                 = Color(0xFF606060),
    outlineVariant          = Color(0xFF303030),
    // Inverse stays reversed
    inverseSurface          = Color(0xFFE8E8E8),
    inverseOnSurface        = Color(0xFF1A1A1A),
)

private fun ColorScheme.applyPureWhite() = copy(
    // Surfaces → true white with off-white variants
    background              = Color(0xFFFFFFFF),
    surface                 = Color(0xFFFFFFFF),
    surfaceVariant          = Color(0xFFF0F0F0),
    surfaceContainer        = Color(0xFFF5F5F5),
    surfaceContainerHigh    = Color(0xFFEEEEEE),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    surfaceContainerLow     = Color(0xFFF9F9F9),
    surfaceContainerLowest  = Color(0xFFFFFFFF),
    // Foregrounds — must be dark so content is readable on white
    onBackground            = Color(0xFF111111),
    onSurface               = Color(0xFF111111),
    onSurfaceVariant        = Color(0xFF555555),
    // Outlines stay visible against white
    outline                 = Color(0xFF888888),
    outlineVariant          = Color(0xFFCCCCCC),
    // Inverse stays reversed
    inverseSurface          = Color(0xFF1A1A1A),
    inverseOnSurface        = Color(0xFFF5F5F5),
)
// ──────────────────────────────────────────────────────────────────────────────

private val lightScheme = lightColorScheme(
    primary = primaryLight, onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight, onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight, onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight, onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight, onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight, onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight, onError = onErrorLight,
    errorContainer = errorContainerLight, onErrorContainer = onErrorContainerLight,
    background = backgroundLight, onBackground = onBackgroundLight,
    surface = surfaceLight, onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight, onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight, outlineVariant = outlineVariantLight,
    scrim = scrimLight, inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight, inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight, surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark, onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark, onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark, onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark, onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark, onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark, onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark, onError = onErrorDark,
    errorContainer = errorContainerDark, onErrorContainer = onErrorContainerDark,
    background = backgroundDark, onBackground = onBackgroundDark,
    surface = surfaceDark, onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark, onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark, outlineVariant = outlineVariantDark,
    scrim = scrimDark, inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark, inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark, surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

/**
 * Produces a Compose [State]<T> backed by a SharedPreferences value, registering a
 * listener so the state — and any composable reading it — updates automatically
 * whenever the preference changes (e.g. when Everlasting writes the sync key).
 *
 * FIX: The previous version read SharedPrefs plain values (not Compose State),
 * so the AppTheme never recomposed when Everlasting changed the theme while
 * the Hail fragment was live. This helper makes those reads reactive.
 */
@Composable
private fun <T> SharedPreferences.observeAsState(key: String, default: T, get: SharedPreferences.(String, T) -> T): State<T> {
    val state = remember(key) { mutableStateOf(get(key, default)) }
    DisposableEffect(key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changedKey ->
            if (changedKey == key) state.value = prefs.get(key, default)
        }
        registerOnSharedPreferenceChangeListener(listener)
        onDispose { unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

/**
 * App Freeze (Hail) theme.
 *
 * Reads Everlasting's theme settings reactively via SharedPreferences listeners,
 * so the freeze UI recomposites immediately when the user changes the theme in
 * Everlasting Settings — no app restart needed.
 *
 * Theme-mode mapping (mirrors EverlastingTheme):
 *   -1 = not yet written (fall back to HailData.appTheme)
 *    0 = follow system
 *    1 = light
 *    2 = dark
 *    3 = pure black (dark + surface overrides)
 *    4 = pure white (light + surface overrides)
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val sp      = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }

    // FIX: Reactive SharedPrefs observation — recompositions fire on change.
    val evMode      by sp.observeAsState(HailData.EVERLASTING_THEME_MODE, -1)
        { k, d -> getInt(k, d) }
    val dynColor    by sp.observeAsState(HailData.EVERLASTING_DYNAMIC_COLOR, true)
        { k, d -> getBoolean(k, d) }
    val customPrimaryHex by sp.observeAsState(HailData.EVERLASTING_CUSTOM_PRIMARY, "")
        { k, d -> getString(k, d) ?: d }

    val systemDark = isSystemInDarkTheme()

    val darkTheme: Boolean = when (evMode) {
        1, 4 -> false                  // light / pure white
        2, 3 -> true                   // dark  / pure black
        0    -> systemDark             // follow system
        else -> when (HailData.appTheme) {   // legacy fallback (first launch)
            HailData.THEME_LIGHT -> false
            HailData.THEME_DARK  -> true
            else                 -> systemDark
        }
    }

    var colorScheme: ColorScheme = when {
        dynColor && HTarget.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkScheme
        else      -> lightScheme
    }

    // Pure-black / pure-white surface overrides to match EverlastingTheme exactly
    colorScheme = when (evMode) {
        3 -> colorScheme.applyPureBlack()
        4 -> colorScheme.applyPureWhite()
        else -> colorScheme
    }

    // Custom accent colour: applied when dynamic colour is off and the user has
    // picked a custom primary in Everlasting Settings → Appearance.
    // Parsing is guarded so a malformed string silently falls back to the scheme colour.
    //
    // ROOT CAUSE FIX: Everlasting stores custom colours as 6-digit hex (e.g. "2196F3").
    // Parsing a 6-digit hex directly via parseLong gives alpha=0x00 (fully transparent),
    // making the primary colour invisible on the screen.  We always prefix "FF" so the
    // alpha channel is opaque (0xFF) before parsing.
    if (!dynColor && customPrimaryHex.isNotEmpty()) {
        runCatching {
            val normalized = when (customPrimaryHex.length) {
                6    -> "FF$customPrimaryHex"   // 6-digit RGB → add opaque alpha
                8    -> customPrimaryHex          // already AARRGGBB
                else -> "FF${customPrimaryHex.takeLast(6).padStart(6, '0')}"
            }
            val argb = java.lang.Long.parseLong(normalized, 16).toInt()
            val primary = Color(argb)
            // Derive a readable on-primary by choosing black or white based on luminance
            val onPrimary = if (primary.luminance() > 0.4f) Color(0xFF000000) else Color(0xFFFFFFFF)
            colorScheme = colorScheme.copy(
                primary            = primary,
                onPrimary          = onPrimary,
                primaryContainer   = primary.copy(alpha = 0.20f),
                onPrimaryContainer = primary,
                inversePrimary     = primary,
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}
