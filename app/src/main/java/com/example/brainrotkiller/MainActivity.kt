package com.example.brainrotkiller

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
import com.example.brainrotkiller.ui.home.HomeScreen
import com.example.brainrotkiller.ui.onboarding.OnboardingFlow
import com.example.brainrotkiller.ui.theme.BrainRotKillerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { viewModel.onboardingComplete.value == null }
        enableEdgeToEdge()
        setContent {
            BrainRotKillerTheme {
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
    val todayCount by viewModel.todayReelCount.collectAsStateWithLifecycle()

    when (onboardingComplete) {
        null -> Unit // still loading settings, avoid flashing the wrong screen
        false -> OnboardingFlow(onComplete = { limit -> viewModel.completeOnboarding(limit) })
        true -> HomeScreen(
            dailyLimit = dailyLimit,
            todayCount = todayCount,
            onLimitChange = { viewModel.updateDailyLimit(it) }
        )
    }
}
