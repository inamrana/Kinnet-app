package com.inamulhaq.i230839

import com.google.firebase.database.ServerValue

/** One entry of the Notifications tab, stored under notifications/{uid}/{id}. */
data class Notice(
    val id: String = "",
    val type: String = "",
    val fromUid: String = "",
    val postId: String = "",
    val text: String = "",
    val read: Boolean = false,
    val timestamp: Long = 0L,
) {
    companion object {
        const val TYPE_REACTION = "reaction"
        const val TYPE_COMMENT = "comment"
        const val TYPE_REPLY = "reply"
        const val TYPE_FRIEND_ACCEPTED = "friend_accepted"
    }
}

/** Writes activity notifications (reactions, comments, replies, accepted requests) for other users. */
object Notices {

    fun send(toUid: String, type: String, postId: String = "", text: String = "") {
        val me = Db.uid ?: return
        if (toUid.isEmpty() || toUid == me) return
        Db.notifications.child(toUid).push().setValue(
            mapOf(
                "type" to type,
                "fromUid" to me,
                "postId" to postId,
                "text" to text,
                "read" to false,
                "timestamp" to ServerValue.TIMESTAMP,
            )
        )
    }

    /** The sentence shown in the Notifications tab, with the sender's name in bold. */
    fun sentence(notice: Notice, name: String): String {
        val who = "<b>${android.text.TextUtils.htmlEncode(name)}</b>"
        val quoted = android.text.TextUtils.htmlEncode(notice.text)
        return when (notice.type) {
            Notice.TYPE_REACTION -> "$who reacted to your post."
            Notice.TYPE_COMMENT -> "$who commented on your post: “$quoted”"
            Notice.TYPE_REPLY -> "$who replied to your comment: “$quoted”"
            Notice.TYPE_FRIEND_ACCEPTED -> "$who accepted your friend request."
            else -> "$who sent you a notification."
        }
    }
}
