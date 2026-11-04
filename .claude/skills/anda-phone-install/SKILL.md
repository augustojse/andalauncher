---
name: anda-phone-install
description: Install the latest Anda Launcher build on the user's physical Pixel over wireless debugging and verify it is really running. Use whenever the user says "install it on my phone", "push the newest version", "put the latest build on the phone", "wireless debugging", "re-pair", "phone not found", or "is the phone on the latest build", even if they do not name the script. Wraps scripts/install-to-phone.ps1, explains each failure, and never wipes the launcher's data without asking.
---

# Install on the phone

The phone is the only place the real feel (speed, wallpaper, notifications) can be judged, so the user installs there often. Wireless debugging drops and un-pairs regularly, which is why a script exists; use it instead of improvising adb commands.

## Before you run it

The script restarts the adb server when the phone is not found (second reconnect attempt). That cuts the adb connection of any QA run using the emulator. Check first that no `run-qa.ps1` or `nightly-push.ps1` is running (the pre-flight query in `anda-build-verify`, section 2) and wait if one is. The nightly starts around 23:09 and the deep run around 06:00.

Then run `adb devices` (one second). If no non-emulator device is listed, the script will spend about **five minutes** on discovery attempts and an adb restart before it fails with "Phone not reachable" (measured 2026-11-04). Unless the user has just said the phone is reconnected, do not start it: ask them to unlock the phone and switch Wireless debugging on, then run it. A listed `offline` entry is also not ready.

## Run

```powershell
powershell -ExecutionPolicy Bypass -File scripts\install-to-phone.ps1 -Launch
```

It finds a JDK, restarts adb, finds the non-emulator device (reconnecting through mDNS if needed), builds `:app:assembleDebug`, installs with `adb install -r -d` by serial, and checks that the SHA-256 of the installed `base.apk` equals the local APK. Parameters: `-Serial`, `-Launch`, `-SkipBuild`, `-ForceReinstall`. It takes a few minutes because of the Gradle build; run it in the background. If another session is building, wait (see `anda-build-verify`), since both would fight over the build folder.

Report the installed `versionName` and `lastUpdateTime` the script prints, and say the hash check passed. "Installed" without the hash check is not enough: the script exists to guarantee the phone runs exactly the latest build, and the hash is how it proves that.

## When it fails

| Output | Meaning and next step |
|---|---|
| `Phone not reachable` | Ask the user to unlock the phone, join the same Wi-Fi and switch Developer options → Wireless debugging on, then re-run. |
| `adb connect` fails instantly, or `SSLV3_ALERT_CERTIFICATE_UNKNOWN` in `%TEMP%\adb.log` | The phone forgot this PC. It must be re-paired: Developer options → Wireless debugging → Pair device with pairing code. The user pastes the pairing `ip:port` and the code here; run `adb pair <ip:port> <code>` using the adb from `local.properties`' `sdk.dir`, then re-run the script. Do not write the code anywhere (no file, memory, note or commit); it is one-time and short-lived. |
| `unauthorized` | The user must accept the debugging prompt on the phone. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or signature mismatch | The installed copy was signed with a different key. `-ForceReinstall` uninstalls first and **deletes the launcher's data (home layout, settings)**. Ask the user, and offer to have them take a backup in Settings first. Never add `-ForceReinstall` on your own. |
| Gradle failure | Nothing was installed. Treat it as a build problem (`anda-build-verify`). |

## Rules

- Target the phone by serial. Do not run bare `gradlew installDebug` or an adb command without `-s`: the emulator may also be attached.
- No `adb uninstall`, `pm clear`, or data wipes on the phone unless the user asks.
- **One phone can show up twice** in `adb devices`: once as `<ip>:<port>` and once as `adb-<serial>._adb-tls-connect._tcp`. `install-to-phone.ps1` takes the first ready entry and is fine. `nightly-push.ps1` counts both as phones, stops with "More than one phone" and pushes nothing. If the nightly's `qa-reports/nightly/latest.md` says that, the fix is on the user's side (`adb disconnect <ip>:<port>` leaves the mDNS entry), or the nightly needs `-Serial`; say so rather than editing `tools/release/`.
- **Success looks like** `Success`, then `versionName=<x.y.z>` and `lastUpdateTime=<now>`, then `OK: latest build is installed and verified on <serial>` (about 25 seconds when the build is up to date). The `monkey` launcher prints its arguments to stderr, which PowerShell shows as a red `NativeCommandError`; with `exit code: 0` that is noise.
- The 11 pm `tools/release/nightly-push.ps1` installs the **release** build with `adb install -r` after QA smoke passes. A debug install and the nightly build can differ in signature or version, so if the user reports the phone "changed overnight", look at `qa-reports/nightly/latest.md`.
- To read what the user wrote in Settings → Dev (notes and screenshots), `tools/dev/pull-dev-notes.ps1` pulls them from the phone into `dev-notes/`.

## Housekeeping

`scripts/install-to-phone.ps1` may still be untracked in git. If it is, mention it and offer to commit it on its own through `anda-commit-scoped`.
