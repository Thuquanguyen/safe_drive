package com.mobile.safedrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.mobile.safedrive.drowsiness.CalibrationManager
import com.mobile.safedrive.service.CalibrationStatus
import com.mobile.safedrive.service.MonitoringStore
import com.mobile.safedrive.ui.components.OutlineButton
import com.mobile.safedrive.ui.components.PrimaryButton
import com.mobile.safedrive.ui.components.ProgressRing
import com.mobile.safedrive.ui.components.TopBar
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText
import kotlin.math.ceil

private const val MS_PER_SECOND = 1_000f

/**
 * Spec Screen 2 "Get Ready / Calibrating…" rendered with the design system
 * (ring from Monitoring, progress bar from Home, typography from the alert screens).
 */
@Composable
fun CalibrationScreen(onCancel: () -> Unit, onRetry: () -> Unit) {
    val c = SdTheme.colors
    val monitor by MonitoringStore.state.collectAsState()
    val failed = monitor.calibrationStatus == CalibrationStatus.FAILED
    val progress = monitor.calibrationProgress
    val secondsLeft = ceil((1f - progress) * CalibrationManager.DURATION_MS / MS_PER_SECOND).toInt()
    val faceMissing = monitor.calibrationStatus == CalibrationStatus.RUNNING && !monitor.calibrationFaceVisible

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Calibration", onBack = onCancel)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            ProgressRing(
                progress = progress,
                modifier = Modifier.size(192.dp),
                color = c.accent,
                trackColor = c.track,
                strokeWidth = 8.dp,
            ) {
                Icon(
                    Icons.Outlined.Face, contentDescription = null,
                    tint = if (faceMissing || failed) c.warning else c.accent,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                if (failed) "Calibration failed" else "Get Ready",
                style = sdText(TextSize.xl2, FontWeight.Bold),
                color = c.textPrimary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    failed -> "We couldn't see your face clearly.\nAdjust the phone and try again."
                    faceMissing -> "Face not detected\nMove the phone so your face is visible."
                    else -> "Keep your face visible\nand look naturally ahead."
                },
                style = sdText(TextSize.s15, lineHeight = 24.sp),
                color = if (faceMissing) c.warning else c.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            if (!failed) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Calibrating...",
                        style = sdText(TextSize.sm, FontWeight.SemiBold),
                        color = c.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "$secondsLeft seconds",
                        style = sdText(TextSize.sm, FontWeight.Medium),
                        color = c.textSecondary,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.track)) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .clip(CircleShape)
                            .background(c.primary),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.padding(24.dp)) {
            if (failed) {
                PrimaryButton("Try Again", onRetry)
                Spacer(Modifier.height(12.dp))
            }
            OutlineButton("Cancel", onCancel)
        }
    }
}
