package com.anda.launcher.core.icons

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import com.anda.launcher.core.apps.AppsRepository
import com.anda.launcher.core.apps.ShortcutRepository
import com.anda.launcher.core.model.AppIconOverride
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.CustomShape
import com.anda.launcher.core.model.IconColor
import com.anda.launcher.core.model.IconColorRole
import com.anda.launcher.core.model.IconEdit
import com.anda.launcher.core.model.IconShapeKind
import com.anda.launcher.core.model.IconSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Calendar

/**
 * Icon pipeline: memory LRU → disk cache → render (docs/RND.md). Bitmaps are converted to
 * HARDWARE config so they live in graphics memory instead of the Java heap.
 *
 * Per-app exceptions ([Spec.perApp]) are part of that app's cache key only, so adding one
 * re-renders that app and nothing else.
 */
class IconRepository(
    private val context: Context,
    private val apps: AppsRepository,
    private val shortcuts: ShortcutRepository,
) {
    data class Spec(
        val shape: IconShapeKind = IconShapeKind.System,
        val iconPack: String? = null,
        val themed: Boolean = false,
        val themedFallback: Boolean = true,
        val normalizeLegacy: Boolean = true,
        val sizePx: Int = 160,
        val darkTheme: Boolean = false,
        val customShape: CustomShape = CustomShape(),
        /** The icon editor's global style. */
        val style: IconEdit = IconEdit.Plain,
        /** Exceptions keyed by [ComponentKey.flatten]. */
        val perApp: Map<String, AppIconOverride> = emptyMap(),
        /** The launcher's theme colors, indexed by [IconColorRole.ordinal]. */
        val palette: List<Int> = emptyList(),
    ) {
        /** Only icons that use theme colors re-render when the theme changes. */
        private val usesPalette: Boolean
            get() = themed || style.usesPalette || perApp.values.any { it.themed == true || it.edit?.usesPalette == true }

        val cacheTag: String by lazy {
            val custom = if (shape == IconShapeKind.Custom || perApp.values.any { it.edit?.shape == IconShapeKind.Custom }) customShape.toString() else ""
            val styleTag = if (style.isPlain) "" else style.toString()
            val paletteTag = if (usesPalette) palette.hashCode() else 0
            "r$RENDER_VERSION|${shape.ordinal}|$iconPack|$themed|$themedFallback|$normalizeLegacy|$sizePx|$darkTheme|$custom|$styleTag|$paletteTag"
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val renderDispatcher = Dispatchers.Default.limitedParallelism(4)
    private val packMutex = Mutex()
    private val diskDir = File(context.cacheDir, "icons").apply { mkdirs() }
    private val density = context.resources.displayMetrics.densityDpi

    @Volatile private var spec = Spec()
    @Volatile private var pack: IconPack? = null
    @Volatile private var shapePath: Path = IconShapes.path(spec.shape, spec.sizePx.toFloat())

    /** Packs used by exceptions or the picker, besides the global one. Guarded by [packMutex]. */
    private val otherPacks = HashMap<String, IconPack?>()

    private val _version = MutableStateFlow(0)

    /** Changes whenever cached icons become invalid; UI keys its icon state on it. */
    val version: StateFlow<Int> = _version

    private val memory = object : LruCache<String, Bitmap>(memoryBudgetKb()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
    }

    val currentSpec: Spec get() = spec

    suspend fun updateSpec(next: Spec) {
        if (next == spec) return
        withContext(renderDispatcher) {
            packMutex.withLock {
                if (next.iconPack != spec.iconPack) {
                    pack = next.iconPack?.let { IconPack.load(context, it) }
                }
                shapePath = IconShapes.path(next.shape, next.sizePx.toFloat(), next.customShape)
                // A new exception changes only that app's key; everything else stays cached.
                if (next.cacheTag != spec.cacheTag) memory.evictAll()
                spec = next
            }
        }
        _version.update { it + 1 }
    }

    /** Call when the theme's dark mode or wallpaper colors change (themed icon colors). */
    fun invalidateAll() {
        memory.evictAll()
        _version.update { it + 1 }
    }

    fun appKey(key: ComponentKey, lastUpdate: Long): String {
        val calendarDay = if (pack?.isCalendar(key.packageName, key.className) == true) {
            Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        } else {
            0
        }
        val exception = spec.perApp[key.flatten()]?.toString().orEmpty()
        return "app|${key.flatten()}|$lastUpdate|$calendarDay|${spec.cacheTag}|$exception"
    }

    /** Synchronous memory lookup so already-loaded icons render in the first frame. */
    fun peek(cacheKey: String): Bitmap? = memory.get(cacheKey)

    suspend fun appIcon(key: ComponentKey, lastUpdate: Long, customIconPath: String? = null): Bitmap? {
        if (customIconPath != null) return customIcon(customIconPath)
        val cacheKey = appKey(key, lastUpdate)
        memory.get(cacheKey)?.let { return it }
        return withContext(renderDispatcher) {
            loadFromDisk(cacheKey)?.let { return@withContext it.also { memory.put(cacheKey, it) } }
            val info = apps.activityInfo(key) ?: return@withContext null
            // Snapshot both: updateSpec() may swap them on another thread mid-render.
            val currentSpec = spec
            val currentPack = pack
            val bitmap = try {
                renderApp(key, info, currentSpec, currentPack, currentSpec.perApp[key.flatten()], currentSpec.sizePx, shapePath)
            } catch (e: RuntimeException) {
                // A third-party drawable that throws while drawing (broken vector, bad pack
                // resource) must not take down the launcher: fall back to the system default icon.
                Log.w(TAG, "Icon render failed for ${key.packageName}", e)
                renderSystem(context.packageManager.defaultActivityIcon, currentSpec, currentSpec.style, shapePath, currentSpec.themed)
            }
            saveToDisk(cacheKey, bitmap)
            bitmap.toHardware().also { memory.put(cacheKey, it) }
        }
    }

    /**
     * One app's icon as [spec] and [exception] say: the exception's source (the app's own icon,
     * the global pack, or another pack and maybe one chosen drawable), its themed choice, and its
     * own style, or the global style.
     */
    @Suppress("LongParameterList")
    private suspend fun renderApp(
        key: ComponentKey,
        info: LauncherActivityInfo,
        s: Spec,
        globalPack: IconPack?,
        exception: AppIconOverride?,
        sizePx: Int,
        globalShape: Path?,
    ): Bitmap {
        val sourcePack = when (exception?.source) {
            IconSource.App -> null
            IconSource.Pack -> exception.pack?.let { if (it == s.iconPack) globalPack else otherPack(it) }
            IconSource.Default, null -> globalPack
        }
        val chosen = exception?.drawable?.let { sourcePack?.drawable(it, density) }
        val packIcon = chosen ?: sourcePack?.iconFor(key.packageName, key.className, density)
        val edit = exception?.edit ?: s.style
        val shapeKind = edit.shape ?: s.shape
        val shape = if (exception?.edit == null && globalShape != null && sizePx == s.sizePx) {
            globalShape
        } else {
            IconShapes.path(shapeKind, sizePx.toFloat(), edit.customShape ?: s.customShape)
        }
        val themed = exception?.themed ?: s.themed
        return when {
            packIcon != null -> IconRenderer.renderFlat(packIcon, sizePx, shape, edit, colorResolver(s))
            sourcePack != null && sourcePack.filter.hasFallbackMask && !themed && edit.isPlain -> IconRenderer.renderWithPackMask(
                original = info.getIcon(density),
                size = sizePx,
                back = sourcePack.back(density, key.packageName.hashCode()),
                mask = sourcePack.mask(density),
                upon = sourcePack.upon(density),
                scale = sourcePack.filter.scale,
            )
            else -> renderSystem(info.getIcon(density), s.copy(sizePx = sizePx), edit, shape, themed)
        }
    }

    suspend fun shortcutIcon(packageName: String, shortcutId: String, user: Long): Bitmap? {
        val cacheKey = "shortcut|$packageName|$shortcutId|$user|${spec.cacheTag}"
        memory.get(cacheKey)?.let { return it }
        return withContext(renderDispatcher) {
            val info = shortcuts.find(packageName, shortcutId, user) ?: return@withContext null
            val drawable = shortcuts.icon(info) ?: return@withContext null
            val s = spec
            val bitmap = runCatching { renderSystem(drawable, s, s.style, shapePath, s.themed) }.getOrNull() ?: return@withContext null
            bitmap.toHardware().also { memory.put(cacheKey, it) }
        }
    }

    /** Renders an arbitrary drawable (e.g. a shortcut info icon) in the current icon style. */
    suspend fun render(drawable: Drawable): Bitmap = withContext(renderDispatcher) {
        val s = spec
        renderSystem(drawable, s, s.style, shapePath, s.themed).toHardware()
    }

    suspend fun customIcon(path: String): Bitmap? {
        val cacheKey = "custom|$path|${File(path).lastModified()}"
        memory.get(cacheKey)?.let { return it }
        return withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
            BitmapFactory.decodeFile(path, options)?.also { memory.put(cacheKey, it) }
        }
    }

    /**
     * One app's icon rendered in [shape], for the settings shape picker (so every option shows a
     * real icon, not an abstract outline). Not cached; callers render a handful at most.
     */
    suspend fun shapePreview(key: ComponentKey, shape: IconShapeKind, sizePx: Int): Bitmap? =
        preview(key, sizePx, spec.copy(shape = shape), exception = null)

    /**
     * The icon editor's live preview: [key] rendered with a spec that isn't saved yet ([draft]
     * holds the shape, style and theme colors being edited) and an optional [exception]. Not cached.
     */
    suspend fun preview(key: ComponentKey, sizePx: Int, draft: Spec, exception: AppIconOverride?): Bitmap? =
        withContext(renderDispatcher) {
            val info = apps.activityInfo(key) ?: return@withContext null
            val draftPack = when (draft.iconPack) {
                null -> null
                spec.iconPack -> pack
                else -> otherPack(draft.iconPack)
            }
            runCatching { renderApp(key, info, draft.copy(sizePx = sizePx), draftPack, exception, sizePx, globalShape = null) }
                .onFailure { Log.w(TAG, "Preview failed for ${key.packageName}", it) }
                .getOrNull()
        }

    /** Every icon a pack offers (icon picker). Loads the pack if it isn't loaded yet. */
    suspend fun packCatalog(packageName: String): List<PackIcon> = withContext(renderDispatcher) {
        loadedPack(packageName)?.catalog.orEmpty()
    }

    /** The drawable [packageName] maps [key] to, so the picker can show it first. */
    suspend fun packMatch(packageName: String, key: ComponentKey): String? = withContext(renderDispatcher) {
        loadedPack(packageName)?.drawableNameFor(key.packageName, key.className)
    }

    /** One pack drawable as a bitmap, for the icon picker grid. Cached in memory. */
    suspend fun packIcon(packageName: String, name: String, sizePx: Int): Bitmap? {
        val cacheKey = "pack|$packageName|$name|$sizePx"
        memory.get(cacheKey)?.let { return it }
        return withContext(renderDispatcher) {
            val drawable = loadedPack(packageName)?.drawable(name, density) ?: return@withContext null
            runCatching { IconRenderer.renderPlain(drawable, sizePx).toHardware() }.getOrNull()?.also { memory.put(cacheKey, it) }
        }
    }

    /** Raw drawable from the current icon pack, for the "pick icon from pack" dialog. */
    fun packDrawable(name: String): Drawable? = pack?.drawable(name, density)

    fun packDrawableNames(): List<String> = pack?.filter?.components?.values?.distinct()?.sorted().orEmpty()

    private suspend fun loadedPack(packageName: String): IconPack? =
        if (packageName == spec.iconPack) pack else otherPack(packageName)

    private suspend fun otherPack(packageName: String): IconPack? = packMutex.withLock {
        otherPacks.getOrPut(packageName) { IconPack.load(context, packageName) }
    }

    private fun renderSystem(drawable: Drawable, s: Spec, edit: IconEdit, shape: Path, themed: Boolean): Bitmap = IconRenderer.render(
        drawable = drawable,
        size = s.sizePx,
        shape = shape,
        themed = if (themed) themedColors(s) else null,
        themedFallback = s.themedFallback,
        normalizeLegacy = s.normalizeLegacy,
        edit = edit,
        colors = colorResolver(s),
    )

    /** Theme roles from the spec's palette; system Material You colors until the theme sends one. */
    private fun colorResolver(s: Spec): (IconColor) -> Int = { color ->
        color.role?.let { role -> s.palette.getOrNull(role.ordinal) ?: systemColor(role, s.darkTheme) }
            ?: color.argb?.toInt()
            ?: Color.GRAY
    }

    /** Themed icons: the launcher's own scheme, so they follow a custom or OLED theme too. */
    private fun themedColors(s: Spec): ThemedColors {
        val resolve = colorResolver(s)
        return if (s.darkTheme) {
            ThemedColors(resolve(IconColor.of(IconColorRole.SurfaceContainerHighest)), resolve(IconColor.of(IconColorRole.Primary)))
        } else {
            ThemedColors(resolve(IconColor.of(IconColorRole.PrimaryContainer)), resolve(IconColor.of(IconColorRole.OnPrimaryContainer)))
        }
    }

    private fun systemColor(role: IconColorRole, dark: Boolean): Int {
        val id = when (role) {
            IconColorRole.Primary -> if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
            IconColorRole.OnPrimary -> if (dark) android.R.color.system_accent1_800 else android.R.color.system_accent1_0
            IconColorRole.PrimaryContainer -> if (dark) android.R.color.system_accent1_700 else android.R.color.system_accent1_100
            IconColorRole.OnPrimaryContainer -> if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_900
            IconColorRole.Secondary -> if (dark) android.R.color.system_accent2_200 else android.R.color.system_accent2_600
            IconColorRole.SecondaryContainer -> if (dark) android.R.color.system_accent2_700 else android.R.color.system_accent2_100
            IconColorRole.OnSecondaryContainer -> if (dark) android.R.color.system_accent2_100 else android.R.color.system_accent2_900
            IconColorRole.Tertiary -> if (dark) android.R.color.system_accent3_200 else android.R.color.system_accent3_600
            IconColorRole.TertiaryContainer -> if (dark) android.R.color.system_accent3_700 else android.R.color.system_accent3_100
            IconColorRole.OnTertiaryContainer -> if (dark) android.R.color.system_accent3_100 else android.R.color.system_accent3_900
            IconColorRole.Surface -> if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_10
            IconColorRole.SurfaceContainerHighest -> if (dark) android.R.color.system_neutral1_800 else android.R.color.system_neutral2_100
            IconColorRole.OnSurface -> if (dark) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900
            IconColorRole.InverseSurface -> if (dark) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_800
            IconColorRole.InverseOnSurface -> if (dark) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_50
        }
        return context.getColor(id)
    }

    private fun diskFile(cacheKey: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(cacheKey.toByteArray())
        return File(diskDir, digest.joinToString("") { "%02x".format(it) } + ".webp")
    }

    private fun loadFromDisk(cacheKey: String): Bitmap? {
        val file = diskFile(cacheKey)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
        return BitmapFactory.decodeFile(file.path, options)
    }

    private fun saveToDisk(cacheKey: String, bitmap: Bitmap) {
        runCatching {
            diskFile(cacheKey).outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }
        }
    }

    private fun Bitmap.toHardware(): Bitmap = copy(Bitmap.Config.HARDWARE, false) ?: this

    /** Drops every rendered icon, in memory and on disk (Settings → Icons → Clear icon cache). */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) { diskDir.listFiles()?.forEach { it.delete() } }
        invalidateAll()
    }

    /** Trims stale disk entries (e.g. after many spec changes). */
    fun trimDiskCache(maxFiles: Int = 3000) {
        val files = diskDir.listFiles() ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }.take(files.size - maxFiles).forEach { it.delete() }
    }

    private fun memoryBudgetKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()

    private companion object {
        const val TAG = "AndaIcons"

        /** Bump when IconRenderer draws differently, so cached icons are drawn again. */
        const val RENDER_VERSION = 2
    }
}
