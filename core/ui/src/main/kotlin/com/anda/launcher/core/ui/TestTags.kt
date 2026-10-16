package com.anda.launcher.core.ui

/**
 * `Modifier.testTag` values shared by the UI and the instrumented tests. Home, drawer and
 * folders render the same icon tiles (same content descriptions), so tests scope lookups by
 * these containers.
 */
object TestTags {
    const val WORKSPACE_GRID = "workspace_grid"
    const val APP_DRAWER = "app_drawer"
    const val SEARCH_RESULTS = "search_results"
    const val FOLDER_PANEL = "folder_panel"
    const val WIDGET_PICKER = "widget_picker"
    const val SETTINGS_SEARCH = "settings_search"
}
