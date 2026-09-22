package com.mobile.safedrive.session

import com.mobile.safedrive.drowsiness.DrowsinessState

data class RoutePoint(val lat: Double, val lng: Double)

/** Statistics-only record of one trip. No images, video or landmarks are ever stored. */
data class DrivingSession(
    val sessionId: String,
    val startTime: Long,
    val endTime: Long,
    val distanceKm: Float,
    val maxDrowsinessLevel: DrowsinessState,
    val attentionAlerts: Int,
    val drowsyAlerts: Int,
    val criticalAlerts: Int,
    val autoStarted: Boolean,
    val route: List<RoutePoint>,
) {
    val durationMs: Long get() = (endTime - startTime).coerceAtLeast(0)
    val numberOfAlerts: Int get() = attentionAlerts + drowsyAlerts + criticalAlerts

    /** 0–100 safety score derived from alerts, weighted by severity. */
    val safetyScore: Int
        get() = (MAX_SCORE - attentionAlerts * ATTENTION_PENALTY - drowsyAlerts * DROWSY_PENALTY -
            criticalAlerts * CRITICAL_PENALTY).coerceIn(0, MAX_SCORE)

    companion object {
        const val MAX_SCORE = 100
        private const val ATTENTION_PENALTY = 2
        private const val DROWSY_PENALTY = 6
        private const val CRITICAL_PENALTY = 15
    }
}

enum class ScoreRating(val label: String) { EXCELLENT("Excellent"), GOOD("Good"), FAIR("Fair"), POOR("Poor") }

fun ratingFor(score: Int): ScoreRating = when {
    score >= 92 -> ScoreRating.EXCELLENT
    score >= 80 -> ScoreRating.GOOD
    score >= 60 -> ScoreRating.FAIR
    else -> ScoreRating.POOR
}
