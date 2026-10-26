package com.anda.launcher.feature.settings.components

/** `BelowDock` → "Below dock", `InstallDate` → "Install date". */
internal fun humanize(name: String): String =
    name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase().replaceFirstChar { it.uppercase() }

internal fun <T> defaultLabel(value: T): String = if (value is Enum<*>) humanize(value.name) else value.toString()
