package com.anda.launcher

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anda.launcher.core.model.AppItem
import com.anda.launcher.core.model.Containers
import com.anda.launcher.core.model.FolderItem
import com.anda.launcher.core.ui.TestTags
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class HomeDragAndDropTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val launcher = LauncherHarness(compose)

    @Before
    fun setUp() = launcher.start()

    @Test
    fun dragAppFromDrawerOntoEmptyHomeCell() = with(launcher) {
        val app = apps().first()
        onMain { activity.drawer.open() }
        compose.waitUntil(LauncherHarness.LONG_TIMEOUT) { activity.drawer.progress.value == 1f }
        waitForNode("${app.label} in the drawer", drawerIcon(app.label))
        val from = compose.onNode(drawerIcon(app.label)).centerInRoot()

        // Middle of the page: clear of the drop-target bar (top) and the page edges (edge scroll).
        val cellX = 2
        val cellY = 3
        val target = cellCenter(cellX, cellY)
        longPressDrag(from, target)

        awaitLayout("${app.label} placed at ($cellX, $cellY)") { layout ->
            layout.itemsOn(LauncherHarness.SCREEN).any { it is AppItem && it.key == app.key && it.rect == spec.cellRect(cellX, cellY) }
        }
        assertEquals(0f, activity.drawer.progress.value)
        waitForNode("${app.label} on the home grid", homeIcon(app.label))
        val icon = compose.onNode(homeIcon(app.label)).fetchSemanticsNode().boundsInRoot
        assertTrue("icon at $icon should cover the cell center $target", icon.contains(target))
    }

    @Test
    fun dropIconOnIconCreatesFolderThatCanBeRenamed() = with(launcher) {
        val (first, second) = apps().take(2)
        runBlocking {
            graph.layoutRepository.addApp(first.key, Containers.DESKTOP, LauncherHarness.SCREEN, spec.cellRect(1, 3))
            graph.layoutRepository.addApp(second.key, Containers.DESKTOP, LauncherHarness.SCREEN, spec.cellRect(3, 3))
        }
        waitForNode("${first.label} on home", homeIcon(first.label))
        waitForNode("${second.label} on home", homeIcon(second.label))

        longPressDrag(cellCenter(1, 3), cellCenter(3, 3))

        awaitLayout("a folder replacing ${second.label}") { layout ->
            layout.itemsOn(LauncherHarness.SCREEN).singleOrNull() is FolderItem
        }
        val folder = activity.home.layout.value.itemsOn(LauncherHarness.SCREEN).single() as FolderItem
        assertEquals(spec.cellRect(3, 3), folder.rect)
        // The icon it was dropped on keeps the first slot (Launcher3 behaviour).
        assertEquals(listOf(second.key, first.key), folder.children.map { (it as AppItem).key })

        // Open it and rename it from the title field.
        tap(cellCenter(3, 3))
        waitForNode("the open folder", hasTestTag(TestTags.FOLDER_PANEL))
        val title = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(TestTags.FOLDER_PANEL)))
        title.performTextReplacement(NEW_NAME)
        title.performImeAction()
        awaitLayout("folder renamed to $NEW_NAME") { layout ->
            (layout.findItem(folder.id) as? FolderItem)?.title == NEW_NAME
        }

        onMain { activity.closeTransientUi() }
        compose.waitUntil(LauncherHarness.LONG_TIMEOUT) { activity.home.openFolderId == null }
        waitForNode("the renamed folder label on home", onHome(hasText(NEW_NAME)))
        val label = compose.onNode(onHome(hasText(NEW_NAME))).fetchSemanticsNode().boundsInRoot
        val halfCell = gridBounds().width / spec.columns / 2
        assertTrue("label should sit in the folder's column", abs(label.center.x - cellCenter(3, 3).x) < halfCell)
    }

    private companion object {
        const val NEW_NAME = "Renamed folder"
    }
}
