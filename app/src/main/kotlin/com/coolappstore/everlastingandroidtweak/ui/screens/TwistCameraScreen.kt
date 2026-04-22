package com.coolappstore.everlastingandroidtweak.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.services.EverlastingForegroundService
import com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar
import com.coolappstore.everlastingandroidtweak.ui.components.SectionHeader
import com.coolappstore.everlastingandroidtweak.ui.components.ToggleSettingRow
import kotlinx.coroutines.launch

@Composable
fun TwistCameraScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by AppPreferences.get(AppPreferences.TWIST_CAMERA_ENABLED, false).collectAsState(false)
    val proximityEnabled by AppPreferences.get(AppPreferences.TWIST_PROXIMITY_ENABLED, false).collectAsState(false)
    val savedSensitivity by AppPreferences.get(AppPreferences.TWIST_SENSITIVITY, 3.5f).collectAsState(3.5f)
    var sensitivity by remember { mutableFloatStateOf(3.5f) }
    LaunchedEffect(savedSensitivity) { sensitivity = savedSensitivity }

    LaunchedEffect(enabled) { if (enabled) EverlastingForegroundService.start(context) }

    Scaffold(topBar = { EverlastingTopBar("Twist for Camera", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(20.dp)) {
                    Icon(Icons.Default.CameraAlt, null, Modifier.size(40.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(if (enabled) "Active — Twist to Open Camera" else "Twist Wrist to Open Camera", style = MaterialTheme.typography.titleMedium)
                        Text("Rotate your wrist twice quickly", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ToggleSettingRow("Enable Twist Gesture", "Double wrist twist opens camera", enabled,
                { scope.launch { AppPreferences.set(AppPreferences.TWIST_CAMERA_ENABLED, it) } })
            HorizontalDivider()
            ToggleSettingRow("Proximity Guard", "Only trigger when phone is in pocket/hand (proximity sensor covered)", proximityEnabled,
                { scope.launch { AppPreferences.set(AppPreferences.TWIST_PROXIMITY_ENABLED, it) } })
            HorizontalDivider()
            SectionHeader("Sensitivity")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val label = when {
                    sensitivity < 0.6f -> "Ultra Sensitive"
                    sensitivity < 1.2f -> "Very Sensitive"
                    sensitivity < 2.5f -> "Sensitive"
                    sensitivity < 4f   -> "Default"
                    sensitivity < 6f   -> "Firm"
                    else -> "Strong Twist Only"
                }
                Text("$label (${"%.2f".format(sensitivity)} rad/s)", style = MaterialTheme.typography.bodyMedium)
                Slider(value = sensitivity, onValueChange = {
                    sensitivity = it
                    scope.launch { AppPreferences.set(AppPreferences.TWIST_SENSITIVITY, it) }
                }, valueRange = 0.3f..8f, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ultra sensitive (0.3)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Strong snap (8.0)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text("Uses gyroscope sensor. Proximity Guard prevents accidental triggers when phone is resting on a surface.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
