package com.anda.launcher.feature.settings.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import com.anda.launcher.core.model.DrawerSort
import com.anda.launcher.core.model.DrawerStyle
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.ColorRow
import com.anda.launcher.feature.settings.components.controls.FloatSliderRow
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.controls.SegmentedRow
import com.anda.launcher.feature.settings.components.controls.SliderRow
import com.anda.launcher.feature.settings.components.controls.SwitchRow
import com.anda.launcher.feature.settings.components.layout.AdvancedSection
import com.anda.launcher.feature.settings.components.layout.SettingsGroup

@Composable
internal fun DrawerSettingsScreen(b: SettingsBinder, openHidden: () -> Unit) {
    val d = b.stored.drawer
    SettingsGroup("Layout") {
        SegmentedRow("Style", b.bind({ it.drawer.style }) { copy(drawer = drawer.copy(style = it)) }, DrawerStyle.entries, ::drawerStyleLabel)
        if (d.style != DrawerStyle.List) SliderRow("Columns", b.bind({ it.drawer.columns }) { copy(drawer = drawer.copy(columns = it)) }, 3..8)
        SegmentedRow("Sort", b.bind({ it.drawer.sort }) { copy(drawer = drawer.copy(sort = it)) }, DrawerSort.entries, ::sortLabel)
        SwitchRow("Search bar at the bottom", b.bind({ it.drawer.searchBarBottom }) { copy(drawer = drawer.copy(searchBarBottom = it)) }, "Easier to reach one-handed")
    }
    SettingsGroup("Organisation") {
        SwitchRow("Custom tabs", b.bind({ it.drawer.manualTabs }) { copy(drawer = drawer.copy(manualTabs = it)) }, "Create tabs from the drawer's + button")
        SwitchRow("Automatic category tabs", b.bind({ it.drawer.autoCategoryTabs }) { copy(drawer = drawer.copy(autoCategoryTabs = it)) }, "Social, Media, Games, …")
        SwitchRow("Separate work apps", b.bind({ it.drawer.showWorkTab }) { copy(drawer = drawer.copy(showWorkTab = it)) })
        SwitchRow("Drawer folders", b.bind({ it.drawer.showDrawerFolders }) { copy(drawer = drawer.copy(showDrawerFolders = it)) })
    }
    SettingsGroup("Privacy") {
        SwitchRow(
            "Require fingerprint for hidden & locked apps",
            b.bind({ it.drawer.authForHiddenApps }) { copy(drawer = drawer.copy(authForHiddenApps = it)) },
        )
        SwitchRow("Show hidden apps in search", b.bind({ it.drawer.hiddenAppsInSearch }) { copy(drawer = drawer.copy(hiddenAppsInSearch = it)) })
        NavRow("Manage hidden apps", null, Icons.Filled.VisibilityOff, openHidden)
    }
    AdvancedSection("drawer", 7) {
        if (d.style == DrawerStyle.HorizontalPaged) {
            SliderRow("Rows per page", b.bind({ it.drawer.pageRows }) { copy(drawer = drawer.copy(pageRows = it)) }, 3..10)
        }
        SwitchRow("Fast scroller", b.bind({ it.drawer.fastScroller }) { copy(drawer = drawer.copy(fastScroller = it)) }, "A–Z strip; shown when sorted A–Z")
        SwitchRow(
            "Favorites row",
            b.bind({ it.drawer.showFavorites }) { copy(drawer = drawer.copy(showFavorites = it)) },
            "Long-press a search result or app to pin it",
        )
        SwitchRow("Suggested apps row", b.bind({ it.drawer.showSuggestions }) { copy(drawer = drawer.copy(showSuggestions = it)) })
        SwitchRow("Open keyboard automatically", b.bind({ it.drawer.openKeyboard }) { copy(drawer = drawer.copy(openKeyboard = it)) })
        ColorRow("Background color", b.bind({ it.drawer.backgroundColor }) { copy(drawer = drawer.copy(backgroundColor = it)) })
        FloatSliderRow("Background opacity", b.bind({ it.drawer.backgroundOpacity }) { copy(drawer = drawer.copy(backgroundOpacity = it)) })
        SwitchRow("Blur wallpaper", b.bind({ it.drawer.blur }) { copy(drawer = drawer.copy(blur = it)) })
    }
    IconStyleGroups("Drawer icons", "drawer", b, { it.icons.drawer }) { copy(icons = icons.copy(drawer = it)) }
}

internal fun drawerStyleLabel(style: DrawerStyle) = when (style) {
    DrawerStyle.VerticalGrid -> "Grid"
    DrawerStyle.HorizontalPaged -> "Pages"
    DrawerStyle.List -> "List"
}

internal fun sortLabel(sort: DrawerSort) = when (sort) {
    DrawerSort.Alphabetical -> "A–Z"
    DrawerSort.InstallDate -> "Newest"
    DrawerSort.MostUsed -> "Most used"
}
