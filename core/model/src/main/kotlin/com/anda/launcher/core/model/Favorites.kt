package com.anda.launcher.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Identifies anything search can find, across kinds: an app, a shortcut, a contact, an event or
 * a settings page. [domain] says which; [id] is unique within it. Favorites use it today; tags and
 * visibility levels will key on it later.
 */
data class ItemKey(val domain: String, val id: String) {
    /** `domain:id`, the database key. */
    fun flatten(): String = "$domain:$id"

    companion object {
        const val APP = "app"
        const val SHORTCUT = "shortcut"
        const val CONTACT = "contact"
        const val EVENT = "event"
        const val SETTING = "setting"
    }
}

/**
 * A pinned item, with what the drawer needs to show and open it again without searching. Stored
 * as JSON per row, so a kind this version doesn't know (from a newer one) only loses that row.
 */
@Serializable
sealed interface Favorite {
    val key: ItemKey
    val label: String

    @Serializable
    @SerialName("app")
    data class App(val component: ComponentKey, override val label: String) : Favorite {
        override val key get() = ItemKey(ItemKey.APP, component.flatten())
    }

    @Serializable
    @SerialName("shortcut")
    data class Shortcut(
        val packageName: String,
        val shortcutId: String,
        val user: Long,
        override val label: String,
        val appLabel: String,
    ) : Favorite {
        override val key get() = ItemKey(ItemKey.SHORTCUT, "$packageName/$shortcutId#$user")
    }

    @Serializable
    @SerialName("contact")
    data class Contact(
        override val label: String,
        val lookupUri: String,
        val photoUri: String? = null,
        val phone: String? = null,
    ) : Favorite {
        override val key get() = ItemKey(ItemKey.CONTACT, lookupUri)
    }

    @Serializable
    @SerialName("event")
    data class Event(
        val eventId: Long,
        override val label: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val color: Int? = null,
    ) : Favorite {
        override val key get() = ItemKey(ItemKey.EVENT, eventId.toString())
    }

    @Serializable
    @SerialName("setting")
    data class Setting(override val label: String, val action: String) : Favorite {
        override val key get() = ItemKey(ItemKey.SETTING, action)
    }

    /** Whether this item belongs to [packageName] in profile [user], so it goes when that app does. */
    fun belongsTo(packageName: String, user: Long): Boolean = when (this) {
        is App -> component.packageName == packageName && component.user == user
        is Shortcut -> this.packageName == packageName && this.user == user
        else -> false
    }

    companion object {
        /** Two rows in a typical drawer; the row is meant for a handful of things. */
        const val MAX_FAVORITES = 20
    }
}

/** Pure list operations behind the favorites repository, so they unit-test without a database. */
object FavoriteOrder {
    /** [items] with [item] added at the end; unchanged if it is already there or the row is full. */
    fun pin(items: List<Favorite>, item: Favorite): List<Favorite> =
        if (items.any { it.key == item.key } || items.size >= Favorite.MAX_FAVORITES) items else items + item

    fun unpin(items: List<Favorite>, key: ItemKey): List<Favorite> = items.filterNot { it.key == key }

    /**
     * [key] moved to where [target] is: just before it when moving earlier, just after it when
     * moving later. The drawer passes the neighbour it can see, so pins it hides don't make a
     * move look like it did nothing.
     */
    fun moveTo(items: List<Favorite>, key: ItemKey, target: ItemKey): List<Favorite> {
        val from = items.indexOfFirst { it.key == key }
        val to = items.indexOfFirst { it.key == target }
        if (from < 0 || to < 0 || from == to) return items
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }
}
