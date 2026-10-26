package com.anda.launcher.feature.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.ColorSource
import com.anda.launcher.core.model.CustomPalette
import com.anda.launcher.core.model.FontChoice
import com.anda.launcher.core.model.PaletteBase
import com.anda.launcher.core.model.ThemeMode
import com.anda.launcher.core.ui.theme.LocalWallpaperTone
import com.anda.launcher.feature.settings.components.Setting
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.ChoiceRow
import com.anda.launcher.feature.settings.components.controls.ColorRow
import com.anda.launcher.feature.settings.components.controls.FloatSliderRow
import com.anda.launcher.feature.settings.components.controls.SegmentedRow
import com.anda.launcher.feature.settings.components.controls.SliderRow
import com.anda.launcher.feature.settings.components.controls.SwitchRow
import com.anda.launcher.feature.settings.components.controls.VisualChoiceRow
import com.anda.launcher.feature.settings.components.controls.toArgbLong
import com.anda.launcher.feature.settings.components.humanize
import com.anda.launcher.feature.settings.components.layout.AdvancedSection
import com.anda.launcher.feature.settings.components.layout.Hint
import com.anda.launcher.feature.settings.components.layout.SettingFrame
import com.anda.launcher.feature.settings.components.layout.SettingTitle
import com.anda.launcher.feature.settings.components.layout.SettingsGroup

internal fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> "Auto"
    ThemeMode.Oled -> "OLED"
    else -> humanize(mode.name)
}

/**
 * Look & feel. The main card holds the few choices most people make (theme, colors, corners) and
 * blur gets its own card with separate background and card intensities; per-tier colors for the
 * custom theme, contrast and tint live under Advanced.
 */
@Composable
internal fun ThemeSettingsScreen(b: SettingsBinder) {
    val t = b.stored.theme
    val custom = t.themeMode == ThemeMode.Custom
    SettingsGroup {
        VisualChoiceRow("Theme", b.bind({ it.theme.themeMode }) { copy(theme = theme.copy(mode = it)) }, ThemeMode.entries, ::themeLabel) {
            ThemeSwatch(it)
        }
        SegmentedRow("Colors", b.bind({ it.theme.colorSource }) { copy(theme = theme.copy(colorSource = it)) }, ColorSource.entries) {
            when (it) {
                ColorSource.Dynamic -> "Material You"
                ColorSource.Wallpaper -> "Wallpaper"
                ColorSource.Custom -> "Pick"
            }
        }
        if (t.colorSource == ColorSource.Custom) {
            val accent = b.bind({ it.theme.customAccent }) { copy(theme = theme.copy(customAccent = it)) }
            ColorRow("Accent", accent.value, accent.modified, accent::reset, suggestions = colorSuggestions(surface = false)) { v ->
                v?.let(accent::set)
            }
        }
        if (custom) {
            SegmentedRow("Custom theme base", b.customPart({ it.base }) { copy(base = it) }, PaletteBase.entries)
            PaletteRow()
        }
        SliderRow("Corner radius", b.bind({ it.theme.cornerRadiusDp }) { copy(theme = theme.copy(cornerRadiusDp = it)) }, 0..40, " dp")
    }
    if (custom) Hint("The custom theme starts from the colors above. Fine-tune each color under Advanced.")

    SettingsGroup("Blur") {
        SwitchRow("Blur effects", b.bind({ it.theme.blur }) { copy(theme = theme.copy(blur = it)) })
        if (t.blur) {
            FloatSliderRow("Background blur", b.bind({ it.theme.backgroundBlur }) { copy(theme = theme.copy(backgroundBlur = it)) })
            FloatSliderRow("Card blur", b.bind({ it.theme.cardBlur }) { copy(theme = theme.copy(cardBlur = it)) })
        }
    }
    if (t.blur) {
        Hint(
            "Background blur softens the wallpaper and home screen behind the drawer and open folders. " +
                "Card blur is the frosted glass inside a folder; it shows more as Folders → Background opacity goes down.",
        )
    }

    AdvancedSection("theme", if (custom) 7 else 3) {
        ChoiceRow("Font", b.bind({ it.theme.font }) { copy(theme = theme.copy(font = it)) }, FontChoice.entries)
        SwitchRow(
            "High-contrast text",
            b.bind({ it.theme.highContrast }) { copy(theme = theme.copy(highContrast = it)) },
            "Secondary text and outlines as strong as titles",
        )
        FloatSliderRow("Accent tint on surfaces", b.bind({ it.theme.surfaceTint }) { copy(theme = theme.copy(surfaceTint = it)) })
        if (custom) {
            val accents = colorSuggestions(surface = false)
            ColorRow("Primary color", b.customPart({ it.primary }) { copy(primary = it) }, accents)
            ColorRow("Secondary color", b.customPart({ it.secondary }) { copy(secondary = it) }, accents)
            ColorRow("Tertiary color", b.customPart({ it.tertiary }) { copy(tertiary = it) }, accents)
            ColorRow("Surface color", b.customPart({ it.surface }) { copy(surface = it) }, colorSuggestions(surface = true))
        }
    }
}

private fun <T> SettingsBinder.customPart(get: (CustomPalette) -> T, put: CustomPalette.(T) -> CustomPalette): Setting<T> =
    bind({ get(it.theme.custom) }) { copy(theme = theme.copy(custom = theme.custom.put(it))) }

/**
 * Starting points for a custom color: the wallpaper's own colors, then Material You's. Accents use
 * the mid tones; surfaces the darkest and lightest neutrals.
 */
@Composable
private fun colorSuggestions(surface: Boolean): List<Long> {
    val wallpaper = LocalWallpaperTone.current
    val fromWallpaper = listOfNotNull(wallpaper.primary, wallpaper.secondary, wallpaper.tertiary).map { it.toArgbLong() }
    val ids = if (surface) {
        listOf(
            android.R.color.system_neutral1_900, android.R.color.system_neutral2_800,
            android.R.color.system_neutral1_50, android.R.color.system_neutral2_100,
        )
    } else {
        listOf(
            android.R.color.system_accent1_400, android.R.color.system_accent2_400, android.R.color.system_accent3_400,
            android.R.color.system_accent1_200, android.R.color.system_accent3_200,
        )
    }
    val fromSystem = ids.map { colorResource(it).toArgbLong() } + if (surface) listOf(0xFF000000) else emptyList()
    return (if (surface) fromSystem + fromWallpaper else fromWallpaper + fromSystem).distinct()
}

/** A tiny screen in each theme's colors, so the choice looks like the result. */
@Composable
private fun ThemeSwatch(mode: ThemeMode) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)
    val frame = Modifier.size(width = 36.dp, height = 48.dp).clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    @Composable
    fun Screen(background: Color, modifier: Modifier) {
        Box(modifier.background(background).padding(5.dp), contentAlignment = Alignment.BottomStart) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
        }
    }
    when (mode) {
        ThemeMode.System -> Row(frame) {
            Screen(LightSwatch, Modifier.weight(1f).fillMaxHeight())
            Screen(DarkSwatch, Modifier.weight(1f).fillMaxHeight())
        }
        ThemeMode.Light -> Screen(LightSwatch, frame)
        ThemeMode.Dark -> Screen(DarkSwatch, frame)
        ThemeMode.Oled -> Screen(Color.Black, frame)
        ThemeMode.Custom -> Column(frame) {
            Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.primary))
            Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.secondary))
            Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.tertiary))
        }
    }
}

private val LightSwatch = Color(0xFFF3F1F6)
private val DarkSwatch = Color(0xFF26252A)

/** The custom theme's resolved tiers, read-only; Advanced changes them. */
@Composable
private fun PaletteRow() {
    val scheme = MaterialTheme.colorScheme
    SettingFrame("Palette") {
        SettingTitle("Palette", modified = false)
        Spacer(Modifier.size(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Primary" to scheme.primary, "Secondary" to scheme.secondary, "Tertiary" to scheme.tertiary, "Surface" to scheme.surface)
                .forEach { (name, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(color).border(1.dp, scheme.outlineVariant, CircleShape))
                        Spacer(Modifier.size(4.dp))
                        Text(name, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                }
        }
    }
}
