package com.nextjsclient.android.utils

import android.content.Context
import android.content.SharedPreferences

class NotificationPreferences(context: Context) {
    
    companion object {
        private const val PREF_NAME = "notification_preferences"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_IMPORT_SUCCESS_ENABLED = "import_success_enabled"
        private const val KEY_IMPORT_ERROR_ENABLED = "import_error_enabled"
        private const val KEY_FCM_TOKEN = "fcm_token"
        
        // Valeurs par défaut
        private const val DEFAULT_NOTIFICATIONS_ENABLED = false
        private const val DEFAULT_IMPORT_SUCCESS_ENABLED = true
        private const val DEFAULT_IMPORT_ERROR_ENABLED = true
    }
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    // Notifications générales
    fun areNotificationsEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, DEFAULT_NOTIFICATIONS_ENABLED)
    }
    
    fun setNotificationsEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }
    
    // Notifications de succès d'import
    fun areImportSuccessNotificationsEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_IMPORT_SUCCESS_ENABLED, DEFAULT_IMPORT_SUCCESS_ENABLED)
    }
    
    fun setImportSuccessNotificationsEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_IMPORT_SUCCESS_ENABLED, enabled).apply()
    }
    
    // Notifications d'erreur d'import
    fun areImportErrorNotificationsEnabled(): Boolean {
        return sharedPrefs.getBoolean(KEY_IMPORT_ERROR_ENABLED, DEFAULT_IMPORT_ERROR_ENABLED)
    }
    
    fun setImportErrorNotificationsEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_IMPORT_ERROR_ENABLED, enabled).apply()
    }
    
    // Token FCM
    fun getFcmToken(): String {
        return sharedPrefs.getString(KEY_FCM_TOKEN, "") ?: ""
    }
    
    fun saveFcmToken(token: String) {
        sharedPrefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }
    
    // Méthode utilitaire pour vérifier si au moins un type de notification est activé
    fun hasAnyNotificationEnabled(): Boolean {
        return areNotificationsEnabled() && 
               (areImportSuccessNotificationsEnabled() || areImportErrorNotificationsEnabled())
    }
}