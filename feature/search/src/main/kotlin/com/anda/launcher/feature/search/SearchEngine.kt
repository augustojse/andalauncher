package com.anda.launcher.feature.search

import android.content.Context
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.LaunchStat
import com.anda.launcher.core.model.SearchSettings
import com.anda.launcher.core.ui.LauncherGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class SearchResults(
    val query: String = "",
    val apps: List<SearchResult.App> = emptyList(),
    /** Calculator, conversions, shortcuts, contacts, settings and web, already ranked. */
    val others: List<SearchResult> = emptyList(),
) {
    val topApp: AppInfo? get() = apps.firstOrNull()?.app
}

/**
 * Runs every enabled provider in parallel. Apps resolve in-memory (< 8 ms budget); contacts hit
 * the content provider on IO. Callers cancel stale queries by restarting (latest-wins).
 */
class SearchEngine(private val context: Context, private val graph: LauncherGraph) {
    private val shortcutSearch = ShortcutSearch(graph.shortcutRepository)

    suspend fun search(
        query: String,
        settings: SearchSettings,
        apps: List<AppInfo>,
        stats: Map<ComponentKey, LaunchStat>,
        includeHidden: Boolean,
    ): SearchResults {
        val q = query.trim()
        if (q.isEmpty()) return SearchResults()
        return coroutineScope {
            val appResults = async(Dispatchers.Default) {
                if (settings.apps) AppSearch.search(q, apps, stats, settings.fuzzyMatching, includeHidden) else emptyList()
            }
            val shortcutResults = async(Dispatchers.Default) {
                if (!settings.shortcuts || q.length < 2) {
                    emptyList()
                } else {
                    val labels = apps.associate { it.key.packageName to it.label }
                    shortcutSearch.search(q, graph.appsRepository.shortcutsVersion.value, graph.users::serialOf, labels)
                }
            }
            val contactResults = async(Dispatchers.IO) {
                if (settings.contacts) ContactSearch.search(context, q) else emptyList()
            }
            val others = ArrayList<SearchResult>()
            if (settings.calculator && ExpressionEvaluator.looksLikeMath(q)) {
                ExpressionEvaluator.evaluate(q)?.let { others += SearchResult.Calculation(q, ExpressionEvaluator.format(it)) }
            }
            if (settings.unitConverter) UnitConverter.convert(q).forEach { others += SearchResult.Conversion(it) }
            if (settings.systemSettings) others += withContext(Dispatchers.Default) { SettingsSearch.search(q) }
            others += shortcutResults.await()
            others += contactResults.await()
            others.sortByDescending { it.score }
            if (settings.webSearch) others += SearchResult.Web(q)
            SearchResults(q, appResults.await(), others)
        }
    }
}
