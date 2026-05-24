package com.corall.agrotrack.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class UserRole { OPERATOR, TECHNICIAN, ADMIN }

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy { buildPrefs() }

    init { _instance = this }

    private fun buildPrefs(): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "agrotrack_session",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSession(token: String, userId: Int, userName: String, role: UserRole) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, userName)
            .putString(KEY_ROLE, role.name)
            .apply()
        _token    = token
        _role     = role
        _userName = userName
    }

    fun getToken(): String?  = prefs.getString(KEY_TOKEN, null)
    fun getUserId(): Int     = prefs.getInt(KEY_USER_ID, -1)
    fun getUserName(): String = prefs.getString(KEY_USER_NAME, "") ?: ""
    fun getRole(): UserRole  = UserRole.valueOf(prefs.getString(KEY_ROLE, UserRole.OPERATOR.name) ?: UserRole.OPERATOR.name)
    fun isSessionActive(): Boolean = getToken() != null

    fun clearSession() {
        prefs.edit().clear().apply()
        _token    = null
        _role     = null
        _userName = ""
    }

    // ── PIN rápido (operadores en planta) ────────────────────────────────────

    fun savePin(pin: String) {
        prefs.edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return hashPin(pin) == stored
    }

    fun hasPin(): Boolean = prefs.contains(KEY_PIN_HASH)

    private fun hashPin(pin: String): String {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: run {
            val newSalt = java.util.UUID.randomUUID().toString()
            prefs.edit().putString(KEY_PIN_SALT, newSalt).apply()
            newSalt
        }
        val combined = "$pin:$salt"
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(combined.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_TOKEN     = "session_token"
        private const val KEY_USER_ID   = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_ROLE      = "user_role"
        private const val KEY_PIN_HASH  = "pin_hash"
        private const val KEY_PIN_SALT  = "pin_salt"

        private var _instance: SessionManager? = null

        // Estado en memoria (evita leer Prefs en cada comprobación de nav)
        private var _token:    String?   = null
        private var _role:     UserRole? = null
        private var _userName: String    = ""

        fun isSessionActive() = _token != null
        fun currentRole()     = _role ?: UserRole.OPERATOR
        fun getToken()        = _token
        fun getUserName()     = _userName

        fun clearSession() {
            _instance?.prefs?.edit()?.clear()?.apply()
            _token    = null
            _role     = null
            _userName = ""
        }
    }
}
