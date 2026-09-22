package com.mobile.safedrive.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.mobile.safedrive.ui.theme.Radius
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText

/** Top bar variants used across the design (centered title, optional back / trailing). */
@Composable
fun TopBar(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    titleSize: TextUnit = TextSize.lg,
    titleWeight: FontWeight = FontWeight.SemiBold,
    subtitle: String? = null,
    leftAligned: Boolean = false,
    bottomBorder: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 12.dp,
    titleColor: Color = SdTheme.colors.textPrimary,
    backTint: Color = SdTheme.colors.textStrong,
) {
    val c = SdTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val start: (@Composable () -> Unit)? = leading ?: onBack?.let { back -> @Composable { BackButton(back, backTint) } }
            Box(Modifier.width(if (leftAligned) Dp.Unspecified else SIDE_SLOT)) { start?.invoke() }
            if (leftAligned && start != null) Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(1f),
                horizontalAlignment = if (leftAligned) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                if (title != null) {
                    Text(
                        title,
                        style = sdText(titleSize, titleWeight),
                        color = titleColor,
                        textAlign = if (leftAligned) TextAlign.Start else TextAlign.Center,
                    )
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = sdText(TextSize.xs, FontWeight.Medium),
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (!leftAligned) Box(Modifier.width(SIDE_SLOT), contentAlignment = Alignment.CenterEnd) { trailing?.invoke() }
        }
        if (bottomBorder) HorizontalDivider(thickness = 1.dp, color = c.border)
    }
}

private val SIDE_SLOT = 40.dp

@Composable
fun BackButton(onClick: () -> Unit, tint: Color = SdTheme.colors.textStrong) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** bg-blue-600 filled button with the design's pressed state (blue-700). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SdTheme.colors.primary,
    pressedColor: Color = SdTheme.colors.primaryPressed,
    textSize: TextUnit = TextSize.base,
    verticalPadding: Dp = 16.dp,
    shape: Shape = Radius.xl,
    elevation: Dp = 0.dp,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    val c = SdTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = when {
        !enabled -> c.borderStrong
        pressed -> pressedColor
        else -> color
    }
    Row(
        modifier
            .fillMaxWidth()
            .shadow(if (enabled) elevation else 0.dp, shape, spotColor = color)
            .clip(shape)
            .background(background)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = c.textOnPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = sdText(textSize, FontWeight.SemiBold),
            color = if (enabled) c.textOnPrimary else c.textSecondary,
        )
    }
}

/** border-blue text-blue outlined button (hover:bg-blue-50 as pressed state). */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = SdTheme.colors.primary,
    textSize: TextUnit = TextSize.base,
    verticalPadding: Dp = 16.dp,
) {
    val c = SdTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .clip(Radius.xl)
            .background(if (pressed) c.primaryTint else Color.Transparent)
            .border(BorderStroke(1.dp, color), Radius.xl)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = sdText(textSize, FontWeight.SemiBold), color = color)
    }
}

/** text-gray-500 font-medium secondary action (hover:bg-gray-50). */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textSize: TextUnit = TextSize.base,
    verticalPadding: Dp = 14.dp,
) {
    val c = SdTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxWidth()
            .clip(Radius.xl)
            .background(if (pressed) c.surfaceMuted else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = sdText(textSize, FontWeight.Medium), color = c.textSecondary)
    }
}

@Composable
fun IconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    background: Color = SdTheme.colors.primaryTint,
    tint: Color = SdTheme.colors.primary,
    border: Color? = null,
    elevation: Dp = 0.dp,
) {
    Box(
        modifier
            .size(size)
            .shadow(elevation, CircleShape)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Icon + title + subtitle row used by the permission, privacy, setup and troubleshooting lists. */
@Composable
fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    iconBackground: Color = SdTheme.colors.primaryTint,
    iconTint: Color = SdTheme.colors.primary,
    titleSize: TextUnit = TextSize.sm,
    subtitleSize: TextUnit = TextSize.xs,
    subtitleTopGap: Dp = 4.dp,
    iconTopOffset: Dp = 0.dp,
) {
    val c = SdTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        IconCircle(icon, Modifier.padding(top = iconTopOffset), background = iconBackground, tint = iconTint)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = sdText(titleSize, FontWeight.SemiBold), color = c.textPrimary)
            Text(
                subtitle,
                style = sdText(subtitleSize),
                color = c.textSecondary,
                modifier = Modifier.padding(top = subtitleTopGap),
            )
        }
    }
}

enum class RestAreaStyle { Bordered, Muted }

/** "Find a rest area / Search nearby places" card shared by the alert screens. */
@Composable
fun FindRestAreaCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RestAreaStyle = RestAreaStyle.Bordered,
    shape: Shape = Radius.xl2,
    titleSize: TextUnit = TextSize.sm,
    titleWeight: FontWeight = FontWeight.SemiBold,
    subtitleSize: TextUnit = TextSize.xs,
) {
    val c = SdTheme.colors
    val muted = style == RestAreaStyle.Muted
    Row(
        modifier
            .fillMaxWidth()
            .then(if (muted) Modifier else Modifier.shadow(1.dp, shape))
            .clip(shape)
            .background(if (muted) c.surfaceMuted else c.card)
            .then(if (muted) Modifier else Modifier.border(1.dp, c.borderStrong, shape))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (muted) {
            IconCircle(Icons.Outlined.LocationOn, size = 32.dp, iconSize = 18.dp, background = c.primaryTint2)
            Spacer(Modifier.width(12.dp))
        } else {
            IconCircle(Icons.Outlined.LocationOn, size = 40.dp, iconSize = 22.dp)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("Find a rest area", style = sdText(titleSize, titleWeight), color = c.textPrimary)
            Text("Search nearby places", style = sdText(subtitleSize), color = c.textSecondary)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = c.textTertiary, modifier = Modifier.size(20.dp))
    }
}

/** bg-gray-50 border-gray-100 rounded-xl stat tile. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    labelSize: TextUnit = TextSize.xs,
    labelWeight: FontWeight = FontWeight.Medium,
) {
    val c = SdTheme.colors
    Column(
        modifier
            .clip(Radius.xl)
            .background(c.surfaceMuted)
            .border(1.dp, c.border, Radius.xl)
            .padding(16.dp),
    ) {
        Text(label, style = sdText(labelSize, labelWeight), color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(value, style = sdText(TextSize.xl, FontWeight.Bold), color = c.textPrimary)
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(caption, style = sdText(TextSize.xxs), color = c.textTertiary)
        }
    }
}

/** Circular progress ring (conic gradient / SVG stroke in the design). */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color,
    trackColor: Color,
    strokeWidth: Dp,
    trackWidth: Dp = strokeWidth,
    roundCap: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = maxOf(strokeWidth.toPx(), trackWidth.toPx())
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(trackWidth.toPx()))
            drawArc(
                color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(strokeWidth.toPx(), cap = if (roundCap) StrokeCap.Round else StrokeCap.Butt),
            )
        }
        content()
    }
}

enum class HomeTab { Home, History, Tips, Settings }

/** Bottom navigation: Home · History · Tips · Settings (active = blue-600). */
@Composable
fun BottomNavBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    val c = SdTheme.colors
    Column(Modifier.background(c.background)) {
        HorizontalDivider(thickness = 1.dp, color = c.border)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NavItem(HomeTab.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home, selected, onSelect)
            NavItem(HomeTab.History, "History", Icons.Filled.History, Icons.Outlined.History, selected, onSelect)
            NavItem(HomeTab.Tips, "Tips", Icons.Filled.Lightbulb, Icons.Outlined.Lightbulb, selected, onSelect)
            NavItem(HomeTab.Settings, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, selected, onSelect)
        }
    }
}

@Composable
private fun RowScope.NavItem(
    tab: HomeTab,
    label: String,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
) {
    val c = SdTheme.colors
    val active = tab == selected
    val tint = if (active) c.primary else c.navInactive
    Column(
        Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onSelect(tab) }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (active) activeIcon else inactiveIcon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = sdText(TextSize.xxs, if (active) FontWeight.SemiBold else FontWeight.Medium), color = tint)
    }
}
