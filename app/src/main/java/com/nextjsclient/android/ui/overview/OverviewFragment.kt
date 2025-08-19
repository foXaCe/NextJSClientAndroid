package com.nextjsclient.android.ui.overview

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewModelScope
import com.google.android.material.snackbar.Snackbar
import com.nextjsclient.android.R
import com.nextjsclient.android.SettingsActivity
import com.nextjsclient.android.databinding.FragmentOverviewBinding
import com.nextjsclient.android.ui.scamark.ScamarkViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.nextjsclient.android.utils.CountUpAnimator
import com.nextjsclient.android.data.models.ScamarkStats

class OverviewFragment : Fragment() {
    
    private var _binding: FragmentOverviewBinding? = null
    val binding get() = _binding!!
    
    private val viewModel: ScamarkViewModel by activityViewModels()
    
    // Helper pour l'interface moderne
    private lateinit var modernHelper: ModernOverviewHelper
    
    // Stocker les produits de la semaine précédente pour calcul entrants/sortants
    private var previousWeekProducts: List<com.nextjsclient.android.data.models.ScamarkProduct> = emptyList()
    
    // Cache des données par fournisseur pour navigation instantanée
    private var preloadedAnecoopProducts: List<com.nextjsclient.android.data.models.ScamarkProduct> = emptyList()
    private var preloadedSolagoraProducts: List<com.nextjsclient.android.data.models.ScamarkProduct> = emptyList()
    private var preloadedAnecoopWeeks: List<com.nextjsclient.android.data.models.AvailableWeek> = emptyList()
    private var preloadedSolagoraWeeks: List<com.nextjsclient.android.data.models.AvailableWeek> = emptyList()
    
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOverviewBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        android.util.Log.d("OverviewFragment", "🚀 OverviewFragment.onViewCreated() DÉBUT")
        
        // Initialiser le helper moderne
        modernHelper = ModernOverviewHelper(this)
        
        setupButtons()
        setupSwipeRefresh()
        observeViewModel()
        
        // Vérifier s'il y a une semaine déjà sélectionnée
        val selectedYear = viewModel.selectedYear.value
        val selectedWeek = viewModel.selectedWeek.value
        
        android.util.Log.d("OverviewFragment", "🔍 État du ViewModel au démarrage:")
        android.util.Log.d("OverviewFragment", "   • selectedYear: $selectedYear")
        android.util.Log.d("OverviewFragment", "   • selectedWeek: $selectedWeek")
        
        if (selectedYear != null && selectedWeek != null) {
            // PRIORITÉ: Utiliser la semaine déjà sélectionnée dans le ViewModel
            android.util.Log.d("OverviewFragment", "📅 ✅ Utilisation de la semaine déjà sélectionnée: $selectedYear-$selectedWeek")
            loadDataForWeek(selectedYear, selectedWeek)
        } else {
            // Fallback seulement si aucune semaine n'est vraiment sélectionnée
            val calendar = java.util.Calendar.getInstance()
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            val currentWeek = getCurrentISOWeek()
            android.util.Log.d("OverviewFragment", "📅 ⚠️ FALLBACK: Aucune semaine sélectionnée, initialisation avec la semaine actuelle: $currentYear-$currentWeek")
            viewModel.selectWeek(currentYear, currentWeek)
            loadDataForWeek(currentYear, currentWeek)
        }
        
        android.util.Log.d("OverviewFragment", "✅ OverviewFragment.onViewCreated() FIN")
    }
    
    private fun loadInitialData() {
        android.util.Log.d("OverviewFragment", "📊 loadInitialData() DÉBUT")
        
        // Utiliser la semaine sélectionnée dans le ViewModel
        val selectedYear = viewModel.selectedYear.value
        val selectedWeek = viewModel.selectedWeek.value
        
        // Si aucune semaine n'est sélectionnée, utiliser la semaine actuelle comme fallback
        val (targetYear, targetWeek) = if (selectedYear != null && selectedWeek != null) {
            Pair(selectedYear, selectedWeek)
        } else {
            val calendar = java.util.Calendar.getInstance()
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            val currentWeek = getCurrentISOWeek()
            Pair(currentYear, currentWeek)
        }
        
        android.util.Log.d("OverviewFragment", "📅 Chargement des données pour: $targetYear-$targetWeek")
        
        viewModel.viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val repository = com.nextjsclient.android.data.repository.FirebaseRepository()
                
                // Charger les données en parallèle avec coroutines
                val currentProductsDeferred = async {
                    android.util.Log.d("OverviewFragment", "🔄 Chargement semaine sélectionnée: $targetYear-$targetWeek")
                    repository.getWeekDecisions(targetYear, targetWeek, "all")
                }
                
                val (previousYear, previousWeek) = getPreviousWeek(targetYear, targetWeek)
                val previousProductsDeferred = async {
                    android.util.Log.d("OverviewFragment", "🔄 Chargement semaine précédente: $previousYear-$previousWeek")
                    repository.getWeekDecisions(previousYear, previousWeek, "all")
                }
                
                // Précharger les données des fournisseurs en parallèle
                val anecoopDeferred = async {
                    repository.getWeekDecisions(targetYear, targetWeek, "anecoop")
                }
                
                val solagoraDeferred = async {
                    repository.getWeekDecisions(targetYear, targetWeek, "solagora")
                }
                
                val weeksAnecoopDeferred = async {
                    repository.getAvailableWeeks("anecoop")
                }
                
                val weeksSolagoraDeferred = async {
                    repository.getAvailableWeeks("solagora")
                }
                
                // Attendre tous les résultats
                val currentProducts = currentProductsDeferred.await()
                previousWeekProducts = previousProductsDeferred.await()
                preloadedAnecoopProducts = anecoopDeferred.await()
                preloadedSolagoraProducts = solagoraDeferred.await()
                preloadedAnecoopWeeks = weeksAnecoopDeferred.await()
                preloadedSolagoraWeeks = weeksSolagoraDeferred.await()
                
                val loadTime = System.currentTimeMillis() - startTime
                android.util.Log.d("OverviewFragment", "⚡ Chargement parallèle terminé en ${loadTime}ms")
                android.util.Log.d("OverviewFragment", "   • Semaine courante: ${currentProducts.size} produits")
                android.util.Log.d("OverviewFragment", "   • Semaine précédente: ${previousWeekProducts.size} produits")
                android.util.Log.d("OverviewFragment", "   • Anecoop préchargé: ${preloadedAnecoopProducts.size} produits")
                android.util.Log.d("OverviewFragment", "   • Solagora préchargé: ${preloadedSolagoraProducts.size} produits")
                
                // Mettre à jour les UI sur le thread principal
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        android.util.Log.d("OverviewFragment", "🔥 EXÉCUTION viewModel.setProducts sur UI thread")
                        // Mettre à jour les produits dans le ViewModel
                        viewModel.setProducts(currentProducts)
                        
                        // Maintenant que previousWeekProducts est chargé, recalculer les stats correctement
                        android.util.Log.d("OverviewFragment", "🔄 Recalcul des stats avec données de semaine précédente")
                        calculateAndDisplayStats(currentProducts)
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("OverviewFragment", "❌ Erreur loadInitialData: ${e.message}", e)
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            "Erreur lors du chargement des données: ${e.message}",
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    
    /**
     * Charge les données pour une semaine spécifique (utilisé quand on vient d'une page fournisseur)
     */
    private fun loadDataForWeek(year: Int, week: Int) {
        val timestamp = System.currentTimeMillis()
        android.util.Log.d("OverviewFragment", "📊 APPEL loadDataForWeek() pour $year-$week - Time: $timestamp")
        
        viewModel.viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val repository = com.nextjsclient.android.data.repository.FirebaseRepository()
                
                // Charger les données de la semaine demandée
                val weekProducts = repository.getWeekDecisions(year, week, "all")
                
                // Charger la semaine précédente pour calculer entrants/sortants
                val (previousYear, previousWeek) = getPreviousWeek(year, week)
                previousWeekProducts = repository.getWeekDecisions(previousYear, previousWeek, "all")
                
                val loadTime = System.currentTimeMillis() - startTime
                android.util.Log.d("OverviewFragment", "⚡ Données de la semaine $year-$week chargées en ${loadTime}ms")
                android.util.Log.d("OverviewFragment", "   • Semaine $year-$week: ${weekProducts.size} produits")
                android.util.Log.d("OverviewFragment", "   • Semaine précédente: ${previousWeekProducts.size} produits")
                
                // Mettre à jour l'UI
                android.util.Log.d("OverviewFragment", "📤 APPEL viewModel.setProducts avec ${weekProducts.size} produits")
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        android.util.Log.d("OverviewFragment", "🔥 EXÉCUTION viewModel.setProducts sur UI thread")
                        viewModel.setProducts(weekProducts)
                        
                        // Maintenant que previousWeekProducts est chargé, recalculer les stats correctement
                        android.util.Log.d("OverviewFragment", "🔄 Recalcul des stats avec données de semaine précédente")
                        calculateAndDisplayStats(weekProducts)
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("OverviewFragment", "❌ Erreur loadDataForWeek: ${e.message}", e)
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        com.google.android.material.snackbar.Snackbar.make(
                            binding.root,
                            "Erreur lors du chargement de la semaine: ${e.message}",
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }
    
    private fun setupButtons() {
        // Settings button
        binding.settingsButton.setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            // Configurer les couleurs du loader
            setColorSchemeResources(
                R.color.md_theme_light_primary,
                R.color.md_theme_light_secondary,
                R.color.md_theme_light_tertiary
            )
            
            // Action de refresh
            setOnRefreshListener {
                viewModel.refresh()
            }
        }
    }
    
    private fun observeViewModel() {
        android.util.Log.d("OverviewFragment", "🔧 SETUP observer viewModel.products")
        
        var lastProcessedProducts: List<com.nextjsclient.android.data.models.ScamarkProduct>? = null
        
        viewModel.products.observe(viewLifecycleOwner) { products ->
            val timestamp = System.currentTimeMillis()
            
            android.util.Log.d("OverviewFragment", "📊 OBSERVER DÉCLENCHÉ - Timestamp: $timestamp")
            android.util.Log.d("OverviewFragment", "📊 Products observés: ${products.size} produits totaux")
            
            // Vérifier si c'est exactement la même liste que la dernière fois
            if (products == lastProcessedProducts) {
                android.util.Log.d("OverviewFragment", "⏭️ IGNORÉ - Liste identique à la précédente")
                return@observe
            }
            
            android.util.Log.d("OverviewFragment", "🔥 NOUVELLE LISTE - Traitement requis")
            lastProcessedProducts = products
            
            products.groupBy { it.supplier.lowercase() }.forEach { (supplier, prods) ->
                android.util.Log.d("OverviewFragment", "   • $supplier: ${prods.size} produits")
            }
            
            // Calculer et afficher les stats en une seule fois
            calculateAndDisplayStats(products)
        }
        
        // Observer les changements de semaine (pour affichage et chargement de données)
        var isInitialLoad = true
        viewModel.selectedWeek.observe(viewLifecycleOwner) { week ->
            viewModel.selectedYear.observe(viewLifecycleOwner) { year ->
                if (week != null && year != null) {
                    // Toujours mettre à jour l'affichage
                    updateWeekDisplay(year, week)
                    
                    if (!isInitialLoad) {
                        // Charger les données seulement si ce n'est pas le chargement initial
                        android.util.Log.d("OverviewFragment", "📅 Changement de semaine détecté: $year-$week")
                        loadDataForWeek(year, week)
                    } else {
                        android.util.Log.d("OverviewFragment", "📅 Semaine initiale détectée: $year-$week")
                        isInitialLoad = false
                    }
                }
            }
        }
        
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.loadingOverlay.visibility = View.GONE // Ne plus utiliser l'overlay de chargement
        }
        
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }
    
    
    private fun calculateAndDisplayStats(products: List<com.nextjsclient.android.data.models.ScamarkProduct>) {
        val startTime = System.currentTimeMillis()
        val stackTrace = Thread.currentThread().stackTrace.take(6).joinToString("\n") { "  $it" }
        
        android.util.Log.d("OverviewFragment", "🔄 DÉBUT calculateAndDisplayStats pour ${products.size} produits - Time: $startTime")
        android.util.Log.d("OverviewFragment", "🔄 Appelé depuis:\n$stackTrace")
        
        // Séparer par fournisseur
        val anecoopProducts = products.filter { it.supplier.lowercase() == "anecoop" }
        val solagoraProducts = products.filter { it.supplier.lowercase() == "solagora" }
        
        android.util.Log.d("OverviewFragment", "📊 Répartition: Anecoop=${anecoopProducts.size}, Solagora=${solagoraProducts.size}")
        
        // Séparer semaine précédente par fournisseur  
        val previousAnecoopProducts = previousWeekProducts.filter { it.supplier.lowercase() == "anecoop" }
        val previousSolagoraProducts = previousWeekProducts.filter { it.supplier.lowercase() == "solagora" }
        
        android.util.Log.d("OverviewFragment", "📊 Semaine précédente: Anecoop=${previousAnecoopProducts.size}, Solagora=${previousSolagoraProducts.size}")
        
        // Calculer les stats
        android.util.Log.d("OverviewFragment", "🧮 Calcul stats Anecoop...")
        val anecoopStats = calculateStatsForProducts(anecoopProducts, previousAnecoopProducts)
        
        android.util.Log.d("OverviewFragment", "🧮 Calcul stats Solagora...")
        val solagoraStats = calculateStatsForProducts(solagoraProducts, previousSolagoraProducts)
        
        android.util.Log.d("OverviewFragment", "✅ Stats calculées - Anecoop: $anecoopStats")
        android.util.Log.d("OverviewFragment", "✅ Stats calculées - Solagora: $solagoraStats")
        
        // Mettre à jour les dashboards simultanément
        android.util.Log.d("OverviewFragment", "🎨 DÉBUT animations dashboards")
        updateSupplierDashboard("anecoop", anecoopStats)
        updateSupplierDashboard("solagora", solagoraStats)
        
        val endTime = System.currentTimeMillis()
        android.util.Log.d("OverviewFragment", "✅ FIN calculateAndDisplayStats - Durée: ${endTime - startTime}ms")
    }
    
    private fun calculateStatsForProducts(
        products: List<com.nextjsclient.android.data.models.ScamarkProduct>,
        previousProducts: List<com.nextjsclient.android.data.models.ScamarkProduct> = emptyList()
    ): com.nextjsclient.android.data.models.ScamarkStats {
        val totalProducts = products.size
        val totalPromos = products.count { it.isPromo }
        val uniqueClients = products.flatMap { it.decisions.map { d -> d.codeClient } }.distinct().size
        
        // Calculer les produits entrants et sortants par rapport à la semaine précédente
        val (productsIn, productsOut) = calculateProductsInOut(products, previousProducts)
        
        return com.nextjsclient.android.data.models.ScamarkStats(
            totalProducts = totalProducts,
            activeClients = uniqueClients,
            productsIn = productsIn,
            productsOut = productsOut,  
            totalPromos = totalPromos
        )
    }
    
    /**
     * Calcule les produits entrants et sortants par rapport à la semaine précédente
     */
    private fun calculateProductsInOut(
        currentProducts: List<com.nextjsclient.android.data.models.ScamarkProduct>,
        previousProducts: List<com.nextjsclient.android.data.models.ScamarkProduct>
    ): Pair<Int, Int> {
        if (previousProducts.isEmpty()) {
            android.util.Log.d("OverviewFragment", "📊 Pas de données de semaine précédente, retour 0,0")
            return Pair(0, 0)
        }
        
        // Obtenir les codes produits de chaque semaine
        val currentProductCodes = currentProducts.map { it.productName }.toSet()
        val previousProductCodes = previousProducts.map { it.productName }.toSet()
        
        android.util.Log.d("OverviewFragment", "📋 Semaine actuelle: ${currentProductCodes.size} produits uniques")
        android.util.Log.d("OverviewFragment", "📋 Semaine précédente: ${previousProductCodes.size} produits uniques")
        
        // Produits entrants: présents cette semaine mais pas la semaine dernière
        val newProducts = currentProductCodes.subtract(previousProductCodes)
        val productsIn = newProducts.size
        
        // Produits sortants: présents la semaine dernière mais pas cette semaine
        val removedProducts = previousProductCodes.subtract(currentProductCodes)
        val productsOut = removedProducts.size
        
        if (productsIn > 0) {
            android.util.Log.d("OverviewFragment", "➕ ${productsIn} produits ENTRANTS:")
            newProducts.take(3).forEach { product ->
                android.util.Log.d("OverviewFragment", "   • $product")
            }
        }
        
        if (productsOut > 0) {
            android.util.Log.d("OverviewFragment", "➖ ${productsOut} produits SORTANTS:")
            removedProducts.take(3).forEach { product ->
                android.util.Log.d("OverviewFragment", "   • $product")
            }
        }
        
        return Pair(productsIn, productsOut)
    }
    
    /**
     * Charge les données de la semaine précédente pour calcul des entrants/sortants
     * NOTE: Cette méthode n'est plus utilisée car le chargement est fait en parallèle dans loadInitialData()
     */
    private fun loadPreviousWeekData(currentYear: Int, currentWeek: Int) {
        // Méthode conservée pour compatibilité mais ne fait plus rien
        return
        val (previousYear, previousWeek) = getPreviousWeek(currentYear, currentWeek)
        
        android.util.Log.d("OverviewFragment", "🎯 loadPreviousWeekData() DÉBUT - currentYear: $currentYear, currentWeek: $currentWeek")
        android.util.Log.d("OverviewFragment", "   → Semaine précédente calculée: $previousYear-$previousWeek")
        
        viewModel.viewModelScope.launch {
            try {
                android.util.Log.d("OverviewFragment", "🔄 Chargement semaine précédente: $previousYear-$previousWeek")
                
                // Utiliser le repository directement pour charger la semaine précédente
                val repository = com.nextjsclient.android.data.repository.FirebaseRepository()
                android.util.Log.d("OverviewFragment", "📦 Repository créé, appel getWeekDecisions($previousYear, $previousWeek, 'all')")
                
                previousWeekProducts = repository.getWeekDecisions(previousYear, previousWeek, "all")
                
                android.util.Log.d("OverviewFragment", "✅ Semaine précédente chargée: ${previousWeekProducts.size} produits totaux")
                previousWeekProducts.groupBy { it.supplier.lowercase() }.forEach { (supplier, prods) ->
                    android.util.Log.d("OverviewFragment", "   • Semaine précédente - $supplier: ${prods.size} produits")
                }
                
                // Recalculer les stats avec les nouvelles données
                viewModel.products.value?.let { currentProducts ->
                    android.util.Log.d("OverviewFragment", "🔄 Recalcul des stats avec ${currentProducts.size} produits actuels")
                    calculateAndDisplayStats(currentProducts)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("OverviewFragment", "❌ ERREUR loadPreviousWeekData: ${e.message}", e)
                e.printStackTrace()
                // Si impossible de charger, garder une liste vide
                previousWeekProducts = emptyList()
            }
        }
    }
    
    /**
     * Calcule la semaine précédente (gère le passage d'année)
     */
    private fun getPreviousWeek(year: Int, week: Int): Pair<Int, Int> {
        return if (week > 1) {
            Pair(year, week - 1)
        } else {
            Pair(year - 1, 52) // Dernière semaine de l'année précédente
        }
    }
    
    /**
     * Précharge les données des deux fournisseurs pour la semaine sélectionnée
     * NOTE: Cette méthode n'est plus utilisée car le chargement est fait en parallèle dans loadInitialData()
     */
    private fun preloadSupplierData(year: Int, week: Int) {
        // Méthode conservée pour compatibilité mais ne fait plus rien
        return
        viewModel.viewModelScope.launch {
            try {
                android.util.Log.d("OverviewFragment", "🔄 Préchargement des données fournisseurs pour $year-$week")
                val startTime = System.currentTimeMillis()
                
                val repository = com.nextjsclient.android.data.repository.FirebaseRepository()
                
                // Précharger en parallèle les données et semaines de chaque fournisseur
                val anecoopProductsDeferred = async { 
                    repository.getWeekDecisions(year, week, "anecoop")
                }
                val solagoraProductsDeferred = async { 
                    repository.getWeekDecisions(year, week, "solagora") 
                }
                val anecoopWeeksDeferred = async { 
                    repository.getAvailableWeeks("anecoop")
                }
                val solagoraWeeksDeferred = async { 
                    repository.getAvailableWeeks("solagora")
                }
                
                // Attendre tous les chargements
                preloadedAnecoopProducts = anecoopProductsDeferred.await()
                preloadedSolagoraProducts = solagoraProductsDeferred.await()
                preloadedAnecoopWeeks = anecoopWeeksDeferred.await()
                preloadedSolagoraWeeks = solagoraWeeksDeferred.await()
                
                val endTime = System.currentTimeMillis()
                android.util.Log.d("OverviewFragment", "✅ Préchargement terminé en ${endTime - startTime}ms:")
                android.util.Log.d("OverviewFragment", "   • Anecoop: ${preloadedAnecoopProducts.size} produits, ${preloadedAnecoopWeeks.size} semaines")
                android.util.Log.d("OverviewFragment", "   • Solagora: ${preloadedSolagoraProducts.size} produits, ${preloadedSolagoraWeeks.size} semaines")
                
            } catch (e: Exception) {
                android.util.Log.w("OverviewFragment", "⚠️ Erreur lors du préchargement: ${e.message}")
            }
        }
    }
    
    private fun updateSupplierDashboard(supplier: String, stats: ScamarkStats) {
        val timestamp = System.currentTimeMillis()
        android.util.Log.d("OverviewFragment", "🎨 DÉBUT updateSupplierDashboard $supplier - Time: $timestamp")
        android.util.Log.d("OverviewFragment", "🎨 Stats pour $supplier: $stats")
        
        // Déléguer à ModernOverviewHelper pour la nouvelle UI moderne
        val isAnecoop = supplier.lowercase() == "anecoop"
        modernHelper.updateSupplierCard(supplier, stats, isAnecoop)
        
        val endTimestamp = System.currentTimeMillis()
        android.util.Log.d("OverviewFragment", "✅ FIN updateSupplierDashboard $supplier - Time: $endTimestamp")
    }
    
    
    private fun getCurrentISOWeek(): Int {
        val calendar = java.util.Calendar.getInstance()
        val date = calendar.time
        
        calendar.time = date
        val dayOfWeek = (calendar.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        calendar.add(java.util.Calendar.DAY_OF_YEAR, -dayOfWeek + 3)
        val firstThursday = calendar.timeInMillis
        
        calendar.set(java.util.Calendar.MONTH, 0)
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        if (calendar.get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.THURSDAY) {
            val daysToAdd = (4 - calendar.get(java.util.Calendar.DAY_OF_WEEK) + 7) % 7
            calendar.add(java.util.Calendar.DAY_OF_YEAR, daysToAdd)
        }
        
        return 1 + ((firstThursday - calendar.timeInMillis) / (7 * 24 * 60 * 60 * 1000)).toInt()
    }
    
    
    private fun navigateToSupplier(supplier: String) {
        android.util.Log.d("OverviewFragment", "🔄 Navigation vers $supplier avec données préchargées")
        
        // Obtenir les données préchargées pour ce fournisseur
        val preloadedProducts = when (supplier) {
            "anecoop" -> preloadedAnecoopProducts
            "solagora" -> preloadedSolagoraProducts
            else -> emptyList()
        }
        
        val preloadedWeeks = when (supplier) {
            "anecoop" -> preloadedAnecoopWeeks
            "solagora" -> preloadedSolagoraWeeks
            else -> emptyList()
        }
        
        android.util.Log.d("OverviewFragment", "📦 Données disponibles: ${preloadedProducts.size} produits, ${preloadedWeeks.size} semaines")
        
        // Stocker les données préchargées dans un cache global accessible par MainActivity
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        mainActivity?.let { activity ->
            // Passer les données préchargées à l'activité
            activity.setPreloadedData(supplier, preloadedProducts, preloadedWeeks)
            
            // Naviguer vers le fournisseur
            val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.nextjsclient.android.R.id.bottom_navigation)
            bottomNav?.selectedItemId = when (supplier) {
                "anecoop" -> com.nextjsclient.android.R.id.navigation_anecoop
                "solagora" -> com.nextjsclient.android.R.id.navigation_solagora
                else -> com.nextjsclient.android.R.id.navigation_anecoop
            }
        }
    }
    
    private fun navigateToSupplierWithFilter(supplier: String, filter: String) {
        android.util.Log.d("OverviewFragment", "🔄 Navigation vers $supplier avec filtre: $filter")
        
        // Obtenir les données préchargées pour ce fournisseur
        val preloadedProducts = when (supplier) {
            "anecoop" -> preloadedAnecoopProducts
            "solagora" -> preloadedSolagoraProducts
            else -> emptyList()
        }
        
        val preloadedWeeks = when (supplier) {
            "anecoop" -> preloadedAnecoopWeeks
            "solagora" -> preloadedSolagoraWeeks
            else -> emptyList()
        }
        
        android.util.Log.d("OverviewFragment", "📊 Données disponibles pour $supplier: ${preloadedProducts.size} produits, ${preloadedWeeks.size} semaines")
        
        // Debug: afficher les premiers produits et leurs fournisseurs
        preloadedProducts.take(5).forEach { product ->
            android.util.Log.d("OverviewFragment", "   🔍 Produit préchargé: '${product.productName}' - Fournisseur: '${product.supplier}'")
        }
        
        // Stocker les données préchargées dans un cache global accessible par MainActivity
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        mainActivity?.let { activity ->
            // Si pas de données préchargées, ne pas mettre en cache - mais stocker le filtre quand même
            if (preloadedProducts.isNotEmpty()) {
                android.util.Log.d("OverviewFragment", "✅ Utilisation des données préchargées")
                activity.setPreloadedDataWithFilter(supplier, preloadedProducts, preloadedWeeks, filter)
            } else {
                android.util.Log.d("OverviewFragment", "⚠️ Pas de données préchargées - stockage du filtre seulement")
                // Stocker seulement le filtre, pas de données vides
                activity.setFilterOnly(supplier, filter)
            }
            
            // Naviguer vers le fournisseur
            val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.nextjsclient.android.R.id.bottom_navigation)
            bottomNav?.selectedItemId = when (supplier) {
                "anecoop" -> com.nextjsclient.android.R.id.navigation_anecoop
                "solagora" -> com.nextjsclient.android.R.id.navigation_solagora
                else -> com.nextjsclient.android.R.id.navigation_anecoop
            }
        }
    }
    
    private fun navigateToSupplierWithEntrantsFilter(supplier: String, filter: String) {
        android.util.Log.d("OverviewFragment", "🟢🟢🟢 DÉBUT navigateToSupplierWithEntrantsFilter")
        android.util.Log.d("OverviewFragment", "   • Supplier: $supplier")
        android.util.Log.d("OverviewFragment", "   • Filter: $filter")
        
        // Pour les produits entrants, on doit identifier ceux qui sont nouveaux cette semaine
        val currentSupplierProducts = when (supplier) {
            "anecoop" -> preloadedAnecoopProducts
            "solagora" -> preloadedSolagoraProducts
            else -> emptyList()
        }
        
        val previousWeekSupplierProducts = previousWeekProducts.filter { 
            it.supplier.lowercase() == supplier.lowercase() 
        }
        
        // Calculer les vrais entrants : présents cette semaine mais pas la semaine dernière
        val previousProductNames = previousWeekSupplierProducts.map { it.productName }.toSet()
        val entrantProducts = currentSupplierProducts.filter { 
            !previousProductNames.contains(it.productName)
        }
        
        android.util.Log.d("OverviewFragment", "📦 Calcul des entrants pour $supplier:")
        android.util.Log.d("OverviewFragment", "   • Produits semaine actuelle: ${currentSupplierProducts.size}")
        android.util.Log.d("OverviewFragment", "   • Produits semaine précédente: ${previousWeekSupplierProducts.size}")
        android.util.Log.d("OverviewFragment", "   • VRAIS ENTRANTS: ${entrantProducts.size}")
        entrantProducts.take(3).forEach { product ->
            android.util.Log.d("OverviewFragment", "   • Entrant: ${product.productName}")
        }
        
        val preloadedWeeks = when (supplier) {
            "anecoop" -> preloadedAnecoopWeeks
            "solagora" -> preloadedSolagoraWeeks
            else -> emptyList()
        }
        
        android.util.Log.d("OverviewFragment", "📅 Semaines disponibles: ${preloadedWeeks.size}")
        
        // Stocker les données dans un cache global accessible par MainActivity
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        if (mainActivity != null) {
            android.util.Log.d("OverviewFragment", "✅ MainActivity trouvée, appel de setPreloadedDataWithFilter")
            android.util.Log.d("OverviewFragment", "   • supplier: $supplier")
            android.util.Log.d("OverviewFragment", "   • ENTRANTS À PASSER: ${entrantProducts.size}")
            android.util.Log.d("OverviewFragment", "   • filter: $filter")
            
            // Passer SEULEMENT les vrais produits entrants avec le filtre "entrants"
            mainActivity.setPreloadedDataWithFilter(supplier, entrantProducts, preloadedWeeks, filter)
            
            android.util.Log.d("OverviewFragment", "🚀 Navigation vers le fournisseur $supplier")
            // Naviguer vers le fournisseur
            val bottomNav = mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.nextjsclient.android.R.id.bottom_navigation)
            bottomNav?.selectedItemId = when (supplier) {
                "anecoop" -> com.nextjsclient.android.R.id.navigation_anecoop
                "solagora" -> com.nextjsclient.android.R.id.navigation_solagora
                else -> com.nextjsclient.android.R.id.navigation_anecoop
            }
        } else {
            android.util.Log.e("OverviewFragment", "❌ MainActivity null!")
        }
        
        android.util.Log.d("OverviewFragment", "🟢🟢🟢 FIN navigateToSupplierWithEntrantsFilter")
    }
    
    private fun navigateToSupplierWithPreviousWeekFilter(supplier: String, filter: String) {
        android.util.Log.d("OverviewFragment", "🔴🔴🔴 DÉBUT navigateToSupplierWithPreviousWeekFilter")
        android.util.Log.d("OverviewFragment", "   • Supplier: $supplier")
        android.util.Log.d("OverviewFragment", "   • Filter: $filter")
        android.util.Log.d("OverviewFragment", "   • previousWeekProducts total: ${previousWeekProducts.size}")
        
        // Pour les produits sortants, on doit identifier ceux qui étaient là la semaine dernière mais plus maintenant
        val previousWeekSupplierProducts = previousWeekProducts.filter { 
            it.supplier.lowercase() == supplier.lowercase() 
        }
        
        // Produits actuels du fournisseur
        val currentSupplierProducts = when (supplier) {
            "anecoop" -> preloadedAnecoopProducts
            "solagora" -> preloadedSolagoraProducts
            else -> emptyList()
        }
        
        // Calculer les vrais sortants : présents la semaine dernière mais pas cette semaine
        val currentProductNames = currentSupplierProducts.map { it.productName }.toSet()
        val sortantProducts = previousWeekSupplierProducts.filter { 
            !currentProductNames.contains(it.productName)
        }
        
        android.util.Log.d("OverviewFragment", "📦 Calcul des sortants pour $supplier:")
        android.util.Log.d("OverviewFragment", "   • Produits semaine précédente: ${previousWeekSupplierProducts.size}")
        android.util.Log.d("OverviewFragment", "   • Produits semaine actuelle: ${currentSupplierProducts.size}")
        android.util.Log.d("OverviewFragment", "   • VRAIS SORTANTS: ${sortantProducts.size}")
        sortantProducts.take(3).forEach { product ->
            android.util.Log.d("OverviewFragment", "   • Sortant: ${product.productName}")
        }
        
        val preloadedWeeks = when (supplier) {
            "anecoop" -> preloadedAnecoopWeeks
            "solagora" -> preloadedSolagoraWeeks
            else -> emptyList()
        }
        
        android.util.Log.d("OverviewFragment", "📅 Semaines disponibles: ${preloadedWeeks.size}")
        
        // Stocker les données dans un cache global accessible par MainActivity
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        if (mainActivity != null) {
            android.util.Log.d("OverviewFragment", "✅ MainActivity trouvée, appel de setPreloadedDataWithFilter")
            android.util.Log.d("OverviewFragment", "   • supplier: $supplier")
            android.util.Log.d("OverviewFragment", "   • SORTANTS À PASSER: ${sortantProducts.size}")
            android.util.Log.d("OverviewFragment", "   • filter: $filter")
            
            // Passer SEULEMENT les vrais produits sortants avec le filtre "sortants"
            mainActivity.setPreloadedDataWithFilter(supplier, sortantProducts, preloadedWeeks, filter)
            
            android.util.Log.d("OverviewFragment", "🚀 Navigation vers le fournisseur $supplier")
            // Naviguer vers le fournisseur
            val bottomNav = mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.nextjsclient.android.R.id.bottom_navigation)
            bottomNav?.selectedItemId = when (supplier) {
                "anecoop" -> com.nextjsclient.android.R.id.navigation_anecoop
                "solagora" -> com.nextjsclient.android.R.id.navigation_solagora
                else -> com.nextjsclient.android.R.id.navigation_anecoop
            }
        } else {
            android.util.Log.e("OverviewFragment", "❌ MainActivity null!")
        }
        
        android.util.Log.d("OverviewFragment", "🔴🔴🔴 FIN navigateToSupplierWithPreviousWeekFilter")
    }
    
    /**
     * Navigation vers un fournisseur avec filtre promotions
     */
    private fun navigateToSupplierWithPromoFilter(supplier: String, filter: String) {
        android.util.Log.d("OverviewFragment", "🔥🔥🔥 DÉBUT navigateToSupplierWithPromoFilter")
        android.util.Log.d("OverviewFragment", "   • Supplier: $supplier")
        android.util.Log.d("OverviewFragment", "   • Filter: $filter")
        
        android.util.Log.d("OverviewFragment", "🔍 État des données préchargées:")
        android.util.Log.d("OverviewFragment", "   • preloadedAnecoopProducts: ${preloadedAnecoopProducts.size} produits")
        android.util.Log.d("OverviewFragment", "   • preloadedSolagoraProducts: ${preloadedSolagoraProducts.size} produits")
        
        // Produits actuels du fournisseur
        val currentSupplierProducts = when (supplier) {
            "anecoop" -> {
                android.util.Log.d("OverviewFragment", "📦 Sélection des produits Anecoop: ${preloadedAnecoopProducts.size}")
                preloadedAnecoopProducts
            }
            "solagora" -> {
                android.util.Log.d("OverviewFragment", "📦 Sélection des produits Solagora: ${preloadedSolagoraProducts.size}")
                preloadedSolagoraProducts
            }
            else -> {
                android.util.Log.d("OverviewFragment", "❌ Supplier non reconnu: $supplier")
                emptyList()
            }
        }
        
        // Vérifier que nous avons des données à traiter
        if (currentSupplierProducts.isEmpty()) {
            android.util.Log.e("OverviewFragment", "❌ ERREUR: Aucun produit trouvé pour $supplier!")
            android.util.Log.e("OverviewFragment", "❌ Les données ne sont peut-être pas encore chargées, utilisation de la méthode normale")
            // Si pas de données préchargées, utiliser la méthode normale qui charge les données
            navigateToSupplierWithFilter(supplier, filter)
            return
        }
        
        // Filtrer seulement les produits en promotion
        val promoProducts = currentSupplierProducts.filter { it.isPromo }
        
        android.util.Log.d("OverviewFragment", "📦 Calcul des promotions pour $supplier:")
        android.util.Log.d("OverviewFragment", "   • Produits totaux: ${currentSupplierProducts.size}")
        android.util.Log.d("OverviewFragment", "   • PROMOTIONS: ${promoProducts.size}")
        promoProducts.take(3).forEach { product ->
            android.util.Log.d("OverviewFragment", "   • Promo: ${product.productName}")
        }
        
        val preloadedWeeks = when (supplier) {
            "anecoop" -> preloadedAnecoopWeeks
            "solagora" -> preloadedSolagoraWeeks
            else -> emptyList()
        }
        
        android.util.Log.d("OverviewFragment", "📅 Semaines disponibles: ${preloadedWeeks.size}")
        
        // Stocker les données dans un cache global accessible par MainActivity
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        if (mainActivity != null) {
            android.util.Log.d("OverviewFragment", "✅ MainActivity trouvée, appel de setPreloadedDataWithFilter")
            android.util.Log.d("OverviewFragment", "   • supplier: $supplier")
            android.util.Log.d("OverviewFragment", "   • PROMOTIONS À PASSER: ${promoProducts.size}")
            android.util.Log.d("OverviewFragment", "   • filter: $filter")
            
            // Passer SEULEMENT les vrais produits en promotion avec le filtre "promo"
            mainActivity.setPreloadedDataWithFilter(supplier, promoProducts, preloadedWeeks, filter)
            
            android.util.Log.d("OverviewFragment", "🚀 Navigation vers le fournisseur $supplier")
            // Naviguer vers le fournisseur
            val bottomNav = mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.nextjsclient.android.R.id.bottom_navigation)
            bottomNav?.selectedItemId = when (supplier) {
                "anecoop" -> com.nextjsclient.android.R.id.navigation_anecoop
                "solagora" -> com.nextjsclient.android.R.id.navigation_solagora
                else -> com.nextjsclient.android.R.id.navigation_anecoop
            }
        } else {
            android.util.Log.e("OverviewFragment", "❌ MainActivity null!")
        }
        
        android.util.Log.d("OverviewFragment", "🔥🔥🔥 FIN navigateToSupplierWithPromoFilter")
    }
    
    /**
     * Met à jour l'affichage de la semaine en cours
     */
    private fun updateWeekDisplay(year: Int, week: Int) {
        val weekStr = week.toString().padStart(2, '0')
        val dates = formatWeekDatesOnly(year, week)
        val weekText = "S$weekStr - $dates"
        
        // Utiliser ModernOverviewHelper pour la nouvelle UI
        modernHelper.updateWeekDisplay(weekText)
    }
    
    /**
     * Formate les dates d'une semaine
     */
    private fun formatWeekDatesOnly(year: Int, week: Int): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.YEAR, year)
        calendar.set(java.util.Calendar.WEEK_OF_YEAR, week)
        calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        
        val weekStart = calendar.time
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 6)
        val weekEnd = calendar.time
        
        val dateFormat = java.text.SimpleDateFormat("dd/MM", java.util.Locale.FRENCH)
        
        return "${dateFormat.format(weekStart)} au ${dateFormat.format(weekEnd)}"
    }
    
    
    /**
     * Méthodes de navigation publiques pour ModernOverviewHelper
     */
    fun navigateToSupplierFromCard(supplier: String) {
        navigateToSupplierWithFilter(supplier, "all")
    }
    
    fun navigateToSupplierWithEntrantsFilterFromCard(supplier: String, filter: String) {
        navigateToSupplierWithEntrantsFilter(supplier, filter)
    }
    
    fun navigateToSupplierWithSortantsFilter(supplier: String, filter: String) {
        navigateToSupplierWithPreviousWeekFilter(supplier, filter)
    }
    
    fun navigateToSupplierWithPromoFilterFromCard(supplier: String, filter: String) {
        navigateToSupplierWithPromoFilter(supplier, filter)
    }
    
    fun navigateToSupplierWithoutFilter(supplier: String) {
        navigateToSupplier(supplier)
    }
    
    /**
     * Switch to a specific supplier (called from MainActivity)
     */
    fun switchSupplier(supplier: String) {
        viewModel.selectSupplier(supplier)
    }
    
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}