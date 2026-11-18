package com.kinnect.i230577_i230839

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File

/**
 * Messaging through the web services (messages/send.php, sync.php, edit.php, delete.php, heart.php).
 *
 * Messages are kept in SQLite (messages table, parent = the other person) so a chat opens instantly
 * and can be read offline. Sending, editing, deleting and hearts are shown at once and queued in
 * [Sync]: offline they wait (marked "Sending…") and go out in order when the connection returns.
 * The server's 5-minute rule decides edits and deletes; a refused change is rolled back on next sync.
 */
object ChatSender {

    private val listeners = mutableSetOf<(String) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val me get() = Db.uid.orEmpty()

    init {
        Sync.addListener { done ->
            if (done.type !in MESSAGE_TYPES) return@addListener
            val other = done.target
            if (done.type == "message") LocalDb.deleteItem("messages", Sync.localId(done.clientId))
            done.response?.optJSONObject("message")?.let { store(it, other) }
            if (done.rejectedMessage != null) LocalDb.putCache("chatsync:$other", "0") // re-read the truth
            changed(other)
        }
    }

    /** Sends a message (from Chat, or from elsewhere: story replies, shared posts, call logs, screenshot alerts). */
    fun send(
        otherUid: String,
        type: String,
        text: String = "",
        file: File? = null,
        fileName: String = "",
        postId: String = "",
        replyTo: Message? = null,
        @Suppress("UNUSED_PARAMETER") preview: String = text,
        @Suppress("UNUSED_PARAMETER") bumpUnread: Boolean = true,
    ) {
        if (otherUid.isEmpty() || otherUid == me) return
        val clientId = Sync.newClientId()
        val local = Message(
            id = Sync.localId(clientId), senderId = me, type = type, text = text,
            media = file?.absolutePath.orEmpty(), fileName = fileName, postId = postId,
            replyToId = replyTo?.id.orEmpty(), replyToText = replyTo?.let(::previewOf).orEmpty(),
            replyToSender = replyTo?.senderId.orEmpty(), timestamp = System.currentTimeMillis(), pending = true,
        )
        save(local, otherUid, pending = true)
        Sync.enqueue(
            "message", "messages/send.php",
            mapOf(
                "to" to otherUid, "type" to type, "text" to text, "fileName" to fileName.ifEmpty { null },
                "postId" to postId.ifEmpty { null }?.let(Sync::ref),
                "replyTo" to replyTo?.id?.let(Sync::ref), "replyToText" to replyTo?.let(::previewOf),
            ),
            target = otherUid, files = file?.let { mapOf("file" to it) }.orEmpty(), clientId = clientId,
        )
        changed(otherUid)
    }

    fun edit(otherUid: String, message: Message, text: String) {
        save(message.copy(text = text, edited = true), otherUid)
        Sync.enqueue("msgedit", "messages/edit.php", mapOf("id" to Sync.ref(message.id), "text" to text), target = otherUid)
        changed(otherUid)
    }

    fun delete(otherUid: String, message: Message) {
        save(message.copy(deleted = true, text = "", media = ""), otherUid)
        Sync.enqueue("msgdelete", "messages/delete.php", mapOf("id" to Sync.ref(message.id)), target = otherUid)
        changed(otherUid)
    }

    fun heart(otherUid: String, message: Message, on: Boolean) {
        val hearts = message.hearts.toMutableMap().apply { if (on) put(me, true) else remove(me) }
        save(message.copy(hearts = hearts), otherUid)
        Sync.enqueue("msgheart", "messages/heart.php", mapOf("id" to Sync.ref(message.id), "on" to if (on) "1" else "0"), target = otherUid)
        changed(otherUid)
    }

    // ---- reading ----------------------------------------------------------------------------

    fun cached(otherUid: String): List<Message> = LocalDb.items("messages", otherUid).map { it.toMessage() }

    /**
     * Asks the server for everything in this chat that is new or changed since the last sync, saves
     * it, and calls [done] with the other person's fresh profile (for "Active now"), or null offline.
     */
    fun sync(otherUid: String, done: (User?) -> Unit) {
        val since = LocalDb.cache("chatsync:$otherUid")?.toLongOrNull() ?: 0L
        Api.get("messages/sync.php", mapOf("with" to otherUid, "since" to since)) { result ->
            val json = result.json?.takeIf { result.ok } ?: return@get done(null)
            val messages = json.optJSONArray("messages").mapObjects { it }
            if (since == 0L) LocalDb.replaceItems("messages", otherUid, emptyList())
            messages.forEach { store(it, otherUid) }
            LocalDb.putCache("chatsync:$otherUid", json.optLong("serverTime").toString())
            val other = json.optJSONObject("other")?.toUser()?.also(LocalDb::putUser)
            if (messages.isNotEmpty() || since == 0L) changed(otherUid)
            done(other)
        }
    }

    fun markRead(otherUid: String) = Api.post("messages/read.php", mapOf("with" to otherUid)) {}

    fun watch(onChange: (String) -> Unit): Watch {
        listeners += onChange
        return Watch().onStop { listeners -= onChange }
    }

    private fun changed(otherUid: String) = main.post { listeners.toList().forEach { it(otherUid) } }

    fun previewOf(message: Message) = when {
        message.deleted -> "Message deleted"
        message.type == Message.TYPE_IMAGE -> "Photo"
        message.type == Message.TYPE_VIDEO -> "Video"
        message.type == Message.TYPE_FILE -> message.fileName.ifEmpty { "File" }
        message.type == Message.TYPE_POST -> "Shared a post"
        else -> message.text
    }

    // ---- JSON <-> SQLite --------------------------------------------------------------------

    private val MESSAGE_TYPES = setOf("message", "msgedit", "msgdelete", "msgheart")

    private fun store(json: JSONObject, otherUid: String) {
        // The server's copy of something sent from this phone replaces the local "Sending…" copy
        json.optString("clientId").takeIf { it.isNotEmpty() }?.let { LocalDb.deleteItem("messages", Sync.localId(it)) }
        json.toItem(otherUid).let { LocalDb.putItem("messages", it.id, it.parent, it.sort, it.json) }
    }

    private fun save(message: Message, otherUid: String, pending: Boolean = false) {
        val wasPending = LocalDb.item("messages", message.id)?.pending ?: false
        LocalDb.putItem("messages", message.id, otherUid, message.timestamp, message.toJson(), pending = pending || wasPending)
    }

    private fun JSONObject.toItem(otherUid: String) = LocalDb.Item(optString("id"), otherUid, optLong("timestamp"), false, this)

    private fun LocalDb.Item.toMessage() = Message(
        id = json.optString("id"), senderId = json.optString("senderId"), type = json.optString("type", Message.TYPE_TEXT),
        text = json.optString("text"), media = json.optString("media"), fileName = json.optString("fileName"),
        replyToId = json.optString("replyToId"), replyToText = json.optString("replyToText"),
        replyToSender = json.optString("replyToSender"), postId = json.optString("postId"),
        edited = json.optBoolean("edited"), deleted = json.optBoolean("deleted"),
        hearts = json.optJSONObject("hearts").toStringMap().mapValues { true },
        timestamp = json.optLong("timestamp"), pending = pending,
    )

    private fun Message.toJson() = JSONObject()
        .put("id", id).put("senderId", senderId).put("type", type).put("text", text).put("media", media)
        .put("fileName", fileName).put("replyToId", replyToId).put("replyToText", replyToText)
        .put("replyToSender", replyToSender).put("postId", postId).put("edited", edited).put("deleted", deleted)
        .put("hearts", JSONObject(hearts as Map<*, *>)).put("timestamp", timestamp)
}
