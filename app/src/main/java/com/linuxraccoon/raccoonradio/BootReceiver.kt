package com.linuxraccoon.raccoonradio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val (stationId, triggerAt) = StationStore.getWakeTimer(context) ?: return
        if (triggerAt > System.currentTimeMillis() && WakeTimerScheduler.canScheduleExactAlarms(context)) {
            WakeTimerScheduler.schedule(context, stationId, triggerAt)
        } else {
            // Either the time has already passed while the phone was off, or
            // the exact-alarm permission was somehow revoked -- either way,
            // there's nothing sensible to reschedule.
            StationStore.clearWakeTimer(context)
        }
    }
}
