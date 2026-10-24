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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.anda.launcher.core.model.ColorSource
import com.anda.launcher.core.model.CustomPalette
import com.anda.launcher.core.model.FontChoice
import com.anda.launcher.core.model.PaletteBase
import com.anda.launcher.core.model.ThemeMode
import com.anda.launcher.core.model.ThemeSettings
import kotlin.math.roundToInt

@Composable
fun isLauncherDark(settings: ThemeSettings): Boolean = when (settings.themeMode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark, ThemeMode.Oled -> true
    ThemeMode.Custom -> settings.custom.base != PaletteBase.Light
}

/**
 * Material You by default; "Wallpaper" seeds from the wallpaper's primary color (Smart Launcher's
 * ambient theme); "Custom" seeds from a user accent. OLED and the custom theme then reshape that
 * scheme, and the Advanced options (tint, contrast) apply on top of every theme.
 */
@Composable
fun LauncherTheme(settings: ThemeSettings, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isLauncherDark(settings)
    val wallpaper = rememberWallpaperTone()
    // Keyed on the wallpaper too: a new wallpaper re-seeds "From wallpaper" and refreshes Monet.
    val scheme = remember(settings, dark, wallpaper) {
        var scheme = when (settings.colorSource) {
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
        when (settings.themeMode) {
            ThemeMode.Oled -> scheme = scheme.withSurface(Color.Black)
            ThemeMode.Custom -> scheme = scheme.customized(settings.custom)
            else -> Unit
        }
        if (settings.surfaceTint > 0f) scheme = scheme.tinted(settings.surfaceTint)
        if (settings.highContrast) scheme = scheme.highContrast()
        scheme
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

// ---- Theme → Blur --------------------------------------------------------------------------------

/** Window blur of the wallpaper behind the drawer and open folders, in px (the window API's unit). */
fun ThemeSettings.wallpaperBlurPx(): Int = if (blur) (backgroundBlur * 120).roundToInt() else 0

/** Blur of the home screen behind an open folder. */
fun ThemeSettings.homeBlurRadius(): Dp = if (blur) (backgroundBlur * 24).dp else 0.dp

/** Frosted glass inside folder cards; 0 means a plain translucent card. */
fun ThemeSettings.cardBlurRadius(): Dp = if (blur) (cardBlur * 40).dp else 0.dp

// ---- OLED and custom themes ----------------------------------------------------------------------

private fun ColorScheme.customized(palette: CustomPalette): ColorScheme {
    var scheme = if (palette.base == PaletteBase.Black) withSurface(Color.Black) else this
    palette.surface?.let { scheme = scheme.withSurface(Color(it.toInt())) }
    val dark = scheme.surface.luminance() < 0.4f
    palette.primary?.let { c ->
        val t = Color(c.toInt()).tiers(dark)
        scheme = scheme.copy(
            primary = t.color, onPrimary = t.on, primaryContainer = t.container, onPrimaryContainer = t.onContainer,
            inversePrimary = t.inverse, surfaceTint = t.color,
        )
    }
    palette.secondary?.let { c ->
        val t = Color(c.toInt()).tiers(dark)
        scheme = scheme.copy(secondary = t.color, onSecondary = t.on, secondaryContainer = t.container, onSecondaryContainer = t.onContainer)
    }
    palette.tertiary?.let { c ->
        val t = Color(c.toInt()).tiers(dark)
        scheme = scheme.copy(tertiary = t.color, onTertiary = t.on, tertiaryContainer = t.container, onTertiaryContainer = t.onContainer)
    }
    return scheme
}

private class Tiers(val color: Color, val on: Color, val container: Color, val onContainer: Color, val inverse: Color)

/**
 * A picked accent is used exactly as picked; its container and text colors are derived from it.
 * Containers are toned down like Material's: a vivid orange at full saturation made a dark-brick card.
 */
private fun Color.tiers(dark: Boolean) = Tiers(
    color = this,
    on = if (luminance() > 0.4f) Color(0xFF1B1B1F) else Color.White,
    container = withLightness(if (dark) 0.28f else 0.90f, maxSaturation = 0.45f),
    onContainer = withLightness(if (dark) 0.90f else 0.12f),
    inverse = withLightness(if (dark) 0.40f else 0.80f),
)

/**
 * Every surface role from one background color. Containers step away from it (lighter on a dark
 * background, darker on a light one), and text gets the contrasting neutral.
 */
private fun ColorScheme.withSurface(background: Color): ColorScheme {
    val dark = background.luminance() < 0.4f
    fun step(amount: Float) = background.shiftLightness(if (dark) amount else -amount)
    val on = if (dark) Color(0xFFE6E1E5) else Color(0xFF1B1B1F)
    return copy(
        background = background,
        onBackground = on,
        surface = background,
        onSurface = on,
        surfaceDim = step(-0.03f),
        surfaceBright = step(0.12f),
        surfaceContainerLowest = step(-0.02f),
        surfaceContainerLow = step(0.025f),
        surfaceContainer = step(0.045f),
        surfaceContainerHigh = step(0.07f),
        surfaceContainerHighest = step(0.10f),
        surfaceVariant = step(0.12f),
        onSurfaceVariant = lerp(on, background, 0.28f),
        outline = lerp(on, background, 0.5f),
        outlineVariant = lerp(on, background, 0.8f),
        inverseSurface = on,
        inverseOnSurface = background,
    )
}

/** Advanced → Accent tint: surfaces lean toward the primary color, at most a quarter of the way. */
private fun ColorScheme.tinted(amount: Float): ColorScheme {
    val f = amount.coerceIn(0f, 1f) * 0.25f
    fun Color.tint() = lerp(this, primary, f)
    return copy(
        background = background.tint(),
        surface = surface.tint(),
        surfaceDim = surfaceDim.tint(),
        surfaceBright = surfaceBright.tint(),
        surfaceContainerLowest = surfaceContainerLowest.tint(),
        surfaceContainerLow = surfaceContainerLow.tint(),
        surfaceContainer = surfaceContainer.tint(),
        surfaceContainerHigh = surfaceContainerHigh.tint(),
        surfaceContainerHighest = surfaceContainerHighest.tint(),
        surfaceVariant = surfaceVariant.tint(),
    )
}

private fun ColorScheme.highContrast(): ColorScheme = copy(
    onSurfaceVariant = onSurface,
    outline = lerp(onSurface, surface, 0.25f),
    outlineVariant = lerp(onSurface, surface, 0.6f),
)

private fun Color.withLightness(lightness: Float, maxSaturation: Float = 1f): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtMost(maxSaturation)
    hsl[2] = lightness.coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.shiftLightness(delta: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    return withLightness(hsl[2] + delta)
}
