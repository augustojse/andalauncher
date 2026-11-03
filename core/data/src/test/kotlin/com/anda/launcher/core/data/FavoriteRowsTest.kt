package com.anda.launcher.core.data

import com.anda.launcher.core.data.db.FavoriteEntity
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.Favorite
import com.anda.launcher.core.model.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteRowsTest {
    private val maps = Favorite.App(ComponentKey("com.example.maps", ".Main"), "Maps")
    private val wifi = Favorite.Setting("Wi-Fi", "android.settings.WIFI_SETTINGS")

    @Test
    fun rowsGetGaplessPositionsAndDecodeBack() {
        val rows = FavoritesRepository.encode(listOf(wifi, maps))
        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(listOf("setting", "app"), rows.map { it.domain })
        assertEquals(listOf(wifi, maps), FavoritesRepository.decode(rows.reversed()))
    }

    @Test
    fun aRowThatNoLongerDecodesIsSkipped() {
        val rows = FavoritesRepository.encode(listOf(maps)) + listOf(
            FavoriteEntity("hologram:1", "hologram", 1, """{"type":"hologram","label":"From a newer version"}"""),
            FavoriteEntity("app:broken", "app", 2, "not json"),
        )
        assertEquals(listOf(maps), FavoritesRepository.decode(rows))
    }

    @Test
    fun backupsWithFavoritesRoundTrip() {
        val bundle = BackupBundle(
            createdAt = 1L,
            settings = LauncherSettings(),
            screens = emptyList(),
            items = emptyList(),
            overrides = emptyList(),
            groups = emptyList(),
            members = emptyList(),
            favorites = FavoritesRepository.encode(listOf(maps, wifi)),
        )
        val decoded = LauncherJson.decodeFromString(BackupBundle.serializer(), LauncherJson.encodeToString(BackupBundle.serializer(), bundle))
        assertEquals(listOf(maps, wifi), FavoritesRepository.decode(decoded.favorites))
    }

    @Test
    fun aBackupFrom050HasNoFavorites() {
        val old = """{"createdAt":1,"settings":{},"screens":[],"items":[],"overrides":[],"groups":[],"members":[]}"""
        assertEquals(emptyList<FavoriteEntity>(), LauncherJson.decodeFromString(BackupBundle.serializer(), old).favorites)
    }
}
