package com.linuxraccoon.raccoonradio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi // <-- ADDED IMPORT
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * Full-screen "now playing" view: opened directly when a station is tapped
 * from the grid. Dismissed via the system back button, a swipe down, or the
 * chevron at the top — all of which just hide this screen; playback keeps
 * running underneath via the existing mini player.
 */
@OptIn(ExperimentalFoundationApi::class) // <-- ADDED ANNOTATION
@Composable
fun NowPlayingScreen(
    station: RadioStation,
    streamTitle: String,
    artUrl: String,
    stats: String,
    timer: String,
    sleepTimerMinutes: Int,
    onSetTimerClick: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    // How far (px) a downward drag needs to travel before it counts as a
    // dismiss rather than snapping back.
    val dismissThresholdPx = 260f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = offsetY.value
                // Fades and slightly shrinks as it's dragged away, so the
                // gesture feels connected to the motion rather than a hard cut.
                val progress = (offsetY.value / dismissThresholdPx).coerceIn(0f, 1f)
                alpha = 1f - (progress * 0.4f)
                scaleX = 1f - (progress * 0.05f)
                scaleY = 1f - (progress * 0.05f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetY.value > dismissThresholdPx) {
                                offsetY.animateTo(size.height.toFloat(), tween(200))
                                onDismiss()
                            } else {
                                offsetY.animateTo(0f, tween(200))
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val newValue = (offsetY.value + dragAmount).coerceAtLeast(0f)
                        scope.launch { offsetY.snapTo(newValue) }
                    }
                )
            }
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Ambient backdrop: a heavily blurred, darkened copy of the artwork
        // filling the whole screen behind the card, for a bit of atmosphere.
        // Degrades gracefully to a plain surface on API < 31, where blur() is
        // a no-op.
        if (artUrl.isNotBlank()) {
            AsyncImage(
                model = artUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(80.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.5f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (artUrl.isNotBlank()) {
                    AsyncImage(
                        model = artUrl,
                        contentDescription = "${station.name} artwork",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(0.4f).align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = station.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            if (streamTitle.isNotBlank()) {
                Text(
                    text = streamTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 2000)
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = if (sleepTimerMinutes > 0) "Closing in ${sleepTimerMinutes}m • $stats" else "$stats • $timer",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSetTimerClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Sleep timer",
                        tint = if (sleepTimerMinutes > 0) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.width(28.dp))

                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(76.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(Modifier.width(28.dp))

                // Empty spacer to balance the timer button on the other side,
                // keeping the stop button visually centered.
                Spacer(Modifier.size(56.dp))
            }
        }
    }
}