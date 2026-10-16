# ADR 0001 — Jetpack Compose for all UI

**Context.** Performance is the top priority. AOSP Launcher3 uses custom Views; modern Compose
launchers ship smooth 120 Hz UIs when state reads are deferred and layouts are custom.

**Decision.** Compose everywhere. The workspace grid is a custom `Layout` keyed by cell rect.
Drag offsets, page scroll and transition effects are read in `graphicsLayer {}` / `offset {}`
lambdas so they only trigger the draw/layout phase. `AppWidgetHostView` is embedded with
`AndroidView`. Baseline Profiles are shipped in the APK.

**Consequences.** One UI toolkit, fast iteration on customisation. If macrobenchmarks miss the
budgets for the workspace, the fallback is a custom `ViewGroup` for the page grid only, behind
the same state holder.
