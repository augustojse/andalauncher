package com.anda.launcher.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anda.launcher.core.model.FolderItem
import com.anda.launcher.core.model.FolderOpenStyle
import com.anda.launcher.core.model.PackageUserKey
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.core.ui.TestTags
import com.anda.launcher.core.ui.drag.DragPayload
import com.anda.launcher.core.ui.drag.DropTarget
import com.anda.launcher.core.ui.drag.LocalDragController
import com.anda.launcher.core.ui.theme.toColorOr

/**
 * Open folder. Rendered in the main window so dragging an icon out keeps the same gesture: the
 * panel fades once the pointer leaves it and closes after the drop.
 */
@Composable
fun FolderOverlay(controller: HomeController, badges: Map<PackageUserKey, Int>, host: HomeHost) {
    val folderId = controller.openFolderId ?: return
    val layout by controller.layout.collectAsState()
    val folder = layout.findItem(folderId) as? FolderItem
    if (folder == null) {
        LaunchedEffect(folderId) { controller.closeFolder() }
        return
    }
    val settings = LocalLauncherSettings.current
    val drag = LocalDragController.current
    val panelBounds = remember { arrayOf(Rect.Zero) }
    // Child bounds for drop-reorder; written during layout (no recomposition), reset per folder.
    val childBounds = remember(folderId) { ArrayList<Rect>() }
    // Open animation: the panel grows out of the folder icon that was tapped.
    val appear = remember(folderId) { Animatable(0f) }
    LaunchedEffect(folderId) { appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) }
    var wasDragging by remember { mutableStateOf(false) }
    val dragging = drag.isDragging
    LaunchedEffect(dragging) {
        if (dragging) wasDragging = true else if (wasDragging) controller.closeFolder()
    }
    BackHandler { controller.closeFolder() }

    // Dropping back inside the panel reorders (or adds) at the nearest position.
    DisposableEffect(folder) {
        drag.register("folder$folderId", { panelBounds[0] }, DropTarget { payload, position ->
            val existing = (payload as? DragPayload.Existing)?.item ?: return@DropTarget false
            val children = folder.children
            val nearest = children.indices.minByOrNull { i ->
                val b = childBounds.getOrNull(i) ?: return@minByOrNull Float.MAX_VALUE
                (b.center - position).getDistance()
            } ?: return@DropTarget false
            if (existing.container == folderId) {
                val ids = children.map { it.id }.toMutableList()
                ids.remove(existing.id)
                ids.add(nearest.coerceIn(0, ids.size), existing.id)
                controller.reorderFolder(folderId, ids)
            } else {
                controller.addToFolder(folderId, existing.id, nearest)
            }
            true
        })
        onDispose { drag.unregister("folder$folderId") }
    }

    val fullScreen = settings.folders.openStyle == FolderOpenStyle.FullScreen
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = if (drag.isDragging && !panelBounds[0].contains(drag.position)) 0f else appear.value.coerceIn(0f, 1f) }
            .background(Color.Black.copy(alpha = 0.25f))
            .pointerInput(Unit) { detectTapGestures { controller.closeFolder() } },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = if (fullScreen) RoundedCornerShape(0.dp) else MaterialTheme.shapes.extraLarge,
            color = settings.folders.backgroundColor.toColorOr(MaterialTheme.colorScheme.surfaceContainer)
                .copy(alpha = settings.folders.backgroundOpacity),
            modifier = (if (fullScreen) Modifier.fillMaxSize() else Modifier.padding(24.dp).widthIn(max = 420.dp))
                .testTag(TestTags.FOLDER_PANEL)
                .onGloballyPositioned { panelBounds[0] = it.boundsInRoot() }
                .graphicsLayer {
                    val panel = panelBounds[0]
                    val anchor = controller.folderAnchor
                    if (panel.width > 0f && anchor != Rect.Zero) {
                        transformOrigin = TransformOrigin(
                            ((anchor.center.x - panel.left) / panel.width).coerceIn(0f, 1f),
                            ((anchor.center.y - panel.top) / panel.height).coerceIn(0f, 1f),
                        )
                    }
                    val s = 0.6f + 0.4f * appear.value
                    scaleX = s
                    scaleY = s
                }
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(
                Modifier.then(if (fullScreen) Modifier.statusBarsPadding() else Modifier).padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Balances the gear so the title stays centered.
                    Box(Modifier.size(40.dp))
                    Box(Modifier.weight(1f)) { FolderTitle(folder, controller) }
                    IconButton(onClick = { controller.closeFolder(); host.openSettings("Folders") }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = "Folder settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val columns = settings.folders.columns.coerceAtLeast(2)
                val style = settings.icons.folder
                folder.children.chunked(columns).forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEachIndexed { colIndex, child ->
                            val index = rowIndex * columns + colIndex
                            // Keyed by item: reordering inside the folder must move each tile's state with it.
                            androidx.compose.runtime.key(child.id) {
                                Box(
                                    Modifier.weight(1f).onGloballyPositioned { coords ->
                                        while (childBounds.size <= index) childBounds.add(Rect.Zero)
                                        childBounds[index] = coords.boundsInRoot()
                                    },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    HomeItemView(
                                        item = child,
                                        controller = controller,
                                        style = style,
                                        badges = badges,
                                        onEdit = host::editItem,
                                        defaultLabelColor = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTitle(folder: FolderItem, controller: HomeController) {
    var title by remember(folder.id) { mutableStateOf(folder.title) }
    BasicTextField(
        value = title,
        onValueChange = { title = it },
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { controller.rename(folder.id, title) }),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    )
    DisposableEffect(folder.id) {
        onDispose { if (title != folder.title) controller.rename(folder.id, title) }
    }
}
