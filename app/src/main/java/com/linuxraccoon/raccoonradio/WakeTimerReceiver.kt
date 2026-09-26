package com.linuxraccoon.raccoonradio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WakeTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val stationId = intent.getIntExtra(EXTRA_STATION_ID, -1)
        if (stationId == -1) return

        val station = StationStore.loadStations(context).find { it.id == stationId } ?: return
        RemotePlayback.playStation(context, station)
        StationStore.clearWakeTimer(context)
    }

    companion object {
        const val EXTRA_STATION_ID = "station_id"
    }
}
