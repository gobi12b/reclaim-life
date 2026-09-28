package io.github.gobi12b.reclaimlife.ui.learn

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Kind(val label: String, val emoji: String) { WATCH("Talk", "▶️"), LISTEN("Podcast", "🎧"), READ("Article", "📖") }

private data class Resource(val kind: Kind, val source: String, val title: String, val why: String, val url: String)

// Hand-picked and link-checked. ReclaimLife has no internet access, so this list ships with the
// app and each one opens in the browser; nothing is fetched or tracked from here.
private val WATCH = listOf(
    Resource(
        Kind.WATCH, "TED", "Why our screens make us less happy",
        "Adam Alter on why it's so hard to put the phone down — and the small rules that help.",
        "https://www.ted.com/talks/adam_alter_why_our_screens_make_us_less_happy"
    ),
    Resource(
        Kind.WATCH, "TED", "How boredom can lead to your most brilliant ideas",
        "Manoush Zomorodi on what a wandering mind gives you that a feed can't.",
        "https://www.ted.com/talks/manoush_zomorodi_how_boredom_can_lead_to_your_most_brilliant_ideas"
    ),
    Resource(
        Kind.WATCH, "TED", "How a handful of tech companies control billions of minds every day",
        "Tristan Harris on how apps are designed to hold your attention.",
        "https://www.ted.com/talks/tristan_harris_how_a_handful_of_tech_companies_control_billions_of_minds_every_day"
    ),
    Resource(
        Kind.LISTEN, "Huberman Lab", "Controlling your dopamine for motivation, focus & satisfaction",
        "How dopamine peaks and dips work, and why endless novelty leaves you flat.",
        "https://www.hubermanlab.com/episode/controlling-your-dopamine-for-motivation-focus-and-satisfaction"
    )
)

private val READ = listOf(
    Resource(
        Kind.READ, "Stanford Medicine", "Addictive potential of social media, explained",
        "Why feeds can be habit-forming, from Stanford Medicine.",
        "https://med.stanford.edu/news/insights/2021/10/addictive-potential-of-social-media-explained.html"
    ),
    Resource(
        Kind.READ, "Center for Humane Technology", "Control your tech use",
        "Practical steps to make your phone work for you, not the other way round.",
        "https://www.humanetech.com/take-control"
    ),
    Resource(
        Kind.READ, "James Clear", "Atomic Habits summary",
        "Swapping a habit beats fighting it — the idea behind your 2-minute swap.",
        "https://jamesclear.com/atomic-habits-summary"
    )
)

private val TRY_TODAY = listOf(
    "📱" to "Move Instagram and YouTube off your home screen, into a folder.",
    "🔕" to "Turn off their notifications — you'll check in on your terms.",
    "🛏️" to "Charge your phone outside the bedroom tonight.",
    "⚫" to "Try grayscale for an evening (it's in Digital Wellbeing or Accessibility settings) — feeds lose their pull.",
    "🎯" to "Before you open an app, say what you came for."
)

/**
 * Short, trusted reads and talks on screen time and attention, plus a few things to try today.
 * Links open in the browser; the app itself stays offline.
 */
@Composable
fun LearnScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 4.dp)) {
            Text("Learn", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(
                "Trusted talks and reads on screen time, dopamine and getting your attention back.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TryTodayCard()

        SectionTitle("Watch & listen")
        WATCH.forEach { ResourceCard(it) }

        SectionTitle("Read")
        READ.forEach { ResourceCard(it) }

        Text(
            "Links open in your browser. ReclaimLife itself has no internet access.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
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

@Composable
private fun TryTodayCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Try one today", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            TRY_TODAY.forEach { (emoji, tip) ->
                Row(modifier = Modifier.padding(top = 10.dp)) {
                    Text(emoji, fontSize = 16.sp, modifier = Modifier.clearAndSetSemantics { })
                    Text(tip, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun ResourceCard(resource: Resource) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Open in browser") {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(resource.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
            ) {
                Text(resource.kind.emoji, fontSize = 20.sp, modifier = Modifier.clearAndSetSemantics { })
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    "${resource.kind.label} · ${resource.source}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(resource.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                Text(
                    resource.why,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text("↗", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics { })
        }
    }
}
