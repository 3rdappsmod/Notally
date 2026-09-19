# Verification — 2026-09-19

Tested source commit: `6199f2b` (base: `5838d63`). The following documentation commit does not change application or CI source.

Environment: Linux x86_64, Temurin JDK 25.0.4.1+1, Android SDK Platform 36, checked-in Gradle 9.7.1 wrapper.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --continue --console=plain
```

Result: BUILD SUCCESSFUL. 13 regression tests, zero failures:

- 9 Room/ViewModel tests: persisted reminder IDs, cancellation, distinct reminder-only notes, clearing existing text/checklists, stable-ID restoration into a new ViewModel, empty-draft cleanup, attachment/metadata-only notes and concurrent saves.
- 2 PDF tests: successful and failed writes, truncation, descriptor closure, adapter completion, partial-file removal and ignoring late completion.
- 2 navigation-inset tests: API 26 and API 30.

Both debug and optimized unsigned release APKs built. lintDebug completed with 65 existing warnings and 78 existing MissingTranslation errors matched by the checked-in baseline. All four NewApi errors discovered in NavigationView were fixed with WindowInsetsCompat, rather than baselined. No runtime API issues were added to the baseline.

`actionlint` 1.7.12 passed for both workflows; `git diff --check` passed. Action SHAs were resolved from the official v4 refs; the Gradle distribution SHA-256 was retrieved from services.gradle.org.

The first test runs exposed a test coroutine scheduler issue and use of inaccessible platform APIs in the new test; those were fixed before the successful run recorded above. The build also exposed the pre-existing API 30-only navigation code and untranslated resources described above.

Limitations: no physical-device or emulator UI/process-death test was executed. The restoration test creates a new ViewModel from an ID captured before saving; follow the device checks in CONTRIBUTING.md before release. The new GitHub workflow has been validated locally, but has not run remotely for this branch because push and PR creation are the user's responsibility.

Remote settings handoff: enable strict up-to-date checking for the existing required `build` check in the master ruleset. Keep zero mandatory reviews and patch/minor auto-merge as agreed. This setting is not applied by any commit in this branch.
