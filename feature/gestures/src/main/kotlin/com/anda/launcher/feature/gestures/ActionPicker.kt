package com.anda.launcher.feature.gestures

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.LauncherAction

/** Picks any [LauncherAction], including "open app". Shared by gesture settings and item editors. */
@Composable
fun ActionPickerDialog(
    title: String,
    current: LauncherAction,
    apps: List<AppInfo>,
    onPick: (LauncherAction) -> Unit,
    onDismiss: () -> Unit,
    extraActions: List<LauncherAction> = emptyList(),
) {
    var pickingApp by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (pickingApp) "Choose app" else title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (pickingApp) {
                    items(apps.filter { !it.isHidden }, key = { it.key.flatten() }) { app ->
                        Text(
                            app.label,
                            Modifier.fillMaxWidth().clickable { onPick(LauncherAction.LaunchApp(app.key)) }.padding(vertical = 12.dp),
                        )
                    }
                } else {
                    items(extraActions + LauncherAction.simpleActions) { action ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(action) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = action == current, onClick = { onPick(action) })
                            Text(action.displayName())
                        }
                    }
                    item {
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable { pickingApp = true }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = current is LauncherAction.LaunchApp, onClick = { pickingApp = true })
                            val label = (current as? LauncherAction.LaunchApp)?.let { a -> apps.firstOrNull { it.key == a.key }?.label }
                            Text(if (label != null) "Open app: $label" else "Open app…", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { if (pickingApp) pickingApp = false else onDismiss() }) { Text(if (pickingApp) "Back" else "Cancel") } },
    )
}
