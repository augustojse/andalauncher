package com.anda.launcher.feature.settings.components.controls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anda.launcher.feature.settings.components.Setting
import com.anda.launcher.feature.settings.components.layout.SettingFrame
import com.anda.launcher.feature.settings.components.layout.SettingTitle
import com.anda.launcher.feature.settings.components.layout.Summary

@Composable
internal fun SwitchRow(title: String, setting: Setting<Boolean>, summary: String? = null, enabled: Boolean = true) =
    SwitchRow(title, setting.value, summary, enabled, setting.modified, setting::reset, setting::set)

@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    summary: String? = null,
    enabled: Boolean = true,
    modified: Boolean = false,
    onReset: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    SettingFrame(title, modified, onReset, onClick = { onChange(!checked) }, enabled = enabled) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                SettingTitle(title, modified)
                if (summary != null) Summary(summary)
            }
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        }
    }
}
