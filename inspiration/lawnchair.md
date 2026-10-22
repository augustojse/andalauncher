# Lawnchair

| | |
|---|---|
| Upstream | https://github.com/LawnchairLauncher/lawnchair (branch for Lawnchair 16) |
| Snapshot | `c7e6b8d`, 2026-10-21 (`repos/lawnchair`) |
| Licence | Root `LICENSE.txt` is Apache-2.0 (Launcher3). 12 of 490 files in `lawnchair/src` carry GPL-3.0 headers. Treat it all as no-copy. |
| Stack | A fork of AOSP Launcher3 (Android 16): Java + Views at the core, with Kotlin and Compose for Lawnchair's own settings and features. Dagger DI. |
| Size | About 700 MB shallow. Lawnchair's own code is `lawnchair/src/app/lawnchair` (about 490 files); the rest is Launcher3, quickstep, SystemUI libraries and prebuilts. |

## What it is

A Launcher3 fork that ports Pixel Launcher features and adds customization. Because it *is*
Launcher3, it gets Launcher3's workspace, drag and drop, widgets and (with root, through
QuickSwitch) recents. Its value for Anda is the **Pixel-like behaviour and the long tail of
settings** that users of a Pixel-style launcher expect.

Anda is a Compose clean-room build (ADR 0001), so Lawnchair's base architecture is **not** a
model. Only its own feature modules are.

## Layout of Lawnchair's own code

All paths are under `repos/lawnchair/lawnchair/src/app/lawnchair/`.

| Package | What it does | Relevant to Anda |
|---|---|---|
| `preferences2/PreferenceManager2.kt` | One typed preference object (DataStore), about 150 keys | Key list = checklist of settings users expect (see improvements.md) |
| `ui/preferences/destinations/` | One file per settings screen (about 30 files) | Structure for splitting `SettingsScreens.kt` (A4) |
| `ui/preferences/components/{controls,layout,colorpreference,reorderable}` | Settings building blocks, foldered by role (see its `README.md`) | Same (A4) |
| `ui/preferences/components/{LauncherPreview,WallpaperPreview,GridOverridesPreview}.kt` | Live home preview inside settings | Anda already applies changes live; low priority |
| `smartspace/` + `smartspace/provider/` | At a Glance: data sources (battery, torch, now playing, onboarding, widget reader) merged into one stream | A6, B5 |
| `search/algorithms/engine/` | `SearchProvider` (Flow per provider), `SectionBuilder` (one per result section), `SearchPermission` | A1 |
| `search/algorithms/engine/provider/` | Calculator, contacts, files, history, settings, shortcuts, web | A1, B4 |
| `qsb/providers/` | About 25 search-bar providers (Google, DDG, Kagi, Firefox…): app intent first, website fallback | B4 (custom engines) |
| `predictions/UsageStatsRanker.kt` | Ranks apps from `UsageStatsManager`: several time windows, each scoring presence + foreground minutes + recency | B11 |
| `backup/NovaBackupConverter.kt` | Reads a Nova Launcher backup and converts it into a Lawnchair layout | B2 |
| `deck/LawndeckManager.kt` | "Lawndeck": snapshots the layout DB, puts every app on home in category folders, and restores the old layout when turned off | B12 |
| `flowerpot/` + `lawnchair/assets/flowerpot/` | App categories from per-category package lists shipped as assets | B13 (own data only) |
| `icons/` | Icon provider, shapes, icon-pack parsing, `picker/` (icon picker grouped by the pack's categories), `data/iconoverride/` (Room table: component → chosen icon) | C10 |
| `theme/color/` | `ColorStyle`: Tonal Spot, Spritz, Vibrant, Expressive, Rainbow, Fruit Salad, Content, Monochromatic; color tokens | C1 |
| `font/` | Font manager, variable font axes, Google Sans Flex, downloadable Google Fonts | C2 |
| `gestures/handlers/` + `SleepMode.kt` | Gesture actions; screen-off through Auto / Root / Accessibility / Device admin | B20 |
| `bugreport/` | Crash capture, share, and upload to a paste service | A9 (minus the upload) |
| `ui/preferences/about/` | About, changelog dialog, acknowledgements (OSS licences), nightly updater | B21 |
| `SearchBarInsetsHandler.kt` | Moves the drawer search bar in step with the keyboard animation | C6 |
| `flags/` (repo root), `ExperimentalFeaturesPreferences.kt`, `FeatureFlagsPreference.kt` | Feature flags and an experimental-features screen | A10 |

## Settings worth noting (from `PreferenceManager2`)

These are settings Lawnchair users expect and Anda doesn't have yet. Each is mapped in improvements.md:

- **Widgets:** `allowWidgetOverlap`, `forceWidgetResize`, `widgetUnlimitedSize`, `roundedWidgets` (B15).
- **Drawer:** `rememberPosition`, `appDrawerHapticFeedback` (B16).
- **Long-press menu:** `launcherPopupOrder` plus lock/edit/system-settings entries (B14).
- **Suggestions:** `predictionMode` (system / own / off) and dismissed prediction apps (B11).
- **Search:** separate result-count limits per category (`maxAppSearchResultCount`, `maxPeopleResultCount`, …) (A1).
- **Status bar:** `darkStatusBar` (C13).
- **Wallpaper:** `wallpaperDepthEffect` (C7).
- **Debug:** `showComponentNames`, a debug screen for developers (A10).

## Not for Anda

- **QuickSwitch / recents provider:** needs root (Magisk module).
- **Web suggestions, announcements, nightly updater, GitHub contributors, bug-report upload:** need the INTERNET permission, which v1 doesn't have. They could come back through a plugin (B19).
- **Feed bridge (Lawnfeed / Google Discover):** the Google app checks the caller's signature (research 001).
- **Launcher3 fork, Dagger DI:** conflict with ADR 0001 (Compose) and ADR 0003 (manual DI).
