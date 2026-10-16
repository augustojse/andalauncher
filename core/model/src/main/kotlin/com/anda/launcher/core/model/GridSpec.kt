package com.anda.launcher.core.model

/**
 * Logical grid of a desktop page. Stored positions use [SUBGRID] fine units per cell.
 */
data class GridSpec(val columns: Int, val rows: Int) {
    val fineColumns: Int get() = columns * SUBGRID
    val fineRows: Int get() = rows * SUBGRID

    /** Rect for a whole-cell position. */
    fun cellRect(cellX: Int, cellY: Int, spanX: Int = 1, spanY: Int = 1): CellRect =
        CellRect(cellX * SUBGRID, cellY * SUBGRID, spanX * SUBGRID, spanY * SUBGRID)

    fun fits(rect: CellRect): Boolean =
        rect.x >= 0 && rect.y >= 0 && rect.right <= fineColumns && rect.bottom <= fineRows

    companion object {
        const val SUBGRID = 2
    }
}

/**
 * Occupancy map of one page, used to find free space when dropping or adding items.
 * Pure Kotlin so the placement logic is unit-tested on the JVM.
 */
class GridOccupancy(private val width: Int, private val height: Int) {
    private val cells = BooleanArray(width * height)

    constructor(spec: GridSpec, items: Iterable<HomeItem>, ignoreId: Long? = null) :
        this(spec.fineColumns, spec.fineRows) {
        items.forEach { if (it.id != ignoreId) mark(it.rect, true) }
    }

    fun mark(rect: CellRect, occupied: Boolean) {
        for (y in rect.y.coerceAtLeast(0) until rect.bottom.coerceAtMost(height)) {
            for (x in rect.x.coerceAtLeast(0) until rect.right.coerceAtMost(width)) {
                cells[y * width + x] = occupied
            }
        }
    }

    fun isFree(rect: CellRect): Boolean {
        if (rect.x < 0 || rect.y < 0 || rect.right > width || rect.bottom > height) return false
        for (y in rect.y until rect.bottom) {
            for (x in rect.x until rect.right) {
                if (cells[y * width + x]) return false
            }
        }
        return true
    }

    /** First free rect in reading order, positions aligned to [step] fine units. */
    fun findVacant(spanX: Int, spanY: Int, step: Int = GridSpec.SUBGRID): CellRect? {
        var y = 0
        while (y + spanY <= height) {
            var x = 0
            while (x + spanX <= width) {
                val rect = CellRect(x, y, spanX, spanY)
                if (isFree(rect)) return rect
                x += step
            }
            y += step
        }
        return null
    }

    /** Free rect whose origin is closest to ([targetX], [targetY]). */
    fun nearestVacant(targetX: Int, targetY: Int, spanX: Int, spanY: Int, step: Int): CellRect? {
        var best: CellRect? = null
        var bestDistance = Int.MAX_VALUE
        var y = 0
        while (y + spanY <= height) {
            var x = 0
            while (x + spanX <= width) {
                val rect = CellRect(x, y, spanX, spanY)
                if (isFree(rect)) {
                    val dx = x - targetX
                    val dy = y - targetY
                    val distance = dx * dx + dy * dy
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = rect
                    }
                }
                x += step
            }
            y += step
        }
        return best
    }
}
