package com.nextjsclient.android.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.nextjsclient.android.R

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
    
    fun getThemeDisplayName(theme: String, context: Context): String {
        return when (theme) {
            THEME_LIGHT -> context.getString(R.string.theme_light)
            THEME_DARK -> context.getString(R.string.theme_dark)
            THEME_SYSTEM -> context.getString(R.string.theme_system)
            else -> context.getString(R.string.theme_system)
        }
    }
    
    fun getAllThemes(context: Context): List<Pair<String, String>> {
        return listOf(
            THEME_SYSTEM to getThemeDisplayName(THEME_SYSTEM, context),
            THEME_LIGHT to getThemeDisplayName(THEME_LIGHT, context),
            THEME_DARK to getThemeDisplayName(THEME_DARK, context)
        )
    }
}