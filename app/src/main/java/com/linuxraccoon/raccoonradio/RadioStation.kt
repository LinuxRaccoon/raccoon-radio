package com.linuxraccoon.raccoonradio

data class RadioStation(
    val id: Int,
    val name: String,
    val streamUrl: String,
    val imageUrl: String,
    // Optional: URL of a JSON endpoint returning live "now playing" info for this
    // station, e.g. {"title": "...", "artist": "...", "artUrl": "..."}. When set,
    // this is polled while the station plays and takes priority over ICY metadata
    // and the static imageUrl for the now-playing artwork. Left blank for stations
    // that only have ICY text metadata (the vast majority of internet radio).
    // Nullable (not defaulted) because Gson deserializes saved stations by
    // reflection, bypassing the constructor — stations saved before this field
    // existed will load with metadataUrl == null rather than triggering the
    // default. Always read it via .orEmpty() rather than assuming non-null.
    val metadataUrl: String? = null
)