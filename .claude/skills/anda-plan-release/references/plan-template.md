# Release plan template

Distilled from `docs/plans/0.6.0.md`. Replace every `<...>`. Keep it to about two screens; per-feature detail lives in the research note.

```markdown
# Anda <X.Y.0> — plan

_<YYYY-MM-DD> · from <previous version> (`<short hash>`) · sources: [improvements.md](../../inspiration/improvements.md), [research <NNN>](../research/<NNN-topic>.md)_

**Theme: "<three to five words>".** <Two sentences: what the release gives the user, and the constraints that hold (offline, no new permission).>

Per-feature sketches (behaviour, implementation, tests, perf) are in research <NNN>, § *<section>*. This plan adds the order, the dependencies, <any refactor up front>, and what runs in parallel.

## Scope

| # | ID | Feature | Effort | Settings key (default) |
|---|---|---|---|---|
| 0 | <A4> | <name> | S | — |
| 1 | <B27> | <name> | S | `<group.key>` (`<default>`) |

About <N> working days. Every item gets a feature intake card before it is built (`docs/research/feature-template.md`).

**Not in <X.Y.0>:** <items and the reason each waits>.

## Order and why

<ASCII dependency chain.>

0. **<ID> first.** <Why it goes first.>
1. **<ID>** <what it is; the measurement it starts with and the number it must hit; the fallback if it misses.>
...
N. **<ID> last**, because it is the riskiest: <migration / backup / hot path>.

## Rules for this release

- Branch `feat/<X.Y.0>-<slug>` from the current tip. One conventional commit per numbered step (RND.md §3); nothing is pushed without asking.
- `CHANGELOG.md` gets the `<X.Y.0>` heading on the first user-visible step, and one bullet per step after that.
- <Performance rules specific to this release: thread, budget, caps.>
- <Safety rules: validation in SettingsSafety, intent-only actions, limits.>

## Run in parallel: spikes for later releases (optional)

| Spike | Question | Output |
|---|---|---|
| <ID name> | <one question that a half-day on the Pixel answers> | `docs/research/<NNN-topic>.md` |

## Verification (release gate for <X.Y.0>)

- `gradlew check` green, including the new tests listed in research <NNN> for each feature, plus:
  - Room migration test <old> → <new> against `core/data/schemas/...` (if data changed).
  - Backup round-trip with the new data; the previous version's backup still restores.
  - `SettingsSerializationTest`: the previous version's settings file decodes with the new keys at their defaults.
- Instrumented: <the flows only a device can show>.
- Macrobenchmark on the Pixel if the drawer, home or startup changed: drawer open p95 ≤ 8.3 ms, cold start ≤ 350 ms.
- A UX round on the emulator (`anda-ux-round`) over <the areas touched>.
- Upgrade test: install <previous>, add a layout, install <X.Y.0> over it — layout, settings and icon exceptions survive.

## Changelog draft

```
## <X.Y.0> — YYYY-MM-DD
- New: <one line per feature, naming where to find it>
```

## After <X.Y.0>

| Version | Theme | Ideas |
|---|---|---|
| <next> | <theme> | <IDs and one-line descriptions> |
```
