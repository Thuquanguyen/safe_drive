package com.mobile.safedrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mobile.safedrive.ui.components.BottomNavBar
import com.mobile.safedrive.ui.components.HomeTab
import com.mobile.safedrive.ui.components.IconCircle
import com.mobile.safedrive.ui.components.TopBar
import com.mobile.safedrive.ui.theme.Radius
import com.mobile.safedrive.ui.theme.SdTheme
import com.mobile.safedrive.ui.theme.TextSize
import com.mobile.safedrive.ui.theme.sdText

private data class Tip(val icon: ImageVector, val title: String, val body: String)

private val TIPS = listOf(
    Tip(Icons.Filled.LocalCafe, "Take Breaks", "Stop every 2 hours\nfor a short break."),
    Tip(Icons.Filled.LocalDrink, "Stay Hydrated", "Drink water\nregularly."),
    Tip(Icons.Filled.Bedtime, "Good Sleep", "Get 7-8 hours\nof sleep."),
    Tip(Icons.Filled.Restaurant, "Avoid Heavy Meals", "Eat light before\nlong drives."),
)

@Composable
fun TipsScreen(onBack: () -> Unit, onTab: (HomeTab) -> Unit) {
    val c = SdTheme.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        TopBar("Tips", onBack = onBack, verticalPadding = 8.dp)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(c.pageMuted)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TIPS.forEach { TipCard(it) }
        }
        BottomNavBar(HomeTab.Tips, onTab)
    }
}

@Composable
private fun TipCard(tip: Tip) {
    val c = SdTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(1.dp, Radius.xl2)
            .clip(Radius.xl2)
            .background(c.card)
            .border(1.dp, c.border, Radius.xl2)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconCircle(tip.icon, size = 48.dp, iconSize = 24.dp, tint = c.accent)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(tip.title, style = sdText(TextSize.base, FontWeight.SemiBold), color = c.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(tip.body, style = sdText(TextSize.sm, lineHeight = 17.5.sp), color = c.textSecondary)
        }
    }
}
