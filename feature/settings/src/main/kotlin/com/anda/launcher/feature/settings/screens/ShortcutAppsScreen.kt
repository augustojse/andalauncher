package com.anda.launcher.feature.settings.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.icon.rememberAppIcon
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.layout.Hint
import com.anda.launcher.feature.settings.components.layout.SettingsGroup

/** Settings → Search → Hidden shortcut apps: apps whose shortcuts are left out of search, each with a way back. */
@Composable
internal fun ShortcutAppsScreen(b: SettingsBinder) {
    val graph = LocalLauncherGraph.current
    val apps by graph.appsRepository.apps.collectAsState()
    val packages = b.stored.search.excludedShortcutApps
    if (packages.isEmpty()) {
        Hint("No hidden shortcut apps. Long-press an app shortcut in the search results and choose \"Hide shortcuts from ...\".")
        return
    }
    SettingsGroup {
        packages.forEach { packageName ->
            key(packageName) {
                ShortcutAppRow(packageName, apps.firstOrNull { it.key.packageName == packageName }) {
                    b.update { it.copy(search = it.search.withShortcutsShown(packageName)) }
                }
            }
        }
    }
    Hint("Their shortcuts are left out of search results. Shortcuts you pinned stay in the Favorites row.")
}

@Composable
private fun ShortcutAppRow(packageName: String, app: AppInfo?, onShowAgain: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val icon = app?.let { rememberAppIcon(it.key, it.lastUpdateTime) }
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp)) else Box(Modifier.size(40.dp))
        // An app that was uninstalled since still has its package name, so it can still be taken off the list.
        Text(app?.label ?: packageName, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onShowAgain) { Text("Show again") }
    }
}
