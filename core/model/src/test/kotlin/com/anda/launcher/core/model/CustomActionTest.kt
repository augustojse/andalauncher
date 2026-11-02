package com.anda.launcher.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomActionTest {
    private val maps = CustomAction("Maps", intentAction = "android.intent.action.VIEW", data = "geo:0,0?q=%s")

    @Test
    fun theQueryIsEncodedIntoTheData() {
        assertEquals("geo:0,0?q=caf%C3%A9%20%26%20bar", maps.dataFor("café & bar"))
    }

    @Test
    fun anExtraOnlyActionHasNoData() {
        val translate = CustomAction("Translate", intentAction = "android.intent.action.PROCESS_TEXT", extraKey = "android.intent.extra.PROCESS_TEXT", mimeType = "text/plain")
        assertTrue(translate.isValid)
        assertNull(translate.dataFor("hola"))
    }

    @Test
    fun theQueryMustGoSomewhere() {
        assertTrue(maps.isValid)
        assertFalse(maps.copy(data = "geo:0,0").isValid)
        assertTrue(maps.copy(data = "geo:0,0", extraKey = "query").isValid)
    }

    @Test
    fun brokenFieldsAreRefused() {
        assertFalse(maps.copy(label = " ").isValid)
        assertFalse(maps.copy(intentAction = "not an action").isValid)
        assertFalse(maps.copy(data = "no scheme %s").isValid)
        assertFalse(maps.copy(data = "javascript:alert('%s')").isValid)
        assertFalse(maps.copy(data = "file:///sdcard/%s").isValid)
        assertFalse(maps.copy(packageName = "not a package").isValid)
        assertFalse(maps.copy(mimeType = "plain").isValid)
    }

    @Test
    fun anAppSearchNeedsOnlyAPackage() {
        assertTrue(CustomAction("Spotify", packageName = "com.spotify.music").isAppSearch)
        assertTrue(CustomAction("Spotify", packageName = "com.spotify.music").isValid)
        assertFalse(CustomAction("Spotify").isValid)
        assertFalse(CustomAction("Spotify", packageName = "com.spotify.music", data = "x:%s").isValid)
    }

    @Test
    fun sanitizeDropsBrokenActionsAndCapsTheList() {
        val actions = listOf(CustomAction(""), maps.copy(data = "file:///%s")) + List(15) { maps.copy(label = "  Maps ") }
        val safe = LauncherSettings(search = SearchSettings(customActions = actions)).sanitized().search.customActions
        assertEquals(CustomAction.MAX_ACTIONS, safe.size)
        assertTrue(safe.all { it.label == "Maps" })
    }

    @Test
    fun builtInOrderPutsTheSavedOrderFirst() {
        val order = QuickActionKind.ordered(listOf(QuickActionKind.SetTimer, QuickActionKind.Call))
        assertEquals(listOf(QuickActionKind.SetTimer, QuickActionKind.Call, QuickActionKind.Message), order.take(3))
        assertEquals(QuickActionKind.entries.toSet(), order.toSet())
        assertEquals(QuickActionKind.entries.size, order.size)
    }

    @Test
    fun anUnknownKindFromANewerVersionIsSkipped() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<SearchSettings>("""{"quickActionOrder":["SetTimer","Teleport","Call"],"disabledQuickActions":["Teleport"]}""")
        assertEquals(listOf(QuickActionKind.SetTimer, QuickActionKind.Call), decoded.quickActionOrder)
        assertEquals(emptyList<QuickActionKind>(), decoded.disabledQuickActions)
    }
}
