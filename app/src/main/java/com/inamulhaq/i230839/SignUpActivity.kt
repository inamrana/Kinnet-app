package com.inamulhaq.i230839

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.database.ServerValue
import java.text.DateFormatSymbols
import java.util.Calendar

/**
 * 03 · Sign up — creates a Firebase account and saves the user to users/{uid}.
 * The password strength meter updates as the user types.
 * New users then go to Edit profile (setup) to choose a username and bio.
 */
class SignUpActivity : BaseActivity() {

    private lateinit var firstName: EditText
    private lateinit var lastName: EditText
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var createButton: Button

    // Birthday shown in the Day / Month / Year boxes (defaults match the design)
    private val birthday = Calendar.getInstance().apply { set(2004, Calendar.MARCH, 14) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        firstName = findViewById(R.id.input_first_name)
        lastName = findViewById(R.id.input_last_name)
        email = findViewById(R.id.input_email)
        password = findViewById(R.id.input_password)
        createButton = findViewById(R.id.btn_create)

        tapCloses(R.id.btn_back, R.id.link_login)
        passwordToggle(R.id.btn_toggle_password, R.id.input_password)
        password.doAfterTextChanged { showStrength(PasswordStrength.of(it?.toString().orEmpty())) }
        showStrength(PasswordStrength.of(""))
        listOf(R.id.birth_day, R.id.birth_month, R.id.birth_year).forEach { id ->
            (findViewById<View>(id).parent.parent as View).setOnClickListener { pickBirthday() }
        }
        showBirthday()
        onTap(R.id.btn_create) { createAccount() }
    }

    private fun showStrength(strength: PasswordStrength) {
        val bars = listOf(R.id.strength_1, R.id.strength_2, R.id.strength_3)
        val filled = if (strength == PasswordStrength.STRONG) R.drawable.bg_teal_rect_r3 else R.drawable.bg_progress_red
        bars.forEachIndexed { index, id ->
            findViewById<View>(id).setBackgroundResource(
                if (index < strength.bars) filled else R.drawable.bg_progress_grey
            )
        }
        findViewById<TextView>(R.id.strength_label).apply {
            text = strength.label
            setTextColor(ContextCompat.getColor(context, strength.color))
        }
    }

    private fun pickBirthday() {
        DatePickerDialog(
            this,
            { _, year, month, day -> birthday.set(year, month, day); showBirthday() },
            birthday.get(Calendar.YEAR), birthday.get(Calendar.MONTH), birthday.get(Calendar.DAY_OF_MONTH)
        ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
    }

    private fun showBirthday() {
        findViewById<TextView>(R.id.birth_day).text = birthday.get(Calendar.DAY_OF_MONTH).toString()
        findViewById<TextView>(R.id.birth_month).text =
            DateFormatSymbols.getInstance().shortMonths[birthday.get(Calendar.MONTH)]
        findViewById<TextView>(R.id.birth_year).text = birthday.get(Calendar.YEAR).toString()
    }

    private fun selectedGender(): String = when (findViewById<RadioGroup>(R.id.gender_group).checkedRadioButtonId) {
        R.id.gender_female -> "Female"
        R.id.gender_male -> "Male"
        else -> "Custom"
    }

    private fun createAccount() {
        val first = firstName.text.toString().trim()
        val last = lastName.text.toString().trim()
        val mail = email.text.toString().trim()
        val pass = password.text.toString()
        when {
            first.isEmpty() -> { firstName.error = "Required"; return }
            last.isEmpty() -> { lastName.error = "Required"; return }
            !Patterns.EMAIL_ADDRESS.matcher(mail).matches() -> { email.error = "Enter a valid email"; return }
            pass.length < 6 -> { password.error = "At least 6 characters"; return }
            !findViewById<CheckBox>(R.id.check_terms).isChecked -> { toast("Please accept the Terms"); return }
        }

        createButton.isEnabled = false
        Db.auth.createUserWithEmailAndPassword(mail, pass)
            .addOnSuccessListener { result -> saveUser(result.user!!.uid, first, last, mail) }
            .addOnFailureListener {
                createButton.isEnabled = true
                toast(it.localizedMessage ?: "Sign up failed")
            }
    }

    private fun saveUser(uid: String, first: String, last: String, mail: String) {
        val fullName = "$first $last"
        val user = User(
            uid = uid,
            firstName = first,
            lastName = last,
            fullName = fullName,
            nameLower = fullName.lowercase(),
            email = mail,
            gender = selectedGender(),
            birthday = "%04d-%02d-%02d".format(
                birthday.get(Calendar.YEAR), birthday.get(Calendar.MONTH) + 1, birthday.get(Calendar.DAY_OF_MONTH)
            ),
        )
        Db.user(uid).setValue(user)
            .addOnSuccessListener {
                Db.user(uid).child("createdAt").setValue(ServerValue.TIMESTAMP)
                RecentLogin(this).save(user)
                Presence.goOnline()
                enterProfileSetup()
            }
            .addOnFailureListener {
                createButton.isEnabled = true
                toast(it.localizedMessage ?: "Could not save your profile")
            }
    }
}

/** Simple strength rules: length, mixed case, digits and symbols. */
enum class PasswordStrength(val bars: Int, val label: String, val color: Int) {
    WEAK(1, "Weak", R.color.badge_red),
    MEDIUM(2, "Medium", R.color.badge_red),
    STRONG(3, "Strong", R.color.teal);

    companion object {
        fun of(password: String): PasswordStrength {
            if (password.length < 6) return WEAK
            var score = 0
            if (password.length >= 8) score++
            if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
            if (password.any { it.isDigit() }) score++
            if (password.any { !it.isLetterOrDigit() }) score++
            return when {
                score >= 3 -> STRONG
                score >= 2 -> MEDIUM
                else -> WEAK
            }
        }
    }
}
