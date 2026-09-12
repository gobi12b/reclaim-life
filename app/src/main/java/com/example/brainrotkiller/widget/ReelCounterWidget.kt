package com.example.brainrotkiller.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrotkiller.BrainRotKillerApp
import com.example.brainrotkiller.MainActivity
import com.example.brainrotkiller.data.DEFAULT_DAILY_REEL_LIMIT
import com.example.brainrotkiller.data.Mood

private val WidgetBackground = Color(0xFF141A14)
private val WidgetOnBackground = Color(0xFFEDE0D4)
private val WidgetSubtext = Color(0xFFC4C8B8)

class ReelCounterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content(context) }
    }

    @Composable
    private fun Content(context: Context) {
        val app = context.applicationContext as BrainRotKillerApp
        val dailyLimit by app.settingsRepository.dailyReelLimit.collectAsState(initial = DEFAULT_DAILY_REEL_LIMIT)
        val todayCount by app.reelUsageRepository.todayCount.collectAsState(initial = 0)
        val extraAllowance by app.reelUsageRepository.todayExtraAllowance.collectAsState(initial = 0)
        val effectiveLimit = dailyLimit + extraAllowance
        val mood = Mood.forProgress(todayCount, effectiveLimit)

        GlanceTheme {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(WidgetBackground))
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${mood.emoji} $todayCount / $effectiveLimit",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorProvider(WidgetOnBackground)
                        )
                    )
                    Text(
                        text = "reels today",
                        style = TextStyle(fontSize = 11.sp, color = ColorProvider(WidgetSubtext))
                    )
                }
            }
        }
    }
}

class ReelCounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReelCounterWidget()
}

/** Call after anything that changes today's count, limit, or extra allowance. Safe to call with no widgets pinned. */
suspend fun refreshReelWidget(context: Context) {
    ReelCounterWidget().updateAll(context)
}
