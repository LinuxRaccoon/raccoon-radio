package com.linuxraccoon.raccoonradio

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Calendar

/**
 * Lets the person pick which station and what time; the actual time entry is
 * the standard Android time picker (avoids pulling in a Compose one just for
 * this) shown once "Set Timer" is tapped.
 */
@Composable
fun WakeTimerDialog(
    stations: List<RadioStation>,
    onDismiss: () -> Unit,
    onSchedule: (RadioStation, hour: Int, minute: Int) -> Unit
) {
    var selectedStation by remember { mutableStateOf(stations.firstOrNull()) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wake Timer") },
        text = {
            Column {
                Text(
                    "Station",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                stations.forEach { station ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedStation = station }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedStation?.id == station.id,
                            onClick = { selectedStation = station }
                        )
                        Text(station.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val station = selectedStation ?: return@Button
                val now = Calendar.getInstance()
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onSchedule(station, hour, minute) },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(context)
                ).show()
                onDismiss()
            }) { Text("Pick Time") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
