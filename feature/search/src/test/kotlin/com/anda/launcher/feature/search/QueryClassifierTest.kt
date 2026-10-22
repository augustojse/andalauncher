package com.anda.launcher.feature.search

import com.anda.launcher.feature.search.QueryAction.AddEvent
import com.anda.launcher.feature.search.QueryAction.Call
import com.anda.launcher.feature.search.QueryAction.Email
import com.anda.launcher.feature.search.QueryAction.Message
import com.anda.launcher.feature.search.QueryAction.OpenUrl
import com.anda.launcher.feature.search.QueryAction.SaveContact
import com.anda.launcher.feature.search.QueryAction.SetAlarm
import com.anda.launcher.feature.search.QueryAction.SetTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QueryClassifierTest {
    private val today = LocalDate.of(2026, 10, 21)

    private fun classify(q: String) = QueryClassifier.classify(q, today)

    @Test
    fun phoneNumbers() {
        assertEquals(listOf(Call("+1 555 123 4567"), Message("+1 555 123 4567"), SaveContact("+1 555 123 4567", null)), classify("+1 555 123 4567"))
        assertTrue(Call("(555) 123-4567") in classify("(555) 123-4567"))
        assertTrue(Call("5551234") in classify("5551234"))
        assertTrue(classify("2048").none { it is Call })
        assertTrue(classify("3.14159").none { it is Call })
        assertTrue(classify("12*7").none { it is Call })
    }

    @Test
    fun emailAddresses() {
        assertEquals(listOf(Email("ana@example.com"), SaveContact(null, "ana@example.com")), classify("ana@example.com"))
        assertTrue(classify("ana@example").none { it is Email })
        assertTrue(classify("ana@example.com").none { it is OpenUrl })
    }

    @Test
    fun webAddresses() {
        assertEquals(listOf(OpenUrl("https://example.com")), classify("example.com"))
        assertEquals(listOf(OpenUrl("http://maps.example.org/a?b=c")), classify("http://maps.example.org/a?b=c"))
        assertTrue(classify("1.5").none { it is OpenUrl })
        assertTrue(classify("v2.0.1").none { it is OpenUrl })
        assertTrue(classify("google maps").none { it is OpenUrl })
    }

    @Test
    fun alarmTimes() {
        assertEquals(listOf(SetAlarm(7, 30)), classify("7:30"))
        assertEquals(listOf(SetAlarm(19, 30)), classify("7:30 pm"))
        assertEquals(listOf(SetAlarm(7, 0)), classify("7am"))
        assertEquals(listOf(SetAlarm(0, 15)), classify("12:15 am"))
        assertEquals(listOf(SetAlarm(12, 0)), classify("12pm"))
        assertEquals(listOf(SetAlarm(6, 0)), classify("alarm 6"))
        assertEquals(listOf(SetAlarm(19, 45)), classify("19:45"))
        assertTrue(classify("25:00").none { it is SetAlarm })
        assertTrue(classify("13pm").none { it is SetAlarm })
        assertTrue(classify("7").none { it is SetAlarm })
    }

    @Test
    fun timerDurations() {
        assertEquals(listOf(SetTimer(300)), classify("5 min"))
        assertEquals(listOf(SetTimer(5400)), classify("1h 30m"))
        assertEquals(listOf(SetTimer(5400)), classify("1h30m"))
        assertEquals(listOf(SetTimer(90)), classify("timer 90 seconds"))
        assertEquals(listOf(SetTimer(600)), classify("10 minutes timer"))
        assertTrue(classify("0 min").none { it is SetTimer })
        assertTrue(classify("48 hours").none { it is SetTimer })
        assertTrue(classify("5 km").none { it is SetTimer })
    }

    @Test
    fun calendarEvents() {
        assertEquals(listOf(AddEvent("dentist", LocalDate.of(2026, 10, 31))), classify("dentist oct 31"))
        assertEquals(listOf(AddEvent("Party", LocalDate.of(2026, 12, 5))), classify("Party 5 December"))
        assertEquals(listOf(AddEvent("", LocalDate.of(2027, 3, 1))), classify("2027-03-01"))
        assertEquals(listOf(AddEvent("call mom", LocalDate.of(2026, 10, 22))), classify("call mom tomorrow"))
        // Already past this year → next year.
        assertEquals(listOf(AddEvent("trip", LocalDate.of(2027, 1, 3))), classify("trip jan 3"))
        assertTrue(classify("today").isEmpty())
        assertTrue(classify("feb 30").isEmpty())
        assertTrue(classify("may").isEmpty())
    }

    @Test
    fun plainWordsHaveNoActions() {
        assertTrue(classify("spotify").isEmpty())
        assertTrue(classify("google maps").isEmpty())
        assertTrue(classify("").isEmpty())
    }

    @Test
    fun durationsFormat() {
        assertEquals("5 min", QueryClassifier.formatDuration(300))
        assertEquals("1 h 30 min", QueryClassifier.formatDuration(5400))
        assertEquals("1 min 30 s", QueryClassifier.formatDuration(90))
    }
}
