package com.anda.launcher.core.apps

import android.content.pm.ApplicationInfo
import com.anda.launcher.core.model.AppCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class AppCategorizerTest {
    @Test
    fun manifestCategoryWins() {
        assertEquals(AppCategory.Games, AppCategorizer.categorize("com.whatsapp", ApplicationInfo.CATEGORY_GAME))
    }

    @Test
    fun packageHeuristics() {
        val none = ApplicationInfo.CATEGORY_UNDEFINED
        assertEquals(AppCategory.Communication, AppCategorizer.categorize("com.whatsapp", none))
        assertEquals(AppCategory.Media, AppCategorizer.categorize("com.spotify.music", none))
        assertEquals(AppCategory.Navigation, AppCategorizer.categorize("com.waze", none))
        assertEquals(AppCategory.Finance, AppCategorizer.categorize("com.revolut.revolut", none))
        assertEquals(AppCategory.Other, AppCategorizer.categorize("org.example.unknown", none))
    }
}
