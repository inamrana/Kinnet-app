package com.inamulhaq.i230839

import android.content.Context

/** Remembers the last account that signed in on this device (for the Log in "Recent login" card). */
class RecentLogin(context: Context) {

    private val prefs = context.getSharedPreferences("recent_login", Context.MODE_PRIVATE)

    val email: String? get() = prefs.getString(KEY_EMAIL, null)
    val name: String? get() = prefs.getString(KEY_NAME, null)
    val initials: String? get() = prefs.getString(KEY_INITIALS, null)

    fun save(user: User) {
        prefs.edit()
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_NAME, user.fullName)
            .putString(KEY_INITIALS, user.initials)
            .apply()
    }

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_INITIALS = "initials"
    }
}
