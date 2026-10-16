package com.anda.launcher.core.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** What the launcher needs to know about the current home wallpaper. */
@Immutable
data class WallpaperTone(
    /** True when the wallpaper is dark, so content on it should be light (the default). */
    val isDark: Boolean = true,
    val primary: Color? = null,
    val secondary: Color? = null,
    val tertiary: Color? = null,
) {
    /** Text/indicator color on top of the wallpaper. */
    val onWallpaper: Color get() = if (isDark) Color.White else Color(0xFF1F1F1F)
}

/** Provided by the launcher root; settings previews provide it too. */
val LocalWallpaperTone = staticCompositionLocalOf { WallpaperTone() }

/** Live wallpaper colors: updates when the user changes the wallpaper, without a restart. */
@Composable
fun rememberWallpaperTone(): WallpaperTone {
    val context = LocalContext.current
    val manager = remember { WallpaperManager.getInstance(context) }
    var colors by remember { mutableStateOf(manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)) }
    DisposableEffect(manager) {
        val listener = WallpaperManager.OnColorsChangedListener { newColors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) colors = newColors
        }
        manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.removeOnColorsChangedListener(listener) }
    }
    return remember(colors) { colors.toTone() }
}

private fun WallpaperColors?.toTone(): WallpaperTone {
    if (this == null) return WallpaperTone()
    return WallpaperTone(
        isDark = (colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) == 0,
        primary = Color(primaryColor.toArgb()),
        secondary = secondaryColor?.let { Color(it.toArgb()) },
        tertiary = tertiaryColor?.let { Color(it.toArgb()) },
    )
}
