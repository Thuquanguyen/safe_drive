package com.mobile.safedrive.drowsiness

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Central brain: Face → Eyes → EAR → temporal eye analysis → PERCLOS → mouth → head pose
 * → multi-signal fusion → score → state machine with hysteresis (spec §55).
 * A single frame never produces an alert.
 */
class DrowsinessEngine(
    private var config: DrowsinessConfig,
    private var profile: CalibrationProfile,
) {
    private val temporal = TemporalAnalyzer()
    private val longBlinks = EventRing(EVENT_CAPACITY)
    private val longClosures = EventRing(EVENT_CAPACITY)
    private val yawns = EventRing(EVENT_CAPACITY)
    private val nods = EventRing(EVENT_CAPACITY)

    private var state = DrowsinessState.NORMAL
    private var eyesClosedSince = 0L
    private var mouthOpenSince = 0L
    private var headDownSince = 0L
    private var faceMissingSince = 0L
    private var lowLightSince = 0L
    private var pendingTarget = DrowsinessState.NORMAL
    private var pendingSince = 0L
    private var lastMetrics = DrowsinessMetrics()

    val currentState: DrowsinessState get() = state

    fun update(config: DrowsinessConfig, profile: CalibrationProfile) {
        this.config = config
        this.profile = profile
    }

    fun reset() {
        temporal.clear()
        longBlinks.clear(); longClosures.clear(); yawns.clear(); nods.clear()
        state = DrowsinessState.NORMAL
        eyesClosedSince = 0; mouthOpenSince = 0; headDownSince = 0
        faceMissingSince = 0; lowLightSince = 0
        pendingTarget = DrowsinessState.NORMAL; pendingSince = 0
        lastMetrics = DrowsinessMetrics()
    }

    fun process(obs: FrameObservation): EngineResult {
        val now = obs.timestampMs
        if (!obs.faceDetected) {
            if (faceMissingSince == 0L) faceMissingSince = now
            resetInstantTrackers()
            val condition = if (now - faceMissingSince >= config.faceLostGraceMs) {
                MonitorCondition.FACE_NOT_DETECTED
            } else {
                MonitorCondition.OK
            }
            return EngineResult(state, lastMetrics, 0f, condition, suspicious = false)
        }
        faceMissingSince = 0L

        val m = obs.metrics
        val lowLight = trackLowLight(obs.luminance, now)
        val faceAway = abs(m.yaw - profile.baselineYaw) > config.faceAwayYawDeg
        val eyeConfidence = eyeConfidence(m, lowLight)
        val eyesReliable = !faceAway && eyeConfidence >= config.minEyeConfidence

        if (eyesReliable) {
            val closed = m.averageEar < profile.eyeClosedThreshold
            temporal.add(now, closed)
            trackEyeClosure(closed, now)
        } else {
            eyesClosedSince = 0L
        }
        if (!faceAway) {
            trackYawn(m.mar, now)
            trackHead(m.pitch - profile.baselinePitch, now)
        }

        val closureMs = if (eyesClosedSince > 0) now - eyesClosedSince else 0L
        val perclos = temporal.perclos(now, config.perclosWindowMs)
        val longBlinkCount = longBlinks.countSince(now - config.longBlinkWindowMs)
        val yawnCount = yawns.countSince(now - config.yawnWindowMs)
        val nodCount = nods.countSince(now - config.nodWindowMs)
        val headDownSustained = headDownSince > 0 && now - headDownSince >= config.headDownHoldMs

        val metrics = fuse(closureMs, perclos, longBlinkCount, yawnCount, nodCount, headDownSustained)
        lastMetrics = metrics

        val condition = when {
            obs.faceCount > 1 -> MonitorCondition.MULTIPLE_FACES
            faceAway -> MonitorCondition.FACE_AWAY
            lowLight -> MonitorCondition.LOW_LIGHT
            !eyesReliable -> MonitorCondition.LOW_CONFIDENCE
            else -> MonitorCondition.OK
        }

        // Low-quality input must not push the driver into a stronger alert (spec §23, §25).
        if (eyesReliable) {
            val signals = countSignals(closureMs, now, perclos, longBlinkCount, yawnCount, nodCount, headDownSustained)
            val target = targetState(metrics.totalScore, closureMs, perclos, longBlinkCount, signals)
            applyHysteresis(target, now)
        }

        val suspicious = state >= DrowsinessState.ATTENTION ||
            eyesClosedSince > 0 ||
            m.averageEar < profile.baselineEar * PARTIAL_CLOSURE_RATIO ||
            abs(m.pitch - profile.baselinePitch) > config.headNodThreshold / 2
        return EngineResult(state, metrics, eyeConfidence, condition, suspicious)
    }

    private fun resetInstantTrackers() {
        eyesClosedSince = 0L
        mouthOpenSince = 0L
        headDownSince = 0L
    }

    private fun trackLowLight(luminance: Float, now: Long): Boolean {
        if (luminance >= config.lowLightLuma) {
            lowLightSince = 0L
            return false
        }
        if (lowLightSince == 0L) lowLightSince = now
        return now - lowLightSince >= config.lowLightHoldMs
    }

    /** Glasses glare / low light make eye landmarks unreliable: lower confidence instead of alarming. */
    private fun eyeConfidence(m: FaceMetrics, lowLight: Boolean): Float {
        var confidence = m.faceConfidence
        if (lowLight) confidence *= LOW_LIGHT_CONFIDENCE_FACTOR
        if (abs(m.leftEar - m.rightEar) > EYE_ASYMMETRY_LIMIT) confidence *= ASYMMETRY_CONFIDENCE_FACTOR
        return confidence
    }

    private fun trackEyeClosure(closed: Boolean, now: Long) {
        if (closed) {
            if (eyesClosedSince == 0L) eyesClosedSince = now
            return
        }
        if (eyesClosedSince > 0) {
            val duration = now - eyesClosedSince
            when {
                duration >= config.eyeClosedDurationMs -> longClosures.add(now)
                duration >= config.longBlinkMs -> longBlinks.add(now)
            }
            eyesClosedSince = 0L
        }
    }

    private fun trackYawn(mar: Float, now: Long) {
        val threshold = max(config.yawnThreshold, profile.baselineMar * YAWN_BASELINE_MULTIPLIER)
        if (mar > threshold) {
            if (mouthOpenSince == 0L) mouthOpenSince = now
        } else if (mouthOpenSince > 0) {
            if (now - mouthOpenSince >= config.yawnMinDurationMs) yawns.add(now)
            mouthOpenSince = 0L
        }
    }

    private fun trackHead(pitchDelta: Float, now: Long) {
        val down = abs(pitchDelta) > config.headNodThreshold
        if (down) {
            if (headDownSince == 0L) headDownSince = now
        } else if (headDownSince > 0 && abs(pitchDelta) < config.headNodThreshold / 2) {
            if (now - headDownSince <= config.nodMaxDurationMs) nods.add(now)
            headDownSince = 0L
        }
    }

    private fun fuse(
        closureMs: Long,
        perclos: Float,
        longBlinkCount: Int,
        yawnCount: Int,
        nodCount: Int,
        headDownSustained: Boolean,
    ): DrowsinessMetrics {
        val eye = clamp01(closureMs.toFloat() / config.criticalEyeClosedDurationMs)
        val perclosScore = clamp01(perclos / config.perclosCriticalThreshold)
        val blink = clamp01(longBlinkCount.toFloat() / config.longBlinksForMaxScore)
        val yawn = clamp01(yawnCount.toFloat() / config.yawnsForMaxScore)
        val head = clamp01(nodCount.toFloat() / config.nodsForMaxScore + if (headDownSustained) HEAD_DOWN_BONUS else 0f)
        val total = SCORE_SCALE * (
            config.eyeWeight * eye + config.perclosWeight * perclosScore + config.blinkWeight * blink +
                config.yawnWeight * yawn + config.headWeight * head
            )
        return DrowsinessMetrics(eye, perclosScore, blink, yawn, head, total)
    }

    private fun countSignals(
        closureMs: Long,
        now: Long,
        perclos: Float,
        longBlinkCount: Int,
        yawnCount: Int,
        nodCount: Int,
        headDownSustained: Boolean,
    ): Int {
        var signals = 0
        if (closureMs >= config.eyeClosedDurationMs || longClosures.countSince(now - RECENT_CLOSURE_MS) > 0) signals++
        if (perclos >= config.perclosDrowsyThreshold) signals++
        if (nodCount > 0 || headDownSustained) signals++
        if (yawnCount >= REPEATED_YAWNS) signals++
        if (longBlinkCount >= REPEATED_LONG_BLINKS) signals++
        return signals
    }

    /** Multi-signal confirmation (spec §46); recovery thresholds are lower than trigger ones (§45). */
    private fun targetState(
        score: Float,
        closureMs: Long,
        perclos: Float,
        longBlinkCount: Int,
        signals: Int,
    ): DrowsinessState {
        fun threshold(level: DrowsinessState, value: Float) =
            if (state >= level) value - config.recoveryScoreMargin else value

        return when {
            closureMs >= config.criticalEyeClosedDurationMs -> DrowsinessState.CRITICAL
            signals >= 2 && (score >= threshold(DrowsinessState.CRITICAL, config.criticalScore) ||
                perclos >= config.perclosCriticalThreshold) -> DrowsinessState.CRITICAL
            signals >= 2 || (signals >= 1 && score >= threshold(DrowsinessState.DROWSY, config.drowsyScore)) ->
                DrowsinessState.DROWSY
            signals >= 1 || longBlinkCount > 0 || perclos >= config.perclosAttentionThreshold ||
                score >= threshold(DrowsinessState.ATTENTION, config.attentionScore) -> DrowsinessState.ATTENTION
            else -> DrowsinessState.NORMAL
        }
    }

    private fun applyHysteresis(target: DrowsinessState, now: Long) {
        if (target == state) {
            pendingSince = 0L
            return
        }
        if (target != pendingTarget || pendingSince == 0L) {
            pendingTarget = target
            pendingSince = now
        }
        val heldMs = now - pendingSince
        if (target > state) {
            val required = when (target) {
                DrowsinessState.CRITICAL -> 0L
                DrowsinessState.DROWSY -> config.drowsyEscalateHoldMs
                else -> config.attentionEscalateHoldMs
            }
            if (heldMs >= required) {
                state = target
                pendingSince = 0L
            }
        } else if (heldMs >= config.recoveryHoldMs) {
            // Never CRITICAL → NORMAL in one go: step down one level at a time (spec §31).
            state = DrowsinessState.entries[state.ordinal - 1]
            pendingSince = now
        }
    }

    private fun clamp01(v: Float) = min(1f, max(0f, v))

    private companion object {
        const val EVENT_CAPACITY = 64
        const val SCORE_SCALE = 100f
        const val PARTIAL_CLOSURE_RATIO = 0.85f
        const val LOW_LIGHT_CONFIDENCE_FACTOR = 0.5f
        const val EYE_ASYMMETRY_LIMIT = 0.12f
        const val ASYMMETRY_CONFIDENCE_FACTOR = 0.6f
        const val YAWN_BASELINE_MULTIPLIER = 4f
        const val HEAD_DOWN_BONUS = 0.5f
        const val RECENT_CLOSURE_MS = 30_000L
        const val REPEATED_YAWNS = 2
        const val REPEATED_LONG_BLINKS = 3
    }
}
