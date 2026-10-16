package com.anda.launcher.core.icons

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

data class IconPackInfo(val packageName: String, val label: String)

/** An installed ADW / Nova / Go-format icon pack. */
class IconPack private constructor(
    val packageName: String,
    private val resources: Resources,
    val filter: AppFilter,
) {
    /** Read and written by up to 4 render threads at once (IconRepository's render dispatcher). */
    private val idCache = ConcurrentHashMap<String, Int>()

    @SuppressLint("DiscouragedApi") // Icon packs are only addressable by resource name.
    private fun drawableId(name: String): Int = idCache.getOrPut(name) {
        resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: resources.getIdentifier(name, "mipmap", packageName)
    }

    fun drawable(name: String, density: Int): Drawable? {
        val id = drawableId(name)
        if (id == 0) return null
        return runCatching { resources.getDrawableForDensity(id, density, null) }.getOrNull()
    }

    /** The pack's icon for a component, honouring dynamic calendar icons. */
    fun iconFor(packageName: String, className: String, density: Int): Drawable? {
        val component = "$packageName/$className"
        filter.calendars[component]?.let { prefix ->
            val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            drawable(prefix + day, density)?.let { return it }
        }
        val name = filter.components[component] ?: return null
        return drawable(name, density)
    }

    fun isCalendar(packageName: String, className: String): Boolean =
        filter.calendars.containsKey("$packageName/$className")

    fun back(density: Int, seed: Int): Drawable? = filter.backs.pick(seed)?.let { drawable(it, density) }
    fun mask(density: Int): Drawable? = filter.masks.firstOrNull()?.let { drawable(it, density) }
    fun upon(density: Int): Drawable? = filter.upons.firstOrNull()?.let { drawable(it, density) }

    private fun List<String>.pick(seed: Int): String? = if (isEmpty()) null else this[Math.floorMod(seed, size)]

    companion object {
        /** Intent actions icon packs register for; covers ADW, Nova, Go, Apex, Action and Lawnchair. */
        private val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "com.actionlauncher.playstore.THEME",
            "app.lawnchair.icons.THEMED_ICON",
        )

        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            return PACK_ACTIONS.flatMap { action ->
                pm.queryIntentActivities(Intent(action), PackageManager.MATCH_ALL)
            }.map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .map { IconPackInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
                .sortedBy { it.label.lowercase() }
        }

        /** Loads and indexes a pack. Must run off the main thread. */
        @SuppressLint("DiscouragedApi")
        fun load(context: Context, packageName: String): IconPack? = runCatching {
            val res = context.packageManager.getResourcesForApplication(packageName)
            val xmlId = res.getIdentifier("appfilter", "xml", packageName)
            val parser: XmlPullParser = if (xmlId != 0) {
                res.getXml(xmlId)
            } else {
                val stream = context.createPackageContext(packageName, 0).assets.open("appfilter.xml")
                Xml.newPullParser().apply { setInput(stream, null) }
            }
            IconPack(packageName, res, AppFilterParser.parse(parser.tags()))
        }.getOrNull()

        private fun XmlPullParser.tags(): Sequence<XmlTag> = sequence {
            var event = eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val attrs = HashMap<String, String>(attributeCount)
                    for (i in 0 until attributeCount) attrs[getAttributeName(i)] = getAttributeValue(i)
                    yield(XmlTag(name, attrs))
                }
                event = next()
            }
        }
    }
}
