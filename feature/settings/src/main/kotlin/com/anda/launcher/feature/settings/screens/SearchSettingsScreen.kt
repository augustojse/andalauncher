package com.anda.launcher.feature.settings.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.WebEngine
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.controls.SwitchRow
import com.anda.launcher.feature.settings.components.layout.AdvancedSection
import com.anda.launcher.feature.settings.components.layout.Hint
import com.anda.launcher.feature.settings.components.layout.SettingsGroup

@Composable
internal fun SearchSettingsScreen(b: SettingsBinder) {
    val context = LocalContext.current
    val contacts = b.bind({ it.search.contacts }) { copy(search = search.copy(contacts = it)) }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) contacts.set(true)
    }
    SettingsGroup("Sources") {
        SwitchRow("Apps", b.bind({ it.search.apps }) { copy(search = search.copy(apps = it)) })
        SwitchRow("App shortcuts", b.bind({ it.search.shortcuts }) { copy(search = search.copy(shortcuts = it)) })
        SwitchRow("Contacts", contacts.value, modified = contacts.modified, onReset = contacts::reset) { v ->
            if (v && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            } else {
                contacts.set(v)
            }
        }
        SwitchRow("Calculator", b.bind({ it.search.calculator }) { copy(search = search.copy(calculator = it)) }, "12*7, 15% of 80")
        SwitchRow("Unit converter", b.bind({ it.search.unitConverter }) { copy(search = search.copy(unitConverter = it)) }, "5 km in miles")
        SwitchRow(
            "Quick actions",
            b.bind({ it.search.quickActions }) { copy(search = search.copy(quickActions = it)) },
            "Call or text a number, open a web address, \"7:30\" sets an alarm, \"5 min\" a timer, \"dentist oct 31\" an event",
        )
        SwitchRow("Android settings", b.bind({ it.search.systemSettings }) { copy(search = search.copy(systemSettings = it)) })
        SwitchRow("Web search suggestion", b.bind({ it.search.webSearch }) { copy(search = search.copy(webSearch = it)) })
    }
    WebEnginesGroup(b)
    AdvancedSection("search", 3) {
        SwitchRow(
            "Fuzzy matching",
            b.bind({ it.search.fuzzyMatching }) { copy(search = search.copy(fuzzyMatching = it)) },
            "\"ggl\" finds Google, \"spotfiy\" finds Spotify",
        )
        SwitchRow(
            "Enter launches top result",
            b.bind({ it.search.launchTopOnEnter }) { copy(search = search.copy(launchTopOnEnter = it)) },
            "Or runs the first chip when no app matches",
        )
        SwitchRow(
            "Category filters",
            b.bind({ it.search.categoryFilters }) { copy(search = search.copy(categoryFilters = it)) },
            "Apps, Shortcuts, Contacts and Settings chips above the keyboard",
        )
    }
}

/** User-defined "Search <name>" rows. Each opens a URL in the browser; Anda itself stays offline. */
@Composable
private fun WebEnginesGroup(b: SettingsBinder) {
    val engines = b.stored.search.webEngines
    var adding by rememberSaveable { mutableStateOf(false) }
    val setEngines: (List<WebEngine>) -> Unit = { list -> b.update { it.copy(search = it.search.copy(webEngines = list)) } }

    SettingsGroup("Web search engines") {
        engines.forEachIndexed { index, engine ->
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(engine.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        engine.urlTemplate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { setEngines(engines.filterIndexed { i, _ -> i != index }) }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove ${engine.name}")
                }
            }
        }
        if (engines.size < WebEngine.MAX_ENGINES) {
            NavRow("Add search engine", "A chip above your results, e.g. \"Wikipedia\"", Icons.Filled.Add) { adding = true }
        }
    }
    Hint("Each engine opens your browser with the query. Anda itself has no internet access.")

    if (adding) {
        AddWebEngineDialog(
            onDismiss = { adding = false },
            onAdd = { engine ->
                adding = false
                setEngines(engines + engine)
            },
        )
    }
}

@Composable
private fun AddWebEngineDialog(onDismiss: () -> Unit, onAdd: (WebEngine) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var template by rememberSaveable { mutableStateOf("https://") }
    val engine = WebEngine(name.trim(), template.trim())
    val templateError = template.length > "https://".length && !WebEngine.isValidTemplate(template.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add search engine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Name") },
                    placeholder = { Text("Wikipedia") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("Address") },
                    supportingText = {
                        Text(if (templateError) "Needs http:// or https:// and %s, no spaces" else "%s is replaced by your search")
                    },
                    isError = templateError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(engine) }, enabled = engine.isValid) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
