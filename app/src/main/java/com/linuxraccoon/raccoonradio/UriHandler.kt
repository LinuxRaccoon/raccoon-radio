package com.linuxraccoon.raccoonradio

import android.content.Intent
import android.net.Uri

object UriHandler {
    fun handleIncomingIntent(intent: Intent?): RadioStation? {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return null

        val data: Uri = intent.data ?: return null

        // "raccoonradio" is this fork's own scheme; "acoustic" is kept so existing
        // "add station" buttons/links built for the upstream app still work.
        if ((data.scheme == "raccoonradio" || data.scheme == "acoustic") && data.host == "register") {
            val name = data.getQueryParameter("name").orEmpty()
            val url = data.getQueryParameter("url").orEmpty()
            val image = data.getQueryParameter("image").orEmpty()
            val meta = data.getQueryParameter("meta").orEmpty()

            if (url.isNotBlank()) {
                return RadioStation(
                    id = -99,
                    name = name,
                    streamUrl = url,
                    imageUrl = image,
                    metadataUrl = meta
                )
            }
        }
        return null
    }
}