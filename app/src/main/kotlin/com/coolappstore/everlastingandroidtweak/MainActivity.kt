package com.coolappstore.everlastingandroidtweak

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.services.EverlastingForegroundService
import com.coolappstore.everlastingandroidtweak.ui.navigation.EverlastingNavHost
import com.coolappstore.everlastingandroidtweak.ui.theme.EverlastingTheme
import com.aistra.hail.app.HailData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.content.Intent

// ── Splash Screen ─────────────────────────────────────────────────────────────
@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    val bg    = MaterialTheme.colorScheme.background
    val fg    = MaterialTheme.colorScheme.primary
    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow))
        delay(1400)
        alpha.animateTo(0f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        onFinished()
    }
    Box(Modifier.fillMaxSize().alpha(alpha.value).background(bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(":)", fontSize = 80.sp, color = fg, fontWeight = FontWeight.Black,
                modifier = Modifier.scale(scale.value))
            Spacer(Modifier.height(8.dp))
            Text("Everlasting Tweak", fontSize = 22.sp, color = fg,
                textAlign = TextAlign.Center, modifier = Modifier.alpha(scale.value))
            Spacer(Modifier.height(4.dp))
            Text("Think Different !", fontSize = 13.sp,
                color = fg.copy(alpha = 0.6f), textAlign = TextAlign.Center,
                modifier = Modifier.alpha(scale.value))
        }
    }
}

// ── Swipe-Up Onboarding ────────────────────────────────────────────────────────
//
// ROOT CAUSE FIX — smooth full-screen swipe:
//
// Old behavior: The exit animation was a simple `slideOutVertically` on the
// AnimatedVisibility wrapper.  This only animated the *container* — the content
// inside barely moved because Compose AnimatedVisibility exit animations clip to
// the composable's bounds.  The result was a jarring instant dismiss.
//
// New behavior:
//   1. The user drags UP.  The entire content moves with their finger in real-time
//      (offset = dragOffset * 1.0, i.e. 1:1 travel — not damped).
//   2. As they drag, the background fades linearly from 1→0 based on progress.
//   3. When the threshold is crossed OR on drag-end above threshold, we launch a
//      coroutine that:
//        a. Animates the offset all the way to -screenHeightPx (fully off screen).
//        b. Simultaneously fades alpha to 0.
//        c. Only calls onUnlocked() AFTER the animation completes.
//   4. If released below threshold, content springs back to y=0 with a bounce.
//
@Composable
private fun SwipeUpScreen(onUnlocked: () -> Unit) {
    val scope       = rememberCoroutineScope()
    val isDark      = isSystemInDarkTheme()
    val primary     = MaterialTheme.colorScheme.primary
    val config      = LocalConfiguration.current
    val screenHeightDp = config.screenHeightDp.toFloat()

    // ── Colour tokens — always follow the active MaterialTheme so dark/light/auto
    //    all look correct without any hardcoded colour guessing
    val bgColor       = MaterialTheme.colorScheme.background
    val textColor     = MaterialTheme.colorScheme.onBackground
    val subColor      = MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleBg      = MaterialTheme.colorScheme.surfaceContainer
    val iconTextColor = primary
    val arrowTint     = primary

    // ── Animations ───────────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "onboard")

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isDark) 0.18f else 0.10f,
        targetValue  = if (isDark) 0.45f else 0.28f,
        label        = "glow",
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -20f, label = "arrowY",
        animationSpec = infiniteRepeatable(
            animation  = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // ── Gesture state ─────────────────────────────────────────────────────────
    // SWIPE_THRESHOLD: how far up (in dp) to trigger unlock
    val SWIPE_THRESHOLD = -220f

    // contentOffset drives both the Y translation and the alpha during the swipe.
    // It runs from 0 (resting) to -screenHeightDp (fully off-screen).
    val contentOffset = remember { Animatable(0f) }
    val bgAlpha       = remember { Animatable(1f) }

    // Derived display values
    val progress = (-contentOffset.value / screenHeightDp).coerceIn(0f, 1f)

    fun triggerExit() {
        scope.launch {
            // Slide content fully off screen — pure translation, no fade
            contentOffset.animateTo(
                -screenHeightDp,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            )
            onUnlocked()
        }
    }

    fun bounceBack() {
        scope.launch {
            // Spring back to origin with a small overshoot
            contentOffset.animateTo(
                0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                )
            )
            bgAlpha.animateTo(1f, animationSpec = tween(150))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .alpha(bgAlpha.value)
            .background(bgColor)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (contentOffset.value < SWIPE_THRESHOLD) {
                            triggerExit()
                        } else {
                            bounceBack()
                        }
                    },
                    onVerticalDrag = { _, delta ->
                        if (delta < 0) {
                            // Only allow upward drag; clamp so it can't go below -screenHeight
                            val newVal = (contentOffset.value + delta)
                                .coerceAtLeast(-screenHeightDp)
                            scope.launch {
                                contentOffset.snapTo(newVal)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Radial glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primary.copy(alpha = glowAlpha * 0.60f),
                        primary.copy(alpha = glowAlpha * 0.20f),
                        Color.Transparent
                    ),
                    center = center.copy(y = center.y - 60f),
                    radius = size.minDimension * 0.75f
                )
            )
        }

        // Light theme: subtle bottom accent line
        if (!isDark) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, primary.copy(alpha = 0.5f), Color.Transparent)
                        )
                    )
            )
        }

        // ── Main content — translates 1:1 with the drag ───────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(y = contentOffset.value.dp)   // 1:1 finger tracking
        ) {
            // App icon bubble
            Box(
                Modifier
                    .size(88.dp)
                    .background(bubbleBg, shape = MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center
            ) {
                Text(":)", fontSize = 42.sp, color = iconTextColor, fontWeight = FontWeight.Black)
            }

            Spacer(Modifier.height(32.dp))
            Text("Everlasting Tweak", fontSize = 26.sp, color = textColor,
                fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(Modifier.height(6.dp))
            Text("Android Enhancement Suite", fontSize = 14.sp, color = subColor)

            Spacer(Modifier.height(80.dp))

            // Floating arrow indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = arrowOffset.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, null,
                    tint = arrowTint, modifier = Modifier.size(38.dp))
                Icon(Icons.Default.KeyboardArrowUp, null,
                    tint = arrowTint.copy(alpha = 0.35f),
                    modifier = Modifier.size(30.dp).offset(y = (-10).dp))
                Spacer(Modifier.height(12.dp))
                Text("Swipe up to start", fontSize = 14.sp,
                    color = subColor, letterSpacing = 1.2.sp)
            }
        }
    }
}

// ── Wallpaper loader ─────────────────────────────────────────────────────────
private suspend fun loadDeviceWallpaperBitmap(ctx: android.content.Context): ImageBitmap? =
    withContext(Dispatchers.IO) {
        try {
            val wm = WallpaperManager.getInstance(ctx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val pfd: ParcelFileDescriptor? = try {
                    wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
                } catch (_: Exception) { null }
                if (pfd != null) {
                    return@withContext try {
                        val bmp = android.graphics.BitmapFactory.decodeFileDescriptor(
                            pfd.fileDescriptor, null,
                            android.graphics.BitmapFactory.Options().also { it.inSampleSize = 2 }
                        )
                        pfd.close()
                        bmp?.asImageBitmap()
                    } catch (_: Exception) { pfd.close(); null }
                }
            }
            val drawable = try { wm.drawable } catch (_: Exception) {
                try { wm.peekDrawable() } catch (_: Exception) { null }
            }
            when (drawable) {
                is BitmapDrawable -> drawable.bitmap?.asImageBitmap()
                null -> null
                else -> {
                    val dm = ctx.resources.displayMetrics
                    val w = (dm.widthPixels / 2).coerceAtLeast(1)
                    val h = (dm.heightPixels / 2).coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(bmp)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(canvas)
                    val isBlank = bmp.getPixel(w / 2, h / 2) == android.graphics.Color.BLACK &&
                                  bmp.getPixel(w / 4, h / 4) == android.graphics.Color.BLACK
                    if (isBlank) null else bmp.asImageBitmap()
                }
            }
        } catch (_: Exception) { null }
    }

// ── Main Activity ─────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        EverlastingForegroundService.start(this)

        setContent {
            val dynamicColor    by AppPreferences.get(AppPreferences.DYNAMIC_COLOR, true).collectAsState(true)
            val themeMode       by AppPreferences.get(AppPreferences.DARK_THEME, 0).collectAsState(0)
            val blurEnabled     by AppPreferences.get(AppPreferences.UI_BLUR_ENABLED, false).collectAsState(false)
            val blurAmount      by AppPreferences.get(AppPreferences.UI_BLUR_AMOUNT, 16f).collectAsState(16f)
            val bgUri           by AppPreferences.get(AppPreferences.BG_WALLPAPER_URI, "").collectAsState("")
            val bgDim           by AppPreferences.get(AppPreferences.BG_DIM_AMOUNT, 0f).collectAsState(0f)
            val bgBlur          by AppPreferences.get(AppPreferences.BG_BLUR_ENABLED, false).collectAsState(false)
            val bgBlurAmount    by AppPreferences.get(AppPreferences.BG_BLUR_AMOUNT, 16f).collectAsState(16f)
            val customPrimary   by AppPreferences.get(AppPreferences.CUSTOM_PRIMARY_COLOR, "").collectAsState("")
            val useDeviceWp     by AppPreferences.get(AppPreferences.USE_DEVICE_WALLPAPER, false).collectAsState(false)
            val firstLaunchDone by AppPreferences.get(AppPreferences.FIRST_LAUNCH_DONE, false).collectAsState(false)
            val systemDark      = isSystemInDarkTheme()

            // On startup, sync Hail's stored theme → Everlasting's themeMode pref (first-launch
            // import so the app honours whatever the user had set in standalone Hail before)
            LaunchedEffect(Unit) {
                val hailTheme = androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(this@MainActivity)
                    .getString(HailData.APP_THEME, HailData.FOLLOW_SYSTEM) ?: HailData.FOLLOW_SYSTEM
                val everlastingTheme = when (hailTheme) {
                    HailData.THEME_LIGHT -> 1
                    HailData.THEME_DARK  -> 2
                    else                 -> 0
                }
                // Only sync Hail's theme after first launch — on fresh install
                // we want the DataStore default (0 = auto/system) to win, not whatever
                // Hail had stored from a previous installation.
                if (firstLaunchDone && everlastingTheme != themeMode) {
                    AppPreferences.set(AppPreferences.DARK_THEME, everlastingTheme)
                }
            }

            // APP-WIDE SYNC: whenever themeMode or dynamicColor changes (in any screen, not just
            // SettingsScreen), mirror both values into SharedPreferences so the Hail Compose
            // AppTheme's SharedPrefs listeners pick them up and recompose without a restart.
            val _sharedPrefsForHailSync = remember {
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@MainActivity)
            }
            LaunchedEffect(themeMode, dynamicColor) {
                _sharedPrefsForHailSync.edit()
                    .putInt(HailData.EVERLASTING_THEME_MODE, themeMode)
                    .putBoolean(HailData.EVERLASTING_DYNAMIC_COLOR, dynamicColor)
                    .apply()
            }
            val isDark = when (themeMode) { 1->false; 2->true; 3->true; 4->false; else->systemDark }
            val wallpaperActive = bgUri.isNotEmpty() || useDeviceWp
            val ctx             = LocalContext.current
            val scope           = rememberCoroutineScope()

            var deviceWpBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(useDeviceWp) {
                deviceWpBitmap = if (useDeviceWp) loadDeviceWallpaperBitmap(ctx) else null
            }

            EverlastingTheme(
                darkTheme          = isDark,
                dynamicColor       = dynamicColor,
                themeMode          = themeMode,
                blurEnabled        = blurEnabled,
                blurAmount         = blurAmount,
                wallpaperActive    = wallpaperActive,
                customPrimaryColor = customPrimary
            ) {
                var showSplash       by remember { mutableStateOf(true) }
                var showOnboarding  by remember { mutableStateOf(false) }
                var showWelcomePopup by remember { mutableStateOf(false) }
                val primary = MaterialTheme.colorScheme.primary

                Box(Modifier.fillMaxSize()) {

                    // ── Background layers ──────────────────────────────────

                    val wpBitmap = deviceWpBitmap
                    if (useDeviceWp && wpBitmap != null) {
                        val blurMod = if (bgBlur || blurEnabled)
                            Modifier.blur((bgBlurAmount.coerceAtLeast(blurAmount)).coerceAtLeast(1f).dp)
                        else Modifier
                        Image(bitmap = wpBitmap, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().then(blurMod))
                        if (bgDim > 0.01f)
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgDim)))
                    }

                    if (bgUri.isNotEmpty() && !useDeviceWp) {
                        key(bgUri) {
                            AsyncImage(
                                model = ImageRequest.Builder(ctx).data(Uri.parse(bgUri))
                                    .crossfade(true).allowHardware(false).memoryCacheKey(bgUri).build(),
                                contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                                    .then(if (bgBlur) Modifier.blur(bgBlurAmount.coerceAtLeast(1f).dp) else Modifier))
                        }
                        if (bgDim > 0.01f)
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = bgDim)))
                    }

                    // ── UI blur background layer ─────────────────────────────
                    // BUG FIX: The blur modifier is applied ONLY to this background
                    // Box which has NO children.  The app UI (EverlastingNavHost) is
                    // in a SIBLING Box rendered afterwards — it is never blurred.
                    // Using graphicsLayer with RenderEffect on API 31+ ensures the
                    // GPU composites this Box in its own isolated render node so the
                    // blur effect cannot visually bleed into the layer above it.
                    if (blurEnabled) {
                        if (wallpaperActive) {
                            // Wallpaper is already blurred above; just add a tinted
                            // overlay so card surfaces read better over the image.
                            Box(
                                Modifier.fillMaxSize()
                                    .background(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.2f)
                                    )
                            )
                        } else {
                            // No wallpaper: render a blurred gradient as the visual
                            // backdrop. Content is drawn in the sibling Box below.
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                // API 31+: use hardware-accelerated RenderEffect so the
                                // blur is composited in a separate render node — it
                                // cannot affect sibling composables drawn on top.
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            val radius = blurAmount.coerceIn(8f, 32f) * density
                                            renderEffect = android.graphics.RenderEffect
                                                .createBlurEffect(radius, radius,
                                                    android.graphics.Shader.TileMode.CLAMP)
                                                .asComposeRenderEffect()
                                        }
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    primary.copy(alpha = 0.25f),
                                                    MaterialTheme.colorScheme.background,
                                                    primary.copy(alpha = 0.18f),
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                                )
                                            )
                                        )
                                )
                            } else {
                                // API < 31: software Modifier.blur() — still isolated to
                                // this Box since it has no children and its parent Box
                                // draws each child in its own composable scope.
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .blur(blurAmount.coerceIn(8f, 32f).dp)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    primary.copy(alpha = 0.25f),
                                                    MaterialTheme.colorScheme.background,
                                                    primary.copy(alpha = 0.18f),
                                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                                )
                                            )
                                        )
                                )
                            }
                        }
                    }

                    // ── Main App UI ──────────────────────────────────────
                    AnimatedVisibility(visible = !showSplash && !showOnboarding,
                        enter = fadeIn(tween(350))) {
                        EverlastingNavHost()
                    }

                    // ── Onboarding ───────────────────────────────────────
                    // ROOT CAUSE FIX: the onboarding slide no longer uses
                    // AnimatedVisibility exit animation. The SwipeUpScreen
                    // itself drives the exit (animates content to -screenHeight
                    // + fades bg) before calling onUnlocked(), at which point
                    // we just flip the flag. No jarring container-level exit.
                    if (showOnboarding) {
                        SwipeUpScreen {
                            scope.launch {
                                AppPreferences.set(AppPreferences.FIRST_LAUNCH_DONE, true)
                                showOnboarding = false
                                showWelcomePopup = true   // show support popup once
                            }
                        }
                    }

                    // ── First-launch Telegram support popup ───────────────────
                    if (showWelcomePopup) {
                        AlertDialog(
                            onDismissRequest = { showWelcomePopup = false },
                            icon = {
                                Box(
                                    Modifier.size(52.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(":)", fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            title = {
                                Text("Welcome to Everlasting!", fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "You can support me only by joining my Telegram Channel and the app support group by navigating to Settings › About ❤️",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.medium)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            "Announcements | Updates | Bug Fixes | Feature Requests | Rate | Support",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    showWelcomePopup = false
                                    try {
                                        this@MainActivity.startActivity(
                                            Intent(Intent.ACTION_VIEW,
                                                Uri.parse("https://t.me/EverlastingAndroidTweak"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (_: Exception) {}
                                }) { Text("Join Telegram") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showWelcomePopup = false }) {
                                    Text("Maybe Later")
                                }
                            }
                        )
                    }

                    // ── Splash ───────────────────────────────────────────
                    if (showSplash) SplashScreen {
                        showSplash = false
                        if (!firstLaunchDone) showOnboarding = true
                    }
                }
            }
        }
    }
}
