package com.anda.launcher.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.anda.launcher.core.model.CellRect
import kotlin.math.roundToInt

private class CellParentData(val rect: CellRect) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@CellParentData
}

/** Places a child at [rect] (subgrid units) inside a [CellLayout]. */
fun Modifier.cell(rect: CellRect): Modifier = this.then(CellParentData(rect))

/**
 * Grid page layout (ADR 0001): a custom Layout that positions children by cell rect. No lazy
 * measurement, no subcomposition, so a page measures in one pass.
 */
@Composable
fun CellLayout(
    fineColumns: Int,
    fineRows: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val unitW = width.toFloat() / fineColumns
        val unitH = height.toFloat() / fineRows
        val placeables = measurables.map { measurable ->
            val rect = (measurable.parentData as? CellParentData)?.rect ?: CellRect(0, 0, 1, 1)
            val w = (rect.spanX * unitW).roundToInt().coerceAtLeast(0)
            val h = (rect.spanY * unitH).roundToInt().coerceAtLeast(0)
            rect to measurable.measure(Constraints.fixed(w, h))
        }
        layout(width, height) {
            placeables.forEach { (rect, placeable) ->
                placeable.place((rect.x * unitW).roundToInt(), (rect.y * unitH).roundToInt())
            }
        }
    }
}
