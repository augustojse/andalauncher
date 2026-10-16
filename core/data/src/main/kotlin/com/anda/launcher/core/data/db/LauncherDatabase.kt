package com.anda.launcher.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        ItemEntity::class,
        ScreenEntity::class,
        AppOverrideEntity::class,
        DrawerGroupEntity::class,
        DrawerGroupMemberEntity::class,
        LaunchStatEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LauncherDatabase : RoomDatabase() {
    abstract fun layoutDao(): LayoutDao
    abstract fun appOverrideDao(): AppOverrideDao
    abstract fun drawerGroupDao(): DrawerGroupDao
    abstract fun launchStatsDao(): LaunchStatsDao

    companion object {
        const val NAME = "launcher.db"

        /**
         * Every schema migration, oldest first. A schema bump adds one here plus a test in
         * `LauncherDatabaseMigrationTest` (core/data/src/androidTest).
         */
        val MIGRATIONS: Array<Migration> = arrayOf()

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
