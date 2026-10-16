package com.anda.launcher.core.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.anda.launcher.core.icons.IconRepository
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.IconShapeKind
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.LocalLauncherGraph
import kotlin.math.roundToInt

/**
 * Pushes the icon style into the icon pipeline before the first icon is drawn, so a cold start
 * never renders every icon twice (default spec, then the real one). Settings calls it too, so its
 * previews pick up a new shape or pack while home is in the background.
 */
/** A real app icon rendered in [shape] (settings shape picker). */
@Composable
fun rememberShapePreview(key: ComponentKey?, shape: IconShapeKind, size: Dp = BaseIconSize): ImageBitmap? {
    val icons = LocalLauncherGraph.current.iconRepository
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val version by icons.version.collectAsState()
    return produceState<ImageBitmap?>(null, key, shape, sizePx, version) {
        value = key?.let { icons.shapePreview(it, shape, sizePx)?.asImageBitmap() }
    }.value
}

@Composable
fun rememberIconSpec(icons: IconRepository, settings: LauncherSettings, dark: Boolean): Boolean {
    val density = LocalDensity.current.density
    var ready by remember { mutableStateOf(false) }
    val i = settings.icons
    val maxPercent = maxOf(i.home.sizePercent, i.dock.sizePercent, i.drawer.sizePercent, i.folder.sizePercent)
    val sizePx = (BaseIconSize.value * density * maxPercent / 100f).roundToInt().coerceIn(48, 512)
    val spec = IconRepository.Spec(i.shape, i.iconPack, i.themedIcons, i.themedFallback, i.normalizeLegacy, sizePx, dark)
    LaunchedEffect(spec) {
        icons.updateSpec(spec)
        ready = true
    }
    return ready
}
