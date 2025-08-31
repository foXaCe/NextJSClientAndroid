package com.nextjsclient.android

import android.content.Context
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.nextjsclient.android.databinding.ActivityRuptureHistoryBinding
import com.nextjsclient.android.ui.ruptures.RupturesDetailFragment
import com.nextjsclient.android.utils.LocaleManager

class RuptureHistoryActivity : AppCompatActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        try {
            val localeManager = LocaleManager(newBase)
            val languageCode = localeManager.getCurrentLanguage()
            
            val locale = when (languageCode) {
                "system" -> java.util.Locale.getDefault()
                "en" -> java.util.Locale.ENGLISH
                "fr" -> java.util.Locale.FRENCH
                "es" -> java.util.Locale("es", "ES")
                else -> java.util.Locale.getDefault()
            }
            
            val config = android.content.res.Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            val updatedContext = newBase.createConfigurationContext(config)
            super.attachBaseContext(updatedContext)
        } catch (e: Exception) {
            super.attachBaseContext(newBase)
        }
    }
    
    private lateinit var binding: ActivityRuptureHistoryBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityRuptureHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Récupérer les paramètres
        val codeProduit = intent.getStringExtra("codeProduit") ?: ""
        val supplier = intent.getStringExtra("supplier") ?: ""
        val productName = intent.getStringExtra("productName") ?: ""
        
        // Ajouter le fragment
        if (savedInstanceState == null) {
            val fragment = RupturesDetailFragment.newInstance(codeProduit, supplier, productName)
            
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, fragment)
                .commit()
        }
        
        // Gérer le bouton retour avec l'approche moderne
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }
}