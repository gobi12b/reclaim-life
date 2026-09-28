package io.github.gobi12b.reclaimlife.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.gobi12b.reclaimlife.MainViewModel
import io.github.gobi12b.reclaimlife.ui.home.HomeScreen
import io.github.gobi12b.reclaimlife.ui.limits.LimitsScreen
import io.github.gobi12b.reclaimlife.ui.settings.SettingsScreen

private enum class TodayScreen { HOME, SETTINGS, LIMITS }

/**
 * The Today tab: Home, plus Settings (gear, top right) and Limits (the Limits row) one level
 * down. System back — predictive back included — returns to Home first.
 */
@Composable
fun TodayHost(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    var screen by rememberSaveable { mutableStateOf(TodayScreen.HOME) }
    BackHandler(enabled = screen != TodayScreen.HOME) { screen = TodayScreen.HOME }
    when (screen) {
        TodayScreen.HOME -> HomeScreen(
            viewModel = viewModel,
            onOpenSettings = { screen = TodayScreen.SETTINGS },
            onOpenLimits = { screen = TodayScreen.LIMITS },
            modifier = modifier
        )
        TodayScreen.SETTINGS -> SettingsScreen(viewModel, onBack = { screen = TodayScreen.HOME }, modifier = modifier)
        TodayScreen.LIMITS -> LimitsScreen(viewModel, onBack = { screen = TodayScreen.HOME }, modifier = modifier)
    }
}
