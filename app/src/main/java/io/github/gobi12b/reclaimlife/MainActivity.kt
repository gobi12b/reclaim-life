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

    when (onboardingComplete) {
        null -> Unit // still loading settings, avoid flashing the wrong screen
        false -> OnboardingFlow(onComplete = { setup ->
            viewModel.completeOnboarding(
                setup.limitMode, setup.dailyLimit, setup.hourlyLimit, setup.nickname,
                setup.activity, setup.deck, setup.apps, setup.gateEnabled
            )
        })
        true -> MainTabs { modifier -> TodayHost(viewModel, modifier) }
    }
}
