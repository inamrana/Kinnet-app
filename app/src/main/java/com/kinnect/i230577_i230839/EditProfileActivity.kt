package com.kinnect.i230577_i230839

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView

/**
 * 16 · Edit profile — loads the profile (cached first) and saves name, username, website and bio
 * through users/update.php. The server keeps usernames unique and stores the profile picture and
 * cover photo as files in its uploads folder.
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
        // Photos are uploaded straight away, like in most social apps
        listOf(R.id.btn_edit_picture, R.id.edit_avatar).forEach { id ->
            onTap(id) { pickImageFile(maxSide = 600) { uploadPhoto(avatar = it) } }
        }
        onTap(R.id.btn_edit_cover) { pickImageFile { uploadPhoto(cover = it) } }
        loadProfile()
    }

    private fun uploadPhoto(avatar: java.io.File? = null, cover: java.io.File? = null) {
        toast("Uploading…")
        Users.update(emptyMap(), avatar, cover) { user, error ->
            if (user == null) return@update toast(error ?: "Could not upload the photo")
            current = user
            showPhotos()
        }
    }

    private fun showPhotos() {
        Avatars.show(findViewById(R.id.edit_avatar), current)
        Images.show(findViewById<ImageView>(R.id.edit_cover), current.coverImage)
    }

    private fun loadProfile() {
        val me = Session.uid ?: return
        var filled = false
        Users.load(me) { user ->
            current = user
            // Don't overwrite what the user is already typing when the fresh copy arrives
            if (!filled) {
                name.setText(user.fullName)
                username.setText(user.username)
                website.setText(user.website)
                bio.setText(user.bio)
                filled = true
            }
            showPhotos()
            findViewById<TextView>(R.id.value_email).text = user.email
            findViewById<TextView>(R.id.value_phone).text = user.phone.ifEmpty { "Not added" }
            findViewById<TextView>(R.id.value_gender).text = user.gender
        }
    }

    private fun save() {
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

        val saveButton = findViewById<View>(R.id.btn_save)
        saveButton.isEnabled = false
        val fields = mapOf(
            "name" to newName,
            "username" to newUsername,
            "website" to website.text.toString().trim(),
            "bio" to bio.text.toString().trim(),
            "setup" to if (setup) "1" else "0",
        )
        Users.update(fields) { user, error ->
            saveButton.isEnabled = true
            if (user == null) {
                // The server is the judge of unique usernames
                if (error == "That username is taken") username.error = error else toast(error ?: "Could not save")
                return@update
            }
            RecentLogin(this).save(user)
            if (setup) enterHome() else finish()
        }
    }

    companion object {
        const val EXTRA_SETUP = "setup"
        private val USERNAME_RULE = Regex("^[a-z0-9._]{3,30}$")
    }
}
