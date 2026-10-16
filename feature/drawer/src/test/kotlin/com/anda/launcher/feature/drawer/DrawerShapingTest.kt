package com.anda.launcher.feature.drawer

import com.anda.launcher.core.model.AppCategory
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.DrawerGroup
import com.anda.launcher.core.model.DrawerSort
import com.anda.launcher.core.model.LaunchStat
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerShapingTest {

    private fun app(name: String, installTime: Long = 0, hidden: Boolean = false, work: Boolean = false) = AppInfo(
        key = ComponentKey("com.example.$name", ".Main", if (work) 10L else 0L),
        label = name,
        originalLabel = name,
        installTime = installTime,
        lastUpdateTime = 0,
        category = AppCategory.Other,
        isWorkProfile = work,
        isHidden = hidden,
    )

    private val alpha = app("alpha", installTime = 1)
    private val beta = app("beta", installTime = 3)
    private val gamma = app("gamma", installTime = 2)
    private val secret = app("secret", hidden = true)
    private val office = app("office", work = true)
    private val apps = listOf(alpha, beta, gamma, secret, office)
    private val folder = DrawerGroup(id = 1, isFolder = true, name = "Tools", rank = 0, members = setOf(beta.key))

    private fun visible(
        sort: DrawerSort = DrawerSort.Alphabetical,
        tab: DrawerTab = DrawerTab.All,
        hiddenUnlocked: Boolean = false,
        separateWork: Boolean = true,
        drawerFolders: Boolean = true,
        stats: Map<ComponentKey, LaunchStat> = emptyMap(),
    ) = visibleApps(apps, listOf(folder), stats, sort, tab, hiddenUnlocked, separateWork, drawerFolders).map { it.label }

    @Test
    fun allTabHidesHiddenWorkAndFolderMembers() {
        // beta lives in a drawer folder; the folder tile shows it (in every drawer style).
        assertEquals(listOf("alpha", "gamma"), visible())
    }

    @Test
    fun folderMembersStayWhenDrawerFoldersAreOff() {
        assertEquals(listOf("alpha", "beta", "gamma"), visible(drawerFolders = false))
    }

    @Test
    fun unlockingRevealsHiddenApps() {
        assertEquals(listOf("alpha", "gamma", "secret"), visible(hiddenUnlocked = true))
    }

    @Test
    fun workTabShowsOnlyWorkApps() {
        assertEquals(listOf("office"), visible(tab = DrawerTab.Work))
        assertEquals(listOf("alpha", "gamma", "office"), visible(separateWork = false))
    }

    @Test
    fun installDateSortIsNewestFirst() {
        assertEquals(listOf("gamma", "alpha"), visible(sort = DrawerSort.InstallDate))
    }

    @Test
    fun mostUsedSortsByLaunchCount() {
        val stats = mapOf(alpha.key to LaunchStat(1, 0), gamma.key to LaunchStat(9, 0))
        assertEquals(listOf("gamma", "alpha"), visible(sort = DrawerSort.MostUsed, stats = stats))
    }

    @Test
    fun suggestionsWeighRecencyAndSkipHiddenApps() {
        val day = 86_400_000L
        val now = 100 * day
        val stats = mapOf(
            alpha.key to LaunchStat(count = 10, lastLaunch = now - 30 * day), // often, but long ago
            gamma.key to LaunchStat(count = 4, lastLaunch = now), // less often, today
            secret.key to LaunchStat(count = 50, lastLaunch = now),
        )
        assertEquals(listOf("gamma", "alpha"), suggestions(apps, stats, count = 5, now = now).map { it.label })
        assertEquals(listOf("gamma"), suggestions(apps, stats, count = 1, now = now).map { it.label })
    }
}
