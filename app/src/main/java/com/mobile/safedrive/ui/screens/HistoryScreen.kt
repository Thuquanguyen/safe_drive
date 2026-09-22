package com.mobile.safedrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.safedrive.session.DrivingSession
import com.mobile.safedrive.session.ratingFor
import com.mobile.safedrive.ui.components.BottomNavBar
import com.mobile.safedrive.ui.components.HomeTab
import com.mobile.safedrive.ui.components.IconCircle
import com.mobile.safedrive.ui.components.TopBar
import com.mobile.safedrive.ui.theme.Radius
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText

private enum class HistoryRange(val label: String) {
    THIS_WEEK("This Week"), LAST_WEEK("Last Week"), THIS_MONTH("This Month"), ALL("All Time")
}

private val WEEKDAYS = listOf("M", "T", "W", "T", "F", "S", "S")
private const val MIN_CHART_MAX = 4
private const val DAYS_PER_WEEK = 7

@Composable
fun HistoryScreen(
    sessions: List<DrivingSession>,
    onBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onTab: (HomeTab) -> Unit,
) {
    val c = SdTheme.colors
    var range by rememberSaveable { mutableStateOf(HistoryRange.THIS_WEEK) }
    val filtered = filter(sessions, range)

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        TopBar("Driving History", onBack = onBack, horizontalPadding = 20.dp, verticalPadding = 16.dp)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            RangeDropdown(range) { range = it }
            Spacer(Modifier.height(24.dp))
            Text("Summary", style = sdText(TextSize.base, FontWeight.SemiBold), color = c.textPrimary)
            Spacer(Modifier.height(16.dp))
            TripsChart(filtered)
            Spacer(Modifier.height(32.dp))
            Text("Trips", style = sdText(TextSize.base, FontWeight.SemiBold), color = c.textPrimary)
            Spacer(Modifier.height(16.dp))
            if (filtered.isEmpty()) {
                Text("No trips yet. Start a drive to see it here.", style = sdText(TextSize.sm), color = c.textSecondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                filtered.forEach { TripRow(it) { onOpenTrip(it.sessionId) } }
            }
            Spacer(Modifier.height(16.dp))
        }
        BottomNavBar(HomeTab.History, onTab)
    }
}

private fun filter(sessions: List<DrivingSession>, range: HistoryRange): List<DrivingSession> {
    val now = System.currentTimeMillis()
    val thisWeek = startOfWeek(now)
    return when (range) {
        HistoryRange.THIS_WEEK -> sessions.filter { it.startTime >= thisWeek }
        HistoryRange.LAST_WEEK -> sessions.filter { it.startTime in (thisWeek - DAYS_PER_WEEK * DAY_MS) until thisWeek }
        HistoryRange.THIS_MONTH -> sessions.filter { it.startTime >= startOfMonth(now) }
        HistoryRange.ALL -> sessions
    }
}

@Composable
private fun RangeDropdown(range: HistoryRange, onSelect: (HistoryRange) -> Unit) {
    val c = SdTheme.colors
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(1.dp, Radius.lg)
                .clip(Radius.lg)
                .background(c.card)
                .border(1.dp, c.borderStrong, Radius.lg)
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(range.label, style = sdText(TextSize.sm, FontWeight.Medium), color = c.textBody, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            HistoryRange.entries.forEach { option ->
                DropdownMenuItem(text = { Text(option.label) }, onClick = { open = false; onSelect(option) })
            }
        }
    }
}

/** Trips per weekday; days with trips in blue-600, empty days in blue-100 (design bars). */
@Composable
private fun TripsChart(sessions: List<DrivingSession>) {
    val c = SdTheme.colors
    val counts = IntArray(DAYS_PER_WEEK)
    sessions.forEach { counts[weekdayIndex(it.startTime)]++ }
    val rawMax = maxOf(MIN_CHART_MAX, counts.max())
    val max = if (rawMax % 2 == 0) rawMax else rawMax + 1
    val barArea = 80.dp

    Column {
        Row(Modifier.fillMaxWidth().height(104.dp)) {
            Column(
                Modifier.fillMaxHeight().padding(bottom = 24.dp).width(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(max, max / 2, 0).forEach {
                    Text(it.toString(), style = sdText(TextSize.xs), color = c.textTertiary)
                }
            }
            Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                counts.forEachIndexed { index, count ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(barArea * (count.toFloat() / max))
                                .then(if (count > 0) Modifier.shadow(1.dp, TopRounded) else Modifier)
                                .clip(TopRounded)
                                .background(if (count > 0) c.primary else c.primaryTint2),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(WEEKDAYS[index], style = sdText(TextSize.xs, FontWeight.Medium), color = c.textSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(0.dp))
        HorizontalDivider(thickness = 1.dp, color = c.border)
    }
}

private val TopRounded = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)

@Composable
private fun TripRow(session: DrivingSession, onClick: () -> Unit) {
    val c = SdTheme.colors
    val rating = ratingFor(session.safetyScore)
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, Radius.xl)
            .clip(Radius.xl)
            .background(c.card)
            .border(1.dp, c.border, Radius.xl)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(Icons.Filled.DirectionsCar, iconSize = 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(formatRelativeDay(session.startTime), style = sdText(TextSize.sm, FontWeight.SemiBold), color = c.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatDuration(session.durationMs)} • ${formatDistance(session.distanceKm)}",
                style = sdText(TextSize.xs),
                color = c.textSecondary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(session.safetyScore.toString(), style = sdText(TextSize.lg, FontWeight.Bold), color = c.textStrong)
            Text(rating.label, style = sdText(TextSize.xs, FontWeight.Medium), color = ratingColor(rating))
        }
    }
}
