package io.github.gobi12b.reclaimlife.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GreenDark,
    onPrimary = OnGreenDark,
    secondary = BrownDark,
    onSecondary = OnBrownDark,
    tertiary = GoldDark,
    onTertiary = OnGoldDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = BackgroundDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorDark,
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = OnGreenContainerDark,
    secondaryContainer = BrownContainerDark,
    onSecondaryContainer = OnBrownContainerDark,
    tertiaryContainer = GoldContainerDark,
    onTertiaryContainer = OnGoldContainerDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = SurfaceLowestDark,
    surfaceContainerLow = SurfaceLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceHighDark,
    surfaceContainerHighest = SurfaceHighestDark
)

private val LightColorScheme = lightColorScheme(
    primary = GreenLight,
    onPrimary = OnGreenLight,
    secondary = BrownLight,
    onSecondary = OnBrownLight,
    tertiary = GoldLight,
    onTertiary = OnGoldLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = BackgroundLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    error = ErrorLight,
    primaryContainer = GreenContainerLight,
    onPrimaryContainer = OnGreenContainerLight,
    secondaryContainer = BrownContainerLight,
    onSecondaryContainer = OnBrownContainerLight,
    tertiaryContainer = GoldContainerLight,
    onTertiaryContainer = OnGoldContainerLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    surfaceContainerLowest = SurfaceLowestLight,
    surfaceContainerLow = SurfaceLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceHighLight,
    surfaceContainerHighest = SurfaceHighestLight
)

/**
 * Home-only roles Material's scheme has no slot for: the hero gradient, the text on it, and the
 * tree. Kept beside the scheme so light and dark switch together.
 */
@Immutable
data class ReclaimColors(
    val heroTop: Color,
    val heroBottom: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroGround: Color,
    val sunGlow: Color,
    val plantStem: Color,
    val plantLeaf: Color,
    val plantLeafBright: Color,
    val plantBloom: Color,
    val treeBark: Color,
    val treeBlossom: Color,
    val treeFruit: Color,
    /** The moon over a resting tree. */
    val restMoon: Color
)

val LightReclaimColors = ReclaimColors(
    heroTop = HeroTopLight,
    heroBottom = HeroBottomLight,
    onHero = OnHeroLight,
    onHeroMuted = OnHeroMutedLight,
    heroGround = HeroGroundLight,
    sunGlow = SunGlowLight,
    plantStem = PlantStemLight,
    plantLeaf = PlantLeafLight,
    plantLeafBright = PlantLeafBrightLight,
    plantBloom = PlantBloomLight,
    treeBark = TreeBarkLight,
    treeBlossom = TreeBlossomLight,
    treeFruit = TreeFruitLight,
    restMoon = RestMoonLight
)

val DarkReclaimColors = ReclaimColors(
    heroTop = HeroTopDark,
    heroBottom = HeroBottomDark,
    onHero = OnHeroDark,
    onHeroMuted = OnHeroMutedDark,
    heroGround = HeroGroundDark,
    sunGlow = SunGlowDark,
    plantStem = PlantStemDark,
    plantLeaf = PlantLeafDark,
    plantLeafBright = PlantLeafBrightDark,
    plantBloom = PlantBloomDark,
    treeBark = TreeBarkDark,
    treeBlossom = TreeBlossomDark,
    treeFruit = TreeFruitDark,
    restMoon = RestMoonDark
)

val LocalReclaimColors = staticCompositionLocalOf { LightReclaimColors }

@Suppress("UnusedReceiverParameter")
val MaterialTheme.reclaim: ReclaimColors
    @Composable @ReadOnlyComposable get() = LocalReclaimColors.current

@Composable
fun ReclaimLifeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the brand's sunrise palette isn't replaced by wallpaper-derived colors.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalReclaimColors provides if (darkTheme) DarkReclaimColors else LightReclaimColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}