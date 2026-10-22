package com.anda.launcher.feature.search

import com.anda.launcher.core.model.SearchSettings
import java.time.LocalDate

/** Turns a query that *is* a phone number, address, time or date into something to do with it. */
internal class QueryActionProvider(private val today: () -> LocalDate = LocalDate::now) : SearchProvider {
    override val id = "actions"

    override fun isEnabled(settings: SearchSettings) = settings.quickActions

    override suspend fun search(request: SearchRequest): List<SearchResult.Action> =
        QueryClassifier.classify(request.query, today()).map { SearchResult.Action(it, score(it)) }

    private fun score(action: QueryAction): Int = when (action) {
        // A date can sit inside an app or contact name ("Today Weather"), so it ranks low.
        is QueryAction.AddEvent -> 400
        is QueryAction.SaveContact -> 1700
        else -> 1800
    }
}
