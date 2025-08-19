package com.nextjsclient.android.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(
    val tagName: String,
    val name: String,
    val body: String,
    val downloadUrl: String,
    val publishedAt: String
)

class UpdateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateManager"
        private const val GITHUB_API_URL = "https://api.github.com/repos/foXaCe/NextJSClientAndroid/releases/latest"
    }
    
    interface UpdateListener {
        fun onUpdateChecking()
        fun onUpdateAvailable(release: Release)
        fun onUpToDate()
        fun onDownloadStarted()
        fun onDownloadProgress(progress: Int)
        fun onDownloadCompleted(file: File)
        fun onError(message: String)
    }
    
    private var listener: UpdateListener? = null
    private var downloadId: Long = -1
    
    fun setUpdateListener(listener: UpdateListener) {
        this.listener = listener
    }
    
    suspend fun checkForUpdates() {
        withContext(Dispatchers.IO) {
            try {
                listener?.onUpdateChecking()
                
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    
                    val tagName = json.getString("tag_name")
                    val name = json.getString("name")
                    val body = json.getString("body")
                    val publishedAt = json.getString("published_at")
                    
                    // Find APK download URL in assets
                    val assets = json.getJSONArray("assets")
                    var downloadUrl = ""
                    
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.getString("name")
                        if (assetName.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                    
                    if (downloadUrl.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            listener?.onError("Aucun APK trouvé dans la release")
                        }
                        return@withContext
                    }
                    
                    val currentVersion = getCurrentVersion()
                    val latestVersion = tagName.removePrefix("v")
                    
                    withContext(Dispatchers.Main) {
                        if (isNewerVersion(currentVersion, latestVersion)) {
                            val release = Release(tagName, name, body, downloadUrl, publishedAt)
                            listener?.onUpdateAvailable(release)
                        } else {
                            listener?.onUpToDate()
                        }
                    }
                } else if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    withContext(Dispatchers.Main) {
                        listener?.onUpToDate() // Pas de releases disponibles
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        listener?.onError("Erreur de connexion: ${connection.responseCode}")
                    }
                }
                
                connection.disconnect()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                withContext(Dispatchers.Main) {
                    listener?.onUpToDate() // Fallback silencieux en cas d'erreur
                }
            }
        }
    }
    
    fun downloadUpdate(release: Release) {
        try {
            val fileName = "NextJSClient-${release.tagName}.apk"
            val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
                .setTitle("NextJS Client Update")
                .setDescription("Téléchargement de la mise à jour ${release.tagName}")
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            
            listener?.onDownloadStarted()
            
            // Register broadcast receiver for download completion
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        val downloadFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                        if (downloadFile.exists()) {
                            listener?.onDownloadCompleted(downloadFile)
                        } else {
                            listener?.onError("Fichier téléchargé non trouvé")
                        }
                        context?.unregisterReceiver(this)
                    }
                }
            }
            
            context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            listener?.onError("Erreur de téléchargement: ${e.message}")
        }
    }
    
    fun installUpdate(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing update", e)
            listener?.onError("Erreur d'installation: ${e.message}")
        }
    }
    
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    
    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxLength = maxOf(currentParts.size, latestParts.size)
            
            for (i in 0 until maxLength) {
                val currentPart = currentParts.getOrElse(i) { 0 }
                val latestPart = latestParts.getOrElse(i) { 0 }
                
                when {
                    latestPart > currentPart -> return true
                    latestPart < currentPart -> return false
                }
            }
            
            false
        } catch (e: Exception) {
            false
        }
    }
}