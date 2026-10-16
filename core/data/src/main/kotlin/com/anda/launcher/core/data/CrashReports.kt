package com.anda.launcher.core.data

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device crash log. Anda has no INTERNET permission (docs/RND.md), so reports never leave the
 * phone on their own: the user can share them from Settings → About.
 *
 * Fatal crashes are written synchronously from the uncaught-exception handler; non-fatal errors
 * (caught by the app coroutine scope) are written the same way so they're visible too.
 */
class CrashReports(context: Context) {
    private val dir = File(context.filesDir, "crashes")
    private val appVersion: String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("unknown")

    fun record(error: Throwable, thread: String, fatal: Boolean) {
        runCatching {
            dir.mkdirs()
            val now = System.currentTimeMillis()
            val kind = if (fatal) "crash" else "error"
            File(dir, "$kind-$now.txt").writeText(format(error, thread, fatal, now))
            trim()
        }
    }

    /** Newest first. */
    fun list(): List<File> = dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()

    /** All reports as one text block for the share sheet, newest first, capped in size. */
    fun combined(maxChars: Int = 90_000): String {
        val sb = StringBuilder()
        for (file in list()) {
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            if (sb.length + text.length > maxChars) break
            sb.append(text).append("\n\n")
        }
        return sb.toString().trimEnd()
    }

    fun clear() {
        list().forEach { it.delete() }
    }

    private fun trim() {
        list().drop(MAX_REPORTS).forEach { it.delete() }
    }

    private fun format(error: Throwable, thread: String, fatal: Boolean, now: Long): String = buildString {
        appendLine(if (fatal) "Anda Launcher crash" else "Anda Launcher error (recovered)")
        appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(now))}")
        appendLine("App: $appVersion")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("Build: ${Build.FINGERPRINT}")
        appendLine("Thread: $thread")
        appendLine()
        append(Log.getStackTraceString(error).ifEmpty { error.toString() })
    }

    private companion object {
        const val MAX_REPORTS = 10
    }
}
