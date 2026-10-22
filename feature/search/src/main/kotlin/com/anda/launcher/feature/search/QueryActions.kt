package com.anda.launcher.feature.search

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Something the query itself can be used for, independent of any installed content. */
sealed interface QueryAction {
    data class Call(val number: String) : QueryAction
    data class Message(val number: String) : QueryAction
    data class Email(val address: String) : QueryAction
    data class OpenUrl(val url: String) : QueryAction
    data class SetAlarm(val hour: Int, val minute: Int) : QueryAction
    data class SetTimer(val seconds: Int) : QueryAction
    data class AddEvent(val title: String, val date: LocalDate) : QueryAction
    /** New contact pre-filled with a phone number or an email address. */
    data class SaveContact(val phone: String?, val email: String?) : QueryAction
}

/**
 * Reads a search query as a phone number, email, web address, alarm time, timer duration or
 * calendar date. Pure Kotlin so it runs in unit tests; the intents live in [QueryActionIntents].
 * English month and unit names only for now (the UI strings are English too).
 */
object QueryClassifier {
    private val phone = Regex("""^\+?[0-9(][0-9 ()\-.]*[0-9]$""")
    private val email = Regex("""^[^\s@]+@[^\s@]+\.[^\s@.]{2,}$""")
    private val url = Regex("""^(https?://)?([a-z0-9-]+\.)+[a-z]{2,24}(:\d{1,5})?([/?#]\S*)?$""", RegexOption.IGNORE_CASE)
    private val time = Regex("""^(?:alarm\s+)?(\d{1,2})(?::(\d{2}))?\s*([ap])\.?m?\.?$|^(?:alarm\s+)?(\d{1,2}):(\d{2})$|^alarm\s+(\d{1,2})$""", RegexOption.IGNORE_CASE)
    private val durationPart = Regex("""(\d+)\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)""", RegexOption.IGNORE_CASE)
    private val duration = Regex("""^(?:timer\s+)?(?:\d+\s*(?:hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\s*)+(?:timer)?$""", RegexOption.IGNORE_CASE)
    private val isoDate = Regex("""\b(\d{4}-\d{2}-\d{2})\b""")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthAlternatives = "(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec)\\.?"
    private val dayMonth = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+$monthAlternatives(?=\s|$)""", RegexOption.IGNORE_CASE)
    private val monthDay = Regex("""\b$monthAlternatives\s+(\d{1,2})(?:st|nd|rd|th)?\b""", RegexOption.IGNORE_CASE)
    private val relativeDay = Regex("""\b(today|tomorrow)\b""", RegexOption.IGNORE_CASE)

    fun classify(query: String, today: LocalDate = LocalDate.now()): List<QueryAction> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val actions = ArrayList<QueryAction>()
        phoneNumber(q)?.let { actions += listOf(QueryAction.Call(it), QueryAction.Message(it), QueryAction.SaveContact(it, null)) }
        if (email.matches(q)) actions += listOf(QueryAction.Email(q), QueryAction.SaveContact(null, q))
        if (!email.matches(q)) webAddress(q)?.let { actions += QueryAction.OpenUrl(it) }
        alarmTime(q)?.let { actions += it }
        timerSeconds(q)?.let { actions += QueryAction.SetTimer(it) }
        event(q, today)?.let { actions += it }
        return actions
    }

    /** 5 to 15 digits, optionally with +, spaces, dashes, dots or brackets. */
    private fun phoneNumber(q: String): String? {
        if (!phone.matches(q) || isoDate.matches(q)) return null
        val digits = q.count(Char::isDigit)
        if (digits !in 5..15) return null
        // "3.14" and "1.5" are numbers, not phone numbers.
        if ('.' in q && q.count { it == '.' } == 1 && q.none { it == ' ' || it == '-' }) return null
        return q
    }

    private fun webAddress(q: String): String? {
        if (!url.matches(q)) return null
        return if (q.startsWith("http://", true) || q.startsWith("https://", true)) q else "https://$q"
    }

    private fun alarmTime(q: String): QueryAction.SetAlarm? {
        val m = time.matchEntire(q) ?: return null
        val g = m.groupValues
        val (hour, minute) = when {
            g[1].isNotEmpty() -> {
                val h = g[1].toInt()
                if (h !in 1..12) return null
                val pm = g[3].equals("p", ignoreCase = true)
                ((h % 12) + if (pm) 12 else 0) to (g[2].ifEmpty { "0" }.toInt())
            }
            g[4].isNotEmpty() -> g[4].toInt() to g[5].toInt()
            else -> g[6].toInt() to 0
        }
        if (hour !in 0..23 || minute !in 0..59) return null
        return QueryAction.SetAlarm(hour, minute)
    }

    private fun timerSeconds(q: String): Int? {
        if (!duration.matches(q)) return null
        val total = durationPart.findAll(q).sumOf { part ->
            val n = part.groupValues[1].toLongOrNull() ?: return null
            val unit = part.groupValues[2].lowercase()
            n * when {
                unit.startsWith("h") -> 3600
                unit.startsWith("m") -> 60
                else -> 1
            }
        }
        return total.takeIf { it in 1..MAX_TIMER_SECONDS }?.toInt()
    }

    private fun event(q: String, today: LocalDate): QueryAction.AddEvent? {
        val found = isoDate.find(q)?.let { m ->
            val date = try { LocalDate.parse(m.groupValues[1]) } catch (_: DateTimeParseException) { return null }
            m.range to date
        } ?: dayMonth.find(q)?.let { m ->
            monthDate(today, months.indexOf(m.groupValues[2].lowercase().take(3)) + 1, m.groupValues[1].toInt())?.let { m.range to it }
        } ?: monthDay.find(q)?.let { m ->
            monthDate(today, months.indexOf(m.groupValues[1].lowercase().take(3)) + 1, m.groupValues[2].toInt())?.let { m.range to it }
        } ?: relativeDay.find(q)?.let { m ->
            m.range to if (m.value.equals("today", ignoreCase = true)) today else today.plusDays(1)
        } ?: return null
        val (range, date) = found
        val title = q.removeRange(range).replace(Regex("\\s+"), " ").trim(' ', ',', '-')
        // A bare "today" is a question, not an event; only offer it with a title.
        if (title.isEmpty() && relativeDay.matches(q)) return null
        return QueryAction.AddEvent(title, date)
    }

    /** The next [month]/[day] on or after [today], or null if that day doesn't exist. */
    private fun monthDate(today: LocalDate, month: Int, day: Int): LocalDate? {
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
        if (thisYear != null && !thisYear.isBefore(today)) return thisYear
        return runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull()
    }

    /** "1 h 30 min", "45 s". */
    fun formatDuration(seconds: Int): String = listOfNotNull(
        (seconds / 3600).takeIf { it > 0 }?.let { "$it h" },
        (seconds % 3600 / 60).takeIf { it > 0 }?.let { "$it min" },
        (seconds % 60).takeIf { it > 0 }?.let { "$it s" },
    ).joinToString(" ")

    private const val MAX_TIMER_SECONDS = 24 * 3600L
}
