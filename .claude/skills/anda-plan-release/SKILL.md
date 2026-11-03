---
name: anda-plan-release
description: Plan the next Anda Launcher release (X.Y.0) from the inspiration backlog and write it as docs/plans/X.Y.0.md. Use whenever the user says "plan the next update", "what should the next version be", "plan the next release from improvements.md", "use the inspiration folder to develop the launcher", or asks which backlog items to build and in what order, even if they never say "plan". Produces a scoped, ordered, verifiable plan with feature intake cards and the clean-room rule; it writes no app code.
---

# Plan the next release

Each release so far (0.3.0 to 0.6.0) came from `inspiration/improvements.md`, a backlog of ideas from Lawnchair and Kvaesitso. The plan's job is to turn a few of those ideas into ordered steps that `anda-feature-step` can build one at a time, with the risky ones measured first and the riskiest built last. `docs/plans/0.6.0.md` is the model; copy its shape (see `references/plan-template.md`).

## Steps

1. **Read the state.** The top of `CHANGELOG.md` (current version), `inspiration/improvements.md` (ideas marked with a check mark are shipped; columns give priority P1-P3, effort S/M/L, API risk, and the Anda files touched), the previous plan in `docs/plans/`, and `docs/release-plan.md` for phases that are still open. Check `git log --oneline` and `git status` so you do not plan work that is already done or in flight.
2. **Choose a theme and 4-6 items.** Prefer P1, then dependencies that unlock later items, mixing sizes so the release is about 8-10 working days. Defer anything marked "needs a spike" unless the plan includes the spike. State what is deliberately left out and why.
3. **Order them.** Pure refactors first, self-contained items next, anything touching a database migration, backups, or the drawer/startup path last. Draw the dependency chain.
4. **Measure before building.** For any item with a performance risk, the plan's first sub-step is a measurement with a number and a fallback ("if it misses < 15 ms, ship only the precompute"). Time-boxed spikes (half a day or less) for platform unknowns end in a numbered research note with question, findings, decision and fallback (`docs/RND.md` section 1.3).
5. **Write intake cards.** Each item needs a feature intake card (`docs/research/feature-template.md`). Put them in one research note `docs/research/NNN-<topic>.md`. Find the next free number with `ls docs/research`; `002` is reserved for the release baseline and `004`/`005` for the 0.6.0 spikes, so check what is actually there.
6. **Fill the template.** Scope table (ID, feature, effort, settings key and default), order and why, rules for the release (branch `feat/X.Y.0-<slug>`, one conventional commit per step, CHANGELOG heading on the first user-visible step, performance and permission rules), verification gate, a changelog draft, and an "After X.Y.0" table so the user sees the road.
7. **Present, do not build.** Save `docs/plans/X.Y.0.md`, summarise the scope in a few lines, and ask for the go-ahead. Mark the plan's date. After approval the user will say "go ahead with step 0" and `anda-feature-step` takes over.

## Rules to carry into every plan

- **Clean-room** (`docs/RND.md` section 1.2): Kvaesitso is GPL-3.0, and some Lawnchair files carry GPL headers. The plan records what a feature does and where it lands in Anda; no code, strings, assets or data files are copied. Google code that those projects bundle (GestureNavContract, Material color utilities) is taken from upstream Apache-2.0 sources with attribution.
- Everything offline: no INTERNET permission, and no new permission without a reason stated in the intake card.
- **Verification gate** for the release always includes: `gradlew check`; Room migration and backup round-trip tests if data changed; a settings-file decode test from the previous version; a macrobenchmark on the Pixel if the drawer, home or startup changed; a UX round on the emulator (`anda-ux-round`); and an upgrade test that installs the previous version, builds a layout, then installs the new one.
- Update `inspiration/improvements.md` (check mark plus version) only when an item ships, not when it is planned.

## Output check

Before presenting: every item has an ID, effort, settings key and default; the order explains itself; nothing in "Scope" lacks a verification line; dates are absolute (not "next week").
