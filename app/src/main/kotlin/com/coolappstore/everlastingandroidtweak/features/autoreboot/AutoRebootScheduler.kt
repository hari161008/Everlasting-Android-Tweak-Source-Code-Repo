package com.coolappstore.everlastingandroidtweak.features.autoreboot

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object AutoRebootScheduler {

    private const val REQUEST_CODE = 9001

    fun schedule(context: Context, hour: Int, minute: Int, days: List<Boolean>) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel any existing alarm first
        cancel(context)

        val selectedDays = days.mapIndexedNotNull { i, selected ->
            if (selected) i + 2 else null // Calendar.MONDAY=2 .. SUNDAY=1, offset by 2
        }.let { list ->
            // If none selected, schedule daily
            if (list.isEmpty()) (2..8).map { if (it > 7) 1 else it } else list
        }

        // Schedule next occurrence
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        // Find next matching day
        var attempts = 0
        while (!selectedDays.contains(cal.get(Calendar.DAY_OF_WEEK)) && attempts < 7) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            attempts++
        }

        val intent = Intent(context, AutoRebootReceiver::class.java).apply {
            action = AutoRebootReceiver.ACTION_REBOOT
        }
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (e: SecurityException) {
            // Fallback to inexact
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutoRebootReceiver::class.java).apply {
            action = AutoRebootReceiver.ACTION_REBOOT
        }
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    // Reschedule daily (called from receiver after each reboot)
    fun rescheduleNext(context: Context, hour: Int, minute: Int, days: List<Boolean>) {
        schedule(context, hour, minute, days)
    }
}
