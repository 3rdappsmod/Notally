# Developing this fork

Use JDK 25 (including javac) and Android SDK Platform 37 (`platforms;android-37.0`). Set JAVA_HOME to the JDK 25 installation and ANDROID_HOME to the Android SDK, or set sdk.dir in an untracked local.properties. Use the checked-in Gradle wrapper; its distribution checksum is pinned. Java/Kotlin bytecode targets and the CI JDK are deliberately aligned at 25.

The build uses AGP's built-in Kotlin support and new Android DSL. Do not restore
`android.builtInKotlin=false`, `android.newDsl=false` or the separate
`org.jetbrains.kotlin.android` plugin. Kotlin Parcelize remains required for
attachment objects, and KSP remains required for Room code generation. The Java
toolchain selects JDK 25; Kotlin's JVM target follows Android compileOptions.
See [build migration notes](docs/builtin-kotlin-migration.md) for validation.

The SDK change from the fork's base was targetSdk 35 → 36 and minSdk 21 → 26. compileSdk was already 36. The Android 17 migration raises both compileSdk and targetSdk to 37; see [migration notes](docs/android-17-migration.md). Android 8.0 is the minimum supported OS.

## Verify a change

```sh
java -version
javac -version
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --stacktrace --continue
```

CI executes this same verification for every PR to master, including documentation PRs. The job ID remains `build`, matching the existing required check. Debug and unsigned, optimized release APKs are under app/build/outputs/apk. Test and lint reports are uploaded by CI.

The lint baseline contains only 78 pre-existing MissingTranslation findings from the reviewed source. It does not suppress API compatibility errors or future translation errors. Do not regenerate the baseline automatically on CI failures: inspect and fix new findings. Existing lint warnings remain visible.

For each local verification, record the commit SHA (`git rev-parse HEAD`), JDK version, exact command and outcome. A local release build at one commit does not validate future dependency updates; CI must validate each PR.

## Branch and merge policy

- All changes go through a branch and PR. Do not commit new work on master or push directly to master.
- Dependabot-authored patch/minor update PRs may auto-merge after all required checks pass. This includes build plugins as well as runtime libraries. Semver classification is the automation boundary, not a promise of compatibility.
- Major updates and human-authored PRs are manually merged by the maintainer. The workflow does not enable auto-merge for them.
- Zero mandatory approving reviews is intentional. Do not enable a blanket one-review requirement as a “fix”: it would change the agreed policy and impede a sole maintainer's own PRs.
- The coding assistant prepares local changes and commits. The user performs git push and all manual GitHub write operations (opening, merging, closing and commenting on PRs, changing settings). The explicitly permitted Dependabot automation is an exception for the bot, not authorization for the assistant to push or merge.

Before new work, fetch origin and check whether the current branch is already contained in origin/master. Start a fresh branch from up-to-date origin/master when needed. Preserve uncommitted work before switching; never assume a previously used branch still has an open PR.

Maintainer-managed GitHub settings (these cannot be encoded in a normal Git commit): keep the master ruleset active, require PRs and the GitHub Actions `build` check, prohibit force pushes and deletion, and enable “Require branches to be up to date before merging”. Check that bypass actors match the intended policy. Keep repository auto-merge enabled. If using a merge queue instead, this workflow also handles merge_group. These settings are a maintainer handoff; adding this document does not apply them remotely.

## Dependency updates

Dependabot checks Gradle and GitHub Actions weekly. Actions are pinned to commit SHAs and updated through PRs. Kotlin and KSP are grouped to validate compiler/plugin compatibility together; their version numbers no longer have to match. Glide uses version 5.0.9 through the version catalog. Its temporary major-update exclusion has been removed after the dedicated migration; future major updates are proposed as PRs and require manual review, like other major updates. See [Glide 5 migration](docs/glide-5-migration.md) for validation and device checks.

Fossify Notes inspired the catalog and CI structure. This fork's patch/minor auto-merge is an intentional addition, not an assumption that Fossify automatically merges dependency PRs. Fossify-specific Commons and Bundler workflows are not applicable here.

Dependabot does not merge upstream application code or migrate targetSdk/API usage. Upstream changes are selected and reviewed as separate PRs. Scheduled upstream synchronization is optional future work, not part of the current automation contract.

## Note persistence and regression coverage

An editor reserves its database ID before accepting edits. Saving and attachment/reminder mutations share a mutex. Alarms are scheduled only after the row and reminder are persisted. Existing notes retain deliberate clearing of their contents; a new empty draft is removed on explicit editor exit. Images, audio, reminders, labels, pinning or a non-default color count as meaningful content.

Background/rotation saving preserves a draft instead of deleting it. The saved instance state refers to the stable ID and retains whether the editor is a new draft. If Android kills the process before a normal exit, an unfinished draft may remain in the list; retaining recoverable work is preferred to deleting it. This does not claim that an asynchronous save survives arbitrary termination before the write completes.

Robolectric/Room regression tests cover alarm IDs/cancellation, cleared text and checklists, restoring an ID captured before saving into a new ViewModel, empty-draft cleanup, attachments/metadata and overlapping saves. This is not a substitute for a real device process-death test.

Before release, manually verify on Android 8 and Android 17:

1. New note → reminder → exit; verify delivery, cancellation and repeat behavior.
2. Clear an existing text note/checklist completely, exit and reopen.
3. Type a new note, background it, kill only the background process, and restore from Recents. Verify content and no duplicate row. Also rotate, restore an externally shared note without overwriting edits, and close an untouched new note.
4. Image-only/audio-only notes, attachment deletion, widgets and backup/restore of an existing database.
5. System Back with search, selection and active/paused recording; ordinary Back should use the system animation. Verify keyboard and system bar insets on each screen.
6. Optimized release UI, PDF export, image loading and relative-time text in Korean/English.

PDF export installs WebView callbacks before loading, reports main-frame load failures through the existing UI callback, ignores duplicate completion, closes file descriptors, finishes the print adapter and destroys the WebView. Failed partial PDFs are removed; subresource errors are logged without cancelling the entire export. Exported URLs are not included in the diagnostic log. Regression tests cover successful and failed writes and cleanup.

## Release identity

The applicationId is still com.omgodse.notally (debug adds .debug). Before publishing a new release, assign its versionCode/versionName and use the intended stable signing key. An APK signed with another key cannot replace the original app in place. Back up notes before a migration; do not uninstall the original as a build workaround without a verified backup. The current work does not publish a release or change signing credentials.
