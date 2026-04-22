package com.coolappstore.everlastingandroidtweak.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.shizuku.ShizukuManager
import rikka.shizuku.Shizuku

// ── Data ──────────────────────────────────────────────────────────────────────

private data class ShizukuFeatureInfo(
    val name: String,
    val description: String,
    val icon: ImageVector
)

private val SHIZUKU_POWERED_FEATURES = listOf(
    ShizukuFeatureInfo("App Freezer",       "Force-stop any app instantly via shell",              Icons.Default.AcUnit),
    ShizukuFeatureInfo("Cache Cleaner",     "Clear app caches with elevated access",               Icons.Default.CleaningServices),
    ShizukuFeatureInfo("Maps Power Saving", "Launch Maps MinMode via privileged am start",         Icons.Default.Map),
    ShizukuFeatureInfo("Task Manager",      "Kill background processes with am kill",              Icons.Default.Memory),
    ShizukuFeatureInfo("Terminal",          "Run any shell command at adb privilege level",        Icons.Default.Terminal),
)

// ── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuScreen(navController: NavController) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-poll state every time the screen resumes (user may have started Shizuku)
    var isRunning  by remember { mutableStateOf(ShizukuManager.isRunning()) }
    var hasPermission by remember { mutableStateOf(ShizukuManager.hasPermission()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isRunning     = ShizukuManager.isRunning()
                hasPermission = ShizukuManager.hasPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Also listen for Shizuku binder events in real-time
    DisposableEffect(Unit) {
        val binderReceived = Shizuku.OnBinderReceivedListener {
            isRunning     = true
            hasPermission = ShizukuManager.hasPermission()
        }
        val binderDead = Shizuku.OnBinderDeadListener {
            isRunning     = false
            hasPermission = false
        }
        val permResult = Shizuku.OnRequestPermissionResultListener { _, result ->
            hasPermission = result == PackageManager.PERMISSION_GRANTED
        }
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permResult)
        onDispose {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permResult)
        }
    }

    val isReady = isRunning && hasPermission

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Shizuku", fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                isReady       -> "Active & ready"
                                isRunning     -> "Running — permission needed"
                                else          -> "Not running"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                isReady   -> Color(0xFF4CAF50)
                                isRunning -> Color(0xFFFF9800)
                                else      -> MaterialTheme.colorScheme.error
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Status hero card ──────────────────────────────────────────────
            StatusHeroCard(isRunning = isRunning, hasPermission = hasPermission)

            // ── Action buttons ────────────────────────────────────────────────
            ActionButtons(
                context       = context,
                isRunning     = isRunning,
                hasPermission = hasPermission,
                onPermissionRequested = {
                    ShizukuManager.requestPermission()
                }
            )

            // ── How to start Shizuku ──────────────────────────────────────────
            if (!isRunning) {
                HowToStartCard(context)
            }

            // ── Features powered by Shizuku ───────────────────────────────────
            ShizukuFeaturesCard(isReady = isReady)

            // ── About ─────────────────────────────────────────────────────────
            AboutCard()
        }
    }
}

// ── Status hero ───────────────────────────────────────────────────────────────

@Composable
private fun StatusHeroCard(isRunning: Boolean, hasPermission: Boolean) {
    val isReady = isRunning && hasPermission

    val pulseFraction = remember { Animatable(0.85f) }
    LaunchedEffect(isReady) {
        if (isReady) {
            pulseFraction.animateTo(
                1f,
                animationSpec = infiniteRepeatable(
                    tween(900, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                )
            )
        } else {
            pulseFraction.snapTo(0.85f)
        }
    }

    val dotColor = when {
        isReady   -> Color(0xFF4CAF50)
        isRunning -> Color(0xFFFF9800)
        else      -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = dotColor.copy(alpha = 0.10f)
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Animated status dot
            Box(contentAlignment = Alignment.Center) {
                if (isReady) {
                    Box(
                        Modifier
                            .size((52 * pulseFraction.value).dp)
                            .clip(CircleShape)
                            .background(dotColor.copy(alpha = 0.20f))
                    )
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.18f))
                        .border(2.dp, dotColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            isReady   -> Icons.Default.CheckCircle
                            isRunning -> Icons.Default.Warning
                            else      -> Icons.Default.Cancel
                        },
                        contentDescription = null,
                        tint = dotColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                when {
                    isReady   -> "Shizuku is Active"
                    isRunning -> "Shizuku Running"
                    else      -> "Shizuku Not Running"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                when {
                    isReady   -> "All Shizuku-powered features are unlocked and ready to use."
                    isRunning -> "Shizuku service is running. Tap 'Grant Permission' to unlock all features."
                    else      -> "Start the Shizuku service via ADB or root to unlock advanced features."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Inline status pills
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    label = "Service",
                    ok    = isRunning,
                    okText    = "Running",
                    notOkText = "Stopped"
                )
                StatusPill(
                    label = "Permission",
                    ok    = hasPermission,
                    okText    = "Granted",
                    notOkText = "Not granted"
                )
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, ok: Boolean, okText: String, notOkText: String) {
    val color = if (ok) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(
                "$label: ${if (ok) okText else notOkText}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Action buttons ────────────────────────────────────────────────────────────

@Composable
private fun ActionButtons(
    context: Context,
    isRunning: Boolean,
    hasPermission: Boolean,
    onPermissionRequested: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

        if (!isRunning) {
            // Install / open Shizuku
            Button(
                onClick = { openShizukuPlayStore(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Install / Open Shizuku App")
            }
        }

        if (isRunning && !hasPermission) {
            Button(
                onClick = onPermissionRequested,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                )
            ) {
                Icon(Icons.Default.VpnKey, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Grant Permission")
            }
        }

        if (isRunning) {
            OutlinedButton(
                onClick = { openShizukuApp(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open Shizuku App")
            }
        }
    }
}

// ── How to start ──────────────────────────────────────────────────────────────

@Composable
private fun HowToStartCard(context: Context) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .fillMaxWidth()
            ) {
                Icon(Icons.Default.HelpOutline, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("How to start Shizuku",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HowToStep(
                        step  = "1",
                        title = "Install Shizuku",
                        body  = "Download from Google Play or GitHub. Open the app once after installing.",
                        action = "Get Shizuku",
                        onAction = { openShizukuPlayStore(context) }
                    )
                    HowToStep(
                        step  = "2a",
                        title = "Start via ADB (no root needed)",
                        body  = "Enable USB Debugging in Developer Options, connect to PC, then run:\n\nadb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                        action = null
                    )
                    HowToStep(
                        step  = "2b",
                        title = "Start via Wireless ADB (Android 11+)",
                        body  = "Go to Developer Options → Wireless Debugging, open Shizuku app and tap 'Pair via Wireless Debugging'.",
                        action = null
                    )
                    HowToStep(
                        step  = "2c",
                        title = "Start via Root",
                        body  = "If your device is rooted, open Shizuku and tap 'Start via root' — no ADB needed.",
                        action = null
                    )
                    HowToStep(
                        step  = "3",
                        title = "Grant permission to this app",
                        body  = "Tap the 'Grant Permission' button above. A dialog from Shizuku will appear — tap Allow.",
                        action = null
                    )
                }
            }
        }
    }
}

@Composable
private fun HowToStep(
    step: String, title: String, body: String,
    action: String?, onAction: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(step, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (action != null && onAction != null) {
                    TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                        Text(action, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ── Features powered by Shizuku ───────────────────────────────────────────────

@Composable
private fun ShizukuFeaturesCard(isReady: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("Shizuku-Powered Features",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            SHIZUKU_POWERED_FEATURES.forEachIndexed { idx, feat ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isReady) Color(0xFF2196F3).copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(feat.icon, null,
                            tint = if (isReady) Color(0xFF2196F3)
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(feat.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                        Text(feat.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(
                        if (isReady) Icons.Default.CheckCircle else Icons.Default.Lock,
                        null,
                        tint = if (isReady) Color(0xFF4CAF50)
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (idx < SHIZUKU_POWERED_FEATURES.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(start = 48.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

// ── About card ────────────────────────────────────────────────────────────────

@Composable
private fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Info, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text("About Shizuku", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Shizuku is a free, open-source tool by RikkaApps that lets apps use system APIs " +
                "with shell (ADB) level privileges — without requiring your device to be rooted. " +
                "It works by running a small service process that your app can connect to, " +
                "unlocking advanced operations like force-stopping apps, clearing caches, and " +
                "starting privileged activities.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Shizuku version used: API 13.1.5  •  Made by RikkaW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun openShizukuPlayStore(context: Context) {
    val pkg = "moe.shizuku.privileged.api"
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openShizukuApp(context: Context) {
    val pkg = "moe.shizuku.privileged.api"
    context.packageManager.getLaunchIntentForPackage(pkg)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ?.let { context.startActivity(it) }
        ?: openShizukuPlayStore(context)
}
