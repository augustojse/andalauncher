package com.anda.launcher.core.apps

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LatinKeyIndexTest {
    private val calls = mutableListOf<String>()

    /** Stands in for ICU: anything starting with "Т" is "non-Latin". */
    private val index = LatinKeyIndex { label ->
        calls += label
        if (label.startsWith("Т")) "telegram" else null
    }
    private val labels = MutableStateFlow<Collection<String>>(listOf("Телеграм", "Maps"))
    private val enabled = MutableStateFlow(true)

    private fun TestScope.start() {
        backgroundScope.launch { index.run(labels, enabled, startDelayMs = 3_000) }
    }

    @Test
    fun nothingRunsBeforeTheStartDelay() = runTest {
        start()
        advanceTimeBy(2_999)
        runCurrent()
        assertTrue(calls.isEmpty())
        assertTrue(index.keys.value.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(mapOf("Телеграм" to "telegram"), index.keys.value)
    }

    @Test
    fun turnedOffNeverTransliterates() = runTest {
        enabled.value = false
        start()
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(calls.isEmpty())

        enabled.value = true
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals(mapOf("Телеграм" to "telegram"), index.keys.value)
    }

    @Test
    fun eachLabelIsKeyedOnce() = runTest {
        start()
        advanceTimeBy(3_001)
        runCurrent()
        labels.value = listOf("Телеграм", "Maps", "Camera")
        runCurrent()
        assertEquals(listOf("Телеграм", "Maps", "Camera"), calls)
    }

    @Test
    fun requestedLabelsAreKeyedToo() = runTest {
        index.request(listOf("Т shortcut"))
        start()
        advanceTimeBy(3_001)
        runCurrent()
        assertEquals("telegram", index.keys.value["Т shortcut"])
    }

    @Test
    fun aFailingLabelDoesNotStopThePass() = runTest {
        val failing = LatinKeyIndex { label -> if (label == "bad") error("ICU") else "key-$label" }
        backgroundScope.launch { failing.run(MutableStateFlow(listOf("bad", "good")), enabled, startDelayMs = 0) }
        runCurrent()
        assertEquals(mapOf("good" to "key-good"), failing.keys.value)
    }
}
