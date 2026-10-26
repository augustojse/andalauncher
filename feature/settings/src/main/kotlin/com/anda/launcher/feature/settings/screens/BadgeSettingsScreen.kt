package com.anda.launcher.feature.settings.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.anda.launcher.core.model.BadgeMode
import com.anda.launcher.core.ui.SystemSettingsIntent
import com.anda.launcher.core.ui.startActivitySafely
import com.anda.launcher.feature.badges.BadgeRepository
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.ColorRow
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.controls.SegmentedRow
import com.anda.launcher.feature.settings.components.layout.SettingsGroup

@Composable
internal fun BadgeSettingsScreen(b: SettingsBinder) {
    val context = LocalContext.current
    if (!BadgeRepository.isAccessGranted(context)) {
        SettingsGroup {
            NavRow("Grant notification access", "Required to show dots", Icons.Filled.Notifications) {
                context.startActivitySafely(BadgeRepository.accessSettingsIntent(context), BadgeRepository.accessSettingsFallback, SystemSettingsIntent)
            }
        }
    }
    SettingsGroup {
        SegmentedRow("Dot style", b.bind({ it.badges.mode }) { copy(badges = badges.copy(mode = it)) }, BadgeMode.entries)
        ColorRow("Color", b.bind({ it.badges.color }) { copy(badges = badges.copy(color = it)) })
    }
}
