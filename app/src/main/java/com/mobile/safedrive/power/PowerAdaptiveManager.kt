package com.mobile.safedrive.power

import com.mobile.safedrive.drowsiness.DrowsinessConfig
import com.mobile.safedrive.drowsiness.DrowsinessState
import com.mobile.safedrive.drowsiness.FpsStrategy
import com.mobile.safedrive.drowsiness.PowerMode

/**
 * Adaptive FPS (spec §5, §37): 5–8 FPS with the screen on, 2–3 FPS headless when NORMAL,
 * and an 8–10 FPS burst for [DrowsinessConfig.burstEvaluationDurationMs] to verify a suspicion.
 */
class PowerAdaptiveManager(private var config: DrowsinessConfig) {

    @Volatile var screenOn: Boolean = true
    @Volatile var ultraSaver: Boolean = true
    @Volatile var stoppedInGrace: Boolean = false
    @Volatile private var burstUntilMs = 0L

    fun update(config: DrowsinessConfig) {
        this.config = config
    }

    fun onEvaluation(state: DrowsinessState, suspicious: Boolean, nowMs: Long) {
        if (suspicious || state != DrowsinessState.NORMAL) {
            burstUntilMs = nowMs + config.burstEvaluationDurationMs
        }
    }

    fun mode(nowMs: Long): PowerMode = when {
        screenOn -> PowerMode.FOREGROUND_PREVIEW
        nowMs < burstUntilMs -> PowerMode.BACKGROUND_BURST_EVALUATION
        else -> PowerMode.BACKGROUND_POWERSAVE
    }

    fun strategy(nowMs: Long): FpsStrategy {
        val fps = when (mode(nowMs)) {
            PowerMode.FOREGROUND_PREVIEW -> config.foregroundFps
            PowerMode.BACKGROUND_BURST_EVALUATION -> config.backgroundBurstFps
            PowerMode.BACKGROUND_POWERSAVE -> when {
                stoppedInGrace -> GRACE_FPS
                ultraSaver -> config.backgroundNormalFps
                else -> config.foregroundFps
            }
        }
        return FpsStrategy(targetFps = fps, minIntervalMs = MS_PER_SECOND / fps, isHeadless = !screenOn)
    }

    fun minIntervalMs(nowMs: Long): Long = MS_PER_SECOND / strategy(nowMs).targetFps

    private companion object {
        const val MS_PER_SECOND = 1_000L
        const val GRACE_FPS = 2
    }
}
