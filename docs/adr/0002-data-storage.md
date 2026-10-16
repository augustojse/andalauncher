# ADR 0002 — Layout in Room, settings in a JSON DataStore

**Context.** The layout is relational (screens, items, folders) and must migrate safely.
Settings are a deep, frequently extended tree of options.

**Decision.**
- **Room** `launcher.db`: `items` (Launcher3-style rows: container, screen, cell, span, type,
  component, user, shortcut id, widget id, rank, overrides), `screens`, `app_overrides`,
  `drawer_tabs`, `launch_stats`. Positions are stored in **subgrid units** (`GridSpec.SUBGRID`
  per cell) so half-cell placement needs no schema change. Schemas are exported and migrations
  tested.
- **DataStore** holding one `@Serializable LauncherSettings` encoded as JSON. New fields get
  defaults, so adding a setting never needs a migration; unknown keys are ignored.

**Consequences.** One typed settings flow for the whole app. Backups are a JSON dump of both
stores plus custom icon files.
