package com.anda.launcher.feature.settings

/** One entry of CHANGELOG.md: `## X.Y.Z — YYYY-MM-DD` followed by `- ` bullets. */
internal data class Release(val version: String, val date: String, val notes: List<String>)

/**
 * Reads the CHANGELOG.md that the app build ships as `assets/changelog.md` (it also sets
 * versionName from the top entry). `ChangelogTest` checks the real file parses and stays ordered.
 */
internal object Changelog {
    const val ASSET = "changelog.md"

    private val heading = Regex("""^## (\d+\.\d+\.\d+) [—-] (\d{4}-\d{2}-\d{2})\s*$""")

    /** Releases newest first, as written. Text before the first version heading is ignored. */
    fun parse(text: String): List<Release> {
        val releases = mutableListOf<Release>()
        var version: String? = null
        var date = ""
        val notes = mutableListOf<String>()
        fun flush() {
            version?.let { releases += Release(it, date, notes.toList()) }
            notes.clear()
        }
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val match = heading.matchEntire(line)
            when {
                match != null -> {
                    flush()
                    version = match.groupValues[1]
                    date = match.groupValues[2]
                }
                version == null -> Unit
                line.startsWith("- ") -> notes += line.removePrefix("- ").trim()
                // A wrapped bullet continues the previous note.
                line.startsWith("  ") && line.isNotBlank() && notes.isNotEmpty() -> notes[notes.lastIndex] += " " + line.trim()
            }
        }
        flush()
        return releases
    }

    /** `1.2.3` → [1, 2, 3], for ordering. */
    fun parts(version: String): List<Int> = version.split('.').map { it.toInt() }
}
