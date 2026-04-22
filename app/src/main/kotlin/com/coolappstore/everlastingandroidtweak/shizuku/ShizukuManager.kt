package com.coolappstore.everlastingandroidtweak.shizuku

import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * ShizukuManager
 *
 * Central object that owns Shizuku's lifecycle listeners and provides
 * a single source-of-truth API for the rest of the app.
 *
 * Call [initialize] once in Application.onCreate().
 * Call [destroy]     once in Application.onTerminate() (optional — process death handles it).
 *
 * All public helpers are safe to call even when Shizuku is not running
 * — they return false / no-op instead of throwing.
 */
object ShizukuManager {

    private const val TAG = "ShizukuManager"

    /** Request code used when asking for Shizuku API permission. */
    const val PERMISSION_REQUEST_CODE = 9_999

    // ── Lifecycle listeners ───────────────────────────────────────────────────

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.i(TAG, "Shizuku binder received — service is running")
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder dead — service stopped or killed")
    }

    /**
     * Global permission-result listener.  Feature-specific callbacks can be
     * added / removed independently via Shizuku.addRequestPermissionResultListener.
     */
    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            Log.i(TAG, "Shizuku permission result [code=$requestCode]: granted=$granted")
        }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Register all Shizuku listeners.  Must be called in Application.onCreate()
     * before any feature that uses Shizuku is started.
     *
     * [addBinderReceivedListenerSticky] replays the callback immediately if the
     * binder is already alive, so features that check [isReady] right after init
     * will see the correct state.
     */
    fun initialize() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        Log.d(TAG, "ShizukuManager initialized")
    }

    /**
     * Unregister all listeners.  Call in Application.onTerminate() if needed.
     * In practice Android kills the process, so this is mostly for hygiene.
     */
    fun destroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    // ── State queries ─────────────────────────────────────────────────────────

    /**
     * Returns true if the Shizuku service process is alive (binder is connected).
     * Does NOT indicate whether API permission has been granted.
     */
    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) { false }

    /**
     * Returns true if the app has been granted the Shizuku API permission
     * (the user tapped "Allow" in the Shizuku dialog).
     */
    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    /**
     * Convenience: returns true only when Shizuku is running AND permission is granted.
     * This is the gate used by all shell-execution helpers.
     */
    fun isReady(): Boolean = isRunning() && hasPermission()

    // ── Permission request ────────────────────────────────────────────────────

    /**
     * Show the Shizuku permission dialog.
     *
     * No-op if Shizuku is not running or permission is already granted.
     * The result is delivered to [requestPermissionResultListener] above and
     * to any listener registered via Shizuku.addRequestPermissionResultListener.
     */
    fun requestPermission() {
        try {
            if (!isRunning()) {
                Log.w(TAG, "requestPermission: Shizuku is not running — cannot show dialog")
                return
            }
            if (hasPermission()) {
                Log.d(TAG, "requestPermission: already granted, nothing to do")
                return
            }
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "requestPermission failed: ${e.message}")
        }
    }
}
