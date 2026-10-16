package com.anda.launcher.core.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests against the schemas exported to `core/data/schemas` (wired in as androidTest
 * assets). Pattern for a schema bump to version N:
 *  1. add `MIGRATION_{N-1}_N` to [LauncherDatabase.MIGRATIONS];
 *  2. add a test that creates the DB at N-1 with [seedVersion1]-style raw SQL, runs
 *     `helper.runMigrationsAndValidate(DB, N, true, MIGRATION_{N-1}_N)` and checks the rows;
 *  3. [migrateAllToLatest] keeps covering the whole chain from version 1.
 */
@RunWith(AndroidJUnit4::class)
class LauncherDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LauncherDatabase::class.java)

    @Test
    fun version1SchemaMatchesExportedJson() {
        helper.createDatabase(DB, 1).apply {
            seedVersion1()
            close()
        }
        // Validates every table, column and index against schemas/.../1.json.
        helper.runMigrationsAndValidate(DB, 1, true, *LauncherDatabase.MIGRATIONS).close()
    }

    @Test
    fun migrateAllToLatest() {
        helper.createDatabase(DB, 1).apply {
            seedVersion1()
            close()
        }
        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, LauncherDatabase::class.java, DB)
            .addMigrations(*LauncherDatabase.MIGRATIONS)
            .build()
        // Opening runs the chain and Room's identity-hash check; then the seeded data must survive.
        helper.closeWhenFinished(db)
        runBlocking {
            val dao = db.layoutDao()
            assertEquals(listOf(ScreenEntity(id = 1, rank = 0)), dao.screens())
            val items = dao.items().sortedBy { it.id }
            assertEquals(3, items.size)
            val folder = items.single { it.type == ItemType.FOLDER }
            assertEquals("Games", folder.title)
            assertEquals(2, items.count { it.container == folder.id })
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.seedVersion1() {
        execSQL("INSERT INTO screens (id, rank) VALUES (1, 0)")
        execSQL(
            "INSERT INTO items (id, type, container, screenId, x, y, spanX, spanY, rank, title, user, appWidgetId) " +
                "VALUES (10, ${ItemType.FOLDER}, -100, 1, 0, 0, 2, 2, 0, 'Games', 0, -1)",
        )
        for ((id, pkg) in listOf(11 to "com.example.a", 12 to "com.example.b")) {
            execSQL(
                "INSERT INTO items (id, type, container, screenId, x, y, spanX, spanY, rank, component, packageName, user, appWidgetId) " +
                    "VALUES ($id, ${ItemType.APP}, 10, 0, 0, 0, 2, 2, ${id - 11}, '$pkg/.Main#0', '$pkg', 0, -1)",
            )
        }
        execSQL("INSERT INTO launch_stats (component, launchCount, lastLaunch) VALUES ('com.example.a/.Main#0', 3, 1000)")
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
