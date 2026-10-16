# ADR 0004 — One action system for gestures, swipe actions and buttons

**Context.** Nova has home gestures, per-icon swipe actions and dock/button actions. Smart
Launcher and Action Launcher have similar ideas ("covers", quick actions). Separate code paths
would drift apart.

**Decision.** A serializable sealed `LauncherAction` in `core:model` (open drawer, notifications,
quick settings, lock, recents, search, launch app, launch shortcut, settings, toggle dock,
edit mode, none). Every configurable trigger (gesture, icon swipe, folder tap/swipe, home-press)
maps to one `LauncherAction`. A single `ActionDispatcher` (feature:gestures) executes them. It
delegates UI actions to a `LauncherNavigator` interface implemented by the activity.

**Consequences.** Adding an action makes it available everywhere at once. Actions that need the
accessibility service degrade to a prompt that explains how to enable it.
