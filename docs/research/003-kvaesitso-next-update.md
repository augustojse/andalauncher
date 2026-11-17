# Kvaesitso inspiration: next update plan

_2026-10-25 · Anda 0.5.0 (`6476c88`) · Sources: Kvaesitso user guide, GitHub releases 1.33.0–1.41.0, IzzyOnDroid listing, Android API reference_

Clean-room note: every Kvaesitso fact here comes from its user-facing docs, release notes or store
listing. No Kvaesitso source code was read for this report.
- The user-guide pages were read from the `docs/` folder of snapshot `b72ddb6`, which builds kvaesitso.mm20.de.
- The Filters page was also checked on the live site.
- References such as [S4] point to [Sources](#sources).

## Summary

- Anda already matches Kvaesitso's core search: typo tolerance, streaming providers, the 8 built-in quick actions, web engines, filter chips, calculator and units.
- The biggest offline gaps are favorites (pin any result), calendar event search, tags, a quick-action editor and contact actions.
- Most of Kvaesitso's breadth beyond that needs the network (Wikipedia, places, cloud files, weather, currency). Those sit in the decision section.
- Proposed 0.6.0, all offline and with no new permission: calendar search, favorites, quick-action editor, contact actions and script-insensitive search.
- Tags, built-in widgets and file search are deferred. Favorites lays the `ItemKey` groundwork that tags (B8) need.

## Gap matrix

Status: **Shipped** / **Partial** / **Missing** / **Already in improvements.md** (with the idea ID).
Impact and effort: H/M/L and S/M/L as in improvements.md. A dash means the column doesn't apply because nothing is to be built.

### Search

| Feature | Kvaesitso behavior (own words) | Anda status | Impact | Effort | Modules | Perf risk |
|---|---|---|---|---|---|---|
| Fuzzy, typo-tolerant app search | Finds apps despite typos; ranking was reworked in 1.34.0 [S23]. Exact scoring [unverified] | Shipped (0.3.0, `FuzzyMatcher` bands + launch stats) | — | — | — | — |
| Script-insensitive search | Transliterates queries, app and shortcut names into Latin script on Android 10+ (1.39.0) [S22] | Missing (diacritics are folded, scripts are not) | M | S | `core/apps`, `feature/search` | Low if keys are precomputed at load |
| App shortcuts in search | Shortcuts are search results; 1.41.0 lets users exclude apps from them [S22] | Shipped (results); exclusion Missing | L–M | S | `feature/search`, `feature/settings` | None |
| Contact search | Contacts are a local search source [S24] | Shipped (opt-in) | — | — | — | — |
| Contact call and message | Option to call a contact straight from the result, skipping the dialer (1.35.0) [S23] | Partial (a result only opens the contact card) | M | S | `feature/search` | Low (one batched phone query) |
| Calendar event search | Calendar events are a local search source [S24]; pinned events show in the calendar widget [S1] | Missing (`READ_CALENDAR` is declared and used by Glance) | H | S–M | `feature/search`, `feature/settings` | Low (slow provider on IO) |
| Local file search | Searches files on the device; the listing ties all-files access to it [S24] | Missing | M | M–L | new provider, manifest | Medium (MediaStore scans, new permission) |
| Task search | Searches tasks from the Tasks.org app (1.36.0) [S23], via its read permission [S24] | Missing | L–M | S | `feature/search` | Low |
| Calculator | Expression calculator; converts positive integers between dec, hex, oct and bin with `0x`, `0`, `0b` prefixes [S7] | Partial (own evaluator; no base conversion) | L | S | `feature/search` | None |
| Unit converter | Time, length, mass, area, data, speed, temperature, volume; `>>` target syntax; currencies if enabled [S8] [S22] | Partial (no area; currency needs the network) | L | S | `feature/search` | None |
| Built-in quick actions | Call, message, email, add contact, alarm, timer, event, open URL, each shown only when the query fits [S6] | Shipped (0.3.0) | — | — | — | — |
| Web search actions | User-made engines with a URL template and a choice of query encoding [S6] | Shipped (`webEngines`); encoding choice Missing | L | S | `feature/search` | None |
| Quick-action editor | Each built-in can be switched off, and actions can be reordered by dragging [S6] | Missing (one global `quickActions` switch) | M | S | `feature/search`, `feature/settings` | None |
| App search actions | Opens a chosen app's own search screen with the query; marked experimental [S6] | Missing | M | S | `feature/search`, `feature/settings` | None |
| Custom intent actions | User-defined intents; the query goes in as a data URI or a string extra [S6] | Missing (deferred in intake v0.3) | L–M | S–M | `feature/search`, `feature/settings` | None |
| Type filters | Filter bar above the keyboard plus a filter menu; filters reset when the query is cleared [S4] | Shipped (0.4.0 filter chips) | — | — | — | — |
| Hidden-results filter | A filter that brings back results the user hid [S4] | Already in improvements.md (B10) | — | — | — | — |
| Default filter, filter bar order | Pre-selected filters and the bar's items and order are settings [S4] | Missing | L | S | `feature/search`, `feature/settings` | None |
| Favorites | Manually sorted pins, auto-sorted pins and frequently used items; any result can be pinned [S1] | Partial (drawer suggestions = frequent apps; the dock) | H | M | `core/model`, `core/data`, `feature/drawer`, `feature/search` | Low |
| Tags | Tags act as extra search keywords and can be pinned to favorites [S2]; tag icons and compact tags (1.34.0) [S23] | Already in improvements.md (B8) | — | — | — | — |
| Per-item customization | Custom label (both labels searchable), icon and tags for any result [S10] | Partial (apps only, 0.5.0); Already in improvements.md (A2) | — | — | — | — |
| Per-item visibility | Hidden items reachable through a filter [S4]; the exact visibility levels are [unverified] | Already in improvements.md (B7) | — | — | — | — |
| Hide app grid while search is empty | Option to show nothing until the user types (1.39.0) [S22] | Missing | L | S | — | — |

### Widgets, gestures, theming and platform

| Feature | Kvaesitso behavior (own words) | Anda status | Impact | Effort | Modules | Perf risk |
|---|---|---|---|---|---|---|
| Widget screens | Extra widget screens, each assignable to a gesture (1.40.0) [S22] | Partial (home pages hold widgets; no gesture-bound screen) | M | M | `feature/home`, `feature/gestures` | Medium (idle PSS with more widgets) |
| Clock widget | Six clock styles, default or compact layout, one dynamic part (date, favorites, media, alarm, battery) [S12] | Already in improvements.md (C5, A6) | — | — | — | — |
| Media controls | Music widget and clock part, fed by media sessions read through notification access [S15] [S19] | Already in improvements.md (B5) | — | — | — | — |
| Calendar widget | Next seven days, calendars to show and hiding all-day events are settings [S13] | Already in improvements.md (B18); Glance shows the next event only | — | — | — | — |
| Notes widget | Markdown notes, several instances, export, optional sync with a linked file [S16] | Already in improvements.md (B18) | — | — | — | — |
| Favorites / apps widget | Shows favorites on the widget page; since 1.39.0 it can show items of chosen tags [S14] [S22] | Already in improvements.md (B18) | — | — | — | — |
| Plugins | Separate apps provide weather, files, contacts, calendar or places after the user grants access [S3] [S25] | Already in improvements.md (B19) | — | — | — | — |
| Configurable gestures | Search and widget gestures freely bound (1.37.0); a gesture can open settings (1.41.0) [S22] | Shipped (8 gestures, `LaunchApp`, `OpenLauncherSettings`) | — | — | — | — |
| Shade gestures without accessibility | Notification and quick-settings gestures no longer need the accessibility service (1.40.0) [S22] | Shipped (`EXPAND_STATUS_BAR`) | — | — | — | — |
| Feed page | Swipe right opens a feed from a third-party provider app; Discover needs an unofficial bridge [S20] | Missing | L | L | — | — |
| Color schemes | Any number of custom schemes from key colors, roles derived by tone, light and dark variants [S9] | Partial (one custom palette); Already in improvements.md (A7, C1) | M | S | `core/ui/theme`, `feature/settings` | None |
| Shape, type, transparency schemes | Separate schemes for each; theme file format and import (1.37.0) [S22] | Already in improvements.md (C8, C9, C14) | — | — | — | — |
| High-contrast themes | Black-and-white scheme [S9] and a Material 3 high-contrast theme (1.37.0) [S22] | Partial (OLED theme, high-contrast text) | L | S | `core/ui/theme` | None |
| Themed icons | Themed icons for all apps, themable icon packs, forced generated icons [S11] | Shipped | — | — | — | — |
| Google Sans font | Default font is Google Sans (1.40.0) [S22] | Already in improvements.md (C2) | — | — | — | — |
| Private space | Private profile shown as its own section [S21]; respects the system "hide private space" setting (1.41.0) [S22] | Already in improvements.md (B3) | — | — | — | — |
| Other calendar systems | Non-Gregorian calendars as the default or a secondary calendar (1.40.0) [S22] | Missing | L | S | `feature/home` (glance) | None |
| Language and region | One screen for measurement system and time format, plus a UK option (1.39.0) [S22] | Missing | L | S | `feature/search`, `feature/settings` | None |
| Apps as a list | List instead of grid (1.35.0) [S23] | Shipped (drawer List style) | — | — | — | — |
| Trim the item menu | Option to hide app details in the context menu (1.40.2) [S22] | Already in improvements.md (B14) | — | — | — | — |

## Requires network — decision needed

Anda has no INTERNET permission (docs/RND.md). None of these are proposed. Each one needs your
decision, and the offline alternative is what Anda can do instead.

| Feature | What Kvaesitso does | Offline alternative |
|---|---|---|
| Wikipedia results | Searches Wikipedia when the online-results filter is on, which is off by default [S4] [S5] | A web engine with a Wikipedia URL template (shipped) hands the query to the browser |
| Places and departures | Searches OpenStreetMap [S5] [S23]; plugins add HERE, Foursquare and public transport [S3] | A custom intent `geo:0,0?q=%s` opens the maps app (0.6.0 feature 3) |
| Cloud file search | Nextcloud [S24] and OneDrive via a plugin [S3] | An app search action into the cloud app (0.6.0 feature 3) |
| Online weather | Weather widget fed by online providers or plugins; location from GPS or a fixed place [S17] [S18] | B6 (Breezy Weather broadcast, already in improvements.md). Anda itself stays offline |
| Currency conversion | Currencies in the unit converter when enabled [S8]; preferred currencies (1.41.0) [S22] | None real. Bundled rates go stale and would mislead. Hand off to a web engine instead |
| Web search auto-import | Fetches a site's search description to fill in the template [S6] | Typing the URL template by hand (shipped) |
| Network plugins | Plugins may sign in to online services [S3] | Decision: does an INTERNET-holding plugin APK break "Anda has no INTERNET"? Needs an ADR before B19 |
| Online-results filter | Opt-in switch that sends queries to online services [S4] [S5] | Not needed while Anda has no online providers |

## Proposed next version (0.6.0)

Five features. All are offline, none adds a permission, and four are in `feature/search`.

| # | Feature | Why this one, in one line |
|---|---|---|
| 1 | Calendar event search | The highest-impact missing source, and its permission is already declared. |
| 2 | Favorites | Kvaesitso's core concept. It builds on the shipped suggestions and lays the `ItemKey` groundwork for tags and visibility. |
| 3 | Quick-action editor | Finishes B4 and is the offline answer to most network-only sources. |
| 4 | Contact actions | Gets from a contact result to a call in one tap, without `CALL_PHONE`. |
| 5 | Script-insensitive search | Small change that makes search usable for users of non-Latin scripts, and removes per-query normalising. |

Each feature gets a feature intake card (`docs/research/feature-template.md`) before it is built.

### 1. Calendar event search

- **Goal:** find an event by typing part of its title, as Kvaesitso does [S24].
- **User-visible behavior:**
  - With Settings → Search → Calendar on, matching events show in an Events group with date and time.
  - An Events filter chip appears; tapping an event opens it in the calendar app.
- **Implementation sketch:**
  - New `CalendarSearchProvider` (`slow = true`) queries `CalendarContract.Instances.CONTENT_SEARCH_URI` [A1] for now −30 d to +365 d, at most 10 results.
  - New `SearchResult.Event(eventId, title, begin, end, allDay, color, score)` and a `SearchCategory.Events` chip.
  - Opening an event reuses the `Events.CONTENT_URI` + begin/end intent that `Glance.kt` already builds.
  - Settings key `LauncherSettings.search.calendar` (default `false`, like contacts). It shares the runtime grant with Glance.
- **Tests to add:**
  - Provider unit test over fake cursor rows: title ranking and the date line.
  - `SearchEngineTest`: apps still publish before the calendar provider finishes.
  - Settings serialization round-trip for the new key.
- **Perf budget check:**
  - Runs on `Dispatchers.IO` as a slow provider, for queries of 2+ characters only, so app results keep their < 8 ms budget.
  - Nothing runs at startup, so cold start is unaffected.

### 2. Favorites

- **Goal:** keep chosen items one tap away, as Kvaesitso's favorites do [S1].
- **User-visible behavior:**
  - Long-press any search result (app, shortcut, contact, event, settings page) → Pin to favorites / Unpin.
  - The drawer shows a Favorites row above Suggestions. An edit sheet reorders or removes pins.
- **Implementation sketch:**
  - `ItemKey(domain, id)` in `core/model`: the first slice of A2, covering app, shortcut, contact, event and setting.
  - Room table `favorites(key, domain, position, payload)`. The database moves from version 1 to 2 (the first migration). `payload` stores what is needed to show the item again.
  - `FavoritesRepository` exposes a `Flow`, and `BackupManager` adds the table to backups.
  - Items whose app or contact is gone are pruned. Reordering offers accessibility move actions (RND.md).
  - Settings key `LauncherSettings.drawer.showFavorites` (default `true`; the row hides itself when empty).
- **Tests to add:**
  - Room migration test 1 → 2 against `core/data/schemas`.
  - Repository: pin, unpin, reorder, pruning of a removed app.
  - Backup and restore round-trip that includes favorites.
  - Instrumented Compose test: a pinned contact shows in the drawer row.
- **Perf budget check:**
  - Favorites load after the first home frame, so cold start stays ≤ 350 ms.
  - The row is an immutable list built before the drawer opens, and contact photos decode asynchronously at thumbnail size, keeping drawer p95 ≤ 8.3 ms.
  - At most two rows of items: negligible PSS.

### 3. Quick-action editor

- **Goal:** let users choose, order and add the actions offered for a query [S6].
- **User-visible behavior:**
  - Settings → Search → Quick actions lists the built-ins with switches, which can be reordered.
  - Add: web search (existing), "Search in an app" or a custom intent.
- **Implementation sketch:**
  - New settings: `search.quickActionOrder: List<QuickActionKind>`, `search.disabledQuickActions: Set<QuickActionKind>`, `search.customActions: List<CustomAction>`.
  - `CustomAction.AppSearch(packageName, label)` sends `Intent.ACTION_SEARCH` with `SearchManager.QUERY` [A3] to that package.
  - `CustomAction.IntentAction(action, dataTemplate, extraKey, packageName, className, mimeType)` uses the existing `%s` placeholder.
  - `webEngines` is unchanged, so no settings migration.
  - Only `startActivity`: no broadcasts or services. A missing target shows a message.
  - At most 10 custom actions, clamped in `SettingsSafety`.
  - Apps that handle `ACTION_SEARCH` are listed in the settings screen, off the main thread (`QUERY_ALL_PACKAGES` is already held).
- **Tests to add:**
  - `QueryClassifierTest`: disabled kinds are dropped and order is respected.
  - New `CustomActionTest`: placeholder substitution, encoding, data vs extra.
  - `SettingsSafety` test: caps and invalid templates.
- **Perf budget check:** per query, only string substitution on `Dispatchers.Default`, and no PackageManager calls. Negligible next to the 8 ms budget.

### 4. Contact actions

- **Goal:** call or text a contact from the result, close to Kvaesitso's direct call (1.35.0) [S23].
- **User-visible behavior:** contact results with a phone number get Call and Message buttons.
- **Implementation sketch:**
  - `ContactSearchProvider` makes one batched query on `ContactsContract.CommonDataKinds.Phone` [A4] for its ≤ 10 contacts and fills `SearchResult.Contact.phone`.
  - Call uses `Intent.ACTION_DIAL` [A5], which needs no permission. Message uses `ACTION_SENDTO` with `smsto:`.
  - Settings key `LauncherSettings.search.contactActions` (default `true`).
- **Tests to add:** provider maps the primary number for each contact; the intents are built with the right URIs.
- **Perf budget check:** one extra query inside a provider that is already slow and off the app path. Apps keep their budget.

### 5. Script-insensitive search

- **Goal:** typing in Latin letters finds apps whose names are in other scripts, as Kvaesitso 1.39.0 does [S22].
- **User-visible behavior:** "telegram" finds an app labelled in Cyrillic, and "toukyou" finds a Japanese-named app.
- **Implementation sketch:**
  - `core/apps` builds a Latin search key for each app and shortcut label at load time, with `android.icu.text.Transliterator` "Any-Latin; Latin-ASCII" [A2].
  - Only labels with non-Latin letters are transliterated, and the instance is created once.
  - Add `AppInfo.latinKey`. `AppSearchProvider` scores the better of the label and `latinKey`, and transliterates the query once.
  - Precompute the normalised `originalLabel` too. Today `AppSearchProvider.kt:22` normalises it on every query.
  - `core/model` stays pure Kotlin: the transliterator is injected from `core/apps`.
  - Settings key `LauncherSettings.search.transliterate` (default `true`).
- **Tests to add:**
  - JVM test with a fake transliterator: the key is used and ranks below an exact label match.
  - Instrumented test with real ICU on a Cyrillic and a Japanese label.
- **Perf budget check:**
  - Load-time cost is measured first, with a target of < 15 ms for 300 labels on a background thread.
  - The per-query cost is one short transliteration. The precomputed `originalLabel` lowers today's per-query cost, keeping well under 8 ms.

### Next after 0.6.0

B8 tags (needs `ItemKey`), B26 shortcut exclusions, B7 visibility levels, then B18 built-in widgets.

## Out of scope / rejected

| Item | Reason |
|---|---|
| Hide app grid while search is empty | Anda's drawer is the app grid, and search lives inside it. |
| Feed page | Discover is blocked for third-party launchers (research 001). The provider-app protocol is [unverified] and unresearched. |
| Local file search in 0.6.0 | Needs a new media or all-files permission. It waits for a spike (B32). |
| Built-in widgets and widget screens in 0.6.0 | M–L effort and idle-PSS risk. Kept in B18 and B31. |
| All network features | Listed in the decision section. Nothing is added without your decision. |
| Kvaesitso's calculator library or unit tables | Licensed third-party code and data. Anda keeps its own evaluator and tables. |

## Sources

Kvaesitso user guide (kvaesitso.mm20.de):
- [S1] [Favorites](https://kvaesitso.mm20.de/docs/user-guide/concepts/favorites)
- [S2] [Tags](https://kvaesitso.mm20.de/docs/user-guide/concepts/tags)
- [S3] [Plugins](https://kvaesitso.mm20.de/docs/user-guide/concepts/plugins)
- [S4] [Filters](https://kvaesitso.mm20.de/docs/user-guide/search/filters)
- [S5] [Online results](https://kvaesitso.mm20.de/docs/user-guide/search/online-results)
- [S6] [Quick actions](https://kvaesitso.mm20.de/docs/user-guide/search/quickactions)
- [S7] [Calculator](https://kvaesitso.mm20.de/docs/user-guide/search/calculator)
- [S8] [Unit converter](https://kvaesitso.mm20.de/docs/user-guide/search/unit-converter)
- [S9] [Color schemes](https://kvaesitso.mm20.de/docs/user-guide/customization/color-schemes)
- [S10] [Per-item customization](https://kvaesitso.mm20.de/docs/user-guide/customization/per-item-customization)
- [S11] [Themed icons](https://kvaesitso.mm20.de/docs/user-guide/customization/themed-icons)
- [S12] [Clock widget](https://kvaesitso.mm20.de/docs/user-guide/widgets/clock)
- [S13] [Calendar widget](https://kvaesitso.mm20.de/docs/user-guide/widgets/calendar-widget)
- [S14] [Favorites widget](https://kvaesitso.mm20.de/docs/user-guide/widgets/favorites-widget)
- [S15] [Music widget](https://kvaesitso.mm20.de/docs/user-guide/widgets/music-widget)
- [S16] [Notes widget](https://kvaesitso.mm20.de/docs/user-guide/widgets/notes-widget)
- [S17] [Weather widget](https://kvaesitso.mm20.de/docs/user-guide/widgets/weather-widget)
- [S18] [Weather integration](https://kvaesitso.mm20.de/docs/user-guide/integrations/weather)
- [S19] [Media control](https://kvaesitso.mm20.de/docs/user-guide/integrations/mediacontrol)
- [S20] [Feed](https://kvaesitso.mm20.de/docs/user-guide/integrations/feed)
- [S21] [FAQ](https://kvaesitso.mm20.de/docs/user-guide/faq)

Releases and listings:
- [S22] [GitHub releases, page 1](https://github.com/MM2-0/Kvaesitso/releases): 1.37.0 to 1.41.0 (1.41.0 dated 2026-09-15)
- [S23] [GitHub releases, page 2](https://github.com/MM2-0/Kvaesitso/releases?page=2): 1.33.0 to 1.36.1
- [S24] [IzzyOnDroid listing](https://apt.izzysoft.de/fdroid/index/apk/de.mm20.launcher2.release): description and permissions of 1.41.0
- [S25] [Plugin developer guide: file search](https://kvaesitso.mm20.de/docs/developer-guide/plugins/plugin-types/file-search) and [calendar](https://kvaesitso.mm20.de/docs/developer-guide/plugins/plugin-types/calendar)

Android reference:
- [A1] [`CalendarContract.Instances`](https://developer.android.com/reference/android/provider/CalendarContract.Instances)
- [A2] [`android.icu.text.Transliterator`](https://developer.android.com/reference/android/icu/text/Transliterator)
- [A3] [`SearchManager`](https://developer.android.com/reference/android/app/SearchManager)
- [A4] [`ContactsContract.CommonDataKinds.Phone`](https://developer.android.com/reference/android/provider/ContactsContract.CommonDataKinds.Phone)
- [A5] [`Intent.ACTION_DIAL`](https://developer.android.com/reference/android/content/Intent#ACTION_DIAL)
