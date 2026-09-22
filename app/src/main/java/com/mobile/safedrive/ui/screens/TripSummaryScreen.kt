package com.mobile.safedrive.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.safedrive.session.DrivingSession
import com.mobile.safedrive.session.RoutePoint
import com.mobile.safedrive.session.ratingFor
import com.mobile.safedrive.ui.components.ProgressRing
import com.mobile.safedrive.ui.components.TopBar
import com.mobile.safedrive.ui.theme.Palette
import com.mobile.safedrive.ui.theme.Radius
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText

@Composable
fun TripSummaryScreen(session: DrivingSession?, onBack: () -> Unit, onViewOnMap: (List<RoutePoint>) -> Unit) {
    val c = SdTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar(
            "Trip Summary",
            onBack = onBack,
            subtitle = session?.let { formatTripDate(it.startTime) } ?: "Saving trip…",
            horizontalPadding = 24.dp,
            verticalPadding = 16.dp,
            bottomBorder = true,
        )
        if (session == null) return@Column
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // Safety score
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Safety Score", style = sdText(TextSize.base, FontWeight.SemiBold), color = c.textStrong)
                Spacer(Modifier.height(16.dp))
                val score = session.safetyScore
                ProgressRing(
                    progress = score / 100f,
                    modifier = Modifier.size(154.dp),
                    color = c.primary,
                    trackColor = c.ringTrack,
                    strokeWidth = 15.dp,
                    trackWidth = 20.dp,
                    roundCap = true,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(score.toString(), style = sdText(40.sp, FontWeight.Bold, lineHeight = 44.sp), color = c.textPrimary)
                        Text("/100", style = sdText(TextSize.xs), color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                val rating = ratingFor(score)
                Text(rating.label, style = sdText(TextSize.sm, FontWeight.Medium), color = ratingColor(rating))
            }
            HorizontalDivider(thickness = 1.dp, color = c.border)
            // Stats grid
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryStat("Duration", formatDuration(session.durationMs), Modifier.weight(1f))
                    SummaryStat("Distance", formatDistance(session.distanceKm), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SummaryStat("Start Time", formatTime(session.startTime), Modifier.weight(1f))
                    SummaryStat("End Time", formatTime(session.endTime), Modifier.weight(1f))
                }
            }
            HorizontalDivider(thickness = 1.dp, color = c.border)
            // Alerts
            Column(Modifier.padding(24.dp)) {
                Text("Alerts", style = sdText(TextSize.sm, FontWeight.SemiBold), color = c.textStrong)
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AlertCountRow(c.warningSoft, "Drowsiness", session.drowsyAlerts)
                    AlertCountRow(c.danger, "Critical", session.criticalAlerts)
                    AlertCountRow(c.accent, "Stay Alert", session.attentionAlerts)
                }
            }
            HorizontalDivider(thickness = 1.dp, color = c.border)
            // Route map
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 32.dp)) {
                Text("Route Map", style = sdText(TextSize.sm, FontWeight.SemiBold), color = c.textStrong)
                Spacer(Modifier.height(12.dp))
                Text(
                    "View on map",
                    style = sdText(TextSize.xs),
                    color = c.primary,
                    modifier = Modifier.clickable(enabled = session.route.size >= 2) { onViewOnMap(session.route) },
                )
                Spacer(Modifier.height(16.dp))
                RouteMap(session.route)
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier) {
    val c = SdTheme.colors
    Column(modifier) {
        Text(label, style = sdText(TextSize.xs), color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = sdText(TextSize.base, FontWeight.SemiBold), color = c.textPrimary)
    }
}

@Composable
private fun AlertCountRow(dot: Color, label: String, count: Int) {
    val c = SdTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(12.dp))
        Text(label, style = sdText(TextSize.sm), color = c.textBody, modifier = Modifier.weight(1f))
        Text(count.toString(), style = sdText(TextSize.sm, FontWeight.Medium), color = c.textPrimary)
    }
}

/** Route drawn from locally stored GPS points — no map tiles are fetched. */
@Composable
private fun RouteMap(route: List<RoutePoint>) {
    val c = SdTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(Radius.lg)
            .background(c.primaryTint)
            .border(1.dp, c.border, Radius.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (route.size < 2) {
            Text("No route recorded", style = sdText(TextSize.xs), color = c.textTertiary)
            return@Box
        }
        Canvas(Modifier.fillMaxSize().padding(24.dp)) {
            val minLat = route.minOf { it.lat }
            val maxLat = route.maxOf { it.lat }
            val minLng = route.minOf { it.lng }
            val maxLng = route.maxOf { it.lng }
            val spanLat = (maxLat - minLat).takeIf { it > 0 } ?: 1.0
            val spanLng = (maxLng - minLng).takeIf { it > 0 } ?: 1.0
            fun project(p: RoutePoint) = Offset(
                ((p.lng - minLng) / spanLng).toFloat() * size.width,
                (1f - ((p.lat - minLat) / spanLat).toFloat()) * size.height,
            )
            val path = Path()
            route.forEachIndexed { i, p ->
                val o = project(p)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(
                path, Palette.Blue400.copy(alpha = 0.8f),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            val dot = Size(16.dp.toPx(), 10.dp.toPx())
            val start = project(route.first())
            val end = project(route.last())
            drawOval(Palette.Blue400, Offset(start.x - dot.width / 2, start.y - dot.height / 2), dot)
            drawOval(Palette.Red500.copy(alpha = 0.6f), Offset(end.x - dot.width / 2, end.y - dot.height / 2), dot)
        }
    }
}
