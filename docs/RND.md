# Anda Launcher — Research & Development Standards

These rules apply to every change. A milestone is not done until its quality gates pass.

## 1. Research

1. **Source hierarchy.** Android API reference / platform docs → AOSP Launcher3 source
   (Apache-2.0, read for behaviour) → other launchers' public docs and changelogs.
2. **Clean-room rule.** Lawnchair, Kvaesitso, Neo Launcher, etc. are GPL. Study *what* they do,
   never copy *how*. No GPL code or assets enter this repository.
3. **Time-boxed spikes.** Any uncertain platform capability gets a ≤ ½-day spike that produces
   `docs/research/NNN-topic.md` with: question, findings, links, decision, fallback.
4. **Feature intake card.** Every feature records: origin (Nova / Smart / other), user value,
   performance cost, API risk, settings key, and conflict/merge notes. Use
   `docs/research/feature-template.md`.
   *Conflict rule:* build the superset behind a setting; Nova's behaviour is the default.
5. **ADRs.** Architecture decisions live in `docs/adr/` (context, decision, consequences).

## 2. Engineering

### Performance budgets (acceptance criteria, measured on a physical Pixel 9)
| Metric | Budget |
|---|---|
| Cold start → first home frame (p50) | ≤ 350 ms |
| Return home from an app | ≤ 100 ms |
| Page swipe / drawer open / drawer scroll p95 frame time | ≤ 8.3 ms (120 Hz), jank < 1 % |
| Search update, 300 apps (off main thread) | < 8 ms |
| Idle PSS, 3 pages + 5 widgets | < 120 MB |
| Release APK | < 10 MB |

### Rules
- **Main thread is sacred.** Package loading, icon rendering, DB access and search run on
  background dispatchers. Debug builds run StrictMode (`detectAll`, `penaltyLog`); every logged
  violation from our code is fixed before a release. (`penaltyDeath` was tried in design and
  rejected: framework calls like `WallpaperManager` and resource loading read disk on the main
  thread and would kill the launcher.)
- **Compose discipline.** Immutable models (`kotlinx.collections.immutable`), stability declared in
  `compose-stability.conf`; read fast-changing state (drag offsets, scroll) inside lambda modifiers
  (`Modifier.offset { }`, `graphicsLayer { }`); the workspace grid is a custom `Layout`, never a
  lazy grid. Check compose compiler reports (`app/build/compose_reports`) for perf-sensitive code.
- **Minimal dependencies.** No image loading library, no reflection DI, no network library.
  v1 ships **without the INTERNET permission**.
- **Unidirectional data flow.** Repositories expose `Flow`; state holders expose `StateFlow`;
  UI sends events back. All settings flow from one typed `LauncherSettings`.
- **Never crash-loop.** Two crashes within 10 s of start → safe mode (default layout, no widgets,
  banner). Rolling automatic layout backups (last 7).
- **Every feature has a setting** with a sensible default.
- **Accessibility.** Content descriptions everywhere; custom accessibility actions replace
  drag & drop (move, add to folder, remove); honour font scale.

## 3. Quality gates (per milestone)
- `gradlew check` — unit tests + Android Lint (warnings as errors, policy in `lint.xml`).
- Room schema exported to `core/data/schemas`; one migration test per schema version.
- Instrumented Compose tests for the critical flows (drag, folder creation, widget add, search).
- Macrobenchmark vs. the budgets above for milestones touching home, drawer or startup:
  `gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`.
- Manual device checklist (README → Verification).
- Git: conventional commits, one per completed step; nothing is pushed without asking.
