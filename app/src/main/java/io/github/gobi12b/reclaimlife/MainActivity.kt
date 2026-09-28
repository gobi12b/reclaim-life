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
import io.github.gobi12b.reclaimlife.ui.home.HomeScreen
import io.github.gobi12b.reclaimlife.ui.onboarding.OnboardingFlow
import io.github.gobi12b.reclaimlife.ui.theme.ReclaimLifeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.onboardingComplete.value == null }
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
    val dailyLimit by viewModel.dailyReelLimit.collectAsStateWithLifecycle()
    val hourlyLimit by viewModel.hourlyReelLimit.collectAsStateWithLifecycle()
    val limitMode by viewModel.limitMode.collectAsStateWithLifecycle()
    val recentReelTimes by viewModel.recentReelTimes.collectAsStateWithLifecycle()
    val todayCount by viewModel.todayReelCount.collectAsStateWithLifecycle()
    val extraAllowance by viewModel.todayExtraAllowance.collectAsStateWithLifecycle()
    val swapActivity by viewModel.replacementActivity.collectAsStateWithLifecycle()
    val flashcardDeck by viewModel.flashcardDeck.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val daysWithinLimit by viewModel.daysWithinLimit.collectAsStateWithLifecycle()
    val daysExceededLimit by viewModel.daysExceededLimit.collectAsStateWithLifecycle()
    val pausedUntilMs by viewModel.pausedUntilMs.collectAsStateWithLifecycle()
    val dayHistory by viewModel.dayHistory.collectAsStateWithLifecycle()

    when (onboardingComplete) {
        null -> Unit // still loading settings, avoid flashing the wrong screen
        false -> OnboardingFlow(onComplete = { mode, limit, hourlyLimit, name, activity, deck ->
            viewModel.completeOnboarding(mode, limit, hourlyLimit, name, activity, deck)
        })
        true -> MainTabs { modifier ->
            HomeScreen(
                modifier = modifier,
                dailyLimit = dailyLimit,
                hourlyLimit = hourlyLimit,
                limitMode = limitMode,
                recentReelTimes = recentReelTimes,
                todayCount = todayCount,
                extraAllowance = extraAllowance,
                nickname = nickname,
                swapActivity = swapActivity,
                flashcardDeck = flashcardDeck,
                daysWithinLimit = daysWithinLimit,
                daysExceededLimit = daysExceededLimit,
                pausedUntilMs = pausedUntilMs,
                dayHistory = dayHistory,
                onLimitChange = { viewModel.updateDailyLimit(it) },
                onHourlyLimitChange = { viewModel.updateHourlyLimit(it) },
                onLimitModeChange = { viewModel.updateLimitMode(it) },
                onSwapChange = { activity, deck -> viewModel.updateSwap(activity, deck) },
                onTrackedAppsChange = { viewModel.updateTrackedApps(it) },
                onPause = { viewModel.pauseTracking(it) },
                onResume = { viewModel.resumeTracking() }
            )
        }
    }
}
