package com.anda.launcher.core.ui.icon

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anda.launcher.core.model.BadgeMode
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.IconStyle
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.theme.LocalWallpaperTone
import com.anda.launcher.core.ui.theme.toColorOr
import com.anda.launcher.core.ui.theme.toFontFamily

/** Base icon size at 100 %; per-area size percentages scale it. */
val BaseIconSize: Dp = 56.dp

fun IconStyle.iconSize(): Dp = BaseIconSize * (sizePercent / 100f)

/**
 * Loads an app icon. A memory-cache hit is returned synchronously so warm frames never show a
 * placeholder; misses load on the icon dispatcher.
 *
 * The state is keyed on the app itself, not just remembered: list slots are reused for other apps
 * (search results, rows, folders), and an unkeyed state would keep showing the previous app's icon.
 * A restyle of the same app (new [cacheKey]) keeps the old bitmap until the new one is ready.
 */
@Composable
fun rememberAppIcon(key: ComponentKey, lastUpdate: Long, customIconPath: String? = null): ImageBitmap? {
    val icons = LocalLauncherGraph.current.iconRepository
    val version by icons.version.collectAsState()
    val cacheKey = remember(key, lastUpdate, version) { icons.appKey(key, lastUpdate) }
    val state = remember(key, customIconPath) {
        val hit = if (customIconPath == null) icons.peek(cacheKey)?.asImageBitmap() else null
        mutableStateOf(LoadedIcon(if (hit != null) cacheKey else null, hit))
    }
    LaunchedEffect(state, cacheKey) {
        if (state.value.cacheKey == cacheKey) return@LaunchedEffect
        state.value = LoadedIcon(cacheKey, icons.appIcon(key, lastUpdate, customIconPath)?.asImageBitmap())
    }
    return state.value.bitmap
}

/** The bitmap on screen and the cache key it was loaded for. */
private class LoadedIcon(val cacheKey: String?, val bitmap: ImageBitmap?)

@Composable
fun rememberShortcutIcon(packageName: String, shortcutId: String, user: Long): ImageBitmap? {
    val icons = LocalLauncherGraph.current.iconRepository
    val version by icons.version.collectAsState()
    // Keyed on the shortcut so a reused slot never shows the previous shortcut's icon while loading.
    val state = remember(packageName, shortcutId, user) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(state, version) {
        state.value = icons.shortcutIcon(packageName, shortcutId, user)?.asImageBitmap()
    }
    return state.value
}

/** Default label color on top of the wallpaper: white on dark wallpapers, near-black on light ones. */
val OnWallpaperLabel: Color
    @Composable @ReadOnlyComposable get() = LocalWallpaperTone.current.onWallpaper

/**
 * Icon + label + badges. Used by the desktop, dock, drawer, folders and search so every surface
 * honours the same [IconStyle] options.
 */
@Composable
fun AppIconTile(
    icon: ImageBitmap?,
    label: String,
    style: IconStyle,
    modifier: Modifier = Modifier,
    defaultLabelColor: Color = OnWallpaperLabel,
    badgeCount: Int = 0,
    badgeMode: BadgeMode = BadgeMode.Off,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    isWork: Boolean = false,
    isLocked: Boolean = false,
    iconAlpha: () -> Float = { 1f },
) {
    val iconSize = style.iconSize()
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = if (badgeCount > 0) "$label, $badgeCount notifications" else label
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(iconSize).graphicsLayer { alpha = iconAlpha() }) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(iconSize))
            } else {
                Box(
                    Modifier.size(iconSize).padding(iconSize * 0.08f).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                )
            }
            NotificationBadge(badgeCount, badgeMode, badgeColor, iconSize, Modifier.align(Alignment.TopEnd))
            if (isWork || isLocked) {
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.Work,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.align(Alignment.BottomEnd).size(iconSize * 0.34f)
                        .clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(2.dp),
                )
            }
        }
        if (style.showLabels) {
            Spacer(Modifier.height(4.dp))
            IconLabel(label, style, defaultLabelColor, Modifier.sizeIn(maxWidth = iconSize * 1.6f))
        }
    }
}

@Composable
fun IconLabel(label: String, style: IconStyle, defaultColor: Color, modifier: Modifier = Modifier) {
    val color = style.labelColor.toColorOr(defaultColor)
    Text(
        text = label,
        modifier = modifier,
        maxLines = style.labelLines.coerceAtLeast(1),
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        style = TextStyle(
            color = color,
            fontSize = style.labelSizeSp.sp,
            fontFamily = style.labelFont.toFontFamily(),
            shadow = if (style.labelShadow && color.luminanceIsLight()) {
                Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1.5f), 4f)
            } else {
                null
            },
        ),
    )
}

private fun Color.luminanceIsLight(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) > 0.5f

@Composable
fun NotificationBadge(count: Int, mode: BadgeMode, color: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    if (count <= 0 || mode == BadgeMode.Off) return
    when (mode) {
        BadgeMode.Dots -> Box(modifier.size(iconSize * 0.24f).clip(CircleShape).background(color))
        BadgeMode.Counts -> Box(
            modifier.sizeIn(minWidth = iconSize * 0.34f, minHeight = iconSize * 0.34f)
                .clip(CircleShape).background(color).padding(horizontal = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = (iconSize.value * 0.2f).sp,
                maxLines = 1,
            )
        }
        BadgeMode.Off -> Unit
    }
}
