package com.coolappstore.everlastingandroidtweak.features.autoreboot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

class AutoRebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REBOOT) {
            Log.d("AutoReboot", "Reboot alarm fired")
            try {
                Runtime.getRuntime().exec(arrayOf("sh", "-c", "reboot"))
            } catch (e: Exception) {
                Log.e("AutoReboot", "Shell reboot failed: ${e.message}")
            }
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.reboot(null)
            } catch (e: Exception) {
                Log.e("AutoReboot", "PowerManager reboot failed (grant REBOOT via ADB): ${e.message}")
            }
        }
    }
    companion object { const val ACTION_REBOOT = "com.coolappstore.everlastingandroidtweak.REBOOT" }
}
