package com.coolappstore.everlastingandroidtweak.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsPowerSavingManager
import com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsPowerSavingViewModel
import com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar
import com.coolappstore.everlastingandroidtweak.ui.components.InfoCard
import com.coolappstore.everlastingandroidtweak.utils.PermissionManager

@Composable
fun MapsPowerSavingScreen(navController: NavController) {
    val context       = LocalContext.current
    val viewModel     = remember { MapsPowerSavingViewModel.create(context) }
    val enabled       by viewModel.isEnabled
    val channels      by viewModel.mapsChannels

    var hasNotifListener by remember {
        mutableStateOf(PermissionManager.isNotificationListenerEnabled(context))
    }
    var shizukuReady by remember {
        mutableStateOf(MapsPowerSavingManager.isShizukuReady())
    }

    // Refresh permissions and channels on every resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifListener = PermissionManager.isNotificationListenerEnabled(context)
                shizukuReady     = MapsPowerSavingManager.isShizukuReady()
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val allPermissionsGranted = hasNotifListener && shizukuReady

    Scaffold(topBar = { EverlastingTopBar("Maps Power Saving", navController) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {

            // ── Header ─────────────────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🗺️", style = MaterialTheme.typography.displaySmall)
                        Column {
                            Text(
                                "Maps Power Saving",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                when {
                                    enabled && allPermissionsGranted ->
                                        "● ACTIVE — MinMode launches on lock screen"
                                    !allPermissionsGranted ->
                                        "Permissions required — see below"
                                    else ->
                                        "Launches Maps MinMode when screen turns off during navigation"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    enabled && allPermissionsGranted -> MaterialTheme.colorScheme.primary
                                    !allPermissionsGranted -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                }
                            )
                        }
                    }
                }
            }

            // ── How it works ────────────────────────────────────────────────────
            InfoCard(
                icon = Icons.Default.Info,
                title = "How It Works",
                subtitle = "This is a Pixel 10 exclusive feature that works on any Android device. " +
                           "When you turn off the screen during Google Maps navigation, it launches " +
                           "Maps' MinMode activity — a low-power navigation overlay — via Shizuku. " +
                           "You may occasionally see a \"does not support landscape\" message; this is " +
                           "normal and can be ignored.",
                isError = false
            )

            // ── Permission: Shizuku ────────────────────────────────────────────
            if (!shizukuReady) {
                InfoCard(
                    icon = Icons.Default.Terminal,
                    title = "Shizuku Required",
                    subtitle = "Shizuku is needed to launch the Maps MinMode activity with shell " +
                               "privileges. Install Shizuku, then run:\n" +
                               "adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh\n" +
                               "or use wireless ADB in Shizuku's settings.",
                    isError = true,
                    actionLabel = "Get Shizuku",
                    onAction = {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            // ── Permission: Notification Listener ──────────────────────────────
            if (!hasNotifListener) {
                InfoCard(
                    icon = Icons.Default.Notifications,
                    title = "Notification Access Required",
                    subtitle = "Needed to detect when Google Maps navigation is active. " +
                               "Without this, the app cannot know when to trigger MinMode.",
                    isError = true,
                    actionLabel = "Grant Access",
                    onAction = { PermissionManager.openNotificationListenerSettings(context) }
                )
            }

            // ── Enable toggle ──────────────────────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Enable Maps Power Saving",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Auto-trigger MinMode when lock screen appears during navigation",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                if (!allPermissionsGranted) {
                                    if (!hasNotifListener)
                                        PermissionManager.openNotificationListenerSettings(context)
                                    return@Switch
                                }
                                viewModel.setEnabled(it)
                            }
                        )
                    }

                    // Manual test button
                    if (shizukuReady) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        OutlinedButton(
                            onClick = { viewModel.triggerMinModeNow() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Test: Launch MinMode Now")
                        }
                    }
                }
            }

            // ── Navigation detection indicator ────────────────────────────────
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsState.hasNavigationNotification)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsState.hasNavigationNotification)
                            Icons.Default.Navigation
                        else
                            Icons.Default.Navigation,
                        contentDescription = null,
                        tint = if (com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsState.hasNavigationNotification)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            "Navigation Status",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsState.hasNavigationNotification)
                                "● Navigation active — MinMode will launch on next lock screen"
                            else
                                "No navigation detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsState.hasNavigationNotification)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Discovered channels ─────────────────────────────────────────────
            if (channels.isNotEmpty() || hasNotifListener) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Navigation Detection Channels",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp, top = 4.dp)
                )
                Text(
                    "Toggle which Maps notification channels trigger the MinMode launch. " +
                    "Channels are discovered automatically when you use navigation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )

                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (channels.isEmpty()) {
                        Text(
                            "No channels discovered yet. Start a Maps navigation session — " +
                            "channels will appear here automatically.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column {
                            channels.forEachIndexed { idx, channel ->
                                ListItem(
                                    headlineContent = { Text(channel.name) },
                                    supportingContent = {
                                        Text(channel.id,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    },
                                    leadingContent = {
                                        Icon(Icons.Default.Navigation, null,
                                            tint = MaterialTheme.colorScheme.primary)
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = channel.isEnabled,
                                            onCheckedChange = { checked ->
                                                viewModel.setMapsChannelDetected(
                                                    channel.id, checked, context)
                                            }
                                        )
                                    }
                                )
                                if (idx < channels.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
            }

            InfoCard(
                icon = Icons.Default.Lightbulb,
                title = "Tip",
                subtitle = "Tap 'Test: Launch MinMode Now' while in a Maps navigation session " +
                           "to verify it works. You should see the Maps mini navigation overlay appear. " +
                           "From then on, it triggers automatically whenever you turn off your screen.",
                isError = false
            )
        }
    }
}
