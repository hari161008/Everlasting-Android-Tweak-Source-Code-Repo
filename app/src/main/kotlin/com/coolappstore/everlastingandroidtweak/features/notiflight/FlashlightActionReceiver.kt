package com.coolappstore.everlastingandroidtweak.features.notiflight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.coolappstore.everlastingandroidtweak.services.EverlastingAccessibilityService

class FlashlightActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PULSE_NOTIFICATION =
            "com.coolappstore.everlastingandroidtweak.ACTION_PULSE_NOTIFICATION"
        const val ACTION_TOGGLE =
            "com.coolappstore.everlastingandroidtweak.ACTION_FLASHLIGHT_TOGGLE"
        const val EXTRA_FLASH_COUNT  = "flash_count"
        const val EXTRA_FLASH_SPEED  = "flash_speed_ms"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d("FlashlightActionReceiver", "Action: $action")

        // Delegate to accessibility service so it runs in a long-lived context
        val svcIntent = Intent(context, EverlastingAccessibilityService::class.java).apply {
            this.action = action
            putExtra(EXTRA_FLASH_COUNT, intent.getIntExtra(EXTRA_FLASH_COUNT, 3))
            putExtra(EXTRA_FLASH_SPEED, intent.getIntExtra(EXTRA_FLASH_SPEED, 150))
        }
        try { context.startService(svcIntent) } catch (_: Exception) {}
    }
}
