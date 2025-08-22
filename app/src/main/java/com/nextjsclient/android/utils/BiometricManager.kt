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
                Log.e(TAG, "onAuthenticationError - Code: $errorCode, Message: $errString")
                Log.e(TAG, "Error code details: ${getErrorCodeString(errorCode)}")
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                        Log.d(TAG, "User cancelled or pressed negative button")
                        onCancel()
                    }
                    else -> {
                        Log.e(TAG, "Authentication error passed to caller")
                        onError(errString.toString())
                    }
                }
            }
            
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "onAuthenticationSucceeded - Authentication successful!")
                Log.d(TAG, "  Crypto object: ${result.cryptoObject}")
                
                val authTypeString = when (result.authenticationType) {
                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC -> "BIOMETRIC (Fingerprint or Face)"
                    BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL -> "DEVICE_CREDENTIAL (PIN/Pattern/Password)"
                    else -> "UNKNOWN (${result.authenticationType})"
                }
                Log.d(TAG, "  Authentication type: $authTypeString")
                
                // Type 2 = AUTHENTICATION_RESULT_TYPE_BIOMETRIC
                // Malheureusement, Android ne distingue pas entre face et fingerprint dans le résultat
                if (result.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
                    Log.d(TAG, "  Note: Android ne distingue pas entre face/fingerprint dans le résultat")
                }
                
                onSuccess()
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.e(TAG, "onAuthenticationFailed - Single attempt failed (user can retry)")
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
        
        // Pour forcer la priorité face, utiliser BIOMETRIC_STRONG qui privilégie les méthodes plus sécurisées
        // Sur la plupart des appareils modernes, la reconnaissance faciale est considérée comme STRONG
        val authenticators = when {
            useFaceFirst && hasFace && strongAvailable -> {
                // Utiliser STRONG pour privilégier la reconnaissance faciale
                Log.d(TAG, "Using BIOMETRIC_STRONG for Face priority")
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            }
            useFaceFirst && hasFace -> {
                // Si STRONG pas disponible, utiliser la combinaison qui permet les deux
                Log.d(TAG, "Using combined BIOMETRIC_STRONG | BIOMETRIC_WEAK for Face priority")
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
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
        
        Log.d(TAG, "=== STARTING BIOMETRIC PROMPT ===")
        Log.d(TAG, "  Title: $title")
        Log.d(TAG, "  Subtitle: $promptSubtitle")
        Log.d(TAG, "  Negative button: $negativeButtonText")
        Log.d(TAG, "  Authenticators: $authenticators")
        Log.d(TAG, "  UseFaceFirst: $useFaceFirst")
        
        try {
            biometricPrompt.authenticate(promptInfo)
            Log.d(TAG, "BiometricPrompt.authenticate() called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling authenticate: ${e.message}", e)
            onError("Erreur lors de l'authentification: ${e.message}")
        }
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
        val hasIris = context.packageManager.hasSystemFeature("android.hardware.biometrics.iris")
        
        Log.d(TAG, "=== AUTHENTICATION WITH FALLBACK ===")
        Log.d(TAG, "Device capabilities:")
        Log.d(TAG, "  - Face Recognition: $hasFace")
        Log.d(TAG, "  - Fingerprint: $hasFingerprint")
        Log.d(TAG, "  - Iris: $hasIris")
        
        // Vérifier si la biométrie est configurée
        val isConfigured = isBiometricConfigured()
        Log.d(TAG, "Biometric configured: $isConfigured")
        
        if (!isConfigured) {
            Log.e(TAG, "No biometric configured on device")
            onError("Aucune biométrie configurée sur l'appareil")
            return
        }
        
        if (hasFace) {
            // NOUVELLE APPROCHE: Essayer de forcer UNIQUEMENT la reconnaissance faciale
            Log.d(TAG, "Attempting FACE-ONLY authentication first")
            authenticateFaceOnly(
                activity = activity,
                onSuccess = onSuccess,
                onFaceError = { faceError ->
                    Log.e(TAG, "Face-only authentication FAILED: $faceError")
                    if (hasFingerprint) {
                        // Fallback vers fingerprint
                        Log.d(TAG, "Face failed, offering FINGERPRINT fallback")
                        authenticate(
                            activity = activity,
                            title = "Authentification digitale",
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
                onFaceCancel = {
                    if (hasFingerprint) {
                        // L'utilisateur veut utiliser l'empreinte
                        Log.d(TAG, "User cancelled face, offering fingerprint")
                        authenticate(
                            activity = activity,
                            title = "Authentification digitale",
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
                }
            )
        } else if (hasFingerprint) {
            // Si pas de face, utiliser directement fingerprint
            Log.d(TAG, "No face recognition available, using FINGERPRINT directly")
            authenticate(
                activity = activity,
                title = "Authentification digitale",
                subtitle = "Placez votre doigt sur le capteur",
                negativeButtonText = "Annuler",
                onSuccess = onSuccess,
                onError = onError,
                onCancel = onCancel,
                useFaceFirst = false
            )
        } else {
            // Fallback générique
            Log.d(TAG, "Using GENERIC biometric authentication")
            authenticate(
                activity = activity,
                onSuccess = onSuccess,
                onError = onError,
                onCancel = onCancel
            )
        }
    }
    
    /**
     * Tente d'authentifier UNIQUEMENT avec la reconnaissance faciale
     */
    private fun authenticateFaceOnly(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFaceError: (String) -> Unit,
        onFaceCancel: () -> Unit
    ) {
        Log.d(TAG, "=== FACE-ONLY AUTHENTICATION ===")
        
        val executor = ContextCompat.getMainExecutor(context)
        
        val biometricPrompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.e(TAG, "Face-only onAuthenticationError - Code: $errorCode, Message: $errString")
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                        Log.d(TAG, "Face-only cancelled by user - offering fingerprint automatically")
                        onFaceCancel()
                    }
                    else -> {
                        Log.e(TAG, "Face-only error: $errString")
                        onFaceError(errString.toString())
                    }
                }
            }
            
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "Face-only authentication SUCCESS!")
                onSuccess()
            }
            
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.e(TAG, "Face-only authentication failed (retry possible)")
                // Ne pas appeler onFaceError ici, laisser l'utilisateur réessayer
            }
        })
        
        // Essayer de forcer uniquement la reconnaissance faciale avec déverrouillage automatique
        // Utiliser BIOMETRIC_STRONG pour permettre l'authentification automatique sans validation
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authentification requise")
            .setNegativeButtonText("Annuler")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(false) // IMPORTANT: Pas de validation manuelle
            .build()
        
        Log.d(TAG, "Starting face-only prompt with BIOMETRIC_STRONG and confirmationRequired=false")
        
        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in face-only authenticate: ${e.message}", e)
            onFaceError("Erreur lors de l'authentification faciale: ${e.message}")
        }
    }
    
    private fun getStatusString(status: Int): String {
        return when (status) {
            BiometricManager.BIOMETRIC_SUCCESS -> "SUCCESS - Available"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "ERROR - No hardware"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "ERROR - Hardware unavailable"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "ERROR - None enrolled"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "ERROR - Security update required"
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "ERROR - Unsupported"
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "STATUS - Unknown"
            else -> "Unknown status: $status"
        }
    }
    
    private fun getErrorCodeString(errorCode: Int): String {
        return when (errorCode) {
            BiometricPrompt.ERROR_CANCELED -> "ERROR_CANCELED - Operation canceled"
            BiometricPrompt.ERROR_HW_NOT_PRESENT -> "ERROR_HW_NOT_PRESENT - No biometric hardware"
            BiometricPrompt.ERROR_HW_UNAVAILABLE -> "ERROR_HW_UNAVAILABLE - Hardware unavailable"
            BiometricPrompt.ERROR_LOCKOUT -> "ERROR_LOCKOUT - Too many attempts"
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> "ERROR_LOCKOUT_PERMANENT - Permanently locked"
            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> "ERROR_NEGATIVE_BUTTON - Negative button pressed"
            BiometricPrompt.ERROR_NO_BIOMETRICS -> "ERROR_NO_BIOMETRICS - No biometrics enrolled"
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> "ERROR_NO_DEVICE_CREDENTIAL - No device credential"
            BiometricPrompt.ERROR_NO_SPACE -> "ERROR_NO_SPACE - Not enough storage"
            BiometricPrompt.ERROR_TIMEOUT -> "ERROR_TIMEOUT - Operation timed out"
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> "ERROR_UNABLE_TO_PROCESS - Unable to process"
            BiometricPrompt.ERROR_USER_CANCELED -> "ERROR_USER_CANCELED - User canceled"
            BiometricPrompt.ERROR_VENDOR -> "ERROR_VENDOR - Vendor error"
            else -> "Unknown error code: $errorCode"
        }
    }
}