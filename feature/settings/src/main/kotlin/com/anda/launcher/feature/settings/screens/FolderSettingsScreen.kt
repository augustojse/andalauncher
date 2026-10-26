package com.anda.launcher.feature.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.FolderOpenStyle
import com.anda.launcher.core.model.FolderPreview
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.feature.home.FolderPreviewSwatch
import com.anda.launcher.feature.settings.components.SettingsBinder
import com.anda.launcher.feature.settings.components.controls.ColorRow
import com.anda.launcher.feature.settings.components.controls.FloatSliderRow
import com.anda.launcher.feature.settings.components.controls.SegmentedRow
import com.anda.launcher.feature.settings.components.controls.SliderRow
import com.anda.launcher.feature.settings.components.controls.SwitchRow
import com.anda.launcher.feature.settings.components.controls.VisualChoiceRow
import com.anda.launcher.feature.settings.components.layout.AdvancedSection
import com.anda.launcher.feature.settings.components.layout.SettingsGroup

@Composable
internal fun FolderSettingsScreen(b: SettingsBinder) {
    val graph = LocalLauncherGraph.current
    val apps by graph.appsRepository.apps.collectAsState()
    SettingsGroup {
        VisualChoiceRow("Preview", b.bind({ it.folders.preview }) { copy(folders = folders.copy(preview = it)) }, FolderPreview.entries) { preview ->
            FolderPreviewSwatch(preview, apps, 48.dp)
        }
        SegmentedRow("Open as", b.bind({ it.folders.openStyle }) { copy(folders = folders.copy(openStyle = it)) }, FolderOpenStyle.entries)
        SliderRow("Columns", b.bind({ it.folders.columns }) { copy(folders = folders.copy(columns = it)) }, 3..6)
    }
    AdvancedSection("folders", 4) {
        SwitchRow("Icon background", b.bind({ it.folders.showIconBackground }) { copy(folders = folders.copy(showIconBackground = it)) })
        ColorRow("Background color", b.bind({ it.folders.backgroundColor }) { copy(folders = folders.copy(backgroundColor = it)) })
        FloatSliderRow("Background opacity", b.bind({ it.folders.backgroundOpacity }) { copy(folders = folders.copy(backgroundOpacity = it)) })
        SwitchRow("Blur behind folders", b.bind({ it.folders.blur }) { copy(folders = folders.copy(blur = it)) })
    }
    IconStyleGroups("Folder icons", "folder", b, { it.icons.folder }) { copy(icons = icons.copy(folder = it)) }
}
