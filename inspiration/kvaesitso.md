# Kvaesitso

| | |
|---|---|
| Upstream | https://github.com/MM2-0/Kvaesitso, docs at https://kvaesitso.mm20.de |
| Snapshot | `b72ddb6`, 2026-10-21 (`repos/kvaesitso`) |
| Licence | GPL-3.0. Treat it as no-copy. |
| Stack | Kotlin + Jetpack Compose throughout, Koin DI, Room, kotlinx.serialization. About 890 Kotlin files in about 50 Gradle modules. |

## What it is

A **search-first** launcher. Its home is a clock plus a column of widgets, with no icon grid.
Everything else comes from gestures and the search bar: apps, shortcuts, contacts, calendar,
files, calculator, unit converter, websites, Wikipedia and places.

It is the closest architectural relative of Anda (Compose and Kotlin from scratch), so it is the
better source for **structural** ideas. Lawnchair is the better source for Pixel-like home-screen
behaviour.

## Module layout

`settings.gradle.kts` sorts modules into four layers. Each layer may depend only on the layers before it:

| Layer | Modules | Role |
|---|---|---|
| `core/*` | base, ktx, preferences, permissions, profiles, crashreporter, i18n, devicepose, shared | Interfaces and models (`Searchable`, `LauncherIcon`, `Backupable`, `Plugin`), helpers |
| `data/*` | one per data source: applications, appshortcuts, contacts, calendar, files, calculator, unitconverter, currencies, websites, wikipedia, locations, weather, widgets, notifications, themes, customattrs, search-actions, searchable, plugins, database | Repositories, each implementing `SearchableRepository<T>` |
| `services/*` | search, icons, favorites, tags, badges, backup, music, widgets, global-actions, plugins, feed, accounts | Logic that combines repositories |
| `app/ui`, `app/app` | Compose UI, activities | Only UI |
| `libs/*`, `plugins/sdk` | Vendored Material color utilities, WebDAV, Nextcloud/ownCloud; the public plugin SDK | |

Anda's `core/*` + `feature/*` split (about 12 modules) is the right size for now. The layering
idea that matters is **one interface per kind of item and one repository per source** (A1, A2),
not the module count.

## Where each idea lives

Kotlin sources are under `src/main/java/de/mm20/launcher2/` in each module.

| Path (module → file) | What it does | Anda idea |
|---|---|---|
| `core/base` → `search/Searchable.kt`, `SavableSearchable.kt`, `SearchableSerializer.kt` | Every result type (app, shortcut, contact, event, file, website…) has a stable `key` and a `domain`, can override its label, launch itself and be serialised. That's why anything can be pinned, tagged or hidden. | A2 |
| `core/base` → `search/ResultScore.kt`, `search/fuzzy/FzfMatcher.kt` | Ranking: fzf-style literal score mapped into strong and weak bands, then a Jaro-Winkler typo band (≥ 0.8, queries of 3+ chars); secondary fields are scored lower | B9 |
| `core/base` → `backup/Backupable.kt` | `backup(toDir)` / `restore(fromDir)`, implemented by each repository | A3 |
| `core/base` → `FeatureFlags.kt`, `debug/DebugInformationDumper.kt` | Feature flags; a full debug-info export | A9, A10 |
| `core/base` → `com/android/launcher3/GestureNavContract.kt` (AOSP, Apache-2.0) + `app/ui` → `launcher/transitions/` | Gets the "swipe home" contract from SystemUI and replies with the app icon's bounds, so the closing app shrinks into its icon | **B1, C4** |
| `core/profiles` → `ProfileManager.kt` | Personal / work / **private space**: type from `LauncherApps.getLauncherUserInfo`, lock and unlock with `requestQuietModeEnabled`, hidden entry point on API 36 | **B3** |
| `services/search` → `SearchService.kt` | Runs every repository in its own coroutine (`supervisorScope`) and publishes a merged `SearchResults` after each one finishes. Filters say which categories run. | A1, B10 |
| `data/search-actions` | `TextClassifier` (phone, email, URL, date/time) → builders for call, message, email, open URL, set alarm, timer, schedule event, share, create contact, custom intent, custom web search with URL templates | **B4** |
| `data/customattrs` | One Room table for per-item custom label, custom icon and tags | A2, B8 |
| `services/favorites` | Pinned items + launch counts → "frequently used"; three visibility levels: Default / Search only / Hidden | B7 |
| `services/tags` | Tags on any item; search by tag | B8 |
| `services/icons` | Ordered icon providers (custom → icon pack → themed → dynamic calendar/clock → system → placeholder) + transformations (legacy → adaptive, forced themed) | A5, C11, C12 |
| `data/themes` → `ThemeBundle.kt` | One versioned JSON with name, author, colors, typography, shapes and transparencies; a legacy reader; import from a shared file (`ImportThemeActivity`) | C8, C9, C14 |
| `data/themes` → `typography/FontManager.kt` | Uses the device's headline and body fonts (`config_headlineFontFamily`, `config_bodyFontFamily`, which give Google Sans on Pixel) | C2 |
| `data/weather` → `breezy/` | Receives weather that the Breezy Weather app broadcasts (gzipped JSON); the launcher needs no network | B6 |
| `plugins/sdk`, `services/plugins` | Other APKs provide search, weather, calendar and contacts through content providers | B19 |
| `app/ui` → `launcher/scaffold/` | Gesture-driven scaffold: each gesture opens a "component" (search, widgets, notifications, recents, power menu, screen off, launch app). The page follows the finger with Push / Rubberband / Zoom-in animations and gives haptic feedback at the threshold. | C3 |
| `app/ui` → `launcher/widgets/clock/` | Clock faces (analog, binary, orbit, segment, two digital, custom) + "parts" (alarm, battery, date, music, favorites, Smartspacer) | C5 |
| `app/ui` → `launcher/widgets/` | Built-in widgets: notes, music, calendar, favorites, apps, weather | B18 |
| `app/ui` → `launcher/sheets/` | One bottom-sheet manager: customize item, edit favorites, tags, hidden items, widget picker, failed gesture | (Anda has `OverlayController`) |
| `docs/` | VitePress site: user guide (concepts, customization, search, widgets, integrations, troubleshooting, FAQ) + contributor guide | A11 |

## Not for Anda

- **Websites, Wikipedia, OSM places, currency rates, online weather providers, Nextcloud/ownCloud/WebDAV file search:** need INTERNET. They could come back through plugins (B19).
- **Koin DI:** ADR 0003 keeps manual DI.
- **A home with no icon grid:** Anda's home is a Nova-style grid. Kvaesitso's widget column could still be an optional extra page later.
- **About 50 modules:** more granular than Anda needs now.
