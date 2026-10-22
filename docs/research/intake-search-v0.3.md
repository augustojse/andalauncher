# Feature intake: search, v0.3

Three ideas from [inspiration/improvements.md](../../inspiration/improvements.md), built together
because B4 needs A1. Each was built from the Android docs; no code came from Lawnchair or Kvaesitso.

## A1 Streaming search providers

| Field | Value |
|---|---|
| Origin | Kvaesitso `SearchService`, Lawnchair `SearchProvider` (structure only) |
| User value | Apps appear as soon as they're ranked. Slow sources (contacts, app shortcuts) are added when they're ready. One broken source no longer empties the whole result list. |
| Performance cost | None. The providers run in parallel, as before. One extra recomposition for each slow provider. |
| API risk | None (internal structure). |
| Settings key | The existing `LauncherSettings.search.*` source switches, one per provider |
| Conflicts / merge | Replaces `SearchEngine.search()` with `SearchEngine.results(): Flow`. Fast providers are published together, so the app grid never flickers. |
| Default | — |

## B4 Quick actions and custom web engines

| Field | Value |
|---|---|
| Origin | Kvaesitso `data/search-actions`, Lawnchair `qsb/providers` |
| User value | A phone number can be called, texted or saved. An email can be written to. A web address opens in the browser. "7:30" sets an alarm, "5 min" starts a timer, "dentist oct 31" adds a calendar event. Any web engine can be added as an extra row with a URL template. |
| Performance cost | None. A few regexes run per query, on `Dispatchers.Default`. |
| API risk | Public intents: `ACTION_DIAL`, `ACTION_SENDTO`, `ACTION_VIEW`, `AlarmClock.ACTION_SET_ALARM` / `ACTION_SET_TIMER` (install-time `SET_ALARM` permission), `ACTION_INSERT` on `CalendarContract.Events`, and `ContactsContract.Intents.Insert`. Every action opens another app with the details filled in, so the user confirms it there. **Still no INTERNET permission.** |
| Settings key | `LauncherSettings.search.quickActions`, `LauncherSettings.search.webEngines` |
| Conflicts / merge | The existing "Web search suggestion" row stays. Custom engines are listed after it. A date ranks low (score 400), because it can be part of an app name ("Today Weather"). Only English month and unit names are recognised for now. |
| Default | Quick actions on. No custom engines. |

Left for later: "Share text" for any query, custom intents (not only URLs) as engines, recognising
other languages, and having Enter run the top action when no app matches.

## B9 Typo-tolerant ranking

| Field | Value |
|---|---|
| Origin | Kvaesitso `ResultScore` (Jaro-Winkler band) |
| User value | "spotfiy" finds Spotify and "gmial" finds Gmail. A renamed app is still found by its original name. |
| Performance cost | Jaro-Winkler only runs when every other band misses, and only for queries of 3 or more letters. Its cost is O(n·m) on short labels, well inside the 8 ms / 300 apps budget. |
| API risk | None. |
| Settings key | `LauncherSettings.search.fuzzyMatching` (the existing switch) |
| Conflicts / merge | A new lowest band, scored 10–45, so it never outranks a subsequence match. The threshold is 0.85 for 3–4 letter queries and 0.8 for longer ones ("cam" vs "car" is 0.82). |
| Default | On (with fuzzy matching). |
