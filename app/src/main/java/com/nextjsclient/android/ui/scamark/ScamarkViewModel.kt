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
    
    // Données complètes NON filtrées pour les calculs de totaux (aperçu)
    private val _allProductsUnfiltered = MutableLiveData<List<ScamarkProduct>>()
    val allProductsUnfiltered: LiveData<List<ScamarkProduct>> = _allProductsUnfiltered
    
    /**
     * Helper pour mettre à jour les données en gardant une copie non filtrée pour les totaux
     */
    private fun setAllProducts(products: List<ScamarkProduct>, keepUnfilteredBackup: Boolean = true) {
        _allProducts.value = products
        if (keepUnfilteredBackup) {
            // Garder une copie complète pour les calculs de totaux (aperçu)
            _allProductsUnfiltered.value = products
        }
    }
    
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
        
        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val weeks = repository.getAvailableWeeksForYear(supplier, year)
                
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
        
        viewModelScope.launch {
            _isLoadingWeeks.value = true
            val startTime = System.currentTimeMillis()
            
            try {
                // Utiliser Dispatchers.IO pour les opérations réseau/cache
                val weeks = withContext(Dispatchers.IO) {
                    repository.getAvailableWeeks(supplier)
                }
                
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
        
        
        // Marquer les prochaines semaines comme "en chargement"
        markWeeksAsLoading()
        
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMoreWeeks.postValue(true)
            // val startTime = System.currentTimeMillis() // Unused variable
            try {
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
                android.util.Log.w("ScamarkVM", "❌ Error loading more weeks: ${e.message}", e)
                // Ne pas afficher l'erreur à l'utilisateur si c'est juste un échec de chargement de plus de semaines
                // _error.postValue("Error loading more weeks: ${e.message}")
            } finally {
                // Nettoyer les semaines en chargement
                clearLoadingWeeks()
                _isLoadingMoreWeeks.postValue(false)
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
                    cachedProducts
                } else {
                    val loadStartTime = System.currentTimeMillis()
                    val loadedProducts = repository.getWeekDecisions(year, week, supplier)
                    
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
            
            // IMPORTANT: S'assurer que le loading est désactivé pour le cache
            _isLoading.value = false
            
            // Assigner directement les données (vérification non-null pour lint)
            _allProducts.value = cachedProducts ?: emptyList()
            val stats = calculateStatsFromProducts(cachedProducts)
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
            
            try {
                val products = repository.getWeekDecisions(year, week, supplier)
                
                // Mettre en cache
                weekDataCache[cacheKey] = products
                
                val stats = calculateStatsFromProducts(products)
                
                setAllProducts(products, keepUnfilteredBackup = true)
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
    fun selectSupplier(supplier: String, resetFilter: Boolean = false) {
        if (_selectedSupplier.value != supplier) {
            // IMPORTANT: Vider immédiatement toutes les listes pour éviter l'affichage flash ET le mélange de fournisseurs
            _allProducts.value = emptyList()
            _products.value = emptyList()
            // Réinitialiser aussi les données de la semaine précédente pour éviter le mélange
            previousWeekProducts = emptyList()
            // Réinitialiser le filtre SEULEMENT si explicitement demandé
            if (resetFilter) {
                _productFilter.value = "all"
            }
            _searchQuery.value = ""
            _searchSuggestions.value = emptyList()
            
            // IMPORTANT: Réinitialiser les produits de la semaine précédente
            // pour éviter les comparaisons erronées entre fournisseurs différents
            previousWeekProducts = emptyList()
            
            // Marquer comme en cours de chargement pour masquer le contenu
            _isLoading.value = true
            
            // IMPORTANT: Définir le nouveau fournisseur
            _selectedSupplier.value = supplier
            
            // Charger en parallèle pour améliorer les performances
            viewModelScope.launch {
                // Délai très court pour permettre à l'UI de se mettre à jour avant le chargement
                kotlinx.coroutines.delay(10) // OPTIMISATION: Réduit de 50ms à 10ms
                
                // Lancer les deux chargements en parallèle
                val weeksDeferred = async(Dispatchers.IO) { 
                    repository.getAvailableWeeks(supplier)
                }
                val dataDeferred = async(Dispatchers.IO) { 
                    val year = _selectedYear.value ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
                    val week = _selectedWeek.value ?: getCurrentISOWeek()
                    repository.getWeekDecisions(year, week, supplier)
                }
                
                // Attendre les résultats
                try {
                    val weeks = weeksDeferred.await()
                    val products = dataDeferred.await()
                    
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
                    _error.value = "Erreur lors du chargement: ${e.message}"
                } finally {
                    // Petit délai avant de masquer le loader pour éviter le clignotement
                    kotlinx.coroutines.delay(25) // OPTIMISATION: Réduit de 100ms à 25ms
                    _isLoading.value = false
                }
            }
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
        val currentSupplier = _selectedSupplier.value ?: "all"
        
        var filtered = allProducts
        
        // IMPORTANT: Filtrer par fournisseur EN PREMIER pour éviter le mélange
        if (currentSupplier != "all") {
            val beforeSupplierFilter = filtered.size
            filtered = filtered.filter { it.supplier.lowercase() == currentSupplier.lowercase() }
        }
        
        // Appliquer le filtre de type
        filtered = when (filter) {
            "promo" -> {
                filtered.filter { it.isPromo }
            }
            "entrants" -> {
                val result = filterEntrants(filtered)
                result
            }
            "sortants" -> {
                // Si nous avons des données préchargées, elles sont DÉJÀ les sortants
                val result = if (filtered.isNotEmpty() && previousWeekProducts.isEmpty()) {
                    // Données préchargées depuis l'aperçu - les produits sont déjà les sortants
                    filtered
                } else {
                    // Navigation normale - calculer les sortants depuis previousWeekProducts
                    filterSortants(filtered)
                }
                result
            }
            else -> {
                filtered
            }
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
        
        // CORRECTION: Réinitialiser le filtre lors du refresh pour revenir à l'affichage normal
        _productFilter.value = "all"
        
        // Réinitialiser la recherche
        _searchQuery.value = ""
        _isClientSearchMode.value = false
        
        // Plus de produits préchargés
        
        // Revenir à la semaine actuelle
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        
        // Réinitialiser la semaine sélectionnée à la semaine actuelle
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
            // Les produits reçus sont les sortants (produits de la semaine précédente)
            // On les met dans _allProducts et on laisse le filtrage normal fonctionner
            setAllProducts(products, keepUnfilteredBackup = false) // Pas de backup pour les données filtrées
            _products.value = products // Directement les sortants pour l'affichage immédiat
            
            // Appliquer filterProducts() pour que les couleurs soient correctes
            filterProducts()
            return
        } else if (currentFilter == "entrants" && products.isNotEmpty()) {
            // Les produits reçus sont DÉJÀ les entrants filtrés depuis OverviewFragment
            // On les met directement dans _products sans passer par filterProducts
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
     * CORRECTION: Remet le ViewModel dans un état propre pour l'aperçu
     */
    fun setProducts(productsList: List<ScamarkProduct>) {
        
        // IMPORTANT: Remettre le ViewModel dans un état propre pour l'aperçu
        _selectedSupplier.value = "all"
        _productFilter.value = "all"
        _searchQuery.value = ""
        _isClientSearchMode.value = false
        _searchSuggestions.value = emptyList()
        
        // Nettoyer les données de comparaison
        previousWeekProducts = emptyList()
        
        // Définir les produits
        _allProducts.value = productsList
        _products.value = productsList
        
        // Pas de filtrage nécessaire car on vient de tout remettre à "all"
    }
    
    /**
     * Force le rechargement spécifique à un fournisseur
     */
    fun forceReloadSupplierData(supplier: String, resetFilter: Boolean = false) {
        
        // IMPORTANT: Vider immédiatement toutes les listes pour éviter l'affichage flash
        _allProducts.value = emptyList()
        _products.value = emptyList()
        
        // Réinitialiser les produits de la semaine précédente car on change de contexte
        previousWeekProducts = emptyList()
        // Réinitialiser le filtre SEULEMENT si explicitement demandé
        if (resetFilter) {
            _productFilter.value = "all"
        }
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