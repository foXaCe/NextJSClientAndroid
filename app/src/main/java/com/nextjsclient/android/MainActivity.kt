package com.nextjsclient.android

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
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
        
        setSupportActionBar(binding.toolbar)
        
        try {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            navController = navHostFragment?.navController
        } catch (e: Exception) {
            // En cas d'erreur de navigation, continuer sans navController
            android.util.Log.w("MainActivity", "Navigation setup failed, using manual fragment management", e)
            navController = null
        }
        
        // Configure toolbar title directly (no navigation arrows)
        supportActionBar?.title = "Scamark - Anecoop"
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        
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
        
        // Cacher/afficher les éléments de navigation selon les préférences
        menu.findItem(R.id.navigation_anecoop)?.isVisible = supplierPreferences.isAnecoopEnabled
        menu.findItem(R.id.navigation_solagora)?.isVisible = supplierPreferences.isSolagoraEnabled
        
        android.util.Log.d("MainActivity", "🧭 Navigation visibility updated - Anecoop: ${supplierPreferences.isAnecoopEnabled}, Solagora: ${supplierPreferences.isSolagoraEnabled}")
    }
    
    private fun switchToOverview() {
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
        
        // Show toolbar and update title
        supportActionBar?.show()
        supportActionBar?.title = when (supplier) {
            "anecoop" -> "Scamark - Anecoop"
            "solagora" -> "Scamark - Solagora"
            else -> "Scamark"
        }
        
        // Apply supplier theme
        applySupplierTheme(supplier)
        
        // Update menu visibility (show search)
        invalidateOptionsMenu()
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
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Add search and settings directly to toolbar
        menuInflater.inflate(R.menu.main_menu, menu)
        
        // Setup SearchView
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        
        searchView.queryHint = "Rechercher par SCA ou produit..."
        searchView.maxWidth = Integer.MAX_VALUE
        
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query)
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                performSearch(newText)
                return true
            }
        })
        
        // Hide search on Overview page
        updateMenuVisibility(menu)
        
        return true
    }
    
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        updateMenuVisibility(menu)
        return super.onPrepareOptionsMenu(menu)
    }
    
    private fun updateMenuVisibility(menu: Menu) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        val searchItem = menu.findItem(R.id.action_search)
        val settingsItem = menu.findItem(R.id.action_settings)
        
        when (currentFragment) {
            is OverviewFragment -> {
                // Hide search and settings on Overview page
                searchItem.isVisible = false
                settingsItem.isVisible = false
            }
            is ScamarkFragment -> {
                // Show search and settings on Scamark pages
                searchItem.isVisible = true
                settingsItem.isVisible = true
            }
            else -> {
                // Default: show search and settings
                searchItem.isVisible = true
                settingsItem.isVisible = true
            }
        }
    }
    
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                isInternalNavigation = true
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun performSearch(query: String?) {
        // Get the current fragment and perform search if it's ScamarkFragment
        val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        
        when (currentFragment) {
            is ScamarkFragment -> {
                currentFragment.performSearch(query)
            }
            is OverviewFragment -> {
                // Overview doesn't support search - maybe switch to Scamark with search?
                // For now, do nothing
            }
        }
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