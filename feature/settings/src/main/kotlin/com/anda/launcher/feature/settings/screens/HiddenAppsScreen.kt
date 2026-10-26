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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.icon.rememberAppIcon
import com.anda.launcher.feature.settings.components.layout.Hint
import com.anda.launcher.feature.settings.components.layout.SettingsGroup
import kotlinx.coroutines.launch

@Composable
internal fun HiddenAppsScreen() {
    val graph = LocalLauncherGraph.current
    val scope = rememberCoroutineScope()
    val apps by graph.appsRepository.apps.collectAsState()
    val hidden = apps.filter { it.isHidden }
    if (hidden.isEmpty()) {
        Hint("No hidden apps. Long-press an app in the drawer and choose Hide.")
        return
    }
    SettingsGroup {
        hidden.forEach { app -> HiddenAppRow(app) { scope.launch { graph.overridesRepository.update(app.key) { it.copy(hidden = false) } } } }
    }
}

@Composable
private fun HiddenAppRow(app: AppInfo, onUnhide: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val icon = rememberAppIcon(app.key, app.lastUpdateTime)
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp)) else Box(Modifier.size(40.dp))
        Text(app.label, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onUnhide) { Text("Unhide") }
    }
}
