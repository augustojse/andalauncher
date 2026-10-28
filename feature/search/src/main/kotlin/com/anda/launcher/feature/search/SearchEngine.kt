package com.anda.launcher.feature.search

import android.content.Context
import android.util.Log
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.LaunchStat
import com.anda.launcher.core.model.SearchSettings
import com.anda.launcher.core.ui.LauncherGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SearchResults(
    val query: String = "",
    val apps: List<SearchResult.App> = emptyList(),
    /** Calculator, conversions, actions, shortcuts, contacts, settings, then web; already ranked. */
    val others: List<SearchResult> = emptyList(),
) {
    val topApp: AppInfo? get() = apps.firstOrNull()?.app
}

/**
 * Runs every enabled [SearchProvider] in parallel and streams the combined results: one emission
 * once all fast providers are done (apps < 8 ms budget), then one more as each slow provider
 * finishes. Collectors cancel stale queries by restarting (latest-wins).
 */
class SearchEngine(
    private val providers: List<SearchProvider>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val onError: (SearchProvider, Throwable) -> Unit = { _, _ -> },
) {
    fun results(
        query: String,
        settings: SearchSettings,
        apps: List<AppInfo>,
        stats: Map<ComponentKey, LaunchStat>,
        includeHidden: Boolean,
        latinKeys: Map<String, String> = emptyMap(),
    ): Flow<SearchResults> = channelFlow {
        val q = query.trim()
        if (q.isEmpty()) {
            send(SearchResults())
            return@channelFlow
        }
        val request = SearchRequest(q, settings, apps, stats, includeHidden, latinKeys)
        val active = providers.filter { it.isEnabled(settings) }
        val found = HashMap<String, List<SearchResult>>()
        val lock = Mutex()
        val fastDone = CompletableDeferred<Unit>()

        for (provider in active.filter { it.slow }) {
            launch(dispatcher) {
                val results = run(provider, request)
                fastDone.await()
                lock.withLock { publish(q, active, found.apply { put(provider.id, results) }) }
            }
        }
        val fast = active.filterNot { it.slow }
            .map { provider -> async(dispatcher) { provider.id to run(provider, request) } }
            .awaitAll()
        lock.withLock { publish(q, active, found.apply { putAll(fast) }) }
        fastDone.complete(Unit)
    }

    private suspend fun run(provider: SearchProvider, request: SearchRequest): List<SearchResult> = try {
        provider.search(request)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(provider, e)
        emptyList()
    }

    private suspend fun ProducerScope<SearchResults>.publish(
        query: String,
        active: List<SearchProvider>,
        found: Map<String, List<SearchResult>>,
    ) {
        // Provider order breaks score ties, so the list doesn't reshuffle as slow providers land.
        val all = active.flatMap { found[it.id].orEmpty() }
        val apps = all.filterIsInstance<SearchResult.App>()
        val web = all.filterIsInstance<SearchResult.Web>()
        val ranked = all.filter { it !is SearchResult.App && it !is SearchResult.Web }.sortedByDescending { it.score }
        send(SearchResults(query, apps, ranked + web))
    }

    companion object {
        private const val TAG = "SearchEngine"

        /** The launcher's providers, in tie-break order. */
        fun create(context: Context, graph: LauncherGraph): SearchEngine = SearchEngine(
            providers = listOf(
                AppSearchProvider(),
                CalculatorProvider(),
                QueryActionProvider(),
                SettingsSearchProvider(),
                ShortcutSearchProvider(graph),
                ContactSearchProvider(context),
                WebSearchProvider(),
            ),
            onError = { provider, error -> Log.w(TAG, "Search provider ${provider.id} failed", error) },
        )
    }
}
