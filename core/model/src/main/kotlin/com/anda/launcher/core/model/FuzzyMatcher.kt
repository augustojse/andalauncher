package com.anda.launcher.core.model

import java.text.Normalizer

/**
 * Scores how well a query matches a label. Higher is better; 0 means no match.
 * Ranking, best first: exact > prefix > word-prefix > initials ("gm" → Google Maps) >
 * substring > fuzzy subsequence.
 */
object FuzzyMatcher {
    private val diacritics = "\\p{Mn}+".toRegex()

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(diacritics, "").lowercase()

    fun score(query: String, normalizedTarget: String, fuzzy: Boolean = true): Int {
        if (query.isEmpty()) return 0
        val q = normalize(query).trim()
        if (q.isEmpty()) return 0
        val t = normalizedTarget
        if (t == q) return 1000
        if (t.startsWith(q)) return 900 - (t.length - q.length).coerceAtMost(100)
        val words = t.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 700
        if (q.length >= 2 && matchesInitials(q, words)) return 650
        val index = t.indexOf(q)
        if (index >= 0) return 500 - index.coerceAtMost(100)
        if (fuzzy && q.length >= 2) {
            val gaps = subsequenceGaps(q, t)
            if (gaps >= 0) return (300 - gaps * 10).coerceAtLeast(50)
        }
        return 0
    }

    private fun matchesInitials(q: String, words: List<String>): Boolean {
        if (words.size < q.length) return false
        var wi = 0
        for (c in q) {
            while (wi < words.size && words[wi][0] != c) wi++
            if (wi == words.size) return false
            wi++
        }
        return true
    }

    /** Number of skipped characters if [q] is a subsequence of [t], else -1. */
    private fun subsequenceGaps(q: String, t: String): Int {
        var ti = 0
        var gaps = 0
        var started = false
        for (c in q) {
            while (ti < t.length && t[ti] != c) {
                if (started) gaps++
                ti++
            }
            if (ti == t.length) return -1
            started = true
            ti++
        }
        return gaps
    }
}
