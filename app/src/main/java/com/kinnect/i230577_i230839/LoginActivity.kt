package com.kinnect.i230577_i230839

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * 02 · Log in — email and password checked by the Kinnect web service (auth/login.php),
 * which returns a session token that is stored encrypted on the device.
 * The Recent login card shows the last account used on this device; tapping it pre-fills the email.
 */
class LoginActivity : BaseActivity() {

    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var loginButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        email = findViewById(R.id.input_email)
        password = findViewById(R.id.input_password)
        loginButton = findViewById(R.id.btn_login)

        showRecentLogin()
        passwordToggle(R.id.btn_toggle_password, R.id.input_password)
        onTap(R.id.btn_login) { logIn() }
        onTap(R.id.link_forgot) { sendPasswordReset() }
        tapOpens(SignUpActivity::class.java, R.id.btn_create_account)
    }

    private fun showRecentLogin() {
        val recent = RecentLogin(this)
        val recentEmail = recent.email
        val visibility = if (recentEmail == null) View.GONE else View.VISIBLE
        findViewById<View>(R.id.recent_login_label).visibility = visibility
        findViewById<View>(R.id.recent_login_card).visibility = visibility
        findViewById<View>(R.id.recent_badge).visibility = View.GONE
        if (recentEmail == null) return

        findViewById<TextView>(R.id.recent_name).text = recent.name
        findViewById<TextView>(R.id.recent_avatar).text = recent.initials
        onTap(R.id.recent_login_card) {
            email.setText(recentEmail)
            password.requestFocus()
        }
    }

    private fun logIn() {
        val mail = email.text.toString().trim()
        val pass = password.text.toString()
        when {
            !Patterns.EMAIL_ADDRESS.matcher(mail).matches() -> { email.error = "Enter a valid email"; return }
            pass.isEmpty() -> { password.error = "Enter your password"; return }
        }

        loginButton.isEnabled = false
        Api.post("auth/login.php", mapOf("email" to mail, "password" to pass)) { result ->
            val json = result.json
            if (!result.ok || json == null) {
                loginButton.isEnabled = true
                toast(if (result.offline) "You're offline. Connect to log in." else result.message)
                return@post
            }
            signedIn(json.getString("token"), json.getJSONObject("user").toUser())
        }
    }

    /** Stores the session, remembers the recent login, then goes to setup or Home. */
    private fun signedIn(token: String, user: User) {
        Session.save(token, user)
        RecentLogin(this).save(user)
        (application as KinnectApp).onSignedIn()
        Push.saveToken()
        if (user.profileComplete) enterHome() else enterProfileSetup()
    }

    private fun sendPasswordReset() {
        val mail = email.text.toString().trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) {
            email.error = "Enter your email first"
            return
        }
        toast("Ask your Kinnect admin to reset the password for $mail")
    }
}
