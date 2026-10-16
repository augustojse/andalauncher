# Anda Launcher — release hardening: testing, bug fixing, visual coherence

## Context
The prototype now runs as the home app on a Pixel 10 (Android 17). For consumers it needs to be
stable (no crashes, crash loops, stuck states or lost layouts) and visually coherent (one design
language, motion on every surface, readable on every wallpaper and font size, localisable).

What this plan is based on (2026-10-15):
- **Device evidence (debug build, about 20 min of use):**
  - No crashes, no ANRs, no StrictMode violations.
  - **Settings runs inside the home task:** the system logs `clear-task-stack` on every Home press, so Settings is thrown away and never shows in Recents.
  - Debug-build frames: jank 1.7%, p95 12 ms, p99 38 ms, PSS 135 MB (budgets: < 1%, 8.3 ms, < 120 MB). These must be re-measured on a **release** build before anything is concluded.
- **Code audits:** a stability audit found 27 risks, including 8 that can crash, crash-loop or freeze the launcher. A visual audit found about 400 hard-coded strings, 7 overlays without motion, two different folder designs and an incomplete custom colour scheme.

Rules for the work:
- Work goes in phases; each phase ends with the gate in the *Release gates* section.
- One fix per change, with a regression test wherever the logic is testable off-device.
- docs/RND.md still applies: main thread is sacred, every feature has a setting, no INTERNET permission.

---

## Phase 0 — Test infrastructure (do first; everything else is measured with it)
1. **Version control.** Install Git for Windows, `git init` and commit the current tree as the baseline. Every fix below is then one conventional commit (RND.md §3). The zip baseline is retired.
2. **Local crash capture.** We have no INTERNET permission, so crash reports stay on the device:
   - `CrashGuard.install()` (app/.../CrashGuard.kt) writes stack trace, build and device info to `filesDir/crashes/` (keep the last 10).
   - Settings → About gets "Share crash report": an Android share intent with the text. The user decides; nothing is sent automatically.
3. **Test devices.**
   - The Pixel 10 (wireless ADB already works).
   - An API 34 emulator and an API 37 emulator, used for the destructive instrumented tests.
   - A tablet or foldable emulator for the dual-panel layout.
   - Device settings to cycle through:

     | Setting | Values |
     |---|---|
     | Font scale | 1.0 / 1.3 / 2.0 |
     | Display size | Default / Largest |
     | Theme | Dark / Light |
     | Layout direction | RTL (pseudo-locale `ar-XB`) |
     | Profiles | Work profile (Test DPC), private space |
     | Language | Long-string pseudo-locale `en-XA` |
4. **Release-build measurement.** `./gradlew :app:assembleRelease`, then `:baselineprofile:connectedBenchmarkReleaseAndroidTest` on the Pixel. Record cold start, jank, p95 and PSS against the RND.md budgets in `docs/research/002-release-baseline.md`.
5. **Stress runs.**
   - `adb shell monkey -p com.anda.launcher --throttle 50 --pct-syskeys 0 -v 20000` on an emulator, with 5 different seeds.
   - Any crash or ANR found becomes a P0 bug.

## Phase 1 — P0 stability (crashes, crash loops, stuck UI)
1. **Stuck drag.** A drag freezes when the dragged item's composable is disposed (e.g. dragging out of drawer search results, dragging two pages away, the package removed mid-drag).
   - Make `DragController` own the gesture's end: in core/ui/.../gesture/ItemGestures.kt, wrap the long-press phase in `try/finally` and call `drag.cancel()` if it hasn't dropped. Also add a `DisposableEffect` in `DragLayer` that cancels on pointer loss.
   - `DrawerController.onDragOut` must not clear the query or scroll until the drop completes. Defer those steps to `DragController.dropCount` and `cancel`.
2. **No uncaught coroutine errors in app scope.**
   - Give `AppGraph.appScope` (app/.../AppGraph.kt) a `CoroutineExceptionHandler` that logs to the crash log instead of killing the process.
   - Wrap `autoBackupIfDue`, `onPackageEvent` and `AppsRepository.getActivityList` calls in `runCatching`.
   - Fix `CrashGuard` so a crash at any time within 2 min of a previous crash counts toward the loop, not only crashes in the first 10 s of the process.
3. **Settings sanitisation.**
   - Add `LauncherSettings.sanitized()` in core/model. It clamps every numeric field to the ranges the UI offers (columns 3–10, rows 3–12, dock 3–8, sizes 50–150, opacities 0–1, …).
   - Apply it in `SettingsRepository` on read and on update, and in backup import.
   - Make `LauncherJson` tolerate unknown `LauncherAction` subtypes and unknown `Gesture` keys: a custom serializer falls back to `None`, so a single unknown value no longer resets all settings.
   - Add unit tests to `SettingsSerializationTest`.
4. **Safe backup import** (core/data/.../BackupManager.kt).
   - Cap entry count and size; reject names containing `..`, `/` or empty.
   - Read into memory and validate the version and bundle **before** touching icons or the database.
   - Write auto-backups to a temporary file and rename atomically.
   - Every caller (`LauncherRoot` SafeModeBanner, `SettingsScreens` Backup) wraps import in try/catch and shows "Couldn't restore this backup".
   - Add a JVM unit test with corrupt, zip-bomb and wrong-version fixtures.
5. **Guard every external intent.**
   - Add a single `Context.startSafely(intent, fallback)` helper in core/ui. The existing `AppLauncher.startSafely` already does this for apps.
   - Use it for accessibility settings, notification-listener detail (fall back to the general listener settings), app details, `ACTION_APPWIDGET_BIND` (MainActivity, PinItemActivity), wallpaper, calendar and alarms.
6. **Icon pipeline thread-safety.**
   - In `IconRepository.appIcon`, read `pack` once into a local and remove the `pack!!`.
   - Make `IconPack.idCache` a `ConcurrentHashMap`.
   - Wrap third-party drawable rendering in `runCatching`, falling back to the system default icon, so one broken app can't crash every start.
7. **Settings in its own task** (found on the device). Give `SettingsActivity` a `taskAffinity` of `com.anda.launcher.settings` and launch it with `FLAG_ACTIVITY_NEW_TASK` from `MainActivity.openSettings`. It then survives Home presses and appears in Recents, as Nova's settings do.
8. **Database downgrade.** Use `fallbackToDestructiveMigrationOnDowngrade()`. The previous layout is restored from the newest auto-backup if the DB was wiped.

## Phase 2 — P1 stability (data integrity, widgets, config changes)
1. **Orphan and out-of-grid reconciliation** (core/data/.../LayoutMapper.kt, LayoutRepository).
   - Items whose screen or folder is gone move to the first free cell, or a new page.
   - After the grid shrinks, items that no longer fit are reflowed with `GridOccupancy.nearestVacant` instead of becoming unreachable.
   - This runs once on settings change and once on start, inside a transaction.
   - Add unit tests next to `GridOccupancyTest`.
2. **Widget id lifecycle.**
   - Add `WidgetHostManager.reconcile()` on start: compare `host.appWidgetIds` with the layout and delete ids that are not used.
   - Persist the pending bind or configure state in `SavedStateHandle`/prefs (HomeController `pendingWidget`, PinItemActivity `pendingWidgetId`).
   - Debounce the "Add" button.
   - Move the undo finalisation to `appScope` so it survives the activity.
3. **Stale gesture callbacks** (feature/home/.../HomeItemViews.kt). Hold callbacks in `rememberUpdatedState`; for widgets, read the item through a ref updated every composition rather than capturing it in the `AndroidView` factory.
4. **Infinite scroll with 2 pages.** Turn infinite scroll off when there are fewer than 3 views. Key `pageBounds` by composition slot, not by screen id. This stops lost drops and blank widget copies.
5. **Configuration changes handled in place:**
   - Key Glance's `SimpleDateFormat` on locale and time zone, and listen for `ACTION_TIMEZONE_CHANGED`/`ACTION_LOCALE_CHANGED`.
   - Read `densityDpi` per render, not once, in IconRepository and ShortcutRepository.
   - Add `fontWeightAdjust` to the manifest's `configChanges`.
   - Verify that dark mode, font scale, display size and fold/unfold all update live (manual matrix in Phase 6).
6. **Main thread.**
   - Move `getWallpaperColors`, `getAppWidgetInfo`, `autoBackups()` and `nextAlarmClock` off the main thread (`produceState` on Dispatchers.IO).
   - Turn StrictMode `detectDiskReads` on in debug and watch logcat during the Phase 6 checklist.
7. **Authentication.**
   - Add `onAuthenticationError` handling to `MainActivity.authenticate`.
   - If there is no screen lock, explain this and offer the lock-screen settings instead of failing silently.
8. **Smaller fixes:**
   - Search result keys use a stable id instead of `hashCode`.
   - Battery `scale == 0` guard.
   - Release `WidgetHostManager` listening per activity instance.
   - Generic toast messages instead of raw exception text.
   - Safe mode clears itself after a stable run.
   - Drawer-folder tiles must not start drags from inside a dialog window. This is fixed properly in Phase 3.2.

## Phase 3 — Visual coherence (one design language)
1. **Design tokens in core/ui/theme.** New `LauncherDimens.kt` and `LauncherMotion.kt`, so every surface uses the same values:

   | Token | Values |
   |---|---|
   | Gutters | screen 16 dp, row padding 16 × 12 dp, card padding 16 dp, section spacing 12 dp |
   | Elevation | menu/sheet shadow 6 dp; panels tonal 3 dp |
   | Scrim | one alpha, 0.32 |
   | Motion | `enter` spring (0.85 damping, MediumLow), `exit` 150 ms tween, `emphasized` for drawer and folder |
   | Icon sizes | menu 24 dp, list 40 dp, tile = `IconStyle.iconSize()` |

   Replace the literals the audit found:
   - 14 shape literals → `MaterialTheme.shapes.*`, or a new `iconShape()` derived from the icon-shape setting.
   - 12 `.sp` literals and the hand-built `TextStyle`s → typography roles, so the Font setting reaches Glance and folder titles.
2. **One folder design.**
   - Drawer folders reuse the home `FolderOverlay` panel (same surface, title, grid, animation). Remove the `DrawerFolderDialog` AlertDialog in feature/drawer/.../DrawerDialogs.kt.
   - Folder icons use the user's icon shape, not `CircleShape` (HomeItemViews.kt:189, AppDrawer.kt).
3. **Complete colour scheme.**
   - `seededScheme` (LauncherTheme.kt) fills every role: `surfaceContainerLowest/Low`, `outline`/`outlineVariant`, `inverse*`, `error*`, `onTertiary*`, `scrim`. Generate tones with the Material color utilities algorithm rather than HSL steps, so Custom and Wallpaper modes no longer show baseline purple.
   - Add a unit test that every role differs from the baseline when seeded.
4. **Contrast everywhere.**
   - Labels on a user-coloured dock, folder or drawer background pick `onColor` from the background's luminance (helper next to `toColorOr`).
   - Wallpaper dimming feeds `LocalWallpaperTone` (dimmed above 0.4 counts as dark).
   - Badge count text uses the chosen badge colour's contrasting colour.
   - Use one text-shadow spec for every on-wallpaper text.
5. **Motion on every overlay**, using the motion tokens:
   - Item menu: scale and fade from its anchor.
   - Widget picker: slide up.
   - Drop-target bar and edit-mode bar: fade and slide.
   - Welcome and safe-mode cards.
   - Folder close: reverse of open, so close becomes an animated state in HomeController instead of an instant null.
   - Page add and remove.
   - Settings screen navigation: `AnimatedContent` with a shared-axis transition.
   - Drawer tab switches: crossfade.
6. **Unify components.**
   - One search-field style for the home pill → drawer field → settings → widget picker, so the colour no longer jumps when tapped.
   - One `LauncherSheet` scaffold shared by CustomizeSheet and WidgetPicker.
   - All confirmation and destructive dialogs go through `OverlayController.confirm` (red action): drawer group delete, auto-backup restore, page remove.
   - One empty-state component, used for no search results, an empty widget filter, no hidden apps and an empty folder.
   - Customize sheet rows reuse the Settings row components, so both look the same.
   - Replace Glance emoji with Material icons.
7. **First impression and branding.**
   - A real app icon: an adaptive icon with a proper monochrome layer, a round icon and the 512 px store icon.
   - SplashScreen API (`core-splashscreen`) for SettingsActivity with a solid theme background, plus night theme variants in `values-night/themes.xml`.
   - Settings waits for stored settings (a small loading state) instead of flashing the defaults. Same for PinItemActivity.
   - The settings "Rounded" dock swatch uses the same shape as the real dock.

## Phase 4 — Consumer basics: localisation, accessibility, RTL
1. **Strings to resources.**
   - Move about 400 hard-coded strings into `strings.xml` per module, using `stringResource`/`pluralStringResource`. Do this module by module: settings → home → drawer → search → gestures → app.
   - `SettingsIndex` titles become string resource ids. `SettingsIndexTest` then checks the ids rather than parsing source text.
   - User-visible enum names go through label functions, not `name`/`humanize`.
   - Add a lint rule or a grep-based unit test that fails on a new `Text("…")` literal.
2. **Accessibility.**
   - Every actionable element is labelled: colour swatches, resize handles, fast scroller.
   - Touch targets are at least 48 dp: swatches, resize handles, folder gear, fast-scroll strip, Glance links.
   - Run Accessibility Scanner on home, drawer, folder, settings and Customize. TalkBack walk-through of the README critical flows using the existing custom actions.
3. **Large font and narrow width.**
   - Ellipsis or wrapping for Welcome chips, Customize segmented labels, the menu card (switch to `widthIn(min = 200.dp, max = 320.dp)`), the safe-mode banner, the undo bar, the Glance title and fast-scroll letters.
   - Verify at font scale 2.0 and the largest display size.
4. **RTL.**
   - `CellLayout` uses `placeRelative`.
   - Mirror the page transitions, line indicator, edge-scroll direction, fast-scroll bubble and resize handles when the layout direction is RTL.
   - Verify in pseudo-locale `ar-XB`.

## Phase 5 — Performance to budget (release build)
1. Measure first (Phase 0.4). Only optimise what misses a budget.
2. **Likely targets:**
   - **High input latency:** check `homeGestures` and the pager nesting.
   - **Slow UI thread:** the main-thread work listed in Phase 2.6.
   - **PSS:** the icon LRU size; the widget preview bitmaps in settings, which should be downsampled.
3. Check `app/build/compose_reports` for unstable params in HomeScreen, AppDrawer and LauncherPreviews.
4. Regenerate the baseline profile after Phases 3–4, since the UI code changed.
5. **Release APK size** (budget < 10 MB): verify that R8 strips the unused material-icons-extended classes; if not, replace them with a small vector set.

## Phase 6 — Testing program
**Automated (CI-ready, `./gradlew check` + `connectedDebugAndroidTest` on an emulator):**
- **New unit tests:**
  - Settings sanitising and unknown-value decoding.
  - Backup import fuzz cases.
  - LayoutMapper orphan and reflow handling.
  - Seeded colour scheme completeness.
  - `CrashGuard` loop detection.
  - `DrawerController` drag-out ordering.
- **New instrumented tests** (extend `LauncherHarness`):
  - Drag from drawer *search results* to home, which covers the stuck-drag regression.
  - Drag an icon across 2+ pages.
  - Remove → undo after the page was deleted.
  - Widget add → rotate during the bind dialog → no leaked id (`host.appWidgetIds` count).
  - Shrink the grid → every item stays reachable.
  - Settings survives a Home press.
  - Dark-mode toggle and font-scale change while home is visible, with no recreation and no crash.
- **Monkey runs:** 5 × 20k events per release candidate, with zero crashes and zero ANRs.

**Manual QA checklist** (`docs/qa-checklist.md`, run on each release candidate on the Pixel plus the device matrix):
- **First run and setup:**
  - Clean install, then welcome card, then set as default home.
  - Restore a backup from another device: widgets come back as removable placeholders.
- **Daily use:**
  - Home ↔ app return.
  - Drawer open, scroll and search.
  - Every gesture binding.
  - Folders: create, rename, reorder, remove.
  - Widgets: add, configure, resize, stack, remove, undo.
- **System events:**
  - Install, update and uninstall an app that is on home, in a folder and in the dock.
  - Pause and resume the work profile.
  - Change the wallpaper (light and dark).
  - Toggle dark mode, change font size and display size.
  - Change language, rotate, fold.
  - Reboot.
  - Low storage: auto-backup fails gracefully.
- **Settings:**
  - Every row changes something visible.
  - Previews match home.
  - Search finds every indexed row.
  - Reset all settings.
- **Accessibility:** TalkBack and Switch Access on the critical flows; 2.0 font scale.

**Dogfood and beta:**
- A 2-week dogfood on the developer's own phone with crash capture on: review `filesDir/crashes` weekly.
- Then a closed beta of 10–20 users (Play internal testing track, plus the direct APK for testers without Play) with the in-app "Share crash report" and a short feedback form link in About.

## Release gates (a phase is done when all apply)
- `./gradlew check` is green (lint warnings are errors) and every new test passes.
- The emulator instrumented suite is green on API 34 and API 37.
- Monkey 5 × 20k has zero crashes and zero ANRs; there are no unexplained StrictMode logs.
- Release-build budgets from RND.md are met:
  - cold start ≤ 350 ms
  - jank < 1 %
  - p95 ≤ 8.3 ms
  - PSS < 120 MB
  - APK < 10 MB
- The manual checklist passes on the Pixel and in the emulator matrix.
- For RC: dogfood finds no P0 or P1 bugs in the final week.

## Order and sizing
| Phase | Size | Can overlap with |
|---|---|---|
| 0 Infrastructure | S | — |
| 1 P0 stability | M | — |
| 2 P1 stability | M | 3 (different files mostly) |
| 3 Visual coherence | L | 2 |
| 4 Localisation/a11y/RTL | L | after 3 (touches the same UI) |
| 5 Performance | S–M | after 3–4 |
| 6 Testing program | ongoing | tests land with each fix |
| 7 Release packaging | M | start the Play paperwork (policy, declaration video) early; it takes review time |

## Phase 7 — Release packaging (decided: Google Play **and** a direct APK; English only for v1)
1. **Signing.**
   - Create an upload keystore. Keep it outside the repo; the password lives in `~/.gradle/gradle.properties`, never committed.
   - Add a `release` signingConfig, removing the debug-key fallback in app/build.gradle.kts.
   - Play uses App Signing with the upload key. The direct APK is signed with a separate release key, and that key must stay the same forever because APK updates depend on it.
2. **Two flavors from one codebase:** `play` (AAB) and `direct` (APK).
   - **Same app id?** No: if both channels are installed they'd clash. Use `com.anda.launcher` for Play and `com.anda.launcher.direct` for the APK; or ship only one id and document "don't mix" — decide at implementation.
   - **Update checks for the direct build:** none, because there is no INTERNET permission. Users update manually; About shows the version.
3. **Play requirements:**
   - A privacy policy page: no data collected, everything on the device.
   - A Data safety form: no data collected or shared.
   - The QUERY_ALL_PACKAGES declaration (the launcher core-functionality exemption).
   - **AccessibilityService:**
     - An in-app prominent disclosure dialog before opening accessibility settings. `requestAccessibility()` in MainActivity already has a dialog; reword it to Play's required wording and add an explicit "I agree".
     - A Play Console declaration and a short video showing the lock-screen and recents gestures.
     - Fallback: if Play rejects it, the `play` flavor drops the service and hides those actions. The setting reflects this, because every feature has a setting.
   - Store listing assets: the 512 px icon (Phase 3.7), screenshots from the preview-accurate home, and a feature graphic.
4. **Versioning.**
   - `versionCode` and `versionName` come from git tags.
   - `CHANGELOG.md` with each release.
   - The direct APK is published with its SHA-256.
5. **Languages.** English only for v1. Phase 4.1 still moves every string into resources, so adding a language later is translation-only. Verify layouts with pseudo-locales `en-XA` and `ar-XB`.
