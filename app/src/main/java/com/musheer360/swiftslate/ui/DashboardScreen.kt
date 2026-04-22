package com.musheer360.swiftslate.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.ui.components.ExtraLargeShape
import com.musheer360.swiftslate.ui.components.IconBox
import com.musheer360.swiftslate.ui.components.slateCardColors

private const val COMBINED_SERVICE_CLASS =
    "com.coolappstore.everlastingandroidtweak.services.EverlastingAccessibilityService"

private fun checkServiceEnabled(context: Context): Boolean {
    return try {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        flat.split(":").any { it.trim().contains(COMBINED_SERVICE_CLASS) }
    } catch (_: Exception) { false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val context        = LocalContext.current
    val haptic         = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyManager     = remember { KeyManager(context) }

    var isServiceEnabled by remember { mutableStateOf(checkServiceEnabled(context)) }
    var keyCount         by remember { mutableIntStateOf(keyManager.getKeys().size) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = checkServiceEnabled(context)
                keyCount         = keyManager.getKeys().size
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SwiftSlate AI", fontWeight = FontWeight.Bold)
                        Text(
                            "AI Writing Assistant",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconBox(color = Color(0xFF7C4DFF), modifier = Modifier.padding(start = 12.dp)) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null,
                            tint = Color(0xFF7C4DFF), modifier = Modifier.size(22.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top    = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start  = 16.dp,
                end    = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // ── Service Status ───────────────────────────────────────────────
            item {
                Text(
                    text     = "Status",
                    style    = MaterialTheme.typography.labelLarge,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = ExtraLargeShape,
                    colors   = slateCardColors(),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    ListItem(
                        headlineContent = { Text("Service Status", fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(
                                if (isServiceEnabled) "Everlasting Accessibility is running"
                                else "Accessibility service not enabled",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            val dotColor = if (isServiceEnabled) MaterialTheme.colorScheme.tertiary
                                           else MaterialTheme.colorScheme.error
                            IconBox(color = dotColor) {
                                Icon(
                                    imageVector = if (isServiceEnabled) Icons.Filled.CheckCircle else Icons.Filled.Error,
                                    contentDescription = null,
                                    tint     = dotColor,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        },
                        trailingContent = {
                            Text(
                                text  = if (isServiceEnabled) "Active" else "Inactive",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isServiceEnabled) MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    AnimatedVisibility(visible = !isServiceEnabled, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
                        Column {
                            HorizontalDivider(
                                modifier  = Modifier.padding(horizontal = 20.dp),
                                color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                thickness = 0.5.dp,
                            )
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text  = "Tap Enable → find \"Everlasting Tweak Accessibility\" → turn it on → come back.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        context.startActivity(
                                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                                    ),
                                ) {
                                    Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open Accessibility Settings", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── API Keys ─────────────────────────────────────────────────────
            item {
                Text(
                    text     = "API Keys",
                    style    = MaterialTheme.typography.labelLarge,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = ExtraLargeShape,
                    colors    = slateCardColors(),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    ListItem(
                        headlineContent   = { Text("API Keys Configured", fontWeight = FontWeight.Medium) },
                        supportingContent = {
                            Text(
                                if (keyCount == 0) "Go to the Keys tab to add a Gemini API key."
                                else "$keyCount key${if (keyCount == 1) "" else "s"} saved",
                                color = if (keyCount == 0) MaterialTheme.colorScheme.tertiaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            IconBox(color = Color(0xFF1976D2)) {
                                Icon(Icons.Filled.Key, null, tint = Color(0xFF1976D2), modifier = Modifier.size(22.dp))
                            }
                        },
                        trailingContent = {
                            Text(
                                text  = "$keyCount",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (keyCount > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── How to use ───────────────────────────────────────────────────
            item {
                Text(
                    text     = "How to use",
                    style    = MaterialTheme.typography.labelLarge,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
                Card(
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = ExtraLargeShape,
                    colors    = slateCardColors(),
                    elevation = CardDefaults.cardElevation(0.dp),
                ) {
                    val steps = listOf(
                        "Enable \"Everlasting Tweak Accessibility\" in Accessibility Settings.",
                        "Add at least one API key in the Keys tab.",
                        "Type anywhere in Android, ending with a trigger like '?fix' or '?casual'.",
                        "Wait a moment — the text is replaced automatically!",
                    )
                    steps.forEachIndexed { i, step ->
                        ListItem(
                            headlineContent = {
                                Text(step, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            leadingContent = {
                                Box(
                                    modifier         = Modifier.size(28.dp).clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text  = "${i + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                        if (i < steps.lastIndex) HorizontalDivider(
                            modifier  = Modifier.padding(horizontal = 56.dp),
                            color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}
