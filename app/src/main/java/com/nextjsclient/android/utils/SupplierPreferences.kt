package com.nextjsclient.android.utils

import android.content.Context
import android.content.SharedPreferences

class SupplierPreferences(context: Context) {
    
    companion object {
        private const val PREF_NAME = "supplier_preferences"
        private const val KEY_ANECOOP_ENABLED = "anecoop_enabled"
        private const val KEY_SOLAGORA_ENABLED = "solagora_enabled"
        
        // Default values
        private const val DEFAULT_ANECOOP = true
        private const val DEFAULT_SOLAGORA = true
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    // Anecoop preferences
    var isAnecoopEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANECOOP_ENABLED, DEFAULT_ANECOOP)
        set(value) = prefs.edit().putBoolean(KEY_ANECOOP_ENABLED, value).apply()
    
    // Solagora preferences
    var isSolagoraEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOLAGORA_ENABLED, DEFAULT_SOLAGORA)
        set(value) = prefs.edit().putBoolean(KEY_SOLAGORA_ENABLED, value).apply()
    
    // Helper methods
    fun getEnabledSuppliers(): List<String> {
        val enabled = mutableListOf<String>()
        if (isAnecoopEnabled) enabled.add("anecoop")
        if (isSolagoraEnabled) enabled.add("solagora")
        return enabled
    }
    
    fun isSupplierEnabled(supplier: String): Boolean {
        return when (supplier.lowercase()) {
            "anecoop" -> isAnecoopEnabled
            "solagora" -> isSolagoraEnabled
            else -> false
        }
    }
    
    fun setSupplierEnabled(supplier: String, enabled: Boolean) {
        when (supplier.lowercase()) {
            "anecoop" -> isAnecoopEnabled = enabled
            "solagora" -> isSolagoraEnabled = enabled
        }
    }
    
    // Ensure at least one supplier is always enabled
    fun validateSettings(): Boolean {
        if (!isAnecoopEnabled && !isSolagoraEnabled) {
            // Force Anecoop enabled if both are disabled
            isAnecoopEnabled = true
            return false // Settings were invalid and corrected
        }
        return true // Settings are valid
    }
    
    fun resetToDefaults() {
        prefs.edit()
            .putBoolean(KEY_ANECOOP_ENABLED, DEFAULT_ANECOOP)
            .putBoolean(KEY_SOLAGORA_ENABLED, DEFAULT_SOLAGORA)
            .apply()
    }
}