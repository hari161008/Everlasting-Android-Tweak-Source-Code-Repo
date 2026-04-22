package com.coolappstore.everlastingandroidtweak.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "everlasting_prefs")

object AppPreferences {
    private lateinit var context: Context

    fun init(ctx: Context) { context = ctx.applicationContext }

    val SHAKE_TORCH_ENABLED          = booleanPreferencesKey("shake_torch_enabled")
    val SHAKE_SENSITIVITY            = floatPreferencesKey("shake_sensitivity")
    val SHAKE_PROXIMITY_ENABLED      = booleanPreferencesKey("shake_proximity_enabled")
    val TWIST_CAMERA_ENABLED         = booleanPreferencesKey("twist_camera_enabled")
    val TWIST_SENSITIVITY            = floatPreferencesKey("twist_sensitivity")
    val TWIST_PROXIMITY_ENABLED      = booleanPreferencesKey("twist_proximity_enabled")
    val CUSTOM_HAPTICS_ENABLED       = booleanPreferencesKey("custom_haptics_enabled")
    val HAPTICS_INTENSITY            = intPreferencesKey("haptics_intensity")
    val HAPTICS_TAP_INTENSITY        = intPreferencesKey("haptics_tap_intensity")
    val HAPTICS_SCROLL_INTENSITY     = intPreferencesKey("haptics_scroll_intensity")
    val HAPTICS_SCROLL_ENABLED       = booleanPreferencesKey("haptics_scroll_enabled")
    val HAPTICS_PATTERN              = stringPreferencesKey("haptics_pattern")
    val TAP_SOUND_ENABLED            = booleanPreferencesKey("tap_sound_enabled")
    val TAP_SOUND_URI                = stringPreferencesKey("tap_sound_uri")
    val LOCK_SOUND_ENABLED           = booleanPreferencesKey("lock_sound_enabled")
    val UNLOCK_SOUND_ENABLED         = booleanPreferencesKey("unlock_sound_enabled")
    val LOCK_SOUND_URI               = stringPreferencesKey("lock_sound_uri")
    val UNLOCK_SOUND_URI             = stringPreferencesKey("unlock_sound_uri")
    val MUSIC_LIGHT_ENABLED          = booleanPreferencesKey("music_light_enabled")
    val MUSIC_VIBRATE_ENABLED        = booleanPreferencesKey("music_vibrate_enabled")
    val MUSIC_LIGHT_SENSITIVITY      = floatPreferencesKey("music_light_sensitivity")
    val MUSIC_BLINK_SPEED            = floatPreferencesKey("music_blink_speed")
    val MUSIC_VIBRATION_INTENSITY    = intPreferencesKey("music_vibration_intensity")
    val MUSIC_SPEED_SENSITIVITY      = floatPreferencesKey("music_speed_sensitivity")
    val MUSIC_BLINK_DURATION_MS      = intPreferencesKey("music_blink_duration_ms")
    val WATERMARK_ENABLED            = booleanPreferencesKey("watermark_enabled")
    val WATERMARK_TEXT               = stringPreferencesKey("watermark_text")
    val WATERMARK_POSITION           = stringPreferencesKey("watermark_position")
    val WATERMARK_FONT_SIZE          = floatPreferencesKey("watermark_font_size")
    val WATERMARK_COLOR              = stringPreferencesKey("watermark_color")
    val WATERMARK_OPACITY            = floatPreferencesKey("watermark_opacity")
    val WATERMARK_BOLD               = booleanPreferencesKey("watermark_bold")
    val WATERMARK_SHADOW             = booleanPreferencesKey("watermark_shadow")
    val AUTO_REBOOT_ENABLED          = booleanPreferencesKey("auto_reboot_enabled")
    val AUTO_REBOOT_TIME             = stringPreferencesKey("auto_reboot_time")
    val AUTO_REBOOT_DAYS             = stringPreferencesKey("auto_reboot_days")
    val DYNAMIC_COLOR                = booleanPreferencesKey("dynamic_color")
    val DARK_THEME                   = intPreferencesKey("dark_theme")
    val SCREENSHOT_BLOCK_ENABLED     = booleanPreferencesKey("screenshot_block_enabled")
    val NAVBAR_OVERLAY_ENABLED       = booleanPreferencesKey("navbar_overlay_enabled")
    val NAVBAR_STYLE                 = stringPreferencesKey("navbar_style")
    val NAVBAR_PILL_COLOR            = stringPreferencesKey("navbar_pill_color")
    val NAVBAR_PILL_OPACITY          = floatPreferencesKey("navbar_pill_opacity")
    val NAVBAR_HEIGHT                = floatPreferencesKey("navbar_height")
    val NAVBAR_Y_OFFSET              = intPreferencesKey("navbar_y_offset")
    val VOLUME_STYLE                 = stringPreferencesKey("volume_style")
    val VOLUME_COLOR                 = stringPreferencesKey("volume_color")
    val VOLUME_CORNER_RADIUS         = floatPreferencesKey("volume_corner_radius")
    val VOLUME_OPACITY               = floatPreferencesKey("volume_opacity")

    // Screensaver — extended
    val SCREENSAVER_THEME            = stringPreferencesKey("screensaver_theme")
    val SCREENSAVER_COLOR            = stringPreferencesKey("screensaver_color")
    val SCREENSAVER_SIZE             = floatPreferencesKey("screensaver_size")
    val SCREENSAVER_FADE_DURATION    = intPreferencesKey("screensaver_fade_duration")
    val SCREENSAVER_MOVE_ENABLED     = booleanPreferencesKey("screensaver_move_enabled")
    val SCREENSAVER_MOVE_SPEED       = intPreferencesKey("screensaver_move_speed")
    val SCREENSAVER_MOVE_INTERVAL_S  = intPreferencesKey("screensaver_move_interval_s")
    val SCREENSAVER_CLOCK_STYLE      = stringPreferencesKey("screensaver_clock_style")
    val SCREENSAVER_CLOCK_COLOR      = stringPreferencesKey("screensaver_clock_color")
    val SCREENSAVER_SHOW_BATTERY     = booleanPreferencesKey("screensaver_show_battery")
    val SCREENSAVER_SHOW_DATE        = booleanPreferencesKey("screensaver_show_date")
    val SCREENSAVER_BURN_IN_ENABLED  = booleanPreferencesKey("screensaver_burn_in_enabled")
    val SCREENSAVER_BURN_IN_INTERVAL = intPreferencesKey("screensaver_burn_in_interval")

    // ── Moto Screen Saver ────────────────────────────────────────────────────
    val SCREENSAVER_MOTO_GLOW_COLOR         = stringPreferencesKey("ss_moto_glow_color")
    val SCREENSAVER_MOTO_TEXT_COLOR         = stringPreferencesKey("ss_moto_text_color")
    val SCREENSAVER_MOTO_ARC_COLOR          = stringPreferencesKey("ss_moto_arc_color")
    val SCREENSAVER_MOTO_BRANDING_TEXT      = stringPreferencesKey("ss_moto_branding_text")
    val SCREENSAVER_MOTO_SHOW_BRANDING      = booleanPreferencesKey("ss_moto_show_branding")
    val SCREENSAVER_MOTO_SHOW_ARC           = booleanPreferencesKey("ss_moto_show_arc")
    val SCREENSAVER_MOTO_GLOW_SIZE          = floatPreferencesKey("ss_moto_glow_size")
    val SCREENSAVER_MOTO_PULSE_SPEED        = floatPreferencesKey("ss_moto_pulse_speed")
    val SCREENSAVER_MOTO_BG_COLOR           = stringPreferencesKey("ss_moto_bg_color")
    val SCREENSAVER_MOTO_FONT_SIZE          = floatPreferencesKey("ss_moto_font_size")
    val SCREENSAVER_MOTO_SHOW_BATTERY       = booleanPreferencesKey("ss_moto_show_battery_pct")
    val SCREENSAVER_MOTO_CUSTOM_PCT         = intPreferencesKey("ss_moto_custom_pct")
    val SCREENSAVER_MOTO_USE_REAL_PCT       = booleanPreferencesKey("ss_moto_use_real_pct")
    val SCREENSAVER_MOTO_ARC_PROGRESS       = booleanPreferencesKey("ss_moto_arc_progress")
    val SCREENSAVER_MOTO_GLOW_LAYERS        = intPreferencesKey("ss_moto_glow_layers")
    // Extended Moto customisation
    val SCREENSAVER_MOTO_GLOW_INTENSITY     = floatPreferencesKey("ss_moto_glow_intensity")
    val SCREENSAVER_MOTO_GLOW_SHAPE         = stringPreferencesKey("ss_moto_glow_shape")       // "Circle"|"Oval"|"Wide Oval"
    val SCREENSAVER_MOTO_SECONDARY_GLOW     = booleanPreferencesKey("ss_moto_secondary_glow")
    val SCREENSAVER_MOTO_SECONDARY_COLOR    = stringPreferencesKey("ss_moto_secondary_color")
    val SCREENSAVER_MOTO_BG_VIGNETTE        = booleanPreferencesKey("ss_moto_bg_vignette")
    val SCREENSAVER_MOTO_BOLT_STYLE         = stringPreferencesKey("ss_moto_bolt_style")       // "Filled"|"Outline"|"None"
    val SCREENSAVER_MOTO_PCT_SUFFIX_STYLE   = stringPreferencesKey("ss_moto_pct_suffix_style") // "%"|"Percent"|"None"
    val SCREENSAVER_MOTO_SHOW_CHARGING_TEXT = booleanPreferencesKey("ss_moto_show_charging_text")
    val SCREENSAVER_MOTO_CHARGING_TEXT      = stringPreferencesKey("ss_moto_charging_text")
    val SCREENSAVER_MOTO_ARC_STROKE_WIDTH   = floatPreferencesKey("ss_moto_arc_stroke")
    val SCREENSAVER_MOTO_ANIMATION_STYLE    = stringPreferencesKey("ss_moto_anim_style")       // "Pulse"|"Breathe"|"Ripple"|"Static"
    val SCREENSAVER_MOTO_GLOW_OFFSET_Y      = floatPreferencesKey("ss_moto_glow_offset_y")    // 0.3..0.6 vertical position
    // ── Moto — new granular controls ────────────────────────────────────────
    val SCREENSAVER_MOTO_NUM_FONT_WEIGHT    = stringPreferencesKey("ss_moto_num_font_weight")  // "Thin"|"Light"|"Regular"|"Bold"
    val SCREENSAVER_MOTO_NUM_LETTER_SPC     = floatPreferencesKey("ss_moto_num_letter_spc")    // letter spacing for the number
    val SCREENSAVER_MOTO_ARC_GAP_MULT       = floatPreferencesKey("ss_moto_arc_gap_mult")      // gap between number bottom and arc top (0.0..1.0)
    val SCREENSAVER_MOTO_ARC_RADIUS_MULT    = floatPreferencesKey("ss_moto_arc_radius_mult")   // arc radius as multiple of screen width (0.1..0.6)
    val SCREENSAVER_MOTO_BOLT_SIZE_MULT     = floatPreferencesKey("ss_moto_bolt_size_mult")    // bolt size relative to pctSz
    val SCREENSAVER_MOTO_BOLT_OFFSET_Y      = floatPreferencesKey("ss_moto_bolt_offset_y")     // vertical offset of bolt below arc (0.0..1.0)
    val SCREENSAVER_MOTO_BRANDING_OFFSET_Y  = floatPreferencesKey("ss_moto_branding_offset_y") // branding Y as fraction of height (0.8..0.98)
    val SCREENSAVER_MOTO_NUM_COLOR_OPACITY  = floatPreferencesKey("ss_moto_num_opacity")       // opacity of the number 0..1
    val SCREENSAVER_MOTO_ARC_ANGLE_START    = floatPreferencesKey("ss_moto_arc_start")         // arc start angle in degrees
    val SCREENSAVER_MOTO_ARC_ANGLE_SWEEP    = floatPreferencesKey("ss_moto_arc_sweep")         // arc total sweep degrees
    val SCREENSAVER_MOTO_SUFFIX_SIZE_MULT   = floatPreferencesKey("ss_moto_suffix_sz")         // % symbol size relative to main number

    // ── Moto per-element X/Y position offsets (0.0..1.0, 0.5 = centred) ────
    val SCREENSAVER_MOTO_GLOW_OFFSET_X      = floatPreferencesKey("ss_moto_glow_offset_x")    // horizontal position of glow centre
    val SCREENSAVER_MOTO_NUM_OFFSET_X       = floatPreferencesKey("ss_moto_num_offset_x")     // horizontal position of number block
    val SCREENSAVER_MOTO_NUM_OFFSET_Y       = floatPreferencesKey("ss_moto_num_offset_y")     // vertical position of number block
    val SCREENSAVER_MOTO_ARC_OFFSET_X       = floatPreferencesKey("ss_moto_arc_offset_x")     // horizontal position of arc + bolt
    val SCREENSAVER_MOTO_ARC_OFFSET_Y_ADJ   = floatPreferencesKey("ss_moto_arc_offset_y_adj") // vertical adjustment for arc relative to number (-0.5..0.5)
    val SCREENSAVER_MOTO_BRANDING_OFFSET_X  = floatPreferencesKey("ss_moto_branding_offset_x")// horizontal position of branding text

    // ── Windows Phone Screen Saver ───────────────────────────────────────────
    val SCREENSAVER_WP_TEXT_COLOR           = stringPreferencesKey("ss_wp_text_color")
    val SCREENSAVER_WP_IS_24H               = booleanPreferencesKey("ss_wp_is_24h")
    val SCREENSAVER_WP_SHOW_DATE            = booleanPreferencesKey("ss_wp_show_date")
    val SCREENSAVER_WP_LAYOUT               = stringPreferencesKey("ss_wp_layout")
    val SCREENSAVER_WP_SHOW_WEATHER         = booleanPreferencesKey("ss_wp_show_weather")
    val SCREENSAVER_WP_CITY                 = stringPreferencesKey("ss_wp_city")
    val SCREENSAVER_WP_CONDITION            = stringPreferencesKey("ss_wp_condition")
    val SCREENSAVER_WP_TEMPERATURE          = stringPreferencesKey("ss_wp_temperature")
    val SCREENSAVER_WP_TEMP_HIGH            = stringPreferencesKey("ss_wp_temp_high")
    val SCREENSAVER_WP_TEMP_LOW             = stringPreferencesKey("ss_wp_temp_low")
    val SCREENSAVER_WP_DAY2_NAME            = stringPreferencesKey("ss_wp_day2_name")
    val SCREENSAVER_WP_DAY2_HIGH            = stringPreferencesKey("ss_wp_day2_high")
    val SCREENSAVER_WP_DAY2_LOW             = stringPreferencesKey("ss_wp_day2_low")
    val SCREENSAVER_WP_DAY3_NAME            = stringPreferencesKey("ss_wp_day3_name")
    val SCREENSAVER_WP_DAY3_HIGH            = stringPreferencesKey("ss_wp_day3_high")
    val SCREENSAVER_WP_DAY3_LOW             = stringPreferencesKey("ss_wp_day3_low")
    val SCREENSAVER_WP_SHOW_EVENTS          = booleanPreferencesKey("ss_wp_show_events")
    val SCREENSAVER_WP_EVENT_TITLE          = stringPreferencesKey("ss_wp_event_title")
    val SCREENSAVER_WP_EVENT_LOCATION       = stringPreferencesKey("ss_wp_event_location")
    val SCREENSAVER_WP_EVENT_TIME           = stringPreferencesKey("ss_wp_event_time")
    val SCREENSAVER_WP_SHOW_NOTIF           = booleanPreferencesKey("ss_wp_show_notif")
    val SCREENSAVER_WP_PHONE_COUNT          = intPreferencesKey("ss_wp_phone_count")
    val SCREENSAVER_WP_EMAIL_COUNT          = intPreferencesKey("ss_wp_email_count")
    val SCREENSAVER_WP_FONT_SIZE            = floatPreferencesKey("ss_wp_font_size")
    val SCREENSAVER_WP_CLOCK_WEIGHT         = stringPreferencesKey("ss_wp_clock_weight")
    val SCREENSAVER_WP_SHOW_ALARM_ICON      = booleanPreferencesKey("ss_wp_show_alarm_icon")
    val SCREENSAVER_WP_BG_COLOR             = stringPreferencesKey("ss_wp_bg_color")
    val SCREENSAVER_WP_LETTER_SPACING       = floatPreferencesKey("ss_wp_letter_spacing")
    // Extended WP customisation
    val SCREENSAVER_WP_SHOW_SECONDS         = booleanPreferencesKey("ss_wp_show_seconds")
    val SCREENSAVER_WP_TIME_COLOR           = stringPreferencesKey("ss_wp_time_color")         // separate color for just the clock digits
    val SCREENSAVER_WP_DATE_OPACITY         = floatPreferencesKey("ss_wp_date_opacity")        // 0.3..1.0
    val SCREENSAVER_WP_CLOCK_POSITION       = stringPreferencesKey("ss_wp_clock_position")     // "Left"|"Center"
    val SCREENSAVER_WP_SHOW_SEPARATOR       = booleanPreferencesKey("ss_wp_show_separator")    // horizontal rule between weather & clock
    val SCREENSAVER_WP_EVENT2_TITLE         = stringPreferencesKey("ss_wp_event2_title")
    val SCREENSAVER_WP_EVENT2_TIME          = stringPreferencesKey("ss_wp_event2_time")
    val SCREENSAVER_WP_COMPACT_MODE         = booleanPreferencesKey("ss_wp_compact_mode")      // tighter line spacing
    val SCREENSAVER_WP_ACCENT_COLOR         = stringPreferencesKey("ss_wp_accent_color")       // accent for event/notif tint
    val SCREENSAVER_WP_SHOW_WEEK_NUMBER     = booleanPreferencesKey("ss_wp_show_week_number")
    val SCREENSAVER_WP_NOTIF_STYLE          = stringPreferencesKey("ss_wp_notif_style")        // "Icons"|"Numbers"|"Both"
    val SCREENSAVER_WP_TEMP_UNIT            = stringPreferencesKey("ss_wp_temp_unit")          // "F"|"C"
    val SCREENSAVER_WP_SHOW_BATTERY         = booleanPreferencesKey("ss_wp_show_battery")
    val SCREENSAVER_WP_CLOCK_SIZE           = floatPreferencesKey("ss_wp_clock_size")          // independent clock text size multiplier
    val SCREENSAVER_WP_CLOCK_VERTICAL_POS  = floatPreferencesKey("ss_wp_clock_v_pos")
    val SCREENSAVER_WP_CLOCK_SIZE_SP       = floatPreferencesKey("ss_wp_clock_size_sp")
    val SCREENSAVER_WP_DATE_SIZE_SP        = floatPreferencesKey("ss_wp_date_size_sp")
    val SCREENSAVER_WP_WEATHER_SIZE_SP     = floatPreferencesKey("ss_wp_weather_size_sp")
    val SCREENSAVER_WP_NOTIF_SIZE_SP       = floatPreferencesKey("ss_wp_notif_size_sp")
    val SCREENSAVER_WP_PADDING_LEFT        = floatPreferencesKey("ss_wp_padding_left")

    val EQUALIZER_ENABLED            = booleanPreferencesKey("equalizer_enabled")
    val EQ_BAND_LEVELS               = stringPreferencesKey("eq_band_levels")
    val BASS_BOOST_ENABLED           = booleanPreferencesKey("bass_boost_enabled")
    val BASS_BOOST_STRENGTH          = intPreferencesKey("bass_boost_strength")
    val KEEP_SCREEN_ON_ENABLED       = booleanPreferencesKey("keep_screen_on_enabled")
    val KEEP_SCREEN_ON_TIMEOUT       = intPreferencesKey("keep_screen_on_timeout")
    val DOUBLE_TAP_BACK_ENABLED      = booleanPreferencesKey("double_tap_back_enabled")
    val DOUBLE_TAP_BACK_ACTION       = stringPreferencesKey("double_tap_back_action")
    val DOUBLE_TAP_BACK_APP          = stringPreferencesKey("double_tap_back_app")
    val DOUBLE_TAP_BACK_APP_NAME     = stringPreferencesKey("double_tap_back_app_name")
    val SLIDER_STYLE                 = stringPreferencesKey("slider_style")
    val CHARGING_SOUND_ENABLED       = booleanPreferencesKey("charging_sound_enabled")
    val CHARGING_SOUND_URI           = stringPreferencesKey("charging_sound_uri")

    // ── Charging Animation ───────────────────────────────────────────────────
    val CHARGING_ANIMATION_ENABLED   = booleanPreferencesKey("charging_animation_enabled")
    val CHARGING_ANIMATION_STYLE     = stringPreferencesKey("charging_animation_style")   // "lightning" | "ripple" | "pulse" | "fire"
    val CHARGING_ANIMATION_COLOR     = stringPreferencesKey("charging_animation_color")
    val CHARGING_ANIMATION_DURATION  = intPreferencesKey("charging_animation_duration")   // seconds, 0 = until unplugged
    val CHARGING_ANIMATION_SHOW_PCT  = booleanPreferencesKey("charging_animation_show_pct")
    val FLIP_DND_ENABLED             = booleanPreferencesKey("flip_dnd_enabled")
    val VOLUME_BOOST_ENABLED         = booleanPreferencesKey("volume_boost_enabled")
    val VOLUME_BOOST_LEVEL           = intPreferencesKey("volume_boost_level")
    val MUSIC_LEVELER_ENABLED        = booleanPreferencesKey("music_leveler_enabled")
    val MUSIC_LEVELER_COLOR          = stringPreferencesKey("music_leveler_color")
    val MUSIC_LEVELER_POSITION       = stringPreferencesKey("music_leveler_position")
    val SECURITY_MOTION_ENABLED      = booleanPreferencesKey("security_motion_enabled")
    val SECURITY_MOTION_SENSITIVITY  = floatPreferencesKey("security_motion_sensitivity")
    val CALL_VIBRATION_PATTERN       = stringPreferencesKey("call_vibration_pattern")
    val ALARM_VIBRATION_PATTERN      = stringPreferencesKey("alarm_vibration_pattern")
    val NOTIF_VIBRATION_PATTERN      = stringPreferencesKey("notif_vibration_pattern")
    val WALKIE_TALKIE_ENABLED        = booleanPreferencesKey("walkie_talkie_enabled")
    val COMPASS_CALIBRATED           = booleanPreferencesKey("compass_calibrated")
    val CHARGE_LIMIT_ENABLED         = booleanPreferencesKey("charge_limit_enabled")
    val CHARGE_LIMIT_PERCENT         = intPreferencesKey("charge_limit_percent")
    val APP_FREEZER_ENABLED          = booleanPreferencesKey("app_freezer_enabled")
    val APP_UPDATER_LAST_CHECK       = stringPreferencesKey("app_updater_last_check")
    val APP_UPDATER_AUTO_CHECK_MINS  = intPreferencesKey("app_updater_auto_check_mins")
    val POWER_SAVING_MAPS_ENABLED    = booleanPreferencesKey("power_saving_maps_enabled")
    val NAVBAR_X_POSITION            = floatPreferencesKey("navbar_x_position")
    val NAVBAR_Y_POSITION            = floatPreferencesKey("navbar_y_position")
    val FAKE_CALL_NAME               = stringPreferencesKey("fake_call_name")
    val FAKE_CALL_NUMBER             = stringPreferencesKey("fake_call_number")
    val FAKE_CALL_DELAY              = intPreferencesKey("fake_call_delay")
    val LOCK_WIDGETS_ENABLED         = booleanPreferencesKey("lock_widgets_enabled")
    val UI_BLUR_ENABLED              = booleanPreferencesKey("ui_blur_enabled")
    val EDGE_LIGHT_ENABLED           = booleanPreferencesKey("edge_light_enabled")
    val EDGE_LIGHT_COLOR             = stringPreferencesKey("edge_light_color")
    val FLASH_NOTIF_ENABLED          = booleanPreferencesKey("flash_notif_enabled")
    val MUSIC_LEVELER_HEIGHT         = floatPreferencesKey("music_leveler_height")
    val MUSIC_LEVELER_OPACITY        = floatPreferencesKey("music_leveler_opacity")
    val MUSIC_LEVELER_AUTO_HIDE      = booleanPreferencesKey("music_leveler_auto_hide")
    val CHARGE_RINGTONE_URI          = stringPreferencesKey("charge_ringtone_uri")
    val CHARGE_REPEAT_ENABLED        = booleanPreferencesKey("charge_repeat_enabled")
    val SECURITY_MOTION_ALARM_URI    = stringPreferencesKey("security_motion_alarm_uri")
    val SECURITY_MOTION_SOUND_ENABLED= booleanPreferencesKey("security_motion_sound_enabled")
    val CUSTOM_PRIMARY_COLOR         = stringPreferencesKey("custom_primary_color")
    val CUSTOM_SECONDARY_COLOR       = stringPreferencesKey("custom_secondary_color")
    val CALL_VIBRATION_ENABLED       = booleanPreferencesKey("call_vibration_enabled")
    val ALARM_VIBRATION_ENABLED      = booleanPreferencesKey("alarm_vibration_enabled")
    val NOTIF_VIBRATION_ENABLED      = booleanPreferencesKey("notif_vibration_enabled")
    val VOLUME_STYLES_ENABLED        = booleanPreferencesKey("volume_styles_enabled")
    val BG_WALLPAPER_URI             = stringPreferencesKey("bg_wallpaper_uri")
    val BG_DIM_AMOUNT                = floatPreferencesKey("bg_dim_amount")
    val BG_BLUR_ENABLED              = booleanPreferencesKey("bg_blur_enabled")
    val AUTO_UPDATE_ENABLED          = booleanPreferencesKey("auto_update_enabled")
    val AUTO_UPDATE_INTERVAL_HOURS   = intPreferencesKey("auto_update_interval_hours")
    val BG_BLUR_AMOUNT               = floatPreferencesKey("bg_blur_amount")
    val USE_DEVICE_WALLPAPER         = booleanPreferencesKey("use_device_wallpaper")
    val UI_BLUR_AMOUNT               = floatPreferencesKey("ui_blur_amount")
    val USE_EMOJI_ICONS              = booleanPreferencesKey("use_emoji_icons")
    val ICON_STYLE_SOLID             = booleanPreferencesKey("icon_style_solid")  // true=solid, false=translucent
    /** App-Freeze tab pills: true = solid surface, false = frosted-glass (semi-transparent + blur) */
    val FREEZE_TAB_PILL_SOLID        = booleanPreferencesKey("freeze_tab_pill_solid")
    /**
     * Home-screen category-pill style.
     * 0 = Match App UI  (uses app's surfaceContainerLow — fully opaque, cohesive)
     * 1 = Blur          (frosted-glass: semi-transparent + RenderEffect blur)
     * 2 = System        (Material3 default, may look transparent on some themes)
     */
    val HOME_PILL_STYLE              = intPreferencesKey("home_pill_style")
    /** Blur radius (dp) used when HOME_PILL_STYLE == 1. Range 4f..30f, default 16f. */
    val HOME_PILL_BLUR_INTENSITY     = floatPreferencesKey("home_pill_blur_intensity")
    val TERMINAL_THEME_INDEX         = intPreferencesKey("terminal_theme_index")  // 0-9 for 10 themes
    val TASK_UPDATE_INTERVAL         = intPreferencesKey("task_update_interval")

    // Onboarding
    val FIRST_LAUNCH_DONE            = booleanPreferencesKey("first_launch_done")

    // Notification Lighting — extended
    val NOTIF_FLASH_COUNT            = intPreferencesKey("notif_flash_count")
    val NOTIF_FLASH_SPEED_MS         = intPreferencesKey("notif_flash_speed_ms")
    val EDGE_LIGHT_THICKNESS         = floatPreferencesKey("edge_light_thickness")
    val EDGE_LIGHT_DURATION_MS       = intPreferencesKey("edge_light_duration_ms")
    val EDGE_LIGHT_STYLE             = stringPreferencesKey("edge_light_style")
    val EDGE_LIGHT_ALPHA             = floatPreferencesKey("edge_light_alpha")
    // Essentials-style edge lighting extended keys (stored in SharedPreferences for cross-process access)
    // These mirror keys used in everlasting_notif_prefs SharedPreferences by NotificationLightingHandler
    val EDGE_LIGHT_ONLY_SCREEN_OFF   = booleanPreferencesKey("edge_light_only_screen_off")
    val EDGE_LIGHT_PULSE_COUNT       = intPreferencesKey("edge_light_pulse_count")
    val EDGE_LIGHT_CORNER_RADIUS     = floatPreferencesKey("edge_light_corner_radius")
    val FLASH_PULSE_ENABLED          = booleanPreferencesKey("flash_pulse_enabled")

    // App Updater persistence
    val APP_UPDATER_SAVED_APPS       = stringPreferencesKey("app_updater_saved_apps")

    // Magnetic field in home
    val MAGNETIC_FIELD_IN_HOME       = booleanPreferencesKey("magnetic_field_in_home")

    // ── Custom Power Menu ────────────────────────────────────────────────────
    val POWER_MENU_ENABLED           = booleanPreferencesKey("power_menu_enabled")
    val POWER_MENU_STYLE             = stringPreferencesKey("power_menu_style")   // "container" | "fullscreen"
    val POWER_MENU_POSITION          = stringPreferencesKey("power_menu_position") // "center" | "near_power"
    val POWER_MENU_APP_SHORTCUTS     = stringPreferencesKey("power_menu_app_shortcuts") // JSON list
    val POWER_MENU_SHOW_PEOPLE       = booleanPreferencesKey("power_menu_show_people")
    val POWER_MENU_PEOPLE_JSON       = stringPreferencesKey("power_menu_people_json")

    // ── Screen-off button actions ────────────────────────────────────────────
    val SCREEN_OFF_ACTIONS_ENABLED    = booleanPreferencesKey("screen_off_actions_enabled")
    val SCREEN_OFF_POWER_LONG         = stringPreferencesKey("screen_off_power_long")   // "flashlight"|"dnd"|"app:|pkg"
    val SCREEN_OFF_VOL_UP_LONG        = stringPreferencesKey("screen_off_vol_up_long")
    val SCREEN_OFF_VOL_DOWN_LONG      = stringPreferencesKey("screen_off_vol_down_long")
    val SCREEN_OFF_POWER_LONG_APP     = stringPreferencesKey("screen_off_power_long_app")
    val SCREEN_OFF_VOL_UP_LONG_APP    = stringPreferencesKey("screen_off_vol_up_long_app")
    val SCREEN_OFF_VOL_DOWN_LONG_APP  = stringPreferencesKey("screen_off_vol_down_long_app")

    // ── Double power press ───────────────────────────────────────────────────
    val DOUBLE_POWER_ENABLED          = booleanPreferencesKey("double_power_enabled")
    val DOUBLE_POWER_ACTION           = stringPreferencesKey("double_power_action") // "flashlight"|"dnd"|"app"
    val DOUBLE_POWER_APP              = stringPreferencesKey("double_power_app")

    // ── Fake Power Off ──────────────────────────────────────────────────────
    val FAKE_POWER_OFF_ENABLED          = booleanPreferencesKey("fake_power_off_enabled")
    val FAKE_POWER_OFF_LOCK_DEVICE      = booleanPreferencesKey("fake_power_off_lock_device")
    val FAKE_POWER_OFF_DND              = booleanPreferencesKey("fake_power_off_dnd")
    val FAKE_POWER_OFF_DISMISS_SEQUENCE = stringPreferencesKey("fake_power_off_dismiss_sequence")

    // ── NavBar accessibility mode ────────────────────────────────────────────
    val NAVBAR_USE_ACCESSIBILITY      = booleanPreferencesKey("navbar_use_accessibility")


    // ── Home screen feature visibility ───────────────────────────────────────
    /** Comma-separated route strings of features hidden from the home screen. */
    val HIDDEN_FEATURE_ROUTES     = stringPreferencesKey("hidden_feature_routes")
    /** Toggle switch animation enabled app-wide. Default true. */
    val TOGGLE_ANIMATION_ENABLED  = booleanPreferencesKey("toggle_animation_enabled")

    // ── Welcome popup (shown once after first-launch swipe) ───────────────────
    val WELCOME_POPUP_SHOWN       = booleanPreferencesKey("welcome_popup_shown")

    // ── Screen Locked Security ────────────────────────────────────────────────
    val SCREEN_LOCKED_SECURITY_ENABLED = booleanPreferencesKey("screen_locked_security_enabled")

    fun <T> get(key: Preferences.Key<T>, default: T): Flow<T> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it[key] ?: default }

    suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    /** Clears every key — used by Reset All in Settings. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
