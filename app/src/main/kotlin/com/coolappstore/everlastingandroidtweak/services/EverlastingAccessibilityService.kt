@file:Suppress("DEPRECATION")

package com.coolappstore.everlastingandroidtweak.services

import android.accessibilityservice.AccessibilityService
import tk.zwander.lockscreenwidgets.services.Accessibility
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.*
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.*
import androidx.core.view.isVisible
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import com.coolappstore.everlastingandroidtweak.features.fakepoweroff.FakePowerOffManager
import com.coolappstore.everlastingandroidtweak.features.security.ScreenLockedSecurityHandler
import com.coolappstore.everlastingandroidtweak.features.notiflight.FlashlightActionReceiver
import com.coolappstore.everlastingandroidtweak.features.notiflight.NotificationLightingHandler
import com.coolappstore.everlastingandroidtweak.features.notiflight.NotificationLightingService
import com.coolappstore.everlastingandroidtweak.utils.FlashlightUtil
import com.musheer360.swiftslate.api.GeminiClient
import com.musheer360.swiftslate.api.OpenAICompatibleClient
import com.musheer360.swiftslate.manager.CommandManager
import com.musheer360.swiftslate.manager.KeyManager
import com.musheer360.swiftslate.model.Command
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class EverlastingAccessibilityService : Accessibility() {

    private val vibrator: Vibrator by lazy {
        try {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } catch (_: Exception) {
            // Fallback — should not happen on API 31+ but guard defensively
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
    }
    private val cameraManager   by lazy { getSystemService(CAMERA_SERVICE) as CameraManager }
    private val audioManager    by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val telephonyManager by lazy { getSystemService(TELEPHONY_SERVICE) as TelephonyManager }
    private val windowManager   by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private var soundPool: SoundPool? = null
    private var tapSoundId: Int = 0
    private var tapSoundLoaded = false

    private val scope     = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Prefs cache ──────────────────────────────────────────────────────────
    private var hapticsEnabled         = false
    private var hapticsScrollEnabled   = false
    private var hapticsTapIntensity    = 150
    private var hapticsScrollIntensity = 80
    private var hapticsPattern         = "Click"
    private var tapSoundEnabled        = false
    private var tapSoundUri            = ""
    private var keepScreenOn           = false
    private var doubleTapBackEnabled   = false
    private var doubleTapBackAction    = "torch"
    private var doubleTapBackApp       = ""
    private var lockSoundEnabled       = false
    private var unlockSoundEnabled     = false
    private var lockSoundUri           = ""
    private var unlockSoundUri         = ""
    private var torchOn                = false

    // Vibration patterns — OFF by default until user enables them
    private var callVibEnabled   = false
    private var callVibPattern   = "Classic Ring"
    private var alarmVibEnabled  = false
    private var alarmVibPattern  = "Urgent"
    private var notifVibEnabled  = false
    private var notifVibPattern  = "Gentle Pulse"

    // Double-tap back
    private var lastBackTime     = 0L
    private val DOUBLE_TAP_MS    = 400L
    private var backDelayJob: Job? = null

    // Double power press (screen ON)
    private var doublePowerEnabled = false
    private var doublePowerAction  = "flashlight"
    private var doublePowerApp     = ""
    private var lastPowerTime      = 0L
    private val DOUBLE_POWER_MS    = 600L

    // Screen-off long press actions
    private var screenOffActionsEnabled  = false
    private var screenOffPowerLongAction = "flashlight"
    private var screenOffPowerLongApp    = ""
    private var screenOffVolUpLongAction = "none"
    private var screenOffVolUpLongApp    = ""
    private var screenOffVolDownLongAction = "none"
    private var screenOffVolDownLongApp  = ""
    private var isScreenOn               = true
    private var powerKeyDownTime         = 0L
    private var volUpKeyDownTime         = 0L
    private var volDownKeyDownTime       = 0L
    private val LONG_PRESS_MS            = 800L

    // Custom power menu
    private var powerMenuEnabled  = false
    private var powerMenuStyle    = "container"   // "container" | "fullscreen"
    private var powerMenuPosition = "center"      // "center" | "near_power"
    private var powerMenuView: View? = null

    // Fake Power Off
    private val fakePowerOffManager by lazy { FakePowerOffManager(this) }
    // Screen Locked Security
    private val screenLockedSecurityHandler by lazy { ScreenLockedSecurityHandler(this) }
    private var screenLockedSecurityEnabled = false
    // Notification Lighting Handler (elevated accessibility overlay)
    private val notificationLightingHandler by lazy { NotificationLightingHandler(this) }

    // Screen receiver
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    if (lockSoundEnabled) playLockSound(lockSoundUri)
                }
                Intent.ACTION_USER_PRESENT -> {
                    isScreenOn = true
                    if (unlockSoundEnabled) playLockSound(unlockSoundUri)
                    screenLockedSecurityHandler.restoreAnimationScale()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    notificationLightingHandler.onScreenOn()
                }
            }
        }
    }

    // ── Telephony callback for call vibration ────────────────────────────────
    // ROOT CAUSE FIX: Vibration patterns for calls were saved but never triggered.
    // We register a TelephonyCallback (API 31+) / PhoneStateListener to detect
    // CALL_STATE_RINGING and fire the user-selected vibration waveform.
    private var telephonyCallback: Any? = null  // TelephonyCallback on API 31+

    @Suppress("DEPRECATION")
    private fun registerCallVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        if (state == TelephonyManager.CALL_STATE_RINGING && callVibEnabled) {
                            applyVibrationPattern(callVibPattern)
                        } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                            vibrator.cancel()
                        }
                    }
                }
                telephonyCallback = cb
                telephonyManager.registerTelephonyCallback(mainExecutor, cb)
            } else {
                val listener = object : PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        if (state == TelephonyManager.CALL_STATE_RINGING && callVibEnabled) {
                            applyVibrationPattern(callVibPattern)
                        } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                            vibrator.cancel()
                        }
                    }
                }
                telephonyCallback = listener
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
        } catch (_: Exception) {
            // READ_PHONE_STATE not granted or telephony unavailable — call vibration disabled
            telephonyCallback = null
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterCallVibration() {
        try {
            telephonyCallback?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    telephonyManager.unregisterTelephonyCallback(it as TelephonyCallback)
                } else {
                    telephonyManager.listen(it as PhoneStateListener, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (_: Exception) {}
        telephonyCallback = null
    }

    // ── Vibration pattern table ──────────────────────────────────────────────
    private val vibPatterns = mapOf(
        "Short Tap"     to longArrayOf(0, 60),
        "Long Press"    to longArrayOf(0, 600),
        "Double Tap"    to longArrayOf(0, 80, 80, 80),
        "Triple Tap"    to longArrayOf(0, 70, 60, 70, 60, 70),
        "Classic Ring"  to longArrayOf(0, 300, 200, 300, 200, 300),
        "Heartbeat"     to longArrayOf(0, 80, 60, 120, 500),
        "Double Heart"  to longArrayOf(0, 80, 60, 120, 120, 80, 60, 120, 500),
        "SOS"           to longArrayOf(0,100,100,100,100,100,300,300,100,300,100,300,300,100,100,100,100,100),
        "Rapid Fire"    to longArrayOf(0, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40),
        "Slow Pulse"    to longArrayOf(0, 400, 400, 400, 400),
        "Escalating"    to longArrayOf(0, 50, 100, 100, 200, 150, 300),
        "Descending"    to longArrayOf(0, 300, 150, 200, 100, 100, 50),
        "Drumroll"      to longArrayOf(0,30,30,30,30,30,30,30,30,30,30,30,30,30,30,30,30),
        "Gentle Pulse"  to longArrayOf(0, 150, 300, 150),
        "Urgent"        to longArrayOf(0, 500, 100, 500),
        "Tick Tock"     to longArrayOf(0, 80, 920, 80, 920),
        "Knock Knock"   to longArrayOf(0, 200, 150, 200),
        "Buzz Buzz"     to longArrayOf(0, 120, 80, 120, 80, 120),
        "Wave"          to longArrayOf(0, 100, 50, 200, 50, 300, 50, 200, 50, 100),
        "Syncopated"    to longArrayOf(0, 100, 200, 50, 150, 200, 100),
        "Alarm Clock"   to longArrayOf(0,200,100,200,100,200,400,200,100,200,100,200),
        "Incoming Call" to longArrayOf(0,500,500,500,500,500,500),
        "Military"      to longArrayOf(0, 200, 150, 200, 150, 200),
        "Morse Hi"      to longArrayOf(0, 300, 100, 100),
        "Silent"        to null  // no-op
    )

    fun applyVibrationPattern(patternName: String, repeat: Boolean = false) {
        if (patternName == "Silent") { vibrator.cancel(); return }
        val waveform = vibPatterns[patternName] ?: return
        if (waveform.all { it == 0L }) { vibrator.cancel(); return }
        try {
            vibrator.cancel()
            val effect = VibrationEffect.createWaveform(waveform, if (repeat) 0 else -1)
            vibrator.vibrate(effect)
        } catch (_: Exception) {}
    }

    // ── Alarm vibration via notification category ────────────────────────────
    // Alarm vibration is triggered from AutoFreezeService (merged notification listener)
    // when it detects CATEGORY_ALARM. This function is called from there.
    fun triggerAlarmVibration() {
        if (alarmVibEnabled) applyVibrationPattern(alarmVibPattern, repeat = true)
    }

    fun triggerNotifVibration() {
        if (notifVibEnabled) applyVibrationPattern(notifVibPattern)
    }

    // ── Service lifecycle ─────────────────────────────────────────────────────
    override fun onServiceConnected() {
        super.onServiceConnected()  // Initialize LSW widgets
        instance = this

        // Do NOT override serviceInfo here — the XML config already sets all flags correctly.
        // Dynamically re-assigning serviceInfo with a bare AccessibilityServiceInfo() strips
        // feedbackType and causes undefined behaviour on some OEMs.

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        soundPool?.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0 && sampleId == tapSoundId) tapSoundLoaded = true
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // API 33+ requires RECEIVER_NOT_EXPORTED or RECEIVER_EXPORTED flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        // Telephony registration requires READ_PHONE_STATE — guard with try/catch
        // to prevent SecurityException from crashing the whole service
        registerCallVibration()
        observePreferences()

        // Init SwiftSlate AI text-replacement engine
        ssKeyManager = KeyManager(applicationContext)
        ssCommandManager = CommandManager(applicationContext)
        ssUpdateTriggers()
    }

    private fun observePreferences() {
        scope.launch { AppPreferences.get(AppPreferences.CUSTOM_HAPTICS_ENABLED, false).collect { hapticsEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.HAPTICS_SCROLL_ENABLED, false).collect { hapticsScrollEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.HAPTICS_TAP_INTENSITY, 150).collect { hapticsTapIntensity = it } }
        scope.launch { AppPreferences.get(AppPreferences.HAPTICS_SCROLL_INTENSITY, 80).collect { hapticsScrollIntensity = it } }
        scope.launch { AppPreferences.get(AppPreferences.HAPTICS_PATTERN, "Click").collect { hapticsPattern = it } }
        scope.launch { AppPreferences.get(AppPreferences.TAP_SOUND_ENABLED, false).collect { tapSoundEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.TAP_SOUND_URI, "").collect { uri -> tapSoundUri = uri; mainScope.launch { reloadTapSound(uri) } } }
        scope.launch { AppPreferences.get(AppPreferences.LOCK_SOUND_ENABLED, false).collect { lockSoundEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.UNLOCK_SOUND_ENABLED, false).collect { unlockSoundEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.LOCK_SOUND_URI, "").collect { lockSoundUri = it } }
        scope.launch { AppPreferences.get(AppPreferences.UNLOCK_SOUND_URI, "").collect { unlockSoundUri = it } }
        scope.launch { AppPreferences.get(AppPreferences.KEEP_SCREEN_ON_ENABLED, false).collect { keepScreenOn = it } }
        scope.launch { AppPreferences.get(AppPreferences.DOUBLE_TAP_BACK_ENABLED, false).collect { doubleTapBackEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.DOUBLE_TAP_BACK_ACTION, "torch").collect { doubleTapBackAction = it } }
        scope.launch { AppPreferences.get(AppPreferences.DOUBLE_TAP_BACK_APP, "").collect { doubleTapBackApp = it } }
        // ROOT CAUSE FIX: Vibration patterns were ON by default (true), meaning they fired
        // immediately on first launch without user enabling them. Changed defaults to false.
        scope.launch { AppPreferences.get(AppPreferences.CALL_VIBRATION_ENABLED, false).collect { callVibEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.CALL_VIBRATION_PATTERN, "Classic Ring").collect { callVibPattern = it } }
        scope.launch { AppPreferences.get(AppPreferences.ALARM_VIBRATION_ENABLED, false).collect { alarmVibEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.ALARM_VIBRATION_PATTERN, "Urgent").collect { alarmVibPattern = it } }
        scope.launch { AppPreferences.get(AppPreferences.NOTIF_VIBRATION_ENABLED, false).collect { notifVibEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.NOTIF_VIBRATION_PATTERN, "Gentle Pulse").collect { notifVibPattern = it } }
        // Double power
        scope.launch { AppPreferences.get(AppPreferences.DOUBLE_POWER_ENABLED, false).collect { doublePowerEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.DOUBLE_POWER_ACTION, "flashlight").collect { doublePowerAction = it } }
        scope.launch { AppPreferences.get(AppPreferences.DOUBLE_POWER_APP, "").collect { doublePowerApp = it } }
        // Screen-off actions
        scope.launch { AppPreferences.get(AppPreferences.SCREEN_OFF_ACTIONS_ENABLED, false).collect { screenOffActionsEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.SCREEN_OFF_POWER_LONG, "flashlight").collect { screenOffPowerLongAction = it } }
        scope.launch { AppPreferences.get(AppPreferences.SCREEN_OFF_POWER_LONG_APP, "").collect { screenOffPowerLongApp = it } }
        scope.launch { AppPreferences.get(AppPreferences.SCREEN_OFF_VOL_UP_LONG, "none").collect { screenOffVolUpLongAction = it } }
        scope.launch { AppPreferences.get(AppPreferences.SCREEN_OFF_VOL_UP_LONG_APP, "").collect { screenOffVolUpLongApp = it } }
        scope.launch { AppPreferences.get(AppPreferences.SCREEN_OFF_VOL_DOWN_LONG, "none").collect { screenOffVolDownLongAction = it } }
        scope.launch { AppPreferences.get(AppPreferences.SCREEN_OFF_VOL_DOWN_LONG_APP, "").collect { screenOffVolDownLongApp = it } }
        // Power menu
        scope.launch { AppPreferences.get(AppPreferences.POWER_MENU_ENABLED, false).collect { powerMenuEnabled = it } }
        scope.launch { AppPreferences.get(AppPreferences.POWER_MENU_STYLE, "container").collect { powerMenuStyle = it } }
        scope.launch { AppPreferences.get(AppPreferences.POWER_MENU_POSITION, "center").collect { powerMenuPosition = it } }
        // Fake Power Off prefs
        scope.launch { AppPreferences.get(AppPreferences.FAKE_POWER_OFF_ENABLED,          false).collect { fakePowerOffManager.enabled         = it } }
        scope.launch { AppPreferences.get(AppPreferences.FAKE_POWER_OFF_LOCK_DEVICE,      false).collect { fakePowerOffManager.lockDevice       = it } }
        scope.launch { AppPreferences.get(AppPreferences.FAKE_POWER_OFF_DND,              false).collect { fakePowerOffManager.dndEnabled       = it } }
        scope.launch { AppPreferences.get(AppPreferences.FAKE_POWER_OFF_DISMISS_SEQUENCE, "UUDD").collect { fakePowerOffManager.dismissSequence = it } }
        // Screen Locked Security
        scope.launch { AppPreferences.get(AppPreferences.SCREEN_LOCKED_SECURITY_ENABLED, false).collect { screenLockedSecurityEnabled = it } }
    }

    // ── Key events ────────────────────────────────────────────────────────────
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code   = event.keyCode
        val action = event.action

        // ── Fake Power Off dismiss sequence ──────────────────────────────────
        if (fakePowerOffManager.onKeyEvent(event)) return true

        // ── Double tap back ──────────────────────────────────────────────────
        if (doubleTapBackEnabled && code == KeyEvent.KEYCODE_BACK && action == KeyEvent.ACTION_UP) {
            val now = System.currentTimeMillis()
            if (now - lastBackTime < DOUBLE_TAP_MS) {
                lastBackTime = 0L; backDelayJob?.cancel(); backDelayJob = null
                handleDoubleTapBack(); return true
            }
            lastBackTime = now
            backDelayJob?.cancel()
            backDelayJob = mainScope.launch {
                delay(DOUBLE_TAP_MS)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            return true
        }

        // ── Double power press (screen ON) ────────────────────────────────────
        // ROOT CAUSE: Accessibility can intercept KEYCODE_POWER via FLAG_REQUEST_FILTER_KEY_EVENTS.
        // We use a 600ms window to detect a second press.
        if (doublePowerEnabled && code == KeyEvent.KEYCODE_POWER && action == KeyEvent.ACTION_UP && isScreenOn) {
            val now = System.currentTimeMillis()
            if (now - lastPowerTime < DOUBLE_POWER_MS) {
                lastPowerTime = 0L
                handleDoublePower()
                return true
            }
            lastPowerTime = now
            return false  // let first press pass through (screen off / lock normally)
        }

        // ── Screen-off long press actions ─────────────────────────────────────
        // ROOT CAUSE: When screen is off, normal tap is not accessible.
        // We track KEY_DOWN time and on KEY_UP, if elapsed > LONG_PRESS_MS, trigger action.
        // Works with screen off because accessibility service is still running.
        if (screenOffActionsEnabled && !isScreenOn) {
            when (code) {
                KeyEvent.KEYCODE_POWER -> {
                    when (action) {
                        KeyEvent.ACTION_DOWN -> powerKeyDownTime = System.currentTimeMillis()
                        KeyEvent.ACTION_UP -> {
                            val held = System.currentTimeMillis() - powerKeyDownTime
                            powerKeyDownTime = 0L
                            if (held >= LONG_PRESS_MS) {
                                executeScreenOffAction(screenOffPowerLongAction, screenOffPowerLongApp)
                                return true
                            }
                        }
                    }
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (screenOffVolUpLongAction == "none") return false
                    when (action) {
                        KeyEvent.ACTION_DOWN -> volUpKeyDownTime = System.currentTimeMillis()
                        KeyEvent.ACTION_UP -> {
                            val held = System.currentTimeMillis() - volUpKeyDownTime
                            volUpKeyDownTime = 0L
                            if (held >= LONG_PRESS_MS) {
                                executeScreenOffAction(screenOffVolUpLongAction, screenOffVolUpLongApp)
                                return true
                            }
                        }
                    }
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (screenOffVolDownLongAction == "none") return false
                    when (action) {
                        KeyEvent.ACTION_DOWN -> volDownKeyDownTime = System.currentTimeMillis()
                        KeyEvent.ACTION_UP -> {
                            val held = System.currentTimeMillis() - volDownKeyDownTime
                            volDownKeyDownTime = 0L
                            if (held >= LONG_PRESS_MS) {
                                executeScreenOffAction(screenOffVolDownLongAction, screenOffVolDownLongApp)
                                return true
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    private fun handleDoubleTapBack() {
        vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        when (doubleTapBackAction) {
            "torch"      -> toggleTorch()
            "app"        -> launchApp(doubleTapBackApp)
            "home"       -> performGlobalAction(GLOBAL_ACTION_HOME)
            "recents"    -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "screenshot" -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }
    }

    private fun handleDoublePower() {
        vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        when (doublePowerAction) {
            "flashlight" -> toggleTorch()
            "dnd"        -> toggleDnd()
            "app"        -> launchApp(doublePowerApp)
        }
    }

    private fun executeScreenOffAction(action: String, appPkg: String) {
        when (action) {
            "flashlight" -> toggleTorch()
            "dnd"        -> toggleDnd()
            "app"        -> launchApp(appPkg)
        }
    }

    // ── Custom Power Menu ─────────────────────────────────────────────────────
    // Triggered by long-pressing the power button when screen is ON.
    // We intercept via global action GLOBAL_ACTION_POWER_DIALOG — but since we can't
    // suppress the system power dialog, we show our overlay ON TOP of it.
    fun showCustomPowerMenu() {
        mainScope.launch { buildAndShowPowerMenu() }
    }

    private fun buildAndShowPowerMenu() {
        removePowerMenuOverlay()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val isFullscreen = powerMenuStyle == "fullscreen"
        val isNearPower  = powerMenuPosition == "near_power"

        // Root container
        val root = if (isFullscreen) {
            FrameLayout(this).apply {
                setBackgroundColor(Color.argb(160, 0, 0, 0))
                setOnClickListener { removePowerMenuOverlay() }
            }
        } else {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E1E2E"))
                    cornerRadius = dp(20).toFloat()
                }
                elevation = 16f
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }
        }

        val menuItems = listOf(
            Triple("⏻", "Power Off")  { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) },
            Triple("🔄", "Restart")   { performGlobalAction(GLOBAL_ACTION_POWER_DIALOG) },
            Triple("🔒", "Lock")      { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) },
            Triple("🛡️", "Lockdown")  { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) },
        )

        val container = if (isFullscreen) {
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1E1E2E"))
                    cornerRadius = dp(24).toFloat()
                }
                elevation = 16f
                setPadding(dp(12), dp(16), dp(12), dp(16))
            }
        } else root as LinearLayout

        menuItems.forEach { (emoji, label, action) ->
            val btn = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.argb(0, 0, 0, 0))
                    cornerRadius = dp(12).toFloat()
                }
                setOnClickListener { removePowerMenuOverlay(); action() }
            }
            btn.addView(TextView(this).apply { text = emoji; textSize = 22f; setTextColor(Color.WHITE) })
            btn.addView(View(this).also { it.layoutParams = LinearLayout.LayoutParams(dp(14), 0) })
            btn.addView(TextView(this).apply {
                text = label; textSize = 16f; setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            container.addView(btn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        if (isFullscreen) (root as FrameLayout).addView(container, FrameLayout.LayoutParams(dp(220), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        val type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        val wParams = WindowManager.LayoutParams(
            if (isFullscreen) WindowManager.LayoutParams.MATCH_PARENT else dp(240),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (isNearPower && !isFullscreen) Gravity.TOP or Gravity.END
                      else Gravity.CENTER
            if (isNearPower && !isFullscreen) { x = dp(16); y = dp(120) }
        }

        powerMenuView = root
        try { windowManager.addView(root, wParams) } catch (_: Exception) {}
    }

    private fun removePowerMenuOverlay() {
        powerMenuView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        powerMenuView = null
    }

    // ── Accessibility events ─────────────────────────────────────────────────
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        super.onAccessibilityEvent(event)  // LSW event handling
        // Screen Locked Security — intercept network tile interactions on keyguard
        screenLockedSecurityHandler.onAccessibilityEvent(event, screenLockedSecurityEnabled)
        // Route text-change events to SwiftSlate AI engine
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            ssHandleTextChanged(event)
            return
        }

        // Fake Power Off: intercept system power menu
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            fakePowerOffManager.onWindowStateChanged(event)
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (hapticsEnabled) performTapHaptic()
                if (tapSoundEnabled && tapSoundLoaded && tapSoundId != 0) {
                    soundPool?.play(tapSoundId, 1f, 1f, 1, 0, 1f)
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (hapticsScrollEnabled) performScrollHaptic()
            }
            else -> {}
        }
    }

    // ── Haptics ───────────────────────────────────────────────────────────────
    private fun performTapHaptic() {
        val amp = hapticsTapIntensity.coerceIn(1, 255)
        val effect = when (hapticsPattern) {
            "Tick"         -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            "Heavy Click"  -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            "Double Click" -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            "Soft"         -> VibrationEffect.createOneShot(8, (amp * 0.4f).toInt().coerceIn(1, 255))
            "Custom"       -> VibrationEffect.createOneShot(20, amp)
            else           -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        }
        vibrator.vibrate(effect)
    }

    private fun performScrollHaptic() {
        vibrator.vibrate(VibrationEffect.createOneShot(6, hapticsScrollIntensity.coerceIn(1, 255)))
    }

    // ── Torch ─────────────────────────────────────────────────────────────────
    private fun toggleTorch() {
        try {
            val id = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            cameraManager.setTorchMode(id, torchOn)
        } catch (_: Exception) {}
    }

    // ── DND ───────────────────────────────────────────────────────────────────
    private fun toggleDnd() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm?.isNotificationPolicyAccessGranted == true) {
                val current = nm.currentInterruptionFilter
                nm.setInterruptionFilter(
                    if (current == NotificationManager.INTERRUPTION_FILTER_ALL)
                        NotificationManager.INTERRUPTION_FILTER_NONE
                    else NotificationManager.INTERRUPTION_FILTER_ALL
                )
            }
        } catch (_: Exception) {}
    }

    // ── App launch ────────────────────────────────────────────────────────────
    private fun launchApp(packageName: String) {
        if (packageName.isBlank()) return
        try {
            startActivity(packageManager.getLaunchIntentForPackage(packageName)
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } ?: return)
        } catch (_: Exception) {}
    }

    // ── Sound ─────────────────────────────────────────────────────────────────
    private fun reloadTapSound(uri: String) {
        if (uri.isBlank()) { tapSoundId = 0; tapSoundLoaded = false; return }
        tapSoundLoaded = false; tapSoundId = 0
        try {
            val parsedUri = Uri.parse(uri)
            val sp = soundPool ?: return
            tapSoundId = if (parsedUri.scheme == "content") {
                val afd = contentResolver.openAssetFileDescriptor(parsedUri, "r") ?: return
                sp.load(afd.fileDescriptor, afd.startOffset, afd.length, 1).also { afd.close() }
            } else sp.load(parsedUri.path, 1)
        } catch (_: Exception) {}
    }

    private fun playLockSound(uri: String) {
        if (uri.isBlank()) return
        mainScope.launch {
            try {
                val parsedUri = Uri.parse(uri)
                val vol = (audioManager.getStreamVolume(AudioManager.STREAM_RING).toFloat() /
                           audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)).coerceIn(0f, 1f)
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    if (parsedUri.scheme == "content") setDataSource(applicationContext, parsedUri)
                    else setDataSource(uri)
                    setVolume(vol, vol); prepare()
                    setOnCompletionListener { release() }; start()
                }
            } catch (_: Exception) {}
        }
    }

    fun playSound(uri: String) = playLockSound(uri)

    // ═══════════════════════════════════════════════════════════════════════
    // SwiftSlate AI Text Replacement — ported from AssistantService
    // ═══════════════════════════════════════════════════════════════════════

    private fun ssDp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun ssUpdateTriggers() {
        ssTriggerLastChars = ssCommandManager.getCommands()
            .mapNotNull { it.trigger.lastOrNull() }.toSet()
        ssTriggerLastRefresh = System.currentTimeMillis()
    }

    private fun ssHandleTextChanged(event: AccessibilityEvent) {
        if (ssIsProcessing) return
        val source = event.source ?: return
        val text = source.text?.toString() ?: return
        if (text.isEmpty()) return

        if (System.currentTimeMillis() - ssTriggerLastRefresh > SS_TRIGGER_REFRESH_MS) {
            ssUpdateTriggers()
        }

        val lastChar = text[text.length - 1]
        if (!ssTriggerLastChars.contains(lastChar)) {
            if (!lastChar.isLetterOrDigit() || !text.contains("?translate:")) return
        }

        val command = ssCommandManager.findCommand(text) ?: return
        val cleanText = text.substring(0, text.length - command.trigger.length).trim()

        if (command.trigger == "?undo") {
            if (source.isPassword) return
            ssIsProcessing = true
            ssCurrentJob?.cancel()
            ssHandleUndo(source, cleanText)
            return
        }

        if (cleanText.isEmpty() || source.isPassword) return

        ssIsProcessing = true
        ssCurrentJob?.cancel()
        ssProcessCommand(source, cleanText, command)
    }

    private fun ssProcessCommand(source: AccessibilityNodeInfo, text: String, command: Command) {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val providerType = prefs.getString("provider_type", "gemini") ?: "gemini"
        val model: String
        val endpoint: String

        if (providerType == "custom") {
            model = prefs.getString("custom_model", "") ?: ""
            endpoint = prefs.getString("custom_endpoint", "") ?: ""
            if (model.isBlank() || endpoint.isBlank()) {
                ssScope.launch { ssShowToast("Custom provider not configured. Set endpoint and model in SwiftSlate Settings.") }
                ssIsProcessing = false
                return
            }
        } else {
            model = prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite"
            endpoint = ""
        }

        ssCurrentJob = ssScope.launch {
            val originalText = text
            var spinnerJob: Job? = null
            try {
                withTimeout(90_000) {
                    val maxAttempts = ssKeyManager.getKeys().size.coerceAtLeast(1)
                    var lastErrorMsg: String? = null
                    var succeeded = false

                    for (attempt in 0 until maxAttempts) {
                        val key = ssKeyManager.getNextKey() ?: break
                        if (spinnerJob == null) spinnerJob = ssStartInlineSpinner(source, originalText)

                        val result = if (providerType == "custom")
                            ssOpenAIClient.generate(command.prompt, text, key, model, SS_DEFAULT_TEMPERATURE, endpoint)
                        else
                            ssGeminiClient.generate(command.prompt, text, key, model, SS_DEFAULT_TEMPERATURE)

                        if (result.isSuccess) {
                            spinnerJob?.cancel(); spinnerJob = null
                            ssLastOriginalText = originalText
                            ssReplaceText(source, result.getOrThrow())
                            ssHaptic(HapticFeedbackConstants.CONFIRM)
                            succeeded = true
                            break
                        }

                        val msg = result.exceptionOrNull()?.message ?: ""
                        lastErrorMsg = msg
                        when {
                            msg.contains("rate limit", ignoreCase = true) -> {
                                val secs = Regex("retry after (\\d+)s").find(msg)?.groupValues?.get(1)?.toLongOrNull() ?: 60
                                ssKeyManager.reportRateLimit(key, secs)
                            }
                            msg.contains("Invalid API key", ignoreCase = true) ||
                            msg.contains("API key not valid", ignoreCase = true) ->
                                ssKeyManager.markInvalid(key)
                            else -> break
                        }
                    }

                    if (!succeeded) {
                        spinnerJob?.cancel(); spinnerJob = null
                        ssReplaceText(source, originalText)
                        ssHaptic(HapticFeedbackConstants.REJECT)
                        val msg = when {
                            lastErrorMsg != null -> "SwiftSlate: $lastErrorMsg"
                            ssKeyManager.getKeys().isEmpty() -> "SwiftSlate: No API keys configured"
                            else -> {
                                val wait = ssKeyManager.getShortestWaitTimeMs()
                                if (wait != null) "SwiftSlate: Rate limited. Try in ${((wait + 999) / 1000)}s"
                                else "SwiftSlate: All API keys invalid"
                            }
                        }
                        ssShowToast(msg)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                spinnerJob?.cancel()
                try { ssReplaceText(source, originalText) } catch (_: Exception) {}
                ssShowToast("SwiftSlate: Request timed out")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                spinnerJob?.cancel()
                try { ssReplaceText(source, originalText) } catch (_: Exception) {}
                ssShowToast("SwiftSlate: ${e.message}")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    spinnerJob?.cancel()
                    if (!ssHandler.postDelayed({ ssIsProcessing = false }, 500)) ssIsProcessing = false
                }
            }
        }
    }

    private fun ssHandleUndo(source: AccessibilityNodeInfo, currentText: String) {
        ssCurrentJob = ssScope.launch {
            try {
                val prev = ssLastOriginalText
                if (prev == null) {
                    ssReplaceText(source, currentText)
                    ssHaptic(HapticFeedbackConstants.REJECT)
                    ssShowToast("Nothing to undo")
                } else {
                    ssLastOriginalText = currentText
                    ssReplaceText(source, prev)
                    ssHaptic(HapticFeedbackConstants.CONFIRM)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ssShowToast("Could not undo")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (!ssHandler.postDelayed({ ssIsProcessing = false }, 500)) ssIsProcessing = false
                }
            }
        }
    }

    private suspend fun ssReplaceText(source: AccessibilityNodeInfo, newText: String) =
        withContext(Dispatchers.Main) {
            source.refresh()
            val bundle = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            val ok = source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
            if (!ok) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val oldClip = clipboard.primaryClip
                clipboard.setPrimaryClip(ClipData.newPlainText("SwiftSlate", newText))
                val selArgs = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, source.text?.length ?: 0)
                }
                source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
                source.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                ssHandler.postDelayed({ if (oldClip != null) clipboard.setPrimaryClip(oldClip) }, 500)
            }
        }

    private fun ssSetFieldText(source: AccessibilityNodeInfo, text: String) {
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    private fun ssStartInlineSpinner(source: AccessibilityNodeInfo, baseText: String): Job =
        ssScope.launch(Dispatchers.Main) {
            var i = 0
            while (isActive) {
                ssSetFieldText(source, "$baseText ${SS_SPINNER_FRAMES[i]}")
                i = (i + 1) % SS_SPINNER_FRAMES.size
                delay(200)
            }
        }

    @Suppress("DEPRECATION")
    private fun ssHaptic(feedbackType: Int) {
        ssHandler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    val vib = vm.defaultVibrator
                    when (feedbackType) {
                        HapticFeedbackConstants.CONFIRM -> vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                        HapticFeedbackConstants.REJECT  -> vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                    }
                } else {
                    val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    vib.vibrate(50)
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun ssShowToast(msg: String) = withContext(Dispatchers.Main) {
        ssDismissOverlayToast()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val tv = TextView(applicationContext).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(ssDp(24), ssDp(12), ssDp(24), ssDp(12))
            maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()
            background = GradientDrawable().apply {
                setColor(SS_TOAST_BG_COLOR)
                cornerRadius = ssDp(24).toFloat()
            }
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = ssDp(SS_TOAST_SLIDE_DP).toFloat()
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = ssDp(SS_TOAST_BOTTOM_DP)
            windowAnimations = 0
        }
        try {
            wm.addView(tv, params)
            ssCurrentOverlayToast = tv
            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(tv, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(tv, View.TRANSLATION_Y, ssDp(SS_TOAST_SLIDE_DP).toFloat(), 0f)
                )
                duration = SS_TOAST_ANIM_MS
                interpolator = DecelerateInterpolator()
                start()
                ssEnterAnimator = this
            }
            val r = Runnable { ssDismissOverlayToastAnimated() }
            ssDismissRunnable = r
            ssHandler.postDelayed(r, SS_TOAST_DURATION_MS)
        } catch (_: Exception) {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun ssDismissOverlayToast() {
        ssDismissRunnable?.let { ssHandler.removeCallbacks(it) }
        ssDismissRunnable = null
        ssEnterAnimator?.cancel(); ssEnterAnimator = null
        ssDismissAnimator?.cancel(); ssDismissAnimator = null
        ssCurrentOverlayToast?.let { v ->
            try { v.visibility = View.GONE; (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {}
            ssCurrentOverlayToast = null
        }
    }

    private fun ssDismissOverlayToastAnimated() {
        ssDismissRunnable?.let { ssHandler.removeCallbacks(it) }
        ssDismissRunnable = null
        ssEnterAnimator?.cancel(); ssEnterAnimator = null
        ssDismissAnimator?.cancel()
        ssCurrentOverlayToast?.let { v ->
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                ssDismissAnimator = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(v, View.ALPHA, v.alpha, 0f),
                        ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, v.translationY, ssDp(SS_TOAST_SLIDE_DP).toFloat())
                    )
                    duration = SS_TOAST_ANIM_MS
                    interpolator = DecelerateInterpolator()
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            v.visibility = View.GONE
                            try { wm.removeView(v) } catch (_: Exception) {}
                            ssDismissAnimator = null
                        }
                    })
                    start()
                }
            } catch (_: Exception) {}
            ssCurrentOverlayToast = null
        }
    }

    override fun onInterrupt() {
        super.onInterrupt()  // LSW cleanup
        ssIsProcessing = false
        ssCurrentJob?.cancel()
    }

    // Route intents from NotificationLightingService / FlashlightActionReceiver
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW_NOTIFICATION_LIGHTING" -> {
                mainScope.launch { notificationLightingHandler.handleIntent(intent) }
            }
            FlashlightActionReceiver.ACTION_PULSE_NOTIFICATION -> {
                scope.launch {
                    val count   = intent.getIntExtra(FlashlightActionReceiver.EXTRA_FLASH_COUNT, 3)
                    val speedMs = intent.getIntExtra(FlashlightActionReceiver.EXTRA_FLASH_SPEED, 150)
                    val camId   = FlashlightUtil.getTorchCameraId(this@EverlastingAccessibilityService) ?: return@launch
                    try { FlashlightUtil.pulseFlashlight(this@EverlastingAccessibilityService, camId, count, speedMs.toLong()) }
                    catch (_: Exception) {}
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        unregisterCallVibration()
        removePowerMenuOverlay()
        fakePowerOffManager.destroy()
        notificationLightingHandler.removeOverlay()
        scope.cancel(); mainScope.cancel()
        // SwiftSlate cleanup
        ssDismissOverlayToast()
        ssServiceJob.cancel()
        ssCurrentJob?.cancel()
        soundPool?.release(); soundPool = null
        super.onDestroy()  // LSW cleanup
    }

    // ── SwiftSlate AI text replacement ────────────────────────────────────────
    private lateinit var ssKeyManager: KeyManager
    private lateinit var ssCommandManager: CommandManager
    private val ssGeminiClient = GeminiClient()
    private val ssOpenAIClient = OpenAICompatibleClient()
    private val ssServiceJob = SupervisorJob()
    private val ssScope = CoroutineScope(ssServiceJob + Dispatchers.IO)
    private val ssHandler = Handler(Looper.getMainLooper())
    @Volatile private var ssIsProcessing = false
    private var ssCurrentJob: Job? = null
    @Volatile private var ssLastOriginalText: String? = null
    private var ssTriggerLastChars = setOf<Char>()
    private var ssTriggerLastRefresh = 0L
    private var ssCurrentOverlayToast: View? = null
    private var ssDismissRunnable: Runnable? = null
    private var ssDismissAnimator: AnimatorSet? = null
    private var ssEnterAnimator: AnimatorSet? = null

    companion object {
        var instance: EverlastingAccessibilityService? = null
        private const val SS_TRIGGER_REFRESH_MS = 5_000L
        private const val SS_DEFAULT_TEMPERATURE = 0.5
        private val SS_SPINNER_FRAMES = arrayOf("◐", "◓", "◑", "◒")
        private const val SS_TOAST_BG_COLOR = 0xE6323232.toInt()
        private const val SS_TOAST_DURATION_MS = 3500L
        private const val SS_TOAST_BOTTOM_DP = 64
        private const val SS_TOAST_ANIM_MS = 300L
        private const val SS_TOAST_SLIDE_DP = 40
    }
}
