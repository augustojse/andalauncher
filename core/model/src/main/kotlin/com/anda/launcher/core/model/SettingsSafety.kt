package com.anda.launcher.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

/**
 * Gesture bindings that survive data from another app version. An unknown gesture name (from a
 * newer version) is skipped, and an unknown or broken action falls back to that gesture's default.
 * Without this a single unreadable entry failed the whole settings file, and DataStore's corruption
 * handler then silently reset *every* setting to its default.
 */
internal object GestureBindingsSerializer : KSerializer<Map<Gesture, LauncherAction>> {
    private val delegate = MapSerializer(Gesture.serializer(), LauncherAction.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<Gesture, LauncherAction>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): Map<Gesture, LauncherAction> {
        val json = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val obj = json.decodeJsonElement() as? JsonObject ?: return GestureSettings.DefaultBindings
        return obj.mapNotNull { (name, element) ->
            val gesture = Gesture.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            val action = runCatching { json.json.decodeFromJsonElement(LauncherAction.serializer(), element) }.getOrNull()
                ?: GestureSettings.DefaultBindings[gesture]
                ?: LauncherAction.None
            gesture to action
        }.toMap()
    }
}

/**
 * Clamps every value to the range the settings UI offers. Stored settings can come from an older
 * build, a hand-edited or foreign backup, or a bug; a grid with 0 columns must never reach the
 * layout code (it crashed every frame, in a loop safe mode couldn't stop).
 */
fun LauncherSettings.sanitized(): LauncherSettings {
    val d = LauncherSettings()
    return copy(
        home = home.copy(
            columns = home.columns.coerceIn(3, 10),
            rows = home.rows.coerceIn(3, 12),
            horizontalPaddingDp = home.horizontalPaddingDp.coerceIn(0, 48),
            verticalPaddingDp = home.verticalPaddingDp.coerceIn(0, 48),
            widgetPaddingDp = home.widgetPaddingDp.coerceIn(0, 24),
            defaultPage = home.defaultPage.coerceAtLeast(0),
            wallpaperDim = home.wallpaperDim.safe(d.home.wallpaperDim).coerceIn(0f, 0.8f),
        ),
        dock = dock.copy(
            columns = dock.columns.coerceIn(3, 8),
            pages = dock.pages.coerceIn(1, 3),
            backgroundOpacity = dock.backgroundOpacity.safe(d.dock.backgroundOpacity).coerceIn(0f, 1f),
        ),
        icons = icons.copy(
            home = icons.home.sanitized(),
            dock = icons.dock.sanitized(),
            drawer = icons.drawer.sanitized(),
            folder = icons.folder.sanitized(),
        ),
        drawer = drawer.copy(
            columns = drawer.columns.coerceIn(3, 8),
            pageRows = drawer.pageRows.coerceIn(3, 10),
            backgroundOpacity = drawer.backgroundOpacity.safe(d.drawer.backgroundOpacity).coerceIn(0f, 1f),
        ),
        folders = folders.copy(
            columns = folders.columns.coerceIn(3, 6),
            backgroundOpacity = folders.backgroundOpacity.safe(d.folders.backgroundOpacity).coerceIn(0f, 1f),
        ),
        theme = theme.copy(cornerRadiusDp = theme.cornerRadiusDp.coerceIn(0, 40)),
    )
}

private fun IconStyle.sanitized(): IconStyle = copy(
    sizePercent = sizePercent.coerceIn(50, 150),
    labelSizeSp = labelSizeSp.coerceIn(8, 18),
    labelLines = labelLines.coerceIn(1, 2),
)

/** NaN or infinity (never produced by the UI, but possible in a foreign file) → [fallback]. */
private fun Float.safe(fallback: Float): Float = if (isFinite()) this else fallback
