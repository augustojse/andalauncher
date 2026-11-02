package com.anda.launcher.feature.search

import com.anda.launcher.feature.search.CalendarSearchProvider.EventRow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CalendarSearchTest {

    private val zone = ZoneId.of("Europe/Paris")
    private val now = at(2026, 11, 2, 10, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun utcMidnight(year: Int, month: Int, day: Int): Long =
        LocalDateTime.of(year, month, day, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun row(id: Long, title: String, begin: Long, end: Long = begin + 3_600_000, allDay: Boolean = false) =
        EventRow(id, title, begin, end, allDay, color = null)

    // ---- Ranking -----------------------------------------------------------------------------

    @Test
    fun aTitleMatchRanksAboveOneFoundInTheNotes() {
        val rows = listOf(
            row(1, "Lunch with Sam", at(2026, 11, 3, 12)),
            row(2, "Dentist", at(2026, 11, 5, 9)),
        )
        val ranked = CalendarSearchProvider.rank(rows, "dentist", now)
        assertEquals(listOf(2L, 1L), ranked.map { it.first.eventId })
    }

    @Test
    fun aRepeatingEventShowsOnceAtItsNextOccurrence() {
        val rows = listOf(
            row(1, "Yoga", at(2026, 10, 26, 18)),
            row(1, "Yoga", at(2026, 11, 2, 18)),
            row(1, "Yoga", at(2026, 11, 9, 18)),
        )
        val ranked = CalendarSearchProvider.rank(rows, "yoga", now)
        assertEquals(listOf(at(2026, 11, 2, 18)), ranked.map { it.first.begin })
    }

    @Test
    fun anEventUnderwayCountsAsNext() {
        val rows = listOf(row(1, "Standup", at(2026, 11, 2, 9, 45), end = at(2026, 11, 2, 10, 15)))
        assertEquals(at(2026, 11, 2, 9, 45), CalendarSearchProvider.rank(rows, "standup", now).single().first.begin)
    }

    @Test
    fun aFinishedEventShowsItsLatestOccurrence() {
        val rows = listOf(
            row(1, "Book club", at(2026, 10, 5, 19)),
            row(1, "Book club", at(2026, 10, 19, 19)),
        )
        assertEquals(at(2026, 10, 19, 19), CalendarSearchProvider.rank(rows, "book", now).single().first.begin)
    }

    @Test
    fun onATieUpcomingEventsComeFirstSoonestFirst() {
        val rows = listOf(
            // Same length, so the same match score.
            row(1, "Team review", at(2026, 10, 20, 14)),
            row(2, "Team outing", at(2026, 12, 1, 9)),
            row(3, "Team dinner", at(2026, 11, 4, 12)),
        )
        val ranked = CalendarSearchProvider.rank(rows, "team", now)
        assertEquals(listOf(3L, 2L, 1L), ranked.map { it.first.eventId })
    }

    @Test
    fun atMostTenEvents() {
        val rows = (1L..15L).map { row(it, "Meeting $it", at(2026, 11, 3, 9) + it * 3_600_000) }
        assertEquals(10, CalendarSearchProvider.rank(rows, "meeting", now).size)
    }

    // ---- Date line ---------------------------------------------------------------------------

    private val dates = EventDateLine("EEE, MMM d", "EEE, MMM d, yyyy", "H:mm", Locale.US, zone)

    private fun line(begin: Long, end: Long, allDay: Boolean = false) = dates.format(begin, end, allDay, now)

    @Test
    fun todayAndTomorrowAreNamed() {
        assertEquals("Today · 14:00 – 15:00", line(at(2026, 11, 2, 14), at(2026, 11, 2, 15)))
        assertEquals("Tomorrow · 9:30 – 10:00", line(at(2026, 11, 3, 9, 30), at(2026, 11, 3, 10)))
        assertEquals("Yesterday · 8:00 – 9:00", line(at(2026, 11, 1, 8), at(2026, 11, 1, 9)))
    }

    @Test
    fun otherDaysShowTheDateAndTheYearWhenItDiffers() {
        assertEquals("Fri, Nov 6 · 9:00 – 10:00", line(at(2026, 11, 6, 9), at(2026, 11, 6, 10)))
        assertEquals("Tue, Jan 5, 2027 · 9:00 – 10:00", line(at(2027, 1, 5, 9), at(2027, 1, 5, 10)))
    }

    @Test
    fun anEventWithoutDurationShowsOneTime() {
        assertEquals("Today · 18:00", line(at(2026, 11, 2, 18), at(2026, 11, 2, 18)))
    }

    @Test
    fun anEventPastMidnightShowsBothDays() {
        assertEquals("Today 22:00 – Tomorrow 2:00", line(at(2026, 11, 2, 22), at(2026, 11, 3, 2)))
    }

    @Test
    fun allDayEventsUseTheirUtcDates() {
        assertEquals("Tomorrow · All day", line(utcMidnight(2026, 11, 3), utcMidnight(2026, 11, 4), allDay = true))
        assertEquals("Fri, Nov 6 – Sun, Nov 8", line(utcMidnight(2026, 11, 6), utcMidnight(2026, 11, 9), allDay = true))
    }
}
