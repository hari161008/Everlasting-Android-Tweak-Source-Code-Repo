package com.coolappstore.everlastingandroidtweak.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.ui.navigation.Screen
import kotlinx.coroutines.delay

data class FeatureItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val category: String,
    val emoji: String = "",
    val iconColor: Long = 0xFF2196F3L
)

// All features available in the app — top-level so SettingsScreen can reference it
val EverlastingAllFeatures: List<FeatureItem> = listOf(
        // ── Gestures ──────────────────────────────────────────────────────────
        FeatureItem("Shake for Torch",       "Shake phone to toggle flashlight",             Icons.Default.FlashOn,             Screen.ShakeTorch.route,        "Gestures",        "🔦", 0xFFFF9800L),
        FeatureItem("Twist for Camera",      "Twist wrist to open camera",                  Icons.Default.CameraAlt,           Screen.TwistCamera.route,       "Gestures",        "📷", 0xFF009688L),
        FeatureItem("Double Tap Back",       "Double-tap back for torch or app",            Icons.Default.TouchApp,            Screen.DoubleTapBack.route,     "Gestures",        "👆", 0xFF673AB7L),
        FeatureItem("Flip to DND",           "Flip face-down to enable Do Not Disturb",     Icons.Default.DoNotDisturb,        Screen.FlipToDnd.route,         "Gestures",        "🤫", 0xFF3F51B5L),
        FeatureItem("Charging Animation",    "Beautiful animation when charger connects",   Icons.Default.BatteryChargingFull, Screen.ChargingAnimation.route, "Gestures",        "⚡", 0xFFFFD600L),
        FeatureItem("Custom Nav Bar",        "Overlay a custom navigation bar",             Icons.Default.Navigation,          Screen.NavBarOverlay.route,     "Gestures",        "🧭", 0xFF607D8BL),
        // ── Power & Buttons ───────────────────────────────────────────────────
        FeatureItem("Custom Power Menu",     "Replacement for the system power dialog",     Icons.Default.PowerSettingsNew,    Screen.CustomPowerMenu.route,   "Power & Buttons", "⚡", 0xFFE91E63L),
        FeatureItem("Double Power Press",    "Two quick power presses trigger an action",   Icons.Default.FlashOn,             Screen.DoublePowerPress.route,  "Power & Buttons", "⚡", 0xFFFF9800L),
        FeatureItem("Screen-Off Actions",    "Long-press buttons while screen is off",      Icons.Default.ScreenLockPortrait,  Screen.ScreenOffActions.route,  "Power & Buttons", "📵", 0xFF607D8BL),
        FeatureItem("Fake Power Off",        "Trick onlookers with a convincing fake shutdown", Icons.Default.PowerOff,        Screen.FakePowerOff.route,      "Power & Buttons", "😴", 0xFF37474FL),
        // ── Audio & Haptics ───────────────────────────────────────────────────
        FeatureItem("Built-in Equalizer",    "Tune audio with 5-band EQ",                  Icons.Default.Equalizer,           Screen.Equalizer.route,         "Audio & Haptics", "🎚️", 0xFF4CAF50L),
        FeatureItem("Volume Booster",        "Amplify audio beyond system limits",          Icons.AutoMirrored.Filled.VolumeUp, Screen.VolumeBooster.route,    "Audio & Haptics", "🔊", 0xFFF44336L),
        FeatureItem("Volume Styles",         "Custom volume panel styles",                  Icons.Default.Tune,                Screen.VolumeStyles.route,      "Audio & Haptics", "🎛️", 0xFF00BCD4L),
        FeatureItem("Custom Haptics",        "Customize tap & scroll vibration",            Icons.Default.Vibration,           Screen.Haptics.route,           "Audio & Haptics", "📳", 0xFF9C27B0L),
        FeatureItem("Custom Sounds",         "Lock, unlock, tap & charging sounds",         Icons.AutoMirrored.Filled.VolumeUp, Screen.CustomSounds.route,     "Audio & Haptics", "🔔", 0xFFFFC107L),
        FeatureItem("Call/Alarm Vibrations", "Custom vibration patterns",                   Icons.Default.Vibration,           Screen.VibrationPatterns.route, "Audio & Haptics", "📳", 0xFFE91E63L),
        // ── Customization ─────────────────────────────────────────────────────
        FeatureItem("Lock Screen Widgets",   "Add widgets to your lock screen",             Icons.Default.Widgets,             Screen.LockScreenWidgets.route, "Customization",   "🔒", 0xFF9C27B0L),
        FeatureItem("Custom QS Tiles",       "Add 15+ custom quick settings tiles",         Icons.Default.GridView,            Screen.CustomQSTiles.route,     "Customization",   "⚡", 0xFF00897BL),
        FeatureItem("Eye Dropper",           "Pick any color from your screen",             Icons.Default.Colorize,            Screen.EyeDropper.route,        "Customization",   "💧", 0xFF2196F3L),
        FeatureItem("Compass",               "Live compass with animated UI",               Icons.Default.Explore,             Screen.Compass.route,           "Customization",   "🧭", 0xFFF44336L),
        // ── Visuals ───────────────────────────────────────────────────────────
        FeatureItem("Screensaver",           "Beautiful screensaver themes",                Icons.Default.Slideshow,           Screen.Screensaver.route,       "Visuals",         "🖼️", 0xFF1565C0L),
        FeatureItem("Wallpaper Effects",     "Pixel-style wallpaper effects",               Icons.Default.Wallpaper,           Screen.WallpaperEffects.route,  "Visuals",         "🎨", 0xFF6A1B9AL),
        FeatureItem("AI Image Upscaler",     "Upscale photos to 2× or 4× with AI",          Icons.Default.AutoFixHigh,         Screen.ImageUpscaler.route,     "Visuals",         "✨", 0xFF7C4DFFL),
        FeatureItem("Watermark Photos",      "EXIF overlay & frame watermark",              Icons.Default.WaterDrop,           Screen.Watermark.route,         "Visuals",         "💧", 0xFF546E7AL),
        // ── Productivity ──────────────────────────────────────────────────────
        FeatureItem("App Freezer",           "Freeze, hide or suspend apps via Hail",       Icons.Default.AcUnit,              Screen.AppFreezer.route,        "Productivity",    "❄️", 0xFF2196F3L),
        FeatureItem("App Updater",           "Check GitHub for sideloaded app updates",     Icons.Default.SystemUpdateAlt,     Screen.AppUpdater.route,        "Productivity",    "⬆️", 0xFF4CAF50L),
        FeatureItem("Charge Limit Alarm",    "Alert when battery hits your set limit",      Icons.Default.BatteryAlert,        Screen.ChargeLimit.route,       "Productivity",    "🔋", 0xFFFF9800L),
        FeatureItem("Keep Screen On",        "Prevent screen sleep + QS tile",              Icons.Default.Lightbulb,           Screen.KeepScreenOn.route,      "Productivity",    "💡", 0xFFFFEB3BL),
        // ── Device & System ───────────────────────────────────────────────────
        FeatureItem("Task Manager",          "View & kill running processes",               Icons.Default.Memory,              Screen.TaskManager.route,       "Device & System", "📱", 0xFF2196F3L),
        FeatureItem("Terminal",              "Run shell commands on device",                Icons.Default.Terminal,            Screen.Terminal.route,          "Device & System", "💻", 0xFF1B5E20L),
        FeatureItem("Cache Cleaner",         "Clear app caches via Shizuku",               Icons.Default.CleaningServices,    Screen.CacheCleaner.route,      "Device & System", "🧹", 0xFFFF9800L),

        FeatureItem("Secondary Display",     "Project content to a second screen",          Icons.Default.Cast,                Screen.SecondaryDisplay.route,  "Device & System", "📺", 0xFF1565C0L),
        FeatureItem("Auto Reboot",           "Schedule automatic reboots",                  Icons.Default.RestartAlt,          Screen.AutoReboot.route,        "Device & System", "🔄", 0xFFFF5722L),
        FeatureItem("Hidden Features",       "Unlock hidden Android settings",              Icons.Default.Lock,                Screen.HiddenFeatures.route,    "Device & System", "🔓", 0xFF9C27B0L),
        FeatureItem("Maps Power Saving",     "Reduce battery drain during navigation",      Icons.Default.Map,                 Screen.MapsPowerSaving.route,   "Device & System", "🗺️", 0xFF00897BL),
        // ── Security ──────────────────────────────────────────────────────────
        FeatureItem("Security Motion Alert", "Alert when device is moved",                  Icons.Default.Security,            Screen.SecurityMotion.route,    "Security",        "🚨", 0xFFF44336L),
        FeatureItem("Screenshot Blocker",    "Block screenshots & screen recording",        Icons.Default.Screenshot,          Screen.ScreenshotBlocker.route, "Security",        "🛡️", 0xFF2196F3L),
        FeatureItem("Screen Locked Security","Block network access when keyguard is locked",Icons.Default.Lock,                Screen.ScreenLockedSecurity.route,"Security",      "🔐", 0xFF7C4DFFL),
        // ── Display & System ──────────────────────────────────────────────────
        FeatureItem("Scale & Adjustments",   "Switch display scale & animation profiles",  Icons.Default.Tune,                Screen.ScaleAdjustments.route,  "Device & System", "📐", 0xFF009688L),
        // ── Communication ─────────────────────────────────────────────────────
        FeatureItem("Walkie Talkie",         "Push-to-talk over Wi-Fi Direct",             Icons.Default.SettingsVoice,       Screen.WalkieTalkie.route,      "Communication",   "📻", 0xFF009688L),
        FeatureItem("Fake Call",             "Simulate an incoming call",                   Icons.Default.Call,                Screen.FakeCall.route,          "Communication",   "📞", 0xFF4CAF50L),
        // ── Notifications ─────────────────────────────────────────────────────
        FeatureItem("Notification Lighting", "Edge light & flashlight pulse",               Icons.Default.NotificationsActive, Screen.NotifLight.route,        "Notifications",   "✨", 0xFFFFB300L),
        FeatureItem("Battery Health",        "Battery stats & OEM data",                    Icons.Default.BatteryFull,         Screen.BatteryHealth.route,     "Notifications",   "🔋", 0xFF2E7D32L),
        // ── Sensors ───────────────────────────────────────────────────────────
        FeatureItem("Magnetic Field",        "Live magnetic field strength sensor",         Icons.Default.Sensors,             Screen.MagneticField.route,     "Sensors",         "🧲", 0xFF00897BL),
        FeatureItem("Device Info",           "Full hardware & software specs",              Icons.Default.PhoneAndroid,        Screen.DeviceInfo.route,        "Sensors",         "📱", 0xFF1976D2L),
        // ── Music & Light ─────────────────────────────────────────────────────
        FeatureItem("Music Reactive Light",  "Flash & vibrate to music beats",             Icons.Default.MusicNote,           Screen.MusicLight.route,        "Music & Light",   "🎵", 0xFFFF5722L),
        FeatureItem("Music Leveler",         "Animated equalizer bar across screen",        Icons.Default.BarChart,            Screen.MusicLeveler.route,      "Music & Light",   "📊", 0xFF03A9F4L),
        // ── AI Writing ────────────────────────────────────────────────────────
        FeatureItem("SwiftSlate AI",         "AI-powered text replacement anywhere",        Icons.Default.AutoFixHigh,         Screen.SwiftSlate.route,        "AI Writing",      "✨", 0xFF7C4DFFL),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current



    val useEmojiIcons       by AppPreferences.get(AppPreferences.USE_EMOJI_ICONS, false).collectAsState(false)
    val iconStyleSolid      by AppPreferences.get(AppPreferences.ICON_STYLE_SOLID, false).collectAsState(false)
    // 0 = Match App UI (opaque surfaceContainer), 1 = Blur (frosted), 2 = System default
    val homePillStyle       by AppPreferences.get(AppPreferences.HOME_PILL_STYLE, 0).collectAsState(0)
    val homePillBlurRadius  by AppPreferences.get(AppPreferences.HOME_PILL_BLUR_INTENSITY, 16f).collectAsState(16f)

    val allFeatures = EverlastingAllFeatures
    val hiddenRoutesStr by AppPreferences.get(AppPreferences.HIDDEN_FEATURE_ROUTES, "").collectAsState("")
    val hiddenRoutes = remember(hiddenRoutesStr) {
        if (hiddenRoutesStr.isBlank()) emptySet<String>()
        else hiddenRoutesStr.split(",").filter { it.isNotBlank() }.toSet()
    }
    val visibleFeatures = remember(allFeatures, hiddenRoutes) {
        allFeatures.filter { it.route !in hiddenRoutes }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All") + visibleFeatures.map { it.category }.distinct()

    val filteredFeatures = remember(searchQuery, selectedCategory, hiddenRoutes) {
        visibleFeatures.filter { f ->
            val matchSearch = searchQuery.isBlank() ||
                f.title.contains(searchQuery, ignoreCase = true) ||
                f.subtitle.contains(searchQuery, ignoreCase = true) ||
                f.category.contains(searchQuery, ignoreCase = true)
            val matchCat = selectedCategory == "All" || f.category == selectedCategory
            matchSearch && matchCat
        }
    }

    val groupedFeatures = remember(filteredFeatures, selectedCategory, searchQuery) {
        if (selectedCategory != "All" || searchQuery.isNotBlank())
            mapOf((if (selectedCategory != "All") selectedCategory else "Results") to filteredFeatures)
        else
            filteredFeatures.groupBy { it.category }
    }

    val primary = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        AnimatedContent(
                            targetState = isSearching,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "topbar_anim"
                        ) { searching ->
                            if (searching) {
                                val fr = remember { FocusRequester() }
                                LaunchedEffect(Unit) {
                                    delay(80); try { fr.requestFocus() } catch (_: Exception) {}
                                }
                                TextField(
                                    value = searchQuery, onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search features…") },
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxWidth().focusRequester(fr),
                                    leadingIcon = { Icon(Icons.Default.Search, null, tint = primary) }
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                                            .background(primary.copy(alpha = if (isDark) 0.22f else 0.13f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(":)", fontSize = 13.sp, fontWeight = FontWeight.Black, color = primary)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text("Everlasting", fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium)
                                        Text("${visibleFeatures.size} features",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = !isSearching; if (!isSearching) searchQuery = "" }) {
                            Icon(if (isSearching) Icons.Default.Close else Icons.Default.Search, null)
                        }
                        if (!isSearching) {
                            IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                                Icon(Icons.Default.Settings, null)
                            }
                        }
                    },
                    // FIX: Use surface — same token the pill row background defaults to.
                    // Both AppBar + pill row now share one unified header color.
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // ── Category chip row ────────────────────────────────────────────────────
                //
                // FIX 1 – Shade mismatch: styles 0 & 2 use Color.Transparent so the pill
                //   row inherits the same 'surface' background as the AppBar above, making
                //   the whole header one unified color band.
                //
                // FIX 2 – Blur only the background: for style 1 (frosted glass) the blurred
                //   layer and the chips are in separate composables stacked inside a Box.
                //   Modifier.blur() is applied ONLY to the background Box — the chips
                //   (text + icons) are drawn on a separate layer above and stay crisp.
                Box(modifier = Modifier.fillMaxWidth()) {

                // Background layer — present for style 0 (solid) and style 1 (blur).
                    // For style 2 (system default) nothing is drawn here; chips float over
                    // the plain surface background.
                    when (homePillStyle) {
                        0 -> {
                            // Solid: same surface color as AppBar → seamless header
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(MaterialTheme.colorScheme.surface)
                            )
                        }
                        1 -> {
                            // Frosted glass: semi-transparent surface so scrolled list
                            // content shows through underneath. The actual blur effect
                            // comes from the background gradient layer in MainActivity
                            // (enabled via Settings → Blur UI Containers). Applying
                            // realBlur() to a solid-colour box has zero visible effect
                            // since there are no pixels to diffuse — removed.
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                                    )
                            )
                        }
                        // style 2 → transparent (system decides background)
                    }

                    // Chip content — always unblurred, drawn on top of the background layer
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val selected = selectedCategory == cat
                            FilterChip(
                                selected = selected,
                                onClick = { selectedCategory = cat },
                                label = {
                                    Text(
                                        cat,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    // Chips use surfaceContainerHigh so they remain visible
                                    // against the surface background of the pill row.
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
                start = 16.dp, end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (filteredFeatures.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 80.dp), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(68.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.SearchOff, null,
                                    modifier = Modifier.size(34.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Nothing found", style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text("Try a different term or category",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                groupedFeatures.forEach { (category, features) ->
                    item(key = "hdr_$category") {
                        val showLabel = selectedCategory == "All" && searchQuery.isBlank()
                        if (showLabel) {
                            Row(
                                Modifier.padding(start = 4.dp, top = 16.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(category, style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold, color = primary)
                                Text("${features.size}", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else if (searchQuery.isNotBlank() && category == "Results") {
                            Text("${features.size} result${if (features.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp))
                        }
                    }
                    item(key = "grp_$category") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            features.forEachIndexed { idx, feature ->
                                FeatureListItem(feature, navController, useEmojiIcons, iconStyleSolid)
                                if (idx < features.lastIndex)
                                    HorizontalDivider(
                                        Modifier.padding(start = 72.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureListItem(
    feature: FeatureItem,
    navController: NavController,
    useEmoji: Boolean = false,    // kept for API compat but emojis never shown on home screen
    solidStyle: Boolean = false
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable {
                when (feature.route) {
                    Screen.Watermark.route -> {
                        val ctx = navController.context
                        ctx.startActivity(android.content.Intent(ctx,
                            com.coolappstore.everlastingandroidtweak.ui.activities.WatermarkActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    Screen.SwiftSlate.route -> {
                        val ctx = navController.context
                        ctx.startActivity(android.content.Intent(ctx,
                            com.musheer360.swiftslate.MainActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    Screen.AppFreezer.route -> {
                        val ctx = navController.context
                        // FIX: suppress default Activity transition so no blank-screen flash
                        val opts = android.app.ActivityOptions
                            .makeCustomAnimation(ctx, 0, 0).toBundle()
                        ctx.startActivity(
                            android.content.Intent(ctx,
                                com.aistra.hail.ui.main.MainActivity::class.java)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            opts
                        )
                    }
                    else -> navController.navigate(feature.route)
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val iconColor = Color(feature.iconColor)
        // ROOT CAUSE FIX: "Emoji Icons" setting previously put literal emoji characters
        // on the home screen cards. Emojis look inconsistent across devices (some show
        // as text, some pixelate at small sizes) and the user wants clean vector icons.
        // Now we ALWAYS use vector icons. When useEmoji (now meaning "colorful/vivid style")
        // is ON we show solid opaque bubble + white icon; OFF = translucent subtle bubble.
        if (solidStyle || useEmoji) {
            // Solid / colorful mode: fully opaque background bubble + white icon
            val bg = if (isDark) iconColor.copy(alpha = 0.88f)
                     else Color(iconColor.red * 0.78f, iconColor.green * 0.78f, iconColor.blue * 0.78f, 0.9f)
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(bg),
                contentAlignment = Alignment.Center) {
                Icon(feature.icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        } else {
            // Translucent / subtle mode
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                .background(iconColor.copy(alpha = if (isDark) 0.20f else 0.13f)),
                contentAlignment = Alignment.Center) {
                Icon(feature.icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(feature.title, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(feature.subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp))
    }
}
