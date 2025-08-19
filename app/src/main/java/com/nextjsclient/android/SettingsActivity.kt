package com.nextjsclient.android

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.nextjsclient.android.databinding.ActivitySettingsBinding
import com.nextjsclient.android.utils.ThemeManager

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var themeManager: ThemeManager
    private lateinit var auth: FirebaseAuth
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        themeManager = ThemeManager(this)
        auth = FirebaseAuth.getInstance()
        
        setupToolbar()
        setupViews()
        updateUI()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Paramètres"
    }
    
    private fun setupViews() {
        // Theme selector
        binding.themeSelector.setOnClickListener {
            showThemeDialog()
        }
        
        // User info
        val user = auth.currentUser
        if (user != null) {
            binding.userEmail.text = user.email ?: "Email non disponible"
            binding.userId.text = "ID: ${user.uid}"
        }
        
        // Logout
        binding.logoutButton.setOnClickListener {
            showLogoutDialog()
        }
        
        // App info
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.appVersion.text = "Version ${packageInfo.versionName}"
        } catch (e: Exception) {
            binding.appVersion.text = "Version inconnue"
        }
        
        // Version info
        binding.androidVersion.text = "Android API ${android.os.Build.VERSION.SDK_INT}"
        binding.firebaseVersion.text = "Firebase BOM 33.6.0"
        binding.materialVersion.text = "Material Design 3 (1.12.0)"
    }
    
    private fun updateUI() {
        val currentTheme = themeManager.getCurrentTheme()
        binding.currentTheme.text = themeManager.getThemeDisplayName(currentTheme)
    }
    
    private fun showThemeDialog() {
        val themes = themeManager.getAllThemes()
        val themeNames = themes.map { it.second }.toTypedArray()
        val currentTheme = themeManager.getCurrentTheme()
        val currentIndex = themes.indexOfFirst { it.first == currentTheme }
        
        AlertDialog.Builder(this)
            .setTitle("Choisir le thème")
            .setSingleChoiceItems(themeNames, currentIndex) { dialog, which ->
                val selectedTheme = themes[which].first
                themeManager.setTheme(selectedTheme)
                updateUI()
                dialog.dismiss()
                
                // Redémarrer l'activité pour appliquer le thème
                recreate()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Déconnexion")
            .setMessage("Êtes-vous sûr de vouloir vous déconnecter ?")
            .setPositiveButton("Déconnexion") { _, _ ->
                logout()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private fun logout() {
        auth.signOut()
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}