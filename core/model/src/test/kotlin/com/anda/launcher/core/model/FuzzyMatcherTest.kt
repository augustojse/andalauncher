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

    @Test
    fun typosStillMatchButRankLast() {
        val typo = score("spotfiy", "Spotify")
        assertTrue(typo > 0)
        assertTrue(typo < score("ggl", "Google"))
        assertTrue(score("yuotube", "YouTube") > 0)
        assertTrue(score("gmial", "Gmail") > 0)
        assertTrue(score("mpas", "Google Maps") > 0)
        assertTrue(score("spotfiy", "Spotify") > score("spotfiy", "Spot Finder"))
    }

    @Test
    fun typoBandNeedsFuzzyAndThreeLetters() {
        assertEquals(0, FuzzyMatcher.score("spotfiy", FuzzyMatcher.normalize("Spotify"), fuzzy = false))
        assertEquals(0, score("sx", "Sz"))
        assertEquals(0, score("cam", "Car"))
        assertEquals(0, score("phone", "Clock"))
    }

    @Test
    fun jaroWinklerKnownValues() {
        assertEquals(1.0, FuzzyMatcher.jaroWinkler("abc", "abc"), 1e-9)
        assertEquals(0.0, FuzzyMatcher.jaroWinkler("abc", "xyz"), 1e-9)
        assertEquals(0.961, FuzzyMatcher.jaroWinkler("martha", "marhta"), 1e-3)
        assertEquals(0.840, FuzzyMatcher.jaroWinkler("dwayne", "duane"), 1e-3)
    }
}
