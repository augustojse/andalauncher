# Improvements from Lawnchair and Kvaesitso

Researched 2026-10-21 from the snapshots in [README.md](README.md). Each idea says what the other
launcher does, what Anda does today, and where the change would land. Paths into the other
projects are in [lawnchair.md](lawnchair.md) and [kvaesitso.md](kvaesitso.md).

**Before building any idea:**
- Write a feature intake card (`docs/research/feature-template.md`).
- Follow docs/RND.md: clean-room, every feature has a setting, main thread is sacred, no INTERNET permission.

| Key | Meaning |
|---|---|
| **P1** | Next up after the release-plan phase it fits. High value, low risk. |
| **P2** | Worth doing soon. |
| **P3** | Later, or needs a spike first. |
| **S / M / L** | Effort: ≤ 1 day / 2–5 days / more than a week. |
| **From** | LC = Lawnchair, KV = Kvaesitso. |

## Start here

The ten ideas with the best value for the effort:

1. **B1 Swipe-home lands on the app icon** (GestureNavContract). It removes a limit that research 001 accepted as permanent.
2. **B2 Import a Nova Launcher backup.** Nova users are Anda's target users, and this lets them switch in one step.
3. **B3 Private Space.** Kvaesitso shows that a non-system launcher can do it, so the spike is worth running now.
4. ✅ **B4 Search actions.** *Done in 0.3.0.* Call, text, email, alarm, timer and event straight from the query. Custom web engines too. All with outgoing intents only.
5. ✅ **A1 Streaming search providers.** *Done in 0.3.0.* One interface, one file per provider. Slow providers stop blocking fast ones. B4, B9 and B10 build on it.
6. **A4 Split the settings screens.** One file per screen, and components grouped into controls and layout.
7. **A7 + C1 Material color utilities and color styles.** Finishes release plan 3.3 and adds Tonal Spot, Vibrant, Expressive and the other styles.
8. **A6 + B5 Glance data sources, then Now playing.** Uses the notification listener Anda already has.
9. ✅ **B9 Typo-tolerant ranking.** *Done in 0.3.0.* "spotfy" finds Spotify.
10. **C3 Finger-following transitions.** The drawer and search follow the finger with a haptic tick at the threshold (release plan 3.5).

---

## A. Codebase structure

| ID | Idea | From | Anda today → change | Lands in | Effort | Priority |
|---|---|---|---|---|---|---|
| A1 | **Streaming search providers** ✅ 0.3.0 | KV `SearchService`, LC `SearchProvider` + `SectionBuilder` | Today `SearchEngine` calls every provider in `SearchProviders.kt` and returns once. Change: a `SearchProvider` interface (`id`, `search(query): Flow<List<SearchResult>>`), one file per provider, and an engine that runs them in a `supervisorScope` and publishes after each one. Apps show at once while contacts are still loading, and one provider failing doesn't break search. Add a per-category result limit and one section builder per result type. | `feature/search` | M | P1 |
| A2 | **One identity for every item** | KV `SavableSearchable` (`key` + `domain` + serializer) | Today `ComponentKey` covers apps only, and `app_overrides` holds a label and icon for apps only. Change: an `ItemKey(domain, id)` for apps, shortcuts, contacts and setting pages, so hiding, renaming, tagging, pinning and launch stats work for every type. This is needed for B7, B8 and pinned contacts. | `core/model`, `core/data` (Room migration + test) | M | P2 |
| A3 | **Each repository backs itself up** | KV `Backupable` | Today `BackupManager` knows every table and file. Change: a `Backupable` interface (`backup(dir)` / `restore(dir)`) on each repository, with `BackupManager` looping over a list. New data (tags, theme bundles, dev notes) then joins backups without touching `BackupManager`. Keep the current validation (size caps, version check) in one place. | `core/data` | S–M | P2 |
| A4 | **One file per settings screen** | LC `ui/preferences/destinations/*`, `components/{controls,layout}` + its README | Today `SettingsScreens.kt` is 1,175 lines and `SettingsComponents.kt` is 502. Change: `screens/HomeSettings.kt`, `screens/DrawerSettings.kt`, …, `components/controls/*` (switch, slider, list, color) and `components/layout/*` (group, scaffold, top bar). This makes release plan 3.6 (customize sheet reuses the settings rows) easier and keeps `SettingsIndex` simple. | `feature/settings` | S | P1 |
| A5 | **Icon provider chain** | KV `services/icons` providers + transformations | Today `IconRepository.appIcon` picks the icon source inline. Change: an ordered `IconProvider` list (custom → icon pack → dynamic calendar/clock → system → placeholder), then `IconTransformation`s (legacy → adaptive, themed, shape). Each step can be unit-tested, and C11 and C12 become one new provider each. | `core/icons` | M | P2 |
| A6 | **Glance as data sources** | LC `SmartspaceProvider` + `SmartspaceDataSource` | Today `Glance.kt` builds date, alarm, battery and calendar in one composable. Change: a `GlanceSource` interface (a Flow of cards, a setting key, a permission check, a "needs setup" card). The sources are combined and sorted by score. New cards (B5, B6, B17) become one file each. | `feature/home` (or a new `feature/glance`) | S–M | P1 |
| A7 | **Material color utilities** | KV vendors `libs/material-color-utilities`; LC uses Monet | Release plan 3.3 already asks for tones from the Material algorithm instead of HSL steps. Take the library from **Google's upstream** (Apache-2.0), as a dependency or a port with attribution, not from Kvaesitso. C1 needs it. | `core/ui/theme` | S | P1 |
| A8 | **Translation pipeline** | LC Crowdin, KV Weblate | After the release plan moves the ~400 hard-coded strings into resources, connect a translation service and pseudo-locale checks. | repo + CI | S | P3 |
| A9 | **Export debug info** | KV `DebugInformationDumper`, LC `bugreport/` | Today Problem reports shares crash traces. Add "Export debug info": version, device, the settings JSON, permission states, icon pack, item and widget counts, recent errors. The user shares it, nothing uploads (no INTERNET). | `core/data/CrashReports.kt`, Settings → About | S | P2 |
| A10 | **Feature flags + experimental screen** | LC `flags/`, `ExperimentalFeaturesPreferences`; KV `FeatureFlags.kt` | Half-finished features ship turned off behind a flag, with toggles in Settings → Dev. Add a "Show component names" debug option (LC). | `core/model`, `feature/settings` | S | P2 |
| A11 | **User guide** | KV `docs/` (VitePress: concepts, customization, search, widgets, troubleshooting, FAQ) | Today the docs are written for developers. Before Play, write a short user guide (it can be static pages) and link it from Settings → About. | `docs/user-guide/` | M | P3 |

---

## B. Features

| ID | Idea | From | What it does / Anda today | Lands in | Effort | Risk | Priority |
|---|---|---|---|---|---|---|---|
| B1 | **Swipe-home lands on the app icon** | KV `GestureNavContract` + `launcher/transitions/` (contract is AOSP, Apache-2.0) | Today research 001 says third-party homes only get the stock swipe-home animation. In fact, when the home is not the recents provider, SystemUI adds a `gesture_nav_contract_v1` bundle to the home intent (`onNewIntent`), carrying the closing app's component, user and a reply `Message`. The launcher replies with the icon's on-screen `RectF`, and the app window shrinks into that icon. Anda: in `MainActivity.onNewIntent`, look up the item (home page or dock, or open the drawer if needed), reply with its bounds and play a small landing animation (C4). | `app/MainActivity`, `feature/home` | M | Public intent extras, API 30+. **Spike on Pixel 10 / Android 17**, then update research 001. | **P1** |
| B2 | **Import a Nova Launcher backup** | LC `NovaBackupConverter` | A `.novabackup` is a zip holding `nova.db` and `nova.xml`. `nova.db` has a Launcher3-style `favorites` table: `container` −100 = desktop, −101 = dock, folder ids otherwise; `cellX/cellY/spanX/spanY/screen/itemType/intent/appWidgetProvider`. `nova.xml` is shared prefs: `desktop_grid` such as `6x5`, `dock_grid_cols`, `theme_icon_pack`. Show a summary first (grid, icon pack, counts of apps, folders, widgets and shortcuts), then import. Widgets need binding again; missing apps are skipped and listed. | `core/data` (new `NovaImporter`), Settings → Backup | M | File format only; use the same zip limits as `BackupReader` | **P1** |
| B3 | **Private Space** | KV `core/profiles/ProfileManager` | Kvaesitso, an ordinary F-Droid app, declares `ACCESS_HIDDEN_PROFILES`. On API 35 it finds the private profile with `LauncherApps.getLauncherUserInfo(user).userType == USER_TYPE_PROFILE_PRIVATE`, and it locks or unlocks it with `UserManager.requestQuietModeEnabled` (works only while it is the default home). On API 36 it respects `PRIVATE_SPACE_ENTRYPOINT_HIDDEN`. Research 001 assumed a privileged permission was needed, so run the M5 spike now. In the drawer, a private section at the bottom with a lock toggle. | `core/apps/UserResolver`, `feature/drawer` | M | Permission grant must be confirmed on device | **P1** |
| B4 | **Search actions from the query** ✅ 0.3.0 | KV `data/search-actions` (`TextClassifier` + builders), LC `qsb/providers` | Today web search is one `ACTION_WEB_SEARCH`. Change: read the query as a phone number (call, text), an email (compose), a URL (open), a time such as "7:30" (`AlarmClock.ACTION_SET_ALARM`), a duration such as "5 min" (`ACTION_SET_TIMER`) or a date (new calendar event). Any text can also be shared or saved as a contact. Add user-defined web engines with URL templates (`https://kagi.com/search?q=%s`) and custom intents. Each of these starts another app, so **no INTERNET permission**. | `feature/search` (needs A1), Settings → Search | M | Public intents | **P1** |
| B5 | **Now playing + torch in At a glance** | LC `NowPlayingProvider`, `TorchProvider` | Show the playing track (title, artist, app icon, tap to open) from active `MediaController`s. That needs the notification-listener access Anda already asks for badges. Show "Torch on" with a tap to turn it off (`CameraManager.registerTorchCallback`). Both opt-in. | `feature/home` glance (A6), `feature/badges` | S | Reuses an existing permission | P2 |
| B6 | **Weather without INTERNET** | KV `data/weather/breezy` | The Breezy Weather app broadcasts its forecast to launchers (gzipped JSON in a `WeatherGz` extra). Anda can listen and show temperature and conditions in Glance. It is opt-in and appears only if Breezy is installed. | glance (A6) | S | Third-party contract; check Breezy's docs | P3 |
| B7 | **Per-item visibility: Default / Search only / Hidden** | KV `VisibilityLevel` | Today `hiddenAppsInSearch` is one switch for all hidden apps. Change: a choice per item, so one app can be kept off the drawer but stay searchable. The global switch becomes the default for new hides. | `core/data` (A2), `feature/drawer` | S | — | P2 |
| B8 | **Tags** | KV `services/tags`, `customattrs` | Users tag apps, shortcuts and contacts. Typing `#work` searches by tag, and a drawer tab can be a tag. Today Anda has manual drawer tabs; by the conflict rule tabs become tag-backed and behave the same by default. | `core/data` (A2), `feature/drawer`, `feature/search` | M | — | P2 |
| B9 | **Typo-tolerant ranking** ✅ 0.3.0 | KV `ResultScore` (fzf literal bands + Jaro-Winkler) | Today `FuzzyMatcher` scores subsequences and initials. Add a third, lower band: for queries of 3+ characters, standard Jaro-Winkler similarity ≥ 0.8 against the label. Score secondary fields (package name, original label behind a custom one, tags) at a lower weight. Keep it under the 8 ms / 300 apps budget, with unit tests in `FuzzyMatcherTest`. | `core/model/FuzzyMatcher` | S | — | P2 |
| B10 | **Search filter chips** | KV `SearchFilters` + filter bar | Chips under the search field: All / Apps / Shortcuts / Contacts / Tools / Settings, plus a "Hidden" chip behind biometrics. Only the chosen providers run. | `feature/search` (A1) | S | — | P2 |
| B11 | **Better suggestions** | LC `UsageStatsRanker`, `PredictionMode`, dismissed predictions | (a) **Dismiss from suggestions** on long-press (S, P2). (b) An opt-in "use Android usage access" source. It scores several time windows by presence, foreground minutes and recency `1 / (1 + hours since last use)`, and is mixed with Anda's own launch stats. Research 001 chose own stats to avoid the permission; this stays off by default (M, P3). | `feature/drawer`, `core/data` | S / M | Special permission (b) | P2 / P3 |
| B12 | **Layout profiles** | LC "Lawndeck" `LawndeckManager` | Lawndeck snapshots the layout, puts every app on home in category folders, and restores the old layout when turned off. More general for Anda: named home layouts (Default, Minimal, Weekend) saved and switched with the existing backup code, plus a built-in "everything on home" layout using `AppCategorizer`. | `core/data`, Settings → Home | M | — | P3 |
| B13 | **Better automatic categories** | LC `flowerpot` (package lists per category) | Today `AppCategorizer` uses the manifest category plus package-name rules. Option: ship **our own** curated package → category list for the top apps that don't declare a category. Lawnchair's lists are not copied. | `core/apps` | M | Data upkeep | P3 |
| B14 | **Customizable long-press menu** | LC `launcherPopupOrder` + menu entry toggles | Users choose and order the entries in the item menu: shortcuts, app info, edit, remove, uninstall, plus "Lock home" and "Edit home" in the empty-space menu. | `core/ui/menu/ItemMenu` | S | — | P3 |
| B15 | **Widget freedom** | LC `allowWidgetOverlap`, `forceWidgetResize`, `widgetUnlimitedSize`, `roundedWidgets` | Settings to let widgets overlap, resize past the provider's limits and to any size, and clip widgets to the system widget corner radius (`system_app_widget_background_radius`). | `feature/home` (`ResizeFrame`, `WidgetHostManager`) | S | Some widgets misdraw when forced; label it "may break some widgets" | P2 |
| B16 | **Drawer polish** | LC `rememberPosition`, `appDrawerHapticFeedback` | Reopen the drawer where the user left it (for a short time). Haptic ticks while the fast scroller moves between letters. | `feature/drawer` | S | — | P3 |
| B17 | **Smartspacer integration** | LC `SmartspacerView`, KV `SmartspacerPartProvider` | Smartspacer is a separate app that offers Pixel-style glance cards (commute, package tracking…) to launchers through its SDK. Anda would show its targets as a glance source. Check the SDK licence first. | glance (A6) | M | Third-party SDK | P3 |
| B18 | **Built-in widgets** | KV `data/widgets` (notes, music, calendar, favorites, apps) | Compose widgets that need no `AppWidgetHost`: a notes card, a favorites grid, a music card. They are lighter than real widgets and themed with Anda. | `feature/home` | M | — | P3 |
| B19 | **Plugin SDK for network features** | KV `plugins/sdk`, `services/plugins` | Separate APKs provide search, weather or calendar data through content providers. Anda keeps no INTERNET permission, while an optional "Anda Web" plugin could add web suggestions, Wikipedia or online weather. Needs an ADR. | new `core/plugins` | L | Security review of the IPC surface | P3 |
| B20 | **Screen-off fallbacks** | LC `SleepMode` (Auto / Root / Accessibility / Device admin) | Today screen-off needs the accessibility service. A Device admin fallback (`DevicePolicyManager.lockNow`) works without it, but forces PIN entry on the next unlock (no biometrics), so say so in the setting. Skip root. | `feature/gestures` | S | Device admin permission | P3 |
| B21 | **Open source licences screen** | LC `about/acknowledgements`, KV `licenses/` | Settings → About lists the libraries Anda ships and their licences. Needed before Play. | `feature/settings` | S | — | P2 |

---

## C. Aesthetics

| ID | Idea | From | What it does / Anda today | Lands in | Effort | Priority |
|---|---|---|---|---|---|---|
| C1 | **Color styles** | LC `ColorStyle` | On top of A7, let the user pick how the scheme is built from the seed: **Tonal Spot** (default, as Pixel), Neutral/Spritz, Vibrant, Expressive, Rainbow, Fruit Salad, Content, Monochrome. Show a row of preview swatches, as Pixel's wallpaper picker does. | `core/ui/theme`, `ThemeSettings` | S | P1 |
| C2 | **Pixel's own fonts** | KV `FontManager`, LC `font/` | Today the "System" font is plain Roboto/sans. Change: read `config_headlineFontFamily` / `config_bodyFontFamily` so headlines and labels use Google Sans on Pixel, as Pixel Launcher does. Later: variable-font weight and width (LC `FontAxes`), and downloadable Google Fonts through the Play services font provider (check in a spike that this needs no INTERNET permission in Anda). | `core/ui/theme`, `FontChoice` | S | P2 |
| C3 | **Finger-following transitions** | KV `launcher/scaffold/` (Push / Rubberband / Zoom-in controllers, haptic at the threshold) | Swiping up for the drawer or down for search should follow the finger and settle with a spring. The home page pushes or zooms back behind it, it rubber-bands when no action is bound, and there is a haptic tick at the commit point (`HapticFeedbackType.GestureThresholdActivate`). Extends release plan 3.5 (motion on every overlay). Read the offsets in `graphicsLayer {}` only (RND.md). | `feature/gestures/HomeGestures`, `feature/drawer` | M | P2 |
| C4 | **Landing animation on return home** | KV `EnterHomeTransition*` | Goes with B1: the icon the app closes into gets a small scale-in. Kvaesitso also returns to the home page when the launcher was away for more than 5 s, and otherwise to the page the user left. | `feature/home` | S | P1 (with B1) |
| C5 | **Clock faces for At a glance** | KV `widgets/clock/clocks` | Optional large clock styles above the glance line: digital (two styles), analog, segment, orbit, binary. Each uses the theme colors and the C2 fonts. | glance (A6) | M | P3 |
| C6 | **Search bar rides the keyboard** | LC `SearchBarInsetsHandler` | With the drawer search at the bottom (one-hand mode), the bar should move up in step with the keyboard animation (`WindowInsetsAnimation`, or `Modifier.imePadding()` in Compose), not jump. Check what Anda does now first. | `feature/drawer` | S | P2 |
| C7 | **Wallpaper depth** | LC `wallpaperDepthEffect` | Pixel zooms the wallpaper out slightly when the drawer or a folder opens. The zoom API Launcher3 uses may be hidden, so spike it first. The fallback is Anda's existing blur. | `core/ui/theme/Wallpaper` | S (spike) | P3 |
| C8 | **Transparency tokens** | KV `Transparencies` (background / surface / elevated surface) | Today dock, drawer and folder each have their own opacity setting. Change: three alpha tokens in the design-token work of release plan 3.1. The per-surface settings stay as overrides. | `core/ui/theme` (`LauncherDimens`) | S | P2 |
| C9 | **Shape tokens** | KV `Shapes` (per-component corner style + radius) | Extends `ThemeSettings.cornerRadiusDp` to small / medium / large / extra-large, with rounded or cut corners. Sheets, menus, folders and search follow the tokens (release plan 3.1). | `core/ui/theme` | S | P3 |
| C10 | **Icon picker by category** | LC `icons/picker` (`IconPickerCategory`) | Today the custom-icon picker shows `packDrawableNames()` as one flat sorted list. Change: group it by the categories in the pack's `drawable.xml`, add search, and put "suggested" first (names that match the app). | `core/icons/IconPack`, picker UI | S | P2 |
| C11 | **Live clock icon** | KV `DynamicClockIconProvider` | Pixel's Clock app icon shows the current time. Read the clock app's hour/minute layer metadata, the same way Launcher3 does (AOSP). | `core/icons` (A5) | M | P3 |
| C12 | **Text and emoji icons** | KV `CustomTextIconProvider` | A custom icon can be a letter or an emoji drawn on the user's icon shape and theme color. It's good for minimal setups and web shortcuts. | `core/icons` (A5) | S | P3 |
| C13 | **Status bar follows the wallpaper** | LC `darkStatusBar` | Turn on dark status-bar icons automatically over light wallpaper (from `LocalWallpaperTone`, release plan 3.4), with a manual override. | `app/MainActivity` | S | P3 |
| C14 | **Shareable theme bundles** | KV `ThemeBundle` + `ImportThemeActivity` | Export the look (colors, color style, fonts, shapes, transparencies, icon shape and pack name) as one versioned JSON file and import it from a share or file open. It's a lighter "share my setup" than a full backup. | `core/data`, `feature/settings` | M | P3 |

---

## Where the ideas fit the release plan

| Release-plan phase | Ideas that belong there |
|---|---|
| Phase 3.1 design tokens | C8, C9 |
| Phase 3.3 complete colour scheme | A7, C1 |
| Phase 3.5 motion on every overlay | C3, C4 |
| Phase 3.6 unify components | A4 |
| Phase 3.7 first impression | C2 |
| Before Play (Phase 7) | B21, A9, A11 |

---

## D. Workflow skills

Project tooling, not launcher features: Claude skills in `.claude/skills/` for jobs the project keeps repeating. Each row names
the evidence from earlier sessions. The six skills that exist today are `anda-feature-step`, `anda-build-verify`,
`anda-commit-scoped`, `anda-emulator-check`, `anda-phone-install`, `anda-plan-release` (plus the weekly `anda-ux-round`).

| ID | Skill | Evidence | What it would do | Effort | Priority |
|---|---|---|---|---|---|
| S1 | **`anda-research-spike`** | RND.md §1.3; the 0.6.0 plan queues spikes B1 and B3; research 001 and 003 were written by hand | Run a half-day spike and write `docs/research/NNN-*.md` with question, findings, decision and fallback. Pick the next free number (002, 004 and 005 are reserved). Start with a measurement and a go/no-go number, as the 0.6.0 transliteration step did. | S | P1 |
| S2 | **`anda-release-cut`** | Release plan Phase 7 (Play and a direct APK); `nightly-push.ps1` already builds `assembleRelease` | Check the CHANGELOG heading and date, run `lint assembleRelease`, check the APK size budget, run the upgrade test (previous version, then the new one over it), and walk the signing checklist (keystore outside the project, never committed). | M | P1 |
| S3 | **`anda-qa-triage`** | Daily and nightly scheduled tasks; two sessions asked "Check nightly-push result and report" three times; on 2026-11-03 the nightly sat 44 minutes (and the night before about 19 hours) on a hung 24-hour-old emulator | Read `qa-reports/` (`latest-*.md`, `nightly/latest.md`, `history.csv`), classify a failure as app, test or emulator artifact, and report in five lines. Also flag a stale `nightly-push` process and unignored logs like `qa-nightly-run.log`. Recognise a stuck run: a QA process with no children, or an `adb ... getprop sys.boot_completed` call that is minutes old, means a hung emulator; the fix is `anda-emulator-check`'s kill recipe, done only with the user's OK. | S | P1 |
| S4 | **`anda-benchmark-pixel`** | RND.md budgets are measured on the Pixel; the 0.6.0 gate asks for a macrobenchmark because the drawer changed, and none has run since favorites | Run `:baselineprofile:connectedBenchmarkReleaseAndroidTest` on the phone through `anda-phone-install`'s connection, and compare cold start, drawer open p95 and idle PSS with the budgets. | M | P2 |
| S5 | **`anda-backlog-sync`** | B22 to B27 still read "Planned for 0.6.0" in this file after they shipped; the check marks are set by hand | When a CHANGELOG entry ships, tick the matching ideas here with the version, update the plan's "After" table, and keep research notes and plans in step. | S | P2 |
| S6 | **`anda-dev-notes-triage`** | Settings → Dev and `tools/dev/pull-dev-notes.ps1` exist for exactly this; no session has used them yet | Pull the notes from the phone, read each note and screenshot, reproduce on the emulator, and turn confirmed ones into fixes. Build it after the first real notes arrive. | S | P3 |
| Research spikes (`docs/research/NNN-*.md`) | B1, B3, C7, B17, B19 (skill: S1) |
| Before Play (Phase 7), tooling | S2, S4 |
| Daily upkeep, tooling | S3, S5, S6 |

## Not taking

| Idea | From | Why |
|---|---|---|
| Web suggestions, Wikipedia, places (OSM), currency rates, online weather, cloud file search | KV, LC | Need INTERNET. They could return through B19 plugins. |
| Nightly updater, announcements, contributor list, bug-report upload | LC | Need INTERNET; updates come through Play and the direct APK. |
| QuickSwitch (Anda as the recents provider) | LC | Needs root. |
| Google Discover feed bridge | LC | The Google app checks the caller's signature (research 001). |
| Fork Launcher3 | LC | ADR 0001: Compose clean-room. |
| Koin / Dagger DI | KV / LC | ADR 0003: manual DI until about 30 bindings. |
| About 50 Gradle modules | KV | More than Anda needs. Split `feature/search` into provider modules only if it grows past about 15 providers. |
| Copying Flowerpot category lists | LC | Licensed data. B13 builds our own list. |
