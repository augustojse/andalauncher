# Anda Launcher

Kotlin / Jetpack Compose Android home launcher for Pixel (package `com.anda.launcher`, minSdk 34). Modules: `app`, `core/{apps,data,icons,model,ui}`, `feature/{badges,drawer,gestures,home,search,settings}`, `baselineprofile`. Manual DI (ADR 0003); settings flow from one typed `LauncherSettings`. Standards: `docs/RND.md`. Tests and QA tiers: `docs/TESTING.md`.

## Environment
- Windows, PowerShell 5.1 (Git Bash also available). Gradle needs `JAVA_HOME = C:\Program Files\Android\Android Studio\jbr`; set it in the same command as `gradlew`.
- Other Claude sessions and the user edit this checkout at the same time. Do not run Gradle while another build or `tools\qa\run-qa.ps1` is running.

## Standing rules
- Never push, amend or force. Stage files by explicit path, never `git add -A`; leave the user's uncommitted edits alone.
- Every app change adds an entry to `CHANGELOG.md` (the build reads the version from it).
- Every feature has a setting with a safe default. No INTERNET permission. Main thread stays free.
- Clean-room: Lawnchair and Kvaesitso (in `inspiration/`) are GPL. Read behaviour, never copy code.
- Emulators: boot one only when a check needs a device, use the `emulator-NNNN` serial only, shut it down afterwards. The user's phone is never touched without an explicit request.

## Project skills (`.claude/skills/`)
- `anda-feature-step`: build one feature or release-plan step (setting checklist, CHANGELOG, tests).
- `anda-build-verify`: build, lint, test and pick the right gate.
- `anda-commit-scoped`: commit only this conversation's files in the shared checkout.
- `anda-emulator-check`: quick on-device check on the emulator, then shut it down.
- `anda-phone-install`: install and verify the latest build on the Pixel over wireless debugging.
- `anda-plan-release`: plan the next X.Y.0 from `inspiration/improvements.md`.
- `anda-ux-round`: weekly simulated-user UX round on the emulator.
