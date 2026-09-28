# ReclaimLife — Home Revamp ("Canopy")

2026-09-28 · Gobinath Manokaran · Design spec for an engineer agent. Builds on `docs/specs/2026-09-28-app-selection-positive-home-open-pause.md` (Feature 2 "Home layout" and its design sign-off are binding here).

## 1. Summary

Home today is seven identical 24dp grey cards, with the hero only a greener one. Nothing says "you won something", and every section carries the same weight.
This revamp gives Home one focal point: a **hero panel with a drawn plant that grows as time won back adds up**, set in a serif display number on a leaf-green gradient with a hill and a soft sun.
Everything else steps back. The Limits row becomes a slim pill with a ring gauge, the insight becomes a soft note, and Last 24 hours and This week sit directly on the background under section headings, with no card boxes.
Rhythm comes from spacing (12dp inside a group, 32dp between groups) and a real type scale. It adds no new dependencies, images or icon library, and dynamic colour stays off.
Behaviour, callbacks, data and numbers stay as they are. Only the presentation changes, plus two display bugs this spec fixes (a possible "0 min" hero and an under-contrast streak label).

**Non-goals**

- No changes to `Progress`, `LimitStatus`, `ProgressEngine`, the insight rules, or any repository. The only data-file edit is to the `InsightKind` enum (drop `emoji`, rename one icon description; see Task 7).
- No changes to Settings, Limits, Insights, Learn, onboarding, the pause sheet, the swap dialog, the block screen, the gate, the widget or the overlay. The shared tokens (Type.kt, Color.kt, Theme.kt) change, but those screens set most sizes with `fontSize` directly, so they barely move (see §4.1).
- No new Gradle dependencies, no downloadable fonts, no bitmaps or vector drawables for Home. Everything is drawn with `Canvas`.
- No sliders and no count-up number animations.

## 2. Diagnosis of the current Home

Line numbers refer to the working tree on `feature/open-pause-and-limit-ux`, including the uncommitted changes.

1. **One box for everything.** `HomeCard` (`ui/home/HomeCards.kt:91-119`) gives every section the same 24dp shape, 20dp padding and full width. Seven stacked cards differ only in container colour, so the eye has no entry point.
2. **The hero doesn't feel like a reward.** `SavedCard` (`HomeCards.kt:416-425`) is a `primaryContainer` card holding a sentence whose number is `headlineMedium` 28sp bold (`HomeCards.kt:360-371`), inside a 16sp sentence. A 1.75× ratio is weak for a hero, and there's no visual, no brand and no sense of growth.
3. **Monotone rhythm.** The whole screen is one `Column` with `spacedBy(12.dp)` (`ui/home/HomeScreen.kt:166-172`). Related and unrelated sections sit the same distance apart, so nothing groups.
4. **No type scale.** `Type.kt:7-14` customises only `bodyLarge`. Home uses Material defaults: the greeting (`titleLarge`, `HomeCards.kt:161`), card titles (`titleMedium`) and the Limits row (`titleMedium`, `HomeCards.kt:332`) all read at nearly the same weight.
5. **Weak brand expression.** The only brand element is a 32dp `SproutBadge` (`HomeCards.kt:158`). The sunrise, hill and leaf shapes in `ui/common/BrandMark.kt:34-73` and the gold accent never appear on Home.
6. **Emoji icons.** The insight icon is an emoji rendered as text (`HomeCards.kt:560`). It looks different on every OEM, clashes with the drawn icon family (`ui/MainTabs.kt:75-119`, `HomeCards.kt:186-207`), and its `primaryContainer` disc competes with the hero's green.
7. **Secondary sections compete with the hero.** Last 24 hours (`HomeCards.kt:593-606`) and This week (`HomeCards.kt:703-719`) each get their own card and `titleMedium` heading, so they carry the same visual weight as the hero.
8. **The Limits row looks like just another card, and its near-limit signal is weak.** It's a full `HomeCard` (`HomeCards.kt:319-338`). The near-limit tint is `secondaryContainer` (`HomeCards.kt:321`), which in dark mode (#5A3F22) is close to the normal `surfaceContainerHigh` (#283028). There's no gauge.
9. **Nested, small tap targets in the hero.** "Allow Usage access" is an inline link inside the clickable card (`HomeCards.kt:432-441`), and "By app" sets `contentPadding` end to 0 (`HomeCards.kt:451`). Both sit inside another click target, and the link is shorter than 48dp.
10. **Contrast bug.** The streak label is `primary` text (#3F7D4A) on `surfaceContainerHigh` (#E6E4D9) (`HomeCards.kt:713-717`). That's **3.88:1**, below 4.5:1 for 14sp text.
11. **TalkBack reads raw dates.** The week cells say "2026-09-24, within limit" (`HomeCards.kt:727-732`).
12. **Glyphs are font characters.** The trend arrows "↑ ↓ →" (`HomeCards.kt:609-613`) and the day marks "✓ ×" (`HomeCards.kt:778, 789`) depend on the font, so their size and weight vary by device.
13. **A "0 min" hero is still possible.** On day 1, before anything is saved (`sinceStartedMs` < 1 min and today < 1 min), the headline falls through to `"0 min saved today"` (`HomeCards.kt:391-398`). Before the waking window, a `yesterdayMs` of 0 gives "Yesterday you saved 0 min" (`HomeCards.kt:394-395`). Both break the sign-off rule "never 0 min as the hero".
14. **What you see and what you hear differ.** On a zero day, "Nothing saved yet today" is only in the TalkBack summary (`HomeCards.kt:404-405`) and is never shown.
15. **One component, two jobs.** `Last24hCard(hero: Boolean)` (`HomeCards.kt:580-606`) switches layout on a flag, so the hero variant can't look like a hero.
16. **The pause calm line can say "0 min".** `"You've won back ${formatSaved(progress.today.totalMs)} today."` (`HomeScreen.kt:243`) shows "You've won back 0 min today." on a zero day.
17. **Home code is scattered.** `AccessibilityBanner` and `AlertGlyph` live in `HomeScreen.kt:291-370`, while every other card is in the 800-line `HomeCards.kt`.
18. **The skeleton is plain text** in a card (`HomeCards.kt:342-349`). It has no shape of the content that's coming, so the layout jumps when data arrives.

## 3. Design direction

- **One focal point.** The hero panel is the only large, colourful surface. Everything else is quieter.
- **Reward, not report.** Time won back grows a drawn plant that never shrinks, because it's keyed to "since you started", which only goes up.
- **Fewer boxes.** Filled surfaces are used only for things you act on or should notice: hero, Limits pill, insight note and alerts. Reading content sits on the background.
- **Space groups things.** 12dp inside a group, 32dp between groups. No dividers.
- **Two voices.** A serif (system `FontFamily.Serif`) for the greeting and the numbers that matter, and the system sans for everything else.
- **Calm colour.** Green for progress, gold for "notice this" (near limit, pause), brown-sand for the insight note. Red is used only for "the counter is broken", never for usage going up.
- **Every glyph is drawn.** Canvas icons only, with no font-character or emoji icons on Home.
- **Motion is a small gift.** The plant grows once and numbers fade. With animations turned off, it all appears in its final state.
- **Home has at most one filled button**, and only when something is broken (the Accessibility banner).

## 4. Design tokens

### 4.1 Type scale (`ui/theme/Type.kt`)

Add `val DisplayFamily: FontFamily = FontFamily.Serif` at the top of Type.kt. It's one constant, so the serif can be swapped for `FontFamily.Default` in one line if needed. Replace `Typography` with the following. Styles not listed keep Material 3 defaults.

| Style | Family | Weight | Size | Line height | Letter spacing | Used for |
| --- | --- | --- | --- | --- | --- | --- |
| `displayMedium` | DisplayFamily | Normal (400) | 56.sp | 60.sp | (-0.5).sp | Hero number digits |
| `displaySmall` | DisplayFamily | Normal (400) | 44.sp | 52.sp | (-0.25).sp | Last 24 h hero digits; fresh-start headline |
| `headlineSmall` | DisplayFamily | Normal (400) | 26.sp | 32.sp | 0.sp | Greeting; Last 24 h section value |
| `titleLarge` | Default | Normal (400) | 22.sp | 28.sp | 0.sp | (M3 default, unchanged, listed for completeness: top app bars elsewhere) |
| `titleMedium` | Default | SemiBold (600) | 16.sp | 24.sp | 0.1.sp | Section headings, pill "20 left", banner titles |
| `titleSmall` | Default | SemiBold (600) | 14.sp | 20.sp | 0.1.sp | Hero context line at 200% font fallback, streak chip |
| `bodyLarge` | Default | Normal (400) | 16.sp | 24.sp | 0.5.sp | (existing, unchanged) Insight text |
| `bodyMedium` | Default | Normal (400) | 14.sp | 20.sp | 0.25.sp | (M3 default) Supporting lines |
| `bodySmall` | Default | Normal (400) | 13.sp | 18.sp | 0.2.sp | Notes, footers (up from 12/16 for legibility) |
| `labelLarge` | Default | Medium (500) | 14.sp | 20.sp | 0.1.sp | (M3 default) Buttons |
| `labelMedium` | Default | Medium (500) | 12.sp | 16.sp | 0.5.sp | (M3 default, do **not** change: the NavigationBar uses it) Weekday initials |

Hero **unit** words ("h", "min") are a `SpanStyle` built in code, not a typography role: `DisplayFamily`, Normal, 24.sp, letterSpacing 0.sp. They sit on the digits' baseline inside the same `Text`. See `HeroNumberText`.

Impact outside Home: today only `HomeScreen.kt` and `HomeCards.kt` read `MaterialTheme.typography.*`. Other screens pass `fontSize` directly. The overrides reach other screens only through Material components' defaults: `titleMedium` (ModalBottomSheet/ListItem are not used with it), `bodySmall` (supporting text in `OutlinedTextField`, 12 → 13sp) and dialogs (`headlineSmall` in `AlertDialog` titles becomes serif 26sp). Accept the dialog title change. It's on-brand. Check `AccessibilityConsentDialog` and Settings' dialogs visually at 100% and 200% in Task 1.

### 4.2 Spacing (`ui/theme/Dimens.kt`, new)

```kotlin
object Spacing { val xxs = 4.dp; val xs = 8.dp; val s = 12.dp; val m = 16.dp; val l = 20.dp; val xl = 24.dp; val xxl = 32.dp }
object Radii { val hero = 28.dp; val panel = 20.dp; val pill = 32.dp; val bar = 8.dp; val chip = 16.dp }
object HomeLayout { val gutter = 20.dp; val minTouch = 48.dp }
```

- Screen gutter: 20dp left and right (up from 16).
- Inside a group (alerts stack; hero → pill → insight): 12dp.
- Between groups (top group → Last 24 hours → This week): 32dp.
- Panel inner padding: hero 24dp; pill 16dp start / 12dp end; insight and banners 16dp.

### 4.3 Radii and elevation

- Hero 28dp, pill 32dp (fully round at 64dp height), insight note and banners 20dp, usage bar 8dp, streak chip 16dp.
- **All surfaces are flat: 0dp shadow and 0dp tonal elevation.** Depth comes from tone only: background `surface` → `surfaceContainerLow` (insight) → `surfaceContainerHigh` (pill, neutral banner) → hero gradient.

### 4.4 Colour

Existing roles stay. Add these Home-only roles as `ReclaimColors` in `ui/theme/Theme.kt` (hex values live in `Color.kt`), provided by `staticCompositionLocalOf` and read through `MaterialTheme.reclaim` (an extension property, see Task 1).

| Role | Light | Dark | Use |
| --- | --- | --- | --- |
| `heroTop` | #E3F2DC | #223D27 | Hero gradient, top |
| `heroBottom` | #C9E6C5 | #18301D | Hero gradient, bottom |
| `onHero` | #0B3818 | #DDF5DF | Hero number and text |
| `onHeroMuted` | #2F5A37 | #A8D4AF | Hero supporting lines, notes |
| `heroGround` | #B8DDB6 | #2B4B31 | Hill fill and the hero's ground band |
| `sunGlow` | #FFE6A8 | #F0C868 | Radial glow, drawn at alpha 0.70 (light) / 0.18 (dark), fading to 0 |
| `plantStem` | #2F7D3F | #8FD19E | Stem |
| `plantLeaf` | #2F8646 | #6FC486 | Left leaves |
| `plantLeafBright` | #4E7F24 | #C3E58F | Right leaves |
| `plantBloom` | #8C5E00 | #F0C868 | Seed, flower petals |

**Computed contrast (WCAG 2.x relative luminance)**

| Pair | Light | Dark | Need |
| --- | --- | --- | --- |
| `onHero` on `heroTop` / `heroBottom` | 11.30 / 9.80 | 10.31 / 12.30 | 4.5 |
| `onHeroMuted` on `heroTop` / `heroBottom` | 6.82 / 5.92 | 7.20 / 8.59 | 4.5 |
| `onHero` / `onHeroMuted` on `heroGround` | 8.82 / 5.33 | 8.45 / 5.90 | 4.5 |
| `plantStem` on `heroBottom` / `heroGround` | 3.78 / 3.41 | 7.96 / 5.47 | 3.0 (graphic) |
| `plantLeaf` on `heroBottom` / `heroTop` | 3.38 / 3.90 | 6.70 / 5.62 | 3.0 |
| `plantLeafBright` on `heroBottom` | 3.56 | 10.09 | 3.0 |
| `plantBloom` on `heroBottom` / `heroTop` | 4.20 / 4.84 | 8.90 / — | 3.0 |
| `onSurface` on `surface` | 13.67 | 14.19 | 4.5 |
| `onSurfaceVariant` on `surface` | 6.53 | 10.36 | 4.5 |
| `onSurface` / `onSurfaceVariant` on `surfaceContainerLow` | 13.06 / 6.24 | — / 9.72 | 4.5 |
| `onSurface` / `onSurfaceVariant` on `surfaceContainerHigh` | 11.80 / 5.64 | 10.92 / 7.97 | 4.5 |
| Gauge fill `primary` on `surfaceContainerHigh` | 3.88 | 7.63 | 3.0 |
| `onTertiaryContainer` on `tertiaryContainer` (near-limit pill, pause banner) | 11.45 | 7.47 | 4.5 |
| `onSecondaryContainer` on `secondaryContainer` (insight glyph) | 12.56 | 7.39 | 3.0 |
| `onPrimaryContainer` on `primaryContainer` (streak chip) | 10.17 | 7.55 | 4.5 |
| `onPrimary` on `primary` (week check mark) | 4.95 | 7.40 | 3.0 |
| `primary` ring on `surface` (today ring) | 4.49 | 9.92 | 3.0 |
| `outline` on `surface` (over-limit ring, no-data ring) | 4.04 | 5.58 | 3.0 |
| `onErrorContainer` on `errorContainer` (M3 defaults) | 13.26 | 7.24 | 4.5 |

Decorative, with no contrast requirement: `heroGround` against `heroBottom` (1.14 light, 1.22 dark), `sunGlow`, the gauge track (`outlineVariant`), and the hero panel edge against `surface` (1.06 light, 1.49 dark). None of these carry meaning.

**Rule:** never use `primary` for small text on a light surface. Light `primary` on `surface` is 4.49:1, just under 4.5. Use `onPrimaryContainer` on `primaryContainer` instead (the streak chip does).

### 4.5 Motion

| Token | Value |
| --- | --- |
| `Motion.plantGrowMs` | 700ms, `FastOutSlowInEasing` |
| `Motion.fadeMs` | 200ms, `LinearOutSlowInEasing` |
| `Motion.expandMs` | 250ms, `FastOutSlowInEasing` (banners, By app, help text) |
| `Motion.gaugeMs` | 300ms, `FastOutSlowInEasing` |
| `Motion.skeletonPulseMs` | 900ms per half-cycle, `RepeatMode.Reverse`, alpha 0.55 ↔ 0.85 |

Reduced motion: add `@Composable fun rememberReducedMotion(): Boolean` in `ui/common/Motion.kt`. It returns `true` when `Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f`, re-read on `ON_RESUME` (use the same lifecycle pattern as `rememberHasUsageAccess` in `ui/common/UsageState.kt:121`). When it's true, every Home animation snaps to its end state: `snap()` specs, `AnimatedVisibility` with `EnterTransition.None`/`ExitTransition.None`, the skeleton at a static alpha of 0.7, and the plant drawn fully grown.

## 5. Components

All Home composables are `internal` in package `io.github.gobi12b.reclaimlife.ui.home` unless noted. Files:

- `HomeScreen.kt`: state, ordering and sheets only.
- `HomeHero.kt`: `SavedHero`, `Last24hHero`, `HeroSkeleton`, `HeroPanel`, `HeroNumberText`, `savedHeadline()`, `SavedDetailSheet`, `spoken()`.
- `GrowthPlant.kt`: `GrowthPlant`, `HeroBackdrop`, `growthStage()`, `GrowthStage`.
- `HomeSections.kt`: `HomeHeader`, `LimitsPill`, `LimitGauge`, `InsightNote`, `Last24hSection`, `UsageBar`, `UsageLegend`, `TrendLine`, `WeekSection`, `StreakChip`, `DayMark`, `SectionHeading`, `PauseTrackingButton`.
- `HomeBanners.kt`: `HomeBanner`, `BannerTone`, `AccessibilityBanner`, `PausedBanner`, `PendingPauseBanner`, `AppsCheckBanner`.
- `HomeGlyphs.kt`: `Chevron`, `GearIcon`, `PauseBars`, `AlertGlyph`, `TrendGlyph`, `CheckGlyph`, `CrossGlyph`, `LeafGlyph`, `InsightGlyph`.
- `HomeCards.kt`: **deleted** at the end.

"Large font" below means `LocalDensity.current.fontScale >= 1.5f`, read once in `HomeScreen` as `val largeFont` and passed down.

### 5.1 Header: `HomeHeader(name: String, nowMs: Long, onSettings: () -> Unit)` (HomeSections.kt)

- **Purpose:** a warm hello and the way to Settings. It holds no numbers.
- **Anatomy:** `SproutBadge` 36dp · a column with the greeting and the date · a 48dp gear `IconButton`.
- **Layout:** `Row(verticalAlignment = CenterVertically, modifier = heightIn(min = 56.dp))`. Badge, then 12dp gap, then `Column(weight(1f))`, then the gear. The greeting sits above the date, with 2dp between them.
- **Type and colour:** greeting `headlineSmall` (serif 26sp) `onSurface`, with the `heading()` semantic. Date `bodyMedium` `onSurfaceVariant`. Gear drawn 24dp in `onSurfaceVariant`.
- **Date:** `DateUtils.formatDateTime(context, nowMs, FORMAT_SHOW_WEEKDAY or FORMAT_SHOW_DATE or FORMAT_NO_YEAR)` → "Sunday, 28 September" (the order follows the locale), remembered per hour key `nowMs / 3_600_000`.
- **Greeting by hour:** 5–11 "Good morning", 12–16 "Good afternoon", 17–21 "Good evening", otherwise "Hello". With a nickname: "Good morning, Gobi".
- **States:** the same in every state. At large font the greeting wraps to 2 lines and the gear stays top-aligned (`Alignment.Top` when `largeFont`).
- **TalkBack:** the badge is decorative (`Modifier.clearAndSetSemantics { }` passed to `SproutBadge`). The greeting is a heading, then the date is read. The gear says "Settings".
- **Motion:** none.

### 5.2 Hero container: `HeroPanel(onClick: (() -> Unit)?, onClickLabel: String?, content: @Composable ColumnScope.() -> Unit)` (HomeHero.kt)

- A `Surface` with `shape = RoundedCornerShape(Radii.hero)`, `color = Color.Transparent`, `tonalElevation = 0.dp`, and `onClick` only when it's non-null (use the `Surface(onClick = …)` overload, otherwise the plain one). The inner `Column` has `Modifier.background(Brush.verticalGradient(listOf(reclaim.heroTop, reclaim.heroBottom)))` and `fillMaxWidth()`.
- `LocalContentColor` provides `reclaim.onHero`.
- The top region is a `Box(Modifier.fillMaxWidth().heightIn(min = 184.dp))` holding `HeroBackdrop` (fills the box, drawn behind), `GrowthPlant` and the text column. The optional **ground band** below it is a `Column(Modifier.fillMaxWidth().background(reclaim.heroGround).padding(horizontal = 24.dp, vertical = 8.dp))`.

### 5.3 Backdrop and plant: `HeroBackdrop(modifier)` and `GrowthPlant(stage: GrowthStage, modifier)` (GrowthPlant.kt)

`HeroBackdrop` is a Canvas, `clearAndSetSemantics { }`:
- **Sun glow:** `drawCircle(Brush.radialGradient(listOf(sunGlow.copy(alpha = a), sunGlow.copy(alpha = 0f)), center, radius = 88.dp), radius = 88.dp, center = Offset(size.width - 72.dp, 44.dp))`. `a` is 0.70 in light and 0.18 in dark, chosen by `MaterialTheme.colorScheme.surface.luminance() < 0.5f`.
- **Hill:** a path from `(0, h - 30.dp)`, `quadraticTo(size.width * 0.55f, h - 64.dp, size.width, h - 40.dp)`, down to `(w, h)` and `(0, h)`, then close. Fill `heroGround`. When the ground band is present, it continues the hill's colour seamlessly.

`GrowthPlant` is a Canvas of 112×148dp (88×116dp at large font), `clearAndSetSemantics { }`, because the meaning lives in the text. Let `u = size.width / 112f` and base `B = (56u, 142u)`.

| `GrowthStage` | Reached at `sinceStartedMs` ≥ | Stem height | Leaf pairs (position along stem : leaf length) | Extra |
| --- | --- | --- | --- | --- |
| `SEED` | 0 | 10u | top: 12u (right leaf only) | Seed: oval 16u×11u centred at `B + (0, -2u)`, `plantBloom` |
| `SPROUT` | 30 min | 40u | top: 26u | — |
| `SEEDLING` | 3 h | 64u | 0.55 : 22u · top : 28u | — |
| `YOUNG` | 12 h | 88u | 0.40 : 20u · 0.70 : 24u · top : 28u | — |
| `SAPLING` | 24 h | 112u | 0.30 : 20u · 0.55 : 24u · 0.80 : 28u · top : 30u | stem stroke 5u |
| `BLOOM` | 72 h | 120u | as SAPLING | a flower at the top: 5 petals, circles r = 6u at distance 7u from the tip, `plantBloom`; centre r = 4u in `heroTop` |

- **Stem:** a quadratic from `B` to `T = (56u, 142u − H)` with control `(48u, 142u − H/2)`, stroke 4u (5u from SAPLING), `StrokeCap.Round`, `plantStem`.
- **Leaf at attachment point P** (a point on the stem curve at parameter t = position; "top" means t = 1). The left leaf angle is −50° from vertical and the right is +50°. `tip = P + L·(sin θ, −cos θ)`, width `w = 0.42·L`, `n` = the unit normal of `(tip − P)`. Path: `moveTo(P)`, `cubicTo(P + (tip−P)·0.25 + n·w/2, P + (tip−P)·0.75 + n·w/2, tip)`, `cubicTo(P + (tip−P)·0.75 − n·w/2, P + (tip−P)·0.25 − n·w/2, P)`, close. Left leaves are filled `plantLeaf` and right leaves `plantLeafBright`, echoing the brand mark.
- `fun growthStage(sinceStartedMs: Long): GrowthStage` is pure, top-level and `internal`. Thresholds are as in the table.
- **Motion:** a single `Animatable(progress)` from 0 → 1 over `Motion.plantGrowMs`. Stem length = `H · min(1, progress / 0.5)`. Leaf pair `i` (counted from the bottom) scales about P by `clamp((progress − 0.3 − i·0.1) / 0.3, 0, 1)`. The flower scales with `clamp((progress − 0.8)/0.2)`.
  - It runs **once per process**, and again when the stage increases. Store `private var lastAnimatedStage: GrowthStage? = null` at the top level in GrowthPlant.kt. Animate when `stage != lastAnimatedStage`, then set it. With reduced motion, `progress` starts at 1.
- `SEED` is also used in `Last24hHero` (no baseline yet), meaning "growth is coming".

### 5.4 Hero, time won back: `SavedHero(progress, tracked, hasUsageAccess, largeFont, reducedMotion, onOpenDetails)` (HomeHero.kt)

**Purpose.** This is the one focal point: time won back, plus one month figure. Tapping it opens `SavedDetailSheet`.

**Headline logic.** This is a pure function, `internal fun savedHeadline(todayMs: Long, beforeWaking: Boolean, yesterdayMs: Long?, sinceStartedMs: Long): SavedHeadline`, returning `data class SavedHeadline(val kind: HeadlineKind, val ms: Long)`, where `enum class HeadlineKind { TODAY, YESTERDAY, SINCE_START, FRESH }`. The first rule that matches wins:

1. `beforeWaking && (yesterdayMs ?: 0) >= 60_000` → YESTERDAY, `yesterdayMs`
2. `todayMs >= 60_000` → TODAY, `todayMs`
3. `sinceStartedMs >= 60_000` → SINCE_START, `sinceStartedMs` (zero day)
4. otherwise → FRESH, 0

This fixes diagnosis #13. No state renders "0 min" as the hero.

**Anatomy (top region, left to right):**

- **Text column:** `Modifier.padding(start = 24.dp, top = 24.dp, end = 136.dp, bottom = 52.dp)`. At large font it's `padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 128.dp)`, so the plant sits below the text.
  1. `HeroNumberText(formatSaved(ms))`: digits in `displayMedium` and unit words in the 24sp unit span, `onHero`. Split with `Regex("(\\d+)|(\\D+)")`, sending digit runs to the digit style and everything else to the unit style. For FRESH, show `Text("Just getting started", style = displaySmall.copy(fontSize = 32.sp, lineHeight = 38.sp))` instead.
  2. Context line (`titleMedium`, Normal weight via `.copy(fontWeight = FontWeight.Normal)`, `onHero`, 4dp top): "saved today" / "saved yesterday" / "saved since you started" / (FRESH) "Time you win back today shows up here."
  3. SINCE_START only: "Nothing saved yet today" (`bodyMedium`, `onHeroMuted`, 4dp top).
  4. Month line (`bodyMedium`, `onHeroMuted`, 12dp top): "≈ 21 h a month at this pace". When `provisionalUntilMs != null`, add " · estimate". When `monthMs == null`, show "Your monthly estimate shows up on day 3". FRESH also shows it. This is the **only** month figure on Home.
- **Plant:** `GrowthPlant(growthStage(progress.sinceStartedMs))`, `Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)`, with the plant's base inside the hill.

**Ground band** (only when at least one of these applies; otherwise the hill closes the panel):

- A notes `Text` (`bodySmall`, `onHeroMuted`), joined with " · ": "Tracking was off for 3 h" (when `trackingOffTodayMs >= 60_000`, using `formatSaved`), "Not tracked while paused" (when `pausedToday`), and "Using ReclaimLife's timing only" (when `!hasUsageAccess`).
- An actions `Row(horizontalArrangement = spacedBy(8.dp))`:
  - `TextButton` "Allow Usage access" (only when `!hasUsageAccess`), `contentColor = onHero`. Its `onClick` is `runCatching { context.startActivity(usageAccessSettingsIntent()) }`.
  - `TextButton` "By app" + `Chevron` rotated 90° (collapsed) or −90° (expanded), only when `today.byApp.size > 1`, `stateDescription` "Collapsed"/"Expanded". Both buttons use default content padding, so they're at least 48dp.
- By-app rows, in `AnimatedVisibility(showApps)`: per tracked app with an entry in `today.byApp`, in `tracked` order, a `Row(heightIn(min = 32.dp), CenterVertically)`. Each has a 10dp `slotColor` dot, 8dp gap, and `"{label} {formatSaved(ms)} saved"` in `bodyMedium` `onHero`, with `semantics(mergeDescendants = true)`.

**States:**

| State | What changes |
| --- | --- |
| Normal | as above |
| Zero day (SINCE_START) | number = since-start total, context "saved since you started", plus the line "Nothing saved yet today" |
| Before the waking window | YESTERDAY: "38 min" / "saved yesterday". Falls through to rules 2–4 if yesterday < 1 min |
| Fresh (day 1, nothing yet) | "Just getting started" headline, SEED plant |
| Provisional estimate | month line ends in " · estimate". The settle date stays in the sheet |
| Month not yet available | "Your monthly estimate shows up on day 3" |
| Paused / paused earlier today | note "Not tracked while paused". Plant and number unchanged |
| Usage access revoked after baseline | note "Using ReclaimLife's timing only" + "Allow Usage access" button |
| Near limit / blocked / hourly-only | hero unchanged (the pill moves above it, see §6) |
| Large font | text full width, plant 88×116dp below the text at the bottom end, the number wraps by word ("6 h" / "10 min") |

**TalkBack.** The top region uses `clearAndSetSemantics { contentDescription = summary }`. The `HeroPanel` has `onClickLabel = "Show details"`. The ground-band buttons stay separately focusable. `summary`:
- TODAY: `"{spoken(formatSaved(ms))} saved today. "`
- YESTERDAY: `"Yesterday you saved {spoken}. "`
- SINCE_START: `"Since you started: {spoken}. Nothing saved yet today. "`
- FRESH: `"Just getting started. Time you win back today shows up here. "`
- Then `"About {spoken(formatProjection(month))} this month"` + `", an estimate. "` or `". "`, or `"Your monthly estimate shows up on day 3. "`
- Then, when `yearMs != null`, `"About {spoken(formatProjection(year))} this year."`

This keeps the Feature 2 acceptance line "42 minutes saved today. About 21 hours this month. About 10 days this year." exactly.

**Motion:** the plant grows (§5.3). The number and context use `AnimatedContent(targetState = headline, transitionSpec = fadeIn(tween(fadeMs)) togetherWith fadeOut(tween(fadeMs)))`, and `snap` with reduced motion. By-app rows expand with `expandMs`.

### 5.5 Hero, last 24 hours: `Last24hHero(progress: Progress, tracked, hasUsageAccess, largeFont, reducedMotion)` (HomeHero.kt)

Shown when `progress != null && !progress.hasBaseline`. It isn't clickable, because there are no saved figures to detail.

- Same `HeroPanel` (`onClick = null`), `HeroBackdrop`, and `GrowthPlant(GrowthStage.SEED)`.
- Text column (same padding as the saved hero):
  1. `HeroNumberText(formatUsage(total))`, with digits in **`displaySmall`** (44sp; usage is information, not the reward, so it's one step smaller) and units at 22sp. If `total < 60_000`, show "Under a minute" in `displaySmall.copy(fontSize = 32.sp, lineHeight = 38.sp)`.
  2. "on your apps in the last 24 hours" (`titleMedium` Normal, 4dp top).
  3. `TrendLine` (§5.9) in `onHeroMuted`, 8dp top, under the same condition as today: `previous24hMs > 0 || usageFromSystem`.
- **Ground band (always shown):**
  - `UsageBar` (§5.9), 12dp top. Gap colour `heroGround`.
  - `UsageLegend` in `onHero` / `onHeroMuted`.
  - The footer, 8dp top. With `!hasUsageAccess`, a `TextButton` "Allow Usage access to see how much time you win back" with `Chevron` (`onHero`, min height 48dp, text may wrap to 2 lines), opening `usageAccessSettingsIntent()`. With access, the `bodySmall` `onHeroMuted` text "Time won back shows up once your phone has 3 days of history."
- **TalkBack:** top region `clearAndSetSemantics { contentDescription = "{spoken(formatUsage(total))} on your apps in the last 24 hours. {trend words}." }`. The legend rows are merged per row ("Instagram, 48 min").
- **States:** no Usage access (button footer), access but under 3 days (text footer), zero usage ("Under a minute", no bar, no legend), large font (as the saved hero), paused (the numbers still show real usage and nothing changes).

### 5.6 Loading: `HeroSkeleton(message: String, reducedMotion: Boolean)` (HomeHero.kt)

- Shown while `progress == null`. With Usage access the message is "Working out your starting point…". Without it, "Adding up your last 24 hours…" (this replaces the old "…" value).
- `HeroPanel(onClick = null)` with `HeroBackdrop` and **no plant**.
- The text column holds three placeholder bars in `onHero.copy(alpha = 0.10f)`, `RoundedCornerShape(12.dp)`: 148×48dp, then 12dp gap, 184×16dp, then 12dp gap, 120×14dp. The whole column's alpha pulses (§4.5).
- Below the bars, 16dp gap, the message in `bodyMedium` `onHeroMuted`.
- The panel is 184dp tall, so nothing jumps when the real hero arrives.
- **TalkBack:** `liveRegion = Polite`, `contentDescription = message`.

### 5.7 Limits pill: `LimitsPill(status: LimitStatus, nowMs: Long, emphasised: Boolean, onOpen: () -> Unit, reducedMotion: Boolean)` (HomeSections.kt)

**Purpose:** the number to act on, compact. It opens the Limits screen.

- `Surface(onClick = onOpen, shape = RoundedCornerShape(Radii.pill), color = if (emphasised) tertiaryContainer else surfaceContainerHigh, modifier = fillMaxWidth().heightIn(min = 64.dp))`.
- `Row(CenterVertically, padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp))`: `LimitGauge` 32dp, 12dp gap, a `Column(weight(1f))` with the lead line (`titleMedium` SemiBold) and the detail line (`bodyMedium`), then `Chevron` 20dp.
- Colours: normal text `onSurface`/`onSurfaceVariant`, gauge fill `primary`, track `outlineVariant`. Emphasised: all text and the gauge fill use `onTertiaryContainer`, the detail line uses `onTertiaryContainer.copy(alpha = 0.85f)` (≥ 8:1), and the track is `onTertiaryContainer.copy(alpha = 0.2f)`.
- `LimitGauge(fraction: Float, fill: Color, track: Color)`: a Canvas of 32dp, `clearAndSetSemantics { }`, stroke 4dp round cap. The track is a full circle. The fill is an arc from −90° clockwise, sweeping `360f * fraction`. `fraction = status.left.toFloat() / status.total.coerceAtLeast(1)`, or 0 when blocked. It animates with `animateFloatAsState(tween(gaugeMs))`, or `snap` with reduced motion.
- **Text (unchanged numbers, split onto two lines):**

| State | Lead line | Detail line |
| --- | --- | --- |
| Daily (incl. daily+hourly) | "20 left" | "30 of 50 reels today" |
| Hourly-only | "4 left this hour" | "26 of 30 this hour" |
| Hourly limit blocking (`hourlyUnblockAtMs != null`) | "Back in 12:04" (`formatPauseRemaining`) | the detail as per mode above |
| Daily reached | "0 left" | "50 of 50 reels today" |
| Paused | unchanged (the paused banner explains) | unchanged |

- **Emphasis** is `status.nearLimit` (≤ 10% left, or blocked). Colour is never the only signal: the pill also **moves up** under the alerts, and the gauge arc is visibly near-empty.
- **TalkBack:** `semantics(mergeDescendants = true)` on the Surface content, with `onClickLabel = "Open limits"`. Content description: `"{lead}. {detail}."`, and "reels" is added to the lead for daily mode ("20 reels left. 30 of 50 reels today."). Hourly block: "Reels are back in 12 minutes." It isn't a live region, so the countdown isn't announced every second.
- **Large font:** the lines wrap, and the gauge and chevron stay vertically centred.
- **Motion:** gauge only. The move between positions is instant (no animated reorder).

### 5.8 Insight note: `InsightNote(insight: HomeInsight, onAction: (HomeInsight) -> Unit)` (HomeSections.kt)

- `Surface(shape = RoundedCornerShape(Radii.panel), color = surfaceContainerLow, modifier = fillMaxWidth().semantics { liveRegion = Polite })`.
- `Row(padding(16.dp))`:
  - `InsightGlyph(insight.kind)`: a 40dp circle in `secondaryContainer` with a 22dp drawn glyph in `onSecondaryContainer`, `clearAndSetSemantics { contentDescription = kind.iconDescription }`.
  - 16dp gap.
  - `Column(weight(1f))`: `Text(insight.text, bodyLarge, onSurface)`. When there's an action, a `TextButton(onClick = { onAction(insight) }, modifier = offset(x = (-12).dp).padding(top = 4.dp))` with `Text(actionLabel)` in `primary`. The offset aligns the label's text with the body text.
- **Glyphs**, drawn in a 24-unit box with a 2dp stroke and round caps, in `HomeGlyphs.kt`:
  - SETTLING: seedling (stem plus two leaves, like `MainTabs.kt:83-95`). Description "Seedling".
  - APP_DRIVEN / CREPT_UP / HEAVIER: wave (two stacked sine strokes, 1.5 periods). Description "Heads-up".
  - CUT_BIG: 5-point star (stroked). Description "Star".
  - CUT: a single rising leaf (`LeafGlyph`). Description changes from "Thumbs up" to "Leaf".
  - STEADY: balance (a beam on a triangle). Description "Balance".
- **One action at most.** It's a text button, so it never competes with a filled primary.
- **States:** no action (text only); with action; large font (text wraps, button below). The Undo snackbar is unchanged.
- **Motion:** none. The content only changes once a day.

### 5.9 Last 24 hours section: `Last24hSection(progress: Progress, tracked: List<TrackedApp>)` (HomeSections.kt)

Shown only when `progress.hasBaseline`, because otherwise it's the hero. It sits on the background with no surface.

- `SectionHeading("Last 24 hours")`: `titleMedium` `onSurface`, `heading()`, `heightIn(min = 32.dp)`.
- Value row, 8dp top: `Row(verticalAlignment = Alignment.Bottom)` holding `Text(formatUsage(total), headlineSmall /* serif 26sp */, onSurface, Modifier.alignByBaseline())` and `Text(" on your apps", bodyLarge, onSurfaceVariant, Modifier.alignByBaseline())`. At large font it becomes a `Column` (value, then words).
- `TrendLine(total, previous24hMs, color = onSurfaceVariant)`, 4dp top, under the same condition as today (`previous24hMs > 0 || usageFromSystem`). It's a `Row(semantics(mergeDescendants = true))` of `TrendGlyph(direction)` 16dp (drawn arrow: up, down, or a flat line meaning "same", in `onSurfaceVariant`, decorative) plus 8dp gap plus the words (`bodyMedium`):
  - `|diff| < 60_000`: "About the same as the day before"
  - `diff < 0`: "{formatUsage(−diff)} less than the day before"
  - otherwise "{formatUsage(diff)} more than the day before"
  - Never red and never a minus sign.
- `UsageBar(perApp, gapColor = surface)`, 16dp top, only when `total > 0`. A Canvas `fillMaxWidth().height(12.dp).clip(RoundedCornerShape(Radii.bar))`, `clearAndSetSemantics { }`. Segments are in `tracked` order with `slotColor`, separated by 2dp gaps in `gapColor`, and apps with 0 ms are skipped. (This keeps the current algorithm, `HomeCards.kt:633-640`.)
- `UsageLegend(perApp)`, 12dp top: rows sorted by ms descending, apps with 0 ms left out. Each is a `Row(heightIn(min = 32.dp), CenterVertically, semantics(mergeDescendants = true))` with a 10dp dot, 8dp gap, label (`bodyMedium` Medium, `weight(1f)`) and `formatUsage(ms)` (`bodyMedium`, `onSurfaceVariant`). The legend doubles as the labels, so colour is never alone.
- **States:** no usage (value "under a minute", no bar, no legend), one app (a single-segment bar), paused (real usage, unchanged), large font (the value row stacks).

### 5.10 Week section: `WeekSection(dayHistory, daysWithinLimit, daysExceededLimit, todayMs, largeFont)` (HomeSections.kt)

- Heading row: `SectionHeading("This week", Modifier.weight(1f))` and, when `streak > 0`, a `StreakChip(streak)`. At large font the chip moves under the heading (a `Column`, with 8dp gap).
- `StreakChip`: `Surface(shape = RoundedCornerShape(Radii.chip), color = primaryContainer)`, `Row(padding(horizontal = 12.dp, vertical = 6.dp))` with `LeafGlyph` 14dp in `onPrimaryContainer`, 6dp gap, and `Text("$streak-day streak", titleSmall, onPrimaryContainer)`. Contrast is 10.17 / 7.55, which fixes diagnosis #10. `clearAndSetSemantics { contentDescription = "$streak-day streak within your limit" }`.
- Strip, 16dp top: `Row(fillMaxWidth(), horizontalArrangement = SpaceBetween)` of 7 cells. Each cell is a `Column(width(40.dp), CenterHorizontally)`: `DayMark` 36dp, 6dp gap, then the weekday initial in `labelMedium` (`onSurfaceVariant`, or `onSurface` Bold for today).
- `DayMark` shapes (Canvas, 36dp):
  - WITHIN: filled circle `primary` + `CheckGlyph` (18dp, 2.5dp stroke, `onPrimary`).
  - OVER: 1.5dp `outline` ring on `surfaceContainerHigh` fill + `CrossGlyph` (14dp, 2dp stroke, `onSurfaceVariant`). No red.
  - No data: a 12dp ring, 1.5dp stroke `outline`, dashed (`PathEffect.dashPathEffect(floatArrayOf(3dp, 3dp))`).
  - Today: a 2.5dp `primary` ring, 36dp.
- Footer, 16dp top, `bodySmall` `onSurfaceVariant`:
  - If any days are recorded: "All time: {n} {day|days} within limit · {m} over"
  - otherwise: "Today closes out at midnight — your first dot fills in tomorrow."
- **TalkBack:** each cell is `clearAndSetSemantics { contentDescription = … }`. The full weekday comes from parsing `dateKey` with `SimpleDateFormat("yyyy-MM-dd", Locale.US)` and formatting with `SimpleDateFormat("EEEE", Locale.getDefault())`, remembered by hour key. The text is "Wednesday, within limit", "Tuesday, over limit", "Monday, no data" or "Today, in progress". This fixes diagnosis #11.

### 5.11 Pause entry: `PauseTrackingButton(onPause, modifier)` (HomeSections.kt)

- A centred `TextButton`, min 48dp high: `PauseBars` 16dp + 8dp gap + "Pause tracking" (`labelLarge`, `onSurfaceVariant`).
- 24dp above it, 32dp bottom padding for the screen.
- Hidden while paused or pending, when the banner offers Resume or Cancel instead (unchanged).
- TalkBack: "Pause tracking", role Button.

### 5.12 Banners: `HomeBanner(tone: BannerTone, glyph: @Composable () -> Unit, title: String, body: String?, modifier, actions: @Composable ColumnScope.() -> Unit)` (HomeBanners.kt)

- `enum class BannerTone { ALERT, PAUSE, NEUTRAL }`, mapping to containers `errorContainer` / `tertiaryContainer` / `surfaceContainerHigh` with their `on…` colours.
- `Surface(shape = RoundedCornerShape(Radii.panel), color = container)`, `Column(padding(16.dp))`:
  - Row: glyph 24dp, 10dp gap, title (`titleMedium`, `heading()`).
  - Body (`bodyMedium`), 6dp top.
  - Actions, 12dp top.
- Stacked, not side by side, so it survives 200% font. This is what the current `PauseAlertCard` does already.
- Enter and exit in `HomeScreen` via `AnimatedVisibility(enter = expandVertically(tween(expandMs)) + fadeIn(tween(expandMs)), exit = shrinkVertically + fadeOut)`, or None with reduced motion.

| Banner | Composable | Tone | Glyph (TalkBack) | Action(s) |
| --- | --- | --- | --- | --- |
| Accessibility off / not running | `AccessibilityBanner(status)` (moved from HomeScreen.kt, behaviour unchanged incl. `AccessibilityConsentDialog`) | ALERT | `AlertGlyph` drawn "!" in an `error` disc ("Warning") | Filled `Button` full-width (the only filled button on Home) + `TextButton` help toggle with `Chevron` and `stateDescription`; help text in `AnimatedVisibility`, `bodySmall`. `liveRegion = Polite` |
| Paused | `PausedBanner(remainingMs, resumesAtMs, intention, onResume)` | PAUSE | `PauseBars` 24dp ("Paused") | `OutlinedButton` "Resume now", end-aligned, `border = BorderStroke(1.dp, onTertiaryContainer)`, `contentColor = onTertiaryContainer` |
| Rest of today pending | `PendingPauseBanner(startsInMs, intention, onCancel)` | PAUSE | `PauseBars` ("Paused") | `OutlinedButton` "Cancel", content description "Cancel Rest of today" |
| Apps check | `AppsCheckBanner(onOpen, onDismiss)` | NEUTRAL | none: a single `Row(heightIn(min = 56.dp))` with the title text + `Chevron` as one `clickable(role = Button)` region (`weight(1f)`), then a `TextButton` "Dismiss" | — |

The Accessibility banner keeps red (`errorContainer`), because the counter being broken is a genuine fault, not a usage increase. The body copy is shortened (§7) so the Limits pill stays above the fold with this banner showing.

### 5.13 Detail sheet: `SavedDetailSheet(progress, onDismiss)` (moved to HomeHero.kt)

- Unchanged, except for one new line after the flavour line: a `bodyMedium` `onSurfaceVariant` text about the plant (§7 copy). It uses `growthStage(progress.sinceStartedMs)` and the next threshold.
- The title "Time won back" uses `headlineSmall` (serif), replacing `titleLarge` + Bold.

## 6. Screen layout

`HomeScreen` root: `Surface(color = surface)` → `Box` → `Column(fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(...).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp))`. There's no `spacedBy`. Use explicit `Spacer(Modifier.height(…))` so the groups are visible in the code. The `SnackbarHost` stays at the bottom centre, with 16dp padding.

**Order (top to bottom):**

| # | Item | Space above |
| --- | --- | --- |
| 1 | `HomeHeader` | 0 (8dp screen top) |
| 2 | Alerts, each shown only when it applies, in this order: `AccessibilityBanner`, `PendingPauseBanner`, `PausedBanner`, `AppsCheckBanner` | 20dp before the first; 12dp between |
| 3 | `LimitsPill` **here only when `status.nearLimit`** | 12dp (20dp if no alert) |
| 4 | Hero: `HeroSkeleton` / `SavedHero` / `Last24hHero` | 12dp after an alert or the promoted pill; 20dp after the header |
| 5 | `LimitsPill` **here when not `nearLimit`** | 12dp |
| 6 | `InsightNote` (when progress is loaded) | 12dp |
| 7 | `Last24hSection` (only with a baseline) | 32dp |
| 8 | `WeekSection` | 32dp |
| 9 | `PauseTrackingButton` (not paused or pending) | 24dp |

**Above the fold on 1080×2400 (411×914dp), 100% font.** The status bar takes about 48dp and the NavigationBar with gesture inset about 104dp, which leaves about 762dp.

- Normal state: header 56 (ends at 64) → hero ≈ 208 (ends ≈ 292) → pill 64 (ends ≈ 368) → insight ≈ 96 (ends ≈ 476) → Last 24 hours heading and value (ends ≈ 560).
- With the Accessibility banner (≈ 232dp) the pill ends at ≈ 612dp, which is inside the fold.
- With two alerts showing at once (for example Accessibility off **and** paused), the pill can fall below the fold. That's accepted, because near the limit it moves above the hero anyway, and while paused nothing is counted.

**Normal state**

```
┌─────────────────────────────────────────┐
│ (☀🌱)  Good evening, Gobi          [⚙]  │  header: serif 26sp + date
│        Sunday, 28 September             │
│                                         │
│ ╭─────────────────────────────────────╮ │
│ │ 42 min                       ░☀░    │ │  hero: serif 56sp digits,
│ │ saved today                    \|/  │ │  24sp units; plant right
│ │                               ─┼─   │ │
│ │ ≈ 21 h a month at this pace   \|/   │ │
│ │ ▁▁▂▂▃▃▄▄▅▅▆▆▆▆▅▅▄▄▃▃▂▂▁▁▁▁▁▁▁▁|▁▁▁ │ │  hill
│ │ Not tracked while paused  [By app ⌄]│ │  ground band (optional)
│ ╰─────────────────────────────────────╯ │
│ ╭─────────────────────────────────────╮ │
│ │ ◔  20 left                        › │ │  Limits pill, gauge ring
│ │    30 of 50 reels today             │ │
│ ╰─────────────────────────────────────╯ │
│ ╭─────────────────────────────────────╮ │
│ │ (✦) You've cut your scrolling by    │ │  insight note (sand disc)
│ │     about 35%. That's a real change.│ │
│ ╰─────────────────────────────────────╯ │
│                                         │  32dp
│ Last 24 hours                           │  no box
│ 1 h 12 min on your apps                 │
│ ↓ 18 min less than the day before       │
│ ████████████▌▐██████▌▐███               │
│ ● Instagram                      48 min │
│ ● YouTube                        24 min │
│                                         │  32dp
│ This week               (🍃 4-day streak)│
│  (✓) (✓) (×) (✓) ( ) (✓) (◯)            │
│   M   T   W   T   F   S   S             │
│ All time: 12 days within limit · 3 over │
│                                         │
│            ‖ Pause tracking             │
└─────────────────────────────────────────┘
```

**Near limit or blocked (pill promoted, gold)**

```
│ (☀🌱)  Good afternoon              [⚙]  │
│        Sunday, 28 September             │
│ ╭─────────────────────────────────────╮ │
│ │ ◌  0 left                         › │ │  tertiaryContainer, bold,
│ │    50 of 50 reels today             │ │  empty ring, above hero
│ ╰─────────────────────────────────────╯ │
│ ╭─────────────────────────────────────╮ │
│ │ 42 min                        \|/   │ │  hero unchanged
│ │ saved today                   ─┼─   │ │
│ │ ≈ 21 h a month at this pace         │ │
│ ╰─────────────────────────────────────╯ │
│ (insight, Last 24 hours, week…)         │
```

**No baseline / no Usage access (Last 24 hours is the hero)**

```
│ (☀🌱)  Good morning                [⚙]  │
│ ╭─────────────────────────────────────╮ │
│ │ 1 h 12 min                    ░☀░   │ │  serif 44sp digits
│ │ on your apps in the                 │ │
│ │ last 24 hours                  ,o   │ │  SEED plant
│ │ ↓ 18 min less than the day before   │ │
│ │▁▁▂▂▃▃▄▄▅▅▆▆▆▆▅▅▄▄▃▃▂▂▁▁▁▁▁▁▁▁▁▁▁▁▁▁ │ │
│ │ ████████████▌▐███████               │ │  ground band: bar,
│ │ ● Instagram                  48 min │ │  legend, footer
│ │ ● YouTube                    24 min │ │
│ │ [Allow Usage access to see how much │ │
│ │  time you win back ›]               │ │
│ ╰─────────────────────────────────────╯ │
│ ╭ ◔ 20 left · 30 of 50 reels today  › ╮ │
│ ╭ (🌱) Give it a couple of days, and… ╮ │
│ This week …                             │
```

**Paused, zero day**

```
│ ╭─────────────────────────────────────╮ │
│ │ ‖ Paused · 12:04 left               │ │  gold banner
│ │ Enjoy: Movie night · back at 16:40  │ │
│ │                      (Resume now)   │ │  outlined button
│ ╰─────────────────────────────────────╯ │
│ ╭─────────────────────────────────────╮ │
│ │ 6 h 10 min                    \|/   │ │
│ │ saved since you started      ─┼─    │ │  SEEDLING plant
│ │ Nothing saved yet today       \|/   │ │
│ │ ≈ 21 h a month at this pace         │ │
│ │ Not tracked while paused            │ │
│ ╰─────────────────────────────────────╯ │
```

## 7. Copy table

Every user-visible string on Home. `{}` marks a value. Strings not listed are removed.

| Where | Key / condition | Final wording |
| --- | --- | --- |
| Header | greeting 05–11 / 12–16 / 17–21 / else | "Good morning" / "Good afternoon" / "Good evening" / "Hello" (+ ", {name}" when a nickname is set) |
| Header | date | system `DateUtils` weekday + date, no year, e.g. "Sunday, 28 September" |
| Header | gear (TalkBack) | "Settings" |
| Saved hero | TODAY context | "saved today" |
| Saved hero | YESTERDAY context | "saved yesterday" |
| Saved hero | SINCE_START context | "saved since you started" |
| Saved hero | SINCE_START extra | "Nothing saved yet today" |
| Saved hero | FRESH headline / context | "Just getting started" / "Time you win back today shows up here." |
| Saved hero | month | "≈ {formatProjection} a month at this pace" (+ " · estimate") |
| Saved hero | month missing | "Your monthly estimate shows up on day 3" |
| Saved hero | notes | "Tracking was off for {formatSaved}" · "Not tracked while paused" · "Using ReclaimLife's timing only" |
| Saved hero | buttons | "Allow Usage access" · "By app" |
| Saved hero | by-app row | "{app} {formatSaved} saved" |
| Saved hero | TalkBack | see §5.4; click label "Show details" |
| Last 24 h hero | number / context | "{formatUsage}" or "Under a minute" / "on your apps in the last 24 hours" |
| Last 24 h hero | footer (no access) | "Allow Usage access to see how much time you win back" |
| Last 24 h hero | footer (access, < 3 days) | "Time won back shows up once your phone has 3 days of history." |
| Trend | same / less / more | "About the same as the day before" / "{formatUsage} less than the day before" / "{formatUsage} more than the day before" |
| Skeleton | with / without Usage access | "Working out your starting point…" / "Adding up your last 24 hours…" |
| Limits pill | daily | "{left} left" / "{used} of {total} reels today" |
| Limits pill | hourly-only | "{left} left this hour" / "{used} of {total} this hour" |
| Limits pill | hourly block | "Back in {formatPauseRemaining}" |
| Limits pill | TalkBack click label | "Open limits" |
| Insight | text, action label | from `chooseInsight` (unchanged) |
| Insight | glyph descriptions | "Seedling" · "Heads-up" · "Star" · "Leaf" · "Balance" |
| Snackbar | gate on | "Pause before opening is on for {app}" · action "Undo" |
| Last 24 h section | heading / value | "Last 24 hours" / "{formatUsage} on your apps" |
| Legend row | | "{app}" · "{formatUsage}" |
| Week | heading | "This week" |
| Week | streak chip / TalkBack | "{n}-day streak" / "{n}-day streak within your limit" |
| Week | cell TalkBack | "{Weekday}, within limit" · "{Weekday}, over limit" · "{Weekday}, no data" · "Today, in progress" |
| Week | footer | "All time: {n} day(s) within limit · {m} over" / "Today closes out at midnight — your first dot fills in tomorrow." |
| Pause | button | "Pause tracking" |
| Pause sheet calm line | saved today ≥ 1 min / baseline but < 1 min / no baseline / loading | "You've won back {formatSaved} today." / "Take a breath." / "{formatUsage, capitalised} on your apps in the last 24 hours." / "Take a breath." |
| Swap dialog | headline / subtitle | "Your 2-minute swap" / "A small reset, whenever you want one." (unchanged) |
| Accessibility banner (off) | title / body / button / help toggle / help | "Reels aren't being counted" / "Accessibility access for ReclaimLife is off, so nothing is counted or blocked. Time saved leaves these hours out." / "Turn on Accessibility access" / "Switch won't move?" / "In Accessibility, find ReclaimLife under Downloaded apps and turn it on. If the switch won't move: Settings → Apps → ReclaimLife → ⋮ menu → Allow restricted settings, then try again." |
| Accessibility banner (not running) | title / body / button / help toggle / help | "The reel counter stopped" / "Accessibility is on for ReclaimLife, but Android isn't running it — often after a crash or battery saver. Turn it off and on again to restart counting. Time saved leaves these hours out." / "Restart it in Accessibility settings" / "Keeps stopping?" / "Some phones stop background services to save battery. Settings → Apps → ReclaimLife → Battery → Unrestricted keeps the counter running." |
| Accessibility banner | glyph TalkBack | "Warning" |
| Paused banner | title / body / body with intention / action | "Paused · {formatPauseRemaining} left" / "Reels aren't counted or blocked. Back on by itself at {time}." / "Enjoy: {intention} · back at {time}" / "Resume now" |
| Pending banner | title / body / body with intention / action | "Rest of today starts in {formatPauseRemaining}" / "Changed your mind? Cancel keeps tracking on." / "Enjoy: {intention}" / "Cancel" (TalkBack "Cancel Rest of today") |
| Apps check | title / action | "Check which apps ReclaimLife helps with" / "Dismiss" |
| Detail sheet | title, rows | "Time won back"; "Since you started", "Today", "This month" ("≈ {…}" or "Shows up on day 3"), "This year" ("≈ {…}" or "Shows up on day 14") (unchanged) |
| Detail sheet | plant line (new) | Stage < BLOOM: "Your plant grows as your time adds up. It reaches its next stage at {formatSaved(threshold)} since you started." BLOOM: "Your plant is in full bloom. Every minute from here is a bonus." |
| Detail sheet | other lines | flavour line, "Today's figure is an estimate · settles on {date}", and the "Measured against…" paragraph (unchanged) |

Tone check: none of these strings use a minus sign, "warning" as visible text, blame, or red for an increase.

## 8. Implementation plan

Each task leaves the app compiling, and `./gradlew --offline :app:assembleDebug` passes after each one. Run the full check (§9, last criterion) after Tasks 4, 9 and 11. Don't change any `MainViewModel` API, repository or `ProgressEngine`.

**Task 1: Tokens.**
- Files: `ui/theme/Type.kt`, `ui/theme/Color.kt`, `ui/theme/Theme.kt`, new `ui/theme/Dimens.kt`.
- Add `DisplayFamily` and the §4.1 styles. Add the §4.4 hex constants (`HeroTopLight`, `HeroTopDark`, …). Add `@Immutable data class ReclaimColors(heroTop, heroBottom, onHero, onHeroMuted, heroGround, sunGlow, plantStem, plantLeaf, plantLeafBright, plantBloom)`, `LightReclaimColors`, `DarkReclaimColors`, `val LocalReclaimColors = staticCompositionLocalOf { LightReclaimColors }`, and `val MaterialTheme.reclaim: ReclaimColors @Composable @ReadOnlyComposable get() = LocalReclaimColors.current`.
- In `ReclaimLifeTheme`, wrap `MaterialTheme` in `CompositionLocalProvider(LocalReclaimColors provides if (darkTheme) DarkReclaimColors else LightReclaimColors)`.
- Remove the commented-out block in Type.kt.
- Add `Spacing`, `Radii` and `HomeLayout` (§4.2).
- Done when: the app builds; the Home, Settings and Accessibility consent dialogs render at 100% and 200% font in light and dark, with no clipped text.

**Task 2: Glyphs and motion helper.**
- Files: new `ui/home/HomeGlyphs.kt`, new `ui/common/Motion.kt`; edit `HomeCards.kt` and `HomeScreen.kt`.
- Move `Chevron`, `GearIcon` (make it `internal`), `PauseBars` (`internal`) and `AlertGlyph` into HomeGlyphs.kt, and delete them from HomeCards.kt and HomeScreen.kt.
- Add `TrendGlyph(direction: TrendDirection, color, modifier)` with `enum class TrendDirection { UP, DOWN, SAME }`, `CheckGlyph`, `CrossGlyph`, `LeafGlyph` and `InsightGlyph(kind)` (§5.8).
- Add `rememberReducedMotion()` and `object Motion` with the §4.5 durations as `const val …Ms: Int`.
- Done when: it builds; `grep -n "fun Chevron\|fun GearIcon\|fun PauseBars\|fun AlertGlyph" -r app/src/main` shows only HomeGlyphs.kt.

**Task 3: Plant.**
- Files: new `ui/home/GrowthPlant.kt`; new test `app/src/test/java/io/github/gobi12b/reclaimlife/ui/home/GrowthStageTest.kt`.
- Add `enum class GrowthStage(val thresholdMs: Long, val label: String)` (SEED 0 "Seed", SPROUT 30 min "Sprout", SEEDLING 3 h "Seedling", YOUNG 12 h "Young plant", SAPLING 24 h "Sapling", BLOOM 72 h "In bloom"), `growthStage()`, `nextStage()`, `HeroBackdrop` and `GrowthPlant` (§5.3).
- Tests: 0 → SEED; 29 min 59 s → SEED; 30 min → SPROUT; 3 h → SEEDLING; 11 h 59 min → SEEDLING; 24 h → SAPLING; 72 h → BLOOM; a negative value → SEED.
- Done when: the tests pass. An `@Preview` of all six stages side by side (light and dark) renders in Android Studio. The preview is optional, so don't block on it.

**Task 4: Hero.**
- Files: new `ui/home/HomeHero.kt`; test `app/src/test/java/io/github/gobi12b/reclaimlife/ui/home/SavedHeadlineTest.kt`; edit `HomeScreen.kt` and `HomeCards.kt`.
- Add `HeroPanel`, `HeroNumberText`, `savedHeadline()`, `SavedHero`, `Last24hHero`, `HeroSkeleton`. Move `spoken()`, `SavedDetailSheet`, `DetailRow` and `flavourLine` here, and add the plant line to the sheet.
- In HomeScreen, replace the hero `when` block (`HomeScreen.kt:202-212`) with:
  - `progress == null` → `HeroSkeleton(if (hasUsageAccess) "Working out your starting point…" else "Adding up your last 24 hours…")`
  - `hasBaseline` → `SavedHero`
  - else → `Last24hHero`
- Fix the calm line to use `savedHeadline` (§7).
- Delete `SavedCard`, `HeroSentence` and `SavedSkeleton` from HomeCards.kt. Keep `Last24hCard` for now (it's still used for the secondary section).
- Tests: `savedHeadline`:
  - today 42 min → TODAY
  - beforeWaking + yesterday 38 min → YESTERDAY
  - beforeWaking + yesterday 0 + since 6 h → SINCE_START
  - today 30 s + since 6 h → SINCE_START
  - all 0 → FRESH
  - beforeWaking + yesterday null + today 0 + since 0 → FRESH
- Tests: `spoken("6 h 10 min") == "6 hours 10 minutes"`.
- Done when: the tests pass; tapping the saved hero opens the sheet; "By app" toggles; "Allow Usage access" opens Settings.

**Task 5: Limits pill.**
- Files: `HomeSections.kt` (new), `HomeScreen.kt`, `HomeCards.kt`.
- Add `LimitsPill` and `LimitGauge` (§5.7). In HomeScreen, the `limitsRow` lambda calls `LimitsPill(status, nowMs, emphasised = status.nearLimit, onOpen = onOpenLimits, reducedMotion)`. Delete `LimitsRow` from HomeCards.kt.
- Done when: at 4 of 50 left, the pill is gold and sits above the hero; at 30 of 50, it's neutral and sits below the hero; hourly blocked shows "Back in m:ss" ticking; tapping opens Limits.

**Task 6: Banners.**
- Files: new `HomeBanners.kt`; `HomeScreen.kt`; `HomeCards.kt`.
- Add `HomeBanner` and `BannerTone`. Move `AccessibilityBanner` from HomeScreen.kt (keep the consent dialog flow exactly) and apply the §7 copy. Replace `PausedBanner`, `PendingPauseCard` → `PendingPauseBanner`, and `AppsCheckCard` → `AppsCheckBanner`. Wrap each alert in `AnimatedVisibility` per §5.12.
- Delete `PauseAlertCard`, `PauseGlyph` and `AppsCheckCard`.
- Done when: turning Accessibility off shows the red banner with a working consent dialog; starting a 15-minute pause shows the gold banner with "Resume now" (outlined) which resumes; Rest of today shows the pending banner with "Cancel" which cancels; the apps card opens Settings and dismisses permanently.

**Task 7: Insight note.**
- Files: `HomeSections.kt`, `HomeScreen.kt`, `HomeCards.kt`, `data/HomeInsight.kt`.
- Add `InsightNote` (§5.8). In `InsightKind`, remove the `emoji` parameter and change `CUT`'s description to "Leaf". Update the KDoc on line 5 to "[iconDescription] travels with the drawn glyph." Delete `InsightCard`.
- Done when: `grep -rn "kind.emoji" app/src` is empty; `HomeInsightTest` passes; all three actions still work (Turn on gate → snackbar with Undo that reverts; Try 10 fewer reels → `EditLimitSheet` prefilled with limit − 10; Start a swap → `SwapDialog`).

**Task 8: Last 24 hours section.**
- Files: `HomeSections.kt`, `HomeHero.kt`, `HomeScreen.kt`, `HomeCards.kt`.
- Add `Last24hSection`, `UsageBar`, `UsageLegend` and `TrendLine`, and use `UsageBar`/`UsageLegend`/`TrendLine` inside `Last24hHero` as well, so they aren't duplicated. Delete `Last24hCard` and `import kotlin.math.abs` wherever it's unused.
- Done when: with a baseline, the section shows on the background with no surface; the bar segments and legend order match; a larger total than the day before shows the up arrow and "more than the day before" in `onSurfaceVariant`.

**Task 9: Week section.**
- Files: `HomeSections.kt`, `HomeScreen.kt`, `HomeCards.kt`.
- Add `WeekSection`, `StreakChip` and `DayMark` (§5.10). Delete `WeekCard` and the old `DayDot`.
- Done when: TalkBack reads weekday names; the streak chip shows with ≥ 4.5:1 contrast; no-data days show a dashed small ring.

**Task 10: Header, layout and cleanup.**
- Files: `HomeSections.kt`, `HomeScreen.kt`; delete `HomeCards.kt`.
- Move `HomeHeader` (new design, §5.1), `PauseTrackingButton` and `SectionHeading` into HomeSections.kt. Rebuild the `HomeScreen` column exactly per §6 (gutter 20dp, explicit spacers, no `spacedBy`).
- Delete `HomeCard`, `SectionLabel`, `HomeCardShape` and then the file `HomeCards.kt`. Remove unused imports in HomeScreen.kt (`Button`, `TextButton`, `background`, `CircleShape`, `FontWeight`, `clearAndSetSemantics`, etc.).
- Keep every callback and state in `HomeScreen`, unchanged: `showPauseSheet` + `PauseSheet(onStart, onScheduleRestOfToday)`, `showSavedDetails` + `SavedDetailSheet`, `swapOnDemand` + `SwapDialog`, `lowerLimitPrefill` + `EditLimitSheet`, the snackbar + Undo, `onOpenSettings`, `onOpenLimits`, `viewModel.dismissAppsCard`, `viewModel.cancelPendingPause`, `viewModel.resumeTracking`, and the 1-second ticker `LaunchedEffect`.
- Done when: `HomeCards.kt` no longer exists; `./gradlew --offline :app:lintDebug` has no new warnings in `ui/home` or `ui/theme`; `grep -rn "HomeCard\b" app/src/main` is empty.

**Task 11: Verification pass.**
- No code, unless it fixes a finding.
- Walk through §9 on an API 34 emulator (1080×2400, 420dpi) in light and dark, at 100% and 200% font, with animations on and off, and with TalkBack.
- Run `JAVA_HOME=/home/gobi/.gradle/jdks/eclipse_adoptium-25-amd64-linux.2 ./gradlew --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Done when: every criterion in §9 holds and the command passes.

## 9. Acceptance criteria

**Hierarchy and layout**

- **Given** a normal day with 42 min saved and 20 of 50 reels left, on a 1080×2400 phone at 100% font, **when** Home opens, **then** the order is header → hero → Limits pill → insight, and the bottom edge of the Limits pill is visible without scrolling.
- **Given** 4 of 50 reels left (or the daily limit reached, or the hourly limit blocking), **then** the Limits pill sits directly under the alerts (or under the header when there are none), above the hero, on `tertiaryContainer`, with a near-empty gauge.
- **Given** the Accessibility banner is showing and the limit is not near, **then** the Limits pill is still fully above the fold at 100% font.
- **Given** any state, **then** Home shows exactly one month figure (the hero month line), and no year figure outside the detail sheet.
- **Given** a baseline, **then** "Last 24 hours" and "This week" have no card surface behind them. The only filled surfaces are the alerts, hero, Limits pill, insight note and streak chip.
- **Given** any state, **then** Home has at most one filled `Button`, which is the Accessibility banner's.

**Hero**

- **Given** today 42 min saved and a month projection of 21 h, **then** the hero shows "42 min" (digits 56sp serif), "saved today", and "≈ 21 h a month at this pace", and TalkBack reads "42 minutes saved today. About 21 hours this month." plus the year sentence when available.
- **Given** today 30 s saved and 6 h 10 min since the start, **then** the hero shows "6 h 10 min", "saved since you started" and "Nothing saved yet today", and never "0 min".
- **Given** a day-1 user with 0 saved ever, **then** the hero shows "Just getting started", the SEED plant, and no "0 min" anywhere in the hero.
- **Given** before the waking window with yesterday 38 min, **then** the hero shows "38 min" and "saved yesterday". **Given** yesterday 0, **then** it falls through to SINCE_START or FRESH.
- **Given** a provisional baseline, **then** the month line ends in "· estimate", and the detail sheet shows the settle date.
- **Given** `sinceStartedMs` of 13 h, **then** the plant has 3 leaf pairs (YOUNG), and the detail sheet says the next stage is at "24 h".
- **Given** no Usage access and no baseline, **then** the hero shows the last 24 hours with the SEED plant, a labelled stacked bar, a legend with per-app times and no minus signs, and an "Allow Usage access to see how much time you win back" button at least 48dp tall that opens Usage access settings. The hero is not clickable.
- **Given** progress is loading, **then** a skeleton of the same height as the hero shows the right message, and the layout doesn't shift by more than 8dp when data arrives.
- **Given** the saved hero is tapped, **then** `SavedDetailSheet` opens.

**Behaviour kept**

- **Given** the insight action "Turn on for YouTube", **when** it's tapped, **then** the snackbar "Pause before opening is on for YouTube" appears with "Undo", and Undo reverts it.
- **Given** "Try 10 fewer reels" with a limit of 50, **then** `EditLimitSheet` opens prefilled with 40. **Given** "Start a swap", **then** `SwapDialog` opens.
- **Given** a pause is running, **then** the gold banner shows the countdown ticking each second, "Resume now" resumes, and "Pause tracking" is hidden. **Given** Rest of today is pending, **then** "Cancel" cancels it.
- **Given** Accessibility is off, **when** the banner button is tapped, **then** `AccessibilityConsentDialog` shows before Settings opens.
- **Given** the gear is tapped, **then** Settings opens. **Given** the apps-check banner is dismissed, **then** it never returns.
- **Given** the same moment, **then** the Limits pill's "left" equals the Limits screen's and the widget's, and the hero's today figure equals the detail sheet's "Today" row.

**Accessibility**

- **Given** TalkBack, **then** every interactive element has a label, the header greeting and section titles are headings, week cells read "Wednesday, within limit" (not ISO dates), the streak reads "4-day streak within your limit", and insight glyphs read their description.
- **Given** 200% font, **then** no text is clipped or overlapping, the hero text uses the full width with the plant below it, the Limits pill lines wrap, and the streak chip moves under "This week".
- **Given** any interactive element on Home, **then** its touch target is at least 48×48dp. Check with Layout Inspector or the Accessibility Scanner.
- **Given** colour is removed (greyscale screenshot), **then** near-limit is still signalled by position and the gauge, within, over and no-data days differ by shape, and the trend by arrow plus words.
- **Given** "Remove animations" is on, **then** the plant appears fully grown, banners appear without animation, and the skeleton doesn't pulse.
- **Given** the usage went up versus the day before, **then** no element is red, and the copy says "{x} more than the day before".

**Dark mode**

- **Given** system dark theme, **then** the hero uses #223D27 → #18301D with text #DDF5DF, the sun glow is subtle (alpha 0.18), the plant uses the dark roles, and all §4.4 contrast pairs hold. Check with a screenshot sampled in any contrast checker.
- **Given** dynamic colour available (Android 12+), **then** the brand palette is still used, because `dynamicColor` stays `false`.

**Build**

- `JAVA_HOME=/home/gobi/.gradle/jdks/eclipse_adoptium-25-amd64-linux.2 ./gradlew --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` passes, including the new `GrowthStageTest` and `SavedHeadlineTest`.
- `app/build.gradle.kts` and `gradle/libs.versions.toml` are unchanged, meaning there are no new dependencies.
- `app/src/main/java/io/github/gobi12b/reclaimlife/ui/home/HomeCards.kt` does not exist.

## 10. Open questions and risks

1. **System serif varies by OEM.** Most phones ship Noto Serif. Some Samsung and Xiaomi builds map `serif` to a different face. Decision: ship serif. If beta screenshots look off, change `DisplayFamily` to `FontFamily.Default` (one line). The layout doesn't depend on the family.
2. **Plant thresholds** (30 min, 3 h, 12 h, 24 h, 72 h) are a guess at a satisfying first week. Decision: ship as specced. Check in the 2-week beta whether most users reach BLOOM within 7 days, and if so add later stages instead of slowing the early ones.
3. **Two alerts at once push the Limits pill below the fold.** Accepted (§6). The sign-off condition is met in the normal state and in every single-alert state.
4. **Dialog titles become serif** because `AlertDialog` uses `headlineSmall`. Accepted as on-brand. If a dialog title clips at 200% font, set that dialog's title `style` explicitly rather than reverting the token.
5. **`lastAnimatedStage` is process-level state**, so the plant regrows after the process is killed. That's intended: a cold start is a fresh visit.
