package com.nextjsclient.android.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "biometric_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val biometricManager = BiometricManager.from(context)
    
    /**
     * Vérifie si la biométrie est disponible sur l'appareil
     */
    fun isBiometricAvailable(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }
    
    /**
     * Vérifie si l'utilisateur a configuré la biométrie
     */
    fun isBiometricConfigured(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false
            else -> false
        }
    }
    
    /**
     * Retourne le type de biométrie disponible
     */
    fun getBiometricType(): String {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Déterminer les types disponibles
                val availableTypes = mutableListOf<String>()
                
                if (context.packageManager.hasSystemFeature("android.hardware.fingerprint")) {
                    availableTypes.add("Empreinte digitale")
                }
                if (context.packageManager.hasSystemFeature("android.hardware.biometrics.face")) {
                    availableTypes.add("Reconnaissance faciale")
                }
                if (context.packageManager.hasSystemFeature("android.hardware.biometrics.iris")) {
                    availableTypes.add("Reconnaissance de l'iris")
                }
                
                when {
                    availableTypes.isEmpty() -> "Authentification biométrique"
                    availableTypes.size == 1 -> availableTypes.first()
                    else -> availableTypes.joinToString(" et ")
                }
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Non disponible"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Temporairement indisponible"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Non configuré"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Mise à jour requise"
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "Non supporté"
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "État inconnu"
            else -> "Non disponible"
        }
    }
    
    /**
     * Retourne un message d'état pour l'utilisateur
     */
    fun getBiometricStatusMessage(): String {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Disponible et configurée"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Votre appareil ne supporte pas la biométrie"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Capteur biométrique temporairement indisponible"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Aucune empreinte configurée dans les paramètres"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Mise à jour de sécurité requise"
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "Biométrie non supportée par votre version Android"
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "État de la biométrie inconnu"
            else -> "Biométrie non disponible"
        }
    }
    
    /**
     * Vérifie si l'utilisateur a activé l'authentification biométrique dans l'app
     */
    fun isBiometricEnabledInApp(): Boolean {
        return sharedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) && isBiometricConfigured()
    }
    
    /**
     * Active/désactive l'authentification biométrique dans l'app
     */
    fun setBiometricEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }
    
    /**
     * Démarre l'authentification biométrique
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Authentification biométrique",
        subtitle: String = "Utilisez votre empreinte digitale ou reconnaissance faciale",
        negativeButtonText: String = "Annuler",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        if (!isBiometricConfigured()) {
            onError("Biométrie non configurée")
            return
        }
        
        val executor = ContextCompat.getMainExecutor(context)
        
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onCancel()
                    else -> onError(errString.toString())
                }
            }
            
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Authentification échouée")
            }
        })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
}