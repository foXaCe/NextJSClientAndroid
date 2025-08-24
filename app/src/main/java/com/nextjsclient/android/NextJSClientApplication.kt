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
        val updatedContext = localeManager.applyLanguageToContext(base, localeManager.getCurrentLanguage())
        super.attachBaseContext(updatedContext)
    }
}