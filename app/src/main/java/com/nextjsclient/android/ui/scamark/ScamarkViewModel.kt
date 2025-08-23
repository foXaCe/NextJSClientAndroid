package com.nextjsclient.android.ui.scamark

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextjsclient.android.data.models.*
import com.nextjsclient.android.data.repository.FirebaseRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
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
    
    private val _isLoadingWeekChange = MutableLiveData<Boolean>()
    val isLoadingWeekChange: LiveData<Boolean> = _isLoadingWeekChange
    
    
    // Suppression complète du système de comparaison entrants/sortants
    
    // Debounce pour éviter les appels multiples lors de navigation rapide
    private var loadWeekDataJob: kotlinx.coroutines.Job? = null
    
    // Variables preloaded supprimées
    
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
    
    // Produits de la semaine précédente pour comparaison S-1
    private var previousWeekProducts: List<ScamarkProduct> = emptyList()
    
    // Cache simple pour éviter les rechargements inutiles 
    private val weekDataCache = mutableMapOf<String, List<ScamarkProduct>>()
    
    private fun getCacheKey(year: Int, week: Int, supplier: String): String = "$year-$week-$supplier"
    
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
     * Charge les semaines disponibles pour une année spécifique
     */
    fun loadAvailableWeeksForYear(supplier: String = "all", year: Int) {
        android.util.Log.d("ScamarkVM", "📅 loadAvailableWeeksForYear: supplier=$supplier, year=$year")
        
        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val weeks = repository.getAvailableWeeksForYear(supplier, year)
                android.util.Log.d("ScamarkVM", "✅ Weeks for year loaded in ${System.currentTimeMillis() - startTime}ms, count=${weeks.size}")
                
                // Fusionner avec les semaines existantes
                val existingWeeks = _availableWeeks.value ?: emptyList()
                val allWeeks = (existingWeeks + weeks).distinctBy { "${it.year}-${it.week}-${it.supplier}" }
                    .sortedWith(compareByDescending<AvailableWeek> { it.year }.thenByDescending { it.week })
                
                _availableWeeks.value = allWeeks
                
            } catch (e: Exception) {
                _error.value = "Erreur lors du chargement de l'année $year: ${e.message}"
            }
        }
    }

    /**
     * Charge les semaines disponibles
     */
    fun loadAvailableWeeks(supplier: String = _selectedSupplier.value ?: "all") {
        android.util.Log.d("ScamarkVM", "📅 loadAvailableWeeks: supplier=$supplier")
        
        viewModelScope.launch {
            _isLoadingWeeks.value = true
            val startTime = System.currentTimeMillis()
            
            try {
                android.util.Log.d("ScamarkVM", "⏳ Starting to load weeks from repository...")
                val weeks = repository.getAvailableWeeks(supplier)
                
                android.util.Log.d("ScamarkVM", "✅ Available weeks loaded in ${System.currentTimeMillis() - startTime}ms, count=${weeks.size}")
                _availableWeeks.value = weeks
                clearLoadingWeeks() // Nettoyer les semaines en chargement après mise à jour
                
                // Tracker la plus ancienne semaine chargée (prendre en compte l'année)
                val oldestWeek = weeks.minByOrNull { it.year * 100 + it.week }
                oldestWeekLoaded = oldestWeek?.week
                oldestYearLoaded = oldestWeek?.year
                
                // Vérifier s'il peut y avoir plus de semaines
                updateCanLoadMoreWeeks(weeks)
                
                // Si aucune semaine sélectionnée, prendre la plus récente
                if (_selectedYear.value == null || _selectedWeek.value == null) {
                    weeks.firstOrNull()?.let { firstWeek ->
                        _selectedYear.value = firstWeek.year
                        _selectedWeek.value = firstWeek.week
                    }
                }
                
            } catch (e: Exception) {
                _error.value = "Erreur lors du chargement des semaines: ${e.message}"
            } finally {
                _isLoadingWeeks.value = false
                
            }
        }
    }
    
    /**
     * Charge plus de semaines historiques
     */
    fun loadMoreWeeks() {
        val supplier = _selectedSupplier.value ?: "all"
        val currentWeeks = _availableWeeks.value ?: emptyList()
        
        android.util.Log.d("ScamarkVM", "📅 loadMoreWeeks: supplier=$supplier, currentCount=${currentWeeks.size}")
        
        // Marquer les prochaines semaines comme "en chargement"
        markWeeksAsLoading()
        
        viewModelScope.launch {
            _isLoadingMoreWeeks.value = true
            val startTime = System.currentTimeMillis()
            try {
                android.util.Log.d("ScamarkVM", "⏳ Loading more weeks...")
                // Calculer la semaine de départ pour l'extension en tenant compte de l'année
                val startWeek = oldestWeekLoaded ?: 1
                val startYear = oldestYearLoaded ?: Calendar.getInstance().get(Calendar.YEAR)
                
                
                // Étendre la recherche vers des semaines plus anciennes
                val moreWeeks = repository.getExtendedAvailableWeeksFromWeek(supplier, startWeek, startYear)
                
                
                if (moreWeeks.isNotEmpty()) {
                    // Grouper par fournisseur pour diagnostiquer
                    val bySupplier = moreWeeks.groupBy { it.supplier }
                    bySupplier.forEach { (_, _) ->
                    }
                }
                
                
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
                        } else {
                        }
                    }
                }
                
                
                _availableWeeks.value = allWeeks
                
                // Si on n'a trouvé AUCUNE nouvelle semaine, arrêter le chargement automatique
                if (moreWeeks.isEmpty()) {
                    _canLoadMoreWeeks.value = false
                } else {
                    // Vérifier s'il peut y avoir encore plus de semaines seulement si on en a trouvé
                    updateCanLoadMoreWeeks(allWeeks)
                }
                
            } catch (e: Exception) {
                _error.value = "Erreur lors du chargement de plus de semaines: ${e.message}"
            } finally {
                // Nettoyer les semaines en chargement
                clearLoadingWeeks()
                _isLoadingMoreWeeks.value = false
            }
        }
    }
    
    /**
     * Met à jour l'état canLoadMoreWeeks selon les semaines actuelles
     */
    private fun updateCanLoadMoreWeeks(weeks: List<AvailableWeek>) {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val oldestYear = weeks.minByOrNull { it.year }?.year ?: currentYear
        
        // Diagnostiquer par fournisseur
        val bySupplier = weeks.groupBy { it.supplier }
        
        bySupplier.forEach { (_, _) ->
        }
        
        // Permettre le chargement de plus si on n'a pas encore atteint 5 ans en arrière (augmenté de 3 à 5)
        // Ou si on a moins de 200 semaines au total
        val yearRange = currentYear - oldestYear
        val canLoadMore = yearRange < 5 && weeks.size < 200
        
        
        _canLoadMoreWeeks.value = canLoadMore
    }
    
    /**
     * Charge les données de la semaine sélectionnée avec debounce et loading principal (pour refresh)
     */
    fun loadWeekDataWithMainLoading() {
        val year = _selectedYear.value ?: return
        val week = _selectedWeek.value ?: return
        val supplier = _selectedSupplier.value ?: "all"
        
        // Annuler le job précédent s'il existe (debounce)
        loadWeekDataJob?.cancel()
        
        
        loadWeekDataJob = viewModelScope.launch {
            _isLoading.value = true
            
            try {
                val cacheKey = getCacheKey(year, week, supplier)
                val cachedProducts = weekDataCache[cacheKey]
                
                val products = if (cachedProducts != null) {
                    android.util.Log.d("ScamarkVM", "💾 Using cached data for $cacheKey")
                    cachedProducts
                } else {
                    android.util.Log.d("ScamarkVM", "🌐 Loading from Firebase for $cacheKey")
                    val loadStartTime = System.currentTimeMillis()
                    val loadedProducts = repository.getWeekDecisions(year, week, supplier)
                    android.util.Log.d("ScamarkVM", "✅ Firebase load completed in ${System.currentTimeMillis() - loadStartTime}ms, products=${loadedProducts.size}")
                    
                    // Mettre en cache
                    weekDataCache[cacheKey] = loadedProducts
                    
                    loadedProducts
                }
                
                
                // Assigner les données
                _allProducts.value = products
                
                // Charger la semaine précédente pour les comparaisons
                loadPreviousWeekForComparison(year, week, supplier)
                
                // Appliquer le filtre de recherche actuel
                filterProducts()
                
            } catch (e: Exception) {
                _error.value = "Erreur lors du chargement: ${e.message}"
            } finally {
                _isLoading.value = false
                
            }
        }
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
        
        
        val cacheKey = getCacheKey(year, week, supplier)
        val cachedProducts = weekDataCache[cacheKey]
        
        if (cachedProducts != null) {
            // Utilisation du cache - assigner directement sans requête réseau
            android.util.Log.d("ScamarkVM", "💾 loadWeekData: Using cache for $cacheKey, products=${cachedProducts.size}")
            
            // IMPORTANT: S'assurer que le loading est désactivé pour le cache
            _isLoading.value = false
            
            // Assigner directement les données (elvis operator pour satisfaire lint)
            _allProducts.value = cachedProducts ?: emptyList()
            val stats = calculateStatsFromProducts(cachedProducts ?: emptyList())
            _stats.value = stats
            
            // Charger S-1 en arrière-plan
            viewModelScope.launch {
                loadPreviousWeekForComparison(year, week, supplier)
                filterProducts()
            }
            return
        }

        // PAS DE CACHE: Activer loading et charger depuis repository
        loadWeekDataJob = viewModelScope.launch {
            _isLoadingWeekChange.value = true
            val startTime = System.currentTimeMillis()
            
            try {
                android.util.Log.d("ScamarkVM", "🌐 loadWeekData: Loading from Firebase for $cacheKey")
                val products = repository.getWeekDecisions(year, week, supplier)
                android.util.Log.d("ScamarkVM", "✅ Week data loaded in ${System.currentTimeMillis() - startTime}ms, products=${products.size}")
                
                // Mettre en cache
                weekDataCache[cacheKey] = products
                
                android.util.Log.d("ScamarkVM", "📊 Calculating stats...")
                val stats = calculateStatsFromProducts(products)
                
                _allProducts.value = products
                _stats.value = stats
                
                // Charger les produits de la semaine précédente pour comparaison
                loadPreviousWeekForComparison(year, week, supplier)
                
                // Appliquer le filtre de recherche actuel
                filterProducts()
                
            } catch (e: Exception) {
                _error.value = "Erreur lors du chargement: ${e.message}"
            } finally {
                _isLoadingWeekChange.value = false
                
            }
        }
    }
    
    /**
     * Change la semaine sélectionnée
     */
    fun selectWeek(year: Int, week: Int) {
        selectWeek(year, week, "unknown")
    }
    
    fun selectWeek(year: Int, week: Int, @Suppress("UNUSED_PARAMETER") source: String) {
        if (_selectedYear.value != year || _selectedWeek.value != week) {
            
            // Réinitialiser le filtre à "all" lors du changement de semaine
            _productFilter.value = "all"
            
            _selectedYear.value = year
            _selectedWeek.value = week
            loadWeekData()
            
        }
    }
    
    /**
     * Change le fournisseur sélectionné
     */
    fun selectSupplier(supplier: String) {
        android.util.Log.d("ScamarkVM", "🔄 selectSupplier: $supplier (current: ${_selectedSupplier.value})")
        if (_selectedSupplier.value != supplier) {
            val startTime = System.currentTimeMillis()
            // IMPORTANT: Vider immédiatement toutes les listes pour éviter l'affichage flash
            _allProducts.value = emptyList()
            _products.value = emptyList()
            _productFilter.value = "all"
            _searchQuery.value = ""
            _searchSuggestions.value = emptyList()
            
            // IMPORTANT: Réinitialiser les produits de la semaine précédente
            // pour éviter les comparaisons erronées entre fournisseurs différents
            previousWeekProducts = emptyList()
            
            // Marquer comme en cours de chargement pour masquer le contenu
            _isLoading.value = true
            
            // IMPORTANT: Définir le nouveau fournisseur
            _selectedSupplier.value = supplier
            android.util.Log.d("ScamarkVM", "⏳ Starting parallel load: weeks + data")
            
            // Charger en parallèle pour améliorer les performances
            viewModelScope.launch {
                // Délai très court pour permettre à l'UI de se mettre à jour avant le chargement
                kotlinx.coroutines.delay(50)
                
                // Lancer les deux chargements en parallèle
                val weeksDeferred = async(Dispatchers.IO) { 
                    android.util.Log.d("ScamarkVM", "🌐 Loading weeks in parallel...")
                    repository.getAvailableWeeks(supplier) 
                }
                val dataDeferred = async(Dispatchers.IO) { 
                    android.util.Log.d("ScamarkVM", "🌐 Loading week data in parallel...")
                    val year = _selectedYear.value ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val week = _selectedWeek.value ?: getCurrentISOWeek()
                    repository.getWeekDecisions(year, week, supplier)
                }
                
                // Attendre les résultats
                try {
                    val weeks = weeksDeferred.await()
                    val products = dataDeferred.await()
                    
                    android.util.Log.d("ScamarkVM", "✅ Parallel load completed in ${System.currentTimeMillis() - startTime}ms")
                    
                    // Mettre à jour les données de manière atomique
                    withContext(Dispatchers.Main) {
                        _availableWeeks.value = weeks
                        _allProducts.value = products
                        weekDataCache[getCacheKey(_selectedYear.value ?: 0, _selectedWeek.value ?: 0, supplier)] = products
                        
                        // Calculer les stats
                        val stats = calculateStatsFromProducts(products)
                        _stats.value = stats
                        
                        // Appliquer le filtre
                        filterProducts()
                    }
                    
                    // Charger S-1 en arrière-plan (sans bloquer)
                    launch(Dispatchers.IO) {
                        loadPreviousWeekForComparison(
                            _selectedYear.value ?: 0, 
                            _selectedWeek.value ?: 0, 
                            supplier
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ScamarkVM", "❌ Error in parallel load: ${e.message}")
                    _error.value = "Erreur lors du chargement: ${e.message}"
                } finally {
                    // Petit délai avant de masquer le loader pour éviter le clignotement
                    kotlinx.coroutines.delay(100)
                    _isLoading.value = false
                }
            }
            
            android.util.Log.d("ScamarkVM", "🚀 Supplier switch initiated in ${System.currentTimeMillis() - startTime}ms")
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
        _searchQuery.value = query ?: ""
        
        // Générer les suggestions si la requête n'est pas vide
        if (!query.isNullOrEmpty() && query.length >= 2) {
            generateSearchSuggestions(query)
        } else {
            _searchSuggestions.value = emptyList()
        }
        
        // Toujours filtrer les produits pour montrer les résultats en temps réel
        filterProducts()
    }
    
    /**
     * Génère des suggestions de recherche basées sur la requête
     */
    private fun generateSearchSuggestions(query: String) {
        val suggestions = mutableListOf<SearchSuggestion>()
        val queryLower = query.lowercase()
        val allProducts = _allProducts.value ?: emptyList()
        
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
        _searchSuggestions.value = finalSuggestions
    }
    
    /**
     * Applique une suggestion de recherche
     */
    fun applySuggestion(suggestion: SearchSuggestion) {
        
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
     * Filtre les produits entrants (nouveaux par rapport à S-1)
     */
    private fun filterEntrants(products: List<ScamarkProduct>): List<ScamarkProduct> {
        return products.filter { product ->
            getProductStatus(product.productName) == ProductStatus.ENTRANT
        }
    }
    
    /**
     * Filtre les produits sortants (disparus par rapport à S-1)
     */
    private fun filterSortants(@Suppress("UNUSED_PARAMETER") products: List<ScamarkProduct>): List<ScamarkProduct> {
        // Les sortants ne sont pas dans la liste courante, il faut les créer à partir de S-1
        return previousWeekProducts.filter { prevProduct ->
            val currentProducts = _allProducts.value ?: emptyList()
            !currentProducts.any { it.productName == prevProduct.productName }
        }
    }
    
    /**
     * Charge les produits de la semaine précédente pour comparaison
     */
    private suspend fun loadPreviousWeekForComparison(currentYear: Int, currentWeek: Int, supplier: String) {
        try {
            // Calculer la semaine précédente
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.YEAR, currentYear)
            calendar.set(Calendar.WEEK_OF_YEAR, currentWeek)
            calendar.add(Calendar.WEEK_OF_YEAR, -1)
            
            val prevYear = calendar.get(Calendar.YEAR)
            val prevWeek = calendar.get(Calendar.WEEK_OF_YEAR)
            
            
            // Charger les produits de la semaine précédente
            previousWeekProducts = repository.getWeekDecisions(prevYear, prevWeek, supplier)
            
            
        } catch (e: Exception) {
            previousWeekProducts = emptyList() // En cas d'erreur, pas de comparaison
        }
    }
    
    /**
     * Détermine le statut d'un produit par rapport à la semaine précédente
     */
    fun getProductStatus(productName: String): ProductStatus {
        // Pas de produits précédents = pas de comparaison possible
        if (previousWeekProducts.isEmpty()) {
            return ProductStatus.NEUTRAL
        }
        
        val currentProducts = _allProducts.value ?: emptyList()
        val isInCurrentWeek = currentProducts.any { it.productName == productName }
        val wasInPreviousWeek = previousWeekProducts.any { it.productName == productName }
        
        return when {
            isInCurrentWeek && !wasInPreviousWeek -> ProductStatus.ENTRANT  // Nouveau cette semaine
            !isInCurrentWeek && wasInPreviousWeek -> ProductStatus.SORTANT  // Disparu cette semaine  
            else -> ProductStatus.NEUTRAL  // Présent les 2 semaines ou absent les 2 semaines
        }
    }
    
    // Fonctions getPreviousWeek et getNextWeek supprimées
    
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
        
        // Nettoyer le cache MainActivity pour éviter la persistance des filtres
        clearMainActivityCache(activity)
        
        // Réinitialiser le filtre à "all" pour afficher tous les produits
        _productFilter.value = "all"
        
        // Réinitialiser la recherche
        _searchQuery.value = ""
        _isClientSearchMode.value = false
        
        // Plus de produits préchargés
        
        // Revenir à la semaine actuelle
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        _selectedYear.value = currentYear
        _selectedWeek.value = currentWeek
        
        // Recharger les données de la semaine actuelle avec l'indicateur de loading principal
        val supplier = _selectedSupplier.value ?: "all"
        loadAvailableWeeks(supplier)
        loadWeekDataWithMainLoading()
        
    }
    
    /**
     * Force le rechargement des données (sans paramètre, pour OverviewFragment)
     */
    fun refresh() {
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
        
        
        // Filtrer par fournisseur ET par année
        val supplierFilteredWeeks = if (currentSupplier == "all") {
            availableWeeksList.filter { it.year == currentDisplayYear }
        } else {
            availableWeeksList.filter { it.year == currentDisplayYear && it.supplier == currentSupplier }
        }
        
        
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
        
        
        for (week in finalMaxWeek downTo 1) {
            // Vérifier la disponibilité selon le fournisseur sélectionné
            val hasData = supplierFilteredWeeks.any { it.week == week }
            val isCurrentWeek = (currentDisplayYear == currentYear && week == currentWeek)
            val isSelected = (currentDisplayYear == selectedYear && week == selectedWeek)
            
            if (hasData) {
            }
            
            val weekItem = com.nextjsclient.android.ui.components.WeekItem(
                year = currentDisplayYear,
                week = week,
                isCurrentWeek = isCurrentWeek,
                isSelected = isSelected,
                hasData = hasData,
                isLoading = isWeekLoading(currentDisplayYear, week)
            )
            
            weekItems.add(weekItem)
        }
        
        
        return weekItems
    }
    
    /**
     * Vérification rapide de disponibilité d'une semaine spécifique
     */
    fun quickCheckWeekAvailability(week: Int, year: Int) {
        val supplier = _selectedSupplier.value ?: "all"
        
        viewModelScope.launch {
            try {
                // Vérifier directement cette semaine spécifique
                val isAvailable = repository.checkWeekAvailability(supplier, week, year)
                
                if (isAvailable) {
                    
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
                    }
                } else {
                }
            } catch (e: Exception) {
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
        
    }
    
    /**
     * Nettoie les semaines en chargement
     */
    private fun clearLoadingWeeks() {
        loadingWeeksSet.clear()
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
        
        // Définir le fournisseur sélectionné
        _selectedSupplier.value = supplier
        
        // Vérifier si nous recevons des produits sortants
        val currentFilter = _productFilter.value
        
        if (currentFilter == "sortants" && products.isNotEmpty()) {
            // Les produits reçus sont DÉJÀ les sortants filtrés depuis OverviewFragment
            // On les met directement dans _products sans passer par filterProducts
            android.util.Log.d("ScamarkVM", "📤 Setting sortants products directly, count=${products.size}")
            _allProducts.value = emptyList() // Pas de produits "all"
            _products.value = products // Directement les sortants
            
            // Ne PAS appeler filterProducts() pour les sortants, ils sont déjà filtrés
            return // Sortir sans appeler filterProducts()
        } else if (currentFilter == "entrants" && products.isNotEmpty()) {
            // Les produits reçus sont DÉJÀ les entrants filtrés depuis OverviewFragment
            // On les met directement dans _products sans passer par filterProducts
            android.util.Log.d("ScamarkVM", "📥 Setting entrants products directly, count=${products.size}")
            _allProducts.value = emptyList() // Pas de produits "all"
            _products.value = products // Directement les entrants
            
            // Ne PAS appeler filterProducts() pour les entrants, ils sont déjà filtrés
            return // Sortir sans appeler filterProducts()
        } else {
            // Charger directement les données sans appels réseau
            _allProducts.value = products
        }
        
        _availableWeeks.value = weeks
        
        // Calculer les stats
        val stats = calculateStatsFromProducts(products)
        _stats.value = stats
        
        // Appliquer les filtres
        filterProducts()
        
    }
    
    /**
     * Calcule les statistiques à partir d'une liste de produits
     */
    /**
     * Méthode pour définir directement les produits (utilisée par OverviewFragment)
     */
    fun setProducts(productsList: List<ScamarkProduct>) {
        _allProducts.value = productsList
        _products.value = productsList
        filterProducts()
    }
    
    /**
     * Force le rechargement spécifique à un fournisseur
     */
    fun forceReloadSupplierData(supplier: String) {
        android.util.Log.d("ScamarkVM", "🔄 forceReloadSupplierData: $supplier")
        
        // IMPORTANT: Vider immédiatement toutes les listes pour éviter l'affichage flash
        _allProducts.value = emptyList()
        _products.value = emptyList()
        
        // Réinitialiser les produits de la semaine précédente car on change de contexte
        previousWeekProducts = emptyList()
        _productFilter.value = "all"
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
        
        // Marquer comme en cours de chargement pour masquer le contenu
        _isLoading.value = true
        
        // Recharger les données spécifiques au fournisseur avec gestion du loading
        viewModelScope.launch {
            try {
                // Délai court pour l'animation
                kotlinx.coroutines.delay(50)
                
                // Chargement parallèle comme pour selectSupplier
                val weeksDeferred = async(Dispatchers.IO) { 
                    repository.getAvailableWeeks(supplier) 
                }
                val dataDeferred = async(Dispatchers.IO) { 
                    val year = _selectedYear.value ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val week = _selectedWeek.value ?: getCurrentISOWeek()
                    repository.getWeekDecisions(year, week, supplier)
                }
                
                val weeks = weeksDeferred.await()
                val products = dataDeferred.await()
                
                // Mettre à jour les données
                _availableWeeks.value = weeks
                _allProducts.value = products
                weekDataCache[getCacheKey(_selectedYear.value ?: 0, _selectedWeek.value ?: 0, supplier)] = products
                
                // Calculer les stats
                val stats = calculateStatsFromProducts(products)
                _stats.value = stats
                
                // Appliquer le filtre
                filterProducts()
                
                // Charger S-1 en arrière-plan
                launch(Dispatchers.IO) {
                    loadPreviousWeekForComparison(
                        _selectedYear.value ?: 0, 
                        _selectedWeek.value ?: 0, 
                        supplier
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ScamarkVM", "❌ Error in forceReload: ${e.message}")
                _error.value = "Erreur lors du rechargement: ${e.message}"
            } finally {
                // Délai avant de masquer le loader
                kotlinx.coroutines.delay(100)
                _isLoading.value = false
            }
        }
    }
    
    private fun calculateStatsFromProducts(products: List<ScamarkProduct>): ScamarkStats {
        val totalProducts = products.size
        val totalPromos = products.count { it.isPromo }
        val uniqueClients = products.flatMap { it.decisions.map { d -> d.codeClient } }.distinct().size
        
        // Plus de calcul entrants/sortants
        val productsIn = 0
        val productsOut = 0
        
        return ScamarkStats(
            totalProducts = totalProducts,
            activeClients = uniqueClients,
            productsIn = productsIn,
            productsOut = productsOut,
            totalPromos = totalPromos
        )
    }
}