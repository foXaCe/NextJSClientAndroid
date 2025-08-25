package com.nextjsclient.android.ui.overview

import android.animation.ObjectAnimator
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
import com.nextjsclient.android.utils.TopProductAnimator
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
    
    // Tracking pour éviter les refresh inutiles
    private var lastDataLoadTime = 0L
    
    // Flag pour indiquer qu'on vient d'arrêter des animations continues
    private var justStoppedContinuousAnimations: Boolean = false
    private var isInitialLoad = true
    
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
        val startTime = System.currentTimeMillis()
        android.util.Log.d("OverviewFragment", "🚀 onViewCreated started")
        
        // Initialiser les préférences fournisseurs
        android.util.Log.d("OverviewFragment", "⚙️ Initializing supplier preferences...")
        supplierPreferences = SupplierPreferences(requireContext())
        android.util.Log.d("OverviewFragment", "✅ Supplier preferences initialized (${System.currentTimeMillis() - startTime}ms)")
        
        // Initialiser le helper moderne
        android.util.Log.d("OverviewFragment", "🎨 Initializing modern helper...")
        modernHelper = ModernOverviewHelper(this)
        android.util.Log.d("OverviewFragment", "✅ Modern helper initialized (${System.currentTimeMillis() - startTime}ms)")
        
        // Initialiser la visibilité des cartes selon les préférences
        android.util.Log.d("OverviewFragment", "👀 Initializing card visibility...")
        initializeCardVisibility()
        android.util.Log.d("OverviewFragment", "✅ Card visibility initialized (${System.currentTimeMillis() - startTime}ms)")
        
        // Mettre à jour l'affichage du numéro de semaine
        android.util.Log.d("OverviewFragment", "📅 Updating week number display...")
        updateWeekNumberDisplay()
        android.util.Log.d("OverviewFragment", "✅ Week number display updated (${System.currentTimeMillis() - startTime}ms)")
        
        android.util.Log.d("OverviewFragment", "🔘 Setting up buttons...")
        setupButtons()
        android.util.Log.d("OverviewFragment", "✅ Buttons setup complete (${System.currentTimeMillis() - startTime}ms)")
        
        android.util.Log.d("OverviewFragment", "🔄 Setting up swipe refresh...")
        setupSwipeRefresh()
        android.util.Log.d("OverviewFragment", "✅ Swipe refresh setup complete (${System.currentTimeMillis() - startTime}ms)")
        
        android.util.Log.d("OverviewFragment", "👁️ Observing ViewModel...")
        observeViewModel()
        android.util.Log.d("OverviewFragment", "✅ ViewModel observers setup (${System.currentTimeMillis() - startTime}ms)")
        
        // Vérifier s'il y a une semaine déjà sélectionnée
        val selectedYear = viewModel.selectedYear.value
        val selectedWeek = viewModel.selectedWeek.value
        
        android.util.Log.d("OverviewFragment", "📊 Checking selected week: year=$selectedYear, week=$selectedWeek")
        
        // Démarrer immédiatement les animations de loading pour donner l'impression d'activité
        startLoadingAnimations()
        
        if (selectedYear != null && selectedWeek != null) {
            // PRIORITÉ: Utiliser la semaine déjà sélectionnée dans le ViewModel
            android.util.Log.d("OverviewFragment", "🎯 Using existing selected week: $selectedYear-W$selectedWeek")
            loadDataForWeek(selectedYear, selectedWeek)
        } else {
            // Fallback seulement si aucune semaine n'est vraiment sélectionnée
            val calendar = java.util.Calendar.getInstance()
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            val currentWeek = getCurrentISOWeek()
            android.util.Log.d("OverviewFragment", "🕰️ Using current week fallback: $currentYear-W$currentWeek")
            viewModel.selectWeek(currentYear, currentWeek)
            loadDataForWeek(currentYear, currentWeek)
        }
        
        android.util.Log.d("OverviewFragment", "✅ onViewCreated completed in ${System.currentTimeMillis() - startTime}ms")
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("OverviewFragment", "🔄 onResume - Fragment is back in foreground")
        
        // Enregistrer le BroadcastReceiver pour écouter les changements de préférences
        val filter = IntentFilter(SupplierPreferences.ACTION_SUPPLIER_PREFERENCES_CHANGED)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(preferencesReceiver, filter)
        
        // Mettre à jour la visibilité des cartes au cas où les préférences auraient changé
        // pendant que le fragment était en pause (ex: dans les paramètres)
        updateCardVisibilityOnResume()
        
        // Force refresh des données pour s'assurer que les calculs sont corrects
        // après un retour depuis une page fournisseur
        forceRefreshDataOnResume()
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
    
    private fun forceRefreshDataOnResume() {
        android.util.Log.d("OverviewFragment", "🔄 Forcing data refresh on resume...")
        
        // Arrêter toutes les animations en cours pour éviter les conflits
        completelyStopAllAnimations()
        
        // Remettre les placeholders pour que les animations se relancent correctement
        resetToPlaceholders()
        
        // Éviter le refresh si les données viennent d'être chargées (moins de 1 seconde)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastLoad = currentTime - lastDataLoadTime
        
        if (!isInitialLoad && timeSinceLastLoad < 1000) {
            android.util.Log.d("OverviewFragment", "⏭️ Skipping refresh - data was loaded recently (${timeSinceLastLoad}ms ago)")
            return
        }
        
        isInitialLoad = false
        
        // Vérifier s'il y a une semaine sélectionnée
        val selectedYear = viewModel.selectedYear.value
        val selectedWeek = viewModel.selectedWeek.value
        
        if (selectedYear != null && selectedWeek != null) {
            android.util.Log.d("OverviewFragment", "📊 Refreshing data for week $selectedYear-W$selectedWeek")
            
            // Démarrer les animations de loading
            startLoadingAnimations()
            
            // IMPORTANT: Toujours recharger TOUTES les données depuis Firebase
            // Le ViewModel peut contenir seulement les données du fournisseur visité !
            android.util.Log.d("OverviewFragment", "⚠️ ViewModel products may be incomplete - forcing fresh Firebase load")
            loadDataForWeek(selectedYear, selectedWeek)
        } else {
            android.util.Log.d("OverviewFragment", "⚠️ No selected week found, using current week")
            
            // Fallback vers la semaine courante
            val calendar = java.util.Calendar.getInstance()
            val currentYear = calendar.get(java.util.Calendar.YEAR)
            val currentWeek = getCurrentISOWeek()
            viewModel.selectWeek(currentYear, currentWeek)
            loadDataForWeek(currentYear, currentWeek)
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
                        // Pas besoin d'arrêter des animations
                        android.util.Log.d("OverviewFragment", "📊 Ready to display real values...")
                        
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
                        // Pas d'animations à arrêter
                        
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
        val loadStartTime = System.currentTimeMillis()
        android.util.Log.d("OverviewFragment", "📥 Starting loadDataForWeek($year, $week)")
        
        viewModel.viewModelScope.launch {
            try {
                android.util.Log.d("OverviewFragment", "🔧 Creating Firebase repository...")
                val repository = com.nextjsclient.android.data.repository.FirebaseRepository()
                android.util.Log.d("OverviewFragment", "✅ Repository created (${System.currentTimeMillis() - loadStartTime}ms)")
                
                // Optimisation: charger les données en parallèle avec coroutines
                android.util.Log.d("OverviewFragment", "📊 Loading all data in parallel...")
                val allDataStartTime = System.currentTimeMillis()
                
                val weekProductsDeferred = async { repository.getWeekDecisions(year, week, "all") }
                
                val (previousYear, previousWeek) = getPreviousWeek(year, week)
                val previousWeekProductsDeferred = async { repository.getWeekDecisions(previousYear, previousWeek, "all") }
                val anecoopProductsDeferred = async { repository.getWeekDecisions(year, week, "anecoop") }
                val solagoraProductsDeferred = async { repository.getWeekDecisions(year, week, "solagora") }
                
                // Attendre toutes les requêtes en parallèle
                val weekProducts = weekProductsDeferred.await()
                previousWeekProducts = previousWeekProductsDeferred.await()
                preloadedAnecoopProducts = anecoopProductsDeferred.await()
                preloadedSolagoraProducts = solagoraProductsDeferred.await()
                
                android.util.Log.d("OverviewFragment", "✅ All data loaded in parallel: ${weekProducts.size} current, ${previousWeekProducts.size} previous, ${preloadedAnecoopProducts.size} anecoop, ${preloadedSolagoraProducts.size} solagora (${System.currentTimeMillis() - allDataStartTime}ms)")
                
                android.util.Log.d("OverviewFragment", "📊 All data loaded in ${System.currentTimeMillis() - loadStartTime}ms, updating UI...")
                
                // Marquer le timestamp du chargement
                lastDataLoadTime = System.currentTimeMillis()
                
                // Activer les boutons trophée maintenant que les données sont chargées
                activity?.runOnUiThread {
                    android.util.Log.d("OverviewFragment", "🏆 Enabling trophy buttons...")
                    enableTrophyButtons()
                    // Auto-refresh du top SCA si un seul fournisseur est activé
                    refreshTopScaIfNeeded()
                    android.util.Log.d("OverviewFragment", "✅ Trophy buttons enabled")
                }
                
                
                // Mettre à jour l'UI
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        android.util.Log.d("OverviewFragment", "🎨 Updating UI with loaded data...")
                        val uiStartTime = System.currentTimeMillis()
                        
                        // Pas besoin d'arrêter des animations (il n'y en a pas)
                        android.util.Log.d("OverviewFragment", "📊 Ready to update stats with real data...")
                        
                        // IMPORTANT: Utiliser directement les weekProducts chargés depuis Firebase
                        // Ne pas passer par viewModel.setProducts() qui peut avoir des données incomplètes
                        android.util.Log.d("OverviewFragment", "📦 Using fresh Firebase data directly: ${weekProducts.size} products")
                        calculateAndDisplayStats(weekProducts)
                        
                        // Mettre à jour le ViewModel après pour les autres fragments
                        viewModel.setProducts(weekProducts)
                        
                        android.util.Log.d("OverviewFragment", "✅ UI updated in ${System.currentTimeMillis() - uiStartTime}ms")
                        android.util.Log.d("OverviewFragment", "🎉 Total loadDataForWeek completed in ${System.currentTimeMillis() - loadStartTime}ms")
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("OverviewFragment", "❌ Error in loadDataForWeek: ${e.message}", e)
                activity?.runOnUiThread {
                    if (_binding != null && isAdded) {
                        // Pas d'animations à arrêter en cas d'erreur non plus
                        
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
                    // Animation de pulsation du bouton
                    animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .setDuration(100)
                        .withEndAction {
                            animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                    
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
                    // Animation de pulsation du bouton
                    animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .setDuration(100)
                        .withEndAction {
                            animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                    
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
    
    private fun startLoadingAnimations() {
        android.util.Log.d("OverviewFragment", "🎭 Starting immediate loading animations...")
        
        // Démarrer les animations shimmer sur toutes les cartes immédiatement
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        // Animation shimmer subtile sur les valeurs principales
        startShimmerAnimation(anecoopCard?.findViewById(R.id.totalProductsValue))
        startShimmerAnimation(anecoopCard?.findViewById(R.id.productsInValue))
        startShimmerAnimation(anecoopCard?.findViewById(R.id.productsOutValue))
        
        startShimmerAnimation(solagoraCard?.findViewById(R.id.totalProductsValue))
        startShimmerAnimation(solagoraCard?.findViewById(R.id.productsInValue))
        startShimmerAnimation(solagoraCard?.findViewById(R.id.productsOutValue))
        
        // Animation de pulsation subtile sur les headers
        startPulseAnimation(anecoopCard?.findViewById(R.id.supplierHeader))
        startPulseAnimation(solagoraCard?.findViewById(R.id.supplierHeader))
        
        // Démarrer immédiatement un CountUp anticipé avec des valeurs estimées
        startAnticipatedCountUp()
        
        // Si le top SCA est visible, l'animer aussi
        if (isShowingTopSca) {
            val topScaCard = binding.root.findViewById<View>(R.id.topScaCard)
            // Pas d'animation sur les top products pour le moment
        }
    }
    
    private fun startAnticipatedCountUp() {
        android.util.Log.d("OverviewFragment", "💤 Setting default values to zero (no animations)")
        
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        // Simplement mettre toutes les valeurs à 0 - pas d'animations
        anecoopCard?.findViewById<TextView>(R.id.totalProductsValue)?.text = "0"
        anecoopCard?.findViewById<TextView>(R.id.productsInValue)?.text = "0"
        anecoopCard?.findViewById<TextView>(R.id.productsOutValue)?.text = "0"
        
        solagoraCard?.findViewById<TextView>(R.id.totalProductsValue)?.text = "0"
        solagoraCard?.findViewById<TextView>(R.id.productsInValue)?.text = "0"
        solagoraCard?.findViewById<TextView>(R.id.productsOutValue)?.text = "0"
    }
    
    private fun startShimmerAnimation(view: TextView?) {
        view?.let { textView ->
            // Animation de shimmer sur le texte
            val shimmerAnimator = ObjectAnimator.ofFloat(textView, "alpha", 1.0f, 0.3f, 1.0f)
            shimmerAnimator.duration = 1200
            shimmerAnimator.repeatCount = ObjectAnimator.INFINITE
            shimmerAnimator.repeatMode = ObjectAnimator.REVERSE
            shimmerAnimator.interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
            shimmerAnimator.start()
            
            // Stocker l'animator pour pouvoir l'arrêter plus tard
            textView.tag = shimmerAnimator
        }
    }
    
    private fun startPulseAnimation(view: View?) {
        view?.let { header ->
            // Animation de pulsation très subtile sur le header
            val pulseAnimator = ObjectAnimator.ofFloat(header, "alpha", 1.0f, 0.9f)
            pulseAnimator.duration = 2000
            pulseAnimator.repeatCount = ObjectAnimator.INFINITE
            pulseAnimator.repeatMode = ObjectAnimator.REVERSE
            pulseAnimator.interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
            pulseAnimator.start()
            
            // Stocker l'animator pour pouvoir l'arrêter plus tard
            header.tag = pulseAnimator
        }
    }
    
    private fun stopLoadingAnimations() {
        android.util.Log.d("OverviewFragment", "🛑 Stopping continuous animations - Firebase data ready...")
        
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        // DEBUG: Vérifier l'état avant arrêt
        val anecoopTotal = anecoopCard?.findViewById<TextView>(R.id.totalProductsValue)
        val anecoopIn = anecoopCard?.findViewById<TextView>(R.id.productsInValue) 
        val anecoopOut = anecoopCard?.findViewById<TextView>(R.id.productsOutValue)
        
        android.util.Log.d("OverviewFragment", "🔍 BEFORE stopping - Anecoop values: total='${anecoopTotal?.text}', in='${anecoopIn?.text}', out='${anecoopOut?.text}'")
        android.util.Log.d("OverviewFragment", "🔍 BEFORE stopping - Anecoop tags: total=${anecoopTotal?.tag != null}, in=${anecoopIn?.tag != null}, out=${anecoopOut?.tag != null}")
        
        // Marquer qu'on vient d'arrêter des animations continues
        val hadContinuousAnimations = (anecoopTotal?.tag != null) || (anecoopIn?.tag != null) || (anecoopOut?.tag != null)
        
        // FORCER l'arrêt des animations continues en annulant les animators ET supprimant les tags
        android.util.Log.d("OverviewFragment", "🛑 Force stopping continuous animations...")
        
        // Annuler les animators avant de supprimer les tags
        val anecoopTotalTV = anecoopCard?.findViewById<TextView>(R.id.totalProductsValue)
        val anecoopInTV = anecoopCard?.findViewById<TextView>(R.id.productsInValue)
        val anecoopOutTV = anecoopCard?.findViewById<TextView>(R.id.productsOutValue)
        val solagoraTotalTV = solagoraCard?.findViewById<TextView>(R.id.totalProductsValue)
        val solagoraInTV = solagoraCard?.findViewById<TextView>(R.id.productsInValue)
        val solagoraOutTV = solagoraCard?.findViewById<TextView>(R.id.productsOutValue)
        
        // Cancel tous les animators actifs
        (anecoopTotalTV?.tag as? android.animation.ValueAnimator)?.cancel()
        (anecoopInTV?.tag as? android.animation.ValueAnimator)?.cancel()
        (anecoopOutTV?.tag as? android.animation.ValueAnimator)?.cancel()
        (solagoraTotalTV?.tag as? android.animation.ValueAnimator)?.cancel()
        (solagoraInTV?.tag as? android.animation.ValueAnimator)?.cancel()
        (solagoraOutTV?.tag as? android.animation.ValueAnimator)?.cancel()
        
        // Supprimer les tags après annulation
        anecoopTotalTV?.tag = null
        anecoopInTV?.tag = null
        anecoopOutTV?.tag = null
        solagoraTotalTV?.tag = null
        solagoraInTV?.tag = null
        solagoraOutTV?.tag = null
        
        // Flag pour indiquer qu'on vient d'arrêter des animations continues
        justStoppedContinuousAnimations = hadContinuousAnimations
        android.util.Log.d("OverviewFragment", "🔍 AFTER stopping - Had continuous animations: $hadContinuousAnimations, Flag set: $justStoppedContinuousAnimations")
        
        // Arrêter complètement les shimmer animations sur les headers
        stopAnimatorIfExists(anecoopCard?.findViewById(R.id.supplierHeader))
        stopAnimatorIfExists(solagoraCard?.findViewById(R.id.supplierHeader))
        anecoopCard?.findViewById<View>(R.id.supplierHeader)?.alpha = 1.0f
        solagoraCard?.findViewById<View>(R.id.supplierHeader)?.alpha = 1.0f
        
        // Les animations CountUp continues vont maintenant s'arrêter naturellement
        // car les tags sont supprimés et ModernOverviewHelper va prendre le relais
        
        // Arrêter complètement les animations de header
        stopAnimatorIfExists(anecoopCard?.findViewById(R.id.supplierHeader))
        stopAnimatorIfExists(solagoraCard?.findViewById(R.id.supplierHeader))
        anecoopCard?.findViewById<View>(R.id.supplierHeader)?.alpha = 1.0f
        solagoraCard?.findViewById<View>(R.id.supplierHeader)?.alpha = 1.0f
        
        // Arrêt progressif après un délai pour laisser les vraies valeurs se stabiliser
        binding.root.postDelayed({
            completelyStopAllAnimations()
        }, 2000L) // 2 secondes de fade lent avant arrêt complet
        
        // DEBUG: Vérifier les valeurs finales après 3 secondes
        binding.root.postDelayed({
            val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
            val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
            val anecoopTotal = anecoopCard?.findViewById<TextView>(R.id.totalProductsValue)?.text
            val anecoopIn = anecoopCard?.findViewById<TextView>(R.id.productsInValue)?.text
            val anecoopOut = anecoopCard?.findViewById<TextView>(R.id.productsOutValue)?.text
            val solagoraTotal = solagoraCard?.findViewById<TextView>(R.id.totalProductsValue)?.text
            val solagoraIn = solagoraCard?.findViewById<TextView>(R.id.productsInValue)?.text
            val solagoraOut = solagoraCard?.findViewById<TextView>(R.id.productsOutValue)?.text
            android.util.Log.d("OverviewFragment", "🔍 FINAL UI VALUES - Anecoop: total='$anecoopTotal', in='$anecoopIn', out='$anecoopOut'")
            android.util.Log.d("OverviewFragment", "🔍 FINAL UI VALUES - Solagora: total='$solagoraTotal', in='$solagoraIn', out='$solagoraOut'")
        }, 3000L)
    }
    
    private fun transitionToSlowFade(view: TextView?) {
        view?.let { textView ->
            // Arrêter l'animation rapide existante
            val currentAnimator = textView.tag as? ObjectAnimator
            currentAnimator?.cancel()
            
            // Démarrer une animation plus lente et plus subtile
            val slowFadeAnimator = ObjectAnimator.ofFloat(textView, "alpha", 1.0f, 0.7f, 1.0f)
            slowFadeAnimator.duration = 3000L // 3 secondes au lieu de 1.2
            slowFadeAnimator.repeatCount = 3 // Seulement 3 répétitions au lieu d'infini
            slowFadeAnimator.repeatMode = ObjectAnimator.REVERSE
            slowFadeAnimator.interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
            
            // À la fin, s'arrêter à alpha = 1.0f
            slowFadeAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    textView.alpha = 1.0f
                    textView.tag = null
                }
            })
            
            slowFadeAnimator.start()
            textView.tag = slowFadeAnimator
        }
    }
    
    private fun completelyStopAllAnimations() {
        android.util.Log.d("OverviewFragment", "🔇 Completely stopping all remaining animations...")
        
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        // Arrêt définitif seulement pour les entrants/sortants
        // (les produits actifs sont déjà arrêtés précédemment)
        stopAnimatorIfExists(anecoopCard?.findViewById(R.id.productsInValue))
        stopAnimatorIfExists(anecoopCard?.findViewById(R.id.productsOutValue))
        stopAnimatorIfExists(solagoraCard?.findViewById(R.id.productsInValue))
        stopAnimatorIfExists(solagoraCard?.findViewById(R.id.productsOutValue))
        
        // Assurer alpha = 1.0f pour entrants/sortants
        // (produits actifs déjà à alpha = 1.0f)
        anecoopCard?.findViewById<View>(R.id.productsInValue)?.alpha = 1.0f
        anecoopCard?.findViewById<View>(R.id.productsOutValue)?.alpha = 1.0f
        solagoraCard?.findViewById<View>(R.id.productsInValue)?.alpha = 1.0f
        solagoraCard?.findViewById<View>(R.id.productsOutValue)?.alpha = 1.0f
    }
    
    private fun resetToPlaceholders() {
        android.util.Log.d("OverviewFragment", "🔄 Resetting to placeholders for fresh animation...")
        
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        // Remettre les placeholders "--" pour forcer une détection de first load
        anecoopCard?.findViewById<TextView>(R.id.totalProductsValue)?.apply {
            text = "--"
            alpha = 1.0f
            translationY = 0f
            tag = null
        }
        anecoopCard?.findViewById<TextView>(R.id.productsInValue)?.apply {
            text = "--"
            alpha = 1.0f
            translationY = 0f
            tag = null
        }
        anecoopCard?.findViewById<TextView>(R.id.productsOutValue)?.apply {
            text = "--"
            alpha = 1.0f
            translationY = 0f
            tag = null
        }
        
        solagoraCard?.findViewById<TextView>(R.id.totalProductsValue)?.apply {
            text = "--"
            alpha = 1.0f
            translationY = 0f
            tag = null
        }
        solagoraCard?.findViewById<TextView>(R.id.productsInValue)?.apply {
            text = "--"
            alpha = 1.0f
            translationY = 0f
            tag = null
        }
        solagoraCard?.findViewById<TextView>(R.id.productsOutValue)?.apply {
            text = "--"
            alpha = 1.0f
            translationY = 0f
            tag = null
        }
    }
    
    private fun stopAnimatorIfExists(view: View?) {
        view?.let {
            val animator = it.tag as? ObjectAnimator
            animator?.cancel()
            it.tag = null
        }
    }
    
    
    private fun enableTrophyButtons() {
        if (!areTopProductsReady()) {
            return
        }
        
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
    
    private fun areTopProductsReady(): Boolean {
        val supplierPreferences = com.nextjsclient.android.utils.SupplierPreferences(requireContext())
        val anecoopEnabled = supplierPreferences.isAnecoopEnabled
        val solagoraEnabled = supplierPreferences.isSolagoraEnabled
        
        // Vérifier que les données des fournisseurs activés sont chargées et contiennent des top produits
        var anecoopReady = true
        var solagoraReady = true
        
        if (anecoopEnabled) {
            anecoopReady = preloadedAnecoopProducts.isNotEmpty() && 
                          preloadedAnecoopProducts.any { it.totalScas > 0 }
        }
        
        if (solagoraEnabled) {
            solagoraReady = preloadedSolagoraProducts.isNotEmpty() && 
                           preloadedSolagoraProducts.any { it.totalScas > 0 }
        }
        
        return anecoopReady && solagoraReady
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
        
        if (!autoShow) {
            // Mode manuel avec animations expressives
            
            // 1. D'abord, faire glisser et disparaître la carte active du fournisseur
            val cardToHide = if (supplier == "anecoop") solagoraCard else anecoopCard
            val cardToShrink = if (supplier == "anecoop") anecoopCard else solagoraCard
            
            // Animation de sortie pour la carte opposée (glissement + fade)
            cardToHide?.animate()
                ?.translationX(if (supplier == "anecoop") 100f else -100f)
                ?.alpha(0f)
                ?.scaleX(0.95f)
                ?.scaleY(0.95f)
                ?.setDuration(350)
                ?.setInterpolator(android.view.animation.AccelerateInterpolator(1.2f))
                ?.withEndAction {
                    cardToHide.visibility = View.GONE
                    cardToHide.translationX = 0f
                    cardToHide.scaleX = 1f
                    cardToHide.scaleY = 1f
                }?.start()
            
            // 2. Animation de réduction pour la carte du fournisseur sélectionné
            cardToShrink?.animate()
                ?.scaleX(0.92f)
                ?.scaleY(0.92f)
                ?.translationY(-30f)
                ?.setDuration(300)
                ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                ?.setStartDelay(100)
                ?.withEndAction {
                    // 3. Après réduction, faire apparaître le Top SCA avec un effet de ressort
                    topScaCard?.apply {
                        visibility = View.VISIBLE
                        alpha = 0f
                        scaleX = 0.8f
                        scaleY = 0.8f
                        translationY = 100f
                        rotationX = 15f // Légère rotation 3D
                        
                        animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationY(0f)
                            .rotationX(0f)
                            .setDuration(500)
                            .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                            .withEndAction {
                                // 4. Faire disparaître complètement la carte du fournisseur après l'apparition du Top SCA
                                cardToShrink.animate()
                                    ?.alpha(0f)
                                    ?.scaleX(0.8f)
                                    ?.scaleY(0.8f)
                                    ?.translationY(-50f)
                                    ?.setDuration(250)
                                    ?.setInterpolator(android.view.animation.AccelerateInterpolator())
                                    ?.withEndAction {
                                        cardToShrink.visibility = View.GONE
                                        cardToShrink.alpha = 1f
                                        cardToShrink.scaleX = 1f
                                        cardToShrink.scaleY = 1f
                                        cardToShrink.translationY = 0f
                                    }?.start()
                            }
                            .start()
                    }
                }?.start()
                
        } else {
            // Mode automatique (un seul fournisseur) - animation plus simple
            topScaCard?.apply {
                visibility = View.VISIBLE
                alpha = 0f
                scaleX = 0.95f
                scaleY = 0.95f
                translationY = 30f
                animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(400)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                    .start()
            }
        }
        
        // Masquer/afficher le bouton fermer selon le mode
        topScaCard?.findViewById<View>(R.id.closeTopScaButton)?.visibility = if (autoShow) View.GONE else View.VISIBLE
        
        // Charger et afficher les données du top SCA
        loadTopScaData(supplier)
    }
    
    private fun hideTopSca() {
        isShowingTopSca = false
        topScaSupplier = null
        
        val topScaCard = binding.root.findViewById<View>(R.id.topScaCard)
        val anecoopCard = binding.root.findViewById<View>(R.id.anecoopModernCard)
        val solagoraCard = binding.root.findViewById<View>(R.id.solagoraModernCard)
        
        // Animation de disparition du Top SCA avec effet de glissement et rotation
        topScaCard?.animate()
            ?.alpha(0f)
            ?.scaleX(0.85f)
            ?.scaleY(0.85f)
            ?.translationY(80f)
            ?.rotationX(-10f) // Rotation 3D inverse
            ?.setDuration(350)
            ?.setInterpolator(android.view.animation.AccelerateInterpolator(1.3f))
            ?.withEndAction {
                topScaCard.visibility = View.GONE
                topScaCard.scaleX = 1f
                topScaCard.scaleY = 1f
                topScaCard.translationY = 0f
                topScaCard.rotationX = 0f
                topScaCard.alpha = 1f
            }?.start()
        
        // Réafficher les cartes fournisseurs avec des animations expressives
        if (supplierPreferences.isAnecoopEnabled) {
            anecoopCard?.apply {
                visibility = View.VISIBLE
                alpha = 0f
                scaleX = 0.9f
                scaleY = 0.9f
                translationY = 40f
                translationX = -50f // Arrive de la gauche
                
                animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .translationX(0f)
                    .setDuration(450)
                    .setStartDelay(150) // Délai pour effet séquentiel
                    .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                    .start()
            }
        }
        
        if (supplierPreferences.isSolagoraEnabled) {
            solagoraCard?.apply {
                visibility = View.VISIBLE
                alpha = 0f
                scaleX = 0.9f
                scaleY = 0.9f
                translationY = 40f
                translationX = 50f // Arrive de la droite
                
                animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .translationX(0f)
                    .setDuration(450)
                    .setStartDelay(if (supplierPreferences.isAnecoopEnabled) 250 else 150) // Délai plus long si les deux cartes
                    .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
                    .start()
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
            android.util.Log.d("OverviewFragment", "🏆 Updating top products display with ${products.size} products")
            
            // Déterminer le type d'animation selon le contexte
            val wasEmpty = card.findViewById<View>(R.id.topSca1)?.visibility != View.VISIBLE
            
            // Mettre à jour les données d'abord (sans animation)
            updateTopScaData(products, card)
            
            // Choisir l'animation appropriée
            if (wasEmpty && products.isNotEmpty()) {
                // Première apparition - animation d'entrée expressive
                android.util.Log.d("OverviewFragment", "🎬 Starting entrance animation for top products")
                TopProductAnimator.animateTopProductsEntrance(products, card) {
                    android.util.Log.d("OverviewFragment", "✨ Top products entrance animation completed")
                }
            } else if (products.isNotEmpty()) {
                // Mise à jour - animation de pulsation subtile
                android.util.Log.d("OverviewFragment", "🔄 Starting update animation for top products")
                TopProductAnimator.animateTopProductsUpdate(products, card)
            }
        }
    }
    
    private fun updateTopScaData(products: List<com.nextjsclient.android.data.models.ScamarkProduct>, card: View) {
        // Top 1
        if (products.isNotEmpty()) {
            val product1 = products[0]
            
            val nameView = card.findViewById<TextView>(R.id.topSca1Name)
            val detailsView = card.findViewById<TextView>(R.id.topSca1Details)
            val scaView = card.findViewById<TextView>(R.id.topSca1Sca)
            val containerView = card.findViewById<View>(R.id.topSca1)
            
            nameView?.text = product1.productName.split(" ").take(3).joinToString(" ")
            detailsView?.text = "${product1.supplier}"
            // Le texte SCA sera animé par TopProductAnimator
            scaView?.text = "0 SCA" // Valeur initiale pour l'animation
            containerView?.visibility = View.VISIBLE
        } else {
            card.findViewById<View>(R.id.topSca1)?.visibility = View.GONE
        }
        
        // Top 2
        if (products.size > 1) {
            val product2 = products[1]
            card.findViewById<TextView>(R.id.topSca2Name)?.text = product2.productName.split(" ").take(3).joinToString(" ")
            card.findViewById<TextView>(R.id.topSca2Details)?.text = "${product2.supplier}"
            card.findViewById<TextView>(R.id.topSca2Sca)?.text = "0 SCA" // Pour l'animation
            card.findViewById<View>(R.id.topSca2)?.visibility = View.VISIBLE
        } else {
            card.findViewById<View>(R.id.topSca2)?.visibility = View.GONE
        }
        
        // Top 3
        if (products.size > 2) {
            val product3 = products[2]
            card.findViewById<TextView>(R.id.topSca3Name)?.text = product3.productName.split(" ").take(3).joinToString(" ")
            card.findViewById<TextView>(R.id.topSca3Details)?.text = "${product3.supplier}"
            card.findViewById<TextView>(R.id.topSca3Sca)?.text = "0 SCA" // Pour l'animation
            card.findViewById<View>(R.id.topSca3)?.visibility = View.VISIBLE
        } else {
            card.findViewById<View>(R.id.topSca3)?.visibility = View.GONE
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
                
                // Réinitialiser l'état du top SCA
                if (isShowingTopSca) {
                    hideTopSca()
                }
                
                // Réinitialiser à la semaine actuelle
                val calendar = java.util.Calendar.getInstance()
                val currentYear = calendar.get(java.util.Calendar.YEAR)
                val currentWeek = getCurrentISOWeek()
                viewModel.selectWeek(currentYear, currentWeek)
                
                // Rafraîchir les données
                viewModel.refresh()
            }
        }
    }
    
    private fun updateWeekNumberDisplay() {
        val weekNumber = viewModel.selectedWeek.value ?: java.util.Calendar.getInstance().get(java.util.Calendar.WEEK_OF_YEAR)
        val year = viewModel.selectedYear.value ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        
        // Mettre à jour le numéro de semaine
        binding.root.findViewById<TextView>(R.id.weekNumberHeader)?.text = getString(R.string.week_with_number, weekNumber)
        
        // Calculer et afficher la plage de dates
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.YEAR, year)
        calendar.set(java.util.Calendar.WEEK_OF_YEAR, weekNumber)
        calendar.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        
        val startDate = calendar.time
        calendar.add(java.util.Calendar.DAY_OF_WEEK, 6)
        val endDate = calendar.time
        
        // Formats pour éviter la répétition de l'année
        val currentLocale = resources.configuration.locales[0]
        val dateFormatWithYear = java.text.SimpleDateFormat("d MMMM yyyy", currentLocale)
        val dateFormatWithoutYear = java.text.SimpleDateFormat("d MMMM", currentLocale)
        
        val startCalendar = java.util.Calendar.getInstance().apply { time = startDate }
        val endCalendar = java.util.Calendar.getInstance().apply { time = endDate }
        
        val dateRangeText = if (startCalendar.get(java.util.Calendar.YEAR) == endCalendar.get(java.util.Calendar.YEAR)) {
            // Même année et même mois : "19 au 25 août 2025"
            if (startCalendar.get(java.util.Calendar.MONTH) == endCalendar.get(java.util.Calendar.MONTH)) {
                val dayOnlyFormat = java.text.SimpleDateFormat("d", currentLocale)
                val startDay = dayOnlyFormat.format(startDate)
                val endStr = dateFormatWithYear.format(endDate)
                val separator = getString(R.string.date_range_separator)
                "$startDay $separator $endStr"
            } else {
                // Même année mais mois différents : "28 décembre au 3 janvier 2025"
                val startStr = dateFormatWithoutYear.format(startDate)
                val endStr = dateFormatWithYear.format(endDate)
                val separator = getString(R.string.date_range_separator)
                "$startStr $separator $endStr"
            }
        } else {
            // Années différentes : "28 décembre 2024 au 3 janvier 2025"
            val startStr = dateFormatWithYear.format(startDate)
            val endStr = dateFormatWithYear.format(endDate)
            val separator = getString(R.string.date_range_separator)
            "$startStr $separator $endStr"
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
        val startTime = System.currentTimeMillis()
        android.util.Log.d("OverviewFragment", "📈 Starting calculateAndDisplayStats with ${products.size} products")
        
        // DEBUG: Analyser les fournisseurs présents dans les données
        val supplierCounts = products.groupingBy { it.supplier.lowercase() }.eachCount()
        android.util.Log.d("OverviewFragment", "🏢 Suppliers in data: $supplierCounts")
        
        // Appliquer le filtre fournisseur selon les préférences
        val filteredProducts = products.filter { product ->
            val supplier = product.supplier.lowercase()
            when (supplier) {
                "anecoop" -> supplierPreferences.isAnecoopEnabled
                "solagora" -> supplierPreferences.isSolagoraEnabled
                else -> true // Garder les autres fournisseurs inconnus
            }
        }
        android.util.Log.d("OverviewFragment", "🔍 Filtered to ${filteredProducts.size} products (${System.currentTimeMillis() - startTime}ms)")
        
        
        // Séparer par fournisseur (après filtrage)
        val anecoopProducts = filteredProducts.filter { it.supplier.lowercase() == "anecoop" }
        val solagoraProducts = filteredProducts.filter { it.supplier.lowercase() == "solagora" }
        
        // IMPORTANT: Synchroniser les données pré-chargées avec les données filtrées utilisées pour les stats
        // Cela assure que le Top SCA et les autres fonctionnalités utilisent les mêmes données
        preloadedAnecoopProducts = anecoopProducts
        preloadedSolagoraProducts = solagoraProducts
        android.util.Log.d("OverviewFragment", "🔄 Synchronized preloaded data: ${anecoopProducts.size} anecoop, ${solagoraProducts.size} solagora")
        
        
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
        android.util.Log.d("OverviewFragment", "🧮 Calculating stats...")
        val statsStartTime = System.currentTimeMillis()
        val anecoopStats = calculateStatsForProducts(anecoopProducts, previousAnecoopProducts)
        val solagoraStats = calculateStatsForProducts(solagoraProducts, previousSolagoraProducts)
        android.util.Log.d("OverviewFragment", "✅ Stats calculated in ${System.currentTimeMillis() - statsStartTime}ms")
        android.util.Log.d("OverviewFragment", "📊 Anecoop: ${anecoopStats.totalProducts} products, ${anecoopStats.productsIn} in, ${anecoopStats.productsOut} out")
        android.util.Log.d("OverviewFragment", "📊 Solagora: ${solagoraStats.totalProducts} products, ${solagoraStats.productsIn} in, ${solagoraStats.productsOut} out")
        
        // Mettre à jour les dashboards simultanément
        android.util.Log.d("OverviewFragment", "🎨 Updating supplier dashboards...")
        val updateStartTime = System.currentTimeMillis()
        updateSupplierDashboard("anecoop", anecoopStats)
        updateSupplierDashboard("solagora", solagoraStats)
        android.util.Log.d("OverviewFragment", "✅ Dashboards updated in ${System.currentTimeMillis() - updateStartTime}ms")
        
        android.util.Log.d("OverviewFragment", "🎉 calculateAndDisplayStats completed in ${System.currentTimeMillis() - startTime}ms")
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
        modernHelper.updateSupplierCard(supplier, stats, isAnecoop, justStoppedContinuousAnimations)
        
        // Reset le flag après utilisation (seul le premier appel doit l'utiliser)
        if (justStoppedContinuousAnimations) {
            justStoppedContinuousAnimations = false
            android.util.Log.d("OverviewFragment", "🔄 Reset justStoppedContinuousAnimations flag after ${supplier.uppercase()} card update")
        }
        
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
        
        val dateFormat = java.text.SimpleDateFormat("dd/MM", resources.configuration.locales[0])
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