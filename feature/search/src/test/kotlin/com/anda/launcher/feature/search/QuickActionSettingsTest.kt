package com.anda.launcher.feature.search

import com.anda.launcher.core.model.CustomAction
import com.anda.launcher.core.model.QuickActionKind
import com.anda.launcher.core.model.SearchSettings
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuickActionSettingsTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val number = "+1 555 123 4567"

    private fun actions(settings: SearchSettings, query: String = number): List<QueryAction> = runBlocking {
        val request = SearchRequest(query, settings, apps = emptyList(), stats = emptyMap(), includeHidden = false)
        QueryActionProvider { today }.search(request).sortedByDescending { it.score }.map { it.action }
    }

    @Test
    fun theDefaultOrderIsUnchanged() {
        assertEquals(
            listOf(QueryAction.Call(number), QueryAction.Message(number), QueryAction.SaveContact(number, null)),
            actions(SearchSettings()),
        )
    }

    @Test
    fun switchedOffKindsAreDropped() {
        val settings = SearchSettings(disabledQuickActions = listOf(QuickActionKind.Call, QuickActionKind.SaveContact))
        assertEquals(listOf(QueryAction.Message(number)), actions(settings))
    }

    @Test
    fun theUsersOrderWins() {
        val settings = SearchSettings(quickActionOrder = listOf(QuickActionKind.SaveContact, QuickActionKind.Message))
        assertEquals(
            listOf(QueryAction.SaveContact(number, null), QueryAction.Message(number), QueryAction.Call(number)),
            actions(settings),
        )
    }

    @Test
    fun yourOwnActionsComeAfterTheWebSearch() = runBlocking {
        val spotify = CustomAction("Spotify", packageName = "com.spotify.music")
        val settings = SearchSettings(customActions = listOf(spotify))
        val engine = SearchEngine(listOf(QueryActionProvider { today }, WebSearchProvider(), CustomActionProvider()))
        val chips = engine.results(number, settings, emptyList(), emptyMap(), includeHidden = false).toList().last().chips()
        assertTrue(chips.first() is SearchResult.Action)
        assertTrue(chips[chips.size - 2] is SearchResult.Web)
        assertEquals(QueryAction.Custom(spotify, number), (chips.last() as SearchResult.Action).action)
    }
}
