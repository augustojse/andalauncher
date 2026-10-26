package com.anda.launcher.feature.settings.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anda.launcher.core.icons.IconPack
import com.anda.launcher.core.icons.IconPackInfo
import com.anda.launcher.core.icons.IconRepository
import com.anda.launcher.core.icons.IconShapes
import com.anda.launcher.core.icons.PackIcon
import com.anda.launcher.core.model.AppIconOverride
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.CornerStyle
import com.anda.launcher.core.model.CustomShape
import com.anda.launcher.core.model.IconBackground
import com.anda.launcher.core.model.IconColor
import com.anda.launcher.core.model.IconColorRole
import com.anda.launcher.core.model.IconEdit
import com.anda.launcher.core.model.IconForeground
import com.anda.launcher.core.model.IconShapeKind
import com.anda.launcher.core.model.IconSource
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.model.ShapeCorner
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.core.ui.icon.color
import com.anda.launcher.core.ui.icon.iconSpec
import com.anda.launcher.core.ui.icon.rememberAppIcon
import com.anda.launcher.core.ui.icon.rememberIconPreview
import com.anda.launcher.core.ui.theme.isLauncherDark
import com.anda.launcher.feature.settings.components.Setting
import com.anda.launcher.feature.settings.components.Update
import com.anda.launcher.feature.settings.components.controls.ChoiceRow
import com.anda.launcher.feature.settings.components.controls.ColorPickerDialog
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.controls.SegmentedRow
import com.anda.launcher.feature.settings.components.controls.SliderRow
import com.anda.launcher.feature.settings.components.controls.SwitchRow
import com.anda.launcher.feature.settings.components.controls.toArgbLong
import com.anda.launcher.feature.settings.components.humanize
import com.anda.launcher.feature.settings.components.layout.Hint
import com.anda.launcher.feature.settings.components.layout.SettingFrame
import com.anda.launcher.feature.settings.components.layout.SettingTitle
import com.anda.launcher.feature.settings.components.layout.SettingsGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ---- Draft ---------------------------------------------------------------------------------------

/**
 * The editor's unsaved state. For all apps, [shape] is the global shape. For one app, [shape] null
 * means "the shape of all apps", and [ownStyle] false means it follows the global style (then
 * [edit] just mirrors it, so the controls show what the app looks like).
 */
@Immutable
private data class EditorDraft(
    val shape: IconShapeKind?,
    val customShape: CustomShape,
    val edit: IconEdit,
    val ownStyle: Boolean,
    val source: IconSource = IconSource.Default,
    val pack: String? = null,
    val drawable: String? = null,
    val themed: Boolean? = null,
) {
    fun exception(): AppIconOverride = AppIconOverride(
        source = source,
        pack = pack,
        drawable = drawable,
        themed = themed,
        edit = if (ownStyle) edit.copy(shape = shape, customShape = customShape.takeIf { shape == IconShapeKind.Custom }) else null,
    ).sanitized()
}

private fun LauncherSettings.draftFor(target: ComponentKey?): EditorDraft {
    val i = icons
    if (target == null) return EditorDraft(i.shape, i.customShape, i.style.copy(shape = null, customShape = null), ownStyle = true)
    val o = i.exceptionFor(target) ?: AppIconOverride()
    val own = o.edit
    return EditorDraft(
        shape = own?.shape,
        customShape = own?.customShape ?: i.customShape,
        edit = (own ?: i.style).copy(shape = null, customShape = null),
        ownStyle = own != null,
        source = o.source,
        pack = o.pack,
        drawable = o.drawable,
        themed = o.themed,
    )
}

private fun LauncherSettings.withDraft(target: ComponentKey?, d: EditorDraft): LauncherSettings {
    if (target == null) {
        return copy(icons = icons.copy(shape = d.shape ?: IconShapeKind.System, customShape = d.customShape, style = d.edit))
    }
    val flat = target.flatten()
    val o = d.exception()
    return copy(icons = icons.copy(perApp = if (o.isEmpty) icons.perApp - flat else icons.perApp + (flat to o)))
}

private enum class EditorTab(val title: String) { Source("Source"), Shape("Shape"), Color("Color"), Adjust("Adjust"), Layout("Layout"), Effects("Effects") }

/** What the preview sits on: the theme, or a dark or light wallpaper stand-in. */
private enum class Backdrop { Theme, Dark, Light }

// ---- Screen --------------------------------------------------------------------------------------

/**
 * The icon editor (Smart Launcher's adaptive-icon editor and Lawnchair's shape editor, in one
 * place): shape, Material You colors, color adjustments, size / position / rotation of the
 * foreground and effects, for all apps or, as an exception, for one. Nothing is saved until Save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IconEditorScreen(
    target: ComponentKey?,
    onTargetChange: (ComponentKey?) -> Unit,
    update: Update,
    onClose: () -> Unit,
) {
    val graph = LocalLauncherGraph.current
    val stored = LocalLauncherSettings.current
    val apps by graph.appsRepository.apps.collectAsState()
    val stats by graph.launchStatsRepository.stats.collectAsState(emptyMap())
    val app = remember(apps, target) { target?.let { t -> apps.firstOrNull { it.key == t } } }
    // Favourites first: the preview should show icons the user knows.
    val samples = remember(apps, stats) {
        apps.filter { !it.isHidden }.sortedByDescending { stats[it.key]?.count ?: 0 }.take(6)
    }
    val saved = remember(stored, target) { stored.draftFor(target) }
    var draft by remember(target) { mutableStateOf(saved) }
    val dirty = draft != saved

    val dark = isLauncherDark(stored.theme)
    val scheme = MaterialTheme.colorScheme
    val baseSpec = remember(stored, dark, scheme) { stored.iconSpec(sizePx = 160, dark = dark, scheme = scheme) }
    val draftSpec = if (target == null) {
        baseSpec.copy(shape = draft.shape ?: IconShapeKind.System, customShape = draft.customShape, style = draft.edit)
    } else {
        baseSpec
    }
    val draftException = if (target == null) null else draft.exception()

    var pendingLeave by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun leave(action: () -> Unit) = if (dirty) pendingLeave = action else action()
    BackHandler { leave(onClose) }

    var pickingTarget by remember { mutableStateOf(false) }
    val tabs = if (target == null) EditorTab.entries - EditorTab.Source else EditorTab.entries
    var tab by rememberSaveable(target) { mutableStateOf(tabs.first()) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            title = { Text(if (app == null) "Icon editor" else "Edit icon", maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = { leave(onClose) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
            actions = {
                IconButton(onClick = {
                    draft = if (target == null) LauncherSettings().draftFor(null) else EditorDraft(null, stored.icons.customShape, stored.icons.style, ownStyle = false)
                }) { Icon(Icons.Filled.Restore, contentDescription = if (target == null) "Reset to defaults" else "Remove this exception") }
                TextButton(onClick = {
                    val d = draft
                    update { it.withDraft(target, d) }
                    onClose()
                }) { Text("Save") }
            },
        )

        PreviewPanel(target, app, samples, draftSpec, draftException)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Editing", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AssistChip(
                onClick = { pickingTarget = true },
                label = { Text(app?.label ?: if (target == null) "All apps" else target.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    if (app != null) {
                        val icon = rememberAppIcon(app.key, app.lastUpdateTime)
                        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                },
                trailingIcon = { Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            if (dirty) Text("Not saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0), edgePadding = 16.dp) {
            tabs.forEach { t -> Tab(selected = t == tab, onClick = { tab = t }, text = { Text(t.title) }) }
        }

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = false)) {
            val set: (EditorDraft) -> Unit = { draft = it }
            if (target != null && tab != EditorTab.Source) {
                StyleOwnershipBanner(app?.label ?: target.packageName, draft) {
                    draft = draft.copy(ownStyle = false, edit = stored.icons.style, shape = null)
                }
            }
            when (tab) {
                EditorTab.Source -> if (target != null) SourceTab(target, draft, set)
                EditorTab.Shape -> ShapeTab(target != null, draft, set)
                EditorTab.Color -> ColorTab(draft, set, samples.firstOrNull()?.key ?: target, target, draftSpec)
                EditorTab.Adjust -> AdjustTab(draft, set)
                EditorTab.Layout -> LayoutTab(draft, set)
                EditorTab.Effects -> EffectsTab(draft, set)
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }

    if (pickingTarget) {
        AppPickerDialog(
            title = "Edit icons for",
            apps = apps,
            allAppsLabel = "All apps",
            onPickAll = {
                pickingTarget = false
                leave { onTargetChange(null) }
            },
            onPick = { picked ->
                pickingTarget = false
                leave { onTargetChange(picked.key) }
            },
            onDismiss = { pickingTarget = false },
        )
    }
    pendingLeave?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingLeave = null },
            title = { Text("Discard changes?") },
            text = { Text("Your icon changes haven't been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingLeave = null
                    action()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val d = draft
                    pendingLeave = null
                    update { it.withDraft(target, d) }
                    action()
                }) { Text("Save") }
            },
        )
    }
}

// ---- Preview -------------------------------------------------------------------------------------

@Composable
private fun PreviewPanel(
    target: ComponentKey?,
    app: AppInfo?,
    samples: List<AppInfo>,
    spec: IconRepository.Spec,
    exception: AppIconOverride?,
) {
    var backdrop by rememberSaveable { mutableStateOf(Backdrop.Theme) }
    val brush = when (backdrop) {
        Backdrop.Theme -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))
        Backdrop.Dark -> Brush.linearGradient(listOf(Color(0xFF1B1D24), Color(0xFF34384A)))
        Backdrop.Light -> Brush.linearGradient(listOf(Color(0xFFF4F1EC), Color(0xFFDCE3EE)))
    }
    Box(Modifier.fillMaxWidth().height(208.dp).background(brush)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            if (target != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val now = app?.let { rememberAppIcon(it.key, it.lastUpdateTime) }
                        IconSlot(now, 56.dp)
                        PreviewCaption("Now", backdrop)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = captionColor(backdrop))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconSlot(rememberIconPreview(target, 104.dp, spec, exception), 104.dp)
                        PreviewCaption(app?.label ?: target.packageName, backdrop)
                    }
                }
            } else {
                IconSlot(rememberIconPreview(samples.firstOrNull()?.key, 96.dp, spec, null), 96.dp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    samples.drop(1).forEach { sample ->
                        IconSlot(rememberIconPreview(sample.key, 44.dp, spec, null), 44.dp)
                    }
                }
            }
        }
        IconButton(
            onClick = { backdrop = Backdrop.entries[(backdrop.ordinal + 1) % Backdrop.entries.size] },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Icon(Icons.Filled.Contrast, contentDescription = "Change preview background", tint = captionColor(backdrop))
        }
    }
}

@Composable
private fun captionColor(backdrop: Backdrop): Color = when (backdrop) {
    Backdrop.Theme -> MaterialTheme.colorScheme.onPrimaryContainer
    Backdrop.Dark -> Color.White
    Backdrop.Light -> Color(0xFF1B1D24)
}

@Composable
private fun PreviewCaption(text: String, backdrop: Backdrop) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = captionColor(backdrop),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun IconSlot(icon: androidx.compose.ui.graphics.ImageBitmap?, size: Dp) {
    Box(Modifier.size(size)) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(size))
    }
}

@Composable
private fun StyleOwnershipBanner(appName: String, draft: EditorDraft, useGlobal: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (draft.ownStyle) "$appName has its own style." else "$appName uses the style of all apps. Changing anything here gives it its own.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            if (draft.ownStyle) TextButton(onClick = useGlobal) { Text("Use all apps' style") }
        }
    }
}

// ---- Draft-backed settings -----------------------------------------------------------------------

/** A [Setting] over one field of the draft style; touching it gives an app its own style. */
private fun <T> styleSetting(
    draft: EditorDraft,
    set: (EditorDraft) -> Unit,
    get: (IconEdit) -> T,
    put: IconEdit.(T) -> IconEdit,
): Setting<T> {
    val write: (T) -> Unit = { v -> set(draft.copy(edit = draft.edit.put(v), ownStyle = true)) }
    return Setting(get(draft.edit), get(IconEdit.Plain), write, write)
}

private fun <T> draftSetting(value: T, default: T, write: (T) -> Unit): Setting<T> = Setting(value, default, write, write)

// ---- Source --------------------------------------------------------------------------------------

@Composable
private fun SourceTab(target: ComponentKey, draft: EditorDraft, set: (EditorDraft) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val packs by produceState(emptyList<IconPackInfo>()) { value = withContext(Dispatchers.IO) { IconPack.installed(context) } }
    val globalPack = LocalLauncherSettings.current.icons.iconPack
    var picking by remember { mutableStateOf(false) }
    SettingsGroup("Icon") {
        SegmentedRow(
            "Icon source",
            draftSetting(draft.source, IconSource.Default) { set(draft.copy(source = it, pack = if (it == IconSource.Pack) draft.pack ?: globalPack ?: packs.firstOrNull()?.packageName else null, drawable = null)) },
            IconSource.entries,
        ) { source ->
            when (source) {
                IconSource.Default -> if (globalPack == null) "Default" else "Your pack"
                IconSource.App -> "Original"
                IconSource.Pack -> "Other pack"
            }
        }
        if (draft.source == IconSource.Pack) {
            if (packs.isEmpty()) {
                Hint("No icon packs are installed. Install one from the Play Store, then pick it here.")
            } else {
                ChoiceRow(
                    "Icon pack",
                    packs.firstOrNull { it.packageName == draft.pack },
                    packs,
                    label = { it?.label ?: "Choose a pack" },
                ) { pack -> set(draft.copy(pack = pack?.packageName, drawable = null)) }
                NavRow(
                    "Pick an icon",
                    draft.drawable?.let { "Picked: ${humanizeDrawable(it)}" } ?: "The pack's own icon for this app",
                    Icons.Filled.GridOn,
                ) { if (draft.pack != null) picking = true }
            }
        }
        SegmentedRow(
            "Themed icon",
            draftSetting(draft.themed, null) { set(draft.copy(themed = it)) },
            listOf(null, true, false),
        ) { when (it) { null -> "Default"; true -> "On"; false -> "Off" } }
    }
    Hint(
        when (draft.source) {
            IconSource.Default -> if (globalPack == null) "This app uses its own icon, like all apps (no icon pack is set)." else "This app follows the icon pack set for all apps."
            IconSource.App -> "This app keeps its original icon, even with an icon pack. Its colors and shape still follow all apps unless you change them in the other tabs."
            IconSource.Pack -> "Mix packs: this app takes its icon from another pack."
        },
    )
    val pack = draft.pack
    if (picking && pack != null) {
        PackIconPicker(
            pack = pack,
            packLabel = packs.firstOrNull { it.packageName == pack }?.label ?: "Icon pack",
            target = target,
            onPick = { name ->
                picking = false
                set(draft.copy(drawable = name))
            },
            onDismiss = { picking = false },
        )
    }
}

private fun humanizeDrawable(name: String) = name.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Every icon in [pack], with the pack's match and look-alikes for [target] first, then by category. */
@Composable
private fun PackIconPicker(pack: String, packLabel: String, target: ComponentKey, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val icons = LocalLauncherGraph.current.iconRepository
    val apps by LocalLauncherGraph.current.appsRepository.apps.collectAsState()
    val label = remember(apps, target) { apps.firstOrNull { it.key == target }?.label.orEmpty() }
    val catalog by produceState<List<PackIcon>?>(null, pack) { value = icons.packCatalog(pack) }
    val match by produceState<String?>(null, pack, target) { value = icons.packMatch(pack, target) }
    var query by remember { mutableStateOf("") }
    val sections = remember(catalog, match, query, label) {
        val all = catalog.orEmpty()
        val q = query.trim().lowercase().replace(' ', '_')
        if (q.isNotEmpty()) return@remember listOf("Results" to all.filter { q in it.name }.map { it.name }.distinct().take(400))
        val words = (label.lowercase().split(' ', '-', '.') + target.packageName.substringAfterLast('.'))
            .map { w -> w.filter(Char::isLetterOrDigit) }.filter { it.length >= 3 }.distinct()
        val suggested = (listOfNotNull(match) + all.map { it.name }.filter { n -> words.any { it in n } }).distinct().take(24)
        listOf("Suggested" to suggested) + all.groupBy { it.category ?: "All icons" }.map { (c, list) -> c to list.map { it.name } }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pick an icon", style = MaterialTheme.typography.titleLarge)
                        Text(packLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onPick(null) }) { Text("Pack's own") }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search icons") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (catalog == null) {
                    Text("Loading icons…", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyVerticalGrid(GridCells.Adaptive(68.dp), contentPadding = PaddingValues(12.dp), modifier = Modifier.weight(1f)) {
                        sections.filter { it.second.isNotEmpty() }.forEach { (title, names) ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "h|$title") {
                                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                            }
                            items(names, key = { "$title|$it" }) { name -> PackIconCell(pack, name) { onPick(name) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackIconCell(pack: String, name: String, onClick: () -> Unit) {
    val icons = LocalLauncherGraph.current.iconRepository
    val sizePx = with(LocalDensity.current) { 48.dp.roundToPx() }
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pack, name) {
        value = icons.packIcon(pack, name, sizePx)?.asImageBitmap()
    }
    Box(
        Modifier.padding(4.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(8.dp)
            .semantics { contentDescription = humanizeDrawable(name) },
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) } ?: Spacer(Modifier.size(48.dp))
    }
}

// ---- Shape ---------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeTab(perApp: Boolean, draft: EditorDraft, set: (EditorDraft) -> Unit) {
    val options: List<IconShapeKind?> = if (perApp) listOf(null) + IconShapeKind.entries else IconShapeKind.entries
    SettingsGroup("Shape") {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { kind ->
                val selected = kind == draft.shape
                Column(
                    Modifier.clip(MaterialTheme.shapes.medium)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.medium,
                        )
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .clickable { set(draft.copy(shape = kind, ownStyle = true)) }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .size(width = 64.dp, height = 62.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (kind == null) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Text("All", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        ShapeSwatch(kind, draft.customShape, 36.dp)
                    }
                    Text(
                        kind?.let { shapeLabel(it) } ?: "Default",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (draft.shape == IconShapeKind.Custom) CustomShapeEditor(draft, set)
}

private fun shapeLabel(kind: IconShapeKind) = when (kind) {
    IconShapeKind.RoundedSquare -> "Rounded"
    else -> humanize(kind.name)
}

@Composable
private fun ShapeSwatch(kind: IconShapeKind, custom: CustomShape, size: Dp) {
    val px = with(LocalDensity.current) { size.toPx() }
    val path = remember(kind, custom, px) { IconShapes.path(kind, px, custom).asComposePath() }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(size)) { drawPath(path, color) }
}

@Composable
private fun CustomShapeEditor(draft: EditorDraft, set: (EditorDraft) -> Unit) {
    val shape = draft.customShape
    var linked by rememberSaveable { mutableStateOf(shape.corners.distinct().size == 1) }
    fun write(next: CustomShape) = set(draft.copy(customShape = next, ownStyle = true))
    SettingsGroup("Corners") {
        SwitchRow("Same for all corners", linked, onChange = { on ->
            linked = on
            if (on) write(shape.withAll(shape.topLeft))
        })
        if (linked) {
            CornerControls("Corner style", "Corner size", shape.topLeft) { write(shape.withAll(it)) }
        } else {
            CornerControls("Top left corner", "Top left size", shape.topLeft) { write(shape.copy(topLeft = it)) }
            CornerControls("Top right corner", "Top right size", shape.topRight) { write(shape.copy(topRight = it)) }
            CornerControls("Bottom right corner", "Bottom right size", shape.bottomRight) { write(shape.copy(bottomRight = it)) }
            CornerControls("Bottom left corner", "Bottom left size", shape.bottomLeft) { write(shape.copy(bottomLeft = it)) }
        }
    }
    Hint("Full round corners make a circle; mix cut and round corners for a leaf or a tag.")
}

@Composable
private fun CornerControls(styleTitle: String, sizeTitle: String, corner: ShapeCorner, write: (ShapeCorner) -> Unit) {
    SegmentedRow(styleTitle, draftSetting(corner.style, CornerStyle.Round) { write(corner.copy(style = it)) }, CornerStyle.entries)
    if (corner.style != CornerStyle.Sharp) {
        SliderRow(
            sizeTitle,
            draftSetting((corner.size * 100).roundToInt(), 50) { write(corner.copy(size = it / 100f)) },
            0..100,
            "%",
            steps = 0,
        )
    }
}

// ---- Color ---------------------------------------------------------------------------------------

/** One-tap looks built from Material You roles, so they follow the wallpaper and theme. */
private val ColorPresets: List<Pair<String, (IconEdit) -> IconEdit>> = listOf(
    "Original" to { e -> e.copy(background = IconBackground.Original, foreground = IconForeground.Original) },
    "Material You" to { e -> e.tinted(IconColorRole.PrimaryContainer, IconColorRole.OnPrimaryContainer) },
    "Tonal" to { e -> e.tinted(IconColorRole.SecondaryContainer, IconColorRole.OnSecondaryContainer) },
    "Tertiary" to { e -> e.tinted(IconColorRole.TertiaryContainer, IconColorRole.OnTertiaryContainer) },
    "Bold" to { e -> e.tinted(IconColorRole.Primary, IconColorRole.OnPrimary) },
    "Inverse" to { e -> e.tinted(IconColorRole.InverseSurface, IconColorRole.InverseOnSurface) },
    "Gradient" to { e ->
        e.copy(
            background = IconBackground.Gradient,
            backgroundColor = IconColor.of(IconColorRole.PrimaryContainer),
            backgroundColor2 = IconColor.of(IconColorRole.TertiaryContainer),
            foreground = IconForeground.Monochrome,
            foregroundColor = IconColor.of(IconColorRole.OnPrimaryContainer),
        )
    },
    "Glyph only" to { e -> e.copy(background = IconBackground.None, foreground = IconForeground.Monochrome, foregroundColor = IconColor.of(IconColorRole.Primary)) },
    "Grayscale" to { e -> e.copy(background = IconBackground.Original, foreground = IconForeground.Original, saturation = 0f) },
)

private fun IconEdit.tinted(background: IconColorRole, foreground: IconColorRole) = copy(
    background = IconBackground.Solid,
    backgroundColor = IconColor.of(background),
    foreground = IconForeground.Monochrome,
    foregroundColor = IconColor.of(foreground),
)

@Composable
private fun ColorTab(draft: EditorDraft, set: (EditorDraft) -> Unit, sampleKey: ComponentKey?, target: ComponentKey?, spec: IconRepository.Spec) {
    fun <T> s(get: (IconEdit) -> T, put: IconEdit.(T) -> IconEdit) = styleSetting(draft, set, get, put)
    SettingsGroup("Presets") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ColorPresets, key = { it.first }) { (name, apply) ->
                val edit = apply(draft.edit)
                val previewDraft = draft.copy(edit = edit, ownStyle = true)
                val icon = if (target == null) {
                    rememberIconPreview(sampleKey, 44.dp, spec.copy(style = edit), null)
                } else {
                    rememberIconPreview(target, 44.dp, spec, previewDraft.exception())
                }
                val selected = edit == draft.edit
                Column(
                    Modifier.clip(MaterialTheme.shapes.medium)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.medium,
                        )
                        .clickable { set(previewDraft) }
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    IconSlot(icon, 44.dp)
                    Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
    SettingsGroup("Background") {
        SegmentedRow("Background", s({ it.background }) { copy(background = it) }, IconBackground.entries)
        when (draft.edit.background) {
            IconBackground.Solid -> IconColorRow("Background color", s({ it.backgroundColor }) { copy(backgroundColor = it) })
            IconBackground.Gradient -> {
                IconColorRow("Gradient start", s({ it.backgroundColor }) { copy(backgroundColor = it) })
                IconColorRow("Gradient end", s({ it.backgroundColor2 }) { copy(backgroundColor2 = it) })
                SliderRow("Gradient angle", s({ it.gradientAngle }) { copy(gradientAngle = it) }, 0..359, "°", steps = 0)
            }
            IconBackground.Original, IconBackground.None -> Unit
        }
    }
    SettingsGroup("Foreground") {
        SegmentedRow("Foreground", s({ it.foreground }) { copy(foreground = it) }, IconForeground.entries) {
            if (it == IconForeground.Original) "App's colors" else "One color"
        }
        if (draft.edit.foreground == IconForeground.Monochrome) {
            IconColorRow("Foreground color", s({ it.foregroundColor }) { copy(foregroundColor = it) })
        }
    }
    Hint("Colors marked with a dot come from Material You: they change with your wallpaper and theme.")
}

/** Material You roles first (they follow the theme), then fixed colors and a picker for any other. */
@Composable
private fun IconColorRow(title: String, setting: Setting<IconColor>) {
    val scheme = MaterialTheme.colorScheme
    var picking by remember { mutableStateOf(false) }
    val value = setting.value
    val fixed = remember(value) { (listOfNotNull(value.argb.takeIf { value.role == null }) + FixedColors).distinct() }
    SettingFrame(title, setting.modified, setting::reset, contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingTitle(title, setting.modified, Modifier.weight(1f))
            Text(
                value.role?.let { humanize(it.name) } ?: value.argb?.let { "#%06X".format(it.toInt() and 0xFFFFFF) } ?: "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(IconColorRole.entries) { role ->
                ColorDot(scheme.color(role), selected = value.role == role, isRole = true, description = humanize(role.name)) {
                    setting.set(IconColor.of(role))
                }
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(fixed) { argb ->
                ColorDot(Color(argb.toInt()), selected = value.role == null && value.argb == argb, isRole = false, description = "#%06X".format(argb.toInt() and 0xFFFFFF)) {
                    setting.set(IconColor.fixed(argb))
                }
            }
            item {
                Box(Modifier.size(48.dp).clip(CircleShape).clickable { picking = true }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Colorize, contentDescription = "Pick any color", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
    if (picking) {
        ColorPickerDialog(
            initial = value.argb ?: value.role?.let { scheme.color(it).toArgbLong() } ?: 0xFF4285F4,
            onPick = {
                picking = false
                setting.set(IconColor.fixed(it))
            },
            onDismiss = { picking = false },
        )
    }
}

private val FixedColors = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF202124, 0xFF5F6368, 0xFF4285F4, 0xFF34A853, 0xFFFBBC04, 0xFFEA4335,
    0xFF9C27B0, 0xFF00BCD4, 0xFFFF7043, 0xFF8D6E63, 0xFFE91E63, 0xFF3F51B5,
)

@Composable
private fun ColorDot(color: Color, selected: Boolean, isRole: Boolean, description: String, onClick: () -> Unit) {
    // A 48 dp touch target around a 36 dp swatch.
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(color)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isRole) {
                // The Material You marker: a small dot in the color's own contrast.
                val dot = if (color.luminanceLight()) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.8f)
                Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
            }
        }
    }
}

private fun Color.luminanceLight(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) > 0.6f

// ---- Adjust / layout / effects -------------------------------------------------------------------

@Composable
private fun AdjustTab(draft: EditorDraft, set: (EditorDraft) -> Unit) {
    fun <T> s(get: (IconEdit) -> T, put: IconEdit.(T) -> IconEdit) = styleSetting(draft, set, get, put)
    if (draft.edit.foreground != IconForeground.Original) {
        // One flat color has nothing to adjust; offer the way back instead of dead sliders.
        SettingsGroup("Color adjustments") {
            NavRow("Use the app's colors", "Adjustments change the app's own colors; this icon uses one color now", Icons.Filled.Palette) {
                set(draft.copy(edit = draft.edit.copy(foreground = IconForeground.Original), ownStyle = true))
            }
        }
        return
    }
    SettingsGroup("Color adjustments") {
        SliderRow("Hue", s({ it.hue }) { copy(hue = it) }, -180..180, "°", steps = 0)
        SliderRow("Saturation", s({ (it.saturation * 100).roundToInt() }) { copy(saturation = it / 100f) }, 0..200, "%", steps = 0)
        SliderRow("Brightness", s({ (it.brightness * 100).roundToInt() }) { copy(brightness = it / 100f) }, -100..100, steps = 0, format = ::signed)
        SliderRow("Contrast", s({ (it.contrast * 100).roundToInt() }) { copy(contrast = it / 100f) }, 50..150, "%", steps = 0)
        SwitchRow("Invert colors", s({ it.invert }) { copy(invert = it) })
    }
    Hint("Adjustments change the app's own colors, background included.")
}

private fun signed(v: Int) = if (v > 0) "+$v" else "$v"

@Composable
private fun LayoutTab(draft: EditorDraft, set: (EditorDraft) -> Unit) {
    fun <T> s(get: (IconEdit) -> T, put: IconEdit.(T) -> IconEdit) = styleSetting(draft, set, get, put)
    SettingsGroup("Foreground") {
        SliderRow("Foreground size", s({ (it.scale * 100).roundToInt() }) { copy(scale = it / 100f) }, 40..160, "%", steps = 0)
        SliderRow("Move sideways", s({ (it.offsetX * 100).roundToInt() }) { copy(offsetX = it / 100f) }, -30..30, steps = 0, format = ::signed)
        SliderRow("Move up or down", s({ (it.offsetY * 100).roundToInt() }) { copy(offsetY = it / 100f) }, -30..30, steps = 0, format = ::signed)
        SliderRow("Rotate", s({ it.rotation }) { copy(rotation = it) }, -180..180, "°", steps = 0)
        SwitchRow("Mirror", s({ it.flip }) { copy(flip = it) }, "Flip the foreground left to right")
    }
    SettingsGroup("Whole icon") {
        SliderRow("Icon padding", s({ (it.padding * 100).roundToInt() }) { copy(padding = it / 100f) }, 0..25, "%", steps = 0)
    }
    Hint("Foreground size, position and rotation move the glyph inside its shape; padding shrinks the whole icon.")
}

@Composable
private fun EffectsTab(draft: EditorDraft, set: (EditorDraft) -> Unit) {
    fun <T> s(get: (IconEdit) -> T, put: IconEdit.(T) -> IconEdit) = styleSetting(draft, set, get, put)
    SettingsGroup("Outline") {
        SliderRow("Outline width", s({ (it.outlineWidth * 100).roundToInt() }) { copy(outlineWidth = it / 100f) }, 0..12, "%", steps = 0)
        if (draft.edit.outlineWidth > 0f) IconColorRow("Outline color", s({ it.outlineColor }) { copy(outlineColor = it) })
    }
    SettingsGroup("Depth") {
        SwitchRow("Shadow", s({ it.shadow }) { copy(shadow = it) }, "A soft shadow under the shape")
    }
}
