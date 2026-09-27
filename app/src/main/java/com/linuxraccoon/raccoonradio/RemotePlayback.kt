package com.linuxraccoon.raccoonradio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
    private const val CONFIRM_TIMEOUT_MS = 8000L

    fun playStation(context: Context, station: RadioStation, onComplete: (success: Boolean) -> Unit = {}) {
        withController(context, onComplete) { controller, finish ->
            val mediaItem = MediaItem.Builder()
                .setUri(station.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setArtist(station.name)
                        .setArtworkUri(Uri.parse(station.imageUrl))
                        .build()
                )
                .build()

            StationStore.setPlayingStationId(context, station.id)
            StationStore.setCurrentArtUrl(context, station.imageUrl)
            RaccoonRadioWidgetProvider.refreshAll(context)

            // Wait for the player to actually confirm it's playing before
            // releasing the controller -- releasing right after play() is
            // called risks tearing the connection down before that command
            // is fully dispatched, since it's asynchronous even in-process.
            waitForConfirmation(
                controller = controller,
                isDone = { it.isPlaying },
                onDone = { success -> finish(success) }
            )

            Log.i(TAG, "Calling setMediaItem/prepare/play")
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }

    fun stopPlayback(context: Context, onComplete: (success: Boolean) -> Unit = {}) {
        withController(context, onComplete) { controller, finish ->
            StationStore.setPlayingStationId(context, null)
            StationStore.setCurrentArtUrl(context, null)
            RaccoonRadioWidgetProvider.refreshAll(context)

            waitForConfirmation(
                controller = controller,
                isDone = { !it.isPlaying },
                onDone = { success -> finish(success) }
            )

            controller.stop()
        }
    }

    /**
     * Attaches a listener that waits for [isDone] to become true (checked on
     * every relevant player event) or for a player error, then calls [onDone]
     * exactly once with whether it finished normally. Falls back to timing
     * out after [CONFIRM_TIMEOUT_MS] so callers relying on goAsync() are
     * never left hanging indefinitely if the stream never loads.
     */
    private fun waitForConfirmation(controller: MediaController, isDone: (Player) -> Boolean, onDone: (Boolean) -> Unit) {
        var finished = false
        val handler = Handler(Looper.getMainLooper())

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = check()
            override fun onPlaybackStateChanged(playbackState: Int) = check()

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.errorCodeName}", error)
                finish(false)
            }

            fun check() {
                if (isDone(controller)) finish(true)
            }

            fun finish(success: Boolean) {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(null)
                controller.removeListener(this)
                onDone(success)
            }
        }

        controller.addListener(listener)
        handler.postDelayed({
            if (!finished) {
                Log.w(TAG, "Timed out -- final playbackState=${stateName(controller.playbackState)}, isPlaying=${controller.isPlaying}, playWhenReady=${controller.playWhenReady}")
                listener.finish(false)
            }
        }, CONFIRM_TIMEOUT_MS)

        // In case the state is already correct by the time we attach (e.g.
        // stop() on an already-stopped player).
        if (isDone(controller)) listener.finish(true)
    }

    private fun stateName(state: Int) = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }

    private fun withController(
        context: Context,
        onComplete: (success: Boolean) -> Unit,
        action: (MediaController, finish: (Boolean) -> Unit) -> Unit
    ) {
        val appContext = context.applicationContext

        // Binding alone (what MediaController.Builder does) doesn't reliably
        // start the service from a background context -- explicitly starting
        // it first is what actually pairs with the exact-alarm/background
        // exemption Android grants for calling startForegroundService().
        Log.i(TAG, "Starting PlaybackService...")
        ContextCompat.startForegroundService(appContext, Intent(appContext, PlaybackService::class.java))

        val sessionToken = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        Log.i(TAG, "Connecting to PlaybackService...")
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                Log.i(TAG, "Connected -- running action")
                action(controller) { success ->
                    Log.i(TAG, "Confirmed (success=$success) -- releasing controller")
                    controller.release()
                    onComplete(success)
                }
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
