package com.coolappstore.everlastingandroidtweak.features.keepon

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.coolappstore.everlastingandroidtweak.data.AppPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@RequiresApi(Build.VERSION_CODES.N)
class KeepScreenOnTileService : TileService() {

    companion object {
        val TIMEOUT_OPTIONS = listOf(-1, 5, 15, 30, 60, 120)
        val TIMEOUT_LABELS  = listOf("∞ Infinite", "5 min", "15 min", "30 min", "1 hour", "2 hours")

        private var wakeLock: PowerManager.WakeLock? = null
        private var autoDisableJob: Job? = null
        private val globalScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        fun acquireWakeLock(context: Context) {
            releaseWakeLock()
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "EverlastingTweak:KeepScreenOn"
            ).apply { acquire(24 * 60 * 60 * 1000L) }
        }

        fun releaseWakeLock() {
            autoDisableJob?.cancel()
            autoDisableJob = null
            try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
            wakeLock = null
        }

        // Persist enabled state without needing a live tile instance
        fun disableGlobally(context: Context) {
            releaseWakeLock()
            globalScope.launch {
                AppPreferences.set(AppPreferences.KEEP_SCREEN_ON_ENABLED, false)
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        globalScope.launch {
            val enabled = AppPreferences.get(AppPreferences.KEEP_SCREEN_ON_ENABLED, false).first()
            val timeout = AppPreferences.get(AppPreferences.KEEP_SCREEN_ON_TIMEOUT, -1).first()
            updateTile(enabled, timeout)
        }
    }

    override fun onClick() {
        super.onClick()
        globalScope.launch {
            val current = AppPreferences.get(AppPreferences.KEEP_SCREEN_ON_ENABLED, false).first()
            if (!current) {
                showTimeoutDialog()
            } else {
                doDisable()
            }
        }
    }

    private fun showTimeoutDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Keep Screen On — Duration")
            .setItems(TIMEOUT_LABELS.toTypedArray()) { _, which ->
                val minutes = TIMEOUT_OPTIONS[which]
                globalScope.launch { doEnable(minutes) }
            }
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        showDialog(dialog)
    }

    private suspend fun doEnable(timeoutMinutes: Int) {
        AppPreferences.set(AppPreferences.KEEP_SCREEN_ON_ENABLED, true)
        AppPreferences.set(AppPreferences.KEEP_SCREEN_ON_TIMEOUT, timeoutMinutes)
        acquireWakeLock(applicationContext)
        updateTile(enabled = true, timeout = timeoutMinutes)

        // Schedule auto-disable
        autoDisableJob?.cancel()
        if (timeoutMinutes > 0) {
            autoDisableJob = globalScope.launch {
                delay(timeoutMinutes * 60 * 1000L)
                doDisable()
            }
        }
    }

    private suspend fun doDisable() {
        AppPreferences.set(AppPreferences.KEEP_SCREEN_ON_ENABLED, false)
        releaseWakeLock()
        updateTile(enabled = false, timeout = -1)
    }

    private fun updateTile(enabled: Boolean, timeout: Int) {
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Keep Screen On"
            subtitle = when {
                !enabled     -> "Tap to enable"
                timeout <= 0 -> "Active — ∞"
                timeout < 60 -> "Active — ${timeout}m"
                else         -> "Active — ${timeout / 60}h"
            }
            icon = Icon.createWithBitmap(createBulbBitmap(enabled))
            updateTile()
        }
    }

    private fun createBulbBitmap(lit: Boolean): Bitmap {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = if (lit) Color.parseColor("#FFD700") else Color.parseColor("#AAAAAA")
        canvas.drawCircle(size / 2f, size * 0.40f, size * 0.28f, paint)

        paint.color = if (lit) Color.parseColor("#C8A800") else Color.parseColor("#888888")
        val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paint.color }
        canvas.drawRect(size * 0.35f, size * 0.62f, size * 0.65f, size * 0.80f, rectPaint)

        paint.color = Color.parseColor("#555555")
        paint.strokeWidth = size * 0.04f
        paint.style = Paint.Style.STROKE
        for (i in 0..2) {
            val y = size * (0.64f + i * 0.06f)
            canvas.drawLine(size * 0.35f, y, size * 0.65f, y, paint)
        }

        if (lit) {
            paint.color = Color.parseColor("#80FFD700")
            paint.strokeWidth = size * 0.03f
            paint.style = Paint.Style.STROKE
            val cx = size / 2f; val cy = size * 0.40f
            listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f).forEach { angle ->
                val rad = Math.toRadians(angle.toDouble())
                canvas.drawLine(
                    cx + Math.cos(rad).toFloat() * size * 0.32f,
                    cy + Math.sin(rad).toFloat() * size * 0.32f,
                    cx + Math.cos(rad).toFloat() * size * 0.42f,
                    cy + Math.sin(rad).toFloat() * size * 0.42f,
                    paint
                )
            }
        }
        return bmp
    }

    override fun onStopListening() {
        super.onStopListening()
        // Do NOT cancel globalScope here — it's shared and needed for auto-disable timer
    }
}
