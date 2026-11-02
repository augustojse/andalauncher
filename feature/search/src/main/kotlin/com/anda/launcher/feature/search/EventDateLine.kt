package com.anda.launcher.feature.search

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The date line under an event: "Today · 14:00 – 15:00", "Tomorrow · All day",
 * "Mon, Nov 3 22:00 – Tue, Nov 4 2:00". The patterns come from the locale (and the 12/24-hour
 * setting) on the device; tests pass fixed ones.
 */
internal class EventDateLine(
    datePattern: String,
    datePatternWithYear: String,
    timePattern: String,
    locale: Locale,
    private val zone: ZoneId,
) {
    private val date = DateTimeFormatter.ofPattern(datePattern, locale)
    private val dateWithYear = DateTimeFormatter.ofPattern(datePatternWithYear, locale)
    private val time = DateTimeFormatter.ofPattern(timePattern, locale)

    fun format(begin: Long, end: Long, allDay: Boolean, now: Long): String {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (allDay) {
            // All-day events are stored as UTC midnights, and the end is exclusive.
            val first = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
            val last = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
            return if (last <= first) "${day(first, today)} · All day" else "${day(first, today)} – ${day(last, today)}"
        }
        val start = Instant.ofEpochMilli(begin).atZone(zone)
        val finish = Instant.ofEpochMilli(end).atZone(zone)
        return when {
            end <= begin -> "${day(start.toLocalDate(), today)} · ${time.format(start)}"
            start.toLocalDate() == finish.toLocalDate() ->
                "${day(start.toLocalDate(), today)} · ${time.format(start)} – ${time.format(finish)}"
            else -> "${day(start.toLocalDate(), today)} ${time.format(start)} – ${day(finish.toLocalDate(), today)} ${time.format(finish)}"
        }
    }

    private fun day(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> (if (day.year == today.year) date else dateWithYear).format(day)
    }
}
