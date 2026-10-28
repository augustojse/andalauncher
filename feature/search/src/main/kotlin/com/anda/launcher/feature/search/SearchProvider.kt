package com.anda.launcher.feature.search

import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.LaunchStat
import com.anda.launcher.core.model.SearchSettings

/** Everything a provider may need to answer one query. [query] is already trimmed and non-empty. */
class SearchRequest(
    val query: String,
    val settings: SearchSettings,
    val apps: List<AppInfo>,
    val stats: Map<ComponentKey, LaunchStat>,
    val includeHidden: Boolean,
    /** Latin keys of labels in other scripts, as far as the background pass has built them. */
    val latinKeys: Map<String, String> = emptyMap(),
)

/**
 * One source of search results. Providers run in parallel; one that throws only loses its own
 * results. Each provider caps its own result count.
 */
interface SearchProvider {
    /** Stable name, used to keep each provider's latest results apart. */
    val id: String

    /**
     * Slow providers (content providers, system services) are published as they finish, after
     * the fast ones, so apps never wait for contacts.
     */
    val slow: Boolean get() = false

    fun isEnabled(settings: SearchSettings): Boolean

    suspend fun search(request: SearchRequest): List<SearchResult>
}
