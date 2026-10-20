---
name: anda-ux-round
description: Weekly simulated-user test round for Anda Launcher. Drives the Android emulator through every feature in priority order (docs/qa/feature-test-guide.md), finds user-visible GUI and behaviour problems, verifies each one before reporting it, then fixes the confirmed ones on a branch and re-verifies them. Use it when asked for a UX round, visual QA, a "simulation testing round", or GUI polish based on what the user sees.
---

# Anda UX round

## Goal

Each week, leave the launcher **visibly better for the person using it**. The round has three outputs:

1. a findings report: what a user would notice, ranked by severity, every item verified;
2. a small set of fixes, on a branch, for the confirmed issues a user notices most;
3. proof that each fix works: before and after screenshots, plus green `gradlew check` and smoke tier.

This round complements the deep QA run (docs/TESTING.md), which checks code paths. This one checks the GUI and what the user experiences.

## Hard rules

- **Emulator only.** Use the serial `emulator-NNNN` through `tools/qa/ux.ps1`, which sets ANDROID_SERIAL. A physical phone may be connected over Wi-Fi adb, and it is never touched. Never run bare `adb` or `gradlew install*` without `ANDROID_SERIAL` pointing at the emulator.
- **Verify, don't assume.** The prototype visuals are known to be rough, so a screenshot that looks wrong is only a lead. Follow §0 of the guide: settle, reproduce 2/2, cross-check the UI tree, and rule out emulator artifacts and settings. Only `CONFIRMED` issues get fixed.
- **Fix on a branch, never on main.**
  - Create the branch `ux/<yyyy-mm-dd>`, and commit there with clear messages.
  - Never push, merge, rebase main or force anything.
  - Never edit tests to make them pass.
  - Never touch `tools/release/` or `.claude/scheduled-tasks/`.
- **Keep fixes small and user-visible.** At most 5 fixes per round, each touching a few files. Prefer existing tokens and patterns (`MaterialTheme`, `WindowInsets`, `LocalLauncherSettings`) over new abstractions. A larger redesign becomes a recommendation in the report, not a fix.
- **Gate every fix.** After all the fixes, `gradlew check` and `tools/qa/run-qa.ps1 -Tier smoke` must both pass. If a fix breaks either, revert that fix's commit (`git revert`) and report it.
- Files are written only in `qa-reports/`, on the branch's source files, and in this skill's `learnings.md`.

## Workflow

Run from the project root, using PowerShell 5.1. `ux` below means `powershell -ExecutionPolicy Bypass -File tools\qa\ux.ps1`.

### 1. Prepare (about 5 min)

1. Run `git status --porcelain --untracked-files=no`.
   - **If any tracked file is modified**, someone (the user or another session) is mid-change in this checkout. Run in **findings-only mode**: do steps 2, 3 and 5, skip step 4, and say so in the report. Never stash, reset or commit someone else's changes.
   - Otherwise, run `git switch main`, then `git switch -c ux/<yyyy-mm-dd>`. The workflow files (this skill, `tools/qa/ux.ps1`, `docs/qa/`) may be untracked, and they carry over between branches.
2. Check that no other session or task is building in this checkout: no `run-qa.ps1` process, no `gradlew` started by someone else. Concurrent Gradle runs in one project fail with file-mode and cache errors, and a run that compiles while you edit sources tests half-finished code.
3. Read `.claude/skills/anda-ux-round/learnings.md` (known false positives, emulator quirks, past fixes), and the previous round's `qa-reports/*-ux/findings.md`, to see what was already fixed or deferred.
4. Run `ux prepare`. It does the following:
   - waits for any `run-qa.ps1` to release the emulator;
   - boots the emulator if needed and installs the debug build;
   - resets Anda to factory state (`pm clear`) and sets it as home;
   - resets the font, density and theme;
   - prints the round folder `OUT`.

   The first Home press then shows the Welcome card, which is case P-01. Tap **Done** (or test it) before moving on to P0.

### 2. Simulated-user pass (the main part)

Open `docs/qa/feature-test-guide.md` and walk through the cases **in tier order**: all of P0, then P1, then P2, then this week's P3 rotation. For each case:

1. Drive the case with `ux tap -Text "<label>"` (add `-Long` for a long press), `ux find -Text`, and raw `adb -s <serial> shell input …` for swipes, drags (`input draganddrop`) and keys.
2. Capture each meaningful state with `ux shot -Name <ID>-<a|b|…>`. After any `input keyevent` (Home, Back), tap empty space first, because key events leave Compose focus highlights that look like grey boxes (see learnings.md). Use `ux crop` to zoom into anything small.
3. **Look at the screenshot** with the Read tool on the PNG. Check it against the case's *Expect* and *Look closely at* lists and the §1 checklist. The audit lines printed by `ux shot` (TOUCH, OFFSCR, STATUS, NAVBAR, OVERLAP) are leads to check against the image. They are not findings.
4. Check the backend where the case says so: force-stop, then relaunch, and check that the state persisted. Then run `ux logs -Name <ID>`.
5. Record PASS, or a suspected issue, in a running notes list. For suspected issues, run the §0 verification right away, while the state is fresh.

Light and dark: run P0 and F-02, M-01 and W-01 a second time after `adb shell cmd uimode night yes`. Then switch back with `night no`.

Token economy:
- Use `ux find` and the audit text first, and open the PNG when the case needs eyes on it.
- Downscale large shots by reading them, not by re-capturing.
- Don't re-read the guide for each case. Read it once.

### 3. Findings report

Write `qa-reports/<stamp>-ux/findings.md`:
- a header with the build, emulator and date, and the cases run with their pass/fail counts per tier;
- one entry per issue, in the guide's §4 format, sorted by severity and then by tier;
- a separate list of `UNCONFIRMED`, `EMULATOR` and `BY DESIGN` items, each with one line. This shows the verification work, and it keeps them out of the fix list.

### 4. Fix (confirmed, user-visible, highest value first)

Choose fixes by the score **severity × tier weight**:
- severity S1=4, S2=3, S3=2, S4=1;
- tier P0=4, P1=3, P2=2, P3=1.

Take the top items, up to 5, that a contained change can fix. For each one:

1. Find the source by searching the composable that draws it. The guide's case IDs map to these areas:
   - H and K: `feature/home`;
   - D: `feature/drawer`;
   - S: `feature/search`;
   - M: `core/ui/menu`;
   - F: `feature/home/FolderOverlay.kt`;
   - W: `feature/home/WidgetPicker.kt` and `ResizeFrame.kt`;
   - ST: `feature/settings`;
   - theme issues: `core/ui/theme`.
2. Before editing, read `docs/release-plan.md` Phase 3. If the fix matches a planned token or design (gutters 16 dp, scrim 0.32, motion springs), use that value, so the fix moves toward the plan instead of away from it.
3. Make the change. Run `gradlew :app:installDebug` with ANDROID_SERIAL set, or simply run `ux prepare -NoInstall` after a manual install.
4. **Re-verify on the emulator.** Repeat the exact case steps and take `ux shot -Name <ID>-after`. The fix counts only if the after shot shows the issue gone, *and* nothing else on that screen got worse.
5. Commit, with one commit per fix: `fix(ui): <what the user sees> (<case ID>)`.

When all fixes are in, run:

```
.\gradlew.bat check
powershell -ExecutionPolicy Bypass -File tools\qa\run-qa.ps1 -Tier smoke
```

If either fails because of a fix, revert that fix's commit and note it.

### 5. Wrap up

1. Run `ux finish`. It restores the font, density and theme, and pulls crash reports.
2. Append a `## Fixes` section to findings.md. For each fix, give the case ID, the commit, and before/after shot paths. Then add the gate results, and **Recommendations**: confirmed issues too big for a round, mapped to release-plan items.
3. Update `learnings.md` with anything that will save the next round time: a new false positive, an emulator quirk, a reliable adb recipe for a case, or a fix pattern that worked. Keep it short and deduplicated.
4. Write a `ux-summary.json` in the round folder, containing `{date, branch, cases_run, pass, confirmed_by_severity, fixed, reverted, gates}`.
5. Leave the branch checked out with the commits in place for review. The final reply is under 15 lines: the counts, the top 3 user-visible issues, what was fixed, the branch name, and the report path.

## Reference: skills this workflow borrows from

Patterns are adapted, not vendored. Read these if a technique needs more depth:
- [android/skills](https://github.com/android/skills) (Google, Apache-2.0): `jetpack-compose` and `testing/testing-setup` give the official Compose and edge-to-edge guidance for fixes.
- [new-silvermoon/awesome-android-agent-skills](https://github.com/new-silvermoon/awesome-android-agent-skills) (Apache-2.0):
  - `android-emulator-skill`: semantic navigation from the UI tree, not pixel coordinates;
  - `android-accessibility`: 48 dp targets, 4.5:1 and 3:1 contrast;
  - `compose-ui`.
- [openai/plugins android-emulator-qa](https://github.com/openai/plugins/tree/main/plugins/test-android-apps): tap centres from `uiautomator dump`, one evidence file per step, clear logcat before each repro, start edge swipes 150–200 px in.
- [lukas-hinterleitner/visual-app-skills](https://github.com/lukas-hinterleitner/visual-app-skills) (Apache-2.0):
  - wait for the first real frame before capturing;
  - scope logcat to the app's pid;
  - verdicts are backed by screenshot evidence.
