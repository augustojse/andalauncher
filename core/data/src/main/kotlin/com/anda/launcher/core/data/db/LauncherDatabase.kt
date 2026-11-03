package com.anda.launcher.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ItemEntity::class,
        ScreenEntity::class,
        AppOverrideEntity::class,
        DrawerGroupEntity::class,
        DrawerGroupMemberEntity::class,
        LaunchStatEntity::class,
        FavoriteEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun layoutDao(): LayoutDao
    abstract fun appOverrideDao(): AppOverrideDao
    abstract fun drawerGroupDao(): DrawerGroupDao
    abstract fun launchStatsDao(): LaunchStatsDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        /** 0.6.0: favorites. A new table only; nothing existing changes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorites` (`key` TEXT NOT NULL, `domain` TEXT NOT NULL, " +
                        "`position` INTEGER NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`key`))",
                )
            }
        }

        const val NAME = "launcher.db"

        /**
         * Every schema migration, oldest first. A schema bump adds one here plus a test in
         * `LauncherDatabaseMigrationTest` (core/data/src/androidTest).
         */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        /**
         * Installing an older APK over a newer one would otherwise crash-loop on open (Room can't
         * migrate down). The DB is recreated instead, and AppGraph restores the newest automatic
         * backup this version can read.
         */
        fun build(context: Context): LauncherDatabase =
            Room.databaseBuilder(context, LauncherDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
    }
}
