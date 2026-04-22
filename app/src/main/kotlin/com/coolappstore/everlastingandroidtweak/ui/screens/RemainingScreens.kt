package com.coolappstore.everlastingandroidtweak.ui.screens

import android.app.Activity
import android.app.TimePickerDialog
import android.app.WallpaperManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.features.autoreboot.AutoRebootScheduler
import com.coolappstore.everlastingandroidtweak.features.cache.CacheCleanerHelper
import com.coolappstore.everlastingandroidtweak.features.navbar.NavBarOverlayService
import com.coolappstore.everlastingandroidtweak.ui.components.*
import com.coolappstore.everlastingandroidtweak.ui.navigation.Screen
import com.coolappstore.everlastingandroidtweak.utils.PermissionManager
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.animation.core.*
import kotlinx.coroutines.delay

// ─── COLOR PICKER ROW HELPER ─────────────────────────────────────────────────
// Upgraded: adds a "Full Picker" button that opens the full HSV EverlastingColorPickerDialog
@Composable
private fun ColorPickerRow(label: String, colorHex: String, onColorChange: (String) -> Unit) {
    val presets = listOf("#FFFFFF", "#000000", "#FF5722", "#2196F3", "#4CAF50", "#FFC107", "#9C27B0", "#00BCD4")
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        com.coolappstore.everlastingandroidtweak.ui.components.EverlastingColorPickerDialog(
            initialHex = colorHex.ifEmpty { "#2196F3" },
            onDismiss = { showPicker = false },
            onColorSelected = { onColorChange(it) }
        )
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Label row with full-picker button
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            androidx.compose.material3.FilledTonalButton(
                onClick = { showPicker = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                androidx.compose.material3.Icon(
                    Icons.Default.Colorize, null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Full Picker", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(8.dp))
        // Quick preset swatches
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { hex ->
                val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (colorHex.equals(hex, true)) 3.dp else 1.dp,
                            color = if (colorHex.equals(hex, true)) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable { onColorChange(hex) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Current color preview + hex display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val previewColor = try { Color(android.graphics.Color.parseColor(colorHex)) }
                               catch (_: Exception) { Color.Gray }
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            var customText by remember(colorHex) { mutableStateOf(colorHex) }
            OutlinedTextField(
                value = customText,
                onValueChange = { v ->
                    customText = v
                    if (v.matches(Regex("#[0-9A-Fa-f]{6}"))) onColorChange(v)
                },
                label = { Text("Hex (e.g. #FF5722)") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
    }
}

// ─── SCREENSAVER PRIVATE HELPERS ─────────────────────────────────────────────

@Composable
private fun SsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)) {
        Text(
            text  = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = MaterialTheme.shapes.extraLarge,
            colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SsSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(2.dp))
        Slider(value = value, onValueChange = onValueChange, valueRange = range, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SsChipRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options) { opt ->
                FilterChip(
                    selected = selected == opt,
                    onClick  = { onSelect(opt) },
                    label    = { Text(opt, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
    }
}

@Composable
private fun SsTextField(
    label: String,
    value: String,
    placeholder: String = "",
    onSave: (String) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value         = local,
        onValueChange = { local = it },
        label         = { Text(label) },
        placeholder   = { if (placeholder.isNotEmpty()) Text(placeholder) },
        modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        singleLine    = true,
        trailingIcon  = {
            if (local != value) {
                IconButton(onClick = { onSave(local) }) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        shape = MaterialTheme.shapes.large
    )
}

// Visual theme card
@Composable
private fun ThemeCard(
    name: String,
    badge: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val containerColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    Card(
        onClick    = onClick,
        modifier   = Modifier
            .width(130.dp)
            .border(width = if (isSelected) 2.dp else 0.dp, color = borderColor, shape = MaterialTheme.shapes.large),
        shape      = MaterialTheme.shapes.large,
        colors     = CardDefaults.cardColors(containerColor = containerColor),
        elevation  = CardDefaults.cardElevation(if (isSelected) 2.dp else 0.dp)
    ) {
        Column(
            Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color preview dot
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Text(badge, style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                ))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text  = name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


// ─── IN-APP SCREENSAVER PREVIEW ──────────────────────────────────────────────
@Composable
fun ScreensaverPreviewDialog(
    selectedThemeKey: String,
    onDismiss: () -> Unit
) {
    // Animate pulse for Moto preview
    val infiniteTransition = rememberInfiniteTransition(label = "preview_pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "phase"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() }
        ) {
            when (selectedThemeKey) {
                "Moto Screen Saver" -> MotoPreview(pulsePhase)
                "Windows Phone"     -> WPPreview()
                "Matrix Rain"       -> Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Text("Matrix Rain", color = Color(0xFF00FF41), modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.headlineMedium)
                }
                else -> Box(Modifier.fillMaxSize().background(Color.Black)) {
                    Text(selectedThemeKey, color = Color.White, modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.headlineMedium)
                }
            }
            // Tap to dismiss hint
            Text(
                "Tap anywhere to close",
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
            )
        }
    }
}

@Composable
private fun MotoPreview(pulsePhase: Float) {
    val context = LocalContext.current

    // ── Prefs ────────────────────────────────────────────────────────────────
    val glowColorHex  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_COLOR,        "#AACC0077").collectAsState("#AACC0077")
    val textColorHex  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_TEXT_COLOR,        "#FFFFFF").collectAsState("#FFFFFF")
    val arcColorHex   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_COLOR,         "#FFCC0077").collectAsState("#FFCC0077")
    val bgColorHex    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BG_COLOR,          "#000000").collectAsState("#000000")
    val brandingText  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BRANDING_TEXT,     "TurboPower").collectAsState("TurboPower")
    val showBranding  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SHOW_BRANDING,     true).collectAsState(true)
    val showArc       by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SHOW_ARC,          true).collectAsState(true)
    val glowSize      by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_SIZE,         1.0f).collectAsState(1.0f)
    val fontSz        by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_FONT_SIZE,         1.0f).collectAsState(1.0f)
    val glowOffsetY   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_OFFSET_Y,     0.42f).collectAsState(0.42f)
    val glowOffsetX   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_OFFSET_X,     0.50f).collectAsState(0.50f)
    val numOffsetX    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_OFFSET_X,      0.50f).collectAsState(0.50f)
    val numOffsetY    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_OFFSET_Y,      0.42f).collectAsState(0.42f)
    val arcOffsetX    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_OFFSET_X,      0.50f).collectAsState(0.50f)
    val arcOffsetYAdj by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_OFFSET_Y_ADJ,  0.0f).collectAsState(0.0f)
    val brandOffsetX  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BRANDING_OFFSET_X, 0.50f).collectAsState(0.50f)
    val brandOffsetY  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BRANDING_OFFSET_Y, 0.950f).collectAsState(0.950f)
    val numFontWeight by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_FONT_WEIGHT,   "Thin").collectAsState("Thin")
    val numLetterSpc  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_LETTER_SPC,    -0.02f).collectAsState(-0.02f)
    val arcRadiusMult by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_RADIUS_MULT,   0.18f).collectAsState(0.18f)
    val arcGapMult    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_GAP_MULT,      0.12f).collectAsState(0.12f)
    val boltOffsetY   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BOLT_OFFSET_Y,     0.0f).collectAsState(0.0f)
    val arcAngleStart by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_ANGLE_START,   195f).collectAsState(195f)
    val arcAngleSweep by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_ANGLE_SWEEP,   150f).collectAsState(150f)
    val arcStroke     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_STROKE_WIDTH,  3.5f).collectAsState(3.5f)
    val bgVignette    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BG_VIGNETTE,       true).collectAsState(true)
    val boltStyle     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BOLT_STYLE,        "Filled").collectAsState("Filled")
    val arcProgress   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_PROGRESS,      true).collectAsState(true)
    val glowLayers    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_LAYERS,       3).collectAsState(3)
    val glowIntensity by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_INTENSITY,    1.0f).collectAsState(1.0f)
    val pctSuffix     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_PCT_SUFFIX_STYLE,  "%").collectAsState("%")
    val suffixMult    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SUFFIX_SIZE_MULT,  0.40f).collectAsState(0.40f)
    val numOpacity    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_COLOR_OPACITY, 1.0f).collectAsState(1.0f)
    val useRealPct    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_USE_REAL_PCT,      true).collectAsState(true)
    val customPct     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_CUSTOM_PCT,        77).collectAsState(77)

    // ── Resolved values ──────────────────────────────────────────────────────
    val glowColor = remember(glowColorHex) { try { android.graphics.Color.parseColor(glowColorHex.replace(" ","")) } catch (_: Exception) { android.graphics.Color.argb(170, 204, 0, 119) } }
    val textColor = remember(textColorHex) { try { android.graphics.Color.parseColor(textColorHex) } catch (_: Exception) { android.graphics.Color.WHITE } }
    val arcColor  = remember(arcColorHex)  { try { android.graphics.Color.parseColor(arcColorHex.replace(" ",""))  } catch (_: Exception) { glowColor } }
    val bgColor   = remember(bgColorHex)   { try { android.graphics.Color.parseColor(bgColorHex)   } catch (_: Exception) { android.graphics.Color.BLACK } }
    val displaySuffix = when (pctSuffix) { "Percent" -> " percent"; "None" -> ""; else -> "%" }
    val battMgr = remember { context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager }
    val realPct = remember { battMgr?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0,100) ?: 77 }
    val displayPct = if (useRealPct) realPct else customPct.coerceIn(0, 100)
    val numTypeface = remember(numFontWeight) {
        when (numFontWeight) {
            "Thin"  -> try { android.graphics.Typeface.create("sans-serif-thin",  android.graphics.Typeface.NORMAL) } catch (_: Exception) { android.graphics.Typeface.DEFAULT }
            "Light" -> try { android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL) } catch (_: Exception) { android.graphics.Typeface.DEFAULT }
            "Bold"  -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            else    -> android.graphics.Typeface.DEFAULT
        }
    }
    val density = context.resources.displayMetrics.density

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w  = size.width
        val h  = size.height

        val glowCX  = w * glowOffsetX.coerceIn(0f, 1f)
        val glowCY  = h * glowOffsetY.coerceIn(0.10f, 0.90f)
        val numCX   = w * numOffsetX.coerceIn(0f, 1f)
        val numCY   = h * numOffsetY.coerceIn(0.10f, 0.90f)
        val arcCX   = w * arcOffsetX.coerceIn(0f, 1f)
        val brandCX = w * brandOffsetX.coerceIn(0f, 1f)
        val brandY  = h * brandOffsetY.coerceIn(0.40f, 0.99f)

        val gr = android.graphics.Color.red(glowColor)
        val gg = android.graphics.Color.green(glowColor)
        val gb = android.graphics.Color.blue(glowColor)
        val tr = android.graphics.Color.red(textColor)
        val tg = android.graphics.Color.green(textColor)
        val tb = android.graphics.Color.blue(textColor)
        val ar = android.graphics.Color.red(arcColor)
        val ag2= android.graphics.Color.green(arcColor)
        val ab2= android.graphics.Color.blue(arcColor)

        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            c.drawColor(bgColor)

            // ── Layered radial glow ───────────────────────────────────────
            val pulse   = kotlin.math.sin(pulsePhase.toDouble()).toFloat() * 0.06f
            val baseR   = minOf(w, h) * 0.40f * glowSize.coerceIn(0.3f, 2.5f)
            val glowR   = baseR * (1f + pulse)
            val baseA   = (glowIntensity.coerceIn(0.2f, 2.0f) * 160f).toInt().coerceIn(15, 240)
            val gPaint  = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

            for (layer in 0 until glowLayers.coerceIn(1, 5)) {
                val la = (baseA - layer * 28).coerceAtLeast(8)
                val lr = glowR * (1f + layer * 0.28f)
                gPaint.shader = android.graphics.RadialGradient(glowCX, glowCY, lr,
                    intArrayOf(
                        android.graphics.Color.argb(la,           gr, gg, gb),
                        android.graphics.Color.argb(la * 6 / 10,  gr, gg, gb),
                        android.graphics.Color.argb(la * 2 / 10,  gr, gg, gb),
                        android.graphics.Color.TRANSPARENT
                    ),
                    floatArrayOf(0f, 0.30f, 0.65f, 1f),
                    android.graphics.Shader.TileMode.CLAMP)
                c.drawCircle(glowCX, glowCY, lr, gPaint)
            }

            // ── Edge vignette ─────────────────────────────────────────────
            if (bgVignette) {
                gPaint.shader = android.graphics.RadialGradient(w / 2f, h / 2f, maxOf(w, h) * 0.85f,
                    intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb(170, 0, 0, 0)),
                    null, android.graphics.Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, gPaint)
                gPaint.shader = null
            }

            // ── Percentage number ─────────────────────────────────────────
            val pctFontSz = w * 0.26f * fontSz.coerceIn(0.3f, 2.5f)
            val sufFontSz = pctFontSz * suffixMult.coerceIn(0.15f, 1.0f)
            val numAlpha  = (numOpacity.coerceIn(0f, 1f) * 255f).toInt()
            val numCol    = android.graphics.Color.argb(numAlpha, tr, tg, tb)

            val numPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize      = pctFontSz
                color         = numCol
                textAlign     = android.graphics.Paint.Align.LEFT
                typeface      = numTypeface
                letterSpacing = numLetterSpc.coerceIn(-0.10f, 0.30f)
            }
            val sufPaint = android.graphics.Paint(numPaint).apply {
                textSize      = sufFontSz
                textAlign     = android.graphics.Paint.Align.LEFT
                letterSpacing = 0f
            }

            val numStr     = "$displayPct"
            val numW       = numPaint.measureText(numStr)
            val sufW       = if (displaySuffix.isNotEmpty()) sufPaint.measureText(displaySuffix) else 0f
            val totalTextW = numW + sufW
            val baseline   = numCY + pctFontSz * 0.35f
            val numLeft    = numCX - totalTextW / 2f

            c.drawText(numStr, numLeft, baseline, numPaint)
            if (displaySuffix.isNotEmpty()) {
                c.drawText(displaySuffix, numLeft + numW, baseline - pctFontSz * 0.32f, sufPaint)
            }

            // ── Thin arc + bolt ───────────────────────────────────────────
            if (showArc) {
                val arcR      = w * arcRadiusMult.coerceIn(0.06f, 0.50f) * fontSz.coerceIn(0.3f, 2.5f)
                val strokeW   = arcStroke.coerceIn(1f, 16f) * density
                val arcCenterY = baseline + pctFontSz * arcGapMult.coerceIn(0.02f, 1.5f) + arcR +
                                 h * arcOffsetYAdj.coerceIn(-0.30f, 0.30f)
                val arcRect = android.graphics.RectF(arcCX - arcR, arcCenterY - arcR, arcCX + arcR, arcCenterY + arcR)

                // Ghost track
                val tPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.style  = android.graphics.Paint.Style.STROKE
                    strokeWidth = strokeW
                    strokeCap   = android.graphics.Paint.Cap.ROUND
                    color       = android.graphics.Color.argb(35, ar, ag2, ab2)
                }
                c.drawArc(arcRect, arcAngleStart, arcAngleSweep, false, tPaint)

                // Glowing sweep
                val sweep   = if (arcProgress) (arcAngleSweep * displayPct / 100f).coerceAtLeast(3f) else arcAngleSweep
                val aPaint  = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.style  = android.graphics.Paint.Style.STROKE
                    strokeWidth = strokeW
                    strokeCap   = android.graphics.Paint.Cap.ROUND
                    shader      = android.graphics.SweepGradient(
                        arcCX, arcCenterY,
                        intArrayOf(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.argb(200, ar, ag2, ab2),
                            android.graphics.Color.argb(220, ar, ag2, ab2),
                            android.graphics.Color.argb(200, ar, ag2, ab2),
                            android.graphics.Color.TRANSPARENT
                        ),
                        floatArrayOf(0.25f, 0.40f, 0.50f, 0.60f, 0.75f)
                    )
                }
                c.drawArc(arcRect, arcAngleStart, sweep, false, aPaint)

                // Bolt at nadir
                if (boltStyle != "None") {
                    val bCX  = arcCX
                    val bCY  = arcCenterY + arcR + boltOffsetY.coerceIn(-1f, 1f) * arcR * 0.5f
                    val bs   = pctFontSz * 0.22f * fontSz.coerceIn(0.3f, 2.5f)
                    val bPath = android.graphics.Path().apply {
                        moveTo(bCX + bs * 0.35f, bCY - bs)
                        lineTo(bCX - bs * 0.10f, bCY - bs * 0.05f)
                        lineTo(bCX + bs * 0.12f, bCY - bs * 0.05f)
                        lineTo(bCX - bs * 0.35f, bCY + bs)
                        lineTo(bCX + bs * 0.10f, bCY + bs * 0.05f)
                        lineTo(bCX - bs * 0.12f, bCY + bs * 0.05f)
                        close()
                    }
                    val bPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.style  = if (boltStyle == "Outline") android.graphics.Paint.Style.STROKE else android.graphics.Paint.Style.FILL
                        strokeWidth = strokeW * 0.5f
                        color       = numCol
                    }
                    c.drawPath(bPath, bPaint)
                }
            }

            // ── Bottom large arc — only top sliver visible ────────────────
            if (showBranding) {
                val bArcR  = w * 0.58f
                val bArcCY = h + bArcR * 0.28f
                val bArcPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.style  = android.graphics.Paint.Style.STROKE
                    strokeWidth = arcStroke.coerceIn(1f, 16f) * density
                    strokeCap   = android.graphics.Paint.Cap.ROUND
                    shader      = android.graphics.SweepGradient(
                        brandCX, bArcCY,
                        intArrayOf(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.argb(120, ar, ag2, ab2),
                            android.graphics.Color.argb(190, ar, ag2, ab2),
                            android.graphics.Color.argb(120, ar, ag2, ab2),
                            android.graphics.Color.TRANSPARENT
                        ),
                        floatArrayOf(0.30f, 0.43f, 0.50f, 0.57f, 0.70f)
                    )
                }
                c.drawArc(
                    android.graphics.RectF(brandCX - bArcR, bArcCY - bArcR, brandCX + bArcR, bArcCY + bArcR),
                    205f, 130f, false, bArcPaint
                )
            }

            // ── TurboPower branding ───────────────────────────────────────
            if (showBranding) {
                val bFontSz = w * 0.040f * fontSz.coerceIn(0.3f, 2.5f)
                val bCapY   = brandY - bFontSz * 0.62f
                val bPaint  = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textSize      = bFontSz
                    textAlign     = android.graphics.Paint.Align.LEFT
                    typeface      = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    color         = android.graphics.Color.argb(210, tr, tg, tb)
                    letterSpacing = 0.04f
                }
                val bW      = bPaint.measureText(brandingText)
                val bIconSz = bFontSz * 0.38f
                val bIconX  = brandCX - bW / 2f - bIconSz * 1.6f

                val bIconPath = android.graphics.Path().apply {
                    moveTo(bIconX + bIconSz * 0.32f, bCapY - bIconSz)
                    lineTo(bIconX - bIconSz * 0.10f, bCapY - bIconSz * 0.05f)
                    lineTo(bIconX + bIconSz * 0.12f, bCapY - bIconSz * 0.05f)
                    lineTo(bIconX - bIconSz * 0.32f, bCapY + bIconSz)
                    lineTo(bIconX + bIconSz * 0.10f, bCapY + bIconSz * 0.05f)
                    lineTo(bIconX - bIconSz * 0.12f, bCapY + bIconSz * 0.05f)
                    close()
                }
                val bIconPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = android.graphics.Paint.Style.FILL
                    color      = android.graphics.Color.argb(210, tr, tg, tb)
                }
                c.drawPath(bIconPath, bIconPaint)
                c.drawText(brandingText, brandCX - bW / 2f + bIconSz * 0.40f, brandY, bPaint)
            }
        }
    }
}
@Composable
private fun WPPreview() {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Windows Phone\nScreensaver Preview\ncoming in full app preview",
            color = Color.White.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// ─── SCREENSAVER ─────────────────────────────────────────────────────────────
@Composable
fun ScreensaverScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // Theme list — renamed legacy + 2 new specialty
    data class ThemeEntry(val key: String, val label: String, val badge: String, val accent: Color)
    val themes = listOf(
        ThemeEntry("Matrix Rain",       "Matrix Rain",       "01", Color(0xFF66BB6A)),
        ThemeEntry("Floating Orbs",     "Floating Orbs",     "●",  Color(0xFFFF8A65)),
        ThemeEntry("Deep Space",        "Deep Space",        "★",  Color(0xFF5C6BC0)),
        ThemeEntry("Ocean Wave",        "Ocean Wave",        "~",  Color(0xFF29B6F6)),
        ThemeEntry("Moto Screen Saver", "Moto Screen Saver", "⚡", Color(0xFFE91E63)),
        ThemeEntry("Windows Phone",     "Windows Phone",     "WP", Color(0xFF78909C))
    )

    // ── Global prefs ─────────────────────────────────────────────────────────
    val savedTheme       by AppPreferences.get(AppPreferences.SCREENSAVER_THEME, "Moto Screen Saver").collectAsState("Moto Screen Saver")
    val savedColor       by AppPreferences.get(AppPreferences.SCREENSAVER_COLOR, "#FFFFFF").collectAsState("#FFFFFF")
    val savedSize        by AppPreferences.get(AppPreferences.SCREENSAVER_SIZE, 1.0f).collectAsState(1.0f)
    val savedClockStyle  by AppPreferences.get(AppPreferences.SCREENSAVER_CLOCK_STYLE, "Digital").collectAsState("Digital")
    val savedClockColor  by AppPreferences.get(AppPreferences.SCREENSAVER_CLOCK_COLOR, "#FFFFFF").collectAsState("#FFFFFF")
    val savedShowBattery by AppPreferences.get(AppPreferences.SCREENSAVER_SHOW_BATTERY, false).collectAsState(false)
    val savedShowDate    by AppPreferences.get(AppPreferences.SCREENSAVER_SHOW_DATE, true).collectAsState(true)
    val savedBurnIn      by AppPreferences.get(AppPreferences.SCREENSAVER_BURN_IN_ENABLED, true).collectAsState(true)
    val savedBurnInSec   by AppPreferences.get(AppPreferences.SCREENSAVER_BURN_IN_INTERVAL, 30).collectAsState(30)
    val savedMoveEnabled by AppPreferences.get(AppPreferences.SCREENSAVER_MOVE_ENABLED, true).collectAsState(true)
    val savedMoveSpeed   by AppPreferences.get(AppPreferences.SCREENSAVER_MOVE_SPEED, 3).collectAsState(3)
    val savedFadeDur     by AppPreferences.get(AppPreferences.SCREENSAVER_FADE_DURATION, 0).collectAsState(0)

    // ── Moto prefs ───────────────────────────────────────────────────────────
    val motoGlowColor    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_COLOR,         "#CC0077AA").collectAsState("#CC0077AA")
    val motoTextColor    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_TEXT_COLOR,         "#FFFFFF").collectAsState("#FFFFFF")
    val motoArcColor     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_COLOR,          "#CC0077AA").collectAsState("#CC0077AA")
    val motoBrandingText by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BRANDING_TEXT,      "TurboPower").collectAsState("TurboPower")
    val motoShowBranding by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SHOW_BRANDING,      true).collectAsState(true)
    val motoShowArc      by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SHOW_ARC,           true).collectAsState(true)
    val motoGlowSize     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_SIZE,          1.0f).collectAsState(1.0f)
    val motoPulseSpeed   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_PULSE_SPEED,        1.0f).collectAsState(1.0f)
    val motoBgColor      by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BG_COLOR,           "#000000").collectAsState("#000000")
    val motoFontSize     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_FONT_SIZE,          1.0f).collectAsState(1.0f)
    val motoUseRealPct   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_USE_REAL_PCT,       true).collectAsState(true)
    val motoCustomPct    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_CUSTOM_PCT,         75).collectAsState(75)
    val motoArcProgress  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_PROGRESS,       true).collectAsState(true)
    val motoGlowLayers   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_LAYERS,        3).collectAsState(3)
    // New granular Moto prefs
    val motoNumFontWeight  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_FONT_WEIGHT,  "Thin").collectAsState("Thin")
    val motoNumLetterSpc   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_LETTER_SPC,   -0.02f).collectAsState(-0.02f)
    val motoArcGapMult     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_GAP_MULT,     0.55f).collectAsState(0.55f)
    val motoArcRadiusMult  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_RADIUS_MULT,  0.36f).collectAsState(0.36f)
    val motoBoltSizeMult   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BOLT_SIZE_MULT,   0.46f).collectAsState(0.46f)
    val motoBoltOffsetY    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BOLT_OFFSET_Y,    0.30f).collectAsState(0.30f)
    val motoBrandingOffset by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BRANDING_OFFSET_Y,0.91f).collectAsState(0.91f)
    val motoNumOpacity     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_COLOR_OPACITY,1.0f).collectAsState(1.0f)
    val motoArcAngleStart  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_ANGLE_START,  25f).collectAsState(25f)
    val motoArcAngleSweep  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_ANGLE_SWEEP,  130f).collectAsState(130f)
    val motoSuffixSizeMult by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SUFFIX_SIZE_MULT, 0.55f).collectAsState(0.55f)
    val motoGlowIntensity by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_INTENSITY,    1.0f).collectAsState(1.0f)
    val motoGlowShape    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_SHAPE,         "Circle").collectAsState("Circle")
    val motoSecondaryGlow by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SECONDARY_GLOW,   false).collectAsState(false)
    val motoSecondaryColor by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SECONDARY_COLOR, "#CC AA0066").collectAsState("#CC AA0066")
    val motoBgVignette   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BG_VIGNETTE,        true).collectAsState(true)
    val motoBoltStyle    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BOLT_STYLE,         "Filled").collectAsState("Filled")
    val motoPctSuffix    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_PCT_SUFFIX_STYLE,   "%").collectAsState("%")
    val motoShowChargTxt by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_SHOW_CHARGING_TEXT, false).collectAsState(false)
    val motoChargingText by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_CHARGING_TEXT,      "Charging").collectAsState("Charging")
    val motoArcStroke    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_STROKE_WIDTH,   4.0f).collectAsState(4.0f)
    val motoAnimStyle    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ANIMATION_STYLE,    "Pulse").collectAsState("Pulse")
    val motoGlowOffsetY    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_OFFSET_Y,        0.44f).collectAsState(0.44f)
    val motoGlowOffsetX    by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_GLOW_OFFSET_X,        0.50f).collectAsState(0.50f)
    val motoNumOffsetX     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_OFFSET_X,         0.50f).collectAsState(0.50f)
    val motoNumOffsetY     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_NUM_OFFSET_Y,         0.40f).collectAsState(0.40f)
    val motoArcOffsetX     by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_OFFSET_X,         0.50f).collectAsState(0.50f)
    val motoArcOffsetYAdj  by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_ARC_OFFSET_Y_ADJ,     0.0f).collectAsState(0.0f)
    val motoBrandOffsetX   by AppPreferences.get(AppPreferences.SCREENSAVER_MOTO_BRANDING_OFFSET_X,    0.50f).collectAsState(0.50f)

    // ── WP prefs ─────────────────────────────────────────────────────────────
    val wpTextColor      by AppPreferences.get(AppPreferences.SCREENSAVER_WP_TEXT_COLOR,      "#FFFFFF").collectAsState("#FFFFFF")
    val wpIs24h          by AppPreferences.get(AppPreferences.SCREENSAVER_WP_IS_24H,          false).collectAsState(false)
    val wpShowDate       by AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_DATE,       true).collectAsState(true)
    val wpLayout         by AppPreferences.get(AppPreferences.SCREENSAVER_WP_LAYOUT,          "Minimal").collectAsState("Minimal")
    val wpShowWeather    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_WEATHER,    false).collectAsState(false)
    val wpCity           by AppPreferences.get(AppPreferences.SCREENSAVER_WP_CITY,            "My City").collectAsState("My City")
    val wpCondition      by AppPreferences.get(AppPreferences.SCREENSAVER_WP_CONDITION,       "Sunny").collectAsState("Sunny")
    val wpTemperature    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_TEMPERATURE,     "72°F").collectAsState("72°F")
    val wpTempHigh       by AppPreferences.get(AppPreferences.SCREENSAVER_WP_TEMP_HIGH,       "78°").collectAsState("78°")
    val wpTempLow        by AppPreferences.get(AppPreferences.SCREENSAVER_WP_TEMP_LOW,        "61°").collectAsState("61°")
    val wpDay2Name       by AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY2_NAME,       "FRI").collectAsState("FRI")
    val wpDay2High       by AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY2_HIGH,       "75°").collectAsState("75°")
    val wpDay2Low        by AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY2_LOW,        "60°").collectAsState("60°")
    val wpDay3Name       by AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY3_NAME,       "SAT").collectAsState("SAT")
    val wpDay3High       by AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY3_HIGH,       "73°").collectAsState("73°")
    val wpDay3Low        by AppPreferences.get(AppPreferences.SCREENSAVER_WP_DAY3_LOW,        "59°").collectAsState("59°")
    val wpShowEvents     by AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_EVENTS,     false).collectAsState(false)
    val wpEventTitle     by AppPreferences.get(AppPreferences.SCREENSAVER_WP_EVENT_TITLE,     "Design workshop").collectAsState("Design workshop")
    val wpEventLocation  by AppPreferences.get(AppPreferences.SCREENSAVER_WP_EVENT_LOCATION,  "Studio A").collectAsState("Studio A")
    val wpEventTime      by AppPreferences.get(AppPreferences.SCREENSAVER_WP_EVENT_TIME,      "All day").collectAsState("All day")
    val wpShowNotif      by AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_NOTIF,      false).collectAsState(false)
    val wpShowBattery    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_BATTERY,    false).collectAsState(false)
    val wpPhoneCount     by AppPreferences.get(AppPreferences.SCREENSAVER_WP_PHONE_COUNT,     0).collectAsState(0)
    val wpEmailCount     by AppPreferences.get(AppPreferences.SCREENSAVER_WP_EMAIL_COUNT,     0).collectAsState(0)
    val wpFontSize       by AppPreferences.get(AppPreferences.SCREENSAVER_WP_FONT_SIZE,       1.0f).collectAsState(1.0f)
    val wpShowAlarmIcon  by AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_ALARM_ICON, true).collectAsState(true)
    val wpBgColor        by AppPreferences.get(AppPreferences.SCREENSAVER_WP_BG_COLOR,        "#000000").collectAsState("#000000")
    val wpLetterSpacing  by AppPreferences.get(AppPreferences.SCREENSAVER_WP_LETTER_SPACING,  0.0f).collectAsState(0.0f)
    val wpShowSeconds    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_SECONDS,    false).collectAsState(false)
    val wpTimeColor      by AppPreferences.get(AppPreferences.SCREENSAVER_WP_TIME_COLOR,      "").collectAsState("")
    val wpDateOpacity    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_DATE_OPACITY,    0.73f).collectAsState(0.73f)
    val wpClockPosition  by AppPreferences.get(AppPreferences.SCREENSAVER_WP_CLOCK_POSITION,  "Left").collectAsState("Left")
    val wpShowSeparator  by AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_SEPARATOR,  true).collectAsState(true)
    val wpEvent2Title    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_EVENT2_TITLE,    "").collectAsState("")
    val wpEvent2Time     by AppPreferences.get(AppPreferences.SCREENSAVER_WP_EVENT2_TIME,     "").collectAsState("")
    val wpCompactMode    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_COMPACT_MODE,    false).collectAsState(false)
    val wpAccentColor    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_ACCENT_COLOR,    "#4090FF").collectAsState("#4090FF")
    val wpShowWeekNum    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_SHOW_WEEK_NUMBER,false).collectAsState(false)
    val wpNotifStyle     by AppPreferences.get(AppPreferences.SCREENSAVER_WP_NOTIF_STYLE,     "Numbers").collectAsState("Numbers")
    val wpClockSize      by AppPreferences.get(AppPreferences.SCREENSAVER_WP_CLOCK_SIZE,          1.0f).collectAsState(1.0f)
    val wpClockVertPos   by AppPreferences.get(AppPreferences.SCREENSAVER_WP_CLOCK_VERTICAL_POS,  0.65f).collectAsState(0.65f)
    val wpClockSizeSp    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_CLOCK_SIZE_SP,       0f).collectAsState(0f)
    val wpDateSizeSp     by AppPreferences.get(AppPreferences.SCREENSAVER_WP_DATE_SIZE_SP,        0f).collectAsState(0f)
    val wpWeatherSizeSp  by AppPreferences.get(AppPreferences.SCREENSAVER_WP_WEATHER_SIZE_SP,     0f).collectAsState(0f)
    val wpNotifSizeSp    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_NOTIF_SIZE_SP,       0f).collectAsState(0f)
    val wpPaddingLeft    by AppPreferences.get(AppPreferences.SCREENSAVER_WP_PADDING_LEFT,        0.072f).collectAsState(0.072f)

    var selectedThemeKey by remember { mutableStateOf("Moto Screen Saver") }
    var size      by remember { mutableFloatStateOf(1.0f) }
    var burnInSec by remember { mutableIntStateOf(30) }
    LaunchedEffect(savedTheme)     { selectedThemeKey = savedTheme }
    LaunchedEffect(savedSize)      { size             = savedSize }
    LaunchedEffect(savedBurnInSec) { burnInSec        = savedBurnInSec }

    val isMotoTheme  = selectedThemeKey == "Moto Screen Saver"
    val isWPTheme    = selectedThemeKey == "Windows Phone"
    val isClockTheme = false  // Clock themes removed

    val clockStyles     = listOf("Digital", "Bold", "Minimal", "Retro")
    val burnInIntervals = listOf(15 to "15 s", 30 to "30 s", 60 to "1 min", 120 to "2 min", 300 to "5 min")

    Scaffold(topBar = { EverlastingTopBar("Screensaver", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {

            Spacer(Modifier.height(4.dp))

            // ── Theme Picker ─────────────────────────────────────────────────
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Style",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
                )
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding        = PaddingValues(end = 4.dp)
                ) {
                    items(themes) { entry ->
                        ThemeCard(
                            name       = entry.label,
                            badge      = entry.badge,
                            accentColor = entry.accent,
                            isSelected = selectedThemeKey == entry.key,
                            onClick    = {
                                selectedThemeKey = entry.key
                                scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_THEME, entry.key) }
                            }
                        )
                    }
                }
            }

            // ── Fade-in (all themes) ─────────────────────────────────────────
            SsSection("Fade In") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Fade Duration", style = MaterialTheme.typography.bodyMedium)
                        Text(if (savedFadeDur == 0) "Off" else "${savedFadeDur}ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(2.dp))
                    Slider(value = savedFadeDur.toFloat(), onValueChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_FADE_DURATION, it.toInt()) } },
                        valueRange = 0f..3000f, modifier = Modifier.fillMaxWidth())
                }
            }

            // ── Clock-based themes ───────────────────────────────────────────
            if (isClockTheme) {
                SsSection("Clock Style") {
                    SsChipRow("Style", clockStyles, savedClockStyle) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_CLOCK_STYLE, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ColorPickerRow("Clock Color", savedClockColor) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_CLOCK_COLOR, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ToggleSettingRow("Show Date", "Display date line below time", savedShowDate,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_SHOW_DATE, it) } })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ToggleSettingRow("Show Battery %", "Show device battery level", savedShowBattery,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_SHOW_BATTERY, it) } })
                }
            }

            // ── Non-specialty themes: color + size + protection ──────────────
            if (!isMotoTheme && !isWPTheme) {
                SsSection("Color & Size") {
                    ColorPickerRow("Accent Color", savedColor) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_COLOR, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Content Scale", size, 0.5f..3.0f, "${String.format("%.1f", size)}×") {
                        size = it; scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_SIZE, it) }
                    }
                }
                SsSection("Movement & Burn-in") {
                    ToggleSettingRow("Burn-in Protection", "Periodically shift content to protect OLED panels", savedBurnIn,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_BURN_IN_ENABLED, it) } })
                    if (savedBurnIn) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text("Shift Interval", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(burnInIntervals) { (secs, label) ->
                                    FilterChip(
                                        selected = burnInSec == secs,
                                        onClick  = { burnInSec = secs; scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_BURN_IN_INTERVAL, secs) } },
                                        label    = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ToggleSettingRow("Floating Animation", "Content drifts slowly across the screen", savedMoveEnabled,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOVE_ENABLED, it) } })
                    if (savedMoveEnabled) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Drift Speed", savedMoveSpeed.toFloat(), 1f..10f, "$savedMoveSpeed") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOVE_SPEED, it.toInt()) }
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════════
            // ── MOTO SCREEN SAVER SETTINGS ───────────────────────────────────
            // ════════════════════════════════════════════════════════════════
            if (isMotoTheme) {

                SsSection("Animation") {
                    SsChipRow("Style", listOf("Pulse", "Breathe", "Ripple", "Static"), motoAnimStyle) {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_ANIMATION_STYLE, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Pulse Speed", motoPulseSpeed, 0f..3f, "${String.format("%.1f", motoPulseSpeed)}×") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_PULSE_SPEED, it) }
                    }
                }

                SsSection("Glow") {
                    ColorPickerRow("Glow Color", motoGlowColor) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_GLOW_COLOR, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsChipRow("Glow Shape", listOf("Circle", "Oval", "Wide Oval"), motoGlowShape) {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_GLOW_SHAPE, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Glow Size", motoGlowSize, 0.4f..2.0f, "${String.format("%.2f", motoGlowSize)}×") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_GLOW_SIZE, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Glow Intensity", motoGlowIntensity, 0.2f..2.0f, "${String.format("%.2f", motoGlowIntensity)}×") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_GLOW_INTENSITY, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Glow Layers", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..5).forEach { n ->
                                FilterChip(selected = motoGlowLayers == n,
                                    onClick = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_GLOW_LAYERS, n) } },
                                    label   = { Text("$n", style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Glow X Position", motoGlowOffsetX, 0.0f..1.0f, "${(motoGlowOffsetX * 100).toInt()}% from left") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_GLOW_OFFSET_X, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Glow Y Position", motoGlowOffsetY, 0.10f..0.90f, "${(motoGlowOffsetY * 100).toInt()}% from top") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_GLOW_OFFSET_Y, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ToggleSettingRow("Secondary Glow", "Add a second offset glow bloom for depth", motoSecondaryGlow,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_SECONDARY_GLOW, it) } })
                    if (motoSecondaryGlow) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ColorPickerRow("Secondary Glow Color", motoSecondaryColor.replace(" ","")) {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_SECONDARY_COLOR, it) }
                        }
                    }
                }

                SsSection("Background") {
                    ColorPickerRow("Background Color", motoBgColor) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_BG_COLOR, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ToggleSettingRow("Edge Vignette", "Dark radial vignette around the glow for cinematic look", motoBgVignette,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_BG_VIGNETTE, it) } })
                }

                SsSection("Number & Battery") {
                    ToggleSettingRow("Use Real Battery Level", "Read actual device battery %", motoUseRealPct,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_USE_REAL_PCT, it) } })
                    if (!motoUseRealPct) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Preview Percentage", motoCustomPct.toFloat(), 1f..100f, "$motoCustomPct%") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_CUSTOM_PCT, it.toInt()) }
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsChipRow("Number Font Weight", listOf("Thin", "Light", "Regular", "Bold"), motoNumFontWeight) {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_NUM_FONT_WEIGHT, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ColorPickerRow("Number Color", motoTextColor) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_TEXT_COLOR, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Number Size", motoFontSize, 0.4f..2.5f, "${String.format("%.2f", motoFontSize)}x") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_FONT_SIZE, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Number Opacity", motoNumOpacity, 0.1f..1.0f, "${(motoNumOpacity * 100).toInt()}%") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_NUM_COLOR_OPACITY, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Number X Position", motoNumOffsetX, 0.0f..1.0f, "${(motoNumOffsetX * 100).toInt()}% from left") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_NUM_OFFSET_X, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Number Y Position", motoNumOffsetY, 0.10f..0.90f, "${(motoNumOffsetY * 100).toInt()}% from top") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_NUM_OFFSET_Y, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Letter Spacing", motoNumLetterSpc, -0.08f..0.20f, String.format("%.2f", motoNumLetterSpc)) {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_NUM_LETTER_SPC, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsChipRow("Percentage Suffix", listOf("%", "Percent", "None"), motoPctSuffix) {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_PCT_SUFFIX_STYLE, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Suffix Size", motoSuffixSizeMult, 0.2f..1.0f, "${(motoSuffixSizeMult * 100).toInt()}% of number") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_SUFFIX_SIZE_MULT, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ToggleSettingRow("Show Charging Label", "Display a custom text above the percentage", motoShowChargTxt,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_SHOW_CHARGING_TEXT, it) } })
                    if (motoShowChargTxt) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsTextField("Label Text", motoChargingText, "Charging") { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_CHARGING_TEXT, it) } }
                        Spacer(Modifier.height(4.dp))
                    }
                }
                SsSection("Arc & Bolt") {
                    ToggleSettingRow("Show Arc & Bolt", "Curved progress arc below the percentage with a lightning bolt", motoShowArc,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_SHOW_ARC, it) } })
                    if (motoShowArc) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ColorPickerRow("Arc Color", motoArcColor) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_ARC_COLOR, it) } }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ToggleSettingRow("Arc Reflects Charge Level", "Arc length equals the battery percentage", motoArcProgress,
                            onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_ARC_PROGRESS, it) } })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Arc Stroke Width", motoArcStroke, 1f..16f, "${String.format("%.1f", motoArcStroke)} dp") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_ARC_STROKE_WIDTH, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Arc Radius", motoArcRadiusMult, 0.12f..0.56f, "${(motoArcRadiusMult * 100).toInt()}% of width") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_ARC_RADIUS_MULT, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Gap: Number to Arc", motoArcGapMult, 0.0f..1.2f, String.format("%.2f", motoArcGapMult)) {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_ARC_GAP_MULT, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Arc Start Angle", motoArcAngleStart, 150f..250f, "${motoArcAngleStart.toInt()}°") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_ARC_ANGLE_START, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Arc Sweep Angle", motoArcAngleSweep, 30f..280f, "${motoArcAngleSweep.toInt()}°") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_ARC_ANGLE_SWEEP, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsChipRow("Bolt Style", listOf("Filled", "Outline", "None"), motoBoltStyle) {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_BOLT_STYLE, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Bolt Size", motoBoltSizeMult, 0.15f..0.80f, "${(motoBoltSizeMult * 100).toInt()}% of number") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_BOLT_SIZE_MULT, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Bolt Vertical Offset", motoBoltOffsetY, -1.0f..1.0f, String.format("%.2f", motoBoltOffsetY)) {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_BOLT_OFFSET_Y, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Arc X Position", motoArcOffsetX, 0.0f..1.0f, "${(motoArcOffsetX * 100).toInt()}% from left") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_ARC_OFFSET_X, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Arc Y Adjustment", motoArcOffsetYAdj, -0.30f..0.30f, String.format("%+.2f", motoArcOffsetYAdj)) {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_ARC_OFFSET_Y_ADJ, it) }
                        }
                    }
                }

                SsSection("Branding") {
                    ToggleSettingRow("Show Branding Text", "Brand name shown at the bottom of the screen", motoShowBranding,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_SHOW_BRANDING, it) } })
                    if (motoShowBranding) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsTextField("Branding Text", motoBrandingText, "TurboPower") { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_BRANDING_TEXT, it) } }
                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Branding X Position", motoBrandOffsetX, 0.0f..1.0f, "${(motoBrandOffsetX * 100).toInt()}% from left") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_BRANDING_OFFSET_X, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Branding Y Position", motoBrandingOffset, 0.40f..0.99f, "${(motoBrandingOffset * 100).toInt()}% from top") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_MOTO_BRANDING_OFFSET_Y, it) }
                        }
                    }
                }
            }

            // ════════════════════════════════════════════════════════════════
            // ── WINDOWS PHONE SETTINGS ────────────────────────────────────────
            // ════════════════════════════════════════════════════════════════
            if (isWPTheme) {

                SsSection("Layout & Style") {
                    SsChipRow("Layout", listOf("Minimal", "Full"), wpLayout) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_LAYOUT, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsChipRow("Clock Alignment", listOf("Left", "Center"), wpClockPosition) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_CLOCK_POSITION, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Clock Vertical Position", wpClockVertPos, 0.35f..0.90f, "${(wpClockVertPos * 100).toInt()}% down screen") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_CLOCK_VERTICAL_POS, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Left Margin", wpPaddingLeft, 0.0f..0.20f, "${(wpPaddingLeft * 100).toInt()}% of screen width") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_PADDING_LEFT, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ToggleSettingRow("Compact Spacing", "Tighter line spacing between elements", wpCompactMode,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_COMPACT_MODE, it) } })
                }

                SsSection("Colors & Font") {
                    ColorPickerRow("Global Text Color", wpTextColor) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_TEXT_COLOR, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ColorPickerRow("Clock Digits Color", wpTimeColor.ifEmpty { "#FFFFFF" }) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_TIME_COLOR, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ColorPickerRow("Background Color", wpBgColor) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_BG_COLOR, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ColorPickerRow("Accent Color", wpAccentColor) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_ACCENT_COLOR, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Font: Segoe WP (Authentic Windows Phone)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Global Font Scale", wpFontSize, 0.4f..3.0f, "${String.format("%.2f", wpFontSize)}x") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_FONT_SIZE, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Letter Spacing", wpLetterSpacing, -0.05f..0.20f, "${String.format("%.2f", wpLetterSpacing)}") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_LETTER_SPACING, it) }
                    }
                }

                SsSection("Text Sizes — 0 = auto") {
                    SsSliderRow(
                        "Clock Size (sp)",
                        wpClockSizeSp, 0f..200f,
                        if (wpClockSizeSp <= 0f) "Auto" else "${wpClockSizeSp.toInt()} sp"
                    ) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_CLOCK_SIZE_SP, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow("Clock Multiplier", wpClockSize, 0.4f..3.0f, "${String.format("%.1f", wpClockSize)}x") {
                        scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_CLOCK_SIZE, it) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow(
                        "Date Size (sp)",
                        wpDateSizeSp, 0f..60f,
                        if (wpDateSizeSp <= 0f) "Auto" else "${wpDateSizeSp.toInt()} sp"
                    ) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_DATE_SIZE_SP, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow(
                        "Weather Text Size (sp)",
                        wpWeatherSizeSp, 0f..50f,
                        if (wpWeatherSizeSp <= 0f) "Auto" else "${wpWeatherSizeSp.toInt()} sp"
                    ) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_WEATHER_SIZE_SP, it) } }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    SsSliderRow(
                        "Notification Size (sp)",
                        wpNotifSizeSp, 0f..40f,
                        if (wpNotifSizeSp <= 0f) "Auto" else "${wpNotifSizeSp.toInt()} sp"
                    ) { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_NOTIF_SIZE_SP, it) } }
                }

                SsSection("Clock") {
                    ToggleSettingRow("24-Hour Time", "Use 24-hour format instead of 12-hour AM/PM", wpIs24h,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_IS_24H, it) } })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ToggleSettingRow("Show Seconds", "Display HH:MM:SS", wpShowSeconds,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_SHOW_SECONDS, it) } })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ToggleSettingRow("Show Alarm Icon", "Superscript alarm indicator next to the time", wpShowAlarmIcon,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_SHOW_ALARM_ICON, it) } })
                }

                SsSection("Date") {
                    ToggleSettingRow("Show Date", "Display day of week and calendar date", wpShowDate,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_SHOW_DATE, it) } })
                    if (wpShowDate) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Date Opacity", wpDateOpacity, 0.2f..1.0f, "${(wpDateOpacity * 100).toInt()}%") {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_DATE_OPACITY, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ToggleSettingRow("Show Week Number", "Append 'W42' week number to the date line", wpShowWeekNum,
                            onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_SHOW_WEEK_NUMBER, it) } })
                    }
                }

                SsSection("Weather") {
                    ToggleSettingRow("Show Weather Block", "Requires Full layout — appears above the clock", wpShowWeather,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_SHOW_WEATHER, it) } })
                    if (wpShowWeather) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ToggleSettingRow("Show Divider Line", "Horizontal separator between weather and clock", wpShowSeparator,
                            onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_SHOW_SEPARATOR, it) } })
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Column(Modifier.padding(top = 6.dp, bottom = 2.dp)) {
                            SsTextField("City Name",          wpCity,        "Bellevue")    { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_CITY, it) } }
                            SsTextField("Condition",          wpCondition,   "Light rain")  { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_CONDITION, it) } }
                            SsTextField("Current Temperature",wpTemperature, "56°F")        { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_TEMPERATURE, it) } }
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                var h by remember(wpTempHigh) { mutableStateOf(wpTempHigh) }
                                OutlinedTextField(value = h, onValueChange = { h = it; scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_TEMP_HIGH, it) } },
                                    label = { Text("High") }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
                                var l by remember(wpTempLow) { mutableStateOf(wpTempLow) }
                                OutlinedTextField(value = l, onValueChange = { l = it; scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_TEMP_LOW, it) } },
                                    label = { Text("Low") }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
                            }
                            Text("Forecast Day 2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp))
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                var n by remember(wpDay2Name) { mutableStateOf(wpDay2Name) }
                                OutlinedTextField(value = n, onValueChange = { n = it; scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_DAY2_NAME, it) } }, label = { Text("Label") }, placeholder = { Text("FRI") }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
                                var h by remember(wpDay2High) { mutableStateOf(wpDay2High) }
                                OutlinedTextField(value = h, onValueChange = { h = it; scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_DAY2_HIGH, it) } }, label = { Text("High") }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
                                var l by remember(wpDay2Low) { mutableStateOf(wpDay2Low) }
                                OutlinedTextField(value = l, onValueChange = { l = it; scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_DAY2_LOW, it) } }, label = { Text("Low") }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
                            }
                            Text("Forecast Day 3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 2.dp))
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                var n by remember(wpDay3Name) { mutableStateOf(wpDay3Name) }
                                OutlinedTextField(value = n, onValueChange = { n = it; scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_DAY3_NAME, it) } }, label = { Text("Label") }, placeholder = { Text("SAT") }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
                                var h by remember(wpDay3High) { mutableStateOf(wpDay3High) }
                                OutlinedTextField(value = h, onValueChange = { h = it; scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_DAY3_HIGH, it) } }, label = { Text("High") }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
                                var l by remember(wpDay3Low) { mutableStateOf(wpDay3Low) }
                                OutlinedTextField(value = l, onValueChange = { l = it; scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_DAY3_LOW, it) } }, label = { Text("Low") }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }

                SsSection("Calendar Events") {
                    ToggleSettingRow("Show Events", "Display upcoming calendar entries below the clock", wpShowEvents,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_SHOW_EVENTS, it) } })
                    if (wpShowEvents) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text("Event 1", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                            SsTextField("Title",         wpEventTitle,    "Design workshop") { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_EVENT_TITLE, it) } }
                            SsTextField("Location",      wpEventLocation, "Studio A")        { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_EVENT_LOCATION, it) } }
                            SsTextField("Time / Duration", wpEventTime,  "All day")          { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_EVENT_TIME, it) } }
                            Text("Event 2", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
                            SsTextField("Title",           wpEvent2Title, "Team standup")     { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_EVENT2_TITLE, it) } }
                            SsTextField("Time / Duration", wpEvent2Time, "10:00 AM")          { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_EVENT2_TIME, it) } }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }

                SsSection("Notifications & Battery") {
                    ToggleSettingRow("Show Battery", "Drawn battery icon with charge level and percentage", wpShowBattery,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_SHOW_BATTERY, it) } })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ToggleSettingRow("Show Notification Counts", "Phone and email counts at the bottom", wpShowNotif,
                        onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_SHOW_NOTIF, it) } })
                    if (wpShowNotif) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsChipRow("Display Style", listOf("Numbers", "Icons", "Both"), wpNotifStyle) {
                            scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_NOTIF_STYLE, it) }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Missed Calls",  wpPhoneCount.toFloat(), 0f..99f, "$wpPhoneCount") { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_PHONE_COUNT, it.toInt()) } }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        SsSliderRow("Unread Emails", wpEmailCount.toFloat(), 0f..99f, "$wpEmailCount") { scope.launch { AppPreferences.set(AppPreferences.SCREENSAVER_WP_EMAIL_COUNT, it.toInt()) } }
                    }
                }
            }

            // ── Preview & Settings buttons ────────────────────────────────────
            var showPreview by remember { mutableStateOf(false) }
            if (showPreview) {
                ScreensaverPreviewDialog(
                    selectedThemeKey = selectedThemeKey,
                    onDismiss = { showPreview = false }
                )
            }
            Column(Modifier.padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 6.dp)) {
                Button(
                    onClick  = { showPreview = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Preview Screensaver", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick  = { context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("System Screensaver Settings")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── AUTO REBOOT ─────────────────────────────────────────────────────────────
@Composable
fun AutoRebootScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by AppPreferences.get(AppPreferences.AUTO_REBOOT_ENABLED, false).collectAsState(false)
    val savedTime by AppPreferences.get(AppPreferences.AUTO_REBOOT_TIME, "03:00").collectAsState("03:00")
    val savedDays by AppPreferences.get(AppPreferences.AUTO_REBOOT_DAYS, "").collectAsState("")
    var hour by remember { mutableIntStateOf(3) }
    var minute by remember { mutableIntStateOf(0) }
    LaunchedEffect(savedTime) {
        savedTime.split(":").let { parts ->
            hour = parts.getOrNull(0)?.toIntOrNull() ?: 3
            minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        }
    }
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val selectedDays = remember { mutableStateListOf(*Array(7) { false }) }
    LaunchedEffect(savedDays) {
        savedDays.split(",").mapNotNull { it.toIntOrNull() }.forEach { if (it in 0..6) selectedDays[it] = true }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var alarmPerm by remember { mutableStateOf(PermissionManager.hasExactAlarmPermission(context)) }
    var accessibilityEnabled by remember { mutableStateOf(PermissionManager.isAccessibilityEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) alarmPerm = PermissionManager.hasExactAlarmPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(topBar = { EverlastingTopBar("Auto Reboot", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            if (!alarmPerm) {
                InfoCard(
                    icon = Icons.Default.Warning,
                    title = "Exact Alarm Permission Required",
                    subtitle = "Needed for precise reboot scheduling",
                    isError = true,
                    actionLabel = "Grant",
                    onAction = { PermissionManager.openExactAlarmSettings(context) }
                )
            }
            if (!accessibilityEnabled) {
                InfoCard(
                    icon = Icons.Default.Accessibility,
                    title = "Accessibility Service Recommended",
                    subtitle = "Enables graceful reboot via accessibility action",
                    isError = false,
                    actionLabel = "Enable",
                    onAction = { PermissionManager.openAccessibilitySettings(context) }
                )
            }

            FeatureSection("Schedule") {
                ToggleSettingRow("Enable Auto Reboot", "Automatically reboot at scheduled time", enabled,
                    { scope.launch { AppPreferences.set(AppPreferences.AUTO_REBOOT_ENABLED, it); if (!it) AutoRebootScheduler.cancel(context) } })
                HorizontalDivider()
                // Android clock time picker
                ListItem(
                    headlineContent = { Text("Reboot Time") },
                    supportingContent = { Text("${hour.toString().padStart(2,'0')}:${minute.toString().padStart(2,'0')}") },
                    trailingContent = {
                        OutlinedButton(onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                hour = h; minute = m
                            }, hour, minute, true).show()
                        }) { Text("Set Time") }
                    }
                )
                HorizontalDivider()
                // Day chips
                Column(Modifier.padding(16.dp)) {
                    Text("Repeat Days", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        dayNames.forEachIndexed { i, d ->
                            FilterChip(
                                selected = selectedDays[i],
                                onClick = { selectedDays[i] = !selectedDays[i] },
                                label = { Text(d, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    val timeStr = "${hour.toString().padStart(2,'0')}:${minute.toString().padStart(2,'0')}"
                    val daysStr = selectedDays.mapIndexedNotNull { i, s -> if (s) i.toString() else null }.joinToString(",")
                    AppPreferences.set(AppPreferences.AUTO_REBOOT_TIME, timeStr)
                    AppPreferences.set(AppPreferences.AUTO_REBOOT_DAYS, daysStr)
                }
                AutoRebootScheduler.schedule(context, hour, minute, selectedDays.toList())
            }, modifier = Modifier.fillMaxWidth().padding(16.dp), enabled = enabled && alarmPerm) { Text("Save Schedule") }

            // ADB command — tap to copy to clipboard
            val adbCmd = "adb shell pm grant ${context.packageName} android.permission.REBOOT"
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("adb_cmd", adbCmd))
                        Toast.makeText(context, "ADB command copied!", Toast.LENGTH_SHORT).show()
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                        Text("Tap to copy ADB command", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(adbCmd, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
    }
}

// ─── CACHE CLEANER ───────────────────────────────────────────────────────────
@Composable
fun CacheCleanerScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var shizukuAvailable by remember { mutableStateOf(false) }
    var cleaning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf(listOf<android.content.pm.ApplicationInfo>()) }
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var loadingApps by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shizukuAvailable = CacheCleanerHelper.isShizukuReady()
                if (shizukuAvailable && apps.isEmpty()) {
                    loadingApps = true
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        apps = context.packageManager.getInstalledApplications(0)
                            .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                            .sortedBy { context.packageManager.getApplicationLabel(it).toString() }
                        loadingApps = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val displayedApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            context.packageManager.getApplicationLabel(it).toString()
                .contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(topBar = { EverlastingTopBar("Cache Cleaner", navController) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Shizuku status
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (shizukuAvailable)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(if (shizukuAvailable) Icons.Default.CheckCircle else Icons.Default.Warning, null)
                    Column {
                        Text(if (shizukuAvailable) "Shizuku Active" else "Shizuku Required",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (shizukuAvailable) "Ready to clear caches"
                            else "Run: adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        cleaning = true; result = ""
                        val targets = if (selectedApps.isNotEmpty()) selectedApps.toList()
                                      else null
                        scope.launch {
                            try {
                                val cleared = if (targets != null)
                                    CacheCleanerHelper.clearCacheForPackages(context, targets)
                                else
                                    CacheCleanerHelper.clearAllCache(context)
                                result = if (cleared > 0) "✓ Cleared caches for $cleared apps"
                                         else "⚠ No caches cleared"
                                selectedApps = emptySet()
                                Toast.makeText(context,
                                    if (cleared > 0) "✓ Cleared $cleared app caches" else "⚠ Failed — Shizuku needed",
                                    Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                result = "Error: ${e.message}"
                            } finally { cleaning = false }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !cleaning && shizukuAvailable
                ) {
                    if (cleaning) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                    Icon(Icons.Default.CleaningServices, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        when {
                            cleaning -> "Clearing…"
                            selectedApps.isNotEmpty() -> "Clear (${selectedApps.size})"
                            else -> "Clear All"
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Get Shizuku", style = MaterialTheme.typography.labelMedium) }
            }

            if (result.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.startsWith("✓"))
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = MaterialTheme.shapes.large
                ) { Text(result, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium) }
            }

            if (shizukuAvailable && apps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                // Search
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true, shape = MaterialTheme.shapes.extraLarge
                )
                Spacer(Modifier.height(4.dp))

                // Select all row
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedApps.size == displayedApps.size && displayedApps.isNotEmpty(),
                        onCheckedChange = {
                            selectedApps = if (it) displayedApps.map { a -> a.packageName }.toSet()
                                           else emptySet()
                        }
                    )
                    Text("Select All (${displayedApps.size} apps) • ${selectedApps.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Per-app list with checkboxes
                FeatureSection("Apps (${displayedApps.size})") {
                    if (loadingApps) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        displayedApps.forEachIndexed { i, app ->
                            val name = context.packageManager.getApplicationLabel(app).toString()
                            val isChecked = selectedApps.contains(app.packageName)
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        selectedApps = if (isChecked)
                                            selectedApps - app.packageName
                                        else selectedApps + app.packageName
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        selectedApps = if (it) selectedApps + app.packageName
                                                       else selectedApps - app.packageName
                                    }
                                )
                                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (i < displayedApps.lastIndex)
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            } else if (shizukuAvailable && loadingApps) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── MUSIC LIGHT ─────────────────────────────────────────────────────────────
@Composable
fun MusicLightScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val lightEnabled   by AppPreferences.get(AppPreferences.MUSIC_LIGHT_ENABLED, false).collectAsState(false)
    val vibrateEnabled by AppPreferences.get(AppPreferences.MUSIC_VIBRATE_ENABLED, false).collectAsState(false)
    val savedSensitivity by AppPreferences.get(AppPreferences.MUSIC_LIGHT_SENSITIVITY, 0.35f).collectAsState(0.35f)
    val savedSpeed       by AppPreferences.get(AppPreferences.MUSIC_SPEED_SENSITIVITY, 1.0f).collectAsState(1.0f)
    val savedBlinkDur    by AppPreferences.get(AppPreferences.MUSIC_BLINK_DURATION_MS, 80).collectAsState(80)
    var sensitivity by remember { mutableFloatStateOf(0.35f) }
    var speed       by remember { mutableFloatStateOf(1.0f) }
    var blinkDur    by remember { mutableIntStateOf(80) }
    LaunchedEffect(savedSensitivity) { sensitivity = savedSensitivity }
    LaunchedEffect(savedSpeed)       { speed       = savedSpeed }
    LaunchedEffect(savedBlinkDur)    { blinkDur    = savedBlinkDur }

    var hasAudioPerm by remember { mutableStateOf(PermissionManager.hasRecordAudio(context)) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAudioPerm = PermissionManager.hasRecordAudio(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var showPermDialog by remember { mutableStateOf(false) }
    if (showPermDialog) {
        PermissionRequiredDialog(
            "Microphone Access Required",
            "Music Reactive Light uses the microphone to detect audio. Tap Grant to allow.",
            isSpecial = false,
            runtimePermissions = listOf(android.Manifest.permission.RECORD_AUDIO),
            onDismiss = { showPermDialog = false },
            onGranted  = { hasAudioPerm = true }
        )
    }

    Scaffold(topBar = { EverlastingTopBar("Music Reactive Light", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🎵 Glyph-style Reactive Light", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Flashlight and/or vibration pulses in sync with music.", style = MaterialTheme.typography.bodySmall)
                }
            }
            FeatureSection("Controls") {
                ToggleSettingRow("Flash with Music", "Pulse torch to the music beat", lightEnabled, {
                    if (!hasAudioPerm) { showPermDialog = true; return@ToggleSettingRow }
                    scope.launch { AppPreferences.set(AppPreferences.MUSIC_LIGHT_ENABLED, it) }
                })
                HorizontalDivider()
                ToggleSettingRow("Vibrate with Music", "Vibration pulses match the music rhythm", vibrateEnabled,
                    { scope.launch { AppPreferences.set(AppPreferences.MUSIC_VIBRATE_ENABLED, it) } })
            }
            FeatureSection("Sensitivity — Reacts to Sound") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val pct = ((1f - sensitivity) * 100).toInt()
                    Text(
                        "Level: $pct% — ${if (pct > 70) "Very Reactive" else if (pct > 40) "Balanced" else "Only Loud Beats"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = 1f - sensitivity,
                        onValueChange = {
                            sensitivity = 1f - it
                            scope.launch { AppPreferences.set(AppPreferences.MUSIC_LIGHT_SENSITIVITY, 1f - it) }
                        },
                        valueRange = 0f..0.9f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Low = reacts to quiet audio   |   High = only reacts to loud beats",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FeatureSection("Speed — Flash/Vibrate Rate") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val speedLabel = when {
                        speed < 0.5f -> "Very Slow"
                        speed < 0.9f -> "Slow"
                        speed < 1.3f -> "Normal"
                        speed < 1.7f -> "Fast"
                        else         -> "Very Fast"
                    }
                    Text("Speed: ${speedLabel} (${String.format("%.1f", speed)}×)",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = speed,
                        onValueChange = {
                            speed = it
                            scope.launch { AppPreferences.set(AppPreferences.MUSIC_SPEED_SENSITIVITY, it) }
                        },
                        valueRange = 0.2f..2.0f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Low = slow blink   |   High = rapid flash matching beat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FeatureSection("Blink Duration — How Long Each Flash Lasts") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    val durLabel = when {
                        blinkDur < 40  -> "Very Short (${blinkDur}ms)"
                        blinkDur < 80  -> "Short (${blinkDur}ms)"
                        blinkDur < 140 -> "Medium (${blinkDur}ms)"
                        blinkDur < 220 -> "Long (${blinkDur}ms)"
                        else           -> "Very Long (${blinkDur}ms)"
                    }
                    Text("Duration: $durLabel", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = blinkDur.toFloat(),
                        onValueChange = {
                            blinkDur = it.toInt()
                            scope.launch { AppPreferences.set(AppPreferences.MUSIC_BLINK_DURATION_MS, it.toInt()) }
                        },
                        valueRange = 20f..300f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Short = sharp strobe   |   Long = sustained glow per beat",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─── HAPTICS ─────────────────────────────────────────────────────────────────
@Composable
fun HapticsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled       by AppPreferences.get(AppPreferences.CUSTOM_HAPTICS_ENABLED, false).collectAsState(false)
    val scrollEnabled by AppPreferences.get(AppPreferences.HAPTICS_SCROLL_ENABLED, false).collectAsState(false)
    val intensity     by AppPreferences.get(AppPreferences.HAPTICS_INTENSITY, 100).collectAsState(100)
    val savedPattern  by AppPreferences.get(AppPreferences.HAPTICS_PATTERN, "Click").collectAsState("Click")
    val patterns = listOf("Click", "Tick", "Heavy Click", "Double Click", "Soft", "Custom")
    var selectedPattern by remember { mutableStateOf(0) }
    LaunchedEffect(savedPattern) { selectedPattern = patterns.indexOf(savedPattern).takeIf { it >= 0 } ?: 0 }
    var accessibilityEnabled by remember { mutableStateOf(PermissionManager.isAccessibilityEnabled(context)) }
    var showAccessDialog by remember { mutableStateOf(false) }
    if (showAccessDialog) {
        PermissionRequiredDialog("Accessibility Service Required",
            "Custom Haptics needs the Accessibility Service. Open Settings → Accessibility → Everlasting Tweak and enable it.",
            isSpecial = true, onOpenSettings = { PermissionManager.openAccessibilitySettings(context) }, onDismiss = { showAccessDialog = false })
    }

    val lifecycleOwnerHapticsScreen = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwnerHapticsScreen) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = PermissionManager.isAccessibilityEnabled(context)
            }
        }
        lifecycleOwnerHapticsScreen.lifecycle.addObserver(obs)
        onDispose { lifecycleOwnerHapticsScreen.lifecycle.removeObserver(obs) }
    }
    Scaffold(topBar = { EverlastingTopBar("Custom Haptics", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            if (!accessibilityEnabled) {
                InfoCard(Icons.Default.Warning, "Accessibility Service not enabled", "Required for custom haptics",
                    isError = true, actionLabel = "Enable", onAction = { showAccessDialog = true })
            }
            FeatureSection("Haptic Feedback") {
                ToggleSettingRow("Custom Tap Haptics", "Haptic feedback on every tap", enabled, {
                    if (!accessibilityEnabled) { showAccessDialog = true; return@ToggleSettingRow }
                    scope.launch { AppPreferences.set(AppPreferences.CUSTOM_HAPTICS_ENABLED, it) }
                })
                HorizontalDivider()
                ToggleSettingRow("Haptics While Scrolling", "Gentle ticks while scrolling", scrollEnabled,
                    { scope.launch { AppPreferences.set(AppPreferences.HAPTICS_SCROLL_ENABLED, it) } })
                HorizontalDivider()
                Column(Modifier.padding(16.dp)) {
                    Text("Intensity: $intensity%")
                    Slider(value = intensity.toFloat(), onValueChange = { scope.launch { AppPreferences.set(AppPreferences.HAPTICS_INTENSITY, it.toInt()) } },
                        valueRange = 10f..200f, modifier = Modifier.fillMaxWidth())
                }
            }
            FeatureSection("Pattern") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    patterns.forEachIndexed { i, p ->
                        FilterChip(selected = selectedPattern == i, onClick = {
                            selectedPattern = i
                            scope.launch { AppPreferences.set(AppPreferences.HAPTICS_PATTERN, p) }
                        }, label = { Text(p, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = {
                    val vibrator = (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(when (patterns.getOrNull(selectedPattern)) {
                            "Tick"         -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK)
                            "Heavy Click"  -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK)
                            "Double Click" -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK)
                            else           -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK)
                        })
                    }
                }, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Test Haptic") }
            }
        }
    }
}

// ─── CUSTOM SOUNDS ───────────────────────────────────────────────────────────
@Composable
fun CustomSoundsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tapEnabled    by AppPreferences.get(AppPreferences.TAP_SOUND_ENABLED, false).collectAsState(false)
    val lockEnabled   by AppPreferences.get(AppPreferences.LOCK_SOUND_ENABLED, false).collectAsState(false)
    val unlockEnabled by AppPreferences.get(AppPreferences.UNLOCK_SOUND_ENABLED, false).collectAsState(false)
    val chargeEnabled by AppPreferences.get(AppPreferences.CHARGING_SOUND_ENABLED, false).collectAsState(false)
    val tapUri    by AppPreferences.get(AppPreferences.TAP_SOUND_URI, "").collectAsState("")
    val lockUri   by AppPreferences.get(AppPreferences.LOCK_SOUND_URI, "").collectAsState("")
    val unlockUri by AppPreferences.get(AppPreferences.UNLOCK_SOUND_URI, "").collectAsState("")
    val chargeUri by AppPreferences.get(AppPreferences.CHARGING_SOUND_URI, "").collectAsState("")
    var pickingFor by remember { mutableStateOf("") }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.toString()?.let { uriStr ->
            scope.launch {
                when (pickingFor) {
                    "tap"    -> AppPreferences.set(AppPreferences.TAP_SOUND_URI, uriStr)
                    "lock"   -> AppPreferences.set(AppPreferences.LOCK_SOUND_URI, uriStr)
                    "unlock" -> AppPreferences.set(AppPreferences.UNLOCK_SOUND_URI, uriStr)
                    "charge" -> AppPreferences.set(AppPreferences.CHARGING_SOUND_URI, uriStr)
                }
            }
        }
    }

    Scaffold(topBar = { EverlastingTopBar("Custom Sounds", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp)) {
                    Text("🔊 Custom Sounds", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Assign custom audio files to tap, lock, unlock and charging events.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }

            // Helper composable for sound rows
            @Composable
            fun SoundCard(
                sectionTitle: String,
                toggleLabel: String,
                toggleSub: String,
                enabled: Boolean,
                onToggle: (Boolean) -> Unit,
                fileUri: String,
                onBrowse: () -> Unit,
                iconEmoji: String
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(iconEmoji + "  " + sectionTitle, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp, top = 8.dp))
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(0.dp)) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(toggleLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(toggleSub, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = enabled, onCheckedChange = onToggle)
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Sound File", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (fileUri.isEmpty()) "No file selected — tap Browse"
                                        else android.net.Uri.parse(fileUri).lastPathSegment ?: "File selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (fileUri.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledTonalButton(onClick = onBrowse) {
                                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Browse")
                                }
                            }
                        }
                    }
                }
            }

            // ROOT CAUSE: Tap sound requires Accessibility service (TYPE_VIEW_CLICKED events).
            // Without it, there is no way to detect taps globally. Show a warning if disabled.
            val accessibilityOk by remember {
                mutableStateOf(PermissionManager.isAccessibilityEnabled(context))
            }
            if (!accessibilityOk) {
                InfoCard(Icons.Default.Accessibility, "Accessibility Required for Tap Sound",
                    "Tap sounds use the Accessibility Service to detect screen touches. Enable it to use this feature.",
                    isError = true, actionLabel = "Enable",
                    onAction = { PermissionManager.openAccessibilitySettings(context) })
            }
            SoundCard("Tap Sound", "Custom Tap Sound", "Play sound on every screen tap",
                tapEnabled && accessibilityOk,
                { if (accessibilityOk) scope.launch { AppPreferences.set(AppPreferences.TAP_SOUND_ENABLED, it) } },
                tapUri, { pickingFor = "tap"; audioPicker.launch("audio/*") }, "👆")

            SoundCard("Lock Sound", "Screen Lock Sound", "Sound when screen locks",
                lockEnabled, { scope.launch { AppPreferences.set(AppPreferences.LOCK_SOUND_ENABLED, it) } },
                lockUri, { pickingFor = "lock"; audioPicker.launch("audio/*") }, "🔒")

            SoundCard("Unlock Sound", "Unlock Sound", "After PIN/biometric unlock",
                unlockEnabled, { scope.launch { AppPreferences.set(AppPreferences.UNLOCK_SOUND_ENABLED, it) } },
                unlockUri, { pickingFor = "unlock"; audioPicker.launch("audio/*") }, "🔓")

            SoundCard("Charging Sound", "Charging Connect Sound", "Plays when charger is plugged in",
                chargeEnabled, { scope.launch { AppPreferences.set(AppPreferences.CHARGING_SOUND_ENABLED, it) } },
                chargeUri, { pickingFor = "charge"; audioPicker.launch("audio/*") }, "⚡")

            Spacer(Modifier.height(8.dp))
            InfoCard(Icons.Default.Info, "How it works",
                "Tap/unlock sounds use the Accessibility Service. Lock/charge sounds use broadcast receivers. Unlock fires only after successful biometric or PIN auth.",
                isError = false)
        }
    }
}

// ─── NAVBAR OVERLAY ──────────────────────────────────────────────────────────
@Composable
fun NavBarOverlayScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val enabled        by AppPreferences.get(AppPreferences.NAVBAR_OVERLAY_ENABLED, false).collectAsState(false)
    val savedStyle     by AppPreferences.get(AppPreferences.NAVBAR_STYLE, "Minimal Pill").collectAsState("Minimal Pill")
    val savedPillColor by AppPreferences.get(AppPreferences.NAVBAR_PILL_COLOR, "#FFFFFF").collectAsState("#FFFFFF")
    val savedOpacity   by AppPreferences.get(AppPreferences.NAVBAR_PILL_OPACITY, 0.8f).collectAsState(0.8f)
    val savedHeight    by AppPreferences.get(AppPreferences.NAVBAR_HEIGHT, 48f).collectAsState(48f)
    val savedXPos      by AppPreferences.get(AppPreferences.NAVBAR_X_POSITION, 0f).collectAsState(0f)
    val savedYPos      by AppPreferences.get(AppPreferences.NAVBAR_Y_POSITION, 0f).collectAsState(0f)

    // Styles — Solid Pill added for fully-opaque no-transparency pill
    val styles = listOf(
        "Solid Pill"          to "Fully opaque pill — always visible",
        "Minimal Pill"        to "Small semi-transparent gesture pill",
        "Blurred Pill"        to "Frosted blur effect behind the pill",
        "Colored"             to "Solid colored bar across full width",
        "Classic Buttons"     to "Back • Home • Recents buttons",
        "Gradient"            to "Fades out at the edges",
        "Neon Glow"           to "Glowing line effect",
        "Dot Indicators"      to "Three dots navigation style"
    )
    var selectedStyle by remember { mutableStateOf(0) }
    LaunchedEffect(savedStyle) { selectedStyle = styles.indexOfFirst { it.first == savedStyle }.takeIf { it >= 0 } ?: 0 }
    var opacity by remember { mutableFloatStateOf(0.8f) }
    LaunchedEffect(savedOpacity) { opacity = savedOpacity }
    var height  by remember { mutableFloatStateOf(48f) }
    LaunchedEffect(savedHeight) { height = savedHeight }
    var xPos    by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(savedXPos)  { xPos = savedXPos }
    var yPos    by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(savedYPos)  { yPos = savedYPos }
    var hasOverlay by remember { mutableStateOf(PermissionManager.hasOverlayPermission(context)) }
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME)
                hasOverlay = PermissionManager.hasOverlayPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs); onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(topBar = { EverlastingTopBar("Custom Nav Bar", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {

            if (!hasOverlay) {
                InfoCard(Icons.Default.Layers, "Overlay Permission Required",
                    "Custom Nav Bar needs the Display Over Other Apps permission.",
                    isError = true, actionLabel = "Grant",
                    onAction = { PermissionManager.openOverlaySettings(context) })
            }

            val useAccessibility by AppPreferences.get(AppPreferences.NAVBAR_USE_ACCESSIBILITY, false).collectAsState(false)
            val accessibilityActive by remember { mutableStateOf(PermissionManager.isAccessibilityEnabled(context)) }

            // Enable toggle
            FeatureSection("Nav Bar Overlay") {
                ToggleSettingRow("Enable Nav Bar Overlay", "Draw a custom navigation bar overlay", enabled, {
                    scope.launch { AppPreferences.set(AppPreferences.NAVBAR_OVERLAY_ENABLED, it) }
                    if (it) NavBarOverlayService.start(context) else NavBarOverlayService.stop(context)
                })
                HorizontalDivider()
                ToggleSettingRow(
                    "Use Accessibility Mode",
                    "No overlay permission needed — uses Accessibility Service instead",
                    useAccessibility,
                    {
                        scope.launch { AppPreferences.set(AppPreferences.NAVBAR_USE_ACCESSIBILITY, it) }
                        if (it && !accessibilityActive) PermissionManager.openAccessibilitySettings(context)
                    }
                )
                if (useAccessibility && !accessibilityActive) {
                    InfoCard(Icons.Default.Accessibility, "Accessibility Required",
                        "Enable the Accessibility Service to use this mode.",
                        isError = true, actionLabel = "Enable",
                        onAction = { PermissionManager.openAccessibilitySettings(context) })
                }
            }

            // Style picker
            FeatureSection("Style") {
                styles.forEachIndexed { i, (style, desc) ->
                    ListItem(
                        headlineContent = { Text(style) },
                        supportingContent = { Text(desc, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = {
                            RadioButton(selected = selectedStyle == i, onClick = {
                                selectedStyle = i
                                scope.launch { AppPreferences.set(AppPreferences.NAVBAR_STYLE, style) }
                                if (enabled) NavBarOverlayService.start(context)
                            })
                        }
                    )
                    if (i < styles.lastIndex) HorizontalDivider()
                }
            }

            // Color picker
            FeatureSection("Pill / Bar Color") {
                ColorPickerRow("Color", savedPillColor) {
                    scope.launch { AppPreferences.set(AppPreferences.NAVBAR_PILL_COLOR, it) }
                    if (enabled) NavBarOverlayService.start(context)
                }
            }

            // Size & opacity sliders — debounce via update()
            FeatureSection("Size & Opacity") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = opacity, onValueChange = {
                        opacity = it; scope.launch { AppPreferences.set(AppPreferences.NAVBAR_PILL_OPACITY, it) }
                        if (enabled) NavBarOverlayService.update(context)
                    }, valueRange = 0.1f..1f, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text("Height: ${height.toInt()}dp", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = height, onValueChange = {
                        height = it; scope.launch { AppPreferences.set(AppPreferences.NAVBAR_HEIGHT, it) }
                        if (enabled) NavBarOverlayService.update(context)
                    }, valueRange = 24f..120f, modifier = Modifier.fillMaxWidth())
                }
            }

            // X / Y position sliders — use update() which debounces internally
            FeatureSection("Position") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("X Offset: ${xPos.toInt()}px  (← left  |  right →)",
                        style = MaterialTheme.typography.bodyMedium)
                    Slider(value = xPos, onValueChange = {
                        xPos = it; scope.launch { AppPreferences.set(AppPreferences.NAVBAR_X_POSITION, it) }
                        if (enabled) NavBarOverlayService.update(context)
                    }, valueRange = -500f..500f, modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.height(4.dp))
                    Text("Y Offset: ${yPos.toInt()}px  (↑ up  |  down ↓)",
                        style = MaterialTheme.typography.bodyMedium)
                    Slider(value = yPos, onValueChange = {
                        yPos = it; scope.launch { AppPreferences.set(AppPreferences.NAVBAR_Y_POSITION, it) }
                        if (enabled) NavBarOverlayService.update(context)
                    }, valueRange = -300f..300f, modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        xPos = 0f; yPos = 0f
                        scope.launch {
                            AppPreferences.set(AppPreferences.NAVBAR_X_POSITION, 0f)
                            AppPreferences.set(AppPreferences.NAVBAR_Y_POSITION, 0f)
                        }
                        if (enabled) NavBarOverlayService.update(context)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CenterFocusStrong, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reset to Center")
                    }
                }
            }

            InfoCard(Icons.Default.Info, "System Navigation Tip",
                "If the system gesture pill overlaps your custom pill, use the 'Solid Pill' style and set Y Offset to positive to push it above the system nav area.",
                isError = false)
        }
    }
}

// ─── SCREENSHOT BLOCKER ──────────────────────────────────────────────────────
@Composable
fun ScreenshotBlockerScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val enabled by AppPreferences.get(AppPreferences.SCREENSHOT_BLOCK_ENABLED, false).collectAsState(false)
    var hasOverlay by remember { mutableStateOf(PermissionManager.hasOverlayPermission(context)) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasOverlay = PermissionManager.hasOverlayPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs); onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Apply FLAG_SECURE globally via the activity — covers the entire app
    LaunchedEffect(enabled) {
        val activity = context as? Activity
        if (activity != null) {
            if (enabled) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    Scaffold(topBar = { EverlastingTopBar("Screenshot Blocker", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            FeatureSection("Screenshot Protection") {
                ToggleSettingRow(
                    "Block Screenshots in This App",
                    "Prevents screenshots & screen recording system-wide via FLAG_SECURE",
                    enabled,
                    { scope.launch { AppPreferences.set(AppPreferences.SCREENSHOT_BLOCK_ENABLED, it) } }
                )
            }
            FeatureSection("How It Works") {
                Column(Modifier.padding(16.dp)) {
                    Text("FLAG_SECURE is applied to the activity window when enabled. This blocks screenshots in all currently-visible activities. The overlay permission extends protection to any overlay windows drawn on top.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!hasOverlay) {
                InfoCard(Icons.Default.Info, "Optional: Overlay Permission",
                    "Grant 'Display over other apps' to extend screenshot blocking to overlay windows too.",
                    isError = false, actionLabel = "Grant",
                    onAction = { PermissionManager.openOverlaySettings(context) })
            }
        }
    }
}

// ─── VOLUME STYLES ───────────────────────────────────────────────────────────
@Composable
fun VolumeStylesScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val volumeStylesEnabled by AppPreferences.get(AppPreferences.VOLUME_STYLES_ENABLED, false).collectAsState(false)
    val savedStyle  by AppPreferences.get(AppPreferences.VOLUME_STYLE, "Default").collectAsState("Default")
    val savedColor  by AppPreferences.get(AppPreferences.VOLUME_COLOR, "#1C1C1E").collectAsState("#1C1C1E")
    val savedCorner by AppPreferences.get(AppPreferences.VOLUME_CORNER_RADIUS, 24f).collectAsState(24f)
    val savedOpacity by AppPreferences.get(AppPreferences.VOLUME_OPACITY, 0.92f).collectAsState(0.92f)
    val styles = listOf("Default", "Compact", "Pill", "Circular", "Minimal", "Expanded")
    var selected by remember { mutableStateOf(0) }
    LaunchedEffect(savedStyle) { selected = styles.indexOf(savedStyle).takeIf { it >= 0 } ?: 0 }
    var cornerRadius by remember { mutableFloatStateOf(24f) }
    LaunchedEffect(savedCorner) { cornerRadius = savedCorner }
    var opacity by remember { mutableFloatStateOf(0.92f) }
    LaunchedEffect(savedOpacity) { opacity = savedOpacity }

    var hasOverlay by remember { mutableStateOf(PermissionManager.hasOverlayPermission(context)) }
    var accessibilityEnabled by remember { mutableStateOf(PermissionManager.isAccessibilityEnabled(context)) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasOverlay = PermissionManager.hasOverlayPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs); onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Scaffold(topBar = { EverlastingTopBar("Volume Styles", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            if (!hasOverlay || !accessibilityEnabled) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⚠️ Permissions Needed for Custom Volume Panel",
                            style = MaterialTheme.typography.titleSmall)
                        if (!hasOverlay) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("• Display Over Other Apps", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { PermissionManager.openOverlaySettings(context) }) { Text("Grant") }
                            }
                        }
                        if (!accessibilityEnabled) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("• Accessibility Service (intercept volume keys)", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { PermissionManager.openAccessibilitySettings(context) }) { Text("Enable") }
                            }
                        }
                    }
                }
            }
            FeatureSection("Volume Panel Override") {
                ToggleSettingRow("Enable Custom Volume Styles", "Override Android's default volume panel", volumeStylesEnabled,
                    { scope.launch { AppPreferences.set(AppPreferences.VOLUME_STYLES_ENABLED, it) } })
                HorizontalDivider()
            }
            FeatureSection("Style") {
                styles.forEachIndexed { i, style ->
                    ListItem(
                        headlineContent = { Text(style) },
                        supportingContent = { Text(when (style) {
                            "Default"  -> "Uses Android's built-in volume panel"
                            "Compact"  -> "Slim vertical bar on the right edge"
                            "Pill"     -> "Rounded pill with smooth fill animation"
                            "Circular" -> "Compact circular indicator"
                            "Minimal"  -> "Ultra-minimal bar, icon only"
                            "Expanded" -> "Full-width bar with seek slider"
                            else -> ""
                        }) },
                        leadingContent = { RadioButton(selected = selected == i, onClick = {
                            selected = i
                            scope.launch { AppPreferences.set(AppPreferences.VOLUME_STYLE, style) }
                        }) }
                    )
                    if (i < styles.lastIndex) HorizontalDivider()
                }
            }
            FeatureSection("Appearance") {
                ColorPickerRow("Panel Background Color", savedColor) { scope.launch { AppPreferences.set(AppPreferences.VOLUME_COLOR, it) } }
                HorizontalDivider()
                Column(Modifier.padding(16.dp)) {
                    Text("Corner Radius: ${cornerRadius.toInt()}dp", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = cornerRadius, onValueChange = {
                        cornerRadius = it; scope.launch { AppPreferences.set(AppPreferences.VOLUME_CORNER_RADIUS, it) }
                    }, valueRange = 0f..64f, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    // Visible opacity label + colored track so it's always visible
                    Text("Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it; scope.launch { AppPreferences.set(AppPreferences.VOLUME_OPACITY, it) } },
                        valueRange = 0.2f..1f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    }
}

// ─── KEEP SCREEN ON ──────────────────────────────────────────────────────────
@Composable
fun KeepScreenOnScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by AppPreferences.get(AppPreferences.KEEP_SCREEN_ON_ENABLED, false).collectAsState(false)
    val canWriteSettings = Settings.System.canWrite(context)

    Scaffold(topBar = { EverlastingTopBar("Keep Screen On", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            if (!canWriteSettings) {
                InfoCard(Icons.Default.Warning, "WRITE_SETTINGS Permission Required",
                    "Needed to control screen timeout",
                    isError = true, actionLabel = "Grant",
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                            Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
            }
            FeatureSection("Screen Control") {
                ToggleSettingRow("Keep Screen On", "Prevent screen from turning off automatically", enabled, {
                    scope.launch { AppPreferences.set(AppPreferences.KEEP_SCREEN_ON_ENABLED, it) }
                    // Wake lock is managed by foreground service — no Settings.System needed
                })
            }
            InfoCard(Icons.Default.Lightbulb, "Quick Settings Tile",
                "Add 'Keep Screen On' to your Quick Settings panel. Swipe down → Edit tiles → drag it in.",
                isError = false)
        }
    }
}

// ─── DOUBLE TAP BACK ─────────────────────────────────────────────────────────
@Composable
fun DoubleTapBackScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled       by AppPreferences.get(AppPreferences.DOUBLE_TAP_BACK_ENABLED, false).collectAsState(false)
    val savedAction   by AppPreferences.get(AppPreferences.DOUBLE_TAP_BACK_ACTION, "torch").collectAsState("torch")
    val savedApp      by AppPreferences.get(AppPreferences.DOUBLE_TAP_BACK_APP, "").collectAsState("")
    val savedAppName  by AppPreferences.get(AppPreferences.DOUBLE_TAP_BACK_APP_NAME, "").collectAsState("")
    var selectedAction by remember { mutableStateOf("torch") }
    LaunchedEffect(savedAction) { selectedAction = savedAction }
    var accessibilityEnabled by remember { mutableStateOf(PermissionManager.isAccessibilityEnabled(context)) }

    val installedApps = remember {
        context.packageManager.getInstalledApplications(0)
            .filter { context.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .sortedBy { context.packageManager.getApplicationLabel(it).toString() }
    }
    var showAppPicker by remember { mutableStateOf(false) }

    if (showAppPicker) {
        AlertDialog(onDismissRequest = { showAppPicker = false },
            title = { Text("Pick an App") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    installedApps.forEach { app ->
                        val label = context.packageManager.getApplicationLabel(app).toString()
                        ListItem(headlineContent = { Text(label) },
                            supportingContent = { Text(app.packageName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.clickable {
                                scope.launch {
                                    AppPreferences.set(AppPreferences.DOUBLE_TAP_BACK_APP, app.packageName)
                                    AppPreferences.set(AppPreferences.DOUBLE_TAP_BACK_APP_NAME, label)
                                }
                                showAppPicker = false
                            })
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppPicker = false }) { Text("Cancel") } }
        )
    }


    val lifecycleOwnerDoubleTapBackScreen = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwnerDoubleTapBackScreen) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = PermissionManager.isAccessibilityEnabled(context)
            }
        }
        lifecycleOwnerDoubleTapBackScreen.lifecycle.addObserver(obs)
        onDispose { lifecycleOwnerDoubleTapBackScreen.lifecycle.removeObserver(obs) }
    }
    Scaffold(topBar = { EverlastingTopBar("Double Tap Back", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            if (!accessibilityEnabled) {
                InfoCard(Icons.Default.Warning, "Accessibility Service Required",
                    "Double tap back uses the accessibility service to intercept back key events",
                    isError = true, actionLabel = "Enable",
                    onAction = { PermissionManager.openAccessibilitySettings(context) })
            }
            FeatureSection("Double Tap Back") {
                ToggleSettingRow("Enable Double Tap Back", "Double-tap the back button to trigger an action", enabled, {
                    if (!accessibilityEnabled) { PermissionManager.openAccessibilitySettings(context); return@ToggleSettingRow }
                    scope.launch { AppPreferences.set(AppPreferences.DOUBLE_TAP_BACK_ENABLED, it) }
                })
                HorizontalDivider()
                ListItem(headlineContent = { Text("Toggle Flashlight") },
                    supportingContent = { Text("Quickly toggle the torch on/off") },
                    leadingContent = {
                        RadioButton(selected = selectedAction == "torch", onClick = {
                            selectedAction = "torch"
                            scope.launch { AppPreferences.set(AppPreferences.DOUBLE_TAP_BACK_ACTION, "torch") }
                        })
                    },
                    trailingContent = { Icon(Icons.Default.FlashOn, null) }
                )
                HorizontalDivider()
                ListItem(headlineContent = { Text("Launch App") },
                    supportingContent = { Text(if (savedAppName.isNotEmpty()) "App: $savedAppName" else "No app selected") },
                    leadingContent = {
                        RadioButton(selected = selectedAction == "app", onClick = {
                            selectedAction = "app"
                            scope.launch { AppPreferences.set(AppPreferences.DOUBLE_TAP_BACK_ACTION, "app") }
                            if (savedApp.isEmpty()) showAppPicker = true
                        })
                    },
                    trailingContent = {
                        if (selectedAction == "app") {
                            TextButton(onClick = { showAppPicker = true }) { Text("Choose App") }
                        }
                    }
                )
            }
        }
    }
}

// ─── HIDDEN FEATURES ─────────────────────────────────────────────────────────
@Composable
fun HiddenFeaturesScreen(navController: NavController) {
    val context = LocalContext.current
    data class HiddenFeature(val title: String, val subtitle: String, val apply: (Boolean) -> Unit)
    val features = listOf(
        HiddenFeature("Force Dark Mode", "Force dark mode on all apps") { on ->
            try { Settings.Global.putInt(context.contentResolver, "ui_night_mode", if (on) 2 else 1) } catch (_: Exception) {}
        },
        HiddenFeature("High Touch Sensitivity", "Enable for screen protectors") { on ->
            try { Settings.System.putInt(context.contentResolver, "touch_sensitivity", if (on) 1 else 0) } catch (_: Exception) {}
        },
        HiddenFeature("Show Tap Circles", "Visual circles on touch") { on ->
            try { Settings.System.putInt(context.contentResolver, "show_touches", if (on) 1 else 0) } catch (_: Exception) {}
        },
        HiddenFeature("Show Pointer Location", "Touch coordinate overlay") { on ->
            try { Settings.System.putInt(context.contentResolver, "pointer_location", if (on) 1 else 0) } catch (_: Exception) {}
        },
        HiddenFeature("Disable Screenshot Sound", "Silent screenshots") { on ->
            try { Settings.Global.putInt(context.contentResolver, "screenshot_shutter_sound", if (on) 0 else 1) } catch (_: Exception) {}
        },
        HiddenFeature("Force 60Hz (Battery Save)", "Lock refresh rate to 60Hz") { on ->
            try { Settings.System.putFloat(context.contentResolver, "min_refresh_rate", if (on) 60f else 0f) } catch (_: Exception) {}
        },
        HiddenFeature("Aggressive Doze", "More aggressive battery saving") { on ->
            try { Runtime.getRuntime().exec(arrayOf("sh", "-c", "settings put global device_idle_constants ${if (on) "light_after_inactive_to=15000,inactive_to=30000" else ""}")) } catch (_: Exception) {}
        },
        HiddenFeature("Disable Bluetooth Scanning", "Stop background BT scanning") { on ->
            try { Settings.Global.putInt(context.contentResolver, "ble_scan_always_enabled", if (on) 0 else 1) } catch (_: Exception) {}
        },
    )
    val states = remember { mutableStateListOf(*Array(features.size) { false }) }
    Scaffold(topBar = { EverlastingTopBar("Hidden Android Features", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            InfoCard(Icons.Default.Build, "Advanced Settings",
                "Some settings require WRITE_SETTINGS or ADB. Changes may be overridden by system.",
                isError = false)
            FeatureSection("Hidden Settings") {
                features.forEachIndexed { i, feature ->
                    ListItem(headlineContent = { Text(feature.title) },
                        supportingContent = { Text(feature.subtitle) },
                        trailingContent = { com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = states[i], onCheckedChange = { isOn -> states[i] = isOn; feature.apply(isOn) }) })
                    if (i < features.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}

// ─── WALLPAPER EFFECTS ───────────────────────────────────────────────────────
@Composable
fun WallpaperEffectsScreen(navController: NavController) {
    val context = LocalContext.current
    val effects = listOf(
        "Pixel Depth Effect" to "3D parallax depth effect",
        "Dock Blur Wall" to "Blur wallpaper behind dock area",
        "Tint on Lock" to "Tint wallpaper when screen locks",
        "Dim on Notification" to "Dim wallpaper when notifications arrive",
        "Color Shift" to "Shift wallpaper colors with time of day",
        "Vignette Edge" to "Dark vignette around screen edges",
        "Blur Widgets Area" to "Blur behind widget zones"
    )
    val states = remember { mutableStateListOf(*Array(effects.size) { false }) }
    var intensity by remember { mutableFloatStateOf(0.5f) }
    Scaffold(topBar = { EverlastingTopBar("Wallpaper Effects", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            FeatureSection("Effect Intensity") {
                Column(Modifier.padding(16.dp)) {
                    Text("Intensity: ${(intensity * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)
                    Slider(value = intensity, onValueChange = { intensity = it }, modifier = Modifier.fillMaxWidth())
                }
            }
            FeatureSection("Effects") {
                effects.forEachIndexed { i, (name, desc) ->
                    ListItem(headlineContent = { Text(name) }, supportingContent = { Text(desc) },
                        trailingContent = { com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = states[i], onCheckedChange = { states[i] = it }) })
                    if (i < effects.lastIndex) HorizontalDivider()
                }
            }
            InfoCard(Icons.Default.Info, "Note",
                "Deeper effects like parallax and blur require a Live Wallpaper.", isError = false)
        }
    }
}

// ─── SCREEN LOCKED SECURITY ──────────────────────────────────────────────────
@Composable
fun ScreenLockedSecurityScreen(navController: androidx.navigation.NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by com.coolappstore.everlastingandroidtweak.data.AppPreferences.get(
        com.coolappstore.everlastingandroidtweak.data.AppPreferences.SCREEN_LOCKED_SECURITY_ENABLED, false
    ).collectAsState(false)

    fun toggleWithBiometric(isChecked: Boolean) {
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (activity != null) {
            val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
            val prompt = androidx.biometric.BiometricPrompt(
                activity, executor,
                object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                        scope.launch {
                            com.coolappstore.everlastingandroidtweak.data.AppPreferences.set(
                                com.coolappstore.everlastingandroidtweak.data.AppPreferences.SCREEN_LOCKED_SECURITY_ENABLED,
                                isChecked
                            )
                        }
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
                    override fun onAuthenticationFailed() {}
                }
            )
            val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle("Screen Locked Security")
                .setSubtitle(
                    if (isChecked) "Authenticate to enable screen locked security"
                    else "Authenticate to disable screen locked security"
                )
                .setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(promptInfo)
        } else {
            scope.launch {
                com.coolappstore.everlastingandroidtweak.data.AppPreferences.set(
                    com.coolappstore.everlastingandroidtweak.data.AppPreferences.SCREEN_LOCKED_SECURITY_ENABLED,
                    isChecked
                )
            }
        }
    }

    Scaffold(topBar = { com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar("Screen Locked Security", navController) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Security",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text("Disable QS tiles when the device is locked") },
                    supportingContent = { Text("Disable quick setting tiles when the device is locked", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Security, null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(
                            checked = enabled,
                            onCheckedChange = { toggleWithBiometric(it) }
                        )
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Device Admin",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "For instant hardware-level lock (DevicePolicyManager.lockNow()), grant Device Admin permission to Everlasting. Open Device Admin settings below and enable the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        val intent = android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                            putExtra(
                                android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                android.content.ComponentName(context,
                                    com.coolappstore.everlastingandroidtweak.features.security.SecurityDeviceAdminReceiver::class.java)
                            )
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Grant Device Admin")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "About",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Enhance security when your device is locked.\n\nRestrict access to some sensitive QS tiles preventing unauthorized network modifications and further preventing them re-attempting to do so by increasing the animation speed to prevent touch spam.\n\nThis feature is not robust and may have flaws such as some tiles which allow toggling directly such as bluetooth or flight mode not being able to be prevented.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── SCALE & ADJUSTMENTS ─────────────────────────────────────────────────────
@Composable
fun ScaleAdjustmentsScreen(navController: androidx.navigation.NavController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val helper = com.coolappstore.everlastingandroidtweak.features.scale.ScaleAdjustmentsHelper
    val scope = rememberCoroutineScope()
    val cr = context.contentResolver

    var currentMode by remember { mutableStateOf(helper.getMode(context)) }
    var fontScale by remember { mutableFloatStateOf(try { android.provider.Settings.System.getFloat(cr, android.provider.Settings.System.FONT_SCALE) } catch (_: Exception) { 1.0f }) }
    var fontWeight by remember { mutableIntStateOf(helper.getFontWeight(context)) }
    var animatorDuration by remember { mutableFloatStateOf(try { android.provider.Settings.Global.getFloat(cr, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE) } catch (_: Exception) { 1.0f }) }
    var transitionAnim by remember { mutableFloatStateOf(try { android.provider.Settings.Global.getFloat(cr, android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE) } catch (_: Exception) { 1.0f }) }
    var windowAnim by remember { mutableFloatStateOf(try { android.provider.Settings.Global.getFloat(cr, android.provider.Settings.Global.WINDOW_ANIMATION_SCALE) } catch (_: Exception) { 1.0f }) }
    var smallestWidth by remember { mutableIntStateOf(helper.getSmallestWidth(context)) }
    var touchSensitivity by remember { mutableStateOf(helper.getTouchSensitivity(context)) }
    var autoRotate by remember { mutableStateOf(try { android.provider.Settings.System.getInt(cr, android.provider.Settings.System.ACCELEROMETER_ROTATION, 0) == 1 } catch (_: Exception) { false }) }

    val timeoutValues = listOf(15000L, 30000L, 60000L, 120000L, 300000L, 600000L, 1800000L)
    var screenTimeoutMs by remember { mutableLongStateOf(try { android.provider.Settings.System.getLong(cr, android.provider.Settings.System.SCREEN_OFF_TIMEOUT, 30000L) } catch (_: Exception) { 30000L }) }
    var timeoutIndex by remember { mutableIntStateOf(timeoutValues.indexOf(screenTimeoutMs).coerceAtLeast(0)) }

    val shizukuReady = com.coolappstore.everlastingandroidtweak.shizuku.ShizukuManager.isReady()

    fun switchMode(mode: String) {
        if (mode == currentMode) return
        val current = com.coolappstore.everlastingandroidtweak.features.scale.ScaleAnimationsProfile(
            fontScale = fontScale,
            animatorDurationScale = animatorDuration,
            transitionAnimationScale = transitionAnim,
            windowAnimationScale = windowAnim,
            smallestWidth = smallestWidth,
            autoRotateEnabled = autoRotate,
            screenTimeoutMs = screenTimeoutMs
        )
        helper.saveProfile(context, currentMode, current)
        val newProfile = helper.loadProfile(context, mode)
        helper.setMode(context, mode)
        helper.applyProfile(context, newProfile)
        fontScale = newProfile.fontScale
        animatorDuration = newProfile.animatorDurationScale
        transitionAnim = newProfile.transitionAnimationScale
        windowAnim = newProfile.windowAnimationScale
        smallestWidth = newProfile.smallestWidth
        autoRotate = newProfile.autoRotateEnabled
        screenTimeoutMs = newProfile.screenTimeoutMs
        timeoutIndex = timeoutValues.indexOf(newProfile.screenTimeoutMs).coerceAtLeast(0)
        currentMode = mode
    }

    Scaffold(topBar = { com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar("Scale & Adjustments", navController) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Mode picker ───────────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("default" to "Default", "glove" to "Glove mode").forEach { (mode, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = currentMode == mode,
                            onClick = { switchMode(mode) },
                            label = { Text(label) },
                            leadingIcon = if (mode == "glove") ({
                                Icon(Icons.Default.PanTool, null, Modifier.size(16.dp))
                            }) else ({
                                Icon(Icons.Default.TouchApp, null, Modifier.size(16.dp))
                            }),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Toggles: touch sensitivity, auto-rotate, screen timeout ──────
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    // Touch sensitivity
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Increase touch sensitivity") },
                        supportingContent = { Text("Improve touch screen responsiveness when using gloves or screen protectors", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = { Icon(Icons.Default.TouchApp, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = touchSensitivity, onCheckedChange = {
                                touchSensitivity = it
                                helper.setTouchSensitivity(context, it)
                            })
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                    HorizontalDivider()
                    // Auto-rotate
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Auto rotate") },
                        supportingContent = { Text("Automatically rotate the screen when the device is turned", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = { Icon(Icons.Default.ScreenRotation, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = autoRotate, onCheckedChange = {
                                autoRotate = it
                                helper.setAutoRotate(context, it)
                            })
                        },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                    HorizontalDivider()
                    // Screen timeout slider
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, null, modifier = Modifier.padding(end = 12.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                val label = timeoutValues.getOrNull(timeoutIndex)?.let { ms ->
                                    when {
                                        ms < 60000L -> "${ms / 1000}s"
                                        ms == 60000L -> "1 minute"
                                        else -> "${ms / 60000} minutes"
                                    }
                                } ?: "30s"
                                Text("Screen timeout: $label", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val newIdx = (timeoutIndex - 1).coerceAtLeast(0)
                                timeoutIndex = newIdx
                                screenTimeoutMs = timeoutValues[newIdx]
                                helper.setScreenTimeout(context, timeoutValues[newIdx])
                            }, enabled = timeoutIndex > 0) {
                                Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = timeoutIndex.toFloat(),
                                onValueChange = {
                                    timeoutIndex = it.toInt()
                                    screenTimeoutMs = timeoutValues[it.toInt()]
                                    helper.setScreenTimeout(context, timeoutValues[it.toInt()])
                                },
                                valueRange = 0f..(timeoutValues.size - 1).toFloat(),
                                steps = timeoutValues.size - 2,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                val newIdx = (timeoutIndex + 1).coerceAtMost(timeoutValues.size - 1)
                                timeoutIndex = newIdx
                                screenTimeoutMs = timeoutValues[newIdx]
                                helper.setScreenTimeout(context, timeoutValues[newIdx])
                            }, enabled = timeoutIndex < timeoutValues.size - 1) {
                                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // ── Text section ──────────────────────────────────────────────────
            Text("Text", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    // Font scale slider
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                        Text("Font Scale: ${"%.2f".format(fontScale)}x", style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                fontScale = ((fontScale - 0.05f) * 100).toInt() / 100f
                                fontScale = fontScale.coerceIn(0.25f, 5.0f)
                                helper.setFontScale(context, fontScale)
                            }) { Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.primary) }
                            Slider(value = fontScale, onValueChange = { fontScale = ((it * 100).toInt() / 100f); helper.setFontScale(context, fontScale) }, valueRange = 0.25f..5.0f, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                fontScale = ((fontScale + 0.05f) * 100).toInt() / 100f
                                fontScale = fontScale.coerceIn(0.25f, 5.0f)
                                helper.setFontScale(context, fontScale)
                            }) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                    // Font weight slider
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                        Text("Font Weight: $fontWeight", style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { fontWeight = (fontWeight - 10).coerceAtLeast(0); helper.setFontWeight(context, fontWeight) }) {
                                Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Slider(value = fontWeight.toFloat(), onValueChange = { fontWeight = (it.toInt() / 10) * 10; helper.setFontWeight(context, fontWeight) }, valueRange = 0f..500f, steps = 10, modifier = Modifier.weight(1f))
                            IconButton(onClick = { fontWeight = (fontWeight + 10).coerceAtMost(500); helper.setFontWeight(context, fontWeight) }) {
                                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    // Reset text button
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceBright).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(onClick = {
                            fontScale = 1.0f; fontWeight = 0
                            helper.setFontScale(context, 1.0f)
                            helper.setFontWeight(context, 0)
                        }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ── Scale section ─────────────────────────────────────────────────
            Text("Scale", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    // Smallest width slider
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                        if (!shizukuReady) {
                            Text("Shizuku permission required to adjust scale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text("Smallest Width: ${smallestWidth} dp", style = MaterialTheme.typography.bodyMedium, color = if (shizukuReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (shizukuReady) { smallestWidth = (smallestWidth - 10).coerceAtLeast(300); helper.setSmallestWidth(context, smallestWidth) } }, enabled = shizukuReady) {
                                Icon(Icons.Default.Remove, null, tint = if (shizukuReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                            }
                            Slider(
                                value = smallestWidth.toFloat(),
                                onValueChange = { if (shizukuReady) { smallestWidth = it.toInt() } },
                                onValueChangeFinished = { if (shizukuReady) helper.setSmallestWidth(context, smallestWidth) },
                                valueRange = 300f..1000f,
                                modifier = Modifier.weight(1f),
                                enabled = shizukuReady
                            )
                            IconButton(onClick = { if (shizukuReady) { smallestWidth = (smallestWidth + 10).coerceAtMost(1000); helper.setSmallestWidth(context, smallestWidth) } }, enabled = shizukuReady) {
                                Icon(Icons.Default.Add, null, tint = if (shizukuReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
                            }
                        }
                    }
                    // Reset scale button
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceBright).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = if (shizukuReady) Arrangement.End else Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!shizukuReady) {
                            FilledTonalButton(onClick = {
                                com.coolappstore.everlastingandroidtweak.shizuku.ShizukuManager.requestPermission()
                            }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                Text("Grant Permission", style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            FilledTonalButton(onClick = {
                                helper.resetSmallestWidth(context)
                                smallestWidth = helper.getSmallestWidth(context)
                            }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                                Text("Reset", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // ── Animations section ────────────────────────────────────────────
            Text("Animations", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    listOf(
                        Triple("Animator duration scale", animatorDuration, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE),
                        Triple("Transition animation scale", transitionAnim, android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE),
                        Triple("Window animation scale", windowAnim, android.provider.Settings.Global.WINDOW_ANIMATION_SCALE)
                    ).forEachIndexed { idx, (label, value, key) ->
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(top = if (idx == 0) 16.dp else 8.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                            Text("$label: ${"%.2f".format(value)}x", style = MaterialTheme.typography.bodyMedium)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = {
                                    val newVal = ((value - 0.05f) * 100).toInt() / 100f
                                    val clamped = newVal.coerceIn(0f, 10f)
                                    helper.setAnimationScale(context, key, clamped)
                                    when (idx) { 0 -> animatorDuration = clamped; 1 -> transitionAnim = clamped; 2 -> windowAnim = clamped }
                                }) { Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.primary) }
                                Slider(
                                    value = value,
                                    onValueChange = { v ->
                                        val clamped = ((v * 100).toInt() / 100f).coerceIn(0f, 10f)
                                        helper.setAnimationScale(context, key, clamped)
                                        when (idx) { 0 -> animatorDuration = clamped; 1 -> transitionAnim = clamped; 2 -> windowAnim = clamped }
                                    },
                                    valueRange = 0f..10f,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    val newVal = ((value + 0.05f) * 100).toInt() / 100f
                                    val clamped = newVal.coerceIn(0f, 10f)
                                    helper.setAnimationScale(context, key, clamped)
                                    when (idx) { 0 -> animatorDuration = clamped; 1 -> transitionAnim = clamped; 2 -> windowAnim = clamped }
                                }) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) }
                            }
                        }
                    }
                    // Reset animations button
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceBright).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(onClick = {
                            animatorDuration = 1.0f; transitionAnim = 1.0f; windowAnim = 1.0f
                            helper.setAnimationScale(context, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
                            helper.setAnimationScale(context, android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE, 1.0f)
                            helper.setAnimationScale(context, android.provider.Settings.Global.WINDOW_ANIMATION_SCALE, 1.0f)
                        }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // ── Quick Settings Tile info ──────────────────────────────────────
            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Quick Settings Tile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Add the 'Scale Animations' tile from your Quick Settings panel (edit tiles). Tapping it saves the current system values into the active profile slot, then loads and applies the opposite profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            InfoCard(Icons.Default.Info, "Permissions Required",
                "Font scale requires WRITE_SETTINGS. Animation scales require WRITE_SECURE_SETTINGS (grant via ADB: adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS). Smallest Width requires Shizuku.",
                isError = false)

            Spacer(Modifier.height(16.dp))
        }
    }
}
