package com.mobile.safedrive.drowsiness

import android.content.Context

/**
 * Collects ~12 s of neutral-gaze samples and derives a numeric-only profile (spec §4, §42).
 * No image is kept — only medians of EAR, MAR, pitch and yaw.
 */
class CalibrationManager(context: Context) {
    private val prefs = context.getSharedPreferences("calibration", Context.MODE_PRIVATE)

    private val ear = FloatArray(MAX_SAMPLES)
    private val mar = FloatArray(MAX_SAMPLES)
    private val pitch = FloatArray(MAX_SAMPLES)
    private val yaw = FloatArray(MAX_SAMPLES)
    private var count = 0

    val sampleCount: Int get() = count

    fun hasProfile(): Boolean = prefs.contains(KEY_EAR)

    fun load(): CalibrationProfile {
        if (!hasProfile()) return CalibrationProfile.DEFAULT
        return CalibrationProfile(
            baselineEar = prefs.getFloat(KEY_EAR, CalibrationProfile.DEFAULT.baselineEar),
            eyeClosedThreshold = prefs.getFloat(KEY_THRESHOLD, CalibrationProfile.DEFAULT.eyeClosedThreshold),
            baselineMar = prefs.getFloat(KEY_MAR, CalibrationProfile.DEFAULT.baselineMar),
            baselinePitch = prefs.getFloat(KEY_PITCH, 0f),
            baselineYaw = prefs.getFloat(KEY_YAW, 0f),
        )
    }

    fun begin() {
        count = 0
    }

    fun addSample(metrics: FaceMetrics) {
        if (count >= MAX_SAMPLES) return
        ear[count] = metrics.averageEar
        mar[count] = metrics.mar
        pitch[count] = metrics.pitch
        yaw[count] = metrics.yaw
        count++
    }

    /** Returns the new profile, or null if too few face samples were collected. */
    fun finish(): CalibrationProfile? {
        if (count < MIN_SAMPLES) return null
        // Upper-quartile EAR ignores blinks that happened during calibration.
        val openEar = quantile(ear, OPEN_EYE_QUANTILE)
        val profile = CalibrationProfile(
            baselineEar = openEar,
            eyeClosedThreshold = (openEar * CLOSED_RATIO).coerceIn(MIN_CLOSED_EAR, MAX_CLOSED_EAR),
            baselineMar = quantile(mar, MEDIAN),
            baselinePitch = quantile(pitch, MEDIAN),
            baselineYaw = quantile(yaw, MEDIAN),
        )
        prefs.edit()
            .putFloat(KEY_EAR, profile.baselineEar)
            .putFloat(KEY_THRESHOLD, profile.eyeClosedThreshold)
            .putFloat(KEY_MAR, profile.baselineMar)
            .putFloat(KEY_PITCH, profile.baselinePitch)
            .putFloat(KEY_YAW, profile.baselineYaw)
            .apply()
        return profile
    }

    private fun quantile(values: FloatArray, q: Float): Float {
        val sorted = values.copyOf(count).also { it.sort() }
        return sorted[((sorted.size - 1) * q).toInt()]
    }

    companion object {
        const val DURATION_MS = 12_000L
        private const val MAX_SAMPLES = 256
        private const val MIN_SAMPLES = 15
        private const val OPEN_EYE_QUANTILE = 0.75f
        private const val MEDIAN = 0.5f
        private const val CLOSED_RATIO = 0.65f
        private const val MIN_CLOSED_EAR = 0.12f
        private const val MAX_CLOSED_EAR = 0.25f
        private const val KEY_EAR = "baseline_ear"
        private const val KEY_THRESHOLD = "eye_closed_threshold"
        private const val KEY_MAR = "baseline_mar"
        private const val KEY_PITCH = "baseline_pitch"
        private const val KEY_YAW = "baseline_yaw"
    }
}
