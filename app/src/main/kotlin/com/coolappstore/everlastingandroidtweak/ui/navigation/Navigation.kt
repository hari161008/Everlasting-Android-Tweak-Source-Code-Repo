package com.coolappstore.everlastingandroidtweak.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.coolappstore.everlastingandroidtweak.ui.screens.*

sealed class Screen(val route: String) {
    object Home                : Screen("home")
    object Settings            : Screen("settings")
    object ShakeTorch          : Screen("shake_torch")
    object TwistCamera         : Screen("twist_camera")
    object TaskManager         : Screen("task_manager")
    object Terminal            : Screen("terminal")
    object Equalizer           : Screen("equalizer")
    object Screensaver         : Screen("screensaver")
    object AutoReboot          : Screen("auto_reboot")
    object Watermark           : Screen("watermark")
    object CacheCleaner        : Screen("cache_cleaner")
    object MusicLight          : Screen("music_light")
    object Haptics             : Screen("haptics")
    object CustomSounds        : Screen("custom_sounds")
    object NavBarOverlay       : Screen("navbar_overlay")
    object ScreenshotBlocker   : Screen("screenshot_blocker")
    object VolumeStyles        : Screen("volume_styles")
    object HiddenFeatures      : Screen("hidden_features")
    object WallpaperEffects    : Screen("wallpaper_effects")
    object KeepScreenOn        : Screen("keep_screen_on")
    object DoubleTapBack       : Screen("double_tap_back")
    object FlipToDnd           : Screen("flip_to_dnd")
    object ChargingSound       : Screen("charging_sound")
    object ChargingAnimation   : Screen("charging_animation")
    object MapsPowerSaving     : Screen("maps_power_saving")
    object VolumeBooster       : Screen("volume_booster")
    object MusicLeveler        : Screen("music_leveler")
    object SecurityMotion      : Screen("security_motion")
    object WalkieTalkie        : Screen("walkie_talkie")
    object Compass             : Screen("compass")
    object FakeCall            : Screen("fake_call")
    object VibrationPatterns   : Screen("vibration_patterns")
    object LockScreenWidgets   : Screen("lock_screen_widgets")
    object CustomQSTiles       : Screen("custom_qs_tiles")
    object ChargeLimit         : Screen("charge_limit")
    object AppFreezer          : Screen("app_freezer")
    object AppUpdater          : Screen("app_updater")
    object EyeDropper          : Screen("eye_dropper")
    object NotifLight          : Screen("notif_light")
    object BatteryHealth       : Screen("battery_health")
    object DeviceInfo          : Screen("device_info")
    object FlashIso            : Screen("flash_iso")
    object SecondaryDisplay    : Screen("secondary_display")
    object MagneticField       : Screen("magnetic_field")
    object ImageUpscaler       : Screen("image_upscaler")
    // New features
    object CustomPowerMenu     : Screen("custom_power_menu")
    object ScreenOffActions    : Screen("screen_off_actions")
    object DoublePowerPress    : Screen("double_power_press")
    object Shizuku             : Screen("shizuku")
    object SwiftSlate          : Screen("swiftslate")
    object FakePowerOff        : Screen("fake_power_off")
    object VisibleFeatures     : Screen("visible_features")
    object RatingReview        : Screen("rating_review")
    object ReviewsWebView      : Screen("reviews_webview")
    object ScreenLockedSecurity : Screen("screen_locked_security")
    object ScaleAdjustments    : Screen("scale_adjustments")
}

@Composable
fun EverlastingNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route)               { HomeScreen(navController) }
        composable(Screen.Settings.route)           { SettingsScreen(navController) }
        composable(Screen.ShakeTorch.route)         { ShakeTorchScreen(navController) }
        composable(Screen.TwistCamera.route)        { TwistCameraScreen(navController) }
        composable(Screen.TaskManager.route)        { TaskManagerScreen(navController) }
        composable(Screen.Terminal.route)           { TerminalScreen(navController) }
        composable(Screen.Equalizer.route)          { EqualizerScreen(navController) }
        composable(Screen.Screensaver.route)        { ScreensaverScreen(navController) }
        composable(Screen.AutoReboot.route)         { AutoRebootScreen(navController) }
        composable(Screen.Watermark.route)          { /* Watermark launches WatermarkActivity from HomeScreen click handler */ }
        composable(Screen.CacheCleaner.route)       { CacheCleanerScreen(navController) }
        composable(Screen.MusicLight.route)         { MusicLightScreen(navController) }
        composable(Screen.Haptics.route)            { HapticsScreen(navController) }
        composable(Screen.CustomSounds.route)       { CustomSoundsScreen(navController) }
        composable(Screen.NavBarOverlay.route)      { NavBarOverlayScreen(navController) }
        composable(Screen.ScreenshotBlocker.route)  { ScreenshotBlockerScreen(navController) }
        composable(Screen.VolumeStyles.route)       { VolumeStylesScreen(navController) }
        composable(Screen.HiddenFeatures.route)     { HiddenFeaturesScreen(navController) }
        composable(Screen.WallpaperEffects.route)   { WallpaperEffectsScreen(navController) }
        composable(Screen.KeepScreenOn.route)       { KeepScreenOnScreen(navController) }
        composable(Screen.DoubleTapBack.route)      { DoubleTapBackScreen(navController) }
        composable(Screen.FlipToDnd.route)          { FlipToDndScreen(navController) }
        composable(Screen.ChargingSound.route)      { ChargingSoundScreen(navController) }
        composable(Screen.ChargingAnimation.route)  { ChargingAnimationScreen(navController) }
        composable(Screen.MapsPowerSaving.route)    { MapsPowerSavingScreen(navController) }
        // Watermark now uses EssentialsWatermarkScreen — launched via WatermarkActivity
        // The Watermark nav route redirects to the activity via the HomeScreen card click
        composable(Screen.VolumeBooster.route)      { VolumeBoosterScreen(navController) }
        composable(Screen.MusicLeveler.route)       { MusicLevelerScreen(navController) }
        composable(Screen.SecurityMotion.route)     { SecurityMotionScreen(navController) }
        composable(Screen.WalkieTalkie.route)       { WalkieTalkieScreen(navController) }
        composable(Screen.Compass.route)            { CompassScreen(navController) }
        composable(Screen.FakeCall.route)           { FakeCallScreen(navController) }
        composable(Screen.VibrationPatterns.route)  { VibrationPatternsScreen(navController) }
        composable(Screen.LockScreenWidgets.route)  { LockScreenWidgetsScreen(navController) }
        composable(Screen.CustomQSTiles.route)      { CustomQSTilesScreen(navController) }
        composable(Screen.ChargeLimit.route)        { ChargeLimitScreen(navController) }
        composable(Screen.AppFreezer.route)         { /* Hail launches as standalone Activity from HomeScreen click handler */ }
        composable(Screen.AppUpdater.route)         { AppUpdaterScreen(navController) }
        composable(Screen.EyeDropper.route)         { EyeDropperScreen(navController) }
        composable(Screen.NotifLight.route)         { NotifLightScreen(navController) }
        composable(Screen.BatteryHealth.route)      { BatteryHealthScreen(navController) }
        composable(Screen.DeviceInfo.route)         { DeviceInfoScreen(navController) }
        composable(Screen.FlashIso.route)           { FlashIsoScreen(navController) }
        composable(Screen.SecondaryDisplay.route)   { SecondaryDisplayScreen(navController) }
        composable(Screen.MagneticField.route)      { MagneticFieldScreen(navController) }
        // New feature screens
        composable(Screen.CustomPowerMenu.route)    { CustomPowerMenuScreen(navController) }
        composable(Screen.ScreenOffActions.route)   { ScreenOffActionsScreen(navController) }
        composable(Screen.DoublePowerPress.route)   { DoublePowerPressScreen(navController) }
        composable(Screen.Shizuku.route)            { ShizukuScreen(navController) }
        // SwiftSlate launches as a standalone Activity from HomeScreen click handler
        composable(Screen.ImageUpscaler.route)      { ImageUpscalerScreen(navController) }
        composable(Screen.SwiftSlate.route)         { }
        composable(Screen.FakePowerOff.route)       { FakePowerOffScreen(navController) }
        composable(Screen.VisibleFeatures.route)    { VisibleFeaturesScreen(navController) }
        composable(Screen.RatingReview.route)       { RatingScreen(navController) }
        composable(Screen.ReviewsWebView.route)     { ReviewsWebViewScreen(navController) }
        composable(Screen.ScreenLockedSecurity.route) { ScreenLockedSecurityScreen(navController) }
        composable(Screen.ScaleAdjustments.route)   { ScaleAdjustmentsScreen(navController) }
    }
}
