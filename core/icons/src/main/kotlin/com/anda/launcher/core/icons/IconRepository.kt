package com.anda.launcher.core.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import com.anda.launcher.core.apps.AppsRepository
import com.anda.launcher.core.apps.ShortcutRepository
import com.anda.launcher.core.model.ComponentKey
import com.anda.launcher.core.model.IconShapeKind
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
    ) {
        val cacheTag: String get() = "${shape.ordinal}|$iconPack|$themed|$themedFallback|$normalizeLegacy|$sizePx|$darkTheme"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val renderDispatcher = Dispatchers.Default.limitedParallelism(4)
    private val packMutex = Mutex()
    private val diskDir = File(context.cacheDir, "icons").apply { mkdirs() }
    private val density = context.resources.displayMetrics.densityDpi

    @Volatile private var spec = Spec()
    @Volatile private var pack: IconPack? = null
    @Volatile private var shapePath: Path = IconShapes.path(spec.shape, spec.sizePx.toFloat())

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
                shapePath = IconShapes.path(next.shape, next.sizePx.toFloat())
                spec = next
                memory.evictAll()
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
        return "app|${key.flatten()}|$lastUpdate|$calendarDay|${spec.cacheTag}"
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
                val packIcon = currentPack?.iconFor(key.packageName, key.className, density)
                when {
                    packIcon != null -> IconRenderer.renderPlain(packIcon, currentSpec.sizePx)
                    currentPack != null && currentPack.filter.hasFallbackMask && !currentSpec.themed -> IconRenderer.renderWithPackMask(
                        original = info.getIcon(density),
                        size = currentSpec.sizePx,
                        back = currentPack.back(density, key.packageName.hashCode()),
                        mask = currentPack.mask(density),
                        upon = currentPack.upon(density),
                        scale = currentPack.filter.scale,
                    )
                    else -> renderSystem(info.getIcon(density), currentSpec)
                }
            } catch (e: RuntimeException) {
                // A third-party drawable that throws while drawing (broken vector, bad pack
                // resource) must not take down the launcher: fall back to the system default icon.
                Log.w(TAG, "Icon render failed for ${key.packageName}", e)
                renderSystem(context.packageManager.defaultActivityIcon, currentSpec)
            }
            saveToDisk(cacheKey, bitmap)
            bitmap.toHardware().also { memory.put(cacheKey, it) }
        }
    }

    suspend fun shortcutIcon(packageName: String, shortcutId: String, user: Long): Bitmap? {
        val cacheKey = "shortcut|$packageName|$shortcutId|$user|${spec.cacheTag}"
        memory.get(cacheKey)?.let { return it }
        return withContext(renderDispatcher) {
            val info = shortcuts.find(packageName, shortcutId, user) ?: return@withContext null
            val drawable = shortcuts.icon(info) ?: return@withContext null
            val bitmap = runCatching { renderSystem(drawable, spec) }.getOrNull() ?: return@withContext null
            bitmap.toHardware().also { memory.put(cacheKey, it) }
        }
    }

    /** Renders an arbitrary drawable (e.g. a shortcut info icon) in the current icon style. */
    suspend fun render(drawable: Drawable): Bitmap = withContext(renderDispatcher) {
        renderSystem(drawable, spec).toHardware()
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
    suspend fun shapePreview(key: ComponentKey, shape: IconShapeKind, sizePx: Int): Bitmap? = withContext(renderDispatcher) {
        val info = apps.activityInfo(key) ?: return@withContext null
        IconRenderer.render(
            drawable = info.getIcon(density),
            size = sizePx,
            shape = IconShapes.path(shape, sizePx.toFloat()),
            themed = if (spec.themed) themedColors(spec.darkTheme) else null,
            themedFallback = spec.themedFallback,
            normalizeLegacy = spec.normalizeLegacy,
        )
    }

    /** Raw drawable from the current icon pack, for the "pick icon from pack" dialog. */
    fun packDrawable(name: String): Drawable? = pack?.drawable(name, density)

    fun packDrawableNames(): List<String> = pack?.filter?.components?.values?.distinct()?.sorted().orEmpty()

    private fun renderSystem(drawable: Drawable, s: Spec): Bitmap = IconRenderer.render(
        drawable = drawable,
        size = s.sizePx,
        shape = shapePath,
        themed = if (s.themed) themedColors(s.darkTheme) else null,
        themedFallback = s.themedFallback,
        normalizeLegacy = s.normalizeLegacy,
    )

    private fun themedColors(dark: Boolean): ThemedColors = if (dark) {
        ThemedColors(
            background = context.getColor(android.R.color.system_neutral1_800),
            foreground = context.getColor(android.R.color.system_accent1_100),
        )
    } else {
        ThemedColors(
            background = context.getColor(android.R.color.system_accent1_100),
            foreground = context.getColor(android.R.color.system_neutral1_900),
        )
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

    /** Trims stale disk entries (e.g. after many spec changes). */
    fun trimDiskCache(maxFiles: Int = 3000) {
        val files = diskDir.listFiles() ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }.take(files.size - maxFiles).forEach { it.delete() }
    }

    private fun memoryBudgetKb(): Int = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()

    private companion object {
        const val TAG = "AndaIcons"
    }
}
