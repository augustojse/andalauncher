---
name: anda-commit-scoped
description: Commit only the work done in this conversation, in a checkout that other Claude sessions and the user are also editing. Use whenever the user says "commit the ...", "commit on a new branch", "leave my edits out", "only those files", or asks to commit a finished feature step, even if they do not mention other changes. Stages by explicit path, handles files with mixed hunks, writes the project's commit message format, and never pushes.
---

# Commit only this work

The Anda checkout is shared. The user keeps uncommitted edits (docs, `tools/qa`, plans), and other sessions leave files behind. Four times the user has had to say "commit this, but leave my edits out". A blanket `git add -A` would sweep someone else's half-finished work into the commit, and stashing or resetting would destroy it. So stage precisely, and leave everything else exactly as found.

## Steps

1. **Look first.** Run `git status --short` and `git branch --show-current`.
2. **Sort the files.** *Mine* = files this conversation created or edited (your Edit/Write calls are the record). *Foreign* = every other modified or untracked path. When unsure whether a file is yours, run `git diff <file>` and compare with what you changed; if still unsure, treat it as foreign and ask.
3. **Branch.** On `main`, create `feat/<version>-<slug>` (or `fix/...`) first. Otherwise commit on the current branch.
4. **Stage by explicit path**: `git add path1 path2 ...`. Never `git add -A`, `git add .`, `git commit -a`, `git stash`, `git reset`, or `git checkout -- <file>` on anything you did not write.
5. **Mixed files.** If one file holds your hunks and someone else's, stage a blob that contains only your changes. Interactive `git add -p` does not work in this environment, and trimming a patch for `git apply --cached --unidiff-zero` is a trap: with zero context lines git places each hunk by its **new-side** line number (`+N`), so deleting an earlier hunk without subtracting its length from every later `+N` lands your hunks that many lines late (measured 2026-11-04: a section went in 13 lines too low, and the line counts still looked right). Do this instead:
   1. Build the intended file in the scratchpad: the working file minus the other person's lines. Find their lines from `git diff -U0 <file>` (the `+N,M` ranges are positions in the working file) and assert each dropped line's text matches before removing it.
   2. Stage it without touching the working tree: `blob=$(git hash-object -w <intended>)`, then `git update-index --cacheinfo <mode>,$blob,<file>` (mode from `git ls-files -s <file>`).
   3. Prove the split, not just the counts: `git diff --cached -U0 <file>` must show only your changes in the right places, and `git diff --numstat <file>` (working tree against the index) must show exactly the other person's added lines and `0` deleted. Any deleted line means something moved.
6. **Check the index**: `git diff --cached --stat`. Every line must be yours. App behaviour changes include the `CHANGELOG.md` entry (see `anda-feature-step`).
7. **Commit** with a heredoc message (format below).
8. **Show what was left**: run `git status --short` again and list the remaining files as "not included".

## Message format

```
type(scope): what the user gets, in plain words

Body: behaviour, then the settings key and where it lives in Settings,
then the tests added. One short paragraph each, no file lists.

Step N of the X.Y.Z plan (ID).   <- only when it belongs to a plan
```

Types used in this repo: `feat`, `fix`, `perf`, `refactor`, `docs`, `qa`, `release`. One commit per completed step. End the message with the attribution line your session instructions specify.

## Never

- Push, amend, rebase or force. Nothing leaves the machine without the user asking (`docs/RND.md` section 3).
- Commit `qa-reports/`, `.claude/settings.local.json`, `dev-notes/`, `inspiration/repos/` or `local.properties`. They are git-ignored; if one shows up as tracked, say so rather than fixing it silently.
- Fold in "while I'm here" edits. A tidy commit is one the user can revert cleanly.

## Files the project depends on but git does not track

Check `git status` for untracked files that skills or scripts rely on (for example `scripts/install-to-phone.ps1`, `docs/plans/`). Do not add them to a feature commit. Mention them in your summary and offer a separate commit.
