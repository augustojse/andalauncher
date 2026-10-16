package com.anda.launcher.core.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/** Maps between [UserHandle] and the stable profile serial numbers stored in the database. */
class UserResolver(context: Context) {
    private val userManager = context.getSystemService(UserManager::class.java)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    val myUser: UserHandle = Process.myUserHandle()
    val mySerial: Long = serialOf(myUser)

    fun serialOf(user: UserHandle): Long = userManager.getSerialNumberForUser(user)

    fun userOf(serial: Long): UserHandle? = userManager.getUserForSerialNumber(serial)

    fun profiles(): List<UserHandle> = launcherApps.profiles

    fun isWorkProfile(user: UserHandle): Boolean {
        if (user == myUser) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val type = launcherApps.getLauncherUserInfo(user)?.userType
            return type == UserManager.USER_TYPE_PROFILE_MANAGED
        }
        return true
    }

    fun isQuietModeEnabled(user: UserHandle): Boolean = userManager.isQuietModeEnabled(user)

    /** Pauses/resumes the work profile. Allowed for the default home app. */
    fun setQuietMode(user: UserHandle, enabled: Boolean): Boolean =
        runCatching { userManager.requestQuietModeEnabled(enabled, user) }.getOrDefault(false)
}
