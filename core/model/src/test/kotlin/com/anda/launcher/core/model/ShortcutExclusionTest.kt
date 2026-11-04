package com.anda.launcher.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ShortcutExclusionTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun hidingAddsEachAppOnceAndKeepsTheOrder() {
        val s = SearchSettings().withShortcutsHidden("com.b").withShortcutsHidden("com.a").withShortcutsHidden("com.b")
        assertEquals(listOf("com.b", "com.a"), s.excludedShortcutApps)
    }

    @Test
    fun hidingTrimsAndIgnoresBlankNames() {
        val s = SearchSettings().withShortcutsHidden("  com.a ")
        assertEquals(listOf("com.a"), s.excludedShortcutApps)
        assertSame(s, s.withShortcutsHidden("   "))
        assertSame(s, s.withShortcutsHidden(""))
    }

    @Test
    fun theListStopsAtTheCap() {
        var s = SearchSettings()
        repeat(SearchSettings.MAX_EXCLUDED_SHORTCUT_APPS + 5) { s = s.withShortcutsHidden("com.app$it") }
        assertEquals(SearchSettings.MAX_EXCLUDED_SHORTCUT_APPS, s.excludedShortcutApps.size)
        assertEquals("com.app0", s.excludedShortcutApps.first())
    }

    @Test
    fun showingBringsAnAppBackAndLeavesTheRest() {
        val s = SearchSettings().withShortcutsHidden("com.a").withShortcutsHidden("com.b").withShortcutsShown("com.a")
        assertEquals(listOf("com.b"), s.excludedShortcutApps)
        assertEquals(s, s.withShortcutsShown("com.never.hidden"))
    }

    @Test
    fun sanitizedDropsBlanksAndRepeatsAndCapsTheList() {
        val messy = LauncherSettings(
            search = SearchSettings(excludedShortcutApps = listOf(" com.a ", "", "com.a", "  ", "com.b")),
        ).sanitized()
        assertEquals(listOf("com.a", "com.b"), messy.search.excludedShortcutApps)

        val huge = LauncherSettings(
            search = SearchSettings(excludedShortcutApps = List(SearchSettings.MAX_EXCLUDED_SHORTCUT_APPS + 50) { "com.app$it" }),
        ).sanitized()
        assertEquals(SearchSettings.MAX_EXCLUDED_SHORTCUT_APPS, huge.search.excludedShortcutApps.size)
    }

    @Test
    fun aSettingsFileFromBefore0_7_0HidesNothing() {
        val old = json.decodeFromString<LauncherSettings>("""{"search":{"shortcuts":true,"contacts":true}}""")
        assertEquals(emptyList<String>(), old.search.excludedShortcutApps)
        assertEquals(true, old.search.contacts)
    }

    @Test
    fun theListSurvivesARoundTrip() {
        val settings = LauncherSettings(search = SearchSettings().withShortcutsHidden("com.a").withShortcutsHidden("com.b"))
        val decoded = json.decodeFromString<LauncherSettings>(json.encodeToString(settings))
        assertEquals(listOf("com.a", "com.b"), decoded.search.excludedShortcutApps)
    }
}
