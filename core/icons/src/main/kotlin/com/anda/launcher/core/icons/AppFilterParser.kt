package com.anda.launcher.core.icons

/** One start tag from `appfilter.xml`, decoupled from XmlPullParser so parsing is JVM-testable. */
data class XmlTag(val name: String, val attributes: Map<String, String>)

/** Parsed ADW/Nova icon pack definition. */
data class AppFilter(
    /** "pkg/cls" → drawable name. */
    val components: Map<String, String>,
    /** "pkg/cls" → calendar drawable prefix (drawable = prefix + day of month). */
    val calendars: Map<String, String>,
    val backs: List<String>,
    val masks: List<String>,
    val upons: List<String>,
    val scale: Float,
) {
    val hasFallbackMask: Boolean get() = backs.isNotEmpty() || masks.isNotEmpty() || upons.isNotEmpty()
}

object AppFilterParser {
    private val componentRegex = Regex("""ComponentInfo\{([^/}]+)/([^}]+)\}""")

    fun parse(tags: Sequence<XmlTag>): AppFilter {
        val components = HashMap<String, String>()
        val calendars = HashMap<String, String>()
        val backs = ArrayList<String>()
        val masks = ArrayList<String>()
        val upons = ArrayList<String>()
        var scale = 1f
        for (tag in tags) {
            when (tag.name) {
                "item" -> {
                    val component = tag.attributes["component"]?.let(::normalizeComponent) ?: continue
                    val drawable = tag.attributes["drawable"]?.takeIf { it.isNotBlank() } ?: continue
                    components.putIfAbsent(component, drawable)
                }
                "calendar" -> {
                    val component = tag.attributes["component"]?.let(::normalizeComponent) ?: continue
                    val prefix = tag.attributes["prefix"] ?: continue
                    calendars[component] = prefix
                }
                "iconback" -> backs += tag.imageAttributes()
                "iconmask" -> masks += tag.imageAttributes()
                "iconupon" -> upons += tag.imageAttributes()
                "scale" -> scale = tag.attributes["factor"]?.toFloatOrNull()?.coerceIn(0.1f, 2f) ?: scale
            }
        }
        return AppFilter(components, calendars, backs, masks, upons, scale)
    }

    /** `ComponentInfo{pkg/.Cls}` → `pkg/pkg.Cls`. Returns null for malformed entries. */
    fun normalizeComponent(raw: String): String? {
        val match = componentRegex.find(raw) ?: return null
        val pkg = match.groupValues[1].trim()
        var cls = match.groupValues[2].trim()
        if (cls.startsWith('.')) cls = pkg + cls
        if (pkg.isEmpty() || cls.isEmpty()) return null
        return "$pkg/$cls"
    }

    private fun XmlTag.imageAttributes(): List<String> =
        attributes.entries.filter { it.key.startsWith("img") }.sortedBy { it.key }.map { it.value }.filter { it.isNotBlank() }
}
