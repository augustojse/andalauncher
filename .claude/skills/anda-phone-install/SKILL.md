---
name: anda-phone-install
description: Install the latest Anda Launcher build on the user's physical Pixel over wireless debugging and verify it is really running. Use whenever the user says "install it on my phone", "push the newest version", "put the latest build on the phone", "wireless debugging", "re-pair", "phone not found", or "is the phone on the latest build", even if they do not name the script. Wraps scripts/install-to-phone.ps1, explains each failure, and never wipes the launcher's data without asking.
---

# Install on the phone

The phone is the only place the real feel (speed, wallpaper, notifications) can be judged, so the user installs there often. Wireless debugging drops and un-pairs regularly, which is why a script exists; use it instead of improvising adb commands.

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
- The 11 pm `tools/release/nightly-push.ps1` installs the **release** build with `adb install -r` after QA smoke passes. A debug install and the nightly build can differ in signature or version, so if the user reports the phone "changed overnight", look at `qa-reports/nightly/latest.md`.
- To read what the user wrote in Settings → Dev (notes and screenshots), `tools/dev/pull-dev-notes.ps1` pulls them from the phone into `dev-notes/`.

## Housekeeping

`scripts/install-to-phone.ps1` may still be untracked in git. If it is, mention it and offer to commit it on its own through `anda-commit-scoped`.
