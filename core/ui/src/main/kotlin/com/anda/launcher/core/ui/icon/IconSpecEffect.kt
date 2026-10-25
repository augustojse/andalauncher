package com.anda.launcher.core.ui.icon

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.anda.launcher.core.icons.IconRepository
import com.anda.launcher.core.model.AppIconOverride
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.IconColorRole
import com.anda.launcher.core.model.IconShapeKind
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.LocalLauncherGraph
import kotlin.math.roundToInt

/** A real app icon rendered in [shape] (settings shape picker). */
@Composable
fun rememberShapePreview(key: ComponentKey?, shape: IconShapeKind, size: Dp = BaseIconSize): ImageBitmap? {
    val icons = LocalLauncherGraph.current.iconRepository
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val version by icons.version.collectAsState()
    // Keyed on the app, so switching the sample app never shows the previous app's icon meanwhile.
    val state = remember(key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(state, shape, sizePx, version) {
        state.value = key?.let { icons.shapePreview(it, shape, sizePx)?.asImageBitmap() }
    }
    return state.value
}

/**
 * The icon editor's live preview of [key]: [draft] is the unsaved spec (shape, style, pack, theme
 * colors) and [exception] the unsaved per-app exception. Re-renders as they change; a change
 * mid-render cancels the stale one, so dragging a slider only ever renders the latest value.
 */
@Composable
fun rememberIconPreview(key: ComponentKey?, size: Dp, draft: IconRepository.Spec, exception: AppIconOverride?): ImageBitmap? {
    val icons = LocalLauncherGraph.current.iconRepository
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val state = remember(key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(state, key, sizePx, draft, exception) {
        state.value = key?.let { icons.preview(it, sizePx, draft, exception)?.asImageBitmap() }
    }
    return state.value
}

/** The spec the launcher renders with: the stored settings plus the theme's colors. */
fun LauncherSettings.iconSpec(sizePx: Int, dark: Boolean, scheme: ColorScheme): IconRepository.Spec {
    val i = icons
    return IconRepository.Spec(
        shape = i.shape,
        iconPack = i.iconPack,
        themed = i.themedIcons,
        themedFallback = i.themedFallback,
        normalizeLegacy = i.normalizeLegacy,
        sizePx = sizePx,
        darkTheme = dark,
        customShape = i.customShape,
        style = i.style,
        perApp = i.perApp,
        palette = scheme.iconPalette(),
    )
}

/** The scheme's colors in [IconColorRole] order, for icons that follow the theme. */
fun ColorScheme.iconPalette(): List<Int> = IconColorRole.entries.map { role -> color(role).toArgb() }

fun ColorScheme.color(role: IconColorRole): Color = when (role) {
    IconColorRole.Primary -> primary
    IconColorRole.OnPrimary -> onPrimary
    IconColorRole.PrimaryContainer -> primaryContainer
    IconColorRole.OnPrimaryContainer -> onPrimaryContainer
    IconColorRole.Secondary -> secondary
    IconColorRole.SecondaryContainer -> secondaryContainer
    IconColorRole.OnSecondaryContainer -> onSecondaryContainer
    IconColorRole.Tertiary -> tertiary
    IconColorRole.TertiaryContainer -> tertiaryContainer
    IconColorRole.OnTertiaryContainer -> onTertiaryContainer
    IconColorRole.Surface -> surface
    IconColorRole.SurfaceContainerHighest -> surfaceContainerHighest
    IconColorRole.OnSurface -> onSurface
    IconColorRole.InverseSurface -> inverseSurface
    IconColorRole.InverseOnSurface -> inverseOnSurface
}

/** Largest icon size any area uses, in pixels: icons render once at that size. */
@Composable
fun iconRenderSizePx(settings: LauncherSettings): Int {
    val density = LocalDensity.current.density
    val i = settings.icons
    val maxPercent = maxOf(i.home.sizePercent, i.dock.sizePercent, i.drawer.sizePercent, i.folder.sizePercent)
    return (BaseIconSize.value * density * maxPercent / 100f).roundToInt().coerceIn(48, 512)
}

/**
 * Pushes the icon style into the icon pipeline before the first icon is drawn, so a cold start
 * never renders every icon twice (default spec, then the real one). Settings calls it too, so its
 * previews pick up a new shape or pack while home is in the background. [scheme] supplies the
 * theme colors that themed icons and the icon editor's color roles use.
 */
@Composable
fun rememberIconSpec(icons: IconRepository, settings: LauncherSettings, dark: Boolean, scheme: ColorScheme): Boolean {
    var ready by remember { mutableStateOf(false) }
    val spec = settings.iconSpec(iconRenderSizePx(settings), dark, scheme)
    LaunchedEffect(spec) {
        icons.updateSpec(spec)
        ready = true
    }
    return ready
}
