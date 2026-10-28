package com.anda.launcher.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun roundTripPreservesEverything() {
        val settings = LauncherSettings(
            home = HomeSettings(columns = 6, subgridPositioning = true),
            gestures = GestureSettings(
                bindings = mapOf(Gesture.DoubleTap to LauncherAction.LaunchApp(ComponentKey("a", "b", 10))),
            ),
            icons = IconSettings(iconPack = "com.example.pack", themedIcons = true),
        )
        val decoded = json.decodeFromString<LauncherSettings>(json.encodeToString(settings))
        assertEquals(settings, decoded)
    }

    @Test
    fun missingAndUnknownFieldsFallBackToDefaults() {
        val decoded = json.decodeFromString<LauncherSettings>("""{"home":{"columns":4},"future":1}""")
        assertEquals(4, decoded.home.columns)
        assertEquals(HomeSettings().rows, decoded.home.rows)
        // Settings saved before 0.6.0 turn script-insensitive search on.
        assertEquals(true, decoded.search.transliterate)
        assertEquals(DockSettings(), decoded.dock)
    }

    @Test
    fun unknownGestureOrActionKeepsTheRestOfTheSettings() {
        // A newer build's gesture and action must not reset everything (it used to throw, and
        // DataStore's corruption handler then replaced the whole file with defaults).
        val decoded = json.decodeFromString<LauncherSettings>(
            """{"home":{"columns":4},"gestures":{"bindings":{
                "DoubleTap":{"type":"teleport"},
                "FutureGesture":{"type":"drawer"},
                "SwipeDown":{"type":"search"}
            }}}""",
        )
        assertEquals(4, decoded.home.columns)
        assertEquals(LauncherAction.OpenSearch, decoded.gestures.actionFor(Gesture.SwipeDown))
        assertEquals(GestureSettings.DefaultBindings[Gesture.DoubleTap], decoded.gestures.actionFor(Gesture.DoubleTap))
        assertEquals(2, decoded.gestures.bindings.size)
    }

    @Test
    fun darkModeFromBeforeThemesBecomesTheTheme() {
        // 0.3 stored only "darkMode"; 0.4's Theme must show the same choice without a migration step.
        val old = json.decodeFromString<LauncherSettings>("""{"theme":{"darkMode":"Dark"}}""")
        assertEquals(ThemeMode.Dark, old.theme.themeMode)
        assertEquals(ThemeMode.System, LauncherSettings().theme.themeMode)
        assertEquals(ThemeMode.Oled, old.theme.copy(mode = ThemeMode.Oled).themeMode)
    }

    @Test
    fun sanitizedClampsBlurAndTint() {
        val theme = LauncherSettings(theme = ThemeSettings(backgroundBlur = 3f, cardBlur = Float.NaN, surfaceTint = -1f)).sanitized().theme
        assertEquals(1f, theme.backgroundBlur)
        assertEquals(ThemeSettings().cardBlur, theme.cardBlur)
        assertEquals(0f, theme.surfaceTint)
    }

    @Test
    fun sanitizedClampsEveryRange() {
        val broken = LauncherSettings(
            home = HomeSettings(columns = 0, rows = 99, horizontalPaddingDp = -5, defaultPage = -1, wallpaperDim = Float.NaN),
            dock = DockSettings(columns = 0, pages = 0, backgroundOpacity = 7f),
            icons = IconSettings(home = IconStyle(sizePercent = 0, labelLines = 0)),
            drawer = DrawerSettings(columns = 100),
            folders = FolderSettings(columns = 1),
            theme = ThemeSettings(cornerRadiusDp = 500),
        ).sanitized()
        assertEquals(3, broken.home.columns)
        assertEquals(12, broken.home.rows)
        assertEquals(0, broken.home.horizontalPaddingDp)
        assertEquals(0, broken.home.defaultPage)
        assertEquals(0f, broken.home.wallpaperDim)
        assertEquals(3, broken.dock.columns)
        assertEquals(1, broken.dock.pages)
        assertEquals(1f, broken.dock.backgroundOpacity)
        assertEquals(50, broken.icons.home.sizePercent)
        assertEquals(1, broken.icons.home.labelLines)
        assertEquals(8, broken.drawer.columns)
        assertEquals(3, broken.folders.columns)
        assertEquals(40, broken.theme.cornerRadiusDp)
    }

    @Test
    fun sanitizedLeavesValidSettingsUntouched() {
        assertEquals(LauncherSettings(), LauncherSettings().sanitized())
    }

    @Test
    fun componentKeyFlattenParse() {
        val key = ComponentKey("com.a", "com.a.Main", 11)
        assertEquals(key, ComponentKey.parse(key.flatten()))
    }
}
