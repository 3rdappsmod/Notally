# Glide 5 migration

This change starts from `origin/master` at `fe3df8e`, after the Glide 4.16.0
and Android 17 migrations were merged. `feature/glide-5` updates the version
catalog to Glide 5.0.9, keeping `implementation libs.glide` in app/build.gradle.

The downloaded Glide 5.0.9 AAR declares `minCompileSdk=37`. The project's
compileSdk 37 satisfies this requirement; targetSdk 37, minSdk 26 and JDK 25
are unchanged. Both `RequestListener<Drawable>` implementations in
`BaseNoteVH` and `PreviewImageVH` compile without changes. They continue to
load local files, crop thumbnails, cross-fade and report failed loads. Fullscreen
zoom uses the separate Subsampling Scale ImageView library and is unchanged.

The temporary Dependabot exclusion for Glide major versions has been removed.
Future major versions can be proposed as PRs but remain subject to manual review;
only patch/minor Dependabot updates qualify for existing auto-merge automation.

## Validation on 2026-09-19

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease \
  :app:dependencyInsight --dependency com.github.bumptech.glide \
  --configuration debugRuntimeClasspath --console=plain --continue
```

Results: **45 tests passed**, no failures or skipped tests; debug and optimized
unsigned release builds passed. `lintDebug` reported 0 unbaselined errors and
60 warnings; the existing 78 translation errors remain in the unchanged baseline.
Dependency insight selected Glide 5.0.9. `actionlint` and `git diff --check` passed.

The new `GlideFileLoadingTest` runs on API 26 and API 37. It checks successful
local PNG loading/cropping, missing-file failure and corrupt-file failure through
the actual Glide request engine. Robolectric supplies platform decoding; legacy
bitmap decoding is configured to reject invalid data rather than manufacture a
bitmap. These tests do not verify device-native codecs, animated-image timing,
view recycling or GPU rendering.

Before release, use the test APK on Android 8 and Android 17 to add JPEG/PNG
images, check list thumbnails and editor previews, scroll rapidly between notes,
rotate, open fullscreen zoom, delete attachments and restart the app. Check a
large image and an animated image if used. Repeat on a signed optimized release
APK. No device testing or release signing was performed as part of this change.

## References

- [Glide 5.0.9 release](https://github.com/bumptech/glide/releases/tag/v5.0.9)
- [Glide 5.0.9 RequestListener](https://github.com/bumptech/glide/blob/v5.0.9/library/src/main/java/com/bumptech/glide/request/RequestListener.java)
