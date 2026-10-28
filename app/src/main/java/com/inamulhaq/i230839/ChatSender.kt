package com.inamulhaq.i230839

import com.google.firebase.database.ServerValue

/**
 * Sends a chat message from outside the Chat screen (story replies, shared posts, call logs,
 * screenshot alerts) and keeps both people's Chats rows up to date, the same way [ChatActivity] does.
 */
object ChatSender {

    fun send(
        otherUid: String,
        type: String,
        text: String = "",
        image: String = "",
        postId: String = "",
        preview: String = text,
        bumpUnread: Boolean = true,
    ) {
        val me = Db.uid ?: return
        if (otherUid.isEmpty() || otherUid == me) return
        val chatId = Db.chatId(me, otherUid)
        val id = Db.messages(chatId).push().key ?: return
        val updates = mutableMapOf<String, Any>(
            "chats/$chatId/messages/$id/senderId" to me,
            "chats/$chatId/messages/$id/type" to type,
            "chats/$chatId/messages/$id/text" to text,
            "chats/$chatId/messages/$id/image" to image,
            "chats/$chatId/messages/$id/postId" to postId,
            "chats/$chatId/messages/$id/timestamp" to ServerValue.TIMESTAMP,
        )
        updates += summaries(me, otherUid, preview, bumpUnread)
        Db.update(updates)
    }

    /** The two userChats rows for a new last message. */
    fun summaries(me: String, otherUid: String, preview: String, bumpUnread: Boolean): Map<String, Any> {
        val updates = mutableMapOf<String, Any>(
            "userChats/$me/$otherUid/otherUid" to otherUid,
            "userChats/$me/$otherUid/lastMessage" to "You: $preview",
            "userChats/$me/$otherUid/lastTime" to ServerValue.TIMESTAMP,
            "userChats/$otherUid/$me/otherUid" to me,
            "userChats/$otherUid/$me/lastMessage" to preview,
            "userChats/$otherUid/$me/lastTime" to ServerValue.TIMESTAMP,
        )
        if (bumpUnread) updates["userChats/$otherUid/$me/unread"] = ServerValue.increment(1)
        return updates
    }
}
