package com.kinnect.i230577_i230839

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Talks to the Kinnect PHP backend (BuildConfig.API_BASE + "api/…"). Every request carries the
 * session token as "Authorization: Bearer …". Responses are JSON: { ok, …data } or { ok:false, error }.
 *
 * The async helpers run on a small thread pool and call back on the main thread.
 */
object Api {

    class Result(val code: Int, val json: JSONObject?, val error: String?) {
        val ok: Boolean get() = code in 200..299 && json?.optBoolean("ok") == true
        /** No response at all: no internet, server unreachable or timed out. */
        val offline: Boolean get() = code == 0
        val message: String get() = error ?: json?.optString("error")?.takeIf { it.isNotEmpty() } ?: "Something went wrong"
    }

    private val io = Executors.newFixedThreadPool(4)
    private val main = Handler(Looper.getMainLooper())

    /** Called (on the main thread) when the server says the token is no longer valid. */
    var onUnauthorized: (() -> Unit)? = null

    fun get(path: String, params: Map<String, Any?> = emptyMap(), done: (Result) -> Unit) =
        async(done) { request("GET", path, params) }

    fun post(path: String, fields: Map<String, Any?> = emptyMap(), done: (Result) -> Unit) =
        async(done) { request("POST", path, fields) }

    fun upload(path: String, fields: Map<String, Any?>, files: Map<String, File>, done: (Result) -> Unit) =
        async(done) { request("POST", path, fields, files) }

    private fun async(done: (Result) -> Unit, work: () -> Result) {
        io.execute {
            val result = work()
            main.post {
                if (result.code == 401 && Session.isLoggedIn) onUnauthorized?.invoke()
                done(result)
            }
        }
    }

    /** Blocking request — only call off the main thread (the sync worker uses this directly). */
    fun request(
        method: String, path: String, fields: Map<String, Any?>,
        files: Map<String, File> = emptyMap(), token: String? = Session.token,
    ): Result {
        val query = if (method == "GET" && fields.isNotEmpty()) "?" + encode(fields) else ""
        val connection = try {
            URL(BuildConfig.API_BASE + "api/" + path + query).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return Result(0, null, "Can't reach the server")
        }
        return try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = if (files.isEmpty()) 20_000 else 120_000
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            when {
                files.isNotEmpty() -> writeMultipart(connection, fields, files)
                method == "POST" -> {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(toJson(fields).toString().toByteArray()) }
                }
            }
            val code = connection.responseCode
            val body = (if (code >= 400) connection.errorStream else connection.inputStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrNull()
            Result(code, json, if (json == null && code >= 400) "Server error ($code)" else null)
        } catch (e: IOException) {
            Result(0, null, "You're offline")
        } finally {
            connection.disconnect()
        }
    }

    private fun writeMultipart(connection: HttpURLConnection, fields: Map<String, Any?>, files: Map<String, File>) {
        val boundary = "kinnect" + UUID.randomUUID().toString().replace("-", "")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.setChunkedStreamingMode(64 * 1024)
        DataOutputStream(connection.outputStream).use { out ->
            fields.forEach { (key, value) ->
                if (value == null) return@forEach
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"\r\n\r\n")
                out.write(value.toString().toByteArray())
                out.writeBytes("\r\n")
            }
            files.forEach { (key, file) ->
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$key\"; filename=\"${file.name}\"\r\n")
                out.writeBytes("Content-Type: application/octet-stream\r\n\r\n")
                file.inputStream().use { it.copyTo(out) }
                out.writeBytes("\r\n")
            }
            out.writeBytes("--$boundary--\r\n")
        }
    }

    private fun encode(fields: Map<String, Any?>) = fields.entries.filter { it.value != null }
        .joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v.toString(), "UTF-8")}" }

    fun toJson(fields: Map<String, Any?>): JSONObject = JSONObject().apply {
        fields.forEach { (k, v) -> if (v != null) put(k, v) }
    }
}

// ---- JSON <-> app models ------------------------------------------------------------------

fun JSONObject.toUser(): User = User(
    uid = optString("uid"),
    firstName = optString("firstName"),
    lastName = optString("lastName"),
    fullName = optString("fullName"),
    nameLower = optString("nameLower"),
    username = optString("username"),
    email = optString("email"),
    gender = optString("gender"),
    birthday = optString("birthday"),
    bio = optString("bio"),
    website = optString("website"),
    phone = optString("phone"),
    profileImage = optString("profileImage"),
    coverImage = optString("coverImage"),
    profileComplete = optBoolean("profileComplete"),
    online = optBoolean("online"),
    lastSeen = optLong("lastSeen"),
    createdAt = optLong("createdAt"),
)

fun User.toJson(): JSONObject = JSONObject()
    .put("uid", uid).put("firstName", firstName).put("lastName", lastName).put("fullName", fullName)
    .put("nameLower", nameLower).put("username", username).put("email", email).put("gender", gender)
    .put("birthday", birthday).put("bio", bio).put("website", website).put("phone", phone)
    .put("profileImage", profileImage).put("coverImage", coverImage).put("profileComplete", profileComplete)
    .put("online", online).put("lastSeen", lastSeen).put("createdAt", createdAt)

inline fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { optJSONObject(it)?.let(transform) }
}
