package com.mobile.safedrive.vision

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.mobile.safedrive.drowsiness.FrameObservation
import kotlin.math.abs

/**
 * On-device MediaPipe Face Landmarker (spec §5, §6). Runs synchronously on the
 * analysis thread in VIDEO mode; nothing leaves the device.
 */
class FaceLandmarkAnalyzer(context: Context) : AutoCloseable {

    private val landmarker: FaceLandmarker? = create(context)
    private val rotationOptions = arrayOfNulls<ImageProcessingOptions>(ROTATIONS)

    val isAvailable: Boolean get() = landmarker != null

    /**
     * Fills [out] with metrics for the largest (driver) face. [width]/[height] are the
     * upright image size (after rotation) used to keep distances isotropic.
     */
    fun analyze(image: MPImage, rotationDegrees: Int, timestampMs: Long, width: Int, height: Int, out: FrameObservation) {
        out.timestampMs = timestampMs
        out.faceCount = 0
        val detector = landmarker ?: return
        val result = runCatching {
            detector.detectForVideo(image, optionsFor(rotationDegrees), timestampMs)
        }.getOrNull() ?: return

        val faces = result.faceLandmarks()
        out.faceCount = faces.size
        if (faces.isEmpty()) return

        val driver = largestFaceIndex(faces)
        val points = faces[driver]
        val aspect = width.toFloat() / height
        val m = out.metrics
        m.leftEar = EyeAnalyzer.leftEar(points, aspect)
        m.rightEar = EyeAnalyzer.rightEar(points, aspect)
        m.averageEar = (m.leftEar + m.rightEar) / 2f
        m.mar = MouthAnalyzer.mar(points, aspect)
        m.faceConfidence = faceConfidence(points)

        val matrices = result.facialTransformationMatrixes()
        if (matrices.isPresent && matrices.get().size > driver) {
            val matrix = matrices.get()[driver]
            m.pitch = HeadPoseAnalyzer.pitch(matrix)
            m.yaw = HeadPoseAnalyzer.yaw(matrix)
            m.roll = HeadPoseAnalyzer.roll(matrix)
        }
    }

    private fun optionsFor(rotation: Int): ImageProcessingOptions {
        val slot = (rotation / DEGREES_PER_SLOT) % ROTATIONS
        return rotationOptions[slot] ?: ImageProcessingOptions.builder()
            .setRotationDegrees(slot * DEGREES_PER_SLOT)
            .build()
            .also { rotationOptions[slot] = it }
    }

    private fun largestFaceIndex(faces: List<List<NormalizedLandmark>>): Int {
        var best = 0
        var bestWidth = -1f
        for (i in faces.indices) {
            val w = abs(faces[i][FACE_LEFT].x() - faces[i][FACE_RIGHT].x())
            if (w > bestWidth) {
                bestWidth = w
                best = i
            }
        }
        return best
    }

    /**
     * Face Landmarker exposes no per-face score, so confidence is estimated from how much
     * of the face is inside the frame and how large it is.
     */
    private fun faceConfidence(points: List<NormalizedLandmark>): Float {
        var inside = 0
        for (i in CONFIDENCE_PROBES) {
            val p = points[i]
            if (p.x() in 0f..1f && p.y() in 0f..1f) inside++
        }
        val coverage = inside.toFloat() / CONFIDENCE_PROBES.size
        val faceWidth = abs(points[FACE_LEFT].x() - points[FACE_RIGHT].x())
        val sizeFactor = (faceWidth / GOOD_FACE_WIDTH).coerceIn(0f, 1f)
        return coverage * (MIN_SIZE_FACTOR + (1f - MIN_SIZE_FACTOR) * sizeFactor)
    }

    override fun close() {
        landmarker?.close()
    }

    companion object {
        private const val TAG = "FaceLandmarkAnalyzer"
        const val MODEL_ASSET = "face_landmarker.task"
        private const val MAX_FACES = 2
        private const val MIN_CONFIDENCE = 0.5f
        private const val ROTATIONS = 4
        private const val DEGREES_PER_SLOT = 90
        private const val FACE_LEFT = 234
        private const val FACE_RIGHT = 454
        private val CONFIDENCE_PROBES = intArrayOf(10, 152, 234, 454, 33, 263, 61, 291)
        private const val GOOD_FACE_WIDTH = 0.25f
        private const val MIN_SIZE_FACTOR = 0.6f

        private fun create(context: Context): FaceLandmarker? = runCatching {
            val base = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .setDelegate(Delegate.CPU)
                .build()
            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.VIDEO)
                .setNumFaces(MAX_FACES)
                .setMinFaceDetectionConfidence(MIN_CONFIDENCE)
                .setMinFacePresenceConfidence(MIN_CONFIDENCE)
                .setMinTrackingConfidence(MIN_CONFIDENCE)
                .setOutputFacialTransformationMatrixes(true)
                .build()
            FaceLandmarker.createFromOptions(context, options)
        }.onFailure { Log.e(TAG, "Face Landmarker unavailable", it) }.getOrNull()
    }
}
