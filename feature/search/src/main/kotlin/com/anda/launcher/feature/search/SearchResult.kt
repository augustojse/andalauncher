package com.anda.launcher.feature.search

import android.net.Uri
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.WebEngine

sealed interface SearchResult {
    val score: Int

    data class App(val app: AppInfo, override val score: Int) : SearchResult
    data class Shortcut(
        val packageName: String,
        val shortcutId: String,
        val user: Long,
        val label: String,
        val appLabel: String,
        override val score: Int,
    ) : SearchResult
    data class Contact(
        val name: String,
        val lookupUri: Uri,
        val photoUri: Uri?,
        override val score: Int,
        /** Primary number, for the Call and Message buttons; null without one or with them off. */
        val phone: String? = null,
    ) : SearchResult
    data class Calculation(val expression: String, val result: String, override val score: Int = 2000) : SearchResult
    data class Conversion(val line: String, override val score: Int = 1900) : SearchResult
    data class SystemSetting(val title: String, val action: String, override val score: Int) : SearchResult

    /** Something to do with the query itself: call it, open it, set an alarm for it… */
    data class Action(val action: QueryAction, override val score: Int = 1800) : SearchResult

    /** Hand the query to a search app ([engine] null) or to a user-defined web engine. */
    data class Web(val query: String, val engine: WebEngine? = null, override val score: Int = 0) : SearchResult
}
