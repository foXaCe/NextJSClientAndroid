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
    
    // Indicateur pour savoir si on est en mode recherche client SCA
    private val _isClientSearchMode = MutableLiveData<Boolean>(false)
    val isClientSearchMode: LiveData<Boolean> = _isClientSearchMode
    
    // Suggestions de recherche
    private val _searchSuggestions = MutableLiveData<List<SearchSuggestion>>(emptyList())
    val searchSuggestions: LiveData<List<SearchSuggestion>> = _searchSuggestions
    
    // Variables pour gérer le chargement progressif des semaines
    private var oldestWeekLoaded: Int? = null
    private var oldestYearLoaded: Int? = null
    private var loadedWeeksPerPage = 12 // Nombre de semaines à charger par page
    private val loadingWeeksSet = mutableSetOf<String>() // "year-week" format
    
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
                clearLoadingWeeks() // Nettoyer les semaines en chargement après mise à jour
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_DATA_SET: Données assignées en ${System.currentTimeMillis() - processStart}ms")
                
                // Tracker la plus ancienne semaine chargée (prendre en compte l'année)
                val trackStart = System.currentTimeMillis()
                val oldestWeek = weeks.minByOrNull { it.year * 100 + it.week }
                oldestWeekLoaded = oldestWeek?.week
                oldestYearLoaded = oldestWeek?.year
                android.util.Log.d("ScamarkViewModel", "⏱️ WEEKS_TRACK: Tracking oldest week en ${System.currentTimeMillis() - trackStart}ms -> semaine $oldestWeekLoaded de l'année $oldestYearLoaded")
                
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
        
        android.util.Log.d("ScamarkViewModel", "🚀 loadMoreWeeks appelé - supplier: '$supplier', semaines actuelles: ${currentWeeks.size}, plus ancienne: semaine $oldestWeekLoaded de $oldestYearLoaded")
        android.util.Log.d("ScamarkViewModel", "🚀 FOURNISSEUR_CHECK: supplier='$supplier' (length=${supplier.length})")
        
        // Marquer les prochaines semaines comme "en chargement"
        markWeeksAsLoading()
        
        viewModelScope.launch {
            _isLoadingMoreWeeks.value = true
            try {
                android.util.Log.d("ScamarkViewModel", "📞 Appel repository.getExtendedAvailableWeeksFromWeek pour '$supplier'...")
                
                // Calculer la semaine de départ pour l'extension en tenant compte de l'année
                val startWeek = oldestWeekLoaded ?: 1
                val startYear = oldestYearLoaded ?: Calendar.getInstance().get(Calendar.YEAR)
                
                android.util.Log.d("ScamarkViewModel", "🔍 RECHERCHE_PARAMS: supplier='$supplier', startWeek=$startWeek, startYear=$startYear")
                
                // Étendre la recherche vers des semaines plus anciennes
                val repoStart = System.currentTimeMillis()
                val moreWeeks = repository.getExtendedAvailableWeeksFromWeek(supplier, startWeek, startYear)
                val repoEnd = System.currentTimeMillis()
                
                android.util.Log.d("ScamarkViewModel", "📦 REPO_RESULT: supplier='$supplier' → ${moreWeeks.size} semaines trouvées en ${repoEnd - repoStart}ms")
                
                if (moreWeeks.isNotEmpty()) {
                    val firstWeek = moreWeeks.first()
                    val lastWeek = moreWeeks.last()
                    android.util.Log.d("ScamarkViewModel", "📦 RANGE_DETAIL: première=${lastWeek.week}/${lastWeek.year}, dernière=${firstWeek.week}/${firstWeek.year}")
                    
                    // Grouper par fournisseur pour diagnostiquer
                    val bySupplier = moreWeeks.groupBy { it.supplier }
                    bySupplier.forEach { (sup, weeks) ->
                        android.util.Log.d("ScamarkViewModel", "📦 SUPPLIER_BREAKDOWN: '$sup' → ${weeks.size} semaines")
                    }
                }
                
                android.util.Log.d("ScamarkViewModel", "📦 Reçu ${moreWeeks.size} nouvelles semaines du repository")
                
                // Fusionner avec les semaines existantes et éviter les doublons
                val allWeeks = (currentWeeks + moreWeeks).distinctBy { "${it.year}-${it.week}-${it.supplier}" }
                    .sortedWith(compareByDescending<AvailableWeek> { it.year }
                        .thenByDescending { it.week }
                        .thenBy { it.supplier })
                
                // Mettre à jour la plus ancienne semaine chargée SEULEMENT si on a vraiment trouvé de nouvelles semaines
                if (moreWeeks.isNotEmpty()) {
                    val actualOldestFromNew = moreWeeks.minByOrNull { it.year * 100 + it.week }
                    if (actualOldestFromNew != null) {
                        // Vérifier qu'on a bien progressé vers une semaine plus ancienne
                        val currentOldestScore = (oldestYearLoaded ?: 2024) * 100 + (oldestWeekLoaded ?: 40)
                        val newOldestScore = actualOldestFromNew.year * 100 + actualOldestFromNew.week
                        
                        if (newOldestScore < currentOldestScore) {
                            oldestWeekLoaded = actualOldestFromNew.week
                            oldestYearLoaded = actualOldestFromNew.year
                            android.util.Log.d("ScamarkViewModel", "🔄 TRACKING_UPDATE: oldestWeekLoaded mis à jour → semaine ${actualOldestFromNew.week}/${actualOldestFromNew.year}")
                        } else {
                            android.util.Log.w("ScamarkViewModel", "⚠️ TRACKING_STUCK: Pas de progression! Resté à semaine $oldestWeekLoaded/$oldestYearLoaded")
                        }
                    }
                }
                
                android.util.Log.d("ScamarkViewModel", "🔗 Total après fusion: ${allWeeks.size} semaines (avant: ${currentWeeks.size}, nouvelles: ${moreWeeks.size}, nouvelle plus ancienne: semaine $oldestWeekLoaded de $oldestYearLoaded)")
                
                _availableWeeks.value = allWeeks
                
                // Si on n'a trouvé AUCUNE nouvelle semaine, arrêter le chargement automatique
                if (moreWeeks.isEmpty()) {
                    android.util.Log.w("ScamarkViewModel", "🛑 STOP_AUTO_LOADING - Aucune nouvelle semaine trouvée, arrêt du chargement automatique")
                    _canLoadMoreWeeks.value = false
                } else {
                    // Vérifier s'il peut y avoir encore plus de semaines seulement si on en a trouvé
                    updateCanLoadMoreWeeks(allWeeks)
                }
                
            } catch (e: Exception) {
                android.util.Log.e("ScamarkViewModel", "🚨 Erreur loadMoreWeeks: ${e.message}")
                _error.value = "Erreur lors du chargement de plus de semaines: ${e.message}"
            } finally {
                // Nettoyer les semaines en chargement
                clearLoadingWeeks()
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
        val newestYear = weeks.maxByOrNull { it.year }?.year ?: currentYear
        
        // Diagnostiquer par fournisseur
        val bySupplier = weeks.groupBy { it.supplier }
        val currentSupplier = _selectedSupplier.value ?: "all"
        
        android.util.Log.d("ScamarkViewModel", "📅 updateCanLoadMoreWeeks - currentSupplier: '$currentSupplier'")
        android.util.Log.d("ScamarkViewModel", "📅 updateCanLoadMoreWeeks - currentYear: $currentYear, oldestYear: $oldestYear, newestYear: $newestYear, total weeks: ${weeks.size}")
        
        bySupplier.forEach { (supplier, supplierWeeks) ->
            android.util.Log.d("ScamarkViewModel", "📅 SUPPLIER_WEEKS: '$supplier' → ${supplierWeeks.size} semaines")
        }
        
        // Permettre le chargement de plus si on n'a pas encore atteint 5 ans en arrière (augmenté de 3 à 5)
        // Ou si on a moins de 200 semaines au total
        val yearRange = currentYear - oldestYear
        val canLoadMore = yearRange < 5 && weeks.size < 200
        
        android.util.Log.d("ScamarkViewModel", "📅 CAN_LOAD_MORE_CALC: yearRange=$yearRange, totalSize=${weeks.size}")
        android.util.Log.d("ScamarkViewModel", "📅 canLoadMore: $canLoadMore (yearRange: $yearRange < 5, size: ${weeks.size} < 200)")
        android.util.Log.d("ScamarkViewModel", "📅 Condition 1 (yearRange < 5): ${yearRange < 5}")
        android.util.Log.d("ScamarkViewModel", "📅 Condition 2 (size < 200): ${weeks.size < 200}")
        
        _canLoadMoreWeeks.value = canLoadMore
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
            android.util.Log.d("ScamarkViewModel", "🔄 BEFORE setting isLoading=true, current value: ${_isLoading.value}")
            _isLoading.value = true
            android.util.Log.d("ScamarkViewModel", "🔄 AFTER setting isLoading=true, new value: ${_isLoading.value}")
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
                android.util.Log.d("ScamarkViewModel", "🔄 FINALLY: BEFORE setting isLoading=false, current value: ${_isLoading.value}")
                _isLoading.value = false
                android.util.Log.d("ScamarkViewModel", "🔄 FINALLY: AFTER setting isLoading=false, new value: ${_isLoading.value}")
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
            
            // IMPORTANT: Nettoyer le cache spécifique au nouveau fournisseur
            FirebaseRepository.clearSupplierCache(supplier)
            android.util.Log.d("ScamarkViewModel", "🧹 Cache spécifique au fournisseur '$supplier' nettoyé")
            
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
     * Effectue une recherche de produits et génère des suggestions
     */
    fun searchProducts(query: String?) {
        android.util.Log.d("ScamarkViewModel", "🔍 searchProducts appelé avec: '$query'")
        _searchQuery.value = query ?: ""
        
        // Générer les suggestions si la requête n'est pas vide
        if (!query.isNullOrEmpty() && query.length >= 2) {
            android.util.Log.d("ScamarkViewModel", "🔍 Génération suggestions pour requête valide")
            generateSearchSuggestions(query)
        } else {
            android.util.Log.d("ScamarkViewModel", "🔍 Requête trop courte, effacement suggestions")
            _searchSuggestions.value = emptyList()
        }
        
        // Toujours filtrer les produits pour montrer les résultats en temps réel
        filterProducts()
    }
    
    /**
     * Génère des suggestions de recherche basées sur la requête
     */
    private fun generateSearchSuggestions(query: String) {
        android.util.Log.d("ScamarkViewModel", "🔍 Génération suggestions pour: '$query'")
        val suggestions = mutableListOf<SearchSuggestion>()
        val queryLower = query.lowercase()
        val allProducts = _allProducts.value ?: emptyList()
        android.util.Log.d("ScamarkViewModel", "🔍 Nombre de produits disponibles: ${allProducts.size}")
        
        // Collecter les clients SCA uniques qui correspondent
        val matchingClients = mutableMapOf<String, Int>()
        val matchingBrands = mutableMapOf<String, Int>()
        val matchingCategories = mutableMapOf<String, Int>()
        val matchingProducts = mutableSetOf<String>()
        
        for (product in allProducts) {
            // Chercher dans les clients SCA
            for (decision in product.decisions) {
                val clientName = decision.nomClient
                val clientNameLower = clientName.lowercase()
                if (clientNameLower.contains(queryLower)) {
                    matchingClients[clientName] = matchingClients.getOrDefault(clientName, 0) + 1
                }
            }
            
            // Chercher dans les marques
            product.articleInfo?.marque?.let { marque ->
                if (marque.lowercase().contains(queryLower)) {
                    matchingBrands[marque] = matchingBrands.getOrDefault(marque, 0) + 1
                }
            }
            
            // Chercher dans les catégories
            product.articleInfo?.categorie?.let { categorie ->
                if (categorie.lowercase().contains(queryLower)) {
                    matchingCategories[categorie] = matchingCategories.getOrDefault(categorie, 0) + 1
                }
            }
            
            // Chercher dans les noms de produits
            if (product.productName.lowercase().contains(queryLower)) {
                matchingProducts.add(product.productName)
            }
        }
        
        // Ajouter les suggestions de clients SCA (priorité haute)
        matchingClients.entries
            .sortedByDescending { it.value }
            .take(3)
            .forEach { (clientName, count) ->
                suggestions.add(
                    SearchSuggestion(
                        text = clientName,
                        type = SuggestionType.CLIENT_SCA,
                        count = count,
                        matchedPart = query
                    )
                )
            }
        
        // Ajouter les suggestions de marques
        matchingBrands.entries
            .sortedByDescending { it.value }
            .take(2)
            .forEach { (brand, count) ->
                suggestions.add(
                    SearchSuggestion(
                        text = brand,
                        type = SuggestionType.BRAND,
                        count = count,
                        matchedPart = query
                    )
                )
            }
        
        // Ajouter les suggestions de produits
        matchingProducts
            .take(3)
            .forEach { productName ->
                suggestions.add(
                    SearchSuggestion(
                        text = productName,
                        type = SuggestionType.PRODUCT,
                        matchedPart = query
                    )
                )
            }
        
        // Limiter à 8 suggestions maximum
        val finalSuggestions = suggestions.take(8)
        android.util.Log.d("ScamarkViewModel", "🔍 Suggestions générées: ${finalSuggestions.size}")
        finalSuggestions.forEach { suggestion ->
            android.util.Log.d("ScamarkViewModel", "🔍 Suggestion: ${suggestion.text} (${suggestion.type}, count: ${suggestion.count})")
        }
        _searchSuggestions.value = finalSuggestions
    }
    
    /**
     * Applique une suggestion de recherche
     */
    fun applySuggestion(suggestion: SearchSuggestion) {
        android.util.Log.d("ScamarkViewModel", "🔍 Application suggestion: ${suggestion.text} (${suggestion.type})")
        
        val searchText = when (suggestion.type) {
            SuggestionType.CLIENT_SCA -> {
                // Pour un client SCA, on garde le nom original (pas en minuscules)
                suggestion.text
            }
            SuggestionType.PRODUCT, SuggestionType.BRAND, SuggestionType.CATEGORY -> {
                // Pour les autres, on met le texte complet
                suggestion.text
            }
        }
        
        _searchQuery.value = searchText
        
        // Effacer les suggestions après sélection
        _searchSuggestions.value = emptyList()
        
        // Filtrer les produits avec le nouveau texte de recherche
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
        
        // Appliquer la recherche textuelle avec recherche partielle par mots
        if (query.isNotEmpty()) {
            // Séparer la requête en mots individuels
            val searchTerms = query.split(" ").filter { it.isNotEmpty() }
            
            // D'abord, vérifier si c'est une recherche de client SCA
            // (un seul terme qui correspond à un nom de client)
            var isClientSearch = false
            val matchingClients = mutableSetOf<String>()
            
            if (searchTerms.size == 1 && searchTerms[0].length >= 2) {
                val term = searchTerms[0]
                // Parcourir tous les produits pour voir si le terme correspond à un client
                for (product in filtered) {
                    for (decision in product.decisions) {
                        val nomClientLower = decision.nomClient.lowercase()
                        val codeClientLower = decision.codeClient.lowercase()
                        
                        // Recherche plus souple : contient le terme n'importe où dans le nom
                        if (nomClientLower.contains(term) || 
                            codeClientLower.contains(term)) {
                            isClientSearch = true
                            matchingClients.add(decision.nomClient)
                        }
                    }
                }
            }
            
            // Mettre à jour l'indicateur de mode recherche client
            _isClientSearchMode.value = isClientSearch
            
            filtered = if (isClientSearch) {
                // Si c'est une recherche de client, ne montrer que les produits de ces clients
                val term = searchTerms[0]
                
                // Filtrer les produits qui ont au moins une décision du client recherché
                val filteredProducts = filtered.filter { product ->
                    product.decisions.any { decision ->
                        val nomClientLower = decision.nomClient.lowercase()
                        val codeClientLower = decision.codeClient.lowercase()
                        
                        nomClientLower.contains(term) ||
                        codeClientLower.contains(term)
                    }
                }
                
                // Créer une nouvelle liste de produits avec uniquement les décisions du client recherché
                filteredProducts.map { product ->
                    val filteredDecisions = product.decisions.filter { decision ->
                        val nomClientLower = decision.nomClient.lowercase()
                        val codeClientLower = decision.codeClient.lowercase()
                        
                        nomClientLower.contains(term) ||
                        codeClientLower.contains(term)
                    }
                    
                    // Créer une copie du produit avec seulement les décisions filtrées
                    product.copy(
                        decisions = filteredDecisions,
                        totalScas = filteredDecisions.size
                    )
                }
            } else {
                // Sinon, recherche normale dans tous les champs
                filtered.filter { product ->
                    searchTerms.all { term ->
                        val productNameLower = product.productName.lowercase()
                        val articleInfo = product.articleInfo
                        
                        // Recherche dans le nom du produit (avec support des mots partiels)
                        productNameLower.contains(term) ||
                        productNameLower.split(" ").any { word -> word.startsWith(term) } ||
                        // Recherche dans les infos article
                        articleInfo?.let { article ->
                            val nomLower = article.nom.lowercase()
                            val marqueLower = article.marque?.lowercase() ?: ""
                            val origineLower = article.origine?.lowercase() ?: ""
                            val categorieLower = article.categorie?.lowercase() ?: ""
                            val codeProduitLower = article.codeProduit.lowercase()
                            val eanLower = article.ean?.lowercase() ?: ""
                            
                            nomLower.contains(term) ||
                            nomLower.split(" ").any { word -> word.startsWith(term) } ||
                            marqueLower.contains(term) ||
                            marqueLower.split(" ").any { word -> word.startsWith(term) } ||
                            origineLower.contains(term) ||
                            categorieLower.contains(term) ||
                            codeProduitLower.contains(term) ||
                            eanLower.contains(term)
                        } == true
                    }
                }
            }
        } else {
            // Si la recherche est vide, réinitialiser le mode recherche client
            _isClientSearchMode.value = false
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
    fun getCurrentISOWeek(): Int {
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
     * Force le rechargement des données avec activité optionnelle
     */
    fun refresh(activity: android.app.Activity? = null) {
        android.util.Log.d("ScamarkViewModel", "🔄 REFRESH: Réinitialisation complète")
        android.util.Log.d("ScamarkViewModel", "🔄 Current isLoading state: ${_isLoading.value}")
        
        // Nettoyer le cache MainActivity pour éviter la persistance des filtres
        clearMainActivityCache(activity)
        
        // Réinitialiser le filtre à "all" pour afficher tous les produits
        _productFilter.value = "all"
        
        // Réinitialiser la recherche
        _searchQuery.value = ""
        _isClientSearchMode.value = false
        
        // Nettoyer les produits sortants et entrants préchargés s'il y en a
        preloadedSortants = null
        preloadedEntrants = null
        
        // Revenir à la semaine actuelle
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        _selectedYear.value = currentYear
        _selectedWeek.value = currentWeek
        android.util.Log.d("ScamarkViewModel", "🔄 Retour à la semaine actuelle: $currentWeek de $currentYear")
        
        // Recharger les données de la semaine actuelle
        val supplier = _selectedSupplier.value ?: "all"
        android.util.Log.d("ScamarkViewModel", "🔄 Calling loadAvailableWeeks($supplier)")
        loadAvailableWeeks(supplier)
        android.util.Log.d("ScamarkViewModel", "🔄 Calling loadWeekData()")
        loadWeekData()
        
        android.util.Log.d("ScamarkViewModel", "✅ REFRESH: Cache nettoyé, filtre réinitialisé, rechargement de tous les produits")
    }
    
    /**
     * Force le rechargement des données (sans paramètre, pour OverviewFragment)
     */
    fun refresh() {
        android.util.Log.d("ScamarkViewModel", "🔄 refresh() called without parameters")
        refresh(null)
    }
    
    /**
     * Génère une grille de semaines pour le sélecteur Material 3
     */
    fun generateWeekGridItems(currentDisplayYear: Int): List<com.nextjsclient.android.ui.components.WeekItem> {
        val availableWeeksList = _availableWeeks.value ?: emptyList()
        val currentSupplier = _selectedSupplier.value ?: "all"
        val selectedYear = _selectedYear.value ?: currentDisplayYear
        val selectedWeek = _selectedWeek.value ?: 1
        
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        
        val weekItems = mutableListOf<com.nextjsclient.android.ui.components.WeekItem>()
        
        android.util.Log.d("ScamarkViewModel", "🗓️ generateWeekGridItems pour année $currentDisplayYear, fournisseur '$currentSupplier'")
        android.util.Log.d("ScamarkViewModel", "🗓️ availableWeeksList total: ${availableWeeksList.size} semaines")
        
        // Filtrer par fournisseur ET par année
        val supplierFilteredWeeks = if (currentSupplier == "all") {
            availableWeeksList.filter { it.year == currentDisplayYear }
        } else {
            availableWeeksList.filter { it.year == currentDisplayYear && it.supplier == currentSupplier }
        }
        
        android.util.Log.d("ScamarkViewModel", "🗓️ Semaines disponibles pour $currentDisplayYear + '$currentSupplier': ${supplierFilteredWeeks.map { "${it.week}(${it.supplier})" }}")
        android.util.Log.d("ScamarkViewModel", "🗓️ Sélectionné: semaine $selectedWeek année $selectedYear")
        
        // Nettoyer les semaines en chargement pour éviter les loaders infinis
        clearLoadingWeeks()
        
        // Générer les semaines de l'année demandée - utiliser getActualMaximum
        val maxWeekInYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentDisplayYear)
            set(Calendar.WEEK_OF_YEAR, 1)
        }.getActualMaximum(Calendar.WEEK_OF_YEAR)
        
        // Limiter aux semaines passées, courante et suivante (s+1)
        val finalMaxWeek = if (currentDisplayYear == currentYear) {
            minOf(maxWeekInYear, currentWeek + 1)
        } else if (currentDisplayYear > currentYear) {
            // Années futures : aucune semaine
            0
        } else {
            // Années passées : toutes les semaines
            maxWeekInYear
        }
        
        android.util.Log.d("ScamarkViewModel", "🗓️ Max semaines dans l'année $currentDisplayYear: $maxWeekInYear → limité à $finalMaxWeek")
        
        for (week in 1..finalMaxWeek) {
            // Vérifier la disponibilité selon le fournisseur sélectionné
            val hasData = supplierFilteredWeeks.any { it.week == week }
            val isCurrentWeek = (currentDisplayYear == currentYear && week == currentWeek)
            val isSelected = (currentDisplayYear == selectedYear && week == selectedWeek)
            
            if (hasData) {
                android.util.Log.d("ScamarkViewModel", "🗓️ Semaine $week/$currentDisplayYear → hasData=true")
            }
            
            val weekItem = com.nextjsclient.android.ui.components.WeekItem(
                year = currentDisplayYear,
                week = week,
                isCurrentWeek = isCurrentWeek,
                isSelected = isSelected,
                hasData = hasData,
                isLoading = isWeekLoading(currentDisplayYear, week)
            )
            
            android.util.Log.d("ScamarkViewModel", "📅 Créé WeekItem semaine $week: hasData=$hasData")
            weekItems.add(weekItem)
        }
        
        android.util.Log.d("ScamarkViewModel", "🗓️ Généré ${weekItems.size} items (${weekItems.count { it.hasData }} avec données)")
        
        return weekItems
    }
    
    /**
     * Vérification rapide de disponibilité d'une semaine spécifique
     */
    fun quickCheckWeekAvailability(week: Int, year: Int) {
        val supplier = _selectedSupplier.value ?: "all"
        android.util.Log.d("ScamarkViewModel", "🔍 QUICK_CHECK_START - Vérification semaine $week/$year pour '$supplier'")
        
        viewModelScope.launch {
            try {
                // Vérifier directement cette semaine spécifique
                val isAvailable = repository.checkWeekAvailability(supplier, week, year)
                
                if (isAvailable) {
                    android.util.Log.d("ScamarkViewModel", "✅ QUICK_CHECK_SUCCESS - Semaine $week/$year disponible!")
                    
                    // Ajouter cette semaine aux disponibles
                    val currentWeeks = _availableWeeks.value?.toMutableList() ?: mutableListOf()
                    if (currentWeeks.none { it.week == week && it.year == year && it.supplier == supplier }) {
                        currentWeeks.add(AvailableWeek(year, week, supplier))
                        _availableWeeks.value = currentWeeks.sortedWith(
                            compareByDescending<AvailableWeek> { it.year }
                                .thenByDescending { it.week }
                                .thenBy { it.supplier }
                        )
                        clearLoadingWeeks() // Nettoyer les semaines en chargement après mise à jour
                        android.util.Log.d("ScamarkViewModel", "➕ QUICK_CHECK_ADDED - Semaine ajoutée aux disponibles")
                    }
                } else {
                    android.util.Log.d("ScamarkViewModel", "❌ QUICK_CHECK_EMPTY - Semaine $week/$year toujours non disponible")
                }
            } catch (e: Exception) {
                android.util.Log.e("ScamarkViewModel", "🚨 QUICK_CHECK_ERROR - ${e.message}")
            }
        }
    }
    
    /**
     * Met à jour l'année affichée dans le sélecteur
     */
    private val _displayYear = MutableLiveData<Int>()
    val displayYear: LiveData<Int> = _displayYear
    
    fun setDisplayYear(year: Int) {
        _displayYear.value = year
    }
    
    fun getCurrentDisplayYear(): Int {
        return _displayYear.value ?: Calendar.getInstance().get(Calendar.YEAR)
    }
    
    /**
     * Marque les semaines comme en chargement pour l'affichage du loader
     */
    private fun markWeeksAsLoading() {
        val startYear = oldestYearLoaded ?: Calendar.getInstance().get(Calendar.YEAR)
        val startWeek = oldestWeekLoaded ?: 1
        
        // Marquer environ 15 semaines précédentes comme "en chargement"
        var year = startYear
        var week = startWeek - 1
        
        repeat(15) {
            if (week < 1) {
                year--
                week = 52 // Approximation
            }
            loadingWeeksSet.add("$year-$week")
            week--
        }
        
        android.util.Log.d("ScamarkViewModel", "⏳ Marqué ${loadingWeeksSet.size} semaines comme en chargement")
    }
    
    /**
     * Nettoie les semaines en chargement
     */
    private fun clearLoadingWeeks() {
        loadingWeeksSet.clear()
        android.util.Log.d("ScamarkViewModel", "✅ Nettoyé les semaines en chargement")
    }
    
    /**
     * Vérifie si une semaine est en cours de chargement
     */
    private fun isWeekLoading(year: Int, week: Int): Boolean {
        return loadingWeeksSet.contains("$year-$week")
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