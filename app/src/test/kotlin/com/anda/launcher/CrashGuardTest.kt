package com.anda.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {
    private val now = 10_000_000L

    @Test
    fun oneCrashIsNotALoop() {
        assertFalse(CrashGuard.isCrashLoop(listOf(now - 5_000), now))
    }

    @Test
    fun twoRecentCrashesAreALoop() {
        assertTrue(CrashGuard.isCrashLoop(listOf(now - 70_000, now - 5_000), now))
    }

    @Test
    fun crashesLongAfterStartStillCount() {
        // The old guard only counted crashes in the first 10 s of a process and reset after 30 s,
        // so a launcher dying ~35 s after every start was never caught.
        val period = 35_000L
        assertTrue(CrashGuard.isCrashLoop(listOf(now - period, now - 2 * period), now))
    }

    @Test
    fun oldCrashesExpire() {
        assertFalse(CrashGuard.isCrashLoop(listOf(now - 10 * 60_000, now - 9 * 60_000), now))
    }

    @Test
    fun clockSkewIntoTheFutureIsIgnored() {
        assertFalse(CrashGuard.isCrashLoop(listOf(now + 1_000, now + 2_000), now))
    }
}
