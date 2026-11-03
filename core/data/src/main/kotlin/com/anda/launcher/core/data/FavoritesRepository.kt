package com.anda.launcher.core.data

import com.anda.launcher.core.data.db.FavoriteEntity
import com.anda.launcher.core.data.db.LauncherDatabase
import com.anda.launcher.core.model.Favorite
import com.anda.launcher.core.model.FavoriteOrder
import com.anda.launcher.core.model.ItemKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pinned items for the drawer's Favorites row. Every change rewrites the (short) row in one
 * transaction, so positions never have gaps or duplicates.
 */
class FavoritesRepository(db: LauncherDatabase) {
    private val dao = db.favoriteDao()
    private val mutex = Mutex()

    val favorites: Flow<List<Favorite>> = dao.observeAll().map(::decode).flowOn(Dispatchers.Default)

    /** False when the row is full and [item] wasn't already in it. */
    suspend fun pin(item: Favorite): Boolean {
        var pinned = false
        change { list -> FavoriteOrder.pin(list, item).also { next -> pinned = next.any { it.key == item.key } } }
        return pinned
    }

    suspend fun unpin(key: ItemKey) = change { FavoriteOrder.unpin(it, key) }

    suspend fun moveTo(key: ItemKey, target: ItemKey) = change { FavoriteOrder.moveTo(it, key, target) }

    /** An uninstalled app takes its pins (the app and its shortcuts) with it. */
    suspend fun removePackage(packageName: String, user: Long) = change { list -> list.filterNot { it.belongsTo(packageName, user) } }

    private suspend fun change(transform: (List<Favorite>) -> List<Favorite>) = mutex.withLock {
        val current = decode(dao.all())
        val next = transform(current)
        if (next != current) dao.replaceAll(encode(next))
    }

    companion object {
        /** Rows that no longer decode (a kind from a newer version, a damaged payload) are skipped. */
        internal fun decode(rows: List<FavoriteEntity>): List<Favorite> = rows.sortedBy { it.position }.mapNotNull { row ->
            runCatching { LauncherJson.decodeFromString(Favorite.serializer(), row.payload) }.getOrNull()
        }

        internal fun encode(items: List<Favorite>): List<FavoriteEntity> = items.mapIndexed { index, item ->
            FavoriteEntity(
                key = item.key.flatten(),
                domain = item.key.domain,
                position = index,
                payload = LauncherJson.encodeToString(Favorite.serializer(), item),
            )
        }
    }
}
