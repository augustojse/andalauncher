package com.anda.launcher.feature.settings.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.ColorSource
import com.anda.launcher.core.model.Gesture
import com.anda.launcher.core.model.IconShapeKind
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.TestTags
import com.anda.launcher.feature.gestures.displayName
import com.anda.launcher.feature.settings.Screen
import com.anda.launcher.feature.settings.SettingsIndex
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.humanize
import com.anda.launcher.feature.settings.components.layout.Hint
import com.anda.launcher.feature.settings.components.layout.SettingsGroup

@Composable
internal fun MainScreen(s: LauncherSettings, go: (Screen, String?) -> Unit) {
    val graph = LocalLauncherGraph.current
    val apps by graph.appsRepository.apps.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    TextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("Search settings") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag(TestTags.SETTINGS_SEARCH),
    )
    if (query.isNotBlank()) {
        val results = remember(query) { SettingsIndex.search(query) }
        SettingsGroup {
            if (results.isEmpty()) {
                Text("No matching settings", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            results.forEach { entry ->
                NavRow(entry.title, entry.screen.title) { go(entry.screen, entry.title) }
            }
        }
        return
    }

    SettingsGroup("Look") {
        NavRow("Home screen", homeSummary(s), Icons.Filled.GridView) { go(Screen.Home, null) }
        NavRow("Dock", dockSummary(s), Icons.Filled.Dock) { go(Screen.Dock, null) }
        NavRow("Icons", iconSummary(s), Icons.Filled.Widgets) { go(Screen.Icons, null) }
        NavRow("Folders", "${humanize(s.folders.preview.name)} preview · opens as ${humanize(s.folders.openStyle.name).lowercase()}", Icons.Filled.Folder) {
            go(Screen.Folders, null)
        }
        NavRow("Look & feel", themeSummary(s), Icons.Filled.Palette) { go(Screen.Theme, null) }
    }
    SettingsGroup("Behaviour") {
        NavRow("App drawer", drawerSummary(s), Icons.Filled.Apps) { go(Screen.Drawer, null) }
        NavRow("Gestures", gestureSummary(s), Icons.Filled.Gesture) { go(Screen.Gestures, null) }
        NavRow("Search", searchSummary(s), Icons.Filled.Search) { go(Screen.Search, null) }
        NavRow("At a glance", glanceSummary(s), Icons.Filled.Today) { go(Screen.Glance, null) }
        NavRow("Notification dots", humanize(s.badges.mode.name), Icons.Filled.Notifications) { go(Screen.Badges, null) }
    }
    SettingsGroup("Privacy & system") {
        val hidden = apps.count { it.isHidden }
        NavRow("Hidden apps", if (hidden == 0) "None" else "$hidden hidden", Icons.Filled.VisibilityOff) { go(Screen.Hidden, null) }
        NavRow("Permissions", "Default home, gestures, dots, contacts", Icons.Filled.Security) { go(Screen.Permissions, null) }
        NavRow("Backup & restore", "Daily automatic backups", Icons.Filled.Backup) { go(Screen.Backup, null) }
        NavRow("Dev", "Notes and screenshots about problems", Icons.Filled.BugReport) { go(Screen.Dev, null) }
        NavRow("About", null, Icons.Filled.Info) { go(Screen.About, null) }
    }
    Hint("A dot marks a setting you changed. Long-press it to reset it to the default.")
}

private fun homeSummary(s: LauncherSettings) =
    "${s.home.columns}×${s.home.rows} grid · ${humanize(s.home.pageTransition.name)} · ${humanize(s.home.pageIndicator.name).lowercase()} indicator"

private fun dockSummary(s: LauncherSettings) = if (!s.dock.enabled) {
    "Off"
} else {
    val pages = if (s.dock.pages > 1) " · ${s.dock.pages} pages" else ""
    "${s.dock.columns} icons$pages" + if (s.dock.showBackground) " · ${humanize(s.dock.backgroundShape.name).lowercase()} background" else ""
}

private fun iconSummary(s: LauncherSettings) = buildList {
    add(if (s.icons.shape == IconShapeKind.System) "System shape" else humanize(s.icons.shape.name))
    if (s.icons.iconPack != null) add("icon pack")
    if (s.icons.themedIcons) add("themed")
    add("${s.icons.home.sizePercent}%")
}.joinToString(" · ")

private fun themeSummary(s: LauncherSettings) = "${themeLabel(s.theme.themeMode)} theme · " + when (s.theme.colorSource) {
    ColorSource.Dynamic -> "Material You"
    ColorSource.Wallpaper -> "wallpaper colors"
    ColorSource.Custom -> "your color"
}

private fun drawerSummary(s: LauncherSettings) =
    "${drawerStyleLabel(s.drawer.style)} · ${sortLabel(s.drawer.sort)} · ${s.drawer.columns} columns"

private fun gestureSummary(s: LauncherSettings) =
    "Swipe up: ${s.gestures.actionFor(Gesture.SwipeUp).displayName()} · double tap: ${s.gestures.actionFor(Gesture.DoubleTap).displayName().lowercase()}"

private fun searchSummary(s: LauncherSettings): String {
    val q = s.search
    val on = listOf(q.apps, q.shortcuts, q.contacts, q.calculator, q.unitConverter, q.systemSettings, q.webSearch, q.quickActions).count { it }
    return "$on of 8 sources" + (if (q.fuzzyMatching) " · fuzzy" else "") +
        if (q.webEngines.isNotEmpty()) " · ${q.webEngines.size} web engines" else ""
}

private fun glanceSummary(s: LauncherSettings): String {
    val g = s.glance
    if (!g.enabled) return "Off"
    return listOfNotNull("date".takeIf { g.showDate }, "alarm".takeIf { g.showNextAlarm }, "battery".takeIf { g.showBattery }, "calendar".takeIf { g.showCalendar })
        .joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "Nothing shown" }
}
