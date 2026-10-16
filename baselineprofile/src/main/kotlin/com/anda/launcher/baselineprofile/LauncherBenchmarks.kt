package com.anda.launcher.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Performance budgets from docs/RND.md, measured on a physical Pixel 9:
 *  - cold start ≤ 350 ms (p50)        → [coldStartup]
 *  - drawer open/scroll p95 ≤ 8.3 ms   → [drawerScroll]
 *
 * Run: `gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LauncherBenchmarks {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.COLD,
        iterations = 10,
    ) {
        startHome()
    }

    @Test
    fun coldStartupNoProfile() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5,
    ) {
        startHome()
    }

    @Test
    fun drawerScroll() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { startHome() },
    ) {
        openDrawer()
        scrollDrawer()
        device.pressBack()
    }

    @Test
    fun pageSwipes() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { startHome() },
    ) {
        val w = device.displayWidth
        val h = device.displayHeight
        repeat(4) {
            device.swipe(w * 8 / 10, h / 2, w * 2 / 10, h / 2, 10)
            device.waitForIdle()
            device.swipe(w * 2 / 10, h / 2, w * 8 / 10, h / 2, 10)
            device.waitForIdle()
        }
    }
}
