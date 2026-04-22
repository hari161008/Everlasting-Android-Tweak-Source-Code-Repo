package com.coolappstore.everlastingandroidtweak.features.mapspowersaving

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class MapsPowerSavingViewModel(context: Context) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(MapsPowerSavingManager.PREFS_NAME, Context.MODE_PRIVATE)

    val isEnabled = mutableStateOf(prefs.getBoolean(MapsPowerSavingManager.KEY_ENABLED, false))
    val mapsChannels = mutableStateOf<List<MapsChannel>>(emptyList())

    private val manager = MapsPowerSavingManager(context.applicationContext)

    init {
        MapsState.isEnabled = isEnabled.value
        loadChannels()
    }

    fun refresh() {
        isEnabled.value = prefs.getBoolean(MapsPowerSavingManager.KEY_ENABLED, false)
        MapsState.isEnabled = isEnabled.value
        loadChannels()
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled.value = enabled
        MapsState.isEnabled = enabled
        prefs.edit().putBoolean(MapsPowerSavingManager.KEY_ENABLED, enabled).apply()
    }

    /** Manually trigger MinMode — for testing from the settings screen */
    fun triggerMinModeNow() = manager.launchMinModeManually()

    fun setMapsChannelDetected(channelId: String, detected: Boolean, context: Context) {
        val current = loadDetectionChannels().toMutableSet()
        if (detected) current.add(channelId) else current.remove(channelId)
        saveDetectionChannels(current)
        loadChannels()
    }

    private fun loadChannels() {
        val discovered   = loadDiscoveredChannels()
        val detectionSet = loadDetectionChannels()
        mapsChannels.value = discovered.map { ch ->
            ch.copy(isEnabled = detectionSet.contains(ch.id))
        }
    }

    private fun loadDiscoveredChannels(): List<MapsChannel> {
        val json = prefs.getString(MapsPowerSavingManager.KEY_DISCOVERED, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                MapsChannel(id = obj.getString("id"), name = obj.getString("name"))
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun loadDetectionChannels(): Set<String> {
        val json = prefs.getString(MapsPowerSavingManager.KEY_DETECTION, null)
        return if (json != null) {
            try {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            } catch (_: Exception) { MapsPowerSavingManager.DEFAULT_NAV_CHANNELS }
        } else MapsPowerSavingManager.DEFAULT_NAV_CHANNELS
    }

    private fun saveDetectionChannels(channels: Set<String>) {
        val arr = org.json.JSONArray(channels.toList())
        prefs.edit().putString(MapsPowerSavingManager.KEY_DETECTION, arr.toString()).apply()
    }

    companion object {
        fun create(context: Context) = MapsPowerSavingViewModel(context.applicationContext)
    }
}
