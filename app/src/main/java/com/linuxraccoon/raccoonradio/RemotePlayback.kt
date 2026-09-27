package com.linuxraccoon.raccoonradio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

/**
 * One-off "connect a MediaController, do one thing, release it" helper for
 * places that don't have a persistent RadioPlayer/MediaController around --
 * the home-screen widget and the wake-timer alarm receiver, both of which can
 * run with the app's Activity fully closed. MainActivity's own RadioPlayer
 * keeps a long-lived controller instead, since it's cheaper for repeated use
 * there and it already needs the connection open for the metadata poller.
 */
@OptIn(UnstableApi::class)
object RemotePlayback {
    private const val TAG = "RemotePlayback"

    fun playStation(context: Context, station: RadioStation, onComplete: (success: Boolean) -> Unit = {}) {
        withController(context, onComplete) { controller ->
            val mediaItem = MediaItem.Builder()
                .setUri(station.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setArtist(station.name)
                        .setArtworkUri(Uri.parse(station.imageUrl))
                        .build()
                )
                .build()
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            StationStore.setPlayingStationId(context, station.id)
            StationStore.setCurrentArtUrl(context, station.imageUrl)
            RaccoonRadioWidgetProvider.refreshAll(context)
        }
    }

    fun stopPlayback(context: Context, onComplete: (success: Boolean) -> Unit = {}) {
        withController(context, onComplete) { controller ->
            controller.stop()
            StationStore.setPlayingStationId(context, null)
            StationStore.setCurrentArtUrl(context, null)
            RaccoonRadioWidgetProvider.refreshAll(context)
        }
    }

    private fun withController(
        context: Context,
        onComplete: (success: Boolean) -> Unit,
        action: (MediaController) -> Unit
    ) {
        val appContext = context.applicationContext
        val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        Log.i(TAG, "Connecting to PlaybackService...")
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                Log.i(TAG, "Connected -- running action")
                action(controller)
                controller.release()
                onComplete(true)
            } catch (e: Exception) {
                // This is exactly the kind of failure that's invisible without
                // logging: the controller never connected (service couldn't
                // start, got killed by the OS, a security exception from a
                // battery manager, etc.) and nothing would otherwise show it.
                Log.e(TAG, "Failed to connect to PlaybackService or run action", e)
                onComplete(false)
            }
        }, MoreExecutors.directExecutor())
    }
}
