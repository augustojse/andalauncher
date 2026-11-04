package com.anda.launcher.feature.settings

internal enum class Screen(val title: String) {
    Main("Anda Launcher settings"),
    Home("Home screen"),
    Dock("Dock"),
    Drawer("App drawer"),
    Icons("Icons"),
    Folders("Folders"),
    Gestures("Gestures"),
    Search("Search"),
    Badges("Notification dots"),
    Theme("Look & feel"),
    Glance("At a glance"),
    Hidden("Hidden apps"),
    Permissions("Permissions"),
    Backup("Backup & restore"),
    Dev("Dev"),
    About("About"),
    IconEditor("Icon editor"),
    QuickActions("Quick actions"),
    ShortcutApps("Hidden shortcut apps"),
    ;

    /** Where Back goes; Main when null. */
    val parent: Screen?
        get() = when (this) {
            IconEditor -> Icons
            QuickActions -> Search
            ShortcutApps -> Search
            else -> null
        }
}

/** One searchable row. [title] must match the row's on-screen title exactly (it's the highlight key). */
internal data class IndexEntry(val title: String, val screen: Screen, val keywords: String = "")

/**
 * Static index behind the settings search, so tucked-away (Advanced) options are one query away.
 * `SettingsIndexTest` checks every title still exists in SettingsScreens.kt.
 */
internal object SettingsIndex {
    val entries: List<IndexEntry> = listOf(
        // Home
        IndexEntry("Columns", Screen.Home, "grid width size icons per row"),
        IndexEntry("Rows", Screen.Home, "grid height size"),
        IndexEntry("Search bar position", Screen.Home, "top dock google"),
        IndexEntry("Search bar style", Screen.Home, "pill rounded square shape"),
        IndexEntry("Scroll effect", Screen.Home, "page transition animation cube depth"),
        IndexEntry("Page indicator", Screen.Home, "dots line"),
        IndexEntry("Subgrid positioning", Screen.Home, "half cell between free placement"),
        IndexEntry("Horizontal padding", Screen.Home, "margin spacing sides"),
        IndexEntry("Vertical padding", Screen.Home, "margin spacing top bottom"),
        IndexEntry("Widget padding", Screen.Home, "margin spacing"),
        IndexEntry("Infinite scroll", Screen.Home, "loop wrap pages"),
        IndexEntry("Wallpaper scrolling", Screen.Home, "parallax"),
        IndexEntry("Two pages side by side", Screen.Home, "fold tablet dual panel large screen"),
        IndexEntry("Show status bar", Screen.Home, "hide clock notification bar fullscreen"),
        IndexEntry("Wallpaper dimming", Screen.Home, "darken legibility contrast"),
        IndexEntry("Lock layout", Screen.Home, "prevent move remove freeze"),
        IndexEntry("Add new apps to home screen", Screen.Home, "install auto"),
        // Dock
        IndexEntry("Show dock", Screen.Dock, "hide hotseat"),
        IndexEntry("Icons per page", Screen.Dock, "dock columns count"),
        IndexEntry("Dock pages", Screen.Dock, "scrollable multi page"),
        IndexEntry("Background", Screen.Dock, "dock background"),
        IndexEntry("Background shape", Screen.Dock, "rectangle rounded pill"),
        // Drawer
        IndexEntry("Style", Screen.Drawer, "drawer layout grid list pages paged horizontal vertical"),
        IndexEntry("Sort", Screen.Drawer, "alphabetical install date most used order"),
        IndexEntry("Search bar at the bottom", Screen.Drawer, "one hand reach"),
        IndexEntry("Custom tabs", Screen.Drawer, "groups"),
        IndexEntry("Automatic category tabs", Screen.Drawer, "categories social games smart"),
        IndexEntry("Separate work apps", Screen.Drawer, "work profile tab"),
        IndexEntry("Drawer folders", Screen.Drawer, "groups"),
        IndexEntry("Rows per page", Screen.Drawer, "paged"),
        IndexEntry("Fast scroller", Screen.Drawer, "alphabet letters index"),
        IndexEntry("Favorites row", Screen.Drawer, "pin pinned favourite star"),
        IndexEntry("Suggested apps row", Screen.Drawer, "predictions recent frequent"),
        IndexEntry("Open keyboard automatically", Screen.Drawer, "focus search ime"),
        IndexEntry("Blur wallpaper", Screen.Drawer, "drawer blur"),
        IndexEntry("Require fingerprint for hidden & locked apps", Screen.Drawer, "biometric lock privacy auth"),
        IndexEntry("Show hidden apps in search", Screen.Drawer, "privacy"),
        // Icons
        IndexEntry("Icon editor", Screen.Icons, "edit customize color colour material you background foreground gradient outline shadow rotate hue saturation"),
        IndexEntry("Icon pack", Screen.Icons, "adw nova theme"),
        IndexEntry("Shape", Screen.Icons, "icon shape circle squircle teardrop"),
        IndexEntry("Themed icons", Screen.Icons, "monochrome material you tint"),
        IndexEntry("Theme apps without a monochrome icon", Screen.Icons, "themed fallback"),
        IndexEntry("Normalize legacy icons", Screen.Icons, "old square background"),
        IndexEntry("Add an exception", Screen.Icons, "unique per app icon different pack mix override custom single"),
        IndexEntry("Reset icon style", Screen.Icons, "editor defaults undo"),
        IndexEntry("Remove all exceptions", Screen.Icons, "unique per app icons reset"),
        IndexEntry("Clear icon cache", Screen.Icons, "refresh reload rerender icon pack update"),
        IndexEntry("Icon size", Screen.Icons, "scale bigger smaller"),
        IndexEntry("Labels", Screen.Icons, "app names text"),
        IndexEntry("Label size", Screen.Icons, "text font size"),
        IndexEntry("Label lines", Screen.Icons, "two lines wrap"),
        IndexEntry("Label color", Screen.Icons, "text color"),
        IndexEntry("Label shadow", Screen.Icons, "text shadow"),
        IndexEntry("Label font", Screen.Icons, "typeface"),
        // Folders
        IndexEntry("Preview", Screen.Folders, "folder icon style stack fan grid line"),
        IndexEntry("Open as", Screen.Folders, "popup full screen"),
        IndexEntry("Icon background", Screen.Folders, "folder circle"),
        IndexEntry("Blur behind folders", Screen.Folders, "blur"),
        // Gestures
        IndexEntry("Swipe up", Screen.Gestures, "gesture drawer"),
        IndexEntry("Swipe down", Screen.Gestures, "gesture notifications"),
        IndexEntry("Double tap", Screen.Gestures, "gesture lock screen sleep"),
        IndexEntry("Pinch in", Screen.Gestures, "gesture edit"),
        IndexEntry("Home button (while on home)", Screen.Gestures, "gesture default page"),
        IndexEntry("Icon swipe actions", Screen.Gestures, "per icon swipe"),
        IndexEntry("Reset gestures to defaults", Screen.Gestures, "restore"),
        // Search
        IndexEntry("Hidden shortcut apps", Screen.Search, "shortcut exclude hide app remove show again"),
        IndexEntry("Contacts", Screen.Search, "people search"),
        IndexEntry("Call and message buttons", Screen.Search, "contact phone dial sms text"),
        IndexEntry("Calendar events", Screen.Search, "agenda appointment meeting"),
        IndexEntry("Calculator", Screen.Search, "math"),
        IndexEntry("Unit converter", Screen.Search, "conversion"),
        IndexEntry("Fuzzy matching", Screen.Search, "typo"),
        IndexEntry("Other alphabets", Screen.Search, "transliterate script cyrillic greek japanese korean arabic latin"),
        IndexEntry("Enter launches top result", Screen.Search, "keyboard"),
        IndexEntry("Web search suggestion", Screen.Search, "google browser"),
        IndexEntry("Quick actions", Screen.QuickActions, "call text sms email url link alarm timer calendar event order reorder"),
        IndexEntry("Search in an app", Screen.QuickActions, "app search spotify youtube maps chip"),
        IndexEntry("Add custom action", Screen.QuickActions, "intent custom chip advanced"),
        IndexEntry("Add search engine", Screen.Search, "web engine kagi duckduckgo wikipedia custom url"),
        IndexEntry("Category filters", Screen.Search, "chips apps contacts shortcuts only filter"),
        // Dots
        IndexEntry("Dot style", Screen.Badges, "badges notification count"),
        // Look & feel
        IndexEntry("Theme", Screen.Theme, "dark mode night light oled amoled black custom palette"),
        IndexEntry("Colors", Screen.Theme, "material you wallpaper accent dynamic"),
        IndexEntry("Corner radius", Screen.Theme, "rounded corners shape"),
        IndexEntry("Blur effects", Screen.Theme, "blur"),
        IndexEntry("Background blur", Screen.Theme, "blur intensity strength wallpaper home behind drawer folder"),
        IndexEntry("Card blur", Screen.Theme, "blur intensity strength frosted glass folder card"),
        IndexEntry("Font", Screen.Theme, "typeface condensed serif"),
        IndexEntry("High-contrast text", Screen.Theme, "legibility readability accessibility outline"),
        IndexEntry("Accent tint on surfaces", Screen.Theme, "colorful tinted background"),
        // Glance
        IndexEntry("Show At a glance", Screen.Glance, "date smartspace header"),
        IndexEntry("Next calendar event", Screen.Glance, "calendar agenda"),
        IndexEntry("Next alarm", Screen.Glance, "alarm clock"),
        // System
        IndexEntry("Default home app", Screen.Permissions, "set default launcher"),
        IndexEntry("Gesture service", Screen.Permissions, "accessibility"),
        IndexEntry("Notification access", Screen.Permissions, "dots badges listener"),
        IndexEntry("Back up now", Screen.Backup, "export save"),
        IndexEntry("Restore from file", Screen.Backup, "import"),
        IndexEntry("Reset all settings", Screen.Backup, "factory defaults"),
        IndexEntry("Save note", Screen.Dev, "dev bug report screenshot problem debug notes misbehaving"),
        IndexEntry("Version history", Screen.About, "changelog release notes what's new update version build"),
    )

    /** Title prefix > title word > keyword matches; every query word must hit. */
    fun search(query: String): List<IndexEntry> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return entries.mapNotNull { e ->
            val title = e.title.lowercase()
            val hay = "$title ${e.screen.title.lowercase()} ${e.keywords}"
            if (!words.all { hay.contains(it) }) return@mapNotNull null
            val score = when {
                title.startsWith(words.first()) -> 3
                words.all { title.contains(it) } -> 2
                else -> 1
            }
            e to score
        }.sortedByDescending { it.second }.map { it.first }
    }
}
