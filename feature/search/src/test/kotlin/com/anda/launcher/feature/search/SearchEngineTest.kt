package com.anda.launcher.feature.search

import com.anda.launcher.core.model.AppCategory
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.FuzzyMatcher
import com.anda.launcher.core.model.SearchSettings
import com.anda.launcher.core.model.WebEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    private fun app(label: String, original: String = label) = AppInfo(
        key = ComponentKey("com.example.${label.lowercase().replace(" ", "")}", "Main"),
        label = label,
        originalLabel = original,
        installTime = 0,
        lastUpdateTime = 0,
        category = AppCategory.Other,
        isWorkProfile = false,
        sortKey = FuzzyMatcher.normalize(label),
        originalSortKey = FuzzyMatcher.normalize(original),
    )

    private class Fake(
        override val id: String,
        override val slow: Boolean = false,
        private val block: suspend (SearchRequest) -> List<SearchResult>,
    ) : SearchProvider {
        override fun isEnabled(settings: SearchSettings) = true
        override suspend fun search(request: SearchRequest) = block(request)
    }

    private val settings = SearchSettings()
    private val apps = listOf(app("Spotify"), app("Maps"), app("Camera"))

    private fun SearchEngine.run(query: String) = results(query, settings, apps, emptyMap(), includeHidden = false)

    @Test
    fun emptyQueryEmitsNothingFound() = runBlocking {
        assertEquals(SearchResults(), SearchEngine(listOf(AppSearchProvider())).run("  ").first())
    }

    @Test
    fun fastProvidersArriveTogetherThenSlowOnesStream() = runBlocking {
        val engine = SearchEngine(
            listOf(
                AppSearchProvider(),
                Fake("contacts", slow = true) { listOf(SearchResult.SystemSetting("Contact", "x", 100)) },
                Fake("calc") { listOf(SearchResult.Calculation("1+1", "2")) },
            ),
        )
        val emissions = engine.run("spot").take(2).toList()
        // First: every fast provider, never apps without the calculator or the other way round.
        assertEquals(listOf("Spotify"), emissions[0].apps.map { it.app.label })
        assertEquals(listOf(SearchResult.Calculation("1+1", "2")), emissions[0].others)
        // Then the slow one joins, ranked by score.
        assertEquals(2, emissions[1].others.size)
        assertTrue(emissions[1].others.first() is SearchResult.Calculation)
    }

    @Test
    fun aFailingProviderOnlyLosesItsOwnResults() = runBlocking {
        val failures = mutableListOf<String>()
        val engine = SearchEngine(
            listOf(AppSearchProvider(), Fake("broken") { error("boom") }),
            onError = { provider, _ -> failures += provider.id },
        )
        val results = engine.run("maps").first()
        assertEquals(listOf("Maps"), results.apps.map { it.app.label })
        assertEquals(listOf("broken"), failures)
    }

    @Test
    fun webRowsStayLastWhateverTheirScore() = runBlocking {
        val engine = SearchEngine(
            listOf(
                WebSearchProvider(),
                Fake("low") { listOf(SearchResult.SystemSetting("Low", "x", -50)) },
            ),
        )
        val kagi = WebEngine("Kagi", "https://kagi.com/search?q=%s")
        val results = engine.results("cats", settings.copy(webEngines = listOf(kagi)), apps, emptyMap(), false).first()
        assertEquals(
            listOf(SearchResult.SystemSetting("Low", "x", -50), SearchResult.Web("cats"), SearchResult.Web("cats", kagi)),
            results.others,
        )
    }

    @Test
    fun renamedAppsAreFoundByTheirOriginalName() = runBlocking {
        val renamed = listOf(app("Music", original = "Spotify"), app("Maps"))
        val results = SearchEngine(listOf(AppSearchProvider()))
            .results("spotify", settings, renamed, emptyMap(), false).first()
        assertEquals(listOf("Music"), results.apps.map { it.app.label })
    }

    @Test
    fun originalNamesMatchWithoutAccents() = runBlocking {
        val renamed = listOf(app("Tickets", original = "Pokémon GO"), app("Maps"))
        val results = SearchEngine(listOf(AppSearchProvider()))
            .results("pokemon", settings, renamed, emptyMap(), false).first()
        assertEquals(listOf("Tickets"), results.apps.map { it.app.label })
    }

    @Test
    fun typosFindApps() = runBlocking {
        val results = SearchEngine(listOf(AppSearchProvider())).run("spotfiy").first()
        assertEquals(listOf("Spotify"), results.apps.map { it.app.label })
    }
}
