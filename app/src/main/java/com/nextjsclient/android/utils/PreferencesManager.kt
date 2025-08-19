package com.nextjsclient.android.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "NextJSClientPrefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_SERVER_URL = "server_url"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun saveAuthToken(token: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }
    
    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }
    
    fun clearAuthToken() {
        prefs.edit().remove(KEY_AUTH_TOKEN).apply()
    }
    
    fun saveUserInfo(userId: String, email: String, name: String) {
        prefs.edit().apply {
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, name)
            apply()
        }
    }
    
    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }
    
    fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }
    
    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }
    
    fun clearUserInfo() {
        prefs.edit().apply {
            remove(KEY_USER_ID)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_NAME)
            apply()
        }
    }
    
    fun saveServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }
    
    fun getServerUrl(): String {
        return prefs.getString(KEY_SERVER_URL, "http://localhost:3000") ?: "http://localhost:3000"
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}