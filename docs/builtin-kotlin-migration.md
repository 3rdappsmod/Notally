# Built-in Kotlin and new Android DSL

This migration starts from master at `90da70c`. It retains AGP 9.4.1,
Gradle 9.7.1, Kotlin 2.4.20, KSP 2.3.12, JDK 25, compile/target SDK 37
and minSdk 26. It changes build configuration rather than application behavior.

## Changes

- Removed the separate `org.jetbrains.kotlin.android` plugin from both build
  files and the version catalog. AGP now supplies Kotlin compilation.
- Removed `android.builtInKotlin=false` and `android.newDsl=false`, using AGP's
  built-in Kotlin and new DSL defaults. No warning suppression was added.
- Retained the Kotlin Parcelize plugin and KSP. The catalog's Kotlin version
  remains in use by Parcelize; the build classpath resolves Kotlin Gradle plugin
  2.4.20 and KSP 2.3.12 rather than AGP's older minimum dependencies.
- Moved JDK selection to `java.toolchain.languageVersion = 25`. Both Android
  compileOptions targets remain 25, which also sets built-in Kotlin's JVM target.
- Moved Room's KSP schema argument into the top-level KSP extension.
- Replaced `packagingOptions` with `packaging`, Groovy property method-style
  assignments with `=`, and `buildDir` with `layout.buildDirectory`.

The existing automatic dependency PR/merge policy is unchanged. Future Kotlin,
KSP, AGP or Gradle updates must still pass the required build check; this is not
certification that an unreleased major toolchain version will work unchanged.

## Scope of warnings

The old Kotlin Android plugin, built-in Kotlin opt-out, new DSL opt-out,
legacy application/test/unit-test variant API and Groovy assignment deprecations
are addressed. Existing Android source API deprecations, unchecked casts,
manifest merger notices, lint findings and the JDK native-access notice from
Robolectric's Conscrypt dependency are separate and may still appear during a
clean build. They have not been hidden or added to the lint baseline.

## Official references

- [Built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [AGP 9.0 DSL and compatibility changes](https://developer.android.com/build/releases/agp-9-0-0-release-notes)

## Validation on 2026-09-20

- Clean build with `clean testDebugUnitTest lintDebug assembleDebug assembleRelease
  buildEnvironment --warning-mode all --continue`: successful.
- After adding Parcelize round-trip coverage, `testDebugUnitTest lintDebug
  assembleDebug assembleRelease --warning-mode all --continue`: successful,
  **53 tests, 0 failures, 0 skipped**. Image and Audio parcel round trips run
  on APIs 26 and 37; existing Room and UI regression tests also pass.
- `assembleRelease --warning-mode fail`: successful without Gradle deprecation
  warnings. A clean compile can still print the existing source-level warnings
  described above.
- `lintDebug`: 0 unbaselined errors, 60 warnings; the 78 existing translation
  baseline entries are unchanged.
- Generated classes are under `intermediates/built_in_kotlinc`; debug and
  release `Audio.class` have major version 69 (Java 25). Room implementation
  classes are generated and compiled in both variants.
- `actionlint` and `git diff --check`: successful.

The release APK is unsigned as before. No device test, signing, push or GitHub
write operation was performed. Existing user device-checklist files are left
untouched. The earlier replay-progress and checklist-focus device rechecks
remain pending; this build migration does not mark them complete.
