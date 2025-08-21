package com.nextjsclient.android.ui.scamark

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextjsclient.android.data.models.*
import com.nextjsclient.android.data.repository.FirebaseRepository
import kotlinx.coroutines.launch
import java.util.*

enum class ProductStatus {
    ENTRANT,  // Produit nouveau cette semaine
    SORTANT,  // Produit qui était là la semaine précédente mais plus maintenant
    NEUTRAL   // Produit normal (présent les deux semaines ou aucune donnée précédente)
}

class ScamarkViewModel : ViewModel() {
    
    private val repository = FirebaseRepository()
    
    // États pour les données principales
    private val _products = MutableLiveData<List<ScamarkProduct>>()
    private val _allProducts = MutableLiveData<List<ScamarkProduct>>()
    val products: LiveData<List<ScamarkProduct>> = _products
    
    private val _stats = MutableLiveData<ScamarkStats>()
    val stats: LiveData<ScamarkStats> = _stats
    
    private val _availableWeeks = MutableLiveData<List<AvailableWeek>>()
    val availableWeeks: LiveData<List<AvailableWeek>> = _availableWeeks
    
    // États pour la sélection
    private val _selectedYear = MutableLiveData<Int>()
    val selectedYear: LiveData<Int> = _selectedYear
    
    private val _selectedWeek = MutableLiveData<Int>()
    val selectedWeek: LiveData<Int> = _selectedWeek
    
    private val _selectedSupplier = MutableLiveData<String>()
    val selectedSupplier: LiveData<String> = _selectedSupplier
    
    // États d'interface
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _isLoadingWeeks = MutableLiveData<Boolean>()
    val isLoadingWeeks: LiveData<Boolean> = _isLoadingWeeks
    
    private val _isLoadingMoreWeeks = MutableLiveData<Boolean>()
    val isLoadingMoreWeeks: LiveData<Boolean> = _isLoadingMoreWeeks
    
    private val _canLoadMoreWeeks = MutableLiveData<Boolean>()
    val canLoadMoreWeeks: LiveData<Boolean> = _canLoadMoreWeeks
    
    // Tracker la plus ancienne semaine chargée pour continuer à rebours
    private var oldestWeekLoaded: Int? = null
    
    // Produits de la semaine précédente pour comparaison entrants/sortants
    private var previousWeekProducts: List<ScamarkProduct> = emptyList()
    
    // Debounce pour éviter les appels multiples lors de navigation rapide
    private var loadWeekDataJob: kotlinx.coroutines.Job? = null
    
    // Produits sortants préchargés depuis l'aperçu
    private var preloadedSortants: List<ScamarkProduct>? = null
    
    // Produits entrants préchargés depuis l'aperçu
    private var preloadedEntrants: List<ScamarkProduct>? = null
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    // Filtres et recherche
    private val _searchQuery = MutableLiveData<String>()
    val searchQuery: LiveData<String> = _searchQuery
    
    private val _productFilter = MutableLiveData<String>()
    val productFilter: LiveData<String> = _productFilter
    
    init {
        // Initialiser avec la semaine courante
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        
        _selectedYear.value = currentYear
        _selectedWeek.value = currentWeek
        // Initialiser avec "all" par défaut
        _selectedSupplier.value = "all"
        _productFilter.value = "all"
        _searchQuery.value = ""
        _canLoadMoreWeeks.value = true
        
        
        // NE PAS charger automatiquement - le Fragment appellera selectSupplier()
        // loadAvailableWeeks("all")
        // loadWeekData()
    }
    
    /**
     * Charge les semaines disponibles
     */
    fun loadAvailableWeeks(supplier: String = _selectedSupplier.value ?: "all") {
        android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_LOAD_START: Début chargement semaines pour '$supplier'")
        val globalStart = System.currentTimeMillis()
        
        viewModelScope.launch {
            _isLoadingWeeks.value = true
            android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_LOADING_STATE: État loading activé")
            
            try {
                val repoStart = System.currentTimeMillis()
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_REPO_CALL: Appel repository.getAvailableWeeks('$supplier')")
                
                val weeks = repository.getAvailableWeeks(supplier)
                
                val repoEnd = System.currentTimeMillis()
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_REPO_DONE: Repository terminé en ${repoEnd - repoStart}ms - ${weeks.size} semaines")
                
                val processStart = System.currentTimeMillis()
                _availableWeeks.value = weeks
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_DATA_SET: Données assignées en ${System.currentTimeMillis() - processStart}ms")
                
                // Tracker la plus ancienne semaine chargée
                val trackStart = System.currentTimeMillis()
                oldestWeekLoaded = weeks.minByOrNull { it.week }?.week
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_TRACK: Tracking oldest week en ${System.currentTimeMillis() - trackStart}ms -> $oldestWeekLoaded")
                
                // Vérifier s'il peut y avoir plus de semaines
                val canLoadStart = System.currentTimeMillis()
                updateCanLoadMoreWeeks(weeks)
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_CAN_LOAD: updateCanLoadMoreWeeks en ${System.currentTimeMillis() - canLoadStart}ms")
                
                // Si aucune semaine sélectionnée, prendre la plus récente
                val selectStart = System.currentTimeMillis()
                if (_selectedYear.value == null || _selectedWeek.value == null) {
                    weeks.firstOrNull()?.let { firstWeek ->
                        android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_AUTO_SELECT: Auto-sélection semaine ${firstWeek.year}-${firstWeek.week}")
                        _selectedYear.value = firstWeek.year
                        _selectedWeek.value = firstWeek.week
                    }
                }
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_SELECT: Gestion sélection en ${System.currentTimeMillis() - selectStart}ms")
                
            } catch (e: Exception) {
                android.util.Log.e("ScamarkViewModel", "⏱️ WEEKS_ERROR: Erreur chargement semaines: ${e.message}")
                _error.value = "Erreur lors du chargement des semaines: ${e.message}"
            } finally {
                val finallyStart = System.currentTimeMillis()
                _isLoadingWeeks.value = false
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_LOADING_OFF: État loading désactivé en ${System.currentTimeMillis() - finallyStart}ms")
                
                val globalEnd = System.currentTimeMillis()
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_LOAD_END: TOTAL chargement semaines en ${globalEnd - globalStart}ms")
            }
        }
    }
    
    /**
     * Charge plus de semaines historiques
     */
    fun loadMoreWeeks() {
        val supplier = _selectedSupplier.value ?: "all"
        val currentWeeks = _availableWeeks.value ?: emptyList()
        
        android.util.Log.d("ScamarkViewModel", "🚀 loadMoreWeeks appelé - supplier: $supplier, semaines actuelles: ${currentWeeks.size}, plus ancienne: $oldestWeekLoaded")
        
        viewModelScope.launch {
            _isLoadingMoreWeeks.value = true
            try {
                android.util.Log.d("ScamarkViewModel", "📞 Appel repository.getExtendedAvailableWeeks...")
                
                // Étendre la recherche vers des semaines plus anciennes à partir de la vraie plus ancienne
                val moreWeeks = repository.getExtendedAvailableWeeksFromWeek(supplier, oldestWeekLoaded ?: 1)
                
                android.util.Log.d("ScamarkViewModel", "📦 Reçu ${moreWeeks.size} nouvelles semaines du repository")
                
                // Fusionner avec les semaines existantes et éviter les doublons
                val allWeeks = (currentWeeks + moreWeeks).distinctBy { "${it.year}-${it.week}-${it.supplier}" }
                    .sortedWith(compareByDescending<AvailableWeek> { it.year }
                        .thenByDescending { it.week }
                        .thenBy { it.supplier })
                
                // Mettre à jour la plus ancienne semaine chargée
                oldestWeekLoaded = allWeeks.minByOrNull { it.week }?.week
                
                android.util.Log.d("ScamarkViewModel", "🔗 Total après fusion: ${allWeeks.size} semaines (avant: ${currentWeeks.size}, nouvelles: ${moreWeeks.size}, nouvelle plus ancienne: $oldestWeekLoaded)")
                
                _availableWeeks.value = allWeeks
                
                // Vérifier s'il peut y avoir encore plus de semaines
                updateCanLoadMoreWeeks(allWeeks)
                
            } catch (e: Exception) {
                android.util.Log.e("ScamarkViewModel", "🚨 Erreur loadMoreWeeks: ${e.message}")
                _error.value = "Erreur lors du chargement de plus de semaines: ${e.message}"
            } finally {
                _isLoadingMoreWeeks.value = false
                android.util.Log.d("ScamarkViewModel", "🏁 loadMoreWeeks terminé")
            }
        }
    }
    
    /**
     * Met à jour l'état canLoadMoreWeeks selon les semaines actuelles
     */
    private fun updateCanLoadMoreWeeks(weeks: List<AvailableWeek>) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val oldestYear = weeks.minByOrNull { it.year }?.year ?: currentYear
        
        // Permettre le chargement de plus si on n'a pas encore atteint 2 ans en arrière
        _canLoadMoreWeeks.value = (currentYear - oldestYear) < 2 && weeks.size < 100
    }
    
    /**
     * Charge les données de la semaine sélectionnée avec debounce
     */
    fun loadWeekData() {
        val year = _selectedYear.value ?: return
        val week = _selectedWeek.value ?: return
        val supplier = _selectedSupplier.value ?: "all"
        
        // Annuler le job précédent s'il existe (debounce)
        loadWeekDataJob?.cancel()
        
        android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_DATA_START: Début chargement données semaine $year-$week pour '$supplier'")
        val startTime = System.currentTimeMillis()
        
        loadWeekDataJob = viewModelScope.launch {
            val loadingStart = System.currentTimeMillis()
            _isLoading.value = true
            android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_LOADING_STATE: État loading activé en ${System.currentTimeMillis() - loadingStart}ms")
            
            try {
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_PRODUCTS_CALL: Appel repository.getWeekDecisions($year, $week, $supplier)")
                val loadProductsStart = System.currentTimeMillis()
                
                // Charger les produits et statistiques en parallèle
                val products = repository.getWeekDecisions(year, week, supplier)
                val loadProductsEnd = System.currentTimeMillis()
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_PRODUCTS_DONE: Products chargés en ${loadProductsEnd - loadProductsStart}ms - ${products.size} produits")
                
                // Afficher un aperçu des fournisseurs des produits chargés
                val supplierBreakdown = products.groupBy { it.supplier }.mapValues { it.value.size }
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_BREAKDOWN: Répartition par fournisseur: $supplierBreakdown")
                
                val loadStatsStart = System.currentTimeMillis()
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_STATS_LOCAL: Calcul stats locales depuis products déjà chargés")
                val stats = calculateStatsFromProducts(products)
                val loadStatsEnd = System.currentTimeMillis()
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_STATS_DONE: Stats calculées localement en ${loadStatsEnd - loadStatsStart}ms")
                
                val assignStart = System.currentTimeMillis()
                _allProducts.value = products
                _stats.value = stats
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_DATA_ASSIGN: Données assignées en ${System.currentTimeMillis() - assignStart}ms")
                
                // Charger les données de la semaine précédente pour les comparaisons
                val prevWeekStart = System.currentTimeMillis()
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_PREV_CALL: Appel loadPreviousWeekForComparison($year, $week)")
                loadPreviousWeekForComparison(year, week)
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_PREV_DONE: loadPreviousWeekForComparison terminé en ${System.currentTimeMillis() - prevWeekStart}ms")
                
                // Appliquer le filtre de recherche actuel
                val filterStart = System.currentTimeMillis()
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_FILTER_CALL: Appel filterProducts()")
                filterProducts()
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_FILTER_DONE: filterProducts terminé en ${System.currentTimeMillis() - filterStart}ms")
                
            } catch (e: Exception) {
                android.util.Log.e("ScamarkViewModel", "⏱️ WEEK_ERROR: Erreur loadWeekData: ${e.message}")
                _error.value = "Erreur lors du chargement: ${e.message}"
            } finally {
                val finallyStart = System.currentTimeMillis()
                _isLoading.value = false
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_LOADING_OFF: État loading désactivé en ${System.currentTimeMillis() - finallyStart}ms")
                
                val endTime = System.currentTimeMillis()
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEK_DATA_END: TOTAL chargement données semaine en ${endTime - startTime}ms")
            }
        }
    }
    
    /**
     * Change la semaine sélectionnée
     */
    fun selectWeek(year: Int, week: Int) {
        if (_selectedYear.value != year || _selectedWeek.value != week) {
            android.util.Log.d("ScamarkViewModel", "📅 CHANGEMENT DE SEMAINE: $year-$week")
            android.util.Log.d("ScamarkViewModel", "🔄 Réinitialisation des filtres lors du changement de semaine")
            
            // Réinitialiser le filtre à "all" lors du changement de semaine
            _productFilter.value = "all"
            
            // Nettoyer les produits entrants/sortants préchargés
            preloadedSortants = null
            preloadedEntrants = null
            
            _selectedYear.value = year
            _selectedWeek.value = week
            loadWeekData()
            
            android.util.Log.d("ScamarkViewModel", "✅ Semaine changée et filtres réinitialisés")
        }
    }
    
    /**
     * Change le fournisseur sélectionné
     */
    fun selectSupplier(supplier: String) {
        if (_selectedSupplier.value != supplier) {
            val oldSupplier = _selectedSupplier.value
            android.util.Log.d("ScamarkViewModel", "🔄 Changement de fournisseur: '$oldSupplier' -> '$supplier'")
            
            // IMPORTANT: Nettoyer les données de la semaine précédente lors du changement de fournisseur
            // pour éviter que les produits du nouveau fournisseur apparaissent en vert (entrants)
            previousWeekProducts = emptyList()
            android.util.Log.d("ScamarkViewModel", "🧹 previousWeekProducts nettoyé lors du changement de fournisseur")
            
            // IMPORTANT: Définir le nouveau fournisseur
            _selectedSupplier.value = supplier
            
            // Charger les semaines disponibles pour ce fournisseur spécifique
            loadAvailableWeeks(supplier)
            
            // Charger les données de la semaine pour ce fournisseur spécifique
            loadWeekData()
        }
    }
    
    /**
     * Met à jour le filtre de produits
     */
    fun setProductFilter(filter: String) {
        _productFilter.value = filter
        filterProducts()
    }
    
    /**
     * Met à jour la requête de recherche
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * Effectue une recherche de produits
     */
    fun searchProducts(query: String?) {
        _searchQuery.value = query ?: ""
        filterProducts()
    }
    
    /**
     * Filtre les produits selon la recherche et le filtre actuel
     */
    private fun filterProducts() {
        val allProducts = _allProducts.value ?: emptyList()
        val query = _searchQuery.value?.lowercase() ?: ""
        val filter = _productFilter.value ?: "all"
        
        var filtered = allProducts
        
        // Appliquer le filtre de type
        filtered = when (filter) {
            "promo" -> filtered.filter { it.isPromo }
            "entrants" -> filterEntrants(filtered)
            "sortants" -> filterSortants(filtered)
            else -> filtered
        }
        
        // Appliquer la recherche textuelle
        if (query.isNotEmpty()) {
            filtered = filtered.filter { product ->
                // Recherche dans le nom du produit
                product.productName.lowercase().contains(query) ||
                // Recherche dans les infos article
                product.articleInfo?.let { article ->
                    article.nom.lowercase().contains(query) ||
                    article.marque?.lowercase()?.contains(query) == true ||
                    article.origine?.lowercase()?.contains(query) == true ||
                    article.categorie?.lowercase()?.contains(query) == true ||
                    article.codeProduit.lowercase().contains(query) ||
                    article.ean?.lowercase()?.contains(query) == true
                } == true ||
                // Recherche dans les clients SCA
                product.decisions.any { decision ->
                    decision.codeClient.lowercase().contains(query) ||
                    decision.nomClient.lowercase().contains(query) ||
                    decision.clientInfo?.nom?.lowercase()?.contains(query) == true ||
                    decision.clientInfo?.nomClient?.lowercase()?.contains(query) == true
                }
            }
        }
        
        _products.value = filtered
    }
    
    /**
     * Filtre les produits entrants (présents cette semaine mais pas la semaine précédente)
     */
    private fun filterEntrants(products: List<ScamarkProduct>): List<ScamarkProduct> {
        if (previousWeekProducts.isEmpty()) {
            return emptyList()
        }
        
        val currentProductNames = products.map { it.productName }.toSet()
        val previousProductNames = previousWeekProducts.map { it.productName }.toSet()
        val entrantNames = currentProductNames.subtract(previousProductNames)
        
        return products.filter { entrantNames.contains(it.productName) }
    }
    
    /**
     * Filtre les produits sortants (présents la semaine précédente mais pas cette semaine)
     */
    private fun filterSortants(products: List<ScamarkProduct>): List<ScamarkProduct> {
        // Si nous avons déjà des produits sortants préchargés (depuis l'aperçu), les utiliser directement
        if (preloadedSortants != null) {
            val result = preloadedSortants ?: emptyList()
            preloadedSortants = null // Nettoyer après utilisation
            return result
        }
        
        if (previousWeekProducts.isEmpty()) {
            return emptyList()
        }
        
        val currentProductNames = products.map { it.productName }.toSet()
        val previousProductNames = previousWeekProducts.map { it.productName }.toSet()
        val sortantNames = previousProductNames.subtract(currentProductNames)
        
        // Pour les sortants, on retourne les produits de la semaine précédente qui ne sont plus présents
        return previousWeekProducts.filter { sortantNames.contains(it.productName) }
    }
    
    /**
     * Charge les données de la semaine précédente pour les comparaisons
     */
    private fun loadPreviousWeekForComparison(currentYear: Int, currentWeek: Int) {
        val (previousYear, previousWeek) = getPreviousWeek(currentYear, currentWeek)
        
        viewModelScope.launch {
            try {
                val repository = FirebaseRepository()
                val supplier = _selectedSupplier.value ?: "all"
                previousWeekProducts = repository.getWeekDecisions(previousYear, previousWeek, supplier)
                
                // Reappliquer les filtres et notifier les observateurs pour actualiser l'affichage des couleurs
                filterProducts()
                
                // Forcer la mise à jour de la liste pour actualiser les couleurs
                val currentProducts = _products.value
                if (currentProducts != null) {
                    _products.value = currentProducts.toList() // Force une nouvelle émission
                }
                
            } catch (e: Exception) {
                previousWeekProducts = emptyList()
            }
        }
    }
    
    /**
     * Détermine le statut d'un produit (entrant, sortant, ou neutre)
     */
    fun getProductStatus(productName: String): ProductStatus {
        if (previousWeekProducts.isEmpty()) {
            return ProductStatus.NEUTRAL
        }
        
        val currentProducts = _allProducts.value ?: emptyList()
        val currentProductNames = currentProducts.map { it.productName }.toSet()
        val previousProductNames = previousWeekProducts.map { it.productName }.toSet()
        
        return when {
            currentProductNames.contains(productName) && !previousProductNames.contains(productName) -> ProductStatus.ENTRANT
            !currentProductNames.contains(productName) && previousProductNames.contains(productName) -> ProductStatus.SORTANT
            else -> ProductStatus.NEUTRAL
        }
    }
    
    /**
     * Calcule la semaine précédente (gère le passage d'année)
     */
    private fun getPreviousWeek(year: Int, week: Int): Pair<Int, Int> {
        return if (week > 1) {
            Pair(year, week - 1)
        } else {
            Pair(year - 1, 52)
        }
    }
    
    /**
     * Formate l'affichage d'une semaine
     */
    fun formatWeekDisplay(year: Int, week: Int, context: android.content.Context? = null): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.WEEK_OF_YEAR, week)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        
        val weekStart = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, 6)
        val weekEnd = calendar.time
        
        val dateFormat = java.text.SimpleDateFormat("dd/MM", Locale.getDefault())
        val weekStr = week.toString().padStart(2, '0')
        
        val weekShort = context?.getString(com.nextjsclient.android.R.string.week_short) ?: "S"
        val separator = context?.getString(com.nextjsclient.android.R.string.date_range_separator) ?: "au"
        
        return "$weekShort$weekStr - ${dateFormat.format(weekStart)} $separator ${dateFormat.format(weekEnd)}"
    }
    
    /**
     * Formate seulement les dates d'une semaine (pour le nouveau sélecteur)
     */
    fun formatWeekDatesOnly(year: Int, week: Int, context: android.content.Context? = null): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.WEEK_OF_YEAR, week)
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        
        val weekStart = calendar.time
        calendar.add(Calendar.DAY_OF_YEAR, 6)
        val weekEnd = calendar.time
        
        val dateFormat = java.text.SimpleDateFormat("dd/MM", Locale.getDefault())
        val separator = context?.getString(com.nextjsclient.android.R.string.date_range_separator) ?: "au"
        
        return "${dateFormat.format(weekStart)} $separator ${dateFormat.format(weekEnd)}"
    }
    
    /**
     * Calcule la semaine ISO courante
     */
    private fun getCurrentISOWeek(): Int {
        val calendar = Calendar.getInstance()
        val date = calendar.time
        
        calendar.time = date
        val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        calendar.add(Calendar.DAY_OF_YEAR, -dayOfWeek + 3)
        val firstThursday = calendar.timeInMillis
        
        calendar.set(Calendar.MONTH, 0)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        if (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) {
            val daysToAdd = (4 - calendar.get(Calendar.DAY_OF_WEEK) + 7) % 7
            calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
        }
        
        return 1 + ((firstThursday - calendar.timeInMillis) / (7 * 24 * 60 * 60 * 1000)).toInt()
    }
    
    /**
     * Efface l'erreur
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Force le rechargement des données
     */
    fun refresh(activity: android.app.Activity? = null) {
        android.util.Log.d("ScamarkViewModel", "🔄 REFRESH: Réinitialisation complète")
        
        // Nettoyer le cache MainActivity pour éviter la persistance des filtres
        clearMainActivityCache(activity)
        
        // Réinitialiser le filtre à "all" pour afficher tous les produits
        _productFilter.value = "all"
        
        // Nettoyer les produits sortants et entrants préchargés s'il y en a
        preloadedSortants = null
        preloadedEntrants = null
        
        // Recharger les données de la semaine actuelle
        loadAvailableWeeks()
        loadWeekData()
        
        android.util.Log.d("ScamarkViewModel", "✅ REFRESH: Cache nettoyé, filtre réinitialisé, rechargement de tous les produits")
    }
    
    /**
     * Nettoie le cache MainActivity (appelé depuis le fragment)
     */
    fun clearMainActivityCache(activity: android.app.Activity?) {
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        mainActivity?.clearPreloadedCache()
    }
    
    /**
     * Charge les données préchargées directement dans le ViewModel pour un chargement instantané
     */
    fun setPreloadedData(
        supplier: String, 
        products: List<ScamarkProduct>, 
        weeks: List<AvailableWeek>
    ) {
        android.util.Log.d("ScamarkViewModel", "🔵🔵🔵 DÉBUT setPreloadedData")
        android.util.Log.d("ScamarkViewModel", "   • Supplier: $supplier")
        android.util.Log.d("ScamarkViewModel", "   • Products reçus: ${products.size}")
        android.util.Log.d("ScamarkViewModel", "   • Weeks reçues: ${weeks.size}")
        val startTime = System.currentTimeMillis()
        
        // Définir le fournisseur sélectionné
        _selectedSupplier.value = supplier
        
        // Vérifier si nous recevons des produits sortants
        val currentFilter = _productFilter.value
        android.util.Log.d("ScamarkViewModel", "   • Filtre actuel: $currentFilter")
        
        if (currentFilter == "sortants" && products.isNotEmpty()) {
            android.util.Log.d("ScamarkViewModel", "🔴 DÉTECTION PRODUITS SORTANTS DÉJÀ FILTRÉS!")
            android.util.Log.d("ScamarkViewModel", "   • Réception de ${products.size} produits sortants déjà filtrés")
            products.take(3).forEach { product ->
                android.util.Log.d("ScamarkViewModel", "   • Produit sortant: ${product.productName}")
            }
            // Les produits reçus sont DÉJÀ les sortants filtrés depuis OverviewFragment
            // On les met directement dans _products sans passer par filterProducts
            _allProducts.value = emptyList() // Pas de produits "all"
            _products.value = products // Directement les sortants
            android.util.Log.d("ScamarkViewModel", "   • Sortants affichés directement: ${products.size} produits")
            
            // Ne PAS appeler filterProducts() pour les sortants, ils sont déjà filtrés
            val endTime = System.currentTimeMillis()
            android.util.Log.d("ScamarkViewModel", "🔵🔵🔵 FIN setPreloadedData (sortants directs) - Durée: ${endTime - startTime}ms")
            return // Sortir sans appeler filterProducts()
        } else if (currentFilter == "entrants" && products.isNotEmpty()) {
            android.util.Log.d("ScamarkViewModel", "🟢 DÉTECTION PRODUITS ENTRANTS DÉJÀ FILTRÉS!")
            android.util.Log.d("ScamarkViewModel", "   • Réception de ${products.size} produits entrants déjà filtrés")
            products.take(3).forEach { product ->
                android.util.Log.d("ScamarkViewModel", "   • Produit entrant: ${product.productName}")
            }
            // Les produits reçus sont DÉJÀ les entrants filtrés depuis OverviewFragment
            // On les met directement dans _products sans passer par filterProducts
            _allProducts.value = emptyList() // Pas de produits "all"
            _products.value = products // Directement les entrants
            android.util.Log.d("ScamarkViewModel", "   • Entrants affichés directement: ${products.size} produits")
            
            // Ne PAS appeler filterProducts() pour les entrants, ils sont déjà filtrés
            val endTime = System.currentTimeMillis()
            android.util.Log.d("ScamarkViewModel", "🔵🔵🔵 FIN setPreloadedData (entrants directs) - Durée: ${endTime - startTime}ms")
            return // Sortir sans appeler filterProducts()
        } else {
            android.util.Log.d("ScamarkViewModel", "📦 Chargement normal des produits")
            // Charger directement les données sans appels réseau
            _allProducts.value = products
            android.util.Log.d("ScamarkViewModel", "   • _allProducts chargé avec ${products.size} produits")
        }
        
        _availableWeeks.value = weeks
        
        // Calculer les stats
        val stats = calculateStatsFromProducts(products)
        _stats.value = stats
        android.util.Log.d("ScamarkViewModel", "   • Stats calculées")
        
        // Appliquer les filtres
        android.util.Log.d("ScamarkViewModel", "🎯 Appel de filterProducts()")
        filterProducts()
        
        val endTime = System.currentTimeMillis()
        android.util.Log.d("ScamarkViewModel", "🔵🔵🔵 FIN setPreloadedData - Durée: ${endTime - startTime}ms")
    }
    
    /**
     * Calcule les statistiques à partir d'une liste de produits
     */
    /**
     * Méthode pour définir directement les produits (utilisée par OverviewFragment)
     */
    fun setProducts(productsList: List<ScamarkProduct>) {
        android.util.Log.d("ScamarkViewModel", "📦 setProducts: ${productsList.size} produits")
        _allProducts.value = productsList
        _products.value = productsList
        filterProducts()
    }
    
    /**
     * Force le rechargement spécifique à un fournisseur sans nettoyer previousWeekProducts
     */
    fun forceReloadSupplierData(supplier: String) {
        android.util.Log.d("ScamarkViewModel", "🔄 forceReloadSupplierData: Force rechargement pour $supplier")
        
        // Nettoyer seulement les données mixtes de l'aperçu
        _allProducts.value = emptyList()
        _products.value = emptyList()
        _productFilter.value = "all"
        
        // NE PAS nettoyer previousWeekProducts pour garder les comparaisons entrants/sortants
        android.util.Log.d("ScamarkViewModel", "✅ Données mixtes nettoyées, previousWeekProducts préservé")
        
        // Recharger les données spécifiques au fournisseur
        loadAvailableWeeks(supplier)
        loadWeekData()
    }
    
    private fun calculateStatsFromProducts(products: List<ScamarkProduct>): ScamarkStats {
        val totalProducts = products.size
        val totalPromos = products.count { it.isPromo }
        val uniqueClients = products.flatMap { it.decisions.map { d -> d.codeClient } }.distinct().size
        
        // Calculer entrants/sortants si on a des données de semaine précédente
        var productsIn = 0
        var productsOut = 0
        
        if (previousWeekProducts.isNotEmpty()) {
            val currentProductNames = products.map { it.productName }.toSet()
            val previousProductNames = previousWeekProducts.map { it.productName }.toSet()
            
            productsIn = (currentProductNames - previousProductNames).size
            productsOut = (previousProductNames - currentProductNames).size
        }
        
        return ScamarkStats(
            totalProducts = totalProducts,
            activeClients = uniqueClients,
            productsIn = productsIn,
            productsOut = productsOut,
            totalPromos = totalPromos
        )
    }
}