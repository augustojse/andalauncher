# Anda Launcher: weekly simulated-user test guide

This guide drives the **weekly visual and UX round** (`.claude/skills/anda-ux-round`). The automated smoke and deep tiers (`tools/qa/run-qa.ps1`, docs/TESTING.md) prove that the code paths work. This round checks what a person actually *sees*: it uses the launcher on the emulator the way a user would, and hunts for visual problems and broken behaviour.

Features are ordered by **how often a real user touches them**, so the first tier gets tested every week even if the round is cut short.

| Tier | Meaning | Weekly coverage |
|---|---|---|
| **P0** | Every unlock. If this breaks, the phone feels broken. | Every case, every week |
| **P1** | Daily or weekly habits | Every case, every week |
| **P2** | Set-up and personalisation (done once, seen forever) | Every case, every week. The live-preview checks matter most. |
| **P3** | Rare, or recovery paths | Rotate: about a third each week (see "Rotation") |

---

## 0. Ground rules: verify, don't assume

The prototype visuals are known to be rough in places. **A screenshot that looks wrong is a lead, not a finding.** Before you log a finding, confirm it with every check below:

1. **Settle first.** Wait at least 1.5 s after the last input. Then take two screenshots 500 ms apart. If they differ, an animation was still running. Wait and retake.
2. **Reproduce it.** Reach the same state a second time by a fresh path: go Home, then repeat the steps. Something seen once is `UNCONFIRMED`.
3. **Cross-check the UI tree.** Dump it with `uiautomator dump`. Then compare the node's `bounds`, `text` and `content-desc` with what the screenshot shows. Clipped text shows up as bounds narrower than the text. Overlap shows up as two nodes whose bounds intersect. Off-screen shows up as bounds outside `wm size`.
4. **Rule out the emulator.**
   - **Gestures and jank:** for any gesture or jank issue, run the same motion on a system app (`dumpsys gfxinfo com.android.settings`). If the system app is equally janky, the emulator's rendering is the cause. On 2026-10-16 every app ran at about 7 fps with GPU context timeouts, which made swipe velocity read 0.
   - **These are not app bugs either:**
   - the stock wallpaper look;
   - blur that is missing or blocky (emulator GPU);
   - status-bar icons;
   - "System UI isn't responding" dialogs;
   - Play Services pop-ups;
   - the first-frame jank after a cold boot.
5. **Rule out settings.** Read the active settings (Settings → the page involved). A "missing dock" with *Show dock* off is correct behaviour.
6. **Check both themes.** If it only happens in light or only in dark, say so. That is usually a token or contrast bug.
7. **Compare with intent.** Read the composable that draws the element. If the code clearly means to do what you see, the issue is a *design* opinion (`Severity: polish`), not a defect.

Verdict per suspected issue: `CONFIRMED`, `UNCONFIRMED` (seen once, not reproduced), `EMULATOR` (environment artifact) or `BY DESIGN`. **Only CONFIRMED issues are eligible for fixes.**

## 1. What to look for on every screen

Check these on each screenshot, in addition to the case-specific expectations:

- **Layout:**
  - text clipped or ellipsized when there's room;
  - elements overlapping;
  - content under the status bar, the navigation bar or the display cutout;
  - uneven gutters between sibling elements;
  - misaligned baselines;
  - a grid that doesn't fill its area, or overflows it.
- **Legibility:**
  - label contrast on the wallpaper below about 4.5:1 for text (3:1 for icons and large text);
  - a label with no shadow on a bright wallpaper;
  - dark text on a dark surface.
- **Colour coherence:**
  - baseline Material purple leaking into a seeded or custom theme;
  - a surface that ignores dark mode;
  - mismatched corner radii on sibling surfaces.
- **Touch targets:** interactive nodes smaller than 48 × 48 dp. Divide the px bounds by the density from `wm density` / 160.
- **State:**
  - a stuck drag shadow;
  - a scrim that stays after its overlay closes;
  - a selection or highlight that never clears;
  - an empty state with no message.
- **Motion:**
  - an overlay that pops in with no transition where its siblings animate;
  - flicker or a blank frame between states;
  - for anything visible only mid-animation, record the screen (`screenrecord`).
- **Feedback:**
  - a tap with no visible response;
  - a toast or snackbar with no action where one is promised (for example Undo).
- **Backend behaviour:**
  - the state on screen must match the persisted state;
  - after `am force-stop` and relaunch, the change is still there;
  - logcat shows no `FATAL`, `ANR` or app-tagged `E/` lines;
  - no new files appear in `files/crashes`.

---

## 2. Test cases

Each case lists **Steps** (adb-drivable), the **Expect** list, and the **Look closely at** points. Use IDs in findings (e.g. `D-03`).

### P0: Every unlock

**H-01 Home renders after Home press**
- **Steps:** press `KEYCODE_HOME` twice.
- **Expect:**
  - the workspace grid, the dock and the search bar (default: below the dock) are visible;
  - the page indicator shows the current page.
- **Look closely at:**
  - icon and label alignment across the grid;
  - spacing between the dock and the nav bar;
  - the search pill sitting on, not under, the gesture bar;
  - the At a glance header clear of the status bar.

**H-02 Launch an app from home and return**
- **Steps:**
  1. Tap an icon on home.
  2. Wait for the app to show.
  3. Press `KEYCODE_HOME`.
- **Expect:**
  - the app opens;
  - Home returns to the *same page*, with no flash of default layout and no blank frame.
- **Look closely at:** the icon's pressed-state feedback, and the return animation.

**D-01 Open the app drawer and scroll**
- **Steps:**
  1. Swipe up from **mid-screen** (`input swipe 540 1300 540 500 250`).
  2. Swipe up again from the dock row.
  3. Fling the list down, then back up.

  Both swipes must open the drawer. SmokeTourTest opens it with `drawer.open()`, so this case is the only check of the real gesture.
- **Expect:**
  - the drawer covers home with the configured background or blur;
  - apps are A–Z;
  - the suggestions row is on top (if enabled);
  - the fast scroller is on the right edge.
- **Look closely at:**
  - the top inset under the status bar;
  - the last row not hidden under the nav bar;
  - the alignment of the fast-scroller letters;
  - label clipping on long app names.

**D-02 Launch from the drawer**
- **Steps:** with the drawer open, tap an app.
- **Expect:**
  - the app opens;
  - on return, the drawer is closed (or remembered, per the design) and the search text is cleared.

**D-03 Fast scroller**
- **Steps:** drag along the fast scroller from A to Z.
- **Expect:**
  - the letter bubble follows the finger;
  - the list jumps to the matching section;
  - no letter is missing for apps that exist.
- **Look closely at:** the bubble clipping at the top and bottom edges.

**S-01 App search**
- **Steps:**
  1. Open the drawer.
  2. Tap the search field.
  3. Type the first 3 letters of an installed app.
- **Expect:**
  - results update as you type;
  - the app is the top result;
  - Enter launches it (setting *Enter launches top result*, on by default).
- **Look closely at:**
  - the keyboard not covering the results (IME insets);
  - the result row height and icon size compared with drawer icons.

**S-02 Fuzzy and initials search**
- **Steps:** type a typo of an app name (for example `chorme`), then the initials (for example `gm` for Google Maps if installed).
- **Expect:** the intended app is still among the top 3 results.

**K-01 Dock**
- **Steps:** look at the dock, then tap a dock icon.
- **Expect:**
  - the dock icons line up with the home grid columns;
  - the background matches the settings;
  - the app launches.

### P1: Daily and weekly habits

**M-01 Long-press menu on an app icon**
- **Steps:** long-press a home icon about 700 ms and release without moving.
- **Expect:**
  - a menu anchored to the icon, with app shortcuts (if any), App info, Remove and Edit;
  - tapping outside closes it.
- **Look closely at:**
  - the menu staying within screen bounds near the edges (test an icon in a corner too);
  - its shadow and corner radius against other sheets;
  - whether it animates from its anchor.

**M-02 App shortcut from the menu**
- **Steps:** long-press an app with shortcuts (Settings, Chrome, Phone), then tap a shortcut.
- **Expect:** the shortcut opens.

**G-01 Rearrange an icon (drag and drop)**
- **Steps:** long-press an icon and drag it to an empty cell (`input draganddrop x1 y1 x2 y2 1500`).
- **Expect:**
  - the icon lands in the target cell;
  - no ghost is left behind;
  - after force-stop and relaunch the position persists.
- **Look closely at:**
  - the drop preview (cell highlight) during the drag;
  - the remove and info drop-target bar appearing and disappearing cleanly.

**G-02 Drag to an adjacent page**
- **Steps:** drag an icon to the right edge and hold about 1 s.
- **Expect:** the page flips, and the icon can be dropped on the next page.

**F-01 Create a folder**
- **Steps:** drag one icon onto another.
- **Expect:**
  - a folder is created with a smart name (for example "Tools" or "Social");
  - the preview is drawn in the configured style (default: grid).
- **Look closely at:**
  - the folder preview's shape matching the icon shape setting (known: it is `CircleShape`; release-plan 3.2);
  - the title truncation.

**F-02 Open, rename and close a folder**
- **Steps:**
  1. Tap the folder.
  2. Edit its title.
  3. Press Back.
- **Expect:**
  - the popup panel opens over a blurred or dimmed home;
  - the rename persists;
  - Back closes the panel with no scrim left behind.
- **Look closely at:**
  - the panel's corner radius and padding;
  - the title field alignment;
  - the open and close animation symmetry (known: close is instant).

**R-01 Remove and Undo**
- **Steps:** drag an icon to Remove (or menu → Remove), then tap **Undo** in the snackbar.
- **Expect:**
  - the item returns to the same cell;
  - the snackbar is legible in both themes;
  - the snackbar is not under the nav bar.

**W-01 Add a widget**
- **Steps:**
  1. Long-press empty space.
  2. Tap **Widgets**.
  3. Search "clock".
  4. Tap a widget, then confirm the system bind dialog.
- **Expect:**
  - the picker slides up and is searchable;
  - the widget lands on home with sensible size.
- **Look closely at:**
  - widget preview images not stretched;
  - the picker's empty state when a search has no match;
  - the picker's top inset.

**W-02 Resize a widget**
- **Steps:** long-press the widget, then drag a resize handle.
- **Expect:**
  - the handles are visible and reachable;
  - the frame snaps to cells;
  - the content reflows.
- **Look closely at:** the handle size (48 dp target), and handles clipped at the screen edge.

**S-03 Calculator and unit converter**
- **Steps:** search `12*7`, then `5 km to mi`, then `100 f to c`.
- **Expect:**
  - `= 84`;
  - about `3.107 mi`;
  - about `37.78 °C`.
- **Look closely at:** the result card layout, and number formatting (no `84.0`, no 12-decimal tails).

**S-04 Settings and web results**
- **Steps:** search `wifi`, then a nonsense word.
- **Expect:**
  - an Android Wi-Fi settings result opens the system page;
  - the nonsense word shows a web-search suggestion, or a clear empty state.

**X-01 Home gestures**
- **Steps:**
  - swipe down on home;
  - press Back;
  - double-tap empty space;
  - pinch in (skip if not drivable).
- **Expect:**
  - swipe down expands notifications;
  - double tap locks the screen, or shows a clear prompt to enable the Gesture service (it must never do nothing silently);
  - pinch in enters edit mode.

**B-01 Notification dots**
- **Steps:**
  1. Grant notification access to Anda (`cmd notification allow_listener`).
  2. Post a notification: `cmd notification post -S bigtext -t Test tag "hello"` from the shell. The dot shows on the **Shell** app icon if it's visible, so you may need to use an app such as Clock with an alarm instead.
- **Expect:** a dot (or count) on the icon, in the badge colour, clearly visible on both themes.

### P2: Set-up and personalisation

**E-01 Edit mode and Customize sheet**
- **Steps:** long-press empty space → **Customize**. Drag the Columns slider from 5 to 4 and release.
- **Expect:**
  - the edit bar appears;
  - the Customize sheet slides up;
  - home re-lays out live while dragging;
  - the value persists after closing.
- **Look closely at:**
  - the sheet covering the home it is previewing (the preview must stay visible);
  - slider labels;
  - the sheet's bottom inset.

**ST-01 Settings main page**
- **Steps:** menu → Settings (or `am start -n com.anda.launcher/com.anda.launcher.feature.settings.SettingsActivity`).
- **Expect:**
  - a list of 14 categories with icons;
  - the search field at the top;
  - its own Recents card.
- **Look closely at:**
  - row height and icon alignment;
  - the top app bar title;
  - edge-to-edge insets.

**ST-02 Settings search**
- **Steps:** search `blur`, then `subgrid`, then `xyz`.
- **Expect:**
  - multiple matches for `blur`;
  - `subgrid` jumps to the highlighted row with its live preview;
  - `xyz` shows an empty state.

**ST-03 Grid size (Home screen page)**
- **Steps:** change Columns to 4 and Rows to 5, then go Home.
- **Expect:**
  - the grid reflows;
  - no icon is lost or off-screen;
  - the dock stays aligned.
- **Afterwards:** restore 5 × 6.

**ST-04 Icon shape, size and labels (Icons page)**
- **Steps:**
  1. Shape → Squircle.
  2. Icon size 120%.
  3. Labels off, then back on.
  4. Go Home after each step.
- **Expect:**
  - every surface (home, dock, drawer, folders, search results) updates consistently;
  - labels don't collide at 120%.

**ST-05 Look & feel: themes, colours and blur**
- **Steps:**
  1. Theme → Dark, then OLED.
  2. Visit home, drawer, a folder, the menu and settings.
  3. Switch Colors to Pick and pick an accent.
  4. Theme → Custom, base Light; under Advanced set a Primary and a Surface color (one from the picker).
  5. Blur: Background blur 100 % and Card blur 0 %, open a folder; then Background 0 % and Card 100 %.
- **Expect:**
  - Dark: every surface goes dark, no light surface left over; OLED: drawer and settings backgrounds are true black;
  - the picked accent shows on the drawer, folder and menu;
  - Custom: the Palette row and every surface use the chosen colors, text stays readable;
  - the two blur settings visibly differ: home behind the folder vs the frosted card itself.
- **Look closely at:** baseline-purple leaks (known risk: release-plan 3.3); card blur misaligned with the icons behind it after the folder's open animation.

**ST-06 Drawer style and sort**
- **Steps:**
  1. Drawer Style → List, open the drawer.
  2. Style → Horizontal pages, open the drawer.
  3. Sort → Most used.
  4. Search bar at the bottom → on.
- **Expect:**
  - each style renders fully, with no clipped last row or column;
  - the page dots show in paged mode;
  - the bottom search bar sits above the keyboard.

**ST-07 Search bar position and style**
- **Steps:** cycle the position through Top, Above dock and None, then the style through Pill, Rounded and Square.
- **Expect:**
  - the bar moves;
  - the grid gives back the space when the bar is None;
  - Top sits below the status bar.

**ST-08 Dock options**
- **Steps:**
  1. Show background → on.
  2. Background shape → Pill.
  3. Dock pages → 2, then swipe the dock.
- **Expect:**
  - the background stays behind the icons, centred;
  - the dock pages scroll independently of home.
- **Look closely at:** icon labels on a coloured background (contrast, release-plan 3.4).

**ST-09 At a glance**
- **Steps:** set an alarm in Clock, then go Home.
- **Expect:**
  - the date and the next alarm show;
  - the text has a shadow and is legible.

**ST-10 Hidden apps**
- **Steps:** long-press a drawer app → Hide, then open Settings → Hidden apps.
- **Expect:**
  - the app is gone from the drawer and search;
  - it is listed under Hidden apps;
  - unhiding restores it.

**ST-11 Page transitions**
- **Steps:**
  1. Add a second home page by dragging an icon to the edge.
  2. Set Scroll effect → Cube.
  3. Swipe between the pages.
- **Expect:**
  - the effect runs;
  - nothing is left drawn mid-transition when you stop half-way.

### P3: Rare and recovery paths (rotate)

**P-01 First run / Welcome card**
- **Steps:** `pm clear com.anda.launcher`, then press Home.
- **Expect:**
  - the welcome card offers "Set Anda as your home app", "Customize more" and "Done";
  - the default layout is populated.

**P-02 Backup now and restore**
- **Steps:** Settings → Backup & restore → Back up now. Change a setting, then Restore from file.
- **Expect:**
  - the system file picker opens;
  - after restore, the changed setting is reverted.

**P-03 Reset all settings**
- **Steps:** Backup page → Reset all settings → confirm.
- **Expect:**
  - all defaults are back;
  - the home layout is kept (it is settings only).

**P-04 Permissions page**
- **Steps:** open each row.
- **Expect:** each one opens the right system screen, or shows a message if that screen is missing; none of them crashes.

**P-05 About → Problem reports**
- **Expect:** "No crashes recorded", or a list with Share and Delete.

**P-06 Rotation / large text**
- **Steps:**
  - `settings put system font_scale 1.3` (then 2.0), and check home, drawer and settings;
  - `wm density 560`, then reset.
- **Expect:**
  - labels wrap or ellipsize gracefully;
  - no overlap;
  - the settings rows grow.
- **Afterwards:** reset font_scale to 1.0 and density.

**P-07 Two-panel / subgrid**
- **Steps:** turn on Subgrid positioning, and drag an icon between cells.
- **Expect:** half-cell placement works, and the icon is not clipped.

**P-08 Install / uninstall**
- **Steps:**
  1. `adb install` any small APK.
  2. Check that it appears in the drawer A–Z.
  3. Uninstall it.
- **Expect:** the icon disappears from home, the drawer and folders, leaving no blank cell artifact.

### Rotation for P3

Week number `w = ISO week % 3`:
- `w = 0` runs P-01, P-02 and P-03;
- `w = 1` runs P-04, P-05 and P-06;
- `w = 2` runs P-07 and P-08, plus P-06 again (accessibility matters).

---

## 3. Severity

| Severity | User-visible meaning | Examples |
|---|---|---|
| **S1 blocker** | Can't use the phone normally | crash, home blank, drawer won't open, can't launch apps, stuck drag or scrim |
| **S2 major** | A feature is broken or clearly wrong | widget won't add, setting has no effect, text unreadable, element off-screen or under a system bar |
| **S3 minor** | Works but looks wrong | clipping, overlap, misalignment, wrong colour in one theme, no feedback on tap |
| **S4 polish** | Inconsistency a designer would flag | radius or spacing mismatch, missing transition, slightly low contrast |

## 4. Findings format

Each finding goes in `qa-reports/<stamp>-ux/findings.md`:

```
### UX-<n> [S3] D-01 Last drawer row hidden under the navigation bar
Verdict: CONFIRMED (2/2 repro, light + dark)
Where: App drawer, vertical grid, default settings
Evidence: shots/D-01-a.png, shots/D-01-b.png, ui/D-01.xml (node "Zoom" bounds [..][..2424] vs screen 2424)
Expected: last row fully above the gesture bar (navigationBarsPadding)
Likely source: feature/drawer/.../AppDrawer.kt:<line>
Fix idea: add contentPadding = WindowInsets.navigationBars.asPaddingValues() to the LazyVerticalGrid
```
