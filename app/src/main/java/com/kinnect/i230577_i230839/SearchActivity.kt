package com.kinnect.i230577_i230839

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ServerValue

/**
 * 13 · Search — finds people by username or name while typing, straight from the Realtime Database.
 * Two prefix queries (users.username and users.nameLower) are merged, so "omar" and "omar.f" both match.
 */
class SearchActivity : BaseActivity() {

    private lateinit var input: EditText
    private lateinit var results: LinearLayout
    private lateinit var empty: TextView
    private val rowWatches = mutableListOf<Watch>()
    private val recentWatch = Watch()
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        input = findViewById(R.id.input_search)
        results = findViewById(R.id.people_results)
        empty = findViewById(R.id.people_empty)

        tapCloses(R.id.btn_back)
        onTap(R.id.btn_clear) { input.setText("") }
        findViewById<View>(R.id.btn_see_all_people).visibility = View.GONE
        input.doAfterTextChanged { search(it?.toString().orEmpty()) }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) saveRecent()
            false
        }
        input.requestFocus()
        Db.uid?.let { me -> recentWatch.on(Db.recentSearches.child(me)) { showRecent(it) } }
    }

    // ---- recent searches (recentSearches/{uid}/{key} -> text + time) ----------------------

    private fun saveRecent() {
        val text = input.text.toString().trim()
        val me = Db.uid ?: return
        if (text.isEmpty()) return
        val key = text.lowercase().replace(Regex("[.#$\\[\\]/]"), "_")
        Db.recentSearches.child(me).child(key).setValue(mapOf("text" to text, "time" to ServerValue.TIMESTAMP))
    }

    private fun showRecent(snapshot: DataSnapshot) {
        val recent = snapshot.children.mapNotNull { child ->
            val text = child.child("text").getValue(String::class.java) ?: return@mapNotNull null
            Triple(child.key.orEmpty(), text, child.child("time").getValue(Long::class.java) ?: 0L)
        }.sortedByDescending { it.third }.take(MAX_RECENT)

        val list = findViewById<LinearLayout>(R.id.recent_list)
        list.removeAllViews()
        findViewById<View>(R.id.recent_title).visibility = if (recent.isEmpty()) View.GONE else View.VISIBLE
        recent.forEach { (key, text, _) ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_recent_search, list, false)
            row.findViewById<TextView>(R.id.recent_text).text = text
            row.setOnClickListener {
                input.setText(text)
                input.setSelection(text.length)
            }
            row.findViewById<View>(R.id.recent_remove).setOnClickListener {
                Db.uid?.let { me -> Db.recentSearches.child(me).child(key).removeValue() }
            }
            list.addView(row)
        }
    }

    private fun search(text: String) {
        val query = text.trim().lowercase()
        currentQuery = query
        if (query.isEmpty()) {
            showResults(emptyList(), "Type a name or username")
            return
        }
        val end = query + ""
        val found = linkedMapOf<String, User>()
        var pending = 2
        fun done() {
            if (--pending == 0 && query == currentQuery) {
                showResults(found.values.toList(), "No people found for \"$text\"")
            }
        }
        val collect = { snapshot: DataSnapshot ->
            snapshot.children.mapNotNull { it.getValue(User::class.java) }
                .filter { it.profileComplete }
                .forEach { found[it.uid] = it }
            done()
        }
        listOf("username", "nameLower").forEach { field ->
            Db.users.orderByChild(field).startAt(query).endAt(end).limitToFirst(LIMIT).get()
                .addOnSuccessListener(collect).addOnFailureListener { done() }
        }
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
        rowWatches.forEach(Watch::stop)
        recentWatch.stop()
        super.onDestroy()
    }

    private companion object {
        const val LIMIT = 20
        const val MAX_RECENT = 8
    }
}
