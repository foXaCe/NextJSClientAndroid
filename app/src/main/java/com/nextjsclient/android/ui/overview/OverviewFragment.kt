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
import com.nextjsclient.android.MainActivity
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
        
        // Initialiser les préférences fournisseurs
        supplierPreferences = SupplierPreferences(requireContext())
        
        // Initialiser le helper moderne
        modernHelper = ModernOverviewHelper(this)
        
        // Initialiser la visibilité des cartes selon les préférences
        initializeCardVisibility()
        
        // Mettre à jour l'affichage du numéro de semaine
        updateWeekNumberDisplay()
        
        setupButtons()
        setupSwipeRefresh()
        observeViewModel()
        
        // Vérifier s'il y a une semaine déjà sélectionnée
        val selectedYear = viewModel.selectedYear.value
        val selectedWeek = viewModel.selectedWeek.value
        
        if (selectedYear != null && selectedWeek != null) {
            // PRIORITÉ: Utiliser la semaine déjà sélectionnée dans le ViewModel
            loadDataForWeek(selectedYear, selectedWeek)
        } else {
            // Fallback seulement si aucune semaine n'est vraiment sélectionnée
            val calendar = java.util.Calendar.getInstance()
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            val currentWeek = getCurrentISOWeek()
            viewModel.selectWeek(currentYear, currentWeek)
            loadDataForWeek(currentYear, currentWeek)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Enregistrer le BroadcastReceiver pour écouter les changements de préférences
        val filter = IntentFilter(SupplierPreferences.ACTION_SUPPLIER_PREFERENCES_CHANGED)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(preferencesReceiver, filter)
        
        // Mettre à jour la visibilité des cartes au cas où les préférences auraient changé
        // pendant que le fragment était en pause (ex: dans les paramètres)
        updateCardVisibilityOnResume()
    }
    
    override fun onPause() {
        super.onPause()
        // Désenregistrer le BroadcastReceiver
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(preferencesReceiver)
    }
    
    private fun initializeCardVisibility() {
        // Appliquer immédiatement la visibilité des cartes selon les préférences
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        anecoopCard?.visibility = if (supplierPreferences.isAnecoopEnabled) View.VISIBLE else View.GONE
        solagoraCard?.visibility = if (supplierPreferences.isSolagoraEnabled) View.VISIBLE else View.GONE
        
        // Gérer la carte top SCA et les boutons trophée
        updateTopScaVisibility()
        
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
            
            // Recalculer et afficher les stats avec les nouvelles préférences
            viewModel.products.value?.let { products ->
                calculateAndDisplayStats(products)
            }
        }
    }
    
    private fun loadInitialData() {
        
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
        
        
        viewModel.viewModelScope.launch {
            try {
                val repository = com.nextjsclient.android.data.repository.FirebaseRepository()
                
                // Charger les données en parallèle avec coroutines
                val currentProductsDeferred = async {
                    repository.getWeekDecisions(targetYear, targetWeek, "all")
                }
                
                val (previousYear, previousWeek) = getPreviousWeek(targetYear, targetWeek)
                val previousProductsDeferred = async {
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
                
                
                // Mettre à jour les UI sur le thread principal
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        // Activer les boutons trophée maintenant que les données sont chargées
                        enableTrophyButtons()
                        // Auto-refresh du top SCA si un seul fournisseur est activé
                        refreshTopScaIfNeeded()
                        // Mettre à jour les produits dans le ViewModel
                        viewModel.setProducts(currentProducts)
                        
                        // Maintenant que previousWeekProducts est chargé, recalculer les stats correctement
                        calculateAndDisplayStats(currentProducts)
                    }
                }
                
            } catch (e: Exception) {
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
        
        viewModel.viewModelScope.launch {
            try {
                val repository = com.nextjsclient.android.data.repository.FirebaseRepository()
                
                // Charger les données de la semaine demandée
                val weekProducts = repository.getWeekDecisions(year, week, "all")
                
                // Charger la semaine précédente pour calculer entrants/sortants
                val (previousYear, previousWeek) = getPreviousWeek(year, week)
                previousWeekProducts = repository.getWeekDecisions(previousYear, previousWeek, "all")
                
                // Précharger les données des fournisseurs pour le top SCA
                preloadedAnecoopProducts = repository.getWeekDecisions(year, week, "anecoop")
                preloadedSolagoraProducts = repository.getWeekDecisions(year, week, "solagora")
                
                
                // Activer les boutons trophée maintenant que les données sont chargées
                activity?.runOnUiThread {
                    enableTrophyButtons()
                    // Auto-refresh du top SCA si un seul fournisseur est activé
                    refreshTopScaIfNeeded()
                }
                
                
                // Mettre à jour l'UI
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        viewModel.setProducts(weekProducts)
                        
                        // Maintenant que previousWeekProducts est chargé, recalculer les stats correctement
                        calculateAndDisplayStats(weekProducts)
                    }
                }
                
            } catch (e: Exception) {
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
            // Animation Material 3 simple
            binding.settingsButton.animate()
                .rotationBy(180f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(200)
                .withEndAction {
                    binding.settingsButton.animate()
                        .rotationBy(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                    
                    // Marquer comme navigation interne
                    (activity as? MainActivity)?.markInternalNavigation()
                    val intent = Intent(requireContext(), SettingsActivity::class.java)
                    startActivity(intent)
                }
                .start()
        }
        
        // Setup trophy buttons
        setupTrophyButtons()
    }
    
    private fun setupTrophyButtons() {
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        val topScaCard = binding.root.findViewById<View>(R.id.topScaCard)
        
        // Désactiver les boutons trophée jusqu'au chargement des données
        val anecoopTrophy = anecoopCard?.findViewById<View>(R.id.trophyButton)
        val solagoraTrophy = solagoraCard?.findViewById<View>(R.id.trophyButton)
        
        anecoopTrophy?.apply {
            isClickable = false
            alpha = 0.5f
            setOnClickListener {
                if (isClickable) {
                    // Toggle : si le top SCA est déjà visible pour ce fournisseur, le fermer
                    if (isShowingTopSca && topScaSupplier == "anecoop") {
                        hideTopSca()
                    } else {
                        showTopScaForSupplier("anecoop")
                    }
                }
            }
        }
        
        solagoraTrophy?.apply {
            isClickable = false
            alpha = 0.5f
            setOnClickListener {
                if (isClickable) {
                    // Toggle : si le top SCA est déjà visible pour ce fournisseur, le fermer
                    if (isShowingTopSca && topScaSupplier == "solagora") {
                        hideTopSca()
                    } else {
                        showTopScaForSupplier("solagora")
                    }
                }
            }
        }
        
        // Setup close button for top SCA card
        topScaCard?.findViewById<View>(R.id.closeTopScaButton)?.setOnClickListener {
            hideTopSca()
        }
    }
    
    
    private fun enableTrophyButtons() {
        
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
            loadTopScaData(topScaSupplier!!)
        } else if (singleSupplier && !isShowingTopSca) {
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
        
        isShowingTopSca = true
        topScaSupplier = supplier
        
        val topScaCard = binding.root.findViewById<View>(R.id.topScaCard)
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        val settingsButton = binding.root.findViewById<View>(R.id.settingsButton)
        
        // Animation d'apparition Material 3 expressive
        topScaCard?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
            translationY = 50f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(400)
                .setInterpolator(com.google.android.material.animation.AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR)
                .start()
        }
        
        // Masquer/afficher le bouton fermer selon le mode
        topScaCard?.findViewById<View>(R.id.closeTopScaButton)?.visibility = if (autoShow) View.GONE else View.VISIBLE
        
        // S'assurer que le bouton paramètres reste visible et au-dessus
        settingsButton?.apply {
            bringToFront()
            elevation = 12f // Plus élevé que la carte (8dp)
        }
        
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
        
        // Animation de disparition Material 3 expressive
        topScaCard?.animate()
            ?.alpha(0f)
            ?.scaleX(0.9f)
            ?.scaleY(0.9f)
            ?.translationY(30f)
            ?.setDuration(300)
            ?.setInterpolator(com.google.android.material.animation.AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR)
            ?.withEndAction {
                topScaCard.visibility = View.GONE
                topScaCard.scaleX = 1f
                topScaCard.scaleY = 1f
                topScaCard.translationY = 0f
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
        
        
        // Debug: afficher tous les produits et leurs SCA
        
        // Calculer le top 3 des SCA
        val productsWithSca = products.filter { it.totalScas > 0 }
        
        val topProducts = productsWithSca
            .sortedByDescending { it.totalScas }
            .take(3)
        
        
        // Debug: vérifier si le problème vient de l'affichage
        if (topProducts.isEmpty()) {
        }
        
        // Mettre à jour l'affichage
        updateTopScaDisplay(topProducts, topScaCard)
    }
    
    private fun updateTopScaDisplay(products: List<com.nextjsclient.android.data.models.ScamarkProduct>, topScaCard: View?) {
        
        topScaCard?.let { card ->
            
            // Top 1
            if (products.isNotEmpty()) {
                val product1 = products[0]
                
                val nameView = card.findViewById<TextView>(R.id.topSca1Name)
                val detailsView = card.findViewById<TextView>(R.id.topSca1Details)
                val scaView = card.findViewById<TextView>(R.id.topSca1Sca)
                val containerView = card.findViewById<View>(R.id.topSca1)
                
                
                nameView?.text = product1.productName.split(" ").take(3).joinToString(" ")
                detailsView?.text = "${product1.supplier}"
                scaView?.text = "${product1.totalScas} SCA"
                containerView?.visibility = View.VISIBLE
            } else {
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
                // Activer le loader immédiatement
                isRefreshing = true
                viewModel.refresh()
            }
        }
    }
    
    private fun updateWeekNumberDisplay() {
        val weekNumber = viewModel.selectedWeek.value ?: java.util.Calendar.getInstance().get(java.util.Calendar.WEEK_OF_YEAR)
        val year = viewModel.selectedYear.value ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        
        // Mettre à jour le numéro de semaine
        binding.root.findViewById<TextView>(R.id.weekNumberHeader)?.text = "Semaine $weekNumber"
        
        // Calculer et afficher la plage de dates
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.YEAR, year)
        calendar.set(java.util.Calendar.WEEK_OF_YEAR, weekNumber)
        calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        
        val startDate = calendar.time
        calendar.add(java.util.Calendar.DAY_OF_WEEK, 6)
        val endDate = calendar.time
        
        // Formats pour éviter la répétition de l'année
        val dateFormatWithYear = java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.FRANCE)
        val dateFormatWithoutYear = java.text.SimpleDateFormat("d MMMM", java.util.Locale.FRANCE)
        
        val startCalendar = java.util.Calendar.getInstance().apply { time = startDate }
        val endCalendar = java.util.Calendar.getInstance().apply { time = endDate }
        
        val dateRangeText = if (startCalendar.get(java.util.Calendar.YEAR) == endCalendar.get(java.util.Calendar.YEAR)) {
            // Même année et même mois : "19 au 25 août 2025"
            if (startCalendar.get(java.util.Calendar.MONTH) == endCalendar.get(java.util.Calendar.MONTH)) {
                val dayOnlyFormat = java.text.SimpleDateFormat("d", java.util.Locale.FRANCE)
                val startDay = dayOnlyFormat.format(startDate)
                val endStr = dateFormatWithYear.format(endDate)
                "$startDay au $endStr"
            } else {
                // Même année mais mois différents : "28 décembre au 3 janvier 2025"
                val startStr = dateFormatWithoutYear.format(startDate)
                val endStr = dateFormatWithYear.format(endDate)
                "$startStr au $endStr"
            }
        } else {
            // Années différentes : "28 décembre 2024 au 3 janvier 2025"
            val startStr = dateFormatWithYear.format(startDate)
            val endStr = dateFormatWithYear.format(endDate)
            "$startStr au $endStr"
        }
        
        binding.root.findViewById<TextView>(R.id.weekDateRange)?.text = dateRangeText
    }
    
    private fun observeViewModel() {
        
        var lastProcessedProducts: List<com.nextjsclient.android.data.models.ScamarkProduct>? = null
        
        viewModel.products.observe(viewLifecycleOwner) { products ->
            
            
            // Vérifier si c'est exactement la même liste que la dernière fois
            if (products == lastProcessedProducts) {
                return@observe
            }
            
            lastProcessedProducts = products
            
            
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
                    updateWeekNumberDisplay()
                    
                    if (!isInitialLoad) {
                        // Charger les données seulement si ce n'est pas le chargement initial
                        loadDataForWeek(year, week)
                    } else {
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
        
        // Appliquer le filtre fournisseur selon les préférences
        val filteredProducts = products.filter { product ->
            val supplier = product.supplier.lowercase()
            when (supplier) {
                "anecoop" -> supplierPreferences.isAnecoopEnabled
                "solagora" -> supplierPreferences.isSolagoraEnabled
                else -> true // Garder les autres fournisseurs inconnus
            }
        }
        
        
        // Séparer par fournisseur (après filtrage)
        val anecoopProducts = filteredProducts.filter { it.supplier.lowercase() == "anecoop" }
        val solagoraProducts = filteredProducts.filter { it.supplier.lowercase() == "solagora" }
        
        
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
        
        
        // Calculer les stats
        val anecoopStats = calculateStatsForProducts(anecoopProducts, previousAnecoopProducts)
        
        val solagoraStats = calculateStatsForProducts(solagoraProducts, previousSolagoraProducts)
        
        
        // Mettre à jour les dashboards simultanément
        updateSupplierDashboard("anecoop", anecoopStats)
        updateSupplierDashboard("solagora", solagoraStats)
        
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
            return Pair(0, 0)
        }
        
        // Obtenir les codes produits de chaque semaine
        val currentProductCodes = currentProducts.map { it.productName }.toSet()
        val previousProductCodes = previousProducts.map { it.productName }.toSet()
        
        
        // Produits entrants: présents cette semaine mais pas la semaine dernière
        val newProducts = currentProductCodes.subtract(previousProductCodes)
        val productsIn = newProducts.size
        
        // Produits sortants: présents la semaine dernière mais pas cette semaine
        val removedProducts = previousProductCodes.subtract(currentProductCodes)
        val productsOut = removedProducts.size
        
        
        return Pair(productsIn, productsOut)
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
    
    
    private fun updateSupplierDashboard(supplier: String, stats: ScamarkStats) {
        
        // Déléguer à ModernOverviewHelper pour la nouvelle UI moderne
        val isAnecoop = supplier.lowercase() == "anecoop"
        modernHelper.updateSupplierCard(supplier, stats, isAnecoop)
        
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
    }
    
    private fun navigateToSupplier(supplier: String) {
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        mainActivity?.let { activity ->
            // Obtenir la semaine et l'année sélectionnées
            val selectedYear = viewModel.selectedYear.value
            val selectedWeek = viewModel.selectedWeek.value
            
            // Passer les informations de semaine/année à MainActivity pour transmission au fragment
            if (selectedYear != null && selectedWeek != null) {
                activity.setSelectedWeekForNavigation(selectedYear, selectedWeek)
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
    
    private fun navigateToSupplierWithFilter(supplier: String, filter: String) {
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        mainActivity?.let { activity ->
            // Obtenir la semaine et l'année sélectionnées
            val selectedYear = viewModel.selectedYear.value
            val selectedWeek = viewModel.selectedWeek.value
            
            // Stocker seulement le filtre et laisser ScamarkFragment charger les bonnes données pour la semaine sélectionnée
            activity.setFilterOnly(supplier, filter)
            
            // Naviguer vers le fournisseur avec les informations de semaine/année
            val bottomNav = activity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.nextjsclient.android.R.id.bottom_navigation)
            
            // IMPORTANT: Passer les informations de semaine/année à MainActivity pour transmission au fragment
            if (selectedYear != null && selectedWeek != null) {
                activity.setSelectedWeekForNavigation(selectedYear, selectedWeek)
            }
            
            bottomNav?.selectedItemId = when (supplier) {
                "anecoop" -> com.nextjsclient.android.R.id.navigation_anecoop
                "solagora" -> com.nextjsclient.android.R.id.navigation_solagora
                else -> com.nextjsclient.android.R.id.navigation_anecoop
            }
        }
    }
    
    private fun navigateToSupplierWithEntrantsFilter(supplier: String, filter: String) {
        
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
        
        
        val preloadedWeeks = when (supplier) {
            "anecoop" -> preloadedAnecoopWeeks
            "solagora" -> preloadedSolagoraWeeks
            else -> emptyList()
        }
        
        
        // Stocker les données dans un cache global accessible par MainActivity
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        if (mainActivity != null) {
            
            // Passer SEULEMENT les vrais produits entrants avec le filtre "entrants"
            mainActivity.setPreloadedDataWithFilter(supplier, entrantProducts, preloadedWeeks, filter)
            
            // Naviguer vers le fournisseur
            val bottomNav = mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.nextjsclient.android.R.id.bottom_navigation)
            bottomNav?.selectedItemId = when (supplier) {
                "anecoop" -> com.nextjsclient.android.R.id.navigation_anecoop
                "solagora" -> com.nextjsclient.android.R.id.navigation_solagora
                else -> com.nextjsclient.android.R.id.navigation_anecoop
            }
        } else {
        }
        
    }
    
    private fun navigateToSupplierWithPreviousWeekFilter(supplier: String, filter: String) {
        
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
        
        
        val preloadedWeeks = when (supplier) {
            "anecoop" -> preloadedAnecoopWeeks
            "solagora" -> preloadedSolagoraWeeks
            else -> emptyList()
        }
        
        
        // Stocker les données dans un cache global accessible par MainActivity
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        if (mainActivity != null) {
            
            // Passer SEULEMENT les vrais produits sortants avec le filtre "sortants"
            mainActivity.setPreloadedDataWithFilter(supplier, sortantProducts, preloadedWeeks, filter)
            
            // Naviguer vers le fournisseur
            val bottomNav = mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.nextjsclient.android.R.id.bottom_navigation)
            bottomNav?.selectedItemId = when (supplier) {
                "anecoop" -> com.nextjsclient.android.R.id.navigation_anecoop
                "solagora" -> com.nextjsclient.android.R.id.navigation_solagora
                else -> com.nextjsclient.android.R.id.navigation_anecoop
            }
        } else {
        }
        
    }
    
    /**
     * Navigation vers un fournisseur avec filtre promotions
     */
    private fun navigateToSupplierWithPromoFilter(supplier: String, filter: String) {
        
        
        // Produits actuels du fournisseur
        val currentSupplierProducts = when (supplier) {
            "anecoop" -> {
                preloadedAnecoopProducts
            }
            "solagora" -> {
                preloadedSolagoraProducts
            }
            else -> {
                emptyList()
            }
        }
        
        // Vérifier que nous avons des données à traiter
        if (currentSupplierProducts.isEmpty()) {
            // Si pas de données préchargées, utiliser la méthode normale qui charge les données
            navigateToSupplierWithFilter(supplier, filter)
            return
        }
        
        // Filtrer seulement les produits en promotion
        val promoProducts = currentSupplierProducts.filter { it.isPromo }
        
        
        val preloadedWeeks = when (supplier) {
            "anecoop" -> preloadedAnecoopWeeks
            "solagora" -> preloadedSolagoraWeeks
            else -> emptyList()
        }
        
        
        // Stocker les données dans un cache global accessible par MainActivity
        val mainActivity = activity as? com.nextjsclient.android.MainActivity
        if (mainActivity != null) {
            
            // Passer SEULEMENT les vrais produits en promotion avec le filtre "promo"
            mainActivity.setPreloadedDataWithFilter(supplier, promoProducts, preloadedWeeks, filter)
            
            // Naviguer vers le fournisseur
            val bottomNav = mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(com.nextjsclient.android.R.id.bottom_navigation)
            bottomNav?.selectedItemId = when (supplier) {
                "anecoop" -> com.nextjsclient.android.R.id.navigation_anecoop
                "solagora" -> com.nextjsclient.android.R.id.navigation_solagora
                else -> com.nextjsclient.android.R.id.navigation_anecoop
            }
        } else {
        }
        
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
    fun showWeekListForSupplier(@Suppress("UNUSED_PARAMETER") supplier: String) {
        // TODO: Implémenter l'affichage d'une liste des semaines disponibles pour ce fournisseur
        // Cette fonction remplace la fonctionnalité du bouton historique du sélecteur de semaine
    }
    
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}