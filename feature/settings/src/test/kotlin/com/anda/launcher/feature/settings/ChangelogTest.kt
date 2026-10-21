package com.anda.launcher.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ChangelogTest {

    /** Unit tests run with the module directory as the working directory. */
    private val file = File("../../CHANGELOG.md")

    @Test
    fun everyVersionHeadingParses() {
        val headings = file.readLines().filter { it.startsWith("## ") }
        val releases = Changelog.parse(file.readText())
        assertTrue("CHANGELOG.md has no releases", releases.isNotEmpty())
        // A heading the parser skipped would vanish from About (and the build would reject it at the top).
        assertEquals("Malformed headings: ${headings.filter { h -> releases.none { h.startsWith("## ${it.version} ") } }}", headings.size, releases.size)
    }

    @Test
    fun versionsStrictlyDecreaseAndDatesNeverIncrease() {
        val releases = Changelog.parse(file.readText())
        releases.zipWithNext().forEach { (newer, older) ->
            val a = Changelog.parts(newer.version)
            val b = Changelog.parts(older.version)
            assertTrue("${newer.version} must be newer than ${older.version}: each update needs a new version", compareParts(a, b) > 0)
            assertTrue("${newer.version} is dated before ${older.version}", !LocalDate.parse(newer.date).isBefore(LocalDate.parse(older.date)))
        }
    }

    @Test
    fun everyReleaseSaysWhatChanged() {
        Changelog.parse(file.readText()).forEach { release ->
            assertTrue("${release.version} has no notes", release.notes.isNotEmpty())
            release.notes.forEach { assertTrue("Empty note in ${release.version}", it.isNotBlank()) }
        }
    }

    @Test
    fun minorAndPatchFitTheVersionCode() {
        Changelog.parse(file.readText()).forEach { release ->
            val (_, minor, patch) = Changelog.parts(release.version)
            assertTrue("${release.version}: minor and patch must stay below 100", minor < 100 && patch < 100)
        }
    }

    @Test
    fun parserReadsHeadingsBulletsAndWrappedLines() {
        val text = """
            # Changelog
            - not a release note
            ## 1.1.0 — 2026-01-02
            - New: one
              continued
            - Fixed: two
            ## 1.0.0 - 2026-01-01
            - First
        """.trimIndent()
        assertEquals(
            listOf(
                Release("1.1.0", "2026-01-02", listOf("New: one continued", "Fixed: two")),
                Release("1.0.0", "2026-01-01", listOf("First")),
            ),
            Changelog.parse(text),
        )
    }

    private fun compareParts(a: List<Int>, b: List<Int>): Int =
        a.zip(b).firstOrNull { (x, y) -> x != y }?.let { (x, y) -> x.compareTo(y) } ?: a.size.compareTo(b.size)
}
