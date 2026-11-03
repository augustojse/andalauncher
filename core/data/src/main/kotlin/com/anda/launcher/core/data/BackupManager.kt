package com.anda.launcher.core.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.anda.launcher.core.data.db.AppOverrideEntity
import com.anda.launcher.core.data.db.DrawerGroupEntity
import com.anda.launcher.core.data.db.DrawerGroupMemberEntity
import com.anda.launcher.core.data.db.FavoriteEntity
import com.anda.launcher.core.data.db.ItemEntity
import com.anda.launcher.core.data.db.LauncherDatabase
import com.anda.launcher.core.data.db.ScreenEntity
import com.anda.launcher.core.model.Favorite
import com.anda.launcher.core.model.LauncherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class BackupBundle(
    val formatVersion: Int = FORMAT_VERSION,
    val createdAt: Long,
    val settings: LauncherSettings,
    val screens: List<ScreenEntity>,
    val items: List<ItemEntity>,
    val overrides: List<AppOverrideEntity>,
    val groups: List<DrawerGroupEntity>,
    val members: List<DrawerGroupMemberEntity>,
    /** Since 0.6.0. Older backups have none; older versions ignore the field, so the format stays 1. */
    val favorites: List<FavoriteEntity> = emptyList(),
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

/**
 * Versioned backup: a zip with `backup.json`, the custom icon files and the Dev notes. Also keeps rolling
 * automatic backups so a broken layout can always be recovered (docs/RND.md, "never crash-loop").
 */
class BackupManager(
    private val context: Context,
    private val db: LauncherDatabase,
    private val settings: SettingsRepository,
    private val devNotes: DevNotes,
) {
    val customIconDir: File get() = File(context.filesDir, "custom_icons").apply { mkdirs() }
    private val autoDir: File get() = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val bundle = db.withTransaction {
            BackupBundle(
                createdAt = System.currentTimeMillis(),
                settings = settings.settings.value ?: LauncherSettings(),
                screens = db.layoutDao().screens(),
                items = db.layoutDao().items(),
                overrides = db.appOverrideDao().all(),
                groups = db.drawerGroupDao().groups(),
                members = db.drawerGroupDao().members(),
                favorites = db.favoriteDao().all(),
            )
        }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupReader.JSON_ENTRY))
            zip.write(LauncherJson.encodeToString(BackupBundle.serializer(), bundle).encodeToByteArray())
            zip.closeEntry()
            customIconDir.listFiles()?.forEach { file ->
                zip.putNextEntry(ZipEntry("${BackupReader.ICON_PREFIX}${file.name}"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            devNotes.backupFiles().forEach { (path, file) ->
                zip.putNextEntry(ZipEntry("${BackupReader.DEV_PREFIX}$path"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Restores a backup. Returns false, and changes nothing, if the file isn't a valid Anda backup
     * (corrupt, truncated, too large, or from a newer version). Never throws for bad input.
     */
    suspend fun import(input: InputStream): Boolean = withContext(Dispatchers.IO) {
        // Read and validate everything first; only then touch icons, database and settings.
        val parsed = try {
            BackupReader.read(input)
        } catch (e: IOException) {
            Log.w(TAG, "Backup refused: ${e.message}")
            return@withContext false
        }
        val restored = parsed.bundle
        parsed.icons.forEach { (name, bytes) -> File(customIconDir, name).writeBytes(bytes) }
        db.withTransaction {
            db.layoutDao().replaceAll(restored.screens, restored.items)
            db.appOverrideDao().clear()
            db.appOverrideDao().upsertAll(restored.overrides)
            db.drawerGroupDao().clearAllMembers()
            db.drawerGroupDao().clearGroups()
            db.drawerGroupDao().insertGroups(restored.groups)
            db.drawerGroupDao().insertMembers(restored.members)
            db.favoriteDao().replaceAll(restored.favorites.sortedBy { it.position }.take(Favorite.MAX_FAVORITES))
        }
        settings.replace(restored.settings)
        // Dev notes merge instead of replace: restoring an old layout must not lose newer notes.
        runCatching {
            if (devNotes.restore(parsed.devFiles) > 0) devNotes.mirrorMissing()
        }.onFailure { Log.w(TAG, "Dev notes not restored", it) }
        true
    }

    /** Writes a rolling automatic backup at most once per [minIntervalMs]; keeps the newest [keep]. */
    suspend fun autoBackupIfDue(minIntervalMs: Long = DAY_MS, keep: Int = 7) = withContext(Dispatchers.IO) {
        val existing = autoBackups()
        val newest = existing.firstOrNull()?.lastModified() ?: 0L
        if (System.currentTimeMillis() - newest < minIntervalMs) return@withContext
        if (db.layoutDao().itemCount() == 0) return@withContext
        // Write to a temp file and rename: a crash or full disk mid-write must never leave a
        // truncated file that looks like the newest backup.
        autoDir.listFiles { f -> f.extension == TMP_EXTENSION }?.forEach { it.delete() }
        val name = "auto-${System.currentTimeMillis()}"
        val tmp = File(autoDir, "$name.$TMP_EXTENSION")
        try {
            tmp.outputStream().use { export(it) }
            if (!tmp.renameTo(File(autoDir, "$name.$EXTENSION"))) throw IOException("rename failed")
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
        autoBackups().drop(keep).forEach { it.delete() }
    }

    /** Automatic backups, newest first. */
    fun autoBackups(): List<File> =
        autoDir.listFiles { f -> f.extension == EXTENSION || f.extension == LEGACY_EXTENSION }
            ?.sortedByDescending { it.lastModified() }.orEmpty()

    companion object {
        const val EXTENSION = "andabackup"
        /** Backups made before the rename to Anda Launcher; same format. */
        private const val LEGACY_EXTENSION = "c1backup"
        const val MIME_TYPE = "application/zip"
        private const val TMP_EXTENSION = "tmp"
        private const val TAG = "AndaBackup"
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
