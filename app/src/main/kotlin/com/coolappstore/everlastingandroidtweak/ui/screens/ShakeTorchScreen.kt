package com.coolappstore.everlastingandroidtweak.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun ShakeTorchScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by AppPreferences.get(AppPreferences.SHAKE_TORCH_ENABLED, false).collectAsState(false)
    val proximityEnabled by AppPreferences.get(AppPreferences.SHAKE_PROXIMITY_ENABLED, false).collectAsState(false)
    val savedSensitivity by AppPreferences.get(AppPreferences.SHAKE_SENSITIVITY, 12f).collectAsState(12f)
    var sensitivity by remember { mutableFloatStateOf(12f) }
    LaunchedEffect(savedSensitivity) { sensitivity = savedSensitivity }

    LaunchedEffect(enabled) { if (enabled) EverlastingForegroundService.start(context) }

    Scaffold(topBar = { EverlastingTopBar("Shake for Torch", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(20.dp)) {
                    Icon(Icons.Default.FlashOn, null, Modifier.size(40.dp),
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(if (enabled) "Active — Shake to Toggle Torch" else "Shake to Toggle Torch", style = MaterialTheme.typography.titleMedium)
                        Text("Shake your phone to turn flashlight on/off", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ToggleSettingRow("Enable Shake Torch", "Shake to toggle flashlight", enabled,
                { scope.launch { AppPreferences.set(AppPreferences.SHAKE_TORCH_ENABLED, it) } })
            HorizontalDivider()
            ToggleSettingRow("Proximity Guard", "Block torch when phone is in pocket — only triggers when phone is free/open", proximityEnabled,
                { scope.launch { AppPreferences.set(AppPreferences.SHAKE_PROXIMITY_ENABLED, it) } })
            HorizontalDivider()
            SectionHeader("Sensitivity")
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val label = when {
                    sensitivity < 3f  -> "Ultra Sensitive"
                    sensitivity < 6f  -> "Very Sensitive"
                    sensitivity < 10f -> "Sensitive"
                    sensitivity < 15f -> "Default"
                    sensitivity < 25f -> "Firm Shake"
                    sensitivity < 35f -> "Hard Shake"
                    else              -> "Very Hard Shake"
                }
                Text("$label (${"%.1f".format(sensitivity)} m/s²)", style = MaterialTheme.typography.bodyMedium)
                Slider(value = sensitivity, onValueChange = {
                    sensitivity = it
                    scope.launch { AppPreferences.set(AppPreferences.SHAKE_SENSITIVITY, it) }
                }, valueRange = 1f..50f, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ultra (1)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Very hard (50)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
            Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text("Uses accelerometer. Proximity Guard is ON = torch only triggers when phone is NOT in pocket/covered. Sensor registers only while shaking to save battery.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
