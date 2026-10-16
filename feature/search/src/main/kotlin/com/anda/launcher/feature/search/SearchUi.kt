package com.anda.launcher.feature.search

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.SearchBarStyle
import com.anda.launcher.core.ui.TestTags
import androidx.compose.ui.platform.testTag
import com.anda.launcher.core.ui.icon.rememberShortcutIcon
import androidx.compose.foundation.Image

fun SearchBarStyle.shape(): Shape = when (this) {
    SearchBarStyle.Pill -> CircleShape
    SearchBarStyle.Rounded -> RoundedCornerShape(16.dp)
    SearchBarStyle.Square -> RoundedCornerShape(4.dp)
}

/** Home-screen search pill (opens the drawer search, Pixel-style). */
@Composable
fun SearchBarPill(
    style: SearchBarStyle,
    onClick: () -> Unit,
    onVoice: () -> Unit,
    onLens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = style.shape(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = onVoice) { Icon(Icons.Filled.Mic, contentDescription = "Voice search") }
            IconButton(onClick = onLens) { Icon(Icons.Filled.CameraAlt, contentDescription = "Lens") }
        }
    }
}

/** Editable search field used at the top (or bottom) of the drawer. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    focusRequester: FocusRequester,
    style: SearchBarStyle,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
        placeholder = { Text("Search apps and more", maxLines = 1) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
            } else {
                trailing?.invoke()
            }
        },
        singleLine = true,
        shape = style.shape(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onSubmit() }),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
}

/** Callbacks for non-app results; apps are rendered by the caller so they stay draggable. */
interface SearchResultActions {
    fun openIntent(intent: Intent)
    fun openShortcut(result: SearchResult.Shortcut)
    fun copyToClipboard(text: String)
}

/**
 * Results list: app grid first (rendered via [appRow]), then cards for everything else.
 */
@Composable
fun SearchResultsList(
    results: SearchResults,
    columns: Int,
    actions: SearchResultActions,
    appRow: @Composable (List<AppInfo>) -> Unit,
    modifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
) {
    LazyColumn(modifier = modifier.testTag(TestTags.SEARCH_RESULTS), reverseLayout = reverseLayout, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
        val appChunks = results.apps.map { it.app }.chunked(columns)
        items(appChunks.size, key = { "apps$it" }) { index -> appRow(appChunks[index]) }
        resultItems(results.others, actions)
    }
}

private fun LazyListScope.resultItems(others: List<SearchResult>, actions: SearchResultActions) {
    items(others, key = { it.hashCode() }) { result ->
        when (result) {
            is SearchResult.Calculation -> ResultRow(
                icon = { Icon(Icons.Filled.Calculate, null) },
                title = "= ${result.result}",
                subtitle = result.expression,
                onClick = { actions.copyToClipboard(result.result) },
                emphasized = true,
            )
            is SearchResult.Conversion -> ResultRow(
                icon = { Icon(Icons.Filled.Straighten, null) },
                title = result.line,
                subtitle = "Tap to copy",
                onClick = { actions.copyToClipboard(result.line.substringAfter("= ")) },
                emphasized = true,
            )
            is SearchResult.Shortcut -> ResultRow(
                icon = {
                    val icon = rememberShortcutIcon(result.packageName, result.shortcutId, result.user)
                    if (icon != null) Image(icon, null, Modifier.size(32.dp)) else Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                },
                title = result.label,
                subtitle = result.appLabel,
                onClick = { actions.openShortcut(result) },
            )
            is SearchResult.Contact -> ResultRow(
                icon = { Icon(Icons.Filled.Person, null) },
                title = result.name,
                subtitle = "Contact",
                onClick = { actions.openIntent(Intent(Intent.ACTION_VIEW, result.lookupUri)) },
            )
            is SearchResult.SystemSetting -> ResultRow(
                icon = { Icon(Icons.Filled.Settings, null) },
                title = result.title,
                subtitle = "Settings",
                onClick = { actions.openIntent(Intent(result.action)) },
            )
            is SearchResult.Web -> ResultRow(
                icon = { Icon(Icons.Filled.Search, null) },
                title = "Search the web for \"${result.query}\"",
                subtitle = null,
                onClick = { actions.openIntent(WebSearch.intent(result.query)) },
            )
            is SearchResult.App -> Unit
        }
    }
}

@Composable
private fun ResultRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (emphasized) FontWeight.SemiBold else null, maxLines = 2)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
