package com.anda.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * Identifies a launchable activity for a specific user profile.
 * [user] is the profile serial number from `UserManager.getSerialNumberForUser`, which is stable
 * across reboots (unlike `UserHandle` identifiers).
 */
@Serializable
data class ComponentKey(
    val packageName: String,
    val className: String,
    val user: Long = 0L,
) {
    /** Stable string form used as a cache/database key: `pkg/cls#user`. */
    fun flatten(): String = "$packageName/$className#$user"

    companion object {
        fun parse(value: String): ComponentKey? {
            val slash = value.indexOf('/')
            val hash = value.lastIndexOf('#')
            if (slash <= 0 || hash <= slash) return null
            val user = value.substring(hash + 1).toLongOrNull() ?: return null
            return ComponentKey(value.substring(0, slash), value.substring(slash + 1, hash), user)
        }
    }
}

/** Identifies a package within a profile; badges and overrides are tracked per package. */
@Serializable
data class PackageUserKey(val packageName: String, val user: Long = 0L)

val ComponentKey.packageUser: PackageUserKey get() = PackageUserKey(packageName, user)
