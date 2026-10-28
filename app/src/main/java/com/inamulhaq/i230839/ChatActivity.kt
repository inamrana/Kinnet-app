package com.inamulhaq.i230839

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 21 · Chat — a live one-to-one conversation stored in chats/{chatId}/messages.
 *
 * Send text, photos or shared posts, reply to a message, heart a message, and edit / delete your own
 * messages for 5 minutes after sending (long press). The header shows "Active now" live.
 * The phone and video icons start a call. Taking a screenshot posts "<name> took a screenshot"
 * in the chat and notifies the other person.
 */
class ChatActivity : BaseActivity() {

    private val otherUid by lazy { intent.getStringExtra(EXTRA_UID).orEmpty() }
    private val me by lazy { Db.uid.orEmpty() }
    private val chatId by lazy { Db.chatId(me, otherUid) }

    private lateinit var input: EditText
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var sendButton: ImageView

    private val watch = Watch()
    private var other = User()
    private val messages = linkedMapOf<String, Message>()
    private val views = hashMapOf<String, View>()
    private var messagesQuery: Query? = null
    private var replyTo: Message? = null
    private val screenshots by lazy { ScreenshotDetector(this) { onScreenshot() } }
    private val askGalleryAccess = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    private var editing: Message? = null

    private val messageListener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previous: String?) = upsert(snapshot)
        override fun onChildChanged(snapshot: DataSnapshot, previous: String?) = upsert(snapshot)
        override fun onChildRemoved(snapshot: DataSnapshot) {
            val id = snapshot.key ?: return
            messages.remove(id)
            views.remove(id)?.let(list::removeView)
            refreshGrouping()
        }
        override fun onChildMoved(snapshot: DataSnapshot, previous: String?) = Unit
        override fun onCancelled(error: DatabaseError) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        if (otherUid.isEmpty() || me.isEmpty()) { finish(); return }

        input = findViewById(R.id.input_message)
        list = findViewById(R.id.messages_list)
        scroll = findViewById(R.id.chat_scroll)
        sendButton = findViewById(R.id.btn_send)

        tapCloses(R.id.btn_back)
        onTap(R.id.btn_call) { Calls.start(this, otherUid, video = false) }
        onTap(R.id.btn_video) { Calls.start(this, otherUid, video = true) }
        onTap(R.id.btn_info) { PersonRow.openProfile(list, otherUid) }
        onTap(R.id.chat_header_avatar) { PersonRow.openProfile(list, otherUid) }
        onTap(R.id.reply_cancel) { clearComposerMode() }
        onTap(R.id.btn_send_image) { pickImage { sendMessage(Message.TYPE_IMAGE, image = it) } }
        onTap(R.id.btn_send) { onSendTapped() }
        input.doAfterTextChanged { updateSendIcon() }
        UserCache.get(me) { }
        // Before Android 14, spotting screenshots needs read access to the gallery
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            Gallery.permissions().none { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        ) askGalleryAccess.launch(Gallery.permissions())
        findViewById<View>(R.id.chat_intro_date).visibility = View.GONE

        watch.on(Db.user(otherUid)) { snapshot -> snapshot.getValue(User::class.java)?.let(::showOther) }
        watch.on(Db.friends.child(me).child(otherUid)) { snapshot ->
            findViewById<TextView>(R.id.chat_intro_info).text =
                if (snapshot.exists()) "You're friends on Kinnect" else "You're not friends on Kinnect"
        }
        messagesQuery = Db.messages(chatId).orderByChild("timestamp").limitToLast(MAX_MESSAGES)
            .also { it.addChildEventListener(messageListener) }
    }

    override fun onStart() {
        super.onStart()
        screenshots.start()
    }

    override fun onStop() {
        screenshots.stop()
        super.onStop()
    }

    /** Lets the other person know, as in the design's "Aisha took a screenshot". */
    private fun onScreenshot() {
        val name = UserCache.peek(me)?.firstName?.ifEmpty { null } ?: "Someone"
        val text = "$name took a screenshot"
        ChatSender.send(otherUid, Message.TYPE_SYSTEM, text = text, preview = text)
    }

    override fun onResume() {
        super.onResume()
        Push.openChatUid = otherUid
        markRead()
    }

    override fun onPause() {
        if (Push.openChatUid == otherUid) Push.openChatUid = null
        super.onPause()
    }

    // ---- header -------------------------------------------------------------------------

    private fun showOther(user: User) {
        other = user
        findViewById<TextView>(R.id.chat_header_name).text = user.fullName
        findViewById<TextView>(R.id.chat_intro_name).text = user.fullName
        Avatars.show(findViewById(R.id.chat_header_avatar), user)
        Avatars.show(findViewById(R.id.chat_intro_avatar), user)
        findViewById<View>(R.id.chat_header_online).visibility = if (user.online) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.chat_header_status).text = presenceText(user)
        refreshGrouping()
    }

    // ---- messages -----------------------------------------------------------------------

    private fun upsert(snapshot: DataSnapshot) {
        val message = snapshot.getValue(Message::class.java)?.copy(id = snapshot.key.orEmpty()) ?: return
        val isNew = message.id !in messages
        messages[message.id] = message
        val view = inflateMessage(message)
        val old = views.put(message.id, view)
        if (old == null) {
            list.addView(view)
        } else {
            list.addView(view, list.indexOfChild(old))
            list.removeView(old)
        }
        refreshGrouping()
        if (isNew) {
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            if (message.senderId != me) markRead()
        }
    }

    private fun inflateMessage(message: Message): View {
        val inflater = LayoutInflater.from(this)
        if (message.type == Message.TYPE_SYSTEM) {
            return inflater.inflate(R.layout.item_message_system, list, false).also {
                (it as TextView).text = message.text
            }
        }
        val mine = message.senderId == me
        val view = inflater.inflate(if (mine) R.layout.item_message_out else R.layout.item_message_in, list, false)
        val text = view.findViewById<TextView>(R.id.msg_text)
        val imageCard = view.findViewById<View>(R.id.msg_image_card)
        val postCard = view.findViewById<View>(R.id.msg_post_card)
        when (message.type) {
            Message.TYPE_IMAGE -> {
                text.visibility = View.GONE
                imageCard.visibility = View.VISIBLE
                Images.show(view.findViewById(R.id.msg_image), message.image)
            }
            Message.TYPE_POST -> {
                text.visibility = View.GONE
                postCard.visibility = View.VISIBLE
                bindSharedPost(postCard, message.postId)
            }
            else -> text.text = message.text
        }

        if (message.replyToId.isNotEmpty()) {
            view.findViewById<TextView>(R.id.msg_reply_label).apply {
                visibility = View.VISIBLE
                this.text = replyLabel(message)
            }
            view.findViewById<TextView>(R.id.msg_reply_quote).apply {
                visibility = View.VISIBLE
                this.text = message.replyToText
            }
        }

        val hearted = message.hearts.isNotEmpty()
        view.findViewById<View>(R.id.msg_heart).visibility = if (hearted) View.VISIBLE else View.GONE
        if (hearted) {
            // Leave room under the bubble for the overlapping heart, as in the design
            val bubble = when (message.type) {
                Message.TYPE_IMAGE -> imageCard
                Message.TYPE_POST -> postCard
                else -> text
            }
            (bubble.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = dp(12)
        }
        view.findViewById<View>(R.id.msg_edited).visibility = if (message.edited) View.VISIBLE else View.GONE

        val body = view.findViewById<View>(R.id.msg_body)
        body.setOnLongClickListener { showOptions(message); true }
        text.setOnLongClickListener { showOptions(message); true }
        imageCard.setOnLongClickListener { showOptions(message); true }
        postCard.setOnLongClickListener { showOptions(message); true }
        return view
    }

    /** Fills the shared-post card and opens the post's comments when tapped. */
    private fun bindSharedPost(card: View, postId: String) {
        card.setOnClickListener { PostCard.openComments(this, postId) }
        Db.posts.child(postId).get().addOnSuccessListener { snapshot ->
            val post = snapshot.getValue(Post::class.java)
            val text = card.findViewById<TextView>(R.id.shared_post_text)
            if (post == null) {
                text.text = "This post is no longer available"
                card.setOnClickListener(null)
                return@addOnSuccessListener
            }
            text.text = post.text.ifEmpty { "Photo" }
            UserCache.get(post.authorId) { author ->
                card.findViewById<TextView>(R.id.shared_post_author).text = author.fullName
                Avatars.show(card.findViewById(R.id.shared_post_avatar), author)
            }
            if (post.imageCount > 0) {
                card.findViewById<View>(R.id.shared_post_image_card).visibility = View.VISIBLE
                PostImages.get(postId) { images ->
                    images.firstOrNull()?.let { Images.show(card.findViewById(R.id.shared_post_image), it) }
                }
            }
        }
    }

    /** Incoming messages show the small avatar only on the last bubble of a run, like the design. */
    private fun refreshGrouping() {
        val ordered = messages.values.toList()
        ordered.forEachIndexed { index, message ->
            val avatar = views[message.id]?.findViewById<TextView>(R.id.msg_avatar) ?: return@forEachIndexed
            val next = ordered.getOrNull(index + 1)
            val lastOfRun = next == null || next.senderId != message.senderId || next.type == Message.TYPE_SYSTEM
            avatar.visibility = if (lastOfRun) View.VISIBLE else View.INVISIBLE
            if (lastOfRun && other.uid.isNotEmpty()) Avatars.show(avatar, other)
        }
        ordered.firstOrNull()?.let { first ->
            findViewById<TextView>(R.id.chat_intro_date).apply {
                visibility = View.VISIBLE
                text = dateLabel(first.timestamp)
            }
        }
    }

    private fun showOptions(message: Message) {
        if (message.type == Message.TYPE_SYSTEM) return
        val mine = message.senderId == me
        val canChange = mine && System.currentTimeMillis() - message.timestamp < Message.EDIT_WINDOW_MS
        val hearted = message.hearts.containsKey(me)
        val options = mutableListOf<Pair<String, () -> Unit>>(
            "Reply" to { startReply(message) },
            (if (hearted) "Remove heart" else "Heart ❤") to { toggleHeart(message, hearted) },
        )
        if (canChange && message.type == Message.TYPE_TEXT) options += "Edit" to { startEdit(message) }
        if (canChange) options += "Delete" to { deleteMessage(message) }

        AlertDialog.Builder(this)
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .show()
    }

    private fun toggleHeart(message: Message, hearted: Boolean) {
        val ref = Db.messages(chatId).child(message.id).child("hearts").child(me)
        if (hearted) ref.removeValue() else ref.setValue(true)
    }

    private fun deleteMessage(message: Message) {
        if (System.currentTimeMillis() - message.timestamp >= Message.EDIT_WINDOW_MS) {
            toast("Messages can only be deleted within 5 minutes")
            return
        }
        Db.messages(chatId).child(message.id).removeValue()
        if (messages.keys.lastOrNull() == message.id) updateSummaries("Message deleted", bumpUnread = false)
    }

    // ---- composer -----------------------------------------------------------------------

    private fun startReply(message: Message) {
        editing = null
        replyTo = message
        val name = if (message.senderId == me) "yourself" else other.firstName.ifEmpty { other.fullName }
        showComposerBar("Replying to $name", previewOf(message))
        input.requestFocus()
    }

    private fun startEdit(message: Message) {
        replyTo = null
        editing = message
        showComposerBar("Editing message", message.text)
        input.setText(message.text)
        input.setSelection(input.text.length)
        input.requestFocus()
    }

    private fun showComposerBar(title: String, text: String) {
        findViewById<View>(R.id.reply_bar).visibility = View.VISIBLE
        findViewById<TextView>(R.id.reply_title).text = title
        findViewById<TextView>(R.id.reply_text).text = text
    }

    private fun clearComposerMode() {
        if (editing != null) input.setText("")
        replyTo = null
        editing = null
        findViewById<View>(R.id.reply_bar).visibility = View.GONE
    }

    /** Empty box sends a like 👍 (the design's like button); otherwise it sends or saves the text. */
    private fun onSendTapped() {
        val text = input.text.toString().trim()
        val edit = editing
        when {
            edit != null -> saveEdit(edit, text)
            text.isEmpty() -> sendMessage(Message.TYPE_TEXT, text = "👍")
            else -> sendMessage(Message.TYPE_TEXT, text = text)
        }
    }

    private fun saveEdit(message: Message, text: String) {
        if (text.isEmpty()) return
        if (System.currentTimeMillis() - message.timestamp >= Message.EDIT_WINDOW_MS) {
            toast("Messages can only be edited within 5 minutes")
        } else {
            Db.messages(chatId).child(message.id).updateChildren(mapOf("text" to text, "edited" to true))
            if (messages.keys.lastOrNull() == message.id) updateSummaries(text, bumpUnread = false)
        }
        input.setText("")
        clearComposerMode()
    }

    private fun sendMessage(type: String, text: String = "", image: String = "") {
        val ref = Db.messages(chatId).push()
        val data = mutableMapOf<String, Any>(
            "senderId" to me,
            "type" to type,
            "text" to text,
            "image" to image,
            "timestamp" to ServerValue.TIMESTAMP,
        )
        replyTo?.let {
            data["replyToId"] = it.id
            data["replyToText"] = previewOf(it)
            data["replyToSender"] = it.senderId
        }
        ref.setValue(data)
        updateSummaries(if (type == Message.TYPE_IMAGE) "Sent a photo" else text, bumpUnread = true)
        input.setText("")
        clearComposerMode()
    }

    /** Keeps both people's Chats list rows (last message, time, unread count) up to date. */
    private fun updateSummaries(preview: String, bumpUnread: Boolean) {
        Db.update(ChatSender.summaries(me, otherUid, preview, bumpUnread))
    }

    private fun markRead() {
        if (me.isEmpty() || otherUid.isEmpty()) return
        Db.userChats.child(me).child(otherUid).child("unread").setValue(0)
    }

    private fun updateSendIcon() {
        val hasText = input.text.isNotBlank()
        sendButton.setImageResource(if (hasText) R.drawable.ic_send else R.drawable.ic_like)
        sendButton.contentDescription = if (hasText) getString(R.string.cd_send) else "Send a like"
    }

    // ---- text helpers -------------------------------------------------------------------

    private fun previewOf(message: Message) = when (message.type) {
        Message.TYPE_IMAGE -> "Photo"
        Message.TYPE_POST -> "Shared a post"
        else -> message.text
    }

    private fun replyLabel(message: Message): String {
        val otherName = other.firstName.ifEmpty { other.fullName }
        return when {
            message.senderId == me && message.replyToSender == me -> "You replied to yourself"
            message.senderId == me -> "You replied to $otherName"
            message.replyToSender == me -> "$otherName replied to you"
            else -> "$otherName replied to themselves"
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        watch.stop()
        messagesQuery?.removeEventListener(messageListener)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_UID = "uid"
        private const val MAX_MESSAGES = 200

        /** "Active now", "Active 5m ago" or "Offline" — used by the chat header. */
        fun presenceText(user: User): String = when {
            user.online -> "Active now"
            user.lastSeen > 0 -> TimeAgo.short(user.lastSeen).let { if (it == "now") "Active just now" else "Active $it ago" }
            else -> "Offline"
        }

        private fun dateLabel(time: Long): String {
            val day = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val today = day.format(Date()) == day.format(Date(time))
            val pattern = if (today) "'TODAY' h:mm a" else "MMM d, h:mm a"
            return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time)).uppercase()
        }
    }
}
