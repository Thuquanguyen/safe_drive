package com.mobile.safedrive.drowsiness

import com.mobile.safedrive.settings.AppSettings
import com.mobile.safedrive.settings.Sensitivity

/** All tunable thresholds in one place (spec §44). Nothing in the engine is hard-coded. */
data class DrowsinessConfig(
    val eyeClosedDurationMs: Long = 1_500L,
    val criticalEyeClosedDurationMs: Long = 3_000L,
    val perclosAttentionThreshold: Float = 0.12f,
    val perclosDrowsyThreshold: Float = 0.20f,
    val perclosCriticalThreshold: Float = 0.35f,
    val yawnThreshold: Float = 0.55f,
    val headNodThreshold: Float = 15f,
    val alertCooldownMs: Long = 15_000L,
    // Auto driving detection
    val autoStartSpeedThresholdKmh: Float = 25.0f,
    val autoStartConfirmDurationSec: Int = 20,
    val autoStopGracePeriodMs: Long = 300_000L,
    val stoppedSpeedKmh: Float = 5f,
    // Battery & adaptive FPS
    val foregroundFps: Int = 6,
    val backgroundNormalFps: Int = 3,
    val backgroundBurstFps: Int = 8,
    val burstEvaluationDurationMs: Long = 2_000L,
    // Temporal analysis
    val longBlinkMs: Long = 400L,
    val yawnMinDurationMs: Long = 1_500L,
    val nodMaxDurationMs: Long = 2_000L,
    val headDownHoldMs: Long = 2_500L,
    val perclosWindowMs: Long = 60_000L,
    val longBlinkWindowMs: Long = 60_000L,
    val yawnWindowMs: Long = 300_000L,
    val nodWindowMs: Long = 120_000L,
    val longBlinksForMaxScore: Int = 5,
    val yawnsForMaxScore: Int = 3,
    val nodsForMaxScore: Int = 3,
    // Signal quality
    val faceLostGraceMs: Long = 3_000L,
    val faceAwayYawDeg: Float = 35f,
    val lowLightLuma: Float = 45f,
    val lowLightHoldMs: Long = 3_000L,
    val minEyeConfidence: Float = 0.5f,
    // Score fusion — eyes/PERCLOS weigh more than secondary features (spec §15)
    val eyeWeight: Float = 0.35f,
    val perclosWeight: Float = 0.30f,
    val blinkWeight: Float = 0.12f,
    val yawnWeight: Float = 0.10f,
    val headWeight: Float = 0.13f,
    val attentionScore: Float = 25f,
    val drowsyScore: Float = 50f,
    val criticalScore: Float = 75f,
    // Hysteresis (spec §45): recovery thresholds sit below trigger thresholds
    val recoveryScoreMargin: Float = 10f,
    val recoveryHoldMs: Long = 5_000L,
    val attentionEscalateHoldMs: Long = 1_000L,
    val drowsyEscalateHoldMs: Long = 500L,
) {
    companion object {
        private const val HIGH_SENSITIVITY = 0.8f
        private const val LOW_SENSITIVITY = 1.25f

        fun from(settings: AppSettings): DrowsinessConfig {
            val factor = when (settings.sensitivity) {
                Sensitivity.HIGH -> HIGH_SENSITIVITY
                Sensitivity.NORMAL -> 1f
                Sensitivity.LOW -> LOW_SENSITIVITY
            }
            val base = DrowsinessConfig()
            return base.copy(
                eyeClosedDurationMs = (base.eyeClosedDurationMs * factor).toLong(),
                criticalEyeClosedDurationMs = (base.criticalEyeClosedDurationMs * factor).toLong(),
                perclosAttentionThreshold = base.perclosAttentionThreshold * factor,
                perclosDrowsyThreshold = base.perclosDrowsyThreshold * factor,
                perclosCriticalThreshold = base.perclosCriticalThreshold * factor,
                autoStartSpeedThresholdKmh = settings.autoStartSpeedKmh.toFloat(),
                autoStopGracePeriodMs = settings.gracePeriodMinutes * 60_000L,
            )
        }
    }
}
