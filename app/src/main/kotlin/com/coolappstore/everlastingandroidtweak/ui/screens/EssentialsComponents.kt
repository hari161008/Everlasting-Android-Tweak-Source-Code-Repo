@file:Suppress("unused")
package com.coolappstore.everlastingandroidtweak.ui.screens

// ─── Essentials helper components — copied verbatim, only package/imports adapted ───
// Source: github.com/sameerasw/essentials (MIT License)

import android.content.Context
import android.view.View
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.coolappstore.everlastingandroidtweak.R
import java.math.BigDecimal
import java.math.RoundingMode

// ── HapticUtil ────────────────────────────────────────────────────────────────
object HapticUtil {
    val isAppHapticsEnabled = mutableStateOf(true)
    fun performUIHaptic(view: View) {
        if (!isAppHapticsEnabled.value) return
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }
    fun performSliderHaptic(view: View) {
        if (!isAppHapticsEnabled.value) return
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }
    fun performVirtualKeyHaptic(view: View) {
        if (!isAppHapticsEnabled.value) return
        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }
    fun performLightHaptic(view: View) {
        if (!isAppHapticsEnabled.value) return
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
    }
    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences("essentials_prefs", Context.MODE_PRIVATE)
        isAppHapticsEnabled.value = prefs.getBoolean("app_haptics_enabled", true)
    }
}

// ── highlight Modifier ────────────────────────────────────────────────────────
fun Modifier.highlight(
    enabled: Boolean,
    color: Color = Color.Unspecified,
    shape: Shape = RectangleShape
): Modifier = composed {
    if (!enabled) return@composed Modifier
    val highlightColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.primaryContainer else color
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            alpha.animateTo(0.7f, animationSpec = repeatable(3,
                tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse))
            alpha.animateTo(0f)
        }
    }
    this.drawWithContent {
        drawContent()
        drawOutline(shape.createOutline(size, layoutDirection, this), highlightColor.copy(alpha.value))
    }
}

// ── RoundedCardContainer ──────────────────────────────────────────────────────
@Composable
fun RoundedCardContainer(
    modifier: Modifier = Modifier,
    spacing: Dp = 2.dp,
    cornerRadius: Dp = 24.dp,
    containerColor: Color = Color.Transparent,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)).background(containerColor),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

// ── IconToggleItem ────────────────────────────────────────────────────────────
@Composable
fun IconToggleItem(
    iconRes: Int,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
    showToggle: Boolean = true
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceBright,
                shape = RoundedCornerShape(MaterialTheme.shapes.extraSmall.bottomEnd)
            )
            .then(
                if (!showToggle && enabled)
                    Modifier.clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        onCheckedChange(!isChecked)
                    }
                else Modifier
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.size(2.dp))
        Icon(painterResource(iconRes), title, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(2.dp))
        if (description != null) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(description, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        if (showToggle) {
            Box {
                com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(
                    checked = if (enabled) isChecked else false,
                    onCheckedChange = { checked ->
                        if (enabled) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                            onCheckedChange(checked)
                        }
                    },
                    enabled = enabled
                )
                if (!enabled && onDisabledClick != null) {
                    Box(Modifier.matchParentSize().clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        onDisabledClick()
                    })
                }
            }
        }
    }
}

// ── SegmentedDropdownMenu ─────────────────────────────────────────────────────
// FIX: containerColor / tonalElevation / shadowElevation were added to DropdownMenu
// in Material3 1.3+. Everlasting uses an older BOM. Use Card wrapper instead.
@Composable
fun SegmentedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.background(Color.Transparent),
        offset = offset
    ) {
        RoundedCardContainer(cornerRadius = 16.dp, spacing = 2.dp, content = content)
    }
}

@Composable
fun SegmentedDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val view = LocalView.current
    DropdownMenuItem(
        text = text,
        onClick = {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled
    )
}

// ── SegmentedPicker ───────────────────────────────────────────────────────────
// FIX: Original used ButtonGroupDefaults + ToggleButton (Material3 Expressive APIs)
// which are not available in the Material3 version Everlasting uses.
// Replaced with FilterChip row — identical visual result, no Expressive APIs needed.
@Composable
fun <T> SegmentedPicker(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    iconProvider: (@Composable (T) -> Unit)? = null,
    modifier: Modifier = Modifier,
    cornerShape: androidx.compose.foundation.shape.CornerSize =
        MaterialTheme.shapes.extraSmall.bottomEnd
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = selectedItem == item,
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onItemSelected(item)
                },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        iconProvider?.let { it(item); Spacer(Modifier.width(4.dp)) }
                        Text(labelProvider(item))
                    }
                },
                leadingIcon = if (selectedItem == item) ({
                    Icon(Icons.Default.Check, null, Modifier.size(14.dp))
                }) else null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── ConfigSliderItem ──────────────────────────────────────────────────────────
@Composable
fun ConfigSliderItem(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    increment: Float = 0.1f,
    valueFormatter: (Float) -> String = { "%.0f".format(it) },
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceBright,
                RoundedCornerShape(MaterialTheme.shapes.extraSmall.bottomEnd)
            )
            .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
    ) {
        Text(
            "$title: ${valueFormatter(value)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    val v = (BigDecimal.valueOf(value.toDouble())
                        .subtract(BigDecimal.valueOf(increment.toDouble()))
                        .setScale(2, RoundingMode.HALF_UP)).toFloat()
                    onValueChange(v.coerceIn(valueRange))
                    onValueChangeFinished?.invoke()
                },
                modifier = Modifier.padding(end = 4.dp), enabled = enabled
            ) {
                Icon(painterResource(R.drawable.rounded_remove_24), "Decrease",
                    tint = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = value, onValueChange = onValueChange, valueRange = valueRange,
                steps = steps, onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier.weight(1f), enabled = enabled
            )
            IconButton(
                onClick = {
                    val v = (BigDecimal.valueOf(value.toDouble())
                        .add(BigDecimal.valueOf(increment.toDouble()))
                        .setScale(2, RoundingMode.HALF_UP)).toFloat()
                    onValueChange(v.coerceIn(valueRange))
                    onValueChangeFinished?.invoke()
                },
                modifier = Modifier.padding(start = 4.dp), enabled = enabled
            ) {
                Icon(painterResource(R.drawable.rounded_add_24), "Increase",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ── ReusableTopAppBar ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReusableTopAppBar(
    title: Any,
    hasBack: Boolean = false,
    isSmall: Boolean = true,
    onBackClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val view = LocalView.current
    val titleStr = when (title) {
        is Int    -> stringResource(title)
        is String -> title
        else      -> ""
    }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
        title = { Text(titleStr, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (hasBack) {
                IconButton(onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onBackClick?.invoke()
                }) { Icon(Icons.Default.ArrowBack, "Back") }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior
    )
}
