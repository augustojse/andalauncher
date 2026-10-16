package com.anda.launcher

import android.os.Process
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.anda.launcher.core.model.WidgetItem
import com.anda.launcher.core.ui.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPickerTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val launcher = LauncherHarness(compose)

    private val testPackage get() = launcher.instrumentation.context.packageName

    @Before
    fun setUp() {
        launcher.start()
        // Without a bind grant, binding goes through the system "Create widget?" dialog.
        // ("--user current" resolves to -2 and fails; per-user uid range is 100000.)
        launcher.shell("appwidget revokebind --package ${LauncherHarness.PACKAGE} --user ${Process.myUid() / 100_000}")
    }

    @Test
    fun addWidgetFromPickerThroughBindDialog() = with(launcher) {
        // Long-press empty space → edit mode → Widgets.
        longPress(cellCenter(2, 2))
        waitForNode("edit mode", hasText("Widgets") and hasClickAction())
        compose.onNode(hasText("Widgets") and hasClickAction()).performClick()
        waitForNode("the widget picker", hasTestTag(TestTags.WIDGET_PICKER))

        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(TestTags.WIDGET_PICKER))).performTextInput("Anda Test")
        waitForNode("the test widget preview", hasText(WIDGET_LABEL) and hasAnyAncestor(hasTestTag(TestTags.WIDGET_PICKER)))
        val preview = compose.onNode(hasText(WIDGET_LABEL) and hasAnyAncestor(hasTestTag(TestTags.WIDGET_PICKER))).centerInRoot()
        tap(preview)

        // System bind dialog (Settings' AllowBindAppWidgetActivity): accept with the positive button.
        val create = device.wait(Until.findObject(By.res("android", "button1")), DIALOG_TIMEOUT)
        assertNotNull("the widget bind dialog should appear", create)
        create.click()

        awaitLayout("the test widget on the home page") { layout ->
            layout.itemsOn(LauncherHarness.SCREEN).any { it is WidgetItem && it.provider.packageName == testPackage }
        }
        val widget = activity.home.layout.value.itemsOn(LauncherHarness.SCREEN).filterIsInstance<WidgetItem>().single()
        assertEquals(TestWidgetProvider::class.java.name, widget.provider.className)
        assertEquals("2×1 cells, first free spot", spec.cellRect(0, 0, 2, 1), widget.rect)
        assertEquals(false, activity.home.widgetPickerOpen)

        // The bound widget really renders its RemoteViews in the launcher. (Inflated views report
        // the provider's package, i.e. the test APK, so match on text only.)
        assertNotNull(
            "widget content should be visible",
            device.wait(Until.findObject(By.text(WIDGET_TEXT)), DIALOG_TIMEOUT),
        )
    }

    private companion object {
        const val WIDGET_LABEL = "Anda test widget"
        const val WIDGET_TEXT = "Anda test widget content"
        const val DIALOG_TIMEOUT = 15_000L
    }
}
