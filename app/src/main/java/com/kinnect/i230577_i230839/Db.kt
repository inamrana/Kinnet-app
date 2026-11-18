package com.kinnect.i230577_i230839

/** The signed-in user's id, from the session token issued by the PHP backend. */
object Db {
    val uid: String? get() = Session.uid
}

/**
 * One chat message from messages/sync.php. [type] is "text", "image", "video", "file", "post" or
 * "system". [media] is the photo / video / file URL (or an outbox path while [pending], i.e. sent
 * offline and not delivered to the server yet).
 */
data class Message(
    val id: String = "",
    val senderId: String = "",
    val type: String = TYPE_TEXT,
    val text: String = "",
    val media: String = "",
    val fileName: String = "",
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSender: String = "",
    val postId: String = "",
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val hearts: Map<String, Boolean> = emptyMap(),
    val timestamp: Long = 0L,
    val pending: Boolean = false,
) {
    /** Kept so older call sites that show a message photo still compile. */
    val image: String get() = media

    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_VIDEO = "video"
        const val TYPE_FILE = "file"
        const val TYPE_SYSTEM = "system"
        const val TYPE_POST = "post"

        /** Own messages can be edited or deleted for this long after sending (the server checks too). */
        const val EDIT_WINDOW_MS = 5 * 60 * 1000L
    }
}

/** One row of the Chats list (chats/list.php). */
data class ChatSummary(
    val otherUid: String = "",
    val lastMessage: String = "",
    val lastTime: Long = 0L,
    val unread: Int = 0,
)

/** A Kinnect account, in the shape the web services return (see user_json in backend/lib.php). */
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
