package com.anda.launcher.feature.search

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.net.toUri
import com.anda.launcher.core.apps.ShortcutRepository
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.FuzzyMatcher
import com.anda.launcher.core.model.LaunchStat

sealed interface SearchResult {
    val score: Int

    data class App(val app: AppInfo, override val score: Int) : SearchResult
    data class Shortcut(
        val packageName: String,
        val shortcutId: String,
        val user: Long,
        val label: String,
        val appLabel: String,
        override val score: Int,
    ) : SearchResult
    data class Contact(val name: String, val lookupUri: Uri, val photoUri: Uri?, override val score: Int) : SearchResult
    data class Calculation(val expression: String, val result: String, override val score: Int = 2000) : SearchResult
    data class Conversion(val line: String, override val score: Int = 1900) : SearchResult
    data class SystemSetting(val title: String, val action: String, override val score: Int) : SearchResult
    data class Web(val query: String, override val score: Int = 0) : SearchResult
}

/** Ranks apps by match quality plus a usage bonus from our own launch stats. */
internal object AppSearch {
    fun search(query: String, apps: List<AppInfo>, stats: Map<com.anda.launcher.core.model.ComponentKey, LaunchStat>, fuzzy: Boolean, includeHidden: Boolean): List<SearchResult.App> =
        apps.asSequence()
            .filter { includeHidden || !it.isHidden }
            .mapNotNull { app ->
                val labelScore = FuzzyMatcher.score(query, app.sortKey, fuzzy)
                val score = maxOf(labelScore, if (labelScore == 0) FuzzyMatcher.score(query, app.key.packageName.substringAfterLast('.'), false) / 2 else 0)
                if (score <= 0) return@mapNotNull null
                val usage = (stats[app.key]?.count ?: 0).coerceAtMost(50) * 2
                SearchResult.App(app, score + usage)
            }
            .sortedByDescending { it.score }
            .take(MAX_APPS)
            .toList()

    const val MAX_APPS = 10
}

internal class ShortcutSearch(private val shortcuts: ShortcutRepository) {
    private data class Entry(val packageName: String, val id: String, val user: Long, val label: String, val normalized: String)

    @Volatile private var cache: List<Entry> = emptyList()
    @Volatile private var cacheVersion = -1

    fun search(query: String, version: Int, users: (android.os.UserHandle) -> Long, appLabels: Map<String, String>): List<SearchResult.Shortcut> {
        if (version != cacheVersion) {
            cache = shortcuts.allShortcuts().map {
                val label = (it.shortLabel ?: it.longLabel ?: "").toString()
                Entry(it.`package`, it.id, users(it.userHandle), label, FuzzyMatcher.normalize(label))
            }
            cacheVersion = version
        }
        return cache.mapNotNull { e ->
            val score = FuzzyMatcher.score(query, e.normalized, fuzzy = false)
            if (score < 500) null else SearchResult.Shortcut(e.packageName, e.id, e.user, e.label, appLabels[e.packageName].orEmpty(), score - 200)
        }.sortedByDescending { it.score }.take(5)
    }
}

internal object ContactSearch {
    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun search(context: Context, query: String): List<SearchResult.Contact> {
        if (query.length < 2 || !hasPermission(context)) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        val results = ArrayList<SearchResult.Contact>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && results.size < 5) {
                    val name = c.getString(2) ?: continue
                    val lookup = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                    results += SearchResult.Contact(
                        name = name,
                        lookupUri = lookup,
                        photoUri = c.getString(3)?.let(Uri::parse),
                        score = FuzzyMatcher.score(query, FuzzyMatcher.normalize(name), false).coerceAtLeast(300) - 100,
                    )
                }
            }
        }
        return results
    }
}

/** Android settings pages reachable by public intents. */
internal object SettingsSearch {
    private data class Entry(val title: String, val action: String, val keywords: List<String>)

    private val entries = listOf(
        Entry("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, listOf("wifi", "wireless", "internet", "network")),
        Entry("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, listOf("bluetooth", "pair", "headphones")),
        Entry("Mobile network", Settings.ACTION_DATA_ROAMING_SETTINGS, listOf("mobile data", "cellular", "sim", "roaming")),
        Entry("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, listOf("airplane", "flight")),
        Entry("Display", Settings.ACTION_DISPLAY_SETTINGS, listOf("display", "brightness", "screen", "font size")),
        Entry("Night Light", Settings.ACTION_NIGHT_DISPLAY_SETTINGS, listOf("night light", "blue light")),
        Entry("Sound & vibration", Settings.ACTION_SOUND_SETTINGS, listOf("sound", "volume", "ringtone", "vibration")),
        Entry("Battery", Intent.ACTION_POWER_USAGE_SUMMARY, listOf("battery", "power", "usage")),
        Entry("Battery saver", Settings.ACTION_BATTERY_SAVER_SETTINGS, listOf("battery saver", "power saving")),
        Entry("Apps", Settings.ACTION_APPLICATION_SETTINGS, listOf("apps", "applications", "permissions")),
        Entry("Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, listOf("default apps", "home app", "launcher", "browser")),
        Entry("Notifications", Settings.ACTION_ALL_APPS_NOTIFICATION_SETTINGS, listOf("notifications")),
        Entry("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, listOf("storage", "space", "memory")),
        Entry("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, listOf("location", "gps")),
        Entry("Security", Settings.ACTION_SECURITY_SETTINGS, listOf("security", "lock screen", "fingerprint", "password")),
        Entry("Privacy", Settings.ACTION_PRIVACY_SETTINGS, listOf("privacy")),
        Entry("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, listOf("accessibility", "talkback")),
        Entry("Date & time", Settings.ACTION_DATE_SETTINGS, listOf("date", "time", "timezone")),
        Entry("Languages", Settings.ACTION_LOCALE_SETTINGS, listOf("language", "locale")),
        Entry("Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS, listOf("keyboard", "input")),
        Entry("NFC", Settings.ACTION_NFC_SETTINGS, listOf("nfc", "tap to pay")),
        Entry("VPN", Settings.ACTION_VPN_SETTINGS, listOf("vpn")),
        Entry("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, listOf("developer", "usb debugging")),
        Entry("About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, listOf("about", "android version", "build number")),
        Entry("Wallpaper", Intent.ACTION_SET_WALLPAPER, listOf("wallpaper", "background")),
        Entry("Settings", Settings.ACTION_SETTINGS, listOf("settings", "preferences")),
    )

    fun search(query: String): List<SearchResult.SystemSetting> {
        if (query.length < 2) return emptyList()
        return entries.mapNotNull { e ->
            val score = (listOf(e.title) + e.keywords).maxOf { FuzzyMatcher.score(query, FuzzyMatcher.normalize(it), false) }
            if (score < 500) null else SearchResult.SystemSetting(e.title, e.action, score - 300)
        }.sortedByDescending { it.score }.take(3)
    }
}

/** Hand-offs to the user's assistant/search app. No network access from the launcher itself. */
object WebSearch {
    fun intent(query: String): Intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)

    fun voiceIntent(): Intent = Intent(Intent.ACTION_VOICE_COMMAND)

    fun lensIntent(): Intent = Intent(Intent.ACTION_VIEW, "googleapp://lens".toUri())
        .setPackage("com.google.android.googlequicksearchbox")

    fun assistantSearchIntent(): Intent = Intent(Intent.ACTION_ASSIST)
}
