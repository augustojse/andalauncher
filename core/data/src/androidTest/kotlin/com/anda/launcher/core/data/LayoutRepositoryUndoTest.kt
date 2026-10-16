package com.anda.launcher.core.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anda.launcher.core.data.db.LauncherDatabase
import com.anda.launcher.core.model.CellRect
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.Containers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Undo must put the layout back exactly, including a folder that the removal unwrapped. */
@RunWith(AndroidJUnit4::class)
class LayoutRepositoryUndoTest {
    private lateinit var db: LauncherDatabase
    private lateinit var repo: LayoutRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LauncherDatabase::class.java).build()
        repo = LayoutRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun key(name: String) = ComponentKey("com.example.$name", ".Main")

    @Test
    fun removeThenRestoreIsANoOp() = runBlocking {
        val screen = repo.addScreen()
        val a = repo.addApp(key("a"), Containers.DESKTOP, screen, CellRect(0, 0, 2, 2))
        val before = repo.snapshot()

        val removal = repo.removeItem(a)
        assertEquals(0, repo.snapshot().second.size)

        repo.restore(removal)
        assertEquals(before, repo.snapshot())
    }

    @Test
    fun restoreRewrapsAFolderTheRemovalUnwrapped() = runBlocking {
        val screen = repo.addScreen()
        val a = repo.addApp(key("a"), Containers.DESKTOP, screen, CellRect(0, 0, 2, 2))
        val b = repo.addApp(key("b"), Containers.DESKTOP, screen, CellRect(2, 0, 2, 2))
        repo.createFolder(a, b, "Pair")
        val before = repo.snapshot()

        // Removing one of two children turns the folder back into the other app...
        val removal = repo.removeItem(b)
        val after = repo.snapshot().second
        assertEquals(1, after.size)
        assertEquals(a, after.single().id)

        // ...and undo brings back the folder, both children and their ranks.
        repo.restore(removal)
        assertEquals(before.second.sortedBy { it.id }, repo.snapshot().second.sortedBy { it.id })
    }
}
