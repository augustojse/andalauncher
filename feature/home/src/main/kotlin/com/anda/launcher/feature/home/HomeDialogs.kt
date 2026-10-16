package com.anda.launcher.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.AppItem
import com.anda.launcher.core.model.FolderItem
import com.anda.launcher.core.model.HomeItem
import com.anda.launcher.core.model.LauncherAction
import com.anda.launcher.core.model.ShortcutItem
import com.anda.launcher.feature.gestures.ActionPickerDialog
import com.anda.launcher.feature.gestures.displayName

/** Nova-style "Edit" dialog for a home item: label, swipe action and folder cover mode. */
@Composable
fun EditHomeItemDialog(
    item: HomeItem,
    apps: List<AppInfo>,
    controller: HomeController,
    onDismiss: () -> Unit,
) {
    val originalTitle = when (item) {
        is AppItem -> item.title ?: apps.firstOrNull { it.key == item.key }?.label.orEmpty()
        is FolderItem -> item.title
        is ShortcutItem -> item.title
        else -> ""
    }
    var title by remember { mutableStateOf(originalTitle) }
    var swipe by remember {
        mutableStateOf(
            when (item) {
                is AppItem -> item.swipeAction ?: LauncherAction.None
                is FolderItem -> item.swipeAction
                else -> LauncherAction.None
            },
        )
    }
    var coverMode by remember { mutableStateOf(item is FolderItem && item.tapAction != LauncherAction.OpenFolder) }
    var pickingSwipe by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item is FolderItem) "Edit folder" else "Edit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Label") }, singleLine = true)
                if (item is FolderItem) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Cover mode", style = MaterialTheme.typography.bodyLarge)
                            Text("Tap opens the first app, swipe up opens the folder", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = coverMode, onCheckedChange = { coverMode = it })
                    }
                }
                if ((item is AppItem || item is FolderItem) && !coverMode) {
                    Column(Modifier.fillMaxWidth().clickable { pickingSwipe = true }.padding(vertical = 8.dp)) {
                        Text("Swipe-up action", style = MaterialTheme.typography.bodyLarge)
                        Text(swipe.displayName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title != originalTitle) controller.rename(item.id, title)
                when (item) {
                    is AppItem -> controller.setActions(item.id, null, swipe.takeIf { it != LauncherAction.None })
                    is FolderItem -> controller.setActions(
                        item.id,
                        // Any non-OpenFolder tap action means "cover": tap launches the first child.
                        tap = if (coverMode) LauncherAction.None else LauncherAction.OpenFolder,
                        swipe = if (coverMode) LauncherAction.OpenFolder else swipe,
                    )
                    else -> Unit
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingSwipe) {
        ActionPickerDialog(
            title = "Swipe-up action",
            current = swipe,
            apps = apps,
            onPick = { swipe = it; pickingSwipe = false },
            onDismiss = { pickingSwipe = false },
        )
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
