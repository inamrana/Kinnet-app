package com.inamulhaq.i230839

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

/**
 * One place for every Realtime Database path Kinnect uses.
 *
 * users/{uid}                         -> [User]
 * usernames/{usernameKey}             -> uid (keeps usernames unique; see [usernameKey])
 * friendRequests/{toUid}/{fromUid}    -> time the request was sent (incoming requests)
 * sentRequests/{fromUid}/{toUid}      -> time (outgoing requests, for the "Requested" state)
 * friends/{uid}/{friendUid}           -> time they became friends
 * chats/{chatId}/messages/{id}        -> [Message]
 * userChats/{uid}/{otherUid}          -> [ChatSummary] (the Chats list)
 * posts/{postId}                      -> [Post]
 * postImages/{postId}/{index}         -> Base64 JPEG of each photo in a post
 * comments/{postId}/{commentId}       -> [Comment]
 * stories/{ownerUid}/{storyId}        -> [Story] (shown for 24 hours)
 * storyMedia/{storyId}                -> Base64 photo or video of a story
 * closeFriends/{uid}/{friendUid}      -> true (who sees "Close friends" stories)
 * notifications/{uid}/{id}            -> [Notice] (Notifications tab)
 * callSessions/{callId}               -> [CallSession] (status of a call)
 * calls/{uid}                         -> id of the call ringing for that user
 * callLogs/{uid}/{id}                 -> [CallLog]
 * fcmTokens/{uid}                     -> device token for push notifications
 */
object Db {

    val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    val uid: String? get() = auth.currentUser?.uid

    private val root: DatabaseReference get() = FirebaseDatabase.getInstance().reference

    val users: DatabaseReference get() = root.child("users")
    val usernames: DatabaseReference get() = root.child("usernames")
    val friendRequests: DatabaseReference get() = root.child("friendRequests")
    val sentRequests: DatabaseReference get() = root.child("sentRequests")
    val friends: DatabaseReference get() = root.child("friends")
    val chats: DatabaseReference get() = root.child("chats")
    val userChats: DatabaseReference get() = root.child("userChats")
    val posts: DatabaseReference get() = root.child("posts")
    val postImages: DatabaseReference get() = root.child("postImages")
    val comments: DatabaseReference get() = root.child("comments")
    val stories: DatabaseReference get() = root.child("stories")
    val storyMedia: DatabaseReference get() = root.child("storyMedia")
    val closeFriends: DatabaseReference get() = root.child("closeFriends")
    val notifications: DatabaseReference get() = root.child("notifications")
    val calls: DatabaseReference get() = root.child("calls")
    val callSessions: DatabaseReference get() = root.child("callSessions")
    val callLogs: DatabaseReference get() = root.child("callLogs")
    val fcmTokens: DatabaseReference get() = root.child("fcmTokens")

    /** Writes several paths at once (all succeed or none do). Keys are full paths like "friends/a/b". */
    fun update(paths: Map<String, Any?>) = root.updateChildren(paths)

    /** Database keys cannot contain ".", so "jacob.west" is stored as "jacob,west". */
    fun usernameKey(username: String): String = username.replace('.', ',')

    fun user(uid: String): DatabaseReference = users.child(uid)
    fun me(): DatabaseReference? = uid?.let(::user)

    /** Both people in a one-to-one chat get the same id, whoever opens it first. */
    fun chatId(a: String, b: String): String = if (a < b) "${a}_$b" else "${b}_$a"
    fun messages(chatId: String): DatabaseReference = chats.child(chatId).child("messages")
}

/** One message in chats/{chatId}/messages. [type] is "text", "image", "post" or "system". */
data class Message(
    val id: String = "",
    val senderId: String = "",
    val type: String = TYPE_TEXT,
    val text: String = "",
    val image: String = "",
    val replyToId: String = "",
    val replyToText: String = "",
    val replyToSender: String = "",
    val postId: String = "",
    val edited: Boolean = false,
    val hearts: Map<String, Boolean> = emptyMap(),
    val timestamp: Long = 0L,
) {
    companion object {
        const val TYPE_TEXT = "text"
        const val TYPE_IMAGE = "image"
        const val TYPE_SYSTEM = "system"
        const val TYPE_POST = "post"

        /** Own messages can be edited or deleted for this long after sending. */
        const val EDIT_WINDOW_MS = 5 * 60 * 1000L
    }
}

/** One row of the Chats list, stored per user under userChats/{uid}/{otherUid}. */
data class ChatSummary(
    val otherUid: String = "",
    val lastMessage: String = "",
    val lastTime: Long = 0L,
    val unread: Int = 0,
)

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
