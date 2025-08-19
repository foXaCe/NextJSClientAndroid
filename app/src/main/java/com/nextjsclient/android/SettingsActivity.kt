package com.nextjsclient.android

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        
        setupWindowInsets()
        setupToolbar()
        setupViews()
        updateUI()
        animateViews()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Paramètres"
    }
    
    private fun setupViews() {
        // Theme selector with ripple effect
        binding.themeSelector.setOnClickListener {
            animateClick(it)
            showThemeDialog()
        }
        
        // User info and avatar
        val user = auth.currentUser
        if (user != null) {
            binding.userEmail.text = user.email ?: "Email non disponible"
            binding.userId.text = "ID: ${user.uid.take(12)}..."
            
            // Set user initial in avatar
            val initial = user.email?.firstOrNull()?.uppercaseChar() ?: 'U'
            binding.userInitial.text = initial.toString()
        }
        
        // Notifications toggle
        binding.notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Save notification preference
            getSharedPreferences("settings", MODE_PRIVATE).edit()
                .putBoolean("notifications_enabled", isChecked)
                .apply()
            
            val message = if (isChecked) "Notifications activées" else "Notifications désactivées"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        
        // Load notification preference
        val notificationsEnabled = getSharedPreferences("settings", MODE_PRIVATE)
            .getBoolean("notifications_enabled", true)
        binding.notificationSwitch.isChecked = notificationsEnabled
        
        // Privacy option
        binding.privacyOption.setOnClickListener {
            animateClick(it)
            // TODO: Open privacy settings
            Toast.makeText(this, "Paramètres de confidentialité", Toast.LENGTH_SHORT).show()
        }
        
        // Logout button with confirmation
        binding.logoutButton.setOnClickListener {
            animateClick(it)
            showLogoutDialog()
        }
        
        // App info
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.appVersion.text = "Version ${packageInfo.versionName}"
            binding.buildInfo.text = "Release"
        } catch (e: Exception) {
            binding.appVersion.text = "Version 1.0.0"
            binding.buildInfo.text = "Release"
        }
        
        // Tech stack info
        binding.androidVersion.text = "API ${android.os.Build.VERSION.SDK_INT}"
        binding.firebaseVersion.text = "Firebase"
        binding.materialVersion.text = "Material 3"
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
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Choisir le thème")
            .setSingleChoiceItems(themeNames, currentIndex) { dialog, which ->
                val selectedTheme = themes[which].first
                themeManager.setTheme(selectedTheme)
                updateUI()
                dialog.dismiss()
                
                // Smooth transition before recreating
                binding.root.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        recreate()
                    }
                    .start()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Déconnexion")
            .setMessage("Êtes-vous sûr de vouloir vous déconnecter ?")
            .setPositiveButton("Se déconnecter") { _, _ ->
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
    
    private fun animateViews() {
        // Animate profile header
        binding.userEmail.alpha = 0f
        binding.userId.alpha = 0f
        binding.userInitial.scaleX = 0f
        binding.userInitial.scaleY = 0f
        
        // Avatar animation
        val avatarAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.userInitial, View.SCALE_X, 0f, 1.1f, 1f),
                ObjectAnimator.ofFloat(binding.userInitial, View.SCALE_Y, 0f, 1.1f, 1f)
            )
            duration = 500
            interpolator = DecelerateInterpolator()
        }
        
        // Text animations
        binding.userEmail.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(200)
            .start()
        
        binding.userId.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(300)
            .start()
        
        avatarAnimator.start()
        
        // Animate cards with stagger effect
        val cards = listOf(
            binding.themeSelector,
            binding.notificationsOption,
            binding.privacyOption,
            binding.logoutButton
        )
        
        cards.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay((100 * index).toLong())
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
    
    private fun animateClick(view: View) {
        val scaleDown = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f)
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f)
        val scaleUp = ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f)
        
        scaleDown.duration = 100
        scaleDownY.duration = 100
        scaleUp.duration = 100
        scaleUpY.duration = 100
        
        val scaleSet = AnimatorSet()
        scaleSet.play(scaleDown).with(scaleDownY)
        scaleSet.play(scaleUp).with(scaleUpY).after(scaleDown)
        scaleSet.start()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}