package com.linuxraccoon.raccoonradio

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Wraps AlarmManager for the wake timer. Kept separate from the UI so
 * BootReceiver can reschedule a pending timer the same way the dialog does.
 */
object WakeTimerScheduler {
    private const val REQUEST_CODE = 8842

    /** Android 12+ requires this be granted via Settings before scheduling. */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun schedule(context: Context, stationId: Int, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(context, stationId)
        )
        StationStore.setWakeTimer(context, stationId, triggerAtMillis)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val existing = StationStore.getWakeTimer(context)
        if (existing != null) {
            alarmManager.cancel(pendingIntent(context, existing.first))
        }
        StationStore.clearWakeTimer(context)
    }

    private fun pendingIntent(context: Context, stationId: Int): PendingIntent {
        val intent = Intent(context, WakeTimerReceiver::class.java).apply {
            putExtra(WakeTimerReceiver.EXTRA_STATION_ID, stationId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
