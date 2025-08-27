package com.nextjsclient.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.WindowInsetsController
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.appcompat.widget.SearchView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.nextjsclient.android.databinding.ActivityMainBinding
import com.nextjsclient.android.ui.scamark.ScamarkFragment
import com.nextjsclient.android.ui.overview.OverviewFragment
import com.nextjsclient.android.utils.ThemeManager
import com.nextjsclient.android.utils.SupplierThemeManager
import com.nextjsclient.android.utils.SupplierPreferences
import com.nextjsclient.android.utils.BiometricManager
import com.nextjsclient.android.utils.UpdateManager
import com.nextjsclient.android.utils.LocaleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Context

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var navController: NavController? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var themeManager: ThemeManager
    private lateinit var supplierThemeManager: SupplierThemeManager
    private lateinit var supplierPreferences: SupplierPreferences
    private lateinit var biometricManager: BiometricManager
    private lateinit var localeManager: LocaleManager
    private var currentScamarkFragment: ScamarkFragment? = null
    private var isBiometricPromptShown = false
    private var isAppInBackground = false
    private var isInternalNavigation = false
    private var lastStopTime = 0L
    private var biometricLockOverlay: View? = null
    private var currentSupplier: String = "anecoop"
    
    
    // Cache pour les données préchargées
    private var preloadedData: MutableMap<String, Pair<List<com.nextjsclient.android.data.models.ScamarkProduct>, List<com.nextjsclient.android.data.models.AvailableWeek>>> = mutableMapOf()
    private var preloadedFilters: MutableMap<String, String> = mutableMapOf()
    
    // Cache pour la semaine sélectionnée lors de navigation depuis l'aperçu
    private var navigationYear: Int? = null
    private var navigationWeek: Int? = null
    
    override fun attachBaseContext(newBase: Context) {
        val localeManager = LocaleManager(newBase)
        val updatedContext = localeManager.applyLanguageToContext(newBase, localeManager.getCurrentLanguage())
        super.attachBaseContext(updatedContext)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize theme before calling super.onCreate()
        themeManager = ThemeManager(this)
        themeManager.initializeTheme()
        
        // Switch from splash screen theme to normal theme to show status bar
        setTheme(R.style.Theme_NextJSClient)
        
        super.onCreate(savedInstanceState)
        
        auth = FirebaseAuth.getInstance()
        supplierThemeManager = SupplierThemeManager(this)
        supplierPreferences = SupplierPreferences(this)
        biometricManager = BiometricManager(this)
        localeManager = LocaleManager(this)
        
        // Nettoyer les anciennes APK au démarrage
        cleanOldApks()
        
        // Check if user is logged in
        if (auth.currentUser == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupWindowInsets()
        // setSupportActionBar(binding.toolbar) // AppBarLayout supprimé
        
        // Configuration status bar
        configureStatusBar()
        
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            navController = navHostFragment?.navController
        } catch (e: Exception) {
            // En cas d'erreur de navigation, continuer sans navController
            navController = null
        }
        
        // Configure toolbar (no title, no navigation arrows)
        supportActionBar?.title = ""
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        
        // Configure status bar
        configureStatusBar()
        
        // Setup custom bottom navigation for suppliers
        setupSupplierNavigation()
        
        // Set default to Overview (using the correct menu item)
        binding.bottomNavigation.selectedItemId = R.id.navigation_overview
        switchToOverview()
        
        // Apply initial theme
        applySupplierTheme(currentSupplier)
        
        // OPTIMISATION: Preload agressif des deux fournisseurs au démarrage
        if (savedInstanceState == null) {
            preloadAllSuppliersOnStartup()
            checkBiometricAuthentication()
        }
    }
    
    /**
     * OPTIMISATION: Preload agressif de tous les fournisseurs au démarrage
     */
    private fun preloadAllSuppliersOnStartup() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = com.nextjsclient.android.data.repository.FirebaseRepository()
                val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                val week = getCurrentISOWeek()
                
                // Preload parallèle des deux fournisseurs
                val anecoopJob = launch {
                    if (supplierPreferences.isAnecoopEnabled) {
                        repository.getAvailableWeeks("anecoop")
                        repository.getWeekDecisions(year, week, "anecoop")
                    }
                }
                
                val solagoraJob = launch {
                    if (supplierPreferences.isSolagoraEnabled) {
                        repository.getAvailableWeeks("solagora")
                        repository.getWeekDecisions(year, week, "solagora")
                    }
                }
                
                // Attendre que tout soit terminé
                anecoopJob.join()
                solagoraJob.join()
                
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error in preload: ${e.message}")
            }
        }
    }
    
    /**
     * Précharge les données de l'autre fournisseur en arrière-plan
     */
    private fun preloadOtherSupplier(currentSupplier: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val otherSupplier = if (currentSupplier == "anecoop") "solagora" else "anecoop"
                
                // Vérifier si le fournisseur est activé
                val isOtherEnabled = if (otherSupplier == "anecoop") {
                    supplierPreferences.isAnecoopEnabled
                } else {
                    supplierPreferences.isSolagoraEnabled
                }
                
                if (isOtherEnabled) {
                    // Précharger les données via le repository pour remplir le cache
                    val repository = com.nextjsclient.android.data.repository.FirebaseRepository()
                    val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val week = getCurrentISOWeek()
                    
                    // Précharger les semaines et les données
                    repository.getAvailableWeeks(otherSupplier)
                    repository.getWeekDecisions(year, week, otherSupplier)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error preloading: ${e.message}")
            }
        }
    }
    
    private fun getCurrentISOWeek(): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.firstDayOfWeek = java.util.Calendar.MONDAY
        calendar.minimalDaysInFirstWeek = 4
        return calendar.get(java.util.Calendar.WEEK_OF_YEAR)
    }
    
    /**
     * Nettoie les anciennes APK téléchargées pour libérer de l'espace
     */
    private fun cleanOldApks() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updateManager = UpdateManager(this@MainActivity)
                updateManager.cleanOldApks()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Re-configurer la status bar à chaque retour
        configureStatusBar()
        
        // Mettre à jour la visibilité du menu quand on revient de la page paramètres
        updateNavigationVisibility()
        
        // Vérifier l'authentification biométrique seulement si on revient d'un vrai arrière-plan
        // (pas d'une navigation interne comme Settings)
        val shouldCheckBiometric = isAppInBackground && !isInternalNavigation && !isBiometricPromptShown && biometricManager.isBiometricEnabledInApp()
        
        if (shouldCheckBiometric) {
            checkBiometricAuthentication()
        }
        
        // Réinitialiser les flags
        isAppInBackground = false
        isInternalNavigation = false
        lastStopTime = 0L
    }
    
    override fun onPause() {
        super.onPause()
        // Marquer que l'app va potentiellement en arrière-plan
        // Sera confirmé dans onStop() si c'est un vrai arrière-plan
        isBiometricPromptShown = false
        
        // IMPORTANT: Ne PAS réinitialiser isInternalNavigation ici
        // Il sera réinitialisé seulement dans onResume()
    }
    
    override fun onStop() {
        super.onStop()
        
        // L'app va en arrière-plan, mais on garde la trace si c'était une navigation interne
        isAppInBackground = true
        lastStopTime = System.currentTimeMillis()
    }
    
    private fun setupSupplierNavigation() {
        // Mettre à jour la visibilité du menu selon les préférences
        updateNavigationVisibility()
        
        // Appliquer les couleurs personnalisées pour chaque élément de navigation
        setupNavigationColors()
        
        // La configuration de navigation est maintenant gérée dans setupNavigationColors()
    }
    
    private fun setupNavigationColors() {
        // Stocker les couleurs pour chaque item
        val itemColors = mapOf(
            R.id.navigation_overview to ContextCompat.getColorStateList(this, R.color.nav_overview_color),
            R.id.navigation_anecoop to ContextCompat.getColorStateList(this, R.color.nav_anecoop_color),
            R.id.navigation_solagora to ContextCompat.getColorStateList(this, R.color.nav_solagora_color),
            R.id.navigation_search to ContextCompat.getColorStateList(this, R.color.nav_search_color)
        )
        
        // Appliquer les couleurs initiales
        updateNavigationItemColors(binding.bottomNavigation.selectedItemId, itemColors)
        
        // Mettre à jour les couleurs quand l'élément sélectionné change
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            updateNavigationItemColors(item.itemId, itemColors)
            
            // Animation de l'icône au clic
            animateIconSelection(item.itemId)
            
            // Navigation vers la destination
            when (item.itemId) {
                R.id.navigation_overview -> {
                    switchToOverview()
                    true
                }
                R.id.navigation_anecoop -> {
                    switchToSupplier("anecoop")
                    true
                }
                R.id.navigation_solagora -> {
                    switchToSupplier("solagora")
                    true
                }
                R.id.navigation_search -> {
                    triggerSearch()
                    false // Ne pas sélectionner l'item recherche
                }
                else -> false
            }
        }
    }
    
    private fun updateNavigationItemColors(selectedItemId: Int, itemColors: Map<Int, android.content.res.ColorStateList?>) {
        val menu = binding.bottomNavigation.menu
        
        for (i in 0 until menu.size()) {
            val menuItem = menu.getItem(i)
            val colorStateList = itemColors[menuItem.itemId]
            
            if (menuItem.itemId == selectedItemId && colorStateList != null) {
                // Appliquer la couleur spécifique pour l'item sélectionné
                binding.bottomNavigation.itemIconTintList = colorStateList
                binding.bottomNavigation.itemTextColor = colorStateList
                break
            }
        }
    }
    
    private fun animateIconSelection(itemId: Int) {
        // Trouver la vue de l'icône sélectionnée et appliquer un effet subtil de mise en avant
        binding.bottomNavigation.findViewById<View>(itemId)?.let { view ->
            // Animation subtile : légère élévation sans rotation
            view.animate()
                .scaleX(1.1f)      // Très légère augmentation de taille
                .scaleY(1.1f)
                .translationZ(4f)  // Légère élévation
                .setDuration(150)  // Animation rapide
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    // Retour immédiat à la taille normale
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }
    
    private fun updateNavigationVisibility() {
        val menu = binding.bottomNavigation.menu
        
        // S'assurer que le bouton overview est toujours visible
        val overviewItem = menu.findItem(R.id.navigation_overview)
        overviewItem?.isVisible = true
        
        // Cacher/afficher les éléments de navigation selon les préférences
        menu.findItem(R.id.navigation_anecoop)?.isVisible = supplierPreferences.isAnecoopEnabled
        menu.findItem(R.id.navigation_solagora)?.isVisible = supplierPreferences.isSolagoraEnabled
        
        
        // Forcer le refresh de la navigation
        binding.bottomNavigation.invalidate()
    }
    
    private fun showSearchButton() {
        // Afficher le bouton recherche dans la barre de navigation
        val searchMenuItem = binding.bottomNavigation.menu.findItem(R.id.navigation_search)
        searchMenuItem?.isVisible = true
    }
    
    private fun hideSearchButton() {
        // Masquer le bouton recherche dans la barre de navigation
        val searchMenuItem = binding.bottomNavigation.menu.findItem(R.id.navigation_search)
        searchMenuItem?.isVisible = false
    }
    
    private fun animateSearchButtonFromRight() {
        // Fonction vide - le bouton recherche dynamique a été supprimé
    }
    
    private fun animateSearchButtonToRight(onComplete: () -> Unit) {
        // Fonction vide - le bouton recherche dynamique a été supprimé
        onComplete()
    }
    
    private fun switchToOverview() {
        // Masquer le bouton recherche avec animation
        hideSearchButton()
        
        // Create and show OverviewFragment
        val overviewFragment = OverviewFragment()
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, overviewFragment)
            .commit()
            
        // Hide toolbar for overview
        supportActionBar?.hide()
        
        // Apply default theme
        applySupplierTheme("all")
        
        // Update menu visibility (hide search)
        invalidateOptionsMenu()
    }
    
    private fun switchToSupplier(supplier: String) {
        currentSupplier = supplier
        
        // Précharger l'autre fournisseur en arrière-plan
        preloadOtherSupplier(supplier)
        
        // Create and show ScamarkFragment with supplier parameter
        val scamarkFragment = ScamarkFragment().apply {
            arguments = Bundle().apply {
                putString("supplier", supplier)
                
                // Passer le filtre s'il y en a un dans le cache
                val filter = preloadedFilters[supplier]
                if (filter != null) {
                    putString("filter", filter)
                    // NE PAS supprimer le filtre ici - le Fragment le supprimera après utilisation
                } else {
                    // Pas de filtre = navigation normale, nettoyer les données préchargées filtrées
                    if (preloadedData.containsKey(supplier)) {
                        preloadedData.remove(supplier)
                    }
                }
                
                // Note: Les données préchargées seront chargées directement dans le ViewModel
            }
        }
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, scamarkFragment)
            .setReorderingAllowed(true) // OPTIMISATION: Allow state loss pour performance
            .commit() // OPTIMISATION: commit() asynchrone au lieu de commitNow() synchrone
        
        // Show toolbar with no title
        supportActionBar?.show()
        supportActionBar?.title = ""
        
        // Apply supplier theme
        applySupplierTheme(supplier)
        
        // Afficher le bouton recherche avec animation
        showSearchButton()
        
        // Update menu visibility (show search)
        invalidateOptionsMenu()
    }
    
    private fun triggerSearch() {
        
        // Obtenir le fragment actuel
        val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        
        when (currentFragment) {
            is ScamarkFragment -> {
                // On est sur une page fournisseur, activer le mode recherche
                currentFragment.toggleSearchMode()
            }
            is OverviewFragment -> {
                // On est sur la page overview, activer la recherche globale
                currentFragment.toggleSearchMode()
            }
        }
    }
    
    
    /**
     * Stocke les données préchargées pour un fournisseur spécifique
     */
    fun setPreloadedData(
        supplier: String, 
        products: List<com.nextjsclient.android.data.models.ScamarkProduct>,
        weeks: List<com.nextjsclient.android.data.models.AvailableWeek>
    ) {
        preloadedData[supplier] = Pair(products, weeks)
        // Supprimer le filtre existant pour ce fournisseur lors de la mise à jour
        preloadedFilters.remove(supplier)
    }
    
    /**
     * Stocke les données préchargées avec un filtre à appliquer
     */
    fun setPreloadedDataWithFilter(
        supplier: String, 
        products: List<com.nextjsclient.android.data.models.ScamarkProduct>,
        weeks: List<com.nextjsclient.android.data.models.AvailableWeek>,
        filter: String
    ) {
        preloadedData[supplier] = Pair(products, weeks)
        preloadedFilters[supplier] = filter
    }
    
    /**
     * Vérifie s'il y a des données préchargées pour un fournisseur
     */
    fun hasPreloadedDataFor(supplier: String): Boolean {
        return preloadedData.containsKey(supplier)
    }
    
    /**
     * Stocke seulement un filtre (sans données)
     */
    fun setFilterOnly(supplier: String, filter: String) {
        preloadedFilters[supplier] = filter
    }
    
    /**
     * Stocke la semaine sélectionnée pour la navigation depuis l'aperçu
     */
    fun setSelectedWeekForNavigation(year: Int, week: Int) {
        navigationYear = year
        navigationWeek = week
    }
    
    /**
     * Récupère la semaine sélectionnée pour la navigation
     */
    fun getSelectedWeekForNavigation(): Pair<Int, Int>? {
        return if (navigationYear != null && navigationWeek != null) {
            Pair(navigationYear!!, navigationWeek!!)
        } else null
    }
    
    /**
     * Nettoie le cache des données préchargées (utilisé lors du refresh)
     */
    fun clearPreloadedCache() {
        preloadedData.clear()
        preloadedFilters.clear()
        navigationYear = null
        navigationWeek = null
    }
    
    /**
     * Méthode publique pour marquer une navigation interne (utilisée par les fragments)
     */
    fun markInternalNavigation() {
        isInternalNavigation = true
    }
    
    /**
     * Charge les données préchargées directement dans le ViewModel
     */
    fun loadPreloadedDataToViewModel(supplier: String, viewModel: com.nextjsclient.android.ui.scamark.ScamarkViewModel) {
        preloadedData[supplier]?.let { (products, weeks) ->
            val filter = preloadedFilters[supplier]
            
            if (filter != null) {
                // IMPORTANT: Appliquer le filtre AVANT de charger les données
                viewModel.setProductFilter(filter)
                viewModel.setPreloadedData(supplier, products, weeks)
                
                // MAINTENANT supprimer le filtre après utilisation
                preloadedFilters.remove(supplier)
            } else {
                viewModel.setPreloadedData(supplier, products, weeks)
            }
        }
    }
    
    private fun applySupplierTheme(supplier: String) {
        // Apply background theme to root view
        supplierThemeManager.applySupplierBackground(supplier, binding.root)
        
        // Apply theme to bottom navigation
        binding.bottomNavigation.setBackgroundColor(
            supplierThemeManager.getSupplierSurfaceColor(supplier)
        )
        
        // Pas de couleur pour la barre d'état - garder le thème par défaut
        // window.statusBarColor = supplierThemeManager.getSupplierPrimaryColor(supplier)
    }
    
    private fun configureStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ : Utiliser la nouvelle API WindowInsetsController
            window.setDecorFitsSystemWindows(false)
            window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
            
            // Adapter les icônes selon le thème (jour/nuit)
            val nightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isNightMode = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            if (isNightMode) {
                // Thème sombre : icônes claires
                window.insetsController?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                // Thème clair : icônes sombres  
                window.insetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // No menu items in toolbar
        return false
    }
    
    
    
    
    private fun checkBiometricAuthentication() {
        // Éviter d'afficher plusieurs prompts biométriques
        if (isBiometricPromptShown) {
            return
        }
        
        // Vérifier si la biométrie est activée dans l'app
        if (!biometricManager.isBiometricEnabledInApp()) {
            removeLockOverlay()
            return
        }
        
        // Afficher l'overlay de verrouillage pour masquer les données
        showLockOverlay()
        
        isBiometricPromptShown = true
        
        // Petit délai pour s'assurer que l'overlay est affiché
        binding.root.postDelayed({
            // Utiliser la nouvelle méthode avec fallback automatique face -> fingerprint
            biometricManager.authenticateWithFallback(
                activity = this,
                onSuccess = {
                    // Authentification réussie, retirer l'overlay
                    isBiometricPromptShown = false
                    removeLockOverlay()
                },
                onError = { error ->
                    // Erreur d'authentification
                    isBiometricPromptShown = false
                    if (error.contains("dépassé") || error.contains("verrouillé")) {
                        // Trop d'échecs, déconnecter l'utilisateur pour sécurité
                        signOut()
                    } else {
                        // Autre erreur, garder l'overlay et permettre de réessayer
                        updateLockOverlayError(error)
                    }
                },
                onCancel = {
                    // L'utilisateur a annulé, fermer l'app
                    isBiometricPromptShown = false
                    finish()
                }
            )
        }, 100)
    }
    
    private fun showLockOverlay() {
        if (biometricLockOverlay == null) {
            // Créer l'overlay
            val inflater = LayoutInflater.from(this)
            biometricLockOverlay = inflater.inflate(R.layout.overlay_biometric_lock, null)
            
            // Configurer le bouton de déverrouillage
            biometricLockOverlay?.findViewById<View>(R.id.unlockButton)?.setOnClickListener {
                isBiometricPromptShown = false
                checkBiometricAuthentication()
            }
            
            // Ajouter l'overlay à la vue racine
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(biometricLockOverlay)
        }
        
        // S'assurer que l'overlay est visible
        biometricLockOverlay?.visibility = View.VISIBLE
    }
    
    private fun removeLockOverlay() {
        biometricLockOverlay?.let {
            it.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    it.visibility = View.GONE
                    val rootView = findViewById<ViewGroup>(android.R.id.content)
                    rootView.removeView(it)
                    biometricLockOverlay = null
                }
                .start()
        }
    }
    
    private fun updateLockOverlayError(error: String) {
        biometricLockOverlay?.findViewById<View>(R.id.lockSubtitle)?.let { subtitle ->
            if (subtitle is android.widget.TextView) {
                subtitle.text = "Erreur: $error\nAppuyez pour réessayer"
                subtitle.visibility = android.view.View.VISIBLE
            }
        }
    }
    
    private fun signOut() {
        auth.signOut()
        startActivity(Intent(this, AuthActivity::class.java))
        finish()
    }
    
    // Removed navigation up method since we don't need back navigation
}