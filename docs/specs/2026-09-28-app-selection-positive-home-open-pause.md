# ReclaimLife — Feature Specs: App Selection, Positive Home, Open Pause Toggle

2026-09-28 · Gobinath Manokaran · Living copy: https://claude.ai/code/artifact/c3cd8400-a015-4fb9-bb4b-7206a880e679

## Overview

This release changes ReclaimLife in three ways. People choose their own apps during onboarding, and each app is shown separately everywhere. Home leads with time won back instead of reels left. The open pause becomes something people turn on, not something forced on them.

**Where the app is today (from the code on `feature/open-pause-and-limit-ux`)**

- Tracked apps already exist (`TrackedApps.kt`): up to 8, default Instagram + YouTube, picked later from a sheet. Onboarding never asks.
- Reels are counted only for Instagram and YouTube, and as **one combined daily number** (`REEL_COUNT`). There is no per-app count.
- Minutes are timed per app, but raw spans are kept for only 8 days (`USAGE_RETENTION_MS`). That is too short for monthly or yearly figures.
- Home leads with reels remaining (UX principle: "Remaining leads over used/total").
- The open pause (`GateActivity`) shows on **every** open of every tracked app, with no way to switch it off.

**Problem.** People can't say which apps pull them in when they first arrive. Home also frames every visit around a budget running out. That is suppression, and it goes against the habit-replacement direction adopted on 2026-09-27.

**Goals**

1. People pick the apps they want help with in their first two minutes.
2. Every number can be read per app as well as in total.
3. Home opens on progress: minutes saved today, and what that adds up to over a month and a year.
4. The open pause is opt-in per person, and optionally per app.

**Non-goals**

- Counting reels in apps other than Instagram and YouTube. Other apps get time tracking and the open pause only.
- Accounts, cloud sync or sharing.
- Changing the block screen or the 2-minute swap.

**Success metrics** (measured on-device only, see Privacy)

| Metric | Target | How we read it |
| --- | --- | --- |
| Onboarding completion | No drop against the current 6-step flow | Beta cohort, self-reported plus Play Console funnel |
| Day-7 retention | +10% relative | Play Console |
| Median minutes on tracked apps, week 4 vs baseline | −20% | Shown to the user on Home; beta testers share screenshots |
| People who keep Pause before opening on after 14 days | ≥ 60% | Beta survey |
| People who turn Pause before opening off within 48 h | ≤ 20% | Beta survey. A default-on setting inflates the keep-on rate, so this is the honest counter-check. |

## Feature 1 — Choose your apps in onboarding, see them separately everywhere

Onboarding gets a "Which apps pull you in?" step. It lists the installed short-video apps and preselects the two we can count reels in. From then on, every number in the app can be split by app.

### User stories

- As a new user, I want to pick the apps that eat my time, so ReclaimLife only watches those.
- As a user, I want to see each app on its own, so I know which one is the real problem.
- As a user, I want to change my apps later without losing my history.

### Onboarding flow

The new step goes right after Welcome. The flow must stay at 6 steps (UX principle #17 already flags 6 as long), so **Limit mode and Set limit merge into one step**. Daily stays preselected, and the hourly option sits under a "More options" row on the same screen.

1. Welcome (name, optional)
2. **Your apps** (new)
3. Your limit (mode + number, merged; hourly sits under an expandable "More options" row with a chevron and TalkBack expanded/collapsed state, not a text link)
4. Your 2-minute swap
5. Permission: Accessibility first, then Usage Access, offered as the thing that unlocks "time saved" ("See how much time you win back"). Usage Access stays skippable.
6. Ready

### "Your apps" step

- **Title:** "Which apps pull you in?" **Subtitle:** "Pick the ones you'd like a little help with. You can change this anytime."
- **List:** installed apps from `SUGGESTED_APPS` first, in that order. Then an "Other apps" row opens the existing `AppPickerSheet` with a search box.
- **Each row:** app icon, app name, checkbox, and a capability line:
  - Instagram, YouTube: "Reels and time"
  - Everything else: "Time only"
- **Preselected:** Instagram and YouTube, when installed. If neither is installed, nothing is preselected.
- **Limits:** at least 1 app and at most 8 (`MAX_TRACKED_APPS`). With 0 selected, Continue is disabled and a helper line reads "Pick at least one app to continue." At 8, unchecked rows are disabled with "8 is the most, untick one to swap."
- **Primary action:** Continue. Back returns to Welcome and keeps the choices.
- **Colour:** each app keeps its chart slot colour from `TrackedApp.slot`. It is shown as a small dot beside the name, and always next to the app name, never alone (a11y).

**Design review additions (2026-09-28)**

- **Empty state:** if none of the suggested apps is installed, "Other apps" becomes a filled row at the top, with the line "None of the usual apps are here. Add the ones you use."
- **One primary action:** the Pause before opening switch (Feature 3) sits as a clearly secondary row under the list. Continue stays the only primary button.

### Separation across the app

"Separate" means every number has a per-app view, and the total is the sum of the apps shown.

| Surface | Total | Per app |
| --- | --- | --- |
| Home: minutes saved | Headline number | Row per app under it: minutes saved today, with an up/down cue |
| Home: last 24 h | Total minutes | Stacked bar split by app, legend with names |
| Limits screen (new, Feature 2) | Reels left today | Reels today per countable app |
| Insights tab | 7-day chart | Already stacked by app; add a per-app filter chip row |
| Open pause | — | Shows that app's own 24 h minutes and reels (today it shows the app's minutes only) |
| Block screen | Reels used / limit | Line under it: "Instagram 18 · YouTube 12" |
| Widget | Minutes saved today | Not shown (too small) |
| Settings | — | "Your apps" list with add/remove, and the per-app open pause switch (Feature 3) |

### Data changes

- Reel counts move from one `REEL_COUNT` to a per-app map for today (`reel_count_by_app`: `com.instagram.android=18;com.google.android.youtube=12`). The total is the sum, so the daily limit logic is unchanged.
- `DAY_HISTORY` keeps its W/O outcome and also stores per-app minutes and reels for the day (see Cross-cutting).
- **Removing an app** keeps its history. Past days still count it in totals, labelled "(removed)", so earlier trends don't jump. **Adding an app** starts its baseline from Usage Access if granted, otherwise from its first 3 days (Feature 2).

**Per-app rows must always add up to the total**

- **Upgrade day:** reels counted before the per-app split exist only as a total. Show them as a designed row, "Earlier today · 30", so Instagram + YouTube + Earlier today = the total.
- **Removed or uninstalled apps:** a greyed "Removed apps" row carries their share of past totals.
- **Charts:** stacked bars label each segment directly, or add a pattern, so colour never carries meaning alone (8 slot colours fail for colour-blind people and in dark mode). Slot colours stay fixed per app, even when an app is removed or dynamic colour is on.

### Acceptance criteria

- **Given** Instagram and TikTok are installed and YouTube is not, **when** the Your apps step opens, **then** Instagram is listed and checked, TikTok is listed and unchecked, and YouTube is not listed.
- **Given** 0 apps are checked, **then** Continue is disabled and the helper line is shown.
- **Given** a user picks Instagram and Reddit, **when** onboarding finishes, **then** `trackedApps` holds exactly those two, the open pause (if on) fires only for them, and Reddit shows "Time only" with no reel numbers anywhere.
- **Given** a user watched 18 reels on Instagram and 12 on YouTube today, **then** Home, the Limits screen, the block screen and the Insights tab all show 30 in total and 18 / 12 per app.
- **Given** an existing user upgrades, **then** they skip the new step and see a one-time, dismissible Home card instead: "Check which apps ReclaimLife helps with ›". It shows once, and dismissing it is permanent. Their current tracked apps are kept, and today's combined count is shown as a total with the per-app split starting from the next reel.
- **Given** TalkBack is on, **then** each row reads as "Instagram, reels and time, checked".

### Edge cases

- A tracked app is uninstalled: hide it from Home, keep its history, and show "Not installed" in Settings.
- Both TikTok package names are installed: show one "TikTok" row that tracks both.
- Work profile or cloned apps: out of scope for v1, and they are not listed.

## Feature 2 — Home leads with what you've won back

Home opens on minutes saved: today, and what that adds up to over a month and a year. The last 24 hours and one plain-language insight come next. Reels left moves to its own Limits screen, one tap away.

### User stories

- As a user, I want to see time I've gained, so opening the app feels like a win and not a warning.
- As a user, I want to see my last 24 hours honestly, per app.
- As a user, I want a short, kind read on my trend, including when it's going the wrong way.
- As a user, I still want to check how many reels I have left, without it being the first thing I see.

### Home layout (top to bottom)

1. **Alerts** (unchanged): accessibility off, pause active.
2. **Saved card (hero)**, kept light so it still fits at 200% font:
   - Big: "**42 min** saved today"
   - One line: "≈ **21 h** a month at this pace". This is the only month figure on Home.
   - Per-app rows, collapsed by default: "Instagram 28 min saved · YouTube 14 min saved" (no minus signs, which read as loss).
   - Tapping the card opens a detail sheet with "Since you started" and "This year ≈ 10 full days" (the year appears from day 14 only).
   - **Zero day:** "0 min saved" is never the hero. The card leads with "Since you started: **6 h 10 min**", and today drops to a secondary line, "Nothing saved yet today".
   - **No Usage Access:** the Saved card is replaced by the Last 24 hours card as hero, plus one line: "Allow Usage access to see how much time you win back ›".
3. **Limits row** (compact): "**20 left** · 30 of 50 reels today ›". It must sit above the fold on a 6.1" phone at 100% font. When 10% or less is left, or the user is blocked, it moves up to sit directly under Alerts. It opens the Limits screen.
4. **Insight card**: one sentence, an icon and at most one action (rules below).
5. **Last 24 hours card**: "**1 h 12 min** on your apps in the last 24 hours", with a labelled stacked bar by app. **Remove** today's "≈ X a month / a year at this pace" usage chips (`HomeCards.kt:333`), so Home never shows two month figures with opposite meanings.
6. 7-day strip and streak (unchanged).
7. Pause (low emphasis, unchanged).

**Design sign-off (2026-09-28): approved with the conditions above** (Limits row above the fold and promoted near the limit, one month figure, no "0 min" hero, no saved minutes for untracked time). The principle is updated to: "Remaining leads wherever limits appear; Home leads with progress."

### Baseline: what "saved" is measured against

Saved time needs a "before". Three sources, in order of preference:

1. **Usage Access with 14+ days of history:** the average daily minutes per tracked app over the **14 days before onboarding**, read from `UsageStatsManager`. Locked on day 1.
2. **Usage Access with 3–13 days of history** (new phone): a **provisional** baseline from the days available. It is refined daily until day 7 after onboarding, then locked. The Saved card says "Estimate · settles on \<date>".
   - Refinement blends the pre-onboarding days with days observed since, weighted by day count. Observed days run under the pause and limits, so this can only lower the baseline, which keeps "saved" conservative.
3. **No Usage Access, or under 3 days of history:** no saved figures at all. A baseline measured while the pause and limits are already on would come out low and show "0 saved" for weeks. Last 24 hours leads Home instead (see layout).
4. **An app added later:** the same rules, per app.

**Days and hours that don't count:** time when tracking was off (Accessibility service off, the phone off) or paused is excluded from **both** the baseline and saved minutes. The Saved card shows "Not tracked while paused" or "Tracking is off" for those stretches.

The baseline is locked once set, because a baseline that moves would quietly shrink people's progress. Settings > Your apps has "Reset baseline": at most once every 30 days, and the confirm shows the old and new figures ("Your baseline goes from 90 to 64 min a day"). This stops people gaming it.

### Formulas

Per tracked app *a*, for a day *d*: saved = max(0, baseline minutes − minutes that day). Today's figure uses minutes so far, compared against the baseline scaled to the hours elapsed. That way 9 am doesn't read as "saved 90 min".

```latex
\text{saved}_{\text{today}} = \sum_a \max\!\Big(0,\; B_a \cdot \tfrac{h_{\text{elapsed}}}{h_{\text{waking}}} - m_{a,\text{today}}\Big)
```

**Day boundary:** the ReclaimLife day starts at **04:00** by default (the usual sleep-app boundary), set in Settings as "My day starts at". A 01:00 scroll counts toward the day before. *h\_waking* is 16 h, starting 3 h after the day starts (07:00 by default). *h\_elapsed* counts only waking hours when tracking was active, so an hour with Accessibility off or a pause running never inflates "saved". Before the waking window starts, the Saved card shows yesterday's final figure ("Yesterday you saved 38 min").

Month and year are projections from the **7-day average saved per day** (S̄₇), not from today alone:

```latex
\text{month} \approx 30 \cdot \bar{S}_7 \qquad \text{year} \approx 365 \cdot \bar{S}_7
```

- Shown with "≈" and formatted with the existing `formatProjection` (min, h, days). Year is written as "≈ 10 full days".
- With fewer than 3 days of data, the month line reads "Your monthly estimate shows up on day 3".
- **Year appears from day 14 only.** A year projected from 3 days is hype.
- **"Since you started"** is real, not projected: the sum of saved minutes per tracked day since the baseline.
- **Never negative.** A day over baseline counts as 0 saved. The insight card carries the bad news, gently.
- **Optional flavour line** (in the detail sheet, rotated weekly, only when the year figure is shown and ≥ 1 day): "That's about N books" (6 h each) or "N long walks" (45 min each). No other unit conversions.

### Last 24 hours

- Rolling 24 h, the same as the existing `usageMsInWindow`, summed over tracked apps and split by app.
- Second line: "18 min less than the day before" or "18 min more than the day before". An arrow icon supports the word, and there are no minus signs.

### Insight rules

Compare the **last 7 days' average** to the **baseline** (and to the previous 7 days once there are 14+ days). One insight at a time, chosen by the first rule that matches:

| # | Condition | Example copy | Action | Icon |
| --- | --- | --- | --- | --- |
| 1 | No saved figures yet (under 3 days, or no Usage Access) | "Give it a couple of days, and we'll show you how it's going." | — | seedling |
| 2 | Up ≥ 10% vs the previous 7 days, **and** one app drives ≥ 70% of the increase | "Most of the extra time is YouTube. Turn on Pause before opening for it?" | "Turn on for YouTube" | wave |
| 3 | Up ≥ 30% vs the previous 7 days | "Scrolling has crept up this week (about +40%). Want a lower limit for a few days?" | "Try 10 fewer reels" | wave |
| 4 | Up 10–30% vs the previous 7 days | "This week's been a bit heavier (+15%). No stress — one 2-minute swap can reset the day." | "Start a swap" | wave |
| 5 | Down ≥ 30% vs baseline | "You've cut your scrolling by about 35%. That's a real change — nice work." | — | star |
| 6 | Down 10–30% vs baseline | "You're scrolling about 20% less than when you started. Good job." | — | thumbs up |
| 7 | Within ±10%, or change < 10 min/day | "Holding steady. Want to try one swap today?" | "Start a swap" | balance |

Rules are checked top to bottom, and the first match wins. The per-app rule (2) sits above the general increase rules so it can actually fire. **"Start a swap"** opens the chosen 2-minute swap (`SwapSession`) on demand. This is a new entry point, because today a swap only starts when a limit is hit.

**Copy rules** (from the habit-replacement tone):

- No red, no "warning", no "you failed", no guilt. Increases get the neutral wave icon and a single constructive offer.
- **Rounding (one rule):** below 20%, show the exact whole number ("15%"). At 20% and above, round to the nearest 5 and say "about" (22% → "about 20%", 34% → "about 35%").
- Use the nickname when set, at most once per day ("Nice work, Gobi").
- An insight changes at most once a day, so it doesn't flicker as minutes tick.

### Limits screen (new, from the Limits row)

- **Structure:** an M3 top app bar with Back, supporting predictive back. Title "Limits".
- Hero: "**20** reels left today". Under it: "30 of 50 used · hourly: 4 of 30 this hour" (when hourly mode is on).
- Per-app list: "Instagram 18 · YouTube 12" (plus "Earlier today" on upgrade day).
- Extras used today, and the edit limit and edit swap sheets (moved from Home, behaviour unchanged, including "Keep N").
- **States:**
  - **Normal:** as above.
  - **Paused:** hero reads "Paused · back at 16:40", numbers greyed, lowering still allowed.
  - **Blocked:** hero reads "0 left today", with "Start a swap" as the primary action.
  - **Extras in use:** "+5 extra today" chip beside the hero.
  - **Hourly limit hit:** "Back in 12:04, or after a 2-minute swap".
- The widget keeps showing reels left, since that is what it is for. Its numbers and its "42 min saved today" line come from the same calculation and refresh as Home (trust principle).

### Acceptance criteria

- **Given** a 14-day baseline of 90 min/day from Usage Access, and 30 min used by 15:00 (8 of 16 waking hours), **then** Home shows "15 min saved today" (45 − 30).
- **Given** a 7-day average of 40 min saved per day, **then** Home shows "This month ≈ 20 h" and "This year ≈ 10 days".
- **Given** a day with more use than the baseline, **then** saved shows "0 min" and never a negative number.
- **Given** no Usage Access and 1 day of data, **then** the Saved card shows the baseline-building state and no projections.
- **Given** the 7-day average is 22% under baseline, **then** insight #6 shows with "about 20% less".
- **Given** this week is 35% above the previous week, **then** insight #3 shows, with no red colour, and its button opens the edit limit sheet prefilled with limit − 10.
- **Given** the user taps the Limits row, **then** the Limits screen opens, and its "left" number equals the widget's and the block screen's.
- **Given** TalkBack, **then** the Saved card reads "42 minutes saved today. About 21 hours this month. About 10 days this year."

* **Given** the Accessibility service was off from 12:00 to 15:00, **then** those hours count toward neither baseline nor saved, and the Saved card notes "Tracking was off for 3 h".
* **Given** a 1-hour pause, **then** saved minutes don't grow during it, and the card shows "Not tracked while paused".
* **Given** 0 min saved so far today and 6 h 10 min since the start, **then** the hero reads "Since you started: 6 h 10 min".
* **Given** no Usage Access, **then** Home shows Last 24 hours as the hero and no saved or projected figures.
* **Given** day 5 after onboarding, **then** no year figure is shown anywhere.
* **Given** this week is +20% and YouTube is 80% of the increase, **then** insight #2 shows, not #4.
* **Given** the Limits row with 4 of 50 reels left, **then** it sits directly under Alerts.

## Feature 3 — A switch for "Pause before opening"

The open pause becomes a setting. A main switch turns it on or off, and each tracked app can opt out. Today the pause (`GateActivity`: a 2-second breath, the app's last 24 h, then "Skip for now" / "Continue") fires on every open with no way to turn it off.

### User stories

- As a user, I want to choose whether ReclaimLife makes me pause before opening my apps.
- As a user, I want the pause on for the app that hooks me most, and off for one I use for messages.

### Behaviour

| Setting | Where | Default | Effect |
| --- | --- | --- | --- |
| **Pause before opening** (main switch) | Settings > Your apps, top of the list; also on the onboarding "Your apps" step | On for new users (switch shown, can be turned off in one tap); unchanged (on) for existing users | Off = no gate for any app. Reel counting, limits and the block screen are unaffected. |
| **Per-app switch** | Settings > Your apps, one switch per row (disabled and greyed when the main switch is off) | On for every app | Off = no gate for that app only |
| **Wait length** (should-have) | Under the main switch | 2 s (today's `PAUSE_MS`) | 2 s · 5 s · 10 s, chosen with option chips, not a slider |

- Onboarding copy, under the app list: switch labelled "Take a short breath before opening these apps", with helper text "A 2-second pause and a look at your day. You can turn it off anytime."
- **Turning it off** loosens a guard rail, so it gets the existing loosening pattern. A confirm reads "Turn off Pause before opening?" with the body "It's helped you skip 12 opens this week." (from `gateDecisions`, shown only when > 0; otherwise the body reads "It gives you a moment to choose before the feed starts."). The primary button is **"Keep it on"**, the secondary is "Turn off". Neutral colours only, no red.
- **Turning it on** needs no confirm.
- The time-boxed tracking pause (the existing Pause on Home) changes one rule. During 15 min and 1 hour pauses the gate still shows (it's a breath, not a limit), but during a Rest of today pause it does not.
- The change applies from the next app open. A gate already on screen finishes normally.
- Insight #2 (Feature 2) can offer "Turn the pause on for YouTube". The button turns on the main switch if needed plus that app's switch, and a snackbar offers Undo.

**Design review additions (2026-09-28)**

- **Last app switched off:** turning off the per-app switch of the last app that still has the pause on gets the same "Keep it on" confirm as the main switch. Otherwise the confirm could be sidestepped one app at a time.
- **Main switch off:** per-app switches keep their states but are disabled, with the supporting text "Turn on Pause before opening to choose apps."
- **No off link on the gate itself.** An exit offered at the moment of craving becomes the one people use. Turning it off lives in Settings only.
- **Naming:** always write "Pause before opening" in full. Never shorten it to "the pause", which is the tracking pause (Feature 4).

### Data changes

- `SettingsRepository`: `gate_enabled: Boolean` (default true), `gate_disabled_apps: Set<String>` (default empty) and `gate_wait_ms: Long` (default 2000).
- `ReelBlockerAccessibilityService.startAppSession` checks `gateEnabled && target !in gateDisabledApps` before `showGate`.
- An app removed from tracking loses its per-app entry.

### Acceptance criteria

- **Given** the main switch is off, **when** the user opens Instagram, **then** no gate appears, reels are still counted, and the block screen still shows at the limit.
- **Given** the main switch is on and YouTube's switch is off, **when** the user opens YouTube, **then** no gate appears. **When** they open Instagram, **then** the gate appears.
- **Given** the user turns the main switch off, **then** the confirm shows with "Keep it on" as the primary button. Choosing it leaves the setting unchanged.
- **Given** the wait length is 5 s, **then** Skip and Continue appear after 5 s.
- **Given** an existing user upgrades, **then** the pause behaves exactly as before (on, 2 s, all apps).
- **Given** TalkBack, **then** each per-app switch reads "Pause before opening Instagram, on".

* **Given** only Instagram has the pause on, **when** the user switches Instagram off, **then** the "Keep it on" confirm shows.
* **Given** a Rest of today pause is running, **when** the user opens Instagram, **then** no gate appears. **Given** a 15 min pause, **then** the gate still appears.

## Feature 4 — Replace the voice recording for pausing

**Decision: remove the record-and-play-back step, and drop the microphone permission.** Pausing gets friction in proportion to its length instead: a hold for short pauses, a reason and a calm countdown for longer ones, and a delayed start plus a typed intention for the rest of the day.

**Status:** approved by product on 2026-09-28. The "Free time" rename is deferred to a later release.

### What happens today

`PauseRecordDialog` asks people to record "I'm choosing to take a break", play it back, then pick a length. "Rest of today" needs two separate recordings, both played back. That is 3 to 5 steps, a microphone permission prompt, and a system settings detour if the mic was denied.

### UX review (lead designer principles vs the current flow)

| Principle | Where the recording breaks it |
| --- | --- |
| Tone: no shame, habit replacement | Hearing your own voice say a scripted line reads as a penance, not a pause. |
| Trust and privacy | A screen-time app asking for the microphone raises suspicion. It adds a Play Data safety entry and a section in `PRIVACY.md` for one feature. |
| Accessibility | Unusable for people who can't speak or hear clearly, and awkward on a bus or in an office, which is exactly where people pause for work. |
| Friction is the point | Keep this: friction should make people think, not wear them out. Recording costs the same whether the reason is good ("teaching a class") or not. |
| Pauses are time-boxed | Unchanged, and kept in the new design. |

**Verdict.** Keep a deliberate moment, lose the performance. The friction should scale with the pause length, stay silent and one-handed, and ask *why* so the app can learn and help later.

### New flow

| Length | Friction | Copy |
| --- | --- | --- |
| 15 min | Pick a reason (one tap), then **hold the button for 3 s** (a ring fills) | "Taking 15 minutes. Hold to start." |
| 1 hour | Reason, then a **10 s calm screen** showing today's minutes saved, then Start | "You've won back 42 min today. Want the full hour?" Primary: "Start my hour". Secondary: "Maybe 15 min" |
| Rest of today | Reason, then **type a short intention** (free text, at least 3 words), then a **60 s delayed start** that can be cancelled from a notification | "What's the plan for tonight?" Placeholder: "Movie night with friends" |

- **Reasons** (chips, one required): Work or study · Relaxing on purpose · With friends · Other. They are stored on-device with the pause.
- **Budget:** at most 1 "rest of today" per 3 days. When it isn't available, the chip reads "Available tonight at 21:10" when that is under 24 h away, and "Available Fri 21:10" otherwise. It is never hidden, and it stays focusable (M3 disabled chips aren't), with the reason in supporting text so TalkBack users hear it.
- The existing rules stay: every pause is time-boxed, limits can only be lowered while paused, and the Home banner shows a live countdown.
- **Accessibility:** with TalkBack or Switch Access on, the hold is replaced by a 3 s countdown and then a Start button. The instruction text ("Hold to start") is always visible, never a tooltip. The typed intention accepts voice typing from the keyboard, which is optional and needs no permission for us.

### Screen by screen

1. **Pause sheet** (from Pause on Home), built as an M3 modal bottom sheet, not a dialog. Title "Take a break?" Subtitle "Tracking and limits turn back on by themselves." Length chips (15 min preselected · 1 hour · Rest of today), then reason chips. The primary button changes with the length. Cancel is always visible.
2. **Hold to start** (15 min). A 3 s ring fills around the button, with a light haptic at the start and on completion. Releasing early drains the ring back over 300 ms. There is no error text.
3. **Calm screen** (1 hour). It reuses the breathing circle from `GateActivity`, with a countdown number. It shows today's saved minutes (Feature 2). If the baseline isn't ready yet, it shows "1 h 12 min on your apps in the last 24 hours" instead.
4. **Intention** (Rest of today). One text field, 3 words minimum and 80 characters maximum. The Home paused banner then reads "Enjoy: Movie night with friends · back at 00:00". The text is deleted when the pause ends and is never written to `pause_log`.
5. **Delayed start** (Rest of today). A Home card counts down 60 s with Cancel. If notifications are already allowed, a notification with Cancel mirrors it. It uses VISIBILITY\_PRIVATE, so the intention never shows on the lock screen. We never ask for the notification permission just for this. People can leave the app during the countdown.

### Edge cases

- **Pausing while blocked by a limit:** allowed, and the block lifts when the pause starts (current behaviour).
- **Many short pauses:** there is no cap on 15 min or 1 hour pauses. From the 3rd pause in a day, every length also gets the 10 s calm screen.
- **Ending early:** "Resume now" on the banner, one tap, no confirm (tightening needs no friction). Ending Rest of today early does not refund the budget.
- **Cancelling during the 60 s delayed start** refunds the budget.
- **End time:** stored as epoch ms, so time zone and clock changes don't stretch a pause. Rest of today ends at local midnight (`nextLocalMidnight`).
- **Reason "Other":** no free-text field.
- **Upgrade:** any leftover recording is deleted on first launch (`deleteStalePauseRecording`), and a pause already running keeps its end time.

### Ideas to evaluate next (ranked by value ÷ effort)

1. **Reason-aware suggestions.** If "Work or study" is picked 3+ times for YouTube in a week, offer "Turn off the open pause for YouTube during 9–17?" Fewer pauses are needed when the rules fit real life.
2. **Planned pauses.** Schedule one ahead ("Sunday 20:00–22:00, movie night"). Planned scrolling is intentional, so it needs no friction at the moment of use.
3. **Later: "Free time" reframe (agreed 2026-09-28, not in this release).** Rename Pause to "Free time" and end it with a warm check-in notification: "Free time's up — how was it?" (Good / Too long). The answers feed Insights.
4. **Pause reflection in Insights.** "3 pauses this week, mostly Work or study." Shown as a fact, never a score.
5. **Earned pauses.** Each day within the limit adds 15 min of pause credit (capped at 1 h), which makes pausing a reward and not an escape hatch. It needs care so it doesn't feel like a game to game.

### Data and code changes

- Delete the recording, playback and permission code from `PauseRecordDialog`, and remove `RECORD_AUDIO` from `AndroidManifest.xml`.
- Remove the Microphone section from `PRIVACY.md`, and update the Play Data safety form.
- New store `pause_log`: `startMs,endMs,reason,durationKind`, kept 90 days, used for the budget and Insights.
- `PauseDuration` stays as it is. Rest of today gains an `availableAfterMs` check.

### Acceptance criteria

- **Given** a fresh install, **then** the app never asks for the microphone, and the manifest has no `RECORD_AUDIO`.
- **Given** the user picks 15 min and a reason, **when** they hold for 3 s, **then** the pause starts. Releasing early resets the ring with no error message.
- **Given** the user picks 1 hour, **then** Start is disabled for 10 s while today's saved minutes show, and "Maybe 15 min" switches the length without restarting.
- **Given** the user picks Rest of today and types an intention, **then** tracking pauses 60 s later. A notification with Cancel is shown during those 60 s.
- **Given** Rest of today was used 1 day ago, **then** its chip is disabled and reads "Available Fri 21:10" (or "Available tonight at 21:10" under 24 h). It stays focusable, and TalkBack reads the reason.
- **Given** TalkBack, **then** the hold is replaced by a 3 s countdown and then a Start button labelled "Start 15 minute pause".

* **Given** two pauses already today, **when** the user starts a 15 min pause, **then** the 10 s calm screen shows before the hold.
* **Given** a Rest of today countdown, **when** the user taps Cancel, **then** no pause starts and Rest of today is still available.
* **Given** a Rest of today pause with an intention, **when** it ends at midnight, **then** the intention text no longer exists on the device.

## Cross-cutting

### Data model

| Store | Change | Why |
| --- | --- | --- |
| `reel_usage` | `reel_count_by_app` replaces `reel_count`. Migrate by putting today's total under a synthetic "unknown" key, dropped at midnight. | Per-app reels (F1) |
| `reel_usage` | New `daily_usage`: `yyyy-MM-dd=pkg:minutes:reels,…`, kept 400 days (≈ 12 KB for 8 apps) | Month, year and "since you started" figures (F2); raw spans stay at 8 days |
| `settings` | `baseline_by_app` (pkg → minutes/day) and `baseline_source` (usage\_access / first\_days) | Saved minutes (F2) |
| `settings` | `gate_enabled`, `gate_disabled_apps`, `gate_wait_ms` | Pause switch (F3) |
| `settings` | `onboarding_version = 2` | Existing users skip the new step |

Further keys from the design review: `day_start_hour` (default 4), `baseline_provisional_until` (date, when refining), `baseline_reset_at` (for the 30-day limit), `onboarding_apps_card_dismissed`, and `pause_log` (Feature 4, 90 days).

**A Settings screen does not exist yet.** Today the app picker opens from Home. This release adds a Settings entry (gear icon, top-right of the Today tab) holding Your apps, Pause before opening, Reset baseline, and the existing limit/swap editors.

### Privacy

Everything stays on the device, and there are no network calls, the same as today. Update `PRIVACY.md` to state that daily per-app minutes are kept for up to 400 days, on-device only. Clearing app data removes them. Pause reasons are kept for 90 days in pause\_log. Typed intentions are never stored. Usage Access stays optional, and without it the baseline comes from the first 3 days.

### Accessibility

- Per-app colour dots always sit beside the app name. Up/down trends always pair an arrow with a word.
- Insight icons have content descriptions. The insight text is announced politely when it changes (at most once a day).
- Large numbers scale with the font size, and the Saved card's three figures wrap to a column at 200% font.

### States to design

| State | Home shows |
| --- | --- |
| Baseline being calculated (reading Usage Access) | Saved card skeleton with "Working out your starting point…", for under 2 s typically |
| Usage Access revoked after the baseline was set | Baseline kept. Saved continues from ReclaimLife's own timing, with a quiet line: "Using ReclaimLife's timing only · Allow Usage access ›" |
| Phone off, or no data for a day | The day is excluded from saved and trends, and shows "No data" in the 7-day strip (with a shape as well as a colour) |
| Accessibility service off | The existing alert, plus the hours excluded from saved (Feature 2) |

### Tone checklist (every new string)

- Frames around what you get back, never around loss.
- No red for increases, no "warning", no shame words.
- One constructive offer per nudge, never a list of demands.

**Two pauses, two names.** "Pause" means the time-boxed tracking pause (Feature 4). "Pause before opening" is the gate (Feature 3), always written in full. The later "Free time" rename removes the clash for good.

### Decisions and open questions

Product decisions made on 2026-09-28. Anything marked Open needs the named person before build starts.

| # | Question | Decision | Why | Status |
| --- | --- | --- | --- | --- |
| 1 | Home no longer leads with reels left | Approved with conditions: the Limits row sits above the fold and moves under Alerts at ≤ 10% left or when blocked; one month figure on Home; no "0 min" hero; no saved minutes for untracked time. | Progress on Home, and remaining still leads wherever limits appear. | Decided (lead designer) |
| 2 | Onboarding at 6 or 7 steps | Merge Limit mode into Set limit and stay at 6, with hourly under an expandable "More options" row. | UX principle #17 already calls 6 long. | Decided |
| 3 | Open pause default for new users | On, with the switch visible on the Your apps step. Track turn-offs within 48 h alongside the 14-day keep-on rate. | The pause is the core habit tool, and default-on inflates the keep-on metric. | Decided |
| 4 | Day boundary and waking hours | The day starts at 04:00, and "My day starts at" is in Settings. Waking window: 16 h from 3 h after the day starts. | 07:00 broke for night owls and shift workers. | Decided (designer pushback accepted) |
| 5 | Per-app reel limits | Not in v1. Backlog. | One shared limit keeps setup short. | Decided |
| 6 | Widget: minutes saved or reels left | Reels left leads, plus one line "42 min saved today" from the same calculation and refresh as Home. | The widget is a glanceable budget, and the numbers must agree. | Decided |
| 7 | Rest of today budget | 1 per rolling 72 h. The chip shows the exact unlock time. | A rolling window doesn't line up with a weekday. | Decided |
| 8 | "Free time" rename | Later release. Until then, "Pause before opening" is always written in full. | Agreed with Gobinath on 2026-09-28. | Decided |
| 9 | Existing users never picked apps | One-time Home card "Check which apps ReclaimLife helps with ›". Dismissing it is permanent. | They already have apps, so "Pick" was the wrong verb. | Decided |
| 10 | Short Usage Access history | 3–13 days → a provisional baseline, refined until day 7, then locked. Under 3 days, or no Usage Access → no saved figures. | A locked 3-day baseline is noisy and permanent. | Decided (designer pushback accepted) |
| 11 | Does the gate show during a pause? | Yes for 15 min / 1 hour. No during Rest of today. | Movie night shouldn't be interrupted on every open. | Decided |
| 12 | Does paused time count toward baseline or saved? | Excluded from both. | Otherwise pausing would inflate "saved". | Decided |
| 13 | Can "Reset baseline" be gamed? | Once per 30 days, and the confirm shows the old and new baseline. | Keeps the number honest. | Decided |
| 14 | Does the app picker need `QUERY_ALL_PACKAGES`? | No. The manifest already declares `<queries>`. Confirm it covers every `SUGGESTED_APPS` package plus a launcher-intent query for "Other apps". | Avoids a sensitive Play permission. | Open: engineering check |

### Release plan

1. **Slice 1 — Data:** per-app reel counts, `daily_usage`, baseline capture and migration. Nothing visible changes. Unit tests on the formulas and the migration.
2. **Slice 2 — Feature 3:** Pause before opening switch plus the Settings screen, shipped with Feature 4 (new pause flow, microphone permission removed). Smallest user-visible win, and low risk.
3. **Slice 3 — Feature 1:** onboarding step, merged limit step, per-app splits on existing screens.
4. **Slice 4 — Feature 2:** new Home, Limits screen, insight engine. Ship to beta testers for 2 weeks and check the insight copy with 5 users before the Play release.

**Definition of done (each slice):** acceptance criteria pass on a Pixel emulator (API 34) and one Samsung device; unit tests for all new pure functions (baseline, saved, projection, insight choice, migration); TalkBack walkthrough; strings reviewed against the tone checklist; `PRIVACY.md` updated where data changes.
