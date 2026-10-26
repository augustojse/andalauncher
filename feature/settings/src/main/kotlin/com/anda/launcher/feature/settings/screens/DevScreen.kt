package com.anda.launcher.feature.settings.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.anda.launcher.core.data.DevNote
import com.anda.launcher.core.ui.LocalLauncherGraph
import com.anda.launcher.feature.settings.components.controls.NavRow
import com.anda.launcher.feature.settings.components.layout.Hint
import com.anda.launcher.feature.settings.components.layout.SettingsGroup
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Notes and screenshots about anything misbehaving, for whoever works on the launcher. Kept on the
 * phone, in backups, and in Downloads/Anda Launcher/dev (see [DevNotes]).
 */
@Composable
internal fun DevScreen() {
    val context = LocalContext.current
    val notes = LocalLauncherGraph.current.devNotes
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val saved by produceState(emptyList<DevNote>(), refresh) { value = withContext(Dispatchers.IO) { notes.list() } }
    var text by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<DevNote?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        picked = (picked + uris.map { it.toString() }).distinct()
    }

    SettingsGroup("New note") {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("What's misbehaving? What did you do, and what did you expect?") },
            minLines = 4,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        picked.forEach { uri ->
            PickedFileRow(uri) { picked = picked - uri }
        }
        NavRow(
            "Add screenshots or files",
            if (picked.isEmpty()) "Screenshots are resized to keep backups small" else "${picked.size} attached",
            Icons.Filled.AttachFile,
        ) { pick.launch(arrayOf("image/*", "text/*", "*/*")) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(
                enabled = !saving && (text.isNotBlank() || picked.isNotEmpty()),
                onClick = {
                    saving = true
                    scope.launch {
                        val result = runCatching { notes.add(text, picked.map { it.toUri() }) }
                        saving = false
                        result.onSuccess { added ->
                            text = ""
                            picked = emptyList()
                            refresh++
                            val skipped = if (added.skipped.isEmpty()) "" else " (skipped ${added.skipped.joinToString()})"
                            Toast.makeText(context, "Note saved$skipped", Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, "Couldn't save the note", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            ) { Text(if (saving) "Saving…" else "Save note") }
        }
    }
    SettingsGroup(if (saved.isEmpty()) "Saved notes" else "Saved notes (${saved.size})") {
        if (saved.isEmpty()) {
            Text("None yet.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        saved.forEach { note -> key(note.id) { DevNoteRow(note) { confirmDelete = note } } }
    }
    Hint("Saved on this phone, included in backups, and copied to Downloads/Anda Launcher/dev. Nothing is sent anywhere.")

    confirmDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this note?") },
            text = { Text("The note, its files and its copy in Downloads are deleted. Older backups still contain it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        notes.delete(note)
                        refresh++
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PickedFileRow(uri: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    val name by produceState(uri.substringAfterLast('/'), uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(uri.toUri(), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: value
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove $name") }
    }
}

@Composable
private fun DevNoteRow(note: DevNote, onDelete: () -> Unit) {
    val notes = LocalLauncherGraph.current.devNotes
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.createdAt)), style = MaterialTheme.typography.titleSmall)
                Text("Version ${note.appVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete note") }
        }
        if (note.text.isNotBlank()) Text(note.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 12.dp))
        if (note.attachments.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                note.attachments.forEach { name ->
                    val thumb by produceState<ImageBitmap?>(null, note.id, name) {
                        value = withContext(Dispatchers.IO) { runCatching { notes.thumbnail(notes.attachment(note, name), 256)?.asImageBitmap() }.getOrNull() }
                    }
                    val shape = RoundedCornerShape(8.dp)
                    val current = thumb
                    if (current != null) {
                        Image(current, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(shape))
                    } else {
                        Box(Modifier.size(72.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(6.dp), contentAlignment = Alignment.Center) {
                            Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 3)
                        }
                    }
                }
            }
        }
    }
}
