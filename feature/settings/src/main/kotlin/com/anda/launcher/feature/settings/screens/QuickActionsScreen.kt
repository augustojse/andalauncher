package com.anda.launcher.feature.settings.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.CustomAction
import com.anda.launcher.core.model.QuickActionKind
import com.anda.launcher.core.model.SearchSettings
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.controls.SwitchRow
import com.anda.launcher.feature.settings.components.layout.Hint
import com.anda.launcher.feature.settings.components.layout.SettingsGroup
import com.anda.launcher.feature.settings.components.layout.Summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Settings → Search → Quick actions: each built-in on or off and in order, plus the user's own. */
@Composable
internal fun QuickActionsScreen(b: SettingsBinder) {
    val search = b.stored.search
    val setSearch: (SearchSettings.() -> SearchSettings) -> Unit = { change -> b.update { it.copy(search = it.search.change()) } }
    val order = QuickActionKind.ordered(search.quickActionOrder)

    SettingsGroup {
        SwitchRow("Quick actions", b.bind({ it.search.quickActions }) { copy(search = search.copy(quickActions = it)) })
    }
    SettingsGroup("Read from what you type") {
        order.forEachIndexed { index, kind ->
            BuiltInRow(
                kind = kind,
                on = kind !in search.disabledQuickActions,
                enabled = search.quickActions,
                canMoveUp = index > 0,
                canMoveDown = index < order.lastIndex,
                onToggle = { on ->
                    setSearch { copy(disabledQuickActions = if (on) disabledQuickActions - kind else disabledQuickActions + kind) }
                },
                onMove = { by ->
                    val moved = order.toMutableList().apply { add(index + by, removeAt(index)) }
                    setSearch { copy(quickActionOrder = moved) }
                },
            )
        }
    }
    Hint("Chips by the search bar, in this order. Enter runs the first one when no app matches.")
    CustomActionsGroup(search.customActions) { list -> setSearch { copy(customActions = list) } }
}

private fun QuickActionKind.title(): String = when (this) {
    QuickActionKind.Call -> "Call"
    QuickActionKind.Message -> "Text"
    QuickActionKind.Email -> "Email"
    QuickActionKind.OpenUrl -> "Open web address"
    QuickActionKind.SetAlarm -> "Set an alarm"
    QuickActionKind.SetTimer -> "Start a timer"
    QuickActionKind.SaveContact -> "Save as contact"
    QuickActionKind.AddEvent -> "Add to calendar"
}

private fun QuickActionKind.example(): String = when (this) {
    QuickActionKind.Call, QuickActionKind.Message -> "A phone number"
    QuickActionKind.Email -> "An email address"
    QuickActionKind.OpenUrl -> "example.com"
    QuickActionKind.SetAlarm -> "\"7:30\", \"6pm\""
    QuickActionKind.SetTimer -> "\"5 min\", \"1h 30m\""
    QuickActionKind.SaveContact -> "A phone number or email address"
    QuickActionKind.AddEvent -> "\"dentist oct 31\""
}

private fun QuickActionKind.icon(): ImageVector = when (this) {
    QuickActionKind.Call -> Icons.Filled.Call
    QuickActionKind.Message -> Icons.AutoMirrored.Filled.Message
    QuickActionKind.Email -> Icons.Filled.Email
    QuickActionKind.OpenUrl -> Icons.Filled.OpenInBrowser
    QuickActionKind.SetAlarm -> Icons.Filled.Alarm
    QuickActionKind.SetTimer -> Icons.Filled.Timer
    QuickActionKind.SaveContact -> Icons.Filled.PersonAdd
    QuickActionKind.AddEvent -> Icons.Filled.Event
}

@Composable
private fun BuiltInRow(
    kind: QuickActionKind,
    on: Boolean,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val title = kind.title()
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onToggle(!on) }.padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(kind.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Summary(kind.example())
        }
        IconButton(onClick = { onMove(-1) }, enabled = enabled && canMoveUp) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move $title up")
        }
        IconButton(onClick = { onMove(1) }, enabled = enabled && canMoveDown) {
            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move $title down")
        }
        Switch(checked = on, onCheckedChange = onToggle, enabled = enabled)
    }
}

/** "Search in <app>" and custom intent chips. Each only starts an activity in another app. */
@Composable
private fun CustomActionsGroup(actions: List<CustomAction>, onChange: (List<CustomAction>) -> Unit) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsGroup("Your own actions") {
        actions.forEachIndexed { index, action ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    if (action.isAppSearch) Icons.Filled.Search else Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(if (action.isAppSearch) "Search ${action.label}" else action.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (action.isAppSearch) "Opens the app's search" else listOfNotNull(action.intentAction, action.data).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { onChange(actions.filterIndexed { i, _ -> i != index }) }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove ${action.label}")
                }
            }
        }
        if (actions.size < CustomAction.MAX_ACTIONS) {
            NavRow("Search in an app", "A chip that opens an app's own search with your query", Icons.Filled.Search) { dialog = APP_SEARCH }
            NavRow("Add custom action", "Any app that accepts an intent, for advanced users", Icons.Filled.Add) { dialog = CUSTOM }
        }
    }
    Hint("These show for every search, after the web search chips.")

    val add: (CustomAction) -> Unit = { action ->
        dialog = null
        onChange(actions + action)
    }
    when (dialog) {
        APP_SEARCH -> AppSearchDialog(onDismiss = { dialog = null }, onAdd = add)
        CUSTOM -> CustomActionDialog(onDismiss = { dialog = null }, onAdd = add)
    }
}

private const val APP_SEARCH = "app"
private const val CUSTOM = "custom"

private data class SearchableApp(val packageName: String, val label: String)

/** Apps with a search screen (`ACTION_SEARCH`), read off the main thread. */
@Composable
private fun AppSearchDialog(onDismiss: () -> Unit, onAdd: (CustomAction) -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<SearchableApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.queryIntentActivities(Intent(Intent.ACTION_SEARCH), PackageManager.ResolveInfoFlags.of(0))
                .filter { it.activityInfo.packageName != context.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { SearchableApp(it.activityInfo.packageName, it.activityInfo.applicationInfo.loadLabel(pm).toString()) }
                .sortedBy { it.label.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search in an app") },
        text = {
            val list = apps
            when {
                list == null -> Text("Looking for apps…")
                list.isEmpty() -> Text("No installed app offers a search screen.")
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.packageName }) { app ->
                        Text(
                            app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onAdd(CustomAction(label = app.label.take(CustomAction.MAX_LABEL), packageName = app.packageName)) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomActionDialog(onDismiss: () -> Unit, onAdd: (CustomAction) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    var intentAction by rememberSaveable { mutableStateOf(Intent.ACTION_VIEW) }
    var data by rememberSaveable { mutableStateOf("") }
    var extraKey by rememberSaveable { mutableStateOf("") }
    var packageName by rememberSaveable { mutableStateOf("") }
    var mimeType by rememberSaveable { mutableStateOf("") }
    val action = CustomAction(
        label = label.trim(),
        intentAction = intentAction.trim(),
        data = data.trim().ifEmpty { null },
        extraKey = extraKey.trim().ifEmpty { null },
        packageName = packageName.trim().ifEmpty { null },
        mimeType = mimeType.trim().ifEmpty { null },
    )
    val dataError = data.isNotBlank() && !CustomAction.isValidDataTemplate(data.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add custom action") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(label, { label = it.take(CustomAction.MAX_LABEL) }, "Name", placeholder = "Translate")
                Field(intentAction, { intentAction = it }, "Intent action")
                Field(
                    data,
                    { data = it },
                    "Data (optional)",
                    placeholder = "geo:0,0?q=%s",
                    help = if (dataError) "Needs a scheme like geo: or https:, no spaces" else "%s is replaced by your search",
                    isError = dataError,
                    keyboardType = KeyboardType.Uri,
                )
                Field(extraKey, { extraKey = it }, "Extra key (optional)", help = "Receives your search as text")
                Field(packageName, { packageName = it }, "Package (optional)", placeholder = "com.example.app")
                Field(mimeType, { mimeType = it }, "MIME type (optional)", placeholder = "text/plain")
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(action) }, enabled = action.isValid) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    help: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Ascii,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = help?.let { { Text(it) } },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
