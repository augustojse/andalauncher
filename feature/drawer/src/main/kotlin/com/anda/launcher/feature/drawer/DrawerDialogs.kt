package com.anda.launcher.feature.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.AppCategory
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.AppOverride
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.DrawerGroup
import com.anda.launcher.core.model.IconStyle
import com.anda.launcher.core.model.LauncherAction
import com.anda.launcher.core.model.PackageUserKey
import com.anda.launcher.feature.gestures.ActionPickerDialog
import com.anda.launcher.feature.gestures.displayName

/** Popup grid for a drawer folder. */
@Composable
internal fun DrawerFolderDialog(
    group: DrawerGroup,
    members: List<AppInfo>,
    controller: DrawerController,
    style: IconStyle,
    badges: Map<PackageUserKey, Int>,
) {
    AlertDialog(
        onDismissRequest = { controller.openGroupId = null },
        title = { Text(group.name) },
        text = {
            Column(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                members.chunked(4).forEach { row -> AppRow(row, controller, style, badges, 4) }
            }
        },
        confirmButton = { TextButton(onClick = { controller.openGroupId = null; controller.editGroup(group) }) { Text("Edit") } },
        dismissButton = { TextButton(onClick = { controller.openGroupId = null }) { Text("Close") } },
    )
}

/**
 * Per-app edit (Nova "Edit app" + Smart Launcher category override + lock):
 * label, category, swipe action, hidden, locked. [onEditIcon] opens the icon editor for this app.
 */
@Composable
fun EditAppDialog(
    app: AppInfo,
    current: AppOverride,
    apps: List<AppInfo>,
    onSave: (AppOverride) -> Unit,
    onDismiss: () -> Unit,
    onEditIcon: (() -> Unit)? = null,
) {
    var label by remember { mutableStateOf(current.label ?: app.originalLabel) }
    var category by remember { mutableStateOf(current.category ?: app.category) }
    var hidden by remember { mutableStateOf(current.hidden) }
    var locked by remember { mutableStateOf(current.locked) }
    var swipe by remember { mutableStateOf(current.swipeAction ?: LauncherAction.None) }
    var categoryMenu by remember { mutableStateOf(false) }
    var pickingSwipe by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${app.originalLabel}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, singleLine = true)
                if (onEditIcon != null) {
                    Column(Modifier.fillMaxWidth().clickable(onClick = onEditIcon).padding(vertical = 8.dp)) {
                        Text("Icon", style = MaterialTheme.typography.bodyLarge)
                        Text("Pack, colors, shape…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(Modifier.fillMaxWidth().clickable { categoryMenu = true }.padding(vertical = 8.dp)) {
                    Text("Category", style = MaterialTheme.typography.bodyLarge)
                    Text(category.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        AppCategory.entries.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { category = c; categoryMenu = false })
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().clickable { pickingSwipe = true }.padding(vertical = 8.dp)) {
                    Text("Swipe-up action", style = MaterialTheme.typography.bodyLarge)
                    Text(swipe.displayName(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                ToggleRow("Hide from drawer", hidden) { hidden = it }
                ToggleRow("Require fingerprint to open", locked) { locked = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    current.copy(
                        label = label.takeIf { it.isNotBlank() && it != app.originalLabel },
                        category = category.takeIf { it != app.category || current.category != null },
                        hidden = hidden,
                        locked = locked,
                        swipeAction = swipe.takeIf { it != LauncherAction.None },
                    ),
                )
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(AppOverride(app.key)); onDismiss() }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
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
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Create/edit a drawer tab or drawer folder. */
@Composable
fun DrawerGroupDialog(
    group: DrawerGroup?,
    apps: List<AppInfo>,
    onSave: (name: String, isFolder: Boolean, members: List<ComponentKey>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var isFolder by remember { mutableStateOf(group?.isFolder ?: false) }
    val selected = remember { group?.members.orEmpty().toList().toMutableStateList() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "New tab or folder" else "Edit ${group.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !isFolder, onClick = { isFolder = false })
                    Text("Tab")
                    RadioButton(selected = isFolder, onClick = { isFolder = true })
                    Text("Drawer folder")
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(apps.filter { !it.isHidden }, key = { it.key.flatten() }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (app.key in selected) selected.remove(app.key) else selected.add(app.key)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = app.key in selected, onCheckedChange = null)
                            Text(app.label, Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), isFolder, selected.toList()); onDismiss() }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { onDelete(); onDismiss() }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
