package com.anda.launcher.core.model

import java.text.Normalizer

/**
 * Scores how well a query matches a label. Higher is better; 0 means no match.
 * Ranking, best first: exact > prefix > word-prefix > initials ("gm" → Google Maps) >
 * substring > fuzzy subsequence > typo (Jaro-Winkler, "spotfiy" → Spotify).
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
        if (fuzzy && q.length >= 3) {
            // Short queries need a closer match: "cam" is already 0.82 similar to "car".
            val threshold = if (q.length >= 5) 0.8 else 0.85
            val similarity = maxOf(jaroWinkler(q, t), words.maxOfOrNull { jaroWinkler(q, it) } ?: 0.0)
            if (similarity >= threshold) return 10 + ((similarity - threshold) / (1 - threshold) * 35).toInt()
        }
        return 0
    }

    /** Standard Jaro-Winkler similarity, 0 (nothing alike) to 1 (equal). */
    internal fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val window = (maxOf(a.length, b.length) / 2 - 1).coerceAtLeast(0)
        val aMatched = BooleanArray(a.length)
        val bMatched = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            for (j in maxOf(0, i - window)..minOf(b.length - 1, i + window)) {
                if (bMatched[j] || a[i] != b[j]) continue
                aMatched[i] = true
                bMatched[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0
        var outOfOrder = 0
        var j = 0
        for (i in a.indices) {
            if (!aMatched[i]) continue
            while (!bMatched[j]) j++
            if (a[i] != b[j]) outOfOrder++
            j++
        }
        val m = matches.toDouble()
        val jaro = (m / a.length + m / b.length + (m - outOfOrder / 2.0) / m) / 3
        var prefix = 0
        while (prefix < minOf(4, a.length, b.length) && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1 - jaro)
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
