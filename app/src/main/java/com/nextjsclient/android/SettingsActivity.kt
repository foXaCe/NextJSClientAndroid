package com.nextjsclient.android

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.nextjsclient.android.databinding.ActivitySettingsBinding
import com.nextjsclient.android.utils.ThemeManager
import com.nextjsclient.android.utils.UpdateManager
import com.nextjsclient.android.utils.Release
import com.nextjsclient.android.utils.SupplierPreferences
import com.nextjsclient.android.utils.BiometricManager
import com.nextjsclient.android.utils.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import android.os.Handler
import android.os.Looper
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.Context

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var themeManager: ThemeManager
    private lateinit var auth: FirebaseAuth
    private lateinit var updateManager: UpdateManager
    private lateinit var supplierPreferences: SupplierPreferences
    private lateinit var biometricManager: BiometricManager
    private lateinit var localeManager: LocaleManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var pendingUpdate: Release? = null
    private var downloadedFile: File? = null
    private var installationTimeoutHandler: Handler? = null
    private var installationReceiver: BroadcastReceiver? = null
    
    override fun attachBaseContext(newBase: Context) {
        val localeManager = LocaleManager(newBase)
        val updatedContext = localeManager.applyLanguageToContext(newBase, localeManager.getCurrentLanguage())
        super.attachBaseContext(updatedContext)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        themeManager = ThemeManager(this)
        auth = FirebaseAuth.getInstance()
        updateManager = UpdateManager(this)
        supplierPreferences = SupplierPreferences(this)
        biometricManager = BiometricManager(this)
        localeManager = LocaleManager(this)
        
        setupWindowInsets()
        setupToolbar()
        setupViews()
        setupSwipeRefresh()
        setupUpdateManager()
        setupSupplierPreferences()
        setupBiometric()
        setupLanguageSelector()
        updateUI()
        animateViews()
        
        // Check for updates when entering settings
        checkForUpdates()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        installationTimeoutHandler?.removeCallbacksAndMessages(null)
        
        // Désenregistrer le receiver d'installation
        installationReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error unregistering installation receiver", e)
            }
        }
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, systemBars.bottom)
            insets
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)
    }
    
    private fun setupSwipeRefresh() {
        // Configurer les couleurs du SwipeRefreshLayout
        binding.swipeRefresh.setColorSchemeResources(
            R.color.md_theme_light_primary,
            R.color.md_theme_light_secondary,
            R.color.md_theme_light_tertiary
        )
        
        // Action de refresh
        binding.swipeRefresh.setOnRefreshListener {
            // Réinitialiser l'état de la mise à jour
            pendingUpdate = null
            downloadedFile = null
            binding.updateProgressBar.visibility = View.GONE
            binding.updateProgressBar.progress = 0
            binding.updateButton.visibility = View.GONE
            
            // Vérifier les mises à jour
            checkForUpdates()
        }
    }
    
    private fun setupViews() {
        // Theme selector with ripple effect
        binding.themeSelector.setOnClickListener {
            animateClick(it)
            showThemeDialog()
        }
        
        // User info and avatar
        val user = auth.currentUser
        if (user != null) {
            binding.userEmail.text = user.email ?: getString(R.string.email_not_available)
            
            // Set user initial in avatar
            val initial = user.email?.firstOrNull()?.uppercaseChar() ?: 'U'
            binding.userInitial.text = initial.toString()
        }
        
        // Logout icon with confirmation
        binding.logoutIcon.setOnClickListener {
            animateClick(it)
            showLogoutDialog()
        }
        
        // Affichage version simplifiée
        try {
            val versionDisplayName = com.nextjsclient.android.BuildConfig.VERSION_DISPLAY_NAME
            val actualBuildNumber = com.nextjsclient.android.BuildConfig.BUILD_NUMBER
            
            // Log pour debug
            android.util.Log.d("SettingsActivity", "versionDisplayName: $versionDisplayName")
            android.util.Log.d("SettingsActivity", "actualBuildNumber: $actualBuildNumber")
            
            // Toujours afficher le vrai buildNumber depuis BuildConfig
            binding.buildInfo.text = "(#$actualBuildNumber)"
            
        } catch (e: Exception) {
            binding.buildInfo.text = "(Build unknown)"
        }
        
        // Tech stack info
        binding.androidVersion.text = getString(R.string.api_format, android.os.Build.VERSION.SDK_INT)
        binding.firebaseVersion.text = getString(R.string.firebase_text)
        binding.materialVersion.text = getString(R.string.material_text)
    }
    
    private fun setupUpdateManager() {
        updateManager.setUpdateListener(object : UpdateManager.UpdateListener {
            override fun onUpdateChecking() {
                binding.updateStatus.text = getString(R.string.checking_updates)
                binding.updateButton.visibility = View.GONE
                // Ne pas arrêter le SwipeRefresh ici car la vérification est en cours
            }
            
            override fun onUpdateAvailable(release: Release) {
                // Extraire la version depuis le nom de la release
                val versionText = when {
                    release.name.contains("nightly", ignoreCase = true) -> {
                        // Extraire la version depuis le nom (ex: "Version 1.2.3" depuis le nom)
                        val versionPattern = Regex("Version\\s+([\\d.]+)")
                        val versionMatch = versionPattern.find(release.name)
                        val version = versionMatch?.groupValues?.get(1) ?: ""
                        
                        // Extraire le run number
                        val runPattern = Regex("run(\\d+)")
                        val runMatch = runPattern.find(release.name)
                        val runNumber = runMatch?.groupValues?.get(1) ?: ""
                        
                        if (version.isNotEmpty() && runNumber.isNotEmpty()) {
                            "Version $version (Build #$runNumber)"
                        } else if (runNumber.isNotEmpty()) {
                            "Build #$runNumber"
                        } else {
                            release.tagName
                        }
                    }
                    else -> "Version ${release.tagName}"
                }
                binding.updateStatus.text = "$versionText ${getString(R.string.available)} • ${getString(R.string.click_here)}"
                binding.updateButton.visibility = View.GONE
                binding.updateProgressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false  // Arrêter le loader
                pendingUpdate = release
                // Rendre la carte cliquable pour ouvrir la bottom sheet
                binding.updateSection.isClickable = true
                
                // Ajouter effet visuel pour indiquer qu'il y a une mise à jour
                addUpdateAvailableEffect()
            }
            
            override fun onUpToDate() {
                binding.updateStatus.text = getString(R.string.up_to_date)
                binding.updateButton.visibility = View.GONE
                binding.updateProgressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
            
            override fun onDownloadStarted() {
                binding.updateStatus.text = getString(R.string.downloading)
                binding.updateButton.visibility = View.GONE
                binding.updateProgressBar.visibility = View.VISIBLE
                binding.updateProgressBar.progress = 0
                binding.swipeRefresh.isRefreshing = false  // Arrêter le loader car le téléchargement a commencé
            }
            
            override fun onDownloadProgress(progress: Int) {
                binding.updateStatus.text = getString(R.string.downloading_update)
                binding.updateProgressBar.progress = progress
            }
            
            override fun onDownloadCompleted(file: File) {
                binding.updateStatus.text = getString(R.string.ready_to_install)
                binding.updateProgressBar.visibility = View.GONE
                binding.updateButton.text = getString(R.string.install_now)
                binding.updateButton.visibility = View.VISIBLE
                binding.updateButton.isEnabled = true
                binding.swipeRefresh.isRefreshing = false  // S'assurer que le loader est arrêté
                downloadedFile = file
                
                // Ne pas lancer l'installation automatiquement
                // L'utilisateur doit cliquer sur le bouton pour installer
            }
            
            override fun onInstallationStarted() {
                binding.updateStatus.text = getString(R.string.installation_in_progress)
                binding.updateButton.visibility = View.GONE
                binding.updateProgressBar.visibility = View.GONE
                // Ne pas afficher d'erreur, l'installation est en cours
            }
            
            override fun onError(message: String) {
                binding.updateStatus.text = message
                binding.updateButton.visibility = View.GONE
                binding.updateProgressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                
                // Réinitialiser l'état après une erreur
                pendingUpdate = null
                downloadedFile = null
            }
        })
        
        // Setup update button click
        binding.updateButton.setOnClickListener {
            when {
                downloadedFile != null -> {
                    binding.updateStatus.text = getString(R.string.installing)
                    binding.updateButton.visibility = View.GONE
                    
                    // Enregistrer un receiver pour écouter les événements d'installation
                    setupInstallationReceiver()
                    
                    // Démarrer un timeout pour détecter les échecs d'installation
                    startInstallationTimeout()
                    
                    // Vérifier que le fichier est toujours valide avant installation
                    if (downloadedFile!!.exists() && downloadedFile!!.canRead()) {
                        updateManager.installUpdate(downloadedFile!!)
                    } else {
                        binding.updateStatus.text = getString(R.string.apk_file_corrupted)
                        binding.updateButton.text = getString(R.string.install_now)
                        binding.updateButton.visibility = View.VISIBLE
                        binding.updateButton.isEnabled = true
                    }
                }
                pendingUpdate != null -> {
                    updateManager.downloadUpdate(pendingUpdate!!)
                }
            }
        }
        
        // Setup update section click
        binding.updateSection.setOnClickListener {
            animateClick(it)
            if (pendingUpdate != null) {
                // Si une mise à jour est disponible, ouvrir la bottom sheet
                showUpdateBottomSheet(pendingUpdate!!)
            } else {
                // Sinon, vérifier les mises à jour
                checkForUpdates()
            }
        }
    }
    
    private fun setupSupplierPreferences() {
        // Initialize checkbox states from preferences
        binding.anecoopCheckbox.isChecked = supplierPreferences.isAnecoopEnabled
        binding.solagoraCheckbox.isChecked = supplierPreferences.isSolagoraEnabled
        
        // Setup checkbox listeners
        binding.anecoopCheckbox.setOnCheckedChangeListener { _, isChecked ->
            // Ensure at least one supplier remains enabled
            if (!isChecked && !binding.solagoraCheckbox.isChecked) {
                // Prevent unchecking if it would leave no suppliers enabled
                binding.anecoopCheckbox.isChecked = true
                Toast.makeText(this, getString(R.string.supplier_required), Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            
            supplierPreferences.isAnecoopEnabled = isChecked
            supplierPreferences.validateSettings()
            
            // Show confirmation message
            val message = if (isChecked) getString(R.string.anecoop_enabled) else getString(R.string.anecoop_disabled)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        
        binding.solagoraCheckbox.setOnCheckedChangeListener { _, isChecked ->
            // Ensure at least one supplier remains enabled
            if (!isChecked && !binding.anecoopCheckbox.isChecked) {
                // Prevent unchecking if it would leave no suppliers enabled
                binding.solagoraCheckbox.isChecked = true
                Toast.makeText(this, getString(R.string.supplier_required), Toast.LENGTH_SHORT).show()
                return@setOnCheckedChangeListener
            }
            
            supplierPreferences.isSolagoraEnabled = isChecked
            supplierPreferences.validateSettings()
            
            // Show confirmation message
            val message = if (isChecked) getString(R.string.solagora_enabled) else getString(R.string.solagora_disabled)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupBiometric() {
        // Vérifier si la biométrie est disponible
        val isAvailable = biometricManager.isBiometricAvailable()
        val isConfigured = biometricManager.isBiometricConfigured()
        
        if (!isAvailable) {
            // Masquer toute la section sécurité si la biométrie n'est pas disponible
            binding.securitySection.visibility = View.GONE
            return
        }
        
        // Mettre à jour l'interface en fonction du statut
        updateBiometricUI()
        
        // Configurer le switch
        binding.biometricSwitch.isChecked = biometricManager.isBiometricEnabledInApp()
        binding.biometricSwitch.isEnabled = isConfigured
        
        // Listener pour le switch
        binding.biometricSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && isConfigured) {
                // Tester l'authentification avant d'activer avec fallback face -> fingerprint
                biometricManager.authenticateWithFallback(
                    activity = this,
                    onSuccess = {
                        biometricManager.setBiometricEnabled(true)
                        updateBiometricUI()
                        Toast.makeText(this, getString(R.string.biometric_enabled), Toast.LENGTH_SHORT).show()
                    },
                    onError = { error ->
                        binding.biometricSwitch.isChecked = false
                        Toast.makeText(this, getString(R.string.biometric_error, error), Toast.LENGTH_SHORT).show()
                    },
                    onCancel = {
                        binding.biometricSwitch.isChecked = false
                    }
                )
            } else {
                // Désactiver directement
                biometricManager.setBiometricEnabled(false)
                updateBiometricUI()
                Toast.makeText(this, getString(R.string.biometric_disabled), Toast.LENGTH_SHORT).show()
            }
        }
        
        // Click sur la section pour ouvrir les paramètres si non configuré
        binding.biometricSection.setOnClickListener {
            if (!isConfigured) {
                animateClick(it)
                showBiometricSettingsDialog()
            }
        }
    }
    
    private fun updateBiometricUI() {
        val biometricType = biometricManager.getBiometricType()
        val statusMessage = biometricManager.getBiometricStatusMessage()
        val isConfigured = biometricManager.isBiometricConfigured()
        
        // Mettre à jour le titre en fonction du type
        binding.biometricTitle.text = when {
            biometricType.contains("Fingerprint") || biometricType.contains("Empreinte") -> getString(R.string.fingerprint_auth)
            biometricType.contains("Face") || biometricType.contains("Reconnaissance faciale") -> getString(R.string.face_auth)
            biometricType.contains("iris") || biometricType.contains("Iris") -> getString(R.string.iris_auth)
            else -> getString(R.string.biometric_auth)
        }
        
        // Mettre à jour le statut
        binding.biometricStatus.text = if (isConfigured && biometricManager.isBiometricEnabledInApp()) {
            getString(R.string.biometric_enabled_status)
        } else {
            statusMessage
        }
        
        // Mettre à jour l'icône en fonction du type
        val iconRes = when {
            biometricType.contains("Fingerprint") || biometricType.contains("Empreinte") -> R.drawable.ic_fingerprint
            biometricType.contains("Face") || biometricType.contains("Reconnaissance faciale") -> R.drawable.ic_face
            else -> R.drawable.ic_fingerprint
        }
        binding.biometricIcon.setImageResource(iconRes)
        
        // Activer/désactiver le switch
        binding.biometricSwitch.isEnabled = isConfigured
    }
    
    private fun showBiometricSettingsDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.biometric_setup_title))
            .setMessage(getString(R.string.biometric_setup_message))
            .setPositiveButton(getString(R.string.settings_button)) { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.unable_open_settings), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }
    
    private fun checkForUpdates() {
        try {
            // Afficher l'indicateur de chargement si ce n'est pas déjà fait par swipe refresh
            if (!binding.swipeRefresh.isRefreshing) {
                binding.updateStatus.text = getString(R.string.checking_updates)
                binding.updateProgressBar.visibility = View.GONE
            }
            
            coroutineScope.launch {
                try {
                    updateManager.checkForUpdates()
                    
                    // Timeout de sécurité pour arrêter le loader si aucun callback n'est appelé
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (binding.swipeRefresh.isRefreshing) {
                            binding.swipeRefresh.isRefreshing = false
                            binding.updateStatus.text = getString(R.string.update_check_error)
                        }
                    }, 15000) // 15 secondes de timeout
                    
                } catch (e: Exception) {
                    binding.updateStatus.text = getString(R.string.update_check_error)
                    binding.updateButton.visibility = View.GONE
                    binding.updateProgressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                }
            }
        } catch (e: Exception) {
            binding.updateStatus.text = getString(R.string.update_check_error)
            binding.updateButton.visibility = View.GONE
            binding.updateProgressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
        }
    }
    
    private fun showUpdateBottomSheet(release: Release) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_update, null)
        bottomSheetDialog.setContentView(bottomSheetView)
        
        // Configurer les vues de la bottom sheet
        val updateChangelog = bottomSheetView.findViewById<TextView>(R.id.updateChangelog)
        val cancelButton = bottomSheetView.findViewById<MaterialButton>(R.id.cancelButton)
        val installButton = bottomSheetView.findViewById<MaterialButton>(R.id.installButton)
        
        // Formater le changelog (commits)
        val formattedChangelog = formatChangelog(release.body)
        updateChangelog.text = formattedChangelog
        
        // Configurer les boutons
        cancelButton.setOnClickListener {
            bottomSheetDialog.dismiss()
        }
        
        installButton.setOnClickListener {
            bottomSheetDialog.dismiss()
            // Démarrer le téléchargement
            updateManager.downloadUpdate(release)
        }
        
        bottomSheetDialog.show()
    }
    
    private fun formatChangelog(body: String): String {
        if (body.isBlank()) return getString(R.string.update_fallback_changelog)
        
        val lines = body.split("\n")
        val formattedLines = mutableListOf<String>()
        var skipSection = false
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // Ignorer les sections indésirables
            if (trimmedLine.startsWith("🔍") || trimmedLine.startsWith("🔢") || 
                trimmedLine.startsWith("🌟") || trimmedLine.startsWith("📱") || 
                trimmedLine.startsWith("📦") || trimmedLine.contains("Installation") ||
                trimmedLine.contains("Sources inconnues") || trimmedLine.contains("APK Disponible") ||
                trimmedLine.contains("Commit:") || trimmedLine.contains("Run Number:") ||
                trimmedLine.contains("Branch:") || trimmedLine.contains("Build Time:") ||
                trimmedLine.contains("Version:") || trimmedLine.contains(".apk")) {
                skipSection = true
                continue
            }
            
            // Réinitialiser après une ligne vide
            if (trimmedLine.isEmpty()) {
                skipSection = false
                continue
            }
            
            // Ajouter seulement les vraies modifications
            if (!skipSection && trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                // Ajouter une puce si ce n'est pas déjà fait
                val formatted = if (trimmedLine.startsWith("•") || trimmedLine.startsWith("-") || trimmedLine.startsWith("*")) {
                    "• ${trimmedLine.substring(1).trim()}"
                } else {
                    "• $trimmedLine"
                }
                formattedLines.add(formatted)
            }
        }
        
        return if (formattedLines.isNotEmpty()) {
            formattedLines.joinToString("\n")
        } else {
            getString(R.string.update_fallback_detailed)
        }
    }
    
    private fun formatUpdateDate(publishedAt: String): String {
        return try {
            // Parse la date ISO 8601 de GitHub
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
            inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            
            val date = inputFormat.parse(publishedAt) ?: return publishedAt
            
            // Format français avec timezone Paris
            val outputFormat = java.text.SimpleDateFormat("d MMMM yyyy 'à' HH:mm", java.util.Locale.FRENCH)
            outputFormat.timeZone = java.util.TimeZone.getTimeZone("Europe/Paris")
            
            outputFormat.format(date)
        } catch (e: Exception) {
            // Fallback en cas d'erreur de parsing
            publishedAt
        }
    }
    
    private fun addUpdateAvailableEffect() {
        // Animation de pulsation pour attirer l'attention
        val pulseAnimation = android.animation.ObjectAnimator.ofFloat(binding.updateSection, "alpha", 1f, 0.7f, 1f)
        pulseAnimation.duration = 1500
        pulseAnimation.repeatCount = android.animation.ObjectAnimator.INFINITE
        pulseAnimation.repeatMode = android.animation.ObjectAnimator.REVERSE
        pulseAnimation.start()
        
        // Note: updateSection is a LinearLayout, not a MaterialCardView
        // Border effect not applicable for LinearLayout
    }
    
    private fun showReleaseNotes(release: Release) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.update_title_format, release.tagName))
            .setMessage(release.body)
            .setPositiveButton(getString(R.string.install_button)) { _, _ ->
                downloadedFile?.let { updateManager.installUpdate(it) }
            }
            .setNegativeButton(getString(R.string.update_button_later), null)
            .show()
    }
    
    private fun updateUI() {
        val currentTheme = themeManager.getCurrentTheme()
        binding.currentTheme.text = themeManager.getThemeDisplayName(currentTheme, this)
        
        // Update language display
        binding.currentLanguage.text = localeManager.getCurrentLanguageDisplayName()
    }
    
    private fun showThemeDialog() {
        val themes = themeManager.getAllThemes(this)
        val themeNames = themes.map { it.second }.toTypedArray()
        val currentTheme = themeManager.getCurrentTheme()
        val currentIndex = themes.indexOfFirst { it.first == currentTheme }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.choose_theme))
            .setSingleChoiceItems(themeNames, currentIndex) { dialog, which ->
                val selectedTheme = themes[which].first
                themeManager.setTheme(selectedTheme)
                updateUI()
                dialog.dismiss()
                
                // Smooth transition before recreating
                binding.root.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        recreate()
                    }
                    .start()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }
    
    private fun setupLanguageSelector() {
        binding.languageSelector.setOnClickListener {
            showLanguageDialog()
        }
    }
    
    private fun showLanguageDialog() {
        val languages = LocaleManager.getSupportedLanguages(this)
        val languageNames = languages.map { it.nativeName }.toTypedArray()
        val currentLanguage = localeManager.getCurrentLanguage()
        val currentIndex = languages.indexOfFirst { it.code == currentLanguage }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.language_dialog_title))
            .setSingleChoiceItems(languageNames, currentIndex) { dialog, which ->
                val selectedLanguage = languages[which].code
                localeManager.setLanguage(selectedLanguage)
                updateUI()
                dialog.dismiss()
                
                // Smooth transition before recreating to apply language change
                binding.root.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        recreate()
                    }
                    .start()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }
    
    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.logout_title))
            .setMessage(getString(R.string.logout_message))
            .setPositiveButton(getString(R.string.logout_button)) { _, _ ->
                logout()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }
    
    private fun logout() {
        auth.signOut()
        val intent = Intent(this, AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun animateViews() {
        // Animate profile header
        binding.userEmail.alpha = 0f
        binding.userInitial.scaleX = 0f
        binding.userInitial.scaleY = 0f
        
        // Avatar animation
        val avatarAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.userInitial, View.SCALE_X, 0f, 1.1f, 1f),
                ObjectAnimator.ofFloat(binding.userInitial, View.SCALE_Y, 0f, 1.1f, 1f)
            )
            duration = 500
            interpolator = DecelerateInterpolator()
        }
        
        // Text animations
        binding.userEmail.animate()
            .alpha(1f)
            .setDuration(600)
            .setStartDelay(200)
            .start()
        
        
        avatarAnimator.start()
        
        // Animate cards with stagger effect
        val cards = listOf(
            binding.themeSelector
        )
        
        cards.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setStartDelay((100 * index).toLong())
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
    
    private fun animateClick(view: View) {
        val scaleDown = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f)
        val scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f)
        val scaleUp = ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f)
        val scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f)
        
        scaleDown.duration = 100
        scaleDownY.duration = 100
        scaleUp.duration = 100
        scaleUpY.duration = 100
        
        val scaleSet = AnimatorSet()
        scaleSet.play(scaleDown).with(scaleDownY)
        scaleSet.play(scaleUp).with(scaleUpY).after(scaleDown)
        scaleSet.start()
    }
    
    override fun onResume() {
        super.onResume()
        // Rafraîchir l'état de la biométrie au retour des paramètres
        if (::biometricManager.isInitialized && biometricManager.isBiometricAvailable()) {
            updateBiometricUI()
            binding.biometricSwitch.isChecked = biometricManager.isBiometricEnabledInApp()
            binding.biometricSwitch.isEnabled = biometricManager.isBiometricConfigured()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun startInstallationTimeout() {
        installationTimeoutHandler = Handler(Looper.getMainLooper())
        installationTimeoutHandler?.postDelayed({
            // Si l'installation prend plus de 2 minutes, considérer qu'elle a échoué
            // Vérifier d'abord si l'installation n'est pas en cours dans les paramètres système
            if (downloadedFile != null && !isInstallationInProgress()) {
                binding.updateStatus.text = getString(R.string.installation_timeout)
                binding.updateButton.text = getString(R.string.install_now)
                binding.updateButton.visibility = View.VISIBLE
                binding.updateButton.isEnabled = true
                
                Toast.makeText(this, getString(R.string.installation_timeout), Toast.LENGTH_LONG).show()
            }
        }, 120000) // 2 minutes au lieu de 30 secondes
    }
    
    private fun isInstallationInProgress(): Boolean {
        return try {
            // Vérifier si PackageInstaller est actif (installation en cours)
            val packageManager = packageManager
            val packageInstaller = packageManager.packageInstaller
            val sessions = packageInstaller.allSessions
            sessions.any { it.isActive }
        } catch (e: Exception) {
            false
        }
    }
    
    private fun setupInstallationReceiver() {
        // Désactiver l'ancien receiver s'il existe
        installationReceiver?.let { receiver ->
            try {
                unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e("SettingsActivity", "Error unregistering old receiver", e)
            }
        }
        
        // Créer un nouveau receiver pour écouter les événements d'installation
        installationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_PACKAGE_REPLACED, Intent.ACTION_PACKAGE_ADDED -> {
                        val installedPackageName = intent.data?.schemeSpecificPart
                        Log.d("SettingsActivity", "Package installation success: $installedPackageName")
                        if (installedPackageName == this@SettingsActivity.packageName) {
                            // L'installation a réussi
                            binding.updateStatus.text = getString(R.string.installation_success)
                            installationTimeoutHandler?.removeCallbacksAndMessages(null)
                            downloadedFile = null
                        }
                    }
                    // Intent.ACTION_PACKAGE_INSTALL deprecated - removed
                }
            }
        }
        
        // Enregistrer le receiver pour les événements de package
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            // ACTION_PACKAGE_INSTALL deprecated - removed
            addDataScheme("package")
        }
        
        try {
            registerReceiver(installationReceiver, filter)
            Log.d("SettingsActivity", "Installation receiver registered")
            
            // Démarrer une vérification périodique de l'état d'installation
            startInstallationStatusCheck()
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error registering installation receiver", e)
        }
    }
    
    private fun startInstallationStatusCheck() {
        // Vérifier toutes les 2 secondes si l'installation est toujours en cours
        val handler = Handler(Looper.getMainLooper())
        var checkCount = 0
        val maxChecks = 15 // 30 secondes max (2s * 15)
        
        val checkRunnable = object : Runnable {
            override fun run() {
                checkCount++
                
                // Vérifier si l'installateur Android est toujours en cours
                val installerRunning = isPackageInstallerRunning()
                
                Log.d("SettingsActivity", "Installation check $checkCount/$maxChecks, installer running: $installerRunning")
                
                // Si l'installateur n'est plus en cours et qu'on n'a pas reçu de succès
                if (!installerRunning && downloadedFile != null) {
                    // L'installation a probablement échoué
                    Log.w("SettingsActivity", "Installation likely failed - installer not running")
                    binding.updateStatus.text = getString(R.string.installation_failed)
                    binding.updateButton.text = getString(R.string.install_now)
                    binding.updateButton.visibility = View.VISIBLE
                    binding.updateButton.isEnabled = true
                    installationTimeoutHandler?.removeCallbacksAndMessages(null)
                    return
                }
                
                // Continuer la vérification si on n'a pas atteint le max
                if (checkCount < maxChecks && downloadedFile != null) {
                    handler.postDelayed(this, 2000)
                }
            }
        }
        
        // Démarrer la première vérification après 3 secondes (laisser le temps à l'installateur de démarrer)
        handler.postDelayed(checkRunnable, 3000)
    }
    
    private fun isPackageInstallerRunning(): Boolean {
        return try {
            val activityManager = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningApps = activityManager.runningAppProcesses
            
            // Chercher le processus de l'installateur Android
            runningApps?.any { processInfo ->
                processInfo.processName.contains("packageinstaller", ignoreCase = true) ||
                processInfo.processName.contains("com.google.android.packageinstaller", ignoreCase = true) ||
                processInfo.processName.contains("com.android.packageinstaller", ignoreCase = true)
            } ?: false
        } catch (e: Exception) {
            Log.e("SettingsActivity", "Error checking package installer status", e)
            true // En cas d'erreur, assumer qu'il est toujours en cours
        }
    }
    
}