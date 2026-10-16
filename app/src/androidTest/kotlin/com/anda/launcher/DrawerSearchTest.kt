package com.anda.launcher

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
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.ui.TestTags
import org.junit.Assert.assertEquals
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

    /** An app whose label isn't contained in another app's label, so it must rank first. */
    private fun distinctiveApp(): AppInfo {
        val apps = launcher.apps()
        return apps.first { app ->
            app.label.length >= 4 && apps.none { it !== app && it.label.contains(app.label, ignoreCase = true) }
        }
    }
}
