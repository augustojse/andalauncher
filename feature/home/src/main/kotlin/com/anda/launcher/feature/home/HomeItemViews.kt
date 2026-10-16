package com.anda.launcher.feature.home

import android.appwidget.AppWidgetHostView
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.anda.launcher.core.model.AppInfo
import com.anda.launcher.core.model.AppItem
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.FolderItem
import com.anda.launcher.core.model.FolderPreview
import com.anda.launcher.core.model.HomeItem
import com.anda.launcher.core.model.IconStyle
import com.anda.launcher.core.model.PackageUserKey
import com.anda.launcher.core.model.ShortcutItem
import com.anda.launcher.core.model.WidgetItem
import com.anda.launcher.core.model.WidgetStackItem
import com.anda.launcher.core.model.packageUser
import com.anda.launcher.core.ui.LocalLauncherSettings
import com.anda.launcher.core.ui.drag.DragPayload
import com.anda.launcher.core.ui.drag.LocalDragController
import com.anda.launcher.core.ui.gesture.ItemGestureCallbacks
import com.anda.launcher.core.ui.gesture.launcherItemGestures
import com.anda.launcher.core.ui.gesture.pressScale
import com.anda.launcher.core.ui.gesture.rememberItemFeedback
import com.anda.launcher.core.ui.icon.AppIconTile
import com.anda.launcher.core.ui.icon.IconLabel
import com.anda.launcher.core.ui.icon.NotificationBadge
import com.anda.launcher.core.ui.icon.OnWallpaperLabel
import com.anda.launcher.core.ui.icon.iconSize
import com.anda.launcher.core.ui.icon.rememberAppIcon
import com.anda.launcher.core.ui.icon.rememberShortcutIcon
import com.anda.launcher.core.ui.theme.toColorOr
import com.anda.launcher.feature.home.widget.LauncherWidgetHostView
import com.anda.launcher.feature.home.widget.WidgetTouchListener

/** Resolves installed-app info for items (labels, icon versions, work badge). */
val LocalAppLookup = compositionLocalOf<(ComponentKey) -> AppInfo?> { { null } }

/** Mutable holder for a composable's latest root-space coordinates (no recomposition). */
internal class CoordsRef {
    var coords: LayoutCoordinates? = null
    fun bounds(): Rect = coords?.takeIf { it.isAttached }?.boundsInRoot() ?: Rect.Zero
    fun toRoot(local: Offset): Offset = coords?.takeIf { it.isAttached }?.localToRoot(local) ?: local
}

/** Renders any home item with all of its gestures. */
@Composable
internal fun HomeItemView(
    item: HomeItem,
    controller: HomeController,
    style: IconStyle,
    badges: Map<PackageUserKey, Int>,
    onEdit: (HomeItem) -> Unit,
    modifier: Modifier = Modifier,
    defaultLabelColor: Color = OnWallpaperLabel,
) {
    val drag = LocalDragController.current
    val ref = remember { CoordsRef() }
    // Hide the original while it's being dragged; read in the draw phase only.
    val alpha: () -> Float = { if ((drag.payload as? DragPayload.Existing)?.item?.id == item.id) 0f else 1f }

    when (item) {
        is AppItem, is ShortcutItem, is FolderItem -> {
            val icon = itemIcon(item)
            val iconPx = with(LocalDensity.current) { style.iconSize().toPx() }
            val callbacks = remember(item, icon, iconPx) {
                object : ItemGestureCallbacks {
                    override fun onTap(position: Offset) = controller.onItemTap(item, ref.bounds())
                    override fun onLongPress(position: Offset) = controller.showMenu(item, ref.bounds()) { onEdit(item) }
                    override fun onDragStart(position: Offset) = controller.startDrag(
                        item = item,
                        icon = icon,
                        iconSize = style.iconSize(),
                        rootPosition = ref.toRoot(position),
                        touchOffset = Offset(iconPx / 2, iconPx / 2),
                    )
                    override fun onDrag(delta: Offset, position: Offset) = drag.moveTo(ref.toRoot(position))
                    override fun onSwipeUp(): Boolean = controller.onItemSwipeUp(item, ref.bounds())
                }
            }
            val feedback = rememberItemFeedback()
            Box(
                modifier
                    .onGloballyPositioned { ref.coords = it }
                    .launcherItemGestures(item.id, callbacks, feedback)
                    .pressScale(feedback)
                    .semantics { customActions = itemA11yActions(item, controller) { onEdit(item) } }
                    .graphicsLayer { this.alpha = alpha() },
                contentAlignment = Alignment.Center,
            ) {
                when (item) {
                    is FolderItem -> FolderIcon(item, style, badges, defaultLabelColor)
                    is AppItem -> {
                        val app = LocalAppLookup.current(item.key)
                        val settings = LocalLauncherSettings.current
                        AppIconTile(
                            icon = icon,
                            label = item.title ?: app?.label ?: "",
                            style = style,
                            defaultLabelColor = defaultLabelColor,
                            badgeCount = badges[item.key.packageUser] ?: 0,
                            badgeMode = settings.badges.mode,
                            badgeColor = settings.badges.color.toColorOr(MaterialTheme.colorScheme.primary),
                            isWork = app?.isWorkProfile == true,
                        )
                    }
                    is ShortcutItem -> AppIconTile(icon = icon, label = item.title, style = style, defaultLabelColor = defaultLabelColor)
                    else -> Unit
                }
            }
        }
        is WidgetItem -> WidgetView(item, controller, ref, modifier.graphicsLayer { this.alpha = alpha() }, onEdit)
        is WidgetStackItem -> WidgetStackView(item, controller, ref, modifier.graphicsLayer { this.alpha = alpha() }, onEdit)
    }
}

@Composable
internal fun itemIcon(item: HomeItem): ImageBitmap? = when (item) {
    is AppItem -> {
        val app = LocalAppLookup.current(item.key)
        rememberAppIcon(item.key, app?.lastUpdateTime ?: 0L, null)
    }
    is ShortcutItem -> rememberShortcutIcon(item.packageName, item.shortcutId, item.user)
    else -> null
}

private fun itemA11yActions(item: HomeItem, controller: HomeController, onEdit: () -> Unit): List<CustomAccessibilityAction> = buildList {
    add(CustomAccessibilityAction("Remove") { controller.remove(item); true })
    if (item !is WidgetItem && item !is WidgetStackItem) add(CustomAccessibilityAction("Edit") { onEdit(); true })
    add(CustomAccessibilityAction("Show menu") { controller.showMenu(item, Rect.Zero, onEdit); true })
}

// ---- Folders -------------------------------------------------------------------------------------

@Composable
internal fun FolderIcon(folder: FolderItem, style: IconStyle, badges: Map<PackageUserKey, Int>, labelColor: Color = OnWallpaperLabel) {
    val settings = LocalLauncherSettings.current
    val size = style.iconSize()
    val previewItems = folder.children.take(if (settings.folders.preview == FolderPreview.Grid) 4 else 3)
    val badgeCount = folder.children.sumOf { child -> (child as? AppItem)?.let { badges[it.key.packageUser] } ?: 0 }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            if (settings.folders.showIconBackground) {
                Box(
                    Modifier.fillMaxSize().padding(size * 0.04f).clip(CircleShape)
                        .background(settings.folders.backgroundColor.toColorOr(MaterialTheme.colorScheme.surfaceContainerHighest).copy(alpha = 0.85f)),
                )
            }
            when (settings.folders.preview) {
                FolderPreview.Grid -> Column(verticalArrangement = Arrangement.spacedBy(size * 0.04f)) {
                    previewItems.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(size * 0.04f)) {
                            row.forEach { MiniIcon(it, size * 0.34f) }
                        }
                    }
                }
                FolderPreview.Stack -> Box(Modifier.size(size * 0.72f)) {
                    previewItems.reversed().forEachIndexed { i, child ->
                        val shift = size * 0.08f * (previewItems.size - 1 - i)
                        Box(Modifier.align(Alignment.BottomStart).offset(x = shift, y = -shift)) { MiniIcon(child, size * 0.52f) }
                    }
                }
                FolderPreview.Fan -> Box(contentAlignment = Alignment.Center) {
                    previewItems.forEachIndexed { i, child ->
                        val angle = (i - (previewItems.size - 1) / 2f) * 18f
                        Box(Modifier.graphicsLayer { rotationZ = angle; transformOrigin = TransformOrigin(0.5f, 1.2f) }) {
                            MiniIcon(child, size * 0.46f)
                        }
                    }
                }
                FolderPreview.Line -> Row(horizontalArrangement = Arrangement.spacedBy(size * 0.02f)) {
                    previewItems.forEach { MiniIcon(it, size * 0.28f) }
                }
            }
            NotificationBadge(
                badgeCount, settings.badges.mode,
                settings.badges.color.toColorOr(MaterialTheme.colorScheme.primary), size, Modifier.align(Alignment.TopEnd),
            )
        }
        if (style.showLabels) {
            Spacer(Modifier.height(4.dp))
            IconLabel(folder.title, style, labelColor)
        }
    }
}

@Composable
private fun MiniIcon(item: HomeItem, size: Dp) {
    val icon = itemIcon(item)
    if (icon != null) {
        Image(icon, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

// ---- Widgets -------------------------------------------------------------------------------------

/** Per-widget mutable state that must survive recomposition but never trigger it. */
private class WidgetRuntime {
    var view: View? = null
    var dragging = false
    var last = Offset.Zero
}

@Composable
private fun WidgetView(item: WidgetItem, controller: HomeController, ref: CoordsRef, modifier: Modifier, onEdit: (HomeItem) -> Unit) {
    val drag = LocalDragController.current
    val density = LocalDensity.current
    val padding = LocalLauncherSettings.current.home.widgetPaddingDp.dp
    val info = remember(item.appWidgetId) { controller.widgets.info(item.appWidgetId) }
    if (controller.safeMode) {
        Box(
            modifier.padding(padding).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { Text("Widget paused (safe mode)", style = MaterialTheme.typography.bodySmall) }
        return
    }
    if (info == null) {
        // The bound id is gone (e.g. a backup restored on another device): removable placeholder.
        Box(
            modifier.padding(padding).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                .onGloballyPositioned { ref.coords = it }
                .launcherItemGestures(
                    item.id,
                    object : ItemGestureCallbacks {
                        override fun onTap(position: Offset) = controller.remove(item)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { Text("Widget unavailable — tap to remove", style = MaterialTheme.typography.bodySmall) }
        return
    }
    val runtime = remember(item.appWidgetId) { WidgetRuntime() }
    Box(
        modifier.padding(padding)
            .onGloballyPositioned { ref.coords = it }
            .onSizeChanged { size ->
                (runtime.view as? AppWidgetHostView)?.let { view ->
                    with(density) { controller.widgets.updateSize(view, size.width.toDp().value, size.height.toDp().value) }
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                val view = controller.widgets.view(ctx, item.appWidgetId) ?: View(ctx)
                (view as? LauncherWidgetHostView)?.touchListener = object : WidgetTouchListener {
                    override fun onLongPress(rawX: Float, rawY: Float) {
                        runtime.last = Offset(rawX, rawY)
                        runtime.dragging = false
                        controller.showMenu(item, ref.bounds()) { onEdit(item) }
                    }

                    override fun onMove(rawX: Float, rawY: Float) {
                        val now = Offset(rawX, rawY)
                        if (!runtime.dragging && (now - runtime.last).getDistance() > DRAG_START_PX) {
                            runtime.dragging = true
                            val bounds = ref.bounds()
                            controller.startDrag(item, null, with(density) { bounds.width.toDp() }, now, now - bounds.topLeft)
                        }
                        if (runtime.dragging) drag.moveTo(now)
                    }

                    override fun onRelease() {
                        if (runtime.dragging) drag.drop()
                        runtime.dragging = false
                    }
                }
                runtime.view = view
                view
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val DRAG_START_PX = 24f

@Composable
private fun WidgetStackView(item: WidgetStackItem, controller: HomeController, ref: CoordsRef, modifier: Modifier, onEdit: (HomeItem) -> Unit) {
    val pager = rememberPagerState { item.widgets.size }
    Box(modifier.onGloballyPositioned { ref.coords = it }) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { item.widgets[it].id }) { page ->
            HomeItemView(
                item = item.widgets[page],
                controller = controller,
                style = IconStyle(),
                badges = emptyMap(),
                onEdit = onEdit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 2.dp).wrapContentSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(item.widgets.size) { i ->
                Box(
                    Modifier.size(5.dp).clip(CircleShape).background(
                        if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
        }
    }
}
