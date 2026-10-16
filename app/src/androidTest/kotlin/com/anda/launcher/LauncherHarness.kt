package com.anda.launcher

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.anda.launcher.core.data.db.ScreenEntity
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.FolderItem
import com.anda.launcher.core.model.GridSpec
import com.anda.launcher.core.model.HomeItem
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.model.WidgetItem
import com.anda.launcher.core.model.WidgetStackItem
import com.anda.launcher.core.ui.TestTags
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Drives the real HOME activity for instrumented tests: wakes the device, makes Anda the home app,
 * (re)creates [MainActivity] under the compose test rule and resets the launcher to a known state
 * (default settings, one empty page, no dock). Destructive for the device's launcher layout, so
 * run it on an emulator.
 */
class LauncherHarness(private val compose: ComposeTestRule) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)
    lateinit var activity: MainActivity
        private set

    val graph: AppGraph get() = (activity.application as LauncherApplication).appGraph
    val spec: GridSpec get() = activity.home.gridSpec

    fun start() {
        shell("input keyevent KEYCODE_WAKEUP")
        shell("wm dismiss-keyguard")
        dismissSystemAnrDialogs()
        shell("cmd role add-role-holder android.app.role.HOME $PACKAGE 0")
        launchFreshActivity()
        resetLauncherState()
    }

    /**
     * A freshly booted (often headless, slow) emulator can leave a "System UI isn't responding"
     * dialog on top. It hides the dialogs and views these tests look for, so tap "Wait" first.
     * The ANR belongs to another app; Anda's own ANRs are caught by the monkey runs, not here.
     */
    private fun dismissSystemAnrDialogs() {
        repeat(3) {
            val wait = device.findObject(By.res("android", "aerr_wait")) ?: return
            wait.click()
            device.waitForIdle()
        }
    }

    fun shell(command: String): String = device.executeShellCommand(command)

    // ---- Activity ------------------------------------------------------------------------------

    /**
     * The system may already have started HOME (and so MainActivity) before this test's compose
     * rule was active; such a composition is invisible to the rule, so it is recreated.
     */
    private fun launchFreshActivity() {
        val existing = mainActivities().firstOrNull()
        if (existing != null) instrumentation.runOnMainSync { existing.recreate() }
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClassName(PACKAGE, MainActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        instrumentation.targetContext.startActivity(intent)
        activity = waitFor("a resumed MainActivity") {
            mainActivities(Stage.RESUMED).firstOrNull { it !== existing && !it.isFinishing }
        }
        // The first frame is held until settings and the icon spec are loaded.
        waitFor("the home grid to compose") { exists(hasTestTag(TestTags.WORKSPACE_GRID)).takeIf { it } }
    }

    private fun mainActivities(vararg stages: Stage = Stage.values().filter { it != Stage.DESTROYED }.toTypedArray()): List<MainActivity> {
        var result = emptyList<MainActivity>()
        instrumentation.runOnMainSync {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            result = stages.flatMap { registry.getActivitiesInStage(it) }.filterIsInstance<MainActivity>().distinct()
        }
        return result
    }

    // ---- State ---------------------------------------------------------------------------------

    private fun resetLauncherState() {
        val graph = graph
        // First run after install: let the default layout land so it can't race the reset.
        waitFor("the first-run default layout") { runBlocking { !graph.layoutRepository.isEmpty() }.takeIf { it } }
        runBlocking {
            // Defaults, minus the first-run welcome card (it would cover the dock area).
            val clean = LauncherSettings(onboardingDone = true)
            graph.settingsRepository.update { clean }
            graph.settingsRepository.settings.filterNotNull().first { it == clean }
            activity.home.layout.value.allItems().filterIsInstance<WidgetItem>().forEach { graph.widgetHost.deleteId(it.appWidgetId) }
            graph.layoutRepository.replaceAll(listOf(ScreenEntity(id = SCREEN, rank = 0)), emptyList())
        }
        onMain {
            activity.closeTransientUi()
            activity.drawer.close(animate = false)
        }
        awaitLayout { it.screens == listOf(SCREEN) && it.itemsOn(SCREEN).isEmpty() && it.dock.isEmpty() }
        compose.waitForIdle()
    }

    fun awaitLayout(description: String = "layout condition", condition: (com.anda.launcher.core.model.HomeLayout) -> Boolean) {
        waitFor(description) { activity.home.layout.value.takeIf(condition) }
        compose.waitForIdle()
    }

    /** Launchable apps in drawer order (alphabetical), excluding Anda itself. */
    fun apps(): List<AppInfo> = graph.appsRepository.apps.value.filter { !it.isHidden && !it.isWorkProfile && it.key.packageName != PACKAGE }

    fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    // ---- Geometry ------------------------------------------------------------------------------

    fun gridBounds(): Rect = compose.onNode(hasTestTag(TestTags.WORKSPACE_GRID)).fetchSemanticsNode().boundsInRoot

    /** Root-space center of a whole cell on the (single) home page. */
    fun cellCenter(cellX: Int, cellY: Int): Offset {
        val bounds = gridBounds()
        val cellW = bounds.width / spec.columns
        val cellH = bounds.height / spec.rows
        return Offset(bounds.left + (cellX + 0.5f) * cellW, bounds.top + (cellY + 0.5f) * cellH)
    }

    fun SemanticsNodeInteraction.centerInRoot(): Offset = fetchSemanticsNode().boundsInRoot.center

    // ---- Gestures ------------------------------------------------------------------------------

    /**
     * Long-press at [from], drag to [to] and release, like a finger. Split in two so the UI can
     * react to the drag start (the drawer gets out of the way) before the pointer moves on.
     */
    fun longPressDrag(from: Offset, to: Offset) {
        val root = compose.onNode(isRoot())
        val nudge = from + (to - from).let { d -> d / d.getDistance().coerceAtLeast(1f) * 60f }
        root.performTouchInput {
            down(from)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 300)
            moveTo(nudge)
        }
        compose.waitForIdle()
        try {
            waitFor("drag to start") { activity.drag.isDragging.takeIf { it } }
            compose.waitUntil(LONG_TIMEOUT) { activity.drawer.progress.value == 0f }
        } catch (e: Throwable) {
            // Never leave the injected finger down: every later touch would fail with
            // "a gesture is already in progress".
            releasePointer()
            throw e
        }
        root.performTouchInput {
            val steps = 12
            for (i in 1..steps) moveTo(nudge + (to - nudge) * (i / steps.toFloat()))
            moveTo(to)
            up()
        }
        compose.waitForIdle()
    }

    /** Lifts an injected pointer left down by a failed gesture (no-op when none is down). */
    fun releasePointer() {
        runCatching { compose.onNode(isRoot()).performTouchInput { cancel() } }
        compose.waitForIdle()
    }

    /**
     * Waits until the soft keyboard has finished appearing or hiding. While it animates, the
     * drawer's content slides (imePadding), so a press computed from an earlier position lands on
     * a moving target and the gesture reads as a move instead of a long-press.
     */
    fun awaitKeyboardSettled() {
        var last: Int? = null
        var stableSince = 0L
        waitFor("the keyboard to settle") {
            var bottom = 0
            onMain {
                bottom = activity.window.decorView.rootWindowInsets
                    ?.getInsets(android.view.WindowInsets.Type.ime())?.bottom ?: 0
            }
            val now = System.currentTimeMillis()
            if (bottom != last) {
                last = bottom
                stableSince = now
            }
            (now - stableSince >= 600).takeIf { it }
        }
        compose.waitForIdle()
    }

    fun longPress(at: Offset) {
        compose.onNode(isRoot()).performTouchInput {
            down(at)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 300)
            up()
        }
        compose.waitForIdle()
    }

    fun tap(at: Offset) {
        compose.onNode(isRoot()).performTouchInput { click(at) }
        compose.waitForIdle()
    }

    // ---- Matchers ------------------------------------------------------------------------------

    fun onHome(matcher: SemanticsMatcher): SemanticsMatcher = matcher and hasAnyAncestor(hasTestTag(TestTags.WORKSPACE_GRID))

    fun onDrawer(matcher: SemanticsMatcher): SemanticsMatcher = matcher and hasAnyAncestor(hasTestTag(TestTags.APP_DRAWER))

    fun homeIcon(label: String) = onHome(hasContentDescription(label))

    fun drawerIcon(label: String) = onDrawer(hasContentDescription(label))

    // ---- Waiting -------------------------------------------------------------------------------

    /** True if a node matches; false (not an exception) while no compose hierarchy exists yet. */
    fun exists(matcher: SemanticsMatcher): Boolean =
        runCatching { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)

    fun waitForNode(what: String, matcher: SemanticsMatcher, timeoutMs: Long = LONG_TIMEOUT) {
        waitFor(what, timeoutMs) { exists(matcher).takeIf { it } }
        compose.waitForIdle()
    }

    /** Polls [probe] off the main thread (real time) until it returns non-null. */
    fun <T : Any> waitFor(what: String, timeoutMs: Long = LONG_TIMEOUT, probe: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            probe()?.let { return it }
            if (System.currentTimeMillis() > deadline) throw AssertionError("Timed out after ${timeoutMs}ms waiting for $what")
            Thread.sleep(POLL_MS)
        }
    }

    companion object {
        const val PACKAGE = "com.anda.launcher"
        const val SCREEN = 1L
        const val LONG_TIMEOUT = 20_000L
        private const val POLL_MS = 100L
    }
}

/** Every item on every page, including folder and stack contents. */
fun com.anda.launcher.core.model.HomeLayout.allItems(): List<HomeItem> {
    fun flatten(item: HomeItem): List<HomeItem> = when (item) {
        is FolderItem -> listOf(item) + item.children.flatMap(::flatten)
        is WidgetStackItem -> listOf(item) + item.widgets.flatMap(::flatten)
        else -> listOf(item)
    }
    return (screens.flatMap { itemsOn(it) } + dock).flatMap(::flatten)
}

/** Reads a node's content description (first entry). */
val androidx.compose.ui.semantics.SemanticsNode.contentDescription: String?
    get() = config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
