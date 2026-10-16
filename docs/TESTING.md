# Testing Anda Launcher

Three tiers, cheapest first. Everything destructive runs on the **emulator** (`C1_Pixel_9_API_37`), never on a real phone: the runner refuses any non-emulator device.

| Tier | When | Time | What it covers |
|---|---|---|---|
| **Unit** | every change (`gradlew check`) | ~3 min | settings safety, backup reader, crash-loop rule, drawer/search logic, grid solver; lint with warnings as errors |
| **Smoke** | daily, or after any feature change | ~8 min | unit tests + **SmokeTourTest**: one pass over every main surface (see below) + log scan |
| **Deep** | daily (scheduled) | ~45 min | smoke + lint + full instrumented suite + monkey stress + performance/memory snapshot + APK size |

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
- It renders with the host GPU, because the AVD default software renderer (`swiftshader`) alone can saturate the CPU.
- Deep runs start from a **wiped** emulator.
- Before any test, it waits for the 1-minute load to fall below 6. If it doesn't, it restarts the emulator once, then reports `ERROR` instead of producing misleading results.
- It enlarges the log buffer to 16 MB; the 2 MB default loses lines within minutes.
- A run where zero tests ran, or where the tour recorded no steps, is a **failure**, never a pass.

Recommended (your end):
- Install a **lighter test image**: SDK Manager → SDK Platforms → *Android 14 (API 34)* → **Automated Test Device (ATD)** x86_64. Create the AVD `Anda_ATD_34` with 4 GB RAM. ATD images leave out Play Store and most background services, so they boot faster and are far less noisy.
- Then run with `-Avd Anda_ATD_34`, or change the default in `tools/qa/run-qa.ps1`.
- The PC needs about 6 GB free for the emulator plus Gradle. Close Android Studio and other heavy apps before the 06:00 run, or leave the PC idle overnight.

## Manual checks (not automated)
Real-phone checks before a release are in `docs/release-plan.md` (Phase 6, manual QA checklist):
- feel, readability on your own wallpaper and real notifications;
- work profile, folding and TalkBack.
