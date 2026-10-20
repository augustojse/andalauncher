# UX round learnings

Short, deduplicated notes that save the next round time. Newest first within each section.

## Environment and scheduling
- **Never share the emulator with `run-qa.ps1`.** The deep tier wipes and restarts it. Using it at the same time corrupted a deep run (2026-10-15: smoke "0 tests", monkey aborted, 100% jank), and the deep run in turn killed the UX round's emulator. `ux prepare` now waits for any running run-qa.ps1. Schedule rounds well clear of the 06:00 daily deep run, which takes about 60 min.
- A physical phone is usually connected over Wi-Fi adb (`adb-…_adb-tls-connect._tcp`). Bare `adb` or `gradlew installDebug` would hit it. Always go through ux.ps1, or set `ANDROID_SERIAL=emulator-5554`.
- Another Claude session may be editing tools/qa/run-qa.ps1 or docs/TESTING.md in the same checkout. Stage only this round's files (`git add <paths>`), never `git add -A`.
- Without a baseline reset, the emulator keeps settings from earlier manual sessions (seen: 9 columns, search bar above the dock). `ux prepare` runs `pm clear` unless `-KeepState` is given.

- **The emulator's GPU path (`-gpu host`) renders every app at about 7 fps on this PC**, with `GPU Context timeout` errors in `dumpsys gfxinfo`. Touch events then arrive 85–185 ms apart, so fling velocity reads 0 and short or fast swipes "fail". Before you report any gesture or jank issue, measure a system app as a control (`dumpsys gfxinfo com.android.settings` after a few swipes). If the system app is equally janky, the issue is EMULATOR.
- **`am start … SettingsActivity --es screen X` is ignored while Settings is already open** (it stays on the current page). Run `am force-stop com.anda.launcher` first, or navigate with `ux tap`.
- **Gradle work during the deep run:** use a separate worktree (`git worktree add ../c1-launcher-ux <branch>`, then copy local.properties) with `--max-workers=2`. The shared checkout's build folders are then never touched.

## Known false positives (do not report)
- **Audit `TOUCH`/`OVERLAP` on settings pages that have a live preview.** These nodes are the miniature's own text ("Friday, October 16"). The real issue behind them is UX-3 from 2026-10-16: the preview isn't hidden from accessibility.
- **Grey box behind the At a glance date, or behind other text, after `input keyevent`.** Key events put the device in non-touch mode, and Compose then draws focus highlights. Tap empty space (for example 540,1300) before screenshots that follow a key event.
- **Audit `OVERLAP 'Thursday, October 15' and '⚡ Charging…'`.** The Glance date's text box includes line-height padding, and the glyphs don't overlap.
- **Audit `NAVBAR '<View>'` on settings pages.** This is the scroll container, whose content scrolls under the gesture bar by design.
- **`STILL ANIMATING` on home.** The wallpaper or the Glance clock can keep the frame changing. Check that nothing in the app is mid-transition, then accept.
- **The settings preview header is white (`surfaceContainerLowest`) against the tinted page.** This is intentional token use. At most it is S4 polish.

## Unconfirmed leads to re-check
- **Search-pill spike.** Only seen with 9 columns and the search bar above the dock. Run with `-KeepState` after setting those, and zoom with `ux crop`.
- **Hard grey slab under the Welcome card** (case P-01). Compare it with a system dialog's shadow on the same emulator.
- A single tap on empty home opened the notification shade once (2026-10-15, right after Home and Back key events). It did not reproduce on the second attempt, so watch X-01.
- The keyboard was showing over home after a cold start of the round (the IME was attached to MainActivity's ComposeView). Possibly the drawer search kept focus from an earlier session, so try D-02 followed by Home.

## Reliable recipes
- Open a settings page directly: `am start -n com.anda.launcher/com.anda.launcher.feature.settings.SettingsActivity --es screen <Home|Dock|Drawer|Icons|Folders|Gestures|Search|Badges|Theme|Glance|Hidden|Permissions|Backup|About>`.
- Zoom into a small detail: `ux crop -Name <shot> -Rect "x,y,w,h"` (device px, enlarged 2x).
