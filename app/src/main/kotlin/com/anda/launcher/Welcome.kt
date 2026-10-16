package com.anda.launcher

import android.app.role.RoleManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.SystemSettingsIntent
import com.anda.launcher.core.ui.startActivitySafely
import com.anda.launcher.feature.home.HomeController

private data class GridPreset(val label: String, val columns: Int, val rows: Int)

private val presets = listOf(
    GridPreset("Roomy 4×5", 4, 5),
    GridPreset("Balanced 5×6", 5, 6),
    GridPreset("Dense 6×7", 6, 7),
)

/**
 * First-run card: make Anda the home app, pick a grid density (applied live behind the card), then
 * customize or start using it. Shown once, until `onboardingDone`.
 */
@Composable
internal fun WelcomeCard(settings: LauncherSettings, home: HomeController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    var roleCheck by remember { mutableIntStateOf(0) }
    val isHome = remember(roleCheck) { roleManager.isRoleHeld(RoleManager.ROLE_HOME) }
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { roleCheck++ }
    val finish: () -> Unit = { home.updateSettings { it.copy(onboardingDone = true) } }

    Surface(
        modifier = modifier.navigationBarsPadding().padding(12.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Welcome to Anda", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pick a grid to start with. You can change everything later: long-press empty space and tap Customize.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    FilterChip(
                        selected = settings.home.columns == p.columns && settings.home.rows == p.rows,
                        onClick = {
                            home.updateSettings {
                                it.copy(home = it.home.copy(columns = p.columns, rows = p.rows), dock = it.dock.copy(columns = p.columns))
                            }
                        },
                        label = { Text(p.label) },
                    )
                }
            }
            if (!isHome) {
                FilledTonalButton(
                    onClick = {
                        runCatching { requestRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                            .onFailure { context.startActivitySafely(Intent(Settings.ACTION_HOME_SETTINGS), SystemSettingsIntent) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Set Anda as your home app")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    finish()
                    home.openCustomize()
                }) { Text("Customize more") }
                Button(onClick = finish, modifier = Modifier.padding(start = 8.dp)) { Text("Done") }
            }
        }
    }
}
