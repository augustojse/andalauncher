package com.anda.launcher.feature.search

import com.anda.launcher.core.model.SearchSettings

/** Inline answers: "12*7", "15% of 80", "5 km in miles". */
internal class CalculatorProvider : SearchProvider {
    override val id = "calculator"

    override fun isEnabled(settings: SearchSettings) = settings.calculator || settings.unitConverter

    override suspend fun search(request: SearchRequest): List<SearchResult> {
        val q = request.query
        val results = ArrayList<SearchResult>()
        if (request.settings.calculator && ExpressionEvaluator.looksLikeMath(q)) {
            ExpressionEvaluator.evaluate(q)?.let { results += SearchResult.Calculation(q, ExpressionEvaluator.format(it)) }
        }
        if (request.settings.unitConverter) UnitConverter.convert(q).forEach { results += SearchResult.Conversion(it) }
        return results
    }
}
