package com.kinnect.i230577_i230839

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * The on-device SQLite cache. Screens read from here first (so they open instantly and work
 * offline) and then refresh from the server. Actions taken offline wait in pending_actions
 * until [Sync] sends them.
 *
 * users           id → profile JSON
 * friend_links    my relation to each person: friends / requested / incoming
 * pending_actions queued actions, oldest first, each with a client UUID
 * recent_searches Search history
 * cache           other server lists by key (suggestions, …)
 */
object LocalDb {

    private lateinit var helper: Helper
    private val db: SQLiteDatabase get() = helper.writableDatabase

    fun init(context: Context) {
        helper = Helper(context.applicationContext)
    }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "kinnect.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE users (id TEXT PRIMARY KEY, json TEXT NOT NULL, updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE friend_links (user_id TEXT PRIMARY KEY, state TEXT NOT NULL, time INTEGER NOT NULL DEFAULT 0, mutual INTEGER NOT NULL DEFAULT 0)")
            db.execSQL(
                """CREATE TABLE pending_actions (
                    local_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    client_id TEXT NOT NULL UNIQUE,
                    type TEXT NOT NULL,
                    endpoint TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    target TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    attempts INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT NOT NULL DEFAULT '')"""
            )
            db.execSQL("CREATE TABLE recent_searches (text TEXT PRIMARY KEY, time INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE cache (key TEXT PRIMARY KEY, json TEXT NOT NULL, updated_at INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    // ---- users -----------------------------------------------------------------------------

    fun putUser(user: User) {
        if (user.uid.isEmpty()) return
        db.insertWithOnConflict("users", null, ContentValues().apply {
            put("id", user.uid)
            put("json", user.toJson().toString())
            put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun putUsers(users: Collection<User>) = db.runInTx { users.forEach(::putUser) }

    fun user(id: String): User? =
        db.rawQuery("SELECT json FROM users WHERE id = ?", arrayOf(id)).use { c ->
            if (c.moveToFirst()) JSONObject(c.getString(0)).toUser() else null
        }

    // ---- friends ---------------------------------------------------------------------------

    class Link(val userId: String, val state: String, val time: Long, val mutual: Int)

    fun setLink(userId: String, state: String, time: Long = System.currentTimeMillis(), mutual: Int = 0) {
        if (state == "none" || state == "self") {
            db.delete("friend_links", "user_id = ?", arrayOf(userId))
        } else {
            db.insertWithOnConflict("friend_links", null, ContentValues().apply {
                put("user_id", userId); put("state", state); put("time", time); put("mutual", mutual)
            }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun linkState(userId: String): String =
        db.rawQuery("SELECT state FROM friend_links WHERE user_id = ?", arrayOf(userId)).use { c ->
            if (c.moveToFirst()) c.getString(0) else "none"
        }

    fun links(state: String): List<Link> =
        db.rawQuery("SELECT user_id, state, time, mutual FROM friend_links WHERE state = ? ORDER BY time DESC", arrayOf(state)).use { c ->
            buildList { while (c.moveToNext()) add(Link(c.getString(0), c.getString(1), c.getLong(2), c.getInt(3))) }
        }

    /** Replaces every link with the server's list, keeping links that still have a queued change. */
    fun replaceLinks(links: List<Link>) = db.runInTx {
        val pendingTargets = pendingTargets("friend")
        db.delete("friend_links", if (pendingTargets.isEmpty()) null else
            "user_id NOT IN (${pendingTargets.joinToString(",") { "?" }})", pendingTargets.toTypedArray())
        links.filter { it.userId !in pendingTargets }.forEach { setLink(it.userId, it.state, it.time, it.mutual) }
    }

    // ---- pending actions (offline queue) ---------------------------------------------------

    class Pending(
        val localId: Long, val clientId: String, val type: String, val endpoint: String,
        val payload: JSONObject, val target: String, val attempts: Int,
    )

    fun addPending(clientId: String, type: String, endpoint: String, payload: JSONObject, target: String) {
        db.insert("pending_actions", null, ContentValues().apply {
            put("client_id", clientId); put("type", type); put("endpoint", endpoint)
            put("payload", payload.toString()); put("target", target); put("created_at", System.currentTimeMillis())
        })
    }

    fun pending(): List<Pending> =
        db.rawQuery("SELECT local_id, client_id, type, endpoint, payload, target, attempts FROM pending_actions ORDER BY local_id", null).use { c ->
            buildList {
                while (c.moveToNext()) add(Pending(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), JSONObject(c.getString(4)), c.getString(5), c.getInt(6)))
            }
        }

    fun removePending(localId: Long) {
        db.delete("pending_actions", "local_id = ?", arrayOf(localId.toString()))
    }

    fun markAttempt(localId: Long, error: String) {
        db.execSQL("UPDATE pending_actions SET attempts = attempts + 1, last_error = ? WHERE local_id = ?", arrayOf(error, localId))
    }

    fun pendingTargets(type: String): Set<String> =
        db.rawQuery("SELECT DISTINCT target FROM pending_actions WHERE type = ?", arrayOf(type)).use { c ->
            buildSet { while (c.moveToNext()) add(c.getString(0)) }
        }

    fun pendingCount(): Int = db.rawQuery("SELECT COUNT(*) FROM pending_actions", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    // ---- recent searches + generic cache ---------------------------------------------------

    fun setRecentSearches(texts: List<String>) = db.runInTx {
        db.delete("recent_searches", null, null)
        val now = System.currentTimeMillis()
        texts.forEachIndexed { i, text ->
            db.insert("recent_searches", null, ContentValues().apply { put("text", text); put("time", now - i) })
        }
    }

    fun recentSearches(): List<String> =
        db.rawQuery("SELECT text FROM recent_searches ORDER BY time DESC LIMIT 8", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    fun putCache(key: String, json: String) {
        db.insertWithOnConflict("cache", null, ContentValues().apply {
            put("key", key); put("json", json); put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun cache(key: String): String? =
        db.rawQuery("SELECT json FROM cache WHERE key = ?", arrayOf(key)).use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** Log out: nothing from this account may stay on the device. */
    fun clearAll() = db.runInTx {
        listOf("users", "friend_links", "pending_actions", "recent_searches", "cache").forEach { db.delete(it, null, null) }
    }

    private inline fun SQLiteDatabase.runInTx(block: () -> Unit) {
        beginTransaction()
        try { block(); setTransactionSuccessful() } finally { endTransaction() }
    }
}
