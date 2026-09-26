package com.linuxraccoon.raccoonradio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews

/**
 * Two-button home-screen widget: one button per station (in station-list
 * order), each toggling that station's playback on tap. Works independently
 * of MainActivity/RadioPlayer, since the widget can be tapped with the app
 * fully closed -- it talks to the same PlaybackService MediaSession directly.
 */
class RaccoonRadioWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_STATION) {
            val stationId = intent.getIntExtra(EXTRA_STATION_ID, -1)
            if (stationId != -1) {
                toggleStation(context, stationId)
            }
        }
    }

    private fun toggleStation(context: Context, stationId: Int) {
        val station = StationStore.loadStations(context).find { it.id == stationId } ?: return
        val alreadyPlaying = StationStore.getPlayingStationId(context) == stationId

        // Same async-after-return concern as WakeTimerReceiver: the actual
        // controller work finishes in a later callback, so hold the process
        // open with goAsync() until it does.
        val pendingResult = goAsync()
        if (alreadyPlaying) {
            RemotePlayback.stopPlayback(context) { pendingResult.finish() }
        } else {
            RemotePlayback.playStation(context, station) { pendingResult.finish() }
        }
    }

    companion object {
        const val ACTION_TOGGLE_STATION = "com.linuxraccoon.raccoonradio.WIDGET_TOGGLE_STATION"
        const val EXTRA_STATION_ID = "station_id"

        private val BUTTON_IDS = listOf(R.id.widget_button_0, R.id.widget_button_1)

        /** Call this whenever playback starts/stops from anywhere -- app or widget. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RaccoonRadioWidgetProvider::class.java))
            ids.forEach { id -> updateWidget(context, manager, id) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val stations = StationStore.loadStations(context).take(BUTTON_IDS.size)
            val playingId = StationStore.getPlayingStationId(context)
            val views = RemoteViews(context.packageName, R.layout.widget_raccoon_radio)

            BUTTON_IDS.forEachIndexed { index, buttonId ->
                val station = stations.getOrNull(index)
                if (station == null) {
                    views.setViewVisibility(buttonId, View.INVISIBLE)
                    return@forEachIndexed
                }

                views.setViewVisibility(buttonId, View.VISIBLE)
                val isPlaying = playingId == station.id
                views.setTextViewText(buttonId, if (isPlaying) "\u25A0 ${station.name}" else "\u25B6 ${station.name}")
                views.setInt(
                    buttonId,
                    "setBackgroundResource",
                    if (isPlaying) R.drawable.widget_button_active else R.drawable.widget_button_idle
                )

                val intent = Intent(context, RaccoonRadioWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_STATION
                    putExtra(EXTRA_STATION_ID, station.id)
                    // Distinct data URI so each station's PendingIntent stays
                    // unique even though the requestCode alone should already
                    // guarantee that -- belt and braces across OEM launchers.
                    data = Uri.parse("raccoonradio://widget/${station.id}")
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    station.id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(buttonId, pendingIntent)
            }

            manager.updateAppWidget(widgetId, views)
        }
    }
}
