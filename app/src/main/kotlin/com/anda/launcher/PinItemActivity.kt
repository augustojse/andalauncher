package com.anda.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.Containers
import com.anda.launcher.core.model.GridSpec
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.ui.theme.LauncherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles `requestPinShortcut` / `requestPinAppWidget` from other apps
 * ("Add to home screen" buttons), following Launcher3's AddItemActivity flow.
 */
class PinItemActivity : ComponentActivity() {
    private val graph: AppGraph get() = (application as LauncherApplication).appGraph
    private var request: LauncherApps.PinItemRequest? = null
    private var pendingWidgetId = -1

    /** "Add" may be tapped twice before the dialog goes away; only the first one counts. */
    private var accepting = false

    private val bind = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingWidgetId >= 0) acceptWidget(pendingWidgetId) else cancel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Survives rotation during the system bind dialog; otherwise the bound id would leak.
        pendingWidgetId = savedInstanceState?.getInt(KEY_PENDING_ID, -1) ?: -1
        accepting = pendingWidgetId >= 0
        val req = getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        if (req == null || !req.isValid) {
            finish()
            return
        }
        request = req
        val label = when (req.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> req.shortcutInfo?.shortLabel?.toString()
            else -> req.getAppWidgetProviderInfo(this)?.loadLabel(packageManager)
        } ?: "this item"
        setContent {
            val settings by graph.settingsRepository.settings.collectAsState()
            LauncherTheme((settings ?: LauncherSettings()).theme) {
                AlertDialog(
                    onDismissRequest = ::cancel,
                    title = { Text("Add to home screen?") },
                    text = { Text(label) },
                    confirmButton = { TextButton(onClick = ::accept) { Text("Add") } },
                    dismissButton = { TextButton(onClick = ::cancel) { Text("Cancel") } },
                )
            }
        }
    }

    private fun spec(): GridSpec {
        val s = graph.settingsRepository.settings.value ?: LauncherSettings()
        return GridSpec(s.home.columns, s.home.rows)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PENDING_ID, pendingWidgetId)
    }

    private fun accept() {
        if (accepting) return
        accepting = true
        val req = request ?: return finish()
        when (req.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                val info = req.shortcutInfo ?: return cancel()
                if (!req.accept()) return cancel()
                lifecycleScope.launch {
                    val (screen, rect) = graph.layoutRepository.findSpace(spec(), 1, 1)
                    graph.layoutRepository.addShortcut(
                        info.`package`, info.id, graph.users.serialOf(info.userHandle),
                        info.shortLabel?.toString().orEmpty(), Containers.DESKTOP, screen, rect,
                    )
                    finish()
                }
            }
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> {
                val info = req.getAppWidgetProviderInfo(this) ?: return cancel()
                val id = graph.widgetHost.allocateId()
                pendingWidgetId = id
                if (graph.widgetHost.bindIfAllowed(id, info)) {
                    acceptWidget(id)
                } else {
                    try {
                        bind.launch(graph.widgetHost.bindIntent(id, info))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, "This widget can't be added on your phone", Toast.LENGTH_SHORT).show()
                        cancel()
                    }
                }
            }
            else -> cancel()
        }
    }

    private fun acceptWidget(id: Int) {
        val req = request ?: return cancel()
        val info = req.getAppWidgetProviderInfo(this) ?: return cancel()
        if (!req.accept(Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, id) })) {
            graph.widgetHost.deleteId(id)
            return cancel()
        }
        lifecycleScope.launch {
            val spec = spec()
            val (spanX, spanY) = withContext(Dispatchers.Default) {
                graph.widgetHost.defaultSpan(info, spec.columns, spec.rows, cellWidthDp = 80f, cellHeightDp = 100f)
            }
            val (screen, rect) = graph.layoutRepository.findSpace(spec, spanX, spanY)
            val provider = ComponentKey(info.provider.packageName, info.provider.className, graph.users.serialOf(info.profile))
            graph.layoutRepository.addWidget(id, provider, screen, rect)
            finish()
        }
    }

    private fun cancel() {
        if (pendingWidgetId >= 0) graph.widgetHost.deleteId(pendingWidgetId)
        pendingWidgetId = -1
        finish()
    }

    private companion object {
        const val KEY_PENDING_ID = "pendingWidgetId"
    }
}
