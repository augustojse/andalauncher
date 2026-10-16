package com.anda.launcher.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.Direction

const val TARGET_PACKAGE = "com.anda.launcher"

/** Explicit HOME intent: the package's LAUNCHER entry is the settings activity, not the home screen. */
fun homeIntent(): Intent = Intent(Intent.ACTION_MAIN)
    .addCategory(Intent.CATEGORY_HOME)
    .setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.MainActivity")
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun MacrobenchmarkScope.startHome() {
    startActivityAndWait(homeIntent())
    device.waitForIdle()
    // A fresh install shows the first-run welcome card over the bottom of home: dismiss it.
    device.findObject(androidx.test.uiautomator.By.text("Done"))?.let {
        it.click()
        device.waitForIdle()
    }
}

/** Swipe between pages, open the drawer, scroll it, search, close. */
fun MacrobenchmarkScope.criticalUserJourney() {
    val w = device.displayWidth
    val h = device.displayHeight
    device.swipe(w * 8 / 10, h / 2, w * 2 / 10, h / 2, 12)
    device.swipe(w * 2 / 10, h / 2, w * 8 / 10, h / 2, 12)
    device.waitForIdle()
    openDrawer()
    scrollDrawer()
    device.pressBack()
    device.waitForIdle()
}

fun MacrobenchmarkScope.openDrawer() {
    val w = device.displayWidth
    val h = device.displayHeight
    device.swipe(w / 2, h * 7 / 10, w / 2, h * 2 / 10, 10)
    device.waitForIdle()
}

fun MacrobenchmarkScope.scrollDrawer() {
    val h = device.displayHeight
    val w = device.displayWidth
    repeat(3) {
        device.swipe(w / 2, h * 8 / 10, w / 2, h * 3 / 10, 15)
        device.waitForIdle()
    }
    // Fling back up via the scrollable node when the tree exposes it.
    device.findObject(androidx.test.uiautomator.By.scrollable(true))?.fling(Direction.UP)
    device.waitForIdle()
}
