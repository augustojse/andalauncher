package com.anda.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.AppOverride
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.DrawerGroup
import com.anda.launcher.core.model.Favorite
import com.anda.launcher.core.model.Gesture
import com.anda.launcher.core.model.HomeItem
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.SystemSettingsIntent
import com.anda.launcher.core.ui.startActivitySafely
import com.anda.launcher.core.ui.drag.DragController
import com.anda.launcher.core.ui.menu.OverlayController
import com.anda.launcher.feature.drawer.DrawerCallbacks
import com.anda.launcher.feature.drawer.DrawerController
import com.anda.launcher.feature.drawer.DrawerGroupDialog
import com.anda.launcher.feature.drawer.EditAppDialog
import com.anda.launcher.feature.gestures.ActionDispatcher
import com.anda.launcher.feature.gestures.LauncherNavigator
import com.anda.launcher.feature.gestures.SystemActions
import com.anda.launcher.feature.home.EXTRA_CUSTOMIZE
import com.anda.launcher.feature.home.EditHomeItemDialog
import com.anda.launcher.feature.home.HomeController
import com.anda.launcher.feature.home.HomeHost
import com.anda.launcher.feature.home.WidgetRequestHandler
import com.anda.launcher.feature.home.widget.WidgetHostManager
import com.anda.launcher.feature.search.SearchEngine
import com.anda.launcher.feature.search.SearchResult
import com.anda.launcher.feature.search.SearchResultActions
import com.anda.launcher.feature.search.WebSearch
import com.anda.launcher.feature.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The HOME activity. Owns the controllers (it is singleTask and handles config changes itself, so
 * it lives as long as the launcher process) and implements the activity-level hooks the
 * features need.
 */
class MainActivity :
    ComponentActivity(),
    LauncherNavigator,
    HomeHost,
    DrawerCallbacks,
    WidgetRequestHandler,
    SearchResultActions {

    private val graph: AppGraph get() = (application as LauncherApplication).appGraph
    internal val drag = DragController()
    internal val overlay = OverlayController()
    internal lateinit var dispatcher: ActionDispatcher
    internal lateinit var home: HomeController
    internal lateinit var drawer: DrawerController
    internal lateinit var searchEngine: SearchEngine
    private var pendingBindId = -1

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingBindId) ?: pendingBindId
        if (result.resultCode == Activity.RESULT_OK) home.onWidgetBound(id) else home.cancelWidget(id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        // stateNotNeeded: never restore stale UI state into a launcher.
        super.onCreate(null)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        dispatcher = ActionDispatcher(this, graph.appLauncher, this)
        // Activity-bound, but a failed layout write is logged instead of taking the launcher down.
        val controllerScope = CoroutineScope(lifecycleScope.coroutineContext + graph.recoverErrors)
        home = HomeController(applicationContext, graph, graph.widgetHost, drag, overlay, controllerScope, dispatcher, this)
        home.safeMode = graph.crashGuard.safeMode
        drawer = DrawerController(graph, overlay, drag, controllerScope, this)
        searchEngine = SearchEngine.create(applicationContext, graph)
        setContent { LauncherRoot(this, graph) }
        if (intent?.getBooleanExtra(EXTRA_CUSTOMIZE, false) == true) home.openCustomize()
    }

    override fun onStart() {
        super.onStart()
        graph.widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        graph.widgetHost.stopListening()
        // Leaving home (app launched, screen off) mid-drag: never come back to a frozen drag icon.
        if (drag.isDragging) drag.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        graph.widgetHost.clearViews()
    }

    /** Home button while already on home: close what's open, else run the HomePress gesture. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != Intent.ACTION_MAIN) return
        if (intent.getBooleanExtra(EXTRA_CUSTOMIZE, false)) {
            // "Try it on your home screen" from a settings preview.
            closeTransientUi()
            home.openCustomize()
            return
        }
        val alreadyOnHome = hasWindowFocus() &&
            (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        val closedSomething = closeTransientUi()
        if (alreadyOnHome && !closedSomething) {
            val action = graph.settingsRepository.settings.value?.gestures?.actionFor(Gesture.HomePress)
            if (action != null) dispatcher.dispatch(action)
        }
    }

    /** Returns true if anything was dismissed. */
    internal fun closeTransientUi(): Boolean {
        var closed = false
        if (overlay.menu != null) { overlay.dismissMenu(); closed = true }
        if (overlay.dialog != null) { overlay.dismissDialog(); closed = true }
        if (home.openFolderId != null) { home.closeFolder(); closed = true }
        if (home.customizeOpen) { home.closeCustomize(); closed = true }
        if (home.widgetPickerOpen) { home.widgetPickerOpen = false; closed = true }
        if (home.resizingItemId != null) { home.resizingItemId = null; closed = true }
        if (home.editMode) { home.editMode = false; closed = true }
        if (drawer.isOpen || drawer.progress.value > 0f) { drawer.close(); closed = true }
        if (drag.isDragging) drag.cancel()
        return closed
    }

    @Deprecated("Widget configuration still requires startActivityForResult via AppWidgetHost")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WidgetHostManager.REQUEST_CONFIGURE) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingBindId) ?: pendingBindId
            home.onWidgetConfigured(id, resultCode == Activity.RESULT_OK)
        }
    }

    // ---- Window --------------------------------------------------------------------------------

    /** Blurs the wallpaper behind the launcher window (drawer/folder open). */
    internal fun setWallpaperBlur(radiusPx: Int) {
        if (!windowManager.isCrossWindowBlurEnabled) return
        val attrs = window.attributes
        if (radiusPx <= 0) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            attrs.blurBehindRadius = radiusPx
            window.attributes = attrs
        }
    }

    internal fun applySystemBars(settings: LauncherSettings, lightIcons: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !lightIcons
        controller.isAppearanceLightNavigationBars = !lightIcons
        if (settings.home.showStatusBar) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    // ---- LauncherNavigator ---------------------------------------------------------------------

    override fun openDrawer() = drawer.open()

    override fun openSearch() = drawer.open(focusSearch = true)

    override fun openSettings() = openSettings(null)

    /**
     * Settings runs in its own task (see its `taskAffinity` in the manifest): inside the home
     * task it was wiped on every Home press (`clearTaskOnLaunch`) and never showed in Recents.
     */
    override fun openSettings(screen: String?) = openSettings(screen, iconApp = null)

    private fun openSettings(screen: String?, iconApp: ComponentKey?) {
        closeTransientUi()
        val intent = Intent(this, SettingsActivity::class.java)
            .putExtra(SettingsActivity.EXTRA_SCREEN, screen)
            .putExtra(SettingsActivity.EXTRA_ICON_APP, iconApp?.flatten())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // A deep link (e.g. "Folder settings") must land on that page, not wherever an existing
        // settings task was left; a plain "Settings" just brings the existing task back.
        if (screen != null) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    override fun enterEditMode() {
        home.editMode = true
    }

    override fun toggleDock() {
        home.dockHidden = !home.dockHidden
    }

    override fun goToDefaultPage() = home.goToDefaultPage()

    override fun requestAccessibility() {
        overlay.showDialog {
            AlertDialog(
                onDismissRequest = overlay::dismissDialog,
                title = { Text("Enable Anda gestures") },
                text = {
                    Text(
                        "Locking the screen, recents, screenshots and the power menu need the Anda Launcher " +
                            "accessibility service. It only performs these actions and never reads the screen.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        overlay.dismissDialog()
                        startActivitySafely(SystemActions.accessibilitySettingsIntent(this), SystemSettingsIntent)
                    }) { Text("Open settings") }
                },
                dismissButton = { TextButton(onClick = overlay::dismissDialog) { Text("Not now") } },
            )
        }
    }

    // ---- HomeHost ------------------------------------------------------------------------------

    override fun openVoiceSearch() {
        graph.appLauncher.startSafely(WebSearch.voiceIntent())
    }

    override fun openLens() {
        if (!graph.appLauncher.startSafely(WebSearch.lensIntent())) openSearch()
    }

    override fun openWallpaperPicker() {
        home.editMode = false
        graph.appLauncher.startSafely(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
    }

    override fun editItem(item: HomeItem) {
        overlay.showDialog {
            val apps by graph.appsRepository.apps.collectAsState()
            EditHomeItemDialog(item, apps, home, overlay::dismissDialog) { app ->
                overlay.dismissDialog()
                openSettings(SettingsActivity.SCREEN_ICON_EDITOR, iconApp = app.key)
            }
        }
    }

    // ---- DrawerCallbacks -----------------------------------------------------------------------

    override fun addToHome(app: AppInfo) {
        home.addToHome(app)
        drawer.close()
    }

    override fun authenticate(reason: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt.Builder(this)
            .setTitle(reason)
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(
            CancellationSignal(),
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
            },
        )
    }

    override fun editApp(app: AppInfo) {
        lifecycleScope.launch {
            val current = graph.overridesRepository.overrides.first()[app.key] ?: AppOverride(app.key)
            overlay.showDialog {
                val apps by graph.appsRepository.apps.collectAsState()
                EditAppDialog(
                    app = app,
                    current = current,
                    apps = apps,
                    onSave = { next -> lifecycleScope.launch { graph.overridesRepository.update(app.key) { next } } },
                    onDismiss = overlay::dismissDialog,
                    onEditIcon = {
                        overlay.dismissDialog()
                        openSettings(SettingsActivity.SCREEN_ICON_EDITOR, iconApp = app.key)
                    },
                )
            }
        }
    }

    override fun editGroup(group: DrawerGroup?) {
        overlay.showDialog {
            val apps by graph.appsRepository.apps.collectAsState()
            DrawerGroupDialog(
                group = group,
                apps = apps,
                onSave = { name, isFolder, members ->
                    lifecycleScope.launch {
                        val repo = graph.drawerGroupsRepository
                        if (group == null) {
                            repo.create(name, isFolder, members)
                        } else {
                            repo.rename(group.id, name)
                            repo.setMembers(group.id, members)
                        }
                    }
                },
                onDelete = group?.let { g -> { lifecycleScope.launch { graph.drawerGroupsRepository.delete(g.id) } } },
                onDismiss = overlay::dismissDialog,
            )
        }
    }

    // ---- WidgetRequestHandler ------------------------------------------------------------------

    override fun requestBind(intent: Intent) {
        pendingBindId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        try {
            bindWidget.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // No bind-permission dialog on this build: give the id back instead of leaking it.
            home.cancelWidget(pendingBindId)
            Toast.makeText(this, "This widget can't be added on your phone", Toast.LENGTH_SHORT).show()
        }
    }

    override fun requestConfigure(appWidgetId: Int) {
        pendingBindId = appWidgetId
        if (!graph.widgetHost.startConfigure(this, appWidgetId, WidgetHostManager.REQUEST_CONFIGURE)) {
            home.onWidgetConfigured(appWidgetId, ok = true)
        }
    }

    // ---- SearchResultActions -------------------------------------------------------------------

    override fun openIntent(intent: Intent) {
        if (graph.appLauncher.startSafely(intent)) drawer.close(animate = false)
    }

    override fun openShortcut(result: SearchResult.Shortcut) {
        graph.appLauncher.launchShortcut(result.packageName, result.shortcutId, result.user)
        drawer.close(animate = false)
    }

    override fun setPinned(favorite: Favorite, pinned: Boolean) {
        lifecycleScope.launch {
            val favorites = graph.favoritesRepository
            if (!pinned) {
                favorites.unpin(favorite.key)
            } else if (!favorites.pin(favorite)) {
                Toast.makeText(this@MainActivity, "Favorites are full. Remove one to pin another.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun copyToClipboard(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Anda", text))
        Toast.makeText(this, "Copied $text", Toast.LENGTH_SHORT).show()
    }
}
