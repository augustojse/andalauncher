package com.anda.launcher.feature.search

import com.anda.launcher.core.model.FuzzyMatcher
import com.anda.launcher.core.model.SearchSettings
import com.anda.launcher.core.ui.LauncherGraph

/** App shortcuts ("New message", "Scan QR code"). Re-reads them only when the shortcut set changes. */
internal class ShortcutSearchProvider(private val graph: LauncherGraph) : SearchProvider {
    private data class Entry(val packageName: String, val id: String, val user: Long, val label: String, val normalized: String)

    override val id = "shortcuts"
    override val slow = true

    @Volatile private var cache: List<Entry> = emptyList()
    @Volatile private var cacheVersion = -1

    override fun isEnabled(settings: SearchSettings) = settings.shortcuts

    override suspend fun search(request: SearchRequest): List<SearchResult.Shortcut> {
        if (request.query.length < 2) return emptyList()
        val version = graph.appsRepository.shortcutsVersion.value
        if (version != cacheVersion) {
            cache = graph.shortcutRepository.allShortcuts().map {
                val label = (it.shortLabel ?: it.longLabel ?: "").toString()
                Entry(it.`package`, it.id, graph.users.serialOf(it.userHandle), label, FuzzyMatcher.normalize(label))
            }
            cacheVersion = version
        }
        val appLabels = request.apps.associate { it.key.packageName to it.label }
        return cache.mapNotNull { e ->
            val score = FuzzyMatcher.score(request.query, e.normalized, fuzzy = false)
            if (score < 500) null else SearchResult.Shortcut(e.packageName, e.id, e.user, e.label, appLabels[e.packageName].orEmpty(), score - 200)
        }.sortedByDescending { it.score }.take(MAX_SHORTCUTS)
    }

    private companion object {
        const val MAX_SHORTCUTS = 10
    }
}
