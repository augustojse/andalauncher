package com.anda.launcher.core.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterParserTest {
    private fun tag(name: String, vararg attrs: Pair<String, String>) = XmlTag(name, attrs.toMap())

    @Test
    fun parsesItemsBacksMasksAndScale() {
        val filter = AppFilterParser.parse(
            sequenceOf(
                tag("resources"),
                tag("iconback", "img2" to "back2", "img1" to "back1"),
                tag("iconmask", "img1" to "mask"),
                tag("iconupon", "img1" to "upon"),
                tag("scale", "factor" to "0.75"),
                tag("item", "component" to "ComponentInfo{com.a/com.a.Main}", "drawable" to "a"),
                tag("item", "component" to "ComponentInfo{com.b/.Main}", "drawable" to "b"),
                tag("item", "component" to "ComponentInfo{com.a/com.a.Main}", "drawable" to "duplicate"),
                tag("item", "component" to ":LAUNCHER_ACTION_APP_DRAWER", "drawable" to "drawer"),
                tag("calendar", "component" to "ComponentInfo{com.cal/com.cal.Main}", "prefix" to "cal_"),
            ),
        )
        assertEquals("a", filter.components["com.a/com.a.Main"])
        assertEquals("b", filter.components["com.b/com.b.Main"])
        assertEquals(listOf("back1", "back2"), filter.backs)
        assertEquals(listOf("mask"), filter.masks)
        assertEquals(0.75f, filter.scale)
        assertEquals("cal_", filter.calendars["com.cal/com.cal.Main"])
        assertEquals(2, filter.components.size)
        assertTrue(filter.hasFallbackMask)
    }

    @Test
    fun malformedComponentsAreIgnored() {
        assertNull(AppFilterParser.normalizeComponent("ComponentInfo{}"))
        assertNull(AppFilterParser.normalizeComponent("garbage"))
    }
}
