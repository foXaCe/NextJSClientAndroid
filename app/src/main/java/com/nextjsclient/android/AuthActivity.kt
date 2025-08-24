package com.nextjsclient.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.nextjsclient.android.databinding.ActivityAuthBinding
import com.nextjsclient.android.data.repository.FirebaseRepository
import com.nextjsclient.android.utils.ThemeManager
import com.nextjsclient.android.utils.LocaleManager
import kotlinx.coroutines.launch
import android.content.Context

class AuthActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityAuthBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var themeManager: ThemeManager
    private val repository = FirebaseRepository()
    
    override fun attachBaseContext(newBase: Context) {
        val localeManager = LocaleManager(newBase)
        val updatedContext = localeManager.applyLanguageToContext(newBase, localeManager.getCurrentLanguage())
        super.attachBaseContext(updatedContext)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize theme before calling super.onCreate()
        themeManager = ThemeManager(this)
        themeManager.initializeTheme()
        
        super.onCreate(savedInstanceState)
        
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        auth = FirebaseAuth.getInstance()
        
        // Check if already logged in
        if (auth.currentUser != null) {
            navigateToMain()
            return
        }
        
        setupViews()
    }
    
    private fun setupViews() {
        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()
            
            if (validateInput(email, password)) {
                performLogin(email, password)
            }
        }
    }
    
    private fun validateInput(email: String, password: String): Boolean {
        // Clear previous errors
        binding.emailInputLayout.error = null
        binding.passwordInputLayout.error = null
        
        if (email.isEmpty()) {
            binding.emailInputLayout.error = "L'email est requis"
            return false
        }
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailInputLayout.error = "Email invalide"
            return false
        }
        
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Le mot de passe est requis"
            return false
        }
        
        return true
    }
    
    private fun performLogin(email: String, password: String) {
        showLoading(true)
        
        lifecycleScope.launch {
            repository.signIn(email, password)
                .onSuccess {
                    navigateToMain()
                }
                .onFailure { exception ->
                    showError(getErrorMessage(exception))
                    showLoading(false)
                }
        }
    }
    
    private fun getErrorMessage(exception: Throwable): String {
        return when ((exception as? FirebaseAuthException)?.errorCode) {
            "ERROR_USER_NOT_FOUND" -> "Utilisateur non trouvé"
            "ERROR_WRONG_PASSWORD" -> "Mot de passe incorrect"
            "ERROR_INVALID_EMAIL" -> "Email invalide"
            "ERROR_TOO_MANY_REQUESTS" -> "Trop de tentatives. Réessayez plus tard."
            "ERROR_USER_DISABLED" -> "Ce compte a été désactivé"
            "ERROR_INVALID_CREDENTIAL" -> "Identifiants invalides"
            else -> "Erreur de connexion. Vérifiez vos identifiants."
        }
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !show
        binding.emailInput.isEnabled = !show
        binding.passwordInput.isEnabled = !show
        
        // Hide error card when loading
        if (show) {
            binding.errorCard.visibility = View.GONE
        }
    }
    
    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorCard.visibility = View.VISIBLE
    }
    
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}