package com.inamulhaq.i230839

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.database.DataSnapshot

/**
 * 13 · Search — finds people by username or name while typing, straight from the Realtime Database.
 * Two prefix queries (users.username and users.nameLower) are merged, so "omar" and "omar.f" both match.
 */
class SearchActivity : BaseActivity() {

    private lateinit var input: EditText
    private lateinit var results: LinearLayout
    private lateinit var empty: TextView
    private val rowWatches = mutableListOf<Watch>()
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
        input.requestFocus()
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
        }
        empty.text = emptyText
        empty.visibility = if (users.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        rowWatches.forEach(Watch::stop)
        super.onDestroy()
    }

    private companion object {
        const val LIMIT = 20
    }
}
