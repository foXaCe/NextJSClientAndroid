package com.nextjsclient.android.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import com.nextjsclient.android.R
import java.util.Locale

class LocaleManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "locale_prefs"
        private const val KEY_LANGUAGE = "language"
        private const val DEFAULT_LANGUAGE = "system"
        
        data class Language(
            val code: String,
            val displayName: String,
            val nativeName: String
        )
        
        fun getSupportedLanguages(context: Context): List<Language> {
            return listOf(
                Language("system", "System Default", context.getString(R.string.language_system)),
                Language("en", "English", context.getString(R.string.language_english)),
                Language("fr", "French", context.getString(R.string.language_french)),
                Language("es", "Spanish", context.getString(R.string.language_spanish))
            )
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun getCurrentLanguage(): String {
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }
    
    fun setLanguage(languageCode: String) {
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
        applyLanguage(languageCode)
    }
    
    fun applyLanguage(languageCode: String): Context {
        val locale = when (languageCode) {
            "system" -> Locale.getDefault()
            "en" -> Locale.ENGLISH
            "fr" -> Locale.FRENCH  
            "es" -> Locale("es", "ES")
            else -> Locale.getDefault()
        }
        
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }
    
    fun applyLanguageToContext(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            "system" -> Locale.getDefault()
            "en" -> Locale.ENGLISH
            "fr" -> Locale.FRENCH  
            "es" -> Locale("es", "ES")
            else -> Locale.getDefault()
        }
        
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            return context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            return context
        }
    }
    
    fun getCurrentLanguageDisplayName(): String {
        val currentCode = getCurrentLanguage()
        val supportedLanguages = getSupportedLanguages(context)
        return supportedLanguages.find { it.code == currentCode }?.nativeName ?: context.getString(R.string.language_system)
    }
}