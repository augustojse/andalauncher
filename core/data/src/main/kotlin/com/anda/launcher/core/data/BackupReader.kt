package com.anda.launcher.core.data

import kotlinx.serialization.SerializationException
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * A backup file that was fully read and checked, before anything on the device is touched.
 * [devFiles] are the Dev notes' files, keyed `<note id>/<file name>`.
 */
internal class ParsedBackup(val bundle: BackupBundle, val icons: Map<String, ByteArray>, val devFiles: Map<String, ByteArray> = emptyMap())

/** Why a backup was refused. */
internal class InvalidBackupException(message: String) : IOException(message)

/**
 * Reads a backup zip into memory with hard limits, so a corrupt, truncated, hostile or
 * newer-format file is rejected as a whole instead of crashing the launcher, filling the disk or
 * half-restoring. Pure JVM code: unit-tested in `BackupReaderTest`.
 */
internal object BackupReader {
    const val MAX_JSON_BYTES = 8 * 1024 * 1024
    const val MAX_ICON_BYTES = 2 * 1024 * 1024
    const val MAX_ICONS = 1_000
    const val MAX_DEV_FILE_BYTES = 8 * 1024 * 1024
    const val MAX_DEV_FILES = 300
    const val MAX_TOTAL_BYTES = 64 * 1024 * 1024

    fun read(input: InputStream): ParsedBackup {
        var bundle: BackupBundle? = null
        val icons = LinkedHashMap<String, ByteArray>()
        val devFiles = LinkedHashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    when {
                        entry.name == JSON_ENTRY -> {
                            val bytes = zip.readAtMost(MAX_JSON_BYTES, "backup.json")
                            total += bytes.size
                            bundle = LauncherJson.decodeFromString(BackupBundle.serializer(), bytes.decodeToString())
                        }
                        entry.name.startsWith(ICON_PREFIX) -> {
                            val name = safeIconName(entry.name.removePrefix(ICON_PREFIX)) ?: continue
                            if (icons.size >= MAX_ICONS) throw InvalidBackupException("Too many icons")
                            val bytes = zip.readAtMost(MAX_ICON_BYTES, name)
                            total += bytes.size
                            icons[name] = bytes
                        }
                        entry.name.startsWith(DEV_PREFIX) -> {
                            val path = safeDevPath(entry.name.removePrefix(DEV_PREFIX)) ?: continue
                            if (devFiles.size >= MAX_DEV_FILES) throw InvalidBackupException("Too many dev note files")
                            val bytes = zip.readAtMost(MAX_DEV_FILE_BYTES, path)
                            total += bytes.size
                            devFiles[path] = bytes
                        }
                    }
                    if (total > MAX_TOTAL_BYTES) throw InvalidBackupException("Backup is too large")
                }
            }
        } catch (e: ZipException) {
            throw InvalidBackupException("Not a zip file: ${e.message}")
        } catch (e: EOFException) {
            throw InvalidBackupException("Backup file is incomplete")
        } catch (e: SerializationException) {
            throw InvalidBackupException("Unreadable backup.json: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("Unreadable backup.json: ${e.message}")
        }
        val parsed = bundle ?: throw InvalidBackupException("Not an Anda Launcher backup")
        if (parsed.formatVersion > BackupBundle.FORMAT_VERSION) {
            throw InvalidBackupException("Made by a newer version of Anda Launcher")
        }
        return ParsedBackup(parsed, icons, devFiles)
    }

    /** A plain file name, or null: no paths, no `..`, no hidden or empty names. */
    fun safeIconName(raw: String): String? {
        if (raw.isEmpty() || raw.length > 200) return null
        if ('/' in raw || '\\' in raw || '\u0000' in raw) return null
        if (raw == "." || raw == ".." || raw.startsWith(".")) return null
        return raw
    }

    /** `<note id>/<file name>`, both plain names, or null. */
    fun safeDevPath(raw: String): String? {
        val parts = raw.split('/')
        if (parts.size != 2) return null
        val id = safeIconName(parts[0]) ?: return null
        val name = safeIconName(parts[1]) ?: return null
        return "$id/$name"
    }

    /** Reads the current entry, failing once it exceeds [limit] (zip bombs lie about sizes). */
    private fun ZipInputStream.readAtMost(limit: Int, what: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            if (out.size() + n > limit) throw InvalidBackupException("$what is too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    const val JSON_ENTRY = "backup.json"
    const val ICON_PREFIX = "icons/"
    const val DEV_PREFIX = "dev/"
}
