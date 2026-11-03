---
name: anda-feature-step
description: Build one feature, setting or step of a release plan in Anda Launcher, end to end. Use whenever the user says "start step N", "go ahead with B25", "implement this feature", "add a setting for ...", "add X to search/drawer/settings", or asks for any user-visible launcher change, even if they do not mention the changelog or tests. Covers the branch, the feature intake card, the new-setting checklist (settings model, index, screen, safety, backup, database), the CHANGELOG entry, the clean-room and performance rules, and the hand-off to the gate and commit skills.
---

# Build one feature step

Every feature in Anda ships the same way: behind a setting, with tests, with a CHANGELOG line, as one commit. The 0.3.0 to 0.6.0 releases were built step by step like this. The details below exist because each is checked by a test or a rule that fails late if forgotten.

## Flow

1. **Read the step.** Find its row in `docs/plans/<version>.md` and the sketch in the research note it cites (for example `docs/research/003-kvaesitso-next-update.md`). With no plan, read the matching idea in `inspiration/improvements.md`.
2. **Branch.** Work on `feat/<version>-<slug>`. If you are on `main`, create it first. Never push.
3. **Intake card.** The feature needs one (`docs/research/feature-template.md`: origin, user value, performance cost, API risk, settings key, conflicts, default). If the plan or research note already holds it, use that; otherwise write it before coding.
4. **Measure first if the plan says so.** Steps with a performance risk start with a measurement and a go/no-go number (as 0.6.0's transliteration did). If it misses the budget, stop and report; do not ship around it quietly.
5. **Implement**, following the rules below and the setting checklist in `references/new-setting-checklist.md` (read it when the step adds or changes a setting, a screen or a database table).
6. **Tests next to the code.** Pure logic gets a JVM unit test in the module's `src/test`; anything needing Android APIs or a database gets an `androidTest`. Add the settings-default line to `SettingsSerializationTest`.
7. **CHANGELOG.** See below.
8. **Gate.** Follow `anda-build-verify` (at minimum `gradlew check`). Say plainly what was not run on a device.
9. **Commit** through `anda-commit-scoped`: one commit per step, code + tests + CHANGELOG together.

## Rules that apply to every feature (docs/RND.md)

- **Clean-room.** Lawnchair and Kvaesitso are GPL. Study what they do from `inspiration/*.md`; write the implementation from Android docs and AOSP. Never copy code, strings, assets or data files. Google code that those projects carry (GestureNavContract, Material color utilities) comes from the upstream Apache-2.0 source, with attribution.
- **Main thread is sacred.** Package loading, icons, database and search work run on background dispatchers. Search providers that touch content providers are `slow = true` on `Dispatchers.IO`; app results keep the 8 ms budget for 300 apps.
- **Compose.** Immutable models (`kotlinx.collections.immutable`), fast-changing state read inside lambdas (`Modifier.offset { }`, `graphicsLayer { }`), no lazy grid for the workspace.
- **No new INTERNET permission.** Anything that starts another app uses `startActivity` with an intent.
- **Every feature has a setting** with a sensible default. User-visible text goes through the existing patterns (no hard-coded colours; use `MaterialTheme` tokens).

## CHANGELOG (the build reads it)

`CHANGELOG.md` is the single source of the version: the build takes `versionName` and `versionCode` from the top heading, Settings → About shows the list, `ChangelogTest` and the nightly push enforce it.

- First user-visible step of a release adds `## X.Y.0 — YYYY-MM-DD` at the top (today's date). Later steps of the same release add a bullet under it.
- Fixes only: bump Z (`X.Y.Z`). New features: bump Y and reset Z.
- One bullet per user-visible change, starting `New:`, `Changed:` or `Fixed:`, written for the person using the launcher and naming where to find it ("Settings → Search → ...").
- Refactors and internal work get no bullet.

## Finish

Summarise for the user: what changed (in their words), the settings key and where it lives, which gates ran and which were not run on a device, and the commit hash. If the step turned up follow-up work, name it.
