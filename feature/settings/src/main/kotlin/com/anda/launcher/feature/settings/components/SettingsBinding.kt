package com.anda.launcher.feature.settings.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import com.anda.launcher.core.model.LauncherSettings

internal typealias Update = ((LauncherSettings) -> LauncherSettings) -> Unit

/** Factory defaults; a row whose value differs shows the "changed" dot and can be reset. */
internal val DefaultSettings = LauncherSettings()

/**
 * One setting: its stored value, its default, how to save it, and how to show an uncommitted
 * value (a slider mid-drag) in the live preview without writing DataStore every frame.
 */
@Stable
internal class Setting<T>(
    val value: T,
    val default: T,
    private val commit: (T) -> Unit,
    private val showPreview: (T) -> Unit,
) {
    val modified: Boolean get() = value != default
    fun set(v: T) = commit(v)
    fun preview(v: T) = showPreview(v)
    fun reset() = commit(default)
}

/** Creates [Setting]s for one screen. [setDraft] feeds the screen's live preview. */
@Stable
internal class SettingsBinder(
    val stored: LauncherSettings,
    private val update: Update,
    private val setDraft: (LauncherSettings) -> Unit,
) {
    fun <T> bind(get: (LauncherSettings) -> T, put: LauncherSettings.(T) -> LauncherSettings): Setting<T> =
        Setting(
            value = get(stored),
            default = get(DefaultSettings),
            commit = { v -> update { it.put(v) } },
            showPreview = { v -> setDraft(stored.put(v)) },
        )

    fun update(block: (LauncherSettings) -> LauncherSettings) = update.invoke(block)
}

/** Title of the row a settings search jumped to; that row scrolls into view and flashes. */
internal val LocalSettingsHighlight = compositionLocalOf<String?> { null }
