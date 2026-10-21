package com.anda.launcher

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.ui.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerSearchTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val launcher = LauncherHarness(compose)

    private val searchField = hasSetTextAction() and hasAnyAncestor(hasTestTag(TestTags.APP_DRAWER))
    private val inResults = hasAnyAncestor(hasTestTag(TestTags.SEARCH_RESULTS))

    @Before
    fun setUp() {
        launcher.start()
        // Home search pill → drawer with the search field focused.
        compose.onNode(hasText("Search") and hasClickAction()).performClick()
        compose.waitUntil(LauncherHarness.LONG_TIMEOUT) { launcher.activity.drawer.progress.value == 1f }
    }

    @Test
    fun calculatorShowsResult() {
        compose.onNode(searchField).performTextInput("12*7")
        launcher.waitForNode("the calculator result", hasText("= 84") and inResults)
    }

    @Test
    fun typingAnAppNameShowsThatAppFirst() {
        val app = distinctiveApp()
        compose.onNode(searchField).performTextReplacement(app.label)
        launcher.waitForNode("${app.label} in the results", SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(app.label)) and inResults)

        val tiles = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription) and inResults)
            .fetchSemanticsNodes()
            .sortedWith(compareBy({ it.boundsInRoot.top }, { it.boundsInRoot.left }))
        assertEquals("first result for \"${app.label}\"", app.label, tiles.first().contentDescription)
    }

    /**
     * Result slots are reused as the query changes. The first tile must switch to the new app's
     * icon instead of keeping the previous app's one (it used to keep it until the drawer closed).
     */
    @Test
    fun iconFollowsTheAppWhenResultsChange() {
        // Reference pictures, each from a results list built from scratch (empty query first).
        val references = LinkedHashMap<AppInfo, IntArray>()
        for (app in distinctiveApps()) {
            references[app] = freshFirstTilePixels(app)
            if (references.values.distinctBy { it.contentHashCode() }.size >= 2) break
        }
        val pair = references.entries.distinctBy { it.value.contentHashCode() }.map { it.key }
        assertTrue("Need two searchable apps with different icons", pair.size >= 2)
        val (first, second) = pair

        // The field still holds the last reference's query (second), so every switch below reuses
        // the first result slot for another app. A stale slot shows the previous app's icon.
        for (app in listOf(first, second, first)) {
            compose.onNode(searchField).performTextReplacement(app.label)
            waitForInResults(app)
            val expected = references.getValue(app)
            launcher.waitFor("${app.label}'s own icon in the reused result slot", timeoutMs = 5_000) {
                firstTilePixels(app).contentEquals(expected).takeIf { it }
            }
        }
    }

    private fun tileOf(app: AppInfo) = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(app.label)) and inResults

    private fun waitForInResults(app: AppInfo) = launcher.waitForNode("${app.label} in the results", tileOf(app))

    private fun firstTilePixels(app: AppInfo): IntArray {
        val image = compose.onAllNodes(tileOf(app)).onFirst().captureToImage()
        return IntArray(image.width * image.height).also { image.readPixels(it) }
    }

    private fun freshFirstTilePixels(app: AppInfo): IntArray {
        compose.onNode(searchField).performTextReplacement("")
        compose.waitForIdle()
        compose.onNode(searchField).performTextReplacement(app.label)
        waitForInResults(app)
        val icons = launcher.graph.iconRepository
        launcher.waitFor("${app.label}'s icon to load") { icons.peek(icons.appKey(app.key, app.lastUpdateTime)) }
        compose.waitForIdle()
        return firstTilePixels(app)
    }

    /** An app whose label isn't contained in another app's label, so it must rank first. */
    private fun distinctiveApp(): AppInfo = distinctiveApps().first()

    private fun distinctiveApps(): List<AppInfo> {
        val apps = launcher.apps()
        return apps.filter { app ->
            app.label.length >= 4 && apps.none { it !== app && it.label.contains(app.label, ignoreCase = true) }
        }
    }
}
