package com.mobile.safedrive.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.safedrive.ui.components.FeatureRow
import com.mobile.safedrive.ui.components.GhostButton
import com.mobile.safedrive.ui.components.IconCircle
import com.mobile.safedrive.ui.components.OutlineButton
import com.mobile.safedrive.ui.components.PrimaryButton
import com.mobile.safedrive.ui.components.TopBar
import com.mobile.safedrive.ui.theme.Palette
import com.mobile.safedrive.ui.theme.Radius
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText

// region Welcome — "Drive Smarter, Stay Safer"

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    val c = SdTheme.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        WelcomeIllustration(Modifier.weight(1f).fillMaxWidth())
        Column(
            Modifier
                .offset(y = (-40).dp)
                .shadow(10.dp, Radius.sheet, ambientColor = Color.Black.copy(alpha = 0.05f))
                .clip(Radius.sheet)
                .background(c.background)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Drive Smarter,\nStay Safer",
                style = sdText(TextSize.xl3, FontWeight.Bold, lineHeight = 37.5.sp),
                color = c.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "AI-powered monitoring to keep you alert and safe on every journey.",
                style = sdText(TextSize.base, lineHeight = 26.sp),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(40.dp))
            PrimaryButton("Get Started", onGetStarted, elevation = 10.dp)
            Spacer(Modifier.height(32.dp))
            PagerDots(count = 4, selected = 0)
        }
    }
}

@Composable
private fun PagerDots(count: Int, selected: Int) {
    val c = SdTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (index == selected) c.primary else c.dotInactive))
        }
    }
}

/** Mountains, pine trees, road, car and the steering-wheel shield from the welcome design. */
@Composable
private fun WelcomeIllustration(modifier: Modifier) {
    BoxWithConstraints(
        modifier.background(Brush.verticalGradient(listOf(Palette.SkyTop, Palette.Sky100))),
    ) {
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            drawScaledPath(MOUNTAINS, viewBox = Size(100f, 50f), heightFraction = 0.4f, color = Palette.Blue100.copy(alpha = 0.7f))
            drawScaledPath(TREES, viewBox = Size(100f, 20f), heightFraction = 0.3f, color = Palette.Blue900)
            val roadTop = size.height * 0.8f
            val road = Path().apply {
                moveTo(size.width * 0.4f, roadTop)
                lineTo(size.width * 0.6f, roadTop)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(road, Palette.Slate100)
        }
        // Shield with steering wheel, top 20%
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = h * 0.2f)
                .size(width = 60.dp, height = 70.dp)
                .shadow(8.dp, HexagonShape)
                .clip(HexagonShape)
                .background(Palette.Blue500),
            contentAlignment = Alignment.Center,
        ) {
            SteeringWheel(Modifier.size(32.dp))
        }
        // Car, bottom 10%
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(h * 0.1f))
                .size(width = 80.dp, height = 50.dp),
        ) {
            Box(
                Modifier
                    .size(80.dp, 40.dp)
                    .shadow(4.dp, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Palette.Blue600),
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .size(48.dp, 16.dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(Palette.Blue400.copy(alpha = 0.5f)),
                )
                Box(Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 8.dp).size(8.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Red500))
                Box(Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 8.dp).size(8.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Red500))
            }
            Box(Modifier.align(Alignment.BottomStart).offset(x = 5.dp).size(15.dp).clip(CircleShape).background(Palette.Slate800))
            Box(Modifier.align(Alignment.BottomStart).offset(x = 60.dp).size(15.dp).clip(CircleShape).background(Palette.Slate800))
        }
    }
}

private val HexagonShape = GenericShape { size, _ ->
    moveTo(size.width * 0.5f, 0f)
    lineTo(size.width, size.height * 0.2f)
    lineTo(size.width, size.height * 0.8f)
    lineTo(size.width * 0.5f, size.height)
    lineTo(0f, size.height * 0.8f)
    lineTo(0f, size.height * 0.2f)
    close()
}

@Composable
private fun SteeringWheel(modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.12f
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - stroke / 2
        drawCircle(Color.White, radius, center, style = Stroke(stroke))
        drawCircle(Color.White, size.minDimension * 0.12f, center)
        drawLine(Color.White, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), stroke)
        drawLine(Color.White, center, Offset(center.x, center.y + radius), stroke)
    }
}

// SVG paths from the design's CSS background images.
private val MOUNTAINS = listOf(
    listOf(0f to 50f, 20f to 20f, 40f to 50f),
    listOf(30f to 50f, 50f to 10f, 80f to 50f),
    listOf(70f to 50f, 85f to 30f, 100f to 50f),
)
private val TREES = listOf(
    listOf(10f to 20f, 15f to 5f, 20f to 20f),
    listOf(30f to 20f, 35f to 8f, 40f to 20f),
    listOf(60f to 20f, 65f to 12f, 70f to 20f),
    listOf(80f to 20f, 85f to 6f, 90f to 20f),
)

private fun DrawScope.drawScaledPath(
    triangles: List<List<Pair<Float, Float>>>,
    viewBox: Size,
    heightFraction: Float,
    color: Color,
) {
    val boxHeight = size.height * heightFraction
    val top = size.height - boxHeight
    val sx = size.width / viewBox.width
    val sy = boxHeight / viewBox.height
    val path = Path()
    triangles.forEach { points ->
        points.forEachIndexed { i, (x, y) ->
            if (i == 0) path.moveTo(x * sx, top + y * sy) else path.lineTo(x * sx, top + y * sy)
        }
        path.close()
    }
    drawPath(path, color)
}

// endregion

// region Your Privacy

@Composable
fun PrivacyScreen(onAgree: () -> Unit) {
    val c = SdTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Your Privacy")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Eco, null, tint = Palette.Gray300,
                    modifier = Modifier.offset(x = (-64).dp).size(16.dp).rotate(-45f).alpha(0.5f),
                )
                IconCircle(Icons.Filled.Shield, size = 96.dp, iconSize = 48.dp, tint = c.accent)
                Icon(
                    Icons.Filled.Eco, null, tint = Palette.Gray300,
                    modifier = Modifier.offset(x = 64.dp).size(16.dp).rotate(45f).alpha(0.5f),
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Your Privacy\nis Our Priority",
                style = sdText(TextSize.xl2, FontWeight.Bold, lineHeight = 30.sp),
                color = c.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "We use AI on your device to protect your data. Your trips and preferences stay private and secure.",
                style = sdText(TextSize.sm, lineHeight = 22.75.sp),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
            )
            Spacer(Modifier.height(40.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                FeatureRow(
                    Icons.Filled.Memory, "100% Local Processing", "AI runs on your device",
                    iconBackground = c.successTint, iconTint = c.success, titleSize = TextSize.base,
                )
                FeatureRow(
                    Icons.Filled.VideocamOff, "No Video Uploads", "Your data never leaves\nyour phone",
                    iconBackground = c.primaryTint, iconTint = c.accent, titleSize = TextSize.base,
                )
                FeatureRow(
                    Icons.Filled.Lock, "Encrypted Logs", "Your data is always\nprotected",
                    iconBackground = c.tealTint, iconTint = c.teal, titleSize = TextSize.base,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
        Box(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp)) {
            PrimaryButton("Agree & Continue", onAgree)
        }
    }
}

// endregion

// region Camera Permissions

@Composable
fun CameraPermissionScreen(onAllow: () -> Unit, onNotNow: () -> Unit) {
    val c = SdTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 32.dp),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconCircle(Icons.Filled.CameraAlt, size = 80.dp, iconSize = 32.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                "Camera Access\nRequired",
                style = sdText(TextSize.xl2, FontWeight.SemiBold),
                color = c.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "To monitor your attention and keep you safe, we need access to your front camera.",
                style = sdText(TextSize.sm, lineHeight = 22.75.sp),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(40.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                FeatureRow(Icons.Filled.Shield, "Real-time monitoring", "AI processing is on device")
                FeatureRow(Icons.Filled.Memory, "Local processing only", "Your data stays private")
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton("Allow Camera Access", onAllow, verticalPadding = 14.dp)
            GhostButton("Not Now", onNotNow, textSize = TextSize.sm, verticalPadding = 14.dp)
        }
    }
}

// endregion

// region Setup Guide

@Composable
fun SetupGuideScreen(onBack: () -> Unit, onNext: () -> Unit) {
    val c = SdTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Setup Guide", onBack = onBack, horizontalPadding = 24.dp)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Follow these steps for\nbest monitoring",
                style = sdText(TextSize.sm, lineHeight = 22.75.sp),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(32.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SetupStep(Icons.Outlined.Visibility, "Eye Level", "Position your phone\nat eye level")
                SetupStep(Icons.Outlined.Person, "Clear View", "Ensure your full face\nis visible")
                SetupStep(Icons.Outlined.WbSunny, "Good Lighting", "Avoid strong backlight\nor shadows")
                SetupStep(Icons.Filled.StayCurrentPortrait, "Stable Position", "Mount your phone\nsecurely")
            }
        }
        Box(Modifier.background(c.background).padding(24.dp)) {
            OutlineButton("Next: Test Camera", onNext)
        }
    }
}

@Composable
private fun SetupStep(icon: ImageVector, title: String, body: String) {
    FeatureRow(
        icon, title, body,
        titleSize = TextSize.base, subtitleSize = TextSize.sm, subtitleTopGap = 4.dp, iconTopOffset = 4.dp,
    )
}

// endregion
