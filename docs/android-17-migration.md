# Android 17 / API 37 migration

Verified locally on 2026-09-19. Both compileSdk and targetSdk are now 37;
minSdk remains 26. JDK 25, AGP 9.4.0 and Gradle 9.7.1 are unchanged.
AGP 9.4 supports API 37 and uses Build Tools 36.0.0 by default.

Install the platform before building:

```sh
sdkmanager "platforms;android-37.0" "build-tools;36.0.0"
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

The PR workflow installs the same SDK packages. The existing required build
check and Dependabot patch/minor auto-merge policy remain in effect.

## Behavior changes addressed

- **Background audio:** opening a recording binds the playback service. Pressing
  Play in the visible activity starts it and promotes it to a `mediaPlayback`
  foreground service before requesting audio focus and starting the player.
  This provides the while-in-use capabilities required for target 37 background
  playback. The manifest declares the service type and its normal permission.
  A low-importance playback notification opens the player or pauses playback.
  Pause, completion, error and destruction release audio focus and foreground
  status. Focus loss, including duck requests, pauses speech recordings;
  resumption requires the user to press Play again. Denied focus does not start
  playback. Process recreation never automatically resumes audio.
- **Keyboard recreation:** the editor saves whether the IME was visible, then
  requests it again after UI initialization and activity resume when restoring
  that state. Android 17 no longer automatically restores IME visibility after
  configuration changes. A previously hidden keyboard is not explicitly shown.
- **Back navigation:** the playback toolbar uses the AndroidX back dispatcher.
- **Sharing:** image, audio, note export and diagnostic-log share intents explicitly
  grant URI read access. This makes access intentional and prepares for the
  removal of implicit grants in Android 18; that removal is not an Android 17
  requirement.

The application-source audit found no LAN access, MessageQueue reflection,
static-final field mutation, or dynamic native-code loading to migrate. Widgets
use text and resource drawables rather than embedded bitmap/icon payloads, so
there is no payload to reduce for the new aggregate RemoteViews image limit.
The manifest does not impose orientation or resizability restrictions. Audio
recording already uses a microphone foreground service started from its UI.

## Validation

- `testDebugUnitTest`: **39 tests, 0 failures, 0 skipped**.
  Note persistence and PDF tests run on APIs 28 and 37; window insets on APIs
  26, 30 and 37; seven playback lifecycle/focus tests on APIs 26 and 37.
- `lintDebug`: **0 unbaselined errors, 63 warnings**. The existing baseline
  still contains 78 translation errors; it was not changed for this migration.
- `assembleDebug` and `assembleRelease`: successful, including R8/shrinking.
  The release APK is unsigned as before.
- `aapt dump badging`: built debug APK reports compile SDK 37, target SDK 37,
  minimum SDK 26, and the media playback foreground-service permission.
- `actionlint` 1.7.12 and `git diff --check`: successful.

Robolectric tests exercise service state and API-level code paths; they do not
prove Android's real foreground-service admission or audio hardware behavior.
No Android 17 device/emulator test was performed. Before release, check on an
Android 17 device and an Android 8 device:

1. Start a recording, press Home and lock the screen: playback continues with
   its notification; pause from the notification and reopen the player.
2. Interrupt playback with another audio app or a call: it pauses, cleans up
   the notification and resumes only on an explicit Play action. Deny app
   notification permission and verify foreground playback behavior as well.
3. Rotate a text note and checklist with the keyboard open, then closed:
   verify keyboard visibility, focused field, cursor and edited content.
   Exercise split-screen resizing and the system back gesture.
4. Share images, audio, exported notes and diagnostic logs to another app;
   verify it can read the attachment. Exercise backup/restore, reminders and
   home-screen widgets.

## Branch relationship

`feature/target-sdk-37` was created from `ce0e45d` on
`fix/note-persistence-and-ci`. At the time of the migration, `origin/master`
was still `5838d63`; the five earlier local fixes had not been merged there.
Consequently a PR from this branch to that master includes those fixes too.
Merge the earlier branch first if separate reviews are preferred. All work is
local; the maintainer performs pushes and GitHub PR/merge operations.

## Official references

- [Android 17 changes for apps targeting 37](https://developer.android.com/about/versions/17/behavior-changes-17)
- [Android 17 changes for all apps](https://developer.android.com/about/versions/17/behavior-changes-all)
- [Background audio restrictions](https://developer.android.com/about/versions/17/changes/bg-audio)
- [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk)
- [AGP 9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
