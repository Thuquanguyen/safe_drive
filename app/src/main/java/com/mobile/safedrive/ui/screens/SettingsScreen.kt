package com.mobile.safedrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.safedrive.settings.AppSettings
import com.mobile.safedrive.settings.Sensitivity
import com.mobile.safedrive.ui.components.BottomNavBar
import com.mobile.safedrive.ui.components.HomeTab
import com.mobile.safedrive.ui.components.IconCircle
import com.mobile.safedrive.ui.components.TopBar
import com.mobile.safedrive.ui.theme.Radius
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText

/** Spec §49 settings, built from the design system's card / icon-circle / typography tokens. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    monitoringActive: Boolean,
    onBack: () -> Unit,
    onTab: (HomeTab) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onToggleAutoDetect: (Boolean) -> Unit,
    onRecalibrate: () -> Unit,
) {
    val c = SdTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        TopBar("Settings", onBack = onBack, verticalPadding = 8.dp)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(c.pageMuted)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Driving detection") {
                SwitchRow(
                    Icons.Outlined.DirectionsCar, "Auto-detect Driving",
                    "Start monitoring automatically when driving",
                    settings.autoDetectDriving, onToggleAutoDetect,
                )
                Divider()
                ChoiceRow(
                    Icons.Outlined.Speed, "Activation speed", "Speed that confirms you are driving",
                    AppSettings.SPEED_OPTIONS.map { "$it km/h" },
                    AppSettings.SPEED_OPTIONS.indexOf(settings.autoStartSpeedKmh),
                ) { i -> onUpdate { it.copy(autoStartSpeedKmh = AppSettings.SPEED_OPTIONS[i]) } }
                Divider()
                ChoiceRow(
                    Icons.Outlined.Timer, "Stop grace period", "Wait before ending a trip when stopped",
                    AppSettings.GRACE_OPTIONS.map { "$it min" },
                    AppSettings.GRACE_OPTIONS.indexOf(settings.gracePeriodMinutes),
                ) { i -> onUpdate { it.copy(gracePeriodMinutes = AppSettings.GRACE_OPTIONS[i]) } }
            }
            Section("Monitoring & battery") {
                SwitchRow(
                    Icons.Outlined.ScreenLockPortrait, "Screen-off Monitoring",
                    "Keep protecting you when the screen is off",
                    settings.screenOffMonitoring,
                ) { v -> onUpdate { it.copy(screenOffMonitoring = v) } }
                Divider()
                SwitchRow(
                    Icons.Outlined.BatterySaver, "Ultra Battery Saver",
                    "Adaptive 2–3 FPS and no rendering when the screen is off",
                    settings.ultraBatterySaver,
                ) { v -> onUpdate { it.copy(ultraBatterySaver = v) } }
                Divider()
                SwitchRow(
                    Icons.Outlined.LightMode, "Keep Screen Awake",
                    "Useful when the phone is on a mount",
                    settings.keepScreenAwake,
                ) { v -> onUpdate { it.copy(keepScreenAwake = v) } }
            }
            Section("Alerts") {
                SliderRow(Icons.AutoMirrored.Outlined.VolumeUp, "Alert Volume", settings.alertVolume) { v ->
                    onUpdate { it.copy(alertVolume = v) }
                }
                Divider()
                SwitchRow(Icons.Outlined.Vibration, "Vibration", "Haptic feedback with alerts", settings.vibration) { v ->
                    onUpdate { it.copy(vibration = v) }
                }
                Divider()
                SwitchRow(
                    Icons.Outlined.RecordVoiceOver, "Voice Alerts", "Spoken warnings after the alarm",
                    settings.voiceAlerts,
                ) { v -> onUpdate { it.copy(voiceAlerts = v) } }
            }
            Section("Detection") {
                ChoiceRow(
                    Icons.Outlined.Tune, "Sensitivity", "How early drowsiness is flagged",
                    listOf("Low", "Normal", "High"),
                    settings.sensitivity.ordinal,
                ) { i -> onUpdate { it.copy(sensitivity = Sensitivity.entries[i]) } }
                Divider()
                ActionRow(
                    Icons.Outlined.Face, "Recalibrate",
                    if (monitoringActive) "Available after the current trip" else "Re-measure your neutral face",
                    enabled = !monitoringActive, onClick = onRecalibrate,
                )
            }
            Section("Display") {
                SwitchRow(Icons.Outlined.DarkMode, "Dark Mode", "Easier on the eyes at night", settings.darkMode) { v ->
                    onUpdate { it.copy(darkMode = v) }
                }
            }
        }
        BottomNavBar(HomeTab.Settings, onTab)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = SdTheme.colors
    Column {
        Text(title, style = sdText(TextSize.sm, FontWeight.SemiBold), color = c.textPrimary, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(1.dp, Radius.xl2)
                .clip(Radius.xl2)
                .background(c.card)
                .border(1.dp, c.border, Radius.xl2),
            content = content,
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(thickness = 1.dp, color = SdTheme.colors.border, modifier = Modifier.padding(start = 72.dp))

@Composable
private fun RowLabel(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier, enabled: Boolean = true) {
    val c = SdTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconCircle(icon, iconSize = 20.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = sdText(TextSize.sm, FontWeight.SemiBold), color = if (enabled) c.textPrimary else c.textTertiary)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = sdText(TextSize.xs), color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = SdTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(icon, title, subtitle, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.textOnPrimary,
                checkedTrackColor = c.primary,
                uncheckedThumbColor = c.textOnPrimary,
                uncheckedTrackColor = c.borderStrong,
                uncheckedBorderColor = c.borderStrong,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val c = SdTheme.colors
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        RowLabel(icon, title, subtitle, Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(start = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, label ->
                val active = index == selected
                Text(
                    label,
                    style = sdText(TextSize.xs, FontWeight.SemiBold),
                    color = if (active) c.textOnPrimary else c.textBody,
                    modifier = Modifier
                        .clip(Radius.lg)
                        .background(if (active) c.primary else c.surfaceMuted)
                        .border(1.dp, if (active) c.primary else c.borderStrong, Radius.lg)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SliderRow(icon: ImageVector, title: String, value: Float, onChange: (Float) -> Unit) {
    val c = SdTheme.colors
    // Local while dragging; persisted once on release instead of on every drag event.
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        RowLabel(icon, title, "${(draft * 100).toInt()}%", Modifier.fillMaxWidth())
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onChange(draft) },
            valueRange = 0.1f..1f,
            modifier = Modifier.padding(start = 56.dp),
            colors = SliderDefaults.colors(
                thumbColor = c.primary,
                activeTrackColor = c.primary,
                inactiveTrackColor = c.borderStrong,
            ),
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val c = SdTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowLabel(icon, title, subtitle, Modifier.weight(1f), enabled)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
    }
}
