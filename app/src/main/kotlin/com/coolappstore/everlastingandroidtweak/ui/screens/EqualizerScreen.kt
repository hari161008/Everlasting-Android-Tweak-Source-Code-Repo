package com.coolappstore.everlastingandroidtweak.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.features.equalizer.BassBoostManager
import com.coolappstore.everlastingandroidtweak.features.equalizer.EqualizerManager
import com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val EQ_RANGE = 15f

// UI always shows 2 extra bands beyond hardware bands
private const val EXTRA_UI_BANDS = 2

// Band labels for 7-band UI (5 hw + 2) and 12-band UI (10 hw + 2)
private val BAND_LABELS_7  = listOf("20Hz", "60Hz", "230Hz", "910Hz", "3.6k", "14k", "20k")
private val BAND_LABELS_12 = listOf("20", "32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k", "20k")

/**
 * Map UI levels (uiCount = hwCount + EXTRA_UI_BANDS) down to hardware band count
 * via linear interpolation so extra UI bands influence adjacent HW bands smoothly.
 */
private fun uiToHwLevels(uiLevels: List<Float>, hwCount: Int): List<Float> {
    val uiCount = uiLevels.size
    if (uiCount == hwCount) return uiLevels
    return List(hwCount) { hw ->
        val uiF = hw.toFloat() * (uiCount - 1).toFloat() / (hwCount - 1).toFloat()
        val lo  = uiF.toInt().coerceIn(0, uiCount - 2)
        val hi  = (lo + 1).coerceAtMost(uiCount - 1)
        val t   = uiF - lo
        uiLevels[lo] * (1f - t) + uiLevels[hi] * t
    }
}

/**
 * Interpolate a base preset (5 or 10 bands) to any uiCount using linear interpolation.
 */
private fun interpolatePreset(base5: List<Float>, base10: List<Float>, uiCount: Int): List<Float> {
    val base      = if (uiCount <= 7) base5 else base10
    val baseCount = if (uiCount <= 7) 5 else 10
    if (uiCount == baseCount) return base
    return List(uiCount) { i ->
        val f  = i.toFloat() * (baseCount - 1).toFloat() / (uiCount - 1).coerceAtLeast(1).toFloat()
        val lo = f.toInt().coerceIn(0, baseCount - 2)
        val hi = (lo + 1).coerceAtMost(baseCount - 1)
        val t  = f - lo
        base[lo] * (1f - t) + base[hi] * t
    }
}

private fun presetsFor(uiCount: Int): List<Triple<String, String, List<Float>>> {
    val flat = List(uiCount) { 0f }
    fun p(b5: List<Float>, b10: List<Float>) = interpolatePreset(b5, b10, uiCount)
    return listOf(
        Triple("Flat",       "", flat),
        Triple("Bass Boost", "", p(listOf(8f, 6f, 1f, -1f, -2f),   listOf(12f, 10f, 7f, 4f, 1f, -1f, -1f, -2f, -2f, -3f))),
        Triple("Treble",     "", p(listOf(-2f,-1f, 2f, 5f,  7f),   listOf(-3f,-2f,-1f, 0f, 1f,  3f,  5f,  7f,  9f, 11f))),
        Triple("Vocal",      "", p(listOf(-2f, 1f, 5f, 3f, -1f),   listOf(-4f,-3f, 0f, 4f, 7f,  7f,  5f,  3f,  0f, -3f))),
        Triple("Rock",       "", p(listOf( 5f, 3f,-2f, 3f,  5f),   listOf( 7f, 5f, 4f, 2f,-2f, -3f,  2f,  4f,  5f,  6f))),
        Triple("Electronic", "", p(listOf( 4f, 2f,-1f, 3f,  4f),   listOf( 7f, 5f, 3f, 0f,-2f, -1f,  2f,  4f,  5f,  5f))),
        Triple("Jazz",       "", p(listOf( 2f, 3f, 1f,-1f,  2f),   listOf( 4f, 3f, 2f, 3f,-1f, -2f,  0f,  2f,  3f,  4f))),
        Triple("Classical",  "", p(listOf( 3f, 2f, 0f, 1f,  3f),   listOf( 5f, 4f, 3f, 2f, 0f,  0f,  0f,  2f,  3f,  4f))),
    )
}

// Swap width/height for vertical slider layout
private fun Modifier.verticalSlider(trackLength: Dp): Modifier =
    layout { measurable, constraints ->
        val trackPx = trackLength.roundToPx()
        val placeable = measurable.measure(
            androidx.compose.ui.unit.Constraints(
                minWidth  = trackPx,
                maxWidth  = trackPx,
                minHeight = 0,
                maxHeight = constraints.maxWidth.coerceAtLeast(1)
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2)
            )
        }
    }.rotate(270f)

// ── EQ Curve ─────────────────────────────────────────────────────────────────
@Composable
private fun EqCurveCanvas(levels: List<Float>, primaryColor: Color, modifier: Modifier = Modifier) {
    val animLevels = levels.map { level ->
        val anim = remember { Animatable(level) }
        LaunchedEffect(level) { anim.animateTo(level, spring(stiffness = Spring.StiffnessMedium)) }
        anim.value
    }
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val mid = h / 2f
        val inset   = w * 0.04f
        val spacing = if (animLevels.size > 1) (w - 2 * inset) / (animLevels.size - 1) else w
        repeat(5) { i -> drawLine(primaryColor.copy(alpha = 0.08f), Offset(0f, h * i / 4f), Offset(w, h * i / 4f), 1f) }
        drawLine(primaryColor.copy(alpha = 0.22f), Offset(0f, mid), Offset(w, mid), 1.5f)
        if (animLevels.size < 2) return@Canvas
        val pts = animLevels.mapIndexed { i, dB -> Offset(inset + i * spacing, mid - (dB / EQ_RANGE) * (h / 2f - 6f)) }
        val fill = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) { val cx = (pts[i].x + pts[i+1].x) / 2f; cubicTo(cx, pts[i].y, cx, pts[i+1].y, pts[i+1].x, pts[i+1].y) }
            lineTo(pts.last().x, mid); lineTo(pts.first().x, mid); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.28f), primaryColor.copy(alpha = 0.02f)), 0f, h))
        val curve = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) { val cx = (pts[i].x + pts[i+1].x) / 2f; cubicTo(cx, pts[i].y, cx, pts[i+1].y, pts[i+1].x, pts[i+1].y) }
        }
        drawPath(curve, primaryColor, style = Stroke(2.5f, cap = StrokeCap.Round))
        pts.forEach { pt -> drawCircle(primaryColor, 4f, pt); drawCircle(Color.White, 2f, pt) }
    }
}

// ── Bass Boost Ring ───────────────────────────────────────────────────────────
@Composable
private fun BassBoostRing(
    strength: Int,        // 0..1000
    enabled: Boolean,
    primaryColor: Color,
    onStrengthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val animSweep by animateFloatAsState(
        targetValue = if (enabled) (strength / 1000f) * 270f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bassRingSweep"
    )
    val ringColor = if (enabled) primaryColor else primaryColor.copy(alpha = 0.25f)

    // Track cumulative vertical drag within a single gesture
    val gestureStart = remember { intArrayOf(0) }
    val gestureDelta = remember { floatArrayOf(0f) }

    Box(
        modifier = modifier
            .size(160.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        gestureStart[0] = strength
                        gestureDelta[0] = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Drag up → increase, drag down → decrease
                        // 500px total vertical drag = full 0→1000 sweep
                        gestureDelta[0] -= dragAmount.y
                        val newStrength = (gestureStart[0] + (gestureDelta[0] / 500f * 1000f).toInt())
                            .coerceIn(0, 1000)
                        if (newStrength != strength) onStrengthChange(newStrength)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 18.dp.toPx()
            val inset       = strokeWidth / 2f
            val arcSize     = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft     = Offset(inset, inset)

            // Background track (full 270° arc)
            drawArc(
                color      = primaryColor.copy(alpha = 0.10f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcSize,
                style      = Stroke(strokeWidth, cap = StrokeCap.Round)
            )

            // Gradient progress ring
            if (animSweep > 0f) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawArc(
                    brush = Brush.sweepGradient(
                        0f   to ringColor.copy(alpha = 0.5f),
                        1f   to ringColor,
                        center = Offset(cx, cy)
                    ),
                    startAngle = 135f,
                    sweepAngle = animSweep,
                    useCenter  = false,
                    topLeft    = topLeft,
                    size       = arcSize,
                    style      = Stroke(strokeWidth, cap = StrokeCap.Round)
                )

                // Moving end-cap dot
                val angleRad = Math.toRadians((135.0 + animSweep))
                val r        = (size.width - strokeWidth) / 2f
                val dotX     = cx + r * cos(angleRad).toFloat()
                val dotY     = cy + r * sin(angleRad).toFloat()
                drawCircle(Color.White,   strokeWidth / 2.5f, Offset(dotX, dotY))
                drawCircle(ringColor,     strokeWidth / 4.0f, Offset(dotX, dotY))
            }

            // Tick marks at 0%, 25%, 50%, 75%, 100%
            val tickAngles = listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f)
            val rOuter = (size.width - strokeWidth) / 2f
            val cx     = size.width / 2f
            val cy     = size.height / 2f
            tickAngles.forEach { frac ->
                val angle  = Math.toRadians((135.0 + frac * 270.0))
                val rInner = rOuter - 8.dp.toPx()
                val x1 = cx + rInner * cos(angle).toFloat()
                val y1 = cy + rInner * sin(angle).toFloat()
                val x2 = cx + (rOuter + strokeWidth / 2f - 2.dp.toPx()) * cos(angle).toFloat()
                val y2 = cy + (rOuter + strokeWidth / 2f - 2.dp.toPx()) * sin(angle).toFloat()
                drawLine(primaryColor.copy(alpha = 0.30f), Offset(x1, y1), Offset(x2, y2), 1.5f)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                "${strength / 10}%",
                style     = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color      = if (enabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Bass",
                style  = MaterialTheme.typography.labelSmall,
                color  = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Main Screen ───────────────────────────────────────────────────────────────
@Composable
fun EqualizerScreen(navController: NavController) {
    val scope   = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary

    val enabled      by AppPreferences.get(AppPreferences.EQUALIZER_ENABLED,   false).collectAsState(false)
    val savedLevels  by AppPreferences.get(AppPreferences.EQ_BAND_LEVELS,       "").collectAsState("")
    val bassEnabled  by AppPreferences.get(AppPreferences.BASS_BOOST_ENABLED,  false).collectAsState(false)
    val bassStrength by AppPreferences.get(AppPreferences.BASS_BOOST_STRENGTH,  0).collectAsState(0)

    var eqInitialized   by remember { mutableStateOf(false) }
    var bassInitialized by remember { mutableStateOf(false) }
    var deviceBandCount by remember { mutableIntStateOf(5) }
    // UI shows 2 extra bands on top of whatever the hardware provides
    val uiBandCount by remember { derivedStateOf { deviceBandCount + EXTRA_UI_BANDS } }

    fun parseLevels(raw: String, count: Int): List<Float> {
        val parts = raw.split(",").map { it.trim().toFloatOrNull() ?: 0f }
        return List(count) { i -> parts.getOrElse(i) { 0f } }
    }

    LaunchedEffect(Unit) {
        eqInitialized   = EqualizerManager.init()
        bassInitialized = BassBoostManager.init()
        deviceBandCount = EqualizerManager.deviceBandCount
        if (eqInitialized) {
            val uiLevels = parseLevels(savedLevels, deviceBandCount + EXTRA_UI_BANDS)
            EqualizerManager.applyAllBands(uiToHwLevels(uiLevels, deviceBandCount))
            EqualizerManager.setEnabled(enabled)
        }
        if (bassInitialized) {
            BassBoostManager.setStrength(bassStrength.toShort())
            BassBoostManager.setEnabled(bassEnabled)
        }
    }

    val bandLevels = remember(deviceBandCount) {
        mutableStateListOf(*parseLevels(savedLevels, deviceBandCount + EXTRA_UI_BANDS).toTypedArray())
    }

    LaunchedEffect(savedLevels, deviceBandCount) {
        parseLevels(savedLevels, deviceBandCount + EXTRA_UI_BANDS).forEachIndexed { i, v ->
            if (i < bandLevels.size) bandLevels[i] = v
        }
    }

    val bandLabels   = if (uiBandCount <= 7) BAND_LABELS_7 else BAND_LABELS_12
    val presets      = presetsFor(uiBandCount)
    var selectedPreset by remember { mutableIntStateOf(-1) }

    // Real-time debounce holder — not a state so it won't trigger recomposition
    val realtimeJobHolder = remember { arrayOf<Job?>(null) }

    /** Apply EQ to hardware immediately (called from real-time slider drag). */
    fun applyRealtime() {
        realtimeJobHolder[0]?.cancel()
        realtimeJobHolder[0] = scope.launch {
            delay(8L) // ~half-frame debounce for maximum responsiveness
            if (eqInitialized) {
                EqualizerManager.applyAllBands(uiToHwLevels(bandLevels.toList(), deviceBandCount))
            }
        }
    }

    /** Persist to DataStore + apply — called on slider release. */
    fun onSliderFinished() {
        scope.launch {
            AppPreferences.set(AppPreferences.EQ_BAND_LEVELS, bandLevels.joinToString(","))
            if (eqInitialized) EqualizerManager.applyAllBands(uiToHwLevels(bandLevels.toList(), deviceBandCount))
        }
    }

    fun applyPreset(name: String, values: List<Float>) {
        values.forEachIndexed { i, v -> if (i < bandLevels.size) bandLevels[i] = v }
        selectedPreset = presets.indexOfFirst { it.first == name }
        scope.launch {
            AppPreferences.set(AppPreferences.EQ_BAND_LEVELS, bandLevels.joinToString(","))
            if (eqInitialized) EqualizerManager.applyAllBands(uiToHwLevels(values, deviceBandCount))
        }
    }

    val trackLength = 290.dp

    Scaffold(topBar = {
        EverlastingTopBar("Built-in Equalizer", navController, actions = {
            IconButton(onClick = {
                bandLevels.indices.forEach { bandLevels[it] = 0f }
                selectedPreset = -1
                scope.launch {
                    val zeros = List(uiBandCount) { 0f }
                    AppPreferences.set(AppPreferences.EQ_BAND_LEVELS, zeros.joinToString(","))
                    if (eqInitialized) EqualizerManager.applyAllBands(List(deviceBandCount) { 0f })
                }
            }) { Icon(Icons.Default.RestartAlt, "Reset", tint = primary) }
        })
    }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {

            // ── Notice card ───────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape  = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info, null,
                        tint     = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Turn on the equaliser first before you play any music to make it work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // ── Error banner ──────────────────────────────────────────────
            if (!eqInitialized) {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape  = MaterialTheme.shapes.large
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Equalizer unavailable — AudioEffect API blocked by this ROM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // ── EQ enable toggle ──────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape  = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled) primary.copy(alpha = 0.12f)
                                     else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Equalizer, null,
                        tint = if (enabled) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Equalizer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (enabled) "Active — ${uiBandCount}-band processing (${deviceBandCount} hw bands)"
                            else "Tap switch to enable",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { isOn ->
                        scope.launch {
                            AppPreferences.set(AppPreferences.EQUALIZER_ENABLED, isOn)
                            if (eqInitialized) {
                                if (isOn) EqualizerManager.applyAllBands(uiToHwLevels(bandLevels.toList(), deviceBandCount))
                                EqualizerManager.setEnabled(isOn)
                            }
                        }
                    })
                }
            }

            // ── EQ curve ─────────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape  = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text("Curve", style = MaterialTheme.typography.labelMedium, color = primary, fontWeight = FontWeight.Bold)
                        Text(
                            "avg ${if (bandLevels.average() >= 0) "+" else ""}${bandLevels.average().toInt()} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    EqCurveCanvas(bandLevels.toList(), primary, Modifier.fillMaxWidth().height(90.dp))
                }
            }

            // ── Band sliders ──────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape  = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            "${uiBandCount}-Band EQ",
                            style = MaterialTheme.typography.labelLarge, color = primary, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "±${EQ_RANGE.toInt()} dB · real-time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    Row(Modifier.fillMaxWidth()) {
                        // dB scale labels
                        Column(
                            Modifier.width(28.dp).height(trackLength),
                            verticalArrangement   = Arrangement.SpaceBetween,
                            horizontalAlignment   = Alignment.End
                        ) {
                            listOf("+15", "+8", "0", "-8", "-15").forEach { label ->
                                Text(
                                    label, fontSize = 7.sp,
                                    color      = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    textAlign  = TextAlign.End,
                                    modifier   = Modifier.padding(end = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))

                        // Sliders — real-time apply on every value change
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                            bandLevels.forEachIndexed { i, level ->
                                val label   = bandLabels.getOrElse(i) { "$i" }
                                val isBoost = level >  2f
                                val isCut   = level < -2f

                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    // dB badge
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(when {
                                                isBoost -> primary.copy(alpha = 0.18f)
                                                isCut   -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                                else    -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                            })
                                            .padding(horizontal = 2.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (level >= 0f) "+${level.toInt()}" else "${level.toInt()}",
                                            fontSize   = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color      = when {
                                                isBoost -> primary
                                                isCut   -> MaterialTheme.colorScheme.error
                                                else    -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))

                                    // Vertical slider — real-time EQ on every value change
                                    Slider(
                                        value    = level,
                                        onValueChange = { newVal ->
                                            bandLevels[i] = newVal
                                            selectedPreset = -1
                                            // Apply to hardware in real time regardless of toggle state
                                            if (eqInitialized) applyRealtime()
                                        },
                                        onValueChangeFinished = { onSliderFinished() },
                                        valueRange = -EQ_RANGE..EQ_RANGE,
                                        modifier   = Modifier
                                            .verticalSlider(trackLength)
                                            .fillMaxWidth(),
                                        colors = SliderDefaults.colors(
                                            thumbColor          = primary,
                                            activeTrackColor    = primary,
                                            inactiveTrackColor  = primary.copy(alpha = 0.20f),
                                            activeTickColor     = Color.Transparent,
                                            inactiveTickColor   = Color.Transparent
                                        )
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        label, fontSize = 7.sp, color = primary,
                                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Bass Booster ──────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape  = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (bassEnabled) primary.copy(alpha = 0.10f)
                                     else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Bass Booster",
                                style      = MaterialTheme.typography.labelLarge,
                                color      = primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (bassEnabled) "Drag ring up/down · ${bassStrength / 10}% strength"
                                else "Enable to boost low frequencies",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = bassEnabled, onCheckedChange = { isOn ->
                            scope.launch {
                                AppPreferences.set(AppPreferences.BASS_BOOST_ENABLED, isOn)
                                if (bassInitialized) {
                                    BassBoostManager.setStrength(bassStrength.toShort())
                                    BassBoostManager.setEnabled(isOn)
                                }
                            }
                        })
                    }

                    Spacer(Modifier.height(16.dp))

                    // Circular ring control
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BassBoostRing(
                            strength         = bassStrength,
                            enabled          = bassEnabled,
                            primaryColor     = primary,
                            onStrengthChange = { newStr ->
                                scope.launch {
                                    AppPreferences.set(AppPreferences.BASS_BOOST_STRENGTH, newStr)
                                    if (bassInitialized && bassEnabled) {
                                        BassBoostManager.setStrength(newStr.toShort())
                                    }
                                }
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Quick strength presets
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Light" to 200, "Medium" to 500, "Heavy" to 800, "Max" to 1000).forEach { (name, str) ->
                            FilterChip(
                                selected = bassStrength == str && bassEnabled,
                                onClick  = {
                                    scope.launch {
                                        AppPreferences.set(AppPreferences.BASS_BOOST_STRENGTH, str)
                                        if (!bassEnabled) {
                                            AppPreferences.set(AppPreferences.BASS_BOOST_ENABLED, true)
                                            if (bassInitialized) BassBoostManager.setEnabled(true)
                                        }
                                        if (bassInitialized) BassBoostManager.setStrength(str.toShort())
                                    }
                                },
                                label    = { Text(name, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = primary.copy(alpha = 0.18f),
                                    selectedLabelColor     = primary
                                )
                            )
                        }
                    }
                }
            }

            // ── Presets ───────────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape  = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Presets", style = MaterialTheme.typography.labelMedium, color = primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    presets.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (name, _, values) ->
                                val idx = presets.indexOfFirst { it.first == name }
                                FilterChip(
                                    selected = selectedPreset == idx,
                                    onClick  = { applyPreset(name, values) },
                                    label    = {
                                        Text(
                                            name,
                                            style    = MaterialTheme.typography.labelSmall,
                                            maxLines = 1
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors   = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = primary.copy(alpha = 0.18f),
                                        selectedLabelColor     = primary
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // ── Info card ─────────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape  = MaterialTheme.shapes.large
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null,
                        tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Hardware: $deviceBandCount bands · UI: $uiBandCount bands · EQ applies in real time while dragging.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
