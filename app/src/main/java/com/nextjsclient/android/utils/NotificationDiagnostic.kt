package com.nextjsclient.android.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging

class NotificationDiagnostic(private val context: Context) {
    
    companion object {
        private const val TAG = "NotificationDiagnostic"
        private const val CHANNEL_ID = "import_notifications"
    }
    
    private val notificationPreferences = NotificationPreferences(context)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    fun runFullDiagnostic(callback: ((DiagnosticResult) -> Unit)? = null): DiagnosticResult {
        Log.d(TAG, "=== DIAGNOSTIC COMPLET DES NOTIFICATIONS ===")
        
        val result = DiagnosticResult()
        
        // 1. Vérifier les permissions
        result.hasNotificationPermission = checkNotificationPermission()
        
        // 2. Vérifier le channel
        result.hasNotificationChannel = checkNotificationChannel()
        
        // 3. Vérifier les préférences
        result.notificationsEnabled = checkNotificationPreferences()
        
        // 4. Vérifier le token FCM (asynchrone)
        checkFCMTokenAsync { tokenStatus ->
            result.fcmTokenStatus = tokenStatus
            
            // 5. Vérifier les topics
            checkTopicSubscriptions()
            
            // 6. Vérifier le background
            result.canRunInBackground = checkBackgroundPermissions()
            
            logDiagnosticSummary(result)
            callback?.invoke(result)
        }
        
        // Retourner un résultat provisoire
        result.fcmTokenStatus = "En cours..."
        return result
    }
    
    private fun checkNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            Log.d(TAG, "✓ Permission POST_NOTIFICATIONS (Android 13+): ${if (hasPermission) "ACCORDÉE" else "REFUSÉE"}")
            return hasPermission
        } else {
            Log.d(TAG, "✓ Permission POST_NOTIFICATIONS: NON REQUISE (Android < 13)")
            return true
        }
    }
    
    private fun checkNotificationChannel(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            val exists = channel != null
            
            if (exists) {
                Log.d(TAG, "✓ Canal de notification '$CHANNEL_ID': EXISTE")
                Log.d(TAG, "  - Nom: ${channel?.name}")
                Log.d(TAG, "  - Importance: ${channel?.importance}")
                Log.d(TAG, "  - Activé: ${channel?.importance != NotificationManager.IMPORTANCE_NONE}")
            } else {
                Log.e(TAG, "✗ Canal de notification '$CHANNEL_ID': N'EXISTE PAS")
            }
            return exists
        } else {
            Log.d(TAG, "✓ Canal de notification: NON REQUIS (Android < 8)")
            return true
        }
    }
    
    private fun checkNotificationPreferences(): Boolean {
        val enabled = notificationPreferences.areNotificationsEnabled()
        val successEnabled = notificationPreferences.areImportSuccessNotificationsEnabled()
        val errorEnabled = notificationPreferences.areImportErrorNotificationsEnabled()
        val token = notificationPreferences.getFcmToken()
        
        Log.d(TAG, "✓ Préférences de notification:")
        Log.d(TAG, "  - Notifications générales: ${if (enabled) "ACTIVÉES" else "DÉSACTIVÉES"}")
        Log.d(TAG, "  - Notifications de succès: ${if (successEnabled) "ACTIVÉES" else "DÉSACTIVÉES"}")
        Log.d(TAG, "  - Notifications d'erreur: ${if (errorEnabled) "ACTIVÉES" else "DÉSACTIVÉES"}")
        Log.d(TAG, "  - Token FCM sauvé: ${if (token.isNotEmpty()) "OUI (${token.take(20)}...)" else "NON"}")
        
        return enabled
    }
    
    private fun checkFCMTokenAsync(callback: (String) -> Unit) {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val tokenStatus = if (!task.isSuccessful) {
                val error = task.exception?.message ?: "Erreur inconnue"
                Log.e(TAG, "✗ Token FCM: ERREUR - $error")
                "Erreur: $error"
            } else {
                val token = task.result
                Log.d(TAG, "✓ Token FCM actuel: ${token.take(20)}...${token.takeLast(10)}")
                Log.d(TAG, "  - Token complet: $token")
                
                // Vérifier si le token sauvé correspond
                val savedToken = notificationPreferences.getFcmToken()
                if (savedToken == token) {
                    Log.d(TAG, "✓ Token sauvé correspond au token actuel")
                } else {
                    Log.w(TAG, "⚠ Token sauvé différent du token actuel")
                    Log.d(TAG, "  - Sauvé: ${savedToken.take(20)}...")
                    Log.d(TAG, "  - Actuel: ${token.take(20)}...")
                }
                "Valide"
            }
            
            callback(tokenStatus)
        }
    }
    
    private fun checkTopicSubscriptions() {
        Log.d(TAG, "✓ Vérification des topics FCM:")
        
        FirebaseMessaging.getInstance().subscribeToTopic("import-success")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "  - Topic 'import-success': ABONNÉ")
                } else {
                    Log.e(TAG, "  - Topic 'import-success': ERREUR - ${task.exception?.message}")
                }
            }
        
        FirebaseMessaging.getInstance().subscribeToTopic("import-error")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "  - Topic 'import-error': ABONNÉ")
                } else {
                    Log.e(TAG, "  - Topic 'import-error': ERREUR - ${task.exception?.message}")
                }
            }
    }
    
    private fun checkBackgroundPermissions(): Boolean {
        // Vérifier si l'app peut tourner en arrière-plan
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val canShowNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
        
        Log.d(TAG, "✓ Fonctionnement en arrière-plan:")
        Log.d(TAG, "  - Notifications système activées: ${if (canShowNotifications) "OUI" else "NON"}")
        
        return canShowNotifications
    }
    
    private fun logDiagnosticSummary(result: DiagnosticResult) {
        Log.d(TAG, "")
        Log.d(TAG, "=== RÉSUMÉ DU DIAGNOSTIC ===")
        Log.d(TAG, "Permission notifications: ${if (result.hasNotificationPermission) "✓ OK" else "✗ MANQUANTE"}")
        Log.d(TAG, "Canal de notification: ${if (result.hasNotificationChannel) "✓ OK" else "✗ MANQUANT"}")
        Log.d(TAG, "Notifications activées: ${if (result.notificationsEnabled) "✓ OUI" else "✗ NON"}")
        Log.d(TAG, "Token FCM: ${result.fcmTokenStatus}")
        Log.d(TAG, "Arrière-plan: ${if (result.canRunInBackground) "✓ OK" else "✗ LIMITÉ"}")
        
        if (result.isFullyFunctional()) {
            Log.d(TAG, "🟢 STATUT GLOBAL: TOUT FONCTIONNE")
        } else {
            Log.e(TAG, "🔴 STATUT GLOBAL: PROBLÈMES DÉTECTÉS")
        }
        Log.d(TAG, "===============================")
    }
    
    fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Import Notifications"
            val descriptionText = "Notifications des imports de prix et Excel"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "✓ Canal de notification créé/vérifié")
        }
    }
}

data class DiagnosticResult(
    var hasNotificationPermission: Boolean = false,
    var hasNotificationChannel: Boolean = false,
    var notificationsEnabled: Boolean = false,
    var fcmTokenStatus: String = "Non vérifié",
    var canRunInBackground: Boolean = false
) {
    fun isFullyFunctional(): Boolean {
        return hasNotificationPermission && 
               hasNotificationChannel && 
               notificationsEnabled && 
               canRunInBackground &&
               (fcmTokenStatus == "Valide" || fcmTokenStatus.contains("En cours"))
    }
}