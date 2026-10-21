package com.anda.launcher.core.data

import com.anda.launcher.core.model.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupReaderTest {

    private fun bundleJson(formatVersion: Int = BackupBundle.FORMAT_VERSION): String = LauncherJson.encodeToString(
        BackupBundle.serializer(),
        BackupBundle(
            formatVersion = formatVersion,
            createdAt = 1L,
            settings = LauncherSettings(onboardingDone = true),
            screens = emptyList(),
            items = emptyList(),
            overrides = emptyList(),
            groups = emptyList(),
            members = emptyList(),
        ),
    )

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun read(bytes: ByteArray) = BackupReader.read(ByteArrayInputStream(bytes))

    private fun assertRefused(bytes: ByteArray) {
        try {
            read(bytes)
            fail("Expected the backup to be refused")
        } catch (expected: InvalidBackupException) {
            // Refused as a whole, nothing restored.
        }
    }

    @Test
    fun validBackupReadsBundleAndIcons() {
        val parsed = read(zip("backup.json" to bundleJson().encodeToByteArray(), "icons/a.png" to byteArrayOf(1, 2, 3)))
        assertTrue(parsed.bundle.settings.onboardingDone)
        assertEquals(listOf("a.png"), parsed.icons.keys.toList())
    }

    @Test
    fun notAZipIsRefused() = assertRefused("hello, not a zip".encodeToByteArray())

    @Test
    fun zipWithoutBackupJsonIsRefused() = assertRefused(zip("icons/a.png" to byteArrayOf(1)))

    @Test
    fun corruptJsonIsRefused() = assertRefused(zip("backup.json" to "{ not json".encodeToByteArray()))

    @Test
    fun truncatedFileIsRefused() {
        val full = zip("backup.json" to bundleJson().encodeToByteArray())
        assertRefused(full.copyOf(full.size / 2))
    }

    @Test
    fun newerFormatIsRefused() = assertRefused(zip("backup.json" to bundleJson(formatVersion = 99).encodeToByteArray()))

    @Test
    fun oversizedIconIsRefused() {
        // Highly compressible: a few KB on disk, megabytes when inflated (zip-bomb shape).
        val huge = ByteArray(BackupReader.MAX_ICON_BYTES + 1)
        assertRefused(zip("backup.json" to bundleJson().encodeToByteArray(), "icons/bomb.png" to huge))
    }

    @Test
    fun pathTraversalIconNamesAreSkipped() {
        val parsed = read(
            zip(
                "backup.json" to bundleJson().encodeToByteArray(),
                "icons/../evil" to byteArrayOf(1),
                "icons/.." to byteArrayOf(1),
                "icons/sub/dir.png" to byteArrayOf(1),
                "icons/ok.png" to byteArrayOf(1),
            ),
        )
        assertEquals(listOf("ok.png"), parsed.icons.keys.toList())
    }

    @Test
    fun devNoteFilesAreRead() {
        val parsed = read(
            zip(
                "backup.json" to bundleJson().encodeToByteArray(),
                "dev/1760000000000/note.json" to "{}".encodeToByteArray(),
                "dev/1760000000000/1-screen.jpg" to byteArrayOf(1, 2),
            ),
        )
        assertEquals(listOf("1760000000000/note.json", "1760000000000/1-screen.jpg"), parsed.devFiles.keys.toList())
    }

    @Test
    fun backupsWithoutDevNotesStillRead() {
        assertTrue(read(zip("backup.json" to bundleJson().encodeToByteArray())).devFiles.isEmpty())
    }

    @Test
    fun pathTraversalDevPathsAreSkipped() {
        val parsed = read(
            zip(
                "backup.json" to bundleJson().encodeToByteArray(),
                "dev/../x" to byteArrayOf(1),
                "dev/a/../../b" to byteArrayOf(1),
                "dev/a/b/c" to byteArrayOf(1),
                "dev/loose" to byteArrayOf(1),
                "dev/.a/b" to byteArrayOf(1),
                "dev/a/ok.txt" to byteArrayOf(1),
            ),
        )
        assertEquals(listOf("a/ok.txt"), parsed.devFiles.keys.toList())
    }

    @Test
    fun oversizedDevFileIsRefused() {
        val huge = ByteArray(BackupReader.MAX_DEV_FILE_BYTES + 1)
        assertRefused(zip("backup.json" to bundleJson().encodeToByteArray(), "dev/1/big.jpg" to huge))
    }

    @Test
    fun safeIconNameRules() {
        assertEquals("icon.png", BackupReader.safeIconName("icon.png"))
        assertNull(BackupReader.safeIconName(""))
        assertNull(BackupReader.safeIconName(".."))
        assertNull(BackupReader.safeIconName(".hidden"))
        assertNull(BackupReader.safeIconName("a\\b"))
    }
}
