package com.nextjsclient.android.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nextjsclient.android.MainActivity
import com.nextjsclient.android.R
import com.nextjsclient.android.utils.NotificationPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class NotificationService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "NotificationService"
        private const val CHANNEL_ID = "import_notifications"
        private const val REGISTER_TOKEN_URL = "https://registerdevicetoken-ibvfjldoja-ew.a.run.app"
    }
    
    private val notificationPreferences by lazy { NotificationPreferences(this) }
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nouveau token FCM reçu: $token")
        
        // Sauvegarder le token localement
        notificationPreferences.saveFcmToken(token)
        
        // Enregistrer le token sur le serveur si les notifications sont activées
        if (notificationPreferences.areNotificationsEnabled()) {
            registerTokenOnServer(token)
        }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        // Vérifier si les notifications sont activées
        if (!notificationPreferences.areNotificationsEnabled()) {
            Log.d(TAG, "Notifications désactivées, message ignoré")
            return
        }
        
        Log.d(TAG, "Message reçu de: ${remoteMessage.from}")
        
        // Créer le channel de notification
        createNotificationChannel()
        
        // Extraire les données
        val title = remoteMessage.notification?.title ?: getString(R.string.notification_default_title)
        val body = remoteMessage.notification?.body ?: getString(R.string.notification_default_body)
        
        // Données personnalisées
        val provider = remoteMessage.data["provider"] ?: ""
        val type = remoteMessage.data["type"] ?: ""
        val status = remoteMessage.data["status"] ?: ""
        
        // Afficher la notification
        showNotification(title, body, provider, type, status)
        
        Log.d(TAG, "Notification affichée: $provider - $status")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showNotification(title: String, body: String, provider: String, type: String, status: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("provider", provider)
            putExtra("type", type)
            putExtra("status", status)
            putExtra("from_notification", true)
        }
        
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Icône et couleur selon le statut
        val (iconRes, colorRes) = when (status) {
            "success" -> Pair(R.drawable.ic_check_circle, R.color.green_500)
            "error" -> Pair(R.drawable.ic_error, R.color.red_500)
            else -> Pair(R.drawable.ic_info, R.color.blue_500)
        }
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(getColor(colorRes))
            .setVibrate(longArrayOf(0, 500, 200, 500))
        
        // Style étendu pour plus de détails
        if (provider.isNotEmpty() && type.isNotEmpty()) {
            val detailedText = "$provider • $type"
            builder.setSubText(detailedText)
        }
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
    
    private fun registerTokenOnServer(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceInfo = JsonObject().apply {
                    addProperty("model", Build.MODEL)
                    addProperty("manufacturer", Build.MANUFACTURER)
                    addProperty("androidVersion", Build.VERSION.RELEASE)
                    addProperty("appVersion", applicationContext.packageName)
                }
                
                val requestData = JsonObject().apply {
                    addProperty("token", token)
                    addProperty("userId", "android-${Build.MODEL}-${System.currentTimeMillis()}")
                    add("deviceInfo", deviceInfo)
                }
                
                val requestBody = gson.toJson(requestData)
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                
                val request = Request.Builder()
                    .url(REGISTER_TOKEN_URL)
                    .post(requestBody)
                    .build()
                
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Token enregistré avec succès sur le serveur")
                    } else {
                        Log.e(TAG, "Erreur lors de l'enregistrement du token: ${response.code}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de l'envoi du token au serveur", e)
            }
        }
    }
    
    fun subscribeToTopics() {
        // Les topics sont gérés côté serveur selon les préférences
        Log.d(TAG, "Abonnement aux topics géré côté serveur")
    }
}