package com.anda.launcher.feature.settings.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.data.BackupManager
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.layout.SettingsGroup
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BackupScreen(b: SettingsBinder) {
    val context = LocalContext.current
    val graph = LocalLauncherGraph.current
    val scope = rememberCoroutineScope()
    var autoBackups by remember { mutableStateOf(graph.backupManager.autoBackups()) }
    var confirmReset by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE)) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { graph.backupManager.export(it) } }
            }.isSuccess
            Toast.makeText(context, if (ok) "Backup saved" else "Backup failed", Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { graph.backupManager.import(it) } } == true
            }.getOrDefault(false)
            Toast.makeText(context, if (ok) "Backup restored" else "Not an Anda Launcher backup", Toast.LENGTH_SHORT).show()
        }
    }
    val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    SettingsGroup {
        NavRow("Back up now", "Layout, settings, hidden apps, tabs and custom icons", Icons.Filled.Backup) {
            export.launch("anda-launcher-$stamp.${BackupManager.EXTENSION}")
        }
        NavRow("Restore from file", null, null) { import.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
    }
    SettingsGroup("Automatic backups (daily, last 7)") {
        autoBackups.forEach { file ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(DateFormat.getDateTimeInstance().format(Date(file.lastModified())), Modifier.weight(1f))
                TextButton(onClick = {
                    scope.launch {
                        val ok = runCatching {
                            withContext(Dispatchers.IO) { file.inputStream().use { graph.backupManager.import(it) } }
                        }.getOrDefault(false)
                        Toast.makeText(context, if (ok) "Restored" else "This backup couldn't be restored", Toast.LENGTH_SHORT).show()
                        autoBackups = graph.backupManager.autoBackups()
                    }
                }) { Text("Restore") }
            }
        }
        if (autoBackups.isEmpty()) {
            Text("None yet. One is made each day you use the launcher.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    SettingsGroup {
        NavRow("Reset all settings", "Back to defaults. Your home layout and hidden apps stay.", Icons.Filled.RestartAlt) { confirmReset = true }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all settings?") },
            text = { Text("Grid, icons, dock, drawer, gestures and theme go back to their defaults. Your apps and home layout are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    b.update { LauncherSettings(onboardingDone = true) }
                    Toast.makeText(context, "Settings reset", Toast.LENGTH_SHORT).show()
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
