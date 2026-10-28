# Changelog

Every update gets a new version here, newest first. The build takes `versionName` and
`versionCode` from the top entry, and Settings → About shows this list.

- Heading: `## X.Y.Z — YYYY-MM-DD`. Bump Z for fixes only, Y for new features (resetting Z).
- One bullet per user-visible change, starting with `New:`, `Changed:` or `Fixed:`.

## 0.6.0 — 2026-10-27
- New: search finds apps and shortcuts named in other alphabets when you type their Latin spelling, so "telegram" finds Телеграм and "kamera" finds Камера (Settings → Search → Advanced → Other alphabets, on by default). It is ready a few seconds after the launcher starts.
- New: contact results show the phone number with Call and Message buttons, which open your dialer or messages app (Settings → Search → Call and message buttons).

## 0.5.0 — 2026-10-22
- New: Icon editor (Settings → Icons, at the top). Change backgrounds (the app's own, a color, a gradient or none), give icons one flat color, adjust hue, saturation, brightness and contrast, resize, move, rotate or mirror the foreground, and add padding, an outline or a shadow, all with a live preview on light, dark or theme backgrounds.
- New: editor colors can follow Material You: pick a theme role such as Primary container and icons change with your wallpaper and theme. One-tap presets (Material You, Tonal, Bold, Gradient, Glyph only, Grayscale…).
- New: 10 more icon shapes (Pebble, Clover, Flower, Sunny, Arch, Diamond, Octagon, Heart, Leaf, Egg) and a Custom shape built corner by corner (round, squircle, cut or sharp, any size).
- New: Unique icons. Keep an icon pack or style for most apps and give single apps an exception: their original icon, an icon from another pack (pick any icon, with search and suggestions), themed on or off, or a style and shape of their own.
- New: long-press an app → Edit → Icon opens the editor for that app.
- New: Advanced on the Icons page can reset the editor style, remove all exceptions or clear the icon cache.
- Changed: the Icons page is reorganised: the editor first, then pack, shape, themed icons and size, then Unique icons, then Advanced.
- Changed: themed icons take their colors from the launcher's theme, so they match a custom or OLED theme too.

## 0.4.0 — 2026-10-22
- New: Look & feel → Theme picks Auto, Light, Dark, OLED (true black) or Custom, each shown as a small preview.
- New: the custom theme starts from Material You, your wallpaper or a picked color; under Advanced you can set its primary, secondary, tertiary and surface colors one by one, from wallpaper and Material You suggestions or any color.
- New: every color setting has a full color picker (hue, saturation, brightness or a hex code).
- New: Background blur and Card blur sliders. The home screen behind an open folder and the folder card's frosted glass now blur separately, at the strength you choose.
- New: Advanced options in Look & feel for high-contrast text and an accent tint on surfaces.
- Changed: search looks and works like Kvaesitso's. Actions (call, alarm, web search…) are chips by the search bar, a calculation is a large answer card, results are grouped into cards with "Show more", and filter chips narrow results to Apps, Shortcuts, Contacts or Settings.
- Changed: with no matching app, Enter runs the highlighted first chip.
- Changed: search shows up to 10 shortcuts and contacts (was 5); long groups fold behind "Show more".
- Fixed: with a dark theme on a light system, the status-bar icons in Settings were dark on dark.
- Changed: "Dark mode" in Look & feel is now "Theme"; your previous choice carries over.

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
