package com.aistra.hail.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.app.Notification
import android.app.NotificationManager
import android.os.BadParcelableException
import android.os.DeadObjectException
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.coolappstore.everlastingandroidtweak.R
import com.aistra.hail.app.HailApi
import com.aistra.hail.app.HailData
import com.aistra.hail.receiver.ScreenOffReceiver
import com.coolappstore.everlastingandroidtweak.features.notiflight.NotifLightManager
import com.coolappstore.everlastingandroidtweak.services.EverlastingAccessibilityService
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import tk.zwander.common.util.BugsnagUtils
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.globalState
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.stringify

class AutoFreezeService : NotificationListenerService(), EventObserver {
    private val channelID = javaClass.simpleName
    private val lockReceiver by lazy { ScreenOffReceiver() }
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    // ── Everlasting notification features (merged from EverlastingNotificationListener) ──
    private var notifLightManager: NotifLightManager? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isListening = atomic(false)
    private val updateJob = atomic<Job?>(null)

    companion object {
        lateinit var instance: AutoFreezeService private set

        val isInstanceInitialized: Boolean
            get() = ::instance.isInitialized

        const val MAPS_PACKAGE   = "com.google.android.apps.maps"
        const val MAPS_PREFS     = "everlasting_maps_prefs"
        const val KEY_DISCOVERED = "maps_discovered_channels"
        const val KEY_DETECTION  = "maps_detection_channels"
        val DEFAULT_NAV_CHANNELS = setOf(
            "navigation_notification_channel",
            "primary_navigation_channel_v1",
            "primary_navigation_channel_v2"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val freezeAuto = PendingIntent.getActivity(
            applicationContext, 0, Intent(HailApi.ACTION_FREEZE_AUTO), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelID)
            .setContentTitle(getString(R.string.auto_freeze_notification_title))
            .setSmallIcon(R.drawable.ic_round_frozen)
            .addAction(R.drawable.ic_round_frozen, getString(R.string.auto_freeze), freezeAuto)
        if (HailData.checkedList.any { it.whitelisted }) {
            val freezeNonWhitelisted = PendingIntent.getActivity(
                applicationContext, 0,
                Intent(HailApi.ACTION_FREEZE_NON_WHITELISTED),
                PendingIntent.FLAG_IMMUTABLE
            )
            notification.addAction(
                R.drawable.ic_round_frozen,
                getString(R.string.action_freeze_non_whitelisted),
                freezeNonWhitelisted
            )
        }
        startForeground(100, notification.build())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.auto_freeze)
        val importance = NotificationManagerCompat.IMPORTANCE_LOW
        val channel = NotificationChannelCompat.Builder(channelID, importance).setName(name).build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Guard camera/hardware access — getCameraIdList() can throw CameraAccessException on
        // some OEMs when CAMERA permission is not yet granted at the time the notification
        // listener is first bound by the system.
        try {
            notifLightManager = NotifLightManager(this)
        } catch (_: Exception) {}
        // ACTION_SCREEN_OFF is a protected broadcast; specify RECEIVER_NOT_EXPORTED on API 33+.
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(lockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(lockReceiver, filter)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isListening.value = true
        handler.post { sendNotificationCountUpdate() }
        eventManager.addObserver(this)
        try {
            activeNotifications?.forEach { sbn ->
                if (sbn.packageName == MAPS_PACKAGE) {
                    com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsState
                        .hasNavigationNotification = isNavigationNotification(sbn)
                    discoverMapsChannel(sbn.notification?.channelId)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isListening.value = false
        eventManager.removeObserver(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS_PACKAGE) {
            discoverMapsChannel(sbn.notification?.channelId)
            com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsState
                .hasNavigationNotification = isNavigationNotification(sbn)
        }
        notifLightManager?.onNotificationPosted(sbn)
        scope.launch {
            val svc = EverlastingAccessibilityService.instance ?: return@launch
            if (sbn.notification?.category == android.app.Notification.CATEGORY_ALARM) {
                svc.triggerAlarmVibration()
            } else {
                svc.triggerNotifVibration()
            }
        }
        sendNotificationCountUpdate()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS_PACKAGE) {
            val stillNavigating = try {
                activeNotifications?.any {
                    it.packageName == MAPS_PACKAGE && isNavigationNotification(it)
                } ?: false
            } catch (_: Exception) { false }
            com.coolappstore.everlastingandroidtweak.features.mapspowersaving.MapsState
                .hasNavigationNotification = stillNavigating
        }
        sendNotificationCountUpdate()
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) {
        sendNotificationCountUpdate()
    }

    override suspend fun onEvent(event: Event) {
        when (event) {
            Event.RequestNotificationCount -> {
                if (isListening.value) {
                    handler.post { sendNotificationCountUpdate() }
                }
            }
            else -> {}
        }
    }

    private fun sendNotificationCountUpdate() {
        updateJob.value?.cancel()
        updateJob.value = scope.launch {
            if (!isListening.value) return@launch
            try {
                val notifications = try {
                    @Suppress("DiscouragedPrivateApi")
                    (NotificationListenerService::class.java
                        .getDeclaredMethod("getActiveNotifications", Array<String>::class.java, Int::class.java)
                        .also { it.isAccessible = true }
                        .invoke(this@AutoFreezeService, null, 2) as? Array<*>)
                        ?.filterIsInstance<StatusBarNotification>()
                        ?.toTypedArray()
                        ?: activeNotifications
                } catch (_: Throwable) { activeNotifications } ?: arrayOf()
                globalState.notificationCount.value = notifications.count { it.shouldCount }
            } catch (e: BadParcelableException) {
                if (e.cause !is DeadObjectException) BugsnagUtils.notify(e)
            } catch (_: OutOfMemoryError) {
            } catch (e: Throwable) {
                BugsnagUtils.leaveBreadcrumb("Error sending notification count update",
                    mapOf("error" to e.stringify()), com.bugsnag.android.BreadcrumbType.ERROR)
            }
        }
    }

    private val StatusBarNotification.shouldCount: Boolean
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (notification.flags and Notification.FLAG_BUBBLE != 0 &&
                    notification.bubbleMetadata?.isNotificationSuppressed == true) return false
            }
            if (notification.visibility == Notification.VISIBILITY_SECRET) return false
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1) {
                val ranking = Ranking()
                val rankingResult = currentRanking.getRanking(key, ranking)
                if (rankingResult && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (ranking.lockscreenVisibilityOverride == Notification.VISIBILITY_SECRET ||
                            ranking.channel.lockscreenVisibility == Notification.VISIBILITY_SECRET)) return false
                val importance = ranking.importance
                if (importance != NotificationManager.IMPORTANCE_UNSPECIFIED) {
                    if (importance <= NotificationManager.IMPORTANCE_MIN) return false
                    val silentCheck = importance <= NotificationManager.IMPORTANCE_LOW &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    if (silentCheck && Settings.Secure.getInt(
                            this@AutoFreezeService.contentResolver,
                            "lock_screen_show_silent_notifications", 0) == 0) return false
                    if (importance <= NotificationManager.IMPORTANCE_LOW &&
                        Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU &&
                        try {
                            Class.forName("android.os.SystemProperties")
                                .getMethod("get", String::class.java)
                                .invoke(null, "ro.vendor.camera.extensions.service") as? String
                        } catch (_: Exception) { null }
                            ?.contains("com.google.android.apps.camera.services.extensions.service.PixelExtensions") == true
                    ) return false
                }
            } else {
                @Suppress("DEPRECATION")
                if (notification.priority <= Notification.PRIORITY_MIN) return false
            }
            return true
        }

    override fun onDestroy() {
        super.onDestroy()
        isListening.value = false
        eventManager.removeObserver(this)
        scope.cancel()
        notifLightManager?.release()
        try { unregisterReceiver(lockReceiver) } catch (_: Exception) {}
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun discoverMapsChannel(channelId: String?) {
        if (channelId.isNullOrBlank()) return
        try {
            val prefs = getSharedPreferences(MAPS_PREFS, MODE_PRIVATE)
            val existing = loadDiscoveredChannels(prefs)
            if (existing.any { it.first == channelId }) return
            val readableName: String = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                    nm.getNotificationChannel(channelId)?.name?.toString() ?: formatChannelId(channelId)
                } catch (_: Exception) { formatChannelId(channelId) }
            } else formatChannelId(channelId)
            val updated = existing + listOf(Pair(channelId, readableName))
            saveDiscoveredChannels(prefs, updated)
        } catch (_: Exception) {}
    }

    private fun formatChannelId(id: String) =
        id.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }

    private fun isNavigationNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val channelId    = notification.channelId ?: ""
        val prefs        = getSharedPreferences(MAPS_PREFS, MODE_PRIVATE)
        val detectionSet = loadDetectionChannels(prefs)
        if (detectionSet.contains(channelId) || channelId.contains("navigation", ignoreCase = true)) return true
        return (notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
    }

    private fun loadDiscoveredChannels(prefs: android.content.SharedPreferences): List<Pair<String, String>> {
        val json = prefs.getString(KEY_DISCOVERED, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Pair(obj.getString("id"), obj.getString("name"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveDiscoveredChannels(prefs: android.content.SharedPreferences, channels: List<Pair<String, String>>) {
        val arr = org.json.JSONArray()
        channels.forEach { (id, name) ->
            arr.put(org.json.JSONObject().apply { put("id", id); put("name", name) })
        }
        prefs.edit().putString(KEY_DISCOVERED, arr.toString()).apply()
    }

    private fun loadDetectionChannels(prefs: android.content.SharedPreferences): Set<String> {
        val json = prefs.getString(KEY_DETECTION, null)
        return if (json != null) {
            try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } catch (_: Exception) { DEFAULT_NAV_CHANNELS }
        } else DEFAULT_NAV_CHANNELS
    }
}