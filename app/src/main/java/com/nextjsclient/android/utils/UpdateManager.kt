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
import java.io.File
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONArray
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
                    
                    // For nightly builds, we want to check the commit hash to see if it's newer
                    val actualLatestVersion = if (latestVersion == "nightly") {
                        // Extract commit hash from release name like "nightly-20250819-dc10f7d"
                        val commitPattern = Regex("nightly-\\d+-([a-f0-9]+)")
                        val commitMatch = commitPattern.find(name)
                        val latestCommit = commitMatch?.groupValues?.get(1) ?: ""
                        
                        Log.d(TAG, "Found commit hash in release: $latestCommit")
                        
                        // For nightly builds, always consider as potentially newer
                        // We'll use the commit hash as version identifier
                        "nightly-$latestCommit"
                    } else {
                        latestVersion
                    }
                    
                    Log.d(TAG, "Current version: $currentVersion")
                    Log.d(TAG, "Latest version: $latestVersion")
                    Log.d(TAG, "Actual latest version: $actualLatestVersion")
                    
                    val isNewer = isNewerVersion(currentVersion, actualLatestVersion)
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
    
    private fun cleanOldUpdates() {
        try {
            val appUpdateDir = File(context.getExternalFilesDir(null), "updates")
            if (appUpdateDir.exists() && appUpdateDir.isDirectory) {
                val oldFiles = appUpdateDir.listFiles { file ->
                    file.name.startsWith("NextJSClient-") && file.name.endsWith(".apk")
                }
                
                oldFiles?.forEach { file ->
                    Log.d(TAG, "🗑️ Deleting old update: ${file.name}")
                    file.delete()
                }
                
                if (oldFiles?.isNotEmpty() == true) {
                    Log.d(TAG, "✅ Cleaned ${oldFiles.size} old update file(s)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old updates", e)
        }
    }
    
    fun downloadUpdate(release: Release) {
        try {
            // Nettoyer les anciennes mises à jour avant de télécharger
            cleanOldUpdates()
            
            val fileName = "NextJSClient-${release.tagName}.apk"
            
            // Utiliser le répertoire externe privé de l'app pour les mises à jour
            val appUpdateDir = File(context.getExternalFilesDir(null), "updates")
            if (!appUpdateDir.exists()) {
                appUpdateDir.mkdirs()
                Log.d(TAG, "📁 Created updates directory: ${appUpdateDir.absolutePath}")
            }
            
            Log.d(TAG, "📂 Update storage location: ${appUpdateDir.absolutePath}")
            Log.d(TAG, "🗺 Storage info: This is in app's private external storage (Android/data/${context.packageName}/files/updates)")
            
            val destinationFile = File(appUpdateDir, fileName)
            Log.d(TAG, "📂 Destination file: ${destinationFile.absolutePath}")
            
            val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
                .setTitle("NextJS Client Update")
                .setDescription("Téléchargement de la mise à jour ${release.tagName}")
                .setDestinationInExternalFilesDir(context, "updates", fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "📥 Download started with ID: $downloadId")
            Log.d(TAG, "📂 File will be saved as: $fileName in app directory")
            
            listener?.onDownloadStarted()
            
            // Start monitoring download progress avec le bon chemin
            startDownloadMonitoring(destinationFile.absolutePath)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading update", e)
            listener?.onError("Erreur de téléchargement: ${e.message}")
        }
    }
    
    private fun startDownloadMonitoring(filePath: String) {
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
                            
                            // Chercher le fichier téléchargé dans le répertoire updates
                            val appUpdateDir = File(context.getExternalFilesDir(null), "updates")
                            Log.d(TAG, "🔍 Searching for downloaded file in: ${appUpdateDir.absolutePath}")
                            
                            val downloadedFiles = appUpdateDir.listFiles { _, name ->
                                name.endsWith(".apk")
                            }
                            
                            val downloadFile = when {
                                downloadedFiles?.isNotEmpty() == true -> {
                                    Log.d(TAG, "📦 Found ${downloadedFiles.size} APK file(s):")
                                    downloadedFiles.forEach { file ->
                                        Log.d(TAG, "   • ${file.name} (${file.length()} bytes)")
                                    }
                                    downloadedFiles.first() // Prendre le premier fichier APK trouvé
                                }
                                else -> {
                                    Log.w(TAG, "⚠️ No APK files found, trying original path...")
                                    File(filePath)
                                }
                            }
                            
                            Log.d(TAG, "📂 Final file to check: ${downloadFile.absolutePath}")
                            Log.d(TAG, "📊 File exists: ${downloadFile.exists()}")
                            
                            if (downloadFile.exists()) {
                                Log.d(TAG, "🎉 File found! Size: ${downloadFile.length()} bytes")
                                
                                // Nettoyer la notification après avoir confirmé le fichier
                                try {
                                    downloadManager.remove(downloadId)
                                    Log.d(TAG, "🧹 Notification cleared")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Could not clear notification: ${e.message}")
                                }
                                
                                withContext(Dispatchers.Main) {
                                    listener?.onDownloadCompleted(downloadFile)
                                }
                            } else {
                                Log.e(TAG, "❌ Downloaded file not found!")
                                Log.d(TAG, "🗂️ Directory contents:")
                                appUpdateDir.listFiles()?.forEach { file ->
                                    Log.d(TAG, "   • ${file.name} (${file.length()} bytes)")
                                }
                                
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
            
            // Note: Si l'installation échoue avec une erreur de signature,
            // l'utilisateur devra désinstaller manuellement l'ancienne version
            // car les APK GitHub et locaux ont des signatures différentes
            Log.i(TAG, "📦 Lancement de l'installation...")
            Log.i(TAG, "⚠️ Si l'installation échoue, désinstallez d'abord l'app actuelle")
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing update", e)
            listener?.onError("Erreur d'installation: ${e.message}")
        }
    }
    
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0"
            
            // Try to get commit hash from BuildConfig if available
            val commitHash = try {
                com.nextjsclient.android.BuildConfig.COMMIT_HASH
            } catch (e: Exception) {
                ""
            }
            
            Log.d(TAG, "Current app version: $versionName")
            Log.d(TAG, "Current commit hash: $commitHash")
            
            // If we have a valid commit hash, return version with commit format
            if (commitHash.isNotEmpty() && commitHash != "unknown") {
                val formattedVersion = "nightly-$commitHash"
                Log.d(TAG, "Using commit-based version: $formattedVersion")
                formattedVersion
            } else {
                versionName
            }
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
            
            // If latest is a special tag (like nightly), check if it's actually different
            if (specialTags.any { latest.lowercase().contains(it) }) {
                Log.d(TAG, "🌙 Latest version contains special tag: $latest")
                
                return when {
                    // If current version is also a special tag, compare commit hashes
                    specialTags.any { current.lowercase().contains(it) } -> {
                        Log.d(TAG, "🔄 Both versions are special tags - comparing commits")
                        // Extract commit hashes and compare
                        val currentCommit = current.substringAfterLast("-", "")
                        val latestCommit = latest.substringAfterLast("-", "")
                        val isNewer = currentCommit != latestCommit && latestCommit.isNotEmpty()
                        Log.d(TAG, "🔍 Commit comparison: current=$currentCommit vs latest=$latestCommit, newer=$isNewer")
                        isNewer
                    }
                    // If current version is numbered, only offer nightly if commit is different from what we expect
                    current.matches(Regex("^[0-9]+(\\.[0-9]+)*$")) -> {
                        Log.d(TAG, "📅 Current version is numbered: $current, checking if nightly is actually newer")
                        // For numbered versions, we assume nightly is newer unless we can determine otherwise
                        val latestCommit = latest.substringAfterLast("-", "")
                        // Only offer update if there's a valid commit hash in the nightly build
                        val hasValidCommit = latestCommit.isNotEmpty() && latestCommit.matches(Regex("[a-f0-9]+"))
                        Log.d(TAG, "🔍 Nightly has valid commit: $hasValidCommit (commit: $latestCommit)")
                        hasValidCommit
                    }
                    else -> {
                        Log.d(TAG, "🤷 Unknown current version format - being conservative")
                        false
                    }
                }
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