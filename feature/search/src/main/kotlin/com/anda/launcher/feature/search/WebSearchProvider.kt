package com.anda.launcher.feature.search

import android.app.SearchManager
import android.content.Intent
import androidx.core.net.toUri
import com.anda.launcher.core.model.SearchSettings
import com.anda.launcher.core.model.WebEngine

/** "Search the web" through the user's search app, then one row per custom web engine. */
internal class WebSearchProvider : SearchProvider {
    override val id = "web"

    override fun isEnabled(settings: SearchSettings) = settings.webSearch || settings.webEngines.isNotEmpty()

    override suspend fun search(request: SearchRequest): List<SearchResult.Web> {
        val results = ArrayList<SearchResult.Web>()
        if (request.settings.webSearch) results += SearchResult.Web(request.query)
        request.settings.webEngines.forEach { results += SearchResult.Web(request.query, it) }
        return results
    }
}

/** Hand-offs to the user's assistant/search app or browser. No network access from the launcher itself. */
object WebSearch {
    fun intent(query: String): Intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)

    fun intent(query: String, engine: WebEngine): Intent =
        Intent(Intent.ACTION_VIEW, engine.urlFor(query).toUri()).addCategory(Intent.CATEGORY_BROWSABLE)

    fun voiceIntent(): Intent = Intent(Intent.ACTION_VOICE_COMMAND)

    fun lensIntent(): Intent = Intent(Intent.ACTION_VIEW, "googleapp://lens".toUri())
        .setPackage("com.google.android.googlequicksearchbox")

    fun assistantSearchIntent(): Intent = Intent(Intent.ACTION_ASSIST)
}
