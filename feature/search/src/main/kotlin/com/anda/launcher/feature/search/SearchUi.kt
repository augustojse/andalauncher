package com.anda.launcher.feature.search

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.anda.launcher.core.model.Favorite
import com.anda.launcher.core.model.SearchBarStyle

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

    /** Pin to, or remove from, the drawer's Favorites row. */
    fun setPinned(favorite: Favorite, pinned: Boolean)
}
