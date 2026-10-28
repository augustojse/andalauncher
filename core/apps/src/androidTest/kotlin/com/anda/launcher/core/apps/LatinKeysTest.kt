package com.anda.launcher.core.apps

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real ICU on the device: what the keys look like, and what building them costs at load. */
@RunWith(AndroidJUnit4::class)
class LatinKeysTest {

    @Test
    fun cyrillicGreekAndKanaBecomeLatin() {
        assertEquals("telegram", LatinKeys.of("Телеграм"))
        assertEquals("kamera", LatinKeys.of("Камера"))
        assertEquals("kairos", LatinKeys.of("Καιρός"))
        assertEquals("toukyou", LatinKeys.of("とうきょう"))
    }

    @Test
    fun latinLabelsGetNoKey() {
        assertNull(LatinKeys.of("Spotify"))
        assertNull(LatinKeys.of("Café 2.0"))
        assertNull(LatinKeys.of("123"))
    }

    @Test
    fun mixedLabelsKeepTheirLatinPart() {
        assertEquals("vk messendzer", LatinKeys.of("VK Мессенджер"))
    }

    /**
     * Logged under the tag LatinKeysTiming (docs/plans/0.6.0.md). Loading ICU is slow, which is
     * why [LatinKeyIndex] builds keys in the background; once a script is loaded, keys must stay
     * cheap, which is what this asserts.
     */
    @Test
    fun timesThreeHundredLabels() {
        val pm = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        val installed = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() }
        // A heavy non-Latin phone: one label in five in another script, the rest Latin.
        val mixed = List(300) { i -> if (i % 5 == 0) Samples[i / 5 % Samples.size] + " $i" else "App number $i" }
        val worst = List(300) { i -> Samples[i % Samples.size] + " $i" }

        val cold = timeMs { LatinKeys.of(Samples.first()) }
        val installedMs = timeMs { installed.forEach { LatinKeys.of(it) } }
        val mixedMs = timeMs { mixed.forEach { LatinKeys.of(it) } }
        val worstMs = timeMs { worst.forEach { LatinKeys.of(it) } }
        val nonLatinInstalled = installed.count { LatinKeys.hasNonLatinLetters(it) }
        val report = "cold first key ${fmt(cold)} ms · ${installed.size} installed labels ($nonLatinInstalled non-Latin) ${fmt(installedMs)} ms · " +
            "300 mixed (60 non-Latin) ${fmt(mixedMs)} ms · 300 non-Latin ${fmt(worstMs)} ms"
        Log.i("LatinKeysTiming", report)
        // Every script is loaded by now: 300 more keys must take well under a frame's worth each.
        assertTrue(report, worstMs < 150.0)
    }

    /**
     * Cold cost of one script, in a fresh process: `am instrument -e sample <index into Samples>`.
     * Skipped when no sample is given (a normal test run).
     */
    @Test
    fun coldCostOfOneScript() {
        val index = InstrumentationRegistry.getArguments().getString("sample")?.toInt() ?: return
        val create = timeMs { android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII") }
        val first = timeMs { LatinKeys.of(Samples[index]) }
        val second = timeMs { LatinKeys.of(Samples[index] + " 2") }
        Log.i("LatinKeysTiming", "sample $index ${Samples[index]}: create ${fmt(create)} ms · first ${fmt(first)} ms · second ${fmt(second)} ms")
    }

    private inline fun timeMs(block: () -> Unit): Double {
        val start = SystemClock.elapsedRealtimeNanos()
        block()
        return (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
    }

    private fun fmt(ms: Double) = "%.1f".format(ms)

    private companion object {
        val Samples = listOf(
            "Телеграм", "Камера", "Καιρός", "とうきょう", "カメラ", "微信", "地图", "카카오톡", "날씨",
            "تيليجرام", "טלגרם", "मौसम", "ไลน์", "Галерея", "Настройки", "Сбербанк", "Ημερολόγιο",
        )
    }
}
