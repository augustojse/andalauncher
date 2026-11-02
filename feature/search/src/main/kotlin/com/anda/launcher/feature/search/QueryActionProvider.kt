package com.anda.launcher.feature.search

import com.anda.launcher.core.model.QuickActionKind
import com.anda.launcher.core.model.SearchSettings
import java.time.LocalDate

/**
 * Turns a query that *is* a phone number, address, time or date into something to do with it.
 * Kinds switched off in settings are dropped; the rest rank in the user's order.
 */
internal class QueryActionProvider(private val today: () -> LocalDate = LocalDate::now) : SearchProvider {
    override val id = "actions"

    override fun isEnabled(settings: SearchSettings) = settings.quickActions

    override suspend fun search(request: SearchRequest): List<SearchResult.Action> {
        val order = QuickActionKind.ordered(request.settings.quickActionOrder)
        val disabled = request.settings.disabledQuickActions
        return QueryClassifier.classify(request.query, today())
            .filter { it.kind !in disabled }
            .map { SearchResult.Action(it, score = TOP_SCORE - order.indexOf(it.kind) * 10) }
    }

    private companion object {
        const val TOP_SCORE = 1800
    }
}

/** The user's "Search in <app>" and custom intent chips, for every query, after the web ones. */
internal class CustomActionProvider : SearchProvider {
    override val id = "custom"

    override fun isEnabled(settings: SearchSettings) = settings.customActions.isNotEmpty()

    override suspend fun search(request: SearchRequest): List<SearchResult.Action> =
        request.settings.customActions.map { SearchResult.Action(QueryAction.Custom(it, request.query), score = 0) }
}
