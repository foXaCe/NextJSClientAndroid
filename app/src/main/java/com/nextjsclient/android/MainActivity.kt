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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var navController: NavController? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var themeManager: ThemeManager
    private lateinit var supplierThemeManager: SupplierThemeManager
    private lateinit var supplierPreferences: SupplierPreferences
    private lateinit var biometricManager: BiometricManager
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
            android.util.Log.w("MainActivity", "Navigation setup failed, using manual fragment management", e)
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
        
        // Vérifier l'authentification biométrique si activée (première fois seulement)
        if (savedInstanceState == null) {
            checkBiometricAuthentication()
        }
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
        
        // Configuration Material 3 expressive pour la navigation
        binding.bottomNavigation.apply {
            setOnItemSelectedListener { item ->
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
        
        android.util.Log.d("MainActivity", "🧭 Navigation visibility updated:")
        android.util.Log.d("MainActivity", "   - Overview: visible=${overviewItem?.isVisible}")
        android.util.Log.d("MainActivity", "   - Anecoop: visible=${menu.findItem(R.id.navigation_anecoop)?.isVisible}")
        android.util.Log.d("MainActivity", "   - Solagora: visible=${menu.findItem(R.id.navigation_solagora)?.isVisible}")
        
        // Forcer le refresh de la navigation
        binding.bottomNavigation.invalidate()
    }
    
    private fun showSearchButton() {
        // Fonction vide - le bouton recherche dynamique a été supprimé
    }
    
    private fun hideSearchButton() {
        // Fonction vide - le bouton recherche dynamique a été supprimé
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
        android.util.Log.d("MainActivity", "🔄 switchToSupplier DÉBUT - supplier: $supplier, currentSupplier: $currentSupplier")
        val startTime = System.currentTimeMillis()
        
        currentSupplier = supplier
        
        // Create and show ScamarkFragment with supplier parameter
        val scamarkFragment = ScamarkFragment().apply {
            arguments = Bundle().apply {
                putString("supplier", supplier)
                
                // Passer le filtre s'il y en a un dans le cache
                val filter = preloadedFilters[supplier]
                if (filter != null) {
                    android.util.Log.d("MainActivity", "📝 Ajout du filtre '$filter' aux arguments du fragment")
                    putString("filter", filter)
                }
                
                // Note: Les données préchargées seront chargées directement dans le ViewModel
            }
        }
        
        android.util.Log.d("MainActivity", "📝 Fragment créé avec arguments supplier: $supplier")
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, scamarkFragment)
            .commitNow()
        
        val endTime = System.currentTimeMillis()
        android.util.Log.d("MainActivity", "✅ switchToSupplier TERMINÉ - Durée: ${endTime - startTime}ms")
        
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
        android.util.Log.d("MainActivity", "🔍 Bouton recherche cliqué")
        
        // Obtenir le fragment actuel
        val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        
        when (currentFragment) {
            is ScamarkFragment -> {
                // On est sur une page fournisseur, activer le mode recherche
                android.util.Log.d("MainActivity", "🔍 Activation du mode recherche pour: $currentSupplier")
                currentFragment.toggleSearchMode()
            }
            is OverviewFragment -> {
                // On est sur la page overview, activer la recherche globale
                android.util.Log.d("MainActivity", "🔍 Activation de la recherche sur l'aperçu")
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
        android.util.Log.d("MainActivity", "💾 Stockage/Mise à jour des données préchargées pour $supplier: ${products.size} produits, ${weeks.size} semaines")
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
        android.util.Log.d("MainActivity", "🟡🟡🟡 DÉBUT setPreloadedDataWithFilter")
        android.util.Log.d("MainActivity", "   • Supplier: $supplier")
        android.util.Log.d("MainActivity", "   • Filter: $filter")
        android.util.Log.d("MainActivity", "   • Products: ${products.size}")
        android.util.Log.d("MainActivity", "   • Weeks: ${weeks.size}")
        
        products.take(3).forEach { product ->
            android.util.Log.d("MainActivity", "   • Produit préchargé: ${product.productName}")
        }
        
        preloadedData[supplier] = Pair(products, weeks)
        preloadedFilters[supplier] = filter
        
        android.util.Log.d("MainActivity", "✅ Données stockées dans le cache")
        android.util.Log.d("MainActivity", "   • preloadedData[$supplier] = ${preloadedData[supplier]?.first?.size} produits")
        android.util.Log.d("MainActivity", "   • preloadedFilters[$supplier] = ${preloadedFilters[supplier]}")
        android.util.Log.d("MainActivity", "🟡🟡🟡 FIN setPreloadedDataWithFilter")
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
        android.util.Log.d("MainActivity", "🎯 Stockage du filtre seulement: $supplier -> $filter")
        preloadedFilters[supplier] = filter
    }
    
    /**
     * Nettoie le cache des données préchargées (utilisé lors du refresh)
     */
    fun clearPreloadedCache() {
        android.util.Log.d("MainActivity", "🧹 Nettoyage du cache des données préchargées")
        android.util.Log.d("MainActivity", "   • Cache avant: ${preloadedData.size} fournisseurs")
        android.util.Log.d("MainActivity", "   • Filtres avant: ${preloadedFilters.size} filtres")
        
        preloadedData.clear()
        preloadedFilters.clear()
        
        android.util.Log.d("MainActivity", "✅ Cache nettoyé")
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
        android.util.Log.d("MainActivity", "🟢🟢🟢 DÉBUT loadPreloadedDataToViewModel")
        android.util.Log.d("MainActivity", "   • Supplier demandé: $supplier")
        android.util.Log.d("MainActivity", "   • Données en cache: ${if (preloadedData[supplier] != null) "OUI" else "NON"}")
        
        preloadedData[supplier]?.let { (products, weeks) ->
            val filter = preloadedFilters[supplier]
            android.util.Log.d("MainActivity", "📦 Données trouvées dans le cache:")
            android.util.Log.d("MainActivity", "   • Products: ${products.size}")
            android.util.Log.d("MainActivity", "   • Weeks: ${weeks.size}")
            android.util.Log.d("MainActivity", "   • Filter: $filter")
            
            products.take(3).forEach { product ->
                android.util.Log.d("MainActivity", "   • Produit à charger: ${product.productName}")
            }
            
            if (filter != null) {
                android.util.Log.d("MainActivity", "🎯 Application du filtre '$filter' AVANT setPreloadedData")
                // IMPORTANT: Appliquer le filtre AVANT de charger les données
                viewModel.setProductFilter(filter)
                android.util.Log.d("MainActivity", "✅ Filtre appliqué, maintenant chargement des données")
                viewModel.setPreloadedData(supplier, products, weeks)
            } else {
                android.util.Log.d("MainActivity", "⚡ Chargement sans filtre")
                viewModel.setPreloadedData(supplier, products, weeks)
            }
            
            android.util.Log.d("MainActivity", "💾 Données conservées en cache pour navigations futures")
        } ?: run {
            android.util.Log.e("MainActivity", "❌ AUCUNE donnée préchargée trouvée pour $supplier!")
        }
        
        android.util.Log.d("MainActivity", "🟢🟢🟢 FIN loadPreloadedDataToViewModel")
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
            
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // API ancienne pour compatibilité
            val transparentColor = ContextCompat.getColor(this, android.R.color.transparent)
            window.statusBarColor = transparentColor
            
            // Edge-to-edge
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or 
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Adapter les icônes selon le thème
                val nightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                val isNightMode = nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
                
                if (!isNightMode) {
                    // Thème clair : icônes sombres
                    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or 
                        View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
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