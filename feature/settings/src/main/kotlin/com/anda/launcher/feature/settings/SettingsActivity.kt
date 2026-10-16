package com.anda.launcher.feature.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.LauncherGraphOwner
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.core.ui.icon.rememberIconSpec
import com.anda.launcher.core.ui.theme.LauncherTheme
import com.anda.launcher.core.ui.theme.isLauncherDark

/** Launcher settings, in its own activity so the home task stays lean. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as LauncherGraphOwner).graph
        setContent {
            val settings by graph.settingsRepository.settings.collectAsState()
            val current = settings ?: LauncherSettings()
            CompositionLocalProvider(LocalLauncherGraph provides graph, LocalLauncherSettings provides current) {
                LauncherTheme(current.theme) {
                    // Home sits in the background while settings are open; keep icons current here
                    // so the previews show a new shape or pack straight away.
                    rememberIconSpec(graph.iconRepository, current, isLauncherDark(current.theme))
                    SettingsRoot(onExit = ::finish, initialScreen = intent.getStringExtra(EXTRA_SCREEN))
                }
            }
        }
    }

    companion object {
        const val EXTRA_SCREEN = "screen"
    }
}
