# New setting / screen / table checklist

Traced from the 0.6.0 commits: `657e8c8` (contact actions), `8484d81` (calendar events), `c528be8` (quick-action editor), `53c5d7e` (favorites, with a database table). Paths are relative to the repo root.

## A. A new setting (the common case)

1. **Model.** Add the field with its default to `core/model/src/main/kotlin/com/anda/launcher/core/model/LauncherSettings.kt`, inside the right group (for example `SearchSettings`). The default must be safe: a settings file written by the previous version has no such key and must decode to this default.
2. **Serialization test.** Add one assertion to `core/model/src/test/.../SettingsSerializationTest.kt` that an old file decodes with the new key at its default (`assertEquals(true, decoded.search.contactActions)`).
3. **Screen row.** In `feature/settings/src/main/kotlin/com/anda/launcher/feature/settings/screens/<Group>SettingsScreen.kt`, add a row bound to the field:
   ```kotlin
   SwitchRow("Call and message buttons", b.bind({ it.search.contactActions }) { copy(search = search.copy(contactActions = it)) }, "On contact results with a phone number")
   ```
   The first argument is the row title. Grey out dependent rows with `enabled = <parent setting>`.
4. **Settings search index.** Add `IndexEntry("<same title>", Screen.<Group>, "keywords the user might type")` to `feature/settings/.../SettingsIndex.kt`. `SettingsIndexTest` fails if the title is not a quoted string in a `*Screen.kt` file, or if two entries share a screen and title.
5. **Safety.** If the value is bounded, comes from the user as free text, or is a list that could grow, clamp or validate it in `core/model/.../SettingsSafety.kt`. Unknown enum names from a newer version must be skipped, not allowed to fail the whole settings file (a failed decode resets every setting).
6. **Permissions.** If turning it on needs a runtime permission, ask when the switch is turned on (see the Contacts and Calendar rows), and do not add a permission to the manifest unless the feature truly needs it. Never add INTERNET.
7. **CHANGELOG** bullet and **gate** (see SKILL.md).

## B. A new settings screen

Do A, plus:
- `screens/<Name>Screen.kt`, one file per screen.
- A new entry in the `Screen` enum in `SettingsIndex.kt`. A sub-screen also gets a `parent` there, so Back returns to the page it was opened from (`QuickActions -> Search`).
- A branch for it in the `when (screen)` in `SettingsRoot.kt`, and a row or button on the parent screen that calls `go(Screen.<Name>, null)`. The parent screen gets a new callback parameter for it (`SearchSettingsScreen(b, onQuickActions, onShortcutApps)`), and `SettingsRoot.kt` passes it by name. Use a `NavRow(title, summary) { ... }` for the link; it has no disabled state.
- Index entries pointing at the new screen. All screens live in the single `SettingsActivity`; no new Activity is needed.
- The manifest changes only for package visibility: to list other apps (as "Search in an app" did) add a `<queries>` entry in `feature/settings/src/main/AndroidManifest.xml`.

## C. New persisted data (a table)

1. Entity in `core/data/.../db/Entities.kt`, DAO in `Daos.kt`, repository class in `core/data/`.
2. Bump the version in `LauncherDatabase.kt` and add the migration. Build once so Room exports the new schema file (`core/data/schemas/<database class>/N.json`, next to `1.json` and `2.json`); commit that file.
3. Migration test in `core/data/src/androidTest/.../LauncherDatabaseMigrationTest.kt`: old schema → new, old rows intact. It needs a device or emulator; say clearly when it was written but not run.
4. **Backups.** Add the data to `BackupManager.kt` (export and the restore path, with a size cap) and check an older backup without the field still restores.
5. Wire the repository in `app/.../AppGraph.kt` and expose it through `core/ui/.../LauncherGraph.kt` (manual DI, see ADR 0003).

## D. A new search source or result type

- Provider in `feature/search/` implementing the provider interface, registered in `SearchEngine.kt`; `slow = true` on IO unless it is the app list.
- `SearchResult.kt` gets the new type; `SearchPage.kt` renders it; add a filter chip if it is its own category.
- If results can be pinned, add the type to the favorites key (`ItemKey(domain, id)`) in `core/model/.../Favorites.kt`.

## D2. A new action on a search result (a long-press menu item)

Found while building B26 (hide shortcuts per app); none of this was in the checklist before.
- The action is a method on `SearchResultActions` in `feature/search/.../SearchUi.kt`.
- `app/.../MainActivity.kt` is its only implementer and is where it touches settings or repositories (`graph.settingsRepository.update { ... }` inside `lifecycleScope.launch`). A new method breaks the build there until it is implemented.
- The menu itself is `ResultRow` in `feature/search/.../SearchPage.kt`; each item is a `DropdownMenuItem`, shown for the result kinds that have a menu (those where `toFavorite()` is not null).
- Any icon it needs comes from `androidx.compose.material.icons` (the extended set is already available to the search module).
- Put rules that both this menu and a settings screen need (trim, de-duplicate, cap) as member functions on the settings class in `core/model`, so one unit test covers them and the two places cannot drift apart.

## E. Before you call it done

- `gradlew check` is green and says which new tests ran.
- The CHANGELOG bullet names the Settings path.
- Nothing from `inspiration/repos/` was copied.
- `git status` shows only files you meant to change.
