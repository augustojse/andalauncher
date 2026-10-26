package com.anda.launcher.feature.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.DockShape
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.ColorRow
import com.anda.launcher.feature.settings.components.controls.FloatSliderRow
import com.anda.launcher.feature.settings.components.controls.SliderRow
import com.anda.launcher.feature.settings.components.controls.SwitchRow
import com.anda.launcher.feature.settings.components.controls.VisualChoiceRow
import com.anda.launcher.feature.settings.components.layout.SettingsGroup

@Composable
internal fun DockSettingsScreen(b: SettingsBinder) {
    val d = b.stored.dock
    SettingsGroup {
        SwitchRow("Show dock", b.bind({ it.dock.enabled }) { copy(dock = dock.copy(enabled = it)) })
        SliderRow("Icons per page", b.bind({ it.dock.columns }) { copy(dock = dock.copy(columns = it)) }, 3..8)
        SliderRow("Dock pages", b.bind({ it.dock.pages }) { copy(dock = dock.copy(pages = it)) }, 1..3)
    }
    SettingsGroup("Background") {
        SwitchRow("Background", b.bind({ it.dock.showBackground }) { copy(dock = dock.copy(showBackground = it)) })
        if (d.showBackground) {
            VisualChoiceRow("Background shape", b.bind({ it.dock.backgroundShape }) { copy(dock = dock.copy(backgroundShape = it)) }, DockShape.entries) { shape ->
                val clip = when (shape) {
                    DockShape.Rectangle -> RoundedCornerShape(0.dp)
                    DockShape.Rounded -> RoundedCornerShape(6.dp)
                    DockShape.Pill -> CircleShape
                }
                Box(Modifier.size(48.dp, 18.dp).clip(clip).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)))
            }
            ColorRow("Background color", b.bind({ it.dock.backgroundColor }) { copy(dock = dock.copy(backgroundColor = it)) })
            FloatSliderRow("Background opacity", b.bind({ it.dock.backgroundOpacity }) { copy(dock = dock.copy(backgroundOpacity = it)) })
        }
    }
    IconStyleGroups("Dock icons", "dock", b, { it.icons.dock }) { copy(icons = icons.copy(dock = it)) }
}
