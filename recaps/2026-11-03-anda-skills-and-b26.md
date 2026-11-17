# Session Recap: Anda project skills, tested on the B26 improvement
**Date:** 2026-11-03 · **Scope:** whole session · **Status:** In progress

_Superseded by `recaps/2026-11-04-anda-skills-and-b26.md` (the emulator was killed and the nightly's smoke run passed since this was written)._

## Summary
Goal: turn the workflows repeated across 40+ earlier sessions into project skills, then exercise them on a real improvement (B26, hide shortcuts per app) and publish the skills to `main` if they all work. Six skills plus a root `CLAUDE.md` exist and are committed (`6bc64fe`); the 0.7.0 plan and intake card are committed (`b26f887`). The B26 code, `gradlew check`, the emulator and phone tests and the push to `main` have **not** happened: a scheduled nightly run is building this checkout and appears stuck on a hung emulator.

## Work Completed
- **Skills created** from the session history (prompts, commands, errors): `anda-feature-step`, `anda-build-verify`, `anda-commit-scoped`, `anda-emulator-check`, `anda-phone-install`, `anda-plan-release`, plus `CLAUDE.md`. Committed as `6bc64fe` on `chore/project-skills`.
- **Static validation** of all skills: frontmatter (name = folder, description ≤ 1024 chars), every referenced path, read-only dry runs. It found and fixed four defects: the build pre-flight query matched its own shell; the new-screen checklist wrongly asked for a new Activity; an unverified claim in `anda-phone-install`; and a wrong count in the draft backlog text ("three sessions" was two sessions, three prompts).
- **`anda-plan-release` tested and passed:** produced `docs/plans/0.7.0.md` and `docs/research/006-shortcut-exclusions.md` (next free number, since 002/004/005 are reserved).
- **`anda-commit-scoped` tested, simple case passed:** committed `b26f887` with exactly those two files; `docs/plans/0.6.0.md` in the same untracked directory stayed out.
- **`anda-build-verify` pre-flight tested and passed:** correctly reported the live nightly and the stale 27 Oct process, and exposed the need for scheduled-run guidance (see Changes Log).
- **Drafted** `## D. Workflow skills` (six recommended skills S1–S6, with evidence) in `inspiration/improvements.md`.

## Artifacts & Files
| Item | Type | Location | Status |
|---|---|---|---|
| Six skills + references | skill | `.claude/skills/anda-{feature-step,build-verify,commit-scoped,emulator-check,phone-install,plan-release}/` | committed (`6bc64fe`) |
| Project instructions | file | `CLAUDE.md` | committed (`6bc64fe`) |
| 0.7.0 plan | file | `docs/plans/0.7.0.md` | committed (`b26f887`) |
| B26 intake card | file | `docs/research/006-shortcut-exclusions.md` | committed (`b26f887`) |
| Skill fixes from the nightly | modified | `.claude/skills/anda-{build-verify,emulator-check,phone-install}/SKILL.md` | modified, **uncommitted** |
| Recommended-skills section | modified | `inspiration/improvements.md` (section D + 2 table rows) | modified, **uncommitted**, mixed with the user's own edits |
| Approved plan | file | `C:\Users\augus\.claude\plans\make-useful-project-agent-staged-lecun.md` | exists (outside the repo) |
| Branches | git | `chore/project-skills` (`6bc64fe`), `feat/0.7.0-shortcut-exclusions` (`b26f887`, checked out) | local only, nothing pushed |
| This recap | file | `recaps/2026-11-03-anda-skills-and-b26.md` | created, untracked |

## Changes Log
- `.claude/skills/anda-build-verify/SKILL.md`: added "scheduled runs build this same working tree" (nightly ≈ 23:09, deep ≈ 06:00): no source or `CHANGELOG.md` edits, no own Gradle build, no adb while one runs; narrowed the worktree advice to other *sessions'* builds.
- `.claude/skills/anda-phone-install/SKILL.md`: added a "before you run it" check; the script restarts the adb server and would cut a running QA run.
- `.claude/skills/anda-emulator-check/SKILL.md`: an emulator already running (the nightly leaves one up) is not necessarily yours; check its age first.
- `inspiration/improvements.md`: added section D and two "where the ideas fit" rows (the file also holds the user's earlier uncommitted B22–B34 and C15 edits).
- `git switch -c chore/project-skills`, `git switch -c feat/0.7.0-shortcut-exclusions`; two commits (`6bc64fe`, `b26f887`). No push, no amend, no installs, no builds.
- A background waiter (`bqhojh2ee`) on the nightly's process was started and then stopped when the session restarted; there is no live waiter now.

## Decisions & Rationale
- Scope = 5 core skills + `anda-plan-release` + `CLAUDE.md`; `dev-notes-triage` and a QA-report skill left out (flow unused / already covered by scheduled prompts). *(decided by: user)*
- Light validation, no eval subagent runs (they would trigger real builds and commits). *(decided by: user)*
- Improvement = B26 "Exclude shortcut apps". *(decided by: user)*
- Push scope = skills only to `main`, via a temporary worktree and cherry-pick; accepted that references to `CHANGELOG.md`, `ChangelogTest`, `Favorites.kt` dangle on `main` until the 0.2–0.6 branches merge. *(decided by: user)*
- Emulator once plus the phone installer; push even though the phone's install-success path cannot be tested. *(decided by: user)*
- Version 0.7.0 (new feature bumps Y), later roadmap rows shift by one; research note number 006; branch name. *(decided by: assistant default)*
- Stopped before any source edit while the nightly runs, and did not build in a second worktree, because extra load can fail the nightly's emulator health gate. *(decided by: assistant default)*

## Issues & Resolutions
- Pre-flight query matched its own shell: **Resolved** (exclude `$PID`, match `GradleWrapperMain` / QA scripts only).
- Checklist said a new settings screen needs an Activity: **Resolved** (it is a `Screen` enum entry plus a route in `SettingsRoot.kt`; manifest only for `<queries>`).
- Skill tool serves a session-start snapshot: edits made mid-session are on disk but **not** shown on re-invocation. **Open** (affects re-testing the fixes).
- Nightly `nightly-push.ps1` (PID 23492, since 23:09:41) is still running at 23:30. Its child `adb -s emulator-5554 shell getprop sys.boot_completed` (PID 12180, since 23:09:43) has not returned in 21 minutes; the emulator (PIDs 27292/29740) is 25 hours old. The same pattern last night: the run started 11-02 23:09 and wrote its report at 11-03 18:27. **Open**, probably a hung emulator (unconfirmed).
- **Update 23:37:** the user approved killing the hung emulator. `emu kill` timed out (hung), so I force-stopped only PIDs 27292 and 29740. The stuck adb call returned, and the nightly started a fresh emulator itself at 23:36:20 (PIDs 12544/21388) and is continuing. That emulator is the nightly's: leave it alone. Hold on source edits and builds until PID 23492 exits (a new background waiter is watching it).
- Stale `nightly-push.ps1` from 27 Oct (PID 22820, no children): **Open**, not mine, left running.
- `qa-nightly-run.log` (0 bytes, untracked, not in `.gitignore`): **Open**, kept out of commits.
- Python is not installed (only the Store shim), so skill-creator's validator and description tuning could not run; a Node check was used instead. **Open**.
- `gh` is not installed; pushing relies on `git push` with the Windows credential manager. **Open**, untested.

## Next Steps
### Planned (user-stated)
1. [ ] Build B26 per the approved plan: setting `search.excludedShortcutApps`, `ShortcutSearchProvider` filter, long-press menu item, `Screen.ShortcutApps` + settings screen, tests, `CHANGELOG.md` `## 0.7.0`.
2. [ ] Test the remaining skills: `anda-feature-step` (checklist completeness), `anda-build-verify` gate (`gradlew check`), `anda-emulator-check` (one boot, then shut down), `anda-phone-install` (expect "Phone not reachable"), `anda-commit-scoped` mixed-hunk commit of `improvements.md` (`-U0` patch + `git apply --cached --unidiff-zero`).
3. [ ] Recommend any further skills the tests expose (add to section D of `improvements.md`).
4. [ ] If all skills work: commit the skill fixes, then in a temporary worktree cherry-pick `6bc64fe` and the fix commits onto `main` and `git push origin main` (fast-forward, no force). This also pushes the unpushed `e8262e7` (UX round).
### Unfinished
1. [ ] B26 implementation: **not started** (no `excludedShortcutApps` in `LauncherSettings.kt`; `CHANGELOG.md` still tops out at 0.6.0). Stopped at "wait for the nightly to finish before touching sources".
2. [ ] The three skill-fix edits and the `improvements.md` section are uncommitted.
### Recommended
1. [ ] **High** Decide what to do about the stuck nightly and the 25-hour-old emulator. If confirmed hung, the `anda-emulator-check` recipe applies (kill with a timeout, then stop `emulator.exe` / `qemu-system-x86_64-headless.exe`). It is the user's scheduled run, so it needs their OK; until then nothing else can safely build here.
2. [ ] **Med** Commit the files the skills depend on but git does not track: `scripts/install-to-phone.ps1`, `docs/plans/0.6.0.md`, `docs/research/003-kvaesitso-next-update.md`, each on its own.
3. [ ] **Med** Fast-forward `main` through the 0.2–0.6 stack (it can fast-forward cleanly) so the skills' references resolve on `main`, once the user is ready to publish 0.6.0.
4. [ ] **Med** Add `qa-nightly-run.log` to `.gitignore`, and look into why the nightly takes ~19 hours when the emulator hangs (a timeout in `run-qa.ps1`'s health wait).
5. [ ] **Low** Re-run the skill tests in a fresh session after the fixes are committed, since this session serves the old skill text.

## Context for Next Session
- Windows, PowerShell 5.1; `JAVA_HOME = C:\Program Files\Android\Android Studio\jbr`, set in the same command as `gradlew`. Repo: `C:\Users\augus\Desktop\projects\c1-launcher`.
- Refs: `origin/main` = `4cf4fb2`; local `main` = `e8262e7` (1 ahead, unpushed); checked out `feat/0.7.0-shortcut-exclusions` = `b26f887`, which sits on `chore/project-skills` (`6bc64fe`) on the 0.6.0 stack. A second worktree `c1-launcher-ux` (`ux/2026-10-15`) pre-exists.
- Not mine, keep out of commits: `docs/TESTING.md`, `tools/qa/run-qa.ps1`, `tools/qa/ux.ps1` (modified); `scripts/`, `docs/plans/0.6.0.md`, `docs/research/003-kvaesitso-next-update.md`, `qa-nightly-run.log` (untracked); the user's own hunks in `inspiration/improvements.md`.
- Scheduled runs: nightly ≈ 23:09, deep QA ≈ 06:00; both build this working tree and use the emulator. Pre-flight query is in `anda-build-verify` §2.
- B26 files to touch: `core/model/.../LauncherSettings.kt` (+`SettingsSafety.kt` `sanitized()` cap), `feature/search/.../ShortcutSearchProvider.kt`, `SearchPage.kt` (`ResultRow` menu, ~line 246), `SearchUi.kt` (`SearchResultActions`), `app/.../MainActivity.kt` (its only implementer), `feature/settings/.../SettingsIndex.kt`, `SettingsRoot.kt`, `screens/SearchSettingsScreen.kt` (takes an `onQuickActions` callback today), new `screens/ShortcutAppsScreen.kt` modelled on `HiddenAppsScreen.kt`.

**Resume prompt:**
> Continue the Anda work on branch `feat/0.7.0-shortcut-exclusions` (`b26f887`): build B26 (hide shortcuts per app) from `docs/plans/0.7.0.md` and `docs/research/006-shortcut-exclusions.md` using the `anda-*` project skills, test the remaining skills (feature-step, build-verify gate, emulator-check once then shut down, phone-install failure path, commit-scoped mixed-hunk commit of `inspiration/improvements.md`), then commit the uncommitted skill fixes and, only if all skills work, cherry-pick `6bc64fe` plus the fixes onto `main` in a temporary worktree and push `main`. First check whether the 23:09 nightly (PID 23492) and its 25-hour-old emulator are still stuck; do not edit sources or run Gradle while a scheduled run is active, and ask before killing anything.
