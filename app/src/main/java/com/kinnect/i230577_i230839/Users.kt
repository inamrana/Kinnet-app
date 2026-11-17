package com.kinnect.i230577_i230839

import org.json.JSONObject
import java.io.File

/**
 * Profiles from the web service, cached in SQLite. Every loader shows the cached copy first
 * (instant and offline) and then the fresh one from the server when it arrives.
 */
object Users {

    /** A profile as shown on Profile / Other profile. */
    class Profile(
        val user: User,
        val state: FriendState,
        val friendCount: Int,
        val mutualCount: Int,
        val mutual: List<User>,
    )

    fun load(id: String, onUser: (User) -> Unit) {
        LocalDb.user(id)?.let(onUser)
        Api.get("users/get.php", mapOf("id" to id)) { result ->
            val user = result.json?.optJSONObject("user")?.takeIf { result.ok }?.toUser() ?: return@get
            LocalDb.putUser(user)
            onUser(user)
        }
    }

    fun profile(id: String, onProfile: (Profile) -> Unit) {
        LocalDb.cache("profile:$id")?.let { onProfile(parse(JSONObject(it))) }
        Api.get("users/get.php", mapOf("id" to id)) { result ->
            val json = result.json?.takeIf { result.ok } ?: return@get
            LocalDb.putCache("profile:$id", json.toString())
            val profile = parse(json)
            LocalDb.putUser(profile.user)
            if (id != Session.uid) Friends.remember(id, profile.state.key)
            onProfile(profile)
        }
    }

    private fun parse(json: JSONObject) = Profile(
        user = json.getJSONObject("user").toUser(),
        state = FriendState.of(json.optString("state")),
        friendCount = json.optInt("friendCount"),
        mutualCount = json.optInt("mutualCount"),
        mutual = json.optJSONArray("mutual").mapObjects { it.toUser() },
    )

    /**
     * Saves profile changes (any of name, username, website, bio) and optional new photos.
     * [done] gets the updated user, or an error message.
     */
    fun update(
        fields: Map<String, Any?>,
        avatar: File? = null,
        cover: File? = null,
        done: (User?, String?) -> Unit,
    ) {
        val files = buildMap { avatar?.let { put("avatar", it) }; cover?.let { put("cover", it) } }
        Api.upload("users/update.php", fields, files) { result ->
            avatar?.delete(); cover?.delete()
            val user = result.json?.optJSONObject("user")?.takeIf { result.ok }?.toUser()
            if (user != null) {
                LocalDb.putUser(user)
                LocalDb.putCache("profile:${user.uid}", JSONObject().put("user", user.toJson()).toString())
                done(user, null)
            } else {
                done(null, if (result.offline) "You're offline. Connect to save your profile." else result.message)
            }
        }
    }
}
