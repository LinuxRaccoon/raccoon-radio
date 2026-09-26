package com.linuxraccoon.raccoonradio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
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
    fun playStation(context: Context, station: RadioStation, onComplete: () -> Unit = {}) {
        withController(context) { controller ->
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
            RaccoonRadioWidgetProvider.refreshAll(context)
        }.also { future ->
            future.addListener({ onComplete() }, MoreExecutors.directExecutor())
        }
    }

    fun stopPlayback(context: Context, onComplete: () -> Unit = {}) {
        withController(context) { controller ->
            controller.stop()
            StationStore.setPlayingStationId(context, null)
            RaccoonRadioWidgetProvider.refreshAll(context)
        }.also { future ->
            future.addListener({ onComplete() }, MoreExecutors.directExecutor())
        }
    }

    private fun withController(context: Context, action: (MediaController) -> Unit): ListenableFuture<MediaController> {
        val appContext = context.applicationContext
        val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()

        controllerFuture.addListener({
            val controller = controllerFuture.get()
            action(controller)
            controller.release()
        }, MoreExecutors.directExecutor())

        return controllerFuture
    }
}
