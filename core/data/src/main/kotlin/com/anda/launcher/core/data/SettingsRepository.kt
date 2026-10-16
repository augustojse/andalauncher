package com.anda.launcher.core.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.anda.launcher.core.model.LauncherSettings
import com.anda.launcher.core.model.sanitized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.SerializationException
import java.io.InputStream
import java.io.OutputStream

/** One typed settings tree persisted as JSON (ADR 0002). */
class SettingsRepository(context: Context, scope: CoroutineScope) {

    private val store: DataStore<LauncherSettings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { LauncherSettings() },
        scope = scope,
        produceFile = { context.dataStoreFile("launcher_settings.json") },
    )

    /** `null` until the first read completes, so UI can hold the splash instead of flashing defaults. */
    val settings: StateFlow<LauncherSettings?> = store.data.stateIn(scope, SharingStarted.Eagerly, null)

    /** Every write is clamped to valid ranges ([sanitized]), whatever the caller computed. */
    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        store.updateData { transform(it).sanitized() }
    }

    suspend fun replace(settings: LauncherSettings) {
        store.updateData { settings.sanitized() }
    }

    private object SettingsSerializer : Serializer<LauncherSettings> {
        override val defaultValue = LauncherSettings()

        override suspend fun readFrom(input: InputStream): LauncherSettings = try {
            // Clamped on read too: files written by older builds or restored backups are not trusted.
            LauncherJson.decodeFromString(LauncherSettings.serializer(), input.readBytes().decodeToString()).sanitized()
        } catch (e: SerializationException) {
            throw CorruptionException("Unreadable settings", e)
        } catch (e: IllegalArgumentException) {
            throw CorruptionException("Unreadable settings", e)
        }

        override suspend fun writeTo(t: LauncherSettings, output: OutputStream) {
            output.write(LauncherJson.encodeToString(LauncherSettings.serializer(), t).encodeToByteArray())
        }
    }
}
