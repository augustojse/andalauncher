package com.anda.launcher

import android.content.Context
import androidx.core.content.edit
import com.anda.launcher.core.data.CrashReports

/**
 * "Never crash-loop" (docs/RND.md): if the launcher crashed at least twice in the last
 * [WINDOW_MS], the next start runs in safe mode (no widgets, no background jobs, banner offering
 * a restore). Every crash is also written to [reports] so the user can share it.
 *
 * The decision only looks at crash times, never at how long a process lived: a launcher that
 * dies 35 s after every start is still a loop (an earlier version reset the count after 30 s of
 * uptime and could never catch that).
 */
class CrashGuard(context: Context, private val reports: CrashReports) {
    private val prefs = context.getSharedPreferences("crash_guard", Context.MODE_PRIVATE)

    /** Decided once per process. */
    val safeMode: Boolean = isCrashLoop(crashTimes(), System.currentTimeMillis())

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                reports.record(error, thread.name, fatal = true)
                val times = (crashTimes() + System.currentTimeMillis()).sorted().takeLast(MAX_TIMES)
                // commit(): the process is about to die, apply() might never reach disk.
                prefs.edit(commit = true) { putStringSet(KEY_CRASHES, times.map { it.toString() }.toSet()) }
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The user left safe mode (dismissed the banner or restored a backup). */
    fun exitSafeMode() {
        prefs.edit { remove(KEY_CRASHES) }
    }

    private fun crashTimes(): List<Long> =
        prefs.getStringSet(KEY_CRASHES, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }

    companion object {
        private const val KEY_CRASHES = "crashes"
        private const val MAX_TIMES = 5
        const val WINDOW_MS = 120_000L

        /** Two or more crashes within the last [WINDOW_MS] mean the launcher is crash-looping. */
        fun isCrashLoop(crashTimes: List<Long>, now: Long): Boolean =
            crashTimes.count { now - it in 0 until WINDOW_MS } >= 2
    }
}
