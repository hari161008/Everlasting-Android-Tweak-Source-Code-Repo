@file:Suppress("DEPRECATION")

package com.coolappstore.everlastingandroidtweak.features.fakepoweroff

import android.accessibilityservice.AccessibilityService
import android.app.Dialog
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageButton
import com.coolappstore.everlastingandroidtweak.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FakePowerOffManager — faithful port of the FakePowerOff open-source project.
 *
 * Detects the system power menu via keyword scan on TYPE_WINDOW_STATE_CHANGED events,
 * dismisses it, shows a look-alike dialog, then on any button tap shows the black
 * "Shutting down…" overlay. A configurable volume-key sequence (default UUDD) dismisses
 * the overlay. Supports lock-device and DND-on-fake-off options.
 */
class FakePowerOffManager(private val service: AccessibilityService) {

    // ── Settings (set by the accessibility service from DataStore flows) ──────
    var enabled         = false
    var lockDevice      = false
    var dndEnabled      = false
    var dismissSequence = "UUDD"   // volume-key sequence to hide the overlay

    private val SYSTEM_UI_PACKAGE = "com.android.systemui"

    private val detectKeywords = "power off, restart, emergency, poweroff, shutdown, shut down"
        .split(',').map { it.trim() }

    // ── Overlay state ─────────────────────────────────────────────────────────
    private var powerMenuOpen = false
    private var overlayView: View? = null

    // ── Volume-key dismiss sequence state ─────────────────────────────────────
    private var triggerState      = 0
    private var triggerResetJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────────

    fun onWindowStateChanged(event: AccessibilityEvent) {
        if (!enabled) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != SYSTEM_UI_PACKAGE) return
        if (powerMenuOpen) return

        try {
            val root = event.source ?: return
            val found = containsPowerKeyword(root)
            root.recycle()
            if (!found) return

            powerMenuOpen = true
            CoroutineScope(Dispatchers.Main).launch {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                showFakePowerMenu()
            }
        } catch (_: Exception) {}
    }

    /**
     * Call this from AccessibilityService.onKeyEvent. Returns true if the event
     * was consumed (it advanced the dismiss sequence and the sequence is complete,
     * or it was a mis-press while the overlay is visible). Returning true here
     * prevents the key from reaching the system, which we only want when the
     * overlay is actually showing.
     */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (overlayView == null) return false
        if (event.action != KeyEvent.ACTION_UP) return false
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        val seq = dismissSequence.uppercase()
        if (seq.isEmpty()) return false

        val pressed = if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) 'U' else 'D'

        if (triggerState < seq.length && seq[triggerState] == pressed) {
            triggerState++
            if (triggerState == seq.length) {
                triggerState = 0
                triggerResetJob?.cancel()
                hideOverlay()
            } else {
                resetTriggerAfterDelay()
            }
        } else {
            triggerState = 0
            resetTriggerAfterDelay()
        }

        return true   // consume the key while overlay is up
    }

    fun destroy() {
        hideOverlay()
    }

    // ── Keyword scan ──────────────────────────────────────────────────────────

    private fun containsPowerKeyword(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.tooltipText?.toString(),
                node.hintText?.toString()
            ).forEach { text ->
                if (detectKeywords.any { text.contains(it, ignoreCase = true) }) return true
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    // ── Fake power menu dialog ────────────────────────────────────────────────

    private fun showFakePowerMenu() {
        val dialog = Dialog(service)
        dialog.setContentView(R.layout.fpo_power_off_menu)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        dialog.setOnDismissListener { powerMenuOpen = false }

        dialog.findViewById<ImageButton>(R.id.fpo_btn_power_off).setOnClickListener {
            dialog.dismiss(); beginShutdownSequence()
        }
        dialog.findViewById<ImageButton>(R.id.fpo_btn_restart).setOnClickListener {
            dialog.dismiss(); beginShutdownSequence()
        }
        dialog.findViewById<ImageButton>(R.id.fpo_btn_emergency).setOnClickListener {
            dialog.dismiss(); beginShutdownSequence()
        }
        dialog.show()
    }

    // ── Shutdown sequence ─────────────────────────────────────────────────────

    private fun beginShutdownSequence() {
        showShutdownOverlay()

        // DND can be set immediately — it doesn't affect the screen
        if (dndEnabled) {
            try {
                val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
            } catch (_: Exception) {}
        }

        CoroutineScope(Dispatchers.Main).launch {
            delay(3000L)
            vibrateDevice()
            delay(2000L)
            overlayView?.findViewById<View>(R.id.fpo_shutdown_view)?.visibility = View.INVISIBLE
            // Lock AFTER the full animation has played so the screen stays on during it
            if (lockDevice) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        }
    }

    // ── Overlay ───────────────────────────────────────────────────────────────

    private fun showShutdownOverlay() {
        if (overlayView != null) return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = View.inflate(service, R.layout.fpo_shutdown_overlay, null)
        overlayView = view

        val flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_FULLSCREEN
                or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags, -1
        ).apply {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try { wm.addView(view, params) } catch (_: Exception) { overlayView = null; return }
        makeImmersive(view)

        view.findViewById<View>(R.id.fpo_countdown_text).visibility = View.GONE
        view.findViewById<View>(R.id.fpo_input_text).visibility     = View.GONE

        triggerState = 0   // reset dismiss sequence whenever a new overlay appears
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                (service.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun makeImmersive(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.windowInsetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            view.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    // ── Vibration ─────────────────────────────────────────────────────────────

    private fun vibrateDevice() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = service.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(700L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val v = service.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (v.hasVibrator()) v.vibrate(
                    VibrationEffect.createOneShot(700L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {}
    }

    // ── Dismiss sequence reset ─────────────────────────────────────────────────

    private fun resetTriggerAfterDelay() {
        triggerResetJob?.cancel()
        triggerResetJob = CoroutineScope(Dispatchers.Main).launch {
            delay(1000L)
            if (triggerState > 0) triggerState = 0
        }
    }
}
