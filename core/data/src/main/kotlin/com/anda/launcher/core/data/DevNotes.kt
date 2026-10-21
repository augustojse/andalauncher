package com.anda.launcher.core.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** A note about something misbehaving, with the screenshots or files attached to it. */
@Serializable
data class DevNote(
    val id: String,
    val createdAt: Long,
    val text: String,
    val appVersion: String = "",
    val device: String = "",
    val attachments: List<String> = emptyList(),
    /** MediaStore copies in Downloads/Anda Launcher/dev, so they can be deleted with the note. */
    val mirrorUris: List<String> = emptyList(),
)

/**
 * Settings → Dev: notes and screenshots about problems, for whoever works on the launcher.
 *
 * Each note lives in `filesDir/dev/<id>/` (note.json plus attachments), is part of every backup
 * (`dev/<id>/<file>` entries) and is copied to Downloads/Anda Launcher/dev/<id>/ so it can be pulled
 * from a PC with `tools/dev/pull-dev-notes.ps1`. Screenshots are downscaled so the seven automatic
 * backups stay small. Nothing is sent anywhere: there is no INTERNET permission.
 */
class DevNotes(private val context: Context) {
    val dir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    private val appVersion: String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrDefault("unknown")

    class AddResult(val note: DevNote, val skipped: List<String>)

    /** Newest first. Blocking: call off the main thread. */
    fun list(): List<DevNote> = dir.listFiles { f -> f.isDirectory }.orEmpty()
        .mapNotNull { read(it) }
        .sortedByDescending { it.createdAt }

    fun attachment(note: DevNote, name: String): File = File(File(dir, note.id), name)

    /** Saves a note with copies of [uris]. Files that can't be read or are too large are skipped. */
    suspend fun add(text: String, uris: List<Uri>): AddResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = uniqueId(now)
        val noteDir = File(dir, id).apply { mkdirs() }
        val attachments = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        uris.forEachIndexed { index, uri ->
            val display = displayName(uri) ?: "file"
            val saved = runCatching { copyIn(uri, display, index + 1, noteDir) }
                .onFailure { Log.w(TAG, "Couldn't attach $display", it) }
                .getOrNull()
            if (saved != null) attachments += saved else skipped += display
        }
        val note = DevNote(
            id = id,
            createdAt = now,
            text = text.trim(),
            appVersion = appVersion,
            device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            attachments = attachments,
        )
        write(note)
        AddResult(mirror(note), skipped)
    }

    suspend fun delete(note: DevNote) = withContext(Dispatchers.IO) {
        note.mirrorUris.forEach { uri -> runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) } }
        File(dir, note.id).deleteRecursively()
    }

    /**
     * The notes' files as `<id>/<name>`, for backups, newest notes first. Whole notes that would
     * push the backup past what [BackupReader] accepts are left out, so the layout stays restorable.
     */
    internal fun backupFiles(
        maxBytes: Long = BackupReader.MAX_TOTAL_BYTES / 2L,
        maxFiles: Int = BackupReader.MAX_DEV_FILES,
    ): Map<String, File> = buildMap {
        var bytes = 0L
        for (note in list()) {
            val files = File(dir, note.id).listFiles { f -> f.isFile }.orEmpty()
                .filter { it.length() <= BackupReader.MAX_DEV_FILE_BYTES }
            val size = files.sumOf { it.length() }
            if (bytes + size > maxBytes || this.size + files.size > maxFiles) continue
            bytes += size
            files.forEach { put("${note.id}/${it.name}", it) }
        }
    }

    /**
     * Adds the notes of a restored backup ([files] keyed `<id>/<name>`). Merges: notes already on
     * the phone are kept as they are, nothing is deleted. Returns how many notes were added.
     */
    internal fun restore(files: Map<String, ByteArray>): Int {
        var added = 0
        files.entries.groupBy { it.key.substringBefore('/') }.forEach { (id, entries) ->
            val noteDir = File(dir, id)
            if (noteDir.exists() || entries.none { it.key == "$id/$NOTE_FILE" }) return@forEach
            noteDir.mkdirs()
            entries.forEach { (path, bytes) -> File(noteDir, path.substringAfter('/')).writeBytes(bytes) }
            // The Downloads copies belonged to the phone that made the backup.
            val note = read(noteDir)
            if (note == null) noteDir.deleteRecursively() else write(note.copy(mirrorUris = emptyList())).also { added++ }
        }
        return added
    }

    /** Copies notes that have no Downloads copy yet (e.g. just restored). Blocking. */
    fun mirrorMissing() {
        list().filter { it.mirrorUris.isEmpty() }.forEach { mirror(it) }
    }

    /** A small bitmap for the Dev screen, or null if [file] isn't an image. Blocking. */
    fun thumbnail(file: File, maxPx: Int): Bitmap? {
        if (!isImage(file.name)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    // ---- Internals -------------------------------------------------------------------------------

    private fun uniqueId(now: Long): String {
        var id = now
        while (File(dir, id.toString()).exists()) id++
        return id.toString()
    }

    private fun read(noteDir: File): DevNote? = runCatching {
        LauncherJson.decodeFromString(DevNote.serializer(), File(noteDir, NOTE_FILE).readText())
    }.getOrNull()?.takeIf { it.id == noteDir.name }

    private fun write(note: DevNote) {
        File(File(dir, note.id), NOTE_FILE).writeText(LauncherJson.encodeToString(DevNote.serializer(), note))
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Copies one picked file into [noteDir]; returns its stored name. Images are downscaled. */
    private fun copyIn(uri: Uri, display: String, index: Int, noteDir: File): String? {
        val base = "$index-" + safeName(display)
        val mime = context.contentResolver.getType(uri).orEmpty()
        if (mime.startsWith("image/") || isImage(display)) {
            val bitmap = runCatching { decodeScaled(uri) }.getOrNull()
            if (bitmap != null) {
                val name = base.substringBeforeLast('.') + ".jpg"
                File(noteDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                bitmap.recycle()
                return name
            }
        }
        val target = File(noteDir, base)
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { stream ->
            target.outputStream().use { out ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_FILE_BYTES) {
                        out.close()
                        target.delete()
                        throw IOException("$display is larger than ${MAX_FILE_BYTES / 1024 / 1024} MB")
                    }
                    out.write(buffer, 0, n)
                }
            }
        }
        return base
    }

    private fun decodeScaled(uri: Uri): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val longEdge = max(info.size.width, info.size.height)
        if (longEdge > MAX_IMAGE_PX) {
            val scale = MAX_IMAGE_PX.toFloat() / longEdge
            decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

    /** Copies [note] to Downloads/Anda Launcher/dev/<id>/; a failure only costs the copy. */
    private fun mirror(note: DevNote): DevNote {
        val uris = mutableListOf<String>()
        runCatching {
            insertDownload(note.id, "note.txt", "text/plain") { it.write(noteText(note).encodeToByteArray()) }?.let { uris += it.toString() }
            note.attachments.forEach { name ->
                val file = attachment(note, name)
                if (!file.exists()) return@forEach
                insertDownload(note.id, name, mimeFor(name)) { out -> file.inputStream().use { it.copyTo(out) } }?.let { uris += it.toString() }
            }
        }.onFailure { Log.w(TAG, "Couldn't copy note ${note.id} to Downloads", it) }
        if (uris.isEmpty()) return note
        return note.copy(mirrorUris = uris).also { write(it) }
    }

    private fun insertDownload(id: String, name: String, mime: String, body: (java.io.OutputStream) -> Unit): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$MIRROR_DIR/$id")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use(body) ?: throw IOException("No output stream for $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun noteText(note: DevNote): String = buildString {
        appendLine("Anda Launcher dev note")
        appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(note.createdAt))}")
        appendLine("App: ${note.appVersion}")
        appendLine("Device: ${note.device}")
        if (note.attachments.isNotEmpty()) appendLine("Attachments: ${note.attachments.joinToString(", ")}")
        appendLine()
        append(note.text)
    }

    companion object {
        private const val TAG = "AndaDevNotes"
        const val DIR = "dev"
        const val NOTE_FILE = "note.json"
        /** Folder inside Downloads; tools/dev/pull-dev-notes.ps1 pulls from here. */
        const val MIRROR_DIR = "Anda Launcher/dev"
        const val MAX_IMAGE_PX = 1600
        const val MAX_FILE_BYTES = 5L * 1024 * 1024
        private const val JPEG_QUALITY = 85

        fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "heic")

        /** A plain file name made from what the picker reported. */
        internal fun safeName(raw: String): String =
            raw.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.', '_').take(80).ifEmpty { "file" }

        private fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "txt", "log", "md" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }
}
