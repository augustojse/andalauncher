package com.anda.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * Every user-facing option, as one immutable tree (ADR 0002). New fields MUST have defaults so
 * older stored JSON keeps decoding.
 */
@Serializable
data class LauncherSettings(
    val home: HomeSettings = HomeSettings(),
    val dock: DockSettings = DockSettings(),
    val icons: IconSettings = IconSettings(),
    val drawer: DrawerSettings = DrawerSettings(),
    val folders: FolderSettings = FolderSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val search: SearchSettings = SearchSettings(),
    val badges: BadgeSettings = BadgeSettings(),
    val theme: ThemeSettings = ThemeSettings(),
    val glance: GlanceSettings = GlanceSettings(),
    val onboardingDone: Boolean = false,
)

@Serializable
data class HomeSettings(
    val columns: Int = 5,
    val rows: Int = 6,
    /** Nova "subgrid positioning": allow half-cell placement. */
    val subgridPositioning: Boolean = false,
    val horizontalPaddingDp: Int = 12,
    val verticalPaddingDp: Int = 8,
    val infiniteScroll: Boolean = false,
    val pageTransition: PageTransition = PageTransition.Slide,
    val pageIndicator: PageIndicatorStyle = PageIndicatorStyle.Dots,
    val searchBar: SearchBarPosition = SearchBarPosition.BelowDock,
    val searchBarStyle: SearchBarStyle = SearchBarStyle.Pill,
    val lockLayout: Boolean = false,
    val wallpaperScrolling: Boolean = true,
    val showStatusBar: Boolean = true,
    /** Pixel-Fold-style: show two pages side by side on wide screens. */
    val dualPanelOnLargeScreens: Boolean = true,
    val widgetPaddingDp: Int = 8,
    val addNewAppsToHome: Boolean = false,
    val defaultPage: Int = 0,
    /** Darken the wallpaper behind home for legibility, 0..1. */
    val wallpaperDim: Float = 0f,
)

@Serializable
enum class PageTransition { Slide, Cube, Depth, Zoom, Stack, Fade, Rotate }

@Serializable
enum class PageIndicatorStyle { None, Dots, Line }

@Serializable
enum class SearchBarPosition { None, Top, AboveDock, BelowDock }

@Serializable
enum class SearchBarStyle { Pill, Rounded, Square }

@Serializable
data class DockSettings(
    val enabled: Boolean = true,
    val columns: Int = 5,
    /** Nova scrollable dock: number of dock pages. */
    val pages: Int = 1,
    val showBackground: Boolean = false,
    val backgroundColor: Long? = null,
    val backgroundOpacity: Float = 0.35f,
    val backgroundShape: DockShape = DockShape.Rounded,
    val showLabels: Boolean = false,
)

@Serializable
enum class DockShape { Rectangle, Rounded, Pill }

@Serializable
data class IconSettings(
    val shape: IconShapeKind = IconShapeKind.System,
    /** Package name of an ADW/Nova icon pack, or null for system icons. */
    val iconPack: String? = null,
    /** Android 13+ monochrome themed icons. */
    val themedIcons: Boolean = false,
    /** Generate a monochrome icon for apps that don't ship one. */
    val themedFallback: Boolean = true,
    /** Wrap legacy (non-adaptive) icons in an adaptive background. */
    val normalizeLegacy: Boolean = true,
    val home: IconStyle = IconStyle(),
    val dock: IconStyle = IconStyle(showLabels = false),
    val drawer: IconStyle = IconStyle(),
    val folder: IconStyle = IconStyle(),
    /** The corners of [IconShapeKind.Custom]. */
    val customShape: CustomShape = CustomShape(),
    /** The icon editor's style for every app (colors, layout, effects). */
    val style: IconEdit = IconEdit(),
    /** Per-app exceptions, keyed by [ComponentKey.flatten]. */
    val perApp: Map<String, AppIconOverride> = emptyMap(),
) {
    fun exceptionFor(key: ComponentKey): AppIconOverride? = perApp[key.flatten()]

    companion object {
        const val MAX_EXCEPTIONS = 500
    }
}

@Serializable
data class IconStyle(
    val sizePercent: Int = 100,
    val showLabels: Boolean = true,
    val labelSizeSp: Int = 12,
    val labelLines: Int = 1,
    val labelColor: Long? = null,
    val labelShadow: Boolean = true,
    val labelFont: FontChoice = FontChoice.System,
)

/** New shapes go at the end: the ordinal is part of the icon cache key. */
@Serializable
enum class IconShapeKind {
    System, Circle, Squircle, RoundedSquare, Square, Teardrop, Cylinder, Hexagon,
    Pebble, Clover, Flower, Sunny, Arch, Diamond, Octagon, Heart, Leaf, Egg, Custom,
}

@Serializable
data class DrawerSettings(
    val style: DrawerStyle = DrawerStyle.VerticalGrid,
    val columns: Int = 5,
    val pageRows: Int = 6,
    val sort: DrawerSort = DrawerSort.Alphabetical,
    val backgroundColor: Long? = null,
    val backgroundOpacity: Float = 0.92f,
    val blur: Boolean = true,
    /** Smart Launcher one-hand mode puts the search bar at the bottom. */
    val searchBarBottom: Boolean = false,
    val showSuggestions: Boolean = true,
    val fastScroller: Boolean = true,
    val manualTabs: Boolean = true,
    /** Smart Launcher automatic categories, shown as extra tabs. */
    val autoCategoryTabs: Boolean = false,
    val showWorkTab: Boolean = true,
    val openKeyboard: Boolean = false,
    val hiddenAppsInSearch: Boolean = false,
    /** Ask for biometrics before revealing hidden apps or launching locked apps. */
    val authForHiddenApps: Boolean = true,
    val showDrawerFolders: Boolean = true,
)

@Serializable
enum class DrawerStyle { VerticalGrid, HorizontalPaged, List }

@Serializable
enum class DrawerSort { Alphabetical, InstallDate, MostUsed }

@Serializable
data class FolderSettings(
    val preview: FolderPreview = FolderPreview.Grid,
    val openStyle: FolderOpenStyle = FolderOpenStyle.Popup,
    val showIconBackground: Boolean = true,
    val backgroundColor: Long? = null,
    val backgroundOpacity: Float = 0.9f,
    val columns: Int = 4,
    val blur: Boolean = true,
)

@Serializable
enum class FolderPreview { Grid, Stack, Fan, Line }

@Serializable
enum class FolderOpenStyle { Popup, FullScreen }

@Serializable
data class GestureSettings(
    @Serializable(with = GestureBindingsSerializer::class)
    val bindings: Map<Gesture, LauncherAction> = DefaultBindings,
    /** Per-icon swipe-up actions (Nova). */
    val iconSwipeActions: Boolean = true,
) {
    fun actionFor(gesture: Gesture): LauncherAction = bindings[gesture] ?: LauncherAction.None

    companion object {
        val DefaultBindings: Map<Gesture, LauncherAction> = mapOf(
            Gesture.SwipeUp to LauncherAction.OpenDrawer,
            Gesture.SwipeDown to LauncherAction.ExpandNotifications,
            Gesture.DoubleTap to LauncherAction.LockScreen,
            Gesture.TwoFingerSwipeDown to LauncherAction.ExpandQuickSettings,
            Gesture.TwoFingerSwipeUp to LauncherAction.OpenSearch,
            Gesture.PinchIn to LauncherAction.EditMode,
            Gesture.PinchOut to LauncherAction.None,
            Gesture.HomePress to LauncherAction.GoToDefaultPage,
        )
    }
}

@Serializable
data class SearchSettings(
    val apps: Boolean = true,
    val shortcuts: Boolean = true,
    val contacts: Boolean = false,
    /** Call and Message buttons on contact results that have a phone number. */
    val contactActions: Boolean = true,
    val calculator: Boolean = true,
    val unitConverter: Boolean = true,
    val systemSettings: Boolean = true,
    val webSearch: Boolean = true,
    val fuzzyMatching: Boolean = true,
    /** Names in other scripts also match their Latin spelling ("telegram" finds Телеграм). */
    val transliterate: Boolean = true,
    /** Launch the top result when pressing enter (Niagara/Kiss behaviour). */
    val launchTopOnEnter: Boolean = true,
    /** Call, text, email, open, alarm, timer and calendar actions read from the query. */
    val quickActions: Boolean = true,
    /** Extra "Search <name>" chips, each opening a URL in the browser. */
    val webEngines: List<WebEngine> = emptyList(),
    /** Apps / Shortcuts / Contacts / Settings chips above the keyboard while searching. */
    val categoryFilters: Boolean = true,
)

/** A user-defined web search; `%s` in [urlTemplate] is replaced by the encoded query. */
@Serializable
data class WebEngine(val name: String, val urlTemplate: String) {
    fun urlFor(query: String): String =
        urlTemplate.replace(PLACEHOLDER, java.net.URLEncoder.encode(query, Charsets.UTF_8).replace("+", "%20"))

    val isValid: Boolean get() = name.isNotBlank() && isValidTemplate(urlTemplate)

    companion object {
        const val PLACEHOLDER = "%s"
        const val MAX_ENGINES = 10

        fun isValidTemplate(template: String): Boolean =
            (template.startsWith("https://") || template.startsWith("http://")) &&
                PLACEHOLDER in template && template.none(Char::isWhitespace) && template.length <= 500
    }
}

@Serializable
data class BadgeSettings(
    val mode: BadgeMode = BadgeMode.Dots,
    val color: Long? = null,
)

@Serializable
enum class BadgeMode { Off, Dots, Counts }

@Serializable
data class ThemeSettings(
    val colorSource: ColorSource = ColorSource.Dynamic,
    val customAccent: Long = 0xFF4285F4,
    /** Before 0.4 the only theme choice; still read while [mode] was never set. */
    val darkMode: DarkMode = DarkMode.System,
    val mode: ThemeMode? = null,
    /** Used when [themeMode] is [ThemeMode.Custom]. */
    val custom: CustomPalette = CustomPalette(),
    val blur: Boolean = true,
    /** Wallpaper and home screen behind the drawer and open folders, 0..1. */
    val backgroundBlur: Float = 0.5f,
    /** Frosted glass inside folder cards, 0..1; independent of [backgroundBlur]. */
    val cardBlur: Float = 0.5f,
    val cornerRadiusDp: Int = 28,
    val font: FontChoice = FontChoice.System,
    /** Secondary text and outlines as strong as primary text. */
    val highContrast: Boolean = false,
    /** How far surfaces lean toward the primary color, 0..1. */
    val surfaceTint: Float = 0f,
) {
    val themeMode: ThemeMode
        get() = mode ?: when (darkMode) {
            DarkMode.System -> ThemeMode.System
            DarkMode.Light -> ThemeMode.Light
            DarkMode.Dark -> ThemeMode.Dark
        }
}

@Serializable
enum class ColorSource { Dynamic, Wallpaper, Custom }

@Serializable
enum class DarkMode { System, Light, Dark }

/** Oled is Dark with true-black surfaces. */
@Serializable
enum class ThemeMode { System, Light, Dark, Oled, Custom }

/**
 * The custom theme: [base] brightness plus one color per tier. A null tier comes from the color
 * source (Material You, wallpaper or the picked accent), so only the tiers you touch are fixed.
 */
@Serializable
data class CustomPalette(
    val base: PaletteBase = PaletteBase.Dark,
    val primary: Long? = null,
    val secondary: Long? = null,
    val tertiary: Long? = null,
    val surface: Long? = null,
)

@Serializable
enum class PaletteBase { Light, Dark, Black }

@Serializable
enum class FontChoice { System, SansSerif, Serif, Monospace, Condensed }

@Serializable
data class GlanceSettings(
    val enabled: Boolean = true,
    val showDate: Boolean = true,
    val showNextAlarm: Boolean = true,
    val showBattery: Boolean = true,
    val showCalendar: Boolean = false,
)
