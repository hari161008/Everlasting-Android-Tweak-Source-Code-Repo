package com.coolappstore.everlastingandroidtweak.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.utils.PermissionManager

data class PermissionStep(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isSpecial: Boolean,         // true = opens Settings, false = runtime dialog
    val checkGranted: (android.content.Context) -> Boolean,
    val runtimePermissions: List<String> = emptyList(),
    val openSettings: ((android.content.Context) -> Unit)? = null,
    val optional: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSetupScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val steps = buildPermissionSteps()

    // Re-check all permissions every time screen resumes (user may have come back from Settings)
    var checkedStates by remember { mutableStateOf(steps.map { it.checkGranted(context) }) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkedStates = steps.map { it.checkGranted(context) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Runtime permission launcher (for RECORD_AUDIO, POST_NOTIFICATIONS, etc.)
    var currentRuntimePermissions by remember { mutableStateOf<List<String>>(emptyList()) }
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        checkedStates = steps.map { it.checkGranted(context) }
    }

    val allRequiredGranted = steps.filterNot { it.optional }
        .all { it.checkGranted(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Permission Setup", fontWeight = FontWeight.Bold)
                        Text("Required for features to work",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val grantedCount = checkedStates.count { it }
                    LinearProgressIndicator(
                        progress = { grantedCount.toFloat() / steps.size },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("$grantedCount / ${steps.size} permissions granted",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center)
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = allRequiredGranted
                    ) {
                        Text(if (allRequiredGranted) "✓ All Set — Continue" else "Grant required permissions above")
                    }
                    if (!allRequiredGranted) {
                        TextButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Skip (some features may not work)") }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            steps.forEachIndexed { index, step ->
                val granted = checkedStates.getOrElse(index) { false }
                PermissionCard(
                    step = step,
                    granted = granted,
                    onGrant = {
                        if (step.isSpecial) {
                            step.openSettings?.invoke(context)
                        } else {
                            currentRuntimePermissions = step.runtimePermissions
                            runtimeLauncher.launch(step.runtimePermissions.toTypedArray())
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    step: PermissionStep,
    granted: Boolean,
    onGrant: () -> Unit
) {
    val containerColor = when {
        granted -> MaterialTheme.colorScheme.primaryContainer
        step.optional -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.errorContainer
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (granted) Icons.Default.CheckCircle else step.icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = if (granted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(step.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (step.optional) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Optional", style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                }
                Text(step.description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!granted) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onGrant,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            when {
                                !step.isSpecial -> "Grant Permission"
                                step.title.contains("Shizuku", ignoreCase = true) -> "Grant Permission"
                                else -> "Open Settings →"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun buildPermissionSteps(): List<PermissionStep> {
    val runtimePerms = mutableListOf<String>().apply {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    return listOf(
        PermissionStep(
            title = "Basic Permissions",
            description = "Camera, microphone, notifications, and media access. Required for Shake Torch, Music Light, Custom Sounds, and Watermark.",
            icon = Icons.Default.PermMedia,
            isSpecial = false,
            checkGranted = { ctx ->
                PermissionManager.hasRecordAudio(ctx) &&
                PermissionManager.hasCamera(ctx) &&
                PermissionManager.hasPostNotifications(ctx) &&
                PermissionManager.hasReadMediaImages(ctx)
            },
            runtimePermissions = runtimePerms
        ),
        PermissionStep(
            title = "Accessibility Service",
            description = "Powers Custom Haptics, Custom Sounds, Screenshot Blocker, Volume Styles, and Custom Nav Bar. Tap 'Open Settings' → find 'Everlasting Tweak' and enable it.",
            icon = Icons.Default.Accessibility,
            isSpecial = true,
            checkGranted = { ctx -> PermissionManager.isAccessibilityEnabled(ctx) },
            openSettings = { ctx -> PermissionManager.openAccessibilitySettings(ctx) }
        ),
        PermissionStep(
            title = "Display Over Other Apps",
            description = "Required for Custom Nav Bar overlay and Volume Style overlays. Tap 'Open Settings' and enable for this app.",
            icon = Icons.Default.Layers,
            isSpecial = true,
            checkGranted = { ctx -> PermissionManager.hasOverlayPermission(ctx) },
            openSettings = { ctx -> PermissionManager.openOverlaySettings(ctx) }
        ),
        PermissionStep(
            title = "Notification Access",
            description = "Required for Music Reactive Light to detect music playback. Tap 'Open Settings' → find 'Everlasting' and enable.",
            icon = Icons.Default.Notifications,
            isSpecial = true,
            checkGranted = { ctx -> PermissionManager.isNotificationListenerEnabled(ctx) },
            openSettings = { ctx -> PermissionManager.openNotificationListenerSettings(ctx) },
            optional = true
        ),
        PermissionStep(
            title = "Usage Access (Stats)",
            description = "Required for Task Manager to show accurate app memory usage. Tap 'Open Settings' → find this app and allow.",
            icon = Icons.Default.BarChart,
            isSpecial = true,
            checkGranted = { ctx -> PermissionManager.hasUsageStatsPermission(ctx) },
            openSettings = { ctx -> PermissionManager.openUsageAccessSettings(ctx) },
            optional = true
        ),
        PermissionStep(
            title = "Schedule Exact Alarms",
            description = "Required for Auto Reboot scheduling to trigger at precise times. Tap 'Open Settings' → enable for this app.",
            icon = Icons.Default.Alarm,
            isSpecial = true,
            checkGranted = { ctx -> PermissionManager.hasExactAlarmPermission(ctx) },
            openSettings = { ctx -> PermissionManager.openExactAlarmSettings(ctx) },
            optional = true
        ),
        PermissionStep(
            title = "Shizuku Permission",
            description = "Powers App Freezer, Cache Cleaner, Maps Power Saving, and other advanced shell features. Install & start Shizuku, then tap 'Grant Permission'.",
            icon = Icons.Default.Terminal,
            isSpecial = true,           // routes to openSettings → Shizuku dialog
            checkGranted = { _ -> PermissionManager.isShizukuGranted() },
            openSettings = { ctx -> PermissionManager.requestShizukuPermission(ctx) },
            optional = true
        ),
        PermissionStep(
            title = "SwiftSlate AI Assistant",
            description = "Required for SwiftSlate's AI text-replacement to work anywhere you type. Tap 'Open Settings' → find 'SwiftSlate Assistant' under Everlasting and enable it.",
            icon = Icons.Default.AutoFixHigh,
            isSpecial = true,
            checkGranted = { ctx -> PermissionManager.isSwiftSlateServiceEnabled(ctx) },
            openSettings = { ctx -> PermissionManager.openAccessibilitySettings(ctx) },
            optional = true
        )
    )
}

// ── Inline Permission Dialog (used per-feature when toggling) ───────────────

@Composable
fun PermissionRequiredDialog(
    title: String,
    message: String,
    isSpecial: Boolean,
    runtimePermissions: List<String> = emptyList(),
    onOpenSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onGranted: (() -> Unit)? = null
) {
    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) onGranted?.invoke()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = {
                if (isSpecial) {
                    onOpenSettings?.invoke()
                    onDismiss()
                } else {
                    runtimeLauncher.launch(runtimePermissions.toTypedArray())
                }
            }) {
                Text(if (isSpecial) "Open Settings" else "Grant")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
