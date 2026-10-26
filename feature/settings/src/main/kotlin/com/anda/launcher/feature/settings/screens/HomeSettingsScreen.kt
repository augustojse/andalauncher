package com.anda.launcher.feature.settings.screens

import androidx.compose.runtime.Composable
import com.anda.launcher.core.model.PageIndicatorStyle
import com.anda.launcher.core.model.PageTransition
import com.anda.launcher.core.model.SearchBarPosition
import com.anda.launcher.core.model.SearchBarStyle
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.ChoiceRow
import com.anda.launcher.feature.settings.components.controls.FloatSliderRow
import com.anda.launcher.feature.settings.components.controls.SegmentedRow
import com.anda.launcher.feature.settings.components.controls.SliderRow
import com.anda.launcher.feature.settings.components.controls.SwitchRow
import com.anda.launcher.feature.settings.components.layout.AdvancedSection
import com.anda.launcher.feature.settings.components.layout.SettingsGroup

@Composable
internal fun HomeSettingsScreen(b: SettingsBinder) {
    SettingsGroup("Grid") {
        SliderRow("Columns", b.bind({ it.home.columns }) { copy(home = home.copy(columns = it)) }, 3..10)
        SliderRow("Rows", b.bind({ it.home.rows }) { copy(home = home.copy(rows = it)) }, 3..12)
    }
    SettingsGroup("Search bar") {
        SegmentedRow("Search bar position", b.bind({ it.home.searchBar }) { copy(home = home.copy(searchBar = it)) }, SearchBarPosition.entries, ::searchBarLabel)
        SegmentedRow("Search bar style", b.bind({ it.home.searchBarStyle }) { copy(home = home.copy(searchBarStyle = it)) }, SearchBarStyle.entries)
    }
    SettingsGroup("Pages") {
        ChoiceRow("Scroll effect", b.bind({ it.home.pageTransition }) { copy(home = home.copy(pageTransition = it)) }, PageTransition.entries)
        SegmentedRow("Page indicator", b.bind({ it.home.pageIndicator }) { copy(home = home.copy(pageIndicator = it)) }, PageIndicatorStyle.entries)
    }
    AdvancedSection("home", 11) {
        SwitchRow("Subgrid positioning", b.bind({ it.home.subgridPositioning }) { copy(home = home.copy(subgridPositioning = it)) }, "Place items between cells")
        SliderRow("Horizontal padding", b.bind({ it.home.horizontalPaddingDp }) { copy(home = home.copy(horizontalPaddingDp = it)) }, 0..48, " dp")
        SliderRow("Vertical padding", b.bind({ it.home.verticalPaddingDp }) { copy(home = home.copy(verticalPaddingDp = it)) }, 0..48, " dp")
        SliderRow("Widget padding", b.bind({ it.home.widgetPaddingDp }) { copy(home = home.copy(widgetPaddingDp = it)) }, 0..24, " dp")
        SwitchRow("Infinite scroll", b.bind({ it.home.infiniteScroll }) { copy(home = home.copy(infiniteScroll = it)) }, "Swipe past the last page to reach the first")
        SwitchRow("Wallpaper scrolling", b.bind({ it.home.wallpaperScrolling }) { copy(home = home.copy(wallpaperScrolling = it)) }, "Parallax as you change pages")
        SwitchRow(
            "Two pages side by side",
            b.bind({ it.home.dualPanelOnLargeScreens }) { copy(home = home.copy(dualPanelOnLargeScreens = it)) },
            "On foldables and tablets",
        )
        SwitchRow("Show status bar", b.bind({ it.home.showStatusBar }) { copy(home = home.copy(showStatusBar = it)) })
        FloatSliderRow("Wallpaper dimming", b.bind({ it.home.wallpaperDim }) { copy(home = home.copy(wallpaperDim = it)) }, 0f..0.8f)
        SwitchRow("Lock layout", b.bind({ it.home.lockLayout }) { copy(home = home.copy(lockLayout = it)) }, "Prevent moving or removing items")
        SwitchRow("Add new apps to home screen", b.bind({ it.home.addNewAppsToHome }) { copy(home = home.copy(addNewAppsToHome = it)) })
    }
}

private fun searchBarLabel(p: SearchBarPosition) = when (p) {
    SearchBarPosition.None -> "None"
    SearchBarPosition.Top -> "Top"
    SearchBarPosition.AboveDock -> "Above dock"
    SearchBarPosition.BelowDock -> "Below dock"
}
