package com.anda.launcher.feature.settings.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.feature.settings.Changelog
import com.anda.launcher.feature.settings.Release
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.layout.SettingsGroup
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AboutScreen() {
    val context = LocalContext.current
    val info = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Anda Launcher ${info.versionName}", style = MaterialTheme.typography.titleLarge)
        Text("Build ${info.longVersionCode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("A fast, deeply customizable home screen for Pixel phones. Inspired by Nova Launcher and Smart Launcher.")
        Text(
            "No internet permission, no analytics. Settings and layout stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    VersionHistoryGroup()
    CrashReportsGroup()
    SettingsGroup("Tips") {
        Tip(Icons.Filled.Widgets, "Long-press empty space on home, then Customize, to change the grid live.")
        Tip(Icons.Filled.Search, "Search settings from the top of the main settings page.")
        Tip(Icons.Filled.Folder, "Drop an icon on another icon to make a folder.")
    }
}

/** What changed in each version, from the CHANGELOG.md the build ships as an asset. */
@Composable
private fun VersionHistoryGroup() {
    val context = LocalContext.current
    val releases by produceState(emptyList<Release>()) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(Changelog.ASSET).bufferedReader().use { Changelog.parse(it.readText()) } }.getOrDefault(emptyList())
        }
    }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val latest = releases.firstOrNull() ?: return
    SettingsGroup("What's new in ${latest.version}") {
        ReleaseNotes(latest, showVersion = false)
        if (releases.size > 1) {
            NavRow(
                "Version history",
                if (showAll) "Hide older versions" else "Show all ${releases.size} versions",
                Icons.Filled.History,
            ) { showAll = !showAll }
            if (showAll) releases.drop(1).forEach { ReleaseNotes(it, showVersion = true) }
        }
    }
}

@Composable
private fun ReleaseNotes(release: Release, showVersion: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val date = runCatching { DateFormat.getDateInstance(DateFormat.MEDIUM).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(release.date)!!) }
            .getOrDefault(release.date)
        Text(
            if (showVersion) "${release.version} · $date" else date,
            style = if (showVersion) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            color = if (showVersion) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        release.notes.forEach { note ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Crash reports stay on the phone (no INTERNET permission); the user decides whether to share
 * them, through the normal share sheet.
 */
@Composable
private fun CrashReportsGroup() {
    val context = LocalContext.current
    val reports = LocalLauncherGraph.current.crashReports
    var refresh by remember { mutableIntStateOf(0) }
    val count by produceState(0, refresh) { value = withContext(Dispatchers.IO) { reports.list().size } }
    SettingsGroup("Problem reports") {
        if (count == 0) {
            Text(
                "No crashes recorded.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SettingsGroup
        }
        NavRow("Share crash reports", "$count saved on this phone. Nothing is sent unless you share it.", Icons.Filled.Share) {
            val text = reports.combined()
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Anda Launcher crash reports")
                .putExtra(Intent.EXTRA_TEXT, text)
            runCatching { context.startActivity(Intent.createChooser(send, "Share crash reports")) }
        }
        NavRow("Delete crash reports", null, Icons.Filled.DeleteOutline) {
            reports.clear()
            refresh++
        }
    }
}

@Composable
private fun Tip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
