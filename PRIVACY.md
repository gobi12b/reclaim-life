# ReclaimLife privacy policy

_Last updated: 29 September 2026_

ReclaimLife (Android package `io.github.gobi12b.reclaimlife`) puts a daily limit on Instagram
Reels and YouTube Shorts. This policy covers exactly what the app accesses, what it keeps, and
where that goes.

**In short:** ReclaimLife has no internet access. It doesn't have Android's `INTERNET`
permission, so it cannot send anything anywhere. It contains no ads, analytics, crash reporting
or other third-party SDKs, and it has no accounts. The only copy of your data that can leave
the phone is Android's own backup, described below.

## Accessibility service

ReclaimLife's reel counter is an Android Accessibility service. You turn it on yourself in
Settings → Accessibility, after the app shows you what it does and you agree.

- **What it receives:** accessibility events only from the apps you choose to watch in
  ReclaimLife — Instagram (`com.instagram.android`) and YouTube (`com.google.android.youtube`)
  unless you change the list. It gets no events from any other app.
- **What it looks at:** scroll events and the view IDs of on-screen layout elements inside
  those two apps. That is enough to tell whether the Reels / Shorts viewer is open and when you
  swipe to the next reel (Instagram and YouTube only). While you're in a watched app, it also
  checks the name of the app in front, so it can notice when you leave and time how long you
  spent there.
- **What it never reads or keeps:** your messages, what you type, passwords, or what's in the
  reels. No screen content is stored. The only result is a number: how many reels you've
  watched today.
- **What it does with this:** counts reels, shows the counter badge over the Reels / Shorts
  viewer, shows a short pause screen when you open a watched app, and opens ReclaimLife's
  block screen once you reach your reel limit. Nothing else.

## Installed apps list

To let you choose which apps to watch, ReclaimLife lists the apps on your phone that have a
launcher icon. The list is only shown in that picker; only the apps you choose are saved.

## Usage access (optional)

The pause screen, Home and Insights show how long you've spent in your watched apps (the last 24
hours, and the last week), and Home shows the time you've won back against your usual daily time
before ReclaimLife, read once from the last 14 days. If you allow Usage access (Settings → Usage access), ReclaimLife reads
Android's own record of when those apps were in front, so the figures are complete. It only looks
up the apps you chose, keeps only per-app daily totals (below) and your usual daily minutes per app, and otherwise works
it out fresh each time. Without Usage access it uses its own timing instead (below) and shows no
time-saved figures. You can turn it off
at any time in the same setting.

## Pausing tracking

ReclaimLife does not use the microphone. Pausing asks for a reason (one tap: Work or study,
Relaxing on purpose, With friends, Other), which is kept with the pause for 90 days. A pause for
the rest of the day also asks you to type a short plan. That text is shown on Home while the pause
runs and is deleted when it ends; it is never kept with the pause or included in backups. Older
versions recorded a spoken phrase; any leftover recording is deleted the first time this version
starts.

If notifications are already allowed, a one-minute heads-up with Cancel is shown before a
rest-of-today pause starts. ReclaimLife never asks for the notification permission.

## Physical activity (step counter)

After your third request for extra reels in a day, ReclaimLife asks you to walk 200 steps. If
you choose to allow the Physical Activity permission, the phone's step-counter sensor is read
while that screen is open, only to count those steps. Step counts are not stored. If you don't
allow it, a two-minute timer is used instead.

## Data stored on your phone

ReclaimLife stores the following in its private app storage:

- your nickname (optional), daily reel limit, whether setup is finished, and when any pause
  ends;
- today's reel count, extra reels granted and requested today, and a per-day history of whether
  you stayed within your limit (plus totals of those days);
- which apps you chose to watch;
- when those apps were in front (start and end times only), deleted after about a week;
- minutes and reels per watched app per day, kept for up to 400 days, for the monthly, yearly and
  "since you started" figures;
- your usual daily minutes per app from before ReclaimLife (your baseline);
- when tracking was off (the counter switched off or the phone off), deleted after two weeks;
- your pauses (start, end, length and reason), deleted after 90 days;
- whether you chose Skip or Continue on the pause screen, and when, deleted after about a week;
- your Pause before opening choices and when your day starts;
- your tree's name (optional), the day you planted it, and your longest streak (so milestones
  you've earned stay on your tree);
- for each day, whether your tree grew or rested and how much it grew, kept for up to 400 days
  (older days are kept only as one running total);
- when you changed a limit, with the old and new number, deleted after two weeks;
- how many 2-minute swaps you finished each day, deleted after two weeks.

When you tap Share on the Profile tab, ReclaimLife draws a picture of your tree into its
private cache and hands it to the app you pick; it's replaced the next time you share.

The home-screen widget shows the same count and limit. Uninstalling the app, or clearing its
storage in Settings → Apps → ReclaimLife, deletes all of it.

## Android backup

ReclaimLife uses Android's Auto Backup so your settings and history survive a new phone. Only
the settings, usage and daily-usage files described above (including your tree) are included. The typed pause plan and
widget state are not. If backup is turned on for your Google account, Android saves these files to your
Google account's backup storage (or copies them during a device-to-device transfer), under
Google's terms. ReclaimLife itself does not send them anywhere. You can turn this off in your
phone's backup settings.

## Other permissions

Android adds `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE` through the WorkManager
library, which updates the home-screen widget. They are used only to keep the widget up to date
and involve no personal data.

## Children

ReclaimLife is not directed at children. It does not collect personal information from anyone:
everything it stores stays on the phone, as described above.

## Changes

If this policy changes, the new version will be published at this address with a new date.

## Contact

Questions: gobi12b@gmail.com
