package com.anda.launcher.feature.home

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.core.ui.TestTags
import com.anda.launcher.core.ui.drag.DragPayload
import com.anda.launcher.core.ui.drag.LocalDragController
import com.anda.launcher.core.ui.gesture.ItemGestureCallbacks
import com.anda.launcher.core.ui.gesture.launcherItemGestures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class WidgetGroup(val appLabel: String, val widgets: List<AppWidgetProviderInfo>)

/** Searchable widget picker. Tap adds to the first free spot; long-press drags onto a page. */
@Composable
fun WidgetPicker(controller: HomeController) {
    if (!controller.widgetPickerOpen) return
    val context = LocalContext.current
    val drag = LocalDragController.current
    val settings = LocalLauncherSettings.current
    var query by remember { mutableStateOf("") }
    BackHandler { controller.widgetPickerOpen = false }

    val groups by produceState(emptyList<WidgetGroup>()) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            controller.widgets.providers()
                .groupBy { it.provider.packageName }
                .map { (pkg, list) ->
                    val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    WidgetGroup(label, list.sortedBy { it.loadLabel(pm) })
                }
                .sortedBy { it.appLabel.lowercase() }
        }
    }
    val filtered = remember(groups, query) {
        if (query.isBlank()) groups else groups.filter { it.appLabel.contains(query, ignoreCase = true) }
    }

    // Stay composed (but invisible) while a widget is dragged out, so the gesture keeps running.
    Surface(
        modifier = Modifier.fillMaxSize().testTag(TestTags.WIDGET_PICKER).graphicsLayer { alpha = if (drag.isDragging) 0f else 1f },
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Widgets", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { controller.widgetPickerOpen = false }) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search widgets") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                items(filtered, key = { it.appLabel + it.widgets.first().provider.packageName }) { group ->
                    Text(group.appLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(group.widgets, key = { it.provider.flattenToString() + it.profile.hashCode() }) { info ->
                            WidgetPreviewCard(info, controller, settings.home.columns, settings.home.rows)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewCard(info: AppWidgetProviderInfo, controller: HomeController, columns: Int, rows: Int) {
    val context = LocalContext.current
    val drag = LocalDragController.current
    val density = LocalDensity.current.density
    val densityDpi = LocalConfiguration.current.densityDpi
    val cell = controller.pageBounds.values.firstOrNull()
    val cellW = cell?.let { it.width / columns / density } ?: 80f
    val cellH = cell?.let { it.height / rows / density } ?: 100f
    val (spanX, spanY) = remember(info) { controller.widgets.defaultSpan(info, columns, rows, cellW, cellH) }
    val label = remember(info) { info.loadLabel(context.packageManager) }
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.IO) {
            val drawable = info.loadPreviewImage(context, densityDpi) ?: info.loadIcon(context, densityDpi)
            drawable?.let { d ->
                val w = d.intrinsicWidth.coerceIn(1, 600)
                val h = d.intrinsicHeight.coerceIn(1, 600)
                d.toBitmap(w, h).asImageBitmap()
            }
        }
    }
    val positionRef = remember { CoordsRef() }
    val callbacks = remember(info) {
        object : ItemGestureCallbacks {
            override fun onTap(position: Offset) {
                controller.widgetPickerOpen = false
                controller.addWidgetToHome(info, spanX, spanY)
            }

            override fun onDragStart(position: Offset) {
                val key = ComponentKey(info.provider.packageName, info.provider.className, controller.graph.users.serialOf(info.profile))
                drag.start(DragPayload.NewWidget(key, spanX, spanY, label), preview, 96.dp, positionRef.toRoot(position), Offset.Zero)
            }

            override fun onDrag(delta: Offset, position: Offset) = drag.moveTo(positionRef.toRoot(position))

            override fun onRelease(dragged: Boolean) {
                if (dragged) {
                    drag.drop()
                    controller.widgetPickerOpen = false
                    controller.editMode = false
                }
            }
        }
    }
    Column(
        Modifier.width(160.dp).onGloballyPositioned { positionRef.coords = it }.launcherItemGestures(info, callbacks),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 96.dp, max = 140.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let { Image(it, contentDescription = null, modifier = Modifier.size(120.dp)) }
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Text("$spanX × $spanY", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.height(4.dp))
    }
}
