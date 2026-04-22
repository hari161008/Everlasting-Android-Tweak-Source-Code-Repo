package com.coolappstore.everlastingandroidtweak.utils

import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.coolappstore.everlastingandroidtweak.shizuku.ShizukuManager

object PermissionManager {

    fun hasOverlayPermission(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(context.packageName)
    }

    fun isSwiftSlateServiceEnabled(context: Context): Boolean {
        // SwiftSlate is now merged into EverlastingAccessibilityService —
        // only one service needs to be enabled for both to work.
        return isAccessibilityEnabled(context)
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return flat.contains(context.packageName)
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.canScheduleExactAlarms()
        } else true
    }

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasPostNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            hasPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
        else true

    fun hasRecordAudio(context: Context): Boolean =
        hasPermission(context, android.Manifest.permission.RECORD_AUDIO)

    fun hasCamera(context: Context): Boolean =
        hasPermission(context, android.Manifest.permission.CAMERA)

    fun hasReadMediaImages(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            hasPermission(context, android.Manifest.permission.READ_MEDIA_IMAGES)
        else hasPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE)

    /**
     * All permissions required for Notification Lighting + Flash Pulse to work correctly.
     * – SYSTEM_ALERT_WINDOW (overlay) is always needed
     * – Notification listener so we can detect incoming notifications
     * – Accessibility service for elevated overlay on Android 12+ lock screen
     */
    fun hasNotificationLightingPermissions(context: Context): Boolean {
        if (!hasOverlayPermission(context)) return false
        if (!isNotificationListenerEnabled(context)) return false
        // Accessibility is required on Android 12+ for lock-screen overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!isAccessibilityEnabled(context)) return false
        }
        return true
    }

    /**
     * Flashlight Pulse only needs notification listener access (no overlay).
     */
    fun hasFlashPulsePermissions(context: Context): Boolean =
        isNotificationListenerEnabled(context)

    // ── Open Settings helpers ────────────────────────────────────────────────

    fun openOverlaySettings(context: Context) = context.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun openAccessibilitySettings(context: Context) = context.startActivity(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun openNotificationListenerSettings(context: Context) = context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun openUsageAccessSettings(context: Context) = context.startActivity(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ── Shizuku ───────────────────────────────────────────────────────────────

    /** True when the Shizuku service is alive (binder connected). */
    fun isShizukuRunning(): Boolean = ShizukuManager.isRunning()

    /** True when permission was granted via the Shizuku dialog. */
    fun isShizukuGranted(): Boolean = ShizukuManager.hasPermission()

    /**
     * Shows the Shizuku grant-permission dialog.
     * Context is accepted for API symmetry; Shizuku handles the dialog itself.
     */
    @Suppress("UNUSED_PARAMETER")
    fun requestShizukuPermission(context: Context) = ShizukuManager.requestPermission()
}
