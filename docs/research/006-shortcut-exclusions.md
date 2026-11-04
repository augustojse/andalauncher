# Research 006: hide shortcuts from an app (B26)

_2026-11-03 · Anda 0.6.0 (`53c5d7e`) · Source: Kvaesitso 1.41.0 release notes (user-facing text only; no Kvaesitso source was read)_

**Question.** Can a user stop one app's shortcuts from appearing in search, without a performance cost and without a new permission?

**Finding.** Yes. `ShortcutSearchProvider` already keeps a cached list of every shortcut (`Entry` with its package) and rebuilds it only when
the shortcut set changes. A package filter applied *after* the cache costs one set lookup per entry per query, which is far below the
8 ms budget for 300 items. The setting is a list of package names, so a settings file written by 0.6.0 (which has no such key) decodes to an empty list.

**Decision.** Ship it as one step. Filter at query time; keep the cache untouched.

**Fallback.** If a query ever shows a measurable cost, move the filter into the cache rebuild and bump `shortcutsVersion` when the setting changes. Not expected.

## Feature intake: Hide shortcuts from an app

| Field | Value |
|---|---|
| Origin | Kvaesitso (1.41.0 release notes: choose apps whose shortcuts are left out of search) |
| User value | A few apps (browsers, messengers) expose dozens of shortcuts that crowd the result list. One long-press removes them. |
| Performance cost | none: a `Set<String>` lookup per shortcut at query time, on the provider's IO dispatcher |
| API risk | public API only; no permission, no INTERNET |
| Settings key | `LauncherSettings.search.excludedShortcutApps` (`List<String>` of package names) |
| Conflicts / merge | Overlaps with per-item visibility (B7). B7 will later offer the same choice per item; this list then becomes the default for shortcuts. The master `search.shortcuts` switch still turns every shortcut off. |
| Default | empty list (nothing hidden) |

## Behaviour

- Long-press a shortcut result: the existing menu gains **Hide shortcuts from <app name>** below **Pin to favorites**. Choosing it removes that app's shortcuts from the current results at once.
- **Settings → Search → Hidden shortcut apps** opens a screen listing the hidden apps (icon, name) with **Show again** on each row. An empty list shows a short hint explaining how to hide one.
- The row in Search settings shows the count ("2 apps"). It stays tappable while **App shortcuts** is off, so the list can still be managed (`NavRow` has no disabled state).
- A pinned shortcut (favorites) is not affected: favorites live in the drawer row, and the user chose that pin on purpose.

## Implementation sketch (paths in the Anda repo)

- `core/model/.../LauncherSettings.kt`: `SearchSettings.excludedShortcutApps: List<String> = emptyList()`.
- `core/model/.../LauncherSettings.kt` also holds `withShortcutsHidden` / `withShortcutsShown` (trim, ignore blanks and repeats, cap 200), so the long-press item and the settings screen share one tested rule.
- `core/model/.../SettingsSafety.kt`: cap at 200, drop blanks and duplicates when the list is written.
- `feature/search/.../ShortcutSearchProvider.kt`: skip excluded packages before scoring and before the 10-result cap, so hidden apps never take a slot.
- `feature/search/.../SearchPage.kt`: second menu item on `SearchResult.Shortcut`, using the same result-actions path as pinning.
- `feature/settings/.../SettingsIndex.kt` (`Screen.ShortcutApps`, parent `Search`, index entry), `SettingsRoot.kt` (route), `screens/ShortcutAppsScreen.kt` (new), `screens/SearchSettingsScreen.kt` (row).
- `CHANGELOG.md`: the `0.7.0` entry.

## Tests

- `SettingsSerializationTest`: a 0.6.0 settings file decodes with the list empty, and a list round-trips.
- `ShortcutExclusionTest` (new, `core/model`): hiding adds once and keeps order, blanks are ignored, the 200 cap, showing brings an app back, `sanitized()` cleans a messy list, a 0.6.0 file decodes to nothing hidden, round trip.
- `SettingsIndexTest` (existing): the new title is a real row. `ChangelogTest` (existing): version and notes.
- Emulator check: the long-press flow and the settings screen, once.
