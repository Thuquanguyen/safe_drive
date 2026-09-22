package com.mobile.safedrive.vision

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/** EAR (spec §7) from the 6 MediaPipe Face Mesh points around each eye. */
object EyeAnalyzer {
    // p1..p6 = outer corner, upper-1, upper-2, inner corner, lower-2, lower-1
    private val RIGHT_EYE = intArrayOf(33, 160, 158, 133, 153, 144)
    private val LEFT_EYE = intArrayOf(362, 385, 387, 263, 373, 380)

    fun leftEar(points: List<NormalizedLandmark>, aspect: Float) = ear(points, LEFT_EYE, aspect)
    fun rightEar(points: List<NormalizedLandmark>, aspect: Float) = ear(points, RIGHT_EYE, aspect)

    private fun ear(p: List<NormalizedLandmark>, idx: IntArray, aspect: Float): Float {
        val horizontal = dist(p[idx[0]], p[idx[3]], aspect)
        if (horizontal <= 0f) return 0f
        return (dist(p[idx[1]], p[idx[5]], aspect) + dist(p[idx[2]], p[idx[4]], aspect)) / (2f * horizontal)
    }
}

/** MAR (spec §11): mean inner-lip opening over mouth width. */
object MouthAnalyzer {
    private val VERTICAL_PAIRS = intArrayOf(81, 178, 13, 14, 311, 402)
    private const val LEFT_CORNER = 61
    private const val RIGHT_CORNER = 291

    fun mar(p: List<NormalizedLandmark>, aspect: Float): Float {
        val width = dist(p[LEFT_CORNER], p[RIGHT_CORNER], aspect)
        if (width <= 0f) return 0f
        var opening = 0f
        var i = 0
        while (i < VERTICAL_PAIRS.size) {
            opening += dist(p[VERTICAL_PAIRS[i]], p[VERTICAL_PAIRS[i + 1]], aspect)
            i += 2
        }
        return opening / (VERTICAL_PAIRS.size / 2) / width
    }
}

/** Pitch / yaw / roll in degrees from MediaPipe's 4x4 column-major facial transformation matrix. */
object HeadPoseAnalyzer {
    private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

    fun pitch(m: FloatArray) = atan2(m[6], m[10]) * RAD_TO_DEG
    fun yaw(m: FloatArray) = asin((-m[2]).coerceIn(-1f, 1f)) * RAD_TO_DEG
    fun roll(m: FloatArray) = atan2(m[1], m[0]) * RAD_TO_DEG
}

/** Distance in image space; x is scaled by width/height so both axes use the same unit. */
internal fun dist(a: NormalizedLandmark, b: NormalizedLandmark, aspect: Float): Float {
    val dx = (a.x() - b.x()) * aspect
    val dy = a.y() - b.y()
    return sqrt(dx * dx + dy * dy)
}
