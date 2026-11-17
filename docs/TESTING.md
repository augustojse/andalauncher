# Testing Anda Launcher

Three tiers, cheapest first. Everything destructive runs on the **emulator** (`C1_Pixel_9_API_37`), never on a real phone: the runner refuses any non-emulator device.

| Tier | When | Time | What it covers |
|---|---|---|---|
| **Unit** | every change (`gradlew check`) | ~3 min | settings safety, backup reader, crash-loop rule, drawer/search logic, grid solver; lint with warnings as errors |
| **Smoke** | daily, or after any feature change | ~3 min on a running emulator | unit tests + **SmokeTourTest**: one pass over every main surface (see below) + log scan |
| **Deep** | daily (scheduled) | ~45–60 min (includes a wiped emulator boot) | smoke + lint + full instrumented suite + monkey stress + performance/memory snapshot + APK size |

## Run it

Smoke (fast, broad):

```bash
powershell -ExecutionPolicy Bypass -File tools\qa\run-qa.ps1 -Tier smoke
```

Deep (everything):

```bash
powershell -ExecutionPolicy Bypass -File tools\qa\run-qa.ps1 -Tier deep
```

The runner boots the emulator headless if needed. Results go to `qa-reports/<date>-<tier>/`, which is not committed:

| File | Contents |
|---|---|
| `report.md` | Pass/fail table, one line per step, with timings and metrics |
| `summary.json` | The same, machine-readable (used by the scheduled task) |
| `smoke-steps.txt` | Each smoke step with PASS or FAIL and the reason |
| `*.log`, `logcat-*.txt`, `monkey-seed-*.txt` | Raw evidence for triage |
| `crash-reports/` | Reports the app wrote on the device during the run |
| `qa-reports/latest-<tier>.md` | The newest report for each tier |
| `qa-reports/history.csv` | One line per run, for trends: failures, monkey issues, jank, p95, memory, APK size |

Exit code: `0` PASS, `1` FAIL, `2` could not run (no emulator, or the build is broken before any test runs).

## What the smoke tour checks
`app/src/androidTest/.../SmokeTourTest.kt` is a single test that walks through the steps below. A failed step is recorded and the tour continues, so one run shows *every* broken area.
1. Home renders the grid and the search bar.
2. The drawer opens and lists apps.
3. Search: the calculator answers `12*7`.
4. Drag a **search result** onto home. This is the Phase 1 stuck-drag regression.
5. Folder: create one by dropping an icon on another, open it, close it.
6. Remove an item, then **Undo** brings it back.
7. Edit mode → the **Customize** panel opens.
8. The widget picker opens.
9. Settings: search "subgrid", which lands on the row with the live preview.
10. All 14 settings pages open.
11. Back on home: nothing stuck (no drag, drawer closed).
12. No crash or error reports were written during the tour.

## What the deep tier adds
- **Lint** across all modules, with warnings as errors.
- **Full instrumented suite:** drag & drop, folders, widget bind dialog, drawer search, undo round-trip, database migrations.
- **Monkey:** 3 seeds × 8,000 random events confined to the launcher. Seeds change daily; each seed is in the report, so a crash can be replayed with `adb shell monkey -p com.anda.launcher -s <seed> …`.
- **Performance snapshot:**
  - A fixed gesture journey: page swipes and drawer open/close.
  - Records jank %, p95 frame time and memory (PSS).
  - The emulator numbers are for **trends only**. The real budgets (docs/RND.md) are measured on the Pixel with a release build.
- **APK size** (debug build).

## Daily scheduled run
- **What runs:** a Claude scheduled task (`daily-anda-qa`) runs the deep tier every morning and reads `summary.json`.
- **When everything passes:** it writes a one-line note.
- **When something fails:** it triages from the saved logs and writes `triage.md` next to the report. That file covers which step failed, the likely cause and file, and a suggested fix.
- **Limits:** it never edits app code, never installs anything on a real phone, and never pushes anything.

Most of the work happens in the PowerShell runner, which costs no tokens. Claude only reads the short summary, and opens the logs only when something failed.

## Test emulator: keep it healthy
An overloaded emulator makes every app, Anda included, miss its startup deadline. That produces fake freezes and tests that never start. On 2026-10-15 an emulator that had been running for an hour sat at 250% system CPU with Play Services busy in the background.

The runner guards against this:
- It renders with the host GPU (`-gpu host`): of every mode measured on this PC it is the only one that ever gets Settings under 30% jank (see "Emulator rendering speed" below).
- Deep runs start from a **wiped** emulator.
- Before any test, it waits for the system to finish booting and for the 1-minute load to stay below 6 for a full minute. If that doesn't happen, it restarts the emulator once, then reports `ERROR` instead of producing misleading results.
- Each monkey seed and the performance snapshot wait for a quiet system too. A seed that stops early records why (crash, ANR with its reason, or how many events ran), along with the load at the time.
- Emulator ANRs are judged against the load. An ANR at load 8–10 with "no focused window" means the system was slow to show a window, not that Anda hung. The main-thread trace (`adb shell dumpsys dropbox --print data_app_anr`) tells the two apart.
- It enlarges the log buffer to 16 MB; the 2 MB default loses lines within minutes.
- A run where zero tests ran, or where the tour recorded no steps, is a **failure**, never a pass.

Recommended (your end):
- Install a **lighter test image**: SDK Manager → SDK Platforms → *Android 14 (API 34)* → **Automated Test Device (ATD)** x86_64. Create the AVD `Anda_ATD_34` with 4 GB RAM. ATD images leave out Play Store and most background services, so they boot faster and are far less noisy.
- Then run with `-Avd Anda_ATD_34`, or change the default in `tools/qa/run-qa.ps1`.
- The PC needs about 6 GB free for the emulator plus Gradle. Close Android Studio and other heavy apps before the 06:00 run, or leave the PC idle overnight.

### Emulator rendering speed (measured 2026-10-20)
Question: does a different `-gpu` mode fix the ~7 fps seen on 2026-10-16? Short answer: **no mode does, and on a freshly wiped emulator nothing tested reaches 30% jank.** Treat emulator jank, frame times and fling velocity as non-gating (the deep tier already labels them "trend only"), and judge gestures and performance on the Pixel.

Setup: PC with an Intel i7-9750H, NVIDIA GTX 1650 (picked by `-gpu host`) and Intel UHD 630, 16 GB RAM; emulator 37.2.12 on WHPX; AVD `C1_Pixel_9_API_37` (4 vCPU, 4 GB). Control journey: the system Settings app, `gfxinfo` reset, 6 vertical swipes (300 ms each, 700 ms apart), then jank % and frame times. The guest was settled first (1-minute load below 6, or below 2 where noted).

| Mode (`-gpu`) | Emulator state | Jank | p50 / p95 | Guest load after journey |
|---|---|---|---|---|
| `host` | wiped, as the deep tier runs it (8 journeys over 28 min; the running QA emulator itself gave 94%) | 59–94% (median 89%) | 44–81 / 69–150 ms | 0.4–13 |
| `host` | existing data, cold boot, load below 2 (5 journeys, one boot) | 22–34% (median 29%) | 30–34 / 48–53 ms | 5–21 |
| `host` | existing data, cold boot, load below 6 (14 journeys, 2 boots) | 37–74% (median 60%) | 34–40 / 57–81 ms | 0.2–4 |
| `auto` | existing data (settle gate missed, load 8.9) | 91% | 77 / 133 ms | 9.4 |
| `swangle` | existing data (5 journeys) | 82–87% | 65–77 / 117–150 ms | 0.9–4.7 |
| `swiftshader` | existing data | 93% | 89 / 400 ms | 5.3 |
| `lavapipe` | existing data | 89% | 81 / 200 ms | 1.9 |
| `software` | existing data (settle gate missed, load 4.1) | 97% | 85 / 400 ms | 3.4 |

Notes:
- `angle_indirect` and `swiftshader_indirect` are no longer valid in this emulator version (it logs "not valid" and falls back to `auto`), so they could not be measured.
- The software renderers are always 80–97% jank, so they are no use as a fallback. The guest load was under 6 at the start of every journey in every mode that settled (the "after journey" column is the 1-minute average right after the swipes, which `input swipe` itself pushes up), so load is not the limit.
- The "GPU Context timeout" and 4950 ms GPU percentile in `gfxinfo` appear in every mode, software ones included. It is a fence placeholder, not a real GPU time; ignore it.
- Things that did **not** help on a wiped emulator: 6 GB guest RAM (80–88%), compiling Settings with `cmd package compile -m speed` (79–82%), running `cmd package bg-dexopt-job` (92–95% while it runs), the Vulkan HWUI renderer `debug.hwui.renderer=skiavk` (81–91% against 87–93% with GL on the same boot).
- A wiped emulator stays slow for at least 28 minutes even with the guest idle, so waiting longer in `Wait-Healthy` will not fix it. Existing-data boots vary from 22% to 74% between boots, so one journey proves nothing; use several and compare medians.
- Not tested: an ATD image (none is installed; see the recommendation above), and forcing the NVIDIA GPU to its maximum-performance power state (it idled at P8, 300 MHz of 2100, while attached) via the NVIDIA control panel for `qemu-system-x86_64-headless.exe`. Both are the next things to try.

## Manual checks (not automated)
Real-phone checks before a release are in `docs/release-plan.md` (Phase 6, manual QA checklist):
- feel, readability on your own wallpaper and real notifications;
- work profile, folding and TalkBack.
