package com.linuxraccoon.raccoonradio

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter


object M3UExporter {
    fun export(context: Context, uri: Uri, stations: List<RadioStation>) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                writer.write("#EXTM3U\n")
                stations.forEach { station ->
                    // "meta-url" is a Raccoon Radio-specific extension attribute; other
                    // M3U players will simply ignore it.
                    writer.write("#EXTINF:-1 tvg-logo=\"${station.imageUrl}\" meta-url=\"${station.metadataUrl.orEmpty()}\" group-title=\"radio\" radio=\"true\", ${station.name}\n")
                    writer.write("${station.streamUrl}\n")
                }
            }
        }
    }
}
object M3UParser {
    fun parse(context: Context, uri: Uri): List<RadioStation> {
        val stations = mutableListOf<RadioStation>()
        val content = context.contentResolver.openInputStream(uri) ?: return emptyList()

        val reader = BufferedReader(InputStreamReader(content))
        var currentName = ""
        var currentLogo = ""
        var currentMetaUrl = ""

        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#EXTINF") -> {
                        currentName = trimmed.substringAfterLast(",").trim()

                        currentLogo = if (trimmed.contains("tvg-logo=\"")) {
                            trimmed.substringAfter("tvg-logo=\"").substringBefore("\"")
                        } else {
                            ""
                        }

                        currentMetaUrl = if (trimmed.contains("meta-url=\"")) {
                            trimmed.substringAfter("meta-url=\"").substringBefore("\"")
                        } else {
                            ""
                        }
                    }

                    trimmed.startsWith("http") -> {
                        if (currentName.isEmpty()) currentName = "Unknown Station"

                        stations.add(
                            RadioStation(
                                id = (System.currentTimeMillis() + stations.size).toInt(),
                                name = currentName,
                                streamUrl = trimmed,
                                imageUrl = currentLogo.ifEmpty { "" },
                                metadataUrl = currentMetaUrl.ifEmpty { "" }
                            )
                        )
                        currentName = ""
                        currentLogo = ""
                        currentMetaUrl = ""
                    }
                }
            }
        }
        return stations
    }
}

