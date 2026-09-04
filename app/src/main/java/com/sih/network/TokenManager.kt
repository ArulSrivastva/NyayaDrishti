package com.sih.network

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nyayadrishti_prefs", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun saveUser(id: Int, email: String, name: String, role: String) {
        prefs.edit()
            .putInt(KEY_USER_ID, id)
            .putString(KEY_USER_EMAIL, email)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }

    fun getUserName(): String {
        return prefs.getString(KEY_USER_NAME, "Officer") ?: "Officer"
    }

    fun getUserEmail(): String {
        return prefs.getString(KEY_USER_EMAIL, "officer@nyayadrishti.gov.in") ?: "officer@nyayadrishti.gov.in"
    }

    fun getUserId(): Int {
        return prefs.getInt(KEY_USER_ID, 1)
    }

    fun getUserRole(): String {
        return prefs.getString(KEY_USER_ROLE, "inspector") ?: "inspector"
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_ROLE = "user_role"
    }
}
