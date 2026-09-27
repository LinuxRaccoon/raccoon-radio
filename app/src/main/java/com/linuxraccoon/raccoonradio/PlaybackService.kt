package com.linuxraccoon.raccoonradio

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val rawPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // false = don't let ExoPlayer auto-manage audio focus. With
                // this on (the previous setting), ExoPlayer silently set
                // playWhenReady=false whenever its own focus request failed
                // or was lost -- which reliably happened when playback was
                // started from a cold background process (wake timer, widget
                // with the app killed), even though the stream itself had
                // buffered successfully. A radio app playing in a vehicle is
                // reasonably expected to just keep playing rather than
                // silently going quiet, so we manage this ourselves instead
                // of leaving it to ExoPlayer's default behaviour.
                false
            )
        }

        rawPlayer.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {}
        })

        val interceptingPlayer = object : ForwardingPlayer(rawPlayer) {
            override fun pause() {
                super.stop()
            }
        }

        mediaSession = MediaSession.Builder(this, interceptingPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}