package com.mobile.safedrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.mobile.safedrive.drowsiness.DrowsinessState
import com.mobile.safedrive.drowsiness.MonitorCondition
import com.mobile.safedrive.service.MonitorUiState
import com.mobile.safedrive.ui.components.OutlineButton
import com.mobile.safedrive.ui.components.ProgressRing
import com.mobile.safedrive.ui.components.StatTile
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText
import kotlinx.coroutines.delay

private const val CLOCK_TICK_MS = 1_000L
private const val MAX_SCORE = 100f

/** Spec Screen 3 in the design's "Monitoring" layout: status ring, drive time, distance, End Trip. */
@Composable
fun MonitoringScreen(monitor: MonitorUiState, keepScreenAwake: Boolean, onEndTrip: () -> Unit) {
    val c = SdTheme.colors
    val view = LocalView.current
    DisposableEffect(keepScreenAwake) {
        view.keepScreenOn = keepScreenAwake
        onDispose { view.keepScreenOn = false }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(CLOCK_TICK_MS)
        }
    }

    val (label, labelColor) = when (monitor.state) {
        DrowsinessState.NORMAL -> "SAFE" to c.success
        DrowsinessState.ATTENTION -> "ALERT" to c.accent
        DrowsinessState.DROWSY -> "DROWSY" to c.warning
        DrowsinessState.CRITICAL -> "DANGER" to c.danger
    }
    val (headline, detail) = statusText(monitor)
    val alertness = ((MAX_SCORE - monitor.drowsinessScore) / MAX_SCORE).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text(
            "Monitoring",
            style = sdText(TextSize.lg, FontWeight.SemiBold),
            color = c.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 48.dp),
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ProgressRing(
                progress = alertness,
                modifier = Modifier.size(192.dp),
                color = c.accent,
                trackColor = c.track,
                strokeWidth = 8.dp,
            ) {
                Box(
                    Modifier
                        .padding(8.dp)
                        .fillMaxSize()
                        .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.06f))
                        .clip(CircleShape)
                        .background(c.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, tint = c.accent, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            label,
                            style = sdText(TextSize.xl2, FontWeight.Bold, letterSpacing = 0.025.em),
                            color = labelColor,
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(headline, style = sdText(TextSize.xl, FontWeight.Bold), color = c.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(detail, style = sdText(TextSize.base), color = c.textSecondary, textAlign = TextAlign.Center)
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val elapsed = if (monitor.startTimeMs > 0) now - monitor.startTimeMs else 0L
            StatTile("Drive Time", formatClock(elapsed), Modifier.weight(1f), labelSize = TextSize.sm, labelWeight = FontWeight.Normal)
            StatTile("Distance", formatDistance(monitor.distanceKm), Modifier.weight(1f), labelSize = TextSize.sm, labelWeight = FontWeight.Normal)
        }
        OutlineButton("End Trip", onEndTrip, color = c.accent, textSize = TextSize.lg)
    }
}

/** Keeps the design copy for the normal case; spec §47 messages for degraded input. */
private fun statusText(monitor: MonitorUiState): Pair<String, String> = when {
    monitor.cameraError -> "Camera unavailable" to "Monitoring stopped safely. End the trip and try again."
    !monitor.modelAvailable -> "Face detection unavailable" to "Reinstall the app to restore the on-device model."
    monitor.condition == MonitorCondition.FACE_NOT_DETECTED -> "Face not visible" to "Please keep your face visible."
    monitor.condition == MonitorCondition.MULTIPLE_FACES -> "Stay focused" to "Only one driver should be visible."
    monitor.condition == MonitorCondition.LOW_LIGHT -> "Low light" to "Please improve lighting."
    monitor.state == DrowsinessState.ATTENTION -> "Stay alert" to "Keep your eyes on the road"
    monitor.state >= DrowsinessState.DROWSY -> "Take a break" to "You may be getting drowsy"
    else -> "Stay focused" to "Keep your eyes on the road"
}
