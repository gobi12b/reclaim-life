# ReclaimLife privacy policy

_Last updated: 28 September 2026_

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

- **What it receives:** the service is configured to get accessibility events only from
  Instagram (`com.instagram.android`) and YouTube (`com.google.android.youtube`). It gets no
  events from any other app.
- **What it looks at:** scroll events and the view IDs of on-screen layout elements inside
  those two apps. That is enough to tell whether the Reels / Shorts viewer is open and when you
  swipe to the next reel. While you're in Instagram or YouTube, it also checks the name of the
  app in front, so it can hide the badge when you leave and time how long you spent there.
- **What it never reads or keeps:** your messages, what you type, passwords, or what's in the
  reels. No screen content is stored. The only result is a number: how many reels you've
  watched today.
- **What it does with this:** counts reels, shows the counter badge over the Reels / Shorts
  viewer, shows a short pause screen when you open Instagram or YouTube, and opens ReclaimLife's
  block screen once you reach your limit. Nothing else.

## Usage access (optional)

The pause screen shown when you open Instagram or YouTube shows how long you've spent in that
app over the last 24 hours. If you allow Usage access (Settings → Usage access), ReclaimLife reads
Android's own record of when those two apps were in front, so the figure covers the full 24
hours. It only looks up Instagram and YouTube, keeps nothing from that record, and works it out
fresh each time. Without Usage access it uses its own timing instead (below). You can turn it off
at any time in the same setting.

## Microphone (pause tracking)

To pause tracking, ReclaimLife asks you to record yourself saying a phrase and play it back. The
microphone permission is requested only when you tap "Start recording".

- The recording is saved only in the app's private cache folder on your phone.
- It is deleted when the pause dialog closes. If the app is closed unexpectedly while the dialog
  is open, the leftover file is deleted the next time the app starts.
- It is never uploaded, shared, transcribed or analysed. It is just played back to you.

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
- when Instagram and YouTube were in front over the last 24 hours (start and end times only),
  which is deleted as it passes 24 hours old.

The home-screen widget shows the same count and limit. Uninstalling the app, or clearing its
storage in Settings → Apps → ReclaimLife, deletes all of it.

## Android backup

ReclaimLife uses Android's Auto Backup so your settings and history survive a new phone. Only
the two settings/history files listed above are included. The pause recording and widget state
are not. If backup is turned on for your Google account, Android saves these files to your
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
