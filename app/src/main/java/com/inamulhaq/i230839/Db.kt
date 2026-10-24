package com.inamulhaq.i230839

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * One place for every Realtime Database path Kinnect uses.
 *
 * users/{uid}            -> [User]
 * usernames/{username}   -> uid (keeps usernames unique)
 */
object Db {

    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val uid: String? get() = auth.currentUser?.uid

    private val root: DatabaseReference get() = FirebaseDatabase.getInstance().reference

    val users: DatabaseReference get() = root.child("users")
    val usernames: DatabaseReference get() = root.child("usernames")

    fun user(uid: String): DatabaseReference = users.child(uid)
    fun me(): DatabaseReference? = uid?.let(::user)
}

/** A Kinnect account as stored under users/{uid}. Default values are needed by Firebase. */
data class User(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val nameLower: String = "",
    val username: String = "",
    val email: String = "",
    val gender: String = "",
    val birthday: String = "",
    val bio: String = "",
    val website: String = "",
    val phone: String = "",
    val profileImage: String = "",
    val coverImage: String = "",
    val profileComplete: Boolean = false,
    val online: Boolean = false,
    val lastSeen: Long = 0L,
    val createdAt: Long = 0L,
) {
    /** "Jacob West" -> "JW", used by the letter avatars. */
    val initials: String
        get() = fullName.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
}
