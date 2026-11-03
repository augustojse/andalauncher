---
name: anda-emulator-check
description: Check a launcher change on the Android emulator, then shut the emulator down. Use whenever the user asks to "check it on the emulator", "install the debug build and look at ...", "see it visually", "run the instrumented/migration test", "switch the emulator to Russian / dark / big font", or when the emulator is hung, will not boot, or adb cannot see it. Not for the full weekly UX round (that is anda-ux-round). Holds the rules the user has repeatedly asked for: decide first whether a device is really needed, touch only the emulator serial, never share it with the QA run, and always kill it afterwards.
---

# Quick emulator check

The user dislikes idle emulators ("Do not run useless emulators"): they eat the machine, and a forgotten one blocks the 06:00 deep QA run. The emulator is also slow here (about 7 fps, 160 s to boot), so a device check costs minutes. Use it only when it buys something a JVM test cannot.

## 1. Decide whether a device is needed

Use `gradlew check` (see `anda-build-verify`) if the change is logic, settings, parsing or ranking. Boot an emulator only for: how something looks or behaves on screen, a database migration test (androidTest), permission or intent flows, or locale-dependent behaviour. Tell the user in one line why. If the check can wait for the release gate or the weekly UX round, say so.

## 2. Safety rules

- **Emulator only.** The user's Pixel is often connected over Wi-Fi adb. Every adb and Gradle call uses `ANDROID_SERIAL=emulator-NNNN` (or `adb -s emulator-NNNN`). Never run a bare `adb install` or `gradlew installDebug`.
- **Never alongside `run-qa.ps1`.** The deep tier wipes and restarts the emulator; sharing it corrupts the QA run and kills yours. Check first with `Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'run-qa\.ps1' }`. If it runs, wait or tell the user. Check the process's `CreationDate` first: one started days ago with no child processes is a stale leftover rather than a live QA run (see `anda-build-verify`, section 2); report it, do not kill it.
- **Leave nothing running.** The last step of every check is shutdown (section 5).

## 3. Get a ready emulator

For a visual check, reuse the project helper rather than hand-rolling adb:

```powershell
powershell -ExecutionPolicy Bypass -File tools\qa\ux.ps1 prepare            # boots if needed, installs debug build, factory-resets Anda, sets it as home
powershell -ExecutionPolicy Bypass -File tools\qa\ux.ps1 prepare -KeepState # keep current settings and layout
```

Other commands (`shot -Name X`, `find -Text T`, `tap -Text T [-Long]`, `logs -Name X`, `crop`, `finish`) are described in the header of `tools/qa/ux.ps1`. Read screenshots with the Read tool and report their path. The audit lines are leads, not findings. Treat odd-looking frames carefully: `.claude/skills/anda-ux-round/learnings.md` lists known emulator artifacts and `docs/qa/feature-test-guide.md` section 0 says how to verify a suspect.

Booting by hand (when `ux.ps1` is more than you need): `Start-Process "<sdk>\emulator\emulator.exe" -ArgumentList "-avd C1_Pixel_9_API_37 -no-window -no-audio -no-boot-anim -gpu host -no-snapshot" -WindowStyle Hidden`, then poll `adb -s emulator-5554 shell getprop sys.boot_completed` until `1`, with a timeout on every adb call. Expect about 160 s. The SDK path is `sdk.dir` in `local.properties`. In Git Bash set `MSYS_NO_PATHCONV=1` or paths like `/sdcard/...` get rewritten.

Instrumented tests: `$env:ANDROID_SERIAL='emulator-5554'; .\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.TestClass>"` (set `JAVA_HOME` first).

## 4. Known traps

- **Changing the system language.** The AVD images are Play Store builds, so `adb root` fails and `setprop persist.sys.locale` does nothing. Add the language in Settings → System → Languages by driving the UI with `ux.ps1 tap`, and make it the first language. Set it back or wipe afterwards.
- **Black or blank screen right after a switch or install.** Restart Anda's process (`am force-stop com.anda.launcher`), press Home, and look again before calling it an app bug.
- **After `input keyevent`, grey boxes appear** (Compose focus highlight). Tap empty space before the screenshot.
- **`adb emu kill` can hang**, and a hung emulator does not answer `getprop`. Run kill with a timeout (a background job and `Wait-Job -Timeout 15`). If it is still there, find the processes with `Get-CimInstance Win32_Process | Where-Object { $_.Name -like 'qemu*' -or $_.Name -eq 'emulator.exe' }` and `Stop-Process -Force` them. Restart it without `-wipe-data` unless a clean state is the point.
- **Jank and slow gestures are the emulator's GPU path**, not the app. Compare against a system app (`dumpsys gfxinfo com.android.settings`) before reporting.

## 5. Shut down and report

```powershell
& $adb -s emulator-5554 emu kill     # with a timeout, see above
```

Confirm with `adb devices` and the process query that no emulator or `qemu` process is left. Run `ux.ps1 finish` first if you used `prepare` (it restores font, density and theme and pulls crash reports).

Report: what you checked, the screenshot paths, what you saw, and that the emulator is stopped. If the check could not be completed (hung, timeout), say what is therefore still unverified.
