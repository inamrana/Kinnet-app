package com.inamulhaq.i230839

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

/**
 * 16 · Edit profile — loads users/{uid} and saves name, username, website and bio.
 * Usernames are kept unique through usernames/{username} -> uid.
 *
 * Opened with [EXTRA_SETUP] right after sign up / first log in: the user must pick a
 * username and bio, and Save then enters Home instead of going back.
 */
class EditProfileActivity : BaseActivity() {

    private lateinit var name: EditText
    private lateinit var username: EditText
    private lateinit var website: EditText
    private lateinit var bio: EditText
    private var current = User()
    private val setup by lazy { intent.getBooleanExtra(EXTRA_SETUP, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        name = findViewById(R.id.input_name)
        username = findViewById(R.id.input_username)
        website = findViewById(R.id.input_website)
        bio = findViewById(R.id.input_bio)

        if (setup) {
            findViewById<View>(R.id.btn_cancel).visibility = View.INVISIBLE
            findViewById<TextView>(R.id.edit_profile_title).text = "Set up profile"
        }
        tapCloses(R.id.btn_cancel)
        onTap(R.id.btn_save) { save() }
        // Photos are saved straight away, like in most social apps
        listOf(R.id.btn_edit_picture, R.id.edit_avatar).forEach { id ->
            onTap(id) { pickImage(maxSide = 400) { savePhoto("profileImage", it) } }
        }
        onTap(R.id.btn_edit_cover) { pickImage { savePhoto("coverImage", it) } }
        loadProfile()
    }

    private fun savePhoto(field: String, base64: String) {
        Db.me()?.child(field)?.setValue(base64)?.addOnSuccessListener {
            current = if (field == "profileImage") current.copy(profileImage = base64) else current.copy(coverImage = base64)
            showPhotos()
        }
    }

    private fun showPhotos() {
        Avatars.show(findViewById(R.id.edit_avatar), current)
        Images.show(findViewById<ImageView>(R.id.edit_cover), current.coverImage)
    }

    private fun loadProfile() {
        Db.me()?.get()?.addOnSuccessListener { snapshot ->
            current = snapshot.getValue(User::class.java) ?: return@addOnSuccessListener
            name.setText(current.fullName)
            username.setText(current.username)
            website.setText(current.website)
            bio.setText(current.bio)
            showPhotos()
            findViewById<TextView>(R.id.value_email).text = current.email
            findViewById<TextView>(R.id.value_phone).text = current.phone.ifEmpty { "Not added" }
            findViewById<TextView>(R.id.value_gender).text = current.gender
        }
    }

    private fun save() {
        val uid = Db.uid ?: return
        val newName = name.text.toString().trim()
        val newUsername = username.text.toString().trim().lowercase()
        when {
            newName.isEmpty() -> { name.error = "Required"; return }
            !USERNAME_RULE.matches(newUsername) -> {
                username.error = "3–30 letters, numbers, . or _"
                return
            }
            setup && bio.text.isBlank() -> { bio.error = "Add a short bio"; return }
        }

        findViewById<View>(R.id.btn_save).isEnabled = false
        if (newUsername == current.username) {
            saveFields(uid, newName, newUsername)
        } else {
            claimUsername(uid, newUsername) { claimed ->
                if (claimed) {
                    if (current.username.isNotEmpty()) Db.usernames.child(current.username).removeValue()
                    saveFields(uid, newName, newUsername)
                } else {
                    findViewById<View>(R.id.btn_save).isEnabled = true
                    username.error = "That username is taken"
                }
            }
        }
    }

    /** Atomically reserves usernames/{name} for [uid]; fails if someone else already owns it. */
    private fun claimUsername(uid: String, name: String, done: (Boolean) -> Unit) {
        Db.usernames.child(name).runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val owner = data.getValue(String::class.java)
                if (owner != null && owner != uid) return Transaction.abort()
                data.value = uid
                return Transaction.success(data)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                runOnUiThread { done(error == null && committed) }
            }
        })
    }

    private fun saveFields(uid: String, newName: String, newUsername: String) {
        val parts = newName.split(" ", limit = 2)
        val updates = mapOf(
            "fullName" to newName,
            "nameLower" to newName.lowercase(),
            "firstName" to parts[0],
            "lastName" to parts.getOrElse(1) { "" },
            "username" to newUsername,
            "website" to website.text.toString().trim(),
            "bio" to bio.text.toString().trim(),
            "profileComplete" to true,
        )
        Db.user(uid).updateChildren(updates)
            .addOnSuccessListener {
                RecentLogin(this).save(current.copy(fullName = newName))
                if (setup) enterHome() else finish()
            }
            .addOnFailureListener {
                findViewById<View>(R.id.btn_save).isEnabled = true
                toast(it.localizedMessage ?: "Could not save")
            }
    }

    companion object {
        const val EXTRA_SETUP = "setup"
        private val USERNAME_RULE = Regex("^[a-z0-9._]{3,30}$")
    }
}
