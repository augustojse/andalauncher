package com.anda.launcher.feature.settings.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.anda.launcher.feature.settings.components.Setting
import com.anda.launcher.feature.settings.components.layout.SettingFrame
import com.anda.launcher.feature.settings.components.layout.SettingTitle

private val palette = listOf(
    null, 0xFFFFFFFF, 0xFF000000, 0xFF202124, 0xFF4285F4, 0xFF34A853, 0xFFFBBC04, 0xFFEA4335,
    0xFF9C27B0, 0xFF00BCD4, 0xFFFF7043, 0xFF8D6E63, 0xFF607D8B, 0xFFE91E63,
)

/**
 * Color choice: "A" (`null`, follow the theme), then [suggestions] (wallpaper / Material You), the
 * fixed palette, and a last button that opens a full picker for any other color.
 */
@Composable
internal fun ColorRow(title: String, setting: Setting<Long?>, suggestions: List<Long> = emptyList()) =
    ColorRow(title, setting.value, setting.modified, setting::reset, suggestions, setting::set)

@Composable
internal fun ColorRow(
    title: String,
    value: Long?,
    modified: Boolean = false,
    onReset: (() -> Unit)? = null,
    suggestions: List<Long> = emptyList(),
    onChange: (Long?) -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    // A color from the picker that's in neither list shows next to "A"; a listed one stays in place,
    // so tapping a swatch never makes the row jump.
    val colors = remember(value, suggestions) {
        val listed = (listOf(null) + suggestions + palette).distinct()
        if (value in listed) listed else listOf(null, value) + listed.drop(1)
    }
    SettingFrame(title, modified, onReset, contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)) {
        SettingTitle(title, modified, Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(colors) { color ->
                val selected = color == value
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(color?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary)
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { onChange(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (color == null) Text("A", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
            item {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable { picking = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Colorize, contentDescription = "Pick any color", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
    if (picking) {
        ColorPickerDialog(
            initial = value ?: MaterialTheme.colorScheme.primary.toArgbLong(),
            onPick = {
                picking = false
                onChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}

/** Hue, saturation and brightness sliders plus a hex field, for any color the palettes don't offer. */
@Composable
internal fun ColorPickerDialog(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var brightness by remember { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(hue, saturation, brightness)
    var hex by remember { mutableStateOf(color.toHex()) }
    fun slid(update: () -> Unit) {
        update()
        hex = Color.hsv(hue, saturation, brightness).toHex()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(56.dp).clip(MaterialTheme.shapes.medium).background(color))
                Spacer(Modifier.size(8.dp))
                Text("Hue", style = MaterialTheme.typography.labelLarge)
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })),
                )
                Slider(value = hue, onValueChange = { slid { hue = it } }, valueRange = 0f..359f)
                Text("Saturation", style = MaterialTheme.typography.labelLarge)
                Slider(value = saturation, onValueChange = { slid { saturation = it } })
                Text("Brightness", style = MaterialTheme.typography.labelLarge)
                Slider(value = brightness, onValueChange = { slid { brightness = it } })
                OutlinedTextField(
                    value = hex,
                    onValueChange = { text ->
                        hex = text.take(7)
                        parseHex(hex)?.let { parsed ->
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed, hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        }
                    },
                    label = { Text("Hex") },
                    isError = parseHex(hex) == null,
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color.toArgbLong()) }) { Text("Use") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Color.toHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

/** "#3366FF" or "3366ff" → opaque ARGB; anything else → null. */
internal fun parseHex(text: String): Int? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6 || !digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return (0xFF000000.toInt() or digits.toInt(16))
}

internal fun Color.toArgbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL
