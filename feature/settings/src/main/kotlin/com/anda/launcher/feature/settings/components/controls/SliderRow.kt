package com.anda.launcher.feature.settings.components.controls

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anda.launcher.feature.settings.components.Setting
import com.anda.launcher.feature.settings.components.layout.SettingFrame
import com.anda.launcher.feature.settings.components.layout.SettingTitle
import kotlin.math.roundToInt

/**
 * Integer slider. The preview follows the thumb live; DataStore is written once, on release.
 */
@Composable
internal fun SliderRow(
    title: String,
    setting: Setting<Int>,
    range: IntRange,
    suffix: String = "",
    /** Tick marks; wide ranges (angles, percentages) pass 0 for a smooth track. */
    steps: Int = (range.last - range.first - 1).coerceAtLeast(0),
    format: (Int) -> String = { "$it$suffix" },
) {
    var local by remember(setting.value) { mutableFloatStateOf(setting.value.toFloat()) }
    SettingFrame(title, setting.modified, setting::reset, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingTitle(title, setting.modified, Modifier.weight(1f))
            Text(format(local.roundToInt()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                setting.preview(it.roundToInt())
            },
            onValueChangeFinished = { setting.set(local.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
        )
    }
}

@Composable
internal fun FloatSliderRow(title: String, setting: Setting<Float>, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    var local by remember(setting.value) { mutableFloatStateOf(setting.value) }
    SettingFrame(title, setting.modified, setting::reset, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingTitle(title, setting.modified, Modifier.weight(1f))
            Text("${(local * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = local,
            onValueChange = {
                local = it
                setting.preview(it)
            },
            onValueChangeFinished = { setting.set(local) },
            valueRange = range,
        )
    }
}
