package com.anda.launcher.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything a gesture, icon swipe, folder cover or button can do (ADR 0004).
 */
@Serializable
sealed interface LauncherAction {
    @Serializable @SerialName("none")
    data object None : LauncherAction

    @Serializable @SerialName("drawer")
    data object OpenDrawer : LauncherAction

    @Serializable @SerialName("notifications")
    data object ExpandNotifications : LauncherAction

    @Serializable @SerialName("quick_settings")
    data object ExpandQuickSettings : LauncherAction

    @Serializable @SerialName("lock")
    data object LockScreen : LauncherAction

    @Serializable @SerialName("recents")
    data object Recents : LauncherAction

    @Serializable @SerialName("search")
    data object OpenSearch : LauncherAction

    @Serializable @SerialName("settings")
    data object OpenLauncherSettings : LauncherAction

    @Serializable @SerialName("edit")
    data object EditMode : LauncherAction

    @Serializable @SerialName("toggle_dock")
    data object ToggleDock : LauncherAction

    @Serializable @SerialName("default_page")
    data object GoToDefaultPage : LauncherAction

    @Serializable @SerialName("screenshot")
    data object TakeScreenshot : LauncherAction

    @Serializable @SerialName("power_menu")
    data object PowerMenu : LauncherAction

    @Serializable @SerialName("open_folder")
    data object OpenFolder : LauncherAction

    @Serializable @SerialName("app")
    data class LaunchApp(val key: ComponentKey) : LauncherAction

    @Serializable @SerialName("shortcut")
    data class LaunchShortcut(
        val packageName: String,
        val shortcutId: String,
        val user: Long,
        val label: String,
    ) : LauncherAction

    companion object {
        /** Actions that need no extra data, in the order shown in pickers. */
        val simpleActions: List<LauncherAction> = listOf(
            None, OpenDrawer, OpenSearch, ExpandNotifications, ExpandQuickSettings, LockScreen,
            Recents, TakeScreenshot, PowerMenu, EditMode, ToggleDock, GoToDefaultPage,
            OpenLauncherSettings,
        )
    }
}

/** Home-screen gestures (Nova "Gestures & inputs"). */
@Serializable
enum class Gesture {
    SwipeUp,
    SwipeDown,
    DoubleTap,
    TwoFingerSwipeUp,
    TwoFingerSwipeDown,
    PinchIn,
    PinchOut,
    HomePress,
}
