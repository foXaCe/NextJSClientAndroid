package com.nextjsclient.android.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nextjsclient.android.data.models.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope

class FirebaseRepository {
    
    private val auth: FirebaseAuth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore
    
    // Cache pour la semaine actuelle et les semaines disponibles - SÉPARÉ PAR FOURNISSEUR
    companion object {
        private data class CacheEntry(
            val data: List<ScamarkProduct>,
            val timestamp: Long,
            val year: Int,
            val week: Int,
            val supplier: String
        )
        
        private data class WeeksCacheEntry(
            val weeks: List<AvailableWeek>,
            val timestamp: Long,
            val supplier: String
        )
        
        private data class StatsEntry(
            val stats: ScamarkStats,
            val timestamp: Long,
            val year: Int,
            val week: Int,
            val supplier: String
        )
        
        // Cache séparé par fournisseur avec clés distinctes
        private val weekCache = mutableMapOf<String, CacheEntry>() // "$year-$week-$supplier"
        private val weeksCache = mutableMapOf<String, WeeksCacheEntry>() // "weeks-$supplier"
        private val statsCache = mutableMapOf<String, StatsEntry>() // "$year-$week-$supplier"
        private val articlesCache = mutableMapOf<String, Pair<Map<String, Article>, Long>>()
        private val clientsCache = mutableMapOf<String, Pair<Map<String, ClientInfo>, Long>>()
        
        // Cache plus long pour les données statiques
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes pour semaines et produits
        private const val STATIC_CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes pour articles/clients
        private const val STATS_CACHE_DURATION_MS = 2 * 60 * 1000L // 2 minutes pour stats
        
        fun clearCache() {
            weekCache.clear()
            weeksCache.clear()
            statsCache.clear()
            articlesCache.clear()
            clientsCache.clear()
        }
        
        fun clearSupplierCache(supplier: String) {
            
            // Nettoyer les entrées spécifiques au fournisseur
            val weekKeysToRemove = weekCache.keys.filter { it.endsWith("-$supplier") }
            weekKeysToRemove.forEach { weekCache.remove(it) }
            
            val weeksKeyToRemove = "weeks-$supplier"
            weeksCache.remove(weeksKeyToRemove)
            
            val statsKeysToRemove = statsCache.keys.filter { it.endsWith("-$supplier") }
            statsKeysToRemove.forEach { statsCache.remove(it) }
            
        }
        
        fun getCacheInfo(): String {
            return "Cache: ${weekCache.size} weeks, ${weeksCache.size} weeksList, ${statsCache.size} stats, ${articlesCache.size} articles, ${clientsCache.size} clients"
        }
    }
    
    // Authentication
    suspend fun signIn(email: String, password: String): Result<String> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user?.uid ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun signUp(email: String, password: String): Result<String> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(result.user?.uid ?: "")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun signOut() {
        auth.signOut()
    }
    
    fun getCurrentUser() = auth.currentUser
    
    fun isLoggedIn() = auth.currentUser != null
    
    // Scamark - Nouvelle architecture conforme au Next.js
    
    /**
     * Récupère les semaines disponibles pour un fournisseur - CHARGE TOUTE L'ANNÉE JUSQU'À S+1 (semaine suivante)
     */
    suspend fun getAvailableWeeks(supplier: String = "all"): List<AvailableWeek> {
        
        // Vérifier le cache
        val cacheKey = "weeks-$supplier"
        weeksCache[cacheKey]?.let { cacheEntry ->
            val cacheAge = System.currentTimeMillis() - cacheEntry.timestamp
            if (cacheAge < CACHE_DURATION_MS) {
                return cacheEntry.weeks
            } else {
                weeksCache.remove(cacheKey)
            }
        }
        
        val availableWeeks = mutableListOf<AvailableWeek>()
        val suppliers = if (supplier == "all") listOf("anecoop", "solagora") else listOf(supplier)
        
        try {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val currentWeek = getCurrentISOWeek()
            
            // Chercher TOUTE l'année courante jusqu'à S+1 (semaine suivante parfois disponible)
            val startWeek = 1
            val endWeek = minOf(52, currentWeek + 1)  // Inclure S+1 car parfois dispo en avance
            val weekRange = startWeek..endWeek
            
            // Paralléliser les requêtes Firestore pour tous les fournisseurs
            
            kotlinx.coroutines.coroutineScope {
                val deferredQueries = suppliers.flatMap { sup ->
                    weekRange.map { week ->
                        async {
                            val weekStr = week.toString().padStart(2, '0')
                            val collectionPath = "decisions_$sup/$currentYear/$weekStr"
                            
                            try {
                                val snapshot = firestore.collection(collectionPath)
                                    .limit(1)
                                    .get()
                                    .await()
                                
                                if (!snapshot.isEmpty) {
                                    Triple(AvailableWeek(currentYear, week, sup), 0L, true)
                                } else {
                                    Triple(null, 0L, false)
                                }
                            } catch (e: Exception) {
                                Triple(null, 0L, false)
                            }
                        }
                    }
                }
                
                // Attendre toutes les requêtes et collecter les résultats
                val results = deferredQueries.awaitAll()
                results.forEach { (week, _, _) ->
                    week?.let { 
                        availableWeeks.add(it)
                    }
                }
            }
            
            
        } catch (e: Exception) {
            // Retourner une liste de fallback
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            return listOf(
                AvailableWeek(currentYear, getCurrentISOWeek(), "anecoop"),
                AvailableWeek(currentYear, getCurrentISOWeek(), "solagora")
            )
        }
        
        val sortedWeeks = availableWeeks.sortedWith(compareByDescending<AvailableWeek> { it.year }
            .thenByDescending { it.week }
            .thenBy { it.supplier })
        
        // Mettre en cache
        weeksCache[cacheKey] = WeeksCacheEntry(
            weeks = sortedWeeks,
            timestamp = System.currentTimeMillis(),
            supplier = supplier
        )
        
        
        return sortedWeeks
    }
    
    /**
     * Récupère plus de semaines à partir d'une semaine donnée - CONTINUE À REBOURS
     */
    suspend fun getExtendedAvailableWeeksFromWeek(supplier: String = "all", fromWeek: Int, fromYear: Int? = null): List<AvailableWeek> {
        val startYear = fromYear ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        
        
        // VALIDATION: Chercher SEULEMENT dans l'année courante
        if (startYear != java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) {
            return emptyList()
        }
        
        val availableWeeks = mutableListOf<AvailableWeek>()
        val suppliers = if (supplier == "all") listOf("anecoop", "solagora") else listOf(supplier)
        
        
        // Calculer la semaine de départ
        val searchWeek = maxOf(1, fromWeek - 1)
        
        try {
            for (sup in suppliers) {
                
                var foundCount = 0
                var batchStart = searchWeek
                val batchSize = 6 // Batch plus large pour réduire les appels
                var shouldStopSearch = false
                
                
                while (batchStart >= 1 && !shouldStopSearch) {
                    val batchEnd = maxOf(1, batchStart - batchSize + 1)
                    
                    
                    var batchFound = 0
                    var emptyWeeksInBatch = 0
                    
                    for (week in batchStart downTo batchEnd) {
                        // Plus de limite artificielle - on charge toutes les semaines disponibles
                        
                        val weekStr = week.toString().padStart(2, '0')
                        val collectionPath = "decisions_$sup/$startYear/$weekStr"
                        
                        try {
                            val snapshot = firestore.collection(collectionPath)
                                .limit(1)
                                .get()
                                .await()
                            
                            if (!snapshot.isEmpty) {
                                availableWeeks.add(AvailableWeek(startYear, week, sup))
                                foundCount++
                                batchFound++
                            } else {
                                emptyWeeksInBatch++
                                
                                // ARRÊT IMMÉDIAT: Si on n'a trouvé aucune donnée et qu'on trouve une semaine vide,
                                // arrêter immédiatement car les semaines précédentes seront probablement vides aussi
                                if (foundCount == 0 && emptyWeeksInBatch >= 1) {
                                    shouldStopSearch = true
                                    break
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                    
                    
                    
                    // Si aucune semaine trouvée dans ce batch et qu'on avait déjà des données, arrêter
                    if (batchFound == 0 && foundCount > 0) {
                        shouldStopSearch = true
                    }
                    
                    // Si plus de 3 semaines vides consécutives dans ce batch, arrêter aussi
                    if (emptyWeeksInBatch >= 3) {
                        shouldStopSearch = true
                    }
                    
                    
                    batchStart = batchEnd - 1
                }
                
                
            }
        } catch (e: Exception) {
            return emptyList()
        }
        
        // Tri final
        val sortedWeeks = availableWeeks.sortedWith(
            compareByDescending<AvailableWeek> { it.year }
                .thenByDescending { it.week }
                .thenBy { it.supplier }
        )
        
        
        
        return sortedWeeks
    }
    
    /**
     * Vérification rapide de disponibilité d'une semaine spécifique
     */
    suspend fun checkWeekAvailability(supplier: String, week: Int, year: Int): Boolean {
        
        try {
            val suppliers = if (supplier == "all") listOf("anecoop", "solagora") else listOf(supplier)
            
            for (sup in suppliers) {
                val weekStr = week.toString().padStart(2, '0')
                val collectionPath = "decisions_$sup/$year/$weekStr"
                
                val snapshot = firestore.collection(collectionPath)
                    .limit(1)
                    .get()
                    .await()
                
                if (!snapshot.isEmpty) {
                    return true
                } else {
                }
            }
            
            return false
            
        } catch (e: Exception) {
            return false
        }
    }
    
    
    /**
     * Récupère les décisions d'une semaine spécifique avec enrichissement
     */
    suspend fun getWeekDecisions(year: Int, week: Int, supplier: String = "all"): List<ScamarkProduct> {
        
        // Vérifier le cache pour la semaine actuelle
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        val cacheKey = "$year-$week-$supplier"
        
        if (year == currentYear && week == currentWeek) {
            weekCache[cacheKey]?.let { cacheEntry ->
                val cacheAge = System.currentTimeMillis() - cacheEntry.timestamp
                if (cacheAge < CACHE_DURATION_MS) {
                    return cacheEntry.data
                } else {
                    weekCache.remove(cacheKey)
                }
            }
        }
        
        val suppliers = if (supplier == "all") listOf("anecoop", "solagora") else listOf(supplier)
        val weekStr = week.toString().padStart(2, '0')
        val allDecisions = mutableListOf<ScamarkDecision>()
        
        // 1. Charger les décisions brutes
        for (sup in suppliers) {
            try {
                val collectionPath = "decisions_$sup/$year/$weekStr"
                
                val snapshot = firestore.collection(collectionPath).get().await()
                
                
                snapshot.documents.forEach { doc ->
                    val decision = doc.toObject(ScamarkDecision::class.java)
                    decision?.let {
                        allDecisions.add(it.copy(supplier = sup))
                    }
                }
                
            } catch (e: Exception) {
            }
            
        }
        
        
        if (allDecisions.isEmpty()) {
            return emptyList()
        }
        
        // 2. Charger les articles pour enrichissement avec cache amélioré
        val productCodes = allDecisions.map { it.codeProduit }.distinct()
        val articlesMap = mutableMapOf<String, Article>()
        
        // Vérifier le cache articles
        val articlesCacheKey = suppliers.sorted().joinToString(",")
        val cachedArticles = articlesCache[articlesCacheKey]
        val articlesFromCache = cachedArticles?.let { (articles, timestamp) ->
            val age = System.currentTimeMillis() - timestamp
            if (age < STATIC_CACHE_DURATION_MS) {
                articles
            } else {
                articlesCache.remove(articlesCacheKey)
                null
            }
        }
        
        val allArticles = articlesFromCache ?: run {
            val articlesFromFirestore = mutableMapOf<String, Article>()
            
            try {
                val articlesSnapshot = firestore.collectionGroup("articles").get().await()
                
                articlesSnapshot.documents.forEach { doc ->
                    val article = doc.toObject(Article::class.java)
                    article?.let {
                        articlesFromFirestore[it.codeProduit] = it
                    }
                }
                
                // Mettre en cache
                articlesCache[articlesCacheKey] = Pair(articlesFromFirestore, System.currentTimeMillis())
                
            } catch (e: Exception) {
            }
            
            articlesFromFirestore
        }
        
        // Filtrer pour les codes produits nécessaires
        productCodes.forEach { code ->
            allArticles[code]?.let { articlesMap[code] = it }
        }
        
        
        // 3. Charger les informations clients avec cache amélioré
        val clientCodes = allDecisions.flatMap { it.scas.map { sca -> sca.codeClient } }.distinct()
        val clientsMap = mutableMapOf<String, ClientInfo>()
        
        // Vérifier le cache clients
        val clientsCacheKey = "all_clients"
        val cachedClients = clientsCache[clientsCacheKey]
        val clientsFromCache = cachedClients?.let { (clients, timestamp) ->
            val age = System.currentTimeMillis() - timestamp
            if (age < STATIC_CACHE_DURATION_MS) {
                clients
            } else {
                clientsCache.remove(clientsCacheKey)
                null
            }
        }
        
        val allClients = clientsFromCache ?: run {
            val clientsFromFirestore = mutableMapOf<String, ClientInfo>()
            
            try {
                val clientsSnapshot = firestore.collection("clients").get().await()
                
                clientsSnapshot.documents.forEach { doc ->
                    val client = doc.toObject(ClientInfo::class.java)
                    client?.let {
                        // Stocker par documentId principal
                        clientsFromFirestore[it.documentId] = it
                        // Stocker aussi par id alternatif si différent
                        if (it.id.isNotEmpty() && it.id != it.documentId) {
                            clientsFromFirestore[it.id] = it
                        }
                    }
                }
                
                // Mettre en cache
                clientsCache[clientsCacheKey] = Pair(clientsFromFirestore, System.currentTimeMillis())
                
            } catch (e: Exception) {
            }
            
            clientsFromFirestore
        }
        
        // Matching optimisé avec les codes clients nécessaires
        var matchedClients = 0
        clientCodes.forEach { codeClient ->
            // Recherche directe
            allClients[codeClient]?.let {
                clientsMap[codeClient] = it.copy(sca = codeClient)
                matchedClients++
                return@forEach
            }
            
            // Recherche par similarité (plus coûteuse, donc après la recherche directe)
            allClients.values.find { client ->
                codeClient.startsWith(client.documentId) || 
                client.documentId.startsWith(codeClient.split(" ")[0]) ||
                (client.nomClient?.isNotEmpty() == true && 
                 (codeClient.contains(client.nomClient) || client.nomClient.contains(codeClient)))
            }?.let { client ->
                clientsMap[codeClient] = client.copy(sca = codeClient)
                matchedClients++
            }
        }
        
        
        
        
        // 4. Traitement et enrichissement
        val finalProducts = allDecisions.map { decision ->
            val article = articlesMap[decision.codeProduit]
            
            // Détecter les promotions
            val hasPromoFlag = decision.isPromo
            val hasPromoComment = decision.commentaire?.lowercase()?.let { comment ->
                comment.contains("promo") || comment.contains("promotion") ||
                comment.contains("solde") || comment.contains("remise") ||
                comment.contains("offre") || comment.contains("spécial")
            } ?: false
            
            val finalIsPromo = hasPromoFlag || hasPromoComment
            
            // Enrichir les décisions clients
            val enrichedDecisions = decision.scas.map { sca ->
                val client = clientsMap[sca.codeClient]
                
                val clientName = if (client?.nom?.isNotEmpty() == true) {
                    client.nom
                } else {
                    sca.codeClient
                }
                
                ClientDecision(
                    codeClient = sca.codeClient,
                    nomClient = clientName,
                    codeProduit = decision.codeProduit,
                    libelle = decision.libelle,
                    prixRetenu = decision.prixRetenu,
                    prixOffert = decision.prixOffert,
                    clientInfo = client
                )
            }
            
            ScamarkProduct(
                productName = article?.nom ?: decision.libelle,
                supplier = decision.supplier,
                prixRetenu = decision.prixRetenu,
                prixOffert = decision.prixOffert,
                isPromo = finalIsPromo,
                articleInfo = article ?: Article(
                    nom = decision.libelle,
                    libelle = decision.libelle,
                    marque = decision.marque,
                    origine = decision.origine,
                    categorie = decision.categorie,
                    codeProduit = decision.codeProduit,
                    supplier = decision.supplier
                ),
                decisions = enrichedDecisions,
                totalScas = decision.totalScas.takeIf { it > 0 } ?: decision.scas.size
            )
        }
        
        val sortedProducts = finalProducts.sortedBy { it.productName }
        
        // Mettre en cache si c'est la semaine actuelle
        if (year == currentYear && week == currentWeek) {
            weekCache[cacheKey] = CacheEntry(
                data = sortedProducts,
                timestamp = System.currentTimeMillis(),
                year = year,
                week = week,
                supplier = supplier
            )
        }
        
        
        return sortedProducts
    }
    
    /**
     * Calcule les statistiques de la semaine
     */
    suspend fun getWeekStats(year: Int, week: Int, supplier: String = "all"): ScamarkStats {
        val products = getWeekDecisions(year, week, supplier)
        val uniqueClients = products.flatMap { it.decisions.map { d -> d.codeClient } }.distinct()
        val totalPromos = products.count { it.isPromo }
        
        // Pour productsIn/Out, il faudrait comparer avec la semaine précédente
        // Implémentation simplifiée pour l'instant - on peut utiliser une logique basique
        val previousWeek = if (week > 1) week - 1 else 52
        val previousYear = if (week > 1) year else year - 1
        
        var productsIn = 0
        var productsOut = 0
        
        try {
            val previousProducts = getWeekDecisions(previousYear, previousWeek, supplier)
            val currentProductNames = products.map { it.productName }.toSet()
            val previousProductNames = previousProducts.map { it.productName }.toSet()
            
            productsIn = (currentProductNames - previousProductNames).size
            productsOut = (previousProductNames - currentProductNames).size
        } catch (e: Exception) {
            // Si impossible de charger la semaine précédente, garder 0
        }
        
        return ScamarkStats(
            totalProducts = products.size,
            activeClients = uniqueClients.size,
            productsIn = productsIn,
            productsOut = productsOut,
            totalPromos = totalPromos
        )
    }
    
    /**
     * Calcule la semaine ISO courante
     */
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
    
    /**
     * Récupère le profil d'un utilisateur commercial depuis Firebase
     */
    suspend fun getUserProfile(email: String): UserProfile? {
        return try {
            val snapshot = firestore.collection(UserProfile.COLLECTION_NAME)
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()
            
            if (snapshot.documents.isNotEmpty()) {
                snapshot.documents[0].toObject(UserProfile::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Error fetching user profile: ${e.message}", e)
            null
        }
    }
    
    // Scafel Articles
    fun getScafelArticles(): Flow<List<ScafelArticle>> = callbackFlow {
        val listener = firestore.collection("scafel_articles")
            .orderBy("designation")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val articles = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ScafelArticle::class.java)
                } ?: emptyList()
                
                trySend(articles)
            }
        
        awaitClose { listener.remove() }
    }
    
    fun searchScafelArticles(query: String): Flow<List<ScafelArticle>> = callbackFlow {
        val listener = firestore.collection("scafel_articles")
            .orderBy("designation")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val articles = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ScafelArticle::class.java)
                } ?: emptyList()
                
                trySend(articles)
            }
        
        awaitClose { listener.remove() }
    }
    
    suspend fun addScafelArticle(article: ScafelArticle): Result<String> {
        return try {
            val docRef = firestore.collection("scafel_articles").add(article).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun updateScafelArticle(article: ScafelArticle): Result<Unit> {
        return try {
            firestore.collection("scafel_articles")
                .document(article.id)
                .set(article)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Récupère l'historique complet d'un produit depuis le 01 octobre pour calculer les statistiques réelles
     */
    suspend fun getProductHistorySinceOctober(productName: String, supplier: String, productCode: String? = null, selectedYear: Int? = null, selectedWeek: Int? = null): ProductPalmares {
        
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        
        // Utiliser la semaine sélectionnée ou la semaine actuelle par défaut
        val targetYear = selectedYear ?: currentYear
        val targetWeek = selectedWeek ?: currentWeek
        
        
        // Calculer TOUTES les semaines depuis le 01 octobre 2024 (semaine 40) 
        // SANS limitation - on scanne TOUTES les collections Firebase
        val octoberFirstWeek = 40
        val octoberYear = 2024 // Octobre 2024 est notre point de départ fixe
        val weeksToCheck = mutableListOf<Pair<Int, Int>>() // Pair(year, week)
        
        
        // D'abord TOUTES les semaines d'octobre à décembre 2024
        for (week in octoberFirstWeek..52) {
            weeksToCheck.add(Pair(octoberYear, week))
        }
        
        // Puis TOUTES les semaines de 2025 depuis janvier jusqu'à la semaine sélectionnée
        if (targetYear > octoberYear) {
            for (week in 1..targetWeek) {
                weeksToCheck.add(Pair(targetYear, week))
            }
        }
        
        
        val referencedWeeks = mutableListOf<Pair<Int, Int>>()
        var totalQueries = 0
        
        try {
            
            
            // OPTIMISATION : Requêtes PARALLÈLES au lieu de séquentielles
            
            kotlinx.coroutines.coroutineScope {
                val deferredQueries = weeksToCheck.map { (year, week) ->
                    async {
                        val weekStr = week.toString().padStart(2, '0')
                        val collectionPath = "decisions_$supplier/$year/$weekStr"
                        totalQueries++
                        
                        try {
                            var snapshot: com.google.firebase.firestore.QuerySnapshot? = null
                            
                            // Si on a un code produit, l'utiliser en priorité (plus fiable)
                            if (!productCode.isNullOrBlank()) {
                                snapshot = firestore.collection(collectionPath)
                                    .whereEqualTo("codeProduit", productCode)
                                    .limit(1)
                                    .get()
                                    .await()
                            }
                            
                            // Si pas trouvé par code, essayer par nom du produit
                            if (snapshot?.isEmpty != false) {
                                snapshot = firestore.collection(collectionPath)
                                    .whereEqualTo("libelle", productName)
                                    .limit(1)
                                    .get()
                                    .await()
                            }
                            
                            
                            if (snapshot?.isEmpty == false) {
                                Triple(Pair(year, week), true, 0L)
                            } else {
                                Triple(Pair(year, week), false, 0L)
                            }
                        } catch (e: Exception) {
                            Triple(Pair(year, week), false, 0L)
                        }
                    }
                }
                
                // Attendre toutes les requêtes et traiter les résultats
                val results = deferredQueries.awaitAll()
                
                results.forEach { (weekPair, found, _) ->
                    if (found) {
                        referencedWeeks.add(weekPair)
                    }
                }
                
            }
        } catch (e: Exception) {
        }
        
        // Calculer les semaines consécutives depuis la fin (semaine actuelle)
        var consecutiveWeeks = 0
        
        if (referencedWeeks.isNotEmpty()) {
            // Trier par année puis semaine (ordre chronologique)
            val sortedWeeks = referencedWeeks.sortedWith(compareBy({ it.first }, { it.second }))
            
            
            // Partir de la semaine sélectionnée et remonter pour compter les consécutives
            var checkYear = targetYear
            var checkWeek = targetWeek
            
            
            while (true) {
                // Vérifier si cette semaine est dans la liste des semaines référencées
                val found = sortedWeeks.contains(Pair(checkYear, checkWeek))
                
                if (found) {
                    consecutiveWeeks++
                } else {
                    // Dès qu'on trouve une semaine manquante, on arrête
                    break
                }
                
                // Passer à la semaine précédente
                if (checkWeek > 1) {
                    checkWeek--
                } else {
                    checkWeek = 52
                    checkYear--
                }
                
                // Sécurité : ne pas remonter avant octobre 2024
                if (checkYear < octoberYear || 
                    (checkYear == octoberYear && checkWeek < octoberFirstWeek)) {
                    break
                }
                
                // Sécurité : limite à 52 semaines pour éviter boucle infinie
                if (consecutiveWeeks >= 52) {
                    break
                }
            }
            
        } else {
        }
        
        val totalReferences = referencedWeeks.size
        
        // Calculer le pourcentage des semaines depuis octobre jusqu'à la semaine actuelle
        val totalWeeksSinceOctober = weeksToCheck.size
        val percentage = if (totalWeeksSinceOctober > 0) {
            (totalReferences * 100) / totalWeeksSinceOctober
        } else {
            0
        }
        
        
        val result = ProductPalmares(
            consecutiveWeeks = consecutiveWeeks,
            totalReferences = totalReferences,
            referencedWeeks = referencedWeeks,
            percentage = percentage
        )
        
        
        return result
    }
    
    // Ancienne méthode dépréciée - gardée pour compatibilité
    /**
     * Charge les semaines disponibles pour une année spécifique
     */
    suspend fun getAvailableWeeksForYear(supplier: String = "all", year: Int): List<AvailableWeek> {
        
        val availableWeeks = mutableListOf<AvailableWeek>()
        val suppliers = if (supplier == "all") listOf("anecoop", "solagora") else listOf(supplier)
        
        try {
            // Chercher toutes les semaines de l'année (1-52)
            val weekRange = 1..52
            
            kotlinx.coroutines.coroutineScope {
                val deferredQueries = suppliers.flatMap { sup ->
                    weekRange.map { week ->
                        async {
                            val weekStr = week.toString().padStart(2, '0')
                            val collectionPath = "decisions_$sup/$year/$weekStr"
                            
                            try {
                                val snapshot = firestore.collection(collectionPath)
                                    .limit(1)
                                    .get()
                                    .await()
                                
                                if (!snapshot.isEmpty) {
                                    AvailableWeek(year, week, sup)
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
                
                val results = deferredQueries.awaitAll()
                results.filterNotNull().forEach { week ->
                    availableWeeks.add(week)
                }
            }
            
            val sortedWeeks = availableWeeks.sortedWith(compareByDescending<AvailableWeek> { it.year }.thenByDescending { it.week })
            
            
            return sortedWeeks
            
        } catch (e: Exception) {
            return emptyList()
        }
    }

    suspend fun getAvailableWeeksOld(): List<String> {
        return try {
            val snapshot = firestore.collection("scamark_products")
                .get()
                .await()
            
            snapshot.documents
                .mapNotNull { it.getString("week") }
                .distinct()
                .sorted()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Récupère l'historique des ruptures pour un produit spécifique
     */
    suspend fun getRuptureHistoryForProduct(codeProduit: String, supplier: String): List<RuptureHistory> {
        return try {
            android.util.Log.d("RuptureHistory", "=== DÉBUT getRuptureHistoryForProduct ===")
            android.util.Log.d("RuptureHistory", "Recherche pour codeProduit: '$codeProduit', supplier: '$supplier'")
            
            val ruptureHistory = mutableListOf<RuptureHistory>()
            
            // Requête directe sur le document du produit spécifique
            android.util.Log.d("RuptureHistory", "Tentative de lecture du document: ruptures-history/$codeProduit")
            val docSnapshot = firestore.collection("ruptures-history")
                .document(codeProduit)
                .get()
                .await()
            
            android.util.Log.d("RuptureHistory", "Document existe: ${docSnapshot.exists()}")
            if (!docSnapshot.exists()) {
                android.util.Log.w("RuptureHistory", "Aucun document trouvé pour le code produit: $codeProduit")
                return emptyList()
            }
            
            val data = docSnapshot.data
            android.util.Log.d("RuptureHistory", "Données du document: ${data?.keys}")
            if (data == null) {
                android.util.Log.w("RuptureHistory", "Données du document null")
                return emptyList()
            }
            
            // Note: Le supplier n'est pas au niveau racine du document, 
            // mais dans chaque rupture individuelle - on filtrera plus tard
            
            @Suppress("UNCHECKED_CAST")
            val ruptures = (data["ruptures"] as? List<Map<String, Any>>) ?: emptyList()
            android.util.Log.d("RuptureHistory", "Nombre de ruptures trouvées: ${ruptures.size}")
            
            // Parser chaque rupture
            for ((index, ruptureMap) in ruptures.withIndex()) {
                android.util.Log.d("RuptureHistory", "--- Parsing rupture $index ---")
                android.util.Log.d("RuptureHistory", "Rupture keys: ${ruptureMap.keys}")
                
                // Vérifier le fournisseur au niveau de chaque rupture
                val ruptureSupplier = ruptureMap["supplier"] as? String
                android.util.Log.d("RuptureHistory", "Supplier de cette rupture: '$ruptureSupplier', recherché: '$supplier'")
                
                if (ruptureSupplier?.lowercase() != supplier.lowercase()) {
                    android.util.Log.d("RuptureHistory", "Rupture ignorée, fournisseur ne correspond pas")
                    continue
                }
                
                val timestamp = (ruptureMap["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                val annee = (ruptureMap["annee"] as? Number)?.toInt() ?: 0
                val semaine = (ruptureMap["semaine"] as? Number)?.toInt() ?: 0
                val reportId = ruptureMap["reportId"] as? String ?: ""
                
                android.util.Log.d("RuptureHistory", "Timestamp: $timestamp, Année: $annee, Semaine: $semaine")
                
                @Suppress("UNCHECKED_CAST")
                val scas = (ruptureMap["scas"] as? List<Map<String, Any>>) ?: emptyList()
                android.util.Log.d("RuptureHistory", "Nombre de SCAs: ${scas.size}")
                
                // Calculer les totaux
                var totalCommande = 0
                var totalLivre = 0
                var totalManquant = 0
                
                val scasAffected = scas.map { scaMap ->
                    val quantiteCommande = (scaMap["quantiteCommande"] as? Number)?.toInt() ?: 0
                    val quantiteLivre = (scaMap["quantiteLivre"] as? Number)?.toInt() ?: 0
                    val quantiteManquant = (scaMap["quantiteManquant"] as? Number)?.toInt() ?: 0
                    
                    totalCommande += quantiteCommande
                    totalLivre += quantiteLivre
                    totalManquant += quantiteManquant
                    
                    ScaAffected(
                        codeClient = scaMap["codeClient"] as? String ?: "",
                        clientName = scaMap["clientName"] as? String ?: "",
                        quantityCommanded = quantiteCommande,
                        quantityAvailable = quantiteLivre,
                        quantityMissing = quantiteManquant
                    )
                }
                
                val ruptureProduct = RuptureProduct(
                    codeProduit = codeProduit,
                    productName = data["productName"] as? String ?: "",
                    category = data["category"] as? String ?: "",
                    stockDisponible = totalLivre, // Le stock disponible = total livré
                    totalMissing = totalManquant,
                    scasAffected = scasAffected
                )
                
                val ruptureEntry = RuptureHistory(
                    date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(timestamp)),
                    time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(timestamp)),
                    supplier = supplier,
                    timestamp = timestamp,
                    week = semaine,
                    year = annee,
                    ruptureCount = 1,
                    totalMissing = totalManquant,
                    products = listOf(ruptureProduct)
                )
                
                ruptureHistory.add(ruptureEntry)
            }
            
            android.util.Log.d("RuptureHistory", "Nombre total d'entrées créées: ${ruptureHistory.size}")
            
            // Trier par timestamp décroissant
            val sortedHistory = ruptureHistory.sortedByDescending { it.timestamp }
            android.util.Log.d("RuptureHistory", "=== FIN getRuptureHistoryForProduct (succès) ===")
            sortedHistory
            
        } catch (e: Exception) {
            android.util.Log.e("RuptureHistory", "Erreur lors de la récupération de l'historique: ${e.message}", e)
            android.util.Log.d("RuptureHistory", "=== FIN getRuptureHistoryForProduct (erreur) ===")
            emptyList()
        }
    }
    
    /**
     * Calcule les statistiques de rupture pour un produit depuis octobre
     */
    suspend fun getRuptureSummaryForProduct(codeProduit: String, supplier: String): RuptureSummary {
        return try {
            android.util.Log.d("RuptureHistory", "=== DÉBUT getRuptureSummaryForProduct ===")
            android.util.Log.d("RuptureHistory", "Paramètres: codeProduit='$codeProduit', supplier='$supplier'")
            
            // Requête directe sur le document du produit spécifique
            val docSnapshot = firestore.collection("ruptures-history")
                .document(codeProduit)
                .get()
                .await()
            
            if (!docSnapshot.exists()) {
                android.util.Log.w("RuptureHistory", "Document non trouvé pour codeProduit: $codeProduit")
                return RuptureSummary()
            }
            
            val data = docSnapshot.data
            if (data == null) {
                android.util.Log.w("RuptureHistory", "Data null pour le document")
                return RuptureSummary()
            }
            
            @Suppress("UNCHECKED_CAST")
            val ruptures = (data["ruptures"] as? List<Map<String, Any>>) ?: emptyList()
            android.util.Log.d("RuptureHistory", "Nombre total de ruptures dans le document: ${ruptures.size}")
            
            // Filtrer les ruptures depuis le 1er octobre de l'année courante
            // Si nous sommes avant octobre, prendre octobre de l'année précédente
            val currentDate = java.util.Calendar.getInstance()
            val octoberStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.MONTH, java.util.Calendar.OCTOBER)
                set(java.util.Calendar.DAY_OF_MONTH, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                
                // Si nous sommes entre janvier et septembre, prendre octobre de l'année précédente
                if (currentDate.get(java.util.Calendar.MONTH) < java.util.Calendar.OCTOBER) {
                    add(java.util.Calendar.YEAR, -1)
                }
            }.timeInMillis
            
            android.util.Log.d("RuptureHistory", "Seuil octobre (timestamp): $octoberStart")
            android.util.Log.d("RuptureHistory", "Seuil octobre (date): ${java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(octoberStart))}")
            
            var totalRuptures = 0
            var totalCommanded = 0
            var totalDelivered = 0
            var totalMissing = 0
            
            for ((index, ruptureMap) in ruptures.withIndex()) {
                android.util.Log.d("RuptureHistory", "--- Analyse rupture $index ---")
                
                // Vérifier le fournisseur
                val ruptureSupplier = ruptureMap["supplier"] as? String
                android.util.Log.d("RuptureHistory", "Supplier de cette rupture: '$ruptureSupplier', recherché: '$supplier'")
                if (ruptureSupplier?.lowercase() != supplier.lowercase()) {
                    android.util.Log.d("RuptureHistory", "Rupture ignorée: supplier ne correspond pas")
                    continue
                }
                
                // Vérifier la date
                val timestamp = (ruptureMap["timestamp"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                android.util.Log.d("RuptureHistory", "Timestamp rupture: $timestamp, seuil octobre: $octoberStart")
                if (timestamp < octoberStart) {
                    android.util.Log.d("RuptureHistory", "Rupture ignorée: trop ancienne")
                    continue
                }
                
                totalRuptures++
                android.util.Log.d("RuptureHistory", "Rupture retenue! Total ruptures: $totalRuptures")
                
                @Suppress("UNCHECKED_CAST")
                val scas = (ruptureMap["scas"] as? List<Map<String, Any>>) ?: emptyList()
                android.util.Log.d("RuptureHistory", "Nombre de SCAs dans cette rupture: ${scas.size}")
                
                for ((scaIndex, scaMap) in scas.withIndex()) {
                    val commanded = (scaMap["quantiteCommande"] as? Number)?.toInt() ?: 0
                    val delivered = (scaMap["quantiteLivre"] as? Number)?.toInt() ?: 0
                    val missing = (scaMap["quantiteManquant"] as? Number)?.toInt() ?: 0
                    
                    android.util.Log.d("RuptureHistory", "SCA $scaIndex: cmd=$commanded, livré=$delivered, manq=$missing")
                    
                    totalCommanded += commanded
                    totalDelivered += delivered
                    totalMissing += missing
                }
                
                android.util.Log.d("RuptureHistory", "Totaux après cette rupture: cmd=$totalCommanded, livré=$totalDelivered, manq=$totalMissing")
            }
            
            val deliveryRate = if (totalCommanded > 0) {
                (totalDelivered.toDouble() / totalCommanded.toDouble()) * 100
            } else {
                0.0
            }
            
            android.util.Log.d("RuptureHistory", "Statistiques calculées: $totalRuptures ruptures, $totalCommanded cmd, $totalDelivered livré, ${deliveryRate}% taux")
            
            RuptureSummary(
                totalRuptures = totalRuptures,
                totalCommanded = totalCommanded,
                totalDelivered = totalDelivered,
                totalMissing = totalMissing,
                deliveryRate = deliveryRate,
                periodStart = "01/10"
            )
            
        } catch (e: Exception) {
            android.util.Log.e("RuptureHistory", "Erreur calcul statistiques: ${e.message}", e)
            RuptureSummary()
        }
    }
    
    /**
     * Parse un produit de rupture depuis les données Firebase
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseRuptureProduct(productMap: Map<String, Any>): RuptureProduct? {
        return try {
            val scasAffectedList = (productMap["scasAffected"] as? List<Map<String, Any>>) ?: emptyList()
            val approvisionneurMap = productMap["approvisionneur"] as? Map<String, Any> ?: emptyMap()
            
            RuptureProduct(
                codeProduit = productMap["codeProduit"] as? String ?: "",
                productName = productMap["productName"] as? String ?: "",
                category = productMap["category"] as? String ?: "",
                stockDisponible = (productMap["stockDisponible"] as? Number)?.toInt() ?: 0,
                totalMissing = (productMap["totalMissing"] as? Number)?.toInt() ?: 0,
                approvisionneur = Approvisionneur(
                    nom = approvisionneurMap["nom"] as? String ?: "",
                    prenom = approvisionneurMap["prenom"] as? String ?: "",
                    email = approvisionneurMap["email"] as? String ?: ""
                ),
                scasAffected = scasAffectedList.map { scaMap ->
                    ScaAffected(
                        codeClient = scaMap["codeClient"] as? String ?: "",
                        clientName = scaMap["clientName"] as? String ?: "",
                        quantityAvailable = (scaMap["quantityAvailable"] as? Number)?.toInt() ?: 0,
                        quantityCommanded = (scaMap["quantityCommanded"] as? Number)?.toInt() ?: 0,
                        quantityMissing = (scaMap["quantityMissing"] as? Number)?.toInt() ?: 0
                    )
                }
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Recherche des produits dans toutes les semaines disponibles
     */
    suspend fun searchProductsInAllWeeks(supplier: String, query: String): List<ScamarkProduct> {
        android.util.Log.d("SearchAllWeeks", "Starting global search for '$query' at $supplier")
        
        val allProducts = mutableListOf<ScamarkProduct>()
        val queryLower = query.lowercase()
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        
        try {
            // Chercher dans les 10 dernières semaines disponibles pour avoir des résultats pertinents
            val weeksToSearch = mutableListOf<Pair<Int, Int>>()
            
            // Ajouter la semaine courante et les 9 précédentes
            for (i in 0..9) {
                var week = currentWeek - i
                var year = currentYear
                
                if (week <= 0) {
                    week += 52
                    year -= 1
                }
                
                weeksToSearch.add(Pair(year, week))
            }
            
            // Recherche parallèle dans toutes les semaines
            kotlinx.coroutines.coroutineScope {
                val deferredQueries = weeksToSearch.map { (year, week) ->
                    async {
                        searchProductsInWeek(supplier, year, week, queryLower)
                    }
                }
                
                deferredQueries.forEach { deferred ->
                    allProducts.addAll(deferred.await())
                }
            }
            
            // Regrouper les produits par nom/code et conserver la plus récente référence
            val productsByKey = mutableMapOf<String, ScamarkProduct>()
            
            for (product in allProducts) {
                val key = "${product.productName}_${product.articleInfo?.codeProduit ?: product.decisions.firstOrNull()?.codeProduit}"
                val existing = productsByKey[key]
                
                if (existing == null) {
                    productsByKey[key] = product
                } else {
                    // Comparer les dates et garder la plus récente
                    val existingWeekScore = (existing.lastReferenceYear ?: 0) * 100 + (existing.lastReferenceWeek ?: 0)
                    val currentWeekScore = (product.lastReferenceYear ?: 0) * 100 + (product.lastReferenceWeek ?: 0)
                    
                    if (currentWeekScore > existingWeekScore) {
                        productsByKey[key] = product
                    }
                }
            }
            
            val uniqueProducts = productsByKey.values.toList()
            
            android.util.Log.d("SearchAllWeeks", "Found ${uniqueProducts.size} unique products")
            return uniqueProducts
            
        } catch (e: Exception) {
            android.util.Log.e("SearchAllWeeks", "Error during global search", e)
            return emptyList()
        }
    }
    
    /**
     * Recherche des produits dans une semaine spécifique
     */
    private suspend fun searchProductsInWeek(supplier: String, year: Int, week: Int, queryLower: String): List<ScamarkProduct> {
        return try {
            // Utiliser la méthode existante pour récupérer les produits de la semaine
            val allProductsInWeek = getWeekDecisions(year, week, supplier)
            
            // Filtrer les produits qui correspondent à la recherche
            val matchingProducts = if (queryLower.isBlank()) {
                // Si la query est vide, retourner une liste vide au lieu de tous les produits
                emptyList()
            } else {
                allProductsInWeek.filter { product ->
                    val productName = product.productName.lowercase()
                    val clientNames = product.decisions.map { it.nomClient.lowercase() }
                    val brand = product.articleInfo?.marque?.lowercase() ?: ""
                    val category = product.articleInfo?.categorie?.lowercase() ?: ""
                    
                    productName.contains(queryLower) || 
                    clientNames.any { it.contains(queryLower) } ||
                    brand.contains(queryLower) ||
                    category.contains(queryLower)
                }
            }
            
            // Ajouter l'information de semaine de référence à chaque produit trouvé
            matchingProducts.map { product ->
                product.copy(
                    lastReferenceWeek = week,
                    lastReferenceYear = year
                )
            }
            
        } catch (e: Exception) {
            android.util.Log.w("SearchAllWeeks", "Error week $year-$week: ${e.message}")
            emptyList()
        }
    }
    
}