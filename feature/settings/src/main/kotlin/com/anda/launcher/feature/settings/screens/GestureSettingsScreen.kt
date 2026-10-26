package com.anda.launcher.feature.settings.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InterpreterMode
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.anda.launcher.core.model.Gesture
import com.anda.launcher.core.model.GestureSettings
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.SystemSettingsIntent
import com.anda.launcher.core.ui.startActivitySafely
import com.anda.launcher.feature.gestures.ActionPickerDialog
import com.anda.launcher.feature.gestures.SystemActions
import com.anda.launcher.feature.gestures.displayName
import com.anda.launcher.feature.settings.components.DefaultSettings
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.controls.SwitchRow
import com.anda.launcher.feature.settings.components.layout.SettingFrame
import com.anda.launcher.feature.settings.components.layout.SettingTitle
import com.anda.launcher.feature.settings.components.layout.SettingsGroup

private val gestureNames = mapOf(
    Gesture.SwipeUp to "Swipe up",
    Gesture.SwipeDown to "Swipe down",
    Gesture.DoubleTap to "Double tap",
    Gesture.TwoFingerSwipeUp to "Two-finger swipe up",
    Gesture.TwoFingerSwipeDown to "Two-finger swipe down",
    Gesture.PinchIn to "Pinch in",
    Gesture.PinchOut to "Pinch out",
    Gesture.HomePress to "Home button (while on home)",
)

@Composable
internal fun GestureSettingsScreen(b: SettingsBinder) {
    val graph = LocalLauncherGraph.current
    val apps by graph.appsRepository.apps.collectAsState()
    var editing by remember { mutableStateOf<Gesture?>(null) }
    val gestures = b.stored.gestures
    val defaults = DefaultSettings.gestures
    if (!SystemActions.isAccessibilityEnabled) {
        val context = LocalContext.current
        SettingsGroup {
            NavRow("Enable gesture service", "Needed for lock screen, recents, screenshot and power menu actions", Icons.Filled.InterpreterMode) {
                context.startActivitySafely(SystemActions.accessibilitySettingsIntent(context), SystemSettingsIntent)
            }
        }
    }
    SettingsGroup("Home screen gestures") {
        Gesture.entries.forEach { gesture ->
            val name = gestureNames.getValue(gesture)
            val modified = gestures.actionFor(gesture) != defaults.actionFor(gesture)
            SettingFrame(
                name,
                modified = modified,
                onReset = { b.update { it.copy(gestures = it.gestures.copy(bindings = it.gestures.bindings + (gesture to defaults.actionFor(gesture)))) } },
                onClick = { editing = gesture },
            ) {
                SettingTitle(name, modified)
                Text(gestures.actionFor(gesture).displayName(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    SettingsGroup {
        SwitchRow(
            "Icon swipe actions",
            b.bind({ it.gestures.iconSwipeActions }) { copy(gestures = gestures.copy(iconSwipeActions = it)) },
            "Swipe up on an icon or folder to run its action",
        )
        NavRow("Reset gestures to defaults", null, Icons.Filled.RestartAlt) {
            b.update { it.copy(gestures = GestureSettings()) }
        }
    }
    editing?.let { gesture ->
        ActionPickerDialog(
            title = gestureNames.getValue(gesture),
            current = gestures.actionFor(gesture),
            apps = apps,
            onPick = { action ->
                b.update { it.copy(gestures = it.gestures.copy(bindings = it.gestures.bindings + (gesture to action))) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}
