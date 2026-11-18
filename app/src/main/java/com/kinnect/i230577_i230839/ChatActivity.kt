package com.kinnect.i230577_i230839

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
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 21 · Chat — a one-to-one conversation through the messaging web services.
 *
 * Messages are read from SQLite first (so the chat opens instantly and offline) and the screen polls
 * messages/sync.php every few seconds, so new messages, edits, deletions and hearts appear without
 * leaving it. Send text, photos, videos, files or shared posts, reply to a message, heart a message,
 * and edit / delete your own messages for 5 minutes after sending (long press; the server enforces
 * the 5 minutes too). Anything sent offline shows "Sending…" and is delivered when the connection
 * returns. The header shows "Active now" live.
 * The phone and video icons start a call. Taking a screenshot posts "<name> took a screenshot"
 * in the chat and notifies the other person.
 */
class ChatActivity : BaseActivity() {

    private val otherUid by lazy { intent.getStringExtra(EXTRA_UID).orEmpty() }
    private val me by lazy { Db.uid.orEmpty() }

    private lateinit var input: EditText
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var sendButton: ImageView

    private var chatWatch: Watch? = null
    private var other = User()
    private val messages = linkedMapOf<String, Message>()
    private val views = hashMapOf<String, View>()
    private var replyTo: Message? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            ChatSender.sync(otherUid) { fresh -> fresh?.let(::showOther) }
            handler.postDelayed(this, POLL_MS)
        }
    }
    private val screenshots by lazy { ScreenshotDetector(this) { onScreenshot() } }
    private val askGalleryAccess = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    private var editing: Message? = null

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
        onTap(R.id.btn_send_image) { pickImageUri("image/*") { sendAttachment(Message.TYPE_IMAGE, it) } }
        onTap(R.id.btn_attach) { chooseAttachment() }
        onTap(R.id.btn_send) { onSendTapped() }
        input.doAfterTextChanged { updateSendIcon() }
        UserCache.get(me) { }
        // Before Android 14, spotting screenshots needs read access to the gallery
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            Gallery.permissions().none { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
        ) askGalleryAccess.launch(Gallery.permissions())
        findViewById<View>(R.id.chat_intro_date).visibility = View.GONE

        LocalDb.user(otherUid)?.let(::showOther)
        Users.load(otherUid) { showOther(it) }
        findViewById<TextView>(R.id.chat_intro_info).text =
            if (Friends.state(otherUid) == FriendState.FRIENDS) "You're friends on Kinnect" else "You're not friends on Kinnect"
        chatWatch = ChatSender.watch { uid -> if (uid == otherUid) render() }
        render()
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
        val name = (UserCache.peek(me) ?: LocalDb.user(me))?.firstName?.ifEmpty { null } ?: "Someone"
        ChatSender.send(otherUid, Message.TYPE_SYSTEM, text = "$name took a screenshot")
    }

    override fun onResume() {
        super.onResume()
        Push.openChatUid = otherUid
        handler.post(poll)
        markRead()
    }

    override fun onPause() {
        handler.removeCallbacks(poll)
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

    /** Rebuilds the thread from SQLite; only bubbles that changed are inflated again. */
    private fun render() {
        val latest = ChatSender.cached(otherUid).takeLast(MAX_MESSAGES)
        val hadNewIncoming = latest.any { it.id !in messages && it.senderId != me }
        val grew = latest.size > messages.size
        val previous = HashMap(messages)
        messages.clear()
        latest.forEach { messages[it.id] = it }
        views.keys.retainAll(messages.keys)
        list.removeAllViews()
        latest.forEach { message ->
            val view = if (previous[message.id] == message) views[message.id] else null
            val bubble = view ?: inflateMessage(message).also { views[message.id] = it }
            (bubble.parent as? ViewGroup)?.removeView(bubble)
            list.addView(bubble)
        }
        refreshGrouping()
        if (grew) scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        if (hadNewIncoming) markRead()
    }

    private fun inflateMessage(message: Message): View {
        val inflater = LayoutInflater.from(this)
        if (message.type == Message.TYPE_SYSTEM) {
            return inflater.inflate(R.layout.item_message_system, list, false).also {
                (it as TextView).text = message.text
            }
        }
        if (message.deleted) {
            return inflater.inflate(R.layout.item_message_system, list, false).also {
                (it as TextView).text = if (message.senderId == me) "You deleted a message" else "Message deleted"
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
                Images.show(view.findViewById(R.id.msg_image), message.media)
                imageCard.setOnClickListener { openMedia(message, "image/*") }
            }
            Message.TYPE_VIDEO -> {
                text.text = "🎬  Video · tap to play"
                text.setOnClickListener { openMedia(message, "video/*") }
            }
            Message.TYPE_FILE -> {
                text.text = "📎  ${message.fileName.ifEmpty { "File" }} · tap to open"
                text.setOnClickListener { openMedia(message, "*/*") }
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
        // "Sending…" while queued offline, "Edited" once changed
        view.findViewById<TextView>(R.id.msg_edited).apply {
            visibility = if (message.pending || message.edited) View.VISIBLE else View.GONE
            this.text = if (message.pending) "Sending…" else "Edited"
        }

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
        Posts.load(postId) { post ->
            val text = card.findViewById<TextView>(R.id.shared_post_text)
            if (post == null) {
                text.text = "This post is no longer available"
                card.setOnClickListener(null)
                return@load
            }
            text.text = post.text.ifEmpty { "Photo" }
            UserCache.get(post.authorId) { author ->
                card.findViewById<TextView>(R.id.shared_post_author).text = author.fullName
                Avatars.show(card.findViewById(R.id.shared_post_avatar), author)
            }
            if (post.imageCount > 0) {
                card.findViewById<View>(R.id.shared_post_image_card).visibility = View.VISIBLE
                post.images.firstOrNull()?.let { Images.show(card.findViewById(R.id.shared_post_image), it) }
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
        if (message.type == Message.TYPE_SYSTEM || message.deleted) return
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

    private fun toggleHeart(message: Message, hearted: Boolean) = ChatSender.heart(otherUid, message, !hearted)

    private fun deleteMessage(message: Message) {
        if (System.currentTimeMillis() - message.timestamp >= Message.EDIT_WINDOW_MS) {
            toast("Messages can only be deleted within 5 minutes")
            return
        }
        ChatSender.delete(otherUid, message)
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
            ChatSender.edit(otherUid, message, text)
        }
        input.setText("")
        clearComposerMode()
    }

    private fun sendMessage(type: String, text: String = "", file: File? = null, fileName: String = "") {
        ChatSender.send(otherUid, type, text = text, file = file, fileName = fileName, replyTo = replyTo)
        input.setText("")
        clearComposerMode()
    }

    /** "+" offers a video or any file; the camera-roll icon sends a photo. */
    private fun chooseAttachment() {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Video", "File")) { _, which ->
                if (which == 0) pickImageUri("video/*") { sendAttachment(Message.TYPE_VIDEO, it) }
                else pickImageUri("*/*") { sendAttachment(Message.TYPE_FILE, it) }
            }
            .show()
    }

    /** Copies the chosen item into the outbox off the main thread, then sends it (queued if offline). */
    private fun sendAttachment(type: String, uri: Uri) {
        worker.execute {
            val file = if (type == Message.TYPE_IMAGE) Outbox.photo(this, uri) else Outbox.copy(this, uri)
            val name = if (type == Message.TYPE_FILE) Outbox.displayName(this, uri) else ""
            runOnUiThread {
                if (file == null) toast(if (type == Message.TYPE_IMAGE) "Could not read that photo" else "That file is too large (max 38 MB)")
                else sendMessage(type, file = file, fileName = name)
            }
        }
    }

    /** Opens a photo, video or file with the phone's own viewer (from the server, or the outbox while sending). */
    private fun openMedia(message: Message, mime: String) {
        val source = message.media.ifEmpty { return }
        val uri = if (Images.isLocalFile(source)) FileProvider.getUriForFile(this, "$packageName.files", File(source)) else Uri.parse(source)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { toast("No app can open this") }
        }
    }

    private fun markRead() {
        // Only once there is a conversation, so opening an empty chat leaves no row behind in Chats
        if (otherUid.isEmpty() || messages.isEmpty()) return
        ChatSender.markRead(otherUid)
    }

    private fun updateSendIcon() {
        val hasText = input.text.isNotBlank()
        sendButton.setImageResource(if (hasText) R.drawable.ic_send else R.drawable.ic_like)
        sendButton.contentDescription = if (hasText) getString(R.string.cd_send) else "Send a like"
    }

    // ---- text helpers -------------------------------------------------------------------

    private fun previewOf(message: Message) = ChatSender.previewOf(message)

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
        handler.removeCallbacks(poll)
        chatWatch?.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_UID = "uid"
        private const val MAX_MESSAGES = 200
        private const val POLL_MS = 2_500L

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
