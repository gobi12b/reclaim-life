package io.github.gobi12b.reclaimlife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.gobi12b.reclaimlife.ui.insights.InsightsScreen
import io.github.gobi12b.reclaimlife.ui.learn.LearnScreen
import io.github.gobi12b.reclaimlife.ui.profile.ProfileScreen

enum class MainTab(val label: String) { TODAY("Today"), INSIGHTS("Insights"), LEARN("Learn"), PROFILE("Profile") }

/**
 * The four places after setup: Today (the dashboard and settings), Insights (the week in
 * charts), Learn and Profile (your tree and milestones). Back from the others returns to Today
 * before leaving the app.
 */
@Composable
fun MainTabs(today: @Composable (Modifier) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    BackHandler(enabled = tab != MainTab.TODAY) { tab = MainTab.TODAY }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { TabIcon(item, if (tab == item) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        val content = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        when (tab) {
            MainTab.TODAY -> today(content)
            MainTab.INSIGHTS -> InsightsScreen(content.statusBarsPadding())
            MainTab.LEARN -> LearnScreen(content.statusBarsPadding())
            MainTab.PROFILE -> ProfileScreen(content.statusBarsPadding())
        }
    }
}

/** Small drawn icons, so the app needs no icon library: a sprout, three bars, an open book, a person. */
@Composable
private fun TabIcon(tab: MainTab, color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        when (tab) {
            MainTab.TODAY -> {
                drawLine(color, Offset(w * 0.5f, w * 0.9f), Offset(w * 0.5f, w * 0.45f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                val left = Path().apply {
                    moveTo(w * 0.5f, w * 0.5f)
                    cubicTo(w * 0.42f, w * 0.18f, w * 0.18f, w * 0.14f, w * 0.1f, w * 0.22f)
                    cubicTo(w * 0.14f, w * 0.45f, w * 0.34f, w * 0.56f, w * 0.5f, w * 0.5f)
                }
                val right = Path().apply {
                    moveTo(w * 0.5f, w * 0.5f)
                    cubicTo(w * 0.58f, w * 0.18f, w * 0.82f, w * 0.14f, w * 0.9f, w * 0.22f)
                    cubicTo(w * 0.86f, w * 0.45f, w * 0.66f, w * 0.56f, w * 0.5f, w * 0.5f)
                }
                drawPath(left, color, style = stroke)
                drawPath(right, color, style = stroke)
            }
            MainTab.INSIGHTS -> {
                val barW = w * 0.18f
                listOf(0.14f to 0.5f, 0.41f to 0.2f, 0.68f to 0.62f).forEach { (x, top) ->
                    drawRoundRect(color, Offset(w * x, w * top), Size(barW, w * (0.88f - top)), CornerRadius(barW / 3f))
                }
            }
            MainTab.LEARN -> {
                val book = Path().apply {
                    moveTo(w * 0.5f, w * 0.28f)
                    cubicTo(w * 0.38f, w * 0.18f, w * 0.2f, w * 0.18f, w * 0.08f, w * 0.24f)
                    lineTo(w * 0.08f, w * 0.82f)
                    cubicTo(w * 0.2f, w * 0.76f, w * 0.38f, w * 0.76f, w * 0.5f, w * 0.86f)
                    cubicTo(w * 0.62f, w * 0.76f, w * 0.8f, w * 0.76f, w * 0.92f, w * 0.82f)
                    lineTo(w * 0.92f, w * 0.24f)
                    cubicTo(w * 0.8f, w * 0.18f, w * 0.62f, w * 0.18f, w * 0.5f, w * 0.28f)
                    close()
                }
                drawPath(book, color, style = stroke)
                drawLine(color, Offset(w * 0.5f, w * 0.28f), Offset(w * 0.5f, w * 0.86f), strokeWidth = 2.dp.toPx())
            }
            MainTab.PROFILE -> {
                drawCircle(color, radius = w * 0.18f, center = Offset(w * 0.5f, w * 0.34f), style = stroke)
                val shoulders = Path().apply {
                    moveTo(w * 0.16f, w * 0.9f)
                    cubicTo(w * 0.2f, w * 0.64f, w * 0.8f, w * 0.64f, w * 0.84f, w * 0.9f)
                }
                drawPath(shoulders, color, style = stroke)
            }
        }
    }
}
