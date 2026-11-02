package com.anda.launcher.feature.search

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances
import android.text.format.DateFormat
import com.anda.launcher.core.model.FuzzyMatcher
import com.anda.launcher.core.model.SearchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.Locale

/** Events whose title, place or notes match, from 30 days ago to a year ahead. */
internal class CalendarSearchProvider(private val context: Context) : SearchProvider {
    override val id = "calendar"
    override val slow = true

    override fun isEnabled(settings: SearchSettings) = settings.calendar

    override suspend fun search(request: SearchRequest): List<SearchResult.Event> {
        val query = request.query
        if (query.length < 2 || !hasPermission(context)) return emptyList()
        return withContext(Dispatchers.IO) { query(query, System.currentTimeMillis()) }
    }

    private fun query(query: String, now: Long): List<SearchResult.Event> {
        val uri = Instances.CONTENT_SEARCH_URI.buildUpon().also {
            ContentUris.appendId(it, now - PAST_MS)
            ContentUris.appendId(it, now + AHEAD_MS)
            it.appendPath(query)
        }.build()
        val projection = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.DISPLAY_COLOR)
        val rows = ArrayList<EventRow>()
        context.contentResolver.query(uri, projection, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && rows.size < MAX_ROWS) {
                rows += EventRow(
                    eventId = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Untitled event",
                    begin = c.getLong(2),
                    end = c.getLong(3),
                    allDay = c.getInt(4) != 0,
                    color = if (c.isNull(5)) null else c.getInt(5),
                )
            }
        }
        if (rows.isEmpty()) return emptyList()
        val locale = Locale.getDefault()
        val dates = EventDateLine(
            datePattern = DateFormat.getBestDateTimePattern(locale, "EEEMMMd"),
            datePatternWithYear = DateFormat.getBestDateTimePattern(locale, "EEEMMMdyyyy"),
            timePattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm"),
            locale = locale,
            zone = ZoneId.systemDefault(),
        )
        return rank(rows, query, now).map { (row, score) ->
            SearchResult.Event(
                eventId = row.eventId,
                title = row.title,
                begin = row.begin,
                end = row.end,
                allDay = row.allDay,
                color = row.color,
                dateLine = dates.format(row.begin, row.end, row.allDay, now),
                score = score,
            )
        }
    }

    /** One occurrence of an event, as the calendar provider lists it. */
    internal data class EventRow(val eventId: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean, val color: Int?)

    companion object {
        private const val MAX_EVENTS = 10
        /** A daily event yields ~400 occurrences in the window; stop reading well past that. */
        private const val MAX_ROWS = 1_000
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val PAST_MS = 30 * DAY_MS
        private const val AHEAD_MS = 365 * DAY_MS

        /**
         * One row per event: its next (or ongoing) occurrence, else its latest past one. Best
         * title match first; on a tie, upcoming events by start, then past ones, latest first.
         * An event found only by its place or notes still shows, below title matches.
         */
        internal fun rank(rows: List<EventRow>, query: String, now: Long): List<Pair<EventRow, Int>> =
            rows.groupBy { it.eventId }.values
                .map { occurrences -> occurrences.filter { it.end >= now }.minByOrNull { it.begin } ?: occurrences.maxBy { it.begin } }
                .map { it to FuzzyMatcher.score(query, FuzzyMatcher.normalize(it.title), false).coerceAtLeast(300) - 100 }
                .sortedWith(
                    compareByDescending<Pair<EventRow, Int>> { it.second }
                        .thenBy { if (it.first.end >= now) 0 else 1 }
                        .thenBy { if (it.first.end >= now) it.first.begin else -it.first.begin },
                )
                .take(MAX_EVENTS)

        fun hasPermission(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }
}
