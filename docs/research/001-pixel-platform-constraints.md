# 001 — What a third-party launcher can and cannot do on Pixel (Android 14+)

**Question.** Which Pixel Launcher behaviours can Anda reproduce, and with which APIs?

| Area | Findings | Decision | Fallback |
|---|---|---|---|
| Recents / gesture-nav animations | Recents (quickstep) is provided by the system's recents component, which is Pixel Launcher. A third-party home gets the framework's stock fallback animations for swipe-home. | Accept. Use `ActivityOptions.makeScaleUpAnimation` / clip-reveal from the icon bounds for app launch. | — |
| Google Discover (−1 screen) | The Google app's launcher-overlay service verifies the caller's signature. | Not committed; later spike. | Swipe-right gesture can open any app (e.g. Google app). |
| At a Glance / Smartspace | Smartspace targets come from a system service only available to the privileged launcher. | Own glance: date, next alarm, battery/charging, next calendar event (opt-in READ_CALENDAR). | — |
| Private Space (Android 15+) | Hidden profiles require the HOME role plus `ACCESS_HIDDEN_PROFILES`, which is role/privileged-gated. | Spike in M5. | Work profile fully supported via `LauncherApps` profiles. |
| Lock screen / shade / recents gestures | No public API. `AccessibilityService.performGlobalAction` covers LOCK_SCREEN, NOTIFICATIONS, QUICK_SETTINGS, RECENTS. | Opt-in accessibility service (sideload distribution allows it). | Shade: `StatusBarManager.expandNotificationsPanel()` via reflection + `EXPAND_STATUS_BAR`. |
| Deep shortcuts / pinning | `LauncherApps.hasShortcutHostPermission()` is true only for the default home app. | Onboarding requests `RoleManager.ROLE_HOME`. | Menu hides shortcuts when not default. |
| Notification badges | `NotificationListenerService` (user grants in Settings). | Opt-in; dots or counts. | Dots off. |
| Usage-based suggestions | `UsageStatsManager` needs a special permission. | Use our own launch statistics (no permission). | — |

**Links.** developer.android.com: LauncherApps, AppWidgetHost, RoleManager, AccessibilityService,
NotificationListenerService; AOSP `packages/apps/Launcher3`.
