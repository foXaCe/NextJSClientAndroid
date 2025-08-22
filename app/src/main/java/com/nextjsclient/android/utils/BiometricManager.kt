package com.nextjsclient.android.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricManager(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "biometric_prefs"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val TAG = "BiometricManager"
    }
    
    private val sharedPrefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val biometricManager = BiometricManager.from(context)
    
    /**
     * Vérifie si la biométrie est disponible sur l'appareil
     */
    fun isBiometricAvailable(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }
    
    /**
     * Vérifie si l'utilisateur a configuré la biométrie
     */
    fun isBiometricConfigured(): Boolean {
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false
            else -> false
        }
    }
    
    /**
     * Retourne le type de biométrie disponible
     */
    fun getBiometricType(): String {
        Log.d(TAG, "=== BIOMETRIC TYPE DETECTION ===")
        
        val strongResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val weakResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        val combinedResult = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
        
        Log.d(TAG, "BIOMETRIC_STRONG result: $strongResult")
        Log.d(TAG, "BIOMETRIC_WEAK result: $weakResult")
        Log.d(TAG, "Combined result: $combinedResult")
        
        return when (combinedResult) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Déterminer les types disponibles
                val availableTypes = mutableListOf<String>()
                
                val hasFingerprint = context.packageManager.hasSystemFeature("android.hardware.fingerprint")
                val hasFace = context.packageManager.hasSystemFeature("android.hardware.biometrics.face")
                val hasIris = context.packageManager.hasSystemFeature("android.hardware.biometrics.iris")
                
                Log.d(TAG, "Hardware features:")
                Log.d(TAG, "  - android.hardware.fingerprint: $hasFingerprint")
                Log.d(TAG, "  - android.hardware.biometrics.face: $hasFace")
                Log.d(TAG, "  - android.hardware.biometrics.iris: $hasIris")
                
                if (hasFingerprint) {
                    availableTypes.add("Empreinte digitale")
                }
                if (hasFace) {
                    availableTypes.add("Reconnaissance faciale")
                }
                if (hasIris) {
                    availableTypes.add("Reconnaissance de l'iris")
                }
                
                Log.d(TAG, "Available types: $availableTypes")
                
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
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Disponible et configurée"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Votre appareil ne supporte pas la biométrie"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Capteur biométrique temporairement indisponible"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Aucune biométrie configurée dans les paramètres"
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
     * Démarre l'authentification biométrique avec priorité à la reconnaissance faciale
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Authentification biométrique",
        subtitle: String = "Utilisez la reconnaissance faciale ou votre empreinte digitale",
        negativeButtonText: String = "Annuler",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {},
        useFaceFirst: Boolean = true
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
        
        // Déterminer le niveau d'authentification à utiliser
        Log.d(TAG, "=== AUTHENTICATION SETUP ===")
        val strongAvailable = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        val weakAvailable = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        
        // Vérifier les types disponibles
        val hasFingerprint = context.packageManager.hasSystemFeature("android.hardware.fingerprint")
        val hasFace = context.packageManager.hasSystemFeature("android.hardware.biometrics.face")
        
        Log.d(TAG, "BIOMETRIC_STRONG available: $strongAvailable")
        Log.d(TAG, "BIOMETRIC_WEAK available: $weakAvailable")
        Log.d(TAG, "Has Face Recognition: $hasFace")
        Log.d(TAG, "Has Fingerprint: $hasFingerprint")
        
        // Déterminer le subtitle en fonction du matériel disponible et de la priorité
        val promptSubtitle = when {
            hasFace && hasFingerprint && useFaceFirst -> "Préférence : Reconnaissance faciale (empreinte disponible en alternative)"
            hasFace && hasFingerprint -> "Reconnaissance faciale ou empreinte digitale"
            hasFace -> "Utilisez la reconnaissance faciale"
            hasFingerprint -> "Utilisez votre empreinte digitale"
            else -> subtitle
        }
        
        // Utiliser BIOMETRIC_WEAK pour permettre face ID sur plus d'appareils
        // BIOMETRIC_STRONG est plus restrictif et peut ne pas inclure la reconnaissance faciale sur certains appareils
        val authenticators = when {
            useFaceFirst && hasFace -> {
                // Priorité à la reconnaissance faciale avec WEAK pour meilleure compatibilité
                Log.d(TAG, "Using BIOMETRIC_WEAK for Face priority")
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            }
            strongAvailable -> {
                Log.d(TAG, "Using BIOMETRIC_STRONG")
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            }
            weakAvailable -> {
                Log.d(TAG, "Using BIOMETRIC_WEAK")
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            }
            else -> {
                Log.d(TAG, "Using combined BIOMETRIC_STRONG | BIOMETRIC_WEAK")
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            }
        }

        // Configuration pour afficher toutes les options biométriques
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(promptSubtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(authenticators)
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    /**
     * Authentification avec fallback automatique de face vers fingerprint
     */
    fun authenticateWithFallback(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        val hasFingerprint = context.packageManager.hasSystemFeature("android.hardware.fingerprint")
        val hasFace = context.packageManager.hasSystemFeature("android.hardware.biometrics.face")
        
        Log.d(TAG, "=== AUTHENTICATION WITH FALLBACK ===")
        Log.d(TAG, "Has Face: $hasFace, Has Fingerprint: $hasFingerprint")
        
        if (hasFace) {
            // Essayer d'abord avec la reconnaissance faciale
            authenticate(
                activity = activity,
                title = "Authentification requise",
                subtitle = "Regardez l'écran pour vous authentifier",
                negativeButtonText = if (hasFingerprint) "Utiliser l'empreinte" else "Annuler",
                onSuccess = onSuccess,
                onError = { faceError ->
                    Log.d(TAG, "Face authentication failed: $faceError")
                    if (hasFingerprint && faceError.contains("Annuler", ignoreCase = true).not()) {
                        // Si échec face et fingerprint disponible, essayer fingerprint
                        Log.d(TAG, "Falling back to fingerprint authentication")
                        authenticate(
                            activity = activity,
                            title = "Authentification par empreinte",
                            subtitle = "Placez votre doigt sur le capteur",
                            negativeButtonText = "Annuler",
                            onSuccess = onSuccess,
                            onError = onError,
                            onCancel = onCancel,
                            useFaceFirst = false
                        )
                    } else {
                        onError(faceError)
                    }
                },
                onCancel = {
                    if (hasFingerprint) {
                        // Si l'utilisateur clique sur "Utiliser l'empreinte"
                        Log.d(TAG, "User chose to use fingerprint instead")
                        authenticate(
                            activity = activity,
                            title = "Authentification par empreinte",
                            subtitle = "Placez votre doigt sur le capteur",
                            negativeButtonText = "Annuler",
                            onSuccess = onSuccess,
                            onError = onError,
                            onCancel = onCancel,
                            useFaceFirst = false
                        )
                    } else {
                        onCancel()
                    }
                },
                useFaceFirst = true
            )
        } else if (hasFingerprint) {
            // Si pas de face, utiliser directement fingerprint
            authenticate(
                activity = activity,
                title = "Authentification par empreinte",
                subtitle = "Placez votre doigt sur le capteur",
                negativeButtonText = "Annuler",
                onSuccess = onSuccess,
                onError = onError,
                onCancel = onCancel,
                useFaceFirst = false
            )
        } else {
            // Fallback générique
            authenticate(
                activity = activity,
                onSuccess = onSuccess,
                onError = onError,
                onCancel = onCancel
            )
        }
    }
}