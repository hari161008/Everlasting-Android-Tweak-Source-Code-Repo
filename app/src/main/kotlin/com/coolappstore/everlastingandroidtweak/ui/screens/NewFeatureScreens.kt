package com.coolappstore.everlastingandroidtweak.ui.screens

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.features.musicleveler.MusicLevelerOverlayService
import com.coolappstore.everlastingandroidtweak.features.navbar.NavBarOverlayService
import com.coolappstore.everlastingandroidtweak.ui.components.*
import com.coolappstore.everlastingandroidtweak.utils.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.*
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.lifecycle.viewmodel.compose.viewModel

// ─── FLIP TO DND ─────────────────────────────────────────────────────────────
@Composable
fun FlipToDndScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by AppPreferences.get(AppPreferences.FLIP_DND_ENABLED, false).collectAsState(false)
    val nm = context.getSystemService(android.app.NotificationManager::class.java)
    val hasDnd = nm?.isNotificationPolicyAccessGranted == true

    Scaffold(topBar = { EverlastingTopBar("Flip to DND", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("📵 Flip to Silence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Place your phone face-down to automatically activate Do Not Disturb. Flip it back to restore normal mode.", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (!hasDnd) {
                InfoCard(Icons.Default.Warning, "Do Not Disturb Access Required",
                    "Tap Grant to allow this app to manage DND mode.",
                    isError = true, actionLabel = "Grant",
                    onAction = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
            }

            FeatureSection("Flip to DND") {
                ToggleSettingRow("Enable Flip to DND", "Flip face-down → DND on, flip back → DND off", enabled, {
                    if (!hasDnd) return@ToggleSettingRow
                    scope.launch { AppPreferences.set(AppPreferences.FLIP_DND_ENABLED, it) }
                })
            }

            FeatureSection("How it Works") {
                Column(Modifier.padding(16.dp)) {
                    Text("The accelerometer detects when the Z-axis reads below -7 m/s² (face-down). DND is activated immediately. When you flip the phone back up, DND is disabled automatically.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ─── CHARGING SOUND ──────────────────────────────────────────────────────────
@Composable
fun ChargingSoundScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by AppPreferences.get(AppPreferences.CHARGING_SOUND_ENABLED, false).collectAsState(false)
    val soundUri by AppPreferences.get(AppPreferences.CHARGING_SOUND_URI, "").collectAsState("")
    var pickingSound by remember { mutableStateOf(false) }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.toString()?.let { scope.launch { AppPreferences.set(AppPreferences.CHARGING_SOUND_URI, it) } }
    }

    Scaffold(topBar = { EverlastingTopBar("Charging Sound", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🔋 Custom Charging Sound", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Play a custom audio file whenever your phone is plugged in to charge.", style = MaterialTheme.typography.bodySmall)
                }
            }
            FeatureSection("Charging Sound") {
                ToggleSettingRow("Enable Charging Sound", "Play sound when charger is connected", enabled,
                    { scope.launch { AppPreferences.set(AppPreferences.CHARGING_SOUND_ENABLED, it) } })
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Sound File") },
                    supportingContent = {
                        Text(if (soundUri.isEmpty()) "No file selected — tap Browse to pick audio"
                             else Uri.parse(soundUri).lastPathSegment ?: "File selected")
                    },
                    trailingContent = {
                        TextButton(onClick = { audioPicker.launch("audio/*") }) { Text("Browse") }
                    }
                )
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val uri = if (soundUri.isNotEmpty()) android.net.Uri.parse(soundUri)
                              else return@launch
                    try {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                        val vol = am.getStreamVolume(android.media.AudioManager.STREAM_NOTIFICATION).toFloat() / am.getStreamMaxVolume(android.media.AudioManager.STREAM_NOTIFICATION)
                        android.media.MediaPlayer().apply {
                            setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build())
                            setDataSource(context, uri); setVolume(vol, vol); prepare()
                            setOnCompletionListener { release() }; start()
                        }
                    } catch (_: Exception) {}
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
               enabled = soundUri.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Preview Charging Sound")
            }
            InfoCard(Icons.Default.Info, "System Volume",
                "The charging sound plays at your current notification volume level.", isError = false)
        }
    }
}

// ─── VOLUME BOOSTER ──────────────────────────────────────────────────────────
@Composable
fun VolumeBoosterScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by AppPreferences.get(AppPreferences.VOLUME_BOOST_ENABLED, false).collectAsState(false)
    val level by AppPreferences.get(AppPreferences.VOLUME_BOOST_LEVEL, 300).collectAsState(300)
    var sliderVal by remember { mutableFloatStateOf(300f) }
    LaunchedEffect(level) { sliderVal = level.toFloat() }

    Scaffold(topBar = { EverlastingTopBar("Volume Booster", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("🔊 Volume Booster", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Uses Android's LoudnessEnhancer audio effect to amplify media output beyond the system maximum.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable Volume Boost", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Pushes volume to max + amplifies further", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = enabled, onCheckedChange = {
                            scope.launch { AppPreferences.set(AppPreferences.VOLUME_BOOST_ENABLED, it) }
                        })
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Column(Modifier.padding(16.dp)) {
                        val dB = (sliderVal / 100).toInt()
                        val label = when {
                            dB == 0    -> "No boost (0 dB)"
                            dB <= 3    -> "Subtle (+${dB} dB)"
                            dB <= 6    -> "Noticeable (+${dB} dB)"
                            dB <= 10   -> "Strong (+${dB} dB) ⚠️"
                            else       -> "EXTREME (+${dB} dB) 🔊"
                        }
                        Text("Boost Level: $label", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Slider(
                            value = sliderVal,
                            onValueChange = { sliderVal = it
                                scope.launch { AppPreferences.set(AppPreferences.VOLUME_BOOST_LEVEL, it.toInt()) }
                            },
                            valueRange = 0f..15000f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("None", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Max (+1500 dB)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            InfoCard(Icons.Default.Warning, "Speaker Safety Warning",
                "Extremely high boost levels can permanently damage speakers and earphones. Increase gradually and test at low volumes first.", isError = true)
        }
    }
}

// ─── MUSIC LEVELER ───────────────────────────────────────────────────────────
@Composable
fun MusicLevelerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled   by AppPreferences.get(AppPreferences.MUSIC_LEVELER_ENABLED, false).collectAsState(false)
    val colorHex  by AppPreferences.get(AppPreferences.MUSIC_LEVELER_COLOR, "#8BCAFF").collectAsState("#8BCAFF")
    val position  by AppPreferences.get(AppPreferences.MUSIC_LEVELER_POSITION, "Bottom").collectAsState("Bottom")
    val autoHide  by AppPreferences.get(AppPreferences.MUSIC_LEVELER_AUTO_HIDE, true).collectAsState(true)
    val savedH    by AppPreferences.get(AppPreferences.MUSIC_LEVELER_HEIGHT, 56f).collectAsState(56f)
    val savedOp   by AppPreferences.get(AppPreferences.MUSIC_LEVELER_OPACITY, 1f).collectAsState(1f)
    var barHeight by remember { mutableFloatStateOf(56f) }
    var opacity   by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(savedH)  { barHeight = savedH }
    LaunchedEffect(savedOp) { opacity = savedOp }

    var hasOverlay by remember { mutableStateOf(PermissionManager.hasOverlayPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasOverlay = PermissionManager.hasOverlayPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs); onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val presetColors = listOf(
        "#8BCAFF" to "Blue", "#FF5722" to "Red", "#4CAF50" to "Green",
        "#FFC107" to "Amber", "#9C27B0" to "Purple", "#00BCD4" to "Cyan",
        "#FF4081" to "Pink", "#FFFFFF" to "White", "#000000" to "Black",
        "#FF6F00" to "Orange", "#1DE9B6" to "Teal", "#FFD600" to "Gold"
    )

    Scaffold(topBar = { EverlastingTopBar("Music Leveler", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // Header
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🎵", style = MaterialTheme.typography.displaySmall)
                        Column {
                            Text("Music Leveler", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(if (enabled) "● ACTIVE — auto-hides when silent" else "Tap toggle to activate",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (enabled) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            if (!hasOverlay) {
                InfoCard(Icons.Default.Layers, "Overlay Permission Required",
                    "Display Over Other Apps must be granted first.",
                    isError = true, actionLabel = "Grant",
                    onAction = { PermissionManager.openOverlaySettings(context) })
            }

            // Control card
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column {
                    // Enable toggle
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Show Music Leveler", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Animated 32-bar equalizer overlay", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = enabled, onCheckedChange = {
                            if (!hasOverlay) { PermissionManager.openOverlaySettings(context) }
                            else {
                                scope.launch { AppPreferences.set(AppPreferences.MUSIC_LEVELER_ENABLED, it) }
                                if (it) MusicLevelerOverlayService.start(context) else MusicLevelerOverlayService.stop(context)
                            }
                        })
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    // Auto-hide toggle
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-Hide When Silent", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Hides when no audio is playing", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = autoHide, onCheckedChange = {
                            scope.launch { AppPreferences.set(AppPreferences.MUSIC_LEVELER_AUTO_HIDE, it) }
                            if (enabled) { MusicLevelerOverlayService.stop(context); MusicLevelerOverlayService.start(context) }
                        })
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    // Position
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Position", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Top", "Bottom").forEach { pos ->
                                FilterChip(selected = position == pos, onClick = {
                                    scope.launch { AppPreferences.set(AppPreferences.MUSIC_LEVELER_POSITION, pos) }
                                    if (enabled) { MusicLevelerOverlayService.stop(context); MusicLevelerOverlayService.start(context) }
                                }, label = { Text(pos) })
                            }
                        }
                    }
                }
            }

            // Size & Opacity
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Size & Opacity", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Bar Height: ${barHeight.toInt()}dp", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = barHeight, onValueChange = {
                        barHeight = it
                        scope.launch { AppPreferences.set(AppPreferences.MUSIC_LEVELER_HEIGHT, it) }
                        if (enabled) { MusicLevelerOverlayService.stop(context); MusicLevelerOverlayService.start(context) }
                    }, valueRange = 24f..120f, modifier = Modifier.fillMaxWidth())

                    Text("Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = opacity, onValueChange = {
                        opacity = it
                        scope.launch { AppPreferences.set(AppPreferences.MUSIC_LEVELER_OPACITY, it) }
                        if (enabled) { MusicLevelerOverlayService.stop(context); MusicLevelerOverlayService.start(context) }
                    }, valueRange = 0.1f..1f, modifier = Modifier.fillMaxWidth())
                }
            }

            // Color palette
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    // Header row with Full Picker button
                    var showLevelerColorPicker by remember { mutableStateOf(false) }
                    if (showLevelerColorPicker) {
                        EverlastingColorPickerDialog(
                            initialHex = colorHex.ifEmpty { "#8BCAFF" },
                            onDismiss = { showLevelerColorPicker = false },
                            onColorSelected = { hex ->
                                scope.launch { AppPreferences.set(AppPreferences.MUSIC_LEVELER_COLOR, hex) }
                                if (enabled) { MusicLevelerOverlayService.stop(context); MusicLevelerOverlayService.start(context) }
                            }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bar Color", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        FilledTonalButton(
                            onClick = { showLevelerColorPicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Colorize, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Full Picker", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    presetColors.chunked(6).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { (hex, name) ->
                                val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
                                val selected = colorHex.equals(hex, true)
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape).background(color)
                                            .clickable {
                                                scope.launch { AppPreferences.set(AppPreferences.MUSIC_LEVELER_COLOR, hex) }
                                                if (enabled) { MusicLevelerOverlayService.stop(context); MusicLevelerOverlayService.start(context) }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) Icon(Icons.Default.Check, null,
                                            tint = if (hex == "#FFFFFF" || hex == "#FFD600") Color.Black else Color.White,
                                            modifier = Modifier.size(20.dp))
                                    }
                                    Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Apply button
            Button(onClick = {
                if (enabled) { MusicLevelerOverlayService.stop(context); MusicLevelerOverlayService.start(context) }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                enabled = enabled) {
                Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Apply Changes")
            }
        }
    }
}

// ─── SECURITY MOTION ─────────────────────────────────────────────────────────
@Composable
fun SecurityMotionScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled     by AppPreferences.get(AppPreferences.SECURITY_MOTION_ENABLED, false).collectAsState(false)
    val sensitivity by AppPreferences.get(AppPreferences.SECURITY_MOTION_SENSITIVITY, 0.5f).collectAsState(0.5f)
    val soundEnabled by AppPreferences.get(AppPreferences.SECURITY_MOTION_SOUND_ENABLED, true).collectAsState(true)
    val alarmUri    by AppPreferences.get(AppPreferences.SECURITY_MOTION_ALARM_URI, "").collectAsState("")
    var sliderVal   by remember { mutableFloatStateOf(0.5f) }
    LaunchedEffect(sensitivity) { sliderVal = sensitivity }
    var alarmActive by remember { mutableStateOf(false) }
    var countdown   by remember { mutableIntStateOf(0) }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(Unit) { onDispose { mediaPlayer?.release() } }

    fun playAlarm() {
        if (!soundEnabled) return
        try {
            val uri = if (alarmUri.isNotEmpty()) android.net.Uri.parse(alarmUri)
                      else android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
                           ?: return
            mediaPlayer?.release()
            mediaPlayer = android.media.MediaPlayer().apply {
                setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM).build())
                setDataSource(context, uri)
                isLooping = true; prepare(); start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }
    fun stopAlarm() { try { mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null } catch (_: Exception) {} }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.toString()?.let { scope.launch { AppPreferences.set(AppPreferences.SECURITY_MOTION_ALARM_URI, it) } }
    }

    LaunchedEffect(enabled) {
        if (enabled) {
            countdown = 5
            while (countdown > 0) { delay(1000); countdown-- }
            alarmActive = true
        } else {
            alarmActive = false; countdown = 0; stopAlarm()
        }
    }

    // Wire alarm to Services SecurityMotionManager
    LaunchedEffect(alarmActive) {
        if (alarmActive) playAlarm() else stopAlarm()
    }

    Scaffold(topBar = { EverlastingTopBar("Security Motion Alert", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // Status card
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        alarmActive -> MaterialTheme.colorScheme.errorContainer
                        countdown > 0 -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(when {
                        alarmActive -> "🚨"
                        countdown > 0 -> "⏱️"
                        else -> "🔐"
                    }, style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(8.dp))
                    Text(when {
                        countdown > 0 -> "Arming in ${countdown}s — place your device"
                        alarmActive -> "🔴 ALARM — Device was moved!"
                        else -> "Motion Guard — Ready to Arm"
                    }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center)
                    if (alarmActive) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { scope.launch { AppPreferences.set(AppPreferences.SECURITY_MOTION_ENABLED, false) }; alarmActive = false; stopAlarm() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Icon(Icons.Default.Close, null); Spacer(Modifier.width(4.dp)); Text("Stop Alarm")
                        }
                    }
                }
            }

            // Enable toggle
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable Motion Guard", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Sound alarm if device is moved", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = enabled, onCheckedChange = {
                            scope.launch { AppPreferences.set(AppPreferences.SECURITY_MOTION_ENABLED, it) }
                        })
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Column(Modifier.padding(16.dp)) {
                        val label = when {
                            sliderVal < 0.3f -> "Low — only large movements"
                            sliderVal < 0.6f -> "Medium — balanced"
                            sliderVal < 0.8f -> "High — sensitive"
                            else -> "Max — any slight movement"
                        }
                        Text("Sensitivity: $label", style = MaterialTheme.typography.bodyMedium)
                        Slider(value = sliderVal, onValueChange = {
                            sliderVal = it; scope.launch { AppPreferences.set(AppPreferences.SECURITY_MOTION_SENSITIVITY, it) }
                        }, valueRange = 0.1f..1f, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Alarm sound settings
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Alarm Sound", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Play loud alarm when motion detected", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = soundEnabled, onCheckedChange = {
                            scope.launch { AppPreferences.set(AppPreferences.SECURITY_MOTION_SOUND_ENABLED, it) }
                        })
                    }
                    if (soundEnabled) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Custom Sound", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(if (alarmUri.isEmpty()) "Default alarm ringtone"
                                     else android.net.Uri.parse(alarmUri).lastPathSegment ?: "Custom file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (alarmUri.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.primary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (alarmUri.isNotEmpty()) {
                                    OutlinedButton(onClick = {
                                        scope.launch { AppPreferences.set(AppPreferences.SECURITY_MOTION_ALARM_URI, "") }
                                    }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text("Reset") }
                                }
                                FilledTonalButton(onClick = { soundPicker.launch("audio/*") },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                                    Icon(Icons.Default.MusicNote, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp)); Text("Browse")
                                }
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        OutlinedButton(onClick = { playAlarm() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Test Alarm Sound")
                        }
                    }
                }
            }

            InfoCard(Icons.Default.Info, "5-Second Arm Delay",
                "After enabling, you have 5 seconds to place the device. The alarm will sound and your screen will alert you if motion is detected.", isError = false)
        }
    }
}

// ─── WALKIE TALKIE ───────────────────────────────────────────────────────────
// ChatMessage is now defined in WalkieTalkieManager.kt

@Composable
fun WalkieTalkieScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isTalking    by remember { mutableStateOf(false) }
    var chatMessages by remember { mutableStateOf(listOf<com.coolappstore.everlastingandroidtweak.features.walkietalkie.ChatMessage>()) }
    var chatText     by remember { mutableStateOf("") }
    var activeTab    by remember { mutableStateOf(0) }
    var hasAudio     by remember { mutableStateOf(PermissionManager.hasRecordAudio(context)) }
    var targetIp     by remember { mutableStateOf("") }
    var myIp         by remember { mutableStateOf("") }

    val audioPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasAudio = it }

    val manager = remember {
        com.coolappstore.everlastingandroidtweak.features.walkietalkie.WalkieTalkieManager(context)
    }

    val sdf = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }

    // Auto-detect IP and start listeners
    LaunchedEffect(Unit) {
        myIp = manager.getLocalIp()
        manager.statusCallback = {}
        manager.onMessageReceived = { msg ->
            chatMessages = chatMessages + msg
        }
        manager.startListening()
        manager.startChatServer()
    }

    DisposableEffect(Unit) {
        onDispose { manager.release() }
    }

    // Wire target IP to manager
    LaunchedEffect(targetIp) {
        manager.setTarget(targetIp)
    }

    Scaffold(topBar = { EverlastingTopBar("Walkie Talkie", navController) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = activeTab, modifier = Modifier.fillMaxWidth()) {
                listOf("🎙️ Talk", "💬 Chat", "⚙️ Setup").forEachIndexed { i, title ->
                    Tab(selected = activeTab == i, onClick = { activeTab = i },
                        text = { Text(title, style = MaterialTheme.typography.labelMedium) })
                }
            }

            when (activeTab) {
                // ── TALK TAB ─────────────────────────────────────────────────
                0 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isTalking) MaterialTheme.colorScheme.errorContainer
                                             else MaterialTheme.colorScheme.primaryContainer),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isTalking) "🔴" else "🟢", style = MaterialTheme.typography.displaySmall)
                            Text(if (isTalking) "Transmitting…" else "Ready — tap to talk",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (myIp.isNotEmpty()) Text("Your IP: $myIp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                            Text(
                                if (targetIp.isBlank()) "Broadcasting to all devices on network"
                                else "Targeting: $targetIp",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                    }

                    if (!hasAudio) {
                        Button(onClick = { audioPerm.launch(android.Manifest.permission.RECORD_AUDIO) },
                            modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Mic, null); Spacer(Modifier.width(6.dp)); Text("Grant Microphone")
                        }
                    }

                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = {
                                if (!hasAudio) return@Button
                                isTalking = !isTalking
                                if (isTalking) manager.startTalking() else manager.stopTalking()
                            },
                            modifier = Modifier.size(140.dp).clip(CircleShape),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTalking) MaterialTheme.colorScheme.error
                                                 else MaterialTheme.colorScheme.primary),
                            enabled = hasAudio
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (isTalking) Icons.Default.MicOff else Icons.Default.Mic,
                                    null, Modifier.size(40.dp))
                                Text(if (isTalking) "Release" else "Push & Talk",
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Text(
                        if (!hasAudio) "⚠️ Microphone permission required"
                        else "Both phones must be on the same Wi-Fi network or hotspot. No IP setup needed!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // ── CHAT TAB ─────────────────────────────────────────────────
                1 -> Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        Modifier.weight(1f).padding(horizontal = 12.dp),
                        reverseLayout = false,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (chatMessages.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("💬", style = MaterialTheme.typography.displaySmall)
                                        Text("Messages appear here when received",
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            items(chatMessages.size) { i ->
                                val msg = chatMessages[i]
                                Row(Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (msg.isMine) Arrangement.End else Arrangement.Start) {
                                    Card(
                                        shape = MaterialTheme.shapes.large,
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (msg.isMine) MaterialTheme.colorScheme.primary
                                                             else MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.widthIn(max = 280.dp)
                                    ) {
                                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            Text(msg.text, color = if (msg.isMine) MaterialTheme.colorScheme.onPrimary
                                                                    else MaterialTheme.colorScheme.onSurface)
                                            Text(msg.time, style = MaterialTheme.typography.labelSmall,
                                                color = if (msg.isMine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(8.dp).imePadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = chatText, onValueChange = { chatText = it },
                            placeholder = { Text("Type a message…") },
                            modifier = Modifier.weight(1f), maxLines = 3,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                        FloatingActionButton(
                            onClick = {
                                if (chatText.isNotBlank()) {
                                    val time = sdf.format(java.util.Date())
                                    val msg = com.coolappstore.everlastingandroidtweak.features.walkietalkie.ChatMessage(chatText.trim(), true, time)
                                    chatMessages = chatMessages + msg
                                    manager.sendChatMessage(chatText.trim())
                                    chatText = ""
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            containerColor = MaterialTheme.colorScheme.primary
                        ) { Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp)) }
                    }
                }

                // ── SETUP TAB ─────────────────────────────────────────────────
                2 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp)) {
                            Text("📡 Auto-Connect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            listOf(
                                "✅ No IP setup needed by default",
                                "📶 Both phones must be on the same Wi-Fi or hotspot",
                                "🔊 Voice broadcasts to everyone on the network",
                                "💬 Chat uses direct TCP connection (optional IP)"
                            ).forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(vertical = 2.dp))
                            }
                        }
                    }

                    // Your IP
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Your IP Address", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.background) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (myIp.isEmpty()) "Detecting…" else myIp,
                                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = {
                                        val cb = context.getSystemService(android.content.ClipboardManager::class.java)
                                        cb?.setPrimaryClip(android.content.ClipData.newPlainText("IP", myIp))
                                        android.widget.Toast.makeText(context, "IP copied!", android.widget.Toast.LENGTH_SHORT).show()
                                    }) { Icon(Icons.Default.ContentCopy, null) }
                                }
                            }
                        }
                    }

                    // Optional target IP for direct chat
                    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Wifi, null, tint = MaterialTheme.colorScheme.secondary)
                                Column {
                                    Text("Target IP (Optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text("Leave blank for broadcast mode", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            OutlinedTextField(
                                value = targetIp, onValueChange = { targetIp = it },
                                label = { Text("Other device's IP (e.g. 192.168.1.5)") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Wifi, null) },
                                trailingIcon = {
                                    if (targetIp.isNotBlank())
                                        IconButton(onClick = { targetIp = "" }) { Icon(Icons.Default.Clear, null) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── COMPASS ─────────────────────────────────────────────────────────────────
@Composable
fun CompassScreen(navController: NavController) {
    val context = LocalContext.current
    var azimuth       by remember { mutableFloatStateOf(0f) }
    var pitch         by remember { mutableFloatStateOf(0f) }
    var roll          by remember { mutableFloatStateOf(0f) }
    var magStrength   by remember { mutableFloatStateOf(0f) }
    var compassTheme  by remember { mutableStateOf("Classic") }
    val compassThemes = listOf("Classic", "Neon", "Minimal", "Military", "Space", "Ocean")

    val animAzimuth by animateFloatAsState(
        targetValue = azimuth,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "az"
    )

    val sm = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val gravity = remember { FloatArray(3) }
    val geomag  = remember { FloatArray(3) }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) {
                when (ev.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER  -> gravity.indices.forEach { gravity[it] = ev.values[it] }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        geomag.indices.forEach { geomag[it] = ev.values[it] }
                        magStrength = kotlin.math.sqrt(geomag[0]*geomag[0] + geomag[1]*geomag[1] + geomag[2]*geomag[2])
                    }
                }
                val r = FloatArray(9); val inc = FloatArray(9)
                if (SensorManager.getRotationMatrix(r, inc, gravity, geomag)) {
                    val ori = FloatArray(3); SensorManager.getOrientation(r, ori)
                    azimuth = Math.toDegrees(ori[0].toDouble()).toFloat().let { if (it < 0) it + 360 else it }
                    pitch   = Math.toDegrees(ori[1].toDouble()).toFloat()
                    roll    = Math.toDegrees(ori[2].toDouble()).toFloat()
                }
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) {}
        }
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let  { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let { sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(listener) }
    }

    val dir = when {
        azimuth < 22.5  || azimuth >= 337.5 -> "N"
        azimuth < 67.5  -> "NE";  azimuth < 112.5 -> "E";  azimuth < 157.5 -> "SE"
        azimuth < 202.5 -> "S";   azimuth < 247.5 -> "SW"; azimuth < 292.5 -> "W"
        else -> "NW"
    }

    // Theme colors
    val (bgCol, primaryCol, northCol, ringCol) = when (compassTheme) {
        "Neon"     -> listOf(Color(0xFF0D0D0D), Color(0xFF00FF88), Color(0xFFFF0040), Color(0xFF00FFFF))
        "Military" -> listOf(Color(0xFF1A2A1A), Color(0xFF7CBF5E), Color(0xFFFF6600), Color(0xFF4A6A4A))
        "Space"    -> listOf(Color(0xFF050520), Color(0xFFBB86FC), Color(0xFFFF4081), Color(0xFF3700B3))
        "Ocean"    -> listOf(Color(0xFF001A33), Color(0xFF00B4D8), Color(0xFFFF6B35), Color(0xFF0077B6))
        "Minimal"  -> listOf(Color.Transparent, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.outline)
        else       -> listOf(Color.Transparent, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.primary)
    }

    val magLevel = (magStrength / 100f).coerceIn(0f, 1f)
    val magLabel = when {
        magStrength < 25  -> "Low — may be near electronics"
        magStrength < 65  -> "Normal — good accuracy"
        magStrength < 100 -> "High — possible interference"
        else              -> "Very High — metal nearby!"
    }

    Scaffold(topBar = { EverlastingTopBar("Compass", navController) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Theme selector
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(compassThemes.size) { i ->
                    FilterChip(selected = compassTheme == compassThemes[i],
                        onClick = { compassTheme = compassThemes[i] },
                        label = { Text(compassThemes[i]) })
                }
            }

            // Compass rose canvas
            Card(
                Modifier.padding(16.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (compassTheme == "Minimal") MaterialTheme.colorScheme.surfaceVariant
                                     else bgCol.takeIf { it != Color.Transparent } ?: MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Fixed size square box centered in its parent
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawThemedCompass(animAzimuth, primaryCol, northCol, ringCol, compassTheme, size.minDimension)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(dir, style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black, color = primaryCol)
                    Text("${azimuth.toInt()}°", style = MaterialTheme.typography.headlineSmall,
                        color = primaryCol.copy(alpha = 0.8f))
                }
            }

            // Stats row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatCard("Pitch",   "${pitch.toInt()}°",   Icons.Default.RotateLeft,  Modifier.weight(1f))
                StatCard("Roll",    "${roll.toInt()}°",    Icons.Default.RotateRight, Modifier.weight(1f))
                StatCard("Bearing", "${azimuth.toInt()}°", Icons.Default.Explore,     Modifier.weight(1f))
                StatCard("🧲 Field", "${magStrength.toInt()}µT", Icons.Default.Explore, Modifier.weight(1f))
            }


            InfoCard(Icons.Default.Info, "Calibration",
                "Move your phone in a figure-8 pattern to calibrate. Keep away from metal objects for accurate readings.", isError = false)
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun DrawScope.drawThemedCompass(azimuth: Float, primary: Color, north: Color, ring: Color, theme: String, sz: Float) {
    val cx = sz / 2f; val cy = sz / 2f; val radius = sz * 0.40f
    val glowAlpha = if (theme == "Neon" || theme == "Space") 0.25f else 0.12f

    drawCircle(color = ring.copy(alpha = glowAlpha), radius = radius + 14f, center = Offset(cx, cy))
    drawCircle(color = ring.copy(alpha = 0.5f), radius = radius + 14f, center = Offset(cx, cy),
        style = androidx.compose.ui.graphics.drawscope.Stroke(if (theme == "Neon") 4f else 2f))

    for (i in 0 until 360 step 5) {
        val isCardinal = i % 90 == 0; val is45 = i % 45 == 0
        val tickLen = if (isCardinal) 28f else if (is45) 18f else 8f
        val rad = Math.toRadians(i.toDouble()).toFloat()
        val sa = rad - Math.toRadians(azimuth.toDouble()).toFloat()
        val sinA = sin(sa); val cosA = cos(sa)
        drawLine(color = if (isCardinal) primary else ring.copy(alpha = 0.4f),
            start = Offset(cx + radius * sinA, cy - radius * cosA),
            end   = Offset(cx + (radius - tickLen) * sinA, cy - (radius - tickLen) * cosA),
            strokeWidth = if (isCardinal) 3.5f else 1f)
    }

    val nAngle = -Math.toRadians(azimuth.toDouble()).toFloat()
    // North needle
    drawLine(color = north, start = Offset(cx, cy),
        end = Offset(cx + sin(nAngle) * radius * 0.68f, cy - cos(nAngle) * radius * 0.68f),
        strokeWidth = 7f)
    // South needle  
    val sAngle = nAngle + Math.PI.toFloat()
    drawLine(color = primary.copy(alpha = 0.45f), start = Offset(cx, cy),
        end = Offset(cx + sin(sAngle) * radius * 0.5f, cy - cos(sAngle) * radius * 0.5f),
        strokeWidth = 5f)
    // Center
    drawCircle(color = ring,    radius = 14f, center = Offset(cx, cy))
    drawCircle(color = north,   radius = 7f,  center = Offset(cx, cy))
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── FAKE CALL ───────────────────────────────────────────────────────────────
@Composable
fun FakeCallScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var callerName   by remember { mutableStateOf("Mom") }
    var callerNumber by remember { mutableStateOf("+91 98765 43210") }
    var callerAvatar by remember { mutableStateOf("👩") }
    var delaySeconds by remember { mutableIntStateOf(5) }
    var ringtoneType by remember { mutableStateOf("Default") }
    var isScheduled  by remember { mutableStateOf(false) }
    var countdown    by remember { mutableIntStateOf(0) }

    val avatarOptions = listOf("👩","👨","👧","👦","👴","👵","🧑","👮","👩‍⚕️","👨‍💼","🤖","👻","🐱","🐶")
    val ringtoneOptions = listOf("Default", "Vibrate Only")

    var hasOverlay by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    val lifecycleOwnerFC = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwnerFC) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME)
                hasOverlay = android.provider.Settings.canDrawOverlays(context)
        }
        lifecycleOwnerFC.lifecycle.addObserver(obs)
        onDispose { lifecycleOwnerFC.lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(isScheduled) {
        if (isScheduled) {
            countdown = delaySeconds
            while (countdown > 0) { delay(1000L); countdown-- }
            isScheduled = false
            com.coolappstore.everlastingandroidtweak.features.fakecall.FakeCallOverlayService.start(
                context, callerName, callerNumber, callerAvatar, ringtoneType
            )
        }
    }

    Scaffold(topBar = { EverlastingTopBar("Fake Call", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // Header
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("📞", style = MaterialTheme.typography.displaySmall)
                        Column {
                            Text("Fake Call", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Simulates a full-screen incoming call overlay. No Telecom setup needed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                        }
                    }
                }
            }

            // Permission check - only needs overlay
            if (!hasOverlay) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Text("Display Over Other Apps required", fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                        }
                        Text("This allows the fake call screen to appear over other apps.",
                            style = MaterialTheme.typography.bodySmall)
                        Button(onClick = {
                            context.startActivity(android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Layers, null); Spacer(Modifier.width(6.dp)); Text("Grant Permission")
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    shape = MaterialTheme.shapes.large) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text("Ready! Just configure and press the button.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Contact picker
            val contactPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
            val hasContactPerm = remember { mutableStateOf(
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            )}
            val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
                uri?.let {
                    try {
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        cursor?.use { c ->
                            if (c.moveToFirst()) {
                                val nameIdx = c.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                                val idIdx = c.getColumnIndex(android.provider.ContactsContract.Contacts._ID)
                                if (nameIdx >= 0) callerName = c.getString(nameIdx) ?: callerName
                                val id = if (idIdx >= 0) c.getString(idIdx) else null
                                if (id != null) {
                                    val phones = context.contentResolver.query(
                                        android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                        null, "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", arrayOf(id), null
                                    )
                                    phones?.use { p ->
                                        if (p.moveToFirst()) {
                                            val numIdx = p.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                                            if (numIdx >= 0) callerNumber = p.getString(numIdx) ?: callerNumber
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Caller setup
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Caller Details", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        FilledTonalButton(onClick = {
                            if (hasContactPerm.value) contactPicker.launch(null)
                            else contactPerm.launch(android.Manifest.permission.READ_CONTACTS)
                        }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Icon(Icons.Default.Contacts, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pick Contact")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(avatarOptions.size) { i ->
                            Surface(onClick = { callerAvatar = avatarOptions[i] },
                                shape = CircleShape,
                                color = if (callerAvatar == avatarOptions[i]) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(48.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(avatarOptions[i], style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = callerName, onValueChange = { callerName = it },
                        label = { Text("Caller Name") }, modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, null) }, singleLine = true)
                    OutlinedTextField(value = callerNumber, onValueChange = { callerNumber = it },
                        label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Phone, null) }, singleLine = true)
                }
            }

            // Ringtone
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Ringtone", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ringtoneOptions.forEach { opt ->
                            FilterChip(selected = ringtoneType == opt, onClick = { ringtoneType = opt },
                                label = { Text(opt) })
                        }
                    }
                }
            }

            // Delay
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Delay: ${if (delaySeconds == 0) "Instant" else "${delaySeconds}s"}",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Slider(value = delaySeconds.toFloat(), onValueChange = { delaySeconds = it.toInt() },
                        valueRange = 0f..60f, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Instant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("60 sec", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isScheduled) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📲 Incoming call in ${countdown}s…",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (delaySeconds > 0) 1f - countdown.toFloat() / delaySeconds else 1f },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { isScheduled = false }) {
                            Icon(Icons.Default.Close, null); Spacer(Modifier.width(4.dp)); Text("Cancel")
                        }
                    }
                }
            } else {
                Button(
                    onClick = { if (hasOverlay) isScheduled = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = callerNumber.isNotBlank() && hasOverlay
                ) {
                    Icon(Icons.Default.Call, null); Spacer(Modifier.width(8.dp))
                    Text(if (delaySeconds == 0) "Trigger Fake Call Now" else "Schedule Fake Call (${delaySeconds}s)")
                }
            }

            InfoCard(Icons.Default.Info, "How It Works",
                "Draws a full-screen call UI over all other apps using the overlay permission. Appears exactly like a real incoming call with accept and decline buttons.",
                isError = false)
        }
    }
}

// ─── VIBRATION PATTERNS ──────────────────────────────────────────────────────
@Composable
fun VibrationPatternsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callPattern  by AppPreferences.get(AppPreferences.CALL_VIBRATION_PATTERN, "Classic Ring").collectAsState("Classic Ring")
    val alarmPattern by AppPreferences.get(AppPreferences.ALARM_VIBRATION_PATTERN, "Urgent").collectAsState("Urgent")
    val notifPattern by AppPreferences.get(AppPreferences.NOTIF_VIBRATION_PATTERN, "Gentle Pulse").collectAsState("Gentle Pulse")
    // ROOT CAUSE FIX: defaults were `true` so all vibration patterns were ON first launch.
    // Vibration works without any special permission (VIBRATE is auto-granted) — the feature
    // works without accessibility too. Defaults changed to false so user must opt-in.

    val allPatterns = listOf(
        VibPatternData("Short Tap",      "·",   "Single quick tap",              longArrayOf(0, 60)),
        VibPatternData("Long Press",     "—",   "Long single buzz",              longArrayOf(0, 600)),
        VibPatternData("Double Tap",     "··",  "Two quick taps",                longArrayOf(0, 80, 80, 80)),
        VibPatternData("Triple Tap",     "···", "Three quick taps",              longArrayOf(0, 70, 60, 70, 60, 70)),
        VibPatternData("Classic Ring",   "∿",   "Old-school phone ring",         longArrayOf(0, 300, 200, 300, 200, 300)),
        VibPatternData("Heartbeat",      "♥",   "Ba-dum heartbeat",              longArrayOf(0, 80, 60, 120, 500)),
        VibPatternData("Double Heart",   "♥♥",  "Double heartbeat rhythm",       longArrayOf(0, 80, 60, 120, 120, 80, 60, 120, 500)),
        VibPatternData("SOS",            "···—···", "Morse code SOS",            longArrayOf(0,100,100,100,100,100,300,300,100,300,100,300,300,100,100,100,100,100)),
        VibPatternData("Rapid Fire",     "≡",   "Quick rapid bursts",            longArrayOf(0, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40)),
        VibPatternData("Slow Pulse",     "○",   "Slow rhythmic pulse",           longArrayOf(0, 400, 400, 400, 400)),
        VibPatternData("Escalating",     "↑",   "Gets stronger over time",       longArrayOf(0, 50, 100, 100, 200, 150, 300)),
        VibPatternData("Descending",     "↓",   "Gets weaker over time",         longArrayOf(0, 300, 150, 200, 100, 100, 50)),
        VibPatternData("Drumroll",       "⊕",   "Fast drumroll effect",          longArrayOf(0,30,30,30,30,30,30,30,30,30,30,30,30,30,30,30,30)),
        VibPatternData("Gentle Pulse",   "~",   "Soft gentle notification",      longArrayOf(0, 150, 300, 150)),
        VibPatternData("Urgent",         "!!",  "Strong urgent alert",           longArrayOf(0, 500, 100, 500)),
        VibPatternData("Tick Tock",      "⏱",   "Clock-like tick pattern",       longArrayOf(0, 80, 920, 80, 920)),
        VibPatternData("Knock Knock",    "✊",   "Two knocks like a door",        longArrayOf(0, 200, 150, 200)),
        VibPatternData("Buzz Buzz",      "⚡",   "Electric buzz feeling",         longArrayOf(0, 120, 80, 120, 80, 120)),
        VibPatternData("Wave",           "≈",   "Smooth wave motion",            longArrayOf(0, 100, 50, 200, 50, 300, 50, 200, 50, 100)),
        VibPatternData("Syncopated",     "♪",   "Off-beat musical rhythm",       longArrayOf(0, 100, 200, 50, 150, 200, 100)),
        VibPatternData("Alarm Clock",    "⏰",   "Traditional alarm pattern",     longArrayOf(0,200,100,200,100,200,400,200,100,200,100,200)),
        VibPatternData("Incoming Call",  "📞",  "Traditional phone ring",        longArrayOf(0,500,500,500,500,500,500)),
        VibPatternData("Military",       "▣",   "Three sharp bursts",            longArrayOf(0, 200, 150, 200, 150, 200)),
        VibPatternData("Morse Hi",       "—·",  "Dash-dot Morse",                longArrayOf(0, 300, 100, 100)),
        VibPatternData("Silent",         "∅",   "No vibration",                  longArrayOf(0))
    )

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
    }

    fun testPattern(patternName: String) {
        // BUG FIX: "Silent" pattern (longArrayOf(0)) causes crash on many devices
        // because VibrationEffect.createWaveform needs at least one non-zero duration.
        // Also guard against any all-zero waveform.
        if (patternName == "Silent") {
            vibrator.cancel()
            return
        }
        val pat = allPatterns.find { it.name == patternName }?.waveform ?: return
        // Additional safety: skip if every element is 0
        if (pat.all { it == 0L }) {
            vibrator.cancel()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                vibrator.cancel()
                vibrator.vibrate(VibrationEffect.createWaveform(pat, -1))
            } catch (e: Exception) {
                // Swallow — some OEM vibrators reject certain waveforms
                android.util.Log.w("VibrationPatterns", "Waveform rejected: ${e.message}")
            }
        }
    }

    Scaffold(topBar = { EverlastingTopBar("Vibration Patterns", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            // Header card
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("📳", fontSize = 36.sp)
                        Column {
                            Text("Custom Vibration Patterns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("${allPatterns.size} unique patterns • Tap any chip to test it live",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                            Spacer(Modifier.height(4.dp))
                            Text("ℹ️ Works without any special permission. Patterns are triggered by your accessibility service when calls/alarms/notifs arrive.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f))
                        }
                    }
                }
            }

            val callEnabled  by AppPreferences.get(AppPreferences.CALL_VIBRATION_ENABLED, false).collectAsState(false)
            val alarmEnabled by AppPreferences.get(AppPreferences.ALARM_VIBRATION_ENABLED, false).collectAsState(false)
            val notifEnabled by AppPreferences.get(AppPreferences.NOTIF_VIBRATION_ENABLED, false).collectAsState(false)

            PatternSection2("📞  Incoming Calls", callPattern, allPatterns, { testPattern(it) },
                isEnabled = callEnabled, onToggle = { scope.launch { AppPreferences.set(AppPreferences.CALL_VIBRATION_ENABLED, it) } }
            ) { scope.launch { AppPreferences.set(AppPreferences.CALL_VIBRATION_PATTERN, it) } }

            PatternSection2("⏰  Alarms", alarmPattern, allPatterns, { testPattern(it) },
                isEnabled = alarmEnabled, onToggle = { scope.launch { AppPreferences.set(AppPreferences.ALARM_VIBRATION_ENABLED, it) } }
            ) { scope.launch { AppPreferences.set(AppPreferences.ALARM_VIBRATION_PATTERN, it) } }

            PatternSection2("🔔  Notifications", notifPattern, allPatterns, { testPattern(it) },
                isEnabled = notifEnabled, onToggle = { scope.launch { AppPreferences.set(AppPreferences.NOTIF_VIBRATION_ENABLED, it) } }
            ) { scope.launch { AppPreferences.set(AppPreferences.NOTIF_VIBRATION_PATTERN, it) } }

            InfoCard(Icons.Default.Info, "Tap to Preview",
                "Selecting a pattern will immediately trigger a test vibration so you can feel it.", isError = false)
            Spacer(Modifier.height(8.dp))
        }
    }
}

data class VibPatternData(val name: String, val emoji: String, val desc: String, val waveform: LongArray)

@Composable
private fun PatternSection2(
    title: String,
    current: String,
    patterns: List<VibPatternData>,
    onTest: (String) -> Unit,
    isEnabled: Boolean = true,
    onToggle: ((Boolean) -> Unit)? = null,
    onSelect: (String) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 12.dp))

        Card(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                // Current selection display
                val selected = patterns.find { it.name == current }
                if (selected != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(selected.emoji,
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 18.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Selected: ${selected.name}",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Text(selected.desc, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row {
                            FilledTonalIconButton(onClick = { onTest(current) }) {
                                Icon(Icons.Default.Vibration, null, modifier = Modifier.size(20.dp))
                            }
                            if (onToggle != null) {
                                Spacer(Modifier.width(4.dp))
                                com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = isEnabled, onCheckedChange = { onToggle(it) })
                            }
                        }
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                }

                // All pattern chips in wrapped rows
                val chunked = patterns.chunked(4)
                chunked.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p ->
                            FilterChip(
                                selected = current == p.name,
                                onClick = { onSelect(p.name); onTest(p.name) },
                                label = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(p.emoji, fontSize = 14.sp)
                                        Text(p.name, style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill remaining slots in last row
                        repeat(4 - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

// ─── LOCK SCREEN WIDGETS ─────────────────────────────────────────────────────
@Composable
fun LockScreenWidgetsScreen(navController: NavController) {
    val context = LocalContext.current

    // Launch LSW MainActivity directly – all real functionality lives there
    LaunchedEffect(Unit) {
        try {
            val intent = android.content.Intent(context, tk.zwander.lockscreenwidgets.MainActivity::class.java)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        navController.popBackStack()
    }
}


// ─── CUSTOM QS TILES ─────────────────────────────────────────────────────────
@Composable
fun CustomQSTilesScreen(navController: NavController) {
    val context = LocalContext.current

    val tiles = listOf(
        Triple("Keep Screen On", "Prevent screen from sleeping", Icons.Default.Lightbulb),
        Triple("Shake Torch", "Toggle shake-to-torch", Icons.Default.FlashOn),
        Triple("Flip to DND", "Toggle flip-to-DND mode", Icons.Default.DoNotDisturb),
        Triple("Volume Boost", "Toggle volume boost", Icons.Default.VolumeUp),
        Triple("Music Leveler", "Toggle equalizer overlay", Icons.Default.BarChart),
        Triple("Security Motion", "Toggle motion security alarm", Icons.Default.Security),
        Triple("Auto Rotate", "Toggle screen rotation", Icons.Default.ScreenRotation),
        Triple("Bluetooth", "Quick Bluetooth toggle", Icons.Default.Bluetooth),
    )
    val states = remember { mutableStateListOf(*Array(tiles.size) { i -> i < 2 }) }

    Scaffold(topBar = { EverlastingTopBar("Custom QS Tiles", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Text("⚡ Quick Settings Tiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Toggle which tiles appear in the Quick Settings panel. Drag tiles to reorder in your notification shade.", style = MaterialTheme.typography.bodySmall)
                }
            }
            FeatureSection("Available Tiles") {
                tiles.forEachIndexed { i, (name, desc, icon) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        supportingContent = { Text(desc) },
                        leadingContent = {
                            Box(Modifier.size(36.dp).clip(MaterialTheme.shapes.small)
                                .background(if (states[i]) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center) {
                                Icon(icon, null, Modifier.size(20.dp),
                                    tint = if (states[i]) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        trailingContent = { com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = states[i], onCheckedChange = { states[i] = it }) }
                    )
                    if (i < tiles.lastIndex) HorizontalDivider()
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                try {
                    context.startActivity(
                        Intent("android.settings.ACTION_QS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {}
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Icon(Icons.Default.GridView, null); Spacer(Modifier.width(6.dp)); Text("Open Quick Settings Panel")
            }
            InfoCard(Icons.Default.Info, "How to Add Tiles",
                "Swipe down twice to open full Quick Settings → tap Edit/Pencil icon → drag Everlasting tiles into position.", isError = false)
        }
    }
}

// ─── CHARGE LIMIT ALARM ───────────────────────────────────────────────────────
@Composable
fun ChargeLimitScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled    by AppPreferences.get(AppPreferences.CHARGE_LIMIT_ENABLED, false).collectAsState(false)
    val limit      by AppPreferences.get(AppPreferences.CHARGE_LIMIT_PERCENT, 80).collectAsState(80)
    val ringtoneUri by AppPreferences.get(AppPreferences.CHARGE_RINGTONE_URI, "").collectAsState("")
    val repeat     by AppPreferences.get(AppPreferences.CHARGE_REPEAT_ENABLED, true).collectAsState(true)
    var sliderVal  by remember { mutableFloatStateOf(80f) }
    LaunchedEffect(limit) { sliderVal = limit.toFloat() }

    val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    val battLevel = remember {
        val lev = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scl = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (lev >= 0) lev * 100 / scl else 0
    }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.toString()?.let { scope.launch { AppPreferences.set(AppPreferences.CHARGE_RINGTONE_URI, it) } }
    }

    Scaffold(topBar = { EverlastingTopBar("Charge Limit Alarm", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // Hero card
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚡", style = MaterialTheme.typography.displayMedium)
                    Text("Battery Health Guard", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Now: $battLevel%  •  Limit: ${sliderVal.toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { battLevel / 100f },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                        color = when {
                            battLevel >= sliderVal.toInt() -> MaterialTheme.colorScheme.error
                            battLevel >= sliderVal.toInt() - 10 -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    )
                    if (battLevel >= sliderVal.toInt()) {
                        Spacer(Modifier.height(8.dp))
                        Text("🔴 UNPLUG NOW!", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Settings
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable Charge Limit Alarm", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Alarm + notification at set %", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = enabled, onCheckedChange = {
                            scope.launch { AppPreferences.set(AppPreferences.CHARGE_LIMIT_ENABLED, it) }
                        })
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Column(Modifier.padding(16.dp)) {
                        Text("Charge Limit: ${sliderVal.toInt()}%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Slider(value = sliderVal, onValueChange = {
                            sliderVal = it
                            scope.launch { AppPreferences.set(AppPreferences.CHARGE_LIMIT_PERCENT, it.toInt()) }
                        }, valueRange = 50f..100f, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("50%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("80% ideal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("100%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Repeat Alarm", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text("Keep playing until phone is unplugged", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = repeat, onCheckedChange = {
                            scope.launch { AppPreferences.set(AppPreferences.CHARGE_REPEAT_ENABLED, it) }
                        })
                    }
                }
            }

            // Ringtone selection
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Alarm Sound", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (ringtoneUri.isEmpty()) "Default alarm ringtone"
                                else android.net.Uri.parse(ringtoneUri).lastPathSegment ?: "Custom file",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (ringtoneUri.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (ringtoneUri.isNotEmpty()) {
                                OutlinedButton(onClick = {
                                    scope.launch { AppPreferences.set(AppPreferences.CHARGE_RINGTONE_URI, "") }
                                }) { Text("Reset") }
                            }
                            FilledTonalButton(onClick = { ringtonePicker.launch("audio/*") }) {
                                Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Browse")
                            }
                        }
                    }
                }
            }

            // Preview alarm button
            OutlinedButton(onClick = {
                scope.launch {
                    val nm = context.getSystemService(android.app.NotificationManager::class.java)
                    nm?.notify(3001,
                        androidx.core.app.NotificationCompat.Builder(context, com.coolappstore.everlastingandroidtweak.EverlastingApp.CHANNEL_ALERTS)
                            .setContentTitle("⚡ Charge Limit TEST")
                            .setContentText("This is how the alarm looks when battery hits the limit.")
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true).build()
                    )
                    // Test vibration
                    try {
                        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                        vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0,300,150,300), -1))
                    } catch (_: Exception) {}
                }
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Icon(Icons.Default.Notifications, null); Spacer(Modifier.width(6.dp)); Text("Preview Alarm")
            }
            InfoCard(Icons.Default.BatteryFull, "Battery Health Tip",
                "Keeping battery between 20-80% can almost double total battery lifespan. Charging to 100% regularly degrades capacity faster.", isError = false)
        }
    }
}

// ─── APP FREEZER ─────────────────────────────────────────────────────────────
@Composable
fun AppFreezerScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shizukuReady by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(listOf<android.content.pm.ApplicationInfo>()) }
    var frozenApps by remember { mutableStateOf(setOf<String>()) }
    // Checkbox multi-select state
    var selectedApps by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(false) }
    var freezingInProgress by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                shizukuReady = com.coolappstore.everlastingandroidtweak.features.appfreezer.AppFreezerHelper.isShizukuReady()
                if (shizukuReady && apps.isEmpty()) {
                    loading = true
                    scope.launch(Dispatchers.IO) {
                        apps = com.coolappstore.everlastingandroidtweak.features.appfreezer.AppFreezerHelper.getUserApps(context)
                        loading = false
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val displayedApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            context.packageManager.getApplicationLabel(it).toString()
                .contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(topBar = { EverlastingTopBar("App Freezer", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {

            // Header
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp)) {
                    Text("❄️ App Freezer", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("Force-stop selected apps immediately — identical to Settings → App Info → Force Stop. Apps resume normally when re-opened. Uses Shizuku.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }

            if (!shizukuReady) {
                InfoCard(Icons.Default.Warning, "Shizuku Required",
                    "Start Shizuku first: adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh",
                    isError = true, actionLabel = "Get Shizuku",
                    onAction = {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("market://details?id=moe.shizuku.privileged.api"))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    })
                return@Scaffold
            }

            if (loading) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true, shape = MaterialTheme.shapes.extraLarge
            )

            // Action buttons row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                // Freeze Selected
                Button(
                    onClick = {
                        if (selectedApps.isEmpty()) return@Button
                        freezingInProgress = true
                        scope.launch(Dispatchers.IO) {
                            selectedApps.forEach { pkg ->
                                com.coolappstore.everlastingandroidtweak.features.appfreezer.AppFreezerHelper.freezeApp(pkg)
                            }
                            frozenApps = frozenApps + selectedApps
                            selectedApps = emptySet()
                            freezingInProgress = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedApps.isNotEmpty() && !freezingInProgress
                ) {
                    if (freezingInProgress) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(4.dp)) }
                    Icon(Icons.Default.AcUnit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Freeze (${selectedApps.size})", style = MaterialTheme.typography.labelMedium)
                }

                // Unfreeze selected
                OutlinedButton(
                    onClick = {
                        if (selectedApps.isEmpty()) return@OutlinedButton
                        scope.launch(Dispatchers.IO) {
                            selectedApps.forEach { pkg ->
                                com.coolappstore.everlastingandroidtweak.features.appfreezer.AppFreezerHelper.unfreezeApp(pkg)
                            }
                            frozenApps = frozenApps - selectedApps
                            selectedApps = emptySet()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedApps.isNotEmpty() && !freezingInProgress
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Unfreeze", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Select all / Freeze all row
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        selectedApps = if (selectedApps.size == displayedApps.size)
                            emptySet()
                        else displayedApps.map { it.packageName }.toSet()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.SelectAll, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (selectedApps.size == displayedApps.size && displayedApps.isNotEmpty()) "Deselect All" else "Select All",
                        style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = {
                        freezingInProgress = true
                        scope.launch(Dispatchers.IO) {
                            apps.forEach { com.coolappstore.everlastingandroidtweak.features.appfreezer.AppFreezerHelper.freezeApp(it.packageName) }
                            frozenApps = apps.map { it.packageName }.toSet()
                            selectedApps = emptySet()
                            freezingInProgress = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !freezingInProgress
                ) {
                    Icon(Icons.Default.AcUnit, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Stop All", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Stats row
            if (apps.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("${apps.size} apps  •  ${frozenApps.size} frozen  •  ${selectedApps.size} selected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // App list with checkboxes
            FeatureSection("Installed Apps (${displayedApps.size})") {
                if (displayedApps.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    displayedApps.forEachIndexed { i, app ->
                        val name = context.packageManager.getApplicationLabel(app).toString()
                        val isFrozen = frozenApps.contains(app.packageName)
                        val isChecked = selectedApps.contains(app.packageName)

                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    selectedApps = if (isChecked)
                                        selectedApps - app.packageName
                                    else
                                        selectedApps + app.packageName
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    selectedApps = if (it)
                                        selectedApps + app.packageName
                                    else
                                        selectedApps - app.packageName
                                }
                            )
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isFrozen) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text("✓ Stopped", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        if (i < displayedApps.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── APP UPDATER ─────────────────────────────────────────────────────────────

// Converts GitHub Markdown to simple HTML for WebView rendering
private fun markdownToHtml(md: String, isDark: Boolean = true): String {
    // Theme-aware colour tokens
    val textColor      = if (isDark) "#e0e0e0" else "#212121"
    val headingColor   = if (isDark) "#bb86fc" else "#6200ea"
    val codeBg         = if (isDark) "#2a2a2a" else "#f0f0f0"
    val codeColor      = if (isDark) "#80cbc4" else "#006064"
    val preCodeBg      = if (isDark) "#1e1e1e" else "#f5f5f5"
    val preCodeColor   = if (isDark) "#e0e0e0" else "#212121"
    val linkColor      = if (isDark) "#4db6ac" else "#00796b"
    val blockBorder    = if (isDark) "#888" else "#bbb"
    val blockColor     = if (isDark) "#aaa" else "#666"

    val codeBlocks = mutableListOf<String>()
    var html = md.replace(Regex("```[\\w]*\\n?([\\s\\S]*?)```")) {
        val idx = codeBlocks.size
        codeBlocks.add("<pre style='background:$preCodeBg;color:$preCodeColor;padding:12px;border-radius:8px;overflow-x:auto;font-size:13px;'><code>${it.groupValues[1].trim()}</code></pre>")
        "\u0000CODE$idx\u0000"
    }
    html = html.replace(Regex("(?m)^### (.+)$")) { "<h3 style='color:$headingColor;'>${it.groupValues[1]}</h3>" }
    html = html.replace(Regex("(?m)^## (.+)$"))  { "<h2 style='color:$headingColor;'>${it.groupValues[1]}</h2>" }
    html = html.replace(Regex("(?m)^# (.+)$"))   { "<h1 style='color:$headingColor;'>${it.groupValues[1]}</h1>" }
    html = html.replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*")) { "<b><i>${it.groupValues[1]}</i></b>" }
    html = html.replace(Regex("\\*\\*(.+?)\\*\\*"))       { "<b>${it.groupValues[1]}</b>" }
    html = html.replace(Regex("`(.+?)`")) { "<code style='background:$codeBg;padding:1px 5px;border-radius:3px;color:$codeColor;'>${it.groupValues[1]}</code>" }
    html = html.replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)")) { "<a href='${it.groupValues[2]}' style='color:$linkColor;'>${it.groupValues[1]}</a>" }
    html = html.replace(Regex("!\\[([^\\]]*)]\\(([^)]+)\\)")) { "<a href='${it.groupValues[2]}'>[image]</a>" }
    html = html.replace(Regex("(?m)^[-*+] (.+)$")) { "<li>${it.groupValues[1]}</li>" }
    html = html.replace(Regex("(?m)^\\d+\\. (.+)$")) { "<li>${it.groupValues[1]}</li>" }
    html = html.replace(Regex("(<li>[\\s\\S]*?</li>\n?)+")) { "<ul style='padding-left:20px;margin:4px 0;'>${it.value}</ul>" }
    html = html.replace(Regex("(?m)^> (.+)$")) { "<blockquote style='border-left:3px solid $blockBorder;padding-left:12px;color:$blockColor;margin:4px 0;'>${it.groupValues[1]}</blockquote>" }
    html = html.replace(Regex("\n\n+"), "<br><br>").replace("\n", "<br>")
    codeBlocks.forEachIndexed { i, block -> html = html.replace("\u0000CODE$i\u0000", block) }
    return "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{font-family:-apple-system,Roboto,sans-serif;font-size:14px;line-height:1.7;color:$textColor;background:transparent;padding:0;margin:0;}h1,h2,h3{margin:8px 0 4px;}a{color:$linkColor;}ul,ol{margin:4px 0;}li{margin:2px 0;}pre{margin:8px 0;}</style></head><body>$html</body></html>"
}

@Composable
fun AppUpdaterScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val viewModel: com.coolappstore.everlastingandroidtweak.features.appupdater.AppUpdatesViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()

    val trackedRepos      by viewModel.trackedRepos
    val isLoading         by viewModel.isLoading
    val refreshingRepoIds by viewModel.refreshingRepoIds
    val updateProgress    by viewModel.updateProgress
    val errorMessage      by viewModel.errorMessage
    val installingRepoId  by viewModel.installingRepoId
    val installStatus     by viewModel.installStatus

    var showAddSheet      by remember { mutableStateOf(false) }
    var searchQuery       by remember { mutableStateOf("") }
    val isSearching       by viewModel.isSearching
    val searchResult      by viewModel.searchResult
    val latestRelease     by viewModel.latestRelease
    val availableApks     by viewModel.availableApks
    val selectedApkName   by viewModel.selectedApkName
    val allowPreReleases  by viewModel.allowPreReleases

    var repoToShowNotes   by remember { mutableStateOf<com.coolappstore.everlastingandroidtweak.features.appupdater.domain.TrackedRepo?>(null) }
    var checking          by remember { mutableStateOf(false) }
    var selfUpdate        by remember { mutableStateOf<com.coolappstore.everlastingandroidtweak.features.appupdater.UpdateResult?>(null) }
    val autoCheckMins     by AppPreferences.get(AppPreferences.APP_UPDATER_AUTO_CHECK_MINS, 0).collectAsState(0)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { try { context.contentResolver.openOutputStream(it)?.use { os -> viewModel.exportTrackedRepos(context, os) } } catch (_: Exception) {} }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { try { context.contentResolver.openInputStream(it)?.use { ins -> viewModel.importTrackedRepos(context, ins) } } catch (_: Exception) {} }
    }

    LaunchedEffect(Unit) { viewModel.loadTrackedRepos(context) }

    // Auto-trigger Check All when repos are loaded
    LaunchedEffect(trackedRepos) {
        if (trackedRepos.isNotEmpty() && refreshingRepoIds.isEmpty()) {
            viewModel.checkForUpdates(context)
        }
    }

    LaunchedEffect(errorMessage) { if (errorMessage != null && !showAddSheet) viewModel.clearError() }

    // ── Add repo bottom sheet ──────────────────────────────────────────────────
    if (showAddSheet) {
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showAddSheet = false; viewModel.clearSearch(); searchQuery = "" }
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Track GitHub App", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Paste a GitHub URL or owner/repo to search for APK releases.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; viewModel.onSearchQueryChanged(it) },
                    label = { Text("github.com/owner/repo or owner/repo") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (isSearching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                )
                Button(onClick = { viewModel.searchRepo(context) },
                    enabled = searchQuery.isNotBlank() && !isSearching,
                    modifier = Modifier.fillMaxWidth()) { Text("Search") }

                errorMessage?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = MaterialTheme.shapes.medium) {
                        Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (searchResult != null && latestRelease != null) {
                    val repo = searchResult!!; val release = latestRelease!!
                    HorizontalDivider()
                    Card(shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(repo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            repo.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text("${repo.stars} stars  •  Latest: ${release.tagName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (availableApks.size > 1) {
                                Spacer(Modifier.height(4.dp))
                                Text("Select APK:", style = MaterialTheme.typography.labelMedium)
                                availableApks.forEach { apk ->
                                    Row(Modifier.fillMaxWidth().clickable { viewModel.setSelectedApk(apk) }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        RadioButton(selected = selectedApkName == apk, onClick = { viewModel.setSelectedApk(apk) })
                                        Text(apk, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Allow pre-releases", style = MaterialTheme.typography.bodySmall)
                                Switch(checked = allowPreReleases, onCheckedChange = { viewModel.setAllowPreReleases(it) })
                            }
                        }
                    }
                    Button(onClick = { viewModel.trackRepo(context); showAddSheet = false; searchQuery = "" },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Track This App")
                    }
                }
            }
        }
    }

    // ── Release notes bottom sheet ─────────────────────────────────────────────
    repoToShowNotes?.let { repo ->
        @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { repoToShowNotes = null }) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("${repo.name} — ${repo.latestTagName}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (repo.latestReleaseBody.isNullOrBlank()) {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                } else {
                    // Render GitHub Markdown as HTML in a WebView
                    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    val htmlContent = remember(repo.latestReleaseBody, isDark) { markdownToHtml(repo.latestReleaseBody, isDark) }
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.webkit.WebView(ctx).apply {
                                settings.javaScriptEnabled = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.setSupportZoom(false)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { wv -> wv.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 480.dp)
                    )
                }
                repo.latestReleaseUrl?.let { url ->
                    OutlinedButton(
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open on GitHub") }
                }
            }
        }
    }

    Scaffold(
        topBar = { EverlastingTopBar("App Updater", navController) },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = { showAddSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) { Icon(Icons.Default.Add, "Add repo") }
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
        ) {

            // ── Header card ──────────────────────────────────────────────────
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.extraLarge) {
                    Column(Modifier.padding(20.dp)) {
                        Text("GitHub App Updater", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text("Track and update sideloaded apps from GitHub releases. Tap + to add any public repo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }

            // ── Self-update section — inside its own Card ────────────────────
            item {
                FeatureSection("Everlasting Android Tweak") {
                    // Self-update card container
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        ListItem(
                            headlineContent = {
                                Text("Check for Everlasting Updates",
                                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            },
                            leadingContent = {
                                Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.SystemUpdateAlt, null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                                }
                            },
                            trailingContent = {
                                if (checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                else FilledTonalButton(onClick = {
                                    checking = true
                                    scope.launch {
                                        selfUpdate = com.coolappstore.everlastingandroidtweak.features.appupdater.AppUpdaterHelper.checkSelfUpdate(context)
                                        checking = false
                                    }
                                }) { Text("Check") }
                            }
                        )
                        selfUpdate?.let { result ->
                            HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            Card(Modifier.fillMaxWidth().padding(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (result.hasUpdate) MaterialTheme.colorScheme.errorContainer
                                    else MaterialTheme.colorScheme.primaryContainer),
                                shape = MaterialTheme.shapes.large) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(if (result.hasUpdate) "Update Available  v${result.latestVersion}" else "Up to date",
                                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Installed: v${result.installedVersion}  •  Latest: v${result.latestVersion}",
                                        style = MaterialTheme.typography.bodySmall)
                                    if (result.hasUpdate) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(result.downloadUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }, modifier = Modifier.weight(1f)) { Text("Download") }
                                            OutlinedButton(onClick = {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/hari161008/Everlasting-Android-Tweak/releases/latest")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            }, modifier = Modifier.weight(1f)) { Text("Releases") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ── Auto-check interval ───────────────────────────────────
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("Auto Check Interval",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                        val intervalOptions = listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 60 to "1 hr", 360 to "6 hrs", 1440 to "24 hrs")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(intervalOptions.size) { i ->
                                val (mins, label) = intervalOptions[i]
                                FilterChip(
                                    selected = autoCheckMins == mins,
                                    onClick = {
                                        scope.launch { AppPreferences.set(AppPreferences.APP_UPDATER_AUTO_CHECK_MINS, mins) }
                                        com.coolappstore.everlastingandroidtweak.features.appupdater.AppUpdaterScheduler.schedule(context, mins)
                                    },
                                    label = { Text(label) },
                                    leadingIcon = if (autoCheckMins == mins) ({ Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }) else null
                                )
                            }
                        }
                    }
                }
            }

            // ── Check All + Export/Import row ────────────────────────────────
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.checkForUpdates(context) },
                        enabled = refreshingRepoIds.isEmpty() && trackedRepos.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (refreshingRepoIds.isNotEmpty()) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp)); Text("Checking...")
                        } else {
                            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp)); Text("Check All")
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                            exportLauncher.launch("everlasting_updates_$ts.json")
                        },
                        modifier = Modifier.size(width = 56.dp, height = 40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Icon(Icons.Default.Upload, null, Modifier.size(18.dp)) }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.size(width = 56.dp, height = 40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Icon(Icons.Default.Download, null, Modifier.size(18.dp)) }
                }
            }

            // ── Loading / Empty state ─────────────────────────────────────────
            if (isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (trackedRepos.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Apps, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp))
                                Text("No apps tracked yet", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Tap + to add a GitHub repository", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                val pending  = trackedRepos.filter { it.isUpdateAvailable }.sortedByDescending { it.publishedAt }
                val upToDate = trackedRepos.filter { !it.isUpdateAvailable }.sortedByDescending { it.publishedAt }

                if (pending.isNotEmpty()) {
                    item {
                        Text("Updates Available (${pending.size})",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp))
                    }
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column {
                                pending.forEachIndexed { idx, repo ->
                                    AppUpdaterRepoRow(
                                        repo = repo,
                                        isRefreshing = refreshingRepoIds.contains(repo.fullName),
                                        isInstalling = installingRepoId == repo.fullName,
                                        installStatus = installStatus,
                                        downloadProgress = updateProgress,
                                        onInstall = { viewModel.downloadAndInstall(context, repo) },
                                        onDelete = { viewModel.untrackRepo(context, repo.fullName) },
                                        onShowNotes = { repoToShowNotes = repo; viewModel.fetchReleaseNotesIfNeeded(context, repo) }
                                    )
                                    if (idx < pending.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                    }
                }

                if (upToDate.isNotEmpty()) {
                    item {
                        Text("Up to Date (${upToDate.size})",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
                    }
                    item {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column {
                                upToDate.forEachIndexed { idx, repo ->
                                    AppUpdaterRepoRow(
                                        repo = repo,
                                        isRefreshing = refreshingRepoIds.contains(repo.fullName),
                                        isInstalling = installingRepoId == repo.fullName,
                                        installStatus = installStatus,
                                        downloadProgress = updateProgress,
                                        onInstall = { viewModel.downloadAndInstall(context, repo) },
                                        onDelete = { viewModel.untrackRepo(context, repo.fullName) },
                                        onShowNotes = { repoToShowNotes = repo; viewModel.fetchReleaseNotesIfNeeded(context, repo) }
                                    )
                                    if (idx < upToDate.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdaterRepoRow(
    repo: com.coolappstore.everlastingandroidtweak.features.appupdater.domain.TrackedRepo,
    isRefreshing: Boolean,
    isInstalling: Boolean,
    installStatus: String?,
    downloadProgress: Float,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    onShowNotes: () -> Unit
) {
    val context = LocalContext.current

    // Load installed app icon
    val appIcon: android.graphics.drawable.Drawable? = remember(repo.mappedPackageName) {
        repo.mappedPackageName?.let { pkg ->
            try { context.packageManager.getApplicationIcon(pkg) } catch (_: Exception) { null }
        }
    }

    val statusDotColor = when {
        isRefreshing -> MaterialTheme.colorScheme.outline
        repo.isUpdateAvailable -> MaterialTheme.colorScheme.error
        else -> Color(0xFF4CAF50)
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App icon with status dot
        Box(Modifier.size(52.dp)) {
            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center) {
                if (appIcon != null) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.ImageView(ctx).apply {
                                setImageDrawable(appIcon)
                                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Android, null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                }
            }
            // Status dot (bottom-right)
            Box(
                Modifier.size(14.dp).align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(statusDotColor)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            )
        }

        Column(Modifier.weight(1f)) {
            // App name — larger and bolder
            Text(
                repo.mappedAppName ?: repo.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(repo.fullName, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            if (repo.isUpdateAvailable && repo.mappedPackageName != null) {
                Surface(shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)) {
                    Text("Update available — ${repo.latestTagName}",
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Surface(shape = MaterialTheme.shapes.small, color = Color(0xFF4CAF50).copy(alpha = 0.15f)) {
                    Text(repo.latestTagName,
                        Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                }
            }
            if (isInstalling && installStatus != null) {
                Spacer(Modifier.height(4.dp))
                Text(installStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (installStatus == "Downloading...") {
                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                }
            }
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isRefreshing || isInstalling) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                if (repo.isUpdateAvailable) {
                    Button(onClick = onInstall,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)) {
                        Text("Install", style = MaterialTheme.typography.labelSmall)
                    }
                }
                OutlinedButton(onClick = onShowNotes,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)) {
                    Text("Notes", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─── EYE DROPPER ─────────────────────────────────────────────────────────────
@Composable
fun EyeDropperScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedColor by remember { mutableStateOf<Color?>(null) }
    var hexValue by remember { mutableStateOf("") }
    var showFullPicker by remember { mutableStateOf(false) }

    // Image-based pixel sampler state
    var samplerBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var samplerImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var sampleX by remember { mutableFloatStateOf(0.5f) }  // normalized 0-1
    var sampleY by remember { mutableFloatStateOf(0.5f) }

    // Image picker — works on all API levels
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            samplerImageUri = it
            scope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(it)
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                    stream?.close()
                    withContext(Dispatchers.Main) {
                        samplerBitmap = bmp
                        // Sample center pixel as default
                        if (bmp != null) {
                            val px = bmp.getPixel(bmp.width / 2, bmp.height / 2)
                            pickedColor = Color(px)
                            hexValue = "#%06X".format(px and 0xFFFFFF)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // Full color picker dialog
    if (showFullPicker) {
        EverlastingColorPickerDialog(
            initialHex = hexValue.ifEmpty { "#2196F3" },
            onDismiss = { showFullPicker = false },
            onColorSelected = { hex ->
                hexValue = hex
                pickedColor = runCatching {
                    Color(android.graphics.Color.parseColor(hex))
                }.getOrNull()
            }
        )
    }

    // Sample pixel from bitmap at normalized position
    fun samplePixel(nx: Float, ny: Float) {
        val bmp = samplerBitmap ?: return
        val px = (nx * (bmp.width - 1)).toInt().coerceIn(0, bmp.width - 1)
        val py = (ny * (bmp.height - 1)).toInt().coerceIn(0, bmp.height - 1)
        val argb = bmp.getPixel(px, py)
        pickedColor = Color(argb)
        hexValue = "#%06X".format(argb and 0xFFFFFF)
        sampleX = nx; sampleY = ny
    }

    Scaffold(topBar = { EverlastingTopBar("Eye Dropper", navController) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Header
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "💧 Eye Dropper / Color Picker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Three ways to pick a color: use the full HSV picker, sample a pixel from any image you load, or manually enter a hex code. Works on all devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Color preview card
            if (pickedColor != null) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(pickedColor!!)
                                .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Picked Color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                hexValue,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val c = pickedColor!!
                            Text(
                                "R:${(c.red * 255).toInt()}  G:${(c.green * 255).toInt()}  B:${(c.blue * 255).toInt()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cb.setPrimaryClip(android.content.ClipData.newPlainText("Color", hexValue))
                                android.widget.Toast.makeText(context, "Copied $hexValue", android.widget.Toast.LENGTH_SHORT).show()
                            }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                                Text("Copy")
                            }
                            OutlinedButton(
                                onClick = { pickedColor = null; hexValue = ""; samplerBitmap = null; samplerImageUri = null },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Clear")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Method 1: Full HSV picker ─────────────────────────────────
            FeatureSection("Method 1: HSV Color Picker") {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Opens a full color picker with hue, saturation, brightness sliders and preset swatches. Works on every device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showFullPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Palette, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Color Picker")
                    }
                }
            }

            // ── Method 2: Image pixel sampler ─────────────────────────────
            FeatureSection("Method 2: Sample Pixel from Image") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Load any image from your gallery, then tap anywhere on it to sample that pixel's color. Works on all Android versions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Load Image from Gallery")
                    }

                    // Tap-to-sample canvas
                    if (samplerBitmap != null) {
                        val bmp = samplerBitmap!!
                        Text(
                            "Tap anywhere on the image to sample that color:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(MaterialTheme.shapes.large)
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
                                .pointerInput(bmp) {
                                    // Use short name — FQN loses PointerInputScope receiver
                                    detectTapGestures { offset: androidx.compose.ui.geometry.Offset ->
                                        if (canvasSize.width > 0 && canvasSize.height > 0) {
                                            samplePixel(
                                                offset.x / canvasSize.width,
                                                offset.y / canvasSize.height
                                            )
                                        }
                                    }
                                }
                        ) {
                            canvasSize = size
                            val imgBitmap = bmp.asImageBitmap()
                            drawImage(
                                image = imgBitmap,
                                dstSize = androidx.compose.ui.unit.IntSize(
                                    size.width.toInt(), size.height.toInt()
                                )
                            )
                            val cx = sampleX * size.width
                            val cy = sampleY * size.height
                            // Stroke named param is `width`, not `strokeWidth`
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color.White,
                                radius = 16f,
                                center = Offset(cx, cy),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                            drawCircle(
                                color = androidx.compose.ui.graphics.Color.Black,
                                radius = 16f,
                                center = Offset(cx, cy),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                            )
                            drawCircle(
                                color = pickedColor ?: androidx.compose.ui.graphics.Color.White,
                                radius = 8f,
                                center = Offset(cx, cy)
                            )
                        }
                        if (pickedColor != null) {
                            Text(
                                "Sampled: $hexValue",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ── Method 3: Manual hex input ────────────────────────────────
            FeatureSection("Method 3: Enter Hex Code") {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = hexValue,
                        onValueChange = { v ->
                            hexValue = v
                            if (v.matches(Regex("#[0-9A-Fa-f]{6}"))) {
                                pickedColor = runCatching {
                                    Color(android.graphics.Color.parseColor(v))
                                }.getOrNull()
                            }
                        },
                        label = { Text("Hex Code (e.g. #FF5722)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = {
                            val previewColor = runCatching {
                                Color(android.graphics.Color.parseColor(hexValue))
                            }.getOrDefault(Color.Gray)
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(previewColor)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                        },
                        trailingIcon = {
                            if (hexValue.matches(Regex("#[0-9A-Fa-f]{6}"))) {
                                Icon(Icons.Default.CheckCircle, null,
                                    tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                            }
                        }
                    )
                }
            }

            InfoCard(
                Icons.Default.Info, "Works on All Devices",
                "Method 1 (HSV picker) and Method 3 (hex input) work everywhere. Method 2 (pixel sampler) works on any Android with a camera roll. No special permissions needed.",
                isError = false
            )
        }
    }
}

// ─── NOTIFICATION LIGHTING ───────────────────────────────────────────────────
@Composable
fun NotifLightScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val PREFS = "everlasting_notif_prefs"

    // SharedPreferences — single source of truth for the manager (mirrors Essentials)
    val sp = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }

    // ── State backed by SharedPreferences ─────────────────────────────────────
    var flashEnabled   by remember { mutableStateOf(sp.getBoolean("flashlight_pulse_enabled", false)) }
    var flashCountVal  by remember { mutableIntStateOf(sp.getInt("notif_flash_count", 3)) }
    var flashSpeedVal  by remember { mutableIntStateOf(sp.getInt("notif_flash_speed_ms", 150)) }

    var edgeEnabled    by remember { mutableStateOf(sp.getBoolean("edge_lighting_enabled", false)) }
    var edgeColor      by remember { mutableStateOf(sp.getString("edge_lighting_color", "#8BCAFF") ?: "#8BCAFF") }
    var thicknessVal   by remember { mutableFloatStateOf(try { sp.getFloat("edge_lighting_stroke_thickness", 8f) } catch (_: Exception) { sp.getInt("edge_lighting_stroke_thickness", 8).toFloat() }) }
    var durationVal    by remember { mutableIntStateOf(try { sp.getInt("edge_lighting_pulse_duration", 3000) } catch (_: Exception) { sp.getFloat("edge_lighting_pulse_duration", 3000f).toInt() }) }
    var edgeStyle      by remember { mutableStateOf(sp.getString("edge_lighting_style", "Full Border") ?: "Full Border") }
    var alphaVal       by remember { mutableFloatStateOf(try { sp.getFloat("edge_lighting_alpha", 0.9f) } catch (_: Exception) { sp.getInt("edge_lighting_alpha", 1).toFloat() }) }
    var onlyScreenOff  by remember { mutableStateOf(sp.getBoolean("edge_lighting_only_screen_off", true)) }
    var customHexEdge  by remember { mutableStateOf(edgeColor) }

    // ── Permission state ──────────────────────────────────────────────────────
    var hasNotifListener by remember { mutableStateOf(PermissionManager.isNotificationListenerEnabled(context)) }
    var hasOverlay       by remember { mutableStateOf(PermissionManager.hasOverlayPermission(context)) }
    var hasAccessibility by remember { mutableStateOf(PermissionManager.isAccessibilityEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifListener = PermissionManager.isNotificationListenerEnabled(context)
                hasOverlay       = PermissionManager.hasOverlayPermission(context)
                hasAccessibility = PermissionManager.isAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val edgeStyles = listOf("Full Border", "Top Only", "Bottom Only", "Left + Right", "Corners Only", "Pulse Wave")

    Scaffold(topBar = { EverlastingTopBar("Notification Lighting", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // Header
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp)) {
                    Text("💡 Notification Lighting", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("Flashlight pulse blinks the torch. Edge lighting draws a glowing animated border around your screen. Both trigger on incoming notifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }

            // ── ONE combined permission card ──────────────────────────────────
            val missingPerms = buildList {
                if (!hasNotifListener) add("Notification Access")
                if (!hasOverlay)       add("Draw Over Other Apps")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !hasAccessibility)
                    add("Accessibility Service")
            }
            if (missingPerms.isNotEmpty()) {
                InfoCard(
                    icon = Icons.Default.Lock,
                    title = "Permissions Required",
                    subtitle = "Missing: ${missingPerms.joinToString(" • ")}. Tap Grant to open the relevant settings page.",
                    isError = true,
                    actionLabel = "Grant",
                    onAction = {
                        when {
                            !hasNotifListener -> PermissionManager.openNotificationListenerSettings(context)
                            !hasOverlay       -> PermissionManager.openOverlaySettings(context)
                            else              -> PermissionManager.openAccessibilitySettings(context)
                        }
                    }
                )
            }

            // ── Screen-off only toggle ────────────────────────────────────────
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center) { Icon(Icons.Default.ScreenLockPortrait, null, tint = MaterialTheme.colorScheme.primary) }
                    Column(Modifier.weight(1f)) {
                        Text("Only When Screen Off", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text("Lighting triggers only when screen is off (recommended)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(
                        checked = onlyScreenOff,
                        onCheckedChange = {
                            onlyScreenOff = it
                            sp.edit().putBoolean("edge_lighting_only_screen_off", it).apply()
                        })
                }
            }

            // ── FLASHLIGHT PULSE ──────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Text("Flashlight Pulse", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp, top = 8.dp))

            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium)
                            .background(Color(0xFFFF9800).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center) { Text("🔦", style = MaterialTheme.typography.titleLarge) }
                        Column(Modifier.weight(1f)) {
                            Text("Flashlight Pulse", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Blink torch on each notification", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = flashEnabled, onCheckedChange = {
                            flashEnabled = it
                            sp.edit().putBoolean("flashlight_pulse_enabled", it).apply()
                            scope.launch { AppPreferences.set(AppPreferences.FLASH_NOTIF_ENABLED, it) }
                        })
                    }

                    if (flashEnabled) {
                        HorizontalDivider()
                        Column {
                            Text("Flash Count: ${flashCountVal}×", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Slider(value = flashCountVal.toFloat(),
                                onValueChange = { v ->
                                    flashCountVal = v.toInt()
                                    sp.edit().putInt("notif_flash_count", v.toInt()).apply()
                                    scope.launch { AppPreferences.set(AppPreferences.NOTIF_FLASH_COUNT, v.toInt()) }
                                },
                                valueRange = 1f..10f, steps = 8, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("1×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("10×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column {
                            Text("Blink Speed: ${flashSpeedVal}ms", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Slider(value = flashSpeedVal.toFloat(),
                                onValueChange = { v ->
                                    flashSpeedVal = v.toInt()
                                    sp.edit().putInt("notif_flash_speed_ms", v.toInt()).apply()
                                    scope.launch { AppPreferences.set(AppPreferences.NOTIF_FLASH_SPEED_MS, v.toInt()) }
                                },
                                valueRange = 50f..500f, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Fast (50ms)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Slow (500ms)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val camMgr = remember { context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager }
                        OutlinedButton(onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val camId = camMgr.cameraIdList.firstOrNull { id ->
                                        camMgr.getCameraCharacteristics(id)
                                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                                    } ?: return@launch
                                    repeat(flashCountVal) {
                                        camMgr.setTorchMode(camId, true); delay(flashSpeedVal.toLong())
                                        camMgr.setTorchMode(camId, false); delay(flashSpeedVal.toLong())
                                    }
                                    camMgr.setTorchMode(camId, false)
                                } catch (_: Exception) {}
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.FlashOn, null); Spacer(Modifier.width(6.dp))
                            Text("Preview (${flashCountVal}× at ${flashSpeedVal}ms)")
                        }
                    }
                }
            }

            // ── EDGE LIGHTING ─────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            Text("Edge Lighting", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp, top = 8.dp))

            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.medium)
                            .background(Color(0xFF9C27B0).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center) { Text("✨", style = MaterialTheme.typography.titleLarge) }
                        Column(Modifier.weight(1f)) {
                            Text("Edge Lighting", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text("Glowing border on notification", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = edgeEnabled, onCheckedChange = {
                            edgeEnabled = it
                            sp.edit().putBoolean("edge_lighting_enabled", it).apply()
                            scope.launch { AppPreferences.set(AppPreferences.EDGE_LIGHT_ENABLED, it) }
                        })
                    }

                    if (edgeEnabled) {
                        HorizontalDivider()
                        Text("Style", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        edgeStyles.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { s ->
                                    FilterChip(
                                        selected = edgeStyle == s,
                                        onClick = {
                                            edgeStyle = s
                                            sp.edit().putString("edge_lighting_style", s).apply()
                                            scope.launch { AppPreferences.set(AppPreferences.EDGE_LIGHT_STYLE, s) }
                                        },
                                        label = { Text(s, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }

                        HorizontalDivider()
                        var showEdgeColorPicker by remember { mutableStateOf(false) }
                        if (showEdgeColorPicker) {
                            EverlastingColorPickerDialog(
                                initialHex = edgeColor.ifEmpty { "#8BCAFF" },
                                onDismiss = { showEdgeColorPicker = false },
                                onColorSelected = { hex ->
                                    edgeColor = hex; customHexEdge = hex
                                    sp.edit().putString("edge_lighting_color", hex).apply()
                                    scope.launch { AppPreferences.set(AppPreferences.EDGE_LIGHT_COLOR, hex) }
                                }
                            )
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Color", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            FilledTonalButton(onClick = { showEdgeColorPicker = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                Icon(Icons.Default.Colorize, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Full Picker", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        val colorPresets = listOf(
                            "#8BCAFF","#FF5722","#4CAF50","#FFC107",
                            "#9C27B0","#00BCD4","#FF4081","#FFFFFF",
                            "#F44336","#3F51B5","#FF9800","#000000"
                        )
                        colorPresets.chunked(6).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { hex ->
                                    val col = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.White }
                                    Box(Modifier.size(36.dp).clip(CircleShape).background(col)
                                        .then(if (edgeColor.equals(hex, true)) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                        .clickable {
                                            edgeColor = hex; customHexEdge = hex
                                            sp.edit().putString("edge_lighting_color", hex).apply()
                                            scope.launch { AppPreferences.set(AppPreferences.EDGE_LIGHT_COLOR, hex) }
                                        },
                                        contentAlignment = Alignment.Center) {
                                        if (edgeColor.equals(hex, true))
                                            Icon(Icons.Default.Check, null,
                                                tint = if (hex == "#FFFFFF" || hex == "#FFC107") Color.Black else Color.White,
                                                modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customHexEdge,
                            onValueChange = { v ->
                                customHexEdge = v
                                if (v.matches(Regex("#[0-9A-Fa-f]{6}"))) {
                                    edgeColor = v
                                    sp.edit().putString("edge_lighting_color", v).apply()
                                    scope.launch { AppPreferences.set(AppPreferences.EDGE_LIGHT_COLOR, v) }
                                }
                            },
                            label = { Text("Custom hex (e.g. #FF5722)") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            leadingIcon = {
                                val previewCol = try { Color(android.graphics.Color.parseColor(customHexEdge)) } catch (_: Exception) { Color.Gray }
                                Box(Modifier.size(20.dp).clip(CircleShape).background(previewCol))
                            }
                        )

                        HorizontalDivider()
                        Column {
                            Text("Border Thickness: ${thicknessVal.toInt()}dp",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Slider(value = thicknessVal,
                                onValueChange = { v ->
                                    thicknessVal = v
                                    sp.edit().putFloat("edge_lighting_stroke_thickness", v).apply()
                                    scope.launch { AppPreferences.set(AppPreferences.EDGE_LIGHT_THICKNESS, v) }
                                },
                                valueRange = 2f..32f, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Thin (2dp)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Thick (32dp)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column {
                            Text("Display Duration: ${durationVal / 1000f}s",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Slider(value = durationVal.toFloat(),
                                onValueChange = { v ->
                                    durationVal = v.toInt()
                                    sp.edit().putInt("edge_lighting_pulse_duration", v.toInt()).apply()
                                    scope.launch { AppPreferences.set(AppPreferences.EDGE_LIGHT_DURATION_MS, v.toInt()) }
                                },
                                valueRange = 500f..10000f, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("0.5s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("10s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()
                        Column {
                            Text("Opacity: ${(alphaVal * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Slider(value = alphaVal,
                                onValueChange = { v ->
                                    alphaVal = v
                                    sp.edit().putFloat("edge_lighting_alpha", v).apply()
                                    scope.launch { AppPreferences.set(AppPreferences.EDGE_LIGHT_ALPHA, v) }
                                },
                                valueRange = 0.1f..1f, modifier = Modifier.fillMaxWidth())
                        }
                        HorizontalDivider()
                        val notifManager = remember {
                            com.coolappstore.everlastingandroidtweak.features.notiflight.NotifLightManager(context)
                        }
                        DisposableEffect(Unit) { onDispose { notifManager.release() } }
                        OutlinedButton(
                            onClick = {
                                notifManager.previewEdgeLighting(
                                    colorHex   = edgeColor.ifEmpty { "#8BCAFF" },
                                    durationMs = durationVal.toLong(),
                                    thicknessDp = thicknessVal,
                                    alphaF     = alphaVal,
                                    style      = edgeStyle
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Preview Edge Lighting")
                        }
                    }
                }
            }

            InfoCard(Icons.Default.Info, "Works Best When Screen is Off",
                "Flashlight pulse is most effective when the screen is off. Edge lighting shows as an overlay — requires Draw Over Other Apps permission.", isError = false)
        }
    }
}

// ─── BATTERY HEALTH ──────────────────────────────────────────────────────────
@Composable
fun BatteryHealthScreen(navController: NavController) {
    val context = LocalContext.current
    val batteryIntent = remember {
        context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    }

    val level    = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale    = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
    val pct      = if (level >= 0) level * 100 / scale else 0
    val status   = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
    val plugged  = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val temp     = (batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val voltage  = (batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0)
    val techStr  = batteryIntent?.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
    val health   = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH,
        android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: android.os.BatteryManager.BATTERY_HEALTH_UNKNOWN

    val healthStr = when (health) {
        android.os.BatteryManager.BATTERY_HEALTH_GOOD          -> "Good ✅"
        android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT      -> "Overheating 🔥"
        android.os.BatteryManager.BATTERY_HEALTH_DEAD          -> "Dead 💀"
        android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE  -> "Over Voltage ⚡"
        android.os.BatteryManager.BATTERY_HEALTH_COLD          -> "Cold 🧊"
        else                                                    -> "Unknown ❓"
    }
    val statusStr = when (status) {
        android.os.BatteryManager.BATTERY_STATUS_CHARGING    -> "Charging 🔌"
        android.os.BatteryManager.BATTERY_STATUS_FULL        -> "Full ✅"
        android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
        android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING-> "Not Charging"
        else -> "Unknown"
    }
    val plugStr = when (plugged) {
        android.os.BatteryManager.BATTERY_PLUGGED_AC    -> "AC Adapter"
        android.os.BatteryManager.BATTERY_PLUGGED_USB   -> "USB"
        android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        else -> "Unplugged"
    }

    var capacityInfo by remember { mutableStateOf<String?>(null) }
    var shizukuRows by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var shizukuReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Standard battery capacity (works without root on API 21+)
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val chargeCounter = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val currentNow = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (chargeCounter > 0) {
                capacityInfo = "${chargeCounter / 1000} mAh remaining"
            }
        } catch (_: Exception) {}

        // ROOT CAUSE FIX: OEM battery data was gated entirely behind Shizuku (third-party app).
        // Most users don't have Shizuku. Fix: first try reading /sys/class/power_supply/ which
        // works on most devices without root, then fall back to dumpsys via shell (works on many
        // devices from within app process), then finally try Shizuku if available.
        var dataLoaded = false

        // Attempt 1: Read /sys/class/power_supply/battery/ directly (no permissions needed)
        try {
            val psDir = java.io.File("/sys/class/power_supply")
            val battDir = psDir.listFiles()?.firstOrNull {
                it.name.lowercase().contains("battery") || it.name.lowercase() == "bms"
            }
            if (battDir != null && battDir.exists()) {
                val rows = mutableListOf<Pair<String, String>>()
                val interestingFiles = listOf(
                    "capacity" to "Capacity (%)",
                    "charge_full" to "Full Charge (µAh)",
                    "charge_full_design" to "Design Capacity (µAh)",
                    "charge_now" to "Charge Now (µAh)",
                    "cycle_count" to "Cycle Count",
                    "health" to "Health",
                    "present" to "Present",
                    "status" to "Status",
                    "technology" to "Technology",
                    "temp" to "Temperature (×10 °C)",
                    "voltage_now" to "Voltage Now (µV)",
                    "current_now" to "Current Now (µA)"
                )
                interestingFiles.forEach { (filename, label) ->
                    try {
                        val f = java.io.File(battDir, filename)
                        if (f.exists() && f.canRead()) {
                            val v = f.readText().trim()
                            if (v.isNotEmpty()) rows.add(label to v)
                        }
                    } catch (_: Exception) {}
                }
                if (rows.isNotEmpty()) {
                    shizukuRows = rows
                    shizukuReady = true
                    dataLoaded = true
                }
            }
        } catch (_: Exception) {}

        // Attempt 2: dumpsys battery via Runtime (works without Shizuku on many ROMs)
        if (!dataLoaded) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys battery"))
                val rawOutput = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                val parsed = rawOutput.lines()
                    .mapNotNull { line ->
                        val trimmed = line.trim()
                        val colonIdx = trimmed.indexOf(':')
                        if (colonIdx > 0) {
                            val key = trimmed.substring(0, colonIdx).trim().replaceFirstChar { it.uppercase() }
                            val value = trimmed.substring(colonIdx + 1).trim()
                            if (key.isNotEmpty() && value.isNotEmpty()) key to value else null
                        } else null
                    }
                    .distinctBy { it.first }
                    .take(14)
                if (parsed.isNotEmpty()) {
                    shizukuRows = parsed
                    shizukuReady = true
                    dataLoaded = true
                }
            } catch (_: Exception) {}
        }

        // Attempt 3: Shizuku (original fallback — kept for users who have it)
        if (!dataLoaded) {
            try {
                shizukuReady = com.coolappstore.everlastingandroidtweak.features.appfreezer.AppFreezerHelper.isShizukuReady()
                if (shizukuReady) {
                    val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "dumpsys battery"))
                    val rawOutput = proc.inputStream.bufferedReader().readText()
                    proc.waitFor()
                    val parsed = rawOutput.lines()
                        .mapNotNull { line ->
                            val trimmed = line.trim()
                            val colonIdx = trimmed.indexOf(':')
                            if (colonIdx > 0) {
                                val key = trimmed.substring(0, colonIdx).trim().replaceFirstChar { it.uppercase() }
                                val value = trimmed.substring(colonIdx + 1).trim()
                                if (key.isNotEmpty() && value.isNotEmpty()) key to value else null
                            } else null
                        }
                        .distinctBy { it.first }
                        .take(14)
                    if (parsed.isNotEmpty()) shizukuRows = parsed
                }
            } catch (_: Exception) {}
        }
    }

    val tempColor = when {
        temp > 45f -> Color(0xFFF44336)
        temp > 35f -> Color(0xFFFF9800)
        else       -> Color(0xFF4CAF50)
    }

    Scaffold(topBar = { EverlastingTopBar("Battery Health", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            // Big battery indicator card
            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$pct%", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black,
                        color = when {
                            pct <= 15 -> MaterialTheme.colorScheme.error
                            pct <= 30 -> MaterialTheme.colorScheme.tertiary
                            else      -> MaterialTheme.colorScheme.primary
                        })
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { pct / 100f },
                        modifier = Modifier.fillMaxWidth().height(12.dp).clip(CircleShape),
                        color = when {
                            pct <= 15 -> MaterialTheme.colorScheme.error
                            pct <= 30 -> MaterialTheme.colorScheme.tertiary
                            else      -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(statusStr, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // Stats grid
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Battery Details", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    val standardRows = listOf(
                        "Health"             to healthStr,
                        "Temperature"        to "${temp}°C",
                        "Voltage"            to "${voltage} mV",
                        "Technology"         to techStr,
                        "Power Source"       to plugStr,
                        "Charge Counter"     to (capacityInfo ?: "—"),
                    )
                    standardRows.forEachIndexed { idx, (label, value) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (label == "Temperature") tempColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (idx < standardRows.lastIndex)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }

            // Shizuku OEM data section — separate card, only shown when data available
            Spacer(Modifier.height(4.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "OEM Battery Data",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (shizukuReady) Color(0xFF4CAF50).copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ) {
                            Text(
                                if (shizukuReady) "Active" else "Inactive",
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (shizukuReady) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (!shizukuReady) {
                        Text(
                            "OEM data unavailable on this device. This data is read from /sys/class/power_supply/ or dumpsys battery — some devices restrict this.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (shizukuRows.isEmpty()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Reading battery data…", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        shizukuRows.forEachIndexed { idx, (key, value) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(key,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text(value,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (idx < shizukuRows.lastIndex)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }

            InfoCard(Icons.Default.BatteryFull, "Keep Battery Healthy",
                "Stay between 20–80% charge, avoid overnight charging, and keep temperature below 35°C for maximum battery lifespan.", isError = false)
        }
    }
}

// ─── DEVICE INFO ─────────────────────────────────────────────────────────────
@Composable
fun DeviceInfoScreen(navController: NavController) {
    val context = LocalContext.current
    data class InfoItem(val label: String, val value: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

    val items = remember {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val dm = context.resources.displayMetrics
        val battIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val battLevel = ((battIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, 0) ?: 0) * 100f /
                         (battIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100)).toInt()
        listOf(
            // Device
            InfoItem("Brand",           android.os.Build.BRAND.replaceFirstChar { it.uppercase() }, Icons.Default.PhoneAndroid),
            InfoItem("Model",           android.os.Build.MODEL, Icons.Default.PhoneAndroid),
            InfoItem("Manufacturer",    android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }, Icons.Default.Business),
            InfoItem("Device",          android.os.Build.DEVICE, Icons.Default.Devices),
            InfoItem("Product",         android.os.Build.PRODUCT, Icons.Default.Label),
            InfoItem("Hardware",        android.os.Build.HARDWARE, Icons.Default.Memory),
            // Android
            InfoItem("Android Version", android.os.Build.VERSION.RELEASE, Icons.Default.Android),
            InfoItem("API Level",       android.os.Build.VERSION.SDK_INT.toString(), Icons.Default.Code),
            InfoItem("Security Patch",  android.os.Build.VERSION.SECURITY_PATCH, Icons.Default.Security),
            InfoItem("Build ID",        android.os.Build.ID, Icons.Default.Tag),
            InfoItem("Build Type",      android.os.Build.TYPE, Icons.Default.Build),
            InfoItem("Fingerprint",     android.os.Build.FINGERPRINT.take(50), Icons.Default.Fingerprint),
            // CPU
            InfoItem("CPU ABI",         android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown", Icons.Default.Memory),
            InfoItem("CPU Cores",       Runtime.getRuntime().availableProcessors().toString(), Icons.Default.Memory),
            InfoItem("Processor",       try { java.io.File("/proc/cpuinfo").readLines().firstOrNull { it.startsWith("Hardware") }?.substringAfter(":") ?.trim() ?: "Unknown" } catch (_: Exception) { "Unknown" }, Icons.Default.Memory),
            // RAM
            InfoItem("Total RAM",       "${memInfo.totalMem / 1024 / 1024} MB", Icons.Default.Storage),
            InfoItem("Available RAM",   "${memInfo.availMem / 1024 / 1024} MB", Icons.Default.Storage),
            InfoItem("Low RAM Device",  if (memInfo.lowMemory) "Yes" else "No", Icons.Default.Warning),
            // Display
            InfoItem("Resolution",      "${dm.widthPixels} × ${dm.heightPixels}", Icons.Default.AspectRatio),
            InfoItem("Density",         "${dm.densityDpi} dpi (${dm.density}x)", Icons.Default.BlurOn),
            InfoItem("Screen Size",     "${String.format("%.1f", kotlin.math.sqrt((dm.widthPixels.toDouble() / dm.xdpi).let { it * it } + (dm.heightPixels.toDouble() / dm.ydpi).let { it * it }))}\"", Icons.Default.AspectRatio),
            InfoItem("Refresh Rate",    try { "${context.display?.refreshRate?.toInt() ?: 60} Hz" } catch (_: Exception) { "60 Hz" }, Icons.Default.Speed),
            // Battery
            InfoItem("Battery Level",   "$battLevel%", Icons.Default.BatteryFull),
            InfoItem("Battery Voltage", "${(battIntent?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0)} mV", Icons.Default.ElectricBolt),
            InfoItem("Battery Temp",    "${((battIntent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f)}°C", Icons.Default.Thermostat),
            InfoItem("Battery Tech",    battIntent?.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown", Icons.Default.BatteryFull),
            // Network
            InfoItem("Bootloader",      android.os.Build.BOOTLOADER, Icons.Default.Settings),
            InfoItem("Kernel",          try { java.io.File("/proc/version").readText().take(60) } catch (_: Exception) { "Unknown" }, Icons.Default.Terminal),
        )
    }

    val clipService = context.getSystemService(android.content.ClipboardManager::class.java)

    Scaffold(topBar = {
        EverlastingTopBar("Device Info", navController, actions = {
            IconButton(onClick = {
                val text = items.joinToString("\n") { "${it.label}: ${it.value}" }
                clipService?.setPrimaryClip(android.content.ClipData.newPlainText("Device Info", text))
                android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Default.ContentCopy, "Copy all") }
        })
    }) { padding ->
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val groups = items.chunked(1)
            // Group into cards of related items
            val sections = listOf(
                "Device" to items.take(6),
                "Android" to items.drop(6).take(6),
                "CPU" to items.drop(12).take(3),
                "Memory" to items.drop(15).take(3),
                "Display" to items.drop(18).take(4),
                "Battery" to items.drop(22).take(4),
                "System" to items.drop(26)
            )
            items(sections.size) { si ->
                val (title, sectionItems) = sections[si]
                // Assign a distinct color per section
                val sectionColor = when (si) {
                    0 -> Color(0xFF2196F3)  // Device — blue
                    1 -> Color(0xFF4CAF50)  // Android — green
                    2 -> Color(0xFFFF5722)  // CPU — deep orange
                    3 -> Color(0xFF9C27B0)  // Memory — purple
                    4 -> Color(0xFF00BCD4)  // Display — cyan
                    5 -> Color(0xFFFF9800)  // Battery — amber
                    else -> Color(0xFF607D8B) // System — blue grey
                }
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val bubbleAlpha = if (isDark) 0.24f else 0.16f

                Text(title, style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column {
                        sectionItems.forEachIndexed { idx, item ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    Modifier.size(34.dp).clip(MaterialTheme.shapes.small)
                                        .background(sectionColor.copy(alpha = bubbleAlpha)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(item.icon, null, tint = sectionColor,
                                        modifier = Modifier.size(17.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(item.label, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(item.value, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                }
                            }
                            if (idx < sectionItems.lastIndex)
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }
    }
}

// ─── SECONDARY DISPLAY ───────────────────────────────────────────────────────
@Composable
fun SecondaryDisplayScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Get list of displays
    val displayManager = remember {
        context.getSystemService(android.content.Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
    }
    var displays by remember {
        mutableStateOf(displayManager.displays.toList())
    }
    var activePresentation by remember { mutableStateOf<android.app.Presentation?>(null) }
    var presentationContent by remember { mutableStateOf("Everlasting Tweak") }
    var selectedDisplayIndex by remember { mutableIntStateOf(-1) }
    var bgColorHex by remember { mutableStateOf("#1A1A2E") }
    var showColorPicker by remember { mutableStateOf(false) }

    // Listen for display changes
    DisposableEffect(Unit) {
        val listener = object : android.hardware.display.DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                displays = displayManager.displays.toList()
            }
            override fun onDisplayRemoved(displayId: Int) {
                displays = displayManager.displays.toList()
                if (activePresentation?.display?.displayId == displayId) {
                    activePresentation?.dismiss()
                    activePresentation = null
                    selectedDisplayIndex = -1
                }
            }
            override fun onDisplayChanged(displayId: Int) {
                displays = displayManager.displays.toList()
            }
        }
        displayManager.registerDisplayListener(listener, null)
        onDispose {
            displayManager.unregisterDisplayListener(listener)
            activePresentation?.dismiss()
        }
    }

    if (showColorPicker) {
        EverlastingColorPickerDialog(
            initialHex = bgColorHex,
            onDismiss = { showColorPicker = false },
            onColorSelected = { bgColorHex = it }
        )
    }

    Scaffold(topBar = { EverlastingTopBar("Secondary Display", navController) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // Header
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "📺 Secondary Display",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Project content to a connected secondary display — TV via HDMI/MHL adapter, Miracast, or a second screen. Connect a display first, then select it below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // Displays list
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Connected Displays (${displays.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { displays = displayManager.displays.toList() }) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (displays.isEmpty()) {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📺", style = MaterialTheme.typography.displaySmall)
                            Text(
                                "No external displays found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Connect a display via HDMI adapter or Miracast",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column {
                        displays.forEachIndexed { idx, display ->
                            val isSelected = selectedDisplayIndex == idx
                            val isPrimary = display.displayId == android.view.Display.DEFAULT_DISPLAY
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isPrimary) {
                                        selectedDisplayIndex = if (isSelected) -1 else idx
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = if (!isPrimary) ({
                                        selectedDisplayIndex = if (isSelected) -1 else idx
                                    }) else null,
                                    enabled = !isPrimary
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        display.name ?: "Display ${display.displayId}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        buildString {
                                            append("ID: ${display.displayId}")
                                            if (isPrimary) append(" • This device")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isPrimary) {
                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "Primary",
                                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            if (idx < displays.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            // Content + background settings
            Spacer(Modifier.height(8.dp))
            FeatureSection("Presentation Content") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = presentationContent,
                        onValueChange = { presentationContent = it },
                        label = { Text("Text to show on secondary display") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.TextFields, null) }
                    )
                    // Background color
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                                .background(
                                    runCatching {
                                        Color(android.graphics.Color.parseColor(bgColorHex))
                                    }.getOrDefault(Color(0xFF1A1A2E))
                                )
                                .border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                        )
                        Column(Modifier.weight(1f)) {
                            Text("Background Color", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            Text(bgColorHex, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(
                            onClick = { showColorPicker = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Colorize, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pick", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            // Action buttons
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Show on secondary display
                Button(
                    onClick = {
                        val selIdx = selectedDisplayIndex
                        if (selIdx < 0 || selIdx >= displays.size) {
                            android.widget.Toast.makeText(
                                context, "Select a secondary display first", android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        scope.launch(Dispatchers.Main) {
                            try {
                                activePresentation?.dismiss()
                                val targetDisplay = displays[selIdx]
                                val bgArgb = runCatching {
                                    android.graphics.Color.parseColor(bgColorHex)
                                }.getOrDefault(android.graphics.Color.parseColor("#1A1A2E"))
                                val textToDraw = presentationContent.ifEmpty { "Everlasting Tweak" }

                                val presentation = object : android.app.Presentation(context, targetDisplay) {
                                    override fun onCreate(savedInstanceState: android.os.Bundle?) {
                                        super.onCreate(savedInstanceState)
                                        // Build a simple view in code — no XML needed
                                        val root = android.widget.FrameLayout(context).apply {
                                            setBackgroundColor(bgArgb)
                                        }
                                        val tv = android.widget.TextView(context).apply {
                                            text = textToDraw
                                            textSize = 48f
                                            setTextColor(android.graphics.Color.WHITE)
                                            gravity = android.view.Gravity.CENTER
                                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                                        }
                                        root.addView(
                                            tv,
                                            android.widget.FrameLayout.LayoutParams(
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                            )
                                        )
                                        setContentView(root)
                                    }
                                }
                                presentation.show()
                                activePresentation = presentation
                                android.widget.Toast.makeText(
                                    context, "Presenting on ${displays[selIdx].name}", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(
                                    context, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedDisplayIndex >= 0
                ) {
                    Icon(Icons.Default.Cast, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Show on Display")
                }

                // Stop
                OutlinedButton(
                    onClick = {
                        activePresentation?.dismiss()
                        activePresentation = null
                        android.widget.Toast.makeText(context, "Presentation stopped", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = activePresentation != null
                ) {
                    Icon(Icons.Default.Stop, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Stop")
                }
            }

            if (activePresentation != null) {
                Spacer(Modifier.height(4.dp))
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(
                            "Presenting on: ${displays.getOrNull(selectedDisplayIndex)?.name ?: "display"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            InfoCard(
                Icons.Default.Info, "How to Connect",
                "Use a USB-C to HDMI adapter, or enable screen mirroring / Miracast from your phone's quick settings to cast to a TV.",
                isError = false
            )
        }
    }
}
@Composable
fun FlashIsoScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var usbDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    var statusMsg by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf("") }
    var isFlashing by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedFile = it.toString() }
    }

    // Detect USB OTG devices
    LaunchedEffect(Unit) {
        try {
            val usbManager = context.getSystemService(android.hardware.usb.UsbManager::class.java)
            val devices = usbManager?.deviceList?.values?.map {
                "USB: ${it.manufacturerName ?: "Unknown"} — ${it.productName ?: "Device ${it.deviceId}"}"
            } ?: emptyList()
            usbDevices = devices.ifEmpty { listOf("No USB devices detected") }
        } catch (_: Exception) {
            usbDevices = listOf("No USB devices detected")
        }
    }

    Scaffold(topBar = { EverlastingTopBar("Flash ISO / USB", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            Card(Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("💾", style = MaterialTheme.typography.displaySmall)
                        Column {
                            Text("Flash ISO to USB", style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Flash bootable images (ISO/IMG) to USB drives via OTG",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // USB devices
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Connected USB Devices", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    val usbManager = context.getSystemService(android.hardware.usb.UsbManager::class.java)
                                    val devices = usbManager?.deviceList?.values?.map {
                                        "USB: ${it.manufacturerName ?: "Unknown"} — ${it.productName ?: "Device ${it.deviceId}"}"
                                    } ?: emptyList()
                                    usbDevices = devices.ifEmpty { listOf("No USB devices detected") }
                                } catch (_: Exception) {}
                            }
                        }) { Icon(Icons.Default.Refresh, null) }
                    }
                    usbDevices.forEach { dev ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Usb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(dev, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // File selection
            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select ISO / IMG File", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (selectedFile.isNotEmpty()) {
                        Text(android.net.Uri.parse(selectedFile).lastPathSegment ?: "Selected",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    FilledTonalButton(onClick = { filePicker.launch(arrayOf("application/octet-stream", "application/x-iso9660-image", "*/*")) },
                        modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Browse Files")
                    }
                }
            }

            // Flash button
            if (selectedFile.isNotEmpty() && usbDevices.any { !it.contains("No USB") }) {
                Button(onClick = {
                    isFlashing = true
                    statusMsg = "Flashing requires root or ADB. Use with Shizuku: dd if=<iso> of=/dev/block/sdX"
                    isFlashing = false
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = !isFlashing) {
                    Icon(Icons.Default.FlashOn, null); Spacer(Modifier.width(6.dp))
                    Text("Flash to USB")
                }
            }

            if (statusMsg.isNotEmpty()) {
                InfoCard(Icons.Default.Info, "Status", statusMsg, isError = false)
            }

            InfoCard(Icons.Default.Warning, "Requirements",
                "Flashing ISO files requires either root access or ADB. Connect a USB OTG drive, select your ISO file, then flash. This writes directly to the USB block device.",
                isError = true)

            // dd command helper
            if (selectedFile.isNotEmpty()) {
                val ddCmd = "dd if=/sdcard/your.iso of=/dev/block/sda bs=4M status=progress"
                val clipSvc = context.getSystemService(android.content.ClipboardManager::class.java)
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable {
                    clipSvc?.setPrimaryClip(android.content.ClipData.newPlainText("dd", ddCmd))
                    android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show()
                }, shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("ADB/Root command (tap to copy):", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(ddCmd, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ─── MAGNETIC FIELD ───────────────────────────────────────────────────────────
@Composable
fun MagneticFieldScreen(navController: NavController) {
    val context = LocalContext.current
    var magneticStrength by remember { mutableFloatStateOf(0f) }
    var rawX by remember { mutableFloatStateOf(0f) }
    var rawY by remember { mutableFloatStateOf(0f) }
    var rawZ by remember { mutableFloatStateOf(0f) }
    var hasSensor by remember { mutableStateOf(true) }

    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }

    DisposableEffect(Unit) {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sensor == null) { hasSensor = false }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                rawX = e.values[0]; rawY = e.values[1]; rawZ = e.values[2]
                magneticStrength = kotlin.math.sqrt((rawX * rawX + rawY * rawY + rawZ * rawZ).toDouble()).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) {}
        }
        sensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    val magnetColor = when {
        magneticStrength > 80f -> Color(0xFFF44336)
        magneticStrength > 40f -> Color(0xFFFF9800)
        else                   -> Color(0xFF4CAF50)
    }
    val magnetLabel = when {
        magneticStrength > 80f -> "High Interference"
        magneticStrength > 40f -> "Moderate"
        magneticStrength > 0f  -> "Normal"
        else                   -> "No reading"
    }

    Scaffold(topBar = { EverlastingTopBar("Magnetic Field", navController) }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            // Big reading card
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = magnetColor.copy(alpha = 0.12f)),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🧲", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (magneticStrength > 0f) "${"%.1f".format(magneticStrength)} µT" else "—",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = magnetColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = magnetColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            magnetLabel,
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = magnetColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (!hasSensor) {
                InfoCard(Icons.Default.Warning, "No Sensor",
                    "This device does not have a magnetic field sensor.", isError = true)
            }

            // Live progress bar
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Field Strength", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(
                        progress = { (magneticStrength / 120f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(10.dp)
                            .clip(MaterialTheme.shapes.small),
                        color = magnetColor,
                        trackColor = magnetColor.copy(alpha = 0.15f)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("0 µT", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("120+ µT", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // X/Y/Z axes card
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Axis Readings", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    listOf("X" to rawX, "Y" to rawY, "Z" to rawZ).forEachIndexed { idx, (axis, value) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$axis axis", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${"%.2f".format(value)} µT",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (idx < 2) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }

            InfoCard(
                Icons.Default.Info, "What it measures",
                "High values (>80 µT) indicate nearby magnets, metal objects, or electronic interference. Normal ambient field is typically 25–65 µT depending on location.",
                isError = false
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NEW FEATURE: CUSTOM POWER MENU
// ─────────────────────────────────────────────────────────────────────────────
// Uses TYPE_ACCESSIBILITY_OVERLAY — no SYSTEM_ALERT_WINDOW needed.
// The overlay is drawn and controlled by EverlastingAccessibilityService.
// This screen just configures what appears in the menu.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CustomPowerMenuScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val enabled      by AppPreferences.get(AppPreferences.POWER_MENU_ENABLED,  false).collectAsState(false)
    val savedStyle   by AppPreferences.get(AppPreferences.POWER_MENU_STYLE,    "container").collectAsState("container")
    val savedPos     by AppPreferences.get(AppPreferences.POWER_MENU_POSITION, "center").collectAsState("center")
    val showPeople   by AppPreferences.get(AppPreferences.POWER_MENU_SHOW_PEOPLE, false).collectAsState(false)
    val accessibilityActive by remember { mutableStateOf(PermissionManager.isAccessibilityEnabled(context)) }

    val styles    = listOf("container" to "Floating card (compact)", "fullscreen" to "Full-screen overlay with dim")
    val positions = listOf("center" to "Screen center", "near_power" to "Near power button (top-right)")

    Scaffold(topBar = { EverlastingTopBar("Custom Power Menu", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {

            // Hero card
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("⚡", fontSize = 32.sp)
                        Column {
                            Text("Custom Power Menu", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Replace the system power dialog with your own layout",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                        }
                    }
                }
            }

            // Accessibility requirement
            if (!accessibilityActive) {
                InfoCard(Icons.Default.Accessibility, "Accessibility Service Required",
                    "Enable the Everlasting accessibility service to use the custom power menu.",
                    isError = true, actionLabel = "Enable",
                    onAction = { PermissionManager.openAccessibilitySettings(context) })
            }

            // Enable toggle
            FeatureSection("Power Menu") {
                ToggleSettingRow("Enable Custom Power Menu",
                    "Show custom menu when power button is long-pressed", enabled,
                    { scope.launch { AppPreferences.set(AppPreferences.POWER_MENU_ENABLED, it) } })
            }

            // UI style
            FeatureSection("UI Style") {
                styles.forEachIndexed { i, (key, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(selected = savedStyle == key, onClick = {
                                scope.launch { AppPreferences.set(AppPreferences.POWER_MENU_STYLE, key) }
                            })
                        }
                    )
                    if (i < styles.lastIndex) HorizontalDivider()
                }
            }

            // Position
            FeatureSection("Position") {
                positions.forEachIndexed { i, (key, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(selected = savedPos == key, onClick = {
                                scope.launch { AppPreferences.set(AppPreferences.POWER_MENU_POSITION, key) }
                            })
                        }
                    )
                    if (i < positions.lastIndex) HorizontalDivider()
                }
            }

            // Built-in options
            FeatureSection("Menu Contents") {
                // Built-in actions (always present)
                listOf(
                    "⏻" to "Power Off",
                    "🔄" to "Restart",
                    "🔒" to "Lock Screen",
                    "🛡️" to "Lockdown Mode"
                ).forEachIndexed { i, (emoji, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = { Text(emoji, fontSize = 20.sp) },
                        trailingContent = {
                            Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    )
                    HorizontalDivider()
                }
                // People / contacts toggle
                ToggleSettingRow("Show People / Contacts",
                    "Quick-dial favourite contacts from the power menu",
                    showPeople,
                    { scope.launch { AppPreferences.set(AppPreferences.POWER_MENU_SHOW_PEOPLE, it) } })
            }

            // How to trigger
            InfoCard(Icons.Default.Info, "How to trigger",
                "When enabled, long-pressing the physical power button shows this menu instead of (or alongside) the system one. Requires Accessibility Service.",
                isError = false)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NEW FEATURE: SCREEN-OFF BUTTON ACTIONS
// ─────────────────────────────────────────────────────────────────────────────
// When the screen is OFF, long-pressing the power button or volume buttons
// for 800ms triggers the configured action (torch, DND, or a specific app).
// Powered entirely by EverlastingAccessibilityService.onKeyEvent().
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ScreenOffActionsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val enabled           by AppPreferences.get(AppPreferences.SCREEN_OFF_ACTIONS_ENABLED,   false).collectAsState(false)
    val powerLongAction   by AppPreferences.get(AppPreferences.SCREEN_OFF_POWER_LONG,        "flashlight").collectAsState("flashlight")
    val volUpLongAction   by AppPreferences.get(AppPreferences.SCREEN_OFF_VOL_UP_LONG,       "none").collectAsState("none")
    val volDownLongAction by AppPreferences.get(AppPreferences.SCREEN_OFF_VOL_DOWN_LONG,     "none").collectAsState("none")
    val powerLongApp      by AppPreferences.get(AppPreferences.SCREEN_OFF_POWER_LONG_APP,    "").collectAsState("")
    val volUpLongApp      by AppPreferences.get(AppPreferences.SCREEN_OFF_VOL_UP_LONG_APP,   "").collectAsState("")
    val volDownLongApp    by AppPreferences.get(AppPreferences.SCREEN_OFF_VOL_DOWN_LONG_APP, "").collectAsState("")

    val accessibilityActive = PermissionManager.isAccessibilityEnabled(context)

    val actionOptions = listOf(
        "none"        to "Disabled",
        "flashlight"  to "🔦  Toggle Flashlight",
        "dnd"         to "🤫  Toggle Do Not Disturb",
        "app"         to "📱  Launch App…"
    )

    // App picker
    var pickingFor by remember { mutableStateOf("") }
    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf(listOf<android.content.pm.ApplicationInfo>()) }

    LaunchedEffect(showAppPicker) {
        if (showAppPicker) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                installedApps = context.packageManager
                    .getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                    .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                    .sortedBy { context.packageManager.getApplicationLabel(it).toString() }
            }
        }
    }

    if (showAppPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAppPicker = false }) {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(16.dp).heightIn(max = 480.dp)) {
                    Text("Pick an App", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(installedApps) { app ->
                            val label = context.packageManager.getApplicationLabel(app).toString()
                            ListItem(
                                headlineContent = { Text(label) },
                                supportingContent = { Text(app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        when (pickingFor) {
                                            "power"   -> AppPreferences.set(AppPreferences.SCREEN_OFF_POWER_LONG_APP,    app.packageName)
                                            "vol_up"  -> AppPreferences.set(AppPreferences.SCREEN_OFF_VOL_UP_LONG_APP,   app.packageName)
                                            "vol_dn"  -> AppPreferences.set(AppPreferences.SCREEN_OFF_VOL_DOWN_LONG_APP, app.packageName)
                                        }
                                    }
                                    showAppPicker = false
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ButtonActionSection(
        title: String,
        currentAction: String,
        currentApp: String,
        prefKey: String,
        onPickApp: () -> Unit
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                actionOptions.forEachIndexed { i, (key, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(selected = currentAction == key, onClick = {
                                scope.launch { AppPreferences.set(
                                    when (prefKey) {
                                        "power"  -> AppPreferences.SCREEN_OFF_POWER_LONG
                                        "vol_up" -> AppPreferences.SCREEN_OFF_VOL_UP_LONG
                                        else     -> AppPreferences.SCREEN_OFF_VOL_DOWN_LONG
                                    }, key) }
                            })
                        },
                        trailingContent = if (key == "app" && currentAction == "app") ({
                            FilledTonalButton(onClick = onPickApp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(
                                    if (currentApp.isNotEmpty())
                                        context.packageManager.getApplicationLabel(
                                            context.packageManager.getApplicationInfo(currentApp, 0)
                                        ).toString().take(12)
                                    else "Pick…",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }) else null
                    )
                    if (i < actionOptions.lastIndex) HorizontalDivider()
                }
            }
        }
    }

    Scaffold(topBar = { EverlastingTopBar("Screen-Off Button Actions", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {

            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("📵", fontSize = 32.sp)
                        Column {
                            Text("Screen-Off Actions", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Trigger actions with a long-press of physical buttons while the screen is off",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                        }
                    }
                }
            }

            if (!accessibilityActive) {
                InfoCard(Icons.Default.Accessibility, "Accessibility Service Required",
                    "Enable the Everlasting accessibility service to intercept button events when the screen is off.",
                    isError = true, actionLabel = "Enable",
                    onAction = { PermissionManager.openAccessibilitySettings(context) })
            }

            FeatureSection("Screen-Off Actions") {
                ToggleSettingRow("Enable Screen-Off Actions",
                    "Long-press buttons (800ms) when screen is off to trigger actions",
                    enabled, { scope.launch { AppPreferences.set(AppPreferences.SCREEN_OFF_ACTIONS_ENABLED, it) } })
            }

            if (enabled) {
                ButtonActionSection("🔴  Power Button (long press)", powerLongAction, powerLongApp, "power") {
                    pickingFor = "power"; showAppPicker = true
                }
                ButtonActionSection("🔼  Volume Up (long press)", volUpLongAction, volUpLongApp, "vol_up") {
                    pickingFor = "vol_up"; showAppPicker = true
                }
                ButtonActionSection("🔽  Volume Down (long press)", volDownLongAction, volDownLongApp, "vol_dn") {
                    pickingFor = "vol_dn"; showAppPicker = true
                }
            }

            Spacer(Modifier.height(8.dp))
            InfoCard(Icons.Default.Info, "How it works",
                "Accessibility keeps running when the screen is off. A long hold (≥800ms) on the configured button fires the action without waking the screen (except for app launch).",
                isError = false)
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── DOUBLE POWER PRESS (existing — no change below this line) ───────────────
// (kept as-is; ChargingAnimationScreen added above)
// ─────────────────────────────────────────────────────────────────────────────
// Detects two quick presses of the power button (within 600ms) while the
// screen is ON and fires: toggle flashlight, toggle DND, or launch an app.
// Intercepted in EverlastingAccessibilityService.onKeyEvent() via
// FLAG_REQUEST_FILTER_KEY_EVENTS + KEYCODE_POWER.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DoublePowerPressScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val enabled     by AppPreferences.get(AppPreferences.DOUBLE_POWER_ENABLED, false).collectAsState(false)
    val savedAction by AppPreferences.get(AppPreferences.DOUBLE_POWER_ACTION, "flashlight").collectAsState("flashlight")
    val savedApp    by AppPreferences.get(AppPreferences.DOUBLE_POWER_APP, "").collectAsState("")

    val accessibilityActive = PermissionManager.isAccessibilityEnabled(context)

    var showAppPicker by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf(listOf<android.content.pm.ApplicationInfo>()) }

    LaunchedEffect(showAppPicker) {
        if (showAppPicker) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                installedApps = context.packageManager
                    .getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                    .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }
                    .sortedBy { context.packageManager.getApplicationLabel(it).toString() }
            }
        }
    }

    if (showAppPicker) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showAppPicker = false }) {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(16.dp).heightIn(max = 480.dp)) {
                    Text("Pick an App", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(installedApps) { app ->
                            val label = context.packageManager.getApplicationLabel(app).toString()
                            ListItem(
                                headlineContent = { Text(label) },
                                supportingContent = { Text(app.packageName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                modifier = Modifier.clickable {
                                    scope.launch { AppPreferences.set(AppPreferences.DOUBLE_POWER_APP, app.packageName) }
                                    showAppPicker = false
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    val actionOptions = listOf(
        "flashlight" to "🔦  Toggle Flashlight",
        "dnd"        to "🤫  Toggle Do Not Disturb",
        "app"        to "📱  Launch App…"
    )

    Scaffold(topBar = { EverlastingTopBar("Double Power Press", navController) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {

            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("⚡", fontSize = 32.sp)
                        Column {
                            Text("Double Power Press", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Press the power button twice quickly to trigger an action",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                        }
                    }
                }
            }

            if (!accessibilityActive) {
                InfoCard(Icons.Default.Accessibility, "Accessibility Service Required",
                    "Enable the Everlasting accessibility service to intercept double power button presses.",
                    isError = true, actionLabel = "Enable",
                    onAction = { PermissionManager.openAccessibilitySettings(context) })
            }

            FeatureSection("Double Power Press") {
                ToggleSettingRow("Enable Double Power Press",
                    "Two quick power button taps (≤600ms) trigger the selected action",
                    enabled, { scope.launch { AppPreferences.set(AppPreferences.DOUBLE_POWER_ENABLED, it) } })
            }

            // Action picker
            FeatureSection("Action") {
                actionOptions.forEachIndexed { i, (key, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            RadioButton(selected = savedAction == key, onClick = {
                                scope.launch { AppPreferences.set(AppPreferences.DOUBLE_POWER_ACTION, key) }
                            })
                        },
                        trailingContent = if (key == "app" && savedAction == "app") ({
                            FilledTonalButton(
                                onClick = { showAppPicker = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (savedApp.isNotEmpty()) {
                                        try { context.packageManager.getApplicationLabel(
                                            context.packageManager.getApplicationInfo(savedApp, 0)
                                        ).toString().take(14) } catch (_: Exception) { "Pick app" }
                                    } else "Pick app…",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }) else null
                    )
                    if (i < actionOptions.lastIndex) HorizontalDivider()
                }
            }

            // Visual indicator of selected action
            if (enabled) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("⚡⚡", fontSize = 22.sp)
                        Column {
                            Text("Double press will:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                when (savedAction) {
                                    "flashlight" -> "Toggle the flashlight"
                                    "dnd"        -> "Toggle Do Not Disturb"
                                    "app"        -> if (savedApp.isNotEmpty()) {
                                        try { "Launch " + context.packageManager.getApplicationLabel(
                                            context.packageManager.getApplicationInfo(savedApp, 0)) }
                                        catch (_: Exception) { "Launch selected app" }
                                    } else "Launch app (none selected)"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            InfoCard(Icons.Default.Info, "Note",
                "The first power press still locks the screen normally. The second press within 600ms intercepts and triggers the action instead.",
                isError = false)
            Spacer(Modifier.height(16.dp))
        }
    }
}


// ─── CHARGING ANIMATION ──────────────────────────────────────────────────────

// In-app preview composable — draws the selected animation style on a Compose Canvas
@Composable
private fun ChargingAnimationPreview(
    style: String,
    colorHex: String,
    showPct: Boolean
) {
    val color = remember(colorHex) {
        try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(colorHex)) }
        catch (_: Exception) { androidx.compose.ui.graphics.Color(0xFFFF69B4) }
    }

    // Animate a pulsing progress value
    val infiniteTransition = rememberInfiniteTransition(label = "preview_anim")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    val ripple by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ripple"
    )

    // Demo battery level for preview
    val previewPct = 14

    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(MaterialTheme.shapes.large)
            .background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w  = size.width
            val h  = size.height
            val cx = w / 2f

            when (style) {
                "ripple" -> {
                    val cy    = h / 2f
                    val maxR  = minOf(w, h) * 0.42f
                    val offsets = listOf(0f, 0.33f, 0.66f)
                    offsets.forEach { offset ->
                        val p     = (ripple + offset) % 1f
                        val r     = maxR * p
                        val alpha = ((1f - p) * 0.85f).coerceIn(0f, 1f)
                        drawCircle(
                            color = color.copy(alpha = alpha),
                            radius = r, center = Offset(cx, cy),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                        )
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "⚡", cx, cy + maxR * 0.28f,
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            textAlign  = android.graphics.Paint.Align.CENTER
                            textSize   = maxR * 0.7f
                            this.color = android.graphics.Color.WHITE
                        }
                    )
                    if (showPct) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "$previewPct% • Charging", cx, cy + maxR * 0.78f,
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign  = android.graphics.Paint.Align.CENTER
                                textSize   = 28f
                                this.color = android.graphics.Color.WHITE
                            }
                        )
                    }
                }

                "pulse" -> {
                    val cy   = h / 2f
                    val sc   = 0.7f + pulse * 0.3f
                    val r    = minOf(w, h) * 0.33f * sc
                    val cr   = color
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(cr, cr.copy(alpha = 0.3f), androidx.compose.ui.graphics.Color.Transparent),
                            center = Offset(cx, cy), radius = r
                        ),
                        radius = r, center = Offset(cx, cy)
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "⚡", cx, cy + r * 0.28f,
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            textAlign  = android.graphics.Paint.Align.CENTER
                            textSize   = r * 0.85f
                            this.color = android.graphics.Color.WHITE
                        }
                    )
                    if (showPct) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "$previewPct%", cx, cy + r * 1.35f,
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign  = android.graphics.Paint.Align.CENTER
                                textSize   = 36f
                                this.color = android.graphics.Color.WHITE
                            }
                        )
                    }
                }

                "fire" -> {
                    // Warm gradient background
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color(0xDCC83C00.toInt()),
                                androidx.compose.ui.graphics.Color(0xDCFF8C00.toInt()),
                                androidx.compose.ui.graphics.Color(0xC8FFC800.toInt())
                            )
                        )
                    )
                    val cy = h / 2f
                    drawContext.canvas.nativeCanvas.drawText(
                        "🔥", cx, cy + minOf(w, h) * 0.14f,
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            textAlign  = android.graphics.Paint.Align.CENTER
                            textSize   = minOf(w, h) * 0.28f
                            this.color = android.graphics.Color.WHITE
                        }
                    )
                    if (showPct) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "$previewPct%", cx, cy + minOf(w, h) * 0.36f,
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                textAlign  = android.graphics.Paint.Align.CENTER
                                textSize   = 44f
                                typeface   = android.graphics.Typeface.DEFAULT_BOLD
                                this.color = android.graphics.Color.WHITE
                            }
                        )
                    }
                }

                else -> {
                    // Lightning / TurboPower style — matches ChargingAnimationManager exactly
                    val cy = h * 0.42f

                    // Pulsing radial glow
                    val glowR    = minOf(w, h) * (0.38f + pulse * 0.05f)
                    val baseAlph = (0.55f + pulse * 0.20f).coerceIn(0f, 0.88f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = baseAlph),
                                color.copy(alpha = baseAlph * 0.45f),
                                androidx.compose.ui.graphics.Color.Transparent
                            ),
                            center = Offset(cx, cy), radius = glowR * 1.25f
                        ),
                        radius = glowR * 1.25f, center = Offset(cx, cy)
                    )

                    if (showPct) {
                        val numFontSz  = minOf(w, h) * 0.26f
                        val sufFontSz  = numFontSz * 0.40f

                        val numPaintN = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            this.color   = android.graphics.Color.WHITE
                            textAlign    = android.graphics.Paint.Align.LEFT
                            textSize     = numFontSz
                            typeface     = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
                        }
                        val sufPaintN = android.graphics.Paint(numPaintN).apply {
                            textSize = sufFontSz; letterSpacing = 0f
                        }

                        val numStr     = "$previewPct"
                        val numW       = numPaintN.measureText(numStr)
                        val sufW       = sufPaintN.measureText("%")
                        val totalTextW = numW + sufW
                        val baseline   = cy + numFontSz * 0.35f
                        val numLeft    = cx - totalTextW / 2f

                        drawContext.canvas.nativeCanvas.drawText(numStr, numLeft, baseline, numPaintN)
                        drawContext.canvas.nativeCanvas.drawText("%", numLeft + numW, baseline - numFontSz * 0.32f, sufPaintN)

                        // Thin arc
                        val arcR      = minOf(w, h) * 0.14f
                        val arcCenterY = baseline + numFontSz * 0.12f + arcR
                        val arcRect   = android.graphics.RectF(cx - arcR, arcCenterY - arcR, cx + arcR, arcCenterY + arcR)
                        val sweep     = (150f * previewPct / 100f).coerceAtLeast(3f)
                        val cr = (color.red * 255).toInt(); val cg = (color.green * 255).toInt(); val cb2 = (color.blue * 255).toInt()

                        // Ghost
                        val tPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            this.style  = android.graphics.Paint.Style.STROKE
                            strokeWidth = 3.5f; strokeCap = android.graphics.Paint.Cap.ROUND
                            this.color  = android.graphics.Color.argb(35, cr, cg, cb2)
                        }
                        drawContext.canvas.nativeCanvas.drawArc(arcRect, 195f, 150f, false, tPaint)

                        // Sweep
                        val aPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            this.style  = android.graphics.Paint.Style.STROKE
                            strokeWidth = 3.5f; strokeCap = android.graphics.Paint.Cap.ROUND
                            shader      = android.graphics.SweepGradient(cx, arcCenterY,
                                intArrayOf(android.graphics.Color.TRANSPARENT,
                                    android.graphics.Color.argb(200, cr, cg, cb2),
                                    android.graphics.Color.argb(220, cr, cg, cb2),
                                    android.graphics.Color.argb(200, cr, cg, cb2),
                                    android.graphics.Color.TRANSPARENT),
                                floatArrayOf(0.25f, 0.40f, 0.50f, 0.60f, 0.75f))
                        }
                        drawContext.canvas.nativeCanvas.drawArc(arcRect, 195f, sweep, false, aPaint)

                        // Bolt
                        val bCX = cx; val bCY = arcCenterY + arcR
                        val bs  = numFontSz * 0.22f
                        val bPath = android.graphics.Path().apply {
                            moveTo(bCX + bs * 0.35f, bCY - bs); lineTo(bCX - bs * 0.10f, bCY - bs * 0.05f)
                            lineTo(bCX + bs * 0.12f, bCY - bs * 0.05f); lineTo(bCX - bs * 0.35f, bCY + bs)
                            lineTo(bCX + bs * 0.10f, bCY + bs * 0.05f); lineTo(bCX - bs * 0.12f, bCY + bs * 0.05f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(bPath,
                            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                this.style = android.graphics.Paint.Style.FILL
                                this.color = android.graphics.Color.WHITE
                            })
                    }

                    // Bottom arc
                    val bArcR  = w * 0.58f
                    val bArcCY = h + bArcR * 0.28f
                    val cr2 = (color.red * 255).toInt(); val cg2 = (color.green * 255).toInt(); val cb3 = (color.blue * 255).toInt()
                    val bArcPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.style  = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f; strokeCap = android.graphics.Paint.Cap.ROUND
                        shader      = android.graphics.SweepGradient(cx, bArcCY,
                            intArrayOf(android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.argb(120, cr2, cg2, cb3),
                                android.graphics.Color.argb(190, cr2, cg2, cb3),
                                android.graphics.Color.argb(120, cr2, cg2, cb3),
                                android.graphics.Color.TRANSPARENT),
                            floatArrayOf(0.30f, 0.43f, 0.50f, 0.57f, 0.70f))
                    }
                    drawContext.canvas.nativeCanvas.drawArc(
                        android.graphics.RectF(cx - bArcR, bArcCY - bArcR, cx + bArcR, bArcCY + bArcR),
                        205f, 130f, false, bArcPaint)

                    // TurboPower label
                    val bFontSz = w * 0.038f
                    val bY      = h * 0.955f
                    val bCapY   = bY - bFontSz * 0.62f
                    val bPaint  = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        textSize      = bFontSz; textAlign = android.graphics.Paint.Align.LEFT
                        typeface      = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                        this.color    = android.graphics.Color.argb(210, 255, 255, 255)
                        letterSpacing = 0.04f
                    }
                    val tpStr  = "TurboPower"
                    val tpW    = bPaint.measureText(tpStr)
                    val bIcSz  = bFontSz * 0.38f
                    val bIcX   = cx - tpW / 2f - bIcSz * 1.6f
                    val bIcPath = android.graphics.Path().apply {
                        moveTo(bIcX + bIcSz * 0.32f, bCapY - bIcSz); lineTo(bIcX - bIcSz * 0.10f, bCapY - bIcSz * 0.05f)
                        lineTo(bIcX + bIcSz * 0.12f, bCapY - bIcSz * 0.05f); lineTo(bIcX - bIcSz * 0.32f, bCapY + bIcSz)
                        lineTo(bIcX + bIcSz * 0.10f, bCapY + bIcSz * 0.05f); lineTo(bIcX - bIcSz * 0.12f, bCapY + bIcSz * 0.05f)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(bIcPath,
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            this.style = android.graphics.Paint.Style.FILL
                            this.color = android.graphics.Color.argb(210, 255, 255, 255)
                        })
                    drawContext.canvas.nativeCanvas.drawText(tpStr, cx - tpW / 2f + bIcSz * 0.40f, bY, bPaint)
                }
            }
        }

        // "PREVIEW" watermark label
        Text(
            "PREVIEW",
            style = MaterialTheme.typography.labelSmall,
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
        )
    }
}

@Composable
fun ChargingAnimationScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val enabled   by AppPreferences.get(AppPreferences.CHARGING_ANIMATION_ENABLED, false).collectAsState(false)
    val style     by AppPreferences.get(AppPreferences.CHARGING_ANIMATION_STYLE,   "lightning").collectAsState("lightning")
    val colorHex  by AppPreferences.get(AppPreferences.CHARGING_ANIMATION_COLOR,   "#FFD600").collectAsState("#FFD600")
    val duration  by AppPreferences.get(AppPreferences.CHARGING_ANIMATION_DURATION, 4).collectAsState(4)
    val showPct   by AppPreferences.get(AppPreferences.CHARGING_ANIMATION_SHOW_PCT, true).collectAsState(true)

    var sliderVal by remember { mutableFloatStateOf(4f) }
    LaunchedEffect(duration) { sliderVal = duration.toFloat() }

    var showColorPicker by remember { mutableStateOf(false) }
    if (showColorPicker) {
        EverlastingColorPickerDialog(
            initialHex = colorHex.ifEmpty { "#FFD600" },
            onDismiss  = { showColorPicker = false },
            onColorSelected = { hex ->
                scope.launch { AppPreferences.set(AppPreferences.CHARGING_ANIMATION_COLOR, hex) }
            }
        )
    }

    // Check overlay permission
    var hasOverlay by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME)
                hasOverlay = android.provider.Settings.canDrawOverlays(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val styles = listOf(
        "lightning" to "⚡ Lightning",
        "ripple"    to "🌊 Ripple",
        "pulse"     to "💫 Pulse",
        "fire"      to "🔥 Fire"
    )
    val presetColors = listOf(
        "#FFD600" to "Gold",  "#2196F3" to "Blue",  "#4CAF50" to "Green",
        "#FF5722" to "Red",   "#9C27B0" to "Purple","#00BCD4" to "Cyan",
        "#FF4081" to "Pink",  "#FFFFFF" to "White"
    )

    Scaffold(topBar = { EverlastingTopBar("Charging Animation", navController) }) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                // Header with Settings navigation button
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)) {
                                Text("⚡", style = MaterialTheme.typography.displaySmall)
                                Column {
                                    Text("Charging Animation",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(
                                        if (enabled) "● ACTIVE — shows when charger connects"
                                        else "Tap the toggle to activate",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (enabled) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            // Settings navigation button
                            FilledTonalIconButton(
                                onClick = { navController.navigate(com.coolappstore.everlastingandroidtweak.ui.navigation.Screen.Settings.route) }
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                }
            }

            item {
                if (!hasOverlay) {
                    InfoCard(
                        Icons.Default.Layers, "Overlay Permission Required",
                        "Display Over Other Apps must be granted for the animation to appear.",
                        isError = true, actionLabel = "Grant",
                        onAction = { PermissionManager.openOverlaySettings(context) }
                    )
                }
            }

            item {
                // ── In-app live preview ──────────────────────────────────
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        "Live Preview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    ChargingAnimationPreview(
                        style    = style,
                        colorHex = colorHex,
                        showPct  = showPct
                    )
                }
            }

            item {
                // Enable toggle
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enable Charging Animation",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold)
                            Text("Show animation overlay when charger is connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(
                            checked = enabled,
                            onCheckedChange = {
                                if (!hasOverlay) { PermissionManager.openOverlaySettings(context) }
                                else { scope.launch { AppPreferences.set(AppPreferences.CHARGING_ANIMATION_ENABLED, it) } }
                            }
                        )
                    }
                }
            }

            item {
                // Style picker
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Animation Style", style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(10.dp))
                        styles.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (key, label) ->
                                    FilterChip(
                                        selected = style == key,
                                        onClick  = { scope.launch { AppPreferences.set(AppPreferences.CHARGING_ANIMATION_STYLE, key) } },
                                        label    = { Text(label) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            item {
                // Color picker
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Animation Color", style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            FilledTonalButton(
                                onClick = { showColorPicker = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Colorize, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Full Picker", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        presetColors.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { (hex, name) ->
                                    val col = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)) }
                                              catch (_: Exception) { androidx.compose.ui.graphics.Color.White }
                                    val sel = colorHex.equals(hex, ignoreCase = true)
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(col)
                                                .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape) else Modifier)
                                                .clickable { scope.launch { AppPreferences.set(AppPreferences.CHARGING_ANIMATION_COLOR, hex) } },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (sel) Icon(Icons.Default.Check, null,
                                                tint = if (hex == "#FFFFFF" || hex == "#FFD600") androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.size(20.dp))
                                        }
                                        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            item {
                // Duration + show % toggles
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Display Duration: ${if (sliderVal == 0f) "Until unplugged" else "${sliderVal.toInt()}s"}",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Slider(
                            value = sliderVal,
                            onValueChange = {
                                sliderVal = it
                                scope.launch { AppPreferences.set(AppPreferences.CHARGING_ANIMATION_DURATION, it.toInt()) }
                            },
                            valueRange = 0f..15f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Until unplugged", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("15 s", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Show Battery Percentage",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold)
                                Text("Display current % inside the animation",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(checked = showPct, onCheckedChange = {
                                scope.launch { AppPreferences.set(AppPreferences.CHARGING_ANIMATION_SHOW_PCT, it) }
                            })
                        }
                    }
                }
            }

            item {
                InfoCard(Icons.Default.Info, "Tap to Dismiss",
                    "The animation overlay appears as soon as the charger is connected. Tap anywhere on the animation to dismiss it early.",
                    isError = false)
            }

            item {
                InfoCard(Icons.Default.BatteryChargingFull, "Battery Impact",
                    "The animation runs briefly (default 4 seconds) then disappears automatically, using minimal battery. Set duration to 0 to keep it until you unplug.",
                    isError = false)
            }
        }
    }
}
