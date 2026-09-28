package io.github.gobi12b.reclaimlife.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

const val PRIVACY_POLICY_URL = "https://github.com/gobi12b/reclaim-life/blob/main/PRIVACY.md"

/** Opens the privacy policy in a browser, or says where it is if there's no browser to open it. */
fun openPrivacyPolicy(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found. The policy is at $PRIVACY_POLICY_URL", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(onClick = { openPrivacyPolicy(context) }, modifier = modifier) {
        Text("Privacy policy", fontSize = 13.sp)
    }
}

/**
 * Prominent disclosure for the Accessibility API, shown before *every* path to the Accessibility
 * settings (onboarding, and the Home banner via [AccessibilityConsentDialog]). Every claim here
 * must stay true of the code: the service only gets events from the apps the user chose
 * (Instagram and YouTube by default), the only thing read outside them is which app is in front,
 * nothing but counts and times is stored, and the app has no INTERNET permission at all.
 */
@Composable
fun AccessibilityDisclosure(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            DisclosureItem(
                title = "What it reads",
                body = "Only the apps you pick: when you open them, and scrolling in Instagram and " +
                    "YouTube to count reels."
            )
            DisclosureItem(
                title = "What it never reads",
                body = "Messages, typing, passwords or reel content. Only counts are saved."
            )
            DisclosureItem(
                title = "Where it goes",
                body = "Nowhere. No internet access. It stays on your phone."
            )
            PrivacyPolicyLink(modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun DisclosureItem(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            text = body,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/** Disclosure plus explicit consent; only "Agree" goes on to the Accessibility settings. */
@Composable
fun AccessibilityConsentDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Before you turn it on") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Needed to count reels.",
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                AccessibilityDisclosure()
            }
        },
        confirmButton = {
            Button(onClick = {
                onDismiss()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) {
                Text("Agree & open settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
