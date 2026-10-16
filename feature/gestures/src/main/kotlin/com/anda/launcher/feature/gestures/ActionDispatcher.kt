package com.anda.launcher.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import com.anda.launcher.core.apps.AppLauncher
import com.anda.launcher.core.model.LauncherAction

/** UI-level actions the dispatcher delegates back to the launcher activity. */
interface LauncherNavigator {
    fun openDrawer()
    fun openSearch()
    fun openSettings()
    fun enterEditMode()
    fun toggleDock()
    fun goToDefaultPage()

    /** Called when an action needs the accessibility service and it's off. */
    fun requestAccessibility()
}

/** Executes any [LauncherAction] (ADR 0004). */
class ActionDispatcher(
    private val context: Context,
    private val launcher: AppLauncher,
    private val navigator: LauncherNavigator,
) {
    fun dispatch(action: LauncherAction, sourceBounds: Rect? = null) {
        when (action) {
            LauncherAction.None, LauncherAction.OpenFolder -> Unit
            LauncherAction.OpenDrawer -> navigator.openDrawer()
            LauncherAction.OpenSearch -> navigator.openSearch()
            LauncherAction.OpenLauncherSettings -> navigator.openSettings()
            LauncherAction.EditMode -> navigator.enterEditMode()
            LauncherAction.ToggleDock -> navigator.toggleDock()
            LauncherAction.GoToDefaultPage -> navigator.goToDefaultPage()
            LauncherAction.ExpandNotifications -> if (!SystemActions.expandNotifications(context)) navigator.requestAccessibility()
            LauncherAction.ExpandQuickSettings -> if (!SystemActions.expandQuickSettings(context)) navigator.requestAccessibility()
            LauncherAction.LockScreen -> globalOrPrompt(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            LauncherAction.Recents -> globalOrPrompt(AccessibilityService.GLOBAL_ACTION_RECENTS)
            LauncherAction.TakeScreenshot -> globalOrPrompt(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            LauncherAction.PowerMenu -> globalOrPrompt(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            is LauncherAction.LaunchApp -> launcher.launch(action.key, sourceBounds)
            is LauncherAction.LaunchShortcut ->
                launcher.launchShortcut(action.packageName, action.shortcutId, action.user, sourceBounds)
        }
    }

    private fun globalOrPrompt(action: Int) {
        if (!SystemActions.global(action)) navigator.requestAccessibility()
    }
}

/** Human-readable action names for settings pickers. */
fun LauncherAction.displayName(): String = when (this) {
    LauncherAction.None -> "Do nothing"
    LauncherAction.OpenDrawer -> "Open app drawer"
    LauncherAction.OpenSearch -> "Search"
    LauncherAction.ExpandNotifications -> "Notifications"
    LauncherAction.ExpandQuickSettings -> "Quick settings"
    LauncherAction.LockScreen -> "Lock screen"
    LauncherAction.Recents -> "Recent apps"
    LauncherAction.TakeScreenshot -> "Screenshot"
    LauncherAction.PowerMenu -> "Power menu"
    LauncherAction.EditMode -> "Edit home screen"
    LauncherAction.ToggleDock -> "Show / hide dock"
    LauncherAction.GoToDefaultPage -> "Go to default page"
    LauncherAction.OpenLauncherSettings -> "Launcher settings"
    LauncherAction.OpenFolder -> "Open folder"
    is LauncherAction.LaunchApp -> "Open app"
    is LauncherAction.LaunchShortcut -> "Shortcut: $label"
}
