package com.anda.launcher.core.apps

import android.content.pm.ApplicationInfo
import com.anda.launcher.core.model.AppCategory

/**
 * Smart-Launcher-style automatic categories. Uses the manifest `appCategory` when the developer
 * declared one, then package-name heuristics. Users can override per app.
 */
object AppCategorizer {

    private val packageRules: List<Pair<List<String>, AppCategory>> = listOf(
        listOf("dialer", "contacts", "messag", "mms", "sms", "whatsapp", "telegram", "signal", "gm", "mail", "email", "outlook", "skype", "teams", "slack", "discord", ".talk", "meet", "zoom", "viber", "line.android") to AppCategory.Communication,
        listOf("facebook", "instagram", "twitter", "x.android", "snapchat", "reddit", "tiktok", "musically", "linkedin", "pinterest", "tumblr", "mastodon", "threads", "bluesky") to AppCategory.Social,
        listOf("youtube", "spotify", "music", "netflix", "video", "player", "podcast", "twitch", "primevideo", "disney", "hulu", "audible", "soundcloud", "deezer", "tidal", "plex", "vlc") to AppCategory.Media,
        listOf("camera", "photo", "gallery", "lightroom", "snapseed", "vsco", "picsart") to AppCategory.Photography,
        listOf("maps", "waze", "uber", "lyft", "transit", "citymapper", "navigation", "gps", "here.app", "osmand") to AppCategory.Navigation,
        listOf("shop", "amazon", "ebay", "aliexpress", "etsy", "wish", "store", "walmart", "target", "temu", "shein") to AppCategory.Shopping,
        listOf("news", "magazines", "feedly", "flipboard", "bbc", "cnn", "nytimes") to AppCategory.News,
        listOf("fit", "health", "strava", "workout", "sleep", "calm", "headspace", "meditat", "garmin") to AppCategory.Health,
        listOf("bank", "pay", "wallet", "finance", "money", "paypal", "revolut", "wise", "venmo", "cashapp", "crypto", "coinbase", "binance", "trading") to AppCategory.Finance,
        listOf("docs", "sheets", "slides", "drive", "office", "word", "excel", "powerpoint", "notion", "evernote", "keep", "calendar", "todo", "tasks", "notes", "dropbox", "onenote", "trello", "obsidian") to AppCategory.Productivity,
        listOf("settings", "calculator", "clock", "files", "documentsui", "filemanager", "vpn", "launcher", "keyboard", "inputmethod", "flashlight", "weather", "authenticator", "backup", "recorder", "scanner") to AppCategory.Tools,
    )

    fun categorize(packageName: String, manifestCategory: Int, isGame: Boolean = false): AppCategory {
        if (isGame) return AppCategory.Games
        when (manifestCategory) {
            ApplicationInfo.CATEGORY_GAME -> return AppCategory.Games
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.Media
            ApplicationInfo.CATEGORY_IMAGE -> return AppCategory.Photography
            ApplicationInfo.CATEGORY_SOCIAL -> return AppCategory.Social
            ApplicationInfo.CATEGORY_NEWS -> return AppCategory.News
            ApplicationInfo.CATEGORY_MAPS -> return AppCategory.Navigation
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategory.Productivity
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> return AppCategory.Tools
        }
        val pkg = packageName.lowercase()
        for ((needles, category) in packageRules) {
            if (needles.any { pkg.contains(it) }) return category
        }
        if (pkg.contains("game") || pkg.startsWith("com.supercell") || pkg.startsWith("com.king.")) return AppCategory.Games
        return AppCategory.Other
    }
}
