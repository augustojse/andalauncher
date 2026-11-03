---
name: anda-build-verify
description: How to build, test and gate Anda Launcher (Kotlin/Compose Android, Gradle, Windows). Use whenever the user asks to run the checks, build, compile, lint, "gradlew check", "is it stable", "does it still pass", or when a Gradle, JAVA_HOME, lint or test failure shows up, even if they never say "gate". Covers the JAVA_HOME setting, avoiding file-lock clashes with other sessions, which gate to run for which change, and reporting what was and was not verified.
---

# Anda build and verify

Every session in this project builds with Gradle, and the same two things go wrong each time: Gradle cannot find a JDK, and two sessions build in one checkout and lock each other's files. This skill front-loads both, then says which gate fits the change.

## 1. Set up the shell

Gradle needs Android Studio's bundled JDK. Without it you get `JAVA_HOME is not set and no 'java' command could be found`.

- PowerShell: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`
- Git Bash: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`

Shell state does not persist between tool calls, so set it in the same command as `gradlew`.

Run Gradle as `.\gradlew.bat <tasks> --console=plain -q > <scratchpad>\gate.log 2>&1`. On failure, read the first `e: file:` line or the `What went wrong` block of the log, not the whole log; the rest is noise and costs tokens.

Builds take minutes. Start them in the background (`run_in_background`) and wait for the completion notice. Do not poll or sleep.

## 2. Pre-flight: is someone else building?

Other Claude sessions and the scheduled QA tasks share this checkout. Concurrent Gradle runs fail with `Unable to delete`, `being used by another process` or `Could not get file mode`, and a run that compiles while another session is mid-edit tests half-written code.

```powershell
Get-CimInstance Win32_Process | Where-Object {
    $_.ProcessId -ne $PID -and (
        ($_.Name -eq 'java.exe' -and $_.CommandLine -match 'GradleWrapperMain') -or
        ($_.Name -match 'powershell' -and $_.CommandLine -match 'run-qa\.ps1|nightly-push\.ps1'))
} | Select-Object ProcessId, Name, CreationDate, @{n='Cmd';e={$_.CommandLine.Substring(0, [Math]::Min(100, $_.CommandLine.Length))}}
```

No output means nobody is building. (A `GradleDaemon` java process alone does not count: idle daemons stay alive after every build. Matching on the wrapper client and the QA scripts avoids that false alarm, and `$PID` keeps the query from matching itself.)

Read `CreationDate` before waiting. A QA run takes at most about 60 minutes and a build a few, so an entry days old with no child processes (`Get-CimInstance Win32_Process | Where-Object ParentProcessId -eq <pid>`) is a stale leftover, not a live build. Do not kill it, because it is not yours: tell the user its PID and age and carry on. If an entry is young or has children, wait for it. If you get a lock error anyway, wait 5-10 minutes and retry once. For a long wait, build in a separate worktree: `git worktree add ../c1-launcher-ux <branch>`, copy `local.properties` into it, and pass `--max-workers=2`. Retrying for any other kind of failure only hides the bug.

## 3. Pick the gate (cheapest first)

| Gate | Command | When |
|---|---|---|
| Compile one module | `:feature:home:compileDebugKotlin` (swap in the module) | While iterating |
| Unit tests + lint | `.\gradlew.bat check` (about 3 min, lint warnings are errors) | Before every commit |
| Smoke / instrumented | `tools\qa\run-qa.ps1 -Tier smoke`, or `:app:connectedDebugAndroidTest` | The change needs a device. Hand over to `anda-emulator-check`; do not boot an emulator from here |
| Deep tier | `run-qa.ps1 -Tier deep` | Runs daily by schedule (45-60 min). Start it by hand only when asked |
| Macrobenchmark | `:baselineprofile:connectedBenchmarkReleaseAndroidTest` | Home, drawer or startup changed. Needs the Pixel, not the emulator |

Details and report formats are in `docs/TESTING.md`; quality gates per milestone are in `docs/RND.md` section 3.

Add the gates that match the change:
- **Settings field added or changed:** `SettingsSerializationTest` must show an old settings file decoding at defaults; `SettingsIndexTest` checks every index title is a real row.
- **Room entity or version change:** the schema JSON must exist in `core/data/schemas/` and the migration test (`LauncherDatabaseMigrationTest`, androidTest) must cover it.
- **CHANGELOG touched:** `ChangelogTest` enforces strictly increasing versions and non-empty notes.

## 4. Report what you actually verified

State each gate as passed, failed, or not run, with the reason. "Migration test compiles but was not run on a device" is a useful sentence; "tests pass" when only `check` ran is not. The user has been burned by the gap between the two: JVM tests do not prove the launcher works on a device.
