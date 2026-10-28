package com.anda.launcher.feature.search

import com.anda.launcher.core.model.FuzzyMatcher
import com.anda.launcher.core.model.SearchSettings

/**
 * Ranks apps by match quality plus a usage bonus from our own launch stats. The label counts
 * fully; the original label behind a rename, the Latin spelling of a name in another script and
 * the package name count for less.
 */
internal class AppSearchProvider : SearchProvider {
    override val id = "apps"

    override fun isEnabled(settings: SearchSettings) = settings.apps

    override suspend fun search(request: SearchRequest): List<SearchResult.App> {
        val fuzzy = request.settings.fuzzyMatching
        return request.apps.asSequence()
            .filter { request.includeHidden || !it.isHidden }
            .mapNotNull { app ->
                val labelScore = FuzzyMatcher.score(request.query, app.sortKey, fuzzy)
                val renamedScore = if (labelScore < 500 && app.originalLabel != app.label) {
                    FuzzyMatcher.score(request.query, app.originalSortKey, fuzzy) * 3 / 4
                } else {
                    0
                }
                // The Latin spelling of a name in another script ranks just below the name itself.
                val latinScore = if (labelScore < 500 && request.settings.transliterate) {
                    request.latinKeys[app.label]?.let { FuzzyMatcher.score(request.query, it, fuzzy) * 9 / 10 } ?: 0
                } else {
                    0
                }
                val packageScore = if (labelScore == 0) FuzzyMatcher.score(request.query, app.key.packageName.substringAfterLast('.'), false) / 2 else 0
                val score = maxOf(labelScore, renamedScore, latinScore, packageScore)
                if (score <= 0) return@mapNotNull null
                val usage = (request.stats[app.key]?.count ?: 0).coerceAtMost(50) * 2
                SearchResult.App(app, score + usage)
            }
            .sortedByDescending { it.score }
            .take(MAX_APPS)
            .toList()
    }

    companion object {
        const val MAX_APPS = 10
    }
}
