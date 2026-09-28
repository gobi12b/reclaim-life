package io.github.gobi12b.reclaimlife.ui.learn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gobi12b.reclaimlife.ui.common.appLabel
import io.github.gobi12b.reclaimlife.ui.common.isInstalled
import io.github.gobi12b.reclaimlife.ui.common.rememberTrackedApps
import java.util.Calendar

private enum class Kind(val verb: String) { WATCH("Watch"), LISTEN("Listen"), READ("Read") }

/** [minutes] is roughly how long it takes — in a screen-time app, what a link costs is worth saying up front. */
private data class Resource(val kind: Kind, val source: String, val minutes: Int, val title: String, val why: String, val url: String)

// Hand-picked and link-checked. ReclaimLife has no internet access, so this list ships with the
// app and each one opens in the browser; nothing is fetched or tracked from here. Shortest first
// within each section, so the easiest start is the first thing you see.
private val WATCH = listOf(
    Resource(
        Kind.WATCH, "TED", 11, "Why our screens make us less happy",
        "Why it's hard to put the phone down.",
        "https://www.ted.com/talks/adam_alter_why_our_screens_make_us_less_happy"
    ),
    Resource(
        Kind.WATCH, "TED", 16, "How boredom can lead to your most brilliant ideas",
        "What a wandering mind gives you that a feed can't.",
        "https://www.ted.com/talks/manoush_zomorodi_how_boredom_can_lead_to_your_most_brilliant_ideas"
    ),
    Resource(
        Kind.WATCH, "TED", 17, "How a handful of tech companies control billions of minds every day",
        "How apps are designed to hold your attention.",
        "https://www.ted.com/talks/tristan_harris_how_a_handful_of_tech_companies_control_billions_of_minds_every_day"
    ),
    Resource(
        Kind.LISTEN, "Huberman Lab", 120, "Controlling your dopamine for motivation, focus & satisfaction",
        "How dopamine works. Good for a walk.",
        "https://www.hubermanlab.com/episode/controlling-your-dopamine-for-motivation-focus-and-satisfaction"
    )
)

private val READ = listOf(
    Resource(
        Kind.READ, "Center for Humane Technology", 5, "Control your tech use",
        "Make your phone work for you.",
        "https://www.humanetech.com/take-control"
    ),
    Resource(
        Kind.READ, "Stanford Medicine", 6, "Addictive potential of social media, explained",
        "Why feeds are habit-forming.",
        "https://med.stanford.edu/news/insights/2021/10/addictive-potential-of-social-media-explained.html"
    ),
    Resource(
        Kind.READ, "James Clear", 15, "Atomic Habits summary",
        "Swapping a habit beats fighting it.",
        "https://jamesclear.com/atomic-habits-summary"
    )
)

/** A small thing to try, with at most one action that takes you straight to where it's done. */
private data class Tip(val text: String, val actionLabel: String? = null, val action: ((Context) -> Unit)? = null)

/** Tips name the apps you actually chose, not a fixed pair. */
private fun tips(appNames: String, firstApp: String?): List<Tip> = listOf(
    Tip(
        "Turn off notifications for $appNames.",
        firstApp?.let { "Open notification settings" },
        firstApp?.let { pkg -> { context: Context -> openNotificationSettings(context, pkg) } }
    ),
    Tip("Move $appNames off your home screen."),
    Tip("Charge your phone outside the bedroom tonight."),
    Tip(
        "Try grayscale for an evening. Feeds lose their pull.",
        "Open Accessibility settings",
        { context -> launch(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    ),
    Tip("Before opening an app, say why. Then close it."),
    Tip("Bored? Look out of a window for a minute.")
)

private fun launch(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openNotificationSettings(context: Context, packageName: String) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    }
    launch(context, intent)
}

/**
 * One small thing to try today (a new one each day, or on request), then short, trusted talks and
 * reads — each with how long it takes. Links open in the browser; the app itself stays offline.
 */
@Composable
fun LearnScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tracked = rememberTrackedApps()
    val names = remember(tracked) {
        tracked.map { it.packageName }.filter { isInstalled(context, it) }.map { appLabel(context, it) }
    }
    val appNames = when (names.size) {
        0 -> "your feed apps"
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> "${names[0]}, ${names[1]} and the rest"
    }
    val firstApp = tracked.firstOrNull { isInstalled(context, it.packageName) }?.packageName
    val allTips = remember(appNames, firstApp) { tips(appNames, firstApp) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            Text("Learn", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
        }

        TipCard(allTips)

        SectionTitle("Watch & listen")
        WATCH.forEach { ResourceCard(it) }

        SectionTitle("Read")
        READ.forEach { ResourceCard(it) }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 4.dp, top = 12.dp)
            .semantics { heading() }
    )
}

/**
 * One idea at a time — a list of five read as homework. It changes daily; "Another idea" moves on
 * for anyone who's already doing this one.
 */
@Composable
private fun TipCard(tips: List<Tip>) {
    val context = LocalContext.current
    val dayOfYear = remember { Calendar.getInstance().get(Calendar.DAY_OF_YEAR) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val tip = tips[(dayOfYear + offset) % tips.size]

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Try this today", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            AnimatedContent(targetState = tip, label = "tip", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) { shown ->
                Column {
                    Text(shown.text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                        if (shown.actionLabel != null && shown.action != null) {
                            FilledTonalButton(onClick = { shown.action.invoke(context) }) { Text(shown.actionLabel) }
                        }
                        TextButton(onClick = { offset++ }) { Text("Another idea") }
                    }
                }
            }
        }
    }
}

private fun lengthLabel(resource: Resource): String {
    val time = if (resource.minutes >= 60) "${resource.minutes / 60} h" else "${resource.minutes} min"
    return when (resource.kind) {
        Kind.READ -> "$time read"
        Kind.WATCH -> "$time talk"
        Kind.LISTEN -> "$time podcast"
    }
}

/** Title leads; source and length sit under it, so what it costs in time is known before tapping. */
@Composable
private fun ResourceCard(resource: Resource) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = "${resource.kind.verb} in your browser") {
                launch(context, Intent(Intent.ACTION_VIEW, Uri.parse(resource.url)))
            }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(resource.title, fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${resource.source} · ${lengthLabel(resource)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    resource.why,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text("↗", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics { })
        }
    }
}
