package com.anda.launcher.core.apps

import android.icu.text.Transliterator
import com.anda.launcher.core.model.FuzzyMatcher

/**
 * Latin search keys for text in other scripts, so "telegram" finds "Телеграм" (research 003, B27).
 * Latin text gets no key: it is already searchable as it is, and skipping it keeps the load cheap.
 */
object LatinKeys {
    // Built on first use, off the main thread: loading the ICU rules is the expensive part.
    private val transliterator by lazy { Transliterator.getInstance("Any-Latin; Latin-ASCII") }

    /** Normalized Latin form of [text], or null when [text] has no letters outside Latin script. */
    fun of(text: String): String? {
        if (!hasNonLatinLetters(text)) return null
        val latin = synchronized(transliterator) { transliterator.transliterate(text) }
        return FuzzyMatcher.normalize(latin).takeIf { it.isNotBlank() }
    }

    fun hasNonLatinLetters(text: String): Boolean = text.codePoints().anyMatch { cp ->
        Character.isLetter(cp) && Character.UnicodeScript.of(cp) != Character.UnicodeScript.LATIN
    }
}
