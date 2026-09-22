package com.mobile.safedrive.drowsiness

/** Fixed-capacity ring of event timestamps. Pre-allocated; no per-frame allocation. */
class EventRing(capacity: Int) {
    private val times = LongArray(capacity)
    private var head = 0
    private var size = 0

    fun add(timestampMs: Long) {
        times[head] = timestampMs
        head = (head + 1) % times.size
        if (size < times.size) size++
    }

    fun countSince(sinceMs: Long): Int {
        var count = 0
        for (i in 0 until size) if (times[i] >= sinceMs) count++
        return count
    }

    fun clear() {
        head = 0
        size = 0
    }
}

/**
 * Sliding-window eye-state time series used for PERCLOS (spec §8, §16).
 * Each sample is weighted by the time until the next sample, so a burst at 8 FPS
 * and power-save at 2–3 FPS contribute correctly.
 */
class TemporalAnalyzer(capacity: Int = SAMPLE_CAPACITY) {
    private val timestamps = LongArray(capacity)
    private val closed = BooleanArray(capacity)
    private var head = 0
    private var size = 0

    fun add(timestampMs: Long, eyesClosed: Boolean) {
        timestamps[head] = timestampMs
        closed[head] = eyesClosed
        head = (head + 1) % timestamps.size
        if (size < timestamps.size) size++
    }

    /** Fraction of observed time with eyes closed in the last [windowMs]. */
    fun perclos(nowMs: Long, windowMs: Long): Float {
        if (size < 2) return 0f
        val from = nowMs - windowMs
        var closedMs = 0L
        var totalMs = 0L
        var nextTime = nowMs
        for (k in 0 until size) {
            val index = (head - 1 - k + timestamps.size) % timestamps.size
            val t = timestamps[index]
            if (t < from) break
            val dt = (nextTime - t).coerceIn(0L, MAX_SAMPLE_WEIGHT_MS)
            totalMs += dt
            if (closed[index]) closedMs += dt
            nextTime = t
        }
        return if (totalMs < MIN_OBSERVED_MS) 0f else closedMs.toFloat() / totalMs
    }

    fun clear() {
        head = 0
        size = 0
    }

    private companion object {
        const val SAMPLE_CAPACITY = 2048
        const val MAX_SAMPLE_WEIGHT_MS = 1_000L
        const val MIN_OBSERVED_MS = 5_000L
    }
}
