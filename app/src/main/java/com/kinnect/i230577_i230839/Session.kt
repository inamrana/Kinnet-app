package com.kinnect.i230577_i230839

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The signed-in account on this device. The session token is encrypted with an AES-256-GCM key
 * that lives in the Android Keystore (the same approach EncryptedSharedPreferences uses), so the
 * token is never stored in plain text. The cached profile itself lives in SQLite ([LocalDb]).
 */
object Session {

    private const val KEY_ALIAS = "kinnect_session_key"
    private lateinit var prefs: SharedPreferences
    private var cachedToken: String? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("kinnect_session", Context.MODE_PRIVATE)
        cachedToken = prefs.getString("token", null)?.let { decrypt(it) }
    }

    val token: String? get() = cachedToken
    val uid: String? get() = if (cachedToken != null) prefs.getString("uid", null) else null
    val isLoggedIn: Boolean get() = uid != null

    /** The signed-in user's profile from the local cache (works offline). */
    val user: User? get() = uid?.let { LocalDb.user(it) }

    fun save(token: String, user: User) {
        prefs.edit().putString("token", encrypt(token)).putString("uid", user.uid).apply()
        cachedToken = token
        LocalDb.putUser(user)
    }

    fun clear() {
        val pendingLogout = prefs.getString("pending_logout", null)
        prefs.edit().clear().apply()
        if (pendingLogout != null) prefs.edit().putString("pending_logout", pendingLogout).apply()
        cachedToken = null
    }

    /** A token we logged out of while offline; [Sync] invalidates it on the server once back online. */
    var pendingLogoutToken: String?
        get() = prefs.getString("pending_logout", null)?.let { decrypt(it) }
        set(value) {
            if (value == null) prefs.edit().remove("pending_logout").apply()
            else prefs.edit().putString("pending_logout", encrypt(value)).apply()
        }

    // ---- Android Keystore AES-GCM --------------------------------------------------------

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()
}
