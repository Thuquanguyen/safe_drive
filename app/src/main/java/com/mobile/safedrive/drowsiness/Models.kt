package com.mobile.safedrive.drowsiness

enum class DrowsinessState { NORMAL, ATTENTION, DROWSY, CRITICAL }

/** Why the engine can or cannot currently trust the camera signal. */
enum class MonitorCondition { OK, FACE_NOT_DETECTED, FACE_AWAY, MULTIPLE_FACES, LOW_LIGHT, LOW_CONFIDENCE }

/**
 * Per-frame face measurements. Mutable and reused across frames so the
 * analysis loop does not allocate (spec §37 "Zero Allocation Loop").
 */
class FaceMetrics {
    var faceConfidence = 0f
    var leftEar = 0f
    var rightEar = 0f
    var averageEar = 0f
    var mar = 0f
    var pitch = 0f
    var yaw = 0f
    var roll = 0f
}

/** One analysed camera frame. Reused by the analyzer. */
class FrameObservation {
    var timestampMs = 0L
    var faceCount = 0
    var luminance = 0f
    val metrics = FaceMetrics()
    val faceDetected: Boolean get() = faceCount > 0
}

data class DrowsinessMetrics(
    val eyeScore: Float = 0f,
    val perclosScore: Float = 0f,
    val blinkScore: Float = 0f,
    val yawnScore: Float = 0f,
    val headScore: Float = 0f,
    val totalScore: Float = 0f,
)

data class EngineResult(
    val state: DrowsinessState,
    val metrics: DrowsinessMetrics,
    val confidence: Float,
    val condition: MonitorCondition,
    /** True when an early sign is seen and the FPS gate should burst to verify it. */
    val suspicious: Boolean,
)

data class CalibrationProfile(
    val baselineEar: Float,
    val eyeClosedThreshold: Float,
    val baselineMar: Float,
    val baselinePitch: Float,
    val baselineYaw: Float,
) {
    companion object {
        /** Population defaults used until the driver calibrates. */
        val DEFAULT = CalibrationProfile(
            baselineEar = 0.28f,
            eyeClosedThreshold = 0.19f,
            baselineMar = 0.05f,
            baselinePitch = 0f,
            baselineYaw = 0f,
        )
    }
}

enum class MotionActivity { UNKNOWN, STILL, WALKING, IN_VEHICLE }

data class DrivingMotionInfo(
    val activity: MotionActivity,
    val speedKmh: Float,
    val isVehicleConfirmed: Boolean,
    val isAutoStarted: Boolean,
)

enum class PowerMode {
    FOREGROUND_PREVIEW,          // screen on: 5–8 FPS
    BACKGROUND_POWERSAVE,        // screen off: 2–3 FPS, headless
    BACKGROUND_BURST_EVALUATION, // screen off: 8–10 FPS burst to verify a suspicion
}

data class FpsStrategy(
    val targetFps: Int,
    val minIntervalMs: Long,
    val isHeadless: Boolean,
)
