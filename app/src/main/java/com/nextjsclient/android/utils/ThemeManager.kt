package com.nextjsclient.android.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class ThemeManager(private val context: Context) {
    
    companion object {
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"
        private const val THEME_PREF_KEY = "app_theme"
    }
    
    private val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    fun setTheme(theme: String) {
        prefs.edit().putString(THEME_PREF_KEY, theme).apply()
        applyTheme(theme)
    }
    
    fun getCurrentTheme(): String {
        return prefs.getString(THEME_PREF_KEY, THEME_DARK) ?: THEME_DARK
    }
    
    fun applyTheme(theme: String) {
        when (theme) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
    
    fun initializeTheme() {
        applyTheme(getCurrentTheme())
    }
    
    fun getThemeDisplayName(theme: String): String {
        return when (theme) {
            THEME_LIGHT -> "Clair"
            THEME_DARK -> "Sombre"
            THEME_SYSTEM -> "Système"
            else -> "Système"
        }
    }
    
    fun getAllThemes(): List<Pair<String, String>> {
        return listOf(
            THEME_SYSTEM to getThemeDisplayName(THEME_SYSTEM),
            THEME_LIGHT to getThemeDisplayName(THEME_LIGHT),
            THEME_DARK to getThemeDisplayName(THEME_DARK)
        )
    }
}