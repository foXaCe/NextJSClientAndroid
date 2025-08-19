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
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
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
        private const val GITHUB_API_URL = "https://api.github.com/repos/foXaCe/NextJSClientAndroid/releases"
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
                Log.d(TAG, "🔍 Starting update check...")
                Log.d(TAG, "🏛️ Repository: foXaCe/NextJSClientAndroid")
                withContext(Dispatchers.Main) {
                    listener?.onUpdateChecking()
                }
                
                Log.d(TAG, "Requesting: $GITHUB_API_URL")
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                // Ajouter token GitHub pour repository privé si nécessaire
                // connection.setRequestProperty("Authorization", "token YOUR_GITHUB_TOKEN")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                Log.d(TAG, "Response code: ${connection.responseCode}")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Response received: ${response.take(200)}...")
                    val releasesArray = JSONArray(response)
                    
                    if (releasesArray.length() == 0) {
                        withContext(Dispatchers.Main) {
                            listener?.onUpToDate()
                        }
                        return@withContext
                    }
                    
                    // Prendre la première release (la plus récente)
                    val latestRelease = releasesArray.getJSONObject(0)
                    
                    val tagName = latestRelease.getString("tag_name")
                    val name = latestRelease.getString("name")
                    val body = latestRelease.getString("body")
                    val publishedAt = latestRelease.getString("published_at")
                    
                    Log.d(TAG, "Found latest release: $tagName - $name")
                    Log.d(TAG, "Published: $publishedAt")
                    
                    // Find APK download URL in assets
                    val assets = latestRelease.getJSONArray("assets")
                    Log.d(TAG, "Found ${assets.length()} assets")
                    var downloadUrl = ""
                    
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.getString("name")
                        Log.d(TAG, "Asset $i: $assetName")
                        // Prioriser app-debug.apk pour les tests
                        if (assetName == "app-debug.apk" || assetName.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            Log.d(TAG, "Found APK: $downloadUrl")
                            if (assetName == "app-debug.apk") break // Prioriser app-debug.apk
                        }
                    }
                    
                    if (downloadUrl.isEmpty()) {
                        Log.w(TAG, "No APK found in assets")
                        withContext(Dispatchers.Main) {
                            listener?.onError("Aucun APK trouvé dans la release")
                        }
                        return@withContext
                    }
                    
                    val currentVersion = getCurrentVersion()
                    val latestVersion = tagName.removePrefix("v")
                    
                    Log.d(TAG, "Current version: $currentVersion")
                    Log.d(TAG, "Latest version: $latestVersion")
                    
                    val isNewer = isNewerVersion(currentVersion, latestVersion)
                    Log.d(TAG, "Is newer version available: $isNewer")
                    
                    withContext(Dispatchers.Main) {
                        if (isNewer) {
                            val release = Release(tagName, name, body, downloadUrl, publishedAt)
                            Log.d(TAG, "✅ Update available: ${release.tagName}")
                            Log.d(TAG, "📦 Download URL: ${release.downloadUrl}")
                            listener?.onUpdateAvailable(release)
                        } else {
                            Log.d(TAG, "✅ App is up to date - no update needed")
                            listener?.onUpToDate()
                        }
                    }
                } else if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    Log.w(TAG, "Repository or releases not found (404)")
                    withContext(Dispatchers.Main) {
                        listener?.onError("Repository non trouvé (404)")
                    }
                } else {
                    Log.e(TAG, "HTTP Error: ${connection.responseCode} - ${connection.responseMessage}")
                    withContext(Dispatchers.Main) {
                        listener?.onError("Erreur de connexion: ${connection.responseCode}")
                    }
                }
                
                connection.disconnect()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates: ${e.message}", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
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
            Log.d(TAG, "📥 Download started with ID: $downloadId")
            Log.d(TAG, "📂 File will be saved as: $fileName")
            
            listener?.onDownloadStarted()
            
            // Start monitoring download progress
            startDownloadMonitoring(fileName)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            listener?.onError("Erreur de téléchargement: ${e.message}")
        }
    }
    
    private fun startDownloadMonitoring(fileName: String) {
        // Use a coroutine to monitor download progress periodically
        CoroutineScope(Dispatchers.IO).launch {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            
            while (true) {
                delay(1000) // Check every second
                
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)
                    
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    
                    val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                    val bytesTotal = cursor.getLong(bytesTotalIndex)
                    
                    Log.d(TAG, "📊 Download status: $status, Progress: $bytesDownloaded/$bytesTotal bytes")
                    
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            Log.d(TAG, "✅ Download completed successfully!")
                            cursor.close()
                            
                            // Check if file exists and notify completion
                            val downloadFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                            Log.d(TAG, "   • Looking for file: ${downloadFile.absolutePath}")
                            Log.d(TAG, "   • File exists: ${downloadFile.exists()}")
                            
                            if (downloadFile.exists()) {
                                Log.d(TAG, "🎉 File found! Calling onDownloadCompleted")
                                withContext(Dispatchers.Main) {
                                    listener?.onDownloadCompleted(downloadFile)
                                }
                            } else {
                                Log.e(TAG, "❌ Downloaded file not found!")
                                withContext(Dispatchers.Main) {
                                    listener?.onError("Fichier téléchargé non trouvé")
                                }
                            }
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            Log.e(TAG, "❌ Download failed!")
                            cursor.close()
                            withContext(Dispatchers.Main) {
                                listener?.onError("Échec du téléchargement")
                            }
                            break
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            if (bytesTotal > 0) {
                                val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                                withContext(Dispatchers.Main) {
                                    listener?.onDownloadProgress(progress)
                                }
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ Download not found in cursor")
                    cursor.close()
                    withContext(Dispatchers.Main) {
                        listener?.onError("Téléchargement introuvable")
                    }
                    break
                }
                cursor.close()
            }
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
            val version = packageInfo.versionName ?: "1.0"
            Log.d(TAG, "Current app version: $version")
            version
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current version", e)
            "1.0"
        }
    }
    
    private fun isNewerVersion(current: String, latest: String): Boolean {
        return try {
            Log.d(TAG, "🔍 Comparing versions: current='$current' vs latest='$latest'")
            
            // Handle special tags like "nightly", "beta", "alpha", etc.
            val specialTags = listOf("nightly", "beta", "alpha", "dev", "test", "pre-release")
            if (specialTags.any { latest.lowercase().contains(it) }) {
                Log.d(TAG, "🌙 Latest version contains special tag - treating as newer than any numbered version")
                return true
            }
            
            // If current version contains special tags, treat numbered releases as newer
            if (specialTags.any { current.lowercase().contains(it) }) {
                // Check if latest is a numbered version
                if (latest.matches(Regex("^[0-9]+(\\.[0-9]+)*$"))) {
                    Log.d(TAG, "📊 Current has special tag, latest is numbered - latest is newer")
                    return true
                }
            }
            
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            
            Log.d(TAG, "📊 Current parts: $currentParts")
            Log.d(TAG, "📊 Latest parts: $latestParts")
            
            val maxLength = maxOf(currentParts.size, latestParts.size)
            
            for (i in 0 until maxLength) {
                val currentPart = currentParts.getOrElse(i) { 0 }
                val latestPart = latestParts.getOrElse(i) { 0 }
                
                Log.d(TAG, "🔢 Comparing part $i: $currentPart vs $latestPart")
                
                when {
                    latestPart > currentPart -> {
                        Log.d(TAG, "✅ Latest version is newer")
                        return true
                    }
                    latestPart < currentPart -> {
                        Log.d(TAG, "📱 Current version is newer")
                        return false
                    }
                }
            }
            
            Log.d(TAG, "🟰 Versions are equal")
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error comparing versions", e)
            false
        }
    }
}