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
            android.util.Log.d("FirebaseRepo", "🧹 Nettoyage complet du cache")
            weekCache.clear()
            weeksCache.clear()
            statsCache.clear()
            articlesCache.clear()
            clientsCache.clear()
        }
        
        fun clearSupplierCache(supplier: String) {
            android.util.Log.d("FirebaseRepo", "🧹 Nettoyage cache pour fournisseur '$supplier'")
            
            // Nettoyer les entrées spécifiques au fournisseur
            val weekKeysToRemove = weekCache.keys.filter { it.endsWith("-$supplier") }
            weekKeysToRemove.forEach { weekCache.remove(it) }
            
            val weeksKeyToRemove = "weeks-$supplier"
            weeksCache.remove(weeksKeyToRemove)
            
            val statsKeysToRemove = statsCache.keys.filter { it.endsWith("-$supplier") }
            statsKeysToRemove.forEach { statsCache.remove(it) }
            
            android.util.Log.d("FirebaseRepo", "🧹 Supprimé: ${weekKeysToRemove.size} weeks, 1 weeksList, ${statsKeysToRemove.size} stats pour '$supplier'")
        }
        
        fun getCacheInfo(): String {
            return "Cache: ${weekCache.size} weeks, ${weeksCache.size} weeksList, ${statsCache.size} stats, ${articlesCache.size} articles, ${clientsCache.size} clients"
        }
    }
    
    // Authentication
    suspend fun signIn(email: String, password: String): Result<String> {
        return try {
            // Vérifier si c'est un projet Firebase correctement configuré
            val projectId = auth.app.options.projectId
            android.util.Log.d("FirebaseRepo", "🔥 Project ID: $projectId")
            android.util.Log.i("FirebaseAuth", "Logging in as $email with empty reCAPTCHA token")
            
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user?.uid ?: "")
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepo", "❌ Erreur Firebase: ${e.message}")
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
     * Récupère les semaines disponibles pour un fournisseur - CHARGE TOUTE L'ANNÉE JUSQU'À LA SEMAINE COURANTE
     */
    suspend fun getAvailableWeeks(supplier: String = "all"): List<AvailableWeek> {
        android.util.Log.d("FirebaseRepo", "⏱️ REPO_WEEKS_START: Début getAvailableWeeks pour '$supplier'")
        val totalStart = System.currentTimeMillis()
        
        // Vérifier le cache
        val cacheCheckStart = System.currentTimeMillis()
        val cacheKey = "weeks-$supplier"
        weeksCache[cacheKey]?.let { cacheEntry ->
            val cacheAge = System.currentTimeMillis() - cacheEntry.timestamp
            if (cacheAge < CACHE_DURATION_MS) {
                android.util.Log.d("FirebaseRepo", "⏱️ REPO_CACHE_HIT: Cache hit en ${System.currentTimeMillis() - cacheCheckStart}ms - ${cacheEntry.weeks.size} semaines (âge: ${cacheAge/1000}s)")
                return cacheEntry.weeks
            } else {
                android.util.Log.d("FirebaseRepo", "⏱️ REPO_CACHE_EXPIRED: Cache expiré (âge: ${cacheAge/1000}s), rechargement...")
                weeksCache.remove(cacheKey)
            }
        }
        android.util.Log.d("FirebaseRepo", "⏱️ REPO_CACHE_CHECK: Vérification cache en ${System.currentTimeMillis() - cacheCheckStart}ms")
        
        val availableWeeks = mutableListOf<AvailableWeek>()
        val suppliers = if (supplier == "all") listOf("anecoop", "solagora") else listOf(supplier)
        
        try {
            val initStart = System.currentTimeMillis()
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            val currentWeek = getCurrentISOWeek()
            android.util.Log.d("FirebaseRepo", "⏱️ REPO_INIT: Initialisation en ${System.currentTimeMillis() - initStart}ms - semaine courante $currentWeek")
            
            // Chercher TOUTE l'année à rebours depuis la semaine courante
            val rangeStart = System.currentTimeMillis()
            val startWeek = 1
            val endWeek = currentWeek  // Seulement jusqu'à la semaine courante (pas de futur)
            val weekRange = startWeek..endWeek
            android.util.Log.d("FirebaseRepo", "⏱️ REPO_RANGE: Calcul range de semaines en ${System.currentTimeMillis() - rangeStart}ms - range: $startWeek..$endWeek")
            
            // Paralléliser les requêtes Firestore pour tous les fournisseurs
            @Suppress("UNUSED_VARIABLE")
            val _parallelStart = System.currentTimeMillis()
            android.util.Log.d("FirebaseRepo", "⏱️ REPO_PARALLEL_START: Démarrage requêtes parallèles pour ${suppliers.size} fournisseurs")
            
            var totalFirestoreTime = 0L
            var totalCollections = 0
            var foundWeeks = 0
            
            kotlinx.coroutines.coroutineScope {
                val deferredQueries = suppliers.flatMap { sup ->
                    weekRange.map { week ->
                        async {
                            val weekStr = week.toString().padStart(2, '0')
                            val collectionPath = "decisions_$sup/$currentYear/$weekStr"
                            
                            try {
                                val firestoreStart = System.currentTimeMillis()
                                val snapshot = firestore.collection(collectionPath)
                                    .limit(1)
                                    .get()
                                    .await()
                                val firestoreTime = System.currentTimeMillis() - firestoreStart
                                
                                if (!snapshot.isEmpty) {
                                    Triple(AvailableWeek(currentYear, week, sup), firestoreTime, true)
                                } else {
                                    Triple(null, firestoreTime, false)
                                }
                            } catch (e: Exception) {
                                android.util.Log.d("FirebaseRepo", "⏱️ REPO_WEEK_ERROR: Erreur semaine $week pour $sup: ${e.message}")
                                Triple(null, 0L, false)
                            }
                        }
                    }
                }
                
                // Attendre toutes les requêtes et collecter les résultats
                val results = deferredQueries.awaitAll()
                @Suppress("UNUSED_DESTRUCTURED_PARAMETER_ENTRY")
                results.forEach { (week, firestoreTime, _found) ->
                    totalFirestoreTime += firestoreTime
                    totalCollections++
                    
                    week?.let { 
                        availableWeeks.add(it)
                        foundWeeks++
                    }
                }
            }
            
            android.util.Log.d("FirebaseRepo", "⏱️ REPO_FIRESTORE_TOTAL: Temps total Firestore: ${totalFirestoreTime}ms pour $totalCollections collections (moyenne: ${if(totalCollections > 0) totalFirestoreTime/totalCollections else 0}ms)")
            
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepo", "⏱️ REPO_ERROR: Erreur générale: ${e.message}")
            // Retourner une liste de fallback
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            return listOf(
                AvailableWeek(currentYear, getCurrentISOWeek(), "anecoop"),
                AvailableWeek(currentYear, getCurrentISOWeek(), "solagora")
            )
        }
        
        val sortStart = System.currentTimeMillis()
        val sortedWeeks = availableWeeks.sortedWith(compareByDescending<AvailableWeek> { it.year }
            .thenByDescending { it.week }
            .thenBy { it.supplier })
        android.util.Log.d("FirebaseRepo", "⏱️ REPO_SORT: Tri des semaines en ${System.currentTimeMillis() - sortStart}ms")
        
        // Mettre en cache
        val cacheStart = System.currentTimeMillis()
        weeksCache[cacheKey] = WeeksCacheEntry(
            weeks = sortedWeeks,
            timestamp = System.currentTimeMillis(),
            supplier = supplier
        )
        android.util.Log.d("FirebaseRepo", "⏱️ REPO_CACHE_STORE: Mise en cache en ${System.currentTimeMillis() - cacheStart}ms")
        
        val totalEnd = System.currentTimeMillis()
        android.util.Log.d("FirebaseRepo", "⏱️ REPO_WEEKS_END: TOTAL getAvailableWeeks en ${totalEnd - totalStart}ms - ${sortedWeeks.size} semaines")
        
        return sortedWeeks
    }
    
    /**
     * Récupère plus de semaines à partir d'une semaine donnée - CONTINUE À REBOURS
     */
    suspend fun getExtendedAvailableWeeksFromWeek(supplier: String = "all", fromWeek: Int, fromYear: Int? = null): List<AvailableWeek> {
        val globalStart = System.currentTimeMillis()
        val startYear = fromYear ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        
        android.util.Log.d("FirebaseRepo", "🚀 SEARCH_START - supplier:'$supplier', fromWeek:$fromWeek, startYear:$startYear")
        
        // VALIDATION: Chercher SEULEMENT dans l'année courante
        if (startYear != java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) {
            android.util.Log.w("FirebaseRepo", "⚠️ SEARCH_BLOCKED - Année $startYear différente de l'année courante, arrêt")
            return emptyList()
        }
        
        val availableWeeks = mutableListOf<AvailableWeek>()
        val suppliers = if (supplier == "all") listOf("anecoop", "solagora") else listOf(supplier)
        
        android.util.Log.d("FirebaseRepo", "📋 SUPPLIERS - ${suppliers.joinToString { "'$it'" }} (${suppliers.size} total)")
        
        // Calculer la semaine de départ
        val searchWeek = maxOf(1, fromWeek - 1)
        android.util.Log.d("FirebaseRepo", "📍 SEARCH_RANGE - De semaine $searchWeek vers semaine 1 (année $startYear SEULEMENT)")
        
        try {
            for (sup in suppliers) {
                val supplierStart = System.currentTimeMillis()
                android.util.Log.d("FirebaseRepo", "🏪 SUPPLIER_START - '$sup' depuis semaine $searchWeek")
                
                var foundCount = 0
                var batchStart = searchWeek
                val batchSize = 6 // Batch plus large pour réduire les appels
                var shouldStopSearch = false
                
                android.util.Log.w("FirebaseRepo", "🔄 WHILE_LOOP_START - '$sup' batchStart=$batchStart, foundCount=$foundCount, shouldStopSearch=$shouldStopSearch")
                
                while (batchStart >= 1 && !shouldStopSearch) {
                    val batchEnd = maxOf(1, batchStart - batchSize + 1)
                    val batchStart_time = System.currentTimeMillis()
                    
                    android.util.Log.d("FirebaseRepo", "🎯 BATCH - '$sup' semaines $batchStart→$batchEnd (batch ${batchSize})")
                    android.util.Log.w("FirebaseRepo", "🔄 BATCH_CONDITION - '$sup' batchStart=$batchStart >= 1? ${batchStart >= 1}, foundCount=$foundCount, shouldStopSearch=$shouldStopSearch")
                    
                    var batchFound = 0
                    var emptyWeeksInBatch = 0
                    
                    for (week in batchStart downTo batchEnd) {
                        // Plus de limite artificielle - on charge toutes les semaines disponibles
                        
                        val weekStr = week.toString().padStart(2, '0')
                        val collectionPath = "decisions_$sup/$startYear/$weekStr"
                        
                        try {
                            val queryStart = System.currentTimeMillis()
                            val snapshot = firestore.collection(collectionPath)
                                .limit(1)
                                .get()
                                .await()
                            val queryTime = System.currentTimeMillis() - queryStart
                            
                            if (!snapshot.isEmpty) {
                                availableWeeks.add(AvailableWeek(startYear, week, sup))
                                foundCount++
                                batchFound++
                                android.util.Log.v("FirebaseRepo", "✅ FOUND - '$sup' W$week (${queryTime}ms, ${snapshot.size()}docs)")
                            } else {
                                emptyWeeksInBatch++
                                android.util.Log.v("FirebaseRepo", "⚪ EMPTY - '$sup' W$week (${queryTime}ms)")
                                
                                // ARRÊT IMMÉDIAT: Si on n'a trouvé aucune donnée et qu'on trouve une semaine vide,
                                // arrêter immédiatement car les semaines précédentes seront probablement vides aussi
                                if (foundCount == 0 && emptyWeeksInBatch >= 1) {
                                    android.util.Log.w("FirebaseRepo", "🛑 STOP_SEARCH - '$sup' arrêt immédiat après $emptyWeeksInBatch semaine vide")
                                    shouldStopSearch = true
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("FirebaseRepo", "❌ QUERY_ERROR - '$sup' W$week: ${e.message}")
                        }
                    }
                    
                    val batchTime = System.currentTimeMillis() - batchStart_time
                    android.util.Log.d("FirebaseRepo", "✅ BATCH_DONE - '$sup' W$batchStart→$batchEnd: ${batchFound}/${batchSize} trouvées (${batchTime}ms)")
                    
                    android.util.Log.w("FirebaseRepo", "🔄 STOP_CHECK - '$sup' batchFound=$batchFound, foundCount=$foundCount, emptyWeeksInBatch=$emptyWeeksInBatch")
                    
                    // Si aucune semaine trouvée dans ce batch et qu'on avait déjà des données, arrêter
                    if (batchFound == 0 && foundCount > 0) {
                        android.util.Log.w("FirebaseRepo", "🛑 STOP_SEARCH_1 - '$sup' batch vide après avoir trouvé des données (batchFound=$batchFound, foundCount=$foundCount)")
                        shouldStopSearch = true
                    }
                    
                    // Si plus de 3 semaines vides consécutives dans ce batch, arrêter aussi
                    if (emptyWeeksInBatch >= 3) {
                        android.util.Log.w("FirebaseRepo", "🛑 STOP_SEARCH_2 - '$sup' trop de semaines vides consécutives ($emptyWeeksInBatch >= 3)")
                        shouldStopSearch = true
                    }
                    
                    android.util.Log.w("FirebaseRepo", "🔄 AFTER_STOP_CHECK - '$sup' shouldStopSearch=$shouldStopSearch, next batchStart will be ${batchEnd - 1}")
                    
                    batchStart = batchEnd - 1
                }
                
                android.util.Log.w("FirebaseRepo", "🔄 WHILE_LOOP_EXIT - '$sup' SORTIE DE BOUCLE: batchStart=$batchStart >= 1? ${batchStart >= 1}, foundCount=$foundCount, shouldStopSearch=$shouldStopSearch")
                
                val supplierTime = System.currentTimeMillis() - supplierStart
                android.util.Log.d("FirebaseRepo", "🏪 SUPPLIER_DONE - '$sup': ${foundCount} semaines en ${supplierTime}ms")
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepo", "🚨 SEARCH_ERROR - ${e.message}")
            return emptyList()
        }
        
        // Tri final
        val sortStart = System.currentTimeMillis()
        val sortedWeeks = availableWeeks.sortedWith(
            compareByDescending<AvailableWeek> { it.year }
                .thenByDescending { it.week }
                .thenBy { it.supplier }
        )
        val sortTime = System.currentTimeMillis() - sortStart
        
        val globalTime = System.currentTimeMillis() - globalStart
        android.util.Log.d("FirebaseRepo", "🏁 SEARCH_COMPLETE - ${sortedWeeks.size} semaines trouvées en ${globalTime}ms (tri: ${sortTime}ms)")
        
        // Log détaillé des résultats
        sortedWeeks.forEach { week ->
            android.util.Log.v("FirebaseRepo", "📅 RESULT - ${week.supplier} W${week.week}/${week.year}")
        }
        
        return sortedWeeks
    }
    
    /**
     * Vérification rapide de disponibilité d'une semaine spécifique
     */
    suspend fun checkWeekAvailability(supplier: String, week: Int, year: Int): Boolean {
        android.util.Log.d("FirebaseRepo", "🔍 CHECK_WEEK - Vérification '$supplier' W$week/$year")
        
        try {
            val suppliers = if (supplier == "all") listOf("anecoop", "solagora") else listOf(supplier)
            
            for (sup in suppliers) {
                val weekStr = week.toString().padStart(2, '0')
                val collectionPath = "decisions_$sup/$year/$weekStr"
                
                val queryStart = System.currentTimeMillis()
                val snapshot = firestore.collection(collectionPath)
                    .limit(1)
                    .get()
                    .await()
                val queryTime = System.currentTimeMillis() - queryStart
                
                if (!snapshot.isEmpty) {
                    android.util.Log.d("FirebaseRepo", "✅ CHECK_WEEK_FOUND - '$sup' W$week/$year disponible (${queryTime}ms)")
                    return true
                } else {
                    android.util.Log.d("FirebaseRepo", "⚪ CHECK_WEEK_EMPTY - '$sup' W$week/$year vide (${queryTime}ms)")
                }
            }
            
            android.util.Log.d("FirebaseRepo", "❌ CHECK_WEEK_NOT_FOUND - Aucune donnée trouvée pour W$week/$year")
            return false
            
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepo", "🚨 CHECK_WEEK_ERROR - ${e.message}")
            return false
        }
    }
    
    
    /**
     * Récupère les décisions d'une semaine spécifique avec enrichissement
     */
    suspend fun getWeekDecisions(year: Int, week: Int, supplier: String = "all"): List<ScamarkProduct> {
        android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_START: Début getWeekDecisions $year-$week pour '$supplier'")
        val startTime = System.currentTimeMillis()
        
        // Vérifier le cache pour la semaine actuelle
        val cacheCheckStart = System.currentTimeMillis()
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        val cacheKey = "$year-$week-$supplier"
        
        if (year == currentYear && week == currentWeek) {
            weekCache[cacheKey]?.let { cacheEntry ->
                val cacheAge = System.currentTimeMillis() - cacheEntry.timestamp
                if (cacheAge < CACHE_DURATION_MS) {
                    android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_CACHE_HIT: Cache hit en ${System.currentTimeMillis() - cacheCheckStart}ms - ${cacheEntry.data.size} produits (âge: ${cacheAge/1000}s)")
                    return cacheEntry.data
                } else {
                    android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_CACHE_EXPIRED: Cache expiré, rechargement...")
                    weekCache.remove(cacheKey)
                }
            }
        }
        android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_CACHE_CHECK: Vérification cache en ${System.currentTimeMillis() - cacheCheckStart}ms")
        
        val suppliers = if (supplier == "all") listOf("anecoop", "solagora") else listOf(supplier)
        val weekStr = week.toString().padStart(2, '0')
        val allDecisions = mutableListOf<ScamarkDecision>()
        
        // 1. Charger les décisions brutes
        val decisionsStart = System.currentTimeMillis()
        var totalDecisionTime = 0L
        
        for (sup in suppliers) {
            val supplierStart = System.currentTimeMillis()
            try {
                val collectionPath = "decisions_$sup/$year/$weekStr"
                android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_COLLECTION: Chargement $collectionPath")
                
                val firestoreStart = System.currentTimeMillis()
                val snapshot = firestore.collection(collectionPath).get().await()
                val firestoreEnd = System.currentTimeMillis()
                val firestoreTime = firestoreEnd - firestoreStart
                totalDecisionTime += firestoreTime
                
                android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_FIRESTORE: Collection $sup chargée en ${firestoreTime}ms - ${snapshot.documents.size} documents")
                
                val parseStart = System.currentTimeMillis()
                var addedCount = 0
                snapshot.documents.forEach { doc ->
                    val decision = doc.toObject(ScamarkDecision::class.java)
                    decision?.let {
                        allDecisions.add(it.copy(supplier = sup))
                        addedCount++
                    }
                }
                val parseEnd = System.currentTimeMillis()
                android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_PARSE: Parsing $sup en ${parseEnd - parseStart}ms - $addedCount décisions ajoutées")
                
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepo", "⏱️ DECISIONS_ERROR: Erreur collection $sup: ${e.message}")
            }
            
            val supplierEnd = System.currentTimeMillis()
            android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_SUPPLIER: Fournisseur $sup traité en ${supplierEnd - supplierStart}ms")
        }
        
        val decisionsEnd = System.currentTimeMillis()
        android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_TOTAL: Toutes décisions chargées en ${decisionsEnd - decisionsStart}ms (Firestore: ${totalDecisionTime}ms)")
        
        if (allDecisions.isEmpty()) {
            android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_EMPTY: Aucune décision trouvée")
            return emptyList()
        }
        
        // 2. Charger les articles pour enrichissement avec cache amélioré
        val articlesStart = System.currentTimeMillis()
        val productCodes = allDecisions.map { it.codeProduit }.distinct()
        val articlesMap = mutableMapOf<String, Article>()
        android.util.Log.d("FirebaseRepo", "⏱️ ARTICLES_START: Chargement articles pour ${productCodes.size} codes produits")
        
        // Vérifier le cache articles
        val articlesCacheKey = suppliers.sorted().joinToString(",")
        val cachedArticles = articlesCache[articlesCacheKey]
        val articlesFromCache = cachedArticles?.let { (articles, timestamp) ->
            val age = System.currentTimeMillis() - timestamp
            if (age < STATIC_CACHE_DURATION_MS) {
                android.util.Log.d("FirebaseRepo", "⏱️ ARTICLES_CACHE_HIT: Cache hit pour articles (âge: ${age/1000}s) - ${articles.size} articles")
                articles
            } else {
                android.util.Log.d("FirebaseRepo", "⏱️ ARTICLES_CACHE_EXPIRED: Cache articles expiré (âge: ${age/1000}s)")
                articlesCache.remove(articlesCacheKey)
                null
            }
        }
        
        val allArticles = articlesFromCache ?: run {
            android.util.Log.d("FirebaseRepo", "⏱️ ARTICLES_FIRESTORE_CALL: Chargement depuis Firestore")
            val articlesFromFirestore = mutableMapOf<String, Article>()
            
            try {
                val firestoreStart = System.currentTimeMillis()
                val articlesSnapshot = firestore.collectionGroup("articles").get().await()
                val firestoreEnd = System.currentTimeMillis()
                android.util.Log.d("FirebaseRepo", "⏱️ ARTICLES_FIRESTORE: Articles chargés en ${firestoreEnd - firestoreStart}ms - ${articlesSnapshot.documents.size} documents")
                
                val parseStart = System.currentTimeMillis()
                var parsedCount = 0
                articlesSnapshot.documents.forEach { doc ->
                    val article = doc.toObject(Article::class.java)
                    article?.let {
                        articlesFromFirestore[it.codeProduit] = it
                        parsedCount++
                    }
                }
                android.util.Log.d("FirebaseRepo", "⏱️ ARTICLES_PARSE: Parsing terminé en ${System.currentTimeMillis() - parseStart}ms - $parsedCount articles")
                
                // Mettre en cache
                articlesCache[articlesCacheKey] = Pair(articlesFromFirestore, System.currentTimeMillis())
                android.util.Log.d("FirebaseRepo", "⏱️ ARTICLES_CACHED: ${articlesFromFirestore.size} articles mis en cache")
                
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepo", "⏱️ ARTICLES_ERROR: Erreur chargement articles: ${e.message}")
            }
            
            articlesFromFirestore
        }
        
        // Filtrer pour les codes produits nécessaires
        productCodes.forEach { code ->
            allArticles[code]?.let { articlesMap[code] = it }
        }
        
        val articlesEnd = System.currentTimeMillis()
        android.util.Log.d("FirebaseRepo", "⏱️ ARTICLES_END: Enrichissement articles terminé en ${articlesEnd - articlesStart}ms")
        
        // 3. Charger les informations clients avec cache amélioré
        val clientsStart = System.currentTimeMillis()
        val clientCodes = allDecisions.flatMap { it.scas.map { sca -> sca.codeClient } }.distinct()
        val clientsMap = mutableMapOf<String, ClientInfo>()
        android.util.Log.d("FirebaseRepo", "⏱️ CLIENTS_START: Chargement clients pour ${clientCodes.size} codes clients")
        
        // Vérifier le cache clients
        val clientsCacheKey = "all_clients"
        val cachedClients = clientsCache[clientsCacheKey]
        val clientsFromCache = cachedClients?.let { (clients, timestamp) ->
            val age = System.currentTimeMillis() - timestamp
            if (age < STATIC_CACHE_DURATION_MS) {
                android.util.Log.d("FirebaseRepo", "⏱️ CLIENTS_CACHE_HIT: Cache hit pour clients (âge: ${age/1000}s) - ${clients.size} clients")
                clients
            } else {
                android.util.Log.d("FirebaseRepo", "⏱️ CLIENTS_CACHE_EXPIRED: Cache clients expiré (âge: ${age/1000}s)")
                clientsCache.remove(clientsCacheKey)
                null
            }
        }
        
        val allClients = clientsFromCache ?: run {
            android.util.Log.d("FirebaseRepo", "⏱️ CLIENTS_FIRESTORE_CALL: Chargement depuis Firestore")
            val clientsFromFirestore = mutableMapOf<String, ClientInfo>()
            
            try {
                val firestoreStart = System.currentTimeMillis()
                val clientsSnapshot = firestore.collection("clients").get().await()
                val firestoreEnd = System.currentTimeMillis()
                android.util.Log.d("FirebaseRepo", "⏱️ CLIENTS_FIRESTORE: Clients chargés en ${firestoreEnd - firestoreStart}ms - ${clientsSnapshot.documents.size} documents")
                
                val parseStart = System.currentTimeMillis()
                var parsedCount = 0
                clientsSnapshot.documents.forEach { doc ->
                    val client = doc.toObject(ClientInfo::class.java)
                    client?.let {
                        // Stocker par documentId principal
                        clientsFromFirestore[it.documentId] = it
                        // Stocker aussi par id alternatif si différent
                        if (it.id.isNotEmpty() && it.id != it.documentId) {
                            clientsFromFirestore[it.id] = it
                        }
                        parsedCount++
                    }
                }
                android.util.Log.d("FirebaseRepo", "⏱️ CLIENTS_PARSE: Parsing terminé en ${System.currentTimeMillis() - parseStart}ms - $parsedCount clients")
                
                // Mettre en cache
                clientsCache[clientsCacheKey] = Pair(clientsFromFirestore, System.currentTimeMillis())
                android.util.Log.d("FirebaseRepo", "⏱️ CLIENTS_CACHED: ${clientsFromFirestore.size} clients mis en cache")
                
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepo", "⏱️ CLIENTS_ERROR: Erreur chargement clients: ${e.message}")
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
        
        android.util.Log.d("FirebaseRepo", "⏱️ CLIENTS_MATCH: $matchedClients correspondances trouvées")
        
        val clientsEnd = System.currentTimeMillis()
        android.util.Log.d("FirebaseRepo", "⏱️ CLIENTS_END: Enrichissement clients terminé en ${clientsEnd - clientsStart}ms")
        
        // 4. Traitement et enrichissement
        val processingStart = System.currentTimeMillis()
        android.util.Log.d("FirebaseRepo", "⏱️ PROCESSING_START: Début traitement et enrichissement ${allDecisions.size} décisions")
        
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
        
        val sortStart = System.currentTimeMillis()
        val sortedProducts = finalProducts.sortedBy { it.productName }
        val processingEnd = System.currentTimeMillis()
        android.util.Log.d("FirebaseRepo", "⏱️ PROCESSING_END: Traitement terminé en ${processingEnd - processingStart}ms (sort: ${System.currentTimeMillis() - sortStart}ms)")
        
        // Mettre en cache si c'est la semaine actuelle
        val cacheStoreStart = System.currentTimeMillis()
        if (year == currentYear && week == currentWeek) {
            weekCache[cacheKey] = CacheEntry(
                data = sortedProducts,
                timestamp = System.currentTimeMillis(),
                year = year,
                week = week,
                supplier = supplier
            )
            android.util.Log.d("FirebaseRepo", "⏱️ CACHE_STORE: Mise en cache en ${System.currentTimeMillis() - cacheStoreStart}ms")
        }
        
        val endTime = System.currentTimeMillis()
        android.util.Log.d("FirebaseRepo", "⏱️ DECISIONS_END: TOTAL getWeekDecisions en ${endTime - startTime}ms - ${sortedProducts.size} produits")
        
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
        android.util.Log.d("FirebaseRepo", "🔍 PALMARES_START: Récupération historique produit='$productName' code='$productCode' supplier='$supplier' depuis octobre")
        val startTime = System.currentTimeMillis()
        
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val currentWeek = getCurrentISOWeek()
        
        // Utiliser la semaine sélectionnée ou la semaine actuelle par défaut
        val targetYear = selectedYear ?: currentYear
        val targetWeek = selectedWeek ?: currentWeek
        
        android.util.Log.d("FirebaseRepo", "📅 PALMARES_TARGET: Semaine sélectionnée = $targetYear-$targetWeek (actuelle: $currentYear-$currentWeek)")
        
        // Calculer TOUTES les semaines depuis le 01 octobre 2024 (semaine 40) 
        // SANS limitation - on scanne TOUTES les collections Firebase
        val octoberFirstWeek = 40
        val octoberYear = 2024 // Octobre 2024 est notre point de départ fixe
        val weeksToCheck = mutableListOf<Pair<Int, Int>>() // Pair(year, week)
        
        android.util.Log.d("FirebaseRepo", "📅 PALMARES_PERIOD_FIXED: Depuis octobre 2024 (semaine 40) jusqu'à $targetYear semaine $targetWeek")
        
        // D'abord TOUTES les semaines d'octobre à décembre 2024
        for (week in octoberFirstWeek..52) {
            weeksToCheck.add(Pair(octoberYear, week))
        }
        android.util.Log.d("FirebaseRepo", "📅 PALMARES_2024: Ajout semaines 40-52 de 2024")
        
        // Puis TOUTES les semaines de 2025 depuis janvier jusqu'à la semaine sélectionnée
        if (targetYear > octoberYear) {
            for (week in 1..targetWeek) {
                weeksToCheck.add(Pair(targetYear, week))
            }
            android.util.Log.d("FirebaseRepo", "📅 PALMARES_2025: Ajout semaines 1-$targetWeek de $targetYear")
        }
        
        android.util.Log.d("FirebaseRepo", "📅 PALMARES_WEEKS_TO_CHECK: ${weeksToCheck.size} semaines à vérifier: ${weeksToCheck.take(5)}...${if(weeksToCheck.size > 5) weeksToCheck.takeLast(5) else ""}")
        
        val referencedWeeks = mutableListOf<Pair<Int, Int>>()
        var totalQueries = 0
        // Track found references for debugging
        var _foundReferences = 0
        
        try {
            android.util.Log.d("FirebaseRepo", "🔍 PALMARES_SEARCH: Début scan Firebase pour produit='$productName' code='$productCode'")
            
            // Pour debugger, afficher les produits disponibles dans la première semaine
            if (weeksToCheck.isNotEmpty()) {
                val (debugYear, debugWeek) = weeksToCheck[0]
                val debugWeekStr = debugWeek.toString().padStart(2, '0')
                val debugCollectionPath = "decisions_$supplier/$debugYear/$debugWeekStr"
                try {
                    val debugSnapshot = firestore.collection(debugCollectionPath).limit(3).get().await()
                    val availableProducts = debugSnapshot.documents.map { 
                        "code=${it.getString("codeProduit")} libelle=${it.getString("libelle")?.take(30)}..." 
                    }
                    android.util.Log.d("FirebaseRepo", "🔍 PALMARES_DEBUG: Exemples produits semaine $debugWeek/$debugYear: $availableProducts")
                } catch (e: Exception) {
                    android.util.Log.d("FirebaseRepo", "🔍 PALMARES_DEBUG: Impossible de lire semaine debug $debugWeek/$debugYear")
                }
            }
            
            // OPTIMISATION : Requêtes PARALLÈLES au lieu de séquentielles
            android.util.Log.d("FirebaseRepo", "⚡ PALMARES_PARALLEL_START: Démarrage de ${weeksToCheck.size} requêtes parallèles")
            val parallelStart = System.currentTimeMillis()
            
            kotlinx.coroutines.coroutineScope {
                val deferredQueries = weeksToCheck.map { (year, week) ->
                    async {
                        val weekStr = week.toString().padStart(2, '0')
                        val collectionPath = "decisions_$supplier/$year/$weekStr"
                        totalQueries++
                        
                        try {
                            val queryStart = System.currentTimeMillis()
                            var snapshot: com.google.firebase.firestore.QuerySnapshot? = null
                            var foundWithMethod = ""
                            
                            // Si on a un code produit, l'utiliser en priorité (plus fiable)
                            if (!productCode.isNullOrBlank()) {
                                snapshot = firestore.collection(collectionPath)
                                    .whereEqualTo("codeProduit", productCode)
                                    .limit(1)
                                    .get()
                                    .await()
                                foundWithMethod = "CODE_PRODUIT"
                                
                                if (!snapshot.isEmpty) {
                                    android.util.Log.d("FirebaseRepo", "🎯 PALMARES_CODE_MATCH: Trouvé par code '$productCode' semaine $week/$year")
                                }
                            }
                            
                            // Si pas trouvé par code, essayer par nom du produit
                            if (snapshot?.isEmpty != false) {
                                snapshot = firestore.collection(collectionPath)
                                    .whereEqualTo("libelle", productName)
                                    .limit(1)
                                    .get()
                                    .await()
                                foundWithMethod = "LIBELLE"
                                
                                if (!snapshot.isEmpty) {
                                    android.util.Log.d("FirebaseRepo", "🎯 PALMARES_LIBELLE_MATCH: Trouvé par libelle '$productName' semaine $week/$year")
                                }
                            }
                            
                            val queryTime = System.currentTimeMillis() - queryStart
                            
                            if (snapshot?.isEmpty == false) {
                                android.util.Log.d("FirebaseRepo", "✅ PALMARES_FOUND: Trouvé semaine $week/$year en ${queryTime}ms avec méthode $foundWithMethod")
                                Triple(Pair(year, week), true, queryTime)
                            } else {
                                android.util.Log.d("FirebaseRepo", "❌ PALMARES_NOT_FOUND: Absent semaine $week/$year en ${queryTime}ms")
                                Triple(Pair(year, week), false, queryTime)
                            }
                        } catch (e: Exception) {
                            android.util.Log.d("FirebaseRepo", "❌ PALMARES_ERROR: Erreur semaine $week/$year: ${e.message}")
                            Triple(Pair(year, week), false, 0L)
                        }
                    }
                }
                
                // Attendre toutes les requêtes et traiter les résultats
                val results = deferredQueries.awaitAll()
                val parallelEnd = System.currentTimeMillis()
                android.util.Log.d("FirebaseRepo", "⚡ PALMARES_PARALLEL_END: ${results.size} requêtes parallèles terminées en ${parallelEnd - parallelStart}ms")
                
                var totalFirestoreTime = 0L
                results.forEach { (weekPair, found, queryTime) ->
                    totalFirestoreTime += queryTime
                    if (found) {
                        referencedWeeks.add(weekPair)
                        _foundReferences++
                    }
                }
                
                android.util.Log.d("FirebaseRepo", "⚡ PALMARES_PARALLEL_STATS: Temps total requêtes: ${totalFirestoreTime}ms, Parallélisation: ${parallelEnd - parallelStart}ms, Gain: ${totalFirestoreTime - (parallelEnd - parallelStart)}ms")
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepo", "🚨 PALMARES_GENERAL_ERROR: Erreur générale historique: ${e.message}")
        }
        
        // Calculer les semaines consécutives depuis la fin (semaine actuelle)
        var consecutiveWeeks = 0
        android.util.Log.d("FirebaseRepo", "🔍 PALMARES_CONSECUTIVE_START: Début calcul consécutif - ${referencedWeeks.size} semaines trouvées")
        
        if (referencedWeeks.isNotEmpty()) {
            // Trier par année puis semaine (ordre chronologique)
            val sortedWeeks = referencedWeeks.sortedWith(compareBy({ it.first }, { it.second }))
            
            android.util.Log.d("FirebaseRepo", "🔍 PALMARES_CONSECUTIVE_LIST: semaines trouvées = ${sortedWeeks.map { "${it.second}/${it.first}" }}")
            
            // Partir de la semaine sélectionnée et remonter pour compter les consécutives
            var checkYear = targetYear
            var checkWeek = targetWeek
            
            android.util.Log.d("FirebaseRepo", "🔍 PALMARES_CONSECUTIVE_START_POINT: Démarrage depuis semaine sélectionnée $checkWeek/$checkYear")
            
            while (true) {
                // Vérifier si cette semaine est dans la liste des semaines référencées
                val found = sortedWeeks.contains(Pair(checkYear, checkWeek))
                android.util.Log.d("FirebaseRepo", "🔍 PALMARES_CONSECUTIVE_CHECK: semaine $checkWeek/$checkYear: ${if(found) "✅ TROUVÉE" else "❌ MANQUANTE"} - Total consécutif actuel: $consecutiveWeeks")
                
                if (found) {
                    consecutiveWeeks++
                    android.util.Log.d("FirebaseRepo", "📈 PALMARES_CONSECUTIVE_INCREMENT: $consecutiveWeeks semaines consécutives")
                } else {
                    // Dès qu'on trouve une semaine manquante, on arrête
                    android.util.Log.d("FirebaseRepo", "⏹️ PALMARES_CONSECUTIVE_STOP: Arrêt du comptage consécutif à $consecutiveWeeks semaines (semaine manquante: $checkWeek/$checkYear)")
                    break
                }
                
                // Passer à la semaine précédente
                val previousCheckWeek = checkWeek
                val previousCheckYear = checkYear
                if (checkWeek > 1) {
                    checkWeek--
                } else {
                    checkWeek = 52
                    checkYear--
                }
                android.util.Log.d("FirebaseRepo", "⬅️ PALMARES_CONSECUTIVE_PREVIOUS: $previousCheckWeek/$previousCheckYear -> $checkWeek/$checkYear")
                
                // Sécurité : ne pas remonter avant octobre 2024
                if (checkYear < octoberYear || 
                    (checkYear == octoberYear && checkWeek < octoberFirstWeek)) {
                    android.util.Log.d("FirebaseRepo", "🛑 PALMARES_CONSECUTIVE_LIMIT_OCT: Limite octobre 2024 atteinte ($checkWeek/$checkYear)")
                    break
                }
                
                // Sécurité : limite à 52 semaines pour éviter boucle infinie
                if (consecutiveWeeks >= 52) {
                    android.util.Log.d("FirebaseRepo", "🛑 PALMARES_CONSECUTIVE_LIMIT_52: Limite sécurité 52 semaines atteinte")
                    break
                }
            }
            
            android.util.Log.d("FirebaseRepo", "📊 PALMARES_CONSECUTIVE_FINAL: $consecutiveWeeks semaines consécutives")
        } else {
            android.util.Log.d("FirebaseRepo", "❌ PALMARES_CONSECUTIVE_EMPTY: Aucune semaine trouvée, consécutif = 0")
        }
        
        val totalReferences = referencedWeeks.size
        val endTime = System.currentTimeMillis()
        
        // Calculer le pourcentage des semaines depuis octobre jusqu'à la semaine actuelle
        val totalWeeksSinceOctober = weeksToCheck.size
        val percentage = if (totalWeeksSinceOctober > 0) {
            (totalReferences * 100) / totalWeeksSinceOctober
        } else {
            0
        }
        
        android.util.Log.d("FirebaseRepo", "📊 PALMARES_RESULT: '$productName' - $totalQueries requêtes, $_foundReferences références trouvées, $consecutiveWeeks consécutives, $totalReferences total ($percentage% de $totalWeeksSinceOctober semaines) en ${endTime - startTime}ms")
        
        val result = ProductPalmares(
            consecutiveWeeks = consecutiveWeeks,
            totalReferences = totalReferences,
            referencedWeeks = referencedWeeks,
            percentage = percentage
        )
        
        android.util.Log.d("FirebaseRepo", "📊 PALMARES_END: Retour résultat - consécutif: ${result.consecutiveWeeks}, total: ${result.totalReferences}, pourcentage: $percentage%")
        
        return result
    }
    
    // Ancienne méthode dépréciée - gardée pour compatibilité
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
    
}