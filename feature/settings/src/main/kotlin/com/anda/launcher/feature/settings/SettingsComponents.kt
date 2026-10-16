package com.anda.launcher.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.LauncherSettings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// ---- Binding -------------------------------------------------------------------------------------

internal typealias Update = ((LauncherSettings) -> LauncherSettings) -> Unit

/** Factory defaults; a row whose value differs shows the "changed" dot and can be reset. */
internal val DefaultSettings = LauncherSettings()

/**
 * One setting: its stored value, its default, how to save it, and how to show an uncommitted
 * value (a slider mid-drag) in the live preview without writing DataStore every frame.
 */
@Stable
internal class Setting<T>(
    val value: T,
    val default: T,
    private val commit: (T) -> Unit,
    private val showPreview: (T) -> Unit,
) {
    val modified: Boolean get() = value != default
    fun set(v: T) = commit(v)
    fun preview(v: T) = showPreview(v)
    fun reset() = commit(default)
}

/** Creates [Setting]s for one screen. [setDraft] feeds the screen's live preview. */
@Stable
internal class SettingsBinder(
    val stored: LauncherSettings,
    private val update: Update,
    private val setDraft: (LauncherSettings) -> Unit,
) {
    fun <T> bind(get: (LauncherSettings) -> T, put: LauncherSettings.(T) -> LauncherSettings): Setting<T> =
        Setting(
            value = get(stored),
            default = get(DefaultSettings),
            commit = { v -> update { it.put(v) } },
            showPreview = { v -> setDraft(stored.put(v)) },
        )

    fun update(block: (LauncherSettings) -> LauncherSettings) = update.invoke(block)
}

/** Title of the row a settings search jumped to; that row scrolls into view and flashes. */
internal val LocalSettingsHighlight = compositionLocalOf<String?> { null }

// ---- Structure -----------------------------------------------------------------------------------

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** A rounded card of related rows. Calmer than one long flat list. */
@Composable
internal fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) SectionHeader(title) else Spacer(Modifier.size(12.dp))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

/**
 * Power-user options, folded by default. Opens by itself when a search lands on one of its rows.
 */
@Composable
internal fun AdvancedSection(key: String, count: Int, content: @Composable ColumnScope.() -> Unit) {
    val forceOpen = LocalSettingsHighlight.current != null
    var expanded by rememberSaveable(key) { mutableStateOf(forceOpen) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Spacer(Modifier.size(12.dp))
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Advanced", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$count", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                )
            }
            AnimatedVisibility(expanded || forceOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    content()
                }
            }
        }
    }
}

/** Gray helper text under a group. */
@Composable
internal fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
    )
}

// ---- Row frame -----------------------------------------------------------------------------------

/**
 * Shared row chrome: click, long-press-to-reset when [modified], the search highlight, and a
 * "Reset to default" accessibility action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingFrame(
    title: String,
    modified: Boolean = false,
    onReset: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val highlighted = LocalSettingsHighlight.current == title
    val requester = remember { BringIntoViewRequester() }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            delay(300)
            requester.bringIntoView()
            flash.snapTo(1f)
            flash.animateTo(0f, tween(1600))
        }
    }
    val canReset = modified && onReset != null && enabled
    val highlightColor = MaterialTheme.colorScheme.primaryContainer
    Column(
        Modifier.fillMaxWidth()
            .bringIntoViewRequester(requester)
            .background(highlightColor.copy(alpha = flash.value * 0.7f))
            .then(
                if (onClick != null || canReset) {
                    Modifier.combinedClickable(
                        enabled = enabled,
                        onClick = { onClick?.invoke() },
                        onLongClick = if (canReset) {
                            {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onReset?.invoke()
                            }
                        } else {
                            null
                        },
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                if (canReset) customActions = listOf(CustomAccessibilityAction("Reset to default") { onReset?.invoke(); true })
            }
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .padding(contentPadding),
        content = content,
    )
}

/** Title with the "changed from default" dot. */
@Composable
internal fun SettingTitle(title: String, modified: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (modified) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable
private fun Summary(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---- Rows ----------------------------------------------------------------------------------------

@Composable
fun NavRow(title: String, summary: String? = null, icon: ImageVector? = null, onClick: () -> Unit) {
    SettingFrame(title, onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) Summary(summary)
            }
        }
    }
}

@Composable
internal fun SwitchRow(title: String, setting: Setting<Boolean>, summary: String? = null, enabled: Boolean = true) =
    SwitchRow(title, setting.value, summary, enabled, setting.modified, setting::reset, setting::set)

@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    summary: String? = null,
    enabled: Boolean = true,
    modified: Boolean = false,
    onReset: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    SettingFrame(title, modified, onReset, onClick = { onChange(!checked) }, enabled = enabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                SettingTitle(title, modified)
                if (summary != null) Summary(summary)
            }
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}

/**
 * Integer slider. The preview follows the thumb live; DataStore is written once, on release.
 */
@Composable
internal fun SliderRow(title: String, setting: Setting<Int>, range: IntRange, suffix: String = "", format: (Int) -> String = { "$it$suffix" }) {
    var local by remember(setting.value) { mutableFloatStateOf(setting.value.toFloat()) }
    SettingFrame(title, setting.modified, setting::reset, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingTitle(title, setting.modified, Modifier.weight(1f))
            Text(format(local.roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                setting.preview(it.roundToInt())
            },
            onValueChangeFinished = { setting.set(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
internal fun FloatSliderRow(title: String, setting: Setting<Float>, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    var local by remember(setting.value) { mutableFloatStateOf(setting.value) }
    SettingFrame(title, setting.modified, setting::reset, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingTitle(title, setting.modified, Modifier.weight(1f))
            Text("${(local * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                setting.preview(it)
            },
            onValueChangeFinished = { setting.set(local) },
            valueRange = range,
        )
    }
}

/** Long option lists: a dialog of radio buttons. */
@Composable
internal fun <T> ChoiceRow(title: String, setting: Setting<T>, options: List<T>, label: (T) -> String = ::defaultLabel) =
    ChoiceRow(title, setting.value, options, label, setting.modified, setting::reset, setting::set)

@Composable
internal fun <T> ChoiceRow(
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String = ::defaultLabel,
    modified: Boolean = false,
    onReset: (() -> Unit)? = null,
    onChange: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingFrame(title, modified, onReset, onClick = { open = true }) {
        SettingTitle(title, modified)
        Summary(label(value))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onChange(option); open = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == value, onClick = { onChange(option); open = false })
                            Text(label(option))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

/** 2–4 options: inline segmented buttons, one tap instead of a dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SegmentedRow(title: String, setting: Setting<T>, options: List<T>, label: (T) -> String = ::defaultLabel) {
    SettingFrame(title, setting.modified, setting::reset, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        SettingTitle(title, setting.modified)
        Spacer(Modifier.size(8.dp))
        // Intrinsic height + fillMaxHeight: a label that wraps to two lines grows every segment alike.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == setting.value,
                    onClick = { setting.set(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    modifier = Modifier.fillMaxHeight(),
                    // From four options a segment is ~80 dp: the checkmark pushed labels like
                    // "Above dock" into an ellipsis. The filled segment already marks the choice.
                    icon = { if (options.size <= 3) SegmentedButtonDefaults.Icon(option == setting.value) },
                    label = { Text(label(option), maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) },
                )
            }
        }
    }
}

/**
 * Options that are visual (icon shape, folder preview, dock shape): a row of rendered swatches,
 * so the choice looks like the result instead of a word.
 */
@Composable
internal fun <T> VisualChoiceRow(
    title: String,
    setting: Setting<T>,
    options: List<T>,
    label: (T) -> String = ::defaultLabel,
    swatch: @Composable (option: T) -> Unit,
) {
    SettingFrame(title, setting.modified, setting::reset, contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)) {
        SettingTitle(title, setting.modified, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.size(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(options) { option ->
                val selected = option == setting.value
                Column(
                    Modifier.clip(MaterialTheme.shapes.medium)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.medium,
                        )
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .clickable { setting.set(option) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { swatch(option) }
                    Text(label(option), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

private val palette = listOf(
    null, 0xFFFFFFFF, 0xFF000000, 0xFF202124, 0xFF4285F4, 0xFF34A853, 0xFFFBBC04, 0xFFEA4335,
    0xFF9C27B0, 0xFF00BCD4, 0xFFFF7043, 0xFF8D6E63, 0xFF607D8B, 0xFFE91E63,
)

/** Color choice from a palette; `null` = follow the theme ("A" for automatic). */
@Composable
internal fun ColorRow(title: String, setting: Setting<Long?>) = ColorRow(title, setting.value, setting.modified, setting::reset, setting::set)

@Composable
internal fun ColorRow(title: String, value: Long?, modified: Boolean = false, onReset: (() -> Unit)? = null, onChange: (Long?) -> Unit) {
    SettingFrame(title, modified, onReset, contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)) {
        SettingTitle(title, modified, Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(palette) { color ->
                val selected = color == value
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary)
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onChange(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (color == null) Text("A", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

internal fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** `BelowDock` → "Below dock", `InstallDate` → "Install date". */
internal fun humanize(name: String): String =
    name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.uppercase() }

internal fun <T> defaultLabel(value: T): String = if (value is Enum<*>) humanize(value.name) else value.toString()
