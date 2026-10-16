package com.anda.launcher.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LayoutDao {
    @Query("SELECT * FROM screens ORDER BY rank")
    fun observeScreens(): Flow<List<ScreenEntity>>

    @Query("SELECT * FROM items")
    fun observeItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM screens ORDER BY rank")
    suspend fun screens(): List<ScreenEntity>

    @Query("SELECT * FROM items")
    suspend fun items(): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun item(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE container = :container")
    suspend fun itemsIn(container: Long): List<ItemEntity>

    @Query("SELECT * FROM items WHERE container = :container AND screenId = :screenId")
    suspend fun itemsOnScreen(container: Long, screenId: Long): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun itemCount(): Int

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    @Update
    suspend fun updateAll(items: List<ItemEntity>)

    @Delete
    suspend fun delete(item: ItemEntity)

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query("SELECT * FROM items WHERE packageName = :packageName AND user = :user")
    suspend fun itemsForPackage(packageName: String, user: Long): List<ItemEntity>

    @Insert
    suspend fun insertScreen(screen: ScreenEntity): Long

    @Update
    suspend fun updateScreens(screens: List<ScreenEntity>)

    @Query("DELETE FROM screens WHERE id = :id")
    suspend fun deleteScreen(id: Long)

    @Query("DELETE FROM items")
    suspend fun clearItems()

    @Query("DELETE FROM screens")
    suspend fun clearScreens()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreens(screens: List<ScreenEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemsReplacing(items: List<ItemEntity>)

    @Transaction
    suspend fun replaceAll(screens: List<ScreenEntity>, items: List<ItemEntity>) {
        clearItems()
        clearScreens()
        insertScreens(screens)
        insertItemsReplacing(items)
    }
}

@Dao
interface AppOverrideDao {
    @Query("SELECT * FROM app_overrides")
    fun observeAll(): Flow<List<AppOverrideEntity>>

    @Query("SELECT * FROM app_overrides")
    suspend fun all(): List<AppOverrideEntity>

    @Query("SELECT * FROM app_overrides WHERE component = :component")
    suspend fun get(component: String): AppOverrideEntity?

    @Upsert
    suspend fun upsert(entity: AppOverrideEntity)

    @Upsert
    suspend fun upsertAll(entities: List<AppOverrideEntity>)

    @Query("DELETE FROM app_overrides")
    suspend fun clear()
}

@Dao
interface DrawerGroupDao {
    @Query("SELECT * FROM drawer_groups ORDER BY rank")
    fun observeGroups(): Flow<List<DrawerGroupEntity>>

    @Query("SELECT * FROM drawer_group_members")
    fun observeMembers(): Flow<List<DrawerGroupMemberEntity>>

    @Query("SELECT * FROM drawer_groups ORDER BY rank")
    suspend fun groups(): List<DrawerGroupEntity>

    @Query("SELECT * FROM drawer_group_members")
    suspend fun members(): List<DrawerGroupMemberEntity>

    @Insert
    suspend fun insertGroup(group: DrawerGroupEntity): Long

    @Update
    suspend fun updateGroup(group: DrawerGroupEntity)

    @Query("DELETE FROM drawer_groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Query("DELETE FROM drawer_group_members WHERE groupId = :groupId")
    suspend fun clearMembers(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<DrawerGroupMemberEntity>)

    @Transaction
    suspend fun setMembers(groupId: Long, components: List<String>) {
        clearMembers(groupId)
        insertMembers(components.map { DrawerGroupMemberEntity(groupId, it) })
    }

    @Transaction
    suspend fun deleteGroupWithMembers(groupId: Long) {
        clearMembers(groupId)
        deleteGroup(groupId)
    }

    @Query("DELETE FROM drawer_groups")
    suspend fun clearGroups()

    @Query("DELETE FROM drawer_group_members")
    suspend fun clearAllMembers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<DrawerGroupEntity>)
}

@Dao
interface LaunchStatsDao {
    @Query("SELECT * FROM launch_stats")
    fun observeAll(): Flow<List<LaunchStatEntity>>

    @Query("SELECT * FROM launch_stats WHERE component = :component")
    suspend fun get(component: String): LaunchStatEntity?

    @Upsert
    suspend fun upsert(entity: LaunchStatEntity)
}
