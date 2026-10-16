# Anda Launcher — next steps

Full plan: [release-plan.md](release-plan.md). Status as of 2026-10-15.

## Done (code)
- **Phase 0:**
  - Crash reports are saved on the phone. Settings → About → Problem reports lets you share or delete them.
  - The API 37 emulator is used for the destructive tests.
- **Phase 1:**
  - A drag can no longer get stuck on screen.
  - Background errors are logged instead of crashing the launcher.
  - Crash-loop detection now also catches launchers that crash after start-up.
  - Settings are clamped to valid ranges, and an unknown gesture no longer wipes all settings.
  - Backup import is validated before anything is changed, and automatic backups are written safely.
  - System-screen links are guarded, so a missing screen shows a message instead of crashing.
  - Icon rendering is thread-safe and survives broken icons.
  - Settings runs in its own task.
  - A downgraded install recovers from the newest backup.
- **Phase 1 status:** verification is in progress: the quality gate, then the emulator tests.

## Your end

1. **Git.** Install Git for Windows (https://git-scm.com/download/win), then in the project folder:

   ```bash
   git init
   ```

   ```bash
   git add -A
   ```

   ```bash
   git commit -m "chore: baseline after phase 1"
   ```

   `prototype-baseline.zip` (the original prototype) stays outside git; it's already in `.gitignore`.

2. **Android 14 emulator** (the lowest Android version Anda supports):
   - Android Studio → Tools → SDK Manager → SDK Platforms.
   - Tick *Android 14 (API 34) → Google APIs Intel x86_64 System Image*.
   - Device Manager → create a Pixel 9 device on that image, named `Anda_API_34`.

3. **Phone checks** on the new build. Tell me what fails.
   - **Drag from search:** open the drawer, search, then long-press a result and drag it onto home. It must drop, not freeze.
   - **Drag across pages:** drag an icon two pages away and drop it.
   - **Settings in Recents:** open Settings, press Home, then open Recents. Settings is still there with its own card.
   - **Customize:** long-press empty space → Customize. Drag the column slider and release. The real home changes.
   - **Crash reports:** Settings → About → Problem reports shows "No crashes recorded".

4. **Release key** (needed for Play and for the direct APK). Create it once and keep the file and passwords **outside** the project, with a backup copy. A lost key means users can't update the direct APK.

   ```bash
   "C:\Program Files\Android\Android Studio\jbr\bin\keytool" -genkeypair -v -keystore "%USERPROFILE%\keys\anda-release.jks" -alias anda -keyalg RSA -keysize 4096 -validity 10000
   ```

   Then add these lines to `%USERPROFILE%\.gradle\gradle.properties`. That file is never committed.

   ```
   ANDA_KEYSTORE=C:\\Users\\augus\\keys\\anda-release.jks
   ANDA_KEY_ALIAS=anda
   ANDA_STORE_PASSWORD=…
   ANDA_KEY_PASSWORD=…
   ```

5. **Play paperwork** (start now; it takes the most calendar time):
   - A Play Console developer account.
   - A one-page privacy policy: "Anda Launcher collects no data; everything stays on the device; no internet access".
   - The app name and app ids. Recommended: `com.anda.launcher` for Play and `com.anda.launcher.direct` for the APK, so the two never clash on one phone.

6. **If anything crashes:** Settings → About → Problem reports → Share. Send it to me, or paste it here.

## Next (my end)
1. Finish the Phase 1 verification:
   - `gradlew check`
   - the instrumented tests on the API 37 emulator, and on API 34 once step 2 above is done
   - monkey stress runs, 5 × 20k events, on the emulator only
2. Measure the release build on your Pixel against the budgets, written to `docs/research/002-release-baseline.md`. This replaces the debug build on the phone.
3. **Phase 2:**
   - unreachable items after the grid shrinks
   - widget id cleanup
   - stale gesture callbacks
   - config changes (time zone, display size)
   - slow work off the main thread
   - an error message when there is no screen lock
4. **Phase 3:** visual coherence (design tokens, one folder design, complete colours, motion on every overlay).
5. Once your key exists: the release signing config and the `play` / `direct` flavors (Phase 7).
