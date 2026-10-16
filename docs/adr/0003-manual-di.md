# ADR 0003 — Manual dependency injection

**Context.** Launchers are killed and cold-started often, so startup cost matters. The object
graph is small (≈ 10 singletons).

**Decision.** A single `AppGraph` created by `LauncherApplication`, with every member `by lazy`.
Features receive dependencies through constructors or the `LocalLauncherGraph` composition local
(interface `LauncherGraph` in `core:ui`).

**Consequences.** Zero reflection or codegen and no extra startup work. Wiring is explicit. If the
graph grows past ~30 bindings, revisit (Hilt / Metro).
