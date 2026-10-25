package com.anda.launcher.core.icons

import com.anda.launcher.core.model.IconEdit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IconColorMatrixTest {

    @Test
    fun plainEditIsIdentity() {
        assertArrayEquals(IconColorMatrix.Identity, IconColorMatrix.of(IconEdit()), 0f)
    }

    @Test
    fun zeroSaturationMakesGray() {
        val (r, g, b) = IconColorMatrix.apply(IconColorMatrix.of(IconEdit(saturation = 0f)), 200, 40, 40)
        assertTrue("expected gray, got $r $g $b", abs(r - g) <= 1 && abs(g - b) <= 1)
    }

    @Test
    fun hueRotationKeepsGrayAndMovesRed() {
        val hue = IconColorMatrix.of(IconEdit(hue = 120))
        assertEquals(Triple(128, 128, 128), IconColorMatrix.apply(hue, 128, 128, 128).roundGray())
        val (r, g, _) = IconColorMatrix.apply(hue, 255, 0, 0)
        assertTrue("red should turn greenish, got r=$r g=$g", g > r)
    }

    @Test
    fun invertFlipsChannels() {
        assertEquals(Triple(255, 155, 0), IconColorMatrix.apply(IconColorMatrix.of(IconEdit(invert = true)), 0, 100, 255))
    }

    @Test
    fun brightnessAndContrastMoveAroundMidGray() {
        val (light, _, _) = IconColorMatrix.apply(IconColorMatrix.of(IconEdit(brightness = 1f)), 0, 0, 0)
        assertEquals(127, light)
        val flat = IconColorMatrix.of(IconEdit(contrast = 0.5f))
        assertEquals(63, IconColorMatrix.apply(flat, 0, 0, 0).first)
        assertEquals(191, IconColorMatrix.apply(flat, 255, 255, 255).first)
    }

    @Test
    fun concatAppliesRightThenLeft() {
        // Invert, then full brightness up: black → white → stays white (clamped).
        val m = IconColorMatrix.concat(IconColorMatrix.brightness(1f), IconColorMatrix.invert())
        assertEquals(Triple(255, 255, 255), IconColorMatrix.apply(m, 0, 0, 0))
    }

    /** Hue rotation is exact only up to float rounding; snap near-equal channels. */
    private fun Triple<Int, Int, Int>.roundGray(): Triple<Int, Int, Int> =
        if (abs(first - second) <= 1 && abs(second - third) <= 1) Triple(second, second, second) else this
}
