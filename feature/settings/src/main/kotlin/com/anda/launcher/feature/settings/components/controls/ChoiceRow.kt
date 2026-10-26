package com.anda.launcher.feature.settings.components.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anda.launcher.feature.settings.components.Setting
import com.anda.launcher.feature.settings.components.defaultLabel
import com.anda.launcher.feature.settings.components.layout.SettingFrame
import com.anda.launcher.feature.settings.components.layout.SettingTitle
import com.anda.launcher.feature.settings.components.layout.Summary

/** Long option lists: a dialog of radio buttons. */
@Composable
internal fun <T> ChoiceRow(title: String, setting: Setting<T>, options: List<T>, label: (T) -> String = ::defaultLabel) =
    ChoiceRow(title, setting.value, options, label, setting.modified, setting::reset, setting::set)

@Composable
internal fun <T> ChoiceRow(
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String = ::defaultLabel,
    modified: Boolean = false,
    onReset: (() -> Unit)? = null,
    onChange: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingFrame(title, modified, onReset, onClick = { open = true }) {
        SettingTitle(title, modified)
        Summary(label(value))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onChange(option); open = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == value, onClick = { onChange(option); open = false })
                            Text(label(option))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}

/** 2–4 options: inline segmented buttons, one tap instead of a dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SegmentedRow(title: String, setting: Setting<T>, options: List<T>, label: (T) -> String = ::defaultLabel) {
    SettingFrame(title, setting.modified, setting::reset, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        SettingTitle(title, setting.modified)
        Spacer(Modifier.size(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == setting.value,
                    onClick = { setting.set(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    label = { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }
    }
}

/**
 * Options that are visual (icon shape, folder preview, dock shape): a row of rendered swatches,
 * so the choice looks like the result instead of a word.
 */
@Composable
internal fun <T> VisualChoiceRow(
    title: String,
    setting: Setting<T>,
    options: List<T>,
    label: (T) -> String = ::defaultLabel,
    swatch: @Composable (option: T) -> Unit,
) {
    SettingFrame(title, setting.modified, setting::reset, contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)) {
        SettingTitle(title, setting.modified, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.size(10.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(options) { option ->
                val selected = option == setting.value
                Column(
                    Modifier.clip(MaterialTheme.shapes.medium)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.medium,
                        )
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .clickable { setting.set(option) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { swatch(option) }
                    Text(label(option), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}
