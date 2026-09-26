package com.linuxraccoon.raccoonradio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RadioPlayer @OptIn(UnstableApi::class) constructor
    (context: Context) {
    private val appContext = context.applicationContext
    private var controller: MediaController? = null

    // Needed so the live-metadata pusher below has a station name/fallback
    // icon to work with -- it only has the poller's output otherwise.
    private var activeStation: RadioStation? = null

    private val _playbackInfo = MutableStateFlow("Stopped")
    val playbackInfo: StateFlow<String> = _playbackInfo

    private val _streamTitle = MutableStateFlow("")
    val streamTitle: StateFlow<String> = _streamTitle

    private val _isControllerConnected = MutableStateFlow(false)
    val isControllerConnected: StateFlow<Boolean> = _isControllerConnected

    // Lives for as long as RadioPlayer does (tied to MainActivity); cancelled
    // implicitly if the process dies, which is fine since polling only matters
    // while something is actively playing.
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val metadataPoller = TrackMetadataPoller(playerScope)

    // Live track metadata (title/artist/artUrl) from a station's optional
    // metadataUrl, when one is set. Null when the current station has no
    // metadata endpoint, or between polls before the first result arrives.
    val trackMetadata: StateFlow<TrackMetadata?> = metadataPoller.metadata

    init {
        playerScope.launch {
            trackMetadata.collect { meta -> pushLiveMetadataToSession(meta) }
        }
    }

    val isPlayingActive: Boolean
        get() = controller?.let {
            it.isPlaying || it.playbackState == Player.STATE_READY || it.playbackState == Player.STATE_BUFFERING
        } ?: false

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            controller = controllerFuture.get()
            setupControllerListener()

            controller?.let { player ->
                if (player.isPlaying || player.playbackState == Player.STATE_READY) {
                    val format = player.currentTracks.groups.firstOrNull()?.getTrackFormat(0)
                    val codec = format?.sampleMimeType?.split("/")?.lastOrNull()?.uppercase()
                    val bitrate = if (format != null && format.bitrate > 0) "${format.bitrate / 1000}kbps" else null

                    _playbackInfo.value = when {
                        codec != null && bitrate != null -> "$codec $bitrate"
                        codec != null -> codec
                        bitrate != null -> bitrate
                        else -> "Playing"
                    }
                } else if (player.playbackState == Player.STATE_BUFFERING) {
                    _playbackInfo.value = "Buffering..."
                }


                val songTitle = player.mediaMetadata.title?.toString() ?: ""
                val stationName = player.mediaMetadata.artist?.toString() ?: ""
                if (songTitle.isNotBlank() || stationName.isNotBlank()) {
                    _streamTitle.value = when {
                        songTitle.isNotBlank() && songTitle != stationName -> "$stationName - $songTitle"
                        else -> stationName
                    }
                }
            }

            _isControllerConnected.value = true
        }, MoreExecutors.directExecutor())
    }

    private fun setupControllerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val songTitle = mediaMetadata.title?.toString() ?: ""
                val stationName = mediaMetadata.artist?.toString() ?: ""

                _streamTitle.value = when {
                    songTitle.isNotBlank() && songTitle != stationName -> "$stationName - $songTitle"
                    else -> stationName
                }
            }

            @OptIn(UnstableApi::class)
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> _playbackInfo.value = "Buffering..."
                    Player.STATE_READY -> {
                        val format = controller?.currentTracks?.groups?.firstOrNull()?.getTrackFormat(0)
                        val codec = format?.sampleMimeType?.split("/")?.lastOrNull()?.uppercase()
                        val bitrate = if (format != null && format.bitrate > 0) "${format.bitrate / 1000}kbps" else null

                        _playbackInfo.value = when {
                            codec != null && bitrate != null -> "$codec $bitrate"
                            codec != null -> codec
                            bitrate != null -> bitrate
                            else -> "Playing"
                        }
                    }
                    else -> _playbackInfo.value = "Stopped"
                }
            }
        })
    }

    // Pushes live track title/artist/art into the MediaSession so the system
    // notification, lock screen, and any connected car display pick it up --
    // same as the in-app now-playing screen. Only touches anything when the
    // poller actually has data (i.e. the station has a metadataUrl); when it
    // doesn't, this is a no-op and Media3's own ICY-based title keeps working
    // exactly as before.
    @OptIn(UnstableApi::class)
    private fun pushLiveMetadataToSession(meta: TrackMetadata?) {
        val station = activeStation ?: return
        val ctrl = controller ?: return
        val currentItem = ctrl.currentMediaItem ?: return

        val metaTitle = meta?.title?.takeIf { it.isNotBlank() }
        val metaArtist = meta?.artist?.takeIf { it.isNotBlank() }
        val trackText = when {
            metaArtist != null && metaTitle != null && metaArtist != metaTitle -> "$metaArtist - $metaTitle"
            metaTitle != null -> metaTitle
            metaArtist != null -> metaArtist
            else -> return // nothing usable yet -- leave ICY metadata alone
        }

        val artUri = meta?.artUrl?.takeIf { it.isNotBlank() }
            ?: station.imageUrl.takeIf { it.isNotBlank() }

        val updatedMetadata = MediaMetadata.Builder()
            .setArtist(station.name)
            .setTitle(trackText)
            .apply { artUri?.let { setArtworkUri(Uri.parse(it)) } }
            .build()

        ctrl.replaceMediaItem(0, currentItem.buildUpon().setMediaMetadata(updatedMetadata).build())
    }

    fun getActiveStationFromSession(): RadioStation? {
        val player = controller ?: return null
        if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
            val metadata = player.mediaMetadata
            val streamUrl = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: ""
            val name = metadata.artist?.toString() ?: metadata.title?.toString() ?: "Unknown Station"
            val imageUrl = metadata.artworkUri?.toString() ?: ""

            if (streamUrl.isNotBlank()) {
                return RadioStation(
                    id = streamUrl.hashCode(),
                    name = name,
                    streamUrl = streamUrl,
                    imageUrl = imageUrl
                )
            }
        }

        return null
    }

    fun play(station: RadioStation) {
        activeStation = station
        StationStore.setPlayingStationId(appContext, station.id)
        RaccoonRadioWidgetProvider.refreshAll(appContext)

        val mediaItem = MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtist(station.name)
                    .setArtworkUri(Uri.parse(station.imageUrl))
                    .build()
            )
            .build()

        controller?.setMediaItem(mediaItem)
        controller?.prepare()
        controller?.play()

        metadataPoller.start(station.metadataUrl.orEmpty())
    }

    fun stop() {
        controller?.stop()
        _streamTitle.value = ""
        activeStation = null
        StationStore.setPlayingStationId(appContext, null)
        RaccoonRadioWidgetProvider.refreshAll(appContext)
        metadataPoller.stop()
    }
}