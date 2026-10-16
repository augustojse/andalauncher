package com.anda.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.CellRect
import com.anda.launcher.core.model.GridOccupancy
import com.anda.launcher.core.model.GridSpec
import com.anda.launcher.core.model.HomeItem
import com.anda.launcher.core.model.WidgetItem
import com.anda.launcher.core.model.WidgetStackItem
import com.anda.launcher.core.ui.LocalLauncherSettings
import kotlin.math.roundToInt

private enum class Edge { Left, Top, Right, Bottom }

/**
 * Launcher3-style resize frame with four handles. Snaps to cells (or half cells with subgrid),
 * respects the widget's minimum size and never overlaps other items.
 */
@Composable
internal fun ResizeFrame(item: HomeItem, spec: GridSpec, controller: HomeController, pageItems: List<HomeItem>, modifier: Modifier) {
    val subgrid = LocalLauncherSettings.current.home.subgridPositioning
    val step = if (subgrid) 1 else GridSpec.SUBGRID
    var rect by remember(item.id, item.rect) { mutableStateOf(item.rect) }
    val minSpan = remember(item.id) { minSpan(item, controller) }

    BoxWithConstraints(modifier.pointerInput(item.id) {
        detectTapGestures {
            if (rect != item.rect) controller.resize(item, rect)
            controller.resizingItemId = null
        }
    }) {
        val density = LocalDensity.current
        val unitW = with(density) { maxWidth.toPx() } / spec.fineColumns
        val unitH = with(density) { maxHeight.toPx() } / spec.fineRows
        val occupancy = remember(pageItems, item.id) { GridOccupancy(spec, pageItems, ignoreId = item.id) }

        fun tryResize(edge: Edge, deltaUnits: Int) {
            if (deltaUnits == 0) return
            val d = deltaUnits * step
            val next = when (edge) {
                Edge.Left -> rect.copy(x = rect.x + d, spanX = rect.spanX - d)
                Edge.Right -> rect.copy(spanX = rect.spanX + d)
                Edge.Top -> rect.copy(y = rect.y + d, spanY = rect.spanY - d)
                Edge.Bottom -> rect.copy(spanY = rect.spanY + d)
            }
            if (next.spanX < minSpan.first * GridSpec.SUBGRID || next.spanY < minSpan.second * GridSpec.SUBGRID) return
            if (!spec.fits(next) || !occupancy.isFree(next)) return
            rect = next
        }

        val left = rect.x * unitW
        val top = rect.y * unitH
        val width = rect.spanX * unitW
        val height = rect.spanY * unitH
        Box(
            Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        ) {
            Edge.entries.forEach { edge ->
                var accumulated by remember { mutableFloatStateOf(0f) }
                val alignment = when (edge) {
                    Edge.Left -> Alignment.CenterStart
                    Edge.Right -> Alignment.CenterEnd
                    Edge.Top -> Alignment.TopCenter
                    Edge.Bottom -> Alignment.BottomCenter
                }
                Box(
                    Modifier.align(alignment).size(28.dp).pointerInput(edge, rect) {
                        detectDragGestures(onDragEnd = { accumulated = 0f }) { change, drag ->
                            change.consume()
                            val unit = (if (edge == Edge.Left || edge == Edge.Right) unitW else unitH) * step
                            accumulated += if (edge == Edge.Left || edge == Edge.Right) drag.x else drag.y
                            val units = (accumulated / unit).toInt()
                            if (units != 0) {
                                tryResize(edge, units)
                                accumulated -= units * unit
                            }
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

private fun minSpan(item: HomeItem, controller: HomeController): Pair<Int, Int> {
    val widget = when (item) {
        is WidgetItem -> item
        is WidgetStackItem -> item.widgets.firstOrNull()
        else -> null
    } ?: return 1 to 1
    val info = controller.widgets.info(widget.appWidgetId) ?: return 1 to 1
    val bounds = controller.pageBounds.values.firstOrNull() ?: return 1 to 1
    val density = android.content.res.Resources.getSystem().displayMetrics.density
    val cellW = bounds.width / controller.gridSpec.columns / density
    val cellH = bounds.height / controller.gridSpec.rows / density
    return controller.widgets.minSpan(info, cellW, cellH)
}
