package com.anda.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridOccupancyTest {
    private val spec = GridSpec(columns = 4, rows = 4)

    private fun app(id: Long, cellX: Int, cellY: Int) = AppItem(
        id = id, container = Containers.DESKTOP, screenId = 1, rect = spec.cellRect(cellX, cellY),
        rank = 0, key = ComponentKey("p", "c$id"),
    )

    @Test
    fun findVacant_skipsOccupiedCells() {
        val grid = GridOccupancy(spec, listOf(app(1, 0, 0), app(2, 1, 0)))
        assertEquals(spec.cellRect(2, 0), grid.findVacant(2, 2))
    }

    @Test
    fun findVacant_returnsNullWhenFull() {
        val items = (0 until 16).map { app(it.toLong(), it % 4, it / 4) }
        assertNull(GridOccupancy(spec, items).findVacant(2, 2))
    }

    @Test
    fun ignoredItemDoesNotBlockItsOwnMove() {
        val grid = GridOccupancy(spec, listOf(app(1, 0, 0)), ignoreId = 1)
        assertTrue(grid.isFree(spec.cellRect(0, 0)))
    }

    @Test
    fun subgridAllowsHalfCellPlacement() {
        val grid = GridOccupancy(spec, listOf(app(1, 0, 0)))
        assertTrue(grid.isFree(CellRect(2, 1, 2, 2)))
        assertFalse(grid.isFree(CellRect(1, 1, 2, 2)))
    }

    @Test
    fun nearestVacant_prefersClosestFreeSpot() {
        val grid = GridOccupancy(spec, listOf(app(1, 1, 1)))
        assertEquals(spec.cellRect(2, 1), grid.nearestVacant(3, 2, 2, 2, GridSpec.SUBGRID))
    }

    @Test
    fun outOfBoundsIsNeverFree() {
        val grid = GridOccupancy(spec, emptyList())
        assertFalse(grid.isFree(CellRect(7, 0, 2, 2)))
        assertFalse(grid.isFree(CellRect(-1, 0, 2, 2)))
    }
}
