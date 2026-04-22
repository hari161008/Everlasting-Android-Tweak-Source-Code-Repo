package com.coolappstore.everlastingandroidtweak.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.launch
import kotlin.math.abs

// ── Top Bar ──────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EverlastingTopBar(
    title: String,
    navController: NavController,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(title, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge)
        },
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// ── Feature Card ─────────────────────────────────────────────────────────────
@Composable
fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = if (isDark) 0.20f else 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Toggle Row ───────────────────────────────────────────────────────────────
@Composable
fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (icon != null) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                    .background(accentColor.copy(alpha = if (isDark) 0.22f else 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ── Animated Switch ───────────────────────────────────────────────────────────
// Reads the TOGGLE_ANIMATION_ENABLED preference. When enabled, plays a quick
// press-and-bounce scale animation every time the checked state changes.
// Degrades gracefully to a plain Switch when animation is disabled.
@Composable
fun AnimatedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val animEnabled by AppPreferences.get(AppPreferences.TOGGLE_ANIMATION_ENABLED, true)
        .collectAsState(true)
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    // Trigger the bounce animation whenever checked flips
    var prevChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (animEnabled && checked != prevChecked) {
            prevChecked = checked
            scope.launch {
                // Quick press-in
                scale.animateTo(0.80f, spring(stiffness = Spring.StiffnessHigh))
                // Bounce out
                scale.animateTo(1.12f, spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness    = Spring.StiffnessMediumLow
                ))
                // Settle
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        }
    }

    Switch(
        checked         = checked,
        onCheckedChange = onCheckedChange,
        modifier        = modifier.scale(scale.value),
        enabled         = enabled
    )
}

// ── Section Header ────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
    )
}

// ── Feature Section Container ─────────────────────────────────────────────────
@Composable
fun FeatureSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .padding(top = 4.dp, bottom = 6.dp)) {
        Row(
            Modifier.padding(start = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title, style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(0.dp)
        ) { Column(content = content) }
    }
}

// ── Divider ───────────────────────────────────────────────────────────────────
@Composable
fun EverDivider(indent: Boolean = false) {
    HorizontalDivider(
        modifier = if (indent) Modifier.padding(start = 72.dp, end = 16.dp)
                   else Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

// ── Color Picker Dialog ───────────────────────────────────────────────────────
@Composable
fun EverlastingColorPickerDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    var hexInput by remember { mutableStateOf(initialHex.removePrefix("#")) }
    val parsedColor = remember(hexInput) {
        try {
            if (hexInput.length == 6) Color(android.graphics.Color.parseColor("#$hexInput"))
            else null
        } catch (_: Exception) { null }
    }
    val presets = listOf(
        "#006397","#E91E63","#9C27B0","#FF5722","#4CAF50",
        "#FF9800","#00BCD4","#3F51B5","#F44336","#009688",
        "#8BC34A","#FF4081","#7C4DFF","#00E5FF","#FFD600"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Pick Color", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                parsedColor?.let { col ->
                    Box(
                        Modifier.fillMaxWidth().height(56.dp)
                            .clip(RoundedCornerShape(14.dp)).background(col)
                    )
                }
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { v -> if (v.length <= 6) hexInput = v.filter { it.isLetterOrDigit() } },
                    label = { Text("Hex code") },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                // Presets
                Text("Presets", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                presets.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { hex ->
                            val col = try { Color(android.graphics.Color.parseColor(hex)) }
                                      catch (_: Exception) { Color.Gray }
                            Box(
                                Modifier.size(40.dp).clip(CircleShape).background(col)
                                    .then(if ("#$hexInput".equals(hex, true))
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier)
                                    .clickable { hexInput = hex.removePrefix("#") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (parsedColor != null) onColorSelected("#$hexInput") },
                enabled = parsedColor != null,
                shape = RoundedCornerShape(12.dp)
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Info Card ─────────────────────────────────────────────────────────────────
@Composable
fun InfoCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = if (isDark) 0.15f else 0.10f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = tint)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(2.dp))
                    FilledTonalButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(actionLabel, style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}
