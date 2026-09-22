package com.mobile.safedrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.AirlineSeatReclineNormal
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mobile.safedrive.ui.components.FeatureRow
import com.mobile.safedrive.ui.components.FindRestAreaCard
import com.mobile.safedrive.ui.components.GhostButton
import com.mobile.safedrive.ui.components.IconCircle
import com.mobile.safedrive.ui.components.PrimaryButton
import com.mobile.safedrive.ui.components.RestAreaStyle
import com.mobile.safedrive.ui.components.TopBar
import com.mobile.safedrive.ui.theme.Palette
import com.mobile.safedrive.ui.theme.Radius
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText

// region ATTENTION — "Stay Alert"

@Composable
fun AttentionAlertScreen(onAcknowledge: () -> Unit, onFindRestArea: () -> Unit) {
    val c = SdTheme.colors
    BackHandler(onBack = onAcknowledge)
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Stay Alert", titleSize = TextSize.xl, titleWeight = FontWeight.Bold)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(160.dp).border(2.dp, c.primaryTint2, CircleShape))
                Box(Modifier.size(128.dp).border(2.dp, c.primaryTint3, CircleShape))
                IconCircle(
                    Icons.Filled.Visibility, size = 96.dp, iconSize = 48.dp,
                    border = c.primaryTint2, elevation = 1.dp,
                )
            }
            Spacer(Modifier.height(48.dp))
            Text(
                "You may be getting tired",
                style = sdText(TextSize.xl2, FontWeight.Bold),
                color = c.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Take a short break\nevery 2 hours.",
                style = sdText(TextSize.s15, lineHeight = 24.4.sp),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(40.dp))
            PrimaryButton("Acknowledge", onAcknowledge, textSize = TextSize.lg, shape = Radius.xl2, elevation = 4.dp)
            Spacer(Modifier.height(32.dp))
        }
        FindRestAreaCard(onFindRestArea, Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp))
    }
}

// endregion

// region DROWSY — "Drowsiness Detected"

@Composable
fun DrowsyAlertScreen(onAcknowledge: () -> Unit, onFindRestArea: () -> Unit) {
    val c = SdTheme.colors
    BackHandler(onBack = onAcknowledge)
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Drowsiness Detected", bottomBorder = true)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(128.dp).border(4.dp, c.warning, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SleepyZ()
            }
            Spacer(Modifier.height(32.dp))
            Text(
                "Signs of fatigue detected.",
                style = sdText(TextSize.xl, FontWeight.SemiBold),
                color = c.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Please stay alert\nor take a break.",
                style = sdText(TextSize.base),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            PrimaryButton("Acknowledge", onAcknowledge, elevation = 1.dp)
            Spacer(Modifier.height(24.dp))
            FindRestAreaCard(
                onFindRestArea,
                style = RestAreaStyle.Muted,
                shape = Radius.xl,
                titleSize = TextSize.base,
                titleWeight = FontWeight.Medium,
            )
        }
    }
}

/** The design's orange "Zᶻ" drowsiness mark. */
@Composable
private fun SleepyZ() {
    val c = SdTheme.colors
    Box(Modifier.size(64.dp)) {
        Text(
            "Z",
            style = sdText(40.sp, FontWeight.Black, lineHeight = 40.sp),
            color = c.warning,
            modifier = Modifier.align(Alignment.Center).offset(x = (-6).dp, y = 6.dp),
        )
        Text(
            "Z",
            style = sdText(22.sp, FontWeight.Black, lineHeight = 22.sp),
            color = c.warning,
            modifier = Modifier.align(Alignment.Center).offset(x = 14.dp, y = (-12).dp),
        )
    }
}

// endregion

// region CRITICAL — "Critical Warning" (also the lock-screen alert, spec Screen 5/7)

@Composable
fun CriticalAlertScreen(onEndTrip: () -> Unit, onFindRestArea: () -> Unit) {
    val c = SdTheme.colors
    // No dismiss by back: CRITICAL persists until the driver recovers or ends the trip (spec §21).
    BackHandler {}
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Critical Warning", modifier = Modifier.padding(top = 8.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = c.danger, modifier = Modifier.size(112.dp))
            Spacer(Modifier.height(32.dp))
            Text(
                "DROWSINESS\nDETECTED",
                style = sdText(TextSize.xl2, FontWeight.Bold, lineHeight = 30.sp, letterSpacing = 0.025.em),
                color = c.danger,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Immediate risk detected.\nPlease find a safe place to\nstop and rest now.",
                style = sdText(TextSize.base),
                color = c.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 48.dp)) {
            PrimaryButton(
                "End Trip", onEndTrip,
                color = c.danger, pressedColor = c.danger.copy(alpha = 0.85f),
                elevation = 1.dp, leadingIcon = Icons.Filled.StopCircle,
            )
            Spacer(Modifier.height(32.dp))
            FindRestAreaCard(onFindRestArea, shape = Radius.xl, titleSize = TextSize.base, subtitleSize = TextSize.sm)
        }
    }
}

// endregion

// region Eyes Not Visible

@Composable
fun EyesNotVisibleScreen(onAdjusted: () -> Unit, onDismiss: () -> Unit) {
    val c = SdTheme.colors
    BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconCircle(Icons.Outlined.VisibilityOff, size = 96.dp, iconSize = 44.dp)
            Spacer(Modifier.height(32.dp))
            Text("Eyes Not Visible", style = sdText(TextSize.xl2, FontWeight.Bold), color = c.textPrimary)
            Spacer(Modifier.height(12.dp))
            Text(
                "The AI can't detect your eyes. Make sure you're facing the camera.",
                style = sdText(TextSize.sm, lineHeight = 22.75.sp),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
            )
            Spacer(Modifier.height(40.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                FeatureRow(Icons.Outlined.VisibilityOff, "Check Sunglasses", "Remove dark glasses")
                FeatureRow(Icons.Outlined.AirlineSeatReclineNormal, "Adjust Seat Position", "Face the camera directly")
                FeatureRow(Icons.Outlined.WbSunny, "Improve Lighting", "Increase the light level")
            }
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PrimaryButton("I've Adjusted", onAdjusted)
            GhostButton("Dismiss", onDismiss, verticalPadding = 12.dp)
        }
    }
}

// endregion

// region Low Light Warning

@Composable
fun LowLightWarningScreen(onFixed: () -> Unit, onDismiss: () -> Unit) {
    val c = SdTheme.colors
    BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        TopBar("Low Light Warning", onBack = onDismiss)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconCircle(Icons.Filled.DarkMode, size = 96.dp, iconSize = 40.dp, tint = c.accent)
            Spacer(Modifier.height(24.dp))
            Text(
                "Low Light\nDetected",
                style = sdText(TextSize.xl2, FontWeight.Bold),
                color = c.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "The road is dark and visibility is low.\n\nConsider turning on cabin light or slowing down.",
                style = sdText(TextSize.sm),
                color = c.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(32.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                LowLightAction(Icons.Filled.Lightbulb, "Turn on Cabin Light")
                LowLightAction(Icons.Filled.PhoneAndroid, "Adjust Device Angle")
            }
        }
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp)) {
            PrimaryButton(
                "I've Fixed It", onFixed,
                color = c.primaryDeep, pressedColor = Palette.Blue800, elevation = 4.dp,
            )
            Spacer(Modifier.height(16.dp))
            GhostButton("Dismiss", onDismiss, verticalPadding = 8.dp)
        }
    }
}

@Composable
private fun LowLightAction(icon: ImageVector, label: String) {
    val c = SdTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.xl)
            .background(c.surfaceMuted)
            .border(1.dp, c.border, Radius.xl)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon, background = c.card, tint = c.accent, border = c.border, elevation = 1.dp, iconSize = 18.dp)
        Spacer(Modifier.width(16.dp))
        Text(label, style = sdText(TextSize.sm, FontWeight.SemiBold), color = c.textStrong)
    }
}

// endregion
