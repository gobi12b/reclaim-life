# ReclaimLife

An Android app that helps you swap some Instagram Reels and YouTube Shorts for small things that
feel better. You pick a limit, it counts what you actually watch, and when you reach it the feed
is replaced — not just blocked — by a 2-minute alternative you chose up front: a breathing
exercise, a flashcard deck, or a journaling prompt. Habit replacement beats habit suppression,
so the app leads you somewhere instead of only stopping you, and its tone stays friendly.

## What it does

- **Onboarding** — a short first-run flow: a welcome with an optional name field, how to limit (daily / hourly / both — Daily preselected, so it's one
  tap), the limit(s) for that choice (deliberately encouraged to start liberal and come down
  over time), your 2-minute swap (Breathe / Flashcards with a deck / Journal — Breathe
  preselected), and Accessibility permission setup with a prominent disclosure
  (auto-advances once granted).
- **Live counting** — an `AccessibilityService` watches Instagram and YouTube in the background.
  Only the reels viewers count: Instagram's `clips_viewer_view_pager` (Reels tab and any reel
  opened from feed/DMs/profile; Instagram calls Reels "clips", and its `reel_viewer_*` IDs are
  Stories) and YouTube's Shorts `reel_*` views. Feed, profiles, explore and stories don't count.
  A swipe counts once, when the pager settles on a new reel.
- **Live overlay badge** — a small always-on-top badge shows today's count and a mood emoji
  (🌱 → 🙂 → 😕 → 🥱 → 😴) that winds down as you approach the limit, while the Reels /
  Shorts viewer is on screen.
- **Swap screen** — reach your limit and the app you're in is replaced by your 2-minute swap
  (no "draw over other apps" permission needed — it's a real Activity with an empty task
  affinity, not a system overlay). Breathing is a guided in-4 / out-6 animation; flashcards are
  built-in decks you tap to flip; journaling shows a prompt and a text box whose contents are
  never saved. The swap can be skipped; after it, a short summary offers "Back to my day",
  another 2 minutes, or "I'd like a few more":
  - 1st ask → 10 extra reels, once a 2-minute swap is finished (the opening one counts).
  - 2nd ask → 5 more, again after a finished swap.
  - 3rd ask → a 200-step walk (step-counter sensor, with a 2-minute timed fallback if that
    permission is denied) for a final 5. After that, "That's all the extras for today."
- **Daily, hourly, or both** — Chosen in onboarding and switchable any time from Home's "Limit by" picker.
  The hourly limit (e.g. 30 reels per hour) is a rolling 60-minute window, not a clock hour, so
  30 at 10:59 + 30 at 11:00 doesn't work. Hit it and the block screen opens into the same swap;
  finishing it starts the hour afresh and goes back to the app. Skip it and the block counts down
  until the oldest reels age out. In hourly-only mode the daily count is still shown but never
  blocks, and a day counts as "within" in the history if 3 or fewer breaks reopened reels. Dropping a limit (e.g. Both → Daily) asks for confirmation
  and isn't allowed while paused, same as raising a limit.
- **Pause tracking** — a deliberate escape hatch on the Home screen. Turning it off requires
  recording yourself out loud saying "I'm choosing to take a break" and playing it back before the
  button to actually pause becomes tappable. Every pause is time-boxed (15 min / 1 hour / rest of
  today — which needs two separate recordings, both played back) and resumes on its own; Home,
  the widget and the overlay badge show the countdown. While paused the limit can be lowered
  but not raised.
- **Home dashboard** — today's remaining reels and count/limit (any extra allowance called out
  explicitly), an editable daily limit (stepper + presets, capped at 300; raising it asks for
  confirmation at Save), your 2-minute swap (change it, or "Try it now"), a 7-day within/over strip with the current streak, and a banner when
  the counter is off *or* switched on but not actually running.
- **Home-screen widget** — a Glance widget showing today's count, updated live.

## Why it's built this way

- **AccessibilityService, not UsageStats** — counting individual reels (not just "time in app")
  requires reading scroll events, which only Accessibility exposes. This is also why Play
  Protect treats a sideloaded build of this app with suspicion — Accessibility + unknown install
  source is the same signature as OTP-stealing malware, so distributing this beyond your own
  devices realistically means Play Store distribution with an Accessibility API declaration.
- **BlockActivity/onboarding use an empty `taskAffinity`** — launching over Instagram/YouTube
  and finishing back into it (rather than into this app's own task) needs the block/gate screens
  to not share a task with `MainActivity`.

## Project layout

```
app/src/main/java/io/github/gobi12b/reclaimlife/
├── data/                   DataStore-backed repositories (settings, usage) + Mood + TargetApps
├── service/                ReelBlockerAccessibilityService — the core detection/blocking logic
├── ui/
│   ├── onboarding/         First-run flow
│   ├── home/               Home screen, edit-limit flow, pause/record dialog
│   ├── block/               Full-screen limit-reached flow (guilt/walk escalation)
│   ├── common/              Shared BrandMark / sprout icon
│   └── theme/                Compose theme (nature palette)
├── widget/                  Glance home-screen widget
├── MainActivity.kt / MainViewModel.kt
└── ReclaimLifeApp.kt         Application class wiring up the repositories
```

## Setup

1. Open in Android Studio, or build from the command line:
   ```
   ./gradlew installDebug
   ```
2. On first launch, complete onboarding and grant Accessibility access when prompted
   (Settings → Accessibility → ReclaimLife reel counter). If the toggle won't move, the device
   is likely blocking it as a "restricted setting" for a sideloaded app — go to
   Settings → Apps → ReclaimLife → ⋮ menu → Allow restricted settings, then try again.
3. Open Instagram Reels or YouTube Shorts — the live counter badge should appear.

## Known limitation

YouTube Shorts doesn't reliably fire scroll-related accessibility events per swipe, so exact
per-swipe counting there is best-effort rather than guaranteed precise — this was confirmed by
live on-device diagnostics, not assumed.

## CI/CD

`.github/workflows/android.yml` runs on every pull request and every push to `main`:

1. **Lint, test & build** — Gradle wrapper validation, `lintDebug`, `testDebugUnitTest`, then
   `assembleDebug` + `assembleRelease` (the release build runs R8, so shrinking problems fail
   the PR rather than the release). Lint/test reports and the debug APK are uploaded as artifacts.
2. **Signed release** (pushes to `main` only, after step 1 passes) — builds the signed release
   APK, verifies it with `apksigner`, and publishes a GitHub Release tagged
   `v<versionName>-build.<run number>` with the APK and its SHA-256.

The release job needs these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 your-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |
| `ANDROID_KEY_ALIAS` | `keyAlias` |
| `ANDROID_KEY_PASSWORD` | `keyPassword` |

Without them the release job fails instead of publishing an unsigned APK.
