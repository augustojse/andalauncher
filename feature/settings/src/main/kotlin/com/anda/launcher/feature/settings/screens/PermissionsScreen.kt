package com.anda.launcher.feature.settings.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.anda.launcher.core.ui.SystemSettingsIntent
import com.anda.launcher.core.ui.startActivitySafely
import com.anda.launcher.feature.badges.BadgeRepository
import com.anda.launcher.feature.gestures.SystemActions
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.layout.Hint
import com.anda.launcher.feature.settings.components.layout.SettingsGroup
import kotlinx.coroutines.launch

@Composable
internal fun PermissionsScreen() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val roleManager = context.getSystemService(RoleManager::class.java)
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    // Re-check every time the user comes back from a system settings page.
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val isHome = remember(refresh) { roleManager.isRoleHeld(RoleManager.ROLE_HOME) }
    val accessibility = remember(refresh) { SystemActions.isAccessibilityEnabled }
    val notifications = remember(refresh) { BadgeRepository.isAccessGranted(context) }
    val contacts = remember(refresh) { context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED }
    SettingsGroup {
        StatusRow("Default home app", isHome, "Required for app shortcuts and pinning") {
            runCatching { roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                .onFailure { context.startActivitySafely(Intent(android.provider.Settings.ACTION_HOME_SETTINGS), SystemSettingsIntent) }
        }
        StatusRow("Gesture service", accessibility, "Lock screen, recents, screenshot, power menu") {
            context.startActivitySafely(SystemActions.accessibilitySettingsIntent(context), SystemSettingsIntent)
        }
        StatusRow("Notification access", notifications, "Notification dots") {
            context.startActivitySafely(BadgeRepository.accessSettingsIntent(context), BadgeRepository.accessSettingsFallback, SystemSettingsIntent)
        }
        StatusRow("Contacts", contacts, "Contact search (optional)") {
            context.startActivitySafely(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)),
                SystemSettingsIntent,
            )
        }
    }
    Hint("Anda Launcher has no internet permission: nothing you do here leaves your phone.")
}

@Composable
private fun StatusRow(title: String, granted: Boolean, summary: String, onClick: () -> Unit) {
    NavRow(title, (if (granted) "✓ Enabled · " else "Off · ") + summary, null, onClick)
}
