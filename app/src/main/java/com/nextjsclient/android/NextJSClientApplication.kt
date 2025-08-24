package com.nextjsclient.android

import android.app.Application
import android.content.Context
import com.nextjsclient.android.utils.LocaleManager

class NextJSClientApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
    }
    
    override fun attachBaseContext(base: Context) {
        val localeManager = LocaleManager(base)
        val updatedContext = updateContextWithLocale(base, localeManager.getCurrentLanguage())
        super.attachBaseContext(updatedContext)
    }
    
    private fun updateContextWithLocale(context: Context, languageCode: String): Context {
        val localeManager = LocaleManager(context)
        localeManager.applyLanguage(languageCode)
        return context
    }
}