package com.mobile.safedrive.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.mobile.safedrive.drowsiness.FrameObservation
import com.mobile.safedrive.vision.FaceLandmarkAnalyzer
import java.nio.ByteBuffer

/**
 * ImageAnalysis analyzer with the adaptive FPS gate (spec §37): frames arriving earlier than
 * [minIntervalMs] are closed immediately without touching CPU/NPU. Accepted RGBA frames are
 * handed to MediaPipe straight from the camera buffer — no display bitmap is created.
 */
class FrameAnalyzer(
    private val face: FaceLandmarkAnalyzer,
    private val minIntervalMs: () -> Long,
    private val onFrame: (FrameObservation) -> Unit,
) : ImageAnalysis.Analyzer {

    private val observation = FrameObservation()
    private var lastAnalyzedMs = 0L
    private var lastTimestampMs = 0L

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.uptimeMillis()
        if (now - lastAnalyzedMs < minIntervalMs()) {
            image.close()
            return
        }
        lastAnalyzedMs = now
        // VIDEO running mode requires strictly increasing timestamps.
        val timestamp = if (now > lastTimestampMs) now else lastTimestampMs + 1
        lastTimestampMs = timestamp

        image.use { frame ->
            val plane = frame.planes[0]
            val buffer = plane.buffer.apply { rewind() }
            observation.luminance = averageLuma(buffer, plane.rowStride, frame.width, frame.height)
            val mpImage: MPImage = if (plane.rowStride == frame.width * BYTES_PER_PIXEL) {
                ByteBufferImageBuilder(buffer, frame.width, frame.height, MPImage.IMAGE_FORMAT_RGBA).build()
            } else {
                // Rare: padded rows can't be wrapped directly.
                BitmapImageBuilder(frame.toBitmap()).build()
            }
            val rotation = frame.imageInfo.rotationDegrees
            val sideways = rotation == SIDEWAYS_90 || rotation == SIDEWAYS_270
            val uprightWidth = if (sideways) frame.height else frame.width
            val uprightHeight = if (sideways) frame.width else frame.height
            face.analyze(mpImage, rotation, timestamp, uprightWidth, uprightHeight, observation)
            mpImage.close()
        }
        onFrame(observation)
    }

    /** Sparse luma estimate (Rec.601) used for low-light detection. */
    private fun averageLuma(buffer: ByteBuffer, rowStride: Int, width: Int, height: Int): Float {
        var sum = 0L
        var n = 0
        var y = 0
        while (y < height) {
            var x = 0
            val row = y * rowStride
            while (x < width) {
                val i = row + x * BYTES_PER_PIXEL
                val r = buffer.get(i).toInt() and BYTE_MASK
                val g = buffer.get(i + 1).toInt() and BYTE_MASK
                val b = buffer.get(i + 2).toInt() and BYTE_MASK
                sum += (r * LUMA_R + g * LUMA_G + b * LUMA_B) / LUMA_DIVISOR
                n++
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4
        const val SIDEWAYS_90 = 90
        const val SIDEWAYS_270 = 270
        const val SAMPLE_STEP = 16
        const val BYTE_MASK = 0xFF
        const val LUMA_R = 299
        const val LUMA_G = 587
        const val LUMA_B = 114
        const val LUMA_DIVISOR = 1000
    }
}
