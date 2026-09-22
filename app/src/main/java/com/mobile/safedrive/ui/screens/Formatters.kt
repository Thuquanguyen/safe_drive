package com.mobile.safedrive.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mobile.safedrive.session.ScoreRating
import com.mobile.safedrive.ui.theme.SdTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val SECONDS_PER_MINUTE = 60L
private const val MS_PER_SECOND = 1_000L
private const val ONE_DECIMAL_BELOW_KM = 10f

/** "2h 45m" / "45m". */
fun formatDuration(ms: Long): String {
    val totalMinutes = ms / MS_PER_MINUTE
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/** "01:26:04". */
fun formatClock(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / MS_PER_SECOND
    val h = totalSeconds / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
    val m = (totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
    val s = totalSeconds % SECONDS_PER_MINUTE
    return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
}

/** "112 km" / "8.4 km". */
fun formatDistance(km: Float): String =
    if (km >= ONE_DECIMAL_BELOW_KM) "${km.roundToInt()} km" else String.format(Locale.US, "%.1f km", km)

fun formatTime(epochMs: Long): String = SimpleDateFormat("hh:mm a", Locale.US).format(Date(epochMs))

/** "Sun, Mar 24, 2024 • 08:30 AM". */
fun formatTripDate(epochMs: Long): String =
    SimpleDateFormat("EEE, MMM d, yyyy '•' hh:mm a", Locale.US).format(Date(epochMs))

/** "Today, 08:30 AM" / "Yesterday, 11:15 AM" / "Mar 22, 07:45 PM". */
fun formatRelativeDay(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val today = startOfDay(now)
    val time = formatTime(epochMs)
    return when {
        epochMs >= today -> "Today, $time"
        epochMs >= today - DAY_MS -> "Yesterday, $time"
        else -> SimpleDateFormat("MMM d, ", Locale.US).format(Date(epochMs)) + time
    }
}

const val DAY_MS = 24 * 60 * 60 * 1_000L

fun startOfDay(epochMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = epochMs
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Monday 00:00 of the week containing [epochMs]. */
fun startOfWeek(epochMs: Long): Long = Calendar.getInstance().apply {
    firstDayOfWeek = Calendar.MONDAY
    timeInMillis = startOfDay(epochMs)
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    if (timeInMillis > epochMs) add(Calendar.WEEK_OF_YEAR, -1)
}.timeInMillis

fun startOfMonth(epochMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(epochMs)
    set(Calendar.DAY_OF_MONTH, 1)
}.timeInMillis

/** 0 = Monday … 6 = Sunday. */
fun weekdayIndex(epochMs: Long): Int {
    val day = Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.DAY_OF_WEEK)
    return (day + 5) % 7
}

@Composable
fun ratingColor(rating: ScoreRating): Color {
    val c = SdTheme.colors
    return when (rating) {
        ScoreRating.EXCELLENT, ScoreRating.GOOD -> c.success
        ScoreRating.FAIR -> c.warning
        ScoreRating.POOR -> c.danger
    }
}
