package com.anda.launcher.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {
    private fun score(query: String, label: String) =
        FuzzyMatcher.score(query, FuzzyMatcher.normalize(label))

    @Test
    fun rankingOrder() {
        val exact = score("maps", "Maps")
        val prefix = score("map", "Maps")
        val word = score("maps", "Google Maps")
        val initials = score("gm", "Google Maps")
        val substring = score("oog", "Google")
        val fuzzy = score("ggl", "Google")
        assertTrue(exact > prefix)
        assertTrue(prefix > word)
        assertTrue(word > initials)
        assertTrue(initials > substring)
        assertTrue(substring > fuzzy)
        assertTrue(fuzzy > 0)
    }

    @Test
    fun diacriticsAreIgnored() {
        assertTrue(score("cafe", "Café") >= 900)
    }

    @Test
    fun noMatchScoresZero() {
        assertEquals(0, score("xyz", "Camera"))
        assertEquals(0, FuzzyMatcher.score("ca", FuzzyMatcher.normalize("Xylophone a c"), fuzzy = false))
    }
}
