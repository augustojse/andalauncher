# Inspiration

Ideas for Anda Launcher taken from other open-source launchers: codebase structure, features and
aesthetics. Each idea is written as *what* the other launcher does and *where it would land in
Anda*, never as code to copy.

| File | What's in it |
|---|---|
| [improvements.md](improvements.md) | **The backlog.** Every idea, sorted into structure / features / aesthetics, with priority, effort, API risk and the Anda files it touches. Start here. |
| [lawnchair.md](lawnchair.md) | Lawnchair: what it is, how it's built, where each idea lives in its source. |
| [kvaesitso.md](kvaesitso.md) | Kvaesitso: the same for Kvaesitso. |
| [fetch-repos.ps1](fetch-repos.ps1) | Clones or updates the source trees in `repos/`. |

## The source trees

`repos/` holds shallow clones of each project. It is **git-ignored**: the clones are for reading
only and never enter this repository.

| Project | Upstream | Snapshot studied |
|---|---|---|
| Lawnchair 16 | https://github.com/LawnchairLauncher/lawnchair | `c7e6b8d` (2026-10-21) |
| Kvaesitso | https://github.com/MM2-0/Kvaesitso | `b72ddb6` (2026-10-21) |

To get them on a new machine, or to refresh them:

```bash
powershell -ExecutionPolicy Bypass -File inspiration/fetch-repos.ps1
```

## Clean-room rule (docs/RND.md §1.2)

- **Kvaesitso** is GPL-3.0.
- **Lawnchair**'s root licence is Apache-2.0 (it is a Launcher3 fork), but some of its own files carry GPL-3.0 headers.

Treat both as **read-only references**. Study what they do and then build it from the Android
docs and AOSP. No code, assets, strings or data files (for example Lawnchair's `flowerpot`
category lists) are copied into Anda.

Some ideas are AOSP or Google code that these projects also carry, such as `GestureNavContract`
and the Material color utilities. Take those from the **upstream** Apache-2.0 source (AOSP
Launcher3, `google/material-color-utilities`), with attribution. Never take them from the copy
in these repos.

## Adding another project

1. Add it to the `$repos` list in `fetch-repos.ps1` and run the script.
2. Write `<name>.md` like the two existing profiles: what it is, licence, layout, and where each idea lives.
3. Add its ideas to `improvements.md` in the right section, and update the "Start here" list if one belongs there.
4. Before building an idea, write a feature intake card (`docs/research/feature-template.md`).
