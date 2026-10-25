package com.anda.launcher.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.icons.IconPack
import com.anda.launcher.core.icons.IconPackInfo
import com.anda.launcher.core.model.AppIconOverride
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.IconEdit
import com.anda.launcher.core.model.IconShapeKind
import com.anda.launcher.core.model.IconSource
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.icon.rememberAppIcon
import com.anda.launcher.core.ui.icon.rememberShapePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Icons, top to bottom: the icon editor (the most visual tool first, like Smart Launcher's icon
 * page), the usual pack / shape / themed / size options, per-app exceptions ("Unique icons"),
 * then Advanced.
 */
@Composable
internal fun IconSettingsScreen(b: SettingsBinder, openEditor: (ComponentKey?) -> Unit) {
    val context = LocalContext.current
    val graph = LocalLauncherGraph.current
    val apps by graph.appsRepository.apps.collectAsState()
    val i = b.stored.icons
    val packs by produceState(emptyList<IconPackInfo>()) { value = withContext(Dispatchers.IO) { IconPack.installed(context) } }
    val samples = remember(apps) { apps.filter { !it.isHidden }.take(5) }
    // A recognisable app for the shape swatches.
    val sampleKey = samples.firstOrNull()?.key

    IconEditorCard(samples, b.stored, onOpen = { openEditor(null) })

    SettingsGroup("Style") {
        val pack = b.bind({ it.icons.iconPack }) { copy(icons = icons.copy(iconPack = it)) }
        ChoiceRow(
            "Icon pack",
            packs.firstOrNull { it.packageName == i.iconPack },
            listOf<IconPackInfo?>(null) + packs,
            label = { it?.label ?: "System icons" },
            modified = pack.modified,
            onReset = pack::reset,
        ) { v -> pack.set(v?.packageName) }
        VisualChoiceRow("Shape", b.bind({ it.icons.shape }) { copy(icons = icons.copy(shape = it)) }, IconShapeKind.entries) { shape ->
            val icon = rememberShapePreview(sampleKey, shape, 44.dp)
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(44.dp))
        }
        if (i.shape == IconShapeKind.Custom) {
            NavRow("Edit custom shape", "Round, squircle, cut or sharp corners, one by one", Icons.Filled.AutoFixHigh) { openEditor(null) }
        }
        SwitchRow("Themed icons", b.bind({ it.icons.themedIcons }) { copy(icons = icons.copy(themedIcons = it)) }, "Tint icons with your wallpaper colors")
    }
    IconStyleGroups("Home screen icons", "home", b, { it.icons.home }) { copy(icons = icons.copy(home = it)) }

    UniqueIconsSection(b, apps, packs, openEditor)

    var confirm by remember { mutableStateOf<Confirm?>(null) }
    val scope = rememberCoroutineScope()
    AdvancedSection("icons", 5) {
        SwitchRow(
            "Theme apps without a monochrome icon",
            b.bind({ it.icons.themedFallback }) { copy(icons = icons.copy(themedFallback = it)) },
            enabled = i.themedIcons,
        )
        SwitchRow(
            "Normalize legacy icons",
            b.bind({ it.icons.normalizeLegacy }) { copy(icons = icons.copy(normalizeLegacy = it)) },
            "Put old square icons on a shaped background",
        )
        NavRow("Reset icon style", "Undo the icon editor's colors, layout and effects for all apps", Icons.Filled.Refresh) {
            confirm = Confirm.ResetStyle
        }
        val count = i.perApp.size
        NavRow("Remove all exceptions", if (count == 1) "1 app has its own icon" else "$count apps have their own icon", Icons.Filled.DeleteSweep) {
            confirm = Confirm.RemoveExceptions
        }
        NavRow("Clear icon cache", "Render every icon again, e.g. after updating an icon pack", Icons.Filled.Brush) {
            scope.launch { graph.iconRepository.clearCache() }
        }
    }
    Hint("Dock, drawer and folder icons have their own size and label options on those pages.")

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (c == Confirm.ResetStyle) "Reset icon style?" else "Remove all exceptions?") },
            text = {
                Text(
                    if (c == Confirm.ResetStyle) {
                        "Colors, layout and effects from the icon editor go back to the app's own. Shape, pack and exceptions stay."
                    } else {
                        "Every app goes back to the icon pack and style you set for all apps."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    b.update { s ->
                        if (c == Confirm.ResetStyle) {
                            s.copy(icons = s.icons.copy(style = IconEdit()))
                        } else {
                            s.copy(icons = s.icons.copy(perApp = emptyMap()))
                        }
                    }
                    confirm = null
                }) { Text(if (c == Confirm.ResetStyle) "Reset" else "Remove") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private enum class Confirm { ResetStyle, RemoveExceptions }

/** The editor's entry point: a few real icons in the current style and one clear button. */
@Composable
private fun IconEditorCard(samples: List<AppInfo>, settings: LauncherSettings, onOpen: () -> Unit) {
    Spacer(Modifier.size(12.dp))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        SettingFrame("Icon editor", onClick = onOpen, contentPadding = PaddingValues(0.dp)) {
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer),
                        ),
                    )
                    .padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    samples.forEach { app ->
                        val icon = rememberAppIcon(app.key, app.lastUpdateTime)
                        Box(Modifier.size(48.dp)) {
                            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    SettingTitle("Icon editor", modified = !settings.icons.style.isPlain || settings.icons.shape == IconShapeKind.Custom)
                    Text(
                        "Colors from Material You, special shapes, size, position and effects, with a live preview",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onOpen) {
                    Icon(Icons.Filled.FormatPaint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Edit")
                }
            }
        }
    }
}

/** Apps that don't follow the icon pack and style: listed, editable, removable. */
@Composable
private fun UniqueIconsSection(b: SettingsBinder, apps: List<AppInfo>, packs: List<IconPackInfo>, openEditor: (ComponentKey?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val byKey = remember(apps) { apps.associateBy { it.key.flatten() } }
    val exceptions = b.stored.icons.perApp.entries
        .sortedBy { (key, _) -> byKey[key]?.label?.lowercase() ?: key }
    SettingsGroup("Unique icons") {
        NavRow("Add an exception", "Give one app its own icon, pack or style", Icons.Filled.Add) { picking = true }
        exceptions.forEach { (flat, exception) ->
            val key = ComponentKey.parse(flat) ?: return@forEach
            ExceptionRow(
                app = byKey[flat],
                key = key,
                summary = exceptionSummary(exception, packs),
                onClick = { openEditor(key) },
                onRemove = { b.update { s -> s.copy(icons = s.icons.copy(perApp = s.icons.perApp - flat)) } },
            )
        }
    }
    if (exceptions.isEmpty()) {
        Hint("Keep an icon pack for most apps and give a few their own: another pack, the app's original icon, or a different color or shape.")
    }
    if (picking) {
        AppPickerDialog(
            title = "Choose an app",
            apps = apps.filter { it.key.flatten() !in b.stored.icons.perApp },
            onPick = { app ->
                picking = false
                openEditor(app.key)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun ExceptionRow(app: AppInfo?, key: ComponentKey, summary: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp)) {
            if (app != null) {
                val icon = rememberAppIcon(app.key, app.lastUpdateTime)
                if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(app?.label ?: key.packageName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (app == null) "Not installed · $summary" else summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove the exception for ${app?.label ?: key.packageName}")
        }
    }
}

internal fun exceptionSummary(o: AppIconOverride, packs: List<IconPackInfo>): String = buildList {
    when (o.source) {
        IconSource.Default -> Unit
        IconSource.App -> add("Original icon")
        IconSource.Pack -> {
            val name = packs.firstOrNull { it.packageName == o.pack }?.label ?: "Icon pack"
            add(if (o.drawable != null) "$name · picked icon" else name)
        }
    }
    when (o.themed) {
        true -> add("themed")
        false -> add("not themed")
        null -> Unit
    }
    o.edit?.let { edit ->
        add(edit.shape?.let { "${humanize(it.name).lowercase()} shape, own style" } ?: "own style")
    }
}.joinToString(" · ").replaceFirstChar { it.uppercase() }.ifEmpty { "Same as all apps" }

/** A searchable list of apps; [allAppsLabel] adds an "every app" choice on top (null pick). */
@Composable
internal fun AppPickerDialog(
    title: String,
    apps: List<AppInfo>,
    onPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    allAppsLabel: String? = null,
    onPickAll: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        val q = query.trim().lowercase()
        apps.filter { !it.isHidden && (q.isEmpty() || q in it.sortKey || q in it.key.packageName) }.sortedBy { it.sortKey }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (allAppsLabel != null && query.isBlank()) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onPickAll).padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.Filled.FormatPaint, contentDescription = null, modifier = Modifier.size(36.dp).padding(6.dp))
                                Text(allAppsLabel, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    items(shown, key = { it.key.flatten() }) { app ->
                        val icon = rememberAppIcon(app.key, app.lastUpdateTime)
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onPick(app) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(36.dp)) {
                                if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(36.dp))
                            }
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
