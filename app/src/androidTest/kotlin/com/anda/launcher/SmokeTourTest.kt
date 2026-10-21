package com.anda.launcher

import android.content.Intent
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anda.launcher.core.model.AppItem
import com.anda.launcher.core.model.Containers
import com.anda.launcher.core.model.FolderItem
import com.anda.launcher.core.ui.TestTags
import com.anda.launcher.feature.settings.SettingsActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The daily smoke test: one pass over every main surface in a single launcher session (about a
 * minute), instead of a fresh harness per feature. Each step is recorded on its own, so one
 * broken area doesn't hide the others; the test fails at the end listing every failed step.
 *
 * Run alone with `tools/qa/run-qa.ps1 -Tier smoke`. Each step's result goes to `smoke-steps.txt`
 * in the instrumentation's additional output (copied back by Gradle) and to logcat (tag
 * AndaSmoke); the runner puts the file in the report.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTourTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val launcher = LauncherHarness(compose)
    private val failures = mutableListOf<String>()
    private val tourStart = System.currentTimeMillis()

    private val drawerSearch = hasSetTextAction() and hasAnyAncestor(hasTestTag(TestTags.APP_DRAWER))
    private val inResults = hasAnyAncestor(hasTestTag(TestTags.SEARCH_RESULTS))

    @Before
    fun setUp() = launcher.start()

    @Test
    fun tour() = with(launcher) {
        step("home renders grid and search bar") {
            check(exists(hasTestTag(TestTags.WORKSPACE_GRID))) { "no workspace grid" }
            waitForNode("search pill", hasText("Search") and hasClickAction())
        }

        step("drawer opens and lists apps") {
            onMain { activity.drawer.open() }
            compose.waitUntil(LauncherHarness.LONG_TIMEOUT) { activity.drawer.progress.value == 1f }
            val app = apps().first()
            waitForNode("${app.label} in the drawer", drawerIcon(app.label))
        }

        step("search: calculator") {
            compose.onNode(drawerSearch).performTextInput("12*7")
            waitForNode("calculator result", hasText("= 84") and inResults)
        }

        step("search: drag a result onto home (stuck-drag regression)") {
            val app = apps().first { it.label.length >= 3 }
            compose.onNode(drawerSearch).performTextReplacement(app.label)
            val result = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(app.label)) and inResults
            waitForNode("${app.label} in the results", result)
            awaitKeyboardSettled()
            val from = compose.onNode(result).centerInRoot()
            longPressDrag(from, cellCenter(2, 3))
            awaitLayout("${app.label} dropped on home") { layout ->
                layout.itemsOn(LauncherHarness.SCREEN).any { it is AppItem && it.key == app.key }
            }
            waitFor<Boolean>("the drag to end") { (!activity.drag.isDragging).takeIf { it } }
        }

        step("folder: create by drop, open, close") {
            val (a, b) = apps().drop(1).take(2)
            runBlocking {
                graph.layoutRepository.addApp(a.key, Containers.DESKTOP, LauncherHarness.SCREEN, spec.cellRect(0, 1))
                graph.layoutRepository.addApp(b.key, Containers.DESKTOP, LauncherHarness.SCREEN, spec.cellRect(2, 1))
            }
            waitForNode("${a.label} on home", homeIcon(a.label))
            waitForNode("${b.label} on home", homeIcon(b.label))
            longPressDrag(cellCenter(0, 1), cellCenter(2, 1))
            awaitLayout("a folder") { layout -> layout.itemsOn(LauncherHarness.SCREEN).any { it is FolderItem } }
            tap(cellCenter(2, 1))
            waitForNode("open folder panel", hasTestTag(TestTags.FOLDER_PANEL))
            onMain { activity.closeTransientUi() }
            waitFor<Boolean>("folder closed") { (activity.home.openFolderId == null).takeIf { it } }
        }

        step("remove then undo") {
            val item = activity.home.layout.value.itemsOn(LauncherHarness.SCREEN).first()
            onMain { activity.home.remove(item) }
            awaitLayout("item removed") { layout -> layout.findItem(item.id) == null }
            waitForNode("undo bar", hasText("Undo") and hasClickAction())
            compose.onNode(hasText("Undo") and hasClickAction()).performClick()
            awaitLayout("item restored") { layout -> layout.findItem(item.id) != null }
        }

        step("edit mode and live Customize panel") {
            longPress(cellCenter(3, 5))
            waitForNode("edit bar", hasText("Customize") and hasClickAction())
            compose.onNode(hasText("Customize") and hasClickAction()).performClick()
            waitForNode("customize panel", hasText("Customize home"))
            onMain { activity.closeTransientUi() }
            waitFor<Boolean>("customize closed") { (!activity.home.customizeOpen).takeIf { it } }
        }

        step("widget picker opens with widgets") {
            longPress(cellCenter(3, 5))
            waitForNode("edit bar", hasText("Widgets") and hasClickAction())
            compose.onNode(hasText("Widgets") and hasClickAction()).performClick()
            waitForNode("widget picker", hasTestTag(TestTags.WIDGET_PICKER))
            onMain { activity.closeTransientUi() }
            waitFor<Boolean>("picker closed") { (!activity.home.widgetPickerOpen).takeIf { it } }
        }

        step("settings: main page, search, deep link with live preview") {
            onMain { activity.openSettings(null) }
            waitForNode("settings search field", hasTestTag(TestTags.SETTINGS_SEARCH))
            compose.onNode(hasTestTag(TestTags.SETTINGS_SEARCH)).performTextInput("subgrid")
            waitForNode("search result", hasText("Subgrid positioning") and hasClickAction())
            compose.onNode(hasText("Subgrid positioning") and hasClickAction()).performClick()
            waitForNode("home settings with preview", hasText("Try on home"))
            device.pressBack()
            device.pressBack()
            device.pressBack()
        }

        step("settings: every page opens") {
            for ((screen, title) in SETTINGS_PAGES) {
                val intent = Intent(instrumentation.targetContext, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_SCREEN, screen)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                instrumentation.targetContext.startActivity(intent)
                // The top bar shows the page title; a crash here kills the process and the run.
                waitForNode("settings page $screen", hasText(title), timeoutMs = 10_000)
            }
            device.pressBack()
        }

        step("back on home, nothing stuck") {
            instrumentation.targetContext.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClassName(LauncherHarness.PACKAGE, MainActivity::class.java.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            waitForNode("home grid", hasTestTag(TestTags.WORKSPACE_GRID))
            check(!activity.drag.isDragging) { "a drag is still active" }
            check(activity.drawer.progress.value == 0f) { "drawer not closed" }
        }

        step("no crash or error reports written during the tour") {
            val fresh = graph.crashReports.list().filter { it.lastModified() >= tourStart }
            check(fresh.isEmpty()) { "new reports: ${fresh.joinToString { it.name }}" }
        }

        if (failures.isNotEmpty()) fail("Smoke tour: ${failures.size} step(s) failed:\n" + failures.joinToString("\n"))
    }

    /** Runs one step; a failure is recorded and the tour moves on. */
    private fun step(name: String, block: () -> Unit) {
        val started = System.currentTimeMillis()
        try {
            block()
            record("PASS $name (${System.currentTimeMillis() - started} ms)")
            Log.i(TAG, "SMOKE PASS $name")
        } catch (e: Throwable) {
            val reason = (e.message ?: e.javaClass.simpleName).lineSequence().first()
            record("FAIL $name: $reason")
            Log.e(TAG, "SMOKE FAIL $name: $reason", e)
            failures += "$name: $reason"
            // Leave a clean home for the next step: no finger down, nothing open, no drag.
            launcher.releasePointer()
            runCatching { launcher.onMain { launcher.activity.closeTransientUi() } }
        }
    }

    /**
     * Appends a line to `smoke-steps.txt` in the instrumentation's additional output directory;
     * Gradle copies that folder to app/build/outputs/connected_android_test_additional_output.
     * Logcat alone isn't reliable for this: a busy emulator's ring buffer drops lines in minutes.
     */
    private fun record(line: String) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        runCatching { File(dir, "smoke-steps.txt").apply { parentFile?.mkdirs() }.appendText(line + "\n") }
    }

    private companion object {
        const val TAG = "AndaSmoke"
        /** Screen extra → top-bar title (feature/settings SettingsIndex.kt `Screen`). */
        val SETTINGS_PAGES = listOf(
            "Home" to "Home screen", "Dock" to "Dock", "Drawer" to "App drawer", "Icons" to "Icons",
            "Folders" to "Folders", "Gestures" to "Gestures", "Search" to "Search", "Badges" to "Notification dots",
            "Theme" to "Look & feel", "Glance" to "At a glance", "Hidden" to "Hidden apps",
            "Permissions" to "Permissions", "Backup" to "Backup & restore", "Dev" to "Dev", "About" to "About",
        )
    }
}
