package com.mobile.safedrive.ui.screens

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mobile.safedrive.camera.CameraController
import com.mobile.safedrive.camera.FrameAnalyzer
import com.mobile.safedrive.drowsiness.DrowsinessConfig
import com.mobile.safedrive.ui.components.PrimaryButton
import com.mobile.safedrive.ui.components.TopBar
import com.mobile.safedrive.ui.theme.Radius
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText
import com.mobile.safedrive.vision.FaceLandmarkAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val PREVIEW_ANALYSIS_INTERVAL_MS = 150L
private const val NOT_ANALYZED = -1
private const val GOOD_FACE_CONFIDENCE = 0.6f
private const val CLEAR_VIEW_CONFIDENCE = 0.8f
private const val STABLE_ANGLE_DELTA_DEG = 4f
private const val STABLE_FRAMES = 5

/**
 * Camera pre-check (spec §2.1, §3.1): live front preview with face guide, plus lighting,
 * stability and clear-view indicators. In trip mode Continue requires a good position.
 */
@Composable
fun PositionCameraScreen(
    requireGoodPosition: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onCalibrationTips: () -> Unit,
) {
    val c = SdTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var faceCount by remember { mutableIntStateOf(NOT_ANALYZED) }
    var confidence by remember { mutableFloatStateOf(0f) }
    var luminance by remember { mutableFloatStateOf(0f) }
    var stableFrames by remember { mutableIntStateOf(0) }
    var modelAvailable by remember { mutableStateOf(true) }
    var cameraFailed by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        val controller = CameraController(context)
        var analyzer: FaceLandmarkAnalyzer? = null
        val job = scope.launch {
            val face = withContext(Dispatchers.Default) { FaceLandmarkAnalyzer(context) }
            analyzer = face
            modelAvailable = face.isAvailable
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            var lastPitch = 0f
            var lastYaw = 0f
            val frameAnalyzer = FrameAnalyzer(face, { PREVIEW_ANALYSIS_INTERVAL_MS }) { obs ->
                faceCount = obs.faceCount
                luminance = obs.luminance
                if (obs.faceDetected) {
                    val m = obs.metrics
                    confidence = m.faceConfidence
                    val steady = abs(m.pitch - lastPitch) < STABLE_ANGLE_DELTA_DEG &&
                        abs(m.yaw - lastYaw) < STABLE_ANGLE_DELTA_DEG
                    stableFrames = if (steady) stableFrames + 1 else 0
                    lastPitch = m.pitch
                    lastYaw = m.yaw
                } else {
                    confidence = 0f
                    stableFrames = 0
                }
            }
            runCatching { controller.bind(lifecycleOwner, frameAnalyzer, preview) }
                .onFailure { cameraFailed = true }
        }
        onDispose {
            job.cancel()
            controller.unbind()
            Thread {
                controller.releaseBlocking()
                analyzer?.close()
            }.start()
        }
    }

    val analyzed = faceCount != NOT_ANALYZED
    val singleFace = faceCount == 1 && confidence >= GOOD_FACE_CONFIDENCE
    val lightingOk = luminance >= DrowsinessConfig().lowLightLuma
    val stableOk = stableFrames >= STABLE_FRAMES
    val clearView = faceCount == 1 && confidence >= CLEAR_VIEW_CONFIDENCE
    val goodPosition = singleFace && lightingOk
    val status: Pair<String, Boolean?> = when {
        cameraFailed -> "Camera unavailable" to false
        !modelAvailable -> "Face detection unavailable" to false
        !analyzed -> "Checking position…" to null
        faceCount == 0 -> "Face not detected" to false
        faceCount > 1 -> "Only one driver should be visible" to false
        !lightingOk -> "Low light" to false
        !singleFace -> "Move closer to the camera" to false
        else -> "Good Position" to true
    }
    val canContinue = !requireGoodPosition || goodPosition || !modelAvailable || cameraFailed

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar(
            "Position Camera", onBack = onBack, leftAligned = true,
            titleColor = c.slateTextStrong, backTint = c.slateText,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Place your phone at eye level", style = sdText(TextSize.sm), color = c.slateText)
            Spacer(Modifier.height(4.dp))
            Text(
                if (status.first == "Face not detected") "Move the phone so your face is visible."
                else "Make sure your face is clearly visible",
                style = sdText(TextSize.sm), color = c.slateTextMuted, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            StatusBadge(status.first, status.second)
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .shadow(4.dp, Radius.xl2)
                    .clip(Radius.xl2)
                    .background(Color.Black),
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                FaceGuide(
                    color = if (status.second == true) c.emeraldLine else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Row(
                Modifier.widthIn(max = 280.dp).fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CheckIndicator(Icons.Outlined.WbSunny, "Good Lighting", if (analyzed) lightingOk else null)
                CheckIndicator(Icons.Outlined.PhoneAndroid, "Stable Position", if (analyzed) stableOk else null)
                CheckIndicator(Icons.Outlined.Map, "Road Clear", if (analyzed) clearView else null)
            }
            Spacer(Modifier.height(32.dp))
        }
        // shadow-up footer
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.05f)))),
        )
        Column(
            Modifier.fillMaxWidth().background(c.background).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryButton("Continue", onContinue, verticalPadding = 14.dp, enabled = canContinue)
            Spacer(Modifier.height(16.dp))
            Text(
                "Calibration tips",
                style = sdText(TextSize.sm, FontWeight.Medium),
                color = c.primary,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onCalibrationTips).padding(4.dp),
            )
        }
    }
}

/** good = emerald badge (design); false = warning; null = neutral while checking. */
@Composable
private fun StatusBadge(text: String, good: Boolean?) {
    val c = SdTheme.colors
    val (bg, fg, border) = when (good) {
        true -> Triple(c.emeraldTint, c.emeraldText, c.emeraldBorder)
        false -> Triple(c.warning.copy(alpha = 0.08f), c.warning, c.warning.copy(alpha = 0.2f))
        null -> Triple(c.slateTint, c.slateText, c.border)
    }
    Row(
        Modifier
            .shadow(1.dp, CircleShape)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (good == false) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
            contentDescription = null, tint = fg, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = sdText(TextSize.sm, FontWeight.Medium), color = fg)
    }
}

@Composable
private fun CheckIndicator(icon: ImageVector, label: String, ok: Boolean?) {
    val c = SdTheme.colors
    val tint = when (ok) {
        true -> c.slateTextStrong
        false -> c.warning
        null -> c.slateTextMuted
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(c.slateTint), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = sdText(10.4.sp, FontWeight.Medium, lineHeight = 13.sp),
            color = c.slateTextStrong,
            textAlign = TextAlign.Center,
        )
    }
}

/** Rounded face frame (80% height) with corner brackets, as in the design overlay. */
@Composable
private fun FaceGuide(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 2.dp.toPx()
        val guideHeight = size.height * 0.8f
        val top = (size.height - guideHeight) / 2
        val radius = 64.dp.toPx()
        drawRoundRect(
            color, topLeft = Offset(0f, top), size = Size(size.width, guideHeight),
            cornerRadius = CornerRadius(radius), style = Stroke(stroke),
        )
        val arm = 16.dp.toPx()
        val out = 8.dp.toPx()
        val r = 8.dp.toPx()
        val left = -out
        val right = size.width + out
        val upper = top - out
        val lower = top + guideHeight + out
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val path = Path().apply {
                moveTo(x, y + dy * arm)
                lineTo(x, y + dy * r)
                quadraticTo(x, y, x + dx * r, y)
                lineTo(x + dx * arm, y)
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        corner(left, upper, 1f, 1f)
        corner(right, upper, -1f, 1f)
        corner(left, lower, 1f, -1f)
        corner(right, lower, -1f, -1f)
    }
}
