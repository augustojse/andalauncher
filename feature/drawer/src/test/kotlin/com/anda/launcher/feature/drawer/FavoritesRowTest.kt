package com.anda.launcher.feature.drawer

import com.anda.launcher.core.model.AppCategory
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.Favorite
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesRowTest {
    private fun app(name: String, hidden: Boolean = false) = AppInfo(
        key = ComponentKey("com.example.$name", ".Main"),
        label = name,
        originalLabel = name,
        installTime = 0,
        lastUpdateTime = 0,
        category = AppCategory.Other,
        isWorkProfile = false,
        isHidden = hidden,
    )

    private val maps = app("maps")
    private val secret = app("secret", hidden = true)
    private val favorites = listOf(
        Favorite.App(maps.key, "Maps"),
        Favorite.App(ComponentKey("com.example.uninstalled", ".Main"), "Gone"),
        Favorite.App(secret.key, "Secret"),
        Favorite.Shortcut("com.example.maps", "home", 0, "Home", "Maps"),
        Favorite.Shortcut("com.example.uninstalled", "x", 0, "X", "Gone"),
        Favorite.Contact("Anna", "content://com.android.contacts/contacts/lookup/abc/1"),
    )

    private fun shown(hiddenUnlocked: Boolean = false) =
        shownFavorites(favorites, listOf(maps, secret), hiddenUnlocked).map { it.favorite.label }

    @Test
    fun pinsOfMissingAppsAreLeftOut() {
        assertEquals(listOf("Maps", "Home", "Anna"), shown())
    }

    @Test
    fun aHiddenAppShowsOnlyWhileHiddenAppsAreUnlocked() {
        assertEquals(listOf("Maps", "Secret", "Home", "Anna"), shown(hiddenUnlocked = true))
    }

    @Test
    fun appPinsUseTheInstalledApp() {
        assertEquals(maps, shownFavorites(favorites, listOf(maps), false).first().app)
    }
}
