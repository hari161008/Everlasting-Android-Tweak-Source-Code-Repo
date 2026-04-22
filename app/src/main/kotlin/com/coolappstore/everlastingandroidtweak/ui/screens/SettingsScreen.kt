package com.coolappstore.everlastingandroidtweak.ui.screens

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.ui.components.EverlastingTopBar
import com.coolappstore.everlastingandroidtweak.ui.components.EverlastingColorPickerDialog
import com.coolappstore.everlastingandroidtweak.utils.PermissionManager
import com.aistra.hail.app.HailData
import androidx.preference.PreferenceManager
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Environment

@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Fix: device back button should navigate to Home, not close the app
    BackHandler { navController.popBackStack() }

    val dynamicColor   by AppPreferences.get(AppPreferences.DYNAMIC_COLOR, true).collectAsState(true)
    val darkTheme      by AppPreferences.get(AppPreferences.DARK_THEME, 0).collectAsState(0)
    val homePillStyle      by AppPreferences.get(AppPreferences.HOME_PILL_STYLE, 0).collectAsState(0)
    val homePillBlurIntensity by AppPreferences.get(AppPreferences.HOME_PILL_BLUR_INTENSITY, 16f).collectAsState(16f)

    // ── Sync Everlasting prefs → Hail SharedPreferences ──────────────────────
    // Hail reads synchronously from SharedPreferences so we mirror the two
    // theme-related DataStore values there whenever they change.
    val hailPrefs = remember {
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    }
    LaunchedEffect(dynamicColor) {
        hailPrefs.edit().putBoolean(com.aistra.hail.app.HailData.EVERLASTING_DYNAMIC_COLOR, dynamicColor).apply()
    }
    LaunchedEffect(darkTheme) {
        hailPrefs.edit().putInt(com.aistra.hail.app.HailData.EVERLASTING_THEME_MODE, darkTheme).apply()
    }
    LaunchedEffect(homePillStyle) {
        hailPrefs.edit().putInt(com.aistra.hail.app.HailData.HOME_PILL_STYLE, homePillStyle).apply()
    }
    LaunchedEffect(homePillBlurIntensity) {
        hailPrefs.edit().putFloat(com.aistra.hail.app.HailData.HOME_PILL_BLUR_INTENSITY, homePillBlurIntensity).apply()
    }
    // Sync custom primary colour: strip the leading '#' and write as AARRGGBB hex string.
    // Hail reads this only when dynamic colour is off, so the guard is safe to skip here.
    val customPrimary  by AppPreferences.get(AppPreferences.CUSTOM_PRIMARY_COLOR, "").collectAsState("")
    LaunchedEffect(customPrimary) {
        val hex = customPrimary.trimStart('#')
        hailPrefs.edit().putString(com.aistra.hail.app.HailData.EVERLASTING_CUSTOM_PRIMARY, hex).apply()
    }
    // ─────────────────────────────────────────────────────────────────────────
    val uiBlur         by AppPreferences.get(AppPreferences.UI_BLUR_ENABLED, false).collectAsState(false)
    val uiBlurAmount   by AppPreferences.get(AppPreferences.UI_BLUR_AMOUNT, 16f).collectAsState(16f)
    val bgUri          by AppPreferences.get(AppPreferences.BG_WALLPAPER_URI, "").collectAsState("")
    val bgDim          by AppPreferences.get(AppPreferences.BG_DIM_AMOUNT, 0f).collectAsState(0f)
    val bgBlur         by AppPreferences.get(AppPreferences.BG_BLUR_ENABLED, false).collectAsState(false)
    val bgBlurAmount   by AppPreferences.get(AppPreferences.BG_BLUR_AMOUNT, 16f).collectAsState(16f)
    val useDeviceWp    by AppPreferences.get(AppPreferences.USE_DEVICE_WALLPAPER, false).collectAsState(false)
    val useEmojiIcons  by AppPreferences.get(AppPreferences.USE_EMOJI_ICONS, false).collectAsState(false)
    val iconStyleSolid      by AppPreferences.get(AppPreferences.ICON_STYLE_SOLID, false).collectAsState(false)
    val toggleAnimEnabled   by AppPreferences.get(AppPreferences.TOGGLE_ANIMATION_ENABLED, true).collectAsState(true)
    val settingsDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val autoUpdate     by AppPreferences.get(AppPreferences.AUTO_UPDATE_ENABLED, false).collectAsState(false)
    val updateInterval by AppPreferences.get(AppPreferences.AUTO_UPDATE_INTERVAL_HOURS, 24).collectAsState(24)
    var bgDimVal       by remember { mutableFloatStateOf(0f) }
    var bgBlurVal      by remember { mutableFloatStateOf(16f) }
    var uiBlurVal      by remember { mutableFloatStateOf(16f) }
    LaunchedEffect(bgDim)      { bgDimVal = bgDim }
    LaunchedEffect(bgBlurAmount) { bgBlurVal = bgBlurAmount }
    LaunchedEffect(uiBlurAmount) { uiBlurVal = uiBlurAmount }

    // ── Permission state — polled every 2 s so readings are always live ─────
    // ROOT CAUSE FIX: previously, non-Shizuku permissions were only refreshed on
    // ON_RESUME. If the user granted Accessibility/Overlay in Settings and quickly
    // came back, the state was stale until the next resume event. Now every
    // permission is re-checked every 2 seconds while SettingsScreen is active.
    var grantedCamera        by remember { mutableStateOf(PermissionManager.hasCamera(context)) }
    var grantedAudio         by remember { mutableStateOf(PermissionManager.hasRecordAudio(context)) }
    var grantedNotifications by remember { mutableStateOf(PermissionManager.hasPostNotifications(context)) }
    var grantedMedia         by remember { mutableStateOf(PermissionManager.hasReadMediaImages(context)) }
    var grantedAccessibility by remember { mutableStateOf(PermissionManager.isAccessibilityEnabled(context)) }
    var grantedOverlay       by remember { mutableStateOf(PermissionManager.hasOverlayPermission(context)) }
    var grantedNotifListener by remember { mutableStateOf(PermissionManager.isNotificationListenerEnabled(context)) }
    var grantedUsageStats    by remember { mutableStateOf(PermissionManager.hasUsageStatsPermission(context)) }
    var grantedAlarm         by remember { mutableStateOf(PermissionManager.hasExactAlarmPermission(context)) }

    // Single poller replaces both the DisposableEffect observer AND the Shizuku-only loop
    val grantedShizuku = remember { mutableStateOf(PermissionManager.isShizukuGranted()) }
    LaunchedEffect(Unit) {
        while (true) {
            grantedCamera        = PermissionManager.hasCamera(context)
            grantedAudio         = PermissionManager.hasRecordAudio(context)
            grantedNotifications = PermissionManager.hasPostNotifications(context)
            grantedMedia         = PermissionManager.hasReadMediaImages(context)
            grantedAccessibility = PermissionManager.isAccessibilityEnabled(context)
            grantedOverlay       = PermissionManager.hasOverlayPermission(context)
            grantedNotifListener = PermissionManager.isNotificationListenerEnabled(context)
            grantedUsageStats    = PermissionManager.hasUsageStatsPermission(context)
            grantedAlarm         = PermissionManager.hasExactAlarmPermission(context)
            grantedShizuku.value = PermissionManager.isShizukuGranted()
            kotlinx.coroutines.delay(2000L)
        }
    }
    // ── Update download state ─────────────────────────────────────────────────
    // States: "idle" | "checking" | "downloading" | "ready" | "installing"
    var updateState       by remember { mutableStateOf("idle") }
    var updateProgress    by remember { mutableIntStateOf(0) }
    var pendingApkFile    by remember { mutableStateOf<java.io.File?>(null) }
    var pendingApkVersion by remember { mutableStateOf("") }
    var showInstallDialog by remember { mutableStateOf(false) }
    val updatePrefs = remember { context.getSharedPreferences("eat_update_prefs", android.content.Context.MODE_PRIVATE) }

    // Restore state on first load: if APK was downloaded before (install was cancelled),
    // check if the install succeeded since then (version changed → delete APK) or not (offer install).
    LaunchedEffect(Unit) {
        val savedPath    = updatePrefs.getString("pending_apk_path", null)    ?: return@LaunchedEffect
        val savedVersion = updatePrefs.getString("pending_apk_version", null) ?: return@LaunchedEffect
        val file = java.io.File(savedPath)
        val curVersion = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() } catch (_: Exception) { "" }
        when {
            !file.exists() -> updatePrefs.edit().clear().apply()
            curVersion == savedVersion -> {
                // Install was cancelled — APK still ready
                pendingApkFile    = file
                pendingApkVersion = savedVersion
                updateState       = "ready"
            }
            else -> {
                // Version changed → install succeeded → clean up
                file.delete()
                updatePrefs.edit().clear().apply()
            }
        }
    }

    // Lambda to launch the system install popup for the downloaded APK
    val launchInstall: (java.io.File) -> Unit = { apk ->
        val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", apk)
        else Uri.fromFile(apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        updateState = "installing"
    }

    // Poll for install completion: once the installed version changes, delete the APK.
    LaunchedEffect(updateState) {
        if (updateState != "installing") return@LaunchedEffect
        val apk            = pendingApkFile           ?: return@LaunchedEffect
        val expectedVersion = updatePrefs.getString("pending_apk_version", null) ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(3000L)
            val curVersion = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() } catch (_: Exception) { "" }
            if (curVersion != expectedVersion) {
                apk.delete()
                updatePrefs.edit().clear().apply()
                pendingApkFile = null
                updateState    = "idle"
                break
            }
        }
    }

    // Install confirmation dialog (shown automatically after download, and again if user taps Install button)
    if (showInstallDialog && pendingApkFile != null) {
        AlertDialog(
            onDismissRequest = { showInstallDialog = false },
            icon  = { Icon(Icons.Default.SystemUpdateAlt, null, tint = Color(0xFF4CAF50)) },
            title = { Text("Install Update v$pendingApkVersion") },
            text  = { Text("APK downloaded to your Downloads folder. Install it now?") },
            confirmButton = {
                Button(onClick = {
                    showInstallDialog = false
                    launchInstall(pendingApkFile!!)
                }) { Text("Install Now") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showInstallDialog = false }) { Text("Later") }
            }
        )
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // permLauncher is still needed for runtime permission dialogs; the 2s poller
        // will pick up the new state within 2 seconds automatically.
    }

    val grantedCount = listOf(grantedCamera, grantedAudio, grantedNotifications, grantedMedia,
        grantedAccessibility, grantedOverlay, grantedNotifListener, grantedUsageStats,
        grantedShizuku.value).count { it }

    // Background picker — Android Photo Picker on API 33+, file manager fallback on older
    val bgPickerModern = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { selectedUri ->
            scope.launch(Dispatchers.IO) {
                try {
                    context.filesDir.listFiles { f -> f.name.startsWith("bg_wallpaper_") && f.name.endsWith(".jpg") }?.forEach { it.delete() }
                    val dest = java.io.File(context.filesDir, "bg_wallpaper_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(selectedUri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                    val fileUri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", dest)
                    AppPreferences.set(AppPreferences.BG_WALLPAPER_URI, fileUri.toString())
                    AppPreferences.set(AppPreferences.USE_DEVICE_WALLPAPER, false)
                } catch (_: Exception) { AppPreferences.set(AppPreferences.BG_WALLPAPER_URI, selectedUri.toString()) }
            }
        }
    }
    val bgPickerLegacy = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedUri ->
            try { context.contentResolver.takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            scope.launch(Dispatchers.IO) {
                try {
                    context.filesDir.listFiles { f -> f.name.startsWith("bg_wallpaper_") && f.name.endsWith(".jpg") }?.forEach { it.delete() }
                    val dest = java.io.File(context.filesDir, "bg_wallpaper_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(selectedUri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                    val fileUri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", dest)
                    AppPreferences.set(AppPreferences.BG_WALLPAPER_URI, fileUri.toString())
                    AppPreferences.set(AppPreferences.USE_DEVICE_WALLPAPER, false)
                } catch (_: Exception) { AppPreferences.set(AppPreferences.BG_WALLPAPER_URI, selectedUri.toString()) }
            }
        }
    }

    // Backup — serialize ALL preference keys to JSON
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val p = AppPreferences
                    val json = org.json.JSONObject()
                    json.put("app", "EverlastingTweak"); json.put("version", com.coolappstore.everlastingandroidtweak.BuildConfig.VERSION_NAME)
                    json.put("date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
                    // ── Boolean keys ──────────────────────────────────────────────────
                    suspend fun b(k: androidx.datastore.preferences.core.Preferences.Key<Boolean>, d: Boolean) =
                        json.put(k.name, p.get(k, d).first())
                    b(p.SHAKE_TORCH_ENABLED, false); b(p.SHAKE_PROXIMITY_ENABLED, false)
                    b(p.TWIST_CAMERA_ENABLED, false); b(p.TWIST_PROXIMITY_ENABLED, false)
                    b(p.CUSTOM_HAPTICS_ENABLED, false); b(p.HAPTICS_SCROLL_ENABLED, false)
                    b(p.TAP_SOUND_ENABLED, false); b(p.LOCK_SOUND_ENABLED, false)
                    b(p.UNLOCK_SOUND_ENABLED, false); b(p.MUSIC_LIGHT_ENABLED, false)
                    b(p.MUSIC_VIBRATE_ENABLED, false); b(p.WATERMARK_ENABLED, false)
                    b(p.WATERMARK_BOLD, false); b(p.WATERMARK_SHADOW, false)
                    b(p.AUTO_REBOOT_ENABLED, false); b(p.DYNAMIC_COLOR, true)
                    b(p.SCREENSHOT_BLOCK_ENABLED, false); b(p.NAVBAR_OVERLAY_ENABLED, false)
                    b(p.EQUALIZER_ENABLED, false); b(p.KEEP_SCREEN_ON_ENABLED, false)
                    b(p.DOUBLE_TAP_BACK_ENABLED, false); b(p.CHARGING_SOUND_ENABLED, false)
                    b(p.CHARGING_ANIMATION_ENABLED, false); b(p.CHARGING_ANIMATION_SHOW_PCT, false)
                    b(p.FLIP_DND_ENABLED, false); b(p.VOLUME_BOOST_ENABLED, false)
                    b(p.MUSIC_LEVELER_ENABLED, false); b(p.MUSIC_LEVELER_AUTO_HIDE, false)
                    b(p.SECURITY_MOTION_ENABLED, false); b(p.SECURITY_MOTION_SOUND_ENABLED, false)
                    b(p.WALKIE_TALKIE_ENABLED, false); b(p.COMPASS_CALIBRATED, false)
                    b(p.CHARGE_LIMIT_ENABLED, false); b(p.APP_FREEZER_ENABLED, false)
                    b(p.POWER_SAVING_MAPS_ENABLED, false); b(p.LOCK_WIDGETS_ENABLED, false)
                    b(p.UI_BLUR_ENABLED, false); b(p.EDGE_LIGHT_ENABLED, false)
                    b(p.FLASH_NOTIF_ENABLED, false); b(p.AUTO_UPDATE_ENABLED, false)
                    b(p.BG_BLUR_ENABLED, false); b(p.USE_DEVICE_WALLPAPER, false)
                    b(p.USE_EMOJI_ICONS, false); b(p.ICON_STYLE_SOLID, false)
                    b(p.FREEZE_TAB_PILL_SOLID, true); b(p.TOGGLE_ANIMATION_ENABLED, true)
                    b(p.FIRST_LAUNCH_DONE, false); b(p.WELCOME_POPUP_SHOWN, false)
                    b(p.MAGNETIC_FIELD_IN_HOME, false); b(p.POWER_MENU_ENABLED, false)
                    b(p.POWER_MENU_SHOW_PEOPLE, false); b(p.SCREEN_OFF_ACTIONS_ENABLED, false)
                    b(p.DOUBLE_POWER_ENABLED, false); b(p.FAKE_POWER_OFF_ENABLED, false)
                    b(p.FAKE_POWER_OFF_LOCK_DEVICE, false); b(p.FAKE_POWER_OFF_DND, false)
                    b(p.NAVBAR_USE_ACCESSIBILITY, false); b(p.CALL_VIBRATION_ENABLED, false)
                    b(p.ALARM_VIBRATION_ENABLED, false); b(p.NOTIF_VIBRATION_ENABLED, false)
                    b(p.VOLUME_STYLES_ENABLED, false); b(p.CHARGE_REPEAT_ENABLED, false)
                    b(p.SCREENSAVER_MOVE_ENABLED, false); b(p.SCREENSAVER_SHOW_BATTERY, false)
                    b(p.SCREENSAVER_SHOW_DATE, false); b(p.SCREENSAVER_BURN_IN_ENABLED, false)
                    b(p.SCREENSAVER_MOTO_SHOW_BRANDING, true); b(p.SCREENSAVER_MOTO_SHOW_ARC, true)
                    b(p.SCREENSAVER_MOTO_SHOW_BATTERY, true); b(p.SCREENSAVER_MOTO_USE_REAL_PCT, true)
                    b(p.SCREENSAVER_MOTO_ARC_PROGRESS, true); b(p.SCREENSAVER_MOTO_SECONDARY_GLOW, false)
                    b(p.SCREENSAVER_MOTO_BG_VIGNETTE, false); b(p.SCREENSAVER_MOTO_SHOW_CHARGING_TEXT, true)
                    b(p.SCREENSAVER_WP_IS_24H, false); b(p.SCREENSAVER_WP_SHOW_DATE, true)
                    b(p.SCREENSAVER_WP_SHOW_WEATHER, false); b(p.SCREENSAVER_WP_SHOW_EVENTS, false)
                    b(p.SCREENSAVER_WP_SHOW_NOTIF, false); b(p.SCREENSAVER_WP_SHOW_ALARM_ICON, false)
                    b(p.SCREENSAVER_WP_SHOW_SECONDS, false); b(p.SCREENSAVER_WP_SHOW_SEPARATOR, false)
                    b(p.SCREENSAVER_WP_COMPACT_MODE, false); b(p.SCREENSAVER_WP_SHOW_WEEK_NUMBER, false)
                    b(p.SCREENSAVER_WP_SHOW_BATTERY, false)
                    // ── Int keys ──────────────────────────────────────────────────────
                    suspend fun i(k: androidx.datastore.preferences.core.Preferences.Key<Int>, d: Int) =
                        json.put(k.name, p.get(k, d).first())
                    i(p.DARK_THEME, 0); i(p.HAPTICS_INTENSITY, 50); i(p.HAPTICS_TAP_INTENSITY, 50)
                    i(p.HAPTICS_SCROLL_INTENSITY, 30); i(p.KEEP_SCREEN_ON_TIMEOUT, 0)
                    i(p.CHARGE_LIMIT_PERCENT, 80); i(p.VOLUME_BOOST_LEVEL, 20)
                    i(p.CHARGING_ANIMATION_DURATION, 30); i(p.HOME_PILL_STYLE, 0)
                    i(p.TASK_UPDATE_INTERVAL, 3); i(p.TERMINAL_THEME_INDEX, 0)
                    i(p.AUTO_UPDATE_INTERVAL_HOURS, 24); i(p.SCREENSAVER_FADE_DURATION, 1000)
                    i(p.SCREENSAVER_MOVE_SPEED, 3); i(p.SCREENSAVER_MOVE_INTERVAL_S, 30)
                    i(p.SCREENSAVER_BURN_IN_INTERVAL, 60); i(p.SCREENSAVER_MOTO_CUSTOM_PCT, 75)
                    i(p.SCREENSAVER_MOTO_GLOW_LAYERS, 3); i(p.SCREENSAVER_WP_PHONE_COUNT, 0)
                    i(p.SCREENSAVER_WP_EMAIL_COUNT, 0); i(p.NOTIF_FLASH_COUNT, 3)
                    i(p.NOTIF_FLASH_SPEED_MS, 300); i(p.EDGE_LIGHT_DURATION_MS, 3000)
                    i(p.FAKE_CALL_DELAY, 5)
                    // ── Float keys ────────────────────────────────────────────────────
                    suspend fun f(k: androidx.datastore.preferences.core.Preferences.Key<Float>, d: Float) =
                        json.put(k.name, p.get(k, d).first().toDouble())
                    f(p.SHAKE_SENSITIVITY, 12f); f(p.TWIST_SENSITIVITY, 3f)
                    f(p.MUSIC_LIGHT_SENSITIVITY, 0.5f); f(p.MUSIC_BLINK_SPEED, 0.5f)
                    f(p.MUSIC_SPEED_SENSITIVITY, 0.5f); f(p.WATERMARK_FONT_SIZE, 14f)
                    f(p.WATERMARK_OPACITY, 0.7f); f(p.NAVBAR_HEIGHT, 48f)
                    f(p.NAVBAR_PILL_OPACITY, 0.8f); f(p.NAVBAR_X_POSITION, 0.5f)
                    f(p.NAVBAR_Y_POSITION, 0.9f); f(p.VOLUME_CORNER_RADIUS, 12f)
                    f(p.VOLUME_OPACITY, 0.9f); f(p.SCREENSAVER_SIZE, 1f)
                    f(p.SCREENSAVER_MOTO_GLOW_SIZE, 0.45f); f(p.SCREENSAVER_MOTO_PULSE_SPEED, 1f)
                    f(p.SCREENSAVER_MOTO_FONT_SIZE, 120f); f(p.SCREENSAVER_MOTO_GLOW_INTENSITY, 0.7f)
                    f(p.SCREENSAVER_MOTO_GLOW_OFFSET_X, 0.5f); f(p.SCREENSAVER_MOTO_GLOW_OFFSET_Y, 0.4f)
                    f(p.SCREENSAVER_MOTO_NUM_OFFSET_X, 0.5f); f(p.SCREENSAVER_MOTO_NUM_OFFSET_Y, 0.35f)
                    f(p.SCREENSAVER_MOTO_ARC_OFFSET_X, 0.5f); f(p.SCREENSAVER_MOTO_ARC_OFFSET_Y_ADJ, 0f)
                    f(p.SCREENSAVER_MOTO_BRANDING_OFFSET_X, 0.5f); f(p.SCREENSAVER_MOTO_BRANDING_OFFSET_Y, 0.88f)
                    f(p.SCREENSAVER_MOTO_ARC_RADIUS_MULT, 0.38f); f(p.SCREENSAVER_MOTO_ARC_GAP_MULT, 0.08f)
                    f(p.SCREENSAVER_MOTO_ARC_STROKE_WIDTH, 6f); f(p.SCREENSAVER_MOTO_ARC_ANGLE_START, 210f)
                    f(p.SCREENSAVER_MOTO_ARC_ANGLE_SWEEP, 120f); f(p.SCREENSAVER_MOTO_BOLT_SIZE_MULT, 0.35f)
                    f(p.SCREENSAVER_MOTO_BOLT_OFFSET_Y, 0.03f); f(p.SCREENSAVER_MOTO_NUM_COLOR_OPACITY, 1f)
                    f(p.SCREENSAVER_MOTO_NUM_LETTER_SPC, 0f); f(p.SCREENSAVER_MOTO_SUFFIX_SIZE_MULT, 0.35f)
                    f(p.SCREENSAVER_WP_FONT_SIZE, 1f); f(p.SCREENSAVER_WP_LETTER_SPACING, 0f)
                    f(p.SCREENSAVER_WP_DATE_OPACITY, 0.7f); f(p.SCREENSAVER_WP_CLOCK_VERTICAL_POS, 0.5f)
                    f(p.SCREENSAVER_WP_CLOCK_SIZE, 1f); f(p.SCREENSAVER_WP_CLOCK_SIZE_SP, 64f)
                    f(p.SCREENSAVER_WP_DATE_SIZE_SP, 18f); f(p.SCREENSAVER_WP_WEATHER_SIZE_SP, 16f)
                    f(p.SCREENSAVER_WP_NOTIF_SIZE_SP, 14f); f(p.SCREENSAVER_WP_PADDING_LEFT, 32f)
                    f(p.SECURITY_MOTION_SENSITIVITY, 12f); f(p.MUSIC_LEVELER_HEIGHT, 4f)
                    f(p.MUSIC_LEVELER_OPACITY, 0.8f); f(p.BG_DIM_AMOUNT, 0f)
                    f(p.BG_BLUR_AMOUNT, 16f); f(p.UI_BLUR_AMOUNT, 16f)
                    f(p.HOME_PILL_BLUR_INTENSITY, 16f); f(p.EDGE_LIGHT_THICKNESS, 4f)
                    f(p.EDGE_LIGHT_ALPHA, 0.9f)
                    // ── String keys ───────────────────────────────────────────────────
                    suspend fun s(k: androidx.datastore.preferences.core.Preferences.Key<String>, d: String) =
                        json.put(k.name, p.get(k, d).first())
                    s(p.HAPTICS_PATTERN, "default"); s(p.TAP_SOUND_URI, "")
                    s(p.LOCK_SOUND_URI, ""); s(p.UNLOCK_SOUND_URI, "")
                    s(p.WATERMARK_TEXT, ""); s(p.WATERMARK_POSITION, "BottomRight")
                    s(p.WATERMARK_COLOR, "#FFFFFF"); s(p.AUTO_REBOOT_TIME, "03:00")
                    s(p.AUTO_REBOOT_DAYS, ""); s(p.CUSTOM_PRIMARY_COLOR, "")
                    s(p.CUSTOM_SECONDARY_COLOR, ""); s(p.NAVBAR_STYLE, "pill")
                    s(p.NAVBAR_PILL_COLOR, "#FFFFFF"); s(p.VOLUME_STYLE, "default")
                    s(p.VOLUME_COLOR, "#FFFFFF"); s(p.SCREENSAVER_THEME, "clock")
                    s(p.SCREENSAVER_COLOR, "#FFFFFF"); s(p.SCREENSAVER_CLOCK_STYLE, "Digital")
                    s(p.SCREENSAVER_CLOCK_COLOR, "#FFFFFF"); s(p.SCREENSAVER_MOTO_GLOW_COLOR, "#4FC3F7")
                    s(p.SCREENSAVER_MOTO_TEXT_COLOR, "#FFFFFF"); s(p.SCREENSAVER_MOTO_ARC_COLOR, "#4FC3F7")
                    s(p.SCREENSAVER_MOTO_BRANDING_TEXT, "moto"); s(p.SCREENSAVER_MOTO_BG_COLOR, "#000000")
                    s(p.SCREENSAVER_MOTO_SECONDARY_COLOR, "#FFFFFF"); s(p.SCREENSAVER_MOTO_GLOW_SHAPE, "Circle")
                    s(p.SCREENSAVER_MOTO_BOLT_STYLE, "Filled"); s(p.SCREENSAVER_MOTO_PCT_SUFFIX_STYLE, "%")
                    s(p.SCREENSAVER_MOTO_CHARGING_TEXT, "Charging"); s(p.SCREENSAVER_MOTO_ANIMATION_STYLE, "Pulse")
                    s(p.SCREENSAVER_MOTO_NUM_FONT_WEIGHT, "Thin"); s(p.SCREENSAVER_WP_TEXT_COLOR, "#FFFFFF")
                    s(p.SCREENSAVER_WP_LAYOUT, "Left"); s(p.SCREENSAVER_WP_CITY, "")
                    s(p.SCREENSAVER_WP_CONDITION, ""); s(p.SCREENSAVER_WP_TEMPERATURE, "")
                    s(p.SCREENSAVER_WP_TEMP_HIGH, ""); s(p.SCREENSAVER_WP_TEMP_LOW, "")
                    s(p.SCREENSAVER_WP_BG_COLOR, "#000000"); s(p.SCREENSAVER_WP_CLOCK_WEIGHT, "Thin")
                    s(p.SCREENSAVER_WP_TIME_COLOR, "#FFFFFF"); s(p.SCREENSAVER_WP_CLOCK_POSITION, "Left")
                    s(p.SCREENSAVER_WP_ACCENT_COLOR, "#4FC3F7"); s(p.SCREENSAVER_WP_NOTIF_STYLE, "Icons")
                    s(p.SCREENSAVER_WP_TEMP_UNIT, "C"); s(p.SCREENSAVER_WP_DAY2_NAME, "")
                    s(p.SCREENSAVER_WP_DAY2_HIGH, ""); s(p.SCREENSAVER_WP_DAY2_LOW, "")
                    s(p.SCREENSAVER_WP_DAY3_NAME, ""); s(p.SCREENSAVER_WP_DAY3_HIGH, "")
                    s(p.SCREENSAVER_WP_DAY3_LOW, ""); s(p.SCREENSAVER_WP_EVENT_TITLE, "")
                    s(p.SCREENSAVER_WP_EVENT_LOCATION, ""); s(p.SCREENSAVER_WP_EVENT_TIME, "")
                    s(p.SCREENSAVER_WP_EVENT2_TITLE, ""); s(p.SCREENSAVER_WP_EVENT2_TIME, "")
                    s(p.EQ_BAND_LEVELS, ""); s(p.DOUBLE_TAP_BACK_ACTION, "torch")
                    s(p.DOUBLE_TAP_BACK_APP, ""); s(p.DOUBLE_TAP_BACK_APP_NAME, "")
                    s(p.SLIDER_STYLE, "default"); s(p.CHARGING_ANIMATION_STYLE, "lightning")
                    s(p.CHARGING_ANIMATION_COLOR, "#FFEB3B"); s(p.CHARGING_SOUND_URI, "")
                    s(p.MUSIC_LEVELER_COLOR, "#FFFFFF"); s(p.MUSIC_LEVELER_POSITION, "bottom")
                    s(p.CALL_VIBRATION_PATTERN, "default"); s(p.ALARM_VIBRATION_PATTERN, "default")
                    s(p.NOTIF_VIBRATION_PATTERN, "default"); s(p.BG_WALLPAPER_URI, "")
                    s(p.EDGE_LIGHT_COLOR, "#8BCAFF"); s(p.EDGE_LIGHT_STYLE, "solid")
                    s(p.SECURITY_MOTION_ALARM_URI, ""); s(p.APP_UPDATER_SAVED_APPS, "[]")
                    s(p.POWER_MENU_STYLE, "container"); s(p.POWER_MENU_POSITION, "center")
                    s(p.POWER_MENU_APP_SHORTCUTS, "[]"); s(p.POWER_MENU_PEOPLE_JSON, "[]")
                    s(p.SCREEN_OFF_POWER_LONG, "flashlight"); s(p.SCREEN_OFF_VOL_UP_LONG, "")
                    s(p.SCREEN_OFF_VOL_DOWN_LONG, ""); s(p.SCREEN_OFF_POWER_LONG_APP, "")
                    s(p.SCREEN_OFF_VOL_UP_LONG_APP, ""); s(p.SCREEN_OFF_VOL_DOWN_LONG_APP, "")
                    s(p.DOUBLE_POWER_ACTION, "flashlight"); s(p.DOUBLE_POWER_APP, "")
                    s(p.FAKE_POWER_OFF_DISMISS_SEQUENCE, "POWER"); s(p.FAKE_CALL_NAME, "")
                    s(p.FAKE_CALL_NUMBER, ""); s(p.CHARGE_RINGTONE_URI, "")
                    s(p.HIDDEN_FEATURE_ROUTES, ""); s(p.APP_UPDATER_LAST_CHECK, "")
                    context.contentResolver.openOutputStream(it)?.use { out ->
                        out.write(json.toString(2).toByteArray())
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "✓ Full backup saved!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Backup failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Restore — read JSON and re-apply ALL settings
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: return@launch
                    val json = org.json.JSONObject(text)
                    if (json.optString("app") != "EverlastingTweak")
                        throw Exception("Not a valid Everlasting Tweak backup file")
                    val p = AppPreferences
                    // Helper: restore only if key exists in the JSON
                    suspend fun rb(k: androidx.datastore.preferences.core.Preferences.Key<Boolean>, d: Boolean) {
                        if (json.has(k.name)) p.set(k, json.getBoolean(k.name))
                    }
                    suspend fun ri(k: androidx.datastore.preferences.core.Preferences.Key<Int>, d: Int) {
                        if (json.has(k.name)) p.set(k, json.getInt(k.name))
                    }
                    suspend fun rf(k: androidx.datastore.preferences.core.Preferences.Key<Float>, d: Float) {
                        if (json.has(k.name)) p.set(k, json.getDouble(k.name).toFloat())
                    }
                    suspend fun rs(k: androidx.datastore.preferences.core.Preferences.Key<String>, d: String) {
                        if (json.has(k.name)) p.set(k, json.getString(k.name))
                    }
                    // Booleans
                    rb(p.SHAKE_TORCH_ENABLED,false);rb(p.SHAKE_PROXIMITY_ENABLED,false)
                    rb(p.TWIST_CAMERA_ENABLED,false);rb(p.TWIST_PROXIMITY_ENABLED,false)
                    rb(p.CUSTOM_HAPTICS_ENABLED,false);rb(p.HAPTICS_SCROLL_ENABLED,false)
                    rb(p.TAP_SOUND_ENABLED,false);rb(p.LOCK_SOUND_ENABLED,false)
                    rb(p.UNLOCK_SOUND_ENABLED,false);rb(p.MUSIC_LIGHT_ENABLED,false)
                    rb(p.MUSIC_VIBRATE_ENABLED,false);rb(p.WATERMARK_ENABLED,false)
                    rb(p.WATERMARK_BOLD,false);rb(p.WATERMARK_SHADOW,false)
                    rb(p.AUTO_REBOOT_ENABLED,false);rb(p.DYNAMIC_COLOR,true)
                    rb(p.SCREENSHOT_BLOCK_ENABLED,false);rb(p.NAVBAR_OVERLAY_ENABLED,false)
                    rb(p.EQUALIZER_ENABLED,false);rb(p.KEEP_SCREEN_ON_ENABLED,false)
                    rb(p.DOUBLE_TAP_BACK_ENABLED,false);rb(p.CHARGING_SOUND_ENABLED,false)
                    rb(p.CHARGING_ANIMATION_ENABLED,false);rb(p.CHARGING_ANIMATION_SHOW_PCT,false)
                    rb(p.FLIP_DND_ENABLED,false);rb(p.VOLUME_BOOST_ENABLED,false)
                    rb(p.MUSIC_LEVELER_ENABLED,false);rb(p.MUSIC_LEVELER_AUTO_HIDE,false)
                    rb(p.SECURITY_MOTION_ENABLED,false);rb(p.SECURITY_MOTION_SOUND_ENABLED,false)
                    rb(p.WALKIE_TALKIE_ENABLED,false);rb(p.COMPASS_CALIBRATED,false)
                    rb(p.CHARGE_LIMIT_ENABLED,false);rb(p.APP_FREEZER_ENABLED,false)
                    rb(p.POWER_SAVING_MAPS_ENABLED,false);rb(p.LOCK_WIDGETS_ENABLED,false)
                    rb(p.UI_BLUR_ENABLED,false);rb(p.EDGE_LIGHT_ENABLED,false)
                    rb(p.FLASH_NOTIF_ENABLED,false);rb(p.AUTO_UPDATE_ENABLED,false)
                    rb(p.BG_BLUR_ENABLED,false);rb(p.USE_DEVICE_WALLPAPER,false)
                    rb(p.USE_EMOJI_ICONS,false);rb(p.ICON_STYLE_SOLID,false)
                    rb(p.FREEZE_TAB_PILL_SOLID,true);rb(p.TOGGLE_ANIMATION_ENABLED,true)
                    rb(p.MAGNETIC_FIELD_IN_HOME,false);rb(p.POWER_MENU_ENABLED,false)
                    rb(p.POWER_MENU_SHOW_PEOPLE,false);rb(p.SCREEN_OFF_ACTIONS_ENABLED,false)
                    rb(p.DOUBLE_POWER_ENABLED,false);rb(p.FAKE_POWER_OFF_ENABLED,false)
                    rb(p.FAKE_POWER_OFF_LOCK_DEVICE,false);rb(p.FAKE_POWER_OFF_DND,false)
                    rb(p.NAVBAR_USE_ACCESSIBILITY,false);rb(p.CALL_VIBRATION_ENABLED,false)
                    rb(p.ALARM_VIBRATION_ENABLED,false);rb(p.NOTIF_VIBRATION_ENABLED,false)
                    rb(p.VOLUME_STYLES_ENABLED,false);rb(p.CHARGE_REPEAT_ENABLED,false)
                    rb(p.SCREENSAVER_MOVE_ENABLED,false);rb(p.SCREENSAVER_SHOW_BATTERY,false)
                    rb(p.SCREENSAVER_SHOW_DATE,false);rb(p.SCREENSAVER_BURN_IN_ENABLED,false)
                    rb(p.SCREENSAVER_MOTO_SHOW_BRANDING,true);rb(p.SCREENSAVER_MOTO_SHOW_ARC,true)
                    rb(p.SCREENSAVER_MOTO_SHOW_BATTERY,true);rb(p.SCREENSAVER_MOTO_USE_REAL_PCT,true)
                    rb(p.SCREENSAVER_MOTO_ARC_PROGRESS,true);rb(p.SCREENSAVER_MOTO_SECONDARY_GLOW,false)
                    rb(p.SCREENSAVER_MOTO_BG_VIGNETTE,false);rb(p.SCREENSAVER_MOTO_SHOW_CHARGING_TEXT,true)
                    rb(p.SCREENSAVER_WP_IS_24H,false);rb(p.SCREENSAVER_WP_SHOW_DATE,true)
                    rb(p.SCREENSAVER_WP_SHOW_WEATHER,false);rb(p.SCREENSAVER_WP_SHOW_EVENTS,false)
                    rb(p.SCREENSAVER_WP_SHOW_NOTIF,false);rb(p.SCREENSAVER_WP_SHOW_ALARM_ICON,false)
                    rb(p.SCREENSAVER_WP_SHOW_SECONDS,false);rb(p.SCREENSAVER_WP_SHOW_SEPARATOR,false)
                    rb(p.SCREENSAVER_WP_COMPACT_MODE,false);rb(p.SCREENSAVER_WP_SHOW_WEEK_NUMBER,false)
                    rb(p.SCREENSAVER_WP_SHOW_BATTERY,false)
                    // Ints
                    ri(p.DARK_THEME,0);ri(p.HAPTICS_INTENSITY,50);ri(p.HAPTICS_TAP_INTENSITY,50)
                    ri(p.HAPTICS_SCROLL_INTENSITY,30);ri(p.KEEP_SCREEN_ON_TIMEOUT,0)
                    ri(p.CHARGE_LIMIT_PERCENT,80);ri(p.VOLUME_BOOST_LEVEL,20)
                    ri(p.CHARGING_ANIMATION_DURATION,30);ri(p.HOME_PILL_STYLE,0)
                    ri(p.TASK_UPDATE_INTERVAL,3);ri(p.TERMINAL_THEME_INDEX,0)
                    ri(p.AUTO_UPDATE_INTERVAL_HOURS,24);ri(p.SCREENSAVER_FADE_DURATION,1000)
                    ri(p.SCREENSAVER_MOVE_SPEED,3);ri(p.SCREENSAVER_MOVE_INTERVAL_S,30)
                    ri(p.SCREENSAVER_BURN_IN_INTERVAL,60);ri(p.SCREENSAVER_MOTO_CUSTOM_PCT,75)
                    ri(p.SCREENSAVER_MOTO_GLOW_LAYERS,3);ri(p.SCREENSAVER_WP_PHONE_COUNT,0)
                    ri(p.SCREENSAVER_WP_EMAIL_COUNT,0);ri(p.NOTIF_FLASH_COUNT,3)
                    ri(p.NOTIF_FLASH_SPEED_MS,300);ri(p.EDGE_LIGHT_DURATION_MS,3000)
                    ri(p.FAKE_CALL_DELAY,5)
                    // Floats
                    rf(p.SHAKE_SENSITIVITY,12f);rf(p.TWIST_SENSITIVITY,3f)
                    rf(p.MUSIC_LIGHT_SENSITIVITY,0.5f);rf(p.MUSIC_BLINK_SPEED,0.5f)
                    rf(p.WATERMARK_FONT_SIZE,14f);rf(p.WATERMARK_OPACITY,0.7f)
                    rf(p.NAVBAR_HEIGHT,48f);rf(p.NAVBAR_PILL_OPACITY,0.8f)
                    rf(p.NAVBAR_X_POSITION,0.5f);rf(p.NAVBAR_Y_POSITION,0.9f)
                    rf(p.VOLUME_CORNER_RADIUS,12f);rf(p.VOLUME_OPACITY,0.9f)
                    rf(p.SCREENSAVER_SIZE,1f);rf(p.SCREENSAVER_MOTO_GLOW_SIZE,0.45f)
                    rf(p.SCREENSAVER_MOTO_PULSE_SPEED,1f);rf(p.SCREENSAVER_MOTO_FONT_SIZE,120f)
                    rf(p.SCREENSAVER_MOTO_GLOW_INTENSITY,0.7f)
                    rf(p.SCREENSAVER_MOTO_GLOW_OFFSET_X,0.5f);rf(p.SCREENSAVER_MOTO_GLOW_OFFSET_Y,0.4f)
                    rf(p.SCREENSAVER_MOTO_NUM_OFFSET_X,0.5f);rf(p.SCREENSAVER_MOTO_NUM_OFFSET_Y,0.35f)
                    rf(p.SCREENSAVER_MOTO_ARC_OFFSET_X,0.5f);rf(p.SCREENSAVER_MOTO_ARC_OFFSET_Y_ADJ,0f)
                    rf(p.SCREENSAVER_MOTO_BRANDING_OFFSET_X,0.5f);rf(p.SCREENSAVER_MOTO_BRANDING_OFFSET_Y,0.88f)
                    rf(p.SCREENSAVER_MOTO_ARC_RADIUS_MULT,0.38f);rf(p.SCREENSAVER_MOTO_ARC_GAP_MULT,0.08f)
                    rf(p.SCREENSAVER_MOTO_ARC_STROKE_WIDTH,6f);rf(p.SCREENSAVER_MOTO_ARC_ANGLE_START,210f)
                    rf(p.SCREENSAVER_MOTO_ARC_ANGLE_SWEEP,120f);rf(p.SCREENSAVER_MOTO_BOLT_SIZE_MULT,0.35f)
                    rf(p.SCREENSAVER_MOTO_BOLT_OFFSET_Y,0.03f);rf(p.SCREENSAVER_MOTO_NUM_COLOR_OPACITY,1f)
                    rf(p.SCREENSAVER_MOTO_NUM_LETTER_SPC,0f);rf(p.SCREENSAVER_MOTO_SUFFIX_SIZE_MULT,0.35f)
                    rf(p.SCREENSAVER_WP_FONT_SIZE,1f);rf(p.SCREENSAVER_WP_LETTER_SPACING,0f)
                    rf(p.SCREENSAVER_WP_DATE_OPACITY,0.7f);rf(p.SCREENSAVER_WP_CLOCK_VERTICAL_POS,0.5f)
                    rf(p.SCREENSAVER_WP_CLOCK_SIZE,1f);rf(p.SCREENSAVER_WP_CLOCK_SIZE_SP,64f)
                    rf(p.SCREENSAVER_WP_DATE_SIZE_SP,18f);rf(p.SCREENSAVER_WP_WEATHER_SIZE_SP,16f)
                    rf(p.SCREENSAVER_WP_NOTIF_SIZE_SP,14f);rf(p.SCREENSAVER_WP_PADDING_LEFT,32f)
                    rf(p.SECURITY_MOTION_SENSITIVITY,12f);rf(p.MUSIC_LEVELER_HEIGHT,4f)
                    rf(p.MUSIC_LEVELER_OPACITY,0.8f);rf(p.BG_DIM_AMOUNT,0f)
                    rf(p.BG_BLUR_AMOUNT,16f);rf(p.UI_BLUR_AMOUNT,16f)
                    rf(p.HOME_PILL_BLUR_INTENSITY,16f);rf(p.EDGE_LIGHT_THICKNESS,4f)
                    rf(p.EDGE_LIGHT_ALPHA,0.9f)
                    // Strings
                    rs(p.HAPTICS_PATTERN,"default");rs(p.TAP_SOUND_URI,"")
                    rs(p.LOCK_SOUND_URI,"");rs(p.UNLOCK_SOUND_URI,"")
                    rs(p.WATERMARK_TEXT,"");rs(p.WATERMARK_POSITION,"BottomRight")
                    rs(p.WATERMARK_COLOR,"#FFFFFF");rs(p.AUTO_REBOOT_TIME,"03:00")
                    rs(p.AUTO_REBOOT_DAYS,"");rs(p.CUSTOM_PRIMARY_COLOR,"")
                    rs(p.CUSTOM_SECONDARY_COLOR,"");rs(p.NAVBAR_STYLE,"pill")
                    rs(p.NAVBAR_PILL_COLOR,"#FFFFFF");rs(p.VOLUME_STYLE,"default")
                    rs(p.VOLUME_COLOR,"#FFFFFF");rs(p.SCREENSAVER_THEME,"clock")
                    rs(p.SCREENSAVER_COLOR,"#FFFFFF");rs(p.SCREENSAVER_CLOCK_STYLE,"Digital")
                    rs(p.SCREENSAVER_CLOCK_COLOR,"#FFFFFF")
                    rs(p.SCREENSAVER_MOTO_GLOW_COLOR,"#4FC3F7");rs(p.SCREENSAVER_MOTO_TEXT_COLOR,"#FFFFFF")
                    rs(p.SCREENSAVER_MOTO_ARC_COLOR,"#4FC3F7");rs(p.SCREENSAVER_MOTO_BRANDING_TEXT,"moto")
                    rs(p.SCREENSAVER_MOTO_BG_COLOR,"#000000");rs(p.SCREENSAVER_MOTO_SECONDARY_COLOR,"#FFFFFF")
                    rs(p.SCREENSAVER_MOTO_GLOW_SHAPE,"Circle");rs(p.SCREENSAVER_MOTO_BOLT_STYLE,"Filled")
                    rs(p.SCREENSAVER_MOTO_PCT_SUFFIX_STYLE,"%");rs(p.SCREENSAVER_MOTO_CHARGING_TEXT,"Charging")
                    rs(p.SCREENSAVER_MOTO_ANIMATION_STYLE,"Pulse");rs(p.SCREENSAVER_MOTO_NUM_FONT_WEIGHT,"Thin")
                    rs(p.SCREENSAVER_WP_TEXT_COLOR,"#FFFFFF");rs(p.SCREENSAVER_WP_LAYOUT,"Left")
                    rs(p.SCREENSAVER_WP_CITY,"");rs(p.SCREENSAVER_WP_CONDITION,"")
                    rs(p.SCREENSAVER_WP_TEMPERATURE,"");rs(p.SCREENSAVER_WP_TEMP_HIGH,"")
                    rs(p.SCREENSAVER_WP_TEMP_LOW,"");rs(p.SCREENSAVER_WP_BG_COLOR,"#000000")
                    rs(p.SCREENSAVER_WP_CLOCK_WEIGHT,"Thin");rs(p.SCREENSAVER_WP_TIME_COLOR,"#FFFFFF")
                    rs(p.SCREENSAVER_WP_CLOCK_POSITION,"Left");rs(p.SCREENSAVER_WP_ACCENT_COLOR,"#4FC3F7")
                    rs(p.SCREENSAVER_WP_NOTIF_STYLE,"Icons");rs(p.SCREENSAVER_WP_TEMP_UNIT,"C")
                    rs(p.SCREENSAVER_WP_DAY2_NAME,"");rs(p.SCREENSAVER_WP_DAY2_HIGH,"")
                    rs(p.SCREENSAVER_WP_DAY2_LOW,"");rs(p.SCREENSAVER_WP_DAY3_NAME,"")
                    rs(p.SCREENSAVER_WP_DAY3_HIGH,"");rs(p.SCREENSAVER_WP_DAY3_LOW,"")
                    rs(p.SCREENSAVER_WP_EVENT_TITLE,"");rs(p.SCREENSAVER_WP_EVENT_LOCATION,"")
                    rs(p.SCREENSAVER_WP_EVENT_TIME,"");rs(p.SCREENSAVER_WP_EVENT2_TITLE,"")
                    rs(p.SCREENSAVER_WP_EVENT2_TIME,"")
                    rs(p.EQ_BAND_LEVELS,"");rs(p.DOUBLE_TAP_BACK_ACTION,"torch")
                    rs(p.DOUBLE_TAP_BACK_APP,"");rs(p.DOUBLE_TAP_BACK_APP_NAME,"")
                    rs(p.SLIDER_STYLE,"default");rs(p.CHARGING_ANIMATION_STYLE,"lightning")
                    rs(p.CHARGING_ANIMATION_COLOR,"#FFEB3B");rs(p.CHARGING_SOUND_URI,"")
                    rs(p.MUSIC_LEVELER_COLOR,"#FFFFFF");rs(p.MUSIC_LEVELER_POSITION,"bottom")
                    rs(p.CALL_VIBRATION_PATTERN,"default");rs(p.ALARM_VIBRATION_PATTERN,"default")
                    rs(p.NOTIF_VIBRATION_PATTERN,"default");rs(p.BG_WALLPAPER_URI,"")
                    rs(p.EDGE_LIGHT_COLOR,"#8BCAFF");rs(p.EDGE_LIGHT_STYLE,"solid")
                    rs(p.SECURITY_MOTION_ALARM_URI,"");rs(p.APP_UPDATER_SAVED_APPS,"[]")
                    rs(p.POWER_MENU_STYLE,"container");rs(p.POWER_MENU_POSITION,"center")
                    rs(p.POWER_MENU_APP_SHORTCUTS,"[]");rs(p.POWER_MENU_PEOPLE_JSON,"[]")
                    rs(p.SCREEN_OFF_POWER_LONG,"flashlight");rs(p.SCREEN_OFF_VOL_UP_LONG,"")
                    rs(p.SCREEN_OFF_VOL_DOWN_LONG,"");rs(p.SCREEN_OFF_POWER_LONG_APP,"")
                    rs(p.SCREEN_OFF_VOL_UP_LONG_APP,"");rs(p.SCREEN_OFF_VOL_DOWN_LONG_APP,"")
                    rs(p.DOUBLE_POWER_ACTION,"flashlight");rs(p.DOUBLE_POWER_APP,"")
                    rs(p.FAKE_POWER_OFF_DISMISS_SEQUENCE,"POWER");rs(p.FAKE_CALL_NAME,"")
                    rs(p.FAKE_CALL_NUMBER,"");rs(p.CHARGE_RINGTONE_URI,"")
                    rs(p.HIDDEN_FEATURE_ROUTES,"");rs(p.APP_UPDATER_LAST_CHECK,"")
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "✓ All settings restored!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Restore failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    Scaffold { innerPadding ->
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(top = innerPadding.calculateTopPadding())) {

        // ── HERO HEADER ──────────────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(50.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Text(":)", fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                        Text("Everlasting Tweak", fontWeight = FontWeight.Black, fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Android Enhancement Suite", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("v${com.coolappstore.everlastingandroidtweak.BuildConfig.VERSION_NAME} · Made By Hari ❤️", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/hariprabhu1008")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            })
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$grantedCount / 9 permissions", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (grantedCount == 9) "All granted ✓" else "$grantedCount / 9 granted", fontSize = 12.sp,
                        color = if (grantedCount == 9) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { grantedCount / 9f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            }
        }

        // ── CHECK FOR UPDATES ─────────────────────────────────────────────────
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = updateState !in listOf("downloading", "installing")) {
                when (updateState) {
                    "ready" -> showInstallDialog = true
                    else -> {
                        updateState = "checking"
                        scope.launch {
                            try {
                                val result = com.coolappstore.everlastingandroidtweak.features.appupdater.AppUpdaterHelper.checkSelfUpdate(context)
                                if (result?.hasUpdate == true) {
                                    val fileName = "EverlastingAndroidTweak-v${result.latestVersion}.apk"
                                    val destFile = java.io.File(
                                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                                        fileName
                                    )
                                    if (destFile.exists()) destFile.delete()

                                    val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                    val request = android.app.DownloadManager.Request(Uri.parse(result.downloadUrl))
                                        .setTitle("Everlasting Android Tweak")
                                        .setDescription("Downloading v${result.latestVersion}…")
                                        .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                        .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                                        .setMimeType("application/vnd.android.package-archive")
                                        .setAllowedOverMetered(true)
                                        .setAllowedOverRoaming(true)
                                    val downloadId = dm.enqueue(request)
                                    updateState    = "downloading"
                                    updateProgress = 0

                                    // Persist so we can resume after process restart
                                    updatePrefs.edit()
                                        .putString("pending_apk_path", destFile.absolutePath)
                                        .putString("pending_apk_version", result.latestVersion)
                                        .apply()

                                    // Poll progress until download finishes
                                    var done = false
                                    while (!done) {
                                        kotlinx.coroutines.delay(500L)
                                        val query  = android.app.DownloadManager.Query().setFilterById(downloadId)
                                        val cursor = dm.query(query)
                                        if (cursor.moveToFirst()) {
                                            val status     = cursor.getInt(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
                                            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                            val total      = cursor.getLong(cursor.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                                            if (total > 0) updateProgress = (downloaded * 100L / total).toInt()
                                            when (status) {
                                                android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                                                    pendingApkFile    = destFile
                                                    pendingApkVersion = result.latestVersion
                                                    updateState       = "ready"
                                                    showInstallDialog = true   // auto-popup
                                                    done = true
                                                }
                                                android.app.DownloadManager.STATUS_FAILED -> {
                                                    updatePrefs.edit().clear().apply()
                                                    android.widget.Toast.makeText(context, "Download failed", android.widget.Toast.LENGTH_SHORT).show()
                                                    updateState = "idle"
                                                    done = true
                                                }
                                            }
                                        } else {
                                            // Removed externally
                                            updatePrefs.edit().clear().apply()
                                            updateState = "idle"
                                            done = true
                                        }
                                        cursor.close()
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "You're up to date ✓", android.widget.Toast.LENGTH_SHORT).show()
                                    updateState = "idle"
                                }
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "Check failed", android.widget.Toast.LENGTH_SHORT).show()
                                updateState = "idle"
                            }
                        }
                    }
                }
            }, shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(
                        if (iconStyleSolid) Color(0xFF4CAF50).copy(alpha = if (settingsDark) 0.9f else 0.85f)
                        else Color(0xFF4CAF50).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center) {
                    when (updateState) {
                        "checking"    -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        "downloading" -> CircularProgressIndicator(
                            progress = { updateProgress / 100f },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        else -> Icon(Icons.Default.SystemUpdateAlt, null,
                            tint = if (iconStyleSolid) Color.White else Color(0xFF4CAF50),
                            modifier = Modifier.size(22.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Check for Updates", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    val subtitle = when (updateState) {
                        "checking"    -> "Checking for updates…"
                        "downloading" -> "Downloading APK… $updateProgress%"
                        "ready"       -> "APK ready — tap to install v$pendingApkVersion"
                        "installing"  -> "Waiting for install to complete…"
                        else          -> "Tap to check for latest version"
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = if (updateState == "ready") Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (updateState) {
                    "ready" -> Button(
                        onClick = { showInstallDialog = true },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("Install", style = MaterialTheme.typography.labelMedium) }
                    else -> Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (updateState == "downloading") {
                LinearProgressIndicator(
                    progress = { updateProgress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    color = Color(0xFF4CAF50)
                )
            }
        }

        // ── RATE & REVIEW ─────────────────────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                // Row 1 — Rate & Review
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate(
                                com.coolappstore.everlastingandroidtweak.ui.navigation.Screen.RatingReview.route
                            )
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon bubble — uses primary theme color to stay in sync with appearance settings
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (iconStyleSolid)
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (settingsDark) 0.9f else 0.85f)
                                else
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RateReview,
                            contentDescription = null,
                            tint = if (iconStyleSolid) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Text column
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Rate & Review",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Tap to rate Everlasting Tweak",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Trailing icon
                    Icon(Icons.Default.ChevronRight, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Row 2 — Check Rate and Reviews (opens in-app WebView)
                val reviewsUrl = REVIEWS_URL
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController.navigate(
                                com.coolappstore.everlastingandroidtweak.ui.navigation.Screen.ReviewsWebView.route
                            )
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (iconStyleSolid)
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (settingsDark) 0.9f else 0.85f)
                                else
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = if (iconStyleSolid) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            "Check Rate and Reviews",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "See what others are saying",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Small icon to open in phone browser
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(reviewsUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = "Open in browser",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        // ─────────────────────────────────────────────────────────────────────

        SettingsSectionLabel("Appearance")
        SettingsCard {
            // Theme chips - Column layout avoids LazyRow height bug
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                        .background(
                            if (iconStyleSolid) Color(0xFF9C27B0).copy(alpha = if (settingsDark) 0.9f else 0.85f)
                            else Color(0xFF9C27B0).copy(alpha = 0.12f)
                        ),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Palette, null,
                            tint = if (iconStyleSolid) Color.White else Color(0xFF9C27B0),
                            modifier = Modifier.size(22.dp))
                    }
                    Text("Theme Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
                // Row 1: Auto / Light / Dark
                val opts1 = listOf(0 to "Auto", 1 to "Light", 2 to "Dark")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    opts1.forEach { (i, label) ->
                        FilterChip(
                            selected = darkTheme == i,
                            onClick = {
                                scope.launch {
                                    AppPreferences.set(AppPreferences.DARK_THEME, i)
                                    val hailTheme = when (i) {
                                        1    -> HailData.THEME_LIGHT
                                        2    -> HailData.THEME_DARK
                                        else -> HailData.FOLLOW_SYSTEM
                                    }
                                    PreferenceManager.getDefaultSharedPreferences(context)
                                        .edit().putString(HailData.APP_THEME, hailTheme).apply()
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Row 2: Black / White / Auto B&W
                val opts2 = listOf(3 to "Black", 4 to "White", 5 to "Auto B&W")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    opts2.forEach { (i, label) ->
                        FilterChip(
                            selected = darkTheme == i,
                            onClick = {
                                scope.launch {
                                    AppPreferences.set(AppPreferences.DARK_THEME, i)
                                    val hailTheme = when (i) {
                                        3    -> HailData.THEME_DARK
                                        4    -> HailData.THEME_LIGHT
                                        else -> HailData.FOLLOW_SYSTEM
                                    }
                                    PreferenceManager.getDefaultSharedPreferences(context)
                                        .edit().putString(HailData.APP_THEME, hailTheme).apply()
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            HDivider()
            // Dynamic Color
            SettingsToggleRow(Icons.Default.AutoAwesome, Color(0xFF2196F3), "Dynamic Color",
                "Wallpaper-based Material You colors", dynamicColor) {
                scope.launch { AppPreferences.set(AppPreferences.DYNAMIC_COLOR, it) }
            }
            // Custom colors when dynamic is OFF
            if (!dynamicColor) {
                HDivider()
                // Color picker dialog state
                var showColorPicker by remember { mutableStateOf(false) }
                if (showColorPicker) {
                    EverlastingColorPickerDialog(
                        initialHex = customPrimary.ifEmpty { "#2196F3" },
                        onDismiss = { showColorPicker = false },
                        onColorSelected = { hex ->
                            scope.launch { AppPreferences.set(AppPreferences.CUSTOM_PRIMARY_COLOR, hex) }
                        }
                    )
                }
                Column(Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(CircleShape)
                                .background(
                                    if (customPrimary.isNotEmpty())
                                        try { Color(android.graphics.Color.parseColor(customPrimary)) }
                                        catch (_: Exception) { Color(0xFFE91E63) }
                                    else Color(0xFFE91E63)
                                )
                        )
                        Text("Custom Primary Color",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        // Open full picker button
                        FilledTonalButton(
                            onClick = { showColorPicker = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Colorize, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pick", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Quick preset swatches
                    val presets = listOf("#006397","#E91E63","#9C27B0","#FF5722","#4CAF50","#FF9800","#00BCD4","#3F51B5","#F44336","#009688")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.take(5).forEach { hex ->
                            val col = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                            Box(Modifier.size(36.dp).clip(CircleShape).background(col)
                                .then(if (customPrimary == hex) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { scope.launch { AppPreferences.set(AppPreferences.CUSTOM_PRIMARY_COLOR, hex) } })
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.drop(5).forEach { hex ->
                            val col = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Gray }
                            Box(Modifier.size(36.dp).clip(CircleShape).background(col)
                                .then(if (customPrimary == hex) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { scope.launch { AppPreferences.set(AppPreferences.CUSTOM_PRIMARY_COLOR, hex) } })
                        }
                    }
                    if (customPrimary.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { scope.launch { AppPreferences.set(AppPreferences.CUSTOM_PRIMARY_COLOR, "") } }) {
                            Text("Reset to default")
                        }
                    }
                }
            }
            HDivider()
            // Toggle animation
            SettingsToggleRow(Icons.Default.Animation, Color(0xFF00ACC1), "Toggle Switch Animations",
                "Animated transitions when turning features on or off", toggleAnimEnabled) {
                scope.launch { AppPreferences.set(AppPreferences.TOGGLE_ANIMATION_ENABLED, it) }
            }
            // ROOT CAUSE FIX: Solid Icon Backgrounds was placed OUTSIDE the SettingsCard closing
            // brace so it rendered floating outside the Appearance card container. Moved inside.
            HDivider()
            SettingsToggleRow(Icons.Default.FormatColorFill, Color(0xFF9C27B0), "Solid Icon Backgrounds",
                "Fully opaque colored bubbles. Off = translucent/subtle like Settings icons", iconStyleSolid) {
                scope.launch { AppPreferences.set(AppPreferences.ICON_STYLE_SOLID, it) }
            }
        }

        // ── HOME SCREEN FEATURES ──────────────────────────────────────────────
        SettingsSectionLabel("Home Screen Features")
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                .clickable { navController.navigate(com.coolappstore.everlastingandroidtweak.ui.navigation.Screen.VisibleFeatures.route) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                        .background(
                            if (iconStyleSolid) MaterialTheme.colorScheme.primary.copy(alpha = if (settingsDark) 0.9f else 0.85f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GridView, null,
                        tint = if (iconStyleSolid) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Visible Features", style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold)
                    val hiddenCountStr by AppPreferences.get(AppPreferences.HIDDEN_FEATURE_ROUTES, "").collectAsState("")
                    val hiddenCount = remember(hiddenCountStr) {
                        if (hiddenCountStr.isBlank()) 0
                        else hiddenCountStr.split(",").count { it.isNotBlank() }
                    }
                    Text(
                        if (hiddenCount == 0) "All features visible on home screen"
                        else "$hiddenCount feature${if (hiddenCount == 1) "" else "s"} hidden · tap to manage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }


        // ── BACKGROUND ───────────────────────────────────────────────────────
        SettingsSectionLabel("Background")
        SettingsCard {
            // Device wallpaper option
            SettingsToggleRow(Icons.Default.Wallpaper, Color(0xFF673AB7), "Use Device Wallpaper",
                "Use your current home screen wallpaper as app background", useDeviceWp) { enable ->
                if (enable && !grantedMedia) {
                    permLauncher.launch(
                        if (android.os.Build.VERSION.SDK_INT >= 33)
                            arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
                        else
                            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    )
                    android.widget.Toast.makeText(context, "Grant Media Access permission to use device wallpaper", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    scope.launch {
                        AppPreferences.set(AppPreferences.USE_DEVICE_WALLPAPER, enable)
                        if (enable) AppPreferences.set(AppPreferences.BG_WALLPAPER_URI, "")
                    }
                }
            }
            HDivider()
            // Custom image
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(if (iconStyleSolid) Color(0xFF9C27B0).copy(alpha = if (settingsDark) 0.9f else 0.85f) else Color(0xFF9C27B0).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Image, null, tint = if (iconStyleSolid) Color.White else Color(0xFF9C27B0), modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Custom Background Image", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(if (bgUri.isEmpty()) "No image selected" else android.net.Uri.parse(bgUri).lastPathSegment?.take(30) ?: "Image set",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bgUri.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (bgUri.isNotEmpty()) {
                        OutlinedButton(onClick = {
                            scope.launch {
                                // BUG FIX: also delete the cached file so Coil can't serve the old image
                                try {
                                    context.filesDir.listFiles { f -> f.name.startsWith("bg_wallpaper_") && f.name.endsWith(".jpg") }
                                        ?.forEach { it.delete() }
                                } catch (_: Exception) {}
                                AppPreferences.set(AppPreferences.BG_WALLPAPER_URI, "")
                            }
                        },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text("Clear") }
                    }
                    FilledTonalButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                            bgPickerModern.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        else
                            bgPickerLegacy.launch("image/*")
                    }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) { Text("Pick") }
                }
            }
            if (bgUri.isNotEmpty()) {
                HDivider()
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Dim slider
                    Text("Dim: ${(bgDimVal * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = bgDimVal, onValueChange = { bgDimVal = it
                        scope.launch { AppPreferences.set(AppPreferences.BG_DIM_AMOUNT, it) }
                    }, valueRange = 0f..0.9f, modifier = Modifier.fillMaxWidth())
                    // Blur toggle + slider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Blur Background", style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = bgBlur, onCheckedChange = { scope.launch { AppPreferences.set(AppPreferences.BG_BLUR_ENABLED, it) } })
                    }
                    if (bgBlur) {
                        Text("Blur Amount: ${bgBlurVal.toInt()}dp", style = MaterialTheme.typography.bodySmall)
                        Slider(value = bgBlurVal, onValueChange = { bgBlurVal = it
                            scope.launch { AppPreferences.set(AppPreferences.BG_BLUR_AMOUNT, it) }
                        }, valueRange = 4f..40f, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // ── PERMISSIONS ───────────────────────────────────────────────────────
        SettingsSectionLabel("Permissions")
        // BUG FIX: permissions UI was cramped with no visual separation per item.
        // Rewrote as individual styled rows with consistent icon + status badge layout.
        SettingsCard {
            val perms = listOf(
                Triple("Camera",          grantedCamera,              Icons.Default.CameraAlt),
                Triple("Microphone",      grantedAudio,               Icons.Default.Mic),
                Triple("Notifications",   grantedNotifications,       Icons.Default.Notifications),
                Triple("Media Access",    grantedMedia,               Icons.Default.Photo),
                Triple("Accessibility",   grantedAccessibility,       Icons.Default.Accessibility),
                Triple("Draw Over Apps",  grantedOverlay,             Icons.Default.Layers),
                Triple("Notif Listener",  grantedNotifListener,       Icons.Default.NotificationsActive),
                Triple("Usage Stats",     grantedUsageStats,          Icons.Default.BarChart),
                Triple("Shizuku",         grantedShizuku.value,       Icons.Default.Terminal),
            )
            perms.forEachIndexed { idx, (name, granted, icon) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Icon bubble
                    Box(
                        Modifier.size(40.dp).clip(MaterialTheme.shapes.medium)
                            .background(
                                if (granted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null,
                            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp))
                    }
                    // Label
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (granted) "Granted" else "Not granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    // Status / action
                    if (granted) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Icon(Icons.Default.Check, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(6.dp).size(16.dp))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                when (name) {
                                    "Camera"         -> permLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                                    "Microphone"     -> permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    "Notifications"  -> if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                    "Media Access"   -> permLauncher.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                                    "Accessibility"  -> PermissionManager.openAccessibilitySettings(context)
                                    "Draw Over Apps" -> PermissionManager.openOverlaySettings(context)
                                    "Notif Listener" -> PermissionManager.openNotificationListenerSettings(context)
                                    "Usage Stats"    -> PermissionManager.openUsageAccessSettings(context)
                                    "Shizuku"        -> PermissionManager.requestShizukuPermission(context)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("Grant", style = MaterialTheme.typography.labelMedium) }
                    }
                }
                if (idx < perms.lastIndex) HDivider()
            }
        }

        // ── ABOUT ─────────────────────────────────────────────────────────────
        SettingsSectionLabel("About")
        SettingsCard {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center) { Text(":)", fontSize = 18.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f)) {
                    Text("Everlasting Tweak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Version ${com.coolappstore.everlastingandroidtweak.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Made By Hari ❤️", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/hariprabhu1008")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        })
                }
            }
            HDivider()
            // ── Support row (top of About) ────────────────────────────────────
            Column(
                Modifier.fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/EverlastingAndroidTweak")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                        .background(if (iconStyleSolid) Color(0xFF2CA5E0).copy(alpha = if (settingsDark) 0.9f else 0.85f) else Color(0xFF2CA5E0).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Campaign, null, tint = if (iconStyleSolid) Color.White else Color(0xFF2CA5E0), modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Announcements | Updates | Bug Fixes | Feature Requests | Rate | Support",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(3.dp))
                        Text("App Support Telegram Group",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp))
                }
            }
            HDivider()
            AboutRow(Icons.Default.Code, Color(0xFF6E40C9), "Source Code | Contributions",
                "github.com/hari161008") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hari161008/Everlasting-Android-Tweak")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            HDivider()
            // ── Apps channel row ──────────────────────────────────────────────
            Column(
                Modifier.fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/CoolAppStore")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                        .background(if (iconStyleSolid) Color(0xFF2CA5E0).copy(alpha = if (settingsDark) 0.9f else 0.85f) else Color(0xFF2CA5E0).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Campaign, null, tint = if (iconStyleSolid) Color.White else Color(0xFF2CA5E0), modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Apps | Discover | Community",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(3.dp))
                        Text("You can also support me by joining my main Telegram Channel @CoolAppStore ❤️",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp))
                }
            }
            HDivider()
            // ADB command
            var adbCopied by remember { mutableStateOf(false) }
            val adbCmd = "adb shell pm grant com.coolappstore.everlastingandroidtweak android.permission.WRITE_SECURE_SETTINGS"
            Row(Modifier.fillMaxWidth().clickable {
                val cb = context.getSystemService(android.content.ClipboardManager::class.java)
                cb?.setPrimaryClip(android.content.ClipData.newPlainText("ADB", adbCmd))
                adbCopied = true
            }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(if (iconStyleSolid) Color(0xFF4CAF50).copy(alpha = if (settingsDark) 0.9f else 0.85f) else Color(0xFF4CAF50).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Terminal, null, tint = if (iconStyleSolid) Color.White else Color(0xFF4CAF50), modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("ADB: Write Secure Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Tap to copy command", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (adbCopied) Icons.Default.CheckCircle else Icons.Default.ContentCopy, null,
                    tint = if (adbCopied) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }


        // ── AUTO UPDATE ───────────────────────────────────────────────────────
        SettingsSectionLabel("Auto Update")
        SettingsCard {
            SettingsToggleRow(Icons.Default.Update, Color(0xFF4CAF50), "Background Update Check",
                "Periodically check GitHub for new releases", autoUpdate) {
                scope.launch { AppPreferences.set(AppPreferences.AUTO_UPDATE_ENABLED, it) }
            }
            if (autoUpdate) {
                HDivider()
                Column(Modifier.padding(16.dp)) {
                    Text("Check Interval", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(6 to "6h", 12 to "12h", 24 to "Daily", 72 to "3d", 168 to "Weekly").forEach { (hrs, lbl) ->
                            FilterChip(selected = updateInterval == hrs,
                                onClick = { scope.launch { AppPreferences.set(AppPreferences.AUTO_UPDATE_INTERVAL_HOURS, hrs) } },
                                label = { Text(lbl, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── ADVANCED ─────────────────────────────────────────────────────────
        SettingsSectionLabel("Advanced")
        SettingsCard {
            // Backup
            Row(Modifier.fillMaxWidth().clickable { backupLauncher.launch("everlasting_backup_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.json") }
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(if (iconStyleSolid) Color(0xFF2196F3).copy(alpha = if (settingsDark) 0.9f else 0.85f) else Color(0xFF2196F3).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Backup, null, tint = if (iconStyleSolid) Color.White else Color(0xFF2196F3), modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("Backup Settings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Export all settings to a JSON file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HDivider()
            // Restore
            Row(Modifier.fillMaxWidth().clickable { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
                .padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(if (iconStyleSolid) Color(0xFF4CAF50).copy(alpha = if (settingsDark) 0.9f else 0.85f) else Color(0xFF4CAF50).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.Restore, null, tint = if (iconStyleSolid) Color.White else Color(0xFF4CAF50), modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("Restore Settings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Import settings from a backup JSON file", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HDivider()
            // Reset
            var showResetDialog by remember { mutableStateOf(false) }
            if (showResetDialog) {
                AlertDialog(onDismissRequest = { showResetDialog = false },
                    icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Reset All Settings?") },
                    text = { Text("This clears ALL preferences, returns everything to defaults, and restarts the app.") },
                    confirmButton = {
                        Button(onClick = {
                            showResetDialog = false
                            scope.launch {
                                // Clear the entire DataStore in one atomic edit
                                AppPreferences.clearAll()
                                // Brief delay so DataStore flush completes before kill
                                kotlinx.coroutines.delay(300)
                                withContext(Dispatchers.Main) {
                                    // Restart: launch MainActivity fresh, then kill this process
                                    val intent = context.packageManager
                                        .getLaunchIntentForPackage(context.packageName)
                                        ?.addFlags(
                                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        )
                                    if (intent != null) context.startActivity(intent)
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Reset & Restart")
                        }
                    },
                    dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
                )
            }
            Row(Modifier.fillMaxWidth().clickable { showResetDialog = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(40.dp).clip(MaterialTheme.shapes.medium).background(if (iconStyleSolid) MaterialTheme.colorScheme.error.copy(alpha = if (settingsDark) 0.9f else 0.85f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.RestartAlt, null, tint = if (iconStyleSolid) Color.White else MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("Reset All Settings", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text("Clear all preferences and return to defaults", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
    } // end Scaffold
}

// ── HELPERS ──────────────────────────────────────────────────────────────────
@Composable private fun SettingsSectionLabel(text: String) {
    Row(
        Modifier.padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) { Column(content = content) }
}

@Composable private fun HDivider() = HorizontalDivider(
    Modifier.padding(horizontal = 16.dp),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

@Composable private fun SettingsRow(
    icon: ImageVector, tint: Color, title: String, subtitle: String?,
    trailing: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
            .background(tint.copy(alpha = if (isDark) 0.22f else 0.13f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable private fun SettingsToggleRow(
    icon: ImageVector, tint: Color, title: String, subtitle: String,
    checked: Boolean, onToggle: (Boolean) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val solidIcons by AppPreferences.get(AppPreferences.ICON_STYLE_SOLID, false).collectAsState(false)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
            .background(
                if (solidIcons) tint.copy(alpha = if (isDark) 0.9f else 0.85f)
                else tint.copy(alpha = if (isDark) 0.22f else 0.13f)
            ),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (solidIcons) Color.White else tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        com.coolappstore.everlastingandroidtweak.ui.components.AnimatedSwitch(
            checked = checked, onCheckedChange = onToggle
        )
    }
}

@Composable private fun AboutRow(
    icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val solidIcons2 by AppPreferences.get(AppPreferences.ICON_STYLE_SOLID, false).collectAsState(false)
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
            .background(
                if (solidIcons2) tint.copy(alpha = if (isDark) 0.9f else 0.85f)
                else tint.copy(alpha = if (isDark) 0.22f else 0.13f)
            ),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (solidIcons2) Color.White else tint, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.OpenInNew, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp))
    }
}
