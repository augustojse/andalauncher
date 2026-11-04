package com.anda.launcher.feature.search

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Shortcut
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.Favorite
import com.anda.launcher.core.model.ItemKey
import com.anda.launcher.core.ui.TestTags
import com.anda.launcher.core.ui.icon.rememberShortcutIcon

/** What a filter chip narrows the results to (Kvaesitso's filter bar). */
enum class SearchCategory(val label: String, val icon: ImageVector) {
    Apps("Apps", Icons.Filled.Apps),
    Shortcuts("Shortcuts", Icons.AutoMirrored.Filled.Shortcut),
    Contacts("Contacts", Icons.Filled.Person),
    Events("Events", Icons.Filled.Event),
    Settings("Settings", Icons.Filled.Settings),
}

private fun SearchResult.category(): SearchCategory? = when (this) {
    is SearchResult.App -> SearchCategory.Apps
    is SearchResult.Shortcut -> SearchCategory.Shortcuts
    is SearchResult.Contact -> SearchCategory.Contacts
    is SearchResult.Event -> SearchCategory.Events
    is SearchResult.SystemSetting -> SearchCategory.Settings
    else -> null
}

/** Things to do with the query itself (call it, set an alarm, search the web): chips next to the search bar. */
fun SearchResults.chips(): List<SearchResult> = others.filter { it is SearchResult.Action || it is SearchResult.Web }

fun SearchResult.chipIntent(): Intent? = when (this) {
    is SearchResult.Action -> QueryActionIntents.intent(action)
    is SearchResult.Web -> if (engine == null) WebSearch.intent(query) else WebSearch.intent(query, engine)
    else -> null
}

/** List groups show this many rows until "Show more". */
private const val COLLAPSED_ROWS = 3

/**
 * The search page, after Kvaesitso: action chips by the search bar, then a featured card for a
 * calculation, then one card per kind of result (apps as a grid via [appRow], the rest as rows
 * joined into a stack), and category filters at the far end, above the keyboard. With
 * [reverseLayout] (search bar at the bottom) everything mirrors so the chips stay by the bar.
 */
@Composable
fun SearchResultsList(
    results: SearchResults,
    columns: Int,
    actions: SearchResultActions,
    appRow: @Composable (List<AppInfo>) -> Unit,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    showFilters: Boolean = true,
    /** What is in the Favorites row, so a long-press offers Unpin instead of Pin. */
    pinned: Set<ItemKey> = emptySet(),
) {
    var filter by remember { mutableStateOf<SearchCategory?>(null) }
    var expanded by remember(results.query) { mutableStateOf(emptySet<SearchCategory>()) }
    val chips = remember(results) { results.chips() }
    val featured = remember(results) { results.others.filter { it is SearchResult.Calculation || it is SearchResult.Conversion } }
    val groups = remember(results) {
        results.others.filter { it.category() != null }.groupBy { it.category()!! }.toSortedMap(compareBy { it.ordinal })
    }
    val available = buildList {
        if (results.apps.isNotEmpty()) add(SearchCategory.Apps)
        addAll(groups.keys)
    }
    // A filter whose category has no results for this query shows everything instead of nothing.
    val active = filter?.takeIf { it in available }

    val chipRow: @Composable () -> Unit = {
        if (chips.isNotEmpty()) ActionChips(chips, highlightFirst = results.apps.isEmpty(), actions)
    }
    val filterRow: @Composable () -> Unit = {
        if (showFilters && available.size > 1) FilterRow(available, active) { filter = if (filter == it) null else it }
    }
    Column(modifier) {
        if (reverseLayout) filterRow() else chipRow()
        LazyColumn(
            modifier = Modifier.weight(1f).testTag(TestTags.SEARCH_RESULTS),
            reverseLayout = reverseLayout,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            var sections = 0
            fun gap(key: String) {
                if (sections++ > 0) item(key = "gap-$key") { Spacer(Modifier.height(12.dp)) }
            }
            if (active == null) {
                featured.forEachIndexed { i, result ->
                    gap("featured$i")
                    item(key = "featured$i") { FeaturedCard(result, actions) }
                }
            }
            if (results.apps.isNotEmpty() && (active == null || active == SearchCategory.Apps)) {
                gap("apps")
                appGroup(results.apps.map { it.app }.chunked(columns), reverseLayout, appRow)
            }
            groups.forEach { (category, items) ->
                if (active != null && active != category) return@forEach
                gap(category.name)
                val collapsed = active == null && category !in expanded && items.size > COLLAPSED_ROWS + 1
                val shown = if (collapsed) items.take(COLLAPSED_ROWS) else items
                // "Show more" is the stack's last segment, so the rows above it keep inner corners.
                val segments = shown.size + if (collapsed) 1 else 0
                listGroup(category, shown, segments, reverseLayout, pinned, actions)
                if (collapsed) {
                    item(key = "${category.name}-more") {
                        ShowMoreRow(items.size - COLLAPSED_ROWS, segmentShape(shown.size, segments, reverseLayout)) {
                            expanded = expanded + category
                        }
                    }
                }
            }
            if (sections == 0) {
                item(key = "empty") { EmptyState(results.query, hasChips = chips.isNotEmpty(), chipsBelow = reverseLayout) }
            }
        }
        if (reverseLayout) chipRow() else filterRow()
    }
}

// ---- Groups --------------------------------------------------------------------------------------

private val OuterCorner = 24.dp
private val InnerCorner = 6.dp

/** One segment of a stacked card: big corners on the outside edges of the group, small inside. */
private fun segmentShape(isTop: Boolean, isBottom: Boolean, inner: Dp = InnerCorner): Shape = RoundedCornerShape(
    topStart = if (isTop) OuterCorner else inner,
    topEnd = if (isTop) OuterCorner else inner,
    bottomStart = if (isBottom) OuterCorner else inner,
    bottomEnd = if (isBottom) OuterCorner else inner,
)

/** In a reversed list the first item sits at the bottom of its group. */
private fun segmentShape(index: Int, count: Int, reverse: Boolean, inner: Dp = InnerCorner): Shape {
    val first = index == 0
    val last = index == count - 1
    return segmentShape(isTop = if (reverse) last else first, isBottom = if (reverse) first else last, inner = inner)
}

/** Apps: one seamless card holding the icon grid. */
private fun LazyListScope.appGroup(rows: List<List<AppInfo>>, reverse: Boolean, appRow: @Composable (List<AppInfo>) -> Unit) {
    items(rows.size, key = { "apps$it" }) { index ->
        val first = index == 0
        val last = index == rows.lastIndex
        val top = if (reverse) last else first
        val bottom = if (reverse) first else last
        Surface(
            shape = segmentShape(index, rows.size, reverse, inner = 0.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(top = if (top) 8.dp else 0.dp, bottom = if (bottom) 8.dp else 0.dp)) { appRow(rows[index]) }
        }
    }
}

/** Shortcuts, contacts, settings: rows joined into one stack with hairline gaps. */
private fun LazyListScope.listGroup(
    category: SearchCategory,
    items: List<SearchResult>,
    segments: Int,
    reverse: Boolean,
    pinned: Set<ItemKey>,
    actions: SearchResultActions,
) {
    items(items.size, key = { "${category.name}$it-${items[it].hashCode()}" }) { index ->
        ResultRow(items[index], segmentShape(index, segments, reverse), pinned, actions)
    }
}

/** One result; a long-press pins it to (or removes it from) the drawer's Favorites row. */
@Composable
private fun ResultRow(result: SearchResult, shape: Shape, pinned: Set<ItemKey>, actions: SearchResultActions) {
    val favorite = remember(result) { result.toFavorite() }
    var menu by remember { mutableStateOf(false) }
    val onLongClick: (() -> Unit)? = favorite?.let { { menu = true } }
    Box {
        ResultContent(result, shape, actions, onLongClick)
        if (favorite != null) {
            val isPinned = favorite.key in pinned
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (isPinned) "Remove from favorites" else "Pin to favorites") },
                    leadingIcon = { Icon(if (isPinned) Icons.Filled.StarBorder else Icons.Filled.Star, contentDescription = null) },
                    onClick = {
                        menu = false
                        actions.setPinned(favorite, !isPinned)
                    },
                )
                if (result is SearchResult.Shortcut) {
                    DropdownMenuItem(
                        text = { Text("Hide shortcuts from ${result.appLabel.ifBlank { "this app" }}") },
                        leadingIcon = { Icon(Icons.Filled.VisibilityOff, contentDescription = null) },
                        onClick = {
                            menu = false
                            actions.hideShortcutsOf(result.packageName)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultContent(result: SearchResult, shape: Shape, actions: SearchResultActions, onLongClick: (() -> Unit)?) {
    when (result) {
        is SearchResult.Shortcut -> SegmentRow(
            shape = shape,
            onLongClick = onLongClick,
            leading = {
                val icon = rememberShortcutIcon(result.packageName, result.shortcutId, result.user)
                if (icon != null) Image(icon, null, Modifier.size(36.dp)) else Avatar(Icons.AutoMirrored.Filled.ArrowForward)
            },
            title = result.label,
            subtitle = result.appLabel,
            onClick = { actions.openShortcut(result) },
        )
        is SearchResult.Contact -> SegmentRow(
            shape = shape,
            onLongClick = onLongClick,
            leading = { InitialAvatar(result.name) },
            title = result.name,
            subtitle = result.phone ?: "Contact",
            onClick = { actions.openIntent(Intent(Intent.ACTION_VIEW, result.lookupUri)) },
            trailing = result.phone?.let { phone ->
                {
                    // ACTION_DIAL and SENDTO open the dialer and messages app: no call permission.
                    Row {
                        ContactAction(Icons.AutoMirrored.Filled.Message, "Message ${result.name}") {
                            actions.openIntent(QueryActionIntents.intent(QueryAction.Message(phone)))
                        }
                        ContactAction(Icons.Filled.Call, "Call ${result.name}") {
                            actions.openIntent(QueryActionIntents.intent(QueryAction.Call(phone)))
                        }
                    }
                }
            },
        )
        is SearchResult.Event -> SegmentRow(
            shape = shape,
            onLongClick = onLongClick,
            leading = { EventAvatar(result.color) },
            title = result.title,
            subtitle = result.dateLine,
            onClick = { actions.openIntent(eventIntent(result.eventId, result.begin, result.end)) },
        )
        is SearchResult.SystemSetting -> SegmentRow(
            shape = shape,
            onLongClick = onLongClick,
            leading = { Avatar(Icons.Filled.Settings) },
            title = result.title,
            subtitle = "Android settings",
            onClick = { actions.openIntent(Intent(result.action)) },
        )
        else -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SegmentRow(
    shape: Shape,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Row(
            Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = onLongClick?.let { "Favorite options" })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}

@Composable
private fun ContactAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ShowMoreRow(hidden: Int, shape: Shape, onClick: () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Show $hidden more", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Avatar(icon: ImageVector, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun InitialAvatar(name: String, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        if (initial != null) {
            Text(initial.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/** The calendar's colour behind an event icon; the theme's when the calendar has none. */
@Composable
private fun EventAvatar(color: Int?, size: Dp = 40.dp) {
    if (color == null) return Avatar(Icons.Filled.Event, size)
    val background = Color(color).copy(alpha = 1f)
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Filled.Event,
            contentDescription = null,
            tint = if (background.luminance() > 0.5f) Color.Black else Color.White,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * The icon of a pinned shortcut, contact, event or settings page, the same as its search row's.
 * Apps are drawn by the caller with their themed launcher icon.
 */
@Composable
fun FavoriteIcon(favorite: Favorite, size: Dp) {
    when (favorite) {
        is Favorite.Shortcut -> {
            val icon = rememberShortcutIcon(favorite.packageName, favorite.shortcutId, favorite.user)
            if (icon != null) Image(icon, null, Modifier.size(size)) else Avatar(Icons.AutoMirrored.Filled.ArrowForward, size)
        }
        is Favorite.Contact -> InitialAvatar(favorite.label, size)
        is Favorite.Event -> EventAvatar(favorite.color, size)
        is Favorite.Setting -> Avatar(Icons.Filled.Settings, size)
        is Favorite.App -> Unit
    }
}

/** How a pinned contact, event or settings page opens; apps and shortcuts launch through their own paths. */
fun Favorite.intent(): Intent? = when (this) {
    is Favorite.Contact -> Intent(Intent.ACTION_VIEW, lookupUri.toUri())
    is Favorite.Event -> eventIntent(eventId, begin, end)
    is Favorite.Setting -> Intent(action)
    is Favorite.App, is Favorite.Shortcut -> null
}

/** The begin and end pick the occurrence when the event repeats. */
private fun eventIntent(eventId: Long, begin: Long, end: Long): Intent =
    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)

// ---- Featured: calculator and unit converter -----------------------------------------------------

/** The answer itself, large; tap copies it. */
@Composable
private fun FeaturedCard(result: SearchResult, actions: SearchResultActions) {
    val (icon, question, answer) = when (result) {
        is SearchResult.Calculation -> Triple(Icons.Filled.Calculate, result.expression, result.result)
        is SearchResult.Conversion -> Triple(Icons.Filled.Straighten, result.line.substringBefore(" = "), result.line.substringAfter(" = "))
        else -> return
    }
    Surface(
        shape = RoundedCornerShape(OuterCorner),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.clickable { actions.copyToClipboard(answer) }.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                Text(
                    question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "= $answer",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Text(
                "Tap to copy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

// ---- Chips ---------------------------------------------------------------------------------------

/**
 * Actions read from the query and web searches. When no app matches, the first one is
 * highlighted: Enter runs it.
 */
@Composable
private fun ActionChips(chips: List<SearchResult>, highlightFirst: Boolean, actions: SearchResultActions) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(chips.size, key = { "chip$it-${chips[it].hashCode()}" }) { index ->
            val chip = chips[index]
            val (icon, label) = when (chip) {
                is SearchResult.Action -> QueryActionIntents.icon(chip.action) to QueryActionIntents.title(chip.action)
                is SearchResult.Web -> if (chip.engine == null) Icons.Filled.Search to "Search the web" else Icons.Filled.Public to chip.engine.name
                else -> return@items
            }
            val highlighted = highlightFirst && index == 0
            AssistChip(
                onClick = { chip.chipIntent()?.let(actions::openIntent) },
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
                colors = if (highlighted) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                },
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = if (highlighted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

/** "All" plus one chip per category that has results; tapping the selected one again clears it. */
@Composable
private fun FilterRow(available: List<SearchCategory>, active: SearchCategory?, onToggle: (SearchCategory) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = active == null,
            onClick = { active?.let(onToggle) },
            label = { Text("All") },
        )
        available.forEach { category ->
            FilterChip(
                selected = active == category,
                onClick = { onToggle(category) },
                label = { Text(category.label) },
                leadingIcon = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
            )
        }
    }
}

@Composable
private fun EmptyState(query: String, hasChips: Boolean, chipsBelow: Boolean) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Nothing found for “$query”",
            style = MaterialTheme.typography.titleMedium,
            // Not inside a Surface, so the default content color would be black.
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (hasChips) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (chipsBelow) "Try an action below" else "Try an action above",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
