package com.anda.launcher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.model.PageIndicatorStyle
import com.anda.launcher.core.model.PageTransition
import com.anda.launcher.core.model.SearchBarPosition
import com.anda.launcher.core.ui.LocalLauncherSettings
import kotlin.math.roundToInt

/** Intent extra: open the home screen with the Customize panel showing (from settings previews). */
const val EXTRA_CUSTOMIZE = "com.anda.launcher.extra.CUSTOMIZE"

/**
 * The most-used home options, applied to the **real** home screen while you adjust them: the
 * most faithful preview there is. Dragging a slider makes the panel nearly transparent so the
 * whole home stays visible; the value is saved on release.
 */
@Composable
fun CustomizeSheet(controller: HomeController, host: HomeHost) {
    val settings = LocalLauncherSettings.current
    var adjusting by remember { mutableStateOf(false) }
    val panelAlpha by animateFloatAsState(if (adjusting) 0.12f else 1f, label = "customizeAlpha")
    if (controller.customizeOpen) BackHandler { controller.closeCustomize() }

    Box(Modifier.fillMaxSize()) {
        if (controller.customizeOpen) {
            // Tap outside closes; transparent so the home stays fully visible.
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { controller.closeCustomize() } })
        }
        AnimatedVisibility(
            visible = controller.customizeOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } * 0.55f
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).graphicsLayer { alpha = panelAlpha }
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
                    Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Customize home", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { controller.closeCustomize(); host.openSettings("Home") }) { Text("All settings") }
                    }
                    val onAdjust: (Boolean) -> Unit = { adjusting = it }
                    LiveSlider("Columns", settings.home.columns, 3..10, controller, onAdjust) { s, v -> s.copy(home = s.home.copy(columns = v)) }
                    LiveSlider("Rows", settings.home.rows, 3..12, controller, onAdjust) { s, v -> s.copy(home = s.home.copy(rows = v)) }
                    LiveSlider("Icon size", settings.icons.home.sizePercent, 50..150, controller, onAdjust, suffix = "%") { s, v ->
                        s.copy(icons = s.icons.copy(home = s.icons.home.copy(sizePercent = v)))
                    }
                    ToggleLine("Labels", settings.icons.home.showLabels) { v ->
                        controller.updateSettings { it.copy(icons = it.icons.copy(home = it.icons.home.copy(showLabels = v))) }
                    }
                    ToggleLine("Dock", settings.dock.enabled) { v -> controller.updateSettings { it.copy(dock = it.dock.copy(enabled = v)) } }
                    Segmented("Search bar", settings.home.searchBar, SearchBarPosition.entries, {
                        when (it) {
                            SearchBarPosition.None -> "None"
                            SearchBarPosition.Top -> "Top"
                            SearchBarPosition.AboveDock -> "Above dock"
                            SearchBarPosition.BelowDock -> "Below dock"
                        }
                    }) { v -> controller.updateSettings { it.copy(home = it.home.copy(searchBar = v)) } }
                    Segmented("Page indicator", settings.home.pageIndicator, PageIndicatorStyle.entries, { it.name }) { v ->
                        controller.updateSettings { it.copy(home = it.home.copy(pageIndicator = v)) }
                    }
                    Text("Scroll effect", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 24.dp, top = 12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PageTransition.entries) { t ->
                            FilterChip(
                                selected = settings.home.pageTransition == t,
                                onClick = { controller.updateSettings { it.copy(home = it.home.copy(pageTransition = t)) } },
                                label = { Text(t.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSlider(
    title: String,
    value: Int,
    range: IntRange,
    controller: HomeController,
    onAdjust: (Boolean) -> Unit,
    suffix: String = "",
    put: (LauncherSettings, Int) -> LauncherSettings,
) {
    val stored = LocalLauncherSettings.current
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("${local.roundToInt()}$suffix", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                onAdjust(true)
                controller.draftSettings = put(controller.draftSettings ?: stored, it.roundToInt())
            },
            onValueChangeFinished = {
                onAdjust(false)
                val v = local.roundToInt()
                controller.updateSettings { s -> put(s, v) }
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ToggleLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(title: String, value: T, options: List<T>, label: (T) -> String, onChange: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == value,
                    onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    label = { Text(label(option), maxLines = 1) },
                )
            }
        }
    }
}
