package com.anda.launcher.core.data

import com.anda.launcher.core.data.db.AppOverrideEntity
import com.anda.launcher.core.data.db.DrawerGroupEntity
import com.anda.launcher.core.data.db.DrawerGroupKind
import com.anda.launcher.core.data.db.LaunchStatEntity
import com.anda.launcher.core.data.db.LauncherDatabase
import com.anda.launcher.core.model.AppCategory
import com.anda.launcher.core.model.AppOverride
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.DrawerGroup
import com.anda.launcher.core.model.LaunchStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Per-app label/icon/hidden/locked/category overrides. */
class AppOverridesRepository(db: LauncherDatabase) {
    private val dao = db.appOverrideDao()

    val overrides: Flow<Map<ComponentKey, AppOverride>> = dao.observeAll().map { list ->
        list.mapNotNull { e -> ComponentKey.parse(e.component)?.let { it to e.toModel(it) } }.toMap()
    }.flowOn(Dispatchers.Default)

    suspend fun update(key: ComponentKey, transform: (AppOverride) -> AppOverride) {
        val current = dao.get(key.flatten())?.toModel(key) ?: AppOverride(key)
        val next = transform(current)
        dao.upsert(
            AppOverrideEntity(
                component = key.flatten(),
                label = next.label,
                iconPath = next.iconPath,
                hidden = next.hidden,
                locked = next.locked,
                category = next.category?.name,
                swipeAction = next.swipeAction.encode(),
            ),
        )
    }

    private fun AppOverrideEntity.toModel(key: ComponentKey) = AppOverride(
        key = key,
        label = label,
        iconPath = iconPath,
        hidden = hidden,
        locked = locked,
        category = category?.let { name -> AppCategory.entries.firstOrNull { it.name == name } },
        swipeAction = swipeAction.decodeAction(),
    )
}

/** Drawer tabs and drawer folders. */
class DrawerGroupsRepository(db: LauncherDatabase) {
    private val dao = db.drawerGroupDao()

    val groups: Flow<List<DrawerGroup>> = combine(dao.observeGroups(), dao.observeMembers()) { groups, members ->
        val byGroup = members.groupBy({ it.groupId }, { it.component })
        groups.map { g ->
            DrawerGroup(
                id = g.id,
                isFolder = g.kind == DrawerGroupKind.FOLDER,
                name = g.name,
                rank = g.rank,
                members = byGroup[g.id].orEmpty().mapNotNull(ComponentKey::parse).toSet(),
            )
        }
    }.flowOn(Dispatchers.Default)

    suspend fun create(name: String, isFolder: Boolean, members: Collection<ComponentKey>): Long {
        val rank = dao.groups().size
        val id = dao.insertGroup(
            DrawerGroupEntity(kind = if (isFolder) DrawerGroupKind.FOLDER else DrawerGroupKind.TAB, name = name, rank = rank),
        )
        dao.setMembers(id, members.map { it.flatten() })
        return id
    }

    suspend fun rename(id: Long, name: String) {
        dao.groups().firstOrNull { it.id == id }?.let { dao.updateGroup(it.copy(name = name)) }
    }

    suspend fun setMembers(id: Long, members: Collection<ComponentKey>) = dao.setMembers(id, members.map { it.flatten() })

    suspend fun delete(id: Long) = dao.deleteGroupWithMembers(id)
}

/** Our own launch statistics: powers suggestions and "most used" sort without usage-access permission. */
class LaunchStatsRepository(db: LauncherDatabase) {
    private val dao = db.launchStatsDao()

    val stats: Flow<Map<ComponentKey, LaunchStat>> = dao.observeAll().map { list ->
        list.mapNotNull { e -> ComponentKey.parse(e.component)?.let { it to LaunchStat(e.launchCount, e.lastLaunch) } }.toMap()
    }.flowOn(Dispatchers.Default)

    suspend fun recordLaunch(key: ComponentKey, now: Long = System.currentTimeMillis()) {
        val flat = key.flatten()
        val current = dao.get(flat)
        dao.upsert(LaunchStatEntity(flat, (current?.launchCount ?: 0) + 1, now))
    }
}
