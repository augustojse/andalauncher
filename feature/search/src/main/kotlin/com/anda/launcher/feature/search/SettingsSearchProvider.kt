package com.anda.launcher.feature.search

import android.content.Intent
import android.provider.Settings
import com.anda.launcher.core.model.FuzzyMatcher
import com.anda.launcher.core.model.SearchSettings

/** Android settings pages reachable by public intents. */
internal class SettingsSearchProvider : SearchProvider {
    private data class Entry(val title: String, val action: String, val keywords: List<String>)

    override val id = "settings"

    override fun isEnabled(settings: SearchSettings) = settings.systemSettings

    override suspend fun search(request: SearchRequest): List<SearchResult.SystemSetting> {
        val query = request.query
        if (query.length < 2) return emptyList()
        return entries.mapNotNull { e ->
            val score = (listOf(e.title) + e.keywords).maxOf { FuzzyMatcher.score(query, FuzzyMatcher.normalize(it), false) }
            if (score < 500) null else SearchResult.SystemSetting(e.title, e.action, score - 300)
        }.sortedByDescending { it.score }.take(MAX_SETTINGS)
    }

    private companion object {
        const val MAX_SETTINGS = 3

        val entries = listOf(
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
    }
}
