package com.anda.launcher.core.ui.theme

import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.anda.launcher.core.model.ColorSource
import com.anda.launcher.core.model.DarkMode
import com.anda.launcher.core.model.FontChoice
import com.anda.launcher.core.model.ThemeSettings

@Composable
fun isLauncherDark(settings: ThemeSettings): Boolean = when (settings.darkMode) {
    DarkMode.System -> isSystemInDarkTheme()
    DarkMode.Light -> false
    DarkMode.Dark -> true
}

/**
 * Material You by default; "Wallpaper" seeds from the wallpaper's primary color (Smart Launcher's
 * ambient theme); "Custom" seeds from a user accent.
 */
@Composable
fun LauncherTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isLauncherDark(settings)
    val wallpaper = rememberWallpaperTone()
    // Keyed on the wallpaper too: a new wallpaper re-seeds "From wallpaper" and refreshes Monet.
    val scheme = remember(settings.colorSource, settings.customAccent, dark, wallpaper) {
        when (settings.colorSource) {
            ColorSource.Dynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            ColorSource.Wallpaper -> {
                val seed = wallpaper.primary?.toArgb()
                if (seed != null) {
                    seededScheme(seed, dark)
                } else if (dark) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }
            ColorSource.Custom -> seededScheme(settings.customAccent.toInt(), dark)
        }
    }
    val family = settings.font.toFontFamily()
    val typography = remember(family) { Typography().withFamily(family) }
    val shapes = remember(settings.cornerRadiusDp) { launcherShapes(settings.cornerRadiusDp) }
    CompositionLocalProvider(LocalWallpaperTone provides wallpaper) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}

/** Theme → Corner radius drives every Material shape; the largest surfaces get the full radius. */
private fun launcherShapes(radiusDp: Int): Shapes {
    fun r(fraction: Float) = RoundedCornerShape((radiusDp * fraction).dp)
    return Shapes(extraSmall = r(0.15f), small = r(0.3f), medium = r(0.5f), large = r(0.75f), extraLarge = r(1f))
}

private val CondensedFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

fun FontChoice.toFontFamily(): FontFamily = when (this) {
    FontChoice.System -> FontFamily.Default
    FontChoice.SansSerif -> FontFamily.SansSerif
    FontChoice.Serif -> FontFamily.Serif
    FontChoice.Monospace -> FontFamily.Monospace
    FontChoice.Condensed -> CondensedFamily
}

private fun Typography.withFamily(family: FontFamily): Typography = if (family == FontFamily.Default) {
    this
} else {
    copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}

/** Tonal palette approximation from a seed color via HSL lightness steps. */
private fun seededScheme(seed: Int, dark: Boolean): ColorScheme {
    fun tone(hueShift: Float, saturationScale: Float, lightness: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(seed, hsl)
        hsl[0] = (hsl[0] + hueShift + 360f) % 360f
        hsl[1] = (hsl[1] * saturationScale).coerceIn(0f, 1f)
        hsl[2] = lightness
        return Color(ColorUtils.HSLToColor(hsl))
    }
    return if (dark) {
        darkColorScheme(
            primary = tone(0f, 1f, 0.80f),
            onPrimary = tone(0f, 1f, 0.20f),
            primaryContainer = tone(0f, 1f, 0.30f),
            onPrimaryContainer = tone(0f, 1f, 0.90f),
            secondary = tone(0f, 0.5f, 0.80f),
            onSecondary = tone(0f, 0.5f, 0.20f),
            secondaryContainer = tone(0f, 0.5f, 0.30f),
            onSecondaryContainer = tone(0f, 0.5f, 0.90f),
            tertiary = tone(60f, 0.6f, 0.80f),
            tertiaryContainer = tone(60f, 0.6f, 0.30f),
            surface = tone(0f, 0.15f, 0.08f),
            onSurface = tone(0f, 0.1f, 0.90f),
            surfaceVariant = tone(0f, 0.2f, 0.25f),
            onSurfaceVariant = tone(0f, 0.2f, 0.80f),
            surfaceContainer = tone(0f, 0.15f, 0.12f),
            surfaceContainerHigh = tone(0f, 0.15f, 0.17f),
            surfaceContainerHighest = tone(0f, 0.15f, 0.22f),
            background = tone(0f, 0.15f, 0.08f),
            onBackground = tone(0f, 0.1f, 0.90f),
        )
    } else {
        lightColorScheme(
            primary = tone(0f, 1f, 0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(0f, 1f, 0.90f),
            onPrimaryContainer = tone(0f, 1f, 0.10f),
            secondary = tone(0f, 0.5f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = tone(0f, 0.5f, 0.90f),
            onSecondaryContainer = tone(0f, 0.5f, 0.10f),
            tertiary = tone(60f, 0.6f, 0.40f),
            tertiaryContainer = tone(60f, 0.6f, 0.90f),
            surface = tone(0f, 0.2f, 0.98f),
            onSurface = tone(0f, 0.1f, 0.10f),
            surfaceVariant = tone(0f, 0.25f, 0.90f),
            onSurfaceVariant = tone(0f, 0.2f, 0.30f),
            surfaceContainer = tone(0f, 0.2f, 0.94f),
            surfaceContainerHigh = tone(0f, 0.2f, 0.92f),
            surfaceContainerHighest = tone(0f, 0.2f, 0.90f),
            background = tone(0f, 0.2f, 0.98f),
            onBackground = tone(0f, 0.1f, 0.10f),
        )
    }
}

/** Converts a stored ARGB Long setting into a Compose color, or [fallback] when unset. */
fun Long?.toColorOr(fallback: Color): Color = if (this == null) fallback else Color(this.toInt())
