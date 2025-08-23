package com.nextjsclient.android.ui.overview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
import com.nextjsclient.android.utils.SupplierPreferences

class OverviewFragment : Fragment() {
    
    private var _binding: FragmentOverviewBinding? = null
    val binding get() = _binding!!
    
    private val viewModel: ScamarkViewModel by activityViewModels()
    lateinit var supplierPreferences: SupplierPreferences
    
    // Helper pour l'interface moderne
    private lateinit var modernHelper: ModernOverviewHelper
    
    // Stocker les produits de la semaine précédente pour calcul entrants/sortants
    private var previousWeekProducts: List<com.nextjsclient.android.data.models.ScamarkProduct> = emptyList()
    
    // Cache des données par fournisseur pour navigation instantanée
    private var preloadedAnecoopProducts: List<com.nextjsclient.android.data.models.ScamarkProduct> = emptyList()
    private var preloadedSolagoraProducts: List<com.nextjsclient.android.data.models.ScamarkProduct> = emptyList()
    private var preloadedAnecoopWeeks: List<com.nextjsclient.android.data.models.AvailableWeek> = emptyList()
    private var preloadedSolagoraWeeks: List<com.nextjsclient.android.data.models.AvailableWeek> = emptyList()
    
    // État du top SCA
    private var isShowingTopSca = false
    private var topScaSupplier: String? = null
    
    // BroadcastReceiver pour écouter les changements de préférences
    private val preferencesReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SupplierPreferences.ACTION_SUPPLIER_PREFERENCES_CHANGED) {
                android.util.Log.d("OverviewFragment", "📡 Received preferences changed broadcast - refreshing cards")
                // Recalculer et afficher les stats avec les nouvelles préférences
                viewModel.products.value?.let { products ->
                    calculateAndDisplayStats(products)
                }
            }
        }
    }
    
    
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
        
        // Initialiser les préférences fournisseurs
        supplierPreferences = SupplierPreferences(requireContext())
        
        // Initialiser le helper moderne
        modernHelper = ModernOverviewHelper(this)
        
        // Initialiser la visibilité des cartes selon les préférences
        initializeCardVisibility()
        
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
    
    override fun onResume() {
        super.onResume()
        // Enregistrer le BroadcastReceiver pour écouter les changements de préférences
        val filter = IntentFilter(SupplierPreferences.ACTION_SUPPLIER_PREFERENCES_CHANGED)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(preferencesReceiver, filter)
        android.util.Log.d("OverviewFragment", "📡 BroadcastReceiver registered for preferences changes")
        
        // Mettre à jour la visibilité des cartes au cas où les préférences auraient changé
        // pendant que le fragment était en pause (ex: dans les paramètres)
        updateCardVisibilityOnResume()
    }
    
    override fun onPause() {
        super.onPause()
        // Désenregistrer le BroadcastReceiver
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(preferencesReceiver)
        android.util.Log.d("OverviewFragment", "📡 BroadcastReceiver unregistered")
    }
    
    private fun initializeCardVisibility() {
        // Appliquer immédiatement la visibilité des cartes selon les préférences
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        val topScaCard = binding.root.findViewById<View>(R.id.topScaCard)
        
        anecoopCard?.visibility = if (supplierPreferences.isAnecoopEnabled) View.VISIBLE else View.GONE
        solagoraCard?.visibility = if (supplierPreferences.isSolagoraEnabled) View.VISIBLE else View.GONE
        
        // Gérer la carte top SCA et les boutons trophée
        updateTopScaVisibility()
        
        android.util.Log.d("OverviewFragment", "🎬 Initial card visibility - Anecoop: ${if (supplierPreferences.isAnecoopEnabled) "VISIBLE" else "GONE"}, Solagora: ${if (supplierPreferences.isSolagoraEnabled) "VISIBLE" else "GONE"}")
    }
    
    private fun updateCardVisibilityOnResume() {
        // Mettre à jour la visibilité et recalculer les stats si nécessaire
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        val newAnecoopVisibility = if (supplierPreferences.isAnecoopEnabled) View.VISIBLE else View.GONE
        val newSolagoraVisibility = if (supplierPreferences.isSolagoraEnabled) View.VISIBLE else View.GONE
        
        val visibilityChanged = (anecoopCard?.visibility != newAnecoopVisibility) || 
                               (solagoraCard?.visibility != newSolagoraVisibility)
        
        if (visibilityChanged) {
            anecoopCard?.visibility = newAnecoopVisibility
            solagoraCard?.visibility = newSolagoraVisibility
            
            // Mettre à jour le top SCA quand les préférences changent
            updateTopScaVisibility()
            
            android.util.Log.d("OverviewFragment", "🔄 Card visibility changed on resume - recalculating stats")
            
            // Recalculer et afficher les stats avec les nouvelles préférences
            viewModel.products.value?.let { products ->
                calculateAndDisplayStats(products)
            }
        } else {
            android.util.Log.d("OverviewFragment", "✅ Card visibility unchanged on resume")
        }
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
                
                // Debug: vérifier le contenu des produits préchargés
                android.util.Log.d("OverviewFragment", "🔍 DEBUG preloadedAnecoopProducts:")
                preloadedAnecoopProducts.take(3).forEachIndexed { index, product ->
                    android.util.Log.d("OverviewFragment", "   [$index] ${product.productName} - SCA: ${product.totalScas}")
                }
                android.util.Log.d("OverviewFragment", "🔍 DEBUG preloadedSolagoraProducts:")
                preloadedSolagoraProducts.take(3).forEachIndexed { index, product ->
                    android.util.Log.d("OverviewFragment", "   [$index] ${product.productName} - SCA: ${product.totalScas}")
                }
                
                // Mettre à jour les UI sur le thread principal
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        android.util.Log.d("OverviewFragment", "🔥 EXÉCUTION viewModel.setProducts sur UI thread")
                        // Activer les boutons trophée maintenant que les données sont chargées
                        enableTrophyButtons()
                        // Auto-refresh du top SCA si un seul fournisseur est activé
                        refreshTopScaIfNeeded()
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
                
                // Précharger les données des fournisseurs pour le top SCA
                preloadedAnecoopProducts = repository.getWeekDecisions(year, week, "anecoop")
                preloadedSolagoraProducts = repository.getWeekDecisions(year, week, "solagora")
                
                android.util.Log.d("OverviewFragment", "🔍 DEBUG preloadedAnecoopProducts après chargement:")
                preloadedAnecoopProducts.take(3).forEachIndexed { index, product ->
                    android.util.Log.d("OverviewFragment", "   [$index] ${product.productName} - SCA: ${product.totalScas}")
                }
                android.util.Log.d("OverviewFragment", "🔍 DEBUG preloadedSolagoraProducts après chargement:")
                preloadedSolagoraProducts.take(3).forEachIndexed { index, product ->
                    android.util.Log.d("OverviewFragment", "   [$index] ${product.productName} - SCA: ${product.totalScas}")
                }
                
                // Activer les boutons trophée maintenant que les données sont chargées
                activity?.runOnUiThread {
                    enableTrophyButtons()
                    // Auto-refresh du top SCA si un seul fournisseur est activé
                    refreshTopScaIfNeeded()
                }
                
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
        // Le bouton Settings a été supprimé avec le bandeau vert
        // Setup trophy buttons
        setupTrophyButtons()
    }
    
    private fun setupTrophyButtons() {
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        // Désactiver les boutons trophée jusqu'au chargement des données
        val anecoopTrophy = anecoopCard?.findViewById<View>(R.id.trophyButton)
        val solagoraTrophy = solagoraCard?.findViewById<View>(R.id.trophyButton)
        
        anecoopTrophy?.apply {
            isClickable = false
            alpha = 0.5f
            setOnClickListener {
                if (isClickable) showTopScaForSupplier("anecoop")
            }
        }
        
        solagoraTrophy?.apply {
            isClickable = false
            alpha = 0.5f
            setOnClickListener {
                if (isClickable) showTopScaForSupplier("solagora")
            }
        }
        
        // Setup close button for top SCA card
        val topScaCard = binding.root.findViewById<View>(R.id.topScaCard)
        topScaCard?.findViewById<View>(R.id.closeTopScaButton)?.setOnClickListener {
            hideTopSca()
        }
    }
    
    
    private fun enableTrophyButtons() {
        android.util.Log.d("OverviewFragment", "🏆 Activation des boutons trophée après chargement des données")
        
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        anecoopCard?.findViewById<View>(R.id.trophyButton)?.apply {
            isClickable = true
            alpha = 1.0f
        }
        
        solagoraCard?.findViewById<View>(R.id.trophyButton)?.apply {
            isClickable = true
            alpha = 1.0f
        }
    }
    
    private fun refreshTopScaIfNeeded() {
        val anecoopEnabled = supplierPreferences.isAnecoopEnabled
        val solagoraEnabled = supplierPreferences.isSolagoraEnabled
        val singleSupplier = (anecoopEnabled && !solagoraEnabled) || (!anecoopEnabled && solagoraEnabled)
        
        if (singleSupplier && isShowingTopSca && topScaSupplier != null) {
            android.util.Log.d("OverviewFragment", "🔄 Refresh du top SCA pour $topScaSupplier après chargement des données")
            loadTopScaData(topScaSupplier!!)
        } else if (singleSupplier && !isShowingTopSca) {
            android.util.Log.d("OverviewFragment", "🔄 Affichage automatique du top SCA après chargement des données")
            val supplier = if (anecoopEnabled) "anecoop" else "solagora"
            showTopScaForSupplier(supplier, autoShow = true)
        }
    }
    
    private fun updateTopScaVisibility() {
        val anecoopEnabled = supplierPreferences.isAnecoopEnabled
        val solagoraEnabled = supplierPreferences.isSolagoraEnabled
        val bothEnabled = anecoopEnabled && solagoraEnabled
        val singleSupplier = (anecoopEnabled && !solagoraEnabled) || (!anecoopEnabled && solagoraEnabled)
        
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        val topScaCard = binding.root.findViewById<View>(R.id.topScaCard)
        
        // Boutons trophée visibles seulement si les deux fournisseurs sont activés
        anecoopCard?.findViewById<View>(R.id.trophyButton)?.visibility = if (bothEnabled) View.VISIBLE else View.GONE
        solagoraCard?.findViewById<View>(R.id.trophyButton)?.visibility = if (bothEnabled) View.VISIBLE else View.GONE
        
        if (singleSupplier && !isShowingTopSca) {
            // Un seul fournisseur activé -> afficher automatiquement le top SCA
            val supplier = if (anecoopEnabled) "anecoop" else "solagora"
            showTopScaForSupplier(supplier, autoShow = true)
        } else if (bothEnabled && isShowingTopSca) {
            // Les deux fournisseurs sont activés -> masquer le top SCA automatique
            hideTopSca()
        }
    }
    
    private fun showTopScaForSupplier(supplier: String, autoShow: Boolean = false) {
        android.util.Log.d("OverviewFragment", "🏆 showTopScaForSupplier appelé pour $supplier (autoShow: $autoShow)")
        
        isShowingTopSca = true
        topScaSupplier = supplier
        
        val topScaCard = binding.root.findViewById<View>(R.id.topScaCard)
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        // Animation d'apparition
        topScaCard?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            scaleY = 0.8f
            animate()
                .alpha(1f)
                .scaleY(1f)
                .setDuration(300)
                .start()
        }
        
        // Masquer/afficher le bouton fermer selon le mode
        topScaCard?.findViewById<View>(R.id.closeTopScaButton)?.visibility = if (autoShow) View.GONE else View.VISIBLE
        
        if (!autoShow) {
            // Mode manuel : masquer l'autre fournisseur
            if (supplier == "anecoop") {
                solagoraCard?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
                    solagoraCard.visibility = View.GONE
                }?.start()
            } else {
                anecoopCard?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
                    anecoopCard.visibility = View.GONE
                }?.start()
            }
        }
        
        // Charger et afficher les données du top SCA
        loadTopScaData(supplier)
    }
    
    private fun hideTopSca() {
        isShowingTopSca = false
        topScaSupplier = null
        
        val topScaCard = binding.root.findViewById<View>(R.id.topScaCard)
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        // Animation de disparition
        topScaCard?.animate()
            ?.alpha(0f)
            ?.scaleY(0.8f)
            ?.setDuration(200)
            ?.withEndAction {
                topScaCard.visibility = View.GONE
            }?.start()
        
        // Réafficher les cartes fournisseurs
        if (supplierPreferences.isAnecoopEnabled) {
            anecoopCard?.apply {
                visibility = View.VISIBLE
                alpha = 0f
                animate().alpha(1f).setDuration(300).start()
            }
        }
        
        if (supplierPreferences.isSolagoraEnabled) {
            solagoraCard?.apply {
                visibility = View.VISIBLE
                alpha = 0f
                animate().alpha(1f).setDuration(300).start()
            }
        }
    }
    
    private fun loadTopScaData(supplier: String) {
        val topScaCard = binding.root.findViewById<View>(R.id.topScaCard)
        
        // Mettre à jour le nom du fournisseur
        topScaCard?.findViewById<TextView>(R.id.topScaSupplierName)?.text = supplier.uppercase()
        
        // Obtenir les produits du fournisseur pour la semaine courante
        val products = when (supplier) {
            "anecoop" -> preloadedAnecoopProducts
            "solagora" -> preloadedSolagoraProducts
            else -> emptyList()
        }
        
        android.util.Log.d("OverviewFragment", "🏆 Top SCA pour $supplier: ${products.size} produits disponibles")
        
        // Debug: afficher tous les produits et leurs SCA
        products.forEachIndexed { index, product ->
            android.util.Log.d("OverviewFragment", "   Produit $index: ${product.productName} - SCA: ${product.totalScas} - Supplier: ${product.supplier}")
        }
        
        // Calculer le top 3 des SCA
        val productsWithSca = products.filter { it.totalScas > 0 }
        android.util.Log.d("OverviewFragment", "🔍 Produits avec SCA > 0: ${productsWithSca.size}")
        
        val topProducts = productsWithSca
            .sortedByDescending { it.totalScas }
            .take(3)
        
        android.util.Log.d("OverviewFragment", "🏆 Top 3 SCA final: ${topProducts.size} produits")
        topProducts.forEachIndexed { index, product ->
            android.util.Log.d("OverviewFragment", "   TOP ${index + 1}: ${product.productName} (${product.totalScas} SCA)")
        }
        
        // Debug: vérifier si le problème vient de l'affichage
        if (topProducts.isEmpty()) {
            android.util.Log.w("OverviewFragment", "⚠️ AUCUN PRODUIT dans le top SCA!")
            android.util.Log.w("OverviewFragment", "   - Total produits: ${products.size}")
            android.util.Log.w("OverviewFragment", "   - Produits avec SCA > 0: ${productsWithSca.size}")
            android.util.Log.w("OverviewFragment", "   - Supplier filter: $supplier")
        }
        
        // Mettre à jour l'affichage
        updateTopScaDisplay(topProducts, topScaCard)
    }
    
    private fun updateTopScaDisplay(products: List<com.nextjsclient.android.data.models.ScamarkProduct>, topScaCard: View?) {
        android.util.Log.d("OverviewFragment", "🎯 updateTopScaDisplay appelé avec ${products.size} produits")
        
        topScaCard?.let { card ->
            android.util.Log.d("OverviewFragment", "✅ TopScaCard trouvé, mise à jour de l'affichage")
            
            // Top 1
            if (products.isNotEmpty()) {
                val product1 = products[0]
                android.util.Log.d("OverviewFragment", "🥇 TOP 1: ${product1.productName} avec ${product1.totalScas} SCA")
                
                val nameView = card.findViewById<TextView>(R.id.topSca1Name)
                val detailsView = card.findViewById<TextView>(R.id.topSca1Details)
                val scaView = card.findViewById<TextView>(R.id.topSca1Sca)
                val containerView = card.findViewById<View>(R.id.topSca1)
                
                android.util.Log.d("OverviewFragment", "🔍 Vues trouvées - Name: $nameView, Details: $detailsView, SCA: $scaView, Container: $containerView")
                
                nameView?.text = product1.productName.split(" ").take(3).joinToString(" ")
                detailsView?.text = "${product1.supplier}"
                scaView?.text = "${product1.totalScas} SCA"
                containerView?.visibility = View.VISIBLE
            } else {
                android.util.Log.w("OverviewFragment", "❌ Aucun produit pour TOP 1")
                card.findViewById<View>(R.id.topSca1)?.visibility = View.GONE
            }
            
            // Top 2
            if (products.size > 1) {
                val product2 = products[1]
                card.findViewById<TextView>(R.id.topSca2Name)?.text = product2.productName.split(" ").take(3).joinToString(" ")
                card.findViewById<TextView>(R.id.topSca2Details)?.text = "${product2.supplier}"
                card.findViewById<TextView>(R.id.topSca2Sca)?.text = "${product2.totalScas} SCA"
                card.findViewById<View>(R.id.topSca2)?.visibility = View.VISIBLE
            } else {
                card.findViewById<View>(R.id.topSca2)?.visibility = View.GONE
            }
            
            // Top 3
            if (products.size > 2) {
                val product3 = products[2]
                card.findViewById<TextView>(R.id.topSca3Name)?.text = product3.productName.split(" ").take(3).joinToString(" ")
                card.findViewById<TextView>(R.id.topSca3Details)?.text = "${product3.supplier}"
                card.findViewById<TextView>(R.id.topSca3Sca)?.text = "${product3.totalScas} SCA"
                card.findViewById<View>(R.id.topSca3)?.visibility = View.VISIBLE
            } else {
                card.findViewById<View>(R.id.topSca3)?.visibility = View.GONE
            }
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
                android.util.Log.d("OverviewFragment", "🔄 Pull-to-refresh triggered")
                android.util.Log.d("OverviewFragment", "🔄 SwipeRefresh state BEFORE: $isRefreshing")
                // Activer le loader immédiatement
                isRefreshing = true
                android.util.Log.d("OverviewFragment", "🔄 SwipeRefresh state AFTER: $isRefreshing")
                android.util.Log.d("OverviewFragment", "🔄 Calling viewModel.refresh()")
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
            android.util.Log.d("OverviewFragment", "🔄 Observer isLoading triggered: $isLoading")
            android.util.Log.d("OverviewFragment", "🔄 SwipeRefresh current state: ${binding.swipeRefresh.isRefreshing}")
            binding.swipeRefresh.isRefreshing = isLoading
            android.util.Log.d("OverviewFragment", "🔄 SwipeRefresh NEW state: ${binding.swipeRefresh.isRefreshing}")
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
        
        // Appliquer le filtre fournisseur selon les préférences
        val filteredProducts = products.filter { product ->
            val supplier = product.supplier.lowercase()
            when (supplier) {
                "anecoop" -> supplierPreferences.isAnecoopEnabled
                "solagora" -> supplierPreferences.isSolagoraEnabled
                else -> true // Garder les autres fournisseurs inconnus
            }
        }
        
        android.util.Log.d("OverviewFragment", "🎯 FILTRE FOURNISSEUR: ${products.size} -> ${filteredProducts.size} produits")
        android.util.Log.d("OverviewFragment", "🎯 Anecoop=${supplierPreferences.isAnecoopEnabled}, Solagora=${supplierPreferences.isSolagoraEnabled}")
        
        // Séparer par fournisseur (après filtrage)
        val anecoopProducts = filteredProducts.filter { it.supplier.lowercase() == "anecoop" }
        val solagoraProducts = filteredProducts.filter { it.supplier.lowercase() == "solagora" }
        
        android.util.Log.d("OverviewFragment", "📊 Répartition: Anecoop=${anecoopProducts.size}, Solagora=${solagoraProducts.size}")
        
        // Appliquer le même filtre à la semaine précédente
        val filteredPreviousProducts = previousWeekProducts.filter { product ->
            val supplier = product.supplier.lowercase()
            when (supplier) {
                "anecoop" -> supplierPreferences.isAnecoopEnabled
                "solagora" -> supplierPreferences.isSolagoraEnabled
                else -> true
            }
        }
        
        // Séparer semaine précédente par fournisseur  
        val previousAnecoopProducts = filteredPreviousProducts.filter { it.supplier.lowercase() == "anecoop" }
        val previousSolagoraProducts = filteredPreviousProducts.filter { it.supplier.lowercase() == "solagora" }
        
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
    private fun loadPreviousWeekData(_currentYear: Int, _currentWeek: Int) {
        // Méthode conservée pour compatibilité mais ne fait plus rien
        return
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
    private fun preloadSupplierData(_year: Int, _week: Int) {
        // Méthode conservée pour compatibilité mais ne fait plus rien
        return
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
    
    
    fun toggleSearchMode() {
        // TODO: Implémenter la recherche sur l'aperçu si nécessaire
        android.util.Log.d("OverviewFragment", "🔍 Recherche sur l'aperçu pas encore implémentée")
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
        val weekShort = getString(R.string.week_short)
        val weekText = "$weekShort$weekStr - $dates"
        
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
        
        val dateFormat = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
        val separator = getString(R.string.date_range_separator)
        
        return "${dateFormat.format(weekStart)} $separator ${dateFormat.format(weekEnd)}"
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
    
    /**
     * Affiche la liste des semaines pour un fournisseur (appelé depuis MainActivity)
     */
    fun showWeekListForSupplier(supplier: String) {
        android.util.Log.d("OverviewFragment", "📅 Affichage liste des semaines pour $supplier")
        // TODO: Implémenter l'affichage d'une liste des semaines disponibles pour ce fournisseur
        // Cette fonction remplace la fonctionnalité du bouton historique du sélecteur de semaine
    }
    
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}