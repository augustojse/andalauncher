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

Read `CreationDate` before waiting. A QA run takes at most about 60 minutes and a build a few, so an entry days old with no child processes (`Get-CimInstance Win32_Process | Where-Object ParentProcessId -eq <pid>`) is a stale leftover, not a live build. Do not kill it, because it is not yours: tell the user its PID and age and carry on. If an entry is young or has children, wait for it.

**Scheduled runs build this same working tree.** `nightly-push.ps1` starts around 23:09 and `run-qa.ps1 -Tier deep` around 06:00; each compiles whatever is on disk and holds the emulator, and its health gate fails if the machine is busy. While one runs, do not edit source files or `CHANGELOG.md`, do not start your own Gradle build (not even in a second worktree: the extra load fails the run), and do not touch adb. A half-written file can fail the run, and a new CHANGELOG heading can trip the nightly's version check. Docs, plans and skill files are safe to edit. Check the clock with `Get-Date` before starting a build late in the evening or early in the morning, start a background waiter on the run's process id so you are notified when it exits, and carry on with docs-only work meanwhile. These runs also leave an untracked `qa-nightly-run.log` in the repo root: it is not yours, so leave it out of your commits. If you get a lock error anyway, wait 5-10 minutes and retry once. If the wait is for another *session's* build and is long, a separate worktree works: `git worktree add ../c1-launcher-ux <new-branch>`, copy `local.properties` into it, and pass `--max-workers=2`. That does not apply to the scheduled runs described next. Retrying for any other kind of failure only hides the bug.

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

## 4. Confirm the tests really ran

With `-q` a passing `gradlew check` prints nothing, so an exit code of 0 and an empty log say little on their own. Check the result files for the tests the change depends on:

```bash
find . -path ./inspiration -prune -o -name "TEST-*.<TestClass>.xml" -mmin -30 -print
grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' <that file>
```

A file older than your run means the test did not run. Look for the new test class, plus the guard tests the change relies on (`SettingsSerializationTest`, `SettingsIndexTest`, `ChangelogTest`). `bc` is not installed in Git Bash; use PowerShell or `node` for sums.

## 5. Report what you actually verified

State each gate as passed, failed, or not run, with the reason. "Migration test compiles but was not run on a device" is a useful sentence; "tests pass" when only `check` ran is not. The user has been burned by the gap between the two: JVM tests do not prove the launcher works on a device.
