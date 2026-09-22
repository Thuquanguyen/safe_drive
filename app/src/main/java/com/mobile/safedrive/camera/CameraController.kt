package com.mobile.safedrive.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Front camera binding at 640x480 (spec §37). With a [Preview] it drives the on-screen
 * setup view; without one it is the headless pipeline used by the monitoring service.
 */
class CameraController(private val context: Context) {

    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private val bound = mutableListOf<UseCase>()

    suspend fun bind(owner: LifecycleOwner, analyzer: ImageAnalysis.Analyzer, preview: Preview? = null) {
        val cameraProvider = awaitProvider()
        unbind()
        val resolution = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(TARGET_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            )
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }
        val useCases = listOfNotNull(preview, analysis)
        cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, *useCases.toTypedArray())
        bound += useCases
    }

    fun unbind() {
        val cameraProvider = provider ?: return
        if (bound.isNotEmpty()) {
            cameraProvider.unbind(*bound.toTypedArray())
            bound.clear()
        }
    }

    /** Call off the main thread after [unbind]: waits for the in-flight frame to finish. */
    fun releaseBlocking() {
        analysisExecutor.shutdown()
        analysisExecutor.awaitTermination(DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private suspend fun awaitProvider(): ProcessCameraProvider {
        provider?.let { return it }
        val future = ProcessCameraProvider.getInstance(context)
        return suspendCancellableCoroutine { cont ->
            future.addListener({
                runCatching { future.get() }
                    .onSuccess { provider = it; cont.resume(it) }
                    .onFailure { cont.resumeWithException(it) }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    companion object {
        val TARGET_SIZE = Size(640, 480)
        private const val DRAIN_TIMEOUT_MS = 1_000L
    }
}
