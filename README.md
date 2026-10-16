# Anda Launcher

A fast, deeply customizable Android home screen for **Pixel 9 and newer** (Android 14+, minSdk 34).
It takes Nova Launcher's customization, Smart Launcher's automatic organization, and the best
ideas from Niagara, Kvaesitso, Action Launcher and Lawnchair, built clean-room in Kotlin + Jetpack Compose.

> R&D rules, performance budgets and quality gates: [docs/RND.md](docs/RND.md).
> Architecture decisions: [docs/adr](docs/adr). Platform research: [docs/research](docs/research).

## Features

| Area | What you get | Inspired by |
|---|---|---|
| Home grid | Columns × rows, **subgrid (half-cell) positioning**, padding, lock layout, infinite scroll, 7 scroll effects, wallpaper parallax, page indicator styles, two-panel pages on foldables | Nova, Pixel Fold |
| Dock | On/off, icons per page, **scrollable multi-page dock**, background shape/color/opacity | Nova |
| Search bar | Top / above dock / below dock / none, pill/rounded/square, voice + Lens buttons | Pixel |
| Icons | **ADW/Nova icon packs** (incl. iconback/mask/upon + dynamic calendar icons), 8 shapes, **themed icons for every app** (generated monochrome fallback), legacy-icon normalisation, per-area size/labels/font/color/shadow | Nova, Pixel, Lawnchair |
| Drawer | Vertical grid / horizontal pages / **list**, alphabet fast scroller, suggestions row, A–Z / install date / most-used sort, **custom tabs + automatic category tabs**, drawer folders, work tab, search at top or **bottom (one-hand)** | Nova, Smart Launcher, Niagara |
| Privacy | Hide apps, **lock apps behind fingerprint**, hidden apps behind biometrics, **no INTERNET permission** | Smart Launcher |
| Folders | Drop-to-create, smart default names, 4 preview styles, popup or full screen, **cover mode** (tap opens first app, swipe opens folder), reorder inside | Nova, Action Launcher |
| Widgets | Searchable picker, bind/configure/reconfigure, **resize handles**, **widget stacks**, pin requests from apps | Launcher3, iOS / Smart |
| Gestures | Swipe up/down, double tap, two-finger swipes, pinch in/out, home-button press, **per-icon swipe actions**; actions include drawer, search, notifications, quick settings, lock, recents, screenshot, power menu, open app/shortcut | Nova |
| Universal search | Apps (fuzzy + initials), app shortcuts, contacts, **calculator**, **unit converter**, Android settings pages, web hand-off | Kvaesitso, Niagara |
| Badges | Notification dots or counts | Nova |
| Theming | Material You, wallpaper-seeded, or custom accent; dark mode; fonts; wallpaper blur behind drawer/folders | Smart Launcher (ambient), Pixel |
| At a glance | Date, next alarm, charging/low battery, next calendar event (opt-in) | Pixel |
| Reliability | Versioned backup/restore (zip with custom icons), **daily automatic backups (last 7)**, **crash-loop safe mode** | — |

## Build

Requirements: Android Studio (2026.2+, bundled JDK) with SDK platform 37.

```bash
./gradlew :app:assembleDebug
```

```bash
./gradlew check
```

Install on a connected Pixel or emulator, then pick **Anda Launcher** as the home app
(Settings → Apps → Default apps → Home app, or the prompt in Anda settings → Permissions):

```bash
./gradlew :app:installDebug
```

Release builds are R8-minified and, for sideloading, signed with the debug key until a real
keystore is configured in `app/build.gradle.kts`.

## Instrumented tests

Drawer → home drag, folder creation and rename, widget add (through the system bind dialog),
drawer search, and Room migrations against `core/data/schemas`:

```bash
./gradlew connectedDebugAndroidTest
```

Run them on an **emulator**: the tests make Anda the home app and wipe its home layout and
settings before every test. They wake the device and dismiss the keyguard themselves
(`input keyevent KEYCODE_WAKEUP`, `wm dismiss-keyguard`), so the device must not have a PIN or
pattern lock.

## Performance work

```bash
./gradlew :app:generateReleaseBaselineProfile
```

```bash
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
```

Run both on a **physical Pixel 9** with Anda set as the default home app. The budgets are in docs/RND.md.

## Optional permissions (all opt-in)

| Feature | How to enable |
|---|---|
| Lock screen, recents, screenshot, power menu gestures | Anda settings → Permissions → Gesture service (accessibility, global actions only) |
| Notification dots | Anda settings → Permissions → Notification access |
| Contact search | Anda settings → Search → Contacts |
| Calendar in At a glance | Anda settings → At a glance → Next calendar event |

## Project layout

```
app/                 HOME activity, object graph, pin-request dialog, crash guard
core/model           Pure-Kotlin domain: items, grid solver, settings, actions, fuzzy matcher
core/data            Room layout DB, JSON settings DataStore, backups
core/apps            LauncherApps loader, shortcuts, profiles, categorizer, default layout
core/icons           Icon pipeline: shapes, themed icons, icon packs, memory + disk cache
core/ui              Theme, icon tile, drag & drop, gestures, menus
feature/home         Workspace, dock, folders, widgets, resize, glance, edit mode
feature/drawer       Drawer layouts, tabs, fast scroller, hidden/locked apps
feature/search       Providers, calculator, unit converter, search UI
feature/gestures     Home gestures, action dispatcher, accessibility service
feature/badges       Notification listener
feature/settings     Settings activity
baselineprofile/     Baseline profile generator + macrobenchmarks
```

## Known limits (see docs/research/001)

- Recents and gesture-navigation animations belong to Pixel Launcher. Third-party launchers get the system fallback animations.
- The Google Discover feed and Pixel's Smartspace aren't available to third-party launchers. Anda ships its own At a glance.
- Private Space needs privileged APIs, so it is still a research spike. Work profiles are fully supported.
