package com.linuxraccoon.raccoonradio

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live "now playing" info for a station, as reported by its (optional)
 * metadata endpoint — separate from whatever ICY text metadata the stream
 * itself carries, since ICY metadata cannot include artwork.
 */
data class TrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val artUrl: String? = null
)

/**
 * Polls a station-supplied JSON endpoint for live track metadata + album art.
 *
 * Expected response shape (extra fields are ignored):
 * { "title": "Song Name", "artist": "Artist Name", "artUrl": "https://.../art.jpg" }
 *
 * Common aliases are also accepted so this works out of the box with a few
 * different naming conventions: "track" / "song" for title, "art" / "image" /
 * "cover" / "art_url" / "artUrl" for the artwork URL.
 *
 * This is intentionally a plain polling loop (no external HTTP library) to
 * avoid adding a new dependency just for this feature.
 */
class TrackMetadataPoller(
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 8000L
) {
    private val _metadata = MutableStateFlow<TrackMetadata?>(null)
    val metadata: StateFlow<TrackMetadata?> = _metadata

    private var job: Job? = null

    fun start(endpointUrl: String) {
        stop()
        if (endpointUrl.isBlank()) return

        job = scope.launch {
            while (true) {
                val result = fetch(endpointUrl)
                if (result != null) {
                    _metadata.value = result
                }
                delay(pollIntervalMs)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _metadata.value = null
    }

    private suspend fun fetch(endpointUrl: String): TrackMetadata? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (connection.responseCode !in 200..299) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            fun firstNonBlank(vararg keys: String): String? {
                for (key in keys) {
                    val value = json.optString(key, "")
                    if (value.isNotBlank()) return value
                }
                return null
            }

            TrackMetadata(
                title = firstNonBlank("title", "track", "song"),
                artist = firstNonBlank("artist"),
                artUrl = firstNonBlank("artUrl", "art_url", "art", "image", "cover", "cover_url")
            )
        } catch (e: Exception) {
            Log.w("TrackMetadataPoller", "Failed to fetch metadata from $endpointUrl", e)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
