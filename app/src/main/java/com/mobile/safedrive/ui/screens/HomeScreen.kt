package com.mobile.safedrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mobile.safedrive.session.DrivingSession
import com.mobile.safedrive.session.ratingFor
import com.mobile.safedrive.ui.components.BottomNavBar
import com.mobile.safedrive.ui.components.HomeTab
import com.mobile.safedrive.ui.components.ProgressRing
import com.mobile.safedrive.ui.components.StatTile
import com.mobile.safedrive.ui.components.TopBar
import com.mobile.safedrive.ui.theme.Radius
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText
import java.util.Calendar
import kotlin.math.roundToInt

private const val NOON = 12
private const val EVENING = 18
private const val PRESSED_SCALE = 0.95f

@Composable
fun HomeScreen(
    sessions: List<DrivingSession>,
    onStartDriving: () -> Unit,
    onTab: (HomeTab) -> Unit,
    onOpenSetupGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onRecalibrate: () -> Unit,
) {
    val c = SdTheme.colors
    val now = System.currentTimeMillis()
    val week = sessions.filter { it.startTime >= startOfWeek(now) }
    val today = sessions.filter { it.startTime >= startOfDay(now) }
    val score = if (week.isEmpty()) null else week.map { it.safetyScore }.average().roundToInt()
    val hasTodayAlerts = today.any { it.numberOfAlerts > 0 }
    var menuOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        TopBar(
            "Home Dashboard",
            titleSize = TextSize.base,
            horizontalPadding = 24.dp,
            leading = {
                Box {
                    Icon(
                        Icons.Outlined.Menu, contentDescription = "Menu", tint = c.textBody,
                        modifier = Modifier.clip(CircleShape).clickable { menuOpen = true }.padding(4.dp).size(24.dp),
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Setup Guide") }, onClick = { menuOpen = false; onOpenSetupGuide() })
                        DropdownMenuItem(text = { Text("Your Privacy") }, onClick = { menuOpen = false; onOpenPrivacy() })
                        DropdownMenuItem(text = { Text("Recalibrate") }, onClick = { menuOpen = false; onRecalibrate() })
                    }
                }
            },
            trailing = {
                Box(Modifier.clip(CircleShape).clickable { onTab(HomeTab.History) }.padding(4.dp)) {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Alerts", tint = c.textBody, modifier = Modifier.size(24.dp))
                    if (hasTodayAlerts) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 2.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(c.danger)
                                .border(1.dp, c.background, CircleShape),
                        )
                    }
                }
            },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(greeting(), style = sdText(TextSize.xl2, FontWeight.Bold), color = c.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("👋", style = sdText(TextSize.xl))
                }
                Spacer(Modifier.height(4.dp))
                Text("Drive safe, arrive safe.", style = sdText(TextSize.sm), color = c.textSecondary)
            }
            StartDrivingCard(onStartDriving)
            SafetyScoreSection(score)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val driveMs = today.sumOf { it.durationMs }
                StatTile(
                    "Drive Time", formatDuration(driveMs), Modifier.weight(1f),
                    caption = today.minByOrNull { it.startTime }?.let { "Since ${formatTime(it.startTime)}" } ?: "No drives today",
                )
                StatTile("Trips", week.size.toString(), Modifier.weight(1f), caption = "This week")
            }
        }
        BottomNavBar(HomeTab.Home, onTab)
    }
}

@Composable
private fun StartDrivingCard(onClick: () -> Unit) {
    val c = SdTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .scale(if (pressed) PRESSED_SCALE else 1f)
            .shadow(4.dp, Radius.xl2, spotColor = c.primary)
            .clip(Radius.xl2)
            .background(if (pressed) c.primaryPressed else c.primary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = c.textOnPrimary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(12.dp))
        Text("Start Driving", style = sdText(TextSize.lg, FontWeight.SemiBold), color = c.textOnPrimary)
        Spacer(Modifier.height(4.dp))
        Text("Tap to begin monitoring", style = sdText(TextSize.xs), color = c.textOnPrimarySoft.copy(alpha = 0.9f))
    }
}

@Composable
private fun SafetyScoreSection(score: Int?) {
    val c = SdTheme.colors
    Column {
        Text("Safety Score", style = sdText(TextSize.sm, FontWeight.SemiBold), color = c.textPrimary)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = (score ?: 0) / 100f,
                modifier = Modifier.size(80.dp),
                color = c.primaryDeep,
                trackColor = c.track,
                strokeWidth = 6.dp,
            ) {
                Text(score?.toString() ?: "--", style = sdText(TextSize.xl3, FontWeight.Bold), color = c.textPrimary)
            }
            Column(Modifier.weight(1f).padding(start = 24.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("/100", style = sdText(TextSize.sm, FontWeight.Medium), color = c.textSecondary)
                    if (score != null) {
                        val rating = ratingFor(score)
                        Text(rating.label, style = sdText(TextSize.sm, FontWeight.SemiBold), color = ratingColor(rating))
                    } else {
                        Text("No trips yet", style = sdText(TextSize.sm, FontWeight.SemiBold), color = c.textTertiary)
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = c.borderStrong)
            }
        }
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < NOON -> "Good morning"
        hour < EVENING -> "Good afternoon"
        else -> "Good evening"
    }
}
