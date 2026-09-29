package com.linuxraccoon.raccoonradio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How Raccoon Radio Works") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                HelpSection(
                    title = "Adding a station",
                    body = "Tap the + button on the Radio tab. Name and Stream URL are required; " +
                        "Icon URL and Metadata Endpoint are both optional."
                )
                HelpSection(
                    title = "Editing, reordering or deleting",
                    body = "Long-press any station to reveal its controls: the pencil edits it, the " +
                        "arrows move it, and X closes editing. Delete is inside the edit screen."
                )
                HelpSection(
                    title = "Pinning stations to the widget",
                    body = "Long-press a station and tap the star (bottom-left). Up to 2 stations can " +
                        "be pinned \u2014 a gold star badge shows which ones. If none are pinned, the " +
                        "widget just shows your first 2 stations instead."
                )
                HelpSection(
                    title = "Adding the widget to your home screen",
                    body = "Long-press an empty spot on your phone's home screen, choose Widgets, find " +
                        "Raccoon Radio, and drag it on. Tap a button to play that station; tap the " +
                        "artwork to open the app."
                )
                HelpSection(
                    title = "Live album art",
                    body = "Only shows for stations with a Metadata Endpoint set \u2014 otherwise you'll " +
                        "see the station's icon and the stream's own text info."
                )
                HelpSection(
                    title = "Sleep and Wake timers",
                    body = "Sleep timer is on the now-playing screen or mini player. Wake Timer is in " +
                        "Settings \u2014 it starts a station at a set time, even if the app is fully closed."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}

@Composable
private fun HelpSection(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(16.dp))
}
