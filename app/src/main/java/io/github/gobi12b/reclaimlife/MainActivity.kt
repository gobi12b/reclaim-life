package io.github.gobi12b.reclaimlife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.gobi12b.reclaimlife.ui.MainTabs
import io.github.gobi12b.reclaimlife.ui.TodayHost
import io.github.gobi12b.reclaimlife.ui.onboarding.MeetTreeScreen
import io.github.gobi12b.reclaimlife.ui.onboarding.OnboardingFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import io.github.gobi12b.reclaimlife.ui.theme.ReclaimLifeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            viewModel.onboardingComplete.value == null ||
                (viewModel.onboardingComplete.value == true && viewModel.treeIntroSeen.value == null)
        }
        enableEdgeToEdge()
        setContent {
            ReclaimLifeTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppContent(viewModel: MainViewModel) {
    val onboardingComplete by viewModel.onboardingComplete.collectAsStateWithLifecycle()

    when (onboardingComplete) {
        null -> Unit // still loading settings, avoid flashing the wrong screen
        false -> OnboardingFlow(onComplete = { setup ->
            viewModel.completeOnboarding(
                setup.limitMode, setup.dailyLimit, setup.hourlyLimit, setup.nickname,
                setup.activity, setup.deck, setup.apps, setup.gateEnabled
            )
        })
        true -> TreeIntroOrTabs(viewModel)
    }
}

/**
 * Meet your tree, once, after onboarding (existing installs see it on their first launch after
 * the update); then the tabs. Waits briefly for the upgrade backfill so the copy doesn't switch.
 */
@Composable
private fun TreeIntroOrTabs(viewModel: MainViewModel) {
    val introSeen by viewModel.treeIntroSeen.collectAsStateWithLifecycle()
    val backfilled by viewModel.treeBackfilled.collectAsStateWithLifecycle()
    val isUpgrade by viewModel.treeIsUpgrade.collectAsStateWithLifecycle()
    val growth by viewModel.treeGrowth.collectAsStateWithLifecycle()
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1500)
        waited = true
    }
    when (introSeen) {
        null -> Unit // still loading, avoid flashing the intro for someone who's seen it
        false -> if (backfilled || waited) {
            MeetTreeScreen(growth.first, growth.second, isUpgrade, onDone = viewModel::completeTreeIntro)
        }
        true -> MainTabs { modifier -> TodayHost(viewModel, modifier) }
    }
}
