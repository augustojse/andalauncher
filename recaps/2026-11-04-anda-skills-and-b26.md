# Session Recap: Anda project skills, tested on the B26 improvement
**Date:** 2026-11-04 · **Scope:** whole session · **Status:** Blocked (waiting on a decision about the idle nightly run)

_Supersedes `recaps/2026-11-03-anda-skills-and-b26.md`, which was written before the emulator was killed and the nightly's smoke result came in._

## Summary
Goal: turn the workflows repeated across 40+ earlier sessions into project skills, test them on a real improvement (B26, hide shortcuts per app), and push the skills to `main` if they all work. Six skills plus `CLAUDE.md` are committed (`6bc64fe`), and the 0.7.0 plan and intake card are committed (`b26f887`). B26 code, `gradlew check`, the emulator and phone tests and the push are **not done**: the nightly push (PID 23492) has been idle since 23:54 and still owns this checkout, so I have not edited sources.

## Work Completed
- **Six skills and `CLAUDE.md` created** from the session history and committed as `6bc64fe` on `chore/project-skills`.
- **Static validation** (frontmatter, referenced paths, read-only dry runs) found and fixed four defects: the pre-flight query matched its own shell; the checklist wrongly asked for a new Activity per settings screen; one unverified claim in `anda-phone-install`; a wrong session count in the backlog draft.
- **`anda-plan-release` tested, passed:** `docs/plans/0.7.0.md` and `docs/research/006-shortcut-exclusions.md`.
- **`anda-commit-scoped` tested, simple case passed:** `b26f887` holds exactly those two files; `docs/plans/0.6.0.md` in the same directory stayed out.
- **`anda-build-verify` pre-flight tested, passed:** it saw the live nightly and the stale 27 Oct process.
- **Drafted** `## D. Workflow skills` (S1-S6) in `inspiration/improvements.md`.
- **Hung emulator killed, with your approval:** `adb emu kill` timed out, so I force-stopped only PIDs 27292 and 29740 (up 25 hours). The stuck `adb getprop` call returned and the nightly started a fresh emulator itself (23:36:20).
- **Nightly's QA smoke finished: PASS** at 23:53:59 (44.3 min, mostly the emulator wait): 147 unit tests, 12 of 12 smoke-tour steps, log scan clean. This covers the 0.6.0 code on this branch, with your uncommitted `tools/qa` edits in the tree.

## Artifacts & Files
| Item | Type | Location | Status |
|---|---|---|---|
| Six skills + references, `CLAUDE.md` | skills/file | `.claude/skills/anda-*/`, `CLAUDE.md` | committed (`6bc64fe`) |
| 0.7.0 plan, B26 intake card | files | `docs/plans/0.7.0.md`, `docs/research/006-shortcut-exclusions.md` | committed (`b26f887`) |
| Skill fixes from the nightly | modified | `anda-{build-verify,emulator-check,phone-install}/SKILL.md` | **uncommitted** |
| Recommended-skills section | modified | `inspiration/improvements.md` | **uncommitted**, mixed with your own edits |
| Branches | git | `chore/project-skills` (`6bc64fe`), `feat/0.7.0-shortcut-exclusions` (`b26f887`, checked out) | local only |
| Recaps | files | `recaps/2026-11-03-anda-skills-and-b26.md` (superseded), `recaps/2026-11-04-anda-skills-and-b26.md` | created, untracked |
| Approved plan | file | `C:\Users\augus\.claude\plans\make-useful-project-agent-staged-lecun.md` | exists, outside the repo |
| Nightly smoke report | report | `qa-reports/2026-11-03_2309-smoke/` | created by the nightly (git-ignored) |

## Changes Log
- `.claude/skills/anda-build-verify/SKILL.md`: scheduled runs build this working tree, so no source or `CHANGELOG.md` edits, no own builds and no adb while one runs; worktree advice narrowed to other sessions' builds.
- `.claude/skills/anda-phone-install/SKILL.md`: pre-check, because the script restarts the adb server and would cut a running QA run.
- `.claude/skills/anda-emulator-check/SKILL.md`: an already-running emulator may belong to the nightly; check its age first.
- `inspiration/improvements.md`: section D plus two "where the ideas fit" rows.
- `Stop-Process -Force` on PIDs 27292 (`emulator.exe`) and 29740 (`qemu-system-x86_64-headless.exe`) after the graceful `emu kill` timed out. Nothing else was stopped.
- `git switch -c` twice and two commits (`6bc64fe`, `b26f887`). No push, amend, install or build.
- Background waiter `bk2j7f2d8` watches PID 23492 until about 00:51; the earlier waiter died with the session restart.

## Decisions & Rationale
- Skill set: 5 core + `anda-plan-release` + `CLAUDE.md`; dev-notes triage and a QA-report skill left out. *(you)*
- Light validation, no eval subagent runs. *(you)*
- Improvement = B26. *(you)*
- Push skills only to `main` via a temporary worktree; dangling references on `main` accepted. *(you)*
- One emulator boot plus the phone installer; push even though the phone's install-success path cannot be tested. *(you)*
- Kill the hung emulator. *(you)*
- Version 0.7.0, research number `006`, branch name. *(assistant default)*
- No source edits and no builds while the nightly owns the checkout, not even in a second worktree. *(assistant default)*

## Issues & Resolutions
- Pre-flight self-match, wrong new-screen checklist: **Resolved** (earlier fixes).
- Hung 25-hour-old emulator: **Resolved** (killed; the nightly booted a fresh one).
- **Open: the nightly is idle.** `nightly-push.ps1` (PID 23492) has had no child processes and no Gradle client since the smoke run ended at 23:53:59, and `qa-reports/nightly/2026-11-03_2309-build.log` was never created, so its `lint assembleRelease` step has not started. **Hypothesis, unverified:** it waits on an output pipe still held by the emulator that `run-qa.ps1` left running. Last night's run showed the same pattern (report written about 19 hours after the start).
- **Open:** the fresh emulator (PIDs 12544/21388) is still up and belongs to the nightly.
- **Open:** if the nightly wakes later it will run `gradlew lint assembleRelease` on whatever is on disk, so editing sources now is risky.
- **Open, not mine:** the stale 27 Oct `nightly-push.ps1` (PID 22820) and the untracked, empty `qa-nightly-run.log`.
- **Open:** the skill loader serves a session-start snapshot, so skill edits made now don't show on re-invocation. Python is not installed (a Node check stood in for skill-creator's validator), and `gh` is not installed.

## Next Steps
### Planned (you stated)
1. [ ] Build B26: `search.excludedShortcutApps` setting, provider filter, long-press menu item, settings screen, tests, `## 0.7.0` in `CHANGELOG.md`.
2. [ ] Test the remaining skills: `anda-feature-step`, the `gradlew check` gate, `anda-emulator-check` (one boot, then shut down), the `anda-phone-install` failure path, and the mixed-hunk commit of `improvements.md`.
3. [ ] Add any further skills the tests expose to section D of `improvements.md`.
4. [ ] If all skills work: commit the fixes, cherry-pick `6bc64fe` and the fixes onto `main` in a temporary worktree, and `git push origin main` (fast-forward, no force). This also pushes `e8262e7`.
### Unfinished
1. [ ] B26 is not started: `LauncherSettings.kt` has no `excludedShortcutApps` and `CHANGELOG.md` still tops out at 0.6.0.
2. [ ] The skill fixes and the `improvements.md` section are uncommitted.
### Recommended
1. [ ] **High** Decide what to do with the idle nightly (23492) and its emulator: wait, stop the nightly run, or accept the risk and continue. It is your scheduled run, so I will not stop it without an OK.
2. [ ] **Med** Fix the cause: a timeout on the emulator health wait in `run-qa.ps1`, and make the nightly not wait on the emulator's pipe. Both caused long stalls two nights running.
3. [ ] **Med** Commit the files the skills depend on, each on its own: `scripts/install-to-phone.ps1`, `docs/plans/0.6.0.md`, `docs/research/003-kvaesitso-next-update.md`.
4. [ ] **Med** Fast-forward `main` through the 0.2-0.6 stack so the skills' references resolve there, when you are ready to publish 0.6.0.
5. [ ] **Med** Add `qa-nightly-run.log` and `recaps/` to your commit plan: ignore the log in `.gitignore`, and decide whether recaps are tracked.
6. [ ] **Low** Re-run the skill tests in a fresh session once the fixes are committed.

## Context for Next Session
- **Environment:** Windows, PowerShell 5.1, `JAVA_HOME = C:\Program Files\Android\Android Studio\jbr`. Repo: `C:\Users\augus\Desktop\projects\c1-launcher`.
- **Refs:**
  - `origin/main` is `4cf4fb2`.
  - Local `main` is `e8262e7`, one commit ahead and unpushed.
  - The checked-out `feat/0.7.0-shortcut-exclusions` is `b26f887`, on top of `chore/project-skills` and the 0.6.0 stack.
- **Not mine, keep out of commits:**
  - Modified: `docs/TESTING.md`, `tools/qa/run-qa.ps1`, `tools/qa/ux.ps1`, and your hunks in `improvements.md`.
  - Untracked: `scripts/`, `docs/plans/0.6.0.md`, `docs/research/003-kvaesitso-next-update.md`, `qa-nightly-run.log`.
- **Scheduled runs:** the nightly starts around 23:09 and the deep QA around 06:00. Both build this tree and use the emulator. The pre-flight query is in `anda-build-verify` section 2.
- **B26 files to touch:**
  - `LauncherSettings.kt` and `SettingsSafety.kt` (`sanitized()`)
  - `ShortcutSearchProvider.kt`
  - the `ResultRow` menu in `SearchPage.kt`
  - `SearchResultActions`, implemented only in `MainActivity.kt`
  - `SettingsIndex.kt`, `SettingsRoot.kt` and `SearchSettingsScreen.kt`
  - a new `ShortcutAppsScreen.kt`, modelled on `HiddenAppsScreen.kt`

**Resume prompt:**
> Continue the Anda work on branch `feat/0.7.0-shortcut-exclusions` (`b26f887`): build B26 (hide shortcuts per app) from `docs/plans/0.7.0.md` and `docs/research/006-shortcut-exclusions.md` using the `anda-*` project skills, test the remaining skills (feature-step, the `gradlew check` gate, emulator-check once then shut down, phone-install failure path, commit-scoped mixed-hunk commit of `inspiration/improvements.md`), then commit the uncommitted skill fixes and, only if all skills work, cherry-pick `6bc64fe` plus the fixes onto `main` in a temporary worktree and push `main`. First check whether the nightly (PID 23492) is still idle with no build log; do not edit sources or run Gradle while a scheduled run is active, and ask before stopping it or its emulator.
