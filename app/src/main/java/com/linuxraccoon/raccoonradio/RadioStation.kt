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
    val metadataUrl: String? = null,
    // Whether this station is pinned to the home-screen widget. Safe as a
    // plain non-null Boolean (unlike metadataUrl above) because Gson leaves
    // a missing primitive field at its JVM default (false) rather than null.
    val starred: Boolean = false
)