package tk.zwander.common.compose

import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.preference.PreferenceManager
import com.aistra.hail.app.HailData
import tk.zwander.common.util.LSDisplayManager
import tk.zwander.common.util.lsDisplayManager
import com.coolappstore.everlastingandroidtweak.R

val LocalLSDisplayManager = compositionLocalOf<LSDisplayManager> { error("LSDisplayManager not provided!") }

// ── Reactive SharedPrefs helper (mirrors Hail Theme.kt) ──────────────────────
@Composable
private fun <T> SharedPreferences.observeAsState(
    key: String, default: T,
    get: SharedPreferences.(String, T) -> T
): State<T> {
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

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lsDisplayManager = remember { context.lsDisplayManager }

    // ── Read Everlasting theme prefs reactively ───────────────────────────────
    val sp = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val evMode     by sp.observeAsState(HailData.EVERLASTING_THEME_MODE, -1) { k, d -> getInt(k, d) }
    val dynColor   by sp.observeAsState(HailData.EVERLASTING_DYNAMIC_COLOR, true) { k, d -> getBoolean(k, d) }
    val customHex  by sp.observeAsState(HailData.EVERLASTING_CUSTOM_PRIMARY, "") { k, d -> getString(k, d) ?: d }

    val systemDark = isSystemInDarkTheme()

    // Map Everlasting mode → dark flag (same mapping as EverlastingTheme)
    // 0=auto/system  1=light  2=dark  3=pureBlack  4=pureWhite  -1=not set→system
    val isDark: Boolean = when (evMode) {
        1, 4 -> false
        2, 3 -> true
        0    -> systemDark
        else -> systemDark
    }

    var colorScheme = when {
        dynColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        isDark -> darkColorScheme(
            primary = colorResource(R.color.colorPrimary),
            primaryContainer = colorResource(R.color.colorPrimaryDark),
            secondary = colorResource(R.color.colorAccent),
        )
        else -> lightColorScheme(
            primary = colorResource(R.color.colorPrimary),
            primaryContainer = colorResource(R.color.colorPrimaryDark),
            secondary = colorResource(R.color.colorAccent),
        )
    }

    // Pure-black / pure-white overrides
    colorScheme = when (evMode) {
        3 -> colorScheme.copy(
            background = Color(0xFF000000), surface = Color(0xFF000000),
            surfaceVariant = Color(0xFF111111), surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerHigh = Color(0xFF0F0F0F), surfaceContainerHighest = Color(0xFF141414),
            surfaceContainerLow = Color(0xFF070707), surfaceContainerLowest = Color(0xFF000000),
            onBackground = Color(0xFFEEEEEE), onSurface = Color(0xFFEEEEEE),
            onSurfaceVariant = Color(0xFFAAAAAA),
        )
        4 -> colorScheme.copy(
            background = Color(0xFFFFFFFF), surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF0F0F0), surfaceContainer = Color(0xFFF5F5F5),
            onBackground = Color(0xFF111111), onSurface = Color(0xFF111111),
            onSurfaceVariant = Color(0xFF555555),
        )
        else -> colorScheme
    }

    // Custom primary colour
    if (!dynColor && customHex.isNotEmpty()) {
        runCatching {
            val argb = java.lang.Long.parseLong(customHex, 16).toInt()
            val primary = Color(argb)
            val onPrimary = if (primary.luminance() > 0.4f) Color.Black else Color.White
            colorScheme = colorScheme.copy(
                primary = primary, onPrimary = onPrimary,
                primaryContainer = primary.copy(alpha = 0.20f),
                onPrimaryContainer = primary,
            )
        }
    }

    CompositionLocalProvider(
        LocalLSDisplayManager provides lsDisplayManager,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
