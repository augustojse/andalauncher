package com.anda.launcher.feature.settings.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.SwitchRow
import com.anda.launcher.feature.settings.components.layout.SettingsGroup
import kotlinx.coroutines.launch

@Composable
internal fun GlanceSettingsScreen(b: SettingsBinder) {
    val g = b.stored.glance
    val context = LocalContext.current
    val calendar = b.bind({ it.glance.showCalendar }) { copy(glance = glance.copy(showCalendar = it)) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) calendar.set(true)
    }
    SettingsGroup {
        SwitchRow("Show At a glance", b.bind({ it.glance.enabled }) { copy(glance = glance.copy(enabled = it)) })
        SwitchRow("Date", b.bind({ it.glance.showDate }) { copy(glance = glance.copy(showDate = it)) }, enabled = g.enabled)
        SwitchRow("Next alarm", b.bind({ it.glance.showNextAlarm }) { copy(glance = glance.copy(showNextAlarm = it)) }, "Within the next 24 hours", enabled = g.enabled)
        SwitchRow(
            "Battery when charging or low",
            b.bind({ it.glance.showBattery }) { copy(glance = glance.copy(showBattery = it)) },
            enabled = g.enabled,
        )
        SwitchRow("Next calendar event", calendar.value, enabled = g.enabled, modified = calendar.modified, onReset = calendar::reset) { v ->
            if (v && context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            } else {
                calendar.set(v)
            }
        }
    }
}
