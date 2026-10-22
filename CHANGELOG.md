# Changelog

Every update gets a new version here, newest first. The build takes `versionName` and
`versionCode` from the top entry, and Settings → About shows this list.

- Heading: `## X.Y.Z — YYYY-MM-DD`. Bump Z for fixes only, Y for new features (resetting Z).
- One bullet per user-visible change, starting with `New:`, `Changed:` or `Fixed:`.

## 0.3.0 — 2026-10-21
- New: quick actions in search. Type a phone number to call, text or save it, an email to write to it, or a web address to open it. "7:30" sets an alarm, "5 min" starts a timer and "dentist oct 31" adds a calendar event.
- New: your own web search engines (Settings → Search → Add search engine), each shown as a row under your results.
- New: search tolerates typos, so "spotfiy" finds Spotify, and renamed apps are still found by their original name.
- Changed: apps show in search straight away; contacts and app shortcuts are added as soon as they're ready.
- Fixed: unlocking hidden apps while searching now updates the results.

## 0.2.0 — 2026-10-20
- New: Settings → Dev, for notes and screenshots about anything misbehaving. They are kept in backups and copied to Downloads/Anda Launcher/dev.
- New: version history in Settings → About; every update now has its own version number.
- Fixed: icons in search results, folders and lists sometimes showed another app's icon.
- Fixed: icons already on screen now pick up a new icon shape or pack straight away.

## 0.1.0 — 2026-10-15
- First prototype running as the home app on Pixel 10: home grid, dock, drawer, search, folders, widgets, gestures, icon packs, backups and crash-loop safe mode.
