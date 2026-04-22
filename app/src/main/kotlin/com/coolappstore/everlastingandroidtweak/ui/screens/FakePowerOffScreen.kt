package com.coolappstore.everlastingandroidtweak.ui.screens

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.ui.components.*
import com.coolappstore.everlastingandroidtweak.utils.PermissionManager
import kotlinx.coroutines.launch

@Composable
fun FakePowerOffScreen(navController: NavController) {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val enabled         by AppPreferences.get(AppPreferences.FAKE_POWER_OFF_ENABLED,          false).collectAsState(false)
    val lockDevice      by AppPreferences.get(AppPreferences.FAKE_POWER_OFF_LOCK_DEVICE,      false).collectAsState(false)
    val dndEnabled      by AppPreferences.get(AppPreferences.FAKE_POWER_OFF_DND,              false).collectAsState(false)
    val dismissSequence by AppPreferences.get(AppPreferences.FAKE_POWER_OFF_DISMISS_SEQUENCE, "UUDD").collectAsState("UUDD")

    // ── Permission states — re-read on every resume so they reflect reality ──
    var accessibilityActive by remember { mutableStateOf(PermissionManager.isAccessibilityEnabled(context)) }
    var hasDndPermission    by remember {
        val nm = context.getSystemService(NotificationManager::class.java)
        mutableStateOf(nm?.isNotificationPolicyAccessGranted == true)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityActive = PermissionManager.isAccessibilityEnabled(context)
                val nm = context.getSystemService(NotificationManager::class.java)
                hasDndPermission = nm?.isNotificationPolicyAccessGranted == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { EverlastingTopBar("Fake Power Off", navController) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Hero card ─────────────────────────────────────────────────
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
                        Text("😴", fontSize = 32.sp)
                        Column {
                            Text(
                                "Fake Power Off",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Intercepts the power menu and shows a convincing fake shutdown screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }

            // ── Required permissions ──────────────────────────────────────
            // Always shown — core requirement for the feature to work at all
            if (!accessibilityActive) {
                InfoCard(
                    icon = Icons.Default.Accessibility,
                    title = "Accessibility Service Required",
                    subtitle = "Enable the Everlasting accessibility service. This is needed to detect and intercept the system power menu.",
                    isError = true,
                    actionLabel = "Enable",
                    onAction = { PermissionManager.openAccessibilitySettings(context) }
                )
            }

            // Shown only when the DND toggle is on and the permission is missing
            if (dndEnabled && !hasDndPermission) {
                InfoCard(
                    icon = Icons.Default.DoNotDisturb,
                    title = "Do Not Disturb Access Required",
                    subtitle = "Grant DND access so Fake Power Off can silence notifications when triggered.",
                    isError = true,
                    actionLabel = "Grant",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            // ── Enable toggle ─────────────────────────────────────────────
            FeatureSection("Fake Power Off") {
                ToggleSettingRow(
                    "Enable Fake Power Off",
                    "Intercepts the system power menu and shows a fake shutdown screen",
                    enabled,
                    { scope.launch { AppPreferences.set(AppPreferences.FAKE_POWER_OFF_ENABLED, it) } }
                )
            }

            // ── Permission status summary ─────────────────────────────────
            FeatureSection("Required Permissions") {
                PermissionStatusRow(
                    label = "Accessibility Service",
                    description = "Detects and intercepts the power menu",
                    granted = accessibilityActive,
                    onFix = { PermissionManager.openAccessibilitySettings(context) }
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                PermissionStatusRow(
                    label = "Do Not Disturb Access",
                    description = "Required only if \"Enable DND\" option is on",
                    granted = hasDndPermission,
                    onFix = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
            }

            // ── Options ───────────────────────────────────────────────────
            FeatureSection("Options") {
                ToggleSettingRow(
                    "Lock Device",
                    "Lock the screen after the fake shutdown animation finishes",
                    lockDevice,
                    { scope.launch { AppPreferences.set(AppPreferences.FAKE_POWER_OFF_LOCK_DEVICE, it) } }
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                ToggleSettingRow(
                    "Enable Do Not Disturb",
                    if (hasDndPermission) "Silence notifications when the fake shutdown triggers"
                    else "Silence notifications when triggered — DND access required",
                    dndEnabled,
                    {
                        if (!hasDndPermission) {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else {
                            scope.launch { AppPreferences.set(AppPreferences.FAKE_POWER_OFF_DND, it) }
                        }
                    }
                )
            }

            // ── Dismiss sequence ──────────────────────────────────────────
            FeatureSection("Dismiss Sequence") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Volume-key sequence to dismiss the overlay",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Use U (Volume Up) and D (Volume Down). Default: UUDD",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dismissSequence,
                        onValueChange = { new ->
                            val filtered = new.uppercase().filter { it == 'U' || it == 'D' }
                            scope.launch {
                                AppPreferences.set(AppPreferences.FAKE_POWER_OFF_DISMISS_SEQUENCE, filtered)
                            }
                        },
                        label = { Text("Sequence (U/D)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── How it works ──────────────────────────────────────────────
            FeatureSection("How It Works") {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HowItWorksStep("1", "Long-press the power button to open the power menu")
                    HowItWorksStep("2", "Everlasting detects it and instantly replaces it with a fake version")
                    HowItWorksStep("3", "Tapping Power Off, Restart, or Emergency triggers the fake shutdown screen")
                    HowItWorksStep("4", "Enter your dismiss sequence (e.g. UUDD) with the volume keys to exit")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Reusable permission row ───────────────────────────────────────────────────
@Composable
private fun PermissionStatusRow(
    label: String,
    description: String,
    granted: Boolean,
    onFix: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(22.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!granted) {
            TextButton(onClick = onFix, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("Fix", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HowItWorksStep(number: String, description: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
