package com.anda.launcher.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsIndexTest {

    /** Unit tests run with the module directory as the working directory. Screens may live in any file. */
    private val screensSource: String =
        File("src/main/kotlin/com/anda/launcher/feature/settings").walk()
            .filter { it.isFile && (it.name.endsWith("Screen.kt") || it.name.endsWith("Screens.kt")) }
            .joinToString("\n") { it.readText() }

    @Test
    fun everyIndexedTitleIsARealRow() {
        // The title is the highlight key: a renamed row would make search land on nothing.
        val missing = SettingsIndex.entries.filter { "\"${it.title}\"" !in screensSource }
        assertTrue("Index titles not found in the settings screens: ${missing.map { it.title }}", missing.isEmpty())
    }

    @Test
    fun titlesAreUniquePerScreen() {
        val dupes = SettingsIndex.entries.groupBy { it.screen to it.title }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate index entries: $dupes", dupes.isEmpty())
    }

    @Test
    fun keywordsFindTuckedAwayOptions() {
        assertEquals("Subgrid positioning", SettingsIndex.search("half cell").first().title)
        assertEquals("Show status bar", SettingsIndex.search("status").first().title)
        assertEquals(Screen.Drawer, SettingsIndex.search("one hand").first().screen)
    }

    @Test
    fun titlePrefixRanksFirst() {
        assertEquals("Wallpaper dimming", SettingsIndex.search("wallpaper dim").first().title)
        assertEquals("Columns", SettingsIndex.search("col").first().title)
    }

    @Test
    fun everyQueryWordMustMatch() {
        assertTrue(SettingsIndex.search("subgrid banana").isEmpty())
        assertTrue(SettingsIndex.search("   ").isEmpty())
    }

    @Test
    fun humanizeSplitsEnumNames() {
        assertEquals("Below dock", humanize("BelowDock"))
        assertEquals("Vertical grid", humanize("VerticalGrid"))
        assertEquals("Dots", humanize("Dots"))
    }
}
