# ReclaimLife — Brain Rot Killer

An Android app that puts a hard daily limit on Instagram Reels and YouTube Shorts, and holds
you to it. You set the number on day one, it counts what you actually watch, and it blocks the
feed once you hit it — with real, escalating friction (not a dismissible nag) if you try to push past it.

## What it does

- **Onboarding** — a short first-run flow: welcome, pick a nickname, set a daily reel limit
  (deliberately encouraged to start liberal and come down over time), an accountability pledge,
  and Accessibility permission setup (auto-advances once granted).
- **Live counting** — an `AccessibilityService` watches Instagram and YouTube in the background.
  Instagram's Reels feed is counted from any scroll while it's frontmost. YouTube is scoped
  specifically to its Shorts surface (internally still named `reel_*` in its view hierarchy) so
  normal YouTube browsing isn't miscounted.
- **Live overlay badge** — a small always-on-top badge shows today's count and a mood emoji
  (🌱 → 🙂 → 😕 → 😩 → 😴) that gets gloomier as you approach the limit, while you're in a
  tracked app.
- **Block screen** — hit your limit and a full-screen stop takes over the app you're in
  (no "draw over other apps" permission needed — it's a real Activity with an empty task
  affinity, not a system overlay). From there:
  - 1st "I really need a few more" → 10 extra reels, no friction.
  - 2nd ask → a guilt-trip screen before granting 2 more.
  - 3rd ask → walk 200 real steps (tracked via the step-counter sensor, with a timed fallback if
    that permission is denied) for a final 50. After that, the ask-for-more option disappears —
    the only way to get more that day is to go disable Accessibility yourself.
- **Pause tracking** — a deliberate escape hatch on the Home screen. Turning it off requires
  recording yourself out loud saying "I want to rot my brain" and playing it back before the
  button to actually pause becomes tappable.
- **Home dashboard** — today's count/limit, an editable daily limit (nudges you against raising
  it, with a reminder dialog), and a running tally of how many days you stayed within limit vs.
  went over.
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
app/src/main/java/com/example/brainrotkiller/
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
└── BrainRotKillerApp.kt      Application class wiring up the repositories
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
