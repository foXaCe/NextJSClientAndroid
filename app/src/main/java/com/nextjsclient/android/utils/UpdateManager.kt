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
    
    /**
     * Nettoie les anciennes APK du dossier Downloads
     * Supprime tous les fichiers NextJSClient*.apk
     */
    fun cleanOldApks() {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir.exists() && downloadsDir.isDirectory) {
                val apkFiles = downloadsDir.listFiles { file ->
                    file.name.startsWith("NextJSClient") && file.name.endsWith(".apk")
                }
                
                apkFiles?.forEach { file ->
                    try {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted old APK: ${file.name}")
                        } else {
                            Log.w(TAG, "Failed to delete: ${file.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting ${file.name}: ${e.message}")
                    }
                }
                
                val deletedCount = apkFiles?.size ?: 0
                if (deletedCount > 0) {
                    Log.i(TAG, "Cleaned $deletedCount old APK file(s) from Downloads")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old APKs: ${e.message}")
        }
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
                    
                    // Récupérer tous les noms d'assets
                    val assetNames = mutableListOf<String>()
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        assetNames.add(asset.getString("name"))
                    }
                    
                    val release = Release(tagName, name, body, downloadUrl, publishedAt)
                    val isNewer = isNewerVersion(currentVersion, latestVersion, release, assetNames)
                    Log.d(TAG, "Is newer version available: $isNewer")
                    
                    withContext(Dispatchers.Main) {
                        if (isNewer) {
                            Log.d(TAG, "✅ GitHub release available: ${release.tagName}")
                            Log.d(TAG, "📦 Download URL: ${release.downloadUrl}")
                            listener?.onUpdateAvailable(release)
                        } else {
                            Log.d(TAG, "❌ No GitHub release available")
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
    
    private fun cleanOldUpdates(keepFileName: String? = null) {
        try {
            // Utiliser le dossier cache externe de l'app (pas de permissions requises)
            val appUpdateDir = File(context.externalCacheDir, "updates")
            if (appUpdateDir.exists() && appUpdateDir.isDirectory) {
                // Supprimer les anciens APK NextJSClient et les fichiers temporaires d'installation
                val oldFiles = appUpdateDir.listFiles { file ->
                    (file.name.startsWith("NextJSClient-") || file.name.startsWith("install_")) && 
                    file.name.endsWith(".apk") &&
                    file.name != keepFileName
                }
                
                oldFiles?.forEach { file ->
                    Log.d(TAG, "🗑️ Deleting old NextJSClient APK: ${file.name}")
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(TAG, "   ✅ Successfully deleted: ${file.name}")
                    } else {
                        Log.w(TAG, "   ⚠️ Failed to delete: ${file.name}")
                    }
                }
                
                if (oldFiles?.isNotEmpty() == true) {
                    Log.d(TAG, "✅ Cleaned ${oldFiles.size} old NextJSClient APK file(s)")
                } else {
                    Log.d(TAG, "📋 No old NextJSClient APK files to clean")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old updates", e)
        }
    }
    
    fun downloadUpdate(release: Release) {
        try {
            Log.d(TAG, "🚀 === DÉBUT TÉLÉCHARGEMENT ===")
            Log.d(TAG, "📋 Release info:")
            Log.d(TAG, "   • Tag: ${release.tagName}")
            Log.d(TAG, "   • Name: ${release.name}")
            Log.d(TAG, "   • URL: ${release.downloadUrl}")
            
            val fileName = "NextJSClient-${release.tagName}.apk"
            Log.d(TAG, "📁 Target filename: $fileName")
            
            // Nettoyer les anciennes mises à jour avant de télécharger (garde le nouveau nom)
            cleanOldUpdates(keepFileName = fileName)
            
            // Utiliser le dossier cache externe de l'app (pas de permissions requises)
            val appUpdateDir = File(context.externalCacheDir, "updates")
            Log.d(TAG, "📂 Updates directory: ${appUpdateDir.absolutePath}")
            Log.d(TAG, "📊 Directory exists before: ${appUpdateDir.exists()}")
            Log.d(TAG, "📊 Directory writable: ${appUpdateDir.canWrite()}")
            
            if (!appUpdateDir.exists()) {
                val created = appUpdateDir.mkdirs()
                Log.d(TAG, "📁 Directory creation result: $created")
                Log.d(TAG, "📁 Created updates directory: ${appUpdateDir.absolutePath}")
            }
            
            // Vérifier si le fichier existe déjà et le supprimer
            val existingFile = File(appUpdateDir, fileName)
            if (existingFile.exists()) {
                Log.d(TAG, "⚠️ File already exists: ${existingFile.name}")
                val deleted = existingFile.delete()
                if (deleted) {
                    Log.d(TAG, "✅ Deleted existing file: ${existingFile.name}")
                } else {
                    Log.w(TAG, "❌ Failed to delete existing file: ${existingFile.name}")
                }
            }
            
            Log.d(TAG, "📊 Directory exists after: ${appUpdateDir.exists()}")
            Log.d(TAG, "📊 Directory permissions: ${appUpdateDir.canRead()}/${appUpdateDir.canWrite()}")
            
            // Lister le contenu du répertoire avant téléchargement
            Log.d(TAG, "📋 Directory contents BEFORE download:")
            val nextjsFiles = appUpdateDir.listFiles { file ->
                file.name.startsWith("NextJSClient-") && file.name.endsWith(".apk")
            }
            nextjsFiles?.forEach { file ->
                Log.d(TAG, "   • ${file.name} (${file.length()} bytes)")
            } ?: Log.d(TAG, "   • No NextJSClient APK files found")
            
            Log.d(TAG, "🗺 Storage info: App external cache directory (no permissions required)")
            
            val destinationFile = File(appUpdateDir, fileName)
            Log.d(TAG, "📂 Expected destination file: ${destinationFile.absolutePath}")
            
            // Utiliser setDestinationUri pour spécifier le chemin exact dans le cache externe
            val destinationUri = Uri.fromFile(File(appUpdateDir, fileName))
            
            val request = DownloadManager.Request(Uri.parse(release.downloadUrl))
                .setTitle("NextJS Client Update")
                .setDescription("Téléchargement de la mise à jour ${release.tagName}")
                .setDestinationUri(destinationUri)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            Log.d(TAG, "⚙️ DownloadManager request configured")
            Log.d(TAG, "   • Title: NextJS Client Update")
            Log.d(TAG, "   • Description: Téléchargement de la mise à jour ${release.tagName}")
            Log.d(TAG, "   • Destination: ${destinationUri.path}")
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "📥 Download enqueued with ID: $downloadId")
            Log.d(TAG, "📂 File will be saved as: $fileName in app directory")
            
            listener?.onDownloadStarted()
            
            // Start monitoring download progress avec le bon chemin
            Log.d(TAG, "🔍 Starting download monitoring for path: ${destinationFile.absolutePath}")
            startDownloadMonitoring(destinationFile.absolutePath)
            
            Log.d(TAG, "✅ === FIN CONFIGURATION TÉLÉCHARGEMENT ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error downloading update", e)
            Log.e(TAG, "❌ Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "❌ Exception message: ${e.message}")
            Log.e(TAG, "❌ Stack trace: ${e.stackTraceToString()}")
            listener?.onError("Erreur de téléchargement: ${e.message}")
        }
    }
    
    private fun startDownloadMonitoring(filePath: String) {
        Log.d(TAG, "🔍 === DÉBUT MONITORING TÉLÉCHARGEMENT ===")
        Log.d(TAG, "📂 Monitoring file path: $filePath")
        Log.d(TAG, "🆔 Download ID: $downloadId")
        
        // Use a coroutine to monitor download progress periodically
        CoroutineScope(Dispatchers.IO).launch {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var iterationCount = 0
            
            while (true) {
                delay(1000) // Check every second
                iterationCount++
                
                Log.d(TAG, "🔄 Monitoring iteration #$iterationCount")
                
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                Log.d(TAG, "📋 Query result: cursor count = ${cursor.count}")
                
                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)
                    
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    
                    val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                    val bytesTotal = cursor.getLong(bytesTotalIndex)
                    val localUri = cursor.getString(localUriIndex) ?: "null"
                    val reason = cursor.getInt(reasonIndex)
                    
                    Log.d(TAG, "📊 Download status: $status, Progress: $bytesDownloaded/$bytesTotal bytes")
                    Log.d(TAG, "📍 Local URI: $localUri")
                    Log.d(TAG, "🔍 Reason code: $reason")
                    
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            Log.d(TAG, "✅ === TÉLÉCHARGEMENT TERMINÉ AVEC SUCCÈS ===")
                            Log.d(TAG, "📍 Local URI from DownloadManager: $localUri")
                            Log.d(TAG, "📊 Final download stats: $bytesDownloaded/$bytesTotal bytes")
                            
                            cursor.close()
                            
                            // Analyse détaillée du fichier via l'URI du DownloadManager
                            if (localUri != "null" && localUri.isNotEmpty()) {
                                Log.d(TAG, "🔍 Analysing DownloadManager URI: $localUri")
                                try {
                                    val uri = Uri.parse(localUri)
                                    val fileFromUri = File(uri.path ?: "")
                                    Log.d(TAG, "📂 File from URI: ${fileFromUri.absolutePath}")
                                    Log.d(TAG, "📊 File from URI exists: ${fileFromUri.exists()}")
                                    if (fileFromUri.exists()) {
                                        Log.d(TAG, "📏 File from URI size: ${fileFromUri.length()} bytes")
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "⚠️ Could not parse DownloadManager URI: ${e.message}")
                                }
                            }
                            
                            // Chercher le fichier téléchargé dans le cache externe de l'app
                            val appUpdateDir = File(context.externalCacheDir, "updates")
                            Log.d(TAG, "🔍 === ANALYSE DU RÉPERTOIRE UPDATES ===")
                            Log.d(TAG, "📂 Directory path: ${appUpdateDir.absolutePath}")
                            Log.d(TAG, "📊 Directory exists: ${appUpdateDir.exists()}")
                            Log.d(TAG, "📊 Directory readable: ${appUpdateDir.canRead()}")
                            Log.d(TAG, "📊 Directory writable: ${appUpdateDir.canWrite()}")
                            
                            // Lister TOUT le contenu du répertoire
                            Log.d(TAG, "📋 ALL directory contents:")
                            appUpdateDir.listFiles()?.forEach { file ->
                                Log.d(TAG, "   • ALL: ${file.name} (${file.length()} bytes, isFile: ${file.isFile}, readable: ${file.canRead()})")
                            } ?: Log.w(TAG, "   • Directory listFiles() returned null!")
                            
                            // Chercher spécifiquement les APK
                            val downloadedFiles = appUpdateDir.listFiles { _, name ->
                                name.endsWith(".apk")
                            }
                            
                            Log.d(TAG, "📦 APK files search result: ${downloadedFiles?.size ?: 0} files")
                            
                            val downloadFile = when {
                                downloadedFiles?.isNotEmpty() == true -> {
                                    Log.d(TAG, "📦 Found ${downloadedFiles.size} APK file(s):")
                                    downloadedFiles.forEachIndexed { index, file ->
                                        Log.d(TAG, "   • APK[$index]: ${file.name} (${file.length()} bytes)")
                                        Log.d(TAG, "     Path: ${file.absolutePath}")
                                        Log.d(TAG, "     Exists: ${file.exists()}, Readable: ${file.canRead()}")
                                    }
                                    Log.d(TAG, "🎯 Using first APK file: ${downloadedFiles.first().name}")
                                    downloadedFiles.first() // Prendre le premier fichier APK trouvé
                                }
                                else -> {
                                    Log.w(TAG, "⚠️ No APK files found in directory, trying original path...")
                                    Log.d(TAG, "📂 Fallback to original path: $filePath")
                                    File(filePath)
                                }
                            }
                            
                            Log.d(TAG, "📂 === VÉRIFICATION FICHIER FINAL ===")
                            Log.d(TAG, "📂 Final file to check: ${downloadFile.absolutePath}")
                            Log.d(TAG, "📊 File exists: ${downloadFile.exists()}")
                            Log.d(TAG, "📊 File readable: ${downloadFile.canRead()}")
                            Log.d(TAG, "📏 File size: ${downloadFile.length()} bytes")
                            Log.d(TAG, "📅 File last modified: ${downloadFile.lastModified()}")
                            
                            if (downloadFile.exists() && downloadFile.length() > 0) {
                                Log.d(TAG, "🎉 === FICHIER TROUVÉ ET VALIDE ===")
                                Log.d(TAG, "✅ File found! Size: ${downloadFile.length()} bytes")
                                
                                // Copier le fichier vers un emplacement sûr avant de nettoyer l'entrée DownloadManager
                                val safeFile = File(appUpdateDir, "install_${downloadFile.name}")
                                try {
                                    downloadFile.copyTo(safeFile, overwrite = true)
                                    Log.d(TAG, "📁 Fichier copié vers: ${safeFile.absolutePath}")
                                    
                                    // Maintenant on peut nettoyer l'entrée DownloadManager sans perdre le fichier
                                    try {
                                        downloadManager.remove(downloadId)
                                        Log.d(TAG, "🧹 Entrée DownloadManager nettoyée")
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Could not clear download entry: ${e.message}")
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        Log.d(TAG, "🚀 Notifying download completion to UI")
                                        listener?.onDownloadCompleted(safeFile)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Erreur lors de la copie du fichier: ${e.message}")
                                    // En cas d'erreur, utiliser le fichier original
                                    withContext(Dispatchers.Main) {
                                        listener?.onDownloadCompleted(downloadFile)
                                    }
                                }
                            } else {
                                Log.e(TAG, "❌ === FICHIER NON TROUVÉ OU INVALIDE ===")
                                Log.e(TAG, "❌ Downloaded file not found or empty!")
                                Log.d(TAG, "🗂️ Complete directory contents for debugging:")
                                
                                // Analyse complète du répertoire parent aussi
                                val parentDir = context.getExternalFilesDir(null)
                                Log.d(TAG, "📁 Parent directory: ${parentDir?.absolutePath}")
                                parentDir?.listFiles()?.forEach { file ->
                                    Log.d(TAG, "   • PARENT: ${file.name} (${if (file.isDirectory()) "DIR" else file.length().toString() + " bytes"})")
                                    if (file.isDirectory() && file.name == "updates") {
                                        file.listFiles()?.forEach { subFile ->
                                            Log.d(TAG, "     └── ${subFile.name} (${subFile.length()} bytes)")
                                        }
                                    }
                                }
                                
                                withContext(Dispatchers.Main) {
                                    listener?.onError("Fichier téléchargé non trouvé")
                                }
                            }
                            break
                        }
                        DownloadManager.STATUS_FAILED -> {
                            Log.e(TAG, "❌ === TÉLÉCHARGEMENT ÉCHOUÉ ===")
                            Log.e(TAG, "❌ Download failed! Reason: $reason")
                            Log.e(TAG, "📊 Download stats at failure: $bytesDownloaded/$bytesTotal bytes")
                            Log.e(TAG, "📍 Local URI: $localUri")
                            
                            // Décoder les raisons d'échec
                            val reasonText = when (reason) {
                                DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
                                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Device not found"
                                DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
                                DownloadManager.ERROR_FILE_ERROR -> "File error"
                                DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient space"
                                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
                                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP code"
                                DownloadManager.ERROR_UNKNOWN -> "Unknown error"
                                else -> "Other error ($reason)"
                            }
                            Log.e(TAG, "❌ Failure reason: $reasonText")
                            
                            cursor.close()
                            withContext(Dispatchers.Main) {
                                listener?.onError("Échec du téléchargement: $reasonText")
                            }
                            break
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            if (bytesTotal > 0) {
                                val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                                Log.d(TAG, "🏃 Download in progress: $progress% ($bytesDownloaded/$bytesTotal bytes)")
                                withContext(Dispatchers.Main) {
                                    listener?.onDownloadProgress(progress)
                                }
                            } else {
                                Log.d(TAG, "🏃 Download running, size unknown: $bytesDownloaded bytes")
                            }
                        }
                        DownloadManager.STATUS_PENDING -> {
                            Log.d(TAG, "⏳ Download pending...")
                        }
                        DownloadManager.STATUS_PAUSED -> {
                            Log.w(TAG, "⏸️ Download paused. Reason: $reason")
                        }
                        else -> {
                            Log.w(TAG, "❓ Unknown download status: $status")
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ === TÉLÉCHARGEMENT INTROUVABLE DANS CURSOR ===")
                    Log.w(TAG, "⚠️ Download not found in cursor for ID: $downloadId")
                    Log.w(TAG, "📊 Cursor count: ${cursor.count}")
                    cursor.close()
                    withContext(Dispatchers.Main) {
                        listener?.onError("Téléchargement introuvable")
                    }
                    break
                }
                cursor.close()
            }
            
            Log.d(TAG, "🔚 === FIN MONITORING TÉLÉCHARGEMENT ===")
        }
    }
    
    fun installUpdate(file: File) {
        try {
            Log.d(TAG, "📦 === DÉBUT INSTALLATION ===")
            Log.d(TAG, "📂 File path: ${file.absolutePath}")
            Log.d(TAG, "📊 File exists: ${file.exists()}")
            Log.d(TAG, "📊 File readable: ${file.canRead()}")
            Log.d(TAG, "📏 File size: ${file.length()} bytes")
            
            // Vérifier que le fichier existe et est valide
            if (!file.exists()) {
                Log.e(TAG, "❌ Le fichier APK n'existe pas: ${file.absolutePath}")
                listener?.onError("Fichier APK introuvable")
                return
            }
            
            if (!file.canRead()) {
                Log.e(TAG, "❌ Le fichier APK n'est pas lisible: ${file.absolutePath}")
                listener?.onError("Impossible de lire le fichier APK")
                return
            }
            
            if (file.length() == 0L) {
                Log.e(TAG, "❌ Le fichier APK est vide: ${file.absolutePath}")
                listener?.onError("Le fichier APK est vide")
                return
            }
            
            Log.d(TAG, "✅ Fichier APK valide, préparation de l'installation...")
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            Log.d(TAG, "📍 FileProvider URI: $uri")
            
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
            Log.d(TAG, "✅ === FIN LANCEMENT INSTALLATION ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error installing update", e)
            Log.e(TAG, "❌ Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "❌ Exception message: ${e.message}")
            Log.e(TAG, "❌ Stack trace: ${e.stackTraceToString()}")
            listener?.onError("Erreur d'installation: ${e.message}")
        }
    }
    
    private fun getCurrentVersion(): String {
        return try {
            // Utiliser BuildConfig.VERSION_DISPLAY_NAME comme Lawnchair
            val versionDisplayName = com.nextjsclient.android.BuildConfig.VERSION_DISPLAY_NAME
            Log.d(TAG, "Current app version display name: $versionDisplayName")
            versionDisplayName
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current version", e)
            "1.Dev.(unknown)"
        }
    }
    
    private fun isNewerVersion(current: String, latest: String, release: Release, assetNames: List<String>): Boolean {
        return try {
            // UNIQUEMENT LES BUILDS GITHUB - pas de version locale
            
            // Extraire le run number actuel (depuis #XX dans VERSION_DISPLAY_NAME)
            val currentRunNumber = try {
                val versionDisplayName = com.nextjsclient.android.BuildConfig.VERSION_DISPLAY_NAME
                if (versionDisplayName.contains("#")) {
                    versionDisplayName
                        .substringAfter("#")
                        .substringBefore(")")
                        .toIntOrNull() ?: 0
                } else {
                    0 // Pas un build GitHub
                }
            } catch (e: Exception) {
                0
            }
            
            // Extraire le run number de la release GitHub
            val latestRunNumber = try {
                val pattern = Regex("run(\\d+)")
                val match = pattern.find(release.name)
                match?.groupValues?.get(1)?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                0
            }
            
            Log.d(TAG, "Version check - Current: $currentRunNumber, Latest: $latestRunNumber")
            
            // Comparaison simple : plus récent = mise à jour disponible
            return latestRunNumber > currentRunNumber
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in version check", e)
            false
        }
    }
    
}