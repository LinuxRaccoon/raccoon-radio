package com.linuxraccoon.raccoonradio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class WakeTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Wake alarm fired")

        val stationId = intent.getIntExtra(EXTRA_STATION_ID, -1)
        if (stationId == -1) {
            Log.w(TAG, "No station id in the alarm intent -- nothing to play")
            return
        }

        val station = StationStore.loadStations(context).find { it.id == stationId }
        if (station == null) {
            Log.w(TAG, "Station id $stationId no longer exists -- was it deleted?")
            return
        }

        Log.i(TAG, "Starting ${station.name}")

        // Connecting a MediaController is asynchronous -- the actual play()
        // call happens later, inside a callback. A plain onReceive() is
        // expected to return almost immediately, and once it does, Android
        // can freeze or kill this process before that callback ever runs
        // (especially when the app was fully closed). goAsync() tells the
        // system to keep this process alive until pendingResult.finish() is
        // called, giving the callback time to actually complete.
        val pendingResult = goAsync()
        RemotePlayback.playStation(context, station) { success ->
            Log.i(TAG, if (success) "Playback started successfully" else "Playback FAILED to start")
            StationStore.clearWakeTimer(context)
            pendingResult.finish()
        }
    }

    companion object {
        const val EXTRA_STATION_ID = "station_id"
        private const val TAG = "WakeTimerReceiver"
    }
}
