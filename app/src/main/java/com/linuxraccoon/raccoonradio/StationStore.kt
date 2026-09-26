package com.linuxraccoon.raccoonradio

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Station list + "what's currently playing" persistence, shared between the
 * main app and the home-screen widget. The widget runs without any Activity,
 * so it needs its own way to read the station list and know what's playing
 * without going through MainActivity at all.
 */
object StationStore {
    private const val PREFS = "wave_prefs"
    private const val KEY_STATIONS = "stations_list"
    private const val KEY_PLAYING_ID = "widget_playing_station_id"
    private const val KEY_WAKE_STATION_ID = "wake_timer_station_id"
    private const val KEY_WAKE_TIME = "wake_timer_trigger_at"

    private val gson = Gson()

    fun loadStations(context: Context): List<RadioStation> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_STATIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<RadioStation>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveStations(context: Context, stations: List<RadioStation>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_STATIONS, gson.toJson(stations)) }
    }

    // Tracked separately from real playback state so the widget can draw
    // itself instantly on tap, without first connecting to the MediaSession.
    // Both the app (via RadioPlayer) and the widget update this whenever
    // they start or stop a station, so they always agree.
    fun setPlayingStationId(context: Context, stationId: Int?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_PLAYING_ID, stationId ?: -1) }
    }

    fun getPlayingStationId(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getInt(KEY_PLAYING_ID, -1)
        return if (id == -1) null else id
    }

    // The wake timer: which station, and when (epoch millis) to start it.
    // Persisted so BootReceiver can reschedule it if the phone reboots before
    // it fires -- AlarmManager alarms don't survive a reboot on their own.
    fun setWakeTimer(context: Context, stationId: Int, triggerAtMillis: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            putInt(KEY_WAKE_STATION_ID, stationId)
            putLong(KEY_WAKE_TIME, triggerAtMillis)
        }
    }

    fun clearWakeTimer(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit {
            remove(KEY_WAKE_STATION_ID)
            remove(KEY_WAKE_TIME)
        }
    }

    /** Returns (stationId, triggerAtMillis), or null if no wake timer is set. */
    fun getWakeTimer(context: Context): Pair<Int, Long>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stationId = prefs.getInt(KEY_WAKE_STATION_ID, -1)
        val time = prefs.getLong(KEY_WAKE_TIME, -1L)
        return if (stationId != -1 && time != -1L) stationId to time else null
    }
}
