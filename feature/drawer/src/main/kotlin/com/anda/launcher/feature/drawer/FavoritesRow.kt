package com.anda.launcher.feature.drawer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.Favorite
import com.anda.launcher.core.model.IconStyle
import com.anda.launcher.core.model.PackageUserKey
import com.anda.launcher.core.ui.icon.iconSize
import com.anda.launcher.core.ui.icon.rememberAppIcon
import com.anda.launcher.feature.search.FavoriteIcon
import com.anda.launcher.feature.search.SearchResult
import com.anda.launcher.feature.search.SearchResultActions
import com.anda.launcher.feature.search.intent

/** A pin with what is needed to show it now: the installed app for app pins, else nothing. */
internal data class ShownFavorite(val favorite: Favorite, val app: AppInfo?)

/**
 * The pins the row shows: apps and shortcuts only while their app is installed (and an app not
 * while it is hidden and locked away); contacts, events and settings pages always.
 */
internal fun shownFavorites(favorites: List<Favorite>, apps: List<AppInfo>, hiddenUnlocked: Boolean): List<ShownFavorite> {
    val byKey: Map<ComponentKey, AppInfo> = apps.associateBy { it.key }
    val installed: Set<PackageUserKey> = apps.mapTo(HashSet()) { PackageUserKey(it.key.packageName, it.key.user) }
    return favorites.mapNotNull { favorite ->
        when (favorite) {
            is Favorite.App -> byKey[favorite.component]?.takeIf { !it.isHidden || hiddenUnlocked }?.let { ShownFavorite(favorite, it) }
            is Favorite.Shortcut -> ShownFavorite(favorite, null).takeIf { PackageUserKey(favorite.packageName, favorite.user) in installed }
            else -> ShownFavorite(favorite, null)
        }
    }
}

/**
 * Pinned items above the app list, scrolling sideways. Tap opens; long-press offers Move earlier,
 * Move later and Remove, which also makes reordering reachable with a screen reader.
 */
@Composable
internal fun FavoritesRow(
    items: List<ShownFavorite>,
    controller: DrawerController,
    actions: SearchResultActions,
    style: IconStyle,
) {
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items.size, key = { items[it].favorite.key.flatten() }) { index ->
                FavoriteTile(
                    item = items[index],
                    previous = items.getOrNull(index - 1)?.favorite,
                    next = items.getOrNull(index + 1)?.favorite,
                    controller = controller,
                    actions = actions,
                    style = style,
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteTile(
    item: ShownFavorite,
    previous: Favorite?,
    next: Favorite?,
    controller: DrawerController,
    actions: SearchResultActions,
    style: IconStyle,
) {
    val favorite = item.favorite
    var menu by remember { mutableStateOf(false) }
    val size = style.iconSize()
    Box {
        Column(
            Modifier.width(size + 24.dp).clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = { open(item, controller, actions) },
                    onLongClick = { menu = true },
                    onLongClickLabel = "Favorite options",
                )
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (item.app != null) {
                val icon = rememberAppIcon(item.app.key, item.app.lastUpdateTime)
                if (icon != null) Image(icon, null, Modifier.size(size)) else Spacer(Modifier.size(size))
            } else {
                FavoriteIcon(favorite, size)
            }
            if (style.showLabels) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.app?.label ?: favorite.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (previous != null) {
                DropdownMenuItem(
                    text = { Text("Move earlier") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                    onClick = {
                        menu = false
                        controller.moveFavorite(favorite, previous)
                    },
                )
            }
            if (next != null) {
                DropdownMenuItem(
                    text = { Text("Move later") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    onClick = {
                        menu = false
                        controller.moveFavorite(favorite, next)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Remove from favorites") },
                leadingIcon = { Icon(Icons.Filled.StarBorder, contentDescription = null) },
                onClick = {
                    menu = false
                    controller.setPinned(favorite, false)
                },
            )
        }
    }
}

private fun open(item: ShownFavorite, controller: DrawerController, actions: SearchResultActions) {
    val favorite = item.favorite
    when {
        item.app != null -> controller.launch(item.app, Rect.Zero)
        favorite is Favorite.Shortcut -> actions.openShortcut(
            SearchResult.Shortcut(favorite.packageName, favorite.shortcutId, favorite.user, favorite.label, favorite.appLabel, score = 0),
        )
        else -> favorite.intent()?.let(actions::openIntent)
    }
}
