package com.nextjsclient.android

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var themeManager: ThemeManager
    private lateinit var auth: FirebaseAuth
    private lateinit var updateManager: UpdateManager
    private lateinit var supplierPreferences: SupplierPreferences
    private lateinit var biometricManager: BiometricManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var pendingUpdate: Release? = null
    private var downloadedFile: File? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        themeManager = ThemeManager(this)
        auth = FirebaseAuth.getInstance()
        updateManager = UpdateManager(this)
        supplierPreferences = SupplierPreferences(this)
        biometricManager = BiometricManager(this)
        
        setupWindowInsets()
        setupToolbar()
        setupViews()
        setupUpdateManager()
        setupSupplierPreferences()
        setupBiometric()
        updateUI()
        animateViews()
        
        // Check for updates when entering settings
        checkForUpdates()
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
        
        // App info with commit/build info
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: "1.0"
            val versionCode = packageInfo.longVersionCode
            
            // Try to get commit hash from BuildConfig or version name
            val commitHash = getCommitHash(versionName)
            
            if (commitHash != null) {
                binding.appVersion.text = getString(R.string.build_format, commitHash)
                binding.buildInfo.text = getString(R.string.version_code_format, versionName, versionCode)
            } else {
                binding.appVersion.text = getString(R.string.update_version_format, versionName)
                binding.buildInfo.text = getString(R.string.build_number_format, versionCode)
            }
        } catch (e: Exception) {
            binding.appVersion.text = getString(R.string.version_default)
            binding.buildInfo.text = getString(R.string.build_default)
        }
        
        // Tech stack info
        binding.androidVersion.text = getString(R.string.api_format, android.os.Build.VERSION.SDK_INT)
        binding.firebaseVersion.text = getString(R.string.firebase_text)
        binding.materialVersion.text = getString(R.string.material_text)
    }
    
    private fun getCommitHash(versionName: String): String? {
        return try {
            // Try to get commit from BuildConfig first
            val commitFromBuildConfig = com.nextjsclient.android.BuildConfig.COMMIT_HASH
            
            if (commitFromBuildConfig.isNotEmpty() && commitFromBuildConfig != "unknown") {
                return commitFromBuildConfig
            }
            
            // Fallback: extract from version name if it follows our nightly pattern
            val nightlyPattern = Regex("nightly-\\d+-([a-f0-9]{7,})")
            val match = nightlyPattern.find(versionName)
            if (match != null) {
                return match.groupValues[1]
            }
            
            // Try other patterns for commit hashes
            val commitPattern = Regex(".*-([a-f0-9]{7,}).*")
            val commitMatch = commitPattern.find(versionName)
            commitMatch?.groupValues?.get(1)
        } catch (e: Exception) {
            android.util.Log.d("SettingsActivity", "Could not get commit hash: ${e.message}")
            
            // Last resort: try to extract any 7+ character hex string
            val hexPattern = Regex("([a-f0-9]{7,})")
            val hexMatch = hexPattern.find(versionName)
            hexMatch?.groupValues?.get(1)
        }
    }
    
    private fun setupUpdateManager() {
        updateManager.setUpdateListener(object : UpdateManager.UpdateListener {
            override fun onUpdateChecking() {
                binding.updateStatus.text = getString(R.string.checking_updates)
                binding.updateButton.visibility = View.GONE
            }
            
            override fun onUpdateAvailable(release: Release) {
                binding.updateStatus.text = getString(R.string.update_available)
                binding.updateButton.visibility = View.GONE
                pendingUpdate = release
                // Rendre la carte cliquable pour ouvrir la bottom sheet
                binding.updateSection.isClickable = true
            }
            
            override fun onUpToDate() {
                binding.updateStatus.text = getString(R.string.up_to_date)
                binding.updateButton.visibility = View.GONE
            }
            
            override fun onDownloadStarted() {
                binding.updateStatus.text = getString(R.string.downloading)
                binding.updateButton.text = getString(R.string.downloading)
                binding.updateButton.isEnabled = false
            }
            
            override fun onDownloadProgress(progress: Int) {
                binding.updateStatus.text = getString(R.string.download_progress, progress)
            }
            
            override fun onDownloadCompleted(file: File) {
                binding.updateStatus.text = getString(R.string.installing)
                binding.updateButton.visibility = View.GONE
                downloadedFile = file
                
                // Automatically install after download
                updateManager.installUpdate(file)
            }
            
            override fun onError(message: String) {
                binding.updateStatus.text = message
                binding.updateButton.visibility = View.GONE
            }
        })
        
        // Setup update button click
        binding.updateButton.setOnClickListener {
            when {
                downloadedFile != null -> {
                    updateManager.installUpdate(downloadedFile!!)
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
            // Masquer la section si la biométrie n'est pas disponible
            binding.biometricSection.visibility = View.GONE
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
                // Tester l'authentification avant d'activer
                biometricManager.authenticate(
                    activity = this,
                    title = getString(R.string.biometric_activate_title),
                    subtitle = getString(R.string.biometric_activate_subtitle),
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
            biometricType.contains("Empreinte") -> getString(R.string.fingerprint_auth)
            biometricType.contains("Reconnaissance faciale") -> getString(R.string.face_auth)
            biometricType.contains("iris") -> getString(R.string.iris_auth)
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
            biometricType.contains("Empreinte") -> R.drawable.ic_fingerprint
            biometricType.contains("Reconnaissance faciale") -> R.drawable.ic_face
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
            coroutineScope.launch {
                updateManager.checkForUpdates()
            }
        } catch (e: Exception) {
            binding.updateStatus.text = getString(R.string.up_to_date)
            binding.updateButton.visibility = View.GONE
        }
    }
    
    private fun showUpdateBottomSheet(release: Release) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_update, null)
        bottomSheetDialog.setContentView(bottomSheetView)
        
        // Configurer les vues de la bottom sheet
        val updateVersion = bottomSheetView.findViewById<TextView>(R.id.updateVersion)
        val updateChangelog = bottomSheetView.findViewById<TextView>(R.id.updateChangelog)
        val cancelButton = bottomSheetView.findViewById<MaterialButton>(R.id.cancelButton)
        val installButton = bottomSheetView.findViewById<MaterialButton>(R.id.installButton)
        
        // Mettre à jour le contenu
        updateVersion.text = getString(R.string.update_version_format, release.tagName)
        
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
        
        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
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
        binding.currentTheme.text = themeManager.getThemeDisplayName(currentTheme)
    }
    
    private fun showThemeDialog() {
        val themes = themeManager.getAllThemes()
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
}