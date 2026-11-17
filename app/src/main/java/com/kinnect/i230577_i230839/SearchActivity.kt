package com.kinnect.i230577_i230839

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged

/**
 * 13 · Search — finds people by username or name while typing, through users/search.php.
 * The chips filter the results to Everyone, Friends or Requests (sent and received).
 * Offline, Friends and Requests still search the people cached in SQLite.
 */
class SearchActivity : BaseActivity() {

    private enum class Filter(val key: String, val viewId: Int) {
        EVERYONE("everyone", R.id.filter_everyone),
        FRIENDS("friends", R.id.filter_friends),
        REQUESTS("requests", R.id.filter_requests),
    }

    private lateinit var input: EditText
    private lateinit var results: LinearLayout
    private lateinit var empty: TextView
    private val rowWatches = mutableListOf<Watch>()
    private val handler = Handler(Looper.getMainLooper())
    private var filter = Filter.EVERYONE
    private var searchSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        input = findViewById(R.id.input_search)
        results = findViewById(R.id.people_results)
        empty = findViewById(R.id.people_empty)

        tapCloses(R.id.btn_back)
        onTap(R.id.btn_clear) { input.setText("") }
        findViewById<View>(R.id.btn_see_all_people).visibility = View.GONE
        Filter.entries.forEach { f -> onTap(f.viewId) { setFilter(f) } }
        // Wait for a short pause in typing so every keystroke doesn't hit the server
        input.doAfterTextChanged {
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ search() }, 250)
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) saveRecent()
            false
        }
        input.requestFocus()
        showRecent(LocalDb.recentSearches())
        Api.get("search/recent.php") { result -> result.json?.let { loadRecent(it.optJSONArray("recent")) } }
        search()
    }

    private fun setFilter(f: Filter) {
        filter = f
        Filter.entries.forEach { chip ->
            val view = findViewById<TextView>(chip.viewId)
            val selected = chip == f
            view.backgroundTintList = if (selected) ColorStateList.valueOf(ContextCompat.getColor(this, R.color.teal_light)) else null
            view.setTextColor(ContextCompat.getColor(this, if (selected) R.color.teal else R.color.text_primary))
        }
        search()
    }

    // ---- recent searches (saved on the server, cached in SQLite) ------------------------

    private fun saveRecent() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        Api.post("search/recent.php", mapOf("text" to text)) { result ->
            result.json?.let { loadRecent(it.optJSONArray("recent")) }
        }
    }

    private fun loadRecent(array: org.json.JSONArray?) {
        val texts = (0 until (array?.length() ?: 0)).map { array!!.getString(it) }
        LocalDb.setRecentSearches(texts)
        showRecent(texts)
    }

    private fun showRecent(recent: List<String>) {
        val list = findViewById<LinearLayout>(R.id.recent_list)
        list.removeAllViews()
        findViewById<View>(R.id.recent_title).visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
        recent.forEach { text ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_recent_search, list, false)
            row.findViewById<TextView>(R.id.recent_text).text = text
            row.setOnClickListener {
                input.setText(text)
                input.setSelection(text.length)
            }
            row.findViewById<View>(R.id.recent_remove).setOnClickListener {
                Api.post("search/recent.php", mapOf("text" to text, "remove" to "1")) { result ->
                    result.json?.let { loadRecent(it.optJSONArray("recent")) }
                }
            }
            list.addView(row)
        }
    }

    // ---- people ---------------------------------------------------------------------------

    private fun search() {
        val text = input.text.toString().trim()
        val seq = ++searchSeq
        if (text.isEmpty() && filter == Filter.EVERYONE) {
            showResults(emptyList(), "Type a name or username")
            return
        }
        Api.get("users/search.php", mapOf("q" to text, "filter" to filter.key)) { result ->
            if (seq != searchSeq) return@get // a newer search is on its way
            val json = result.json
            if (!result.ok || json == null) {
                if (result.offline) showResults(searchCached(text), "You're offline — no saved people match \"$text\"")
                return@get
            }
            val people = json.optJSONArray("results").mapObjects { item ->
                val user = item.getJSONObject("user").toUser()
                Friends.remember(user.uid, item.optString("state"))
                user
            }
            LocalDb.putUsers(people)
            showResults(people, if (text.isEmpty()) "No one here yet" else "No people found for \"$text\"")
        }
    }

    /** Offline fallback: friends and requests already saved on this phone. */
    private fun searchCached(text: String): List<User> {
        val states = when (filter) {
            Filter.FRIENDS -> listOf("friends")
            Filter.REQUESTS -> listOf("incoming", "requested")
            Filter.EVERYONE -> listOf("friends", "incoming", "requested")
        }
        val needle = text.lowercase()
        return states.flatMap { LocalDb.links(it) }.mapNotNull { LocalDb.user(it.userId) }
            .filter { needle.isEmpty() || it.username.startsWith(needle) || it.fullName.lowercase().split(" ").any { w -> w.startsWith(needle) } }
    }

    private fun showResults(users: List<User>, emptyText: String) {
        rowWatches.forEach(Watch::stop)
        rowWatches.clear()
        results.removeAllViews()
        users.forEach { user ->
            PersonRow.add(results, user, "@${user.username}")?.let(rowWatches::add)
            // Opening a result also remembers the search
            results.getChildAt(results.childCount - 1).setOnClickListener {
                saveRecent()
                PersonRow.openProfile(results, user.uid)
            }
        }
        empty.text = emptyText
        empty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        rowWatches.forEach(Watch::stop)
        super.onDestroy()
    }
}
