package com.anda.launcher.core.data

import com.anda.launcher.core.model.LauncherAction
import kotlinx.serialization.json.Json

/** Lenient JSON shared by settings, stored actions and backups so old data keeps decoding. */
val LauncherJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false
}

internal fun LauncherAction?.encode(): String? = this?.let { LauncherJson.encodeToString(LauncherAction.serializer(), it) }

internal fun String?.decodeAction(): LauncherAction? = this?.let {
    runCatching { LauncherJson.decodeFromString(LauncherAction.serializer(), it) }.getOrNull()
}
