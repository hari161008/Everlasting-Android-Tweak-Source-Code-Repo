package com.coolappstore.everlastingandroidtweak.features.fakecall

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.coolappstore.everlastingandroidtweak.EverlastingApp

/**
 * Full-screen fake incoming call UI via WindowManager overlay.
 *
 * ROOT CAUSE FIX — FLAG_LAYOUT_NO_LIMITS:
 *   Without FLAG_LAYOUT_NO_LIMITS the overlay respected the status bar and
 *   navigation bar insets, leaving visible gaps at top and bottom.
 *   Adding this flag tells WindowManager to extend the view into those insets
 *   so the call screen genuinely covers the entire display edge-to-edge.
 *
 *   Combined flags in both incoming + active call params:
 *     FLAG_LAYOUT_NO_LIMITS   — extend into status/nav bar areas
 *     FLAG_LAYOUT_IN_SCREEN   — stay within screen (not cut off by notch logic)
 *     FLAG_SHOW_WHEN_LOCKED   — appear over the lock screen
 *     FLAG_TURN_SCREEN_ON     — wake the screen when the call arrives
 *     FLAG_KEEP_SCREEN_ON     — keep it lit during the call
 *     FLAG_NOT_FOCUSABLE      — allow touches to pass through to our buttons
 *                               (we handle clicks ourselves in the View)
 */
class FakeCallOverlayService : Service() {

    private var wm: WindowManager? = null
    private var overlayView: View? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        const val ACTION_STOP    = "action_stop_fake_call"
        const val EXTRA_NAME     = "caller_name"
        const val EXTRA_NUMBER   = "caller_number"
        const val EXTRA_AVATAR   = "caller_avatar"
        const val EXTRA_RINGTONE = "ringtone_type"
        const val NOTIF_ID       = 9001

        fun start(ctx: Context, name: String, number: String, avatar: String, ringtone: String) {
            if (!Settings.canDrawOverlays(ctx)) return
            ctx.startService(Intent(ctx, FakeCallOverlayService::class.java).apply {
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_NUMBER, number)
                putExtra(EXTRA_AVATAR, avatar)
                putExtra(EXTRA_RINGTONE, ringtone)
            })
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, FakeCallOverlayService::class.java).apply {
                action = ACTION_STOP
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cleanup(); stopSelf(); return START_NOT_STICKY
        }
        val name     = intent?.getStringExtra(EXTRA_NAME)     ?: "Unknown"
        val number   = intent?.getStringExtra(EXTRA_NUMBER)   ?: ""
        val avatar   = intent?.getStringExtra(EXTRA_AVATAR)   ?: "📞"
        val ringtone = intent?.getStringExtra(EXTRA_RINGTONE) ?: "Default"

        startForeground(NOTIF_ID, buildNotif(name, number))
        showCallScreen(name, number, avatar, ringtone)
        return START_NOT_STICKY
    }

    private fun buildNotif(name: String, number: String): Notification =
        NotificationCompat.Builder(this, EverlastingApp.CHANNEL_ALERTS)
            .setContentTitle("Incoming call from $name")
            .setContentText(number)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()

    // ─── Shared WindowManager params builder ─────────────────────────────────

    /**
     * ROOT CAUSE FIX: added FLAG_LAYOUT_NO_LIMITS so the overlay truly covers
     * the entire screen including status bar and navigation bar areas.
     */
    private fun buildFullscreenParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            // FLAG_LAYOUT_NO_LIMITS: extend view into status bar + nav bar insets
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE          or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN       or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS       or  // ← KEY FIX
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED       or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON         or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
    }

    // ─── Incoming call screen ─────────────────────────────────────────────────

    private fun showCallScreen(name: String, number: String, avatar: String, ringtone: String) {
        removeView()
        startRinging(ringtone)

        val view = buildCallView(name, number, avatar)
        overlayView = view

        try { wm?.addView(view, buildFullscreenParams()) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun buildCallView(name: String, number: String, avatar: String): View {
        val ctx = this
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        // ── Root — full gradient background ──────────────────────────────────
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Rich gradient: deep navy → dark blue-purple
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0D1B2A"), Color.parseColor("#1A1A2E"), Color.parseColor("#16213E"))
            )
            // Extra top padding for status bar area (FLAG_LAYOUT_NO_LIMITS means we own that space)
            val statusBarHeight = try {
                val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resId > 0) resources.getDimensionPixelSize(resId) else dp(24)
            } catch (_: Exception) { dp(24) }
            setPadding(dp(24), statusBarHeight + dp(16), dp(24), dp(48))
        }

        // ── Ripple animation dots (decorative) ───────────────────────────────
        val rippleContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8)
            )
        }
        root.addView(rippleContainer)

        // ── "Incoming Call" label ─────────────────────────────────────────────
        root.addView(TextView(ctx).apply {
            text = "Incoming Call"
            textSize = 13f
            letterSpacing = 0.12f
            setTextColor(Color.argb(180, 180, 200, 255))
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        root.addView(View(ctx), LinearLayout.LayoutParams(0, dp(32)))

        // ── Avatar circle ─────────────────────────────────────────────────────
        val avatarBg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            // Soft gradient ring
            colors = intArrayOf(Color.parseColor("#3D5A80"), Color.parseColor("#2D2D5E"))
        }
        root.addView(TextView(ctx).apply {
            text = avatar
            textSize = 52f
            gravity = Gravity.CENTER
            background = avatarBg
        }, LinearLayout.LayoutParams(dp(128), dp(128)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        root.addView(View(ctx), LinearLayout.LayoutParams(0, dp(24)))

        // ── Caller name ───────────────────────────────────────────────────────
        root.addView(TextView(ctx).apply {
            text = name
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.01f
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // ── Number ────────────────────────────────────────────────────────────
        if (number.isNotBlank()) {
            root.addView(View(ctx), LinearLayout.LayoutParams(0, dp(6)))
            root.addView(TextView(ctx).apply {
                text = number
                textSize = 16f
                setTextColor(Color.argb(180, 180, 200, 255))
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        // ── Spacer to push buttons down ───────────────────────────────────────
        root.addView(LinearLayout(ctx), LinearLayout.LayoutParams(0, 0, 1f))

        // ── Action buttons row ────────────────────────────────────────────────
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            weightSum = 2f
        }

        fun actionCol(emoji: String, label: String, bgColor: Int, onClick: () -> Unit): LinearLayout {
            val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bgColor) }
            val btn = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = bg
                val pad = dp(20); setPadding(pad, pad, pad, pad)
                setOnClickListener { onClick() }
                elevation = 8f
            }
            btn.addView(TextView(ctx).apply {
                text = emoji; textSize = 30f; gravity = Gravity.CENTER
            })
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            }
            col.addView(btn, LinearLayout.LayoutParams(dp(76), dp(76)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            })
            col.addView(View(ctx), LinearLayout.LayoutParams(0, dp(10)))
            col.addView(TextView(ctx).apply {
                text = label; textSize = 13f; setTextColor(Color.argb(200, 255, 255, 255)); gravity = Gravity.CENTER
            })
            return col
        }

        btnRow.addView(
            actionCol("✕", "Decline", Color.parseColor("#E53935")) {
                cleanup(); stopSelf()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        btnRow.addView(
            actionCol("📞", "Accept", Color.parseColor("#43A047")) {
                stopRinging()
                showActiveCallScreen(name, number)
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        root.addView(btnRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        return root
    }

    // ─── Active call screen ───────────────────────────────────────────────────

    private fun showActiveCallScreen(name: String, number: String) {
        removeView()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        var seconds = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0D1B2A"), Color.parseColor("#1A1A2E"))
            )
            val statusBarHeight = try {
                val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
                if (resId > 0) resources.getDimensionPixelSize(resId) else dp(24)
            } catch (_: Exception) { dp(24) }
            setPadding(dp(24), statusBarHeight + dp(32), dp(24), dp(48))
        }

        root.addView(TextView(this).apply {
            text = name; textSize = 28f; setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val timerView = TextView(this).apply {
            text = "00:00"; textSize = 18f
            setTextColor(Color.parseColor("#66BB6A")); gravity = Gravity.CENTER
        }
        root.addView(timerView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        root.addView(LinearLayout(this), LinearLayout.LayoutParams(0, 0, 1f))

        val endBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#E53935")) }
        val endBtn = LinearLayout(this).apply {
            gravity = Gravity.CENTER; background = endBg; elevation = 8f
            val pad = dp(20); setPadding(pad, pad, pad, pad)
            setOnClickListener { cleanup(); stopSelf() }
        }
        endBtn.addView(TextView(this).apply { text = "✕"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER })
        root.addView(View(this), LinearLayout.LayoutParams(0, dp(12)))
        root.addView(endBtn, LinearLayout.LayoutParams(dp(76), dp(76)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(TextView(this).apply {
            text = "End Call"; textSize = 13f; setTextColor(Color.argb(200, 255, 255, 255)); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        overlayView = root
        // Also use FLAG_LAYOUT_NO_LIMITS for active call screen
        try { wm?.addView(root, buildFullscreenParams()) } catch (e: Exception) { e.printStackTrace() }

        // Timer tick
        val timerRunnable = object : Runnable {
            override fun run() {
                seconds++
                timerView.text = "%02d:%02d".format(seconds / 60, seconds % 60)
                handler.postDelayed(this, 1000L)
            }
        }
        handler.postDelayed(timerRunnable, 1000L)
    }

    // ─── Ringing ──────────────────────────────────────────────────────────────

    private fun startRinging(ringtoneType: String) {
        try {
            if (ringtoneType == "Vibrate Only") {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
                return
            }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val vol = am.getStreamVolume(AudioManager.STREAM_RING).toFloat() /
                      am.getStreamMaxVolume(AudioManager.STREAM_RING)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(this@FakeCallOverlayService, uri)
                isLooping = true
                setVolume(vol, vol)
                prepare(); start()
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 1000), 0))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopRinging() {
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        vibrator?.cancel()
    }

    private fun removeView() {
        overlayView?.let { try { wm?.removeView(it) } catch (_: Exception) {} }
        overlayView = null
    }

    private fun cleanup() {
        stopRinging()
        handler.removeCallbacksAndMessages(null)
        removeView()
    }

    override fun onDestroy() { super.onDestroy(); cleanup() }
    override fun onBind(intent: Intent?): IBinder? = null
}
