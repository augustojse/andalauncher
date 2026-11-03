package com.anda.launcher.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesTest {
    private val maps = Favorite.App(ComponentKey("com.example.maps", ".Main"), "Maps")
    private val anna = Favorite.Contact("Anna", "content://com.android.contacts/contacts/lookup/abc/1", phone = "+1 555 0100")
    private val dentist = Favorite.Event(7, "Dentist", begin = 1_000, end = 2_000, allDay = false, color = 0xFF3366CC.toInt())
    private val wifi = Favorite.Setting("Wi-Fi", "android.settings.WIFI_SETTINGS")
    private val compose = Favorite.Shortcut("com.example.mail", "compose", 0, "Compose", "Mail")

    @Test
    fun pinningAddsOnceAtTheEnd() {
        val pinned = FavoriteOrder.pin(FavoriteOrder.pin(listOf(maps), anna), anna)
        assertEquals(listOf(maps, anna), pinned)
    }

    @Test
    fun aFullRowTakesNoMore() {
        val full = List(Favorite.MAX_FAVORITES) { Favorite.Setting("Page $it", "action.$it") }
        assertEquals(full, FavoriteOrder.pin(full, maps))
    }

    @Test
    fun unpinningRemovesOnlyThatItem() {
        assertEquals(listOf(maps, wifi), FavoriteOrder.unpin(listOf(maps, anna, wifi), anna.key))
    }

    @Test
    fun movingTakesTheNeighboursPlace() {
        val row = listOf(maps, anna, dentist, wifi)
        assertEquals(listOf(maps, dentist, anna, wifi), FavoriteOrder.moveTo(row, dentist.key, anna.key))
        assertEquals(listOf(maps, dentist, anna, wifi), FavoriteOrder.moveTo(row, anna.key, dentist.key))
        // A pin hidden between them (an app hidden from the drawer) is stepped over.
        assertEquals(listOf(anna, dentist, maps, wifi), FavoriteOrder.moveTo(row, maps.key, dentist.key))
        assertEquals(row, FavoriteOrder.moveTo(row, maps.key, ItemKey(ItemKey.APP, "gone")))
    }

    @Test
    fun keysAreUniqueAcrossKinds() {
        val keys = listOf(maps, anna, dentist, wifi, compose).map { it.key.flatten() }
        assertEquals(keys.toSet().size, keys.size)
        assertEquals("app:com.example.maps/.Main#0", maps.key.flatten())
        assertEquals("event:7", dentist.key.flatten())
    }

    @Test
    fun appsAndShortcutsGoWithTheirPackage() {
        assertTrue(maps.belongsTo("com.example.maps", 0))
        assertFalse(maps.belongsTo("com.example.maps", 10))
        assertTrue(compose.belongsTo("com.example.mail", 0))
        assertFalse(anna.belongsTo("com.example.maps", 0))
    }

    @Test
    fun everyKindSurvivesJson() {
        val json = Json { ignoreUnknownKeys = true }
        listOf(maps, anna, dentist, wifi, compose).forEach { favorite ->
            assertEquals(favorite, json.decodeFromString(Favorite.serializer(), json.encodeToString(Favorite.serializer(), favorite)))
        }
    }
}
